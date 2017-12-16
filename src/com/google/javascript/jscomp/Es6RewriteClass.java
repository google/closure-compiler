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

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Converts ES6 classes to valid ES5 or ES3 code.
 */
public final class Es6RewriteClass implements NodeTraversal.Callback, HotSwapCompilerPass {
  private final AbstractCompiler compiler;
  private static final FeatureSet features = FeatureSet.BARE_MINIMUM.with(Feature.CLASSES);

  // Whether to add $jscomp.inherits(Parent, Child) for each subclass.
  private final boolean shouldAddInheritsPolyfill;

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

  public Es6RewriteClass(AbstractCompiler compiler) {
    this(compiler, true);
  }

  public Es6RewriteClass(AbstractCompiler compiler, boolean shouldAddInheritsPolyfill) {
    this.compiler = compiler;
    this.shouldAddInheritsPolyfill = shouldAddInheritsPolyfill;
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, externs, features, this);
    TranspilationPasses.processTranspile(compiler, root, features, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(compiler, scriptRoot, features, this);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case GETTER_DEF:
      case SETTER_DEF:
        if (compiler.getOptions().getLanguageOut() == LanguageMode.ECMASCRIPT3) {
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
    NodeTraversal.traverseEs6(compiler, enclosingFunction, checkAssigns);
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
    ClassDeclarationMetadata metadata = ClassDeclarationMetadata.create(classNode, parent);

    if (metadata == null || metadata.fullClassName == null) {
      throw new IllegalStateException(
          "Can only convert classes that are declarations or the right hand"
          + " side of a simple assignment: " + classNode);
    }
    if (metadata.hasSuperClass() && !metadata.superClassNameNode.isQualifiedName()) {
      compiler.report(JSError.make(metadata.superClassNameNode, DYNAMIC_EXTENDS_TYPE));
      return;
    }

    Preconditions.checkState(NodeUtil.isStatement(metadata.insertionPoint),
        "insertion point must be a statement: %s", metadata.insertionPoint);

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
        constructor = member.getFirstChild().detach();
        if (!metadata.anonymous) {
          // Turns class Foo { constructor: function() {} } into function Foo() {},
          // i.e. attaches the name to the ctor function.
          constructor.replaceChild(
              constructor.getFirstChild(), metadata.classNameNode.cloneNode());
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

    if (metadata.definePropertiesObjForPrototype.hasChildren()) {
      compiler.ensureLibraryInjected("util/global", false);
      Node definePropsCall =
          IR.exprResult(
              IR.call(
                  NodeUtil.newQName(compiler, "$jscomp.global.Object.defineProperties"),
                  NodeUtil.newQName(compiler, metadata.fullClassName + ".prototype"),
                  metadata.definePropertiesObjForPrototype));
      definePropsCall.useSourceInfoIfMissingFromForTree(classNode);
      metadata.insertNodeAndAdvance(definePropsCall);
    }

    if (metadata.definePropertiesObjForClass.hasChildren()) {
      compiler.ensureLibraryInjected("util/global", false);
      Node definePropsCall =
          IR.exprResult(
              IR.call(
                  NodeUtil.newQName(compiler, "$jscomp.global.Object.defineProperties"),
                  NodeUtil.newQName(compiler, metadata.fullClassName),
                  metadata.definePropertiesObjForClass));
      definePropsCall.useSourceInfoIfMissingFromForTree(classNode);
      metadata.insertNodeAndAdvance(definePropsCall);
    }

    checkNotNull(constructor);

    JSDocInfo classJSDoc = NodeUtil.getBestJSDocInfo(classNode);
    JSDocInfoBuilder newInfo = JSDocInfoBuilder.maybeCopyFrom(classJSDoc);

    newInfo.recordConstructor();

    Node enclosingStatement = NodeUtil.getEnclosingStatement(classNode);
    if (metadata.hasSuperClass()) {
      String superClassString = metadata.superClassNameNode.getQualifiedName();
      if (newInfo.isInterfaceRecorded()) {
        newInfo.recordExtendedInterface(new JSTypeExpression(new Node(Token.BANG,
            IR.string(superClassString)),
            metadata.superClassNameNode.getSourceFileName()));
      } else {
        if (shouldAddInheritsPolyfill && !classNode.isFromExterns()) {
          Node classNameNode = NodeUtil.newQName(compiler, metadata.fullClassName)
              .useSourceInfoIfMissingFrom(metadata.classNameNode);
          Node superClassNameNode = metadata.superClassNameNode.cloneTree();

          Node inherits = IR.call(
              NodeUtil.newQName(compiler, INHERITS), classNameNode, superClassNameNode);
          Node inheritsCall = IR.exprResult(inherits);
          compiler.ensureLibraryInjected("es6/util/inherits", false);

          inheritsCall.useSourceInfoIfMissingFromForTree(metadata.superClassNameNode);
          enclosingStatement.getParent().addChildAfter(inheritsCall, enclosingStatement);
        }
        newInfo.recordBaseType(new JSTypeExpression(new Node(Token.BANG,
            IR.string(superClassString)),
            metadata.superClassNameNode.getSourceFileName()));
      }
    }

    addTypeDeclarations(metadata, enclosingStatement);

    updateClassJsDoc(ctorJSDocInfo, newInfo);

    if (NodeUtil.isStatement(classNode)) {
      constructor.getFirstChild().setString("");
      Node ctorVar = IR.let(metadata.classNameNode.cloneNode(), constructor);
      ctorVar.useSourceInfoIfMissingFromForTree(classNode);
      parent.replaceChild(classNode, ctorVar);
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

    constructor.putBooleanProp(Node.IS_ES6_CLASS, true);
    t.reportCodeChange();
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
            ? metadata.definePropertiesObjForClass
            : metadata.definePropertiesObjForPrototype;
    Node prop =
        member.isComputedProp()
            ? NodeUtil.getFirstComputedPropMatchingKey(obj, member.getFirstChild())
            : NodeUtil.getFirstPropMatchingKey(obj, member.getString());
    if (prop == null) {
      prop =
          IR.objectlit(
              IR.stringKey("configurable", IR.trueNode()),
              IR.stringKey("enumerable", IR.trueNode()));
      if (member.isComputedProp()) {
        obj.addChildToBack(IR.computedProp(member.getFirstChild().cloneTree(), prop));
      } else {
        obj.addChildToBack(IR.stringKey(member.getString(), prop));
      }
    }

    Node function = member.getLastChild();
    JSDocInfoBuilder info = JSDocInfoBuilder.maybeCopyFrom(
        NodeUtil.getBestJSDocInfo(function));

    info.recordThisType(new JSTypeExpression(new Node(
        Token.BANG, IR.string(metadata.fullClassName)), member.getSourceFileName()));
    Node stringKey =
        IR.stringKey(
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

    Map<String, JSDocInfo> membersToDeclare;
    String memberName;
    if (member.isComputedProp()) {
      checkState(!member.isStaticMember());
      membersToDeclare = metadata.prototypeComputedPropsToDeclare;
      memberName = member.getFirstChild().getQualifiedName();
    } else {
      membersToDeclare =
          member.isStaticMember()
              ? metadata.classMembersToDeclare
              : metadata.prototypeMembersToDeclare;
      memberName = member.getString();
    }
    JSDocInfo existingJSDoc = membersToDeclare.get(memberName);
    JSTypeExpression existingType = existingJSDoc == null ? null : existingJSDoc.getType();
    if (existingType != null && typeExpr != null && !existingType.equals(typeExpr)) {
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
      membersToDeclare.put(memberName, jsDoc.build());
    }
  }

  /**
   * Handles transpilation of a standard class member function. Getters, setters, and the
   * constructor are not handled here.
   */
  private void visitMethod(Node member, ClassDeclarationMetadata metadata) {
    Node qualifiedMemberAccess = getQualifiedMemberAccess(
        member,
        NodeUtil.newQName(compiler, metadata.fullClassName),
        NodeUtil.newQName(compiler, metadata.fullClassName + ".prototype"));
    Node method = member.getLastChild().detach();

    Node assign = IR.assign(qualifiedMemberAccess, method);
    assign.useSourceInfoIfMissingFromForTree(member);

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
   * Add declarations for properties that were defined with a getter and/or setter,
   * so that the typechecker knows those properties exist on the class.
   * This is a temporary solution. Eventually, the type checker should understand
   * Object.defineProperties calls directly.
   */
  private void addTypeDeclarations(ClassDeclarationMetadata metadata, Node insertionPoint) {
    for (Map.Entry<String, JSDocInfo> entry : metadata.prototypeMembersToDeclare.entrySet()) {
      String declaredMember = entry.getKey();
      Node declaration = IR.getprop(
          NodeUtil.newQName(compiler, metadata.fullClassName + ".prototype"),
          IR.string(declaredMember));
      declaration.setJSDocInfo(entry.getValue());
      declaration =
          IR.exprResult(declaration).useSourceInfoIfMissingFromForTree(metadata.classNameNode);
      insertionPoint.getParent().addChildAfter(declaration, insertionPoint);
      insertionPoint = declaration;
    }
    for (Map.Entry<String, JSDocInfo> entry : metadata.classMembersToDeclare.entrySet()) {
      String declaredMember = entry.getKey();
      Node declaration = IR.getprop(
          NodeUtil.newQName(compiler, metadata.fullClassName),
          IR.string(declaredMember));
      declaration.setJSDocInfo(entry.getValue());
      declaration =
          IR.exprResult(declaration).useSourceInfoIfMissingFromForTree(metadata.classNameNode);
      insertionPoint.getParent().addChildAfter(declaration, insertionPoint);
      insertionPoint = declaration;
    }
    for (Map.Entry<String, JSDocInfo> entry : metadata.prototypeComputedPropsToDeclare.entrySet()) {
      String declaredMember = entry.getKey();
      Node declaration = IR.getelem(
          NodeUtil.newQName(compiler, metadata.fullClassName + ".prototype"),
          NodeUtil.newQName(compiler, declaredMember));
      declaration.setJSDocInfo(entry.getValue());
      declaration =
          IR.exprResult(declaration).useSourceInfoIfMissingFromForTree(metadata.classNameNode);
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
  private static Node getQualifiedMemberAccess(Node member,
      Node staticAccess, Node instanceAccess) {
    Node context = member.isStaticMember() ? staticAccess : instanceAccess;
    context = context.cloneTree();
    context.makeNonIndexableRecursive();
    if (member.isComputedProp()) {
      return IR.getelem(context, member.removeFirstChild());
    } else {
      Node methodName = member.getFirstFirstChild();
      return IR.getprop(context, IR.string(member.getString()))
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

  /**
   * Represents static metadata on a class declaration expression - i.e. the qualified name that a
   * class declares (directly or by assignment), whether it's anonymous, and where transpiled code
   * should be inserted (i.e. which object will hold the prototype after transpilation).
   */
  static class ClassDeclarationMetadata {
    /** A statement node. Transpiled methods etc of the class are inserted after this node. */
    private Node insertionPoint;

    /**
     * An object literal node that will be used in a call to Object.defineProperties, to add getters
     * and setters to the prototype.
     */
    private final Node definePropertiesObjForPrototype;

    /**
     * An object literal node that will be used in a call to Object.defineProperties, to add getters
     * and setters to the class.
     */
    private final Node definePropertiesObjForClass;

    // Normal declarations to be added to the prototype: Foo.prototype.bar
    private final Map<String, JSDocInfo> prototypeMembersToDeclare;

    // Computed property declarations to be added to the prototype: Foo.prototype[bar]
    private final Map<String, JSDocInfo> prototypeComputedPropsToDeclare;

    // Normal declarations to be added to the class: Foo.bar
    private final Map<String, JSDocInfo> classMembersToDeclare;

    /**
     * The fully qualified name of the class, which will be used in the output. May come from the
     * class itself or the LHS of an assignment.
     */
    final String fullClassName;
    /** Whether the constructor function in the output should be anonymous. */
    final boolean anonymous;
    final Node classNameNode;
    final Node superClassNameNode;

    private ClassDeclarationMetadata(Node insertionPoint, String fullClassName,
        boolean anonymous, Node classNameNode, Node superClassNameNode) {
      this.insertionPoint = insertionPoint;
      this.definePropertiesObjForClass = IR.objectlit();
      this.definePropertiesObjForPrototype = IR.objectlit();
      this.prototypeMembersToDeclare = new LinkedHashMap<>();
      this.prototypeComputedPropsToDeclare = new LinkedHashMap<>();
      this.classMembersToDeclare = new LinkedHashMap<>();
      this.fullClassName = fullClassName;
      this.anonymous = anonymous;
      this.classNameNode = classNameNode;
      this.superClassNameNode = superClassNameNode;
    }

    static ClassDeclarationMetadata create(Node classNode, Node parent) {
      Node classNameNode = classNode.getFirstChild();
      Node superClassNameNode = classNameNode.getNext();

      // If this is a class statement, or a class expression in a simple
      // assignment or var statement, convert it. In any other case, the
      // code is too dynamic, so return null.
      if (NodeUtil.isClassDeclaration(classNode)) {
        return new ClassDeclarationMetadata(classNode, classNameNode.getString(), false,
            classNameNode, superClassNameNode);
      } else if (parent.isAssign() && parent.getParent().isExprResult()) {
        // Add members after the EXPR_RESULT node:
        // example.C = class {}; example.C.prototype.foo = function() {};
        String fullClassName = parent.getFirstChild().getQualifiedName();
        if (fullClassName == null) {
          return null;
        }
        return new ClassDeclarationMetadata(parent.getParent(), fullClassName, true, classNameNode,
            superClassNameNode);
      } else if (parent.isExport()) {
        return new ClassDeclarationMetadata(
            classNode, classNameNode.getString(), false, classNameNode, superClassNameNode);
      } else if (parent.isName()) {
        // Add members after the 'var' statement.
        // var C = class {}; C.prototype.foo = function() {};
        return new ClassDeclarationMetadata(parent.getParent(), parent.getString(), true,
            classNameNode, superClassNameNode);
      } else {
        // Cannot handle this class declaration.
        return null;
      }
    }

    void insertNodeAndAdvance(Node newNode) {
      insertionPoint.getParent().addChildAfter(newNode, insertionPoint);
      insertionPoint = newNode;
    }

    boolean hasSuperClass() {
      return !superClassNameNode.isEmpty();
    }
  }
}
