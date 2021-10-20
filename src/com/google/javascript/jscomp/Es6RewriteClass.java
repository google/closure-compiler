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
import static com.google.javascript.jscomp.AstFactory.type;
import static com.google.javascript.jscomp.Es6ToEs3Util.cannotConvert;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticScope;
import com.google.javascript.rhino.Token;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

/** Converts ES6 classes to valid ES5 or ES3 code. */
public final class Es6RewriteClass implements NodeTraversal.Callback, CompilerPass {
  private static final FeatureSet features =
      FeatureSet.BARE_MINIMUM.with(
          Feature.CLASSES,
          Feature.CLASS_EXTENDS,
          Feature.CLASS_GETTER_SETTER,
          Feature.NEW_TARGET,
          Feature.SUPER);

  // This function is defined in js/es6/util/inherits.js
  static final String INHERITS = "$jscomp.inherits";

  private final AbstractCompiler compiler;
  private final AstFactory astFactory;
  private final Es6ConvertSuperConstructorCalls convertSuperConstructorCalls;
  private final StaticScope transpilationNamespace;

  public Es6RewriteClass(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
    this.transpilationNamespace = compiler.getTranspilationNamespace();
    this.convertSuperConstructorCalls = new Es6ConvertSuperConstructorCalls(compiler);
  }

  @Override
  public void process(Node externs, Node root) {
    // TODO(b/171853310): This tranpilation should be turned off in externs
    TranspilationPasses.processTranspile(compiler, externs, features, this);
    TranspilationPasses.processTranspile(compiler, root, features, this);
    // Super constructor calls are done all at once as a separate step largely for historical
    // reasons. It used to be an entirely separate pass, but that has been fixed so we no longer
    // have an invalid AST state between passes.
    // TODO(bradfordcsmith): It would probably be more readable and efficient to merge the super
    //     constructor rewriting logic into this class.
    convertSuperConstructorCalls.setGlobalNamespace(new GlobalNamespace(compiler, externs, root));
    TranspilationPasses.processTranspile(compiler, root, features, convertSuperConstructorCalls);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, features);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case GETTER_DEF:
      case SETTER_DEF:
        if (FeatureSet.ES3.contains(compiler.getOptions().getOutputFeatureSet())) {
          cannotConvert(compiler, n, "ES5 getters/setters (consider using --language_out=ES5)");
          return false;
        }
        break;
      default:
        break;
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isClass()) {
      visitClass(t, n, parent);
    }
  }

  /**
   * Classes are processed in 3 phases:
   *
   * <ol>
   *   <li>The class name is extracted.
   *   <li>Class members are processed and rewritten.
   *   <li>The constructor is built.
   * </ol>
   */
  private void visitClass(final NodeTraversal t, final Node classNode, final Node parent) {
    // Collect Metadata
    ClassDeclarationMetadata metadata =
        ClassDeclarationMetadata.create(classNode, parent, astFactory);

    if (metadata == null) {
      throw new IllegalStateException(
          "Can only convert classes that are declarations or the right hand"
              + " side of a simple assignment: "
              + classNode);
    }
    if (metadata.hasSuperClass()) {
      checkState(
          metadata.getSuperClassNameNode().isQualifiedName(),
          "Expected Es6RewriteClassExtendsExpressions to make all extends clauses into qualified"
              + " names, found %s",
          metadata.getSuperClassNameNode());
    }

    Preconditions.checkState(
        NodeUtil.isStatement(metadata.getInsertionPoint().getNode()),
        "insertion point must be a statement: %s",
        metadata.getInsertionPoint().getNode());

    Node constructor = null;
    // Process all members of the class
    Node classMembers = classNode.getLastChild();
    for (Node member = classMembers.getFirstChild(); member != null; ) {
      final Node next = member.getNext();
      if ((member.isComputedProp()
              && (member.getBooleanProp(Node.COMPUTED_PROP_GETTER)
                  || member.getBooleanProp(Node.COMPUTED_PROP_SETTER)))
          || (member.isGetterDef() || member.isSetterDef())) {
        visitNonMethodMember(member, metadata);
      } else if (NodeUtil.isEs6ConstructorMemberFunctionDef(member)) {
        constructor = member.removeFirstChild().setColor(classNode.getColor());
        if (!metadata.isAnonymous()) {
          // Turns class Foo { constructor: function() {} } into function Foo() {},
          // i.e. attaches the name to the ctor function.
          constructor.getFirstChild().replaceWith(metadata.getClassNameNode().cloneNode());
        }
      } else if (member.isEmpty()) {
        // Do nothing.
      } else {
        Preconditions.checkState(
            member.isMemberFunctionDef() || member.isComputedProp(),
            "Unexpected class member:",
            member);
        Preconditions.checkState(
            !member.getBooleanProp(Node.COMPUTED_PROP_VARIABLE),
            "Member variables should have been transpiled earlier:",
            member);
        visitMethod(member, metadata);
      }
      member = next;
    }
    checkNotNull(
        constructor,
        "Es6RewriteClasses expects all classes to have (possibly synthetic) constructors");

    if (metadata.getDefinePropertiesObjForPrototype().hasChildren()) {
      Node definePropsCall =
          IR.exprResult(
              astFactory.createCall(
                  createObjectDotDefineProperties(),
                  type(metadata.getClassPrototypeNode()),
                  metadata.getClassPrototypeNode().cloneTree(),
                  metadata.getDefinePropertiesObjForPrototype()));
      definePropsCall.srcrefTreeIfMissing(classNode);
      metadata.insertNodeAndAdvance(definePropsCall);
    }

    if (metadata.getDefinePropertiesObjForClass().hasChildren()) {
      Node definePropsCall =
          IR.exprResult(
              astFactory.createCall(
                  createObjectDotDefineProperties(),
                  type(metadata.getFullClassNameNode()),
                  metadata.getFullClassNameNode().cloneTree(),
                  metadata.getDefinePropertiesObjForClass()));
      definePropsCall.srcrefTreeIfMissing(classNode);
      metadata.insertNodeAndAdvance(definePropsCall);
    }

    JSDocInfo classJSDoc = NodeUtil.getBestJSDocInfo(classNode);
    JSDocInfo.Builder newInfo = JSDocInfo.Builder.maybeCopyFrom(classJSDoc);
    newInfo.recordConstructor();

    Node enclosingStatement = NodeUtil.getEnclosingStatement(classNode);
    if (metadata.hasSuperClass()) {
      if (!classNode.isFromExterns()) {
        Node inheritsCall =
            IR.exprResult(
                    astFactory.createCall(
                        astFactory.createQName(this.transpilationNamespace, INHERITS),
                        type(StandardColors.NULL_OR_VOID),
                        metadata.getFullClassNameNode().cloneTree(),
                        metadata.getSuperClassNameNode().cloneTree()))
                .srcrefTreeIfMissing(metadata.getSuperClassNameNode());
        inheritsCall.insertAfter(enclosingStatement);
      }
    }

    addTypeDeclarations(metadata, enclosingStatement);

    if (NodeUtil.isStatement(classNode)) {
      constructor.getFirstChild().setString("");
      Node ctorVar = IR.let(metadata.getClassNameNode().cloneNode(), constructor);
      ctorVar.srcrefTreeIfMissing(classNode);
      classNode.replaceWith(ctorVar);
      NodeUtil.addFeatureToScript(t.getCurrentScript(), Feature.LET_DECLARATIONS, compiler);
    } else {
      classNode.replaceWith(constructor);
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

    t.reportCodeChange();
  }

  private Node createObjectDotDefineProperties() {
    return astFactory.createJSCompDotGlobalAccess(
        this.transpilationNamespace, "Object.defineProperties");
  }

  private Node createObjectDotDefineProperty() {
    return astFactory.createJSCompDotGlobalAccess(
        this.transpilationNamespace, "Object.defineProperty");
  }

  /** @param member A getter or setter */
  private void addToDefinePropertiesObject(ClassDeclarationMetadata metadata, Node member) {
    Preconditions.checkArgument(!member.isComputedProp());
    Node obj =
        member.isStaticMember()
            ? metadata.getDefinePropertiesObjForClass()
            : metadata.getDefinePropertiesObjForPrototype();
    Node prop = NodeUtil.getFirstPropMatchingKey(obj, member.getString());
    if (prop == null) {
      prop = createPropertyDescriptor();
      Node stringKey = astFactory.createStringKey(member.getString(), prop);
      if (member.isQuotedString()) {
        stringKey.putBooleanProp(Node.QUOTED_PROP, true);
      }
      obj.addChildToBack(stringKey);
    }

    Node function = member.getLastChild();
    JSDocInfo info = NodeUtil.getBestJSDocInfo(function);

    Node stringKey =
        astFactory.createStringKey(member.isGetterDef() ? "get" : "set", function.detach());
    stringKey.setJSDocInfo(info);
    prop.addChildToBack(stringKey);
    prop.srcrefTreeIfMissing(member);
  }

  /** Appends an Object.defineProperty call defining the given computed getter or setter */
  private void extractComputedProperty(Node computedMember, ClassDeclarationMetadata metadata) {
    Node owner =
        computedMember.isStaticMember()
            ? metadata.getFullClassNameNode()
            : metadata.getClassPrototypeNode();
    Node property = computedMember.removeFirstChild();
    Node propertyValue = computedMember.removeFirstChild();

    Node propertyDescriptor = createPropertyDescriptor();
    Node stringKey =
        astFactory.createStringKey(
            computedMember.getBooleanProp(Node.COMPUTED_PROP_GETTER) ? "get" : "set",
            propertyValue);
    propertyDescriptor.addChildToBack(stringKey);

    Node objectDefinePropertyCall =
        astFactory.createCall(
            createObjectDotDefineProperty(),
            type(owner),
            owner.cloneTree(),
            property,
            propertyDescriptor);

    metadata.insertNodeAndAdvance(
        IR.exprResult(objectDefinePropertyCall).srcrefTreeIfMissing(computedMember));
  }

  /**
   * Visits getters and setters, including both static and instance properties, and computed and
   * non-computed properties.
   *
   * <p>Non-computed getters and setters are aggregated into two Object.defineProperties calls. One
   * defines static members and the other instance members. This is just an optimization; it's just
   * as sound to append a single Object.defineProperty definition for each here.
   *
   * <p>Computed getters and setters are defined in individual Object.defineProperty calls
   */
  private void visitNonMethodMember(Node member, ClassDeclarationMetadata metadata) {
    if (member.isComputedProp()) {
      extractComputedProperty(member.detach(), metadata);
      return;
    }

    addToDefinePropertiesObject(metadata, member);

    if (!member.isStaticMember()) {
      return;
    }
    checkState(!member.isComputedProp(), member);
    // Add stub declarations of static properties so that they are not broken by property collapsing

    Map<String, ClassProperty> membersToDeclare = metadata.getClassMembersToDeclare();
    ClassProperty.Builder builder = ClassProperty.builder();
    String memberName = member.getString();

    if (member.isQuotedString()) {
      builder.kind(ClassProperty.PropertyKind.QUOTED_PROPERTY);
    } else {
      builder.kind(ClassProperty.PropertyKind.NORMAL_PROPERTY);
    }

    builder.propertyKey(memberName).propertyType(member.getColor());

    JSDocInfo.Builder jsDoc = JSDocInfo.builder();
    jsDoc.recordNoCollapse();
    builder.jsDocInfo(jsDoc.build());
    membersToDeclare.put(memberName, builder.build());
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
    Node assign = astFactory.createAssign(qualifiedMemberAccess, method).srcrefIfMissing(method);

    JSDocInfo info = member.getJSDocInfo();
    if (member.isStaticMember() && NodeUtil.referencesOwnReceiver(assign.getLastChild())) {
      JSDocInfo.Builder memberDoc = JSDocInfo.Builder.maybeCopyFrom(info);
      // adding an @this type prevents a JSC_UNSAFE_THIS error later on.
      memberDoc.recordThisType(
          new JSTypeExpression(
              new Node(Token.BANG, new Node(Token.QMARK)).srcrefTree(member),
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
   * Adds declarations for static properties defined with a getter or setter, so that optimizations
   * like property collapsing recognize the properties' existence.
   */
  private void addTypeDeclarations(ClassDeclarationMetadata metadata, Node insertionPoint) {
    for (ClassProperty property : metadata.getClassMembersToDeclare().values()) {
      Node declaration =
          property.getDeclaration(astFactory, metadata.getFullClassNameNode().cloneTree());
      declaration.srcrefTreeIfMissing(metadata.getClassNameNode());
      declaration.insertAfter(insertionPoint);
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

    context.makeNonIndexableRecursive();
    if (member.isComputedProp()) {
      return astFactory
          .createGetElem(context, member.removeFirstChild())
          .srcrefTreeIfMissing(member);
    } else {
      Node methodName = member.getFirstFirstChild();
      return astFactory
          .createGetProp(context, member.getString(), type(member))
          .srcrefTree(methodName);
    }
  }

  private Node createPropertyDescriptor() {
    return astFactory.createObjectLit(
        astFactory.createStringKey("configurable", astFactory.createBoolean(true)),
        astFactory.createStringKey("enumerable", astFactory.createBoolean(true)));
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

    @Nullable
    abstract Color propertyType();

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
    final Node getDeclaration(AstFactory astFactory, Node toDeclareOn) {
      Node decl = null;

      switch (kind()) {
        case QUOTED_PROPERTY:
          decl = astFactory.createGetElem(toDeclareOn, astFactory.createString(propertyKey()));
          break;
        case COMPUTED_PROPERTY:
          // No need to declare computed properties as they're unaffected by property collapsing
          throw new UnsupportedOperationException(this.toString());
        case NORMAL_PROPERTY:
          decl = astFactory.createGetProp(toDeclareOn, propertyKey(), type(propertyType()));
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

      abstract Builder propertyType(@Nullable Color type);

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
      AstFactory.Type classType = type(builder.getFullClassNameNode());
      builder.setClassPrototypeNode(
          astFactory.createPrototypeAccess(builder.getFullClassNameNode().cloneTree()));
      builder.setDefinePropertiesObjForClass(astFactory.createObjectLit(classType));
      builder.setDefinePropertiesObjForPrototype(astFactory.createObjectLit(classType));
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
      newNode.insertAfter(insertionPoint);
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
