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

import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_CLOSURE_CALL_SCOPE_ERROR;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
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
 * <p>Adds forwardDeclared and goog.defined names to the compiler.
 */
class ProcessClosurePrimitives extends AbstractPostOrderCallback implements HotSwapCompilerPass {

  static final DiagnosticType NULL_ARGUMENT_ERROR = DiagnosticType.error(
      "JSC_NULL_ARGUMENT_ERROR",
      "method \"{0}\" called without an argument");

  static final DiagnosticType EXPECTED_OBJECTLIT_ERROR = DiagnosticType.error(
      "JSC_EXPECTED_OBJECTLIT_ERROR",
      "method \"{0}\" expected an object literal argument");

  static final DiagnosticType EXPECTED_STRING_ERROR =
      DiagnosticType.error(
          "JSC_EXPECTED_STRING_ERROR", "method \"{0}\" expected a string argument");

  static final DiagnosticType INVALID_ARGUMENT_ERROR = DiagnosticType.error(
      "JSC_INVALID_ARGUMENT_ERROR",
      "method \"{0}\" called with invalid argument");

  static final DiagnosticType INVALID_STYLE_ERROR = DiagnosticType.error(
      "JSC_INVALID_CSS_NAME_MAP_STYLE_ERROR",
      "Invalid CSS name map style {0}");

  static final DiagnosticType TOO_MANY_ARGUMENTS_ERROR = DiagnosticType.error(
      "JSC_TOO_MANY_ARGUMENTS_ERROR",
      "method \"{0}\" called with more than one argument");

  static final DiagnosticType WEAK_NAMESPACE_TYPE = DiagnosticType.warning(
      "JSC_WEAK_NAMESPACE_TYPE",
      "Provided symbol declared with type Object. This is rarely useful. "
      + "For more information see "
      + "https://github.com/google/closure-compiler/wiki/A-word-about-the-type-Object");

  static final DiagnosticType CLASS_NAMESPACE_ERROR = DiagnosticType.error(
      "JSC_CLASS_NAMESPACE_ERROR",
    "\"{0}\" cannot be both provided and declared as a class. Try var {0} = class '{'...'}'");

  static final DiagnosticType FUNCTION_NAMESPACE_ERROR = DiagnosticType.error(
      "JSC_FUNCTION_NAMESPACE_ERROR",
      "\"{0}\" cannot be both provided and declared as a function");

  static final DiagnosticType INVALID_PROVIDE_ERROR = DiagnosticType.error(
      "JSC_INVALID_PROVIDE_ERROR",
      "\"{0}\" is not a valid {1} qualified name");

  static final DiagnosticType INVALID_DEFINE_NAME_ERROR = DiagnosticType.error(
      "JSC_INVALID_DEFINE_NAME_ERROR",
      "\"{0}\" is not a valid JS identifier name");

  static final DiagnosticType MISSING_DEFINE_ANNOTATION = DiagnosticType.error(
      "JSC_INVALID_MISSING_DEFINE_ANNOTATION",
      "Missing @define annotation");

  static final DiagnosticType XMODULE_REQUIRE_ERROR =
      DiagnosticType.warning(
          "JSC_XMODULE_REQUIRE_ERROR",
          "namespace \"{0}\" is required in module {2} but provided in module {1}."
              + " Is module {2} missing a dependency on module {1}?");

  static final DiagnosticType NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR =
      DiagnosticType.error(
          "JSC_NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR",
      "goog.setCssNameMapping only takes an object literal with string values");

  static final DiagnosticType INVALID_CSS_RENAMING_MAP = DiagnosticType.warning(
      "INVALID_CSS_RENAMING_MAP",
      "Invalid entries in css renaming map: {0}");

  static final DiagnosticType GOOG_BASE_CLASS_ERROR = DiagnosticType.error(
      "JSC_GOOG_BASE_CLASS_ERROR",
      "incorrect use of goog.base: {0}");

  static final DiagnosticType BASE_CLASS_ERROR = DiagnosticType.error(
      "JSC_BASE_CLASS_ERROR",
      "incorrect use of {0}.base: {1}");

  static final DiagnosticType CLOSURE_DEFINES_ERROR = DiagnosticType.error(
      "JSC_CLOSURE_DEFINES_ERROR",
      "Invalid CLOSURE_DEFINES definition");

  static final DiagnosticType DEFINE_CALL_WITHOUT_ASSIGNMENT =
      DiagnosticType.error(
          "JSC_DEFINE_CALL_WITHOUT_ASSIGNMENT",
          "The result of a goog.define call must be assigned as an isolated statement.");

  static final DiagnosticType INVALID_FORWARD_DECLARE = DiagnosticType.error(
      "JSC_INVALID_FORWARD_DECLARE",
      "Malformed goog.forwardDeclaration");

  static final DiagnosticType USE_OF_GOOG_BASE = DiagnosticType.disabled(
      "JSC_USE_OF_GOOG_BASE",
      "goog.base is not compatible with ES5 strict mode.\n"
      + "Please use an alternative.\n"
      + "For EcmaScript classes use the super keyword. For traditional Closure classes,\n"
      + "use the class specific base method instead. For example, for the constructor MyClass:\n"
      + "   MyClass.base(this, ''constructor'')");

  static final DiagnosticType CLOSURE_CALL_CANNOT_BE_ALIASED_ERROR =
      DiagnosticType.error(
          "JSC_CLOSURE_CALL_CANNOT_BE_ALIASED_ERROR",
          "Closure primitive method {0} may not be aliased");

  static final DiagnosticType CLOSURE_CALL_CANNOT_BE_ALIASED_OUTSIDE_MODULE_ERROR =
      DiagnosticType.error(
          "JSC_CLOSURE_CALL_CANNOT_BE_ALIASED_ERROR",
          "Closure primitive method {0} may not be aliased  outside a module (ES "
              + "module, CommonJS module, or goog.module)");

  /** The root Closure namespace */
  static final String GOOG = "goog";

  private final AbstractCompiler compiler;

  private final Set<String> knownClosureSubclasses = new HashSet<>();

  private final Set<String> exportedVariables = new HashSet<>();
  private final PreprocessorSymbolTable preprocessorSymbolTable;
  private final List<Node> defineCalls = new ArrayList<>();

  ProcessClosurePrimitives(
      AbstractCompiler compiler, @Nullable PreprocessorSymbolTable preprocessorSymbolTable) {
    this.compiler = compiler;
    this.preprocessorSymbolTable = preprocessorSymbolTable;
  }

  Set<String> getExportedVariableNames() {
    return exportedVariables;
  }

  @Override
  public void process(Node externs, Node root) {
    // Replace and validate other Closure primitives
    NodeTraversal.traverseRoots(compiler, this, externs, root);

    for (Node n : defineCalls) {
      replaceGoogDefines(n);
    }
  }

  /**
   * @param n
   */
  private void replaceGoogDefines(Node n) {
    Node parent = n.getParent();
    String name = n.getSecondChild().getString();
    Node value = n.getChildAtIndex(2).detach();

    switch (parent.getToken()) {
      case NAME:
        parent.setDefineName(name);
        n.replaceWith(value);
        compiler.reportChangeToEnclosingScope(parent);
        break;
      case ASSIGN:
        checkState(n == parent.getLastChild());
        parent.getFirstChild().setDefineName(name);
        n.replaceWith(value);
        compiler.reportChangeToEnclosingScope(parent);
        break;
      default:
        throw new IllegalStateException("goog.define outside of NAME, or ASSIGN");
    }
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    // TODO(bashir): Implement a real hot-swap version instead and make it fully
    // consistent with the full version.
    this.compiler.process(this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case CALL:
        Node left = n.getFirstChild();
        if (left.isGetProp()) {
          Node name = left.getFirstChild();
          if (name.isName() && GOOG.equals(name.getString())) {
            // For the sake of simplicity, we report code changes
            // when we see a provides/requires, and don't worry about
            // reporting the change when we actually do the replacement.
            String methodName = name.getNext().getString();
            switch (methodName) {
              case "base":
                processBaseClassCall(t, n);
                break;
              case "define":
                processDefineCall(t, n, parent);
                break;
              case "inherits":
                // Note: inherits is allowed in local scope
                processInheritsCall(n);
                break;
              case "exportSymbol":
                // Note: exportSymbol is allowed in local scope
                Node arg = left.getNext();
                if (arg.isString()) {
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
                if (validateUnaliasablePrimitiveCall(t, n, methodName)) {
                  processAddDependency(n, parent);
                }
                break;
              case "setCssNameMapping":
                processSetCssNameMapping(n, parent);
                break;
              case "forwardDeclare":
                if (validatePrimitiveCallWithMessage(
                    t,
                    n,
                    methodName,
                    ProcessClosurePrimitives.CLOSURE_CALL_CANNOT_BE_ALIASED_OUTSIDE_MODULE_ERROR)) {
                  processForwardDeclare(n);
                }
                break;
              default: // fall out
            }
          } else if (left.getLastChild().getString().equals("base")) {
            // maybe an "base" setup by goog.inherits
            maybeProcessClassBaseCall(n);
          }
        }
        break;

      case ASSIGN:
      case NAME:
        if (n.isName() && n.getString().equals("CLOSURE_DEFINES")) {
          handleClosureDefinesValues(n);
        }
        break;

      case GETPROP:
        if (n.getFirstChild().isName()
            && !parent.isCall()
            && !parent.isAssign()
            && n.matchesQualifiedName("goog.base")
            && !n.getSourceFileName().endsWith("goog.js")) {
          reportBadGoogBaseUse(n, "May only be called directly.");
        }
        break;
      default:
        break;
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
    } else if (!n.getParent().isExprResult()
        && !t.inModuleScope()
        && !"goog.define".equals(methodName)) {
      // If the call is in the global hoist scope, but the result is used
      compiler.report(JSError.make(n, invalidAliasingError, GOOG + "." + methodName));
      return false;
    }
    return true;
  }

  private void handleClosureDefinesValues(Node n) {
    // var CLOSURE_DEFINES = {};
    if (NodeUtil.isNameDeclaration(n.getParent())
        && n.hasOneChild()
        && n.getFirstChild().isObjectLit()) {
      HashMap<String, Node> builder = new HashMap<>();
      builder.putAll(compiler.getDefaultDefineValues());
      for (Node c : n.getFirstChild().children()) {
        if (c.isStringKey()
            && c.getFirstChild() != null  // Shorthand assignment
            && isValidDefineValue(c.getFirstChild())) {
          builder.put(c.getString(), c.getFirstChild().cloneTree());
        } else {
          reportBadClosureCommonDefinesDefinition(c);
        }
      }
      compiler.setDefaultDefineValues(ImmutableMap.copyOf(builder));
    }
  }

  static boolean isValidDefineValue(Node val) {
    switch (val.getToken()) {
      case STRING:
      case NUMBER:
      case TRUE:
      case FALSE:
        return true;
      case NEG:
        return val.getFirstChild().isNumber();
      default:
        return false;
    }
  }

  /** Handles a goog.define call. */
  private void processDefineCall(NodeTraversal t, Node n, Node parent) {
    Node left = n.getFirstChild();
    Node args = left.getNext();
    if (verifyDefine(t, parent, left, args)) {
      Node nameNode = args;

      maybeAddNameToSymbolTable(left);
      maybeAddStringToSymbolTable(nameNode);

      this.defineCalls.add(n);
    }
  }

  /** Processes the base class call. */
  private void processBaseClassCall(NodeTraversal t, Node n) {
    // Two things must hold for every goog.base call:
    // 1) We must be calling it on "this".
    // 2) We must be calling it on a prototype method of the same name as
    //    the one we're in, OR we must be calling it from a constructor.
    // If both of those things are true, then we can rewrite:
    // <pre>
    // function Foo() {
    //   goog.base(this);
    // }
    // goog.inherits(Foo, BaseFoo);
    // Foo.prototype.bar = function() {
    //   goog.base(this, 'bar', 1);
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

    // If requested report uses of goog.base.
    t.report(n, USE_OF_GOOG_BASE);

    if (baseUsedInClass(n)){
      reportBadGoogBaseUse(n, "goog.base in ES6 class is not allowed. Use super instead.");
      return;
    }

    Node callee = n.getFirstChild();
    Node thisArg = callee.getNext();
    if (thisArg == null || !thisArg.isThis()) {
      reportBadGoogBaseUse(n, "First argument must be 'this'.");
      return;
    }

    Node enclosingFnNameNode = getEnclosingDeclNameNode(n);
    if (enclosingFnNameNode == null) {
      reportBadGoogBaseUse(n, "Could not find enclosing method.");
      return;
    }

    String enclosingQname = enclosingFnNameNode.getQualifiedName();
    if (!enclosingQname.contains(".prototype.")) {
      // Handle constructors.
      Node enclosingParent = enclosingFnNameNode.getParent();
      Node maybeInheritsExpr =
          (enclosingParent.isAssign() ? enclosingParent.getParent() : enclosingParent).getNext();
      Node baseClassNode = null;
      if (maybeInheritsExpr != null
          && maybeInheritsExpr.isExprResult()
          && maybeInheritsExpr.getFirstChild().isCall()) {
        Node callNode = maybeInheritsExpr.getFirstChild();
        if (callNode.getFirstChild().matchesQualifiedName("goog.inherits")
            && callNode.getLastChild().isQualifiedName()) {
          baseClassNode = callNode.getLastChild();
        }
      }

      if (baseClassNode == null) {
        reportBadGoogBaseUse(n, "Could not find goog.inherits for base class");
        return;
      }

      // We're good to go.
      Node newCallee =
          NodeUtil.newQName(
              compiler, baseClassNode.getQualifiedName() + ".call", callee, "goog.base");
      n.replaceChild(callee, newCallee);
      compiler.reportChangeToEnclosingScope(newCallee);
    } else {
      // Handle methods.
      Node methodNameNode = thisArg.getNext();
      if (methodNameNode == null || !methodNameNode.isString()) {
        reportBadGoogBaseUse(n, "Second argument must name a method.");
        return;
      }

      String methodName = methodNameNode.getString();
      String ending = ".prototype." + methodName;
      if (enclosingQname == null || !enclosingQname.endsWith(ending)) {
        reportBadGoogBaseUse(n, "Enclosing method does not match " + methodName);
        return;
      }

      // We're good to go.
      Node className =
          enclosingFnNameNode.getFirstFirstChild();
      n.replaceChild(
          callee,
          NodeUtil.newQName(
            compiler,
            className.getQualifiedName() + ".superClass_." + methodName + ".call",
            callee, "goog.base"));
      n.removeChild(methodNameNode);
      compiler.reportChangeToEnclosingScope(n);
    }
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
    Node baseContainerNode = callTarget.getFirstChild();
    if (!baseContainerNode.isUnscopedQualifiedName()) {
      // Some unknown "base" method.
      return;
    }
    String baseContainer = callTarget.getFirstChild().getQualifiedName();

    Node enclosingFnNameNode = getEnclosingDeclNameNode(n);
    if (enclosingFnNameNode == null || !enclosingFnNameNode.isUnscopedQualifiedName()) {
      // some unknown container method.
      if (knownClosureSubclasses.contains(baseContainer)) {
        reportBadBaseMethodUse(n, baseContainer, "Could not find enclosing method.");
      } else if (baseUsedInClass(n)) {
        Node clazz = NodeUtil.getEnclosingClass(n);
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
      reportBadGoogBaseUse(n, "goog.base in ES6 class is not allowed. Use super instead.");
      return;
    }

    String enclosingQname = enclosingFnNameNode.getQualifiedName();
    if (!enclosingQname.contains(".prototype.")) {
      // Handle constructors.

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
      Node maybeInheritsExpr =
          (enclosingParent.isAssign() ? enclosingParent.getParent() : enclosingParent).getNext();
      while (maybeInheritsExpr != null && maybeInheritsExpr.isEmpty()) {
        maybeInheritsExpr = maybeInheritsExpr.getNext();
      }
      Node baseClassNode = null;
      if (maybeInheritsExpr != null
          && maybeInheritsExpr.isExprResult()
          && maybeInheritsExpr.getFirstChild().isCall()) {
        Node callNode = maybeInheritsExpr.getFirstChild();
        if (callNode.getFirstChild().matchesQualifiedName("goog.inherits")
            && callNode.getLastChild().isQualifiedName()) {
          baseClassNode = callNode.getLastChild();
        }
      }

      if (baseClassNode == null) {
        // If there is no "goog.inherits", this might be some other "base"
        // method.
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
          || !methodNameNode.isString()
          || !methodNameNode.getString().equals("constructor")) {
        reportBadBaseMethodUse(n, baseContainer, "Second argument must be 'constructor'.");
        return;
      }

      // We're good to go.
      n.replaceChild(
          callee,
          NodeUtil.newQName(
            compiler,
            baseClassNode.getQualifiedName() + ".call",
            callee, enclosingQname + ".base"));
      n.removeChild(methodNameNode);
      compiler.reportChangeToEnclosingScope(n);
    } else {
      if (!knownClosureSubclasses.contains(baseContainer)) {
        // Can't determine if this is a known "class" that has a known "base"
        // method.
        return;
      }

      boolean misuseOfBase = !enclosingFnNameNode.
          getFirstFirstChild().matchesQualifiedName(baseContainer);
      if (misuseOfBase) {
        // Report misuse of "base" methods from other known classes.
        reportBadBaseMethodUse(
            n, baseContainer, "Must be used within " + baseContainer + " methods");
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
      if (methodNameNode == null || !methodNameNode.isString()) {
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
      Node className =
          enclosingFnNameNode.getFirstFirstChild();
      n.replaceChild(
          callee,
          NodeUtil.newQName(
            compiler,
            className.getQualifiedName() + ".superClass_." + methodName + ".call",
            callee, enclosingQname + ".base"));
      n.removeChild(methodNameNode);
      compiler.reportChangeToEnclosingScope(n);
    }
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
  private boolean baseUsedInClass(Node n){
    for (Node curr = n; curr != null; curr = curr.getParent()){
      if (curr.isClassMembers()) {
        return true;
      }
    }
    return false;
  }

  /** Reports an incorrect use of super-method calling. */
  private void reportBadGoogBaseUse(Node n, String extraMessage) {
    compiler.report(JSError.make(n, GOOG_BASE_CLASS_ERROR, extraMessage));
  }

  /** Reports an incorrect use of super-method calling. */
  private void reportBadBaseMethodUse(Node n, String className, String extraMessage) {
    compiler.report(JSError.make(n, BASE_CLASS_ERROR, className, extraMessage));
  }

  /** Reports an incorrect CLOSURE_DEFINES definition. */
  private void reportBadClosureCommonDefinesDefinition(Node n) {
    compiler.report(JSError.make(n, CLOSURE_DEFINES_ERROR));
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
        if (!key.isStringKey()
            || value == null
            || !value.isString()) {
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

  /**
   * Verifies that a goog.define method call has exactly two arguments, with the first a string
   * literal whose contents is a valid JS qualified name. Reports a compile error if it doesn't.
   *
   * @return Whether the argument checked out okay
   */
  private boolean verifyDefine(NodeTraversal t, Node parent, Node methodName, Node args) {
    // Calls to goog.define must be in the global hoist scope.  This is copied from
    // validate(Un)aliasablePrimitiveCall.
    // TODO(sdh): loosen this restriction if the results are assigned?
    if (!compiler.getOptions().shouldPreserveGoogModule()
        && !t.inGlobalHoistScope()
        && !t.inModuleScope()) {
      compiler.report(JSError.make(methodName.getParent(), INVALID_CLOSURE_CALL_SCOPE_ERROR));
      return false;
    }

    // It is an error for goog.define to show up anywhere except immediately after =.
    if (parent.isAssign() && parent.getParent().isExprResult()) {
      parent = parent.getParent();
    } else if (parent.isName() && NodeUtil.isNameDeclaration(parent.getParent())) {
      parent = parent.getParent();
    } else {
      compiler.report(JSError.make(methodName.getParent(), DEFINE_CALL_WITHOUT_ASSIGNMENT));
      return false;
    }

    // Verify first arg
    Node arg = args;
    if (!verifyNotNull(methodName, arg) || !verifyOfType(methodName, arg, Token.STRING)) {
      return false;
    }

    // Verify second arg
    arg = arg.getNext();
    if (!args.isFromExterns()
        && (!verifyNotNull(methodName, arg) || !verifyIsLast(methodName, arg))) {
      return false;
    }

    String name = args.getString();
    if (!NodeUtil.isValidQualifiedName(
        compiler.getOptions().getLanguageIn().toFeatureSet(), name)) {
      compiler.report(JSError.make(args, INVALID_DEFINE_NAME_ERROR, name));
      return false;
    }

    JSDocInfo info = (parent.isExprResult() ? parent.getFirstChild() : parent).getJSDocInfo();
    if (info == null || !info.isDefine()) {
      compiler.report(JSError.make(parent, MISSING_DEFINE_ANNOTATION));
      return false;
    }
    return true;
  }

  /**
   * Process a goog.addDependency() call and record any forward declarations.
   */
  private void processAddDependency(Node n, Node parent) {
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
    parent.replaceChild(n, emptyNode);
    compiler.reportChangeToEnclosingScope(emptyNode);
  }

  /** Process a goog.forwardDeclare() call and record the specified forward declaration. */
  private void processForwardDeclare(Node n) {
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

  /** @return Whether the argument checked out okay */
  private boolean verifyNotNull(Node methodName, Node arg) {
    if (arg == null) {
      compiler.report(JSError.make(methodName, NULL_ARGUMENT_ERROR, methodName.getQualifiedName()));
      return false;
    }
    return true;
  }

  /** @return Whether the argument checked out okay */
  private boolean verifyOfType(Node methodName, Node arg, Token desiredType) {
    if (arg.getToken() != desiredType) {
      compiler.report(
          JSError.make(methodName, INVALID_ARGUMENT_ERROR, methodName.getQualifiedName()));
      return false;
    }
    return true;
  }

  /** @return Whether the argument checked out okay */
  private boolean verifyIsLast(Node methodName, Node arg) {
    if (arg.getNext() != null) {
      compiler.report(
          JSError.make(methodName, TOO_MANY_ARGUMENTS_ERROR, methodName.getQualifiedName()));
      return false;
    }
    return true;
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
      if (!secondArg.isString()) {
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

  /** Add the given qualified name node to the symbol table. */
  private void maybeAddStringToSymbolTable(Node string) {
    if (preprocessorSymbolTable != null) {
      preprocessorSymbolTable.addStringNode(string, compiler);
    }
  }

  /** Add the given qualified name node to the symbol table. */
  private void maybeAddNameToSymbolTable(Node name) {
    if (preprocessorSymbolTable != null) {
      preprocessorSymbolTable.addReference(name);
    }
  }
}
