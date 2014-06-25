/*
 * Copyright 2014 The Closure Compiler Authors.
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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

/**
 * Rewrites static inheritance to explicitly copy inherited properties from super class to
 * sub class eliminating dynamic copying from the original transpilation pass in the process.
 *
 * For example, {@link Es6ToEs3Converter} will convert
 *
 * <pre>
 * class Foo { static f() {} } class Bar extends Foo {}
 * </pre>
 * to
 *
 * <pre>
 * function Foo() {} foo.f = function() {}; function Bar() {} $jscomp$copy$properties(Foo, Bar);
 * </pre>
 *
 * and then this class will convert that to
 *
 * <pre>
 * function Foo() {} Foo.f = function() {}; function Bar() {} Bar.f = Foo.f;
 * </pre>
 *
 * @author mattloring@google.com (Matthew Loring)
 */
public class Es6ToEs3ClassSideInheritance extends NodeTraversal.AbstractPostOrderCallback
    implements CompilerPass {

  final AbstractCompiler compiler;
  // Map from class names to the static members in each class.
  private final Multimap<Scope.Var, String> staticMembers = ArrayListMultimap.create();

  public Es6ToEs3ClassSideInheritance(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, new FindStaticMembers());
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal nodeTraversal, Node n, Node parent) {
    if (!n.isCall()) {
      return;
    }
    if (n.getFirstChild().matchesQualifiedName(Es6ToEs3Converter.COPY_PROP)) {
      Node superClassName = n.getLastChild();
      Node subClassName = n.getChildBefore(superClassName);
      Scope.Var key = nodeTraversal.getScope().getVar(superClassName.getQualifiedName());
      if (staticMembers.containsKey(key)) {
        for (String staticMember : staticMembers.get(key)) {
          Node sAssign = IR.exprResult(
              IR.assign(
                  IR.getprop(subClassName.detachFromParent(), IR.string(staticMember)),
                  IR.getprop(superClassName.detachFromParent(), IR.string(staticMember))));
          sAssign.useSourceInfoIfMissingFromForTree(n);
          parent.getParent().addChildAfter(sAssign, parent);
          staticMembers.put(nodeTraversal.getScope().getVar(subClassName.getQualifiedName()),
              staticMember);
        }
        parent.detachFromParent();
      }
    }
  }

  private class FindStaticMembers extends NodeTraversal.AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal nodeTraversal, Node n, Node parent) {
      if (!n.isAssign() || !n.hasChildren() || !n.getFirstChild().isGetProp()) {
        return;
      }
      Node potentialClassName = n.getFirstChild().getFirstChild();
      Scope.Var potentialClassDecl = nodeTraversal.getScope().getVar(
          potentialClassName.getQualifiedName());
      if (potentialClassDecl != null && potentialClassDecl.getNode().getParent() != null
          && potentialClassDecl.getNode().getParent().getJSDocInfo() != null) {
        JSDocInfo classInfo = potentialClassDecl.getNode().getParent().getJSDocInfo();
        if (classInfo.isConstructor() && n.getLastChild().isFunction()) {
          staticMembers.put(potentialClassDecl, n.getFirstChild().getLastChild().getString());
        }
      }
    }

  }

}

