/*
 * Copyright 2017 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.AstFactory.type;

import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticScope;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.List;

/** Converts ES6 "for of" loops to ES5. */
public final class Es6ForOfConverter extends NodeTraversal.AbstractPostOrderCallback
    implements CompilerPass {
  private final AbstractCompiler compiler;
  private static final FeatureSet transpiledFeatures = FeatureSet.BARE_MINIMUM.with(Feature.FOR_OF);
  private final DefaultNameGenerator namer;
  private final AstFactory astFactory;
  private final StaticScope namespace;

  private static final String ITER_BASE = "$jscomp$iter$";

  private static final String ITER_RESULT = "$jscomp$key$";

  private static final String RET_FN = "$jscomp$retFn$";

  public Es6ForOfConverter(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.namer = new DefaultNameGenerator();
    this.astFactory = compiler.createAstFactory();
    this.namespace = compiler.getTranspilationNamespace();
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, root, transpiledFeatures);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isForOf()) {
      visitForOf(t, n);
    }
  }

  // TODO(lharker): break up this method
  private void visitForOf(NodeTraversal t, Node node) {
    // `var v` or `let v` or v any valid lhs
    Node variable = node.removeFirstChild();
    Node iterable = node.removeFirstChild();
    Node body = node.removeFirstChild();
    JSDocInfo varJSDocInfo = variable.getJSDocInfo();
    // `$jscomp$iter$0`
    Node iterName =
        astFactory.createName(
            ITER_BASE + compiler.getUniqueIdSupplier().getUniqueId(t.getInput()),
            type(StandardColors.ITERATOR_ID));
    iterName.makeNonIndexable();
    // `$jscomp$iter$0.next()`
    Node getNext =
        astFactory.createCallWithUnknownType(
            astFactory.createGetPropWithUnknownType(iterName.cloneTree(), "next"));
    // generate a unique iterator result name for every for-of loop getting rewritten to avoid
    // conflicts
    String iteratorResultName =
        ITER_RESULT + compiler.getUniqueIdSupplier().getUniqueId(t.getInput()) + "$";

    if (NodeUtil.isNameDeclaration(variable)) {
      iteratorResultName += variable.getFirstChild().getString();
    } else if (variable.isName()) {
      iteratorResultName += variable.getString();
    } else {
      // give arbitrary lhs expressions an arbitrary name
      iteratorResultName += namer.generateNextName();
    }
    // `$jscomp$key$extraName`
    Node iterResult = astFactory.createNameWithUnknownType(iteratorResultName);
    iterResult.makeNonIndexable();

    String returnFuncName = RET_FN + compiler.getUniqueIdSupplier().getUniqueId(t.getInput());
    Node retFn = astFactory.createNameWithUnknownType(returnFuncName);
    retFn.makeNonIndexable();

    // `$jscomp.makeIterator(iterable)`
    Node callMakeIterator =
        astFactory
            .createJSCompMakeIteratorCall(iterable, this.namespace)
            .srcrefTreeIfMissing(iterable);
    // `var $jscomp$iter$0 = $jscomp.makeIterator(iterable)`
    Node initIter = IR.var(iterName.cloneTree(), callMakeIterator).srcrefTreeIfMissing(iterable);
    // var $jscomp$key$extraName = $jscomp$iter$0.next();
    Node initIterResult =
        IR.var(iterResult.cloneTree(), getNext.cloneTree()).srcrefTreeIfMissing(iterable);
    // var $jscomp$retFn$0;
    Node initRetFn = IR.var(retFn.cloneTree()).srcrefTreeIfMissing(iterable);

    // !$jscomp$key$extraName.done
    Node cond =
        astFactory.createNot(
            astFactory.createGetProp(iterResult.cloneTree(), "done", type(StandardColors.BOOLEAN)));
    // $jscomp$key$extraName = $jscomp$iter$0.next()
    Node incr = astFactory.createAssign(iterResult.cloneTree(), getNext.cloneTree());

    Node declarationOrAssign;
    if (!NodeUtil.isNameDeclaration(variable)) {
      // e.g. `for(a.b of []) {}`
      declarationOrAssign =
          astFactory.createAssign(
              variable.cloneTree().setJSDocInfo(null),
              astFactory.createGetProp(iterResult.cloneTree(), "value", type(variable)));
      declarationOrAssign.setJSDocInfo(varJSDocInfo);
      declarationOrAssign = IR.exprResult(declarationOrAssign);
    } else {
      // `for(let a of []) {}` or `for(const a of []) {}`
      AstFactory.Type type = type(variable.getFirstChild());
      Token declarationType = variable.getToken(); // i.e. VAR, CONST, or LET.
      checkState(
          !declarationType.equals(Token.VAR),
          "var initializers must've gotten moved out of the loop during normalize");
      declarationOrAssign =
          astFactory.createSingleNameDeclaration(
              declarationType,
              variable.getFirstChild().getString(),
              astFactory.createGetProp(iterResult.cloneTree(), "value", type));
      if (variable.getFirstChild().getBooleanProp(Node.IS_CONSTANT_NAME)) {
        // if the original name was const, then the new name should be too
        // e.g. `for(let CID of []) {}` where `CID` was originally marked constant by coding
        // convention
        declarationOrAssign.getFirstChild().putBooleanProp(Node.IS_CONSTANT_NAME, true);
      }
      declarationOrAssign.setJSDocInfo(varJSDocInfo);
    }
    Node newBody = IR.block(declarationOrAssign, body).srcref(body);
    Node empty = astFactory.createEmpty();
    Node newFor = IR.forNode(empty, cond, incr, newBody).srcrefTreeIfMissing(node);

    // Build finally block:
    // if ($jscomp$key$extraName && !$jscomp$key$extraName.done && ($jscomp$retFn$0 =
    // $jscomp$iter$0.return)) {
    //   $jscomp$retFn$0.call($jscomp$iter$0);
    // }
    Node notDone =
        astFactory.createNot(
            astFactory.createGetProp(iterResult.cloneTree(), "done", type(StandardColors.BOOLEAN)));
    Node and1 = astFactory.createAnd(iterResult.cloneTree(), notDone);
    Node getReturn = astFactory.createGetPropWithUnknownType(iterName.cloneTree(), "return");
    Node assignRetFn = astFactory.createAssign(retFn.cloneTree(), getReturn);
    Node ifCond = astFactory.createAnd(and1, assignRetFn);

    Node callRetFn =
        astFactory.createCall(
            astFactory.createGetPropWithUnknownType(retFn.cloneTree(), "call"),
            type(StandardColors.UNKNOWN),
            iterName.cloneTree());
    Node ifBody = astFactory.createBlock(astFactory.exprResult(callRetFn));
    Node ifStmt = astFactory.createIf(ifCond, ifBody);
    Node finallyBlock = astFactory.createBlock(ifStmt);

    // Check if the for loop has a parent that is a label i.e. `loop1: for(...of ...)`
    List<Node> labelNames = new ArrayList<>();
    Node insertionPoint = node;
    while (insertionPoint.getParent() != null && insertionPoint.getParent().isLabel()) {
      insertionPoint = insertionPoint.getParent();
      labelNames.add(insertionPoint.getFirstChild().cloneNode());
    }

    Node innerLoop = newFor;
    for (Node labelName : labelNames) {
      innerLoop = astFactory.createLabel(labelName, innerLoop);
    }
    Node tryBlock = astFactory.createBlock(innerLoop);
    Node tryFinally = astFactory.createTryFinally(tryBlock, finallyBlock).srcrefTreeIfMissing(node);

    insertionPoint.replaceWith(tryFinally);

    initIter.insertBefore(tryFinally);
    initIterResult.insertAfter(initIter);
    initRetFn.insertAfter(initIterResult);
    compiler.reportChangeToEnclosingScope(tryFinally);
  }
}
