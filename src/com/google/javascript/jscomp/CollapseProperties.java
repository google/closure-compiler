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
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.GlobalNamespace.AstChange;
import com.google.javascript.jscomp.GlobalNamespace.Name;
import com.google.javascript.jscomp.GlobalNamespace.Ref;
import com.google.javascript.jscomp.GlobalNamespace.Ref.Type;
import com.google.javascript.jscomp.ReferenceCollectingCallback.ReferenceCollection;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;
import com.google.javascript.rhino.jstype.JSType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

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

    for (Name name : globalNames) {
      flattenReferencesToCollapsibleDescendantNames(name, name.getBaseName());
    }

    // We collapse property definitions after collapsing property references
    // because this step can alter the parse tree above property references,
    // invalidating the node ancestry stored with each reference.
    for (Name name : globalNames) {
      collapseDeclarationOfNameAndDescendants(name, name.getBaseName());
    }
  }

  /**
   * For each qualified name N in the global scope, we check if:
   * (a) No ancestor of N is ever aliased or assigned an unknown value type.
   *     (If N = "a.b.c", "a" and "a.b" are never aliased).
   * (b) N has exactly one write, and it lives in the global scope.
   * (c) N is aliased in a local scope.
   * (d) N is aliased in global scope
   *
   * If (a) is true, then GlobalNamespace must know all the writes to N.
   * If (a) and (b) are true, then N cannot change during the execution of
   *    a local scope.
   * If (a) and (b) and (c) are true, then the alias can be inlined if the
   *    alias obeys the usual rules for how we decide whether a variable is
   *    inlineable.
   * If (a) and (b) and (d) are true, then inline the alias if possible (if
   * it is assigned exactly once unconditionally).
   * @see InlineVariables
   */
  private void inlineAliases(GlobalNamespace namespace) {
    // Invariant: All the names in the worklist meet condition (a).
    Deque<Name> workList = new ArrayDeque<Name>(namespace.getNameForest());

    while (!workList.isEmpty()) {
      Name name = workList.pop();

      // Don't attempt to inline a getter or setter property as a variable.
      if (name.type == Name.Type.GET || name.type == Name.Type.SET) {
        continue;
      }

      if (!name.inExterns && name.globalSets == 1 && name.localSets == 0 &&
          name.aliasingGets > 0) {
        // {@code name} meets condition (b). Find all of its local aliases
        // and try to inline them.
        List<Ref> refs = Lists.newArrayList(name.getRefs());
        for (Ref ref : refs) {
          if (ref.type == Type.ALIASING_GET && ref.scope.isLocal()) {
            // {@code name} meets condition (c). Try to inline it.
            // TODO(johnlenz): consider picking up new aliases at the end
            // of the pass instead of immediately like we do for global
            // inlines.
            if (inlineAliasIfPossible(ref, namespace)) {
              name.removeRef(ref);
            }
          } else if (ref.type == Type.ALIASING_GET
              && ref.scope.isGlobal()
              && ref.getTwin() == null) {  // ignore aliases in chained assignments
            if (inlineGlobalAliasIfPossible(ref, namespace)) {
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

  /**
   * Attempt to inline an global alias of a global name. This requires that
   * the name is well defined: assigned unconditionally, assigned exactly once.
   * It is assumed that, the name for which it is an alias must already
   * meet these same requirements.
   *
   * @param alias The alias to inline
   * @return Whether the alias was inlined.
   */
  private boolean inlineGlobalAliasIfPossible(
      Ref alias, GlobalNamespace namespace) {
    // Ensure that the alias is assigned to global name at that the
    // declaration.

    Node aliasParent = alias.node.getParent();
    if (aliasParent.isAssign() && NodeUtil.isExecutedExactlyOnce(aliasParent)) {
      String target = aliasParent.getFirstChild().getQualifiedName();
      if (target != null) {
        Name name = namespace.getSlot(target);
        if (name != null && isInlinableGlobalAlias(name)) {
          List<AstChange> newNodes = Lists.newArrayList();

          List<Ref> refs = Lists.newArrayList(name.getRefs());
          for (Ref ref : refs) {
            switch (ref.type) {
              case SET_FROM_GLOBAL:
                continue;
              case DIRECT_GET:
              case ALIASING_GET:
                Node newNode = alias.node.cloneTree();
                Node node = ref.node;
                node.getParent().replaceChild(node, newNode);
                newNodes.add(new AstChange(ref.module, ref.scope, newNode));
                name.removeRef(ref);
                break;
              default:
                throw new IllegalStateException();
            }
          }

          rewriteAliasProps(name, alias.node, 0, newNodes);

          // just set the original alias to null.
          aliasParent.replaceChild(alias.node, IR.nullNode());
          compiler.reportCodeChange();

          // Inlining the variable may have introduced new references
          // to descendants of {@code name}. So those need to be collected now.
          namespace.scanNewNodes(newNodes);

          return true;
        }
      }
    }

    return false;
  }

  /**
   * @param name The Name whose properties references should be updated.
   * @param value The value to use when rewriting.
   * @param depth The chain depth.
   * @param newNodes Expression nodes that have been updated.
   */
  private void rewriteAliasProps(
      Name name, Node value, int depth, List<AstChange> newNodes) {
    if (name.props != null) {
      Preconditions.checkState(!
          value.getQualifiedName().equals(name.getFullName()));

      for (Name prop : name.props) {
        rewriteAliasProps(prop, value, depth + 1, newNodes);

        List<Ref> refs = Lists.newArrayList(prop.getRefs());
        for (Ref ref : refs) {
          Node target = ref.node;
          for (int i = 0; i <= depth; i++) {
            if (target.isGetProp()) {
              target = target.getFirstChild();
            } else if (NodeUtil.isObjectLitKey(target)) {
              // Object literal key definitions are a little trickier, as we
              // need to find the assignment target
              Node gparent = target.getParent().getParent();
              if (gparent.isAssign()) {
                target = gparent.getFirstChild();
              } else {
                Preconditions.checkState(NodeUtil.isObjectLitKey(gparent));
                target = gparent;
              }
            } else {
              throw new IllegalStateException(
                  "unexpected: " + target.toString());
            }
          }
          Preconditions.checkState(target.isGetProp() || target.isName());
          target.getParent().replaceChild(target, value.cloneTree());
          prop.removeRef(ref);
          // Rescan the expression root.
          newNodes.add(new AstChange(ref.module, ref.scope, ref.node));
        }
      }
    }
  }

  private boolean isInlinableGlobalAlias(Name name) {
    // Only simple aliases with direct usage are inlinable.
    if (name.inExterns || name.globalSets != 1 || name.localSets != 0
        || !name.canCollapse()) {
      return false;
    }

    // Only allow inlining of simple references.
    for (Ref ref : name.getRefs()) {
      switch (ref.type) {
        case SET_FROM_GLOBAL:
          // Expect one global set
          continue;
        case SET_FROM_LOCAL:
          throw new IllegalStateException();
        case ALIASING_GET:
        case DIRECT_GET:
          continue;
        case PROTOTYPE_GET:
        case CALL_GET:
        case DELETE_PROP:
          return false;
        default:
          throw new IllegalStateException();
      }
    }
    return true;
  }

  private boolean inlineAliasIfPossible(Ref alias, GlobalNamespace namespace) {
    // Ensure that the alias is assigned to a local variable at that
    // variable's declaration. If the alias's parent is a NAME,
    // then the NAME must be the child of a VAR node, and we must
    // be in a VAR assignment.
    Node aliasParent = alias.node.getParent();
    if (aliasParent.isName()) {
      // Ensure that the local variable is well defined and never reassigned.
      Scope scope = alias.scope;
      Var aliasVar = scope.getVar(aliasParent.getString());
      ReferenceCollectingCallback collector =
          new ReferenceCollectingCallback(compiler,
              ReferenceCollectingCallback.DO_NOTHING_BEHAVIOR,
              Predicates.<Var>equalTo(aliasVar));
      (new NodeTraversal(compiler, collector)).traverseAtScope(scope);

      ReferenceCollection aliasRefs = collector.getReferences(aliasVar);
      List<AstChange> newNodes = Lists.newArrayList();
      if (aliasRefs.isWellDefined()
          && aliasRefs.firstReferenceIsAssigningDeclaration()
          && aliasRefs.isAssignedOnceInLifetime()) {
        // The alias is well-formed, so do the inlining now.
        int size = aliasRefs.references.size();
        for (int i = 1; i < size; i++) {
          ReferenceCollectingCallback.Reference aliasRef =
              aliasRefs.references.get(i);

          Node newNode = alias.node.cloneTree();
          aliasRef.getParent().replaceChild(aliasRef.getNode(), newNode);
          newNodes.add(new AstChange(
              getRefModule(aliasRef), aliasRef.getScope(), newNode));
        }

        // just set the original alias to null.
        aliasParent.replaceChild(alias.node, IR.nullNode());
        compiler.reportCodeChange();

        // Inlining the variable may have introduced new references
        // to descendants of {@code name}. So those need to be collected now.
        namespace.scanNewNodes(newNodes);
        return true;
      }
    }

    return false;
  }

  JSModule getRefModule(ReferenceCollectingCallback.Reference ref) {
    CompilerInput input  = compiler.getInput(ref.getInputId());
    return input == null ? null : input.getModule();
  }

  /**
   * Runs through all namespaces (prefixes of classes and enums), and checks if
   * any of them have been used in an unsafe way.
   */
  private void checkNamespaces() {
    for (Name name : nameMap.values()) {
      if (name.isNamespace() &&
          (name.aliasingGets > 0 || name.localSets + name.globalSets > 1 ||
           name.deleteProps > 0)) {
        boolean initialized = name.getDeclaration() != null;
        for (Ref ref : name.getRefs()) {
          if (ref == name.getDeclaration()) {
            continue;
          }

          if (ref.type == Ref.Type.DELETE_PROP) {
            if (initialized) {
              warnAboutNamespaceRedefinition(name, ref);
            }
          } else if (
              ref.type == Ref.Type.SET_FROM_GLOBAL ||
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
        JSError.make(ref.getSourceName(), ref.node,
                     UNSAFE_NAMESPACE_WARNING, nameObj.getFullName()));
  }

  /**
   * Reports a warning because a namespace was redefined.
   *
   * @param nameObj A namespace that is being redefined
   * @param ref The reference that set the namespace
   */
  private void warnAboutNamespaceRedefinition(Name nameObj, Ref ref) {
    compiler.report(
        JSError.make(ref.getSourceName(), ref.node,
                     NAMESPACE_REDEFINED_WARNING, nameObj.getFullName()));
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
    if (n.props == null) {
      return;
    }

    for (Name p : n.props) {
      String propAlias = appendPropForAlias(alias, p.getBaseName());

      if (p.canCollapse()) {
        flattenReferencesTo(p, propAlias);
      } else if (p.isSimpleStubDeclaration()) {
        flattenSimpleStubDeclaration(p, propAlias);
      }

      flattenReferencesToCollapsibleDescendantNames(p, propAlias);
    }
  }


  /**
   * Flattens a stub declaration.
   * This is mostly a hack to support legacy users.
   */
  private void flattenSimpleStubDeclaration(Name name, String alias) {
    Ref ref = Iterables.getOnlyElement(name.getRefs());
    Node nameNode = NodeUtil.newName(
        compiler.getCodingConvention(), alias, ref.node,
        name.getFullName());
    Node varNode = IR.var(nameNode).copyInformationFrom(nameNode);

    Preconditions.checkState(
        ref.node.getParent().isExprResult());
    Node parent = ref.node.getParent();
    Node gramps = parent.getParent();
    gramps.replaceChild(parent, varNode);
    compiler.reportCodeChange();
  }


  /**
   * Flattens all references to a collapsible property of a global name except
   * its initial definition.
   *
   * @param n A global property name (e.g. "a.b" or "a.b.c.d")
   * @param alias The flattened name (e.g. "a$b" or "a$b$c$d")
   */
  private void flattenReferencesTo(Name n, String alias) {
    String originalName = n.getFullName();
    for (Ref r : n.getRefs()) {
      if (r == n.getDeclaration()) {
        // Declarations are handled separately.
        continue;
      }

      Node rParent = r.node.getParent();

      // There are two cases when we shouldn't flatten a reference:
      // 1) Object literal keys, because duplicate keys show up as refs.
      // 2) References inside a complex assign. (a = x.y = 0). These are
      //    called TWIN references, because they show up twice in the
      //    reference list. Only collapse the set, not the alias.
      if (!NodeUtil.isObjectLitKey(r.node) &&
          (r.getTwin() == null || r.isSet())) {
        flattenNameRef(alias, r.node, rParent, originalName);
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
    String originalName = n.getFullName();
    Ref decl = n.getDeclaration();
    if (decl != null && decl.node != null &&
        decl.node.isGetProp()) {
      flattenNameRefAtDepth(alias, decl.node, depth, originalName);
    }

    for (Ref r : n.getRefs()) {
      if (r == decl) {
        // Declarations are handled separately.
        continue;
      }

      // References inside a complex assign (a = x.y = 0)
      // have twins. We should only flatten one of the twins.
      if (r.getTwin() == null || r.isSet()) {
        flattenNameRefAtDepth(alias, r.node, depth, originalName);
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
    boolean isObjKey = NodeUtil.isObjectLitKey(n);
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
    if (parent.isCall() && n == parent.getFirstChild()) {
      // The node was a call target, we are deliberately flatten these as
      // we node the "this" isn't provided by the namespace. Mark it as such:
      parent.putBooleanProp(Node.FREE_CALL, true);
    }

    JSType type = n.getJSType();
    if (type != null) {
      ref.setJSType(type);
    }
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
    if (n.canCollapse()) {
      updateObjLitOrFunctionDeclaration(n, alias, canCollapseChildNames);
    }

    if (n.props != null) {
      for (Name p : n.props) {
        // Recurse first so that saved node ancestries are intact when needed.
        collapseDeclarationOfNameAndDescendants(
            p, appendPropForAlias(alias, p.getBaseName()));

        if (!p.inExterns && canCollapseChildNames &&
            p.getDeclaration() != null &&
            p.canCollapse() &&
            p.getDeclaration().node != null &&
            p.getDeclaration().node.getParent() != null &&
            p.getDeclaration().node.getParent().isAssign()) {
          updateSimpleDeclaration(
              appendPropForAlias(alias, p.getBaseName()), p, p.getDeclaration());
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

    if (rvalue != null && rvalue.isFunction()) {
      checkForHosedThisReferences(rvalue, refName.docInfo, refName);
    }

    // Create the new alias node.
    Node nameNode = NodeUtil.newName(
        compiler.getCodingConvention(), alias, gramps.getFirstChild(),
        refName.getFullName());
    NodeUtil.copyNameAnnotations(ref.node.getLastChild(), nameNode);

    if (gramps.isExprResult()) {
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

      // Remove the r-value (NODE).
      parent.removeChild(rvalue);
      nameNode.addChildToFront(rvalue);

      Node varNode = IR.var(nameNode);
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
      for (; !currentParent.isScript() &&
             !currentParent.isBlock();
           current = currentParent,
           currentParent = currentParent.getParent()) {}

      // Create a stub variable declaration right
      // before the current statement.
      Node stubVar = IR.var(nameNode.cloneTree())
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
   * in a local scope.
   *
   * It may seem odd that this function also takes care of declaring stubs
   * for direct children. The ultimate goal of this function is to eliminate
   * the global name entirely (when possible), so that "middlemen" namespaces
   * disappear, and to do that we need to make sure that all the direct children
   * will be collapsed as well.
   *
   * @param n An object representing a global name (e.g. "a", "a.b.c")
   * @param alias The flattened name for {@code n} (e.g. "a", "a$b$c")
   * @param canCollapseChildNames Whether it's possible to collapse children of
   *     this name. (This is mostly passed for convenience; it's equivalent to
   *     n.canCollapseChildNames()).
   */
  private void updateObjLitOrFunctionDeclaration(
      Name n, String alias, boolean canCollapseChildNames) {
    Ref decl = n.getDeclaration();
    if (decl == null) {
      // Some names do not have declarations, because they
      // are only defined in local scopes.
      return;
    }

    if (decl.getTwin() != null) {
      // Twin declarations will get handled when normal references
      // are handled.
      return;
    }

    switch (decl.node.getParent().getType()) {
      case Token.ASSIGN:
        updateObjLitOrFunctionDeclarationAtAssignNode(
            n, alias, canCollapseChildNames);
        break;
      case Token.VAR:
        updateObjLitOrFunctionDeclarationAtVarNode(n, canCollapseChildNames);
        break;
      case Token.FUNCTION:
        updateFunctionDeclarationAtFunctionNode(n, canCollapseChildNames);
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
      Name n, String alias, boolean canCollapseChildNames) {
    // NOTE: It's important that we don't add additional nodes
    // (e.g. a var node before the exprstmt) because the exprstmt might be
    // the child of an if statement that's not inside a block).

    Ref ref = n.getDeclaration();
    Node rvalue = ref.node.getNext();
    Node varNode = new Node(Token.VAR);
    Node varParent = ref.node.getAncestor(3);
    Node gramps = ref.node.getAncestor(2);
    boolean isObjLit = rvalue.isObjectLit();
    boolean insertedVarNode = false;

    if (isObjLit && n.canEliminate()) {
      // Eliminate the object literal altogether.
      varParent.replaceChild(gramps, varNode);
      ref.node = null;
      insertedVarNode = true;

    } else if (!n.isSimpleName()) {
      // Create a VAR node to declare the name.
      if (rvalue.isFunction()) {
        checkForHosedThisReferences(rvalue, n.docInfo, n);
      }

      ref.node.getParent().removeChild(rvalue);

      Node nameNode = NodeUtil.newName(
          compiler.getCodingConvention(),
          alias, ref.node.getAncestor(2), n.getFullName());

      JSDocInfo info = ref.node.getParent().getJSDocInfo();
      if (ref.node.getLastChild().getBooleanProp(Node.IS_CONSTANT_NAME) ||
          (info != null && info.isConstant())) {
        nameNode.putBooleanProp(Node.IS_CONSTANT_NAME, true);
      }

      if (info != null) {
        varNode.setJSDocInfo(info);
      }
      varNode.addChildToBack(nameNode);
      nameNode.addChildToFront(rvalue);
      varParent.replaceChild(gramps, varNode);

      // Update the node ancestry stored in the reference.
      ref.node = nameNode;
      insertedVarNode = true;
    }

    if (canCollapseChildNames) {
      if (isObjLit) {
        declareVarsForObjLitValues(
            n, alias, rvalue,
            varNode, varParent.getChildBefore(varNode), varParent);
      }

      addStubsForUndeclaredProperties(n, alias, varParent, varNode);
    }

    if (insertedVarNode) {
      if (!varNode.hasChildren()) {
        varParent.removeChild(varNode);
      }
      compiler.reportCodeChange();
    }
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
            @Override
            public void visit(NodeTraversal t, Node n, Node parent) {
              if (n.isThis()) {
                compiler.report(
                    JSError.make(name.getDeclaration().getSourceName(), n,
                        UNSAFE_THIS, name.getFullName()));
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
  private void updateObjLitOrFunctionDeclarationAtVarNode(
      Name n, boolean canCollapseChildNames) {
    if (!canCollapseChildNames) {
      return;
    }

    Ref ref = n.getDeclaration();
    String name = ref.node.getString();
    Node rvalue = ref.node.getFirstChild();
    Node varNode = ref.node.getParent();
    Node gramps = varNode.getParent();

    boolean isObjLit = rvalue.isObjectLit();
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
  private void updateFunctionDeclarationAtFunctionNode(
      Name n, boolean canCollapseChildNames) {
    if (!canCollapseChildNames) {
      return;
    }

    Ref ref = n.getDeclaration();
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

      // A get or a set can not be rewritten as a VAR.
      if (key.isGetterDef() || key.isSetterDef()) {
        continue;
      }

      // We generate arbitrary names for keys that aren't valid JavaScript
      // identifiers, since those keys are never referenced. (If they were,
      // this object literal's child names wouldn't be collapsible.) The only
      // reason that we don't eliminate them entirely is the off chance that
      // their values are expressions that have side effects.
      boolean isJsIdentifier = !key.isNumber() &&
                               TokenStream.isJSIdentifier(key.getString());
      String propName = isJsIdentifier ?
          key.getString() : String.valueOf(++arbitraryNameCounter);

      // If the name cannot be collapsed, skip it.
      String qName = objlitName.getFullName() + '.' + propName;
      Name p = nameMap.get(qName);
      if (p != null && !p.canCollapse()) {
        continue;
      }

      String propAlias = appendPropForAlias(alias, propName);
      Node refNode = null;
      if (discardKeys) {
        objlit.removeChild(key);
        value.detachFromParent();
      } else {
        // Substitute a reference for the value.
        refNode = IR.name(propAlias);
        if (key.getBooleanProp(Node.IS_CONSTANT_NAME)) {
          refNode.putBooleanProp(Node.IS_CONSTANT_NAME, true);
        }

        key.replaceChild(value, refNode);
      }

      // Declare the collapsed name as a variable with the original value.
      Node nameNode = IR.name(propAlias);
      nameNode.addChildToFront(value);
      if (key.getBooleanProp(Node.IS_CONSTANT_NAME)) {
        nameNode.putBooleanProp(Node.IS_CONSTANT_NAME, true);
      }
      Node newVar = IR.var(nameNode)
          .copyInformationFromForTree(key);
      if (nameToAddAfter != null) {
        varParent.addChildAfter(newVar, nameToAddAfter);
      } else {
        varParent.addChildBefore(newVar, varNode);
      }
      compiler.reportCodeChange();
      nameToAddAfter = newVar;

      // Update the global name's node ancestry if it hasn't already been
      // done. (Duplicate keys in an object literal can bring us here twice
      // for the same global name.)
      if (isJsIdentifier && p != null) {
        if (!discardKeys) {
          Ref newAlias =
              p.getDeclaration().cloneAndReclassify(Ref.Type.ALIASING_GET);
          newAlias.node = refNode;
          p.addRef(newAlias);
        }

        p.getDeclaration().node = nameNode;

        if (value.isFunction()) {
          checkForHosedThisReferences(value, value.getJSDocInfo(), p);
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
    Preconditions.checkState(n.canCollapseUnannotatedChildNames());
    Preconditions.checkArgument(NodeUtil.isStatementBlock(parent));
    Preconditions.checkNotNull(addAfter);
    int numStubs = 0;
    if (n.props != null) {
      for (Name p : n.props) {
        if (p.needsToBeStubbed()) {
          String propAlias = appendPropForAlias(alias, p.getBaseName());
          Node nameNode = IR.name(propAlias);
          Node newVar = IR.var(nameNode)
              .copyInformationFromForTree(addAfter);
          parent.addChildAfter(newVar, addAfter);
          addAfter = newVar;
          numStubs++;
          compiler.reportCodeChange();

          // Determine if this is a constant var by checking the first
          // reference to it. Don't check the declaration, as it might be null.
          if (p.getRefs().get(0).node.getLastChild().getBooleanProp(
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
      prop = prop.replace("$", "$0");
    }
    return root + '$' + prop;
  }
}
