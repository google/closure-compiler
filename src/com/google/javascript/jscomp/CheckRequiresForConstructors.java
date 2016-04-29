/*
 * Copyright 2008 The Closure Compiler Authors.
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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.NodeUtil.Visitor;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * This pass walks the AST to create a Collection of 'new' nodes and
 * 'goog.require' nodes. It reconciles these Collections, creating a
 * warning for each discrepancy.
 *
 * <p>The rules on when a warning is reported are: <ul>
 * <li>Type is referenced in code -> goog.require is required
 *     (missingRequires check fails if it's not there)
 * <li>Type is referenced in an @extends or @implements -> goog.require is required
 *     (missingRequires check fails if it's not there)
 * <li>Type is referenced in other JsDoc (@type etc) -> goog.require is optional
 *     (don't warn, regardless of if it is there)
 * <li>Type is not referenced at all -> goog.require is forbidden
 *     (extraRequires check fails if it is there)
 * </ul>
 *
 */
class CheckRequiresForConstructors implements HotSwapCompilerPass, NodeTraversal.Callback {
  private final AbstractCompiler compiler;
  private final CodingConvention codingConvention;

  private static final Splitter DOT_SPLITTER = Splitter.on('.');
  private static final Joiner DOT_JOINER = Joiner.on('.');

  public static enum Mode {
    // Looking at a single file. Only a minimal set of externs are present.
    SINGLE_FILE,
    // Used during a normal compilation. The entire program + externs are available.
    FULL_COMPILE
  };
  private final Mode mode;

  private final Set<String> providedNames = new HashSet<>();
  private final Map<String, Node> requires = new HashMap<>();

  // Only used in single-file mode.
  private final Set<String> closurizedNamespaces = new HashSet<>();

  // Adding an entry to usages indicates that the name is used and should be required.
  private final Map<String, Node> usages = new HashMap<>();

  // Adding an entry to weakUsages indicates that the name is used, but in a way which may not
  // require a goog.require, such as in a @type annotation. If the only usages of a name are
  // in weakUsages, don't give a missingRequire warning, nor an extraRequire warning.
  private final Set<String> weakUsages = new HashSet<>();

  // The body of the goog.scope function, if any.
  @Nullable
  private Node googScopeBlock;

  static final DiagnosticType MISSING_REQUIRE_WARNING =
      DiagnosticType.disabled(
          "JSC_MISSING_REQUIRE_WARNING", "missing require: ''{0}''");

  // TODO(tbreisacher): Move this into the missingRequire group (b/27856452).
  static final DiagnosticType MISSING_REQUIRE_CALL_WARNING =
      DiagnosticType.disabled(
          "JSC_MISSING_REQUIRE_CALL_WARNING", "missing require: ''{0}''");

  static final DiagnosticType EXTRA_REQUIRE_WARNING = DiagnosticType.disabled(
      "JSC_EXTRA_REQUIRE_WARNING", "extra require: ''{0}''");

  static final DiagnosticType DUPLICATE_REQUIRE_WARNING = DiagnosticType.disabled(
      "JSC_DUPLICATE_REQUIRE_WARNING",
      "''{0}'' required more than once.");

  private static final Set<String> DEFAULT_EXTRA_NAMESPACES = ImmutableSet.of(
      "goog.testing.asserts", "goog.testing.jsunit", "goog.testing.JsTdTestCaseAdapter");

  CheckRequiresForConstructors(AbstractCompiler compiler, Mode mode) {
    this.compiler = compiler;
    this.mode = mode;
    this.codingConvention = compiler.getCodingConvention();
  }

  /**
   * Uses Collections of new and goog.require nodes to create a compiler warning
   * for each new class name without a corresponding goog.require().
   */
  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseRootsEs6(compiler, this, externs, root);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverseEs6(compiler, scriptRoot, this);
  }

  // Return true if the name is a class name (starts with an uppercase
  // character, but is not in all-caps).
  private static boolean isClassName(String name) {
    return isClassOrConstantName(name)
        && !name.equals(name.toUpperCase());
  }

  // Return true if the name looks like a class name or a constant name.
  private static boolean isClassOrConstantName(String name) {
    return name != null && name.length() > 1
        && Character.isUpperCase(name.charAt(0));
  }

  // Return the shortest prefix of the className that refers to a class,
  // or null if no part refers to a class.
  private static List<String> getClassNames(String qualifiedName) {
    ImmutableList.Builder<String> classNames = ImmutableList.builder();
    List<String> parts = DOT_SPLITTER.splitToList(qualifiedName);
    for (int i = 0; i < parts.size(); i++) {
      String part = parts.get(i);
      if (isClassOrConstantName(part)) {
        classNames.add(DOT_JOINER.join(parts.subList(0, i + 1)));
      }
    }
    return classNames.build();
  }

  // TODO(tbreisacher): Update CodingConvention.extractClassNameIf{Require,Provide} to match this.
  private String extractNamespace(Node call, String functionName) {
    Node callee = call.getFirstChild();
    if (callee.isGetProp() && callee.matchesQualifiedName(functionName)) {
      Node target = callee.getNext();
      if (target != null && target.isString()) {
        return target.getString();
      }
    }
    return null;
  }

  private String extractNamespaceIfRequire(Node call) {
    return extractNamespace(call, "goog.require");
  }

  private String extractNamespaceIfProvide(Node call) {
    return extractNamespace(call, "goog.provide");
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    if (n.isCall() && n.getFirstChild().matchesQualifiedName("goog.scope")) {
      Node function = n.getSecondChild();
      if (function.isFunction()) {
        googScopeBlock = NodeUtil.getFunctionBody(function);
      }
    }

    return parent == null || !parent.isScript() || !t.getInput().isExtern();
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    maybeAddJsDocUsages(t, n);
    switch (n.getType()) {
      case Token.ASSIGN:
        maybeAddProvidedName(n);
        break;
      case Token.VAR:
      case Token.LET:
      case Token.CONST:
        maybeAddProvidedName(n);
        maybeAddGoogScopeUsage(n, parent);
        break;
      case Token.FUNCTION:
        // Exclude function expressions.
        if (NodeUtil.isStatement(n)) {
          maybeAddProvidedName(n);
        }
        break;
      case Token.NAME:
        if (!NodeUtil.isLValue(n)) {
          visitQualifiedName(n);
        }
        break;
      case Token.GETPROP:
        // If parent is a GETPROP, they will handle the weak usages.
        if (!parent.isGetProp() && n.isQualifiedName()) {
          visitQualifiedName(n);
        }
        break;
      case Token.CALL:
        visitCallNode(t, n, parent);
        break;
      case Token.SCRIPT:
        visitScriptNode(t);
        reset();
        break;
      case Token.NEW:
        visitNewNode(t, n);
        break;
      case Token.CLASS:
        visitClassNode(t, n);
        break;
      case Token.IMPORT:
        visitImportNode(n);
        break;
    }
  }

  private void reset() {
    this.usages.clear();
    this.weakUsages.clear();
    this.requires.clear();
    this.closurizedNamespaces.clear();
    this.providedNames.clear();
    this.googScopeBlock = null;
  }

  private void visitScriptNode(NodeTraversal t) {
    if (mode == Mode.SINGLE_FILE && requires.isEmpty() && closurizedNamespaces.isEmpty()) {
      // Likely a file that isn't using Closure at all.
      return;
    }

    Set<String> namespaces = new HashSet<>();

    // For every usage, check that there is a goog.require, and warn if not.
    for (Map.Entry<String, Node> entry : usages.entrySet()) {
      String namespace = entry.getKey();
      if (namespace.endsWith(".call") || namespace.endsWith(".apply")) {
        namespace = namespace.substring(0, namespace.lastIndexOf('.'));
      }
      if (namespace.startsWith("goog.global.")
          // Most functions in base.js are goog.someName, but
          // goog.module.{get,declareLegacyNamespace} are the exceptions, so just check for them
          // explicitly.
          || namespace.equals("goog.module.get")
          || namespace.equals("goog.module.declareLegacyNamespace")) {
        continue;
      }

      Node node = entry.getValue();
      JSDocInfo info = NodeUtil.getBestJSDocInfo(NodeUtil.getEnclosingStatement(node));
      if (info != null && info.getSuppressions().contains("missingRequire")) {
        continue;
      }

      List<String> classNames = getClassNames(namespace);
      // The parent namespace of the outermost class is also checked, so that classes
      // used by goog.module are still checked properly. This may cause missing requires
      // to be missed but in practice that should happen rarely.
      String nonNullClassName = Iterables.getFirst(classNames, namespace);
      String parentNamespace = null;
      int separatorIndex = nonNullClassName.lastIndexOf('.');
      if (separatorIndex > 0) {
        parentNamespace = nonNullClassName.substring(0, separatorIndex);
      }
      if ("goog".equals(parentNamespace)
          && !isClassName(nonNullClassName.substring(separatorIndex + 1))) {
        // This is probably something provided in Closure's base.js so it doesn't need
        // to be required.
        continue;
      }

      boolean providedByConstructors =
          providedNames.contains(namespace) || providedNames.contains(parentNamespace);
      boolean providedByRequires =
          requires.containsKey(namespace) || requires.containsKey(parentNamespace);

      for (String className : classNames) {
        if (providedNames.contains(className)) {
          providedByConstructors = true;
        }
        if (requires.containsKey(className)) {
          providedByRequires = true;
        }
      }

      if (!providedByConstructors && !providedByRequires && !namespaces.contains(namespace)) {
        // TODO(mknichel): If the symbol is not explicitly provided, find the next best
        // symbol from the provides in the same file.
        String rootName = Splitter.on('.').split(namespace).iterator().next();
        if (mode != Mode.SINGLE_FILE || closurizedNamespaces.contains(rootName)) {
          if (node.isCall()) {
            String defaultName = parentNamespace != null ? parentNamespace : namespace;
            String nameToReport = Iterables.getFirst(classNames, defaultName);
            compiler.report(t.makeError(node, MISSING_REQUIRE_CALL_WARNING, nameToReport));
          } else {
            compiler.report(t.makeError(node, MISSING_REQUIRE_WARNING, namespace));
          }
          namespaces.add(namespace);
        }
      }
    }

    // For every goog.require, check that there is a usage (in either usages or weakUsages)
    // and warn if there is not.
    for (Map.Entry<String, Node> entry : requires.entrySet()) {
      String require = entry.getKey();
      Node call = entry.getValue();
      if (!usages.containsKey(require) && !weakUsages.contains(require)) {
        reportExtraRequireWarning(call, require);
      }
    }
  }

  private void reportExtraRequireWarning(Node call, String require) {
    if (DEFAULT_EXTRA_NAMESPACES.contains(require)) {
      return;
    }
    JSDocInfo jsDoc = NodeUtil.getBestJSDocInfo(call);
    if (jsDoc != null && jsDoc.getSuppressions().contains("extraRequire")) {
      // There is a @suppress {extraRequire} on the call node or its enclosing statement.
      // This is one of the acceptable places for a @suppress, per
      // https://github.com/google/closure-compiler/wiki/@suppress-annotations
      return;
    }
    compiler.report(JSError.make(call, EXTRA_REQUIRE_WARNING, require));
  }

  private void reportDuplicateRequireWarning(Node call, String require) {
    compiler.report(JSError.make(call, DUPLICATE_REQUIRE_WARNING, require));
  }

  /**
   * @param localName The name that should be used in this file.
   *
   * <pre>
   * Require style                        | localName
   * -------------------------------------|----------
   * goog.require('foo.bar');             | foo.bar
   * var bar = goog.require('foo.bar');   | bar
   * var {qux} = goog.require('foo.bar'); | qux
   * import {qux} from 'foo.bar';         | qux
   * </pre>
   */
  private void visitRequire(String localName, Node node) {
    if (requires.containsKey(localName)) {
      reportDuplicateRequireWarning(node, localName);
    } else {
      requires.put(localName, node);
    }
  }

  private void visitImportNode(Node importNode) {
    Node defaultImport = importNode.getFirstChild();
    if (defaultImport.isName()) {
      visitRequire(defaultImport.getString(), importNode);
    }
    Node namedImports = defaultImport.getNext();
    if (namedImports.getType() == Token.IMPORT_SPECS) {
      for (Node importSpec : namedImports.children()) {
        visitRequire(importSpec.getLastChild().getString(), importNode);
      }
    }
  }

  private void maybeAddClosurizedNamespace(String requiredName) {
    if (mode == Mode.SINGLE_FILE) {
      String rootName = Splitter.on('.').split(requiredName).iterator().next();
      closurizedNamespaces.add(rootName);
    }
  }

  private void visitCallNode(NodeTraversal t, Node call, Node parent) {
    String required = extractNamespaceIfRequire(call);
    if (required != null) {
      maybeAddClosurizedNamespace(required);
      if (parent.isName()) {
        visitRequire(parent.getString(), call);
      } else if (parent.isDestructuringLhs() && parent.getFirstChild().isObjectPattern()) {
        for (Node stringKey : parent.getFirstChild().children()) {
          if (stringKey.hasChildren()) {
            visitRequire(stringKey.getFirstChild().getString(), call);
          } else {
            visitRequire(stringKey.getString(), call);
          }
        }
      } else {
        visitRequire(required, call);
      }
      return;
    }
    String provided = extractNamespaceIfProvide(call);
    if (provided != null) {
      providedNames.add(provided);
      return;
    }

    Node callee = call.getFirstChild();
    if (callee.matchesQualifiedName("goog.module.get") && call.getSecondChild().isString()) {
      weakUsages.add(call.getSecondChild().getString());
    }

    if (codingConvention.isClassFactoryCall(call)) {
      if (parent.isName()) {
        providedNames.add(parent.getString());
      } else if (parent.isAssign()) {
        providedNames.add(parent.getFirstChild().getQualifiedName());
      }
    }

    if (callee.isName()) {
      weakUsages.add(callee.getString());
    } else if (callee.isQualifiedName()) {
      Node root = NodeUtil.getRootOfQualifiedName(callee);
      if (root.isName()) {
        Var var = t.getScope().getVar(root.getString());
        if (var == null || (!var.isExtern() && !var.isLocal())) {
          usages.put(callee.getQualifiedName(), call);
        }
      }
    }
  }

  private void addWeakUsagesOfAllPrefixes(String qualifiedName) {
    // For "foo.bar.baz.qux" add weak usages for "foo.bar.baz.qux", "foo.bar.baz",
    // "foo.bar", and "foo" because those might all be goog.provide'd in different files,
    // so it doesn't make sense to require the user to goog.require all of them.
    for (int i = qualifiedName.indexOf('.'); i != -1; i = qualifiedName.indexOf('.', i + 1)) {
      String prefix = qualifiedName.substring(0, i);
      weakUsages.add(prefix);
    }
    weakUsages.add(qualifiedName);
  }

  private void visitQualifiedName(Node getpropOrName) {
    if (getpropOrName.isName() && getpropOrName.getString() != null) {
      // If the referenced thing is a goog.require as desugared from goog.module().
      if (getpropOrName.getBooleanProp(Node.GOOG_MODULE_REQUIRE)) {
        Node declStatement = NodeUtil.getEnclosingStatement(getpropOrName);
        if (NodeUtil.isNameDeclaration(declStatement)) {
          for (Node varChild : declStatement.children()) {
            // Normal declaration.
            if (varChild.isName()) {
              requires.put(varChild.getString(), getpropOrName);
            }
            // Object destructuring declaration.
            if (varChild.isObjectPattern()) {
              for (Node objectChild : varChild.children()) {
                if (objectChild.isStringKey()) {
                  requires.put(objectChild.getString(), getpropOrName);
                }
              }
            }
          }
        }
      }
    }

    addWeakUsagesOfAllPrefixes(getpropOrName.getQualifiedName());
  }

  private void visitNewNode(NodeTraversal t, Node newNode) {
    Node qNameNode = newNode.getFirstChild();

    // Single names are likely external, but if this is running in single-file mode, they
    // will not be in the externs, so add a weak usage.
    if (mode == Mode.SINGLE_FILE && qNameNode.isName()) {
      weakUsages.add(qNameNode.getString());
      return;
    }

    // If the ctor is something other than a qualified name, ignore it.
    if (!qNameNode.isQualifiedName()) {
      return;
    }

    // Grab the root ctor namespace.
    Node root = NodeUtil.getRootOfQualifiedName(qNameNode);

    // We only consider programmer-defined constructors that are
    // global variables, or are defined on global variables.
    if (!root.isName()) {
      return;
    }

    String name = root.getString();
    Var var = t.getScope().getVar(name);
    if (var != null
        && (var.isExtern()
            || var.getSourceFile() == newNode.getStaticSourceFile()
            || ClosureRewriteModule.isModuleExport(name))) {
      return;
    }
    usages.put(qNameNode.getQualifiedName(), newNode);

    // for "new foo.bar.Baz.Qux" add weak usages for "foo.bar.Baz", "foo.bar", and "foo"
    // because those might be goog.provide'd from a different file than foo.bar.Baz.Qux,
    // so it doesn't make sense to require the user to goog.require all of them.
    for (; qNameNode != null; qNameNode = qNameNode.getFirstChild()) {
      weakUsages.add(qNameNode.getQualifiedName());
    }
  }

  private void visitClassNode(NodeTraversal t, Node classNode) {
    String name = NodeUtil.getName(classNode);
    if (name != null) {
      providedNames.add(name);
    }

    Node extendClass = classNode.getSecondChild();

    // If the superclass is something other than a qualified name, ignore it.
    if (!extendClass.isQualifiedName()) {
      return;
    }

    // Single names are likely external, but if this is running in single-file mode, they
    // will not be in the externs, so add a weak usage.
    if (mode == Mode.SINGLE_FILE && extendClass.isName()) {
      weakUsages.add(extendClass.getString());
      return;
    }

    Node root = NodeUtil.getRootOfQualifiedName(extendClass);

    // It should always be a name. Extending this.something or
    // super.something is unlikely.
    // We only consider programmer-defined superclasses that are
    // global variables, or are defined on global variables.
    if (root.isName()) {
      String rootName = root.getString();
      Var var = t.getScope().getVar(rootName);
      if (var != null
          && (var.isLocal() || var.isExtern() || ClosureRewriteModule.isModuleExport(rootName))) {
        // "require" not needed for these
      } else {
        usages.put(extendClass.getQualifiedName(), extendClass);
      }
    }
  }

  private void maybeAddProvidedName(Node n) {
    Node name = n.getFirstChild();
    if (name.isQualifiedName()) {
      providedNames.add(name.getQualifiedName());
    }
  }

  /**
   * "var Dog = some.cute.Dog;" counts as a usage of some.cute.Dog, if it's immediately
   * inside a goog.scope function.
   */
  private void maybeAddGoogScopeUsage(Node n, Node parent) {
    Preconditions.checkState(NodeUtil.isNameDeclaration(n));
    if (n.getChildCount() == 1 && parent == googScopeBlock) {
      Node rhs = n.getFirstFirstChild();
      if (rhs != null && rhs.isQualifiedName()) {
        usages.put(rhs.getQualifiedName(), rhs);
      }
    }
  }

  /**
   * If this returns true, check for @extends and @implements annotations on this node.
   * Otherwise, it's probably an alias for an existing class, so skip those annotations.
   *
   * @return Whether the given node declares a function. True for the following forms:
   *      <li><pre>function foo() {}</pre>
   *      <li><pre>var foo = function() {};</pre>
   *      <li><pre>foo.bar = function() {};</pre>
   */
  private boolean declaresFunction(Node n) {
    if (n.isFunction()) {
      return true;
    }

    if (n.isAssign() && n.getLastChild().isFunction()) {
      return true;
    }

    if (NodeUtil.isNameDeclaration(n)
        && n.getFirstChild().hasChildren()
        && n.getFirstFirstChild().isFunction()) {
      return true;
    }

    return false;
  }

  private void maybeAddJsDocUsages(NodeTraversal t, Node n) {
    JSDocInfo info = n.getJSDocInfo();
    if (info == null) {
      return;
    }

    if (declaresFunction(n)) {
      for (JSTypeExpression expr : info.getImplementedInterfaces()) {
        maybeAddUsage(t, n, expr);
      }
      if (info.getBaseType() != null) {
        maybeAddUsage(t, n, info.getBaseType());
      }
      for (JSTypeExpression extendedInterface : info.getExtendedInterfaces()) {
        maybeAddUsage(t, n, extendedInterface);
      }
    }

    for (Node typeNode : info.getTypeNodes()) {
      maybeAddWeakUsage(t, n, typeNode);
    }
  }

  /**
   * Adds a weak usage for the given type expression (unless it references a variable that is
   * defined in the externs, in which case no goog.require() is needed). When a "weak usage"
   * is added, it means that a goog.require for that type is optional: No
   * warning is given whether the require is there or not.
   */
  private void maybeAddWeakUsage(NodeTraversal t, Node n, Node typeNode) {
    maybeAddUsage(t, n, typeNode, false, Predicates.<Node>alwaysTrue());
  }

  /**
   * Adds a usage for the given type expression (unless it references a variable that is
   * defined in the externs, in which case no goog.require() is needed). When a usage is
   * added, it means that there should be a goog.require for that type.
   */
  private void maybeAddUsage(NodeTraversal t, Node n, final JSTypeExpression expr) {
    // Just look at the root node, don't traverse.
    Predicate<Node> pred =
        new Predicate<Node>() {
          @Override
          public boolean apply(Node n) {
            return n == expr.getRoot();
          }
        };
    maybeAddUsage(t, n, expr.getRoot(), true, pred);
  }

  private void maybeAddUsage(
      final NodeTraversal t,
      final Node n,
      Node rootTypeNode,
      final boolean markStrongUsages,
      Predicate<Node> pred) {
    Visitor visitor =
        new Visitor() {
          @Override
          public void visit(Node typeNode) {
            if (typeNode.isString()) {
              String typeString = typeNode.getString();
              if (mode == Mode.SINGLE_FILE && !typeString.contains(".")) {
                // If using a single-name type, it's probably something like Error, which we
                // don't have externs for.
                weakUsages.add(typeString);
                return;
              }
              String rootName = Splitter.on('.').split(typeString).iterator().next();
              Var var = t.getScope().getVar(rootName);
              if ((var == null || !var.isExtern())
                  && !ClosureRewriteModule.isModuleExport(rootName)) {
                if (markStrongUsages) {
                  usages.put(typeString, n);
                } else {
                  // If we're not adding strong usages here, add weak usages for the prefixes of the
                  // namespace, like we do for GETPROP nodes. Otherwise we get an extra require
                  // warning for cases like:
                  //
                  //     goog.require('foo.bar.SomeService');
                  //
                  //     /** @constructor @extends {foo.bar.SomeService.Handler} */
                  //     var MyHandler = function() {};
                  addWeakUsagesOfAllPrefixes(typeString);
                }
              } else {
                // Even if the root namespace is in externs, add a weak usage because the full
                // namespace may still be goog.provided.
                weakUsages.add(typeString);
              }
            }
          }
        };

    NodeUtil.visitPreOrder(rootTypeNode, visitor, pred);
  }
}
