/*
 * Copyright 2009 The Closure Compiler Authors.
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
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TypeI;

import java.util.HashSet;
import java.util.Set;

/**
 * Checks that the code obeys the static restrictions of strict mode:
 * <ol>
 * <li> No use of "with".
 * <li> No deleting variables, functions, or arguments.
 * <li> No re-declarations or assignments of "eval" or arguments.
 * <li> No use of arguments.callee
 * <li> No use of arguments.caller
 * <li> Class: Always under strict mode
 * <li>   In addition, no duplicate class method names
 * </ol>
 *
 */
class StrictModeCheck extends AbstractPostOrderCallback
    implements CompilerPass {

  static final DiagnosticType USE_OF_WITH = DiagnosticType.warning(
      "JSC_USE_OF_WITH",
      "The 'with' statement cannot be used in ES5 strict mode.");

  static final DiagnosticType EVAL_DECLARATION = DiagnosticType.warning(
      "JSC_EVAL_DECLARATION",
      "\"eval\" cannot be redeclared in ES5 strict mode");

  static final DiagnosticType EVAL_ASSIGNMENT = DiagnosticType.warning(
      "JSC_EVAL_ASSIGNMENT",
      "the \"eval\" object cannot be reassigned in ES5 strict mode");

  static final DiagnosticType ARGUMENTS_DECLARATION = DiagnosticType.warning(
      "JSC_ARGUMENTS_DECLARATION",
      "\"arguments\" cannot be redeclared in ES5 strict mode");

  static final DiagnosticType ARGUMENTS_ASSIGNMENT = DiagnosticType.warning(
      "JSC_ARGUMENTS_ASSIGNMENT",
      "the \"arguments\" object cannot be reassigned in ES5 strict mode");

  static final DiagnosticType ARGUMENTS_CALLEE_FORBIDDEN = DiagnosticType.warning(
      "JSC_ARGUMENTS_CALLEE_FORBIDDEN",
      "\"arguments.callee\" cannot be used in ES5 strict mode");

  static final DiagnosticType ARGUMENTS_CALLER_FORBIDDEN = DiagnosticType.warning(
      "JSC_ARGUMENTS_CALLER_FORBIDDEN",
      "\"arguments.caller\" cannot be used in ES5 strict mode");

  static final DiagnosticType FUNCTION_CALLER_FORBIDDEN = DiagnosticType.warning(
      "JSC_FUNCTION_CALLER_FORBIDDEN",
      "A function''s \"caller\" property cannot be used in ES5 strict mode");

  static final DiagnosticType FUNCTION_ARGUMENTS_PROP_FORBIDDEN = DiagnosticType.warning(
      "JSC_FUNCTION_ARGUMENTS_PROP_FORBIDDEN",
      "A function''s \"arguments\" property cannot be used in ES5 strict mode");



  static final DiagnosticType DELETE_VARIABLE = DiagnosticType.warning(
      "JSC_DELETE_VARIABLE",
      "variables, functions, and arguments cannot be deleted in "
      + "ES5 strict mode");

  static final DiagnosticType DUPLICATE_OBJECT_KEY = DiagnosticType.warning(
      "JSC_DUPLICATE_OBJECT_KEY",
      "object literals cannot contain duplicate keys in ES5 strict mode");

  static final DiagnosticType DUPLICATE_CLASS_METHODS = DiagnosticType.error(
      "JSC_DUPLICATE_CLASS_METHODS",
      "Classes cannot contain duplicate method names");

  static final DiagnosticType BAD_FUNCTION_DECLARATION = DiagnosticType.error(
      "JSC_BAD_FUNCTION_DECLARATION",
      "functions can only be declared at top level or immediately within"
      + " another function in ES5 strict mode");

  private final AbstractCompiler compiler;

  StrictModeCheck(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override public void process(Node externs, Node root) {
    NodeTraversal.traverseRootsEs6(compiler, this, externs, root);
    NodeTraversal.traverseEs6(compiler, root, new NonExternChecks());
  }

  @Override public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isFunction()) {
      checkFunctionUse(t, n);
    } else if (n.isAssign()) {
      checkAssignment(t, n);
    } else if (n.isDelProp()) {
      checkDelete(t, n);
    } else if (n.isObjectLit()) {
      checkObjectLiteralOrClass(t, n);
    } else if (n.isClass()) {
      checkObjectLiteralOrClass(t, n.getLastChild());
    } else if (n.isWith()) {
      checkWith(t, n);
    }
  }

  /** Reports a warning for with statements. */
  private static void checkWith(NodeTraversal t, Node n) {
    JSDocInfo info = n.getJSDocInfo();
    boolean allowWith =
        info != null && info.getSuppressions().contains("with");
    if (!allowWith) {
      t.report(n, USE_OF_WITH);
    }
  }

  /** Checks that the function is used legally. */
  private static void checkFunctionUse(NodeTraversal t, Node n) {
    if (NodeUtil.isFunctionDeclaration(n) && !NodeUtil.isHoistedFunctionDeclaration(n)) {
      t.report(n, BAD_FUNCTION_DECLARATION);
    }
  }

  /**
   * Determines if the given name is a declaration, which can be a declaration
   * of a variable, function, or argument.
   */
  private static boolean isDeclaration(Node n) {
    switch (n.getParent().getType()) {
      case Token.LET:
      case Token.CONST:
      case Token.VAR:
      case Token.FUNCTION:
      case Token.CATCH:
        return true;

      case Token.PARAM_LIST:
        return n.getGrandparent().isFunction();

      default:
        return false;
    }
  }

  /** Checks that an assignment is not to the "arguments" object. */
  private static void checkAssignment(NodeTraversal t, Node n) {
    if (n.getFirstChild().isName()) {
      if ("arguments".equals(n.getFirstChild().getString())) {
        t.report(n, ARGUMENTS_ASSIGNMENT);
      } else if ("eval".equals(n.getFirstChild().getString())) {
        // Note that assignment to eval is already illegal because any use of
        // that name is illegal.
        t.report(n, EVAL_ASSIGNMENT);
      }
    }
  }

  /** Checks that variables, functions, and arguments are not deleted. */
  private static void checkDelete(NodeTraversal t, Node n) {
    if (n.getFirstChild().isName()) {
      Var v = t.getScope().getVar(n.getFirstChild().getString());
      if (v != null) {
        t.report(n, DELETE_VARIABLE);
      }
    }
  }

  /** Checks that object literal keys or class method names are valid. */
  private static void checkObjectLiteralOrClass(NodeTraversal t, Node n) {
    Set<String> getters = new HashSet<>();
    Set<String> setters = new HashSet<>();
    for (Node key = n.getFirstChild();
         key != null;
         key = key.getNext()) {
      if (!key.isSetterDef()) {
        // normal property and getter cases
        if (!getters.add(key.getString())) {
          if (n.isClassMembers()) {
            t.report(key, DUPLICATE_CLASS_METHODS);
          } else {
            t.report(key, DUPLICATE_OBJECT_KEY);
          }
        }
      }
      if (!key.isGetterDef()) {
        // normal property and setter cases
        if (!setters.add(key.getString())) {
          if (n.isClassMembers()) {
            t.report(key, DUPLICATE_CLASS_METHODS);
          } else {
            t.report(key, DUPLICATE_OBJECT_KEY);
          }
        }
      }
    }
  }

  /** Checks that are performed on non-extern code only. */
  private static class NonExternChecks extends AbstractPostOrderCallback {
    @Override public void visit(NodeTraversal t, Node n, Node parent) {
      if ((n.isName()) && isDeclaration(n)) {
        checkDeclaration(t, n);
      } else if (n.isGetProp()) {
        checkGetProp(t, n);
      }
    }

    /** Checks for illegal declarations. */
    private void checkDeclaration(NodeTraversal t, Node n) {
      if ("eval".equals(n.getString())) {
        t.report(n, EVAL_DECLARATION);
      } else if ("arguments".equals(n.getString())) {
        t.report(n, ARGUMENTS_DECLARATION);
      }
    }

    /** Checks that the arguments.callee is not used. */
    private void checkGetProp(NodeTraversal t, Node n) {
      Node target = n.getFirstChild();
      Node prop = n.getLastChild();
      if (prop.getString().equals("callee")) {
        if (target.isName() && target.getString().equals("arguments")) {
          t.report(n, ARGUMENTS_CALLEE_FORBIDDEN);
        }
      } else if (prop.getString().equals("caller")) {
        if (target.isName() && target.getString().equals("arguments")) {
          t.report(n, ARGUMENTS_CALLER_FORBIDDEN);
        } else if (isFunctionType(target)) {
          t.report(n, FUNCTION_CALLER_FORBIDDEN);
        }
      } else if (prop.getString().equals("arguments")
          && isFunctionType(target)) {
        t.report(n, FUNCTION_ARGUMENTS_PROP_FORBIDDEN);
      }
    }
  }

  private static boolean isFunctionType(Node n) {
    TypeI type = n.getTypeI();
    return (type != null && type.isFunctionType());
  }
}
