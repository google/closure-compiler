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

import com.google.common.base.Predicates;
import com.google.javascript.jscomp.NodeTraversal.ExternsSkippingCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.Node;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Inline aliases created by exports of modules before type checking.
 *
 * <p>The old type inference doesn't deal as well with aliased types as with unaliased ones, such as
 * in extends clauses (@extends {alias}) and templated types (alias<T>). This pass inlines these
 * aliases to make type checking's job easier.
 *
 * <p>This alias inliner is not very aggressive. It will only inline explicitly const aliases but
 * not effectively const ones (for example ones that are only ever assigned a value once). This is
 * done to be conservative since it's not a good idea to be making dramatic AST changes before type
 * checking. There is a more aggressive alias inliner that runs at the start of optimization.
 *
 * @author blickly@gmail.com (Ben Lickly)
 */
final class InlineAliases implements CompilerPass {

  static final DiagnosticType ALIAS_CYCLE =
      DiagnosticType.error("JSC_ALIAS_CYCLE", "Alias path contains a cycle: {0} to {1}");

  private final AbstractCompiler compiler;
  private final Map<String, String> aliases = new LinkedHashMap<>();
  private GlobalNamespace namespace;

  InlineAliases(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    namespace = new GlobalNamespace(compiler, externs, root);
    NodeTraversal.traverseRoots(compiler, new AliasesCollector(), externs, root);
    NodeTraversal.traverseRoots(compiler, new AliasesInliner(), externs, root);
  }

  private class AliasesCollector extends ExternsSkippingCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case VAR:
        case CONST:
        case LET:
          if (n.hasOneChild() && t.inGlobalScope()) {
            visitAliasDefinition(n.getFirstChild(), NodeUtil.getBestJSDocInfo(n.getFirstChild()));
          }
          break;
        case ASSIGN:
          if (parent != null && parent.isExprResult() && t.inGlobalScope()) {
            visitAliasDefinition(n.getFirstChild(), n.getJSDocInfo());
          }
          break;
        default:
          break;
      }
    }

    /**
     * Maybe record that given lvalue is an alias of the qualified name on its rhs.
     * Note that since we are doing a post-order traversal, any previous aliases contained in
     * the rhs will have already been substituted by the time we record the new alias.
     */
    private void visitAliasDefinition(Node lhs, JSDocInfo info) {
      if (isDeclaredConst(lhs, info) && (info == null || !info.hasTypeInformation())
          && lhs.isQualifiedName()) {
        Node rhs = NodeUtil.getRValueOfLValue(lhs);
        if (rhs != null && rhs.isQualifiedName()) {
          GlobalNamespace.Name lhsName = namespace.getOwnSlot(lhs.getQualifiedName());
          GlobalNamespace.Name rhsName = namespace.getOwnSlot(rhs.getQualifiedName());
          if (lhsName != null
              && lhsName.calculateInlinability().shouldInlineUsages()
              && rhsName != null
              && rhsName.calculateInlinability().shouldInlineUsages()
              && !isPrivate(rhsName.getDeclaration().getNode())) {
            aliases.put(lhs.getQualifiedName(), rhs.getQualifiedName());
          }
        }
      }
    }

    private boolean isDeclaredConst(Node lhs, JSDocInfo info) {
      if (info != null && info.hasConstAnnotation()) {
        return true;
      }
      return lhs.getParent().isConst();
    }

    private boolean isPrivate(Node nameNode) {
      if (nameNode.isQualifiedName()
          && compiler.getCodingConvention().isPrivate(nameNode.getQualifiedName())) {
        return true;
      }
      JSDocInfo info = NodeUtil.getBestJSDocInfo(nameNode);
      return info != null && info.getVisibility().equals(Visibility.PRIVATE);
    }
  }

  private class AliasesInliner extends ExternsSkippingCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case NAME:
        case GETPROP:
          if (n.isQualifiedName() && aliases.containsKey(n.getQualifiedName())) {
            String leftmostName = NodeUtil.getRootOfQualifiedName(n).getString();
            Var v = t.getScope().getVar(leftmostName);
            if (v != null && v.isLocal()) {
              // Shadow of alias. Don't rewrite
              return;
            }
            if (NodeUtil.isNameDeclOrSimpleAssignLhs(n, parent)) {
              // Alias definition. Don't rewrite
              return;
            }

            Node newNode = NodeUtil.newQName(compiler, resolveAlias(n.getQualifiedName(), n));

            // If n is get_prop like "obj.foo" then newNode should use only location of foo, not
            // obj.foo.
            newNode.useSourceInfoFromForTree(n.isGetProp() ? n.getLastChild() : n);
            // Similarly if n is get_prop like "obj.foo" we should index only foo. obj should not
            // be indexed as it's invisible to users.
            if (newNode.isGetProp()) {
              newNode.getFirstChild().makeNonIndexableRecursive();
            }
            parent.replaceChild(n, newNode);
            t.reportCodeChange();
          }
          break;
        default:
          break;
      }
      maybeRewriteJsdoc(n.getJSDocInfo());
    }

    /**
     * Use the alias table to look up the resolved name of the given alias. If the result is also an
     * alias repeat until the real name is resolved.
     * @param n
     */
    private String resolveAlias(String name, Node n) {
      Set<String> aliasPath = new LinkedHashSet<>();
      while (aliases.containsKey(name)) {
        if (!aliasPath.add(name)) {
          compiler.report(JSError.make(n, ALIAS_CYCLE, aliasPath.toString(), name));

          // Cut the cycle so that it doesn't get reported more than once.
          aliases.remove(name);
          break;
        }

        name = aliases.get(name);
      }
      return name;
    }

    private void maybeRewriteJsdoc(JSDocInfo info) {
      if (info == null) {
        return;
      }
      for (Node typeNode : info.getTypeNodes()) {
        NodeUtil.visitPreOrder(typeNode, fixJsdocTypeNodes, Predicates.alwaysTrue());
      }
    }

    private final NodeUtil.Visitor fixJsdocTypeNodes =
        new NodeUtil.Visitor() {
          @Override
          public void visit(Node aliasReference) {
            if (!aliasReference.isString()) {
              return;
            }
            String fullTypeName = aliasReference.getString();
            int dotIndex = 0;
            do {
              dotIndex = fullTypeName.indexOf('.', dotIndex + 1);
              String aliasName =
                  dotIndex == -1 ? fullTypeName : fullTypeName.substring(0, dotIndex);
              if (aliases.containsKey(aliasName)) {
                String replacement =
                    resolveAlias(aliasName, aliasReference)
                        + fullTypeName.substring(aliasName.length());
                aliasReference.setString(replacement);
                return;
              }
            } while (dotIndex != -1);
          }
        };
  }
}
