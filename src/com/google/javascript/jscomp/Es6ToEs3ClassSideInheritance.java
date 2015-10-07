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

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Rewrites static inheritance to explicitly copy inherited properties from superclass to
 * subclass so that the typechecker knows the subclass has those properties.
 *
 * <p>For example, {@link Es6ToEs3Converter} will convert
 *
 * <pre>
 *   class Foo { static f() {} }
 *   class Bar extends Foo {}
 * </pre>
 * to
 *
 * <pre>
 *   function Foo() {}
 *   Foo.f = function() {};
 *   function Bar() {}
 *   $jscomp.inherits(Foo, Bar);
 * </pre>
 *
 * and then this class will convert that to
 *
 * <pre>
 *   function Foo() {}
 *   Foo.f = function() {};
 *   function Bar() {}
 *   $jscomp.inherits(Foo, Bar);
 *   Bar.f = Foo.f;
 * </pre>
 *
 * @author mattloring@google.com (Matthew Loring)
 */
public final class Es6ToEs3ClassSideInheritance implements HotSwapCompilerPass {

  final AbstractCompiler compiler;

  // Map from class names to the static members in each class.
  private final Multimap<String, Node> staticMethods = ArrayListMultimap.create();

  // Map from class names to static properties (getters/setters) in each class.
  private final Multimap<String, Node> staticProperties = ArrayListMultimap.create();

  static final DiagnosticType DUPLICATE_CLASS = DiagnosticType.error(
      "DUPLICATE_CLASS",
      "Multiple classes cannot share the same name.");

  private final Set<String> multiplyDefinedClasses = new HashSet<>();

  public Es6ToEs3ClassSideInheritance(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    FindStaticMembers findStaticMembers = new FindStaticMembers();
    NodeTraversal.traverseEs6(compiler, externs, findStaticMembers);
    NodeTraversal.traverseEs6(compiler, root, findStaticMembers);
    processInherits(findStaticMembers.inheritsCalls);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    FindStaticMembers findStaticMembers = new FindStaticMembers();
    NodeTraversal.traverseEs6(compiler, scriptRoot, findStaticMembers);
    processInherits(findStaticMembers.inheritsCalls);
  }

  private void processInherits(List<Node> inheritsCalls) {
    for (Node n : inheritsCalls) {
      Node parent = n.getParent();
      Node superclassNameNode = n.getLastChild();
      Node subclassNameNode = n.getChildBefore(superclassNameNode);
      if (multiplyDefinedClasses.contains(superclassNameNode.getQualifiedName())) {
        compiler.report(JSError.make(n, DUPLICATE_CLASS));
        return;
      }
      for (Node staticMethod : staticMethods.get(superclassNameNode.getQualifiedName())) {
        copyStaticMethod(staticMethod, superclassNameNode, subclassNameNode, parent);
      }

      for (Node staticProperty : staticProperties.get(superclassNameNode.getQualifiedName())) {
        Preconditions.checkState(staticProperty.isGetProp(), staticProperty);
        String memberName = staticProperty.getLastChild().getString();
        // Add a declaration. Assuming getters are side-effect free,
        // this is a no-op statement, but it lets the typechecker know about the property.
        Node getprop = IR.getprop(subclassNameNode.cloneTree(), IR.string(memberName));

        JSDocInfoBuilder info = JSDocInfoBuilder.maybeCopyFrom(staticProperty.getJSDocInfo());
        JSTypeExpression unknown = new JSTypeExpression(new Node(Token.QMARK), "<synthetic>");
        info.recordType(unknown); // In case there wasn't a type specified on the base class.
        info.addSuppression("visibility");
        getprop.setJSDocInfo(info.build());

        Node declaration = IR.exprResult(getprop);
        declaration.useSourceInfoIfMissingFromForTree(n);
        parent.getParent().addChildAfter(declaration, parent);
        staticProperties.put(subclassNameNode.getQualifiedName(), staticProperty);
        compiler.reportCodeChange();
      }
    }
  }

  private void copyStaticMethod(
      Node staticMember, Node superclassNameNode, Node subclassNameNode, Node insertionPoint) {
    Preconditions.checkState(staticMember.isAssign(), staticMember);
    String memberName = staticMember.getFirstChild().getLastChild().getString();

    for (Node subclassMember : staticMethods.get(subclassNameNode.getQualifiedName())) {
      Preconditions.checkState(subclassMember.isAssign(), subclassMember);
      if (subclassMember.getFirstChild().getLastChild().getString().equals(memberName)) {
        // This subclass overrides the static method, so there is no need to copy the
        // method from the base class.
        return;
      }
    }

    JSDocInfoBuilder info = JSDocInfoBuilder.maybeCopyFrom(staticMember.getJSDocInfo());

    Node assign = IR.assign(
        IR.getprop(subclassNameNode.cloneTree(), IR.string(memberName)),
        IR.getprop(superclassNameNode.cloneTree(), IR.string(memberName)));
    info.addSuppression("visibility");
    assign.setJSDocInfo(info.build());
    Node exprResult = IR.exprResult(assign);
    exprResult.useSourceInfoIfMissingFromForTree(superclassNameNode);
    insertionPoint.getParent().addChildAfter(exprResult, insertionPoint);
    staticMethods.put(subclassNameNode.getQualifiedName(), assign);
    compiler.reportCodeChange();
  }

  private class FindStaticMembers extends NodeTraversal.AbstractPostOrderCallback {
    private final Set<String> classNames = new HashSet<>();
    private final List<Node> inheritsCalls = new LinkedList<>();

    @Override
    public void visit(NodeTraversal nodeTraversal, Node n, Node parent) {
      switch (n.getType()) {
        case Token.CALL:
          if (n.getFirstChild().matchesQualifiedName(Es6ToEs3Converter.INHERITS)) {
            inheritsCalls.add(n);
          }
          break;
        case Token.VAR:
          visitVar(n);
          break;
        case Token.ASSIGN:
          visitAssign(n);
          break;
        case Token.GETPROP:
          if (parent.isExprResult()) {
            visitGetProp(n);
          }
          break;
        case Token.FUNCTION:
          visitFunctionClassDef(n);
          break;
      }
    }

    private void visitFunctionClassDef(Node n) {
      JSDocInfo classInfo = NodeUtil.getBestJSDocInfo(n);
      if (classInfo != null && classInfo.isConstructor()) {
        String name = NodeUtil.getFunctionName(n);
        if (classNames.contains(name)) {
          multiplyDefinedClasses.add(name);
        } else if (name != null) {
          classNames.add(name);
        }
      }
    }

    private void visitGetProp(Node n) {
      String className = n.getFirstChild().getQualifiedName();
      if (classNames.contains(className)) {
        staticProperties.put(className, n);
      }
    }

    private void visitAssign(Node n) {
      // Alias for classes. We assume that the alias appears after the class
      // declaration.
      if (classNames.contains(n.getLastChild().getQualifiedName())) {
        String maybeAlias = n.getFirstChild().getQualifiedName();
        if (maybeAlias != null) {
          classNames.add(maybeAlias);
          staticMethods.putAll(maybeAlias, staticMethods.get(n.getLastChild().getQualifiedName()));
        }
      } else if (n.getFirstChild().isGetProp()) {
        Node getProp = n.getFirstChild();
        String maybeClassName = getProp.getFirstChild().getQualifiedName();
        if (classNames.contains(maybeClassName)) {
          staticMethods.put(maybeClassName, n);
        }
      }
    }

    private void visitVar(Node n) {
      Node child = n.getFirstChild();
      if (!child.hasChildren()) {
        return;
      }
      String maybeOriginalName = child.getFirstChild().getQualifiedName();
      if (classNames.contains(maybeOriginalName)) {
        String maybeAlias = child.getQualifiedName();
        if (maybeAlias != null) {
          classNames.add(maybeAlias);
          staticMethods.putAll(maybeAlias, staticMethods.get(maybeOriginalName));
        }
      }
    }
  }
}
