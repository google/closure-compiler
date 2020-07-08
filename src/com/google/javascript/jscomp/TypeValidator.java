/*
 * Copyright 2009 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.rhino.jstype.JSTypeNative.ARRAY_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.ASYNC_GENERATOR_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BIGINT_NUMBER;
import static com.google.javascript.rhino.jstype.JSTypeNative.BIGINT_NUMBER_OBJECT;
import static com.google.javascript.rhino.jstype.JSTypeNative.BIGINT_NUMBER_STRING;
import static com.google.javascript.rhino.jstype.JSTypeNative.BIGINT_NUMBER_STRING_OBJECT;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.GENERATOR_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.ITERABLE_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.I_TEMPLATE_ARRAY_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NO_OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NULL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_STRING_SYMBOL;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_SYMBOL;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_SYMBOL;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.VOID_TYPE;

import com.google.common.base.Joiner;
import com.google.common.base.Supplier;
import com.google.javascript.jscomp.JsIterables.MaybeBoxedIterableOrAsyncIterable;
import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.EnumElementType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSType.Nullability;
import com.google.javascript.rhino.jstype.JSType.SubtypingMode;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.NamedType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.Property;
import com.google.javascript.rhino.jstype.Property.OwnedProperty;
import com.google.javascript.rhino.jstype.TemplateType;
import com.google.javascript.rhino.jstype.TemplateTypeMap;
import com.google.javascript.rhino.jstype.TemplateTypeReplacer;
import com.google.javascript.rhino.jstype.TemplatizedType;
import com.google.javascript.rhino.jstype.UnionType;
import com.google.javascript.rhino.jstype.UnknownType;
import com.google.javascript.rhino.jstype.Visitor;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nullable;

/**
 * A central reporter for all type violations: places where the programmer
 * has annotated a variable (or property) with one type, but has assigned
 * another type to it.
 *
 * Also doubles as a central repository for all type violations, so that
 * type-based optimizations (like AmbiguateProperties) can be fault-tolerant.
 */
class TypeValidator implements Serializable {
  private final transient AbstractCompiler compiler;
  private final JSTypeRegistry typeRegistry;
  private final JSType allBitwisableValueTypes;
  private final JSType nullOrUndefined;
  private final JSType promiseOfUnknownType;
  private final JSType iterableOrAsyncIterable;

  // In TypeCheck, when we are analyzing a file with .java.js suffix, we set
  // this field to IGNORE_NULL_UNDEFINED
  private SubtypingMode subtypingMode = SubtypingMode.NORMAL;

  private final TypeMismatch.Accumulator mismatches = new TypeMismatch.Accumulator();

  // User warnings
  private static final String FOUND_REQUIRED =
      "{0}\n"
          + "found   : {1}\n"
          + "required: {2}";

  private static final String FOUND_REQUIRED_MISSING =
      "{0}\n"
      + "found   : {1}\n"
      + "required: {2}\n"
      + "missing : [{3}]\n"
      + "mismatch: [{4}]";

  static final DiagnosticType INVALID_CAST =
      DiagnosticType.warning("JSC_INVALID_CAST",
          "invalid cast - must be a subtype or supertype\n"
              + "from: {0}\n"
              + "to  : {1}");

  static final DiagnosticType TYPE_MISMATCH_WARNING =
      DiagnosticType.warning("JSC_TYPE_MISMATCH", "{0}");

  static final DiagnosticType INVALID_ASYNC_RETURN_TYPE =
      DiagnosticType.warning(
          "JSC_INVALID_ASYNC_RETURN_TYPE",
          "The return type of an async function must be a supertype of Promise\n" + "found: {0}");

  static final DiagnosticType INVALID_OPERAND_TYPE =
      DiagnosticType.disabled("JSC_INVALID_OPERAND_TYPE", "{0}");

  static final DiagnosticType MISSING_EXTENDS_TAG_WARNING =
      DiagnosticType.warning(
          "JSC_MISSING_EXTENDS_TAG",
          "Missing @extends tag on type {0}");

  static final DiagnosticType DUP_VAR_DECLARATION =
      DiagnosticType.warning("JSC_DUP_VAR_DECLARATION",
          "variable {0} redefined, original definition at {1}:{2}");

  static final DiagnosticType DUP_VAR_DECLARATION_TYPE_MISMATCH =
      DiagnosticType.warning("JSC_DUP_VAR_DECLARATION_TYPE_MISMATCH",
          "variable {0} redefined with type {1}, original definition at {2}:{3} with type {4}");

  static final DiagnosticType INTERFACE_METHOD_NOT_IMPLEMENTED =
      DiagnosticType.warning(
          "JSC_INTERFACE_METHOD_NOT_IMPLEMENTED",
          "property {0} on interface {1} is not implemented by type {2}");

  static final DiagnosticType HIDDEN_INTERFACE_PROPERTY_MISMATCH =
      DiagnosticType.warning(
          "JSC_HIDDEN_INTERFACE_PROPERTY_MISMATCH",
          "mismatch of the {0} property on type {4} and the type "
              + "of the property it overrides from interface {1}\n"
              + "original: {2}\n"
              + "override: {3}");

  // TODO(goktug): consider updating super class / interface property mismatch to follow the same
  // pattern.
  static final DiagnosticType HIDDEN_SUPERCLASS_PROPERTY_MISMATCH =
      DiagnosticType.warning(
          "JSC_HIDDEN_SUPERCLASS_PROPERTY_MISMATCH",
          "mismatch of the {0} property type and the type "
              + "of the property it overrides from superclass {1}\n"
              + "original: {2}\n"
              + "override: {3}");

  static final DiagnosticType ABSTRACT_METHOD_NOT_IMPLEMENTED =
      DiagnosticType.warning(
          "JSC_ABSTRACT_METHOD_NOT_IMPLEMENTED",
          "property {0} on abstract class {1} is not implemented by type {2}");

  static final DiagnosticType UNKNOWN_TYPEOF_VALUE =
      DiagnosticType.warning("JSC_UNKNOWN_TYPEOF_VALUE", "unknown type: {0}");

  static final DiagnosticType ILLEGAL_PROPERTY_ACCESS =
      DiagnosticType.warning("JSC_ILLEGAL_PROPERTY_ACCESS",
                             "Cannot do {0} access on a {1}");

  static final DiagnosticGroup ALL_DIAGNOSTICS =
      new DiagnosticGroup(
          ABSTRACT_METHOD_NOT_IMPLEMENTED,
          DUP_VAR_DECLARATION,
          DUP_VAR_DECLARATION_TYPE_MISMATCH,
          HIDDEN_INTERFACE_PROPERTY_MISMATCH,
          ILLEGAL_PROPERTY_ACCESS,
          INTERFACE_METHOD_NOT_IMPLEMENTED,
          INVALID_ASYNC_RETURN_TYPE,
          INVALID_CAST,
          MISSING_EXTENDS_TAG_WARNING,
          TYPE_MISMATCH_WARNING,
          UNKNOWN_TYPEOF_VALUE);

  TypeValidator(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.typeRegistry = compiler.getTypeRegistry();
    this.allBitwisableValueTypes =
        typeRegistry.createUnionType(STRING_TYPE, NUMBER_TYPE, BOOLEAN_TYPE, NULL_TYPE, VOID_TYPE);
    this.nullOrUndefined = typeRegistry.getNativeType(JSTypeNative.NULL_VOID);
    this.promiseOfUnknownType =
        typeRegistry.createTemplatizedType(
            typeRegistry.getNativeObjectType(JSTypeNative.PROMISE_TYPE),
            typeRegistry.getNativeType(JSTypeNative.UNKNOWN_TYPE));
    this.iterableOrAsyncIterable =
        typeRegistry.createUnionType(
            typeRegistry.getNativeObjectType(JSTypeNative.ITERATOR_TYPE),
            typeRegistry.getNativeObjectType(JSTypeNative.ASYNC_ITERATOR_TYPE));
  }

  /** Utility function that attempts to get an instance type from a potential constructor type */
  static ObjectType getInstanceOfCtor(@Nullable JSType t) {
    if (t == null) {
      return null;
    }
    FunctionType ctor = JSType.toMaybeFunctionType(t.dereference());
    if (ctor != null && ctor.isConstructor()) {
      return ctor.getInstanceType();
    }
    return null;
  }

  /**
   * Gets a list of type violations.
   *
   * For each violation, one element is the expected type and the other is
   * the type that is actually found. Order is not significant.
   *
   * NOTE(dimvar): Even though TypeMismatch is a pair, the passes that call this
   * method never use it as a pair; they just add both its elements to a set
   * of invalidating types. Consider just maintaining a set of types here
   * instead of a set of type pairs.
   */
  Iterable<TypeMismatch> getMismatches() {
    return this.mismatches.getMismatches();
  }

  void setSubtypingMode(SubtypingMode mode) {
    this.subtypingMode = mode;
  }

  /**
   * all uses of implicitly implemented interfaces,
   * captured during type validation and type checking
   * (uses of explicitly @implemented structural interfaces are excluded)
   */
  public Iterable<TypeMismatch> getImplicitInterfaceUses() {
    return this.mismatches.getImplicitInterfaceUses();
  }

  // All non-private methods should have the form:
  // expectCondition(Node n, ...);
  // If there is a mismatch, the {@code expect} method should issue
  // a warning and attempt to correct the mismatch, when possible.

  void expectValidTypeofName(Node n, String found) {
    this.compiler.report(JSError.make(n, UNKNOWN_TYPEOF_VALUE, found));
  }

  /**
   * Expect the type to be an object, or a type convertible to object. If the expectation is not
   * met, issue a warning at the provided node's source code position.
   *
   * @return True if there was no warning, false if there was a mismatch.
   */
  boolean expectObject(Node n, JSType type, String msg) {
    if (!type.matchesObjectContext()) {
      mismatch(n, msg, type, OBJECT_TYPE);
      return false;
    }
    return true;
  }

  /**
   * Expect the type to be an object. Unlike expectObject, a type convertible to object is not
   * acceptable.
   */
  void expectActualObject(Node n, JSType type, String msg) {
    if (!type.isObject()) {
      mismatch(n, msg, type, OBJECT_TYPE);
    }
  }

  /**
   * Expect the type to contain an object sometimes. If the expectation is not met, issue a warning
   * at the provided node's source code position.
   */
  void expectAnyObject(Node n, JSType type, String msg) {
    JSType anyObjectType = getNativeType(NO_OBJECT_TYPE);
    if (!anyObjectType.isSubtypeOf(type) && !type.isEmptyType()) {
      mismatch(n, msg, type, anyObjectType);
    }
  }
  /**
   * Expect the type to autobox to be an Iterable.
   *
   * @return True if there was no warning, false if there was a mismatch.
   */
  boolean expectAutoboxesToIterable(Node n, JSType type, String msg) {
    // Note: we don't just use JSType.autobox() here because that removes null and undefined.
    // We want to keep null and undefined around.
    if (type.isUnionType()) {
      for (JSType alt : type.toMaybeUnionType().getAlternates()) {
        alt = alt.isBoxableScalar() ? alt.autoboxesTo() : alt;
        if (!alt.isSubtypeOf(getNativeType(ITERABLE_TYPE))) {
          mismatch(n, msg, type, ITERABLE_TYPE);
          return false;
        }
      }

    } else {
      JSType autoboxedType = type.isBoxableScalar() ? type.autoboxesTo() : type;
      if (!autoboxedType.isSubtypeOf(getNativeType(ITERABLE_TYPE))) {
        mismatch(n, msg, type, ITERABLE_TYPE);
        return false;
      }
    }
    return true;
  }

  /**
   * Expect the type to autobox to be an Iterable or AsyncIterable.
   *
   * @return The unwrapped variants of the iterable(s), or empty if not iterable.
   */
  Optional<JSType> expectAutoboxesToIterableOrAsyncIterable(Node n, JSType type, String msg) {
    MaybeBoxedIterableOrAsyncIterable maybeBoxed =
        JsIterables.maybeBoxIterableOrAsyncIterable(type, typeRegistry);

    if (maybeBoxed.isMatch()) {
      return Optional.of(maybeBoxed.getTemplatedType());
    }

    mismatch(n, msg, type, iterableOrAsyncIterable);

    return Optional.empty();
  }

  /** Expect the type to be a Generator or supertype of Generator. */
  void expectGeneratorSupertype(Node n, JSType type, String msg) {
    if (!getNativeType(GENERATOR_TYPE).isSubtypeOf(type)) {
      mismatch(n, msg, type, GENERATOR_TYPE);
    }
  }

  /** Expect the type to be a AsyncGenerator or supertype of AsyncGenerator. */
  void expectAsyncGeneratorSupertype(Node n, JSType type, String msg) {
    if (!getNativeType(ASYNC_GENERATOR_TYPE).isSubtypeOf(type)) {
      mismatch(n, msg, type, ASYNC_GENERATOR_TYPE);
    }
  }

  /**
   * Expect the type to be a supertype of `Promise`.
   *
   * <p>`Promise` is the <em>lower</em> bound of the declared return type, since that's what async
   * functions always return; the user can't return an instance of a more specific type.
   */
  void expectValidAsyncReturnType(Node n, JSType type) {
    if (promiseOfUnknownType.isSubtypeOf(type)) {
      return;
    }

    JSError err = JSError.make(n, INVALID_ASYNC_RETURN_TYPE, type.toString());
    registerMismatchAndReport(type, promiseOfUnknownType, err);
  }

  /** Expect the type to be an ITemplateArray or supertype of ITemplateArray. */
  void expectITemplateArraySupertype(Node n, JSType type, String msg) {
    if (!getNativeType(I_TEMPLATE_ARRAY_TYPE).isSubtypeOf(type)) {
      mismatch(n, msg, type, I_TEMPLATE_ARRAY_TYPE);
    }
  }

  /**
   * Expect the type to be a string, or a type convertible to string. If the expectation is not met,
   * issue a warning at the provided node's source code position.
   */
  void expectString(Node n, JSType type, String msg) {
    if (!type.matchesStringContext()) {
      mismatch(n, msg, type, STRING_TYPE);
    }
  }

  /**
   * Expect the type to be a number, or a type convertible to number. If the expectation is not met,
   * issue a warning at the provided node's source code position.
   */
  void expectNumber(Node n, JSType type, String msg) {
    if (!type.matchesNumberContext()) {
      mismatch(n, msg, type, NUMBER_TYPE);
    } else {
      expectNumberStrict(n, type, msg);
    }
  }

  /**
   * Expect the type to be a number or a subtype.
   */
  void expectNumberStrict(Node n, JSType type, String msg) {
    if (!type.isSubtypeOf(getNativeType(NUMBER_TYPE))) {
      registerMismatchAndReport(
          n, INVALID_OPERAND_TYPE, msg, type, getNativeType(NUMBER_TYPE), null, null);
    }
  }

  void expectMatchingTypesStrict(Node n, JSType left, JSType right, String msg) {
    if (!left.isSubtypeOf(right) && !right.isSubtypeOf(left)) {
      registerMismatchAndReport(n, INVALID_OPERAND_TYPE, msg, right, left, null, null);
    }
  }

  /**
   * Expect the type to be a valid operand to a bitwise operator. This includes numbers, any type
   * convertible to a number, or any other primitive type (undefined|null|boolean|string).
   */
  void expectBitwiseable(Node n, JSType type, String msg) {
    if (!type.matchesNumberContext() && !type.isSubtypeOf(allBitwisableValueTypes)) {
      mismatch(n, msg, type, allBitwisableValueTypes);
    } else {
      expectNumberStrict(n, type, msg);
    }
  }

  /**
   * Expect the type to be a number or string, or a type convertible to a number or symbol. If the
   * expectation is not met, issue a warning at the provided node's source code position.
   */
  void expectNumberOrSymbol(Node n, JSType type, String msg) {
    if (!type.matchesNumberContext() && !type.matchesSymbolContext()) {
      mismatch(n, msg, type, NUMBER_SYMBOL);
    }
  }

  /**
   * Expect the type to be a string or symbol, or a type convertible to a string. If the expectation
   * is not met, issue a warning at the provided node's source code position.
   */
  void expectStringOrSymbol(Node n, JSType type, String msg) {
    if (!type.matchesStringContext() && !type.matchesSymbolContext()) {
      mismatch(n, msg, type, STRING_SYMBOL);
    }
  }

  /** Expect the type to be unknown or a comparable type (bigint, number, or string) */
  void expectUnknownOrComparable(Node n, JSType type, String msg) {
    if (!type.isSubtypeOf(getNativeType(BIGINT_NUMBER_STRING))
        && !type.isSubtypeOf(getNativeType(BIGINT_NUMBER_STRING_OBJECT))) {
      if (type.matchesNumberContext() || type.matchesStringContext()) {
        // INVALID_OPERAND_TYPE is suppressed unless strict type checking is enabled
        registerMismatchAndReport(
            n, INVALID_OPERAND_TYPE, msg, type, getNativeType(BIGINT_NUMBER_STRING), null, null);
      } else {
        mismatch(n, msg, type, BIGINT_NUMBER_STRING);
      }
    }
  }

  /**
   * Expect the type to be a number or string or symbol, or a type convertible to a number or
   * string. If the expectation is not met, issue a warning at the provided node's source code
   * position.
   */
  void expectStringOrNumberOrSymbol(Node n, JSType type, String msg) {
    if (!type.matchesNumberContext()
        && !type.matchesStringContext()
        && !type.matchesSymbolContext()) {
      mismatch(n, msg, type, NUMBER_STRING_SYMBOL);
    } else {
      expectStringOrNumberOrSymbolStrict(n, type, msg);
    }
  }

  void expectStringOrNumberOrSymbolStrict(Node n, JSType type, String msg) {
    if (!type.isSubtypeOf(getNativeType(NUMBER_STRING_SYMBOL))) {
      registerMismatchAndReport(
          n, INVALID_OPERAND_TYPE, msg, type, getNativeType(NUMBER_STRING_SYMBOL), null, null);
    }
  }

  /**
   * Expect the type to be a bigint or number, or a type convertible to number. If the expectation
   * is not met, issue a warning at the provided node's source code position.
   */
  void expectBigIntOrNumber(Node n, JSType type, String msg) {
    if (!type.isSubtypeOf(getNativeType(BIGINT_NUMBER))
        && !type.isSubtypeOf(getNativeType(BIGINT_NUMBER_OBJECT))) {
      if (type.matchesNumberContext()) {
        // INVALID_OPERAND_TYPE is suppressed unless strict type checking is enabled
        registerMismatchAndReport(
            n, INVALID_OPERAND_TYPE, msg, type, getNativeType(BIGINT_NUMBER), null, null);
      } else {
        mismatch(n, msg, type, BIGINT_NUMBER);
      }
    }
  }

  /**
   * Expect the type to be anything but the null or void type. If the expectation is not met, issue
   * a warning at the provided node's source code position. Note that a union type that includes the
   * void type and at least one other type meets the expectation.
   *
   * @return Whether the expectation was met.
   */
  boolean expectNotNullOrUndefined(
      NodeTraversal t, Node n, JSType type, String msg, JSType expectedType) {
    if (!type.isNoType() && !type.isUnknownType()
        && type.isSubtypeOf(nullOrUndefined)
        && !containsForwardDeclaredUnresolvedName(type)) {

      // There's one edge case right now that we don't handle well, and
      // that we don't want to warn about.
      // if (this.x == null) {
      //   this.initializeX();
      //   this.x.foo();
      // }
      // In this case, we incorrectly type x because of how we
      // infer properties locally. See issue 109.
      // http://blickly.github.io/closure-compiler-issues/#109
      //
      // We do not do this inference globally.
      if (n.isGetProp() && !t.inGlobalScope() && type.isNullType()) {
        return true;
      }

      mismatch(n, msg, type, expectedType);
      return false;
    }
    return true;
  }

  private static boolean containsForwardDeclaredUnresolvedName(JSType type) {
    if (type.isUnionType()) {
      for (JSType alt : type.toMaybeUnionType().getAlternates()) {
        if (containsForwardDeclaredUnresolvedName(alt)) {
          return true;
        }
      }
    }
    return type.isNoResolvedType();
  }

  /** Expect that the type of a switch condition matches the type of its case condition. */
  void expectSwitchMatchesCase(Node n, JSType switchType, JSType caseType) {
    // ECMA-262, page 68, step 3 of evaluation of CaseBlock
    if (!switchType.canTestForShallowEqualityWith(caseType)) {
      mismatch(n.getFirstChild(), "case expression doesn't match switch", caseType, switchType);
    }
  }

  /**
   * Expect that the first type can be addressed with GETELEM syntax and that the second type is the
   * right type for an index into the first type.
   *
   * @param n The GETELEM or COMPUTED_PROP node to issue warnings on.
   * @param objType The type we're indexing into (the left side of the GETELEM).
   * @param indexType The type inside the brackets of the GETELEM/COMPUTED_PROP.
   */
  void expectIndexMatch(Node n, JSType objType, JSType indexType) {
    checkState(n.isGetElem() || n.isOptChainGetElem() || n.isComputedProp(), n);
    Node indexNode = n.isGetElem() ? n.getLastChild() : n.getFirstChild();
    if (indexType.isSymbolValueType()) {
      // For now, allow symbols definitions/access on any type. In the future only allow them
      // on the subtypes for which they are defined.
      return;
    }
    if (objType.isUnknownType()) {
      expectStringOrNumberOrSymbol(indexNode, indexType, "property access");
      return;
    }
    ObjectType dereferenced = objType.dereference();
    if (dereferenced != null
        && dereferenced.getTemplateTypeMap().hasTemplateKey(typeRegistry.getObjectIndexKey())) {
      expectCanAssignTo(
          indexNode,
          indexType,
          dereferenced
              .getTemplateTypeMap()
              .getResolvedTemplateType(typeRegistry.getObjectIndexKey()),
          "restricted index type");
    } else if (dereferenced != null && dereferenced.isArrayType()) {
      expectNumberOrSymbol(indexNode, indexType, "array access");
    } else if (objType.isStruct()) {
      this.compiler.report(JSError.make(indexNode, ILLEGAL_PROPERTY_ACCESS, "'[]'", "struct"));
    } else if (objType.matchesObjectContext()) {
      expectStringOrSymbol(indexNode, indexType, "property access");
    } else {
      mismatch(
          n,
          "only arrays or objects can be accessed",
          objType,
          typeRegistry.createUnionType(ARRAY_TYPE, OBJECT_TYPE));
    }
  }

  /**
   * Expect that the first type can be assigned to a symbol of the second type.
   *
   * @param n The node to issue warnings on.
   * @param rightType The type on the RHS of the assign.
   * @param leftType The type of the symbol on the LHS of the assign.
   * @param owner The owner of the property being assigned to.
   * @param propName The name of the property being assigned to.
   * @return True if the types matched, false otherwise.
   */
  boolean expectCanAssignToPropertyOf(
      Node n, JSType rightType, JSType leftType, Node owner, String propName) {
    return expectCanAssignToPropertyOf(
        n,
        rightType,
        leftType,
        getJSType(owner),
        () -> typeRegistry.getReadableTypeName(owner),
        propName);
  }

  /**
   * Expect that the first type can be assigned to a symbol of the second type.
   *
   * @param n The node to issue warnings on.
   * @param rightType The type on the RHS of the assign.
   * @param leftType The type of the symbol on the LHS of the assign.
   * @param ownerType The owner of the property being assigned to.
   * @param propName The name of the property being assigned to.
   * @return True if the types matched, false otherwise.
   */
  boolean expectCanAssignToPropertyOf(
      Node n,
      JSType rightType,
      JSType leftType,
      JSType ownerType,
      Supplier<String> typeNameSupplier,
      String propName) {
    if (leftType.isTemplateType()) {
      TemplateType left = leftType.toMaybeTemplateType();
      if (rightType.containsReferenceAncestor(left)
          || rightType.isUnknownType()
          || left.isUnknownType()) {
        // The only time we can assign to a variable with a template type is if the value assigned
        // has a type that explicitly has it as a supertype.
        // Otherwise, the template type is existential and it is unknown whether or not it is a
        // proper super type.
        return true;
      } else {
        registerMismatchAndReport(
            n,
            TYPE_MISMATCH_WARNING,
            "assignment to property " + propName + " of " + typeNameSupplier.get(),
            rightType,
            leftType,
            new HashSet<>(),
            new HashSet<>());
        return false;
      }
    }
    // The NoType check is a hack to make typedefs work OK.
    if (!leftType.isNoType() && !rightType.isSubtypeOf(leftType)) {
      // Do not type-check interface methods, because we expect that
      // they will have dummy implementations that do not match the type
      // annotations.
      if (ownerType.isFunctionPrototypeType()) {
        FunctionType ownerFn = ownerType.toObjectType().getOwnerFunction();
        if (ownerFn.isInterface()
            && rightType.isFunctionType() && leftType.isFunctionType()) {
          return true;
        }
      }

      mismatch(
          n,
          "assignment to property " + propName + " of " + typeNameSupplier.get(),
          rightType,
          leftType);
      return false;
    } else if (!leftType.isNoType() && !rightType.isSubtypeWithoutStructuralTyping(leftType)){
      this.mismatches.recordImplicitInterfaceUses(n, rightType, leftType);
      this.mismatches.recordImplicitUseOfNativeObject(n, rightType, leftType);
    }
    return true;
  }

  /**
   * Expect that the first type can be assigned to a symbol of the second type.
   *
   * @param n The node to issue warnings on.
   * @param rightType The type on the RHS of the assign.
   * @param leftType The type of the symbol on the LHS of the assign.
   * @param msg An extra message for the mismatch warning, if necessary.
   * @return True if the types matched, false otherwise.
   */
  boolean expectCanAssignTo(Node n, JSType rightType, JSType leftType, String msg) {
    if (leftType.isTemplateType()) {
      TemplateType left = leftType.toMaybeTemplateType();
      if (rightType.containsReferenceAncestor(left)
          || rightType.isUnknownType()
          || left.isUnknownType()) {
        // The only time we can assign to a variable with a template type is if the value assigned
        // has a type that explicitly has it as a supertype.
        // Otherwise, the template type is existential and it is unknown whether or not it is a
        // proper super type.
        return true;
      } else {
        registerMismatchAndReport(
            n, TYPE_MISMATCH_WARNING, msg, rightType, leftType, new HashSet<>(), new HashSet<>());
        return false;
      }
    }
    if (!rightType.isSubtypeOf(leftType)) {
      mismatch(n, msg, rightType, leftType);
      return false;
    } else if (!rightType.isSubtypeWithoutStructuralTyping(leftType)) {
      this.mismatches.recordImplicitInterfaceUses(n, rightType, leftType);
      this.mismatches.recordImplicitUseOfNativeObject(n, rightType, leftType);
    }
    return true;
  }

  /**
   * Expect that the type of an argument matches the type of the parameter that it's fulfilling.
   *
   * @param n The node to issue warnings on.
   * @param argType The type of the argument.
   * @param paramType The type of the parameter.
   * @param callNode The call node, to help with the warning message.
   * @param ordinal The argument ordinal, to help with the warning message.
   */
  void expectArgumentMatchesParameter(
      Node n, JSType argType, JSType paramType, Node callNode, int ordinal) {
    if (!argType.isSubtypeOf(paramType)) {
      mismatch(
          n,
          SimpleFormat.format(
              "actual parameter %d of %s does not match formal parameter",
              ordinal, typeRegistry.getReadableTypeNameNoDeref(callNode.getFirstChild())),
          argType,
          paramType);
    } else if (!argType.isSubtypeWithoutStructuralTyping(paramType)){
      this.mismatches.recordImplicitInterfaceUses(n, argType, paramType);
      this.mismatches.recordImplicitUseOfNativeObject(n, argType, paramType);
    }
  }

  /**
   * Expect that the first type is the direct superclass of the second type.
   *
   * @param n The node where warnings should point to.
   * @param superObject The expected super instance type.
   * @param subObject The sub instance type.
   */
  void expectSuperType(Node n, ObjectType superObject, ObjectType subObject) {
    FunctionType subCtor = subObject.getConstructor();
    ObjectType implicitProto = subObject.getImplicitPrototype();
    ObjectType declaredSuper =
        implicitProto == null ? null : implicitProto.getImplicitPrototype();
    if (declaredSuper != null && declaredSuper.isTemplatizedType()) {
      declaredSuper =
          declaredSuper.toMaybeTemplatizedType().getReferencedType();
    }
    if (declaredSuper != null
        && !(superObject instanceof UnknownType)
        && !declaredSuper.equals(superObject)) {
      if (declaredSuper.equals(getNativeType(OBJECT_TYPE))) {
        registerMismatchAndReport(
            superObject,
            declaredSuper,
            JSError.make(n, MISSING_EXTENDS_TAG_WARNING, subObject.toString()));
      } else {
        mismatch(n, "mismatch in declaration of superclass type", superObject, declaredSuper);
      }

      // Correct the super type.
      if (!subCtor.hasCachedValues()) {
        subCtor.setPrototypeBasedOn(superObject);
      }
    }
  }

  /**
   * Expect that an ES6 class's extends clause is actually a supertype of the given class.
   * Compares the registered supertype, which is taken from the JSDoc if present, otherwise
   * from the AST, with the type in the extends node of the AST.
   *
   * @param n The node where warnings should point to.
   * @param subCtor The sub constructor type.
   * @param astSuperCtor The expected super constructor from the extends node in the AST.
   */
  void expectExtends(Node n, FunctionType subCtor, FunctionType astSuperCtor) {
    if (astSuperCtor == null || (!astSuperCtor.isConstructor() && !astSuperCtor.isInterface())) {
      // toMaybeFunctionType failed, or we've got a loose type.  Let it go for now.
      return;
    }
    if (astSuperCtor.isConstructor() != subCtor.isConstructor()) {
      // Don't bother looking if one is a constructor and the other is an interface.
      // We'll report an error elsewhere.
      return;
    }
    ObjectType astSuperInstance = astSuperCtor.getInstanceType();
    if (subCtor.isConstructor()) {
      // There should be exactly one superclass, and it needs to have this constructor.
      // Note: if the registered supertype (from the @extends jsdoc) was unresolved,
      // then getSuperClassConstructor will be null - make sure not to crash.
      FunctionType registeredSuperCtor = subCtor.getSuperClassConstructor();
      if (registeredSuperCtor != null) {
        ObjectType registeredSuperInstance = registeredSuperCtor.getInstanceType();
        if (!astSuperInstance.equals(registeredSuperInstance)) {
          mismatch(
              n,
              "mismatch in declaration of superclass type",
              astSuperInstance,
              registeredSuperInstance);
        }
      }
    } else if (subCtor.isInterface()) {
      // We intentionally skip this check for interfaces because they can extend multiple other
      // interfaces.
    }
  }

  /**
   * Expect that it's valid to assign something to a given type's prototype.
   *
   * <p>Most of these checks occur during TypedScopeCreator, so we just handle very basic cases here
   *
   * <p>For example, assuming `Foo` is a constructor, `Foo.prototype = 3;` will warn because `3` is
   * not an object.
   *
   * @param ownerType The type of the object whose prototype is being changed. (e.g. `Foo` above)
   * @param node Node to issue warnings on (e.g. `3` above)
   * @param rightType the rvalue type being assigned to the prototype (e.g. `number` above)
   */
  void expectCanAssignToPrototype(JSType ownerType, Node node, JSType rightType) {
    if (ownerType.isFunctionType()) {
      FunctionType functionType = ownerType.toMaybeFunctionType();
      if (functionType.isConstructor()) {
        expectObject(node, rightType, "cannot override prototype with non-object");
      }
    }
  }

  /**
   * Expect that the first type can be cast to the second type. The first type must have some
   * relationship with the second.
   *
   * @param n The node where warnings should point.
   * @param targetType The type being cast to.
   * @param sourceType The type being cast from.
   */
  void expectCanCast(Node n, JSType targetType, JSType sourceType) {
    if (!sourceType.canCastTo(targetType)) {
      registerMismatchAndReport(
          sourceType,
          targetType,
          JSError.make(n, INVALID_CAST, sourceType.toString(), targetType.toString()));
    } else if (!sourceType.isSubtypeWithoutStructuralTyping(targetType)){
      this.mismatches.recordImplicitInterfaceUses(n, sourceType, targetType);
    }
  }

  /**
   * Expect that the given variable has not been declared with a type.
   *
   * @param sourceName The name of the source file we're in.
   * @param n The node where warnings should point to.
   * @param parent The parent of {@code n}.
   * @param var The variable that we're checking.
   * @param variableName The name of the variable.
   * @param newType The type being applied to the variable. Mostly just here
   *     for the benefit of the warning.
   * @return The variable we end up with. Most of the time, this will just
   *     be {@code var}, but in some rare cases we will need to declare
   *     a new var with new source info.
   */
  TypedVar expectUndeclaredVariable(String sourceName, CompilerInput input,
      Node n, Node parent, TypedVar var, String variableName, JSType newType) {
    TypedVar newVar = var;
    JSType varType = var.getType();

    // Only report duplicate declarations that have types. Other duplicates
    // will be reported by the syntactic scope creator later in the
    // compilation process.
    if (varType != null
        && varType != typeRegistry.getNativeType(UNKNOWN_TYPE)
        && newType != null
        && newType != typeRegistry.getNativeType(UNKNOWN_TYPE)) {
      // If there are two typed declarations of the same variable, that
      // is an error and the second declaration is ignored, except in the
      // case of native types. A null input type means that the declaration
      // was made in TypedScopeCreator#createInitialScope and is a
      // native type. We should redeclare it at the new input site.
      if (var.getInput() == null) {
        TypedScope s = var.getScope();
        s.undeclare(var);
        newVar = s.declare(variableName, n, varType, input, false);

        n.setJSType(varType);
        if (parent.isVar()) {
          if (n.hasChildren()) {
            n.getFirstChild().setJSType(varType);
          }
        } else {
          checkState(parent.isFunction() || parent.isClass());
          parent.setJSType(varType);
        }
      } else {
        // Check for @suppress duplicate or similar warnings guard on the previous variable
        // declaration location.
        boolean allowDupe = hasDuplicateDeclarationSuppression(compiler, var.getNameNode());
        // If the previous definition doesn't suppress the warning, emit it here (i.e. always emit
        // on the second of the duplicate definitions). The warning might still be suppressed by an
        // @suppress tag on this declaration.
        if (!allowDupe) {
          // Report specifically if it is not just a duplicate, but types also don't mismatch.
          if (!newType.equals(varType)) {
            this.compiler.report(
                JSError.make(
                    n,
                    DUP_VAR_DECLARATION_TYPE_MISMATCH,
                    variableName,
                    newType.toString(),
                    var.getInputName(),
                    String.valueOf(var.getNameNode().getLineno()),
                    varType.toString()));
          } else if (!var.getParentNode().isExprResult()) {
            // If the type matches and the previous declaration was a stub declaration
            // (isExprResult), then ignore the duplicate, otherwise emit an error.
            this.compiler.report(
                JSError.make(
                    n,
                    DUP_VAR_DECLARATION,
                    variableName,
                    var.getInputName(),
                    String.valueOf(var.getNameNode().getLineno())));
          }
        }
      }
    }

    return newVar;
  }

  /**
   * Expect that all properties on interfaces that this type implements are implemented and
   * correctly typed.
   */
  void expectAllInterfaceProperties(Node n, FunctionType type) {
    ObjectType instance = type.getInstanceType();
    for (ObjectType implemented : type.getAllImplementedInterfaces()) {
      expectInterfaceProperties(n, instance, implemented);
    }
    for (ObjectType extended : type.getExtendedInterfaces()) {
      expectInterfaceProperties(n, instance, extended);
    }
  }

  private void expectInterfaceProperties(
      Node n, ObjectType instance, ObjectType ancestorInterface) {
    // Case: `/** @interface */ class Foo { constructor() { this.prop; } }`
    for (String prop : ancestorInterface.getOwnPropertyNames()) {
      expectInterfaceProperty(n, instance, ancestorInterface, prop);
    }
    if (ancestorInterface.getImplicitPrototype() != null) {
      // Case: `/** @interface */ class Foo { prop() { } }`
      for (String prop : ancestorInterface.getImplicitPrototype().getOwnPropertyNames()) {
        expectInterfaceProperty(n, instance, ancestorInterface, prop);
      }
    }
  }

  /**
   * Expect that the property in an interface that this type implements is implemented and correctly
   * typed.
   */
  private void expectInterfaceProperty(
      Node n, ObjectType instance, ObjectType implementedInterface, String propName) {
    OwnedProperty propSlot = instance.findClosestDefinition(propName);
    if (propSlot == null
        || (!instance.getConstructor().isInterface() && propSlot.isOwnedByInterface())) {

      if (instance.getConstructor().isAbstract() || instance.getConstructor().isInterface()) {
        // Abstract classes and interfaces are not required to implement interface properties.
        return;
      }
      if (implementedInterface.getPropertyType(propName).isVoidable()) {
        // Voidable properties don't require explicit initializaition in type constructors.
        return;
      }

      registerMismatchAndReport(
          instance,
          implementedInterface,
          JSError.make(
              n,
              INTERFACE_METHOD_NOT_IMPLEMENTED,
              propName,
              implementedInterface.getReferenceName(),
              instance.toString()));
    } else {
      boolean local = propSlot.getOwnerInstanceType().equals(instance);
      if (!local && instance.getConstructor().isInterface()) {
        // non-local interface mismatches already handled via interface property conflict checks.
        return;
      }

      Property prop = propSlot.getValue();
      Node propNode = prop.getDeclaration() == null ? null : prop.getDeclaration().getNode();
      // Fall back on the constructor node if we can't find a node for the property.
      propNode = propNode == null ? n : propNode;

      checkPropertyType(propNode, instance, implementedInterface, propName, prop.getType());
    }
  }

  /**
   * Check the property is correctly typed (i.e. subtype of the parent property's type declaration).
   */
  void checkPropertyType(
      Node n, JSType instance, ObjectType parent, String propertyName, JSType found) {
    JSType required = parent.getPropertyType(propertyName);
    TemplateTypeMap typeMap = instance.getTemplateTypeMap();
    if (!typeMap.isEmpty() && required.hasAnyTemplateTypes()) {
      required = required.visit(TemplateTypeReplacer.forPartialReplacement(typeRegistry, typeMap));
    }

    if (found.isSubtypeOf(required, this.subtypingMode)) {
      return;
    }

    // Implemented, but not correctly typed
    JSError err =
        JSError.make(
            n,
            parent.getConstructor().isInterface()
                ? HIDDEN_INTERFACE_PROPERTY_MISMATCH
                : HIDDEN_SUPERCLASS_PROPERTY_MISMATCH,
            propertyName,
            parent.getReferenceName(),
            required.toString(),
            found.toString(),
            instance.toString());
    registerMismatchAndReport(found, required, err);
  }

  /**
   * For a concrete class, expect that all abstract methods that haven't been implemented by any of
   * the super classes on the inheritance chain are implemented.
   */
  void expectAbstractMethodsImplemented(Node n, FunctionType ctorType) {
    checkArgument(ctorType.isConstructor());

    Map<String, ObjectType> abstractMethodSuperTypeMap = new LinkedHashMap<>();
    FunctionType currSuperCtor = ctorType.getSuperClassConstructor();
    if (currSuperCtor == null || !currSuperCtor.isAbstract()) {
      return;
    }

    while (currSuperCtor != null && currSuperCtor.isAbstract()) {
      ObjectType superType = currSuperCtor.getInstanceType();
      for (String prop : currSuperCtor.getPrototype().getOwnPropertyNames()) {
        FunctionType maybeAbstractMethod = superType.findPropertyType(prop).toMaybeFunctionType();
        if (maybeAbstractMethod != null
            && maybeAbstractMethod.isAbstract()
            && !abstractMethodSuperTypeMap.containsKey(prop)) {
          abstractMethodSuperTypeMap.put(prop, superType);
        }
      }
      currSuperCtor = currSuperCtor.getSuperClassConstructor();
    }

    ObjectType instance = ctorType.getInstanceType();
    for (Map.Entry<String, ObjectType> entry : abstractMethodSuperTypeMap.entrySet()) {
      String method = entry.getKey();
      ObjectType superType = entry.getValue();
      FunctionType abstractMethod = instance.findPropertyType(method).toMaybeFunctionType();
      if (abstractMethod == null || abstractMethod.isAbstract()) {
        registerMismatchAndReport(
            instance,
            superType,
            JSError.make(
                n,
                ABSTRACT_METHOD_NOT_IMPLEMENTED,
                method,
                superType.toString(),
                instance.toString()));
      }
    }
  }

  private void mismatch(Node n, String msg, JSType found, JSTypeNative required) {
    mismatch(n, msg, found, getNativeType(required));
  }

  private void mismatch(Node n, String msg, JSType found, JSType required) {
    if (!found.isSubtypeOf(required, this.subtypingMode)) {
      Set<String> missing = null;
      Set<String> mismatch = null;
      if (required.isStructuralType()) {
        missing = new TreeSet<>();
        mismatch = new TreeSet<>();
        ObjectType requiredObject = required.toMaybeObjectType();
        ObjectType foundObject = found.toMaybeObjectType();
        if (requiredObject != null && foundObject != null) {
          for (String property : requiredObject.getPropertyNames()) {
            JSType propRequired = requiredObject.getPropertyType(property);
            boolean hasProperty = foundObject.hasProperty(property);
            if (!propRequired.isExplicitlyVoidable() || hasProperty) {
              if (hasProperty) {
                if (!foundObject
                    .getPropertyType(property)
                    .isSubtypeOf(propRequired, subtypingMode)) {
                  mismatch.add(property);
                }
              } else {
                missing.add(property);
              }
            }
          }
        }
      }
      registerMismatchAndReport(n, TYPE_MISMATCH_WARNING, msg, found, required, missing, mismatch);
    }
  }

  /**
   * Used both for TYPE_MISMATCH_WARNING and INVALID_OPERAND_TYPE.
   */
  private void registerMismatchAndReport(
      Node n,
      DiagnosticType diagnostic,
      String msg,
      JSType found,
      JSType required,
      Set<String> missing,
      Set<String> mismatch) {
    String foundRequiredFormatted = formatFoundRequired(msg, found, required, missing, mismatch);
    JSError err = JSError.make(n, diagnostic, foundRequiredFormatted);
    registerMismatchAndReport(found, required, err);
  }

  /** Registers a type mismatch into the universe of mismatches owned by this pass. */
  private void registerMismatchAndReport(JSType found, JSType required, JSError error) {
    this.compiler.report(error);
    this.mismatches.registerMismatch(error.getNode(), found, required);
  }

  /** Formats a found/required error message. */
  private static String formatFoundRequired(
      String description,
      JSType found,
      JSType required,
      Set<String> missing,
      Set<String> mismatch) {
    String foundStr = found.toString();
    String requiredStr = required.toString();
    if (foundStr.equals(requiredStr)) {
      foundStr = found.toAnnotationString(Nullability.IMPLICIT);
      requiredStr = required.toAnnotationString(Nullability.IMPLICIT);
    }
    String missingStr = "";
    String mismatchStr = "";
    if (missing != null && !missing.isEmpty()) {
      missingStr = Joiner.on(",").join(missing);
    }
    if (mismatch != null && !mismatch.isEmpty()) {
      mismatchStr = Joiner.on(",").join(mismatch);
    }
     if (missingStr.length() > 0 || mismatchStr.length() > 0) {
      return MessageFormat.format(
          FOUND_REQUIRED_MISSING, description, foundStr, requiredStr, missingStr, mismatchStr);
    } else {
      return MessageFormat.format(FOUND_REQUIRED, description, foundStr, requiredStr);
    }
  }

  /**
   * This method gets the JSType from the Node argument and verifies that it is
   * present.
   */
  private JSType getJSType(Node n) {
    return checkNotNull(n.getJSType(), "%s has no JSType attached", n);
  }

  private JSType getNativeType(JSTypeNative typeId) {
    return typeRegistry.getNativeType(typeId);
  }

  /**
   * @param decl The declaration to check.
   * @return Whether duplicated declarations warnings should be suppressed for the given node.
   */
  static boolean hasDuplicateDeclarationSuppression(AbstractCompiler compiler, Node decl) {
    // NB: DUP_VAR_DECLARATION is somewhat arbitrary here, but it must be one of the errors
    // suppressed by the "duplicate" group.
    CheckLevel originalDeclLevel =
        compiler.getErrorLevel(JSError.make(decl, DUP_VAR_DECLARATION, "dummy", "dummy"));
    return originalDeclLevel == CheckLevel.OFF;
  }

  void expectWellFormedTemplatizedType(Node n) {
    WellFormedTemplatizedTypeVerifier verifier = new WellFormedTemplatizedTypeVerifier(n);
    if (n.getJSType() != null) {
      n.getJSType().visit(verifier);
    }
  }

  final class WellFormedTemplatizedTypeVerifier extends Visitor.WithDefaultCase<Boolean> {
    Node node;

    WellFormedTemplatizedTypeVerifier(Node node) {
      this.node = node;
    }

    @Override
    protected Boolean caseDefault(@Nullable JSType type) {
      return true;
    }

    @Override
    public Boolean caseEnumElementType(EnumElementType type) {
      return type.getPrimitiveType() == null || type.getPrimitiveType().visit(this);
    }

    @Override
    public Boolean caseFunctionType(FunctionType type) {
      for (FunctionType.Parameter param : type.getParameters()) {
        if (!param.getJSType().visit(this)) {
          return false;
        }
      }
      return type.getReturnType().visit(this);
    }

    @Override
    public Boolean caseNamedType(NamedType type) {
      return type.getReferencedType().visit(this);
    }

    @Override
    public Boolean caseUnionType(UnionType type) {
      for (JSType alt : type.getAlternates()) {
        if (!alt.visit(this)) {
          return false;
        }
      }
      return true;
    }

    @Override
    public Boolean caseTemplatizedType(TemplatizedType type) {
      List<TemplateType> referencedTemplates =
          type.getReferencedType().getTemplateTypeMap().getTemplateKeys();
      for (int i = 0; i < type.getTemplateTypes().size(); i++) {
        JSType assignedType = type.getTemplateTypes().get(i);
        if (i < referencedTemplates.size()) {
          TemplateType templateType = referencedTemplates.get(i);
          if (!assignedType.isSubtype(templateType.getBound())) {
            registerMismatchAndReport(
                assignedType,
                templateType.getBound(),
                JSError.make(
                    node,
                    RhinoErrorReporter.BOUNDED_GENERIC_TYPE_ERROR,
                    assignedType.toString(),
                    templateType.getReferenceName(),
                    templateType.getBound().toString()));
            return false;
          }
        }
      }
      return true;
    }

    @Override
    public Boolean caseTemplateType(TemplateType templateType) {
      return !templateType.containsCycle() && templateType.getBound().visit(this);
    }
  }
}
