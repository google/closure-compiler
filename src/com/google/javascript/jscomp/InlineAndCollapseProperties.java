/*
 * Copyright 2021 The Closure Compiler Authors.
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
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.jscomp.CompilerOptions.ChunkOutputType;
import com.google.javascript.jscomp.CompilerOptions.PropertyCollapseLevel;
import com.google.javascript.jscomp.GlobalNamespace.AstChange;
import com.google.javascript.jscomp.GlobalNamespace.Inlinability;
import com.google.javascript.jscomp.GlobalNamespace.Name;
import com.google.javascript.jscomp.GlobalNamespace.Ref;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.ExternsSkippingCallback;
import com.google.javascript.jscomp.Normalize.PropagateConstantPropertyOverVars;
import com.google.javascript.jscomp.base.format.SimpleFormat;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.jscomp.diagnostic.LogFile;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.nullness.Nullable;

/**
 * Perform inlining of aliases and collapsing of qualified names in order to improve later
 * optimizations, such as RemoveUnusedCode.
 */
class InlineAndCollapseProperties implements CompilerPass {

  // Warnings
  static final DiagnosticType PARTIAL_NAMESPACE_WARNING =
      DiagnosticType.warning(
          "JSC_PARTIAL_NAMESPACE",
          "Partial alias created for namespace {0}, possibly due to await/yield transpilation.\n"
              + "This may prevent optimization of anything nested under this namespace.\n"
              + "See https://github.com/google/closure-compiler/wiki/FAQ#i-got-a-partial-alias-created-for-namespace-error--what-do-i-do"
              + " for more details.");

  static final DiagnosticType NAMESPACE_REDEFINED_WARNING =
      DiagnosticType.warning("JSC_NAMESPACE_REDEFINED", "namespace {0} should not be redefined");

  static final DiagnosticType RECEIVER_AFFECTED_BY_COLLAPSE =
      DiagnosticType.warning(
          "JSC_RECEIVER_AFFECTED_BY_COLLAPSE",
          "Receiver reference in function {0} changes meaning when namespace is collapsed.\n"
              + " Consider annotating @nocollapse; however, other properties on the receiver may"
              + " still be collapsed.");

  static final DiagnosticType UNSAFE_CTOR_ALIASING =
      DiagnosticType.warning(
          "JSC_UNSAFE_CTOR_ALIASING",
          "Variable {0} aliases a constructor, so it cannot be assigned multiple times");

  static final DiagnosticType ALIAS_CYCLE =
      DiagnosticType.error("JSC_ALIAS_CYCLE", "Alias path contains a cycle: {0} to {1}");

  private final AbstractCompiler compiler;
  private final PropertyCollapseLevel propertyCollapseLevel;
  private final ChunkOutputType chunkOutputType;
  private final boolean haveModulesBeenRewritten;
  private final ResolutionMode moduleResolutionMode;
  private final boolean staticInheritanceUsed;

  /**
   * Used by `AggressiveInlineAliasesTest` to enable execution of the aggressive inlining logic
   * without doing any collapsing.
   */
  private final boolean testAggressiveInliningOnly;

  /**
   * Supplied by `AggressiveInlineAliasesTest`.
   *
   * <p>The `GlobalNamespace` created by `AggressiveInlineAliases` will be passed to this `Consumer`
   * for examination.
   */
  private final Optional<Consumer<GlobalNamespace>> optionalGlobalNamespaceTester;

  /**
   * Records decisions made by this class and related logic.
   *
   * <p>This field is allocated and cleaned up by process(). It's a class field to avoid having to
   * pass it as an extra argument through a lot of methods.
   */
  private @Nullable LogFile decisionsLog = null;

  /** A `GlobalNamespace` that is shared by alias inlining and property collapsing code. */
  private GlobalNamespace namespace;

  private InlineAndCollapseProperties(Builder builder) {
    this.compiler = builder.compiler;
    this.propertyCollapseLevel = builder.propertyCollapseLevel;
    this.chunkOutputType = builder.chunkOutputType;
    this.haveModulesBeenRewritten = builder.haveModulesBeenRewritten;
    this.moduleResolutionMode = builder.moduleResolutionMode;
    this.testAggressiveInliningOnly = builder.testAggressiveInliningOnly;
    this.optionalGlobalNamespaceTester = builder.optionalGlobalNamespaceTester;
    this.staticInheritanceUsed = builder.staticInheritanceUsed;
  }

  static final class Builder {
    final AbstractCompiler compiler;
    private PropertyCollapseLevel propertyCollapseLevel;
    private ChunkOutputType chunkOutputType;
    private boolean haveModulesBeenRewritten;
    private ResolutionMode moduleResolutionMode;
    private boolean testAggressiveInliningOnly = false;
    private Optional<Consumer<GlobalNamespace>> optionalGlobalNamespaceTester = Optional.empty();
    private boolean staticInheritanceUsed = false;

    Builder(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @CanIgnoreReturnValue
    public Builder setPropertyCollapseLevel(PropertyCollapseLevel propertyCollapseLevel) {
      this.propertyCollapseLevel = propertyCollapseLevel;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setChunkOutputType(ChunkOutputType chunkOutputType) {
      this.chunkOutputType = chunkOutputType;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setHaveModulesBeenRewritten(boolean haveModulesBeenRewritten) {
      this.haveModulesBeenRewritten = haveModulesBeenRewritten;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setModuleResolutionMode(ResolutionMode moduleResolutionMode) {
      this.moduleResolutionMode = moduleResolutionMode;
      return this;
    }

    @CanIgnoreReturnValue
    @VisibleForTesting
    public Builder testAggressiveInliningOnly(Consumer<GlobalNamespace> globalNamespaceTester) {
      this.testAggressiveInliningOnly = true;
      this.optionalGlobalNamespaceTester = Optional.of(globalNamespaceTester);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setAssumeStaticInheritanceIsNotUsed(boolean assumeStaticInheritanceIsNotUsed) {
      this.staticInheritanceUsed = !assumeStaticInheritanceIsNotUsed;
      return this;
    }

    InlineAndCollapseProperties build() {
      return new InlineAndCollapseProperties(this);
    }
  }

  static Builder builder(AbstractCompiler compiler) {
    return new Builder(compiler);
  }

  @Override
  public void process(Node externs, Node root) {
    checkState(
        !testAggressiveInliningOnly || propertyCollapseLevel == PropertyCollapseLevel.ALL,
        "testAggressiveInlining is invalid for: %s",
        propertyCollapseLevel);
    try (LogFile logFile = compiler.createOrReopenIndexedLog(this.getClass(), "decisions.log")) {
      // NOTE: decisionsLog will be a do-nothing proxy object unless the compiler
      // was given an option telling it to generate log files and where to put them.
      decisionsLog = logFile;
      switch (propertyCollapseLevel) {
        case NONE:
          performMinimalInliningAndNoCollapsing(externs, root);
          break;
        case MODULE_EXPORT:
          performMinimalInliningAndModuleExportCollapsing(externs, root);
          break;
        case ALL:
          if (testAggressiveInliningOnly) {
            performAggressiveInliningForTest(externs, root);
          } else {
            performAggressiveInliningAndCollapsing(externs, root);
          }
          break;
      }
    } finally {
      decisionsLog = null;
    }
  }

  private void performMinimalInliningAndNoCollapsing(Node externs, Node root) {
    // TODO(b/124915436): Remove InlineAliases completely after cleaning up the codebase.
    new InlineAliases().process(externs, root);
  }

  private void performMinimalInliningAndModuleExportCollapsing(Node externs, Node root) {
    // TODO(b/124915436): Remove InlineAliases completely after cleaning up the codebase.
    new InlineAliases().process(externs, root);
    // CollapseProperties needs this namespace.
    // TODO(bradfordcsmith): Have `InlineAliases` update the namespace it already created
    // and reuse that one instead.
    namespace = new GlobalNamespace(decisionsLog, compiler, root);
    new CollapseProperties().process(externs, root);

    namespace = null; // free up memory before PropagateConstantPropertyOverVars
    // This shouldn't be necessary, this pass should already be setting new constants as
    // constant.
    // TODO(b/64256754): Investigate.
    new PropagateConstantPropertyOverVars(compiler, false).process(externs, root);
  }

  private void performAggressiveInliningAndCollapsing(Node externs, Node root) {
    new ConcretizeStaticInheritanceForInlining(compiler).process(externs, root);
    new AggressiveInlineAliases().process(externs, root);

    new CollapseProperties().process(externs, root);

    namespace = null; // free up memory before PropagateConstantPropertyOverVars
    // This shouldn't be necessary, this pass should already be setting new constants as
    // constant.
    // TODO(b/64256754): Investigate.
    new PropagateConstantPropertyOverVars(compiler, false).process(externs, root);
  }

  private void performAggressiveInliningForTest(Node externs, Node root) {
    final AggressiveInlineAliases aggressiveInlineAliases = new AggressiveInlineAliases();
    aggressiveInlineAliases.process(externs, root);
    optionalGlobalNamespaceTester
        .get()
        .accept(aggressiveInlineAliases.getLastUsedGlobalNamespace());
  }

  /**
   * Inlines type aliases if they are explicitly or effectively const. Also inlines inherited static
   * property accesses for ES6 classes.
   *
   * <p>This frees subsequent optimization passes from the responsibility of having to reason about
   * alias chains and is a requirement for correct behavior in at least CollapseProperties and
   * J2clPropertyInlinerPass.
   *
   * <p>This is designed to be no more unsafe than CollapseProperties. It will in some cases inline
   * properties, possibly past places that change the property value. However, it will only do so in
   * cases where CollapseProperties would unsafely collapse the property anyway.
   */
  class AggressiveInlineAliases implements CompilerPass {

    private boolean codeChanged;

    AggressiveInlineAliases() {
      this.codeChanged = true;
    }

    @VisibleForTesting
    GlobalNamespace getLastUsedGlobalNamespace() {
      return namespace;
    }

    @Override
    public void process(Node externs, Node root) {
      if (!staticInheritanceUsed) {
        new StaticSuperPropReplacer(compiler).replaceAll(root);
      }

      NodeTraversal.traverse(compiler, root, new RewriteSimpleDestructuringAliases());

      // Building the `GlobalNamespace` dominates the cost of this pass, so it is built once and
      // updated as changes are made so it can be reused for the next iteration.
      namespace = new GlobalNamespace(decisionsLog, compiler, root);
      while (codeChanged) {
        codeChanged = false;
        inlineAliases(namespace);
      }
    }

    /**
     * For each qualified name N in the global scope, we check if: (a) No ancestor of N is ever
     * aliased or assigned an unknown value type. (If N = "a.b.c", "a" and "a.b" are never aliased).
     * (b) N has exactly one write, and it lives in the global scope. (c) N is aliased in a local
     * scope. (d) N is aliased in global scope
     *
     * <p>If (a) is true, then GlobalNamespace must know all the writes to N. If (a) and (b) are
     * true, then N cannot change during the execution of a local scope. If (a) and (b) and (c) are
     * true, then the alias can be inlined if the alias obeys the usual rules for how we decide
     * whether a variable is inlineable. If (a) and (b) and (d) are true, then inline the alias if
     * possible (if it is assigned exactly once unconditionally).
     *
     * <p>For (a), (b), and (c) are true and the alias is of a constructor, we may also partially
     * inline the alias - i.e. replace some references with the constructor but not all - since
     * constructor properties are always collapsed, so we want to be more aggressive about removing
     * aliases. This is similar to what FlowSensitiveInlineVariables does.
     *
     * <p>If (a) is not true, but the property is a 'declared type' (which CollapseProperties will
     * unsafely collapse), we also inline any properties without @nocollapse. This is unsafe but no
     * more unsafe than what CollapseProperties does. This pass and CollapseProperties share the
     * logic to determine when a name is unsafely collapsible in {@link Name#canCollapse()}
     *
     * @see InlineVariables
     */
    private void inlineAliases(GlobalNamespace namespace) {
      // Invariant: All the names in the worklist meet condition (a).
      // adds all top-level names to the worklist, but not any properties on those names.
      Deque<Name> workList = new ArrayDeque<>(namespace.getNameForest());

      while (!workList.isEmpty()) {
        Name name = workList.pop();

        // Don't attempt to inline a getter or setter property as a variable.
        if (name.isGetOrSetDefinition()) {
          continue;
        }

        if (!name.inExterns() // not an externs definition
            && name.getGlobalSets() == 1 // set exactly once and only set in the global scope
            && name.getLocalSets() == 0) {
          // {@code name} meets condition (b). Find all of its aliases
          // and try to inline them.
          maybeInlineInnerName(name);
          if (name.getAliasingGets() > 0 || name.getSubclassingGets() > 0) {
            // condition (c) and/or condition (d) are true
            inlineAliasesForName(name, namespace);
          }
        }
        maybeAddPropertiesToWorklist(name, workList);
      }
    }

    /**
     * Inlines a global name into all the places where references for aliases to it currently exist.
     *
     * <p>e.g.
     *
     * <pre><code>
     *   const globalName = { method() {} };
     *   const aliasForGlobalName = globalName;
     *   aliasForGlobalName.method(); // replace this with globalName.method();
     * </code></pre>
     *
     * <p>This method only handles aliases created by assignment. In particular, it doesn't handle
     * aliases created by inner names on class or function expressions. See (maybeInlineInnerName()
     * for that).
     *
     * @param name the global name whose aliases will be replaced
     * @param namespace used to find references to the global name that create aliases e.g. {@code
     *     const aliasName = globalName}.
     */
    private void inlineAliasesForName(Name name, GlobalNamespace namespace) {
      List<Ref> refs = new ArrayList<>(name.getRefs());
      for (Ref ref : refs) {
        Scope hoistScope = ref.scope.getClosestHoistScope();
        if (ref.isAliasingGet() && !mayBeGlobalAlias(ref) && !ref.isTwin()) {
          // {@code name} meets condition (c). Try to inline it.
          // TODO(johnlenz): consider picking up new aliases at the end
          // of the pass instead of immediately like we do for global
          // inlines.
          inlineAliasIfPossible(name, ref, namespace);
        } else if (ref.isAliasingGet() && hoistScope.isGlobal() && !ref.isTwin()) {
          inlineGlobalAliasIfPossible(name, ref, namespace);
        } else if (name.isClass() && ref.isSubclassingGet() && name.props != null) {
          for (Name prop : name.props) {
            rewriteAllSubclassInheritedAccesses(name, ref, prop, namespace);
          }
        }
      }
    }

    /**
     * If the global name is a class or function with an inner-scope name, inline references to that
     * name with the global name.
     *
     * <p>e.g.
     *
     * <pre><code>
     *   var globalFunction = function innerName() {
     *     // change this to globalFunction.someProp
     *     use(innerName.someProp);
     *   }
     *   var GlobalClass = class InnerName {
     *     method() {
     *       // change this to GlobalClass.someProp
     *       use(InnerName.someProp);
     *     }
     *   };
     * </code></pre>
     */
    private void maybeInlineInnerName(Name globalName) {
      final Ref globalNameDeclaration = checkNotNull(globalName.getDeclaration(), globalName);
      final Node globalDeclarationNode =
          checkNotNull(globalNameDeclaration.getNode(), globalNameDeclaration);
      final Node valueNode = NodeUtil.getRValueOfLValue(globalDeclarationNode);
      if (valueNode == null) {
        // no function or class expression, so no inner name
        return;
      }
      final Node innerNameNode = maybeGetInnerNameNode(valueNode);
      if (innerNameNode == null) {
        // no inner name to require inlining
        return;
      }
      final String innerName = innerNameNode.getString();
      final SyntacticScopeCreator syntacticScopeCreator = new SyntacticScopeCreator(compiler);
      final Scope innerScope =
          syntacticScopeCreator.createScope(valueNode, globalNameDeclaration.scope);
      final Var innerNameVar = checkNotNull(innerScope.getVar(innerName));
      final ReferenceCollector collector =
          new ReferenceCollector(
              compiler,
              ReferenceCollector.DO_NOTHING_BEHAVIOR,
              syntacticScopeCreator,
              Predicates.equalTo(innerNameVar));
      collector.processScope(innerScope);
      final ReferenceCollection innerNameRefs = collector.getReferences(innerNameVar);

      final Set<AstChange> newNodes = new LinkedHashSet<>();

      for (Reference innerNameRef : innerNameRefs) {
        // replace all references to the inner name other than its declaration
        final Node innerNameRefNode = innerNameRef.getNode();
        if (NodeUtil.isNormalGet(innerNameRefNode.getParent())) {
          // Replace `innerName` with `globalName` for `innerName.prop` and `innerName[expr]`
          //
          // TODO(b/148237949): We are intentionally ignoring cases where the inner name
          // escapes to other scopes where properties may be accessed on it (e.g. `use(InnerName)`).
          // This is unsafe, but currently necessary to avoid large code size regressions.
          //
          // NOTE: We also don't want to introduce a global reference for cases like
          // `x instanceof innerName`. It would be safe to inline these, but it also isn't
          // necessary,
          // and the introduction of a reference to a global in a local scope can cause other
          // optimizations to back off.
          newNodes.add(replaceAliasReference(globalNameDeclaration, innerNameRef));
        }
      }
      namespace.scanNewNodes(newNodes);
    }

    /**
     * Inline all references to inherited static superclass properties from the subclass or any
     * descendant of the given subclass. Avoids inlining references to inherited methods when
     * possible, since they may use this or super().
     *
     * @param superclassNameObj The Name of the superclass
     * @param superclassRef The SUBCLASSING_REF
     * @param prop The property on the superclass to rewrite, if any descendant accesses it.
     * @param namespace The GlobalNamespace containing superclassNameObj
     */
    private boolean rewriteAllSubclassInheritedAccesses(
        Name superclassNameObj, Ref superclassRef, Name prop, GlobalNamespace namespace) {
      if (!prop.canCollapse()) {
        return false; // inlining is a) unnecessary if there is @nocollapse and b) might break
        // usages of `this` in the method
      }
      Node subclass = getSubclassForEs6Superclass(superclassRef.getNode());
      if (subclass == null || !subclass.isQualifiedName()) {
        return false;
      }

      String subclassName = subclass.getQualifiedName();
      String subclassQualifiedPropName = subclassName + "." + prop.getBaseName();
      Name subclassPropNameObj = namespace.getOwnSlot(subclassQualifiedPropName);
      // Don't rewrite if the subclass ever shadows the parent static property.
      // This may also back off on cases where the subclass first accesses the parent property, then
      // shadows it.
      if (subclassPropNameObj != null
          && (subclassPropNameObj.getLocalSets() > 0 || subclassPropNameObj.getGlobalSets() > 0)) {
        return false;
      }

      // Recurse to find potential sub-subclass accesses of the superclass property.
      Name subclassNameObj = namespace.getOwnSlot(subclassName);
      if (subclassNameObj != null && subclassNameObj.subclassingGetCount() > 0) {
        for (Ref ref : subclassNameObj.getRefs()) {
          if (ref.isSubclassingGet()) {
            rewriteAllSubclassInheritedAccesses(superclassNameObj, ref, prop, namespace);
          }
        }
      }

      if (subclassPropNameObj != null) {
        Set<AstChange> newNodes = new LinkedHashSet<>();

        // Use this node as a template for rewriteNestedAliasReference.
        Node superclassNameNode = superclassNameObj.getDeclaration().getNode();
        if (superclassNameNode.isName()) {
          superclassNameNode = superclassNameNode.cloneNode();
        } else if (superclassNameNode.isGetProp()) {
          superclassNameNode = superclassNameNode.cloneTree();
        } else {
          return false;
        }

        rewriteNestedAliasReference(superclassNameNode, 0, newNodes, subclassPropNameObj);
        namespace.scanNewNodes(newNodes);
      }
      return true;
    }

    /**
     * Recognizes aliases for the special global variables representing the `exports` values for
     * goog modules which are safe to inline.
     *
     * <p>The compiler will enforce that references to these module objects only occur after they
     * have been initialized and that they are only ever assigned one time. So, as long as a
     * variable that aliases one of them is only ever assigned that one module exports value and
     * only used for getting properties off of it with destructuring or dot-property access, we know
     * it is safe to inline those aliases.
     *
     * <p>This inlining is important to support `await goog.requireDynamic()` when transpiling down
     * to es5. In that case the compiler generates one of these aliases which risks producing the
     * PARTIAL_NAMESPACE_WARNING.
     */
    private boolean isInlineableModuleExportsAlias(Name name, ReferenceCollection aliasRefs) {
      final String aliasedNameStr = name.getFullName();
      if (!aliasedNameStr.startsWith(ClosureRewriteModule.MODULE_EXPORTS_PREFIX)) {
        return false;
      }
      // the first rhs value Node we find assigned to the name
      Node firstRhs = null;
      int size = aliasRefs.references.size();
      for (int i = 0; i < size; i++) {
        Reference ref = aliasRefs.references.get(i);
        if (ref.isVarDeclaration() || ref.getParent().isAssign()) {
          Node rhs;
          if (ref.getParent().isAssign()) {
            rhs = ref.getParent().getSecondChild();
          } else {
            rhs = ref.getNode().getFirstChild();
          }

          if (rhs != null) {
            // Make sure that every time a value is assigned to the alias, it is
            // the aliased name we expect. If not, we cannot inline this alias.
            if (firstRhs == null) {
              // NOTE: comparing with a string is slower, but necessary the first time.
              if (!rhs.matchesQualifiedName(aliasedNameStr)) {
                return false;
              }
              firstRhs = rhs;
            } else {
              // comparing nodes is faster than comparing with the string name
              if (!rhs.matchesQualifiedName(firstRhs)) {
                return false;
              }
            }
          }
          continue;
        }
        if (!ref.isDotPropertyAccess() && !ref.isAssignedToObjectDestructuringPattern()) {
          return false;
        }
      }
      return true;
    }

    /**
     * Attempts to inline a non-global alias of a global name.
     *
     * <p>It is assumed that the name for which it is an alias meets conditions (a) and (b).
     *
     * <p>The non-global alias is only inlinable if it is well-defined and assigned once, according
     * to the definitions in {@link ReferenceCollection}
     *
     * <p>If the aliasing name is completely removed, also deletes the aliasing Ref.
     *
     * @param name The global name being aliased
     * @param alias The aliasing reference to the name to remove
     */
    private void inlineAliasIfPossible(Name name, Ref alias, GlobalNamespace namespace) {
      // Ensure that the alias is assigned to a local variable at that
      // variable's declaration. If the alias's parent is a NAME,
      // then the NAME must be the child of a VAR, LET, or CONST node, and we must
      // be in a VAR, LET, or CONST assignment.
      // Otherwise if the parent is an assign, we are in a "a = alias" case.
      Node aliasParent = alias.getNode().getParent();
      if (aliasParent.isName() || aliasParent.isAssign()) {
        Node aliasLhsNode = aliasParent.isName() ? aliasParent : aliasParent.getFirstChild();
        String aliasVarName = aliasLhsNode.getString();

        Var aliasVar = alias.scope.getVar(aliasVarName);
        checkState(aliasVar != null, "Expected variable to be defined in scope (%s)", aliasVarName);
        ReferenceCollector collector =
            new ReferenceCollector(
                compiler,
                ReferenceCollector.DO_NOTHING_BEHAVIOR,
                new SyntacticScopeCreator(compiler),
                Predicates.equalTo(aliasVar));
        Scope aliasScope = aliasVar.getScope();
        collector.processScope(aliasScope);

        ReferenceCollection aliasRefs = collector.getReferences(aliasVar);
        Set<AstChange> newNodes = new LinkedHashSet<>();

        if (aliasRefs.isWellDefined()
            && (aliasRefs.isAssignedOnceInLifetime()
                || isInlineableModuleExportsAlias(name, aliasRefs))) {
          // The alias is well-formed, so do the inlining now.
          int size = aliasRefs.references.size();
          // It's initialized on either the first or second reference.
          int firstRead = aliasRefs.references.get(0).isInitializingDeclaration() ? 1 : 2;
          for (int i = firstRead; i < size; i++) {
            Reference aliasRef = aliasRefs.references.get(i);
            newNodes.add(replaceAliasReference(alias, aliasRef));
          }

          // just set the original alias to null.
          tryReplacingAliasingAssignment(alias, name, aliasLhsNode);

          // Inlining the variable may have introduced new references
          // to descendants of {@code name}. So those need to be collected now.
          namespace.scanNewNodes(newNodes);
          return;
        }

        if (name.isConstructor()) {
          // TODO(lharker): the main reason this was added is because method decomposition inside
          // generators introduces some constructor aliases that weren't getting inlined.
          // If we find another (safer) way to avoid aliasing in method decomposition, consider
          // removing this.
          if (!partiallyInlineAlias(alias, name, namespace, aliasRefs, aliasLhsNode)) {
            // If we can't inline all alias references, make sure there are no unsafe property
            // accesses.
            if (referencesCollapsibleProperty(aliasRefs, name, namespace)) {
              compiler.report(JSError.make(aliasParent, UNSAFE_CTOR_ALIASING, aliasVarName));
            }
          }
        }
      }
    }

    /**
     * Inlines some references to an alias with its value. This handles cases where the alias is not
     * declared at initialization. It does nothing if the alias is reassigned after being
     * initialized, unless the reassignment occurs because of an enclosing function or a loop.
     *
     * @param alias An alias of some variable, which may not be well-defined.
     * @param namespace The GlobalNamespace, which will be updated with all new nodes created.
     * @param aliasRefs All references to the alias in its scope.
     * @param aliasLhsNode The lhs name of the alias when it is first initialized.
     * @return Whether all references to the alias were inlined
     */
    private boolean partiallyInlineAlias(
        Ref alias,
        Name aliasingName,
        GlobalNamespace namespace,
        ReferenceCollection aliasRefs,
        Node aliasLhsNode) {
      BasicBlock aliasBlock = null;
      // This initial iteration through all the alias references does two things:
      // a) Find the control flow block in which the alias is assigned.
      // b) See if the alias var is assigned to in multiple places, and return if that's the case.
      //    NOTE: we still may inline if the alias is assigned in a loop or inner function and that
      //    assignment statement is potentially executed multiple times.
      //    This is more aggressive than what "inlineAliasIfPossible" does.
      for (Reference aliasRef : aliasRefs) {
        Node aliasRefNode = aliasRef.getNode();
        if (aliasRefNode == aliasLhsNode) {
          aliasBlock = aliasRef.getBasicBlock();
          continue;
        } else if (aliasRef.isLvalue()) {
          // Don't replace any references if the alias is reassigned
          return false;
        }
      }

      Set<AstChange> newNodes = new LinkedHashSet<>();
      boolean alreadySeenInitialAlias = false;
      boolean foundNonReplaceableAlias = false;
      // Do a second iteration through all the alias references, and replace any inlinable
      // references.
      for (Reference aliasRef : aliasRefs) {
        Node aliasRefNode = aliasRef.getNode();
        if (aliasRefNode == aliasLhsNode) {
          alreadySeenInitialAlias = true;
          continue;
        } else if (aliasRef.isDeclaration()) {
          // Ignore any alias declarations, e.g. "var alias;", since there's nothing to inline.
          continue;
        }

        BasicBlock refBlock = aliasRef.getBasicBlock();
        if ((refBlock != aliasBlock && aliasBlock.provablyExecutesBefore(refBlock))
            || (refBlock == aliasBlock && alreadySeenInitialAlias)) {
          // We replace the alias only if the alias and reference are in the same BasicBlock,
          // the aliasing assignment takes place before the reference, and the alias is
          // never reassigned.
          codeChanged = true;
          newNodes.add(replaceAliasReference(alias, aliasRef));
        } else {
          foundNonReplaceableAlias = true;
        }
      }

      // We removed all references to the alias, so remove the original aliasing assignment.
      if (!foundNonReplaceableAlias) {
        tryReplacingAliasingAssignment(alias, aliasingName, aliasLhsNode);
      }

      if (codeChanged) {
        // Inlining the variable may have introduced new references
        // to descendants of {@code name}. So those need to be collected now.
        namespace.scanNewNodes(newNodes);
      }
      return !foundNonReplaceableAlias;
    }

    /**
     * Replaces the rhs of an aliasing assignment with null, unless the assignment result is used in
     * a complex expression.
     */
    @CanIgnoreReturnValue
    private boolean tryReplacingAliasingAssignment(Ref alias, Name aliasName, Node aliasLhsNode) {
      // either VAR/CONST/LET or ASSIGN.
      Node assignment = aliasLhsNode.getParent();
      if (!NodeUtil.isNameDeclaration(assignment) && NodeUtil.isExpressionResultUsed(assignment)) {
        // e.g. don't change "if (alias = someVariable)" to "if (alias = null)"
        // TODO(lharker): instead replace the entire assignment with the RHS - "alias = x" becomes
        // "x"
        return false;
      }
      Node aliasParent = alias.getNode().getParent();
      alias.getNode().replaceWith(IR.nullNode());
      aliasName.removeRef(alias);
      codeChanged = true;
      compiler.reportChangeToEnclosingScope(aliasParent);
      return true;
    }

    /**
     * @param alias A GlobalNamespace.Ref of the variable being aliased
     * @param aliasRef One particular usage of an alias that we want to replace with the aliased
     *     var.
     * @return an AstChange representing the new node(s) added to the AST *
     */
    private AstChange replaceAliasReference(Ref alias, Reference aliasRef) {
      final Node originalRefNode = alias.getNode();
      final Node nodeToReplace = aliasRef.getNode();
      checkState(nodeToReplace.isQualifiedName(), nodeToReplace);
      // If the reference node is a NAME it could be
      // const origName = value;
      // If we use cloneTree() for that we'll clone the value, which we don't want.
      // Otherwise, we do want to clone the tree of GETPROP nodes.
      final Node newNode =
          originalRefNode.isName() ? originalRefNode.cloneNode() : originalRefNode.cloneTree();
      newNode.srcrefTree(nodeToReplace);
      nodeToReplace.replaceWith(newNode);
      compiler.reportChangeToEnclosingScope(newNode);
      return new AstChange(aliasRef.getScope(), newNode);
    }

    /**
     * Attempt to inline an global alias of a global name. This requires that the name is well
     * defined: assigned unconditionally, assigned exactly once. It is assumed that, the name for
     * which it is an alias must already meet these same requirements.
     *
     * <p>If the alias is completely removed, also deletes the aliasing Ref.
     *
     * @param name The global name being aliased
     * @param alias The alias to inline
     */
    private void inlineGlobalAliasIfPossible(Name name, Ref alias, GlobalNamespace namespace) {
      // Ensure that the alias is assigned to global name at that the
      // declaration.
      Node aliasParent = alias.getNode().getParent();
      if (((aliasParent.isAssign() || aliasParent.isName())
              && NodeUtil.isExecutedExactlyOnce(aliasParent))
          // We special-case for constructors here, to inline constructor aliases
          // more aggressively in global scope.
          // We do this because constructor properties are always collapsed,
          // so we want to inline the aliases also to avoid breakages.
          || (aliasParent.isName() && name.isConstructor())) {
        Node lvalue = aliasParent.isName() ? aliasParent : aliasParent.getFirstChild();
        if (!lvalue.isQualifiedName()) {
          return;
        }
        if (lvalue.isName()
            && compiler.getCodingConvention().isExported(lvalue.getString(), /* local= */ false)) {
          return;
        }
        Name aliasingName = namespace.getSlot(lvalue.getQualifiedName());

        if (aliasingName == null) {
          // this is true for names in externs or properties on extern names
          return;
        }

        if (name.equals(aliasingName) && aliasParent.isAssign()) {
          // Ignore `a.b.c = a.b.c;` with `a.b.c;`.
          return;
        }

        Inlinability aliasInlinability = aliasingName.calculateInlinability();
        if (!aliasInlinability.shouldInlineUsages()) {
          // nothing to do here
          return;
        }
        Set<AstChange> newNodes = new LinkedHashSet<>();

        // Rewrite all references to the aliasing name, except for the initialization
        rewriteAliasReferences(aliasingName, alias, newNodes);
        rewriteAliasProps(aliasingName, alias.getNode(), 0, newNodes);

        if (aliasInlinability.shouldRemoveDeclaration()) {
          // Rewrite the initialization of the alias.
          Ref aliasDeclaration = aliasingName.getDeclaration();
          if (aliasDeclaration.isTwin()) {
            // This is in a nested assign.
            // Replace
            //   a.b = aliasing.name = aliased.name
            // with
            //   a.b = aliased.name
            checkState(aliasParent.isAssign(), aliasParent);
            Node aliasGrandparent = aliasParent.getParent();
            aliasParent.replaceWith(alias.getNode().detach());
            // Remove the ref to 'aliasing.name' entirely
            aliasingName.removeRef(aliasDeclaration);
            // Force GlobalNamespace to revisit the new reference to 'aliased.name' and update its
            // internal state.
            newNodes.add(new AstChange(alias.scope, alias.getNode()));
            compiler.reportChangeToEnclosingScope(aliasGrandparent);
          } else {
            // Replace
            //  aliasing.name = aliased.name
            // with
            //  aliasing.name = null;
            alias.getNode().replaceWith(IR.nullNode());
            compiler.reportChangeToEnclosingScope(aliasParent);
          }
          codeChanged = true;
          // Update the original aliased name to say that it has one less ALIASING_REF.
          name.removeRef(alias);
        }

        // Inlining the variable may have introduced new references
        // to descendants of {@code name}. So those need to be collected now.
        namespace.scanNewNodes(newNodes);
      }
    }

    /** Replaces all reads of a name with the name it aliases */
    private void rewriteAliasReferences(
        Name aliasingName, Ref aliasingRef, Set<AstChange> newNodes) {
      List<Ref> refs = new ArrayList<>(aliasingName.getRefs());
      for (Ref ref : refs) {
        if (ref.isSetFromGlobal()) {
          // Handled elsewhere
          continue;
        }

        checkState(ref.isGet(), ref); // names with local sets should not be rewritten
        // Twin refs that are both gets and sets are handled later, with the other sets.
        checkState(!ref.isTwin(), ref);
        if (ref.getNode().isStringKey()) {
          // e.g. `y` in `const {y} = x;`
          DestructuringGlobalNameExtractor.reassignDestructringLvalue(
              ref.getNode(), aliasingRef.getNode().cloneTree(), newNodes, ref, compiler);
        } else {
          // e.g. `x.y`
          checkState(ref.getNode().isGetProp() || ref.getNode().isName());
          Node newNode = aliasingRef.getNode().cloneTree();
          Node node = ref.getNode();
          newNode.srcref(node);
          node.replaceWith(newNode);
          compiler.reportChangeToEnclosingScope(newNode);
          newNodes.add(new AstChange(ref.scope, newNode));
        }
        aliasingName.removeRef(ref);
      }
    }

    /**
     * @param name The Name whose properties references should be updated.
     * @param value The value to use when rewriting.
     * @param depth The chain depth.
     * @param newNodes Expression nodes that have been updated.
     */
    private void rewriteAliasProps(Name name, Node value, int depth, Set<AstChange> newNodes) {
      if (name.props == null) {
        return;
      }
      Preconditions.checkState(
          !value.matchesQualifiedName(name.getFullName()),
          "%s should not match name %s",
          value,
          name.getFullName());
      for (Name prop : name.props) {
        rewriteNestedAliasReference(value, depth, newNodes, prop);
      }
    }

    /**
     * Replaces references to an alias that are nested inside a longer getprop chain or an object
     * literal
     *
     * <p>For example: if we have an inlined alias 'const A = B;', and reference a property 'A.x',
     * then this method is responsible for replacing 'A.x' with 'B.x'.
     *
     * <p>This is necessary because in the above example, given 'A.x', there is only one {@link Ref}
     * that points to the whole name 'A.x', not a direct {@link Ref} to 'A'. So the only way to
     * replace 'A.x' with 'B.x' is by looking at the property 'x' reference.
     *
     * @param value The value to use when rewriting.
     * @param depth The property chain depth.
     * @param newNodes Expression nodes that have been updated.
     * @param prop The property to rewrite with value.
     */
    private void rewriteNestedAliasReference(
        Node value, int depth, Set<AstChange> newNodes, Name prop) {
      rewriteAliasProps(prop, value, depth + 1, newNodes);
      List<Ref> refs = new ArrayList<>(prop.getRefs());
      for (Ref ref : refs) {
        Node target = ref.getNode();
        if (target.isStringKey() && target.getParent().isDestructuringPattern()) {
          // Do nothing for alias properties accessed through object destructuring. This would be
          // redundant. This method is intended for names nested inside getprop chains, because
          // GlobalNamespace only creates a single Ref for the outermost getprop. However, for
          // destructuring property accesses, GlobalNamespace creates multiple Refs, one for the
          // destructured object, and one for each string key in the pattern.
          //
          // For example, consider:
          //   const originalObj = {key: 0};
          //   const rhs = originalObj;
          //   const {key: lhs} = rhs;
          //   const otherLhs = rhs.key;
          // AggressiveInlineAliases is inlining rhs -> originalObj.
          //
          // GlobalNamespace creates two Refs for the name 'rhs': one for its declaration,
          // and one for 'const {key: lhs} = rhs;'. There is no Ref pointing directly to the 'rhs'
          // in 'const otherLhs = rhs.key', though.
          // There are also two Refs to the name 'rhs.key': one for the destructuring access and one
          // for the getprop access. This loop will visit both Refs.
          // This method is responsible for inlining "const otherLhs = originalObj.key" but not
          // "const {key: lhs} = originalObj;". We bail out at the Ref in the latter case.
          checkState(
              target.getGrandparent().isAssign() || target.getGrandparent().isDestructuringLhs(),
              // Currently GlobalNamespace doesn't create Refs for 'b' in const {a: {b}} = obj;
              // If it does start creating those Refs, we may have to update this method to handle
              // them explicitly.
              "Did not expect GlobalNamespace to create Ref for key in nested object pattern %s",
              target);
          continue;
        }

        for (int i = 0; i <= depth; i++) {
          if (target.isGetProp()) {
            target = target.getFirstChild();
          } else if (NodeUtil.isObjectLitKey(target)) {
            // Object literal key definitions are a little trickier, as we
            // need to find the assignment target
            Node gparent = target.getGrandparent();
            if (gparent.isAssign()) {
              target = gparent.getFirstChild();
            } else {
              checkState(NodeUtil.isObjectLitKey(gparent));
              target = gparent;
            }
          } else {
            throw new IllegalStateException("unexpected node: " + target);
          }
        }
        checkState(target.isGetProp() || target.isName());
        Node newValue = value.cloneTree();
        target.replaceWith(newValue);
        compiler.reportChangeToEnclosingScope(newValue);
        prop.removeRef(ref);
        // Rescan the expression root.
        newNodes.add(new AstChange(ref.scope, ref.getNode()));
        codeChanged = true;
      }
    }
  }

  /**
   * Rewrite "simple" destructuring aliases to a format that is more amenable to inlining.
   *
   * <p>To be specific, this rewrites aliases of the form: const {x} = qualified.name; to: const x =
   * qualified.name.x;
   */
  private static class RewriteSimpleDestructuringAliases extends AbstractPostOrderCallback {

    public static boolean isSimpleDestructuringAlias(Node n) {
      if (!NodeUtil.isStatement(n) || !n.isConst()) {
        return false;
      }
      checkState(n.hasOneChild());
      Node destructuringLhs = n.getFirstChild();
      if (!destructuringLhs.isDestructuringLhs()) {
        return false;
      }
      Node objectPattern = destructuringLhs.getFirstChild();
      if (!objectPattern.isObjectPattern()) {
        return false;
      }
      Node rhs = destructuringLhs.getLastChild();
      if (!rhs.isQualifiedName()) {
        return false;
      }
      return isSimpleDestructuringPattern(objectPattern);
    }

    private static boolean isSimpleDestructuringPattern(Node objectPattern) {
      checkArgument(objectPattern.isObjectPattern());
      for (Node key = objectPattern.getFirstChild(); key != null; key = key.getNext()) {
        if (!key.isStringKey() || key.isQuotedStringKey()) {
          return false;
        }
        checkState(key.hasOneChild());
        Node rhs = key.getFirstChild();
        if (!rhs.isObjectPattern() && !rhs.isName()) {
          return false;
        }
        if (rhs.isObjectPattern() && !isSimpleDestructuringPattern(rhs)) {
          return false;
        }
      }
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!isSimpleDestructuringAlias(n)) {
        return;
      }

      Node insertionPoint = n;

      Node destructuringLhs = n.getFirstChild();
      Node objectPattern = destructuringLhs.getFirstChild();
      Node rhs = destructuringLhs.getLastChild();
      Node unusedNewInsertionPoint = expandObjectPattern(t, insertionPoint, objectPattern, rhs);
      n.detach();
      t.reportCodeChange();
    }

    private Node expandObjectPattern(
        NodeTraversal t, Node insertionPoint, Node objectPattern, Node rhs) {
      for (Node key = objectPattern.getFirstChild(); key != null; key = key.getNext()) {
        Node keyChild = key.getFirstChild();
        final Node nameNode;
        if (keyChild.isName()) {
          nameNode = keyChild.detach();
        } else {
          checkState(keyChild.isObjectPattern());
          String uniqueId = t.getCompiler().getUniqueIdSupplier().getUniqueId(t.getInput());
          nameNode = IR.name("destructuring$" + uniqueId).srcref(keyChild);
        }
        Node newRhs = IR.getprop(rhs.cloneTree(), key.getString()).srcref(keyChild);
        Node newConstNode = IR.constNode(nameNode, newRhs).srcref(objectPattern);
        newConstNode.insertAfter(insertionPoint);
        insertionPoint = newConstNode;
        if (keyChild.isObjectPattern()) {
          insertionPoint = expandObjectPattern(t, insertionPoint, keyChild, nameNode.cloneNode());
        }
      }
      return insertionPoint;
    }
  }

  private static @Nullable Node maybeGetInnerNameNode(Node maybeFunctionOrClassNode) {
    if (NodeUtil.isFunctionExpression(maybeFunctionOrClassNode)) {
      Node nameNode = maybeFunctionOrClassNode.getFirstChild();
      checkState(nameNode.isName(), nameNode);
      // functions with no name have a NAME node with an empty string
      return nameNode.getString().isEmpty() ? null : nameNode;
    } else if (NodeUtil.isClassExpression(maybeFunctionOrClassNode)) {
      Node nameNode = maybeFunctionOrClassNode.getFirstChild();
      // classes with no name have an EMPTY node first child
      return nameNode.isName() ? nameNode : null;
    } else {
      return null; // not a function or class expression
    }
  }

  /**
   * Adds properties of `name` to the worklist if the following conditions hold:
   *
   * <ol>
   *   <li>1. The given property of `name` either meets condition (a) or is unsafely collapsible (as
   *       defined by {@link Name#canCollapse()}
   *   <li>2. `name` meets condition (b)
   * </ol>
   *
   * This only adds direct properties of a name, not all its descendants. For example, this adds
   * `a.b` given `a`, but not `a.b.c`.
   */
  private static void maybeAddPropertiesToWorklist(Name name, Deque<Name> workList) {
    if (!(name.isObjectLiteral() || name.isFunction() || name.isClass())) {
      // Don't add properties for things like `Foo` in
      //   const Foo = someMysteriousFunctionCall();
      // Since `Foo` is not declared as an object, class, or function literal, assume its value
      // may be aliased somewhere and its properties do not meet condition (a).
      return;
    }
    if (isUnsafelyReassigned(name)) {
      // Don't add properties if this was assigned multiple times, except for 'safe'
      // reassignments:
      //    var ns = ns || {};
      // This is equivalent to condition (b)
      return;
    }
    if (name.props == null) {
      return;
    }

    if (name.getAliasingGets() == 0) {
      // All of {@code name}'s children meet condition (a), so they can be
      // added to the worklist.
      workList.addAll(name.props);
    } else {
      // The children do NOT meet condition (a) but we may try to add them anyway.
      // This is because CollapseProperties will unsafely collapse properties on constructors and
      // enums, so we want to be more aggressive about inlining references to their children.
      for (Name property : name.props) {
        // Only add properties that would be unsafely collapsed by CollapseProperties
        if (property.canCollapse()) {
          workList.add(property);
        }
      }
    }
  }

  /**
   * Returns true if the alias is possibly defined in the global scope, which we handle with more
   * caution than with locally scoped variables. May return false positives.
   *
   * @param alias An aliasing get.
   * @return If the alias is possibly defined in the global scope.
   */
  private static boolean mayBeGlobalAlias(Ref alias) {
    // Note: alias.scope is the closest scope in which the aliasing assignment occurred.
    // So for "if (true) { var alias = aliasedVar; }", the alias.scope would be the IF block
    // scope.
    if (alias.scope.isGlobal()) {
      return true;
    }
    // If the scope in which the alias is assigned is not global, look up the LHS of the
    // assignment.
    Node aliasParent = alias.getNode().getParent();
    if (!aliasParent.isAssign() && !aliasParent.isName()) {
      // Only handle variable assignments and initializing declarations.
      return true;
    }
    Node aliasLhsNode = aliasParent.isName() ? aliasParent : aliasParent.getFirstChild();
    if (!aliasLhsNode.isName()) {
      // Only handle assignments to simple names, not qualified names or GETPROPs.
      return true;
    }
    String aliasVarName = aliasLhsNode.getString();
    Var aliasVar = alias.scope.getVar(aliasVarName);
    if (aliasVar != null) {
      return aliasVar.isGlobal();
    }
    return true;
  }

  /**
   * Returns whether a ReferenceCollection for some aliasing variable references a property on the
   * original aliased variable that may be collapsed in CollapseProperties.
   *
   * <p>See {@link Name#canCollapse} for what can/cannot be collapsed.
   */
  private static boolean referencesCollapsibleProperty(
      ReferenceCollection aliasRefs, Name aliasedName, GlobalNamespace namespace) {
    for (Reference ref : aliasRefs.references) {
      if (ref.getParent() == null) {
        continue;
      }
      if (NodeUtil.isNormalOrOptChainGetProp(ref.getParent())) {
        // e.g. if the reference is "alias.b.someProp", this will be "b".
        String propertyName = ref.getParent().getString();
        // e.g. if the aliased name is "originalName", this will be "originalName.b".
        String originalPropertyName = aliasedName.getName() + "." + propertyName;
        Name originalProperty = namespace.getOwnSlot(originalPropertyName);
        // If the original property isn't in the namespace or can't be collapsed, keep going.
        if (originalProperty == null || !originalProperty.canCollapse()) {
          continue;
        }
        return true;
      }
    }
    return false;
  }

  /** Check if the name has multiple sets that are not of the form "a = a || {}" */
  private static boolean isUnsafelyReassigned(Name name) {
    boolean foundOriginalDefinition = false;
    for (Ref ref : name.getRefs()) {
      if (!ref.isSet()) {
        continue;
      }
      if (isSafeNamespaceReinit(ref)) {
        continue;
      }
      if (!foundOriginalDefinition) {
        foundOriginalDefinition = true;
      } else {
        return true;
      }
    }
    return false;
  }

  /**
   * Tries to find an lvalue for the subclass given the superclass node in an `class ... extends `
   * clause
   *
   * <p>Only handles cases where we have either a class declaration or a class expression in an
   * assignment or name declaration. Otherwise returns null.
   */
  private static @Nullable Node getSubclassForEs6Superclass(Node superclass) {
    Node classNode = superclass.getParent();
    checkArgument(classNode.isClass(), classNode);
    if (NodeUtil.isNameDeclaration(classNode.getGrandparent())) {
      // const Clazz = class extends Super {
      return classNode.getParent();
    } else if (superclass.getGrandparent().isAssign()) {
      // ns.foo.Clazz = class extends Super {
      return classNode.getPrevious();
    } else if (NodeUtil.isClassDeclaration(classNode)) {
      // class Clazz extends Super {
      return classNode.getFirstChild();
    }
    return null;
  }

  /**
   * Flattens global objects/namespaces by replacing each '.' with '$' in their names.
   *
   * <p>This reduces the number of property lookups the browser has to do and allows the {@link
   * RenameVars} pass to shorten namespaced names. For example, goog.events.handleEvent() ->
   * goog$events$handleEvent() -> Za().
   *
   * <p>If a global object's name is assigned to more than once, or if a property is added to the
   * global object in a complex expression, then none of its properties will be collapsed (for
   * safety/correctness).
   *
   * <p>If, after a global object is declared, it is never referenced except when its properties are
   * read or set, then the object will be removed after its properties have been collapsed.
   *
   * <p>Uninitialized variable stubs are created at a global object's declaration site for any of
   * its properties that are added late in a local scope.
   *
   * <p>Static properties of constructors are always collapsed, unsafely! For other objects: if,
   * after an object is declared, it is referenced directly in a way that might create an alias for
   * it, then none of its properties will be collapsed. This behavior is a safeguard to prevent the
   * values associated with the flattened names from getting out of sync with the object's actual
   * property values. For example, in the following case, an alias a$b, if created, could easily
   * keep the value 0 even after a.b became 5: <code> a = {b: 0}; c = a; c.b = 5; </code>.
   *
   * <p>This pass may break code, but relies on {@link AggressiveInlineAliases} running before this
   * pass to make some common patterns safer.
   *
   * <p>This pass doesn't flatten property accesses of the form: a[b].
   *
   * <p>For lots of examples, see the unit test.
   */
  class CollapseProperties implements CompilerPass {

    /** Maps names (e.g. "a.b.c") to nodes in the global namespace tree */
    private Map<String, Name> nameMap;

    private final HashSet<String> dynamicallyImportedModules = new HashSet<>();

    @Override
    public void process(Node externs, Node root) {
      if (propertyCollapseLevel == PropertyCollapseLevel.MODULE_EXPORT
          || chunkOutputType == ChunkOutputType.ES_MODULES) {
        NodeTraversal.traverse(
            compiler,
            root,
            new FindDynamicallyImportedModules(haveModulesBeenRewritten, moduleResolutionMode));
      }

      nameMap = checkNotNull(namespace, "namespace was not initialized").getNameIndex();
      List<Name> globalNames = namespace.getNameForest();
      ImmutableSet<Name> escaped = checkNamespaces();
      for (Name name : globalNames) {
        flattenReferencesToCollapsibleDescendantNames(name, name.getBaseName(), escaped);
        // We collapse property definitions after collapsing property references
        // because this step can alter the parse tree above property references,
        // invalidating the node ancestry stored with each reference.
        collapseDeclarationOfNameAndDescendants(name, name.getBaseName(), escaped);
      }
    }

    private boolean canCollapse(Name name) {
      final Inlinability inlinability = name.canCollapseOrInline();
      if (!inlinability.canCollapse()) {
        logDecisionForName(name, inlinability, "canCollapse() returns false");
        return false;
      }

      if (propertyCollapseLevel == PropertyCollapseLevel.MODULE_EXPORT) {
        if (!name.isModuleExport()) {
          logDecisionForName(name, inlinability, "module export: canCollapse() returns false");
          return false;
        } else if (dynamicallyImportedModules.contains(name.getBaseName())) {
          logDecisionForName(
              name, inlinability, "dynamic module export: canCollapse() returns false");
          return false;
        }
      }

      logDecisionForName(name, inlinability, "canCollapse() returns true");
      return true;
    }

    private boolean canEliminate(Name name) {
      if (!name.canEliminate()) {
        return false;
      }

      if (name.props == null
          || name.props.isEmpty()
          || propertyCollapseLevel != PropertyCollapseLevel.MODULE_EXPORT) {
        return true;
      }

      return false;
    }

    /**
     * Runs through all namespaces (prefixes of classes and enums), and checks if any of them have
     * been used in an unsafe way.
     */
    private ImmutableSet<Name> checkNamespaces() {
      ImmutableSet.Builder<Name> escaped = ImmutableSet.builder();
      HashSet<String> dynamicallyImportedModuleRefs = new HashSet<>(dynamicallyImportedModules);
      if (!dynamicallyImportedModules.isEmpty()) {
        // When the output chunk type is ES_MODULES, properties of the module namespace
        // must not be collapsed as they are referenced off the namespace object. The namespace
        // objects escape via the dynamic import expression.
        for (Name name : nameMap.values()) {
          // Test if the name is a rewritten module namespace variable and if so mark it as escaped
          // to prevent any property collapsing.
          //
          // example:
          // /** @const */ var module$foo = {};
          if (dynamicallyImportedModules.contains(name.getFullName())) {
            logDecisionForName(name, "escapes - dynamically imported module namespace");
            escaped.add(name);
            if (name.props == null) {
              continue;
            }
            for (Name prop : name.props) {
              Ref propDeclaration = prop.getDeclaration();
              if (propDeclaration == null) {
                continue;
              }
              if (propDeclaration.getNode() != null) {
                // ES Module rewriting creates aliases on the module namespace object. These aliased
                // names also escape and their properties may not be collapsed.
                //
                // example:
                // class Foo$$module$foo {
                //   static bar() { return 'bar'; }
                // }
                // /** @const */ var module$foo = {};
                // /** @const */ module$foo.Foo = Foo$$module$foo;
                //
                // While module$foo.Foo cannot be collapsed because we marked the module namespace
                // as escaped, we also need to prevent any property collapsing on the
                // Foo$$module$foo
                // class itself.
                Node rValue = NodeUtil.getRValueOfLValue(propDeclaration.getNode());
                if (rValue.isName()) {
                  logDecisionForName(
                      name, "escapes - dynamically imported module namespace property alias");
                  dynamicallyImportedModuleRefs.add(rValue.getQualifiedName());
                }
              }
            }
          }
        }
      }

      for (Name name : nameMap.values()) {
        if (!dynamicallyImportedModuleRefs.isEmpty()
            && dynamicallyImportedModuleRefs.contains(name.getFullName())) {
          escaped.add(name);
        }
        if (!name.isNamespaceObjectLit()) {
          continue;
        }
        if (name.getAliasingGets() == 0
            && name.getLocalSets() + name.getGlobalSets() <= 1
            && name.getDeleteProps() == 0) {
          continue;
        }
        boolean initialized = name.getDeclaration() != null;
        for (Ref ref : name.getRefs()) {
          if (ref.isDeleteProp()) {
            if (initialized) {
              warnAboutNamespaceRedefinition(name, ref);
            }
          } else if (ref.isSet() && ref != name.getDeclaration()) {
            if (initialized && !isSafeNamespaceReinit(ref)) {
              warnAboutNamespaceRedefinition(name, ref);
            }

            initialized = true;
          } else if (ref.isAliasingGet()) {
            warnAboutNamespaceAliasing(name, ref);
            logDecisionForName(name, "escapes");
            escaped.add(name);
            break;
          }
        }
      }
      return escaped.build();
    }

    /**
     * Reports a warning because a namespace was aliased.
     *
     * @param nameObj A namespace that is being aliased
     * @param ref The reference that forced the alias
     */
    private void warnAboutNamespaceAliasing(Name nameObj, Ref ref) {
      compiler.report(
          JSError.make(ref.getNode(), PARTIAL_NAMESPACE_WARNING, nameObj.getFullName()));
    }

    /**
     * Reports a warning because a namespace was redefined.
     *
     * @param nameObj A namespace that is being redefined
     * @param ref The reference that set the namespace
     */
    private void warnAboutNamespaceRedefinition(Name nameObj, Ref ref) {
      compiler.report(
          JSError.make(ref.getNode(), NAMESPACE_REDEFINED_WARNING, nameObj.getFullName()));
    }

    /**
     * Flattens all references to collapsible properties of a global name except their initial
     * definitions. Recurs on subnames.
     *
     * @param n An object representing a global name
     * @param alias The flattened name for {@code n}
     */
    private void flattenReferencesToCollapsibleDescendantNames(
        Name n, String alias, Set<Name> escaped) {
      if (n.props == null) {
        return;
      }
      if (n.isCollapsingExplicitlyDenied()) {
        logDecisionForName(n, "@nocollapse: will not flatten descendant name references");
        return;
      }
      if (escaped.contains(n)) {
        logDecisionForName(n, "escapes: will not flatten descendant name references");
        return;
      }

      for (Name p : n.props) {
        String propAlias = appendPropForAlias(alias, p.getBaseName());
        final Inlinability inlinability = p.canCollapseOrInline();

        boolean isAllowedToCollapse =
            propertyCollapseLevel != PropertyCollapseLevel.MODULE_EXPORT || p.isModuleExport();
        if (isAllowedToCollapse) {
          if (inlinability.canCollapse()) {
            logDecisionForName(p, inlinability, "will flatten references");
            flattenReferencesTo(p, propAlias);
          } else if (p.isCollapsingExplicitlyDenied()) {
            logDecisionForName(p, "@nocollapse: will not flatten references");
          } else if (p.isSimpleStubDeclaration()) {
            logDecisionForName(p, "simple stub declaration: will flatten references");
            flattenSimpleStubDeclaration(p, propAlias);
          } else {
            logDecisionForName(p, inlinability, "will not flatten references");
          }
        }

        flattenReferencesToCollapsibleDescendantNames(p, propAlias, escaped);
      }
    }

    private void logDecisionForName(Name name, Inlinability inlinability, String message) {
      logDecisionForName(
          name, () -> SimpleFormat.format("inlinability %s: %s", inlinability, message));
    }

    private void logDecisionForName(Name name, String message) {
      decisionsLog.log(() -> SimpleFormat.format("%s: %s", name.getFullName(), message));
    }

    private void logDecisionForName(Name name, Supplier<String> messageSupplier) {
      decisionsLog.log(
          () -> SimpleFormat.format("%s: %s", name.getFullName(), messageSupplier.get()));
    }

    /** Flattens a stub declaration. This is mostly a hack to support legacy users. */
    private void flattenSimpleStubDeclaration(Name name, String alias) {
      Ref ref = Iterables.getOnlyElement(name.getRefs());
      Node nameNode = NodeUtil.newName(compiler, alias, ref.getNode(), name.getFullName());
      Node varNode = IR.var(nameNode).srcrefIfMissing(nameNode);

      checkState(ref.getNode().getParent().isExprResult());
      Node parent = ref.getNode().getParent();
      parent.replaceWith(varNode);
      compiler.reportChangeToEnclosingScope(varNode);
    }

    /**
     * Flattens all references to a collapsible property of a global name except its initial
     * definition.
     *
     * @param n A global property name (e.g. "a.b" or "a.b.c.d")
     * @param alias The flattened name (e.g. "a$b" or "a$b$c$d")
     */
    private void flattenReferencesTo(Name n, String alias) {
      String originalName = n.getFullName();
      for (Ref r : n.getRefs()) {
        if (r == n.getDeclaration()) {
          // Declarations are handled separately.
          continue;
        }
        Node rParent = r.getNode().getParent();
        // We shouldn't flatten a reference that's an object literal key, because duplicate keys
        // show up as refs.
        if (!NodeUtil.mayBeObjectLitKey(r.getNode())) {
          flattenNameRef(alias, r.getNode(), rParent, originalName);
        } else if (r.getNode().isStringKey() && r.getNode().getParent().isObjectPattern()) {
          Node newNode = IR.name(alias).srcref(r.getNode());
          NodeUtil.copyNameAnnotations(r.getNode(), newNode);
          DestructuringGlobalNameExtractor.reassignDestructringLvalue(
              r.getNode(), newNode, null, r, compiler);
        }
      }

      // Flatten all occurrences of a name as a prefix of its subnames. For
      // example, if {@code n} corresponds to the name "a.b", then "a.b" will be
      // replaced with "a$b" in all occurrences of "a.b.c", "a.b.c.d", etc.
      if (n.props != null) {
        for (Name p : n.props) {
          flattenPrefixes(alias, originalName + p.getBaseName(), p, 1);
        }
      }
    }

    /**
     * Flattens all occurrences of a name as a prefix of subnames beginning with a particular
     * subname.
     *
     * @param n A global property name (e.g. "a.b.c.d")
     * @param alias A flattened prefix name (e.g. "a$b")
     * @param originalName The full original name of the global property (e.g. "a.b.c.d") equivalent
     *     to n.getFullName(), but pre-computed to save on intermediate string allocation
     * @param depth The difference in depth between the property name and the prefix name (e.g. 2)
     */
    private void flattenPrefixes(String alias, String originalName, Name n, int depth) {
      // Only flatten the prefix of a name declaration if the name being
      // initialized is fully qualified (i.e. not an object literal key).
      Ref decl = n.getDeclaration();
      if (decl != null && decl.getNode() != null && decl.getNode().isGetProp()) {
        flattenNameRefAtDepth(alias, decl.getNode(), depth, originalName);
      }

      for (Ref r : n.getRefs()) {
        if (r == decl) {
          // Declarations are handled separately.
          continue;
        }

        flattenNameRefAtDepth(alias, r.getNode(), depth, originalName);
      }

      if (n.props != null) {
        for (Name p : n.props) {
          flattenPrefixes(alias, originalName + p.getBaseName(), p, depth + 1);
        }
      }
    }

    /**
     * Flattens a particular prefix of a single name reference.
     *
     * @param alias A flattened prefix name (e.g. "a$b")
     * @param n The node corresponding to a subproperty name (e.g. "a.b.c.d")
     * @param depth The difference in depth between the property name and the prefix name (e.g. 2)
     * @param originalName String version of the property name.
     */
    private void flattenNameRefAtDepth(String alias, Node n, int depth, String originalName) {
      // This method has to work for both GETPROP chains and, in rare cases,
      // OBJLIT keys, possibly nested. That's why we check for children before
      // proceeding. In the OBJLIT case, we don't need to do anything.
      Token nType = n.getToken();
      boolean isQName = nType == Token.NAME || nType == Token.GETPROP;
      boolean isObjKey = NodeUtil.mayBeObjectLitKey(n);
      checkState(isObjKey || isQName);
      if (isQName) {
        for (int i = 1; i < depth && n.hasChildren(); i++) {
          n = n.getFirstChild();
        }
        if (n.isGetProp() && n.getFirstChild().isGetProp()) {
          flattenNameRef(alias, n.getFirstChild(), n, originalName);
        }
      }
    }

    /**
     * Replaces a GETPROP a.b.c with a NAME a$b$c.
     *
     * @param alias A flattened prefix name (e.g. "a$b")
     * @param n The GETPROP node corresponding to the original name (e.g. "a.b")
     * @param parent {@code n}'s parent
     * @param originalName String version of the property name.
     */
    private void flattenNameRef(String alias, Node n, Node parent, String originalName) {
      Preconditions.checkArgument(
          n.isGetProp(), "Expected GETPROP, found %s. Node: %s", n.getToken(), n);

      // BEFORE:
      //   getprop
      //     getprop
      //       name a
      //       string b
      //     string c
      // AFTER:
      //   name a$b$c
      Node ref = NodeUtil.newName(compiler, alias, n, originalName).copyTypeFrom(n);
      NodeUtil.copyNameAnnotations(n, ref);
      if (NodeUtil.isNormalOrOptChainCall(parent) && n.isFirstChildOf(parent)) {
        // The node was a call target. We are deliberately flattening these as
        // the "this" isn't provided by the namespace. Mark it as such:
        parent.putBooleanProp(Node.FREE_CALL, true);
      }

      n.replaceWith(ref);
      compiler.reportChangeToEnclosingScope(ref);
    }

    /**
     * Collapses definitions of the collapsible properties of a global name. Recurs on subnames that
     * also represent JavaScript objects with collapsible properties.
     *
     * @param n A node representing a global name
     * @param alias The flattened name for {@code n}
     */
    private void collapseDeclarationOfNameAndDescendants(Name n, String alias, Set<Name> escaped) {
      final Inlinability childNameInlinability = n.canCollapseOrInlineChildNames();
      final boolean canCollapseChildNames;
      if (!childNameInlinability.canCollapse()) {
        logDecisionForName(
            n,
            () ->
                SimpleFormat.format(
                    "child name inlinability: %s: will not collapse child names",
                    childNameInlinability));
        canCollapseChildNames = false;
      } else if (escaped.contains(n)) {
        logDecisionForName(n, "escapes: will not collapse child names");
        canCollapseChildNames = false;
      } else {
        canCollapseChildNames = true;
      }

      // Handle this name first so that nested object literals get unrolled.
      if (canCollapse(n)) {
        logDecisionForName(n, "collapsing");
        updateGlobalNameDeclaration(n, alias, canCollapseChildNames);
      }

      if (n.props == null || escaped.contains(n)) {
        return;
      }
      logDecisionForName(n, "collapsing descendants");
      for (Name p : n.props) {
        collapseDeclarationOfNameAndDescendants(
            p, appendPropForAlias(alias, p.getBaseName()), escaped);
      }
    }

    /**
     * Updates the initial assignment to a collapsible property at global scope by adding a VAR stub
     * and collapsing the property. e.g. c = a.b = 1; => var a$b; c = a$b = 1; This specifically
     * handles "twinned" assignments, which are those where the assignment is also used as a
     * reference and which need special handling.
     *
     * @param alias The flattened property name (e.g. "a$b")
     * @param refName The name for the reference being updated.
     * @param ref An object containing information about the assignment getting updated
     */
    private void updateTwinnedDeclaration(String alias, Name refName, Ref ref) {
      checkState(ref.isTwin(), ref);
      // Don't handle declarations of an already flat name, just qualified names.
      if (!ref.getNode().isGetProp()) {
        return;
      }
      Node rvalue = ref.getNode().getNext();
      Node parent = ref.getNode().getParent();
      Node grandparent = parent.getParent();

      if (rvalue != null && rvalue.isFunction()) {
        checkForReceiverAffectedByCollapse(rvalue, refName.getJSDocInfo(), refName);
      }

      // Create the new alias node.
      Node nameNode =
          NodeUtil.newName(compiler, alias, grandparent.getFirstChild(), refName.getFullName());
      NodeUtil.copyNameAnnotations(ref.getNode(), nameNode);

      // BEFORE:
      // ... (x.y = 3);
      //
      // AFTER:
      // var x$y;
      // ... (x$y = 3);

      Node current = grandparent;
      Node currentParent = grandparent.getParent();
      for (;
          !currentParent.isScript() && !currentParent.isBlock();
          current = currentParent, currentParent = currentParent.getParent()) {}

      // Create a stub variable declaration right
      // before the current statement.
      Node stubVar = IR.var(nameNode.cloneTree()).srcrefIfMissing(nameNode);
      stubVar.insertBefore(current);

      ref.getNode().replaceWith(nameNode);
      compiler.reportChangeToEnclosingScope(nameNode);
    }

    /**
     * Updates the first initialization (a.k.a "declaration") of a global name. This involves
     * flattening the global name (if it's not just a global variable name already), collapsing
     * object literal keys into global variables, declaring stub global variables for properties
     * added later in a local scope.
     *
     * <p>It may seem odd that this function also takes care of declaring stubs for direct children.
     * The ultimate goal of this function is to eliminate the global name entirely (when possible),
     * so that "middlemen" namespaces disappear, and to do that we need to make sure that all the
     * direct children will be collapsed as well.
     *
     * @param n An object representing a global name (e.g. "a", "a.b.c")
     * @param alias The flattened name for {@code n} (e.g. "a", "a$b$c")
     * @param canCollapseChildNames Whether it's possible to collapse children of this name. (This
     *     is mostly passed for convenience; it's equivalent to n.canCollapseChildNames()).
     */
    private void updateGlobalNameDeclaration(Name n, String alias, boolean canCollapseChildNames) {
      Ref decl = n.getDeclaration();
      if (decl == null) {
        // Some names do not have declarations, because they
        // are only defined in local scopes.
        logDecisionForName(n, "no global declaration found");
        return;
      }

      final Node declNode = decl.getNode();
      switch (declNode.getParent().getToken()) {
        case ASSIGN:
          logDeclarationAction(n, declNode, "updating assignment");
          updateGlobalNameDeclarationAtAssignNode(n, alias, canCollapseChildNames);
          break;
        case VAR:
        case LET:
        case CONST:
          logDeclarationAction(n, declNode, "updating variable declaration");
          updateGlobalNameDeclarationAtVariableNode(n, canCollapseChildNames);
          break;
        case FUNCTION:
          logDeclarationAction(n, declNode, "updating function declaration");
          updateGlobalNameDeclarationAtFunctionNode(n, canCollapseChildNames);
          break;
        case CLASS:
          logDeclarationAction(n, declNode, "updating class declaration");
          updateGlobalNameDeclarationAtClassNode(n, canCollapseChildNames);
          break;
        case CLASS_MEMBERS:
          logDeclarationAction(n, declNode, "updating static member declaration");
          updateGlobalNameDeclarationAtStaticMemberNode(n, alias, canCollapseChildNames);
          break;
        default:
          logDeclarationAction(n, declNode, "not updating an unsupported type of declaration node");
          break;
      }
    }

    private void logDeclarationAction(Name name, Node declarationNode, String message) {
      logDecisionForName(name, () -> SimpleFormat.format("%s: %s", declarationNode, message));
    }

    /**
     * Updates the first initialization (a.k.a "declaration") of a global name that occurs at an
     * ASSIGN node. See comment for {@link #updateGlobalNameDeclaration}.
     *
     * @param n An object representing a global name (e.g. "a", "a.b.c")
     * @param alias The flattened name for {@code n} (e.g. "a", "a$b$c")
     */
    private void updateGlobalNameDeclarationAtAssignNode(
        Name n, String alias, boolean canCollapseChildNames) {
      // NOTE: It's important that we don't add additional nodes
      // (e.g. a var node before the exprstmt) because the exprstmt might be
      // the child of an if statement that's not inside a block).

      // All qualified names - even for variables that are initially declared as LETS and CONSTS -
      // are being declared as VAR statements, but this is not incorrect because
      // we are only collapsing for global names.
      Ref ref = n.getDeclaration();
      Node rvalue = ref.getNode().getNext();
      if (ref.isTwin()) {
        updateTwinnedDeclaration(alias, n, ref);
        return;
      }
      Node varNode = new Node(Token.VAR);
      Node varParent = ref.getNode().getAncestor(3);
      Node grandparent = ref.getNode().getAncestor(2);
      boolean isObjLit = rvalue.isObjectLit();
      boolean insertedVarNode = false;

      if (isObjLit && canEliminate(n)) {
        // Eliminate the object literal altogether.
        grandparent.replaceWith(varNode);
        n.updateRefNode(ref, null);
        insertedVarNode = true;
        compiler.reportChangeToEnclosingScope(varNode);
      } else if (!n.isSimpleName()) {
        // Create a VAR node to declare the name.
        if (rvalue.isFunction()) {
          checkForReceiverAffectedByCollapse(rvalue, n.getJSDocInfo(), n);
        }

        compiler.reportChangeToEnclosingScope(rvalue);
        rvalue.detach();

        Node nameNode =
            NodeUtil.newName(compiler, alias, ref.getNode().getAncestor(2), n.getFullName());

        Node constPropNode = ref.getNode();
        JSDocInfo info = NodeUtil.getBestJSDocInfo(ref.getNode().getParent());
        nameNode.putBooleanProp(
            Node.IS_CONSTANT_NAME,
            (info != null && info.hasConstAnnotation())
                || constPropNode.getBooleanProp(Node.IS_CONSTANT_NAME));

        if (info != null) {
          varNode.setJSDocInfo(info);
        }
        varNode.addChildToBack(nameNode);
        nameNode.addChildToFront(rvalue);
        grandparent.replaceWith(varNode);

        // Update the node ancestry stored in the reference.
        n.updateRefNode(ref, nameNode);
        insertedVarNode = true;
        compiler.reportChangeToEnclosingScope(varNode);
      }

      if (canCollapseChildNames) {
        if (isObjLit) {
          declareVariablesForObjLitValues(n, alias, rvalue, varNode, varNode.getPrevious());
        }

        addStubsForUndeclaredProperties(n, alias, varParent, varNode);
      }

      if (insertedVarNode) {
        if (!varNode.hasChildren()) {
          varNode.detach();
        }
      }
    }

    /**
     * Warns about any references to "this" in the given FUNCTION. The function is getting
     * collapsed, so the references will change.
     */
    private void checkForReceiverAffectedByCollapse(Node function, JSDocInfo docInfo, Name name) {
      checkState(function.isFunction());

      if (docInfo != null) {
        // Don't rely on type inference for this check.

        if (docInfo.isConstructorOrInterface()) {
          return; // Ctors and interfaces need to be able to reference `this`
        }
        if (docInfo.hasThisType()) {
          /*
           * Use `@this` as a signal that the reference is intentional.
           *
           * <p>TODO(b/156823102): This signal also silences the check on all transpiled static
           * methods.
           */
          return;
        }
      }

      // Use the NodeUtil method so we don't forget to update this logic.
      if (NodeUtil.referencesOwnReceiver(function)) {
        compiler.report(JSError.make(function, RECEIVER_AFFECTED_BY_COLLAPSE, name.getFullName()));
      }
    }

    /**
     * Updates the first initialization (a.k.a "declaration") of a global name that occurs at a VAR
     * node. See comment for {@link #updateGlobalNameDeclaration}.
     *
     * @param n An object representing a global name (e.g. "a")
     */
    private void updateGlobalNameDeclarationAtVariableNode(Name n, boolean canCollapseChildNames) {
      if (!canCollapseChildNames) {
        logDecisionForName(n, "cannot collapse child names: skipping");
        return;
      }

      Ref ref = n.getDeclaration();
      String name = ref.getNode().getString();
      Node rvalue = ref.getNode().getFirstChild();
      Node variableNode = ref.getNode().getParent();
      Node grandparent = variableNode.getParent();

      boolean isObjLit = rvalue.isObjectLit();

      if (isObjLit) {
        declareVariablesForObjLitValues(n, name, rvalue, variableNode, variableNode.getPrevious());
      }

      addStubsForUndeclaredProperties(n, name, grandparent, variableNode);

      if (isObjLit && canEliminate(n)) {
        ref.getNode().detach();
        compiler.reportChangeToEnclosingScope(variableNode);
        if (!variableNode.hasChildren()) {
          variableNode.detach();
        }

        // Clear out the object reference, since we've eliminated it from the
        // parse tree.
        n.updateRefNode(ref, null);
      }
    }

    /**
     * Updates the first initialization (a.k.a "declaration") of a global name that occurs at a
     * FUNCTION node. See comment for {@link #updateGlobalNameDeclaration}.
     *
     * @param n An object representing a global name (e.g. "a")
     */
    private void updateGlobalNameDeclarationAtFunctionNode(Name n, boolean canCollapseChildNames) {
      if (!canCollapseChildNames || !canCollapse(n)) {
        return;
      }

      Ref ref = n.getDeclaration();
      String fnName = ref.getNode().getString();
      addStubsForUndeclaredProperties(
          n, fnName, ref.getNode().getAncestor(2), ref.getNode().getParent());
    }

    /**
     * Updates the first initialization (a.k.a "declaration") of a global name that occurs at a
     * CLASS node. See comment for {@link #updateGlobalNameDeclaration}.
     *
     * @param n An object representing a global name (e.g. "a")
     */
    private void updateGlobalNameDeclarationAtClassNode(Name n, boolean canCollapseChildNames) {
      if (!canCollapseChildNames || !canCollapse(n)) {
        return;
      }

      Ref ref = n.getDeclaration();
      String className = ref.getNode().getString();
      addStubsForUndeclaredProperties(
          n, className, ref.getNode().getAncestor(2), ref.getNode().getParent());
    }

    /**
     * Updates the first initialization (a.k.a "declaration") of a global name that occurs in a
     * static MEMBER_FUNCTION_DEF in a class. See comment for {@link #updateGlobalNameDeclaration}.
     *
     * @param n A static MEMBER_FUNCTION_DEF in a class assigned to a global name (e.g. `a.b`)
     * @param alias The new flattened name for `n` (e.g. "a$b")
     * @param canCollapseChildNames whether properties of `n` are also collapsible, meaning that any
     *     properties only assigned locally need stub declarations
     */
    private void updateGlobalNameDeclarationAtStaticMemberNode(
        Name n, String alias, boolean canCollapseChildNames) {

      Ref declaration = n.getDeclaration();
      Node classNode = declaration.getNode().getGrandparent();
      checkState(classNode.isClass(), classNode);
      Node enclosingStatement = NodeUtil.getEnclosingStatement(classNode);

      if (canCollapseChildNames) {
        addStubsForUndeclaredProperties(n, alias, enclosingStatement.getParent(), classNode);
      }

      // detach `static m() {}` from `class Foo { static m() {} }`
      Node memberFn = declaration.getNode().detach();
      Node fnNode = memberFn.getOnlyChild().detach();
      checkForReceiverAffectedByCollapse(fnNode, memberFn.getJSDocInfo(), n);

      // add a var declaration, creating `var Foo$m = function() {}; class Foo {}`
      Node varDecl = IR.var(NodeUtil.newName(compiler, alias, memberFn), fnNode).srcref(memberFn);
      varDecl.insertBefore(enclosingStatement);
      // We would lose optimization-relevant jsdoc tags here because they are stored on the class
      // member node, not the function node. Copy them over to the new declaration statement so
      // later passes can make use of them.
      varDecl.setJSDocInfo(memberFn.getJSDocInfo());
      compiler.reportChangeToEnclosingScope(varDecl);

      // collapsing this name's properties requires updating this Ref
      n.updateRefNode(declaration, varDecl.getFirstChild());
    }

    /**
     * Declares global variables to serve as aliases for the values in an object literal, optionally
     * removing all of the object literal's keys and values.
     *
     * @param alias The object literal's flattened name (e.g. "a$b$c")
     * @param objlit The OBJLIT node
     * @param varNode The VAR node to which new global variables should be added as children
     * @param nameToAddAfter The child of {@code varNode} after which new variables should be added
     *     (may be null)
     */
    private void declareVariablesForObjLitValues(
        Name objlitName, String alias, Node objlit, Node varNode, Node nameToAddAfter) {
      int arbitraryNameCounter = 0;
      boolean discardKeys = !objlitName.shouldKeepKeys();

      for (Node key = objlit.getFirstChild(), nextKey; key != null; key = nextKey) {
        Node value = key.getFirstChild();
        nextKey = key.getNext();

        // A computed property, or a get or a set can not be rewritten as a VAR. We don't know what
        // properties will be generated by a spread.
        switch (key.getToken()) {
          case GETTER_DEF:
          case SETTER_DEF:
          case COMPUTED_PROP:
          case OBJECT_SPREAD:
            continue;
          case STRING_KEY:
          case MEMBER_FUNCTION_DEF:
            break;
          default:
            throw new IllegalStateException("Unexpected child of OBJECTLIT: " + key.toStringTree());
        }

        // We generate arbitrary names for keys that aren't valid JavaScript
        // identifiers, since those keys are never referenced. (If they were,
        // this object literal's child names wouldn't be collapsible.) The only
        // reason that we don't eliminate them entirely is the off chance that
        // their values are expressions that have side effects.
        boolean isJsIdentifier = !key.isNumber() && TokenStream.isJSIdentifier(key.getString());
        String propName = isJsIdentifier ? key.getString() : String.valueOf(++arbitraryNameCounter);

        // If the name cannot be collapsed, skip it.
        String qName = objlitName.getFullName() + '.' + propName;
        Name p = nameMap.get(qName);
        if (p != null && !canCollapse(p)) {
          continue;
        }

        String propAlias = appendPropForAlias(alias, propName);
        Node refNode = null;
        if (discardKeys) {
          key.detach();
          value.detach();
          // Don't report a change here because the objlit has already been removed from the tree.
        } else {
          // Substitute a reference for the value.
          refNode = IR.name(propAlias);
          if (key.getBooleanProp(Node.IS_CONSTANT_NAME)) {
            refNode.putBooleanProp(Node.IS_CONSTANT_NAME, true);
          }

          value.replaceWith(refNode);
          compiler.reportChangeToEnclosingScope(refNode);
        }

        // Declare the collapsed name as a variable with the original value.
        Node nameNode = IR.name(propAlias);
        nameNode.addChildToFront(value);
        if (key.getBooleanProp(Node.IS_CONSTANT_NAME)) {
          nameNode.putBooleanProp(Node.IS_CONSTANT_NAME, true);
        }
        Node newVar = IR.var(nameNode).srcrefTreeIfMissing(key);
        if (nameToAddAfter != null) {
          newVar.insertAfter(nameToAddAfter);
        } else {
          newVar.insertBefore(varNode);
        }
        compiler.reportChangeToEnclosingScope(newVar);
        nameToAddAfter = newVar;

        // Update the global name's node ancestry if it hasn't already been
        // done. (Duplicate keys in an object literal can bring us here twice
        // for the same global name.)
        if (isJsIdentifier && p != null) {
          if (!discardKeys) {
            p.addAliasingGetClonedFromDeclaration(refNode);
          }

          p.updateRefNode(p.getDeclaration(), nameNode);

          if (value.isFunction()) {
            checkForReceiverAffectedByCollapse(value, key.getJSDocInfo(), p);
          }
        }
      }
    }

    /**
     * Adds global variable "stubs" for any properties of a global name that are only set in a local
     * scope or read but never set.
     *
     * @param n An object representing a global name (e.g. "a", "a.b.c")
     * @param alias The flattened name of the object whose properties we are adding stubs for (e.g.
     *     "a$b$c")
     * @param parent The node to which new global variables should be added as children
     * @param addAfter The child of after which new variables should be added
     */
    private void addStubsForUndeclaredProperties(Name n, String alias, Node parent, Node addAfter) {
      checkState(n.canCollapseUnannotatedChildNames(), n);
      checkArgument(NodeUtil.isStatementBlock(parent), parent);
      checkNotNull(addAfter);
      if (n.props == null) {
        return;
      }
      for (Name p : n.props) {
        if (!p.needsToBeStubbed()) {
          continue;
        }

        String propAlias = appendPropForAlias(alias, p.getBaseName());
        Node nameNode = IR.name(propAlias);
        Node newVar = IR.var(nameNode).srcrefTreeIfMissing(addAfter);
        newVar.insertAfter(addAfter);

        // Determine if this is a constant var by checking the first
        // reference to it. Don't check the declaration, as it might be null.
        Node constPropNode = p.getFirstRef().getNode();
        nameNode.putBooleanProp(
            Node.IS_CONSTANT_NAME, constPropNode.getBooleanProp(Node.IS_CONSTANT_NAME));

        compiler.reportChangeToEnclosingScope(newVar);
        addAfter = newVar;
      }
    }

    private String appendPropForAlias(String root, String prop) {
      if (prop.indexOf('$') != -1) {
        // Encode '$' in a property as '$0'. Because '0' cannot be the
        // start of an identifier, this will never conflict with our
        // encoding from '.' -> '$'.
        prop = prop.replace("$", "$0");
      }
      String result = root + '$' + prop;
      int id = 1;
      while (nameMap.containsKey(result)) {
        result = root + '$' + prop + '$' + id;
        id++;
      }
      return result;
    }

    /** Find all the module namespace objects which are referenced by a dynamic import */
    class FindDynamicallyImportedModules extends AbstractPostOrderCallback {
      private final boolean processCommonJSModules;
      private final ResolutionMode moduleResolutionMode;

      FindDynamicallyImportedModules(
          boolean processCommonJSModules, ResolutionMode resolutionMode) {
        this.processCommonJSModules = processCommonJSModules;
        this.moduleResolutionMode = resolutionMode;
      }

      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        // After rewriting, CommonJS dynamic imports are of the form
        // __webpack_require__.e(2).then(function() { return module$mod1.default; })
        //
        // Mark the base module name as being dynamically imported.
        if (processCommonJSModules
            && n.isGetProp()
            && n.isQualifiedName()
            && n.getParent() != null
            && n.getParent().isReturn()
            && n.getGrandparent().isBlock()
            && n.getGrandparent().hasOneChild()
            && n.getGrandparent().getParent().isFunction()) {
          Node potentialCallback = NodeUtil.getEnclosingFunction(n);
          if (potentialCallback != null
              && ProcessCommonJSModules.isCommonJsDynamicImportCallback(
                  NodeUtil.getEnclosingFunction(potentialCallback), moduleResolutionMode)) {
            dynamicallyImportedModules.add(NodeUtil.getRootOfQualifiedName(n.getQualifiedName()));
          }
        } else if (ConvertChunksToESModules.isDynamicImportCallback(n)) {
          Node moduleNamespace =
              ConvertChunksToESModules.getDynamicImportCallbackModuleNamespace(compiler, n);
          if (moduleNamespace != null) {
            dynamicallyImportedModules.add(moduleNamespace.getQualifiedName());
          }
        }
      }
    }
  }

  static boolean isSafeNamespaceReinit(Ref ref) {
    // allow "a = a || {}" or "var a = a || {}" or "var a;"
    Node valParent = getValueParent(ref);
    Node val = valParent.getLastChild();
    if (val != null && val.isOr()) {
      Node maybeName = val.getFirstChild();
      if (ref.getNode().matchesQualifiedName(maybeName)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Gets the parent node of the value for any assignment to a Name. For example, in the assignment
   * {@code var x = 3;} the parent would be the NAME node.
   */
  private static Node getValueParent(Ref ref) {
    // there are four types of declarations: VARs, LETs, CONSTs, and ASSIGNs
    Node n = ref.getNode().getParent();
    return (n != null && NodeUtil.isNameDeclaration(n)) ? ref.getNode() : ref.getNode().getParent();
  }

  /**
   * Inline constant aliases
   *
   * <p>This pass was originally necessary because typechecking did not handle type aliases well.
   * Now typechecking understands type aliases. In theory, this pass can be deleted, but in practice
   * this pass affects some check passes that run post-typechecking.
   *
   * <p>This alias inliner is not very aggressive. It will only inline explicitly const aliases but
   * not effectively const ones (for example ones that are only ever assigned a value once). This is
   * done to be conservative since it's not a good idea to be making dramatic AST changes during
   * checks (or really, any AST changes at all). There is a more aggressive alias inliner that runs
   * at the start of optimization.
   *
   * <p>TODO(b/124915436): Delete this pass.
   */
  final class InlineAliases implements CompilerPass {

    private final Map<String, String> aliases = new LinkedHashMap<>();
    private GlobalNamespace namespace;
    private final AstFactory astFactory;

    InlineAliases() {
      this.astFactory = compiler.createAstFactory();
    }

    @Override
    public void process(Node externs, Node root) {
      namespace = new GlobalNamespace(compiler, externs, root);
      NodeTraversal.traverseRoots(compiler, new AliasesCollector(), externs, root);
      NodeTraversal.traverseRoots(compiler, new AliasesInliner(), externs, root);
    }

    private class AliasesCollector extends ExternsSkippingCallback {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        switch (n.getToken()) {
          case VAR:
          case CONST:
          case LET:
            if (n.hasOneChild() && t.inGlobalScope()) {
              visitAliasDefinition(n.getFirstChild(), NodeUtil.getBestJSDocInfo(n.getFirstChild()));
            }
            break;
          case ASSIGN:
            if (parent != null && parent.isExprResult() && t.inGlobalScope()) {
              visitAliasDefinition(n.getFirstChild(), n.getJSDocInfo());
            }
            break;
          default:
            break;
        }
      }

      /**
       * Maybe record that given lvalue is an alias of the qualified name on its rhs. Note that
       * since we are doing a post-order traversal, any previous aliases contained in the rhs will
       * have already been substituted by the time we record the new alias.
       */
      private void visitAliasDefinition(Node lhs, JSDocInfo info) {
        if (isDeclaredConst(lhs, info)
            && (info == null || !info.hasTypeInformation())
            && lhs.isQualifiedName()) {
          Node rhs = NodeUtil.getRValueOfLValue(lhs);
          if (rhs != null && rhs.isQualifiedName()) {
            Name lhsName = namespace.getOwnSlot(lhs.getQualifiedName());
            Name rhsName = namespace.getOwnSlot(rhs.getQualifiedName());
            if (lhsName != null
                && lhsName.calculateInlinability().shouldInlineUsages()
                && rhsName != null
                && rhsName.calculateInlinability().shouldInlineUsages()) {
              aliases.put(lhs.getQualifiedName(), rhs.getQualifiedName());
            }
          }
        }
      }

      private boolean isDeclaredConst(Node lhs, JSDocInfo info) {
        if (info != null && info.hasConstAnnotation()) {
          return true;
        }
        return lhs.getParent().isConst();
      }
    }

    private class AliasesInliner extends ExternsSkippingCallback {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        switch (n.getToken()) {
          case NAME:
          case GETPROP:
            if (n.isQualifiedName() && aliases.containsKey(n.getQualifiedName())) {
              if (isLeftmostNameLocal(t, n)) {
                // The alias is shadowed by a local variable. Don't rewrite.
                return;
              }
              if (NodeUtil.isNameDeclOrSimpleAssignLhs(n, parent)) {
                // The node defines an alias. Don't rewrite.
                return;
              }

              Node newNode =
                  astFactory.createQName(namespace, resolveAlias(n.getQualifiedName(), n));
              if (isLeftmostNameLocal(t, newNode)) {
                // The aliased name is shadowed by a local variable. Don't rewrite.
                return;
              }

              // If n is get_prop like "obj.foo" then newNode should use only location of foo, not
              // obj.foo.
              newNode.srcrefTree(n);
              // Similarly if n is get_prop like "obj.foo" we should index only foo. obj should not
              // be indexed as it's invisible to users.
              if (newNode.isGetProp()) {
                newNode.getFirstChild().makeNonIndexableRecursive();
              }
              n.replaceWith(newNode);
              t.reportCodeChange();
            }
            break;
          default:
            break;
        }
      }

      private boolean isLeftmostNameLocal(NodeTraversal t, Node n) {
        checkState(n.isQualifiedName());
        String leftmostName = NodeUtil.getRootOfQualifiedName(n).getString();
        Var v = t.getScope().getVar(leftmostName);
        return v != null && v.isLocal();
      }

      /**
       * Use the alias table to look up the resolved name of the given alias. If the result is also
       * an alias repeat until the real name is resolved.
       */
      private String resolveAlias(String name, Node n) {
        Set<String> aliasPath = new LinkedHashSet<>();
        while (aliases.containsKey(name)) {
          if (!aliasPath.add(name)) {
            compiler.report(JSError.make(n, ALIAS_CYCLE, aliasPath.toString(), name));

            // Cut the cycle so that it doesn't get reported more than once.
            aliases.remove(name);
            break;
          }

          name = aliases.get(name);
        }
        return name;
      }
    }
  }
}
