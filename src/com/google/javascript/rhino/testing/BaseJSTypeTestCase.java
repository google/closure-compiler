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

package com.google.javascript.rhino.testing;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;
import static com.google.javascript.rhino.testing.TypeSubject.types;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionBuilder;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.RecordTypeBuilder;
import com.google.javascript.rhino.jstype.TemplatizedType;
import junit.framework.TestCase;
import org.junit.Before;

public abstract class BaseJSTypeTestCase extends TestCase {
  protected static final String FORWARD_DECLARED_TYPE_NAME = "forwardDeclared";

  protected static final Joiner LINE_JOINER = Joiner.on('\n');

  protected JSTypeRegistry registry;
  protected TestErrorReporter errorReporter;

  protected JSType ALL_TYPE;
  protected ObjectType NO_OBJECT_TYPE;
  protected ObjectType NO_TYPE;
  protected ObjectType NO_RESOLVED_TYPE;
  protected FunctionType ARRAY_FUNCTION_TYPE;
  protected ObjectType ARRAY_TYPE;
  protected JSType BOOLEAN_OBJECT_FUNCTION_TYPE;
  protected ObjectType BOOLEAN_OBJECT_TYPE;
  protected JSType BOOLEAN_TYPE;
  protected ObjectType CHECKED_UNKNOWN_TYPE;
  protected JSType DATE_FUNCTION_TYPE;
  protected ObjectType DATE_TYPE;
  protected FunctionType FUNCTION_FUNCTION_TYPE;
  protected FunctionType FUNCTION_INSTANCE_TYPE;
  protected ObjectType FUNCTION_PROTOTYPE;
  protected JSType GREATEST_FUNCTION_TYPE;
  protected JSType LEAST_FUNCTION_TYPE;
  protected JSType NULL_TYPE;
  protected JSType NUMBER_OBJECT_FUNCTION_TYPE;
  protected ObjectType NUMBER_OBJECT_TYPE;
  protected JSType NUMBER_STRING;
  protected JSType NUMBER_STRING_BOOLEAN;
  protected JSType NUMBER_TYPE;
  protected FunctionType OBJECT_FUNCTION_TYPE;
  protected JSType NULL_VOID;
  protected JSType OBJECT_NUMBER_STRING;
  protected JSType OBJECT_NUMBER_STRING_BOOLEAN;
  protected JSType OBJECT_PROTOTYPE;
  protected ObjectType OBJECT_TYPE;
  protected JSType REGEXP_FUNCTION_TYPE;
  protected ObjectType REGEXP_TYPE;
  protected JSType STRING_OBJECT_FUNCTION_TYPE;
  protected ObjectType STRING_OBJECT_TYPE;
  protected JSType STRING_TYPE;
  protected ObjectType SYMBOL_OBJECT_TYPE;
  protected JSType SYMBOL_TYPE;
  protected FunctionType U2U_CONSTRUCTOR_TYPE;
  protected FunctionType U2U_FUNCTION_TYPE;
  protected ObjectType UNKNOWN_TYPE;
  protected JSType VOID_TYPE;

  protected int NATIVE_PROPERTIES_COUNT;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    errorReporter = new TestErrorReporter(null, null);
    registry = new JSTypeRegistry(errorReporter, ImmutableSet.of(FORWARD_DECLARED_TYPE_NAME));
    initTypes();
  }

  protected void initTypes() {
    ALL_TYPE = registry.getNativeType(JSTypeNative.ALL_TYPE);
    NO_OBJECT_TYPE = registry.getNativeObjectType(JSTypeNative.NO_OBJECT_TYPE);
    NO_TYPE = registry.getNativeObjectType(JSTypeNative.NO_TYPE);
    NO_RESOLVED_TYPE = registry.getNativeObjectType(JSTypeNative.NO_RESOLVED_TYPE);
    ARRAY_FUNCTION_TYPE = registry.getNativeFunctionType(JSTypeNative.ARRAY_FUNCTION_TYPE);
    ARRAY_TYPE = registry.getNativeObjectType(JSTypeNative.ARRAY_TYPE);
    BOOLEAN_OBJECT_FUNCTION_TYPE =
        registry.getNativeType(JSTypeNative.BOOLEAN_OBJECT_FUNCTION_TYPE);
    BOOLEAN_OBJECT_TYPE = registry.getNativeObjectType(JSTypeNative.BOOLEAN_OBJECT_TYPE);
    BOOLEAN_TYPE = registry.getNativeType(JSTypeNative.BOOLEAN_TYPE);
    CHECKED_UNKNOWN_TYPE = registry.getNativeObjectType(JSTypeNative.CHECKED_UNKNOWN_TYPE);
    DATE_FUNCTION_TYPE = registry.getNativeType(JSTypeNative.DATE_FUNCTION_TYPE);
    DATE_TYPE = registry.getNativeObjectType(JSTypeNative.DATE_TYPE);
    FUNCTION_FUNCTION_TYPE = registry.getNativeFunctionType(JSTypeNative.FUNCTION_FUNCTION_TYPE);
    FUNCTION_INSTANCE_TYPE = registry.getNativeFunctionType(JSTypeNative.FUNCTION_INSTANCE_TYPE);
    FUNCTION_PROTOTYPE = registry.getNativeObjectType(JSTypeNative.FUNCTION_PROTOTYPE);
    GREATEST_FUNCTION_TYPE = registry.getNativeType(JSTypeNative.GREATEST_FUNCTION_TYPE);
    LEAST_FUNCTION_TYPE = registry.getNativeType(JSTypeNative.LEAST_FUNCTION_TYPE);
    NULL_TYPE = registry.getNativeType(JSTypeNative.NULL_TYPE);
    NUMBER_OBJECT_FUNCTION_TYPE = registry.getNativeType(JSTypeNative.NUMBER_OBJECT_FUNCTION_TYPE);
    NUMBER_OBJECT_TYPE = registry.getNativeObjectType(JSTypeNative.NUMBER_OBJECT_TYPE);
    NUMBER_STRING = registry.getNativeType(JSTypeNative.NUMBER_STRING);
    NUMBER_STRING_BOOLEAN = registry.getNativeType(JSTypeNative.NUMBER_STRING_BOOLEAN);
    NUMBER_TYPE = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    OBJECT_FUNCTION_TYPE = registry.getNativeFunctionType(JSTypeNative.OBJECT_FUNCTION_TYPE);
    NULL_VOID = registry.getNativeType(JSTypeNative.NULL_VOID);
    OBJECT_NUMBER_STRING = registry.getNativeType(JSTypeNative.OBJECT_NUMBER_STRING);
    OBJECT_NUMBER_STRING_BOOLEAN =
        registry.getNativeType(JSTypeNative.OBJECT_NUMBER_STRING_BOOLEAN);
    OBJECT_PROTOTYPE = registry.getNativeType(JSTypeNative.OBJECT_PROTOTYPE);
    OBJECT_TYPE = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    REGEXP_FUNCTION_TYPE = registry.getNativeType(JSTypeNative.REGEXP_FUNCTION_TYPE);
    REGEXP_TYPE = registry.getNativeObjectType(JSTypeNative.REGEXP_TYPE);
    STRING_OBJECT_FUNCTION_TYPE = registry.getNativeType(JSTypeNative.STRING_OBJECT_FUNCTION_TYPE);
    STRING_OBJECT_TYPE = registry.getNativeObjectType(JSTypeNative.STRING_OBJECT_TYPE);
    STRING_TYPE = registry.getNativeType(JSTypeNative.STRING_TYPE);
    SYMBOL_OBJECT_TYPE = registry.getNativeObjectType(JSTypeNative.SYMBOL_OBJECT_TYPE);
    SYMBOL_TYPE = registry.getNativeType(JSTypeNative.SYMBOL_TYPE);
    U2U_CONSTRUCTOR_TYPE = registry.getNativeFunctionType(JSTypeNative.U2U_CONSTRUCTOR_TYPE);
    U2U_FUNCTION_TYPE = registry.getNativeFunctionType(JSTypeNative.U2U_FUNCTION_TYPE);
    UNKNOWN_TYPE = registry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE);
    VOID_TYPE = registry.getNativeType(JSTypeNative.VOID_TYPE);

    addNativeProperties(registry);

    NATIVE_PROPERTIES_COUNT = OBJECT_TYPE.getPropertiesCount();
  }

  /** Adds a basic set of properties to the native types. */
  public static void addNativeProperties(JSTypeRegistry registry) {
    JSType booleanType = registry.getNativeType(JSTypeNative.BOOLEAN_TYPE);
    JSType numberType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    JSType stringType = registry.getNativeType(JSTypeNative.STRING_TYPE);
    JSType unknownType = registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);

    ObjectType objectType =
        registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    ObjectType arrayType =
        registry.getNativeObjectType(JSTypeNative.ARRAY_TYPE);
    ObjectType dateType =
        registry.getNativeObjectType(JSTypeNative.DATE_TYPE);
    ObjectType regexpType =
        registry.getNativeObjectType(JSTypeNative.REGEXP_TYPE);
    ObjectType booleanObjectType =
        registry.getNativeObjectType(JSTypeNative.BOOLEAN_OBJECT_TYPE);
    ObjectType numberObjectType =
        registry.getNativeObjectType(JSTypeNative.NUMBER_OBJECT_TYPE);
    ObjectType stringObjectType =
        registry.getNativeObjectType(JSTypeNative.STRING_OBJECT_TYPE);

    ObjectType objectPrototype = registry
        .getNativeFunctionType(JSTypeNative.OBJECT_FUNCTION_TYPE)
        .getPrototype();
    addMethod(registry, objectPrototype, "constructor", objectType);
    addMethod(registry, objectPrototype, "toString", stringType);
    addMethod(registry, objectPrototype, "toLocaleString", stringType);
    addMethod(registry, objectPrototype, "valueOf", unknownType);
    addMethod(registry, objectPrototype, "hasOwnProperty", booleanType);
    addMethod(registry, objectPrototype, "isPrototypeOf", booleanType);
    addMethod(registry, objectPrototype, "propertyIsEnumerable", booleanType);

    ObjectType arrayPrototype = registry
        .getNativeFunctionType(JSTypeNative.ARRAY_FUNCTION_TYPE)
        .getPrototype();
    addMethod(registry, arrayPrototype, "constructor", arrayType);
    addMethod(registry, arrayPrototype, "toString", stringType);
    addMethod(registry, arrayPrototype, "toLocaleString", stringType);
    addMethod(registry, arrayPrototype, "concat", arrayType);
    addMethod(registry, arrayPrototype, "join", stringType);
    addMethod(registry, arrayPrototype, "pop", unknownType);
    addMethod(registry, arrayPrototype, "push", numberType);
    addMethod(registry, arrayPrototype, "reverse", arrayType);
    addMethod(registry, arrayPrototype, "shift", unknownType);
    addMethod(registry, arrayPrototype, "slice", arrayType);
    addMethod(registry, arrayPrototype, "sort", arrayType);
    addMethod(registry, arrayPrototype, "splice", arrayType);
    addMethod(registry, arrayPrototype, "unshift", numberType);
    arrayType.defineDeclaredProperty("length", numberType, null);

    ObjectType booleanPrototype = registry
        .getNativeFunctionType(JSTypeNative.BOOLEAN_OBJECT_FUNCTION_TYPE)
        .getPrototype();
    addMethod(registry, booleanPrototype, "constructor", booleanObjectType);
    addMethod(registry, booleanPrototype, "toString", stringType);
    addMethod(registry, booleanPrototype, "valueOf", booleanType);

    ObjectType datePrototype = registry
        .getNativeFunctionType(JSTypeNative.DATE_FUNCTION_TYPE)
        .getPrototype();
    addMethod(registry, datePrototype, "constructor", dateType);
    addMethod(registry, datePrototype, "toString", stringType);
    addMethod(registry, datePrototype, "toDateString", stringType);
    addMethod(registry, datePrototype, "toTimeString", stringType);
    addMethod(registry, datePrototype, "toLocaleString", stringType);
    addMethod(registry, datePrototype, "toLocaleDateString", stringType);
    addMethod(registry, datePrototype, "toLocaleTimeString", stringType);
    addMethod(registry, datePrototype, "valueOf", numberType);
    addMethod(registry, datePrototype, "getTime", numberType);
    addMethod(registry, datePrototype, "getFullYear", numberType);
    addMethod(registry, datePrototype, "getUTCFullYear", numberType);
    addMethod(registry, datePrototype, "getMonth", numberType);
    addMethod(registry, datePrototype, "getUTCMonth", numberType);
    addMethod(registry, datePrototype, "getDate", numberType);
    addMethod(registry, datePrototype, "getUTCDate", numberType);
    addMethod(registry, datePrototype, "getDay", numberType);
    addMethod(registry, datePrototype, "getUTCDay", numberType);
    addMethod(registry, datePrototype, "getHours", numberType);
    addMethod(registry, datePrototype, "getUTCHours", numberType);
    addMethod(registry, datePrototype, "getMinutes", numberType);
    addMethod(registry, datePrototype, "getUTCMinutes", numberType);
    addMethod(registry, datePrototype, "getSeconds", numberType);
    addMethod(registry, datePrototype, "getUTCSeconds", numberType);
    addMethod(registry, datePrototype, "getMilliseconds", numberType);
    addMethod(registry, datePrototype, "getUTCMilliseconds", numberType);
    addMethod(registry, datePrototype, "getTimezoneOffset", numberType);
    addMethod(registry, datePrototype, "setTime", numberType);
    addMethod(registry, datePrototype, "setMilliseconds", numberType);
    addMethod(registry, datePrototype, "setUTCMilliseconds", numberType);
    addMethod(registry, datePrototype, "setSeconds", numberType);
    addMethod(registry, datePrototype, "setUTCSeconds", numberType);
    addMethod(registry, datePrototype, "setMinutes", numberType);
    addMethod(registry, datePrototype, "setUTCMinutes", numberType);
    addMethod(registry, datePrototype, "setHours", numberType);
    addMethod(registry, datePrototype, "setUTCHours", numberType);
    addMethod(registry, datePrototype, "setDate", numberType);
    addMethod(registry, datePrototype, "setUTCDate", numberType);
    addMethod(registry, datePrototype, "setMonth", numberType);
    addMethod(registry, datePrototype, "setUTCMonth", numberType);
    addMethod(registry, datePrototype, "setFullYear", numberType);
    addMethod(registry, datePrototype, "setUTCFullYear", numberType);
    addMethod(registry, datePrototype, "toUTCString", stringType);
    addMethod(registry, datePrototype, "toGMTString", stringType);

    ObjectType numberPrototype = registry
        .getNativeFunctionType(JSTypeNative.NUMBER_OBJECT_FUNCTION_TYPE)
        .getPrototype();
    addMethod(registry, numberPrototype, "constructor", numberObjectType);
    addMethod(registry, numberPrototype, "toString", stringType);
    addMethod(registry, numberPrototype, "toLocaleString", stringType);
    addMethod(registry, numberPrototype, "valueOf", numberType);
    addMethod(registry, numberPrototype, "toFixed", stringType);
    addMethod(registry, numberPrototype, "toExponential", stringType);
    addMethod(registry, numberPrototype, "toPrecision", stringType);

    ObjectType regexpPrototype = registry
        .getNativeFunctionType(JSTypeNative.REGEXP_FUNCTION_TYPE)
        .getPrototype();
    addMethod(registry, regexpPrototype, "constructor", regexpType);
    addMethod(registry, regexpPrototype, "exec",
        registry.createNullableType(arrayType));
    addMethod(registry, regexpPrototype, "test", booleanType);
    addMethod(registry, regexpPrototype, "toString", stringType);
    regexpType.defineDeclaredProperty("source", stringType, null);
    regexpType.defineDeclaredProperty("global", booleanType, null);
    regexpType.defineDeclaredProperty("ignoreCase", booleanType, null);
    regexpType.defineDeclaredProperty("multiline", booleanType, null);
    regexpType.defineDeclaredProperty("lastIndex", numberType, null);

    ObjectType stringPrototype = registry
        .getNativeFunctionType(JSTypeNative.STRING_OBJECT_FUNCTION_TYPE)
        .getPrototype();
    addMethod(registry, stringPrototype, "constructor", stringObjectType);
    addMethod(registry, stringPrototype, "toString", stringType);
    addMethod(registry, stringPrototype, "valueOf", stringType);
    addMethod(registry, stringPrototype, "charAt", stringType);
    addMethod(registry, stringPrototype, "charCodeAt", numberType);
    addMethod(registry, stringPrototype, "concat", stringType);
    addMethod(registry, stringPrototype, "indexOf", numberType);
    addMethod(registry, stringPrototype, "lastIndexOf", numberType);
    addMethod(registry, stringPrototype, "localeCompare", numberType);
    addMethod(registry, stringPrototype, "match",
        registry.createNullableType(arrayType));
    addMethod(registry, stringPrototype, "replace", stringType);
    addMethod(registry, stringPrototype, "search", numberType);
    addMethod(registry, stringPrototype, "slice", stringType);
    addMethod(registry, stringPrototype, "split", arrayType);
    addMethod(registry, stringPrototype, "substr", stringType);
    addMethod(registry, stringPrototype, "substring", stringType);
    addMethod(registry, stringPrototype, "toLowerCase", stringType);
    addMethod(registry, stringPrototype, "toLocaleLowerCase", stringType);
    addMethod(registry, stringPrototype, "toUpperCase", stringType);
    addMethod(registry, stringPrototype, "toLocaleUpperCase", stringType);
    stringObjectType.defineDeclaredProperty("length", numberType, null);
  }

  private static void addMethod(
      JSTypeRegistry registry, ObjectType receivingType, String methodName,
      JSType returnType) {
    receivingType.defineDeclaredProperty(methodName,
        new FunctionBuilder(registry).withReturnType(returnType).build(),
        null);
  }

  protected JSType createUnionType(JSType... variants) {
    return registry.createUnionType(variants);
  }

  protected RecordTypeBuilder createRecordTypeBuilder() {
    return new RecordTypeBuilder(registry);
  }

  protected JSType createNullableType(JSType type) {
    return registry.createNullableType(type);
  }

  protected JSType createOptionalType(JSType type) {
    return registry.createOptionalType(type);
  }

  protected TemplatizedType createTemplatizedType(
      ObjectType baseType, ImmutableList<JSType> templatizedTypes) {
    return registry.createTemplatizedType(baseType, templatizedTypes);
  }

  protected TemplatizedType createTemplatizedType(
      ObjectType baseType, JSType... templatizedType) {
    return createTemplatizedType(
        baseType, ImmutableList.copyOf(templatizedType));
  }

  /**
   * Asserts that a Node representing a type expression resolves to the
   * correct {@code JSType}.
   */
  protected void assertTypeEquals(JSType expected, Node actual) {
    assertTypeEquals(expected, new JSTypeExpression(actual, "<BaseJSTypeTestCase.java>"));
  }

  /**
   * Asserts that a a type expression resolves to the correct {@code JSType}.
   */
  protected void assertTypeEquals(JSType expected, JSTypeExpression actual) {
    assertTypeEquals(expected, resolve(actual));
  }

  protected final void assertTypeEquals(JSType a, JSType b) {
    assertType(b).isStructurallyEqualTo(a);
  }

  protected final void assertTypeEquals(String msg, JSType a, JSType b) {
    assertWithMessage(msg).about(types()).that(b).isStructurallyEqualTo(a);
  }

  /**
   * Resolves a type expression, expecting the given warnings.
   */
  protected JSType resolve(JSTypeExpression n, String... warnings) {
    errorReporter.setWarnings(warnings);
    return n.evaluate(null, registry);
  }

  /**
   * A definition of all extern types. This should be kept in sync with
   * javascript/externs/es3.js. This is used to check that the built-in types
   * declared in {@link JSTypeRegistry} have the same type as that in the
   * externs. It can also be used for any tests that want to use built-in types
   * in their externs.
   */
  public static final String ALL_NATIVE_EXTERN_TYPES = lines(
      "/**",
      " * @constructor",
      " * @param {*=} opt_value",
      " * @return {!Object}",
      " */",
      "function Object(opt_value) {}",
      "",
      "/**",
      " * @constructor",
      " * @param {...*} var_args",
      " */",
      "",
      "function Function(var_args) {}",
      "/**",
      " * @constructor",
      " * @param {...*} var_args",
      " * @return {!Array.<?>}",
      " * @template T",
      " */",
      "function Array(var_args) {}",
      "",
      "/**",
      " * @constructor",
      " * @param {*=} opt_value",
      " * @return {boolean}",
      " */",
      "function Boolean(opt_value) {}",
      "",
      "/**",
      " * @constructor",
      " * @param {*=} opt_value",
      " * @return {number}",
      " */",
      "function Number(opt_value) {}",
      "",
      "/**",
      " * @constructor",
      " * @param {?=} opt_yr_num",
      " * @param {?=} opt_mo_num",
      " * @param {?=} opt_day_num",
      " * @param {?=} opt_hr_num",
      " * @param {?=} opt_min_num",
      " * @param {?=} opt_sec_num",
      " * @param {?=} opt_ms_num",
      " * @return {string}",
      " */",
      "function Date(opt_yr_num, opt_mo_num, opt_day_num, opt_hr_num,",
      "    opt_min_num, opt_sec_num, opt_ms_num) {}",
      "",
      "/**",
      " * @constructor",
      " * @param {*=} opt_str",
      " * @return {string}",
      " */",
      "function String(opt_str) {}",
      "",
      "/**",
      " * @constructor",
      " * @param {*=} opt_pattern",
      " * @param {*=} opt_flags",
      " * @return {!RegExp}",
      " */",
      "function RegExp(opt_pattern, opt_flags) {}",
      "",
      "/**",
      " * @constructor",
      " * @param {*=} opt_message",
      " * @param {*=} opt_file",
      " * @param {*=} opt_line",
      " * @return {!Error}",
      " */",
      "function Error(opt_message, opt_file, opt_line) {}",
      "",
      "/**",
      " * @constructor",
      " * @extends {Error}",
      " * @param {*=} opt_message",
      " * @param {*=} opt_file",
      " * @param {*=} opt_line",
      " * @return {!EvalError}",
      " */",
      "function EvalError(opt_message, opt_file, opt_line) {}",
      "",
      "/**",
      " * @constructor",
      " * @extends {Error}",
      " * @param {*=} opt_message",
      " * @param {*=} opt_file",
      " * @param {*=} opt_line",
      " * @return {!RangeError}",
      " */",
      "function RangeError(opt_message, opt_file, opt_line) {}",
      "",
      "/**",
      " * @constructor",
      " * @extends {Error}",
      " * @param {*=} opt_message",
      " * @param {*=} opt_file",
      " * @param {*=} opt_line",
      " * @return {!ReferenceError}",
      " */",
      "function ReferenceError(opt_message, opt_file, opt_line) {}",
      "",
      "/**",
      " * @constructor",
      " * @extends {Error}",
      " * @param {*=} opt_message",
      " * @param {*=} opt_file",
      " * @param {*=} opt_line",
      " * @return {!SyntaxError}",
      " */",
      "function SyntaxError(opt_message, opt_file, opt_line) {}",
      "",
      "/**",
      " * @constructor",
      " * @extends {Error}",
      " * @param {*=} opt_message",
      " * @param {*=} opt_file",
      " * @param {*=} opt_line",
      " * @return {!TypeError}",
      " */",
      "function TypeError(opt_message, opt_file, opt_line) {}",
      "",
      "/**",
      " * @constructor",
      " * @extends {Error}",
      " * @param {*=} opt_message",
      " * @param {*=} opt_file",
      " * @param {*=} opt_line",
      " * @return {!URIError}",
      " */",
      "function URIError(opt_message, opt_file, opt_line) {}",
      "",
      "/**",
      " * @param {string} progId",
      " * @param {string=} opt_location",
      " * @constructor",
      " */",
      "function ActiveXObject(progId, opt_location) {}");

  protected final void assertTypeNotEquals(JSType a, JSType b) {
    assertType(b).isNotEqualTo(a);
  }

  protected static String lines(String line) {
    return line;
  }

  protected static String lines(String ...lines) {
    return LINE_JOINER.join(lines);
  }
}
