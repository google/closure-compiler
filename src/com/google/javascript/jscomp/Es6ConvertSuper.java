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

import static com.google.javascript.jscomp.Es6ToEs3Converter.CANNOT_CONVERT_YET;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Converts {@code super.method()} calls and adds constructors to any classes that lack them.
 *
 * <p>This has to run before the main {@link Es6ToEs3Converter} pass. The super() constructor calls
 * are not converted here, but rather in {@link Es6ConvertSuperConstructorCalls}, which runs later.
 */
public final class Es6ConvertSuper extends NodeTraversal.AbstractPostOrderCallback
    implements HotSwapCompilerPass {
  private final AbstractCompiler compiler;

  public Es6ConvertSuper(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isClass()) {
      boolean hasConstructor = false;
      for (Node member = n.getLastChild().getFirstChild();
          member != null;
          member = member.getNext()) {
        if (member.isMemberFunctionDef() && member.getString().equals("constructor")) {
          hasConstructor = true;
          break;
        }
      }
      if (!hasConstructor) {
        addSyntheticConstructor(t, n);
      }
    } else if (n.isSuper()) {
      visitSuper(n, parent);
    }
  }

  private void addSyntheticConstructor(NodeTraversal t, Node classNode) {
    Node superClass = classNode.getSecondChild();
    Node classMembers = classNode.getLastChild();
    Node memberDef;
    if (superClass.isEmpty()) {
      memberDef = IR.memberFunctionDef("constructor",
          IR.function(IR.name(""), IR.paramList(), IR.block()));
    } else {
      if (!superClass.isQualifiedName()) {
        // This will be reported as an error in Es6ToEs3Converter.
        return;
      }
      Node body = IR.block();

      // If a class is defined in an externs file or as an interface, it's only a stub, not an
      // implementation that should be instantiated.
      // A call to super() shouldn't actually exist for these cases and is problematic to
      // transpile, so don't generate it.
      if (!classNode.isFromExterns()  && !isInterface(classNode)) {
        Node exprResult = IR.exprResult(IR.call(
            IR.getprop(IR.superNode(), IR.string("apply")),
            IR.thisNode(),
            IR.name("arguments")));
        body.addChildToFront(exprResult);
      }
      Node constructor = IR.function(
          IR.name(""),
          IR.paramList(IR.name("var_args")),
          body);
      memberDef = IR.memberFunctionDef("constructor", constructor);
      JSDocInfoBuilder info = new JSDocInfoBuilder(false);
      info.recordParameter(
          "var_args",
          new JSTypeExpression(
              new Node(Token.ELLIPSIS, new Node(Token.QMARK)), "<Es6ConvertSuper>"));
      memberDef.setJSDocInfo(info.build());
    }
    memberDef.useSourceInfoIfMissingFromForTree(classNode);
    classMembers.addChildToFront(memberDef);
  }

  private boolean isInterface(Node classNode) {
    JSDocInfo classJsDocInfo = NodeUtil.getBestJSDocInfo(classNode);
    return classJsDocInfo != null && classJsDocInfo.isInterface();
  }

  private void visitSuper(Node node, Node parent) {
    Node enclosingCall = parent;
    Node potentialCallee = node;
    if (!parent.isCall()) {
      enclosingCall = parent.getParent();
      potentialCallee = parent;
    }
    if (!enclosingCall.isCall()
        || enclosingCall.getFirstChild() != potentialCallee
        || enclosingCall.getFirstChild().isGetElem()) {
      compiler.report(JSError.make(node, CANNOT_CONVERT_YET,
          "Only calls to super or to a method of super are supported."));
      return;
    }
    Node clazz = NodeUtil.getEnclosingClass(node);
    Node superName = clazz.getSecondChild();
    if (!superName.isQualifiedName()) {
      // This will be reported as an error in Es6ToEs3Converter.
      return;
    }

    Node enclosingMemberDef = NodeUtil.getEnclosingClassMemberFunction(node);
    if (enclosingMemberDef.getString().equals("constructor")
        && parent.isCall()
        && parent.getFirstChild() == node) {
      // Calls to super() constructors will be transpiled by Es6ConvertSuperConstructorCalls later.
      if (node.isFromExterns() || isInterface(clazz)) {
        // If a class is defined in an externs file or as an interface, it's only a stub, not an
        // implementation that should be instantiated.
        // A call to super() shouldn't actually exist for these cases and is problematic to
        // transpile, so just drop it.
        NodeUtil.getEnclosingStatement(node).detach();
        compiler.reportCodeChange();
      }
      // Calls to super() constructors will be transpiled by Es6ConvertSuperConstructorCalls
      // later.
      return;
    }
    if (enclosingMemberDef.isStaticMember()) {
      Node callTarget;
      potentialCallee.detach();
      if (potentialCallee == node) {
        // of the form super()
        // TODO(bradfordcsmith): This should report an error since this is not allowed by the
        //     current spec.
        potentialCallee =
            IR.getprop(superName.cloneTree(), IR.string(enclosingMemberDef.getString()));
        enclosingCall.putBooleanProp(Node.FREE_CALL, false);
      } else {
        // of the form super.method()
        potentialCallee.replaceChild(node, superName.cloneTree());
      }
      callTarget = IR.getprop(potentialCallee, IR.string("call"));
      enclosingCall.addChildToFront(callTarget);
      enclosingCall.addChildAfter(IR.thisNode(), callTarget);
      enclosingCall.useSourceInfoIfMissingFromForTree(enclosingCall);
      compiler.reportCodeChange();
      return;
    }

    String methodName;
    Node callName = enclosingCall.removeFirstChild();
    if (callName.isSuper()) {
      // TODO(bradfordcsmith): This should report an error since this is not allowed by the
      //     current spec.
      methodName = enclosingMemberDef.getString();
    } else {
      methodName = callName.getLastChild().getString();
    }
    Node baseCall = baseCall(
        superName.getQualifiedName(), methodName, enclosingCall.removeChildren());
    baseCall.useSourceInfoIfMissingFromForTree(enclosingCall);
    enclosingCall.getParent().replaceChild(enclosingCall, baseCall);
    compiler.reportCodeChange();
  }

  private Node baseCall(String baseClass, String methodName, Node arguments) {
    Preconditions.checkNotNull(baseClass);
    Preconditions.checkNotNull(methodName);
    String baseMethodName;
    baseMethodName = Joiner.on('.').join(baseClass, "prototype", methodName, "call");
    Node methodCall = NodeUtil.newQName(compiler, baseMethodName);
    Node callNode = IR.call(methodCall, IR.thisNode());
    if (arguments != null) {
      callNode.addChildrenToBack(arguments);
    }
    return callNode;
  }

  @Override
  public void process(Node externs, Node root) {
    // Might need to synthesize constructors for ambient classes in .d.ts externs
    TranspilationPasses.processTranspile(compiler, externs, this);
    TranspilationPasses.processTranspile(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(compiler, scriptRoot, this);
  }
}
