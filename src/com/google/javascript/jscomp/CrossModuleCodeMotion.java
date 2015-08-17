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

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.jscomp.ReferenceCollectingCallback.Reference;
import com.google.javascript.jscomp.ReferenceCollectingCallback.ReferenceCollection;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A {@link Compiler} pass for moving code to a deeper module if possible.
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

      // Make is so we can ignore constructor references in instanceof.
      if (parentModuleCanSeeSymbolsDeclaredInChildren) {
        makeInstanceOfCodeOrderIndependent();
      }

      // Move the functions + variables to a deeper module [if possible]
      moveCode();
    }
  }

  /** move the code accordingly */
  private void moveCode() {
    for (NamedInfo info : namedInfo.values()) {
      JSModule deepestDependency = info.deepestModule;

      // Only move if all are true:
      // a) allowMove is true
      // b) it was used + declared somewhere [if not, then it will be removed
      // as dead or invalid code elsewhere]
      // c) the new dependency depends on the declModule
      if (info.allowMove && deepestDependency != null) {
        Iterator<Declaration> it = info.declarationIterator();
        JSModuleGraph moduleGraph = compiler.getModuleGraph();
        while (it.hasNext()) {
          Declaration decl = it.next();
          if (decl.module != null &&
              moduleGraph.dependsOn(deepestDependency,
                  decl.module)) {

            // Find the appropriate spot to move it to
            Node destParent = moduleVarParentMap.get(deepestDependency);
            if (destParent == null) {
              destParent = compiler.getNodeForCodeInsertion(deepestDependency);
              moduleVarParentMap.put(deepestDependency, destParent);
            }

            // VAR Nodes are normalized to have only one child.
            Node declParent = decl.node.getParent();
            Preconditions.checkState(
                !declParent.isVar() || declParent.hasOneChild(),
                "AST not normalized.");

            // Remove it
            declParent.detachFromParent();

            // Add it to the new spot
            destParent.addChildToFront(declParent);

            compiler.reportCodeChange();
          }
        }
      }
    }
  }

  /** useful information for each variable candidate */
  private class NamedInfo {
    boolean allowMove = true;

    // The deepest module where the variable is used. Starts at null
    private JSModule deepestModule = null;

    // The module where declarations appear
    private JSModule declModule = null;

    // information on the spot where the item was declared
    private final Deque<Declaration> declarations =
        new ArrayDeque<>();

    // Add a Module where it is used
    void addUsedModule(JSModule m) {
      // If we are not allowed to move it, all bets are off
      if (!allowMove) {
        return;
      }

      // If we have no deepest module yet, set this one
      if (deepestModule == null) {
        deepestModule = m;
      } else {
        // Find the deepest common dependency
        deepestModule =
            graph.getDeepestCommonDependencyInclusive(m, deepestModule);
      }
    }

    boolean isUsedInOrDependencyOfModule(JSModule m) {
      if (deepestModule == null || m == null) {
        return false;
      }
      return m == deepestModule || graph.dependsOn(m, deepestModule);
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
      this.module = module;
      this.node = node;
    }
  }

  /**
   * return true if the node has any form of conditional in its ancestry
   * TODO(nicksantos) keep track of the conditionals in the ancestry, so
   * that we don't have to recrawl it.
   */
  private static boolean hasConditionalAncestor(Node n) {
    for (Node ancestor : n.getAncestors()) {
      switch (ancestor.getType()) {
        case Token.DO:
        case Token.FOR:
        case Token.HOOK:
        case Token.IF:
        case Token.SWITCH:
        case Token.WHILE:
        case Token.FUNCTION:
          return true;
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
    ReferenceCollectingCallback collector = new ReferenceCollectingCallback(
        compiler, ReferenceCollectingCallback.DO_NOTHING_BEHAVIOR,
        new Predicate<Var>() {
          @Override public boolean apply(Var var) {
            // Only collect global and non-exported names.
            return var.isGlobal() &&
                !compiler.getCodingConvention().isExported(var.getName());
          }
        });
    NodeTraversal.traverseEs6(compiler, root, collector);

    for (Var v : collector.getAllSymbols()) {
      ReferenceCollection refCollection = collector.getReferences(v);
      NamedInfo info = getNamedInfo(v);
      for (Reference ref : refCollection) {
        processReference(collector, ref, info);
      }
    }
  }

  private void processReference(
      ReferenceCollectingCallback collector, Reference ref, NamedInfo info) {
    Node n = ref.getNode();
    Node parent = n.getParent();
    if (info.allowMove) {
      if (maybeProcessDeclaration(collector, ref, info)) {
        // Check to see if the declaration is conditional starting at the
        // grandparent of the name node. Since a function declaration
        // is considered conditional (the function might not be called)
        // we would need to skip the parent in this check as the name could
        // just be a function itself.
        if (hasConditionalAncestor(parent.getParent())) {
          info.allowMove = false;
        }
      } else {
        if (parentModuleCanSeeSymbolsDeclaredInChildren &&
            parent.isInstanceOf() && parent.getLastChild() == n) {
          instanceofNodes.put(parent, new InstanceofInfo(getModule(ref), info));
        } else {
          // Otherwise, it's a read
          processRead(ref, info);
        }
      }
    }
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
      ReferenceCollectingCallback collector, Reference ref, NamedInfo info) {
    Node name = ref.getNode();
    Node parent = name.getParent();
    Node grandparent = parent.getParent();
    switch (parent.getType()) {
      case Token.VAR:
        if (canMoveValue(collector, ref.getScope(), name.getFirstChild())) {
          return info.addDeclaration(
              new Declaration(getModule(ref), name));
        }
        return false;

      case Token.FUNCTION:
        if (NodeUtil.isFunctionDeclaration(parent)) {
          return info.addDeclaration(
              new Declaration(getModule(ref), name));
        }
        return false;

      case Token.ASSIGN:
      case Token.GETPROP:
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

      case Token.CALL:
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
      ReferenceCollectingCallback collector, Scope scope, Node n) {
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
   * Transforms instanceof usages into an expression that short circuits to
   * false if tested with a constructor that is undefined. This allows ignoring
   * instanceof with respect to cross module code motion.
   */
  private void makeInstanceOfCodeOrderIndependent() {
    Node tmp = IR.block();
    for (Map.Entry<Node, InstanceofInfo> entry : instanceofNodes.entrySet()) {
      Node n = entry.getKey();
      InstanceofInfo info = entry.getValue();
      if (!info.namedInfo.allowMove || !info.mustBeGuardedByTypeof()) {
        continue;
      }
      // In order for the compiler pass to be idempotent, this checks whether
      // the instanceof is already wrapped in the code that is generated below.
      Node parent = n.getParent();
      if (parent.isAnd() && parent.getLastChild() == n
          && parent.getFirstChild().isNE()) {
        Node ne = parent.getFirstChild();
        if (ne.getFirstChild().isString()
            && "undefined".equals(ne.getFirstChild().getString())
            && ne.getLastChild().isTypeOf()) {
          Node ref = ne.getLastChild().getFirstChild();
          if (ref.isEquivalentTo(n.getLastChild())) {
            continue;
          }
        }
      }
      // Wrap "foo instanceof Bar" in
      // "('undefined' != typeof Bar && foo instanceof Bar)"
      Node reference = n.getLastChild().cloneNode();
      Preconditions.checkState(reference.isName());
      n.getParent().replaceChild(n, tmp);
      Node and = IR.and(
          new Node(Token.NE,
              IR.string("undefined"),
              new Node(Token.TYPEOF, reference)
          ),
          n
      );
      and.useSourceInfoIfMissingFromForTree(n);
      tmp.getParent().replaceChild(tmp, and);
      compiler.reportCodeChange();
    }
  }

  private class InstanceofInfo {
    private final JSModule module;
    private final NamedInfo namedInfo;

    InstanceofInfo(JSModule module, NamedInfo namedInfo) {
      this.module = module;
      this.namedInfo = namedInfo;
    }

    /**
     * Returns true if this instance of instanceof is in a deeper module than
     * the deepest module (by reference) of the related name.
     * In that case the name may be undefined when the instanceof runs and we
     * have to guard it with typeof.
     */
    boolean mustBeGuardedByTypeof() {
      return !this.namedInfo.isUsedInOrDependencyOfModule(this.module);
    }
  }
}
