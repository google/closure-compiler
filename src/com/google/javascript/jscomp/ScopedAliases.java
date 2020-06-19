/*
 * Copyright 2010 The Closure Compiler Authors.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import com.google.javascript.jscomp.CompilerOptions.AliasTransformation;
import com.google.javascript.jscomp.CompilerOptions.AliasTransformationHandler;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.modules.ModuleMetadataMap;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SourcePosition;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Process aliases in goog.scope blocks.
 *
 * <pre>
 * goog.scope(function() {
 *   var dom = goog.dom;
 *   var DIV = dom.TagName.DIV;
 *
 *   dom.createElement(DIV);
 * });
 * </pre>
 *
 * should become
 *
 * <pre>
 * goog.dom.createElement(goog.dom.TagName.DIV);
 * </pre>
 *
 * The advantage of using goog.scope is that the compiler will *guarantee* the anonymous function
 * will be inlined, even if it can't prove that it's semantically correct to do so. For example,
 * consider this case:
 *
 * <pre>
 * goog.scope(function() {
 *   goog.getBar = function () { return alias; };
 *   ...
 *   var alias = foo.bar;
 * })
 * </pre>
 *
 * <p>In theory, the compiler can't inline 'alias' unless it can prove that goog.getBar is called
 * only after 'alias' is defined. In practice, the compiler will inline 'alias' anyway, at the risk
 * of 'fixing' bad code.
 */
class ScopedAliases implements HotSwapCompilerPass {
  /** Name used to denote an scoped function block used for aliasing. */
  static final String SCOPING_METHOD_NAME = "goog.scope";

  private final AbstractCompiler compiler;
  private final PreprocessorSymbolTable preprocessorSymbolTable;
  private final AliasTransformationHandler transformationHandler;

  // Errors
  static final DiagnosticType GOOG_SCOPE_MUST_BE_ALONE = DiagnosticType.error(
      "JSC_GOOG_SCOPE_MUST_BE_ALONE",
      "The call to goog.scope must be alone in a single statement.");

  static final DiagnosticType GOOG_SCOPE_MUST_BE_IN_GLOBAL_SCOPE = DiagnosticType.error(
      "JSC_GOOG_SCOPE_MUST_BE_IN_GLOBAL_SCOPE",
      "The call to goog.scope must be in the global scope.");

  static final DiagnosticType GOOG_SCOPE_HAS_BAD_PARAMETERS =
      DiagnosticType.error(
          "JSC_GOOG_SCOPE_HAS_BAD_PARAMETERS",
          "The call to goog.scope must take only a single parameter.  It must"
              + " be an anonymous function that itself takes no parameters.");

  static final DiagnosticType GOOG_SCOPE_REFERENCES_THIS = DiagnosticType.error(
      "JSC_GOOG_SCOPE_REFERENCES_THIS",
      "The body of a goog.scope function cannot reference 'this'.");

  static final DiagnosticType GOOG_SCOPE_USES_RETURN = DiagnosticType.error(
      "JSC_GOOG_SCOPE_USES_RETURN",
      "The body of a goog.scope function cannot use 'return'.");

  static final DiagnosticType GOOG_SCOPE_USES_THROW = DiagnosticType.error(
      "JSC_GOOG_SCOPE_USES_THROW",
      "The body of a goog.scope function cannot use 'throw'.");

  static final DiagnosticType GOOG_SCOPE_ALIAS_REDEFINED = DiagnosticType.error(
      "JSC_GOOG_SCOPE_ALIAS_REDEFINED",
      "The alias {0} is assigned a value more than once.");

  static final DiagnosticType GOOG_SCOPE_ALIAS_CYCLE = DiagnosticType.error(
      "JSC_GOOG_SCOPE_ALIAS_CYCLE",
      "The aliases {0} has a cycle.");

  static final DiagnosticType GOOG_SCOPE_NON_ALIAS_LOCAL = DiagnosticType.error(
      "JSC_GOOG_SCOPE_NON_ALIAS_LOCAL",
      "The local variable {0} is in a goog.scope and is not an alias.");

  static final DiagnosticType GOOG_SCOPE_INVALID_VARIABLE =
      DiagnosticType.error(
          "JSC_GOOG_SCOPE_INVALID_VARIABLE", "The variable {0} cannot be declared in this scope");

  private final Multiset<String> scopedAliasNames = HashMultiset.create();
  private final Set<String> closureNamespaces;
  private final InvalidModuleGetHandling invalidModuleGetHandling;

  /** What to do with goog.module.get calls importing an inexistent Closure namespace */
  enum InvalidModuleGetHandling {
    PRESERVE,
    DELETE;

    boolean shouldDelete() {
      return this.equals(DELETE);
    }
  }

  /** @deprecated use the builder instead of this constructor */
  @Deprecated
  ScopedAliases(
      AbstractCompiler compiler,
      @Nullable PreprocessorSymbolTable preprocessorSymbolTable,
      AliasTransformationHandler transformationHandler) {
    this(
        compiler,
        preprocessorSymbolTable,
        transformationHandler,
        ImmutableSet.of(),
        InvalidModuleGetHandling.PRESERVE);
  }

  private ScopedAliases(
      AbstractCompiler compiler,
      @Nullable PreprocessorSymbolTable preprocessorSymbolTable,
      AliasTransformationHandler transformationHandler,
      Set<String> closureNamespaces,
      InvalidModuleGetHandling invalidModuleGetHandling) {
    this.compiler = compiler;
    this.preprocessorSymbolTable = preprocessorSymbolTable;
    this.transformationHandler = transformationHandler;
    this.closureNamespaces = closureNamespaces;
    this.invalidModuleGetHandling = invalidModuleGetHandling;
  }

  static Builder builder(AbstractCompiler compiler) {
    return new Builder(compiler);
  }

  static class Builder {

    private final AbstractCompiler compiler;
    @Nullable private PreprocessorSymbolTable preprocessorSymbolTable = null;
    private AliasTransformationHandler transformationHandler =
        CompilerOptions.NULL_ALIAS_TRANSFORMATION_HANDLER;
    private ModuleMetadataMap moduleMetadataMap = null;
    private InvalidModuleGetHandling invalidModuleGetHandling = InvalidModuleGetHandling.PRESERVE;

    private Builder(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    Builder setPreprocessorSymbolTable(@Nullable PreprocessorSymbolTable preprocessorSymbolTable) {
      this.preprocessorSymbolTable = preprocessorSymbolTable;
      return this;
    }

    Builder setModuleMetadataMap(ModuleMetadataMap moduleMetadataMap) {
      this.moduleMetadataMap = moduleMetadataMap;
      return this;
    }

    /**
     * Configures whether to delete or preserve invalid goog.module get calls that are top-level
     * aliases in a goog.scope.
     */
    Builder setInvalidModuleGetHandling(InvalidModuleGetHandling invalidModuleGetHandling) {
      this.invalidModuleGetHandling = invalidModuleGetHandling;
      return this;
    }

    Builder setAliasTransformationHandler(AliasTransformationHandler aliasTransformationHandler) {
      this.transformationHandler = aliasTransformationHandler;
      return this;
    }

    ScopedAliases build() {
      return new ScopedAliases(
          compiler,
          preprocessorSymbolTable,
          transformationHandler,
          moduleMetadataMap == null
              ? ImmutableSet.of()
              : moduleMetadataMap.getModulesByGoogNamespace().keySet(),
          invalidModuleGetHandling);
    }
  }

  @Override
  public void process(Node externs, Node root) {
    hotSwapScript(root, null);
  }

  @Override
  public void hotSwapScript(Node root, Node originalRoot) {
    Traversal traversal = new Traversal();
    NodeTraversal.traverse(compiler, root, traversal);

    if (!traversal.hasErrors()) {
      // Apply the aliases.
      List<AliasUsage> aliasWorkQueue =
           new ArrayList<>(traversal.getAliasUsages());
      while (!aliasWorkQueue.isEmpty()) {
        List<AliasUsage> newQueue = new ArrayList<>();
        for (AliasUsage aliasUsage : aliasWorkQueue) {
          if (aliasUsage.referencesOtherAlias(traversal.deletedAliasVars)) {
            newQueue.add(aliasUsage);
          } else {
            aliasUsage.applyAlias(compiler);
          }
        }

        // Prevent an infinite loop.
        if (newQueue.size() == aliasWorkQueue.size()) {
          Var cycleVar = newQueue.get(0).aliasVar;
          compiler.report(JSError.make(
              cycleVar.getNode(), GOOG_SCOPE_ALIAS_CYCLE, cycleVar.getName()));
          break;
        } else {
          aliasWorkQueue = newQueue;
        }
      }

      // Remove the alias definitions.
      for (Node aliasDefinition : traversal.getAliasDefinitionsToDelete()) {
        compiler.reportChangeToEnclosingScope(aliasDefinition);
        if (NodeUtil.isNameDeclaration(aliasDefinition.getParent())
            && aliasDefinition.getParent().hasOneChild()) {
          aliasDefinition.getParent().detach();
        } else {
          aliasDefinition.detach();
        }
      }

      // Collapse the scopes.
      for (Node scopeCall : traversal.getScopeCalls()) {
        Node expressionWithScopeCall = scopeCall.getParent();
        Node scopeClosureBlock = scopeCall.getLastChild().getLastChild();
        scopeClosureBlock.detach();
        expressionWithScopeCall.replaceWith(scopeClosureBlock);
        NodeUtil.markFunctionsDeleted(expressionWithScopeCall, compiler);
        compiler.reportChangeToEnclosingScope(scopeClosureBlock);
        NodeUtil.tryMergeBlock(scopeClosureBlock, false);
      }
    }
  }

  private abstract static class AliasUsage {
    final Var aliasVar;
    final Node aliasReference;

    AliasUsage(Var aliasVar, Node aliasReference) {
      this.aliasVar = aliasVar;
      this.aliasReference = aliasReference;
    }

    /** Checks to see if this references another alias. */
    public boolean referencesOtherAlias(Set<Var> deletedAliasVars) {
      Node aliasDefinition = aliasVar.getInitialValue();
      String qname = getAliasedNamespace(aliasDefinition);
      int dotIndex = qname.indexOf('.');
      String rootName = dotIndex == -1 ? qname : qname.substring(0, dotIndex);
      Var otherAliasVar = aliasVar.getScope().getOwnSlot(rootName);
      return otherAliasVar != null && !deletedAliasVars.contains(otherAliasVar);
    }

    public abstract void applyAlias(AbstractCompiler compiler);
  }

  private static boolean isValidAliasRhs(Node rhs) {
    switch (rhs.getToken()) {
      case GETPROP:
        return isValidAliasRhs(rhs.getFirstChild());
      case NAME:
        return true;
      case CALL:
        return NodeUtil.isCallTo(rhs, "goog.module.get");
      default:
        return false;
    }
  }

  private static boolean isAliasDefinition(Node nameNode) {
    if (!nameNode.hasChildren()) {
      return false;
    }
    Node rhs = nameNode.getLastChild();
    return isValidAliasRhs(rhs);
  }

  private static String getAliasedNamespace(Node rhs) {
    switch (rhs.getToken()) {
      case GETPROP:
        return getAliasedNamespace(rhs.getFirstChild()) + '.' + rhs.getLastChild().getString();
      case NAME:
        return rhs.getString();
      case CALL:
        checkState(NodeUtil.isCallTo(rhs, "goog.module.get"), rhs);
        checkState(rhs.hasTwoChildren(), rhs);
        return rhs.getLastChild().getString();
      default:
        throw new RuntimeException("Invalid alias RHS:" + rhs);
    }
  }

  private static class AliasedNode extends AliasUsage {
    AliasedNode(Var aliasVar, Node aliasReference) {
      super(aliasVar, aliasReference);
    }

    @Override
    public void applyAlias(AbstractCompiler compiler) {
      Node aliasDefinition = aliasVar.getInitialValue();
      Node replacement = aliasDefinition.cloneTree();
      replacement.useSourceInfoFromForTree(aliasReference);
      // Given alias "var Bar = foo.Bar;" here we replace a usage of Bar with foo.Bar.
      // foo is generated and never visible to user. Because of that we should mark all new nodes as
      // non-indexable leaving only Bar indexable.
      // Given that replacement is GETPROP node, prefix is first child. It's also possible that
      // replacement is single-part namespace. Like goog.provide('Foo') in that case replacement
      // won't have children.
      if (replacement.hasChildren()) {
        replacement.getFirstChild().makeNonIndexableRecursive();
      }
      if (aliasReference.isStringKey()) {
        checkState(!aliasReference.hasChildren());
        aliasReference.addChildToFront(replacement);
      } else {
        aliasReference.replaceWith(replacement);
      }
      compiler.reportChangeToEnclosingScope(replacement);
    }
  }

  private static class AliasedTypeNode extends AliasUsage {
    AliasedTypeNode(Var aliasVar, Node aliasReference) {
      super(aliasVar, aliasReference);
    }

    @Override
    public void applyAlias(AbstractCompiler compiler) {
      Node aliasDefinition = aliasVar.getInitialValue();
      String aliasName = aliasVar.getName();
      String typeName = aliasReference.getString();
      if (typeName.startsWith("$jscomp.scope.")) {
        // Already visited.
        return;
      }
      String aliasExpanded = checkNotNull(getAliasedNamespace(aliasDefinition));
      Preconditions.checkState(typeName.startsWith(aliasName),
          "%s must start with %s", typeName, aliasName);
      String replacement =
          aliasExpanded + typeName.substring(aliasName.length());
      aliasReference.setString(replacement);
    }
  }

  private class Traversal extends AbstractPostOrderCallback
      implements NodeTraversal.ScopedCallback {
    // The job of this class is to collect these three data sets.

    private final List<Node> aliasDefinitionsToDelete = new ArrayList<>();

    private final List<Node> scopeCalls = new ArrayList<>();

    private final List<AliasUsage> aliasUsages = new ArrayList<>();

    // This map is temporary and cleared for each scope.
    private final Map<String, Var> aliases = new HashMap<>();

    // Also temporary and cleared for each scope.
    private final Set<Node> injectedDecls = new HashSet<>();

    // Persists across scopes.
    private final Set<Var> deletedAliasVars = new HashSet<>();

    // Suppose you create an alias.
    // var x = goog.x;
    // As a side-effect, this means you can shadow the namespace 'goog'
    // in inner scopes. When we inline the namespaces, we have to rename
    // these shadows.
    //
    // Fortunately, we already have a name uniquifier that runs during tree
    // normalization (before optimizations). We run it here on a limited
    // set of variables, but only as a last resort (because this will screw
    // up warning messages downstream).
    private final Set<String> forbiddenLocals = new HashSet<>(
        ImmutableSet.of("$jscomp"));

    private boolean hasNamespaceShadows = false;

    private boolean hasErrors = false;

    private AliasTransformation transformation = null;

    // The body of the function that is passed to goog.scope.
    // Set when the traversal enters the body, and set back to null when it exits.
    private Node scopeFunctionBody = null;

    Collection<Node> getAliasDefinitionsToDelete() {
      return aliasDefinitionsToDelete;
    }

    private List<AliasUsage> getAliasUsages() {
      return aliasUsages;
    }

    List<Node> getScopeCalls() {
      return scopeCalls;
    }

    boolean hasErrors() {
      return hasErrors;
    }

    /**
     * Returns true if this NodeTraversal is currently within a goog.scope function body
     */
    private boolean inGoogScopeBody() {
      return scopeFunctionBody != null;
    }

    /**
     * Returns true if n is the goog.scope function body
     */
    private boolean isGoogScopeFunctionBody(Node n) {
      return inGoogScopeBody() && n == scopeFunctionBody;
    }

    private boolean isCallToScopeMethod(Node n) {
      return n.isCall() && n.getFirstChild().matchesQualifiedName(SCOPING_METHOD_NAME);
    }

    /**
     * @param scopeRoot the Node which is the root of the current scope
     * @return the goog.scope() CALL node containing the scopeRoot, or null if scopeRoot is not
     *     in a goog.scope() call.
     */
    private Node findScopeMethodCall(Node scopeRoot) {
      Node n = scopeRoot.getGrandparent();
      if (isCallToScopeMethod(n)) {
        return n;
      }
      return null;
    }

    @Override
    public void enterScope(NodeTraversal t) {
      if (t.inGlobalHoistScope()) {
        return;
      }
      Node scopeMethodCall = findScopeMethodCall(t.getScopeRoot());
      if (scopeMethodCall != null) {
        transformation = transformationHandler.logAliasTransformation(
            scopeMethodCall.getSourceFileName(), getSourceRegion(scopeMethodCall));
        findAliases(t.getScope());
        scopeFunctionBody = scopeMethodCall.getLastChild().getLastChild();
      }
    }

    @Override
    public void exitScope(NodeTraversal t) {
      if (isGoogScopeFunctionBody(t.getScopeRoot())) {
        scopeFunctionBody = null;
        renameNamespaceShadows(t);
        injectedDecls.clear();
        aliases.clear();
        forbiddenLocals.clear();
        transformation = null;
        hasNamespaceShadows = false;
      } else if (inGoogScopeBody()) {
        // Called on inner scopes within a goog.scope, including both block scopes and
        // function scopes.
        findNamespaceShadows(t);
        reportInvalidVariables(t);
      }
    }

    private void reportInvalidVariables(NodeTraversal t) {
      Node scopeRoot = t.getScopeRoot();
      Node enclosingFunctionBody = t.getEnclosingFunction().getLastChild();
      if (isGoogScopeFunctionBody(enclosingFunctionBody)
          && scopeRoot.isBlock()
          && !scopeRoot.getParent().isFunction()) {
        for (Var v : t.getScope().getVarIterable()) {
          Node parent = v.getNameNode().getParent();
          if (NodeUtil.isFunctionDeclaration(parent)) {
            // Disallow block-scoped function declarations that leak into the goog.scope
            // function body. Technically they shouldn't leak in ES6 but the browsers don't agree
            // on that yet.
            report(v.getNode(), GOOG_SCOPE_INVALID_VARIABLE, v.getName());
          }
        }
      }
    }

    private SourcePosition<AliasTransformation> getSourceRegion(Node n) {
      Node testNode = n;
      Node next = null;
      for (; next != null || testNode.isScript();) {
        next = testNode.getNext();
        testNode = testNode.getParent();
      }

      int endLine = next == null ? Integer.MAX_VALUE : next.getLineno();
      int endChar = next == null ? Integer.MAX_VALUE : next.getCharno();
      SourcePosition<AliasTransformation> pos =
          new SourcePosition<AliasTransformation>() {};
      pos.setPositionInformation(
          n.getLineno(), n.getCharno(), endLine, endChar);
      return pos;
    }

    private void report(Node n, DiagnosticType error, String... arguments) {
      compiler.report(JSError.make(n, error, arguments));
      hasErrors = true;
    }

    private void findAliases(Scope scope) {
      for (Var v : scope.getVarIterable()) {
        Node n = v.getNode();
        Node parent = n.getParent();
        // We use isBlock to avoid variables declared in loop headers.
        boolean isVar = NodeUtil.isNameDeclaration(parent) && parent.getParent().isBlock();
        boolean isFunctionDecl = NodeUtil.isFunctionDeclaration(parent);
        if (isVar && isAliasDefinition(n)) {
          recordAlias(v);
        } else if (v.isBleedingFunction()) {
          // Bleeding functions already get a BAD_PARAMETERS error, so just
          // do nothing.
        } else if (parent.isParamList()) {
          // Parameters of the scope function also get a BAD_PARAMETERS
          // error.
        } else if (isVar || isFunctionDecl || NodeUtil.isClassDeclaration(parent)) {
          boolean isHoisted = NodeUtil.isHoistedFunctionDeclaration(parent);
          Node grandparent = parent.getParent();
          Node value = v.getInitialValue();
          Node varNode = null;

          // Pull out inline type declaration if present.
          if (n.getJSDocInfo() != null) {
            JSDocInfoBuilder builder = JSDocInfoBuilder.maybeCopyFrom(parent.getJSDocInfo());
            if (isFunctionDecl) { // Fix inline return type.
              builder.recordReturnType(n.getJSDocInfo().getType());
            } else {
              builder.recordType(n.getJSDocInfo().getType());
            }
            parent.setJSDocInfo(builder.build());
            n.setJSDocInfo(null);
          }
          // Grab the docinfo before we do any AST manipulation.
          JSDocInfo varDocInfo = v.getJSDocInfo();

          String name = n.getString();
          int nameCount = scopedAliasNames.count(name);
          scopedAliasNames.add(name);
          String globalName =
              "$jscomp.scope." + name + (nameCount == 0 ? "" : ("$jscomp$" + nameCount));

          Node lastInjectedNode = compiler.ensureLibraryInjected("base", false);
          if (lastInjectedNode != null) {
            compiler.reportChangeToEnclosingScope(lastInjectedNode);
          }

          // First, we need to free up the function expression (EXPR)
          // to be used in another expression.
          if (isFunctionDecl || NodeUtil.isClassDeclaration(parent)) {
            // Replace "function NAME() { ... }" with "var NAME;".
            // Replace "class NAME { ... }" with "var NAME;".

            // We can't keep the local name on the function expression,
            // because IE is buggy and will leak the name into the global
            // scope. This is covered in more detail here:
            // http://wiki.ecmascript.org/lib/exe/fetch.php?id=resources:resources&cache=cache&media=resources:jscriptdeviationsfromes3.pdf
            //
            // This will only cause problems if this is a hoisted, recursive
            // function, and the programmer is using the hoisting.
            Node newName;
            if (isFunctionDecl) {
              newName = IR.name("");
            } else {
              newName = IR.empty();
            }
            newName.useSourceInfoFrom(n);
            value.replaceChild(n, newName);
            compiler.reportChangeToEnclosingScope(newName);

            varNode = IR.var(n).useSourceInfoFrom(n);
            grandparent.replaceChild(parent, varNode);
          } else {
            if (value != null) {
              // If this is a VAR, we can just detach the expression and
              // the tree will still be valid.
              value.detach();
            }
            varNode = parent;
          }

          // Add $jscomp.scope.name = EXPR;
          // Make sure we copy over all the jsdoc and debug info.
          if (value != null || varDocInfo != null) {
            Node newDecl = NodeUtil.newQNameDeclaration(
                compiler,
                globalName,
                value,
                varDocInfo)
                .useSourceInfoIfMissingFromForTree(n);
            newDecl.getFirstFirstChild().useSourceInfoFrom(n);
            newDecl.getFirstFirstChild().setOriginalName(name);

            if (isHoisted) {
              grandparent.addChildToFront(newDecl);
            } else {
              grandparent.addChildBefore(newDecl, varNode);
            }
            compiler.reportChangeToEnclosingScope(newDecl);
            injectedDecls.add(newDecl.getFirstChild());
          }

          // Rewrite "var name = EXPR;" to "var name = $jscomp.scope.name;"
          v.getNameNode().addChildToFront(
              NodeUtil.newQName(
                  compiler, globalName, n, name));

          recordAlias(v);
        } else {
          // Do not other kinds of local symbols, like catch params.
          report(n, GOOG_SCOPE_NON_ALIAS_LOCAL, n.getString());
        }
      }
    }

    private void recordAlias(Var aliasVar) {
      Node initialValue = aliasVar.getInitialValue();
      aliasDefinitionsToDelete.add(aliasVar.getNameNode());

      if (invalidModuleGetHandling.shouldDelete() && containsInvalidGoogModuleGet(initialValue)) {
        deletedAliasVars.add(aliasVar);
        return;
      }

      String name = aliasVar.getName();
      aliases.put(name, aliasVar);

      String qualifiedName = getAliasedNamespace(initialValue);
      transformation.addAlias(name, qualifiedName);

      int rootIndex = qualifiedName.indexOf('.');
      if (rootIndex != -1) {
        String qNameRoot = qualifiedName.substring(0, rootIndex);
        if (!aliases.containsKey(qNameRoot)) {
          forbiddenLocals.add(qNameRoot);
        }
      }
    }

    /** Returns whether the rhs contains any goog.module.get calls to inexistent namespaces */
    private boolean containsInvalidGoogModuleGet(Node expression) {
      switch (expression.getToken()) {
        case NAME:
          return false;
        case GETPROP:
          return containsInvalidGoogModuleGet(expression.getFirstChild());
        case CALL:
          String namespace = expression.getSecondChild().getString();
          return !closureNamespaces.contains(namespace);
        default:
          throw new IllegalStateException("Unrecognized alias rhs " + expression);
      }
    }

    /** Find out if there are any local shadows of namespaces. */
    private void findNamespaceShadows(NodeTraversal t) {
      if (hasNamespaceShadows) {
        return;
      }

      Scope scope = t.getScope();
      for (Var v : scope.getVarIterable()) {
        if (forbiddenLocals.contains(v.getName())) {
          hasNamespaceShadows = true;
          return;
        }
      }
    }

    /**
     * Rename any local shadows of namespaces.
     * This should be a very rare occurrence, so only do this traversal
     * if we know that we need it.
     */
    private void renameNamespaceShadows(NodeTraversal t) {
      checkState(NodeUtil.isFunctionBlock(t.getScopeRoot()), t.getScopeRoot());

      if (hasNamespaceShadows) {
        MakeDeclaredNamesUnique.Renamer renamer =
            new MakeDeclaredNamesUnique.TargettedRenamer(
                new MakeDeclaredNamesUnique.ContextualRenamer(), forbiddenLocals);
        for (String s : forbiddenLocals) {
          renamer.addDeclaredName(s, false);
        }
        MakeDeclaredNamesUnique uniquifier = new MakeDeclaredNamesUnique(renamer);
        NodeTraversal.traverseScopeRoots(
            compiler, null, ImmutableList.of(t.getScopeRoot()), uniquifier, true);
      }
    }

    private void renameBleedingFunctionName(NodeTraversal t, final Node fnName) {
      final String name = fnName.getString();
      final String suffix = compiler.getUniqueNameIdSupplier().get();
      Callback cb =
          new AbstractPostOrderCallback() {
            @Override
            public void visit(NodeTraversal t, Node n, Node parent) {
              if (n.isName()
                  && n.getString().equals(name)
                  && t.getScope().getVar(name).getNode() == fnName) {
                n.setString(name + "$jscomp$scopedAliases$" + suffix);
                compiler.reportChangeToEnclosingScope(n);
              }
            }
          };
      (new NodeTraversal(compiler, cb, t.getScopeCreator())).traverseAtScope(t.getScope());
      fnName.setString(name + "$jscomp$scopedAliases$" + suffix);
    }

    private void validateScopeCall(NodeTraversal t, Node n, Node parent) {
      if (preprocessorSymbolTable != null) {
        preprocessorSymbolTable.addReference(n.getFirstChild());
      }
      if (!parent.isExprResult()) {
        report(n, GOOG_SCOPE_MUST_BE_ALONE);
      }
      if (t.getEnclosingFunction() != null) {
        report(n, GOOG_SCOPE_MUST_BE_IN_GLOBAL_SCOPE);
      }
      if (!n.hasTwoChildren()) {
        // The goog.scope call should have exactly 1 parameter.  The first
        // child is the "goog.scope" and the second should be the parameter.
        report(n, GOOG_SCOPE_HAS_BAD_PARAMETERS);
      } else {
        Node anonymousFnNode = n.getSecondChild();
        if (!anonymousFnNode.isFunction()
            || NodeUtil.getName(anonymousFnNode) != null
            || NodeUtil.getFunctionParameters(anonymousFnNode).hasChildren()) {
          report(anonymousFnNode, GOOG_SCOPE_HAS_BAD_PARAMETERS);
        } else {
          scopeCalls.add(n);
        }
      }
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (isCallToScopeMethod(n)) {
        validateScopeCall(t, n, n.getParent());
      }

      if (!inGoogScopeBody()) {
        return;
      }

      Token type = n.getToken();
      Var aliasVar = null;
      if (type == Token.NAME) {
        String name = n.getString();
        Var lexicalVar = t.getScope().getVar(name);
        if (lexicalVar != null && lexicalVar.equals(aliases.get(name))) {
          aliasVar = lexicalVar;
          // For nodes that are referencing the aliased type, set the original name so it
          // can be accessed later in tools such as the CodePrinter or refactoring tools.
          if (compiler.getOptions().preservesDetailedSourceInfo() && n.isName()) {
            n.setOriginalName(name);
          }
        }
      }

      if (isGoogScopeFunctionBody(t.getEnclosingFunction().getLastChild())) {
        if (aliasVar != null && NodeUtil.isLValue(n)) {
          if (aliasVar.getNode() == n) {
            // Return early, to ensure that we don't record this as an alias usage.
            return;
          } else {
            report(n, GOOG_SCOPE_ALIAS_REDEFINED, n.getString());
          }
        }

        if (type == Token.RETURN) {
          report(n, GOOG_SCOPE_USES_RETURN);
        } else if (type == Token.THIS) {
          report(n, GOOG_SCOPE_REFERENCES_THIS);
        } else if (type == Token.THROW) {
          report(n, GOOG_SCOPE_USES_THROW);
        }
      }

      // If this is a bleeding function expression, like
      // var x = function y() { ... }
      // then old versions of IE declare "y" in the current scope. We don't
      // want the scope unboxing to add "y" to the global scope, so we
      // need to rename it.
      //
      // TODO(moz): Remove this once we stop supporting IE8.
      if (NodeUtil.isBleedingFunctionName(n)) {
        renameBleedingFunctionName(t, n);
      }

      // Check if this name points to an alias.
      if (aliasVar != null) {
        // Note, to support the transitive case, it's important we don't
        // clone aliasedNode here.  For example,
        // var g = goog; var d = g.dom; d.createElement('DIV');
        // The node in aliasedNode (which is "g") will be replaced in the
        // changes pass above with "goog".  If we cloned here, we'd end up
        // with <code>g.dom.createElement('DIV')</code>.
        aliasUsages.add(new AliasedNode(aliasVar, n));
      }

      // When we inject declarations, we duplicate jsdoc. Make sure
      // we only process that jsdoc once.
      JSDocInfo info = n.getJSDocInfo();
      if (info != null && !injectedDecls.contains(n)) {
        for (Node node : info.getTypeNodes()) {
          fixTypeNode(node);
        }
      }
    }

    private void fixTypeNode(Node typeNode) {
      if (typeNode.isString()) {
        String name = typeNode.getString();
        int endIndex = name.indexOf('.');
        if (endIndex == -1) {
          endIndex = name.length();
        }
        String baseName = name.substring(0, endIndex);
        Var aliasVar = aliases.get(baseName);
        if (aliasVar != null) {
          aliasUsages.add(new AliasedTypeNode(aliasVar, typeNode));
        }
        // For nodes that are referencing the aliased type, set the original name so it
        // can be accessed later in tools such as the CodePrinter or refactoring tools.
        if (compiler.getOptions().preservesDetailedSourceInfo()) {
          typeNode.setOriginalName(name);
        }
      }
      for (Node child = typeNode.getFirstChild(); child != null;
           child = child.getNext()) {
        fixTypeNode(child);
      }
    }
  }
}
