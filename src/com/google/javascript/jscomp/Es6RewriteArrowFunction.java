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
  // The name of the vars that capture 'this' and 'arguments' for converting arrow functions. Note
  // that these names can be reused (once per scope) because declarations in nested scopes will
  // shadow one another, which results in the intended behaviour.
  private static final String ARGUMENTS_VAR = "$jscomp$arguments";
  static final String THIS_VAR = "$jscomp$this";

  private final AbstractCompiler compiler;
  private final Deque<ThisAndArgumentsContext> contextStack;
  private static final FeatureSet transpiledFeatures =
      FeatureSet.BARE_MINIMUM.with(Feature.ARROW_FUNCTIONS);

  public Es6RewriteArrowFunction(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.contextStack = new ArrayDeque<>();
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, externs, transpiledFeatures, this);
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
    TranspilationPasses.markFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(compiler, scriptRoot, transpiledFeatures, this);
    TranspilationPasses.markFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case SCRIPT:
        contextStack.push(ThisAndArgumentsContext.forScript(n));
        break;
      case FUNCTION:
        if (!n.isArrowFunction()) {
          contextStack.push(ThisAndArgumentsContext.forFunction(n, parent));
        }
        break;
      case SUPER:
        ThisAndArgumentsContext context = checkNotNull(contextStack.peek());
        // super(...) within a constructor.
        if (context.isConstructor && parent.isCall() && parent.getFirstChild() == n) {
          context.lastSuperStatement = getEnclosingStatement(parent, context.scopeBody);
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
    ThisAndArgumentsContext context = contextStack.peek();

    if (n.isArrowFunction()) {
      visitArrowFunction(t, n, checkNotNull(context));
    } else if (context != null && context.scopeBody == n) {
      contextStack.pop();
      addVarDeclarations(context);
    }
  }

  private void visitArrowFunction(NodeTraversal t, Node n, ThisAndArgumentsContext context) {
    n.setIsArrowFunction(false);
    n.makeNonIndexable();

    Node body = n.getLastChild();
    if (!body.isBlock()) {
      body.detach();
      body = IR.block(IR.returnNode(body)).useSourceInfoIfMissingFromForTree(body);
      n.addChildToBack(body);
    }

    ThisAndArgumentsReferenceUpdater updater = new ThisAndArgumentsReferenceUpdater(compiler);
    NodeTraversal.traverse(compiler, body, updater);

    // TODO(nickreid): Should this just live in the updater? But that would make it more than a dumb
    // data object.
    if (updater.changedThis) {
      context.needsThisVar = true;
    }
    if (updater.changedArguments) {
      context.needsArgumentsVar = true;
    }

    t.reportCodeChange();
  }

  private void addVarDeclarations(ThisAndArgumentsContext context) {
    Node scopeBody = context.scopeBody;

    if (context.needsThisVar) {
      Node name = IR.name(THIS_VAR);
      Node thisVar = IR.constNode(name, IR.thisNode());
      thisVar.useSourceInfoIfMissingFromForTree(scopeBody);
      makeTreeNonIndexable(thisVar);

      if (context.lastSuperStatement == null) {
        scopeBody.addChildToFront(thisVar);
      } else {
        // Not safe to reference `this` until after super() has been called.
        // TODO(bradfordcsmith): Some complex cases still aren't covered, like
        //     if (...) { super(); arrow function } else { super(); }
        scopeBody.addChildAfter(thisVar, context.lastSuperStatement);
      }
      compiler.reportChangeToEnclosingScope(thisVar);
    }

    if (context.needsArgumentsVar) {
      Node name = IR.name(ARGUMENTS_VAR);
      Node argumentsVar = IR.constNode(name, IR.name("arguments"));
      scopeBody.addChildToFront(argumentsVar);

      JSDocInfoBuilder jsdoc = new JSDocInfoBuilder(false);
      jsdoc.recordType(
          new JSTypeExpression(
              new Node(Token.BANG, IR.string("Arguments")), "<Es6RewriteArrowFunction>"));
      argumentsVar.setJSDocInfo(jsdoc.build());
      argumentsVar.useSourceInfoIfMissingFromForTree(scopeBody);

      compiler.reportChangeToEnclosingScope(argumentsVar);
    }
  }

  private void makeTreeNonIndexable(Node n) {
    n.makeNonIndexable();
    for (Node child : n.children()) {
      makeTreeNonIndexable(child);
    }
  }

  /** Accumulates information about a scope in which `this` and `arguments` are consistent. */
  private static class ThisAndArgumentsContext {
    final Node scopeBody;
    final boolean isConstructor;
    Node lastSuperStatement = null; // Last statement in the body that refers to super().

    boolean needsThisVar = false;
    boolean needsArgumentsVar = false;

    ThisAndArgumentsContext(Node scopeBody, boolean isConstructor) {
      this.scopeBody = scopeBody;
      this.isConstructor = isConstructor;
    }

    static ThisAndArgumentsContext forFunction(Node functionNode, Node functionParent) {
      Node scopeBody = functionNode.getLastChild();
      boolean isConstructor =
          functionParent.isMemberFunctionDef() && functionParent.getString().equals("constructor");
      return new ThisAndArgumentsContext(scopeBody, isConstructor);
    }

    static ThisAndArgumentsContext forScript(Node scriptNode) {
      return new ThisAndArgumentsContext(scriptNode, false /* isConstructor */);
    }
  }

  /** Sub-rewriter for references to `this` and `arguments` within a single arrow. */
  private static class ThisAndArgumentsReferenceUpdater implements NodeTraversal.Callback {
    private boolean changedThis = false;
    private boolean changedArguments = false;
    private final AbstractCompiler compiler;

    public ThisAndArgumentsReferenceUpdater(AbstractCompiler compiler) {
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

        n.replaceWith(name);
        changedThis = true;
      } else if (n.isName() && n.getString().equals("arguments")) {
        Node name = IR.name(ARGUMENTS_VAR).srcref(n);
        if (compiler.getOptions().preservesDetailedSourceInfo()) {
          name.setOriginalName("arguments");
        }

        n.replaceWith(name);
        changedArguments = true;
      }
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return !n.isFunction() || n.isArrowFunction();
    }
  }
}
