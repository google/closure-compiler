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
 * Converts ES6 generator functions to valid ES3 code. This pass runs after all ES6 features
 * except for yield and generators have been transpiled.
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
    Node varRoot = getUnique(genBlock, Token.VAR);

    while (originalBody.hasChildren()) {
      Node nextStatement = originalBody.removeFirstChild();
      boolean advanceCase = translateStatementInOriginalBody(nextStatement, currentCase,
          originalBody, varRoot);

      if (advanceCase) {
        int caseNumber;
        if (nextStatement.isGeneratorMarker()) {
          caseNumber = (int) nextStatement.getDouble();
        } else {
          caseNumber = generatorCaseCount;
          generatorCaseCount++;
        }
        Node newCase = IR.caseNode(IR.number(caseNumber), IR.block());
        currentCase.getParent().addChildAfter(newCase, currentCase);
        currentCase = newCase;
      }
    }

    parent.replaceChild(n, genFunc);
    parent.useSourceInfoIfMissingFromForTree(parent);
    compiler.reportCodeChange();
  }

  /** Returns true if a new case node should be added */
  private boolean translateStatementInOriginalBody(Node statement, Node currentCase,
      Node originalBody, Node varRoot) {
    if (statement.isExprResult() && statement.getFirstChild().isYield()) {
      visitYieldExprResult(statement, currentCase);
      return true;
    } else if (statement.isVar()) {
      visitVar(statement, currentCase, varRoot);
      return false;
    } else if (statement.isFor() && NodeUtil.referencesYield(statement)) {
      visitFor(statement, originalBody);
      return false;
    } else if (statement.isWhile() && NodeUtil.referencesYield(statement)) {
      visitWhile(statement, originalBody);
      return false;
    } else if (statement.isIf() && NodeUtil.referencesYield(statement)
        && !statement.isGeneratorSafeIf()) {
      visitIf(statement, originalBody);
      return false;
    } else if (statement.isBlock()) {
      visitBlock(statement, originalBody);
      return false;
    } else if (statement.isGeneratorMarker()) {
      return true;
    } else {
      // In the default case, add the statement to the current case block unchanged.
      currentCase.getLastChild().addChildToBack(statement);
      return false;
    }
  }

  /**
   * {@code if} statements have their bodies lifted to the function top level
   * and use a case statement to jump over the body if the condition of the
   * if statement is false.
   */
  private void visitIf(Node ifStatement, Node originalGeneratorBody) {
    Node condition = ifStatement.removeFirstChild();
    Node ifBody = ifStatement.removeFirstChild();
    boolean hasElse = ifStatement.hasChildren();

    int ifEndState = generatorCaseCount++;

    Node invertedConditional = IR.ifNode(IR.not(condition),
        IR.block(createStateUpdate(ifEndState), IR.breakNode()));
    invertedConditional.setGeneratorSafeIf(true);
    Node endIf = IR.number(ifEndState);
    endIf.setGeneratorMarker(true);

    originalGeneratorBody.addChildToFront(invertedConditional);
    originalGeneratorBody.addChildAfter(ifBody, invertedConditional);
    originalGeneratorBody.addChildAfter(endIf, ifBody);

    if (hasElse) {
      Node elseBlock = ifStatement.removeFirstChild();

      int elseEndState = generatorCaseCount++;

      Node endElse = IR.number(elseEndState);
      endElse.setGeneratorMarker(true);

      ifBody.addChildToBack(createStateUpdate(elseEndState));
      ifBody.addChildToBack(IR.breakNode());
      originalGeneratorBody.addChildAfter(elseBlock, endIf);
      originalGeneratorBody.addChildAfter(endElse, elseBlock);
    }
  }

  /**
   * Blocks are flattened by lifting all children to the body of the original generator.
   */
  private void visitBlock(Node blockStatement, Node originalGeneratorBody) {
    Node insertionPoint = blockStatement.removeFirstChild();
    originalGeneratorBody.addChildToFront(insertionPoint);
    for (Node child = blockStatement.removeFirstChild(); child != null;
        child = blockStatement.removeFirstChild()) {
      originalGeneratorBody.addChildAfter(child, insertionPoint);
      insertionPoint = child;
    }
  }

  /**
   * {@code while} loops are translated to a case statement followed by an if statement
   * containing the loop body. The if statement finishes by jumping back to the initial
   * case statement to enter the loop again.
   *
   * <code>
   * while (b) {
   *   s;
   * }
   * </code>
   *
   * is rewritten to:
   *
   * <code>
   * case_marker n;
   * if (b) {
   *   s;
   *   state = n;
   *   break;
   * }
   * </code>
   */
  private void visitWhile(Node whileStatement, Node originalGeneratorBody) {
    Node condition = whileStatement.removeFirstChild();
    Node body = whileStatement.removeFirstChild();

    int loopBeginState = generatorCaseCount++;

    Node beginCase = IR.number(loopBeginState);
    beginCase.setGeneratorMarker(true);
    Node conditionalBranch = IR.ifNode(condition, body);
    Node setStateLoopStart = createStateUpdate(loopBeginState);
    Node breakToStart = IR.breakNode();

    originalGeneratorBody.addChildToFront(beginCase);
    originalGeneratorBody.addChildAfter(conditionalBranch, beginCase);
    body.addChildToBack(setStateLoopStart);
    body.addChildToBack(breakToStart);
  }

  /**
   * For loops are translated into equivalent while loops to consolidate loop translation logic.
   */
  private void visitFor(Node whileStatement, Node originalGeneratorBody) {
    Node initializer = whileStatement.removeFirstChild();
    Node condition = whileStatement.removeFirstChild();
    Node postStatement = IR.exprResult(whileStatement.removeFirstChild());
    Node body = whileStatement.removeFirstChild();

    Node equivalentWhile = IR.whileNode(condition, body);
    body.addChildToBack(postStatement);

    originalGeneratorBody.addChildToFront(initializer);
    originalGeneratorBody.addChildAfter(equivalentWhile, initializer);
  }

  /**
   * Vars are hoisted into the closure containing the iterator to preserve their state accross
   * multiple calls to next().
   */
  private void visitVar(Node varStatement, Node enclosingCase, Node hoistRoot) {
    Node name = varStatement.removeFirstChild();
    while (name != null) {
      enclosingCase.getLastChild().addChildToBack(
          IR.exprResult(IR.assign(name, name.removeFirstChild())));
      hoistRoot.getParent().addChildAfter(IR.var(name.cloneTree()), hoistRoot);
      name = varStatement.removeFirstChild();
    }
  }

  /**
   * Yield sets the state so that execution resume at the next statement when the function is next
   * called and then returns an iterator result with the desired value.
   */
  private void visitYieldExprResult(Node yieldStatement, Node enclosingCase) {
    enclosingCase.getLastChild().addChildToBack(createStateUpdate());
    enclosingCase.getLastChild().addChildToBack(IR.returnNode(
        createIteratorResult(yieldStatement.getFirstChild().removeFirstChild())));
  }

  private Node createStateUpdate() {
    return IR.exprResult(
        IR.assign(IR.name(GENERATOR_STATE), IR.number(generatorCaseCount)));
  }

  private Node createStateUpdate(int state) {
    return IR.exprResult(
        IR.assign(IR.name(GENERATOR_STATE), IR.number(state)));
  }

  private Node createIteratorResult(Node value) {
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

