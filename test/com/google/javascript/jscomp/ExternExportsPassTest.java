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

import static com.google.common.truth.Truth.assertThat;

import com.google.javascript.jscomp.testing.NoninjectingCompiler;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ExternExportsPass}. */
@RunWith(JUnit4.class)
public final class ExternExportsPassTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
  }

  @Override
  protected Compiler createCompiler() {
    // For those test cases that perform transpilation, we don't want any of the runtime code
    // to be injected, since that will just make the test cases harder to read and write.
    return new NoninjectingCompiler();
  }

  @Override
  public CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setExternExportsPath("exports.js");
    // Check types so we can make sure our exported externs have type information.
    options.setCheckSymbols(true);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ExternExportsPass(compiler);
  }

  @Test
  public void testExportSymbol() {
    compileAndCheck(
        """
        /** @const */ var a = {};
        /** @const */ a.b = {};
        a.b.c = function(d, e, f) {};
        var x;
        // Ensure we don't have a recurrence of a bug that caused us to consider the last
        // assignment containing 'a.b.c' to be its definition, even if it was on the rhs.
        x = a.b.c;
        goog.exportSymbol('foobar', a.b.c)
        """,
        """
        /**
         * @param {?} d
         * @param {?} e
         * @param {?} f
         * @return {undefined}
         */
        var foobar = function(d, e, f) {
        };
        """);
  }

  @Test
  public void exportClassAsSymbolAndMethodAsProperty() {
    compileAndCheck(
        """
        /** @const */ var a = {};
        /** @const */ a.b = {};
        a.b.c = class {
          constructor(d, e, f) {
            /** @export {number} */ this.thisProp = 1;
          }
          /**
           * @param {string} a
           * @return {number}
           */
          method(a) {}

          static staticMethod() {}
        };
        goog.exportSymbol('foobar', a.b.c)
        goog.exportProperty(a.b.c.prototype, 'exportedMethod', a.b.c.prototype.method)
        goog.exportProperty(a.b.c, 'exportedStaticMethod', a.b.c.staticMethod)
        """,
        // TODO(b/123352214): `@this {!a.b.c}` should be renamed to `foobar`
        """
        /**
         * @param {?} d
         * @param {?} e
         * @param {?} f
         * @constructor
         */
        var foobar = function(d, e, f) {
        };
        /**
         * @return {undefined}
         * @this {(typeof a.b.c)}
         */
        foobar.exportedStaticMethod = function() {
        };
        /**
         * @param {string} a
         * @return {number}
         * @this {!a.b.c}
         */
        foobar.prototype.exportedMethod = function(a) {
        };
        foobar.prototype.thisProp;
        """);
  }

  @Test
  public void noAtThisIsGeneratedForClassMethodWhenExportedClassNameMatches() {
    compileAndCheck(
        """
        class Foo {
          /**
           * @param {string} a
           * @return {number}
           */
          method(a) {}
        };
        goog.exportSymbol('Foo', Foo)
        goog.exportProperty(Foo.prototype, 'method', Foo.prototype.method)
        """,
        // @this not generated on the method because exported and internal class names are the same
        """
        /**
         * @constructor
         */
        var Foo = function() {
        };
        /**
         * @param {string} a
         * @return {number}
         */
        Foo.prototype.method = function(a) {
        };
        """);
  }

  @Test
  public void exportClassHierarchyAsSymbols() {
    compileAndCheck(
        """
        /** @const */ var a = {};
        /** @const */ a.b = {};
        a.b.c = class {
          constructor(d, e, f) {}
        };
        goog.exportSymbol('foobar', a.b.c)
        a.b.d = class extends a.b.c {
          constructor(d, e, f) {}
        };
        goog.exportSymbol('bazboff', a.b.d)
        """,
        // NOTE: foobar and bazboff end up sorted in ASCII order. This is expected.
        // TODO(b/123352214): @extends {a.b.c} should be updated
        """
        /**
         * @param {?} d
         * @param {?} e
         * @param {?} f
         * @extends {a.b.c}
         * @constructor
         */
        var bazboff = function(d, e, f) {
        };
        /**
         * @param {?} d
         * @param {?} e
         * @param {?} f
         * @constructor
         */
        var foobar = function(d, e, f) {
        };
        """);
  }

  @Test
  public void testInterface() {
    compileAndCheck(
        """
        /** @interface */ function Iface() {};
        goog.exportSymbol('Iface', Iface)
        """,
        """
        /**
         * @interface
         */
        var Iface = function() {
        };
        """);
  }

  @Test
  public void exportInterfaceDefinedWithClass() {
    compileAndCheck(
        """
        /** @interface */ class Iface {};
        goog.exportSymbol('Iface', Iface)
        """,
        """
        /**
         * @interface
         */
        var Iface = function() {
        };
        """);
  }

  @Test
  public void exportInterfaceExtendedWithClass() {
    compileAndCheck(
        """
        /** @interface */ class Iface {};
         goog.exportSymbol('Iface', Iface)
        /** @interface */ class ExtendedIface extends Iface {};
         goog.exportSymbol('ExtendedIface', ExtendedIface)
        """,
        // order of the interfaces is reversed, but that doesn't matter
        """
        /**
         * @extends {Iface}
         * @interface
         */
        var ExtendedIface = function() {
        };
        /**
         * @interface
         */
        var Iface = function() {
        };
        """);
  }

  @Test
  public void exportInterfaceDefinedWithClassThatExtendsUnexportedInterface() {
    compileAndCheck(
        """
        /** @interface */ class Iface {}; // not exported
        /** @interface */ class ExtendedIface extends Iface {};
         goog.exportSymbol('ExtendedIface', ExtendedIface)
        """,
        """
        /**
         * @extends {Iface}
         * @interface
         */
        var ExtendedIface = function() {
        };
        """);
  }

  @Test
  public void testRecord() {
    compileAndCheck(
        "/** @record */ function Iface() {}; goog.exportSymbol('Iface', Iface)",
        """
        /**
         * @record
         */
        var Iface = function() {
        };
        """);
  }

  @Test
  public void exportRecordDefinedWithClass() {
    compileAndCheck(
        """
        /** @record */ class Iface {};
        goog.exportSymbol('Iface', Iface)
        """,
        """
        /**
         * @record
         */
        var Iface = function() {
        };
        """);
  }

  @Test
  public void exportRecordExtendedWithClass() {
    compileAndCheck(
        """
        /** @record */ class Iface {};
         goog.exportSymbol('Iface', Iface)
        /** @record */ class ExtendedIface extends Iface {};
         goog.exportSymbol('ExtendedIface', ExtendedIface)
        """,
        // order of the interfaces is reversed, but that doesn't matter
        """
        /**
         * @extends {Iface}
         * @record
         */
        var ExtendedIface = function() {
        };
        /**
         * @record
         */
        var Iface = function() {
        };
        """);
  }

  @Test
  public void testExportSymbolDefinedInVar() {
    compileAndCheck(
        "var a = function(d, e, f) {}; goog.exportSymbol('foobar', a)",
        """
        /**
         * @param {?} d
         * @param {?} e
         * @param {?} f
         * @return {undefined}
         */
        var foobar = function(d, e, f) {
        };
        """);
  }

  @Test
  public void exportClassDefinedWithConst() {
    compileAndCheck(
        """
        const a = class {
          constructor(d, e, f) {}
        };
        goog.exportSymbol('foobar', a)
        """,
        // const becomes a var because we only generate var definitions for now
        """
        /**
         * @param {?} d
         * @param {?} e
         * @param {?} f
         * @constructor
         */
        var foobar = function(d, e, f) {
        };
        """);
  }

  @Test
  public void exportClassDefinedWithLet() {
    compileAndCheck(
        """
        let a = class {
          constructor(d, e, f) {}
        };
        goog.exportSymbol('foobar', a)
        """,
        // The let becomes a var because we only generate var definitions for now
        """
        /**
         * @param {?} d
         * @param {?} e
         * @param {?} f
         * @constructor
         */
        var foobar = function(d, e, f) {
        };
        """);
  }

  @Test
  public void testExportProperty() {
    compileAndCheck(
        """
        /** @const */ var a = {};
        /** @const */ a.b = {};
        a.b.c = function(d, e, f) {};
        goog.exportProperty(a.b, 'cprop', a.b.c)
        """,
        """
        var a;
        a.b;
        /**
         * @param {?} d
         * @param {?} e
         * @param {?} f
         * @return {undefined}
         */
        a.b.cprop = function(d, e, f) {
        };
        """);
  }

  @Test
  public void exportClassAsNamespaceProperty() {
    compileAndCheck(
        """
        const a = {};
        /** @const */ a.b = {};
        a.b.c = class {
          constructor(d, e, f) {}
        };
        goog.exportProperty(a.b, 'cprop', a.b.c)
        """,
        """
        /**
         * @const
         * @suppress {const,duplicate}
         */
        var a = {};
        /**
         * @const
         * @suppress {const,duplicate}
         */
        a.b = {};
        /**
         * @param {?} d
         * @param {?} e
         * @param {?} f
         * @constructor
         */
        a.b.cprop = function(d, e, f) {
        };
        """);
  }

  @Test
  public void exportQNameFunctionAsSymbolPlusPropertiesOnIt() {
    compileAndCheck(
        """
        /** @const */ var a = {};
        a.b = function(p1) {};
        /** @constructor */
        a.b.c = function(d, e, f) {};
        /** @return {number} */
        a.b.c.prototype.method = function(g, h, i) {};
        goog.exportSymbol('a.b', a.b);
        goog.exportProperty(a.b, 'c', a.b.c);
        goog.exportProperty(a.b.c.prototype, 'exportedMethod', a.b.c.prototype.method);
        """,
        """
        var a;
        /**
         * @param {?} p1
         * @return {undefined}
         */
        a.b = function(p1) {
        };
        /**
         * @param {?} d
         * @param {?} e
         * @param {?} f
         * @constructor
         */
        a.b.c = function(d, e, f) {
        };
        /**
         * @param {?} g
         * @param {?} h
         * @param {?} i
         * @return {number}
         */
        a.b.c.prototype.exportedMethod = function(g, h, i) {
        };
        """);
  }

  @Test
  public void exportQNameClassAsSymbolPlusPropertiesOnIt() {
    compileAndCheck(
        """
        const a = {};
        a.b = class {
          constructor(p1) {
          }
        };
        a.b.c = function(d, e, f) {};
        a.b.prototype.c = function(g, h, i) {};
        goog.exportSymbol('a.b', a.b);
        goog.exportProperty(a.b, 'c', a.b.c);
        goog.exportProperty(a.b.prototype, 'c', a.b.prototype.c);
        """,
        """
        /**
         * @const
         * @suppress {const,duplicate}
         */
        var a = {};
        /**
         * @param {?} p1
         * @constructor
         */
        a.b = function(p1) {
        };
        /**
         * @param {?} d
         * @param {?} e
         * @param {?} f
         * @return {undefined}
         */
        a.b.c = function(d, e, f) {
        };
        /**
         * @param {?} g
         * @param {?} h
         * @param {?} i
         * @return {undefined}
         */
        a.b.prototype.c = function(g, h, i) {
        };
        """);
  }

  @Test
  public void symbolsRenamedInExportAreAlsoRenamedInExportedChildQNames() {
    compileAndCheck(
        """
        /** @const */ var a = {};
        a.b = function(p1) {};
        a.b.c = function(d, e, f) {};
        a.b.prototype.c = function(g, h, i) {};
        goog.exportSymbol('hello', a);
        goog.exportProperty(a.b, 'c', a.b.c);
        goog.exportProperty(a.b.prototype, 'c', a.b.prototype.c);
        """,
        """
        /** @type {{b: function(?): undefined}} */
        var hello = {};
        hello.b;
        /**
         * @param {?} d
         * @param {?} e
         * @param {?} f
         * @return {undefined}
         */
        hello.b.c = function(d, e, f) {
        };
        /**
         * @param {?} g
         * @param {?} h
         * @param {?} i
         * @return {undefined}
         */
        hello.b.prototype.c = function(g, h, i) {
        };
        """);
  }

  @Test
  public void exportingQnameAsSimpleNameAffectsLaterPropertyExports() {
    compileAndCheck(
        """
        /** @const */ var a = {};
        a.b = function(p1) {};
        a.b.c = function(d, e, f) {};
        a.b.prototype.c = function(g, h, i) {};
        goog.exportSymbol('prefix', a.b);
        goog.exportProperty(a.b, 'c', a.b.c);
        """,
        """
        /**
         * @param {?} p1
         * @return {undefined}
         */
        var prefix = function(p1) {
        };
        /**
         * @param {?} d
         * @param {?} e
         * @param {?} f
         * @return {undefined}
         */
        prefix.c = function(d, e, f) {
        };
        """);
  }

  @Test
  public void testExportNonStaticSymbol() {
    compileAndCheck(
        """
        /** @const */ var a = {};
        /** @const */ a.b = {};
        /** @const */ var d = {};
        a.b.c = d;
        goog.exportSymbol('foobar', a.b.c)
        """,
        "var foobar;\n");
  }

  @Test
  public void testExportNonStaticSymbol2() {
    compileAndCheck(
        """
        /** @const */ var a = {};
        /** @const */ a.b = {};
        var d = function() {};
        a.b.c = d;
        goog.exportSymbol('foobar', a.b.c())
        """,
        "var foobar;\n");
  }

  @Test
  public void testExportNonexistentProperty() {
    compileAndCheck(
        """
        /** @fileoverview @suppress {missingProperties} */
        /** @const */ var a = {};
        /** @const */ a.b = {};
        a.b.c = function(d, e, f) {};
        goog.exportProperty(a.b, 'none', a.b.none)
        """,
        """
        var a;
        a.b;
        a.b.none;
        """);
  }

  @Test
  public void testExportSymbolWithTypeAnnotation() {

    compileAndCheck(
        """
        var internalName;
        /**
         * @param {string} param1
         * @param {number} param2
         * @return {string}
         */
        internalName = function(param1, param2) {
        return param1 + param2;
        };
        goog.exportSymbol('externalName', internalName)
        """,
        """
        /**
         * @param {string} param1
         * @param {number} param2
         * @return {string}
         */
        var externalName = function(param1, param2) {
        };
        """);
  }

  @Test
  public void testExportSymbolWithTemplateAnnotation() {

    compileAndCheck(
        """
        var internalName;
        /**
         * @param {T} param1
         * @return {T}
         * @template T
         */
        internalName = function(param1) {
        return param1;
        };
        goog.exportSymbol('externalName', internalName)
        """,
        """
        /**
         * @param {T} param1
         * @return {T}
         * @template T
         */
        var externalName = function(param1) {
        };
        """);
  }

  @Test
  public void exportSubClassWithoutConstructor() {
    compileAndCheck(
        """
        class SuperClass {
          /**
           * @param {number} num
           */
           constructor(num) {
           }
        }
        goog.exportSymbol('SuperClass', SuperClass);
        class SubClass extends SuperClass {
        }
        goog.exportSymbol('SubClass', SubClass);
        """,
        // The "a" parameter name is automatically generated
        """
        /**
         * @param {number} a
         * @extends {SuperClass}
         * @constructor
         */
        var SubClass = function(a) {
        };
        /**
         * @param {number} num
         * @constructor
         */
        var SuperClass = function(num) {
        };
        """);
  }

  @Test
  public void exportClassWithTemplateAnnotation() {

    compileAndCheck(
        """
        var internalName;
        /**
         * @template T
         */
        internalName = class {
          /**
           * @param {T} param1
           */
          constructor(param1) {
            /** @const */
            this.data = param1;
          }
        };
        goog.exportSymbol('externalName', internalName)
        """,
        """
        /**
         * @param {T} param1
         * @constructor
         * @template T
         */
        var externalName = function(param1) {
        };
        """);
  }

  @Test
  public void testExportSymbolWithMultipleTemplateAnnotation() {

    compileAndCheck(
        """
        var internalName;

        /**
         * @param {K} param1
         * @return {V}
         * @template K,V
         */
        internalName = function(param1) {
          return /** @type {?} */ (param1);
        };
        goog.exportSymbol('externalName', internalName);
        """,
        """
        /**
         * @param {K} param1
         * @return {V}
         * @template K,V
         */
        var externalName = function(param1) {
        };
        """);
  }

  @Test
  public void testExportSymbolWithoutTypeCheck() {
    // ExternExportsPass should not emit annotations
    // if there is no type information available.
    disableTypeCheck();

    compileAndCheck(
        """
        var internalName;

        /**
         * @param {string} param1
         * @param {number} param2
         * @return {string}
         */
        internalName = function(param1, param2) {
        return param1 + param2;
        };
        goog.exportSymbol('externalName', internalName)
        """,
        """
        var externalName = function(param1, param2) {
        };
        """);
  }

  @Test
  public void testExportSymbolWithConstructor() {
    compileAndCheck(
        """
        var internalName;

        /**
         * @constructor
         */
        internalName = function() {
        };
        goog.exportSymbol('externalName', internalName)
        """,
        """
        /**
         * @constructor
         */
        var externalName = function() {
        };
        """);
  }

  @Test
  public void exportClass() {
    compileAndCheck(
        """
        var internalName;

        internalName = class {
        };
        goog.exportSymbol('externalName', internalName)
        """,
        """
        /**
         * @constructor
         */
        var externalName = function() {
        };
        """);
  }

  @Test
  public void testNonNullTypes() {
    compileAndCheck(
        """
        /**
         * @constructor
         */
        function Foo() {}
        goog.exportSymbol('Foo', Foo);
        /**
         * @param {!Foo} x
         * @return {!Foo}
         */
        Foo.f = function(x) { return x; };
        goog.exportProperty(Foo, 'f', Foo.f);
        """,
        """
        /**
         * @constructor
         */
        var Foo = function() {
        };
        /**
         * @param {!Foo} x
         * @return {!Foo}
         */
        Foo.f = function(x) {
        };
        """);
  }

  @Test
  public void testExportSymbolWithConstructorWithoutTypeCheck() {
    // For now, skipping type checking should prevent generating
    // annotations of any kind, so, e.g., @constructor is not preserved.
    // This is probably not ideal, but since JSDocInfo for functions is attached
    // to JSTypes and not Nodes (and no JSTypes are created when checkTypes
    // is false), we don't really have a choice.

    disableTypeCheck();

    compileAndCheck(
        """
        var internalName;
        /**
         * @constructor
         */
        internalName = function() {
        };
        goog.exportSymbol('externalName', internalName)
        """,
        """
        var externalName = function() {
        };
        """);
  }

  @Test
  @Ignore("(b/141729691): this test fails, but unlikely to fix as this feature is deprecated")
  public void exportClassWithoutTypeCheck() {
    // For now, skipping type checking should prevent generating
    // annotations of any kind, so, e.g., @constructor is not preserved.
    // This is probably not ideal, but since JSDocInfo for functions is attached
    // to JSTypes and not Nodes (and no JSTypes are created when checkTypes
    // is false), we don't really have a choice.

    disableTypeCheck();

    compileAndCheck(
        """
        var internalName;
        internalName = class {
        };
        goog.exportSymbol('externalName', internalName)
        """,
        """
        var externalName = function() {
        };
        """);
  }

  // x.Y is present in the generated externs but lacks the @constructor annotation.
  @Test
  public void testExportPrototypePropsWithoutConstructor() {
    compileAndCheck(
        """
        /** @constructor */
        x.Y = function() {};
        x.Y.prototype.z = function() {};
        goog.exportProperty(x.Y.prototype, 'z', x.Y.prototype.z);
        """,
        """
        var x;
        x.Y;
        /**
         * @return {undefined}
         */
        x.Y.prototype.z = function() {
        };
        """);
  }

  // x.Y is present in the generated externs but lacks the @constructor annotation.
  @Test
  public void exportMethodButNotTheClass() {
    compileAndCheck(
        """
        x.Y = class {
          z() {};
        };
        goog.exportProperty(x.Y.prototype, 'z', x.Y.prototype.z);
        """,
        """
        var x;
        x.Y;
        /**
         * @return {undefined}
         */
        x.Y.prototype.z = function() {
        };
        """);
  }

  @Test
  public void testExportFunctionWithOptionalArguments1() {
    compileAndCheck(
        """
        var internalName;

        /**
         * @param {number=} a
         */
        internalName = function(a) {
        };
        goog.exportSymbol('externalName', internalName)
        """,
        """
        /**
         * @param {number=} a
         * @return {undefined}
         */
        var externalName = function(a) {
        };
        """);
  }

  @Test
  public void testExportFunctionWithOptionalArguments2() {
    compileAndCheck(
        """
        var internalName;

        /**
         * @param {number=} a
         */
        internalName = function(a) {
          return /** @type {?} */ (6);
        };
        goog.exportSymbol('externalName', internalName)
        """,
        """
        /**
         * @param {number=} a
         * @return {?}
         */
        var externalName = function(a) {
        };
        """);
  }

  @Test
  public void testExportFunctionWithOptionalArguments3() {
    compileAndCheck(
        """
        var internalName;

        /**
         * @param {number=} a
         */
        internalName = function(a) {
          return /** @type {?} */ (a);
        };
        goog.exportSymbol('externalName', internalName)
        """,
        """
        /**
         * @param {number=} a
         * @return {?}
         */
        var externalName = function(a) {
        };
        """);
  }

  @Test
  public void testExportFunctionWithVariableArguments() {
    compileAndCheck(
        """
        var internalName;

        /**
         * @param {...number} a
         * @return {number}
         */
        internalName = function(a) {
          return 6;
        };
        goog.exportSymbol('externalName', internalName)
        """,
        """
        /**
         * @param {...number} a
         * @return {number}
         */
        var externalName = function(a) {
        };
        """);
  }

  @Test
  public void exportArrowFunction() {
    compileAndCheck(
        """
        var internalName;

        /**
         * @param {number} a
         * @return {number}
         */
        internalName = (a) => a;
        goog.exportSymbol('externalName', internalName)
        """,
        """
        /**
         * @param {number} a
         * @return {number}
         */
        var externalName = function(a) {
        };
        """);
  }

  @Test
  public void exportGeneratorFunction() {
    compileAndCheck(
        """
        var internalName;

        /**
         * @param {number} a
         * @return {!Iterator<number>}
         */
        internalName = function *(a) { yield a; };
        goog.exportSymbol('externalName', internalName)
        """,
        """
        /**
         * @param {number} a
         * @return {!Iterator<number,?,?>}
         */
        var externalName = function(a) {
        };
        """);
  }

  @Test
  public void exportAsyncGeneratorFunction() {
    compileAndCheck(
        """
        var internalName;

        /**
         * @param {number} a
         * @return {!AsyncGenerator<number>}
         */
        internalName = async function *(a) { yield a; };
        goog.exportSymbol('externalName', internalName)
        """,
        """
        /**
         * @param {number} a
         * @return {!AsyncGenerator<number,?,?>}
         */
        var externalName = function(a) {
        };
        """);
  }

  @Test
  public void exportAsyncFunction() {
    compileAndCheck(
        """
        var internalName;

        /**
         * @param {number} a
         * @return {!Promise<number>}
         */
        internalName = async function(a) { return a; };
        goog.exportSymbol('externalName', internalName)
        """,
        """
        /**
         * @param {number} a
         * @return {!Promise<number>}
         */
        var externalName = function(a) {
        };
        """);
  }

  @Test
  public void exportFunctionWithDefaultParameter() {
    compileAndCheck(
        """
        var internalName;

        /**
         * @param {number=} a
         */
        internalName = function(a = 1) {
        };
        goog.exportSymbol('externalName', internalName)
        """,
        """
        /**
         * @param {number=} a
         * @return {undefined}
         */
        var externalName = function(a) {
        };
        """);
  }

  @Test
  public void exportFunctionWithRestParameter() {
    compileAndCheck(
        """
        var internalName;

        /**
         * @param {...number} numbers
         */
        internalName = function(...numbers) {
        };
        goog.exportSymbol('externalName', internalName)
        """,
        """
        /**
         * @param {...number} numbers
         * @return {undefined}
         */
        var externalName = function(numbers) {
        };
        """);
  }

  @Test
  public void exportFunctionWithPatternParameters() {
    compileAndCheck(
        """
        var internalName;

        /**
         * @param {{p: number, q: string}} options
         * @param {number} a
         * @param {!Array<number>} moreOptions
         */
        internalName = function({p, q}, a, [x, y]) {
        };
        goog.exportSymbol('externalName', internalName)
        """,
        """
        /**
         * @param {{p: number, q: string}} b
         * @param {number} a
         * @param {!Array<number>} c
         * @return {undefined}
         */
        var externalName = function(b, a, c) {
        };
        """);
  }

  @Test
  public void exportFunctionWithInlineDeclaredPatternParameters() {
    compileAndCheck(
        """
        var internalName;

        internalName = function(
            /** !Array<string> */ [p, q],
            /** number */ a, // generated names start with 'a', make sure we don't conflict
            /** !{x: number, y: string} */ {x, y}) {
        };
        goog.exportSymbol('externalName', internalName)
        """,
        // Param names are "b, a, c" because name 'a' was taken by a named parameter
        """
        /**
         * @param {!Array<string>} b
         * @param {number} a
         * @param {{x: number, y: string}} c
         * @return {undefined}
         */
        var externalName = function(b, a, c) {
        };
        """);
  }

  /** Enums are not currently handled. */
  @Test
  public void testExportEnum() {
    // We don't care what the values of the object properties are.
    // They're ignored by the type checker, and even if they weren't, it'd
    // be incomputable to get them correct in all cases
    // (think complex objects).
    compileAndCheck(
        """
        /**
         * @enum {string}
         * @export
         */
        var E = {A:'a', B:'b'};
        goog.exportSymbol('E', E);
        """,
        """
        /** @enum {string} */
        var E = {A:1, B:2};
        """);
  }

  @Test
  public void testExportWithReferenceToEnum() {
    String js =
        """
        /**
         * @enum {number}
         * @export
         */
        var E = {A:1, B:2};
        goog.exportSymbol('E', E);

        /**
         * @param {!E} e
         * @export
         */
        function f(e) {}
        goog.exportSymbol('f', f);
        """;
    String expected =
        """
        /** @enum {number} */
        var E = {A:1, B:2};
        /**
         * @param {E} e
         * @return {undefined}
         */
        var f = function(e) {
        };
        """;

    // NOTE: The type should print {E} for the @param, but is not.
    compileAndCheck(js, expected.replace("{E}", "{number}"));
  }

  /**
   * If we export a property with "prototype" as a path component, there is no need to emit the
   * initializer for prototype because every namespace has one automatically.
   */
  @Test
  public void exportDontEmitPrototypePathPrefixForClassMethod() {
    compileAndCheck(
        """
        var Foo = class {
          /**
           * @return {number}
           */
          m() {return 6;};
        };
        goog.exportSymbol('Foo', Foo);
        goog.exportProperty(Foo.prototype, 'm', Foo.prototype.m);
        """,
        """
        /**
         * @constructor
         */
        var Foo = function() {
        };
        /**
         * @return {number}
         */
        Foo.prototype.m = function() {
        };
        """);
  }

  /**
   * Test the workflow of creating an externs file for a library via the export pass and then using
   * that externs file in a client.
   *
   * <p>There should be no warnings in the client if the library includes type information for the
   * exported functions and the client uses them correctly.
   */
  @Test
  public void useExportsAsExternsWithClass() {
    String librarySource =
        """
        var InternalName = class {
          /**
           * @param {number} n
           */
          constructor(n) {
          }
        };
        goog.exportSymbol('ExternalName', InternalName)
        """;

    String clientSource =
        """
        var foo = new ExternalName(6);
        /**
         * @param {ExternalName} x
         */
        var bar = function(x) {};
        """;

    compileAndExportExterns(
        librarySource,
        MINIMAL_EXTERNS,
        generatedExterns ->
            compileAndExportExterns(clientSource, MINIMAL_EXTERNS + generatedExterns));
  }

  @Test
  public void testDontWarnOnExportFunctionWithUnknownReturnType() {
    String librarySource =
        """
        var InternalName = function() {
          return 6;
        };
        goog.exportSymbol('ExternalName', InternalName)
        """;

    compileAndExportExterns(librarySource);
  }

  @Test
  public void testDontWarnOnExportConstructorWithUnknownReturnType() {
    String librarySource =
        """
        /**
         * @constructor
         */
        var InternalName = function() {
        };
        goog.exportSymbol('ExternalName', InternalName)
        """;

    compileAndExportExterns(librarySource);
  }

  @Test
  public void testTypedef() {
    compileAndCheck(
        """
        /** @typedef {{x: number, y: number}} */ var Coord;
        /**
         * @param {Coord} a
         * @export
         */
        var fn = function(a) {};
        goog.exportSymbol('fn', fn);
        """,
        """
        /**
         * @param {{x: number, y: number}} a
         * @return {undefined}
         */
        var fn = function(a) {
        };
        """);
  }

  @Test
  public void testExportParamWithNull() {
    compileAndCheck(
        """
        /** @param {string|null=} d */
        var f = function(d) {};
        goog.exportSymbol('foobar', f)
        """,
        """
        /**
         * @param {(null|string)=} d
         * @return {undefined}
         */
        var foobar = function(d) {
        };
        """);
  }

  @Test
  public void testExportConstructor() {
    compileAndCheck(
        "/** @constructor */ var a = function() {}; goog.exportSymbol('foobar', a)",
        """
        /**
         * @constructor
         */
        var foobar = function() {
        };
        """);
  }

  @Test
  public void exportEs5ClassHierarchy() {
    compileAndCheck(
        """
        /** @constructor */
        function SuperClass() {}
        goog.exportSymbol('Foo', SuperClass);

        /**
         * @constructor
         * @extends {SuperClass}
         */
        function SubClass() {}
        goog.exportSymbol('Bar', SubClass);
        """,
        // TODO(b/123352214): SuperClass should be called Foo in exported @extends annotation
        """
        /**
         * @extends {SuperClass}
         * @constructor
         */
        var Bar = function() {
        };
        /**
         * @constructor
         */
        var Foo = function() {
        };
        """);
  }

  @Test
  public void testExportLocalPropertyInConstructor() {
    compileAndCheck(
        "/** @constructor */function F() { /** @export */ this.x = 5;} goog.exportSymbol('F', F);",
        """
        /**
         * @constructor
         */
        var F = function() {
        };
        F.prototype.x;
        """);
  }

  @Test
  public void testExportLocalPropertyInConstructor2() {
    compileAndCheck(
        """
        /** @constructor */function F() { /** @export */ this.x = 5;}
        goog.exportSymbol('F', F);
        goog.exportProperty(F.prototype, 'x', F.prototype.x);
        """,
        """
        /**
         * @constructor
         */
        var F = function() {
        };
        F.prototype.x;
        """);
  }

  @Test
  public void testExportLocalPropertyInConstructor3() {
    compileAndCheck(
        "/** @constructor */function F() { /** @export */ this.x;} goog.exportSymbol('F', F);",
        """
        /**
         * @constructor
         */
        var F = function() {
        };
        F.prototype.x;
        """);
  }

  @Test
  public void testExportLocalPropertyInConstructor4() {
    compileAndCheck(
        """
        /** @constructor */
        function F() { /** @export */ this.x = function(/** string */ x){};}
        goog.exportSymbol('F', F);
        """,
        """
        /**
         * @constructor
         */
        var F = function() {
        };
        F.prototype.x;
        """);
  }

  @Test
  public void testExportLocalPropertyNotInConstructor() {
    compileAndCheck(
        "/** @this {?} */ function f() { /** @export */ this.x = 5;} goog.exportSymbol('f', f);",
        """
        /**
         * @return {undefined}
         */
        var f = function() {
        };
        """);
  }

  @Test
  public void testExportParamWithSymbolDefinedInFunction() {
    compileAndCheck(
        """
        var id = function() {return /** @type {?} */ ('id')};
        var ft = function() {
          var id;
          return 1;
        };
        goog.exportSymbol('id', id);
        """,
        """
        /**
         * @return {?}
         */
        var id = function() {
        };
        """);
  }

  @Test
  public void testExportSymbolWithFunctionDefinedAsFunction() {

    compileAndCheck(
        """
        /**
         * @param {string} param1
         * @return {string}
         */
        function internalName(param1) {
          return param1
        };
        goog.exportSymbol('externalName', internalName)
        """,
        """
        /**
         * @param {string} param1
         * @return {string}
         */
        var externalName = function(param1) {
        };
        """);
  }

  @Test
  public void testExportSymbolWithFunctionAlias() {

    compileAndCheck(
        """
        /**
         * @param {string} param1
         */
        var y = function(param1) {
        };
        /**
         * @param {string} param1
         * @param {string} param2
         */
        var x = function y(param1, param2) {
        };
        goog.exportSymbol('externalName', y)
        """,
        """
        /**
         * @param {string} param1
         * @return {undefined}
         */
        var externalName = function(param1) {
        };
        """);
  }

  @Test
  public void testNamespaceDefinitionInExterns() {
    compileAndCheck(
        """
        /** @const */
        var ns = {};
        /** @const */
        ns.subns = {};
        /** @constructor */
        ns.subns.Foo = function() {};
        goog.exportSymbol('ns.subns.Foo', ns.subns.Foo);
        """,
        """
        /**
         * @const
         * @suppress {const,duplicate}
         */
        var ns = {};
        /**
         * @const
         * @suppress {const,duplicate}
         */
        ns.subns = {};
        /**
         * @constructor
         */
        ns.subns.Foo = function() {
        };
        """);
  }

  @Test
  public void testNullabilityInFunctionTypes() {
    compileAndCheck(
        """
        /**
         * @param {function(Object)} takesNullable
         * @param {function(!Object)} takesNonNullable
         */
        function x(takesNullable, takesNonNullable) {}
        goog.exportSymbol('x', x);
        """,
        """
        /**
         * @param {function((Object|null)): ?} takesNullable
         * @param {function(!Object): ?} takesNonNullable
         * @return {undefined}
         */
        var x = function(takesNullable, takesNonNullable) {
        };
        """);
  }

  @Test
  public void testNullabilityInRecordTypes() {
    compileAndCheck(
        """
        /** @typedef {{ nonNullable: !Object, nullable: Object }} */
        var foo;
        /** @param {foo} record */
        function x(record) {}
        goog.exportSymbol('x', x);
        """,
        """
        /**
         * @param {{nonNullable: !Object, nullable: (Object|null)}} record
         * @return {undefined}
         */
        var x = function(record) {
        };
        """);
  }

  private void compileAndCheck(String js, final String expected) {
    compileAndCheck(MINIMAL_EXTERNS, js, expected);
  }

  private void compileAndCheck(String externs, String js, final String expected) {
    compileAndExportExterns(
        js,
        externs,
        generatedExterns -> {
          String fileoverview =
              """
              /**
               * @fileoverview Generated externs.
               * @externs
               */
              """;
          // NOTE(sdh): The type checker just produces {?}.
          // For now we will not worry about this distinction and just normalize it.
          generatedExterns = generatedExterns.replace("?=", "?");

          assertThat(generatedExterns).isEqualTo(fileoverview + expected);
        });
  }

  @Test
  public void testDontWarnOnExportFunctionWithUnknownParameterTypes() {
    /* This source is missing types for the b and c parameters */
    String librarySource =
        """
        /**
         * @param {number} a
         * @return {number}
         */
        var InternalName = function(a,b,c) {
          return 6;
        };
        goog.exportSymbol('ExternalName', InternalName)
        """;

    compileAndExportExterns(librarySource);
  }

  /**
   * Compiles the passed in JavaScript and returns the new externs exported by the this pass.
   *
   * @param js the source to be compiled
   */
  private void compileAndExportExterns(String js) {
    compileAndExportExterns(js, MINIMAL_EXTERNS);
  }

  /**
   * Compiles the passed in JavaScript with the passed in externs and returns the new externs
   * exported by the this pass.
   *
   * @param js the source to be compiled
   * @param externs the externs the {@code js} source needs
   */
  private void compileAndExportExterns(String js, String externs) {
    compileAndExportExterns(js, externs, null);
  }

  /**
   * Compiles the passed in JavaScript with the passed in externs and returns the new externs
   * exported by the this pass.
   *
   * @param js the source to be compiled
   * @param externs the externs the {@code js} source needs
   * @param consumer consumer for the externs generated from {@code js}
   */
  private void compileAndExportExterns(
      String js, String externs, final @Nullable Consumer<String> consumer) {
    js =
        """
        /** @const */ var goog = {};
        goog.exportSymbol = function(a, b) {};
        goog.exportProperty = function(a, b, c) {};
        """
            + js;

    test(
        externs(externs),
        srcs(js),
        (Postcondition)
            compiler -> {
              if (consumer != null) {
                consumer.accept(compiler.getResult().externExport);
              }
            });
  }
}
