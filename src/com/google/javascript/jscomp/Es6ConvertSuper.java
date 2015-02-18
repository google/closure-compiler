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

import static com.google.javascript.jscomp.Es6ToEs3Converter.CANNOT_CONVERT;
import static com.google.javascript.jscomp.Es6ToEs3Converter.CANNOT_CONVERT_YET;
import static com.google.javascript.jscomp.Es6ToEs3Converter.getUniqueClassName;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

/**
 * Converts {@code super} nodes. This has to run before the main
 * {@link Es6ToEs3Converter} pass.
 */
public class Es6ConvertSuper implements NodeTraversal.Callback, HotSwapCompilerPass {
  static final DiagnosticType NO_SUPERTYPE = DiagnosticType.error(
      "JSC_NO_SUPERTYPE",
      "The super keyword may only appear in classes with an extends clause.");

  private final AbstractCompiler compiler;

  public Es6ConvertSuper(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  private void checkClassSuperReferences(Node classNode) {
    Node className = classNode.getFirstChild();
    Node superClassName = className.getNext();
    if (NodeUtil.referencesSuper(classNode) && superClassName.isEmpty()) {
      compiler.report(JSError.make(classNode, NO_SUPERTYPE));
    }
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    if (n.isClass()) {
      boolean hasConstructor = false;
      for (Node member = n.getLastChild().getFirstChild();
          member != null;
          member = member.getNext()) {
        if (member.isGetterDef() || member.isSetterDef()
            || member.getBooleanProp(Node.COMPUTED_PROP_GETTER)
            || member.getBooleanProp(Node.COMPUTED_PROP_SETTER)) {
          compiler.report(JSError.make(member, CANNOT_CONVERT,
              "getters or setters in class definitions"));
          return false;
        }
        if (member.isMemberFunctionDef() && member.getString().equals("constructor")) {
          hasConstructor = true;
        }
      }
      if (!hasConstructor) {
        addSyntheticConstructor(n);
      }
      checkClassSuperReferences(n);
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isSuper()) {
      visitSuper(n, parent);
    }
  }

  private void addSyntheticConstructor(Node classNode) {
    Node superClass = classNode.getFirstChild().getNext();
    Node classMembers = classNode.getLastChild();
    Node memberDef;
    if (superClass.isEmpty()) {
      memberDef = IR.memberFunctionDef("constructor",
          IR.function(IR.name(""), IR.paramList(), IR.block()));
    } else {
      Node paramList = IR.paramList(IR.rest("args"));
      Node body = IR.block(
          IR.exprResult(IR.call(
              IR.superNode(),
              IR.spread(IR.name("args")))));
      Node constructor = IR.function(
          IR.name(""),
          paramList,
          body);
      memberDef = IR.memberFunctionDef("constructor", constructor);
    }
    memberDef.useSourceInfoIfMissingFromForTree(classNode);
    classMembers.addChildToFront(memberDef);
  }

  private void visitSuper(Node node, Node parent) {
    Node enclosing = parent;
    Node potentialCallee = node;
    if (!parent.isCall()) {
      enclosing = parent.getParent();
      potentialCallee = parent;
    }
    if (!enclosing.isCall() || enclosing.getFirstChild() != potentialCallee) {
      compiler.report(JSError.make(node, CANNOT_CONVERT_YET,
          "Only calls to super or to a method of super are supported."));
      return;
    }
    Node clazz = NodeUtil.getEnclosingClass(node);
    if (clazz == null) {
      compiler.report(JSError.make(node, NO_SUPERTYPE));
      return;
    }
    if (NodeUtil.getClassNameNode(clazz) == null) {
      // Unnamed classes of the form:
      //   f(class extends D { ... });
      // give the problem that there is no name to be used in the call to goog.base for the
      // translation of super calls.
      // This will throw an error when the class is processed.
      return;
    }

    Node enclosingMemberDef = NodeUtil.getEnclosingClassMemberFunction(node);
    if (enclosingMemberDef.isStaticMember()) {
      Node superName = clazz.getFirstChild().getNext();
      if (!superName.isQualifiedName()) {
        // This has already been reported, just don't need to continue processing the class.
        return;
      }
      Node callTarget;
      potentialCallee.detachFromParent();
      if (potentialCallee == node) {
        // of the form super()
        potentialCallee =
            IR.getprop(superName.cloneTree(), IR.string(enclosingMemberDef.getString()));
        enclosing.putBooleanProp(Node.FREE_CALL, false);
      } else {
        // of the form super.method()
        potentialCallee.replaceChild(node, superName.cloneTree());
      }
      callTarget = IR.getprop(potentialCallee, IR.string("call"));
      enclosing.addChildToFront(callTarget);
      enclosing.addChildAfter(IR.thisNode(), callTarget);
      enclosing.useSourceInfoIfMissingFromForTree(enclosing);
      compiler.reportCodeChange();
      return;
    }

    String methodName;
    Node callName = enclosing.removeFirstChild();
    if (callName.isSuper()) {
      methodName = enclosingMemberDef.getString();
    } else {
      methodName = callName.getLastChild().getString();
    }
    Node baseCall = baseCall(clazz, methodName, enclosing.removeChildren())
        .useSourceInfoIfMissingFromForTree(enclosing);
    enclosing.getParent().replaceChild(enclosing, baseCall);
    compiler.reportCodeChange();
  }

  private Node baseCall(Node clazz, String methodName, Node arguments) {
    boolean useUnique = NodeUtil.isStatement(clazz) && !NodeUtil.isInFunction(clazz);
    String uniqueClassString = useUnique ? getUniqueClassName(NodeUtil.getClassName(clazz))
        : NodeUtil.getClassName(clazz);
    Node uniqueClassName = NodeUtil.newQName(compiler,
        uniqueClassString);
    Node base = IR.getprop(uniqueClassName, IR.string("base"));
    Node call = IR.call(base, IR.thisNode(), IR.string(methodName));
    if (arguments != null) {
      call.addChildrenToBack(arguments);
    }
    return call;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }
}
