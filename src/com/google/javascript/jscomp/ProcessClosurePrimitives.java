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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_CLOSURE_CALL_SCOPE_ERROR;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.parsing.JsDocInfoParser;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Replaces goog.provide calls, removes goog.{require,requireType} calls, verifies that each
 * goog.{require,requireType} has a corresponding goog.provide, and performs some Closure-pecific
 * simplifications.
 *
 * @author chrisn@google.com (Chris Nokleberg)
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

  static final DiagnosticType DUPLICATE_NAMESPACE_ERROR =
      DiagnosticType.error(
          "JSC_DUPLICATE_NAMESPACE_ERROR",
          "namespace \"{0}\" cannot be provided twice\n" //
              + "Originally provided at {1}");

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

  static final DiagnosticType MISSING_PROVIDE_ERROR = DiagnosticType.error(
      "JSC_MISSING_PROVIDE_ERROR",
      "required \"{0}\" namespace never provided");

  static final DiagnosticType LATE_PROVIDE_ERROR = DiagnosticType.error(
      "JSC_LATE_PROVIDE_ERROR",
      "required \"{0}\" namespace not provided yet");

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
  private final JSModuleGraph moduleGraph;

  // The goog.provides must be processed in a deterministic order.
  private final Map<String, ProvidedName> providedNames = new LinkedHashMap<>();

  private final Set<String> knownClosureSubclasses = new HashSet<>();

  private final List<UnrecognizedRequire> unrecognizedRequires = new ArrayList<>();
  private final Set<String> exportedVariables = new HashSet<>();
  private final CheckLevel requiresLevel;
  private final PreprocessorSymbolTable preprocessorSymbolTable;
  private final List<Node> defineCalls = new ArrayList<>();
  private final boolean preserveGoogProvidesAndRequires;
  private final List<Node> requiresToBeRemoved = new ArrayList<>();
  private final Set<Node> maybeTemporarilyLiveNodes = new HashSet<>();

  ProcessClosurePrimitives(AbstractCompiler compiler,
      @Nullable PreprocessorSymbolTable preprocessorSymbolTable,
      CheckLevel requiresLevel,
      boolean preserveGoogProvidesAndRequires) {
    this.compiler = compiler;
    this.preprocessorSymbolTable = preprocessorSymbolTable;
    this.moduleGraph = compiler.getModuleGraph();
    this.requiresLevel = requiresLevel;
    this.preserveGoogProvidesAndRequires = preserveGoogProvidesAndRequires;

    // goog is special-cased because it is provided in Closure's base library.
    providedNames.put(GOOG,
        new ProvidedName(GOOG, null, null, false /* implicit */));
  }

  Set<String> getExportedVariableNames() {
    return exportedVariables;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseRoots(compiler, this, externs, root);

    for (Node n : defineCalls) {
      replaceGoogDefines(n);
    }

    for (ProvidedName pn : providedNames.values()) {
      pn.replace();
    }

    if (requiresLevel.isOn()) {
      for (UnrecognizedRequire r : unrecognizedRequires) {
        checkForLateOrMissingProvide(r);
      }
    }

    for (Node closureRequire : requiresToBeRemoved) {
      compiler.reportChangeToEnclosingScope(closureRequire);
      closureRequire.detach();
    }
    for (Node liveNode : maybeTemporarilyLiveNodes) {
      compiler.reportChangeToEnclosingScope(liveNode);
    }
  }

  private void checkForLateOrMissingProvide(UnrecognizedRequire r) {
    // Both goog.require and goog.requireType must have a matching goog.provide.
    // However, goog.require must match an earlier goog.provide, while goog.requireType is allowed
    // to match a later goog.provide.
    DiagnosticType error;
    ProvidedName expectedName = providedNames.get(r.namespace);
    if (expectedName != null && expectedName.firstNode != null) {
      if (r.isRequireType) {
        return;
      }
      error = LATE_PROVIDE_ERROR;
    } else {
      error = MISSING_PROVIDE_ERROR;
    }
    compiler.report(JSError.make(r.requireNode, requiresLevel, error, r.namespace));
  }

  private Node getAnyValueOfType(JSDocInfo jsdoc) {
    checkArgument(jsdoc.hasType());
    Node typeAst = jsdoc.getType().getRoot();
    if (typeAst.getToken() == Token.BANG) {
      typeAst = typeAst.getLastChild();
    }
    checkState(typeAst.isString(), typeAst);
    switch (typeAst.getString()) {
      case "boolean":
        return IR.falseNode();
      case "string":
        return IR.string("");
      case "number":
        return IR.number(0);
      default:
        throw new RuntimeException(typeAst.getString());
    }
  }

  /**
   * @param n
   */
  private void replaceGoogDefines(Node n) {
    Node parent = n.getParent();
    checkState(parent.isExprResult());
    String name = n.getSecondChild().getString();
    JSDocInfo jsdoc = n.getJSDocInfo();
    Node value =
        n.isFromExterns() ? getAnyValueOfType(jsdoc).srcref(n) : n.getChildAtIndex(2).detach();

    Node replacement = NodeUtil.newQNameDeclaration(compiler, name, value, jsdoc);
    replacement.useSourceInfoIfMissingFromForTree(parent);
    parent.replaceWith(replacement);
    compiler.reportChangeToEnclosingScope(replacement);
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
                if (validateUnaliasablePrimitiveCall(t, n, methodName)) {
                  processDefineCall(t, n, parent);
                }
                break;
              case "require":
              case "requireType":
                if (validateAliasiablePrimitiveCall(t, n, methodName)) {
                  processRequireCall(t, n, parent);
                }
                break;
              case "provide":
                if (validateUnaliasablePrimitiveCall(t, n, methodName)) {
                  processProvideCall(t, n, parent);
                }
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
              case "forwardDeclare":
                if (validateAliasiablePrimitiveCall(t, n, methodName)) {
                  processForwardDeclare(t, n, parent);
                }
                break;
              case "addDependency":
                if (validateUnaliasablePrimitiveCall(t, n, methodName)) {
                  processAddDependency(n, parent);
                }
                break;
              case "setCssNameMapping":
                processSetCssNameMapping(t, n, parent);
                break;
              default: // fall out
            }
          } else if (left.getLastChild().getString().equals("base")) {
            // maybe an "base" setup by goog.inherits
            maybeProcessClassBaseCall(t, n);
          }
        }
        break;

      case ASSIGN:
      case NAME:
        if (n.isName() && n.getString().equals("CLOSURE_DEFINES")) {
          handleClosureDefinesValues(t, n);
        } else {
          // If this is an assignment to a provided name, remove the provided
          // object.
          handleCandidateProvideDefinition(t, n, parent);
        }
        break;

      case EXPR_RESULT:
        handleStubDefinition(t, n);
        break;

      case CLASS:
        if (t.inGlobalHoistScope() && !NodeUtil.isClassExpression(n)) {
          String name = n.getFirstChild().getString();
          ProvidedName pn = providedNames.get(name);
          if (pn != null) {
            compiler.report(t.makeError(n, CLASS_NAMESPACE_ERROR, name));
          }
        }
        break;

      case FUNCTION:
        // If this is a declaration of a provided named function, this is an
        // error. Hoisted functions will explode if they're provided.
        if (t.inGlobalHoistScope() && NodeUtil.isFunctionDeclaration(n)) {
          String name = n.getFirstChild().getString();
          ProvidedName pn = providedNames.get(name);
          if (pn != null) {
            compiler.report(t.makeError(n, FUNCTION_NAMESPACE_ERROR, name));
          }
        }
        break;

      case GETPROP:
        if (n.getFirstChild().isName()
            && !parent.isCall()
            && !parent.isAssign()
            && n.matchesQualifiedName("goog.base")
            && !n.getSourceFileName().endsWith("goog.js")) {
          reportBadGoogBaseUse(t, n, "May only be called directly.");
        }
        break;
      default:
        break;
    }
  }

  /**
   * Verifies that a) the call is in the global scope and b) the return value is unused
   *
   * <p>This method is for primitives that never return a value.
   */
  private boolean validateUnaliasablePrimitiveCall(NodeTraversal t, Node n, String methodName) {
    return validatePrimitiveCallWithMessage(t, n, methodName, CLOSURE_CALL_CANNOT_BE_ALIASED_ERROR);
  }

  /**
   * Verifies that a) the call is in the global scope and b) the return value is unused
   *
   * <p>This method is for primitives that do return a value in modules, but not in scripts/
   * goog.provide files
   */
  private boolean validateAliasiablePrimitiveCall(NodeTraversal t, Node n, String methodName) {
    return validatePrimitiveCallWithMessage(
        t, n, methodName, CLOSURE_CALL_CANNOT_BE_ALIASED_OUTSIDE_MODULE_ERROR);
  }

  /**
   * @param methodName list of primitve types classed together with this one
   * @param invalidAliasingError which DiagnosticType to emit if this call is aliased. this depends
   *     on whether the primitive is sometimes aliasiable in a module or never aliasable.
   */
  private boolean validatePrimitiveCallWithMessage(
      NodeTraversal t, Node n, String methodName, DiagnosticType invalidAliasingError) {
    // Ignore invalid primitives if we didn't strip module sugar.
    if (compiler.getOptions().shouldPreserveGoogModule()) {
      return true;
    }

    if (!t.inGlobalHoistScope()) {
      compiler.report(t.makeError(n, INVALID_CLOSURE_CALL_SCOPE_ERROR));
      return false;
    } else if (!n.getParent().isExprResult()) {
      // If the call is in the global hoist scope, but the result is used
      compiler.report(t.makeError(n, invalidAliasingError, GOOG + "." + methodName));
      return false;
    }
    return true;
  }

  private void handleClosureDefinesValues(NodeTraversal t, Node n) {
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
          reportBadClosureCommonDefinesDefinition(t, c);
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

  /** Handles a goog.require or goog.requireType call. */
  private void processRequireCall(NodeTraversal t, Node n, Node parent) {
    Node left = n.getFirstChild();
    Node arg = left.getNext();
    String method = left.getFirstChild().getNext().getString();
    if (verifyLastArgumentIsString(t, left, arg)) {
      String ns = arg.getString();
      ProvidedName provided = providedNames.get(ns);
      if (provided == null || !provided.isExplicitlyProvided()) {
        unrecognizedRequires.add(new UnrecognizedRequire(n, ns, method.equals("requireType")));
      } else {
        JSModule providedModule = provided.explicitModule;

        if (!provided.isFromExterns()) {
          // TODO(tbreisacher): Report an error if there's a goog.provide in an @externs file.
          checkNotNull(providedModule, n);

          JSModule module = t.getModule();
          // A cross-chunk goog.require must match a goog.provide in an earlier chunk. However, a
          // cross-chunk goog.requireType is allowed to match a goog.provide in a later chunk.
          if (module != providedModule
              && !moduleGraph.dependsOn(module, providedModule)
              && !method.equals("requireType")) {
            compiler.report(
                t.makeError(n, XMODULE_REQUIRE_ERROR, ns,
                    providedModule.getName(),
                    module.getName()));
          }
        }
      }

      maybeAddToSymbolTable(left);
      maybeAddStringNodeToSymbolTable(arg);

      // Requires should be removed before further processing.
      // Some clients run closure pass multiple times, first with
      // the checks for broken requires turned off. In these cases, we
      // allow broken requires to be preserved by the first run to
      // let them be caught in the subsequent run.
      if (!preserveGoogProvidesAndRequires && (provided != null || requiresLevel.isOn())) {
        requiresToBeRemoved.add(parent);
      }
    }
  }

  /**
   * Handles a goog.provide call.
   */
  private void processProvideCall(NodeTraversal t, Node n, Node parent) {
    checkState(n.isCall());
    Node left = n.getFirstChild();
    Node arg = left.getNext();
    if (verifyProvide(t, left, arg)) {
      String ns = arg.getString();

      maybeAddToSymbolTable(left);
      maybeAddStringNodeToSymbolTable(arg);

      if (providedNames.containsKey(ns)) {
        ProvidedName previouslyProvided = providedNames.get(ns);
        if (!previouslyProvided.isExplicitlyProvided()) {
          previouslyProvided.addProvide(parent, t.getModule(), true);
        } else {
          String explicitSourceName = previouslyProvided.explicitNode.getSourceFileName();
          compiler.report(t.makeError(n, DUPLICATE_NAMESPACE_ERROR, ns, explicitSourceName));
        }
      } else {
        registerAnyProvidedPrefixes(ns, parent, t.getModule());
        providedNames.put(
            ns, new ProvidedName(ns, parent, t.getModule(), true));
      }
    }
  }

  /**
   * Handles a goog.define call.
   */
  private void processDefineCall(NodeTraversal t, Node n, Node parent) {
    Node left = n.getFirstChild();
    Node args = left.getNext();
    if (verifyDefine(t, parent, left, args)) {
      Node nameNode = args;

      maybeAddToSymbolTable(left);
      maybeAddStringNodeToSymbolTable(nameNode);

      this.defineCalls.add(n);
    }
  }

  /**
   * Handles a stub definition for a goog.provided name
   * (e.g. a @typedef or a definition from externs)
   *
   * @param n EXPR_RESULT node.
   */
  private void handleStubDefinition(NodeTraversal t, Node n) {
    if (!t.inGlobalHoistScope()) {
      return;
    }
    JSDocInfo info = n.getFirstChild().getJSDocInfo();
    boolean hasStubDefinition = info != null && (n.isFromExterns() || info.hasTypedefType());
    if (hasStubDefinition) {
      if (n.getFirstChild().isQualifiedName()) {
        String name = n.getFirstChild().getQualifiedName();
        ProvidedName pn = providedNames.get(name);
        if (pn != null) {
          n.putBooleanProp(Node.WAS_PREVIOUSLY_PROVIDED, true);
          pn.addDefinition(n, t.getModule());
        } else if (n.getBooleanProp(Node.WAS_PREVIOUSLY_PROVIDED)) {
          // We didn't find it in the providedNames, but it was previously marked as provided.
          // This implies we're in hotswap pass and the current typedef is a provided namespace.
          ProvidedName provided = new ProvidedName(name, n, t.getModule(), true);
          providedNames.put(name, provided);
        }
      }
    }
  }

  /**
   * Handles a candidate definition for a goog.provided name.
   */
  private void handleCandidateProvideDefinition(
      NodeTraversal t, Node n, Node parent) {
    if (t.inGlobalHoistScope()) {
      String name = null;
      if (n.isName() && NodeUtil.isNameDeclaration(parent)) {
        name = n.getString();
      } else if (n.isAssign() && parent.isExprResult()) {
        name = n.getFirstChild().getQualifiedName();
      }

      if (name != null) {
        if (parent.getBooleanProp(Node.IS_NAMESPACE)) {
          processProvideFromPreviousPass(t, name, parent);
        } else {
          ProvidedName pn = providedNames.get(name);
          if (pn != null) {
            pn.addDefinition(parent, t.getModule());
          }
        }
      }
    }
  }

  /**
   * Processes the base class call.
   */
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
      reportBadGoogBaseUse(t, n, "goog.base in ES6 class is not allowed. Use super instead.");
      return;
    }

    Node callee = n.getFirstChild();
    Node thisArg = callee.getNext();
    if (thisArg == null || !thisArg.isThis()) {
      reportBadGoogBaseUse(t, n, "First argument must be 'this'.");
      return;
    }

    Node enclosingFnNameNode = getEnclosingDeclNameNode(n);
    if (enclosingFnNameNode == null) {
      reportBadGoogBaseUse(t, n, "Could not find enclosing method.");
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
        reportBadGoogBaseUse(
            t, n, "Could not find goog.inherits for base class");
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
        reportBadGoogBaseUse(t, n, "Second argument must name a method.");
        return;
      }

      String methodName = methodNameNode.getString();
      String ending = ".prototype." + methodName;
      if (enclosingQname == null || !enclosingQname.endsWith(ending)) {
        reportBadGoogBaseUse(
            t, n, "Enclosing method does not match " + methodName);
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

  private void maybeProcessClassBaseCall(NodeTraversal t, Node n) {
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
        reportBadBaseMethodUse(t, n, baseContainer, "Could not find enclosing method.");
      } else if (baseUsedInClass(n)) {
        Node clazz = NodeUtil.getEnclosingClass(n);
        if ((clazz.getFirstChild().isName()
                && clazz.getFirstChild().getString().equals(baseContainer))
            || (clazz.getSecondChild().isName()
                && clazz.getSecondChild().getString().equals(baseContainer))) {
          reportBadBaseMethodUse(t, n, clazz.getFirstChild().getString(),
              "base method is not allowed in ES6 class. Use super instead.");
        }
      }
      return;
    }

    if (baseUsedInClass(n)) {
      reportBadGoogBaseUse(t, n, "goog.base in ES6 class is not allowed. Use super instead.");
      return;
    }

    String enclosingQname = enclosingFnNameNode.getQualifiedName();
    if (!enclosingQname.contains(".prototype.")) {
      // Handle constructors.

      // Check if this is some other "base" method.
      if (!enclosingQname.equals(baseContainer)) {
        // Report misuse of "base" methods from other known classes.
        if (knownClosureSubclasses.contains(baseContainer)) {
          reportBadBaseMethodUse(t, n, baseContainer, "Must be used within "
              + baseContainer + " methods");
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
        reportBadBaseMethodUse(t, n, baseContainer,
            "First argument must be 'this'.");
        return;
      }

      // Handle methods.
      Node methodNameNode = thisArg.getNext();
      if (methodNameNode == null
          || !methodNameNode.isString()
          || !methodNameNode.getString().equals("constructor")) {
        reportBadBaseMethodUse(t, n, baseContainer,
            "Second argument must be 'constructor'.");
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
        reportBadBaseMethodUse(t, n, baseContainer, "Must be used within "
            + baseContainer + " methods");
        return;
      }

      // The super class is known.
      Node callee = n.getFirstChild();
      Node thisArg = callee.getNext();
      if (thisArg == null || !thisArg.isThis()) {
        reportBadBaseMethodUse(t, n, baseContainer,
            "First argument must be 'this'.");
        return;
      }

      // Handle methods.
      Node methodNameNode = thisArg.getNext();
      if (methodNameNode == null || !methodNameNode.isString()) {
        reportBadBaseMethodUse(t, n, baseContainer,
            "Second argument must name a method.");
        return;
      }

      String methodName = methodNameNode.getString();
      String ending = ".prototype." + methodName;
      if (enclosingQname == null || !enclosingQname.endsWith(ending)) {
        reportBadBaseMethodUse(t, n, baseContainer,
            "Enclosing method does not match " + methodName);
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
    if (n.getChildCount() == 3) {
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
  private void reportBadGoogBaseUse(
      NodeTraversal t, Node n, String extraMessage) {
    compiler.report(t.makeError(n, GOOG_BASE_CLASS_ERROR, extraMessage));
  }

  /** Reports an incorrect use of super-method calling. */
  private void reportBadBaseMethodUse(
      NodeTraversal t, Node n, String className, String extraMessage) {
    compiler.report(t.makeError(n, BASE_CLASS_ERROR, className, extraMessage));
  }

  /** Reports an incorrect CLOSURE_DEFINES definition. */
  private void reportBadClosureCommonDefinesDefinition(
      NodeTraversal t, Node n) {
    compiler.report(t.makeError(n, CLOSURE_DEFINES_ERROR));
  }

  /**
   * Processes the output of processed-provide from a previous pass.  This will
   * update our data structures in the same manner as if the provide had been
   * processed in this pass.
   */
  private void processProvideFromPreviousPass(
      NodeTraversal t, String name, Node parent) {
    if (!providedNames.containsKey(name)) {
      // Record this provide created on a previous pass, and create a dummy
      // EXPR node as a placeholder to simulate an explicit provide.
      Node expr = new Node(Token.EXPR_RESULT);
      expr.useSourceInfoIfMissingFromForTree(parent);
      parent.getParent().addChildBefore(expr, parent);
      /**
       * 'expr' has been newly added to the AST, but it might be removed again before this pass
       * finishes. Keep it in a list for later change reporting if it doesn't get removed again
       * before the end of the pass.
       */
      maybeTemporarilyLiveNodes.add(expr);

      JSModule module = t.getModule();
      registerAnyProvidedPrefixes(name, expr, module);

      // If registerAnyProvidedPrefixes didn't add any children, add a no-op child so that
      // the AST is valid.
      if (!expr.hasChildren()) {
        expr.addChildToBack(NodeUtil.newUndefinedNode(parent));
      }

      ProvidedName provided = new ProvidedName(name, expr, module, true);
      providedNames.put(name, provided);
      provided.addDefinition(parent, module);
    } else {
      // Remove this provide if it came from a previous pass since we have an
      // replacement already.
      if (isNamespacePlaceholder(parent)) {
        compiler.reportChangeToEnclosingScope(parent);
        parent.detach();
      }
    }
  }

  /**
   * Processes a call to goog.setCssNameMapping(). Either the argument to
   * goog.setCssNameMapping() is valid, in which case it will be used to create
   * a CssRenamingMap for the compiler of this CompilerPass, or it is invalid
   * and a JSCompiler error will be reported.
   * @see #visit(NodeTraversal, Node, Node)
   */
  private void processSetCssNameMapping(NodeTraversal t, Node n, Node parent) {
    Node left = n.getFirstChild();
    Node arg = left.getNext();
    if (verifySetCssNameMapping(t, left, arg)) {
      // Translate OBJECTLIT into SubstitutionMap. All keys and
      // values must be strings, or an error will be thrown.
      final Map<String, String> cssNames = new HashMap<>();

      for (Node key = arg.getFirstChild(); key != null;
          key = key.getNext()) {
        Node value = key.getFirstChild();
        if (!key.isStringKey()
            || value == null
            || !value.isString()) {
          compiler.report(
              t.makeError(n,
                  NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR));
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
        compiler.report(
            t.makeError(n, INVALID_STYLE_ERROR, styleStr));
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
          compiler.report(
            t.makeError(n, INVALID_CSS_RENAMING_MAP, errors.toString()));
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
          compiler.report(
            t.makeError(n, INVALID_CSS_RENAMING_MAP, errors.toString()));
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
   * Verifies that a provide method call has exactly one argument,
   * and that it's a string literal and that the contents of the string are
   * valid JS tokens. Reports a compile error if it doesn't.
   *
   * @return Whether the argument checked out okay
   */
  private boolean verifyProvide(NodeTraversal t, Node methodName, Node arg) {
    if (!verifyLastArgumentIsString(t, methodName, arg)) {
      return false;
    }

    if (!NodeUtil.isValidQualifiedName(
        compiler.getOptions().getLanguageIn().toFeatureSet(), arg.getString())) {
      compiler.report(t.makeError(arg, INVALID_PROVIDE_ERROR,
          arg.getString(), compiler.getOptions().getLanguageIn().toString()));
      return false;
    }

    return true;
  }

  /**
   * Verifies that a provide method call has exactly one argument,
   * and that it's a string literal and that the contents of the string are
   * valid JS tokens. Reports a compile error if it doesn't.
   *
   * @return Whether the argument checked out okay
   */
  private boolean verifyDefine(NodeTraversal t,
      Node expr,
      Node methodName, Node args) {

    // Verify first arg
    Node arg = args;
    if (!verifyNotNull(t, methodName, arg) || !verifyOfType(t, methodName, arg, Token.STRING)) {
      return false;
    }

    // Verify second arg
    arg = arg.getNext();
    if (!args.isFromExterns()
        && (!verifyNotNull(t, methodName, arg) || !verifyIsLast(t, methodName, arg))) {
      return false;
    }

    String name = args.getString();
    if (!NodeUtil.isValidQualifiedName(
        compiler.getOptions().getLanguageIn().toFeatureSet(), name)) {
      compiler.report(t.makeError(args, INVALID_DEFINE_NAME_ERROR, name));
      return false;
    }

    JSDocInfo info = expr.getFirstChild().getJSDocInfo();
    if (info == null || !info.isDefine()) {
      compiler.report(t.makeError(expr, MISSING_DEFINE_ANNOTATION));
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

  /**
   * Process a goog.forwardDeclare() call and record the specified forward
   * declaration.
   */
  private void processForwardDeclare(NodeTraversal t, Node n, Node parent) {
    CodingConvention convention = compiler.getCodingConvention();

    String typeDeclaration = null;
    try {
      typeDeclaration = Iterables.getOnlyElement(
          convention.identifyTypeDeclarationCall(n));
    } catch (NullPointerException | NoSuchElementException | IllegalArgumentException e) {
      compiler.report(
          t.makeError(
              n,
              INVALID_FORWARD_DECLARE,
              "A single type could not identified for the goog.forwardDeclare statement"));
    }

    if (typeDeclaration != null) {
      compiler.forwardDeclareType(typeDeclaration);
      // Forward declaration was recorded and we can remove the call.
      Node toRemove = parent.isExprResult() ? parent : parent.getParent();
      NodeUtil.deleteNode(toRemove, compiler);
    }
  }

  /**
   * Verifies that a method call has exactly one argument, and that it's a
   * string literal. Reports a compile error if it doesn't.
   *
   * @return Whether the argument checked out okay
   */
  private boolean verifyLastArgumentIsString(
      NodeTraversal t, Node methodName, Node arg) {
    return verifyNotNull(t, methodName, arg)
        && verifyOfType(t, methodName, arg, Token.STRING)
        && verifyIsLast(t, methodName, arg);
  }

  /**
   * @return Whether the argument checked out okay
   */
  private boolean verifyNotNull(NodeTraversal t, Node methodName, Node arg) {
    if (arg == null) {
      compiler.report(
          t.makeError(methodName,
              NULL_ARGUMENT_ERROR, methodName.getQualifiedName()));
      return false;
    }
    return true;
  }

  /**
   * @return Whether the argument checked out okay
   */
  private boolean verifyOfType(NodeTraversal t, Node methodName,
      Node arg, Token desiredType) {
    if (arg.getToken() != desiredType) {
      compiler.report(
          t.makeError(methodName,
              INVALID_ARGUMENT_ERROR, methodName.getQualifiedName()));
      return false;
    }
    return true;
  }

  /**
   * @return Whether the argument checked out okay
   */
  private boolean verifyIsLast(NodeTraversal t, Node methodName, Node arg) {
    if (arg.getNext() != null) {
      compiler.report(
          t.makeError(methodName,
              TOO_MANY_ARGUMENTS_ERROR, methodName.getQualifiedName()));
      return false;
    }
    return true;
  }

  /**
   * Verifies that setCssNameMapping is called with the correct methods.
   *
   * @return Whether the arguments checked out okay
   */
  private boolean verifySetCssNameMapping(NodeTraversal t, Node methodName,
      Node firstArg) {
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
      compiler.report(
          t.makeError(methodName,
              diagnostic, methodName.getQualifiedName()));
      return false;
    }
    return true;
  }

  /**
   * Registers ProvidedNames for prefix namespaces if they haven't
   * already been defined. The prefix namespaces must be registered in
   * order from shortest to longest.
   *
   * @param ns The namespace whose prefixes may need to be provided.
   * @param node The EXPR of the provide call.
   * @param module The current module.
   */
  private void registerAnyProvidedPrefixes(
      String ns, Node node, JSModule module) {
    int pos = ns.indexOf('.');
    while (pos != -1) {
      String prefixNs = ns.substring(0, pos);
      pos = ns.indexOf('.', pos + 1);
      if (providedNames.containsKey(prefixNs)) {
        providedNames.get(prefixNs).addProvide(
            node, module, false /* implicit */);
      } else {
        providedNames.put(
            prefixNs,
            new ProvidedName(prefixNs, node, module, false /* implicit */));
      }
    }
  }

  // -------------------------------------------------------------------------

  /**
   * Information required to replace a goog.provide call later in the traversal.
   */
  private class ProvidedName {
    private final String namespace;

    // The node and module where the call was explicitly or implicitly
    // goog.provided.
    private final Node firstNode;
    private final JSModule firstModule;

    // The node where the call was explicitly goog.provided. May be null
    // if the namespace is always provided implicitly.
    private Node explicitNode = null;
    private JSModule explicitModule = null;

    // There are child namespaces of this one.
    private boolean hasAChildNamespace = false;

    // The candidate definition.
    private Node candidateDefinition = null;

    // The minimum module where the provide must appear.
    private JSModule minimumModule = null;

    // The replacement declaration.
    private Node replacementNode = null;

    ProvidedName(String namespace, Node node, JSModule module,
        boolean explicit) {
      Preconditions.checkArgument(node == null /* The base case */ || node.isExprResult());
      this.namespace = namespace;
      this.firstNode = node;
      this.firstModule = module;

      addProvide(node, module, explicit);
    }

    /**
     * Add an implicit or explicit provide.
     */
    void addProvide(Node node, JSModule module, boolean explicit) {
      if (explicit) {
        // goog.provide('name.space');
        checkState(explicitNode == null);
        checkArgument(node.isExprResult());
        explicitNode = node;
        explicitModule = module;
      } else {
        // goog.provide('name.space.some.child');
        hasAChildNamespace = true;
      }
      updateMinimumModule(module);
    }

    boolean isExplicitlyProvided() {
      return explicitNode != null;
    }

    boolean isFromExterns() {
      return explicitNode.isFromExterns();
    }

    /**
     * Record function declaration, variable declaration or assignment that
     * refers to the same name as the provide statement.  Give preference to
     * declarations; if no declaration exists, record a reference to an
     * assignment so it repurposed later.
     */
    void addDefinition(Node node, JSModule module) {
      Preconditions.checkArgument(
          node.isExprResult() // assign
              || node.isFunction()
              || NodeUtil.isNameDeclaration(node));
      checkArgument(explicitNode != node);
      if ((candidateDefinition == null) || !node.isExprResult()) {
        candidateDefinition = node;
        updateMinimumModule(module);
      }
    }

    private void updateMinimumModule(JSModule newModule) {
      if (minimumModule == null) {
        minimumModule = newModule;
      } else if (moduleGraph.getModuleCount() > 1) {
        minimumModule = moduleGraph.getDeepestCommonDependencyInclusive(
            minimumModule, newModule);
      } else {
        // If there is no module graph, then there must be exactly one
        // module in the program.
        checkState(newModule == minimumModule, "Missing module graph");
      }
    }

    /**
     * Replace the provide statement.
     *
     * If we're providing a name with no definition, then create one.
     * If we're providing a name with a duplicate definition, then make sure
     * that definition becomes a declaration.
     */
    void replace() {
      if (firstNode == null) {
        // Don't touch the base case ('goog').
        replacementNode = candidateDefinition;
        return;
      }

      // Handle the case where there is a duplicate definition for an explicitly
      // provided symbol.
      if (candidateDefinition != null && explicitNode != null) {
        JSDocInfo info;
        if (candidateDefinition.isExprResult()) {
          info = candidateDefinition.getFirstChild().getJSDocInfo();
        } else {
          info = candidateDefinition.getJSDocInfo();
        }

        // Validate that the namespace is not declared as a generic object type.
        if (info != null) {
          JSTypeExpression expr = info.getType();
          if (expr != null) {
            Node n = expr.getRoot();
            if (n.getToken() == Token.BANG) {
              n = n.getFirstChild();
            }
            if (n.isString()
                && !n.hasChildren() // templated object types are ok.
                && n.getString().equals("Object")) {
              compiler.report(
                  JSError.make(candidateDefinition, WEAK_NAMESPACE_TYPE));
            }
          }
        }

        // Does this need a VAR keyword?
        replacementNode = candidateDefinition;
        if (candidateDefinition.isExprResult()) {
          Node exprNode = candidateDefinition.getOnlyChild();
          if (exprNode.isAssign()) {
            // namespace = value;
            candidateDefinition.putBooleanProp(Node.IS_NAMESPACE, true);
            Node nameNode = exprNode.getFirstChild();
            if (nameNode.isName()) {
              // Need to convert this assign to a var declaration.
              Node valueNode = nameNode.getNext();
              exprNode.removeChild(nameNode);
              exprNode.removeChild(valueNode);
              nameNode.addChildToFront(valueNode);
              Node varNode = IR.var(nameNode);
              varNode.useSourceInfoFrom(candidateDefinition);
              candidateDefinition.replaceWith(varNode);
              varNode.setJSDocInfo(exprNode.getJSDocInfo());
              compiler.reportChangeToEnclosingScope(varNode);
              replacementNode = varNode;
            }
          } else {
            // /** @typedef {something} */ name.space.Type;
            checkState(exprNode.isQualifiedName(), exprNode);
            // If this namespace has child namespaces, we still need to add an object to hang them
            // on to avoid creating broken code.
            // We must cast the type of the literal to unknown, because the type checker doesn't
            // expect the namespace to have a value.
            if (hasAChildNamespace) {
              replaceWith(createDeclarationNode(
                  IR.cast(IR.objectlit(), createUnknownTypeJsDocInfo())));
            }
          }
        }
      } else {
        // Handle the case where there's not a duplicate definition.
        replaceWith(createDeclarationNode(IR.objectlit()));
      }
      if (explicitNode != null) {
        if (preserveGoogProvidesAndRequires && explicitNode.hasChildren()) {
          return;
        }
        /*
         * If 'explicitNode' was added earlier in this pass then don't bother to report its removal
         * right here as a change (since the original AST state is being restored). Also remove
         * 'explicitNode' from the list of "possibly live" nodes so that it does not get reported as
         * a change at the end of the pass.
         */
        if (!maybeTemporarilyLiveNodes.remove(explicitNode)) {
          compiler.reportChangeToEnclosingScope(explicitNode);
        }
        explicitNode.detach();
      }
    }

    private void replaceWith(Node replacement) {
      replacementNode = replacement;
      if (firstModule == minimumModule) {
        firstNode.getParent().addChildBefore(replacementNode, firstNode);
      } else {
        // In this case, the name was implicitly provided by two independent
        // modules. We need to move this code up to a common module.
        int indexOfDot = namespace.lastIndexOf('.');
        if (indexOfDot == -1) {
          // Any old place is fine.
          compiler.getNodeForCodeInsertion(minimumModule)
              .addChildToBack(replacementNode);
        } else {
          // Add it after the parent namespace.
          ProvidedName parentName =
              providedNames.get(namespace.substring(0, indexOfDot));
          checkNotNull(parentName);
          checkNotNull(parentName.replacementNode);
          parentName.replacementNode.getParent().addChildAfter(
              replacementNode, parentName.replacementNode);
        }
      }
      compiler.reportChangeToEnclosingScope(replacementNode);
    }

    /**
     * Create the declaration node for this name, without inserting it
     * into the AST.
     */
    private Node createDeclarationNode(Node value) {
      if (namespace.indexOf('.') == -1) {
        return makeVarDeclNode(value);
      } else {
        return makeAssignmentExprNode(value);
      }
    }

    /**
     * Creates a simple namespace variable declaration
     * (e.g. <code>var foo = {};</code>).
     */
    private Node makeVarDeclNode(Node value) {
      Node name = IR.name(namespace);
      name.addChildToFront(value);

      Node decl = IR.var(name);
      decl.putBooleanProp(Node.IS_NAMESPACE, true);

      if (compiler.getCodingConvention().isConstant(namespace)) {
        name.putBooleanProp(Node.IS_CONSTANT_NAME, true);
      }
      if (candidateDefinition == null) {
        decl.setJSDocInfo(NodeUtil.createConstantJsDoc());
      }

      checkState(isNamespacePlaceholder(decl));
      setSourceInfo(decl);
      return decl;
    }

    /**
     * Creates a dotted namespace assignment expression
     * (e.g. <code>foo.bar = {};</code>).
     */
    private Node makeAssignmentExprNode(Node value) {
      Node lhs =
          NodeUtil.newQName(
              compiler,
              namespace,
              firstNode /* real source info will be filled in below */,
              namespace);
      Node decl = IR.exprResult(IR.assign(lhs, value));
      decl.putBooleanProp(Node.IS_NAMESPACE, true);
      if (candidateDefinition == null) {
        decl.getFirstChild().setJSDocInfo(NodeUtil.createConstantJsDoc());
      }
      checkState(isNamespacePlaceholder(decl));
      setSourceInfo(decl);
      // This function introduces artifical nodes and we don't need them for indexing.
      // Marking all but the last one as non-indexable. So if this function adds:
      // foo.bar.baz = {};
      // then we mark foo and bar as non-indexable.
      lhs.getFirstChild().makeNonIndexableRecursive();
      return decl;
    }

    /**
     * Copy source info to the new node.
     */
    private void setSourceInfo(Node newNode) {
      Node provideStringNode = getProvideStringNode();
      int offset = provideStringNode == null ? 0 : getSourceInfoOffset();
      Node sourceInfoNode = provideStringNode == null ? firstNode : provideStringNode;
      newNode.useSourceInfoIfMissingFromForTree(sourceInfoNode);
      if (offset != 0) {
        newNode.setSourceEncodedPositionForTree(
            sourceInfoNode.getSourcePosition() + offset);
      }
    }

    /**
     * Get the offset into the provide node where the symbol appears.
     */
    private int getSourceInfoOffset() {
      int indexOfLastDot = namespace.lastIndexOf('.');

      // +1 for the opening quote
      // +1 for the dot
      // if there's no dot, then the -1 index cancels it out
      // so elegant!
      return 2 + indexOfLastDot;
    }

    private Node getProvideStringNode() {
      return (firstNode.getFirstChild() != null && NodeUtil.isExprCall(firstNode))
          ? firstNode.getFirstChild().getLastChild()
          : null;
    }
  }

  private JSDocInfo createUnknownTypeJsDocInfo() {
    JSDocInfoBuilder castToUnknownBuilder = new JSDocInfoBuilder(true);
    castToUnknownBuilder.recordType(
        new JSTypeExpression(
            JsDocInfoParser.parseTypeString("?"), "<ProcessClosurePrimitives.java>"));
    return castToUnknownBuilder.build();
  }

  /**
   * @return Whether the node is namespace placeholder.
   */
  private static boolean isNamespacePlaceholder(Node n) {
    if (!n.getBooleanProp(Node.IS_NAMESPACE)) {
      return false;
    }

    Node value = null;
    if (n.isExprResult()) {
      Node assign = n.getFirstChild();
      value = assign.getLastChild();
    } else if (n.isVar()) {
      Node name = n.getFirstChild();
      value = name.getFirstChild();
    }

    if (value == null) {
      return false;
    }
    if (value.isCast()) {
      // There may be a cast to unknown type wrapped around the value.
      value = value.getOnlyChild();
    }
    return value.isObjectLit() && !value.hasChildren();
  }

  /**
   * The string in {@code n} is a reference name. Create a synthetic
   * node for it with all the proper source info, and add it to the symbol
   * table.
   */
  private void maybeAddStringNodeToSymbolTable(Node n) {
    if (preprocessorSymbolTable == null) {
      return;
    }

    String name = n.getString();
    Node syntheticRef = NodeUtil.newQName(
        compiler, name,
        n /* real source offsets will be filled in below */,
        name);

    // Offsets to add to source. Named for documentation purposes.
    final int forQuote = 1;
    final int forDot = 1;

    Node current = null;
    for (current = syntheticRef;
         current.isGetProp();
         current = current.getFirstChild()) {
      int fullLen = current.getQualifiedName().length();
      int namespaceLen = current.getFirstChild().getQualifiedName().length();

      current.setSourceEncodedPosition(n.getSourcePosition() + forQuote);
      current.setLength(fullLen);

      current.getLastChild().setSourceEncodedPosition(
          n.getSourcePosition() + namespaceLen + forQuote + forDot);
      current.getLastChild().setLength(
          current.getLastChild().getString().length());
    }

    current.setSourceEncodedPosition(n.getSourcePosition() + forQuote);
    current.setLength(current.getString().length());

    maybeAddToSymbolTable(syntheticRef);
  }

  /**
   * Add the given qualified name node to the symbol table.
   */
  private void maybeAddToSymbolTable(Node n) {
    if (preprocessorSymbolTable != null) {
      preprocessorSymbolTable.addReference(n);
    }
  }

  // -------------------------------------------------------------------------

  /**
   * Information required to create a {@code MISSING_PROVIDE_ERROR} warning.
   */
  private static class UnrecognizedRequire {
    final Node requireNode;
    final String namespace;
    final boolean isRequireType;

    UnrecognizedRequire(Node requireNode, String namespace, boolean isRequireType) {
      this.requireNode = requireNode;
      this.namespace = namespace;
      this.isRequireType = isRequireType;
    }
  }
}
