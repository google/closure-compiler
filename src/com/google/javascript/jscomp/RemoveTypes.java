/*
 * Copyright 2020 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import javax.annotation.Nullable;

/**
 * Pass to remove from the AST types and type-based information and to delete all JSDoc fields not
 * needed for optimizations.
 *
 * <p>Eventually, we anticipate this pass to run at the beginning of optimizations.
 */
final class RemoveTypes implements CompilerPass {
  private final AbstractCompiler compiler;

  RemoveTypes(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  private static class RemoveTypesFromAst extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      n.setJSType(null);
      n.setJSTypeBeforeCast(null);

      n.setTypedefTypeProp(null);
      n.setDeclaredTypeExpression(null);
      JSDocInfo jsdoc = n.getJSDocInfo();
      if (jsdoc != null) {
        n.setJSDocInfo(convertJSDocInfo(jsdoc));
      }
    }

    /**
     * Returns a clone of the input JSDocInfo, modulo fields not needed for optimizations
     *
     * <p>This mimics the eventual serialization / deserialization of a subset of JSDoc fields and
     * ensures optimizations can't accidentally depend on fields that we don't plan to serialize.
     *
     * @return a new JSDocInfo object or null if no serializable fields are found
     */
    @Nullable
    private static JSDocInfo convertJSDocInfo(JSDocInfo jsdoc) {
      JSDocInfoBuilder builder = new JSDocInfoBuilder(/* parseDocumentation= */ false);
      if (jsdoc.getLicense() != null) {
        builder.addLicense(jsdoc.getLicense());
      }

      if (jsdoc.isNoInline()) {
        builder.recordNoInline();
      }
      if (jsdoc.isDefine()) {
        builder.recordDefineType(createUnknown());
      }

      // Used by PureFunctionIdentifier
      if (jsdoc.isNoSideEffects()) {
        builder.recordNoSideEffects();
      }
      if (jsdoc.hasModifies()) {
        builder.recordModifies(jsdoc.getModifies());
      }
      if (!jsdoc.getThrownTypes().isEmpty()) {
        builder.recordThrowType(createUnknown());
      }
      return builder.build();
    }

    // Optimizations shouldn't care about the contents of JSTypeExpressions but some JSDoc APIs
    // expect their presense, so just create a dummy '?' type.
    private static JSTypeExpression createUnknown() {
      return new JSTypeExpression(new Node(Token.QMARK), "<synthetic serialized type>");
    }
  }

  @Override
  public void process(Node externs, Node root) {
    Node externsAndJsRoot = root.getParent();
    NodeTraversal.traverse(compiler, externsAndJsRoot, new RemoveTypesFromAst());
    compiler.clearJSTypeRegistry();
  }
}
