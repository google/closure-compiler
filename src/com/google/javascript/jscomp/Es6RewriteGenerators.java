/*
 * Copyright 2014 The Closure Compiler Authors.
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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts ES6 generator functions to valid ES3 code.
 *
 * @author mattloring@google.com (Matthew Loring)
 */
public class Es6RewriteGenerators extends NodeTraversal.AbstractPostOrderCallback
    implements HotSwapCompilerPass {
  private final AbstractCompiler compiler;

  private static final String ITER_KEY = "$$iterator";

  // This holds the current state at which a generator should resume execution after
  // a call to yield or return. The beginning state is 0 and the end state is -1.
  private static final String GENERATOR_STATE = "$jscomp$generator$state";

  private static int generatorCaseCount = 0;

  public Es6RewriteGenerators(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.FUNCTION:
        if (n.isGeneratorFunction()) {
          generatorCaseCount = 0;
          visitGenerator(n, parent);
        }
        break;
    }
  }

  private void visitGenerator(Node n, Node parent) {
    Node genBlock = compiler.parseSyntheticCode(Joiner.on('\n').join(
      "{",
      "  return {" + ITER_KEY + ": function() {",
      "    var " + GENERATOR_STATE + " = " + generatorCaseCount + ";",
      "    return { next: function() {",
      "      while (1) switch (" + GENERATOR_STATE + ") {",
      "        case " + generatorCaseCount + ":",
      "        default:",
      "          return {done: true};",
      "      }",
      "    }}",
      "  }}",
      "}"
    )).removeFirstChild();
    generatorCaseCount++;

    Node genFunc = IR.function(n.removeFirstChild(), n.removeFirstChild(), genBlock);

    Node originalBody = n.getFirstChild();

    // Set state to the default after the body of the function has completed.
    originalBody.addChildToBack(
        IR.exprResult(IR.assign(IR.name(GENERATOR_STATE), IR.number(-1))));

    Node currentCase = getUnique(genBlock, Token.CASE);

    while (originalBody.hasChildren()) {
      Node nextStatement = originalBody.removeFirstChild();
      boolean makeFreshCase;

      if (nextStatement.isExprResult() && nextStatement.getFirstChild().isYield()) {
        visitYieldExprResult(nextStatement, currentCase);
        makeFreshCase = true;
      } else {
        // In the default case, add the statement to the current case block unchanged.
        currentCase.getLastChild().addChildToBack(nextStatement);
        makeFreshCase = false;
      }

      if (makeFreshCase) {
        Node newCase = IR.caseNode(IR.number(generatorCaseCount), IR.block());
        currentCase.getParent().addChildAfter(newCase, currentCase);
        currentCase = newCase;
        generatorCaseCount++;
      }
    }

    parent.replaceChild(n, genFunc);
    parent.useSourceInfoIfMissingFromForTree(parent);
    compiler.reportCodeChange();
  }

  private void visitYieldExprResult(Node statement, Node enclosingCase) {
    enclosingCase.getLastChild().addChildToBack(getContextUpdate());
    enclosingCase.getLastChild().addChildToBack(IR.returnNode(
        getIteratorResult(statement.getFirstChild().removeFirstChild())));
  }

  private Node getContextUpdate() {
    return IR.exprResult(
        IR.assign(IR.name(GENERATOR_STATE), IR.number(generatorCaseCount)));
  }

  private Node getIteratorResult(Node value) {
    return IR.objectlit(
        IR.propdef(IR.stringKey("value"), value),
        IR.propdef(IR.stringKey("done"), IR.falseNode()));
  }

  //TODO(mattloring): move these to NodeUtil if deemed generally useful.
  /**
   * Finds the only child of the provided node of the given type.
   */
  private Node getUnique(Node node, int type) {
    List<Node> matches = new ArrayList<>();
    insertAll(node, type, matches);
    Preconditions.checkState(matches.size() == 1);
    return matches.get(0);
  }

  /**
   * Adds all children of the provided node of the given type to given list.
   */
  private void insertAll(Node node, int type, List<Node> matchingNodes) {
    if (node.getType() == type) {
      matchingNodes.add(node);
    }
    for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
      insertAll(c, type, matchingNodes);
    }
  }

}

