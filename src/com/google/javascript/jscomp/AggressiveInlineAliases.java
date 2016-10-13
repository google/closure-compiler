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

import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.javascript.jscomp.GlobalNamespace.AstChange;
import com.google.javascript.jscomp.GlobalNamespace.Name;
import com.google.javascript.jscomp.GlobalNamespace.Ref;
import com.google.javascript.jscomp.GlobalNamespace.Ref.Type;
import com.google.javascript.jscomp.ReferenceCollectingCallback.Reference;
import com.google.javascript.jscomp.ReferenceCollectingCallback.ReferenceCollection;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Inlines type aliases if they are explicitly or effectively const.
 *
 * <p>This frees subsequent optimization passes from the responsibility of having to reason about
 * alias chains and is a requirement for correct behavior in at least CollapseProperties and
 * J2clPropertyInlinerPass.
 */
class AggressiveInlineAliases implements CompilerPass {

  static final DiagnosticType UNSAFE_CTOR_ALIASING =
      DiagnosticType.warning(
          "JSC_UNSAFE_CTOR_ALIASING",
          "Variable {0} aliases a constructor, " + "so it cannot be assigned multiple times");

  /**
   * @param name The Name whose properties references should be updated.
   * @param value The value to use when rewriting.
   * @param depth The chain depth.
   * @param newNodes Expression nodes that have been updated.
   */
  private static void rewriteAliasProps(Name name, Node value, int depth, Set<AstChange> newNodes) {
    if (name.props == null) {
      return;
    }
    Preconditions.checkState(
        !value.matchesQualifiedName(name.getFullName()),
        "%s should not match name %s",
        value,
        name.getFullName());
    for (Name prop : name.props) {
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
              Preconditions.checkState(NodeUtil.isObjectLitKey(gparent));
              target = gparent;
            }
          } else {
            throw new IllegalStateException("unexpected: " + target);
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

  private AbstractCompiler compiler;

  AggressiveInlineAliases(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    GlobalNamespace namespace = new GlobalNamespace(compiler, root);
    inlineAliases(namespace);
  }

  private JSModule getRefModule(ReferenceCollectingCallback.Reference ref) {
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
   * @see InlineVariables
   */
  private void inlineAliases(GlobalNamespace namespace) {
    // Invariant: All the names in the worklist meet condition (a).
    Deque<Name> workList = new ArrayDeque<>(namespace.getNameForest());

    while (!workList.isEmpty()) {
      Name name = workList.pop();

      // Don't attempt to inline a getter or setter property as a variable.
      if (name.type == Name.Type.GET || name.type == Name.Type.SET) {
        continue;
      }

      if (!name.inExterns && name.globalSets == 1 && name.localSets == 0 && name.aliasingGets > 0) {
        // {@code name} meets condition (b). Find all of its local aliases
        // and try to inline them.
        List<Ref> refs = new ArrayList<>(name.getRefs());
        for (Ref ref : refs) {
          if (ref.type == Type.ALIASING_GET && ref.scope.isLocal()) {
            // {@code name} meets condition (c). Try to inline it.
            // TODO(johnlenz): consider picking up new aliases at the end
            // of the pass instead of immediately like we do for global
            // inlines.
            if (inlineAliasIfPossible(name, ref, namespace)) {
              name.removeRef(ref);
            }
          } else if (ref.type == Type.ALIASING_GET
              && ref.scope.isGlobal()
              && ref.getTwin() == null) { // ignore aliases in chained assignments
            if (inlineGlobalAliasIfPossible(name, ref, namespace)) {
              name.removeRef(ref);
            }
          }
        }
      }

      // Check if {@code name} has any aliases left after the
      // local-alias-inlining above.
      if ((name.type == Name.Type.OBJECTLIT || name.type == Name.Type.FUNCTION)
          && name.aliasingGets == 0
          && name.props != null) {
        // All of {@code name}'s children meet condition (a), so they can be
        // added to the worklist.
        workList.addAll(name.props);
      }
    }
  }

  private boolean inlineAliasIfPossible(Name name, Ref alias, GlobalNamespace namespace) {
    // Ensure that the alias is assigned to a local variable at that
    // variable's declaration. If the alias's parent is a NAME,
    // then the NAME must be the child of a VAR node, and we must
    // be in a VAR assignment.
    Node aliasParent = alias.node.getParent();
    if (aliasParent.isName()) {
      // Ensure that the local variable is well defined and never reassigned.
      Scope scope = alias.scope;
      String aliasVarName = aliasParent.getString();
      Var aliasVar = scope.getVar(aliasVarName);

      ReferenceCollectingCallback collector =
          new ReferenceCollectingCallback(
              compiler,
              ReferenceCollectingCallback.DO_NOTHING_BEHAVIOR,
              Predicates.equalTo(aliasVar));
      collector.processScope(scope);

      ReferenceCollection aliasRefs = collector.getReferences(aliasVar);
      Set<AstChange> newNodes = new LinkedHashSet<>();

      if (aliasRefs.isWellDefined() && aliasRefs.firstReferenceIsAssigningDeclaration()) {
        if (!aliasRefs.isAssignedOnceInLifetime()) {
          // Static properties of constructors are always collapsed.
          // So, if a constructor is aliased and its properties are accessed from
          // the alias, we would like to inline the alias here to access the
          // properties correctly.
          // But if the aliased variable is assigned more than once, we can't
          // inline, so we warn.
          if (name.isConstructor()) {
            boolean accessPropsAfterAliasing = false;
            for (Reference ref : aliasRefs.references) {
              if (ref.getNode().getParent().isGetProp()) {
                accessPropsAfterAliasing = true;
                break;
              }
            }
            if (accessPropsAfterAliasing) {
              compiler.report(JSError.make(aliasParent, UNSAFE_CTOR_ALIASING, aliasVarName));
            }
          }
          return false;
        }

        // The alias is well-formed, so do the inlining now.
        int size = aliasRefs.references.size();
        for (int i = 1; i < size; i++) {
          ReferenceCollectingCallback.Reference aliasRef = aliasRefs.references.get(i);

          Node newNode = alias.node.cloneTree();
          aliasRef.getParent().replaceChild(aliasRef.getNode(), newNode);
          newNodes.add(new AstChange(getRefModule(aliasRef), aliasRef.getScope(), newNode));
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

  /**
   * Attempt to inline an global alias of a global name. This requires that the name is well
   * defined: assigned unconditionally, assigned exactly once. It is assumed that, the name for
   * which it is an alias must already meet these same requirements.
   *
   * @param alias The alias to inline
   * @return Whether the alias was inlined.
   */
  private boolean inlineGlobalAliasIfPossible(Name name, Ref alias, GlobalNamespace namespace) {
    // Ensure that the alias is assigned to global name at that the
    // declaration.
    Node aliasParent = alias.node.getParent();
    if ((aliasParent.isAssign() || aliasParent.isName())
            && NodeUtil.isExecutedExactlyOnce(aliasParent)
        // We special-case for constructors here, to inline constructor aliases
        // more aggressively in global scope.
        // We do this because constructor properties are always collapsed,
        // so we want to inline the aliases also to avoid breakages.
        // TODO(tbreisacher): Do we still need this special case?
        || aliasParent.isName() && name.isConstructor()) {
      Node lvalue = aliasParent.isName() ? aliasParent : aliasParent.getFirstChild();
      if (!lvalue.isQualifiedName()) {
        return false;
      }
      if (lvalue.isName()
          && compiler.getCodingConvention().isExported(lvalue.getString(), /* local */ false)) {
        return false;
      }
      name = namespace.getSlot(lvalue.getQualifiedName());
      if (name != null && name.isInlinableGlobalAlias()) {
        Set<AstChange> newNodes = new LinkedHashSet<>();

        List<Ref> refs = new ArrayList<>(name.getRefs());
        for (Ref ref : refs) {
          switch (ref.type) {
            case SET_FROM_GLOBAL:
              continue;
            case DIRECT_GET:
            case ALIASING_GET:
            case PROTOTYPE_GET:
            case CALL_GET:
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
    return false;
  }
}
