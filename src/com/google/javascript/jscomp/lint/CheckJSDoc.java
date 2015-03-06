/*
 * Copyright 2015 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.lint;

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.Node;

/**
 * Checks for various JSDoc-related issues, such as function definitions without JsDoc, params
 * with no corresponding {@code @param} annotation, etc.
 */
public final class CheckJSDoc extends AbstractPostOrderCallback implements CompilerPass {
  public static final DiagnosticType MISSING_PARAM_JSDOC =
      DiagnosticType.warning("JSC_MISSING_PARAM_JSDOC", "Missing JSDoc for param {0}");

  public static final DiagnosticType MUST_BE_PRIVATE =
      DiagnosticType.warning("JSC_MUST_BE_PRIVATE", "Function {0} must be marked @private");

  public static final DiagnosticType OPTIONAL_NAME_NOT_MARKED_OPTIONAL =
      DiagnosticType.warning("JSC_OPTIONAL_NAME_NOT_MARKED_OPTIONAL",
          "Optional parameter {0} type must end with =");

  public static final DiagnosticType OPTIONAL_TYPE_NOT_USING_OPTIONAL_NAME =
      DiagnosticType.warning("JSC_OPTIONAL_TYPE_NOT_USING_OPTIONAL_NAME",
          "Optional parameter name {0} must be prefixed with opt_");

  private final AbstractCompiler compiler;

  public CheckJSDoc(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isFunction()) {
      String name = NodeUtil.getFunctionName(n);
      JSDocInfo jsDoc = NodeUtil.getBestJSDocInfo(n);
      if (jsDoc != null) {
        for (Node param : n.getFirstChild().getNext().children()) {
          if (!jsDoc.hasParameterType(param.getString())) {
            t.report(param, MISSING_PARAM_JSDOC, param.getString());
          } else {
            boolean jsDocOptional = jsDoc.getParameterType(param.getString()).isOptionalArg();
            boolean nameOptional = compiler.getCodingConvention().isOptionalParameter(param);
            if (nameOptional && !jsDocOptional) {
              t.report(param, OPTIONAL_NAME_NOT_MARKED_OPTIONAL, param.getString());
            } else if (!nameOptional && jsDocOptional) {
              t.report(param, OPTIONAL_TYPE_NOT_USING_OPTIONAL_NAME, param.getString());
            }
          }
        }

        if (name != null && compiler.getCodingConvention().isPrivate(name)
            && !jsDoc.getVisibility().equals(Visibility.PRIVATE)) {
          t.report(n, MUST_BE_PRIVATE, name);
        }
      }
    }
  }
}

