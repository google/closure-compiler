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

import com.google.javascript.jscomp.CrossChunkReferenceCollector.TopLevelStatement;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A compiler pass for moving global variable declarations and assignments to their properties to a
 * deeper chunk if possible.
 *
 * <ol>
 *   <li>Collect all global-level statements and the references they contain.
 *   <li>Statements that do not declare global variables are assumed to exist for their side-effects
 *       and are considered immovable.
 *   <li>Statements that declare global variables may be movable. See CrossChunkReferenceCollector.
 *   <li>Within each chunk gather all declarations for a single global variable into a
 *       DeclarationStatementGroup (DSG). Keep track of the references to other globals that appear
 *       in the DSG. A DSG is movable if all of its statements are movable.
 *   <li>Gather the DSGs for each global variable and note all of the chunks that contain immovable
 *       references to it.
 *   <li>The global variables form a directed graph. Global A has an edge to B if A has a DSG with a
 *       reference to B.
 *   <li>Convert this to a directed-acyclic graph by grouping together all of the strongly-connected
 *       global variables into GlobalSymbolCycles. There is an edge from GlobalSymbolCycle A to
 *       GlobalSymbolCycle B if A contains a DSG (in one of its GlobalSymbols) that contains a
 *       reference to a GlobalSymbol in B.
 *   <li>Sort the GlobalSymbolCycles into a list c[1], c[2], c[3],..., c[n], such that there are no
 *       references to GlobalSymbols in c[i] from DSGs in GlobalSymbolCycles c[i+1]...c[n].
 *   <li>Traverse the list in that order, so statements for GlobalSymbol X will only be moved after
 *       all statements that refer to X have already been moved.
 *   <li>Within a GlobalSymbolCycle, combine statements from DSGs in the same chunk and keep them
 *       in the same order when moving them.
 * </ol>
 */
class CrossChunkCodeMotion implements CompilerPass {

  private final AbstractCompiler compiler;
  private final JSModuleGraph graph;

  /**
   * Map from chunk to the node in that chunk that should parent variable declarations that have
   * to be moved into that chunk
   */
  private final Map<JSModule, Node> moduleInsertionPointMap = new HashMap<>();

  private final boolean parentModuleCanSeeSymbolsDeclaredInChildren;

  /**
   * Creates an instance.
   *
   * @param compiler The compiler
   */
  CrossChunkCodeMotion(
      AbstractCompiler compiler,
      JSModuleGraph graph,
      boolean parentModuleCanSeeSymbolsDeclaredInChildren) {
    this.compiler = compiler;
    this.graph = graph;
    this.parentModuleCanSeeSymbolsDeclaredInChildren = parentModuleCanSeeSymbolsDeclaredInChildren;
  }

  @Override
  public void process(Node externs, Node root) {
    // If there are <2 chunks, then we will never move anything, so we're done
    if (graph.getModuleCount() > 1) {
      CrossChunkReferenceCollector referenceCollector =
          new CrossChunkReferenceCollector(compiler, new Es6SyntacticScopeCreator(compiler));
      referenceCollector.process(root);
      Collection<GlobalSymbol> globalSymbols =
          new GlobalSymbolCollector().collectGlobalSymbols(referenceCollector);
      moveGlobalSymbols(globalSymbols);
      addInstanceofGuards(globalSymbols);
    }
  }

  private void addInstanceofGuards(Collection<GlobalSymbol> globalSymbols) {
    for (GlobalSymbol globalSymbol : globalSymbols) {
      for (InstanceofReference instanceofReference : globalSymbol.instanceofReferencesToGuard) {
        if (!globalSymbol.declarationsCoverModule(instanceofReference.getModule())) {
          addGuardToInstanceofReference(instanceofReference.getReference().getNode());
        }
      }
    }
  }

  /** Collects all global symbols, their declaration statements and references. */
  private class GlobalSymbolCollector {

    final Map<Var, GlobalSymbol> globalSymbolforVar = new HashMap<>();

    /**
     * Returning the symbols in the reverse order in which they are defined helps to minimize
     * unnecessary reordering of declaration statements.
     */
    final Deque<GlobalSymbol> symbolStack = new ArrayDeque<>();

    Collection<GlobalSymbol> collectGlobalSymbols(CrossChunkReferenceCollector referenceCollector) {

      for (TopLevelStatement statement : referenceCollector.getTopLevelStatements()) {
        if (statement.isDeclarationStatement()) {
          processDeclarationStatement(statement);
        } else {
          processImmovableStatement(statement);
        }
      }
      return symbolStack;
    }

    private void processImmovableStatement(TopLevelStatement statement) {
      for (Reference ref : statement.getNonDeclarationReferences()) {
        processImmovableReference(ref, statement.getModule());
      }
    }

    private void processImmovableReference(Reference ref, JSModule module) {
      GlobalSymbol globalSymbol = getGlobalSymbol(ref.getSymbol());
      if (parentModuleCanSeeSymbolsDeclaredInChildren) {
        // It is possible to move the declaration of `Foo` after
        // `'undefined' != typeof Foo && x instanceof Foo`.
        // We'll add the undefined check, if necessary.
        Node n = ref.getNode();
        if (isGuardedInstanceofReference(n) || isUndefinedTypeofGuardReference(n)) {
          return;
        } else if (isUnguardedInstanceofReference(n)) {
          ImmovableInstanceofReference instanceofReference =
              new ImmovableInstanceofReference(module, ref);

          globalSymbol.instanceofReferencesToGuard.push(instanceofReference);
          return;
        }
      }
      globalSymbol.addImmovableReference(module);
    }

    private void processDeclarationStatement(TopLevelStatement statement) {
      GlobalSymbol declaredSymbol =
          getGlobalSymbol(statement.getDeclaredNameReference().getSymbol());
      DeclarationStatementGroup dsg = declaredSymbol.addDeclarationStatement(statement);
      processDeclarationStatementContainedReferences(statement, declaredSymbol, dsg);
    }

    private void processDeclarationStatementContainedReferences(
        TopLevelStatement statement, GlobalSymbol declaredSymbol, DeclarationStatementGroup dsg) {
      for (Reference ref : statement.getNonDeclarationReferences()) {
        GlobalSymbol refSymbol = getGlobalSymbol(ref.getSymbol());
        if (refSymbol.equals(declaredSymbol)) {
          continue; // ignore circular reference
        }
        if (parentModuleCanSeeSymbolsDeclaredInChildren) {
          // It is possible to move the declaration of `Foo` after
          // `'undefined' != typeof Foo && x instanceof Foo`.
          // We'll add the undefined check, if necessary.
          Node n = ref.getNode();
          if (isGuardedInstanceofReference(n) || isUndefinedTypeofGuardReference(n)) {
            continue;
          } else if (isUnguardedInstanceofReference(n)) {
            MovableInstanceofReference instanceofReference =
                new MovableInstanceofReference(dsg, ref);

            refSymbol.instanceofReferencesToGuard.push(instanceofReference);
            continue;
          }
        }
        dsg.addReferenceToGlobalSymbol(refSymbol);
        refSymbol.addReferringGlobalSymbol(declaredSymbol);
      }
    }

    private GlobalSymbol getGlobalSymbol(Var var) {
      GlobalSymbol globalSymbol = globalSymbolforVar.get(var);
      if (globalSymbol == null) {
        globalSymbol = new GlobalSymbol(var);
        globalSymbolforVar.put(var, globalSymbol);
        symbolStack.push(globalSymbol);
      }
      return globalSymbol;
    }
  }

  /**
   * Moves all of the declaration statements that can move to their best possible chunk location.
   */
  private void moveGlobalSymbols(Collection<GlobalSymbol> globalSymbols) {
    for (GlobalSymbolCycle globalSymbolCycle :
        new OrderAndCombineGlobalSymbols(globalSymbols).orderAndCombine()) {
      // Symbols whose declarations refer to each other must be grouped together and their
      // declaration statements moved together.
      globalSymbolCycle.moveDeclarationStatements();
    }
  }

  /** Represents a global symbol whose declaration statements may be moved. */
  private class GlobalSymbol {
    final Var var;
    /**
     * As we traverse the statements in execution order the top of the stack represents the most
     * recently seen DSG for the variable.
     */
    final Deque<DeclarationStatementGroup> dsgStack = new ArrayDeque<>();

    final BitSet modulesWithImmovableReferences = new BitSet(graph.getModuleCount());

    /**
     * Symbols whose declaration statements refer to this symbol.
     *
     * This is a LinkedHashSet in order to enforce a consistent ordering when we iterate over these
     * to identify cycles. This guarantees that the order in which statements are moved won't
     * depend on the arbitrary ordering of HashSet.
     */
    final Set<GlobalSymbol> referencingGlobalSymbols = new LinkedHashSet<>();
    /** Instanceof references we may need to update with a guard after moving declarations. */
    Deque<InstanceofReference> instanceofReferencesToGuard = new ArrayDeque<>();
    /** Used by OrderAndCombineGlobalSymbols to find reference cycles. */
    int preorderNumber = -1;
    /** Used by OrderAndCombineGlobalSymbols to find reference cycles. */
    boolean hasBeenAssignedToAStronglyConnectedComponent = false;
    /** Used to confirm all symbols get moved in the correct order. */
    boolean isMoveDeclarationStatementsDone = false;

    GlobalSymbol(Var var) {
      this.var = var;
    }

    @Override
    public String toString() {
      return var.getName();
    }

    void addImmovableReference(JSModule module) {
      modulesWithImmovableReferences.set(module.getIndex());
    }

    /** Adds the statement to the appropriate DeclarationStatementGroup and returns it. */
    DeclarationStatementGroup addDeclarationStatement(TopLevelStatement statement) {
      JSModule module = statement.getModule();
      DeclarationStatementGroup lastDsg = dsgStack.peek();
      if (lastDsg == null) {
        lastDsg = new DeclarationStatementGroup(this, module);
        dsgStack.push(lastDsg);
      }
      DeclarationStatementGroup statementDsg;
      if (module.equals(lastDsg.currentModule)) {
        statementDsg = lastDsg;
      } else {
        // new chunk requires a new DSG
        statementDsg = new DeclarationStatementGroup(this, module);
        dsgStack.push(statementDsg);
      }
      statementDsg.statementStack.push(statement);
      return statementDsg;
    }

    void addReferringGlobalSymbol(GlobalSymbol declaredSymbol) {
      referencingGlobalSymbols.add(declaredSymbol);
    }

    /**
     * Does the chunk depend on at least one of the chunks containing declaration statements for
     * this symbol?
     */
    boolean declarationsCoverModule(JSModule module) {
      for (DeclarationStatementGroup dsg : dsgStack) {
        if (module.equals(dsg.currentModule) || graph.dependsOn(module, dsg.currentModule)) {
          return true;
        }
      }
      return false;
    }
  }

  /**
   * Represents a set of global symbols that all refer to each other, and so must be considered
   * for movement as a group.
   */
  private class GlobalSymbolCycle {
    final Deque<GlobalSymbol> symbols = new ArrayDeque<>();

    void addSymbol(GlobalSymbol symbol) {
      symbols.add(symbol);
    }

    void moveDeclarationStatements() {
      for (GlobalSymbol symbol : symbols) {
        checkState(!symbol.isMoveDeclarationStatementsDone, "duplicate attempt to move %s", symbol);
      }
      BitSet modulesWithImmovableReferences = new BitSet(graph.getModuleCount());
      List<DeclarationStatementGroupCycle> cyclesLatestFirst = getDsgCyclesLatestFirst();
      for (DeclarationStatementGroupCycle dsgCycle : cyclesLatestFirst) {
        // Each move may change modulesWithImmovableReferences
        for (GlobalSymbol symbol : symbols) {
          modulesWithImmovableReferences.or(symbol.modulesWithImmovableReferences);
        }
        dsgCycle.moveToPreferredModule(modulesWithImmovableReferences);
      }
      for (GlobalSymbol symbol : symbols) {
        symbol.isMoveDeclarationStatementsDone = true;
      }
    }

    List<DeclarationStatementGroupCycle> getDsgCyclesLatestFirst() {
      List<DeclarationStatementGroupCycle> cyclesLatestFirst = new ArrayList<>();
      Deque<DeclarationStatementGroup> dsgsLatestFirst = getDsgsLatestFirst();
      DeclarationStatementGroupCycle cycle = null;
      for (DeclarationStatementGroup dsg : dsgsLatestFirst) {
        if (cycle == null || !cycle.currentModule.equals(dsg.currentModule)) {
          cycle = new DeclarationStatementGroupCycle(this, dsg.currentModule);
          cyclesLatestFirst.add(cycle);
        }
        cycle.dsgs.add(dsg);
      }
      return cyclesLatestFirst;
    }

    private Deque<DeclarationStatementGroup> getDsgsLatestFirst() {
      Deque<DeclarationStatementGroup> resultStack = new ArrayDeque<>();
      for (GlobalSymbol symbol : symbols) {
        Deque<DeclarationStatementGroup> stack1 = resultStack;
        Deque<DeclarationStatementGroup> stack2 = new ArrayDeque<>(symbol.dsgStack);
        resultStack = new ArrayDeque<>(stack1.size() + stack2.size());
        while (true) {
          if (stack1.isEmpty()) {
            resultStack.addAll(stack2);
            break;
          } else if (stack2.isEmpty()) {
            resultStack.addAll(stack1);
            break;
          } else {
            DeclarationStatementGroup dsg1 = stack1.peek();
            DeclarationStatementGroup dsg2 = stack2.peek();
            if (dsg1.currentModule.getIndex() > dsg2.currentModule.getIndex()) {
              resultStack.add(stack1.pop());
              checkState(
                  stack1.isEmpty()
                      || stack1.peek().currentModule.getIndex() <= dsg1.currentModule.getIndex(),
                  "DSG stacks are out of order.");
            } else {
              resultStack.add(stack2.pop());
              checkState(
                  stack2.isEmpty()
                      || stack2.peek().currentModule.getIndex() <= dsg2.currentModule.getIndex(),
                  "DSG stacks are out of order.");
            }
          }
        }
      }
      return resultStack;
    }
  }

  private void addGuardToInstanceofReference(Node referenceNode) {
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
            new Node(Token.NE, IR.string("undefined"), new Node(Token.TYPEOF, referenceForTypeOf)),
            instanceofNode);
    and.useSourceInfoIfMissingFromForTree(instanceofNode);
    tmp.replaceWith(and);
    compiler.reportChangeToEnclosingScope(and);
  }

  /**
   * A group of declaration statements that must be moved (or not) as a group.
   *
   * <p>All of the statements must be in the same chunk initially. If there are declarations for
   * the same variable in different chunks, they will be grouped separately.
   */
  private static class DeclarationStatementGroup {

    final GlobalSymbol declaredGlobalSymbol;
    final Set<GlobalSymbol> referencedGlobalSymbols = new HashSet<>();
    /** chunk containing the statements */
    JSModule currentModule;
    /** statements in the group, latest first */
    Deque<TopLevelStatement> statementStack = new ArrayDeque<>();

    DeclarationStatementGroup(GlobalSymbol declaredGlobalSymbol, JSModule currentModule) {
      this.declaredGlobalSymbol = declaredGlobalSymbol;
      this.currentModule = currentModule;
    }

    boolean allStatementsCanMove() {
      for (TopLevelStatement s : statementStack) {
        if (!s.isMovableDeclaration()) {
          return false;
        }
      }
      return true;
    }

    void addReferenceToGlobalSymbol(GlobalSymbol refSymbol) {
      referencedGlobalSymbols.add(refSymbol);
    }

    void makeReferencesImmovable() {
      declaredGlobalSymbol.addImmovableReference(currentModule);
      for (GlobalSymbol symbol : referencedGlobalSymbols) {
        checkState(
            !symbol.isMoveDeclarationStatementsDone,
            "symbol %s moved before referring symbol %s",
            symbol,
            declaredGlobalSymbol);
        symbol.addImmovableReference(currentModule);
      }
    }
  }

  /**
   * Orders DeclarationStatementGroups so that each DSG appears in the list only after all of the
   * DSGs that contain references to it.
   *
   * <p>DSGs that form cycles are combined into a single DSG. This happens when declarations within
   * a chunk form cycles by referring to each other.
   *
   * <p>This is an implementation of the path-based strong component algorithm as it is described in
   * the Wikipedia article https://en.wikipedia.org/wiki/Path-based_strong_component_algorithm.
   */
  private class OrderAndCombineGlobalSymbols {
    final Collection<GlobalSymbol> inputSymbols;
    /** Tracks DSGs that may be part of a strongly connected component (reference cycle). */
    final Deque<GlobalSymbol> componentContents = new ArrayDeque<>();
    /** Tracks DSGs that may be the root of a strongly connected component (reference cycle). */
    final Deque<GlobalSymbol> componentRoots = new ArrayDeque<>();
    /** Filled with lists of strongly connected DSGs as they are discovered. */
    final Deque<GlobalSymbolCycle> stronglyConnectedSymbols;

    int preorderCounter = 0;

    OrderAndCombineGlobalSymbols(Collection<GlobalSymbol> dsgs) {
      inputSymbols = dsgs;
      stronglyConnectedSymbols = new ArrayDeque<>(dsgs.size());
    }

    Deque<GlobalSymbolCycle> orderAndCombine() {
      for (GlobalSymbol globalSymbol : inputSymbols) {
        if (globalSymbol.preorderNumber < 0) {
          processGlobalSymbol(globalSymbol);
        } // else already processed
      }
      // At this point stronglyConnectedSymbols has been filled.
      return stronglyConnectedSymbols;
    }

    /**
     * Determines to which strongly connected component this GlobalSymbol belongs.
     *
     * <p>Called exactly once for each GlobalSymbol. When this method returns we will have
     * determined which strongly connected component contains globalSymbol. Either:
     *
     * <ul>
     *   <li>globalSymbol was the first member of the strongly connected component we encountered,
     *       and the entire component has now been added to stronglyConnectedSymbols.
     *   <li>OR, we encountered the first member of the strongly connected component in an earlier
     *       call to this method on the call stack. In this case the top of componentRoots will be
     *       the first member, and we'll add the component to stronglyConnectedSymbols once
     *       execution has fallen back to the call made with that GlobalSymbol.
     * </ul>
     */
    void processGlobalSymbol(GlobalSymbol symbol) {
      // preorderNumber is used to track whether we've already processed a DSG and the order in
      // which they have been processed. It is initially -1.
      checkState(symbol.preorderNumber < 0, "already processed: %s", symbol);
      symbol.preorderNumber = preorderCounter++;
      componentRoots.push(symbol); // could be the start of a new strongly connected component
      componentContents.push(symbol); // could be part of an existing strongly connected component
      for (GlobalSymbol referringSymbol : symbol.referencingGlobalSymbols) {
        if (referringSymbol.preorderNumber < 0) {
          processGlobalSymbol(referringSymbol);
        } else {
          if (!referringSymbol.hasBeenAssignedToAStronglyConnectedComponent) {
            // This GlobalSymbol is part of a not-yet-completed strongly connected component.
            // Back off the potential roots stack to the earliest symbol that is part of the
            // component.
            while (componentRoots.peek().preorderNumber > referringSymbol.preorderNumber) {
              componentRoots.pop();
            }
          }
        }
      }
      if (componentRoots.peek().equals(symbol)) {
        // After exploring all paths from here, this symbol is still at the top of the potential
        // component roots stack, so this symbol is the root of a strongly connected component.
        componentRoots.pop();
        GlobalSymbolCycle cycle = new GlobalSymbolCycle();
        // this symbol and all those after it on the componentContents stack are part of a single
        // cycle.
        GlobalSymbol connectedSymbol;
        do {
          connectedSymbol = componentContents.pop();
          cycle.addSymbol(connectedSymbol);
          connectedSymbol.hasBeenAssignedToAStronglyConnectedComponent = true;
        } while (!connectedSymbol.equals(symbol));
        this.stronglyConnectedSymbols.add(cycle);
      }
    }
  }

  /**
   * One or more DeclarationStatementGroups that that share the same chunk and whose declared
   * global symbols refer to each other.
   */
  private class DeclarationStatementGroupCycle {
    GlobalSymbolCycle globalSymbolCycle;
    JSModule currentModule;
    Deque<DeclarationStatementGroup> dsgs;

    DeclarationStatementGroupCycle(GlobalSymbolCycle globalSymbolCycle, JSModule currentModule) {
      this.globalSymbolCycle = globalSymbolCycle;
      this.currentModule = currentModule;
      this.dsgs = new ArrayDeque<>();
    }

    void moveToPreferredModule(BitSet modulesWithImmovableReferences) {
      JSModule preferredModule = getPreferredModule(modulesWithImmovableReferences);
      if (!preferredModule.equals(currentModule)) {
        moveStatementsToModule(preferredModule);
      }
      // Now that all the statements have been moved, update the current chunk for all the DSGs
      // and treat all of the references they contain as now immovable.
      for (DeclarationStatementGroup dsg : dsgs) {
        dsg.currentModule = preferredModule;
        dsg.makeReferencesImmovable();
      }
    }

    private void moveStatementsToModule(JSModule preferredModule) {
      Node destParent = moduleInsertionPointMap.get(preferredModule);
      if (destParent == null) {
        destParent = compiler.getNodeForCodeInsertion(preferredModule);
        moduleInsertionPointMap.put(preferredModule, destParent);
      }
      Deque<TopLevelStatement> statementsLastFirst = getStatementsLastFirst();
      for (TopLevelStatement statement : statementsLastFirst) {
        Node statementNode = statement.getStatementNode();
        // Remove it
        compiler.reportChangeToEnclosingScope(statementNode);
        statementNode.detach();

        // Add it to the new spot
        destParent.addChildToFront(statementNode);
        compiler.reportChangeToEnclosingScope(statementNode);
      }
    }

    private Deque<TopLevelStatement> getStatementsLastFirst() {
      Deque<TopLevelStatement> result = new ArrayDeque<>();

      for (DeclarationStatementGroup dsg : dsgs) {
        // combine previous result with statements for current DSG
        Deque<TopLevelStatement> stack1 = new ArrayDeque<>(dsg.statementStack);
        Deque<TopLevelStatement> stack2 = result;
        result = new ArrayDeque<>(stack1.size() + stack2.size());
        while (true) {
          if (stack1.isEmpty()) {
            result.addAll(stack2);
            break;
          } else if (stack2.isEmpty()) {
            result.addAll(stack1);
            break;
          } else {
            TopLevelStatement s1 = stack1.peek();
            TopLevelStatement s2 = stack2.peek();
            if (s1.getOriginalOrder() > s2.getOriginalOrder()) {
              result.add(stack1.pop());
              checkState(
                  stack1.isEmpty() || stack1.peek().getOriginalOrder() < s1.getOriginalOrder(),
                  "Statements are recorded in the wrong order.");
            } else {
              result.add(stack2.pop());
              checkState(
                  stack2.isEmpty() || stack2.peek().getOriginalOrder() < s2.getOriginalOrder(),
                  "Statements are recorded in the wrong order.");
            }
          }
        }
      }
      return result;
    }

    private JSModule getPreferredModule(BitSet modulesWithImmovableReferences) {
      if (modulesWithImmovableReferences.isEmpty()) {
        return currentModule;
      } else if (!allStatementsCanMove()) {
        return currentModule;
      } else {
        return graph.getSmallestCoveringSubtree(currentModule, modulesWithImmovableReferences);
      }
    }

    boolean allStatementsCanMove() {
      for (DeclarationStatementGroup dsg : dsgs) {
        if (!dsg.allStatementsCanMove()) {
          return false;
        }
      }
      return true;
    }
  }

  interface InstanceofReference {
    JSModule getModule();

    Reference getReference();
  }

  private static class ImmovableInstanceofReference implements InstanceofReference {
    JSModule module;
    Reference reference;

    ImmovableInstanceofReference(JSModule module, Reference reference) {
      this.module = module;
      this.reference = reference;
    }

    @Override
    public JSModule getModule() {
      return module;
    }

    @Override
    public Reference getReference() {
      return reference;
    }
  }

  private static class MovableInstanceofReference implements InstanceofReference {
    DeclarationStatementGroup containingDsg;
    Reference reference;

    MovableInstanceofReference(DeclarationStatementGroup containingDsg, Reference reference) {
      this.containingDsg = containingDsg;
      this.reference = reference;
    }

    @Override
    public JSModule getModule() {
      return containingDsg.currentModule;
    }

    @Override
    public Reference getReference() {
      return reference;
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
