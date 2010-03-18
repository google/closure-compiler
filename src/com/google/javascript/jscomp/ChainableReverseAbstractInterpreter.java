/*
 * Copyright 2007 Google Inc.
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

import static com.google.javascript.rhino.jstype.JSTypeNative.ALL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NO_OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NO_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NULL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.U2U_CONSTRUCTOR_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.VOID_TYPE;

import com.google.javascript.rhino.jstype.EnumElementType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticSlot;
import com.google.javascript.rhino.jstype.UnionType;
import com.google.javascript.rhino.jstype.Visitor;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Chainable reverse abstract interpreter providing basic functionality.
 *
*
 */
abstract class ChainableReverseAbstractInterpreter
    implements ReverseAbstractInterpreter {
  protected final CodingConvention convention;
  final JSTypeRegistry typeRegistry;
  private ChainableReverseAbstractInterpreter firstLink;
  private ChainableReverseAbstractInterpreter nextLink;

  /**
   * Constructs an interpreter, which is the only link in a chain. Interpreters
   * can be appended using {@link #append}.
   */
  ChainableReverseAbstractInterpreter(CodingConvention convention,
      JSTypeRegistry typeRegistry) {
    Preconditions.checkNotNull(convention);
    this.convention = convention;
    this.typeRegistry = typeRegistry;
    firstLink = this;
    nextLink = null;
  }

  /**
   * Appends a link to {@code this}, returning the updated last link.
   * <p>
   * The pattern {@code new X().append(new Y())...append(new Z())} forms a
   * chain starting with X, then Y, then ... Z.
   * @param lastLink a chainable interpreter, with no next link
   * @return the updated last link
   */
  ChainableReverseAbstractInterpreter append(
      ChainableReverseAbstractInterpreter lastLink) {
    Preconditions.checkArgument(lastLink.nextLink == null);
    this.nextLink = lastLink;
    lastLink.firstLink = this.firstLink;
    return lastLink;
  }

  /**
   * Gets the first link of this chain.
   */
  ChainableReverseAbstractInterpreter getFirst() {
    return firstLink;
  }

  /**
   * Calculates the preciser scope starting with the first link.
   */
  protected FlowScope firstPreciserScopeKnowingConditionOutcome(Node condition,
      FlowScope blindScope, boolean outcome) {
    return firstLink.getPreciserScopeKnowingConditionOutcome(
        condition, blindScope, outcome);
  }

  /**
   * Delegates the calculation of the preciser scope to the next link.
   * If there is no next link, returns the blind scope.
   */
  protected FlowScope nextPreciserScopeKnowingConditionOutcome(Node condition,
      FlowScope blindScope, boolean outcome) {
    return nextLink != null ? nextLink.getPreciserScopeKnowingConditionOutcome(
        condition, blindScope, outcome) : blindScope;
  }

  /**
   * Returns the type of a node in the given scope if the node corresponds to a
   * name whose type is capable of being refined.
   * @return The current type of the node if it can be refined, null otherwise.
   */
  JSType getTypeIfRefinable(Node node, FlowScope scope) {
    switch (node.getType()) {
      case Token.NAME:
        StaticSlot<JSType> nameVar = scope.getSlot(node.getString());
        if (nameVar != null) {
          JSType nameVarType = nameVar.getType();
          if (nameVarType == null) {
            nameVarType = node.getJSType();
          }
          return nameVarType;
        }
        return null;

      case Token.GETPROP:
        String qualifiedName = node.getQualifiedName();
        if (qualifiedName == null) {
          return null;
        }
        StaticSlot<JSType> propVar = scope.getSlot(qualifiedName);
        JSType propVarType = null;
        if (propVar != null) {
          propVarType = propVar.getType();
        }
        if (propVarType == null) {
          propVarType = node.getJSType();
        }
        if (propVarType == null) {
          propVarType = getNativeType(UNKNOWN_TYPE);
        }
        return propVarType;
    }
    return null;
  }

  /**
   * Declares a refined type in {@code scope} for the name represented by
   * {@code node}. It must be possible to refine the type of the given node in
   * the given scope, as determined by {@link #getTypeIfRefinable}.
   */
  protected void declareNameInScope(FlowScope scope, Node node, JSType type) {
    switch (node.getType()) {
      case Token.NAME:
        scope.inferSlotType(node.getString(), type);
        break;

      case Token.GETPROP:
        String qualifiedName = node.getQualifiedName();
        Preconditions.checkNotNull(qualifiedName);

        JSType origType = node.getJSType();
        origType = origType == null ? getNativeType(UNKNOWN_TYPE) : origType;
        scope.inferQualifiedSlot(qualifiedName, origType, type);
        break;

      default:
        throw new IllegalArgumentException("Node cannot be refined. \n" +
            node.toStringTree());
    }
  }

  /**
   * @see #getRestrictedWithoutUndefined(JSType)
   */
  private final Visitor<JSType> restrictUndefinedVisitor =
    new Visitor<JSType>() {
      public JSType caseEnumElementType(EnumElementType enumElementType) {
        JSType type = enumElementType.getPrimitiveType().visit(this);
        if (type != null && enumElementType.getPrimitiveType().equals(type)) {
          return enumElementType;
        } else {
          return type;
        }
      }

      public JSType caseAllType() {
        return typeRegistry.createUnionType(OBJECT_TYPE, NUMBER_TYPE,
            STRING_TYPE, BOOLEAN_TYPE, NULL_TYPE);
      }

      public JSType caseNoObjectType() {
        return getNativeType(NO_OBJECT_TYPE);
      }

      public JSType caseNoType() {
        return getNativeType(NO_TYPE);
      }

      public JSType caseBooleanType() {
        return getNativeType(BOOLEAN_TYPE);
      }

      public JSType caseFunctionType(FunctionType type) {
        return type;
      }

      public JSType caseNullType() {
        return getNativeType(NULL_TYPE);
      }

      public JSType caseNumberType() {
        return getNativeType(NUMBER_TYPE);
      }

      public JSType caseObjectType(ObjectType type) {
        return type;
      }

      public JSType caseStringType() {
        return getNativeType(STRING_TYPE);
      }

      public JSType caseUnionType(UnionType type) {
        return type.getRestrictedUnion(getNativeType(VOID_TYPE));
      }

      public JSType caseUnknownType() {
        return getNativeType(UNKNOWN_TYPE);
      }

      public JSType caseVoidType() {
        return null;
      }
    };


  /**
   * @see #getRestrictedWithoutNull(JSType)
   */
  private final Visitor<JSType> restrictNullVisitor =
    new Visitor<JSType>() {
      public JSType caseEnumElementType(EnumElementType enumElementType) {
        JSType type = enumElementType.getPrimitiveType().visit(this);
        if (type != null && enumElementType.getPrimitiveType().equals(type)) {
          return enumElementType;
        } else {
          return type;
        }
      }

      public JSType caseAllType() {
        return typeRegistry.createUnionType(OBJECT_TYPE, NUMBER_TYPE,
            STRING_TYPE, BOOLEAN_TYPE, VOID_TYPE);
      }

      public JSType caseNoObjectType() {
        return getNativeType(NO_OBJECT_TYPE);
      }

      public JSType caseNoType() {
        return getNativeType(NO_TYPE);
      }

      public JSType caseBooleanType() {
        return getNativeType(BOOLEAN_TYPE);
      }

      public JSType caseFunctionType(FunctionType type) {
        return type;
      }

      public JSType caseNullType() {
        return null;
      }

      public JSType caseNumberType() {
        return getNativeType(NUMBER_TYPE);
      }

      public JSType caseObjectType(ObjectType type) {
        return type;
      }

      public JSType caseStringType() {
        return getNativeType(STRING_TYPE);
      }

      public JSType caseUnionType(UnionType type) {
        return type.getRestrictedUnion(getNativeType(NULL_TYPE));
      }

      public JSType caseUnknownType() {
        return getNativeType(UNKNOWN_TYPE);
      }

      public JSType caseVoidType() {
        return getNativeType(VOID_TYPE);
      }
    };

  /**
   * A class common to all visitors that need to restrict the type based on
   * {@code typeof}-like conditions.
   */
  abstract class RestrictByTypeOfResultVisitor
      implements Visitor<JSType> {

    /**
     * Abstracts away the similarities between visiting the unknown type and the
     * all type.
     * @param topType {@code UNKNOWN_TYPE} or {@code ALL_TYPE}
     * @return the restricted type
     * @see #caseAllType
     * @see #caseUnknownType
     */
    protected abstract JSType caseTopType(JSType topType);

    public JSType caseAllType() {
      return caseTopType(getNativeType(ALL_TYPE));
    }

    public JSType caseUnknownType() {
      return caseTopType(getNativeType(UNKNOWN_TYPE));
    }

    public JSType caseUnionType(UnionType type) {
      JSType restricted = null;
      for (JSType alternate : type.getAlternates()) {
        JSType restrictedAlternate = alternate.visit(this);
        if (restrictedAlternate != null) {
          if (restricted == null) {
            restricted = restrictedAlternate;
          } else {
            restricted = restrictedAlternate.getLeastSupertype(restricted);
          }
        }
      }
      return restricted;
    }

    public JSType caseNoType() {
      return getNativeType(NO_TYPE);
    }

    public JSType caseEnumElementType(EnumElementType enumElementType) {
      // NOTE(nicksantos): This is a white lie. Suppose we have:
      // /** @enum {string|number} */ var MyEnum = ...;
      // if (goog.isNumber(myEnumInstance)) {
      //   /* what is myEnumInstance here? */
      // }
      // There is no type that represents {MyEnum - string}. What we really
      // need is a notion of "enum subtyping", so that we could dynamically
      // create a subtype of MyEnum restricted by string. In any case,
      // this should catch the common case.
      JSType type = enumElementType.getPrimitiveType().visit(this);
      if (type != null && enumElementType.getPrimitiveType().equals(type)) {
        return enumElementType;
      } else {
        return type;
      }
    }
  }

  /**
   * A class common to all visitors that need to restrict the type based on
   * some {@code typeof}-like condition being true. All base cases return
   * {@code null}. It is up to the subclasses to override the appropriate ones.
   */
  abstract class RestrictByTrueTypeOfResultVisitor
      extends RestrictByTypeOfResultVisitor {
    public JSType caseNoObjectType() {
      return null;
    }

    public JSType caseBooleanType() {
      return null;
    }

    public JSType caseFunctionType(FunctionType type) {
      return null;
    }

    public JSType caseNullType() {
      return null;
    }

    public JSType caseNumberType() {
      return null;
    }

    public JSType caseObjectType(ObjectType type) {
      return null;
    }

    public JSType caseStringType() {
      return null;
    }

    public JSType caseVoidType() {
      return null;
    }
  }

  /**
   * A class common to all visitors that need to restrict the type based on
   * some {@code typeof}-like condition being false. All base cases return
   * their type. It is up to the subclasses to override the appropriate ones.
   */
  abstract class RestrictByFalseTypeOfResultVisitor
      extends RestrictByTypeOfResultVisitor {
    @Override
    protected JSType caseTopType(JSType topType) {
      return topType;
    }

    public JSType caseNoObjectType() {
      return getNativeType(NO_OBJECT_TYPE);
    }

    public JSType caseBooleanType() {
      return getNativeType(BOOLEAN_TYPE);
    }

    public JSType caseFunctionType(FunctionType type) {
      return type;
    }

    public JSType caseNullType() {
      return getNativeType(NULL_TYPE);
    }

    public JSType caseNumberType() {
      return getNativeType(NUMBER_TYPE);
    }

    public JSType caseObjectType(ObjectType type) {
      return type;
    }

    public JSType caseStringType() {
      return getNativeType(STRING_TYPE);
    }

    public JSType caseVoidType() {
      return getNativeType(VOID_TYPE);
    }
  }

  /**
   * @see ChainableReverseAbstractInterpreter#getRestrictedByTypeOfResult
   */
  private class RestrictByOneTypeOfResultVisitor
      extends RestrictByTypeOfResultVisitor {
    /**
     * A value known to be equal or not equal to the result of the
     * {@code typeOf} operation.
     */
    private final String value;

    /**
     * {@code true} if the {@code typeOf} result is known to equal
     * {@code value}; {@code false} if it is known <em>not</em> to equal
     * {@code value}.
     */
    private final boolean resultEqualsValue;

    RestrictByOneTypeOfResultVisitor(String value, boolean resultEqualsValue) {
      this.value = value;
      this.resultEqualsValue = resultEqualsValue;
    }

    /**
     * Computes whether the given result of a {@code typeof} operator matches
     * expectations, i.e. whether a type that gives such a result should be
     * kept.
     */
    private boolean matchesExpectation(String result) {
      return result.equals(value) == resultEqualsValue;
    }

    @Override
    protected JSType caseTopType(JSType topType) {
      JSType result = topType;
      if (resultEqualsValue) {
        JSType typeByName = getNativeTypeForTypeOf(value);
        if (typeByName != null) {
          result = typeByName;
        }
      }
      return result;
    }

    public JSType caseNoObjectType() {
      return (value.equals("object") || value.equals("function")) ==
          resultEqualsValue ? getNativeType(NO_OBJECT_TYPE) : null;
    }

    public JSType caseBooleanType() {
      return matchesExpectation("boolean") ? getNativeType(BOOLEAN_TYPE) : null;
    }

    public JSType caseFunctionType(FunctionType type) {
      return matchesExpectation("function") ? type : null;
    }

    public JSType caseNullType() {
      return matchesExpectation("object") ? getNativeType(NULL_TYPE) : null;
    }

    public JSType caseNumberType() {
      return matchesExpectation("number") ? getNativeType(NUMBER_TYPE) : null;
    }

    public JSType caseObjectType(ObjectType type) {
      if (value.equals("function")) {
        JSType ctorType = getNativeType(U2U_CONSTRUCTOR_TYPE);
        return resultEqualsValue && ctorType.isSubtype(type) ? ctorType : null;
      }
      return matchesExpectation("object") ? type : null;
    }

    public JSType caseStringType() {
      return matchesExpectation("string") ? getNativeType(STRING_TYPE) : null;
    }

    public JSType caseVoidType() {
      return matchesExpectation("undefined") ? getNativeType(VOID_TYPE) : null;
    }
  }

  /**
   * Returns a version of type where undefined is not present.
   */
  final JSType getRestrictedWithoutUndefined(JSType type) {
    return type == null ? null : type.visit(restrictUndefinedVisitor);
  }

  /**
   * Returns a version of type where null is not present.
   */
  final JSType getRestrictedWithoutNull(JSType type) {
    return type == null ? null : type.visit(restrictNullVisitor);
  }

  /**
   * Returns a version of {@code type} that is restricted by some knowledge
   * about the result of the {@code typeof} operation.
   * <p>
   * The behavior of the {@code typeof} operator can be summarized by the
   * following table:
   * <table>
   * <tr><th>type</th><th>result</th></tr>
   * <tr><td>{@code undefined}</td><td>"undefined"</td></tr>
   * <tr><td>{@code null}</td><td>"object"</td></tr>
   * <tr><td>{@code boolean}</td><td>"boolean"</td></tr>
   * <tr><td>{@code number}</td><td>"number"</td></tr>
   * <tr><td>{@code string}</td><td>"string"</td></tr>
   * <tr><td>{@code Object} (which doesn't implement [[Call]])</td>
   *     <td>"object"</td></tr>
   * <tr><td>{@code Object} (which implements [[Call]])</td>
   *     <td>"function"</td></tr>
   * </table>
   * @param type the type to restrict
   * @param value A value known to be equal or not equal to the result of the
   *        {@code typeof} operation
   * @param resultEqualsValue {@code true} if the {@code typeOf} result is known
   *        to equal {@code value}; {@code false} if it is known <em>not</em> to
   *        equal {@code value}
   * @return the restricted type or null if no version of the type matches the
   *         restriction
   */
  JSType getRestrictedByTypeOfResult(JSType type, String value,
                                     boolean resultEqualsValue) {
    if (type == null) {
      if (resultEqualsValue) {
        JSType result = getNativeTypeForTypeOf(value);
        return result == null ? getNativeType(UNKNOWN_TYPE) : result;
      } else {
        return null;
      }
    }
    return type.visit(
        new RestrictByOneTypeOfResultVisitor(value, resultEqualsValue));
  }

  JSType getNativeType(JSTypeNative typeId) {
    return typeRegistry.getNativeType(typeId);
  }

  /**
   * If we definitely know what a type is based on the typeof result,
   * return it.  Otherwise, return null.
   *
   * The typeof operation in JS is poorly defined, and this function works
   * for both the native typeof and goog.typeOf. It should not be made public,
   * because its semantics are informally defined, and would be wrong in
   * the general case.
   */
  private JSType getNativeTypeForTypeOf(String value) {
    if (value.equals("number")) {
      return getNativeType(NUMBER_TYPE);
    } else if (value.equals("boolean")) {
      return getNativeType(BOOLEAN_TYPE);
    } else if (value.equals("string")) {
      return getNativeType(STRING_TYPE);
    } else if (value.equals("undefined")) {
      return getNativeType(VOID_TYPE);
    } else if (value.equals("function")) {
      return getNativeType(U2U_CONSTRUCTOR_TYPE);
    } else {
      return null;
    }
  }
}
