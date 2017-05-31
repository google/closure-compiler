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

import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
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
        Iterator<Declaration> it = info.declarationIterator();
        // Find the appropriate spot to move it to
        JSModule preferredModule = info.getPreferredModule();
        Node destParent = moduleVarParentMap.get(preferredModule);
        if (destParent == null) {
          destParent = compiler.getNodeForCodeInsertion(preferredModule);
          moduleVarParentMap.put(preferredModule, destParent);
        }
        while (it.hasNext()) {
          Declaration decl = it.next();
          checkState(decl.module == info.declModule);

          // VAR Nodes are normalized to have only one child.
          Node declParent = decl.node.getParent();
          checkState(
              !declParent.isVar() || declParent.hasOneChild(),
              "AST not normalized.");

          // Remove it
          compiler.reportChangeToEnclosingScope(declParent);
          declParent.detach();

          // Add it to the new spot
          destParent.addChildToFront(declParent);

          compiler.reportChangeToEnclosingScope(declParent);
        }
        // Update variable declaration location.
        info.wasMoved = true;
        info.declModule = preferredModule;
      }
    }
  }

  /** useful information for each variable candidate */
  private class NamedInfo {
    boolean allowMove = true;

    // If movement is allowed, this will be filled with the indices of all modules referring to
    // the global name.
    private BitSet modulesWithReferences = null;

    // A place to stash the results of getPreferredModule() to avoid recalculating it unnecessarily.
    private JSModule preferredModule = null;

    // The module where declarations appear
    private JSModule declModule = null;

    private boolean wasMoved = false;

    // information on the spot where the item was declared
    private final Deque<Declaration> declarations =
        new ArrayDeque<>();

    boolean isAllowedToMove() {
      return allowMove;
    }

    void disallowMovement() {
      allowMove = false;
      // If we cannot move it, there's no point tracking where it's used.
      modulesWithReferences = null;
      preferredModule = declModule;
    }

    // Add a Module where it is used
    void addUsedModule(JSModule m) {
      // If we are not allowed to move it, don't waste time and space tracking modules with
      // references.
      if (allowMove) {
        if (modulesWithReferences == null) {
          // first call to this method
          modulesWithReferences = new BitSet(graph.getModuleCount());
        }
        modulesWithReferences.set(m.getIndex());
        // invalidate preferredModule, so it will be recalculated next time getPreferredModule() is
        // called.
        preferredModule = null;
      }
    }

    /**
     * Returns the root module of a dependency subtree that contains all of the modules which refer
     * to this global name.
     */
    JSModule getPreferredModule() {
      // It doesn't even make sense to call this method if the declarations cannot be moved.
      checkState(allowMove);
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

    /**
     * Add a declaration for this name.
     * @return Whether this is a valid declaration. If this returns false,
     *    this should be added as a reference.
     */
    boolean addDeclaration(Declaration d) {
      // all declarations must appear in the same module.
      if (declModule != null && d.module != declModule) {
        return false;
      }
      declarations.push(d);
      declModule = d.module;
      return true;
    }

    boolean shouldBeMoved() {
      // Only move if all are true:
      // a) allowMove is true
      // b) it is declared somewhere (declModule != null)
      // c) the all usages depend on the declModule by way of a different, preferred module
      return allowMove && declModule != null && graph.dependsOn(getPreferredModule(), declModule);
    }

    /**
     * Returns an iterator over the declarations, in the order that they were
     * declared.
     */
    Iterator<Declaration> declarationIterator() {
      return declarations.iterator();
    }
  }

  private static class Declaration {
    final JSModule module;
    final Node node;

    Declaration(JSModule module, Node node) {
      this.module = checkNotNull(module);
      this.node = checkNotNull(node);
    }
  }

  /**
   * return true if the node has any form of conditional in its ancestry
   * TODO(nicksantos) keep track of the conditionals in the ancestry, so
   * that we don't have to recrawl it.
   */
  private static boolean hasConditionalAncestor(Node n) {
    for (Node ancestor : n.getAncestors()) {
      switch (ancestor.getToken()) {
        case DO:
        case FOR:
        case FOR_IN:
        case HOOK:
        case IF:
        case SWITCH:
        case WHILE:
        case FUNCTION:
          return true;
        default:
          break;
      }
    }
    return false;
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

  /**
   * Process the reads to named variables
   */
  private void processRead(Reference ref, NamedInfo info) {
    // A name is recursively defined if:
    //   1: It is calling itself.
    //   2: One of its property calls itself.
    // Recursive definition should not block movement.
    String name = ref.getNode().getString();
    boolean recursive = false;
    Scope hoistTarget = ref.getScope().getClosestHoistScope();
    if (hoistTarget.isFunctionBlockScope()) {
      Node rootNode = hoistTarget.getRootNode().getParent();
      // CASE #1:
      String scopeFuncName = rootNode.getFirstChild().getString();
      Node scopeFuncParent = rootNode.getParent();
      if (scopeFuncName.equals(name)) {
        recursive = true;
      } else if (scopeFuncParent.isName() &&
          scopeFuncParent.getString().equals(name)) {
        recursive = true;
      } else {
        // CASE #2:
        // Suppose name is Foo, we keep look up the scope stack to look for
        // a scope with "Foo.prototype.bar = function() { ..... "
        for (Scope s = ref.getScope();
             s.getParent() != null; s = s.getParent()) {
          Node curRoot = s.getRootNode();
          if (curRoot.getParent().isAssign()) {
            Node owner = curRoot.getParent().getFirstChild();
            while (owner.isGetProp()) {
              owner = owner.getFirstChild();
            }
            if (owner.isName() &&
                owner.getString().equals(name)) {
              recursive = true;
              break;
            }
          }
        }
      }
    }

    if (!recursive) {
      info.addUsedModule(getModule(ref));
    }
  }

  private void collectReferences(Node root) {
    CrossModuleReferenceCollector collector = new CrossModuleReferenceCollector(
        compiler,
        new Es6SyntacticScopeCreator(compiler));
    collector.process(root);

    for (Var v : collector.getAllSymbols()) {
      NamedInfo info = getNamedInfo(v);
      if (info.isAllowedToMove()) {
        ReferenceCollection refCollection = collector.getReferences(v);
        for (Reference ref : refCollection) {
          processReference(collector, ref, info, v);
        }
      }
    }
  }

  private void processReference(
      CrossModuleReferenceCollector collector, Reference ref, NamedInfo info, Var v) {
    Node n = ref.getNode();
    if (isRecursiveDeclaration(v, n)) {
      return;
    }

    Node parent = n.getParent();
    if (maybeProcessDeclaration(collector, ref, info)) {
      // Check to see if the declaration is conditional starting at the
      // grandparent of the name node. Since a function declaration
      // is considered conditional (the function might not be called)
      // we would need to skip the parent in this check as the name could
      // just be a function itself.
      if (hasConditionalAncestor(parent.getParent())) {
        info.disallowMovement();
      }
    } else {
      if (!parentModuleCanSeeSymbolsDeclaredInChildren) {
        // Modules are loaded in such a way that Foo really must be defined before any
        // expressions like `x instanceof Foo` are evaluated.
        processRead(ref, info);
      } else {
        if (isUnguardedInstanceofReference(n)) {
          // Save a list of unguarded instanceof references.
          // We'll add undefined typeof guards to them instead of allowing them to block code
          // motion.
          instanceofNodes.put(parent, new InstanceofInfo(getModule(ref), info));
        } else if (!(isUndefinedTypeofGuardReference(n) || isGuardedInstanceofReference(n))) {
          // Ignore `'undefined' != typeof Ref && x instanceof Ref`
          // Otherwise, it's a read
          processRead(ref, info);
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

  /**
   * @param variable a variable which may be movable
   * @param referenceNode a node which is a reference to 'variable'
   * @return whether the reference to the variable is a recursive declaration
   *     e.g. function foo() { foo = function() {}; }
   */
  private boolean isRecursiveDeclaration(Var variable, Node referenceNode) {
    if (!referenceNode.getParent().isAssign()) {
      return false;
    }
    Node enclosingFunction = NodeUtil.getEnclosingFunction(referenceNode);
    return enclosingFunction != null
      && variable.getName().equals(NodeUtil.getNearestFunctionName(enclosingFunction));
  }

  private JSModule getModule(Reference ref) {
    return compiler.getInput(ref.getInputId()).getModule();
  }

  /**
   * Determines whether the given NAME node belongs to a declaration that
   * can be moved across modules. If it is, registers it properly.
   *
   * There are four types of movable declarations:
   * 1) var NAME = [movable object];
   * 2) function NAME() {}
   * 3) NAME = [movable object];
   *    NAME.prop = [movable object];
   *    NAME.prop.prop2 = [movable object];
   *    etc.
   * 4) Class-defining function calls, like "inherits" and "mixin".
   *    NAME.inherits([some other name]);
   * where "movable object" is a literal or a function.
   */
  private boolean maybeProcessDeclaration(
      CrossModuleReferenceCollector collector, Reference ref, NamedInfo info) {
    Node name = ref.getNode();
    Node parent = name.getParent();
    Node grandparent = parent.getParent();
    switch (parent.getToken()) {
      case VAR:
        if (canMoveValue(collector, ref.getScope(), name.getFirstChild())) {
          return info.addDeclaration(
              new Declaration(getModule(ref), name));
        }
        return false;

      case FUNCTION:
        if (NodeUtil.isFunctionDeclaration(parent)) {
          return info.addDeclaration(
              new Declaration(getModule(ref), name));
        }
        return false;

      case ASSIGN:
      case GETPROP:
        Node child = name;

        // Look for assignment expressions where the name is the root
        // of a qualified name on the left hand side of the assignment.
        for (Node current : name.getAncestors()) {
          if (current.isGetProp()) {
            // fallthrough
          } else if (current.isAssign() &&
                     current.getFirstChild() == child) {
            Node currentParent = current.getParent();
            if (currentParent.isExprResult() &&
                canMoveValue(
                    collector, ref.getScope(), current.getLastChild())) {
              return info.addDeclaration(
                  new Declaration(getModule(ref), current));
            }
          } else {
            return false;
          }

          child = current;
        }
        return false;

      case CALL:
        if (NodeUtil.isExprCall(grandparent)) {
          SubclassRelationship relationship =
              compiler.getCodingConvention().getClassesDefinedByCall(parent);
          if (relationship != null &&
              name.getString().equals(relationship.subclassName)) {
            return info.addDeclaration(
                new Declaration(getModule(ref), parent));
          }
        }
        return false;

      default:
        return false;
    }
  }

  /**
   * Determines whether the given value is eligible to be moved across modules.
   */
  private static boolean canMoveValue(
      CrossModuleReferenceCollector collector, Scope scope, Node n) {
    // the value is only movable if it's
    // a) nothing,
    // b) a constant literal,
    // c) a function, or
    // d) an array/object literal of movable values.
    // e) a function stub generated by CrossModuleMethodMotion.
    if (n == null || NodeUtil.isLiteralValue(n, true) ||
        n.isFunction()) {
      return true;
    } else if (n.isCall()) {
      Node functionName = n.getFirstChild();
      return functionName.isName() &&
          (functionName.getString().equals(
              CrossModuleMethodMotion.STUB_METHOD_NAME) ||
           functionName.getString().equals(
              CrossModuleMethodMotion.UNSTUB_METHOD_NAME));
    } else if (n.isArrayLit() || n.isObjectLit()) {
      boolean isObjectLit = n.isObjectLit();
      for (Node child = n.getFirstChild(); child != null;
           child = child.getNext()) {
        if (!canMoveValue(collector, scope,
                          isObjectLit ? child.getFirstChild() : child)) {
          return false;
        }
      }

      return true;
    } else if (n.isName()) {
      // If the value is guaranteed to never be changed after
      // this reference, then we can move it.
      Var v = scope.getVar(n.getString());
      if (v != null && v.isGlobal()) {
        ReferenceCollection refCollection = collector.getReferences(v);
        if (refCollection != null &&
            refCollection.isWellDefined() &&
            refCollection.isAssignedOnceInLifetime()) {
          return true;
        }
      }
    }

    return false;
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
