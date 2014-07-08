/*
 * Copyright 2008 The Closure Compiler Authors.
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

import java.util.List;

import com.google.common.collect.Lists;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;

/**
 * A compiler pass that checks for calls to nosideeffects extern methods 
 * have results that are used. If not, we should protect the call
 * and warn the user about potentially useless code. Because some extern
 * methods have hidden side effects, we cannot simply remove them as dead code.
 * See issue 237.
 *
 * Because the JSDoc information needed is type information,
 * it's important that TypedScopeCreator, TypeInference, and InferJSDocInfo
 * all run before this pass, otherwise this would be part of the
 * CheckSideEffects pass.
 *
 * @author chadkillingsworth@missouristate.edu (Chad Killingsworth)
 */
class CheckNoSideEffectExternCalls extends AbstractPostOrderCallback
    implements HotSwapCompilerPass {

  static final DiagnosticType USELESS_CODE_ERROR = DiagnosticType.warning(
      "JSC_USELESS_CODE",
      "Suspicious code. " +
      "The result of the extern function call \"{0}\" is not being used.");

  static final String PROTECTOR_FN = "JSCOMPILER_PRESERVE";

  private final AbstractCompiler compiler;
  private final CheckLevel level;
  private final boolean protectHiddenSideEffects;
  private final List<Node> problemNodes = Lists.newArrayList();

  CheckNoSideEffectExternCalls(AbstractCompiler compiler, CheckLevel level,
       boolean protectHiddenSideEffects) {
    this.compiler = compiler;
    this.level = level;
    this.protectHiddenSideEffects= protectHiddenSideEffects; 
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);

    if (protectHiddenSideEffects) {
      protectSideEffects();
    }
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isCall() && !NodeUtil.isExpressionResultUsed(n) &&
        (n.getFirstChild().isName() || n.getFirstChild().isString())) {
      JSType targetType = n.getFirstChild().getJSType();
      if (targetType != null && targetType.isFunctionType()) {
        targetType = targetType.restrictByNotNullOrUndefined();
        FunctionType functionType = targetType.toMaybeFunctionType();
        JSDocInfo functionJSDocInfo = functionType.getJSDocInfo();
        if (functionJSDocInfo != null &&
            functionJSDocInfo.isNoSideEffects() &&
            functionJSDocInfo.getAssociatedNode() != null &&
            functionJSDocInfo.getAssociatedNode().isFromExterns()) {
          t.getCompiler().report(t.makeError(n, level, USELESS_CODE_ERROR,
              n.getFirstChild().getString()));
          problemNodes.add(n);
        }
      }
    }
  }

  /**
   * Protect side-effect free nodes by making them parameters
   * to a extern function call.  This call will be removed
   * after all the optimizations passes have run.
   */
  private void protectSideEffects() {
    if (!problemNodes.isEmpty()) {
      addExtern();
      for (Node n : problemNodes) {
        Node name = IR.name(PROTECTOR_FN).srcref(n);
        name.putBooleanProp(Node.IS_CONSTANT_NAME, true);
        Node replacement = IR.call(name).srcref(n);
        replacement.putBooleanProp(Node.FREE_CALL, true);
        n.getParent().replaceChild(n, replacement);
        replacement.addChildToBack(n);
      }
      compiler.reportCodeChange();
    }
  }

  // TODO(chadkillingsworth) Check to see if the protector function has
  // already been added by the CheckSideEffects pass
  private void addExtern() {
    Node name = IR.name(PROTECTOR_FN);
    name.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    Node var = IR.var(name);
    /* Add "@noalias" so we can strip the method when AliasExternals is
     * enabled.
     */
    JSDocInfoBuilder builder = new JSDocInfoBuilder(false);
    builder.recordNoAlias();
    var.setJSDocInfo(builder.build(var));
    CompilerInput input = compiler.getSynthesizedExternsInput();
    input.getAstRoot(compiler).addChildrenToBack(var);
    compiler.reportCodeChange();
  }
}
