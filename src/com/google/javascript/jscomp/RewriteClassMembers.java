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
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;
import java.util.ArrayDeque;
import java.util.Deque;
import org.jspecify.nullness.Nullable;

/** Replaces the ES2022 class fields and class static blocks with constructor declaration. */
public final class RewriteClassMembers implements NodeTraversal.ScopedCallback, CompilerPass {

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

        if (classNameNode == null) {
          t.report(
              n, TranspilationUtil.CANNOT_CONVERT_YET, "Anonymous classes with ES2022 features");
          return false;
        }

        @Nullable Node classInsertionPoint = getStatementDeclaringClass(n, classNameNode);

        if (classInsertionPoint == null) {
          t.report(
              n,
              TranspilationUtil.CANNOT_CONVERT_YET,
              "Class in a non-extractable location with ES2022 features");
          return false;
        }

        if (!n.getFirstChild().isEmpty()
            && !classNameNode.matchesQualifiedName(n.getFirstChild())) {
          // we do not allow `let x = class C {}` where the names inside the class can be shadowed
          // at this time
          t.report(n, TranspilationUtil.CANNOT_CONVERT_YET, "Classes with possible name shadowing");
          return false;
        }

        classStack.push(new ClassRecord(n, classNameNode.getQualifiedName(), classInsertionPoint));
        break;
      case COMPUTED_FIELD_DEF:
        checkState(!classStack.isEmpty());
        t.report(n, TranspilationUtil.CANNOT_CONVERT_YET, "Computed fields");
        classStack.peek().cannotConvert = true;
        return false;
      case MEMBER_FIELD_DEF:
        checkState(!classStack.isEmpty());
        classStack.peek().enterField(n);
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
        checkState(!classStack.isEmpty());
        classStack.peek().recordStaticBlock(n);
        break;
      case NAME:
        for (ClassRecord record : classStack) {
          // For now, we are just processing these names as strings, and so we will also give
          // CANNOT_CONVERT_YET errors for patterns that technically can be simply inlined, such as:
          // class C {
          //    y = (x) => x;
          //    constructor(x) {}
          // }
          // Either using scopes to be more precise or just doing renaming for all conflicting
          // constructor declarations would address this issue.
          record.potentiallyRecordNameInRhs(n);
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
        visitClass(t);
        return;
      case MEMBER_FIELD_DEF:
        classStack.peek().exitField();
        return;
      case THIS:
        Node rootNode = t.getClosestScopeRootNodeBindingThisOrSuper();
        if (rootNode.isMemberFieldDef() && rootNode.isStaticMember()) {
          Node className = rootNode.getGrandparent().getFirstChild().cloneNode();
          n.replaceWith(className);
          t.reportCodeChange(className);
        }
        return;
      case SUPER:
        rootNode = t.getClosestScopeRootNodeBindingThisOrSuper();
        if (rootNode.isMemberFieldDef() && rootNode.isStaticMember()) {
          Node superclassName = rootNode.getGrandparent().getChildAtIndex(1).cloneNode();
          n.replaceWith(superclassName);
          t.reportCodeChange(superclassName);
        }
        return;
      default:
        return;
    }
  }

  /** Transpile the actual class members themselves */
  private void visitClass(NodeTraversal t) {
    ClassRecord currClassRecord = classStack.pop();
    if (currClassRecord.cannotConvert) {
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
    ImmutableSet<String> ctorDefinedNames = record.getConstructorDefinedNames();

    while (!instanceMembers.isEmpty()) {
      Node instanceMember = instanceMembers.pop();
      checkState(instanceMember.isMemberFieldDef());

      for (Node nameInRhs : record.referencedNamesByMember.get(instanceMember)) {
        String name = nameInRhs.getString();
        if (ctorDefinedNames.contains(name)) {
          t.report(
              nameInRhs,
              TranspilationUtil.CANNOT_CONVERT_YET,
              "Initializer referencing identifier '" + name + "' declared in constructor");
          return;
        }
      }

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

    while (!staticMembers.isEmpty()) {
      Node staticMember = staticMembers.pop();
      // if the name is a property access, we want the whole chain of accesses, while for other
      // cases we only want the name node
      Node nameToUse =
          astFactory.createQNameWithUnknownType(record.classNameString).srcrefTree(staticMember);

      Node transpiledNode;

      switch (staticMember.getToken()) {
        case BLOCK:
          if (!NodeUtil.getVarsDeclaredInBranch(staticMember).isEmpty()) {
            t.report(staticMember, TranspilationUtil.CANNOT_CONVERT_YET, "Var in static block");
          }
          transpiledNode = staticMember.detach();
          break;
        case MEMBER_FIELD_DEF:
          transpiledNode = convNonCompFieldToGetProp(nameToUse, staticMember.detach());
          break;
        default:
          throw new IllegalStateException(String.valueOf(staticMember));
      }
      transpiledNode.insertAfter(record.classInsertionPoint);
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
    // During traversal, contains the current member being traversed. After traversal, always null
    @Nullable Node currentMember;
    boolean cannotConvert;

    // Instance fields
    final Deque<Node> instanceMembers = new ArrayDeque<>();
    // Static fields + static blocks
    final Deque<Node> staticMembers = new ArrayDeque<>();

    // Mapping from MEMBER_FIELD_DEF (& COMPUTED_FIELD_DEF) nodes to all name nodes in that RHS
    final SetMultimap<Node, Node> referencedNamesByMember =
        MultimapBuilder.linkedHashKeys().hashSetValues().build();
    // Set of all the Vars defined in the constructor arguments scope and constructor body scope
    ImmutableSet<Var> constructorVars = ImmutableSet.of();

    final Node classNode;
    final String classNameString;
    final Node classInsertionPoint;

    ClassRecord(Node classNode, String classNameString, Node classInsertionPoint) {
      this.classNode = classNode;
      this.classNameString = classNameString;
      this.classInsertionPoint = classInsertionPoint;
    }

    void enterField(Node field) {
      checkArgument(field.isComputedFieldDef() || field.isMemberFieldDef());
      if (field.isStaticMember()) {
        staticMembers.push(field);
      } else {
        instanceMembers.push(field);
      }
      currentMember = field;
    }

    void exitField() {
      currentMember = null;
    }

    void recordStaticBlock(Node block) {
      checkArgument(NodeUtil.isClassStaticBlock(block));
      staticMembers.push(block);
    }

    void potentiallyRecordNameInRhs(Node nameNode) {
      checkArgument(nameNode.isName());
      if (currentMember == null) {
        return;
      }
      checkState(currentMember.isMemberFieldDef());
      referencedNamesByMember.put(currentMember, nameNode);
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

    ImmutableSet<String> getConstructorDefinedNames() {
      return constructorVars.stream().map(Var::getName).collect(toImmutableSet());
    }
  }
}
