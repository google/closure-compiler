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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.NodeTraversal.AbstractScopedCallback;
import com.google.javascript.jscomp.TypeValidator.TypeMismatch;
import com.google.javascript.jscomp.graph.StandardUnionFind;
import com.google.javascript.jscomp.graph.UnionFind;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * NOTE(dimvar): For every property, this pass groups together the types that
 * can't be disambiguated. If a type inherits from another type, their common
 * properties can never be disambiguated, yet we have to compute this info once
 * per property rather than just once in the pass. This is where the bulk of the
 * time is spent.
 * We have added many caches that help a lot, but it is probably worth it to
 * revisit this pass and rewrite it in a way that does not compute the same
 * thing over and over.
 *
 */
class DisambiguateProperties implements CompilerPass {
  // To prevent the logs from filling up, we cap the number of warnings
  // that we tell the user to fix per-property.
  private static final int MAX_INVALIDATION_WARNINGS_PER_PROPERTY = 10;

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
  private final Set<JSType> invalidatingTypes;
  private final JSTypeRegistry registry;
  // Used as a substitute for null in gtwpCache. The method gtwpCacheGet returns
  // null to indicate that an element wasn't present.
  private final ObjectType BOTTOM_OBJECT;

  /**
   * Map of a type to all the related errors that invalidated the type
   * for disambiguation. It has to be Object because of the generic nature of
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

  // FunctionType#getImplementedInterfaces() is slow, so we use this cache
  // to call it just once per constructor.
  private Map<FunctionType, Iterable<ObjectType>> implementedInterfaces;

  // Cache calls to getTypeWithProperty.
  private Map<String, IdentityHashMap<JSType, ObjectType>> gtwpCache;

  private ObjectType gtwpCacheGet(String field, JSType type) {
    IdentityHashMap<JSType, ObjectType> m = gtwpCache.get(field);
    return m == null ? null : m.get(type);
  }

  private void gtwpCachePut(String field, JSType type, ObjectType top) {
    IdentityHashMap<JSType, ObjectType> m = gtwpCache.get(field);
    if (m == null) {
      m = new IdentityHashMap<>();
      gtwpCache.put(field, m);
    }
    Preconditions.checkState(null == m.put(type, top));
  }

  private class Property {
    /** The name of the property. */
    final String name;

    /**
     * All top types on which the field exists, grouped together if related.
     * See getTypeWithProperty. If a property exists on a parent class and a
     * subclass, only the parent class is recorded here.
     */
    private UnionFind<JSType> types;

    /**
     * A set of types for which renaming this field should be skipped. This
     * list is first filled by fields defined in the externs file.
     */
    Set<JSType> typesToSkip = new HashSet<>();

    /**
     * If true, do not rename any instance of this field, as it has been
     * referenced from an unknown type.
     */
    boolean skipRenaming;

    /**
     * A map from nodes that need renaming to the highest type in the prototype
     * chain containing the field for each node. In the case of a union, the
     * type is the highest type of one of the types in the union.
     */
    Map<Node, JSType> rootTypesByNode = new HashMap<>();

    /**
     * For every property p and type t, we only need to run recordInterfaces
     * once. Use this cache to avoid needless calls.
     */
    private final Set<JSType> recordInterfacesCache = new HashSet<>();

    Property(String name) {
      this.name = name;
    }

    /** Returns the types on which this field is referenced. */
    UnionFind<JSType> getTypes() {
      if (types == null) {
        types = new StandardUnionFind<>();
      }
      return types;
    }

    /**
     * Record that this property is referenced from this type.
     */
    void addType(JSType type, JSType relatedType) {
      checkState(!skipRenaming, "Attempt to record skipped property: %s", name);
      JSType top = getTypeWithProperty(this.name, type);
      if (isInvalidatingType(top)) {
        invalidate();
        return;
      }
      if (isTypeToSkip(top)) {
        addTypeToSkip(top);
      }
      if (relatedType == null) {
        getTypes().add(top);
      } else {
        getTypes().union(top, relatedType);
      }
      FunctionType constructor = getConstructor(type);
      if (constructor != null && recordInterfacesCache.add(type)) {
        recordInterfaces(constructor, top, this);
      }
    }

    /** Records the given type as one to skip for this property. */
    void addTypeToSkip(JSType type) {
      for (JSType skipType : getTypesToSkipForType(type)) {
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
          Set<JSType> rootTypesToSkip = new HashSet<>();
          for (JSType subType : typesToSkip) {
            rootTypesToSkip.add(types.find(subType));
          }
          typesToSkip.addAll(rootTypesToSkip);

          Set<JSType> newTypesToSkip = new HashSet<>();
          Set<JSType> allTypes = types.elements();
          int originalTypesSize = allTypes.size();
          for (JSType subType : allTypes) {
            if (!typesToSkip.contains(subType)
                && typesToSkip.contains(types.find(subType))) {
              newTypesToSkip.add(subType);
            }
          }

          for (JSType newType : newTypesToSkip) {
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
    boolean shouldRename(JSType type) {
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
      typesToSkip = null;
      rootTypesByNode = null;
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
    boolean scheduleRenaming(Node node, JSType type) {
      if (!skipRenaming) {
        if (isInvalidatingType(type)) {
          invalidate();
          return false;
        }
        rootTypesByNode.put(node, type);
      }
      return true;
    }
  }

  private Map<String, Property> properties = new HashMap<>();

  DisambiguateProperties(
      AbstractCompiler compiler, Map<String, CheckLevel> propertiesToErrorFor) {
    this.compiler = compiler;
    this.registry = compiler.getTypeRegistry();
    this.BOTTOM_OBJECT =
        this.registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE).toObjectType();
    this.invalidatingTypes = new HashSet<>(ImmutableSet.of(
        registry.getNativeType(JSTypeNative.ALL_TYPE),
        registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
        registry.getNativeType(JSTypeNative.NO_TYPE),
        registry.getNativeType(JSTypeNative.FUNCTION_PROTOTYPE),
        registry.getNativeType(JSTypeNative.FUNCTION_INSTANCE_TYPE),
        registry.getNativeType(JSTypeNative.OBJECT_PROTOTYPE),
        registry.getNativeType(JSTypeNative.TOP_LEVEL_PROTOTYPE),
        registry.getNativeType(JSTypeNative.UNKNOWN_TYPE)));
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
    this.implementedInterfaces = new HashMap<>();
    this.gtwpCache = new HashMap<>();
    // TypeValidator records places where a type A is used in a context that
    // expects a type B.
    // For each pair (A, B), here we mark both A and B as types whose properties
    // cannot be renamed.
    for (TypeMismatch mis : compiler.getTypeMismatches()) {
      recordInvalidatingType(mis.typeA, mis.src);
      recordInvalidatingType(mis.typeB, mis.src);
    }
    for (TypeMismatch mis : compiler.getImplicitInterfaceUses()) {
      recordInvalidatingType(mis.typeA, mis.src);
      recordInvalidatingType(mis.typeB, mis.src);
    }
    // Gather names of properties in externs; these properties can't be renamed.
    NodeTraversal.traverseEs6(compiler, externs, new FindExternProperties());
    // Look at each unquoted property access and decide if that property will
    // be renamed.
    NodeTraversal.traverseEs6(compiler, root, new FindRenameableProperties());
    // Do the actual renaming.
    renameProperties();
  }

  private void recordInvalidationError(JSType t, JSError error) {
    if (!t.isObject()) {
      return;
    }
    if (invalidationMap != null) {
      Collection<JSError> errors = this.invalidationMap.get(t);
      if (errors.size() < MAX_INVALIDATION_WARNINGS_PER_PROPERTY) {
        errors.add(error);
      }
    }
  }

  /**
   * Invalidates the given type, so that no properties on it will be renamed.
   */
  private void recordInvalidatingType(JSType type, JSError error) {
    type = type.restrictByNotNullOrUndefined();
    if (type.isUnionType()) {
      for (JSType alt : type.toMaybeUnionType().getAlternatesWithoutStructuralTyping()) {
        recordInvalidatingType(alt, error);
      }
    } else if (type.isEnumElementType()) {
      recordInvalidatingType(
          type.toMaybeEnumElementType().getPrimitiveType(), error);
    } else {
      addInvalidatingType(type);
      recordInvalidationError(type, error);
      ObjectType objType = ObjectType.cast(type);
      if (objType != null && objType.getImplicitPrototype() != null) {
        addInvalidatingType(objType.getImplicitPrototype());
        recordInvalidationError(objType.getImplicitPrototype(), error);
      }
      if (objType != null
          && objType.isConstructor() && objType.isFunctionType()) {
        addInvalidatingType(objType.toMaybeFunctionType().getInstanceType());
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

  /**
   * Finds all properties defined in the externs file and sets them as
   * ineligible for renaming from the type on which they are defined.
   */
  private class FindExternProperties extends AbstractScopedCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      // TODO(johnlenz): Support object-literal property definitions.
      if (n.isGetProp()) {
        String field = n.getLastChild().getString();
        JSType type = getType(n.getFirstChild());
        Property prop = getProperty(field);
        if (isInvalidatingType(type) || isStructuralInterfacePrototype(type)) {
          prop.invalidate();
        } else if (!prop.skipRenaming) {
          prop.addTypeToSkip(type);
          // If this is a prototype property, then we want to skip assignments
          // to the instance type as well.  These assignments are not usually
          // seen in the extern code itself, so we must handle them here.
          if ((type = getInstanceFromPrototype(type)) != null) {
            prop.getTypes().add(type);
            prop.typesToSkip.add(type);
          }
        }
      }
    }

    private boolean isStructuralInterfacePrototype(JSType type) {
      return type.isFunctionPrototypeType()
          && type.toObjectType().getOwnerFunction().isStructuralInterface();
    }
  }

  /**
   * Traverses the tree, building a map from field names to Nodes for all
   * fields that can be renamed.
   */
  private class FindRenameableProperties extends AbstractScopedCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isGetProp()) {
        handleGetProp(t, n);
      } else if (n.isObjectLit()) {
        handleObjectLit(t, n);
      }
    }

    private void handleGetProp(NodeTraversal t, Node n) {
      String name = n.getLastChild().getString();
      JSType type = getType(n.getFirstChild());

      Property prop = getProperty(name);
      if (!prop.scheduleRenaming(
             n.getLastChild(),
             processProperty(t, prop, type, null))
          && propertiesToErrorFor.containsKey(name)) {
        String suggestion = "";
        if (type instanceof JSType) {
          JSType jsType = (JSType) type;
          if (jsType.isAllType() || jsType.isUnknownType()) {
            if (n.getFirstChild().isThis()) {
              suggestion = "The \"this\" object is unknown in the function," +
                    "consider using @this";
            } else {
              String qName = n.getFirstChild().getQualifiedName();
              suggestion = "Consider casting " + qName + " if you know its type.";
            }
          } else {
            List<String> errors = new ArrayList<>();
            printErrorLocations(errors, jsType);
            if (!errors.isEmpty()) {
              suggestion = "Consider fixing errors for the following types:\n";
              suggestion += Joiner.on("\n").join(errors);
            }
          }
        }
        compiler.report(JSError.make(n, propertiesToErrorFor.get(name),
                Warnings.INVALIDATION, name, String.valueOf(type), n.toString(),
                suggestion));
      }
    }

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
        JSType type = getType(n);

        Property prop = getProperty(name);
        if (!prop.scheduleRenaming(child,
                                   processProperty(t, prop, type, null))) {
          // TODO(user): It doesn't look like the user can do much in this
          // case right now.
          if (propertiesToErrorFor.containsKey(name)) {
            compiler.report(JSError.make(child, propertiesToErrorFor.get(name),
                Warnings.INVALIDATION, name, String.valueOf(type), n.toString(), ""));
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
        errors.add(t + " at " + error.sourceName + ":" + error.lineNumber);
      }
    }

    /**
     * Processes a property, adding it to the list of properties to rename.
     * @return a representative type for the property reference, which will be
     *   the highest type on the prototype chain of the provided type.  In the
     *   case of a union type, it will be the highest type on the prototype
     *   chain of one of the members of the union.
     */
    private JSType processProperty(
        NodeTraversal t, Property prop, JSType type, JSType relatedType) {
      type = type.restrictByNotNullOrUndefined();
      if (prop.skipRenaming || isInvalidatingType(type)) {
        return null;
      }

      Iterable<JSType> alternatives = getTypeAlternatives(type);
      if (alternatives != null) {
        JSType firstType = relatedType;
        for (JSType subType : alternatives) {
          JSType lastType = processProperty(t, prop, subType, firstType);
          if (lastType != null) {
            firstType = firstType == null ? lastType : firstType;
          }
        }
        return firstType;
      } else {
        JSType topType = getTypeWithProperty(prop.name, type);
        if (isInvalidatingType(topType)) {
          return null;
        }
        prop.addType(type, relatedType);
        return topType;
      }
    }
  }

  /** Renames all properties with references on more than one type. */
  void renameProperties() {
    int propsRenamed = 0, propsSkipped = 0, instancesRenamed = 0,
        instancesSkipped = 0, singleTypeProps = 0;

    Set<String> reported = new HashSet<>();
    for (Property prop : properties.values()) {
      if (prop.shouldRename()) {
        UnionFind<JSType> pTypes = prop.getTypes();
        Map<JSType, String> propNames = buildPropNames(prop);

        ++propsRenamed;
        prop.expandTypesToSkip();
        // This loop has poor locality, because instead of walking the AST,
        // we iterate over all accesses of a property, which can be in very
        // different places in the code.
        for (Map.Entry<Node, JSType> entry : prop.rootTypesByNode.entrySet()) {
          Node node = entry.getKey();
          JSType rootType = entry.getValue();
          if (prop.shouldRename(rootType)) {
            String newName = propNames.get(pTypes.find(rootType));
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
   * the representative type of that class to that name.
   */
  private Map<JSType, String> buildPropNames(Property prop) {
    UnionFind<JSType> pTypes = prop.getTypes();
    String pname = prop.name;
    Map<JSType, String> names = new HashMap<>();
    for (Set<JSType> set : pTypes.allEquivalenceClasses()) {
      checkState(!set.isEmpty());
      JSType representative = pTypes.find(set.iterator().next());
      String typeName = null;
      for (JSType type : set) {
        String typeString = type.toString();
        if (typeName == null || typeString.compareTo(typeName) < 0) {
          typeName = typeString;
        }
      }
      String newName;
      if ("{...}".equals(typeName)) {
        newName = pname;
      } else {
        newName = NONWORD_PATTERN.matcher(typeName).replaceAll("_") + '$' + pname;
      }
      names.put(representative, newName);
    }
    return names;
  }

  /** Returns a map from field name to types for which it will be renamed. */
  Multimap<String, Collection<JSType>> getRenamedTypesForTesting() {
    Multimap<String, Collection<JSType>> ret = HashMultimap.create();
    for (Map.Entry<String, Property> entry : properties.entrySet()) {
      Property prop = entry.getValue();
      if (!prop.skipRenaming) {
        for (Collection<JSType> c : prop.getTypes().allEquivalenceClasses()) {
          if (!c.isEmpty() && !prop.typesToSkip.contains(c.iterator().next())) {
            ret.put(entry.getKey(), c);
          }
        }
      }
    }
    return ret;
  }

  private void addInvalidatingType(JSType type) {
    checkState(!type.isUnionType());
    invalidatingTypes.add(type);
  }

  private JSType getType(Node node) {
    if (node.getJSType() == null) {
      return registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
    }
    return node.getJSType();
  }

  /**
   * Returns true if a field reference on this type will invalidate all
   * references to that field as candidates for renaming. This is true if the
   * type is unknown or all-inclusive, as variables with such a type could be
   * references to any object.
   */
  private boolean isInvalidatingType(JSType type) {
    if (type == null || invalidatingTypes.contains(type) ||
        type.isUnknownType() /* unresolved types */) {
      return true;
    }
    ObjectType objType = ObjectType.cast(type);
    return objType != null && !objType.hasReferenceName();
  }

  /**
   * Returns a set of types that should be skipped given the given type. This is
   * necessary for interfaces, as all super interfaces must also be skipped.
   */
  private ImmutableSet<JSType> getTypesToSkipForType(JSType type) {
    type = type.restrictByNotNullOrUndefined();
    if (type.isUnionType()) {
      ImmutableSet.Builder<JSType> types = ImmutableSet.builder();
      types.add(type);
      for (JSType alt : type.toMaybeUnionType().getAlternates()) {
        types.addAll(getTypesToSkipForTypeNonUnion(alt));
      }
      return types.build();
    } else if (type.isEnumElementType()) {
      return getTypesToSkipForType(
          type.toMaybeEnumElementType().getPrimitiveType());
    }
    return ImmutableSet.copyOf(getTypesToSkipForTypeNonUnion(type));
  }

  private Set<JSType> getTypesToSkipForTypeNonUnion(JSType type) {
    Set<JSType> types = new HashSet<>();
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

  /**
   * Determines whether the given type is one whose properties should not be
   * considered for renaming.
   */
  private boolean isTypeToSkip(JSType type) {
    return type.isEnumType() || (type.autoboxesTo() != null);
  }

  /**
   * Returns the alternatives if this is a type that represents multiple
   * types, and null if not. Union and interface types can correspond to
   * multiple other types.
   */
  private Iterable<JSType> getTypeAlternatives(JSType type) {
    if (type.isUnionType()) {
      return type.toMaybeUnionType().getAlternatesWithoutStructuralTyping();
    } else {
      ObjectType objType = type.toObjectType();
      if (objType != null &&
          objType.getConstructor() != null &&
          objType.getConstructor().isInterface()) {
        List<JSType> list = new ArrayList<>();
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

  /**
   * Returns the type in the chain from the given type that contains the given
   * field or null if it is not found anywhere.
   * Can return a subtype of the input type.
   */
  @VisibleForTesting
  ObjectType getTypeWithProperty(String field, JSType type) {
    if (type == null) {
      return null;
    }

    ObjectType foundType = gtwpCacheGet(field, type);
    if (foundType != null) {
      return foundType.equals(BOTTOM_OBJECT) ? null : foundType;
    }

    if (type.isEnumElementType()) {
      foundType = getTypeWithProperty(
          field, type.toMaybeEnumElementType().getPrimitiveType());
      gtwpCachePut(field, type, foundType == null ? BOTTOM_OBJECT : foundType);
      return foundType;
    }

    if (!(type instanceof ObjectType)) {
      if (type.autoboxesTo() != null) {
        foundType = getTypeWithProperty(field, type.autoboxesTo());
        gtwpCachePut(field, type, foundType == null ? BOTTOM_OBJECT : foundType);
        return foundType;
      } else {
        gtwpCachePut(field, type, BOTTOM_OBJECT);
        return null;
      }
    }

    // Ignore the prototype itself at all times.
    if ("prototype".equals(field)) {
      gtwpCachePut(field, type, BOTTOM_OBJECT);
      return null;
    }

    // We look up the prototype chain to find the highest place (if any) that
    // this appears.  This will make references to overridden properties look
    // like references to the initial property, so they are renamed alike.
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

    // Since disambiguation just looks at names, we must return a uniquely named type rather
    // than an "equivalent" type. In particular, we must manually unwrap named types
    // so that the returned type has the correct name.
    if (foundType != null && foundType.isNamedType()) {
      foundType = foundType.toMaybeNamedType().getReferencedType().toMaybeObjectType();
    }

    gtwpCachePut(field, type, foundType == null ? BOTTOM_OBJECT : foundType);
    return foundType;
  }

  private JSType getInstanceFromPrototype(JSType type) {
    if (type.isFunctionPrototypeType()) {
      ObjectType prototype = (ObjectType) type;
      FunctionType owner = prototype.getOwnerFunction();
      if (owner.isConstructor() || owner.isInterface()) {
        return prototype.getOwnerFunction().getInstanceType();
      }
    }
    return null;
  }

  /**
   * Records that this property could be referenced from any interface that
   * this type, or any type in its superclass chain, implements.
   *
   * If the property p is defined only on a subtype of constructor, then this
   * method has no effect. But we tried modifying getTypeWithProperty to tell us
   * when the returned type is a subtype, and then skip those calls to
   * recordInterface, and there was no speed-up.
   * And it made the code harder to understand, so we don't do it.
   */
  private void recordInterfaces(FunctionType constructor, JSType relatedType,
      DisambiguateProperties.Property p) {
    Preconditions.checkArgument(constructor.isConstructor());
    Iterable<ObjectType> interfaces = implementedInterfaces.get(constructor);
    if (interfaces == null) {
      interfaces = constructor.getImplementedInterfaces();
      implementedInterfaces.put(constructor, interfaces);
    }
    for (ObjectType itype : interfaces) {
      JSType top = getTypeWithProperty(p.name, itype);
      if (top != null) {
        p.addType(itype, relatedType);
      }
      // If this interface invalidated this property, return now.
      if (p.skipRenaming) {
        return;
      }
    }
  }

  private FunctionType getConstructor(JSType type) {
    ObjectType objType = ObjectType.cast(type);
    if (objType == null) {
      return null;
    }
    FunctionType constructor = null;
    if (objType.isFunctionType()) {
      constructor = objType.toMaybeFunctionType();
    } else if (objType.isFunctionPrototypeType()) {
      constructor = objType.getOwnerFunction();
    } else {
      constructor = objType.getConstructor();
    }
    return constructor != null && constructor.isConstructor()
        ? constructor : null;
  }
}
