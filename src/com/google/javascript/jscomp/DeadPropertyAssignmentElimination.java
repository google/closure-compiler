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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * An optimization pass that finds and removes dead property assignments within functions.
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
    if (compiler.getExternProperties() == null) {
      return;
    }

    GetterSetterCollector getterSetterCollector = new GetterSetterCollector();
    NodeTraversal.traverseEs6(compiler, root, getterSetterCollector);

    // If there's any potentially unknown getter/setter property, back off of the optimization.
    if (getterSetterCollector.unknownGetterSetterPresent) {
      return;
    }

    Set<String> blacklistedPropNames = Sets.union(
        getterSetterCollector.propNames, compiler.getExternProperties());
    NodeTraversal.traverseEs6(compiler, root, new FunctionVisitor(compiler, blacklistedPropNames));
  }

  private static class FunctionVisitor extends AbstractPostOrderCallback {

    private final AbstractCompiler compiler;

    /**
     * A set of properties names that are potentially unsafe to remove duplicate writes to.
     */
    private final Set<String> blacklistedPropNames;

    FunctionVisitor(AbstractCompiler compiler, Set<String> blacklistedPropNames) {
      this.compiler = compiler;
      this.blacklistedPropNames = blacklistedPropNames;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!n.isFunction()) {
        return;
      }

      Node body = NodeUtil.getFunctionBody(n);
      if (!body.hasChildren() || NodeUtil.containsFunction(body)) {
        return;
      }

      FindCandidateAssignmentTraversal traversal =
          new FindCandidateAssignmentTraversal(blacklistedPropNames, NodeUtil.isConstructor(n));
      NodeTraversal.traverseEs6(compiler, body, traversal);

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
            rhs.detachFromParent();
            assignNode.getParent().replaceChild(assignNode, rhs);
            compiler.reportCodeChange();
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
    private final LinkedList<PropertyWrite> writes = new LinkedList<>();

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
      Queue<Property> propertyQueue = new LinkedList<>(propertiesSet);

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
      Preconditions.checkArgument(lhs.isQualifiedName());
      writes.addLast(new PropertyWrite(lhs));
    }
  }

  private static class PropertyWrite {
    private final Node assignedAt;
    private boolean isRead = false;
    private final String qualifiedName;

    PropertyWrite(Node assignedAt) {
      Preconditions.checkArgument(assignedAt.isQualifiedName());
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

    /**
     * A set of properties names that are potentially unsafe to remove duplicate writes to.
     */
    private final Set<String> blacklistedPropNames;

    /**
     * Whether or not the function being analyzed is a constructor.
     */
    private final boolean isConstructor;

    FindCandidateAssignmentTraversal(Set<String> blacklistedPropNames, boolean isConstructor) {
      this.blacklistedPropNames = blacklistedPropNames;
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
      if (propertyMap.containsKey(propName)) {
        return propertyMap.get(propName);
      }

      Property property = new Property(propName);
      propertyMap.put(propName, property);

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

      // Mark all properties as read when leaving a block since we haven't proven that the block
      // will execute.
      if (n.isBlock()) {
        visitBlock(n);
      }
    }

    private void visitBlock(Node blockNode) {
      Preconditions.checkArgument(blockNode.isBlock());

      // We don't do flow analysis yet so we're going to assume everything written up to this
      // block is read.
      if (blockNode.hasChildren()) {
        markAllPropsRead();
      }
    }

    private static boolean isConditionalExpression(Node n) {
      switch (n.getType()) {
        case AND:
        case OR:
        case HOOK:
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
      switch (n.getType()) {
        case GETPROP:
          // Handle potential getters/setters.
          if (n.isGetProp()
              && n.getLastChild().isString()
              && blacklistedPropNames.contains(n.getLastChild().getString())) {
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
        case CALL:
          if (ASSUME_CONSTRUCTORS_HAVENT_ESCAPED && isConstructor && !NodeUtil.referencesThis(n)
              && NodeUtil.getEnclosingType(n, Token.TRY) == null) {
            // this.x properties are okay.
            markAllPropsReadExceptThisProps();
          } else {
            markAllPropsRead();
          }
          return false;

        case THIS:
        case NAME:
          Property nameProp = Preconditions.checkNotNull(getOrCreateProperty(n));
          nameProp.markLastWriteRead();
          if (!parent.isGetProp()) {
            nameProp.markChildrenRead();
          }
          return true;

        case THROW:
        case FOR:
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

  /**
   * A traversal to find all property names that are defined to have a getter and/or setter
   * associated with them.
   */
  private static class GetterSetterCollector implements Callback {

    /**
     * A set of properties names that are known to be assigned to getter/setters. This is important
     * since any reference to these properties needs to be treated as if it were a call.
     */
    private final Set<String> propNames = new HashSet<>();

    /**
     * Whether or not a property might have a getter/setter but it could not be statically analyzed
     * to determine which one.
     */
    private boolean unknownGetterSetterPresent = false;


    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      // Don't traverse into $jscomp.inherits's definition; it uses Object.defineProperty to copy
      // properties. It will not introduce a getter/setter that we haven't already seen.
      if (n.isFunction()) {
        String funcName = NodeUtil.getName(n);
        if (funcName != null
            && (funcName.equals("$jscomp.inherits") || funcName.equals("$jscomp$inherits"))) {
          return false;
        }
      }

      // Stop the traversal if there's a unknown getter/setter present.
      return !unknownGetterSetterPresent;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (NodeUtil.isObjectDefinePropertyDefinition(n)) {
        // We must assume any property in the compilation can be a getter/setter if the property
        // name and what is being assigned to are aliased.
        if (!n.getChildAtIndex(2).isString() && !n.getLastChild().isObjectLit()) {
          unknownGetterSetterPresent = true;
        } else if (!n.getLastChild().isObjectLit()) {
          // If know the property name but not what it's being assigned to then we need to blackist
          // the property name.
          propNames.add(n.getChildAtIndex(2).getString());
        }
        return;
      } else if (NodeUtil.isObjectDefinePropertiesDefinition(n)
          && !n.getChildAtIndex(2).isObjectLit()) {
        // If the second param is not an object literal then we must assume any property in the
        // compilation can be a getter/setter.
        unknownGetterSetterPresent = true;
        return;
      }

      // Keep track of any potential getters/setters.
      if (NodeUtil.isGetterOrSetter(n)) {
        Node grandparent = parent.getParent();
        if (NodeUtil.isGetOrSetKey(n) && n.getString() != null) {
          // ES5 getter/setter nodes contain the property name directly on the node.
          propNames.add(n.getString());
        } else if (NodeUtil.isObjectDefinePropertyDefinition(grandparent)) {
          // Handle Object.defineProperties(obj, 'propName', { ... }).
          Node propNode = grandparent.getChildAtIndex(2);
          if (propNode.isString()) {
            propNames.add(propNode.getString());
          } else {
            // Putting a getter/setter on an aliased property means any property can be a getter or
            // setter.
            unknownGetterSetterPresent = true;
          }
        } else if (grandparent.isStringKey()
            && NodeUtil.isObjectDefinePropertiesDefinition(grandparent.getParent().getParent())) {
          // Handle Object.defineProperties(obj, {propName: { ... }}).
          propNames.add(grandparent.getString());
        }
      } else if (isAliasedPropertySet(n)) {
        // If we know this property is being injected but don't know if there's a getter/setter
        // then the property still must be blacklisted.
        propNames.add(n.getString());
      }
    }

    /**
     * Determines if the given keyNode contains an aliased property set. In particular this is only
     * true if the grandparent is an {@code Object.defineProperties} call.
     *
     * <p>Ex. {@code Object.defineProperties(Foo.prototype, {bar: someObj}}.
     */
    private static boolean isAliasedPropertySet(Node keyNode) {
      if (keyNode == null || !keyNode.isStringKey() || keyNode.getParent() == null) {
        return false;
      }

      Node objectLit = keyNode.getParent();
      return objectLit.getParent() != null
          && NodeUtil.isObjectDefinePropertiesDefinition(objectLit.getParent())
          && objectLit.getParent().getLastChild() == objectLit
          && !keyNode.getFirstChild().isObjectLit();
    }
  }
}
