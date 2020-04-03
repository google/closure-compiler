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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.NodeUtil.Visitor;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Walks the AST looking for usages of qualified names, and 'goog.require's of those names. Then,
 * reconciles the two lists, and reports warning for any discrepancies.
 *
 * <p>This pass is deprecated, and it's functionality is being moved to other passes, such as:
 *
 * <ul>
 *   <li>The {@link com.google.javascript.jscomp.lint.CheckExtraRequires} pass for checking extra
 *       requires.
 * </ul>
 *
 * <p>The rules on when a warning is reported are:
 *
 * <ul>
 *   <li>Type is referenced in code → goog.require is required (missingRequires check fails if it's
 *       not there)
 *   <li>Type is referenced in an @extends or @implements → goog.require is required
 *       (missingRequires check fails if it's not there)
 *   <li>Type is referenced in other JsDoc (@type etc) → goog.require is optional (don't warn,
 *       regardless of if it is there)
 * </ul>
 */
public class CheckMissingAndExtraRequires implements HotSwapCompilerPass, NodeTraversal.Callback {
  private final AbstractCompiler compiler;
  private final CodingConvention codingConvention;

  private static final Splitter DOT_SPLITTER = Splitter.on('.');
  private static final Joiner DOT_JOINER = Joiner.on('.');

  private final Set<String> providedNames = new HashSet<>();

  // Keys are the local name of a required namespace. Values are the goog.require CALL node.
  private final Map<String, Node> requires = new HashMap<>();

  // Adding an entry to usages indicates that the name is used and should be required.
  private final Map<String, Node> usages = new HashMap<>();

  // Adding an entry to weakUsages indicates that the name is used, but in a way which may not
  // require a goog.require, such as in a @type annotation. If the only usages of a name are
  // in weakUsages, don't give a missingRequire warning, nor an extraRequire warning.
  private final Set<String> weakUsages = new HashSet<>();

  // The body of the goog.scope function, if any.
  @Nullable private Node googScopeBlock;

  public static final DiagnosticType MISSING_REQUIRE_WARNING =
      DiagnosticType.disabled("JSC_MISSING_REQUIRE_WARNING", "missing require: ''{0}''");

  static final DiagnosticType MISSING_REQUIRE_FOR_GOOG_SCOPE =
      DiagnosticType.disabled("JSC_MISSING_REQUIRE_FOR_GOOG_SCOPE", "missing require: ''{0}''");

  // TODO(tbreisacher): Remove this and just use MISSING_REQUIRE_WARNING.
  public static final DiagnosticType MISSING_REQUIRE_STRICT_WARNING =
      DiagnosticType.disabled("JSC_MISSING_REQUIRE_STRICT_WARNING", "missing require: ''{0}''");

  CheckMissingAndExtraRequires(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.codingConvention = compiler.getCodingConvention();
  }

  @Override
  public void process(Node externs, Node root) {
    reset();
    NodeTraversal.traverseRoots(compiler, this, externs, root);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    // TODO(joeltine): Remove this and properly handle hot swap passes. See
    // b/28869281 for context.
    reset();
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }

  // Return true if the name is a class name (starts with an uppercase
  // character, but is not in all-caps).
  private static boolean isClassName(String name) {
    return isClassOrConstantName(name) && !name.equals(name.toUpperCase(Locale.ROOT));
  }

  // Return true if the name looks like a class name or a constant name.
  private static boolean isClassOrConstantName(String name) {
    return name != null && name.length() > 1 && Character.isUpperCase(name.charAt(0));
  }

  // Return the shortest prefix of the className that refers to a class,
  // or null if no part refers to a class.
  private static ImmutableList<String> getClassNames(String qualifiedName) {
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
  private String extractNamespace(Node call, String... primitiveNames) {
    Node callee = call.getFirstChild();
    if (!callee.isGetProp()) {
      return null;
    }
    for (String primitiveName : primitiveNames) {
      if (callee.matchesQualifiedName(primitiveName)) {
        Node target = callee.getNext();
        if (target != null && target.isString()) {
          return target.getString();
        }
      }
    }
    return null;
  }

  private String extractNamespaceIfRequire(Node call) {
    return extractNamespace(call, "goog.require", "goog.requireType");
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
    switch (n.getToken()) {
      case ASSIGN:
        maybeAddProvidedName(n);
        break;
      case VAR:
      case LET:
      case CONST:
        maybeAddProvidedName(n);
        maybeAddGoogScopeUsage(t, n, parent);
        break;
      case FUNCTION:
        // Exclude function expressions.
        if (NodeUtil.isStatement(n)) {
          maybeAddProvidedName(n);
        }
        break;
      case NAME:
        if (!NodeUtil.isLValue(n) && !parent.isGetProp() && !parent.isImportSpec()) {
          visitQualifiedName(n);
        }
        break;
      case GETPROP:
        // If parent is a GETPROP, they will handle the weak usages.
        if (!parent.isGetProp() && n.isQualifiedName()) {
          visitQualifiedName(n);
        }
        break;
      case CALL:
        visitCallNode(t, n, parent);
        break;
      case SCRIPT:
        visitScriptNode();
        reset();
        break;
      case NEW:
        visitNewNode(t, n);
        break;
      case CLASS:
        visitClassNode(t, n);
        break;
      case IMPORT:
        visitImportNode(n);
        break;
      default:
        break;
    }
  }

  private void reset() {
    this.usages.clear();
    this.weakUsages.clear();
    this.requires.clear();
    this.providedNames.clear();
    this.googScopeBlock = null;
  }

  private void visitScriptNode() {
    Set<String> namespaces = new HashSet<>();

    // For every usage, check that there is a goog.require, and warn if not.
    for (Map.Entry<String, Node> entry : usages.entrySet()) {
      String namespace = entry.getKey();
      Node node = entry.getValue();
      boolean isMissing = isMissingRequire(namespace, node);
      if (isMissing
          && (namespace.endsWith(".call")
              || namespace.endsWith(".apply")
              || namespace.endsWith(".bind"))) {
        // assume that the user is calling the corresponding built in function and only look for
        // imports 'above' it.
        String namespaceMinusApply = namespace.substring(0, namespace.lastIndexOf('.'));
        isMissing = isMissingRequire(namespaceMinusApply, node);
      }
      if (isMissing && !namespaces.contains(namespace)) {
        // TODO(mknichel): If the symbol is not explicitly provided, find the next best
        // symbol from the provides in the same file.
        if (node.isCall()) {
          String defaultName =
              namespace.lastIndexOf('.') > 0
                  ? namespace.substring(0, namespace.lastIndexOf('.'))
                  : namespace;
          String nameToReport = Iterables.getFirst(getClassNames(namespace), defaultName);
          compiler.report(JSError.make(node, MISSING_REQUIRE_STRICT_WARNING, nameToReport));
        } else if (node.getParent().isName()
            && node.getParent().getGrandparent() == googScopeBlock) {
          compiler.report(JSError.make(node, MISSING_REQUIRE_FOR_GOOG_SCOPE, namespace));
        } else {
          if (node.isGetProp() && !node.getParent().isClass()) {
            compiler.report(JSError.make(node, MISSING_REQUIRE_STRICT_WARNING, namespace));
          } else {
            compiler.report(JSError.make(node, MISSING_REQUIRE_WARNING, namespace));
          }
        }
        namespaces.add(namespace);
      }
    }
  }

  /** Returns true if the given namespace is not satisfied by any {@code goog.provide}. */
  private boolean isMissingRequire(String namespace, Node node) {
    if (namespace.startsWith("goog.global.")
        // Most functions in base.js are goog.someName, but
        // goog.module.{get,declareLegacyNamespace} are the exceptions, so just
        // check for them explicitly.
        || namespace.equals("goog.module.get")
        || namespace.equals("goog.module.declareLegacyNamespace")) {
      return false;
    }

    JSDocInfo info = NodeUtil.getBestJSDocInfo(NodeUtil.getEnclosingStatement(node));
    if (info != null && info.getSuppressions().contains("missingRequire")) {
      return false;
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
      return false;
    }

    boolean providedByConstructors =
        providedNames.contains(namespace) || providedNames.contains(parentNamespace);
    boolean providedByRequires =
        requires.containsKey(namespace) || requires.containsKey(parentNamespace);

    for (String className : classNames) {
      if (providedNames.contains(className)) {
        providedByConstructors = true;
        break;
      }
      if (requires.containsKey(className)) {
        providedByRequires = true;
        break;
      }
    }
    return !providedByRequires && !providedByConstructors;
  }

  /**
   * @param localName The name that should be used in this file.
   *     <pre>
   * Require style                        | localName
   * -------------------------------------|----------
   * goog.require('foo.bar');             | foo.bar
   * var bar = goog.require('foo.bar');   | bar
   * var {qux} = goog.require('foo.bar'); | qux
   * import {qux} from 'foo.bar';         | qux
   * </pre>
   */
  private void visitRequire(String localName, Node node) {
    if (!requires.containsKey(localName)) {
      requires.put(localName, node);
    }

    // For goog.require('example.Outer.Inner'), add example.Outer as well.
    for (String className : getClassNames(localName)) {
      requires.put(className, node);
    }
  }

  private void visitImportNode(Node importNode) {
    Node defaultImport = importNode.getFirstChild();
    if (defaultImport.isName()) {
      visitRequire(defaultImport.getString(), importNode);
    }
    Node namedImports = defaultImport.getNext();
    if (namedImports.isImportSpecs()) {
      for (Node importSpec : namedImports.children()) {
        visitRequire(importSpec.getLastChild().getString(), importNode);
      }
    }
  }

  private void visitGoogRequire(String namespace, Node googRequireCall, Node parent) {
    if (parent.isName()) {
      visitRequire(parent.getString(), googRequireCall);
    } else if (parent.isDestructuringLhs() && parent.getFirstChild().isObjectPattern()) {
      if (parent.getFirstChild().hasChildren()) {
        for (Node stringKey : parent.getFirstChild().children()) {
          Node importName = stringKey.getFirstChild();
          if (!importName.isName()) {
            // invalid reported elsewhere
            continue;
          }
          visitRequire(importName.getString(), importName);
        }
      } else {
        visitRequire(namespace, googRequireCall);
      }
    } else {
      visitRequire(namespace, googRequireCall);
    }
  }

  private void visitCallNode(NodeTraversal t, Node call, Node parent) {
    String required = extractNamespaceIfRequire(call);
    if (required != null) {
      visitGoogRequire(required, call, parent);
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
        if (var == null || (!var.isExtern() && var.isGlobal())) {
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

  private void visitQualifiedName(Node n) {
    checkState(n.isName() || n.isGetProp() || n.isStringKey(), n);
    String qualifiedName = n.isStringKey() ? n.getString() : n.getQualifiedName();
    addWeakUsagesOfAllPrefixes(qualifiedName);
  }

  private void visitNewNode(NodeTraversal t, Node newNode) {
    Node qNameNode = newNode.getFirstChild();

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
    if (var != null && (var.isExtern() || var.getSourceFile() == newNode.getStaticSourceFile())) {
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

    Node root = NodeUtil.getRootOfQualifiedName(extendClass);

    // It should always be a name. Extending this.something or
    // super.something is unlikely.
    // We only consider programmer-defined superclasses that are
    // global variables, or are defined on global variables.
    if (root.isName()) {
      String rootName = root.getString();
      Var var = t.getScope().getVar(rootName);
      if (var != null && (var.isLocal() || var.isExtern())) {
        // "require" not needed for these
      } else {
        List<String> classNames = getClassNames(extendClass.getQualifiedName());
        String outermostClassName = Iterables.getFirst(classNames, extendClass.getQualifiedName());
        usages.put(outermostClassName, extendClass);
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
   * "var Dog = some.cute.Dog;" counts as a usage of some.cute.Dog, if it's immediately inside a
   * goog.scope function.
   */
  private void maybeAddGoogScopeUsage(NodeTraversal t, Node n, Node parent) {
    checkState(NodeUtil.isNameDeclaration(n));
    if (n.hasOneChild() && parent == googScopeBlock) {
      Node rhs = n.getFirstFirstChild();
      if (rhs != null && rhs.isQualifiedName()) {
        Node root = NodeUtil.getRootOfQualifiedName(rhs);
        if (root.isName()) {
          Var var = t.getScope().getVar(root.getString());
          if (var == null || (var.isGlobal() && !var.isExtern())) {
            usages.put(rhs.getQualifiedName(), rhs);
          }
        }
      }
    }
  }

  /**
   * If this returns true, check for @extends and @implements annotations on this node. Otherwise,
   * it's probably an alias for an existing class, so skip those annotations.
   *
   * @return Whether the given node declares a function. True for the following forms:
   *     <li>
   *         <pre>function foo() {}</pre>
   *     <li>
   *         <pre>var foo = function() {};</pre>
   *     <li>
   *         <pre>foo.bar = function() {};</pre>
   */
  private boolean declaresFunctionOrClass(Node n) {
    if (n.isFunction() || n.isClass()) {
      return true;
    }

    if (n.isAssign() && (n.getLastChild().isFunction() || n.getLastChild().isClass())) {
      return true;
    }

    if (NodeUtil.isNameDeclaration(n)
        && n.getFirstChild().hasChildren()
        && (n.getFirstFirstChild().isFunction() || n.getFirstFirstChild().isClass())) {
      return true;
    }

    return false;
  }

  private void maybeAddJsDocUsages(NodeTraversal t, Node n) {
    JSDocInfo info = n.getJSDocInfo();
    if (info == null) {
      return;
    }

    if (declaresFunctionOrClass(n)) {
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
   * defined in the externs, in which case no goog.require() is needed). When a "weak usage" is
   * added, it means that a goog.require for that type is optional: No warning is given whether the
   * require is there or not.
   */
  private void maybeAddWeakUsage(NodeTraversal t, Node n, Node typeNode) {
    maybeAddUsage(t, n, typeNode, false, Predicates.alwaysTrue());
  }

  /**
   * Adds a usage for the given type expression (unless it references a variable that is defined in
   * the externs, in which case no goog.require() is needed). When a usage is added, it means that
   * there should be a goog.require for that type.
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
              String rootName = Splitter.on('.').split(typeString).iterator().next();
              Var var = t.getScope().getVar(rootName);
              if (var == null || (var.isGlobal() && !var.isExtern())) {
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
                // Even if the root namespace is in externs, add weak usages because the full
                // namespace may still be goog.provided.
                addWeakUsagesOfAllPrefixes(typeString);
              }
            }
          }
        };

    NodeUtil.visitPreOrder(rootTypeNode, visitor, pred);
  }
}
