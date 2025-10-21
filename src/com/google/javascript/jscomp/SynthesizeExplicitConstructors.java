/*
 * Copyright 2021 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.AstFactory.type;

import com.google.errorprone.annotations.CheckReturnValue;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

/** Adds explicit constructors to classes that lack them. */
@CheckReturnValue
final class SynthesizeExplicitConstructors {
  private final AbstractCompiler compiler;
  private final AstFactory astFactory;

  SynthesizeExplicitConstructors(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
  }

  /** Add a synthetic constructor to the given class if no constructor is present */
  void synthesizeClassConstructorIfMissing(NodeTraversal t, Node classNode) {
    checkArgument(classNode.isClass());
    if (NodeUtil.getEs6ClassConstructorMemberFunctionDef(classNode) == null) {
      addSyntheticConstructor(t, classNode);
    }
  }

  private void addSyntheticConstructor(NodeTraversal t, Node classNode) {
    Node superClass = classNode.getSecondChild();
    Node classMembers = classNode.getLastChild();
    Node memberDef;
    if (superClass.isEmpty()) {
      Node function = astFactory.createEmptyFunction(type(classNode));
      compiler.reportChangeToChangeScope(function);
      memberDef = astFactory.createMemberFunctionDef("constructor", function);
    } else {
      checkState(
          superClass.isQualifiedName(),
          "Expected Es6NormalizeClasses to make all extends clauses into qualified names, found %s",
          superClass);
      Node body = IR.block();

      // If a class is defined in an externs file or as an interface, it's only a stub, not an
      // implementation that should be instantiated.
      // A call to super() shouldn't actually exist for these cases and is problematic to
      // transpile, so don't generate it.
      if (!classNode.isFromExterns() && !isInterface(classNode)) {
        // Generate required call to super()
        // `super(...arguments);`
        // Note that transpilation of spread args must occur after this pass for this to work.
        Node exprResult =
            IR.exprResult(
                astFactory.createConstructorCall(
                    type(classNode), // returned type is the subclass
                    astFactory.createSuper(type(superClass)),
                    IR.iterSpread(astFactory.createArgumentsReference())));
        body.addChildToFront(exprResult);
        NodeUtil.addFeatureToScript(t.getCurrentScript(), Feature.SUPER, compiler);
        NodeUtil.addFeatureToScript(t.getCurrentScript(), Feature.SPREAD_EXPRESSIONS, compiler);
      }
      Node constructor = astFactory.createFunction("", IR.paramList(), body, type(classNode));
      memberDef = astFactory.createMemberFunctionDef("constructor", constructor);
    }
    memberDef.srcrefTreeIfMissing(classNode);
    memberDef.makeNonIndexableRecursive();
    classMembers.addChildToFront(memberDef);
    NodeUtil.addFeatureToScript(t.getCurrentScript(), Feature.MEMBER_DECLARATIONS, compiler);
    // report newly created constructor
    compiler.reportChangeToChangeScope(memberDef.getOnlyChild());
    // report change to scope containing the class
    compiler.reportChangeToEnclosingScope(memberDef);
  }

  private boolean isInterface(Node classNode) {
    JSDocInfo classJsDocInfo = NodeUtil.getBestJSDocInfo(classNode);
    return classJsDocInfo != null && classJsDocInfo.isInterface();
  }
}
