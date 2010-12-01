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
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.GlobalNamespace.Name;
import com.google.javascript.jscomp.GlobalNamespace.Ref;
import com.google.javascript.jscomp.GlobalNamespace.Ref.Type;
import com.google.javascript.jscomp.ReferenceCollectingCallback;
import com.google.javascript.jscomp.ReferenceCollectingCallback.ReferenceCollection;
import com.google.javascript.jscomp.Scope;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Flattens global objects/namespaces by replacing each '.' with '$' in
 * their names. This reduces the number of property lookups the browser has
 * to do and allows the {@link RenameVars} pass to shorten namespaced names.
 * For example, goog.events.handleEvent() -> goog$events$handleEvent() -> Za().
 *
 * <p>If a global object's name is assigned to more than once, or if a property
 * is added to the global object in a complex expression, then none of its
 * properties will be collapsed (for safety/correctness).
 *
 * <p>If, after a global object is declared, it is never referenced except when
 * its properties are read or set, then the object will be removed after its
 * properties have been collapsed.
 *
 * <p>Uninitialized variable stubs are created at a global object's declaration
 * site for any of its properties that are added late in a local scope.
 *
 * <p>If, after an object is declared, it is referenced directly in a way that
 * might create an alias for it, then none of its properties will be collapsed.
 * This behavior is a safeguard to prevent the values associated with the
 * flattened names from getting out of sync with the object's actual property
 * values. For example, in the following case, an alias a$b, if created, could
 * easily keep the value 0 even after a.b became 5:
 * <code> a = {b: 0}; c = a; c.b = 5; </code>.
 *
 * <p>This pass doesn't flatten property accesses of the form: a[b].
 *
 * <p>For lots of examples, see the unit test.
 *
 */
class CollapseProperties implements CompilerPass {

  // Warnings
  static final DiagnosticType UNSAFE_NAMESPACE_WARNING =
      DiagnosticType.warning(
          "JSC_UNSAFE_NAMESPACE",
          "incomplete alias created for namespace {0}");

  static final DiagnosticType NAMESPACE_REDEFINED_WARNING =
      DiagnosticType.warning(
          "JSC_NAMESPACE_REDEFINED",
          "namespace {0} should not be redefined");

  static final DiagnosticType UNSAFE_THIS = DiagnosticType.warning(
      "JSC_UNSAFE_THIS",
      "dangerous use of 'this' in static method {0}");

  private AbstractCompiler compiler;

  /** Global namespace tree */
  private List<Name> globalNames;

  /** Maps names (e.g. "a.b.c") to nodes in the global namespace tree */
  private Map<String, Name> nameMap;

  private final boolean collapsePropertiesOnExternTypes;
  private final boolean inlineAliases;

  /**
   * Creates an instance.
   *
   * @param compiler The JSCompiler, for reporting code changes
   * @param collapsePropertiesOnExternTypes if true, will rename user-defined
   *     static properties on externed typed. E.g. String.foo.
   * @param inlineAliases Whether we're allowed to inline local aliases of
   *     namespaces, etc.
   */
  CollapseProperties(AbstractCompiler compiler,
      boolean collapsePropertiesOnExternTypes, boolean inlineAliases) {
    this.compiler = compiler;
    this.collapsePropertiesOnExternTypes = collapsePropertiesOnExternTypes;
    this.inlineAliases = inlineAliases;
  }

  @Override
  public void process(Node externs, Node root) {
    GlobalNamespace namespace;
    if (collapsePropertiesOnExternTypes) {
      namespace = new GlobalNamespace(compiler, externs, root);
    } else {
      namespace = new GlobalNamespace(compiler, root);
    }

    if (inlineAliases) {
      inlineAliases(namespace);
    }
    nameMap = namespace.getNameIndex();
    globalNames = namespace.getNameForest();
    checkNamespaces();

    for (Name n : globalNames) {
      flattenReferencesToCollapsibleDescendantNames(n, n.name);
    }

    // We collapse property definitions after collapsing property references
    // because this step can alter the parse tree above property references,
    // invalidating the node ancestry stored with each reference.
    for (Name n : globalNames) {
      collapseDeclarationOfNameAndDescendants(n, n.name);
    }
  }

  /**
   * For each qualified name N in the global scope, we check if:
   * (a) No ancestor of N is ever aliased or assigned an unknown value type.
   *     (If N = "a.b.c", "a" and "a.b" are never aliased).
   * (b) N has exactly one write, and it lives in the global scope.
   * (c) N is aliased in a local scope.
   *
   * If (a) is true, then GlobalNamespace must know all the writes to N.
   * If (a) and (b) are true, then N cannot change during the execution of
   *    a local scope.
   * If (a) and (b) and (c) are true, then the alias can be inlined if the
   *    alias obeys the usual rules for how we decide whether a variable is
   *    inlineable.
   * @see InlineVariables
   */
  private void inlineAliases(GlobalNamespace namespace) {
    // Invariant: All the names in the worklist meet condition (a).
    Deque<Name> workList = new ArrayDeque<Name>(namespace.getNameForest());
    while (!workList.isEmpty()) {
      Name name = workList.pop();

      if (name.globalSets == 1 && name.localSets == 0 &&
          name.aliasingGets > 0) {
        // {@code name} meets condition (b). Find all of its local aliases
        // and try to inline them.
        List<Ref> refs = Lists.newArrayList(name.refs);
        for (Ref ref : refs) {
          if (ref.type == Type.ALIASING_GET && ref.scope.isLocal()) {
            // {@code name} meets condition (c). Try to inline it.
            if (inlineAliasIfPossible(ref, namespace)) {
              name.removeRef(ref);
            }
          }
        }
      }

      // Check if {@code name} has any aliases left after the
      // local-alias-inlining above.
      if ((name.type == Name.Type.OBJECTLIT ||
           name.type == Name.Type.FUNCTION) &&
          name.aliasingGets == 0 && name.props != null) {
        // All of {@code name}'s children meet condition (a), so they can be
        // added to the worklist.
        workList.addAll(name.props);
      }
    }
  }

  private boolean inlineAliasIfPossible(Ref alias, GlobalNamespace namespace) {
    // Ensure that the alias is assigned to a local variable at that
    // variable's declaration. If the alias's parent is a NAME,
    // then the NAME must be the child of a VAR node, and we must
    // be in a VAR assignment.
    Node aliasParent = alias.node.getParent();
    if (aliasParent.getType() == Token.NAME) {
      // Ensure that the local variable is well defined and never reassigned.
      Scope scope = alias.scope;
      Var aliasVar = scope.getVar(aliasParent.getString());
      ReferenceCollectingCallback collector =
          new ReferenceCollectingCallback(compiler,
              ReferenceCollectingCallback.DO_NOTHING_BEHAVIOR,
              Predicates.<Var>equalTo(aliasVar));
      (new NodeTraversal(compiler, collector)).traverseAtScope(scope);

      ReferenceCollection aliasRefs =
          collector.getReferenceCollection(aliasVar);
      if (aliasRefs.isWellDefined()
          && aliasRefs.firstReferenceIsAssigningDeclaration()
          && aliasRefs.isAssignedOnceInLifetime()) {
        // The alias is well-formed, so do the inlining now.
        int size = aliasRefs.references.size();
        Set<Node> newNodes = Sets.newHashSetWithExpectedSize(size - 1);
        for (int i = 1; i < size; i++) {
          ReferenceCollectingCallback.Reference aliasRef =
              aliasRefs.references.get(i);

          Node newNode = alias.node.cloneTree();
          aliasRef.getParent().replaceChild(aliasRef.getNameNode(), newNode);
          newNodes.add(newNode);
        }

        // just set the original alias to null.
        aliasParent.replaceChild(alias.node, new Node(Token.NULL));
        compiler.reportCodeChange();

        // Inlining the variable may have introduced new references
        // to descendents of {@code name}. So those need to be collected now.
        namespace.scanNewNodes(alias.scope, newNodes);
        return true;
      }
    }

    return false;
  }

  /**
   * Runs through all namespaces (prefixes of classes and enums), and checks if
   * any of them have been used in an unsafe way.
   */
  private void checkNamespaces() {
    for (Name name : nameMap.values()) {
      if (name.isNamespace() && name.refs != null &&
          (name.aliasingGets > 0 || name.localSets + name.globalSets > 1)) {
        boolean initialized = name.declaration != null;
        for (Ref ref : name.refs) {
          if (ref.type == Ref.Type.SET_FROM_GLOBAL ||
              ref.type == Ref.Type.SET_FROM_LOCAL) {
            if (initialized) {
              warnAboutNamespaceRedefinition(name, ref);
            }

            initialized = true;
          } else if (ref.type == Ref.Type.ALIASING_GET) {
            warnAboutNamespaceAliasing(name, ref);
          }
        }
      }
    }
  }

  /**
   * Reports a warning because a namespace was aliased.
   *
   * @param nameObj A namespace that is being aliased
   * @param ref The reference that forced the alias
   */
  private void warnAboutNamespaceAliasing(Name nameObj, Ref ref) {
    compiler.report(
        JSError.make(ref.sourceName, ref.node,
                     UNSAFE_NAMESPACE_WARNING, nameObj.fullName()));
  }

  /**
   * Reports a warning because a namespace was redefined.
   *
   * @param nameObj A namespace that is being redefined
   * @param ref The reference that set the namespace
   */
  private void warnAboutNamespaceRedefinition(Name nameObj, Ref ref) {
    compiler.report(
        JSError.make(ref.sourceName, ref.node,
                     NAMESPACE_REDEFINED_WARNING, nameObj.fullName()));
  }

  /**
   * Flattens all references to collapsible properties of a global name except
   * their initial definitions. Recurses on subnames.
   *
   * @param n An object representing a global name
   * @param alias The flattened name for {@code n}
   */
  private void flattenReferencesToCollapsibleDescendantNames(
      Name n, String alias) {
    if (n.props == null) return;

    for (Name p : n.props) {
      String propAlias = appendPropForAlias(alias, p.name);

      if (p.canCollapse()) {
        flattenReferencesTo(p, propAlias);
      }

      flattenReferencesToCollapsibleDescendantNames(p, propAlias);
    }
  }

  /**
   * Flattens all references to a collapsible property of a global name except
   * its initial definition.
   *
   * @param n A global property name (e.g. "a.b" or "a.b.c.d")
   * @param alias The flattened name (e.g. "a$b" or "a$b$c$d")
   */
  private void flattenReferencesTo(Name n, String alias) {
    if (n.refs != null) {
      String originalName = n.fullName();
      for (Ref r : n.refs) {
        Node rParent = r.node.getParent();

        // There are two cases when we shouldn't flatten a reference:
        // 1) Object literal keys, because duplicate keys show up as refs.
        // 2) References inside a complex assign. (a = x.y = 0). These are
        //    called TWIN references, because they show up twice in the
        //    reference list. Only collapse the set, not the alias.
        if (!NodeUtil.isObjectLitKey(r.node, rParent) &&
            (r.getTwin() == null || r.isSet())) {
          flattenNameRef(alias, r.node, rParent, originalName);
        }
      }
    }

    // Flatten all occurrences of a name as a prefix of its subnames. For
    // example, if {@code n} corresponds to the name "a.b", then "a.b" will be
    // replaced with "a$b" in all occurrences of "a.b.c", "a.b.c.d", etc.
    if (n.props != null) {
      for (Name p : n.props) {
        flattenPrefixes(alias, p, 1);
      }
    }
  }

  /**
   * Flattens all occurrences of a name as a prefix of subnames beginning
   * with a particular subname.
   *
   * @param n A global property name (e.g. "a.b.c.d")
   * @param alias A flattened prefix name (e.g. "a$b")
   * @param depth The difference in depth between the property name and
   *    the prefix name (e.g. 2)
   */
  private void flattenPrefixes(String alias, Name n, int depth) {
    // Only flatten the prefix of a name declaration if the name being
    // initialized is fully qualified (i.e. not an object literal key).
    String originalName = n.fullName();
    if (n.declaration != null && n.declaration.node != null &&
        n.declaration.node.getType() == Token.GETPROP) {
      flattenNameRefAtDepth(alias, n.declaration.node, depth, originalName);
    }

    if (n.refs != null) {
      for (Ref r : n.refs) {

        // References inside a complex assign (a = x.y = 0)
        // have twins. We should only flatten one of the twins.
        if (r.getTwin() == null || r.isSet()) {
          flattenNameRefAtDepth(alias, r.node, depth, originalName);
        }
      }
    }

    if (n.props != null) {
      for (Name p : n.props) {
        flattenPrefixes(alias, p, depth + 1);
      }
    }
  }

  /**
   * Flattens a particular prefix of a single name reference.
   *
   * @param alias A flattened prefix name (e.g. "a$b")
   * @param n The node corresponding to a subproperty name (e.g. "a.b.c.d")
   * @param depth The difference in depth between the property name and
   *    the prefix name (e.g. 2)
   * @param originalName String version of the property name.
   */
  private void flattenNameRefAtDepth(String alias, Node n, int depth,
      String originalName) {
    // This method has to work for both GETPROP chains and, in rare cases,
    // OBJLIT keys, possibly nested. That's why we check for children before
    // proceeding. In the OBJLIT case, we don't need to do anything.
    int nType = n.getType();
    boolean isQName = nType == Token.NAME || nType == Token.GETPROP;
    boolean isObjKey = nType == Token.STRING || nType == Token.NUMBER;
    Preconditions.checkState(isObjKey || isQName);
    if (isQName) {
      for (int i = 1; i < depth && n.hasChildren(); i++) {
        n = n.getFirstChild();
      }
      if (n.hasChildren()) {
        flattenNameRef(alias, n.getFirstChild(), n, originalName);
      }
    }
  }

  /**
   * Replaces a GETPROP a.b.c with a NAME a$b$c.
   *
   * @param alias A flattened prefix name (e.g. "a$b")
   * @param n The GETPROP node corresponding to the original name (e.g. "a.b")
   * @param parent {@code n}'s parent
   * @param originalName String version of the property name.
   */
  private void flattenNameRef(String alias, Node n, Node parent,
      String originalName) {
    // BEFORE:
    //   getprop
    //     getprop
    //       name a
    //       string b
    //     string c
    // AFTER:
    //   name a$b$c
    Node ref = NodeUtil.newName(
        compiler.getCodingConvention(), alias, n, originalName);
    NodeUtil.copyNameAnnotations(n.getLastChild(), ref);
    parent.replaceChild(n, ref);
    compiler.reportCodeChange();
  }

  /**
   * Collapses definitions of the collapsible properties of a global name.
   * Recurses on subnames that also represent JavaScript objects with
   * collapsible properties.
   *
   * @param n A node representing a global name
   * @param alias The flattened name for {@code n}
   */
  private void collapseDeclarationOfNameAndDescendants(Name n, String alias) {
    boolean canCollapseChildNames = n.canCollapseUnannotatedChildNames();

    // Handle this name first so that nested object literals get unrolled.
    if (n.canCollapse() && canCollapseChildNames) {
      updateObjLitOrFunctionDeclaration(n, alias);
    }

    if (n.props != null) {
      for (Name p : n.props) {
        // Recurse first so that saved node ancestries are intact when needed.
        collapseDeclarationOfNameAndDescendants(
            p, appendPropForAlias(alias, p.name));

        if (!p.inExterns && canCollapseChildNames && p.declaration != null &&
            p.declaration.node != null &&
            p.declaration.node.getParent() != null &&
            p.declaration.node.getParent().getType() == Token.ASSIGN) {
          updateSimpleDeclaration(
              appendPropForAlias(alias, p.name), p, p.declaration);
        }
      }
    }
  }

  /**
   * Updates the initial assignment to a collapsible property at global scope
   * by changing it to a variable declaration (e.g. a.b = 1 -> var a$b = 1).
   * The property's value may either be a primitive or an object literal or
   * function whose properties aren't collapsible.
   *
   * @param alias The flattened property name (e.g. "a$b")
   * @param refName The name for the reference being updated.
   * @param ref An object containing information about the assignment getting
   *     updated
   */
  private void updateSimpleDeclaration(String alias, Name refName, Ref ref) {
    Node rvalue = ref.node.getNext();
    Node parent = ref.node.getParent();
    Node gramps = parent.getParent();
    Node greatGramps = gramps.getParent();
    Node greatGreatGramps = greatGramps.getParent();

    if (rvalue != null && rvalue.getType() == Token.FUNCTION) {
      checkForHosedThisReferences(rvalue, refName.docInfo, refName);
    }

    // Create the new alias node.
    Node nameNode = NodeUtil.newName(
        compiler.getCodingConvention(), alias, gramps.getFirstChild(),
        refName.fullName());
    NodeUtil.copyNameAnnotations(ref.node.getLastChild(), nameNode);

    if (gramps.getType() == Token.EXPR_RESULT) {
      // BEFORE: a.b.c = ...;
      //   exprstmt
      //     assign
      //       getprop
      //         getprop
      //           name a
      //           string b
      //         string c
      //       NODE
      // AFTER: var a$b$c = ...;
      //   var
      //     name a$b$c
      //       NODE

      // Remove the rvalue (NODE).
      parent.removeChild(rvalue);
      nameNode.addChildToFront(rvalue);

      Node varNode = new Node(Token.VAR, nameNode);
      greatGramps.replaceChild(gramps, varNode);
    } else {
      // This must be a complex assignment.
      Preconditions.checkNotNull(ref.getTwin());

      // BEFORE:
      // ... (x.y = 3);
      //
      // AFTER:
      // var x$y;
      // ... (x$y = 3);

      Node current = gramps;
      Node currentParent = gramps.getParent();
      for (; currentParent.getType() != Token.SCRIPT &&
             currentParent.getType() != Token.BLOCK;
           current = currentParent,
           currentParent = currentParent.getParent()) {}

      // Create a stub variable declaration right
      // before the current statement.
      Node stubVar = new Node(Token.VAR, nameNode.cloneTree())
          .copyInformationFrom(nameNode);
      currentParent.addChildBefore(stubVar, current);

      parent.replaceChild(ref.node, nameNode);
    }

    compiler.reportCodeChange();
  }

  /**
   * Updates the first initialization (a.k.a "declaration") of a global name.
   * This involves flattening the global name (if it's not just a global
   * variable name already), collapsing object literal keys into global
   * variables, declaring stub global variables for properties added later
   * in a local scope, and eliminating the global name entirely (if possible).
   *
   * @param n An object representing a global name (e.g. "a", "a.b.c")
   * @param alias The flattened name for {@code n} (e.g. "a", "a$b$c")
   */
  private void updateObjLitOrFunctionDeclaration(Name n, String alias) {
    switch (n.declaration.node.getParent().getType()) {
      case Token.ASSIGN:
        updateObjLitOrFunctionDeclarationAtAssignNode(n, alias);
        break;
      case Token.VAR:
        updateObjLitOrFunctionDeclarationAtVarNode(n);
        break;
      case Token.FUNCTION:
        updateFunctionDeclarationAtFunctionNode(n);
        break;
    }
  }

  /**
   * Updates the first initialization (a.k.a "declaration") of a global name
   * that occurs at an ASSIGN node. See comment for
   * {@link #updateObjLitOrFunctionDeclaration}.
   *
   * @param n An object representing a global name (e.g. "a", "a.b.c")
   * @param alias The flattened name for {@code n} (e.g. "a", "a$b$c")
   */
  private void updateObjLitOrFunctionDeclarationAtAssignNode(
      Name n, String alias) {
    // NOTE: It's important that we don't add additional nodes
    // (e.g. a var node before the exprstmt) because the exprstmt might be
    // the child of an if statement that's not inside a block).

    Ref ref = n.declaration;
    Node rvalue = ref.node.getNext();
    Node varNode = new Node(Token.VAR);
    Node varParent = ref.node.getAncestor(3);
    Node gramps = ref.node.getAncestor(2);
    boolean isObjLit = rvalue.getType() == Token.OBJECTLIT;

    if (isObjLit && n.canEliminate()) {
      // Eliminate the object literal altogether.
      varParent.replaceChild(gramps, varNode);
      ref.node = null;

    } else {
      if (rvalue.getType() == Token.FUNCTION) {
        checkForHosedThisReferences(rvalue, n.docInfo, n);
      }

      ref.node.getParent().removeChild(rvalue);

      Node nameNode = NodeUtil.newName(
          compiler.getCodingConvention(),
          alias, ref.node.getAncestor(2), n.fullName());

      if (ref.node.getLastChild().getBooleanProp(Node.IS_CONSTANT_NAME)) {
        nameNode.putBooleanProp(Node.IS_CONSTANT_NAME, true);
      }

      varNode.addChildToBack(nameNode);
      nameNode.addChildToFront(rvalue);
      varParent.replaceChild(gramps, varNode);

      // Update the node ancestry stored in the reference.
      ref.node = nameNode;
    }

    if (isObjLit) {
      declareVarsForObjLitValues(
          n, alias, rvalue,
          varNode, varParent.getChildBefore(varNode), varParent);
    }

    addStubsForUndeclaredProperties(n, alias, varParent, varNode);

    if (!varNode.hasChildren()) {
      varParent.removeChild(varNode);
    }

    compiler.reportCodeChange();
  }

  /**
   * Warns about any references to "this" in the given FUNCTION. The function
   * is getting collapsed, so the references will change.
   */
  private void checkForHosedThisReferences(Node function, JSDocInfo docInfo,
      final Name name) {
    // A function is getting collapsed. Make sure that if it refers to
    // "this", it must be a constructor or documented with @this.
    if (docInfo == null ||
        (!docInfo.isConstructor() && !docInfo.hasThisType())) {
      NodeTraversal.traverse(compiler, function.getLastChild(),
          new NodeTraversal.AbstractShallowCallback() {
            public void visit(NodeTraversal t, Node n, Node parent) {
              if (n.getType() == Token.THIS) {
                compiler.report(
                    JSError.make(name.declaration.sourceName, n,
                        UNSAFE_THIS, name.fullName()));
              }
            }
          });
    }
  }

  /**
   * Updates the first initialization (a.k.a "declaration") of a global name
   * that occurs at a VAR node. See comment for
   * {@link #updateObjLitOrFunctionDeclaration}.
   *
   * @param n An object representing a global name (e.g. "a")
   */
  private void updateObjLitOrFunctionDeclarationAtVarNode(Name n) {
    Ref ref = n.declaration;
    String name = ref.node.getString();
    Node rvalue = ref.node.getFirstChild();
    Node varNode = ref.node.getParent();
    Node gramps = varNode.getParent();

    boolean isObjLit = rvalue.getType() == Token.OBJECTLIT;
    int numChanges = 0;

    if (isObjLit) {
      numChanges += declareVarsForObjLitValues(
          n, name, rvalue, varNode, gramps.getChildBefore(varNode),
          gramps);
    }

    numChanges += addStubsForUndeclaredProperties(n, name, gramps, varNode);

    if (isObjLit && n.canEliminate()) {
      varNode.removeChild(ref.node);
      if (!varNode.hasChildren()) {
        gramps.removeChild(varNode);
      }
      numChanges++;

      // Clear out the object reference, since we've eliminated it from the
      // parse tree.
      ref.node = null;
    }

    if (numChanges > 0) {
      compiler.reportCodeChange();
    }
  }

  /**
   * Updates the first initialization (a.k.a "declaration") of a global name
   * that occurs at a FUNCTION node. See comment for
   * {@link #updateObjLitOrFunctionDeclaration}.
   *
   * @param n An object representing a global name (e.g. "a")
   */
  private void updateFunctionDeclarationAtFunctionNode(Name n) {
    Ref ref = n.declaration;
    String fnName = ref.node.getString();
    addStubsForUndeclaredProperties(
        n, fnName, ref.node.getAncestor(2), ref.node.getParent());
  }

  /**
   * Declares global variables to serve as aliases for the values in an object
   * literal, optionally removing all of the object literal's keys and values.
   *
   * @param alias The object literal's flattened name (e.g. "a$b$c")
   * @param objlit The OBJLIT node
   * @param varNode The VAR node to which new global variables should be added
   *     as children
   * @param nameToAddAfter The child of {@code varNode} after which new
   *     variables should be added (may be null)
   * @param varParent {@code varNode}'s parent
   * @return The number of variables added
   */
  private int declareVarsForObjLitValues(
      Name objlitName, String alias, Node objlit, Node varNode,
      Node nameToAddAfter, Node varParent) {
    int numVars = 0;
    int arbitraryNameCounter = 0;
    boolean discardKeys = !objlitName.shouldKeepKeys();

    for (Node key = objlit.getFirstChild(), nextKey; key != null;
         key = nextKey) {
      Node value = key.getFirstChild();
      nextKey = key.getNext();

      // We generate arbitrary names for keys that aren't valid JavaScript
      // identifiers, since those keys are never referenced. (If they were,
      // this object literal's child names wouldn't be collapsible.) The only
      // reason that we don't eliminate them entirely is the off chance that
      // their values are expressions that have side effects.
      boolean isJsIdentifier = key.getType() != Token.NUMBER &&
                               TokenStream.isJSIdentifier(key.getString());
      String propName = isJsIdentifier ?
          key.getString() : String.valueOf(++arbitraryNameCounter);
      String propAlias = appendPropForAlias(alias, propName);
      String qName = objlitName.fullName() + '.' + propName;

      Node refNode = null;
      if (discardKeys) {
        objlit.removeChild(key);
        value.detachFromParent();
      } else {
        // Substitute a reference for the value.
        refNode = Node.newString(Token.NAME, propAlias);
        if (key.getBooleanProp(Node.IS_CONSTANT_NAME)) {
          refNode.putBooleanProp(Node.IS_CONSTANT_NAME, true);
        }

        key.replaceChild(value, refNode);
      }

      // Declare the collapsed name as a variable with the original value.
      Node nameNode = Node.newString(Token.NAME, propAlias);
      nameNode.addChildToFront(value);
      if (key.getBooleanProp(Node.IS_CONSTANT_NAME)) {
        nameNode.putBooleanProp(Node.IS_CONSTANT_NAME, true);
      }
      Node newVar = new Node(Token.VAR, nameNode)
          .copyInformationFromForTree(key);
      if (nameToAddAfter != null) {
        varParent.addChildAfter(newVar, nameToAddAfter);
      } else {
        varParent.addChildBefore(newVar, varNode);
      }
      compiler.reportCodeChange();
      nameToAddAfter = newVar;

      if (isJsIdentifier) {
        // Update the global name's node ancestry if it hasn't already been
        // done. (Duplicate keys in an object literal can bring us here twice
        // for the same global name.)
        Name p = nameMap.get(qName);
        if (p != null) {
          if (!discardKeys) {
            Ref newAlias =
                p.declaration.cloneAndReclassify(Ref.Type.ALIASING_GET);
            newAlias.node = refNode;
            p.addRef(newAlias);
          }

          p.declaration.node = nameNode;

          if (value.getType() == Token.FUNCTION) {
            checkForHosedThisReferences(value, value.getJSDocInfo(), p);
          }
        }
      }

      numVars++;
    }
    return numVars;
  }

  /**
   * Adds global variable "stubs" for any properties of a global name that are
   * only set in a local scope or read but never set.
   *
   * @param n An object representing a global name (e.g. "a", "a.b.c")
   * @param alias The flattened name of the object whose properties we are
   *     adding stubs for (e.g. "a$b$c")
   * @param parent The node to which new global variables should be added
   *     as children
   * @param addAfter The child of after which new
   *     variables should be added (may be null)
   * @return The number of variables added
   */
  private int addStubsForUndeclaredProperties(
      Name n, String alias, Node parent, Node addAfter) {
    Preconditions.checkArgument(NodeUtil.isStatementBlock(parent));
    Preconditions.checkNotNull(addAfter);
    int numStubs = 0;
    if (n.props != null) {
      for (Name p : n.props) {
        if (p.needsToBeStubbed()) {
          String propAlias = appendPropForAlias(alias, p.name);
          Node nameNode = Node.newString(Token.NAME, propAlias);
          Node newVar = new Node(Token.VAR, nameNode)
              .copyInformationFromForTree(addAfter);
          parent.addChildAfter(newVar, addAfter);
          addAfter = newVar;
          numStubs++;
          compiler.reportCodeChange();

          // Determine if this is a constant var by checking the first
          // reference to it. Don't check the declaration, as it might be null.
          if (p.refs.get(0).node.getLastChild().getBooleanProp(
                Node.IS_CONSTANT_NAME)) {
            nameNode.putBooleanProp(Node.IS_CONSTANT_NAME, true);
          }
        }
      }
    }
    return numStubs;
  }

  private static String appendPropForAlias(String root, String prop) {
    if (prop.indexOf('$') != -1) {
      // Encode '$' in a property as '$0'. Because '0' cannot be the
      // start of an identifier, this will never conflict with our
      // encoding from '.' -> '$'.
      prop = prop.replaceAll("\\$", "\\$0");
    }
    return root + '$' + prop;
  }
}
