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
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
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

  static final DiagnosticType GOOG_SCOPE_NON_ALIAS_LOCAL = DiagnosticType.error(
      "JSC_GOOG_SCOPE_NON_ALIAS_LOCAL",
      "The local variable {0} is in a goog.scope and is not an alias.");

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
      for (AliasUsage aliasUsage : traversal.getAliasUsages()) {
        aliasUsage.applyAlias();
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

  private interface AliasUsage {
    public void applyAlias();
  }

  private class AliasedNode implements AliasUsage {
    private final Node aliasReference;

    private final Node aliasDefinition;

    AliasedNode(Node aliasReference, Node aliasDefinition) {
      this.aliasReference = aliasReference;
      this.aliasDefinition = aliasDefinition;
    }

    @Override
    public void applyAlias() {
      aliasReference.getParent().replaceChild(
          aliasReference, aliasDefinition.cloneTree());
    }
  }

  private class AliasedTypeNode implements AliasUsage {
    private final Node typeReference;
    private final Node aliasDefinition;
    private final String aliasName;

    AliasedTypeNode(Node typeReference, Node aliasDefinition,
        String aliasName) {
      this.typeReference = typeReference;
      this.aliasDefinition = aliasDefinition;
      this.aliasName = aliasName;
    }

    @Override
    public void applyAlias() {
      String typeName = typeReference.getString();
      String aliasExpanded =
          Preconditions.checkNotNull(aliasDefinition.getQualifiedName());
      Preconditions.checkState(typeName.startsWith(aliasName));
      typeReference.setString(typeName.replaceFirst(aliasName, aliasExpanded));
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
    private final Set<String> forbiddenLocals = Sets.newHashSet();
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
        int type = n.getType();
        Node parent = n.getParent();
        if (parent.isVar() &&
            n.hasChildren() && n.getFirstChild().isQualifiedName()) {
          String name = n.getString();
          Var aliasVar = scope.getVar(name);
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
        } else if (v.isBleedingFunction()) {
          // Bleeding functions already get a BAD_PARAMETERS error, so just
          // do nothing.
        } else if (parent.getType() == Token.LP) {
          // Parameters of the scope function also get a BAD_PARAMETERS
          // error.
        } else {
          // TODO(robbyw): Support using locals for private variables.
          report(t, n, GOOG_SCOPE_NON_ALIAS_LOCAL, n.getString());
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
          Node aliasedNode = aliasVar.getInitialValue();
          aliasUsages.add(new AliasedNode(n, aliasedNode));
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
          Node aliasedNode = aliasVar.getInitialValue();
          aliasUsages.add(new AliasedTypeNode(typeNode, aliasedNode, baseName));
        }
      }

      for (Node child = typeNode.getFirstChild(); child != null;
           child = child.getNext()) {
        fixTypeNode(child);
      }
    }
  }
}
