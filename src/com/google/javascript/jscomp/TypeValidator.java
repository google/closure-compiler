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

import static com.google.javascript.rhino.jstype.JSTypeNative.ARRAY_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NO_OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NULL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_STRING;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.VOID_TYPE;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;

import java.text.MessageFormat;
import java.util.Iterator;
import java.util.List;

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
class TypeValidator {

  private final AbstractCompiler compiler;
  private final JSTypeRegistry typeRegistry;
  private final JSType allValueTypes;
  private boolean shouldReport = true;

  // TODO(nicksantos): Provide accessors to better filter the list of type
  // mismatches. For example, if we pass (Cake|null) where only Cake is
  // allowed, that doesn't mean we should invalidate all Cakes.
  private final List<TypeMismatch> mismatches = Lists.newArrayList();

  // User warnings
  private static final String FOUND_REQUIRED =
      "{0}\n" +
      "found   : {1}\n" +
      "required: {2}";

  static final DiagnosticType INVALID_CAST =
      DiagnosticType.warning("JSC_INVALID_CAST",
          "invalid cast - must be a subtype or supertype\n" +
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
          "variable {0} redefined with type {1}, " +
          "original definition at {2}:{3} with type {4}");

  static final DiagnosticType HIDDEN_PROPERTY_MISMATCH =
      DiagnosticType.warning("JSC_HIDDEN_PROPERTY_MISMATCH",
          "mismatch of the {0} property type and the type " +
          "of the property it overrides from superclass {1}\n" +
          "original: {2}\n" +
          "override: {3}");

  static final DiagnosticType INTERFACE_METHOD_NOT_IMPLEMENTED =
      DiagnosticType.warning(
          "JSC_INTERFACE_METHOD_NOT_IMPLEMENTED",
          "property {0} on interface {1} is not implemented by type {2}");

  static final DiagnosticGroup ALL_DIAGNOSTICS = new DiagnosticGroup(
      INVALID_CAST,
      TYPE_MISMATCH_WARNING,
      MISSING_EXTENDS_TAG_WARNING,
      DUP_VAR_DECLARATION,
      HIDDEN_PROPERTY_MISMATCH,
      INTERFACE_METHOD_NOT_IMPLEMENTED);

  TypeValidator(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.typeRegistry = compiler.getTypeRegistry();
    this.allValueTypes = typeRegistry.createUnionType(
        STRING_TYPE, NUMBER_TYPE, BOOLEAN_TYPE, NULL_TYPE, VOID_TYPE);
  }

  /**
   * Gets a list of type violations.
   *
   * For each violation, one element is the expected type and the other is
   * the type that is actually found. Order is not signficant.
   */
  Iterable<TypeMismatch> getMismatches() {
    return mismatches;
  }

  void setShouldReport(boolean report) {
    this.shouldReport = report;
  }

  // All non-private methods should have the form:
  // expectCondition(NodeTraversal t, Node n, ...);
  // If there is a mismatch, the {@code expect} method should issue
  // a warning and attempt to correct the mismatch, when possible.

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
    if (!anyObjectType.isSubtype(type)) {
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
   * Expect the type to be a number or string, or a type convertible to a number
   * or string. If the expectation is not met, issue a warning at the provided
   * node's source code position.
   */
  void expectStringOrNumber(
      NodeTraversal t, Node n, JSType type, String msg) {
    if (!type.matchesNumberContext() && !type.matchesStringContext()) {
      mismatch(t, n, msg, type, NUMBER_STRING);
    }
  }

  /**
   * Expect the type to be anything but the void type. If the expectation is not
   * met, issue a warning at the provided node's source code position. Note that
   * a union type that includes the void type and at least one other type meets
   * the expectation.
   * @return Whether the expectation was met.
   */
  boolean expectNotVoid(
      NodeTraversal t, Node n, JSType type, String msg, JSType expectedType) {
    if (type.isVoidType()) {
      mismatch(t, n, msg, type, expectedType);
      return false;
    }
    return true;
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
    }
  }

  /**
   * Expect that the first type can be addressed with GETELEM syntax,
   * and that the second type is the right type for an index into the
   * first type.
   *
   * @param t The node traversal.
   * @param n The node to issue warnings on.
   * @param objType The type of the left side of the GETELEM.
   * @param indexType The type inside the brackets of the GETELEM.
   */
  void expectIndexMatch(NodeTraversal t, Node n, JSType objType,
      JSType indexType) {
    if (objType.isUnknownType()) {
      expectStringOrNumber(t, n, indexType, "property access");
    } else if (objType.toObjectType() != null &&
        objType.toObjectType().getIndexType() != null) {
      expectCanAssignTo(t, n, indexType, objType.toObjectType().getIndexType(),
          "restricted index type");
    } else if (objType.isArrayType()) {
      expectNumber(t, n, indexType, "array access");
    } else if (objType.matchesObjectContext()) {
      expectString(t, n, indexType, "property access");
    } else {
      mismatch(t, n, "only arrays or objects can be accessed",
          objType, typeRegistry.createUnionType(ARRAY_TYPE, OBJECT_TYPE));
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
    // The NoType check is a hack to make typedefs work ok.
    if (!leftType.isNoType() && !rightType.canAssignTo(leftType)) {
      if (bothIntrinsics(rightType, leftType)) {
        // We have a superior warning for this mistake, which gives you
        // the line numbers of both types.
        registerMismatch(rightType, leftType);
      } else {
        mismatch(t, n,
            "assignment to property " + propName + " of " +
            getReadableJSTypeName(owner, true),
            rightType, leftType);
      }
      return false;
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
    if (!rightType.canAssignTo(leftType)) {
      if (bothIntrinsics(rightType, leftType)) {
        // We have a superior warning for this mistake, which gives you
        // the line numbers of both types.
        registerMismatch(rightType, leftType);
      } else {
        mismatch(t, n, msg, rightType, leftType);
      }
      return false;
    }
    return true;
  }

  private boolean bothIntrinsics(JSType rightType, JSType leftType) {
    return (leftType.isConstructor() || leftType.isEnumType()) &&
        (rightType.isConstructor() || rightType.isEnumType());
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
    if (!argType.canAssignTo(paramType)) {
      mismatch(t, n,
          String.format("actual parameter %d of %s does not match " +
              "formal parameter", ordinal,
              getReadableJSTypeName(callNode.getFirstChild(), false)),
          argType, paramType);
    }
  }

  /**
   * Expect that the first type can override a property of the second
   * type.
   *
   * @param t The node traversal.
   * @param n The node to issue warnings on.
   * @param overridingType The overriding type.
   * @param hiddenType The type of the property being overridden.
   * @param propertyName The name of the property, for use in the
   *     warning message.
   * @param ownerType The type of the owner of the property, for use
   *     in the warning message.
   */
  void expectCanOverride(NodeTraversal t, Node n, JSType overridingType,
      JSType hiddenType, String propertyName, JSType ownerType) {
    if (!overridingType.canAssignTo(hiddenType)) {
      registerMismatch(overridingType, hiddenType);
      if (shouldReport) {
        compiler.report(
            t.makeError(n, HIDDEN_PROPERTY_MISMATCH,
                propertyName, ownerType.toString(),
                hiddenType.toString(), overridingType.toString()));
      }
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
    ObjectType declaredSuper =
        subObject.getImplicitPrototype().getImplicitPrototype();
    if (!declaredSuper.equals(superObject)) {
      if (declaredSuper.equals(getNativeType(OBJECT_TYPE))) {
        if (shouldReport) {
          compiler.report(
              t.makeError(n, MISSING_EXTENDS_TAG_WARNING,
                  subObject.toString()));
        }
        registerMismatch(superObject, declaredSuper);
      } else {
        mismatch(t.getSourceName(), n,
            "mismatch in declaration of superclass type",
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
   * should be either a subtype or supertype of the second.
   *
   * @param t The node traversal.
   * @param n The node where warnings should point.
   * @param type The type being cast from.
   * @param castType The type being cast to.
   */
  void expectCanCast(NodeTraversal t, Node n, JSType type, JSType castType) {
    castType = castType.restrictByNotNullOrUndefined();
    type = type.restrictByNotNullOrUndefined();

    if (!type.canAssignTo(castType) && !castType.canAssignTo(type)) {
      if (shouldReport) {
        compiler.report(
            t.makeError(n, INVALID_CAST,
                castType.toString(), type.toString()));
      }
      registerMismatch(type, castType);
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
   */
  void expectUndeclaredVariable(String sourceName, Node n, Node parent, Var var,
      String variableName, JSType newType) {
    boolean allowDupe = false;
    if (n.getType() == Token.GETPROP) {
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
      // native type.
      if (var.input == null) {
        n.setJSType(varType);
        if (parent.getType() == Token.VAR) {
          if (n.getFirstChild() != null) {
            n.getFirstChild().setJSType(varType);
          }
        } else {
          Preconditions.checkState(parent.getType() == Token.FUNCTION);
          parent.setJSType(varType);
        }
      } else {
        // Always warn about duplicates if the overridden type does not
        // match the original type.
        //
        // If the types match, suppress the warning iff there was a @suppress
        // tag, or if the original declaration was a stub.
        if (!(allowDupe ||
              var.getParentNode().getType() == Token.EXPR_RESULT) ||
            !newType.equals(varType)) {
          if (shouldReport) {
            compiler.report(
                JSError.make(sourceName, n, DUP_VAR_DECLARATION,
                    variableName, newType.toString(), var.getInputName(),
                    String.valueOf(var.nameNode.getLineno()),
                    varType.toString()));
          }
        }
      }
    }
  }

  /**
   * Expect that all properties on interfaces that this type implements are
   * implemented.
   */
  void expectAllInterfacePropertiesImplemented(FunctionType type) {
    ObjectType instance = type.getInstanceType();
    for (ObjectType implemented : type.getAllImplementedInterfaces()) {
      if (implemented.getImplicitPrototype() != null) {
        for (String prop :
            implemented.getImplicitPrototype().getOwnPropertyNames()) {
          if (!instance.hasProperty(prop)) {
            Node source = type.getSource();
            Preconditions.checkNotNull(source);
            String sourceName = (String) source.getProp(Node.SOURCENAME_PROP);
            sourceName = sourceName == null ? "" : sourceName;
            if (shouldReport) {
              compiler.report(JSError.make(sourceName, source,
                  INTERFACE_METHOD_NOT_IMPLEMENTED,
                  prop, implemented.toString(), instance.toString()));
            }
            registerMismatch(instance, implemented);
          }
        }
      }
    }
  }

  /**
   * Report a type mismatch
   */
  private void mismatch(NodeTraversal t, Node n,
                        String msg, JSType found, JSType required) {
    mismatch(t.getSourceName(), n, msg, found, required);
  }

  private void mismatch(NodeTraversal t, Node n,
                        String msg, JSType found, JSTypeNative required) {
    mismatch(t, n, msg, found, getNativeType(required));
  }

  private void mismatch(String sourceName, Node n,
                        String msg, JSType found, JSType required) {
    registerMismatch(found, required);
    if (shouldReport) {
      compiler.report(
          JSError.make(sourceName, n, TYPE_MISMATCH_WARNING,
                       formatFoundRequired(msg, found, required)));
    }
  }

  private void registerMismatch(JSType found, JSType required) {
    // Don't register a mismatch for differences in null or undefined or if the
    // code didn't downcast.
    found = found.restrictByNotNullOrUndefined();
    required = required.restrictByNotNullOrUndefined();
    if (found.canAssignTo(required) || required.canAssignTo(found)) {
      return;
    }

    mismatches.add(new TypeMismatch(found, required));
    if (found instanceof FunctionType &&
        required instanceof FunctionType) {
      FunctionType fnTypeA = ((FunctionType) found);
      FunctionType fnTypeB = ((FunctionType) required);
      Iterator<Node> paramItA = fnTypeA.getParameters().iterator();
      Iterator<Node> paramItB = fnTypeB.getParameters().iterator();
      while (paramItA.hasNext() && paramItB.hasNext()) {
        registerIfMismatch(paramItA.next().getJSType(),
            paramItB.next().getJSType());
      }

      registerIfMismatch(fnTypeA.getReturnType(), fnTypeB.getReturnType());
    }
  }

  private void registerIfMismatch(JSType found, JSType required) {
    if (found != null && required != null &&
        !found.canAssignTo(required)) {
      registerMismatch(found, required);
    }
  }

  /**
   * Formats a found/required error message.
   */
  private String formatFoundRequired(String description, JSType found,
      JSType required) {
    return MessageFormat.format(FOUND_REQUIRED, description, found, required);
  }

  /**
   * Given a node, get a human-readable name for the type of that node so
   * that will be easy for the programmer to find the original declaration.
   *
   * For example, if SubFoo's property "bar" might have the human-readable
   * name "Foo.prototype.bar".
   *
   * @param n The node.
   * @param dereference If true, the type of the node will be dereferenced
   *     to an Object type, if possible.
   */
  String getReadableJSTypeName(Node n, boolean dereference) {
    // If we're analyzing a GETPROP, the property may be inherited by the
    // prototype chain. So climb the prototype chain and find out where
    // the property was originally defined.
    if (n.getType() == Token.GETPROP) {
      ObjectType objectType = getJSType(n.getFirstChild()).dereference();
      if (objectType != null) {
        String propName = n.getLastChild().getString();
        while (objectType != null && !objectType.hasOwnProperty(propName)) {
          objectType = objectType.getImplicitPrototype();
        }

        // Don't show complex function names or anonymous types.
        // Instead, try to get a human-readable type name.
        if (objectType != null &&
            (objectType.getConstructor() != null ||
             objectType.isFunctionPrototypeType())) {
          return objectType.toString() + "." + propName;
        }
      }
    }

    JSType type = getJSType(n);
    if (dereference) {
      ObjectType dereferenced = type.dereference();
      if (dereferenced != null) {
        type = dereferenced;
      }
    }

    String qualifiedName = n.getQualifiedName();
    if (type.isFunctionPrototypeType() ||
        (type.toObjectType() != null &&
         type.toObjectType().getConstructor() != null)) {
      return type.toString();
    } else if (qualifiedName != null) {
      return qualifiedName;
    } else if (type instanceof FunctionType) {
      // Don't show complex function names.
      return "function";
    } else {
      return type.toString();
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

  /**
   * Signals that the first type and the second type have been
   * used interchangeably.
   *
   * Type-based optimizations should take this into account
   * so that they don't wreck code with type warnings.
   */
  static class TypeMismatch {
    final JSType typeA;
    final JSType typeB;

    /**
     * It's the responsibility of the class that creates the
     * {@code TypeMismatch} to ensure that {@code a} and {@code b} are
     * non-matching types.
     */
    TypeMismatch(JSType a, JSType b) {
      this.typeA = a;
      this.typeB = b;
    }

    @Override public boolean equals(Object object) {
      if (object instanceof TypeMismatch) {
        TypeMismatch that = (TypeMismatch) object;
        return (that.typeA.equals(this.typeA) && that.typeB.equals(this.typeB))
            || (that.typeB.equals(this.typeA) && that.typeA.equals(this.typeB));
      }
      return false;
    }

    @Override public int hashCode() {
      return Objects.hashCode(typeA, typeB);
    }

    @Override public String toString() {
      return "(" + typeA + ", " + typeB + ")";
    }
  }
}
