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

import static com.google.javascript.jscomp.Es6ToEs3Util.withType;

import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;

/**
 * Converts ES6 "for of" loops to ES5.
 *
 * @author tbreisacher@google.com (Tyler Breisacher)
 */
public final class Es6ForOfConverter extends NodeTraversal.AbstractPostOrderCallback
    implements HotSwapCompilerPass {
  private final AbstractCompiler compiler;
  private static final FeatureSet transpiledFeatures = FeatureSet.BARE_MINIMUM.with(Feature.FOR_OF);
  private final DefaultNameGenerator namer;
  private final AstFactory astFactory;

  private static final String ITER_BASE = "$jscomp$iter$";

  private static final String ITER_RESULT = "$jscomp$key$";

  public Es6ForOfConverter(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.namer = new DefaultNameGenerator();
    this.astFactory = compiler.createAstFactory();
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, externs, transpiledFeatures, this);
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(compiler, scriptRoot, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isForOf()) {
      visitForOf(t, n, parent);
    }
  }

  // TODO(lharker): break up this method
  private void visitForOf(NodeTraversal t, Node node, Node parent) {
    Node variable = node.removeFirstChild();
    Node iterable = node.removeFirstChild();
    Node body = node.removeFirstChild();

    // Create `$jscomp.makeIterator(iterable);`
    Node call = astFactory.createJSCompMakeIteratorCall(iterable, t.getScope());
    JSType iteratorType = call.getJSType();

    // If, e.g., we have an `Iterable<number>`, then this type will be `number`.
    JSType typeParam =
        NodeUtil.isNameDeclaration(variable)
            ? variable.getFirstChild().getJSType()
            : variable.getJSType();

    JSDocInfo varJSDocInfo = variable.getJSDocInfo();
    Node iterName =
        astFactory.createName(ITER_BASE + compiler.getUniqueNameIdSupplier().get(), iteratorType);
    iterName.makeNonIndexable();
    Node getNext = astFactory.createCall(astFactory.createGetProp(iterName.cloneTree(), "next"));
    JSType iIterableResultType = getNext.getJSType();

    String iteratorResultName = ITER_RESULT;
    if (NodeUtil.isNameDeclaration(variable)) {
      iteratorResultName += variable.getFirstChild().getString();
    } else if (variable.isName()) {
      iteratorResultName += variable.getString();
    } else {
      // give arbitrary lhs expressions an arbitrary name
      iteratorResultName += namer.generateNextName();
    }
    Node iterResult = astFactory.createName(iteratorResultName, iIterableResultType);
    iterResult.makeNonIndexable();

    Node init = IR.var(withType(iterName.cloneTree(), iterName.getJSType()), call);
    Node initIterResult = iterResult.cloneTree();
    initIterResult.addChildToFront(getNext.cloneTree());
    init.addChildToBack(initIterResult);

    Node cond = astFactory.createNot(astFactory.createGetProp(iterResult.cloneTree(), "done"));
    Node incr = astFactory.createAssign(iterResult.cloneTree(), getNext.cloneTree());

    Node declarationOrAssign;
    if (!NodeUtil.isNameDeclaration(variable)) {
      declarationOrAssign =
          astFactory.createAssign(
              withType(variable.cloneTree().setJSDocInfo(null), typeParam),
              astFactory.createGetProp(iterResult.cloneTree(), "value"));
      declarationOrAssign.setJSDocInfo(varJSDocInfo);
      declarationOrAssign = IR.exprResult(declarationOrAssign);
    } else {
      Token declarationType = variable.getToken(); // i.e. VAR, CONST, or LET.
      declarationOrAssign =
          new Node(
              declarationType,
              astFactory
                  .createName(variable.getFirstChild().getString(), typeParam)
                  .useSourceInfoFrom(variable.getFirstChild()));
      declarationOrAssign
          .getFirstChild()
          .addChildToBack(astFactory.createGetProp(iterResult.cloneTree(), "value"));
      declarationOrAssign.setJSDocInfo(varJSDocInfo);
    }
    Node newBody = IR.block(declarationOrAssign, body).useSourceInfoFrom(body);
    Node newFor = IR.forNode(init, cond, incr, newBody);
    newFor.useSourceInfoIfMissingFromForTree(node);
    parent.replaceChild(node, newFor);
    compiler.reportChangeToEnclosingScope(newFor);
  }
}
