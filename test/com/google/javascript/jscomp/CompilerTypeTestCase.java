/*
 * Copyright 2008 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;
import static com.google.javascript.rhino.testing.TypeSubject.types;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Correspondence;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.RecordTypeBuilder;
import com.google.javascript.rhino.jstype.TemplatizedType;
import com.google.javascript.rhino.testing.TestErrorReporter;
import java.util.Objects;
import org.junit.Before;

/**
 * This class is mostly used by passes testing the old type checker. Passes that run after type
 * checking and need type information use the class TypeICompilerTestCase.
 */
abstract class CompilerTypeTestCase {
  protected static final Joiner LINE_JOINER = Joiner.on('\n');

  static final String CLOSURE_DEFS = LINE_JOINER.join(
      "/** @const */ var goog = {};",
      "goog.inherits = function(x, y) {};",
      "/** @type {!Function} */ goog.abstractMethod = function() {};",
      "goog.isArray = function(x) {};",
      "goog.isDef = function(x) {};",
      "goog.isFunction = function(x) {};",
      "goog.isNull = function(x) {};",
      "goog.isString = function(x) {};",
      "goog.isObject = function(x) {};",
      "goog.isDefAndNotNull = function(x) {};",
      "/** @const */ goog.array = {};",
      // simplified ArrayLike definition
      "/**",
      " * @typedef {Array|{length: number}}",
      " */",
      "goog.array.ArrayLike;",
      "/**",
      " * @param {Array.<T>|{length:number}} arr",
      " * @param {function(this:S, T, number, goog.array.ArrayLike):boolean} f",
      " * @param {S=} opt_obj",
      " * @return {!Array.<T>}",
      " * @template T,S",
      " */",
      // return empty array to satisfy return type
      "goog.array.filter = function(arr, f, opt_obj){ return []; };",
      "goog.asserts = {};",
      "/** @return {*} */ goog.asserts.assert = function(x) { return x; };");

  /**
   * A default set of externs for testing.
   *
   * TODO(bradfordcsmith): Replace this with externs built by TestExternsBuilder.
   */
  static final String DEFAULT_EXTERNS = CompilerTestCase.DEFAULT_EXTERNS;

  protected Compiler compiler;
  protected JSTypeRegistry registry;
  protected TestErrorReporter errorReporter;

  protected CompilerOptions getDefaultOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT5);
    options.setWarningLevel(
        DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.WARNING);
    options.setWarningLevel(
        DiagnosticGroups.MISPLACED_TYPE_ANNOTATION, CheckLevel.WARNING);
    options.setWarningLevel(
        DiagnosticGroups.INVALID_CASTS, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.JSDOC_MISSING_TYPE, CheckLevel.WARNING);
    options.setCodingConvention(getCodingConvention());
    return options;
  }

  protected CodingConvention getCodingConvention() {
    return new GoogleCodingConvention();
  }

  protected void checkReportedWarningsHelper(String[] expected) {
    if (expected == null) {
      expected = new String[0];
    }

    assertWithMessage("Regarding warnings:")
        .that(compiler.getWarnings())
        .asList()
        .comparingElementsUsing(DESCRIPTION_EQUALITY)
        .containsExactlyElementsIn(expected)
        .inOrder();
  }

  @Before
  public void setUp() throws Exception {
    errorReporter = new TestErrorReporter(null, null);
    initializeNewCompiler(getDefaultOptions());
  }

  protected static String lines(String line) {
    return line;
  }

  protected static String lines(String... lines) {
    return LINE_JOINER.join(lines);
  }

  protected void initializeNewCompiler(CompilerOptions options) {
    compiler = new Compiler();
    compiler.initOptions(options);
    compiler.setFeatureSet(compiler.getFeatureSet().without(Feature.MODULES));
    registry = compiler.getTypeRegistry();
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

  protected TemplatizedType createTemplatizedType(ObjectType baseType, JSType... templatizedType) {
    return createTemplatizedType(baseType, ImmutableList.copyOf(templatizedType));
  }

  /** Asserts that a Node representing a type expression resolves to the correct {@code JSType}. */
  protected void assertTypeEquals(JSType expected, Node actual) {
    assertTypeEquals(expected, new JSTypeExpression(actual, "<BaseJSTypeTestCase.java>"));
  }

  /** Asserts that a a type expression resolves to the correct {@code JSType}. */
  protected void assertTypeEquals(JSType expected, JSTypeExpression actual) {
    assertTypeEquals(expected, resolve(actual));
  }

  protected final void assertTypeEquals(JSType a, JSType b) {
    assertType(b).isStructurallyEqualTo(a);
  }

  protected final void assertTypeEquals(String msg, JSType a, JSType b) {
    assertWithMessage(msg).about(types()).that(b).isStructurallyEqualTo(a);
  }

  /** Resolves a type expression, expecting the given warnings. */
  protected JSType resolve(JSTypeExpression n, String... warnings) {
    errorReporter.setWarnings(warnings);
    return n.evaluate(null, registry);
  }

  protected ObjectType getNativeNoObjectType() {
    return getNativeObjectType(JSTypeNative.NO_OBJECT_TYPE);
  }

  protected ObjectType getNativeArrayType() {
    return getNativeObjectType(JSTypeNative.ARRAY_TYPE);
  }

  protected ObjectType getNativeStringObjectType() {
    return getNativeObjectType(JSTypeNative.STRING_OBJECT_TYPE);
  }

  protected ObjectType getNativeNumberObjectType() {
    return getNativeObjectType(JSTypeNative.NUMBER_OBJECT_TYPE);
  }

  protected ObjectType getNativeBooleanObjectType() {
    return getNativeObjectType(JSTypeNative.BOOLEAN_OBJECT_TYPE);
  }

  protected ObjectType getNativeNoType() {
    return getNativeObjectType(JSTypeNative.NO_TYPE);
  }

  protected ObjectType getNativeUnknownType() {
    return getNativeObjectType(JSTypeNative.UNKNOWN_TYPE);
  }

  protected ObjectType getNativeCheckedUnknownType() {
    return getNativeObjectType(JSTypeNative.CHECKED_UNKNOWN_TYPE);
  }

  protected ObjectType getNativeObjectType() {
    return getNativeObjectType(JSTypeNative.OBJECT_TYPE);
  }

  ObjectType getNativeObjectType(JSTypeNative jsTypeNative) {
    return registry.getNativeObjectType(jsTypeNative);
  }

  protected FunctionType getNativeObjectConstructorType() {
    return getNativeFunctionType(JSTypeNative.OBJECT_FUNCTION_TYPE);
  }

  protected FunctionType getNativeArrayConstructorType() {
    return getNativeFunctionType(JSTypeNative.ARRAY_FUNCTION_TYPE);
  }

  protected FunctionType getNativeBooleanObjectConstructorType() {
    return getNativeFunctionType(JSTypeNative.BOOLEAN_OBJECT_FUNCTION_TYPE);
  }

  protected FunctionType getNativeNumberObjectConstructorType() {
    return getNativeFunctionType(JSTypeNative.NUMBER_OBJECT_FUNCTION_TYPE);
  }

  protected FunctionType getNativeStringObjectConstructorType() {
    return getNativeFunctionType(JSTypeNative.STRING_OBJECT_FUNCTION_TYPE);
  }

  protected FunctionType getNativeDateConstructorType() {
    return getNativeFunctionType(JSTypeNative.DATE_FUNCTION_TYPE);
  }

  protected FunctionType getNativeRegexpConstructorType() {
    return getNativeFunctionType(JSTypeNative.REGEXP_FUNCTION_TYPE);
  }

  protected FunctionType getNativeU2UConstructorType() {
    return getNativeFunctionType(JSTypeNative.U2U_CONSTRUCTOR_TYPE);
  }

  protected FunctionType getNativeU2UFunctionType() {
    return getNativeFunctionType(JSTypeNative.U2U_FUNCTION_TYPE);
  }

  FunctionType getNativeFunctionType(JSTypeNative jsTypeNative) {
    return registry.getNativeFunctionType(jsTypeNative);
  }

  protected JSType getNativeVoidType() {
    return getNativeType(JSTypeNative.VOID_TYPE);
  }

  protected JSType getNativeNullType() {
    return getNativeType(JSTypeNative.NULL_TYPE);
  }

  protected JSType getNativeNullVoidType() {
    return getNativeType(JSTypeNative.NULL_VOID);
  }

  protected JSType getNativeNumberType() {
    return getNativeType(JSTypeNative.NUMBER_TYPE);
  }

  protected JSType getNativeBooleanType() {
    return getNativeType(JSTypeNative.BOOLEAN_TYPE);
  }

  protected JSType getNativeStringType() {
    return getNativeType(JSTypeNative.STRING_TYPE);
  }

  protected JSType getNativeObjectNumberStringBooleanType() {
    return getNativeType(JSTypeNative.OBJECT_NUMBER_STRING_BOOLEAN);
  }

  protected JSType getNativeNumberStringBooleanType() {
    return getNativeType(JSTypeNative.NUMBER_STRING_BOOLEAN);
  }

  protected JSType getNativeObjectNumberStringBooleanSymbolType() {
    return getNativeType(JSTypeNative.OBJECT_NUMBER_STRING_BOOLEAN_SYMBOL);
  }

  protected JSType getNativeNumberStringBooleanSymbolType() {
    return getNativeType(JSTypeNative.NUMBER_STRING_BOOLEAN_SYMBOL);
  }

  JSType getNativeAllType() {
    return getNativeType(JSTypeNative.ALL_TYPE);
  }

  JSType getNativeType(JSTypeNative jsTypeNative) {
    return registry.getNativeType(jsTypeNative);
  }

  static final Correspondence<JSError, String> DESCRIPTION_EQUALITY =
      new Correspondence<JSError, String>() {
        @Override
        public boolean compare(JSError error, String description) {
          return Objects.equals(error.description, description);
        }

        @Override
        public String toString() {
          return "has description equal to";
        }
      };
}
