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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.TypeValidator.TypeMismatch;
import com.google.javascript.jscomp.graph.StandardUnionFind;
import com.google.javascript.jscomp.graph.UnionFind;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticScope;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Logger;
import java.util.regex.Pattern;

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
  // To prevent the logs from filling up, we cap the number of warnings
  // that we tell the user to fix per-property.
  private static final int MAX_INVALDIATION_WARNINGS_PER_PROPERTY = 10;

  private static final Logger logger = Logger.getLogger(
      DisambiguateProperties.class.getName());
  private static final Pattern NONWORD_PATTERN = Pattern.compile("[^\\w$]");

  static class Warnings {
    // TODO(user): {1} and {2} are not exactly useful for most people.
    static final DiagnosticType INVALIDATION = DiagnosticType.disabled(
        "JSC_INVALIDATION",
        "Property disambiguator skipping all instances of property {0} "
        + "because of type {1} node {2}. {3}");

    static final DiagnosticType INVALIDATION_ON_TYPE = DiagnosticType.disabled(
        "JSC_INVALIDATION_TYPE",
        "Property disambiguator skipping instances of property {0} "
        + "on type {1}. {2}");
  }

  private final AbstractCompiler compiler;
  private final TypeSystem<T> typeSystem;

  /**
   * Map of a type to all the related errors that invalidated the type
   * for disambiguation. It has be Object because of the generic nature of
   * this pass.
   */
  private Multimap<Object, JSError> invalidationMap;

  /**
   * In practice any large code base will have thousands and thousands of
   * type invalidations, which makes reporting all of the errors useless.
   * However, certain properties are worth specifically guarding because of the
   * large amount of code that can be removed as dead code. This list contains
   * the properties (eg: "toString") that we care about; if any of these
   * properties is invalidated it causes an error.
   */
  private final Map<String, CheckLevel> propertiesToErrorFor;

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
        types = new StandardUnionFind<>();
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
      AbstractCompiler compiler,
      Map<String, CheckLevel> propertiesToErrorFor) {
    return new DisambiguateProperties<>(
        compiler, new JSTypeSystem(compiler), propertiesToErrorFor);
  }

  /**
   * This constructor should only be called by one of the helper functions
   * above for either the JSType system, or the concrete type system.
   */
  private DisambiguateProperties(AbstractCompiler compiler,
      TypeSystem<T> typeSystem, Map<String, CheckLevel> propertiesToErrorFor) {
    this.compiler = compiler;
    this.typeSystem = typeSystem;
    this.propertiesToErrorFor = propertiesToErrorFor;
    if (!this.propertiesToErrorFor.isEmpty()) {
      this.invalidationMap = LinkedHashMultimap.create();
    } else {
      this.invalidationMap = null;
    }
  }

  @Override
  public void process(Node externs, Node root) {
    Preconditions.checkState(
        compiler.getLifeCycleStage() == LifeCycleStage.NORMALIZED);
    for (TypeMismatch mis : compiler.getTypeValidator().getMismatches()) {
      addInvalidatingType(mis.typeA, mis.src);
      addInvalidatingType(mis.typeB, mis.src);
    }

    NodeTraversal.traverse(compiler, externs, new FindExternProperties());
    NodeTraversal.traverse(compiler, root, new FindRenameableProperties());
    renameProperties();
  }

  private void recordInvalidationError(JSType t, JSError error) {
    if (!t.isObject()) {
      return;
    }
    if (invalidationMap != null) {
      invalidationMap.put(t, error);
    }
  }

  /**
   * Invalidates the given type, so that no properties on it will be renamed.
   */
  private void addInvalidatingType(JSType type, JSError error) {
    type = type.restrictByNotNullOrUndefined();
    if (type.isUnionType()) {
      for (JSType alt : type.toMaybeUnionType().getAlternates()) {
        addInvalidatingType(alt, error);
      }
    } else if (type.isEnumElementType()) {
      addInvalidatingType(
          type.toMaybeEnumElementType().getPrimitiveType(), error);
    } else {
      typeSystem.addInvalidatingType(type);
      recordInvalidationError(type, error);
      ObjectType objType = ObjectType.cast(type);
      if (objType != null && objType.getImplicitPrototype() != null) {
        typeSystem.addInvalidatingType(objType.getImplicitPrototype());
        recordInvalidationError(objType.getImplicitPrototype(), error);
      }
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
        new Stack<>();

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return true;
    }

    @Override
    public void enterScope(NodeTraversal t) {
      if (t.inGlobalScope()) {
        scopes.push(typeSystem.getRootScope());
      } else {
        scopes.push(typeSystem.getFunctionScope(t.getScopeRoot()));
      }
    }

    @Override
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
      // TODO(johnlenz): Support object-literal property definitions.
      if (n.isGetProp()) {
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
      if (n.isGetProp()) {
        handleGetProp(t, n);
      } else if (n.isObjectLit()) {
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
        if (propertiesToErrorFor.containsKey(name)) {
          String suggestion = "";
          if (type instanceof JSType) {
            JSType jsType = (JSType) type;
            if (jsType.isAllType() || jsType.isUnknownType()) {
              if (n.getFirstChild().isThis()) {
                suggestion = "The \"this\" object is unknown in the function," +
                    "consider using @this";
              } else {
                String qName = n.getFirstChild().getQualifiedName();
                suggestion = "Consider casting " + qName +
                    " if you know it's type.";
              }
            } else {
              List<String> errors = Lists.newArrayList();
              printErrorLocations(errors, jsType);
              if (!errors.isEmpty()) {
                suggestion = "Consider fixing errors for the following types:\n";
                suggestion += Joiner.on("\n").join(errors);
              }
            }
          }
          compiler.report(JSError.make(
              n, propertiesToErrorFor.get(name),
              Warnings.INVALIDATION, name,
              (type == null ? "null" : type.toString()),
              n.toString(), suggestion));
        }
      }
    }

    /**
     * Processes a OBJECTLIT node.
     */
    private void handleObjectLit(NodeTraversal t, Node n) {
      for (Node child = n.getFirstChild();
          child != null;
          child = child.getNext()) {
        // Maybe STRING, GET, SET
        if (child.isQuotedString()) {
          continue;
        }

        // We should never see a mix of numbers and strings.
        String name = child.getString();
        T type = typeSystem.getType(getScope(), n, name);

        Property prop = getProperty(name);
        if (!prop.scheduleRenaming(child,
                                   processProperty(t, prop, type, null))) {
          // TODO(user): It doesn't look like the user can do much in this
          // case right now.
          if (propertiesToErrorFor.containsKey(name)) {
            compiler.report(JSError.make(
                child, propertiesToErrorFor.get(name),
                Warnings.INVALIDATION, name,
                (type == null ? "null" : type.toString()), n.toString(), ""));
          }
        }
      }
    }

    private void printErrorLocations(List<String> errors, JSType t) {
      if (!t.isObject() || t.isAllType()) {
        return;
      }

      if (t.isUnionType()) {
        for (JSType alt : t.toMaybeUnionType().getAlternates()) {
          printErrorLocations(errors, alt);
        }
        return;
      }

      for (JSError error : invalidationMap.get(t)) {
        if (errors.size() > MAX_INVALDIATION_WARNINGS_PER_PROPERTY) {
          return;
        }

        errors.add(
            t.toString() + " at " + error.sourceName + ":" + error.lineNumber);
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

    Set<String> reported = Sets.newHashSet();
    for (Property prop : properties.values()) {
      if (prop.shouldRename()) {
        Map<T, String> propNames = buildPropNames(prop.getTypes(), prop.name);

        ++propsRenamed;
        prop.expandTypesToSkip();
        for (Node node : prop.renameNodes) {
          T rootType = prop.rootTypes.get(node);
          if (prop.shouldRename(rootType)) {
            String newName = propNames.get(rootType);
            node.setString(newName);
            compiler.reportCodeChange();
            ++instancesRenamed;
          } else {
            ++instancesSkipped;

            CheckLevel checkLevelForProp = propertiesToErrorFor.get(prop.name);
            if (checkLevelForProp != null &&
                checkLevelForProp != CheckLevel.OFF &&
                !reported.contains(prop.name)) {
              reported.add(prop.name);
              compiler.report(JSError.make(
                  node,
                  checkLevelForProp,
                  Warnings.INVALIDATION_ON_TYPE, prop.name,
                  rootType.toString(), ""));
            }
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
    logger.fine("Renamed " + instancesRenamed + " instances of "
                + propsRenamed + " properties.");
    logger.fine("Skipped renaming " + instancesSkipped + " invalidated "
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
        newName = NONWORD_PATTERN.matcher(typeName).replaceAll("_") + '$' + name;
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
    for (Map.Entry<String, Property> entry : properties.entrySet()) {
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
     * Returns true if a field reference on this type will invalidate all
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
      if (type.isUnionType()) {
        Set<JSType> types = Sets.newHashSet(type);
        for (JSType alt : type.toMaybeUnionType().getAlternates()) {
          types.addAll(getTypesToSkipForTypeNonUnion(alt));
        }
        return ImmutableSet.copyOf(types);
      } else if (type.isEnumElementType()) {
        return getTypesToSkipForType(
            type.toMaybeEnumElementType().getPrimitiveType());
      }
      return ImmutableSet.copyOf(getTypesToSkipForTypeNonUnion(type));
    }

    private static Set<JSType> getTypesToSkipForTypeNonUnion(JSType type) {
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
        return type.toMaybeUnionType().getAlternates();
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
      if (type == null) {
        return null;
      }

      if (type.isEnumElementType()) {
        return getTypeWithProperty(
            field, type.toMaybeEnumElementType().getPrimitiveType());
      }

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
      // this appears.  This will make references to overridden properties look
      // like references to the initial property, so they are renamed alike.
      ObjectType foundType = null;
      ObjectType objType = ObjectType.cast(type);
      if (objType != null && objType.getConstructor() != null
          && objType.getConstructor().isInterface()) {
        ObjectType topInterface = FunctionType.getTopDefiningInterface(
            objType, field);
        if (topInterface != null && topInterface.getConstructor() != null) {
          foundType = topInterface.getConstructor().getPrototype();
        }
      } else {
        while (objType != null && objType.getImplicitPrototype() != objType) {
          if (objType.hasOwnProperty(field)) {
            foundType = objType;
          }
          objType = objType.getImplicitPrototype();
        }
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

      // Unwrap templatized types, they are not unique at runtime.
      if (foundType != null && foundType.isTemplatizedType()) {
        foundType = foundType.toMaybeTemplatizedType().getReferencedType();
      }

      return foundType;
    }

    @Override public JSType getInstanceFromPrototype(JSType type) {
      if (type.isFunctionPrototypeType()) {
        ObjectType prototype = (ObjectType) type;
        FunctionType owner = prototype.getOwnerFunction();
        if (owner.isConstructor() || owner.isInterface()) {
          return prototype.getOwnerFunction().getInstanceType();
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
        if (objType.isFunctionType()) {
          constructor = objType.toMaybeFunctionType();
        } else if (objType.isFunctionPrototypeType()) {
          constructor = objType.getOwnerFunction();
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
            if (p.skipRenaming) {
              return;
            }
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
}
