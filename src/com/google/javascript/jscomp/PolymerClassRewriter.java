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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.NodeUtil.isBundledGoogModuleCall;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.PolymerBehaviorExtractor.BehaviorDefinition;
import com.google.javascript.jscomp.PolymerPass.MemberDefinition;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Rewrites a given call to Polymer({}) to a set of declarations and assignments which can be
 * understood by the compiler.
 */
final class PolymerClassRewriter {
  private static final String VIRTUAL_FILE = "<PolymerClassRewriter.java>";
  private final AbstractCompiler compiler;
  private final int polymerVersion;
  private final PolymerExportPolicy polymerExportPolicy;
  private final boolean propertyRenamingEnabled;
  @VisibleForTesting static final String POLYMER_ELEMENT_PROP_CONFIG = "PolymerElementProperties";

  static final DiagnosticType IMPLICIT_GLOBAL_CONFLICT =
      DiagnosticType.error(
          "JSC_POLYMER_IMPLICIT_GLOBAL_CONFLICT",
          "Implicit global name for Polymer element conflicts with existing var {0}. Either"
              + " give the element a lhs or rename {0}. (Or move to class-based Polymer 2"
              + " elements)");

  static final DiagnosticType POLYMER_ELEMENT_CONFLICT =
      DiagnosticType.error(
          "JSC_POLYMER_ELEMENT_CONFLICT",
          "Cannot generate correct types for Polymer call due to PolymerElement definition at"
              + " {0}:{2}:{1}.\n"
              + "Rename the local PolymerElement to avoid shadowing the PolymerElement externs.");

  private final Node polymerElementExterns;
  boolean propertySinkExternInjected = false;

  PolymerClassRewriter(
      AbstractCompiler compiler,
      Node polymerElementExterns,
      int polymerVersion,
      PolymerExportPolicy polymerExportPolicy,
      boolean propertyRenamingEnabled) {
    this.compiler = compiler;
    this.polymerElementExterns = polymerElementExterns;
    this.polymerVersion = polymerVersion;
    this.polymerExportPolicy = polymerExportPolicy;
    this.propertyRenamingEnabled = propertyRenamingEnabled;
  }

  static boolean isIIFE(Node n) {
    return n.isCall() && n.getFirstChild().isFunction();
  }

  static boolean isFunctionArgInGoogLoadModule(Node n) {
    if (!n.isFunction()) {
      return false;
    }

    Node parent = n.getParent();
    return parent != null && isBundledGoogModuleCall(parent);
  }

  /**
   * This function accepts declaration code generated for a nonGlobal Polymer call and inserts that
   * into the AST depending on the enclosing scope of the Polymer call.
   *
   * @param enclosingNode The enclosing scope of the Polymer call decided by the rewritePolymerCall
   * @param declarationCode declaration code generated for Polymer call
   */
  private void insertGeneratedDeclarationCodeToGlobalScope(
      Node enclosingNode, Node declarationCode) {
    switch (enclosingNode.getToken()) {
      case MODULE_BODY:
        {
          Node insertionPoint = getNodeForInsertion(enclosingNode.getParent());
          insertionPoint.addChildToFront(declarationCode);
          compiler.reportChangeToChangeScope(NodeUtil.getEnclosingScript(insertionPoint));
        }
        break;
      case SCRIPT:
        {
          enclosingNode.addChildToFront(declarationCode);
          compiler.reportChangeToChangeScope(NodeUtil.getEnclosingScript(enclosingNode));
        }
        break;
      case CALL:
        {
          // This case represents only the Polymer calls which are enclosed inside an IIFE
          checkState(isIIFE(enclosingNode));
          Node enclosingNodeForIIFE =
              NodeUtil.getEnclosingNode(
                  enclosingNode.getParent(), node -> node.isScript() || node.isModuleBody());
          if (enclosingNodeForIIFE.isScript()) {
            enclosingNodeForIIFE.addChildToFront(declarationCode);
            compiler.reportChangeToChangeScope(NodeUtil.getEnclosingScript(enclosingNodeForIIFE));
          } else {
            checkState(enclosingNodeForIIFE.isModuleBody());
            Node insertionPoint = getNodeForInsertion(enclosingNodeForIIFE.getParent());
            insertionPoint.addChildToFront(declarationCode);
            compiler.reportChangeToChangeScope(NodeUtil.getEnclosingScript(insertionPoint));
          }
        }
        break;
      case FUNCTION:
        {
          // This case represents only the Polymer calls that are inside a function which is an arg
          // to goog.loadModule
          checkState(isFunctionArgInGoogLoadModule(enclosingNode));
          Node enclosingScript = NodeUtil.getEnclosingScript(enclosingNode);
          Node insertionPoint = getNodeForInsertion(enclosingScript);
          insertionPoint.addChildToFront(declarationCode);
          compiler.reportChangeToChangeScope(insertionPoint);
        }
        break;
      default:
        throw new RuntimeException("Enclosing node for Polymer is incorrect");
    }
  }

  /** Returns a SCRIPT node in which to insert new global declarations */
  private Node getNodeForInsertion(Node enclosingScript) {
    if (NodeUtil.isFromTypeSummary(enclosingScript)) {
      return polymerElementExterns.isScript()
          ? polymerElementExterns
          : polymerElementExterns.getParent();
    } else {
      return compiler.getNodeForCodeInsertion(null);
    }
  }

  /**
   * This function accepts code generated for a nonGlobal Polymer call and inserts that code into
   * the AST depending on the enclosing scope of the Polymer call.
   *
   * @param enclosingNode The enclosing scope of the Polymer call decided by the rewritePolymerCall
   * @param statements code generated for Polymer's properties and behavior
   */
  private void insertGeneratedPropsAndBehaviorCode(Node enclosingNode, Node statements) {
    switch (enclosingNode.getToken()) {
      case MODULE_BODY:
        {
        if (enclosingNode.getParent().getBooleanProp(Node.GOOG_MODULE)) {
          // The goog.module('ns'); call must remain the first statement in the module.
          Node insertionPoint = getInsertionPointForGoogModule(enclosingNode);
          enclosingNode.addChildrenAfter(statements, insertionPoint);
        } else {
          enclosingNode.addChildrenToFront(statements);
        }
        }
        break;
      case SCRIPT:
        enclosingNode.addChildrenToFront(statements);
        compiler.reportChangeToChangeScope(NodeUtil.getEnclosingScript(enclosingNode));
        break;
      case CALL:
        {
          // This case represents only the Polymer calls which are enclosed inside an IIFE
          checkState(isIIFE(enclosingNode));
          Node functionNode = enclosingNode.getFirstChild();
          Node functionBlock = functionNode.getLastChild();
          functionBlock.addChildrenToFront(statements);
        }
        break;
      case FUNCTION:
        // This case represents only the Polymer calls that are inside a function which is an arg
        // to goog.loadModule
        checkState(isFunctionArgInGoogLoadModule(enclosingNode));
        Node functionBlock = enclosingNode.getLastChild();
        Node insertionPoint = getInsertionPointForGoogModule(functionBlock);
        // Node insertionPoint will be null here if functionBlock does not contain a goog.module()
        // Missing goog.module inside the loadModule's functionBlock is semantically incorrect
        // That will cause the compiler to crash in closureRewriteModule pass.
        if (insertionPoint != null) {
          functionBlock.addChildrenAfter(statements, insertionPoint);
        }
        break;
      default:
        break;
    }
  }

  /**
   * Rewrites a given call to Polymer({}) to a set of declarations and assignments which can be
   * understood by the compiler.
   *
   * @param cls The extracted {@link PolymerClassDefinition} for the Polymer element created by this
   *     call.
   * @param traversal Nodetraversal used here to identify the scope in which Polymer exists
   */
  void rewritePolymerCall(final PolymerClassDefinition cls, NodeTraversal traversal) {
    Node callParent = cls.definition.getParent();
    // Determine whether we are in a Polymer({}) call at the top level versus in an assignment.
    Node exprRoot = callParent.isExprResult() ? callParent : callParent.getParent();
    checkState(NodeUtil.isStatementParent(exprRoot.getParent()), exprRoot.getParent());
    Node objLit = checkNotNull(cls.descriptor);

    // Add {@code @lends} to the object literal.
    JSDocInfoBuilder objLitDoc = new JSDocInfoBuilder(true);
    JSTypeExpression jsTypeExpression =
        new JSTypeExpression(
            IR.string(cls.target.getQualifiedName() + ".prototype").srcref(exprRoot),
            exprRoot.getSourceFileName());
    objLitDoc.recordLends(jsTypeExpression);
    objLit.setJSDocInfo(objLitDoc.build());

    addTypesToFunctions(objLit, cls.target.getQualifiedName(), cls.defType);
    PolymerPassStaticUtils.switchDollarSignPropsToBrackets(objLit, compiler);
    PolymerPassStaticUtils.quoteListenerAndHostAttributeKeys(objLit, compiler);

    for (MemberDefinition prop : cls.props) {
      if (prop.value.isObjectLit()) {
        PolymerPassStaticUtils.switchDollarSignPropsToBrackets(prop.value, compiler);
      }
    }

    // The propsAndBehaviorBlock holds code generated for the  Polymer's properties and behaviors
    Node propsAndBehaviorBlock = IR.block();

    JSDocInfoBuilder constructorDoc = this.getConstructorDoc(cls);

    // Remove the original constructor JS docs from the objlit.
    Node ctorKey = cls.constructor.value.getParent();
    if (ctorKey != null) {
      ctorKey.removeProp(Node.JSDOC_INFO_PROP);
    }
    // Check for a conflicting definition of PolymerElement
    if (!traversal.inGlobalScope()) {
      Var polymerElement = traversal.getScope().getVar("PolymerElement");
      if (polymerElement != null && !polymerElement.getScope().isGlobal()) {
        Node nameNode = polymerElement.getNameNode();
        compiler.report(
            JSError.make(
                cls.constructor.value,
                POLYMER_ELEMENT_CONFLICT,
                nameNode.getSourceFileName(),
                Integer.toString(nameNode.getLineno()),
                Integer.toString(nameNode.getCharno())));
      }
    }
    Node declarationCode = generateDeclarationCode(exprRoot, cls, constructorDoc, traversal);
    appendPropertiesToBlock(
        cls.props,
        propsAndBehaviorBlock,
        cls.target.getQualifiedName() + ".prototype.",
        /* isExternsBlock= */ false);
    appendBehaviorMembersToBlock(cls, propsAndBehaviorBlock);
    ImmutableList<MemberDefinition> readOnlyProps =
        parseReadOnlyProperties(cls, propsAndBehaviorBlock);
    ImmutableList<MemberDefinition> attributeReflectedProps =
        parseAttributeReflectedProperties(cls);
    createExportsAndExterns(cls, readOnlyProps, attributeReflectedProps);
    removePropertyDocs(objLit, PolymerClassDefinition.DefinitionType.ObjectLiteral);

    Node propsAndBehaviorCode = propsAndBehaviorBlock.removeChildren();
    Node parent = exprRoot.getParent();

    // Put the type declaration in to either the enclosing module scope, if in a module, or the
    // enclosing script node. Compiler support for local scopes like IIFEs is sometimes lacking but
    // module scopes are well-supported. If this is not in a module or the global scope it is likely
    // exported.
    if (!traversal.inGlobalScope() && cls.hasGeneratedLhs && !cls.target.isGetProp()) {
      Node enclosingNode =
          NodeUtil.getEnclosingNode(
              parent,
              node ->
                  node.isScript()
                      || node.isModuleBody()
                      || isIIFE(node)
                      || isFunctionArgInGoogLoadModule(node));

      // For module, IIFE and goog.LoadModule enclosed Polymer calls, the declaration code and the
      // code generated from properties and behavior have to be hoisted in different places within
      // the AST. We want to insert the generated declarations to global scope, and insert the
      // propsAndbehaviorCode in the same scope. Hence, dealing with them separately.
      insertGeneratedDeclarationCodeToGlobalScope(enclosingNode, declarationCode);
      if (propsAndBehaviorCode != null) {
        insertGeneratedPropsAndBehaviorCode(enclosingNode, propsAndBehaviorCode);
      }
    } else {
      Node beforeRoot = exprRoot.getPrevious();
      if (beforeRoot == null) {
        if (propsAndBehaviorCode != null) {
          parent.addChildrenToFront(propsAndBehaviorCode);
        }
        parent.addChildToFront(declarationCode);
      } else {
        if (propsAndBehaviorCode != null) {
          parent.addChildrenAfter(propsAndBehaviorCode, beforeRoot);
        }
        parent.addChildAfter(declarationCode, beforeRoot);
      }
      compiler.reportChangeToEnclosingScope(parent);
    }
    if (propsAndBehaviorCode != null) {
      compiler.reportChangeToEnclosingScope(propsAndBehaviorCode);
    }

    // Since behavior files might contain language features that aren't present in the class file,
    // we might need to update the FeatureSet.
    if (cls.features != null) {
      Node scriptNode = NodeUtil.getEnclosingScript(parent);
      FeatureSet oldFeatures = (FeatureSet) scriptNode.getProp(Node.FEATURE_SET);
      FeatureSet newFeatures = oldFeatures.union(cls.features);
      if (!newFeatures.equals(oldFeatures)) {
        scriptNode.putProp(Node.FEATURE_SET, newFeatures);
        compiler.reportChangeToChangeScope(scriptNode);
      }
    }

    if (NodeUtil.isNameDeclaration(exprRoot)) {
      Node assignExpr = varToAssign(exprRoot);
      parent.replaceChild(exprRoot, assignExpr);
      compiler.reportChangeToEnclosingScope(assignExpr);
    }

    // If property renaming is enabled, wrap the properties object literal
    // in a reflection call so that the properties are renamed consistently
    // with the class members.
    if (polymerVersion > 1 && propertyRenamingEnabled && cls.descriptor != null) {
      Node props = NodeUtil.getFirstPropMatchingKey(cls.descriptor, "properties");
      if (props != null && props.isObjectLit()) {
        addPropertiesConfigObjectReflection(cls, props);
      }
    }
  }

  /**
   * Rewrites a class which extends Polymer.Element to a set of declarations and assignments which
   * can be understood by the compiler.
   *
   * @param clazz The class node
   * @param cls The extracted {@link PolymerClassDefinition} for the Polymer element created by this
   *     call.
   */
  void rewritePolymerClassDeclaration(
      Node clazz, NodeTraversal traversal, final PolymerClassDefinition cls) {

    if (cls.descriptor != null) {
      addTypesToFunctions(cls.descriptor, cls.target.getQualifiedName(), cls.defType);
    }
    PolymerPassStaticUtils.switchDollarSignPropsToBrackets(
        NodeUtil.getClassMembers(clazz), compiler);

    for (MemberDefinition prop : cls.props) {
      if (prop.value.isObjectLit()) {
        PolymerPassStaticUtils.switchDollarSignPropsToBrackets(prop.value, compiler);
      }
    }

    // For simplicity add everything into a block, before adding it to the AST.
    Node block = IR.block();

    // For each Polymer property we found in the "properties" configuration object, append a
    // property declaration to the prototype (e.g. "/** @type {string} */ MyElement.prototype.foo").
    appendPropertiesToBlock(
        cls.props,
        block,
        cls.target.getQualifiedName() + ".prototype.",
        /* isExternsBlock= */ false);

    ImmutableList<MemberDefinition> readOnlyProps = parseReadOnlyProperties(cls, block);
    ImmutableList<MemberDefinition> attributeReflectedProps =
        parseAttributeReflectedProperties(cls);
    createExportsAndExterns(cls, readOnlyProps, attributeReflectedProps);

    // If an external interface is required, mark the class as implementing it
    if (polymerExportPolicy == PolymerExportPolicy.EXPORT_ALL
        || !readOnlyProps.isEmpty()
        || !attributeReflectedProps.isEmpty()) {
      Node jsDocInfoNode = NodeUtil.getBestJSDocInfoNode(clazz);
      JSDocInfoBuilder classInfo = JSDocInfoBuilder.maybeCopyFrom(jsDocInfoNode.getJSDocInfo());
      String interfaceName = cls.getInterfaceName(compiler.getUniqueNameIdSupplier());
      JSTypeExpression interfaceType =
          new JSTypeExpression(
              new Node(Token.BANG, IR.string(interfaceName)).srcrefTree(jsDocInfoNode),
              VIRTUAL_FILE);
      classInfo.recordImplementedInterface(interfaceType);
      jsDocInfoNode.setJSDocInfo(classInfo.build());
    }

    Node insertAfterReference = NodeUtil.getEnclosingStatement(clazz);
    if (block.hasChildren()) {
      removePropertyDocs(cls.descriptor, cls.defType);
      Node newInsertAfterReference = block.getLastChild();
      insertAfterReference
          .getParent()
          .addChildrenAfter(block.removeChildren(), insertAfterReference);
      compiler.reportChangeToEnclosingScope(insertAfterReference);
      insertAfterReference = newInsertAfterReference;
    }

    addReturnTypeIfMissing(cls, "is", new JSTypeExpression(IR.string("string"), VIRTUAL_FILE));

    Node type = new Node(Token.BANG);
    Node array = IR.string("Array");
    type.addChildToBack(array);
    Node arrayTemplateType = new Node(Token.BLOCK, IR.string("string"));
    array.addChildToBack(arrayTemplateType);
    addReturnTypeIfMissing(cls, "observers", new JSTypeExpression(type, VIRTUAL_FILE));
    addReturnTypeIfMissing(
        cls,
        "properties",
        new JSTypeExpression(IR.string(POLYMER_ELEMENT_PROP_CONFIG), VIRTUAL_FILE));

    // If property renaming is enabled, wrap the properties object literal
    // in a reflection call so that the properties are renamed consistently
    // with the class members.
    //
    // Also add reflection and sinks for computed properties and complex observers
    // and switch simple observers to direct function references.
    if (propertyRenamingEnabled && cls.descriptor != null) {
      convertSimpleObserverStringsToReferences(cls);
      List<Node> propertySinks = new ArrayList<>();
      if (polymerExportPolicy != PolymerExportPolicy.EXPORT_ALL) {
        propertySinks.addAll(addComputedPropertiesReflectionCalls(cls));
        propertySinks.addAll(addComplexObserverReflectionCalls(cls));
      }

      if (propertySinks.size() > 0) {
        if (!propertySinkExternInjected
            && traversal.getScope().getVar(CheckSideEffects.PROTECTOR_FN) == null) {
          CheckSideEffects.addExtern(compiler);
          propertySinkExternInjected = true;
        }

        for (Node propertyRef : propertySinks) {
          Node name = IR.name(CheckSideEffects.PROTECTOR_FN).srcref(propertyRef);
          name.putBooleanProp(Node.IS_CONSTANT_NAME, true);
          Node protectorCall = IR.call(name, propertyRef).srcref(propertyRef);
          protectorCall.putBooleanProp(Node.FREE_CALL, true);
          protectorCall = IR.exprResult(protectorCall).useSourceInfoFrom(propertyRef);
          insertAfterReference.getParent().addChildAfter(protectorCall, insertAfterReference);
          insertAfterReference = protectorCall;
        }

        compiler.reportChangeToEnclosingScope(insertAfterReference);
      }

      addPropertiesConfigObjectReflection(cls, cls.descriptor);
    }
  }

  /** Adds return type information to class getters */
  private static void addReturnTypeIfMissing(
      PolymerClassDefinition cls, String getterPropName, JSTypeExpression jsType) {
    Node classMembers = NodeUtil.getClassMembers(cls.definition);
    Node getter = NodeUtil.getFirstGetterMatchingKey(classMembers, getterPropName);
    if (getter != null) {
      JSDocInfo info = NodeUtil.getBestJSDocInfo(getter);
      if (info == null || !info.hasReturnType()) {
        JSDocInfoBuilder builder = JSDocInfoBuilder.maybeCopyFrom(info);
        builder.recordReturnType(jsType);
        jsType.getRoot().useSourceInfoIfMissingFromForTree(getter);
        getter.setJSDocInfo(builder.build());
      }
    }
  }

  /** Wrap the properties config object in an objectReflect call */
  private void addPropertiesConfigObjectReflection(
      PolymerClassDefinition cls, Node propertiesLiteral) {
    checkNotNull(propertiesLiteral);
    checkState(propertiesLiteral.isObjectLit());

    Node parent = propertiesLiteral.getParent();

    Node objReflectCall =
        IR.call(
                NodeUtil.newQName(compiler, "$jscomp.reflectObject"),
                cls.target.cloneTree(),
                propertiesLiteral.detach())
            .useSourceInfoIfMissingFromForTree(propertiesLiteral);
    parent.addChildToFront(objReflectCall);
    compiler.reportChangeToEnclosingScope(parent);
  }

  /** Adds an @this annotation to all functions in the objLit. */
  private void addTypesToFunctions(
      Node objLit, String thisType, PolymerClassDefinition.DefinitionType defType) {
    checkState(objLit.isObjectLit());
    for (Node keyNode : objLit.children()) {
      Node value = keyNode.getLastChild();
      if (value != null && value.isFunction()) {
        JSDocInfoBuilder fnDoc = JSDocInfoBuilder.maybeCopyFrom(keyNode.getJSDocInfo());
        fnDoc.recordThisType(
            new JSTypeExpression(
                new Node(Token.BANG, IR.string(thisType)).srcrefTree(keyNode), VIRTUAL_FILE));
        keyNode.setJSDocInfo(fnDoc.build());
      }
    }

    // Add @this and @return to default property values.
    for (MemberDefinition property :
        PolymerPassStaticUtils.extractProperties(
            objLit,
            defType,
            compiler,
            /** constructor= */
            null)) {
      if (!property.value.isObjectLit()) {
        continue;
      }

      Node defaultValue = NodeUtil.getFirstPropMatchingKey(property.value, "value");
      if (defaultValue == null || !defaultValue.isFunction()) {
        continue;
      }
      Node defaultValueKey = defaultValue.getParent();
      JSDocInfoBuilder fnDoc = JSDocInfoBuilder.maybeCopyFrom(defaultValueKey.getJSDocInfo());
      fnDoc.recordThisType(
          new JSTypeExpression(
              new Node(Token.BANG, IR.string(thisType)).srcrefTree(defaultValueKey), VIRTUAL_FILE));
      fnDoc.recordReturnType(PolymerPassStaticUtils.getTypeFromProperty(property, compiler));
      defaultValueKey.setJSDocInfo(fnDoc.build());
    }
  }

  /**
   * Generates the _set* setters for readonly properties and appends them to the given block.
   *
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
          Node setter = makeReadOnlySetter(prop, qualifiedPath);
          setter.useSourceInfoIfMissingFromForTree(prop.name);
          block.addChildToBack(setter);
          readOnlyProps.add(prop);
        }
      }
    }

    return readOnlyProps.build();
  }

  private static ImmutableList<MemberDefinition> parseAttributeReflectedProperties(
      final PolymerClassDefinition cls) {
    ImmutableList.Builder<MemberDefinition> attrReflectedProps = ImmutableList.builder();

    for (MemberDefinition prop : cls.props) {
      // Generate the setter for readOnly properties.
      if (prop.value.isObjectLit()) {
        Node reflectedValue = NodeUtil.getFirstPropMatchingKey(prop.value, "reflectToAttribute");
        if (reflectedValue != null && reflectedValue.isTrue()) {
          attrReflectedProps.add(prop);
        }
      }
    }

    return attrReflectedProps.build();
  }

  /** @return The proper constructor doc for the Polymer call. */
  private JSDocInfoBuilder getConstructorDoc(final PolymerClassDefinition cls) {
    JSDocInfoBuilder constructorDoc = JSDocInfoBuilder.maybeCopyFrom(cls.constructor.info);
    constructorDoc.recordConstructor();

    JSTypeExpression baseType =
        new JSTypeExpression(
            new Node(Token.BANG, IR.string(PolymerPassStaticUtils.getPolymerElementType(cls)))
                .srcrefTree(cls.definition),
            VIRTUAL_FILE);
    constructorDoc.recordBaseType(baseType);

    String interfaceName = cls.getInterfaceName(compiler.getUniqueNameIdSupplier());
    JSTypeExpression interfaceType =
        new JSTypeExpression(
            new Node(Token.BANG, IR.string(interfaceName)).srcrefTree(cls.definition),
            VIRTUAL_FILE);
    constructorDoc.recordImplementedInterface(interfaceType);

    return constructorDoc;
  }

  /* Appends var declaration code created from the Polymer call to the given block */
  private Node generateDeclarationCode(
      Node exprRoot,
      final PolymerClassDefinition cls,
      JSDocInfoBuilder constructorDoc,
      NodeTraversal traversal) {
    if (cls.target.isGetProp()) {
      // foo.bar = Polymer({...});
      Node assign = IR.assign(cls.target.cloneTree(), cls.constructor.value.cloneTree());
      NodeUtil.markNewScopesChanged(assign, compiler);
      assign.setJSDocInfo(constructorDoc.build());
      Node exprResult = IR.exprResult(assign);
      exprResult.useSourceInfoIfMissingFromForTree(cls.target);
      return exprResult;
    } else {
      // var foo = Polymer({...}); OR Polymer({...});
      Node var = IR.var(cls.target.cloneTree(), cls.constructor.value.cloneTree());
      NodeUtil.markNewScopesChanged(var, compiler);
      var.useSourceInfoIfMissingFromForTree(exprRoot);
      var.setJSDocInfo(constructorDoc.build());
      String name = cls.target.getString();
      Var existingVar = traversal.getScope().getSlot(name);
      if (existingVar != null && cls.hasGeneratedLhs) {
        compiler.report(JSError.make(cls.constructor.value, IMPLICIT_GLOBAL_CONFLICT, name));
      }
      return var;
    }
  }

  /** Appends all of the given properties to the given block. */
  private void appendPropertiesToBlock(
      List<MemberDefinition> props, Node block, String basePath, boolean isExternsBlock) {
    for (MemberDefinition prop : props) {
      Node propertyNode =
          IR.exprResult(NodeUtil.newQName(compiler, basePath + prop.name.getString()));

      // If a property string is quoted, make sure the added prototype properties are also quoted
      if (prop.name.isQuotedString()) {
        continue;
      }

      propertyNode.useSourceInfoIfMissingFromForTree(prop.name);
      JSDocInfoBuilder infoBuilder = JSDocInfoBuilder.maybeCopyFrom(prop.info);

      JSTypeExpression propType = PolymerPassStaticUtils.getTypeFromProperty(prop, compiler);
      if (propType == null) {
        return;
      }
      infoBuilder.recordType(propType);

      JSDocInfo info = infoBuilder.build();

      // We make all externs' types as unknown, and generate new vars with {propName:?} JsDoc
      // to prevent those properties from renaming
      if (isExternsBlock) {
        ImmutableSet<String> propertyNames = propType.getRecordPropertyNames();
        createVarsInExternsBlock(block, propertyNames, propType, prop);
        JSTypeExpression unknown =
            new JSTypeExpression(new Node(Token.QMARK), propType.getSourceName());

        JSDocInfoBuilder newInfoBuilder = JSDocInfoBuilder.copyFromWithNewType(info, unknown);
        info = newInfoBuilder.build();
      }
      propertyNode.getFirstChild().setJSDocInfo(info);
      block.addChildToBack(propertyNode);
    }
  }

  /**
   * For a JSDoc like @type {{ propertyName : string }}, collects all such "propertyName"s, and
   * generates extern vars with an attached {{propertyName: ?}} JsDoc. This is to prevent renaming
   * of vars in source code with the same names as propertyNames.
   *
   * <p>If we do not preserve the property names, then 540 targets get broken on TGP testing. This
   * indicates that those targets probably accidentally relied on properties not being renamed, and
   * we did not find it important to clean up all those targets' JS source.
   */
  private void createVarsInExternsBlock(
      Node block,
      ImmutableSet<String> propertyNames,
      JSTypeExpression propType,
      MemberDefinition prop) {

    for (String propName : propertyNames) {
      String varName = "PolymerDummyVar" + compiler.getUniqueNameIdSupplier().get();
      Node n = Node.newString(Token.NAME, varName);
      Node var = new Node(Token.VAR);
      var.addChildToBack(n);

      // Forming @type {{ propertyName : ? }}
      JSTypeExpression newType =
          createNewTypeExpressionForExtern(propName, propType.getSourceName(), prop);

      JSDocInfoBuilder oldInfoBuilder = JSDocInfoBuilder.maybeCopyFrom(prop.info);
      JSDocInfo info = oldInfoBuilder.build();

      JSDocInfoBuilder newInfo = JSDocInfoBuilder.copyFromWithNewType(info, newType);
      var.setJSDocInfo(newInfo.build());
      block.addChildToBack(var);
    }
  }

  /** Creates a new type expression for JSDoc like @type {{ propertyName : ? }} */
  private static JSTypeExpression createNewTypeExpressionForExtern(
      String propName, String sourceName, MemberDefinition prop) {
    Node leftCurly = new Node(Token.LC);
    Node leftBracket = new Node(Token.LB);
    Node colon = new Node(Token.COLON);
    Node propertyName = Node.newString(propName);
    propertyName.setToken(Token.STRING_KEY);
    Node unknown = new Node(Token.QMARK);
    colon.addChildToBack(propertyName);
    colon.addChildToBack(unknown);
    leftBracket.addChildToBack(colon);
    leftCurly.addChildToBack(leftBracket);
    leftCurly.useSourceInfoIfMissingFromForTree(prop.name);
    return new JSTypeExpression(leftCurly, sourceName);
  }

  /** Remove all JSDocs from properties of a class definition */
  private void removePropertyDocs(
      final Node objLit, PolymerClassDefinition.DefinitionType defType) {
    for (MemberDefinition prop :
        PolymerPassStaticUtils.extractProperties(
            objLit,
            defType,
            compiler,
            /** constructor= */
            null)) {
      prop.name.removeProp(Node.JSDOC_INFO_PROP);
    }
  }

  /** Appends all required behavior functions and non-property members to the given block. */
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
        NodeUtil.markNewScopesChanged(fnValue, compiler);
        Node exprResult =
            IR.exprResult(IR.assign(NodeUtil.newQName(compiler, qualifiedPath + fnName), fnValue));
        exprResult.useSourceInfoIfMissingFromForTree(behaviorFunction.name);
        JSDocInfoBuilder info = JSDocInfoBuilder.maybeCopyFrom(behaviorFunction.info);
        // Uses of private members that come from behaviors are not recognized correctly,
        // so just suppress that warning.
        info.addSuppression("unusedPrivateMembers");

        // If the function in the behavior is @protected, switch it to @public so that
        // we don't get a visibility warning. This is a bit of a hack but easier than
        // making the type system understand that methods are "inherited" from behaviors.
        if (behaviorFunction.info != null
            && behaviorFunction.info.getVisibility() == Visibility.PROTECTED) {
          info.overwriteVisibility(Visibility.PUBLIC);
        }

        // Behaviors whose declarations are not in the global scope may contain references to
        // symbols which do not exist in the element's scope. Only copy a function stub.
        if (!behavior.isGlobalDeclaration) {
          Node body = NodeUtil.getFunctionBody(fnValue);
          if (fnValue.isArrowFunction() && !NodeUtil.getFunctionBody(fnValue).isBlock()) {
            // replace `() => <someExpr>` with `() => undefined`
            body.replaceWith(NodeUtil.newUndefinedNode(body));
          } else {
            body.removeChildren();
          }
          // Remove any non-named parameters, which may reference locals.
          int paramIndex = 0;
          for (Node param : NodeUtil.getFunctionParameters(fnValue).children()) {
            makeParamSafe(param, paramIndex++);
          }
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
        exprResult.useSourceInfoFromForTree(behaviorProp.name);
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

  /** Removes any potential local names referenced within a formal parameter */
  private static void makeParamSafe(Node param, int index) {
    if (param.isRest()) {
      // The lhs may be a destructuring pattern.
      param = param.getOnlyChild();
    } else if (param.isDefaultValue()) {
      // Replace default value with void 0, then look at the lhs
      Node value = param.getSecondChild();
      value.replaceWith(NodeUtil.newUndefinedNode(param));
      param = param.getFirstChild();
    }

    if (param.isDestructuringPattern()) {
      param.replaceWith(IR.name("param$polymer$" + index).srcref(param));
    }
  }

  /**
   * Adds the generated setter for a readonly property.
   *
   * @see https://www.polymer-project.org/0.8/docs/devguide/properties.html#read-only
   */
  private Node makeReadOnlySetter(MemberDefinition prop, String qualifiedPath) {
    String propName = prop.name.getString();
    String setterName =
        "_set" + propName.substring(0, 1).toUpperCase(Locale.ROOT) + propName.substring(1);
    Node fnNode = IR.function(IR.name(""), IR.paramList(IR.name(propName)), IR.block());
    compiler.reportChangeToChangeScope(fnNode);
    Node exprResNode =
        IR.exprResult(IR.assign(NodeUtil.newQName(compiler, qualifiedPath + setterName), fnNode));

    JSDocInfoBuilder info = new JSDocInfoBuilder(true);
    // This is overriding a generated function which was added to the interface in
    // {@code createExportsAndExterns}.
    info.recordOverride();
    JSTypeExpression propType = PolymerPassStaticUtils.getTypeFromProperty(prop, compiler);
    info.recordParameter(propName, propType);
    exprResNode.getFirstChild().setJSDocInfo(info.build());
    return exprResNode;
  }

  /**
   * Create exports and externs to protect element properties and methods from renaming and dead
   * code removal.
   *
   * <p>Since Polymer templates, observers, and computed properties rely on string references to
   * element properties and methods, and because we don't yet have a way to update those references
   * reliably, we instead export or extern them.
   *
   * <p>For properties, we create a new interface called {@code Polymer<ElementName>Interface}, add
   * all element properties to it, mark that the element class {@code @implements} this interface,
   * and add the interface to the Closure externs. The specific set of properties we add to this
   * interface is determined by the value of {@code polymerExportPolicy}.
   *
   * <p>For methods, when {@code polymerExportPolicy = EXPORT_ALL}, we instead append to {@code
   * Object.prototype} in the externs using {@code @export} annotations. This approach is a
   * compromise, with the following alternatives considered:
   *
   * <p>Alternative 1: Add methods to our generated {@code Polymer<ElementName>Interface} in the
   * externs. Pro: More optimal than {@code Object.prototype} when type-aware optimizations are
   * enabled. Con 1: When a class {@code @implements} an interface, and when {@code
   * report_missing_override} is enabled, any method on the class that is also in the interface must
   * have an {@code @override} annotation, which means we generate a spurious warning for all
   * methods. Con 2: An unresolved bug was encountered (b/115942961) relating to a mismatch between
   * the signatures of the class and the generated interface.
   *
   * <p>Alternative 2: Generate goog.exportProperty calls, which causes aliases on the prototype
   * from original to optimized names to be set. Pro: Compiled code can still use the optimized
   * name. Con: In practice, for Polymer applications, we see a net increase in bundle size due to
   * the high number of new {@code Foo.prototype.originalName = Foo.prototype.z} expressions.
   *
   * <p>Alternative 3: Append directly to the {@code Object.prototype} externs, instead of using
   * {@code @export} annotations for the {@link GenerateExports} pass. Pro: Doesn't depend on the
   * {@code generate_exports} and {@code export_local_property_definitions} flags. Con: The
   * PolymerPass runs in the type checking phase, so modifying {@code Object.prototype} here causes
   * unwanted type checking effects, such as allowing the method to be called on any object, and
   * generating incorrect warnings when {@code report_missing_override} is enabled.
   */
  private void createExportsAndExterns(
      final PolymerClassDefinition cls,
      List<MemberDefinition> readOnlyProps,
      List<MemberDefinition> attributeReflectedProps) {
    Node block = IR.block();

    String interfaceName = cls.getInterfaceName(compiler.getUniqueNameIdSupplier());
    Node fnNode = NodeUtil.emptyFunction();
    compiler.reportChangeToChangeScope(fnNode);
    Node varNode = IR.var(NodeUtil.newQName(compiler, interfaceName), fnNode);

    JSDocInfoBuilder info = new JSDocInfoBuilder(true);
    info.recordInterface();
    varNode.setJSDocInfo(info.build());
    block.addChildToBack(varNode);
    String interfaceBasePath = interfaceName + ".prototype.";

    if (polymerExportPolicy == PolymerExportPolicy.EXPORT_ALL) {
      // Properties from behaviors were added to our element definition earlier.
      appendPropertiesToBlock(cls.props, block, interfaceBasePath, /* isExternsBlock= */ true);

      // Methods from behaviors were not already added to our element definition, so we need to
      // export those in addition to methods defined directly on the element. Note it's possible
      // and valid for two behaviors, or a behavior and an element, to implement the same method,
      // so we de-dupe by name. We're not checking that the signatures are compatible in the way
      // that normal class inheritance would, but that's not easy to do since these aren't classes.
      // Class mixins replace Polymer behaviors and are supported directly by Closure, so new code
      // should use those instead.
      LinkedHashMap<String, MemberDefinition> uniqueMethods = new LinkedHashMap<>();
      if (cls.behaviors != null) {
        for (BehaviorDefinition behavior : cls.behaviors) {
          for (MemberDefinition method : behavior.functionsToCopy) {
            uniqueMethods.put(method.name.getString(), method);
          }
        }
      }
      for (MemberDefinition method : cls.methods) {
        uniqueMethods.put(method.name.getString(), method);
      }
      for (MemberDefinition method : uniqueMethods.values()) {
        addMethodToObjectExternsUsingExportAnnotation(cls, method);
      }

    } else if (polymerVersion == 1) {
      // For Polymer 1, all declared properties are non-renameable
      appendPropertiesToBlock(cls.props, block, interfaceBasePath, /* isExternsBlock= */ true);
    } else {
      // For Polymer 2, only read-only properties and reflectToAttribute properties are
      // non-renameable. Other properties follow the ALL_UNQUOTED renaming rules.
      List<MemberDefinition> interfaceProperties = new ArrayList<>();
      interfaceProperties.addAll(readOnlyProps);
      if (attributeReflectedProps != null) {
        interfaceProperties.addAll(attributeReflectedProps);
      }
      appendPropertiesToBlock(
          interfaceProperties, block, interfaceBasePath, /* isExternsBlock= */ true);
    }

    for (MemberDefinition prop : readOnlyProps) {
      // Add all _set* functions to avoid renaming.
      String propName = prop.name.getString();
      String setterName =
          "_set" + propName.substring(0, 1).toUpperCase(Locale.ROOT) + propName.substring(1);
      Node setterExprNode =
          IR.exprResult(NodeUtil.newQName(compiler, interfaceBasePath + setterName));

      JSDocInfoBuilder setterInfo = new JSDocInfoBuilder(true);
      JSTypeExpression propType = PolymerPassStaticUtils.getTypeFromProperty(prop, compiler);
      JSTypeExpression unknown =
          new JSTypeExpression(new Node(Token.QMARK), propType.getSourceName());
      setterInfo.recordParameter(propName, unknown);
      setterExprNode.getFirstChild().setJSDocInfo(setterInfo.build());

      block.addChildToBack(setterExprNode);
    }

    block.useSourceInfoIfMissingFromForTree(polymerElementExterns);

    Node scopeRoot = polymerElementExterns;
    if (!scopeRoot.isScript()) {
      scopeRoot = scopeRoot.getParent();
    }
    Node stmts = block.removeChildren();
    scopeRoot.addChildrenToBack(stmts);

    compiler.reportChangeToEnclosingScope(stmts);
  }

  /**
   * Add a method to {@code Object.prototype} in the externs by inserting a {@code GETPROP}
   * expression with an {@code @export} annotation into the program.
   *
   * <p>This relies on the {@code --generate_exports} and {@code export_local_property_definitions}
   * flags to enable the {@link GenerateExports} pass, which will add properties exported in this
   * way to {@code Object.prototype} in the externs, thus preventing renaming and dead code removal.
   * Note that {@link GenerateExports} runs after type checking, so extending {@code
   * Object.prototype} does not cause unwanted type checking effects.
   */
  private void addMethodToObjectExternsUsingExportAnnotation(
      PolymerClassDefinition cls, MemberDefinition method) {
    Node getprop =
        NodeUtil.newQName(
            compiler, cls.target.getQualifiedName() + ".prototype." + method.name.getString());
    JSDocInfoBuilder info =
        new JSDocInfoBuilder(
            /** parseDocumentation */
            true);
    if (method.info != null) {
      // We need to preserve visibility, but other JSDoc doesn't matter (and can cause
      // re-declaration errors).
      info.recordVisibility(method.info.getVisibility());
    }
    info.recordExport();
    getprop.setJSDocInfo(info.build());
    Node expression = IR.exprResult(getprop).useSourceInfoIfMissingFromForTree(method.name);
    // Walk up until we find a statement we can insert after.
    Node insertAfter = cls.definition;
    while (!NodeUtil.isStatementBlock(insertAfter.getParent())) {
      insertAfter = insertAfter.getParent();
    }
    insertAfter.getParent().addChildAfter(expression, insertAfter);
    compiler.reportChangeToEnclosingScope(expression);
  }

  /** Returns an assign replacing the equivalent var or let declaration. */
  private static Node varToAssign(Node var) {
    Node assign =
        IR.assign(var.getFirstChild().cloneNode(), var.getFirstChild().removeFirstChild());
    return IR.exprResult(assign).useSourceInfoIfMissingFromForTree(var);
  }

  /**
   * Converts property observer strings to direct function references.
   *
   * <p>From: <code>observer: '_observerName'</code> To: <code>
   * observer: ClassName.prototype._observerName</code>
   */
  private void convertSimpleObserverStringsToReferences(final PolymerClassDefinition cls) {
    for (MemberDefinition prop : cls.props) {
      if (prop.value.isObjectLit()) {
        Node observer = NodeUtil.getFirstPropMatchingKey(prop.value, "observer");
        if (observer != null && observer.isString()) {
          Node observerDirectReference =
              IR.getprop(cls.target.cloneTree(), "prototype", observer.getString())
                  .useSourceInfoFrom(observer);

          observer.replaceWith(observerDirectReference);
          compiler.reportChangeToEnclosingScope(observerDirectReference);
        }
      }
    }
  }

  /**
   * For any property in the Polymer property configuration object with a `computed` key, parse the
   * method call and path arguments and replace them with property reflection calls.
   *
   * <p>Returns a list of property sink statements to guard against dead code elimination since the
   * compiler may not see these methods as being used.
   */
  private List<Node> addComputedPropertiesReflectionCalls(final PolymerClassDefinition cls) {
    List<Node> propertySinkStatements = new ArrayList<>();
    for (MemberDefinition prop : cls.props) {
      if (prop.value.isObjectLit()) {
        Node computed = NodeUtil.getFirstPropMatchingKey(prop.value, "computed");
        if (computed != null && computed.isString()) {
          propertySinkStatements.addAll(
              replaceMethodStringWithReflectedCalls(cls.target, computed));
        }
      }
    }
    return propertySinkStatements;
  }

  /**
   * For any strings returned in the array from the Polymer static observers property, parse the
   * method call and path arguments and replace them with property reflection calls.
   *
   * <p>Returns a list of property sink statements to guard against dead code elimination since the
   * compiler may not see these methods as being used.
   */
  private List<Node> addComplexObserverReflectionCalls(final PolymerClassDefinition cls) {
    List<Node> propertySinkStatements = new ArrayList<>();
    Node classMembers = NodeUtil.getClassMembers(cls.definition);
    Node getter = NodeUtil.getFirstGetterMatchingKey(classMembers, "observers");
    if (getter != null) {
      Node complexObservers = null;
      for (Node child : NodeUtil.getFunctionBody(getter.getFirstChild()).children()) {
        if (child.isReturn()) {
          if (child.hasChildren() && child.getFirstChild().isArrayLit()) {
            complexObservers = child.getFirstChild();
            break;
          }
        }
      }
      if (complexObservers != null) {
        for (Node complexObserver : complexObservers.children()) {
          if (complexObserver.isString()) {
            propertySinkStatements.addAll(
                replaceMethodStringWithReflectedCalls(cls.target, complexObserver));
          }
        }
      }
    }
    return propertySinkStatements;
  }

  /**
   * Given a Polymer method call string such as: <code>"methodName(path.item, other.property.path)"
   * </code> parses the string into a method name and arguments and builds up a new string of
   * property reflection calls so that the properties can be renamed consistently.
   *
   * <p>Returns a list of property sink statements to guard against dead code elimination.
   */
  private List<Node> replaceMethodStringWithReflectedCalls(Node className, Node methodSignature) {
    checkArgument(methodSignature.isString());
    List<Node> propertySinkStatements = new ArrayList<>();
    String methodSignatureString = methodSignature.getString().trim();
    int openParenIndex = methodSignatureString.indexOf('(');
    if (methodSignatureString.charAt(methodSignatureString.length() - 1) != ')'
        || openParenIndex < 1) {
      compiler.report(JSError.make(methodSignature, PolymerPassErrors.POLYMER_UNPARSABLE_STRING));
      return propertySinkStatements;
    }

    // Reflect property calls require an instance of a type. Since we don't have one,
    // just cast an object literal to be that type. While not generally safe, it is
    // safe for property reflection.
    JSDocInfoBuilder classTypeDoc = new JSDocInfoBuilder(false);
    JSTypeExpression classType =
        new JSTypeExpression(
            new Node(Token.BANG, IR.string(className.getQualifiedName()))
                .srcrefTree(methodSignature),
            className.getSourceFileName());
    classTypeDoc.recordType(classType);
    Node classTypeExpression = IR.cast(IR.objectlit(), classTypeDoc.build());

    // Add reflect and property sinks for the method name which will be a property on the class
    String methodName = methodSignatureString.substring(0, openParenIndex).trim();
    propertySinkStatements.add(
        IR.getprop(className.cloneTree(), "prototype", methodName)
            .useSourceInfoFromForTree(methodSignature));

    Node reflectedMethodName =
        IR.call(
            IR.getprop(IR.name("$jscomp"), IR.string("reflectProperty")),
            IR.string(methodName),
            classTypeExpression.cloneTree());

    Node reflectedSignature = reflectedMethodName;

    // Process any parameters in the method call
    String nextParamDelimeter = "(";
    if (openParenIndex < methodSignatureString.length() - 2) {
      String methodParamsString =
          methodSignatureString
              .substring(openParenIndex + 1, methodSignatureString.length() - 1)
              .trim();

      List<String> methodParams = parseMethodParams(methodParamsString, methodSignature);

      // Add property reflection for each parameter
      for (String methodParam : methodParams) {
        Node reflectedTypeReference = classTypeExpression;
        if (methodParam.length() == 0) {
          continue;
        }

        if (isParamLiteral(methodParam)) {
          Node term = IR.string(methodParam);

          reflectedSignature =
              IR.add(IR.add(reflectedSignature, IR.string(nextParamDelimeter)), term);
        } else {
          // Arguments in conmplex observer or computed property strings are property paths.
          // We need to rename each path segment.
          List<String> paramParts = Splitter.on('.').splitToList(methodParam);
          String nextPropertyTermDelimiter = nextParamDelimeter;
          for (int i = 0; i < paramParts.size(); i++) {
            // Polymer property paths have two special terms recognized when they are the last
            // path reference:
            //   - * - any sub-property change
            //   - splices - Adds or removes of array items
            // These terms are not renamable and are left as is
            if (i > 0
                && i == paramParts.size() - 1
                && (paramParts.get(i).equals("*") || paramParts.get(i).equals("splices"))) {
              reflectedSignature =
                  IR.add(
                      reflectedSignature, IR.string(nextPropertyTermDelimiter + paramParts.get(i)));
            } else {
              if (i == 0) {
                // The root of the parameter will be a property reference on the class
                // Create both a property sink and a reflection call
                propertySinkStatements.add(
                    IR.getprop(
                            className.cloneTree(),
                            IR.string("prototype"),
                            IR.string(paramParts.get(i)))
                        .useSourceInfoFromForTree(methodSignature));
              }
              Node reflectedParamPart =
                  IR.call(
                      IR.getprop(IR.name("$jscomp"), IR.string("reflectProperty")),
                      IR.string(paramParts.get(i)),
                      reflectedTypeReference.cloneTree());
              reflectedSignature =
                  IR.add(
                      IR.add(reflectedSignature, IR.string(nextPropertyTermDelimiter)),
                      reflectedParamPart);

              reflectedTypeReference =
                  IR.getprop(reflectedTypeReference.cloneTree(), paramParts.get(i));
            }
            nextPropertyTermDelimiter = ".";
          }
        }
        nextParamDelimeter = ",";
      }

      if (methodParams.size() == 0) {
        reflectedSignature = IR.add(reflectedSignature, IR.string("()"));
      } else {
        reflectedSignature = IR.add(reflectedSignature, IR.string(")"));
      }
    } else {
      reflectedSignature = IR.add(reflectedSignature, IR.string("()"));
    }

    methodSignature.replaceWith(reflectedSignature.useSourceInfoFromForTree(methodSignature));
    compiler.reportChangeToEnclosingScope(reflectedSignature);
    return propertySinkStatements;
  }

  /**
   * Parses the parameters string from a complex observer or computed property into distinct
   * parameters. Since a parameter can be a quoted string literal, we can't just split on commas.
   */
  private List<String> parseMethodParams(String methodParameters, Node methodSignature) {
    List<String> parsedParameters = new ArrayList<>();

    char nextDelimeter = ',';
    String currentTerm = "";
    for (int i = 0; i < methodParameters.length(); i++) {
      if (methodParameters.charAt(i) == nextDelimeter) {
        if (nextDelimeter == ',') {
          parsedParameters.add(currentTerm.trim());
          currentTerm = "";
        } else {
          currentTerm += nextDelimeter;
          nextDelimeter = ',';
        }
      } else {
        currentTerm += methodParameters.charAt(i);
        if (methodParameters.charAt(i) == '"' || methodParameters.charAt(i) == '\'') {
          nextDelimeter = methodParameters.charAt(i);
        }
      }
    }
    if (nextDelimeter != ',') {
      compiler.report(JSError.make(methodSignature, PolymerPassErrors.POLYMER_UNPARSABLE_STRING));
      return parsedParameters;
    }
    if (currentTerm.length() > 0) {
      parsedParameters.add(currentTerm.trim());
    }
    return parsedParameters;
  }

  /** Determine if the method parameter a quoted string or numeric literal recognized by Polymer. */
  private static boolean isParamLiteral(String param) {
    try {
      Double.parseDouble(param);
      return true;
    } catch (NumberFormatException e) {
      // Check to see if the parameter is a literal - either a quoted string or
      // numeric literal
      if (param.length() > 1
          && (param.charAt(0) == '"' || param.charAt(0) == '\'')
          && param.charAt(0) == param.charAt(param.length() - 1)) {
        return true;
      }
    }
    return false;
  }

  private static Node getInsertionPointForGoogModule(Node moduleBody) {
    Node insertionPoint = moduleBody.getFirstChild(); // goog.module('ns');
    Node next = insertionPoint.getNext();
    while (isGoogRequireExpr(next)
        || NodeUtil.isGoogModuleDeclareLegacyNamespaceCall(next)
        || NodeUtil.isGoogSetTestOnlyCall(next)) {
      insertionPoint = next;
      next = next.getNext();
    }
    return insertionPoint;
  }

  private static boolean isGoogRequireExpr(Node statement) {
    if (NodeUtil.isExprCall(statement)
        && ModuleImportResolver.isGoogModuleDependencyCall(statement.getOnlyChild())) {
      // `goog.require('a.b.c');`
      return true;
    }
    if (!NodeUtil.isNameDeclaration(statement)) {
      return false;
    }
    Node rhs =
        statement.getFirstChild().isName()
            // `const c = goog.require('a.b.c');`
            ? statement.getFirstFirstChild()
            // `const {D} = goog.require('a.b.c');`
            : statement.getFirstChild().getSecondChild();
    return ModuleImportResolver.isGoogModuleDependencyCall(rhs);
  }
}
