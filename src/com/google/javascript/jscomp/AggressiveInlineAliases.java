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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.javascript.jscomp.GlobalNamespace.AstChange;
import com.google.javascript.jscomp.GlobalNamespace.Name;
import com.google.javascript.jscomp.GlobalNamespace.Name.Inlinability;
import com.google.javascript.jscomp.GlobalNamespace.Ref;
import com.google.javascript.jscomp.GlobalNamespace.Ref.Type;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Inlines type aliases if they are explicitly or effectively const. Also inlines inherited static
 * property accesses for ES6 classes.
 *
 * <p>This frees subsequent optimization passes from the responsibility of having to reason about
 * alias chains and is a requirement for correct behavior in at least CollapseProperties and
 * J2clPropertyInlinerPass.
 *
 * <p>This is designed to be no more unsafe than CollapseProperties. It will in some cases inline
 * properties, possibly past places that change the property value. However, it will only do so in
 * cases where CollapseProperties would unsafely collapse the property anyway.
 */
class AggressiveInlineAliases implements CompilerPass {

  static final DiagnosticType UNSAFE_CTOR_ALIASING =
      DiagnosticType.warning(
          "JSC_UNSAFE_CTOR_ALIASING",
          "Variable {0} aliases a constructor, " + "so it cannot be assigned multiple times");

  private final AbstractCompiler compiler;
  private boolean codeChanged;

  AggressiveInlineAliases(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.codeChanged = true;
  }

  @Override
  public void process(Node externs, Node root) {
    while (this.codeChanged) {
      this.codeChanged = false;
      GlobalNamespace namespace = new GlobalNamespace(compiler, root);
      inlineAliases(namespace);
    }
  }

  private JSModule getRefModule(Reference ref) {
    CompilerInput input = compiler.getInput(ref.getInputId());
    return input == null ? null : input.getModule();
  }

  /**
   * For each qualified name N in the global scope, we check if: (a) No ancestor of N is ever
   * aliased or assigned an unknown value type. (If N = "a.b.c", "a" and "a.b" are never aliased).
   * (b) N has exactly one write, and it lives in the global scope. (c) N is aliased in a local
   * scope. (d) N is aliased in global scope
   *
   * <p>If (a) is true, then GlobalNamespace must know all the writes to N. If (a) and (b) are true,
   * then N cannot change during the execution of a local scope. If (a) and (b) and (c) are true,
   * then the alias can be inlined if the alias obeys the usual rules for how we decide whether a
   * variable is inlineable. If (a) and (b) and (d) are true, then inline the alias if possible (if
   * it is assigned exactly once unconditionally).
   *
   * <p>For (a), (b), and (c) are true and the alias is of a constructor, we may also partially
   * inline the alias - i.e. replace some references with the constructor but not all - since
   * constructor properties are always collapsed, so we want to be more aggressive about removing
   * aliases. This is similar to what FlowSensitiveInlineVariables does.
   *
   * <p>If (a) is not true, but the property is a 'declared type' (which CollapseProperties will
   * unsafely collapse), we also inline any properties without @nocollapse. This is unsafe but no
   * more unsafe than what CollapseProperties does. This pass and CollapseProperties share the logic
   * to determine when a name is unsafely collapsible in {@link Name#canCollapse()}
   *
   * @see InlineVariables
   */
  private void inlineAliases(GlobalNamespace namespace) {
    // Invariant: All the names in the worklist meet condition (a).
    // adds all top-level names to the worklist, but not any properties on those names.
    Deque<Name> workList = new ArrayDeque<>(namespace.getNameForest());

    while (!workList.isEmpty()) {
      Name name = workList.pop();

      // Don't attempt to inline a getter or setter property as a variable.
      if (name.isGetOrSetDefinition()) {
        continue;
      }

      if (!name.inExterns()
          && name.getGlobalSets() == 1
          && name.getLocalSets() == 0
          && name.getAliasingGets() > 0) {
        // {@code name} meets condition (b). Find all of its local aliases
        // and try to inline them.
        List<Ref> refs = new ArrayList<>(name.getRefs());
        for (Ref ref : refs) {
          Scope hoistScope = ref.scope.getClosestHoistScope();
          if (ref.type == Type.ALIASING_GET && !mayBeGlobalAlias(ref) && ref.getTwin() == null) {
            // {@code name} meets condition (c). Try to inline it.
            // TODO(johnlenz): consider picking up new aliases at the end
            // of the pass instead of immediately like we do for global
            // inlines.
            inlineAliasIfPossible(name, ref, namespace);
          } else if (ref.type == Type.ALIASING_GET
              && hoistScope.isGlobal()
              && ref.getTwin() == null) { // ignore aliases in chained assignments
            inlineGlobalAliasIfPossible(name, ref, namespace);
          }
        }
      }

      if (!name.inExterns() && name.isClass()) {
        List<Name> subclasses = name.subclasses;
        if (subclasses != null && name.props != null) {
          for (Name subclass : subclasses) {
            for (Name prop : name.props) {
              rewriteAllSubclassInheritedAccesses(name, subclass, prop, namespace);
            }
          }
        }
      }
      maybeAddPropertiesToWorklist(name, workList);
    }
  }

  /**
   * Adds properties of `name` to the worklist if the following conditions hold:
   *
   * <ol>
   *   <li>1. The given property of `name` either meets condition (a) or is unsafely collapsible (as
   *       defined by {@link Name#canCollapse()}
   *   <li>2. `name` meets condition (b)
   * </ol>
   *
   * This only adds direct properties of a name, not all its descendants. For example, this adds
   * `a.b` given `a`, but not `a.b.c`.
   */
  private void maybeAddPropertiesToWorklist(Name name, Deque<Name> workList) {
    if (!(name.isObjectLiteral() || name.isFunction() || name.isClass())) {
      // Don't add properties for things like `Foo` in
      //   const Foo = someMysteriousFunctionCall();
      // Since `Foo` is not declared as an object, class, or function literal, assume its value
      // may be aliased somewhere and its properties do not meet condition (a).
      return;
    }
    if (isUnsafelyReassigned(name)) {
      // Don't add properties if this was assigned multiple times, except for 'safe' reassignments:
      //    var ns = ns || {};
      // This is equivalent to condition (b)
      return;
    }
    if (name.props == null) {
      return;
    }

    if (name.getAliasingGets() == 0) {
      // All of {@code name}'s children meet condition (a), so they can be
      // added to the worklist.
      workList.addAll(name.props);
    } else {
      // The children do NOT meet condition (a) but we may try to add them anyway.
      // This is because CollapseProperties will unsafely collapse properties on constructors and
      // enums, so we want to be more aggressive about inlining references to their children.
      for (Name property : name.props) {
        // Only add properties that would be unsafely collapsed by CollapseProperties
        if (property.canCollapse()) {
          workList.add(property);
        }
      }
    }
  }

  /**
   * Inline all references to inherited static superclass properties from the subclass or any
   * descendant of the given subclass. Avoids inlining references to inherited methods when
   * possible, since they may use this or super().
   *
   * @param superclassNameObj The Name of the superclass
   * @param subclassNameObj The Name of the subclass
   * @param prop The property on the superclass to rewrite, if any descendant accesses it.
   * @param namespace The GlobalNamespace containing superclassNameObj
   */
  private boolean rewriteAllSubclassInheritedAccesses(
      Name superclassNameObj, Name subclassNameObj, Name prop, GlobalNamespace namespace) {
    Ref propDeclRef = prop.getDeclaration();
    if (propDeclRef == null
        || propDeclRef.node == null
        || !propDeclRef.node.getParent().isAssign()) {
      return false;
    }
    Node propRhs = propDeclRef.node.getParent().getLastChild();
    if (propRhs.isFunction()) {
      return false;
    }

    String subclassQualifiedPropName = subclassNameObj.getFullName() + "." + prop.getBaseName();
    Name subclassPropNameObj = namespace.getOwnSlot(subclassQualifiedPropName);
    // Don't rewrite if the subclass ever shadows the parent static property.
    // This may also back off on cases where the subclass first accesses the parent property, then
    // shadows it.
    if (subclassPropNameObj != null
        && (subclassPropNameObj.getLocalSets() > 0 || subclassPropNameObj.getGlobalSets() > 0)) {
      return false;
    }

    // Recurse to find potential sub-subclass accesses of the superclass property.
    if (subclassNameObj.subclasses != null) {
      for (Name name : subclassNameObj.subclasses) {
        rewriteAllSubclassInheritedAccesses(superclassNameObj, name, prop, namespace);
      }
    }

    if (subclassPropNameObj != null) {
      Set<AstChange> newNodes = new LinkedHashSet<>();

      // Use this node as a template for rewriteAliasProp.
      Node superclassNameNode = superclassNameObj.getDeclaration().node;
      if (superclassNameNode.isName()) {
        superclassNameNode = superclassNameNode.cloneNode();
      } else if (superclassNameNode.isGetProp()) {
        superclassNameNode = superclassNameNode.cloneTree();
      } else {
        return false;
      }

      rewriteAliasProp(superclassNameNode, 0, newNodes, subclassPropNameObj);
      namespace.scanNewNodes(newNodes);
    }
    return true;
  }

  /**
   * Returns true if the alias is possibly defined in the global scope, which we handle with more
   * caution than with locally scoped variables. May return false positives.
   *
   * @param alias An aliasing get.
   * @return If the alias is possibly defined in the global scope.
   */
  private boolean mayBeGlobalAlias(Ref alias) {
    // Note: alias.scope is the closest scope in which the aliasing assignment occurred.
    // So for "if (true) { var alias = aliasedVar; }", the alias.scope would be the IF block scope.
    if (alias.scope.isGlobal()) {
      return true;
    }
    // If the scope in which the alias is assigned is not global, look up the LHS of the assignment.
    Node aliasParent = alias.node.getParent();
    if (!aliasParent.isAssign() && !aliasParent.isName()) {
      // Only handle variable assignments and initializing declarations.
      return true;
    }
    Node aliasLhsNode = aliasParent.isName() ? aliasParent : aliasParent.getFirstChild();
    if (!aliasLhsNode.isName()) {
      // Only handle assignments to simple names, not qualified names or GETPROPs.
      return true;
    }
    String aliasVarName = aliasLhsNode.getString();
    Var aliasVar = alias.scope.getVar(aliasVarName);
    if (aliasVar != null) {
      return aliasVar.isGlobal();
    }
    return true;
  }

  /**
   * Attempts to inline a non-global alias of a global name.
   *
   * <p>It is assumed that the name for which it is an alias meets conditions (a) and (b).
   *
   * <p>The non-global alias is only inlinable if it is well-defined and assigned once, according to
   * the definitions in {@link ReferenceCollection}
   *
   * <p>If the aliasing name is completely removed, also deletes the aliasing Ref.
   *
   * @param name The global name being aliased
   * @param alias The aliasing reference to the name to remove
   */
  private void inlineAliasIfPossible(Name name, Ref alias, GlobalNamespace namespace) {
    // Ensure that the alias is assigned to a local variable at that
    // variable's declaration. If the alias's parent is a NAME,
    // then the NAME must be the child of a VAR, LET, or CONST node, and we must
    // be in a VAR, LET, or CONST assignment.
    // Otherwise if the parent is an assign, we are in a "a = alias" case.
    Node aliasParent = alias.node.getParent();
    if (aliasParent.isName() || aliasParent.isAssign()) {
      Node aliasLhsNode = aliasParent.isName() ? aliasParent : aliasParent.getFirstChild();
      String aliasVarName = aliasLhsNode.getString();

      Var aliasVar = alias.scope.getVar(aliasVarName);
      checkState(aliasVar != null, "Expected variable to be defined in scope", aliasVarName);
      ReferenceCollectingCallback collector =
          new ReferenceCollectingCallback(
              compiler,
              ReferenceCollectingCallback.DO_NOTHING_BEHAVIOR,
              new Es6SyntacticScopeCreator(compiler),
              Predicates.equalTo(aliasVar));
      Scope aliasScope = aliasVar.getScope();
      collector.processScope(aliasScope);

      ReferenceCollection aliasRefs = collector.getReferences(aliasVar);
      Set<AstChange> newNodes = new LinkedHashSet<>();

      if (aliasRefs.isWellDefined() && aliasRefs.isAssignedOnceInLifetime()) {
        // The alias is well-formed, so do the inlining now.
        int size = aliasRefs.references.size();
        // It's initialized on either the first or second reference.
        int firstRead = aliasRefs.references.get(0).isInitializingDeclaration() ? 1 : 2;
        for (int i = firstRead; i < size; i++) {
          Reference aliasRef = aliasRefs.references.get(i);
          newNodes.add(replaceAliasReference(alias, aliasRef));
        }

        // just set the original alias to null.
        replaceAliasAssignment(alias, aliasLhsNode);

        // Inlining the variable may have introduced new references
        // to descendants of {@code name}. So those need to be collected now.
        namespace.scanNewNodes(newNodes);
        name.removeRef(alias);
        return;
      }

      if (name.isConstructor()) {
        // TODO(lharker): the main reason this was added is because method decomposition inside
        // generators introduces some constructor aliases that weren't getting inlined.
        // If we find another (safer) way to avoid aliasing in method decomposition, consider
        // removing this.
        if (partiallyInlineAlias(alias, namespace, aliasRefs, aliasLhsNode)) {
          name.removeRef(alias);
        } else {
          // If we can't inline all alias references, make sure there are no unsafe property
          // accesses.
          if (referencesCollapsibleProperty(aliasRefs, name, namespace)) {
            compiler.report(JSError.make(aliasParent, UNSAFE_CTOR_ALIASING, aliasVarName));
          }
        }
      }
    }
  }

  /**
   * Inlines some references to an alias with its value. This handles cases where the alias is not
   * declared at initialization. It does nothing if the alias is reassigned after being initialized,
   * unless the reassignment occurs because of an enclosing function or a loop.
   *
   * @param alias An alias of some variable, which may not be well-defined.
   * @param namespace The GlobalNamespace, which will be updated with all new nodes created.
   * @param aliasRefs All references to the alias in its scope.
   * @param aliasLhsNode The lhs name of the alias when it is first initialized.
   * @return Whether all references to the alias were inlined
   */
  private boolean partiallyInlineAlias(
      Ref alias, GlobalNamespace namespace, ReferenceCollection aliasRefs, Node aliasLhsNode) {
    BasicBlock aliasBlock = null;
    // This initial iteration through all the alias references does two things:
    // a) Find the control flow block in which the alias is assigned.
    // b) See if the alias var is assigned to in multiple places, and return if that's the case.
    //    NOTE: we still may inline if the alias is assigned in a loop or inner function and that
    //    assignment statement is potentially executed multiple times.
    //    This is more aggressive than what "inlineAliasIfPossible" does.
    for (Reference aliasRef : aliasRefs) {
      Node aliasRefNode = aliasRef.getNode();
      if (aliasRefNode == aliasLhsNode) {
        aliasBlock = aliasRef.getBasicBlock();
        continue;
      } else if (aliasRef.isLvalue()) {
        // Don't replace any references if the alias is reassigned
        return false;
      }
    }

    Set<AstChange> newNodes = new LinkedHashSet<>();
    boolean alreadySeenInitialAlias = false;
    boolean foundNonReplaceableAlias = false;
    // Do a second iteration through all the alias references, and replace any inlinable references.
    for (Reference aliasRef : aliasRefs) {
      Node aliasRefNode = aliasRef.getNode();
      if (aliasRefNode == aliasLhsNode) {
        alreadySeenInitialAlias = true;
        continue;
      } else if (aliasRef.isDeclaration()) {
        // Ignore any alias declarations, e.g. "var alias;", since there's nothing to inline.
        continue;
      }

      BasicBlock refBlock = aliasRef.getBasicBlock();
      if ((refBlock != aliasBlock && aliasBlock.provablyExecutesBefore(refBlock))
          || (refBlock == aliasBlock && alreadySeenInitialAlias)) {
        // We replace the alias only if the alias and reference are in the same BasicBlock,
        // the aliasing assignment takes place before the reference, and the alias is
        // never reassigned.
        codeChanged = true;
        newNodes.add(replaceAliasReference(alias, aliasRef));
      } else {
        foundNonReplaceableAlias = true;
      }
    }

    // We removed all references to the alias, so remove the original aliasing assignment.
    if (!foundNonReplaceableAlias) {
      replaceAliasAssignment(alias, aliasLhsNode);
    }

    if (codeChanged) {
      // Inlining the variable may have introduced new references
      // to descendants of {@code name}. So those need to be collected now.
      namespace.scanNewNodes(newNodes);
    }
    return !foundNonReplaceableAlias;
  }

  /**
   * Replaces the rhs of an aliasing assignment with null, unless the assignment result is used in a
   * complex expression.
   */
  private void replaceAliasAssignment(Ref alias, Node aliasLhsNode) {
    // either VAR/CONST/LET or ASSIGN.
    Node assignment = aliasLhsNode.getParent();
    if (!NodeUtil.isNameDeclaration(assignment) && NodeUtil.isExpressionResultUsed(assignment)) {
      // e.g. don't change "if (alias = someVariable)" to "if (alias = null)"
      // TODO(lharker): instead replace the entire assignment with the RHS - "alias = x" becomes "x"
      return;
    }
    Node aliasParent = alias.node.getParent();
    aliasParent.replaceChild(alias.node, IR.nullNode());
    alias.name.removeRef(alias);
    codeChanged = true;
    compiler.reportChangeToEnclosingScope(aliasParent);
  }

  /**
   * Returns whether a ReferenceCollection for some aliasing variable references a property on the
   * original aliased variable that may be collapsed in CollapseProperties.
   *
   * <p>See {@link GlobalNamespace.Name#canCollapse} for what can/cannot be collapsed.
   */
  private boolean referencesCollapsibleProperty(
      ReferenceCollection aliasRefs, Name aliasedName, GlobalNamespace namespace) {
    for (Reference ref : aliasRefs.references) {
      if (ref.getParent() == null) {
        continue;
      }
      if (ref.getParent().isGetProp()) {
        Node propertyNode = ref.getNode().getNext();
        // e.g. if the reference is "alias.b.someProp", this will be "b".
        String propertyName = propertyNode.getString();
        // e.g. if the aliased name is "originalName", this will be "originalName.b".
        String originalPropertyName = aliasedName.getName() + "." + propertyName;
        Name originalProperty = namespace.getOwnSlot(originalPropertyName);
        // If the original property isn't in the namespace or can't be collapsed, keep going.
        if (originalProperty == null || !originalProperty.canCollapse()) {
          continue;
        }
        return true;
      }
    }
    return false;
  }

  /**
   * @param alias A GlobalNamespace.Ref of the variable being aliased
   * @param aliasRef One particular usage of an alias that we want to replace with the aliased var.
   * @return an AstChange representing the new node(s) added to the AST *
   */
  private AstChange replaceAliasReference(Ref alias, Reference aliasRef) {
    Node newNode = alias.node.cloneTree();
    aliasRef.getParent().replaceChild(aliasRef.getNode(), newNode);
    compiler.reportChangeToEnclosingScope(newNode);
    return new AstChange(getRefModule(aliasRef), aliasRef.getScope(), newNode);
  }

  /**
   * Attempt to inline an global alias of a global name. This requires that the name is well
   * defined: assigned unconditionally, assigned exactly once. It is assumed that, the name for
   * which it is an alias must already meet these same requirements.
   *
   * <p>If the alias is completely removed, also deletes the aliasing Ref.
   *
   * @param name The global name being aliased
   * @param alias The alias to inline
   */
  private void inlineGlobalAliasIfPossible(Name name, Ref alias, GlobalNamespace namespace) {
    // Ensure that the alias is assigned to global name at that the
    // declaration.
    Node aliasParent = alias.node.getParent();
    if (((aliasParent.isAssign() || aliasParent.isName())
            && NodeUtil.isExecutedExactlyOnce(aliasParent))
        // We special-case for constructors here, to inline constructor aliases
        // more aggressively in global scope.
        // We do this because constructor properties are always collapsed,
        // so we want to inline the aliases also to avoid breakages.
        || (aliasParent.isName() && name.isConstructor())) {
      Node lvalue = aliasParent.isName() ? aliasParent : aliasParent.getFirstChild();
      if (!lvalue.isQualifiedName()) {
        return;
      }
      if (lvalue.isName()
          && compiler.getCodingConvention().isExported(lvalue.getString(), /* local */ false)) {
        return;
      }
      Name aliasingName = namespace.getSlot(lvalue.getQualifiedName());

      if (aliasingName == null) {
        // this is true for names in externs or properties on extern names
        return;
      }

      if (name.equals(aliasingName) && aliasParent.isAssign()) {
        // Ignore `a.b.c = a.b.c;` with `a.b.c;`.
        return;
      }

      Inlinability aliasInlinability = aliasingName.calculateInlinability();
      if (!aliasInlinability.shouldInlineUsages()) {
        // nothing to do here
        return;
      }
      Set<AstChange> newNodes = new LinkedHashSet<>();

      // Rewrite all references to the aliasing name, except for the initialization
      rewriteAliasReferences(aliasingName, alias, newNodes);
      rewriteAliasProps(aliasingName, alias.node, 0, newNodes);

      if (aliasInlinability.shouldRemoveDeclaration()) {
        // Rewrite the initialization of the alias, unless this is an unsafe alias inline
        // caused by an @constructor. In that case, we need to leave the initialization around.
        Ref aliasDeclaration = aliasingName.getDeclaration();
        if (aliasDeclaration.getTwin() != null) {
          // This is in a nested assign.
          // Replace
          //   a.b = aliasing.name = aliased.name
          // with
          //   a.b = aliased.name
          checkState(aliasParent.isAssign(), aliasParent);
          Node aliasGrandparent = aliasParent.getParent();
          aliasParent.replaceWith(alias.node.detach());
          aliasingName.removeRef(aliasDeclaration);
          aliasingName.removeRef(aliasDeclaration.getTwin());
          compiler.reportChangeToEnclosingScope(aliasGrandparent);
        } else {
          // just set the original alias to null.
          aliasParent.replaceChild(alias.node, IR.nullNode());
          compiler.reportChangeToEnclosingScope(aliasParent);
        }
        codeChanged = true;
        // Update the original aliased name to say that it has one less ALIASING_REF.
        name.removeRef(alias);
      }

      // Inlining the variable may have introduced new references
      // to descendants of {@code name}. So those need to be collected now.
      namespace.scanNewNodes(newNodes);
    }
  }

  /** Replaces all reads of a name with the name it aliases */
  private void rewriteAliasReferences(Name aliasingName, Ref aliasingRef, Set<AstChange> newNodes) {
    List<Ref> refs = new ArrayList<>(aliasingName.getRefs());
    for (Ref ref : refs) {
      switch (ref.type) {
        case SET_FROM_GLOBAL:
          continue;
        case DIRECT_GET:
        case ALIASING_GET:
        case PROTOTYPE_GET:
        case CALL_GET:
          if (ref.getTwin() != null) {
            // The reference is the left-hand side of a nested assignment. This means we store two
            // separate 'twin' Refs with the same node of types ALIASING_GET and SET_FROM_GLOBAL.
            // For example, the read of `c.d` has a twin reference in
            //   a.b = c.d = e.f;
            // We handle this case later.
            checkState(ref.type == Type.ALIASING_GET, ref);
            break;
          }
          Node newNode = aliasingRef.node.cloneTree();
          Node node = ref.node;
          node.getParent().replaceChild(node, newNode);
          compiler.reportChangeToEnclosingScope(newNode);
          newNodes.add(new AstChange(ref.module, ref.scope, newNode));
          aliasingName.removeRef(ref);
          break;
        default:
          throw new IllegalStateException();
      }
    }
  }

  /** Check if the name has multiple sets that are not of the form "a = a || {}" */
  private static boolean isUnsafelyReassigned(Name name) {
    boolean foundOriginalDefinition = false;
    for (Ref ref : name.getRefs()) {
      if (!ref.isSet()) {
        continue;
      }
      if (CollapseProperties.isSafeNamespaceReinit(ref)) {
        continue;
      }
      if (!foundOriginalDefinition) {
        foundOriginalDefinition = true;
      } else {
        return true;
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
  private void rewriteAliasProps(Name name, Node value, int depth, Set<AstChange> newNodes) {
    if (name.props == null) {
      return;
    }
    Preconditions.checkState(
        !value.matchesQualifiedName(name.getFullName()),
        "%s should not match name %s",
        value,
        name.getFullName());
    for (Name prop : name.props) {
      rewriteAliasProp(value, depth, newNodes, prop);
    }
  }

  /**
   * @param value The value to use when rewriting.
   * @param depth The chain depth.
   * @param newNodes Expression nodes that have been updated.
   * @param prop The property to rewrite with value.
   */
  private void rewriteAliasProp(Node value, int depth, Set<AstChange> newNodes, Name prop) {
    rewriteAliasProps(prop, value, depth + 1, newNodes);
    List<Ref> refs = new ArrayList<>(prop.getRefs());
    for (Ref ref : refs) {
      Node target = ref.node;
      for (int i = 0; i <= depth; i++) {
        if (target.isGetProp()) {
          target = target.getFirstChild();
        } else if (NodeUtil.isObjectLitKey(target)) {
          // Object literal key definitions are a little trickier, as we
          // need to find the assignment target
          Node gparent = target.getGrandparent();
          if (gparent.isAssign()) {
            target = gparent.getFirstChild();
          } else {
            checkState(NodeUtil.isObjectLitKey(gparent));
            target = gparent;
          }
        } else {
          throw new IllegalStateException("unexpected: " + target);
        }
      }
      checkState(target.isGetProp() || target.isName());
      Node newValue = value.cloneTree();
      target.replaceWith(newValue);
      compiler.reportChangeToEnclosingScope(newValue);
      prop.removeRef(ref);
      // Rescan the expression root.
      newNodes.add(new AstChange(ref.module, ref.scope, ref.node));
      codeChanged = true;
    }
  }
}
