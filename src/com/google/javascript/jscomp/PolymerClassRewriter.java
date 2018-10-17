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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
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

  private final Node polymerElementExterns;

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

  /**
   * Rewrites a given call to Polymer({}) to a set of declarations and assignments which can be
   * understood by the compiler.
   *
   * @param exprRoot The root expression of the call to Polymer({}).
   * @param cls The extracted {@link PolymerClassDefinition} for the Polymer element created by this
   *     call.
   */
  void rewritePolymerCall(
      Node exprRoot, final PolymerClassDefinition cls, boolean isInGlobalScope) {
    Node objLit = checkNotNull(cls.descriptor);

    // Add {@code @lends} to the object literal.
    JSDocInfoBuilder objLitDoc = new JSDocInfoBuilder(true);
    JSTypeExpression jsTypeExpression =
        new JSTypeExpression(
            IR.string(cls.target.getQualifiedName() + ".prototype"), exprRoot.getSourceFileName());
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
      Node assign = IR.assign(cls.target.cloneTree(), cls.constructor.value.cloneTree());
      NodeUtil.markNewScopesChanged(assign, compiler);
      assign.setJSDocInfo(constructorDoc.build());
      Node exprResult = IR.exprResult(assign);
      exprResult.useSourceInfoIfMissingFromForTree(cls.target);
      block.addChildToBack(exprResult);
    } else {
      // var foo = Polymer({...}); OR Polymer({...});
      Node var = IR.var(cls.target.cloneTree(), cls.constructor.value.cloneTree());
      NodeUtil.markNewScopesChanged(var, compiler);
      var.useSourceInfoIfMissingFromForTree(exprRoot);
      var.setJSDocInfo(constructorDoc.build());
      block.addChildToBack(var);
    }

    appendPropertiesToBlock(cls.props, block, cls.target.getQualifiedName() + ".prototype.");
    appendBehaviorMembersToBlock(cls, block);
    ImmutableList<MemberDefinition> readOnlyProps = parseReadOnlyProperties(cls, block);
    ImmutableList<MemberDefinition> attributeReflectedProps =
        parseAttributeReflectedProperties(cls);
    createExportsAndExterns(cls, readOnlyProps, attributeReflectedProps);
    removePropertyDocs(objLit, PolymerClassDefinition.DefinitionType.ObjectLiteral);

    Node statements = block.removeChildren();
    Node parent = exprRoot.getParent();

    // If the call to Polymer() is not in the global scope and the assignment target
    // is not namespaced (which likely means it's exported to the global scope), put the type
    // declaration into the global scope at the start of the current script.
    //
    // This avoids unknown type warnings which are a result of the compiler's poor understanding of
    // types declared inside IIFEs or any non-global scope. We should revisit this decision as
    // the typechecker's support for non-global types improves.
    if (!isInGlobalScope && !cls.target.isGetProp()) {
      Node scriptNode = NodeUtil.getEnclosingScript(parent);
      scriptNode.addChildrenToFront(statements);
      compiler.reportChangeToChangeScope(scriptNode);
    } else {
      Node beforeRoot = exprRoot.getPrevious();
      if (beforeRoot == null) {
        parent.addChildrenToFront(statements);
      } else {
        parent.addChildrenAfter(statements, beforeRoot);
      }
      compiler.reportChangeToEnclosingScope(parent);
    }
    compiler.reportChangeToEnclosingScope(statements);

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
        addObjectReflectionCall(cls, props);
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
      Node clazz, final PolymerClassDefinition cls, boolean isInGlobalScope) {

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
    appendPropertiesToBlock(cls.props, block, cls.target.getQualifiedName() + ".prototype.");

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
      String interfaceName = getInterfaceName(cls);
      JSTypeExpression interfaceType =
          new JSTypeExpression(new Node(Token.BANG, IR.string(interfaceName)), VIRTUAL_FILE);
      classInfo.recordImplementedInterface(interfaceType);
      jsDocInfoNode.setJSDocInfo(classInfo.build());
    }

    if (block.hasChildren()) {
      removePropertyDocs(cls.descriptor, cls.defType);
      Node stmt = NodeUtil.getEnclosingStatement(clazz);
      stmt.getParent().addChildrenAfter(block.removeChildren(), stmt);
      compiler.reportChangeToEnclosingScope(stmt);
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
    if (propertyRenamingEnabled && cls.descriptor != null) {
      addObjectReflectionCall(cls, cls.descriptor);
    }
  }

  /** Adds return type information to class getters */
  private void addReturnTypeIfMissing(
      PolymerClassDefinition cls, String getterPropName, JSTypeExpression jsType) {
    Node classMembers = NodeUtil.getClassMembers(cls.definition);
    Node getter = NodeUtil.getFirstGetterMatchingKey(classMembers, getterPropName);
    if (getter != null) {
      JSDocInfo info = NodeUtil.getBestJSDocInfo(getter);
      if (info == null || !info.hasReturnType()) {
        JSDocInfoBuilder builder = JSDocInfoBuilder.maybeCopyFrom(info);
        builder.recordReturnType(jsType);
        getter.setJSDocInfo(builder.build());
      }
    }
  }

  /** Wrap the properties config object in an objectReflect call */
  private void addObjectReflectionCall(PolymerClassDefinition cls, Node propertiesLiteral) {
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
            new JSTypeExpression(new Node(Token.BANG, IR.string(thisType)), VIRTUAL_FILE));
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
          new JSTypeExpression(new Node(Token.BANG, IR.string(thisType)), VIRTUAL_FILE));
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
          Node setter = makeReadOnlySetter(prop.name.getString(), qualifiedPath);
          setter.useSourceInfoIfMissingFromForTree(prop.name);
          block.addChildToBack(setter);
          readOnlyProps.add(prop);
        }
      }
    }

    return readOnlyProps.build();
  }

  /**
   * Generates the _set* setters for readonly properties and appends them to the given block.
   *
   * @return A List of all readonly properties.
   */
  private ImmutableList<MemberDefinition> parseAttributeReflectedProperties(
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
            new Node(Token.BANG, IR.string(PolymerPassStaticUtils.getPolymerElementType(cls))),
            VIRTUAL_FILE);
    constructorDoc.recordBaseType(baseType);

    String interfaceName = getInterfaceName(cls);
    JSTypeExpression interfaceType =
        new JSTypeExpression(new Node(Token.BANG, IR.string(interfaceName)), VIRTUAL_FILE);
    constructorDoc.recordImplementedInterface(interfaceType);

    return constructorDoc;
  }

  /** Appends all of the given properties to the given block. */
  private void appendPropertiesToBlock(List<MemberDefinition> props, Node block, String basePath) {
    for (MemberDefinition prop : props) {
      Node propertyNode =
          IR.exprResult(NodeUtil.newQName(compiler, basePath + prop.name.getString()));

      // If a property string is quoted, make sure the added prototype properties are also quoted
      if (prop.name.isQuotedString()) {
        continue;
      }

      propertyNode.useSourceInfoIfMissingFromForTree(prop.name);
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

  /**
   * Adds the generated setter for a readonly property.
   *
   * @see https://www.polymer-project.org/0.8/docs/devguide/properties.html#read-only
   */
  private Node makeReadOnlySetter(String propName, String qualifiedPath) {
    String setterName = "_set" + propName.substring(0, 1).toUpperCase() + propName.substring(1);
    Node fnNode = IR.function(IR.name(""), IR.paramList(IR.name(propName)), IR.block());
    compiler.reportChangeToChangeScope(fnNode);
    Node exprResNode =
        IR.exprResult(IR.assign(NodeUtil.newQName(compiler, qualifiedPath + setterName), fnNode));

    JSDocInfoBuilder info = new JSDocInfoBuilder(true);
    // This is overriding a generated function which was added to the interface in
    // {@code createExportsAndExterns}.
    info.recordOverride();
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

    String interfaceName = getInterfaceName(cls);
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
      appendPropertiesToBlock(cls.props, block, interfaceBasePath);

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
      appendPropertiesToBlock(cls.props, block, interfaceBasePath);
    } else {
      // For Polymer 2, only read-only properties and reflectToAttribute properties are
      // non-renameable. Other properties follow the ALL_UNQUOTED renaming rules.
      List<MemberDefinition> interfaceProperties = new ArrayList<>();
      interfaceProperties.addAll(readOnlyProps);
      if (attributeReflectedProps != null) {
        interfaceProperties.addAll(attributeReflectedProps);
      }
      appendPropertiesToBlock(interfaceProperties, block, interfaceBasePath);
    }

    for (MemberDefinition prop : readOnlyProps) {
      // Add all _set* functions to avoid renaming.
      String propName = prop.name.getString();
      String setterName = "_set" + propName.substring(0, 1).toUpperCase() + propName.substring(1);
      Node setterExprNode =
          IR.exprResult(NodeUtil.newQName(compiler, interfaceBasePath + setterName));

      JSDocInfoBuilder setterInfo = new JSDocInfoBuilder(true);
      JSTypeExpression propType = PolymerPassStaticUtils.getTypeFromProperty(prop, compiler);
      setterInfo.recordParameter(propName, propType);
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

  /** Returns the name of the generated extern interface which the element implements. */
  private static String getInterfaceName(final PolymerClassDefinition cls) {
    return "Polymer" + cls.target.getQualifiedName().replace('.', '_') + "Interface";
  }

  /** Returns an assign replacing the equivalent var or let declaration. */
  private static Node varToAssign(Node var) {
    Node assign =
        IR.assign(var.getFirstChild().cloneNode(), var.getFirstChild().removeFirstChild());
    return IR.exprResult(assign).useSourceInfoIfMissingFromForTree(var);
  }
}
