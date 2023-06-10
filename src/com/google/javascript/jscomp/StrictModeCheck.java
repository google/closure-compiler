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
import com.google.javascript.rhino.jstype.JSType;
import java.util.HashSet;

/**
 * Checks that the code obeys the static restrictions of strict mode:
 *
 * <ol>
 *   <li>No use of "with".
 *   <li>No deleting variables, functions, or arguments.
 *   <li>No re-declarations or assignments of "eval" or arguments.
 *   <li>No use of arguments.callee
 *   <li>No use of arguments.caller
 *   <li>Class: Always under strict mode
 *   <li>In addition, no duplicate class method names
 * </ol>
 */
class StrictModeCheck extends AbstractPostOrderCallback implements CompilerPass {

  static final DiagnosticType USE_OF_WITH =
      DiagnosticType.error(
          "JSC_USE_OF_WITH", "The 'with' statement cannot be used in strict mode.");

  static final DiagnosticType EVAL_DECLARATION =
      DiagnosticType.error("JSC_EVAL_DECLARATION", "\"eval\" cannot be redeclared in strict mode");

  static final DiagnosticType EVAL_ASSIGNMENT =
      DiagnosticType.error(
          "JSC_EVAL_ASSIGNMENT", "the \"eval\" object cannot be reassigned in strict mode");

  static final DiagnosticType ARGUMENTS_DECLARATION =
      DiagnosticType.error(
          "JSC_ARGUMENTS_DECLARATION", "\"arguments\" cannot be redeclared in strict mode");

  static final DiagnosticType ARGUMENTS_ASSIGNMENT =
      DiagnosticType.error(
          "JSC_ARGUMENTS_ASSIGNMENT",
          "the \"arguments\" object cannot be reassigned in strict mode");

  static final DiagnosticType ARGUMENTS_CALLEE_FORBIDDEN =
      DiagnosticType.error(
          "JSC_ARGUMENTS_CALLEE_FORBIDDEN", "\"arguments.callee\" cannot be used in strict mode");

  static final DiagnosticType ARGUMENTS_CALLER_FORBIDDEN =
      DiagnosticType.error(
          "JSC_ARGUMENTS_CALLER_FORBIDDEN", "\"arguments.caller\" cannot be used in strict mode");

  static final DiagnosticType FUNCTION_CALLER_FORBIDDEN =
      DiagnosticType.error(
          "JSC_FUNCTION_CALLER_FORBIDDEN",
          "A function''s \"caller\" property cannot be used in strict mode");

  static final DiagnosticType FUNCTION_ARGUMENTS_PROP_FORBIDDEN =
      DiagnosticType.error(
          "JSC_FUNCTION_ARGUMENTS_PROP_FORBIDDEN",
          "A function''s \"arguments\" property cannot be used in strict mode");

  static final DiagnosticType DELETE_VARIABLE =
      DiagnosticType.error(
          "JSC_DELETE_VARIABLE",
          "variables, functions, and arguments cannot be deleted in strict mode");

  static final DiagnosticType DUPLICATE_MEMBER =
      DiagnosticType.warning(
          "JSC_DUPLICATE_MEMBER",
          "Class or object literal contains duplicate member \"{0}\". In non-strict code, the last"
              + " duplicate will overwrite the others.");

  private final AbstractCompiler compiler;
  private final CheckLevel defaultLevel;

  StrictModeCheck(AbstractCompiler compiler, CheckLevel defaultLevel) {
    this.compiler = compiler;
    this.defaultLevel = defaultLevel;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseRoots(compiler, this, externs, root);
    NodeTraversal.traverse(compiler, root, new NonExternChecks());
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isAssign()) {
      checkAssignment(n);
    } else if (n.isDelProp()) {
      checkDelete(t, n);
    } else if (n.isObjectLit()) {
      checkObjectLiteralOrClass(n);
    } else if (n.isClass()) {
      checkObjectLiteralOrClass(n.getLastChild());
    } else if (n.isWith()) {
      checkWith(n);
    }
  }

  /** Reports a warning for with statements. */
  private void checkWith(Node n) {
    JSDocInfo info = n.getJSDocInfo();
    boolean allowWith = info != null && info.getSuppressions().contains("with");
    if (!allowWith) {
      this.report(n, USE_OF_WITH);
    }
  }

  /**
   * Determines if the given name is a declaration, which can be a declaration of a variable,
   * function, or argument.
   */
  private static boolean isDeclaration(Node n) {
    switch (n.getParent().getToken()) {
      case LET:
      case CONST:
      case VAR:
      case CATCH:
        return true;
      case FUNCTION:
        return n == n.getParent().getFirstChild();

      case PARAM_LIST:
        return n.getGrandparent().isFunction();

      default:
        return false;
    }
  }

  /** Checks that an assignment is not to the "arguments" object. */
  private void checkAssignment(Node n) {
    if (n.getFirstChild().isName()) {
      if ("arguments".equals(n.getFirstChild().getString())) {
        this.report(n, ARGUMENTS_ASSIGNMENT);
      } else if ("eval".equals(n.getFirstChild().getString())) {
        // Note that assignment to eval is already illegal because any use of
        // that name is illegal.
        this.report(n, EVAL_ASSIGNMENT);
      }
    }
  }

  /** Checks that variables, functions, and arguments are not deleted. */
  private void checkDelete(NodeTraversal t, Node n) {
    if (n.getFirstChild().isName()) {
      Var v = t.getScope().getVar(n.getFirstChild().getString());
      if (v != null) {
        this.report(n, DELETE_VARIABLE);
      }
    }
  }

  /** Checks that object literal keys or class method names are valid. */
  private void checkObjectLiteralOrClass(Node n) {
    HashSet<String> getters = new HashSet<>();
    HashSet<String> setters = new HashSet<>();
    HashSet<String> staticGetters = new HashSet<>();
    HashSet<String> staticSetters = new HashSet<>();

    /*
     * Iterate backwards because the last duplicate is the one that will be used in sloppy or ES6
     * code. The earlier duplicates are the ones that should be removed.
     */
    for (Node key = n.getLastChild(); key != null; key = key.getPrevious()) {
      if (key.isEmpty()
          || key.isComputedProp()
          || key.isSpread()
          || key.isComputedFieldDef()
          // Computed properties cannot be computed at compile time
          || NodeUtil.isClassStaticBlock(key)
      // Will not check since whether duplicates are declared/assigned cannot be determined at
      // compile time since we do not know which code will be run
      ) {
        continue;
      }

      String keyName = key.getString();
      if (!key.isSetterDef()) {
        // normal property and getter cases
        HashSet<String> set = key.isStaticMember() ? staticGetters : getters;
        if (!set.add(keyName)) {
          this.report(key, DUPLICATE_MEMBER, keyName);
        }
      }
      if (!key.isGetterDef()) {
        // normal property and setter cases
        HashSet<String> set = key.isStaticMember() ? staticSetters : setters;
        if (!set.add(keyName)) {
          this.report(key, DUPLICATE_MEMBER, keyName);
        }
      }
    }
  }

  /** Checks that are performed on non-extern code only. */
  private class NonExternChecks extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isName() && isDeclaration(n)) {
        checkDeclaration(n);
      } else if (n.isGetProp()) {
        checkGetProp(n);
      }
    }

    /** Checks for illegal declarations. */
    private void checkDeclaration(Node n) {
      if ("eval".equals(n.getString())) {
        report(n, EVAL_DECLARATION);
      } else if ("arguments".equals(n.getString())) {
        report(n, ARGUMENTS_DECLARATION);
      }
    }

    /** Checks that the arguments.callee is not used. */
    private void checkGetProp(Node n) {
      Node target = n.getFirstChild();
      String name = n.getString();
      if (name.equals("callee")) {
        if (target.isName() && target.getString().equals("arguments")) {
          report(n, ARGUMENTS_CALLEE_FORBIDDEN);
        }
      } else if (name.equals("caller")) {
        if (target.isName() && target.getString().equals("arguments")) {
          report(n, ARGUMENTS_CALLER_FORBIDDEN);
        } else if (isFunctionType(target)) {
          report(n, FUNCTION_CALLER_FORBIDDEN);
        }
      } else if (name.equals("arguments") && isFunctionType(target)) {
        report(n, FUNCTION_ARGUMENTS_PROP_FORBIDDEN);
      }
    }
  }

  private static boolean isFunctionType(Node n) {
    JSType type = n.getJSType();
    return (type != null && type.isFunctionType());
  }

  private void report(Node n, DiagnosticType diagnostic, String... args) {
    this.compiler.report(
        JSError.builder(diagnostic, args).setLevel(this.defaultLevel).setNode(n).build());
  }
}
