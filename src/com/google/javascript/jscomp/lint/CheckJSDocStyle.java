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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
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
import com.google.javascript.rhino.Token;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Checks for various JSDoc-related style issues, such as function definitions without JsDoc, params
 * with no corresponding {@code @param} annotation, coding conventions not being respected, etc.
 */
public final class CheckJSDocStyle extends AbstractPostOrderCallback implements CompilerPass {
  public static final DiagnosticType INVALID_SUPPRESS =
      DiagnosticType.disabled(
          "JSC_INVALID_SUPPRESS",
          "@suppress annotation not allowed here. See"
              + " https://github.com/google/closure-compiler/wiki/@suppress-annotations");

  public static final DiagnosticType CONSTRUCTOR_DISALLOWED_JSDOC =
      DiagnosticType.disabled("JSC_CONSTRUCTOR_DISALLOWED_JSDOC",
          "Visibility annotations on constructors are not supported.\n"
          + "Please mark the visibility on the class instead.");

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
          "Function with non-trivial return must have @return JSDoc or inline return JSDoc.");

  public static final DiagnosticType MUST_BE_PRIVATE =
      DiagnosticType.disabled("JSC_MUST_BE_PRIVATE", "Property {0} must be marked @private");

  public static final DiagnosticType MUST_HAVE_TRAILING_UNDERSCORE =
      DiagnosticType.disabled(
          "JSC_MUST_HAVE_TRAILING_UNDERSCORE", "Private property {0} should end with ''_''");

  public static final DiagnosticType OPTIONAL_PARAM_NOT_MARKED_OPTIONAL =
      DiagnosticType.disabled("JSC_OPTIONAL_PARAM_NOT_MARKED_OPTIONAL",
          "Parameter {0} is optional so its type must end with =");

  public static final DiagnosticType OPTIONAL_TYPE_NOT_USING_OPTIONAL_NAME =
      DiagnosticType.disabled("JSC_OPTIONAL_TYPE_NOT_USING_OPTIONAL_NAME",
          "Optional parameter name {0} must be prefixed with opt_");

  public static final DiagnosticType WRONG_NUMBER_OF_PARAMS =
      DiagnosticType.disabled("JSC_WRONG_NUMBER_OF_PARAMS", "Wrong number of @param annotations");

  public static final DiagnosticType INCORRECT_PARAM_NAME =
      DiagnosticType.disabled("JSC_INCORRECT_PARAM_NAME",
          "Incorrect param name. Are your @param annotations in the wrong order?");

  public static final DiagnosticType EXTERNS_FILES_SHOULD_BE_ANNOTATED =
      DiagnosticType.disabled("JSC_EXTERNS_FILES_SHOULD_BE_ANNOTATED",
          "Externs files should be annotated with @externs in the @fileoverview block.");

  public static final DiagnosticGroup ALL_DIAGNOSTICS =
      new DiagnosticGroup(
          INVALID_SUPPRESS,
          CONSTRUCTOR_DISALLOWED_JSDOC,
          MISSING_JSDOC,
          MISSING_PARAMETER_JSDOC,
          MIXED_PARAM_JSDOC_STYLES,
          MISSING_RETURN_JSDOC,
          MUST_BE_PRIVATE,
          MUST_HAVE_TRAILING_UNDERSCORE,
          OPTIONAL_PARAM_NOT_MARKED_OPTIONAL,
          OPTIONAL_TYPE_NOT_USING_OPTIONAL_NAME,
          WRONG_NUMBER_OF_PARAMS,
          INCORRECT_PARAM_NAME,
          EXTERNS_FILES_SHOULD_BE_ANNOTATED);

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
    switch (n.getType()) {
      case Token.FUNCTION:
        visitFunction(t, n, parent);
        break;
      case Token.ASSIGN:
        // If the right side is a function it will be handled when the function is visited.
        if (!n.getLastChild().isFunction()) {
          visitNonFunction(t, n);
        }
        checkStyleForPrivateProperties(t, n);
        break;
      case Token.VAR:
      case Token.LET:
      case Token.CONST:
        for (Node decl : n.children()) {
          // If the right side is a function it will be handled when the function is visited.
          if (decl.getFirstChild() == null || !decl.getFirstChild().isFunction()) {
            visitNonFunction(t, n);
          }
        }
        break;
      case Token.STRING_KEY:
        // If the value is a function it will be handled when the function is visited.
        if (n.getFirstChild() == null || !n.getFirstChild().isFunction()) {
          visitNonFunction(t, n);
        }
        break;
      case Token.MEMBER_FUNCTION_DEF:
      case Token.GETTER_DEF:
      case Token.SETTER_DEF:
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

  private void visitNonFunction(NodeTraversal t, Node n) {
    JSDocInfo jsDoc = n.getJSDocInfo();
    if (jsDoc == null) {
      return;
    }

    if (!n.isScript()) {
      checkSuppressionsOnNonFunction(t, n, jsDoc);
    }
  }

  private void checkStyleForPrivateProperties(NodeTraversal t, Node n) {
    JSDocInfo jsDoc = NodeUtil.getBestJSDocInfo(n);
    String name;
    if (n.isMemberFunctionDef() || n.isGetterDef() || n.isSetterDef()) {
      name = n.getString();
    } else {
      Preconditions.checkState(n.isAssign());
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
  private void checkSuppressionsOnNonFunction(NodeTraversal t, Node n, JSDocInfo jsDoc) {
    // Suppressions that are allowed to be in places other than functions and @fileoverview blocks.
    Set<String> specialSuppressions =
        ImmutableSet.of("const", "duplicate", "extraRequire", "missingRequire");

    Set<String> suppressions = Sets.difference(jsDoc.getSuppressions(), specialSuppressions);
    if (!suppressions.isEmpty()) {
      t.report(n, INVALID_SUPPRESS);
    }
  }

  private void visitFunction(NodeTraversal t, Node function, Node parent) {
    JSDocInfo jsDoc = NodeUtil.getBestJSDocInfo(function);

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

    if (parent.isMemberFunctionDef()
        && "constructor".equals(parent.getString())
        && jsDoc != null
        && !jsDoc.getVisibility().equals(Visibility.INHERITED)) {
      t.report(function, CONSTRUCTOR_DISALLOWED_JSDOC);
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
    if (!t.inGlobalHoistScope()) {
      return false;
    }
    if (NodeUtil.isFunctionDeclaration(function)) {
      return true;
    }
    if (NodeUtil.isNameDeclaration(function.getGrandparent()) || function.getParent().isAssign()) {
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

  private void checkReturn(NodeTraversal t, Node function, JSDocInfo jsDoc) {
    if (jsDoc != null
        && (jsDoc.hasType()
            || jsDoc.hasReturnType()
            || jsDoc.isOverride()
            || jsDoc.isConstructor())) {
      return;
    }
    if (function.getFirstChild().getJSDocInfo() != null) {
      return;
    }

    FindNonTrivialReturn finder = new FindNonTrivialReturn();
    NodeTraversal.traverseEs6(compiler, function.getLastChild(), finder);
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
