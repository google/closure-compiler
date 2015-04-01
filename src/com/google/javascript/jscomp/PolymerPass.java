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

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.List;

/**
 * Rewrites "Polymer({})" calls into a form that is suitable for type checking and dead code
 * elimination. Also ensures proper format and types.
 *
 * @author jlklein@google.com (Jeremy Klein)
 */
final class PolymerPass extends AbstractPostOrderCallback implements HotSwapCompilerPass {

  // Errors
  static final DiagnosticType POLYMER_DESCRIPTOR_NOT_VALID = DiagnosticType.error(
      "JSC_POLYMER_DESCRIPTOR_NOT_VALID", "The class descriptor must be an object literal.");

  static final DiagnosticType POLYMER_MISSING_IS = DiagnosticType.error("JSC_POLYMER_MISSING_IS",
      "The class descriptor must include an 'is' property.");

  static final DiagnosticType POLYMER_UNEXPECTED_PARAMS = DiagnosticType.error(
      "JSC_POLYMER_UNEXPECTED_PARAMS", "The class definition has too many arguments.");

  static final String VIRTUAL_FILE = "<PolymerPass.java>";

  private final AbstractCompiler compiler;

  public PolymerPass(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    hotSwapScript(root, null);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (isPolymerCall(n)) {
      rewriteClassDefinition(n, parent);
    }
  }

  private void rewriteClassDefinition(Node n, Node parent) {
    ClassDefinition def = extractClassDefinition(n);
    if (def != null) {
      if (NodeUtil.isNameDeclaration(parent.getParent()) || parent.isAssign()) {
        rewritePolymerClass(parent.getParent(), def);
      } else {
        rewritePolymerClass(parent, def);
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

  private static final class ClassDefinition {
    final Node target;
    final JSDocInfo classInfo;
    final MemberDefinition constructor;
    final List<MemberDefinition> props;

    ClassDefinition(Node target, JSDocInfo classInfo, MemberDefinition constructor,
        List<MemberDefinition> props) {
      this.target = target;
      this.classInfo = classInfo;
      this.constructor = constructor;
      this.props = props;
    }
  }

  /**
   * Validates the class definition and if valid, destructively extracts the class definition from
   * the AST.
   */
  private ClassDefinition extractClassDefinition(Node callNode) {
    Node description = NodeUtil.getArgumentForCallOrNew(callNode, 0);
    if (description == null || !description.isObjectLit()) {
      // report bad class definition
      compiler.report(JSError.make(callNode, POLYMER_DESCRIPTOR_NOT_VALID));
      return null;
    }

    int paramCount = callNode.getChildCount() - 1;
    if (paramCount != 1) {
      compiler.report(JSError.make(callNode, POLYMER_UNEXPECTED_PARAMS));
      return null;
    }

    Node elName = NodeUtil.getFirstPropMatchingKey(description, "is");
    if (elName == null) {
      compiler.report(JSError.make(callNode, POLYMER_MISSING_IS));
      return null;
    }

    String elNameString = CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, elName.getString());
    elNameString += "Element";

    Node target;
    if (NodeUtil.isNameDeclaration(callNode.getParent().getParent())) {
      target = IR.name(callNode.getParent().getString());
    } else if (callNode.getParent().isAssign()) {
      target = callNode.getParent().getFirstChild().cloneTree();
    } else {
      target = IR.name(elNameString);
    }

    target.useSourceInfoIfMissingFrom(callNode);
    JSDocInfo classInfo = NodeUtil.getBestJSDocInfo(target);


    Node constructor = NodeUtil.getFirstPropMatchingKey(description, "constructor");
    if (constructor == null) {
      constructor = IR.function(IR.name(""), IR.paramList(), IR.block());
      constructor.useSourceInfoFromForTree(callNode);
    }

    JSDocInfo info = NodeUtil.getBestJSDocInfo(constructor);

    ClassDefinition def = new ClassDefinition(target, classInfo,
        new MemberDefinition(info, null, constructor), objectLitToList(description));
    return def;
  }

  private static List<MemberDefinition> objectLitToList(Node objlit) {
    ImmutableList.Builder<MemberDefinition> members = ImmutableList.builder();
    for (Node keyNode : objlit.children()) {
      members.add(new MemberDefinition(NodeUtil.getBestJSDocInfo(keyNode), keyNode,
          keyNode.getFirstChild()));
    }
    return members.build();
  }

  private void rewritePolymerClass(Node exprRoot, final ClassDefinition cls) {
    // Add {@code @lends} to the object literal.
    Node call = exprRoot.getFirstChild();
    if (call.isAssign()) {
      call = call.getChildAtIndex(1);
    } else if (call.isName()) {
      call = call.getFirstChild();
    }

    Node objLit = NodeUtil.getArgumentForCallOrNew(call, 0);
    JSDocInfoBuilder objLitDoc = new JSDocInfoBuilder(true);
    objLitDoc.recordLends(cls.target.getQualifiedName() + ".prototype");
    objLit.setJSDocInfo(objLitDoc.build(objLit));

    // For simplicity add everything into a block, before adding it to the AST.
    Node block = IR.block();

    JSDocInfoBuilder constructorDoc = new JSDocInfoBuilder(true);
    constructorDoc.recordConstructor();
    JSTypeExpression baseType = new JSTypeExpression(
        new Node(Token.BANG, IR.string("PolymerBase")), VIRTUAL_FILE);
    constructorDoc.recordBaseType(baseType);

    if (cls.target.isGetProp()) {
      // foo.bar = Polymer({...});
      Node assign = IR.assign(
          cls.target.cloneTree(),
          cls.constructor.value.cloneTree());
      assign.setJSDocInfo(constructorDoc.build(assign));
      Node exprResult = IR.exprResult(assign);
      block.addChildToBack(exprResult);
    } else {
      // var foo = Polymer({...}); OR Polymer({...});
      Node var = IR.var(cls.target.cloneTree(), cls.constructor.value.cloneTree());
      var.setJSDocInfo(constructorDoc.build(var));
      block.addChildToBack(var);
    }

    // TODO(jlklein): Extract all the props in "properties" and copy them to the prototype.

    block.useSourceInfoFromForTree(exprRoot);
    Node parent = exprRoot.getParent();
    Node stmts = block.removeChildren();
    // TODO(jlklein): Create an addChildrenBefore and use that instead.
    parent.addChildBefore(stmts, exprRoot);

    if (exprRoot.isVar()) {
      Node assignExpr = varToAssign(exprRoot);
      parent.replaceChild(exprRoot, assignExpr);
    }

    compiler.reportCodeChange();
  }

  /**
   * @return An assign replacing the equivalent var declaration.
   */
  private static Node varToAssign(Node var) {
    Node assign = IR.assign(
        IR.name(var.getFirstChild().getString()),
        var.getFirstChild().removeFirstChild());
    return IR.exprResult(assign).useSourceInfoFromForTree(var);
  }

  /**
   * @return Whether the call represents a call to Polymer.
   */
  private static boolean isPolymerCall(Node value) {
    return value != null && value.isCall() && value.getFirstChild().matchesQualifiedName("Polymer");
  }
}
