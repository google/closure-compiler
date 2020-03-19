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

package com.google.javascript.jscomp.lint;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.HotSwapCompilerPass;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.NodeUtil.Visitor;
import com.google.javascript.jscomp.Var;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Walks the AST looking for usages of qualified names, and 'goog.require's of those names. Then,
 * reconciles the two lists, and reports warning for any unnecessary require statements.
 */
public class CheckExtraRequires implements HotSwapCompilerPass, NodeTraversal.Callback {
  private final AbstractCompiler compiler;

  private static final Splitter DOT_SPLITTER = Splitter.on('.');
  private static final Joiner DOT_JOINER = Joiner.on('.');

  // Keys are the local name of a required namespace. Values are the goog.require CALL node.
  private final Map<String, Node> requires = new HashMap<>();

  // Adding an entry to usages indicates that the name (either a fully qualified or local name)
  // is used and can be required.
  private final Set<String> usages = new HashSet<>();

  // The body of the goog.scope function, if any.
  @Nullable private Node googScopeBlock;

  public static final DiagnosticType EXTRA_REQUIRE_WARNING =
      DiagnosticType.disabled(
          "JSC_EXTRA_REQUIRE_WARNING", "extra require: ''{0}'' is never referenced in this file");

  // TODO(b/130215517): This should eventually be removed and exceptions supressed
  private static final ImmutableSet<String> DEFAULT_EXTRA_NAMESPACES =
      ImmutableSet.of(
          "goog.testing.asserts", "goog.testing.jsunit", "goog.testing.JsTdTestCaseAdapter");

  public CheckExtraRequires(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    reset();
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    // TODO(joeltine): Remove this and properly handle hot swap passes. See
    // b/28869281 for context.
    reset();
    NodeTraversal.traverse(compiler, scriptRoot, this);
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

  private String extractNamespaceIfForwardDeclare(Node call) {
    return extractNamespace(call, "goog.forwardDeclare");
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

    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    maybeAddJsDocUsages(t, n);
    switch (n.getToken()) {
      case VAR:
      case LET:
      case CONST:
        maybeAddGoogScopeUsage(t, n, parent);
        break;
      case NAME:
        if (!NodeUtil.isLValue(n) && !parent.isGetProp() && !parent.isImportSpec()) {
          visitQualifiedName(t, n, parent);
        }
        break;
      case GETPROP:
        // If parent is a GETPROP, they will handle the weak usages.
        if (!parent.isGetProp() && n.isQualifiedName()) {
          visitQualifiedName(t, n, parent);
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
    this.requires.clear();
    this.googScopeBlock = null;
  }

  private void visitScriptNode() {
    // For every goog.require, check that there is a usage and warn if there is not.
    for (Map.Entry<String, Node> entry : requires.entrySet()) {
      String require = entry.getKey();
      Node call = entry.getValue();
      if (!usages.contains(require)) {
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

  private void visitForwardDeclare(String namespace, Node forwardDeclareCall, Node parent) {
    visitGoogRequire(namespace, forwardDeclareCall, parent);
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
    String declare = extractNamespaceIfForwardDeclare(call);
    if (declare != null) {
      visitForwardDeclare(declare, call, parent);
      return;
    }
    String provided = extractNamespaceIfProvide(call);
    if (provided != null) {
      return;
    }

    Node callee = call.getFirstChild();
    if (callee.matchesQualifiedName("goog.module.get") && call.getSecondChild().isString()) {
      usages.add(call.getSecondChild().getString());
    }

    usages.add(callee.getQualifiedName());
  }

  private void addUsagesOfAllPrefixes(String qualifiedName) {
    // For "foo.bar.baz.qux" add usages for "foo.bar.baz.qux", "foo.bar.baz",
    // "foo.bar", and "foo" because any of thoes might be a require that
    // we need to include.
    for (int i = qualifiedName.indexOf('.'); i != -1; i = qualifiedName.indexOf('.', i + 1)) {
      String prefix = qualifiedName.substring(0, i);
      usages.add(prefix);
    }
    usages.add(qualifiedName);
  }

  private void visitQualifiedName(NodeTraversal t, Node n, Node parent) {
    checkState(n.isName() || n.isGetProp() || n.isStringKey(), n);
    String qualifiedName = n.isStringKey() ? n.getString() : n.getQualifiedName();
    addUsagesOfAllPrefixes(qualifiedName);
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
    if (var != null && var.getSourceFile() == newNode.getStaticSourceFile()) {
      return;
    }
    usages.add(qNameNode.getQualifiedName());

    // for "new foo.bar.Baz.Qux" add weak usages for "foo.bar.Baz", "foo.bar", and "foo"
    // because those might be goog.provide'd from a different file than foo.bar.Baz.Qux,
    // so it doesn't make sense to require the user to goog.require all of them.
    for (; qNameNode != null; qNameNode = qNameNode.getFirstChild()) {
      usages.add(qNameNode.getQualifiedName());
    }
  }

  private void visitClassNode(NodeTraversal t, Node classNode) {
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
      if (var != null && var.isLocal()) {
        // "require" not needed for these
      } else {
        List<String> classNames = getClassNames(extendClass.getQualifiedName());
        String outermostClassName = Iterables.getFirst(classNames, extendClass.getQualifiedName());
        usages.add(outermostClassName);
      }
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
          if (var == null || var.isGlobal()) {
            usages.add(rhs.getQualifiedName());
          }
        }
      }
    }
  }

  private void maybeAddJsDocUsages(NodeTraversal t, Node n) {
    JSDocInfo info = n.getJSDocInfo();
    if (info == null) {
      return;
    }

    for (JSTypeExpression expr : info.getImplementedInterfaces()) {
      maybeAddUsage(t, n, expr);
    }
    if (info.getBaseType() != null) {
      maybeAddUsage(t, n, info.getBaseType());
    }
    for (JSTypeExpression extendedInterface : info.getExtendedInterfaces()) {
      maybeAddUsage(t, n, extendedInterface);
    }

    for (Node typeNode : info.getTypeNodes()) {
      maybeAddUsage(t, n, typeNode, Predicates.alwaysTrue());
    }
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
    maybeAddUsage(t, n, expr.getRoot(), pred);
  }

  private void maybeAddUsage(
      final NodeTraversal t,
      final Node n,
      Node rootTypeNode,
      Predicate<Node> pred) {
    Visitor visitor =
        new Visitor() {
          @Override
          public void visit(Node typeNode) {
            if (typeNode.isString()) {
              String typeString = typeNode.getString();
              addUsagesOfAllPrefixes(typeString);
            }
          }
        };

    NodeUtil.visitPreOrder(rootTypeNode, visitor, pred);
  }
}
