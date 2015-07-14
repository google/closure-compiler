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

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.NodeUtil.Visitor;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

  private final Set<String> constructors = new HashSet<>();
  private final Map<String, Node> requires = new HashMap<>();

  // Adding an entry to usages indicates that the name is used and should be required.
  private final Map<String, Node> usages = new HashMap<>();

  // Adding an entry to weakUsages indicates that the name is used, but in a way which may not
  // require a goog.require, such as in a @type annotation. If the only usages of a name are
  // in weakUsages, don't give a missingRequire warning, nor an extraRequire warning.
  private final Map<String, Node> weakUsages = new HashMap<>();

  // Warnings
  static final DiagnosticType MISSING_REQUIRE_WARNING =
      DiagnosticType.disabled(
          "JSC_MISSING_REQUIRE_WARNING", "''{0}'' used but not goog.require''d");

  static final DiagnosticType EXTRA_REQUIRE_WARNING = DiagnosticType.disabled(
      "JSC_EXTRA_REQUIRE_WARNING",
      "''{0}'' goog.require''d but not used");

  private static final Set<String> DEFAULT_EXTRA_NAMESPACES = ImmutableSet.of(
    "goog.testing.asserts", "goog.testing.jsunit");

  CheckRequiresForConstructors(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.codingConvention = compiler.getCodingConvention();
  }

  /**
   * Uses Collections of new and goog.require nodes to create a compiler warning
   * for each new class name without a corresponding goog.require().
   */
  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseRoots(compiler, this, externs, root);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    new NodeTraversal(compiler, this).traverse(scriptRoot);
  }

  // Return true if the name is a class name (starts with an uppercase
  // character, but is not in all caps).
  private static boolean isClassName(String name) {
    return (name != null && name.length() > 1
            && Character.isUpperCase(name.charAt(0))
            && !name.equals(name.toUpperCase()));
  }

  // Return the shortest prefix of the className that refers to a class,
  // or null if no part refers to a class.
  private static String getOutermostClassName(String className) {
    for (String part : Splitter.on('.').split(className)) {
      if (isClassName(part)) {
        return className.substring(0,
            className.indexOf(part) + part.length());
      }
    }

    return null;
  }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return parent == null || !parent.isScript() || !t.getInput().isExtern();
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      maybeAddJsDocUsages(t, n);
      switch (n.getType()) {
        case Token.ASSIGN:
        case Token.VAR:
          maybeAddConstructor(n);
          break;
        case Token.FUNCTION:
          // Exclude function expressions.
          if (NodeUtil.isStatement(n)) {
            maybeAddConstructor(n);
          }
          break;
        case Token.GETPROP:
          visitGetProp(n);
          break;
        case Token.CALL:
          visitCallNode(n, parent);
          break;
        case Token.SCRIPT:
          visitScriptNode(t);
          break;
        case Token.NEW:
          visitNewNode(t, n);
          break;
        case Token.CLASS:
          visitClassNode(n);
      }
    }

    private void visitScriptNode(NodeTraversal t) {
      Set<String> classNames = new HashSet<>();

      // For every usage, check that there is a goog.require, and warn if not.
      for (Map.Entry<String, Node> entry : usages.entrySet()) {
        String className = entry.getKey();
        Node node = entry.getValue();

        String outermostClassName = getOutermostClassName(className);
        // The parent namespace is also checked as part of the requires so that classes
        // used by goog.module are still checked properly. This may cause missing requires
        // to be missed but in practice that should happen rarely.
        String nonNullClassName = outermostClassName != null ? outermostClassName : className;
        String parentNamespace = null;
        int separatorIndex = nonNullClassName.lastIndexOf('.');
        if (separatorIndex > 0) {
          parentNamespace = nonNullClassName.substring(0, separatorIndex);
        }
        boolean notProvidedByConstructors =
            (constructors == null
            || (!constructors.contains(className) && !constructors.contains(outermostClassName)));
        boolean notProvidedByRequires =
            (requires == null || (!requires.containsKey(className)
                                  && !requires.containsKey(outermostClassName)
                                  && !requires.containsKey(parentNamespace)));
        if (notProvidedByConstructors && notProvidedByRequires
            && !classNames.contains(className)) {
          // TODO(mknichel): If the symbol is not explicitly provided, find the next best
          // symbol from the provides in the same file.
          compiler.report(t.makeError(node, MISSING_REQUIRE_WARNING, className));
          classNames.add(className);
        }
      }

      // For every goog.require, check that there is a usage (in either usages or weakUsages)
      // and warn if there is not.
      for (Map.Entry<String, Node> entry : requires.entrySet()) {
        String require = entry.getKey();
        Node call = entry.getValue();
        Node parent = call.getParent();
        if (parent.isAssign()) {
          // var baz = goog.require('foo.bar.baz');
          // Assume that the var 'baz' is used somewhere, and don't warn.
          continue;
        }
        if (!usages.containsKey(require) && !weakUsages.containsKey(require)) {
          reportExtraRequireWarning(call, require);
        }
      }

      // for the next script, if there is one, we don't want the new, ctor, and
      // require nodes to spill over.
      this.usages.clear();
      this.weakUsages.clear();
      this.requires.clear();
      this.constructors.clear();
    }

    private void reportExtraRequireWarning(Node call, String require) {
      if (DEFAULT_EXTRA_NAMESPACES.contains(require)) {
        return;
      }
      JSDocInfo jsDoc = call.getJSDocInfo();
      if (jsDoc != null && jsDoc.getSuppressions().contains("extraRequire")) {
        // There is a @suppress {extraRequire} on the call node. Even though the compiler generally
        // doesn't understand @suppress in that position, respect it in this case,
        // since lots of people put it there to suppress the closure-linter's extraRequire check.
        return;
      }
      compiler.report(JSError.make(call, EXTRA_REQUIRE_WARNING, require));
    }

    private void visitCallNode(Node call, Node parent) {
      String required = codingConvention.extractClassNameIfRequire(call, parent);
      if (required != null) {
        requires.put(required, call);
      }

      Node callee = call.getFirstChild();
      if (callee.isName()) {
        weakUsages.put(callee.getString(), callee);
      }
    }

    private void visitGetProp(Node getprop) {
      // For "foo.bar.baz.qux" add weak usages for "foo.bar.baz.qux", foo.bar.baz",
      // "foo.bar", and "foo" because those might all be goog.provide'd in different files,
      // so it doesn't make sense to require the user to goog.require all of them.
      for (; getprop != null; getprop = getprop.getFirstChild()) {
        weakUsages.put(getprop.getQualifiedName(), getprop);
      }
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
      if (var != null && (var.isLocal() || var.isExtern())) {
        return;
      }
      usages.put(qNameNode.getQualifiedName(), newNode);

      // for "new foo.bar.Baz.Qux" add weak usages for "foo.bar.Baz", "foo.bar", and "foo"
      // because those might be goog.provide'd from a different file than foo.bar.Baz.Qux,
      // so it doesn't make sense to require the user to goog.require all of them.
      for (; qNameNode != null; qNameNode = qNameNode.getFirstChild()) {
        weakUsages.put(qNameNode.getQualifiedName(), qNameNode);
      }
    }

    private void visitClassNode(Node classNode) {
      String name = NodeUtil.getClassName(classNode);
      if (name != null) {
        constructors.add(name);
      }
      Node extendClass = classNode.getFirstChild().getNext();
      if (extendClass.isQualifiedName()) {
        usages.put(extendClass.getQualifiedName(), extendClass);
      }
    }

    private void maybeAddConstructor(Node n) {
      JSDocInfo info = n.getJSDocInfo();
      if (info != null) {
        String ctorName = n.getFirstChild().getQualifiedName();
        if (info.isConstructor() || info.isInterface()) {
          constructors.add(ctorName);
        } else {
          JSTypeExpression typeExpr = info.getType();
          if (typeExpr != null) {
            Node typeExprRoot = typeExpr.getRoot();
            if (typeExprRoot.isFunction() && typeExprRoot.getFirstChild().isNew()) {
              constructors.add(ctorName);
            }
          }
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

      if (NodeUtil.isNameDeclaration(n) && n.getFirstChild().hasChildren()
          && n.getFirstChild().getFirstChild().isFunction()) {
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
      maybeAddUsage(t, n, typeNode, this.weakUsages, Predicates.<Node>alwaysTrue());
    }

    /**
     * Adds a usage for the given type expression (unless it references a variable that is
     * defined in the externs, in which case no goog.require() is needed). When a usage is
     * added, it means that there should be a goog.require for that type.
     */
    private void maybeAddUsage(NodeTraversal t, Node n, final JSTypeExpression expr) {
      // Just look at the root node, don't traverse.
      Predicate<Node> pred = new Predicate<Node>() {
        public boolean apply(Node n) {
          return n == expr.getRoot();
        }
      };
      maybeAddUsage(t, n, expr.getRoot(), this.usages, pred);
    }

    private void maybeAddUsage(final NodeTraversal t, final Node n, Node rootTypeNode,
        final Map<String, Node> usagesMap, Predicate<Node> pred) {
      Visitor visitor = new Visitor() {
        public void visit(Node typeNode) {
          if (typeNode.isString()) {
            String typeString = typeNode.getString();
            String rootName = Splitter.on('.').split(typeString).iterator().next();
            Var var = t.getScope().getVar(rootName);
            if (var == null || !var.isExtern()) {
              usagesMap.put(typeString, n);

              // Regardless of whether we're adding a weak or strong usage here, add weak usages for
              // the prefixes of the namespace, like we do for GETPROP nodes. Otherwise we get an
              // extra require warning for cases like:
              //
              //     goog.require('foo.bar.SomeService');
              //
              //     /** @constructor @extends {foo.bar.SomeService.Handler} */
              //     var MyHandler = function() {};
              Node getprop = NodeUtil.newQName(compiler, typeString);
              getprop.useSourceInfoIfMissingFromForTree(typeNode);
              visitGetProp(getprop);
            }
          }
        }
      };

      NodeUtil.visitPreOrder(rootTypeNode, visitor, pred);
    }
}
