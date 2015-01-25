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

import static com.google.javascript.rhino.jstype.JSTypeNative.GLOBAL_THIS;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.StaticReference;
import com.google.javascript.rhino.jstype.StaticScope;
import com.google.javascript.rhino.jstype.StaticSlot;
import com.google.javascript.rhino.jstype.StaticSourceFile;
import com.google.javascript.rhino.jstype.StaticSymbolTable;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a global namespace of all the objects and their properties in
 * the global scope. Also builds an index of all the references to those names.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
class GlobalNamespace
    implements StaticScope<JSType>,
    StaticSymbolTable<GlobalNamespace.Name, GlobalNamespace.Ref> {

  private AbstractCompiler compiler;
  private final Node root;
  private final Node externsRoot;
  private boolean inExterns;
  private Scope externsScope;
  private boolean generated = false;

  /**
   * Each reference has an index in post-order.
   * Notice that some nodes are represented by 2 Ref objects, so
   * this index is not necessarily unique.
   */
  private int currentPreOrderIndex = 0;

  /** Global namespace tree */
  private List<Name> globalNames = new ArrayList<>();

  /** Maps names (e.g. "a.b.c") to nodes in the global namespace tree */
  private Map<String, Name> nameMap = new HashMap<>();

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

  boolean hasExternsRoot() {
    return externsRoot != null;
  }

  @Override
  public Node getRootNode() {
    return root.getParent();
  }

  @Override
  public StaticScope<JSType> getParentScope() {
    return null;
  }

  @Override
  public Name getSlot(String name) {
    return getOwnSlot(name);
  }

  @Override
  public Name getOwnSlot(String name) {
    ensureGenerated();
    return nameMap.get(name);
  }

  @Override
  public JSType getTypeOfThis() {
    return compiler.getTypeRegistry().getNativeObjectType(GLOBAL_THIS);
  }

  @Override
  public Iterable<Ref> getReferences(Name slot) {
    ensureGenerated();
    return Collections.unmodifiableList(slot.getRefs());
  }

  @Override
  public StaticScope<JSType> getScope(Name slot) {
    return this;
  }

  @Override
  public Iterable<Name> getAllSymbols() {
    ensureGenerated();
    return Collections.unmodifiableCollection(getNameIndex().values());
  }

  private void ensureGenerated() {
    if (!generated) {
      process();
    }
  }

  /**
   * Gets a list of the roots of the forest of the global names, where the
   * roots are the top-level names.
   */
  List<Name> getNameForest() {
    ensureGenerated();
    return globalNames;
  }

  /**
   * Gets an index of all the global names, indexed by full qualified name
   * (as in "a", "a.b.c", etc.).
   */
  Map<String, Name> getNameIndex() {
    ensureGenerated();
    return nameMap;
  }

  /**
   * A simple data class that contains the information necessary to inspect
   * a node for changes to the global namespace.
   */
  static class AstChange {
    final JSModule module;
    final Scope scope;
    final Node node;

    AstChange(JSModule module, Scope scope, Node node) {
      this.module = module;
      this.scope = scope;
      this.node = node;
    }
  }

  /**
   * If the client adds new nodes to the AST, scan these new nodes
   * to see if they've added any references to the global namespace.
   * @param newNodes New nodes to check.
   */
  void scanNewNodes(List<AstChange> newNodes) {
    BuildGlobalNamespace builder = new BuildGlobalNamespace();

    for (AstChange info : newNodes) {
      if (!info.node.isQualifiedName() && !NodeUtil.isObjectLitKey(info.node)) {
        continue;
      }
      scanFromNode(builder, info.module, info.scope, info.node);
    }
  }

  private void scanFromNode(
    BuildGlobalNamespace builder, JSModule module, Scope scope, Node n) {
    // Check affected parent nodes first.
    if (n.isName() || n.isGetProp()) {
      scanFromNode(builder, module, scope, n.getParent());
    }
    builder.collect(module, scope, n);
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
  private static String getTopVarName(String name) {
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
  private static boolean isGlobalScope(Scope s) {
    return s.getParent() == null;
  }

  // -------------------------------------------------------------------------

  /**
   * Builds a tree representation of the global namespace. Omits prototypes.
   */
  private class BuildGlobalNamespace implements NodeTraversal.Callback {

    BuildGlobalNamespace() {
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {}

    /** Collect the references in pre-order. */
    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      // If we are traversing the externs, then we save a pointer to the scope
      // generated by them, so that we can do lookups in it later.
      if (externsRoot != null && n == externsRoot) {
        externsScope = t.getScope();
      }

      collect(t.getModule(), t.getScope(), n);

      return true;
    }

    public void collect(JSModule module, Scope scope, Node n) {
      Node parent = n.getParent();

      String name;
      boolean isSet = false;
      Name.Type type = Name.Type.OTHER;
      boolean isPropAssign = false;

      switch (n.getType()) {
        case Token.GETTER_DEF:
        case Token.SETTER_DEF:
        case Token.STRING_KEY:
          // This may be a key in an object literal declaration.
          name = null;
          if (parent != null && parent.isObjectLit()) {
            name = getNameForObjLitKey(n);
          }
          if (name == null) {
            return;
          }
          isSet = true;
          switch (n.getType()) {
            case Token.STRING_KEY:
              type = getValueType(n.getFirstChild());
              break;
            case Token.GETTER_DEF:
              type = Name.Type.GET;
              break;
            case Token.SETTER_DEF:
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
                if (gramps == null || NodeUtil.isFunctionExpression(parent)) {
                  return;
                }
                isSet = true;
                type = Name.Type.FUNCTION;
                break;
              case Token.CATCH:
              case Token.INC:
              case Token.DEC:
                isSet = true;
                type = Name.Type.OTHER;
                break;
              default:
                if (NodeUtil.isAssignmentOp(parent) &&
                    parent.getFirstChild() == n) {
                  isSet = true;
                  type = Name.Type.OTHER;
                }
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
              case Token.INC:
              case Token.DEC:
                isSet = true;
                type = Name.Type.OTHER;
                break;
              case Token.GETPROP:
                return;
              default:
                if (NodeUtil.isAssignmentOp(parent) &&
                    parent.getFirstChild() == n) {
                  isSet = true;
                  type = Name.Type.OTHER;
                }
            }
          }
          name = n.getQualifiedName();
          if (name == null) {
            return;
          }
          break;
        default:
          return;
      }

      // We are only interested in global names.
      if (!isGlobalNameReference(name, scope)) {
        return;
      }

      if (isSet) {
        if (isGlobalScope(scope)) {
          handleSetFromGlobal(module, scope, n, parent, name, isPropAssign, type);
        } else {
          handleSetFromLocal(module, scope, n, parent, name);
        }
      } else {
        handleGet(module, scope, n, parent, name);
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
      Preconditions.checkState(parent.isObjectLit());

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
          if (greatGramps == null || !greatGramps.isVar()) {
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
        case Token.STRING_KEY:
          // OBJLIT
          //   STRING (gramps)
          //     OBJLIT (parent)
          //       STRING (n)
          if (greatGramps != null &&
              greatGramps.isObjectLit()) {
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
     * @param n An r-value in an assignment or variable declaration (not null)
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
          if (t != Name.Type.OTHER) {
            return t;
          }
          Node third = second.getNext();
          return getValueType(third);
      }
      return Name.Type.OTHER;
    }

    /**
     * Updates our representation of the global namespace to reflect an
     * assignment to a global name in global scope.
     *
     * @param module the current module
     * @param scope the current scope
     * @param n The node currently being visited
     * @param parent {@code n}'s parent
     * @param name The global name (e.g. "a" or "a.b.c.d")
     * @param isPropAssign Whether this set corresponds to a property
     *     assignment of the form <code>a.b.c = ...;</code>
     * @param type The type of the value that the name is being assigned
     */
    void handleSetFromGlobal(JSModule module, Scope scope,
        Node n, Node parent, String name,
        boolean isPropAssign, Name.Type type) {
      if (maybeHandlePrototypePrefix(module, scope, n, parent, name)) {
        return;
      }

      Name nameObj = getOrCreateName(name);
      nameObj.type = type;

      Ref set = new Ref(module, scope, n, nameObj, Ref.Type.SET_FROM_GLOBAL,
          currentPreOrderIndex++);
      nameObj.addRef(set);

      if (isNestedAssign(parent)) {
        // This assignment is both a set and a get that creates an alias.
        Ref get = new Ref(module, scope, n, nameObj, Ref.Type.ALIASING_GET,
            currentPreOrderIndex++);
        nameObj.addRef(get);
        Ref.markTwins(set, get);
      } else if (isTypeDeclaration(n, parent)) {
        // Names with a @constructor or @enum annotation are always collapsed
        nameObj.setDeclaredType();
      }
    }

    /**
     * Determines whether a set operation is a constructor or enumeration
     * or interface declaration. The set operation may either be an assignment
     * to a name, a variable declaration, or an object literal key mapping.
     *
     * @param n The node that represents the name being set
     * @param parent Parent node of {@code n} (an ASSIGN, VAR, or OBJLIT node)
     * @return Whether the set operation is either a constructor or enum
     *     declaration
     */
    private boolean isTypeDeclaration(Node n, Node parent) {
      Node valueNode = NodeUtil.getRValueOfLValue(n);
      JSDocInfo info = NodeUtil.getBestJSDocInfo(n);
      // Heed the annotations only if they're sensibly used.
      return info != null && valueNode != null &&
             (info.isConstructor() && valueNode.isFunction() ||
              info.isInterface() && valueNode.isFunction() ||
              info.hasEnumParameterType() && valueNode.isObjectLit());
    }

    /**
     * Updates our representation of the global namespace to reflect an
     * assignment to a global name in a local scope.
     *
     * @param module The current module
     * @param scope The current scope
     * @param n The node currently being visited
     * @param parent {@code n}'s parent
     * @param name The global name (e.g. "a" or "a.b.c.d")
     */
    void handleSetFromLocal(JSModule module, Scope scope, Node n, Node parent,
                            String name) {
      if (maybeHandlePrototypePrefix(module, scope, n, parent, name)) {
        return;
      }

      Name nameObj = getOrCreateName(name);
      Ref set = new Ref(module, scope, n, nameObj,
          Ref.Type.SET_FROM_LOCAL, currentPreOrderIndex++);
      nameObj.addRef(set);

      if (isNestedAssign(parent)) {
        // This assignment is both a set and a get that creates an alias.
        Ref get = new Ref(module, scope, n, nameObj,
            Ref.Type.ALIASING_GET, currentPreOrderIndex++);
        nameObj.addRef(get);
        Ref.markTwins(set, get);
      }
    }

    /**
     * Updates our representation of the global namespace to reflect a read
     * of a global name.
     *
     * @param module The current module
     * @param scope The current scope
     * @param n The node currently being visited
     * @param parent {@code n}'s parent
     * @param name The global name (e.g. "a" or "a.b.c.d")
     */
    void handleGet(JSModule module, Scope scope,
        Node n, Node parent, String name) {
      if (maybeHandlePrototypePrefix(module, scope, n, parent, name)) {
        return;
      }

      Ref.Type type = Ref.Type.DIRECT_GET;
      if (parent != null) {
        switch (parent.getType()) {
          case Token.IF:
          case Token.INSTANCEOF:
          case Token.TYPEOF:
          case Token.VOID:
          case Token.NOT:
          case Token.BITNOT:
          case Token.POS:
          case Token.NEG:
            break;
          case Token.CALL:
            if (n == parent.getFirstChild()) {
              // It is a call target
              type = Ref.Type.CALL_GET;
            } else if (isClassDefiningCall(parent)) {
              type = Ref.Type.DIRECT_GET;
            } else {
              type = Ref.Type.ALIASING_GET;
            }
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
            type = determineGetTypeForHookOrBooleanExpr(module, scope, parent, name);
            break;
          case Token.HOOK:
            if (n != parent.getFirstChild()) {
              // This node is y or z in (x?y:z). We only know that an alias is
              // not getting created for this name if the result is assigned to
              // the same name (e.g. var a = a ? a : {}).
              type = determineGetTypeForHookOrBooleanExpr(module, scope, parent, name);
            }
            break;
          case Token.DELPROP:
            type = Ref.Type.DELETE_PROP;
            break;
          default:
            type = Ref.Type.ALIASING_GET;
            break;
        }
      }

      handleGet(module, scope, n, parent, name, type);
    }

    private boolean isClassDefiningCall(Node callNode) {
      CodingConvention convention = compiler.getCodingConvention();
      // Look for goog.inherits, goog.mixin
      SubclassRelationship classes =
          convention.getClassesDefinedByCall(callNode);
      if (classes != null) {
        return true;
      }

      // Look for calls to goog.addSingletonGetter calls.
      String className = convention.getSingletonGetterClassName(callNode);
      return className != null;
    }

    /**
     * Determines whether the result of a hook (x?y:z) or boolean expression
     * (x||y) or (x&&y) is assigned to a specific global name.
     *
     * @param module The current module
     * @param scope The current scope
     * @param parent The parent of the current node in the traversal. This node
     *     should already be known to be a HOOK, AND, or OR node.
     * @param name A name that is already known to be global in the current
     *     scope (e.g. "a" or "a.b.c.d")
     * @return The expression's get type, either {@link Ref.Type#DIRECT_GET} or
     *     {@link Ref.Type#ALIASING_GET}
     */
    Ref.Type determineGetTypeForHookOrBooleanExpr(
        JSModule module, Scope scope, Node parent, String name) {
      Node prev = parent;
      for (Node anc : parent.getAncestors()) {
        switch (anc.getType()) {
          case Token.INSTANCEOF:
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
          case Token.DELPROP:
            return Ref.Type.DELETE_PROP;
        }
        prev = anc;
      }
      return Ref.Type.ALIASING_GET;
    }

    /**
     * Updates our representation of the global namespace to reflect a read
     * of a global name.
     *
     * @param module The current module
     * @param scope The current scope
     * @param n The node currently being visited
     * @param parent {@code n}'s parent
     * @param name The global name (e.g. "a" or "a.b.c.d")
     * @param type The reference type
     */
    void handleGet(JSModule module, Scope scope, Node n, Node parent,
        String name, Ref.Type type) {
      Name nameObj = getOrCreateName(name);

      // No need to look up additional ancestors, since they won't be used.
      nameObj.addRef(
          new Ref(module, scope, n, nameObj, type, currentPreOrderIndex++));
    }

    /**
     * Updates our representation of the global namespace to reflect a read
     * of a global name's longest prefix before the "prototype" property if the
     * name includes the "prototype" property. Does nothing otherwise.
     *
     * @param module The current module
     * @param scope The current scope
     * @param n The node currently being visited
     * @param parent {@code n}'s parent
     * @param name The global name (e.g. "a" or "a.b.c.d")
     * @return Whether the name was handled
     */
    boolean maybeHandlePrototypePrefix(JSModule module, Scope scope,
        Node n, Node parent, String name) {
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

      if (parent != null && NodeUtil.isObjectLitKey(n)) {
        // Object literal keys have no prefix that's referenced directly per
        // key, so we're done.
        return true;
      }

      for (int i = 0; i < numLevelsToRemove; i++) {
        parent = n;
        n = n.getFirstChild();
      }

      handleGet(module, scope, n, parent, prefix, Ref.Type.PROTOTYPE_GET);
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
      return parent.isAssign() &&
             !parent.getParent().isExprResult();
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
  static class Name implements StaticSlot<JSType> {
    enum Type {
      OBJECTLIT,
      FUNCTION,
      GET,
      SET,
      OTHER,
    }

    private final String baseName;
    final Name parent;
    List<Name> props;

    /** The first global assignment to a name. */
    private Ref declaration;

    /** All references to a name. This must contain {@code declaration}. */
    private List<Ref> refs;

    Type type;
    private boolean declaredType = false;
    private boolean hasDeclaredTypeDescendant = false;
    int globalSets = 0;
    int localSets = 0;
    int aliasingGets = 0;
    int totalGets = 0;
    int callGets = 0;
    int deleteProps = 0;
    final boolean inExterns;

    JSDocInfo docInfo = null;

    Name(String name, Name parent, boolean inExterns) {
      this.baseName = name;
      this.parent = parent;
      this.type = Type.OTHER;
      this.inExterns = inExterns;
    }

    Name addProperty(String name, boolean inExterns) {
      if (props == null) {
        props = new ArrayList<>();
      }
      Name node = new Name(name, this, inExterns);
      props.add(node);
      return node;
    }

    String getBaseName() {
      return baseName;
    }

    @Override
    public String getName() {
      return getFullName();
    }

    String getFullName() {
      return parent == null ? baseName : parent.getFullName() + '.' + baseName;
    }

    @Override
    public Ref getDeclaration() {
      return declaration;
    }

    @Override
    public boolean isTypeInferred() {
      return false;
    }

    @Override
    public JSType getType() {
      return null;
    }

    void addRef(Ref ref) {
      addRefInternal(ref);
      switch (ref.type) {
        case SET_FROM_GLOBAL:
          if (declaration == null) {
            declaration = ref;
            docInfo = getDocInfoForDeclaration(ref);
          }
          globalSets++;
          break;
        case SET_FROM_LOCAL:
          localSets++;
          break;
        case PROTOTYPE_GET:
        case DIRECT_GET:
          totalGets++;
          break;
        case ALIASING_GET:
          aliasingGets++;
          totalGets++;
          break;
        case CALL_GET:
          callGets++;
          totalGets++;
          break;
        case DELETE_PROP:
          deleteProps++;
          break;
        default:
          throw new IllegalStateException();
      }
    }

    void removeRef(Ref ref) {
      if (refs != null && refs.remove(ref)) {
        if (ref == declaration) {
          declaration = null;
          if (refs != null) {
            for (Ref maybeNewDecl : refs) {
              if (maybeNewDecl.type == Ref.Type.SET_FROM_GLOBAL) {
                declaration = maybeNewDecl;
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
          case DELETE_PROP:
            deleteProps--;
            break;
          default:
            throw new IllegalStateException();
        }
      }
    }

    List<Ref> getRefs() {
      return refs == null ? ImmutableList.<Ref>of() : refs;
    }

    void addRefInternal(Ref ref) {
      if (refs == null) {
        refs = Lists.newArrayList();
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

    boolean isSimpleStubDeclaration() {
      if (getRefs().size() == 1) {
        Ref ref = refs.get(0);
        if (ref.node.getParent() != null &&
            ref.node.getParent().isExprResult()) {
          return true;
        }
      }
      return false;
    }

    boolean canCollapse() {
      return !inExterns && !isGetOrSetDefinition() && (declaredType ||
          (parent == null || parent.canCollapseUnannotatedChildNames()) &&
          (globalSets > 0 || localSets > 0) &&
          deleteProps == 0);
    }

    boolean isGetOrSetDefinition() {
      return this.type == Type.GET || this.type == Type.SET;
    }

    boolean canCollapseUnannotatedChildNames() {
      if (type == Type.OTHER || isGetOrSetDefinition()
          || globalSets != 1 || localSets != 0 || deleteProps != 0) {
        return false;
      }

      // Don't try to collapse if the one global set is a twin reference.
      // We could theoretically handle this case in CollapseProperties, but
      // it's probably not worth the effort.
      Preconditions.checkNotNull(declaration);
      if (declaration.getTwin() != null) {
        return false;
      }

      if (declaredType) {
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

    void setDeclaredType() {
      declaredType = true;
      for (Name ancestor = parent; ancestor != null;
           ancestor = ancestor.parent) {
        ancestor.hasDeclaredTypeDescendant = true;
      }
    }

    boolean isDeclaredType() {
      return declaredType;
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
      return hasDeclaredTypeDescendant && type == Type.OBJECTLIT;
    }

    /**
     * Determines whether this is a simple name (as opposed to a qualified
     * name).
     */
    boolean isSimpleName() {
      return parent == null;
    }

    @Override public String toString() {
      return getFullName() + " (" + type + "): globalSets=" + globalSets +
          ", localSets=" + localSets + ", totalGets=" + totalGets +
          ", aliasingGets=" + aliasingGets + ", callGets=" + callGets;
    }

    @Override
    public JSDocInfo getJSDocInfo() {
      return docInfo;
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
  static class Ref implements StaticReference<JSType> {

    // Note: we are more aggressive about collapsing @enum and @constructor
    // declarations than implied here, see Name#canCollapse
    enum Type {
      SET_FROM_GLOBAL,
      SET_FROM_LOCAL,
      PROTOTYPE_GET,
      ALIASING_GET,     // Prevents a name's properties from being collapsed
      DIRECT_GET,       // Prevents a name from being completely eliminated
      CALL_GET,         // Prevents a name from being collapsed if never set
      DELETE_PROP,      // Prevents a name from being collapsed at all.
    }

    Node node;
    final JSModule module;
    final StaticSourceFile source;
    final Name name;
    final Type type;
    final Scope scope;
    final int preOrderIndex;

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
    Ref(JSModule module, Scope scope, Node node, Name name, Type type, int index) {
      this.node = node;
      this.name = name;
      this.module = module;
      this.source = node.getStaticSourceFile();
      this.type = type;
      this.scope = scope;
      this.preOrderIndex = index;
    }

    private Ref(Ref original, Type type, int index) {
      this.node = original.node;
      this.name = original.name;
      this.module = original.module;
      this.source = original.source;
      this.type = type;
      this.scope = original.scope;
      this.preOrderIndex = index;
    }

    private Ref(Type type, int index) {
      this.type = type;
      this.module = null;
      this.source = null;
      this.scope = null;
      this.name = null;
      this.preOrderIndex = index;
    }

    @Override
    public Node getNode() {
      return node;
    }

    @Override
    public StaticSourceFile getSourceFile() {
      return source;
    }

    @Override
    public StaticSlot<JSType> getSymbol() {
      return name;
    }

    JSModule getModule() {
      return module;
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
      return new Ref(this, type, this.preOrderIndex);
    }

    static Ref createRefForTesting(Type type) {
      return new Ref(type, -1);
    }
  }


  /**
   * An experimental compiler pass for tracking what symbols were added/removed
   * at each stage of compilation.
   *
   * When "global namespace tracker" mode is on, we rebuild the global namespace
   * after each pass, and diff it against the last namespace built.
   */
  static class Tracker implements CompilerPass {
    private final AbstractCompiler compiler;
    private final PrintStream stream;
    private final Predicate<String> isInterestingSymbol;

    private Set<String> previousSymbolsInTree = ImmutableSet.of();

    /**
       @param stream The stream to print logs to.
     * @param isInterestingSymbol A predicate to determine which symbols
     *     we care about.
     */
    Tracker(AbstractCompiler compiler, PrintStream stream,
        Predicate<String> isInterestingSymbol) {
      this.compiler = compiler;
      this.stream = stream;
      this.isInterestingSymbol = isInterestingSymbol;
    }

    @Override public void process(Node externs, Node root) {
      GlobalNamespace namespace = new GlobalNamespace(compiler, externs, root);

      Set<String> currentSymbols = Sets.newTreeSet();
      for (String name : namespace.getNameIndex().keySet()) {
        if (isInterestingSymbol.apply(name)) {
          currentSymbols.add(name);
        }
      }

      String passName = compiler.getLastPassName();
      if (passName == null) {
        passName = "[Unknown pass]";
      }

      for (String sym : currentSymbols) {
        if (!previousSymbolsInTree.contains(sym)) {
          stream.printf("%s: Added by %s%n", sym, passName);
        }
      }

      for (String sym : previousSymbolsInTree) {
        if (!currentSymbols.contains(sym)) {
          stream.printf("%s: Removed by %s%n", sym, passName);
        }
      }

      previousSymbolsInTree = currentSymbols;
    }
  }
}
