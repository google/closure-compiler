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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Checks for misplaced, misused or deprecated JSDoc annotations.
 *
 * @author chadkillingsworth@gmail.com (Chad Killingsworth)
 */
final class CheckJSDoc extends AbstractPostOrderCallback implements HotSwapCompilerPass {

  public static final DiagnosticType MISPLACED_MSG_ANNOTATION =
      DiagnosticType.disabled(
          "JSC_MISPLACED_MSG_ANNOTATION",
          "Misplaced message annotation. @desc, @hidden, and @meaning annotations should only "
              + "be on message nodes.\nMessage constants must be prefixed with 'MSG_'.");

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

  public static final DiagnosticType INVALID_NO_SIDE_EFFECT_ANNOTATION =
      DiagnosticType.error(
          "JSC_INVALID_NO_SIDE_EFFECT_ANNOTATION",
          "@nosideeffects may only appear in externs files.");

  public static final DiagnosticType INVALID_MODIFIES_ANNOTATION =
      DiagnosticType.error(
          "JSC_INVALID_MODIFIES_ANNOTATION", "@modifies may only appear in externs files.");

  public static final DiagnosticType INVALID_DEFINE_ON_LET =
      DiagnosticType.error(
          "JSC_INVALID_DEFINE_ON_LET",
          "variables annotated with @define may only be declared with VARs, ASSIGNs, or CONSTs");

  public static final DiagnosticType MISPLACED_SUPPRESS =
      DiagnosticType.warning(
          "JSC_MISPLACED_SUPPRESS",
          "@suppress annotation not allowed here. See"
              + " https://github.com/google/closure-compiler/wiki/@suppress-annotations");

  private final AbstractCompiler compiler;
  private boolean inExterns;

  CheckJSDoc(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    inExterns = true;
    NodeTraversal.traverse(compiler, externs, this);
    inExterns = false;
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
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
    validateDefaultValue(n);
    validateTemplates(n, info);
    validateTypedefs(n, info);
    validateNoSideEffects(n, info);
    validateAbstractJsDoc(n, info);
    validateDefinesDeclaration(n, info);
    validateSuppress(n, info);
    validateImplicitCast(n, info);
  }

  private void validateSuppress(Node n, JSDocInfo info) {
    if (info == null || info.getSuppressions().isEmpty()) {
      return;
    }
    switch (n.getToken()) {
      case FUNCTION:
      case CLASS:
      case VAR:
      case LET:
      case CONST:
      case SCRIPT:
      case MEMBER_FUNCTION_DEF:
      case GETTER_DEF:
      case SETTER_DEF:
        // Suppressions are always valid here.
        return;

      case COMPUTED_PROP:
        if (n.getLastChild().isFunction()) {
          return; // Suppressions are valid on computed properties that declare functions.
        }
        break;

      case STRING_KEY:
        if (n.getParent().isObjectLit()) {
          return;
        }
        break;

      case ASSIGN:
      case GETPROP:
        if (n.getParent().isExprResult()) {
          return;
        }
        break;

      case CALL:
        // TODO(blickly): Stop ignoring no-op extraProvide suppression.
        // We don't actually support extraProvide, but if we did, it would go on a CALL.
        if (containsOnlySuppressionFor(info, "extraRequire")
            || containsOnlySuppressionFor(info, "extraProvide")) {
          return;
        }
        break;

      case WITH:
        if (containsOnlySuppressionFor(info, "with")) {
          return;
        }
        break;

      default:
        break;
    }
    if (containsOnlySuppressionFor(info, "missingRequire")) {
      return;
    }
    compiler.report(JSError.make(n, MISPLACED_SUPPRESS));
  }

  private static boolean containsOnlySuppressionFor(JSDocInfo jsdoc, String allowedSuppression) {
    Set<String> suppressions = jsdoc.getSuppressions();
    return suppressions.size() == 1
        && Iterables.getOnlyElement(suppressions).equals(allowedSuppression);
  }

  private void validateTypedefs(Node n, JSDocInfo info) {
    if (info != null && info.hasTypedefType() && isClassDecl(n)) {
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
    if (NodeUtil.isNameDeclaration(n)
        && n.getFirstFirstChild() != null
        && n.getFirstFirstChild().isFunction()) {
      return n.getFirstFirstChild();
    }

    if (n.isAssign() && n.getFirstChild().isQualifiedName() && n.getLastChild().isFunction()) {
      return n.getLastChild();
    }

    if (n.isStringKey() && n.getGrandparent() != null
        && ClosureRewriteClass.isGoogDefineClass(n.getGrandparent())
        && n.getFirstChild().isFunction()) {
      return n.getFirstChild();
    }

    if (n.isGetterDef() || n.isSetterDef()) {
      return n.getFirstChild();
    }

    return null;
  }

  private boolean isClassDecl(Node n) {
    return isClass(n)
        || (n.isAssign() && isClass(n.getLastChild()))
        || (NodeUtil.isNameDeclaration(n) && isNameInitializeWithClass(n.getFirstChild()))
        || isNameInitializeWithClass(n);
  }

  private boolean isNameInitializeWithClass(Node n) {
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
    if (isClassDecl(n)) {
      return;
    }

    Node functionNode = getFunctionDecl(n);

    if (functionNode == null) {
      // @abstract annotation on a non-function
      report(
          n,
          MISPLACED_ANNOTATION,
          "@abstract",
          "only functions or non-static methods can be abstract");
      return;
    }

    if (!info.isConstructor() && NodeUtil.getFunctionBody(functionNode).hasChildren()) {
      // @abstract annotation on a function with a non-empty body
      report(n, MISPLACED_ANNOTATION, "@abstract",
          "function with a non-empty body cannot be abstract");
      return;
    }

    if ((n.isMemberFunctionDef() || n.isStringKey()) && "constructor".equals(n.getString())) {
      // @abstract annotation on an ES6 or goog.defineClass constructor
      report(n, MISPLACED_ANNOTATION, "@abstract", "constructors cannot be abstract");
      return;
    }

    if (!info.isConstructor()
        && !n.isMemberFunctionDef()
        && !n.isStringKey()
        && !n.isGetterDef()
        && !n.isSetterDef()
        && !NodeUtil.isPrototypeMethod(functionNode)) {
      // @abstract annotation on a non-method (or static method) in ES5
      report(
          n,
          MISPLACED_ANNOTATION,
          "@abstract",
          "only functions or non-static methods can be abstract");
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
      switch (n.getToken()) {
        case FUNCTION:
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
        case VAR:
        case LET:
        case CONST:
        case ASSIGN: {
          Node lhs = n.getFirstChild();
          Node rhs = NodeUtil.getRValueOfLValue(lhs);
          if (rhs != null && isClass(rhs) && !info.isConstructor()) {
            break;
          }
          // TODO(tbreisacher): Check that the RHS of the assignment is a
          // function. Note that it can be a FUNCTION node, but it can also be
          // a call to goog.abstractMethod, goog.functions.constant, etc.
          return;
        }
        default:
          break;
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
      switch (n.getToken()) {
        case ASSIGN:
        case VAR:
        case LET:
        case CONST:
          descOkay = isValidMsgName(n.getFirstChild());
          break;
        case STRING_KEY:
          descOkay = isValidMsgName(n);
          break;
        case GETPROP:
          if (n.isFromExterns() && n.isQualifiedName()) {
            descOkay = isValidMsgName(n);
          }
          break;
        default:
          break;
      }
      if (!descOkay) {
        report(n, MISPLACED_MSG_ANNOTATION);
      }
    }
  }

  /** Returns whether of not the given name is valid target for the result of goog.getMsg */
  private boolean isValidMsgName(Node nameNode) {
    if (nameNode.isName() || nameNode.isStringKey()) {
      return nameNode.getString().startsWith("MSG_");
    } else if (nameNode.isQualifiedName()) {
      return nameNode.getLastChild().getString().startsWith("MSG_");
    } else {
      return false;
    }
  }

  /**
   * Check that JSDoc with a {@code @type} annotation is in a valid place.
   */
  private void validateTypeAnnotations(Node n, JSDocInfo info) {
    if (info != null && info.hasType()) {
      boolean valid = false;
      switch (n.getToken()) {
        // Function declarations are valid
        case FUNCTION:
          valid = NodeUtil.isFunctionDeclaration(n);
          break;
        // Object literal properties, catch declarations and variable
        // initializers are valid.
        case NAME:
          valid = isTypeAnnotationAllowedForName(n);
          break;
        case ARRAY_PATTERN:
        case OBJECT_PATTERN:
          // allow JSDoc like
          //   function f(/** !Object */ {x}) {}
          //   function f(/** !Array */ [x]) {}
          valid = n.getParent().isParamList();
          break;
        // Casts, exports, and Object literal properties are valid.
        case CAST:
        case EXPORT:
        case STRING_KEY:
        case GETTER_DEF:
        case SETTER_DEF:
          valid = true;
          break;
        // Declarations are valid iff they only contain simple names
        //   /** @type {number} */ var x = 3; // ok
        //   /** @type {number} */ var {x} = obj; // forbidden
        case VAR:
        case LET:
        case CONST:
          valid = !NodeUtil.isDestructuringDeclaration(n);
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

  /**
   * Is it valid to have a type annotation on the given NAME node?
   */
  private boolean isTypeAnnotationAllowedForName(Node n) {
    checkState(n.isName(), n);
    // Only allow type annotations on nodes used as an lvalue.
    if (!NodeUtil.isLValue(n)) {
      return false;
    }
    // Don't allow JSDoc on a name in an assignment. Simple names should only have JSDoc on them
    // when originally declared.
    Node rootTarget = NodeUtil.getRootTarget(n);
    return !NodeUtil.isLhsOfAssign(rootTarget);
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
   * Check that a parameter with a default value is marked as optional.
   * TODO(bradfordcsmith): This is redundant. We shouldn't require it.
   */
  private void validateDefaultValue(Node n) {
    if (n.isDefaultValue() && n.getParent().isParamList()) {
      Node targetNode = n.getFirstChild();
      JSDocInfo info = targetNode.getJSDocInfo();
      if (info == null) {
        return;
      }

      JSTypeExpression typeExpr = info.getType();
      if (typeExpr == null) {
        return;
      }

      Node typeNode = typeExpr.getRoot();
      if (typeNode.getToken() != Token.EQUALS) {
        report(typeNode, DEFAULT_PARAM_MUST_BE_MARKED_OPTIONAL);
      }
    }
  }

  /**
   * Check that @nosideeeffects annotations are only present in externs.
   */
  private void validateNoSideEffects(Node n, JSDocInfo info) {
    // Cannot have @modifies or @nosideeffects in regular (non externs) js. Report errors.
    if (info == null) {
      return;
    }

    if (n.isFromExterns()) {
      return;
    }

    if (info.hasSideEffectsArgumentsAnnotation() || info.modifiesThis()) {
      report(n, INVALID_MODIFIES_ANNOTATION);
    }
    if (info.isNoSideEffects()) {
      report(n, INVALID_NO_SIDE_EFFECT_ANNOTATION);
    }
  }

  /**
   * Check that a let declaration is not used with {@defines}
   */
  private void validateDefinesDeclaration(Node n, JSDocInfo info) {
    if (info != null && info.isDefine() && n.isLet()) {
      report(n, INVALID_DEFINE_ON_LET);
    }
  }

  /** Checks that an @implicitCast annotation is in the externs */
  private void validateImplicitCast(Node n, JSDocInfo info) {
    if (!inExterns && info != null && info.isImplicitCast()) {
      report(n, TypeCheck.ILLEGAL_IMPLICIT_CAST);
    }
  }
}
