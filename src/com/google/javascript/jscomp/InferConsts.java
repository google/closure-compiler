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

import com.google.javascript.jscomp.ReferenceCollectingCallback.ReferenceCollection;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

/**
 * Attaches the CONST_VAR annotation to any variable that's
 * 1) Provably well-defined and assigned once in its lifetime.
 * 2) Annotated 'const'
 * 3) Declared with the 'const' keyword.
 * 4) Is constant by naming convention.
 *
 * These 3 are considered semantically equivalent. Notice that a variable
 * in a loop is never considered const.
 *
 * Note that criteria (1) is only used for normal code, not externs.
 *
 * @author nicholas.j.santos@gmail.com (Nick Santos)
 */
class InferConsts implements CompilerPass {
  private final AbstractCompiler compiler;

  InferConsts(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node js) {
    ReferenceCollectingCallback collector = new ReferenceCollectingCallback(
        compiler, ReferenceCollectingCallback.DO_NOTHING_BEHAVIOR);
    NodeTraversal.traverse(compiler, js, collector);

    for (Var v : collector.getAllSymbols()) {
      considerVar(v, collector.getReferences(v));
    }

    Scope globalExternsScope =
        new SyntacticScopeCreator(compiler).createScope(externs, null);
    for (Var v : globalExternsScope.getAllSymbols()) {
      considerVar(v, null);
    }
  }

  private void considerVar(Var v, ReferenceCollection refCollection) {
    Node nameNode = v.getNameNode();
    JSDocInfo docInfo = v.getJSDocInfo();
    if (docInfo != null && docInfo.isConstant()) {
      nameNode.putBooleanProp(Node.IS_CONSTANT_VAR, true);
    } else if (nameNode != null && nameNode.getParent().isConst()) {
      nameNode.putBooleanProp(Node.IS_CONSTANT_VAR, true);
    } else if (nameNode != null &&
        compiler.getCodingConvention().isConstant(nameNode.getString())) {
      nameNode.putBooleanProp(Node.IS_CONSTANT_VAR, true);
    } else if (nameNode != null &&
               refCollection != null &&
               refCollection.isWellDefined() &&
               refCollection.isAssignedOnceInLifetime() &&
               refCollection.firstReferenceIsAssigningDeclaration()) {
      nameNode.putBooleanProp(Node.IS_CONSTANT_VAR, true);
    }
  }
}
