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
import com.google.javascript.rhino.Token;

import java.util.HashSet;
import java.util.Set;

/**
 * Rewrites static inheritance to explicitly copy inherited properties from super class to
 * sub class eliminating dynamic copying from the original transpilation pass in the process.
 *
 * For example, {@link Es6ToEs3Converter} will convert
 *
 * <pre>
 * class Foo { static f() {} }
 * class Bar extends Foo {}
 * </pre>
 * to
 *
 * <pre>
 * function Foo() {}
 * Foo.f = function() {};
 * function Bar() {}
 * $jscomp$copy$properties(Foo, Bar);
 * </pre>
 *
 * and then this class will convert that to
 *
 * <pre>
 * function Foo() {}
 * Foo.f = function() {};
 * function Bar() {}
 * Bar.f = Foo.f;
 * </pre>
 *
 * @author mattloring@google.com (Matthew Loring)
 */
public class Es6ToEs3ClassSideInheritance extends NodeTraversal.AbstractPostOrderCallback
    implements CompilerPass {

  final AbstractCompiler compiler;
  // Map from class names to the static members in each class.
  private final Multimap<String, String> staticMembers = ArrayListMultimap.create();

  static final DiagnosticType DUPLICATE_CLASS = DiagnosticType.error(
      "DUPLICATE_CLASS",
      "Multiple classes cannot share the same name.");

  private final Set<String> multiplyDefinedClasses = new HashSet<>();

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
      String key = superClassName.getQualifiedName();
      if (multiplyDefinedClasses.contains(key)) {
        compiler.report(JSError.make(n, DUPLICATE_CLASS));
        return;
      }
      if (staticMembers.containsKey(key)) {
        for (String staticMember : staticMembers.get(key)) {
          Node sAssign = IR.exprResult(
              IR.assign(
                  IR.getprop(subClassName.cloneTree(), IR.string(staticMember)),
                  IR.getprop(superClassName.cloneTree(), IR.string(staticMember))));
          sAssign.useSourceInfoIfMissingFromForTree(n);
          parent.getParent().addChildAfter(sAssign, parent);
          staticMembers.put(subClassName.getQualifiedName(),
              staticMember);
        }
        parent.detachFromParent();
      }
    }
  }

  private class FindStaticMembers extends NodeTraversal.AbstractPostOrderCallback {

    private final Set<String> classNames = new HashSet<String>();

    @Override
    public void visit(NodeTraversal nodeTraversal, Node n, Node parent) {
      switch(n.getType()) {
        case Token.ASSIGN:
          visitAssign(n);
          break;
        case Token.FUNCTION:
          visitFunctionClassDef(n);
          break;
      }
    }

    public void visitFunctionClassDef(Node n) {
      JSDocInfo classInfo = NodeUtil.getBestJSDocInfo(n);
      if (classInfo != null && classInfo.isConstructor()) {
        String name = NodeUtil.getFunctionName(n);
        if (classNames.contains(name)) {
          multiplyDefinedClasses.add(name);
        } else {
          classNames.add(name);
        }
      }
    }

    public void visitAssign(Node n) {
      if (!n.hasChildren() || !n.getFirstChild().isGetProp()) {
        return;
      }
      Node potentialClassName = n.getFirstChild().getFirstChild();
      if (classNames.contains(potentialClassName.getQualifiedName())) {
        staticMembers.put(potentialClassName.getQualifiedName(),
              n.getFirstChild().getLastChild().getString());
      }
    }

  }

}

