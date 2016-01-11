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
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

  private final AbstractCompiler compiler;

  // Map from class names to the static members in each class. This is not a SetMultiMap because
  // when we find an alias A for a class C, we copy the *set* of C's static methods
  // so that adding a method to C automatically causes it to be added to A, and vice versa.
  private final LinkedHashMap<String, LinkedHashSet<Node>> staticMethods = new LinkedHashMap<>();

  // Map from class names to static properties (getters/setters) in each class. This could be a
  // SetMultimap but it is not, to be consistent with {@code staticMethods}.
  private final LinkedHashMap<String, LinkedHashSet<Node>> staticProperties = new LinkedHashMap<>();

  static final DiagnosticType DUPLICATE_CLASS = DiagnosticType.error(
      "DUPLICATE_CLASS",
      "Multiple classes cannot share the same name.");

  private final Set<String> multiplyDefinedClasses = new HashSet<>();

  public Es6ToEs3ClassSideInheritance(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  private LinkedHashSet<Node> getSet(LinkedHashMap<String, LinkedHashSet<Node>> map, String key) {
    LinkedHashSet<Node> s = map.get(key);
    if (s == null) {
      s = new LinkedHashSet<>();
      map.put(key, s);
    }
    return s;
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
      String superclassQname = superclassNameNode.getQualifiedName();
      Node subclassNameNode = n.getChildBefore(superclassNameNode);
      String subclassQname = subclassNameNode.getQualifiedName();
      if (multiplyDefinedClasses.contains(superclassQname)) {
        compiler.report(JSError.make(n, DUPLICATE_CLASS));
        return;
      }
      for (Node staticMethod : getSet(staticMethods, superclassQname)) {
        copyStaticMethod(staticMethod, superclassNameNode, subclassNameNode, subclassQname, parent);
      }
      for (Node staticProperty : getSet(staticProperties, superclassQname)) {
        copyStaticProperty(staticProperty, subclassNameNode, subclassQname, n);
      }
    }
  }

  private void copyStaticMethod(Node staticMember, Node superclassNameNode,
      Node subclassNameNode, String subclassQname, Node insertionPoint) {
    Preconditions.checkState(staticMember.isAssign(), staticMember);
    String memberName = staticMember.getFirstChild().getLastChild().getString();
    LinkedHashSet<Node>  subclassMethods = getSet(staticMethods, subclassQname);
    for (Node subclassMember : subclassMethods) {
      Preconditions.checkState(subclassMember.isAssign(), subclassMember);
      if (subclassMember.getFirstChild().getLastChild().getString().equals(memberName)) {
        // This subclass overrides the static method, so there is no need to copy the
        // method from the base class.
        return;
      }
    }

    JSDocInfoBuilder info = JSDocInfoBuilder.maybeCopyFrom(staticMember.getJSDocInfo());

    Node function = staticMember.getLastChild();
    if (function.isFunction()) {
      Node params = NodeUtil.getFunctionParameters(function);
      Preconditions.checkState(params.isParamList(), params);
      for (Node param : params.children()) {
        if (param.getJSDocInfo() != null) {
          String name = param.getString();
          info.recordParameter(name, param.getJSDocInfo().getType());
        }
      }
    }

    Node assign = IR.assign(
        IR.getprop(subclassNameNode.cloneTree(), IR.string(memberName)),
        IR.getprop(superclassNameNode.cloneTree(), IR.string(memberName)));
    info.addSuppression("visibility");
    assign.setJSDocInfo(info.build());
    Node exprResult = IR.exprResult(assign);
    exprResult.useSourceInfoIfMissingFromForTree(superclassNameNode);
    insertionPoint.getParent().addChildAfter(exprResult, insertionPoint);
    subclassMethods.add(assign);
    compiler.reportCodeChange();
  }

  private void copyStaticProperty(Node staticProperty, Node subclassNameNode,
      String subclassQname, Node inheritsCall) {
    Preconditions.checkState(staticProperty.isGetProp(), staticProperty);
    String memberName = staticProperty.getLastChild().getString();
    LinkedHashSet<Node>  subclassProps = getSet(staticProperties, subclassQname);
    for (Node subclassMember : getSet(staticProperties, subclassQname)) {
      Preconditions.checkState(subclassMember.isGetProp());
      if (subclassMember.getLastChild().getString().equals(memberName)) {
        return;
      }
    }
    // Add a declaration. Assuming getters are side-effect free,
    // this is a no-op statement, but it lets the typechecker know about the property.
    Node getprop = IR.getprop(subclassNameNode.cloneTree(), IR.string(memberName));
    JSDocInfoBuilder info = JSDocInfoBuilder.maybeCopyFrom(staticProperty.getJSDocInfo());
    JSTypeExpression unknown = new JSTypeExpression(new Node(Token.QMARK), "<synthetic>");
    info.recordType(unknown); // In case there wasn't a type specified on the base class.
    info.addSuppression("visibility");
    getprop.setJSDocInfo(info.build());

    Node declaration = IR.exprResult(getprop);
    declaration.useSourceInfoIfMissingFromForTree(inheritsCall);
    Node parent = inheritsCall.getParent();
    parent.getParent().addChildBefore(declaration, parent);
    subclassProps.add(staticProperty);
    compiler.reportCodeChange();
  }

  private class FindStaticMembers extends NodeTraversal.AbstractPostOrderCallback {
    private final Set<String> classNames = new HashSet<>();
    private final List<Node> inheritsCalls = new LinkedList<>();

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
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
          visitAssign(t, n);
          break;
        case Token.GETPROP:
          if (parent.isExprResult()) {
            visitGetProp(t, n);
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
        String name = NodeUtil.getName(n);
        if (classNames.contains(name)) {
          multiplyDefinedClasses.add(name);
        } else if (name != null) {
          classNames.add(name);
        }
      }
    }

    private void visitGetProp(NodeTraversal t, Node n) {
      Node classNode = n.getFirstChild();
      if (isReferenceToClass(t, classNode)) {
        getSet(staticProperties, classNode.getQualifiedName()).add(n);
      }
    }

    private void visitAssign(NodeTraversal t, Node n) {
      // Alias for classes. We assume that the alias appears after the class
      // declaration.
      String existingClassQname = n.getLastChild().getQualifiedName();
      if (classNames.contains(existingClassQname)) {
        String maybeAlias = n.getFirstChild().getQualifiedName();
        if (maybeAlias != null) {
          classNames.add(maybeAlias);
          staticMethods.put(maybeAlias, getSet(staticMethods, existingClassQname));
        }
      } else if (n.getFirstChild().isGetProp()) {
        Node getProp = n.getFirstChild();
        Node classNode = getProp.getFirstChild();
        if (isReferenceToClass(t, classNode)) {
          getSet(staticMethods, classNode.getQualifiedName()).add(n);
        }
      }
    }

    private boolean isReferenceToClass(NodeTraversal t, Node n) {
      String className = n.getQualifiedName();
      if (!classNames.contains(className)) {
        return false;
      }

      if (!n.isName()) {
        return true;
      }

      Var var = t.getScope().getVar(className);
      return var == null || !var.isLocal();
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
          staticMethods.put(maybeAlias, getSet(staticMethods, maybeOriginalName));
        }
      }
    }
  }
}
