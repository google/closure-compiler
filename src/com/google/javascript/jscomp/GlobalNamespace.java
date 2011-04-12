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
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Builds a global namespace of all the objects and their properties in
 * the global scope. Also builds an index of all the references to those names.
 *
 */
class GlobalNamespace {

  private AbstractCompiler compiler;
  private final Node root;
  private final Node externsRoot;
  private boolean inExterns;
  private Scope externsScope;
  private boolean generated = false;

  /** Global namespace tree */
  private List<Name> globalNames = new ArrayList<Name>();

  /** Maps names (e.g. "a.b.c") to nodes in the global namespace tree */
  private Map<String, Name> nameMap = new HashMap<String, Name>();

  /**
   * Creates an instance that may emit warnings when building the namespace.
   *
   * @param compiler The AbstractCompiler, for reporting code changes
   * @param root The root of the rest of the code to build a namespace for.
   */
  GlobalNamespace(AbstractCompiler compiler, Node root) {
    this(compiler, null, root);
  }

  /**
   * Creates an instance that may emit warnings when building the namespace.
   *
   * @param compiler The AbstractCompiler, for reporting code changes
   * @param externsRoot The root of the externs to build a namespace for. If
   *     this is null, externs and properties defined on extern types will not
   *     be included in the global namespace.  If non-null, it allows
   *     user-defined function on extern types to be included in the global
   *     namespace.  E.g. String.foo.
   * @param root The root of the rest of the code to build a namespace for.
   */
  GlobalNamespace(AbstractCompiler compiler, Node externsRoot, Node root) {
    this.compiler = compiler;
    this.externsRoot = externsRoot;
    this.root = root;
  }

  /**
   * Gets a list of the roots of the forest of the global names, where the
   * roots are the top-level names.
   */
  List<Name> getNameForest() {
    if (!generated) {
      process();
    }
    return globalNames;
  }

  /**
   * Gets an index of all the global names, indexed by full qualified name
   * (as in "a", "a.b.c", etc.).
   */
  Map<String, Name> getNameIndex() {
    if (!generated) {
      process();
    }
    return nameMap;
  }

  /**
   * If the client adds new nodes to the AST, scan these new nodes
   * to see if they've added any references to the global namespace.
   * @param scope The scope to scan.
   * @param newNodes New nodes to check.
   */
  void scanNewNodes(Scope scope, Set<Node> newNodes) {
    NodeTraversal t = new NodeTraversal(compiler,
        new BuildGlobalNamespace(new NodeFilter(newNodes)));
    t.traverseAtScope(scope);
  }

  /**
   * A filter that looks for qualified names that contain one of the nodes
   * in the given set.
   */
  private static class NodeFilter implements Predicate<Node> {
    private final Set<Node> newNodes;

    NodeFilter(Set<Node> newNodes) {
      this.newNodes = newNodes;
    }

    public boolean apply(Node n) {
      if (!n.isQualifiedName()) {
        return false;
      }

      Node current;
      for (current = n;
           current.getType() == Token.GETPROP;
           current = current.getFirstChild()) {
        if (newNodes.contains(current)) {
          return true;
        }
      }

      return current.getType() == Token.NAME && newNodes.contains(current);
    }
  }

  /**
   * Builds the namespace lazily.
   */
  private void process() {
    if (externsRoot != null) {
      inExterns = true;
      NodeTraversal.traverse(compiler, externsRoot, new BuildGlobalNamespace());
    }
    inExterns = false;

    NodeTraversal.traverse(compiler, root, new BuildGlobalNamespace());
    generated = true;
  }

  /**
   * Determines whether a name reference in a particular scope is a global name
   * reference.
   *
   * @param name A variable or property name (e.g. "a" or "a.b.c.d")
   * @param s The scope in which the name is referenced
   * @return Whether the name reference is a global name reference
   */
  private boolean isGlobalNameReference(String name, Scope s) {
    String topVarName = getTopVarName(name);
    return isGlobalVarReference(topVarName, s);
  }

  /**
   * Gets the top variable name from a possibly namespaced name.
   *
   * @param name A variable or qualified property name (e.g. "a" or "a.b.c.d")
   * @return The top variable name (e.g. "a")
   */
  private String getTopVarName(String name) {
    int firstDotIndex = name.indexOf('.');
    return firstDotIndex == -1 ? name : name.substring(0, firstDotIndex);
  }

  /**
   * Determines whether a variable name reference in a particular scope is a
   * global variable reference.
   *
   * @param name A variable name (e.g. "a")
   * @param s The scope in which the name is referenced
   * @return Whether the name reference is a global variable reference
   */
  private boolean isGlobalVarReference(String name, Scope s) {
    Scope.Var v = s.getVar(name);
    if (v == null && externsScope != null) {
      v = externsScope.getVar(name);
    }
    return v != null && !v.isLocal();
  }

  /**
   * Gets whether a scope is the global scope.
   *
   * @param s A scope
   * @return Whether the scope is the global scope
   */
  private boolean isGlobalScope(Scope s) {
    return s.getParent() == null;
  }

  // -------------------------------------------------------------------------

  /**
   * Builds a tree representation of the global namespace. Omits prototypes.
   */
  private class BuildGlobalNamespace extends AbstractPostOrderCallback {

    private final Predicate<Node> nodeFilter;

    BuildGlobalNamespace() {
      this(null);
    }

    /**
     * Builds a global namepsace, but only visits nodes that match the
     * given filter.
     */
    BuildGlobalNamespace(Predicate<Node> nodeFilter) {
      this.nodeFilter = nodeFilter;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (nodeFilter != null && !nodeFilter.apply(n)) {
        return;
      }

      // If we are traversing the externs, then we save a pointer to the scope
      // generated by them, so that we can do lookups in it later.
      if (externsRoot != null && n == externsRoot) {
        externsScope = t.getScope();
      }

      String name;
      boolean isSet = false;
      Name.Type type = Name.Type.OTHER;
      boolean isPropAssign = false;

      switch (n.getType()) {
        case Token.GET:
        case Token.SET:
        case Token.STRING:
          // This may be a key in an object literal declaration.
          name = null;
          if (parent != null && parent.getType() == Token.OBJECTLIT) {
            name = getNameForObjLitKey(n);
          }
          if (name == null) return;
          isSet = true;
          switch (n.getType()) {
            case Token.STRING:
              type = getValueType(n.getFirstChild());
              break;
            case Token.GET:
              type = Name.Type.GET;
              break;
            case Token.SET:
              type = Name.Type.SET;
              break;
            default:
              throw new IllegalStateException("unexpected:" + n);
          }
          break;
        case Token.NAME:
          // This may be a variable get or set.
          if (parent != null) {
            switch (parent.getType()) {
              case Token.VAR:
                isSet = true;
                Node rvalue = n.getFirstChild();
                type = rvalue == null ? Name.Type.OTHER : getValueType(rvalue);
                break;
              case Token.ASSIGN:
                if (parent.getFirstChild() == n) {
                  isSet = true;
                  type = getValueType(n.getNext());
                }
                break;
              case Token.GETPROP:
                return;
              case Token.FUNCTION:
                Node gramps = parent.getParent();
                if (gramps == null ||
                    NodeUtil.isFunctionExpression(parent)) return;
                isSet = true;
                type = Name.Type.FUNCTION;
                break;
            }
          }
          name = n.getString();
          break;
        case Token.GETPROP:
          // This may be a namespaced name get or set.
          if (parent != null) {
            switch (parent.getType()) {
              case Token.ASSIGN:
                if (parent.getFirstChild() == n) {
                  isSet = true;
                  type = getValueType(n.getNext());
                  isPropAssign = true;
                }
                break;
              case Token.GETPROP:
                return;
            }
          }
          name = n.getQualifiedName();
          if (name == null) return;
          break;
        default:
          return;
      }

      // We are only interested in global names.
      Scope scope = t.getScope();
      if (!isGlobalNameReference(name, scope)) {
        return;
      }

      if (isSet) {
        if (isGlobalScope(scope)) {
          handleSetFromGlobal(t, n, parent, name, isPropAssign, type);
        } else {
          handleSetFromLocal(t, n, parent, name);
        }
      } else {
        handleGet(t, n, parent, name);
      }
    }

    /**
     * Gets the fully qualified name corresponding to an object literal key,
     * as long as it and its prefix property names are valid JavaScript
     * identifiers. The object literal may be nested inside of other object
     * literals.
     *
     * For example, if called with node {@code n} representing "z" in any of
     * the following expressions, the result would be "w.x.y.z":
     * <code> var w = {x: {y: {z: 0}}}; </code>
     * <code> w.x = {y: {z: 0}}; </code>
     * <code> w.x.y = {'a': 0, 'z': 0}; </code>
     *
     * @param n A child of an OBJLIT node
     * @return The global name, or null if {@code n} doesn't correspond to the
     *   key of an object literal that can be named
     */
    String getNameForObjLitKey(Node n) {
      Node parent = n.getParent();
      Preconditions.checkState(parent.getType() == Token.OBJECTLIT);

      Node gramps = parent.getParent();
      if (gramps == null) {
        return null;
      }

      Node greatGramps = gramps.getParent();
      String name;
      switch (gramps.getType()) {
        case Token.NAME:
          // VAR
          //   NAME (gramps)
          //     OBJLIT (parent)
          //       STRING (n)
          if (greatGramps == null ||
              greatGramps.getType() != Token.VAR) {
            return null;
          }
          name = gramps.getString();
          break;
        case Token.ASSIGN:
          // ASSIGN (gramps)
          //   NAME|GETPROP
          //   OBJLIT (parent)
          //     STRING (n)
          Node lvalue = gramps.getFirstChild();
          name = lvalue.getQualifiedName();
          break;
        case Token.STRING:
          // OBJLIT
          //   STRING (gramps)
          //     OBJLIT (parent)
          //       STRING (n)
          if (greatGramps != null &&
              greatGramps.getType() == Token.OBJECTLIT) {
            name = getNameForObjLitKey(gramps);
          } else {
            return null;
          }
          break;
        default:
          return null;
      }
      if (name != null) {
        String key = n.getString();
        if (TokenStream.isJSIdentifier(key)) {
          return name + '.' + key;
        }
      }
      return null;
    }

    /**
     * Gets the type of a value or simple expression.
     *
     * @param n An rvalue in an assignment or variable declaration (not null)
     * @return A {@link Name.Type}
     */
    Name.Type getValueType(Node n) {
      switch (n.getType()) {
        case Token.OBJECTLIT:
          return Name.Type.OBJECTLIT;
        case Token.FUNCTION:
          return Name.Type.FUNCTION;
        case Token.OR:
          // Recurse on the second value. If the first value were an object
          // literal or function, then the OR would be meaningless and the
          // second value would be dead code. Assume that if the second value
          // is an object literal or function, then the first value will also
          // evaluate to one when it doesn't evaluate to false.
          return getValueType(n.getLastChild());
        case Token.HOOK:
          // The same line of reasoning used for the OR case applies here.
          Node second = n.getFirstChild().getNext();
          Name.Type t = getValueType(second);
          if (t != Name.Type.OTHER) return t;
          Node third = second.getNext();
          return getValueType(third);
      }
      return Name.Type.OTHER;
    }

    /**
     * Updates our respresentation of the global namespace to reflect an
     * assignment to a global name in global scope.
     *
     * @param t The traversal
     * @param n The node currently being visited
     * @param parent {@code n}'s parent
     * @param name The global name (e.g. "a" or "a.b.c.d")
     * @param isPropAssign Whether this set corresponds to a property
     *     assignment of the form <code>a.b.c = ...;</code>
     * @param type The type of the value that the name is being assigned
     */
    void handleSetFromGlobal(NodeTraversal t, Node n, Node parent, String name,
                             boolean isPropAssign, Name.Type type) {
      if (maybeHandlePrototypePrefix(t, n, parent, name)) return;

      Name nameObj = getOrCreateName(name);
      nameObj.type = type;

      Ref set = new Ref(t, n, Ref.Type.SET_FROM_GLOBAL);
      nameObj.addRef(set);

      if (isNestedAssign(parent)) {
        // This assignment is both a set and a get that creates an alias.
        Ref get = new Ref(t, n, Ref.Type.ALIASING_GET);
        nameObj.addRef(get);
        Ref.markTwins(set, get);
      } else if (isConstructorOrEnumDeclaration(n, parent)) {
        // Names with a @constructor or @enum annotation are always collapsed
        nameObj.setIsClassOrEnum();
      }
    }

    /**
     * Determines whether a set operation is a constructor or enumeration
     * declaration. The set operation may either be an assignment to a name,
     * a variable declaration, or an object literal key mapping.
     *
     * @param n The node that represents the name being set
     * @param parent Parent node of {@code n} (an ASSIGN, VAR, or OBJLIT node)
     * @return Whether the set operation is either a constructor or enum
     *     declaration
     */
    private boolean isConstructorOrEnumDeclaration(Node n, Node parent) {
      JSDocInfo info;
      int valueNodeType;
      switch (parent.getType()) {
        case Token.ASSIGN:
          info = parent.getJSDocInfo();
          valueNodeType = n.getNext().getType();
          break;
        case Token.VAR:
          info = n.getJSDocInfo();
          if (info == null) {
            info = parent.getJSDocInfo();
          }
          Node valueNode = n.getFirstChild();
          valueNodeType = valueNode != null ? valueNode.getType() : Token.VOID;
          break;
        default:
          if (NodeUtil.isFunctionDeclaration(parent)) {
            info = parent.getJSDocInfo();
            valueNodeType = Token.FUNCTION;
            break;
          }
          return false;
      }
      // Heed the annotations only if they're sensibly used.
      return info != null &&
             (info.isConstructor() && valueNodeType == Token.FUNCTION ||
              info.hasEnumParameterType() && valueNodeType == Token.OBJECTLIT);
    }

    /**
     * Updates our respresentation of the global namespace to reflect an
     * assignment to a global name in a local scope.
     *
     * @param t The traversal
     * @param n The node currently being visited
     * @param parent {@code n}'s parent
     * @param name The global name (e.g. "a" or "a.b.c.d")
     */
    void handleSetFromLocal(NodeTraversal t, Node n, Node parent,
                            String name) {
      if (maybeHandlePrototypePrefix(t, n, parent, name)) return;

      Name node = getOrCreateName(name);
      Ref set = new Ref(t, n, Ref.Type.SET_FROM_LOCAL);
      node.addRef(set);

      if (isNestedAssign(parent)) {
        // This assignment is both a set and a get that creates an alias.
        Ref get = new Ref(t, n, Ref.Type.ALIASING_GET);
        node.addRef(get);
        Ref.markTwins(set, get);
      }
    }

    /**
     * Updates our respresentation of the global namespace to reflect a read
     * of a global name.
     *
     * @param t The traversal
     * @param n The node currently being visited
     * @param parent {@code n}'s parent
     * @param name The global name (e.g. "a" or "a.b.c.d")
     */
    void handleGet(NodeTraversal t, Node n, Node parent, String name) {
      if (maybeHandlePrototypePrefix(t, n, parent, name)) return;

      Ref.Type type = Ref.Type.DIRECT_GET;
      if (parent != null) {
        switch (parent.getType()) {
          case Token.IF:
          case Token.TYPEOF:
          case Token.VOID:
          case Token.NOT:
          case Token.BITNOT:
          case Token.POS:
          case Token.NEG:
            break;
          case Token.CALL:
            type = n == parent.getFirstChild()
                   ? Ref.Type.CALL_GET
                   : Ref.Type.ALIASING_GET;
            break;
          case Token.NEW:
            type = n == parent.getFirstChild()
                   ? Ref.Type.DIRECT_GET
                   : Ref.Type.ALIASING_GET;
            break;
          case Token.OR:
          case Token.AND:
            // This node is x or y in (x||y) or (x&&y). We only know that an
            // alias is not getting created for this name if the result is used
            // in a boolean context or assigned to the same name
            // (e.g. var a = a || {}).
            type = determineGetTypeForHookOrBooleanExpr(t, parent, name);
            break;
          case Token.HOOK:
            if (n != parent.getFirstChild()) {
              // This node is y or z in (x?y:z). We only know that an alias is
              // not getting created for this name if the result is assigned to
              // the same name (e.g. var a = a ? a : {}).
              type = determineGetTypeForHookOrBooleanExpr(t, parent, name);
            }
            break;
          default:
            type = Ref.Type.ALIASING_GET;
            break;
        }
      }

      handleGet(t, n, parent, name, type);
    }

    /**
     * Determines whether the result of a hook (x?y:z) or boolean expression
     * (x||y) or (x&&y) is assigned to a specific global name.
     *
     * @param t The traversal
     * @param parent The parent of the current node in the traversal. This node
     *     should already be known to be a HOOK, AND, or OR node.
     * @param name A name that is already known to be global in the current
     *     scope (e.g. "a" or "a.b.c.d")
     * @return The expression's get type, either {@link Ref.Type#DIRECT_GET} or
     *     {@link Ref.Type#ALIASING_GET}
     */
    Ref.Type determineGetTypeForHookOrBooleanExpr(
        NodeTraversal t, Node parent, String name) {
      Node prev = parent;
      for (Node anc : parent.getAncestors()) {
        switch (anc.getType()) {
          case Token.EXPR_RESULT:
          case Token.VAR:
          case Token.IF:
          case Token.WHILE:
          case Token.FOR:
          case Token.TYPEOF:
          case Token.VOID:
          case Token.NOT:
          case Token.BITNOT:
          case Token.POS:
          case Token.NEG:
            return Ref.Type.DIRECT_GET;
          case Token.HOOK:
            if (anc.getFirstChild() == prev) {
              return Ref.Type.DIRECT_GET;
            }
            break;
          case Token.ASSIGN:
            if (!name.equals(anc.getFirstChild().getQualifiedName())) {
              return Ref.Type.ALIASING_GET;
            }
            break;
          case Token.NAME:  // a variable declaration
            if (!name.equals(anc.getString())) {
              return Ref.Type.ALIASING_GET;
            }
            break;
          case Token.CALL:
            if (anc.getFirstChild() != prev) {
              return Ref.Type.ALIASING_GET;
            }
            break;
        }
        prev = anc;
      }
      return Ref.Type.ALIASING_GET;
    }

    /**
     * Updates our respresentation of the global namespace to reflect a read
     * of a global name.
     *
     * @param t The current node traversal
     * @param n The node currently being visited
     * @param parent {@code n}'s parent
     * @param name The global name (e.g. "a" or "a.b.c.d")
     * @param type The reference type
     */
    void handleGet(NodeTraversal t, Node n, Node parent,
        String name, Ref.Type type) {
      Name node = getOrCreateName(name);

      // No need to look up additional ancestors, since they won't be used.
      node.addRef(new Ref(t, n, type));
    }

    /**
     * Updates our respresentation of the global namespace to reflect a read
     * of a global name's longest prefix before the "prototype" property if the
     * name includes the "prototype" property. Does nothing otherwise.
     *
     * @param t The current node traversal
     * @param n The node currently being visited
     * @param parent {@code n}'s parent
     * @param name The global name (e.g. "a" or "a.b.c.d")
     * @return Whether the name was handled
     */
    boolean maybeHandlePrototypePrefix(NodeTraversal t, Node n, Node parent,
        String name) {
      // We use a string-based approach instead of inspecting the parse tree
      // to avoid complexities with object literals, possibly nested, beneath
      // assignments.

      int numLevelsToRemove;
      String prefix;
      if (name.endsWith(".prototype")) {
        numLevelsToRemove = 1;
        prefix = name.substring(0, name.length() - 10);
      } else {
        int i = name.indexOf(".prototype.");
        if (i == -1) {
          return false;
        }
        prefix = name.substring(0, i);
        numLevelsToRemove = 2;
        i = name.indexOf('.', i + 11);
        while (i >= 0) {
          numLevelsToRemove++;
          i = name.indexOf('.', i + 1);
        }
      }

      if (parent != null && NodeUtil.isObjectLitKey(n, parent)) {
        // Object literal keys have no prefix that's referenced directly per
        // key, so we're done.
        return true;
      }

      for (int i = 0; i < numLevelsToRemove; i++) {
        parent = n;
        n = n.getFirstChild();
      }

      handleGet(t, n, parent, prefix, Ref.Type.PROTOTYPE_GET);
      return true;
    }

    /**
     * Determines whether an assignment is nested (i.e. whether its return
     * value is used).
     *
     * @param parent The parent of the current traversal node (not null)
     * @return Whether it appears that the return value of the assignment is
     *     used
     */
    boolean isNestedAssign(Node parent) {
      return parent.getType() == Token.ASSIGN &&
             !NodeUtil.isExpressionNode(parent.getParent());
    }

    /**
     * Gets a {@link Name} instance for a global name. Creates it if necessary,
     * as well as instances for any of its prefixes that are not yet defined.
     *
     * @param name A global name (e.g. "a", "a.b.c.d")
     * @return The {@link Name} instance for {@code name}
     */
    Name getOrCreateName(String name) {
      Name node = nameMap.get(name);
      if (node == null) {
        int i = name.lastIndexOf('.');
        if (i >= 0) {
          String parentName = name.substring(0, i);
          Name parent = getOrCreateName(parentName);
          node = parent.addProperty(name.substring(i + 1), inExterns);
        } else {
          node = new Name(name, null, inExterns);
          globalNames.add(node);
        }
        nameMap.put(name, node);
      }
      return node;
    }
  }

  // -------------------------------------------------------------------------

  /**
   * A name defined in global scope (e.g. "a" or "a.b.c.d"). These form a tree.
   * As the parse tree traversal proceeds, we'll discover that some names
   * correspond to JavaScript objects whose properties we should consider
   * collapsing.
   */
  static class Name {
    enum Type {
      OBJECTLIT,
      FUNCTION,
      GET,
      SET,
      OTHER,
    }

    final String name;
    final Name parent;
    List<Name> props;
    Ref declaration;
    List<Ref> refs;
    Type type;
    private boolean isClassOrEnum = false;
    private boolean hasClassOrEnumDescendant = false;
    int globalSets = 0;
    int localSets = 0;
    int aliasingGets = 0;
    int totalGets = 0;
    int callGets = 0;
    boolean inExterns;

    JSDocInfo docInfo = null;

    Name(String name, Name parent, boolean inExterns) {
      this.name = name;
      this.parent = parent;
      this.type = Type.OTHER;
      this.inExterns = inExterns;
    }

    Name addProperty(String name, boolean inExterns) {
      if (props == null) {
        props = new ArrayList<Name>();
      }
      Name node = new Name(name, this, inExterns);
      props.add(node);
      return node;
    }

    void addRef(Ref ref) {
      switch (ref.type) {
        case SET_FROM_GLOBAL:
          if (declaration == null) {
            declaration = ref;
            docInfo = getDocInfoForDeclaration(ref);
          } else {
            addRefInternal(ref);
          }
          globalSets++;
          break;
        case SET_FROM_LOCAL:
          addRefInternal(ref);
          localSets++;
          break;
        case PROTOTYPE_GET:
        case DIRECT_GET:
          addRefInternal(ref);
          totalGets++;
          break;
        case ALIASING_GET:
          addRefInternal(ref);
          aliasingGets++;
          totalGets++;
          break;
        case CALL_GET:
          addRefInternal(ref);
          callGets++;
          totalGets++;
          break;
        default:
          throw new IllegalStateException();
      }
    }

    void removeRef(Ref ref) {
      if (ref == declaration ||
          (refs != null && refs.remove(ref))) {
        if (ref == declaration) {
          declaration = null;
          if (refs != null) {
            for (Ref maybeNewDecl : refs) {
              if (maybeNewDecl.type == Ref.Type.SET_FROM_GLOBAL) {
                declaration = maybeNewDecl;
                refs.remove(declaration);
                break;
              }
            }
          }
        }

        switch (ref.type) {
          case SET_FROM_GLOBAL:
            globalSets--;
            break;
          case SET_FROM_LOCAL:
            localSets--;
            break;
          case PROTOTYPE_GET:
          case DIRECT_GET:
            totalGets--;
            break;
          case ALIASING_GET:
            aliasingGets--;
            totalGets--;
            break;
          case CALL_GET:
            callGets--;
            totalGets--;
            break;
          default:
            throw new IllegalStateException();
        }
      }
    }

    void addRefInternal(Ref ref) {
      if (refs == null) {
        refs = new LinkedList<Ref>();
      }
      refs.add(ref);
    }

    boolean canEliminate() {
      if (!canCollapseUnannotatedChildNames() || totalGets > 0) {
        return false;
      }

      if (props != null) {
        for (Name n : props) {
          if (!n.canCollapse()) {
            return false;
          }
        }
      }
      return true;
    }

    boolean canCollapse() {
      return !inExterns && !isGetOrSetDefinition() && (isClassOrEnum ||
          (parent == null || parent.canCollapseUnannotatedChildNames()) &&
          (globalSets > 0 || localSets > 0));
    }

    boolean isGetOrSetDefinition() {
      return this.type == Type.GET || this.type == Type.SET;
    }

    boolean canCollapseUnannotatedChildNames() {
      if (type == Type.OTHER || isGetOrSetDefinition()
          || globalSets != 1 || localSets != 0) {
        return false;
      }

      // Don't try to collapse if the one global set is a twin reference.
      // We could theoretically handle this case in CollapseProperties, but
      // it's probably not worth the effort.
      Preconditions.checkNotNull(declaration);
      if (declaration.getTwin() != null) {
        return false;
      }

      if (isClassOrEnum) {
        return true;
      }

      // If this is a key of an aliased object literal, then it will be aliased
      // later. So we won't be able to collapse its properties.
      if (parent != null && parent.shouldKeepKeys()) {
        return false;
      }

      // If this is aliased, then its properties can't be collapsed either.
      if (aliasingGets > 0) {
        return false;
      }

      return (parent == null || parent.canCollapseUnannotatedChildNames());
    }

    /** Whether this is an object literal that needs to keep its keys. */
    boolean shouldKeepKeys() {
      return type == Type.OBJECTLIT && aliasingGets > 0;
    }

    boolean needsToBeStubbed() {
      return globalSets == 0 && localSets > 0;
    }

    void setIsClassOrEnum() {
      isClassOrEnum = true;
      for (Name ancestor = parent; ancestor != null;
           ancestor = ancestor.parent) {
        ancestor.hasClassOrEnumDescendant = true;
      }
    }

    /**
     * Determines whether this name is a prefix of at least one class or enum
     * name. Because classes and enums are always collapsed, the namespace will
     * have different properties in compiled code than in uncompiled code.
     *
     * For example, if foo.bar.DomHelper is a class, then foo and foo.bar are
     * considered namespaces.
     */
    boolean isNamespace() {
      return hasClassOrEnumDescendant && type == Type.OBJECTLIT;
    }

    /**
     * Determines whether this is a simple name (as opposed to a qualified
     * name).
     */
    boolean isSimpleName() {
      return parent == null;
    }

    @Override public String toString() {
      return fullName() + " (" + type + "): globalSets=" + globalSets +
          ", localSets=" + localSets + ", totalGets=" + totalGets +
          ", aliasingGets=" + aliasingGets + ", callGets=" + callGets;
    }

    String fullName() {
      return parent == null ? name : parent.fullName() + '.' + name;
    }

    /**
     * Tries to get the doc info for a given declaration ref.
     */
    private static JSDocInfo getDocInfoForDeclaration(Ref ref) {
      if (ref.node != null) {
        Node refParent = ref.node.getParent();
        switch (refParent.getType()) {
          case Token.FUNCTION:
          case Token.ASSIGN:
            return refParent.getJSDocInfo();
          case Token.VAR:
            return ref.node == refParent.getFirstChild() ?
                refParent.getJSDocInfo() : ref.node.getJSDocInfo();
        }
      }

      return null;
    }
  }

  // -------------------------------------------------------------------------

  /**
   * A global name reference. Contains references to the relevant parse tree
   * node and its ancestors that may be affected.
   */
  static class Ref {
    enum Type {
      SET_FROM_GLOBAL,
      SET_FROM_LOCAL,
      PROTOTYPE_GET,
      ALIASING_GET,     // Prevents a name's properties from being collapsed
      DIRECT_GET,       // Prevents a name from being completely eliminated
      CALL_GET,         // Prevents a name from being collapsed if never set
    }

    Node node;
    final Type type;
    final String sourceName;
    final Scope scope;
    final JSModule module;

    /**
     * Certain types of references are actually double-refs. For example,
     * var a = b = 0;
     * counts as both a "set" of b and an "alias" of b.
     *
     * We create two Refs for this node, and mark them as twins of each other.
     */
    private Ref twin = null;

    /**
     * Creates a reference at the current node.
     */
    Ref(NodeTraversal t, Node name, Type type) {
      this.node = name;
      this.sourceName = t.getSourceName();
      this.type = type;
      this.scope = t.getScope();
      this.module = t.getModule();
    }

    private Ref(Ref original, Type type) {
      this.node = original.node;
      this.sourceName = original.sourceName;
      this.type = type;
      this.scope = original.scope;
      this.module = original.module;
    }

    private Ref(Type type) {
      this.type = type;
      this.sourceName = "source";
      this.scope = null;
      this.module = null;
    }

    Ref getTwin() {
      return twin;
    }

    boolean isSet() {
      return type == Type.SET_FROM_GLOBAL || type == Type.SET_FROM_LOCAL;
    }

    static void markTwins(Ref a, Ref b) {
      Preconditions.checkArgument(
          (a.type == Type.ALIASING_GET || b.type == Type.ALIASING_GET) &&
          (a.type == Type.SET_FROM_GLOBAL || a.type == Type.SET_FROM_LOCAL ||
           b.type == Type.SET_FROM_GLOBAL || b.type == Type.SET_FROM_LOCAL));
      a.twin = b;
      b.twin = a;
    }

    /**
     * Create a new ref that is the same as this one, but of
     * a different class.
     */
    Ref cloneAndReclassify(Type type) {
      return new Ref(this, type);
    }

    static Ref createRefForTesting(Type type) {
      return new Ref(type);
    }
  }
}
