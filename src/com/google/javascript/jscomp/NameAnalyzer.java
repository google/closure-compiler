/*
 * Copyright 2006 The Closure Compiler Authors.
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
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.jscomp.GatherSideEffectSubexpressionsCallback.GetReplacementSideEffectSubexpressions;
import com.google.javascript.jscomp.GatherSideEffectSubexpressionsCallback.SideEffectAccumulator;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This pass identifies all global names, simple (e.g. <code>a</code>) or
 * qualified (e.g. <code>a.b.c</code>), and the dependencies between them, then
 * removes code associated with unreferenced names. It starts by assuming that
 * only externally accessible names (e.g. <code>window</code>) are referenced,
 * then iteratively marks additional names as referenced (e.g. <code>Foo</code>
 * in <code>window['foo'] = new Foo();</code>). This makes it possible to
 * eliminate code containing circular references.
 *
 * <p>Qualified names can be defined using dotted or object literal syntax
 * (<code>a.b.c = x;</code> or <code>a.b = {c: x};</code>, respectively).
 *
 * <p>Removal of prototype classes is currently all or nothing. In other words,
 * prototype properties and methods are never individually removed.
 *
 * <p>Optionally generates pretty HTML output of data so that it is easy to
 * analyze dependencies.
 *
 * <p>Only operates on names defined in the global scope, but it would be easy
 * to extend the pass to names defined in local scopes.
 *
 * TODO(nicksantos): In the initial implementation of this pass, it was
 * important to understand namespaced names (e.g., that a.b is distinct from
 * a.b.c). Now that this pass comes after CollapseProperties, this is no longer
 * necessary. For now, I've changed so that {@code referenceParentNames}
 * creates a two-way reference between a.b and a.b.c, so that they're
 * effectively the same name. When someone has the time, we should completely
 * rip out all the logic that understands namespaces.
 *
 */
final class NameAnalyzer implements CompilerPass {

  /** Reference to the JS compiler */
  private final AbstractCompiler compiler;

  /** Map of all JS names found */
  private final Map<String, JsName> allNames = Maps.newTreeMap();

  /** Reference dependency graph */
  private LinkedDirectedGraph<JsName, RefType> referenceGraph =
      LinkedDirectedGraph.createWithoutAnnotations();

  /**
   * Map of name scopes - all children of the Node key have a dependency on the
   * name value.
   *
   * If scopes.get(node).equals(name) && node2 is a child of node, then node2
   * will not get executed unless name is referenced via a get operation
   */
  private final ListMultimap<Node, NameInformation> scopes =
      LinkedListMultimap.create();

  /** Used to parse prototype names */
  private static final String PROTOTYPE_SUBSTRING = ".prototype.";

  private static final int PROTOTYPE_SUBSTRING_LEN =
      PROTOTYPE_SUBSTRING.length();

  private static final int PROTOTYPE_SUFFIX_LEN = ".prototype".length();

  /** Window root */
  private static final String WINDOW = "window";

  /** Function class name */
  private static final String FUNCTION = "Function";

  /** All of these refer to global scope. These can be moved to config */
  static final Set<String> DEFAULT_GLOBAL_NAMES = ImmutableSet.of(
      "window", "goog.global");

  /** Whether to remove unreferenced variables in main pass */
  private final boolean removeUnreferenced;

  /** Names that refer to the global scope */
  private final Set<String> globalNames;

  /** Ast change helper */
  private final AstChangeProxy changeProxy;

  /** Names that are externally defined */
  private final Set<String> externalNames = Sets.newHashSet();

  /** Name declarations or assignments, in post-order traversal order */
  private final List<RefNode> refNodes = Lists.newArrayList();

  /**
   * When multiple names in the global scope point to the same object, we
   * call them aliases. Store a map from each alias name to the alias set.
   */
  private final Map<String, AliasSet> aliases = Maps.newHashMap();

  /**
   * All the aliases in a program form a graph, where each global name is
   * a node in the graph, and two names are connected if one directly aliases
   * the other.
   *
   * An {@code AliasSet} represents a connected component in that graph. We do
   * not explicitly track the graph--we just track the connected components.
   */
  private static class AliasSet {
    Set<String> names = Sets.newHashSet();

    // Every alias set starts with exactly 2 names.
    AliasSet(String name1, String name2) {
      names.add(name1);
      names.add(name2);
    }
  }

  /**
   * Relationship between the two names.
   * Currently only two different reference types exists:
   * goog.inherits class relations and all other references.
   */
  private static enum RefType {
    REGULAR,
    INHERITANCE,
  }

  /**
   * Class to hold information that can be determined from a node tree about a
   * given name
   */
  private static class NameInformation {
    /** Fully qualified name */
    String name;

    /** Whether the name is guaranteed to be externally referenceable */
    boolean isExternallyReferenceable = false;

    /** Whether this name is a prototype function */
    boolean isPrototype = false;

    /** Name of the prototype class, i.e. "a" if name is "a.prototype.b" */
    String prototypeClass = null;

    /** Local name of prototype property i.e. "b" if name is "a.prototype.b" */
    String prototypeProperty = null;

    /** Name of the super class of name */
    String superclass = null;

    /** Whether this is a call that only affects the class definition */
    boolean onlyAffectsClassDef = false;
  }

  /**
   * Struct to hold information about a fully qualified JS name
   */
  private static class JsName implements Comparable<JsName> {
    /** Fully qualified name */
    String name;

    /** Name of prototype functions attached to this name */
    List<String> prototypeNames = Lists.newArrayList();

    /** Whether this is an externally defined name */
    boolean externallyDefined = false;

    /** Whether this node is referenced */
    boolean referenced = false;

    /** Whether the name has descendants that are written to. */
    boolean hasWrittenDescendants = false;

    /** Whether the name is used in a instanceof check */
    boolean hasInstanceOfReference = false;

    /** Whether the name is directly set */
    boolean hasSetterReference = false;

    /**
     * Output the node as a string
     *
     * @return Node as a string
     */
    @Override
    public String toString() {
      StringBuilder out = new StringBuilder();
      out.append(name);

      if (!prototypeNames.isEmpty()) {
        out.append(" (CLASS)\n");
        out.append(" - FUNCTIONS: ");
        Iterator<String> pIter = prototypeNames.iterator();
        while (pIter.hasNext()) {
          out.append(pIter.next());
          if (pIter.hasNext()) {
            out.append(", ");
          }
        }
      }

      return out.toString();
    }

    @Override
    public int compareTo(JsName rhs) {
      return this.name.compareTo(rhs.name);
    }
  }

  /**
   * Interface to get information about and remove unreferenced names.
   */
  interface RefNode {
    JsName name();
    void remove();
  }

  /**
   * Class for nodes that reference a fully-qualified JS name. Fully qualified
   * names are of form A or A.B (A.B.C, etc.). References can get the value or
   * set the value of the JS name.
   */
  private class JsNameRefNode implements RefNode {
    /** JsName node for this reference */
    JsName name;

    /**
     * Parent node of the name access
     * (ASSIGN, VAR, FUNCTION, OBJECTLIT, or CALL)
     */
    Node parent;


    /**
     * Create a node that refers to a name
     *
     * @param name The name
     * @param node The top node representing the name (GETPROP, NAME, STRING)
     */
    JsNameRefNode(JsName name, Node node) {
      this.name = name;
      this.parent = node.getParent();
    }

    @Override
    public JsName name() {
      return name;
    }

    @Override
    public void remove() {
      // Setters have VAR, FUNCTION, or ASSIGN parent nodes. CALL parent
      // nodes are global refs, and are handled later in this function.
      Node containingNode = parent.getParent();
      switch (parent.getType()) {
        case Token.VAR:
          Preconditions.checkState(parent.hasOneChild());
          replaceWithRhs(containingNode, parent);
          break;
        case Token.FUNCTION:
          replaceWithRhs(containingNode, parent);
          break;
        case Token.ASSIGN:
          if (containingNode.isExprResult()) {
            replaceWithRhs(containingNode.getParent(), containingNode);
          } else {
            replaceWithRhs(containingNode, parent);
          }
          break;
        case Token.OBJECTLIT:
          // TODO(nicksantos): Come up with a way to remove this.
          // If we remove object lit keys, then we will need to also
          // create dependency scopes for them.
          break;
      }
    }
  }


  /**
   * Class for nodes that set prototype properties or methods.
   */
  private class PrototypeSetNode extends JsNameRefNode {
    /**
     * Create a set node from the name & setter node
     *
     * @param name The name
     * @param parent Parent node that assigns the expression (an ASSIGN)
     */
    PrototypeSetNode(JsName name, Node parent) {
      super(name, parent.getFirstChild());

      Preconditions.checkState(parent.isAssign());
    }

    @Override public void remove() {
      Node gramps = parent.getParent();
      if (gramps.isExprResult()) {
        // name.prototype.foo = function() { ... };
        changeProxy.removeChild(gramps.getParent(), gramps);
      } else {
        // ... name.prototype.foo = function() { ... } ...
        changeProxy.replaceWith(gramps, parent,
                                parent.getLastChild().detachFromParent());
      }
    }
  }

  /**
   * Base class for special reference nodes.
   */
  private abstract static class SpecialReferenceNode implements RefNode {
    /** JsName node for the function */
    JsName name;

    /** The CALL node */
    Node node;

    /**
     * Create a special reference node.
     *
     * @param name The name
     * @param node The CALL node
     */
    SpecialReferenceNode(JsName name, Node node) {
      this.name = name;
      this.node = node;
    }

    @Override
    public JsName name() {
      return name;
    }

    Node getParent() {
      return node.getParent();
    }

    Node getGramps() {
      return node.getParent() == null ? null : node.getParent().getParent();
    }
  }



  /**
   * Class for nodes that are function calls that may change a function's
   * prototype
   */
  private class ClassDefiningFunctionNode extends SpecialReferenceNode {
    /**
     * Create a class defining function node from the name & setter node
     *
     * @param name The name
     * @param node The CALL node
     */
    ClassDefiningFunctionNode(JsName name, Node node) {
      super(name, node);
      Preconditions.checkState(node.isCall());
    }

    @Override
    public void remove() {
      Preconditions.checkState(node.isCall());
      Node parent = getParent();
      if (parent.isExprResult()) {
        changeProxy.removeChild(getGramps(), parent);
      } else {
        changeProxy.replaceWith(parent, node, IR.voidNode(IR.number(0)));
      }
    }
  }



  /**
   * Class for nodes that check instanceof
   */
  private class InstanceOfCheckNode extends SpecialReferenceNode {
    /**
     * Create an instanceof node from the name and parent node
     *
     * @param name The name
     * @param node The qualified name node
     */
    InstanceOfCheckNode(JsName name, Node node) {
      super(name, node);
      Preconditions.checkState(node.isQualifiedName());
      Preconditions.checkState(getParent().isInstanceOf());
    }

    @Override
    public void remove() {
      changeProxy.replaceWith(getGramps(), getParent(), IR.falseNode());
    }
  }

  /**
   * Walk through externs and mark nodes as externally declared if declared
   */
  private class ProcessExternals extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      NameInformation ns = null;
      if (NodeUtil.isVarDeclaration(n)) {
        ns = createNameInformation(t, n);
      } else if (NodeUtil.isFunctionDeclaration(n)) {
        ns = createNameInformation(t, n.getFirstChild());
      }
      if (ns != null) {
        JsName jsName = getName(ns.name, true);
        jsName.externallyDefined = true;
        externalNames.add(ns.name);
      }
    }
  }

  /**
   * <p>Identifies all dependency scopes.
   *
   * <p>A dependency scope is a relationship between a node tree and a name that
   * implies that the node tree will not execute (and thus can be eliminated) if
   * the name is never referenced.
   *
   * <p>The entire parse tree is ultimately in a dependency scope relationship
   * with <code>window</code> (or an equivalent name for the global scope), but
   * the goal here is to find finer-grained relationships. This callback creates
   * dependency scopes for every assignment statement, variable declaration, and
   * function call in the global scope.
   *
   * <p>Note that dependency scope node trees aren't necessarily disjoint.
   * In the following code snippet, for example, the function definition
   * forms a dependency scope with the name <code>f</code> and the assignment
   * inside the function forms a dependency scope with the name <code>x</code>.
   * <pre>
   * var x; function f() { x = 1; }
   * </pre>
   */
  private class FindDependencyScopes extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!t.inGlobalScope()) {
        return;
      }

      if (n.isAssign()) {
        recordAssignment(t, n, n);
        if (!NodeUtil.isImmutableResult(n.getLastChild())) {
          recordConsumers(t, n, n);
        }
      } else if (NodeUtil.isVarDeclaration(n)) {
        NameInformation ns = createNameInformation(t, n);
        recordDepScope(n, ns);
      } else if (NodeUtil.isFunctionDeclaration(n)) {
        NameInformation ns = createNameInformation(t, n.getFirstChild());
        recordDepScope(n, ns);
      } else if (NodeUtil.isExprCall(n)) {
        Node callNode = n.getFirstChild();
        Node nameNode = callNode.getFirstChild();
        NameInformation ns = createNameInformation(t, nameNode);
        if (ns != null && ns.onlyAffectsClassDef) {
          recordDepScope(n, ns);
        }
      }
    }

    private void recordConsumers(NodeTraversal t, Node n, Node recordNode) {
      Node parent = n.getParent();
      switch (parent.getType()) {
        case Token.ASSIGN:
          if (n == parent.getLastChild()) {
            recordAssignment(t, parent, recordNode);
          }
          recordConsumers(t, parent, recordNode);
          break;
        case Token.NAME:
          NameInformation ns = createNameInformation(t, parent);
          recordDepScope(recordNode, ns);
          break;
        case Token.OR:
          recordConsumers(t, parent, recordNode);
          break;
        case Token.AND:
          // In "a && b" only "b" can be meaningfully aliased.
          // "a" must be falsy, which it must be an immutable, non-Object
        case Token.COMMA:
        case Token.HOOK:
          if (n != parent.getFirstChild()) {
            recordConsumers(t, parent, recordNode);
          }
          break;
      }
    }

    private void recordAssignment(NodeTraversal t, Node n, Node recordNode) {
      Node nameNode = n.getFirstChild();
      Node parent = n.getParent();
      NameInformation ns = createNameInformation(t, nameNode);
      if (ns != null) {
        if (parent.isFor() && !NodeUtil.isForIn(parent)) {
          // Patch for assignments that appear in the init,
          // condition or iteration part of a FOR loop.  Without
          // this change, all 3 of those parts try to claim the for
          // loop as their dependency scope.  The last assignment in
          // those three fields wins, which can result in incorrect
          // reference edges between referenced and assigned variables.
          //
          // TODO(user) revisit the dependency scope calculation
          // logic.
          if (parent.getFirstChild().getNext() != n) {
            recordDepScope(recordNode, ns);
          } else {
            recordDepScope(nameNode, ns);
          }
        } else if (!(parent.isCall() && parent.getFirstChild() == n)) {
          // The rhs of the assignment is the caller, so it's used by the
          // context. Don't associate it w/ the lhs.
          // FYI: this fixes only the specific case where the assignment is the
          // caller expression, but it could be nested deeper in the caller and
          // we would still get a bug.
          // See testAssignWithCall2 for an example of this.
          recordDepScope(recordNode, ns);
        }
      }
    }

    /**
     * Defines a dependency scope.
     */
    private void recordDepScope(Node node, NameInformation name) {
      Preconditions.checkNotNull(name);
      scopes.put(node, name);
    }
  }

  /**
   * Create JsName objects for variable and function declarations in
   * the global scope before computing name references.  In JavaScript
   * it is legal to refer to variable and function names before the
   * actual declaration.
   */
  private class HoistVariableAndFunctionDeclarations
      extends NodeTraversal.AbstractShallowCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (NodeUtil.isVarDeclaration(n)) {
        NameInformation ns = createNameInformation(t, n);
        Preconditions.checkNotNull(ns, "NameInformation is null");
        createName(ns.name);
      } else if (NodeUtil.isFunctionDeclaration(n)) {
        Node nameNode = n.getFirstChild();
        NameInformation ns = createNameInformation(t, nameNode);
        Preconditions.checkNotNull(ns, "NameInformation is null");
        createName(nameNode.getString());
      }
    }
  }

  /**
   * Identifies all declarations of global names and setter statements
   * affecting global symbols (assignments to global names).
   *
   * All declarations and setters must be gathered in a single
   * traversal and stored in traversal order so "removeUnreferenced"
   * can perform modifications in traversal order.
   */
  private class FindDeclarationsAndSetters extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {

      // Record global variable and function declarations
      if (t.inGlobalScope()) {
        if (NodeUtil.isVarDeclaration(n)) {
          NameInformation ns = createNameInformation(t, n);
          Preconditions.checkNotNull(ns);
          recordSet(ns.name, n);
        } else if (NodeUtil.isFunctionDeclaration(n)) {
          Node nameNode = n.getFirstChild();
          NameInformation ns = createNameInformation(t, nameNode);
          if (ns != null) {
            JsName nameInfo = getName(nameNode.getString(), true);
            recordSet(nameInfo.name, nameNode);
          }
        } else if (NodeUtil.isObjectLitKey(n)) {
          NameInformation ns = createNameInformation(t, n);
          if (ns != null) {
            recordSet(ns.name, n);
          }
        }
      }

      // Record assignments and call sites
      if (n.isAssign()) {
        Node nameNode = n.getFirstChild();

        NameInformation ns = createNameInformation(t, nameNode);
        if (ns != null) {
          if (ns.isPrototype) {
            recordPrototypeSet(ns.prototypeClass, ns.prototypeProperty, n);
          } else {
            recordSet(ns.name, nameNode);
          }
        }
      } else if (n.isCall()) {
        Node nameNode = n.getFirstChild();
        NameInformation ns = createNameInformation(t, nameNode);
        if (ns != null && ns.onlyAffectsClassDef) {
          JsName name = getName(ns.name, true);
          refNodes.add(new ClassDefiningFunctionNode(name, n));
        }
      }
    }

    /**
     * Records the assignment of a value to a global name.
     *
     * @param name Fully qualified name
     * @param node The top node representing the name (GETPROP, NAME, or STRING
     * [objlit key])
     */
    private void recordSet(String name, Node node) {
      JsName jsn = getName(name, true);
      JsNameRefNode nameRefNode = new JsNameRefNode(jsn, node);
      refNodes.add(nameRefNode);
      jsn.hasSetterReference = true;

      // Now, look at all parent names and record that their properties have
      // been written to.
      if (node.isGetElem()) {
        recordWriteOnProperties(name);
      } else if (name.indexOf('.') != -1) {
        recordWriteOnProperties(name.substring(0, name.lastIndexOf('.')));
      }
    }

    /**
     * Records the assignment to a prototype property of a global name,
     * if possible.
     *
     * @param className The name of the class.
     * @param prototypeProperty The name of the prototype property.
     * @param node The top node representing the name (GETPROP)
     */
    private void recordPrototypeSet(String className, String prototypeProperty,
        Node node) {
      JsName name = getName(className, true);
      name.prototypeNames.add(prototypeProperty);
      refNodes.add(new PrototypeSetNode(name, node));
      recordWriteOnProperties(className);
    }

    /**
     * Record that the properties of this name have been written to.
     */
    private void recordWriteOnProperties(String parentName) {
      do {
        JsName parent = getName(parentName, true);
        if (parent.hasWrittenDescendants) {
          // If we already recorded this name, then all its parents must
          // also be recorded. short-circuit this loop.
          return;
        } else {
          parent.hasWrittenDescendants = true;
        }

        if (parentName.indexOf('.') == -1) {
          return;
        }
        parentName = parentName.substring(0, parentName.lastIndexOf('.'));
      } while(true);
    }
  }

  private static final Predicate<Node> NON_LOCAL_RESULT_PREDICATE =
      new Predicate<Node>() {
        @Override
        public boolean apply(Node input) {
          if (input.isCall()) {
            return false;
          }
          // TODO(johnlenz): handle NEW calls that record their 'this'
          // in global scope and effectively return an alias.
          // Other non-local references are handled by this pass.
          return true;
        }
      };

  /**
   * <p>Identifies all references between global names.
   *
   * <p>A reference from a name <code>f</code> to a name <code>g</code> means
   * that if the name <code>f</code> must be defined, then the name
   * <code>g</code> must also be defined. This would be the case if, for
   * example, <code>f</code> were a function that called <code>g</code>.
   */
  private class FindReferences implements Callback {
    Set<Node> nodesToKeep;
    FindReferences() {
      nodesToKeep = Sets.newHashSet();
    }

    private void addAllChildren(Node n) {
      nodesToKeep.add(n);
      for (Node child = n.getFirstChild();
           child != null;
           child = child.getNext()) {
        addAllChildren(child);
      }
    }

    private void addSimplifiedChildren(Node n) {
      NodeTraversal.traverse(
          compiler, n,
          new GatherSideEffectSubexpressionsCallback(
              compiler, new NodeAccumulator()));
    }

    private void addSimplifiedExpression(Node n, Node parent) {
      if (parent.isVar()) {
        Node value = n.getFirstChild();
        if (value != null) {
          addSimplifiedChildren(value);
        }
      } else if (n.isAssign() &&
          (parent.isExprResult() ||
           parent.isFor() ||
           parent.isReturn())) {
        for (Node child : n.children()) {
          addSimplifiedChildren(child);
        }
      } else if (n.isCall() &&
                 parent.isExprResult()) {
        addSimplifiedChildren(n);
      } else {
        addAllChildren(n);
      }
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      if (parent == null) {
        return true;
      }

      // Gather the list of nodes that either have side effects, are
      // arguments to function calls with side effects or are used in
      // control structure predicates.  These names are always
      // referenced when the enclosing function is called.
      if (n.isFor()) {
        if (!NodeUtil.isForIn(n)) {
          Node decl = n.getFirstChild();
          Node pred = decl.getNext();
          Node step = pred.getNext();
          addSimplifiedExpression(decl, n);
          addSimplifiedExpression(pred, n);
          addSimplifiedExpression(step, n);
        } else { // n.getChildCount() == 3
          Node decl = n.getFirstChild();
          Node iter = decl.getNext();
          addAllChildren(decl);
          addAllChildren(iter);
        }
      }

      if (parent.isVar() ||
          parent.isExprResult() ||
          parent.isReturn() ||
          parent.isThrow()) {
        addSimplifiedExpression(n, parent);
      }

      if ((parent.isIf() ||
           parent.isWhile() ||
           parent.isWith() ||
           parent.isSwitch() ||
           parent.isCase()) &&
          parent.getFirstChild() == n) {
        addAllChildren(n);
      }

      if (parent.isDo() && parent.getLastChild() == n) {
        addAllChildren(n);
      }

      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!(n.isName() || (NodeUtil.isGet(n) && !parent.isGetProp()))) {
        // This is not a simple or qualified name.
        return;
      }

      NameInformation nameInfo = createNameInformation(t, n);
      if (nameInfo == null) {
        // The name is not a global name
        return;
      }

      if (nameInfo.onlyAffectsClassDef) {
        if (nameInfo.superclass != null) {
          recordReference(
              nameInfo.name, nameInfo.superclass, RefType.INHERITANCE);
        }

        // Make sure that we record a reference to the function that does
        // the inheritance, so that the inherits() function itself does
        // not get stripped.
        String nodeName = n.getQualifiedName();
        if (nodeName != null) {
          recordReference(
              nameInfo.name, nodeName, RefType.REGULAR);
        }

        return;
      }

      // instanceof checks are not handled like regular read references.
      boolean isInstanceOfCheck = parent.isInstanceOf() &&
          parent.getLastChild() == n;
      if (isInstanceOfCheck) {
        JsName checkedClass = getName(nameInfo.name, true);

        // If we know where this constructor is created, and we
        // know we can find all 'new' calls on it, then treat
        // this as a special reference. It will be replaced with
        // false if there are no other references, because we
        // know the class can't be instantiated.
        if (checkedClass.hasSetterReference &&
            !nameInfo.isExternallyReferenceable &&
            // Exclude GETELEMs.
            n.isQualifiedName()) {
          refNodes.add(new InstanceOfCheckNode(checkedClass, n));
          checkedClass.hasInstanceOfReference = true;
          return;
        }
      }

      // Determine which name might be potentially referring to this one by
      // looking up the nearest enclosing dependency scope. It's unnecessary to
      // determine all enclosing dependency scopes because this callback should
      // create a chain of references between them.
      List<NameInformation> referers = getDependencyScope(n);
      if (referers.isEmpty()) {
        maybeRecordReferenceOrAlias(t, n, parent, nameInfo, null);
      } else {
        for (NameInformation referring : referers) {
          maybeRecordReferenceOrAlias(t, n, parent, nameInfo, referring);
        }
        recordAliases(referers);
      }
    }

    private void maybeRecordReferenceOrAlias(
        NodeTraversal t, Node n, Node parent,
        NameInformation nameInfo, NameInformation referring) {
      String referringName = "";
      if (referring != null) {
        referringName = referring.isPrototype
                      ? referring.prototypeClass
                      : referring.name;
      }

      String name = nameInfo.name;

      // A value whose result is the return value of a function call
      // can be an alias to global object.
      // Here we add an alias to the general "global" object
      // to act as a placeholder for the actual (unnamed) value.
      if (maybeHiddenAlias(n)) {
        recordAlias(name, WINDOW);
      }

      // An externally referenceable name must always be defined, so we add a
      // reference to it from the global scope (a.k.a. window).
      if (nameInfo.isExternallyReferenceable) {
        recordReference(WINDOW, name, RefType.REGULAR);
        maybeRecordAlias(name, parent, referring, referringName);
        return;
      }

      // An assignment implies a reference from the enclosing dependency scope.
      // For example, foo references bar in: function foo() {bar=5}.
      if (NodeUtil.isVarOrSimpleAssignLhs(n, parent)) {
        if (referring != null) {
          recordReference(referringName, name, RefType.REGULAR);
        }
        return;
      }

      if (nodesToKeep.contains(n)) {
        List<NameInformation> functionScopes =
            getEnclosingFunctionDependencyScope(t);
        if (!functionScopes.isEmpty()) {
          for (NameInformation functionScope : functionScopes) {
            recordReference(functionScope.name, name, RefType.REGULAR);
          }
        } else {
          recordReference(WINDOW, name, RefType.REGULAR);
          if (referring != null) {
            maybeRecordAlias(name, parent, referring, referringName);
          }
        }
      } else if (referring != null) {
        if (!maybeRecordAlias(name, parent, referring, referringName)) {
          RefType depType = referring.onlyAffectsClassDef ?
              RefType.INHERITANCE : RefType.REGULAR;
          recordReference(referringName, name, depType);
        }
      } else {
        // No named dependency scope found.  Unfortunately that might
        // mean that the expression is a child of an function expression
        // or assignment with a complex lhs.  In those cases,
        // protect this node by creating a reference to WINDOW.
        for (Node ancestor : n.getAncestors()) {
          if (NodeUtil.isAssignmentOp(ancestor) ||
              ancestor.isFunction()) {
            recordReference(WINDOW, name, RefType.REGULAR);
            break;
          }
        }
      }
    }

    private void recordAliases(List<NameInformation> referers) {
      int size = referers.size();
      for (int i = 0; i < size; i++) {
        for (int j = i + 1; j < size; j++) {
          recordAlias(referers.get(i).name, referers.get(j).name);
          recordAlias(referers.get(j).name, referers.get(i).name);
        }
      }
    }

    /**
     * A value whose result is the return value of a function call
     * can be an alias to global object. The dependency on the call target will
     * prevent the removal of the function and its dependent values, but won't
     * prevent the alias' removal.
     */
    private boolean maybeHiddenAlias(Node n) {
      Node parent = n.getParent();
      if (NodeUtil.isVarOrSimpleAssignLhs(n, parent)) {
        Node rhs = (parent.isVar())
            ? n.getFirstChild() : parent.getLastChild();
        return (rhs != null && !NodeUtil.evaluatesToLocalValue(
            rhs, NON_LOCAL_RESULT_PREDICATE));
      }
      return false;
    }

    /**
     * @return Whether the alias was recorded.
     */
    private boolean maybeRecordAlias(
        String name, Node parent,
        NameInformation referring, String referringName) {
      // A common type of reference is
      // function F() {}
      // F.prototype.bar = goog.nullFunction;
      //
      // In this specific case, we do not want a reference to goog.nullFunction
      // to preserve F.
      //
      // In the general case, the user could do something like
      // function F() {}
      // F.prototype.bar = goog.nullFunction;
      // F.prototype.bar.baz = 3;
      // where it would not be safe to remove F.
      //
      // So we do not treat this alias as a backdoor for people to mutate the
      // original object. We think that this heuristic will always be
      // OK in real code.
      boolean isPrototypePropAssignment =
          parent.isAssign()
          && NodeUtil.isPrototypeProperty(parent.getFirstChild());

      if ((parent.isName() || parent.isAssign()) && !isPrototypePropAssignment && referring != null
          && scopes.containsEntry(parent, referring)) {
        recordAlias(referringName, name);
        return true;
      }
      return false;
    }

    /**
     * Helper class that gathers the list of nodes that would be left
     * behind after simplification.
     */
    private class NodeAccumulator
        implements SideEffectAccumulator {

      @Override
      public boolean classDefiningCallsHaveSideEffects() {
        return false;
      }

      @Override
      public void keepSubTree(Node original) {
        addAllChildren(original);
      }

      @Override
      public void keepSimplifiedShortCircuitExpression(Node original) {
        Node condition = original.getFirstChild();
        Node thenBranch = condition.getNext();
        addAllChildren(condition);
        addSimplifiedChildren(thenBranch);
      }

      @Override
      public void keepSimplifiedHookExpression(Node hook,
                                               boolean thenHasSideEffects,
                                               boolean elseHasSideEffects) {
        Node condition = hook.getFirstChild();
        Node thenBranch = condition.getNext();
        Node elseBranch = thenBranch.getNext();
        addAllChildren(condition);
        if (thenHasSideEffects) {
          addSimplifiedChildren(thenBranch);
        }
        if (elseHasSideEffects) {
          addSimplifiedChildren(elseBranch);
        }
      }
    }
  }

  private class RemoveListener implements AstChangeProxy.ChangeListener {
    @Override
    public void nodeRemoved(Node n) {
      compiler.reportCodeChange();
    }
  }

  /**
   * Creates a name analyzer, with option to remove unreferenced variables when
   * calling process().
   *
   * The analyzer make a best guess at whether functions affect global scope
   * based on usage (no assignment of return value means that a function has
   * side effects).
   *
   * @param compiler The AbstractCompiler
   * @param removeUnreferenced If true, remove unreferenced variables during
   *        process()
   */
  NameAnalyzer(AbstractCompiler compiler, boolean removeUnreferenced) {
    this.compiler = compiler;
    this.removeUnreferenced = removeUnreferenced;
    this.globalNames = DEFAULT_GLOBAL_NAMES;
    this.changeProxy = new AstChangeProxy();
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, externs, new ProcessExternals());
    NodeTraversal.traverse(compiler, root, new FindDependencyScopes());
    NodeTraversal.traverse(
        compiler, root, new HoistVariableAndFunctionDeclarations());
    NodeTraversal.traverse(compiler, root, new FindDeclarationsAndSetters());
    NodeTraversal.traverse(compiler, root, new FindReferences());

    // Create bi-directional references between parent names and their
    // descendants. This may create new names.
    referenceParentNames();

    // If we modify the property of an alias, make sure that modification
    // gets reflected in the original object.
    referenceAliases();

    calculateReferences();

    if (removeUnreferenced) {
      removeUnreferenced();
    }
  }

  /**
   * Records an alias of one name to another name.
   */
  private void recordAlias(String fromName, String toName) {
    recordReference(fromName, toName, RefType.REGULAR);

    // We need to add an edge to the alias graph. The alias graph is expressed
    // implicitly as a set of connected components, called AliasSets.
    //
    // There are three possibilities:
    // 1) Neither name is part of a connected component. Create a new one.
    // 2) Exactly one name is part of a connected component. Merge the new
    //    name into the component.
    // 3) The two names are already part of connected components. Merge
    //    those components together.
    AliasSet toNameAliasSet = aliases.get(toName);
    AliasSet fromNameAliasSet = aliases.get(fromName);
    AliasSet resultSet = null;
    if (toNameAliasSet == null && fromNameAliasSet == null) {
      resultSet = new AliasSet(toName, fromName);
    } else if (toNameAliasSet != null && fromNameAliasSet != null) {
      resultSet = toNameAliasSet;
      resultSet.names.addAll(fromNameAliasSet.names);
      for (String name : fromNameAliasSet.names) {
        aliases.put(name, resultSet);
      }
    } else if (toNameAliasSet != null) {
      resultSet = toNameAliasSet;
      resultSet.names.add(fromName);
    } else {
      resultSet = fromNameAliasSet;
      resultSet.names.add(toName);
    }
    aliases.put(fromName, resultSet);
    aliases.put(toName, resultSet);
  }

  /**
   * Records a reference from one name to another name.
   */
  private void recordReference(String fromName, String toName,
                               RefType depType) {
    if (fromName.equals(toName)) {
      // Don't bother recording self-references.
      return;
    }

    JsName from = getName(fromName, true);
    JsName to = getName(toName, true);
    referenceGraph.connectIfNotConnectedInDirection(from, depType, to);
  }

  /**
   * Records a reference from one name to another name.
   */
  private void recordReference(
      DiGraphNode<JsName, RefType> from,
      DiGraphNode<JsName, RefType> to,
      RefType depType) {
    if (from == to) {
      // Don't bother recording self-references.
      return;
    }

    if (!referenceGraph.isConnectedInDirection(from, Predicates.equalTo(depType), to)) {
      referenceGraph.connect(from, depType, to);
    }
  }

  /**
   * Removes all unreferenced variables.
   */
  void removeUnreferenced() {
    RemoveListener listener = new RemoveListener();
    changeProxy.registerListener(listener);

    for (RefNode refNode : refNodes) {
      JsName name = refNode.name();
      if (!name.referenced && !name.externallyDefined) {
        refNode.remove();
      }
    }

    changeProxy.unregisterListener(listener);
  }

  /**
   * Generates an HTML report
   *
   * @return The report
   */
  String getHtmlReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("<html><body><style type=\"text/css\">"
        + "body, td, p {font-family: Arial; font-size: 83%} "
        + "ul {margin-top:2px; margin-left:0px; padding-left:1em;} "
        + "li {margin-top:3px; margin-left:24px; padding-left:0px;"
        + "padding-bottom: 4px}</style>");
    sb.append("OVERALL STATS<ul>");
    appendListItem(sb, "Total Names: " + countOf(TriState.BOTH, TriState.BOTH));
    appendListItem(sb, "Total Classes: "
        + countOf(TriState.TRUE, TriState.BOTH));
    appendListItem(sb, "Total Static Functions: "
        + countOf(TriState.FALSE, TriState.BOTH));
    appendListItem(sb, "Referenced Names: "
        + countOf(TriState.BOTH, TriState.TRUE));
    appendListItem(sb, "Referenced Classes: "
        + countOf(TriState.TRUE, TriState.TRUE));
    appendListItem(sb, "Referenced Functions: "
        + countOf(TriState.FALSE, TriState.TRUE));
    sb.append("</ul>");

    sb.append("ALL NAMES<ul>\n");
    for (JsName node : allNames.values()) {
      sb.append("<li>" + nameAnchor(node.name) + "<ul>");
      if (!node.prototypeNames.isEmpty()) {
        sb.append("<li>PROTOTYPES: ");
        Iterator<String> protoIter = node.prototypeNames.iterator();
        while (protoIter.hasNext()) {
          sb.append(protoIter.next());
          if (protoIter.hasNext()) {
            sb.append(", ");
          }
        }
      }

      if (referenceGraph.hasNode(node)) {
        List<DiGraphEdge<JsName, RefType>> refersTo =
            referenceGraph.getOutEdges(node);
        if (!refersTo.isEmpty()) {
          sb.append("<li>REFERS TO: ");
          Iterator<DiGraphEdge<JsName, RefType>> toIter = refersTo.iterator();
          while (toIter.hasNext()) {
            sb.append(nameLink(toIter.next().getDestination().getValue().name));
            if (toIter.hasNext()) {
              sb.append(", ");
            }
          }
        }

        List<DiGraphEdge<JsName, RefType>> referencedBy =
            referenceGraph.getInEdges(node);
        if (!referencedBy.isEmpty()) {
          sb.append("<li>REFERENCED BY: ");
          Iterator<DiGraphEdge<JsName, RefType>> fromIter = refersTo.iterator();
          while (fromIter.hasNext()) {
            sb.append(
                nameLink(fromIter.next().getDestination().getValue().name));
            if (fromIter.hasNext()) {
              sb.append(", ");
            }
          }
        }
      }
      sb.append("</li>");
      sb.append("</ul></li>");
    }
    sb.append("</ul>");
    sb.append("</body></html>");

    return sb.toString();
  }

  private static void appendListItem(StringBuilder sb, String text) {
    sb.append("<li>" + text + "</li>\n");
  }

  private static String nameLink(String name) {
    return "<a href=\"#" + name + "\">" + name + "</a>";
  }

  private static String nameAnchor(String name) {
    return "<a name=\"" + name + "\">" + name + "</a>";
  }

  /**
   * Looks up a {@link JsName} by name, optionally creating one if it doesn't
   * already exist.
   *
   * @param name A fully qualified name
   * @param canCreate Whether to create the object if necessary
   * @return The {@code JsName} object, or null if one can't be found and
   *   can't be created.
   */
  private JsName getName(String name, boolean canCreate) {
    if (canCreate) {
      createName(name);
    }
    return allNames.get(name);
  }

  /**
   * Creates a {@link JsName} for the given name if it doesn't already
   * exist.
   *
   * @param name A fully qualified name
   */
  private void createName(String name) {
    JsName jsn = allNames.get(name);
    if (jsn == null) {
      jsn = new JsName();
      jsn.name = name;
      allNames.put(name, jsn);
    }
  }

  /**
   * The NameAnalyzer algorithm works best when all objects have a canonical
   * name in the global scope. When multiple names in the global scope
   * point to the same object, things start to break down.
   *
   * For example, if we have
   * <code>
   * var a = {};
   * var b = a;
   * a.foo = 3;
   * alert(b.foo);
   * </code>
   * then a.foo and b.foo are the same name, even though NameAnalyzer doesn't
   * represent them as such.
   *
   * To handle this case, we look at all the aliases in the program.
   * If descendant properties of that alias are assigned, then we create a
   * directional reference from the original name to the alias. For example,
   * in this case, the assign to {@code a.foo} triggers a reference from
   * {@code b} to {@code a}, but NOT from a to b.
   *
   * Similarly, "instanceof" checks do not prevent the removal
   * of a unaliased name but an instanceof check on an alias can only be removed
   * if the other aliases are also removed, so we add a connection here.
   */
  private void referenceAliases() {

    // Minimize the number of connections in the graph by creating a connected
    // cluster for names that are used to modify the object and then ensure
    // there is at least one link to the cluster from the other names (which are
    // removalable on there own) in the AliasSet.

    Set<AliasSet> sets = new HashSet<>(aliases.values());
    for (AliasSet set : sets) {
      DiGraphNode<JsName, RefType> first = null;
      Set<DiGraphNode<JsName, RefType>> required = new HashSet<>();
      for (String key : set.names) {
        JsName name = getName(key, false);
        if (name.hasWrittenDescendants || name.hasInstanceOfReference) {
          DiGraphNode<JsName, RefType> node = getGraphNode(name);
          required.add(node);
          if (first == null) {
            first = node;
          }
        }
      }

      if (!required.isEmpty()) {
        // link the required nodes together to form a cluster so that if one
        // is needed, all are kept.
        for (DiGraphNode<JsName, RefType> node : required) {
          recordReference(node, first, RefType.REGULAR);
          recordReference(first, node, RefType.REGULAR);
        }

        // link all the other aliases to the one of the required nodes, so
        // that if they are kept only if referenced directly, but all the
        // required nodes are kept if any are referenced.
        for (String key : set.names) {
          DiGraphNode<JsName, RefType> alias = getGraphNode(getName(key, false));
          recordReference(alias, first, RefType.REGULAR);
        }
      }
    }
  }

  private DiGraphNode<JsName, RefType> getGraphNode(JsName name) {
    return referenceGraph.createDirectedGraphNode(name);
  }

  /**
   * Adds mutual references between all known global names and their parent
   * names. (e.g. between <code>a.b.c</code> and <code>a.b</code>).
   */
  private void referenceParentNames() {
    // Duplicate set of nodes to process so we don't modify set we are
    // currently iterating over
    Set<JsName> allNamesCopy = Sets.newHashSet(allNames.values());

    for (JsName name : allNamesCopy) {
      String curName = name.name;
      // Add a reference to the direct parent. It in turn will point to its parent.
      if (curName.contains(".")) {
        String parentName = curName.substring(0, curName.lastIndexOf('.'));
        if (!globalNames.contains(parentName)) {

          JsName parentJsName = getName(parentName, true);

          DiGraphNode<JsName, RefType> nameNode = getGraphNode(name);
          DiGraphNode<JsName, RefType> parentNode = getGraphNode(parentJsName);

          recordReference(nameNode, parentNode, RefType.REGULAR);
          recordReference(parentNode, nameNode, RefType.REGULAR);
        }
      }
    }
  }

  /**
   * Creates name information for the current node during a traversal.
   *
   * @param t The node traversal
   * @param n The current node
   * @return The name information, or null if the name is irrelevant to this
   *     pass
   */
  private NameInformation createNameInformation(NodeTraversal t, Node n) {
    Node parent = n.getParent();
    // Build the full name and find its root node by iterating down through all
    // GETPROP/GETELEM nodes.
    String name = "";
    Node rootNameNode = n;
    boolean bNameWasShortened = false;
    while (true) {
      if (NodeUtil.isGet(rootNameNode)) {
        Node prop = rootNameNode.getLastChild();
        if (rootNameNode.isGetProp()) {
          name = "." + prop.getString() + name;
        } else {
          // We consider the name to be "a.b" in a.b['c'] or a.b[x].d.
          bNameWasShortened = true;
          name = "";
        }
        rootNameNode = rootNameNode.getFirstChild();
      } else if (NodeUtil.isObjectLitKey(rootNameNode)) {
        name = "." + rootNameNode.getString() + name;

        // Check if this is an object literal assigned to something.
        Node objLit = rootNameNode.getParent();
        Node objLitParent = objLit.getParent();
        if (objLitParent.isAssign()) {
          // This must be the right side of the assign.
          rootNameNode = objLitParent.getFirstChild();
        } else if (objLitParent.isName()) {
          // This must be a VAR initialization.
          rootNameNode = objLitParent;
        } else if (objLitParent.isStringKey()) {
          // This must be a object literal key initialization.
          rootNameNode = objLitParent;
        } else {
          return null;
        }
      } else {
        break;
      }
    }

    // Check whether this is a class-defining call. Classes may only be defined
    // in the global scope.
    if (parent.isCall() && t.inGlobalScope()) {
      CodingConvention convention = compiler.getCodingConvention();
      SubclassRelationship classes = convention.getClassesDefinedByCall(parent);
      if (classes != null) {
        NameInformation nameInfo = new NameInformation();
        nameInfo.name = classes.subclassName;
        nameInfo.onlyAffectsClassDef = true;
        nameInfo.superclass = classes.superclassName;
        return nameInfo;
      }

      String singletonGetterClass =
          convention.getSingletonGetterClassName(parent);
      if (singletonGetterClass != null) {
        NameInformation nameInfo = new NameInformation();
        nameInfo.name = singletonGetterClass;
        nameInfo.onlyAffectsClassDef = true;
        return nameInfo;
      }
    }

    switch (rootNameNode.getType()) {
      case Token.NAME:
        // Check whether this is an assignment to a prototype property
        // of an object defined in the global scope.
        if (!bNameWasShortened &&
            n.isGetProp() &&
            parent.isAssign() &&
            "prototype".equals(n.getLastChild().getString())) {
          if (createNameInformation(t, n.getFirstChild()) != null) {
            name = rootNameNode.getString() + name;
            name = name.substring(0, name.length() - PROTOTYPE_SUFFIX_LEN);
            NameInformation nameInfo = new NameInformation();
            nameInfo.name = name;
            return nameInfo;
          } else {
            return null;
          }
        }
        return createNameInformation(
            rootNameNode.getString() + name, t.getScope(), rootNameNode);
      case Token.THIS:
        if (t.inGlobalScope()) {
          NameInformation nameInfo = new NameInformation();
          if (name.indexOf('.') == 0) {
            nameInfo.name = name.substring(1);  // strip leading "."
          } else {
            nameInfo.name = name;
          }
          nameInfo.isExternallyReferenceable = true;
          return nameInfo;
        }
        return null;
      default:
        return null;
    }
  }

  /**
   * Creates name information for a particular qualified name that occurs in a
   * particular scope.
   *
   * @param name A qualified name (e.g. "x" or "a.b.c")
   * @param scope The scope in which {@code name} occurs
   * @param rootNameNode The NAME node for the first token of {@code name}
   * @return The name information, or null if the name is irrelevant to this
   *     pass
   */
  private NameInformation createNameInformation(
      String name, Scope scope, Node rootNameNode) {
    // Check the scope. Currently we're only looking at globally scoped vars.
    String rootName = rootNameNode.getString();
    Var v = scope.getVar(rootName);
    boolean isExtern = (v == null && externalNames.contains(rootName));
    boolean isGlobalRef = (v != null && v.isGlobal()) || isExtern ||
        rootName.equals(WINDOW);
    if (!isGlobalRef) {
      return null;
    }

    NameInformation nameInfo = new NameInformation();

    // If a prototype property or method, fill in prototype information.
    int idx = name.indexOf(PROTOTYPE_SUBSTRING);
    if (idx != -1) {
      nameInfo.isPrototype = true;
      nameInfo.prototypeClass = name.substring(0, idx);
      nameInfo.prototypeProperty = name.substring(
          idx + PROTOTYPE_SUBSTRING_LEN);
    }

    nameInfo.name = name;
    nameInfo.isExternallyReferenceable =
        isExtern || isExternallyReferenceable(scope, name);
    return nameInfo;
  }

  /**
   * Checks whether a name can be referenced outside of the compiled code.
   * These names will be the root of dependency trees.
   *
   * @param scope The current variable scope
   * @param name The name
   * @return True if can be referenced outside
   */
  private boolean isExternallyReferenceable(Scope scope, String name) {
    if (compiler.getCodingConvention().isExported(name)) {
      return true;
    }
    if (scope.isLocal()) {
      return false;
    }
    for (String s : globalNames) {
      if (name.startsWith(s)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Gets the nearest enclosing dependency scope, or null if there isn't one.
   */
  private List<NameInformation> getDependencyScope(Node n) {
    for (Node node : n.getAncestors()) {
      List<NameInformation> refs = scopes.get(node);
      if (!refs.isEmpty()) {
        return refs;
      }
    }

    return Collections.emptyList();
  }

  /**
   * Get dependency scope defined by the enclosing function, or null.
   * If enclosing function is a function expression, determine scope based on
   * its parent if the parent node is a variable declaration or
   * assignment.
   */
  private List<NameInformation> getEnclosingFunctionDependencyScope(
      NodeTraversal t) {
    Node function = t.getEnclosingFunction();
    if (function == null) {
      return Collections.emptyList();
    }

    List<NameInformation> refs = scopes.get(function);
    if (!refs.isEmpty()) {
      return refs;
    }

    // Function expression.  try to get a name from the parent var
    // declaration or assignment.
    Node parent = function.getParent();
    if (parent != null) {
      // Account for functions defined in the form:
      //   var a = cond ? function a() {} : function b() {};
      while (parent.isHook()) {
        parent = parent.getParent();
      }

      if (parent.isName()) {
        return scopes.get(parent);
      }

      if (parent.isAssign()) {
        return scopes.get(parent);
      }
    }

    return Collections.emptyList();
  }

  /**
   * Propagate "referenced" property down the graph.
   */
  private void calculateReferences() {
    JsName window = getName(WINDOW, true);
    window.referenced = true;
    JsName function = getName(FUNCTION, true);
    function.referenced = true;

    propagateReference(window, function);
  }

  private void propagateReference(JsName ... names) {
    Deque<DiGraphNode<JsName, RefType>> work = new ArrayDeque<>();
    for (JsName name : names) {
      work.push(referenceGraph.createDirectedGraphNode(name));
    }
    while (!work.isEmpty()) {
      DiGraphNode<JsName, RefType> source = work.pop();
      List<DiGraphEdge<JsName, RefType>> outEdges = source.getOutEdges();
      int len = outEdges.size();
      for (int i = 0; i < len; i++) {
        DiGraphNode<JsName, RefType> item = outEdges.get(i).getDestination();
        JsName destNode = item.getValue();
        if (!destNode.referenced) {
          destNode.referenced = true;
          work.push(item);
        }
      }
    }
  }


  /**
   * Enum for saying a value can be true, false, or either (cleaner than using a
   * Boolean with null)
   */
  private enum TriState {
    /** If value is true */
    TRUE,
    /** If value is false */
    FALSE,
    /** If value can be true or false */
    BOTH
  }

  /**
   * Gets the count of nodes matching the criteria
   *
   * @param isClass Whether the node is a class
   * @param referenced Whether the node is referenced
   * @return Number of matches
   */
  private int countOf(TriState isClass, TriState referenced) {
    int count = 0;
    for (JsName name : allNames.values()) {
      boolean nodeIsClass = !name.prototypeNames.isEmpty();

      boolean classMatch =
          isClass == TriState.BOTH || (nodeIsClass && isClass == TriState.TRUE)
          || (!nodeIsClass && isClass == TriState.FALSE);

      boolean referenceMatch = referenced == TriState.BOTH
          || (name.referenced && referenced == TriState.TRUE)
          || (!name.referenced && referenced == TriState.FALSE);

      if (classMatch && referenceMatch && !name.externallyDefined) {
        count++;
      }
    }
    return count;
  }


  /**
   * Extract a list of replacement nodes to use.
   */
  private List<Node> getSideEffectNodes(Node n) {
    List<Node> subexpressions = Lists.newArrayList();
    NodeTraversal.traverse(
        compiler, n,
        new GatherSideEffectSubexpressionsCallback(
            compiler,
            new GetReplacementSideEffectSubexpressions(
                compiler, subexpressions)));

    List<Node> replacements =
        Lists.newArrayListWithExpectedSize(subexpressions.size());
    for (Node subexpression : subexpressions) {
      replacements.add(NodeUtil.newExpr(subexpression));
    }
    return replacements;
  }

  /**
   * Replace n with a simpler expression, while preserving program
   * behavior.
   *
   * If the n's value is used, replace it with its RHS; otherwise
   * replace it with the subexpressions that have side effects.
   */
  private void replaceWithRhs(Node parent, Node n) {
    if (valueConsumedByParent(n, parent)) {
      // parent reads from n directly; replace it with n's rhs + lhs
      // subexpressions with side effects.
      List<Node> replacements = getRhsSubexpressions(n);
      List<Node> newReplacements = Lists.newArrayList();
      for (int i = 0; i < replacements.size() - 1; i++) {
        newReplacements.addAll(getSideEffectNodes(replacements.get(i)));
      }
      Node valueExpr = replacements.get(replacements.size() - 1);
      valueExpr.detachFromParent();
      newReplacements.add(valueExpr);
      changeProxy.replaceWith(
          parent, n, collapseReplacements(newReplacements));
    } else if (n.isAssign() && !parent.isFor()) {
      // assignment appears in a RHS expression.  we have already
      // considered names in the assignment's RHS as being referenced;
      // replace the assignment with its RHS.
      // TODO(user) make the pass smarter about these cases and/or run
      // this pass and RemoveConstantExpressions together in a loop.
      Node replacement = n.getLastChild();
      replacement.detachFromParent();
      changeProxy.replaceWith(parent, n, replacement);
    } else {
      replaceTopLevelExpressionWithRhs(parent, n);
    }
  }

  /**
   * Simplify a toplevel expression, while preserving program
   * behavior.
   */
  private void replaceTopLevelExpressionWithRhs(Node parent, Node n) {
    // validate inputs
    switch (parent.getType()) {
      case Token.BLOCK:
      case Token.SCRIPT:
      case Token.FOR:
      case Token.LABEL:
        break;
      default:
        throw new IllegalArgumentException(
            "Unsupported parent node type in replaceWithRhs " +
            Token.name(parent.getType()));
    }

    switch (n.getType()) {
      case Token.EXPR_RESULT:
      case Token.FUNCTION:
      case Token.VAR:
        break;
      case Token.ASSIGN:
        Preconditions.checkArgument(parent.isFor(),
            "Unsupported assignment in replaceWithRhs. parent: %s",
            Token.name(parent.getType()));
        break;
      default:
        throw new IllegalArgumentException(
            "Unsupported node type in replaceWithRhs " +
            Token.name(n.getType()));
    }

    // gather replacements
    List<Node> replacements = Lists.newArrayList();
    for (Node rhs : getRhsSubexpressions(n)) {
      replacements.addAll(getSideEffectNodes(rhs));
    }

    if (parent.isFor()) {
      // tweak replacements array s.t. it is a single expression node.
      if (replacements.isEmpty()) {
        replacements.add(IR.empty());
      } else {
        Node expr = collapseReplacements(replacements);
        replacements.clear();
        replacements.add(expr);
      }
    }

    changeProxy.replaceWith(parent, n, replacements);
  }

  /**
   * Determine if the parent reads the value of a child expression
   * directly.  This is true children used in predicates, RETURN
   * statements and, RHS of variable declarations and assignments.
   *
   * In the case of:
   * if (a) b else c
   *
   * This method returns true for "a", and false for "b" and "c": the
   * IF expression does something special based on "a"'s value.  "b"
   * and "c" are effectively outputs.  Same logic applies to FOR,
   * WHILE and DO loop predicates.  AND/OR/HOOK expressions are
   * syntactic sugar for IF statements; therefore this method returns
   * true for the predicate and false otherwise.
   */
  private static boolean valueConsumedByParent(Node n, Node parent) {
    if (NodeUtil.isAssignmentOp(parent)) {
      return parent.getLastChild() == n;
    }

    switch (parent.getType()) {
      case Token.NAME:
      case Token.RETURN:
        return true;
      case Token.AND:
      case Token.OR:
      case Token.HOOK:
        return parent.getFirstChild() == n;
      case Token.FOR:
        return parent.getFirstChild().getNext() == n;
      case Token.IF:
      case Token.WHILE:
        return parent.getFirstChild() == n;
      case Token.DO:
        return parent.getLastChild() == n;
      default:
        return false;
    }
  }

  /**
   * Merge a list of nodes into a single expression.  The value of the
   * new expression is determined by the last expression in the list.
   */
  private static Node collapseReplacements(List<Node> replacements) {
    Node expr = null;
    for (Node rep : replacements) {
      if (rep.isExprResult()) {
        rep = rep.getFirstChild();
        rep.detachFromParent();
      }

      if (expr == null) {
        expr = rep;
      } else {
        expr = IR.comma(expr, rep);
      }
    }

    return expr;
  }

  /**
   * Extract a list of subexpressions that act as right hand sides.
   */
  private static List<Node> getRhsSubexpressions(Node n) {
    switch (n.getType()) {
      case Token.EXPR_RESULT:
        // process body
        return getRhsSubexpressions(n.getFirstChild());
      case Token.FUNCTION:
        // function nodes have no RHS
        return Collections.emptyList();
      case Token.NAME:
        {
          // parent is a var node.  RHS is the first child
          Node rhs = n.getFirstChild();
          if (rhs != null) {
            return Lists.newArrayList(rhs);
          } else {
            return Collections.emptyList();
          }
        }
      case Token.ASSIGN:
        {
          // add LHS and RHS expressions - LHS may be a complex expression
          Node lhs = n.getFirstChild();
          Node rhs = lhs.getNext();
          return Lists.newArrayList(lhs, rhs);
        }
      case Token.VAR:
        {
          // recurse on all children
          List<Node> nodes = Lists.newArrayList();
          for (Node child : n.children()) {
            nodes.addAll(getRhsSubexpressions(child));
          }
          return nodes;
        }
      default:
        throw new IllegalArgumentException("AstChangeProxy::getRhs " + n);
    }
  }
}
