/*
 * Copyright 2025 The Closure Compiler Authors.
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
import static com.google.javascript.jscomp.AstFactory.type;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.ExpressionDecomposer.DecompositionType;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSTypeNative;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Normalizes ES6 classes to a simpler form for easier optimization.
 *
 * <p>This pass handles four main transformations which are performed when needed:
 *
 * <ol>
 *   <li>Extracts class extends expressions into variables.
 *   <li>Extracts classes into variables.
 *   <li>Rewrites static class field initializers and static blocks into a static initialization
 *       method that is called after class definition.
 *   <li>If transpilation of {@link Feature#PUBLIC_CLASS_FIELDS} is needed, rewrites public class
 *       instance fields into assignments in the constructor and removes static field declarations.
 * </ol>
 *
 * <p>These transformations must be done before passes like {@link Es6RewriteClass} and {@link
 * Es6ConvertSuper}, which only handle classes in simpler forms.
 */
public final class Es6NormalizeClasses implements NodeTraversal.ScopedCallback, CompilerPass {
  private static final String CLASS_DECL_VAR = "$jscomp$classDecl$";
  private static final String CLASS_EXTENDS_VAR = "$jscomp$classExtends$";
  private static final String COMP_FIELD_VAR = "$jscomp$compField$";
  private static final String STATIC_INIT_METHOD_NAME = "$jscomp$staticInit$";

  @VisibleForTesting
  static final ImmutableMap<String, String> GENERIC_NAME_REPLACEMENTS =
      ImmutableMap.<String, String>builder()
          .put("CLASS_DECL", CLASS_DECL_VAR)
          .put("CLASS_EXTENDS", CLASS_EXTENDS_VAR)
          .put("COMP_FIELD", COMP_FIELD_VAR)
          .put("STATIC_INIT", STATIC_INIT_METHOD_NAME)
          .buildOrThrow();

  /** Returns $jscomp$classDecl$[FILE_ID]$[number] */
  private String generateUniqueClassDeclVarName(NodeTraversal t) {
    return CLASS_DECL_VAR + compiler.getUniqueIdSupplier().getUniqueId(t.getInput());
  }

  /** Returns $jscomp$classExtends$[FILE_ID]$[number] */
  private String generateUniqueClassExtendsVarName(NodeTraversal t) {
    return CLASS_EXTENDS_VAR + compiler.getUniqueIdSupplier().getUniqueId(t.getInput());
  }

  /** Returns $jscomp$compfield$[FILE_ID]$[number] */
  private String generateUniqueCompFieldVarName(NodeTraversal t) {
    return COMP_FIELD_VAR + compiler.getUniqueIdSupplier().getUniqueId(t.getInput());
  }

  /** Returns $jscomp$staticInit$[FILE_ID]$[number] */
  private String generateUniqueStaticInitMethodName(NodeTraversal t) {
    return STATIC_INIT_METHOD_NAME + compiler.getUniqueIdSupplier().getUniqueId(t.getInput());
  }

  private final AbstractCompiler compiler;
  private final AstFactory astFactory;
  private final ExpressionDecomposer expressionDecomposer;
  private final SynthesizeExplicitConstructors ctorCreator;
  private final boolean transpileClassFields;

  private final boolean doStaticInheritanceRewrites;
  private final boolean doStaticSuperRewrites;

  Es6NormalizeClasses(AbstractCompiler compiler) {
    this.compiler = compiler;
    astFactory = compiler.createAstFactory();
    expressionDecomposer = compiler.createDefaultExpressionDecomposer();
    ctorCreator = new SynthesizeExplicitConstructors(compiler);
    // Note: This covers both public fields as well as static fields (they launched together).
    var options = compiler.getOptions();
    transpileClassFields = options.needsTranspilationOf(Feature.PUBLIC_CLASS_FIELDS);

    doStaticInheritanceRewrites =
        options.getAssumeStaticInheritanceIsNotUsed() && !options.getSkipNonTranspilationPasses();

    // Es6ConvertSuper handles static super rewrites otherwise.
    doStaticSuperRewrites = !options.needsTranspilationOf(Feature.SUPER);
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseRoots(compiler, this, externs, root);

    // We transpiled away class static blocks. We always do this, regardless of language out, to
    // normalize the AST.
    FeatureSet transpiledFeatures = FeatureSet.BARE_MINIMUM.with(Feature.CLASS_STATIC_BLOCK);
    if (transpileClassFields) {
      // We removed public and static field declarations if they need transpilation.
      transpiledFeatures = transpiledFeatures.with(Feature.PUBLIC_CLASS_FIELDS);
    }
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, root, transpiledFeatures);
  }

  private final Deque<ClassRecord> classStack = new ArrayDeque<>();

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case SCRIPT -> {
        FeatureSet scriptFeatures = NodeUtil.getFeatureSetOfScript(n);
        return scriptFeatures == null
            || scriptFeatures.contains(Feature.CLASSES)
            || scriptFeatures.contains(Feature.CLASS_STATIC_BLOCK)
            || scriptFeatures.contains(Feature.PUBLIC_CLASS_FIELDS);
      }
      case CLASS -> {
        // First, rewrite complex extends expressions. This must happen before class extraction
        // to ensure the class's structure is simplified first.
        if (shouldExtractExtends(n)) {
          if (!canExtractExtendsSimply(n)) {
            // When a class is used in an expressions where adding an alias as the previous
            // statement might change execution order of a side-effect causing statement, wrap the
            // class in an IIFE so that decomposition can happen safely.
            pushDownNodeIntoNewIife(n);

            // The parent of the class has been updated so reflect that.
            parent = n.getParent();
          }
          extractExtends(t, n);
        }

        // Second, extract the class itself if it's in an expression context or it needs a name.
        // We do the actual class extraction in pre-order so that we can rewrite the inner class
        // name to the new outer name if needed.
        if (shouldExtractClass(n)) {
          if (expressionDecomposer.canExposeExpression(n) != DecompositionType.MOVABLE) {
            // When class is not movable, we wrap it inside an IIFE. We have observed unsafe
            // circumstances where decomposing causes issues so this is safer. See b/417772606.
            pushDownNodeIntoNewIife(n);
          }
          extractClass(t, n);

          // The parent of the class has been updated so reflect that.
          parent = n.getParent();
        }

        // Finally, create the ClassRecord which will be referenced when we visit the class later.

        // Either the assigned name for a class expression or the class declaration name (in which
        // case this would be the same as `bindingIdentifier`).
        Node classNameNode = NodeUtil.getNameNode(n);
        checkState(classNameNode != null, "Class missing a name: %s", n);
        // The class name for declarations and class expression inner names.
        Optional<Node> bindingIdentifier =
            n.getFirstChild().isName() ? Optional.of(n.getFirstChild()) : Optional.empty();
        Optional<Node> superClassNameNode =
            n.getSecondChild().isQualifiedName()
                ? Optional.of(n.getSecondChild())
                : Optional.empty();
        Node classInsertionPoint = getStatementDeclaringClass(n, classNameNode);
        checkState(classInsertionPoint != null, "Class was not extracted: %s", n);
        classStack.addFirst(
            new ClassRecord(
                classNameNode,
                bindingIdentifier,
                /* classNode= */ n,
                superClassNameNode,
                classInsertionPoint));
      }
      case COMPUTED_FIELD_DEF -> {
        checkState(!classStack.isEmpty());
        var classRecord = classStack.peek();
        classRecord.maybeRecordComputedPropWithSideEffects(n);
        classRecord.recordField(n);
      }
      case MEMBER_FIELD_DEF -> {
        checkState(!classStack.isEmpty());
        classStack.peek().recordField(n);
      }
      case BLOCK -> {
        if (!NodeUtil.isClassStaticBlock(n)) {
          break;
        }

        checkState(!classStack.isEmpty());
        classStack.peek().recordStaticBlock(n);
      }
      // For example, a class method with a computed property name.
      case COMPUTED_PROP -> {
        if (!n.getParent().isClassMembers()) {
          break;
        }

        checkState(!classStack.isEmpty());
        classStack.peek().maybeRecordComputedPropWithSideEffects(n);
      }
      default -> {}
    }
    return true;
  }

  private boolean shouldExtractExtends(Node classNode) {
    checkArgument(classNode.isClass());
    Node superClassNode = classNode.getSecondChild();
    return !superClassNode.isEmpty() && !superClassNode.isQualifiedName();
  }

  /**
   * Find common cases where we can safely decompose class extends expressions which are not
   * qualified names. Enables transpilation of complex extends expressions.
   *
   * <p>We can only decompose the expression in a limited set of cases to avoid changing evaluation
   * order of side-effect causing statements.
   */
  private boolean canExtractExtendsSimply(Node classNode) {
    Node enclosingStatement = checkNotNull(NodeUtil.getEnclosingStatement(classNode), classNode);
    if (enclosingStatement == classNode) {
      // `class Foo extends some_expression {}`
      return true;
    }

    Node classNodeParent = classNode.getParent();
    if (NodeUtil.isNameDeclaration(enclosingStatement)
        && classNodeParent.isName()
        && classNodeParent.isFirstChildOf(enclosingStatement)) {
      // `const Foo = class extends some_expression {}, maybe_other_var;`
      return true;
    }

    if (enclosingStatement.isExprResult()
        && classNodeParent.isOnlyChildOf(enclosingStatement)
        && classNodeParent.isAssign()
        && classNode.isSecondChildOf(classNodeParent)) {
      // `lhs = class extends some_expression {};`
      Node lhsNode = classNodeParent.getFirstChild();
      // We can extract a temporary variable for some_expression as long as lhs expression
      // has no side effects.
      return !compiler.getAstAnalyzer().mayHaveSideEffects(lhsNode);
    }

    return false;
  }

  private void extractExtends(NodeTraversal t, Node classNode) {
    String name = generateUniqueClassExtendsVarName(t);

    Node statement = NodeUtil.getEnclosingStatement(classNode);
    Node originalExtends = classNode.getSecondChild();

    // If the extracted superclass is itself a named class expression, remove its inner name to
    // avoid creating a new, useless variable in the scope.
    if (originalExtends.isClass() && originalExtends.getFirstChild().isName()) {
      originalExtends
          .getFirstChild()
          .replaceWith(IR.empty().srcref(originalExtends.getFirstChild()));
    }

    Node nameNode =
        astFactory.createConstantName(name, type(originalExtends)).srcref(originalExtends);
    originalExtends.replaceWith(nameNode);
    Node extendsAlias =
        astFactory
            .createSingleConstNameDeclaration(name, originalExtends)
            .srcrefTreeIfMissing(originalExtends);
    extendsAlias.insertBefore(statement);
    NodeUtil.addFeatureToScript(
        NodeUtil.getEnclosingScript(classNode), Feature.CONST_DECLARATIONS, compiler);
    t.reportCodeChange(classNode);
  }

  /** Replaces a node with an IIFE that returns that node. */
  private void pushDownNodeIntoNewIife(Node n) {
    // Create the shell of the IIFE.
    Node functionBody = IR.block();
    Node arrowFn = IR.arrowFunction(IR.name(""), IR.paramList(), functionBody).srcref(n);
    arrowFn.setColor(StandardColors.UNKNOWN);
    Node iife = astFactory.createCallWithUnknownType(arrowFn).srcrefTreeIfMissing(n);

    // This replaces n with the IIFE and detaches n from the AST.
    n.replaceWith(iife);

    // Now that n is detached, it's safe to make it the child of the RETURN node.
    functionBody.addChildToBack(astFactory.createReturn(n).srcref(n));

    NodeUtil.addFeatureToScript(
        NodeUtil.getEnclosingScript(iife), Feature.ARROW_FUNCTIONS, compiler);

    compiler.reportChangeToEnclosingScope(iife);
  }

  /**
   * Determines if a class node needs to be extracted into a separate variable declaration.
   *
   * <p>Extraction is necessary for class expressions in contexts where their side effects or
   * structure might interfere with subsequent transpilation passes. However, some forms of class
   * expressions are already handled correctly by other parts of the transpilation, specifically
   * {@code Es6ToEs3Converter.ClassDeclarationMetadata#create}.
   */
  private boolean shouldExtractClass(Node classNode) {
    Node parent = classNode.getParent();
    if (NodeUtil.isClassDeclaration(classNode)
        || parent.isName()
        || (parent.isAssign()
            && parent.getFirstChild().isQualifiedName()
            && parent.getParent().isExprResult())) {
      // No need to extract. Handled directly by Es6ToEs3Converter.ClassDeclarationMetadata#create.
      return false;
    }

    return true;
  }

  private void extractClass(NodeTraversal t, Node classNode) {
    Node parent = classNode.getParent();

    String name = generateUniqueClassDeclVarName(t);
    JSDocInfo info = NodeUtil.getBestJSDocInfo(classNode);

    Node statement = NodeUtil.getEnclosingStatement(parent);
    // class name node used as LHS in newly created assignment
    Node classNameLhs = astFactory.createConstantName(name, type(classNode));
    // class name node that replaces the class literal in the original statement
    Node classNameRhs = classNameLhs.cloneTree();
    classNode.replaceWith(classNameRhs);
    Node classDeclaration = IR.constNode(classNameLhs, classNode).srcrefTreeIfMissing(classNode);
    NodeUtil.addFeatureToScript(t.getCurrentScript(), Feature.CONST_DECLARATIONS, compiler);
    classDeclaration.setJSDocInfo(JSDocInfo.Builder.maybeCopyFrom(info).build());
    classDeclaration.insertBefore(statement);

    // If the original statement was a variable declaration or qualified name assignment like
    // like these:
    // var ClassName = class {...
    // OR
    // some.qname.ClassName = class {...
    //
    // We will have changed the original statement to
    //
    // var ClassName = generatedName;
    // OR
    // some.qname.ClassName = generatedName;
    //
    // This is creating a type alias for a class, but since there's no literal class on the RHS,
    // it doesn't look like one. Add at-constructor JSDoc to make it clear that this is happening.
    //
    // This was added to fix a specific problem where the original definition was for an abstract
    // class, so its JSDoc included at-abstract.
    // This caused ClosureCodeRemoval to think this rewritten assignment was a removable abstract
    // method definition instead of the definition of an abstract class.
    //
    // TODO(b/117292942): Make ClosureCodeRemoval smarter so this hack isn't necessary to
    // prevent incorrect removal of assignments.
    if (NodeUtil.isNameDeclaration(statement)
        && statement.hasOneChild()
        && statement.getOnlyChild() == parent) {
      // var ClassName = generatedName;
      addAtConstructor(statement);
    } else if (statement.isExprResult()) {
      Node expr = statement.getOnlyChild();
      if (expr.isAssign()
          && expr.getFirstChild().isQualifiedName()
          && expr.getSecondChild() == classNameRhs) {
        // some.qname.ClassName = generatedName;
        addAtConstructor(expr);
      }
    }
    compiler.reportChangeToEnclosingScope(classDeclaration);
  }

  /** Add at-constructor to the JSDoc of the given node. */
  private void addAtConstructor(Node node) {
    JSDocInfo.Builder builder = JSDocInfo.Builder.maybeCopyFrom(node.getJSDocInfo());
    builder.recordConstructor();
    node.setJSDocInfo(builder.build());
  }

  /**
   * Gets the location of the statement declaring the class
   *
   * @return null if the class cannot be extracted
   */
  private @Nullable Node getStatementDeclaringClass(Node classNode, Node classNameNode) {
    if (NodeUtil.isClassDeclaration(classNode)) {
      // `class C {}` -> can use `C.staticMember` to extract static fields
      checkState(NodeUtil.isStatement(classNode));
      return classNode;
    }
    final Node parent = classNode.getParent();
    if (parent.isName()) {
      // `let C = class {};`
      // We can use `C.staticMemberName = ...` to extract static fields
      checkState(parent == classNameNode);
      checkState(NodeUtil.isStatement(classNameNode.getParent()));
      return classNameNode.getParent();
    }
    if (parent.isAssign()
        && parent.getFirstChild() == classNameNode
        && parent.getParent().isExprResult()) {
      // `something.C = class {}`
      // we can use `something.C.staticMemberName = ...` to extract static fields
      checkState(NodeUtil.isStatement(classNameNode.getGrandparent()));
      return classNameNode.getGrandparent();
    }
    return null;
  }

  @Override
  public void enterScope(NodeTraversal t) {
    Node scopeRoot = t.getScopeRoot();
    if (NodeUtil.isFunctionBlock(scopeRoot) && NodeUtil.isEs6Constructor(scopeRoot.getParent())) {
      classStack.peek().recordConstructorScope(t.getScope());
    }
  }

  @Override
  public void exitScope(NodeTraversal t) {}

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case CLASS -> {
        ClassRecord currClassRecord = classStack.removeFirst();
        checkState(currClassRecord.classNode == n, "unexpected node: %s", n);

        // The parent passed to visit is the parent before shouldTraverse is called.
        // The AST may be modified in shouldTraverse, so we have to get the current parent.
        parent = n.getParent();

        // For class expressions, remove the inner name if it's present.
        // Note: Safe because we removed all static initialization logic, ensured the class has a
        // name, and we replaced all references to the inner class name.
        if (currClassRecord.bindingIdentifier.isPresent()
            && (parent.isName() || parent.isAssign())) {
          n.getFirstChild().replaceWith(IR.empty().srcref(n.getFirstChild()));
          compiler.reportChangeToEnclosingScope(n);
        }

        // Rewrite parts of the class based on the record.
        rewriteSideEffectedComputedProps(t, currClassRecord);
        if (transpileClassFields) {
          rewriteInstanceMembers(t, currClassRecord);
        }
        rewriteStaticMembers(t, currClassRecord);
      }
      case NAME -> maybeUpdateClassSelfRef(t, n);
      case THIS, SUPER -> visitThisAndSuper(t, n);
      default -> {}
    }
  }

  /** Rewrites and moves all side effected computed field keys to the top */
  private void rewriteSideEffectedComputedProps(NodeTraversal t, ClassRecord record) {
    Deque<Node> computedPropsWithSideEffects = record.computedPropsWithSideEffects;

    if (computedPropsWithSideEffects.isEmpty()) {
      return;
    }

    while (!computedPropsWithSideEffects.isEmpty()) {
      Node computedPropMember = computedPropsWithSideEffects.remove();
      extractExpressionFromCompField(t, record, computedPropMember);
    }
    t.reportCodeChange();
  }

  /**
   * Extracts the expression in the LHS of a computed field to not disturb possible side effects and
   * allow for this and super to be used in the LHS of a computed field in certain cases. Does not
   * extract a computed field that was already moved into a computed function.
   *
   * <p>E.g.
   *
   * <pre>
   * class Foo {
   *   [bar('str')] = 4;
   * }
   * </pre>
   *
   * becomes
   *
   * <pre>
   * var $jscomp$computedfield$0 = bar('str');
   * class Foo {
   *   [$jscomp$computedfield$0] = 4;
   * }
   * </pre>
   */
  private void extractExpressionFromCompField(
      NodeTraversal t, ClassRecord record, Node memberField) {
    checkArgument(memberField.isComputedFieldDef() || memberField.isComputedProp(), memberField);

    Node compExpression = memberField.removeFirstChild();
    Node compFieldVar =
        astFactory
            .createSingleVarNameDeclaration(generateUniqueCompFieldVarName(t), compExpression)
            .srcrefTreeIfMissing(record.classNode);
    Node compFieldName = compFieldVar.getFirstChild();
    memberField.addChildToFront(compFieldName.cloneNode());
    compFieldVar.insertBefore(record.insertionPoint);
    compFieldVar.srcrefTreeIfMissing(record.classNode);
  }

  /** Rewrites and moves all instance fields */
  private void rewriteInstanceMembers(NodeTraversal t, ClassRecord record) {
    Deque<Node> instanceMembers = record.instanceMembers;

    if (instanceMembers.isEmpty()) {
      return;
    }
    ctorCreator.synthesizeClassConstructorIfMissing(t, record.classNode);
    Node ctor = NodeUtil.getEs6ClassConstructorMemberFunctionDef(record.classNode);
    Node ctorBlock = ctor.getFirstChild().getLastChild();
    Node insertionPoint = addTemporaryInsertionPoint(ctorBlock, record);

    while (!instanceMembers.isEmpty()) {
      Node instanceMember = instanceMembers.remove();
      checkState(
          instanceMember.isMemberFieldDef() || instanceMember.isComputedFieldDef(), instanceMember);

      Node thisNode = astFactory.createThisForEs6ClassMember(instanceMember);
      Node transpiledNode =
          instanceMember.isMemberFieldDef()
              ? convNonCompFieldToGetProp(thisNode, instanceMember, /* isStatic= */ false)
              : convCompFieldToGetElem(thisNode, instanceMember, /* isStatic= */ false);

      transpiledNode.insertBefore(checkNotNull(insertionPoint));
    }

    insertionPoint.detach();

    // We moved the field from the class body to the constructor, so we need both.
    t.reportCodeChange();
    t.reportCodeChange(ctorBlock);
  }

  /**
   * Finds the location of super() call in the constructor and add a temporary empty node after the
   * super() call. If there is no super() call, add a temporary empty node at the beginning of the
   * constructor body after all function declarations.
   *
   * <p>Returns the added temporary empty node
   */
  private Node addTemporaryInsertionPoint(Node ctorBlock, ClassRecord classRecord) {
    checkState(
        classRecord.superCalls.size() <= 1,
        "classes with public fields must have only one super() call at the constructor root: %s",
        classRecord.classNode);

    Node tempNode = IR.empty();
    if (classRecord.superCalls.size() == 1) {
      Node stmt = ensureSuperCallIsOwnStatement(classRecord.superCalls.get(0));
      tempNode.insertAfter(stmt);
      return tempNode;
    }
    // Search for super() calls added by SynthesizeExplicitConstructors.
    for (Node stmt = ctorBlock.getFirstChild(); stmt != null; stmt = stmt.getNext()) {
      if (NodeUtil.isExprCall(stmt) && stmt.getFirstFirstChild().isSuper()) {
        tempNode.insertAfter(stmt);
        return tempNode;
      }
    }
    Node insertionPoint = NodeUtil.getInsertionPointAfterAllInnerFunctionDeclarations(ctorBlock);
    if (insertionPoint != null) {
      tempNode.insertBefore(insertionPoint);
    } else {
      // functionBody only contains hoisted function declarations
      ctorBlock.addChildToBack(tempNode);
    }
    return tempNode;
  }

  /**
   * Returns a statement containing either {@code super(...);} or {@code var tmp = super(...)},
   * extracting the given super call into a {@code tmp} variable if necessary.
   */
  private Node ensureSuperCallIsOwnStatement(Node superCall) {
    Node parent = superCall.getParent();
    if (parent.isExprResult()) {
      return parent; // No extraction needed.
    }
    checkState(
        expressionDecomposer.canExposeExpression(superCall) != DecompositionType.UNDECOMPOSABLE,
        "Cannot decompose super() call in a class with class fields. Move super() call to the"
            + " root of the constructor. %s",
        superCall);
    expressionDecomposer.maybeExposeExpression(superCall);
    expressionDecomposer.moveExpression(superCall);
    return NodeUtil.getEnclosingStatement(superCall);
  }

  /**
   * Creates a node that represents receiver.key = value; where the key and value comes from the
   * non-computed field
   */
  private @Nullable Node convNonCompFieldToGetProp(
      Node receiver, Node noncomputedField, boolean isStatic) {
    checkArgument(noncomputedField.isMemberFieldDef());
    checkArgument(receiver.getParent() == null, receiver);

    Node fieldValue = null;
    // We always move out the static field initialization.
    if (isStatic || transpileClassFields) {
      fieldValue = noncomputedField.getFirstChild();
    }

    return createFieldAssignment(
        noncomputedField,
        fieldValue,
        () ->
            astFactory.createGetProp(
                receiver, noncomputedField.getString(), type(noncomputedField)));
  }

  /**
   * Creates a node that represents receiver[key] = value; where the key and value comes from the
   * computed field
   */
  private @Nullable Node convCompFieldToGetElem(
      Node receiver, Node computedField, boolean isStatic) {
    checkArgument(computedField.isComputedFieldDef(), computedField);
    checkArgument(receiver.getParent() == null, receiver);

    Node fieldValue = null;
    if (computedField.getChildCount() == 2 && (isStatic || transpileClassFields)) {
      fieldValue = computedField.getLastChild();
    }

    return createFieldAssignment(
        computedField,
        fieldValue,
        () -> {
          Node compFieldNameExpr;
          if (transpileClassFields) {
            compFieldNameExpr = computedField.removeFirstChild();
          } else {
            compFieldNameExpr = computedField.getFirstChild().cloneTree();
          }
          return astFactory.createGetElem(receiver, compFieldNameExpr);
        });
  }

  /**
   * Shared helper to create an assignment statement for a class field. Handles logic for creating
   * undefined values for uninitialized fields during transpilation, as well as JSDoc transfer and
   * source reference mapping.
   */
  private @Nullable Node createFieldAssignment(
      Node field, @Nullable Node fieldValue, Supplier<Node> createAssignLhs) {
    if (transpileClassFields) {
      // We don't need to keep around the field declaration.
      field.detach();
    }

    // We don't make a copy of the static field declaration if it doesn't have an initializer and
    // we're not transpiling class fields.
    if (fieldValue == null) {
      if (!transpileClassFields) {
        return null;
      }

      // If we are transpiling class fields, we want an explicit undefined value so we properly
      // initialize the field.
      fieldValue = astFactory.createUndefinedValue();
    } else {
      // We are going to move fieldValue to a new parent, so detach it now.
      fieldValue.detach();
    }

    Node result = astFactory.createAssignStatement(createAssignLhs.get(), fieldValue);

    if (transpileClassFields) {
      // Move any JSDoc from the field declaration to the child of the EXPR_RESULT, which represents
      // the new declaration.
      // field is already detached, so there's no benefit to calling
      // NodeUtil.getBestJSDocInfo(field). For now at least, the JSDocInfo we want will always be
      // directly on field in all cases.
      result.getFirstChild().setJSDocInfo(field.getJSDocInfo());
    }
    result.srcrefTreeIfMissing(field);
    return result;
  }

  /** Rewrites and moves all static blocks and fields */
  private void rewriteStaticMembers(NodeTraversal t, ClassRecord record) {
    Deque<Node> staticMembers = record.staticMembers;
    if (staticMembers.isEmpty()) {
      return;
    }

    Node staticInitTempParent = IR.block();
    while (!staticMembers.isEmpty()) {
      Node staticMember = staticMembers.remove();
      // if the name is a property access, we want the whole chain of accesses, while for other
      // cases we only want the name node
      Node nameToUse = record.createNewNameReferenceNode().srcrefTree(staticMember);

      Node transpiledNode =
          switch (staticMember.getToken()) {
            case BLOCK -> {
              Node block = staticMember.detach();
              t.reportCodeChange();
              // We ignore empty static initialization blocks.
              yield block.hasChildren() ? block : null;
            }
            case MEMBER_FIELD_DEF ->
                convNonCompFieldToGetProp(nameToUse, staticMember, /* isStatic= */ true);
            case COMPUTED_FIELD_DEF ->
                convCompFieldToGetElem(nameToUse, staticMember, /* isStatic= */ true);
            default -> throw new IllegalStateException(String.valueOf(staticMember));
          };
      if (transpiledNode != null) {
        staticInitTempParent.addChildToBack(transpiledNode);
      }
    }

    // If there is no static initialization logic, we are done.
    if (!staticInitTempParent.hasChildren()) {
      return;
    }

    // If there are no super references, place all the static initialization code after the class.
    // TODO: b/236744850 - Also skip if there are private field refs.
    if (!NodeUtil.has(
        staticInitTempParent,
        /* pred= */ Node::isSuper,
        /* traverseChildrenPred= */ (n) ->
            !n.isClass() && (!n.isFunction() || n.isArrowFunction()))) {
      while (staticInitTempParent.hasChildren()) {
        staticInitTempParent.getLastChild().detach().insertAfter(record.insertionPoint);
      }
      t.reportCodeChange(record.insertionPoint.getParent());
      return;
    }

    // We will introduce a new static method and a call to it to contain static initialization code.

    // Add new static method "$jscomp$staticInit$[FILE_ID]$[number]".
    // All the static initialization logic goes into this method. We need a static method as opposed
    // to doing this logic alongside the class because #private members can only be accessed within
    // the class.
    String staticInitMethodName = generateUniqueStaticInitMethodName(t);
    Node staticInitMethodFunc =
        astFactory.createEmptyFunction(type(JSTypeNative.FUNCTION_TYPE, StandardColors.TOP_OBJECT));
    Node staticInitMethod =
        astFactory.createMemberFunctionDef(staticInitMethodName, staticInitMethodFunc);
    staticInitMethod.setStaticMember(true);

    Node staticInitBlock = staticInitMethod.getFirstChild().getLastChild();
    staticInitBlock.addChildrenToFront(staticInitTempParent.removeChildren());

    Node classMembers = NodeUtil.getClassMembers(record.classNode);
    staticInitMethod.srcrefTree(classMembers);
    classMembers.addChildToBack(staticInitMethod);
    compiler.reportChangeToChangeScope(staticInitMethodFunc);
    t.reportCodeChange(staticInitMethod);
    // Record the feature corresponding to a static method.
    NodeUtil.addFeatureToScript(t.getCurrentScript(), Feature.MEMBER_DECLARATIONS, compiler);

    // Add a call to the static initialization method after the class definition.
    Node staticInitCall =
        astFactory
            .createCall(
                astFactory.createGetProp(
                    record.createNewNameReferenceNode(),
                    staticInitMethodName,
                    type(staticInitMethodFunc)),
                type(JSTypeNative.VOID_TYPE, StandardColors.NULL_OR_VOID))
            .srcrefTree(classMembers);
    Node staticInitCallStmt = astFactory.exprResult(staticInitCall);
    staticInitCallStmt.insertAfter(record.insertionPoint);
  }

  private void maybeUpdateClassSelfRef(NodeTraversal t, Node nameNode) {
    for (ClassRecord klass : classStack) {
      if (klass.bindingIdentifier.isEmpty()) {
        continue;
      }

      Node bindingIdentifier = klass.bindingIdentifier.get();
      if (
      // Skip if the inner class name is the same as the class name (either this is a class
      // declaration or the outer and inner class names are the same).
      bindingIdentifier.getString().equals(klass.classNameNode.getQualifiedName())
          // Skip if this IS the inner class name node (removed later when we visit the class).
          || nameNode == bindingIdentifier
          // Skip if this nameNode is not equal to the inner class name.
          || !nameNode.matchesQualifiedName(bindingIdentifier)) {
        continue;
      }

      // Ensure that this nameNode is not being shadowed by a different variable with the same name
      // as the inner class name.
      Var var = t.getScope().getVar(nameNode.getString());
      if (var == null || var.getNameNode() != bindingIdentifier) {
        continue;
      }

      Node newNameNode = klass.createNewNameReferenceNode().srcref(nameNode);
      nameNode.replaceWith(newNameNode);
      compiler.reportChangeToEnclosingScope(newNameNode);

      return; // We made the replacement so bail.
    }
  }

  /**
   * Visits {@code this} and {@code super} nodes in the class. Only makes changes in static contexts
   * and records super calls in the constructor for later.
   *
   * <p>For static {@code this}, we can only replace with the class name if in a static
   * initialization context (i.e. in a static field initializer or a static block).
   *
   * <p>For static {@code super}, we can always replace with the extended class name.
   *
   * <p>Note: This runs before {@link #rewriteStaticMembers} so the static members are still there.
   */
  private void visitThisAndSuper(NodeTraversal t, Node n) {
    if (isSuperCall(n.getParent())) {
      // super(...); calls are only legal in a constructor so we don't need to check for static
      // context.
      ClassRecord classRecord = classStack.peek();
      classRecord.superCalls.add(n.getParent());
      return;
    }
    Node rootNode = t.getClosestScopeRootNodeBindingThisOrSuper();
    Node rootParent = rootNode.getParent();
    if (rootNode.isFunction()
        && (rootParent.isMemberFunctionDef()
            || rootParent.isComputedProp()
            || rootParent.isGetterDef()
            || rootParent.isSetterDef())) {
      rootNode = rootParent;
    }
    boolean inStaticBlock = rootNode.isBlock() && rootNode.getParent().isClassMembers();
    if (!rootNode.isStaticMember() && !inStaticBlock) {
      return;
    }

    // We always rewrite static this to the class name in a static initialization context so that we
    // can normalize-away static initialization from field initializers and static blocks.
    boolean forceRewriteStaticThis = n.isThis() && isInStaticInitializationContext(rootNode);

    if (!doStaticInheritanceRewrites && !forceRewriteStaticThis) {
      return;
    }

    Node classNode = rootNode.getGrandparent();
    ClassRecord classRecord = classStack.peek();
    checkState(
        classRecord.classNode == classNode,
        "wrong class node: %s != %s",
        classRecord.classNode,
        classNode);

    Node newNameNode = null;
    if (forceRewriteStaticThis) {
      // For static this, we can only universally replace with the class name in a static
      // initialization context.
      newNameNode = classRecord.createNewNameReferenceNode().srcrefTree(n);
    } else if (doStaticSuperRewrites && n.isSuper()) {
      // For a static super, we can always rewrite the super as something else.
      checkState(classRecord.superClassNameNode.isPresent(), classRecord.classNode);
      newNameNode = classRecord.superClassNameNode.get().cloneTree();
    }

    if (newNameNode != null) {
      String originalName = checkNotNull(n.getQualifiedName());
      newNameNode.srcrefTree(n).setOriginalName(originalName);
      n.replaceWith(newNameNode);
      t.reportCodeChange(newNameNode);
    }
  }

  private static boolean isSuperCall(Node n) {
    return n.isCall() && n.getFirstChild().isSuper();
  }

  // Note: Assumes that we have already filtered out non-static members and non-static blocks.
  private static boolean isInStaticInitializationContext(Node rootNode) {
    return switch (rootNode.getToken()) {
      case MEMBER_FUNCTION_DEF, COMPUTED_PROP, GETTER_DEF, SETTER_DEF -> {
        checkState(rootNode.isStaticMember());
        yield false;
      }
      case MEMBER_FIELD_DEF, COMPUTED_FIELD_DEF -> {
        checkState(rootNode.isStaticMember());
        yield true;
      }
      case BLOCK -> {
        checkState(rootNode.getParent().isClassMembers());
        yield true;
      }
      default -> throw new IllegalArgumentException("invalid root " + rootNode);
    };
  }

  private static class ClassRecord {
    /**
     * The node for the qualified name of the class.
     *
     * <p>{@code "C"} as in:
     *
     * <ul>
     *   <li>{@code class C {}}
     *   <li>{@code const C = class D {};}
     *   <li>{@code const C = class {};}
     * </ul>
     *
     * <p>{@code "ns.C"} as in:
     *
     * <ul>
     *   <li>{@code const ns = {}; ns.C = class {};}
     * </ul>
     */
    final Node classNameNode;

    /**
     * The binding identifier (a.k.a. name node) of the class itself.
     *
     * <p>The node for {@code C} as in:
     *
     * <ul>
     *   <li>{@code class C {}}
     *   <li>{@code const D = class C {};} // Inner name node in this case.
     * </ul>
     *
     * <p>Absent as in:
     *
     * <ul>
     *   <li>{@code const C = class {};}
     * </ul>
     */
    final Optional<Node> bindingIdentifier;

    /** The node for `class` in both class declarations and class expressions. */
    final Node classNode;

    /**
     * The name node of the super class, if present.
     *
     * <p>The node for {@code E} as in:
     *
     * <ul>
     *   <li>{@code class C extends E {}}
     *   <li>{@code const D = class C extends E {};}
     *   <li>{@code const D = class extends E {};}
     * </ul>
     */
    final Optional<Node> superClassNameNode;

    /** Insert before or after this node to add stuff outside the class. */
    final Node insertionPoint;

    /** Instance fields in the class. */
    final Deque<Node> instanceMembers = new ArrayDeque<>();

    /** Static fields + static blocks */
    final Deque<Node> staticMembers = new ArrayDeque<>();

    /** computed props with side effects */
    final Deque<Node> computedPropsWithSideEffects = new ArrayDeque<>();

    /** Set of all the Vars defined in the constructor arguments scope and constructor body scope */
    ImmutableSet<Var> constructorVars = ImmutableSet.of();

    /**
     * List of all the {@code super()} calls within the constructor, if any.
     *
     * <p>It's legal for classes to have more than one super() call, hence this is a list rather
     * than an {@code Optional<Node>}. However, public class field transpilation will throw an error
     * if this list has more than one entry in a class with a public field; see {@link
     * Es6NormalizeClasses#ensureSuperCallIsOwnStatement}. We wait to emit this error until after
     * traversing the entire class body to find public fields.
     */
    final List<Node> superCalls = new ArrayList<>();

    ClassRecord(
        Node classNameNode,
        Optional<Node> bindingIdentifier,
        Node classNode,
        Optional<Node> superClassNameNode,
        Node insertionPoint) {
      this.classNameNode = classNameNode;
      this.bindingIdentifier = bindingIdentifier;
      this.classNode = classNode;
      this.superClassNameNode = superClassNameNode;
      this.insertionPoint = insertionPoint;
    }

    void recordField(Node field) {
      checkArgument(field.isComputedFieldDef() || field.isMemberFieldDef());
      if (field.isStaticMember()) {
        staticMembers.add(field);
      } else {
        instanceMembers.add(field);
      }
    }

    void maybeRecordComputedPropWithSideEffects(Node computedProp) {
      checkArgument(computedProp.isComputedProp() || computedProp.isComputedFieldDef());

      if (NodeUtil.canBeSideEffected(computedProp.getFirstChild())) {
        computedPropsWithSideEffects.add(computedProp);
      }
    }

    void recordStaticBlock(Node block) {
      checkArgument(NodeUtil.isClassStaticBlock(block));
      staticMembers.add(block);
    }

    void recordConstructorScope(Scope s) {
      checkArgument(s.isFunctionBlockScope(), s);
      checkState(constructorVars.isEmpty(), constructorVars);
      Scope argsScope = s.getParent();
      constructorVars =
          ImmutableSet.<Var>builder()
              .addAll(s.getAllSymbols())
              .addAll(argsScope.getAllSymbols())
              .build();
    }

    Node createNewNameReferenceNode() {
      if (classNameNode.isName()) {
        // Don't cloneTree() here, because the name may have a child node, the class itself.
        return classNameNode.cloneNode();
      }

      // Must cloneTree() for a qualified name.
      return classNameNode.cloneTree();
    }
  }
}
