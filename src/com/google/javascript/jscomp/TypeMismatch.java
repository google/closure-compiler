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


import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Signals that the first type and the second type have been used interchangeably.
 *
 * <p>Type-based optimizations should take this into account so that they don't wreck code with type
 * warnings.
 */
@AutoValue
public abstract class TypeMismatch implements Serializable {
  /** The RHS type; the type of the assignment target. */
  public abstract JSType getFound();

  /** The LHS type; the type being assigned. */
  public abstract JSType getRequired();

  /** The location of the assignment. */
  public abstract Node getLocation();

  private static TypeMismatch create(JSType found, JSType required, Node location) {
    return new AutoValue_TypeMismatch(found, required, location);
  }

  @VisibleForTesting
  public static TypeMismatch createForTesting(JSType found, JSType required) {
    return create(found, required, TEST_LOCATION);
  }

  private static final Node TEST_LOCATION = IR.empty();

  /** Collects a set of related mismatches. */
  static class Accumulator implements Serializable {

    private final ArrayList<TypeMismatch> mismatches = new ArrayList<>();

    void registerMismatch(Node location, JSType found, JSType required) {
      // Don't register a mismatch for differences in null or undefined or if the
      // code didn't downcast.
      found = removeNullUndefinedAndTemplates(found);
      required = removeNullUndefinedAndTemplates(required);
      if (found.isSubtypeOf(required) || required.isSubtypeOf(found)) {
        return;
      }
      if (required.isUnionType()) {
        for (JSType requiredAltType : required.toMaybeUnionType().getAlternates()) {
          if (requiredAltType.isSubtypeOf(found)) {
            return;
          }
        }
      }

      if (bothAreNotTemplateTypes(found, required)) {
        this.mismatches.add(TypeMismatch.create(found, required, location));
      }

      if (found.isFunctionType() && required.isFunctionType()) {
        FunctionType fnTypeA = found.toMaybeFunctionType();
        FunctionType fnTypeB = required.toMaybeFunctionType();
        Iterator<FunctionType.Parameter> paramItA = fnTypeA.getParameters().iterator();
        Iterator<FunctionType.Parameter> paramItB = fnTypeB.getParameters().iterator();
        while (paramItA.hasNext() && paramItB.hasNext()) {
          this.registerIfMismatch(
              location, paramItA.next().getJSType(), paramItB.next().getJSType());
        }
        this.registerIfMismatch(location, fnTypeA.getReturnType(), fnTypeB.getReturnType());
      }
    }

    ImmutableCollection<TypeMismatch> getMismatches() {
      return ImmutableList.copyOf(this.mismatches);
    }

    private void registerIfMismatch(Node location, JSType found, JSType required) {
      if (found != null && required != null && !found.isSubtypeOf(required)) {
        this.registerMismatch(location, found, required);
      }
    }

    /**
     * A type variable is considered unknown, so other types can be used as type variables, and vice
     * versa, without warning. Otherwise, to get good disambiguation, we would have to add casts all
     * over the code base.
     *
     * <p>TODO(dimvar): this can be made safe in the distant future where we have bounded generics
     * *and* we have switched all the unsafe uses of type variables in the code base to use bounded
     * generics.
     */
    private static boolean bothAreNotTemplateTypes(JSType found, JSType required) {
      return !found.isTemplateType() && !required.isTemplateType();
    }

    private static JSType removeNullUndefinedAndTemplates(JSType t) {
      JSType result = t.restrictByNotNullOrUndefined();
      ObjectType obj = result.toMaybeObjectType();
      if (obj != null && obj.isTemplatizedType()) {
        // We don't care about the specific specalization involved in the mismatch because all
        // specializations share the same JS code.
        return obj.toMaybeTemplatizedType().getRawType();
      }
      return result;
    }
  }
}
