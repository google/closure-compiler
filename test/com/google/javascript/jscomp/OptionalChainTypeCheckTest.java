/*
 * Copyright 2020 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.CompilerTypeTestCase.lines;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.Node;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests types for Optional chain nodes.
 *
 * <p>IMPORTANT: Do not put {@code {@literal @}Test} methods directly in this class, they must be
 * inside an inner class or they won't be executed, because we're using the {@code Enclosed} JUnit
 * runner.
 */
@RunWith(Enclosed.class)
public class OptionalChainTypeCheckTest {

  @AutoValue
  public abstract static class OptChainTestCase {
    abstract Optional<String> forObjType();

    abstract String withExpr();

    abstract Optional<String> assignedTo();

    abstract Optional<String> mustReport();

    abstract Optional<String> withPropReturnType(); // only used in testing CALL

    static Builder builder() {
      return new AutoValue_OptionalChainTypeCheckTest_OptChainTestCase.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
      abstract Builder forObjType(String objType);

      abstract Builder withExpr(String expr);

      abstract Builder assignedTo(String type);

      abstract Builder mustReport(String error);
      // Sets the return type of the function property, if any on this object type.
      abstract Builder withPropReturnType(String type);

      abstract OptChainTestCase build();
    }
  }

  /**
   * Constructs the test case string from a test case object. e.g. `/\** @type {({b:number})} *\/
   * var a; var x; x =a?.[b];`
   */
  private static String createTestString(OptChainTestCase testCase) {
    String objType = testCase.forObjType().get();
    String expr = testCase.withExpr();
    String expectType = testCase.assignedTo().get();
    return lines(
        "/** @type {(" + objType + ")} */ var a;",
        "/** @type {(" + expectType + ")} */ var x;",
        "x = " + expr + ";");
  }

  @RunWith(Parameterized.class)
  public static final class OptChainGetElemTests extends TypeCheckTestCase {
    @Parameter public OptChainTestCase testCase;

    /**
     * Generates test cases for testing `?.[]` OPTCHAIN_GETPROP expressions first followed by normal
     * `[]` GETPROP expressions. Each generated test case is executed by the `test()` method.
     */
    @Parameters
    public static ImmutableList<OptChainTestCase> cases() {
      return ImmutableList.of(
          // Inferred type for [] access on an Object is always unknown. This is consistent
          // for optional as well as normal [] access.
          // Then the unknown value can be assigned to the variable no matter what its type is.
          // Note the intentionally different types on the property and the assigned-to variable
          // which illustrate this.
          OptChainTestCase.builder()
              .forObjType("{prop: number}")
              .withExpr("a[index]")
              .assignedTo("string")
              .build(),
          OptChainTestCase.builder()
              .forObjType("{prop: number}")
              .withExpr("a?.[index]")
              .assignedTo("string")
              .build(),
          OptChainTestCase.builder()
              .forObjType("{prop: number}")
              .withExpr("a['index'];")
              .assignedTo("string")
              .build(),
          OptChainTestCase.builder()
              .forObjType("{prop: number}")
              .withExpr("a?.['index'];")
              .assignedTo("string")
              .build(),

          // missing prop
          OptChainTestCase.builder()
              .forObjType("{b:number}")
              .withExpr("a?.c[b]")
              .assignedTo("?")
              .mustReport("Property c never defined on a")
              .build(),

          // Tests that Arrays and Objects are inferred correctly by OPTCHAIN_GETELEM
          OptChainTestCase.builder()
              .forObjType("Array<number>")
              .withExpr("a?.[0];")
              .assignedTo("number|undefined")
              .build(),
          OptChainTestCase.builder()
              .forObjType("Array<number|string>")
              .withExpr("a?.[0];")
              .assignedTo("number|string|undefined")
              .build(),
          OptChainTestCase.builder()
              .forObjType("Array<number|?string>")
              .withExpr("a?.[0];")
              .assignedTo("number|?string|undefined")
              .build(),
          OptChainTestCase.builder()
              .forObjType("Array<?number>")
              .withExpr("a?.[0];")
              .assignedTo("?number|undefined")
              .build(),
          OptChainTestCase.builder()
              .forObjType("Object<string, number>")
              .withExpr("a?.['b'];")
              .assignedTo("number|undefined")
              .build(),

          // type mismatch on assignments using `?.[]` on `Array` and `Object` are reported
          OptChainTestCase.builder()
              .forObjType("Array<number>")
              .withExpr("a?.[0];")
              .assignedTo("string")
              .mustReport(lines("assignment", "found   : (number|undefined)", "required: string"))
              .build(),
          OptChainTestCase.builder()
              .forObjType("Array<?number>")
              .withExpr("a?.[0];")
              .assignedTo("number|undefined")
              .mustReport(
                  lines(
                      "assignment",
                      "found   : (null|number|undefined)",
                      "required: (number|undefined)"))
              .build(),
          OptChainTestCase.builder()
              .forObjType("null")
              .withExpr("a?.['b'];")
              .assignedTo("undefined")
              .build(),
          OptChainTestCase.builder()
              .forObjType("{b:number}")
              .withExpr("a?.[c];")
              .assignedTo("?")
              .build(),

          // testing non-optional exprs
          OptChainTestCase.builder()
              .forObjType("{b:(?|number)}")
              .withExpr("a[b];")
              .assignedTo("(?|number)")
              .build(),
          OptChainTestCase.builder()
              .forObjType("{b:number}")
              .withExpr("a[b];")
              .assignedTo("number")
              .build(),
          OptChainTestCase.builder()
              .forObjType("?{b:number}")
              .withExpr("a[b];")
              .assignedTo("number")
              .build(),

          // error cases - when using unguarded null|undefined LHS non-optionally
          OptChainTestCase.builder()
              .forObjType("null")
              .withExpr("a[b];")
              .assignedTo("?")
              .mustReport(
                  lines(
                      "only arrays or objects can be accessed",
                      "found   : null",
                      "required: Object"))
              .build(),
          OptChainTestCase.builder()
              .forObjType("null")
              .withExpr("a.b?.[c];")
              .assignedTo("?")
              .mustReport(
                  lines("No properties on this expression", "found   : null", "required: Object"))
              .build());
    }

    @Override
    protected CompilerOptions getDefaultOptions() {
      CompilerOptions options = super.getDefaultOptions();
      options.setWarningLevel(DiagnosticGroups.STRICT_MISSING_PROPERTIES, CheckLevel.WARNING);
      return options;
    }

    @Test
    public void test() {
      String js = createTestString(testCase);
      if (testCase.mustReport().isEmpty()) {
        newTest().addSource(js).run();
      } else {
        newTest().addSource(js).addDiagnostic(testCase.mustReport().get()).run();
      }
    }
  }

  @RunWith(Parameterized.class)
  public static final class OptChainGetPropTests extends TypeCheckTestCase {
    @Parameter public OptChainTestCase testCase;

    @Parameters
    public static ImmutableList<OptChainTestCase> cases() {
      return ImmutableList.of(
          OptChainTestCase.builder()
              .forObjType("{b:number}")
              .withExpr("a?.b;")
              .assignedTo("number")
              .build(),
          OptChainTestCase.builder()
              .forObjType("{b:?number}")
              .withExpr("a?.b;")
              .assignedTo("?number")
              .build(),
          OptChainTestCase.builder()
              .forObjType("?{b:number}")
              .withExpr("a?.b;")
              .assignedTo("undefined|number")
              .build(),
          OptChainTestCase.builder()
              .forObjType("?{b:?number}")
              .withExpr("a?.b;")
              .assignedTo("undefined|(?number)")
              .build(),
          OptChainTestCase.builder()
              .forObjType("null")
              .withExpr("a?.b;")
              .assignedTo("undefined")
              .build(),

          // missing property
          OptChainTestCase.builder()
              .forObjType("{b:number}")
              .withExpr("a?.c;")
              .assignedTo("?")
              .mustReport("Property c never defined on a")
              .build(),

          // type mismatch reported
          OptChainTestCase.builder()
              .forObjType("{b:number}")
              .withExpr("a?.b;")
              .assignedTo("string")
              .mustReport(lines("assignment", "found   : number", "required: string"))
              .build(),
          OptChainTestCase.builder()
              .forObjType("?{b:number}")
              .withExpr("a?.b;")
              .assignedTo("undefined|string")
              .mustReport(
                  lines(
                      "assignment", "found   : (number|undefined)", "required: (string|undefined)"))
              .build(),

          // normal GETPROP
          OptChainTestCase.builder()
              .forObjType("null")
              .withExpr("a.alert();")
              .assignedTo("?")
              .mustReport(
                  lines("No properties on this expression", "found   : null", "required: Object"))
              .build());
    }

    @Override
    protected CompilerOptions getDefaultOptions() {
      CompilerOptions options = super.getDefaultOptions();
      options.setWarningLevel(DiagnosticGroups.STRICT_MISSING_PROPERTIES, CheckLevel.WARNING);
      return options;
    }

    @Test
    public void test() {
      String js = createTestString(testCase);
      if (testCase.mustReport().isEmpty()) {
        newTest().addSource(js).run();
      } else {
        newTest().addSource(js).addDiagnostic(testCase.mustReport().get()).run();
      }
    }
  }

  @RunWith(Parameterized.class)
  public static final class OptChainAccessOnDictTests extends TypeCheckTestCase {

    @Parameter public OptChainTestCase testCase;

    @Parameters
    public static ImmutableList<OptChainTestCase> cases() {
      return ImmutableList.of(
          OptChainTestCase.builder()
              .withExpr("x?.prop;")
              .mustReport("Cannot do '?.' access on a dict")
              .build(),
          OptChainTestCase.builder()
              .withExpr("x.prop;")
              .mustReport("Cannot do '.' access on a dict")
              .build());
    }

    @Override
    protected CompilerOptions getDefaultOptions() {
      CompilerOptions options = super.getDefaultOptions();
      options.setWarningLevel(DiagnosticGroups.STRICT_MISSING_PROPERTIES, CheckLevel.WARNING);
      return options;
    }

    @Test
    public void testOptChainGetProp_accessOnDict1() {
      newTest()
          .addSource(
              "/**",
              " * @constructor",
              " * @dict",
              " */",
              "function Dict1(){ this['prop'] = 123; }",
              "/** @param{Dict1} x */",
              "function takesDict(x) {return " + testCase.withExpr() + " }")
          .addDiagnostic(testCase.mustReport().get())
          .run();
    }

    @Test
    public void testOptChainGetPropDict2() {
      newTest()
          .addSource(
              "/**",
              " * @constructor",
              " * @dict",
              " */",
              "function Dict1(){ this['prop'] = 123; }",
              "/**",
              " * @constructor",
              " * @extends {Dict1}",
              " */",
              "function Dict1kid(){ this['prop'] = 123; }",
              "/** @param{Dict1kid} x */",
              "function takesDict(x) { return ",
              testCase.withExpr(),
              " }")
          .addDiagnostic(testCase.mustReport().get())
          .run();
    }

    @Test
    public void testOptChainGetPropDict_accessingDictOrNonDict() {
      newTest()
          .addSource(
              "/**",
              " * @constructor",
              " * @dict",
              " */",
              "function Dict1() { this['prop'] = 123; }",
              "/** @constructor */",
              "function NonDict() { this.prop = 321; }",
              "/** @param{(NonDict|Dict1)} x */",
              "function takesDict(x) { return ",
              testCase.withExpr(),
              "}")
          .addDiagnostic(testCase.mustReport().get())
          .run();
    }

    @Test
    public void testOptChainGetProp_accessingDictOrStruct() {
      newTest()
          .addSource(
              "/**",
              " * @constructor",
              " * @dict",
              " */",
              "function Dict1() { this['prop'] = 123; }",
              "/**",
              " * @constructor",
              " * @struct",
              " */",
              "function Struct1() { this.prop = 123; }",
              "/** @param{(Struct1|Dict1)} x */",
              "function takesNothing(x) { return ",
              testCase.withExpr(),
              "}")
          .addDiagnostic(testCase.mustReport().get())
          .run();
    }
  }

  @RunWith(Parameterized.class)
  public static final class OptChainCallTests extends TypeCheckTestCase {

    @Parameter public OptChainTestCase testCase;

    @Parameters
    public static ImmutableList<OptChainTestCase> cases() {
      return ImmutableList.of(
          OptChainTestCase.builder()
              .withPropReturnType("string")
              .withExpr("a?.prop()")
              .assignedTo("string")
              .build(),
          OptChainTestCase.builder()
              .withPropReturnType("?string")
              .withExpr("a?.prop()")
              .assignedTo("?string")
              .build(),

          // testing null objType
          OptChainTestCase.builder()
              .forObjType("null")
              .withExpr("a?.prop()")
              .assignedTo("undefined")
              .build(),
          OptChainTestCase.builder()
              .forObjType("null")
              .withExpr("a?.()")
              .assignedTo("undefined")
              .build(),

          // missing prop
          OptChainTestCase.builder()
              .forObjType("{b:number}")
              .withExpr("a?.c()")
              .assignedTo("?")
              .mustReport("Property c never defined on a")
              .build(),

          // mismatch
          OptChainTestCase.builder()
              .withPropReturnType("string")
              .withExpr("a?.prop()")
              .assignedTo("number")
              .mustReport(lines("assignment", "found   : string", "required: number"))
              .build(),
          OptChainTestCase.builder()
              .withPropReturnType("?string")
              .withExpr("a?.prop()")
              .assignedTo("string")
              .mustReport(lines("assignment", "found   : (null|string)", "required: string"))
              .build(),

          // non-optional tests
          OptChainTestCase.builder()
              .withPropReturnType("string")
              .withExpr("a.prop()")
              .assignedTo("string")
              .build(),
          OptChainTestCase.builder()
              .withPropReturnType("?string")
              .withExpr("a.prop()")
              .assignedTo("?string")
              .build(),

          // type mismatch
          OptChainTestCase.builder()
              .withPropReturnType("string")
              .withExpr("a.prop()")
              .assignedTo("number")
              .mustReport(lines("assignment", "found   : string", "required: number"))
              .build(),
          OptChainTestCase.builder()
              .withPropReturnType("?string")
              .withExpr("a.prop()")
              .assignedTo("string")
              .mustReport(lines("assignment", "found   : (null|string)", "required: string"))
              .build(),

          // We don't report an error when a null or undefined type is called as a function
          OptChainTestCase.builder().forObjType("null").withExpr("a()").assignedTo("?").build());
    }

    private static String createOptChainCallTestString(OptChainTestCase testCase) {
      StringBuilder js = new StringBuilder();
      js.append("let a = function() {};");
      if (testCase.withPropReturnType().isPresent()) {
        js.append("/** @type {function():")
            .append(testCase.withPropReturnType().get())
            .append(" } */");
      }
      js.append("  a.prop = function() {};");
      js.append("/**@type {").append(testCase.assignedTo().get()).append("} */var x;");
      js.append("x = ").append(testCase.withExpr());
      return js.toString();
    }

    @Override
    protected CompilerOptions getDefaultOptions() {
      CompilerOptions options = super.getDefaultOptions();
      options.setWarningLevel(DiagnosticGroups.STRICT_MISSING_PROPERTIES, CheckLevel.WARNING);
      return options;
    }

    @Test
    public void testOptChainCallExpressions() {
      String js = createOptChainCallTestString(testCase);
      if (testCase.mustReport().isEmpty()) {
        newTest().addSource(js).run();
      } else {
        newTest().addSource(js).addDiagnostic(testCase.mustReport().get()).run();
      }
    }
  }

  @RunWith(JUnit4.class)
  public static final class OptChainTestsNonParameterized extends TypeCheckTestCase {
    @Override
    protected CompilerOptions getDefaultOptions() {
      CompilerOptions options = super.getDefaultOptions();
      options.setWarningLevel(DiagnosticGroups.STRICT_MISSING_PROPERTIES, CheckLevel.WARNING);
      return options;
    }

    // Confirms that OPTCHAIN_GETELEM nodes are inferred as unknown type
    @Test
    public void testOptChainGetElemExpressions_nonNullObject() {
      String js = "/** @type {({b:number})} */ var a; var x; x = a?.[b]";

      Node script = parseAndTypeCheck(js);
      Node assign = script.getLastChild().getFirstChild();
      assertThat(assign.isAssign()).isTrue();

      Node xName = assign.getFirstChild();
      assertThat(xName.isName()).isTrue();
      assertThat(xName.getJSType().isUnknownType()).isTrue();

      Node getElem = assign.getSecondChild();
      assertThat(getElem.isOptChainGetElem() || getElem.isGetElem()).isTrue();
      assertThat(getElem.getJSType().isUnknownType()).isTrue();
    }

    @Test
    public void testOptChainGetElemOnStruct1() {
      newTest()
          .addSource(
              "/**",
              " * @constructor",
              " * @struct",
              " */",
              "function AStruct(){ this.b = 123; }",
              "/** @param{AStruct} x */",
              "function takesStruct(x) {",
              "  var a = x;",
              "  return a?.[b];",
              "}")
          .addDiagnostic("Cannot do '[]' access on a struct")
          .run();
    }

    @Test
    public void testOptChainGetElemOnStruct2() {
      newTest()
          .addSource(
              "/**",
              " * @constructor",
              " * @struct",
              " */",
              "function Struct1(){ this.b = 123; }",
              "/**",
              " * @constructor",
              " * @extends {Struct1}",
              " */",
              "function Struct1kid(){ this.b = 123; }",
              "/** @param{Struct1kid} a */",
              "function takesStruct2(a) { return a?.[b];",
              " }")
          .addDiagnostic("Cannot do '[]' access on a struct")
          .run();
    }

    @Test
    public void testGetProp_propAccessOnVoidFunction() {
      newTest()
          .addSource("/** @return {void}*/function foo(){foo().bar;}")
          .addDiagnostic(
              lines("No properties on this expression", "found   : undefined", "required: Object"))
          .run();
    }

    // Optionally accessing a prop on NULL_TYPE or VOID_TYPE is not an error.
    @Test
    public void testOptChainGetProp_propAccessOnVoidFunction_guarded() {
      newTest().addSource("/** @return {void}*/function foo(){foo()?.bar;}").run();
    }

    @Test
    public void testOptChainGetProp_propAccessedAfterObjectInitialized() {
      newTest()
          .addSource(
              "/** @constructor */ ",
              "function Foo() { /** @type {?Object} */ this.x = null; }",
              "Foo.prototype.initX = function() { this.x = {foo: 1}; };",
              "Foo.prototype.bar = function() {",
              "  if (this.x == null) { this.initX(); alert(this.x?.foo); }",
              "};")
          .run();
    }

    @Test
    public void testAssignToUnknown_noError() {
      newTest().addSource("let a = 4;", "/** @type {?} */ let b;", "a =b;").run();
    }

    @Test
    public void testOptChainCall_numberCalled() {
      newTest().addSource("3?.();").addDiagnostic("number expressions are not callable").run();
    }

    @Test
    public void testCall_paramMismatch() {
      newTest()
          .addSource("/** @param {!Number} foo*/function bar(foo){ bar('abc'); }")
          .addDiagnostic(
              lines(
                  "actual parameter 1 of bar does not match formal parameter",
                  "found   : string",
                  "required: Number"))
          .run();
    }

    @Test
    public void testOptChainCall_paramMismatch() {
      newTest()
          .addSource("/** @param {!Number} foo*/function bar(foo){ bar?.('abc'); }")
          .addDiagnostic(
              lines(
                  "actual parameter 1 of bar does not match formal parameter",
                  "found   : string",
                  "required: Number"))
          .run();
    }

    @Test
    public void testCall3() {
      // We are checking that an unresolved named type can successfully
      // meet with a functional type to produce a callable type.
      newTest()
          .addSource(
              "/** @type {Function|undefined} */var opt_f;",
              "/** @type {some.unknown.type} */var f1;",
              "var f2 = opt_f || f1;",
              "f2();")
          .addDiagnostic("Bad type annotation. Unknown type some.unknown.type")
          .run();
    }

    @Test
    public void testOptChainCall3() {
      // We are checking that an unresolved named type can successfully
      // meet with a functional type to produce a callable type.
      newTest()
          .addSource(
              "/** @type {Function|undefined} */var opt_f;",
              "/** @type {some.unknown.type} */var f1;",
              "var f2 = opt_f || f1;",
              "f2();")
          .addDiagnostic("Bad type annotation. Unknown type some.unknown.type")
          .run();
    }
  }
}
