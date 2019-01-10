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

import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.UnionTypeBuilder;

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
  private final JSTypeRegistry registry;
  private final JSType unknownType;
  private final JSType stringType;
  private final JSType booleanType;
  private final DefaultNameGenerator namer;

  private static final String ITER_BASE = "$jscomp$iter$";

  private static final String ITER_RESULT = "$jscomp$key$";

  public Es6ForOfConverter(AbstractCompiler compiler) {
    this.compiler = compiler;
    // Only add type information if type checking has been run.
    this.addTypes = compiler.hasTypeCheckingRun();
    this.registry = compiler.getTypeRegistry();
    this.unknownType = createType(addTypes, registry, JSTypeNative.UNKNOWN_TYPE);
    this.stringType = createType(addTypes, registry, JSTypeNative.STRING_TYPE);
    this.booleanType = createType(addTypes, registry, JSTypeNative.BOOLEAN_TYPE);
    namer = new DefaultNameGenerator();
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
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case FOR_OF:
        visitForOf(n, parent);
        break;
      default:
        break;
    }
  }

  // TODO(lharker): break up this method
  private void visitForOf(Node node, Node parent) {
    Node variable = node.removeFirstChild();
    Node iterable = node.removeFirstChild();
    Node body = node.removeFirstChild();

    JSType typeParam = unknownType;
    if (addTypes) {
      // TODO(sdh): This is going to be null if the iterable is nullable or unknown. We might want
      // to consider some way of unifying rather than simply looking at the nominal type.
      ObjectType iterableType = iterable.getJSType().autobox().toMaybeObjectType();
      if (iterableType != null) {
        // This will be the unknown type if iterableType is not actually a subtype of Iterable
        typeParam =
            iterableType.getInstantiatedTypeArgument(
                registry.getNativeType(JSTypeNative.ITERABLE_TYPE));
      }
    }
    JSType iteratorType = createGenericType(JSTypeNative.ITERATOR_TYPE, typeParam);
    FunctionType iteratorNextType =
        addTypes
            ? iteratorType.toMaybeObjectType().getPropertyType("next").toMaybeFunctionType()
            : null;
    JSType iIterableResultType = addTypes ? iteratorNextType.getReturnType() : null;

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
    String iteratorResultName = ITER_RESULT;
    if (NodeUtil.isNameDeclaration(variable)) {
      iteratorResultName += variable.getFirstChild().getString();
    } else if (variable.isName()) {
      iteratorResultName += variable.getString();
    } else {
      // give arbitrary lhs expressions an arbitrary name
      iteratorResultName += namer.generateNextName();
    }
    Node iterResult = withType(IR.name(iteratorResultName), iIterableResultType);
    iterResult.makeNonIndexable();

    Node call = Es6ToEs3Util.makeIterator(compiler, iterable);
    if (addTypes) {
      // Create the function type for $jscomp.makeIterator.
      // Build "@param {string|!Iterable<T>|!Iterator<T>|!Arguments<T>}"
      UnionTypeBuilder paramBuilder =
          UnionTypeBuilder.create(registry)
              .addAlternate(registry.getNativeType(JSTypeNative.STRING_TYPE))
              .addAlternate(registry.getNativeType(JSTypeNative.ITERATOR_TYPE))
              .addAlternate(registry.getNativeType(JSTypeNative.ITERABLE_TYPE));
      JSType argumentsType = registry.getGlobalType("Arguments");
      // If the user didn't provide externs for Arguments, let TypeCheck take care of issuing a
      // warning.
      if (argumentsType != null) {
        paramBuilder.addAlternate(argumentsType);
      }
      FunctionType makeIteratorType =
          registry.createFunctionType(iteratorType, paramBuilder.build());

      // Put types on the $jscomp.makeIterator getprop
      Node getProp = call.getFirstChild();
      getProp.setJSType(makeIteratorType);
      // typing $jscomp as unknown since the $jscomp polyfill may not be injected before
      // typechecking. (See https://github.com/google/closure-compiler/issues/2908)
      getProp.getFirstChild().setJSType(registry.getNativeType(JSTypeNative.UNKNOWN_TYPE));
      getProp.getSecondChild().setJSType(registry.getNativeType(JSTypeNative.STRING_TYPE));

      call.setJSType(iteratorType);
    }
    Node init = IR.var(withType(iterName.cloneTree(), iterName.getJSType()), call);
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
    if (!NodeUtil.isNameDeclaration(variable)) {
      declarationOrAssign =
          withType(
              IR.assign(
                  withType(variable.cloneTree().setJSDocInfo(null), typeParam),
                  withType(
                      IR.getprop(iterResult.cloneTree(), withStringType(IR.string("value"))),
                      typeParam)),
              typeParam);
      declarationOrAssign.setJSDocInfo(varJSDocInfo);
      declarationOrAssign = IR.exprResult(declarationOrAssign);
    } else {
      Token declarationType = variable.getToken(); // i.e. VAR, CONST, or LET.
      declarationOrAssign =
          new Node(
                  declarationType,
                  IR.name(variable.getFirstChild().getString())
                      .useSourceInfoFrom(variable.getFirstChild()))
              .setJSType(typeParam);
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

  private JSType createGenericType(JSTypeNative typeName, JSType typeArg) {
    return Es6ToEs3Util.createGenericType(addTypes, registry, typeName, typeArg);
  }

  private Node withStringType(Node n) {
    return withType(n, stringType);
  }

  private Node withBooleanType(Node n) {
    return withType(n, booleanType);
  }
}
