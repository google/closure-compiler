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

import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;
import java.util.ArrayDeque;
import java.util.Deque;

/** Replaces the ES2022 class fields and class static blocks with constructor declaration. */
public final class RewriteClassMembers implements NodeTraversal.Callback, CompilerPass {

  private final AbstractCompiler compiler;
  private final AstFactory astFactory;
  private final SynthesizeExplicitConstructors ctorCreator;
  private final Deque<ClassRecord> classStack;

  public RewriteClassMembers(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
    this.ctorCreator = new SynthesizeExplicitConstructors(compiler);
    this.classStack = new ArrayDeque<>();
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(
        compiler, Feature.PUBLIC_CLASS_FIELDS, Feature.CLASS_STATIC_BLOCK);
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
        classStack.push(new ClassRecord(n));
        break;
      case COMPUTED_FIELD_DEF:
        checkState(!classStack.isEmpty());
        t.report(n, TranspilationUtil.CANNOT_CONVERT_YET, "Computed fields");
        classStack.peek().cannotConvert = true;
        return false;
      case MEMBER_FIELD_DEF:
        checkState(!classStack.isEmpty());
        if (NodeUtil.referencesEnclosingReceiver(n)) {
          t.report(n, TranspilationUtil.CANNOT_CONVERT_YET, "Member references this or super");
          classStack.peek().cannotConvert = true;
          break;
        }
        classStack.peek().recordField(n);
        break;
      case BLOCK:
        if (!NodeUtil.isClassStaticBlock(n)) {
          break;
        }
        if (NodeUtil.referencesEnclosingReceiver(n)) {
          t.report(n, TranspilationUtil.CANNOT_CONVERT_YET, "Member references this or super");
          classStack.peek().cannotConvert = true;
          break;
        }
        classStack.peek().recordStaticBlock(n);
        break;
      default:
        break;
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isClass()) {
      visitClass(t);
    }
  }

  /** Transpile the actual class members themselves */
  private void visitClass(NodeTraversal t) {
    ClassRecord currClassRecord = classStack.pop();
    if (currClassRecord.cannotConvert) {
      return;
    }

    if (!NodeUtil.isClassDeclaration(currClassRecord.classNode)) {
      t.report(
          currClassRecord.classNode,
          TranspilationUtil.CANNOT_CONVERT_YET,
          "Not a class declaration");
      return;
    }

    rewriteInstanceMembers(t, currClassRecord);
    rewriteStaticMembers(t, currClassRecord);
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
    Node insertionPoint = findInitialInstanceInsertionPoint(ctorBlock);

    while (!instanceMembers.isEmpty()) {
      Node instanceMember = instanceMembers.pop();
      checkState(instanceMember.isMemberFieldDef());

      Node thisNode = astFactory.createThisForEs6ClassMember(instanceMember);

      Node transpiledNode = convNonCompFieldToGetProp(thisNode, instanceMember.detach());
      if (insertionPoint == ctorBlock) { // insert the field at the beginning of the block, no super
        ctorBlock.addChildToFront(transpiledNode);
      } else {
        transpiledNode.insertAfter(insertionPoint);
      }
      t.reportCodeChange(); // we moved the field from the class body
      t.reportCodeChange(ctorBlock); // to the constructor, so we need both
    }
  }

  /** Rewrites and moves all static blocks and fields */
  private void rewriteStaticMembers(NodeTraversal t, ClassRecord record) {
    Deque<Node> staticMembers = record.staticMembers;

    Node classNameNode = NodeUtil.getNameNode(record.classNode);
    Node classInsertionPoint = NodeUtil.getEnclosingStatement(record.classNode);

    while (!staticMembers.isEmpty()) {
      Node staticMember = staticMembers.pop();

      Node transpiledNode;
      switch (staticMember.getToken()) {
        case BLOCK:
          if (!NodeUtil.getVarsDeclaredInBranch(staticMember).isEmpty()) {
            t.report(staticMember, TranspilationUtil.CANNOT_CONVERT_YET, "Var in static block");
          }
          transpiledNode = staticMember.detach();
          break;
        case MEMBER_FIELD_DEF:
          transpiledNode =
              convNonCompFieldToGetProp(classNameNode.cloneNode(), staticMember.detach());
          break;
        default:
          throw new IllegalStateException(String.valueOf(staticMember));
      }
      transpiledNode.insertAfter(classInsertionPoint);
      t.reportCodeChange();
    }
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

  /**
   * Finds the location in the constructor to put the transpiled instance fields
   *
   * <p>Returns the constructor body if there is no super() call so the field can be put at the
   * beginning of the class
   *
   * <p>Returns the super() call otherwise so the field can be put after the super() call
   */
  private Node findInitialInstanceInsertionPoint(Node ctorBlock) {
    if (NodeUtil.referencesSuper(ctorBlock)) {
      // will use the fact that if there is super in the constructor, the first appearance of
      // super
      // must be the super call
      for (Node stmt = ctorBlock.getFirstChild(); stmt != null; stmt = stmt.getNext()) {
        if (NodeUtil.isExprCall(stmt) && stmt.getFirstFirstChild().isSuper()) {
          return stmt;
        }
      }
    }
    return ctorBlock; // in case the super loop doesn't work, insert at beginning of block
  }

  /**
   * Accumulates information about different classes while going down the AST in shouldTraverse()
   */
  private static final class ClassRecord {
    boolean cannotConvert = false;
    final Deque<Node> instanceMembers =
        new ArrayDeque<>(); // instance computed + noncomputed fields
    final Deque<Node> staticMembers =
        new ArrayDeque<>(); // static blocks + computed + noncomputed fields
    final Node classNode;

    private ClassRecord(Node classNode) {
      this.classNode = classNode;
    }

    private void recordField(Node field) {
      checkArgument(field.isComputedFieldDef() || field.isMemberFieldDef());
      if (field.isStaticMember()) {
        staticMembers.push(field);
      } else {
        instanceMembers.push(field);
      }
    }

    private void recordStaticBlock(Node block) {
      checkArgument(NodeUtil.isClassStaticBlock(block));
      staticMembers.push(block);
    }
  }
}
