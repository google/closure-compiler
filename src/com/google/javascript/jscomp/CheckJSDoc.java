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

package com.google.javascript.jscomp;

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import javax.annotation.Nullable;

/**
 * Checks for misplaced, misused or deprecated JSDoc annotations.
 *
 * @author chadkillingsworth@gmail.com (Chad Killingsworth)
 */
final class CheckJSDoc extends AbstractPostOrderCallback implements HotSwapCompilerPass {

  public static final DiagnosticType MISPLACED_MSG_ANNOTATION =
      DiagnosticType.disabled("JSC_MISPLACED_MSG_ANNOTATION",
          "Misplaced message annotation. @desc, @hidden, and @meaning annotations should only"
                  + "be on message nodes.");

  public static final DiagnosticType MISPLACED_ANNOTATION =
      DiagnosticType.warning("JSC_MISPLACED_ANNOTATION",
          "Misplaced {0} annotation. {1}");

  public static final DiagnosticType ANNOTATION_DEPRECATED =
      DiagnosticType.warning("JSC_ANNOTATION_DEPRECATED",
          "The {0} annotation is deprecated. {1}");

  public static final DiagnosticType DISALLOWED_MEMBER_JSDOC =
      DiagnosticType.warning("JSC_DISALLOWED_MEMBER_JSDOC",
          "Class level JSDocs (@interface, @extends, etc.) are not allowed on class members");

  static final DiagnosticType ARROW_FUNCTION_AS_CONSTRUCTOR = DiagnosticType.error(
      "JSC_ARROW_FUNCTION_AS_CONSTRUCTOR",
      "Arrow functions cannot be used as constructors");

  static final DiagnosticType DEFAULT_PARAM_MUST_BE_MARKED_OPTIONAL = DiagnosticType.error(
      "JSC_DEFAULT_PARAM_MUST_BE_MARKED_OPTIONAL",
      "Inline JSDoc on default parameters must be marked as optional");

  private final AbstractCompiler compiler;

  CheckJSDoc(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, externs, this);
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverseEs6(compiler, scriptRoot, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    JSDocInfo info = n.getJSDocInfo();
    validateTypeAnnotations(n, info);
    validateFunctionJsDoc(n, info);
    validateMsgJsDoc(n, info);
    validateDeprecatedJsDoc(n, info);
    validateNoCollapse(n, info);
    validateClassLevelJsDoc(n, info);
    validateArrowFunction(n);
    validateDefaultValue(n, info);
    validateTemplates(n, info);
    validateTypedefs(n, info);
    validateNoSideEffects(n, info);
    validateAbstractJsDoc(n, info);
  }

  private void validateTypedefs(Node n, JSDocInfo info) {
    if (info != null && info.getTypedefType() != null && isClassDecl(n)) {
      reportMisplaced(n, "typedef", "@typedef does not make sense on a class declaration.");
    }
  }

  private void validateTemplates(Node n, JSDocInfo info) {
    if (info != null
        && !info.getTemplateTypeNames().isEmpty()
        && !info.isConstructorOrInterface()
        && !isClassDecl(n)
        && !info.containsFunctionDeclaration()) {
      if (getFunctionDecl(n) != null) {
        reportMisplaced(n, "template",
            "The template variable is unused."
            + " Please remove the @template annotation.");
      } else {
        reportMisplaced(n, "template",
            "@template is only allowed in class, constructor, interface, function "
            + "or method declarations");
      }
    }
  }

  /**
   * @return The function node associated with the function declaration associated with the
   *     specified node, no null if no such function exists.
   */
  @Nullable
  private Node getFunctionDecl(Node n) {
    if (n.isFunction()) {
      return n;
    }
    if (n.isMemberFunctionDef()) {
      return n.getFirstChild();
    }
    if (n.isVar()
        && n.getFirstFirstChild() != null
        && n.getFirstFirstChild().isFunction()) {
      return n.getFirstFirstChild();
    }

    if (n.isAssign() && n.getFirstChild().isQualifiedName() && n.getLastChild().isFunction()) {
      return n.getLastChild();
    }

    return null;
  }

  private boolean isClassDecl(Node n) {
    return isClass(n)
        || (n.isAssign() && isClass(n.getLastChild()))
        || (NodeUtil.isNameDeclaration(n) && isNameIntializeWithClass(n.getFirstChild()))
        || isNameIntializeWithClass(n);
  }

  private boolean isNameIntializeWithClass(Node n) {
    return n != null && n.isName() && n.hasChildren() && isClass(n.getFirstChild());
  }

  private boolean isClass(Node n) {
    return n.isClass()
        || (n.isCall() && compiler.getCodingConvention().isClassFactoryCall(n));
  }

  /**
   * Checks that class-level annotations like @interface/@extends are not used on member functions.
   */
  private void validateClassLevelJsDoc(Node n, JSDocInfo info) {
    if (info != null && n.isMemberFunctionDef()
        && hasClassLevelJsDoc(info)) {
      report(n, DISALLOWED_MEMBER_JSDOC);
    }
  }

  private void validateAbstractJsDoc(Node n, JSDocInfo info) {
    if (info == null || !info.isAbstract()) {
      return;
    }
    Node functionNode = getFunctionDecl(n);

    if (functionNode == null) {
      // @abstract annotation on a non-function
      report(n, MISPLACED_ANNOTATION, "@abstract", "only functions or methods can be abstract");
      return;
    }

    if (NodeUtil.getFunctionBody(functionNode).hasChildren()) {
      // @abstract annotation on a function with a non-empty body
      report(n, MISPLACED_ANNOTATION, "@abstract",
          "function with a non-empty body cannot be abstract");
      return;
    }

    if (n.isMemberFunctionDef() && "constructor".equals(n.getString())) {
      // @abstract annotation on an ES6 constructor
      report(n, MISPLACED_ANNOTATION, "@abstract", "constructors cannot be abstract");
      return;
    }

    if (!n.isMemberFunctionDef() && !NodeUtil.isPrototypeMethod(functionNode)) {
      // @abstract annotation on a non-method (or static method) in ES5
      report(n, MISPLACED_ANNOTATION, "@abstract", "only functions or methods can be abstract");
      return;
    }

    if (n.isStaticMember()) {
      // @abstract annotation on a static method in ES6
      report(n, MISPLACED_ANNOTATION, "@abstract", "static methods cannot be abstract");
      return;
    }
  }

  private boolean hasClassLevelJsDoc(JSDocInfo info) {
    return info.isConstructorOrInterface()
        || info.hasBaseType()
        || info.getImplementedInterfaceCount() != 0
        || info.getExtendedInterfacesCount() != 0;
  }

  /**
   * Checks that deprecated annotations such as @expose are not present
   */
  private void validateDeprecatedJsDoc(Node n, JSDocInfo info) {
    if (info != null && info.isExpose()) {
      report(n, ANNOTATION_DEPRECATED, "@expose",
              "Use @nocollapse or @export instead.");
    }
  }

  /**
   * Warns when nocollapse annotations are present on nodes
   * which are not eligible for property collapsing.
   */
  private void validateNoCollapse(Node n, JSDocInfo info) {
    if (n.isFromExterns()) {
      if (info != null && info.isNoCollapse()) {
        // @nocollapse has no effect in externs
        reportMisplaced(n, "nocollapse", "This JSDoc has no effect in externs.");
      }
      return;
    }
    if (!NodeUtil.isPrototypePropertyDeclaration(n.getParent())) {
      return;
    }
    JSDocInfo jsdoc = n.getJSDocInfo();
    if (jsdoc != null && jsdoc.isNoCollapse()) {
      reportMisplaced(n, "nocollapse", "This JSDoc has no effect on prototype properties.");
    }
  }

  /**
   * Checks that JSDoc intended for a function is actually attached to a
   * function.
   */
  private void validateFunctionJsDoc(Node n, JSDocInfo info) {
    if (info == null) {
      return;
    }

    if (info.containsFunctionDeclaration() && !info.hasType()) {
      // This JSDoc should be attached to a FUNCTION node, or an assignment
      // with a function as the RHS, etc.
      switch (n.getType()) {
        case FUNCTION:
        case VAR:
        case LET:
        case CONST:
        case GETTER_DEF:
        case SETTER_DEF:
        case MEMBER_FUNCTION_DEF:
        case STRING_KEY:
        case COMPUTED_PROP:
        case EXPORT:
          return;
        case GETELEM:
        case GETPROP:
          if (n.getFirstChild().isQualifiedName()) {
            return;
          }
          break;
        case ASSIGN: {
          // TODO(tbreisacher): Check that the RHS of the assignment is a
          // function. Note that it can be a FUNCTION node, but it can also be
          // a call to goog.abstractMethod, goog.functions.constant, etc.
          return;
        }
      }
      reportMisplaced(n,
          "function", "This JSDoc is not attached to a function node. "
              + "Are you missing parentheses?");
    }
  }

  /**
   * Checks that annotations for messages ({@code @desc}, {@code @hidden},
   * and {@code @meaning})
   * are in the proper place, namely on names starting with MSG_ which
   * indicates they should be
   * extracted for translation. A later pass checks that the right side is
   * a call to goog.getMsg.
   */
  private void validateMsgJsDoc(Node n,
      JSDocInfo info) {
    if (info == null) {
      return;
    }

    if (info.getDescription() != null || info.isHidden() || info.getMeaning() != null) {
      boolean descOkay = false;
      switch (n.getType()) {
        case ASSIGN: {
          Node lhs = n.getFirstChild();
          if (lhs.isName()) {
            descOkay = lhs.getString().startsWith("MSG_");
          } else if (lhs.isQualifiedName()) {
            descOkay = lhs.getLastChild().getString().startsWith("MSG_");
          }
          break;
        }
        case VAR:
        case LET:
        case CONST:
          descOkay = n.getFirstChild().getString().startsWith("MSG_");
          break;
        case STRING_KEY:
          descOkay = n.getString().startsWith("MSG_");
          break;
      }
      if (!descOkay) {
        report(n, MISPLACED_MSG_ANNOTATION);
      }
    }
  }

  /**
   * Check that JSDoc with a {@code @type} annotation is in a valid place.
   */
  private void validateTypeAnnotations(Node n, JSDocInfo info) {
    if (info != null && info.hasType()) {
      boolean valid = false;
      switch (n.getType()) {
        // Function declarations are valid
        case FUNCTION:
          valid = NodeUtil.isFunctionDeclaration(n);
          break;
        // Object literal properties, catch declarations and variable
        // initializers are valid.
        case NAME:
        case DEFAULT_VALUE:
        case ARRAY_PATTERN:
        case OBJECT_PATTERN:
          Node parent = n.getParent();
          switch (parent.getType()) {
            case GETTER_DEF:
            case SETTER_DEF:
            case CATCH:
            case FUNCTION:
            case VAR:
            case LET:
            case CONST:
            case PARAM_LIST:
              valid = true;
              break;
          }
          break;
        // Casts, variable declarations, exports, and Object literal properties are valid.
        case CAST:
        case VAR:
        case LET:
        case CONST:
        case EXPORT:
        case STRING_KEY:
        case GETTER_DEF:
        case SETTER_DEF:
          valid = true;
          break;
        // Property assignments are valid, if at the root of an expression.
        case ASSIGN: {
          Node lvalue = n.getFirstChild();
          valid = n.getParent().isExprResult()
              && (lvalue.isGetProp()
                  || lvalue.isGetElem()
                  || lvalue.matchesQualifiedName("exports"));
          break;
        }
        case GETPROP:
          valid = n.getParent().isExprResult() && n.isQualifiedName();
          break;
        case CALL:
          valid = info.isDefine();
          break;
        default:
          break;
      }

      if (!valid) {
        reportMisplaced(n, "type", "Type annotations are not allowed here. "
            + "Are you missing parentheses?");
      }
    }
  }

  private void reportMisplaced(Node n, String annotationName, String note) {
    compiler.report(JSError.make(n, MISPLACED_ANNOTATION,
        annotationName, note));
  }

  private void report(Node n, DiagnosticType type, String... arguments) {
    compiler.report(JSError.make(n, type, arguments));
  }

  /**
   * Check that an arrow function is not annotated with {@constructor}.
   */
  private void validateArrowFunction(Node n) {
    if (n.isArrowFunction()) {
      JSDocInfo info = NodeUtil.getBestJSDocInfo(n);
      if (info != null && info.isConstructorOrInterface()) {
        report(n, ARROW_FUNCTION_AS_CONSTRUCTOR);
      }
    }
  }

  /**
   * Check that an arrow function is not annotated with {@constructor}.
   */
  private void validateDefaultValue(Node n, JSDocInfo info) {
    if (n.isDefaultValue() && n.getParent().isParamList() && info != null) {
      JSTypeExpression typeExpr = info.getType();
      if (typeExpr == null) {
        return;
      }
      Node typeNode = typeExpr.getRoot();
      if (typeNode.getType() != Token.EQUALS) {
        report(typeNode, DEFAULT_PARAM_MUST_BE_MARKED_OPTIONAL);
      }
    }
  }

  /**
   * Check that @nosideeeffects annotations are only present in externs.
   */
  private void validateNoSideEffects(Node n, JSDocInfo info) {
    if (info != null && info.isNoSideEffects() && !n.isFromExterns()) {
      reportMisplaced(n, "nosideeffects", "@nosideeffects is only supported in externs.");
    }
  }
}
