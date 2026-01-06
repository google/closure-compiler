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

package com.google.javascript.jscomp.integration;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.DevMode;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.GoogleCodingConvention;
import com.google.javascript.jscomp.PropertyRenamingPolicy;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.VariableRenamingPolicy;
import com.google.javascript.jscomp.js.RuntimeJsLibManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Integration tests specifically for handling of getter / setter properties.
 *
 * <p>These factors must be covered here.
 *
 * <ol>
 *   <li>How is the getter/setter defined? (get/set syntax, {@code Object.defineProperty}, {@code
 *       Object.defineProperties})
 *   <li>What is it defined on? (object literal, class static, or class prototype)
 *   <li>Is the property completely unreferenced, only read, only written, or both?
 * </ol>
 *
 * <p>Every test case includes a property that is both read and written to test the most common case
 * behavior. These are not broken out into separate test cases, because we need the class or object
 * to contain properties that cannot be removed. Otherwise, the compiler could remove the entire
 * object or class as unused, obscuring the removal of individual properties and their getters and
 * setters that we're trying to test here.
 *
 * <p>It is an intentional omission that there are no test cases for using {@code
 * Object.defineProperty()} to define properties on a class prototype. {@code
 * Object.defineProperty()} is only very minimally recognized as defining getters and setters and
 * this isn't likely to change, so additional test cases for it does not seem worthwhile. On the
 * other hand, the compiler generates {@code Object.defineProperties()} calls when transpiling
 * classes to ES5 syntax and has long supported removal of unused properties that are defined with
 * it.
 */
@RunWith(JUnit4.class)
public final class GetterAndSetterIntegrationTest extends IntegrationTestCase {

  /** Creates a CompilerOptions object with google coding conventions. */
  public CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDevMode(DevMode.EVERY_PASS);
    options.setCodingConvention(new GoogleCodingConvention());
    options.setRenamePrefixNamespaceAssumeCrossChunkNames(true);
    options.setCheckTypes(true);
    // Make it easier to read the mismatch reports
    options.setPrettyPrint(true);
    // Spitting out 'use strict'; is just noise here
    options.setEmitUseStrict(false);
    // renaming doesn't matter for these tests, and just makes them harder to read
    options.setRenamingPolicy(VariableRenamingPolicy.OFF, PropertyRenamingPolicy.OFF);
    options.setAssumeGettersArePure(false);
    return options;
  }

  @Test
  public void unreferencedGetterSetterPropIsRemoved_forObjectLiteral() {
    CompilerOptions options = createCompilerOptions();

    testSame(
        options,
        """
        const obj = {
        // TODO(b/115853720); Should remove unusedProp setter and getter, because
        // there are no references to it
          get unusedProp() { return this.unusedProp_; },
          set unusedProp(value) { this.unusedProp_ = value; },
        // used property included for comparison, to show that it isn't removed
          get usedProp() { return this.usedProp_; },
          set usedProp(value) { this.usedProp_ = value; },
        };
        obj.usedProp = 2;
        alert(obj.usedProp);
        """);
  }

  @Test
  public void unreferencedGetterSetterPropIsRemoved_forObjectLiteralPrototype() {
    CompilerOptions options = createCompilerOptions();

    test(
        options,
        """
        /** @constructor */
        function ES5Class() {
          /** @private {number} */
          this.unusedProp_ = 1;
          /** @private {number} */
          this.usedProp_ = 2;
        }
        ES5Class.prototype = {
          /** @type {number} */
          get unusedProp() { return this.unusedProp_; },
          set unusedProp(value) { this.unusedProp_ = value; },
        // used property included for comparison, to show that it isn't removed
          /** @type {number} */
          get usedProp() { return this.usedProp_; },
          set usedProp(value) { this.usedProp_ = value; },
        };
        const obj = new ES5Class();
        obj.usedProp = 2;
        alert(obj.usedProp);
        """,
        """
        /** @constructor */
        function ES5Class() {
        // unusedProp_ is gone
          /** @private {number} */
          this.usedProp_ = 2;
        }
        ES5Class.prototype = {
        // unusedProp getter and setter are gone
          /** @type {number} */
          get usedProp() { return this.usedProp_; },
          set usedProp(value) { this.usedProp_ = value; },
        };
        const obj = new ES5Class();
        obj.usedProp = 2;
        alert(obj.usedProp);
        """);
  }

  @Test
  public void unreferencedGetterSetterPropIsRemoved_forObjectDotDefineProperty() {
    CompilerOptions options = createCompilerOptions();

    test(
        options,
        """
        /** @constructor */
        function ES5Class() {
        }
        // static properties used by static getter/setter properties
        /** @private {number} */
        ES5Class.unusedProp_ = 1;
        /** @private {number} */
        ES5Class.usedProp_ = 2;
        Object.defineProperty(
            ES5Class,
            'unusedProp',
            {
              configurable: false,
              writable: true,
              /**
               * @this {typeof ES5Class}
               * @return {number}
               */
              get: function() { return this.unusedProp_; },
              /**
               * @this {typeof ES5Class}
               * @param {number} value
               */
              set: function(value) { this.unusedProp_ = value; },
            });
        Object.defineProperty(
            ES5Class,
        // used property included for comparison, to show that it isn't removed
            'usedProp',
            {
              configurable: false,
              writable: true,
              /**
               * @this {typeof ES5Class}
               * @return {number}
               */
              get: function() { return this.usedProp_; },
              /**
               * @this {typeof ES5Class}
               * @param {number} value
               */
              set: function(value) { this.usedProp_ = value; },
            });
        ES5Class.usedProp = 2;
        alert(ES5Class.usedProp);
        """,
        """
        /** @constructor */
        function ES5Class() {
        }
        // TODO(b/130682799): Properties used in getters and setters should not be removed
        Object.defineProperty(
            ES5Class,
        // TODO(b/115853720): unused property definition should be removed.
            'unusedProp',
            {
              configurable: !1,
              writable: !0,
              /**
               * @this {typeof ES5Class}
               * @return {number}
               */
              get: function() { return this.unusedProp_; },
              /**
               * @this {typeof ES5Class}
               * @param {number} value
               */
              set: function(value) { this.unusedProp_ = value; },
            });
        // used property included for comparison, to show that it isn't removed
        Object.defineProperty(
            ES5Class,
            'usedProp',
            {
              configurable: !1, // unrelated size improvement
              writable: !0,
              /**
               * @this {typeof ES5Class}
               * @return {number}
               */
              get: function() { return this.usedProp_; },
              /**
               * @this {typeof ES5Class}
               * @param {number} value
               */
              set: function(value) { this.usedProp_ = value; },
            });
        // TODO(b/72879754): Property with getter should not be inlined
        alert(2);
        """);
  }

  @Test
  public void unreferencedGetterSetterPropIsRemoved_forObjectDotDefineProperties() {
    CompilerOptions options = createCompilerOptions();

    test(
        options,
        """
        /** @constructor */
        function ES5Class() {
        }
        // static properties used by static getter/setter properties
        /** @private {number} */
        ES5Class.unusedProp_ = 1;
        /** @private {number} */
        ES5Class.usedProp_ = 2;
        Object.defineProperties(
            ES5Class,
            {
              unusedProp: {
        // use pre-optimized boolean values to avoid unrelated changes
                configurable: !1,
                writable: !0,
                /**
                 * @this {typeof ES5Class}
                 * @return {number}
                 */
                get: function() { return this.unusedProp_; },
                /**
                 * @this {typeof ES5Class}
                 * @param {number} value
                 */
                set: function(value) { this.unusedProp_ = value; },
              },
        // used property included for comparison, to show that it isn't removed
              usedProp: {
        // use pre-optimized boolean values to avoid unrelated changes
                configurable: !1,
                writable: !0,
                /**
                 * @this {typeof ES5Class}
                 * @return {number}
                 */
                get: function() { return this.usedProp_; },
                /**
                 * @this {typeof ES5Class}
                 * @param {number} value
                 */
                set: function(value) { this.usedProp_ = value; },
              }
            });
        ES5Class.usedProp = 2;
        alert(ES5Class.usedProp);
        """,
        """
        /** @constructor */
        function ES5Class() {
        }
        // static properties used by static getter/setter properties
        // TODO(b/130682799): Properties used in getters and setters should not be removed
        // "/** @private {number} */",
        // "ES5Class.usedProp_ = 2;",
        Object.defineProperties(
            ES5Class,
            {
        // used property included for comparison, to show that it isn't removed
              usedProp: {
        // use pre-optimized boolean values to avoid unrelated changes
                configurable: !1,
                writable: !0,
                /**
                 * @this {typeof ES5Class}
                 * @return {number}
                 */
                get: function() { return this.usedProp_; },
                /**
                 * @this {typeof ES5Class}
                 * @param {number} value
                 */
                set: function(value) { this.usedProp_ = value; },
              }
            });
        ES5Class.usedProp = 2;
        alert(ES5Class.usedProp);
        """);
  }

  @Test
  public void unreferencedGetterSetterPropIsRemoved_forPrototypeObjectDotDefineProperties() {
    CompilerOptions options = createCompilerOptions();

    test(
        options,
        """
        /** @constructor */
        function ES5Class() {
          /** @private {number} */ this.unusedProp_ = 1;
          /** @private {number} */ this.usedProp_ = 2;
        }
        // static properties used by static getter/setter properties
        Object.defineProperties(
            ES5Class.prototype,
            {
              unusedProp: {
                configurable: false,
                writable: true,
                /**
                 * @this {ES5Class}
                 * @return {number}
                 */
                get: function() { return this.unusedProp_; },
                /**
                 * @this {ES5Class}
                 * @param {number} value
                 */
                set: function(value) { this.unusedProp_ = value; },
              },
        // used property included for comparison, to show that it isn't removed
              usedProp: {
                configurable: false,
                writable: true,
                /**
                 * @this {ES5Class}
                 * @return {number}
                 */
                get: function() { return this.usedProp_; },
                /**
                 * @this {ES5Class}
                 * @param {number} value
                 */
                set: function(value) { this.usedProp_ = value; },
              }
            });
        const obj = new ES5Class();
        obj.usedProp = 2;
        alert(obj.usedProp);
        """,
        """
        /** @constructor */
        function ES5Class() {
          /** @private {number} */ this.usedProp_ = 2;
        }
        // static properties used by static getter/setter properties
        Object.defineProperties(
            ES5Class.prototype,
            {
              usedProp: {
                configurable: !1, // unrelated optimization
                writable: !0,
                /**
                 * @this {ES5Class}
                 * @return {number}
                 */
                get: function() { return this.usedProp_; },
                /**
                 * @this {ES5Class}
                 * @param {number} value
                 */
                set: function(value) { this.usedProp_ = value; },
              }
            });
        const obj = new ES5Class();
        obj.usedProp = 2;
        alert(obj.usedProp);
        """);
  }

  @Test
  public void unreferencedGetterSetterPropIsRemoved_forStaticClassProperties() {
    CompilerOptions options = createCompilerOptions();

    test(
        options,
        """
        class C {
          /** @type {number} */
          static get unusedProp() { return this.unusedProp_; }
          static set unusedProp(value) { this.unusedProp_ = value; }
        // used property included for comparison, to show that it isn't removed
          /** @type {number} */
          static get usedProp() { return this.usedProp_; }
          static set usedProp(value) { this.usedProp_ = value; }
        }
        // static properties used by static getter/setter properties
        /** @private {number} */
        C.unusedProp_ = 1;
        /** @private {number} */
        C.usedProp_ = 2;
        C.usedProp = 2;
        alert(C.usedProp);
        """,
        """
        class C {
          /** @type {number} */
          static get usedProp() { return this.usedProp_; }
          static set usedProp(value) { this.usedProp_ = value; }
        }
        // TODO(b/130682799): Properties used inside getters and setters should not be removed
        C.usedProp = 2;
        // Property with getter should not be inlined
        alert(C.usedProp);
        """);
  }

  @Test
  public void unreferencedGetterSetterPropIsRemoved_forClassProperties() {
    CompilerOptions options = createCompilerOptions();

    test(
        options,
        """
        class C {
          constructor() {
            /** @private {number} */ this.unusedProp_ = 1;
            /** @private {number} */ this.usedProp_ = 2;
          }
          /** @type {number} */
          get unusedProp() { return this.unusedProp_; }
          set unusedProp(value) { this.unusedProp_ = value; }
        // used property included for comparison, to show that it isn't removed
          /** @type {number} */
          get usedProp() { return this.usedProp_; }
          set usedProp(value) { this.usedProp_ = value; }

        }
        const obj = new C;
        obj.usedProp = 2;
        alert(obj.usedProp);
        """,
        """
        class C {
          constructor() {
        // unusedProp_ is correctly gone
            /** @private {number} */ this.usedProp_ = 2;
          }
        // used property included for comparison, to show that it isn't removed
          /** @type {number} */
          get usedProp() { return this.usedProp_; }
          set usedProp(value) { this.usedProp_ = value; }

        }
        const obj = new C;
        obj.usedProp = 2;
        alert(obj.usedProp); // property with getter cannot be inlined
        """);
  }

  @Test
  public void assignmentToSetterPropIsNotRemoved_forObjectLiteral() {
    CompilerOptions options = createCompilerOptions();

    testSame(
        options,
        """
        const obj = {
        // TODO(b/115853720); Should remove getter for onlySetProp, because
        // there is only an assignment to it
          get onlySetProp() { return this.onlySetProp_; },
          set onlySetProp(value) { this.onlySetProp_ = value; },
        // used property included for comparison, to show that it isn't removed
          get usedProp() { return this.usedProp_; },
          set usedProp(value) { this.usedProp_ = value; },
        };
        obj.onlySetProp = 1;
        obj.usedProp = 2;
        alert(obj.usedProp);
        """);
  }

  @Test
  public void assignmentToSetterPropIsNotRemoved_forObjectLiteralPrototype() {
    CompilerOptions options = createCompilerOptions();

    test(
        options,
        """
        /** @constructor */
        function ES5Class() {
          /** @private {number} */
          this.onlySetProp_ = 1;
          /** @private {number} */
          this.usedProp_ = 2;
        }
        ES5Class.prototype = {
        // TODO(b/115853720); Should remove getter for onlySetProp, because
        // there is only an assignment to it
          /** @type {number} */
          get onlySetProp() { return this.onlySetProp_; },
          set onlySetProp(value) { this.onlySetProp_ = value; },
        // used property included for comparison, to show that it isn't removed
          /** @type {number} */
          get usedProp() { return this.usedProp_; },
          set usedProp(value) { this.usedProp_ = value; },
        };
        const obj = new ES5Class();
        obj.onlySetProp = 1;
        obj.usedProp = 2;
        alert(obj.usedProp);
        """,
        """
        /** @constructor */
        function ES5Class() {
          /** @private {number} */
          this.onlySetProp_ = 1;
          /** @private {number} */
          this.usedProp_ = 2;
        }
        ES5Class.prototype = {
        // TODO(b/115853720); Should remove getter for onlySetProp, because
        // there is only an assignment to it
          /** @type {number} */
          get onlySetProp() { return this.onlySetProp_; },
          set onlySetProp(value) { this.onlySetProp_ = value; },
        // used property included for comparison, to show that it isn't removed
          /** @type {number} */
          get usedProp() { return this.usedProp_; },
          set usedProp(value) { this.usedProp_ = value; },
        };
        const obj = new ES5Class();
        obj.onlySetProp = 1;
        obj.usedProp = 2;
        alert(obj.usedProp);
        """);
  }

  @Test
  public void assignmentToSetterPropIsNotRemoved_forObjectDotDefineProperty() {
    CompilerOptions options = createCompilerOptions();

    test(
        options,
        """
        /** @constructor */
        function ES5Class() {
        }
        // static properties used by static getter/setter properties
        /** @private {number} */
        ES5Class.onlySetProp_ = 1;
        /** @private {number} */
        ES5Class.usedProp_ = 2;
        Object.defineProperty(
            ES5Class,
            'onlySetProp',
            {
              configurable: false,
              writable: true,
              /**
               * @this {typeof ES5Class}
               * @return {number}
               */
              get: function() { return this.onlySetProp_; },
              /**
               * @this {typeof ES5Class}
               * @param {number} value
               */
              set: function(value) { this.onlySetProp_ = value; },
            });
        Object.defineProperty(
            ES5Class,
        // used property included for comparison, to show that it isn't removed
            'usedProp',
            {
              configurable: false,
              writable: true,
              /**
               * @this {typeof ES5Class}
               * @return {number}
               */
              get: function() { return this.usedProp_; },
              /**
               * @this {typeof ES5Class}
               * @param {number} value
               */
              set: function(value) { this.usedProp_ = value; },
            });
        ES5Class.onlySetProp = 1;
        ES5Class.usedProp = 2;
        alert(ES5Class.usedProp);
        """,
        """
        /** @constructor */
        function ES5Class() {
        }
        // TODO(b/130682799): Properties used in getters and setters should not be removed
        Object.defineProperty(
            ES5Class,
            'onlySetProp',
            {
              configurable: !1, // unrelated optimization
              writable: !0,
              /**
               * @this {typeof ES5Class}
               * @return {number}
               */
              get: function() { return this.onlySetProp_; },
              /**
               * @this {typeof ES5Class}
               * @param {number} value
               */
              set: function(value) { this.onlySetProp_ = value; },
            });
        Object.defineProperty(
            ES5Class,
        // used property included for comparison, to show that it isn't removed
            'usedProp',
            {
              configurable: !1, // unrelated optimization
              writable: !0,
              /**
               * @this {typeof ES5Class}
               * @return {number}
               */
              get: function() { return this.usedProp_; },
              /**
               * @this {typeof ES5Class}
               * @param {number} value
               */
              set: function(value) { this.usedProp_ = value; },
            });
        // TODO(b/72879754): property with getter should not be inlined
        alert(2); // prop with getter not inlined
        """);
  }

  @Test
  public void assignmentToSetterPropIsNotRemoved_forObjectDotDefineProperties() {
    CompilerOptions options = createCompilerOptions();

    test(
        options,
        """
        /** @constructor */
        function ES5Class() {
        }
        // static properties used by static getter/setter properties
        /** @private {number} */
        ES5Class.onlySetProp_ = 1;
        /** @private {number} */
        ES5Class.usedProp_ = 2;
        Object.defineProperties(
            ES5Class,
            {
              onlySetProp: {
        // use pre-optimzed values for true and false, so the result
        // won't have changes irrelevant to this test
                configurable: !1,
                writable: !0,
                /**
                 * @this {typeof ES5Class}
                 * @return {number}
                 */
                get: function() { return this.onlySetProp_; },
                /**
                 * @this {typeof ES5Class}
                 * @param {number} value
                 */
                set: function(value) { this.onlySetProp_ = value; },
              },
        // used property included for comparison, to show that it isn't removed
              usedProp: {
        // use pre-optimzed values for true and false, so the result
        // won't have changes irrelevant to this test
                configurable: !1,
                writable: !0,
                /**
                 * @this {typeof ES5Class}
                 * @return {number}
                 */
                get: function() { return this.usedProp_; },
                /**
                 * @this {typeof ES5Class}
                 * @param {number} value
                 */
                set: function(value) { this.usedProp_ = value; },
              }
            });
        ES5Class.onlySetProp = 1;
        ES5Class.usedProp = 2;
        alert(ES5Class.usedProp);
        """,
        """
        /** @constructor */
        function ES5Class() {
        }
        // TODO(b/130682799): Properties used inside getters and setters should not be removed
        // "/** @private {number} */",
        // "ES5Class.onlySetProp_ = 1;",
        // "/** @private {number} */",
        // "ES5Class.usedProp_ = 2;",
        Object.defineProperties(
            ES5Class,
            {
              onlySetProp: {
        // use pre-optimzed values for true and false, so the result
        // won't have changes irrelevant to this test
                configurable: !1,
                writable: !0,
                /**
                 * @this {typeof ES5Class}
                 * @return {number}
                 */
                get: function() { return this.onlySetProp_; },
                /**
                 * @this {typeof ES5Class}
                 * @param {number} value
                 */
                set: function(value) { this.onlySetProp_ = value; },
              },
        // used property included for comparison, to show that it isn't removed
              usedProp: {
        // use pre-optimzed values for true and false, so the result
        // won't have changes irrelevant to this test
                configurable: !1,
                writable: !0,
                /**
                 * @this {typeof ES5Class}
                 * @return {number}
                 */
                get: function() { return this.usedProp_; },
                /**
                 * @this {typeof ES5Class}
                 * @param {number} value
                 */
                set: function(value) { this.usedProp_ = value; },
              }
            });
        ES5Class.onlySetProp = 1;
        ES5Class.usedProp = 2;
        alert(ES5Class.usedProp);
        """);
  }

  @Test
  public void assignmentToSetterPropIsNotRemoved_forPrototypeObjectDotDefineProperties() {
    CompilerOptions options = createCompilerOptions();

    test(
        options,
        """
        /** @constructor */
        function ES5Class() {
          /** @private {number} */ this.onlySetProp_ = 1;
          /** @private {number} */ this.usedProp_ = 2;
        }
        Object.defineProperties(
            ES5Class.prototype,
            {
              onlySetProp: {
                configurable: false,
                writable: true,
                /**
                 * @this {ES5Class}
                 * @return {number}
                 */
                get: function() { return this.onlySetProp_; },
                /**
                 * @this {ES5Class}
                 * @param {number} value
                 */
                set: function(value) { this.onlySetProp_ = value; },
              },
        // used property included for comparison, to show that it isn't removed
              usedProp: {
                configurable: false,
                writable: true,
                /**
                 * @this {ES5Class}
                 * @return {number}
                 */
                get: function() { return this.usedProp_; },
                /**
                 * @this {ES5Class}
                 * @param {number} value
                 */
                set: function(value) { this.usedProp_ = value; },
              }
            });
        const onlySetObj = new ES5Class();
        onlySetObj.onlySetProp = 1;
        const usedObj = new ES5Class();
        usedObj.usedProp = 2;
        alert(usedObj.usedProp);
        """,
        """
        /** @constructor */
        function ES5Class() {
          /** @private {number} */ this.onlySetProp_ = 1;
          /** @private {number} */ this.usedProp_ = 2;
        }
        Object.defineProperties(
            ES5Class.prototype,
            {
              onlySetProp: {
                configurable: !1,
                writable: !0,
        // TODO(b/135640150): This getter is removable.
                /**
                 * @this {ES5Class}
                 * @return {number}
                 */
                get: function() { return this.onlySetProp_; },
                /**
                 * @this {ES5Class}
                 * @param {number} value
                 */
                set: function(value) { this.onlySetProp_ = value; },
              },
              usedProp: {
                configurable: !1,
                writable: !0,
                /**
                 * @this {ES5Class}
                 * @return {number}
                 */
                get: function() { return this.usedProp_; },
                /**
                 * @this {ES5Class}
                 * @param {number} value
                 */
                set: function(value) { this.usedProp_ = value; },
              }
            });
        new ES5Class().onlySetProp = 1;
        const usedObj = new ES5Class();
        usedObj.usedProp = 2;
        alert(usedObj.usedProp);
        """);
  }

  @Test
  public void assignmentToSetterPropIsNotRemoved_forStaticClassProperties() {
    CompilerOptions options = createCompilerOptions();

    test(
        options,
        """
        class C {
          /** @type {number} */
          static get onlySetProp() { return this.onlySetProp_; }
          static set onlySetProp(value) { this.onlySetProp_ = value; }
        // used property included for comparison, to show that it isn't removed
          /** @type {number} */
          static get usedProp() { return this.usedProp_; }
          static set usedProp(value) { this.usedProp_ = value; }
        }
        // static properties used by static getter/setter properties
        /** @private {number} */
        C.onlySetProp_ = 1;
        /** @private {number} */
        C.usedProp_ = 2;
         // use of getters/setters
        C.onlySetProp = 1;
        C.usedProp = 2;
        alert(C.usedProp);
        """,
        """
        class C {
        // TODO(b/115853720): unused getter should be removed.
          /** @type {number} */
          static get onlySetProp() { return this.onlySetProp_; }
          static set onlySetProp(value) { this.onlySetProp_ = value; }
          /** @type {number} */
          static get usedProp() { return this.usedProp_; }
          static set usedProp(value) { this.usedProp_ = value; }

        }
        // TODO(b/130682799): Properties used inside getters and setters should not be removed

        // assignment to property with setter must not be removed
        C.onlySetProp = 1;
        C.usedProp = 2;
        // Property with getter should not be inlined
        alert(C.usedProp);
        """);
  }

  @Test
  public void assignmentToSetterPropIsNotRemoved_forClassProperties() {
    CompilerOptions options = createCompilerOptions();

    test(
        options,
        """
        class C {
          constructor() {
            /** @private {number} */ this.onlySetProp_ = 1;
            /** @private {number} */ this.usedProp_ = 2;
          }
          /** @type {number} */
          get onlySetProp() { return this.onlySetProp_; }
          set onlySetProp(value) { this.onlySetProp_ = value; }
        // used property included for comparison, to show that it isn't removed
          /** @type {number} */
          get usedProp() { return this.usedProp_; }
          set usedProp(value) { this.usedProp_ = value; }

        }
        const onlySetObj = new C;
        onlySetObj.onlySetProp = 1; // use of setter should prevent removal
        const usedObj = new C;
        usedObj.usedProp = 2;
        alert(usedObj.usedProp);
        """,
        """
        class C {
          constructor() {
            /** @private {number} */ this.onlySetProp_ = 1;
            /** @private {number} */ this.usedProp_ = 2;
          }
        // TODO(b/135640150): This getter is removable.
          /** @type {number} */
          get onlySetProp() { return this.onlySetProp_; }
          set onlySetProp(value) { this.onlySetProp_ = value; }
          /** @type {number} */
          get usedProp() { return this.usedProp_; }
          set usedProp(value) { this.usedProp_ = value; }
        }
        (new C).onlySetProp = 1;
        const usedObj = new C;
        usedObj.usedProp = 2;
        alert(usedObj.usedProp); // property with getter cannot be inlined
        """);
  }

  @Test
  public void referenceToGetterPropIsNotRemoved_forObjectLiteral() {
    CompilerOptions options = createCompilerOptions();

    test(
        options,
        """
        const obj = {
          onlyGetProp_: 1,
          usedProp_: 1,
          get onlyGetProp() { return this.onlyGetProp_; },
        // TODO(b/115853720); Should remove setter for onlyGetProp, because
        // there is no assignment to it
          set onlyGetProp(value) { this.onlyGetProp_ = value; },
        // used property included for comparison, to show that it isn't removed
          get usedProp() { return this.usedProp_; },
          set usedProp(value) { this.usedProp_ = value; },
        };
        // This reference must be left even though nothing uses it, because we assume getters
        // could have side effects.
        obj.onlyGetProp;
        obj.usedProp = 2;
        alert(obj.usedProp);
        """,
        """
        const obj = {
        // TODO(b/130682799): onlyGetProp_ and usedProp_ should not be removed
          get onlyGetProp() { return this.onlyGetProp_; },
        // TODO(b/115853720); Should remove setter for onlyGetProp, because
        // there is no assignment to it
          set onlyGetProp(value) { this.onlyGetProp_ = value; },
        // used property included for comparison, to show that it isn't removed
          get usedProp() { return this.usedProp_; },
          set usedProp(value) { this.usedProp_ = value; },
        };
        // This reference must be left even though nothing uses it, because we assume getters
        // could have side effects.
        obj.onlyGetProp;
        obj.usedProp = 2;
        alert(obj.usedProp);
        """);
  }

  @Test
  public void referenceToGetterPropIsNotRemoved_forObjectLiteralPrototype() {
    CompilerOptions options = createCompilerOptions();

    test(
        options,
        """
        /** @constructor */
        function ES5Class() {
          /** @private {number} */
          this.onlyGetProp_ = 1;
          /** @private {number} */
          this.usedProp_ = 2;
        }
        ES5Class.prototype = {
          /** @type {number} */
          get onlyGetProp() { return this.onlyGetProp_; },
        // TODO(b/115853720); Should remove setter for onlyGetProp, because
        // there is no assignment to it
          set onlyGetProp(value) { this.onlyGetProp_ = value; },
        // used property included for comparison, to show that it isn't removed
          /** @type {number} */
          get usedProp() { return this.usedProp_; },
          set usedProp(value) { this.usedProp_ = value; },
        };
        const obj = new ES5Class();
        // This reference must be left even though nothing uses it, because we assume getters
        // could have side effects.
        obj.onlyGetProp;
        obj.usedProp = 2;
        alert(obj.usedProp);
        """,
        """
        /** @constructor */
        function ES5Class() {
          /** @private {number} */
          this.onlyGetProp_ = 1;
          /** @private {number} */
          this.usedProp_ = 2;
        }
        ES5Class.prototype = {
          /** @type {number} */
          get onlyGetProp() { return this.onlyGetProp_; },
        // TODO(b/115853720); Should remove setter for onlyGetProp, because
        // there is no assignment to it
          set onlyGetProp(value) { this.onlyGetProp_ = value; },
        // used property included for comparison, to show that it isn't removed
          /** @type {number} */
          get usedProp() { return this.usedProp_; },
          set usedProp(value) { this.usedProp_ = value; },
        };
        const obj = new ES5Class();
        // This reference must be left even though nothing uses it, because we assume getters
        // could have side effects.
        obj.onlyGetProp;
        obj.usedProp = 2;
        alert(obj.usedProp);
        """);
  }

  @Test
  public void referenceToGetterPropIsNotRemoved_forObjectDotDefineProperty() {
    CompilerOptions options = createCompilerOptions();

    test(
        options,
        """
        /**
         * @fileoverview
         * @suppress {missingProperties}
         */
        /** @constructor */
        function ES5Class() {
        }
        // static properties used by static getter/setter properties
        /** @private {number} */
        ES5Class.onlyGetProp_ = 1;
        /** @private {number} */
        ES5Class.usedProp_ = 2;
        Object.defineProperty(
            ES5Class,
            'onlyGetProp',
            {
              configurable: false,
              writable: true,
              /**
               * @this {typeof ES5Class}
               * @return {number}
               */
              get: function() { return this.onlyGetProp_; },
              /**
               * @this {typeof ES5Class}
               * @param {number} value
               */
              set: function(value) { this.onlyGetProp_ = value; },
            });
        Object.defineProperty(
            ES5Class,
        // used property included for comparison, to show that it isn't removed
            'usedProp',
            {
              configurable: false,
              writable: true,
              /**
               * @this {typeof ES5Class}
               * @return {number}
               */
              get: function() { return this.usedProp_; },
              /**
               * @this {typeof ES5Class}
               * @param {number} value
               */
              set: function(value) { this.usedProp_ = value; },
            });
        ES5Class.onlyGetProp;
        ES5Class.usedProp = 2;
        alert(ES5Class.usedProp);
        """,
        """
        /** @constructor */
        function ES5Class() {
        }
        // TODO(b/130682799): Properties used in getters and setters should not be removed
        Object.defineProperty(
            ES5Class,
            'onlyGetProp',
            {
              configurable: !1, // unrelated optimization
              writable: !0,
              /**
               * @this {typeof ES5Class}
               * @return {number}
               */
              get: function() { return this.onlyGetProp_; },
              /**
               * @this {typeof ES5Class}
               * @param {number} value
               */
              set: function(value) { this.onlyGetProp_ = value; },
            });
        Object.defineProperty(
            ES5Class,
        // used property included for comparison, to show that it isn't removed
            'usedProp',
            {
              configurable: !1, // unrelated optimization
              writable: !0,
              /**
               * @this {typeof ES5Class}
               * @return {number}
               */
              get: function() { return this.usedProp_; },
              /**
               * @this {typeof ES5Class}
               * @param {number} value
               */
              set: function(value) { this.usedProp_ = value; },
            });
        ES5Class.onlyGetProp;
        // TODO(b/72879754): property with getter should not be inlined
        alert(2); // prop with getter not inlined
        """);
  }

  @Test
  public void referenceToGetterPropIsNotRemoved_forObjectDotDefineProperties() {
    CompilerOptions options = createCompilerOptions();

    test(
        options,
        """
        /**
         * @fileoverview
         * @suppress {missingProperties}
         */
        /** @constructor */
        function ES5Class() {
        }
        // static properties used by static getter/setter properties
        /** @private {number} */
        ES5Class.onlyGetProp_ = 1;
        /** @private {number} */
        ES5Class.usedProp_ = 2;
        Object.defineProperties(
            ES5Class,
            {
              onlyGetProp: {
        // use pre-optimized boolean values to avoid unrelated changes
                configurable: !1,
                writable: !0,
                /**
                 * @this {typeof ES5Class}
                 * @return {number}
                 */
                get: function() { return this.onlyGetProp_; },
                /**
                 * @this {typeof ES5Class}
                 * @param {number} value
                 */
                set: function(value) { this.onlyGetProp_ = value; },
              },
        // used property included for comparison, to show that it isn't removed
              usedProp: {
        // use pre-optimized boolean values to avoid unrelated changes
                configurable: !0,
                writable: !0,
                /**
                 * @this {typeof ES5Class}
                 * @return {number}
                 */
                get: function() { return this.usedProp_; },
                /**
                 * @this {typeof ES5Class}
                 * @param {number} value
                 */
                set: function(value) { this.usedProp_ = value; },
              }
            });
        ES5Class.onlyGetProp; // only read, never set, and value unused
        ES5Class.usedProp = 2;
        alert(ES5Class.usedProp);
        """,
        """
        function ES5Class() {
        }

        Object.defineProperties(
            ES5Class,
            {
              onlyGetProp: {
                configurable: !1, // unrelated optimization
                writable: !0,
                /**
                 * @this {typeof ES5Class}
                 * @return {number}
                 */
                get: function() { return this.onlyGetProp_; },
                /**
                 * @this {typeof ES5Class}
                 * @param {number} value
                 */
                set: function(value) { this.onlyGetProp_ = value; },
              },
              usedProp: {
        // use pre-optimized boolean values to avoid unrelated changes
                configurable: !0,
                writable: !0,
                /**
                 * @this {typeof ES5Class}
                 * @return {number}
                 */
                get: function() { return this.usedProp_; },
                /**
                 * @this {typeof ES5Class}
                 * @param {number} value
                 */
                set: function(value) { this.usedProp_ = value; },
              }
            });
        ES5Class.onlyGetProp; // reference to property with getter must remain
        ES5Class.usedProp = 2;
        alert(ES5Class.usedProp);
        """);
  }

  @Test
  public void referenceToGetterPropIsNotRemoved_forPrototypeObjectDotDefineProperties() {
    CompilerOptions options = createCompilerOptions();

    test(
        options,
        """
        /**
         * @fileoverview
         * @suppress {missingProperties}
         */
        /** @constructor */
        function ES5Class() {
          /** @private {number} */ this.onlyGetProp_ = 1;
          /** @private {number} */ this.usedProp_ = 2;
        }
        Object.defineProperties(
            ES5Class.prototype,
            {
              onlyGetProp: {
                configurable: false,
                writable: true,
                /**
                 * @this {ES5Class}
                 * @return {number}
                 */
                get: function() { return this.onlyGetProp_; },
                /**
                 * @this {ES5Class}
                 * @param {number} value
                 */
                set: function(value) { this.onlyGetProp_ = value; },
              },
        // used property included for comparison, to show that it isn't removed
              usedProp: {
                configurable: false,
                writable: true,
                /**
                 * @this {ES5Class}
                 * @return {number}
                 */
                get: function() { return this.usedProp_; },
                /**
                 * @this {ES5Class}
                 * @param {number} value
                 */
                set: function(value) { this.usedProp_ = value; },
              }
            });
        const onlyGetObj = new ES5Class();
        const unusedVar = onlyGetObj.onlyGetProp;
        const usedObj = new ES5Class();
        usedObj.usedProp = 2;
        alert(usedObj.usedProp);
        """,
        """
        /** @constructor */
        function ES5Class() {
          /** @private {number} */ this.onlyGetProp_ = 1;
          /** @private {number} */ this.usedProp_ = 2;
        }
        Object.defineProperties(
            ES5Class.prototype,
            {
              onlyGetProp: {
                configurable: !1,
                writable: !0,
                /**
                 * @this {ES5Class}
                 * @return {number}
                 */
                get: function() { return this.onlyGetProp_; },
        // TODO(b/135640150): This setter is removable.
                /**
                 * @this {ES5Class}
                 * @param {number} value
                 */
                set: function(value) { this.onlyGetProp_ = value; },
              },
              usedProp: {
                configurable: !1,
                writable: !0,
                /**
                 * @this {ES5Class}
                 * @return {number}
                 */
                get: function() { return this.usedProp_; },
                /**
                 * @this {ES5Class}
                 * @param {number} value
                 */
                set: function(value) { this.usedProp_ = value; },
              }
            });
        new ES5Class().onlyGetProp;
        const usedObj = new ES5Class();
        usedObj.usedProp = 2;
        alert(usedObj.usedProp);
        """);
  }

  @Test
  public void referenceToGetterPropIsNotRemoved_forStaticClassProperties() {
    CompilerOptions options = createCompilerOptions();

    test(
        options,
        """
        class C {
          /** @type {number} */
          static get onlyGetProp() { return this.onlyGetProp_; }
          static set onlyGetProp(value) { this.onlyGetProp_ = value; }
        // used property included for comparison, to show that it isn't removed
          /** @type {number} */
          static get usedProp() { return this.usedProp_; }
          static set usedProp(value) { this.usedProp_ = value; }
        }
        // static properties used by static getter/setter properties
        /** @private {number} */
        C.onlyGetProp_ = 1;
        /** @private {number} */
        C.usedProp_ = 2;
         // use of getters/setters
        C.onlyGetProp; // value unused here and never set anywhere
        C.usedProp = 2;
        alert(C.usedProp);
        """,
        """
        class C {
          /** @type {number} */
          static get onlyGetProp() { return this.onlyGetProp_; }
        // TODO(b/115853720): unused setter should be removed.
          static set onlyGetProp(value) { this.onlyGetProp_ = value; }
          /** @type {number} */
          static get usedProp() { return this.usedProp_; }
          static set usedProp(value) { this.usedProp_ = value; }

        }
        // TODO(b/130682799): Properties used inside getters and setters should not be removed

        // reference to property with getter must not be removed
        C.onlyGetProp;
        C.usedProp = 2;
        // Property with getter should not be inlined
        alert(C.usedProp);
        """);
  }

  @Test
  public void referenceToGetterPropIsNotRemoved_forClassProperties() {
    CompilerOptions options = createCompilerOptions();

    test(
        options,
        """
        class C {
          constructor() {
            /** @private {number} */ this.onlyGetProp_ = 1;
            /** @private {number} */ this.usedProp_ = 2;
          }
          /** @type {number} */
          get onlyGetProp() { return this.onlyGetProp_; }
          set onlyGetProp(value) { this.onlyGetProp_ = value; }
        // used property included for comparison, to show that it isn't removed
          /** @type {number} */
          get usedProp() { return this.usedProp_; }
          set usedProp(value) { this.usedProp_ = value; }

        }
        const onlyGetObj = new C;
        // reference to property with getter cannot be removed
        var unusedVar = onlyGetObj.onlyGetProp;
        const usedObj = new C;
        usedObj.usedProp = 2;
        alert(usedObj.usedProp); // property with getter cannot be inlined
        """,
        """
        class C {
          constructor() {
            /** @private {number} */ this.onlyGetProp_ = 1;
            /** @private {number} */ this.usedProp_ = 2;
          }
          /** @type {number} */
          get onlyGetProp() { return this.onlyGetProp_; }
        // TODO(b/135640150): This setter is removable.
          set onlyGetProp(value) { this.onlyGetProp_ = value; }
          /** @type {number} */
          get usedProp() { return this.usedProp_; }
          set usedProp(value) { this.usedProp_ = value; }
        }
        new C().onlyGetProp;
        const usedObj = new C;
        usedObj.usedProp = 2;
        alert(usedObj.usedProp); // property with getter cannot be inlined
        """);
  }

  @Test
  public void testSuperGetter_fromStaticFieldInitializer() {
    CompilerOptions options = createCompilerOptions();

    // Avoiding diffing the polyfills.
    options.setRuntimeLibraryMode(RuntimeJsLibManager.RuntimeLibraryMode.RECORD_ONLY);

    // Include externs definitions for the stuff that would have been injected.
    var externsList =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(
                SourceFile.fromCode(
                    "extraExterns",
                    """
                    /** @const */
                    var $jscomp = {};
                    $jscomp.global = {};
                    $jscomp.inherits = function(subClass, superClass) {};
                    """));
    externs = externsList.build();

    var src =
        """
        class Parent {
          static getName() {
            return 'Parent';
          }
          static get greeting() {
            return 'Hello ' + this.getName();
          }
        }
        class Child extends Parent {
          static getName() {
            return 'Child';
          }
          static msg = super.greeting;  // 'Hello Child'
        }
        alert(Child.msg);
        """;

    // Because we assume static inheritance is not used, we don't expect `Child.msg` to resolve to
    // the correct value.
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2021);
    options.setAssumeStaticInheritanceIsNotUsed(true);
    test(
        options,
        src,
        """
        class Parent {
          static get greeting() {
            // TODO: user - Properties used in getters and setters should not be removed
            return "Hello " + this.getName();
          }
        }
        var Child$msg = Parent.greeting;
        alert(Child$msg);
        """);

    // We expect `Child.msg` to resolve to the correct value.
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2021);
    options.setAssumeStaticInheritanceIsNotUsed(false);
    test(
        options,
        src,
        """
        class Parent {
          static getName() {
            return "Parent";
          }
          static get greeting() {
            return "Hello " + this.getName();
          }
        }
        class Child extends Parent {
          static getName() {
            return "Child";
          }
          static $jscomp$staticInit$98447280$0() {
            Child.msg = super.greeting;
          }
        }
        Child.$jscomp$staticInit$98447280$0();
        alert(Child.msg);
        """);

    // Because we assume static inheritance is not used, we don't expect `Child.msg` to resolve to
    // the correct value.
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setAssumeStaticInheritanceIsNotUsed(true);
    test(
        options,
        src,
        """
        function Parent() {}
        Parent.greeting;
        $jscomp.global.Object.defineProperties(Parent, {
          greeting: {
            configurable: !0,
            enumerable: !0,
            get: function() {
              // TODO: user - Properties used in getters and setters should not be removed
              return 'Hello ' + this.getName();
            }
          }
        });
        function Child() {}
        var Child$msg;
        $jscomp.inherits(Child, Parent);
        Child$msg = Reflect.get(Parent, 'greeting', Child);
        alert(Child$msg);
        """);

    // We expect `Child.msg` to resolve to the correct value.
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setAssumeStaticInheritanceIsNotUsed(false);
    test(
        options,
        src,
        """
        function Parent() {}
        Parent.greeting;
        Parent.getName = function() {
          return 'Parent';
        };
        $jscomp.global.Object.defineProperties(Parent, {
          greeting: {
            configurable: !0,
            enumerable: !0,
            get: function() {
              return 'Hello ' + this.getName();
            }
          }
        });
        function Child() {}
        $jscomp.inherits(Child, Parent);
        Child.getName = function() {
          return 'Child';
        };
        Child.msg = Reflect.get(Parent, 'greeting', Child);
        alert(Child.msg);
        """);

    // Ensure that 'greeting' is properly renamed.
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    test(
        options,
        src,
        """
        function a() {}
        a.b;
        a.a = function() {
          return 'Parent';
        };
        $jscomp.global.Object.defineProperties(a, {
          // b was 'greeting'
          b: {
            configurable: !0,
            enumerable: !0,
            get: function() {
              return 'Hello ' + this.a();
            }
          }
        });
        function b() {}
        $jscomp.inherits(b, a);
        b.a = function() {
          return 'Child';
        };
        // 'b' (in quotes) was 'greeting'
        b.c = Reflect.get(a, 'b', b);
        alert(b.c);
        """);
  }

  @Test
  public void testSuperGetter_fromStaticMethod() {
    CompilerOptions options = createCompilerOptions();

    // Avoiding diffing the polyfills.
    options.setRuntimeLibraryMode(RuntimeJsLibManager.RuntimeLibraryMode.RECORD_ONLY);

    // Include externs definitions for the stuff that would have been injected.
    var externsList =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(
                SourceFile.fromCode(
                    "extraExterns",
                    """
                    /** @const */
                    var $jscomp = {};
                    $jscomp.global = {};
                    $jscomp.inherits = function(subClass, superClass) {};
                    """));
    externs = externsList.build();

    var src =
        """
        class Parent {
          static getName() {
            return 'Parent';
          }
          static get greeting() {
            return 'Hello ' + this.getName();
          }
        }
        class Child extends Parent {
          static getName() {
            return 'Child';
          }
          static getGreeting() {
            return super.greeting;
          }
        }
        class GrandChild extends Child {
          static getName() {
            return 'GrandChild';
          }
        }

        alert(Parent.greeting);
        alert(Child.greeting);
        alert(GrandChild.greeting);

        alert(Child.getGreeting());
        alert(GrandChild.getGreeting());
        """;

    // Because we assume static inheritance is not used, we don't expect the greeting to be correct.
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2021);
    options.setAssumeStaticInheritanceIsNotUsed(true);
    test(
        options,
        src,
        """
        class Parent {
          static get greeting() {
            // TODO: user - Properties used in getters and setters should not be removed
            return "Hello " + this.getName();
          }
        }
        class Child extends Parent {}
        class GrandChild extends Child {}

        alert(Parent.greeting);  // Throws as getName is missing
        alert(Child.greeting);
        alert(GrandChild.greeting);

        alert(Parent.greeting);
        alert(Parent.greeting);
        """);

    // We expect the greeting to be correct.
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2021);
    options.setAssumeStaticInheritanceIsNotUsed(false);
    testSame(options, src);

    // Because we assume static inheritance is not used, we don't expect the greeting to be correct.
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setAssumeStaticInheritanceIsNotUsed(true);
    test(
        options,
        src,
        """
        function Parent() {}
        Parent.greeting;
        $jscomp.global.Object.defineProperties(Parent, {
          greeting: {
            configurable: !0,
            enumerable: !0,
            get: function() {
              // TODO: user - Properties used in getters and setters should not be removed
              return 'Hello ' + this.getName();
            }
          }
        });
        function Child() {}
        $jscomp.inherits(Child, Parent);
        Child.getGreeting = function() {
          return Reflect.get(Parent, 'greeting', this);
        };
        function GrandChild() {}
        $jscomp.inherits(GrandChild, Child);
        GrandChild.getGreeting = Child.getGreeting;

        alert(Parent.greeting);
        alert(Child.greeting);
        alert(GrandChild.greeting);

        alert(Child.getGreeting());
        alert(GrandChild.getGreeting());
        """);

    // We expect the greeting to be correct.
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setAssumeStaticInheritanceIsNotUsed(false);
    test(
        options,
        src,
        """
        function Parent() {}
        Parent.greeting;
        Parent.getName = function() {
          return 'Parent';
        };
        $jscomp.global.Object.defineProperties(Parent, {
          greeting: {
            configurable: !0,
            enumerable: !0,
            get: function() {
              return 'Hello ' + this.getName();
            }
          }
        });
        function Child() {}
        $jscomp.inherits(Child, Parent);
        Child.getName = function() {
          return 'Child';
        };
        Child.getGreeting = function() {
          return Reflect.get(Parent, 'greeting', this);
        };
        function GrandChild() {}
        $jscomp.inherits(GrandChild, Child);
        GrandChild.getGreeting = Child.getGreeting;
        GrandChild.getName = function() {
          return 'GrandChild';
        };

        alert(Parent.greeting);  // Alerts, "Hello Parent"
        alert(Child.greeting);  // Alerts, "Hello Child"
        alert(GrandChild.greeting);  // Alerts, "Hello GrandChild"

        alert(Child.getGreeting());  // Alerts, "Hello Child"
        alert(GrandChild.getGreeting());  // Alerts, "Hello GrandChild"
        """);

    // Ensure that 'greeting' is properly renamed.
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    test(
        options,
        src,
        """
        function a() {}
        a.a;
        a.c = function() {
          return 'Parent';
        };
        $jscomp.global.Object.defineProperties(a, {
          // a was greeting
          a: {
            configurable: !0,
            enumerable: !0,
            get: function() {
              return 'Hello ' + this.c();
            }
          }
        });
        function b() {}
        $jscomp.inherits(b, a);
        b.c = function() {
          return 'Child';
        };
        b.b = function() {
          // 'a' (in quotes) was 'greeting'
          return Reflect.get(a, 'a', this);
        };
        function c() {}
        $jscomp.inherits(c, b);
        c.b = b.b;
        c.c = function() {
          return 'GrandChild';
        };

        alert(a.a);  // Alerts, "Hello Parent"
        alert(b.a);  // Alerts, "Hello Child"
        alert(c.a);  // Alerts, "Hello GrandChild"

        alert(b.b());  // Alerts, "Hello Child"
        alert(c.b());  // Alerts, "Hello GrandChild"
        """);
  }
}
