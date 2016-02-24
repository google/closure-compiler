/*
 * Copyright 2016 The Closure Compiler Authors.
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
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.PolymerBehaviorExtractor.BehaviorDefinition;
import com.google.javascript.jscomp.PolymerPass.MemberDefinition;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rewrites a given call to Polymer({}) to a set of declarations and assignments which can be
 * understood by the compiler.
 */
final class PolymerClassRewriter {

  private final AbstractCompiler compiler;

  private Node polymerElementExterns;

  PolymerClassRewriter(AbstractCompiler compiler, Node polymerElementExterns) {
    this.compiler = compiler;
    this.polymerElementExterns = polymerElementExterns;
  }

  /**
   * Rewrites a given call to Polymer({}) to a set of declarations and assignments which can be
   * understood by the compiler.
   *
   * @param exprRoot The root expression of the call to Polymer({}).
   * @param cls The extracted {@link PolymerClassDefinition} for the Polymer element created by this
   *     call.
   */
  void rewritePolymerClass(
      Node exprRoot, final PolymerClassDefinition cls, boolean isInGlobalScope) {
    Node call = exprRoot.getFirstChild();
    if (call.isAssign()) {
      call = call.getSecondChild();
    } else if (call.isName()) {
      call = call.getFirstChild();
    }

    Node objLit = cls.descriptor;
    if (hasShorthandAssignment(objLit)){
      compiler.report(JSError.make(objLit, PolymerPassErrors.POLYMER_SHORTHAND_NOT_SUPPORTED));
      return;
    }

    // Add {@code @lends} to the object literal.
    JSDocInfoBuilder objLitDoc = new JSDocInfoBuilder(true);
    objLitDoc.recordLends(cls.target.getQualifiedName() + ".prototype");
    objLit.setJSDocInfo(objLitDoc.build());

    addTypesToFunctions(objLit, cls.target.getQualifiedName());
    PolymerPassStaticUtils.switchDollarSignPropsToBrackets(objLit, compiler);
    PolymerPassStaticUtils.quoteListenerAndHostAttributeKeys(objLit);

    // For simplicity add everything into a block, before adding it to the AST.
    Node block = IR.block();

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
    ImmutableList<MemberDefinition> readOnlyProps = parseReadOnlyProperties(cls, block);
    addInterfaceExterns(cls, readOnlyProps);
    removePropertyDocs(objLit);

    block.useSourceInfoIfMissingFromForTree(exprRoot);
    Node statements = block.removeChildren();
    Node parent = exprRoot.getParent();

    // If the call to Polymer() is not in the global scope and the assignment target is not
    // namespaced (which likely means it's exported to the global scope), put the type declaration
    // into the global scope at the start of the current script.
    //
    // This avoids unknown type warnings which are a result of the compiler's poor understanding of
    // types declared inside IIFEs or any non-global scope. We should revisit this decision after
    // moving to the new type inference system which should be able to infer these types better.
    if (!isInGlobalScope && !cls.target.isGetProp()) {
      Node scriptNode = NodeUtil.getEnclosingScript(exprRoot);
      scriptNode.addChildrenToFront(statements);
    } else {
      Node beforeRoot = parent.getChildBefore(exprRoot);
      if (beforeRoot == null) {
        parent.addChildrenToFront(statements);
      } else {
        parent.addChildrenAfter(statements, beforeRoot);
      }
    }

    if (NodeUtil.isNameDeclaration(exprRoot)) {
      Node assignExpr = varToAssign(exprRoot);
      parent.replaceChild(exprRoot, assignExpr);
    }

    compiler.reportCodeChange();
  }

  /**
   * Adds an @this annotation to all functions in the objLit.
   */
  private void addTypesToFunctions(Node objLit, String thisType) {
    Preconditions.checkState(objLit.isObjectLit());
    for (Node keyNode : objLit.children()) {
      Node value = keyNode.getLastChild();
      if (value != null && value.isFunction()) {
        JSDocInfoBuilder fnDoc = JSDocInfoBuilder.maybeCopyFrom(keyNode.getJSDocInfo());
        fnDoc.recordThisType(new JSTypeExpression(
            new Node(Token.BANG, IR.string(thisType)), PolymerPass.VIRTUAL_FILE));
        keyNode.setJSDocInfo(fnDoc.build());
      }
    }

    // Add @this and @return to default property values.
    for (MemberDefinition property : PolymerPassStaticUtils.extractProperties(objLit)) {
      if (!property.value.isObjectLit()) {
        continue;
      }
      if (hasShorthandAssignment(property.value)){
        compiler.report(
            JSError.make(property.value, PolymerPassErrors.POLYMER_SHORTHAND_NOT_SUPPORTED));
        return;
      }

      Node defaultValue = NodeUtil.getFirstPropMatchingKey(property.value, "value");
      if (defaultValue == null || !defaultValue.isFunction()) {
        continue;
      }
      Node defaultValueKey = defaultValue.getParent();
      JSDocInfoBuilder fnDoc = JSDocInfoBuilder.maybeCopyFrom(defaultValueKey.getJSDocInfo());
      fnDoc.recordThisType(new JSTypeExpression(
          new Node(Token.BANG, IR.string(thisType)), PolymerPass.VIRTUAL_FILE));
      fnDoc.recordReturnType(PolymerPassStaticUtils.getTypeFromProperty(property, compiler));
      defaultValueKey.setJSDocInfo(fnDoc.build());
    }
  }

  /**
   * Generates the _set* setters for readonly properties and appends them to the given block.
   * @return A List of all readonly properties.
   */
  private ImmutableList<MemberDefinition> parseReadOnlyProperties(
      final PolymerClassDefinition cls, Node block) {
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
   * @return The proper constructor doc for the Polymer call.
   */
  private JSDocInfoBuilder getConstructorDoc(final PolymerClassDefinition cls) {
    JSDocInfoBuilder constructorDoc = JSDocInfoBuilder.maybeCopyFrom(cls.constructor.info);
    constructorDoc.recordConstructor();

    JSTypeExpression baseType = new JSTypeExpression(
        new Node(Token.BANG, IR.string(PolymerPassStaticUtils.getPolymerElementType(cls))),
        PolymerPass.VIRTUAL_FILE);
    constructorDoc.recordBaseType(baseType);

    String interfaceName = getInterfaceName(cls);
    JSTypeExpression interfaceType = new JSTypeExpression(
        new Node(Token.BANG, IR.string(interfaceName)), PolymerPass.VIRTUAL_FILE);
    constructorDoc.recordImplementedInterface(interfaceType);

    return constructorDoc;
  }

  /**
   * Appends all properties in the ClassDefinition to the prototype of the custom element.
   */
  private void appendPropertiesToBlock(
      final PolymerClassDefinition cls, Node block, String basePath) {
    for (MemberDefinition prop : cls.props) {
      Node propertyNode = IR.exprResult(
          NodeUtil.newQName(compiler, basePath + prop.name.getString()));
      JSDocInfoBuilder info = JSDocInfoBuilder.maybeCopyFrom(prop.info);

      JSTypeExpression propType = PolymerPassStaticUtils.getTypeFromProperty(prop, compiler);
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
    for (MemberDefinition prop : PolymerPassStaticUtils.extractProperties(objLit)) {
      prop.name.removeProp(Node.JSDOC_INFO_PROP);
    }
  }

  /**
   * Appends all required behavior functions and non-property members to the given block.
   */
  private void appendBehaviorMembersToBlock(final PolymerClassDefinition cls, Node block) {
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
        // symbols which do not exist in the element's scope. Only copy a function stub.
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
   * Adds an interface for the given ClassDefinition to externs. This allows generated setter
   * functions for read-only properties to avoid renaming altogether.
   * @see https://www.polymer-project.org/0.8/docs/devguide/properties.html#read-only
   */
  private void addInterfaceExterns(
      final PolymerClassDefinition cls, List<MemberDefinition> readOnlyProps) {
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
      JSTypeExpression propType = PolymerPassStaticUtils.getTypeFromProperty(prop, compiler);
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

  private static boolean hasShorthandAssignment(Node objLit) {
    Preconditions.checkState(objLit.isObjectLit());
    for (Node property : objLit.children()){
      if (property.isStringKey() && !property.hasChildren()){
        return true;
      }
    }
    return false;
  }

  /**
   * @return The name of the generated extern interface which the element implements.
   */
  private static String getInterfaceName(final PolymerClassDefinition cls) {
    return "Polymer" + cls.target.getQualifiedName().replaceAll("\\.", "_") + "Interface";
  }

  /**
   * @return An assign replacing the equivalent var or let declaration.
   */
  private static Node varToAssign(Node var) {
    Node assign = IR.assign(
        var.getFirstChild().cloneNode(),
        var.getFirstChild().removeFirstChild());
    return IR.exprResult(assign).useSourceInfoFromForTree(var);
  }
}
