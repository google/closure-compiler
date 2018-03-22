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

import static com.google.javascript.jscomp.Es6ToEs3Util.createType;
import static com.google.javascript.jscomp.Es6ToEs3Util.withType;

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.AbstractCompiler.MostRecentTypechecker;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.ObjectTypeI;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TypeI;
import com.google.javascript.rhino.TypeIRegistry;
import com.google.javascript.rhino.jstype.JSTypeNative;

/**
 * Converts ES6 "for of" loops to ES5.
 *
 * @author tbreisacher@google.com (Tyler Breisacher)
 */
public final class Es6ForOfConverter implements NodeTraversal.Callback, HotSwapCompilerPass {
  private final AbstractCompiler compiler;
  private static final FeatureSet transpiledFeatures = FeatureSet.BARE_MINIMUM.with(Feature.FOR_OF);
  // addTypes indicates whether we should add type information when transpiling.
  private final boolean addTypes;
  private final TypeIRegistry registry;
  private final TypeI unknownType;
  private final TypeI stringType;
  private final TypeI booleanType;

  private static final String ITER_BASE = "$jscomp$iter$";

  private static final String ITER_RESULT = "$jscomp$key$";

  public Es6ForOfConverter(AbstractCompiler compiler) {
    this.compiler = compiler;
    // Only add type information if NTI has been run.
    this.addTypes = MostRecentTypechecker.NTI.equals(compiler.getMostRecentTypechecker());
    this.registry = compiler.getTypeIRegistry();
    this.unknownType = createType(addTypes, registry, JSTypeNative.UNKNOWN_TYPE);
    this.stringType = createType(addTypes, registry, JSTypeNative.STRING_TYPE);
    this.booleanType = createType(addTypes, registry, JSTypeNative.BOOLEAN_TYPE);
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, externs, transpiledFeatures, this);
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
    TranspilationPasses.markFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(compiler, scriptRoot, transpiledFeatures, this);
    TranspilationPasses.markFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case FOR_OF:
        visitForOf(t, n, parent);
        break;
      default:
        break;
    }
  }

  private void visitForOf(NodeTraversal t, Node node, Node parent) {
    Node variable = node.removeFirstChild();
    Node iterable = node.removeFirstChild();
    Node body = node.removeFirstChild();

    TypeI typeParam = unknownType;
    if (addTypes) {
      // TODO(sdh): This is going to be null if the iterable is nullable or unknown. We might want
      // to consider some way of unifying rather than simply looking at the nominal type.
      ObjectTypeI iterableType = iterable.getTypeI().autobox().toMaybeObjectType();
      if (iterableType != null) {
        TypeIRegistry registry = compiler.getTypeIRegistry();
        TypeI iterableBaseType = registry.getNativeType(JSTypeNative.ITERABLE_TYPE);
        typeParam = iterableType.getInstantiatedTypeArgument(iterableBaseType);
      }
    }
    TypeI iteratorType = createGenericType(JSTypeNative.ITERATOR_TYPE, typeParam);
    TypeI iIterableResultType = createGenericType(JSTypeNative.I_ITERABLE_RESULT_TYPE, typeParam);
    TypeI iteratorNextType =
        addTypes ? iteratorType.toMaybeObjectType().getPropertyType("next") : null;

    JSDocInfo varJSDocInfo = variable.getJSDocInfo();
    Node iterName =
        withType(IR.name(ITER_BASE + compiler.getUniqueNameIdSupplier().get()), iteratorType);
    iterName.makeNonIndexable();
    Node getNext =
        withType(
            IR.call(
                withType(
                    IR.getprop(iterName.cloneTree(), withStringType(IR.string("next"))),
                    iteratorNextType)),
            iIterableResultType);
    String variableName;
    Token declType;
    if (variable.isName()) {
      declType = Token.NAME;
      variableName = variable.getQualifiedName();
    } else {
      Preconditions.checkState(NodeUtil.isNameDeclaration(variable),
          "Expected var, let, or const. Got %s", variable);
      declType = variable.getToken();
      variableName = variable.getFirstChild().getQualifiedName();
    }
    Node iterResult = withType(IR.name(ITER_RESULT + variableName), iIterableResultType);
    iterResult.makeNonIndexable();

    Node call = Es6ToEs3Util.makeIterator(compiler, iterable);
    if (addTypes) {
      TypeI jscompType = t.getScope().getVar("$jscomp").getNode().getTypeI();
      TypeI makeIteratorType = jscompType.toMaybeObjectType().getPropertyType("makeIterator");
      call.getFirstChild().setTypeI(makeIteratorType);
      call.getFirstFirstChild().setTypeI(jscompType);
    }
    Node init = IR.var(iterName.cloneTree(), withType(call, iteratorType));
    Node initIterResult = iterResult.cloneTree();
    initIterResult.addChildToFront(getNext.cloneTree());
    init.addChildToBack(initIterResult);

    Node cond =
        withBooleanType(
            IR.not(
                withBooleanType(
                    IR.getprop(iterResult.cloneTree(), withStringType(IR.string("done"))))));
    Node incr =
        withType(IR.assign(iterResult.cloneTree(), getNext.cloneTree()), iIterableResultType);

    Node declarationOrAssign;
    if (declType == Token.NAME) {
      declarationOrAssign =
          withType(
              IR.assign(
                  withType(IR.name(variableName).useSourceInfoFrom(variable), typeParam),
                  withType(
                      IR.getprop(iterResult.cloneTree(), withStringType(IR.string("value"))),
                      typeParam)),
              typeParam);
      declarationOrAssign.setJSDocInfo(varJSDocInfo);
      declarationOrAssign = IR.exprResult(declarationOrAssign);
    } else {
      declarationOrAssign = new Node(
          declType,
          withType(IR.name(variableName).useSourceInfoFrom(variable.getFirstChild()), typeParam));
      declarationOrAssign.getFirstChild().addChildToBack(
              withType(
                  IR.getprop(iterResult.cloneTree(), withStringType(IR.string("value"))),
                  typeParam));
      declarationOrAssign.setJSDocInfo(varJSDocInfo);
    }
    Node newBody = IR.block(declarationOrAssign, body).useSourceInfoFrom(body);
    Node newFor = IR.forNode(init, cond, incr, newBody);
    newFor.useSourceInfoIfMissingFromForTree(node);
    parent.replaceChild(node, newFor);
    compiler.reportChangeToEnclosingScope(newFor);
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
}
