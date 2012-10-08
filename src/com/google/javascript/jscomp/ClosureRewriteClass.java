/*
 * Copyright 2012 The Closure Compiler Authors.
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
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

import java.util.Collections;
import java.util.List;

/**
 * Rewrites "goog.defineClass" into a form that is suitable for
 * type checking and dead code elimination.
 *
 * @author johnlenz@google.com (John Lenz)
 */
class ClosureRewriteClass extends AbstractPostOrderCallback
    implements HotSwapCompilerPass {

  // Errors
  static final DiagnosticType GOOG_CLASS_TARGET_INVALID = DiagnosticType.error(
      "JSC_GOOG_CLASS_TARGET_INVALID",
      "Unsupported goog.defineClass expression.");

  static final DiagnosticType GOOG_CLASS_SUPER_CLASS_NOT_VALID = DiagnosticType.error(
      "JSC_GOOG_CLASS_SUPER_CLASS_NOT_VALID",
      "The super class must be null or a valid name reference");

  static final DiagnosticType GOOG_CLASS_DESCRIPTOR_NOT_VALID = DiagnosticType.error(
      "JSC_GOOG_CLASS_DESCRIPTOR_NOT_VALID",
      "The class descriptor must be an object literal");

  static final DiagnosticType GOOG_CLASS_CONSTRUCTOR_MISING = DiagnosticType.error(
      "JSC_GOOG_CLASS_CONSTRUCTOR_MISING",
      "The constructor expression is missing for the class descriptor");

  static final DiagnosticType GOOG_CLASS_MODIFIERS_NOT_VALID = DiagnosticType.error(
      "JSC_CLASS_MODIFIERS_NOT_VALID",
      "The class modifier list must be an array literal");

  static final DiagnosticType GOOG_CLASS_STATICS_NOT_VALID = DiagnosticType.error(
      "JSC_GOOG_CLASS_STATICS_NOT_VALID",
      "The class statics descriptor must be an object literal");

  private final AbstractCompiler compiler;

  public ClosureRewriteClass(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    this.compiler.process(this);

  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isVar()) {
      Node target = n.getFirstChild();
      Node value = target.getFirstChild();
      maybeRewriteClassDefinition(t, n, target, value);
    } else if (NodeUtil.isExprAssign(n)) {
      Node assign = n.getFirstChild();
      Node target = assign.getFirstChild();
      Node value = assign.getLastChild();
      maybeRewriteClassDefinition(t, n, target, value);
    }
  }

  private void maybeRewriteClassDefinition(
      NodeTraversal t, Node n, Node target, Node value) {
    if (isGoogDefineClass(value)) {
      if (!target.isQualifiedName()) {
        compiler.report(t.makeError(n, GOOG_CLASS_TARGET_INVALID));
      }
      ClassDefinition def = extractClassDefinition(t, target, value);
      if (def != null) {
        value.detachFromParent();
        target.detachFromParent();
        rewriteGoogDefineClass(n, def);
      }
    }
  }

  private static class MemberDefinition {
    final JSDocInfo info;
    final Node name;
    final Node value;

    MemberDefinition(JSDocInfo info, Node name, Node value) {
      this.info = info;
      this.name = name;
      this.value = value;
    }
  }

  private final class ClassDefinition {
    final Node name;
    final Node superClass;
    final MemberDefinition constructor;
    final List<MemberDefinition> staticProps;
    final List<MemberDefinition> props;
    final List<Node>  modifiers;

    ClassDefinition(
        Node name,
        Node superClass,
        MemberDefinition constructor,
        List<MemberDefinition> staticProps,
        List<MemberDefinition> props,
        List<Node> modifiers) {
      this.name = name;
      this.superClass = superClass;
      this.constructor = constructor;
      this.staticProps = staticProps;
      this.props = props;
      this.modifiers = modifiers;
    }
  }

  /**
   * Validates the class definition and if valid, destructively extracts
   * the class definition from the AST.
   */
  private ClassDefinition extractClassDefinition(
      NodeTraversal t, Node targetName, Node callNode) {
    // name = goog.defineClass(superClass, {...}, [modifier, ...])
    Node superClass = NodeUtil.getArgumentForCallOrNew(callNode, 0);
    if (superClass == null ||
        (!superClass.isNull() && !superClass.isQualifiedName())) {
      compiler.report(t.makeError(callNode, GOOG_CLASS_SUPER_CLASS_NOT_VALID));
      return null;
    }
    if (NodeUtil.isNullOrUndefined(superClass)) {
      superClass = null;
    }

    Node description = NodeUtil.getArgumentForCallOrNew(callNode, 1);
    if (description == null
        || !description.isObjectLit()
        || !validateObjLit(description)) {
      // report bad class definition
      compiler.report(t.makeError(callNode, GOOG_CLASS_DESCRIPTOR_NOT_VALID));
      return null;
    }

    Node constructor = extractProperty(description, "constructor");
    if (constructor == null) {
      // report missing constructor
      compiler.report(t.makeError(description, GOOG_CLASS_CONSTRUCTOR_MISING));
      return null;
    }
    JSDocInfo info = NodeUtil.getBestJSDocInfo(constructor);

    Node statics = extractProperty(description, "statics");
    if (statics != null
        && !(statics.isObjectLit() && validateObjLit(statics))) {
      compiler.report(t.makeError(statics, GOOG_CLASS_STATICS_NOT_VALID));
      return null;
    }
    if (statics == null) {
      statics = IR.objectlit();
    }

    Node modifiers = NodeUtil.getArgumentForCallOrNew(callNode, 2);
    if (modifiers != null && !modifiers.isArrayLit()) {
      compiler.report(t.makeError(modifiers, GOOG_CLASS_MODIFIERS_NOT_VALID));
      return null;
    }
    if (modifiers == null) {
      modifiers = IR.arraylit();
    }

    // Ok, now rip apart the definition into its component pieces.
    maybeDetach(constructor.getParent());  // remove the property node.
    maybeDetach(statics.getParent());
    ClassDefinition def = new ClassDefinition(
        targetName,
        maybeDetach(superClass),
        new MemberDefinition(info, null, maybeDetach(constructor)),
        objectLitToList(maybeDetach(statics)),
        objectLitToList(description),
        arrayNodeToList(modifiers));
    return def;
  }

  private Node maybeDetach(Node node) {
    if (node != null && node.getParent() != null) {
      node.detachFromParent();
    }
    return node;
  }

  // Only unquoted plain properties are currently supported.
  private boolean validateObjLit(Node objlit) {
    for (Node key : objlit.children()) {
      if (!key.isStringKey() || key.isQuotedString()) {
        return false;
      }
    }
    return true;
  }

  /**
   * @return The first property in the objlit that matches the key.
   */
  private Node extractProperty(Node objlit, String keyName) {
    for (Node keyNode : objlit.children()) {
      if (keyNode.getString().equals(keyName)) {
        return keyNode.isStringKey() ? keyNode.getFirstChild() : null;
      }
    }
    return null;
  }

  private List<MemberDefinition> objectLitToList(
      Node objlit) {
    List<MemberDefinition> result = Lists.newArrayList();
    for (Node keyNode : objlit.children()) {
      result.add(
          new MemberDefinition(
                NodeUtil.getBestJSDocInfo(keyNode),
                keyNode,
                keyNode.removeFirstChild()));
    }
    objlit.detachChildren();
    return result;
  }

  private List<Node> arrayNodeToList(Node arr) {
    Preconditions.checkState(arr == null || arr.isArrayLit());

    if (arr == null) {
      return Collections.<Node>emptyList();
    } else {
      List<Node> result = Lists.newArrayList(arr.children());
      arr.detachChildren();
      return result;
    }
  }

  private void rewriteGoogDefineClass(Node exprRoot, ClassDefinition cls) {

    // For simplicity add everything into a block, before adding it to the AST.
    Node block = IR.block();

    if (exprRoot.isVar()) {
      // example: var ctr = function(){}
      block.addChildToBack(
          IR.var(
          cls.name.cloneTree(), cls.constructor.value)
          .srcref(exprRoot).setJSDocInfo(cls.constructor.info));
    } else {
      // example: ns.ctr = function(){}
      block.addChildToBack(
          fixupSrcref(IR.exprResult(
          IR.assign(
          cls.name.cloneTree(), cls.constructor.value)
          .srcref(exprRoot).setJSDocInfo(cls.constructor.info)
          .srcref(exprRoot))).setJSDocInfo(cls.constructor.info));
    }

    if (cls.superClass != null) {
      // example: goog.inherits(ctr, superClass)
      block.addChildToBack(
          fixupSrcref(IR.exprResult(
              IR.call(
                  NodeUtil.newQualifiedNameNode(
                      compiler.getCodingConvention(), "goog.inherits")
                      .srcrefTree(cls.superClass),
                  cls.name.cloneTree(),
                  cls.superClass.cloneTree()).srcref(cls.superClass))));
    }

    for (MemberDefinition def : cls.staticProps) {
      // example: ctr.prop = value
      block.addChildToBack(
          fixupSrcref(IR.exprResult(
          fixupSrcref(IR.assign(
              IR.getprop(cls.name.cloneTree(),
                  IR.string(def.name.getString()).srcref(def.name))
                  .srcref(def.name),
              def.value)).setJSDocInfo(def.info))));
    }

    for (MemberDefinition def : cls.props) {
      // example: ctr.prototype.prop = value
      block.addChildToBack(
          fixupSrcref(IR.exprResult(
          fixupSrcref(IR.assign(
              IR.getprop(
                  fixupSrcref(IR.getprop(cls.name.cloneTree(),
                      IR.string("prototype").srcref(def.name))),
                  IR.string(def.name.getString()).srcref(def.name))
                  .srcref(def.name),
              def.value)).setJSDocInfo(def.info))));
    }

    for (Node modifier : cls.modifiers) {
      // example: modifier(ctr)
      block.addChildToBack(
          IR.exprResult(
              fixupFreeCall(IR.call(
                  modifier,
                  cls.name.cloneTree())
                  .srcref(modifier)))
              .srcref(modifier));

    }

    exprRoot.getParent().replaceChild(exprRoot, block);
    compiler.reportCodeChange();
  }

  private Node fixupSrcref(Node node) {
    node.srcref(node.getFirstChild());
    return node;
  }

  private Node fixupFreeCall(Node call) {
    if (call.getFirstChild().isName()) {
      call.putBooleanProp(Node.FREE_CALL, true);
    }
    return call;
  }

  /**
   * @return Whether the call represents a class definition.
   */
  private boolean isGoogDefineClass(Node value) {
    return value != null && value.isCall()
        && "goog.defineClass".equals(value.getFirstChild().getQualifiedName());
  }
}
