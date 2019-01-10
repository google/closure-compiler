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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.Es6ToEs3Util.CANNOT_CONVERT;
import static com.google.javascript.jscomp.Es6ToEs3Util.CANNOT_CONVERT_YET;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Converts ES6 classes to valid ES5 or ES3 code.
 */
public final class Es6RewriteClass implements NodeTraversal.Callback, HotSwapCompilerPass {
  private static final FeatureSet features =
      FeatureSet.BARE_MINIMUM.with(
          Feature.CLASSES,
          Feature.CLASS_EXTENDS,
          Feature.CLASS_GETTER_SETTER,
          Feature.NEW_TARGET);

  static final DiagnosticType DYNAMIC_EXTENDS_TYPE = DiagnosticType.error(
      "JSC_DYNAMIC_EXTENDS_TYPE",
      "The class in an extends clause must be a qualified name.");

  static final DiagnosticType CLASS_REASSIGNMENT = DiagnosticType.error(
      "CLASS_REASSIGNMENT",
      "Class names defined inside a function cannot be reassigned.");

  static final DiagnosticType CONFLICTING_GETTER_SETTER_TYPE = DiagnosticType.error(
      "CONFLICTING_GETTER_SETTER_TYPE",
      "The types of the getter and setter for property ''{0}'' do not match.");

  // This function is defined in js/es6/util/inherits.js
  static final String INHERITS = "$jscomp.inherits";

  private final AbstractCompiler compiler;
  private final JSTypeRegistry registry;
  private final AstFactory astFactory;
  private final JSType objectPropertyDescriptorType;

  public Es6RewriteClass(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.registry = compiler.getTypeRegistry();
    this.astFactory = compiler.createAstFactory();

    // Finds the type for `ObjectPropertyDescriptor`. Fallback to the unknown type if it's not
    // present, which may happen if typechecking hasn't run or this is a unit test w/o externs.
    JSType actualObjectPropertyDescriptorType = registry.getGlobalType("ObjectPropertyDescriptor");
    this.objectPropertyDescriptorType =
        actualObjectPropertyDescriptorType != null
            ? actualObjectPropertyDescriptorType
            : registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, externs, features, this);
    TranspilationPasses.processTranspile(compiler, root, features, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, features);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(compiler, scriptRoot, features, this);
    // Don't mark features as transpiled away if we had errors that prevented transpilation.
    // We don't want a redundant error from the AstValidator complaining that the features are still
    // there
    if (!compiler.hasHaltingErrors()) {
      TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, features);
    }
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case GETTER_DEF:
      case SETTER_DEF:
        if (FeatureSet.ES3.contains(compiler.getOptions().getOutputFeatureSet())) {
          cannotConvert(n, "ES5 getters/setters (consider using --language_out=ES5)");
          return false;
        }
        break;
      case NEW_TARGET:
        cannotConvertYet(n, "new.target");
        break;
      default:
        break;
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case CLASS:
        visitClass(t, n, parent);
        break;
      default:
        break;
    }
  }

  private void checkClassReassignment(Node clazz) {
    Node name = NodeUtil.getNameNode(clazz);
    Node enclosingFunction = NodeUtil.getEnclosingFunction(clazz);
    if (enclosingFunction == null) {
      return;
    }
    CheckClassAssignments checkAssigns = new CheckClassAssignments(name);
    NodeTraversal.traverse(compiler, enclosingFunction, checkAssigns);
  }

  /**
   * Classes are processed in 3 phases:
   * <ol>
   *   <li>The class name is extracted.
   *   <li>Class members are processed and rewritten.
   *   <li>The constructor is built.
   * </ol>
   */
  private void visitClass(final NodeTraversal t, final Node classNode, final Node parent) {
    checkClassReassignment(classNode);
    // Collect Metadata
    ClassDeclarationMetadata metadata =
        ClassDeclarationMetadata.create(classNode, parent, astFactory);

    if (metadata == null) {
      throw new IllegalStateException(
          "Can only convert classes that are declarations or the right hand"
          + " side of a simple assignment: " + classNode);
    }
    if (metadata.hasSuperClass() && !metadata.getSuperClassNameNode().isQualifiedName()) {
      compiler.report(JSError.make(metadata.getSuperClassNameNode(), DYNAMIC_EXTENDS_TYPE));
      return;
    }

    Preconditions.checkState(
        NodeUtil.isStatement(metadata.getInsertionPoint().getNode()),
        "insertion point must be a statement: %s",
        metadata.getInsertionPoint().getNode());

    Node constructor = null;
    JSDocInfo ctorJSDocInfo = null;
    // Process all members of the class
    Node classMembers = classNode.getLastChild();
    for (Node member : classMembers.children()) {
      if ((member.isComputedProp()
              && (member.getBooleanProp(Node.COMPUTED_PROP_GETTER)
                  || member.getBooleanProp(Node.COMPUTED_PROP_SETTER)))
          || (member.isGetterDef() || member.isSetterDef())) {
        visitNonMethodMember(member, metadata);
      } else if (member.isMemberFunctionDef() && member.getString().equals("constructor")) {
        ctorJSDocInfo = member.getJSDocInfo();
        constructor = member.getFirstChild().detach().setJSType(classNode.getJSType());
        constructor.setJSTypeBeforeCast(classNode.getJSTypeBeforeCast());
        if (!metadata.isAnonymous()) {
          // Turns class Foo { constructor: function() {} } into function Foo() {},
          // i.e. attaches the name to the ctor function.
          constructor.replaceChild(
              constructor.getFirstChild(), metadata.getClassNameNode().cloneNode());
        }
      } else if (member.isEmpty()) {
        // Do nothing.
      } else {
        Preconditions.checkState(member.isMemberFunctionDef() || member.isComputedProp(),
            "Unexpected class member:", member);
        Preconditions.checkState(!member.getBooleanProp(Node.COMPUTED_PROP_VARIABLE),
            "Member variables should have been transpiled earlier:", member);
        visitMethod(member, metadata);
      }
    }
    checkNotNull(
        constructor,
        "Es6RewriteClasses expects all classes to have (possibly synthetic) constructors");

    if (metadata.getDefinePropertiesObjForPrototype().hasChildren()) {
      compiler.ensureLibraryInjected("util/global", false);
      Node definePropsCall =
          IR.exprResult(
              astFactory.createCall(
                  createObjectDotDefineProperties(t.getScope()),
                  metadata.getClassPrototypeNode().cloneTree(),
                  metadata.getDefinePropertiesObjForPrototype()));
      definePropsCall.useSourceInfoIfMissingFromForTree(classNode);
      metadata.insertNodeAndAdvance(definePropsCall);
    }

    if (metadata.getDefinePropertiesObjForClass().hasChildren()) {
      compiler.ensureLibraryInjected("util/global", false);
      Node definePropsCall =
          IR.exprResult(
              astFactory.createCall(
                  createObjectDotDefineProperties(t.getScope()),
                  metadata.getFullClassNameNode().cloneTree(),
                  metadata.getDefinePropertiesObjForClass()));
      definePropsCall.useSourceInfoIfMissingFromForTree(classNode);
      metadata.insertNodeAndAdvance(definePropsCall);
    }
    JSDocInfo classJSDoc = NodeUtil.getBestJSDocInfo(classNode);
    JSDocInfoBuilder newInfo = JSDocInfoBuilder.maybeCopyFrom(classJSDoc);

    newInfo.recordConstructor();

    Node enclosingStatement = NodeUtil.getEnclosingStatement(classNode);
    if (metadata.hasSuperClass()) {
      String superClassString = metadata.getSuperClassNameNode().getQualifiedName();
      if (newInfo.isInterfaceRecorded()) {
        newInfo.recordExtendedInterface(
            new JSTypeExpression(
                new Node(Token.BANG, IR.string(superClassString)),
                metadata.getSuperClassNameNode().getSourceFileName()));
      } else {
        if (!classNode.isFromExterns()) {
          compiler.ensureLibraryInjected("es6/util/inherits", false);
          Node inheritsCall =
              IR.exprResult(
                      astFactory.createCall(
                          astFactory.createQName(t.getScope(), "$jscomp.inherits"),
                          metadata.getFullClassNameNode().cloneTree(),
                          metadata.getSuperClassNameNode().cloneTree()))
                  .useSourceInfoIfMissingFromForTree(metadata.getSuperClassNameNode());
          enclosingStatement.getParent().addChildAfter(inheritsCall, enclosingStatement);
        }
        newInfo.recordBaseType(
            new JSTypeExpression(
                new Node(Token.BANG, IR.string(superClassString)),
                metadata.getSuperClassNameNode().getSourceFileName()));
      }
    }

    addTypeDeclarations(t.getScope(), metadata, enclosingStatement);

    updateClassJsDoc(ctorJSDocInfo, newInfo);

    if (NodeUtil.isStatement(classNode)) {
      constructor.getFirstChild().setString("");
      Node ctorVar = IR.let(metadata.getClassNameNode().cloneNode(), constructor);
      ctorVar.useSourceInfoIfMissingFromForTree(classNode);
      parent.replaceChild(classNode, ctorVar);
      NodeUtil.addFeatureToScript(t.getCurrentFile(), Feature.LET_DECLARATIONS);
    } else {
      parent.replaceChild(classNode, constructor);
    }
    NodeUtil.markFunctionsDeleted(classNode, compiler);

    if (NodeUtil.isStatement(constructor)) {
      constructor.setJSDocInfo(newInfo.build());
    } else if (parent.isName()) {
      // The constructor function is the RHS of a var statement.
      // Add the JSDoc to the VAR node.
      Node var = parent.getParent();
      var.setJSDocInfo(newInfo.build());
    } else if (constructor.getParent().isName()) {
      // Is a newly created VAR node.
      Node var = constructor.getGrandparent();
      var.setJSDocInfo(newInfo.build());
    } else if (parent.isAssign()) {
      // The constructor function is the RHS of an assignment.
      // Add the JSDoc to the ASSIGN node.
      parent.setJSDocInfo(newInfo.build());
    } else {
      throw new IllegalStateException("Unexpected parent node " + parent);
    }

    FunctionType classType = JSType.toMaybeFunctionType(classNode.getJSType());
    if (classType != null) {
      // classNode is no longer in the AST, so we need to update the reference to it that is
      // stored in the class's type, if any.
      classType.setSource(constructor);
    }

    constructor.putBooleanProp(Node.IS_ES6_CLASS, true);
    t.reportCodeChange();
  }

  private Node createObjectDotDefineProperties(Scope scope) {
    return astFactory.createQName(scope, "$jscomp.global.Object.defineProperties");
  }

  /**
   * @param ctorInfo the JSDocInfo from the constructor method of the ES6 class.
   * @param newInfo the JSDocInfo that will be added to the constructor function in the ES3 output
   */
  private void updateClassJsDoc(@Nullable JSDocInfo ctorInfo, JSDocInfoBuilder newInfo) {
    // Classes are @struct by default.
    if (!newInfo.isUnrestrictedRecorded() && !newInfo.isDictRecorded()
        && !newInfo.isStructRecorded()) {
      newInfo.recordStruct();
    }

    if (ctorInfo != null) {
      if (!ctorInfo.getSuppressions().isEmpty()) {
        newInfo.recordSuppressions(ctorInfo.getSuppressions());
      }

      for (String param : ctorInfo.getParameterNames()) {
        newInfo.recordParameter(param, ctorInfo.getParameterType(param));
        newInfo.recordParameterDescription(param, ctorInfo.getDescriptionForParameter(param));
      }

      for (JSTypeExpression thrown : ctorInfo.getThrownTypes()) {
        newInfo.recordThrowType(thrown);
        newInfo.recordThrowDescription(thrown, ctorInfo.getThrowsDescriptionForType(thrown));
      }

      JSDocInfo.Visibility visibility = ctorInfo.getVisibility();
      if (visibility != null && visibility != JSDocInfo.Visibility.INHERITED) {
        newInfo.recordVisibility(visibility);
      }

      if (ctorInfo.isDeprecated()) {
        newInfo.recordDeprecated();
      }

      if (ctorInfo.getDeprecationReason() != null
          && !newInfo.isDeprecationReasonRecorded()) {
        newInfo.recordDeprecationReason(ctorInfo.getDeprecationReason());
      }

      newInfo.mergePropertyBitfieldFrom(ctorInfo);

      for (String templateType : ctorInfo.getTemplateTypeNames()) {
        newInfo.recordTemplateTypeName(templateType);
      }
    }
  }

  /**
   * @param node A getter or setter node.
   */
  @Nullable
  private JSTypeExpression getTypeFromGetterOrSetter(Node node) {
    JSDocInfo info = node.getJSDocInfo();
    if (info != null) {
      boolean getter = node.isGetterDef() || node.getBooleanProp(Node.COMPUTED_PROP_GETTER);
      if (getter && info.getReturnType() != null) {
        return info.getReturnType();
      } else {
        Set<String> paramNames = info.getParameterNames();
        if (paramNames.size() == 1) {
          JSTypeExpression paramType =
              info.getParameterType(Iterables.getOnlyElement(info.getParameterNames()));
          if (paramType != null) {
            return paramType;
          }
        }
      }
    }

    return null;
  }

  /**
   * @param member A getter or setter, or a computed property that is a getter/setter.
   */
  private void addToDefinePropertiesObject(ClassDeclarationMetadata metadata, Node member) {
    Node obj =
        member.isStaticMember()
            ? metadata.getDefinePropertiesObjForClass()
            : metadata.getDefinePropertiesObjForPrototype();
    Node prop =
        member.isComputedProp()
            ? NodeUtil.getFirstComputedPropMatchingKey(obj, member.getFirstChild())
            : NodeUtil.getFirstPropMatchingKey(obj, member.getString());
    if (prop == null) {
      prop = createPropertyDescriptor();
      if (member.isComputedProp()) {
        obj.addChildToBack(
            astFactory.createComputedProperty(member.getFirstChild().cloneTree(), prop));
      } else {
        Node stringKey = astFactory.createStringKey(member.getString(), prop);
        if (member.isQuotedString()) {
          stringKey.putBooleanProp(Node.QUOTED_PROP, true);
        }
        obj.addChildToBack(stringKey);
      }
    }

    Node function = member.getLastChild();
    JSDocInfoBuilder info = JSDocInfoBuilder.maybeCopyFrom(
        NodeUtil.getBestJSDocInfo(function));

    info.recordThisType(
        new JSTypeExpression(
            new Node(Token.BANG, IR.string(metadata.getFullClassNameNode().getQualifiedName())),
            member.getSourceFileName()));
    Node stringKey =
        astFactory.createStringKey(
            (member.isGetterDef() || member.getBooleanProp(Node.COMPUTED_PROP_GETTER))
                ? "get"
                : "set",
            function.detach());
    stringKey.setJSDocInfo(info.build());
    prop.addChildToBack(stringKey);
    prop.useSourceInfoIfMissingFromForTree(member);
  }

  /**
   * Visits class members other than simple methods: Getters, setters, and computed properties.
   */
  private void visitNonMethodMember(Node member, ClassDeclarationMetadata metadata) {
    if (member.isComputedProp() && member.isStaticMember()) {
      cannotConvertYet(member, "Static computed property");
      return;
    }
    if (member.isComputedProp() && !member.getFirstChild().isQualifiedName()) {
      cannotConvert(member.getFirstChild(), "Computed property with non-qualified-name key");
      return;
    }

    JSTypeExpression typeExpr = getTypeFromGetterOrSetter(member);
    addToDefinePropertiesObject(metadata, member);

    Map<String, ClassProperty> membersToDeclare =
        member.isStaticMember()
            ? metadata.getClassMembersToDeclare()
            : metadata.getPrototypeMembersToDeclare();
    ClassProperty.Builder builder = ClassProperty.builder();
    String memberName;

    if (member.isComputedProp()) {
      checkState(!member.isStaticMember());
      memberName = member.getFirstChild().getQualifiedName();
      builder.kind(ClassProperty.PropertyKind.COMPUTED_PROPERTY);
    } else if (member.isQuotedString()) {
      memberName = member.getString();
      builder.kind(ClassProperty.PropertyKind.QUOTED_PROPERTY);
    } else {
      memberName = member.getString();
      builder.kind(ClassProperty.PropertyKind.NORMAL_PROPERTY);
    }

    builder.propertyKey(memberName);
    ClassProperty existingProperty = membersToDeclare.get(memberName);
    JSTypeExpression existingType =
        existingProperty == null ? null : existingProperty.jsDocInfo().getType();
    if (existingProperty != null && typeExpr != null && !existingType.equals(typeExpr)) {
      compiler.report(JSError.make(member, CONFLICTING_GETTER_SETTER_TYPE, memberName));
    } else {
      JSDocInfoBuilder jsDoc = new JSDocInfoBuilder(false);
      if (member.getJSDocInfo() != null && member.getJSDocInfo().isExport()) {
        jsDoc.recordExport();
        jsDoc.recordVisibility(Visibility.PUBLIC);
      }
      if (member.getJSDocInfo() != null && member.getJSDocInfo().isOverride()) {
        jsDoc.recordOverride();
      } else if (typeExpr == null) {
        typeExpr = new JSTypeExpression(new Node(Token.QMARK), member.getSourceFileName());
      }
      if (typeExpr != null) {
        jsDoc.recordType(typeExpr.copy());
      }
      if (member.isStaticMember() && !member.isComputedProp()) {
        jsDoc.recordNoCollapse();
      }
      builder.jsDocInfo(jsDoc.build());
      membersToDeclare.put(memberName, builder.build());
    }
  }

  /**
   * Handles transpilation of a standard class member function. Getters, setters, and the
   * constructor are not handled here.
   */
  private void visitMethod(Node member, ClassDeclarationMetadata metadata) {
    Node qualifiedMemberAccess = getQualifiedMemberAccess(member, metadata);
    Node method = member.getLastChild().detach();

    // Use the source info from the method (a FUNCTION) not the MEMBER_FUNCTION_DEF
    // because the MEMBER_FUNCTION_DEf source info only corresponds to the identifier
    Node assign =
        astFactory.createAssign(qualifiedMemberAccess, method).useSourceInfoIfMissingFrom(method);

    JSDocInfo info = member.getJSDocInfo();
    if (member.isStaticMember() && NodeUtil.referencesThis(assign.getLastChild())) {
      JSDocInfoBuilder memberDoc = JSDocInfoBuilder.maybeCopyFrom(info);
      memberDoc.recordThisType(
          new JSTypeExpression(new Node(Token.BANG, new Node(Token.QMARK)),
          member.getSourceFileName()));
      info = memberDoc.build();
    }
    if (info != null) {
      assign.setJSDocInfo(info);
    }

    Node newNode = NodeUtil.newExpr(assign);
    metadata.insertNodeAndAdvance(newNode);
  }

  /**
   * Add declarations for properties that were defined with a getter and/or setter, so that the
   * typechecker knows those properties exist on the class. This is a temporary solution.
   * Eventually, the type checker should understand Object.defineProperties calls directly.
   */
  private void addTypeDeclarations(
      Scope scope, ClassDeclarationMetadata metadata, Node insertionPoint) {
    for (ClassProperty property : metadata.getPrototypeMembersToDeclare().values()) {
      Node declaration =
          property.getDeclaration(astFactory, scope, metadata.getClassPrototypeNode().cloneTree());
      declaration.useSourceInfoIfMissingFromForTree(metadata.getClassNameNode());
      insertionPoint.getParent().addChildAfter(declaration, insertionPoint);
      insertionPoint = declaration;
    }
    for (ClassProperty property : metadata.getClassMembersToDeclare().values()) {
      Node declaration =
          property.getDeclaration(astFactory, scope, metadata.getFullClassNameNode().cloneTree());
      declaration.useSourceInfoIfMissingFromForTree(metadata.getClassNameNode());
      insertionPoint.getParent().addChildAfter(declaration, insertionPoint);
      insertionPoint = declaration;
    }
  }

  /**
   * Constructs a Node that represents an access to the given class member, qualified by either the
   * static or the instance access context, depending on whether the member is static.
   *
   * <p><b>WARNING:</b> {@code member} may be modified/destroyed by this method, do not use it
   * afterwards.
   */
  private Node getQualifiedMemberAccess(Node member, ClassDeclarationMetadata metadata) {
    Node context =
        member.isStaticMember()
            ? metadata.getFullClassNameNode().cloneTree()
            : metadata.getClassPrototypeNode().cloneTree();
    // context.useSourceInfoIfMissingFromForTree(member);
    context.makeNonIndexableRecursive();
    if (member.isComputedProp()) {
      return astFactory
          .createGetElem(context, member.removeFirstChild())
          .useSourceInfoIfMissingFromForTree(member);
    } else {
      Node methodName = member.getFirstFirstChild();
      return astFactory
          .createGetProp(context, member.getString())
          .useSourceInfoFromForTree(methodName);
    }
  }

  private class CheckClassAssignments extends NodeTraversal.AbstractPostOrderCallback {
    private final Node className;

    public CheckClassAssignments(Node className) {
      this.className = className;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!n.isAssign() || n.getFirstChild() == className) {
        return;
      }
      if (className.matchesQualifiedName(n.getFirstChild())) {
        compiler.report(JSError.make(n, CLASS_REASSIGNMENT));
      }
    }

  }

  private void cannotConvert(Node n, String message) {
    compiler.report(JSError.make(n, CANNOT_CONVERT, message));
  }

  /**
   * Warns the user that the given ES6 feature cannot be converted to ES3
   * because the transpilation is not yet implemented. A call to this method
   * is essentially a "TODO(tbreisacher): Implement {@code feature}" comment.
   */
  private void cannotConvertYet(Node n, String feature) {
    compiler.report(JSError.make(n, CANNOT_CONVERT_YET, feature));
  }

  private Node createPropertyDescriptor() {
    return IR.objectlit(
            astFactory.createStringKey("configurable", astFactory.createBoolean(true)),
            astFactory.createStringKey("enumerable", astFactory.createBoolean(true)))
        .setJSType(objectPropertyDescriptorType);
  }

  @AutoValue
  abstract static class ClassProperty {
    enum PropertyKind {
      /**
       * Any kind of quoted property, which can include numeric properties that we treated as
       * quoted.
       *
       * <pre>
       * class Example {
       *   'quoted'() {}
       *   42() { return 'the answer'; }
       * }
       * </pre>
       */
      QUOTED_PROPERTY,

      /**
       * A computed property, e.g. using bracket [] access.
       *
       * <p>Computed properties *must* currently be qualified names, and not literals, function
       * calls, etc.
       *
       * <pre>
       * class Example {
       *   [variable]() {}
       *   [another.example]() {}
       * }
       * </pre>
       */
      COMPUTED_PROPERTY,

      /**
       * A normal property definition.
       *
       * <pre>
       * class Example {
       *   normal() {}
       * }
       * </pre>
       */
      NORMAL_PROPERTY,
    }

    /**
     * The name of this ClassProperty for NORMAL_PROPERTY, the string value of this property if
     * QUOTED_PROPERTY, or the qualified name of the computed property.
     */
    abstract String propertyKey();

    abstract PropertyKind kind();

    abstract JSDocInfo jsDocInfo();

    /**
     * Returns an EXPR_RESULT node that declares this property on the given node.
     *
     * <p>Examples:
     *
     * <pre>
     *   /** @type {string} *\/
     *   Class.prototype.property;
     *
     *   /** @type {string} *\/
     *   Class.staticProperty;
     * </pre>
     *
     * @param toDeclareOn the node to declare the property on. This should either be a reference to
     *     the class (if a static property) or a class' prototype (if non-static). This should
     *     always be a new node, as this method will insert it into the returned EXPR_RESULT.
     */
    final Node getDeclaration(AstFactory astFactory, Scope scope, Node toDeclareOn) {
      Node decl = null;

      switch (kind()) {
        case QUOTED_PROPERTY:
          decl = astFactory.createGetElem(toDeclareOn, astFactory.createString(propertyKey()));
          break;
        case COMPUTED_PROPERTY:
          // Note that at the moment we only allow qualified names as the keys for class computed
          // properties.
          // TODO(bradfordcsmith): Clone the original declared member qualified name instead of
          // creating it from scratch from the string form. That way we would just reuse the type
          // information, instead of AstFactory having to re-create it.
          decl =
              astFactory.createGetElem(toDeclareOn, astFactory.createQName(scope, propertyKey()));
          break;
        case NORMAL_PROPERTY:
          decl = astFactory.createGetProp(toDeclareOn, propertyKey());
          break;
      }

      decl.setJSDocInfo(jsDocInfo());
      decl = astFactory.exprResult(decl);
      return decl;
    }

    static Builder builder() {
      return new AutoValue_Es6RewriteClass_ClassProperty.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder propertyKey(String value);

      abstract Builder kind(PropertyKind value);

      abstract Builder jsDocInfo(JSDocInfo value);

      abstract ClassProperty build();
    }
  }

  /**
   * Represents static metadata on a class declaration expression - i.e. the qualified name that a
   * class declares (directly or by assignment), whether it's anonymous, and where transpiled code
   * should be inserted (i.e. which object will hold the prototype after transpilation).
   *
   * <p>Note that this class is NOT deeply immutable! Don't use it in a Map. The AutoValue(.Builder)
   * is just used to simplify creating instances.
   */
  @AutoValue
  abstract static class ClassDeclarationMetadata {
    /** A statement node. Transpiled methods etc of the class are inserted after this node. */
    abstract InsertionPoint getInsertionPoint();

    /**
     * An object literal node that will be used in a call to Object.defineProperties, to add getters
     * and setters to the prototype.
     */
    abstract Node getDefinePropertiesObjForPrototype();

    /**
     * An object literal node that will be used in a call to Object.defineProperties, to add getters
     * and setters to the class.
     */
    abstract Node getDefinePropertiesObjForClass();

    // Property declarations to be added to the prototype
    abstract Map<String, ClassProperty> getPrototypeMembersToDeclare();

    // Property declarations to be added to the class
    abstract Map<String, ClassProperty> getClassMembersToDeclare();

    /**
     * The fully qualified name of the class, as a cloneable node. May come from the class itself or
     * the LHS of an assignment.
     */
    abstract Node getFullClassNameNode();

    /**
     * The fully qualified name of this class, plus ".prototype", as a cloneable node with type
     * information as needed.
     */
    abstract Node getClassPrototypeNode();

    /** Whether the constructor function in the output should be anonymous. */
    abstract boolean isAnonymous();

    abstract Node getClassNameNode();

    abstract Node getSuperClassNameNode();

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setInsertionPoint(InsertionPoint insertionPoint);

      abstract Builder setFullClassNameNode(Node fullClassNameNode);

      abstract Node getFullClassNameNode();

      abstract Builder setPrototypeMembersToDeclare(
          Map<String, ClassProperty> prototypeMembersToDeclare);

      abstract Builder setClassMembersToDeclare(Map<String, ClassProperty> classMembersToDeclare);

      abstract Builder setAnonymous(boolean anonymous);

      abstract Builder setClassNameNode(Node classNameNode);

      abstract Builder setSuperClassNameNode(Node superClassNameNode);

      abstract Builder setClassPrototypeNode(Node node);

      abstract Builder setDefinePropertiesObjForClass(Node node);

      abstract Builder setDefinePropertiesObjForPrototype(Node node);

      abstract ClassDeclarationMetadata build();
    }

    public static Builder builder() {
      return new AutoValue_Es6RewriteClass_ClassDeclarationMetadata.Builder()
          .setPrototypeMembersToDeclare(new LinkedHashMap<>())
          .setClassMembersToDeclare(new LinkedHashMap<>());
    }

    /**
     * Creates an instance for a class statement or a class expression in a simple assignment or var
     * statement with a qualified name. In any other case, returns null.
     */
    @Nullable
    static ClassDeclarationMetadata create(Node classNode, Node parent) {
      return create(classNode, parent, AstFactory.createFactoryWithoutTypes());
    }

    private static ClassDeclarationMetadata create(
        Node classNode, Node parent, AstFactory astFactory) {
      Node classNameNode = classNode.getFirstChild();
      Node superClassNameNode = classNameNode.getNext();
      Builder builder =
          ClassDeclarationMetadata.builder()
              .setSuperClassNameNode(superClassNameNode)
              .setClassNameNode(classNameNode);

      // If this is a class statement, or a class expression in a simple
      // assignment or var statement, convert it. In any other case, the
      // code is too dynamic, so return null.
      if (NodeUtil.isClassDeclaration(classNode)) {
        builder
            .setInsertionPoint(InsertionPoint.from(classNode))
            .setFullClassNameNode(classNameNode)
            .setAnonymous(false);
      } else if (parent.isAssign() && parent.getParent().isExprResult()) {
        // Add members after the EXPR_RESULT node:
        // example.C = class {}; example.C.prototype.foo = function() {};
        Node fullClassNameNode = parent.getFirstChild();
        if (!fullClassNameNode.isQualifiedName()) {
          return null;
        }
        builder
            .setInsertionPoint(InsertionPoint.from(parent.getParent()))
            .setFullClassNameNode(fullClassNameNode)
            .setAnonymous(true);
      } else if (parent.isExport()) {
        builder
            .setInsertionPoint(InsertionPoint.from(classNode))
            .setFullClassNameNode(classNameNode)
            .setAnonymous(false);
      } else if (parent.isName()) {
        // Add members after the 'var' statement.
        // var C = class {}; C.prototype.foo = function() {};
        builder
            .setInsertionPoint(InsertionPoint.from(parent.getParent()))
            .setFullClassNameNode(parent.cloneNode()) // specifically don't want children
            .setAnonymous(true);
      } else {
        // Cannot handle this class declaration.
        return null;
      }

      // TODO(sdh): are these types safe?
      JSType classType = builder.getFullClassNameNode().getJSType();
      builder.setClassPrototypeNode(
          astFactory.createGetProp(builder.getFullClassNameNode().cloneTree(), "prototype"));
      builder.setDefinePropertiesObjForClass(IR.objectlit().setJSType(classType));
      builder.setDefinePropertiesObjForPrototype(IR.objectlit().setJSType(classType));
      return builder.build();
    }

    void insertNodeAndAdvance(Node newNode) {
      getInsertionPoint().insertNodeAndAdvance(newNode);
    }

    boolean hasSuperClass() {
      return !getSuperClassNameNode().isEmpty();
    }
  }

  /**
   * Used by ClassDeclarationMetadata to represent the point where we insert the next transpiled
   * method of a class
   */
  static class InsertionPoint {
    private Node insertionPoint;

    private InsertionPoint(Node insertionPoint) {
      this.insertionPoint = insertionPoint;
    }

    void insertNodeAndAdvance(Node newNode) {
      insertionPoint.getParent().addChildAfter(newNode, insertionPoint);
      insertionPoint = newNode;
    }

    static InsertionPoint from(Node start) {
      return new InsertionPoint(start);
    }

    Node getNode() {
      return insertionPoint;
    }
  }
}
