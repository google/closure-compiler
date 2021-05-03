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

import static com.google.javascript.jscomp.base.JSCompObjects.identical;

import com.google.common.collect.Sets;
import com.google.javascript.rhino.jstype.ContainsUpperBoundSuperTypeVisitor.Result;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A type visitor that traverse through the referenced types of "forwaring types" to search for a
 * target type.
 *
 * <p>The result of the visitation indicates whether the target type was present, absent, or
 * undcidable due to a cycle.
 *
 * <p>This is used by {@link TemplateType} as a helper in cycle detection and subtyping. Cycles are
 * checked for once after TemplateTypes are constructed in FunctionTypeBuilder and an error is
 * emitted if they are found.
 */
final class ContainsUpperBoundSuperTypeVisitor extends Visitor.WithDefaultCase<Result> {

  private final JSType target;
  private final Set<JSType> seen = Sets.newIdentityHashSet();

  public ContainsUpperBoundSuperTypeVisitor(JSType target) {
    this.target = target;
  }

  @Override
  protected Result caseDefault(@Nullable JSType type) {
    if (this.target == null) {
      return Result.ABSENT;
    }

    return identical(type, this.target) ? Result.PRESENT : Result.ABSENT;
  }

  @Override
  public Result caseTemplateType(TemplateType type) {
    return caseForwardingType(type, type.getBound());
  }

  @Override
  public Result caseNamedType(NamedType type) {
    return caseForwardingType(type, type.getReferencedType());
  }

  @Override
  public Result caseTemplatizedType(TemplatizedType type) {
    return caseForwardingType(type, type.getReferencedType());
  }

  @Override
  public Result caseUnionType(UnionType type) {
    if (identical(type, target)) {
      return Result.PRESENT;
    }

    for (JSType alt : type.getAlternates()) {
      Result foundInAlt = alt.visit(this);
      if (foundInAlt != Result.ABSENT) {
        return foundInAlt;
      }
    }

    return Result.ABSENT;
  }

  private Result caseForwardingType(JSType type, JSType reference) {
    if (identical(type, target)) {
      return Result.PRESENT;
    } else if (seen.contains(type)) {
      return Result.CYCLE;
    } else {
      seen.add(type);
      return reference.visit(this);
    }
  }

  /** Represents the outcome of a visitation of the {@link ContainsUpperBoundSuperTypeVisitor}. */
  enum Result {
    PRESENT,
    ABSENT,
    /** Containment is undecidable due to a reference cycle. */
    CYCLE;
  }
}
