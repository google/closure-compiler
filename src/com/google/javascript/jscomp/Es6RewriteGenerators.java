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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.Es6ToEs3Util.createType;
import static com.google.javascript.jscomp.Es6ToEs3Util.makeIterator;
import static com.google.javascript.jscomp.Es6ToEs3Util.withType;

import com.google.common.base.Joiner;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.AbstractCompiler.MostRecentTypechecker;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.FunctionTypeI;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.ObjectTypeI;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TypeI;
import com.google.javascript.rhino.TypeIRegistry;
import com.google.javascript.rhino.jstype.JSTypeNative;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Converts ES6 generator functions to valid ES3 code. This pass runs after all ES6 features
 * except for yield and generators have been transpiled.
 *
 * @author mattloring@google.com (Matthew Loring)
 */
public final class Es6RewriteGenerators
    extends NodeTraversal.AbstractPostOrderCallback implements HotSwapCompilerPass {

  static final String GENERATOR_PRELOAD_FUNCTION_NAME = "$jscomp$generator$function$name";

  // Name of the variable that holds the state at which the generator
  // should resume execution after a call to yield or return.
  // The beginning state is 0 and the end state is -1.
  private static final String GENERATOR_STATE = "$jscomp$generator$state";
  private static final String GENERATOR_DO_WHILE_INITIAL = "$jscomp$generator$first$do";
  private static final String GENERATOR_YIELD_ALL_NAME = "$jscomp$generator$yield$all";
  private static final String GENERATOR_YIELD_ALL_ENTRY = "$jscomp$generator$yield$entry";
  private static final String GENERATOR_ARGUMENTS = "$jscomp$generator$arguments";
  private static final String GENERATOR_THIS = "$jscomp$generator$this";
  private static final String GENERATOR_ACTION_ARG = "$jscomp$generator$action$arg";
  private static final double GENERATOR_ACTION_NEXT = 0;
  private static final double GENERATOR_ACTION_THROW = 1;
  private static final String GENERATOR_NEXT_ARG = "$jscomp$generator$next$arg";
  private static final String GENERATOR_THROW_ARG = "$jscomp$generator$throw$arg";
  private static final String GENERATOR_SWITCH_ENTERED = "$jscomp$generator$switch$entered";
  private static final String GENERATOR_SWITCH_VAL = "$jscomp$generator$switch$val";
  private static final String GENERATOR_FINALLY_JUMP = "$jscomp$generator$finally";
  private static final String GENERATOR_ERROR = "$jscomp$generator$global$error";
  private static final String GENERATOR_FOR_IN_ARRAY = "$jscomp$generator$forin$array";
  private static final String GENERATOR_FOR_IN_VAR = "$jscomp$generator$forin$var";
  private static final String GENERATOR_FOR_IN_ITER = "$jscomp$generator$forin$iter";
  private static final String GENERATOR_LOOP_GUARD = "$jscomp$generator$loop$guard";

  private final AbstractCompiler compiler;
  private static final FeatureSet transpiledFeatures =
      FeatureSet.BARE_MINIMUM.with(Feature.GENERATORS);

  // Maintains a stack of numbers which identify the cases which mark the end of loops. These
  // are used to manage jump destinations for break and continue statements.
  private final List<LoopContext> currentLoopContext;

  private final List<ExceptionContext> currentExceptionContext;

  private static int generatorCaseCount;

  private final Supplier<String> generatorCounter;

  // Current case statement onto which translated statements from the
  // body of a generator will be appended.
  private Node enclosingBlock;

  // Destination for vars defined in the body of a generator.
  private Node hoistRoot;

  // Body of the generator function currently being translated.
  private Node originalGeneratorBody;

  // Current statement being translated.
  private Node currentStatement;

  private boolean hasTranslatedTry;

  // Whether we should preserve type information during transpilation.
  private final boolean addTypes;

  private final TypeIRegistry registry;

  private final TypeI unknownType;
  private final TypeI undefinedType;
  private final TypeI stringType;
  private final TypeI booleanType;
  private final TypeI falseType;
  private final TypeI trueType;
  private final TypeI numberType;

  public Es6RewriteGenerators(AbstractCompiler compiler) {
    checkNotNull(compiler);
    this.compiler = compiler;
    this.currentLoopContext = new ArrayList<>();
    this.currentExceptionContext = new ArrayList<>();
    generatorCounter = compiler.getUniqueNameIdSupplier();
    this.addTypes = MostRecentTypechecker.NTI.equals(compiler.getMostRecentTypechecker());
    this.registry = compiler.getTypeIRegistry();
    this.unknownType = createType(addTypes, registry, JSTypeNative.UNKNOWN_TYPE);
    this.undefinedType = createType(addTypes, registry, JSTypeNative.VOID_TYPE);
    this.stringType = createType(addTypes, registry, JSTypeNative.STRING_TYPE);
    this.booleanType = createType(addTypes, registry, JSTypeNative.BOOLEAN_TYPE);
    this.falseType = createType(addTypes, registry, JSTypeNative.FALSE_TYPE);
    this.trueType = createType(addTypes, registry, JSTypeNative.TRUE_TYPE);
    this.numberType = createType(addTypes, registry, JSTypeNative.NUMBER_TYPE);
  }

  @Override
  public void process(Node externs, Node root) {
    // Report change only if the generator function is preloaded. See #cleanUpGeneratorSkeleton.
    boolean reportChange = getPreloadedGeneratorFunc(compiler.getJsRoot()) != null;
    TranspilationPasses.processTranspile(
        compiler, root, transpiledFeatures, new DecomposeYields(compiler), this);
    cleanUpGeneratorSkeleton(reportChange);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(
        compiler, scriptRoot, transpiledFeatures, new DecomposeYields(compiler), this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case FUNCTION:
        if (n.isGeneratorFunction()) {
          generatorCaseCount = 0;
          visitGenerator(n, parent);
        }
        break;
      case NAME:
        Node enclosing = NodeUtil.getEnclosingFunction(n);
        if (enclosing != null
            && enclosing.isGeneratorFunction()
            && n.matchesQualifiedName("arguments")) {
          n.setString(GENERATOR_ARGUMENTS);
        }
        break;
      case THIS:
        enclosing = NodeUtil.getEnclosingFunction(n);
        if (enclosing != null && enclosing.isGeneratorFunction()) {
          n.replaceWith(withType(IR.name(GENERATOR_THIS), n.getTypeI()));
        }
        break;
      case YIELD:
        if (n.isYieldAll()) {
          visitYieldAll(t, n, parent);
        } else if (!parent.isExprResult()) {
          visitYieldExpr(t, n, parent);
        } else {
          visitYieldThrows(t, parent, parent.getParent());
        }
        break;
      default:
        break;
    }
  }

  private void visitYieldThrows(NodeTraversal t, Node n, Node parent) {
    Node ifThrows =
        IR.ifNode(
            withBooleanType(
                IR.eq(
                    withNumberType(IR.name(GENERATOR_ACTION_ARG)),
                    withNumberType(IR.number(GENERATOR_ACTION_THROW)))),
            IR.block(IR.throwNode(withUnknownType(IR.name(GENERATOR_THROW_ARG)))));
    parent.addChildAfter(ifThrows, n);
    t.reportCodeChange();
  }

  /**
   * Translates expressions using the new yield-for syntax.
   *
   * <p>Sample translation:
   *
   * <pre>
   * var i = yield * gen();
   * </pre>
   *
   * <p>Is rewritten to:
   *
   * <pre>
   * var $jscomp$generator$yield$all = gen();
   * var $jscomp$generator$yield$entry;
   * while (!($jscomp$generator$yield$entry =
   *     $jscomp$generator$yield$all.next($jscomp$generator$next$arg)).done) {
   *   yield $jscomp$generator$yield$entry.value;
   * }
   * var i = $jscomp$generator$yield$entry.value;
   * </pre>
   */
  private void visitYieldAll(NodeTraversal t, Node n, Node parent) {
    ObjectTypeI yieldAllType = null;
    TypeI typeParam = unknownType;
    if (addTypes) {
      yieldAllType = n.getFirstChild().getTypeI().autobox().toMaybeObjectType();
      typeParam = yieldAllType.getTemplateTypes().get(0);
    }
    TypeI iteratorType = createGenericType(JSTypeNative.ITERATOR_TYPE, typeParam);
    TypeI iteratorNextType =
        addTypes ? iteratorType.toMaybeObjectType().getPropertyType("next") : null;
    TypeI iIterableResultType = createGenericType(JSTypeNative.I_ITERABLE_RESULT_TYPE, typeParam);
    TypeI iIterableResultDoneType =
        addTypes ? iIterableResultType.toMaybeObjectType().getPropertyType("done") : null;
    TypeI iIterableResultValueType =
        addTypes ? iIterableResultType.toMaybeObjectType().getPropertyType("value") : null;

    Node enclosingStatement = NodeUtil.getEnclosingStatement(n);
    Node iterator = makeIterator(compiler, n.removeFirstChild());
    if (addTypes) {
      TypeI jscompType = t.getScope().getVar("$jscomp").getNode().getTypeI();
      TypeI makeIteratorType = jscompType.toMaybeObjectType().getPropertyType("makeIterator");
      iterator.getFirstChild().setTypeI(makeIteratorType);
      iterator.getFirstFirstChild().setTypeI(jscompType);
    }
    Node generator =
        IR.var(
            withType(IR.name(GENERATOR_YIELD_ALL_NAME), iteratorType),
            withType(iterator, iteratorType));
    Node entryDecl = IR.var(withType(IR.name(GENERATOR_YIELD_ALL_ENTRY), iIterableResultType));
    Node assignIterResult =
        withType(
            IR.assign(
                withType(IR.name(GENERATOR_YIELD_ALL_ENTRY), iIterableResultType),
                withType(
                    IR.call(
                        withType(
                            IR.getprop(
                                withType(IR.name(GENERATOR_YIELD_ALL_NAME), iteratorType),
                                withStringType(IR.string("next"))),
                            iteratorNextType),
                        withUnknownType(IR.name(GENERATOR_NEXT_ARG))),
                    iIterableResultType)),
            iIterableResultType);
    Node loopCondition =
        withBooleanType(
            IR.not(
                withType(
                    IR.getprop(assignIterResult, withStringType(IR.string("done"))),
                    iIterableResultDoneType)));
    Node elemValue =
        withType(
            IR.getprop(
                withType(IR.name(GENERATOR_YIELD_ALL_ENTRY), iIterableResultType),
                withStringType(IR.string("value"))),
            iIterableResultValueType);
    Node yieldStatement = IR.exprResult(withUnknownType(IR.yield(elemValue.cloneTree())));
    Node loop = IR.whileNode(loopCondition, IR.block(yieldStatement));

    enclosingStatement.getParent().addChildBefore(generator, enclosingStatement);
    enclosingStatement.getParent().addChildBefore(entryDecl, enclosingStatement);
    enclosingStatement.getParent().addChildBefore(loop, enclosingStatement);
    if (parent.isExprResult()) {
      parent.detach();
    } else {
      parent.replaceChild(n, elemValue);
    }

    visitYieldThrows(t, yieldStatement, yieldStatement.getParent());
    t.reportCodeChange();
  }

  private void visitYieldExpr(NodeTraversal t, Node n, Node parent) {
    Node enclosingStatement = NodeUtil.getEnclosingStatement(n);
    Node yieldStatement =
        IR.exprResult(
            n.hasChildren()
                ? withType(IR.yield(n.removeFirstChild()), n.getTypeI())
                : withType(IR.yield(), n.getTypeI()));
    Node yieldResult = withUnknownType(IR.name(GENERATOR_NEXT_ARG + generatorCounter.get()));
    Node yieldResultDecl =
        IR.var(yieldResult.cloneTree(), withUnknownType(IR.name(GENERATOR_NEXT_ARG)));

    parent.replaceChild(n, yieldResult);
    enclosingStatement.getParent().addChildBefore(yieldStatement, enclosingStatement);
    enclosingStatement.getParent().addChildBefore(yieldResultDecl, enclosingStatement);

    visitYieldThrows(t, yieldStatement, yieldStatement.getParent());
    t.reportCodeChange();
  }

  private void visitGenerator(Node n, Node parent) {
    Es6ToEs3Util.preloadEs6Symbol(compiler);
    hasTranslatedTry = false;
    Node genBlock = preloadGeneratorSkeleton(compiler, false).getLastChild().cloneTree();
    generatorCaseCount++;

    originalGeneratorBody = n.getLastChild();
    n.replaceChild(originalGeneratorBody, genBlock);
    NodeUtil.markNewScopesChanged(genBlock, compiler);
    n.setIsGeneratorFunction(false);

    TypeI generatorFuncType = n.getTypeI();
    TypeI generatorReturnType =
        addTypes ? generatorFuncType.toMaybeFunctionType().getReturnType() : null;
    TypeI yieldType = unknownType;
    if (addTypes) {
      if (generatorReturnType.isGenericObjectType()) {
        yieldType = generatorReturnType.autobox().toMaybeObjectType().getTemplateTypes().get(0);
      }
      addTypesToGeneratorSkeleton(genBlock, yieldType);
    }

    // TODO(mattloring): remove this suppression once we can optimize the switch statement to
    // remove unused cases.
    JSDocInfoBuilder builder = JSDocInfoBuilder.maybeCopyFrom(n.getJSDocInfo());
    // TODO(mattloring): copy existing suppressions.
    builder.recordSuppressions(ImmutableSet.of("uselessCode"));
    JSDocInfo info = builder.build();
    n.setJSDocInfo(info);

    // Set state to the default after the body of the function has completed.
    originalGeneratorBody.addChildToBack(
        IR.exprResult(
            withNumberType(
                IR.assign(
                    withNumberType(IR.name(GENERATOR_STATE)), withNumberType(IR.number(-1))))));

    enclosingBlock = getUnique(genBlock, Token.CASE).getLastChild();
    hoistRoot = genBlock.getFirstChild();

    if (NodeUtil.isNameReferenced(originalGeneratorBody, GENERATOR_ARGUMENTS)) {
      hoistRoot
          .getParent()
          .addChildAfter(
              IR.var(
                  withUnknownType(IR.name(GENERATOR_ARGUMENTS)),
                  withUnknownType(IR.name("arguments"))),
              hoistRoot);
    }
    if (NodeUtil.isNameReferenced(originalGeneratorBody, GENERATOR_THIS)) {
      hoistRoot
          .getParent()
          .addChildAfter(
              IR.var(withUnknownType(IR.name(GENERATOR_THIS)), withUnknownType(IR.thisNode())),
              hoistRoot);
    }

    while (originalGeneratorBody.hasChildren()) {
      currentStatement = originalGeneratorBody.removeFirstChild();
      boolean advanceCase = translateStatementInOriginalBody();

      if (advanceCase) {
        int caseNumber;
        if (currentStatement.isGeneratorMarker()) {
          caseNumber = (int) currentStatement.getFirstChild().getDouble();
        } else {
          caseNumber = generatorCaseCount;
          generatorCaseCount++;
        }
        Node oldCase = enclosingBlock.getParent();
        Node newCase =
            withBooleanType(IR.caseNode(withNumberType(IR.number(caseNumber)), IR.block()));
        enclosingBlock = newCase.getLastChild();
        if (oldCase.isTry()) {
          oldCase = oldCase.getGrandparent();
          if (!currentExceptionContext.isEmpty()) {
            Node newTry =
                IR.tryCatch(IR.block(), currentExceptionContext.get(0).catchBlock.cloneTree());
            newCase.getLastChild().addChildToBack(newTry);
            enclosingBlock = newCase.getLastChild().getLastChild().getFirstChild();
          }
        }
        oldCase.getParent().addChildAfter(newCase, oldCase);
      }
    }

    parent.useSourceInfoIfMissingFromForTree(parent);
    compiler.reportChangeToEnclosingScope(genBlock);
  }

  /** Returns {@code true} if a new case node should be added */
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
    } else if (currentStatement.isNormalBlock()) {
      visitBlock();
      return false;
    } else if (controlCanExit(currentStatement)) {
      switch (currentStatement.getToken()) {
        case WHILE:
        case DO:
        case FOR:
          visitLoop(null);
          return false;
        case FOR_IN:
          visitForIn();
          return false;
        case LABEL:
          visitLabel();
          return false;
        case SWITCH:
          visitSwitch();
          return false;
        case IF:
          if (!currentStatement.isGeneratorSafe()) {
            visitIf();
            return false;
          }
          break;
        case TRY:
          visitTry();
          return false;
        case EXPR_RESULT:
          if (currentStatement.getFirstChild().isYield()) {
            visitYieldExprResult();
            return true;
          }
          break;
        case RETURN:
          visitReturn();
          return false;
        case CONTINUE:
          visitContinue();
          return false;
        case BREAK:
          if (!currentStatement.isGeneratorSafe()) {
            visitBreak();
            return false;
          }
          break;
        case THROW:
          visitThrow();
          return false;
        default:
          // We never want to copy over an untranslated statement for which control exits.
          throw new RuntimeException(
              "Untranslatable control-exiting statement in generator function: "
                  + currentStatement.getToken());
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
      caughtError = withUnknownType(IR.name(GENERATOR_ERROR + "temp"));
      catchBody = IR.block(IR.throwNode(caughtError.cloneTree()));
      catchBody.getFirstChild().setGeneratorSafe(true);
    }
    Node finallyBody = catchBlock.getNext();
    int catchStartState = generatorCaseCount++;
    Node catchStart = makeGeneratorMarker(catchStartState);

    Node errorNameGenerated =
        withUnknownType(IR.name("$jscomp$generator$" + caughtError.getString()));

    originalGeneratorBody.addChildToFront(catchStart);
    originalGeneratorBody.addChildAfter(catchBody, catchStart);

    Node assignError =
        withUnknownType(
            IR.assign(withUnknownType(IR.name(GENERATOR_ERROR)), errorNameGenerated.cloneTree()));
    Node newCatchBody =
        IR.block(IR.exprResult(assignError), createStateUpdate(catchStartState), createSafeBreak());
    Node newCatch = IR.catchNode(errorNameGenerated, newCatchBody);

    currentExceptionContext.add(0, new ExceptionContext(catchStartState, newCatch));

    if (finallyBody != null) {
      Node finallyName = withNumberType(IR.name(GENERATOR_FINALLY_JUMP + generatorCounter.get()));
      int finallyStartState = generatorCaseCount++;
      Node finallyStart = makeGeneratorMarker(finallyStartState);
      int finallyEndState = generatorCaseCount++;
      Node finallyEnd = makeGeneratorMarker(finallyEndState);

      NodeTraversal.traverseEs6(
          compiler, tryBody, new ControlExitsCheck(finallyName, finallyStartState));
      NodeTraversal.traverseEs6(
          compiler, catchBody, new ControlExitsCheck(finallyName, finallyStartState));
      originalGeneratorBody.addChildToFront(tryBody.detach());

      originalGeneratorBody.addChildAfter(finallyStart, catchBody);
      originalGeneratorBody.addChildAfter(finallyBody.detach(), finallyStart);
      originalGeneratorBody.addChildAfter(finallyEnd, finallyBody);
      originalGeneratorBody.addChildToFront(IR.var(finallyName.cloneTree()));

      finallyBody.addChildToBack(
          IR.exprResult(
              withNumberType(
                  IR.assign(withNumberType(IR.name(GENERATOR_STATE)), finallyName.cloneTree()))));
      finallyBody.addChildToBack(createSafeBreak());
      tryBody.addChildToBack(
          IR.exprResult(
              withNumberType(
                  IR.assign(finallyName.cloneTree(), withNumberType(IR.number(finallyEndState))))));
      tryBody.addChildToBack(createStateUpdate(finallyStartState));
      tryBody.addChildToBack(createSafeBreak());
      catchBody.addChildToBack(
          IR.exprResult(
              withNumberType(
                  IR.assign(finallyName.cloneTree(), withNumberType(IR.number(finallyEndState))))));
    } else {
      int catchEndState = generatorCaseCount++;
      Node catchEnd = makeGeneratorMarker(catchEndState);
      originalGeneratorBody.addChildAfter(catchEnd, catchBody);
      tryBody.addChildToBack(createStateUpdate(catchEndState));
      tryBody.addChildToBack(createSafeBreak());
      originalGeneratorBody.addChildToFront(tryBody.detach());
    }

    catchBody.addChildToFront(IR.var(caughtError, withUnknownType(IR.name(GENERATOR_ERROR))));

    if (enclosingBlock.getParent().isTry()) {
      enclosingBlock = enclosingBlock.getGrandparent();
    }

    enclosingBlock.addChildToBack(IR.tryCatch(IR.block(), newCatch));
    enclosingBlock = enclosingBlock.getLastChild().getFirstChild();
    if (!hasTranslatedTry) {
      hasTranslatedTry = true;
      hoistRoot
          .getParent()
          .addChildAfter(IR.var(withUnknownType(IR.name(GENERATOR_ERROR))), hoistRoot);
    }
  }

  private void visitContinue() {
    checkState(currentLoopContext.get(0).continueCase != -1);
    int continueCase;
    if (currentStatement.hasChildren()) {
      continueCase = getLoopContext(currentStatement.removeFirstChild().getString()).continueCase;
    } else {
      continueCase = currentLoopContext.get(0).continueCase;
    }
    enclosingBlock.addChildToBack(createStateUpdate(continueCase));
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
        compiler.report(
            JSError.make(
                currentStatement,
                Es6ToEs3Util.CANNOT_CONVERT_YET,
                "Breaking to a label that is not a loop"));
        return;
      }
      breakCase = loop.breakCase;
    } else {
      breakCase = currentLoopContext.get(0).breakCase;
    }
    enclosingBlock.addChildToBack(createStateUpdate(breakCase));
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
   * Pops the loop information off of our stack if we reach the marker cooresponding
   * to the end of the current loop.
   */
  private void visitGeneratorMarker() {
    if (!currentLoopContext.isEmpty()
        && currentLoopContext.get(0).breakCase == currentStatement.getFirstChild().getDouble()) {
      currentLoopContext.remove(0);
    }
    if (!currentExceptionContext.isEmpty()
        && currentExceptionContext.get(0).catchStartCase
            == currentStatement.getFirstChild().getDouble()) {
      currentExceptionContext.remove(0);
    }
  }

  /**
   * Uses a case statement to jump over the body if the condition of the
   * if statement is false. Additionally, lift the body of the {@code if}
   * statement to the top level.
   */
  private void visitIf() {
    Node condition = currentStatement.removeFirstChild();
    Node ifBody = currentStatement.removeFirstChild();
    boolean hasElse = currentStatement.hasChildren();

    int ifEndState = generatorCaseCount++;

    Node invertedConditional =
        IR.ifNode(
            withBooleanType(IR.not(condition)),
            IR.block(createStateUpdate(ifEndState), createSafeBreak()));
    invertedConditional.setGeneratorSafe(true);
    Node endIf = makeGeneratorMarker(ifEndState);

    originalGeneratorBody.addChildToFront(invertedConditional);
    originalGeneratorBody.addChildAfter(ifBody, invertedConditional);
    originalGeneratorBody.addChildAfter(endIf, ifBody);

    if (hasElse) {
      Node elseBlock = currentStatement.removeFirstChild();

      int elseEndState = generatorCaseCount++;

      Node endElse = makeGeneratorMarker(elseEndState);

      ifBody.addChildToBack(createStateUpdate(elseEndState));
      ifBody.addChildToBack(createSafeBreak());
      originalGeneratorBody.addChildAfter(elseBlock, endIf);
      originalGeneratorBody.addChildAfter(endElse, elseBlock);
    }
  }

  /**
   * Translates switch statements into a series of if statements.
   *
   * <p>Sample translation:
   * <pre>
   * switch (i) {
   *   case 1:
   *     s;
   *   case 2:
   *     t;
   *   ...
   * }
   * </pre>
   *
   * <p>Is eventually rewritten to:
   *
   * <pre>
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
   * </pre>
   */
  private void visitSwitch() {
    Node didEnter = withBooleanType(IR.name(GENERATOR_SWITCH_ENTERED + generatorCounter.get()));
    Node didEnterDecl = IR.var(didEnter.cloneTree(), withFalseType(IR.falseNode()));
    Node switchVal =
        withType(
            IR.name(GENERATOR_SWITCH_VAL + generatorCounter.get()),
            currentStatement.getFirstChild().getTypeI());
    Node switchValDecl = IR.var(switchVal.cloneTree(), currentStatement.removeFirstChild());
    originalGeneratorBody.addChildToFront(didEnterDecl);
    originalGeneratorBody.addChildAfter(switchValDecl, didEnterDecl);
    Node insertionPoint = switchValDecl;

    while (currentStatement.hasChildren()) {
      Node currCase = currentStatement.removeFirstChild();
      Node equivBlock;
      currCase
          .getLastChild()
          .addChildToFront(
              IR.exprResult(
                  withBooleanType(IR.assign(didEnter.cloneTree(), withTrueType(IR.trueNode())))));
      if (currCase.isDefaultCase()) {
        if (currentStatement.hasChildren()) {
          compiler.report(
              JSError.make(
                  currentStatement,
                  Es6ToEs3Util.CANNOT_CONVERT_YET,
                  "Default case as intermediate case"));
        }
        equivBlock = IR.block(currCase.removeFirstChild());
      } else {
        equivBlock =
            IR.ifNode(
                withBooleanType(
                    IR.or(
                        didEnter.cloneTree(),
                        withBooleanType(
                            IR.sheq(switchVal.cloneTree(), currCase.removeFirstChild())))),
                currCase.removeFirstChild());
      }
      originalGeneratorBody.addChildAfter(equivBlock, insertionPoint);
      insertionPoint = equivBlock;
    }

    int breakTarget = generatorCaseCount++;
    int cont = currentLoopContext.isEmpty() ? -1 : currentLoopContext.get(0).continueCase;
    currentLoopContext.add(0, new LoopContext(breakTarget, cont, null));
    Node breakCase = makeGeneratorMarker(breakTarget);
    originalGeneratorBody.addChildAfter(breakCase, insertionPoint);
  }

  /**
   * Lifts all children to the body of the original generator to flatten the block.
   */
  private void visitBlock() {
    if (!currentStatement.hasChildren()) {
      return;
    }
    Node insertionPoint = currentStatement.removeFirstChild();
    originalGeneratorBody.addChildToFront(insertionPoint);
    for (Node child = currentStatement.removeFirstChild();
        child != null;
        child = currentStatement.removeFirstChild()) {
      originalGeneratorBody.addChildAfter(child, insertionPoint);
      insertionPoint = child;
    }
  }

  /**
   * Translates for in loops to a for in loop which produces an array of
   * values iterated over followed by a plain for loop which performs the logic
   * contained in the body of the original for in.
   *
   * <p>Sample translation:
   * <pre>
   * for (i in j) {
   *   s;
   * }
   * </pre>
   *
   * <p>Is eventually rewritten to:
   *
   * <pre>
   * $jscomp$arr = [];
   * $jscomp$iter = j;
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
   * </pre>
   */
  private void visitForIn() {
    Node variable = currentStatement.removeFirstChild();
    Node iterable = currentStatement.removeFirstChild();
    Node body = currentStatement.removeFirstChild();

    TypeI iterableType = iterable.getTypeI();
    TypeI typeParam = unknownType;
    if (addTypes) {
      typeParam = iterableType.autobox().toMaybeObjectType().getTemplateTypes().get(0);
    }
    TypeI arrayType = createGenericType(JSTypeNative.ARRAY_TYPE, typeParam);
    String loopId = generatorCounter.get();
    Node arrayName = withType(IR.name(GENERATOR_FOR_IN_ARRAY + loopId), arrayType);
    Node varName = withNumberType(IR.name(GENERATOR_FOR_IN_VAR + loopId));
    Node iterableName = withType(IR.name(GENERATOR_FOR_IN_ITER + loopId), iterableType);

    if (variable.isVar()) {
      variable = variable.removeFirstChild();
    }
    body.addChildToFront(
        IR.ifNode(
            withBooleanType(IR.not(IR.in(variable.cloneTree(), iterableName.cloneTree()))),
            IR.block(IR.continueNode())));
    body.addChildToFront(
        IR.var(variable.cloneTree(), IR.getelem(arrayName.cloneTree(), varName.cloneTree())));
    hoistRoot.getParent().addChildAfter(IR.var(arrayName.cloneTree()), hoistRoot);
    hoistRoot.getParent().addChildAfter(IR.var(varName.cloneTree()), hoistRoot);
    hoistRoot.getParent().addChildAfter(IR.var(iterableName.cloneTree()), hoistRoot);

    Node arrayDef =
        IR.exprResult(
            withType(
                IR.assign(arrayName.cloneTree(), withType(IR.arraylit(), arrayType)), arrayType));
    Node iterDef =
        IR.exprResult(withType(IR.assign(iterableName.cloneTree(), iterable), iterableType));
    Node newForIn =
        IR.forIn(
            variable.cloneTree(),
            iterableName,
            IR.block(
                IR.exprResult(
                    withNumberType(
                        IR.call(IR.getprop(arrayName.cloneTree(), IR.string("push")), variable)))));
    Node newFor =
        IR.forNode(
            withNumberType(IR.assign(varName.cloneTree(), withNumberType(IR.number(0)))),
            withBooleanType(
                IR.lt(
                    varName.cloneTree(),
                    withNumberType(IR.getprop(arrayName, IR.string("length"))))),
            withNumberType(IR.inc(varName, true)),
            body);

    enclosingBlock.addChildToBack(arrayDef);
    enclosingBlock.addChildToBack(iterDef);
    enclosingBlock.addChildToBack(newForIn);
    originalGeneratorBody.addChildToFront(newFor);
  }

  /**
   * Translates loops to a case statement followed by an if statement
   * containing the loop body. The if statement finishes by
   * jumping back to the initial case statement to enter the loop again.
   * In the case of for and do loops, initialization and post loop statements are inserted
   * before and after the if statement. Below is a sample translation for a while loop:
   *
   * <p>Sample translation:
   * <pre>
   * while (b) {
   *   s;
   * }
   * </pre>
   *
   * <p>Is eventually rewritten to:
   * <pre>
   * case n:
   *   if (b) {
   *     s;
   *     state = n;
   *     break;
   *   }
   * </pre>
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
    } else if (currentStatement.isVanillaFor()) {
      initializer = currentStatement.removeFirstChild();
      if (initializer.isAssign()) {
        initializer = IR.exprResult(initializer);
      }
      guard = currentStatement.removeFirstChild();
      incr = currentStatement.removeFirstChild();
      body = currentStatement.removeFirstChild();
    } else {
      checkState(currentStatement.isDo());
      initializer = IR.empty();
      incr =
          withBooleanType(
              IR.assign(
                  withBooleanType(IR.name(GENERATOR_DO_WHILE_INITIAL)),
                  withFalseType(IR.falseNode())));

      body = currentStatement.removeFirstChild();
      guard = currentStatement.removeFirstChild();
    }

    Node condition;
    Node prestatement;

    if (guard.isNormalBlock()) {
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
      Node continueCase = makeGeneratorMarker(continueState);
      body.addChildToBack(continueCase);
      body.addChildToBack(incr.isNormalBlock() ? incr : IR.exprResult(incr));
    }

    currentLoopContext.add(0, new LoopContext(generatorCaseCount, continueState, label));

    Node beginCase = makeGeneratorMarker(loopBeginState);
    Node conditionalBranch =
        IR.ifNode(condition.isEmpty() ? withTrueType(IR.trueNode()) : condition, body);
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
   * Hoists {@code var} statements into the closure containing the iterator
   * to preserve their state across
   * multiple calls to next().
   */
  private void visitVar() {
    Node name = currentStatement.removeFirstChild();
    while (name != null) {
      if (name.hasChildren()) {
        enclosingBlock.addChildToBack(
            IR.exprResult(withType(IR.assign(name, name.removeFirstChild()), name.getTypeI())));
      }
      hoistRoot.getParent().addChildAfter(IR.var(name.cloneTree()), hoistRoot);
      // name now refers to "generated" assignment which is not visible to end user. Don't index it.
      name.makeNonIndexable();
      name = currentStatement.removeFirstChild();
    }
  }

  /**
   * Translates {@code yield} to set the state so that execution resume at the next statement
   * when the function is next called and then returns an iterator result with
   * the desired value.
   */
  private void visitYieldExprResult() {
    enclosingBlock.addChildToBack(createStateUpdate());
    Node yield = currentStatement.getFirstChild();
    Node value =
        yield.hasChildren() ? yield.removeFirstChild() : withUndefinedType(IR.name("undefined"));
    enclosingBlock.addChildToBack(IR.returnNode(createIteratorResult(value, false)));
  }

  /**
   * Translates {@code return} statements to set the state to done before returning the
   * desired value.
   */
  private void visitReturn() {
    enclosingBlock.addChildToBack(createStateUpdate(-1));
    enclosingBlock.addChildToBack(
        IR.returnNode(
            createIteratorResult(
                currentStatement.hasChildren()
                    ? currentStatement.removeFirstChild()
                    : withUndefinedType(IR.name("undefined")),
                true)));
  }

  private Node createStateUpdate() {
    return IR.exprResult(
        withNumberType(
            IR.assign(
                withNumberType(IR.name(GENERATOR_STATE)),
                withNumberType(IR.number(generatorCaseCount)))));
  }

  private Node createStateUpdate(int state) {
    return IR.exprResult(
        withNumberType(
            IR.assign(withNumberType(IR.name(GENERATOR_STATE)), withNumberType(IR.number(state)))));
  }

  private Node createIteratorResult(Node value, boolean done) {
    TypeI iIterableResultType =
        createGenericType(JSTypeNative.I_ITERABLE_RESULT_TYPE, value.getTypeI());
    return withType(
        IR.objectlit(
            IR.propdef(IR.stringKey("value"), value),
            IR.propdef(
                IR.stringKey("done"),
                done ? withTrueType(IR.trueNode()) : withFalseType(IR.falseNode()))),
        iIterableResultType);
  }

  private static Node createSafeBreak() {
    Node breakNode = IR.breakNode();
    breakNode.setGeneratorSafe(true);
    return breakNode;
  }

  private Node createFinallyJumpBlock(Node finallyName, int finallyStartState) {
    int jumpPoint = generatorCaseCount++;
    Node setReturnState =
        IR.exprResult(
            withNumberType(
                IR.assign(finallyName.cloneTree(), withNumberType(IR.number(jumpPoint)))));
    Node toFinally = createStateUpdate(finallyStartState);
    Node returnPoint = makeGeneratorMarker(jumpPoint);
    Node returnBlock = IR.block(setReturnState, toFinally, createSafeBreak());
    returnBlock.addChildToBack(returnPoint);
    return returnBlock;
  }

  private LoopContext getLoopContext(String label) {
    for (LoopContext context : currentLoopContext) {
      if (label.equals(context.label)) {
        return context;
      }
    }
    return null;
  }

  private boolean controlCanExit(Node n) {
    ControlExitsCheck exits = new ControlExitsCheck();
    NodeTraversal.traverseEs6(compiler, n, exits);
    return exits.didExit();
  }

  /**
   * Finds the only child of the {@code node} of the given type.
   */
  private Node getUnique(Node node, Token type) {
    List<Node> matches = new ArrayList<>();
    insertAll(node, type, matches);
    checkState(matches.size() == 1, matches);
    return matches.get(0);
  }

  /**
   * Adds all children of the {@code node} of the given type to given list.
   */
  private void insertAll(Node node, Token type, List<Node> matchingNodes) {
    if (node.getToken() == type) {
      matchingNodes.add(node);
    }
    for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
      insertAll(c, type, matchingNodes);
    }
  }

  /**
   * Decomposes expressions with yields inside of them to equivalent
   * sequence of expressions in which all non-statement yields are
   * of the form:
   *
   * <pre>
   *   var name = yield expr;
   * </pre>
   *
   * <p>For example, change the following code:
   * <pre>
   *   return x || yield y;
   * </pre>
   * <p>Into:
   * <pre>
   *  var temp$$0;
   *  if (temp$$0 = x); else temp$$0 = yield y;
   *  return temp$$0;
   * </pre>
   *
   * This uses the {@link ExpressionDecomposer} class
   */
  private final class DecomposeYields extends NodeTraversal.AbstractPreOrderCallback {

    private final AbstractCompiler compiler;
    private final ExpressionDecomposer decomposer;

    DecomposeYields(AbstractCompiler compiler) {
      this.compiler = compiler;
      Set<String> consts = new HashSet<>();
      decomposer =
          new ExpressionDecomposer(
              compiler,
              compiler.getUniqueNameIdSupplier(),
              consts,
              Scope.createGlobalScope(new Node(Token.SCRIPT)),
              compiler.getOptions().allowMethodCallDecomposing());
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case YIELD:
          visitYieldExpression(t, n);
          break;
        case DO:
        case FOR:
        case WHILE:
          visitLoop(t, n);
          break;
        case CASE:
          if (controlCanExit(n.getFirstChild())) {
            compiler.report(
                JSError.make(
                    n, Es6ToEs3Util.CANNOT_CONVERT_YET, "Case statements that contain yields"));
            return false;
          }
          break;
        default:
          break;
      }
      return true;
    }

    private void visitYieldExpression(NodeTraversal t, Node n) {
      if (n.getParent().isExprResult()) {
        return;
      }
      if (decomposer.canExposeExpression(n)
          != ExpressionDecomposer.DecompositionType.UNDECOMPOSABLE) {
        decomposer.exposeExpression(n);
        t.reportCodeChange();
      } else {
        String link = "https://github.com/google/closure-compiler/wiki/FAQ"
            + "#i-get-an-undecomposable-expression-error-for-my-yield-or-await-expression"
            + "-what-do-i-do";
        String suggestion = "Please rewrite the yield or await as a separate statement.";
        String message = "Undecomposable expression: " + suggestion + "\nSee " + link;
        compiler.report(JSError.make(n, Es6ToEs3Util.CANNOT_CONVERT, message));
      }
    }

    private void visitLoop(NodeTraversal t, Node n) {
      Node enclosingFunc = NodeUtil.getEnclosingFunction(n);
      if (enclosingFunc == null || !enclosingFunc.isGeneratorFunction() || n.isForIn()) {
        return;
      }
      Node enclosingBlock = NodeUtil.getEnclosingBlock(n);
      Node guard = null;
      Node incr = null;
      switch (n.getToken()) {
        case FOR:
          guard = n.getSecondChild();
          incr = guard.getNext();
          break;
        case WHILE:
          guard = n.getFirstChild();
          incr = IR.empty();
          break;
        case DO:
          guard = n.getLastChild();
          if (!guard.isEmpty()) {
            Node firstEntry = IR.name(GENERATOR_DO_WHILE_INITIAL);
            enclosingBlock.addChildToFront(
                IR.var(firstEntry.cloneTree(), withTrueType(IR.trueNode())));
            guard = withBooleanType(IR.or(firstEntry, n.getLastChild().detach()));
            n.addChildToBack(guard);
          }
          incr = IR.empty();
          break;
        default:
          break;
      }
      if (!controlCanExit(guard) && !controlCanExit(incr)) {
        return;
      }
      Node guardName = IR.name(GENERATOR_LOOP_GUARD + generatorCounter.get());
      if (!guard.isEmpty()) {
        Node container = new Node(Token.BLOCK);
        n.replaceChild(guard, container);
        container.addChildToFront(
            IR.block(
                IR.exprResult(
                    withType(
                        IR.assign(guardName.cloneTree(), guard.cloneTree()), guard.getTypeI()))));
        container.addChildToBack(guardName.cloneTree());
      }
      if (!incr.isEmpty()) {
        n.addChildBefore(IR.block(IR.exprResult(incr.detach())), n.getLastChild());
      }
      enclosingBlock.addChildToFront(IR.var(guardName));
      t.reportCodeChange();
    }
  }

  private Node makeGeneratorMarker(int i) {
    Node n = IR.exprResult(withNumberType(IR.number(i)));
    n.setGeneratorMarker(true);
    return n;
  }

  private final class ControlExitsCheck implements NodeTraversal.Callback {

    int continueCatchers;
    int breakCatchers;
    int throwCatchers;
    List<String> labels = new ArrayList<>();
    boolean exited;
    boolean addJumps;
    private Node finallyName;
    private int finallyStartState;

    ControlExitsCheck(Node finallyName, int finallyStartState) {
      this.finallyName = finallyName;
      this.finallyStartState = finallyStartState;
      addJumps = true;
    }

    ControlExitsCheck() {
      addJumps = false;
    }

    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      switch (n.getToken()) {
        case FUNCTION:
          return false;
        case LABEL:
          labels.add(0, n.getFirstChild().getString());
          break;
        case DO:
        case WHILE:
        case FOR:
        case FOR_IN:
          continueCatchers++;
          breakCatchers++;
          break;
        case SWITCH:
          breakCatchers++;
          break;
        case BLOCK:
          parent = n.getParent();
          if (parent != null
              && parent.isTry()
              && parent.getFirstChild() == n
              && n.getNext().hasChildren()) {
            throwCatchers++;
          }
          break;
        case BREAK:
          if (!n.isGeneratorSafe()
              && ((breakCatchers == 0 && !n.hasChildren())
                  || (n.hasChildren() && !labels.contains(n.getFirstChild().getString())))) {
            exited = true;
            if (addJumps) {
              parent.addChildBefore(createFinallyJumpBlock(finallyName, finallyStartState), n);
            }
          }
          break;
        case CONTINUE:
          if (continueCatchers == 0
              || (n.hasChildren() && !labels.contains(n.getFirstChild().getString()))) {
            exited = true;
            if (addJumps) {
              parent.addChildBefore(createFinallyJumpBlock(finallyName, finallyStartState), n);
            }
          }
          break;
        case THROW:
          if (throwCatchers == 0) {
            exited = true;
            if (addJumps && !n.isGeneratorSafe()) {
              parent.addChildBefore(createFinallyJumpBlock(finallyName, finallyStartState), n);
            }
          }
          break;
        case RETURN:
          exited = true;
          if (addJumps) {
            parent.addChildBefore(createFinallyJumpBlock(finallyName, finallyStartState), n);
          }
          break;
        case YIELD:
          exited = true;
          break;
        default:
          break;
      }
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case LABEL:
          labels.remove(0);
          break;
        case DO:
        case WHILE:
        case FOR:
        case FOR_IN:
          continueCatchers--;
          breakCatchers--;
          break;
        case SWITCH:
          breakCatchers--;
          break;
        case BLOCK:
          parent = n.getParent();
          if (parent != null
              && parent.isTry()
              && parent.getFirstChild() == n
              && n.getNext().hasChildren()) {
            throwCatchers--;
          }
          break;
        default:
          break;
      }
    }

    public boolean didExit() {
      return exited;
    }
  }

  private static final class LoopContext {
    int breakCase;
    int continueCase;
    String label;

    LoopContext(int breakCase, int continueCase, String label) {
      this.breakCase = breakCase;
      this.continueCase = continueCase;
      this.label = label;
    }
  }

  private static final class ExceptionContext {
    int catchStartCase;
    Node catchBlock;

    ExceptionContext(int catchStartCase, Node catchBlock) {
      this.catchStartCase = catchStartCase;
      this.catchBlock = catchBlock;
    }
  }

  /**
   * Preloads the skeleton AST function that is needed for generators,
   * reports change to enclosing scope, and returns it.
   * If the skeleton is already preloaded, does not do anything, just returns the node.
   */
  static Node preloadGeneratorSkeletonAndReportChange(AbstractCompiler compiler) {
    return preloadGeneratorSkeleton(compiler, true);
  }

  /**
   * Preloads the skeleton AST function that is needed for generators and returns it.
   * If the skeleton is already preloaded, does not do anything, just returns the node.
   * reportChange tells the function whether to report a code change in the enclosing scope.
   *
   * Because validity checks happen between passes, we need to report the change if the generator
   * was preloaded in the {@link EarlyEs6ToEs3Converter} class.
   * However, if the generator was preloaded in this {@link Es6RewriteGenerators} class, we do not
   * want to report the change since it will be removed by {@link #cleanUpGeneratorSkeleton}
   */
  private static Node preloadGeneratorSkeleton(AbstractCompiler compiler, boolean reportChange) {
    Node root = compiler.getJsRoot();
    Node generatorFunc = getPreloadedGeneratorFunc(root);
    if (generatorFunc != null) {
      return generatorFunc;
    }
    Node genFunc = compiler.parseSyntheticCode(Joiner.on('\n').join(
        "function " + GENERATOR_PRELOAD_FUNCTION_NAME + "() {",
        "  var " + GENERATOR_STATE + " = 0;",
        "  function $jscomp$generator$impl(",
        "      " + GENERATOR_ACTION_ARG + ",",
        "      " + GENERATOR_NEXT_ARG + ",",
        "      " + GENERATOR_THROW_ARG + ") {",
        "    while (1) switch (" + GENERATOR_STATE + ") {",
        "      case 0:",
        "      default:",
        "        return {value: undefined, done: true};",
        "    }",
        "  }",
        // TODO(tbreisacher): Remove this cast if we start returning an actual
        // Generator object.
        "  var iterator = /** @type {!Generator<?>} */ ({",
        "    next: function(arg) {",
        "      return $jscomp$generator$impl("
            + GENERATOR_ACTION_NEXT
            + ", arg, undefined);",
        "    },",
        "    throw: function(arg) {",
        "      return $jscomp$generator$impl("
            + GENERATOR_ACTION_THROW
            + ", undefined, arg);",
        "    },",
        // TODO(tbreisacher): Implement Generator.return:
        // http://www.ecma-international.org/ecma-262/6.0/#sec-generator.prototype.return
        "    return: function(arg) { throw Error('Not yet implemented'); },",
        "  });",
        "  $jscomp.initSymbolIterator();",
        "  /** @this {!Generator<?>} */",
        "  iterator[Symbol.iterator] = function() { return this; };",
        "  return iterator;",
        "}"))
    .getFirstChild() // function
    .detach();
    root.getFirstChild().addChildToFront(genFunc);
    if (reportChange) {
      NodeUtil.markNewScopesChanged(genFunc, compiler);
      compiler.reportChangeToEnclosingScope(genFunc);
    }
    return genFunc;
  }

  /**
   * Add types to key nodes in the generator AST created by {@link #preloadGeneratorSkeleton} For
   * example, changes {@code Generator<?>} to {@code Generator<yieldType>}, where yieldType is the
   * inferred yield type of the original user-defined generator function.
   */
  private void addTypesToGeneratorSkeleton(Node genBlock, TypeI yieldType) {
    TypeI generatorType = createGenericType(JSTypeNative.GENERATOR_TYPE, yieldType);
    TypeI iIterableResultType = createGenericType(JSTypeNative.I_ITERABLE_RESULT_TYPE, yieldType);

    // Add type to the generator implementation function node.
    Node impl = genBlock.getSecondChild();
    checkState(impl.isFunction());
    FunctionTypeI implFuncType = impl.getTypeI().toMaybeFunctionType();
    implFuncType = implFuncType.toBuilder().withReturnType(iIterableResultType).build();
    impl.setTypeI(implFuncType);
    impl.getFirstChild().setTypeI(implFuncType);

    Node objectLit =
        impl.getChildAtIndex(2)
            .getFirstChild()
            .getSecondChild()
            .getFirstChild()
            .getChildAtIndex(2)
            .getFirstFirstChild() // RETURN node in default case
            .getFirstChild();
    checkState(objectLit.isObjectLit());
    objectLit.setTypeI(iIterableResultType);
    objectLit.getFirstChild().setTypeI(iIterableResultType);

    // Add type to the var iterator = {next: function (...) {...}, throw: ..., return: ... } node
    Node iteratorVar = impl.getNext();
    checkState(iteratorVar.isVar());
    iteratorVar.getFirstChild().setTypeI(generatorType);
    iteratorVar.getFirstFirstChild().setTypeI(generatorType);
    iteratorVar.getFirstFirstChild().getFirstChild().setTypeI(generatorType);

    Node next = iteratorVar.getFirstFirstChild().getFirstFirstChild(); // String key "next"
    checkState(next.isStringKey());
    FunctionTypeI nextFunctionType = next.getTypeI().toMaybeFunctionType();
    nextFunctionType = nextFunctionType.toBuilder().withReturnType(iIterableResultType).build();
    next.setTypeI(nextFunctionType);
    next.getFirstChild().setTypeI(nextFunctionType);

    // CALL node of function $jscomp$generator$impl within RETURN node in function of "next"
    Node call = next.getFirstChild().getChildAtIndex(2).getFirstFirstChild();
    checkState(call.isCall());
    call.setTypeI(iIterableResultType);

    Node genImplName = call.getFirstChild();
    checkState(genImplName.isName());
    FunctionTypeI genImplType = genImplName.getTypeI().toMaybeFunctionType();
    genImplType = genImplType.toBuilder().withReturnType(iIterableResultType).build();
    genImplName.setTypeI(genImplType);

    // Add type to the iterator[Symbol.iterator] = function () { return this; } node
    Node exprResult = iteratorVar.getNext().getNext();
    checkState(exprResult.isExprResult());
    FunctionTypeI funcType = exprResult.getFirstChild().getTypeI().toMaybeFunctionType();
    // Set function type to be function(this:Generator<?>):Generator<inferred yield type>
    funcType = funcType.toBuilder().withReturnType(iIterableResultType).build();
    exprResult.getFirstChild().setTypeI(funcType);
    exprResult.getFirstFirstChild().setTypeI(funcType);
    exprResult.getFirstFirstChild().getFirstChild().setTypeI(generatorType);
    exprResult.getFirstChild().getSecondChild().setTypeI(funcType); // FUNCTION node
    exprResult
        .getFirstChild()
        .getSecondChild()
        .getChildAtIndex(2)
        .getFirstFirstChild() // THIS node
        .setTypeI(generatorType);

    // Add type to the final return node of genBlock
    exprResult.getNext().getFirstChild().setTypeI(generatorType);
  }

  /** Returns the generator function that was preloaded, or null if not found. */
  @Nullable
  private static Node getPreloadedGeneratorFunc(Node root) {
    if (root.getFirstChild() == null) {
      return null;
    }
    for (Node c = root.getFirstFirstChild(); c != null; c = c.getNext()) {
      if (c.isFunction() && GENERATOR_PRELOAD_FUNCTION_NAME.equals(c.getFirstChild().getString())) {
        return c;
      }
    }
    return null;
  }

  /**
   * Delete the preloaded generator function, and report code change if reportChange is true.
   *
   * We only want to reportChange if the generator function was preloaded in the
   * {@link EarlyEs6ToEs3Converter} class, since a change was reported there.
   * If we preload the generator function in this class, it will be an addition and deletion of the
   * same node, which means we do not have to report code change in either case since the code was
   * ultimately not changed.
   */
  private void cleanUpGeneratorSkeleton(boolean reportChange) {
    Node genFunc = getPreloadedGeneratorFunc(compiler.getJsRoot());
    if (genFunc != null) {
      if (reportChange) {
        NodeUtil.deleteNode(genFunc, compiler);
      } else {
        genFunc.detach();
      }
    }
  }

  private TypeI createGenericType(JSTypeNative typeName, TypeI typeArg) {
    return Es6ToEs3Util.createGenericType(addTypes, registry, typeName, typeArg);
  }

  private Node withStringType(Node n) {
    return withType(n, stringType);
  }

  private Node withBooleanType(Node n) {
    return withType(n, booleanType);
  }

  private Node withFalseType(Node n) {
    return withType(n, falseType);
  }

  private Node withTrueType(Node n) {
    return withType(n, trueType);
  }

  private Node withUnknownType(Node n) {
    return withType(n, unknownType);
  }

  private Node withNumberType(Node n) {
    return withType(n, numberType);
  }

  private Node withUndefinedType(Node n) {
    return withType(n, undefinedType);
  }
}
