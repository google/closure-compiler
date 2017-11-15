/*
 * Copyright 2015 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayDeque;
import java.util.Deque;

/** Converts ES6 arrow functions to standard anonymous ES3 functions. */
public class Es6RewriteArrowFunction implements NodeTraversal.Callback, HotSwapCompilerPass {
  // The name of the vars that capture 'this' and 'arguments'
  // for converting arrow functions.
  private static final String ARGUMENTS_VAR = "$jscomp$arguments";
  static final String THIS_VAR = "$jscomp$this";

  private static class ThisContext {
    final Node scopeBody;
    final boolean isConstructor;
    Node lastSuperStatement = null; // Last statement in the body that refers to super().
    boolean needsThisVar = false;
    boolean needsArgumentsVar = false;

    ThisContext(Node scopeBody, boolean isConstructor) {
      this.scopeBody = scopeBody;
      this.isConstructor = isConstructor;
    }

    static ThisContext forFunction(Node functionNode, Node functionParent) {
      Node scopeBody = functionNode.getLastChild();
      boolean isConstructor =
          functionParent.isMemberFunctionDef() && functionParent.getString().equals("constructor");
      return new ThisContext(scopeBody, isConstructor);
    }

    static ThisContext forScript(Node scriptNode) {
      return new ThisContext(scriptNode, false /* isConstructor */);
    }
  }

  private final AbstractCompiler compiler;
  private final Deque<ThisContext> thisContextStack;
  private static final FeatureSet transpiledFeatures =
      FeatureSet.BARE_MINIMUM.with(Feature.ARROW_FUNCTIONS);

  public Es6RewriteArrowFunction(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.thisContextStack = new ArrayDeque<>();
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, externs, transpiledFeatures, this);
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(compiler, scriptRoot, transpiledFeatures, this);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case SCRIPT:
        thisContextStack.push(ThisContext.forScript(n));
        break;
      case FUNCTION:
        if (!n.isArrowFunction()) {
          thisContextStack.push(ThisContext.forFunction(n, parent));
        }
        break;
      case SUPER:
        ThisContext thisContext = checkNotNull(thisContextStack.peek());
        // super(...) within a constructor.
        if (thisContext.isConstructor && parent.isCall() && parent.getFirstChild() == n) {
          thisContext.lastSuperStatement = getEnclosingStatement(parent, thisContext.scopeBody);
        }
        break;
      default:
        break;
    }
    return true;
  }

  /**
   * @param n
   * @param block
   * @return The statement Node that is a child of block and contains n.
   */
  private Node getEnclosingStatement(Node n, Node block) {
    while (checkNotNull(n.getParent()) != block) {
      n = checkNotNull(NodeUtil.getEnclosingStatement(n.getParent()));
    }
    return n;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    ThisContext thisContext = thisContextStack.peek();

    if (n.isArrowFunction()) {
      visitArrowFunction(t, n, checkNotNull(thisContext));
    } else if (thisContext != null && thisContext.scopeBody == n) {
      thisContextStack.pop();
      addVarDecls(thisContext);
    }
  }

  private void visitArrowFunction(NodeTraversal t, Node n, ThisContext thisContext) {
    n.setIsArrowFunction(false);
    n.makeNonIndexable();
    Node body = n.getLastChild();
    if (!body.isNormalBlock()) {
      body.detach();
      body = IR.block(IR.returnNode(body)).useSourceInfoIfMissingFromForTree(body);
      n.addChildToBack(body);
    }

    UpdateThisAndArgumentsReferences updater = new UpdateThisAndArgumentsReferences(compiler);
    NodeTraversal.traverseEs6(compiler, body, updater);
    thisContext.needsThisVar = thisContext.needsThisVar || updater.changedThis;
    thisContext.needsArgumentsVar = thisContext.needsArgumentsVar || updater.changedArguments;

    t.reportCodeChange();
  }

  private void addVarDecls(ThisContext thisContext) {
    Node scopeBody = thisContext.scopeBody;
    if (thisContext.needsArgumentsVar) {
      Node name = IR.name(ARGUMENTS_VAR);
      Node argumentsVar = IR.constNode(name, IR.name("arguments"));
      JSDocInfoBuilder jsdoc = new JSDocInfoBuilder(false);
      jsdoc.recordType(
          new JSTypeExpression(
              new Node(Token.BANG, IR.string("Arguments")), "<Es6RewriteArrowFunction>"));
      argumentsVar.setJSDocInfo(jsdoc.build());
      argumentsVar.useSourceInfoIfMissingFromForTree(scopeBody);
      scopeBody.addChildToFront(argumentsVar);
      compiler.reportChangeToEnclosingScope(argumentsVar);
    }
    if (thisContext.needsThisVar) {
      Node name = IR.name(THIS_VAR);
      Node thisVar = IR.constNode(name, IR.thisNode());
      thisVar.useSourceInfoIfMissingFromForTree(scopeBody);
      makeTreeNonIndexable(thisVar);
      if (thisContext.lastSuperStatement == null) {
        scopeBody.addChildToFront(thisVar);
      } else {
        // Not safe to reference `this` until after super() has been called.
        // TODO(bradfordcsmith): Some complex cases still aren't covered, like
        //     if (...) { super(); arrow function } else { super(); }
        scopeBody.addChildAfter(thisVar, thisContext.lastSuperStatement);
      }
      compiler.reportChangeToEnclosingScope(thisVar);
    }
  }

  private void makeTreeNonIndexable(Node n) {
    n.makeNonIndexable();
    for (Node child : n.children()) {
      makeTreeNonIndexable(child);
    }
  }

  private static class UpdateThisAndArgumentsReferences implements NodeTraversal.Callback {
    private boolean changedThis = false;
    private boolean changedArguments = false;
    private final AbstractCompiler compiler;

    public UpdateThisAndArgumentsReferences(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isThis()) {
        Node name = IR.name(THIS_VAR).srcref(n);
        name.makeNonIndexable();
        if (compiler.getOptions().preservesDetailedSourceInfo()) {
          name.setOriginalName("this");
        }
        parent.replaceChild(n, name);
        changedThis = true;
      } else if (n.isName() && n.getString().equals("arguments")) {
        Node name = IR.name(ARGUMENTS_VAR).srcref(n);
        if (compiler.getOptions().preservesDetailedSourceInfo()) {
          name.setOriginalName("arguments");
        }
        parent.replaceChild(n, name);
        changedArguments = true;
      }
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return !n.isFunction() || n.isArrowFunction();
    }
  }
}
