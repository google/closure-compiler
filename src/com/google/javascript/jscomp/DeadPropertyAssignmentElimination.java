/*
 * Copyright 2016 The Closure Compiler Authors.
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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Predicates.alwaysTrue;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.NodeTraversal.ChangeScopeRootCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * An optimization pass that finds and removes dead property assignments within functions and
 * classes.
 *
 * <p>This pass does not currently use the control-flow graph. It makes the following assumptions:
 * <ul>
 * <li>Functions with inner functions are not processed.</li>
 * <li>All properties are read whenever entering a block node. Dead assignments within a block
 * are processed.</li>
 * <li>Hook nodes are not processed (it's assumed they read everything)</li>
 * <li>Switch blocks are not processed (it's assumed they read everything)</li>
 * <li>Any reference to a property getter/setter is treated like a call that escapes all props.</li>
 * <li>If there's an Object.definePropert{y,ies} call where the object or property name is aliased
 * then the optimization does not run at all.</li>
 * <li>Properties names defined in externs will not be pruned.</li>
 * </ul>
 */
public class DeadPropertyAssignmentElimination implements CompilerPass {

  private final AbstractCompiler compiler;

  // TODO(kevinoconnor): Try to give special treatment to constructor, else remove this field
  // and cleanup dead code.
  @VisibleForTesting
  static final boolean ASSUME_CONSTRUCTORS_HAVENT_ESCAPED = false;

  DeadPropertyAssignmentElimination(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    // GatherExternProperties must be enabled for this pass to safely know what property writes are
    // eligible for removal.
    if (compiler.getExternProperties() == null || compiler.getAccessorSummary() == null) {
      return;
    }

    Set<String> skiplistedPropNames =
        Sets.union(
            compiler.getAccessorSummary().getAccessors().keySet(), compiler.getExternProperties());

    NodeTraversal.traverseChangedFunctions(compiler, new FunctionVisitor(skiplistedPropNames));
  }

  private static class FunctionVisitor implements ChangeScopeRootCallback {

    /** A set of properties names that are potentially unsafe to remove duplicate writes to. */
    private final Set<String> skiplistedPropNames;

    FunctionVisitor(Set<String> skiplistedPropNames) {
      this.skiplistedPropNames = skiplistedPropNames;
    }

    @Override
    public void enterChangeScopeRoot(AbstractCompiler compiler, Node root) {
      if (!root.isFunction()) {
        return;
      }

      Node body = NodeUtil.getFunctionBody(root);
      if (!body.hasChildren() || NodeUtil.has(body, Node::isFunction, alwaysTrue())) {
        return;
      }

      FindCandidateAssignmentTraversal traversal =
          new FindCandidateAssignmentTraversal(skiplistedPropNames, NodeUtil.isConstructor(root));
      NodeTraversal.traverse(compiler, body, traversal);

      // Any candidate property assignment can have a write removed if that write is never read
      // and it's written to at least one more time.
      for (Property property : traversal.propertyMap.values()) {
        if (property.writes.size() <= 1) {
          continue;
        }
        PeekingIterator<PropertyWrite> iter = Iterators.peekingIterator(property.writes.iterator());
        while (iter.hasNext()) {
          PropertyWrite propertyWrite = iter.next();
          if (iter.hasNext() && propertyWrite.isSafeToRemove(iter.peek())) {
            Node lhs = propertyWrite.assignedAt;
            Node rhs = lhs.getNext();
            Node assignNode = lhs.getParent();
            if (assignNode.isAssign()) {
              // replace "a.b.c = <expr>" with "<expr>"
              rhs.detach();
              assignNode.replaceWith(rhs);
              compiler.reportChangeToEnclosingScope(rhs);
            } else {
              checkState(NodeUtil.isAssignmentOp(assignNode));
              // replace "a.b.c += <expr>" with "a.b.c + expr"
              Token opType = NodeUtil.getOpFromAssignmentOp(assignNode);
              assignNode.setToken(opType);
              compiler.reportChangeToEnclosingScope(assignNode);
            }
          }
        }
      }
    }
  }

  private static class Property {

    private final String name;

    // This pass doesn't use a control-flow graph; this field contains a rough approximation
    // of the control flow. For writes in the same block, they appear in this list in
    // program-execution order.
    // All writes in a list are to the same property name, but the full qualified names may
    // differ, eg, a.b.c and e.d.c can be in the list. Consecutive writes to the same qname
    // may mean that the first write can be removed (see isSafeToRemove).
    private final Deque<PropertyWrite> writes = new ArrayDeque<>();

    private final Set<Property> children = new HashSet<>();

    Property(String name) {
      this.name = name;
    }

    void markLastWriteRead() {
      if (!writes.isEmpty()) {
        writes.getLast().markRead();
      }
    }

    /**
     * Marks all children of this property as read.
     */
    void markChildrenRead() {
      // If a property is in propertiesSet, it has been added to the queue and processed,
      // it will not be added to the queue again.
      Set<Property> propertiesSet = new HashSet<>(children);
      Queue<Property> propertyQueue = new ArrayDeque<>(propertiesSet);

      // Ensure we don't process ourselves.
      propertiesSet.add(this);

      while (!propertyQueue.isEmpty()) {
        Property childProperty = propertyQueue.remove();
        childProperty.markLastWriteRead();
        for (Property grandchildProperty : childProperty.children) {
          if (propertiesSet.add(grandchildProperty)) {
            propertyQueue.add(grandchildProperty);
          }
        }
      }
    }

    void addWrite(Node lhs) {
      checkArgument(lhs.isQualifiedName());
      writes.addLast(new PropertyWrite(lhs));
    }

    @Override
    public String toString() {
      return "Property " + name;
    }
  }

  private static class PropertyWrite {
    private final Node assignedAt;
    private boolean isRead = false;
    private final String qualifiedName;

    PropertyWrite(Node assignedAt) {
      checkArgument(assignedAt.isQualifiedName());
      this.assignedAt = assignedAt;
      this.qualifiedName = assignedAt.getQualifiedName();
    }

    boolean isSafeToRemove(@Nullable PropertyWrite nextWrite) {
      return !isRead && nextWrite != null && Objects.equals(qualifiedName, nextWrite.qualifiedName);
    }

    void markRead() {
      isRead = true;
    }

    boolean isChildPropOf(String lesserPropertyQName) {
      return qualifiedName != null && qualifiedName.startsWith(lesserPropertyQName + ".");
    }
  }

  /**
   * A NodeTraversal that operates within a function block and collects candidate properties
   * assignments.
   */
  private static class FindCandidateAssignmentTraversal implements Callback {

    /**
     * A map of property names to their nodes.
     *
     * <p>Note: the references {@code a.b} and {@code c.b} will assume that it's the same b,
     * because a and c may be aliased, and we don't track aliasing.
     */
    Map<String, Property> propertyMap = new HashMap<>();

    /** A set of properties names that are potentially unsafe to remove duplicate writes to. */
    private final Set<String> skiplistedPropNames;

    /**
     * Whether or not the function being analyzed is a constructor.
     */
    private final boolean isConstructor;

    FindCandidateAssignmentTraversal(Set<String> skiplistedPropNames, boolean isConstructor) {
      this.skiplistedPropNames = skiplistedPropNames;
      this.isConstructor = isConstructor;
    }

    /**
     * Gets a {@link Property} given the node that references it; the {@link Property} is created
     * if it does not already exist.
     *
     * @return A {@link Property}, or null if the provided node is not a qualified name.
     */
    private Property getOrCreateProperty(Node propNode) {
      if (!propNode.isQualifiedName()) {
        return null;
      }

      String propName =
          propNode.isGetProp() ? propNode.getLastChild().getString() : propNode.getQualifiedName();

      Property property = propertyMap.computeIfAbsent(propName, name -> new Property(name));

      /* Using the GETPROP chain, build out the tree of children properties.

         For example, from a.b.c and a.c we can build:
                 a
                / \
               b   c
              /
             c

        Note: c is the same Property in this tree.
      */
      if (propNode.isGetProp()) {
        Property parentProperty = getOrCreateProperty(propNode.getFirstChild());
        if (parentProperty != null) {
          parentProperty.children.add(property);
        }
      }

      return property;
    }

    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      return visitNode(n, parent);
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      // Visit the LHS of an assignment in post-order
      if (NodeUtil.isAssignmentOp(n)) {
        visitAssignmentLhs(n.getFirstChild());
      }

      // Assume that all properties may be read when control flow leaves the function
      if (NodeUtil.isInvocation(n) || n.isYield() || n.isAwait()) {
        if (ASSUME_CONSTRUCTORS_HAVENT_ESCAPED
            && isConstructor
            && !NodeUtil.referencesEnclosingReceiver(n)
            && NodeUtil.getEnclosingType(n, Token.TRY) == null) {
          // this.x properties are okay.
          markAllPropsReadExceptThisProps();
        } else {
          markAllPropsRead();
        }
      }

      // Mark all properties as read when leaving a block since we haven't proven that the block
      // will execute.
      if (n.isBlock()) {
        visitBlock(n);
      }
    }

    private void visitBlock(Node blockNode) {
      checkArgument(blockNode.isBlock());

      // We don't do flow analysis yet so we're going to assume everything written up to this
      // block is read.
      if (blockNode.hasChildren()) {
        markAllPropsRead();
      }
    }

    private static boolean isConditionalExpression(Node n) {
      switch (n.getToken()) {
        case AND:
        case OR:
        case HOOK:
        case COALESCE:
          return true;
        default:
          return false;
      }
    }

    private void visitAssignmentLhs(Node lhs) {
      Property property = getOrCreateProperty(lhs);

      if (property == null) {
        return;
      }

      if (!lhs.isGetProp()) {
        property.markLastWriteRead();
        property.markChildrenRead();
        return;
      }

      Node assignNode = lhs.getParent();

      // If it's mutating assignment (+=, *=, etc.) then mark the last assignment read first.
      if (!assignNode.isAssign()) {
        property.markLastWriteRead();
      }

      // Reassignment of a qualified name prefix might change what child properties are referenced
      // later on, so consider children properties as read.
      // Ex. a.b.c = 10; a.b = other; a.b.c = 20;
      property.markChildrenRead();
      property.addWrite(lhs);

      // Now we need to go up the prop chain and mark those as read.
      Node child = lhs.getFirstChild();
      while (child != null) {
        Property childProperty = getOrCreateProperty(child);
        if (childProperty == null) {
          break;
        }
        childProperty.markLastWriteRead();
        child = child.getFirstChild();
      }
    }

    private boolean visitNode(Node n, Node parent) {
      switch (n.getToken()) {
        case GETPROP:
          // Handle potential getters/setters.
          if (n.isGetProp() && skiplistedPropNames.contains(n.getLastChild().getString())) {
            // We treat getters/setters as if they were a call, thus we mark all properties as read.
            markAllPropsRead();
            return true;
          }

          if (NodeUtil.isAssignmentOp(parent) && parent.getFirstChild() == n) {
            // We always visit the LHS assignment in post-order
            return false;
          }
          Property property = getOrCreateProperty(n);
          if (property != null) {
            // Mark all children properties as read.
            property.markLastWriteRead();

            // Only mark children properties as read if we're at at the end of the referenced
            // property chain.
            // Ex. A read of "a.b.c" should mark a, a.b, a.b.c, and a.b.c.* as read, but not a.d
            if (!parent.isGetProp()) {
              property.markChildrenRead();
            }
          }
          return true;

        case THIS:
        case NAME:
          Property nameProp = checkNotNull(getOrCreateProperty(n));
          nameProp.markLastWriteRead();
          if (!parent.isGetProp()) {
            nameProp.markChildrenRead();
          }
          return true;

        case THROW:
        case FOR:
        case FOR_IN:
        case SWITCH:
          // TODO(kevinoconnor): Switch/for statements need special consideration since they may
          // execute out of order.
          markAllPropsRead();
          return false;
        case BLOCK:
          visitBlock(n);
          return true;
        default:
          if (isConditionalExpression(n)) {
            markAllPropsRead();
            return false;
          }
          return true;
      }
    }

    private void markAllPropsRead() {
      markAllPropsReadHelper(false /* excludeThisProps*/);
    }

    private void markAllPropsReadExceptThisProps() {
      markAllPropsReadHelper(true /* excludeThisProps */);
    }

    private void markAllPropsReadHelper(boolean excludeThisProps) {
      for (Property property : propertyMap.values()) {
        if (property.writes.isEmpty()) {
          continue;
        }

        if (excludeThisProps && property.writes.getLast().isChildPropOf("this")) {
          continue;
        }

        property.markLastWriteRead();
      }
    }
  }
}
