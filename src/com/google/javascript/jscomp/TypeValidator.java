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

import static com.google.common.base.Strings.nullToEmpty;
import static com.google.javascript.rhino.jstype.JSTypeNative.ARRAY_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NO_OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NULL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_STRING_SYMBOL;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_SYMBOL;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.SYMBOL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.VOID_TYPE;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSType.SubtypingMode;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticTypedSlot;
import com.google.javascript.rhino.jstype.TemplateTypeMap;
import com.google.javascript.rhino.jstype.TemplateTypeMapReplacer;
import com.google.javascript.rhino.jstype.UnknownType;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 *
 * @author nicksantos@google.com (Nick Santos)
 */
class TypeValidator implements Serializable {
  private final transient AbstractCompiler compiler;
  private final JSTypeRegistry typeRegistry;
  private final JSType allValueTypes;
  private final JSType nullOrUndefined;

  // In TypeCheck, when we are analyzing a file with .java.js suffix, we set
  // this field to IGNORE_NULL_UNDEFINED
  private SubtypingMode subtypingMode = SubtypingMode.NORMAL;

  // TODO(nicksantos): Provide accessors to better filter the list of type
  // mismatches. For example, if we pass (Cake|null) where only Cake is
  // allowed, that doesn't mean we should invalidate all Cakes.
  private final List<TypeMismatch> mismatches = new ArrayList<>();
  // the detection logic of this one is similar to this.mismatches
  private final List<TypeMismatch> implicitInterfaceUses = new ArrayList<>();

  // User warnings
  private static final String FOUND_REQUIRED =
      "{0}\n" +
      "found   : {1}\n" +
      "required: {2}";

  private static final String FOUND_REQUIRED_MISSING =
      "{0}\n"
      + "found   : {1}\n"
      + "required: {2}\n"
      + "missing : [{3}]\n"
      + "mismatch: [{4}]";

  static final DiagnosticType INVALID_CAST =
      DiagnosticType.warning("JSC_INVALID_CAST",
          "invalid cast - must be a subtype or supertype\n" +
          "from: {0}\n" +
          "to  : {1}");

  static final DiagnosticType UNNECESSARY_CAST =
      DiagnosticType.disabled("JSC_UNNECESSARY_CAST",
          "unnecessary cast\n" +
          "from: {0}\n" +
          "to  : {1}");

  static final DiagnosticType TYPE_MISMATCH_WARNING =
      DiagnosticType.warning(
          "JSC_TYPE_MISMATCH",
          "{0}");

  static final DiagnosticType MISSING_EXTENDS_TAG_WARNING =
      DiagnosticType.warning(
          "JSC_MISSING_EXTENDS_TAG",
          "Missing @extends tag on type {0}");

  static final DiagnosticType DUP_VAR_DECLARATION =
      DiagnosticType.warning("JSC_DUP_VAR_DECLARATION",
          "variable {0} redefined, original definition at {1}:{2}");

  static final DiagnosticType DUP_VAR_DECLARATION_TYPE_MISMATCH =
      DiagnosticType.warning("JSC_DUP_VAR_DECLARATION_TYPE_MISMATCH",
          "variable {0} redefined with type {1}, " +
          "original definition at {2}:{3} with type {4}");

  static final DiagnosticType INTERFACE_METHOD_NOT_IMPLEMENTED =
      DiagnosticType.warning(
          "JSC_INTERFACE_METHOD_NOT_IMPLEMENTED",
          "property {0} on interface {1} is not implemented by type {2}");

  static final DiagnosticType HIDDEN_INTERFACE_PROPERTY_MISMATCH =
      DiagnosticType.warning(
          "JSC_HIDDEN_INTERFACE_PROPERTY_MISMATCH",
          "mismatch of the {0} property on type {1} and the type "
              + "of the property it overrides from interface {2}\n"
              + "original: {3}\n"
              + "override: {4}");

  static final DiagnosticType ABSTRACT_METHOD_NOT_IMPLEMENTED =
      DiagnosticType.warning(
          "JSC_ABSTRACT_METHOD_NOT_IMPLEMENTED",
          "property {0} on abstract class {1} is not implemented by type {2}");

  static final DiagnosticType UNKNOWN_TYPEOF_VALUE =
      DiagnosticType.warning("JSC_UNKNOWN_TYPEOF_VALUE", "unknown type: {0}");

  static final DiagnosticType ILLEGAL_PROPERTY_ACCESS =
      DiagnosticType.warning("JSC_ILLEGAL_PROPERTY_ACCESS",
                             "Cannot do {0} access on a {1}");

  static final DiagnosticGroup ALL_DIAGNOSTICS = new DiagnosticGroup(
      ABSTRACT_METHOD_NOT_IMPLEMENTED,
      INVALID_CAST,
      TYPE_MISMATCH_WARNING,
      MISSING_EXTENDS_TAG_WARNING,
      DUP_VAR_DECLARATION,
      DUP_VAR_DECLARATION_TYPE_MISMATCH,
      INTERFACE_METHOD_NOT_IMPLEMENTED,
      HIDDEN_INTERFACE_PROPERTY_MISMATCH,
      UNKNOWN_TYPEOF_VALUE,
      ILLEGAL_PROPERTY_ACCESS);

  TypeValidator(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.typeRegistry = compiler.getTypeRegistry();
    this.allValueTypes = typeRegistry.createUnionType(
        STRING_TYPE, NUMBER_TYPE, BOOLEAN_TYPE, NULL_TYPE, VOID_TYPE);
    this.nullOrUndefined = typeRegistry.createUnionType(
        NULL_TYPE, VOID_TYPE);
  }

  /**
   * Utility function for getting a function type from a var.
   */
  static FunctionType getFunctionType(@Nullable TypedVar v) {
    JSType t = v == null ? null : v.getType();
    ObjectType o = t == null ? null : t.dereference();
    return JSType.toMaybeFunctionType(o);
  }

  /**
   * Utility function for getting an instance type from a var pointing
   * to the constructor.
   */
  static ObjectType getInstanceOfCtor(@Nullable TypedVar v) {
    FunctionType ctor = getFunctionType(v);
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
    return mismatches;
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
    return implicitInterfaceUses;
  }

  // All non-private methods should have the form:
  // expectCondition(NodeTraversal t, Node n, ...);
  // If there is a mismatch, the {@code expect} method should issue
  // a warning and attempt to correct the mismatch, when possible.

  void expectValidTypeofName(NodeTraversal t, Node n, String found) {
    report(JSError.make(n, UNKNOWN_TYPEOF_VALUE, found));
  }

  /**
   * Expect the type to be an object, or a type convertible to object. If the
   * expectation is not met, issue a warning at the provided node's source code
   * position.
   * @return True if there was no warning, false if there was a mismatch.
   */
  boolean expectObject(NodeTraversal t, Node n, JSType type, String msg) {
    if (!type.matchesObjectContext()) {
      mismatch(t, n, msg, type, OBJECT_TYPE);
      return false;
    }
    return true;
  }

  /**
   * Expect the type to be an object. Unlike expectObject, a type convertible
   * to object is not acceptable.
   */
  void expectActualObject(NodeTraversal t, Node n, JSType type, String msg) {
    if (!type.isObject()) {
      mismatch(t, n, msg, type, OBJECT_TYPE);
    }
  }

  /**
   * Expect the type to contain an object sometimes. If the expectation is
   * not met, issue a warning at the provided node's source code position.
   */
  void expectAnyObject(NodeTraversal t, Node n, JSType type, String msg) {
    JSType anyObjectType = getNativeType(NO_OBJECT_TYPE);
    if (!anyObjectType.isSubtype(type) && !type.isEmptyType()) {
      mismatch(t, n, msg, type, anyObjectType);
    }
  }

  /**
   * Expect the type to be a string, or a type convertible to string. If the
   * expectation is not met, issue a warning at the provided node's source code
   * position.
   */
  void expectString(NodeTraversal t, Node n, JSType type, String msg) {
    if (!type.matchesStringContext()) {
      mismatch(t, n, msg, type, STRING_TYPE);
    }
  }

  /**
   * Expect the type to be a string or symbol, or a type convertible to a string
   * or symbol. If the expectation is not met, issue a warning at the
   * provided node's source code position.
   */
  void expectStringOrSymbol(NodeTraversal t, Node n, JSType type, String msg) {
    if (!type.matchesStringContext() && !type.isSubtype(getNativeType(SYMBOL_TYPE))) {
      mismatch(t, n, msg, type, STRING_SYMBOL);
    }
  }

  /**
   * Expect the type to be a number, or a type convertible to number. If the
   * expectation is not met, issue a warning at the provided node's source code
   * position.
   */
  void expectNumber(NodeTraversal t, Node n, JSType type, String msg) {
    if (!type.matchesNumberContext()) {
      mismatch(t, n, msg, type, NUMBER_TYPE);
    }
  }

  /**
   * Expect the type to be a valid operand to a bitwise operator. This includes
   * numbers, any type convertible to a number, or any other primitive type
   * (undefined|null|boolean|string).
   */
  void expectBitwiseable(NodeTraversal t, Node n, JSType type, String msg) {
    if (!type.matchesNumberContext() && !type.isSubtype(allValueTypes)) {
      mismatch(t, n, msg, type, allValueTypes);
    }
  }

  /**
   * Expect the type to be a number or string or symbol, or a type convertible to
   * a number or string or symbol. If the expectation is not met, issue a warning
   * at the provided node's source code position.
   */
  void expectStringOrNumberOrSymbol(
      NodeTraversal t, Node n, JSType type, String msg) {
    if (!type.matchesNumberContext() && !type.matchesStringContext() && ! !type.isSubtype(getNativeType(SYMBOL_TYPE))) {
      mismatch(t, n, msg, type, NUMBER_STRING_SYMBOL);
    }
  }

  /**
   * Expect the type to be anything but the null or void type. If the
   * expectation is not met, issue a warning at the provided node's
   * source code position. Note that a union type that includes the
   * void type and at least one other type meets the expectation.
   * @return Whether the expectation was met.
   */
  boolean expectNotNullOrUndefined(
      NodeTraversal t, Node n, JSType type, String msg, JSType expectedType) {
    if (!type.isNoType() && !type.isUnknownType()
        && type.isSubtype(nullOrUndefined)
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
      if (n.isGetProp() &&
          !t.inGlobalScope() && type.isNullType()) {
        return true;
      }

      mismatch(t, n, msg, type, expectedType);
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

  /**
   * Expect that the type of a switch condition matches the type of its
   * case condition.
   */
  void expectSwitchMatchesCase(NodeTraversal t, Node n, JSType switchType,
      JSType caseType) {
    // ECMA-262, page 68, step 3 of evaluation of CaseBlock,
    // but allowing extra autoboxing.
    // TODO(user): remove extra conditions when type annotations
    // in the code base have adapted to the change in the compiler.
    if (!switchType.canTestForShallowEqualityWith(caseType) &&
        (caseType.autoboxesTo() == null ||
        !caseType.autoboxesTo().isSubtype(switchType))) {
      mismatch(t, n.getFirstChild(),
          "case expression doesn't match switch",
          caseType, switchType);
    } else if (!switchType.canTestForShallowEqualityWith(caseType)
        && (caseType.autoboxesTo() == null
        || !caseType.autoboxesTo()
        .isSubtypeWithoutStructuralTyping(switchType))) {
      TypeMismatch.recordImplicitInterfaceUses(this.implicitInterfaceUses, n, caseType, switchType);
      TypeMismatch.recordImplicitUseOfNativeObject(this.mismatches, n, caseType, switchType);
    }
  }

  /**
   * Expect that the first type can be addressed with GETELEM syntax,
   * and that the second type is the right type for an index into the
   * first type.
   *
   * @param t The node traversal.
   * @param n The GETELEM node to issue warnings on.
   * @param objType The type of the left side of the GETELEM.
   * @param indexType The type inside the brackets of the GETELEM.
   */
  void expectIndexMatch(NodeTraversal t, Node n, JSType objType,
                        JSType indexType) {
    Preconditions.checkState(n.isGetElem(), n);
    Node indexNode = n.getLastChild();
    if (objType.isStruct() && !indexType.isSubtype(getNativeType(SYMBOL_TYPE))) {
      report(JSError.make(indexNode,
                          ILLEGAL_PROPERTY_ACCESS, "'[]'", "struct"));
    }
    if (objType.isUnknownType()) {
      expectStringOrNumberOrSymbol(t, indexNode, indexType, "property access");
    } else {
      ObjectType dereferenced = objType.dereference();
      if (dereferenced != null && dereferenced
          .getTemplateTypeMap()
          .hasTemplateKey(typeRegistry.getObjectIndexKey())) {
        expectCanAssignTo(t, indexNode, indexType, dereferenced
            .getTemplateTypeMap().getResolvedTemplateType(typeRegistry.getObjectIndexKey()),
            "restricted index type");
      } else if (dereferenced != null && dereferenced.isArrayType()) {
        expectNumber(t, indexNode, indexType, "array access");
      } else if (objType.matchesObjectContext()) {
        expectStringOrSymbol(t, indexNode, indexType, "property access");
      } else {
        mismatch(t, n, "only arrays or objects can be accessed",
            objType,
            typeRegistry.createUnionType(ARRAY_TYPE, OBJECT_TYPE));
      }
    }
  }

  /**
   * Expect that the first type can be assigned to a symbol of the second
   * type.
   *
   * @param t The node traversal.
   * @param n The node to issue warnings on.
   * @param rightType The type on the RHS of the assign.
   * @param leftType The type of the symbol on the LHS of the assign.
   * @param owner The owner of the property being assigned to.
   * @param propName The name of the property being assigned to.
   * @return True if the types matched, false otherwise.
   */
  boolean expectCanAssignToPropertyOf(NodeTraversal t, Node n, JSType rightType,
      JSType leftType, Node owner, String propName) {
    // The NoType check is a hack to make typedefs work OK.
    if (!leftType.isNoType() && !rightType.isSubtype(leftType)) {
      // Do not type-check interface methods, because we expect that
      // they will have dummy implementations that do not match the type
      // annotations.
      JSType ownerType = getJSType(owner);
      if (ownerType.isFunctionPrototypeType()) {
        FunctionType ownerFn = ownerType.toObjectType().getOwnerFunction();
        if (ownerFn.isInterface()
            && rightType.isFunctionType() && leftType.isFunctionType()) {
          return true;
        }
      }

      mismatch(t, n,
          "assignment to property " + propName + " of " +
          typeRegistry.getReadableTypeName(owner),
          rightType, leftType);
      return false;
    } else if (!leftType.isNoType() && !rightType.isSubtypeWithoutStructuralTyping(leftType)){
      TypeMismatch.recordImplicitInterfaceUses(this.implicitInterfaceUses, n, rightType, leftType);
      TypeMismatch.recordImplicitUseOfNativeObject(this.mismatches, n, rightType, leftType);
    }
    return true;
  }

  /**
   * Expect that the first type can be assigned to a symbol of the second
   * type.
   *
   * @param t The node traversal.
   * @param n The node to issue warnings on.
   * @param rightType The type on the RHS of the assign.
   * @param leftType The type of the symbol on the LHS of the assign.
   * @param msg An extra message for the mismatch warning, if necessary.
   * @return True if the types matched, false otherwise.
   */
  boolean expectCanAssignTo(NodeTraversal t, Node n, JSType rightType,
      JSType leftType, String msg) {
    if (!rightType.isSubtype(leftType)) {
      mismatch(t, n, msg, rightType, leftType);
      return false;
    } else if (!rightType.isSubtypeWithoutStructuralTyping(leftType)) {
      TypeMismatch.recordImplicitInterfaceUses(this.implicitInterfaceUses, n, rightType, leftType);
      TypeMismatch.recordImplicitUseOfNativeObject(this.mismatches, n, rightType, leftType);
    }
    return true;
  }

  /**
   * Expect that the type of an argument matches the type of the parameter
   * that it's fulfilling.
   *
   * @param t The node traversal.
   * @param n The node to issue warnings on.
   * @param argType The type of the argument.
   * @param paramType The type of the parameter.
   * @param callNode The call node, to help with the warning message.
   * @param ordinal The argument ordinal, to help with the warning message.
   */
  void expectArgumentMatchesParameter(NodeTraversal t, Node n, JSType argType,
      JSType paramType, Node callNode, int ordinal) {
    if (!argType.isSubtype(paramType)) {
      mismatch(t, n,
          SimpleFormat.format("actual parameter %d of %s does not match " +
              "formal parameter", ordinal,
              typeRegistry.getReadableTypeNameNoDeref(callNode.getFirstChild())),
          argType, paramType);
    } else if (!argType.isSubtypeWithoutStructuralTyping(paramType)){
      TypeMismatch.recordImplicitInterfaceUses(this.implicitInterfaceUses, n, argType, paramType);
      TypeMismatch.recordImplicitUseOfNativeObject(this.mismatches, n, argType, paramType);
    }
  }

  /**
   * Expect that the first type is the direct superclass of the second type.
   *
   * @param t The node traversal.
   * @param n The node where warnings should point to.
   * @param superObject The expected super instance type.
   * @param subObject The sub instance type.
   */
  void expectSuperType(NodeTraversal t, Node n, ObjectType superObject,
      ObjectType subObject) {
    FunctionType subCtor = subObject.getConstructor();
    ObjectType implicitProto = subObject.getImplicitPrototype();
    ObjectType declaredSuper =
        implicitProto == null ? null : implicitProto.getImplicitPrototype();
    if (declaredSuper != null && declaredSuper.isTemplatizedType()) {
      declaredSuper =
          declaredSuper.toMaybeTemplatizedType().getReferencedType();
    }
    if (declaredSuper != null &&
        !(superObject instanceof UnknownType) &&
        !declaredSuper.isEquivalentTo(superObject)) {
      if (declaredSuper.isEquivalentTo(getNativeType(OBJECT_TYPE))) {
        TypeMismatch.registerMismatch(this.mismatches, this.implicitInterfaceUses,
            superObject, declaredSuper,
            report(t.makeError(n, MISSING_EXTENDS_TAG_WARNING, subObject.toString())));
      } else {
        mismatch(n, "mismatch in declaration of superclass type",
            superObject, declaredSuper);
      }

      // Correct the super type.
      if (!subCtor.hasCachedValues()) {
        subCtor.setPrototypeBasedOn(superObject);
      }
    }
  }

  /**
   * Expect that the first type can be cast to the second type. The first type
   * must have some relationship with the second.
   *
   * @param t The node traversal.
   * @param n The node where warnings should point.
   * @param targetType The type being cast to.
   * @param sourceType The type being cast from.
   */
  void expectCanCast(NodeTraversal t, Node n, JSType targetType, JSType sourceType) {
    if (!sourceType.canCastTo(targetType)) {
      TypeMismatch.registerMismatch(
          this.mismatches, this.implicitInterfaceUses, sourceType, targetType,
          report(t.makeError(n, INVALID_CAST, sourceType.toString(), targetType.toString())));
    } else if (!sourceType.isSubtypeWithoutStructuralTyping(targetType)){
      TypeMismatch.recordImplicitInterfaceUses(
          this.implicitInterfaceUses, n, sourceType, targetType);
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
    boolean allowDupe = false;
    if (n.isGetProp() || NodeUtil.isObjectLitKey(n) || NodeUtil.isNameDeclaration(n.getParent())) {
      JSDocInfo info = n.getJSDocInfo();
      if (info == null) {
        info = parent.getJSDocInfo();
      }
      allowDupe =
          info != null && info.getSuppressions().contains("duplicate");
    }

    JSType varType = var.getType();

    // Only report duplicate declarations that have types. Other duplicates
    // will be reported by the syntactic scope creator later in the
    // compilation process.
    if (varType != null &&
        varType != typeRegistry.getNativeType(UNKNOWN_TYPE) &&
        newType != null &&
        newType != typeRegistry.getNativeType(UNKNOWN_TYPE)) {
      // If there are two typed declarations of the same variable, that
      // is an error and the second declaration is ignored, except in the
      // case of native types. A null input type means that the declaration
      // was made in TypedScopeCreator#createInitialScope and is a
      // native type. We should redeclare it at the new input site.
      if (var.input == null) {
        TypedScope s = var.getScope();
        s.undeclare(var);
        newVar = s.declare(variableName, n, varType, input, false);

        n.setJSType(varType);
        if (parent.isVar()) {
          if (n.getFirstChild() != null) {
            n.getFirstChild().setJSType(varType);
          }
        } else {
          Preconditions.checkState(parent.isFunction());
          parent.setJSType(varType);
        }
      } else {
        // Always warn about duplicates if the overridden type does not
        // match the original type.
        //
        // If the types match, suppress the warning iff there was a @suppress
        // tag, or if the original declaration was a stub.
        if (!(allowDupe ||
              var.getParentNode().isExprResult()) ||
            !newType.isEquivalentTo(varType)) {

          if (newType.isEquivalentTo(varType)) {
            report(JSError.make(n, DUP_VAR_DECLARATION,
                variableName, var.getInputName(),
                String.valueOf(var.nameNode.getLineno())));
          } else {
            report(JSError.make(n, DUP_VAR_DECLARATION_TYPE_MISMATCH,
                variableName, newType.toString(), var.getInputName(),
                String.valueOf(var.nameNode.getLineno()),
                varType.toString()));
          }
        }
      }
    }

    return newVar;
  }

  /**
   * Expect that all properties on interfaces that this type implements are
   * implemented and correctly typed.
   */
  void expectAllInterfaceProperties(NodeTraversal t, Node n,
      FunctionType type) {
    ObjectType instance = type.getInstanceType();
    for (ObjectType implemented : type.getAllImplementedInterfaces()) {
      if (implemented.getImplicitPrototype() != null) {
        for (String prop :
             implemented.getImplicitPrototype().getOwnPropertyNames()) {
          expectInterfaceProperty(t, n, instance, implemented, prop);
        }
      }
    }
  }

  /**
   * Expect that the property in an interface that this type implements is
   * implemented and correctly typed.
   */
  private void expectInterfaceProperty(NodeTraversal t, Node n,
      ObjectType instance, ObjectType implementedInterface, String prop) {
    StaticTypedSlot<JSType> propSlot = instance.getSlot(prop);
    if (propSlot == null) {
      // Not implemented
      String sourceName = n.getSourceFileName();
      sourceName = nullToEmpty(sourceName);
      TypeMismatch.registerMismatch(
          this.mismatches,
          this.implicitInterfaceUses,
          instance,
          implementedInterface,
          report(
              JSError.make(
                  n,
                  INTERFACE_METHOD_NOT_IMPLEMENTED,
                  prop,
                  implementedInterface.toString(),
                  instance.toString())));
    } else {
      Node propNode = propSlot.getDeclaration() == null ?
          null : propSlot.getDeclaration().getNode();

      // Fall back on the constructor node if we can't find a node for the
      // property.
      propNode = propNode == null ? n : propNode;

      JSType found = propSlot.getType();
      found = found.restrictByNotNullOrUndefined();

      JSType required
          = implementedInterface.getImplicitPrototype().getPropertyType(prop);
      TemplateTypeMap typeMap = implementedInterface.getTemplateTypeMap();
      if (!typeMap.isEmpty()) {
        TemplateTypeMapReplacer replacer = new TemplateTypeMapReplacer(
            typeRegistry, typeMap);
        required = required.visit(replacer);
      }
      required = required.restrictByNotNullOrUndefined();

      if (!found.isSubtype(required, this.subtypingMode)) {
        // Implemented, but not correctly typed
        FunctionType constructor =
            implementedInterface.toObjectType().getConstructor();
        JSError err =
            t.makeError(
                propNode,
                HIDDEN_INTERFACE_PROPERTY_MISMATCH,
                prop,
                instance.toString(),
                constructor.getTopMostDefiningType(prop).toString(),
                required.toString(),
                found.toString());
        TypeMismatch.registerMismatch(
            this.mismatches, this.implicitInterfaceUses, found, required, err);
        report(err);
      }
    }
  }

  /**
   * For a concrete class, expect that all abstract methods that haven't been implemented by any of
   * the super classes on the inheritance chain are implemented.
   */
  void expectAbstractMethodsImplemented(Node n, FunctionType ctorType) {
    Preconditions.checkArgument(ctorType.isConstructor());

    Map<String, ObjectType> abstractMethodSuperTypeMap = new LinkedHashMap<>();
    FunctionType currSuperCtor = ctorType.getSuperClassConstructor();
    if (currSuperCtor == null || !currSuperCtor.isAbstract()) {
      return;
    }

    while (currSuperCtor != null && currSuperCtor.isAbstract()) {
      ObjectType superType = currSuperCtor.getInstanceType();
      for (String prop :
          currSuperCtor.getInstanceType().getImplicitPrototype().getOwnPropertyNames()) {
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
        String sourceName = n.getSourceFileName();
        sourceName = nullToEmpty(sourceName);
        TypeMismatch.registerMismatch(
            this.mismatches,
            this.implicitInterfaceUses,
            instance,
            superType,
            report(
                JSError.make(
                    n,
                    ABSTRACT_METHOD_NOT_IMPLEMENTED,
                    method,
                    superType.toString(),
                    instance.toString())));
      }
    }
  }

  /** Report a type mismatch */
  private void mismatch(NodeTraversal unusedT, Node n, String msg, JSType found, JSType required) {
    mismatch(n, msg, found, required);
  }

  private void mismatch(NodeTraversal t, Node n, String msg, JSType found, JSTypeNative required) {
    mismatch(t, n, msg, found, getNativeType(required));
  }

  private void mismatch(Node n, String msg, JSType found, JSType required) {
    if (!found.isSubtype(required, this.subtypingMode)) {
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
                if (!foundObject.getPropertyType(property).isSubtype(propRequired, subtypingMode)) {
                  mismatch.add(property);
                }
              } else {
                missing.add(property);
              }
            }
          }
        }
      }
      JSError err =
          JSError.make(
              n,
              TYPE_MISMATCH_WARNING,
              formatFoundRequired(msg, found, required, missing, mismatch));
      TypeMismatch.registerMismatch(
          this.mismatches, this.implicitInterfaceUses, found, required, err);
      report(err);
    }
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
      foundStr = found.toAnnotationString();
      requiredStr = required.toAnnotationString();
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
    JSType jsType = n.getJSType();
    if (jsType == null) {
      // TODO(user): This branch indicates a compiler bug, not worthy of
      // halting the compilation but we should log this and analyze to track
      // down why it happens. This is not critical and will be resolved over
      // time as the type checker is extended.
      return getNativeType(UNKNOWN_TYPE);
    } else {
      return jsType;
    }
  }

  private JSType getNativeType(JSTypeNative typeId) {
    return typeRegistry.getNativeType(typeId);
  }

  private JSError report(JSError error) {
    compiler.report(error);
    return error;
  }
}
