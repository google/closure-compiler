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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.Es6ToEs3Util.CANNOT_CONVERT_YET;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;

/**
 * Converts {@code super.method()} calls and adds constructors to any classes that lack them.
 *
 * <p>This has to run before the main {@link Es6RewriteClass} pass. The super() constructor calls
 * are not converted here, but rather in {@link Es6ConvertSuperConstructorCalls}, which runs later.
 */
public final class Es6ConvertSuper extends NodeTraversal.AbstractPostOrderCallback
    implements HotSwapCompilerPass {
  private final AbstractCompiler compiler;
  private final AstFactory astFactory;
  private static final FeatureSet transpiledFeatures = FeatureSet.BARE_MINIMUM.with(Feature.SUPER);

  public Es6ConvertSuper(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
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
      // use the pre-cast type because createEmptyFunction expects a FunctionType
      Node function = astFactory.createEmptyFunction(getTypeBeforeCast(classNode));
      compiler.reportChangeToChangeScope(function);
      memberDef = astFactory.createMemberFunctionDef("constructor", function);
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
      if (!classNode.isFromExterns() && !isInterface(classNode)) {
        // Generate required call to super()
        // `super(...arguments);`
        // Note that transpilation of spread args must occur after this pass for this to work.
        Node exprResult =
            IR.exprResult(
                astFactory.createConstructorCall(
                    classNode.getJSType(), // returned type is the subclass
                    IR.superNode().setJSType(superClass.getJSType()),
                    IR.spread(astFactory.createArgumentsReference())));
        body.addChildToFront(exprResult);
        NodeUtil.addFeatureToScript(t.getCurrentFile(), Feature.SUPER);
        NodeUtil.addFeatureToScript(t.getCurrentFile(), Feature.SPREAD_EXPRESSIONS);
      }
      Node constructor =
          astFactory.createFunction(
              "",
              IR.paramList(astFactory.createName("var_args", JSTypeNative.UNKNOWN_TYPE)),
              body,
              classNode.getJSType());
      memberDef = astFactory.createMemberFunctionDef("constructor", constructor);
      // TODO(bradfordcsmith): Drop creation of JSDoc once transpilation moves after all checks.
      JSDocInfoBuilder info = new JSDocInfoBuilder(false);
      info.recordParameter(
          "var_args",
          new JSTypeExpression(
              new Node(Token.ELLIPSIS, new Node(Token.QMARK)), "<Es6ConvertSuper>"));
      memberDef.setJSDocInfo(info.build());
    }
    memberDef.useSourceInfoIfMissingFromForTree(classNode);
    memberDef.makeNonIndexableRecursive();
    classMembers.addChildToFront(memberDef);
    NodeUtil.addFeatureToScript(t.getCurrentFile(), Feature.MEMBER_DECLARATIONS);
    // report newly created constructor
    compiler.reportChangeToChangeScope(memberDef.getOnlyChild());
    // report change to scope containing the class
    compiler.reportChangeToEnclosingScope(memberDef);
  }

  /** Returns the node's pre-CAST type, if is has one. Otherwise just returns node.getJSType() */
  private static JSType getTypeBeforeCast(Node node) {
    JSType typeBeforeCast = node.getJSTypeBeforeCast();
    // might still return null if node.getJSType() is null (i.e. typechecking hasn't run)
    return typeBeforeCast != null ? typeBeforeCast : node.getJSType();
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
            new Predicate<Node>() {
              @Override
              public boolean apply(Node n) {
                switch (n.getToken()) {
                  case MEMBER_FUNCTION_DEF:
                  case GETTER_DEF:
                  case SETTER_DEF:
                  case COMPUTED_PROP:
                    return true;
                  default:
                    return false;
                }
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

    if (enclosingMemberDef.isMemberFunctionDef()
        && enclosingMemberDef.getString().equals("constructor")) {
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
    Node callNode = IR.string("call");
    callNode.makeNonIndexable();
    if (enclosingMemberDef.isStaticMember()) {
      Node expandedSuper = superName.cloneTree().useSourceInfoFromForTree(node);
      expandedSuper.setOriginalName("super");
      callTarget.replaceChild(node, expandedSuper);
      callTarget = astFactory.createGetProp(callTarget.detach(), "call");
      grandparent.addChildToFront(callTarget);
      Node thisNode = astFactory.createThis(clazz.getJSType());
      thisNode.makeNonIndexable(); // no direct correlation with original source
      grandparent.addChildAfter(thisNode, callTarget);
      grandparent.useSourceInfoIfMissingFromForTree(parent);
    } else {
      // Replace super node to give
      // super.method(...) -> SuperClass.prototype.method(...)
      Node expandedSuper =
          astFactory
              .createGetProp(superName.cloneTree(), "prototype")
              .useSourceInfoFromForTree(node);
      expandedSuper.setOriginalName("super");
      node.replaceWith(expandedSuper);
      // Set the 'this' object correctly for the call
      // SuperClass.prototype.method(...) -> SuperClass.prototype.method.call(this, ...)
      callTarget = astFactory.createGetProp(callTarget.detach(), "call");
      grandparent.addChildToFront(callTarget);
      JSType thisType = getInstanceTypeForClassNode(clazz);
      Node thisNode = astFactory.createThis(thisType);
      thisNode.makeNonIndexable(); // no direct correlation with original source
      grandparent.addChildAfter(thisNode, callTarget);
      grandparent.putBooleanProp(Node.FREE_CALL, false);
      grandparent.useSourceInfoIfMissingFromForTree(parent);
    }
    compiler.reportChangeToEnclosingScope(grandparent);
  }

  private JSType getInstanceTypeForClassNode(Node classNode) {
    checkArgument(classNode.isClass(), classNode);
    final JSType constructorType = classNode.getJSType();
    final JSType result;
    if (constructorType != null) {
      checkArgument(constructorType.isConstructor(), classNode);
      result = JSType.toMaybeFunctionType(constructorType).getInstanceType();
    } else {
      result = null;
    }
    return result;
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
      Node expandedSuper = superName.cloneTree().useSourceInfoFromForTree(node);
      expandedSuper.setOriginalName("super");
      node.replaceWith(expandedSuper);
    } else {
      if (astFactory.isAddingTypes()) {
        // super.prop -> SuperClass.prototype.prop
        Node newprop =
            astFactory
                .createGetProp(superName.cloneTree(), "prototype")
                .useSourceInfoFromForTree(node);
        newprop.setOriginalName("super");
        node.replaceWith(newprop);
      } else {
        String newPropName = Joiner.on('.').join(superName.getQualifiedName(), "prototype");
        // TODO(bradfordcsmith): This is required for Kythe, which doesn't work correctly with
        // Node#useSourceInfoIfMissingFromForTree.  Fortunately, we only care about Kythe
        // if we're not adding types.
        // Once this pass is always run after type checking, we can eliminate this branch.
        node.replaceWith(
            NodeUtil.newQName(compiler, newPropName, node, "super").useSourceInfoFromForTree(node));
      }
    }

    compiler.reportChangeToEnclosingScope(grandparent);
  }

  @Override
  public void process(Node externs, Node root) {
    // Might need to synthesize constructors for ambient classes in .d.ts externs
    TranspilationPasses.processTranspile(compiler, externs, transpiledFeatures, this);
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(compiler, scriptRoot, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }
}
