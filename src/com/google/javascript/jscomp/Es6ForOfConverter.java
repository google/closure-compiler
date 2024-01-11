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
      visitForOf(n);
    }
  }

  // TODO(lharker): break up this method
  private void visitForOf(Node node) {
    // `var v` or `let v` or v any valid lhs
    Node variable = node.removeFirstChild();
    Node iterable = node.removeFirstChild();
    Node body = node.removeFirstChild();
    JSDocInfo varJSDocInfo = variable.getJSDocInfo();
    // `$jscomp$iter$0`
    Node iterName =
        astFactory.createName(
            ITER_BASE + compiler.getUniqueNameIdSupplier().get(), type(StandardColors.ITERATOR_ID));
    iterName.makeNonIndexable();
    // `$jscomp$iter$0.next()`
    Node getNext =
        astFactory.createCallWithUnknownType(
            astFactory.createGetPropWithUnknownType(iterName.cloneTree(), "next"));
    // generate a unique iterator result name for every for-of loop getting rewritten to avoid
    // conflicts
    String iteratorResultName =
        ITER_RESULT
            + compiler
                .getUniqueIdSupplier()
                .getUniqueId(compiler.getInput(NodeUtil.getEnclosingScript(node).getInputId()))
            + "$";

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
    // `$jscomp.makeIterator(iterable)`
    Node callMakeIterator =
        astFactory
            .createJSCompMakeIteratorCall(iterable, this.namespace)
            .srcrefTreeIfMissing(iterable);
    // `var $jscomp$iter$0 = $jscomp.makeIterator(iterable)`
    Node initIter = IR.var(iterName, callMakeIterator).srcrefTreeIfMissing(iterable);
    // var $jscomp$key$extraName = $jscomp$iter$0.next();
    Node initIterResult =
        IR.var(iterResult.cloneTree(), getNext.cloneTree()).srcrefTreeIfMissing(iterable);
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
      declarationOrAssign.setJSDocInfo(varJSDocInfo);
    }
    Node newBody = IR.block(declarationOrAssign, body).srcref(body);
    Node empty = astFactory.createEmpty();
    Node newFor = IR.forNode(empty, cond, incr, newBody).srcrefTreeIfMissing(node);
    node.replaceWith(newFor);
    // Check if the for loop has a parent that is a label i.e. `loop 1: for(...of .. .)`
    Node insertionPoint = newFor;
    while (insertionPoint.getParent().isLabel()) {
      insertionPoint = insertionPoint.getParent();
    }
    initIter.insertBefore(insertionPoint);
    initIterResult.insertAfter(initIter);
    compiler.reportChangeToEnclosingScope(newFor);
  }
}
