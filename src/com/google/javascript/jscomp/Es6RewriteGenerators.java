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

  // The current case statement onto which translated statements from the
  // body of a generator will be appended.
  private Node enclosingCase;

  // The destination for vars defined in the body of a generator.
  private Node hoistRoot;

  // The body of the generator function currently being translated.
  private Node originalGeneratorBody;

  // The current statement being translated.
  private Node currentStatement;

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

    originalGeneratorBody = n.getFirstChild();

    // Set state to the default after the body of the function has completed.
    originalGeneratorBody.addChildToBack(
        IR.exprResult(IR.assign(IR.name(GENERATOR_STATE), IR.number(-1))));

    enclosingCase = getUnique(genBlock, Token.CASE);
    hoistRoot = getUnique(genBlock, Token.VAR);

    while (originalGeneratorBody.hasChildren()) {
      currentStatement = originalGeneratorBody.removeFirstChild();
      boolean advanceCase = translateStatementInOriginalBody();

      if (advanceCase) {
        int caseNumber;
        if (currentStatement.isGeneratorMarker()) {
          caseNumber = (int) currentStatement.getDouble();
        } else {
          caseNumber = generatorCaseCount;
          generatorCaseCount++;
        }
        Node newCase = IR.caseNode(IR.number(caseNumber), IR.block());
        enclosingCase.getParent().addChildAfter(newCase, enclosingCase);
        enclosingCase = newCase;
      }
    }

    parent.replaceChild(n, genFunc);
    parent.useSourceInfoIfMissingFromForTree(parent);
    compiler.reportCodeChange();
  }

  /** Returns true if a new case node should be added */
  private boolean translateStatementInOriginalBody() {
    if (currentStatement.isExprResult() && currentStatement.getFirstChild().isYield()) {
      visitYieldExprResult();
      return true;
    } else if (currentStatement.isVar()) {
      visitVar();
      return false;
    } else if (currentStatement.isFor() && yieldsOrReturns(currentStatement)) {
      visitFor();
      return false;
    } else if (currentStatement.isWhile() && yieldsOrReturns(currentStatement)) {
      visitWhile();
      return false;
    } else if (currentStatement.isIf() && yieldsOrReturns(currentStatement)
        && !currentStatement.isGeneratorSafe()) {
      visitIf();
      return false;
    } else if (currentStatement.isBlock()) {
      visitBlock();
      return false;
    } else if (currentStatement.isGeneratorMarker()) {
      return true;
    } else if (currentStatement.isReturn()) {
      visitReturn();
      return false;
    } else if ((currentStatement.isBreak() && !currentStatement.isGeneratorSafe())
        || currentStatement.isContinue() || currentStatement.isThrow()) {
      compiler.report(JSError.make(currentStatement, Es6ToEs3Converter.CANNOT_CONVERT_YET,
          "Breaks, continues, and throws are not yet allowed if their"
          + " enclosing control structure contains a yield or return."));
      return false;
    } else {
      // In the default case, add the statement to the current case block unchanged.
      enclosingCase.getLastChild().addChildToBack(currentStatement);
      return false;
    }
  }

  /**
   * {@code if} statements have their bodies lifted to the function top level
   * and use a case statement to jump over the body if the condition of the
   * if statement is false.
   */
  private void visitIf() {
    Node condition = currentStatement.removeFirstChild();
    Node ifBody = currentStatement.removeFirstChild();
    boolean hasElse = currentStatement.hasChildren();

    int ifEndState = generatorCaseCount++;

    Node safeBreak = IR.breakNode();
    safeBreak.setGeneratorSafe(true);
    Node invertedConditional = IR.ifNode(IR.not(condition),
        IR.block(createStateUpdate(ifEndState), safeBreak));
    invertedConditional.setGeneratorSafe(true);
    Node endIf = IR.number(ifEndState);
    endIf.setGeneratorMarker(true);

    originalGeneratorBody.addChildToFront(invertedConditional);
    originalGeneratorBody.addChildAfter(ifBody, invertedConditional);
    originalGeneratorBody.addChildAfter(endIf, ifBody);

    if (hasElse) {
      Node elseBlock = currentStatement.removeFirstChild();

      int elseEndState = generatorCaseCount++;

      Node endElse = IR.number(elseEndState);
      endElse.setGeneratorMarker(true);

      ifBody.addChildToBack(createStateUpdate(elseEndState));
      ifBody.addChildToBack(safeBreak.cloneTree());
      originalGeneratorBody.addChildAfter(elseBlock, endIf);
      originalGeneratorBody.addChildAfter(endElse, elseBlock);
    }
  }

  /**
   * Blocks are flattened by lifting all children to the body of the original generator.
   */
  private void visitBlock() {
    Node insertionPoint = currentStatement.removeFirstChild();
    originalGeneratorBody.addChildToFront(insertionPoint);
    for (Node child = currentStatement.removeFirstChild(); child != null;
        child = currentStatement.removeFirstChild()) {
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
  private void visitWhile() {
    Node condition = currentStatement.removeFirstChild();
    Node body = currentStatement.removeFirstChild();

    int loopBeginState = generatorCaseCount++;

    Node beginCase = IR.number(loopBeginState);
    beginCase.setGeneratorMarker(true);
    Node conditionalBranch = IR.ifNode(condition, body);
    Node setStateLoopStart = createStateUpdate(loopBeginState);
    Node breakToStart = IR.breakNode();
    breakToStart.setGeneratorSafe(true);

    originalGeneratorBody.addChildToFront(beginCase);
    originalGeneratorBody.addChildAfter(conditionalBranch, beginCase);
    body.addChildToBack(setStateLoopStart);
    body.addChildToBack(breakToStart);
  }

  /**
   * {@code for} loops are translated into equivalent while loops to
   * consolidate loop translation logic.
   */
  private void visitFor() {
    Node initializer = currentStatement.removeFirstChild();
    Node condition = currentStatement.removeFirstChild();
    Node postStatement = IR.exprResult(currentStatement.removeFirstChild());
    Node body = currentStatement.removeFirstChild();

    Node equivalentWhile = IR.whileNode(condition, body);
    body.addChildToBack(postStatement);

    originalGeneratorBody.addChildToFront(initializer);
    originalGeneratorBody.addChildAfter(equivalentWhile, initializer);
  }

  /**
   * {@code var} statements are hoisted into the closure containing the iterator
   * to preserve their state accross
   * multiple calls to next().
   */
  private void visitVar() {
    Node name = currentStatement.removeFirstChild();
    while (name != null) {
      enclosingCase.getLastChild().addChildToBack(
          IR.exprResult(IR.assign(name, name.removeFirstChild())));
      hoistRoot.getParent().addChildAfter(IR.var(name.cloneTree()), hoistRoot);
      name = currentStatement.removeFirstChild();
    }
  }

  /**
   * {@code yield} sets the state so that execution resume at the next statement
   * when the function is next called and then returns an iterator result with
   * the desired value.
   */
  private void visitYieldExprResult() {
    enclosingCase.getLastChild().addChildToBack(createStateUpdate());
    enclosingCase.getLastChild().addChildToBack(IR.returnNode(
        createIteratorResult(currentStatement.getFirstChild().removeFirstChild())));
  }

  /**
   * {@code return} statements are translated to set the state to done before returning the
   * desired value.
   */
  private void visitReturn() {
    enclosingCase.getLastChild().addChildToBack(createStateUpdate(-1));
    enclosingCase.getLastChild().addChildToBack(IR.returnNode(
        createIteratorResult(currentStatement.removeFirstChild())));
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
  private static boolean yieldsOrReturns(Node n) {
    return NodeUtil.referencesYield(n) || NodeUtil.referencesReturn(n);
  }

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

