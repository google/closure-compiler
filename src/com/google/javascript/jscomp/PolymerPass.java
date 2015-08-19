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
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.GlobalNamespace.Name;
import com.google.javascript.jscomp.GlobalNamespace.Ref;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rewrites "Polymer({})" calls into a form that is suitable for type checking and dead code
 * elimination. Also ensures proper format and types.
 *
 * <p>Only works with Polymer version: 0.8
 *
 * @author jlklein@google.com (Jeremy Klein)
 */
final class PolymerPass extends AbstractPostOrderCallback implements HotSwapCompilerPass {

  // TODO(jlklein): Switch back to an error when everyone is upgraded to Polymer 1.0
  static final DiagnosticType POLYMER_DESCRIPTOR_NOT_VALID = DiagnosticType.warning(
      "JSC_POLYMER_DESCRIPTOR_NOT_VALID",
      "The argument to Polymer() is not an obj lit (perhaps because this is a pre-Polymer-1.0 "
      + "call). Ignoring this call.");

  static final DiagnosticType POLYMER_INVALID_DECLARATION = DiagnosticType.error(
      "JSC_POLYMER_INVALID_DECLARAION", "A Polymer() declaration cannot use 'let' or 'const'.");

  // Errors
  static final DiagnosticType POLYMER_MISSING_IS = DiagnosticType.error("JSC_POLYMER_MISSING_IS",
      "The class descriptor must include an 'is' property.");

  static final DiagnosticType POLYMER_UNEXPECTED_PARAMS = DiagnosticType.error(
      "JSC_POLYMER_UNEXPECTED_PARAMS", "The class definition has too many arguments.");

  static final DiagnosticType POLYMER_MISSING_EXTERNS = DiagnosticType.error(
      "JSC_POLYMER_MISSING_EXTERNS", "Missing Polymer externs.");

  static final DiagnosticType POLYMER_INVALID_PROPERTY = DiagnosticType.error(
      "JSC_POLYMER_INVALID_PROPERTY", "Polymer property has an invalid or missing type.");

  static final DiagnosticType POLYMER_INVALID_BEHAVIOR_ARRAY = DiagnosticType.error(
      "JSC_POLYMER_INVALID_BEHAVIOR_ARRAY", "The behaviors property must be an array literal.");

  static final DiagnosticType POLYMER_UNQUALIFIED_BEHAVIOR = DiagnosticType.error(
      "JSC_POLYMER_UNQUALIFIED_BEHAVIOR",
      "Behaviors must be global, fully qualified names which are declared as object literals or "
      + "array literals of other valid Behaviors.");

  static final DiagnosticType POLYMER_UNANNOTATED_BEHAVIOR = DiagnosticType.error(
      "JSC_POLYMER_UNANNOTATED_BEHAVIOR",
      "Behavior declarations must be annotated with @polymerBehavior.");

  static final DiagnosticType POLYMER_SHORTHAND_NOT_SUPPORTED = DiagnosticType.error(
      "JSC_POLYMER_SHORTHAND_NOT_SUPPORTED",
      "Shorthand assignment in object literal is not allowed in "
      + "Polymer call arguments");

  static final String VIRTUAL_FILE = "<PolymerPass.java>";

  private final AbstractCompiler compiler;
  private Node polymerElementExterns;
  private Set<String> nativeExternsAdded;
  private final Map<String, String> tagNameMap;
  private List<Node> polymerElementProps;
  private final ImmutableSet<String> behaviorNamesNotToCopy;
  private GlobalNamespace globalNames;

  public PolymerPass(AbstractCompiler compiler) {
    this.compiler = compiler;
    tagNameMap = TagNameToType.getMap();
    polymerElementProps = new ArrayList<>();
    nativeExternsAdded = new HashSet<>();
    behaviorNamesNotToCopy = ImmutableSet.of(
        "created", "attached", "detached", "attributeChanged", "configure", "ready",
        "properties", "listeners", "observers", "hostAttributes");
  }

  @Override
  public void process(Node externs, Node root) {
    FindPolymerExterns externsCallback = new FindPolymerExterns();
    NodeTraversal.traverseEs6(compiler, externs, externsCallback);
    polymerElementExterns = externsCallback.polymerElementExterns;
    polymerElementProps = externsCallback.getpolymerElementProps();

    if (polymerElementExterns == null) {
      compiler.report(JSError.make(externs, POLYMER_MISSING_EXTERNS));
      return;
    }

    globalNames = new GlobalNamespace(compiler, externs, root);

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
      if (isPolymerElementExterns(n)) {
        polymerElementExterns = n;
      } else if (isPolymerElementPropExpr(n)) {
        polymerElementProps.add(n);
      }
    }

    /**
     * @return Whether the node is the declaration of PolymerElement.
     */
    private boolean isPolymerElementExterns(Node value) {
      return value != null && value.isVar()
          && value.getFirstChild().matchesQualifiedName(POLYMER_ELEMENT_NAME);
    }

    /**
     * @return Whether the node is an expression result of an assignment to a property of
     * PolymerElement.
     */
    private boolean isPolymerElementPropExpr(Node value) {
      return value != null && value.isExprResult()
          && value.getFirstChild().getFirstChild() != null
          && value.getFirstChild().getFirstChild().isGetProp()
          && NodeUtil.getRootOfQualifiedName(
              value.getFirstChild().getFirstChild()).matchesQualifiedName(POLYMER_ELEMENT_NAME);
    }

    public List<Node> getpolymerElementProps() {
      return polymerElementProps.build();
    }
  }

  /**
   * For every Polymer Behavior, strip property type annotations and add suppress checktypes on
   * functions.
   */
  private static class SuppressBehaviors extends AbstractPostOrderCallback {
    private final AbstractCompiler compiler;

    public SuppressBehaviors(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (isBehavior(n)) {
        if (!n.isVar() && !n.isAssign()) {
          compiler.report(JSError.make(n, POLYMER_UNQUALIFIED_BEHAVIOR));
          return;
        }

        // Add @nocollapse.
        JSDocInfoBuilder newDocs = JSDocInfoBuilder.maybeCopyFrom(n.getJSDocInfo());
        newDocs.recordNoCollapse();
        n.setJSDocInfo(newDocs.build());

        Node behaviorValue = n.getChildAtIndex(1);
        if (n.isVar()) {
          behaviorValue = n.getFirstChild().getFirstChild();
        }
        suppressBehavior(behaviorValue);
      }
    }

    /**
     * @return Whether the node is the declaration of a Behavior.
     */
    private boolean isBehavior(Node value) {
      return value.getJSDocInfo() != null && value.getJSDocInfo().isPolymerBehavior();
    }

    /**
     * Strip property type annotations and add suppress checkTypes and globalThis on functions.
     */
    private void suppressBehavior(Node behaviorValue) {
      if (behaviorValue == null) {
        compiler.report(JSError.make(behaviorValue, POLYMER_UNQUALIFIED_BEHAVIOR));
        return;
      }

      if (behaviorValue.isArrayLit()) {
        for (Node child : behaviorValue.children()) {
          suppressBehavior(child);
        }
      } else if (behaviorValue.isObjectLit()) {
        stripPropertyTypes(behaviorValue);
        addBehaviorSuppressions(behaviorValue);
      }
    }

    private void stripPropertyTypes(Node behaviorValue) {
      List<MemberDefinition> properties = extractProperties(behaviorValue);
      for (MemberDefinition property : properties) {
        property.name.removeProp(Node.JSDOC_INFO_PROP);
      }
    }

    private void suppressDefaultValues(Node behaviorValue) {
      for (MemberDefinition property : extractProperties(behaviorValue)) {
        if (!property.value.isObjectLit()) {
          continue;
        }

        Node defaultValue = NodeUtil.getFirstPropMatchingKey(property.value, "value");
        if (defaultValue == null || !defaultValue.isFunction()) {
          continue;
        }
        Node defaultValueKey = defaultValue.getParent();
        JSDocInfoBuilder suppressDoc =
            JSDocInfoBuilder.maybeCopyFrom(defaultValueKey.getJSDocInfo());
        suppressDoc.addSuppression("checkTypes");
        suppressDoc.addSuppression("globalThis");
        defaultValueKey.setJSDocInfo(suppressDoc.build());
      }
    }

    private void addBehaviorSuppressions(Node behaviorValue) {
      for (Node keyNode : behaviorValue.children()) {
        if (keyNode.getFirstChild().isFunction()) {
          keyNode.removeProp(Node.JSDOC_INFO_PROP);
          JSDocInfoBuilder suppressDoc = new JSDocInfoBuilder(true);
          suppressDoc.addSuppression("checkTypes");
          suppressDoc.addSuppression("globalThis");
          keyNode.setJSDocInfo(suppressDoc.build());
        }
      }
      suppressDefaultValues(behaviorValue);
    }
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverseEs6(compiler, scriptRoot, this);
    SuppressBehaviors suppressBehaviorsCallback = new SuppressBehaviors(compiler);
    NodeTraversal.traverseEs6(compiler, scriptRoot, suppressBehaviorsCallback);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (isPolymerCall(n)) {
      rewriteClassDefinition(n, parent, t);
    }
  }

  private void rewriteClassDefinition(Node n, Node parent, NodeTraversal t) {
    if (parent.getParent().isConst() || parent.getParent().isLet()) {
      compiler.report(JSError.make(n, POLYMER_INVALID_DECLARATION));
      return;
    }
    ClassDefinition def = extractClassDefinition(n);
    if (def != null) {
      if (NodeUtil.isNameDeclaration(parent.getParent()) || parent.isAssign()) {
        rewritePolymerClass(parent.getParent(), def, t);
      } else {
        rewritePolymerClass(parent, def, t);
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

  private static final class BehaviorDefinition {
    final List<MemberDefinition> props;
    final List<MemberDefinition> functionsToCopy;
    final List<MemberDefinition> nonPropertyMembersToCopy;
    final boolean isGlobalDeclaration;

    BehaviorDefinition(
        List<MemberDefinition> props, List<MemberDefinition> functionsToCopy,
        List<MemberDefinition> nonPropertyMembersToCopy, boolean isGlobalDeclaration) {
      this.props = props;
      this.functionsToCopy = functionsToCopy;
      this.nonPropertyMembersToCopy = nonPropertyMembersToCopy;
      this.isGlobalDeclaration = isGlobalDeclaration;
    }
  }

  private static final class ClassDefinition {
    /** The target node (LHS) for the Polymer element definition. */
    final Node target;

    /** The object literal passed to the call to the Polymer() function. */
    final Node descriptor;

    /** The constructor function for the element. */
    final MemberDefinition constructor;

    /** The name of the native HTML element which this element extends. */
    final String nativeBaseElement;

    /** Properties declared in the Polymer "properties" block. */
    final List<MemberDefinition> props;

    /** Flattened list of behavior definitions used by this element. */
    final List<BehaviorDefinition> behaviors;

    ClassDefinition(Node target, Node descriptor, JSDocInfo classInfo, MemberDefinition constructor,
        String nativeBaseElement, List<MemberDefinition> props,
        List<BehaviorDefinition> behaviors) {
      this.target = target;
      Preconditions.checkState(descriptor.isObjectLit());
      this.descriptor = descriptor;
      this.constructor = constructor;
      this.nativeBaseElement = nativeBaseElement;
      this.props = props;
      this.behaviors = behaviors;
    }
  }

  /**
   * Validates the class definition and if valid, destructively extracts the class definition from
   * the AST.
   */
  private ClassDefinition extractClassDefinition(Node callNode) {
    Node descriptor = NodeUtil.getArgumentForCallOrNew(callNode, 0);
    if (descriptor == null || !descriptor.isObjectLit()) {
      // report bad class definition
      compiler.report(JSError.make(callNode, POLYMER_DESCRIPTOR_NOT_VALID));
      return null;
    }

    int paramCount = callNode.getChildCount() - 1;
    if (paramCount != 1) {
      compiler.report(JSError.make(callNode, POLYMER_UNEXPECTED_PARAMS));
      return null;
    }

    Node elName = NodeUtil.getFirstPropMatchingKey(descriptor, "is");
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

    JSDocInfo ctorInfo = null;
    Node constructor = NodeUtil.getFirstPropMatchingKey(descriptor, "factoryImpl");
    if (constructor == null) {
      constructor = IR.function(IR.name(""), IR.paramList(), IR.block());
      constructor.useSourceInfoFromForTree(callNode);
    } else {
      ctorInfo = NodeUtil.getBestJSDocInfo(constructor);
    }

    Node baseClass = NodeUtil.getFirstPropMatchingKey(descriptor, "extends");
    String nativeBaseElement = baseClass == null ? null : baseClass.getString();

    Node behaviorArray = NodeUtil.getFirstPropMatchingKey(descriptor, "behaviors");
    List<BehaviorDefinition> behaviors = extractBehaviors(behaviorArray);
    List<MemberDefinition> allProperties = new LinkedList<>();
    for (BehaviorDefinition behavior : behaviors) {
      overwriteMembersIfPresent(allProperties, behavior.props);
    }
    overwriteMembersIfPresent(allProperties, extractProperties(descriptor));

    ClassDefinition def = new ClassDefinition(target, descriptor, classInfo,
        new MemberDefinition(ctorInfo, null, constructor), nativeBaseElement, allProperties,
        behaviors);
    return def;
  }

  /**
   * Appends a list of new MemberDefinitions to the end of a list and removes any previous
   * MemberDefinition in the list which has the same name as the new member.
   */
  private static void overwriteMembersIfPresent(
      List<MemberDefinition> list, List<MemberDefinition> newMembers) {
    for (MemberDefinition newMember : newMembers) {
      for (MemberDefinition member : list) {
        if (member.name.getString().equals(newMember.name.getString())) {
          list.remove(member);
          break;
        }
      }
      list.add(newMember);
    }
  }

  /**
   * Extracts all Behaviors from an array recursively. The array must be an array
   * literal whose value is known at compile-time. Entries in the array can be
   * object literals or array literals (of other behaviors). Behavior names must be
   * global, fully qualified names.
   * @see https://github.com/Polymer/polymer/blob/0.8-preview/PRIMER.md#behaviors
   * @return A list of all {@code BehaviorDefinitions} in the array.
   */
  private List<BehaviorDefinition> extractBehaviors(Node behaviorArray) {
    if (behaviorArray == null) {
      return ImmutableList.of();
    }

    if (!behaviorArray.isArrayLit()) {
      compiler.report(JSError.make(behaviorArray, POLYMER_INVALID_BEHAVIOR_ARRAY));
      return ImmutableList.of();
    }

    ImmutableList.Builder<BehaviorDefinition> behaviors = ImmutableList.builder();
    for (Node behaviorName : behaviorArray.children()) {
      if (behaviorName.isObjectLit()) {
        this.switchDollarSignPropsToBrackets(behaviorName);
        this.quoteListenerAndHostAttributeKeys(behaviorName);
        behaviors.add(new BehaviorDefinition(
            extractProperties(behaviorName), getBehaviorFunctionsToCopy(behaviorName),
            getNonPropertyMembersToCopy(behaviorName), !NodeUtil.isInFunction(behaviorName)));
        continue;
      }

      Name behaviorGlobalName = globalNames.getSlot(behaviorName.getQualifiedName());
      boolean isGlobalDeclaration = true;
      if (behaviorGlobalName == null) {
        compiler.report(JSError.make(behaviorName, POLYMER_UNQUALIFIED_BEHAVIOR));
        continue;
      }

      Ref behaviorDeclaration = behaviorGlobalName.getDeclaration();

      // Use any set as a backup declaration, even if it's local.
      if (behaviorDeclaration == null) {
        List<Ref> behaviorRefs = behaviorGlobalName.getRefs();
        for (Ref ref : behaviorRefs) {
          if (ref.isSet()) {
            isGlobalDeclaration = false;
            behaviorDeclaration = ref;
            break;
          }
        }
      }

      if (behaviorDeclaration == null) {
        compiler.report(JSError.make(behaviorName, POLYMER_UNQUALIFIED_BEHAVIOR));
        continue;
      }

      Node behaviorDeclarationNode = behaviorDeclaration.getNode();
      JSDocInfo behaviorInfo = NodeUtil.getBestJSDocInfo(behaviorDeclarationNode);
      if (behaviorInfo == null || !behaviorInfo.isPolymerBehavior()) {
        compiler.report(JSError.make(behaviorDeclarationNode, POLYMER_UNANNOTATED_BEHAVIOR));
      }

      Node behaviorValue = NodeUtil.getRValueOfLValue(behaviorDeclarationNode);

      if (behaviorValue == null) {
        compiler.report(JSError.make(behaviorName, POLYMER_UNQUALIFIED_BEHAVIOR));
      } else if (behaviorValue.isArrayLit()) {
        // Individual behaviors can also be arrays of behaviors. Parse them recursively.
        behaviors.addAll(extractBehaviors(behaviorValue));
      } else if (behaviorValue.isObjectLit()) {
        this.switchDollarSignPropsToBrackets(behaviorValue);
        this.quoteListenerAndHostAttributeKeys(behaviorValue);
        behaviors.add(new BehaviorDefinition(
            extractProperties(behaviorValue), getBehaviorFunctionsToCopy(behaviorValue),
            getNonPropertyMembersToCopy(behaviorValue), isGlobalDeclaration));
      } else {
        compiler.report(JSError.make(behaviorName, POLYMER_UNQUALIFIED_BEHAVIOR));
      }
    }

    return behaviors.build();
  }

  /**
   * @return A list of functions from a behavior which should be copied to the element prototype.
   */
  private List<MemberDefinition> getBehaviorFunctionsToCopy(Node behaviorObjLit) {
    Preconditions.checkState(behaviorObjLit.isObjectLit());
    ImmutableList.Builder<MemberDefinition> functionsToCopy = ImmutableList.builder();

    for (Node keyNode : behaviorObjLit.children()) {
      if ((keyNode.isStringKey() && keyNode.getFirstChild().isFunction()
          || keyNode.isMemberFunctionDef())
          && !behaviorNamesNotToCopy.contains(keyNode.getString())) {
        functionsToCopy.add(new MemberDefinition(NodeUtil.getBestJSDocInfo(keyNode), keyNode,
          keyNode.getFirstChild()));
      }
    }

    return functionsToCopy.build();
  }

  /**
   * @return A list of MemberDefinitions in a behavior which are not in the properties block, but
   *     should still be copied to the element prototype.
   */
  private List<MemberDefinition> getNonPropertyMembersToCopy(Node behaviorObjLit) {
    Preconditions.checkState(behaviorObjLit.isObjectLit());
    ImmutableList.Builder<MemberDefinition> membersToCopy = ImmutableList.builder();

    for (Node keyNode : behaviorObjLit.children()) {
      if (keyNode.isGetterDef() || (keyNode.isStringKey() && !keyNode.getFirstChild().isFunction()
          && !behaviorNamesNotToCopy.contains(keyNode.getString()))) {
        membersToCopy.add(new MemberDefinition(NodeUtil.getBestJSDocInfo(keyNode), keyNode,
          keyNode.getFirstChild()));
      }
    }

    return membersToCopy.build();
  }

  private static List<MemberDefinition> extractProperties(Node descriptor) {
    Node properties = NodeUtil.getFirstPropMatchingKey(descriptor, "properties");
    if (properties == null) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<MemberDefinition> members = ImmutableList.builder();
    for (Node keyNode : properties.children()) {
      members.add(new MemberDefinition(NodeUtil.getBestJSDocInfo(keyNode), keyNode,
          keyNode.getFirstChild()));
    }
    return members.build();
  }

  private void rewritePolymerClass(Node exprRoot, final ClassDefinition cls, NodeTraversal t) {
    // Add {@code @lends} to the object literal.
    Node call = exprRoot.getFirstChild();
    if (call.isAssign()) {
      call = call.getChildAtIndex(1);
    } else if (call.isName()) {
      call = call.getFirstChild();
    }

    Node objLit = cls.descriptor;
    if (hasShorthandAssignment(objLit)){
      compiler.report(JSError.make(objLit, POLYMER_SHORTHAND_NOT_SUPPORTED));
      return;
    }

    JSDocInfoBuilder objLitDoc = new JSDocInfoBuilder(true);
    objLitDoc.recordLends(cls.target.getQualifiedName() + ".prototype");
    objLit.setJSDocInfo(objLitDoc.build());

    this.addTypesToFunctions(objLit, cls.target.getQualifiedName());
    this.switchDollarSignPropsToBrackets(objLit);
    this.quoteListenerAndHostAttributeKeys(objLit);

    // For simplicity add everything into a block, before adding it to the AST.
    Node block = IR.block();

    if (cls.nativeBaseElement != null) {
      this.appendPolymerElementExterns(cls);
    }
    JSDocInfoBuilder constructorDoc = this.getConstructorDoc(cls);

    // Remove the original constructor JS docs from the objlit.
    Node ctorKey = cls.constructor.value.getParent();
    if (ctorKey != null) {
      ctorKey.removeProp(Node.JSDOC_INFO_PROP);
    }

    if (cls.target.isGetProp()) {
      // foo.bar = Polymer({...});
      Node assign = IR.assign(
          cls.target.cloneTree(),
          cls.constructor.value.cloneTree());
      assign.setJSDocInfo(constructorDoc.build());
      Node exprResult = IR.exprResult(assign);
      block.addChildToBack(exprResult);
    } else {
      // var foo = Polymer({...}); OR Polymer({...});
      Node var = IR.var(cls.target.cloneTree(), cls.constructor.value.cloneTree());
      var.setJSDocInfo(constructorDoc.build());
      block.addChildToBack(var);
    }

    appendPropertiesToBlock(cls, block, cls.target.getQualifiedName() + ".prototype.");
    appendBehaviorMembersToBlock(cls, block);
    List<MemberDefinition> readOnlyProps = parseReadOnlyProperties(cls, block);
    addInterfaceExterns(cls, readOnlyProps);
    removePropertyDocs(objLit);

    block.useSourceInfoIfMissingFromForTree(exprRoot);
    Node stmts = block.removeChildren();
    Node parent = exprRoot.getParent();

    // If the call to Polymer() is not in the global scope and the assignment target is not
    // namespaced (which likely means it's exported to the global scope), put the type declaration
    // into the global scope at the start of the current script.
    //
    // This avoids unknown type warnings which are a result of the compiler's poor understanding of
    // types declared inside IIFEs or any non-global scope. We should revisit this decision after
    // moving to the new type inference system which should be able to infer these types better.
    if (!t.getScope().isGlobal() && !cls.target.isGetProp()) {
      Node scriptNode = NodeUtil.getEnclosingScript(exprRoot);
      scriptNode.addChildrenToFront(stmts);
    } else {
      Node beforeRoot = parent.getChildBefore(exprRoot);
      if (beforeRoot == null) {
        parent.addChildrenToFront(stmts);
      } else {
        parent.addChildrenAfter(stmts, beforeRoot);
      }
    }

    if (exprRoot.isVar()) {
      Node assignExpr = varToAssign(exprRoot);
      parent.replaceChild(exprRoot, assignExpr);
    }

    compiler.reportCodeChange();
  }

  /**
   * Add an @this annotation to all functions in the objLit.
   */
  private void addTypesToFunctions(Node objLit, String thisType) {
    Preconditions.checkState(objLit.isObjectLit());
    for (Node keyNode : objLit.children()) {
      Node value = keyNode.getLastChild();
      if (value != null && value.isFunction()) {
        JSDocInfoBuilder fnDoc = JSDocInfoBuilder.maybeCopyFrom(keyNode.getJSDocInfo());
        fnDoc.recordThisType(new JSTypeExpression(
            new Node(Token.BANG, IR.string(thisType)), VIRTUAL_FILE));
        keyNode.setJSDocInfo(fnDoc.build());
      }
    }

    // Add @this and @return to default property values.
    for (MemberDefinition property : extractProperties(objLit)) {
      if (!property.value.isObjectLit()) {
        continue;
      }
      if (hasShorthandAssignment(property.value)){
        compiler.report(JSError.make(property.value, POLYMER_SHORTHAND_NOT_SUPPORTED));
        return;
      }

      Node defaultValue = NodeUtil.getFirstPropMatchingKey(property.value, "value");
      if (defaultValue == null || !defaultValue.isFunction()) {
        continue;
      }
      Node defaultValueKey = defaultValue.getParent();
      JSDocInfoBuilder fnDoc = JSDocInfoBuilder.maybeCopyFrom(defaultValueKey.getJSDocInfo());
      fnDoc.recordThisType(new JSTypeExpression(
          new Node(Token.BANG, IR.string(thisType)), VIRTUAL_FILE));
      fnDoc.recordReturnType(getTypeFromProperty(property));
      defaultValueKey.setJSDocInfo(fnDoc.build());
    }
  }

  /**
   * Switches all "this.$.foo" to "this.$['foo']".
   */
  private void switchDollarSignPropsToBrackets(Node objLit) {
    Preconditions.checkState(objLit.isObjectLit());
    for (Node keyNode : objLit.children()) {
      Node value = keyNode.getFirstChild();
      if (value != null && value.isFunction()) {
        NodeUtil.visitPostOrder(
            value.getLastChild(),
            new NodeUtil.Visitor() {
              @Override
              public void visit(Node n) {
                if (n.isString() && n.getString().equals("$") && n.getParent().isGetProp()
                    && n.getParent().getParent().isGetProp()) {
                  Node dollarChildProp = n.getParent().getParent();
                  dollarChildProp.setType(Token.GETELEM);
                  compiler.reportCodeChange();
                }
              }
            },
            Predicates.<Node>alwaysTrue());
      }
    }
  }

  /**
   * Makes sure that the keys for listeners and hostAttributes blocks are quoted to avoid renaming.
   */
  private void quoteListenerAndHostAttributeKeys(Node objLit) {
    Preconditions.checkState(objLit.isObjectLit());
    for (Node keyNode : objLit.children()) {
      if (keyNode.isComputedProp()) {
        continue;
      }
      if (!keyNode.getString().equals("listeners")
          && !keyNode.getString().equals("hostAttributes")) {
        continue;
      }
      for (Node keyToQuote : keyNode.getFirstChild().children()) {
        keyToQuote.setQuotedString();
      }
    }
  }

  /**
   * Appends all properties in the ClassDefinition to the prototype of the custom element.
   */
  private void appendPropertiesToBlock(final ClassDefinition cls, Node block, String basePath) {
    for (MemberDefinition prop : cls.props) {
      Node propertyNode = IR.exprResult(
          NodeUtil.newQName(compiler, basePath + prop.name.getString()));
      JSDocInfoBuilder info = JSDocInfoBuilder.maybeCopyFrom(prop.info);

      JSTypeExpression propType = getTypeFromProperty(prop);
      if (propType == null) {
        return;
      }
      info.recordType(propType);
      propertyNode.getFirstChild().setJSDocInfo(info.build());

      block.addChildToBack(propertyNode);
    }
  }

  /**
   * Remove all JSDocs from properties of a class definition
   */
  private void removePropertyDocs(final Node objLit) {
    for (MemberDefinition prop : extractProperties(objLit)) {
      prop.name.removeProp(Node.JSDOC_INFO_PROP);
    }
  }

  /**
   * Appends all required behavior functions and non-property members to the given block.
   */
  private void appendBehaviorMembersToBlock(final ClassDefinition cls, Node block) {
    String qualifiedPath = cls.target.getQualifiedName() + ".prototype.";
    Map<String, Node> nameToExprResult = new HashMap<>();
    for (BehaviorDefinition behavior : cls.behaviors) {
      for (MemberDefinition behaviorFunction : behavior.functionsToCopy) {
        String fnName = behaviorFunction.name.getString();
        // Don't copy functions already defined by the element itself.
        if (NodeUtil.getFirstPropMatchingKey(cls.descriptor, fnName) != null) {
          continue;
        }

        // Avoid copying over the same function twice. The last definition always wins.
        if (nameToExprResult.containsKey(fnName)) {
          block.removeChild(nameToExprResult.get(fnName));
        }

        Node fnValue = behaviorFunction.value.cloneTree();
        Node exprResult = IR.exprResult(
            IR.assign(NodeUtil.newQName(compiler, qualifiedPath + fnName), fnValue));
        JSDocInfoBuilder info = JSDocInfoBuilder.maybeCopyFrom(behaviorFunction.info);

        // Behaviors whose declarations are not in the global scope may contain references to
        // symbols which do not exist in the element's scope. Only copy a function stub. See
        if (!behavior.isGlobalDeclaration) {
          NodeUtil.getFunctionBody(fnValue).removeChildren();
        }

        exprResult.getFirstChild().setJSDocInfo(info.build());
        block.addChildToBack(exprResult);
        nameToExprResult.put(fnName, exprResult);
      }

      // Copy other members.
      for (MemberDefinition behaviorProp : behavior.nonPropertyMembersToCopy) {
        String propName = behaviorProp.name.getString();
        if (nameToExprResult.containsKey(propName)) {
          block.removeChild(nameToExprResult.get(propName));
        }

        Node exprResult = IR.exprResult(NodeUtil.newQName(compiler, qualifiedPath + propName));
        JSDocInfoBuilder info = JSDocInfoBuilder.maybeCopyFrom(behaviorProp.info);

        if (behaviorProp.name.isGetterDef()) {
          info = new JSDocInfoBuilder(true);
          if (behaviorProp.info != null && behaviorProp.info.getReturnType() != null) {
            info.recordType(behaviorProp.info.getReturnType());
          }
        }

        exprResult.getFirstChild().setJSDocInfo(info.build());
        block.addChildToBack(exprResult);
        nameToExprResult.put(propName, exprResult);
      }
    }
  }

  /**
   * Generates the _set* setters for readonly properties and appends them to the given block.
   * @return A List of all readonly properties.
   */
  private List<MemberDefinition> parseReadOnlyProperties(final ClassDefinition cls, Node block) {
    String qualifiedPath = cls.target.getQualifiedName() + ".prototype.";
    ImmutableList.Builder<MemberDefinition> readOnlyProps = ImmutableList.builder();

    for (MemberDefinition prop : cls.props) {
      // Generate the setter for readOnly properties.
      if (prop.value.isObjectLit()) {
        Node readOnlyValue = NodeUtil.getFirstPropMatchingKey(prop.value, "readOnly");
        if (readOnlyValue != null && readOnlyValue.isTrue()) {
          block.addChildToBack(makeReadOnlySetter(prop.name.getString(), qualifiedPath));
          readOnlyProps.add(prop);
        }
      }
    }

    return readOnlyProps.build();
  }

  /**
   * Gets the JSTypeExpression for a given property using its "type" key.
   * @see https://github.com/Polymer/polymer/blob/0.8-preview/PRIMER.md#configuring-properties
   */
  private JSTypeExpression getTypeFromProperty(MemberDefinition property) {
    if (property.info != null && property.info.hasType()) {
      return property.info.getType();
    }

    String typeString = "";
    if (property.value.isObjectLit()) {
      Node typeValue = NodeUtil.getFirstPropMatchingKey(property.value, "type");
      if (typeValue == null || !typeValue.isName()) {
        compiler.report(JSError.make(property.name, POLYMER_INVALID_PROPERTY));
        return null;
      }
      typeString = typeValue.getString();
    } else if (property.value.isName()) {
      typeString = property.value.getString();
    }

    Node typeNode = null;
    switch (typeString) {
      case "Boolean":
      case "String":
      case "Number":
        typeNode = IR.string(typeString.toLowerCase());
        break;
      case "Array":
      case "Function":
      case "Object":
      case "Date":
        typeNode = new Node(Token.BANG, IR.string(typeString));
        break;
      default:
        compiler.report(JSError.make(property.name, POLYMER_INVALID_PROPERTY));
        return null;
    }

    return new JSTypeExpression(typeNode, VIRTUAL_FILE);
  }

  /**
   * Adds the generated setter for a readonly property.
   * @see https://www.polymer-project.org/0.8/docs/devguide/properties.html#read-only
   */
  private Node makeReadOnlySetter(String propName, String qualifiedPath) {
    String setterName = "_set" + propName.substring(0, 1).toUpperCase() + propName.substring(1);
    Node fnNode = IR.function(IR.name(""), IR.paramList(IR.name(propName)), IR.block());
    Node exprResNode = IR.exprResult(
        IR.assign(NodeUtil.newQName(compiler, qualifiedPath + setterName), fnNode));

    JSDocInfoBuilder info = new JSDocInfoBuilder(true);
    // This is overriding a generated function which was added to the interface in
    // {@code addInterfaceExterns}.
    info.recordOverride();
    exprResNode.getFirstChild().setJSDocInfo(info.build());

    return exprResNode;
  }

  /**
   * Duplicates the PolymerElement externs with a different element base class if needed.
   * For example, if the base class is HTMLInputElement, then a class PolymerInputElement will be
   * added. If the element does not extend a native HTML element, this method is a no-op.
   */
  private void appendPolymerElementExterns(final ClassDefinition cls) {
    if (!nativeExternsAdded.add(cls.nativeBaseElement)) {
      return;
    }

    Node block = IR.block();

    Node baseExterns = polymerElementExterns.cloneTree();
    String polymerElementType = getPolymerElementType(cls);
    baseExterns.getFirstChild().setString(polymerElementType);

    String elementType = tagNameMap.get(cls.nativeBaseElement);
    JSTypeExpression elementBaseType = new JSTypeExpression(
        new Node(Token.BANG, IR.string(elementType)), VIRTUAL_FILE);
    JSDocInfoBuilder baseDocs = JSDocInfoBuilder.copyFrom(baseExterns.getJSDocInfo());
    baseDocs.changeBaseType(elementBaseType);
    baseExterns.setJSDocInfo(baseDocs.build());
    block.addChildToBack(baseExterns);

    for (Node baseProp : polymerElementProps) {
      Node newProp = baseProp.cloneTree();
      Node newPropRootName = NodeUtil.getRootOfQualifiedName(
           newProp.getFirstChild().getFirstChild());
      newPropRootName.setString(polymerElementType);
      block.addChildToBack(newProp);
    }

    block.useSourceInfoIfMissingFromForTree(polymerElementExterns);

    Node parent = polymerElementExterns.getParent();
    Node stmts = block.removeChildren();
    parent.addChildrenAfter(stmts, polymerElementExterns);

    compiler.reportCodeChange();
  }

  /**
   * Adds an interface for the given ClassDefinition to externs. This allows generated setter
   * functions for read-only properties to avoid renaming altogether.
   * @see https://www.polymer-project.org/0.8/docs/devguide/properties.html#read-only
   */
  private void addInterfaceExterns(final ClassDefinition cls,
      List<MemberDefinition> readOnlyProps) {
    Node block = IR.block();

    String interfaceName = getInterfaceName(cls);
    Node fnNode = IR.function(IR.name(""), IR.paramList(), IR.block());
    Node varNode = IR.var(NodeUtil.newQName(compiler, interfaceName), fnNode);

    JSDocInfoBuilder info = new JSDocInfoBuilder(true);
    info.recordInterface();
    varNode.setJSDocInfo(info.build());
    block.addChildToBack(varNode);

    appendPropertiesToBlock(cls, block, interfaceName + ".prototype.");
    for (MemberDefinition prop : readOnlyProps) {
      // Add all _set* functions to avoid renaming.
      String propName = prop.name.getString();
      String setterName = "_set" + propName.substring(0, 1).toUpperCase() + propName.substring(1);
      Node setterExprNode = IR.exprResult(
          NodeUtil.newQName(compiler, interfaceName + ".prototype." + setterName));

      JSDocInfoBuilder setterInfo = new JSDocInfoBuilder(true);
      JSTypeExpression propType = getTypeFromProperty(prop);
      setterInfo.recordParameter(propName, propType);
      setterExprNode.getFirstChild().setJSDocInfo(setterInfo.build());

      block.addChildToBack(setterExprNode);
    }

    block.useSourceInfoIfMissingFromForTree(polymerElementExterns);

    Node parent = polymerElementExterns.getParent();
    Node stmts = block.removeChildren();
    parent.addChildrenToBack(stmts);

    compiler.reportCodeChange();
  }

  /**
   * @return The name of the generated extern interface which the element implements.
   */
  private String getInterfaceName(final ClassDefinition cls) {
    return "Polymer" + cls.target.getQualifiedName().replaceAll("\\.", "_") + "Interface";
  }

  /**
   * @return The proper constructor doc for the Polymer call.
   */
  private JSDocInfoBuilder getConstructorDoc(final ClassDefinition cls) {
    JSDocInfoBuilder constructorDoc = JSDocInfoBuilder.maybeCopyFrom(cls.constructor.info);
    constructorDoc.recordConstructor();

    JSTypeExpression baseType = new JSTypeExpression(
        new Node(Token.BANG, IR.string(getPolymerElementType(cls))), VIRTUAL_FILE);
    constructorDoc.recordBaseType(baseType);

    String interfaceName = getInterfaceName(cls);
    JSTypeExpression interfaceType = new JSTypeExpression(
        new Node(Token.BANG, IR.string(interfaceName)), VIRTUAL_FILE);
    constructorDoc.recordImplementedInterface(interfaceType);

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
    return SimpleFormat.format("Polymer%sElement", cls.nativeBaseElement == null ? ""
        : CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, cls.nativeBaseElement));
  }

  /**
   * @return Whether the call represents a call to Polymer.
   */
  private static boolean isPolymerCall(Node value) {
    return value != null && value.isCall() && value.getFirstChild().matchesQualifiedName("Polymer");
  }

  private boolean hasShorthandAssignment (Node objLit){
    Preconditions.checkState(objLit.isObjectLit());
    for (Node property : objLit.children()){
      if (property.isStringKey() && !property.hasChildren()){
        return true;
      }
    }
    return false;
  }

}
