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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

  static final DiagnosticType POLYMER_MISSING_EXTERNS = DiagnosticType.error(
      "JSC_POLYMER_MISSING_EXTERNS", "Missing Polymer externs.");

  static final String VIRTUAL_FILE = "<PolymerPass.java>";

  private final AbstractCompiler compiler;
  private Node polymerElementExterns;
  private final Map<String, String> tagNameMap;
  private List<Node> polymerElementProps;

  public PolymerPass(AbstractCompiler compiler) {
    this.compiler = compiler;
    tagNameMap = TagNameToType.getMap();
    polymerElementProps = new ArrayList<>();
  }

  @Override
  public void process(Node externs, Node root) {
    FindPolymerExterns externsCallback = new FindPolymerExterns();
    NodeTraversal.traverse(compiler, externs, externsCallback);
    polymerElementExterns = externsCallback.polymerElementExterns;
    polymerElementProps = externsCallback.getpolymerElementProps();

    if (polymerElementExterns == null) {
      compiler.report(JSError.make(externs, POLYMER_MISSING_EXTERNS));
      return;
    }

    hotSwapScript(root, null);
  }

  /**
   * Finds the externs for the PolymerElement base class and all of its properties.
   */
  private static class FindPolymerExterns extends AbstractPostOrderCallback {
    private Node polymerElementExterns;
    private ImmutableList.Builder<Node> polymerElementProps;
    private static final String POLYMER_ELEMENT_NAME = "PolymerElement";

    public FindPolymerExterns() {
      polymerElementProps = ImmutableList.builder();
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (ispolymerElementExterns(n)) {
        polymerElementExterns = n;
      } else if (isPolymerElementPropExpr(n)) {
        polymerElementProps.add(n);
      }
    }

    /**
     * @return Whether the node is the declaration of PolymerElement.
     */
    private boolean ispolymerElementExterns(Node value) {
      return value != null && value.isVar()
          && value.getFirstChild().matchesQualifiedName(POLYMER_ELEMENT_NAME);
    }

    /**
     * @return Whether the node is an expression result of an assignment to a property of
     * PolymerElement.
     */
    private boolean isPolymerElementPropExpr(Node value) {
      return value != null && value.isExprResult() && value.getFirstChild().isAssign()
          && value.getFirstChild().getFirstChild().isGetProp()
          && NodeUtil.getRootOfQualifiedName(
              value.getFirstChild().getFirstChild()).matchesQualifiedName(POLYMER_ELEMENT_NAME);
    }

    public List<Node> getpolymerElementProps() {
      return polymerElementProps.build();
    }
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
    final String nativeBaseElement;
    final List<MemberDefinition> props;

    ClassDefinition(Node target, JSDocInfo classInfo, MemberDefinition constructor,
        String nativeBaseElement, List<MemberDefinition> props) {
      this.target = target;
      this.classInfo = classInfo;
      this.constructor = constructor;
      this.nativeBaseElement = nativeBaseElement;
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

    Node baseClass = NodeUtil.getFirstPropMatchingKey(description, "extends");
    String nativeBaseElement = baseClass == null ? null : baseClass.getString();

    JSDocInfo info = NodeUtil.getBestJSDocInfo(constructor);

    ClassDefinition def = new ClassDefinition(target, classInfo,
        new MemberDefinition(info, null, constructor), nativeBaseElement,
        objectLitToList(description));
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

    this.appendPolymerElementExterns(cls);
    JSDocInfoBuilder constructorDoc = this.getConstructorDoc(cls);

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
   * Duplicates the PolymerElement externs with a different element base class if needed.
   * For example, if the base class is HTMLInputElement, then a class PolymerInputElement will be
   * added. If the element does not extend a native HTML element, this method is a no-op.
   */
  private void appendPolymerElementExterns(final ClassDefinition cls) {
    if (cls.nativeBaseElement == null) {
      return;
    }

    Node block = IR.block();

    Node baseExterns = polymerElementExterns.cloneTree();
    String polymerElementType = this.getPolymerElementType(cls);
    baseExterns.getFirstChild().setString(polymerElementType);

    String elementType = tagNameMap.get(cls.nativeBaseElement);
    JSTypeExpression elementBaseType = new JSTypeExpression(
        new Node(Token.BANG, IR.string(elementType)), VIRTUAL_FILE);
    JSDocInfoBuilder baseDocs = JSDocInfoBuilder.copyFrom(baseExterns.getJSDocInfo());
    baseDocs.changeBaseType(elementBaseType);
    baseExterns.setJSDocInfo(baseDocs.build(baseExterns));
    block.addChildToBack(baseExterns);

    for (Node baseProp : polymerElementProps) {
      Node newProp = baseProp.cloneTree();
      Node newPropRootName = NodeUtil.getRootOfQualifiedName(
           newProp.getFirstChild().getFirstChild());
      newPropRootName.setString(polymerElementType);
      block.addChildToBack(newProp);
    }

    Node parent = polymerElementExterns.getParent();
    Node stmts = block.removeChildren();
    parent.addChildrenAfter(stmts, polymerElementExterns);

    compiler.reportCodeChange();
  }

  /**
   * @return The proper constructor doc for the Polymer call.
   */
  private JSDocInfoBuilder getConstructorDoc(final ClassDefinition cls) {
    JSDocInfoBuilder constructorDoc = new JSDocInfoBuilder(true);
    constructorDoc.recordConstructor();

    JSTypeExpression baseType = new JSTypeExpression(
        new Node(Token.BANG, IR.string(this.getPolymerElementType(cls))), VIRTUAL_FILE);

    constructorDoc.recordBaseType(baseType);
    return constructorDoc;
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
   * @return The PolymerElement type string for a class definition.
   */
  private static String getPolymerElementType(final ClassDefinition cls) {
    return String.format("Polymer%sElement", cls.nativeBaseElement == null ? ""
        : CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, cls.nativeBaseElement));
  }

  /**
   * @return Whether the call represents a call to Polymer.
   */
  private static boolean isPolymerCall(Node value) {
    return value != null && value.isCall() && value.getFirstChild().matchesQualifiedName("Polymer");
  }
}
