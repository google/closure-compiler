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

import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Converts ES6 generator functions to valid ES3 code. This pass runs after all ES6 features except
 * for yield and generators have been transpiled.
 *
 * <p>Genertor transpilation pass uses two sets of node properties:
 * <ul><li>generatorMarker property - to indicate that subtee contains YIELD nodes;
 *     <li>generatorSafe property - the node is known to require no further modifications to work in
 *         the transpiled form of the generator body.
 * </ul>
 *
 * <p>The conversion is done in the following steps:
 *
 * <ul>
 *   <li>Find a generator function: <code>function *() {}</code>
 *   <li>Replace its original body with a template
 *   <li>Mark all nodes in original body that contain any YIELD nodes
 *   <li>Transpile every statement of the original body into replaced template
 *       <ul>
 *         <li>unmarked nodes may be copied into the template with a trivial transpilation
 *             of "this", "break", "continue", "return" and "arguments" keywords.
 *         <li>marked nodes must be broken up into multiple states to support the yields
 *             they contain.
 *       </ul>
 * </ul>
 *
 * <p>{@code Es6RewriteGenerators} depends on {@link EarlyEs6ToEs3Converter} to inject
 * <code>generator_engine.js</code> template.
 */
final class Es6RewriteGenerators implements HotSwapCompilerPass {

  private static final String GENERATOR_FUNCTION = "$jscomp$generator$function";
  private static final String GENERATOR_CONTEXT = "$jscomp$generator$context";
  private static final String GENERATOR_ARGUMENTS = "$jscomp$generator$arguments";
  private static final String GENERATOR_THIS = "$jscomp$generator$this";
  private static final String GENERATOR_FORIN_PREFIX = "$jscomp$generator$forin$";

  private static final FeatureSet transpiledFeatures =
      FeatureSet.BARE_MINIMUM.with(Feature.GENERATORS);

  private final AbstractCompiler compiler;

  Es6RewriteGenerators(AbstractCompiler compiler) {
    checkNotNull(compiler);
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(
        compiler, root, transpiledFeatures, new GeneratorFunctionsTranspiler());
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(
        compiler, scriptRoot, transpiledFeatures, new GeneratorFunctionsTranspiler());
  }

  /**
   * Exposes expression with yield inside to an equivalent expression in which yield is of the form:
   *
   * <pre>
   * var name = yield expr;
   * </pre>
   *
   * <p>For example, changes the following code:
   *
   * <pre>
   * { return x || yield y; }
   * </pre>
   *
   * into:
   *
   * <pre>
   * {
   *   var temp$$0;
   *   if (temp$$0 = x); else temp$$0 = yield y;
   *   return temp$$0;
   * }
   * </pre>
   *
   * <p>Expression should always be inside a block, so that other statemetns could be added at need.
   *
   * <p>Uses the {@link ExpressionDecomposer} class.
   */
  private class YieldExposer extends NodeTraversal.AbstractPreOrderCallback {

    final ExpressionDecomposer decomposer;

    YieldExposer() {
      decomposer =
          new ExpressionDecomposer(
              compiler,
              compiler.getUniqueNameIdSupplier(),
              new HashSet<>(),
              Scope.createGlobalScope(new Node(Token.SCRIPT)),
              compiler.getOptions().allowMethodCallDecomposing());
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      n.setGeneratorMarker(false);
      if (n.isFunction()) {
        return false;
      }
      if (n.isYield()) {
        visitYield(n);
        return false;
      }
      return true;
    }

    void visitYield(Node n) {
      if (n.getParent().isExprResult()) {
        return;
      }
      if (decomposer.canExposeExpression(n)
          != ExpressionDecomposer.DecompositionType.UNDECOMPOSABLE) {
        decomposer.exposeExpression(n);
      } else {
        String link =
            "https://github.com/google/closure-compiler/wiki/FAQ"
                + "#i-get-an-undecomposable-expression-error-for-my-yield-or-await-expression"
                + "-what-do-i-do";
        String suggestion = "Please rewrite the yield or await as a separate statement.";
        String message = "Undecomposable expression: " + suggestion + "\nSee " + link;
        compiler.report(JSError.make(n, Es6ToEs3Util.CANNOT_CONVERT, message));
      }
    }
  }

  /** Finds generator functions and performs ES6 -> ES3 trnspilation */
  private class GeneratorFunctionsTranspiler implements NodeTraversal.Callback {
    int generatorNestingLevel = 0;

    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      if (n.isGeneratorFunction()) {
        ++generatorNestingLevel;
      }
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isGeneratorFunction()) {
        new SingleGeneratorFunctionTranspiler(n, --generatorNestingLevel).transpile();
      }
    }
  }

  /** Transpiles a single generator function into a state machine program. */
  private class SingleGeneratorFunctionTranspiler {

    final int generatorNestingLevel;

    /** The transpilation context for the state machine program. */
    final TranspilationContext context = new TranspilationContext();

    /** The body of original generator function that should be transpiled */
    final Node originalGeneratorBody;

    /** Control Flow graph that is used to avoid generation of unreachable code.*/
    ControlFlowGraph<Node> controlFlowGraph;

    /** The body of a replacement function. */
    Node newGeneratorBody;

    SingleGeneratorFunctionTranspiler(Node genFunc, int genaratorNestingLevel) {
      this.generatorNestingLevel = genaratorNestingLevel;
      this.originalGeneratorBody = genFunc.getLastChild();
    }

    public void transpile() {
      // Ensure that the state machine program ends
      Node jumpToEnd = context.callContextMethodResult(originalGeneratorBody, "jumpToEnd");
      jumpToEnd.setGeneratorSafe(true);
      originalGeneratorBody.addChildToBack(jumpToEnd);

      // Insert $context.jumpToEnd only if it's reachable
      controlFlowGraph = ControlFlowAnalysis.getCfg(compiler, originalGeneratorBody.getParent());
      if (controlFlowGraph.getInEdges(jumpToEnd).isEmpty()) {
        jumpToEnd.detach();
      }

      Node genFunc = originalGeneratorBody.getParent();
      checkState(genFunc.isGeneratorFunction());

      Node genFuncName = genFunc.getFirstChild();
      checkState(genFuncName.isName());
      // The transpiled function needs to be able to refer to itself, so make sure it has a name.
      if (genFuncName.getString().isEmpty()) {
        genFuncName.setString(context.getScopedName(GENERATOR_FUNCTION).getString());
      }

      // Prepare a "program" function:
      //   function($jscomp$generator$context) {
      //       while ($jscomp$generator$context.nextAddress) {
      //           switch ($jscomp$generator$context.nextAddress) {
      //             case 0:
      //           }
      //       });
      //   }
      Node program =
          IR.function(
              IR.name(""),
              IR.paramList(context.getJsContextNameNode(genFunc)),
              IR.block(
                  // Without the while loop, OTI assumes the switch statement is only executed
                  // once, which messes up its understanding of the types assigned to variables
                  // within it.
                  IR.whileNode( // TODO(skill):  Remove while loop when this pass moves after
                      // type checking or when OTI is fixed to handle this correctly.
                      IR.getprop(context.getJsContextNameNode(genFunc), "nextAddress"),
                      IR.block(
                          IR.switchNode(
                              IR.getprop(context.getJsContextNameNode(genFunc), "nextAddress"),
                              context.currentCase.caseNode)))));

      // Propagate all suppressions from original generator function to a new "program" function.
      JSDocInfoBuilder jsDocBuilder = new JSDocInfoBuilder(false);
      if (genFunc.getJSDocInfo() != null) {
        if (!genFunc.getJSDocInfo().getSuppressions().isEmpty()) {
          jsDocBuilder.recordSuppressions(genFunc.getJSDocInfo().getSuppressions());
        }
      }
      // Add "uselessCode" suppression as we don't ensure that program's code don't have unreachable
      // statements.
      // TODO(skill): ensure that program doesn't emit unreachable code.
      jsDocBuilder.addSuppression("uselessCode");
      program.setJSDocInfo(jsDocBuilder.build());

      // Replace original generator function body with:
      //   return $jscomp.generator.createGenerator(<origGenerator>, <program function>);
      newGeneratorBody =
          IR.block(
                  IR.returnNode(
                      IR.call(
                          IR.getprop(IR.name("$jscomp"), "generator", "createGenerator"),
                          genFuncName.cloneNode(),
                          program)))
              .useSourceInfoFromForTree(genFunc);

      // Newly introduced functions have to be reported immediately.
      compiler.reportChangeToChangeScope(program);

      originalGeneratorBody.replaceWith(newGeneratorBody);

      NodeTraversal.traverseEs6(compiler, originalGeneratorBody, new YieldNodeMarker());

      // Transpile statements from original generator function
      while (originalGeneratorBody.hasChildren()) {
        Node statement = originalGeneratorBody.getFirstChild().detach();
        transpileStatement(statement);
      }

      context.checkStateIsEmpty();

      genFunc.putBooleanProp(Node.GENERATOR_FN, false);
      compiler.reportChangeToChangeScope(genFunc);
    }

    /** @see #transpileStatement(Node, TranspilationContext.Case, TranspilationContext.Case) */
    void transpileStatement(Node statement) {
      transpileStatement(statement, null, null);
    }

    /**
     * Transpiles a detached node and adds transpiled version of it to the {@link
     * TranspilationContext.Case#currentCase currentCase} of the {@link #context}.
     *
     * @param statement Node to transpile
     * @param breakCase
     * @param continueCase
     */
    void transpileStatement(
        Node statement,
        @Nullable TranspilationContext.Case breakCase,
        @Nullable TranspilationContext.Case continueCase) {
      checkState(IR.mayBeStatement(statement));
      checkState(statement.getParent() == null);

      if (!statement.isGeneratorMarker()) {
        transpileUnmarkedNode(statement);
        return;
      }
      switch (statement.getToken()) {
        case LABEL:
          transpileLabel(statement);
          break;

        case BLOCK:
          transpileBlock(statement);
          break;

        case EXPR_RESULT:
          transpileExpressionResult(statement);
          break;

        case VAR:
          transpileVar(statement);
          break;

        case RETURN:
          transpileReturn(statement);
          break;

        case THROW:
          transpileThrow(statement);
          break;

        case IF:
          transpileIf(statement, breakCase);
          break;

        case FOR:
          transpileFor(statement, breakCase, continueCase);
          break;

        case FOR_IN:
          transpileForIn(statement, breakCase, continueCase);
          break;

        case WHILE:
          transpileWhile(statement, breakCase, continueCase);
          break;

        case DO:
          transpileDo(statement, breakCase, continueCase);
          break;

        case TRY:
          transpileTry(statement, breakCase);
          break;

        case SWITCH:
          transpileSwitch(statement, breakCase);
          break;

        default:
          checkState(false, "Unsupported token: %s ", statement.getToken());
      }
    }

    /** Transpiles code that doesn't contain <code>yield</code>s. */
    void transpileUnmarkedNode(Node n) {
      checkState(!n.isGeneratorMarker());
      if (n.isFunction()) {
        // All function statemnts will be normalized:
        // "function a() {}"   =>   "var a = function() {};"
        // so we have to move them to the outer scope.
        String functionName = n.getFirstChild().getString();
        // Make sure there are no "function (...) {...}" statements (note that
        // "function *(...) {...}" becomes "function $jscomp$generator$function(...) {...}" as
        // inner generator functions are transpiled first).
        checkState(!functionName.isEmpty() && !functionName.startsWith(GENERATOR_FUNCTION));
        newGeneratorBody.addChildBefore(n, newGeneratorBody.getLastChild());
        return;
      }
      context.transpileUnmarkedBlock(n.isNormalBlock() || n.isAddedBlock() ? n : IR.block(n));
    }

    /** Transpiles a label with marked statement. */
    void transpileLabel(Node n) {
      // Collect all labels names in "a: b: c: {}" statement
      ArrayList<Node> labelNames = new ArrayList<>();
      while (n.isLabel()) {
        labelNames.add(n.removeFirstChild());
        n = n.removeFirstChild();
      }

      // Push label names and continue transpilation
      TranspilationContext.Case continueCase =
          NodeUtil.isLoopStructure(n) ? context.createCase() : null;
      TranspilationContext.Case breakCase = context.createCase();
      context.pushLabels(labelNames, breakCase, continueCase);
      transpileStatement(n, breakCase, continueCase);
      context.popLabels(labelNames);

      // Switch to endCase if it's not yet active.
      if (breakCase != context.currentCase) {
        context.switchCaseTo(breakCase);
      }
    }

    /** Transpiles a block. */
    void transpileBlock(Node n) {
      while (n.hasChildren()) {
        transpileStatement(n.removeFirstChild());
      }
    }

    /** Transpiles marked expression result statement. */
    void transpileExpressionResult(Node n) {
      Node exposedExpression = exposeYieldAndTranspileRest(n.removeFirstChild());
      Node decomposed = transpileYields(exposedExpression);

      // Tanspile "a = yield;" into "a = $context.yieldResult;"
      // But don't transpile "yield;" into "$context.yieldResult;"
      // As it influences the collapsing of empty case sections.
      if (!exposedExpression.isYield()) {
        n.addChildToFront(prepareNodeForWrite(decomposed));
        n.setGeneratorMarker(false);
        context.writeGeneratedNode(n);
      }
    }

    /** Transpiles marked "var" statement. */
    void transpileVar(Node n) {
      n.setGeneratorMarker(false);
      Node newVars = n.cloneNode();
      while (n.hasChildren()) {
        Node var;
        // Just collect all unmarked vars and transpile them together.
        while ((var = n.removeFirstChild()) != null && !var.isGeneratorMarker()) {
          newVars.addChildToBack(var);
        }
        if (newVars.hasChildren()) {
          transpileUnmarkedNode(newVars);
          newVars = n.cloneNode();
        }

        // Transpile marked var
        if (var != null) {
          checkState(var.isGeneratorMarker());
          var.addChildToFront(maybeDecomposeExpression(var.removeFirstChild()));
          var.setGeneratorMarker(false);
          newVars.addChildToBack(var);
        }
      }

      // Flush the vars if not empty
      if (newVars.hasChildren()) {
        transpileUnmarkedNode(newVars);
      }
    }

    /** Transpiles marked "return" statement. */
    void transpileReturn(Node n) {
      n.addChildToFront(
          context.returnExpression(
              prepareNodeForWrite(maybeDecomposeExpression(n.removeFirstChild()))));
      context.writeGeneratedNode(n);
    }

    /** Transpiles marked "throw" statement. */
    void transpileThrow(Node n) {
      n.addChildToFront(prepareNodeForWrite(maybeDecomposeExpression(n.removeFirstChild())));
      context.writeGeneratedNode(n);
    }

    /** Exposes YIELD operator so it's free of side effects transpiling some code on the way. */
    Node exposeYieldAndTranspileRest(Node n) {
      checkState(n.isGeneratorMarker());
      if (n.isYield()) {
        return n;
      }

      // Assuming the initial node is "a + (a = b) + (b = yield) + a".

      // YieldExposer may break n up into multiple statements.
      // Place n into a temporary block to hold those statements:
      // {
      //   var JSCompiler_temp_const$jscomp$0 = a + (a = b);
      //   return JSCompiler_temp_const$jscomp$0 + (b = yield) + a;
      // }
      // Need to put expression nodes into return node so that they always stay expression nodes
      // If expression put into expression result YieldExposer may turn it into an "if" statement.
      boolean isExpression = IR.mayBeExpression(n);
      Node block = IR.block(isExpression ? IR.returnNode(n) : n);
      NodeTraversal.traverseEs6(compiler, n, new YieldExposer());
      // Make sure newly created statements are correctly marked for recursive transpileStatement()
      // calls.
      NodeTraversal.traverseEs6(compiler, block, new YieldNodeMarker());

      // The last child of decomposed block free of side effects.
      Node decomposed = block.getLastChild().detach();
      transpileStatement(block);
      return isExpression ? decomposed.removeFirstChild() : decomposed;
    }

    /** Converts an expression node containing YIELD into an unmarked analogue. */
    Node maybeDecomposeExpression(@Nullable Node n) {
      if (n == null || !n.isGeneratorMarker()) {
        return n;
      }
      return transpileYields(exposeYieldAndTranspileRest(n));
    }

    /**
     * Makes unmarked node containing arbitary code suitable to write using {@link
     * TranspilationContext#writeGeneratedNode} method.
     */
    Node prepareNodeForWrite(@Nullable Node n) {
      if (n == null) {
        return null;
      }

      // Need to wrap a node so it can be replaced in the tree with some other node if nessesary.
      Node wrapper = IR.mayBeStatement(n) ? IR.block(n) : IR.exprResult(n);
      NodeTraversal.traverseEs6(compiler, wrapper, context.new UnmarkedNodeTranspiler());
      checkState(wrapper.hasOneChild());
      return wrapper.removeFirstChild();
    }

    /** Converts node with YIELD into $jscomp$generator$context.yieldResult. */
    Node transpileYields(Node n) {
      if (!n.isGeneratorMarker()) {
        // In some cases exposing yield causes it to disapear from the resulting statement.
        // I.e. the following node: "0 || yield;" becomes:
        // {
        //   var JSCompiler_temp$jscomp$0;
        //   if (JSCompiler_temp$jscomp$0 = 0); else JSCompiler_temp$jscomp$0 = yield;
        // }
        // JSCompiler_temp$jscomp$0; // This is our resulting statement.
        return n;
      }
      TranspilationContext.Case jumpToSection = context.createCase();
      Node yieldNode = findYield(n);
      Node yieldExpression =
          prepareNodeForWrite(maybeDecomposeExpression(yieldNode.removeFirstChild()));
      if (yieldNode.isYieldAll()) {
        context.yieldAll(yieldExpression, jumpToSection, yieldNode);
      } else {
        context.yield(yieldExpression, jumpToSection, yieldNode);
      }
      context.switchCaseTo(jumpToSection);
      Node yieldResult = context.yieldResult(yieldNode);
      if (yieldNode == n) {
        return yieldResult;
      }
      // Replace YIELD with $context.yeildResult
      yieldNode.replaceWith(yieldResult);
      // Remove generator markings from subtree
      while (yieldResult != n) {
        yieldResult = yieldResult.getParent();
        yieldResult.setGeneratorMarker(false);
      }
      return n;
    }

    /** Transpiles marked "if" stetement. */
    void transpileIf(Node n, @Nullable TranspilationContext.Case breakCase) {
      // Decompose condition first
      Node condition = maybeDecomposeExpression(n.removeFirstChild());
      Node ifBlock = n.getFirstChild();
      Node elseBlock = ifBlock.getNext();

      // No transpilation is needed
      if (!ifBlock.isGeneratorMarker() && (elseBlock == null || !elseBlock.isGeneratorMarker())) {
        n.addChildToFront(condition);
        n.setGeneratorMarker(false);
        transpileUnmarkedNode(n);
        return;
      }

      ifBlock.detach();
      if (elseBlock == null) {
        // No "else" block, just create an empty one as will need it anyway.
        elseBlock = IR.block().useSourceInfoFrom(n);
      } else {
        elseBlock.detach();
      }

      // Only "else" block is unmarked, swap "if" and "else" blocks and negate the condition.
      if (ifBlock.isGeneratorMarker() && !elseBlock.isGeneratorMarker()) {
        condition = IR.not(condition).useSourceInfoFrom(condition);
        Node tmpNode = ifBlock;
        ifBlock = elseBlock;
        elseBlock = tmpNode;
      }

      // Unmarked "if" block (marked "else")
      if (!ifBlock.isGeneratorMarker()) {
        TranspilationContext.Case endCase = context.maybeCreateCase(breakCase);
        Node jumoToBlock = context.createJumpToBlock(endCase, ifBlock);
        while (jumoToBlock.hasChildren()) {
          Node jumpToNode = jumoToBlock.removeFirstChild();
          jumpToNode.setGeneratorSafe(true);
          ifBlock.addChildToBack(jumpToNode);
        }
        transpileUnmarkedNode(IR.ifNode(condition, ifBlock).useSourceInfoFrom(n));
        transpileStatement(elseBlock);
        context.switchCaseTo(endCase);
        return;
      }

      TranspilationContext.Case ifCase = context.createCase();
      TranspilationContext.Case endCase = context.maybeCreateCase(breakCase);

      // "if" and "else" blocks marked
      context.writeGeneratedNode(
              IR.ifNode(prepareNodeForWrite(condition), context.createJumpToBlock(ifCase, n))
          .useSourceInfoFrom(n));
      transpileStatement(elseBlock);
      context.writeJumpTo(endCase, elseBlock);
      context.switchCaseTo(ifCase);
      transpileStatement(ifBlock);
      context.switchCaseTo(endCase);
    }

    /** Transpiles marked "for" statement. */
    void transpileFor(
        Node n,
        @Nullable TranspilationContext.Case breakCase,
        @Nullable TranspilationContext.Case continueCase) {
      // Decompose init first
      Node init = maybeDecomposeExpression(n.removeFirstChild());
      Node condition = n.getFirstChild();
      Node increment = condition.getNext();
      Node body = increment.getNext();

      // No transpilation is needed
      if (!condition.isGeneratorMarker()
          && !increment.isGeneratorMarker()
          && !body.isGeneratorMarker()) {
        n.addChildToFront(init);
        n.setGeneratorMarker(false);
        transpileUnmarkedNode(n);
        return;
      }

      // Move init expression out of for loop.
      if (!init.isEmpty()) {
        if (IR.mayBeExpression(init)) {
          // Convert expression into expression result.
          init = IR.exprResult(init).useSourceInfoFrom(init);
        }
        transpileUnmarkedNode(init);
      }

      TranspilationContext.Case startCase = context.createCase();
      startCase.markUsed(); // start of loop is referenced at the end, prevent case from collapsing.

      TranspilationContext.Case incrementCase = context.maybeCreateCase(continueCase);
      TranspilationContext.Case endCase = context.maybeCreateCase(breakCase);

      context.switchCaseTo(startCase);

      // Transpile condition expression
      if (!condition.isEmpty()) {
        condition = prepareNodeForWrite(maybeDecomposeExpression(condition.detach()));
        context.writeGeneratedNode(
            IR.ifNode(
                    IR.not(condition).useSourceInfoFrom(condition),
                    context.createJumpToBlock(endCase, n))
                .useSourceInfoFrom(n));
      }

      // Transpile "for" body
      context.pushBreakContinueContext(endCase, incrementCase);
      transpileStatement(body.detach());
      context.popBreakContinueContext();

      // Transpile increment expression
      context.switchCaseTo(incrementCase);
      if (!increment.isEmpty()) {
        increment = maybeDecomposeExpression(increment.detach());
        transpileUnmarkedNode(IR.exprResult(increment).useSourceInfoFrom(increment));
      }
      context.writeJumpTo(startCase, n);

      context.switchCaseTo(endCase);
    }

    /**
     * Transpile "for in" statement by converting it into "for".
     *
     * <p><code>for (var i in expr) {}</code> will be converted into <code>
     * for (var i, $for$in = $context.forIn(expr); i = $for$in.getNext(); ) {}</code>
     */
    void transpileForIn(
        Node n,
        @Nullable TranspilationContext.Case breakCase,
        @Nullable TranspilationContext.Case continueCase) {
      // Decompose condition first
      Node detachedCond = maybeDecomposeExpression(n.getSecondChild().detach());
      Node target = n.getFirstChild();
      Node body = n.getSecondChild();

      // No transpilation is needed
      if (!target.isGeneratorMarker() && !body.isGeneratorMarker()) {
        n.addChildAfter(detachedCond, target);
        n.setGeneratorMarker(false);
        transpileUnmarkedNode(n);
        return;
      }

      // Prepare a new init statement
      final Node init;
      if (target.detach().isVar()) {
        // "var i in x"    =>   "var i"
        checkState(!target.isGeneratorMarker());
        init = target;
        checkState(!init.getFirstChild().hasChildren());
        target = init.getFirstChild().cloneNode();
      } else {
        // "i in x"   =>    "var"
        init = new Node(Token.VAR).useSourceInfoFrom(target);
      }

      // "$for$in"
      Node forIn =
          context
              .getScopedName(GENERATOR_FORIN_PREFIX + compiler.getUniqueNameIdSupplier().get())
              .useSourceInfoFrom(target);
      // "$context.forIn(x)"
      forIn.addChildToFront(context.callContextMethod(target, "forIn", detachedCond));
      // "var ..., $for$in = $context.forIn(expr)"
      init.addChildToBack(forIn);

      // "(i = $for$in.getNext()) != null"
      Node forCond =
          IR.ne(
            IR.assign(
                    target,
                    IR.call(
                            IR.getprop(
                                    forIn.cloneNode(),
                                    IR.string("getNext").useSourceInfoFrom(detachedCond))
                                .useSourceInfoFrom(detachedCond))
                        .useSourceInfoFrom(detachedCond))
                .useSourceInfoFrom(detachedCond),
                IR.nullNode().useSourceInfoFrom(forIn))
          .useSourceInfoFrom(detachedCond);
      forCond.setGeneratorMarker(target.isGeneratorMarker());

      // Prepare "for" statement.
      // "for (var i, $for$in = $context.forIn(expr); (i = $for$in.getNext()) != null; ) {}"
      Node forNode =
          IR.forNode(init, forCond, IR.empty().useSourceInfoFrom(n), body.detach())
              .useSourceInfoFrom(n);

      // Transpile "for" instead of "for in".
      transpileFor(forNode, breakCase, continueCase);
    }

    /** Transpiles "while" statement. */
    void transpileWhile(
        Node n,
        @Nullable TranspilationContext.Case breakCase,
        @Nullable TranspilationContext.Case continueCase) {
      TranspilationContext.Case startCase = context.maybeCreateCase(continueCase);
      startCase.markUsed(); // start of loop is referenced at the end, prevent case from collapsing.
      TranspilationContext.Case endCase = context.maybeCreateCase(breakCase);

      context.switchCaseTo(startCase);

      // Transpile condition
      Node condition = prepareNodeForWrite(maybeDecomposeExpression(n.removeFirstChild()));
      Node body = n.removeFirstChild();
      context.writeGeneratedNode(
          IR.ifNode(
                  IR.not(condition).useSourceInfoFrom(condition),
                  context.createJumpToBlock(endCase, n))
              .useSourceInfoFrom(n));

      // Transpile "while" body
      context.pushBreakContinueContext(endCase, startCase);
      transpileStatement(body);
      context.popBreakContinueContext();
      context.writeJumpTo(startCase, n);

      context.switchCaseTo(endCase);
    }

    /** Transpiles "do while" statement. */
    void transpileDo(
        Node n,
        @Nullable TranspilationContext.Case breakCase,
        @Nullable TranspilationContext.Case continueCase) {
      TranspilationContext.Case startCase = context.createCase();
      startCase.markUsed(); // start of loop is referenced at the end, prevent case from collapsing.
      breakCase = context.maybeCreateCase(breakCase);
      continueCase = context.maybeCreateCase(continueCase);

      context.switchCaseTo(startCase);

      // Transpile body
      Node body = n.removeFirstChild();
      context.pushBreakContinueContext(breakCase, continueCase);
      transpileStatement(body);
      context.popBreakContinueContext();

      // Transpile condition
      context.switchCaseTo(continueCase);
      Node condition = prepareNodeForWrite(maybeDecomposeExpression(n.removeFirstChild()));
      context.writeGeneratedNode(
          IR.ifNode(condition, context.createJumpToBlock(startCase, n)).useSourceInfoFrom(n));
      context.switchCaseTo(breakCase);
    }

    /** Transpiles "try" statement */
    void transpileTry(Node n, @Nullable TranspilationContext.Case breakCase) {
      Node tryBlock = n.removeFirstChild();
      Node catchBlock = n.removeFirstChild();
      Node finallyBlock = n.removeFirstChild();

      TranspilationContext.Case catchCase = catchBlock.hasChildren() ? context.createCase() : null;
      TranspilationContext.Case finallyCase = finallyBlock == null ? null : context.createCase();
      TranspilationContext.Case endCase = context.maybeCreateCase(breakCase);

      // Transpile "try" block
      context.enterTryBlock(catchCase, finallyCase, tryBlock);
      transpileStatement(tryBlock);

      if (finallyBlock == null) {
        context.leaveTryBlock(catchCase, endCase, tryBlock);
      } else {
        // Transpile "finally" block
        context.switchCaseTo(finallyCase);
        context.enterFinallyBlock(catchCase, finallyCase, finallyBlock);
        transpileStatement(finallyBlock);
        context.leaveFinallyBlock(endCase, finallyBlock);
      }

      // Transpile "catch" block
      if (catchBlock.hasChildren()) {
        checkState(catchBlock.getFirstChild().isCatch());

        context.switchCaseTo(catchCase);
        Node exceptionName = catchBlock.getFirstFirstChild().detach();
        context.enterCatchBlock(finallyCase, exceptionName);

        Node catchBody = catchBlock.getFirstFirstChild().detach();
        checkState(catchBody.isNormalBlock());
        transpileStatement(catchBody);
        context.leaveCatchBlock(finallyCase, catchBody);
      }

      context.switchCaseTo(endCase);
    }

    // Transpiles "switch" statement.
    void transpileSwitch(Node n, @Nullable TranspilationContext.Case breakCase) {
      // Transpile condition first
      n.addChildToFront(maybeDecomposeExpression(n.removeFirstChild()));

      // Are all "switch" cases unmarked?
      boolean hasGeneratorMarker = false;
      for (Node caseSection = n.getSecondChild();
          caseSection != null;
          caseSection = caseSection.getNext()) {
        if (caseSection.isGeneratorMarker()) {
          hasGeneratorMarker = true;
          break;
        }
      }
      // No transpilation is needed
      if (!hasGeneratorMarker) {
        n.setGeneratorMarker(false);
        transpileUnmarkedNode(n);
        return;
      }

      /** Stores a detached body of a case statement and a case section assosiated with it. */
      class SwitchCase {
        private final TranspilationContext.Case generatedCase;
        private final Node body;

        SwitchCase(TranspilationContext.Case generatedCase, Node caseNode) {
          this.generatedCase = generatedCase;
          this.body = caseNode;
        }
      }

      // TODO(skill): Don't move all case sections.
      ArrayList<SwitchCase> detachedCases = new ArrayList<>();

      // We don't have to transpile unmarked cases at the beginning of "switch".
      boolean canSkipUnmarkedCases = true;
      for (Node caseSection = n.getSecondChild();
          caseSection != null;
          caseSection = caseSection.getNext()) {
        if (!caseSection.isDefaultCase() && caseSection.getFirstChild().isGeneratorMarker()) {
          // Following example is possible to transpile, but it's not trivial.
          // switch (cond) {
          //   case yield "test": break;
          //   case 5 + yield:  break;
          // }
          compiler.report(
              JSError.make(
                  n, Es6ToEs3Util.CANNOT_CONVERT_YET, "Case statements that contain yields"));
          return;
        }
        Node body = caseSection.getLastChild();

        if (!body.hasChildren() || (canSkipUnmarkedCases && !body.isGeneratorMarker())) {
          // Can skip empty or unmarked case.
          continue;
        }

        canSkipUnmarkedCases = false;

        // Check whether we can start skipping unmarked cases again
        if (!canSkipUnmarkedCases && !body.isGeneratorMarker()) {
          List<DiGraphEdge<Node, Branch>> inEdges = controlFlowGraph.getInEdges(body);
          if (inEdges.size() == 1) {
            checkState(Iterables.getOnlyElement(inEdges).getSource().getValue() == caseSection);
            canSkipUnmarkedCases = true;
            continue;
          }
        }

        // Move case's body under a global switch statement...

        // Allocate a new case
        TranspilationContext.Case generatedCase = context.createCase();
        generatedCase.caseNode.useSourceInfoFrom(caseSection);
        generatedCase.caseBlock.useSourceInfoFrom(body);

        // Replace old body with a jump instruction.
        Node newBody = IR.block(context.createJumpToNode(generatedCase, body));
        newBody.setIsAddedBlock(true);
        newBody.setGeneratorSafe(true); // make sure we don't transpile generated "jump" instruction
        body.replaceWith(newBody);

        // Remember the body and the case under which the body will be moved.
        detachedCases.add(new SwitchCase(generatedCase, body));

        caseSection.setGeneratorMarker(false);
      }

      TranspilationContext.Case endCase = context.maybeCreateCase(breakCase);

      // Transpile the barebone of original "switch" statement
      n.setGeneratorMarker(false);
      transpileUnmarkedNode(n);
      context.writeJumpTo(endCase, n); // TODO(skill): do not always add this.

      // Transpile all detached case bodies
      context.pushBreakContext(endCase);
      for (SwitchCase detachedCase : detachedCases) {
        TranspilationContext.Case generatedCase = detachedCase.generatedCase;
        context.switchCaseTo(generatedCase);
        transpileStatement(detachedCase.body);
      }
      context.popBreakContext();

      context.switchCaseTo(endCase);
    }

    /** Finds the only YIELD node in a tree. */
    Node findYield(Node n) {
      YieldFinder yieldFinder = new YieldFinder();
      NodeTraversal.traverseEs6(compiler, n, yieldFinder);
      return yieldFinder.getYieldNode();
    }

    /** Finds the only YIELD node in a tree. */
    private class YieldFinder extends NodeTraversal.AbstractPreOrderCallback {

      private Node yieldNode;

      @Override
      public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
        if (n.isFunction()) {
          return false;
        }
        if (n.isYield()) {
          checkState(yieldNode == null);
          yieldNode = n;
          return false;
        }
        return true;
      }

      Node getYieldNode() {
        checkNotNull(yieldNode);
        return yieldNode;
      }
    }

    /** State machine context that is used during generator function transpilation. */
    private class TranspilationContext {

      /** Most recently assigned id. */
      int caseIdCounter;

      /**
       * Points to the switch case that is being populated with transpiled instructions from the
       * original generator function that is being transpiled.
       */
      private Case currentCase;

      private final HashMap<String, LabelCases> namedLabels = new HashMap<>();
      private final ArrayDeque<Case> breakCases = new ArrayDeque<>();
      private final ArrayDeque<Case> continueCases = new ArrayDeque<>();

      private final ArrayDeque<CatchCase> catchCases = new ArrayDeque<>();
      private final ArrayDeque<Case> finallyCases = new ArrayDeque<>();
      private final HashSet<String> catchNames = new HashSet<>();
      int nestedFinallyBlockCount = 0;

      boolean thisReferenceFound;
      boolean argumentsReferenceFound;

      TranspilationContext() {
        currentCase = new Case(IR.caseNode(IR.number(++caseIdCounter), IR.block()));
      }

      /** Ensures that the context has an empty state. */
      public void checkStateIsEmpty() {
        checkState(namedLabels.isEmpty());
        checkState(breakCases.isEmpty());
        checkState(continueCases.isEmpty());
        checkState(catchCases.isEmpty());
        checkState(finallyCases.isEmpty());
        checkState(nestedFinallyBlockCount == 0);
      }

      /** Adds a block of original code to the end of the current case. */
      void transpileUnmarkedBlock(Node block) {
        if (block.hasChildren()) {
          NodeTraversal.traverseEs6(compiler, block, new UnmarkedNodeTranspiler());
          while (block.hasChildren()) {
            writeGeneratedNode(block.removeFirstChild());
          }
        }
      }

      /** Adds a new generated node to the end of the current case. */
      void writeGeneratedNode(Node n) {
        currentCase.addNode(n);
      }

      /** Creates a new detached case statement. */
      Case createCase() {
        return new Case();
      }

      /** Returns a passed case object or creates a new one if it's null. */
      Case maybeCreateCase(@Nullable Case other) {
        if (other != null) {
          return other;
        }
        return createCase();
      }

      /** Returns the name node of context parameter passed to the program. */
      Node getJsContextNameNode(Node sourceNode) {
        return getScopedName(GENERATOR_CONTEXT).useSourceInfoFrom(sourceNode);
      }

      /** Returns unique name in the current context. */
      Node getScopedName(String name) {
        return IR.name(name + (generatorNestingLevel == 0 ? "" : "$" + generatorNestingLevel));
      }

      /** Creates node that access a specified field of the current context. */
      Node getContextField(Node sourceNode, String fieldName) {
        return IR.getprop(
                getJsContextNameNode(sourceNode),
                IR.string(fieldName).useSourceInfoFrom(sourceNode))
            .useSourceInfoFrom(sourceNode);
      }

      /** Creates node that make a call to a context function. */
      Node callContextMethod(Node sourceNode, String methodName, Node... args) {
        return IR.call(getContextField(sourceNode, methodName), args).useSourceInfoFrom(sourceNode);
      }

      /** Creates node that make a call to a context function. */
      Node callContextMethodResult(Node sourceNode, String methodName, Node... args) {
        return IR.exprResult(callContextMethod(sourceNode, methodName, args))
            .useSourceInfoFrom(sourceNode);
      }

      /** Creates node that returns the result of a call to a context function. */
      Node returnContextMethod(Node sourceNode, String methodName, Node... args) {
        return IR.returnNode(callContextMethod(sourceNode, methodName, args))
            .useSourceInfoFrom(sourceNode);
      }

      /**
       * Returns a node that instructs a state machine program to jump to a selected case section.
       */
      Node createJumpToNode(Case section, Node sourceNode) {
        return returnContextMethod(sourceNode, "jumpTo", section.getNumber(sourceNode));
      }

      /** Instructs a state machine program to jump to a selected case section. */
      void writeJumpTo(Case section, Node sourceNode) {
        writeGeneratedNode(
            callContextMethodResult(sourceNode, "jumpTo", section.getNumber(sourceNode)));
        writeGeneratedNode(IR.breakNode().useSourceInfoFrom(sourceNode));
      }

      /** Creates a block node that contains a jump instruction. */
      Node createJumpToBlock(Case section, Node sourceNode) {
        return IR.block(
                callContextMethodResult(sourceNode, "jumpTo", section.getNumber(sourceNode)),
                IR.breakNode().useSourceInfoFrom(sourceNode))
            .useSourceInfoFrom(sourceNode);
      }

      /** Converts "break" and "continue" statements into state machine jumps. */
      void replaceBreakContinueWithJump(Node sourceNode, Case section, int breakSuppressors) {
        final String jumpMethod;
        if (finallyCases.isEmpty() || finallyCases.getFirst().id < section.id) {
          // There are no finally blocks that should be exectuted pior to jumping
          jumpMethod = "jumpTo";
        } else {
          // There are some finally blocks that should be exectuted before we can break/continue.
          checkState(finallyCases.getFirst().id != section.id);
          jumpMethod = "jumpThroughFinallyBlocks";
        }
        if (breakSuppressors == 0) {
          // continue;  =>  $context.jumpTo(x); break;
          sourceNode
              .getParent()
              .addChildBefore(
                  callContextMethodResult(sourceNode, jumpMethod, section.getNumber(sourceNode)),
                  sourceNode);
          sourceNode.replaceWith(IR.breakNode());
        } else {
          // "break;" inside a loop or swtich statement:
          // for (...) {
          //   break l1;
          // }
          // becomes:
          // for (...) {                  // loop doesn't allow to use "break" to advance to the
          //   return $context.jumpTo(x); // next address, so "return" is used instead.
          // }
          sourceNode.replaceWith(
              returnContextMethod(sourceNode, jumpMethod, section.getNumber(sourceNode)));
        }
      }

      /**
       * Instructs a state machine program to yield a value and then jump to a selected case
       * section.
       */
      void yield(
          @Nullable Node expression, TranspilationContext.Case jumpToSection, Node sourceNode) {
        ArrayList<Node> args = new ArrayList<>();
        args.add(
            expression == null ? IR.name("undefined").useSourceInfoFrom(sourceNode) : expression);
        args.add(jumpToSection.getNumber(sourceNode));
        context.writeGeneratedNode(
            returnContextMethod(sourceNode, "yield", args.toArray(new Node[0])));
      }

      /**
       * Instructs a state machine program to yield all values and then jump to a selected case
       * section.
       */
      void yieldAll(Node expression, TranspilationContext.Case jumpToSection, Node sourceNode) {
        writeGeneratedNode(
            returnContextMethod(
                sourceNode, "yieldAll", expression, jumpToSection.getNumber(sourceNode)));
      }

      /** Instructs a state machine program to return a given expression. */
      Node returnExpression(@Nullable Node expression) {
        if (expression == null) {
          expression = IR.name("undefined").useSourceInfoFrom(expression);
        }
        return callContextMethod(expression, "return", expression);
      }

      /** Instructs a state machine program to consume a yield result after yielding. */
      Node yieldResult(Node sourceNode) {
        return getContextField(sourceNode, "yieldResult");
      }

      /** Adds references to catch and finally blocks to the transpilation context. */
      private void addCatchFinallyCases(@Nullable Case catchCase, @Nullable Case finallyCase) {
        if (finallyCase != null) {
          if (!catchCases.isEmpty()) {
            ++catchCases.getFirst().finallyBlocks;
          }
          finallyCases.addFirst(finallyCase);
        }
        if (catchCase != null) {
          catchCases.addFirst(new CatchCase(catchCase));
        }
      }

      /** Returns the case section of the next catch block that is not hidden by finally blocks. */
      @Nullable
      private Case getNextCatchCase() {
        for (CatchCase catchCase : catchCases) {
          if (catchCase.finallyBlocks == 0) {
            return catchCase.catchCase;
          }
          break;
        }
        return null;
      }

      /** Returns the case section of the next finally block. */
      @Nullable
      private Case getNextFinallyCase() {
        return finallyCases.isEmpty() ? null : finallyCases.getFirst();
      }

      /** Removes references to catch and finally blocks from the transpilation context. */
      private void removeCatchFinallyCases(@Nullable Case catchCase, @Nullable Case finallyCase) {
        if (catchCase != null) {
          CatchCase lastCatch = catchCases.removeFirst();
          checkState(lastCatch.finallyBlocks == 0);
          checkState(lastCatch.catchCase == catchCase);
        }
        if (finallyCase != null) {
          if (!catchCases.isEmpty()) {
            int finallyBlocks = --catchCases.getFirst().finallyBlocks;
            checkState(finallyBlocks >= 0);
          }
          Case lastFinally = finallyCases.removeFirst();
          checkState(lastFinally == finallyCase);
        }
      }

      /** Writes a statement Node that should be placed at the beginning of try block. */
      void enterTryBlock(@Nullable Case catchCase, @Nullable Case finallyCase, Node sourceNode) {
        addCatchFinallyCases(catchCase, finallyCase);

        final String methodName;
        ArrayList<Node> args = new ArrayList<>();
        if (catchCase == null) {
          methodName = "setFinallyBlock";
          args.add(finallyCase.getNumber(sourceNode));
        } else {
          methodName = "setCatchFinallyBlocks";
          args.add(catchCase.getNumber(sourceNode));
          if (finallyCase != null) {
            args.add(finallyCase.getNumber(sourceNode));
          }
        }
        writeGeneratedNode(
            callContextMethodResult(sourceNode, methodName, args.toArray(new Node[0])));
      }

      /**
       * Writes a statements that should be placed at the end of try block if finally block is not
       * present.
       */
      void leaveTryBlock(@Nullable Case catchCase, Case endCase, Node sourceNode) {
        removeCatchFinallyCases(catchCase, null);
        ArrayList<Node> args = new ArrayList<>();
        args.add(endCase.getNumber(sourceNode));
        // Find the next catch block that is not hidden by any finally blocks.
        Case nextCatchCase = getNextCatchCase();
        if (nextCatchCase != null) {
          args.add(nextCatchCase.getNumber(sourceNode));
        }
        writeGeneratedNode(
            callContextMethodResult(sourceNode, "leaveTryBlock", args.toArray(new Node[0])));
        writeGeneratedNode(IR.breakNode().useSourceInfoFrom(sourceNode));
      }

      /** Writes a statement Node that should be placed at the beginning of catch block. */
      void enterCatchBlock(@Nullable Case finallyCase, Node exceptionName) {
        checkState(exceptionName.isName());
        addCatchFinallyCases(null, finallyCase);

        // Find the next catch block that is not hidden by any finally blocks.
        Case nextCatchCase = getNextCatchCase();

        if (catchNames.add(exceptionName.getString())) {
          newGeneratorBody.addChildBefore(
              IR.var(exceptionName.cloneNode()).useSourceInfoFrom(exceptionName),
              newGeneratorBody.getLastChild());
        }

        ArrayList<Node> args = new ArrayList<>();
        if (nextCatchCase != null) {
          args.add(nextCatchCase.getNumber(exceptionName));
        }

        writeGeneratedNode(
            IR.exprResult(
                    IR.assign(
                            exceptionName,
                            callContextMethod(
                                exceptionName, "enterCatchBlock", args.toArray(new Node[0])))
                        .useSourceInfoFrom(exceptionName))
                .useSourceInfoFrom(exceptionName));
      }

      /** Writes a statement to jump to the finally block if it's present. */
      void leaveCatchBlock(@Nullable Case finallyCase, Node sourceNode) {
        if (finallyCase != null) {
          removeCatchFinallyCases(null, finallyCase);
          writeJumpTo(finallyCase, sourceNode);
        }
      }

      /** Writes a Node that should be placed at the beginning of finally block. */
      void enterFinallyBlock(
          @Nullable Case catchCase, @Nullable Case finallyCase, Node sourceNode) {
        removeCatchFinallyCases(catchCase, finallyCase);

        Case nextCatchCase = getNextCatchCase();
        Case nextFinallyCase = getNextFinallyCase();

        ArrayList<Node> args = new ArrayList<>();
        if (nestedFinallyBlockCount == 0) {
          if (nextCatchCase != null || nextFinallyCase != null) {
            args.add(
                nextCatchCase == null
                    ? IR.number(0).useSourceInfoFrom(sourceNode)
                    : nextCatchCase.getNumber(sourceNode));
            if (nextFinallyCase != null) {
              args.add(nextFinallyCase.getNumber(sourceNode));
            }
          }
        } else {
          args.add(
              nextCatchCase == null
                  ? IR.number(0).useSourceInfoFrom(sourceNode)
                  : nextCatchCase.getNumber(sourceNode));
          args.add(
              nextFinallyCase == null
                  ? IR.number(0).useSourceInfoFrom(sourceNode)
                  : nextFinallyCase.getNumber(sourceNode));
          args.add(IR.number(nestedFinallyBlockCount).useSourceInfoFrom(sourceNode));
        }

        writeGeneratedNode(
            callContextMethodResult(sourceNode, "enterFinallyBlock", args.toArray(new Node[0])));

        ++nestedFinallyBlockCount;
      }

      /** Writes a Node that should be placed at the end of finally block. */
      void leaveFinallyBlock(Case endCase, Node sourceNode) {
        ArrayList<Node> args = new ArrayList<>();
        args.add(endCase.getNumber(sourceNode));
        if (--nestedFinallyBlockCount != 0) {
          args.add(IR.number(nestedFinallyBlockCount).useSourceInfoFrom(sourceNode));
        }

        writeGeneratedNode(
            callContextMethodResult(sourceNode, "leaveFinallyBlock", args.toArray(new Node[0])));
        writeGeneratedNode(IR.breakNode().useSourceInfoFrom(sourceNode));
      }

      /** Changes the {@link #currentCase} to a new one. */
      void switchCaseTo(@Nullable Case caseSection) {
        if (caseSection != null) {
          currentCase = caseSection.insertAfter(currentCase);
        }
      }

      /** Adds a named labels to the context. */
      public void pushLabels(
          ArrayList<Node> labelNames, Case breakCase, @Nullable Case continueCase) {
        for (Node labelName : labelNames) {
          checkState(labelName.isLabelName());
          namedLabels.put(labelName.getString(), new LabelCases(breakCase, continueCase));
        }
      }

      /** Removes the named labels from the context. */
      public void popLabels(ArrayList<Node> labelNames) {
        for (Node labelName : labelNames) {
          checkState(labelName.isLabelName());
          namedLabels.remove(labelName.getString());
        }
      }

      /** Adds "break" jump point to the context */
      public void pushBreakContext(Case breakCase) {
        breakCases.push(breakCase);
      }

      /** Adds "break" and "continue" jump points to the context */
      public void pushBreakContinueContext(Case breakCase, Case continueCase) {
        pushBreakContext(breakCase);
        continueCases.push(continueCase);
      }

      /** Removes "break" jump point from the context, restoring the previous one */
      public void popBreakContext() {
        breakCases.pop();
      }

      /**
       * Removes "break" and "continue" jump points from the context, restoring the previous ones.
       */
      public void popBreakContinueContext() {
        popBreakContext();
        continueCases.pop();
      }

      /** A case section in a switch block of generator program. */
      private class Case {
        final int id;
        final Node caseNode;
        final Node caseBlock;

        /**
         * Records number of times the section was referenced.
         *
         * <p>It's used to drop unreferenced sections.
         */
        int referenceCount;

        /** Creates a Case object for an already created case node. */
        Case(Node caseNode) {
          checkState(caseNode.isCase());
          this.id = NodeUtil.getNumberValue(caseNode.getFirstChild()).intValue();
          this.caseNode = caseNode;
          this.caseBlock = caseNode.getLastChild();
          referenceCount = 1;
        }

        /** Creates a new empty case section and assings a new id. */
        Case() {
          id = ++caseIdCounter;
          caseNode =
              IR.caseNode(IR.number(id), caseBlock = IR.block())
                  .useSourceInfoFromForTree(originalGeneratorBody);
          referenceCount = 0;
        }

        /** Returns the number node of the case section and increments a reference counter. */
        Node getNumber(Node sourceNode) {
          markUsed();
          return IR.number(id).useSourceInfoFrom(sourceNode);
        }

        /** Increases a reference counter. */
        void markUsed() {
          ++referenceCount;
        }

        /** Adds a new node to the end of the case block. */
        void addNode(Node n) {
          checkState(IR.mayBeStatement(n));
          caseBlock.addChildToBack(n);
        }

        /**
         * Chains two case sections togeter.
         *
         * @return A combined section.
         */
        Case insertAfter(Case other) {
          checkState(caseNode.getParent() == null);
          checkState(other.caseNode.getParent() != null);
          if (referenceCount == 0) {
            // No references, just merge with the previous case
            other.caseBlock.addChildrenToBack(caseBlock.removeChildren());
            return other;
          } else {
            other.caseNode.getParent().addChildAfter(caseNode, other.caseNode);
            return this;
          }
        }
      }

      /**
       * Adjust YIELD-free nodes to run correctly inside a state machine program.
       *
       * <p>The following transformations are performed:
       *
       * <ul>
       *   <li>moving <code>var</code> into hois scope;
       *   <li>transpiling <code>return</code> statements;
       *   <li>transpiling <code>break</code> and <code>continue</code> statements;
       *   <li>transpiling references to <code>this</code> and <code>arguments</code>.
       * </ul>
       */
      private class UnmarkedNodeTranspiler implements NodeTraversal.Callback {

        // Count the number of enclosing statements that a bare break could address.
        // A value > 0 means that a bare break statement we encounter can be left unmodified,
        // since it addresses a statement within the node we are transpiling.
        int breakSuppressors;
        // Same as breakSuppressors, but for bare continue statements.
        int continueSuppressors;

        @Override
        public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
          if (n.isGeneratorSafe()) {
            // Skip nodes that were generated by the compiler.
            n.setGeneratorSafe(false);
            return false;
          }

          checkState(!n.isGeneratorMarker());
          checkState(!n.isSuper(), "Reference to SUPER is not supported");

          if (NodeUtil.isLoopStructure(n)) {
            ++continueSuppressors;
            ++breakSuppressors;
          } else if (n.isSwitch()) {
            ++breakSuppressors;
          }

          if (n.isBreak() || n.isContinue()) {
            if (n.hasChildren()) {
              visitNamedBreakContinue(n);
            } else {
              visitBreakContinue(n);
            }
            return false;
          }

          return !n.isFunction();
        }

        @Override
        public void visit(NodeTraversal t, Node n, Node parent) {
          if (NodeUtil.isLoopStructure(n)) {
            --continueSuppressors;
            --breakSuppressors;
          } else if (n.isSwitch()) {
            --breakSuppressors;
          } else if (n.isThis()) {
            visitThis(n);
          } else if (n.isReturn()) {
            visitReturn(n);
          } else if (n.isName() && n.getString().equals("arguments")) {
            visitArguments(n);
          } else if (n.isVar()) {
            // don't transpile var in "for (var i = 0;;)"
            if (!(parent.isVanillaFor() || parent.isForIn()) || parent.getFirstChild() != n) {
              visitVar(n);
            }
          } // else no changes need to be made
        }

        /** Adjust return statements. */
        void visitReturn(Node n) {
          Node returnExpression = n.removeFirstChild();
          if (returnExpression == null) {
            // return;   =>   return $context.return(undefined);
            returnExpression = IR.name("undefined").useSourceInfoFrom(n);
          }
          // return ...;   =>   return $context.return(...);
          n.addChildToFront(returnExpression(returnExpression));
        }

        /** Converts labeled <code>break</code> or <code>continue</code> statement into a jump. */
        void visitNamedBreakContinue(Node n) {
          checkState(n.getFirstChild().isLabelName());
          LabelCases cases = namedLabels.get(n.getFirstChild().getString());
          if (cases != null) {
            Case caseSection = n.isBreak() ? cases.breakCase : cases.continueCase;
            context.replaceBreakContinueWithJump(n, caseSection, breakSuppressors);
          }
        }

        /** Converts <code>break</code> or <code>continue</code> statement into a jump. */
        void visitBreakContinue(Node n) {
          Case caseSection = null;
          if (n.isBreak() && breakSuppressors == 0) {
            caseSection = breakCases.getFirst();
          }
          if (n.isContinue() && continueSuppressors == 0) {
            caseSection = continueCases.getFirst();
          }
          if (caseSection != null) {
            context.replaceBreakContinueWithJump(n, caseSection, breakSuppressors);
          }
        }

        /** Replaces reference to <code>this</code> with <code>$jscomp$generator$this</code>. */
        void visitThis(Node n) {
          Node newThis = context.getScopedName(GENERATOR_THIS).useSourceInfoFrom(n);
          n.replaceWith(newThis);
          if (!thisReferenceFound) {
            Node var = IR.var(newThis.cloneNode(), n).useSourceInfoFrom(newGeneratorBody);
            JSDocInfoBuilder jsDoc = new JSDocInfoBuilder(false);
            jsDoc.recordConstancy();
            var.setJSDocInfo(jsDoc.build());
            newGeneratorBody.addChildBefore(var, newGeneratorBody.getLastChild());
            thisReferenceFound = true;
          }
        }

        /**
         * Replaces reference to <code>arguments</code> with <code>$jscomp$generator$arguments
         * </code>.
         */
        void visitArguments(Node n) {
          Node newArguments = context.getScopedName(GENERATOR_ARGUMENTS).useSourceInfoFrom(n);
          n.replaceWith(newArguments);
          if (!argumentsReferenceFound) {
            Node var =
                IR.var(newArguments.cloneNode(), n).useSourceInfoFrom(newGeneratorBody);
            JSDocInfoBuilder jsDoc = new JSDocInfoBuilder(false);
            jsDoc.recordConstancy();
            var.setJSDocInfo(jsDoc.build());
            newGeneratorBody.addChildBefore(var, newGeneratorBody.getLastChild());
            argumentsReferenceFound = true;
          }
        }

        /** Removes {@code @const} annotation from the node if present. */
        void maybeRemoveConstAnnotation(Node n) {
          if (n.getJSDocInfo() != null && n.getJSDocInfo().hasConstAnnotation()) {
            // TODO(skill): report a warning that @const will be ignored.
            JSDocInfoBuilder fixedJSDoc = JSDocInfoBuilder.copyFrom(n.getJSDocInfo());
            fixedJSDoc.clearConstancy();
            n.setJSDocInfo(fixedJSDoc.build());
          }
        }

        /** Moves JsDocInfo from one node to another */
        void moveJsDocInfo(Node from, Node to) {
          checkState(to.getJSDocInfo() == null);
          JSDocInfo jsDocInfo = from.getJSDocInfo();
          if (jsDocInfo != null) {
            from.setJSDocInfo(null);
            to.setJSDocInfo(jsDocInfo);
          }
        }

        /**
         * Hoists {@code var} statements into the closure containing the generator to preserve their
         * state across multiple invocation of state machine program.
         *
         * <p>
         *
         * <pre>
         * var a = "test", b = i + 5;
         * </pre>
         *
         * is transpiled to:
         *
         * <pre>
         * var a, b;
         * a = "test", b = i + 5;
         * </pre>
         */
        void visitVar(Node varStatement) {
          maybeRemoveConstAnnotation(varStatement);
          ArrayList<Node> assignments = new ArrayList<>();
          for (Node varName = varStatement.getFirstChild();
              varName != null;
              varName = varName.getNext()) {
            if (varName.hasChildren()) {
              Node copiedVarName = varName.cloneNode();
              Node assign =
                  IR.assign(copiedVarName, varName.removeFirstChild()).useSourceInfoFrom(varName);
              moveJsDocInfo(copiedVarName, assign);
              assign.setTypeI(varName.getTypeI());
              assignments.add(assign);
            }
            // Variable assignment will keep @const declaration, if any, but we must remove it from
            // the name declaration.
            maybeRemoveConstAnnotation(varName);
          }
          if (assignments.isEmpty()) {
            varStatement.detach();
          } else {
            Node commaExpression = null;
            for (Node assignment : assignments) {
              commaExpression =
                  commaExpression == null
                      ? assignment
                      : IR.comma(commaExpression, assignment).useSourceInfoFrom(assignment);
            }
            varStatement.replaceWith(IR.exprResult(commaExpression));
          }
          // Place original var statement with initial values removed to just before
          // the program method definition.
          newGeneratorBody.addChildBefore(varStatement, newGeneratorBody.getLastChild());
        }
      }

      /** Reprasents a catch case that is used by try/catch transpilation */
      class CatchCase {
        final Case catchCase;

        /**
         * Number of finally blocks that should be executed before exception can be handled by this
         * catch case.
         */
        int finallyBlocks;

        CatchCase(Case catchCase) {
          this.catchCase = catchCase;
        }
      }

      /** Stores "break" and "continue" case sections assosiated with a label. */
      class LabelCases {

        final Case breakCase;

        @Nullable final Case continueCase;

        LabelCases(Case breakCase, @Nullable Case continueCase) {
          this.breakCase = breakCase;
          this.continueCase = continueCase;
        }
      }
    }
  }

  /** Marks "yield" nodes and propagates this information up through the tree */
  private static class YieldNodeMarker implements NodeTraversal.Callback {

    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      return !n.isFunction();
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isYield()) {
        n.setGeneratorMarker(true);
      }

      // This class is used on a tree that is detached from the main AST, so this will not end up
      // marking the parent of the node used to start the traversal.
      if (parent != null && n.isGeneratorMarker()) {
        parent.setGeneratorMarker(true);
      }
    }
  }
}
