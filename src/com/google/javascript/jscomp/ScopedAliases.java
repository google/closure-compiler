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

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.CompilerOptions.AliasTransformation;
import com.google.javascript.jscomp.CompilerOptions.AliasTransformationHandler;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SourcePosition;
import com.google.javascript.rhino.Token;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Process aliases in goog.scope blocks.
 *
 * goog.scope(function() {
 *   var dom = goog.dom;
 *   var DIV = dom.TagName.DIV;
 *
 *   dom.createElement(DIV);
 * });
 *
 * should become
 *
 * goog.dom.createElement(goog.dom.TagName.DIV);
 *
 * The advantage of using goog.scope is that the compiler will *guarantee*
 * the anonymous function will be inlined, even if it can't prove
 * that it's semantically correct to do so. For example, consider this case:
 *
 * goog.scope(function() {
 *   goog.getBar = function () { return alias; };
 *   ...
 *   var alias = foo.bar;
 * })
 *
 * In theory, the compiler can't inline 'alias' unless it can prove that
 * goog.getBar is called only after 'alias' is defined.
 *
 * In practice, the compiler will inline 'alias' anyway, at the risk of
 * 'fixing' bad code.
 *
 * @author robbyw@google.com (Robby Walker)
 */
class ScopedAliases implements HotSwapCompilerPass {
  /** Name used to denote an scoped function block used for aliasing. */
  static final String SCOPING_METHOD_NAME = "goog.scope";

  private final AbstractCompiler compiler;
  private final PreprocessorSymbolTable preprocessorSymbolTable;
  private final AliasTransformationHandler transformationHandler;

  // Errors
  static final DiagnosticType GOOG_SCOPE_USED_IMPROPERLY = DiagnosticType.error(
      "JSC_GOOG_SCOPE_USED_IMPROPERLY",
      "The call to goog.scope must be alone in a single statement.");

  static final DiagnosticType GOOG_SCOPE_HAS_BAD_PARAMETERS =
      DiagnosticType.error(
          "JSC_GOOG_SCOPE_HAS_BAD_PARAMETERS",
          "The call to goog.scope must take only a single parameter.  It must" +
              " be an anonymous function that itself takes no parameters.");

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

  private Multiset<String> scopedAliasNames = HashMultiset.create();

  ScopedAliases(AbstractCompiler compiler,
      @Nullable PreprocessorSymbolTable preprocessorSymbolTable,
      AliasTransformationHandler transformationHandler) {
    this.compiler = compiler;
    this.preprocessorSymbolTable = preprocessorSymbolTable;
    this.transformationHandler = transformationHandler;
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
          Lists.newArrayList(traversal.getAliasUsages());
      while (!aliasWorkQueue.isEmpty()) {
        List<AliasUsage> newQueue = Lists.newArrayList();
        for (AliasUsage aliasUsage : aliasWorkQueue) {
          if (aliasUsage.referencesOtherAlias()) {
            newQueue.add(aliasUsage);
          } else {
            aliasUsage.applyAlias();
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
      for (Node aliasDefinition : traversal.getAliasDefinitionsInOrder()) {
        if (aliasDefinition.getParent().isVar() &&
            aliasDefinition.getParent().hasOneChild()) {
          aliasDefinition.getParent().detachFromParent();
        } else {
          aliasDefinition.detachFromParent();
        }
      }

      // Collapse the scopes.
      for (Node scopeCall : traversal.getScopeCalls()) {
        Node expressionWithScopeCall = scopeCall.getParent();
        Node scopeClosureBlock = scopeCall.getLastChild().getLastChild();
        scopeClosureBlock.detachFromParent();
        expressionWithScopeCall.getParent().replaceChild(
            expressionWithScopeCall,
            scopeClosureBlock);
        NodeUtil.tryMergeBlock(scopeClosureBlock);
      }

      if (traversal.getAliasUsages().size() > 0 ||
          traversal.getAliasDefinitionsInOrder().size() > 0 ||
          traversal.getScopeCalls().size() > 0) {
        compiler.reportCodeChange();
      }
    }
  }

  private abstract class AliasUsage {
    final Var aliasVar;
    final Node aliasReference;

    AliasUsage(Var aliasVar, Node aliasReference) {
      this.aliasVar = aliasVar;
      this.aliasReference = aliasReference;
    }

    /** Checks to see if this references another alias. */
    public boolean referencesOtherAlias() {
      Node aliasDefinition = aliasVar.getInitialValue();
      Node root = NodeUtil.getRootOfQualifiedName(aliasDefinition);
      Var otherAliasVar = aliasVar.getScope().getOwnSlot(root.getString());
      return otherAliasVar != null;
    }

    public abstract void applyAlias();
  }

  private class AliasedNode extends AliasUsage {
    AliasedNode(Var aliasVar, Node aliasReference) {
      super(aliasVar, aliasReference);
    }

    @Override
    public void applyAlias() {
      Node aliasDefinition = aliasVar.getInitialValue();
      aliasReference.getParent().replaceChild(
          aliasReference, aliasDefinition.cloneTree());
    }
  }

  private class AliasedTypeNode extends AliasUsage {
    AliasedTypeNode(Var aliasVar, Node aliasReference) {
      super(aliasVar, aliasReference);
    }

    @Override
    public void applyAlias() {
      Node aliasDefinition = aliasVar.getInitialValue();
      String aliasName = aliasVar.getName();
      String typeName = aliasReference.getString();
      String aliasExpanded =
          Preconditions.checkNotNull(aliasDefinition.getQualifiedName());
      Preconditions.checkState(typeName.startsWith(aliasName));
      aliasReference.setString(typeName.replaceFirst(aliasName, aliasExpanded));
    }
  }


  private class Traversal implements NodeTraversal.ScopedCallback {
    // The job of this class is to collect these three data sets.

    // The order of this list determines the order that aliases are applied.
    private final List<Node> aliasDefinitionsInOrder = Lists.newArrayList();

    private final List<Node> scopeCalls = Lists.newArrayList();

    private final List<AliasUsage> aliasUsages = Lists.newArrayList();

    // This map is temporary and cleared for each scope.
    private final Map<String, Var> aliases = Maps.newHashMap();

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
    private final Set<String> forbiddenLocals = Sets.newHashSet("$jscomp");
    private boolean hasNamespaceShadows = false;

    private boolean hasErrors = false;

    private AliasTransformation transformation = null;

    Collection<Node> getAliasDefinitionsInOrder() {
      return aliasDefinitionsInOrder;
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

    private boolean isCallToScopeMethod(Node n) {
      return n.isCall() &&
          SCOPING_METHOD_NAME.equals(n.getFirstChild().getQualifiedName());
    }

    @Override
    public void enterScope(NodeTraversal t) {
      Node n = t.getCurrentNode().getParent();
      if (n != null && isCallToScopeMethod(n)) {
        transformation = transformationHandler.logAliasTransformation(
            n.getSourceFileName(), getSourceRegion(n));
        findAliases(t);
      }
    }

    @Override
    public void exitScope(NodeTraversal t) {
      if (t.getScopeDepth() > 2) {
        findNamespaceShadows(t);
      }

      if (t.getScopeDepth() == 2) {
        renameNamespaceShadows(t);
        aliases.clear();
        forbiddenLocals.clear();
        transformation = null;
        hasNamespaceShadows = false;
      }
    }

    @Override
    public final boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      if (n.isFunction() && t.inGlobalScope()) {
        // Do not traverse in to functions except for goog.scope functions.
        if (parent == null || !isCallToScopeMethod(parent)) {
          return false;
        }
      }
      return true;
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

    private void report(NodeTraversal t, Node n, DiagnosticType error,
        String... arguments) {
      compiler.report(t.makeError(n, error, arguments));
      hasErrors = true;
    }

    private void findAliases(NodeTraversal t) {
      Scope scope = t.getScope();
      for (Var v : scope.getVarIterable()) {
        Node n = v.getNode();
        Node parent = n.getParent();
        boolean isVar = parent.isVar();
        if (isVar && n.getFirstChild() != null && n.getFirstChild().isQualifiedName()) {
          recordAlias(v);
        } else if (v.isBleedingFunction()) {
          // Bleeding functions already get a BAD_PARAMETERS error, so just
          // do nothing.
        } else if (parent.getType() == Token.LP) {
          // Parameters of the scope function also get a BAD_PARAMETERS
          // error.
        } else if (isVar) {
          Node value = n.hasChildren() ?
              v.getInitialValue().detachFromParent() :
              null;
          String name = n.getString();
          int nameCount = scopedAliasNames.count(name);
          scopedAliasNames.add(name);
          String globalName =
              "$jscomp.scope." + name + (nameCount == 0 ? "" : ("$" + nameCount));

          compiler.ensureLibraryInjected("base");

          // Add $jscomp.scope.name = EXPR;
          // Make sure we copy over all the jsdoc and debug info.
          if (value != null || v.getJSDocInfo() != null) {
            Node newDecl = NodeUtil.newQualifiedNameNodeDeclaration(
                compiler.getCodingConvention(),
                globalName,
                value,
                v.getJSDocInfo())
                .useSourceInfoIfMissingFromForTree(n);
            NodeUtil.setDebugInformation(
                newDecl.getFirstChild().getFirstChild(), n, name);
            parent.getParent().addChildBefore(newDecl, parent);
          }

          // Rewrite "var name = EXPR;" to "var name = $jscomp.scope.name;"
          v.getNameNode().addChildToFront(
              NodeUtil.newQualifiedNameNode(
                  compiler.getCodingConvention(), globalName, n, name));

          recordAlias(v);
        } else {
          // Do not allow hoisted functions or other kinds of local symbols.
          report(t, n, GOOG_SCOPE_NON_ALIAS_LOCAL, n.getString());
        }
      }
    }

    private void recordAlias(Var aliasVar) {
      String name = aliasVar.getName();
      aliases.put(name, aliasVar);

      String qualifiedName =
        aliasVar.getInitialValue().getQualifiedName();
      transformation.addAlias(name, qualifiedName);

      int rootIndex = qualifiedName.indexOf(".");
      if (rootIndex != -1) {
        String qNameRoot = qualifiedName.substring(0, rootIndex);
        if (!aliases.containsKey(qNameRoot)) {
          forbiddenLocals.add(qNameRoot);
        }
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
      if (hasNamespaceShadows) {
        MakeDeclaredNamesUnique.Renamer renamer =
            new MakeDeclaredNamesUnique.WhitelistedRenamer(
                new MakeDeclaredNamesUnique.ContextualRenamer(),
                forbiddenLocals);
        for (String s : forbiddenLocals) {
          renamer.addDeclaredName(s);
        }
        MakeDeclaredNamesUnique uniquifier =
            new MakeDeclaredNamesUnique(renamer);
        NodeTraversal.traverse(compiler, t.getScopeRoot(), uniquifier);
      }
    }

    private void validateScopeCall(NodeTraversal t, Node n, Node parent) {
      if (preprocessorSymbolTable != null) {
        preprocessorSymbolTable.addReference(n.getFirstChild());
      }
      if (!parent.isExprResult()) {
        report(t, n, GOOG_SCOPE_USED_IMPROPERLY);
      }
      if (n.getChildCount() != 2) {
        // The goog.scope call should have exactly 1 parameter.  The first
        // child is the "goog.scope" and the second should be the parameter.
        report(t, n, GOOG_SCOPE_HAS_BAD_PARAMETERS);
      } else {
        Node anonymousFnNode = n.getChildAtIndex(1);
        if (!anonymousFnNode.isFunction() ||
            NodeUtil.getFunctionName(anonymousFnNode) != null ||
            NodeUtil.getFunctionParameters(anonymousFnNode).hasChildren()) {
          report(t, anonymousFnNode, GOOG_SCOPE_HAS_BAD_PARAMETERS);
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

      if (t.getScopeDepth() < 2) {
        return;
      }

      int type = n.getType();
      Var aliasVar = null;
      if (type == Token.NAME) {
        String name = n.getString();
        Var lexicalVar = t.getScope().getVar(n.getString());
        if (lexicalVar != null && lexicalVar == aliases.get(name)) {
          aliasVar = lexicalVar;
        }
      }

      // Validate the top-level of the goog.scope block.
      if (t.getScopeDepth() == 2) {
        if (aliasVar != null && NodeUtil.isLValue(n)) {
          if (aliasVar.getNode() == n) {
            aliasDefinitionsInOrder.add(n);

            // Return early, to ensure that we don't record a definition
            // twice.
            return;
          } else {
            report(t, n, GOOG_SCOPE_ALIAS_REDEFINED, n.getString());
          }
        }

        if (type == Token.RETURN) {
          report(t, n, GOOG_SCOPE_USES_RETURN);
        } else if (type == Token.THIS) {
          report(t, n, GOOG_SCOPE_REFERENCES_THIS);
        } else if (type == Token.THROW) {
          report(t, n, GOOG_SCOPE_USES_THROW);
        }
      }

      // Validate all descendent scopes of the goog.scope block.
      if (t.getScopeDepth() >= 2) {
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

        JSDocInfo info = n.getJSDocInfo();
        if (info != null) {
          for (Node node : info.getTypeNodes()) {
            fixTypeNode(node);
          }
        }

        // TODO(robbyw): Error for goog.scope not at root.
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
      }

      for (Node child = typeNode.getFirstChild(); child != null;
           child = child.getNext()) {
        fixTypeNode(child);
      }
    }
  }
}
