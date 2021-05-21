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
package com.google.javascript.jscomp.lint;

import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import javax.annotation.Nullable;

/** Checks for errors related to interfaces. */
public final class CheckInterfaces extends AbstractPostOrderCallback implements CompilerPass {
  // Placeholder class name for error reporting on anonymous classes.
  private static final String ANONYMOUS_CLASSNAME = "<anonymous>";

  public static final DiagnosticType NON_DECLARATION_STATEMENT_IN_INTERFACE =
      DiagnosticType.disabled(
          "JSC_NON_DECLARATION_STATEMENT_IN_INTERFACE",
          "@interface or @record functions should not contain statements other than field"
              + " declarations");

  public static final DiagnosticType MISSING_JSDOC_IN_DECLARATION_STATEMENT =
      DiagnosticType.disabled(
          "JSC_MISSING_JSDOC_IN_DECLARATION_STATEMENT",
          "@interface or @record functions must contain JSDoc for each field declaration.");

  public static final DiagnosticType INTERFACE_CLASS_NONSTATIC_METHOD_NOT_EMPTY =
      DiagnosticType.disabled(
          "JSC_INTERFACE_CLASS_NONSTATIC_METHOD_NOT_EMPTY",
          "interface methods must have an empty body");

  public static final DiagnosticType INTERFACE_CONSTRUCTOR_SHOULD_NOT_TAKE_ARGS =
      DiagnosticType.disabled(
          "JSC_INTERFACE_CONSTRUCTOR_SHOULD_NOT_TAKE_ARGS",
          "Interface constructors should not take any arguments");

  public static final DiagnosticType STATIC_MEMBER_FUNCTION_IN_INTERFACE_CLASS =
      DiagnosticType.disabled(
          "JSC_STATIC_MEMBER_FUNCTION_IN_INTERFACE_CLASS",
          "Interface class should not have static member functions. "
              + "Consider pulling out the static method into a flat name as {0}_{1}");

  public static final DiagnosticType INTERFACE_DEFINED_WITH_EXTENDS =
      DiagnosticType.disabled(
          "JSC_INTERFACE_DEFINED_WITH_EXTENDS",
          "Interface/Record class should use the `@extends` annotation instead of extends"
              + " keyword.");

  private final AbstractCompiler compiler;

  public CheckInterfaces(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  /** Whether jsDoc is present and has an {@code @interface} or {@code @record} annotation */
  private static boolean isInterface(JSDocInfo jsDoc) {
    return jsDoc != null && jsDoc.isInterface();
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case FUNCTION:
        {
          JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(n);
          if (isInterface(jsdoc)) {
            checkInterfaceConstructorArgs(t, n);
            checkConstructorBlock(t, n);
          }
          break;
        }
      case CLASS:
        {
          JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(n);
          if (isInterface(jsdoc)) {
            if (n.getSecondChild() != null && n.getSecondChild().isName()) {
              t.report(n, INTERFACE_DEFINED_WITH_EXTENDS);
            }
            Node ctorDef = NodeUtil.getEs6ClassConstructorMemberFunctionDef(n);
            if (ctorDef != null) {
              Node ctor = ctorDef.getFirstChild();
              checkInterfaceConstructorArgs(t, ctor);
              checkConstructorBlock(t, ctor);
            }
            checkClassMethods(t, n, ctorDef);
          }
          break;
        }
      default:
        return;
    }
  }

  private static void checkInterfaceConstructorArgs(NodeTraversal t, Node funcNode) {
    Node args = funcNode.getSecondChild();
    if (args.hasChildren()) {
      t.report(args.getFirstChild(), INTERFACE_CONSTRUCTOR_SHOULD_NOT_TAKE_ARGS);
    }
  }

  // Non-static class methods must be empty for `@record` and `@interface` as per the style guide.
  private static void checkClassMethods(NodeTraversal t, Node classNode, @Nullable Node ctorDef) {
    Node classMembers = classNode.getLastChild();
    checkState(classMembers.isClassMembers(), classMembers);
    for (Node memberFuncDef = classMembers.getFirstChild();
        memberFuncDef != null;
        memberFuncDef = memberFuncDef.getNext()) {
      if (memberFuncDef.equals(ctorDef)) {
        continue; // constructor was already checked; don't check here.
      }
      if (memberFuncDef.isStaticMember()) {
        // `static foo() {...}`
        String className = NodeUtil.getName(classNode);
        if (className == null) {
          className = ANONYMOUS_CLASSNAME;
        }
        String funcName = memberFuncDef.getString();
        t.report(memberFuncDef, STATIC_MEMBER_FUNCTION_IN_INTERFACE_CLASS, className, funcName);
      } else {
        Node block = memberFuncDef.getLastChild().getLastChild();
        if (block.hasChildren()) {
          t.report(block.getFirstChild(), INTERFACE_CLASS_NONSTATIC_METHOD_NOT_EMPTY);
        }
      }
    }
  }

  // `@record` constructors can be non-empty, check that only field declarations exist in them.
  private static void checkConstructorBlock(NodeTraversal t, Node funcNode) {
    Node block = funcNode.getLastChild();
    if (!block.hasChildren()) {
      return;
    }

    for (Node stmt = block.getFirstChild(); stmt != null; stmt = stmt.getNext()) {
      if (!isThisPropAccess(stmt)) {
        // Only field declarations are expected inside @record and @interface.
        t.report(stmt, NON_DECLARATION_STATEMENT_IN_INTERFACE);
        break;
      } else if (stmt.getFirstChild().getJSDocInfo() == null) {
        // A field declaration that's missing a JSDoc.
        t.report(stmt, MISSING_JSDOC_IN_DECLARATION_STATEMENT);
        break;
      }
    }
  }

  private static boolean isThisPropAccess(Node stmt) {
    return stmt.isExprResult()
        && stmt.getFirstChild().isGetProp()
        && stmt.getFirstFirstChild().isThis();
  }
}

