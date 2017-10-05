/*
 * Copyright 2017 The Closure Compiler Authors.
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

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkState;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A collection of references. Can be subclassed to apply checks or store additional state when
 * adding.
 */
public final class ReferenceCollection implements Iterable<Reference>, Serializable {

  List<Reference> references = new ArrayList<>();

  @Override
  public Iterator<Reference> iterator() {
    return references.iterator();
  }

  void add(Reference reference) {
    references.add(reference);
  }

  /**
   * Determines if the variable for this reference collection is "well-defined." A variable is
   * well-defined if we can prove at compile-time that it's assigned a value before it's used.
   *
   * <p>Notice that if this function returns false, this doesn't imply that the variable is used
   * before it's assigned. It just means that we don't have enough information to make a definitive
   * judgment.
   */
  protected boolean isWellDefined() {
    int size = references.size();
    if (size == 0) {
      return false;
    }

    // If this is a declaration that does not instantiate the variable,
    // it's not well-defined.
    Reference init = getInitializingReference();
    if (init == null) {
      return false;
    }

    checkState(references.get(0).isDeclaration());
    BasicBlock initBlock = init.getBasicBlock();
    for (int i = 1; i < size; i++) {
      if (!initBlock.provablyExecutesBefore(references.get(i).getBasicBlock())) {
        return false;
      }
    }

    return true;
  }

  /** Whether the variable is escaped into an inner function. */
  boolean isEscaped() {
    Scope hoistScope = null;
    for (Reference ref : references) {
      if (hoistScope == null) {
        hoistScope = ref.getScope().getClosestHoistScope();
      } else if (hoistScope != ref.getScope().getClosestHoistScope()) {
        return true;
      }
    }
    return false;
  }

  /**
   * @param index The index into the references array to look for an assigning declaration.
   *     <p>This is either the declaration if a value is assigned (such as "var a = 2", "function
   *     a()...", "... catch (a)...").
   */
  private boolean isInitializingDeclarationAt(int index) {
    Reference maybeInit = references.get(index);
    if (maybeInit.isInitializingDeclaration()) {
      // This is a declaration that represents the initial value.
      // Specifically, var declarations without assignments such as "var a;"
      // are not.
      return true;
    }
    return false;
  }

  /**
   * @param index The index into the references array to look for an initialized assignment
   *     reference. That is, an assignment immediately follow a variable declaration that itself
   *     does not initialize the variable.
   */
  private boolean isInitializingAssignmentAt(int index) {
    if (index < references.size() && index > 0) {
      Reference maybeDecl = references.get(index - 1);
      if (maybeDecl.isVarDeclaration() || maybeDecl.isLetDeclaration()) {
        checkState(!maybeDecl.isInitializingDeclaration());
        Reference maybeInit = references.get(index);
        if (maybeInit.isSimpleAssignmentToName()) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * @return The reference that provides the value for the variable at the time of the first read,
   *     if known, otherwise null.
   *     <p>This is either the variable declaration ("var a = ...") or first reference following the
   *     declaration if it is an assignment.
   */
  Reference getInitializingReference() {
    if (isInitializingDeclarationAt(0)) {
      return references.get(0);
    } else if (isInitializingAssignmentAt(1)) {
      return references.get(1);
    }
    return null;
  }

  /** Constants are allowed to be defined after their first use. */
  Reference getInitializingReferenceForConstants() {
    int size = references.size();
    for (int i = 0; i < size; i++) {
      if (isInitializingDeclarationAt(i) || isInitializingAssignmentAt(i)) {
        return references.get(i);
      }
    }
    return null;
  }

  /** @return Whether the variable is only assigned a value once for its lifetime. */
  boolean isAssignedOnceInLifetime() {
    Reference ref = getOneAndOnlyAssignment();
    if (ref == null) {
      return false;
    }

    // Make sure this assignment is not in a loop or an enclosing function.
    for (BasicBlock block = ref.getBasicBlock(); block != null; block = block.getParent()) {
      if (block.isFunction()) {
        if (ref.getSymbol().getScope().getClosestHoistScope()
            != ref.getScope().getClosestHoistScope()) {
          return false;
        }
        break;
      } else if (block.isLoop()) {
        return false;
      }
    }

    return true;
  }

  /**
   * @return The one and only assignment. Returns null if the number of assignments is not exactly
   *     one.
   */
  @Nullable
  Reference getOneAndOnlyAssignment() {
    Reference assignment = null;
    int size = references.size();
    for (int i = 0; i < size; i++) {
      Reference ref = references.get(i);
      if (ref.isLvalue() || ref.isInitializingDeclaration()) {
        if (assignment == null) {
          assignment = ref;
        } else {
          return null;
        }
      }
    }
    return assignment;
  }

  /** @return Whether the variable is never assigned a value. */
  boolean isNeverAssigned() {
    int size = references.size();
    for (int i = 0; i < size; i++) {
      Reference ref = references.get(i);
      if (ref.isLvalue() || ref.isInitializingDeclaration()) {
        return false;
      }
    }
    return true;
  }

  boolean firstReferenceIsAssigningDeclaration() {
    int size = references.size();
    return size > 0 && references.get(0).isInitializingDeclaration();
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("initRef", getInitializingReference())
        .add("references", references)
        .add("wellDefined", isWellDefined())
        .add("assignedOnce", isAssignedOnceInLifetime())
        .toString();
  }
}
