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

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;
import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.jscomp.base.format.SimpleFormat;
import com.google.javascript.jscomp.diagnostic.LogFile;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jspecify.nullness.Nullable;

/**
 * Builds a namespace of all qualified names whose root is in the global scope or a module, plus an
 * index of all references to those global names.
 *
 * <p>This class tracks assignments to qualified names (e.g. `a.b.c`), because we often want to
 * treat them as if they were global variables (e.g. CollapseProperties). However, when a qualified
 * name begins an optional chain, we will not consider the optional chain to be part of the
 * qualified name (e.g. `a.b?.c` is the qualified name `a.b` with an optional reference to property
 * `c`.) We will record such optional chains as ALIASING_GET references to the non-optional
 * qualified name part.
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
  private final boolean enableImplicitlyAliasedValues;
  private final Node root;
  private final Node externsRoot;
  private final Node globalRoot = IR.root();
  private final LinkedHashMap<Node, Boolean> spreadSiblingCache = new LinkedHashMap<>();
  private SourceKind sourceKind;
  private boolean generated = false;

  /**
   * Records decisions made by this class.
   *
   * <p>Since this class is a utility used by others, the creating class may provide the log file.
   */
  private final @Nullable LogFile decisionsLog;

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

  /** Global namespace tree */
  private final List<Name> globalNames = new ArrayList<>();

  /** Maps names (e.g. "a.b.c") to nodes in the global namespace tree */
  private final Map<String, Name> nameMap = new LinkedHashMap<>();

  /** Maps names (e.g. "a.b.c") and MODULE_BODY nodes to Names in that module */
  private final Table<ModuleMetadata, String, Name> nameMapByModule = HashBasedTable.create();

  /** Limits traversal to scripts matching the given predicate. */
  private Predicate<Node> shouldTraverseScript = (n) -> true;

  /**
   * Creates an instance that may emit warnings when building the namespace.
   *
   * @param compiler The AbstractCompiler, for reporting code changes
   * @param root The root of the rest of the code to build a namespace for.
   */
  GlobalNamespace(LogFile decisionsLog, AbstractCompiler compiler, Node root) {
    this(decisionsLog, compiler, null, root);
  }

  /**
   * Creates an instance that may emit warnings when building the namespace.
   *
   * @param compiler The AbstractCompiler, for reporting code changes
   * @param root The root of the rest of the code to build a namespace for.
   */
  GlobalNamespace(AbstractCompiler compiler, Node root) {
    this(/* decisionsLog= */ null, compiler, null, root);
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
    this(null, compiler, externsRoot, root);
  }

  /**
   * Creates an instance that may emit warnings when building the namespace.
   *
   * @param decisionsLog where to log decisions made by this instance
   * @param compiler The AbstractCompiler, for reporting code changes
   * @param externsRoot The root of the externs to build a namespace for. If this is null, externs
   *     and properties defined on extern types will not be included in the global namespace. If
   *     non-null, it allows user-defined function on extern types to be included in the global
   *     namespace. E.g. String.foo.
   * @param root The root of the rest of the code to build a namespace for.
   */
  GlobalNamespace(
      @Nullable LogFile decisionsLog,
      AbstractCompiler compiler,
      @Nullable Node externsRoot,
      Node root) {
    this.decisionsLog = decisionsLog;
    this.compiler = compiler;
    this.externsRoot = externsRoot;
    this.root = root;
    this.enableImplicitlyAliasedValues =
        !compiler.getOptions().getAssumeStaticInheritanceIsNotUsed();
  }

  void setShouldTraverseScriptPredicate(Predicate<Node> shouldTraverseScript) {
    this.shouldTraverseScript = shouldTraverseScript;
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
  private @Nullable Node getRootNode(String name, Scope s) {
    name = getTopVarName(name);
    Var v = s.getVar(name);
    if (v == null) {
      Name providedName = nameMap.get(name);
      return providedName != null && providedName.getBooleanProperty(NameProp.IS_PROVIDED)
          ? globalRoot
          : null;
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
  public Collection<Name> getAllSymbols() {
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
    final Scope scope;
    final Node node;

    AstChange(Scope scope, Node node) {
      this.scope = scope;
      this.node = node;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof AstChange) {
        AstChange other = (AstChange) obj;
        return Objects.equals(this.scope, other.scope) && Objects.equals(this.node, other.node);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.scope, this.node);
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
      scanFromNode(builder, info.scope, info.node);
    }
  }

  private void scanFromNode(BuildGlobalNamespace builder, Scope scope, Node n) {
    // Check affected parent nodes first.
    Node parent = n.getParent();
    if ((n.isName() || n.isGetProp()) && parent.isGetProp()) {
      // e.g. when replacing "my.alias.prop" with "foo.bar.prop"
      // we want also want to visit "foo.bar.prop", since that's a new global qname we are now
      // referencing.
      scanFromNode(builder, scope, n.getParent());
    } else if (n.getPrevious() != null && n.getPrevious().isObjectPattern()) {
      // e.g. if we change `const {x} = bar` to `const {x} = foo`, add a new reference to `foo.x`
      // attached to the STRING_KEY `x`
      Node pattern = n.getPrevious();
      for (Node key = pattern.getFirstChild(); key != null; key = key.getNext()) {
        if (key.isStringKey()) {
          scanFromNode(builder, scope, key);
        }
      }
    }
    builder.collect(scope, n);
  }

  /** Builds the namespace lazily. */
  private void process() {
    NodeTraversal.Builder traversal =
        NodeTraversal.builder().setCompiler(compiler).setCallback(new BuildGlobalNamespace());

    if (hasExternsRoot()) {
      traversal.traverseRoots(externsRoot, root);
    } else {
      traversal.traverse(root);
    }

    generated = true;
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

  @Nullable Name getNameFromModule(ModuleMetadata moduleMetadata, String name) {
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

    /*
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
    private @Nullable Node curModuleRoot = null;
    private @Nullable ModuleMetadata curMetadata = null;
    /** Collect the references in pre-order. */
    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      if (n.isScript() && !shouldTraverseScript.test(n)) {
        return false;
      }
      if (hasExternsRoot() && n.isScript()) {
        // When checking type-summary files, we want to consider them like normal code
        // for some things (like alias inlining) but like externs for other things.
        sourceKind = SourceKind.fromScriptNode(n);
      } else if (n == root) {
        sourceKind = SourceKind.CODE;
      }
      if (n.isModuleBody() || NodeUtil.isBundledGoogModuleScopeRoot(n)) {
        setupModuleMetadata(n);
      } else if (n.isScript() || NodeUtil.isBundledGoogModuleCall(n)) {
        curModuleRoot = null;
        curMetadata = null;
      }

      collect(t.getScope(), n);

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

    private void collect(Scope scope, Node n) {
      Node parent = n.getParent();

      String name;
      boolean isSet = false;
      NameProp type = NameProp.OTHER_OBJECT;

      switch (n.getToken()) {
        case GETTER_DEF:
        case SETTER_DEF:
        case MEMBER_FUNCTION_DEF:
          if (parent.isClassMembers() && !n.isStaticMember()) {
            return; // Within a class, only static members define global names.
          }
          name = NodeUtil.getBestLValueName(n);
          isSet = true;
          type = n.isMemberFunctionDef() ? NameProp.FUNCTION : NameProp.GET_SET;
          break;
        case STRING_KEY:
          name = null;
          if (parent.isObjectLit()) {
            ObjLitStringKeyAnalysis analysis = createObjLitStringKeyAnalysis(n);
            name = analysis.getNameString();
            type = analysis.getNameType();
            isSet = true;
          } else if (parent.isObjectPattern()) {
            name = getNameForObjectPatternKey(n);
            type = getValueType(n.getFirstChild());
            // not a set
          } // else not a reference we should record
          break;
        case NAME:
        case GETPROP:
          // OPTCHAIN_GETPROP is intentionally not included in this case.
          // "a.b?.c" is not a reference to the global name "a.b.c" for the
          // purposes of GlobalNamespace.
          // TODO(b/127505242): CAST parents may indicate a set.
          // This may be a variable get or set.
          switch (parent.getToken()) {
            case VAR:
            case LET:
            case CONST:
              isSet = true;
              Node rvalue = n.getFirstChild();
              type = (rvalue == null) ? NameProp.OTHER_OBJECT : getValueType(rvalue);
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
              type = NameProp.FUNCTION;
              break;
            case CATCH:
            case INC:
            case DEC:
              isSet = true;
              type = NameProp.OTHER_OBJECT;
              break;
            case CLASS:
              // The first child is the class name, and the second child is the superclass name.
              if (parent.getFirstChild() == n) {
                isSet = true;
                type = NameProp.CLASS;
              }
              break;
            case STRING_KEY:
            case ARRAY_PATTERN:
            case DEFAULT_VALUE:
            case COMPUTED_PROP:
            case ITER_REST:
            case OBJECT_REST:
              // This may be a set.
              // TODO(b/120303257): this should extend to qnames too, but doing
              // so causes invalid output. Covered in CollapsePropertiesTest
              if (n.isName() && NodeUtil.isLhsByDestructuring(n)) {
                isSet = true;
                type = NameProp.OTHER_OBJECT;
              }
              break;
            case ITER_SPREAD:
            case OBJECT_SPREAD:
              break; // isSet = false, type = OTHER.
            case CALL:
              if (n.isFirstChildOf(parent) && isObjectHasOwnPropertyCall(parent)) {
                String qname = n.getFirstChild().getQualifiedName();
                Name globalName = getOrCreateName(qname, curMetadata);
                globalName.setBooleanProperty(NameProp.IS_USED_HAS_OWN_PROPERTY);
              }
              break;
            default:
              if (NodeUtil.isAssignmentOp(parent) && parent.getFirstChild() == n) {
                isSet = true;
                type = NameProp.OTHER_OBJECT;
              }
          }
          if (!n.isQualifiedName()) {
            return;
          }
          name = n.getQualifiedName();
          break;

        case CALL:
          if (parent.isExprResult()
              && GOOG_PROVIDE.matches(n.getFirstChild())
              && n.getSecondChild().isStringLit()) {
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
          handleSetFromGlobal(scope, n, name, type, nameMetadata);
        } else {
          handleSetFromLocal(scope, n, name, nameMetadata);
        }
      } else {
        handleGet(scope, n, name, nameMetadata);
      }
    }

    private ObjLitStringKeyAnalysis createObjLitStringKeyAnalysis(Node stringKeyNode) {
      String nameString = NodeUtil.getBestLValueName(stringKeyNode);
      if (nameString != null) {
        // `parent.qname = { myPropName: myValue }`;
        // `NodeUtil.getBestLValueName()` finds the name being assigned for this case.
        return ObjLitStringKeyAnalysis.forObjLitAssignment(
            nameString, getValueType(stringKeyNode.getOnlyChild()));
      } else {
        // maybe we have a case like
        // `Object.defineProperties(parentName, { myPropName: { get: ..., set: ..., ... })`
        Node objLitNode = stringKeyNode.getParent();
        checkArgument(objLitNode.isObjectLit(), objLitNode);
        Node objLitParentNode = objLitNode.getParent();
        if (NodeUtil.isObjectDefinePropertiesDefinition(objLitParentNode)) {
          Node receiverNode = objLitParentNode.getSecondChild();
          if (receiverNode.isQualifiedName()) {
            checkState(objLitNode == receiverNode.getNext(), objLitParentNode);
            nameString = receiverNode.getQualifiedName() + "." + stringKeyNode.getString();
            return ObjLitStringKeyAnalysis.forObjectDefineProperty(nameString);
          }
        }
        return ObjLitStringKeyAnalysis.forNonReference();
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
        name.setBooleanProperty(NameProp.IS_PROVIDED);
      }

      Name newName = getOrCreateName(namespace, null);
      newName.setBooleanProperty(NameProp.IS_PROVIDED);
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
    @Nullable String getNameForObjectPatternKey(Node stringKey) {
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
     */
    NameProp getValueType(Node n) {
      switch (n.getToken()) {
        case CLASS:
          return NameProp.CLASS;
        case OBJECTLIT:
          return NameProp.OBJECTLIT;
        case FUNCTION:
          return NameProp.FUNCTION;
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
          NameProp t = getValueType(second);
          if (t != NameProp.OTHER_OBJECT) {
            return t;
          }
          Node third = second.getNext();
          return getValueType(third);
        default:
          break;
      }
      return NameProp.OTHER_OBJECT;
    }

    /**
     * Updates our representation of the global namespace to reflect an assignment to a global name
     * in any scope where variables are hoisted to the global scope (i.e. the global scope in an ES5
     * sense).
     *
     * @param scope the current scope
     * @param n The node currently being visited
     * @param name The global name (e.g. "a" or "a.b.c.d")
     * @param type The type of the value that the name is being assigned
     */
    void handleSetFromGlobal(
        Scope scope, Node n, String name, NameProp type, ModuleMetadata metadata) {
      if (maybeHandlePrototypePrefix(scope, n, name, metadata)) {
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
        nameObj.setNameType(type);
      }
      if (n.getBooleanProp(Node.MODULE_EXPORT)) {
        nameObj.setBooleanProperty(NameProp.IS_MODULE_PROP);
      }

      if (isNestedAssign(n.getParent())) {
        // This assignment is both a set and a get that creates an alias.
        Ref.Type refType = Ref.Type.GET_AND_SET_FROM_GLOBAL;
        addOrConfirmRef(nameObj, n, refType, scope);
      } else {
        addOrConfirmRef(nameObj, n, Ref.Type.SET_FROM_GLOBAL, scope);
        nameObj.setDeclaredTypeKind(getDeclaredTypeKind(n));
      }
    }

    /**
     * Determines whether a set operation is a constructor or enumeration or interface declaration.
     * The set operation may either be an assignment to a name, a variable declaration, or an object
     * literal key mapping.
     *
     * @param n The node that represents the name being set
     */
    private NameProp getDeclaredTypeKind(Node n) {
      Node valueNode = NodeUtil.getRValueOfLValue(n);
      final NameProp kind;
      if (valueNode == null) {
        kind = NameProp.NOT_A_TYPE;
      } else if (valueNode.isClass()) {
        // Always treat classes as having a declared type. (Transpiled classes are annotated
        // @constructor)
        kind = NameProp.CONSTRUCTOR_TYPE;
      } else {
        JSDocInfo info = NodeUtil.getBestJSDocInfo(n);
        // Heed the annotations only if they're sensibly used.
        if (info == null) {
          kind = NameProp.NOT_A_TYPE;
        } else if (info.isConstructor() && valueNode.isFunction()) {
          kind = NameProp.CONSTRUCTOR_TYPE;
        } else if (info.isInterface() && valueNode.isFunction()) {
          kind = NameProp.INTERFACE_TYPE;
        } else if (info.hasEnumParameterType() && valueNode.isObjectLit()) {
          kind = NameProp.ENUM_TYPE;
        } else {
          kind = NameProp.NOT_A_TYPE;
        }
      }
      return kind;
    }

    /**
     * Updates our representation of the global namespace to reflect an assignment to a global name
     * in a local scope.
     *
     * @param scope The current scope
     * @param n The node currently being visited
     * @param name The global name (e.g. "a" or "a.b.c.d")
     */
    void handleSetFromLocal(Scope scope, Node n, String name, ModuleMetadata metadata) {
      if (maybeHandlePrototypePrefix(scope, n, name, metadata)) {
        return;
      }

      Name nameObj = getOrCreateName(name, metadata);
      if (n.getBooleanProp(Node.MODULE_EXPORT)) {
        nameObj.setBooleanProperty(NameProp.IS_MODULE_PROP);
      }

      if (isNestedAssign(n.getParent())) {
        // This assignment is both a set and a get that creates an alias.
        addOrConfirmRef(nameObj, n, Ref.Type.GET_AND_SET_FROM_LOCAL, scope);
      } else {
        addOrConfirmRef(nameObj, n, Ref.Type.SET_FROM_LOCAL, scope);
      }
    }

    /**
     * Updates our representation of the global namespace to reflect a read of a global name.
     *
     * @param scope The current scope
     * @param n The node currently being visited
     * @param name The global name (e.g. "a" or "a.b.c.d")
     */
    void handleGet(Scope scope, Node n, String name, ModuleMetadata metadata) {
      if (maybeHandlePrototypePrefix(scope, n, name, metadata)) {
        return;
      }
      Ref.Type type = determineRefTypeForGet(n, n, name);

      addOrConfirmRef(getOrCreateName(name, metadata), n, type, scope);
    }

    /**
     * Determine the Ref.Type for referenceNode by inspecting parents and recusively ascending as
     * necessary, where n represents.
     *
     * @param n The node currently being visited
     * @param referenceNode The node hosting the name.
     * @param name The global name (e.g. "a" or "a.b.c.d")
     */
    private Ref.Type determineRefTypeForGet(Node n, Node referenceNode, String name) {
      Ref.Type type;
      Node parent = n.getParent();
      switch (parent.getToken()) {
        case EXPR_RESULT:
        case IF:
        case WHILE:
        case FOR:
        case INSTANCEOF:
        case TYPEOF:
        case VOID:
        case NOT:
        case BITNOT:
        case POS:
        case NEG:
        case SHEQ:
        case EQ:
        case SHNE:
        case NE:
        case LT:
        case LE:
        case GT:
        case GE:
        case ADD:
        case SUB:
        case MUL:
        case DIV:
        case MOD:
        case EXPONENT:
        case BITAND:
        case BITOR:
        case BITXOR:
        case LSH:
        case RSH:
        case URSH:
          type = Ref.Type.DIRECT_GET;
          break;
        case OPTCHAIN_CALL:
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
        case CAST:
        case OR:
        case AND:
        case COALESCE:
          // This node is x or y in (x||y), (x&&y), or (x??y). We only know that an
          // alias is not getting created for this name if the result is used
          // in a boolean context or assigned to the same name
          // (e.g. var a = a || {}).
          type = determineRefTypeForGet(parent, referenceNode, name);
          break;
        case NAME:
          // Only LET, CONST, VAR declarations have NAME nodes
          // with children.
          // Of particular interest is "var n = n || {}"
          if (n != referenceNode && name.equals(parent.getString())) {
            type = Ref.Type.DIRECT_GET;
          } else {
            type = Ref.Type.ALIASING_GET;
          }
          break;
        case COMMA:
        case HOOK:
          if (n != parent.getFirstChild()) {
            // This node is y or z in (x?y:z) or (x,y). We only know that an alias is
            // not getting created for this name if the result is assigned to
            // the same name (e.g. var a = a ? a : {}).
            type = determineRefTypeForGet(parent, referenceNode, name);
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
          if (lhs == null) {
            // TODO(b/127505242): CAST confused the "is this a get or set?"
            // logic and "handleGet" should not have been called.
            type = Ref.Type.ALIASING_GET;
            break;
          }
          while (lhs.isCast()) {
            // Case: `/** @type {!Foo} */ (x) = ...`; or multiple casts like `(cast(cast(x)) =`
            lhs = lhs.getOnlyChild();
          }

          // This is a recursive ascent check if this an assignment
          // to itself.  This handles cases like: "a.b = a.b || {}"
          if (n != referenceNode) {
            if (lhs.matchesQualifiedName(name)) {
              return Ref.Type.DIRECT_GET;
            }
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
        case RETURN:
        case THROW:
        default:
          // NOTE: There are likely more cases where we should be returning
          // DIRECT_GET.
          type = Ref.Type.ALIASING_GET;
          break;
      }
      return type;
    }

    /**
     * If there is already a Ref for the given name & node, confirm it matches what we would create.
     * Otherwise add a new one.
     */
    private void addOrConfirmRef(Name nameObj, Node node, Ref.Type refType, Scope scope) {
      Ref existingRef = nameObj.getRefForNode(node);
      if (existingRef == null) {
        nameObj.addRef(scope, node, refType);
      } else {
        // module and scope are dependent on Node, so not much point in checking them
        Ref.Type existingRefType = existingRef.type;
        checkState(
            existingRefType == refType,
            "existing ref type: %s expected: %s",
            existingRefType,
            refType);
      }
    }

    private boolean isClassDefiningCall(Node callNode) {
      CodingConvention convention = compiler.getCodingConvention();
      // Look for goog.inherits and J2CL mixin calls
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
      Node callee = callNode.getFirstChild();
      if (!callee.isGetProp()) {
        return false;
      }
      Node receiver = callee.getFirstChild();
      return "hasOwnProperty".equals(callee.getString()) && receiver.isQualifiedName();
    }

    /**
     * Updates our representation of the global namespace to reflect a read of a global name's
     * longest prefix before the "prototype" property if the name includes the "prototype" property.
     * Does nothing otherwise.
     *
     * @param scope The current scope
     * @param n The node currently being visited
     * @param name The global name (e.g. "a" or "a.b.c.d")
     * @return Whether the name was handled
     */
    boolean maybeHandlePrototypePrefix(Scope scope, Node n, String name, ModuleMetadata metadata) {
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

      if (NodeUtil.mayBeObjectLitKey(n)) {
        // Object literal keys have no prefix that's referenced directly per
        // key, so we're done.
        return true;
      }

      for (int i = 0; i < numLevelsToRemove; i++) {
        n = n.getFirstChild();
      }

      addOrConfirmRef(getOrCreateName(prefix, metadata), n, Ref.Type.PROTOTYPE_GET, scope);
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
    Name getOrCreateName(String name, @Nullable ModuleMetadata metadata) {
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

  /**
   * How much to inline a {@link Name}.
   *
   * <p>The `Inlinability#INLINE_BUT_KEEP_DECLARATION_*` cass are really an indicator that something
   * 'unsafe' is happening in order to not break CollapseProperties as badly. Sadly {@link
   * Inlinability #INLINE_COMPLETELY} may <em>also</em> be unsafe.
   */
  enum Inlinability {
    INLINE_COMPLETELY(
        /* shouldInlineUsages= */ true,
        /* shouldRemoveDeclaration= */ true,
        /* canCollapse= */ true),
    INLINE_BUT_KEEP_DECLARATION_ENUM(
        /* shouldInlineUsages= */ true,
        /* shouldRemoveDeclaration= */ false,
        /* canCollapse= */ true),
    INLINE_BUT_KEEP_DECLARATION_INTERFACE(
        /* shouldInlineUsages= */ true,
        /* shouldRemoveDeclaration= */ false,
        /* canCollapse= */ true),
    INLINE_BUT_KEEP_DECLARATION_CLASS(
        /* shouldInlineUsages= */ true,
        /* shouldRemoveDeclaration= */ false,
        /* canCollapse= */ true),
    DO_NOT_INLINE(
        /* shouldInlineUsages= */ false,
        /* shouldRemoveDeclaration= */ false,
        /* canCollapse= */ false);

    private final boolean shouldInlineUsages;
    private final boolean shouldRemoveDeclaration;
    private final boolean canCollapse;

    Inlinability(boolean shouldInlineUsages, boolean shouldRemoveDeclaration, boolean canCollapse) {
      this.shouldInlineUsages = shouldInlineUsages;
      this.shouldRemoveDeclaration = shouldRemoveDeclaration;
      this.canCollapse = canCollapse;
    }

    boolean shouldInlineUsages() {
      return this.shouldInlineUsages;
    }

    boolean shouldRemoveDeclaration() {
      return this.shouldRemoveDeclaration;
    }

    boolean canCollapse() {
      return this.canCollapse;
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
    private @Nullable Ref declaration;

    /**
     * Keep track of which Nodes are Refs for this Name.
     *
     * <p>This is either null, a {@code Map<Node, Ref>} refsForNodeMap or a singleton Ref.
     *
     * <p>This makes the code using refsForNode more complex but greatly decreases the memory usage
     * of this class. For example, one project had more than 5 million Names, and more than 80% of
     * those Names had exactly 1 Ref, their declaration. So specializing this field to sometimes be
     * a single Ref, not a map, saves on creating > 4 million Map instances.
     */
    private Object refsForNode = null;

    private int globalSets = 0;
    private int localSets = 0;
    private int localSetsWithNoCollapse = 0;
    private int aliasingGets = 0;
    private int totalGets = 0;
    private int callGets = 0;
    private int deleteProps = 0;
    private int subclassingGets = 0;

    /**
     * Bitset containing {@link NameProp}s
     *
     * <p>Using a bit set over boolean fields is a memory optimization. Large projects can have
     * millions of Name objects, so saving a few bytes per Name is useful.
     */
    private int propertyBitSet = 0;

    // Will be set to the JSDocInfo associated with the first SET_FROM_GLOBAL reference added
    // that has JSDocInfo.
    // e.g.
    // /** @type {number} */
    // X.numberProp = 3;
    private @Nullable JSDocInfo firstDeclarationJSDocInfo = null;

    // Will be set to the JSDocInfo associated with the first get reference that is a statement
    // by itself.
    // e.g.
    // /** @type {number} */
    // X.numberProp;
    private @Nullable JSDocInfo firstQnameDeclarationWithoutAssignmentJsDocInfo = null;

    private Name(String name, Name parent, SourceKind sourceKind) {
      this.baseName = name;
      this.parent = parent;
      this.setBooleanProperty(NameProp.NOT_A_TYPE);
      this.setBooleanProperty(NameProp.OTHER_OBJECT);
      if (sourceKind.equals(SourceKind.EXTERN)) {
        this.setBooleanProperty(NameProp.IS_EXTERN);
      }
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
      return this.getBooleanProperty(NameProp.IS_EXTERN);
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

    boolean usesHasOwnProperty() {
      return getBooleanProperty(NameProp.IS_USED_HAS_OWN_PROPERTY);
    }

    @Override
    public @Nullable Ref getDeclaration() {
      return declaration;
    }

    boolean isFunction() {
      return getBooleanProperty(NameProp.FUNCTION);
    }

    boolean isClass() {
      return getBooleanProperty(NameProp.CLASS);
    }

    boolean isObjectLiteral() {
      return getBooleanProperty(NameProp.OBJECTLIT);
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

    int getTotalSets() {
      return globalSets + localSets;
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

    private void setBooleanProperty(NameProp property) {
      this.propertyBitSet = this.propertyBitSet | property.bit;
    }

    private boolean getBooleanProperty(NameProp property) {
      return (this.propertyBitSet & property.bit) != 0;
    }

    private void addRef(Scope scope, Node node, Ref.Type type) {
      checkNoExistingRefsForNode(node);
      Ref ref = createNewRef(scope, node, type);
      putRef(node, ref);
      updateStateForAddedRef(ref);
    }

    private void checkNoExistingRefsForNode(Node node) {
      if (this.refsForNode == null) {
        return;
      }
      if (this.refsForNode instanceof Ref) {
        checkState(
            ((Ref) this.refsForNode).node != node, "Ref already exists for node: %s", refsForNode);
        return;
      }
      Ref refForNode = castRefsForNodeMap().get(node);
      checkState(refForNode == null, "Ref already exists for node: %s", refForNode);
    }

    @SuppressWarnings("unchecked")
    private Map<Node, Ref> castRefsForNodeMap() {
      return (Map<Node, Ref>) refsForNode;
    }

    private Ref createNewRef(Scope scope, Node node, Ref.Type type) {
      return new Ref(
          checkNotNull(scope),
          checkNotNull(node), // may be null later, but not on creation
          type);
    }

    private void putRef(Node node, Ref ref) {
      if (this.refsForNode == null) {
        this.refsForNode = ref;
        return;
      }
      if (refsForNode instanceof Ref) {
        // Convert the singleton Ref object into a map, so that we can store a second Ref.
        Map<Node, Ref> refsForNodeMap = new LinkedHashMap<>();
        Ref existingRef = (Ref) this.refsForNode;
        refsForNodeMap.put(existingRef.node, existingRef);
        this.refsForNode = refsForNodeMap;
      }
      castRefsForNodeMap().put(node, ref);
    }

    Ref addSingleRefForTesting(Node node, Ref.Type type) {
      Ref ref = new Ref(/* scope= */ null, /* node= */ node, type);
      putRef(node, ref);
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
      // TODO(bradfordcsmith): It would be good to add checks that the scope is correct.
      Ref declRef = checkNotNull(declaration);
      addRef(declRef.scope, newRefNode, Ref.Type.ALIASING_GET);
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
        case GET_AND_SET_FROM_GLOBAL:
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
          if (ref.type.equals(Ref.Type.GET_AND_SET_FROM_GLOBAL)) {
            aliasingGets++;
            totalGets++;
          }
          break;
        case GET_AND_SET_FROM_LOCAL:
        case SET_FROM_LOCAL:
          localSets++;
          JSDocInfo info = ref.getNode() == null ? null : NodeUtil.getBestJSDocInfo(ref.getNode());
          if (info != null && info.isNoCollapse()) {
            localSetsWithNoCollapse++;
          }
          if (ref.type.equals(Ref.Type.GET_AND_SET_FROM_LOCAL)) {
            aliasingGets++;
            totalGets++;
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

      if (refsForNode == null) {
        refsForNode = ref;
      } else if (refsForNode instanceof Ref) {
        // No update needed, since refsForNode is a singleton.
        checkState(refsForNode == ref);
      } else {
        Map<Node, Ref> refsForNodeMap = castRefsForNodeMap();
        refsForNodeMap.remove(oldNode);
        if (newNode != null) {
          Ref existingRefForNewNode = refsForNodeMap.get(newNode);
          checkArgument(
              existingRefForNewNode == null, "refs already exist: %s", existingRefForNewNode);
          refsForNodeMap.put(newNode, ref);
        }
      }
    }

    /**
     * Removes the given Ref, which must belong to this Name.
     *
     * <p>NOTE: if this is a twin ref, i.e. both a get and a set of this Name, this removes both the
     * get and the set.
     */
    void removeRef(Ref ref) {
      checkNotNull(refsForNode, "removeRef(%s): unknown ref", ref);
      if (refsForNode instanceof Ref) {
        checkState(refsForNode == ref, "removeRef(%s): unknown ref", ref);
        refsForNode = null;
      } else {
        checkState(
            castRefsForNodeMap().containsKey(ref.getNode()), "removeRef(%s): unknown ref", ref);
        Node refNode = ref.getNode();
        if (refNode != null) {
          removeRefFromNodeMap(ref);
        }
      }
      removeRefAndUpdateState(ref);
    }

    /** Update counts, declaration, and JSDoc to reflect removal of the given Ref. */
    private void removeRefAndUpdateState(Ref ref) {
      if (ref == declaration) {
        declaration = null;
        for (Ref maybeNewDecl : getRefs()) {
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
        case GET_AND_SET_FROM_GLOBAL:
          aliasingGets--;
          totalGets--;
          globalSets--;
          break;
        case SET_FROM_LOCAL:
        case GET_AND_SET_FROM_LOCAL:
          localSets--;
          info = ref.getNode() == null ? null : NodeUtil.getBestJSDocInfo(ref.getNode());
          if (info != null && info.isNoCollapse()) {
            localSetsWithNoCollapse--;
          }
          if (ref.type.equals(Ref.Type.GET_AND_SET_FROM_LOCAL)) {
            aliasingGets--;
            totalGets--;
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

    private void removeRefFromNodeMap(Ref ref) {
      Node refNode = checkNotNull(ref.getNode(), ref);
      checkNotNull(refsForNode, "Missing ref when trying to remove it: %s", ref);
      if (refsForNode instanceof Ref) {
        refsForNode = null;
      } else {
        Ref refsForNode = castRefsForNodeMap().get(refNode);
        checkState(
            refsForNode == ref,
            "Unexpected Refs for Node: %s: when removing Ref: %s",
            refsForNode,
            ref);
        castRefsForNodeMap().remove(refNode);
      }
    }

    Collection<Ref> getRefs() {
      if (refsForNode == null) {
        return ImmutableSet.of();
      }
      if (refsForNode instanceof Ref) {
        return ImmutableSet.of((Ref) refsForNode);
      }
      return castRefsForNodeMap().values();
    }

    /**
     * Get the Ref for this name that belongs to the given node.
     *
     * <p>Returns null if there are no Refs corresponding to the node.
     */
    @VisibleForTesting
    @Nullable Ref getRefForNode(Node node) {
      checkNotNull(node);
      if (refsForNode == null) {
        return null;
      }
      if (refsForNode instanceof Ref) {
        Ref ref = (Ref) refsForNode;
        return ref.getNode() == node ? ref : null;
      }
      return castRefsForNodeMap().get(node);
    }

    Ref getFirstRef() {
      checkNotNull(refsForNode, "no first Ref to get");
      if (refsForNode instanceof Ref) {
        return (Ref) refsForNode;
      }
      return castRefsForNodeMap().values().iterator().next();
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
        Ref ref = getFirstRef();
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
     * Returns whether to treat this alias as completely inlineable or to keep the aliasing
     * assignment
     *
     * <p>This method used to only return true/false, but now returns an enum in order to track more
     * information about "unsafely" inlineable names.
     *
     * <p>CollapseProperties will flatten `@constructor` properties even if they are potentially
     * accessed by a reference other than their fully qualified name, which breaks those other refs.
     * To avoid breakages AggressiveInlineAliases must unsafely inline constructor properties that
     * alias another global name. Existing code depends on this behavior, and it's not easily
     * determinable where these dependencies are.
     *
     * <p>However, AggressiveInlineAliases must not also remove the initializtion of an alias if it
     * is not safely inlineable. (i.e. if Inlinability#shouldRemoveDeclaration()). It's possible
     * that a third name aliases the alias - we might later inline the third name (as an alias of
     * the original alias) and don't want to set the third name to null.
     */
    Inlinability calculateInlinability() {
      // Only simple aliases with direct usage are inlineable.
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
          case GET_AND_SET_FROM_GLOBAL:
            // Expect one global set
            continue;
          case SET_FROM_LOCAL:
          case GET_AND_SET_FROM_LOCAL:
            throw new IllegalStateException();
          case ALIASING_GET:
          case DIRECT_GET:
          case PROTOTYPE_GET:
          case CALL_GET:
          case SUBCLASSING_GET:
            continue;
          case DELETE_PROP:
            return Inlinability.DO_NOT_INLINE;
        }
        throw new AssertionError();
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
    Inlinability canCollapseOrInline() {
      if (inExterns()) {
        // condition (d)
        logDecision(Inlinability.DO_NOT_INLINE, "declared in externs");
        return Inlinability.DO_NOT_INLINE;
      }
      if (isGetOrSetDefinition()) {
        // condition (e)
        logDecision(Inlinability.DO_NOT_INLINE, "getter / setter");
        return Inlinability.DO_NOT_INLINE;
      }
      if (isCollapsingExplicitlyDenied()) {
        // condition (c)
        logDecision(Inlinability.DO_NOT_INLINE, "@nocollapse");
        return Inlinability.DO_NOT_INLINE;
      }

      if (referencesSuperOrInnerClassName()) {
        // condition (f)
        logDecision(Inlinability.DO_NOT_INLINE, "references super or inner class name");
        return Inlinability.DO_NOT_INLINE;
      }

      if (isToStringValueOfInObjectLiteral()) {
        logDecision(
            Inlinability.DO_NOT_INLINE,
            "references explicit definition of toString/valueOf functions used implicitly in the JS"
                + " language");
        return Inlinability.DO_NOT_INLINE;
      }

      if (deleteProps > 0) {
        // If we inline or collapse, then the delete operation will be incorrect.
        logDecision(Inlinability.DO_NOT_INLINE, "delete operator is used on this property");
        return Inlinability.DO_NOT_INLINE;
      }

      if (getDeclaration() != null) {
        Node declaration = getDeclaration().getNode();
        if (declaration.getParent().isObjectLit()) {
          if (declarationHasFollowingObjectSpreadSibling(declaration)) {
            // Case: `var x = {a: 0, ...b, c: 2}` where declaration is `a` but not `c`.
            // Following spreads may overwrite the declaration.
            logDecision(Inlinability.DO_NOT_INLINE, "obj lit property followed by spread");
            return Inlinability.DO_NOT_INLINE;
          }
          // We may be in a deeply nested object literal like, `{a: {b: {c: 1}}}`, so find the
          // outermost object literal node in order to determine whether it is used conditionally.
          final Node objectLitParent = getOutermostObjectLit(declaration).getParent();
          if (objectLitParent.isOr() || objectLitParent.isHook()) {
            // Case: `var x = y || {a: b}` or `var x = cond ? y : {a: b}`.
            logDecision(Inlinability.DO_NOT_INLINE, "conditional definition");
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
            logDecision(
                Inlinability.INLINE_COMPLETELY, "parent inlineable: unchanged through full name");
            return Inlinability.INLINE_COMPLETELY;
          } else {
            // maybe inline usages of this name, but only if a declared type. non-declared-types
            // just
            // back off and don't inline at all
            final Inlinability unsafeInlinablility = getUnsafeInlinabilityBasedOnDeclaredType();
            logDecision(unsafeInlinablility, "parent inlineable: changed through full name");
            return unsafeInlinablility;
          }

        case INLINE_BUT_KEEP_DECLARATION_CLASS:
        case INLINE_BUT_KEEP_DECLARATION_ENUM:
        case INLINE_BUT_KEEP_DECLARATION_INTERFACE:
          // this is definitely not safe to completely inline/collapse of its parent
          // if it's a declared type, we should still partially inline it and completely collapse it
          // if not a declared type we should partially inline it iff the other conditions hold
          if (isDeclaredType()) {
            final Inlinability unsafeInlinability = getUnsafeInlinabilityBasedOnDeclaredType();
            logDecision(unsafeInlinability, "parent unsafely inlineable & is declared type");
            return unsafeInlinability;
          } else if (isUnchangedThroughFullName) {
            logDecision(
                parentInlinability, "parent unsafely inlineable & unchanged through full name");
            return parentInlinability;
          } else {
            // Not a declared type. We may still 'partially' inline it because it must be a property
            // on an @enum or @constructor, but only if it actually matches conditions (a) and (b)
            logDecision(
                Inlinability.DO_NOT_INLINE,
                "parent unsafely inlineable & changed through full name");
            return Inlinability.DO_NOT_INLINE;
          }

        case DO_NOT_INLINE:
          {
            // If the parent is unsafely to collapse/inline, we will still inline it if it's on
            // a declaredType (i.e. @constructor or @enum), but we propagate the information that
            // the parent is unsafe. If this is not a declared type, return DO_NOT_INLINE.
            final Inlinability unsafeInlinability = getUnsafeInlinabilityBasedOnDeclaredType();
            logDecision(unsafeInlinability, "parent cannot be inlined");
            return unsafeInlinability;
          }
      }
      throw new IllegalStateException("unknown enum value " + parentInlinability);
    }

    private void logDecision(Inlinability inlinability, String reason) {
      if (decisionsLog != null && decisionsLog.isLogging()) {
        decisionsLog.log("%s: %s: %s", getFullName(), inlinability, reason);
      }
    }

    private Inlinability getUnsafeInlinabilityBasedOnDeclaredType() {
      if (this.getBooleanProperty(NameProp.CONSTRUCTOR_TYPE)) {
          return Inlinability.INLINE_BUT_KEEP_DECLARATION_CLASS;
      } else if (this.getBooleanProperty(NameProp.INTERFACE_TYPE)) {
          return Inlinability.INLINE_BUT_KEEP_DECLARATION_INTERFACE;
      } else if (this.getBooleanProperty(NameProp.ENUM_TYPE)) {
          return Inlinability.INLINE_BUT_KEEP_DECLARATION_ENUM;
      } else if (this.getBooleanProperty(NameProp.NOT_A_TYPE)) {
          return Inlinability.DO_NOT_INLINE;
      }
      throw new IllegalStateException(
          SimpleFormat.format("name missing declaredType value: %s", this));
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
      return getBooleanProperty(NameProp.GET_SET);
    }

    boolean canCollapseUnannotatedChildNames() {
      return canCollapseOrInlineChildNames().canCollapse();
    }

    // toString/valueOf are implicitly used as part of the JS language should not be collapsed.
    boolean isToStringValueOfInObjectLiteral() {
      Name parent = this.getParent();
      String baseName = this.getBaseName();
      return this.isFunction()
          && parent != null
          && parent.isObjectLiteral()
          && (baseName.equals("toString") || baseName.equals("valueOf"));
    }

    /**
     * Returns whether to assume that child properties of this name are collapsible/inlineable
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
    Inlinability canCollapseOrInlineChildNames() {
      // condition (a) and (b)
      if (getBooleanProperty(NameProp.OTHER_OBJECT)) {
        logChildNamesDecision(Inlinability.DO_NOT_INLINE, "NameProp.OTHER_OBJECT");
        return Inlinability.DO_NOT_INLINE;
      } else if (isGetOrSetDefinition()) {
        logChildNamesDecision(Inlinability.DO_NOT_INLINE, "getter/setter");
        return Inlinability.DO_NOT_INLINE;
      } else if (globalSets != 1) {
        logChildNamesDecision(
            Inlinability.DO_NOT_INLINE,
            () -> SimpleFormat.format("set %d times globally", globalSets));
        return Inlinability.DO_NOT_INLINE;
      } else if (localSets != 0) {
        logChildNamesDecision(
            Inlinability.DO_NOT_INLINE,
            () -> SimpleFormat.format("set %d times locally", localSets));
        return Inlinability.DO_NOT_INLINE;
      } else if (deleteProps != 0) {
        logChildNamesDecision(
            Inlinability.DO_NOT_INLINE,
            () -> SimpleFormat.format("properties are deleted %d times", deleteProps));
        return Inlinability.DO_NOT_INLINE;
      }

      // Don't try to collapse if the one global set is a twin reference.
      // We could theoretically handle this case in CollapseProperties, but
      // it's probably not worth the effort.
      checkNotNull(declaration);
      if (declaration.isTwin()) {
        logChildNamesDecision(Inlinability.DO_NOT_INLINE, "twinned declaration");
        return Inlinability.DO_NOT_INLINE;
      }

      if (isCollapsingExplicitlyDenied()) {
        // condition (d)
        logChildNamesDecision(Inlinability.DO_NOT_INLINE, "@nocollapse");
        return Inlinability.DO_NOT_INLINE;
      }

      if (isSetInLoop()) {
        // condition (a)
        logChildNamesDecision(Inlinability.DO_NOT_INLINE, "set in a loop");
        return Inlinability.DO_NOT_INLINE;
      }

      if (this.usesHasOwnProperty()) {
        // condition (b)
        logChildNamesDecision(Inlinability.DO_NOT_INLINE, "hasOwnProperty() call exists");
        return Inlinability.DO_NOT_INLINE;
      }

      if (valueImplicitlySupportsAliasing()) {
        // condition (f)
        logChildNamesDecision(Inlinability.DO_NOT_INLINE, "value implicitly supports aliasing");
        return Inlinability.DO_NOT_INLINE;
      }

      // If this is a key of an aliased object literal, then it will be aliased
      // later. So we won't be able to collapse its properties.
      // condition (b)
      if (parent != null && parent.shouldKeepKeys()) {
        final Inlinability unsafeInlinability = getUnsafeInlinabilityBasedOnDeclaredType();
        logChildNamesDecision(unsafeInlinability, "parent.shouldKeepKeys()");
        return unsafeInlinability;
      }

      // If this is aliased, then its properties can't be collapsed either. but we may do so anyway
      // if it's a declared type.
      // condition (b)
      if (aliasingGets > 0) {
        final Inlinability unsafeInlinability = getUnsafeInlinabilityBasedOnDeclaredType();
        logChildNamesDecision(
            unsafeInlinability, () -> SimpleFormat.format("%d aliasing gets exist", aliasingGets));
        return unsafeInlinability;
      }

      if (parent == null) {
        // this is completely safe to inline! yay
        logChildNamesDecision(Inlinability.INLINE_COMPLETELY, "no reason not to inline");
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
        final Inlinability unsafeInlinability = getUnsafeInlinabilityBasedOnDeclaredType();
        logChildNamesDecision(unsafeInlinability, "parent is not inlineable");
        return unsafeInlinability;
      }
      logChildNamesDecision(parentInlinability, "inherited from parent");
      return parentInlinability;
    }

    private void logChildNamesDecision(Inlinability inlinability, String reason) {
      if (decisionsLog != null && decisionsLog.isLogging()) {
        decisionsLog.log("%s: children: %s: %s", getFullName(), inlinability, reason);
      }
    }

    private void logChildNamesDecision(Inlinability inlinability, Supplier<String> reasonSupplier) {
      if (decisionsLog != null && decisionsLog.isLogging()) {
        decisionsLog.log(
            () ->
                SimpleFormat.format(
                    "%s: children: %s: %s", getFullName(), inlinability, reasonSupplier.get()));
      }
    }

    private boolean valueImplicitlySupportsAliasing() {
      if (!GlobalNamespace.this.enableImplicitlyAliasedValues) {
        return false;
      }

      if (getBooleanProperty(NameProp.CLASS)) {
        // Properties on classes may be referenced via `this` in static methods.
        return true;
      }
      if (getBooleanProperty(NameProp.FUNCTION)) {
          // We want ES5 ctors/interfaces to behave consistently with ES6 because:
          // - transpilation should not change behaviour
          // - updating code shouldn't be hindered by behaviour changes
          @Nullable JSDocInfo jsdoc = getJSDocInfo();
          return jsdoc != null && jsdoc.isConstructorOrInterface();
      }
      return false;
    }

    /** Whether this is an object literal that needs to keep its keys. */
    boolean shouldKeepKeys() {
      return isObjectLiteral() && (aliasingGets > 0 || isCollapsingExplicitlyDenied());
    }

    boolean needsToBeStubbed() {
      return globalSets == 0
          && localSets > 0
          && localSetsWithNoCollapse == 0
          && !isCollapsingExplicitlyDenied();
    }

    private void setDeclaredTypeKind(NameProp declaredType) {
      checkArgument(
          (declaredType.bit & NameProp.DECLARED_TYPE_KIND_MASK) != 0,
          "Unexpected NameProp for declaredType %s",
          declaredType);
      this.propertyBitSet =
          (this.propertyBitSet & ~NameProp.DECLARED_TYPE_KIND_MASK) | declaredType.bit;
      if (declaredType != NameProp.NOT_A_TYPE) {
        for (Name ancestor = parent; ancestor != null; ancestor = ancestor.parent) {
          ancestor.setBooleanProperty(NameProp.IS_DECLARED);
        }
      }
    }

    private void setNameType(NameProp type) {
      checkArgument(
          (type.bit & NameProp.NAME_KIND_MASK) != 0, "Unexpected NameProp for nameType %s", type);
      propertyBitSet = (this.propertyBitSet & ~NameProp.NAME_KIND_MASK) | type.bit;
    }

    boolean isDeclaredType() {
      checkState(
          (this.propertyBitSet & NameProp.DECLARED_TYPE_KIND_MASK) != 0, this.propertyBitSet);
      return !getBooleanProperty(NameProp.NOT_A_TYPE);
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
      return getBooleanProperty(NameProp.IS_DECLARED) && isObjectLiteral();
    }

    /** Determines whether this is a simple name (as opposed to a qualified name). */
    boolean isSimpleName() {
      return parent == null;
    }

    String getTypeDebugString() {
      if (this.isClass()) {
        return "CLASS";
      } else if (this.isFunction()) {
        return "FUNCTION";
      } else if (this.isObjectLiteral()) {
        return "OBJECTLIT";
      } else if (this.isGetOrSetDefinition()) {
        return "GET_SET";
      } else if (this.getBooleanProperty(NameProp.OTHER_OBJECT)) {
        return "OTHER";
      }
      throw new AssertionError("Missing NameProp for name kind in " + this.propertyBitSet);
    }

    @Override
    public String toString() {
      return getFullName()
          + " ("
          + getTypeDebugString()
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

    @Override
    public @Nullable JSDocInfo getJSDocInfo() {
      // e.g.
      // /** @type {string} */ X.numProp;     // could be a declaration, but...
      // /** @type {number} */ X.numProp = 3; // assignment wins
      return firstDeclarationJSDocInfo != null
          ? firstDeclarationJSDocInfo
          : firstQnameDeclarationWithoutAssignmentJsDocInfo;
    }

    /** Tries to get the doc info for a given declaration ref. */
    private @Nullable JSDocInfo getDocInfoForDeclaration(Ref ref) {
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
      return getBooleanProperty(NameProp.IS_MODULE_PROP);
    }
  }

  /**
   * Given something like the `'c'` STRING_KEY node in `x = {a: {b: {c: 0}}};`, return the Node for
   * the outermost object literal.
   *
   * @param objLitKey Must be the child of an OBJECT_LIT node
   * @return The first ancestor that is an OBJECT_LIT and whose grandparent is not an OBJECT_LIT
   */
  private Node getOutermostObjectLit(Node objLitKey) {
    Node outermostObjectLit = objLitKey.getParent();
    checkState(outermostObjectLit.isObjectLit(), outermostObjectLit);
    while (true) {
      final Node objLitGrandparent = outermostObjectLit.getGrandparent();
      if (objLitGrandparent != null && objLitGrandparent.isObjectLit()) {
        outermostObjectLit = objLitGrandparent;
      } else {
        return outermostObjectLit;
      }
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
  static final class Ref implements StaticRef {

    // Note: we are more aggressive about collapsing @enum and @constructor
    // declarations than implied here, see Name#canCollapse
    // Non-private for testing
    enum Type {
      /**
       * Set in the scope in which a name is declared, either the global scope or a module scope:
       * `a.b.c = 0;` or `goog.module('mod'); exports.Foo = class {};`
       */
      SET_FROM_GLOBAL,

      /** Set in a local scope: function f() { a.b.c = 0; } */
      SET_FROM_LOCAL,
      /**
       * Combined get and set in the scope in which a name is declared, either the global scope or a
       * module scope: `const c = a.b.c = 0;` or `goog.module('mod'); exports.Foo = class {};`
       */
      GET_AND_SET_FROM_GLOBAL,

      /** Combined get and set in a local scope: function f() { return a.b.c = 0; } */
      GET_AND_SET_FROM_LOCAL,

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
    private final Type type;

    /**
     * The scope in which the reference is resolved. Note that for ALIASING_GETS like "var x = ns;"
     * this scope may not be the correct hoist scope of the aliasing VAR.
     */
    final Scope scope;

    /**
     * Creates a Ref
     *
     * <p>No parameter checking is done here, because we allow nulls for several fields in Refs
     * created just for testing. However, all Refs for real use must be created by methods on the
     * Name class, which does do argument checking.
     */
    private Ref(@Nullable Scope scope, @Nullable Node node, Type type) {
      this.node = node;
      this.type = type;
      this.scope = scope;
    }

    @Override
    public Node getNode() {
      return node;
    }

    @Override
    public @Nullable StaticSourceFile getSourceFile() {
      return node != null ? node.getStaticSourceFile() : null;
    }

    @Override
    public StaticSlot getSymbol() {
      throw new UnsupportedOperationException();
    }

    boolean isDeleteProp() {
      return this.type == Type.DELETE_PROP;
    }

    boolean isSubclassingGet() {
      return this.type == Type.SUBCLASSING_GET;
    }

    /**
     * Whether this is a "twin" ref, i.e. a ref that is both a get and a set.
     *
     * <p>Example: `a.b` from `x = a.b = 0;`
     */
    boolean isTwin() {
      switch (this.type) {
        case GET_AND_SET_FROM_GLOBAL:
        case GET_AND_SET_FROM_LOCAL:
          return true;
        default:
          return false;
      }
    }

    boolean isGet() {
      switch (this.type) {
        case DIRECT_GET:
        case ALIASING_GET:
        case SUBCLASSING_GET:
        case CALL_GET:
        case GET_AND_SET_FROM_GLOBAL:
        case GET_AND_SET_FROM_LOCAL:
        case PROTOTYPE_GET:
          return true;
        default:
          return false;
      }
    }

    boolean isAliasingGet() {
      switch (this.type) {
        case ALIASING_GET:
        case GET_AND_SET_FROM_GLOBAL:
        case GET_AND_SET_FROM_LOCAL:
          return true;
        default:
          return false;
      }
    }

    boolean isSet() {
      switch (this.type) {
        case SET_FROM_GLOBAL:
        case SET_FROM_LOCAL:
        case GET_AND_SET_FROM_GLOBAL:
        case GET_AND_SET_FROM_LOCAL:
          return true;
        default:
          return false;
      }
    }

    boolean isSetFromGlobal() {
      return this.type == Type.SET_FROM_GLOBAL || this.type == Type.GET_AND_SET_FROM_GLOBAL;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .omitNullValues()
          .add("type", type)
          .add("node", node)
          .add("scope", scope)
          .toString();
    }
  }

  /**
   * Enum of boolean properties of a {@link Name}, to be stored in one bit set per name.
   *
   * <p>Non-private for @AutoValue. Code outside this class shouldn't need this enum though.
   */
  enum NameProp {
    // Increment the "index" passed to the constructor by 1 for each enum member
    IS_DECLARED(0),
    IS_MODULE_PROP(1),
    IS_PROVIDED(2), // If this name was in any goog.provide() calls.
    IS_USED_HAS_OWN_PROPERTY(3),
    IS_EXTERN(4), // corresponds to SourceKind.EXTERN

    // Mutually exclusive properties indicating what kind of Closure type this Name is, if any.
    // Corresponds to DECLARED_TYPE_KIND_MASK.
    CONSTRUCTOR_TYPE(5), // a non-interface class {} or a `/** @constructor */ function`
    INTERFACE_TYPE(6), // a `/** @interface */` or `/** @record */`
    ENUM_TYPE(7), // an `/** @enum */`
    NOT_A_TYPE(8),

    // Mutually exclusive properties indicating what kind of JavaScript entity this Name is.
    // Corresponds to NAME_KIND_MASK.
    CLASS(9), // class C {}
    OBJECTLIT(10), // var x = {};
    FUNCTION(11), // function f() {}
    GET_SET(12), // a getter, setter, or both; e.g. `obj.b` in `const obj = {set b(x) {}};`
    OTHER_OBJECT(13); // anything else, including `var x = 1;`, var x = new Something();`, etc.

    private static final int DECLARED_TYPE_KIND_MASK =
        (CONSTRUCTOR_TYPE.bit | INTERFACE_TYPE.bit | ENUM_TYPE.bit | NOT_A_TYPE.bit);
    private static final int NAME_KIND_MASK =
        (CLASS.bit | OBJECTLIT.bit | FUNCTION.bit | GET_SET.bit | OTHER_OBJECT.bit);

    private final int bit; // some power of 2

    NameProp(int index) {
      this.bit = 1 << index;
    }
  }

  @AutoValue
  abstract static class ObjLitStringKeyAnalysis {
    public abstract @Nullable String getNameString();

    public abstract @Nullable NameProp getNameType();

    /**
     * The object literal key is used to define a property. <code>
     * Object.defineProperty(parent.qname, { strKeyName: value, { get: ..., } })</code>
     */
    static ObjLitStringKeyAnalysis forObjectDefineProperty(String nameString) {
      // Technically the definition may not have a getter or setter, but we'll just
      // always pretend it does, because we cannot inline and collapse properties defined this
      // way.
      return new AutoValue_GlobalNamespace_ObjLitStringKeyAnalysis(
          checkNotNull(nameString), NameProp.GET_SET);
    }

    /** The object literal key represents `parent.qname = { strKeyName: value }` */
    static ObjLitStringKeyAnalysis forObjLitAssignment(String nameString, NameProp nameType) {
      return new AutoValue_GlobalNamespace_ObjLitStringKeyAnalysis(
          checkNotNull(nameString), nameType);
    }

    /** The object literal key does not represent a qualified name assignment. */
    static ObjLitStringKeyAnalysis forNonReference() {
      return new AutoValue_GlobalNamespace_ObjLitStringKeyAnalysis(
          /* nameString= */ null, NameProp.OTHER_OBJECT);
    }
  }
}
