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
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
  private Node enclosingBlock;

  // The destination for vars defined in the body of a generator.
  private Node hoistRoot;

  // The body of the generator function currently being translated.
  private Node originalGeneratorBody;

  // The current statement being translated.
  private Node currentStatement;

  private static final String ITER_KEY = "$$iterator";

  // The name of the variable that holds the state at which the generator
  // should resume execution after a call to yield or return.
  // The beginning state is 0 and the end state is -1.
  private static final String GENERATOR_STATE = "$jscomp$generator$state";

  private static int generatorCaseCount;

  private static final String GENERATOR_DO_WHILE_INITIAL = "$jscomp$generator$first$do";

  private static final String GENERATOR_YIELD_ALL_NAME = "$jscomp$generator$yield$all";

  private static final String GENERATOR_YIELD_ALL_ENTRY = "$jscomp$generator$yield$entry";

  private static final String GENERATOR_ARGUMENTS = "$jscomp$generator$arguments";

  private static final String GENERATOR_THIS = "$jscomp$generator$this";

  private static final String GENERATOR_NEXT_ARG = "$jscomp$generator$next$arg";

  private static final String GENERATOR_THROW_ARG = "$jscomp$generator$throw$arg";

  private Supplier<String> generatorCounter;

  private static final String GENERATOR_SWITCH_ENTERED = "$jscomp$generator$switch$entered";

  private static final String GENERATOR_SWITCH_VAL = "$jscomp$generator$switch$val";

  private static final String GENERATOR_FINALLY_JUMP = "$jscomp$generator$finally";

  private static final String GENERATOR_ERROR = "$jscomp$generator$global$error";

  private static final String GENERATOR_FOR_IN_ARRAY = "$jscomp$generator$forin$array";

  private static final String GENERATOR_FOR_IN_VAR = "$jscomp$generator$forin$var";

  private static final String GENERATOR_FOR_IN_ITER = "$jscomp$generator$forin$iter";

  private static final String GENERATOR_LOOP_GUARD = "$jscomp$generator$loop$guard";

  // Maintains a stack of numbers which identify the cases which mark the end of loops. These
  // are used to manage jump destinations for break and continue statements.
  private List<LoopContext> currentLoopContext;

  private List<ExceptionContext> currentExceptionContext;

  private boolean hasTranslatedTry;

  public Es6RewriteGenerators(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.currentLoopContext = new ArrayList<>();
    this.currentExceptionContext = new ArrayList<>();
    generatorCounter = compiler.getUniqueNameIdSupplier();
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, new DecomposeYields(compiler));
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, new DecomposeYields(compiler));
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
      case Token.NAME:
        Node enclosing = NodeUtil.getEnclosingFunction(n);
        if (enclosing != null && enclosing.isGeneratorFunction()
            && n.matchesQualifiedName("arguments")) {
          n.setString(GENERATOR_ARGUMENTS);
        }
        break;
      case Token.THIS:
        enclosing = NodeUtil.getEnclosingFunction(n);
        if (enclosing != null && enclosing.isGeneratorFunction()) {
          n.getParent().replaceChild(n, IR.name(GENERATOR_THIS));
        }
        break;
      case Token.YIELD:
        if (n.isYieldFor()) {
          visitYieldFor(n, parent);
        } else if (!parent.isExprResult()) {
          visitYieldExpr(n, parent);
        } else {
          visitYieldThrows(parent, parent.getParent());
        }
        break;
    }
  }

  private void visitYieldThrows(Node n, Node parent) {
    Node ifThrows = IR.ifNode(
        IR.shne(IR.name(GENERATOR_THROW_ARG), IR.name("undefined")),
        IR.block(IR.throwNode(IR.name(GENERATOR_THROW_ARG))));
    parent.addChildAfter(ifThrows, n);
    compiler.reportCodeChange();
  }

  /**
   * Sample translation:
   *
   * <code>
   * var i = yield * gen();
   * </code>
   *
   * is rewritten to:
   *
   * <code>
   * var $jscomp$generator$yield$all = gen();
   * var $jscomp$generator$yield$entry;
   * while (!($jscomp$generator$yield$entry =
   *     $jscomp$generator$yield$all.next($jscomp$generator$next$arg)).done) {
   *   yield $jscomp$generator$yield$entry.value;
   * }
   * var i = $jscomp$generator$yield$entry.value;
   * </code>
   */
  private void visitYieldFor(Node n, Node parent) {
    Node enclosingStatement = NodeUtil.getEnclosingStatement(n);

    Node generator = IR.var(
        IR.name(GENERATOR_YIELD_ALL_NAME),
        IR.call(
            NodeUtil.newQName(compiler, Es6ToEs3Converter.MAKE_ITER),
            n.removeFirstChild()));
    Node entryDecl = IR.var(IR.name(GENERATOR_YIELD_ALL_ENTRY));
    compiler.needsEs6Runtime = true;

    Node assignIterResult = IR.assign(
        IR.name(GENERATOR_YIELD_ALL_ENTRY),
        IR.call(IR.getprop(IR.name(GENERATOR_YIELD_ALL_NAME), IR.string("next")),
            IR.name(GENERATOR_NEXT_ARG)));
    Node loopCondition = IR.not(IR.getprop(assignIterResult, IR.string("done")));
    Node elemValue = IR.getprop(IR.name(GENERATOR_YIELD_ALL_ENTRY), IR.string("value"));
    Node yieldStatement = IR.exprResult(IR.yield(elemValue.cloneTree()));
    Node loop = IR.whileNode(loopCondition,
        IR.block(yieldStatement));

    enclosingStatement.getParent().addChildBefore(generator, enclosingStatement);
    enclosingStatement.getParent().addChildBefore(entryDecl, enclosingStatement);
    enclosingStatement.getParent().addChildBefore(loop, enclosingStatement);
    if (parent.isExprResult()) {
      parent.detachFromParent();
    } else {
      parent.replaceChild(n, elemValue);
    }

    visitYieldThrows(yieldStatement, yieldStatement.getParent());
    compiler.reportCodeChange();
  }

  private void visitYieldExpr(Node n, Node parent) {
    Node enclosingStatement = NodeUtil.getEnclosingStatement(n);
    Node yieldStatement = IR.exprResult(
        n.hasChildren() ? IR.yield(n.removeFirstChild()) : IR.yield());
    Node yieldResult = IR.name(GENERATOR_NEXT_ARG + generatorCounter.get());
    Node yieldResultDecl = IR.var(yieldResult.cloneTree(), IR.name(GENERATOR_NEXT_ARG));

    parent.replaceChild(n, yieldResult);
    enclosingStatement.getParent().addChildBefore(yieldStatement, enclosingStatement);
    enclosingStatement.getParent().addChildBefore(yieldResultDecl, enclosingStatement);

    visitYieldThrows(yieldStatement, yieldStatement.getParent());
    compiler.reportCodeChange();
  }

  private void visitGenerator(Node n, Node parent) {
    hasTranslatedTry = false;
    Node genBlock = compiler.parseSyntheticCode(Joiner.on('\n').join(
      "function generatorBody() {",
      "  var " + GENERATOR_STATE + " = " + generatorCaseCount + ";",
      "  function $jscomp$generator$impl(" + GENERATOR_NEXT_ARG + ", ",
      "      " + GENERATOR_THROW_ARG + ") {",
      "    while (1) switch (" + GENERATOR_STATE + ") {",
      "      case " + generatorCaseCount + ":",
      "      default:",
      "        return {value: undefined, done: true};",
      "    }",
      "  }",
      "  return {",
      "    " + ITER_KEY + ": function() { return this; },",
      "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
      "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
      "  }",
      "}"
    )).getFirstChild().getLastChild().detachFromParent();
    generatorCaseCount++;

    originalGeneratorBody = n.getLastChild();
    n.replaceChild(originalGeneratorBody, genBlock);
    n.setIsGeneratorFunction(false);

    //TODO(mattloring): remove this suppression once we can optimize the switch statement to
    // remove unused cases.
    JSDocInfoBuilder builder;
    if (n.getJSDocInfo() == null) {
      builder = new JSDocInfoBuilder(true);
    } else {
      builder = JSDocInfoBuilder.copyFrom(n.getJSDocInfo());
    }
    //TODO(mattloring): copy existing suppressions.
    builder.recordSuppressions(ImmutableSet.of("uselessCode"));
    JSDocInfo info = builder.build(n);
    n.setJSDocInfo(info);


    // Set state to the default after the body of the function has completed.
    originalGeneratorBody.addChildToBack(
        IR.exprResult(IR.assign(IR.name(GENERATOR_STATE), IR.number(-1))));

    enclosingBlock = getUnique(genBlock, Token.CASE).getLastChild();
    hoistRoot = getUnique(genBlock, Token.VAR);

    if (NodeUtil.isNameReferenced(originalGeneratorBody, GENERATOR_ARGUMENTS)) {
      hoistRoot.getParent().addChildAfter(
          IR.var(IR.name(GENERATOR_ARGUMENTS), IR.name("arguments")), hoistRoot);
    }
    if (NodeUtil.isNameReferenced(originalGeneratorBody, GENERATOR_THIS)) {
      hoistRoot.getParent().addChildAfter(
          IR.var(IR.name(GENERATOR_THIS), IR.thisNode()), hoistRoot);
    }

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
        Node oldCase = enclosingBlock.getParent();
        Node newCase = IR.caseNode(IR.number(caseNumber), IR.block());
        enclosingBlock = newCase.getLastChild();
        if (oldCase.isTry()) {
          oldCase = oldCase.getParent().getParent();
          if (!currentExceptionContext.isEmpty()) {
            Node newTry = IR.tryCatch(IR.block(),
                currentExceptionContext.get(0).catchBlock.cloneTree());
            newCase.getLastChild().addChildToBack(newTry);
            enclosingBlock = newCase.getLastChild().getLastChild().getFirstChild();
          }
        }
        oldCase.getParent().addChildAfter(newCase, oldCase);
      }
    }

    parent.useSourceInfoIfMissingFromForTree(parent);
    compiler.reportCodeChange();
  }

  /** Returns true if a new case node should be added */
  private boolean translateStatementInOriginalBody() {
    if (currentStatement.isVar()) {
      visitVar();
      return false;
    } else if (currentStatement.isGeneratorMarker()) {
      visitGeneratorMarker();
      return true;
    } else if (currentStatement.isFunction()) {
      visitFunctionStatement();
      return false;
    } else if (currentStatement.isBlock()) {
        visitBlock();
        return false;
    } else if (controlCanExit(currentStatement)) {
      switch (currentStatement.getType()) {
        case Token.WHILE:
        case Token.DO:
        case Token.FOR:
          if (NodeUtil.isForIn(currentStatement)) {
            visitForIn();
            return false;
          }
          visitLoop(null);
          return false;
        case Token.LABEL:
          visitLabel();
          return false;
        case Token.SWITCH:
          visitSwitch();
          return false;
        case Token.IF:
          if (!currentStatement.isGeneratorSafe()) {
            visitIf();
            return false;
          }
          break;
        case Token.TRY:
          visitTry();
          return false;
        case Token.EXPR_RESULT:
          if (currentStatement.getFirstChild().isYield()) {
            visitYieldExprResult();
            return true;
          }
          break;
        case Token.RETURN:
          visitReturn();
          return false;
        case Token.CONTINUE:
          visitContinue();
          return false;
        case Token.BREAK:
          if (!currentStatement.isGeneratorSafe()) {
            visitBreak();
            return false;
          }
          break;
        case Token.THROW:
          visitThrow();
          return false;
        default:
          // We never want to copy over an untranslated statement for which control exits.
          throw new RuntimeException(
              "Untranslatable control-exiting statement in generator function: "
              + Token.name(currentStatement.getType()));
      }
    }

    // In the default case, add the statement to the current case block unchanged.
    enclosingBlock.addChildToBack(currentStatement);
    return false;
  }

  private void visitFunctionStatement() {
    hoistRoot.getParent().addChildAfter(currentStatement, hoistRoot);
  }

  private void visitTry() {
    Node tryBody = currentStatement.getFirstChild();
    Node caughtError;
    Node catchBody;
    Node catchBlock = tryBody.getNext();
    if (catchBlock.hasChildren()) {
      // There is a catch block
      caughtError = catchBlock.getFirstChild().removeFirstChild();
      catchBody = catchBlock.getFirstChild().removeFirstChild();
    } else {
      caughtError = IR.name(GENERATOR_ERROR + "temp");
      catchBody = IR.block(IR.throwNode(caughtError.cloneTree()));
      catchBody.getFirstChild().setGeneratorSafe(true);
    }
    Node finallyBody = catchBlock.getNext();
    int catchStartState = generatorCaseCount++;
    Node catchStart = IR.number(catchStartState);
    catchStart.setGeneratorMarker(true);

    Node errorNameGenerated = IR.name("$jscomp$generator$" + caughtError.getString());

    originalGeneratorBody.addChildToFront(catchStart);
    originalGeneratorBody.addChildAfter(catchBody, catchStart);

    Node assignError = IR.assign(IR.name(GENERATOR_ERROR), errorNameGenerated.cloneTree());
    Node newCatchBody = IR.block(IR.exprResult(assignError),
        createStateUpdate(catchStartState), createSafeBreak());
    Node newCatch = IR.catchNode(errorNameGenerated, newCatchBody);

    currentExceptionContext.add(0, new ExceptionContext(catchStartState, newCatch));

    if (finallyBody != null) {
      Node finallyName = IR.name(GENERATOR_FINALLY_JUMP + generatorCounter.get());
      int finallyStartState = generatorCaseCount++;
      Node finallyStart = IR.number(finallyStartState);
      finallyStart.setGeneratorMarker(true);
      int finallyEndState = generatorCaseCount++;
      Node finallyEnd = IR.number(finallyEndState);
      finallyEnd.setGeneratorMarker(true);

      NodeTraversal.traverse(compiler,
          tryBody,
          new ControlExitsCheck(finallyName, finallyStartState));
      NodeTraversal.traverse(compiler, catchBody,
          new ControlExitsCheck(finallyName, finallyStartState));
      originalGeneratorBody.addChildToFront(tryBody.detachFromParent());

      originalGeneratorBody.addChildAfter(finallyStart, catchBody);
      originalGeneratorBody.addChildAfter(finallyBody.detachFromParent(), finallyStart);
      originalGeneratorBody.addChildAfter(finallyEnd, finallyBody);
      originalGeneratorBody.addChildToFront(IR.var(finallyName.cloneTree()));

      finallyBody.addChildToBack(IR.exprResult(
          IR.assign(IR.name(GENERATOR_STATE), finallyName.cloneTree())));
      finallyBody.addChildToBack(createSafeBreak());
      tryBody.addChildToBack(IR.exprResult(
          IR.assign(finallyName.cloneTree(), IR.number(finallyEndState))));
      tryBody.addChildToBack(createStateUpdate(finallyStartState));
      tryBody.addChildToBack(createSafeBreak());
      catchBody.addChildToBack(IR.exprResult(
          IR.assign(finallyName.cloneTree(), IR.number(finallyEndState))));
    } else {
      int catchEndState = generatorCaseCount++;
      Node catchEnd = IR.number(catchEndState);
      catchEnd.setGeneratorMarker(true);
      originalGeneratorBody.addChildAfter(catchEnd, catchBody);
      tryBody.addChildToBack(createStateUpdate(catchEndState));
      tryBody.addChildToBack(createSafeBreak());
      originalGeneratorBody.addChildToFront(tryBody.detachFromParent());
    }

    catchBody.addChildToFront(IR.var(caughtError, IR.name(GENERATOR_ERROR)));

    if (enclosingBlock.getParent().isTry()) {
      enclosingBlock = enclosingBlock.getParent().getParent();
    }

    enclosingBlock.addChildToBack(
        IR.tryCatch(IR.block(), newCatch));
    enclosingBlock = enclosingBlock.getLastChild().getFirstChild();
    if (!hasTranslatedTry) {
      hasTranslatedTry = true;
      hoistRoot.getParent().addChildAfter(IR.var(IR.name(GENERATOR_ERROR)), hoistRoot);
    }
  }

  private void visitContinue() {
    Preconditions.checkState(currentLoopContext.get(0).continueCase != -1);
    int continueCase;
    if (currentStatement.hasChildren()) {
      continueCase = getLoopContext(
          currentStatement.removeFirstChild().getString()).continueCase;
    } else {
      continueCase = currentLoopContext.get(0).continueCase;
    }
    enclosingBlock.addChildToBack(
        createStateUpdate(continueCase));
    enclosingBlock.addChildToBack(createSafeBreak());
  }

  private void visitThrow() {
    enclosingBlock.addChildToBack(createStateUpdate(-1));
    enclosingBlock.addChildToBack(currentStatement);
  }

  private void visitBreak() {
    int breakCase;
    if (currentStatement.hasChildren()) {
      LoopContext loop = getLoopContext(currentStatement.removeFirstChild().getString());
      if (loop == null) {
        compiler.report(JSError.make(currentStatement, Es6ToEs3Converter.CANNOT_CONVERT_YET,
          "Breaking to a label that is not a loop"));
        return;
      }
      breakCase = loop.breakCase;
    } else {
      breakCase = currentLoopContext.get(0).breakCase;
    }
    enclosingBlock.addChildToBack(
        createStateUpdate(breakCase));
    enclosingBlock.addChildToBack(createSafeBreak());
  }

  private void visitLabel() {
    Node labelName = currentStatement.removeFirstChild();
    Node child = currentStatement.removeFirstChild();
    if (NodeUtil.isLoopStructure(child)) {
      currentStatement = child;
      visitLoop(labelName.getString());
    } else {
      originalGeneratorBody.addChildToFront(child);
    }
  }

  /**
   * If we reach the marker cooresponding to the end of the current loop,
   * pop the loop information off of our stack.
   */
  private void visitGeneratorMarker() {
    if (!currentLoopContext.isEmpty()
        && currentLoopContext.get(0).breakCase == currentStatement.getDouble()) {
      currentLoopContext.remove(0);
    }
    if (!currentExceptionContext.isEmpty()
        && currentExceptionContext.get(0).catchStartCase == currentStatement.getDouble()) {
      currentExceptionContext.remove(0);
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

    Node invertedConditional = IR.ifNode(IR.not(condition),
        IR.block(createStateUpdate(ifEndState), createSafeBreak()));
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
      ifBody.addChildToBack(createSafeBreak());
      originalGeneratorBody.addChildAfter(elseBlock, endIf);
      originalGeneratorBody.addChildAfter(endElse, elseBlock);
    }
  }

  /**
   * Switch statements are translated into a series of if statements.
   *
   * <code>
   * switch (i) {
   *   case 1:
   *     s;
   *   case 2:
   *     t;
   *   ...
   * }
   * </code>
   *
   * is eventually rewritten to:
   *
   * <code>
   * $jscomp$generator$switch$entered0 = false;
   * if ($jscomp$generator$switch$entered0 || i == 1) {
   *   $jscomp$generator$switch$entered0 = true;
   *   s;
   * }
   * if ($jscomp$generator$switch$entered0 || i == 2) {
   *   $jscomp$generator$switch$entered0 = true;
   *   t;
   * }
   * ...
   *
   * </code>
   */
  private void visitSwitch() {
    Node didEnter = IR.name(GENERATOR_SWITCH_ENTERED + generatorCounter.get());
    Node didEnterDecl = IR.var(didEnter.cloneTree(), IR.falseNode());
    Node switchVal = IR.name(GENERATOR_SWITCH_VAL + generatorCounter.get());
    Node switchValDecl = IR.var(switchVal.cloneTree(), currentStatement.removeFirstChild());
    originalGeneratorBody.addChildToFront(didEnterDecl);
    originalGeneratorBody.addChildAfter(switchValDecl, didEnterDecl);
    Node insertionPoint = switchValDecl;

    while (currentStatement.hasChildren()) {
      Node currCase = currentStatement.removeFirstChild();
      Node equivBlock;
      currCase.getLastChild().addChildToFront(
          IR.exprResult(IR.assign(didEnter.cloneTree(), IR.trueNode())));
      if (currCase.isDefaultCase()) {
        if (currentStatement.hasChildren()) {
          compiler.report(JSError.make(currentStatement, Es6ToEs3Converter.CANNOT_CONVERT_YET,
            "Default case as intermediate case"));
        }
        equivBlock = IR.block(currCase.removeFirstChild());
      } else {
        equivBlock = IR.ifNode(IR.or(didEnter.cloneTree(),
            IR.sheq(switchVal.cloneTree(), currCase.removeFirstChild())),
          currCase.removeFirstChild());
      }
      originalGeneratorBody.addChildAfter(equivBlock, insertionPoint);
      insertionPoint = equivBlock;
    }

    int breakTarget = generatorCaseCount++;
    int cont = currentLoopContext.isEmpty() ? -1 : currentLoopContext.get(0).continueCase;
    currentLoopContext.add(0, new LoopContext(breakTarget, cont, null));
    Node breakCase = IR.number(breakTarget);
    breakCase.setGeneratorMarker(true);
    originalGeneratorBody.addChildAfter(breakCase, insertionPoint);
  }

  /**
   * Blocks are flattened by lifting all children to the body of the original generator.
   */
  private void visitBlock() {
    if (currentStatement.getChildCount() == 0) {
      return;
    }
    Node insertionPoint = currentStatement.removeFirstChild();
    originalGeneratorBody.addChildToFront(insertionPoint);
    for (Node child = currentStatement.removeFirstChild(); child != null;
        child = currentStatement.removeFirstChild()) {
      originalGeneratorBody.addChildAfter(child, insertionPoint);
      insertionPoint = child;
    }
  }

  /**
   * For in loops are eventually translated to a for in loop which produces an array of
   * values iterated over followed by a plain for loop which performs the logic
   * contained in the body of the original for in.
   *
   * <code>
   * for (i in j) {
   *   s;
   * }
   * </code>
   *
   * is eventually rewritten to:
   *
   * <code>
   * $jscomp$arr = []
   * $jscomp$iter = j
   * for (i in $jscomp$iter) {
   *   $jscomp$arr.push(i);
   * }
   * for ($jscomp$var = 0; $jscomp$var < $jscomp$arr.length; $jscomp$var++) {
   *   i = $jscomp$arr[$jscomp$var];
   *   if (!(i in $jscomp$iter)) {
   *     continue;
   *   }
   *   s;
   * }
   * </code>
   */
  private void visitForIn() {
    Node variable = currentStatement.removeFirstChild();
    Node iterable = currentStatement.removeFirstChild();
    Node body = currentStatement.removeFirstChild();

    String loopId = generatorCounter.get();
    Node arrayName = IR.name(GENERATOR_FOR_IN_ARRAY + loopId);
    Node varName = IR.name(GENERATOR_FOR_IN_VAR + loopId);
    Node iterableName = IR.name(GENERATOR_FOR_IN_ITER + loopId);

    if (variable.isVar()) {
      variable = variable.removeFirstChild();
    }
    body.addChildToFront(IR.ifNode(
        IR.not(IR.in(variable.cloneTree(), iterableName.cloneTree())),
        IR.block(IR.continueNode())));
    body.addChildToFront(IR.var(variable.cloneTree(),
        IR.getelem(arrayName.cloneTree(), varName.cloneTree())));
    hoistRoot.getParent().addChildAfter(IR.var(arrayName.cloneTree()), hoistRoot);
    hoistRoot.getParent().addChildAfter(IR.var(varName.cloneTree()), hoistRoot);
    hoistRoot.getParent().addChildAfter(IR.var(iterableName.cloneTree()), hoistRoot);

    Node arrayDef = IR.exprResult(IR.assign(arrayName.cloneTree(), IR.arraylit()));
    Node iterDef = IR.exprResult(IR.assign(iterableName.cloneTree(), iterable));
    Node newForIn = IR.forIn(variable.cloneTree(), iterableName,
        IR.block(IR.exprResult(
            IR.call(IR.getprop(arrayName.cloneTree(), IR.string("push")),
                variable))));
    Node newFor = IR.forNode(IR.assign(varName.cloneTree(), IR.number(0)),
        IR.lt(varName.cloneTree(), IR.getprop(arrayName, IR.string("length"))),
        IR.inc(varName, true),
        body);

    enclosingBlock.addChildToBack(arrayDef);
    enclosingBlock.addChildToBack(iterDef);
    enclosingBlock.addChildToBack(newForIn);
    originalGeneratorBody.addChildToFront(newFor);
  }

  /**
   * Loops are eventually translated to a case statement followed by an if statement
   * containing the loop body. The if statement finishes by
   * jumping back to the initial case statement to enter the loop again.
   * In the case of for and do loops, initialization and post loop statements are inserted
   * before and after the if statement. Below is a sample translation for a while loop:
   *
   * <code>
   * while (b) {
   *   s;
   * }
   * </code>
   *
   * is eventually rewritten to:
   *
   * <code>
   * case n:
   *   if (b) {
   *     s;
   *     state = n;
   *     break;
   *   }
   * </code>
   */
  private void visitLoop(String label) {
    Node initializer;
    Node guard;
    Node incr;
    Node body;

    if (currentStatement.isWhile()) {
      guard = currentStatement.removeFirstChild();
      body = currentStatement.removeFirstChild();
      initializer = IR.empty();
      incr = IR.empty();
    } else if (currentStatement.isFor()) {
      initializer = currentStatement.removeFirstChild();
      if (initializer.isAssign()) {
        initializer = IR.exprResult(initializer);
      }
      guard = currentStatement.removeFirstChild();
      incr = currentStatement.removeFirstChild();
      body = currentStatement.removeFirstChild();
    } else {
      Preconditions.checkState(currentStatement.isDo());
      initializer = IR.empty();
      incr = IR.assign(IR.name(GENERATOR_DO_WHILE_INITIAL), IR.falseNode());

      body = currentStatement.removeFirstChild();
      guard = currentStatement.removeFirstChild();
    }

    Node condition, prestatement;

    if (guard.isBlock()) {
      prestatement = guard.removeFirstChild();
      condition = guard.removeFirstChild();
    } else {
      prestatement = IR.block();
      condition = guard;
    }

    int loopBeginState = generatorCaseCount++;
    int continueState = loopBeginState;

    if (!incr.isEmpty()) {
      continueState = generatorCaseCount++;
      Node continueCase = IR.number(continueState);
      continueCase.setGeneratorMarker(true);
      body.addChildToBack(continueCase);
      body.addChildToBack(incr.isBlock() ? incr : IR.exprResult(incr));
    }

    currentLoopContext.add(0, new LoopContext(generatorCaseCount, continueState, label));

    Node beginCase = IR.number(loopBeginState);
    beginCase.setGeneratorMarker(true);
    Node conditionalBranch = IR.ifNode(condition.isEmpty() ? IR.trueNode() : condition, body);
    Node setStateLoopStart = createStateUpdate(loopBeginState);
    Node breakToStart = createSafeBreak();

    originalGeneratorBody.addChildToFront(conditionalBranch);
    if (!prestatement.isEmpty()) {
      originalGeneratorBody.addChildToFront(prestatement);
    }
    originalGeneratorBody.addChildToFront(beginCase);
    if (!initializer.isEmpty()) {
      originalGeneratorBody.addChildToFront(initializer);
    }
    body.addChildToBack(setStateLoopStart);
    body.addChildToBack(breakToStart);
  }

  /**
   * {@code var} statements are hoisted into the closure containing the iterator
   * to preserve their state across
   * multiple calls to next().
   */
  private void visitVar() {
    Node name = currentStatement.removeFirstChild();
    while (name != null) {
      if (name.hasChildren()) {
        enclosingBlock.addChildToBack(
            IR.exprResult(IR.assign(name, name.removeFirstChild())));
      }
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
    enclosingBlock.addChildToBack(createStateUpdate());
    Node yield = currentStatement.getFirstChild();
    Node value = yield.hasChildren() ? yield.removeFirstChild() : IR.name("undefined");
    enclosingBlock.addChildToBack(IR.returnNode(
        createIteratorResult(value, false)));
  }

  /**
   * {@code return} statements are translated to set the state to done before returning the
   * desired value.
   */
  private void visitReturn() {
    enclosingBlock.addChildToBack(createStateUpdate(-1));
    enclosingBlock.addChildToBack(IR.returnNode(
        createIteratorResult(currentStatement.hasChildren() ? currentStatement.removeFirstChild()
            : IR.name("undefined"), true)));
  }

  private static Node createStateUpdate() {
    return IR.exprResult(
        IR.assign(IR.name(GENERATOR_STATE), IR.number(generatorCaseCount)));
  }

  private static Node createStateUpdate(int state) {
    return IR.exprResult(
        IR.assign(IR.name(GENERATOR_STATE), IR.number(state)));
  }

  private static Node createIteratorResult(Node value, boolean done) {
    return IR.objectlit(
        IR.propdef(IR.stringKey("value"), value),
        IR.propdef(IR.stringKey("done"), done ? IR.trueNode() : IR.falseNode()));
  }

  private static Node createSafeBreak() {
    Node breakNode = IR.breakNode();
    breakNode.setGeneratorSafe(true);
    return breakNode;
  }

  private static Node createFinallyJumpBlock(Node finallyName, int finallyStartState) {
    int jumpPoint = generatorCaseCount++;
    Node setReturnState =  IR.exprResult(
        IR.assign(finallyName.cloneTree(), IR.number(jumpPoint)));
    Node toFinally = createStateUpdate(finallyStartState);
    Node returnPoint = IR.number(jumpPoint);
    returnPoint.setGeneratorMarker(true);
    Node returnBlock = IR.block(setReturnState, toFinally, createSafeBreak());
    returnBlock.addChildToBack(returnPoint);
    return returnBlock;
  }

  private LoopContext getLoopContext(String label) {
    for (int i = 0; i < currentLoopContext.size(); i++) {
      if (label.equals(currentLoopContext.get(i).label)) {
        return currentLoopContext.get(i);
      }
    }
    return null;
  }

  private boolean controlCanExit(Node n) {
    ControlExitsCheck exits = new ControlExitsCheck();
    NodeTraversal.traverse(compiler, n, exits);
    return exits.didExit();
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

  /**
   * Decompose expressions with yields inside of them to equivalent
   * sequence of expressions in which all non-statement yields are
   * of the form:
   * <pre>
   *   var name = yield expr;
   * </pre>
   *
   * For example, change the following code:
   * <pre>
   *   return x || yield y;
   * </pre>
   * into
   * <pre>
   *  var temp$$0;
   *  if (temp$$0 = x); else temp$$0 = yield y;
   *  return temp$$0
   * </pre>
   *
   * This uses the {@link #ExpressionDecomposer} class
   */
  class DecomposeYields extends NodeTraversal.AbstractPreOrderCallback {

    private final AbstractCompiler compiler;

    private final ExpressionDecomposer decomposer;

    public DecomposeYields(AbstractCompiler compiler) {
      this.compiler = compiler;
      Set<String> consts = new HashSet<>();
      decomposer = new ExpressionDecomposer(
        compiler, compiler.getUniqueNameIdSupplier(), consts,
          Scope.createGlobalScope(new Node(Token.SCRIPT)));
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.YIELD:
          visitYieldExpression(n);
          break;
        case Token.DO:
        case Token.FOR:
        case Token.WHILE:
          visitLoop(n);
          break;
        case Token.CASE:
          if (controlCanExit(n.getFirstChild())) {
            compiler.report(JSError.make(currentStatement, Es6ToEs3Converter.CANNOT_CONVERT_YET,
              "Case statements that contain yields"));
            return false;
          }
          break;
      }
      return true;
    }

    private void visitYieldExpression(Node n) {
      if (n.getParent().isExprResult()) {
        return;
      }
      if (decomposer.canExposeExpression(n)
          != ExpressionDecomposer.DecompositionType.UNDECOMPOSABLE) {
        decomposer.exposeExpression(n);
        compiler.reportCodeChange();
      } else {
        compiler.report(JSError.make(currentStatement, Es6ToEs3Converter.CANNOT_CONVERT,
          "Undecomposable expression"));
      }
    }

    private void visitLoop(Node n) {
      Node enclosingFunc = NodeUtil.getEnclosingFunction(n);
      if (enclosingFunc  == null || !enclosingFunc.isGeneratorFunction()
          || NodeUtil.isForIn(n)) {
        return;
      }
      Node enclosingBlock = NodeUtil.getEnclosingType(n, Token.BLOCK);
      Node guard = null, incr = null;
      switch (n.getType()) {
        case Token.FOR:
          guard = n.getFirstChild().getNext();
          incr = guard.getNext();
          break;
        case Token.WHILE:
          guard = n.getFirstChild();
          incr = IR.empty();
          break;
        case Token.DO:
          guard = n.getLastChild();
          if (!guard.isEmpty()) {
            Node firstEntry = IR.name(GENERATOR_DO_WHILE_INITIAL);
            enclosingBlock.addChildToFront(IR.var(firstEntry.cloneTree(), IR.trueNode()));
            guard = IR.or(firstEntry, n.getLastChild().detachFromParent());
            n.addChildToBack(guard);
          }
          incr = IR.empty();
          break;
      }
      if (!controlCanExit(guard) && !controlCanExit(incr)) {
        return;
      }
      Node guardName = IR.name(GENERATOR_LOOP_GUARD + generatorCounter.get());
      if (!guard.isEmpty()) {
        Node container = new Node(Token.BLOCK);
        n.replaceChild(guard, container);
        container.addChildToFront(IR.block(IR.exprResult(IR.assign(
          guardName.cloneTree(), guard.cloneTree()))));
        container.addChildToBack(guardName.cloneTree());
      }
      if (!incr.isEmpty()) {
        n.addChildBefore(IR.block(IR.exprResult(incr.detachFromParent())), n.getLastChild());
      }
      enclosingBlock.addChildToFront(IR.var(guardName));
      compiler.reportCodeChange();
    }
  }

  class ControlExitsCheck implements NodeTraversal.Callback {
    int continueCatchers = 0;
    int breakCatchers = 0;
    int throwCatchers = 0;
    List<String> labels = new ArrayList<>();
    boolean exited = false;

    boolean addJumps = false;
    private Node finallyName;
    private int finallyStartState;

    public ControlExitsCheck(Node finallyName, int finallyStartState) {
      this.finallyName = finallyName;
      this.finallyStartState = finallyStartState;
      addJumps = true;
    }

    public ControlExitsCheck() {
      addJumps = false;
    }

    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      switch (n.getType()) {
        case Token.FUNCTION:
          return false;
        case Token.LABEL:
          labels.add(0, n.getFirstChild().getString());
          break;
        case Token.DO:
        case Token.WHILE:
        case Token.FOR:
          continueCatchers++;
          breakCatchers++;
          break;
        case Token.SWITCH:
          breakCatchers++;
          break;
        case Token.BLOCK:
          parent = n.getParent();
          if (parent != null && parent.isTry() && parent.getFirstChild() == n
              && n.getNext().hasChildren()) {
            throwCatchers++;
          }
          break;
        case Token.BREAK:
          if (!n.isGeneratorSafe()
              && ((breakCatchers == 0 && !n.hasChildren()) || (n.hasChildren()
                  && !labels.contains(n.getFirstChild().getString())))) {
            exited = true;
            if (addJumps) {
              parent.addChildBefore(createFinallyJumpBlock(finallyName, finallyStartState), n);
            }
          }
          break;
        case Token.CONTINUE:
          if (continueCatchers == 0 || (n.hasChildren()
              && !labels.contains(n.getFirstChild().getString()))) {
            exited = true;
            if (addJumps) {
              parent.addChildBefore(createFinallyJumpBlock(finallyName, finallyStartState), n);
            }
          }
          break;
        case Token.THROW:
          if (throwCatchers == 0) {
            exited = true;
            if (addJumps && !n.isGeneratorSafe()) {
              parent.addChildBefore(createFinallyJumpBlock(finallyName, finallyStartState), n);
            }
          }
          break;
        case Token.RETURN:
          exited = true;
          if (addJumps) {
            parent.addChildBefore(createFinallyJumpBlock(finallyName, finallyStartState), n);
          }
          break;
        case Token.YIELD:
          exited = true;
          break;
      }
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.LABEL:
          labels.remove(0);
          break;
        case Token.DO:
        case Token.WHILE:
        case Token.FOR:
          continueCatchers--;
          breakCatchers--;
          break;
        case Token.SWITCH:
          breakCatchers--;
          break;
        case Token.BLOCK:
          parent = n.getParent();
          if (parent != null && parent.isTry() && parent.getFirstChild() == n
              && n.getNext().hasChildren()) {
            throwCatchers--;
          }
          break;
      }
    }

    public boolean didExit() {
      return exited;
    }
  }

  private class LoopContext {

    int breakCase;
    int continueCase;
    String label;

    public LoopContext(int breakCase, int continueCase, String label) {
      this.breakCase = breakCase;
      this.continueCase = continueCase;
      this.label = label;
    }

  }

  private class ExceptionContext {
    int catchStartCase;
    Node catchBlock;

    public ExceptionContext(int catchStartCase, Node catchBlock) {
      this.catchStartCase = catchStartCase;
      this.catchBlock = catchBlock;
    }
  }

}
