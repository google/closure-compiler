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

import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.AstFactory.type;
import static com.google.javascript.jscomp.Es6ToEs3Util.CANNOT_CONVERT_YET;

import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

/**
 * Converts {@code super.method()} calls and adds constructors to any classes that lack them.
 *
 * <p>This has to run before the main {@link Es6RewriteClass} pass. The super() constructor calls
 * are not converted here, but rather in {@link Es6ConvertSuperConstructorCalls}, which runs later.
 */
public final class Es6ConvertSuper extends NodeTraversal.AbstractPostOrderCallback
    implements CompilerPass {
  private final AbstractCompiler compiler;
  private final AstFactory astFactory;
  private final SynthesizeExplicitConstructors constructorSynthesizer;
  private static final FeatureSet transpiledFeatures = FeatureSet.BARE_MINIMUM.with(Feature.SUPER);

  public Es6ConvertSuper(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
    this.constructorSynthesizer = new SynthesizeExplicitConstructors(compiler);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isClass()) {
      constructorSynthesizer.synthesizeClassConstructorIfMissing(t, n);
    } else if (n.isSuper()) {
      visitSuper(n, parent);
    }
  }

  private boolean isInterface(Node classNode) {
    JSDocInfo classJsDocInfo = NodeUtil.getBestJSDocInfo(classNode);
    return classJsDocInfo != null && classJsDocInfo.isInterface();
  }

  private void visitSuper(Node node, Node parent) {
    checkState(node.isSuper());
    Node exprRoot = node;

    if (exprRoot.getParent().isGetElem() || exprRoot.getParent().isGetProp()) {
      exprRoot = exprRoot.getParent();
    }

    Node enclosingMemberDef =
        NodeUtil.getEnclosingNode(
            exprRoot,
            (Node n) -> {
              switch (n.getToken()) {
                case MEMBER_FUNCTION_DEF:
                case GETTER_DEF:
                case SETTER_DEF:
                case COMPUTED_PROP:
                  return true;
                default:
                  return false;
              }
            });

    if (parent.isCall()) {
      // super(...)
      visitSuperCall(node, parent, enclosingMemberDef);
    } else if (parent.isGetProp() || parent.isGetElem()) {
      if (parent.getFirstChild() == node) {
        if (parent.getParent().isCall() && NodeUtil.isInvocationTarget(parent)) {
          // super.something(...) or super['something'](..)
          visitSuperPropertyCall(node, parent, enclosingMemberDef);
        } else {
          // super.something or super['something']
          visitSuperPropertyAccess(node, parent, enclosingMemberDef);
        }
      } else {
        // super.something used in some other way
        compiler.report(
            JSError.make(
                node,
                CANNOT_CONVERT_YET,
                "Only calls to super or to a method of super are supported."));
      }
    } else if (parent.isNew()) {
      throw new IllegalStateException("This should never happen. Did Es6SuperCheck fail to run?");
    } else {
      // some other use of super we don't support yet
      compiler.report(
          JSError.make(
              node,
              CANNOT_CONVERT_YET,
              "Only calls to super or to a method of super are supported."));
    }
  }

  private void visitSuperCall(Node node, Node parent, Node enclosingMemberDef) {
    checkState(parent.isCall(), parent);
    checkState(node.isSuper(), node);

    Node clazz = NodeUtil.getEnclosingClass(node);
    Node superName = clazz.getSecondChild();
    if (!superName.isQualifiedName()) {
      // This will be reported as an error in Es6ToEs3Converter.
      return;
    }

    if (NodeUtil.isEs6ConstructorMemberFunctionDef(enclosingMemberDef)) {
      // Calls to super() constructors will be transpiled by Es6ConvertSuperConstructorCalls later.
      if (node.isFromExterns() || isInterface(clazz)) {
        // If a class is defined in an externs file or as an interface, it's only a stub, not an
        // implementation that should be instantiated.
        // A call to super() shouldn't actually exist for these cases and is problematic to
        // transpile, so just drop it.
        Node enclosingStatement = NodeUtil.getEnclosingStatement(node);
        Node enclosingStatementParent = enclosingStatement.getParent();
        enclosingStatement.detach();
        compiler.reportChangeToEnclosingScope(enclosingStatementParent);
      }
      // Calls to super() constructors will be transpiled by Es6ConvertSuperConstructorCalls
      // later.
      return;
    } else {
      // super can only be directly called in a constructor
      throw new IllegalStateException("This should never happen. Did Es6SuperCheck fail to run?");
    }
  }

  private void visitSuperPropertyCall(Node node, Node parent, Node enclosingMemberDef) {
    checkState(parent.isGetProp() || parent.isGetElem(), parent);
    checkState(node.isSuper(), node);
    Node grandparent = parent.getParent();
    checkState(grandparent.isCall());

    Node clazz = NodeUtil.getEnclosingClass(node);
    Node superName = clazz.getSecondChild();
    if (!superName.isQualifiedName()) {
      // This will be reported as an error in Es6ToEs3Converter.
      return;
    }

    Node callTarget = parent;
    if (enclosingMemberDef.isStaticMember()) {
      Node expandedSuper = superName.cloneTree().srcrefTree(node);
      expandedSuper.setOriginalName("super");
      node.replaceWith(expandedSuper);
      callTarget = astFactory.createGetPropWithUnknownType(callTarget.detach(), "call");
      grandparent.addChildToFront(callTarget);
      Node thisNode = astFactory.createThis(type(clazz));
      thisNode.makeNonIndexable(); // no direct correlation with original source
      thisNode.insertAfter(callTarget);
      grandparent.srcrefTreeIfMissing(parent);
    } else {
      // Replace super node to give
      // super.method(...) -> SuperClass.prototype.method(...)
      Node expandedSuper = astFactory.createPrototypeAccess(superName.cloneTree()).srcrefTree(node);
      expandedSuper.setOriginalName("super");
      node.replaceWith(expandedSuper);
      // Set the 'this' object correctly for the call
      // SuperClass.prototype.method(...) -> SuperClass.prototype.method.call(this, ...)
      callTarget = astFactory.createGetPropWithUnknownType(callTarget.detach(), "call");
      grandparent.addChildToFront(callTarget);
      Node thisNode = astFactory.createThisForEs6Class(clazz);
      thisNode.makeNonIndexable(); // no direct correlation with original source
      thisNode.insertAfter(callTarget);
      grandparent.putBooleanProp(Node.FREE_CALL, false);
      grandparent.srcrefTreeIfMissing(parent);
    }
    compiler.reportChangeToEnclosingScope(grandparent);
  }

  private void visitSuperPropertyAccess(Node node, Node parent, Node enclosingMemberDef) {
    checkState(parent.isGetProp() || parent.isGetElem(), parent);
    checkState(node.isSuper(), node);
    Node grandparent = parent.getParent();

    if (NodeUtil.isLValue(parent)) {
      // We don't support assigning to a super property
      compiler.report(JSError.make(parent, CANNOT_CONVERT_YET, "assigning to a super property"));
      return;
    }

    Node clazz = NodeUtil.getEnclosingClass(node);
    Node superName = clazz.getSecondChild();
    if (!superName.isQualifiedName()) {
      // This will be reported as an error in Es6ToEs3Converter.
      return;
    }

    if (enclosingMemberDef.isStaticMember()) {
      // super.prop -> SuperClass.prop
      Node expandedSuper = superName.cloneTree().srcrefTree(node);
      expandedSuper.setOriginalName("super");
      node.replaceWith(expandedSuper);
    } else {
      // super.prop -> SuperClass.prototype.prop
      Node newprop = astFactory.createPrototypeAccess(superName.cloneTree()).srcrefTree(node);
      newprop.setOriginalName("super");
      node.replaceWith(newprop);
    }

    compiler.reportChangeToEnclosingScope(grandparent);
  }

  @Override
  public void process(Node externs, Node root) {
    // Might need to synthesize constructors for ambient classes in .d.ts externs
    TranspilationPasses.processTranspile(compiler, externs, transpiledFeatures, this);
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
  }

}
