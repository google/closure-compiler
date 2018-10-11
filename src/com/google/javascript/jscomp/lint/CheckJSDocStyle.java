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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticGroup;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.ExportTestFunctions;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.AbstractPreOrderCallback;
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

  public static final DiagnosticType CLASS_DISALLOWED_JSDOC =
      DiagnosticType.disabled("JSC_CLASS_DISALLOWED_JSDOC",
          "@constructor annotations are redundant on classes.");

  public static final DiagnosticType MISSING_JSDOC =
      DiagnosticType.disabled("JSC_MISSING_JSDOC", "Function must have JSDoc.");

  public static final DiagnosticType MISSING_PARAMETER_JSDOC =
      DiagnosticType.disabled("JSC_MISSING_PARAMETER_JSDOC", "Parameter must have JSDoc.");

  public static final DiagnosticType MIXED_PARAM_JSDOC_STYLES =
      DiagnosticType.disabled("JSC_MIXED_PARAM_JSDOC_STYLES",
      "Functions may not use both @param annotations and inline JSDoc");

  public static final DiagnosticType MISSING_RETURN_JSDOC =
      DiagnosticType.disabled(
          "JSC_MISSING_RETURN_JSDOC",
          "Function with non-trivial return must have JSDoc indicating the return type.");

  public static final DiagnosticType MUST_BE_PRIVATE =
      DiagnosticType.disabled("JSC_MUST_BE_PRIVATE", "Property {0} must be marked @private");

  public static final DiagnosticType MUST_HAVE_TRAILING_UNDERSCORE =
      DiagnosticType.disabled(
          "JSC_MUST_HAVE_TRAILING_UNDERSCORE", "Private property {0} should end with ''_''");

  public static final DiagnosticType OPTIONAL_PARAM_NOT_MARKED_OPTIONAL =
      DiagnosticType.disabled("JSC_OPTIONAL_PARAM_NOT_MARKED_OPTIONAL",
          "Parameter {0} is optional so its type must end with =");

  public static final DiagnosticType WRONG_NUMBER_OF_PARAMS =
      DiagnosticType.disabled("JSC_WRONG_NUMBER_OF_PARAMS", "Wrong number of @param annotations");

  public static final DiagnosticType INCORRECT_PARAM_NAME =
      DiagnosticType.disabled("JSC_INCORRECT_PARAM_NAME",
          "Incorrect param name. Are your @param annotations in the wrong order?");

  public static final DiagnosticType EXTERNS_FILES_SHOULD_BE_ANNOTATED =
      DiagnosticType.disabled("JSC_EXTERNS_FILES_SHOULD_BE_ANNOTATED",
          "Externs files should be annotated with @externs in the @fileoverview block.");

  public static final DiagnosticType PREFER_BACKTICKS_TO_AT_SIGN_CODE =
      DiagnosticType.disabled(
          "JSC_PREFER_BACKTICKS_TO_AT_SIGN_CODE",
          "Use `some_code` instead of '{'@code some_code'}'.");

  public static final DiagnosticGroup ALL_DIAGNOSTICS =
      new DiagnosticGroup(
          CLASS_DISALLOWED_JSDOC,
          MISSING_JSDOC,
          MISSING_PARAMETER_JSDOC,
          MIXED_PARAM_JSDOC_STYLES,
          MISSING_RETURN_JSDOC,
          MUST_BE_PRIVATE,
          MUST_HAVE_TRAILING_UNDERSCORE,
          OPTIONAL_PARAM_NOT_MARKED_OPTIONAL,
          WRONG_NUMBER_OF_PARAMS,
          INCORRECT_PARAM_NAME,
          EXTERNS_FILES_SHOULD_BE_ANNOTATED,
          PREFER_BACKTICKS_TO_AT_SIGN_CODE);

  private final AbstractCompiler compiler;

  public CheckJSDocStyle(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
    NodeTraversal.traverse(compiler, externs, new ExternsCallback());
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node unused) {
    switch (n.getToken()) {
      case FUNCTION:
        visitFunction(t, n);
        break;
      case CLASS:
        visitClass(t, n);
        break;
      case ASSIGN:
        checkStyleForPrivateProperties(t, n);
        break;
      case VAR:
      case LET:
      case CONST:
      case STRING_KEY:
      case SCRIPT:
        break;
      case MEMBER_FUNCTION_DEF:
      case GETTER_DEF:
      case SETTER_DEF:
        // Don't need to call visitFunction because this JSDoc will be visited when the function is
        // visited.
        if (NodeUtil.getEnclosingClass(n) != null) {
          checkStyleForPrivateProperties(t, n);
        }
        break;
      default:
        visitNonFunction(t, n);
    }
  }

  private void checkForAtSignCodePresence(NodeTraversal t, Node n, @Nullable JSDocInfo jsDoc) {
    if (jsDoc == null) {
      return;
    }
    if (jsDoc.isAtSignCodePresent()) {
      t.report(n, PREFER_BACKTICKS_TO_AT_SIGN_CODE);
    }
  }

  private void visitNonFunction(NodeTraversal t, Node n) {
    JSDocInfo jsDoc = n.getJSDocInfo();

    checkForAtSignCodePresence(t, n, jsDoc);
  }

  private void checkStyleForPrivateProperties(NodeTraversal t, Node n) {
    JSDocInfo jsDoc = NodeUtil.getBestJSDocInfo(n);

    checkForAtSignCodePresence(t, n, jsDoc);

    String name;
    if (n.isMemberFunctionDef() || n.isGetterDef() || n.isSetterDef()) {
      name = n.getString();
    } else {
      checkState(n.isAssign());
      Node lhs = n.getFirstChild();
      if (!lhs.isGetProp()) {
        return;
      }
      name = lhs.getLastChild().getString();
    }
    if (name.equals("constructor")) {
      return;
    }

    if (jsDoc != null && name != null) {
      if (compiler.getCodingConvention().isPrivate(name)
          && !jsDoc.getVisibility().equals(Visibility.PRIVATE)) {
        t.report(n, MUST_BE_PRIVATE, name);
      } else if (compiler.getCodingConvention().hasPrivacyConvention()
          && !compiler.getCodingConvention().isPrivate(name)
          && jsDoc.getVisibility().equals(Visibility.PRIVATE)) {
        t.report(n, MUST_HAVE_TRAILING_UNDERSCORE, name);
      }
    }
  }

  private void visitFunction(NodeTraversal t, Node function) {
    JSDocInfo jsDoc = NodeUtil.getBestJSDocInfo(function);

    checkForAtSignCodePresence(t, function, jsDoc);

    if (jsDoc == null && !hasAnyInlineJsDoc(function)) {
      checkMissingJsDoc(t, function);
    } else {
      if (t.inGlobalScope()
          || hasAnyInlineJsDoc(function)
          || !jsDoc.getParameterNames().isEmpty()
          || jsDoc.hasReturnType()) {
        checkParams(t, function, jsDoc);
      }
      checkReturn(t, function, jsDoc);
    }
  }

  private void visitClass(NodeTraversal t, Node cls) {
    JSDocInfo jsDoc = NodeUtil.getBestJSDocInfo(cls);

    checkForAtSignCodePresence(t, cls, jsDoc);

    if (jsDoc == null) {
      return;
    }
    if (jsDoc.isConstructor()) {
      t.report(cls, CLASS_DISALLOWED_JSDOC);
    }
  }

  private void checkMissingJsDoc(NodeTraversal t, Node function) {
    if (isFunctionThatShouldHaveJsDoc(t, function)) {
      String name = NodeUtil.getName(function);
      // Don't warn for test functions, setUp, tearDown, etc.
      if (name == null || !ExportTestFunctions.isTestFunction(name)) {
        t.report(function, MISSING_JSDOC);
      }
    }
  }

  /**
   * Whether the given function should have JSDoc. True if it's a function declared
   * in the global scope, or a method on a class which is declared in the global scope.
   */
  private boolean isFunctionThatShouldHaveJsDoc(NodeTraversal t, Node function) {
    if (!(t.inGlobalHoistScope() || t.inModuleScope())) {
      return false;
    }
    if (NodeUtil.isFunctionDeclaration(function)) {
      return true;
    }
    if (NodeUtil.isNameDeclaration(function.getGrandparent()) || function.getParent().isAssign()) {
      return true;
    }
    if (function.getParent().isExport()) {
      return true;
    }

    if (function.getGrandparent().isClassMembers()) {
      Node memberNode = function.getParent();
      if (memberNode.isMemberFunctionDef()) {
        // A constructor with no parameters doesn't need JSDoc,
        // but all other member functions do.
        return !isConstructorWithoutParameters(function);
      } else if (memberNode.isGetterDef() || memberNode.isSetterDef()) {
        return true;
      }
    }

    if (function.getGrandparent().isObjectLit()
        && NodeUtil.isCallTo(function.getGrandparent().getParent(), "Polymer")) {
      return true;
    }

    return false;
  }

  private boolean isConstructorWithoutParameters(Node function) {
    return function.getParent().matchesQualifiedName("constructor")
        && !NodeUtil.getFunctionParameters(function).hasChildren();
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
            ? ImmutableList.of()
            : ImmutableList.copyOf(jsDoc.getParameterNames());
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
        checkNotNull(paramType, "Inline JSDoc info should always have a type");
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
      checkState(param.isDestructuringPattern() || param.isRest(), param);
      nameOptional = false;
    }

    if (name == null || !nodeToCheck.isName()) {
      // Skip the name check, but use "<unknown name>" for other errors that might be reported.
      name = "<unknown name>";
    } else if (!nodeToCheck.matchesQualifiedName(name)) {
      t.report(nodeToCheck, INCORRECT_PARAM_NAME);
      return true;
    }

    boolean jsDocOptional = paramType != null && paramType.isOptionalArg();
    if (nameOptional && !jsDocOptional) {
      t.report(nodeToCheck, OPTIONAL_PARAM_NOT_MARKED_OPTIONAL, name);
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

  private void checkReturn(NodeTraversal t, Node function, JSDocInfo jsDoc) {
    if (jsDoc != null
        && (jsDoc.hasType()
            || jsDoc.isConstructor()
            || jsDoc.hasReturnType()
            || jsDoc.isOverride())) {
      return;
    }

    Node parent = function.getParent();
    if (parent.isMemberFunctionDef() && parent.getString().equals("constructor")) {
      // ES6 class constructors should never have "@return".
      return;
    }

    if (function.getFirstChild().getJSDocInfo() != null) {
      return;
    }

    FindNonTrivialReturn finder = new FindNonTrivialReturn();
    NodeTraversal.traverse(compiler, function.getLastChild(), finder);
    if (finder.found) {
      t.report(function, MISSING_RETURN_JSDOC);
    }
  }

  private static class FindNonTrivialReturn extends AbstractPreOrderCallback {
    private boolean found;

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      if (found) {
        return false;
      }

      // Shallow traversal, since we don't need to inspect within functions or expressions.
      if (parent == null
          || NodeUtil.isControlStructure(parent)
          || NodeUtil.isStatementBlock(parent)) {
        if (n.isReturn() && n.hasChildren()) {
          found = true;
          return false;
        }
        return true;
      }
      return false;
    }
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
