/*
 * Copyright 2006 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Table;
import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.jscomp.modules.ModuleMap;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.StaticRef;
import com.google.javascript.rhino.StaticScope;
import com.google.javascript.rhino.StaticSlot;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.StaticSymbolTable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Builds a namespace of all qualified names whose root is in the global scope or a module, plus an
 * index of all references to those global names.
 *
 * <p>When used as a StaticScope this class acts like a single parentless global scope. The module
 * references are currently only accessible by {@link #getNameFromModule(ModuleMetadata, String)},
 * as many use cases only care about global names. (This may change as module rewriting is moved
 * later in compilation). Module tracking also only occurs when {@link
 * com.google.javascript.jscomp.modules.ModuleMapCreator} has run.
 *
 * <p>The namespace can be updated as the AST is changed. Removing names or references should be
 * done by the methods on Name. Adding new names should be done with {@link #scanNewNodes}.
 */
class GlobalNamespace
    implements StaticScope, StaticSymbolTable<GlobalNamespace.Name, GlobalNamespace.Ref> {

  private final AbstractCompiler compiler;
  private final boolean enableImplicityAliasedValues;
  private final Node root;
  private final Node externsRoot;
  private final Node globalRoot = IR.root();
  private final LinkedHashMap<Node, Boolean> spreadSiblingCache = new LinkedHashMap<>();
  private SourceKind sourceKind;
  private Scope externsScope;
  private boolean generated = false;
  private static final QualifiedName GOOG_PROVIDE = QualifiedName.of("goog.provide");

  enum SourceKind {
    EXTERN,
    TYPE_SUMMARY,
    CODE;

    static SourceKind fromScriptNode(Node n) {
      if (!n.isFromExterns()) {
        return CODE;
      } else if (NodeUtil.isFromTypeSummary(n)) {
        return TYPE_SUMMARY;
      } else {
        return EXTERN;
      }
    }
  }

  /**
   * Each reference has an index in post-order. Notice that some nodes are represented by 2 Ref
   * objects, so this index is not necessarily unique.
   */
  private int currentPreOrderIndex = 0;

  /** Global namespace tree */
  private final List<Name> globalNames = new ArrayList<>();

  /** Maps names (e.g. "a.b.c") to nodes in the global namespace tree */
  private final Map<String, Name> nameMap = new HashMap<>();

  /** Maps names (e.g. "a.b.c") and MODULE_BODY nodes to Names in that module */
  private final Table<ModuleMetadata, String, Name> nameMapByModule = HashBasedTable.create();

  /**
   * Creates an instance that may emit warnings when building the namespace.
   *
   * @param compiler The AbstractCompiler, for reporting code changes
   * @param root The root of the rest of the code to build a namespace for.
   */
  GlobalNamespace(AbstractCompiler compiler, Node root) {
    this(compiler, null, root);
  }

  /**
   * Creates an instance that may emit warnings when building the namespace.
   *
   * @param compiler The AbstractCompiler, for reporting code changes
   * @param externsRoot The root of the externs to build a namespace for. If this is null, externs
   *     and properties defined on extern types will not be included in the global namespace. If
   *     non-null, it allows user-defined function on extern types to be included in the global
   *     namespace. E.g. String.foo.
   * @param root The root of the rest of the code to build a namespace for.
   */
  GlobalNamespace(AbstractCompiler compiler, Node externsRoot, Node root) {
    this.compiler = compiler;
    this.externsRoot = externsRoot;
    this.root = root;
    this.enableImplicityAliasedValues = compiler.getOptions().getAssumeStaticInheritanceRequired();
  }

  boolean hasExternsRoot() {
    return externsRoot != null;
  }

  @Override
  public Node getRootNode() {
    return root.getParent();
  }

  /**
   * Returns the root node of the scope in which the root of a qualified name is declared, or null.
   *
   * @param name A variable name (e.g. "a")
   * @param s The scope in which the name is referenced
   * @return The root node of the scope in which this is defined, or null if this is undeclared.
   */
  private Node getRootNode(String name, Scope s) {
    name = getTopVarName(name);
    Var v = s.getVar(name);
    if (v == null && externsScope != null) {
      v = externsScope.getVar(name);
    }
    if (v == null) {
      Name providedName = nameMap.get(name);
      return providedName != null && providedName.isProvided ? globalRoot : null;
    }
    return v.isLocal() ? v.getScopeRoot() : globalRoot;
  }

  @Override
  public StaticScope getParentScope() {
    return null;
  }

  @Override
  public Name getSlot(String name) {
    return getOwnSlot(name);
  }

  @Override
  public Name getOwnSlot(String name) {
    ensureGenerated();
    return nameMap.get(name);
  }

  @Override
  public Iterable<Ref> getReferences(Name slot) {
    ensureGenerated();
    return Collections.unmodifiableCollection(slot.getRefs());
  }

  @Override
  public StaticScope getScope(Name slot) {
    return this;
  }

  @Override
  public Iterable<Name> getAllSymbols() {
    ensureGenerated();
    return Collections.unmodifiableCollection(getNameIndex().values());
  }

  private void ensureGenerated() {
    if (!generated) {
      process();
    }
  }

  /**
   * Gets a list of the roots of the forest of the global names, where the roots are the top-level
   * names.
   */
  List<Name> getNameForest() {
    ensureGenerated();
    return globalNames;
  }

  /**
   * Gets an index of all the global names, indexed by full qualified name (as in "a", "a.b.c",
   * etc.).
   */
  Map<String, Name> getNameIndex() {
    ensureGenerated();
    return nameMap;
  }

  /**
   * A simple data class that contains the information necessary to inspect a node for changes to
   * the global namespace.
   */
  static class AstChange {
    final JSModule module;
    final Scope scope;
    final Node node;

    AstChange(JSModule module, Scope scope, Node node) {
      this.module = module;
      this.scope = scope;
      this.node = node;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof AstChange) {
        AstChange other = (AstChange) obj;
        return Objects.equals(this.module, other.module)
            && Objects.equals(this.scope, other.scope)
            && Objects.equals(this.node, other.node);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.module, this.scope, this.node);
    }
  }

  /**
   * If the client adds new nodes to the AST, scan these new nodes to see if they've added any
   * references to the global namespace.
   *
   * @param newNodes New nodes to check.
   */
  void scanNewNodes(Set<AstChange> newNodes) {
    BuildGlobalNamespace builder = new BuildGlobalNamespace();

    for (AstChange info : newNodes) {
      if (!info.node.isQualifiedName() && !NodeUtil.mayBeObjectLitKey(info.node)) {
        continue;
      }
      scanFromNode(builder, info.module, info.scope, info.node);
    }
  }

  private void scanFromNode(BuildGlobalNamespace builder, JSModule module, Scope scope, Node n) {
    // Check affected parent nodes first.
    Node parent = n.getParent();
    if ((n.isName() || n.isGetProp()) && parent.isGetProp()) {
      // e.g. when replacing "my.alias.prop" with "foo.bar.prop"
      // we want also want to visit "foo.bar.prop", since that's a new global qname we are now
      // referencing.
      scanFromNode(builder, module, scope, n.getParent());
    } else if (n.getPrevious() != null && n.getPrevious().isObjectPattern()) {
      // e.g. if we change `const {x} = bar` to `const {x} = foo`, add a new reference to `foo.x`
      // attached to the STRING_KEY `x`
      Node pattern = n.getPrevious();
      for (Node key : pattern.children()) {
        if (key.isStringKey()) {
          scanFromNode(builder, module, scope, key);
        }
      }
    }
    builder.collect(module, scope, n);
  }

  /** Builds the namespace lazily. */
  private void process() {
    if (hasExternsRoot()) {
      sourceKind = SourceKind.EXTERN;
      NodeTraversal.traverse(compiler, externsRoot, new BuildGlobalNamespace());
    }
    sourceKind = SourceKind.CODE;

    NodeTraversal.traverse(compiler, root, new BuildGlobalNamespace());
    generated = true;
    externsScope = null;
  }

  /**
   * Gets the top variable name from a possibly namespaced name.
   *
   * @param name A variable or qualified property name (e.g. "a" or "a.b.c.d")
   * @return The top variable name (e.g. "a")
   */
  private static String getTopVarName(String name) {
    int firstDotIndex = name.indexOf('.');
    return firstDotIndex == -1 ? name : name.substring(0, firstDotIndex);
  }

  @Nullable
  Name getNameFromModule(ModuleMetadata moduleMetadata, String name) {
    checkNotNull(moduleMetadata);
    checkNotNull(name);
    ensureGenerated();
    return nameMapByModule.get(moduleMetadata, name);
  }

  /**
   * Returns whether a declaration node, inside an object-literal, has a following OBJECT_SPREAD
   * sibling.
   *
   * <p>This check is implemented using a cache because otherwise it has aggregate {@code O(n^2)}
   * performance in terms of the size of an OBJECTLIT. If each declaration checked each sibling
   * independently, each sibling would be checked up-to once for each of it's preceeding siblings.
   */
  private boolean declarationHasFollowingObjectSpreadSibling(Node declaration) {
    checkState(declaration.getParent().isObjectLit(), declaration);

    @Nullable Boolean cached = this.spreadSiblingCache.get(declaration);
    if (cached != null) {
      return cached;
    }

    /**
     * Iterate backward over all children of the object-literal, filling in the cache.
     *
     * <p>We iterate the entire literal because we expect to eventually need the result for each of
     * them. Additionally, it makes the loop conditions simpler.
     *
     * <p>We use a loop rather than recursion to minimize stack depth. Large object-literals were
     * the reason caching was added.
     */
    boolean toCache = false;
    for (Node sibling = declaration.getParent().getLastChild();
        sibling != null;
        sibling = sibling.getPrevious()) {
      if (sibling.isSpread()) {
        toCache = true;
      }
      this.spreadSiblingCache.put(sibling, toCache);
    }

    return this.spreadSiblingCache.get(declaration);
  }

  // -------------------------------------------------------------------------

  /** Builds a tree representation of the global namespace. Omits prototypes. */
  private class BuildGlobalNamespace extends NodeTraversal.AbstractPreOrderCallback {
    private Node curModuleRoot = null;
    private ModuleMetadata curMetadata = null;
    /** Collect the references in pre-order. */
    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      if (hasExternsRoot()) {
        if (n == externsRoot) {
          // If we are traversing the externs, then we save a pointer to the scope
          // generated by them, so that we can do lookups in it later.
          externsScope = t.getScope();
        } else if (n.isScript()) {
          // When checking type-summary files, we want to consider them like normal code
          // for some things (like alias inlining) but like externs for other things.
          sourceKind = SourceKind.fromScriptNode(n);
        }
      }
      if (n.isModuleBody() || NodeUtil.isBundledGoogModuleScopeRoot(n)) {
        setupModuleMetadata(n);
      } else if (n.isScript() || NodeUtil.isBundledGoogModuleCall(n)) {
        curModuleRoot = null;
        curMetadata = null;
      }

      collect(t.getModule(), t.getScope(), n);

      return true;
    }

    /**
     * Initializes the {@link ModuleMetadata} for a goog;.module or ES module
     *
     * @param moduleRoot either a MODULE_BODY or a goog.loadModule BLOCK.
     */
    private void setupModuleMetadata(Node moduleRoot) {
      ModuleMap moduleMap = compiler.getModuleMap();
      if (moduleMap == null) {
        return;
      }
      curModuleRoot = moduleRoot;

      curMetadata =
          checkNotNull(
              ModuleImportResolver.getModuleFromScopeRoot(moduleMap, compiler, moduleRoot)
                  .metadata());
      if (curMetadata.isGoogModule()) {
        getOrCreateName("exports", curMetadata);
      }
    }

    private void collect(JSModule module, Scope scope, Node n) {
      Node parent = n.getParent();

      String name;
      boolean isSet = false;
      NameType type = NameType.OTHER;

      switch (n.getToken()) {
        case GETTER_DEF:
        case SETTER_DEF:
        case MEMBER_FUNCTION_DEF:
          if (parent.isClassMembers() && !n.isStaticMember()) {
            return; // Within a class, only static members define global names.
          }
          name = NodeUtil.getBestLValueName(n);
          isSet = true;
          type = n.isMemberFunctionDef() ? NameType.FUNCTION : NameType.GET_SET;
          break;
        case STRING_KEY:
          name = null;
          if (parent.isObjectLit()) {
            name = NodeUtil.getBestLValueName(n);
            isSet = true;
          } else if (parent.isObjectPattern()) {
            name = getNameForObjectPatternKey(n);
            // not a set
          }
          type = getValueType(n.getFirstChild());
          break;
        case NAME:
          // TODO(b/127505242): CAST parents may indicate a set.
          // This may be a variable get or set.
          switch (parent.getToken()) {
            case VAR:
            case LET:
            case CONST:
              isSet = true;
              Node rvalue = n.getFirstChild();
              type = (rvalue == null) ? NameType.OTHER : getValueType(rvalue);
              break;
            case ASSIGN:
              if (parent.getFirstChild() == n) {
                isSet = true;
                type = getValueType(n.getNext());
              }
              break;
            case GETPROP:
              // This name is nested in a getprop. Return and only create a Ref for the outermost
              // getprop in the chain.
              return;
            case FUNCTION:
              Node grandparent = parent.getParent();
              if (grandparent == null || NodeUtil.isFunctionExpression(parent)) {
                return;
              }
              isSet = true;
              type = NameType.FUNCTION;
              break;
            case CATCH:
            case INC:
            case DEC:
              isSet = true;
              type = NameType.OTHER;
              break;
            case CLASS:
              // The first child is the class name, and the second child is the superclass name.
              if (parent.getFirstChild() == n) {
                isSet = true;
                type = NameType.CLASS;
              }
              break;
            case STRING_KEY:
            case ARRAY_PATTERN:
            case DEFAULT_VALUE:
            case COMPUTED_PROP:
            case ITER_REST:
            case OBJECT_REST:
              // This may be a set.
              if (NodeUtil.isLhsByDestructuring(n)) {
                isSet = true;
                type = NameType.OTHER;
              }
              break;
            case ITER_SPREAD:
            case OBJECT_SPREAD:
              break; // isSet = false, type = OTHER.
            default:
              if (NodeUtil.isAssignmentOp(parent) && parent.getFirstChild() == n) {
                isSet = true;
                type = NameType.OTHER;
              }
          }
          name = n.getString();
          break;
        case GETPROP:
          // TODO(b/117673791): Merge this case with NAME case to fix.
          // TODO(b/120303257): Merging this case with the NAME case makes this a breaking bug.
          // TODO(b/127505242): CAST parents may indicate a set.
          // This may be a namespaced name get or set.
          if (parent != null) {
            switch (parent.getToken()) {
              case ASSIGN:
                if (parent.getFirstChild() == n) {
                  isSet = true;
                  type = getValueType(n.getNext());
                }
                break;
              case GETPROP:
                // This is nested in another getprop. Return and only create a Ref for the outermost
                // getprop in the chain.
                return;
              case INC:
              case DEC:
              case ITER_SPREAD:
              case OBJECT_SPREAD:
                break; // isSet = false, type = OTHER.
              default:
                if (NodeUtil.isAssignmentOp(parent) && parent.getFirstChild() == n) {
                  isSet = true;
                  type = NameType.OTHER;
                }
            }
          }
          if (!n.isQualifiedName()) {
            return;
          }
          name = n.getQualifiedName();
          break;
        case CALL:
          if (isObjectHasOwnPropertyCall(n)) {
            String qname = n.getFirstFirstChild().getQualifiedName();
            Name globalName = getOrCreateName(qname, curMetadata);
            globalName.usedHasOwnProperty = true;
          } else if (parent.isExprResult()
              && GOOG_PROVIDE.matches(n.getFirstChild())
              && n.getSecondChild().isString()) {
            // goog.provide goes through a different code path than regular sets because it can
            // create multiple names, e.g. `goog.provide('a.b.c');` creates the global names
            // a, a.b, and a.b.c. Other sets only create a single global name.
            createNamesFromProvide(n.getSecondChild().getString());
            return;
          }
          return;
        default:
          return;
      }

      if (name == null) {
        return;
      }

      Node root = getRootNode(name, scope);
      // We are only interested in global and module names.
      if (!isTopLevelScopeRoot(root)) {
        return;
      }

      ModuleMetadata nameMetadata = root == globalRoot ? null : curMetadata;
      if (isSet) {
        // Use the closest hoist scope to select handleSetFromGlobal or handleSetFromLocal
        // because they use the term 'global' in an ES5, pre-block-scoping sense.
        Scope hoistScope = scope.getClosestHoistScope();
        // Consider a set to be 'global' if it is in the hoist scope in which the name is defined.
        // For example, a global name set in a module scope is a 'local' set, but a module-level
        // name set in a module scope is a 'global' set.
        if (hoistScope.isGlobal()
            || (root != globalRoot && hoistScope.getRootNode() == curModuleRoot)) {
          handleSetFromGlobal(module, scope, n, parent, name, type, nameMetadata);
        } else {
          handleSetFromLocal(module, scope, n, parent, name, nameMetadata);
        }
      } else {
        handleGet(module, scope, n, parent, name, nameMetadata);
      }
    }

    /** Declares all subnamespaces from `goog.provide('some.long.namespace')` globally. */
    private void createNamesFromProvide(String namespace) {
      Name name;
      int dot = 0;

      while (dot >= 0) {
        dot = namespace.indexOf('.', dot + 1);
        String subNamespace = dot < 0 ? namespace : namespace.substring(0, dot);
        checkState(!subNamespace.isEmpty());
        name = getOrCreateName(subNamespace, null);
        name.isProvided = true;
      }

      Name newName = getOrCreateName(namespace, null);
      newName.isProvided = true;
    }

    /**
     * Whether the given name root represents a global or module-level name
     *
     * <p>This method will return false for functions and blocks and true for module bodies and the
     * {@code globalRoot}. The one exception is if the function or block is from a goog.loadModule
     * argument, as those functions/blocks are treated as module roots.
     */
    private boolean isTopLevelScopeRoot(Node root) {
      if (root == null) {
        return false;
      } else if (root == globalRoot) {
        return true;
      } else if (root == curModuleRoot) {
        return true;
      }
      // Given
      //   goog.loadModule(function(exports) {
      // pretend that assignments to `exports` or `exports.x = ...` are scoped to the function body,
      // although `exports` is really in the enclosing function parameter scope.
      return curModuleRoot != null && curModuleRoot.isBlock() && root == curModuleRoot.getParent();
    }

    /**
     * Gets the fully qualified name corresponding to an object pattern key, as long as it is not in
     * a nested pattern and is destructuring an qualified name.
     *
     * @param stringKey A child of an OBJECT_PATTERN node
     * @return The global name, or null if {@code n} doesn't correspond to the key of an object
     *     literal that can be named
     */
    String getNameForObjectPatternKey(Node stringKey) {
      Node parent = stringKey.getParent();
      checkState(parent.isObjectPattern());

      Node patternParent = parent.getParent();
      if (patternParent.isAssign() || patternParent.isDestructuringLhs()) {
        // this is a top-level string key. we find the name.
        Node rhs = patternParent.getSecondChild();
        if (rhs == null || !rhs.isQualifiedName()) {
          // The rhs is null for patterns in parameter lists, enhanced for loops, and catch exprs
          return null;
        }
        return rhs.getQualifiedName() + "." + stringKey.getString();

      } else {
        // skip this step for nested patterns for now
        return null;
      }
    }

    /**
     * Gets the type of a value or simple expression.
     *
     * @param n An r-value in an assignment or variable declaration (not null)
     * @return A {@link NameType}
     */
    NameType getValueType(Node n) {
      // Shorthand assignment of extended object literal
      if (n == null) {
        return NameType.OTHER;
      }
      switch (n.getToken()) {
        case CLASS:
          return NameType.CLASS;
        case OBJECTLIT:
          return NameType.OBJECTLIT;
        case FUNCTION:
          return NameType.FUNCTION;
        case OR:
          // Recurse on the second value. If the first value were an object
          // literal or function, then the OR would be meaningless and the
          // second value would be dead code. Assume that if the second value
          // is an object literal or function, then the first value will also
          // evaluate to one when it doesn't evaluate to false.
          return getValueType(n.getLastChild());
        case HOOK:
          // The same line of reasoning used for the OR case applies here.
          Node second = n.getSecondChild();
          NameType t = getValueType(second);
          if (t != NameType.OTHER) {
            return t;
          }
          Node third = second.getNext();
          return getValueType(third);
        default:
          break;
      }
      return NameType.OTHER;
    }

    /**
     * Updates our representation of the global namespace to reflect an assignment to a global name
     * in any scope where variables are hoisted to the global scope (i.e. the global scope in an ES5
     * sense).
     *
     * @param module the current module
     * @param scope the current scope
     * @param n The node currently being visited
     * @param parent {@code n}'s parent
     * @param name The global name (e.g. "a" or "a.b.c.d")
     * @param type The type of the value that the name is being assigned
     */
    void handleSetFromGlobal(
        JSModule module,
        Scope scope,
        Node n,
        Node parent,
        String name,
        NameType type,
        ModuleMetadata metadata) {
      if (maybeHandlePrototypePrefix(module, scope, n, parent, name, metadata)) {
        return;
      }

      Name nameObj = getOrCreateName(name, metadata);
      if (!nameObj.isGetOrSetDefinition()) {
        // Don't change the type of a getter or setter. This is because given:
        //   var a = {set b(item) {}}; a.b = class {};
        // `a.b = class {};` does not change the runtime value of a.b, and we do not want to change
        // the 'type' of a.b to Type.CLASS.
        // TODO(lharker): for non-setter cases, do we really want to just treat the last set of
        // a name as canonical? e.g. what if a name is first set to a class, then an object literal?
        nameObj.type = type;
      }
      if (n.getBooleanProp(Node.MODULE_EXPORT)) {
        nameObj.isModuleProp = true;
      }

      if (isNestedAssign(parent)) {
        // This assignment is both a set and a get that creates an alias.
        Ref.Type refType = Ref.Type.SET_FROM_GLOBAL;
        addOrConfirmTwinRefs(nameObj, n, refType, module, scope);
      } else {
        addOrConfirmRef(nameObj, n, Ref.Type.SET_FROM_GLOBAL, module, scope);
        if (isTypeDeclaration(n)) {
          // Names with a @constructor or @enum annotation are always collapsed
          nameObj.setDeclaredType();
        }
      }
    }

    /**
     * If Refs already exist for the given Node confirm they match what we would create. Otherwise,
     * create them.
     *
     * @param nameObj
     * @param node
     * @param setRefType
     * @param module
     * @param scope
     */
    private void addOrConfirmTwinRefs(
        Name nameObj, Node node, Ref.Type setRefType, JSModule module, Scope scope) {
      ImmutableList<Ref> existingRefs = nameObj.getRefsForNode(node);
      if (existingRefs.isEmpty()) {
        nameObj.addTwinRefs(module, scope, node, setRefType, currentPreOrderIndex);
        currentPreOrderIndex += 2; // addTwinRefs uses 2 index values
      } else {
        checkState(existingRefs.size() == 2, "unexpected existing refs: %s", existingRefs);
        Ref setRef = existingRefs.get(0);
        // module and scope are dependent on Node, so not much point in checking them
        // the type of the getRef is set within the Name class, so no need to check that either.
        checkState(setRef.type == setRefType, "unexpected existing set Ref type: %s", setRef.type);
      }
    }

    /**
     * Determines whether a set operation is a constructor or enumeration or interface declaration.
     * The set operation may either be an assignment to a name, a variable declaration, or an object
     * literal key mapping.
     *
     * @param n The node that represents the name being set
     * @return Whether the set operation is either a constructor or enum declaration
     */
    private boolean isTypeDeclaration(Node n) {
      Node valueNode = NodeUtil.getRValueOfLValue(n);
      if (valueNode == null) {
        return false;
      } else if (valueNode.isClass()) {
        // Always treat classes as having a declared type. (Transpiled classes are annotated
        // @constructor)
        return true;
      }
      JSDocInfo info = NodeUtil.getBestJSDocInfo(n);
      // Heed the annotations only if they're sensibly used.
      return info != null
          && ((info.isConstructor() && valueNode.isFunction())
              || (info.isInterface() && valueNode.isFunction())
              || (info.hasEnumParameterType() && valueNode.isObjectLit()));
    }

    /**
     * Updates our representation of the global namespace to reflect an assignment to a global name
     * in a local scope.
     *
     * @param module The current module
     * @param scope The current scope
     * @param n The node currently being visited
     * @param parent {@code n}'s parent
     * @param name The global name (e.g. "a" or "a.b.c.d")
     */
    void handleSetFromLocal(
        JSModule module, Scope scope, Node n, Node parent, String name, ModuleMetadata metadata) {
      if (maybeHandlePrototypePrefix(module, scope, n, parent, name, metadata)) {
        return;
      }

      Name nameObj = getOrCreateName(name, metadata);
      if (n.getBooleanProp(Node.MODULE_EXPORT)) {
        nameObj.isModuleProp = true;
      }

      if (isNestedAssign(parent)) {
        // This assignment is both a set and a get that creates an alias.
        addOrConfirmTwinRefs(nameObj, n, Ref.Type.SET_FROM_LOCAL, module, scope);
      } else {
        addOrConfirmRef(nameObj, n, Ref.Type.SET_FROM_LOCAL, module, scope);
      }
    }

    /**
     * Updates our representation of the global namespace to reflect a read of a global name.
     *
     * @param module The current module
     * @param scope The current scope
     * @param n The node currently being visited
     * @param parent {@code n}'s parent
     * @param name The global name (e.g. "a" or "a.b.c.d")
     */
    void handleGet(
        JSModule module, Scope scope, Node n, Node parent, String name, ModuleMetadata metadata) {
      if (maybeHandlePrototypePrefix(module, scope, n, parent, name, metadata)) {
        return;
      }

      Ref.Type type;
      switch (parent.getToken()) {
        case EXPR_RESULT:
        case IF:
        case INSTANCEOF:
        case TYPEOF:
        case VOID:
        case NOT:
        case BITNOT:
        case POS:
        case NEG:
          type = Ref.Type.DIRECT_GET;
          break;
        case CALL:
          if (n == parent.getFirstChild()) {
            // It is a call target
            type = Ref.Type.CALL_GET;
          } else if (isClassDefiningCall(parent)) {
            type = Ref.Type.DIRECT_GET;
          } else {
            type = Ref.Type.ALIASING_GET;
          }
          break;
        case NEW:
          type = n == parent.getFirstChild() ? Ref.Type.DIRECT_GET : Ref.Type.ALIASING_GET;
          break;
        case OR:
        case AND:
          // This node is x or y in (x||y) or (x&&y). We only know that an
          // alias is not getting created for this name if the result is used
          // in a boolean context or assigned to the same name
          // (e.g. var a = a || {}).
          type = determineGetTypeForHookOrBooleanExpr(module, scope, parent, name);
          break;
        case HOOK:
          if (n != parent.getFirstChild()) {
            // This node is y or z in (x?y:z). We only know that an alias is
            // not getting created for this name if the result is assigned to
            // the same name (e.g. var a = a ? a : {}).
            type = determineGetTypeForHookOrBooleanExpr(module, scope, parent, name);
          } else {
            type = Ref.Type.DIRECT_GET;
          }
          break;
        case DELPROP:
          type = Ref.Type.DELETE_PROP;
          break;
        case CLASS:
          // This node is the superclass in an extends clause.
          type = Ref.Type.SUBCLASSING_GET;
          break;
        case DESTRUCTURING_LHS:
        case ASSIGN:
          Node lhs = n.getPrevious();
          if (lhs.isCast()) {
            // Case: `/** @type {!Foo} */ (x) = ...`;
            lhs = lhs.getOnlyChild();
          }

          switch (lhs.getToken()) {
            case NAME:
            case GETPROP:
            case GETELEM:
              // The rhs of an assign or a name declaration is escaped if it's assigned to a name
              // directly ...
            case ARRAY_PATTERN:
            case OBJECT_PATTERN:
              // ... or referenced through numeric/object keys.
              type = Ref.Type.ALIASING_GET;
              break;
            default:
              throw new IllegalStateException(
                  "Unexpected previous sibling of " + n.getToken() + ": " + n.getPrevious());
          }

          break;
        case OBJECT_PATTERN: // Handle STRING_KEYS in object patterns.
        case ITER_SPREAD:
        case OBJECT_SPREAD:
        default:
          type = Ref.Type.ALIASING_GET;
          break;
      }

      handleGet(module, scope, n, parent, name, type, metadata);
    }

    /**
     * Updates our representation of the global namespace to reflect a read of a global name.
     *
     * @param module The current module
     * @param scope The current scope
     * @param n The node currently being visited
     * @param parent {@code n}'s parent
     * @param name The global name (e.g. "a" or "a.b.c.d")
     * @param type The reference type
     */
    void handleGet(
        JSModule module,
        Scope scope,
        Node n,
        Node parent,
        String name,
        Ref.Type type,
        ModuleMetadata metadata) {
      Name nameObj = getOrCreateName(name, metadata);

      // No need to look up additional ancestors, since they won't be used.
      addOrConfirmRef(nameObj, n, type, module, scope);
    }

    /**
     * If there is already a Ref for the given name & node, confirm it matches what we would create.
     * Otherwise add a new one.
     */
    private void addOrConfirmRef(
        Name nameObj, Node node, Ref.Type refType, JSModule module, Scope scope) {
      ImmutableList<Ref> existingRefs = nameObj.getRefsForNode(node);
      if (existingRefs.isEmpty()) {
        nameObj.addSingleRef(module, scope, node, refType, currentPreOrderIndex++);
      } else {
        checkState(existingRefs.size() == 1, "unexpected twin refs: %s", existingRefs);
        // module and scope are dependent on Node, so not much point in checking them
        Ref.Type existingRefType = existingRefs.get(0).type;
        checkState(
            existingRefType == refType,
            "existing ref type: %s expected: %s",
            existingRefType,
            refType);
      }
    }

    private boolean isClassDefiningCall(Node callNode) {
      CodingConvention convention = compiler.getCodingConvention();
      // Look for goog.inherits, goog.mixin
      SubclassRelationship classes = convention.getClassesDefinedByCall(callNode);
      if (classes != null) {
        return true;
      }

      // Look for calls to goog.addSingletonGetter calls.
      String className = convention.getSingletonGetterClassName(callNode);
      return className != null;
    }

    /** Detect calls of the form a.b.hasOwnProperty(c); that prevent property collapsing on a.b */
    private boolean isObjectHasOwnPropertyCall(Node callNode) {
      checkArgument(callNode.isCall(), callNode);
      if (!callNode.hasTwoChildren()) {
        return false;
      }
      Node fn = callNode.getFirstChild();
      if (!fn.isGetProp()) {
        return false;
      }
      Node callee = fn.getFirstChild();
      Node method = fn.getSecondChild();
      return method.isString()
          && "hasOwnProperty".equals(method.getString())
          && callee.isQualifiedName();
    }

    /**
     * Determines whether the result of a hook (x?y:z) or boolean expression (x||y) or (x&&y) is
     * assigned to a specific global name.
     *
     * @param module The current module
     * @param scope The current scope
     * @param parent The parent of the current node in the traversal. This node should already be
     *     known to be a HOOK, AND, or OR node.
     * @param name A name that is already known to be global in the current scope (e.g. "a" or
     *     "a.b.c.d")
     * @return The expression's get type, either {@link Ref.Type#DIRECT_GET} or {@link
     *     Ref.Type#ALIASING_GET}
     */
    Ref.Type determineGetTypeForHookOrBooleanExpr(
        JSModule module, Scope scope, Node parent, String name) {
      Node prev = parent;
      for (Node anc : parent.getAncestors()) {
        switch (anc.getToken()) {
          case INSTANCEOF:
          case EXPR_RESULT:
          case VAR:
          case LET:
          case CONST:
          case IF:
          case WHILE:
          case FOR:
          case FOR_IN:
          case TYPEOF:
          case VOID:
          case NOT:
          case BITNOT:
          case POS:
          case NEG:
            return Ref.Type.DIRECT_GET;
          case HOOK:
            if (anc.getFirstChild() == prev) {
              return Ref.Type.DIRECT_GET;
            }
            break;
          case ASSIGN:
            if (!anc.getFirstChild().matchesQualifiedName(name)) {
              return Ref.Type.ALIASING_GET;
            }
            break;
          case NAME: // a variable declaration
            if (!name.equals(anc.getString())) {
              return Ref.Type.ALIASING_GET;
            }
            break;
          case CALL:
            if (anc.getFirstChild() != prev) {
              return Ref.Type.ALIASING_GET;
            }
            break;
          case DELPROP:
            return Ref.Type.DELETE_PROP;
          default:
            break;
        }
        prev = anc;
      }
      return Ref.Type.ALIASING_GET;
    }

    /**
     * Updates our representation of the global namespace to reflect a read of a global name's
     * longest prefix before the "prototype" property if the name includes the "prototype" property.
     * Does nothing otherwise.
     *
     * @param module The current module
     * @param scope The current scope
     * @param n The node currently being visited
     * @param parent {@code n}'s parent
     * @param name The global name (e.g. "a" or "a.b.c.d")
     * @return Whether the name was handled
     */
    boolean maybeHandlePrototypePrefix(
        JSModule module, Scope scope, Node n, Node parent, String name, ModuleMetadata metadata) {
      // We use a string-based approach instead of inspecting the parse tree
      // to avoid complexities with object literals, possibly nested, beneath
      // assignments.

      int numLevelsToRemove;
      String prefix;
      if (name.endsWith(".prototype")) {
        numLevelsToRemove = 1;
        prefix = name.substring(0, name.length() - 10);
      } else {
        int i = name.indexOf(".prototype.");
        if (i == -1) {
          return false;
        }
        prefix = name.substring(0, i);
        numLevelsToRemove = 2;
        i = name.indexOf('.', i + 11);
        while (i >= 0) {
          numLevelsToRemove++;
          i = name.indexOf('.', i + 1);
        }
      }

      if (parent != null && NodeUtil.mayBeObjectLitKey(n)) {
        // Object literal keys have no prefix that's referenced directly per
        // key, so we're done.
        return true;
      }

      for (int i = 0; i < numLevelsToRemove; i++) {
        parent = n;
        n = n.getFirstChild();
      }

      handleGet(module, scope, n, parent, prefix, Ref.Type.PROTOTYPE_GET, metadata);
      return true;
    }

    /**
     * Determines whether an assignment is nested (i.e. whether its return value is used).
     *
     * @param parent The parent of the current traversal node (not null)
     * @return Whether it appears that the return value of the assignment is used
     */
    boolean isNestedAssign(Node parent) {
      return parent.isAssign() && !parent.getParent().isExprResult();
    }

    /**
     * Gets a {@link Name} instance for a global name. Creates it if necessary, as well as instances
     * for any of its prefixes that are not yet defined.
     *
     * @param name A global name (e.g. "a", "a.b.c.d")
     * @return The {@link Name} instance for {@code name}
     */
    Name getOrCreateName(String name, ModuleMetadata metadata) {
      Name node = metadata == null ? nameMap.get(name) : nameMapByModule.get(metadata, name);
      if (node == null) {
        int i = name.lastIndexOf('.');
        if (i >= 0) {
          String parentName = name.substring(0, i);
          Name parent = getOrCreateName(parentName, metadata);
          node = parent.addProperty(name.substring(i + 1), sourceKind);
          if (metadata == null) {
            nameMap.put(name, node);
          } else {
            nameMapByModule.put(metadata, name, node);
          }
        } else {
          node = new Name(name, null, sourceKind);
          if (metadata == null) {
            globalNames.add(node);
            nameMap.put(name, node);
          } else {
            nameMapByModule.put(metadata, name, node);
          }
        }
      }
      return node;
    }
  }

  // -------------------------------------------------------------------------

  @VisibleForTesting
  Name createNameForTesting(String name) {
    return new Name(name, null, SourceKind.CODE);
  }

  private enum NameType {
    CLASS, // class C {}
    OBJECTLIT, // var x = {};
    FUNCTION, // function f() {}
    SUBCLASSING_GET, // class C extends SuperClass {
    GET_SET, // a getter, setter, or both; e.g. `obj.b` in `const obj = {set b(x) {}};`
    OTHER; // anything else, including `var x = 1;`, var x = new Something();`, etc.
  }

  /**
   * How much to inline a {@link Name}.
   *
   * <p>The {@link INLINE_BUT_KEEP_DECLARATION} case is really an indicator that something 'unsafe'
   * is happening in order to not break CollapseProperties as badly. Sadly {@link INLINE_COMPLETELY}
   * may <em>also</em> be unsafe.
   */
  enum Inlinability {
    INLINE_COMPLETELY,
    INLINE_BUT_KEEP_DECLARATION,
    DO_NOT_INLINE;

    boolean shouldInlineUsages() {
      return this != DO_NOT_INLINE;
    }

    boolean shouldRemoveDeclaration() {
      return this == INLINE_COMPLETELY;
    }

    boolean canCollapse() {
      return this != DO_NOT_INLINE;
    }
  }

  /**
   * A name defined in global scope (e.g. "a" or "a.b.c.d").
   *
   * <p>Instances form a tree describing the "Closure namespaces" in the program. As the parse tree
   * traversal proceeds, we'll discover that some names correspond to JavaScript objects whose
   * properties we should consider collapsing.
   */
  final class Name implements StaticSlot {

    private final String baseName;
    private final Name parent;

    // The children of this name. Must be null if there are no children.
    @Nullable List<Name> props;
    /** The first global assignment to a name. */
    private Ref declaration;

    /** All references to a name. This must contain {@code declaration}. */
    private final LinkedHashSet<Ref> refs = new LinkedHashSet<>();

    /** Keep track of which Nodes are Refs for this Name */
    private final Map<Node, ImmutableList<Ref>> refsForNodeMap = new HashMap<>();

    private NameType type; // not final to handle forward references to names
    private boolean declaredType = false;
    private boolean isDeclared = false;
    private boolean isModuleProp = false;
    private boolean isProvided = false; // If this name was in any goog.provide() calls.
    private boolean usedHasOwnProperty = false;
    private int globalSets = 0;
    private int localSets = 0;
    private int localSetsWithNoCollapse = 0;
    private int aliasingGets = 0;
    private int totalGets = 0;
    private int callGets = 0;
    private int deleteProps = 0;
    private int subclassingGets = 0;
    private final SourceKind sourceKind;

    // Will be set to the JSDocInfo associated with the first SET_FROM_GLOBAL reference added
    // that has JSDocInfo.
    // e.g.
    // /** @type {number} */
    // X.numberProp = 3;
    @Nullable private JSDocInfo firstDeclarationJSDocInfo = null;

    // Will be set to the JSDocInfo associated with the first get reference that is a statement
    // by itself.
    // e.g.
    // /** @type {number} */
    // X.numberProp;
    @Nullable private JSDocInfo firstQnameDeclarationWithoutAssignmentJsDocInfo = null;

    private Name(String name, Name parent, SourceKind sourceKind) {
      this.baseName = name;
      this.parent = parent;
      this.type = NameType.OTHER;
      this.sourceKind = sourceKind;
    }

    Name addProperty(String name, SourceKind sourceKind) {
      if (props == null) {
        props = new ArrayList<>();
      }
      Name node = new Name(name, this, sourceKind);
      props.add(node);
      return node;
    }

    String getBaseName() {
      return baseName;
    }

    boolean inExterns() {
      return this.sourceKind == SourceKind.EXTERN;
    }

    SourceKind getSourceKind() {
      return this.sourceKind;
    }

    int subclassingGetCount() {
      return this.subclassingGets;
    }

    @Override
    public String getName() {
      return getFullName();
    }

    String getFullName() {
      return parent == null ? baseName : parent.getFullName() + '.' + baseName;
    }

    @Nullable
    @Override
    public Ref getDeclaration() {
      return declaration;
    }

    boolean isFunction() {
      return this.type == NameType.FUNCTION;
    }

    boolean isClass() {
      return this.type == NameType.CLASS;
    }

    boolean isObjectLiteral() {
      return this.type == NameType.OBJECTLIT;
    }

    int getAliasingGets() {
      return aliasingGets;
    }

    int getSubclassingGets() {
      return subclassingGets;
    }

    int getLocalSets() {
      return localSets;
    }

    int getGlobalSets() {
      return globalSets;
    }

    int getCallGets() {
      return callGets;
    }

    int getTotalGets() {
      return totalGets;
    }

    int getDeleteProps() {
      return deleteProps;
    }

    Name getParent() {
      return parent;
    }

    @Override
    public StaticScope getScope() {
      throw new UnsupportedOperationException();
    }

    /**
     * Add a pair of Refs for the same Node.
     *
     * <p>This covers cases like `var a = b = 0`. The 'b' node needs a ALIASING_GET reference and a
     * SET_FROM_GLOBAL or SET_FROM_LOCAL reference.
     *
     * @param module
     * @param scope
     * @param node
     * @param setType either SET_FROM_LOCAL or SET_FROM_GLOBAL
     * @param setRefPreOrderIndex used for setter Ref, getter ref will be this value + 1
     */
    private void addTwinRefs(
        JSModule module, Scope scope, Node node, Ref.Type setType, int setRefPreOrderIndex) {
      checkArgument(
          setType == Ref.Type.SET_FROM_GLOBAL || setType == Ref.Type.SET_FROM_LOCAL, setType);
      Ref setRef = createNewRef(module, scope, node, setType, setRefPreOrderIndex);
      Ref getRef =
          createNewRef(module, scope, node, Ref.Type.ALIASING_GET, setRefPreOrderIndex + 1);
      setRef.twin = getRef;
      getRef.twin = setRef;
      refsForNodeMap.put(node, ImmutableList.of(setRef, getRef));
      refs.add(setRef);
      updateStateForAddedRef(setRef);
      refs.add(getRef);
      updateStateForAddedRef(getRef);
    }

    private void addSingleRef(
        JSModule module, Scope scope, Node node, Ref.Type type, int preOrderIndex) {
      checkNoExistingRefsForNode(node);
      Ref ref = createNewRef(module, scope, node, type, preOrderIndex);
      refs.add(ref);
      refsForNodeMap.put(node, ImmutableList.of(ref));
      updateStateForAddedRef(ref);
    }

    private void checkNoExistingRefsForNode(Node node) {
      ImmutableList<Ref> refsForNode = refsForNodeMap.get(node);
      checkState(refsForNode == null, "Refs already exist for node: %s", refsForNode);
    }

    private Ref createNewRef(
        JSModule module, Scope scope, Node node, Ref.Type type, int preOrderIndex) {
      return new Ref(
          module, // null if the compilation isn't using JSModules
          checkNotNull(scope),
          checkNotNull(node), // may be null later, but not on creation
          this,
          type,
          preOrderIndex);
    }

    Ref addSingleRefForTesting(Ref.Type type, int preOrderIndex) {
      Ref ref =
          new Ref(
              /* module= */ null, /* scope= */ null, /* node = */ null, this, type, preOrderIndex);
      refs.add(ref);
      // node is Null for testing in this case, so nothing to add to refsForNodeMap
      updateStateForAddedRef(ref);
      return ref;
    }

    /**
     * Add an ALIASING_GET Ref for the given Node using the same Ref properties as the declaration
     * Ref, which must exist.
     *
     * <p>Only for use by CollapseProperties.
     *
     * @param newRefNode newly added AST node that refers to this Name and appears in the same
     *     module and scope as the Ref that declares this Name
     */
    void addAliasingGetClonedFromDeclaration(Node newRefNode) {
      // TODO(bradfordcsmith): It would be good to add checks that the scope and module are correct.
      Ref declRef = checkNotNull(declaration);
      addSingleRef(
          declRef.module, declRef.scope, newRefNode, Ref.Type.ALIASING_GET, declRef.preOrderIndex);
    }

    /**
     * Updates counters and JSDocInfo recorded for the name to include a newly added Ref.
     *
     * <p>Must be called exactly once when a new Ref is added.
     *
     * @param ref a Ref that has just been added for this Name
     */
    private void updateStateForAddedRef(Ref ref) {
      switch (ref.type) {
        case SET_FROM_GLOBAL:
          if (declaration == null) {
            declaration = ref;
          }
          if (firstDeclarationJSDocInfo == null) {
            // JSDocInfo from the first SET_FROM_GLOBAL will be assumed to be canonical
            // Note that this will not change if the first declaration is later removed
            // by optimizations.
            firstDeclarationJSDocInfo = getDocInfoForDeclaration(ref);
          }
          globalSets++;
          break;
        case SET_FROM_LOCAL:
          localSets++;
          JSDocInfo info = ref.getNode() == null ? null : NodeUtil.getBestJSDocInfo(ref.getNode());
          if (info != null && info.isNoCollapse()) {
            localSetsWithNoCollapse++;
          }
          break;
        case PROTOTYPE_GET:
        case DIRECT_GET:
          Node node = ref.getNode();
          if (firstQnameDeclarationWithoutAssignmentJsDocInfo == null
              && isQnameDeclarationWithoutAssignment(node)) {
            // /** @type {sometype} */
            // some.qname.ref;
            firstQnameDeclarationWithoutAssignmentJsDocInfo = node.getJSDocInfo();
          }
          totalGets++;
          break;
        case ALIASING_GET:
          aliasingGets++;
          totalGets++;
          break;
        case CALL_GET:
          callGets++;
          totalGets++;
          break;
        case DELETE_PROP:
          deleteProps++;
          break;
        case SUBCLASSING_GET:
          subclassingGets++;
          totalGets++;
          break;
        default:
          throw new IllegalStateException();
      }
    }

    /**
     * This is the only safe way to update the Node belonging to a Ref once it is added to a Name.
     *
     * <p>This is a specialized method that exists only for use by CollapseProperties.
     *
     * @param ref reference to update - it must belong to this name
     * @param newNode new value for the ref's node
     */
    void updateRefNode(Ref ref, @Nullable Node newNode) {
      checkArgument(ref.node != newNode, "redundant update to Ref node: %s", ref);

      // Once a Ref's node is set to null, it shouldn't ever be set to anything else.
      // TODO(bradfordcsmith): Document here what it means when we set the node to null.
      //     Seems to be a way to keep name.getDeclaration() returning the original declaration
      //     Ref even though its node is no longer in the AST.
      Node oldNode = ref.getNode();
      checkState(oldNode != null, "Ref's node is already null: %s", ref);
      ref.node = newNode;

      // If this ref was a twin, it isn't anymore, and its previous twin is now the only ref to the
      // original node.
      Ref twinRef = ref.getTwin();
      if (twinRef != null) {
        ref.twin = null;
        twinRef.twin = null;
        refsForNodeMap.put(oldNode, ImmutableList.of(twinRef));
      } else {
        refsForNodeMap.remove(oldNode); // this ref was the only reference on the node
      }

      if (newNode != null) {
        ImmutableList<Ref> existingRefsForNewNode = refsForNodeMap.get(newNode);
        checkArgument(
            existingRefsForNewNode == null, "refs already exist: %s", existingRefsForNewNode);
        refsForNodeMap.put(newNode, ImmutableList.of(ref));
      }
    }

    /**
     * Remove a Ref and its twin at the same time.
     *
     * <p>If you intend to remove both, it is more efficient and less error prone to use this method
     * instead of removing them one at a time.
     *
     * @param ref A Ref that has a twin.
     */
    void removeTwinRefs(Ref ref) {
      checkArgument(
          ref.name == this, "removeTwinRefs(%s): node does not belong to this name: %s", ref, this);
      checkState(refs.contains(ref), "removeRef(%s): unknown ref", ref);
      Ref twinRef = ref.getTwin();
      checkArgument(twinRef != null, ref);

      removeTwinRefsFromNodeMap(ref);
      removeRefAndUpdateState(ref);
      removeRefAndUpdateState(twinRef);
    }

    /**
     * Removes the given Ref, which must belong to this Name.
     *
     * <p>NOTE: if ref has a twin, they will no longer be twins after this method finishes. Use
     * removeTwinRefs() to remove a pair of twins at the same time.
     *
     * @param ref
     */
    void removeRef(Ref ref) {
      checkState(
          ref.name == this, "removeRef(%s): node does not belong to this name: %s", ref, this);
      checkState(refs.contains(ref), "removeRef(%s): unknown ref", ref);
      Node refNode = ref.getNode();
      if (refNode != null) {
        removeSingleRefFromNodeMap(ref);
      }
      removeRefAndUpdateState(ref);
    }

    /**
     * Update counts, declaration, and JSDoc to reflect removal of the given Ref.
     *
     * @param ref
     */
    private void removeRefAndUpdateState(Ref ref) {
      refs.remove(ref);
      if (ref == declaration) {
        declaration = null;
        for (Ref maybeNewDecl : refs) {
          if (maybeNewDecl.type == Ref.Type.SET_FROM_GLOBAL) {
            declaration = maybeNewDecl;
            break;
          }
        }
      }

      JSDocInfo info;
      switch (ref.type) {
        case SET_FROM_GLOBAL:
          globalSets--;
          break;
        case SET_FROM_LOCAL:
          localSets--;
          info = ref.getNode() == null ? null : NodeUtil.getBestJSDocInfo(ref.getNode());
          if (info != null && info.isNoCollapse()) {
            localSetsWithNoCollapse--;
          }
          break;
        case PROTOTYPE_GET:
        case DIRECT_GET:
          totalGets--;
          break;
        case ALIASING_GET:
          aliasingGets--;
          totalGets--;
          break;
        case CALL_GET:
          callGets--;
          totalGets--;
          break;
        case DELETE_PROP:
          deleteProps--;
          break;
        case SUBCLASSING_GET:
          subclassingGets--;
          totalGets--;
          break;
          // Leaving off default: allows compile-time enforcement that all values are covered
      }
    }

    private void removeSingleRefFromNodeMap(Ref ref) {
      Node refNode = checkNotNull(ref.getNode(), ref);
      if (ref.getTwin() != null) {
        removeTwinRefsFromNodeMap(ref);
        Ref twinRef = ref.getTwin();
        // break the twin relationship
        ref.twin = null;
        twinRef.twin = null;
        // put twin back alone, since we're not really removing it
        refsForNodeMap.put(refNode, ImmutableList.of(twinRef));
      } else {
        ImmutableList<Ref> refsForNode = refsForNodeMap.get(refNode);
        checkState(
            refsForNode.size() == 1 && refsForNode.get(0) == ref,
            "Unexpected Refs for Node: %s: when removing Ref: %s",
            refsForNode,
            ref);
        refsForNodeMap.remove(refNode);
      }
    }

    private void removeTwinRefsFromNodeMap(Ref ref) {
      Ref twinRef = checkNotNull(ref.getTwin(), ref);
      Node refNode = checkNotNull(ref.getNode(), ref);
      ImmutableList<Ref> refsForNode = refsForNodeMap.get(refNode);

      checkState(
          refsForNode.size() == 2,
          "unexpected Refs for Node: %s, when removing: %s",
          refsForNode,
          ref);
      checkState(
          refsForNode.contains(ref),
          "Refs for Node: %s does not contain Ref to remove: %s",
          refsForNode,
          ref);
      checkState(
          refsForNode.contains(twinRef),
          "Refs for Node: %s does not contain expected twin: %s",
          refsForNode,
          twinRef);
      refsForNodeMap.remove(refNode);
    }

    Collection<Ref> getRefs() {
      return refs == null ? ImmutableList.of() : Collections.unmodifiableCollection(refs);
    }

    /**
     * Get the Refs for this name that belong to the given node.
     *
     * <p>Returns an empty list if there are no Refs, or a list with only one Ref, or a list with
     * exactly 2 refs that are twins of each other.
     */
    @VisibleForTesting
    ImmutableList<Ref> getRefsForNode(Node node) {
      ImmutableList<Ref> refsForNode = refsForNodeMap.get(checkNotNull(node));
      return (refsForNode == null) ? ImmutableList.of() : refsForNode;
    }

    Ref getFirstRef() {
      checkState(!refs.isEmpty(), "no first Ref to get");
      return Iterables.get(refs, 0);
    }

    boolean canEliminate() {
      if (!canCollapseUnannotatedChildNames() || totalGets > 0) {
        return false;
      }

      if (props != null) {
        for (Name n : props) {
          if (!n.canCollapse()) {
            return false;
          }
        }
      }
      return true;
    }

    boolean isSimpleStubDeclaration() {
      if (getRefs().size() == 1) {
        Ref ref = Iterables.get(refs, 0);
        if (ref.node.getParent().isExprResult()) {
          return true;
        }
      }
      return false;
    }

    boolean isCollapsingExplicitlyDenied() {
      JSDocInfo docInfo = getJSDocInfo();
      return docInfo != null && docInfo.isNoCollapse();
    }

    /**
     * Returns whether to treat this alias as completely inlinable or to keep the aliasing
     * assignment
     *
     * <p>This method used to only return true/false, but now returns an enum in order to track more
     * information about "unsafely" inlinable names.
     *
     * <p>CollapseProperties will flatten `@constructor` properties even if they are potentially
     * accessed by a reference other than their fully qualified name, which breaks those other refs.
     * To avoid breakages AggressiveInlineAliases must unsafely inline constructor properties that
     * alias another global name. Existing code depends on this behavior, and it's not easily
     * determinable where these dependencies are.
     *
     * <p>However, AggressiveInlineAliases must not also remove the initializtion of an alias if it
     * is not safely inlinable. (i.e. if Inlinability#shouldRemoveDeclaration()). It's possible that
     * a third name aliases the alias - we might later inline the third name (as an alias of the
     * original alias) and don't want to set the third name to null.
     */
    Inlinability calculateInlinability() {
      // Only simple aliases with direct usage are inlinable.
      if (inExterns() || globalSets != 1 || localSets != 0) {
        return Inlinability.DO_NOT_INLINE;
      }

      // TODO(lharker): consider separating canCollapseOrInline() into this method, since it
      // duplicates some logic here
      Inlinability collapsibility = canCollapseOrInline();
      if (!collapsibility.shouldInlineUsages()) {
        // if you can't even inline the usages, do nothing.
        return Inlinability.DO_NOT_INLINE;
      }

      // Only allow inlining of simple references.
      for (Ref ref : getRefs()) {
        switch (ref.type) {
          case SET_FROM_GLOBAL:
            // Expect one global set
            continue;
          case SET_FROM_LOCAL:
            throw new IllegalStateException();
          case ALIASING_GET:
          case DIRECT_GET:
          case PROTOTYPE_GET:
          case CALL_GET:
          case SUBCLASSING_GET:
            continue;
          case DELETE_PROP:
            return Inlinability.DO_NOT_INLINE;
          default:
            throw new IllegalStateException();
        }
      }
      return collapsibility;
    }

    boolean canCollapse() {
      return canCollapseOrInline().canCollapse();
    }

    /**
     * Determines whether it's safe to collapse properties on an objects
     *
     * <p>For legacy reasons, both CollapseProperties and AggressiveInlineAliases share the same
     * logic when deciding whether to inline properties or to collapse them.
     *
     * <p>The main reasons we cannot inline/collapse properties of a name name are:
     *
     * <pre>
     *   a) it is set multiple times or set once in a local scope,
     *   b) one or more of the above conditions in canCollapseOrInlineChildNames
     *      applies to the namespace it's on,
     *   c) it's annotated at-nocollapse
     *   d) it's in the externs,
     *   e) or it's a known getter or setter, not a regular property
     *   f) it's an ES6 class static method that references `super` or the internal class name
     * </pre>
     *
     * <p>We ignore conditions (a) and (b) on at-constructor and at-enum names in
     * CollapseProperties.
     *
     * <p>In AggressiveInlineAliases we want to do some partial backoff if (a) and (b) are false for
     * at-constructor or at-enum names, which is why we return an enum value instead of a boolean.
     */
    private Inlinability canCollapseOrInline() {
      if (inExterns()) {
        // condition (d)
        return Inlinability.DO_NOT_INLINE;
      }
      if (isGetOrSetDefinition()) {
        // condition (e)
        return Inlinability.DO_NOT_INLINE;
      }
      if (isCollapsingExplicitlyDenied()) {
        // condition (c)
        return Inlinability.DO_NOT_INLINE;
      }

      if (referencesSuperOrInnerClassName()) {
        // condition (f)
        return Inlinability.DO_NOT_INLINE;
      }

      if (getDeclaration() != null) {
        Node declaration = getDeclaration().getNode();
        if (declaration.getParent().isObjectLit()) {
          if (declarationHasFollowingObjectSpreadSibling(declaration)) {
            // Case: `var x = {a: 0, ...b, c: 2}` where declaration is `a` but not `c`.
            // Following spreads may overwrite the declaration.
            return Inlinability.DO_NOT_INLINE;
          }
          Node grandparent = declaration.getGrandparent();
          if (grandparent.isOr() || grandparent.isHook()) {
            // Case: `var x = y || {a: b}` or `var x = cond ? y : {a: b}`.
            return Inlinability.DO_NOT_INLINE;
          }
        }
      }

      // condition (a)
      boolean isUnchangedThroughFullName =
          (globalSets > 0 || localSets > 0) && localSetsWithNoCollapse == 0 && deleteProps == 0;
      // additional information about condition (b)
      Inlinability parentInlinability =
          parent == null ? Inlinability.INLINE_COMPLETELY : parent.canCollapseOrInlineChildNames();

      // if condition (a) or condition (b) is not true, but this is a declared name, we may need
      // to allow inlining usages of a variable but keep the declaration.
      switch (parentInlinability) {
        case INLINE_COMPLETELY:
          if (isUnchangedThroughFullName) {
            return Inlinability.INLINE_COMPLETELY;
          }
          // maybe inline usages of this name, but only if a declared type. non-declared-types just
          // back off and don't inline at all
          return declaredType
              ? Inlinability.INLINE_BUT_KEEP_DECLARATION
              : Inlinability.DO_NOT_INLINE;

        case INLINE_BUT_KEEP_DECLARATION:
          // this is definitely not safe to completely inline/collapse of its parent
          // if it's a declared type, we should still partially inline it and completely collapse it
          // if not a declared type we should partially inline it iff the other conditions hold
          if (declaredType) {
            return Inlinability.INLINE_BUT_KEEP_DECLARATION;
          }
          // Not a declared type. We may still 'partially' inline it because it must be a property
          // on an @enum or @constructor, but only if it actually matches conditions (a) and (b)
          return isUnchangedThroughFullName
              ? Inlinability.INLINE_BUT_KEEP_DECLARATION
              : Inlinability.DO_NOT_INLINE;

        case DO_NOT_INLINE:
          // If the parent is unsafely to collapse/inline, we will still inline it if it's on
          // a declaredType (i.e. @constructor or @enum), but we propagate the information that
          // the parent is unsafe. If this is not a declared type, return DO_NOT_INLINE.
          return declaredType
              ? Inlinability.INLINE_BUT_KEEP_DECLARATION
              : Inlinability.DO_NOT_INLINE;
      }
      throw new IllegalStateException("unknown enum value " + parentInlinability);
    }

    /**
     * Examines ES6 class members for some syntax that blocks collapsing
     *
     * <p>Specifically, this looks for super references and references to inner class names. These
     * are unique to ES6 static class members so we don't need more general handling.
     *
     * <p>TODO(b/122665204): also return false on `super` in an object lit method
     */
    boolean referencesSuperOrInnerClassName() {
      Ref ref = this.getDeclaration();
      if (ref == null) {
        return false;
      }
      Node member = ref.getNode();
      if (member == null || !(member.isStaticMember() && member.getParent().isClassMembers())) {
        return false;
      }
      if (NodeUtil.referencesSuper(NodeUtil.getFunctionBody(member.getFirstChild()))) {
        return true;
      }

      Node classNode = member.getGrandparent();
      if (NodeUtil.isClassDeclaration(classNode)) {
        return false; // e.g. class C {}
      }

      Node innerNameNode = classNode.getFirstChild();
      return !innerNameNode.isEmpty() // e.g. const C = class {};
          && NodeUtil.isNameReferenced(member, innerNameNode.getString());
    }

    private boolean isSetInLoop() {
      Ref ref = this.getDeclaration();
      if (ref != null) {
        Node n = ref.getNode();
        if (n != null) {
          return NodeUtil.isWithinLoop(n);
        }
      }
      return false;
    }

    boolean isGetOrSetDefinition() {
      return this.type == NameType.GET_SET;
    }

    boolean canCollapseUnannotatedChildNames() {
      return canCollapseOrInlineChildNames().canCollapse();
    }

    /**
     * Returns whether to assume that child properties of this name are collapsible/inlinable
     *
     * <p>For legacy reasons, both CollapseProperties and AggressiveInlineAliases share the same
     * logic when deciding whether to inline properties or to collapse them.
     *
     * <p>The main reasons we cannot inline/collapse properties of a name name are:
     *
     * <pre>
     *   a) it is set multiple times
     *   b) its properties might not be referred to by their full qname but on a different object
     *   c) one or more of the above conditions applies to a parent name
     *   d) it's annotated @nocollapse
     *   e) it's in the externs.
     *   f) it's assigned a value that supports being aliased (e.g. ctors, so their static
     *      properties can be accessed via `this`)
     * </pre>
     *
     * <p>However, in some cases for properties of `@constructor` or `@enum` names, we ignore some
     * of these conditions in order to more aggressively collapse `@constructor`s used in
     * goog.provide namespace chains.
     */
    private Inlinability canCollapseOrInlineChildNames() {
      if (type == NameType.OTHER
          || isGetOrSetDefinition()
          || globalSets != 1
          || localSets != 0
          || deleteProps != 0) {
        // condition (a) and (b)
        return Inlinability.DO_NOT_INLINE;
      }

      // Don't try to collapse if the one global set is a twin reference.
      // We could theoretically handle this case in CollapseProperties, but
      // it's probably not worth the effort.
      checkNotNull(declaration);
      if (declaration.getTwin() != null) {
        return Inlinability.DO_NOT_INLINE;
      }

      if (isCollapsingExplicitlyDenied()) {
        // condition (d)
        return Inlinability.DO_NOT_INLINE;
      }

      if (isSetInLoop()) {
        // condition (a)
        return Inlinability.DO_NOT_INLINE;
      }

      if (usedHasOwnProperty) {
        // condition (b)
        return Inlinability.DO_NOT_INLINE;
      }

      if (valueImplicitlySupportsAliasing()) {
        // condition (f)
        return Inlinability.DO_NOT_INLINE;
      }

      // If this is a key of an aliased object literal, then it will be aliased
      // later. So we won't be able to collapse its properties.
      // condition (b)
      if (parent != null && parent.shouldKeepKeys()) {
        return declaredType ? Inlinability.INLINE_BUT_KEEP_DECLARATION : Inlinability.DO_NOT_INLINE;
      }

      // If this is aliased, then its properties can't be collapsed either. but we may do so anyway
      // if it's a declared type.
      // condition (b)
      if (aliasingGets > 0) {
        return declaredType ? Inlinability.INLINE_BUT_KEEP_DECLARATION : Inlinability.DO_NOT_INLINE;
      }

      if (parent == null) {
        // this is completely safe to inline! yay
        return Inlinability.INLINE_COMPLETELY;
      }

      // Cases are:
      //  - parent is safe to completely inline. then same for this name
      //  - parent is unsafe but should still be inlined. then same for this name
      //  - parent is unsafe, should not be inlined at all. then return either DO_NOT_INLINE,
      //    or maybe unsafely inline if this is a ctor property
      Inlinability parentInlinability = parent.canCollapseOrInlineChildNames();
      if (parentInlinability == Inlinability.DO_NOT_INLINE) {
        // the parent name is used in a way making this unsafe to inline, but we might want to
        // inline usages of this name
        return declaredType ? Inlinability.INLINE_BUT_KEEP_DECLARATION : Inlinability.DO_NOT_INLINE;
      }
      return parentInlinability;
    }

    private boolean valueImplicitlySupportsAliasing() {
      if (!GlobalNamespace.this.enableImplicityAliasedValues) {
        return false;
      }

      switch (type) {
        case CLASS:
          // Properties on classes may be referenced via `this` in static methods.
          return true;
        case FUNCTION:
          // We want ES5 ctors/interfaces to behave consistently with ES6 because:
          // - transpilation should not change behaviour
          // - updating code shouldn't be hindered by behaviour changes
          @Nullable JSDocInfo jsdoc = getJSDocInfo();
          return jsdoc != null && jsdoc.isConstructorOrInterface();
        default:
          return false;
      }
    }

    /** Whether this is an object literal that needs to keep its keys. */
    boolean shouldKeepKeys() {
      return type == NameType.OBJECTLIT && (aliasingGets > 0 || isCollapsingExplicitlyDenied());
    }

    boolean needsToBeStubbed() {
      return globalSets == 0
          && localSets > 0
          && localSetsWithNoCollapse == 0
          && !isCollapsingExplicitlyDenied();
    }

    void setDeclaredType() {
      declaredType = true;
      for (Name ancestor = parent; ancestor != null; ancestor = ancestor.parent) {
        ancestor.isDeclared = true;
      }
    }

    boolean isDeclaredType() {
      return declaredType;
    }

    boolean isConstructor() {
      Node declNode = declaration.node;
      Node rvalueNode = NodeUtil.getRValueOfLValue(declNode);
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(declNode);
      return rvalueNode != null
          && rvalueNode.isFunction()
          && jsdoc != null
          && jsdoc.isConstructor();
    }

    /**
     * Determines whether this name is a prefix of at least one class or enum name. Because classes
     * and enums are always collapsed, the namespace will have different properties in compiled code
     * than in uncompiled code.
     *
     * <p>For example, if foo.bar.DomHelper is a class, then foo and foo.bar are considered
     * namespaces.
     */
    boolean isNamespaceObjectLit() {
      return isDeclared && type == NameType.OBJECTLIT;
    }

    /** Determines whether this is a simple name (as opposed to a qualified name). */
    boolean isSimpleName() {
      return parent == null;
    }

    @Override
    public String toString() {
      return getFullName()
          + " ("
          + type
          + "): "
          + Joiner.on(", ")
              .join(
                  "globalSets=" + globalSets,
                  "localSets=" + localSets,
                  "totalGets=" + totalGets,
                  "aliasingGets=" + aliasingGets,
                  "callGets=" + callGets,
                  "subclassingGets=" + subclassingGets);
    }

    @Nullable
    @Override
    public JSDocInfo getJSDocInfo() {
      // e.g.
      // /** @type {string} */ X.numProp;     // could be a declaration, but...
      // /** @type {number} */ X.numProp = 3; // assignment wins
      return firstDeclarationJSDocInfo != null
          ? firstDeclarationJSDocInfo
          : firstQnameDeclarationWithoutAssignmentJsDocInfo;
    }

    /** Tries to get the doc info for a given declaration ref. */
    private JSDocInfo getDocInfoForDeclaration(Ref ref) {
      if (ref.node != null) {
        Node refParent = ref.node.getParent();
        if (refParent == null) {
          // May happen when inlineAliases removes refs from the AST.
          return null;
        }
        switch (refParent.getToken()) {
          case FUNCTION:
          case ASSIGN:
          case CLASS:
            return refParent.getJSDocInfo();
          case VAR:
          case LET:
          case CONST:
            return ref.node == refParent.getFirstChild()
                ? refParent.getJSDocInfo()
                : ref.node.getJSDocInfo();
          case OBJECTLIT:
          case CLASS_MEMBERS:
            return ref.node.getJSDocInfo();
          default:
            break;
        }
      }

      return null;
    }

    boolean isModuleExport() {
      return isModuleProp;
    }
  }

  /**
   * True if the given Node is the GETPROP in a statement like `some.q.name;`
   *
   * <p>Such do-nothing statements often have JSDoc on them and are intended to declare the
   * qualified name.
   *
   * @param node any Node, or even null
   */
  private static boolean isQnameDeclarationWithoutAssignment(@Nullable Node node) {
    return node != null && node.isGetProp() && node.getParent().isExprResult();
  }

  // -------------------------------------------------------------------------

  /**
   * A global name reference. Contains references to the relevant parse tree node and its ancestors
   * that may be affected.
   */
  static class Ref implements StaticRef {

    // Note: we are more aggressive about collapsing @enum and @constructor
    // declarations than implied here, see Name#canCollapse
    enum Type {
      /**
       * Set in the scope in which a name is declared, either the global scope or a module scope:
       * `a.b.c = 0;` or `goog.module('mod'); exports.Foo = class {};`
       */
      SET_FROM_GLOBAL, // TODO(lharker): rename this to explain it includes modules

      /** Set in a local scope: function f() { a.b.c = 0; } */
      SET_FROM_LOCAL,

      /** Get a name's prototype: a.b.c.prototype */
      PROTOTYPE_GET,

      /**
       * Includes all uses that prevent a name's properties from being collapsed: var x = a.b.c
       * f(a.b.c) new Foo(a.b.c)
       */
      ALIASING_GET,

      /**
       * Includes all uses that prevent a name from being completely eliminated:
       * goog.inherits(anotherName, a.b.c) new a.b.c() x instanceof a.b.c void a.b.c if (a.b.c) {}
       */
      DIRECT_GET,

      /** Calling a name: a.b.c(); Prevents a name from being collapsed if never set. */
      CALL_GET,

      /** Deletion of a property: delete a.b.c; Prevents a name from being collapsed at all. */
      DELETE_PROP,

      /** ES6 subclassing ref: class extends A {} */
      SUBCLASSING_GET,
    }

    // Not final because CollapseProperties needs to update the namespace in-place.
    private Node node;
    final JSModule module;
    final Name name;
    final Type type;
    /**
     * The scope in which the reference is resolved. Note that for ALIASING_GETS like "var x = ns;"
     * this scope may not be the correct hoist scope of the aliasing VAR.
     */
    final Scope scope;

    final int preOrderIndex;

    /**
     * Certain types of references are actually double-refs. For example, var a = b = 0; counts as
     * both a "set" of b and an "alias" of b.
     *
     * <p>We create two Refs for this node, and mark them as twins of each other.
     */
    private Ref twin = null;

    /**
     * Creates a Ref
     *
     * <p>No parameter checking is done here, because we allow nulls for several fields in Refs
     * created just for testing. However, all Refs for real use must be created by methods on the
     * Name class, which does do argument checking.
     */
    private Ref(JSModule module, Scope scope, Node node, Name name, Type type, int index) {
      this.node = node;
      this.name = name;
      this.module = module;
      this.type = type;
      this.scope = scope;
      this.preOrderIndex = index;
    }

    @Override
    public Node getNode() {
      return node;
    }

    @Override
    public StaticSourceFile getSourceFile() {
      return node != null ? node.getStaticSourceFile() : null;
    }

    @Override
    public StaticSlot getSymbol() {
      return name;
    }

    JSModule getModule() {
      return module;
    }

    /** Returns the corresponding read/write Ref of a name in a nested assign, or null otherwise */
    Ref getTwin() {
      return twin;
    }

    boolean isSet() {
      return type == Type.SET_FROM_GLOBAL || type == Type.SET_FROM_LOCAL;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .omitNullValues()
          .add("name", name)
          .add("type", type)
          .add("node", node)
          .add("preOrderIndex", preOrderIndex)
          .add("isTwin", twin != null)
          .add("module", module)
          .add("scope", scope)
          .toString();
    }
  }
}
