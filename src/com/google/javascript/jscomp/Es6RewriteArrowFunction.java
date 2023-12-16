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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayDeque;
import java.util.Deque;
import org.jspecify.nullness.Nullable;

/** Converts ES6 arrow functions to standard anonymous ES3 functions. */
public class Es6RewriteArrowFunction implements NodeTraversal.Callback, CompilerPass {
  // The name of the vars that capture 'this' and 'arguments' for converting arrow functions. Note
  // that these names can be reused (once per scope) because declarations in nested scopes will
  // shadow one another, which results in the intended behaviour.
  private static final String ARGUMENTS_VAR = "$jscomp$arguments";
  static final String THIS_VAR = "$jscomp$this";

  private final AbstractCompiler compiler;
  private final AstFactory astFactory;
  private final Deque<ThisAndArgumentsContext> contextStack;
  private static final FeatureSet transpiledFeatures =
      FeatureSet.BARE_MINIMUM.with(Feature.ARROW_FUNCTIONS);

  public Es6RewriteArrowFunction(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
    this.contextStack = new ArrayDeque<>();
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, root, transpiledFeatures);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case SCRIPT:
        contextStack.push(contextForScript(n, t.getInput()));
        break;
      case FUNCTION:
        if (!n.isArrowFunction()) {
          contextStack.push(contextForFunction(n, t.getInput()));
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
      addVarDeclarations(t, context);
    }
  }

  private void visitArrowFunction(NodeTraversal t, Node n, ThisAndArgumentsContext context) {
    n.setIsArrowFunction(false);

    Node body = n.getLastChild();
    if (!body.isBlock()) {
      body.detach();
      body = IR.block(IR.returnNode(body)).srcrefTreeIfMissing(body);
      n.addChildToBack(body);
    }

    ThisAndArgumentsReferenceUpdater updater =
        new ThisAndArgumentsReferenceUpdater(compiler, context, astFactory);
    NodeTraversal.traverse(compiler, body, updater);

    t.reportCodeChange();
  }

  private void addVarDeclarations(NodeTraversal t, ThisAndArgumentsContext context) {
    Node scopeBody = context.scopeBody;

    if (context.needsThisVar) {
      Node name = astFactory.createName(THIS_VAR + "$" + context.uniqueId, context.getThisType());
      Node thisVar = IR.constNode(name, astFactory.createThis(context.getThisType()));
      NodeUtil.addFeatureToScript(t.getCurrentScript(), Feature.CONST_DECLARATIONS, compiler);
      thisVar.srcrefTreeIfMissing(scopeBody);
      makeTreeNonIndexable(thisVar);

      if (context.lastSuperStatement == null) {
        scopeBody.addChildToFront(thisVar);
      } else {
        // Not safe to reference `this` until after super() has been called.
        // TODO(bradfordcsmith): Some complex cases still aren't covered, like
        //     if (...) { super(); arrow function } else { super(); }
        thisVar.insertAfter(context.lastSuperStatement);
      }
      compiler.reportChangeToEnclosingScope(thisVar);
    }

    if (context.needsArgumentsVar) {
      Node argumentsVar = astFactory.createArgumentsAliasDeclaration(ARGUMENTS_VAR);
      NodeUtil.addFeatureToScript(t.getCurrentScript(), Feature.CONST_DECLARATIONS, compiler);
      scopeBody.addChildToFront(argumentsVar);

      argumentsVar.srcrefTreeIfMissing(scopeBody);
      compiler.reportChangeToEnclosingScope(argumentsVar);
    }
  }

  private void makeTreeNonIndexable(Node n) {
    n.makeNonIndexable();
    for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
      makeTreeNonIndexable(child);
    }
  }

  /**
   * Accumulates information about a scope in which `this` and `arguments` are consistent.
   *
   * <p>Instances are maintained in a DFS stack during traversal of {@link Es6RewriteArrowFunction}.
   * They can't be immutable because a context isn't fully defined by a single node (`super()` makes
   * this hard).
   */
  private static class ThisAndArgumentsContext {
    final Node scopeBody;
    final boolean isConstructor;
    @Nullable Node lastSuperStatement = null; // Last statement in the body that refers to super().
    // An object of ThisAndArgumentsContext exists for each Script and each Function. Store a
    // uniqueId string based on the script node's filename's hash. This gets used to generate a
    // unique name when rewriting `this` to `$jscomp$this`.
    String uniqueId = "";

    boolean needsThisVar = false;
    private AstFactory.@Nullable Type thisType;

    boolean needsArgumentsVar = false;

    private ThisAndArgumentsContext(Node scopeBody, boolean isConstructor, String uniqueId) {
      this.scopeBody = scopeBody;
      this.isConstructor = isConstructor;
      this.uniqueId = uniqueId;
    }

    AstFactory.@Nullable Type getThisType() {
      return thisType;
    }

    @CanIgnoreReturnValue
    ThisAndArgumentsContext setNeedsThisVarWithType(AstFactory.Type type) {
      thisType = type;
      needsThisVar = true;
      return this;
    }

    @CanIgnoreReturnValue
    ThisAndArgumentsContext setNeedsArgumentsVar() {
      needsArgumentsVar = true;
      return this;
    }
  }

  private ThisAndArgumentsContext contextForFunction(Node functionNode, CompilerInput input) {
    Node scopeBody = functionNode.getLastChild();
    return new ThisAndArgumentsContext(
        scopeBody,
        NodeUtil.isEs6Constructor(functionNode),
        compiler.getUniqueIdSupplier().getUniqueId(input));
  }

  private ThisAndArgumentsContext contextForScript(Node scriptNode, CompilerInput input) {
    return new ThisAndArgumentsContext(
        scriptNode, /* isConstructor= */ false, compiler.getUniqueIdSupplier().getUniqueId(input));
  }

  /**
   * Sub-rewriter for references to `this` and `arguments` in a single arrow function.
   *
   * <p>An instance is generated for each arrow function in order of <em>decreasing</em> depth. This
   * isn't too inefficient, because instances don't traverse into non-arrow functions and all nested
   * functions will already have been "de-arrowed".
   */
  private static class ThisAndArgumentsReferenceUpdater implements NodeTraversal.Callback {
    private final AbstractCompiler compiler;
    private final ThisAndArgumentsContext context;
    private final AstFactory astFactory;

    public ThisAndArgumentsReferenceUpdater(
        AbstractCompiler compiler, ThisAndArgumentsContext context, AstFactory astFactory) {
      this.compiler = compiler;
      this.context = context;
      this.astFactory = astFactory;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isThis()) {
        context.setNeedsThisVarWithType(AstFactory.type(n));

        Node name =
            astFactory
                .createName(THIS_VAR + "$" + context.uniqueId, context.getThisType())
                .srcref(n);
        name.makeNonIndexable();
        if (compiler.getOptions().preservesDetailedSourceInfo()) {
          name.setOriginalName("this");
        }

        n.replaceWith(name);
      } else if (n.isName() && n.getString().equals("arguments")) {
        context.setNeedsArgumentsVar();

        Node name = astFactory.createName(ARGUMENTS_VAR, AstFactory.type(n)).srcref(n);
        if (compiler.getOptions().preservesDetailedSourceInfo()) {
          name.setOriginalName("arguments");
        }

        n.replaceWith(name);
      }
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return !n.isFunction()
          // TODO(nickreid): Remove this check? All functions below root should have already been
          // "de-arrowed".
          || n.isArrowFunction();
    }
  }
}
