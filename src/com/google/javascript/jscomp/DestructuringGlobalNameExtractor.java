/*
 * Copyright 2018 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.GlobalNamespace.AstChange;
import com.google.javascript.jscomp.GlobalNamespace.Ref;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Helper for changing the value of an lvalue in a destructuring pattern. Intended for use by {@link
 * InlineAndCollapseProperties.CollapseProperties} and {@link
 * InlineAndCollapseProperties.AggressiveInlineAliases} only. This class makes some assumptions that
 * don't generally hold in order to preserve {@link GlobalNamespace} validity and avoid creating
 * temporary variables.
 */
class DestructuringGlobalNameExtractor {
  /**
   * Given an lvalue in a destructuring pattern, and a detached subtree, rewrites the AST to assign
   * the lvalue to the subtree instead of its previous value, while preserving the rest of the
   * destructuring pattern
   *
   * <p>For example: given stringKey: 'y' and newName: 'new.name', where 'y' is contained in the
   * declaration `const {x, y, z} = original;`, this method will produce `const {x} = original;
   * const y = new.name; const {z} = original;`
   *
   * <p>This method only handles a limited subset of destructuring patterns and is intended only for
   * CollapseProperties and AggressiveInlineAliases. Preconditions are:
   *
   * <ul>
   *   <li>the pattern must be the lhs of an assignment or declaration (e.g. not a nested pattern)
   *   <li>the original rvalue for the pattern must be an effectively constant qualified name. it is
   *       safe to evaluate the rvalue multiple times.
   * </ul>
   *
   * @param stringKey the STRING_KEY node representing the ref to rewrite, e.g. `y`
   * @param newName what the lvalue in the STRING_KEY should now be assigned to, e.g. foo.bar
   * @param newNodes optionally a set to add new AstChanges to, if you need to keep the
   *     GlobalNamespace up-to-date. Otherwise null.
   * @param ref the Ref corresponding to the `stringKey`
   */
  static void reassignDestructringLvalue(
      Node stringKey,
      Node newName,
      @Nullable Set<AstChange> newNodes,
      Ref ref,
      AbstractCompiler compiler) {
    Node pattern = stringKey.getParent();
    Node assignmentType = pattern.getParent();
    checkState(assignmentType.isAssign() || assignmentType.isDestructuringLhs(), assignmentType);
    Node originalRvalue = pattern.getNext(); // e.g. `original`
    checkState(originalRvalue.isQualifiedName()); // don't handle rvalues with side effects

    // Create a new assignment using the provided qualified name, e.g. `const y = new.name;`
    Node lvalueToReassign =
        stringKey.getOnlyChild().isDefaultValue()
            ? stringKey.getOnlyChild().getFirstChild()
            : stringKey.getOnlyChild();
    if (newNodes != null) {
      newNodes.add(new AstChange(ref.scope, newName));
    }
    Node rvalue = makeNewRvalueForDestructuringKey(stringKey, newName, newNodes, ref);

    // Add that new assignment to the AST after the original destructuring pattern
    if (stringKey.getPrevious() == null && stringKey.getNext() != null) {
      // Remove the original item completely if the given string key doesn't have any preceding
      // string keys, /and/ it has succeeding string keys.
      replaceDestructuringAssignment(pattern, lvalueToReassign.detach(), rvalue);
    } else {
      addAfter(pattern, lvalueToReassign.detach(), rvalue);
    }

    // Remove any lvalues in the pattern that are after the given stringKey, and put them in
    // a second destructuring pattern. This is necessary in case they depend on side effects from
    // assigning `lvalueToReassign`. e.g. create `const {z} = original;`
    if (stringKey.getNext() != null) {
      Node newPattern = createNewObjectPatternFromSuccessiveKeys(stringKey).srcref(pattern);

      // reuse the original rvalue if we don't need it for earlier keys, otherwise make a copy.
      final Node newRvalue;
      if (stringKey.getPrevious() == null) {
        newRvalue = originalRvalue.detach();
      } else {
        newRvalue = originalRvalue.cloneTree();
        if (newNodes != null) {
          newNodes.add(new AstChange(ref.scope, newRvalue));
        }
      }
      addAfter(lvalueToReassign, newPattern, newRvalue);
    }

    stringKey.detach();
    compiler.reportChangeToEnclosingScope(lvalueToReassign);
  }

  /** Adds the new assign or name declaration after the original assign or name declaration */
  private static void addAfter(Node originalLvalue, Node newLvalue, Node newRvalue) {
    Node parent = originalLvalue.getParent();
    if (parent.isAssign()) {
      // create `(<originalLvalue = ...>, <newLvalue = newRvalue>)`
      Node newAssign = IR.assign(newLvalue, newRvalue).srcref(parent);
      Node newComma = new Node(Token.COMMA, newAssign);
      parent.replaceWith(newComma);
      newComma.addChildToFront(parent);
      return;
    }
    // This must have been in a var/let/const.
    if (newLvalue.isDestructuringPattern()) {
      newLvalue = new Node(Token.DESTRUCTURING_LHS, newLvalue, newRvalue).srcref(parent);
    } else {
      newLvalue.addChildToBack(newRvalue);
    }
    Node declaration = parent.isDestructuringLhs() ? originalLvalue.getGrandparent() : parent;
    checkState(NodeUtil.isNameDeclaration(declaration), declaration);
    if (NodeUtil.isStatementParent(declaration.getParent())) {
      // `const {} = originalRvalue; const newLvalue = newRvalue;`
      // create an entirely new statement
      Node newDeclaration = new Node(declaration.getToken()).srcref(declaration);
      newDeclaration.addChildToBack(newLvalue);
      newDeclaration.insertAfter(declaration);
    } else {
      // `const {} = originalRvalue, newLvalue = newRvalue;`
      // The Normalize pass tries to ensure name declarations are always in statement blocks, but
      // currently has not implemented normalization for `for (let x = 0; ...`
      // so we can't add a new statement
      declaration.addChildToBack(newLvalue);
    }
  }

  /**
   * Replaces the given assignment or declaration with the new lvalue/rvalue
   *
   * @param pattern a destructuring pattern in an ASSIGN or VAR/LET/CONST
   */
  private static void replaceDestructuringAssignment(Node pattern, Node newLvalue, Node newRvalue) {
    Node parent = pattern.getParent();
    if (parent.isAssign()) {
      Node newAssign = IR.assign(newLvalue, newRvalue).srcref(parent);
      parent.replaceWith(newAssign);
    } else if (newLvalue.isName()) {
      checkState(parent.isDestructuringLhs());
      parent.replaceWith(newLvalue);
      newLvalue.addChildToBack(newRvalue);
    } else {
      pattern.getNext().detach();
      pattern.detach();
      parent.addChildToBack(newLvalue);
      parent.addChildToBack(newRvalue);
    }
  }

  /**
   * Makes a default value expression from the rvalue, or otherwise just returns it
   *
   * <p>e.g. for `const {x = defaultValue} = y;`, and the new rvalue `rvalue`, returns `void 0 ===
   * rvalue ? defaultValue : rvalue`
   */
  private static Node makeNewRvalueForDestructuringKey(
      Node stringKey, Node rvalue, Set<AstChange> newNodes, Ref ref) {
    if (stringKey.getOnlyChild().isDefaultValue()) {
      Node defaultValue = stringKey.getFirstChild().getSecondChild().detach();
      // Assume `rvalue` has no side effects since it's a qname, and we can create multiple
      // references to it. This ignores getters/setters.
      Node rvalueForSheq = rvalue.cloneTree();
      if (newNodes != null) {
        newNodes.add(new AstChange(ref.scope, rvalueForSheq));
      }
      // `void 0 === rvalue ? defaultValue : rvalue`
      rvalue =
          IR.hook(IR.sheq(NodeUtil.newUndefinedNode(rvalue), rvalueForSheq), defaultValue, rvalue)
              .srcrefTree(defaultValue);
    }
    return rvalue;
  }

  /** Removes any keys after the given key, and adds them in order to a new object pattern */
  private static Node createNewObjectPatternFromSuccessiveKeys(Node stringKey) {
    Node newPattern = stringKey.getParent().cloneNode(); // copies the JSType
    for (Node next = stringKey.getNext(); next != null; ) {
      Node newKey = next;
      next = newKey.getNext();
      newPattern.addChildToBack(newKey.detach());
    }
    return newPattern;
  }
}
