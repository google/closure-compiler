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


/**
 * Tests the type transformation language as implemented in the new type inference.
 */

public final class NewTypeInferenceTTLTest extends NewTypeInferenceTestBase {

  public void testTypecheckFunctionBodyWithTTLvars() {
    typeCheck(lines(
        "/**",
        " * @template T := 'number' =:",
        " * @return {T}",
        " */",
        "function f() { return 123; }"));

    // TODO(dimvar): warn for invalid return type.
    typeCheck(lines(
        "/**",
        " * @template T := 'number' =:",
        " * @return {T}",
        " */",
        "function f() { return 'asdf'; }"));

    // TODO(dimvar): warn for invalid assignment.
    typeCheck(lines(
        "/**",
        " * @template T := 'number' =:",
        " * @param {T} x",
        " */",
        "function f(x) { var /** string */ s = x; }"));

    // TODO(dimvar): warn for invalid assignment.
    typeCheck(lines(
        "/**",
        " * @template T := 'number' =:",
        " * @this {T}",
        " */",
        "function f() { var /** string */ s = this; }"));

    // We need to instantiate both T and S to ?, to avoid warning about non-declared return type
    typeCheck(lines(
        "/** @constructor */",
        "function Foo() {}",
        "/**",
        " * @param {T=} x",
        " * @template T",
        " * @template S := cond(isUnknown(T), 'string', T) =:",
        " * @return {T}",
        " */",
        "Foo.prototype.method = function(x) {",
        "  if (x === undefined) {",
        "    return '';",
        "  }",
        "  return x;",
        "}"));

    typeCheck(lines(
        "/** @interface */",
        "function Foo() {}",
        "/**",
        " * @param {T=} x",
        " * @template T",
        " * @template S := cond(isUnknown(T), 'string', T) =:",
        " * @return {T}",
        " */",
        "Foo.prototype.method = function(x) {};",
        "/**",
        " * @constructor",
        " * @implements {Foo}",
        " */",
        "function Bar() {}",
        "/** @override */",
        "Bar.prototype.method = function(x) {",
        "  if (x === undefined) {",
        "    return '';",
        "  }",
        "  return x;",
        "};"));
  }

  public void testBasicTypes() {
    typeCheck(lines(
        "/**",
        " * @template T := 'number' =:",
        " * @return {T}",
        " */",
        "function f() { return 123; }",
        "var /** number */ x = f();"));

    typeCheck(lines(
        "/**",
        " * @template T := 'asdf' =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "f();"),
        TypeTransformation.UNKNOWN_TYPENAME);

    typeCheck(lines(
        "/**",
        " * @template T := 'number' =:",
        " * @return {T}",
        " */",
        "function f() { return 123; }",
        "var /** string */ x = f();"),
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(lines(
        "/**",
        " * @template T := all() =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "var /** * */ x = f();"));

    typeCheck(lines(
        "/**",
        " * @template T := all() =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "var /** number */ x = f();"),
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(lines(
        "/**",
        " * @template T := unknown() =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "var /** number */ x = f();"));

    typeCheck(lines(
        "/**",
        " * @template T := none() =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "var /** number */ x = f();",
        "var /** string */ y = f();"));
  }

  public void testUnions() {
    typeCheck(lines(
        "/**",
        " * @template T := union('number', 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return 123; }",
        "var /** (number|string) */ x = f();"));

    typeCheck(lines(
        "/**",
        " * @template T := union('number', 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return 123; }",
        "var /** number */ x = f();"),
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    // The union is ? because asdf is not defined
    typeCheck(lines(
        "/**",
        " * @template T := union('number', 'asdf') =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "var /** null */ x = f();"),
        TypeTransformation.UNKNOWN_TYPENAME);
  }

  public void testGenerics() {
    typeCheck(lines(
        "/**",
        " * @template T := type('Array', 'number') =:",
        " * @return {T}",
        " */",
        "function f() { return []; }",
        "var /** !Array<number> */ x = f();"));

    typeCheck(lines(
        "/**",
        " * @template T := type('Array', 'number') =:",
        " * @return {T}",
        " */",
        "function f() { return []; }",
        "var /** !Array<string> */ x = f();"),
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(lines(
        "/**",
        " * @template T := type('string', 'number') =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "f();"),
        TypeTransformation.BASETYPE_INVALID);

    // TODO(dimvar): it would be good to analyze TTL function definitions by themselves, without
    // waiting for them to be called. E.g., here we would catch the BASETYPE_INVALID error.
    typeCheck(lines(
        "/**",
        " * @template T := type('string', 'number') =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }"));

    typeCheck(lines(
        "/** @typedef {!Array<number>} */",
        "var ArrayNumber;",
        "/**",
        " * @template T := 'ArrayNumber' =:",
        " * @return {T}",
        " */",
        "function f() { return []; }",
        "f();"));

    typeCheck(lines(
        "/**",
        " * @template T := rawTypeOf(type('Array', 'number')) =:",
        " * @return {T}",
        " */",
        "function f() { return []; }",
        "var /** !Array<?> */ x = f();"));

    typeCheck(lines(
        "/**",
        " * @template T := rawTypeOf('number') =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "f();"),
        TypeTransformation.TEMPTYPE_INVALID);

    typeCheck(lines(
        "/**",
        " * @template T := templateTypeOf(type('Array', 'number'), 0) =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "var /** number */ x = f();"));

    // templateTypeOf is 0-based
    typeCheck(lines(
        "/**",
        " * @template T := templateTypeOf(type('Array', 'number'), 1) =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "var /** number */ x = f();"),
        TypeTransformation.INDEX_OUTOFBOUNDS);

    typeCheck(lines(
        "/**",
        " * @template T := templateTypeOf('number', 0) =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "var /** number */ x = f();"),
        TypeTransformation.TEMPTYPE_INVALID);

    typeCheck(lines(
        "/**",
        " * @constructor",
        " * @template T, U",
        " */",
        "function Foo() {}",
        "/**",
        " * @template T := templateTypeOf(type('Foo', 'number', 'string'), 1) =:",
        " * @return {T}",
        " */",
        "function f() { return ''; }",
        "var /** number */ x = f();"),
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    // TODO(dimvar): it'd be nice to warn about "too many type arguments" here.
    typeCheck(lines(
        "/**",
        " * @template T := type('Array', 'number', 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return []; }",
        "var /** !Array<number> */ x = f();"));

    typeCheck(lines(
        "/**",
        " * @constructor",
        " * @template T, U",
        " */",
        "function Foo() {}",
        "/**",
        " * @template T := type('Foo', 'number') =:",
        " * @return {T}",
        " */",
        "function f() { return new Foo; }",
        "var /** !Foo<number, ?> */ x = f();"));
  }

  public void testConditionals() {
    typeCheck(lines(
        "/**",
        " * @template T := cond(eq('number', 'number'), 'number', 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return 1; }",
        "var /** number */ n = f();"));

    typeCheck(lines(
        "/**",
        " * @template T := cond(eq('number', 'number'), 'number', 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return 'asdf'; }",
        "var /** string */ s = f();"),
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(lines(
        "/**",
        " * @template T := cond(eq('number', 'undefined'), 'number', 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return 'asdf'; }",
        "var /** string */ s = f();"));

    typeCheck(lines(
        "/**",
        " * @template T := cond(sub('Number', 'Object'), 'number', 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return 'asdf'; }",
        "var /** number */ n = f();"));

    typeCheck(lines(
        "/**",
        " * @template T := cond(sub('Number', 'Number'), 'number', 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return 123; }",
        "var /** number */ n = f();"));

    typeCheck(lines(
        "/**",
        " * @template T := cond(sub('Object', 'Number'), 'number', 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return 'asdf'; }",
        "var /** string */ s = f();"));

    typeCheck(lines(
        "/**",
        " * @template T := cond(eq('number', 'asdf'), 'number', 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "var /** string */ s = f();"),
        TypeTransformation.UNKNOWN_TYPENAME);

    typeCheck(lines(
        "/** @constructor */",
        "function Foo() {}",
        "/**",
        " * @template T := cond(isCtor(typeOfVar('Foo')), 'number', 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return 123; }",
        "var /** number */ n = f();"));

    typeCheck(lines(
        "/**",
        " * @template T := cond(isCtor('number'), 'number', 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return 'asdf'; }",
        "var /** string */ s = f();"));
  }

  public void testMapUnion() {
    typeCheck(lines(
        "/**",
        " * @template T := mapunion(union('number', 'string'), (x) => 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return 'asdf'; }",
        "var /** string */ s = f();"));

    typeCheck(lines(
        "/**",
        " * @template T := mapunion(union('number', 'string'), (x) => 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return 'asdf'; }",
        "var /** number */ n = f();"),
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(lines(
        "/**",
        " * @template T :=",
        "     mapunion(union('number', 'string'), (x) => cond(eq(x, 'number'), none(), x)) =:",
        " * @return {T}",
        " */",
        "function f() { return 'asdf'; }",
        "var /** string */ s = f();"));

    typeCheck(lines(
        "/**",
        " * @template T :=",
        "     mapunion(union('number', 'string'), (x) =>",
        "       mapunion(union('number', 'boolean'), (y) => cond(eq(x, y), x, none()))) =:",
        " * @return {T}",
        " */",
        "function f() { return 123; }",
        "var /** number */ n = f();"));

    typeCheck(lines(
        "/**",
        " * @template T :=",
        "     mapunion(union('number', 'string'), (x) =>",
        "       mapunion(union('number', 'boolean'), (y) => cond(eq(x, y), x, none()))) =:",
        " * @return {T}",
        " */",
        "function f() { return 123; }",
        "var /** string */ s = f();"),
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(lines(
        "/**",
        " * @template T :=",
        " *   mapunion(",
        " *     union('number','string','boolean','null','undefined'),",
        " *     (x) =>",
        " *     cond(eq(x, 'number'), 'Number',",
        " *     cond(eq(x, 'string'), 'String',",
        " *     cond(eq(x, 'boolean'), 'Boolean',",
        " *     cond(eq(x, 'null'), 'Object',",
        " *     cond(eq(x, 'undefined'), 'Object', x))))))",
        " *   =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "var /** !Object */ x = f();"));

    typeCheck(lines(
        "/**",
        " * @template T := mapunion('number', (x) => 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return 'asdf'; }",
        "var /** string */ s = f();"));

    typeCheck(lines(
        "/**",
        " * @template T := mapunion(union('number', 'string'), (x) => y) =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "var /** string */ s = f();"),
        TypeTransformation.UNKNOWN_TYPEVAR);

    typeCheck(lines(
        "/**",
        " * @template T := mapunion('number',",
        " *   (x) => mapunion('number', (x) => 'string')) =:",
        " */",
        "function f() {}",
        "f();"),
        TypeTransformation.DUPLICATE_VARIABLE);

    // If the same variable name is used in non-nested scopes it's OK
    typeCheck(lines(
        "/**",
        " * @template T := cond(eq('number', 'number'),",
        " *    mapunion('number', (x) => 'string'),",
        " *    mapunion('number', (x) => 'string')) =:",
        " */",
        "function f() {}",
        "f();"));
  }

  public void testRecord() {
    typeCheck(lines(
        "/**",
        " * @template T := record({a: 'number', b: 'string'}) =:",
        " * @return {T}",
        " */",
        "function f() { return { a: 1, b: 'adsf' }; }",
        "var /** number */ x = f().a;",
        "var /** string */ y = f().b;"));

    typeCheck(lines(
        "/**",
        " * @template T := record({a: 'number'}, {b: 'string'}) =:",
        " * @return {T}",
        " */",
        "function f() { return { a: 1, b: 'adsf' }; }",
        "var /** string */ x = f().a;"),
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(lines(
        "/** @constructor */",
        "function Foo() {",
        "  /** @type {number} */",
        "  this.a = 123;",
        "}",
        "/** @type {string} */",
        "Foo.prototype.b = '';",
        "/**",
        " * @template T := record('Foo') =:",
        " * @return {T}",
        " */",
        "function f() { return new Foo; }",
        "var /** number */ x = f().a;",
        "var /** string */ y = f().b;"));

    // The properties of the supertype are not included in the result.
    // OTI doesn't type them, but doesn't warn either.
    typeCheck(lines(
        "/** @constructor */",
        "function Foo() {",
        "  /** @type {number} */",
        "  this.a = 1;",
        "}",
        "/** @constructor @extends {Foo} */",
        "function Bar() {}",
        "/**",
        " * @template T := record('Bar') =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "var /** string */ s = f().a;"),
        NewTypeInference.INEXISTENT_PROPERTY);

    typeCheck(lines(
        "/**",
        " * @template T := record('number') =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "f();"),
        TypeTransformation.RECPARAM_INVALID);
  }

  public void testPropType() {
    typeCheck(lines(
        "/**",
        " * @template T := propType('a', record({a: 'number', b: 'string'})) =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "var /** number */ x = f();"));

    // propType on a missing property returns unknown.
    typeCheck(lines(
        "/**",
        " * @template T := propType('c', record({a: 'number', b: 'string'})) =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "var /** null */ x = f();",
        "var /** boolean */ y = f();"));

    typeCheck(lines(
        "/**",
        " * @template T := propType('a', 'number') =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "f();"),
        TypeTransformation.PROPTYPE_INVALID);
  }

  public void testTypeExpression() {
    typeCheck(lines(
        "/**",
        " * @template T := typeExpr('?Object') =:",
        " * @return {T}",
        " */",
        "function f() { return {}; }",
        "var /** !Object */ x = f();"),
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(lines(
        "/**",
        " * @template T := typeExpr('!Object') =:",
        " * @return {T}",
        " */",
        "function f() { return {}; }",
        "var /** !Object */ x = f();"));

    // Preserving the OTI behavior where there is no warning for bad types inside typeExpr.
    // Could tighten this in the future.
    typeCheck(lines(
        "/**",
        " * @template T := typeExpr('asdf') =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "var /** null */ x = f();"));
  }

  public void testMapRecord() {
    typeCheck(lines(
        "/**",
        " * @template T := maprecord(record({a: 'number'}),",
        " *   (k, v) => record({[k]: 'string'})) =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "var /** string */ s = f().a;"));

    typeCheck(lines(
        "/**",
        " * @template T := maprecord(record({a: 'number'}),",
        " *   (k, v) => record({[k]: v})) =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "var /** number */ x = f().a;"));

    typeCheck(lines(
        "/**",
        " * @template T := maprecord(record({a: 'number'}), (k, v) => none()) =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "var /** string */ s = f().a;"),
        NewTypeInference.INEXISTENT_PROPERTY);

    typeCheck(lines(
        "/**",
        " * @template T := maprecord(record({a: 'number'}), (k, v) => 'number') =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "f();"),
        TypeTransformation.MAPRECORD_BODY_INVALID);

    typeCheck(lines(
        "/**",
        " * @template T := maprecord(record({a: 'number'}),",
        " *   (k, v) => record({b: 'string'})) =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "var /** string */ s = f().b;"));

    // c is typed string, not (number|string). This means that its type is dependent on the order
    // of iteration of the record's properties. Not ideal, but preserving the OTI behavior here.
    // Maybe worth changing in the future.
    typeCheck(lines(
        "/**",
        " * @template T := maprecord(record({a: 'number', b: 'string'}),",
        " *   (k, v) => record({c: v})) =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "function g(/** (number|string) */ x) {",
        "  f().c = x;",
        "}"),
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(lines(
        "/**",
        " * @template T := maprecord(record({a: 'number'}),",
        " *   (k, v) => record({[k2]: 'string'})) =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "f();"),
        TypeTransformation.UNKNOWN_NAMEVAR,
        TypeTransformation.RECPARAM_INVALID);

    typeCheck(lines(
        "/**",
        " * @template T := maprecord('number', (k, v) => record({[k]: 'string'})) =:",
        " */",
        "function f() {}",
        "f();"),
        TypeTransformation.RECTYPE_INVALID);

    typeCheck(lines(
        "/**",
        " * @template T := maprecord(record({a: 'number'}), (k, v) => 'number') =:",
        " */",
        "function f() {}",
        "f();"),
        TypeTransformation.MAPRECORD_BODY_INVALID);
  }

  public void testIsTemplatized() {
    // 'Array' is raw Array, not Array<?>.
    typeCheck(lines(
        "/**",
        " * @template T := cond(isTemplatized('Array'), 'number', 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return 'asdf'; }",
        "var /** string */ s = f();"));

    typeCheck(lines(
        "/**",
        " * @template T := cond(isTemplatized(type('Array', unknown())), 'number', 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return 123; }",
        "var /** number */ n = f();"));

    typeCheck(lines(
        "/**",
        " * @template T := cond(isTemplatized(type('Array', 'number')), 'number', 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return 123; }",
        "var /** number */ n = f();"));


    typeCheck(lines(
        "/**",
        " * @template T := cond(isTemplatized('number'), 'number', 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return 'asdf'; }",
        "var /** string */ s = f();"));

    // isTemplatized is true for partially instantiated types
    typeCheck(lines(
        "/**",
        " * @constructor",
        " * @template T, U",
        " */",
        "function Foo() {}",
        "/**",
        " * @template T := cond(isTemplatized(type('Foo', 'number')), 'number', 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return 123; }",
        "var /** number */ x = f();"));
  }

  public void testIsRecord() {
    typeCheck(lines(
        "/**",
        " * @template T := cond(isRecord(record({a: 'number'})), 'number', 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return 123; }",
        "var /** number */ n = f();"));

    typeCheck(lines(
        "/**",
        " * @template T := cond(isRecord(unknown()), 'number', 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return 'asdf'; }",
        "var /** string */ s = f();"));

    typeCheck(lines(
        "/**",
        " * @template T := cond(isRecord('Array'), 'number', 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return 'asdf'; }",
        "var /** string */ s = f();"));
  }

  public void testIsUnknown() {
    typeCheck(lines(
        "/**",
        " * @template T := cond(isUnknown(unknown()), 'number', 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return 123; }",
        "var /** number */ n = f();"));

    typeCheck(lines(
        "/**",
        " * @template T := cond(isUnknown(all()), 'number', 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return 'asdf'; }",
        "var /** string */ s = f();"));

    typeCheck(lines(
        "/**",
        " * @template T := cond(isUnknown('number'), 'number', 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return 'asdf'; }",
        "var /** string */ s = f();"));
  }

  public void testBooleanOperators() {
    typeCheck(lines(
        "/**",
        " * @template T := cond(isUnknown(unknown()) && isRecord(record({a: 'number'})),",
        " *   'number', 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return 123; }",
        "var /** number */ n = f();"));

    typeCheck(lines(
        "/**",
        " * @template T := cond(isUnknown('number') || isRecord('number'),",
        " *   'number', 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return 'asdf'; }",
        "var /** string */ s = f();"));

    typeCheck(lines(
        "/**",
        " * @template T := cond(!isUnknown(unknown()), 'number', 'string') =:",
        " * @return {T}",
        " */",
        "function f() { return 'asdf'; }",
        "var /** string */ s = f();"));
  }

  public void testInstanceOf() {
    // typeOfVar('Foo') returns the constructor Foo
    typeCheck(lines(
        "/**",
        " * @template T := instanceOf(typeOfVar('Array')) =:",
        " * @return {T}",
        " */",
        "function f() { return []; }",
        "var /** !Array */ x = f();"));

    // number is a scalar so it can't be referring to a constructor type
    typeCheck(lines(
        "/**",
        " * @template T := instanceOf('number') =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "f();"),
        TypeTransformation.INVALID_CTOR);
  }

  public void testTypeOfVar() {
    typeCheck(lines(
        "/**",
        " * @template T := typeOfVar('Array') =:",
        " * @return {T}",
        " */",
        "function f() { return Array; }",
        "var /** !Function */ x = f();"));

    typeCheck(lines(
        "/**",
        " * @template T := typeOfVar('asdf') =:",
        " * @return {T}",
        " */",
        "function f() { return any(); }",
        "f();"),
        TypeTransformation.VAR_UNDEFINED);
  }

  public void testTtlFunctionTypesPropagate() {
    typeCheck(lines(
        "/**",
        " * @template T := 'number' =:",
        " * @return {T}",
        " */",
        "function f() { return 123; }",
        "var g = f;",
        "var /** string */ x = g();"),
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(lines(
        "/** @interface */",
        "function Foo() {}",
        "/**",
        " * @template T := 'string' =:",
        " * @return {T}",
        " */",
        "Foo.prototype.method = function() {};",
        "/**",
        " * @constructor",
        " * @implements {Foo}",
        " */",
        "function Bar() {}",
        "/** @override */",
        "Bar.prototype.method = function() { return 'asdf' };",
        "var /** number */ x = (new Bar).method()"),
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  // When the TTL expression is evaluated we want lexical scope, so the return type of f()
  // must be the outer Foo.
  public void testNoDynamicScopingWhenEvaluatingTTL() {
    typeCheck(lines(
        "/** @constructor */",
        "function Foo() {}",
        "var /** !Foo */ x;",
        "/**",
        " * @template T := 'Foo' =:",
        " * @return {T}",
        " */",
        "function f() { return new Foo; }",
        "function g() {",
        "  /** @constructor */",
        "  function Foo() {}",
        "  x = f();",
        "}"));
  }

  public void testTypedefsWithTTL() {
    typeCheck(lines(
        "/** @typedef {number} */",
        "var MyNum;",
        "/**",
        " * @template T := 'MyNum' =:",
        " * @return {T}",
        " */",
        "function f() { return 123; }",
        "var /** number */ x = f();"));

    typeCheck(lines(
        "/** @typedef {number} */",
        "var MyNum;",
        "/**",
        " * @template T := 'MyNum' =:",
        " * @return {T}",
        " */",
        "function f() { return 123; }",
        "var /** string */ x = f();"),
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(lines(
        "/** @const */",
        "var ns = {};",
        "/** @typedef {number} */",
        "ns.MyNum;",
        "/**",
        " * @template T := 'ns.MyNum' =:",
        " * @return {T}",
        " */",
        "function f() { return 123; }",
        "var /** string */ x = f();"),
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(lines(
        "/** @const */",
        "var ns = {};",
        "function g() {",
        "  /** @typedef {number} */",
        "  ns.MyNum;",
        "}",
        "/**",
        " * @template T := 'ns.MyNum' =:",
        " * @return {T}",
        " */",
        "function f() { return 123; }",
        "var /** string */ x = f();"),
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testUsingOrdinaryTypeVariablesInTTL() {
    typeCheck(lines(
        "/**",
        " * @param {T} x",
        " * @param {U} y",
        " * @return {R}",
        " * @template T, U",
        " * @template R := cond (eq(T, U), 'string', 'boolean') =:",
        " */",
        "function f(x, y) { return any(); }",
        "var /** string */ s = f({}, []);"),
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(lines(
        "/**",
        " * @param {T} x",
        " * @param {U} y",
        " * @return {R}",
        " * @template T, U",
        " * @template R := cond (eq(T, U), 'string', 'boolean') =:",
        " */",
        "function f(x, y) { return any(); }",
        "var /** string */ s = f(1, 2);",
        "var /** boolean */ b = f({}, []);"));

    typeCheck(lines(
        "/**",
        " * @param {T} x",
        " * @template T",
        " * @template R :=",
        " *   mapunion(",
        " *     T,",
        " *     (x) =>",
        " *     cond(eq(x, 'number'), 'Number',",
        " *     cond(eq(x, 'string'), 'String',",
        " *     cond(eq(x, 'boolean'), 'Boolean',",
        " *     cond(eq(x, 'null'), 'Object',",
        " *     cond(eq(x, 'undefined'), 'Object', x))))))",
        " *   =:",
        " * @return {R}",
        " */",
        "function f(x) { return any(); }",
        "var /** !Number */ x = f(1);",
        "var /** !String */ y = f('');"));
  }

  public void testCreateDomPattern() {
    typeCheck(lines(
        "/**",
        " * @constructor",
        " * @template T",
        " */",
        "function TagName() {}",
        "/** @constructor */",
        "function Foo() {}",
        "/** @type {!TagName<!Foo>} */",
        "TagName.FOO;",
        "/**",
        " * @template T",
        " * @param {string|!TagName<T>} x",
        " * @template R := cond(isUnknown(T), 'Object', T)  =:",
        " * @return {R}",
        " */",
        "function f(x) { return any(); }",
        "var /** !Foo */ x = f(TagName.FOO);"));
  }

  public void testPromiseThen() {
    typeCheck(lines(
        "/**",
        " * @interface",
        " * @template TYPE",
        " */",
        "function MyThenable() {};",
        "/**",
        " * @param {?(function(this:THIS, TYPE): VALUE)=} opt_onFulfilled",
        " * @param {?(function(this:THIS, *): *)=} opt_onRejected",
        " * @param {THIS=} opt_context *",
        " * @return {RESULT}",
        " * @template VALUE",
        " * @template THIS",
        " *",
        " * @template RESULT := type('MyPromise',",
        " *     cond(isUnknown(VALUE), unknown(),",
        " *       mapunion(VALUE, (V) =>",
        " *         cond(isTemplatized(V) && sub(rawTypeOf(V), 'IThenable'),",
        " *           templateTypeOf(V, 0),",
        " *           cond(sub(V, 'Thenable'),",
        " *              unknown(),",
        " *              V)))))",
        " *  =:",
        " *",
        " */",
        "MyThenable.prototype.then = function(",
        "    opt_onFulfilled, opt_onRejected, opt_context) {};",
        "/**",
        " * @constructor",
        " * @implements {MyThenable<TYPE>}",
        " * @template TYPE",
        " */",
        "function MyPromise() {}",
        "/** @override */",
        "MyPromise.prototype.then = function(x, y, z) { return any(); };",
        "var x = (new MyPromise).then(null);"));
  }

  public void testInferUnannotatedCallback() {
    typeCheck(LINE_JOINER.join(
        "/**",
        " * @param {!Promise<!string>} a",
        " * @return {!Promise<!number>}",
        " */",
        "function f(a) {",
        "  var b = a;",
        "  return b.then(function(x) {",
        "    return x - 1;",
        "  });",
        "}"),
        NewTypeInference.INVALID_OPERAND_TYPE);
  }
}
