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

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CodingConvention.AssertionFunctionSpec;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * <p>Compiler pass that removes Closure-specific code patterns.</p>
 *
 * <p>Currently does the following:</p>
 *
 * <ul>
 *   <li> Instead of setting abstract methods to a function that throws an
 *        informative error, this pass allows some binary size reduction by
 *        removing these methods altogether for production builds.</li>
 *   <li> Remove calls to assertion functions (like goog.asserts.assert).
 *        If the return value of the assertion function is used, then
 *        the first argument (the asserted value) will be directly inlined.
 *        Otherwise, the entire call will be removed. It is well-known that
 *        this is not provably safe, much like the equivalent assert
 *        statement in Java.</li>
 * </ul>
 *
 * @author robbyw@google.com (Robby Walker)
 */
final class ClosureCodeRemoval implements CompilerPass {

  /** Reference to the JS compiler */
  private final AbstractCompiler compiler;

  /** Name used to denote an abstract function */
  static final String ABSTRACT_METHOD_NAME = "goog.abstractMethod";

  private final boolean removeAbstractMethods;
  private final boolean removeAssertionCalls;

  /**
   * List of names referenced in successive generations of finding referenced
   * nodes.
   */
  private final List<RemovableAssignment> abstractMethodAssignmentNodes =
       new ArrayList<>();

  /** List of member function definition nodes annotated with @abstract. */
  private final List<Node> abstractMemberFunctionNodes = new ArrayList<>();

  /**
   * List of assertion functions.
   */
  private final List<Node> assertionCalls = new ArrayList<>();


  /**
   * Utility class to track a node and its parent.
   */
  private class RemovableAssignment {
    /**
     * The node
     */
    final Node node;

    /**
     * Its parent
     */
    final Node parent;

    /**
     * Full chain of ASSIGN ancestors
     */
    final List<Node> assignAncestors = new ArrayList<>();

    /**
     * The last ancestor
     */
    final Node lastAncestor;

    /**
     * Data structure for information about a removable assignment.
     *
     * @param nameNode The LHS
     * @param assignNode The parent ASSIGN node
     * @param traversal Access to further levels, assumed to start at 1
     */
    public RemovableAssignment(Node nameNode, Node assignNode,
        NodeTraversal traversal) {
      this.node = nameNode;
      this.parent = assignNode;

      Node ancestor = assignNode;
      do {
        ancestor = ancestor.getParent();
        assignAncestors.add(ancestor);
      } while (ancestor.isAssign() &&
               ancestor.getFirstChild().isQualifiedName());
      lastAncestor = ancestor.getParent();
    }

    /**
     * Remove this node.
     */
    public void remove() {
      Node rhs = node.getNext();
      Node last = parent;
      for (Node ancestor : assignAncestors) {
        if (ancestor.isExprResult()) {
          lastAncestor.removeChild(ancestor);
          NodeUtil.markFunctionsDeleted(ancestor, compiler);
        } else {
          rhs.detach();
          ancestor.replaceChild(last, rhs);
        }
        last = ancestor;
      }
      compiler.reportChangeToEnclosingScope(lastAncestor);
    }
  }

  /**
   * Identifies all assignments of the abstract method to a variable and all methods annotated with
   * "@abstract" in their JSDoc.
   */
  private class FindAbstractMethods extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isAssign()) {
        Node nameNode = n.getFirstChild();
        Node valueNode = n.getLastChild();

        if (nameNode.isQualifiedName() &&
            valueNode.isQualifiedName() &&
            valueNode.matchesQualifiedName(ABSTRACT_METHOD_NAME)) {
          // Foo.prototype.bar = goog.abstractMethod
          abstractMethodAssignmentNodes.add(
              new RemovableAssignment(n.getFirstChild(), n, t));
        } else if (n.getJSDocInfo() != null
            && n.getJSDocInfo().isAbstract()
            && !(n.getJSDocInfo().isConstructor() || valueNode.isClass())) {
          // @abstract
          abstractMethodAssignmentNodes.add(
              new RemovableAssignment(n.getFirstChild(), n, t));
        }
      } else if (n.isMemberFunctionDef() && parent.isClassMembers()) {
        if (n.getJSDocInfo() != null && n.getJSDocInfo().isAbstract()) {
          abstractMemberFunctionNodes.add(n);
        }
      }
    }
  }


  /**
   * Identifies all assertion calls.
   */
  private class FindAssertionCalls extends AbstractPostOrderCallback {
    final Set<String> assertionNames;

    FindAssertionCalls() {
      // TODO(b/126254920): make this use ClosurePrimitive instead,
      assertionNames =
          compiler.getCodingConvention().getAssertionFunctions().stream()
              .map(AssertionFunctionSpec::getFunctionName)
              // Filter out assertion functions with a null functionName, which are identified only
              // by ClosurePrimitive id.
              .filter(Objects::nonNull)
              .collect(ImmutableSet.toImmutableSet());
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isCall()) {
        String fnName = n.getFirstChild().getQualifiedName();
        if (assertionNames.contains(fnName)) {
          assertionCalls.add(n);
        }
      }
    }
  }


  /**
   * Creates a Closure code remover.
   *
   * @param compiler The AbstractCompiler
   * @param removeAbstractMethods Remove declarations of abstract methods.
   * @param removeAssertionCalls Remove calls to goog.assert functions.
   */
  ClosureCodeRemoval(AbstractCompiler compiler, boolean removeAbstractMethods,
                     boolean removeAssertionCalls) {
    this.compiler = compiler;
    this.removeAbstractMethods = removeAbstractMethods;
    this.removeAssertionCalls = removeAssertionCalls;
  }

  @Override
  public void process(Node externs, Node root) {
    List<Callback> passes = new ArrayList<>();
    if (removeAbstractMethods) {
      passes.add(new FindAbstractMethods());
    }
    if (removeAssertionCalls) {
      passes.add(new FindAssertionCalls());
    }
    CombinedCompilerPass.traverse(compiler, root, passes);

    for (RemovableAssignment assignment : abstractMethodAssignmentNodes) {
      assignment.remove();
    }

    for (Node memberFunction : abstractMemberFunctionNodes) {
      compiler.reportFunctionDeleted(memberFunction.getFirstChild());
      Node parent = memberFunction.getParent();
      parent.removeChild(memberFunction);
      compiler.reportChangeToEnclosingScope(parent);
    }

    for (Node call : assertionCalls) {
      // If the assertion is an expression, just strip the whole thing.
      compiler.reportChangeToEnclosingScope(call);
      Node parent = call.getParent();
      if (parent.isExprResult()) {
        parent.detach();
        NodeUtil.markFunctionsDeleted(parent, compiler);
      } else {
        // Otherwise, replace the assertion with its first argument,
        // which is the return value of the assertion.
        Node firstArg = call.getSecondChild();
        if (firstArg == null) {
          parent.replaceChild(call, NodeUtil.newUndefinedNode(call));
        } else {
          Node replacement = firstArg.detach();
          replacement.setJSType(call.getJSType());
          parent.replaceChild(call, replacement);
        }
        NodeUtil.markFunctionsDeleted(call, compiler);
      }
    }
  }
}
