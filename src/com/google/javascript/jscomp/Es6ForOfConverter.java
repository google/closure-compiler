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
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isForOf()) {
      visitForOf(n);
    }
  }

  // TODO(lharker): break up this method
  private void visitForOf(Node node) {
    Node variable = node.removeFirstChild();
    Node iterable = node.removeFirstChild();
    Node body = node.removeFirstChild();

    JSDocInfo varJSDocInfo = variable.getJSDocInfo();
    Node iterName =
        astFactory.createName(
            ITER_BASE + compiler.getUniqueNameIdSupplier().get(), type(StandardColors.ITERATOR_ID));
    iterName.makeNonIndexable();
    Node getNext =
        astFactory.createCallWithUnknownType(
            astFactory.createGetPropWithUnknownType(iterName.cloneTree(), "next"));
    String iteratorResultName = ITER_RESULT;
    if (NodeUtil.isNameDeclaration(variable)) {
      iteratorResultName += variable.getFirstChild().getString();
    } else if (variable.isName()) {
      iteratorResultName += variable.getString();
    } else {
      // give arbitrary lhs expressions an arbitrary name
      iteratorResultName += namer.generateNextName();
    }
    Node iterResult = astFactory.createNameWithUnknownType(iteratorResultName);
    iterResult.makeNonIndexable();

    Node call = astFactory.createJSCompMakeIteratorCall(iterable, this.namespace);
    Node init = IR.var(iterName.cloneTree().setColor(iterName.getColor()), call);
    Node initIterResult = iterResult.cloneTree();
    initIterResult.addChildToFront(getNext.cloneTree());
    init.addChildToBack(initIterResult);

    Node cond =
        astFactory.createNot(
            astFactory.createGetProp(iterResult.cloneTree(), "done", type(StandardColors.BOOLEAN)));
    Node incr = astFactory.createAssign(iterResult.cloneTree(), getNext.cloneTree());

    Node declarationOrAssign;
    if (!NodeUtil.isNameDeclaration(variable)) {
      declarationOrAssign =
          astFactory.createAssign(
              variable.cloneTree().setJSDocInfo(null),
              astFactory.createGetProp(iterResult.cloneTree(), "value", type(variable)));
      declarationOrAssign.setJSDocInfo(varJSDocInfo);
      declarationOrAssign = IR.exprResult(declarationOrAssign);
    } else {
      AstFactory.Type type = type(variable.getFirstChild());
      Token declarationType = variable.getToken(); // i.e. VAR, CONST, or LET.
      declarationOrAssign =
          new Node(
              declarationType,
              astFactory
                  .createName(variable.getFirstChild().getString(), type)
                  .srcref(variable.getFirstChild()));
      declarationOrAssign
          .getFirstChild()
          .addChildToBack(astFactory.createGetProp(iterResult.cloneTree(), "value", type));
      declarationOrAssign.setJSDocInfo(varJSDocInfo);
    }
    Node newBody = IR.block(declarationOrAssign, body).srcref(body);
    Node newFor = IR.forNode(init, cond, incr, newBody);
    newFor.srcrefTreeIfMissing(node);
    node.replaceWith(newFor);
    compiler.reportChangeToEnclosingScope(newFor);
  }
}
