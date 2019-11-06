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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.NodeTraversal.AbstractScopedCallback;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

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
 *   Foo.Foo$a;
 *   Bar.Bar$a;
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
 */
class DisambiguateProperties implements CompilerPass {
  // To prevent the logs from filling up, we cap the number of warnings
  // that we tell the user to fix per-property.
  private static final int MAX_INVALIDATION_WARNINGS_PER_PROPERTY = 10;

  private static final Logger logger = Logger.getLogger(
      DisambiguateProperties.class.getName());
  private static final Pattern NONWORD_PATTERN = Pattern.compile("[^\\w$]");

  private final AbstractCompiler compiler;

  private final InvalidatingTypes invalidatingTypes;
  private final JSTypeRegistry registry;

  /**
   * Map of a type to all the related errors that invalidated the type
   * for disambiguation.
   */
  private final Multimap<JSType, Supplier<JSError>> invalidationMap;

  /**
   * In practice any large code base will have thousands and thousands of
   * type invalidations, which makes reporting all of the errors useless.
   * However, certain properties are worth specifically guarding because of the
   * large amount of code that can be removed as dead code. This list contains
   * the properties (eg: "toString") that we care about; if any of these
   * properties is invalidated it causes an error.
   */
  private final Map<String, CheckLevel> propertiesToErrorFor;

  // Use this cache to call FunctionType#getImplementedInterfaces
  // or FunctionType#getExtendedInterfaces only once per constructor.
  private Map<FunctionType, Iterable<ObjectType>> ancestorInterfaces;

  // Cache calls to getRepresentativeType.
  private final Table<String, JSType, ObjectType> representativeTypeCache =
      Tables.newCustomTable(new LinkedHashMap<>(), IdentityHashMap::new);

  /**
   * A sentinel value for cached computations of {@link #getRepresentativeType} which returned
   * {@code null}.
   *
   * <p>Use of a sentinel allows the value in {@link #representativeTypeCache} to encode that there
   * is no useful result, obviating the need to first check {@code Table#contains}. That is, it
   * ensures that within the cache, {@code null} always means "not yet computed".
   */
  private final ObjectType representativeTypeSentinel;

  private class Property {
    /** The name of the property. */
    final String name;

    /**
     * All top types on which the field exists, grouped together if related. See
     * getRepresentativeType. If a property exists on a parent class and a subclass, only the parent
     * class is recorded here.
     */
    private UnionFind<JSType> types;

    /**
     * A set of types for which renaming this field should be skipped. This
     * list is first filled by fields defined in the externs file.
     */
    Set<JSType> typesToSkip = new HashSet<>();

    /**
     * If false, do not rename any instance of this field, as it has been referenced in an
     * untracable way.
     */
    boolean isValidForRenaming = true;

    /**
     * A map from nodes that need renaming to the highest type in the prototype
     * chain containing the field for each node. In the case of a union, the
     * type is the highest type of one of the types in the union.
     */
    Map<Node, JSType> rootTypesByNode = new LinkedHashMap<>();

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
      checkState(this.isValidForRenaming, "Attempt to record an invalidated property: %s", name);
      JSType top = getRepresentativeType(this.name, type);
      if (invalidatingTypes.isInvalidating(top)) {
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

      recordInterfaces(type, top);
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
      if (!this.wouldBeDisambiguatedByRenaming()) {
        return;
      }

      for (int count = 0; count < 10; count++) {
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
          if (!typesToSkip.contains(subType) && typesToSkip.contains(types.find(subType))) {
            newTypesToSkip.add(subType);
          }
        }

        for (JSType newType : newTypesToSkip) {
          addTypeToSkip(newType);
        }

        // If there were not any new types added, we are done here.
        if (types.elements().size() == originalTypesSize) {
          return;
        }
      }

      throw new IllegalStateException("Stuck in loop expanding types to skip.");
    }

    /** Returns true if any instance of this property should be renamed. */
    boolean wouldBeDisambiguatedByRenaming() {
      return this.isValidForRenaming && types != null && types.allEquivalenceClasses().size() > 1;
    }

    /**
     * Returns true if this property should be renamed on this type. expandTypesToSkip() should be
     * called before this, if anything has been added to the typesToSkip list.
     */
    boolean shouldRenameOnType(JSType type) {
      return this.isValidForRenaming && !typesToSkip.contains(type);
    }

    /**
     * Invalidates a field from renaming.  Used for field references on an
     * object with unknown type.
     */
    boolean invalidate() {
      boolean changed = this.isValidForRenaming;
      this.isValidForRenaming = false;
      types = null;
      typesToSkip = null;
      rootTypesByNode = null;
      return changed;
    }

    /**
     * Schedule the node to potentially be renamed.
     *
     * @param node the node to rename
     * @return True if type was accepted without invalidation or if the property was already
     *     invalidated. False if this property was invalidated this time.
     */
    boolean scheduleRenaming(Node node, JSType targetType) {
      JSType type = processProperty(targetType);
      if (this.isValidForRenaming) {
        if (invalidatingTypes.isInvalidating(type)) {
          invalidate();
          return false;
        }
        rootTypesByNode.put(node, type);
      }
      return true;
    }

    @Nullable
    private JSType processProperty(JSType type) {
      return processProperty(type, null);
    }

    /**
     * Processes a property, adding it to the list of properties to rename.
     *
     * @param type a type this property is known to be on, not necessarily the highest type
     * @param relatedType only for use inside this function, other callers should pass null.
     * @return a representative type for the property reference, which will be the highest type on
     *     the prototype chain of the provided type. In the case of a union type, it will be the
     *     highest type on the prototype chain of one of the members of the union. Returns null if
     *     the property is marked as not renamable or the given type is invalidated
     */
    @Nullable
    private JSType processProperty(JSType type, @Nullable JSType relatedType) {
      type = type.restrictByNotNullOrUndefined();
      if (!this.isValidForRenaming || invalidatingTypes.isInvalidating(type)) {
        return null;
      }
      Iterable<? extends JSType> alternatives = getTypeAlternatives(type);
      if (alternatives != null) {
        JSType firstType = relatedType;
        for (JSType subType : alternatives) {
          JSType lastType = processProperty(subType, firstType);
          if (firstType == null) {
            firstType = lastType; // maybe null
          }
        }
        return firstType;
      } else {
        JSType topType = getRepresentativeType(this.name, type);
        if (invalidatingTypes.isInvalidating(topType)) {
          return null;
        }
        this.addType(type, relatedType);
        return topType;
      }
    }

    /**
     * Records that this property could be referenced from any interface that this type inherits
     * from.
     *
     * <p>If the property p is defined only on a subtype of constructor, then this method has no
     * effect. But we tried modifying getRepresentativeType to tell us when the returned type is a
     * subtype, and then skip those calls to recordInterface, and there was no speed-up. And it made
     * the code harder to understand, so we don't do it.
     */
    private void recordInterfaces(JSType type, JSType relatedType) {
      @Nullable FunctionType constructor = getConstructor(type);
      if (constructor == null || !recordInterfacesCache.add(type)) {
        return;
      }

      Iterable<ObjectType> interfaces = ancestorInterfaces.get(constructor);
      if (interfaces == null) {
        interfaces = constructor.getAncestorInterfaces();
        ancestorInterfaces.put(constructor, interfaces);
      }
      for (ObjectType itype : interfaces) {
        JSType top = getRepresentativeType(name, itype);
        if (top != null) {
          addType(itype, relatedType);
        }
        // If this interface invalidated this property, return now.
        if (!this.isValidForRenaming) {
          return;
        }
      }
    }
  }

  private final Map<String, Property> properties = new LinkedHashMap<>();

  DisambiguateProperties(
      AbstractCompiler compiler, Map<String, CheckLevel> propertiesToErrorFor) {
    this.compiler = compiler;
    this.registry = compiler.getTypeRegistry();
    this.representativeTypeSentinel =
        this.registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE).toMaybeObjectType();

    this.propertiesToErrorFor = propertiesToErrorFor;
    this.invalidationMap = propertiesToErrorFor.isEmpty() ? null : LinkedHashMultimap.create();

    this.invalidatingTypes = new InvalidatingTypes.Builder(registry)
        .writeInvalidationsInto(this.invalidationMap)
        .addTypesInvalidForPropertyRenaming()
        .addAllTypeMismatches(compiler.getTypeMismatches())
        .addAllTypeMismatches(compiler.getImplicitInterfaceUses())
        .allowEnumsAndScalars()
        .build();
  }

  @Override
  public void process(Node externs, Node root) {
    checkState(compiler.getLifeCycleStage() == LifeCycleStage.NORMALIZED);
    this.ancestorInterfaces = new HashMap<>();
    // Gather names of properties in externs; these properties can't be renamed.
    NodeTraversal.traverse(compiler, externs, new FindExternProperties());
    // Look at each unquoted property access and decide if that property will
    // be renamed.
    NodeTraversal.traverse(compiler, root, new FindRenameableProperties());
    // Do the actual renaming.
    renameProperties();
    // Update any getters and setters we renamed.
    GatherGetterAndSetterProperties.update(compiler, externs, root);
  }

  /** Returns the property for the given name, creating it if necessary. */
  protected Property getProperty(String name) {
    if (!properties.containsKey(name)) {
      properties.put(name, new Property(name));
    }
    return properties.get(name);
  }

  /**
   * Finds all properties defined in the externs file and sets them as ineligible for renaming from
   * the type on which they are defined.
   */
  private class FindExternProperties extends AbstractScopedCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      // TODO(johnlenz): Support object-literal property definitions.
      if (n.isGetProp()) {
        Node recv = n.getFirstChild();
        JSType recvType = getType(recv);
        Property prop = getProperty(n.getLastChild().getString());
        processExternsProperty(prop, recvType);
      } else if (n.isClass()) {
        JSType classType = n.getJSType();
        JSType classInstanceType =
            // the class type may not be a function type if it was in a cast, so treat it as unknown
            classType.toMaybeFunctionType() != null
                ? classType.toMaybeFunctionType().getPrototype()
                : registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);

        for (Node member : NodeUtil.getClassMembers(n).children()) {
          // possibilities are:
          //   MEMBER_FUNCTION_DEF (could be static)
          //   COMPUTED_PROP (ignore)
          //   GETTER_DEF (could be static)
          //   SETTER_DEF (could be static)
          if (member.isComputedProp() || member.isQuotedString()) {
            // TODO(b/119682883): consider handling symbols here
            continue;
          }
          String name = member.getString();
          JSType ownerType = member.isStaticMember() ? classType : classInstanceType;

          Property prop = getProperty(name);
          processExternsProperty(prop, ownerType);
        }
      }
    }

    /**
     * Marks an externs property as not-renamable, and possibly invalidates the property based on
     * the type it is on
     *
     * @param ownerType The type of the object the property is on (e.g. `ns.a` for `ns.a.MyType;`)
     */
    private void processExternsProperty(Property prop, JSType ownerType) {
      if (invalidatingTypes.isInvalidating(ownerType)
          // TODO(b/119886075): invalidating here when isStructuralInterfacePrototype is true is
          // kind of arbitrary. We should only do it when the @record is implicitly implemented.
          || isStructuralInterfacePrototype(ownerType)) {
        prop.invalidate();
      } else if (prop.isValidForRenaming) {
        prop.addTypeToSkip(ownerType);
        // If this is a prototype property, then we want to skip assignments
        // to the instance type as well.  These assignments are not usually
        // seen in the extern code itself, so we must handle them here.
        JSType instanceType = getInstanceIfPrototype(ownerType);
        if (instanceType != null) {
          prop.getTypes().add(instanceType);
          prop.typesToSkip.add(instanceType);
        }
      }
    }
  }

  /**
   * Traverses the tree, building a map from field names to Nodes for all fields that can be
   * renamed.
   */
  private class FindRenameableProperties extends AbstractScopedCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isGetProp()) {
        handleGetProp(n);
      } else if (n.isObjectLit()) {
        handleObjectLit(n);
      } else if (n.isCall()) {
        handleCall(n);
      } else if (n.isClass()) {
        handleClass(n);
      } else if (n.isObjectPattern()) {
        handleObjectPattern(n);
      }
    }

    private void handleGetProp(Node n) {
      String name = n.getLastChild().getString();
      JSType type = getType(n.getFirstChild());
      Property prop = getProperty(name);
      if (!prop.scheduleRenaming(n.getLastChild(), type)
          && propertiesToErrorFor.containsKey(name)) {
        String suggestion = "";
        if (type.isAllType() || type.isUnknownType()) {
          if (n.getFirstChild().isThis()) {
            suggestion = "The \"this\" object is unknown in the function, consider using @this";
          } else {
            String qName = n.getFirstChild().getQualifiedName();
            suggestion = "Consider casting " + qName + " if you know its type.";
          }
        } else {
          List<String> errors = new ArrayList<>();
          printErrorLocations(errors, type);
          if (!errors.isEmpty()) {
            suggestion = "Consider fixing errors for the following types:\n";
            suggestion += Joiner.on("\n").join(errors);
          }
        }
        compiler.report(
            JSError.make(
                n,
                propertiesToErrorFor.get(name),
                PropertyRenamingDiagnostics.INVALIDATION,
                name,
                String.valueOf(type),
                n.toString(),
                suggestion));
      }
    }

    private void handleObjectLit(Node n) {
      // Object.defineProperties literals are handled at the CALL node.
      if (n.getParent().isCall() && NodeUtil.isObjectDefinePropertiesDefinition(n.getParent())) {
        return;
      }

      for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
        switch (child.getToken()) {
          case COMPUTED_PROP:
            // These won't be renamed due to our assumptions. Ignore them.
          case OBJECT_SPREAD:
            // Ignore properties added via spread. All properties accessed from object literals are
            // invalidated regardless, so we don't have to explicitly do that here. Additionally,
            // even if we invalidated all the properties known to be on the spread type, there may
            // be others we're unaware of, so it would be insufficient.
            continue;

          case STRING_KEY:
          case MEMBER_FUNCTION_DEF:
          case GETTER_DEF:
          case SETTER_DEF:
            if (child.isQuotedString()) {
              continue; // These won't be renamed due to our assumptions. Ignore them.
            }

            // We should never see a mix of numbers and strings.
            String name = child.getString();
            JSType objlitType = getType(n);
            Property prop = getProperty(name);
            if (!prop.scheduleRenaming(child, objlitType)) {
              // TODO(user): It doesn't look like the user can do much in this
              // case right now.
              if (propertiesToErrorFor.containsKey(name)) {
                compiler.report(
                    JSError.make(
                        child,
                        propertiesToErrorFor.get(name),
                        PropertyRenamingDiagnostics.INVALIDATION,
                        name,
                        String.valueOf(objlitType),
                        n.toString(),
                        ""));
              }
            }
            break;

          default:
            throw new IllegalStateException(
                "Unexpected child of OBJECTLIT: " + child.toStringTree());
        }
      }
    }

    /** Examines calls in case they are Object.defineProperties calls */
    private void handleCall(Node call) {
      Node target = call.getFirstChild();
      if (!target.isQualifiedName()) {
        return;
      }

      String functionName = target.getOriginalQualifiedName();
      if (functionName != null
          && compiler.getCodingConvention().isPropertyRenameFunction(functionName)) {
        handlePropertyRenameFunctionCall(call, functionName);
      } else if (NodeUtil.isObjectDefinePropertiesDefinition(call)) {
        handleObjectDefineProperties(call);
      }
    }

    private void handleClass(Node classNode) {
      JSType classType = classNode.getJSType();
      JSType classInstanceType =
          // the class type may not be a function type if it was in a cast, so treat it as unknown
          classType.toMaybeFunctionType() != null
              ? classType.toMaybeFunctionType().getInstanceType()
              : registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);

      for (Node member : NodeUtil.getClassMembers(classNode).children()) {
        // possibilities are:
        //   MEMBER_FUNCTION_DEF (could be static)
        //   COMPUTED_PROP (ignore)
        //   GETTER_DEF (could be static)
        //   SETTER_DEF (could be static)
        if (member.isComputedProp() || member.isQuotedString()) {
          // TODO(b/119682883): consider handling symbols here
          continue;
        }
        String name = member.getString();
        Property prop = getProperty(name);
        JSType ownerType = member.isStaticMember() ? classType : classInstanceType;

        if (!prop.scheduleRenaming(member, ownerType) && propertiesToErrorFor.containsKey(name)) {
          String suggestion = "";
          List<String> errors = new ArrayList<>();
          printErrorLocations(errors, ownerType);
          if (!errors.isEmpty()) {
            suggestion = "Consider fixing errors for the following types:\n";
            suggestion += Joiner.on("\n").join(errors);
          }
          compiler.report(
              JSError.make(
                  member,
                  propertiesToErrorFor.get(name),
                  PropertyRenamingDiagnostics.INVALIDATION,
                  name,
                  String.valueOf(ownerType),
                  member.toString(),
                  suggestion));
        }
      }
    }

    private void handleObjectPattern(Node pattern) {
      JSType objectPatternType = checkNotNull(pattern.getJSType());

      for (DestructuredTarget target :
          DestructuredTarget.createAllNonEmptyTargetsInPattern(
              compiler.getTypeRegistry(), objectPatternType, pattern)) {
        if (!target.hasStringKey()) {
          // ignore computed properties and rest
          // TODO(b/119682883): consider handling symbols here
          continue;
        }
        Node stringKey = target.getStringKey();
        if (stringKey.isQuotedString()) {
          // Never rename quoted property accesses, e.g.
          //   const {'prop': localVar} = someObj;
          continue;
        }
        String name = stringKey.getString();
        Property prop = getProperty(name);
        if (!prop.scheduleRenaming(stringKey, objectPatternType)
            && propertiesToErrorFor.containsKey(name)) {
          String suggestion = "";
          if (objectPatternType.isAllType() || objectPatternType.isUnknownType()) {
            String qName = target.getNode().getQualifiedName();
            if (qName != null) {
              suggestion = "Consider tightening the type assigned to " + qName;
            } else {
              suggestion = "Consider tightening the type assigned to " + target.getNode();
            }
          } else {
            List<String> errors = new ArrayList<>();
            printErrorLocations(errors, objectPatternType);
            if (!errors.isEmpty()) {
              suggestion = "Consider fixing errors for the following types:\n";
              suggestion += Joiner.on("\n").join(errors);
            }
          }
          compiler.report(
              JSError.make(
                  stringKey,
                  propertiesToErrorFor.get(name),
                  PropertyRenamingDiagnostics.INVALIDATION,
                  name,
                  String.valueOf(objectPatternType),
                  stringKey.toString(),
                  suggestion));
        }
      }
    }

    private void handlePropertyRenameFunctionCall(Node call, String renameFunctionName) {
      int childCount = call.getChildCount();
      if (childCount != 2 && childCount != 3) {
        compiler.report(
            JSError.make(
                call,
                PropertyRenamingDiagnostics.INVALID_RENAME_FUNCTION,
                renameFunctionName,
                " Must be called with 1 or 2 arguments"));
        return;
      }

      if (!call.getSecondChild().isString()) {
        compiler.report(
            JSError.make(
                call,
                PropertyRenamingDiagnostics.INVALID_RENAME_FUNCTION,
                renameFunctionName,
                " The first argument must be a string literal."));
        return;
      }

      String propName = call.getSecondChild().getString();

      if (propName.contains(".")) {
        compiler.report(
            JSError.make(
                call,
                PropertyRenamingDiagnostics.INVALID_RENAME_FUNCTION,
                renameFunctionName,
                " The first argument must not be a property path."));
        return;
      }

      Node obj = call.getChildAtIndex(2);
      JSType type = getType(obj);
      Property prop = getProperty(propName);
      if (!prop.scheduleRenaming(call.getSecondChild(), type)
          && propertiesToErrorFor.containsKey(propName)) {
        String suggestion = "";
        if (type.isAllType() || type.isUnknownType()) {
          if (obj.isThis()) {
            suggestion = "The \"this\" object is unknown in the function, consider using @this";
          } else {
            String qName = obj.getQualifiedName();
            suggestion = "Consider casting " + qName + " if you know its type.";
          }
        } else {
          List<String> errors = new ArrayList<>();
          printErrorLocations(errors, type);
          if (!errors.isEmpty()) {
            suggestion = "Consider fixing errors for the following types:\n";
            suggestion += Joiner.on("\n").join(errors);
          }
        }

        compiler.report(
            JSError.make(
                call,
                propertiesToErrorFor.get(propName),
                PropertyRenamingDiagnostics.INVALIDATION,
                propName,
                String.valueOf(type),
                renameFunctionName,
                suggestion));
      }
    }

    private void handleObjectDefineProperties(Node call) {
      Node typeObj = call.getSecondChild();
      JSType type = getType(typeObj);
      Node objectLiteral = typeObj.getNext();
      if (!objectLiteral.isObjectLit()) {
        return;
      }

      for (Node key : objectLiteral.children()) {
        switch (key.getToken()) {
          case COMPUTED_PROP:
          case OBJECT_SPREAD:
            break;

          case STRING_KEY:
          case MEMBER_FUNCTION_DEF:
          case GETTER_DEF:
          case SETTER_DEF:
            if (!key.isQuotedString()) {
              Property prop = getProperty(key.getString());
              prop.scheduleRenaming(key, type);
            }
            break;

          default:
            throw new IllegalStateException("Unrecognized child of object lit " + key);
        }
      }
    }

    private void printErrorLocations(List<String> errors, JSType t) {
      if (!t.isObjectType() || t.isAllType()) {
        return;
      }

      if (t.isUnionType()) {
        for (JSType alt : t.getUnionMembers()) {
          printErrorLocations(errors, alt);
        }
        return;
      }

      Iterable<JSError> invalidations =
          FluentIterable.from(invalidationMap.get(t))
              .transform(Suppliers.supplierFunction())
              .limit(MAX_INVALIDATION_WARNINGS_PER_PROPERTY);
      for (JSError error : invalidations) {
        errors.add(t + " at " + error.getSourceName() + ":" + error.getLineNumber());
      }
    }
  }

  /** Renames all properties with references on more than one type. */
  void renameProperties() {
    int propsRenamed = 0;
    int propsSkipped = 0;
    int instancesRenamed = 0;
    int instancesSkipped = 0;
    int singleTypeProps = 0;

    Set<String> reported = new HashSet<>();
    for (Property prop : properties.values()) {
      if (!prop.wouldBeDisambiguatedByRenaming()) {
        if (!prop.isValidForRenaming) {
          ++propsSkipped;
        } else {
          ++singleTypeProps;
        }
        continue;
      }

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

        if (prop.shouldRenameOnType(rootType)) {
          String newName = propNames.get(pTypes.find(rootType));
          node.setString(newName);
          compiler.reportChangeToEnclosingScope(node);
          ++instancesRenamed;
          continue;
        }

        ++instancesSkipped;

        CheckLevel checkLevelForProp = propertiesToErrorFor.getOrDefault(prop.name, CheckLevel.OFF);
        if (!prop.isValidForRenaming
            && checkLevelForProp != CheckLevel.OFF
            && !reported.contains(prop.name)) {
          reported.add(prop.name);
          compiler.report(
              JSError.make(
                  node,
                  checkLevelForProp,
                  PropertyRenamingDiagnostics.INVALIDATION_ON_TYPE,
                  prop.name,
                  rootType.toString(),
                  ""));
        }
      }
    }

    if (logger.isLoggable(Level.FINE)) {
      logger.fine("Renamed " + instancesRenamed + " instances of "
                  + propsRenamed + " properties.");
      logger.fine("Skipped renaming " + instancesSkipped + " invalidated "
                  + "properties, " + propsSkipped + " instances of properties "
                  + "that were skipped for specific types and " + singleTypeProps
                  + " properties that were referenced from only one type.");
    }
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
  @VisibleForTesting
  Multimap<String, Collection<JSType>> getRenamedTypesForTesting() {
    Multimap<String, Collection<JSType>> ret = HashMultimap.create();
    for (Map.Entry<String, Property> entry : properties.entrySet()) {
      Property prop = entry.getValue();
      if (prop.isValidForRenaming) {
        for (Collection<JSType> c : prop.getTypes().allEquivalenceClasses()) {
          if (!c.isEmpty() && !prop.typesToSkip.contains(c.iterator().next())) {
            ret.put(entry.getKey(), c);
          }
        }
      }
    }
    return ret;
  }

  private JSType getType(Node node) {
    if (node == null || node.getJSType() == null) {
      return registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
    }
    return node.getJSType();
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
      for (JSType alt : type.getUnionMembers()) {
        types.addAll(getTypesToSkipForTypeNonUnion(alt));
      }
      return types.build();
    } else if (type.isEnumElementType()) {
      return getTypesToSkipForType(type.getEnumeratedTypeOfEnumElement());
    }
    return ImmutableSet.copyOf(getTypesToSkipForTypeNonUnion(type));
  }

  private Set<JSType> getTypesToSkipForTypeNonUnion(JSType type) {
    Set<JSType> types = new HashSet<>();
    JSType skipType = type;
    while (skipType != null) {
      types.add(skipType);
      ObjectType objSkipType = skipType.toMaybeObjectType();
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
    return type.isEnumType() || type.isBoxableScalar();
  }

  /**
   * Returns the alternatives if this is a type that represents multiple
   * types, and null if not. Union and interface types can correspond to
   * multiple other types.
   */
  private Iterable<? extends JSType> getTypeAlternatives(JSType type) {
    if (type.isUnionType()) {
      return type.getUnionMembers();
    } else {
      ObjectType objType = type.toMaybeObjectType();
      FunctionType constructor = objType != null ? objType.getConstructor() : null;
      if (constructor != null && constructor.isInterface()) {
        List<JSType> list = new ArrayList<>();
        for (FunctionType impl : constructor.getDirectSubTypes()) {
          list.add(impl.getInstanceType());
        }
        return list.isEmpty() ? null : list;
      } else {
        return null;
      }
    }
  }

  /**
   * Returns the type representing an innate disambiguation cluster of types as defined by the
   * declarations of properties named {@code field} against the type lattice.
   *
   * <p>The representative type is the top-most type that declares {@code field} and which is above
   * {@code type}. Therefore, for any type, its representative is always the same, and it is
   * possible to compute disambiguations by only considering representatives.
   *
   * <p>Interface edges in the lattice, because they form a DAG and not a tree, are not walked when
   * finding the representative. Interfaces must therefore be considered as being in distinct innate
   * clusters.
   */
  private ObjectType getRepresentativeType(String field, JSType type) {
    if (type == null) {
      return null;
    }

    ObjectType foundType = representativeTypeCache.get(field, type);
    if (foundType != null) {
      return foundType.equals(representativeTypeSentinel) ? null : foundType;
    }

    if (type.isEnumElementType()) {
      foundType = getRepresentativeType(field, type.getEnumeratedTypeOfEnumElement());
      representativeTypeCache.put(
          field, type, foundType == null ? representativeTypeSentinel : foundType);
      return foundType;
    }

    if (!type.isObjectType()) {
      if (type.isBoxableScalar()) {
        foundType = getRepresentativeType(field, type.autobox());
        representativeTypeCache.put(
            field, type, foundType == null ? representativeTypeSentinel : foundType);
        return foundType;
      } else {
        representativeTypeCache.put(field, type, representativeTypeSentinel);
        return null;
      }
    }

    // Ignore the prototype itself at all times.
    if ("prototype".equals(field)) {
      representativeTypeCache.put(field, type, representativeTypeSentinel);
      return null;
    }

    // We look up the prototype chain to find the highest place (if any) that
    // this appears.  This will make references to overridden properties look
    // like references to the initial property, so they are renamed alike.
    ObjectType objType = type.toMaybeObjectType();
    if (objType != null) {
      foundType = objType.getTopMostDefiningType(field);
    }

    // If the property does not exist on the referenced type but the original
    // type is an object type, see if any subtype has the property.
    if (foundType == null) {
      JSType subtypeWithProp = type.getGreatestSubtypeWithProperty(field);
      ObjectType maybeType = subtypeWithProp == null ? null : subtypeWithProp.toMaybeObjectType();
      // getGreatestSubtypeWithProperty does not guarantee that the property
      // is defined on the returned type, it just indicates that it might be,
      // so we have to double check.
      if (maybeType != null && maybeType.hasOwnProperty(field)) {
        foundType = maybeType;
      }
    }

    // Unwrap templatized types, they are not unique at runtime.
    if (foundType != null && foundType.isTemplatizedType()) {
      foundType = foundType.getRawType();
    }

    // Since disambiguation just looks at names, we must return a uniquely named type rather
    // than an "equivalent" type. In particular, we must manually unwrap named types
    // so that the returned type has the correct name.
    if (foundType != null && foundType.isNamedType()) {
      foundType = foundType.toMaybeNamedType().getReferencedType().toMaybeObjectType();
    }

    representativeTypeCache.put(
        field, type, foundType == null ? representativeTypeSentinel : foundType);
    return foundType;
  }

  private static boolean isStructuralInterfacePrototype(JSType type) {
    if (!type.isFunctionPrototypeType()) {
      return false;
    }
    FunctionType constructor = type.toObjectType().getOwnerFunction();
    return constructor != null && constructor.isStructuralInterface();
  }

  /**
   * Returns the corresponding instance if `maybePrototype` is a prototype of a constructor,
   * otherwise null
   */
  @Nullable
  private static JSType getInstanceIfPrototype(JSType maybePrototype) {
    if (maybePrototype.isFunctionPrototypeType()) {
      FunctionType constructor = maybePrototype.toObjectType().getOwnerFunction();
      if (constructor != null) {
        if (!constructor.hasInstanceType()) {
          // this can happen when adding to the prototype of a non-constructor function
          return null;
        }
        return constructor.getInstanceType();
      }
    }
    return null;
  }

  /**
   * Return the constructor type of {@code type}, which holds {@code type}'s implemented interfaces.
   */
  private FunctionType getConstructor(JSType type) {
    ObjectType objType = type.toMaybeObjectType();
    if (objType == null) {
      return null;
    }

    if (objType.isFunctionType()) {
      // Recall that the constructor of a constructor `A`, is not `A` itself, but rather `Function`.
      // If `Function` implemented some interface, that interface would be conflated with all other
      // ctors, however that currently isn't the case, and would be correct.
      return (FunctionType) registry.getNativeType(JSTypeNative.FUNCTION_FUNCTION_TYPE);
    } else if (objType.isFunctionPrototypeType()) {
      // For the purposes of disambiguation, pretend that prototypes implement the interfaces of
      // their instance type.
      return objType.getOwnerFunction();
    } else {
      return objType.getConstructor();
    }
  }
}
