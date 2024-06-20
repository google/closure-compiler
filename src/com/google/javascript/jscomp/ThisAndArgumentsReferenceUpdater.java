/*
 * Copyright 2024 The Closure Compiler Authors.
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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import org.jspecify.annotations.Nullable;

/**
 * Rewrites references to `this` and `arguments` in a single function to instead refer to local
 * variables.
 *
 * <p>When systematically transpiling away all arrow functions, an instance is generated for each
 * arrow function in order of <em>decreasing</em> depth. This isn't too inefficient, because
 * instances don't traverse into non-arrow functions and all nested functions will already have been
 * "de-arrowed". This class is also used for one-off cases (e.g. instrumenting generators for {@code
 * AsyncContext}), in which case there may be nested arrow functions that need to be recursed into.
 */
class ThisAndArgumentsReferenceUpdater implements NodeTraversal.Callback {
  // The name of the vars that capture 'this' and 'arguments' for converting arrow functions. Note
  // that these names can be reused (once per scope) because declarations in nested scopes will
  // shadow one another, which results in the intended behaviour.
  private static final String ARGUMENTS_VAR = "$jscomp$arguments";
  private static final String THIS_VAR = "$jscomp$this";

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
          astFactory.createName(THIS_VAR + "$" + context.uniqueId, context.getThisType()).srcref(n);
      name.makeNonIndexable();
      if (compiler.getOptions().preservesDetailedSourceInfo()) {
        name.setOriginalName("this");
      }

      n.replaceWith(name);
    } else if (n.isName() && n.getString().equals("arguments")) {
      context.setNeedsArgumentsVar();

      Node name =
          astFactory
              .createName(ARGUMENTS_VAR + "$" + context.uniqueId, AstFactory.type(n))
              .srcref(n);
      if (compiler.getOptions().preservesDetailedSourceInfo()) {
        name.setOriginalName("arguments");
      }

      n.replaceWith(name);
    }
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    return !n.isFunction() || n.isArrowFunction();
  }

  /**
   * Accumulates information about a scope in which `this` and `arguments` are consistent.
   *
   * <p>Instances are maintained in a DFS stack during traversal of {@link Es6RewriteArrowFunction}.
   * They can't be immutable because a context isn't fully defined by a single node (`super()` makes
   * this hard).
   */
  static class ThisAndArgumentsContext {
    final Node scopeBody;
    final boolean isConstructor;
    @Nullable Node lastSuperStatement = null; // Last statement in the body that refers to super().
    // An object of ThisAndArgumentsContext exists for each Script and each Function. Store a
    // uniqueId string based on the script node's filename's hash. This gets used to generate a
    // unique name when rewriting `this` to `$jscomp$this`.
    String uniqueId = "";

    boolean needsThisVar = false;
    AstFactory.@Nullable Type thisType;

    boolean needsArgumentsVar = false;

    ThisAndArgumentsContext(Node scopeBody, boolean isConstructor, String uniqueId) {
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

    void addVarDeclarations(AbstractCompiler compiler, AstFactory astFactory, Node script) {
      if (needsThisVar) {
        Node name = astFactory.createName(THIS_VAR + "$" + uniqueId, getThisType());
        Node thisVar = IR.constNode(name, astFactory.createThis(getThisType()));
        NodeUtil.addFeatureToScript(script, Feature.CONST_DECLARATIONS, compiler);
        thisVar.srcrefTreeIfMissing(scopeBody);
        makeTreeNonIndexable(thisVar);

        if (lastSuperStatement == null) {
          insertVarDeclaration(thisVar, scopeBody);
        } else {
          // Not safe to reference `this` until after super() has been called.
          // TODO(bradfordcsmith): Some complex cases still aren't covered, like
          //     if (...) { super(); arrow function } else { super(); }
          thisVar.insertAfter(lastSuperStatement);
        }
        compiler.reportChangeToEnclosingScope(thisVar);
      }

      if (needsArgumentsVar) {
        Node argumentsVar =
            astFactory.createArgumentsAliasDeclaration(ARGUMENTS_VAR + "$" + uniqueId);
        NodeUtil.addFeatureToScript(script, Feature.CONST_DECLARATIONS, compiler);

        insertVarDeclaration(argumentsVar, scopeBody);

        argumentsVar.srcrefTreeIfMissing(scopeBody);
        compiler.reportChangeToEnclosingScope(argumentsVar);
      }
    }

    private static void insertVarDeclaration(Node varDeclaration, Node scopeBody) {
      if (scopeBody.getParent().isFunction()) {
        // for functions, we must find the correct insertion point to preserve normalization
        Node insertBeforePoint =
            NodeUtil.getInsertionPointAfterAllInnerFunctionDeclarations(scopeBody);
        if (insertBeforePoint != null) {
          varDeclaration.insertBefore(insertBeforePoint);
        } else {
          // functionBody only contains hoisted function declarations
          scopeBody.addChildToBack(varDeclaration);
        }
      } else {
        scopeBody.addChildToFront(varDeclaration);
      }
    }

    private static void makeTreeNonIndexable(Node n) {
      n.makeNonIndexable();
      for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
        makeTreeNonIndexable(child);
      }
    }
  }
}
