/*
 * Copyright 2008 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.CrossModuleReferenceCollector.TopLevelStatement;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A compiler pass for moving global variable declarations and assignments to their properties to a
 * deeper module if possible.
 */
class CrossModuleCodeMotion implements CompilerPass {

  private final AbstractCompiler compiler;
  private final JSModuleGraph graph;

  /**
   * Map from module to the node in that module that should parent variable declarations that have
   * to be moved into that module
   */
  private final Map<JSModule, Node> moduleInsertionPointMap = new HashMap<>();

  private final boolean parentModuleCanSeeSymbolsDeclaredInChildren;

  /**
   * Creates an instance.
   *
   * @param compiler The compiler
   */
  CrossModuleCodeMotion(
      AbstractCompiler compiler,
      JSModuleGraph graph,
      boolean parentModuleCanSeeSymbolsDeclaredInChildren) {
    this.compiler = compiler;
    this.graph = graph;
    this.parentModuleCanSeeSymbolsDeclaredInChildren = parentModuleCanSeeSymbolsDeclaredInChildren;
  }

  @Override
  public void process(Node externs, Node root) {
    // If there are <2 modules, then we will never move anything, so we're done
    if (graph != null && graph.getModuleCount() > 1) {
      CrossModuleReferenceCollector referenceCollector =
          new CrossModuleReferenceCollector(compiler, new Es6SyntacticScopeCreator(compiler));
      referenceCollector.process(root);
      Deque<DeclarationStatementGroup> declarationStatementGroups =
          new DeclarationStatementGroupCollector()
              .collectDeclarationStatementGroups(referenceCollector);
      moveDeclarationStatementGroups(declarationStatementGroups);
    }
  }

  private class DeclarationStatementGroupCollector {

    /** Stack to fill with DeclarationStatementGroups as they are discovered. */
    final Deque<DeclarationStatementGroup> allDsgsStack = new ArrayDeque<>();
    /**
     * Keep a stack of DeclarationStatementGroups for each global variable. As we traverse the
     * statements in execution order order the top of the stack represents the most recently seen
     * DSG for the variable.
     */
    final Map<Var, Deque<DeclarationStatementGroup>> dsgStackForVar = new HashMap<>();

    Deque<DeclarationStatementGroup> collectDeclarationStatementGroups(
        CrossModuleReferenceCollector referenceCollector) {

      for (TopLevelStatement statement : referenceCollector.getTopLevelStatements()) {
        if (statement.isDeclarationStatement()) {
          DeclarationStatementGroup statementDsg = addStatementToDsg(statement);
          for (Reference ref : statement.getNonDeclarationReferences()) {
            processReferenceFromDsg(statement, statementDsg, ref);
          }
        } else {
          for (Reference ref : statement.getNonDeclarationReferences()) {
            processImmovableReference(statement, ref);
          }
        }
      }
      return allDsgsStack;
    }

    /**
     * Finds or creates the DeclarationStatementGroup appropriate for this statement, adds the
     * statement to it and returns the DSG.
     */
    private DeclarationStatementGroup addStatementToDsg(TopLevelStatement statement) {
      DeclarationStatementGroup statementDsg;
      Var declaredVar = statement.getDeclaredNameReference().getSymbol();
      Deque<DeclarationStatementGroup> declaredVarDsgStack =
          getVarDsgStack(declaredVar, statement.getModule());
      DeclarationStatementGroup lastDsg = declaredVarDsgStack.peek();
      if (lastDsg.currentModule.equals(statement.getModule())) {
        // Still in the same module and no intervening declarations since the last declaration
        // for this variable group.
        statementDsg = lastDsg;
        statementDsg.statementStack.push(statement);
      } else {
        // New module requires a new DSG
        statementDsg = new DeclarationStatementGroup(statement);
        // New DSG depends on the previous one
        statementDsg.referencedStatementGroups.add(lastDsg);
        lastDsg.referencingStatementGroups.add(statementDsg);
        declaredVarDsgStack.push(statementDsg);
        allDsgsStack.push(statementDsg);
      }
      return statementDsg;
    }

    private void processReferenceFromDsg(
        TopLevelStatement statement, DeclarationStatementGroup statementDsg, Reference ref) {
      Var refVar = ref.getSymbol();
      Deque<DeclarationStatementGroup> rdsgStack = getVarDsgStack(refVar, statement.getModule());
      DeclarationStatementGroup rdsg = rdsgStack.peek();

      if (rdsg == statementDsg) {
        return;
      } else if (parentModuleCanSeeSymbolsDeclaredInChildren) {
        // It is possible to move the declaration of `Foo` after
        // `'undefined' != typeof Foo && x instanceof Foo`.
        // We'll add the undefined check, if necessary.
        Node n = ref.getNode();
        if (isGuardedInstanceofReference(n) || isUndefinedTypeofGuardReference(n)) {
          return;
        } else if (isUnguardedInstanceofReference(n)) {
          // add a guard, if rdsg moves
          InstanceofReference instanceofReference =
              new InstanceofReference(statementDsg.currentModule, ref);
          rdsg.instanceofReferencesToGuard.push(instanceofReference);
          statementDsg.containedUnguardedInstanceofReferences.push(instanceofReference);
          return;
        }
      }

      // reference restricts movement for all DSGs for the referenced variable
      for (DeclarationStatementGroup varDsg : rdsgStack) {
        // varDsg must be updated when this statement moves
        statementDsg.referencedStatementGroups.add(varDsg);
        varDsg.referencingStatementGroups.add(statementDsg);
      }
    }

    /**
     * Get the DeclarationStatementGroup stack containing DSGs for the given Var.
     *
     * <p>If no stack exists, this means we're seeing the variable for the first time, so we create
     * a stack with a single DSG for the current module.
     *
     * @param refVar the variable
     * @param currentModule the module whose statements we're currently examining.
     * @return a new or existing stack of DSGs for the given variable
     */
    private Deque<DeclarationStatementGroup> getVarDsgStack(Var refVar, JSModule currentModule) {
      Deque<DeclarationStatementGroup> rdsgStack = dsgStackForVar.get(refVar);
      if (rdsgStack == null) {
        // First reference is in a non-declaration statement.
        // Create an empty DSG for this reference, which may be filled in if we find later
        // declaration statements for it in the same module.
        rdsgStack = new ArrayDeque<>();
        dsgStackForVar.put(refVar, rdsgStack);
        DeclarationStatementGroup rdsg = new DeclarationStatementGroup(currentModule);
        rdsgStack.push(rdsg);
        allDsgsStack.push(rdsg);
      }
      return rdsgStack;
    }

    private void processImmovableReference(TopLevelStatement statement, Reference ref) {
      Var refVar = ref.getSymbol();
      Deque<DeclarationStatementGroup> rdsgStack = getVarDsgStack(refVar, statement.getModule());
      DeclarationStatementGroup rdsg = rdsgStack.peek();

      if (parentModuleCanSeeSymbolsDeclaredInChildren) {
        // It is possible to move the declaration of `Foo` after
        // `'undefined' != typeof Foo && x instanceof Foo`.
        // We'll add the undefined check, if necessary.
        Node n = ref.getNode();
        if (isGuardedInstanceofReference(n) || isUndefinedTypeofGuardReference(n)) {
          return;
        } else if (isUnguardedInstanceofReference(n)) {
          // add a guard, if rdsg moves
          InstanceofReference instanceofReference =
              new InstanceofReference(statement.getModule(), ref);
          rdsg.instanceofReferencesToGuard.push(instanceofReference);
          return;
        }
      }

      // reference restricts movement for all DSGs for the referenced variable
      for (DeclarationStatementGroup varDsg : rdsgStack) {
        varDsg.addImmovableReference(statement.getModule());
      }
    }
  }

  /**
   * Moves all of the declaration statements that can move to their best possible module location.
   *
   * @param dsgStack DSGs in last to first order
   */
  private void moveDeclarationStatementGroups(Deque<DeclarationStatementGroup> dsgStack) {
    for (DeclarationStatementGroup dsg :
        new OrderAndCombineDeclarationStatementGroups(dsgStack).orderAndCombine()) {
      // Ordering enforces that all DSGs with references to this one will have already moved or
      // been found to be immovable before this one is considered.
      // Their references will have been converted into bits in modulesWithImmovableReferences.
      checkState(dsg.referencingStatementGroups.isEmpty());
      moveDeclarationStatementGroupIfPossible(dsg);

      for (DeclarationStatementGroup referencedDsg : dsg.referencedStatementGroups) {
        // Convert all reference relationships to immovable references to be considered when it's
        // referencedDsg's turn to move.
        referencedDsg.addImmovableReference(dsg.currentModule);
        // Technically the algorithm would still be correct if we left this reference alone,
        // but removing it allows us to include the checkState() above to confirm that we never
        // move groups in the wrong order.
        referencedDsg.referencingStatementGroups.remove(dsg);
      }
      // It would just feel wrong to leave this dsg pointing to ones that no longer point back to
      // it. This also allows for some GC, but probably not much.
      dsg.referencedStatementGroups.clear();
    }
  }

  /**
   * Orders DeclarationStatementGroups so that each DSG appears in the list only after all of the
   * DSGs that contain references to it.
   *
   * <p>DSGs that form cycles are combined into a single DSG. This happens when declarations within
   * a module form cycles by referring to each other.
   *
   * <p>This is an implementation of the path-based strong component algorithm as it is described in
   * the Wikipedia article https://en.wikipedia.org/wiki/Path-based_strong_component_algorithm.
   */
  private static class OrderAndCombineDeclarationStatementGroups {
    final Deque<DeclarationStatementGroup> inputDsgs;
    /** Tracks DSGs that may be part of a strongly connected component (reference cycle). */
    final Deque<DeclarationStatementGroup> componentContents = new ArrayDeque<>();
    /** Tracks DSGs that may be the root of a strongly connected component (reference cycle). */
    final Deque<DeclarationStatementGroup> componentRoots = new ArrayDeque<>();
    /** Filled with lists of strongly connected DSGs as they are discovered. */
    final Deque<Deque<DeclarationStatementGroup>> stronglyConnectedDsgs;

    int preorderCounter = 0;

    OrderAndCombineDeclarationStatementGroups(Deque<DeclarationStatementGroup> dsgs) {
      inputDsgs = dsgs;
      stronglyConnectedDsgs = new ArrayDeque<>(dsgs.size());
    }

    public Deque<DeclarationStatementGroup> orderAndCombine() {
      for (DeclarationStatementGroup dsg : inputDsgs) {
        if (dsg.preorderNumber < 0) {
          processDsg(dsg);
        } // else already processed
      }
      // At this point stronglyConnectedDsgs has been filled.
      Deque<DeclarationStatementGroup> results = new ArrayDeque<>(stronglyConnectedDsgs.size());
      for (Deque<DeclarationStatementGroup> dsgs : stronglyConnectedDsgs) {
        DeclarationStatementGroup resultDsg = dsgs.pop();
        while (!dsgs.isEmpty()) {
          DeclarationStatementGroup cycleDsg = dsgs.pop();
          resultDsg.merge(cycleDsg);
        }
        results.add(resultDsg);
      }

      return results;
    }

    /**
     * Determines to which strongly connnected component this DeclarationStatementGroup belongs.
     *
     * <p>Called exactly once for each DSG. When this method returns we will have determined which
     * strongly connnected component contains dsg. Either:
     *
     * <ul>
     *   <li>dsg was the first member of the strongly connected component we encountered, and the
     *       entire component has now been added to stronglyConnectedDsgs.
     *   <li>OR, we encountered the first member of the strongly connected component in an earlier
     *       call to this method on the call stack. In this case the top of componentRoots will be
     *       the first member, and we'll add the component to stronglyConnectedDsgs once execution
     *       has fallen back to the call made with that DSG.
     * </ul>
     */
    void processDsg(DeclarationStatementGroup dsg) {
      // preorderNumber is used to track whether we've already processed a DSG and the order in
      // which they have been processed. It is initially -1.
      checkState(dsg.preorderNumber < 0, "already processed: %s", dsg);
      dsg.preorderNumber = preorderCounter++;
      componentRoots.push(dsg); // could be the start of a new strongly connected component
      componentContents.push(dsg); // could be part of an existing strongly connected component
      for (DeclarationStatementGroup referringDsg : dsg.referencingStatementGroups) {
        if (referringDsg.preorderNumber < 0) {
          processDsg(referringDsg);
        } else {
          if (!referringDsg.hasBeenAssignedToAStronglyConnectedComponent) {
            // This DSG is part of a not-yet-completed strongly connected component.
            // Back off the potential roots stack to the earliest DSG that is part of the
            // component.
            while (componentRoots.peek().preorderNumber > referringDsg.preorderNumber) {
              componentRoots.pop();
            }
          }
        }
      }
      if (componentRoots.peek().equals(dsg)) {
        // After exploring all paths from here, this DSG is still at the top of the potential
        // component roots stack, so this DSG is the root of a strongly connected component.
        componentRoots.pop();
        Deque<DeclarationStatementGroup> stronglyConnectedDsgs = new ArrayDeque<>();
        // Gather this DSG and all of the ones we visited that are part of this component.
        DeclarationStatementGroup connectedDsg;
        do {
          connectedDsg = componentContents.pop();
          stronglyConnectedDsgs.push(connectedDsg);
          connectedDsg.hasBeenAssignedToAStronglyConnectedComponent = true;
        } while (!connectedDsg.equals(dsg));
        this.stronglyConnectedDsgs.add(stronglyConnectedDsgs);
      }
    }
  }

  private void moveDeclarationStatementGroupIfPossible(DeclarationStatementGroup dsg) {
    JSModule preferredModule = dsg.getPreferredModule();
    if (preferredModule == dsg.currentModule) {
      return;
    }
    Node destParent = moduleInsertionPointMap.get(preferredModule);
    if (destParent == null) {
      destParent = compiler.getNodeForCodeInsertion(preferredModule);
      moduleInsertionPointMap.put(preferredModule, destParent);
    }
    for (TopLevelStatement statement : dsg.statementStack) {
      Node statementNode = statement.getStatementNode();

      // Remove it
      compiler.reportChangeToEnclosingScope(statementNode);
      statementNode.detach();

      // Add it to the new spot
      destParent.addChildToFront(statementNode);
      compiler.reportChangeToEnclosingScope(statementNode);
    }
    dsg.currentModule = preferredModule;
    // update any unguarded instanceof references contained in this statement group
    for (InstanceofReference instanceofReference : dsg.containedUnguardedInstanceofReferences) {
      instanceofReference.module = preferredModule;
    }
    guardInstanceofReferences(dsg);
  }

  /**
   * Adds guards to any instanceof references to this statement group that might execute before the
   * statements in this DeclarationStatementGroup.
   */
  private void guardInstanceofReferences(DeclarationStatementGroup dsg) {
    for (InstanceofReference instanceofRef : dsg.instanceofReferencesToGuard) {
      if (!graph.dependsOn(instanceofRef.module, dsg.currentModule)) {
        Node referenceNode = instanceofRef.reference.getNode();
        checkState(
            isUnguardedInstanceofReference(referenceNode),
            "instanceof Reference is already guarded: %s",
            referenceNode);
        Node instanceofNode = checkNotNull(referenceNode.getParent());
        Node referenceForTypeOf = referenceNode.cloneNode();
        Node tmp = IR.block();
        // Wrap "foo instanceof Bar" in
        // "('undefined' != typeof Bar && foo instanceof Bar)"
        instanceofNode.replaceWith(tmp);
        Node and =
            IR.and(
                new Node(
                    Token.NE, IR.string("undefined"), new Node(Token.TYPEOF, referenceForTypeOf)),
                instanceofNode);
        and.useSourceInfoIfMissingFromForTree(instanceofNode);
        tmp.replaceWith(and);
        compiler.reportChangeToEnclosingScope(and);
      }
    }
  }

  /**
   * A group of declaration statements that must be moved (or not) as a group.
   *
   * <p>All of the statements must be in the same module initially. If there are declarations for
   * the same variable in different modules, they will be grouped separately and the later module's
   * group will refer to the earlier one.
   */
  private class DeclarationStatementGroup {
    /** module containing the statements */
    JSModule currentModule;
    /** statements in the group, latest first */
    Deque<TopLevelStatement> statementStack = new ArrayDeque<>();

    BitSet modulesWithImmovableReferences = new BitSet(graph.getModuleCount());

    /**
     * DSGs containing references to this one that aren't covered by modulesWithImmovableReferences.
     */
    Set<DeclarationStatementGroup> referencingStatementGroups = new HashSet<>();
    /**
     * This DSG has references to these that aren't covered by their modulesWithImmovableReferences.
     */
    Set<DeclarationStatementGroup> referencedStatementGroups = new HashSet<>();
    /** Instanceof references that may need to be guarded if this DSG moves. */
    Deque<InstanceofReference> instanceofReferencesToGuard = new ArrayDeque<>();
    /** Unguarded instanceof references that will move with this DSG. */
    Deque<InstanceofReference> containedUnguardedInstanceofReferences = new ArrayDeque<>();
    /** Used by OrderAndCombineDeclarationStatementGroups */
    int preorderNumber = -1;
    /** Used by OrderAndCombineDeclarationStatementGroups */
    boolean hasBeenAssignedToAStronglyConnectedComponent = false;

    /** Creates a DSG for the case where the first reference we see is a declaration. */
    DeclarationStatementGroup(TopLevelStatement initialStatement) {
      this(initialStatement.getModule());
      statementStack.push(initialStatement);
    }

    /**
     * Creates a DSG with no statements for the case where the first reference seen is not a
     * declaration.
     */
    DeclarationStatementGroup(JSModule initialReferenceModule) {
      this.currentModule = initialReferenceModule;
    }

    void addImmovableReference(JSModule module) {
      modulesWithImmovableReferences.set(module.getIndex());
    }

    JSModule getPreferredModule() {
      if (!allStatementsCanMove()) {
        return currentModule;
      } else if (modulesWithImmovableReferences.isEmpty()) {
        // no information to use to choose a different module
        return currentModule;
      } else {
        return graph.getSmallestCoveringSubtree(currentModule, modulesWithImmovableReferences);
      }
    }

    boolean allStatementsCanMove() {
      for (TopLevelStatement s : statementStack) {
        if (!s.isMovableDeclaration()) {
          return false;
        }
      }
      return true;
    }

    /**
     * Combines the information from another DeclarationStatementGroup into this one, effectively
     * making the other empty.
     */
    void merge(DeclarationStatementGroup other) {
      checkState(
          currentModule.equals(other.currentModule),
          "Attempt to merge declarations from %s and %s",
          currentModule,
          other.currentModule);
      // statement order on the stack must be last statement first according to the original source
      // order.
      statementStack = mergeStatementsLatestFirst(statementStack, other.statementStack);
      modulesWithImmovableReferences.or(other.modulesWithImmovableReferences);
      for (DeclarationStatementGroup rdsg : other.referencingStatementGroups) {
        rdsg.referencedStatementGroups.remove(other);
        if (!rdsg.equals(this)) {
          referencingStatementGroups.add(rdsg);
          rdsg.referencedStatementGroups.add(this);
        }
      }
      other.referencingStatementGroups.clear();
      for (DeclarationStatementGroup rdsg : other.referencedStatementGroups) {
        rdsg.referencingStatementGroups.remove(other);
        if (!rdsg.equals(this)) {
          referencedStatementGroups.add(rdsg);
          rdsg.referencingStatementGroups.add(this);
        }
      }
      other.referencedStatementGroups.clear();
      instanceofReferencesToGuard.addAll(other.instanceofReferencesToGuard);
      other.instanceofReferencesToGuard.clear();
      containedUnguardedInstanceofReferences.addAll(other.containedUnguardedInstanceofReferences);
      other.containedUnguardedInstanceofReferences.clear();
    }
  }

  /**
   * Creates a new stack of statements by merging two existing ones and maintaining the same
   * last-statement-first order.
   *
   * <p>The input stacks will be cleared.
   */
  private Deque<TopLevelStatement> mergeStatementsLatestFirst(
      Deque<TopLevelStatement> stackA, Deque<TopLevelStatement> stackB) {
    Deque<TopLevelStatement> newStack = new ArrayDeque<>(stackA.size() + stackB.size());
    TopLevelStatement a = stackA.peek();
    TopLevelStatement b = stackB.peek();
    while (true) {
      if (a == null) {
        newStack.addAll(stackB);
        stackB.clear();
        break;
      } else if (b == null) {
        newStack.addAll(stackA);
        stackA.clear();
        break;
      } else if (a.getOriginalOrder() > b.getOriginalOrder()) {
        newStack.add(stackA.pop());
        a = stackA.peek();
      } else {
        newStack.add(stackB.pop());
        b = stackB.peek();
      }
    }
    return newStack;
  }

  private static class InstanceofReference {
    JSModule module;
    Reference reference;

    InstanceofReference(JSModule module, Reference reference) {
      this.module = module;
      this.reference = reference;
    }
  }

  /**
   * Is the reference node the first {@code Ref} in an expression like {@code 'undefined' != typeof
   * Ref && x instanceof Ref}?
   *
   * <p>It's safe to ignore this kind of reference when moving the definition of {@code Ref}.
   */
  private boolean isUndefinedTypeofGuardReference(Node reference) {
    // reference => typeof => `!=`
    Node undefinedTypeofGuard = reference.getGrandparent();
    if (undefinedTypeofGuard != null
        && isUndefinedTypeofGuardFor(undefinedTypeofGuard, reference)) {
      Node andNode = undefinedTypeofGuard.getParent();
      return andNode != null
          && andNode.isAnd()
          && isInstanceofFor(andNode.getLastChild(), reference);
    } else {
      return false;
    }
  }

  /**
   * Is the expression of the form {@code 'undefined' != typeof Ref}?
   *
   * @param expression
   * @param reference Ref node must be equivalent to this node
   */
  private boolean isUndefinedTypeofGuardFor(Node expression, Node reference) {
    if (expression.isNE()) {
      Node undefinedString = expression.getFirstChild();
      Node typeofNode = expression.getLastChild();
      return undefinedString.isString()
          && undefinedString.getString().equals("undefined")
          && typeofNode.isTypeOf()
          && typeofNode.getFirstChild().isEquivalentTo(reference);
    } else {
      return false;
    }
  }

  /**
   * Is the reference node the second {@code Ref} in an expression like {@code 'undefined' != typeof
   * Ref && x instanceof Ref}?
   *
   * <p>It's safe to ignore this kind of reference when moving the definition of {@code Ref}.
   */
  private boolean isGuardedInstanceofReference(Node reference) {
    Node instanceofNode = reference.getParent();
    if (isInstanceofFor(instanceofNode, reference)) {
      Node andNode = instanceofNode.getParent();
      return andNode != null
          && andNode.isAnd()
          && isUndefinedTypeofGuardFor(andNode.getFirstChild(), reference);
    } else {
      return false;
    }
  }

  /** Is the reference the right hand side of an {@code instanceof} and not guarded? */
  private boolean isUnguardedInstanceofReference(Node reference) {
    Node instanceofNode = reference.getParent();
    if (isInstanceofFor(instanceofNode, reference)) {
      Node andNode = instanceofNode.getParent();
      return !(andNode != null
          && andNode.isAnd()
          && isUndefinedTypeofGuardFor(andNode.getFirstChild(), reference));
    } else {
      return false;
    }
  }

  /**
   * Is the expression of the form {@code x instanceof Ref}?
   *
   * @param expression
   * @param reference Ref node must be equivalent to this node
   */
  private boolean isInstanceofFor(Node expression, Node reference) {
    return expression.isInstanceOf() && expression.getLastChild().isEquivalentTo(reference);
  }
}
