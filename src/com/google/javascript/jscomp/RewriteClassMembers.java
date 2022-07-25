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

import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;
import javax.annotation.Nullable;

/** Replaces the ES2022 class fields and class static blocks with constructor declaration. */
public final class RewriteClassMembers implements NodeTraversal.Callback, CompilerPass {

  private final AbstractCompiler compiler;
  private final AstFactory astFactory;
  private final SynthesizeExplicitConstructors ctorCreator;

  public RewriteClassMembers(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
    this.ctorCreator = new SynthesizeExplicitConstructors(compiler);
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(
        compiler, Feature.PUBLIC_CLASS_FIELDS, Feature.CLASS_STATIC_BLOCK);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    if (n.isScript()) {
      FeatureSet scriptFeatures = NodeUtil.getFeatureSetOfScript(n);
      return scriptFeatures == null
          || scriptFeatures.contains(Feature.PUBLIC_CLASS_FIELDS)
          || scriptFeatures.contains(Feature.CLASS_STATIC_BLOCK);
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isClass()) {
      visitClass(t, n);
    }
  }

  /** Transpile the actual class members themselves */
  private void visitClass(NodeTraversal t, Node classNode) {
    if (!NodeUtil.isClassDeclaration(classNode)) {
      t.report(classNode, Es6ToEs3Util.CANNOT_CONVERT_YET, "Not a class declaration");
      return;
    }
    Node classNameNode = NodeUtil.getNameNode(classNode);
    Node staticInsertionPoint = classNode;
    @Nullable Node instanceInsertionPoint = null;
    Node classMembers = classNode.getLastChild();

    Node next;
    for (Node member = classMembers.getFirstChild(); member != null; member = next) {
      next = member.getNext();
      // this next is necessary because we directly move static blocks so we will get the incorrect
      // element if just update member in the loop guard
      if (member.isMemberFieldDef()) {
        if (member.isStaticMember()) {
          staticInsertionPoint =
              visitNoncomputedStaticField(t, member, staticInsertionPoint, classNameNode);
        } else {
          ctorCreator.synthesizeClassConstructorIfMissing(t, classNode);
          Node ctor = NodeUtil.getEs6ClassConstructorMemberFunctionDef(classNode);
          Node ctorBlock = ctor.getFirstChild().getLastChild();
          instanceInsertionPoint =
              visitNoncomputedInstanceField(t, member, instanceInsertionPoint, ctorBlock);
        }
      } else if (member.isComputedFieldDef()) {
        t.report(member, Es6ToEs3Util.CANNOT_CONVERT_YET, "Computed fields");
      } else if (NodeUtil.isClassStaticBlock(member)) {
        staticInsertionPoint = visitClassStaticBlock(t, member, staticInsertionPoint);
      }
    }
  }

  /** Handles the transpilation of class static blocks */
  private Node visitClassStaticBlock(NodeTraversal t, Node staticBlock, Node insertionPoint) {
    // TODO(b/235871861): replace all this and super inside the static block
    if (NodeUtil.referencesEnclosingReceiver(staticBlock)) {
      t.report(staticBlock, Es6ToEs3Util.CANNOT_CONVERT_YET, "This or super in static block");
      return insertionPoint;
    }
    if (!NodeUtil.getVarsDeclaredInBranch(staticBlock).isEmpty()) {
      t.report(staticBlock, Es6ToEs3Util.CANNOT_CONVERT_YET, "Var in static block");
      return insertionPoint;
    }
    staticBlock.detach();
    staticBlock.insertAfter(insertionPoint);
    t.reportCodeChange();
    return staticBlock;
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

  private Node visitNoncomputedStaticField(
      NodeTraversal t, Node staticField, Node insertionPoint, Node classNameNode) {
    if (NodeUtil.referencesEnclosingReceiver(staticField)) {
      t.report(staticField, Es6ToEs3Util.CANNOT_CONVERT_YET, "This or super in static field");
      return insertionPoint;
    }
    classNameNode = classNameNode.cloneNode();
    staticField.detach();
    Node result = convNonCompFieldToGetProp(classNameNode, staticField);
    result.insertAfter(insertionPoint);
    t.reportCodeChange();
    return result;
  }

  private Node visitNoncomputedInstanceField(
      NodeTraversal t, Node instanceField, @Nullable Node insertionPoint, Node ctorBlock) {
    if (NodeUtil.referencesEnclosingReceiver(instanceField)) {
      t.report(instanceField, Es6ToEs3Util.CANNOT_CONVERT_YET, "This or super in instance field");
      return insertionPoint;
    }
    Node thisNode = astFactory.createThisForEs6ClassMember(instanceField);
    instanceField.detach();
    Node result = convNonCompFieldToGetProp(thisNode, instanceField);
    if (insertionPoint == null) {
      insertionPoint = findInitialInstanceInsertionPoint(ctorBlock);
    }
    if (insertionPoint == ctorBlock) { // insert the field at the beginning of the block, no super
      ctorBlock.addChildToFront(result);
    } else {
      result.insertAfter(insertionPoint);
    }
    t.reportCodeChange(); // we moved the field from the class body
    t.reportCodeChange(ctorBlock); // to the constructor, so we need both

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
      // will use the fact that if there is super in the constructor, the first appearance of super
      // must be the super call
      for (Node stmt = ctorBlock.getFirstChild(); stmt != null; stmt = stmt.getNext()) {
        if (NodeUtil.isExprCall(stmt) && stmt.getFirstFirstChild().isSuper()) {
          return stmt;
        }
      }
    }
    return ctorBlock; // in case the super loop doesn't work, insert at beginning of block
  }
}
