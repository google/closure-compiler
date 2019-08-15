/*
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Bob Jervis
 *   Google Inc.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */
package com.google.javascript.rhino.jstype;

import com.google.javascript.rhino.jstype.ContainsUpperBoundSuperTypeVisitor.Result;
import java.util.AbstractMap;
import java.util.IdentityHashMap;
import javax.annotation.Nullable;

/**
 * A type visitor that traverse through the referenced types of {@link ProxyObjectType} and the
 * alternate types of {@link UnionType} to search for a target type. If the target type is found,
 * a Result object FOUND is returned. If it is not found, a Result of NOT_FOUND is returned.
 * If a cycle is found before the target type is found, a Result containing the cycle-forming type
 * encountered in the reference chain is returned.
 *
 * This is used by {@link TemplateType} as a helper in cycle detection and subtyping. Cycles are
 * checked for once after TemplateTypes are constructed in FunctionTypeBuilder and an error is
 * emitted if they are found.
 *
 * @author liuamanda@google.com (Amanda Liu)
 */
class ContainsUpperBoundSuperTypeVisitor extends AbstractDefaultValueVisitor<Result> {
  private final JSType target;
  // NOTE: An IdentityHashMap is used to keep track of seen ProxyObjectTypes since these types
  // normally toss the hashing to their referenced type. This is used instead to maintain uniqueness
  // of inserted types and avoid false positives in cycle detection.
  private final AbstractMap<ProxyObjectType, Void> seen;

  public ContainsUpperBoundSuperTypeVisitor(JSType target) {
    super(NOT_FOUND);
    this.target = target;
    this.seen = new IdentityHashMap<>();
  }

  @Override
  public Result caseTemplateType(TemplateType type) {
    return caseProxyObjectTypeHelper(type, type.getBound());
  }

  @Override
  public Result caseNamedType(NamedType type) {
    return caseProxyObjectTypeHelper(type, type.getReferencedType());
  }

  @Override
  public Result caseTemplatizedType(TemplatizedType type) {
    return caseProxyObjectTypeHelper(type, type.getReferencedType());
  }

  @Override
  public Result caseUnionType(UnionType type) {
    if (JSType.areIdentical(type, target)) {
      return FOUND;
    } else {
      for (JSType alt : type.getAlternates()) {
        Result foundInAlt = alt.visit(this);
        if (foundInAlt != NOT_FOUND) {
          return foundInAlt;
        }
      }
      return NOT_FOUND;
    }
  }

  private Result caseProxyObjectTypeHelper(ProxyObjectType type, JSType reference) {
    if (JSType.areIdentical(type, target)) {
      return FOUND;
    } else if (seen.containsKey(type)) {
      // A cycle has been detected
      return cycle(type);
    } else {
      // We only care about uniqueness of keys, so store a dummy value
      seen.put(type, null);
      return reference.visit(this);
    }
  }

  /**
   * This class represents all possible resulting states of a traversal of
   * the ContainsUpperBoundSuperTypeVisitor.
   *
   * The result of a traversal of this visitor may either find the target type,
   * not find the target type, or encounter a cycle.
   */
  static class Result {
    boolean foundSupertype;
    @Nullable JSType cycle;

    Result(boolean foundSupertype, JSType cycle) {
      this.foundSupertype = foundSupertype;
      this.cycle = cycle;
    }
  }

  static final Result FOUND = new Result(true, null);
  static final Result NOT_FOUND = new Result(false, null);

  static Result cycle(JSType type) {
    return new Result(false, type);
  }
}
