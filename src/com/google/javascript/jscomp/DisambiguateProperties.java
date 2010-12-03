/*
 * Copyright 2008 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkState;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.ConcreteType.ConcreteFunctionType;
import com.google.javascript.jscomp.ConcreteType.ConcreteInstanceType;
import com.google.javascript.jscomp.ConcreteType.ConcreteUnionType;
import com.google.javascript.jscomp.ConcreteType.ConcreteUniqueType;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.TypeValidator.TypeMismatch;
import com.google.javascript.jscomp.graph.StandardUnionFind;
import com.google.javascript.jscomp.graph.UnionFind;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionPrototypeType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticScope;
import com.google.javascript.rhino.jstype.UnionType;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Logger;

/**
 * DisambiguateProperties renames properties to disambiguate between unrelated
 * fields with the same name. Two properties are considered related if they
 * share a definition on their prototype chains, or if they are potentially
 * referenced together via union types.
 *
 * <p> Renamimg only occurs if there are two or more distinct properties with
 * the same name.
 *
 * <p> This pass allows other passes, such as inlining and code removal to take
 * advantage of type information implicitly.
 *
 * <pre>
 *   Foo.a;
 *   Bar.a;
 * </pre>
 *
 * <p> will become
 *
 * <pre>
 *   Foo.a$Foo;
 *   Bar.a$Bar;
 * </pre>
 *
 */
class DisambiguateProperties<T> implements CompilerPass {
  private static final Logger logger = Logger.getLogger(
      DisambiguateProperties.class.getName());

  // TODO(user): add a flag to allow enabling of this once apps start
  // using it.
  static final DiagnosticType INVALIDATION = DiagnosticType.warning(
      "JSC_INVALIDATION",
      "Property disambiguator skipping all instances of property {0} "
      + "because of type {1} node {2}");
  private final boolean showInvalidationWarnings = false;

  private final AbstractCompiler compiler;
  private final TypeSystem<T> typeSystem;

  private class Property {
    /** The name of the property. */
    final String name;

    /** All types on which the field exists, grouped together if related. */
    private UnionFind<T> types;

    /**
     * A set of types for which renaming this field should be skipped. This
     * list is first filled by fields defined in the externs file.
     */
    Set<T> typesToSkip = Sets.newHashSet();

    /**
     * If true, do not rename any instance of this field, as it has been
     * referenced from an unknown type.
     */
    boolean skipRenaming;

    /** Set of nodes for this field that need renaming. */
    Set<Node> renameNodes = Sets.newHashSet();

    /**
     * Map from node to the highest type in the prototype chain containing the
     * field for that node. In the case of a union, the type is the highest type
     * of one of the types in the union.
     */
    final Map<Node, T> rootTypes = Maps.newHashMap();

    Property(String name) {
      this.name = name;
    }

    /** Returns the types on which this field is referenced. */
    UnionFind<T> getTypes() {
      if (types == null) {
        types = new StandardUnionFind<T>();
      }
      return types;
    }

    /**
     * Record that this property is referenced from this type.
     * @return true if the type was recorded for this property, else false,
     *     which would happen if the type was invalidating.
     */
    boolean addType(T type, T top, T relatedType) {
      checkState(!skipRenaming, "Attempt to record skipped property: %s", name);
      if (typeSystem.isInvalidatingType(top)) {
        invalidate();
        return false;
      } else {
        if (typeSystem.isTypeToSkip(top)) {
          addTypeToSkip(top);
        }

        if (relatedType == null) {
          getTypes().add(top);
        } else {
          getTypes().union(top, relatedType);
        }
        typeSystem.recordInterfaces(type, top, this);
        return true;
      }
    }

    /** Records the given type as one to skip for this property. */
    void addTypeToSkip(T type) {
      for (T skipType : typeSystem.getTypesToSkipForType(type)) {
        typesToSkip.add(skipType);
        getTypes().union(skipType, type);
      }
    }

    /** Invalidates any types related to invalid types. */
    void expandTypesToSkip() {
      // If we are not going to rename any properties, then we do not need to
      // update the list of invalid types, as they are all invalid.
      if (shouldRename()) {
        int count = 0;
        while (true) {
          // It should usually only take one time through this do-while.
          checkState(++count < 10, "Stuck in loop expanding types to skip.");

          // Make sure that the representative type for each type to skip is
          // marked as being skipped.
          Set<T> rootTypesToSkip = Sets.newHashSet();
          for (T subType : typesToSkip) {
            rootTypesToSkip.add(types.find(subType));
          }
          typesToSkip.addAll(rootTypesToSkip);

          Set<T> newTypesToSkip = Sets.newHashSet();
          Set<T> allTypes = types.elements();
          int originalTypesSize = allTypes.size();
          for (T subType : allTypes) {
            if (!typesToSkip.contains(subType)
                && typesToSkip.contains(types.find(subType))) {
              newTypesToSkip.add(subType);
            }
          }

          for (T newType : newTypesToSkip) {
            addTypeToSkip(newType);
          }

          // If there were not any new types added, we are done here.
          if (types.elements().size() == originalTypesSize) {
            break;
          }
        }
      }
    }

    /** Returns true if any instance of this property should be renamed. */
    boolean shouldRename() {
      return !skipRenaming && types != null
          && types.allEquivalenceClasses().size() > 1;
    }

    /**
     * Returns true if this property should be renamed on this type.
     * expandTypesToSkip() should be called before this, if anything has been
     * added to the typesToSkip list.
     */
    boolean shouldRename(T type) {
      return !skipRenaming && !typesToSkip.contains(type);
    }

    /**
     * Invalidates a field from renaming.  Used for field references on an
     * object with unknown type.
     */
    boolean invalidate() {
      boolean changed = !skipRenaming;
      skipRenaming = true;
      types = null;
      return changed;
    }

    /**
     * Schedule the node to potentially be renamed.
     * @param node the node to rename
     * @param type the highest type in the prototype chain for which the
     *     property is defined
     * @return True if type was accepted without invalidation or if the property
     *     was already invalidated.  False if this property was invalidated this
     *     time.
     */
    boolean scheduleRenaming(Node node, T type) {
      if (!skipRenaming) {
        if (typeSystem.isInvalidatingType(type)) {
          invalidate();
          return false;
        }
        renameNodes.add(node);
        rootTypes.put(node, type);
      }
      return true;
    }
  }

  private Map<String, Property> properties = Maps.newHashMap();

  static DisambiguateProperties<JSType> forJSTypeSystem(
      AbstractCompiler compiler) {
    return new DisambiguateProperties<JSType>(
        compiler, new JSTypeSystem(compiler));
  }

  static DisambiguateProperties<ConcreteType> forConcreteTypeSystem(
      AbstractCompiler compiler, TightenTypes tt) {
    return new DisambiguateProperties<ConcreteType>(
        compiler, new ConcreteTypeSystem(tt, compiler.getCodingConvention()));
  }

  /**
   * This constructor should only be called by one of the helper functions
   * above for either the JSType system, or the concrete type system.
   */
  private DisambiguateProperties(AbstractCompiler compiler,
                                 TypeSystem<T> typeSystem) {
    this.compiler = compiler;
    this.typeSystem = typeSystem;
  }

  public void process(Node externs, Node root) {
    for (TypeMismatch mis : compiler.getTypeValidator().getMismatches()) {
      addInvalidatingType(mis.typeA);
      addInvalidatingType(mis.typeB);
    }

    StaticScope<T> scope = typeSystem.getRootScope();
    NodeTraversal.traverse(compiler, externs, new FindExternProperties());
    NodeTraversal.traverse(compiler, root, new FindRenameableProperties());
    renameProperties();
  }

  /**
   * Invalidates the given type, so that no properties on it will be renamed.
   */
  private void addInvalidatingType(JSType type) {
    type = type.restrictByNotNullOrUndefined();
    if (type instanceof UnionType) {
      for (JSType alt : ((UnionType) type).getAlternates()) {
        addInvalidatingType(alt);
      }
      return;
    }

    typeSystem.addInvalidatingType(type);
    ObjectType objType = ObjectType.cast(type);
    if (objType != null && objType.getImplicitPrototype() != null) {
      typeSystem.addInvalidatingType(objType.getImplicitPrototype());
    }
  }


  /** Returns the property for the given name, creating it if necessary. */
  protected Property getProperty(String name) {
    if (!properties.containsKey(name)) {
      properties.put(name, new Property(name));
    }
    return properties.get(name);
  }

  /** Public for testing. */
  T getTypeWithProperty(String field, T type) {
    return typeSystem.getTypeWithProperty(field, type);
  }

  /** Tracks the current type system scope while traversing. */
  private abstract class AbstractScopingCallback implements ScopedCallback {
    protected final Stack<StaticScope<T>> scopes =
        new Stack<StaticScope<T>>();

    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return true;
    }

    public void enterScope(NodeTraversal t) {
      if (t.inGlobalScope()) {
        scopes.push(typeSystem.getRootScope());
      } else {
        scopes.push(typeSystem.getFunctionScope(t.getScopeRoot()));
      }
    }

    public void exitScope(NodeTraversal t) {
      scopes.pop();
    }

    /** Returns the current scope at this point in the file. */
    protected StaticScope<T> getScope() {
      return scopes.peek();
    }
  }

  /**
   * Finds all properties defined in the externs file and sets them as
   * ineligible for renaming from the type on which they are defined.
   */
  private class FindExternProperties extends AbstractScopingCallback {
    @Override public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.getType() == Token.GETPROP) {
        String field = n.getLastChild().getString();
        T type = typeSystem.getType(getScope(), n.getFirstChild(), field);
        Property prop = getProperty(field);
        if (typeSystem.isInvalidatingType(type)) {
          prop.invalidate();
        } else {
          prop.addTypeToSkip(type);

          // If this is a prototype property, then we want to skip assignments
          // to the instance type as well.  These assignments are not usually
          // seen in the extern code itself, so we must handle them here.
          if ((type = typeSystem.getInstanceFromPrototype(type)) != null) {
            prop.getTypes().add(type);
            prop.typesToSkip.add(type);
          }
        }
      }
    }
  }

  /**
   * Traverses the tree, building a map from field names to Nodes for all
   * fields that can be renamed.
   */
  private class FindRenameableProperties extends AbstractScopingCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.getType() == Token.GETPROP) {
        handleGetProp(t, n);
      } else if (n.getType() == Token.OBJECTLIT) {
        handleObjectLit(t, n);
      }
    }

    /**
     * Processes a GETPROP node.
     */
    private void handleGetProp(NodeTraversal t, Node n) {
      String name = n.getLastChild().getString();
      T type = typeSystem.getType(getScope(), n.getFirstChild(), name);

      Property prop = getProperty(name);
      if (!prop.scheduleRenaming(n.getLastChild(),
                                 processProperty(t, prop, type, null))) {
        if (showInvalidationWarnings) {
          compiler.report(JSError.make(
              t.getSourceName(), n, INVALIDATION, name,
              (type == null ? "null" : type.toString()), n.toString()));
        }
      }
    }

    /**
     * Processes a OBJECTLIT node.
     */
    private void handleObjectLit(NodeTraversal t, Node n) {
      Node child = n.getFirstChild();
      while (child != null) {
        if (child.getType() == Token.STRING) {
          // We should never see a mix of numbers and strings.
          String name = child.getString();
          T type = typeSystem.getType(getScope(), n, name);

          Property prop = getProperty(name);
          if (!prop.scheduleRenaming(child,
                                     processProperty(t, prop, type, null))) {
            if (showInvalidationWarnings) {
              compiler.report(JSError.make(
                  t.getSourceName(), child, INVALIDATION, name,
                  (type == null ? "null" : type.toString()), n.toString()));
            }
          }
        }

        child = child.getNext();
      }
    }

    /**
     * Processes a property, adding it to the list of properties to rename.
     * @return a representative type for the property reference, which will be
     *   the highest type on the prototype chain of the provided type.  In the
     *   case of a union type, it will be the highest type on the prototype
     *   chain of one of the members of the union.
     */
    private T processProperty(
        NodeTraversal t, Property prop, T type, T relatedType) {
      type = typeSystem.restrictByNotNullOrUndefined(type);
      if (prop.skipRenaming || typeSystem.isInvalidatingType(type)) {
        return null;
      }

      Iterable<T> alternatives = typeSystem.getTypeAlternatives(type);
      if (alternatives != null) {
        T firstType = relatedType;
        for (T subType : alternatives) {
          T lastType = processProperty(t, prop, subType, firstType);
          if (lastType != null) {
            firstType = firstType == null ? lastType : firstType;
          }
        }
        return firstType;
      } else {
        T topType = typeSystem.getTypeWithProperty(prop.name, type);
        if (typeSystem.isInvalidatingType(topType)) {
          return null;
        }
        prop.addType(type, topType, relatedType);
        return topType;
      }
    }
  }

  /** Renames all properties with references on more than one type. */
  void renameProperties() {
    int propsRenamed = 0, propsSkipped = 0, instancesRenamed = 0,
        instancesSkipped = 0, singleTypeProps = 0;

    for (Property prop : properties.values()) {
      if (prop.shouldRename()) {
        Map<T, String> propNames = buildPropNames(prop.getTypes(), prop.name);

        ++propsRenamed;
        prop.expandTypesToSkip();
        UnionFind<T> types = prop.getTypes();
        for (Node node : prop.renameNodes) {
          T rootType = prop.rootTypes.get(node);
          if (prop.shouldRename(rootType)) {
            String newName = propNames.get(rootType);
            node.setString(newName);
            compiler.reportCodeChange();
            ++instancesRenamed;
          } else {
            ++instancesSkipped;
          }
        }
      } else {
        if (prop.skipRenaming) {
          ++propsSkipped;
        } else {
          ++singleTypeProps;
        }
      }
    }
    logger.info("Renamed " + instancesRenamed + " instances of "
                + propsRenamed + " properties.");
    logger.info("Skipped renaming " + instancesSkipped + " invalidated "
                + "properties, " + propsSkipped + " instances of properties "
                + "that were skipped for specific types and " + singleTypeProps
                + " properties that were referenced from only one type.");
  }

  /**
   * Chooses a name to use for renaming in each equivalence class and maps
   * each type in that class to it.
   */
  private Map<T, String> buildPropNames(UnionFind<T> types, String name) {
    Map<T, String> names = Maps.newHashMap();
    for (Set<T> set : types.allEquivalenceClasses()) {
      checkState(!set.isEmpty());

      String typeName = null;
      for (T type : set) {
        if (typeName == null || type.toString().compareTo(typeName) < 0) {
          typeName = type.toString();
        }
      }

      String newName;
      if ("{...}".equals(typeName)) {
        newName = name;
      } else {
        newName = typeName.replaceAll("[^\\w$]", "_") + "$" + name;
      }

      for (T type : set) {
        names.put(type, newName);
      }
    }
    return names;
  }

  /** Returns a map from field name to types for which it will be renamed. */
  Multimap<String, Collection<T>> getRenamedTypesForTesting() {
    Multimap<String, Collection<T>> ret = HashMultimap.create();
    for (Map.Entry<String, Property> entry: properties.entrySet()) {
      Property prop = entry.getValue();
      if (!prop.skipRenaming) {
        for (Collection<T> c : prop.getTypes().allEquivalenceClasses()) {
          if (!c.isEmpty() && !prop.typesToSkip.contains(c.iterator().next())) {
            ret.put(entry.getKey(), c);
          }
        }
      }
    }
    return ret;
  }

  /** Interface for providing the type information needed by this pass. */
  private interface TypeSystem<T> {
    // TODO(user): add a getUniqueName(T type) method that is guaranteed
    // to be unique, performant and human-readable.

    /** Returns the top-most scope used by the type system (if any). */
    StaticScope<T> getRootScope();

    /** Returns the new scope started at the given function node. */
    StaticScope<T> getFunctionScope(Node node);

    /**
     * Returns the type of the given node.
     * @param prop Only types with this property need to be returned. In general
     *     with type tightening, this will require no special processing, but in
     *     the case of an unknown JSType, we might need to add in the native
     *     types since we don't track them, but only if they have the given
     *     property.
     */
    T getType(StaticScope<T> scope, Node node, String prop);

    /**
     * Returns true if a field reference on this type will invalidiate all
     * references to that field as candidates for renaming. This is true if the
     * type is unknown or all-inclusive, as variables with such a type could be
     * references to any object.
     */
    boolean isInvalidatingType(T type);

    /**
     * Informs the given type system that a type is invalidating due to a type
     * mismatch found during type checking.
     */
    void addInvalidatingType(JSType type);

    /**
     * Returns a set of types that should be skipped given the given type.
     * This is necessary for interfaces when using JSTypes, as all super
     * interfaces must also be skipped.
     */
    ImmutableSet<T> getTypesToSkipForType(T type);

    /**
     * Determines whether the given type is one whose properties should not be
     * considered for renaming.
     */
    boolean isTypeToSkip(T type);

    /** Remove null and undefined from the options in the given type. */
    T restrictByNotNullOrUndefined(T type);

    /**
     * Returns the alternatives if this is a type that represents multiple
     * types, and null if not. Union and interface types can correspond to
     * multiple other types.
     */
    Iterable<T> getTypeAlternatives(T type);

    /**
     * Returns the type in the chain from the given type that contains the given
     * field or null if it is not found anywhere.
     */
    T getTypeWithProperty(String field, T type);

    /**
     * Returns the type of the instance of which this is the prototype or null
     * if this is not a function prototype.
     */
    T getInstanceFromPrototype(T type);

    /**
     * Records that this property could be referenced from any interface that
     * this type, or any type in its superclass chain, implements.
     */
    void recordInterfaces(T type, T relatedType,
                          DisambiguateProperties<T>.Property p);
  }

  /** Implementation of TypeSystem using JSTypes. */
  private static class JSTypeSystem implements TypeSystem<JSType> {
    private final Set<JSType> invalidatingTypes;
    private JSTypeRegistry registry;

    public JSTypeSystem(AbstractCompiler compiler) {
      registry = compiler.getTypeRegistry();
      invalidatingTypes = Sets.newHashSet(
          registry.getNativeType(JSTypeNative.ALL_TYPE),
          registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
          registry.getNativeType(JSTypeNative.NO_TYPE),
          registry.getNativeType(JSTypeNative.FUNCTION_PROTOTYPE),
          registry.getNativeType(JSTypeNative.FUNCTION_INSTANCE_TYPE),
          registry.getNativeType(JSTypeNative.OBJECT_PROTOTYPE),
          registry.getNativeType(JSTypeNative.TOP_LEVEL_PROTOTYPE),
          registry.getNativeType(JSTypeNative.UNKNOWN_TYPE));

    }

    @Override public void addInvalidatingType(JSType type) {
      checkState(!type.isUnionType());
      invalidatingTypes.add(type);
    }

    @Override public StaticScope<JSType> getRootScope() { return null; }

    @Override public StaticScope<JSType> getFunctionScope(Node node) {
      return null;
    }

    @Override public JSType getType(
        StaticScope<JSType> scope, Node node, String prop) {
      if (node.getJSType() == null) {
        return registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
      }
      return node.getJSType();
    }

    @Override public boolean isInvalidatingType(JSType type) {
      if (type == null || invalidatingTypes.contains(type) ||
          type.isUnknownType() /* unresolved types */) {
        return true;
      }

      ObjectType objType = ObjectType.cast(type);
      return objType != null && !objType.hasReferenceName();
    }

    @Override public ImmutableSet<JSType> getTypesToSkipForType(JSType type) {
      type = type.restrictByNotNullOrUndefined();
      if (type instanceof UnionType) {
        Set<JSType> types = Sets.newHashSet(type);
        for (JSType alt : ((UnionType) type).getAlternates()) {
          types.addAll(getTypesToSkipForTypeNonUnion(type));
        }
        return ImmutableSet.copyOf(types);
      }
      return ImmutableSet.copyOf(getTypesToSkipForTypeNonUnion(type));
    }

    private Set<JSType> getTypesToSkipForTypeNonUnion(JSType type) {
      Set<JSType> types = Sets.newHashSet();
      JSType skipType = type;
      while (skipType != null) {
        types.add(skipType);

        ObjectType objSkipType = skipType.toObjectType();
        if (objSkipType != null) {
          skipType = objSkipType.getImplicitPrototype();
        } else {
          break;
        }
      }
      return types;
    }

    @Override public boolean isTypeToSkip(JSType type) {
      return type.isEnumType() || (type.autoboxesTo() != null);
    }

    @Override public JSType restrictByNotNullOrUndefined(JSType type) {
      return type.restrictByNotNullOrUndefined();
    }

    @Override public Iterable<JSType> getTypeAlternatives(JSType type) {
      if (type.isUnionType()) {
        return ((UnionType) type).getAlternates();
      } else {
        ObjectType objType = type.toObjectType();
        if (objType != null &&
            objType.getConstructor() != null &&
            objType.getConstructor().isInterface()) {
          List<JSType> list = Lists.newArrayList();
          for (FunctionType impl
                   : registry.getDirectImplementors(objType)) {
            list.add(impl.getInstanceType());
          }
          return list;
        } else {
          return null;
        }
      }
    }

    @Override public ObjectType getTypeWithProperty(String field, JSType type) {
      if (!(type instanceof ObjectType)) {
        if (type.autoboxesTo() != null) {
          type = type.autoboxesTo();
        } else {
          return null;
        }
      }

      // Ignore the prototype itself at all times.
      if ("prototype".equals(field)) {
        return null;
      }

      // We look up the prototype chain to find the highest place (if any) that
      // this appears.  This will make references to overriden properties look
      // like references to the initial property, so they are renamed alike.
      ObjectType foundType = null;
      ObjectType objType = ObjectType.cast(type);
      while (objType != null && objType.getImplicitPrototype() != objType) {
        if (objType.hasOwnProperty(field)) {
          foundType = objType;
        }
        objType = objType.getImplicitPrototype();
      }
      // If the property does not exist on the referenced type but the original
      // type is an object type, see if any subtype has the property.
      if (foundType == null) {
        ObjectType maybeType = ObjectType.cast(
            registry.getGreatestSubtypeWithProperty(type, field));
        // getGreatestSubtypeWithProperty does not guarantee that the property
        // is defined on the returned type, it just indicates that it might be,
        // so we have to double check.
        if (maybeType != null && maybeType.hasOwnProperty(field)) {
          foundType = maybeType;
        }
      }
      return foundType;
    }

    @Override public JSType getInstanceFromPrototype(JSType type) {
      if (type.isFunctionPrototypeType()) {
        FunctionPrototypeType prototype = (FunctionPrototypeType) type;
        FunctionType owner = prototype.getOwnerFunction();
        if (owner.isConstructor() || owner.isInterface()) {
          return ((FunctionPrototypeType) type).getOwnerFunction()
              .getInstanceType();
        }
      }
      return null;
    }

    @Override
    public void recordInterfaces(JSType type, JSType relatedType,
                                 DisambiguateProperties<JSType>.Property p) {
      ObjectType objType = ObjectType.cast(type);
      if (objType != null) {
        FunctionType constructor;
        if (objType instanceof FunctionType) {
          constructor = (FunctionType) objType;
        } else if (objType instanceof FunctionPrototypeType) {
          constructor = ((FunctionPrototypeType) objType).getOwnerFunction();
        } else {
          constructor = objType.getConstructor();
        }
        while (constructor != null) {
          for (ObjectType itype : constructor.getImplementedInterfaces()) {
            JSType top = getTypeWithProperty(p.name, itype);
            if (top != null) {
              p.addType(itype, top, relatedType);
            } else {
              recordInterfaces(itype, relatedType, p);
            }

            // If this interface invalidated this property, return now.
            if (p.skipRenaming) return;
          }
          if (constructor.isInterface() || constructor.isConstructor()) {
            constructor = constructor.getSuperClassConstructor();
          } else {
            constructor = null;
          }
        }
      }
    }
  }

  /** Implementation of TypeSystem using concrete types. */
  private static class ConcreteTypeSystem implements TypeSystem<ConcreteType> {
    private final TightenTypes tt;
    private int nextUniqueId;
    private CodingConvention codingConvention;
    private final Set<JSType> invalidatingTypes = Sets.newHashSet();

    // An array of native types that are not tracked by type tightening, and
    // thus need to be added in if an unknown type is encountered.
    private static final JSTypeNative [] nativeTypes = new JSTypeNative[] {
        JSTypeNative.BOOLEAN_OBJECT_TYPE,
        JSTypeNative.NUMBER_OBJECT_TYPE,
        JSTypeNative.STRING_OBJECT_TYPE
    };

    public ConcreteTypeSystem(TightenTypes tt, CodingConvention convention) {
      this.tt = tt;
      this.codingConvention = convention;
    }

    @Override public void addInvalidatingType(JSType type) {
      checkState(!type.isUnionType());
      invalidatingTypes.add(type);
    }

    @Override public StaticScope<ConcreteType> getRootScope() {
      return tt.getTopScope();
    }

    @Override public StaticScope<ConcreteType> getFunctionScope(Node decl) {
      ConcreteFunctionType func = tt.getConcreteFunction(decl);
      return (func != null) ?
          func.getScope() : (StaticScope<ConcreteType>) null;
    }

    @Override
    public ConcreteType getType(
        StaticScope<ConcreteType> scope, Node node, String prop) {
      if (scope != null) {
        ConcreteType c = tt.inferConcreteType(
            (TightenTypes.ConcreteScope) scope, node);
        return maybeAddAutoboxes(c, node, prop);
      } else {
        return null;
      }
    }

    /**
     * Add concrete types for autoboxing types if necessary. The concrete type
     * system does not track native types, like string, so add them if they are
     * present in the JSType for the node.
     */
    private ConcreteType maybeAddAutoboxes(
        ConcreteType cType, Node node, String prop) {
      JSType jsType = node.getJSType();
      if (jsType == null) {
        return cType;
      } else if (jsType.isUnknownType()) {
        for (JSTypeNative nativeType : nativeTypes) {
          ConcreteType concrete = tt.getConcreteInstance(
              tt.getTypeRegistry().getNativeObjectType(nativeType));
          if (concrete != null && !concrete.getPropertyType(prop).isNone()) {
            cType = cType.unionWith(concrete);
          }
        }
        return cType;
      }

      return maybeAddAutoboxes(cType, jsType, prop);
    }

    private ConcreteType maybeAddAutoboxes(
        ConcreteType cType, JSType jsType, String prop) {
      jsType = jsType.restrictByNotNullOrUndefined();
      if (jsType instanceof UnionType) {
        for (JSType alt : ((UnionType) jsType).getAlternates()) {
          return maybeAddAutoboxes(cType, alt, prop);
        }
      }

      if (jsType.autoboxesTo() != null) {
        JSType autoboxed = jsType.autoboxesTo();
        return cType.unionWith(tt.getConcreteInstance((ObjectType) autoboxed));
      } else if (jsType.unboxesTo() != null) {
        return cType.unionWith(tt.getConcreteInstance((ObjectType) jsType));
      }

      return cType;
    }

    @Override public boolean isInvalidatingType(ConcreteType type) {
      // We will disallow types on functions so that 'prototype' is not renamed.
      // TODO(user): Support properties on functions as well.
      return (type == null) || type.isAll() || type.isFunction()
        || (type.isInstance()
            && invalidatingTypes.contains(type.toInstance().instanceType));
    }

    @Override
    public ImmutableSet<ConcreteType> getTypesToSkipForType(ConcreteType type) {
      return ImmutableSet.of(type);
    }

    @Override public boolean isTypeToSkip(ConcreteType type) {
      // Skip anonymous object literals and enum types.
      return type.isInstance()
        && !(type.toInstance().isFunctionPrototype()
             || type.toInstance().instanceType.isInstanceType());
    }

    @Override
    public ConcreteType restrictByNotNullOrUndefined(ConcreteType type) {
      // These are not represented in concrete types.
      return type;
    }

    @Override
    public Iterable<ConcreteType> getTypeAlternatives(ConcreteType type) {
      if (type.isUnion()) {
        return ((ConcreteUnionType) type).getAlternatives();
      } else {
        return null;
      }
    }

    @Override public ConcreteType getTypeWithProperty(String field,
                                                      ConcreteType type) {
      if (type.isInstance()) {
        ConcreteInstanceType instanceType = (ConcreteInstanceType) type;
        return instanceType.getInstanceTypeWithProperty(field);
      } else if (type.isFunction()) {
        if ("prototype".equals(field)
            || codingConvention.isSuperClassReference(field)) {
          return type;
        }
      } else if (type.isNone()) {
        // If the receiver is none, then this code is never reached.  We will
        // return a new fake type to ensure that this access is renamed
        // differently from any other, so it can be easily removed.
        return new ConcreteUniqueType(++nextUniqueId);
      } else if (type.isUnion()) {
        // If only one has the property, return that.
        for (ConcreteType t : ((ConcreteUnionType) type).getAlternatives()) {
          ConcreteType ret = getTypeWithProperty(field, t);
          if (ret != null) {
            return ret;
          }
        }
      }
      return null;
    }

    @Override public ConcreteType getInstanceFromPrototype(ConcreteType type) {
      if (type.isInstance()) {
        ConcreteInstanceType instanceType = (ConcreteInstanceType) type;
        if (instanceType.isFunctionPrototype()) {
          return instanceType.getConstructorType().getInstanceType();
        }
      }
      return null;
    }

    @Override
    public void recordInterfaces(ConcreteType type, ConcreteType relatedType,
        DisambiguateProperties<ConcreteType>.Property p) {
      // No need to record interfaces when using concrete types.
    }
  }
}
