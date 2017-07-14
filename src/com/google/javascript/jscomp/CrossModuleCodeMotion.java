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

import com.google.common.base.Objects;
import com.google.javascript.jscomp.CrossModuleReferenceCollector.TopLevelStatement;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A compiler pass for moving code to a deeper module if possible.
 * - currently it only moves functions + variables
 *
 */
class CrossModuleCodeMotion implements CompilerPass {

  private static final Logger logger =
      Logger.getLogger(CrossModuleCodeMotion.class.getName());

  private final AbstractCompiler compiler;
  private final JSModuleGraph graph;

  /**
   * Map from module to the node in that module that should parent any string
   * variable declarations that have to be moved into that module
   */
  private final Map<JSModule, Node> moduleVarParentMap =
      new HashMap<>();

  /*
   * NOTE - I made this a LinkedHashMap to make testing easier. With a regular
   * HashMap, the variables may not output in a consistent order
   */
  private final Map<Var, NamedInfo> namedInfo =
      new LinkedHashMap<>();

  private final Map<Node, InstanceofInfo> instanceofNodes =
      new LinkedHashMap<>();

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
    this.parentModuleCanSeeSymbolsDeclaredInChildren =
        parentModuleCanSeeSymbolsDeclaredInChildren;
  }

  @Override
  public void process(Node externs, Node root) {
    logger.fine("Moving functions + variable into deeper modules");

    // If there are <2 modules, then we will never move anything, so we're done
    if (graph != null && graph.getModuleCount() > 1) {

      // Traverse the tree and find the modules where a var is declared + used
      collectReferences(root);

      // Move the functions + variables to a deeper module [if possible]
      moveCode();

      // Make is so we can ignore constructor references in instanceof.
      if (parentModuleCanSeeSymbolsDeclaredInChildren) {
        addInstanceofGuards();
      }
    }
  }

  /** move the code accordingly */
  private void moveCode() {
    for (NamedInfo info : namedInfo.values()) {
      if (info.shouldBeMoved()) {
        // Find the appropriate spot to move it to
        JSModule preferredModule = info.getPreferredModule();
        Node destParent = moduleVarParentMap.get(preferredModule);
        if (destParent == null) {
          destParent = compiler.getNodeForCodeInsertion(preferredModule);
          moduleVarParentMap.put(preferredModule, destParent);
        }
        for (TopLevelStatement declaringStatement : info.movableDeclaringStatementStack) {
          Node statementNode = declaringStatement.getStatementNode();

          // Remove it
          compiler.reportChangeToEnclosingScope(statementNode);
          statementNode.detach();

          // Add it to the new spot
          destParent.addChildToFront(statementNode);

          compiler.reportChangeToEnclosingScope(statementNode);
        }
        // Update variable declaration location.
        info.wasMoved = true;
        info.declModule = preferredModule;
      }
    }
  }

  /** useful information for each variable candidate */
  private class NamedInfo {
    private boolean allowMove = true;

    // Indices of all modules referring to the global name.
    private BitSet modulesWithReferences = null;

    // A place to stash the results of getPreferredModule() to avoid recalculating it unnecessarily.
    private JSModule preferredModule = null;

    // The module where declarations appear
    private JSModule declModule = null;

    private boolean wasMoved = false;

    /** Stack of declaring statements. Last in is first to be moved. */
    private final Deque<TopLevelStatement> movableDeclaringStatementStack = new ArrayDeque<>();

    void addMovableDeclaringStatement(TopLevelStatement declaringStatement) {
      // Ignore declaring statements once we've decided we cannot move them.
      if (allowMove) {
        if (modulesWithReferences != null) {
          // We've already started seeing non-declaration references, so we cannot actually
          // move this statement and must treat it like a non-declaration reference.
          addReferringStatement(declaringStatement);
        } else if (declModule == null) {
          declModule = declaringStatement.getModule();
          movableDeclaringStatementStack.push(declaringStatement);
        } else if (declModule.equals(declaringStatement.getModule())) {
          movableDeclaringStatementStack.push(declaringStatement);
        } else {
          // Cannot move declarations not in the same module with the first declaration.
          addReferringStatement(declaringStatement);
        }
      }
    }

    void addReferringStatement(TopLevelStatement referringStatement) {
      // Ignore referring statements if we cannot move declaration statements anyway.
      if (allowMove) {
        if (declModule == null) {
          // First reference we see is not a declaration, so we cannot move any declaration
          // statements.
          allowMove = false;
        } else if (referringStatement.getModule().equals(declModule)) {
          // The first non-declaration reference we see is in the same module as the declaration
          // statements, so we cannot move them.
          allowMove = false;
          movableDeclaringStatementStack.clear();  // save some memory
          modulesWithReferences = null;
        } else {
          addUsedModule(referringStatement.getModule());
        }
      }
    }

    // Add a Module where it is used
    private void addUsedModule(JSModule m) {
      if (modulesWithReferences == null) {
        // first call to this method
        modulesWithReferences = new BitSet(graph.getModuleCount());
      }
      modulesWithReferences.set(m.getIndex());
      // invalidate preferredModule, so it will be recalculated next time getPreferredModule() is
      // called.
      preferredModule = null;
    }

    /**
     * Returns the root module of a dependency subtree that contains all of the modules which refer
     * to this global name.
     */
    JSModule getPreferredModule() {
      if (preferredModule == null) {
        if (modulesWithReferences == null) {
          // If we saw no references, we must at least have seen a declaration.
          preferredModule = checkNotNull(declModule);
        } else {
          // Note that getSmallestCoveringDependency() will do this:
          // checkState(!modulesWithReferences.isEmpty())
          preferredModule = graph.getSmallestCoveringDependency(modulesWithReferences);
        }
      }
      return preferredModule;
    }

    boolean shouldBeMoved() {
      // Only move if all are true:
      // a) allowMove is true
      // b) it is declared somewhere (declModule != null)
      // c) the all usages depend on the declModule by way of a different, preferred module
      return allowMove && declModule != null && graph.dependsOn(getPreferredModule(), declModule);
    }
  }

  /**
   * get the information on a variable
   */
  private NamedInfo getNamedInfo(Var v) {
    NamedInfo info = namedInfo.get(v);
    if (info == null) {
      info = new NamedInfo();
      namedInfo.put(v, info);
    }
    return info;
  }

  private void collectReferences(Node root) {
    CrossModuleReferenceCollector collector = new CrossModuleReferenceCollector(
        compiler,
        new Es6SyntacticScopeCreator(compiler));
    collector.process(root);

    for (TopLevelStatement statement : collector.getTopLevelStatements()) {
      Var declaredVar = null;
      if (statement.isDeclarationStatement()) {
        declaredVar = statement.getDeclaredNameReference().getSymbol();
        NamedInfo declaredNameInfo = getNamedInfo(declaredVar);
        if (statement.isMovableDeclaration()) {
          declaredNameInfo.addMovableDeclaringStatement(statement);
        } else {
          // It's a declaration, but not movable, so treat its as a non-declaration reference.
          declaredNameInfo.addReferringStatement(statement);
        }
      }
      for (Reference ref : statement.getNonDeclarationReferences()) {
        Var v = ref.getSymbol();
        // ignore recursive references
        if (!Objects.equal(declaredVar, v)) {
          NamedInfo info = getNamedInfo(v);
          if (!parentModuleCanSeeSymbolsDeclaredInChildren) {
            // Modules are loaded in such a way that Foo really must be defined before any
            // expressions like `x instanceof Foo` are evaluated.
            info.addReferringStatement(statement);
          } else {
            Node n = ref.getNode();
            if (isUnguardedInstanceofReference(n)) {
              // Save a list of unguarded instanceof references.
              // We'll add undefined typeof guards to them instead of allowing them to block code
              // motion.
              instanceofNodes.put(n.getParent(), new InstanceofInfo(getModule(ref), info));
            } else if (!(isUndefinedTypeofGuardReference(n) || isGuardedInstanceofReference(n))) {
              // Ignore `'undefined' != typeof Ref && x instanceof Ref`
              // Otherwise, it's a read
              info.addReferringStatement(statement);
            }
          }
        }
      }
    }
  }

  /**
   * Is the reference node the first {@code Ref} in an expression like
   * {@code 'undefined' != typeof Ref && x instanceof Ref}?
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
   * Is the reference node the second {@code Ref} in an expression like
   * {@code 'undefined' != typeof Ref && x instanceof Ref}?
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

  private JSModule getModule(Reference ref) {
    return compiler.getInput(ref.getInputId()).getModule();
  }

  /**
   * Transforms instanceof usages into an expression that short circuits to false if tested with a
   * constructor that is undefined. This allows ignoring instanceof with respect to cross module
   * code motion.
   */
  private void addInstanceofGuards() {
    Node tmp = IR.block();
    for (Map.Entry<Node, InstanceofInfo> entry : instanceofNodes.entrySet()) {
      Node n = entry.getKey();
      InstanceofInfo info = entry.getValue();
      // No need for a guard if:
      // 1. the declaration wasn't moved
      // 2. OR it was moved to the start of the module containing this instanceof reference
      // 3. OR it was moved to a module the instanceof reference's module depends on
      if (!info.namedInfo.wasMoved
          || info.namedInfo.declModule.equals(info.module)
          || graph.dependsOn(info.module, info.namedInfo.declModule)) {
        continue;
      }
      // Wrap "foo instanceof Bar" in
      // "('undefined' != typeof Bar && foo instanceof Bar)"
      Node originalReference = n.getLastChild();
      checkState(
          isUnguardedInstanceofReference(originalReference),
          "instanceof Reference is already guarded: %s",
          originalReference);
      Node reference = originalReference.cloneNode();
      checkState(reference.isName());
      n.replaceWith(tmp);
      Node and = IR.and(
          new Node(Token.NE,
              IR.string("undefined"),
              new Node(Token.TYPEOF, reference)
          ),
          n
      );
      and.useSourceInfoIfMissingFromForTree(n);
      tmp.replaceWith(and);
      compiler.reportChangeToEnclosingScope(and);
    }
  }

  private static class InstanceofInfo {
    private final JSModule module;
    private final NamedInfo namedInfo;

    InstanceofInfo(JSModule module, NamedInfo namedInfo) {
      this.module = checkNotNull(module);
      this.namedInfo = checkNotNull(namedInfo);
    }
  }
}
