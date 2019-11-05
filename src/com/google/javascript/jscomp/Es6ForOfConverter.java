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
import com.google.javascript.rhino.jstype.UnionType;

/**
 * Converts ES6 "for of" loops to ES5.
 */
public final class Es6ForOfConverter extends NodeTraversal.AbstractPostOrderCallback
    implements HotSwapCompilerPass {
  private final AbstractCompiler compiler;
  private static final FeatureSet transpiledFeatures = FeatureSet.BARE_MINIMUM.with(Feature.FOR_OF);
  // addTypes indicates whether we should add type information when transpiling.
  private final boolean addTypes;
  private final JSTypeRegistry registry;
  private final JSType unknownType;
  private final JSType makeIteratorTypeArg;
  private final DefaultNameGenerator namer;
  private final AstFactory astFactory;

  private static final String ITER_BASE = "$jscomp$iter$";

  private static final String ITER_RESULT = "$jscomp$key$";

  public Es6ForOfConverter(AbstractCompiler compiler) {
    this.compiler = compiler;
    // Only add type information if type checking has been run.
    this.addTypes = compiler.hasTypeCheckingRun();
    this.registry = compiler.getTypeRegistry();
    this.unknownType = createType(addTypes, registry, JSTypeNative.UNKNOWN_TYPE);
    this.makeIteratorTypeArg = createMakeIteratorTypeArg();
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
      visitForOf(n, parent);
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
        typeParam =
            iterableType
                .getTemplateTypeMap()
                .getResolvedTemplateType(registry.getIterableTemplate());
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
        astFactory.createName(ITER_BASE + compiler.getUniqueNameIdSupplier().get(), iteratorType);
    iterName.makeNonIndexable();
    Node getNext = astFactory.createCall(astFactory.createGetProp(iterName.cloneTree(), "next"));
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

    // TODO(lharker): replace this with astFactory.createJscompMakeIteratorCall once b/136592294 is
    // fixed.
    Node call = Es6ToEs3Util.makeIterator(compiler, iterable);
    if (addTypes) {
      // Put types on the $jscomp.makeIterator getprop
      Node getProp = call.getFirstChild();
      getProp.setJSType(registry.createFunctionType(iteratorType, makeIteratorTypeArg));
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

  private JSType createGenericType(JSTypeNative typeName, JSType typeArg) {
    return Es6ToEs3Util.createGenericType(addTypes, registry, typeName, typeArg);
  }

  /**
   * Create the function type for $jscomp.makeIterator.
   *
   * <p>Build "{string|!Iterable<T>|!Iterator<T>|!Arguments<T>}"
   */
  private JSType createMakeIteratorTypeArg() {
    UnionType.Builder builder =
        UnionType.builder(registry)
            .addAlternate(registry.getNativeType(JSTypeNative.STRING_TYPE))
            .addAlternate(registry.getNativeType(JSTypeNative.ITERATOR_TYPE))
            .addAlternate(registry.getNativeType(JSTypeNative.ITERABLE_TYPE));

    // If the user didn't provide externs for Arguments, let TypeCheck take care of issuing a
    // warning.
    JSType argumentsType = registry.getGlobalType("Arguments");
    if (argumentsType != null) {
      builder.addAlternate(argumentsType);
    }

    return builder.build();
  }
}
