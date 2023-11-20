/*
 * Copyright 2021 The Closure Compiler Authors.
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
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import java.util.ArrayDeque;
import java.util.Deque;
import org.jspecify.nullness.Nullable;

/** Replaces the ES2022 class fields and class static blocks with constructor declaration. */
public final class RewriteClassMembers implements NodeTraversal.ScopedCallback, CompilerPass {

  private final AbstractCompiler compiler;
  private final AstFactory astFactory;
  private final SynthesizeExplicitConstructors ctorCreator;
  private final Deque<ClassRecord> classStack;

  private static final String COMP_FIELD_VAR = "$jscomp$compfield$";
  private static final String STATIC_FIELD_NAME = "$jscomp$static$init$";
  private static final String STATIC_BLOCK_NAME = "$jscomp$static$block$";

  public RewriteClassMembers(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
    this.ctorCreator = new SynthesizeExplicitConstructors(compiler);
    this.classStack = new ArrayDeque<>();
  }

  @Override
  public void process(Node externs, Node root) {
    // Make all declared names unique to ensure that name shadowing can't occur in classes and
    // public fields don't share names with constructor parameters
    MakeDeclaredNamesUnique renamer = MakeDeclaredNamesUnique.builder().build();
    NodeTraversal.traverseRoots(compiler, renamer, externs, root);
    NodeTraversal.traverse(compiler, root, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(
        compiler, root, Feature.PUBLIC_CLASS_FIELDS, Feature.CLASS_STATIC_BLOCK);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case SCRIPT:
        FeatureSet scriptFeatures = NodeUtil.getFeatureSetOfScript(n);
        return scriptFeatures == null
            || scriptFeatures.contains(Feature.PUBLIC_CLASS_FIELDS)
            || scriptFeatures.contains(Feature.CLASS_STATIC_BLOCK);
      case CLASS:
        Node classNameNode = NodeUtil.getNameNode(n);
        checkState(classNameNode != null, "Class missing a name: %s", n);
        Node classInsertionPoint = getStatementDeclaringClass(n, classNameNode);
        checkState(classInsertionPoint != null, "Class was not extracted: %s", n);
        checkState(
            n.getFirstChild().isEmpty() || classNameNode.matchesQualifiedName(n.getFirstChild()),
            "Class name shadows variable declaring the class: %s",
            n);
        classStack.push(new ClassRecord(n, classNameNode, classInsertionPoint));
        break;
      case COMPUTED_FIELD_DEF:
        checkState(!classStack.isEmpty());
        if (NodeUtil.canBeSideEffected(n.getFirstChild())) {
          classStack.peek().computedPropMembers.add(n);
        }
        classStack.peek().enterField(n);
        break;
      case MEMBER_FIELD_DEF:
        checkState(!classStack.isEmpty());
        classStack.peek().enterField(n);
        break;
      case BLOCK:
        if (!NodeUtil.isClassStaticBlock(n)) {
          break;
        }
        checkState(!classStack.isEmpty());
        classStack.peek().recordStaticBlock(n);
        break;
      case COMPUTED_PROP:
        checkState(!classStack.isEmpty());
        if (NodeUtil.canBeSideEffected(n.getFirstChild())) {
          classStack.peek().computedPropMembers.add(n);
        }
        break;
      default:
        break;
    }
    return true;
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
      case CLASS:
        visitClass(t, n);
        break;
      default:
        break;
    }
  }

  /** Transpile the actual class members themselves */
  private void visitClass(NodeTraversal t, Node classNode) {
    ClassRecord currClassRecord = classStack.remove();
    checkState(currClassRecord.classNode == classNode, "unexpected node: %s", classNode);
    if (currClassRecord.cannotConvert) {
      return;
    }
    rewriteSideEffectedComputedProp(t, currClassRecord);
    rewriteInstanceMembers(t, currClassRecord);
    rewriteStaticMembers(t, currClassRecord);
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
    compFieldVar.insertBefore(record.insertionPointBeforeClass);
    compFieldVar.srcrefTreeIfMissing(record.classNode);
  }

  /** Returns $jscomp$compfield$[FILE_ID]$[number] */
  private String generateUniqueCompFieldVarName(NodeTraversal t) {
    return COMP_FIELD_VAR + compiler.getUniqueIdSupplier().getUniqueId(t.getInput());
  }

  /**
   * Returns $jscomp$static$init$[FILE_ID]$[number] for static fields
   * $jscomp$static$block$[FILE_ID]$[number] for static blocks
   */
  private String generateUniqueStaticMethodName(
      NodeTraversal t, Node staticMember, boolean isStaticBlock) {
    String prefix = isStaticBlock ? STATIC_BLOCK_NAME : STATIC_FIELD_NAME;
    String uniqueName = prefix + compiler.getUniqueIdSupplier().getUniqueId(t.getInput());

    if (staticMember.isMemberFieldDef()) {
      uniqueName = uniqueName + "$" + staticMember.getString();
    }

    return uniqueName;
  }

  /** Rewrites and moves all side effected computed field keys to the top */
  private void rewriteSideEffectedComputedProp(NodeTraversal t, ClassRecord record) {
    Deque<Node> computedPropMembers = record.computedPropMembers;

    if (computedPropMembers.isEmpty()) {
      return;
    }

    while (!computedPropMembers.isEmpty()) {
      Node computedPropMember = computedPropMembers.remove();
      extractExpressionFromCompField(t, record, computedPropMember);
    }
    t.reportCodeChange();
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
    Node insertionPoint = addTemporaryInsertionPoint(ctorBlock);

    while (!instanceMembers.isEmpty()) {
      Node instanceMember = instanceMembers.remove();
      checkState(
          instanceMember.isMemberFieldDef() || instanceMember.isComputedFieldDef(), instanceMember);

      Node thisNode = astFactory.createThisForEs6ClassMember(instanceMember);
      Node transpiledNode =
          instanceMember.isMemberFieldDef()
              ? convNonCompFieldToGetProp(thisNode, instanceMember.detach())
              : convCompFieldToGetElem(thisNode, instanceMember.detach());

      transpiledNode.insertBefore(insertionPoint);
    }

    insertionPoint.detach();

    t.reportCodeChange(); // we moved the field from the class body
    t.reportCodeChange(ctorBlock); // to the constructor, so we need both
  }

  /** Rewrites and moves all static blocks and fields */
  private void rewriteStaticMembers(NodeTraversal t, ClassRecord record) {
    Deque<Node> staticMembers = record.staticMembers;
    Node insertionPoint = addTemporaryInsertionPointAfterNode(record.insertionPointAfterClass);

    while (!staticMembers.isEmpty()) {
      Node staticMember = staticMembers.remove();
      // if the name is a property access, we want the whole chain of accesses, while for other
      // cases we only want the name node
      Node nameToUse = record.createNewNameReferenceNode().srcrefTree(staticMember);

      Node transpiledNode;
      // a placeholder node is added after the staticMember to keep track of the position
      Node staticMethodInsertionPoint = addTemporaryInsertionPointAfterNode(staticMember);
      Node blockNode;
      Node callNode;

      switch (staticMember.getToken()) {
        case BLOCK:
          blockNode = staticMember.detach();
          callNode =
              convBlockToStaticMethod(
                  t,
                  nameToUse,
                  staticMethodInsertionPoint,
                  blockNode,
                  /* returnType= */ null,
                  staticMember,
                  /* isStaticBlock= */ true);
          transpiledNode = astFactory.exprResult(callNode).srcrefTreeIfMissing(staticMember);
          break;
        case MEMBER_FIELD_DEF:
          if (!staticMember.hasChildren()) {
            transpiledNode =
                convStaticMethodToCall(nameToUse, staticMember.detach(), /* callNode= */ null);
            break;
          }
          blockNode = createBlockNodeWithReturn(staticMember.removeFirstChild());
          callNode =
              convBlockToStaticMethod(
                  t,
                  nameToUse,
                  staticMethodInsertionPoint,
                  blockNode,
                  blockNode.getJSType(),
                  staticMember,
                  /* isStaticBlock= */ false);
          transpiledNode = convStaticMethodToCall(nameToUse, staticMember.detach(), callNode);
          break;
        case COMPUTED_FIELD_DEF:
          if (staticMember.hasChildren() && staticMember.getChildCount() > 1) {
            blockNode = createBlockNodeWithReturn(staticMember.getLastChild().detach());
            callNode =
                convBlockToStaticMethod(
                    t,
                    nameToUse,
                    staticMethodInsertionPoint,
                    blockNode,
                    blockNode.getJSType(),
                    staticMember,
                    /* isStaticBlock= */ false);
            transpiledNode = convStaticMethodToCall(nameToUse, staticMember.detach(), callNode);
          } else {
            transpiledNode =
                convStaticMethodToCall(nameToUse, staticMember.detach(), /* callNode= */ null);
          }
          break;
        default:
          throw new IllegalStateException(String.valueOf(staticMember));
      }
      staticMethodInsertionPoint.detach();
      transpiledNode.insertBefore(insertionPoint);
      t.reportCodeChange();
    }
    insertionPoint.detach();
  }

  /**
   * Creates a node that represents receiver.key = value; where the key and value comes from the
   * non-computed field
   */
  private Node convNonCompFieldToGetProp(Node receiver, Node noncomputedField) {
    checkArgument(noncomputedField.isMemberFieldDef());
    checkArgument(noncomputedField.getParent() == null, noncomputedField);
    checkArgument(receiver.getParent() == null, receiver);
    Node getProp =
        astFactory.createGetProp(
            receiver, noncomputedField.getString(), AstFactory.type(noncomputedField));
    Node fieldValue = noncomputedField.getFirstChild();
    Node result =
        (fieldValue != null)
            ? astFactory.createAssignStatement(getProp, fieldValue.detach())
            : astFactory.exprResult(getProp);
    result.srcrefTreeIfMissing(noncomputedField);
    return result;
  }

  private Node convStaticMethodToCall(Node receiver, Node staticMember, Node callNode) {
    checkArgument(staticMember.isMemberFieldDef() || staticMember.isComputedFieldDef());
    checkArgument(staticMember.getParent() == null, staticMember);
    checkArgument(receiver.getParent() == null, receiver);

    Node getPropOrElem;
    if (staticMember.isMemberFieldDef()) {
      getPropOrElem =
          astFactory
              .createGetProp(receiver, staticMember.getString(), AstFactory.type(staticMember))
              .srcrefTreeIfMissing(staticMember);
    } else {
      getPropOrElem = astFactory.createGetElem(receiver, staticMember.removeFirstChild());
    }

    Node result;
    if (callNode != null) {
      result = astFactory.createAssignStatement(getPropOrElem, callNode);
    } else {
      result = astFactory.exprResult(getPropOrElem);
    }
    result.srcrefTreeIfMissing(staticMember);
    return result;
  }

  private Node convBlockToStaticMethod(
      NodeTraversal t,
      Node receiver,
      Node insertionPoint,
      Node blockNode,
      JSType returnType,
      Node staticMember,
      boolean isStaticBlock) {

    checkArgument(blockNode.isBlock());
    checkArgument(blockNode.getParent() == null, blockNode);
    checkArgument(receiver.getParent() == null, receiver);

    Node functionNode =
        astFactory
            .createZeroArgFunction("", blockNode, returnType)
            .srcrefTreeIfMissing(staticMember);
    compiler.reportChangeToChangeScope(functionNode);
    String uniqueStaticMethodName = generateUniqueStaticMethodName(t, staticMember, isStaticBlock);
    Node methodNode =
        astFactory
            .createMemberFunctionDef(uniqueStaticMethodName, functionNode)
            .srcrefTreeIfMissing(staticMember);
    methodNode.setStaticMember(true);
    methodNode.insertBefore(insertionPoint);
    compiler.reportChangeToEnclosingScope(methodNode);

    Node getPropNode =
        astFactory
            .createGetProp(
                receiver.cloneTree(), methodNode.getString(), AstFactory.type(methodNode))
            .srcrefTreeIfMissing(staticMember);
    return astFactory.createCall(getPropNode, AstFactory.type(methodNode.getFirstChild()));
  }

  /**
   * Creates a node that represents receiver[key] = value; where the key and value comes from the
   * computed field
   */
  private Node convCompFieldToGetElem(Node receiver, Node computedField) {
    checkArgument(computedField.isComputedFieldDef(), computedField);
    checkArgument(computedField.getParent() == null, computedField);
    checkArgument(receiver.getParent() == null, receiver);
    Node getElem = astFactory.createGetElem(receiver, computedField.removeFirstChild());
    Node fieldValue = computedField.getLastChild();
    Node result =
        (fieldValue != null)
            ? astFactory.createAssignStatement(getElem, fieldValue.detach())
            : astFactory.exprResult(getElem);
    result.srcrefTreeIfMissing(computedField);
    return result;
  }

  private Node createBlockNodeWithReturn(Node returnValue) {

    Node returnNode = astFactory.createReturn(returnValue);
    return astFactory.createBlock(returnNode);
  }

  /**
   * Finds the location of super() call in the constructor and add a temporary empty node after the
   * super() call. If there is no super() call, add a temporary empty node at the beginning of the
   * constructor body.
   *
   * <p>Returns the added temporary empty node
   */
  private Node addTemporaryInsertionPoint(Node ctorBlock) {
    Node tempNode = IR.empty();
      for (Node stmt = ctorBlock.getFirstChild(); stmt != null; stmt = stmt.getNext()) {
        if (NodeUtil.isExprCall(stmt) && stmt.getFirstFirstChild().isSuper()) {
        tempNode.insertAfter(stmt);
        return tempNode;
        }
      }
    ctorBlock.addChildToFront(tempNode);
    return tempNode;
  }

  private Node addTemporaryInsertionPointAfterNode(Node node) {
    Node tempNode = IR.empty();
    tempNode.insertAfter(node);
    return tempNode;
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

  /**
   * Accumulates information about different classes while going down the AST in shouldTraverse()
   */
  private static final class ClassRecord {

    boolean cannotConvert;

    // Instance fields
    final Deque<Node> instanceMembers = new ArrayDeque<>();
    // Static fields + static blocks
    final Deque<Node> staticMembers = new ArrayDeque<>();
    // computed props with side effects
    final Deque<Node> computedPropMembers = new ArrayDeque<>();

    // Set of all the Vars defined in the constructor arguments scope and constructor body scope
    ImmutableSet<Var> constructorVars = ImmutableSet.of();

    final Node classNode;
    final Node classNameNode;

    // Keeps track of the statement declaring the class
    final Node insertionPointBeforeClass;

    // Keeps track of the last statement inserted after the class
    final Node insertionPointAfterClass;

    ClassRecord(Node classNode, Node classNameNode, Node classInsertionPoint) {
      this.classNode = classNode;
      this.classNameNode = classNameNode;
      this.insertionPointBeforeClass = classInsertionPoint;
      this.insertionPointAfterClass = classInsertionPoint;
    }

    void enterField(Node field) {
      checkArgument(field.isComputedFieldDef() || field.isMemberFieldDef());
      if (field.isStaticMember()) {
        staticMembers.add(field);
      } else {
        instanceMembers.add(field);
      }
    }

    void recordStaticBlock(Node block) {
      checkArgument(NodeUtil.isClassStaticBlock(block));
      staticMembers.add(block);
    }

    void recordConstructorScope(Scope s) {
      checkArgument(s.isFunctionBlockScope(), s);
      checkState(constructorVars.isEmpty(), constructorVars);
      ImmutableSet.Builder<Var> builder = ImmutableSet.builder();
      builder.addAll(s.getAllSymbols());
      Scope argsScope = s.getParent();
      builder.addAll(argsScope.getAllSymbols());
      constructorVars = builder.build();
    }

    Node createNewNameReferenceNode() {
      if (classNameNode.isName()) {
        // Don't cloneTree() here, because the name may have a child node, the class itself.
        return classNameNode.cloneNode();
      } else {
        // Must cloneTree() for a qualified name.
        return classNameNode.cloneTree();
      }
    }
  }
}
