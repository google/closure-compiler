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

import com.google.common.collect.Lists;

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.Scope.Var;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Checks that the code obeys the static restrictions of strict mode:
 * <ol>
 * <li> No use of "with".
 * <li> No deleting variables, functions, or arguments.
 * <li> No re-declarations or assignments of "eval" or arguments.
 * <li> No use of "eval" (optional check for Caja).
 * </ol>
 *
 */
class StrictModeCheck extends AbstractPostOrderCallback
    implements CompilerPass {

  static final DiagnosticType UNKNOWN_VARIABLE = DiagnosticType.error(
      "JSC_UNKNOWN_VARIABLE", "unknown variable {0}");

  static final DiagnosticType WITH_DISALLOWED = DiagnosticType.error(
      "JSC_WITH_DISALLOWED", "\"with\" cannot be used in ES5 strict mode");

  static final DiagnosticType EVAL_USE = DiagnosticType.error(
      "JSC_EVAL_USE", "\"eval\" cannot be used in Caja");

  static final DiagnosticType EVAL_DECLARATION = DiagnosticType.error(
      "JSC_EVAL_DECLARATION",
      "\"eval\" cannot be redeclared in ES5 strict mode");

  static final DiagnosticType EVAL_ASSIGNMENT = DiagnosticType.error(
      "JSC_EVAL_ASSIGNMENT",
      "the \"eval\" object cannot be reassigned in ES5 strict mode");

  static final DiagnosticType ARGUMENTS_DECLARATION = DiagnosticType.error(
      "JSC_ARGUMENTS_DECLARATION",
      "\"arguments\" cannot be redeclared in ES5 strict mode");

  static final DiagnosticType ARGUMENTS_ASSIGNMENT = DiagnosticType.error(
      "JSC_ARGUMENTS_ASSIGNMENT",
      "the \"arguments\" object cannot be reassigned in ES5 strict mode");

  static final DiagnosticType DELETE_VARIABLE = DiagnosticType.error(
      "JSC_DELETE_VARIABLE",
      "variables, functions, and arguments cannot be deleted in "
      + "ES5 strict mode");

  static final DiagnosticType ILLEGAL_NAME = DiagnosticType.error(
      "JSC_ILLEGAL_NAME",
      "identifiers ending in '__' cannot be used in Caja");

  private final AbstractCompiler compiler;
  private final boolean noVarCheck;
  private final boolean noCajaChecks;

  StrictModeCheck(AbstractCompiler compiler) {
    this(compiler, false, false);
  }

  StrictModeCheck(
      AbstractCompiler compiler, boolean noVarCheck, boolean noCajaChecks) {
    this.compiler = compiler;
    this.noVarCheck = noVarCheck;
    this.noCajaChecks = noCajaChecks;
  }

  @Override public void process(Node externs, Node root) {
    NodeTraversal.traverseRoots(
        compiler, Lists.newArrayList(externs, root), this);
    NodeTraversal.traverse(compiler, root, new NonExternChecks());
  }

  @Override public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.getType() == Token.WITH) {
      t.report(n, WITH_DISALLOWED);
    } else if (n.getType() == Token.NAME) {
      if (!isDeclaration(n)) {
        checkNameUse(t, n);
      }
    } else if (n.getType() == Token.ASSIGN) {
      checkAssignment(t, n);
    } else if (n.getType() == Token.DELPROP) {
      checkDelete(t, n);
    } else if (n.getType() == Token.OBJECTLIT) {
      checkObjectLiteral(t, n);
    } else if (n.getType() == Token.LABEL) {
      checkLabel(t, n);
    }
  }

  /**
   * Determines if the given name is a declaration, which can be a declaration
   * of a variable, function, or argument.
   */
  private static boolean isDeclaration(Node n) {
    switch (n.getParent().getType()) {
      case Token.VAR:
      case Token.FUNCTION:
      case Token.CATCH:
        return true;

      case Token.LP:
        return n.getParent().getParent().getType() == Token.FUNCTION;

      default:
        return false;
    }
  }

  /** Checks that the given name is used legally. */
  private void checkNameUse(NodeTraversal t, Node n) {
    Var v = t.getScope().getVar(n.getString());
    if (v == null) {
      // In particular, this prevents creating a global variable by assigning
      // to it without a declaration.
      if (!noVarCheck) {
        t.report(n, UNKNOWN_VARIABLE, n.getString());
      }
    }

    if (!noCajaChecks) {
      if ("eval".equals(n.getString())) {
        t.report(n, EVAL_USE);
      } else if (n.getString().endsWith("__")) {
        t.report(n, ILLEGAL_NAME);
      }
    }
  }

  /** Checks that an assignment is not to the "arguments" object. */
  private void checkAssignment(NodeTraversal t, Node n) {
    if (n.getFirstChild().getType() == Token.NAME) {
      if ("arguments".equals(n.getFirstChild().getString())) {
        t.report(n, ARGUMENTS_ASSIGNMENT);
      } else if ("eval".equals(n.getFirstChild().getString())) {
        // Note that assignment to eval is already illegal because any use of
        // that name is illegal.
        if (noCajaChecks) {
          t.report(n, EVAL_ASSIGNMENT);
        }
      }
    }
  }

  /** Checks that variables, functions, and arguments are not deleted. */
  private void checkDelete(NodeTraversal t, Node n) {
    if (n.getFirstChild().getType() == Token.NAME) {
      Var v = t.getScope().getVar(n.getFirstChild().getString());
      if (v != null) {
        t.report(n, DELETE_VARIABLE);
      }
    }
  }

  /** Checks that object literal keys are valid. */
  private void checkObjectLiteral(NodeTraversal t, Node n) {
    if (!noCajaChecks) {
      for (Node key = n.getFirstChild();
           key != null;
           key = key.getNext()) {
        if (key.getType() != Token.NUMBER && key.getString().endsWith("__")) {
          t.report(key, ILLEGAL_NAME);
        }
      }
    }
  }

  /** Checks that label names are valid. */
  private void checkLabel(NodeTraversal t, Node n) {
    if (n.getFirstChild().getString().endsWith("__")) {
      if (!noCajaChecks) {
        t.report(n.getFirstChild(), ILLEGAL_NAME);
      }
    }
  }

  /** Checks that are performed on non-extern code only. */
  private class NonExternChecks extends AbstractPostOrderCallback {
    @Override public void visit(NodeTraversal t, Node n, Node parent) {
      if ((n.getType() == Token.NAME) && isDeclaration(n)) {
        checkDeclaration(t, n);
      } else if (n.getType() == Token.GETPROP) {
        checkProperty(t, n);
      }
    }

    /** Checks for illegal declarations. */
    private void checkDeclaration(NodeTraversal t, Node n) {
      if ("eval".equals(n.getString())) {
        t.report(n, EVAL_DECLARATION);
      } else if ("arguments".equals(n.getString())) {
        t.report(n, ARGUMENTS_DECLARATION);
      } else if (n.getString().endsWith("__")) {
        if (!noCajaChecks) {
          t.report(n, ILLEGAL_NAME);
        }
      }
    }

    /** Checks for illegal property accesses. */
    private void checkProperty(NodeTraversal t, Node n) {
      if (n.getLastChild().getString().endsWith("__")) {
        if (!noCajaChecks) {
          t.report(n.getLastChild(), ILLEGAL_NAME);
        }
      }
    }
  }
}
