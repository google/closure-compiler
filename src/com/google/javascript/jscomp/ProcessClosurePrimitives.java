/*
 * Copyright 2006 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_CLOSURE_CALL_SCOPE_ERROR;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.NULL_ARGUMENT_ERROR;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.TOO_MANY_ARGUMENTS_ERROR;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Performs some Closure-specific simplifications including rewriting goog.base, goog.addDependency.
 *
 * <p>Records forwardDeclared names in the internal compiler state.
 */
class ProcessClosurePrimitives extends AbstractPostOrderCallback implements CompilerPass {

  static final DiagnosticType EXPECTED_OBJECTLIT_ERROR = DiagnosticType.error(
      "JSC_EXPECTED_OBJECTLIT_ERROR",
      "method \"{0}\" expected an object literal argument");

  static final DiagnosticType EXPECTED_STRING_ERROR =
      DiagnosticType.error(
          "JSC_EXPECTED_STRING_ERROR", "method \"{0}\" expected a string argument");

  static final DiagnosticType INVALID_STYLE_ERROR = DiagnosticType.error(
      "JSC_INVALID_CSS_NAME_MAP_STYLE_ERROR",
      "Invalid CSS name map style {0}");

  static final DiagnosticType WEAK_NAMESPACE_TYPE = DiagnosticType.warning(
      "JSC_WEAK_NAMESPACE_TYPE",
      "Provided symbol declared with type Object. This is rarely useful. "
      + "For more information see "
      + "https://github.com/google/closure-compiler/wiki/A-word-about-the-type-Object");

  static final DiagnosticType CLASS_NAMESPACE_ERROR =
      DiagnosticType.error(
          "JSC_CLASS_NAMESPACE_ERROR",
          "\"{0}\" cannot be both provided and declared as a class. Try var {0} = class '{'...'}'"
              + " (metadata {1})");

  static final DiagnosticType FUNCTION_NAMESPACE_ERROR =
      DiagnosticType.error(
          "JSC_FUNCTION_NAMESPACE_ERROR",
          "\"{0}\" cannot be both provided and declared as a function. (metadata {1})");

  static final DiagnosticType INVALID_PROVIDE_ERROR = DiagnosticType.error(
      "JSC_INVALID_PROVIDE_ERROR",
      "\"{0}\" is not a valid {1} qualified name");

  static final DiagnosticType NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR =
      DiagnosticType.error(
          "JSC_NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR",
      "goog.setCssNameMapping only takes an object literal with string values");

  static final DiagnosticType INVALID_CSS_RENAMING_MAP = DiagnosticType.warning(
      "INVALID_CSS_RENAMING_MAP",
      "Invalid entries in css renaming map: {0}");

  static final DiagnosticType BASE_CLASS_ERROR = DiagnosticType.error(
      "JSC_BASE_CLASS_ERROR",
      "incorrect use of {0}.base: {1}");

  static final DiagnosticType INVALID_FORWARD_DECLARE =
      DiagnosticType.error("JSC_INVALID_FORWARD_DECLARE", "Malformed goog.forwardDeclare");

  static final DiagnosticType CLOSURE_CALL_CANNOT_BE_ALIASED_ERROR =
      DiagnosticType.error(
          "JSC_CLOSURE_CALL_CANNOT_BE_ALIASED_ERROR",
          "Closure primitive method {0} may not be aliased");

  static final DiagnosticType CLOSURE_CALL_CANNOT_BE_ALIASED_OUTSIDE_MODULE_ERROR =
      DiagnosticType.error(
          "JSC_CLOSURE_CALL_CANNOT_BE_ALIASED_ERROR",
          "Closure primitive method {0} may not be aliased  outside a module (ES "
              + "module, CommonJS module, or goog.module)");

  static final DiagnosticType INVALID_RENAME_FUNCTION =
      DiagnosticType.error("JSC_INVALID_RENAME_FUNCTION", "{0} call is invalid: {1}");

  /** The root Closure namespace */
  static final String GOOG = "goog";

  private final AbstractCompiler compiler;

  private final Set<String> knownClosureSubclasses = new HashSet<>();

  private final Set<String> exportedVariables = new HashSet<>();

  private final ImmutableMap<String, ModuleMetadata> closureModules;

  ProcessClosurePrimitives(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.closureModules =
        checkNotNull(compiler.getModuleMetadataMap(), "Need to run GatherModuleMetadata")
            .getModulesByGoogNamespace();
  }

  Set<String> getExportedVariableNames() {
    return exportedVariables;
  }

  @Override
  public void process(Node externs, Node root) {
    // Replace and validate other Closure primitives
    NodeTraversal.traverseRoots(compiler, this, externs, root);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case CALL:
        {
          this.checkGoogFunctions(t, n);
          this.maybeProcessClassBaseCall(n);
          this.checkPropertyRenameCall(n);
        }
        break;

      case FUNCTION:
      case CLASS:
        if (!t.inGlobalHoistScope() || n.isFromExterns()) {
          return;
        }
        if (!NodeUtil.isFunctionDeclaration(n) && !NodeUtil.isClassDeclaration(n)) {
          return;
        }
        String name = n.getFirstChild().getString();
        ModuleMetadata pn = this.closureModules.get(name);
        if (pn != null && pn.isGoogProvide()) {
          compiler.report(
              JSError.make(
                  n,
                  n.isClass() ? CLASS_NAMESPACE_ERROR : FUNCTION_NAMESPACE_ERROR,
                  name,
                  pn.toString()));
        }
        break;

      case EXPR_RESULT:
        if (!n.getFirstChild().isAssign() || !n.getFirstFirstChild().isQualifiedName()) {
          break;
        }
        String lhs = n.getFirstFirstChild().getQualifiedName();
        checkPossibleGoogProvideInit(lhs, n.getFirstChild().getJSDocInfo(), n);
        break;
      case VAR:
      case CONST:
      case LET:
        if (!n.getFirstChild().isName()) {
          break;
        }
        checkPossibleGoogProvideInit(n.getFirstChild().getString(), n.getJSDocInfo(), n);
        break;

      default:
        break;
    }
  }

  private void checkPossibleGoogProvideInit(
      String namespace, @Nullable JSDocInfo info, Node definition) {
    if (info == null || definition.isFromExterns()) {
      return;
    }

    ModuleMetadata metadata = this.closureModules.get(namespace);
    if (metadata == null || !metadata.isGoogProvide()) {
      return;
    }

    // Validate that the namespace is not declared as a generic object type.
    JSTypeExpression expr = info.getType();
    if (expr == null) {
      return;
    }
    Node n = expr.getRoot();
    if (n.getToken() == Token.BANG) {
      n = n.getFirstChild();
    }
    if (n.isStringLit()
        && !n.hasChildren() // templated object types are ok.
        && n.getString().equals("Object")) {
      compiler.report(JSError.make(definition, WEAK_NAMESPACE_TYPE));
    }
  }

  private void checkGoogFunctions(NodeTraversal t, Node call) {
    Node callee = call.getFirstChild();
    if (!callee.isGetProp()) {
      return;
    }

    Node receiver = callee.getFirstChild();
    if (!receiver.isName() || !receiver.getString().equals(GOOG)) {
      return;
    }

    // For the sake of simplicity, we report code changes
    // when we see a provides/requires, and don't worry about
    // reporting the change when we actually do the replacement.
    String methodName = callee.getString();
    Node arg = callee.getNext();
    switch (methodName) {
      case "inherits":
        // Note: inherits is allowed in local scope
        processInheritsCall(call);
        break;
      case "exportSymbol":
        // Note: exportSymbol is allowed in local scope
        if (arg.isStringLit()) {
          String argString = arg.getString();
          int dot = argString.indexOf('.');
          if (dot == -1) {
            exportedVariables.add(argString);
          } else {
            exportedVariables.add(argString.substring(0, dot));
          }
        }
        break;
      case "addDependency":
        if (validateUnaliasablePrimitiveCall(t, call, methodName)) {
          processAddDependency(call);
        }
        break;
      case "setCssNameMapping":
        processSetCssNameMapping(call, call.getParent());
        break;
      case "forwardDeclare":
        if (validatePrimitiveCallWithMessage(
            t,
            call,
            methodName,
            ProcessClosurePrimitives.CLOSURE_CALL_CANNOT_BE_ALIASED_OUTSIDE_MODULE_ERROR)) {
          processForwardDeclare(call);
        }
        break;
      default: // fall out
    }
  }

  /**
   * Verifies that a) the call is in the top level of a file and b) the return value is unused
   *
   * <p>This method is for primitives that never return a value.
   */
  private boolean validateUnaliasablePrimitiveCall(NodeTraversal t, Node n, String methodName) {
    return validatePrimitiveCallWithMessage(t, n, methodName, CLOSURE_CALL_CANNOT_BE_ALIASED_ERROR);
  }

  /**
   * @param methodName list of primitive types classed together with this one
   * @param invalidAliasingError which DiagnosticType to emit if this call is aliased. this depends
   *     on whether the primitive is sometimes aliasiable in a module or never aliasable.
   */
  private boolean validatePrimitiveCallWithMessage(
      NodeTraversal t, Node n, String methodName, DiagnosticType invalidAliasingError) {
    // Ignore invalid primitives if we didn't strip module sugar.
    if (compiler.getOptions().shouldPreserveGoogModule()) {
      return true;
    }

    if (!t.inGlobalHoistScope() && !t.inModuleScope()) {
      compiler.report(JSError.make(n, INVALID_CLOSURE_CALL_SCOPE_ERROR));
      return false;
    } else if (!n.getParent().isExprResult() && !t.inModuleScope()) {
      // If the call is in the global hoist scope, but the result is used
      compiler.report(JSError.make(n, invalidAliasingError, GOOG + "." + methodName));
      return false;
    }
    return true;
  }

  private void maybeProcessClassBaseCall(Node n) {
    // Two things must hold for every base call:
    // 1) We must be calling it on "this".
    // 2) We must be calling it on a prototype method of the same name as
    //    the one we're in, OR we must be calling it from a constructor.
    // If both of those things are true, then we can rewrite:
    // <pre>
    // function Foo() {
    //   Foo.base(this);
    // }
    // goog.inherits(Foo, BaseFoo);
    // Foo.prototype.bar = function() {
    //   Foo.base(this, 'bar', 1);
    // };
    // </pre>
    // as the easy-to-optimize:
    // <pre>
    // function Foo() {
    //   BaseFoo.call(this);
    // }
    // goog.inherits(Foo, BaseFoo);
    // Foo.prototype.bar = function() {
    //   Foo.superClass_.bar.call(this, 1);
    // };
    //
    // Most of the logic here is just to make sure the AST's
    // structure is what we expect it to be.

    Node callTarget = n.getFirstChild();
    if (!callTarget.isGetProp() || !callTarget.getString().equals("base")) {
      return;
    }

    Node baseContainerNode = callTarget.getFirstChild();
    if (!baseContainerNode.isUnscopedQualifiedName()) {
      // Some unknown "base" method.
      return;
    }
    String baseContainer = callTarget.getFirstChild().getQualifiedName();

    Node enclosingFnNameNode = getEnclosingDeclNameNode(n);
    if (enclosingFnNameNode == null || !enclosingFnNameNode.isUnscopedQualifiedName()) {
      // some unknown container method or a MEMBER_FUNCTION_DEF.
      if (knownClosureSubclasses.contains(baseContainer)) {
        reportBadBaseMethodUse(n, baseContainer, "Could not find enclosing method.");
      } else if (baseUsedInClass(n)) {
        Node clazz = NodeUtil.getEnclosingClass(n);
        // TODO(lharker): this check ignores class expressions like "foo.Bar = class extends X {}"
        if ((clazz.getFirstChild().isName()
                && clazz.getFirstChild().getString().equals(baseContainer))
            || (clazz.getSecondChild().isName()
                && clazz.getSecondChild().getString().equals(baseContainer))) {
          reportBadBaseMethodUse(
              n,
              clazz.getFirstChild().getString(),
              "base method is not allowed in ES6 class. Use super instead.");
        }
      }
      return;
    }

    if (baseUsedInClass(n)) {
      Node clazz = NodeUtil.getEnclosingClass(n);
      String name = NodeUtil.getBestLValueName(clazz);
      reportBadBaseMethodUse(
          n, name, "base method is not allowed in ES6 class. Use super instead.");
      return;
    }

    String enclosingQname = enclosingFnNameNode.getQualifiedName();
    if (!enclosingQname.contains(".prototype.")) {
      rewriteBaseCallInConstructor(enclosingQname, baseContainer, n, enclosingFnNameNode);
    } else {
      rewriteBaseCallInMethod(enclosingQname, baseContainer, n, enclosingFnNameNode);
    }
  }

  private void rewriteBaseCallInConstructor(
      String enclosingQname, String baseContainer, Node n, Node enclosingFnNameNode) {
    // Check if this is some other "base" method.
    if (!enclosingQname.equals(baseContainer)) {
      // Report misuse of "base" methods from other known classes.
      if (knownClosureSubclasses.contains(baseContainer)) {
        reportBadBaseMethodUse(
            n, baseContainer, "Must be used within " + baseContainer + " methods");
      }
      return;
    }

    // Determine if this is a class with a "base" method created by
    // goog.inherits.
    Node enclosingParent = enclosingFnNameNode.getParent();
    Node baseClassNode =
        findGoogInheritsCall(
            enclosingParent.isAssign() ? enclosingParent.getParent() : enclosingParent);
    if (baseClassNode == null) {
      // If there is no "goog.inherits", this might be some other "base" method.
      return;
    }

    // This is the expected method, validate its parameters.
    Node callee = n.getFirstChild();
    Node thisArg = callee.getNext();
    if (thisArg == null || !thisArg.isThis()) {
      reportBadBaseMethodUse(n, baseContainer, "First argument must be 'this'.");
      return;
    }

    // Handle methods.
    Node methodNameNode = thisArg.getNext();
    if (methodNameNode == null
        || !methodNameNode.isStringLit()
        || !methodNameNode.getString().equals("constructor")) {
      reportBadBaseMethodUse(n, baseContainer, "Second argument must be 'constructor'.");
      return;
    }

    // We're good to go.
    callee.replaceWith(
        NodeUtil.newQName(
            compiler,
            baseClassNode.getQualifiedName() + ".call",
            callee,
            enclosingQname + ".base"));
    methodNameNode.detach();
    compiler.reportChangeToEnclosingScope(n);
  }

  private void rewriteBaseCallInMethod(
      String enclosingQname, String baseContainer, Node n, Node enclosingFnNameNode) {
    if (!knownClosureSubclasses.contains(baseContainer)) {
      // Can't determine if this is a known "class" that has a known "base" method.
      return;
    }

    boolean misuseOfBase =
        !enclosingFnNameNode.getFirstFirstChild().matchesQualifiedName(baseContainer);
    if (misuseOfBase) {
      // Report misuse of "base" methods from other known classes.
      reportBadBaseMethodUse(n, baseContainer, "Must be used within " + baseContainer + " methods");
      return;
    }

    // The super class is known.
    Node callee = n.getFirstChild();
    Node thisArg = callee.getNext();
    if (thisArg == null || !thisArg.isThis()) {
      reportBadBaseMethodUse(n, baseContainer, "First argument must be 'this'.");
      return;
    }

    // Handle methods.
    Node methodNameNode = thisArg.getNext();
    if (methodNameNode == null || !methodNameNode.isStringLit()) {
      reportBadBaseMethodUse(n, baseContainer, "Second argument must name a method.");
      return;
    }

    String methodName = methodNameNode.getString();
    String ending = ".prototype." + methodName;
    if (enclosingQname == null || !enclosingQname.endsWith(ending)) {
      reportBadBaseMethodUse(n, baseContainer, "Enclosing method does not match " + methodName);
      return;
    }

    // We're good to go.
    Node className = enclosingFnNameNode.getFirstFirstChild();
    callee.replaceWith(
        NodeUtil.newQName(
            compiler,
            className.getQualifiedName() + ".superClass_." + methodName + ".call",
            callee,
            enclosingQname + ".base"));
    methodNameNode.detach();
    compiler.reportChangeToEnclosingScope(n);
  }

  private static Node findGoogInheritsCall(Node ctorDeclarationNode) {
    Node maybeInheritsExpr = ctorDeclarationNode.getNext();

    while (maybeInheritsExpr != null
        && (maybeInheritsExpr.isEmpty() || NodeUtil.isExprAssign(maybeInheritsExpr))) {
      // Skip empty nodes (e.g. those created by an unnecessary semicolon) and potential aliases,
      // e.g. "exports = Ctor;" in a goog.module.
      maybeInheritsExpr = maybeInheritsExpr.getNext();
    }
    Node baseClassNode = null;
    if (maybeInheritsExpr != null && NodeUtil.isExprCall(maybeInheritsExpr)) {
      Node callNode = maybeInheritsExpr.getFirstChild();
      if (callNode.getFirstChild().matchesQualifiedName("goog.inherits")
          && callNode.getLastChild().isQualifiedName()) {
        baseClassNode = callNode.getLastChild();
      }
    }
    return baseClassNode;
  }

  /**
   * Processes the goog.inherits call.
   */
  private void processInheritsCall(Node n) {
    if (n.hasXChildren(3)) {
      Node subClass = n.getSecondChild();
      Node superClass = subClass.getNext();
      if (subClass.isUnscopedQualifiedName() && superClass.isUnscopedQualifiedName()) {
        knownClosureSubclasses.add(subClass.getQualifiedName());
      }
    }
  }

  /**
   * Returns the qualified name node of the function whose scope we're in,
   * or null if it cannot be found.
   */
  private static Node getEnclosingDeclNameNode(Node n) {
    Node fn = NodeUtil.getEnclosingFunction(n);
    return fn == null ? null : NodeUtil.getNameNode(fn);
  }

  /** Verify if goog.base call is used in a class */
  private static boolean baseUsedInClass(Node n) {
    for (Node curr = n; curr != null; curr = curr.getParent()){
      if (curr.isClassMembers()) {
        return true;
      }
    }
    return false;
  }

  /** Reports an incorrect use of super-method calling. */
  private void reportBadBaseMethodUse(Node n, String className, String extraMessage) {
    compiler.report(JSError.make(n, BASE_CLASS_ERROR, className, extraMessage));
  }

  /**
   * Processes a call to goog.setCssNameMapping(). Either the argument to goog.setCssNameMapping()
   * is valid, in which case it will be used to create a CssRenamingMap for the compiler of this
   * CompilerPass, or it is invalid and a JSCompiler error will be reported.
   *
   * @see #visit(NodeTraversal, Node, Node)
   */
  private void processSetCssNameMapping(Node n, Node parent) {
    Node left = n.getFirstChild();
    Node arg = left.getNext();
    if (verifySetCssNameMapping(left, arg)) {
      // Translate OBJECTLIT into SubstitutionMap. All keys and
      // values must be strings, or an error will be thrown.
      final Map<String, String> cssNames = new HashMap<>();

      for (Node key = arg.getFirstChild(); key != null;
          key = key.getNext()) {
        Node value = key.getFirstChild();
        if (!key.isStringKey() || value == null || !value.isStringLit()) {
          compiler.report(JSError.make(n, NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR));
          return;
        }
        cssNames.put(key.getString(), value.getString());
      }

      String styleStr = "BY_PART";
      if (arg.getNext() != null) {
        styleStr = arg.getNext().getString();
      }

      final CssRenamingMap.Style style;
      try {
        style = CssRenamingMap.Style.valueOf(styleStr);
      } catch (IllegalArgumentException e) {
        compiler.report(JSError.make(n, INVALID_STYLE_ERROR, styleStr));
        return;
      }

      if (style == CssRenamingMap.Style.BY_PART) {
        // Make sure that no keys contain -'s
        List<String> errors = new ArrayList<>();
        for (String key : cssNames.keySet()) {
          if (key.contains("-")) {
            errors.add(key);
          }
        }
        if (!errors.isEmpty()) {
          compiler.report(JSError.make(n, INVALID_CSS_RENAMING_MAP, errors.toString()));
        }
      } else if (style == CssRenamingMap.Style.BY_WHOLE) {
        // Verifying things is a lot trickier here. We just do a quick
        // n^2 check over the map which makes sure that if "a-b" in
        // the map, then map(a-b) = map(a)-map(b).
        // To speed things up, only consider cases where len(b) <= 10
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, String> b : cssNames.entrySet()) {
          if (b.getKey().length() > 10) {
            continue;
          }
          for (Map.Entry<String, String> a : cssNames.entrySet()) {
            String combined = cssNames.get(a.getKey() + "-" + b.getKey());
            if (combined != null && !combined.equals(a.getValue() + "-" + b.getValue())) {
              errors.add("map(" + a.getKey() + "-" + b.getKey() + ") != map("
                  + a.getKey() + ")-map(" + b.getKey() + ")");
            }
          }
        }
        if (!errors.isEmpty()) {
          compiler.report(JSError.make(n, INVALID_CSS_RENAMING_MAP, errors.toString()));
        }
      }

      CssRenamingMap cssRenamingMap = new CssRenamingMap() {
        @Override
        public String get(String value) {
          if (cssNames.containsKey(value)) {
            return cssNames.get(value);
          } else {
            return value;
          }
        }

        @Override
        public CssRenamingMap.Style getStyle() {
          return style;
        }
      };
      compiler.setCssRenamingMap(cssRenamingMap);
      compiler.reportChangeToEnclosingScope(parent);
      parent.detach();
    }
  }

  /** Process a goog.addDependency() call and record any forward declarations. */
  private void processAddDependency(Node n) {
    CodingConvention convention = compiler.getCodingConvention();
    List<String> typeDecls =
        convention.identifyTypeDeclarationCall(n);

    // TODO(nnaze): Use of addDependency() should someday cause a warning
    // as we migrate users to explicit goog.forwardDeclare() calls.
    if (typeDecls != null) {
      for (String typeDecl : typeDecls) {
        compiler.forwardDeclareType(typeDecl);
      }
    }

    // We can't modify parent, so just create a node that will
    // get compiled out.
    Node emptyNode = IR.number(0);
    n.replaceWith(emptyNode);
    compiler.reportChangeToEnclosingScope(emptyNode);
  }

  /** Process a goog.forwardDeclare() call and record the specified forward declaration. */
  private void processForwardDeclare(Node n) {
    if (!n.getParent().isExprResult()) {
      //  Ignore "const Foo = goog.forwardDeclare('my.Foo');". It's legal but does not actually
      // forward declare the type 'my.Foo'.
      return;
    }
    CodingConvention convention = compiler.getCodingConvention();

    String typeDeclaration = null;
    try {
      typeDeclaration = Iterables.getOnlyElement(
          convention.identifyTypeDeclarationCall(n));
    } catch (NullPointerException | NoSuchElementException | IllegalArgumentException e) {
      compiler.report(
          JSError.make(
              n,
              INVALID_FORWARD_DECLARE,
              "A single type could not identified for the goog.forwardDeclare statement"));
    }

    if (typeDeclaration != null) {
      compiler.forwardDeclareType(typeDeclaration);
    }
  }

  /**
   * Verifies that setCssNameMapping is called with the correct methods.
   *
   * @return Whether the arguments checked out okay
   */
  private boolean verifySetCssNameMapping(Node methodName, Node firstArg) {
    DiagnosticType diagnostic = null;
    if (firstArg == null) {
      diagnostic = NULL_ARGUMENT_ERROR;
    } else if (!firstArg.isObjectLit()) {
      diagnostic = EXPECTED_OBJECTLIT_ERROR;
    } else if (firstArg.getNext() != null) {
      Node secondArg = firstArg.getNext();
      if (!secondArg.isStringLit()) {
        diagnostic = EXPECTED_STRING_ERROR;
      } else if (secondArg.getNext() != null) {
        diagnostic = TOO_MANY_ARGUMENTS_ERROR;
      }
    }
    if (diagnostic != null) {
      compiler.report(JSError.make(methodName, diagnostic, methodName.getQualifiedName()));
      return false;
    }
    return true;
  }

  private void checkPropertyRenameCall(Node call) {
    Node callee = call.getFirstChild();
    // TODO(b/193038601): make this work when the callee is a module import
    if (!compiler.getCodingConvention().isPropertyRenameFunction(callee)) {
      return;
    }
    String calleeName = callee.getQualifiedName();

    switch (call.getChildCount() - 1) {
      case 1:
      case 2:
        break;
      default:
        compiler.report(
            JSError.make(
                call,
                INVALID_RENAME_FUNCTION,
                calleeName,
                "Must be called with 1 or 2 arguments."));
    }

    Node propName = callee.getNext();
    if (propName == null || !propName.isStringLit()) {
      compiler.report(
          JSError.make(
              call,
              INVALID_RENAME_FUNCTION,
              calleeName,
              "The first argument must be a string literal."));
    } else if (propName.getString().contains(".")) {
      compiler.report(
          JSError.make(
              call,
              INVALID_RENAME_FUNCTION,
              calleeName,
              "The first argument must not be a property path."));
    }
  }
}
