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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.ExportTestFunctions;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;

import java.util.List;

import javax.annotation.Nullable;

/**
 * Checks for various JSDoc-related style issues, such as function definitions without JsDoc, params
 * with no corresponding {@code @param} annotation, coding conventions not being respected, etc.
 */
public final class CheckJSDocStyle extends AbstractPostOrderCallback implements CompilerPass {
  public static final DiagnosticType MISSING_JSDOC =
      DiagnosticType.warning("JSC_MISSING_JSDOC", "Function must have JSDoc.");

  public static final DiagnosticType MISSING_PARAMETER_JSDOC =
      DiagnosticType.warning("JSC_MISSING_PARAMETER_JSDOC", "Parameter must have JSDoc.");

  public static final DiagnosticType MIXED_PARAM_JSDOC_STYLES =
      DiagnosticType.warning("JSC_MIXED_PARAM_JSDOC_STYLES",
      "Functions may not use both @param annotations and inline JSDoc");

  public static final DiagnosticType MUST_BE_PRIVATE =
      DiagnosticType.warning("JSC_MUST_BE_PRIVATE", "Function {0} must be marked @private");

  public static final DiagnosticType OPTIONAL_PARAM_NOT_MARKED_OPTIONAL =
      DiagnosticType.warning("JSC_OPTIONAL_PARAM_NOT_MARKED_OPTIONAL",
          "Parameter {0} is optional so its type must end with =");

  public static final DiagnosticType OPTIONAL_TYPE_NOT_USING_OPTIONAL_NAME =
      DiagnosticType.warning("JSC_OPTIONAL_TYPE_NOT_USING_OPTIONAL_NAME",
          "Optional parameter name {0} must be prefixed with opt_");

  public static final DiagnosticType WRONG_NUMBER_OF_PARAMS =
      DiagnosticType.warning("JSC_WRONG_NUMBER_OF_PARAMS",
          "Wrong number of @param annotations");

  public static final DiagnosticType INCORRECT_PARAM_NAME =
      DiagnosticType.warning("JSC_INCORRECT_PARAM_NAME",
          "Incorrect param name. Are your @param annotations in the wrong order?");

  public static final DiagnosticType EXTERNS_FILES_SHOULD_BE_ANNOTATED =
      DiagnosticType.warning("JSC_EXTERNS_FILES_SHOULD_BE_ANNOTATED",
          "Externs files should be annotated with @externs in the @fileoverview block.");

  private final AbstractCompiler compiler;

  public CheckJSDocStyle(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, this);
    NodeTraversal.traverseEs6(compiler, externs, new ExternsCallback());
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isFunction()) {
      visitFunction(t, n);
    }
  }

  private void visitFunction(NodeTraversal t, Node function) {
    JSDocInfo jsDoc = NodeUtil.getBestJSDocInfo(function);

    if (jsDoc == null && !hasAnyInlineJsDoc(function)) {
      checkMissingJsDoc(t, function);
    } else {
      checkParams(t, function, jsDoc);
    }

    String name = NodeUtil.getName(function);
    if (jsDoc != null
        && name != null
        && compiler.getCodingConvention().isPrivate(name)
        && !jsDoc.getVisibility().equals(Visibility.PRIVATE)) {
      t.report(function, MUST_BE_PRIVATE, name);
    }
  }

  private void checkMissingJsDoc(NodeTraversal t, Node function) {
    if (t.inGlobalScope()
        && (NodeUtil.isFunctionDeclaration(function)
            || NodeUtil.isNameDeclaration(function.getParent().getParent())
            || function.getParent().isAssign())) {
      String name = NodeUtil.getName(function);
      // Don't warn for test functions, setUp, tearDown, etc.
      if (name == null || !ExportTestFunctions.isTestFunction(name)) {
        t.report(function, MISSING_JSDOC);
      }
    }
  }

  private void checkParams(NodeTraversal t, Node function, JSDocInfo jsDoc) {
    if (jsDoc != null && jsDoc.isOverride()) {
      return;
    }

    if (jsDoc != null && jsDoc.getType() != null) {
      // Sometimes functions are declared with @type {function(Foo, Bar)} instead of
      //   @param {Foo} foo
      //   @param {Bar} bar
      // which is fine.
      return;
    }

    List<String> paramsFromJsDoc =
        jsDoc == null
            ? ImmutableList.<String>of()
            : ImmutableList.<String>copyOf(jsDoc.getParameterNames());
    if (paramsFromJsDoc.isEmpty()) {
      checkInlineParams(t, function);
    } else {
      Node paramList = NodeUtil.getFunctionParameters(function);
      if (paramsFromJsDoc.size() != paramList.getChildCount()) {
        t.report(paramList, WRONG_NUMBER_OF_PARAMS);
        return;
      }

      Node param = paramList.getFirstChild();
      for (int i = 0; i < paramsFromJsDoc.size(); i++) {
        if (param.getJSDocInfo() != null) {
          t.report(param, MIXED_PARAM_JSDOC_STYLES);
        }
        String name = paramsFromJsDoc.get(i);
        JSTypeExpression paramType = jsDoc.getParameterType(name);
        if (checkParam(t, param, name, paramType)) {
          return;
        }
        param = param.getNext();
      }
    }
  }

  /**
   * Checks that the inline type annotations are correct.
   */
  private void checkInlineParams(NodeTraversal t, Node function) {
    Node paramList = NodeUtil.getFunctionParameters(function);

    for (Node param : paramList.children()) {
      JSDocInfo jsDoc = param.getJSDocInfo();
      if (jsDoc == null) {
        t.report(param, MISSING_PARAMETER_JSDOC);
        return;
      } else {
        JSTypeExpression paramType = jsDoc.getType();
        Preconditions.checkNotNull(paramType, "Inline JSDoc info should always have a type");
        checkParam(t, param, null, paramType);
      }
    }
  }

  /**
   * Checks that the given parameter node has the given name, and that the given type is
   * compatible.
   * @param param If this is a non-NAME node, such as a destructuring pattern, skip the name check.
   * @param name If null, skip the name check
   * @return Whether a warning was reported
   */
  private boolean checkParam(
      NodeTraversal t, Node param, @Nullable String name, JSTypeExpression paramType) {
    boolean nameOptional;
    Node nodeToCheck = param;
    if (param.isDefaultValue()) {
      nodeToCheck = param.getFirstChild();
      nameOptional = true;
    } else if (param.isName()) {
      nameOptional = param.getString().startsWith("opt_");
    } else {
      Preconditions.checkState(param.isDestructuringPattern() || param.isRest(), param);
      nameOptional = false;
    }

    if (name == null || !nodeToCheck.isName()) {
      // Skip the name check.
    } else if (!nodeToCheck.matchesQualifiedName(name)) {
      t.report(nodeToCheck, INCORRECT_PARAM_NAME);
      return true;
    }

    boolean jsDocOptional = paramType != null && paramType.isOptionalArg();
    if (nameOptional && !jsDocOptional) {
      t.report(nodeToCheck, OPTIONAL_PARAM_NOT_MARKED_OPTIONAL, nodeToCheck.getString());
      return true;
    } else if (!nameOptional && jsDocOptional) {
      t.report(nodeToCheck, OPTIONAL_TYPE_NOT_USING_OPTIONAL_NAME, nodeToCheck.getString());
      return true;
    }
    return false;
  }

  private boolean hasAnyInlineJsDoc(Node function) {
    if (function.getFirstChild().getJSDocInfo() != null) {
      // Inline return annotation.
      return true;
    }
    for (Node param : NodeUtil.getFunctionParameters(function).children()) {
      if (param.getJSDocInfo() != null) {
        return true;
      }
    }
    return false;
  }

  private static class ExternsCallback implements NodeTraversal.Callback {
    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return parent == null || n.isScript();
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isScript()) {
        JSDocInfo info = n.getJSDocInfo();
        if (info == null || !info.isExterns()) {
          t.report(n, EXTERNS_FILES_SHOULD_BE_ANNOTATED);
        }
      }
    }
  }
}
