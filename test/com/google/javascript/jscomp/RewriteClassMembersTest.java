/*
 * Copyright 2021 The Closure Compiler Authors.
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
import static org.junit.Assert.assertThrows;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test cases for transpilation pass that replaces public class fields and class static blocks:
 * <code><pre>
 * class C {
 *   x = 2;
 *   ['y'] = 3;
 *   static a;
 *   static ['b'] = 'hi';
 *   static {
 *     let c = 4;
 *     this.z = c;
 *   }
 * }
 * </pre></code>
 */
@RunWith(JUnit4.class)
public final class RewriteClassMembersTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableNormalize();
    enableTypeInfoValidation();
    enableTypeCheck();
    replaceTypesWithColors();
    enableMultistageCompilation();
    setGenericNameReplacements(Es6NormalizeClasses.GENERIC_NAME_REPLACEMENTS);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new Es6NormalizeClasses(compiler);
  }

  Options withOptions() {
    return new Options(true, LanguageMode.ECMASCRIPT_2021);
  }

  record Options(boolean assumeStaticInheritanceIsNotUsed, LanguageMode languageOut) {
    Options useStaticInheritance() {
      return new Options(false, languageOut());
    }

    Options useEs2022LanguageOut() {
      return new Options(assumeStaticInheritanceIsNotUsed(), LanguageMode.ECMASCRIPT_NEXT);
    }

    Options useEs5LanguageOut() {
      return new Options(assumeStaticInheritanceIsNotUsed(), LanguageMode.ECMASCRIPT5);
    }
  }

  @Override
  protected void test(String input, String expected) {
    test(withOptions(), input, expected);
  }

  void test(Options options, String input, String expected) {
    setAssumeStaticInheritanceIsNotUsed(options.assumeStaticInheritanceIsNotUsed());
    setLanguageOut(options.languageOut());

    super.test(input, expected);
  }

  @Override
  protected void testError(String input, DiagnosticType error) {
    testError(withOptions(), input, error);
  }

  void testError(Options options, String input, DiagnosticType error) {
    setAssumeStaticInheritanceIsNotUsed(options.assumeStaticInheritanceIsNotUsed());
    setLanguageOut(options.languageOut());

    super.testError(input, error);
  }

  @Override
  protected void testSame(String src) {
    test(withOptions(), src, src);
  }

  void testSame(Options options, String src) {
    test(options, src, src);
  }

  @Test
  public void testClassStaticBlock_superRef() {
    test(
        """
        class B {
          static y = 3;
        }
        class C extends B {
          static {
            let x = super.y;
          }
        }
        """,
        """
        class B {}
        B.y = 3;
        class C extends B {}
        {
          // TODO (tflo): Reflect.get(B, 'y') is the technically correct way.
          let x = B.y;
        }
        """);
  }

  @Test
  public void testPrivateField() {
    test(
        """
        class Foo {
          #field;
          #field_initialized = 1;
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          constructor() {
            const PRIVATE$1 = Object.create(null);
            PRIVATE_MAP$0.set(this, PRIVATE$1);
            PRIVATE$1.field = void 0;
            PRIVATE$1.field_initialized = 1;
          }
        }
        """);
  }

  @Test
  public void testPrivateField_interleavedWithPublicFields() {
    test(
        """
        class Foo {
          f1 = 1;
          #pf2 = this.f1 + 2;
          f3 = this.#pf2 + 3;
          #pf4 = this.f3 + this.#pf2;
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          constructor() {
            const PRIVATE$1 = Object.create(null);
            PRIVATE_MAP$0.set(this, PRIVATE$1);
            this.f1 = 1;
            PRIVATE$1.pf2 = this.f1 + 2;
            this.f3 = PRIVATE_MAP$0.get(this).pf2 + 3;
            PRIVATE$1.pf4 = this.f3 + PRIVATE_MAP$0.get(this).pf2;
          }
        }
        """);
  }

  @Test
  public void testPrivateStaticField() {
    test(
        """
        class Foo {
          static #static_field;
          static #static_field_initialized = 2;
        }
        """,
        """
        const STATIC_PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          static STATIC_INIT$2() {
            const PRIVATE$1 = Object.create(null);
            STATIC_PRIVATE_MAP$0.set(Foo, PRIVATE$1);
            PRIVATE$1.static_field = void 0;
            PRIVATE$1.static_field_initialized = 2;
          }
        }
        Foo.STATIC_INIT$2();
        """);
  }

  @Test
  public void testPrivateAndStaticFieldMultipleClasses() {
    test(
        """
        class Foo {
          #field;
          #field_initialized = 1;
          static #static_field;
          static #static_field_initialized = 2;
        }
        class Bar {
          #field;
          #field_initialized = 1;
          static #static_field;
          static #static_field_initialized = 2;
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const STATIC_PRIVATE_MAP$1 = new $jscomp.PrivateMap();
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(null);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
            PRIVATE$2.field = void 0;
            PRIVATE$2.field_initialized = 1;
          }
          static STATIC_INIT$4() {
            const PRIVATE$3 = Object.create(null);
            STATIC_PRIVATE_MAP$1.set(Foo, PRIVATE$3);
            PRIVATE$3.static_field = void 0;
            PRIVATE$3.static_field_initialized = 2;
          }
        }
        Foo.STATIC_INIT$4();
        const PRIVATE_MAP$5 = new $jscomp.PrivateMap();
        const STATIC_PRIVATE_MAP$6 = new $jscomp.PrivateMap();
        class Bar {
          constructor() {
            const PRIVATE$7 = Object.create(null);
            PRIVATE_MAP$5.set(this, PRIVATE$7);
            PRIVATE$7.field = void 0;
            PRIVATE$7.field_initialized = 1;
          }
          static STATIC_INIT$9() {
            const PRIVATE$8 = Object.create(null);
            STATIC_PRIVATE_MAP$6.set(Bar, PRIVATE$8);
            PRIVATE$8.static_field = void 0;
            PRIVATE$8.static_field_initialized = 2;
          }
        }
        Bar.STATIC_INIT$9();
        """);
  }

  @Test
  public void testPrivateMethod() {
    test(
        """
        class Foo {
          #method() {}
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          method: {
            value: function() {}
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
        }
        """);
  }

  @Test
  public void testPrivateStaticMethod() {
    test(
        """
        class Foo {
          static #staticMethod() { return 42; }
          static callStatic() {
            return Foo.#staticMethod();
          }
        }
        """,
        """
        const STATIC_PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          static callStatic() {
            return STATIC_PRIVATE_MAP$0.get(Foo).staticMethod.call(Foo);
          }
          static STATIC_INIT$2() {
            const PRIVATE$1 = Object.create(null);
            STATIC_PRIVATE_MAP$0.set(Foo, PRIVATE$1);
            PRIVATE$1.staticMethod = function() {
              return 42;
            };
          }
        }
        Foo.STATIC_INIT$2();
        """);
  }

  @Test
  public void testPrivateStaticMethodCallThis() {
    test(
        """
        class Foo {
          static #staticMethod() { return 42; }
          static #helper() { return this.#staticMethod(); }
          static callStatic() {
            return this.#helper();
          }
        }
        """,
        """
        const STATIC_PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          static callStatic() {
            return STATIC_PRIVATE_MAP$0.get(this).helper.call(this);
          }
          static STATIC_INIT$2() {
            const PRIVATE$1 = Object.create(null);
            STATIC_PRIVATE_MAP$0.set(Foo, PRIVATE$1);
            PRIVATE$1.staticMethod = function() {
              return 42;
            };
            PRIVATE$1.helper = function() {
              return STATIC_PRIVATE_MAP$0.get(this).staticMethod.call(this);
            };
          }
        }
        Foo.STATIC_INIT$2();
        """);
  }

  @Test
  public void testPrivateStaticMethodAndFieldOrder() {
    test(
        """
        class C {
          static #a = 1;
          static #b() { return this.#a + 1; }
          static #c = this.#b() + 1;
          static getC() { return this.#c; }
        }
        """,
        """
        const STATIC_PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class C {
          static getC() {
            return STATIC_PRIVATE_MAP$0.get(this).c;
          }
          static STATIC_INIT$2() {
            const PRIVATE$1 = Object.create(null);
            STATIC_PRIVATE_MAP$0.set(C, PRIVATE$1);
            PRIVATE$1.b = function() {
              return STATIC_PRIVATE_MAP$0.get(this).a + 1;
            };
            PRIVATE$1.a = 1;
            PRIVATE$1.c = STATIC_PRIVATE_MAP$0.get(C).b.call(C) + 1;
          }
        }
        C.STATIC_INIT$2();
        """);
  }

  @Test
  public void testPrivateAndStaticMethod() {
    test(
        """
        class Foo {
          #instanceMethod() { return 1; }
          static #staticMethod() { return 2; }
          callInstance() { return this.#instanceMethod(); }
          static callStatic() { return Foo.#staticMethod(); }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$2 = Object.create(null, {
          instanceMethod: {
            value: function() {
              return 1;
            }
          }
        });
        const STATIC_PRIVATE_MAP$1 = new $jscomp.PrivateMap();
        class Foo {
          constructor() {
            const PRIVATE$3 = Object.create(PRIVATE_PROTO$2);
            PRIVATE_MAP$0.set(this, PRIVATE$3);
          }
          callInstance() {
            return PRIVATE_MAP$0.get(this).instanceMethod.call(this);
          }
          static callStatic() {
            return STATIC_PRIVATE_MAP$1.get(Foo).staticMethod.call(Foo);
          }
          static STATIC_INIT$5() {
            const PRIVATE$4 = Object.create(null);
            STATIC_PRIVATE_MAP$1.set(Foo, PRIVATE$4);
            PRIVATE$4.staticMethod = function() {
              return 2;
            };
          }
        }
        Foo.STATIC_INIT$5();
        """);
  }

  @Test
  public void testPrivateGetter() {
    test(
        """
        class Foo {
          get #prop() { return 3; }
          getValue() {
            return this.#prop;
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          prop: {
            get: function() {
              return 3;
            }
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
          getValue() {
            return PRIVATE_MAP$0.get(this).prop;
          }
        }
        """);
  }

  @Test
  public void testPrivateStaticGetter() {
    test(
        """
        class Foo {
          static get #prop() { return 4; }
          static getValue() {
            return Foo.#prop;
          }
        }
        """,
        """
        const STATIC_PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          static getValue() {
            return STATIC_PRIVATE_MAP$0.get(Foo).prop;
          }
          static STATIC_INIT$2() {
            const PRIVATE$1 = Object.create(null);
            STATIC_PRIVATE_MAP$0.set(Foo, PRIVATE$1);
            Object.defineProperty(PRIVATE$1, "prop", {
              get: function() {
                return 4;
              }
            });
          }
        }
        Foo.STATIC_INIT$2();
        """);
  }

  @Test
  public void testPrivateGetterWithThis() {
    ignoreWarnings(TypeCheck.INEXISTENT_PROPERTY);
    test(
        """
        class Foo {
          get #prop() { return this.x; }
          getValue() {
            return this.#prop;
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          prop: {
            get: function() {
              return this.$self.x;
            }
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE$2.$self = this;
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
          getValue() {
            return PRIVATE_MAP$0.get(this).prop;
          }
        }
        """);
  }

  @Test
  public void testPrivateStaticGetterWithThis() {
    ignoreWarnings(TypeCheck.INEXISTENT_PROPERTY);
    test(
        """
        class Foo {
          static get #prop() { return this.x; }
          static getValue() {
            return Foo.#prop;
          }
        }
        """,
        """
        const STATIC_PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          static getValue() {
            return STATIC_PRIVATE_MAP$0.get(Foo).prop;
          }
          static STATIC_INIT$2() {
            const PRIVATE$1 = Object.create(null);
            PRIVATE$1.$self = Foo;
            STATIC_PRIVATE_MAP$0.set(Foo, PRIVATE$1);
            Object.defineProperty(PRIVATE$1, "prop", {
              get: function() {
                return this.$self.x;
              }
            });
          }
        }
        Foo.STATIC_INIT$2();
        """);
  }

  @Test
  public void testPrivateSetter() {
    test(
        """
        class Foo {
          set #prop(val) {}
          setValue(val) {
            this.#prop = val;
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          prop: {
            set: function(val) {}
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
          setValue(val$jscomp$1) {
            PRIVATE_MAP$0.get(this).prop = val$jscomp$1;
          }
        }
        """);
  }

  @Test
  public void testPrivateSetterWithThis() {
    ignoreWarnings(TypeCheck.INEXISTENT_PROPERTY);
    test(
        """
        class Foo {
          x = 0;
          set #prop(val) { this.x = val; }
          setValue(val) {
            this.#prop = val;
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          prop: {
            set: function(val) {
              this.$self.x = val;
            }
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE$2.$self = this;
            PRIVATE_MAP$0.set(this, PRIVATE$2);
            this.x = 0;
          }
          setValue(val$jscomp$1) {
            PRIVATE_MAP$0.get(this).prop = val$jscomp$1;
          }
        }
        """);
  }

  @Test
  public void testPrivateStaticSetter() {
    test(
        """
        class Foo {
          static set #prop(val) {}
        }
        """,
        """
        const STATIC_PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          static STATIC_INIT$2() {
            const PRIVATE$1 = Object.create(null);
            STATIC_PRIVATE_MAP$0.set(Foo, PRIVATE$1);
            Object.defineProperty(PRIVATE$1, "prop", {
              set: function(val) {}
            });
          }
        }
        Foo.STATIC_INIT$2();
        """);
  }

  @Test
  public void testPrivateIdInOperator() {
    test(
        """
        class Foo {
          #field;
          brandCheck(x) { return #field in x; }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          constructor() {
            const PRIVATE$1 = Object.create(null);
            PRIVATE_MAP$0.set(this, PRIVATE$1);
            PRIVATE$1.field = void 0;
          }
          brandCheck(x) {
            return PRIVATE_MAP$0.has(x);
          }
        }
        """);
  }

  @Test
  public void testPrivateIdInOperator_static() {
    test(
        """
        class Foo {
          static #staticField;
          brandCheck(x) { return #staticField in x; }
        }
        """,
        """
        const STATIC_PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          brandCheck(x) {
            return STATIC_PRIVATE_MAP$0.has(x);
          }
          static STATIC_INIT$2() {
            const PRIVATE$1 = Object.create(null);
            STATIC_PRIVATE_MAP$0.set(Foo, PRIVATE$1);
            PRIVATE$1.staticField = void 0;
          }
        }
        Foo.STATIC_INIT$2();
        """);
  }

  @Test
  public void testPrivateIdInOperator_methodAndAccessor() {
    test(
        """
        class Foo {
          #method() {}
          get #prop() { return 1; }
          brandCheck(x) { return #method in x && #prop in x; }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          method: {
            value: function() {}
          },
          prop: {
            get: function() {
              return 1;
            }
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
          brandCheck(x) {
            return PRIVATE_MAP$0.has(x) && PRIVATE_MAP$0.has(x);
          }
        }
        """);
  }

  @Test
  public void testPrivateDestructuringAssignment() {
    ignoreWarnings(TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);
    test(
        """
        class Foo {
          #x;
          assignFromObj(obj) {
            ({ val: this.#x } = obj);
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          constructor() {
            const PRIVATE$1 = Object.create(null);
            PRIVATE_MAP$0.set(this, PRIVATE$1);
            PRIVATE$1.x = void 0;
          }
          assignFromObj(obj) {
            ({ val: PRIVATE_MAP$0.get(this).x } = obj);
          }
        }
        """);
  }

  @Test
  public void testPrivateMethodWithComplexParameters() {
    test(
        """
        class Foo {
          #method(a = 1, ...rest) { return a + rest.length; }
          callMethod() { return this.#method(10, 20, 30); }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          method: {
            value: function(a = 1, ...rest) {
              return a + rest.length;
            }
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
          callMethod() {
            return PRIVATE_MAP$0.get(this).method.call(this, 10, 20, 30);
          }
        }
        """);
  }

  @Test
  public void testPrivateUpdateAndCompoundAssignment() {
    test(
        """
        class Foo {
          #x = 1;
          increment() {
            this.#x++;
            this.#x += 10;
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          constructor() {
            const PRIVATE$1 = Object.create(null);
            PRIVATE_MAP$0.set(this, PRIVATE$1);
            PRIVATE$1.x = 1;
          }
          increment() {
            PRIVATE_MAP$0.get(this).x++;
            PRIVATE_MAP$0.get(this).x += 10;
          }
        }
        """);
  }

  // TODO(b/236744850): Consider memoizing PRIVATE_MAP.get(this) into a local temporary variable
  // when multiple private accesses occur on 'this' within the same function or statement scope.
  @Test
  public void testPrivateFieldAccess() {
    test(
        """
        class Foo {
          #x = 1;
          getX(otherObj1) {
            return otherObj1.#x;
          }
          setX(otherObj2, v) {
            otherObj2.#x = v;
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          constructor() {
            const PRIVATE$1 = Object.create(null);
            PRIVATE_MAP$0.set(this, PRIVATE$1);
            PRIVATE$1.x = 1;
          }
          getX(otherObj1) {
            return PRIVATE_MAP$0.get(otherObj1).x;
          }
          setX(otherObj2, v) {
            PRIVATE_MAP$0.get(otherObj2).x = v;
          }
        }
        """);
  }

  @Test
  public void testPrivateFieldAccess_direct() {
    test(
        """
        class Foo {
          #x = 1;
          getX() {
            return this.#x;
          }
          setX(v) {
            this.#x = v;
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          constructor() {
            const PRIVATE$1 = Object.create(null);
            PRIVATE_MAP$0.set(this, PRIVATE$1);
            PRIVATE$1.x = 1;
          }
          getX() {
            return PRIVATE_MAP$0.get(this).x;
          }
          setX(v) {
            PRIVATE_MAP$0.get(this).x = v;
          }
        }
        """);
  }

  @Test
  public void testPrivateMethodAccess() {
    test(
        """
        class Foo {
          #method(paramX) { return paramX + 1; }
          callMethod(otherObj, argX) {
            return otherObj.#method(argX);
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          method: {
            value: function(paramX) {
              return paramX + 1;
            }
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
          callMethod(otherObj, argX) {
            return PRIVATE_MAP$0.get(otherObj).method.call(otherObj, argX);
          }
        }
        """);
  }

  @Test
  public void testPrivateMethodAccess_direct() {
    test(
        """
        class Foo {
          #method(paramX) { return paramX + 1; }
          callMethod(argX) {
            return this.#method(argX);
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          method: {
            value: function(paramX) {
              return paramX + 1;
            }
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
          callMethod(argX) {
            return PRIVATE_MAP$0.get(this).method.call(this, argX);
          }
        }
        """);
  }

  @Test
  public void testPrivateGetterSetterAccess() {
    test(
        """
        class Foo {
          #propVal = 1;
          get #prop() { return this.#propVal; }
          set #prop(paramV) { this.#propVal = paramV; }
          access(otherObj, argV) {
            otherObj.#prop = otherObj.#prop + argV;
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          prop: {
            get: function() {
              return PRIVATE_MAP$0.get(this.$self).propVal;
            },
            set: function(paramV) {
              PRIVATE_MAP$0.get(this.$self).propVal = paramV;
            }
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE$2.$self = this;
            PRIVATE_MAP$0.set(this, PRIVATE$2);
            PRIVATE$2.propVal = 1;
          }
          access(otherObj, argV) {
            PRIVATE_MAP$0.get(otherObj).prop = PRIVATE_MAP$0.get(otherObj).prop + argV;
          }
        }
        """);
  }

  @Test
  public void testPrivateGetterSetterAccess_direct() {
    test(
        """
        class Foo {
          #propVal = 1;
          get #prop() { return this.#propVal; }
          set #prop(paramV) { this.#propVal = paramV; }
          access(argV) {
            this.#prop = this.#prop + argV;
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          prop: {
            get: function() {
              return PRIVATE_MAP$0.get(this.$self).propVal;
            },
            set: function(paramV) {
              PRIVATE_MAP$0.get(this.$self).propVal = paramV;
            }
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE$2.$self = this;
            PRIVATE_MAP$0.set(this, PRIVATE$2);
            PRIVATE$2.propVal = 1;
          }
          access(argV) {
            PRIVATE_MAP$0.get(this).prop = PRIVATE_MAP$0.get(this).prop + argV;
          }
        }
        """);
  }

  @Test
  public void testPrivateStaticMemberAccess() {
    test(
        """
        class Foo {
          static #staticField = 1;
          static #staticPropVal = 3;
          static #staticMethod() { return 2; }
          static get #staticProp() { return this.#staticPropVal; }
          static set #staticProp(paramV) { this.#staticPropVal = paramV; }
          static access(argV) {
            Foo.#staticProp = Foo.#staticField + Foo.#staticMethod() + Foo.#staticProp + argV;
          }
        }
        """,
        """
        const STATIC_PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          static access(argV) {
            STATIC_PRIVATE_MAP$0.get(Foo).staticProp =
                STATIC_PRIVATE_MAP$0.get(Foo).staticField
                    + STATIC_PRIVATE_MAP$0.get(Foo).staticMethod.call(Foo)
                    + STATIC_PRIVATE_MAP$0.get(Foo).staticProp
                    + argV;
          }
          static STATIC_INIT$2() {
            const PRIVATE$1 = Object.create(null);
            PRIVATE$1.$self = Foo;
            STATIC_PRIVATE_MAP$0.set(Foo, PRIVATE$1);
            PRIVATE$1.staticMethod = function() {
              return 2;
            };
            Object.defineProperty(PRIVATE$1, "staticProp", {
              get: function() {
                return STATIC_PRIVATE_MAP$0.get(this.$self).staticPropVal;
              },
              set: function(paramV) {
                STATIC_PRIVATE_MAP$0.get(this.$self).staticPropVal = paramV;
              }
            });
            PRIVATE$1.staticField = 1;
            PRIVATE$1.staticPropVal = 3;
          }
        }
        Foo.STATIC_INIT$2();
        """);
  }

  @Test
  public void testPrivateStaticMemberAccess_this() {
    test(
        """
        class Foo {
          static #staticField = 1;
          static #staticPropVal = 3;
          static #staticMethod() { return 2; }
          static get #staticProp() { return this.#staticPropVal; }
          static set #staticProp(paramV) { this.#staticPropVal = paramV; }
          static access(argV) {
            this.#staticProp = this.#staticField + this.#staticMethod() + this.#staticProp + argV;
          }
        }
        """,
        """
        const STATIC_PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          static access(argV) {
            STATIC_PRIVATE_MAP$0.get(this).staticProp =
                STATIC_PRIVATE_MAP$0.get(this).staticField
                    + STATIC_PRIVATE_MAP$0.get(this).staticMethod.call(this)
                    + STATIC_PRIVATE_MAP$0.get(this).staticProp
                    + argV;
          }
          static STATIC_INIT$2() {
            const PRIVATE$1 = Object.create(null);
            PRIVATE$1.$self = Foo;
            STATIC_PRIVATE_MAP$0.set(Foo, PRIVATE$1);
            PRIVATE$1.staticMethod = function() {
              return 2;
            };
            Object.defineProperty(PRIVATE$1, "staticProp", {
              get: function() {
                return STATIC_PRIVATE_MAP$0.get(this.$self).staticPropVal;
              },
              set: function(paramV) {
                STATIC_PRIVATE_MAP$0.get(this.$self).staticPropVal = paramV;
              }
            });
            PRIVATE$1.staticField = 1;
            PRIVATE$1.staticPropVal = 3;
          }
        }
        Foo.STATIC_INIT$2();
        """);
  }

  // Verifies optional chaining on an instance private field access (otherObj?.#field)
  // short-circuits before private map lookup.
  @Test
  public void testPrivateOptionalChaining() {
    test(
        """
        class Foo {
          #field = 2;
          access(otherObj) {
            return otherObj?.#field;
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          constructor() {
            const PRIVATE$1 = Object.create(null);
            PRIVATE_MAP$0.set(this, PRIVATE$1);
            PRIVATE$1.field = 2;
          }
          access(otherObj) {
            return otherObj == null ? void 0 : PRIVATE_MAP$0.get(otherObj).field;
          }
        }
        """);
  }

  // Verifies optional chaining on an instance private field using 'this' reference (this?.#field).
  @Test
  public void testPrivateOptionalChaining_direct() {
    test(
        """
        class Foo {
          #field = 2;
          access() {
            return this?.#field;
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          constructor() {
            const PRIVATE$1 = Object.create(null);
            PRIVATE_MAP$0.set(this, PRIVATE$1);
            PRIVATE$1.field = 2;
          }
          access() {
            return this == null ? void 0 : PRIVATE_MAP$0.get(this).field;
          }
        }
        """);
  }

  // Verifies optional chaining when reading an instance private method as a property tear-off
  // (otherObj?.#method).
  @Test
  public void testPrivateMethodAccessWithOptionalChain() {
    test(
        """
        class Foo {
          #method() {}
          access(otherObj) {
            return otherObj?.#method;
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          method: {
            value: function() {}
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
          access(otherObj) {
            return otherObj == null ? void 0 : PRIVATE_MAP$0.get(otherObj).method;
          }
        }
        """);
  }

  // Verifies invoking an instance private method on an optional receiver (otherObj?.#method())
  // short-circuits before private map lookup.
  @Test
  public void testPrivateMethodCallWithOptionalChain() {
    test(
        """
        class Foo {
          #method() {}
          access(otherObj) {
            return otherObj?.#method();
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          method: {
            value: function() {}
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
          access(otherObj) {
            return otherObj == null ? void 0 : PRIVATE_MAP$0.get(otherObj).method.call(otherObj);
          }
        }
        """);
  }

  // Verifies invoking a private method on an optional receiver with side effects
  // (getObj()?.#method()) extracts a temp variable and short-circuits before lookup.
  @Test
  public void testPrivateMethodCallWithOptionalChain_sideEffectingReceiver() {
    test(
        """
        class Foo {
          #method() {}
          access() {
            return getObj()?.#method();
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$2 = Object.create(null, {
          method: {
            value: function() {}
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$3 = Object.create(PRIVATE_PROTO$2);
            PRIVATE_MAP$0.set(this, PRIVATE$3);
          }
          access() {
            let TMP$1;
            return (TMP$1 = getObj(), TMP$1 == null ? void 0 : PRIVATE_MAP$0.get(TMP$1).method.call(TMP$1));
          }
        }
        """);
  }

  // Verifies optional chaining on an instance private field with a side-effecting receiver
  // (getObj()?.#field) extracts a temp variable.
  @Test
  public void testPrivateOptionalChaining_sideEffectingReceiverField() {
    test(
        """
        class Foo {
          #field = 10;
          access() {
            return getObj()?.#field;
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(null);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
            PRIVATE$2.field = 10;
          }
          access() {
            let TMP$1;
            return (TMP$1 = getObj(), TMP$1 == null ? void 0 : PRIVATE_MAP$0.get(TMP$1).field);
          }
        }
        """);
  }

  // Verifies optional chaining on an instance private field or method followed by non-optional
  // trailing segment property accesses (e.g., obj?.#field.prop) wraps the entire segment in
  // short-circuiting check.
  @Test
  public void testPrivateOptionalChaining_trailingSegment() {
    test(
        """
        class Foo {
          #field = { prop: 42 };
          access(obj) {
            return obj?.#field.prop;
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          constructor() {
            const PRIVATE$1 = Object.create(null);
            PRIVATE_MAP$0.set(this, PRIVATE$1);
            PRIVATE$1.field = { prop: 42 };
          }
          access(obj) {
            return obj == null ? void 0 : PRIVATE_MAP$0.get(obj).field.prop;
          }
        }
        """);
  }

  // Verifies optional chaining where the private field access continues an optional chain
  // (e.g., obj?.a.#field) extracts a temp variable and guards PRIVATE_MAP.get against nullish
  // receivers.
  @Test
  public void testPrivateOptionalChaining_continuationChain() {
    test(
        """
        class Foo {
          #field = 10;
          access(obj) {
            return obj?.a.#field;
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(null);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
            PRIVATE$2.field = 10;
          }
          access(obj) {
            let TMP$1;
            return (TMP$1 = obj?.a, TMP$1 == null ? void 0 : PRIVATE_MAP$0.get(TMP$1).field);
          }
        }
        """);
  }

  // Verifies optional chaining on an outer class's private field accessed from inside an inner
  // class.
  @Test
  public void testPrivateOptionalChaining_outerClassFieldFromInner() {
    test(
        """
        class Outer {
          #x = 1;
          createInner() {
            return class Inner {
              readOuter(o) { return o?.#x; }
            };
          }
        }
        """,
        """
        const PRIVATE_MAP$1 = new $jscomp.PrivateMap();
        class Outer {
          constructor() {
            const PRIVATE$2 = Object.create(null);
            PRIVATE_MAP$1.set(this, PRIVATE$2);
            PRIVATE$2.x = 1;
          }
          createInner() {
            const CLASS_DECL$0 = class {
              readOuter(o) {
                return o == null ? void 0 : PRIVATE_MAP$1.get(o).x;
              }
            };
            return CLASS_DECL$0;
          }
        }
        """);
  }

  // Verifies multiple optional chained field and method accesses on instantiated objects.
  @Test
  public void testPrivateOptionalChaining_instantiatedCalls() {
    test(
        """
        class Foo {
          #field = 42;
          #method() { return 100; }
          getField(obj) {
            return obj?.#field;
          }
          getMethod(obj) {
            return obj?.#method;
          }
        }
        const foo = new Foo();
        const validField = foo.getField(foo);
        const nullField = foo.getField(null);
        const validMethod = foo.getMethod(foo);
        const nullMethod = foo.getMethod(null);
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          method: {
            value: function() {
              return 100;
            }
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
            PRIVATE$2.field = 42;
          }
          getField(obj) {
            return obj == null ? void 0 : PRIVATE_MAP$0.get(obj).field;
          }
          getMethod(obj$jscomp$1) {
            return obj$jscomp$1 == null ? void 0 : PRIVATE_MAP$0.get(obj$jscomp$1).method;
          }
        }
        const foo = new Foo();
        const validField = foo.getField(foo);
        const nullField = foo.getField(null);
        const validMethod = foo.getMethod(foo);
        const nullMethod = foo.getMethod(null);
        """);
  }

  // Verifies optional chaining on a static private field access (cls?.#staticField) short-circuits
  // on nullish class receiver.
  @Test
  public void testPrivateStaticFieldWithOptionalChain() {
    test(
        """
        class Foo {
          static #staticField = 10;
          static getStaticField(cls) {
            return cls?.#staticField;
          }
        }
        """,
        """
        const STATIC_PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          static getStaticField(cls) {
            return cls == null ? void 0 : STATIC_PRIVATE_MAP$0.get(cls).staticField;
          }
          static STATIC_INIT$2() {
            const PRIVATE$1 = Object.create(null);
            STATIC_PRIVATE_MAP$0.set(Foo, PRIVATE$1);
            PRIVATE$1.staticField = 10;
          }
        }
        Foo.STATIC_INIT$2();
        """);
  }

  // Verifies optional chaining when invoking a static private method on an optional class receiver
  // (cls?.#staticMethod()).
  @Test
  public void testPrivateStaticMethodWithOptionalChain() {
    test(
        """
        class Foo {
          static #staticMethod() { return 20; }
          static callStaticMethod(cls) {
            return cls?.#staticMethod();
          }
        }
        """,
        """
        const STATIC_PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          static callStaticMethod(cls) {
            return cls == null ? void 0 : STATIC_PRIVATE_MAP$0.get(cls).staticMethod.call(cls);
          }
          static STATIC_INIT$2() {
            const PRIVATE$1 = Object.create(null);
            STATIC_PRIVATE_MAP$0.set(Foo, PRIVATE$1);
            PRIVATE$1.staticMethod = function() {
              return 20;
            };
          }
        }
        Foo.STATIC_INIT$2();
        """);
  }

  // Verifies optional chaining on an instance private getter access (otherObj?.#getter).
  @Test
  public void testPrivateGetterWithOptionalChain() {
    test(
        """
        class Foo {
          get #getter() { return this; }
          accessGetter(obj) {
            return obj?.#getter;
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          getter: {
            get: function() { return this.$self; }
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE$2.$self = this;
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
          accessGetter(obj) {
            return obj == null ? void 0 : PRIVATE_MAP$0.get(obj).getter;
          }
        }
        """);
  }

  // Verifies optional chaining on a static private getter access (cls?.#staticGetter).
  @Test
  public void testPrivateStaticGetterWithOptionalChain() {
    test(
        """
        class Foo {
          static get #staticGetter() { return this; }
          static accessStaticGetter(cls) {
            return cls?.#staticGetter;
          }
        }
        """,
        """
        const STATIC_PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          static accessStaticGetter(cls) {
            return cls == null ? void 0 : STATIC_PRIVATE_MAP$0.get(cls).staticGetter;
          }
          static STATIC_INIT$2() {
            const PRIVATE$1 = Object.create(null);
            PRIVATE$1.$self = Foo;
            STATIC_PRIVATE_MAP$0.set(Foo, PRIVATE$1);
            Object.defineProperty(PRIVATE$1, "staticGetter", {
              get: function() { return this.$self; }
            });
          }
        }
        Foo.STATIC_INIT$2();
        """);
  }

  // Verifies optional call invocation on a non-optional static private method
  // (Foo.#staticMethod?.(a)).
  @Test
  public void testPrivateStaticMethodWithOptionalMethodCall() {
    test(
        """
        class Foo {
          static #staticMethod(a) { return a; }
          static call(a) {
            return Foo.#staticMethod?.(a);
          }
        }
        """,
        """
        const STATIC_PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          static call(a$jscomp$1) {
            return STATIC_PRIVATE_MAP$0.get(Foo).staticMethod?.call(Foo, a$jscomp$1);
          }
          static STATIC_INIT$2() {
            const PRIVATE$1 = Object.create(null);
            STATIC_PRIVATE_MAP$0.set(Foo, PRIVATE$1);
            PRIVATE$1.staticMethod = function(a) {
              return a;
            };
          }
        }
        Foo.STATIC_INIT$2();
        """);
  }

  // Verifies both optional receiver and optional invocation on a static private method
  // (Foo?.#staticMethod?.(a)).
  @Test
  public void testPrivateStaticMethodWithBothReceiverAndMethodOptional() {
    test(
        """
        class Foo {
          static #staticMethod(a) { return a; }
          static call(a) {
            return Foo?.#staticMethod?.(a);
          }
        }
        """,
        """
        const STATIC_PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          static call(a$jscomp$1) {
            return Foo == null ? void 0 : STATIC_PRIVATE_MAP$0.get(Foo).staticMethod?.call(Foo, a$jscomp$1);
          }
          static STATIC_INIT$2() {
            const PRIVATE$1 = Object.create(null);
            STATIC_PRIVATE_MAP$0.set(Foo, PRIVATE$1);
            PRIVATE$1.staticMethod = function(a) {
              return a;
            };
          }
        }
        Foo.STATIC_INIT$2();
        """);
  }

  // Verifies optional call invocation on a non-optional instance private method (obj.#method?.(a)).
  @Test
  public void testPrivateOptionalChaining_optionalMethodCall() {
    test(
        """
        class Foo {
          #method(a) { return a; }
          call(obj, a) {
            return obj.#method?.(a);
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          method: {
            value: function(a) { return a; }
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
          call(obj, a$jscomp$1) {
            return PRIVATE_MAP$0.get(obj).method?.call(obj, a$jscomp$1);
          }
        }
        """);
  }

  // Verifies both optional receiver and optional invocation on an instance private method
  // (obj?.#method?.(a)).
  @Test
  public void testPrivateOptionalChaining_bothReceiverAndMethodOptional() {
    test(
        """
        class Foo {
          #method(a) { return a; }
          call(obj, a) {
            return obj?.#method?.(a);
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          method: {
            value: function(a) { return a; }
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
          call(obj, a$jscomp$1) {
            return obj == null ? void 0 : PRIVATE_MAP$0.get(obj).method?.call(obj, a$jscomp$1);
          }
        }
        """);
  }

  // Verifies optional invocation on a private field holding a function (this.#field?.(a)).
  @Test
  public void testPrivateOptionalChaining_fieldHoldingFunction() {
    test(
        """
        class Foo {
          #field = (a) => a;
          call(a) {
            return this.#field?.(a);
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          constructor() {
            const PRIVATE$1 = Object.create(null);
            PRIVATE_MAP$0.set(this, PRIVATE$1);
            PRIVATE$1.field = (a) => {
              return a;
            };
          }
          call(a$jscomp$1) {
            return PRIVATE_MAP$0.get(this).field?.call(this, a$jscomp$1);
          }
        }
        """);
  }

  // Verifies optional invocation on a static private field holding an arrow function
  // (Foo.#field?.(a)).
  @Test
  public void testPrivateOptionalChaining_staticFieldHoldingArrowFunction() {
    test(
        """
        class Foo {
          static #field = (a) => a;
          static call(a) {
            return Foo.#field?.(a);
          }
        }
        """,
        """
        const STATIC_PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          static call(a$jscomp$1) {
            return STATIC_PRIVATE_MAP$0.get(Foo).field?.call(Foo, a$jscomp$1);
          }
          static STATIC_INIT$2() {
            const PRIVATE$1 = Object.create(null);
            STATIC_PRIVATE_MAP$0.set(Foo, PRIVATE$1);
            PRIVATE$1.field = (a) => {
              return a;
            };
          }
        }
        Foo.STATIC_INIT$2();
        """);
  }

  // Verifies optional invocation on an instance private field holding a function expression
  // referencing this.
  @Test
  public void testPrivateOptionalChaining_fieldHoldingFunctionExpressionWithThis() {
    test(
        """
        class Foo {
          x = 1;
          #field = function() { return this.x; };
          call() {
            return this.#field?.();
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          constructor() {
            const PRIVATE$1 = Object.create(null);
            PRIVATE_MAP$0.set(this, PRIVATE$1);
            this.x = 1;
            PRIVATE$1.field = function() {
              return this.x;
            };
          }
          call() {
            return PRIVATE_MAP$0.get(this).field?.call(this);
          }
        }
        """);
  }

  // Verifies optional invocation on a static private field holding a function expression
  // referencing this.
  @Test
  public void testPrivateOptionalChaining_staticFieldHoldingFunctionExpressionWithThis() {
    test(
        """
        class Foo {
          static x = 1;
          static #field = function() { return this.x; };
          static call() {
            return Foo.#field?.();
          }
        }
        """,
        """
        const STATIC_PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          static call() {
            return STATIC_PRIVATE_MAP$0.get(Foo).field?.call(Foo);
          }
          static STATIC_INIT$2() {
            const PRIVATE$1 = Object.create(null);
            STATIC_PRIVATE_MAP$0.set(Foo, PRIVATE$1);
            Foo.x = 1;
            PRIVATE$1.field = function() {
              return this.x;
            };
          }
        }
        Foo.STATIC_INIT$2();
        """);
  }

  // Verifies optional chained property read followed by an optional property access
  // (obj?.#field?.prop).
  @Test
  public void testPrivateOptionalChaining_chainedAccess() {
    test(
        """
        class Foo {
          #field = { prop: 123 };
          getProp(obj) {
            return obj?.#field?.prop;
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          constructor() {
            const PRIVATE$1 = Object.create(null);
            PRIVATE_MAP$0.set(this, PRIVATE$1);
            PRIVATE$1.field = { prop: 123 };
          }
          getProp(obj) {
            return (obj == null ? void 0 : PRIVATE_MAP$0.get(obj).field)?.prop;
          }
        }
        """);
  }

  // Verifies optional chained method invocation on a property receiver (obj.foo?.#method()).
  @Test
  public void testPrivateOptionalChaining_chainedMethodCall() {
    test(
        """
        class Foo {
          #method() {}
          callMethod(obj) {
            return obj.foo?.#method();
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$2 = Object.create(null, {
          method: {
            value: function() {}
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$3 = Object.create(PRIVATE_PROTO$2);
            PRIVATE_MAP$0.set(this, PRIVATE$3);
          }
          callMethod(obj) {
            let TMP$1;
            return (TMP$1 = obj.foo, TMP$1 == null ? void 0 : PRIVATE_MAP$0.get(TMP$1).method.call(TMP$1));
          }
        }
        """);
  }

  @Test
  public void testPrivateInheritanceAndSuper() {
    test(
        """
        class Base {
          constructor() {
            this.baseProp = 1;
          }
        }
        class Sub extends Base {
          #field = 2;
          #method() { return 3; }
          constructor() {
            super();
            this.#field = this.baseProp + this.#method();
          }
        }
        """,
        """
        class Base {
          constructor() {
            this.baseProp = 1;
          }
        }
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          method: {
            value: function() {
              return 3;
            }
          }
        });
        class Sub extends Base {
          constructor() {
            super();
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
            PRIVATE$2.field = 2;
            PRIVATE_MAP$0.get(this).field = this.baseProp + PRIVATE_MAP$0.get(this).method.call(this);
          }
        }
        """);
  }

  @Test
  public void testPrivateInheritanceWithSuperclassHavingPrivates() {
    test(
        """
        class Base {
          #baseField = 1;
          #baseMethod() { return this.#baseField; }
          getBase() { return this.#baseMethod(); }
        }
        class Sub extends Base {
          #subField = 2;
          #subMethod() { return this.#subField; }
          getSub() { return this.#subMethod(); }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          baseMethod: {
            value: function() {
              return PRIVATE_MAP$0.get(this).baseField;
            }
          }
        });
        class Base {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
            PRIVATE$2.baseField = 1;
          }
          getBase() {
            return PRIVATE_MAP$0.get(this).baseMethod.call(this);
          }
        }
        const PRIVATE_MAP$3 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$4 = Object.create(null, {
          subMethod: {
            value: function() {
              return PRIVATE_MAP$3.get(this).subField;
            }
          }
        });
        class Sub extends Base {
          constructor() {
            super(...arguments);
            const PRIVATE$5 = Object.create(PRIVATE_PROTO$4);
            PRIVATE_MAP$3.set(this, PRIVATE$5);
            PRIVATE$5.subField = 2;
          }
          getSub() {
            return PRIVATE_MAP$3.get(this).subMethod.call(this);
          }
        }
        """);
  }

  @Test
  public void testPrivateMethodWithSuperReference() {
    test(
        """
        class Base {
          baseProp = 5;
          baseMethod() { return 10; }
        }
        class Sub extends Base {
          #privateMethod() {
            return super.baseMethod() + super.baseProp;
          }
          callPrivate() {
            return this.#privateMethod();
          }
        }
        """,
        """
        class Base {
          constructor() {
            this.baseProp = 5;
          }
          baseMethod() { return 10; }
        }
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          privateMethod: {
            value: function() {
              return Base.prototype.baseMethod.call(this) + Reflect.get(Base.prototype, JSCompiler_renameProperty("baseProp", Base), this);
            }
          }
        });
        class Sub extends Base {
          constructor() {
            super(...arguments);
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
          callPrivate() {
            return PRIVATE_MAP$0.get(this).privateMethod.call(this);
          }
        }
        """);
  }

  @Test
  public void testPrivateMethodValueRead() {
    test(
        """
        class Foo {
          #method() {}
          getMethod() {
            return this.#method;
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          method: {
            value: function() {}
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
          getMethod() {
            return PRIVATE_MAP$0.get(this).method;
          }
        }
        """);
  }

  // TODO(b/236744850): Consider optimizing intra-instance private method calls from inside a
  // private
  // method to direct descriptor invocations (e.g. `this.a.call(this.$self)`) in the future.
  @Test
  public void testPrivateMethodCallingPrivateMethod() {
    test(
        """
        class Foo {
          #a() { return 1; }
          #b() { return this.#a() + 2; }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          a: {
            value: function() {
              return 1;
            }
          },
          b: {
            value: function() {
              return PRIVATE_MAP$0.get(this).a.call(this) + 2;
            }
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
        }
        """);
  }

  @Test
  public void testPrivateMethodCallingPublicMethod() {
    test(
        """
        class Foo {
          a() { return 1; }
          #b() { return this.a() + 2; }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          b: {
            value: function() {
              return this.a() + 2;
            }
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
          a() { return 1; }
        }
        """);
  }

  // Verifies that a nested class inside a private method containing 'super' references (e.g.
  // super.m()) references the nested class's own superclass without confusing the outer class's
  // private method traversal.
  @Test
  public void testPrivateMethodWithNestedClassUsingSuper() {
    test(
        """
        class A {
          m() {}
        }
        class B {
          #priv() {
            return class C extends A {
              m() {
                super.m();
              }
            }
          }
        }
        """,
        """
        class A {
          m() {}
        }
        const PRIVATE_MAP$1 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$2 = Object.create(null, {
          priv: {
            value: function() {
              const CLASS_DECL$0 = class extends A {
                m() {
                  super.m();
                }
              };
              return CLASS_DECL$0;
            }
          }
        });
        class B {
          constructor() {
            const PRIVATE$3 = Object.create(PRIVATE_PROTO$2);
            PRIVATE_MAP$1.set(this, PRIVATE$3);
          }
        }
        """);
  }

  // TODO(b/236744850): Consider emitting dynamic prototype chain lookups
  // (`Object.getPrototypeOf(Child.prototype).foo.call(this)`) rather than static prototype
  // references (`Base.prototype.foo.call(this)`) if dynamic prototype mutation must be supported.
  @Test
  public void testPrivateMethodWithSuper() {
    test(
        """
        class Base {
          foo() { return 10; }
        }
        class Child extends Base {
          #privateMethod() {
            return super.foo() + 1;
          }
          callPrivate() {
            return this.#privateMethod();
          }
        }
        """,
        """
        class Base {
          foo() { return 10; }
        }
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          privateMethod: {
            value: function() {
              return Base.prototype.foo.call(this) + 1;
            }
          }
        });
        class Child extends Base {
          constructor() {
            super(...arguments);
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
          callPrivate() {
            return PRIVATE_MAP$0.get(this).privateMethod.call(this);
          }
        }
        """);
  }

  @Test
  public void testPrivateStaticMethodWithSuper() {
    test(
        """
        class Base {
          static staticFoo() { return 20; }
        }
        class Child extends Base {
          static #staticPrivateMethod() {
            return super.staticFoo() + 5;
          }
          static callStaticPrivate() {
            return Child.#staticPrivateMethod();
          }
        }
        """,
        """
        class Base {
          static staticFoo() { return 20; }
        }
        const STATIC_PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Child extends Base {
          static callStaticPrivate() {
            return STATIC_PRIVATE_MAP$0.get(Child).staticPrivateMethod.call(Child);
          }
          static STATIC_INIT$2() {
            const PRIVATE$1 = Object.create(null);
            STATIC_PRIVATE_MAP$0.set(Child, PRIVATE$1);
            PRIVATE$1.staticPrivateMethod = function() {
              return Base.staticFoo() + 5;
            };
          }
        }
        Child.STATIC_INIT$2();
        """);
  }

  // Verifies that a complex or side-effecting receiver expression (e.g. getObj().#method()) is
  // evaluated exactly once into a temporary variable to prevent duplicate side effects.
  @Test
  public void testPrivateMethodSideEffectingReceiver() {
    test(
        """
        class Foo {
          #method() {}
          callMethod(getObj) {
            return getObj().#method();
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$2 = Object.create(null, {
          method: {
            value: function() {}
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$3 = Object.create(PRIVATE_PROTO$2);
            PRIVATE_MAP$0.set(this, PRIVATE$3);
          }
          callMethod(getObj) {
            let $jscomp$tmp$m1146332801$1;
            return ($jscomp$tmp$m1146332801$1 = getObj(), PRIVATE_MAP$0.get($jscomp$tmp$m1146332801$1).method.call($jscomp$tmp$m1146332801$1));
          }
        }
        """);
  }

  // Verifies that a property access receiver (e.g. holder['prop'].#method()) is evaluated exactly
  // once into a temporary variable to prevent duplicate evaluation in case the property access
  // triggers getter side effects.
  @Test
  public void testPrivateMethodPropertyAccessReceiver() {
    test(
        """
        class Foo {
          #method() {}
          static callOnProp(holder) {
            return holder['prop'].#method();
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$2 = Object.create(null, {
          method: {
            value: function() {}
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$3 = Object.create(PRIVATE_PROTO$2);
            PRIVATE_MAP$0.set(this, PRIVATE$3);
          }
          static callOnProp(holder) {
            let $jscomp$tmp$m1146332801$1;
            return ($jscomp$tmp$m1146332801$1 = holder['prop'],
                PRIVATE_MAP$0.get($jscomp$tmp$m1146332801$1).method.call($jscomp$tmp$m1146332801$1));
          }
        }
        """);
  }

  @Test
  public void testPrivateMethodAsyncAndGenerator() {
    test(
        """
        class Foo {
          async #asyncMethod() {}
          *#genMethod() {}
          async *#asyncGenMethod() {}
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          asyncMethod: {
            value: async function() {}
          },
          genMethod: {
            value: function*() {}
          },
          asyncGenMethod: {
            value: async function*() {}
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
        }
        """);
  }

  // Verifies that calling a private method on another instance of the same class (e.g.
  // `other.#method()`) is valid per ES2022 class-scoped private member semantics and transpiles to
  // `PRIVATE_MAP.get(other).method.call(other)`.
  @Test
  public void testPrivateMethodCalledOnOtherInstance() {
    test(
        """
        class Foo {
          #method() { return 42; }
          callOther(other) {
            return other.#method();
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          method: {
            value: function() {
              return 42;
            }
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
          callOther(other) {
            return PRIVATE_MAP$0.get(other).method.call(other);
          }
        }
        """);
  }

  // Verifies the combination of method descriptors and instance field initializations in a single
  // class.
  @Test
  public void testPrivateMethodMultiplePrivateMethodsAndFields() {
    test(
        """
        class Foo {
          #field = 10;
          #getValue() { return this.#field; }
          publicMethod() {
            return this.#getValue();
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          getValue: {
            value: function() {
              return PRIVATE_MAP$0.get(this).field;
            }
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
            PRIVATE$2.field = 10;
          }
          publicMethod() {
            return PRIVATE_MAP$0.get(this).getValue.call(this);
          }
        }
        """);
  }

  @Test
  public void testPrivateMethodWriteUnsupported() {
    testError(
        """
        class Foo {
          #method() {}
          write() {
            this.#method = 1;
          }
        }
        """,
        Es6NormalizeClasses.ILLEGAL_PRIVATE_MEMBER_ASSIGNMENT);
  }

  @Test
  public void testPrivateAccessorCompoundAssignment() {
    test(
        """
        class Foo {
          get #prop() { return 1; }
          set #prop(v) {}
          update() {
            this.#prop += 5;
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          prop: {
            get: function() {
              return 1;
            },
            set: function(v) {}
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
          update() {
            PRIVATE_MAP$0.get(this).prop += 5;
          }
        }
        """);
  }

  @Test
  public void testPrivateAccessorIncDec() {
    test(
        """
        class Foo {
          get #prop() { return 1; }
          set #prop(v) {}
          update() {
            ++this.#prop;
            this.#prop++;
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          prop: {
            get: function() {
              return 1;
            },
            set: function(v) {}
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
          update() {
            ++PRIVATE_MAP$0.get(this).prop;
            PRIVATE_MAP$0.get(this).prop++;
          }
        }
        """);
  }

  @Test
  public void testPrivateAccessorCompoundAssignmentSideEffectingReceiver() {
    test(
        """
        class Foo {
          get #prop() { return 1; }
          set #prop(v) {}
          update() {
            getObj().#prop += 5;
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          prop: {
            get: function() {
              return 1;
            },
            set: function(v) {}
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
          update() {
            PRIVATE_MAP$0.get(getObj()).prop += 5;
          }
        }
        """);
  }

  @Test
  public void testPrivateMethodExplicitCall() {
    ignoreWarnings(TypeCheck.INEXISTENT_PROPERTY);
    test(
        """
        class Foo {
          #method() { return this.val; }
          test(other) {
            return this.#method.call(other);
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          method: {
            value: function() {
              return this.val;
            }
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
          test(other) {
            return PRIVATE_MAP$0.get(this).method.call(other);
          }
        }
        """);
  }

  @Test
  public void testPrivateMethodWithSuperGetterAndSetter() {
    test(
        """
        class S {
          get g() { return 'g S'; }
          set s(v) {}
        }
        class C extends S {
          get g() { return 'g C'; }
          #checkG(v) {
            super.s = v;
            return this.g + ' ' + super.g;
          }
        }
        """,
        """
        class S {
          get g() { return 'g S'; }
          set s(v) {}
        }
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          checkG: {
            value: function(v$jscomp$1) {
              Reflect.set(S.prototype, JSCompiler_renameProperty("s", S), v$jscomp$1, this);
              return this.g + " " + Reflect.get(S.prototype, JSCompiler_renameProperty("g", S), this);
            }
          }
        });
        class C extends S {
          constructor() {
            super(...arguments);
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
          get g() { return 'g C'; }
        }
        """);
  }

  @Test
  public void testPrivateGetterWithSuper() {
    test(
        """
        class Base {
          get baseProp() { return 'base'; }
        }
        class Child extends Base {
          get #foo() {
            return super.baseProp + ' child';
          }
          getFoo() {
            return this.#foo;
          }
        }
        """,
        """
        class Base {
          get baseProp() { return 'base'; }
        }
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          foo: {
            get: function() {
              return Reflect.get(Base.prototype, JSCompiler_renameProperty("baseProp", Base), this.$self) + " child";
            }
          }
        });
        class Child extends Base {
          constructor() {
            super(...arguments);
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE$2.$self = this;
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
          getFoo() {
            return PRIVATE_MAP$0.get(this).foo;
          }
        }
        """);
  }

  @Test
  public void testPrivateSetterWithSuperProperty() {
    test(
        """
        class Base {
          set baseProp(v) {}
        }
        class Child extends Base {
          set #foo(v) {
            super.baseProp = v;
          }
          setFoo(v) {
            this.#foo = v;
          }
        }
        """,
        """
        class Base {
          set baseProp(v) {}
        }
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          foo: {
            set: function(v$jscomp$1) {
              Reflect.set(Base.prototype, JSCompiler_renameProperty("baseProp", Base), v$jscomp$1, this.$self);
            }
          }
        });
        class Child extends Base {
          constructor() {
            super(...arguments);
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE$2.$self = this;
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
          setFoo(v$jscomp$2) {
            PRIVATE_MAP$0.get(this).foo = v$jscomp$2;
          }
        }
        """);
  }

  @Test
  public void testPrivateMethodSuperInClassExpression() {
    test(
        """
        class C extends (class { m() { return 'x'; } }) {
          m() { return 'y'; }
          #pm() { return super.m(); }
        }
        """,
        """
        const CLASS_EXTENDS$0 = (() => {
          const CLASS_DECL$1 = class {
            m() {
              return "x";
            }
          };
          return CLASS_DECL$1;
        })();
        const PRIVATE_MAP$2 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$3 = Object.create(null, {
          pm: {
            value: function() {
              return CLASS_EXTENDS$0.prototype.m.call(this);
            }
          }
        });
        class C extends CLASS_EXTENDS$0 {
          constructor() {
            super(...arguments);
            const PRIVATE$4 = Object.create(PRIVATE_PROTO$3);
            PRIVATE_MAP$2.set(this, PRIVATE$4);
          }
          m() {
            return "y";
          }
        }
        """);
  }

  @Test
  public void testPrivateSetterWithSuper() {
    test(
        """
        class Base {
          setVal(v) {}
        }
        class Sub extends Base {
          set #prop(v) {
            super.setVal(v);
          }
          update(v) {
            this.#prop = v;
          }
        }
        """,
        """
        class Base {
          setVal(v) {}
        }
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          prop: {
            set: function(v$jscomp$1) {
              Base.prototype.setVal.call(this.$self, v$jscomp$1);
            }
          }
        });
        class Sub extends Base {
          constructor() {
            super(...arguments);
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE$2.$self = this;
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
          update(v$jscomp$2) {
            PRIVATE_MAP$0.get(this).prop = v$jscomp$2;
          }
        }
        """);
  }

  @Test
  public void testPrivateFieldWithSuperArrowFunction() {
    test(
        """
        class Sup {
          m() {}
        }
        class Sub extends Sup {
          #x = () => super.m();
        }
        """,
        """
        class Sup {
          m() {}
        }
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Sub extends Sup {
          constructor() {
            super(...arguments);
            const PRIVATE$1 = Object.create(null);
            PRIVATE_MAP$0.set(this, PRIVATE$1);
            PRIVATE$1.x = () => {
              return super.m();
            };
          }
        }
        """);
  }

  @Test
  public void testPrivateNestedClassScoping() {
    test(
        """
        class Outer {
          #x = 1;
          createInner() {
            return class Inner {
              readOuter(o) { return o.#x; }
            };
          }
        }
        """,
        """
        const PRIVATE_MAP$1 = new $jscomp.PrivateMap();
        class Outer {
          constructor() {
            const PRIVATE$2 = Object.create(null);
            PRIVATE_MAP$1.set(this, PRIVATE$2);
            PRIVATE$2.x = 1;
          }
          createInner() {
            const CLASS_DECL$0 = class {
              readOuter(o) {
                return PRIVATE_MAP$1.get(o).x;
              }
            };
            return CLASS_DECL$0;
          }
        }
        """);
  }

  @Test
  public void testPrivateMemberInConstructorArrowFunction() {
    test(
        """
        class Foo {
          #field = 10;
          #method() { return 20; }
          constructor() {
            const getVal = () => this.#field + this.#method();
            this.val = getVal();
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          method: {
            value: function() {
              return 20;
            }
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
            PRIVATE$2.field = 10;
            const getVal = () => {
              return PRIVATE_MAP$0.get(this).field + PRIVATE_MAP$0.get(this).method.call(this);
            };
            this.val = getVal();
          }
        }
        """);
  }

  @Test
  public void testPrivateMethodCalledInsideArrowFunction() {
    test(
        """
        class Foo {
          #method() { return 42; }
          getArrow() {
            return () => this.#method();
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          method: {
            value: function() {
              return 42;
            }
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
          getArrow() {
            return () => {
              return PRIVATE_MAP$0.get(this).method.call(this);
            };
          }
        }
        """);
  }

  @Test
  public void testNestedClassAccessingOuterPrivateMethod() {
    test(
        """
        class Outer {
          #foo() { return 1; }
          method() {
            class Inner {
              #bar() { return 2; }
              use(outer) {
                return outer.#foo() + this.#bar();
              }
            }
            return new Inner();
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$4 = Object.create(null, {
          foo: {
            value: function() {
              return 1;
            }
          }
        });
        class Outer {
          constructor() {
            const PRIVATE$5 = Object.create(PRIVATE_PROTO$4);
            PRIVATE_MAP$0.set(this, PRIVATE$5);
          }
          method() {
            const PRIVATE_MAP$1 = new $jscomp.PrivateMap();
            const PRIVATE_PROTO$2 = Object.create(null, {
              bar: {
                value: function() {
                  return 2;
                }
              }
            });
            class Inner {
              constructor() {
                const PRIVATE$3 = Object.create(PRIVATE_PROTO$2);
                PRIVATE_MAP$1.set(this, PRIVATE$3);
              }
              use(outer) {
                return PRIVATE_MAP$0.get(outer).foo.call(outer) + PRIVATE_MAP$1.get(this).bar.call(this);
              }
            }
            return new Inner();
          }
        }
        """);
  }

  @Test
  public void testNestedClassShadowingPrivateMemberName() {
    test(
        """
        class Outer {
          #foo() { return 1; }
          method() {
            class Inner {
              #foo() { return 2; }
              use(outer) {
                return outer.#foo() + this.#foo();
              }
            }
            return new Inner();
          }
        }
        """,
        """
        const PRIVATE_MAP$3 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$4 = Object.create(null, {
          foo: {
            value: function() {
              return 1;
            }
          }
        });
        class Outer {
          constructor() {
            const PRIVATE$5 = Object.create(PRIVATE_PROTO$4);
            PRIVATE_MAP$3.set(this, PRIVATE$5);
          }
          method() {
            const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
            const PRIVATE_PROTO$1 = Object.create(null, {
              foo: {
                value: function() {
                  return 2;
                }
              }
            });
            class Inner {
              constructor() {
                const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
                PRIVATE_MAP$0.set(this, PRIVATE$2);
              }
              use(outer) {
                return PRIVATE_MAP$0.get(outer).foo.call(outer) + PRIVATE_MAP$0.get(this).foo.call(this);
              }
            }
            return new Inner();
          }
        }
        """);
  }

  @Test
  public void testPrivateStaticMemberAccessInStaticBlock() {
    test(
        """
        class Foo {
          static #x = 42;
          static #foo() { return this.#x; }
          static y;
          static {
            this.y = this.#foo();
          }
        }
        """,
        """
        const STATIC_PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          static STATIC_INIT$2() {
            const PRIVATE$1 = Object.create(null);
            STATIC_PRIVATE_MAP$0.set(Foo, PRIVATE$1);
            PRIVATE$1.foo = function() {
              return STATIC_PRIVATE_MAP$0.get(this).x;
            };
            PRIVATE$1.x = 42;
            Foo.y = void 0;
            {
              Foo.y = STATIC_PRIVATE_MAP$0.get(Foo).foo.call(Foo);
            }
          }
        }
        Foo.STATIC_INIT$2();
        """);
  }

  @Test
  public void testNestedClassInsideStaticBlock() {
    test(
        """
        class Outer {
          #foo() { return 1; }
          static innerInstance;
          static {
            class Inner {
              #bar() { return 2; }
              use(outer) {
                return outer.#foo() + this.#bar();
              }
            }
            Outer.innerInstance = new Inner();
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$4 = Object.create(null, {
          foo: {
            value: function() {
              return 1;
            }
          }
        });
        class Outer {
          constructor() {
            const PRIVATE$5 = Object.create(PRIVATE_PROTO$4);
            PRIVATE_MAP$0.set(this, PRIVATE$5);
          }
        }
        Outer.innerInstance = void 0;
        {
          const PRIVATE_MAP$1 = new $jscomp.PrivateMap();
          const PRIVATE_PROTO$2 = Object.create(null, {
            bar: {
              value: function() {
                return 2;
              }
            }
          });
          class Inner {
            constructor() {
              const PRIVATE$3 = Object.create(PRIVATE_PROTO$2);
              PRIVATE_MAP$1.set(this, PRIVATE$3);
            }
            use(outer) {
              return PRIVATE_MAP$0.get(outer).foo.call(outer) + PRIVATE_MAP$1.get(this).bar.call(this);
            }
          }
          Outer.innerInstance = new Inner();
        }
        """);
  }

  @Test
  public void testClassStaticBlock_superRef_onClassWithNameSpace() {
    test(
        """
        const ns = {};
        ns.B = class {
          static y = 3;
        };
        class C extends ns.B {
          static {
            let x = super.y;
          }
        }
        """,
        """
        const ns = {};
        ns.B = class {};
        ns.B.y = 3;
        class C extends ns.B {}
        {
          let x = ns.B.y;
        }
        """);
  }

  @Test
  public void testClassStaticBlock_thisRef() {
    var src =
        """
        class C {
          static {
            C.x = 2
            const y = this.x
          }
        }
        """;

    test(
        src,
        """
        class C {}
        {
          C.x = 2;
          const y = C.x;
        }
        """);

    test(
        withOptions().useStaticInheritance(),
        src,
        """
        class C {}
        {
          C.x = 2;
          const y = C.x;
        }
        """);
  }

  @Test
  public void testClassStaticBlock_varInStaticBlock() {
    test(
        """
        var z = 1
        class C {
          static {
            let x = 2
            var z = 3;
          }
        }
        """,
        """
        var z = 1;
        class C {}
        {
          let x = 2;
          var z$jscomp$1 = 3;
        }
        """);
  }

  @Test
  public void testClassStaticBlock_classExpression() {
    test(
        """
        let C = class {
          static prop = 5;
        };
        let D = class extends C {
          static {
            this.prop = 10;
          }
        };
        """,
        """
        let C = class {};
        C.prop = 5;
        let D = class extends C {};
        {
          D.prop = 10;
        }
        """);
  }

  @Test
  public void testClassStaticBlock_multipleClassesInLet() {
    test(
        """
        let C = class {
          static prop = 5;
        },
        D = class extends C {
          static {
            this.prop = 10;
          }
        }
        """,
        """
        let C = class {};
        C.prop = 5;
        let D = class extends C {};
        {
          D.prop = 10;
        }
        """);
  }

  @Test
  public void testClassStaticBlock_fieldAndBlock() {
    test(
        """
        class C {
          static f;
          static {
            C.f = 1;
          }
        }
        """,
        """
        class C {}
        C.f = void 0;
        {
          C.f = 1;
        }
        """);
  }

  @Test
  public void testMultipleStaticBlocks() {
    test(
        """
        var z = 1
        /** @unrestricted */
        class C {
          static x = 2;
          static {
            z = z + this.x;
          }
          static [z] = 3;
          static w = 5;
          static {
            z = z + this.w;
          }
        }
        """,
        """
        var z = 1;
        var COMP_FIELD$0 = z;
        class C {}
        C.x = 2;
        {
          z = z + C.x;
        }
        C[COMP_FIELD$0] = 3;
        C.w = 5;
        {
          z = z + C.w;
        }
        """);
  }

  @Test
  public void testThisInNonStaticPublicField() {
    var src =
        """
        class A {
          /** @suppress {partialAlias} */
          b = 'word';
          c = this.b;
        }
        """;
    test(
        src,
        """
        class A {
          constructor() {
            /** @suppress {partialAlias} */
            this.b = 'word';
            this.c = this.b;
          }
        }
        """);
    testSame(withOptions().useEs2022LanguageOut(), src);

    test(
        """
        let obj = { bar() { return 9; } };
        class D {
          e = obj;
          f = this.e.bar() * 4;
        }
        """,
        """
        let obj = { bar() { return 9; } };
        class D {
          constructor() {
            this.e = obj;
            this.f = this.e.bar() * 4;
          }
        }
        """);

    test(
        """
        class Foo {
          y = 'apple';
          x = () => { return this.y + ' and banana'; };
        }
        """,
        """
        class Foo {
          constructor() {
            this.y = 'apple';
            this.x = () => { return this.y + ' and banana'; };
          }
        }
        """);

    test(
        """
        class Bar {
          x = () => { this.method(); };
          method() {}
        }
        """,
        """
        class Bar {
          constructor() {
            this.x = () => { this.method(); };
          }
          method() {}
        }
        """);
  }

  @Test
  public void testSuperInNonStaticPublicField() {
    var src =
        """
        class Foo {
          x() {
            return 3;
          }
        }
        class Bar extends Foo {
          y = 1 + super.x();
        }
        """;
    test(
        src,
        """
        class Foo {
          x() {
            return 3;
          }
        }
        class Bar extends Foo {
          constructor() {
            super(...arguments);
            this.y = 1 + super.x();
          }
        }
        """);
    testSame(withOptions().useEs2022LanguageOut(), src);
  }

  @Test
  public void testThisInStaticField_otherFieldRef() {
    var src =
        """
        class C {
          static x = 1;
          static y = this.x + 1;
        }
        """;

    test(
        src,
        """
        class C {}
        C.x = 1;
        C.y = C.x + 1;
        """);

    test(
        withOptions().useStaticInheritance(),
        src,
        """
        class C {}
        C.x = 1;
        C.y = C.x + 1;
        """);

    test(
        withOptions().useEs2022LanguageOut(),
        src,
        """
        class C {
          static x;
          static y;
        }
        C.x = 1;
        C.y = C.x + 1;
        """);
  }

  @Test
  public void testThisInStaticField_thisInArrowFunction() {
    var src =
        """
        class C {
          static x = 2;
          static y = () => this.x;
        }
        """;

    test(
        src,
        """
        class C {}
        C.x = 2;
        C.y = () => {
          // Note: This is the correct behavior.
          return C.x;
        };
        """);

    test(
        withOptions().useStaticInheritance(),
        src,
        """
        class C {}
        C.x = 2;
        C.y = () => {
          return C.x;
        };
        """);

    test(
        withOptions().useEs2022LanguageOut(),
        src,
        """
        class C {
          static x;
          static y;
        }
        C.x = 2;
        C.y = () => {
          return C.x;
        };
        """);
  }

  @Test
  public void testThisInStaticField_staticMethodCall() {
    test(
        """
        class F {
          static a = 'there';
          static b = this.c() + this.a;
          static c() { return 'hi'; }
        }
        """,
        """
        class F {
          static c() {
            return "hi";
          }
        }
        F.a = "there";
        F.b = F.c() + F.a;
        """);
  }

  @Test
  public void testThisInStaticGetter() {
    testSame(
        """
        let x = 1;
        class Child {
          static getX() {
            return x;
          }
          static get prop() {
            return this.getX();
          }
        }
        """);
  }

  @Test
  public void testThisInStaticSetter() {
    testSame(
        """
        let x = 1;
        class Child {
          static setX(newX) {
            x = newX;
          }
          static set prop(p) {
            this.setX(p);
          }
        }
        """);
  }

  @Test
  public void testSuperInStaticField() {
    test(
        """
        class Foo {
          static x(a, b) {
            return a + b;
          }
          static y(c, d) {
            return c - d;
          }
        }
        class Bar extends Foo {
          static z = () => super.x(1, 2) + 12 + super.y(3, 4);
        }
        """,
        """
        class Foo {
          static x(a, b) {
            return a + b;
          }
          static y(c, d) {
            return c - d;
          }
        }
        class Bar extends Foo {}
        Bar.z = () => {
          return Foo.x(1, 2) + 12 + Foo.y(3, 4);
        };
        """);

    test(
        """
        const ns = {};
        ns.Foo = class {
          static x(a, b) {
            return 5;
          }
        }
        class Bar extends ns.Foo {
          static z = () => super.x(1, 2);
        }
        """,
        """
        const ns = {};
        ns.Foo = class {
          static x(a, b) {
            return 5;
          }
        };
        class Bar extends ns.Foo {}
        Bar.z = () => {
          return ns.Foo.x(1, 2);
        };
        """);

    test(
        """
        class Bar {
          static a = { method1() {} };
          static b = { method2() { super.method1(); } };
        }
        """,
        """
        class Bar {}
        Bar.a = {method1() {}};
        Bar.b = {method2() {
          super.method1();
        }};
        """);

    test(
        """
        class Parent {
          static get parentGetter() {
            return {val: 1};
          }
        }
        class Child extends Parent {
          static val = super.parentGetter.val;
        }
        """,
        """
        class Parent {
          static get parentGetter() {
            return {val:1};
          }
        }
        class Child extends Parent {}
        Child.val = Parent.parentGetter.val;
        """);
  }

  @Test
  public void testSuperInStaticField_superInArrowFunction() {
    var src =
        """
        class Parent {
          static x = 1;
        }
        class Child extends Parent {
          static y = () => super.x;
        }
        """;

    test(
        src,
        """
        class Parent {}
        Parent.x = 1;
        class Child extends Parent {}
        Child.y = () => {
          return Parent.x;
        };
        """);

    test(
        withOptions().useStaticInheritance(),
        src,
        """
        class Parent {}
        Parent.x = 1;
        class Child extends Parent {
          // static_init method stays because of the super reference.
          static STATIC_INIT$0() {
            Child.y = () => {
              return super.x;
            };
          }
        }
        Child.STATIC_INIT$0();
        """);
  }

  @Test
  public void testSuperReferencesStaticGetter() {
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
        """;

    // non-strict
    test(
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
        }
        Child.msg = Parent.greeting;
        """);

    // strict
    test(
        withOptions().useStaticInheritance(),
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
          // static_init method stays because of the super reference.
          static STATIC_INIT$0() {
            Child.msg = super.greeting;
          }
        }
        Child.STATIC_INIT$0();
        """);
  }

  @Test
  public void testSuperReferencesStaticGetter_viaElementAccess() {
    var src =
        """
        /** @unrestricted */
        class Parent {
          static getName() {
            return 'Parent';
          }
          static get ['greeting']() {
            return 'Hello ' + this.getName();
          }
        }
        class Child extends Parent {
          static getName() {
            return 'Child';
          }
          static msg = super['greeting'];  // 'Hello Child'
        }
        """;

    // non-strict
    test(
        src,
        """
        class Parent {
          static getName() {
            return "Parent";
          }
          static get ["greeting"]() {
            return "Hello " + this.getName();
          }
        }
        class Child extends Parent {
          static getName() {
            return "Child";
          }
        }
        Child.msg = Parent["greeting"];
        """);

    // strict
    test(
        withOptions().useStaticInheritance(),
        src,
        """
        class Parent {
          static getName() {
            return "Parent";
          }
          static get ["greeting"]() {
            return "Hello " + this.getName();
          }
        }
        class Child extends Parent {
          static getName() {
            return "Child";
          }
          // static_init method stays because of the super reference.
          static STATIC_INIT$0() {
            Child.msg = super["greeting"];
          }
        }
        Child.STATIC_INIT$0();
        """);
  }

  @Test
  public void testSuperInStaticFieldObjectSpread() {
    test(
        """
        class Base {
          static X = {a: 1};
        }

        class Child extends Base {
          /** @type {!Object} */
          static Y = {...super.X, b: 2};
        }
        """,
        """
        class Base {}
        Base.X = {a:1};
        class Child extends Base {}
        Child.Y = {...Base.X, b:2};
        """);

    test(
        """
        const ns = {};
        ns.Base = class {
          static X = {a: 1};
        };

        class Child extends ns.Base {
          /** @type {!Object} */
          static Y = {...super.X, b: 2};
        }
        """,
        """
        const ns = {};
        ns.Base = class {};
        ns.Base.X = {a:1};
        class Child extends ns.Base {}
        Child.Y = {...ns.Base.X, b:2};
        """);
  }

  @Test
  public void testSuperInStaticBlock() {
    test(
        """
        class Parent {
          static getGreeting() {
            return 'Hello';
          }
        }
        class Child extends Parent {
          static {
            alert(super.getGreeting());
          }
        }
        """,
        """
        class Parent {
          static getGreeting() {
            return "Hello";
          }
        }
        class Child extends Parent {}
        {
          alert(Parent.getGreeting());
        }
        """);
  }

  @Test
  public void testSuperInStaticBlock_strictSuperRewrite() {
    String source =
        """
        class Parent {
          static getName() {
            return 'Parent';
          }
          static getGreeting() {
            return 'Hello ' + this.getName();
          }
        }
        class Child extends Parent {
          static getName() {
            return 'Child';
          }
          static {
            alert(super.getGreeting());  // Alerts: 'Hello Child'
          }
        }
        """;

    // Test both conditions that trigger strict super rewrite.

    test(
        withOptions().useStaticInheritance(),
        source,
        """
        class Parent {
          static getName() {
            return "Parent";
          }
          static getGreeting() {
            return "Hello " + this.getName();
          }
        }
        class Child extends Parent {
          static getName() {
            return "Child";
          }
          // static_init method stays because of the super reference.
          static STATIC_INIT$0() {
            {
              alert(super.getGreeting());
            }
          }
        }
        Child.STATIC_INIT$0();
        """);

    test(
        withOptions().useEs5LanguageOut(),
        source,
        """
        class Parent {
          static getName() {
            return "Parent";
          }
          static getGreeting() {
            return "Hello " + this.getName();
          }
        }
        class Child extends Parent {
          static getName() {
            return "Child";
          }
          // static_init method stays because of the super reference.
          static STATIC_INIT$0() {
            {
              alert(super.getGreeting());
            }
          }
        }
        Child.STATIC_INIT$0();
        """);
  }

  @Test
  public void testSuperInStaticMethod() {
    test(
        """
        class Parent {
          static getGreeting() {
            return 'Hello';
          }
        }
        class Child extends Parent {
          static sayHello() {
            alert(super.getGreeting());
          }
        }
        Child.sayHello();
        """,
        """
        class Parent {
          static getGreeting() {
            return "Hello";
          }
        }
        class Child extends Parent {
          static sayHello() {
            alert(Parent.getGreeting());
          }
        }
        Child.sayHello();
        """);
  }

  @Test
  public void testSuperInStaticMethod_strictSuperCallRewrite() {
    String source =
        """
        class Parent {
          static getName() {
            return 'Parent';
          }
          static getGreeting() {
            return 'Hello ' + this.getName();
          }
        }
        class Child extends Parent {
          static getName() {
            return 'Child';
          }
          static sayHello() {
            alert(super.getGreeting());  // Alerts: 'Hello Child'
          }
        }
        Child.sayHello();
        """;

    // Test both conditions that trigger strict super rewrite.

    test(
        withOptions().useStaticInheritance(),
        source,
        """
        class Parent {
          static getName() {
            return "Parent";
          }
          static getGreeting() {
            return "Hello " + this.getName();
          }
        }
        class Child extends Parent {
          static getName() {
            return "Child";
          }
          static sayHello() {
            alert(super.getGreeting());
          }
        }
        Child.sayHello();
        """);

    test(
        withOptions().useEs5LanguageOut(),
        source,
        """
        class Parent {
          static getName() {
            return "Parent";
          }
          static getGreeting() {
            return "Hello " + this.getName();
          }
        }
        class Child extends Parent {
          static getName() {
            return "Child";
          }
          static sayHello() {
            alert(super.getGreeting());
          }
        }
        Child.sayHello();
        """);
  }

  @Test
  public void testSuperInComputedStaticMethod() {
    test(
        """
        class Parent {
          static getGreeting() {
            return 'Hello';
          }
        }
        /** @unrestricted */
        class Child extends Parent {
          static ['sayHello']() {
            alert(super.getGreeting());
          }
        }
        Child['sayHello']();
        """,
        """
        class Parent {
          static getGreeting() {
            return "Hello";
          }
        }
        class Child extends Parent {
          static ["sayHello"]() {
            alert(Parent.getGreeting());
          }
        }
        Child["sayHello"]();
        """);
  }

  @Test
  public void testSuperInStaticGetter() {
    test(
        """
        class Parent {
          static getVal() {
            return 1;
          }
        }
        class Child extends Parent {
          static get childProp() {
            return super.getVal();
          }
        }
        """,
        """
        class Parent {
          static getVal() {
            return 1;
          }
        }
        class Child extends Parent {
          static get childProp() {
            return Parent.getVal();
          }
        }
        """);
  }

  @Test
  public void testSuperInStaticSetter() {
    test(
        """
        class Parent {
          static getVal() {
            return 1;
          }
        }
        class Child extends Parent {
          static set childProp(x) {
            alert(super.getVal());
          }
        }
        """,
        """
        class Parent {
          static getVal() {
            return 1;
          }
        }
        class Child extends Parent {
          static set childProp(x) {
            alert(Parent.getVal());
          }
        }
        """);
  }

  @Test
  public void testComputedPropInNonStaticField() {
    var src =
        """
        /** @unrestricted */
        class C {
          [x+=1];
          [x+=2] = 3;
        }
        """;
    test(
        src,
        """
        var COMP_FIELD$0 = x = x + 1;
        var COMP_FIELD$1 = x = x + 2;
        class C {
          constructor() {
            this[COMP_FIELD$0] = void 0;
            this[COMP_FIELD$1] = 3;
          }
        }
        """);
    test(
        withOptions().useEs2022LanguageOut(),
        src,
        """
        var COMP_FIELD$0 = x = x + 1;
        var COMP_FIELD$1 = x = x + 2;
        class C {
          [COMP_FIELD$0];
          [COMP_FIELD$1] = 3;
        }
        """);

    test(
        """
        /** @unrestricted */
        class C {
          [1] = 1;
          /** @suppress {partialAlias} */
          [2] = this[1];
        }
        """,
        """
        class C {
          constructor() {
            this[1] = 1;
            /** @suppress {partialAlias} */
            this[2] = this[1];
          }
        }
        """);

    test(
        """
        /** @unrestricted */
        let c = class C {
          static [1] = 2;
          [2] = C[1]
        }
        """,
        """
        let c = class {
          constructor() {
            this[2] = c[1];
          }
        };
        c[1] = 2;
        """);

    test(
        """
        foo(/** @unrestricted */ class C {
          static [1] = 2;
          [2] = C[1]
        })
        """,
        """
        const CLASS_DECL$0 = class {
          constructor() {
            this[2] = CLASS_DECL$0[1];
          }
        };
        CLASS_DECL$0[1] = 2;
        foo(CLASS_DECL$0);
        """);

    test(
        """
        let c = class {
          x = 1
          y = this.x
        }
        /** @unrestricted */
        class B {
          [1] = 2;
          [2] = this[1]
        }
        """,
        """
        let c = class {
          constructor() {
            this.x = 1;
            this.y = this.x;
          }
        };
        class B {
          constructor() {
            this[1] = 2;
            this[2] = this[1];
          }
        }
        """);

    testSame(
        """
        class Clazz {
          [Symbol.toPrimitive]() {
            return 42;
          }
        }
        """);
  }

  // TODO(b/538155997): Fix RewriteClassMembers to define __proto__ fields using
  // Object.defineProperty
  @Test
  public void testProtoField_instance() {
    var src =
        """
        /** @unrestricted */
        class C {
          __proto__ = {a: 1};
          ['__proto__'] = {b: 2};
          '__proto__' = {c: 3};
        }
        """;
    test(
        src,
        """
        class C {
          constructor() {
            this.__proto__ = {a: 1};
            this["__proto__"] = {b: 2};
            this["__proto__"] = {c: 3};
          }
        }
        """);
    test(
        withOptions().useEs2022LanguageOut(),
        src,
        """
        class C {
          __proto__ = {a: 1};
          ["__proto__"] = {b: 2};
          ["__proto__"] = {c: 3};
        }
        """);
  }

  // TODO(b/538155997): Fix RewriteClassMembers to define __proto__ fields using
  // Object.defineProperty
  @Test
  public void testProtoField_uninitializedInstance() {
    var src =
        """
        /** @unrestricted */
        class C {
          __proto__;
          ['__proto__'];
        }
        """;
    test(
        src,
        """
        class C {
          constructor() {
            this.__proto__ = void 0;
            this["__proto__"] = void 0;
          }
        }
        """);
    test(
        withOptions().useEs2022LanguageOut(),
        src,
        """
        class C {
          __proto__;
          ["__proto__"];
        }
        """);
  }

  // TODO(b/538155997): Fix RewriteClassMembers to define __proto__ fields using
  // Object.defineProperty
  @Test
  public void testProtoField_static() {
    var src =
        """
        /** @unrestricted */
        class C {
          static __proto__ = {a: 1};
          static ['__proto__'] = {b: 2};
        }
        """;
    test(
        src,
        """
        class C {}
        C.__proto__ = {a: 1};
        C["__proto__"] = {b: 2};
        """);
    test(
        withOptions().useEs2022LanguageOut(),
        src,
        """
        class C {
          static __proto__;
          static ["__proto__"];
        }
        C.__proto__ = {a: 1};
        C["__proto__"] = {b: 2};
        """);
  }

  // TODO(b/538155997): Fix RewriteClassMembers to define __proto__ fields using
  // Object.defineProperty
  @Test
  public void testProtoField_uninitializedStatic() {
    var src =
        """
        /** @unrestricted */
        class C {
          static __proto__;
          static ['__proto__'];
        }
        """;
    test(
        src,
        """
        class C {}
        C.__proto__ = void 0;
        C["__proto__"] = void 0;
        """);
    test(
        withOptions().useEs2022LanguageOut(),
        src,
        """
        class C {
          static __proto__;
          static ["__proto__"];
        }
        """);
  }

  // TODO(b/538155997): Fix RewriteClassMembers to define __proto__ fields using
  // Object.defineProperty
  @Test
  public void testProtoField_interleavedWithNormalFields() {
    test(
        """
        class C {
          x = 1;
          __proto__ = 2;
          y = 3;
          static a = 4;
          static __proto__ = 5;
          static b = 6;
        }
        """,
        """
        class C {
          constructor() {
            this.x = 1;
            this.__proto__ = 2;
            this.y = 3;
          }
        }
        C.a = 4;
        C.__proto__ = 5;
        C.b = 6;
        """);
  }

  @Test
  public void testComputedPropInStaticField() {
    var src =
        """
        /** @unrestricted */
        class C {
          static ['x'];
          static ['y'] = 2;
        }
        """;
    test(
        src,
        """
        class C {}
        C["x"] = void 0;
        C["y"] = 2;
        """);
    test(
        withOptions().useEs2022LanguageOut(),
        src,
        """
        class C {
          static ["x"];
          static ["y"];
        }
        C["y"] = 2;
        """);

    test(
        """
        /** @unrestricted */
        class C {
          static [1] = 1;
          static [2] = this[1];
        }
        """,
        """
        class C {}
        C[1] = 1;
        C[2] = C[1];
        """);

    test(
        """
        /** @unrestricted */
        const C = class {
          static [1] = 1;
          static [2] = this[1];
        }
        """,
        """
        const C = class {};
        C[1] = 1;
        C[2] = C[1];
        """);

    test(
        """
        /** @unrestricted */
        const C = class InnerC {
          static [1] = 1;
          static [2] = this[1];
          static [3] = InnerC[2];
        }
        """,
        """
        const C = class {};
        C[1] = 1;
        C[2] = C[1];
        C[3] = C[2];
        """);

    test(
        """
        /** @unrestricted */
        let c = class C {
          static [1] = 2;
          static [2] = C[1]
        }
        """,
        """
        let c = class {};
        c[1] = 2;
        c[2] = c[1];
        """);

    test(
        """
        foo(/** @unrestricted */ class C {
          static [1] = 2;
          static [2] = C[1]
        })
        """,
        """
        const CLASS_DECL$0 = class {};
        CLASS_DECL$0[1] = 2;
        CLASS_DECL$0[2] = CLASS_DECL$0[1];
        foo(CLASS_DECL$0);
        """);

    test(
        """
        foo(/** @unrestricted */ class {
          static [1] = 1
        })
        """,
        """
        const CLASS_DECL$0 = class {};
        CLASS_DECL$0[1] = 1;
        foo(CLASS_DECL$0);
        """);

    testSame(
        """
        class Clazz {
          static [Symbol.hasInstance](x) {
            return false;
          }
        }
        """);
  }

  @Test
  public void testSideEffectsInComputedField() {
    test(
        """
        function bar() {
          this.x = 3;
          /** @unrestricted */
          class Foo {
            y;
            [this.x] = 2;
          }
        }
        """,
        """
        function bar() {
          this.x = 3;
          var COMP_FIELD$0 = this.x;
          class Foo {
            constructor() {
              this.y = void 0;
              this[COMP_FIELD$0] = 2;
            }
          }
        }
        """);

    test(
        """
        class E {
          y() { return 1; }
        }
        class F extends E {
          x() {
            return /** @unrestricted */ class {
              [super.y()] = 4;
            }
          }
        }
        """,
        """
        class E {
          y() {
            return 1;
          }
        }
        class F extends E {
          x() {
            var COMP_FIELD$1 = super.y();
            const CLASS_DECL$0 = class {
              constructor() {
                this[COMP_FIELD$1] = 4;
              }
            };
            return CLASS_DECL$0;
          }
        }
        """);

    test(
        """
        function bar(num) {}
        /** @unrestricted */
        class Foo {
          [bar(1)] = 'a';
          static b = bar(3);
          static [bar(2)] = bar(4);
        }
        """,
        """
        function bar(num) {}
        var COMP_FIELD$0 = bar(1);
        var COMP_FIELD$1 = bar(2);
        class Foo {
          constructor() {
            this[COMP_FIELD$0] = "a";
          }
        }
        Foo.b = bar(3);
        Foo[COMP_FIELD$1] = bar(4);
        """);

    test(
        """
        let x = 'hello';
        /** @unrestricted */ class Foo {
          static n = (x=5);
          static [x] = 'world';
        }
        """,
        """
        let x = "hello";
        var COMP_FIELD$0 = x;
        class Foo {}
        Foo.n = x = 5;
        Foo[COMP_FIELD$0] = "world";
        """);

    test(
        """
        function foo(num) {}
        /** @unrestricted */
        class Baz {
          ['f' + foo(1)];
          static x = foo(6);
          ['m' + foo(2)]() {};
          static [foo(3)] = foo(7);
          [foo(4)] = 2;
          get [foo(5)]() {}
        }
        """,
        """
        function foo(num) {}
        var COMP_FIELD$0 = "f" + foo(1);
        var COMP_FIELD$1 = "m" + foo(2);
        var COMP_FIELD$2 = foo(3);
        var COMP_FIELD$3 = foo(4);
        var COMP_FIELD$4 = foo(5);
        class Baz {
          constructor() {
            this[COMP_FIELD$0] = void 0;
            this[COMP_FIELD$3] = 2;
          }
          [COMP_FIELD$1]() {}
          get [COMP_FIELD$4]() {}
        }
        Baz.x = foo(6);
        Baz[COMP_FIELD$2] = foo(7);
        """);
  }

  @Test
  public void testClassStaticBlocksNoFieldAssign() {
    test(
        """
        class C {
          static {
          }
        }
        """,
        """
        class C {
        }
        """);

    test(
        """
        class C {
          static {
            let x = 2
            const y = x
          }
        }
        """,
        """
        class C {}
        {
          let x = 2;
          const y = x;
        }
        """);

    test(
        """
        class C {
          static {
            let x = 2
            const y = x
            let z;
            if (x - y == 0) {z = 1} else {z = 2}
            while (x - z > 10) {z++;}
            for (;;) {break;}
          }
        }
        """,
        """
        class C {}
        {
          let x = 2;
          const y = x;
          let z;
          if (x - y == 0) {
            z = 1;
          } else {
            z = 2;
          }
          for (; x - z > 10;) {
            z++;
          }
          for (;;) {
            break;
          }
        }
        """);

    test(
        """
        class C {
          static {
            let x = 2
          }
          static {
            const y = x
          }
        }
        """,
        """
        class C {}
        {
          let x = 2;
        }
        {
          const y = x;
        }
        """);

    test(
        """
        class C {
          static {
            let x = 2
          }
          static {
            const y = x
          }
        }
        class D {
          static {
            let z = 1
          }
        }
        """,
        """
        class C {}
        {
          let x = 2;
        }
        {
          const y = x;
        }
        class D {}
        {
          let z = 1;
        }
        """);

    test(
        """
        class C {
          static {
            let x = function () {return 1;}
            const y = () => {return 2;}
            function a() {return 3;}
            let z = (() => {return 4;})();
          }
        }
        """,
        """
        class C {}
        {
          function a() {
            return 3;
          }
          let x = function() {
            return 1;
          };
          const y = () => {
            return 2;
          };
          let z = (() => {
            return 4;
          })();
        }
        """);

    test(
        """
        class C {
          static {
            C.x = 2
            const y = C.x;
          }
        }
        """,
        """
        class C {}
        {
          C.x = 2;
          const y = C.x;
        }
        """);

    test(
        """
        class Foo {
          static {
            let x = 5;
            class Bar {
              static {
                let x = 'str';
              }
            }
          }
        }
        """,
        """
        class Foo {}
        {
          let x = 5;
          class Bar {}
          {
            let x$jscomp$1 = "str";
          }
        }
        """);
  }

  @Test
  public void testStaticNoncomputed() {
    test(
        """
        class C {
          static x = 2
        }
        """,
        """
        class C {}
        C.x = 2;
        """);

    var src =
        """
        class C {
          static x;
        }
        """;
    test(
        src,
        """
        class C {}
        C.x = void 0;
        """);
    testSame(withOptions().useEs2022LanguageOut(), src);

    src =
        """
        class C {
          static x = 2
          static y = 'hi'
          static z;
        }
        """;
    test(
        src,
        """
        class C {}
        C.x = 2;
        C.y = "hi";
        C.z = void 0;
        """);
    test(
        withOptions().useEs2022LanguageOut(),
        src,
        """
        class C {
          static x;
          static y;
          static z;
        }
        C.x = 2;
        C.y = "hi";
        """);

    test(
        """
        class C {
          static x = 2
          static y = 3
        }
        class D {
          static z = 1
        }
        """,
        """
        class C {}
        C.x = 2;
        C.y = 3;
        class D {}
        D.z = 1;
        """);

    test(
        """
        class C {
          static w = function () {return 1;};
          static x = () => {return 2;};
          static y = (function a() {return 3;})();
          static z = (() => {return 4;})();
        }
        """,
        """
        class C {}
        C.w = function() {
          return 1;
        };
        C.x = () => {
          return 2;
        };
        C.y = function a() {
          return 3;
        }();
        C.z = (() => {
          return 4;
        })();
        """);

    test(
        """
        class C {
          static x = 2
          static y = C.x
        }
        """,
        """
        class C {}
        C.x = 2;
        C.y = C.x;
        """);

    test(
        """
        class C {
          static x = 2
          static {let y = C.x}
        }
        """,
        """
        class C {}
        C.x = 2;
        {
          let y = C.x;
        }
        """);
  }

  @Test
  public void testInstanceNoncomputedWithNonemptyConstructor() {
    test(
        """
        class C extends Object {
          x = 1;
          z = 3;
          constructor() {
            super();
            this.y = 2;
          }
        }
        """,
        """
        class C extends Object{
          constructor() {
            super();
            this.x = 1
            this.z = 3
            this.y = 2;
          }
        }
        """);

    test(
        """
        class C {
          x;
          constructor() {
            this.y = 2;
          }
        }
        """,
        """
        class C {
          constructor() {
            this.x = void 0;
            this.y = 2;
          }
        }
        """);

    test(
        """
        class C {
          x = 1
          y = 2
          constructor() {
            this.z = 3;
          }
        }
        """,
        """
        class C {
          constructor() {
            this.x = 1;
            this.y = 2;
            this.z = 3;
          }
        }
        """);

    test(
        """
        class C {
          x = 1
          y = 2
          constructor() {
            alert(3);
            this.z = 4;
          }
        }
        """,
        """
        class C {
          constructor() {
            this.x = 1;
            this.y = 2;
            alert(3);
            this.z = 4;
          }
        }
        """);

    test(
        """
        class C {
          x = 1
          constructor() {
            alert(3);
            this.z = 4;
          }
          y = 2
        }
        """,
        """
        class C {
          constructor() {
            this.x = 1;
            this.y = 2;
            alert(3);
            this.z = 4;
          }
        }
        """);

    test(
        """
        class C {
          x = 1
          constructor() {
            alert(3);
            this.z = 4;
          }
          y = 2
        }
        class D {
          a = 5;
          constructor() { this.b = 6;}
        }
        """,
        """
        class C {
          constructor() {
            this.x = 1;
            this.y = 2;
            alert(3);
            this.z = 4;
          }
        }
        class D {
        constructor() {
          this.a = 5;
          this.b = 6
        }
        }
        """);
  }

  @Test
  public void testInstanceComputedWithNonemptyConstructorAndSuper() {
    var src =
        """
        class A { constructor() { alert(1); } }
        /** @unrestricted */ class C extends A {
          ['x'] = 1;
          constructor() {
            super();
            this['y'] = 2;
            this['z'] = 3;
          }
        }
        """;
    test(
        src,
        """
        class A { constructor() { alert(1); } }
        class C extends A {
          constructor() {
            super()
            this['x'] = 1
            this['y'] = 2;
            this['z'] = 3;
          }
        }
        """);
    test(
        withOptions().useEs2022LanguageOut(),
        src,
        """
        class A {
          constructor() {
            alert(1);
          }
        }
        class C extends A {
          ["x"] = 1;
          constructor() {
            super();
            this["y"] = 2;
            this["z"] = 3;
          }
        }
        """);
  }

  @Test
  public void testInstanceNoncomputedWithNonemptyConstructorAndSuper() {
    test(
        """
        class A { constructor() { alert(1); } }
        class C extends A {
          x = 1;
          constructor() {
            super()
            this.y = 2;
          }
        }
        """,
        """
        class A { constructor() { alert(1); } }
        class C extends A {
          constructor() {
            super()
            this.x = 1
            this.y = 2;
          }
        }
        """);

    test(
        """
        class A { constructor() { this.x = 1; } }
        class C extends A {
          y;
          constructor() {
            super()
            alert(3);
            this.z = 4;
          }
        }
        """,
        """
        class A {
          constructor() {
            this.x = 1;
          }
        }
        class C extends A {
          constructor() {
            super();
            this.y = void 0;
            alert(3);
            this.z = 4;
          }
        }
        """);

    test(
        """
        class A { constructor() { this.x = 1; } }
        class C extends A {
          y;
          constructor() {
            alert(3);
            super()
            this.z = 4;
          }
        }
        """,
        """
        class A {
          constructor() {
            this.x = 1;
          }
        }
        class C extends A {
          constructor() {
            alert(3);
            super();
            this.y = void 0;
            this.z = 4;
          }
        }
        """);
  }

  @Test
  public void testNonComputedInstanceWithEmptyConstructor() {
    test(
        """
        class C {
          x = 2;
          constructor() {}
        }
        """,
        """
        class C {
          constructor() {
            this.x = 2;
          }
        }
        """);

    test(
        """
        class C {
          x;
          constructor() {}
        }
        """,
        """
        class C {
          constructor() {
            this.x = void 0;
          }
        }
        """);

    test(
        """
        class C {
          x = 2
          y = 'hi'
          z;
          constructor() {}
        }
        """,
        """
        class C {
          constructor() {
            this.x = 2;
            this.y = "hi";
            this.z = void 0;
          }
        }
        """);

    test(
        """
        class C {
          x = 1
          constructor() {
          }
          y = 2
        }
        """,
        """
        class C {
          constructor() {
            this.x = 1;
            this.y = 2;
          }
        }
        """);

    test(
        """
        class C {
          x = 1
          constructor() {
          }
          y = 2
        }
        class D {
          a = 5;
          constructor() {}
        }
        """,
        """
        class C {
          constructor() {
            this.x = 1;
            this.y = 2;
          }
        }
        class D {
        constructor() {
          this.a = 5;
        }
        }
        """);

    test(
        """
        class C {
          w = function () {return 1;};
          x = () => {return 2;};
          y = (function a() {return 3;})();
          z = (() => {return 4;})();
          constructor() {}
        }
        """,
        """
        class C {
          constructor() {
            this.w = function () {return 1;};
            this.x = () => {return 2;};
            this.y = (function a() {return 3;})();
            this.z = (() => {return 4;})();
          }
        }
        """);

    test(
        """
        class C {
          static x = 2
          constructor() {}
          y = C.x
        }
        """,
        """
        class C {
          constructor() {
            this.y = C.x;
          }
        }
        C.x = 2;
        """);
  }

  @Test
  public void testInstanceNoncomputedNoConstructor() {
    test(
        """
        class C {
          x = 2;
        }
        """,
        """
        class C {
          constructor() {this.x=2;}
        }
        """);

    test(
        """
        class C {
          x;
        }
        """,
        """
        class C {
          constructor() {
            this.x = void 0;
          }
        }
        """);

    test(
        """
        class C {
          x = 2
          y = 'hi'
          z;
        }
        """,
        """
        class C {
          constructor() {
            this.x = 2;
            this.y = "hi";
            this.z = void 0;
          }
        }
        """);
    test(
        """
        class C {
          foo() {}
          x = 1;
        }
        """,
        """
        class C {
          constructor() {this.x = 1;}
          foo() {}
        }
        """);

    test(
        """
        class C {
          static x = 2
          y = C.x
        }
        """,
        """
        class C {
          constructor() {
            this.y = C.x;
          }
        }
        C.x = 2;
        """);

    test(
        """
        class C {
          w = function () {return 1;};
          x = () => {return 2;};
          y = (function a() {return 3;})();
          z = (() => {return 4;})();
        }
        """,
        """
        class C {
          constructor() {
            this.w = function () {return 1;};
            this.x = () => {return 2;};
            this.y = (function a() {return 3;})();
            this.z = (() => {return 4;})();
          }
        }
        """);
  }

  @Test
  public void testInstanceNonComputedNoConstructorWithSuperclass() {
    test(
        """
        class B {}
        class C extends B {x = 1;}
        """,
        """
        class B {}
        class C extends B {
          constructor() {
            super(...arguments);
            this.x = 1;
          }
        }
        """);
    test(
        """
        class B {constructor() {}; y = 2;}
        class C extends B {x = 1;}
        """,
        """
        class B {constructor() {this.y = 2}}
        class C extends B {
          constructor() {
            super(...arguments);
            this.x = 1;
          }
        }
        """);
    test(
        """
        class B {constructor(a, b) {}; y = 2;}
        class C extends B {x = 1;}
        """,
        """
        class B {constructor(a, b) {this.y = 2}}
        class C extends B {
          constructor() {
            super(...arguments);
            this.x = 1;
          }
        }
        """);
  }

  @Test
  public void testClassExpressionsStaticBlocks() {
    test(
        """
        let c = class C {
          static {
            C.y = 2;
            let x = C.y
          }
        }
        """,
        """
        let c = class {};
        {
          c.y = 2;
          let x = c.y;
        }
        """);

    test(
        """
        foo(class C {
          static {
            C.y = 2;
            let x = C.y
          }
        })
        """,
        """
        foo((() => {
          const CLASS_DECL$0 = class {};
          {
            CLASS_DECL$0.y = 2;
            let x = CLASS_DECL$0.y;
          }
          return CLASS_DECL$0;
        })());
        """);

    test(
        """
        class A { static b = {}; }
        foo(A.b.c = class C {
          static {
            C.y = 2;
            let x = C.y
          }
        })
        """,
        """
        class A {}
        A.b = {};
        foo(A.b.c = (() => {
          const CLASS_DECL$0 = class {};
          {
            CLASS_DECL$0.y = 2;
            let x = CLASS_DECL$0.y;
          }
          return CLASS_DECL$0;
        })());
        """);
  }

  @Test
  public void testNonClassDeclarationsStaticBlocks() {
    test(
        """
        let c = class {
          static {
            let x = 1
          }
        }
        """,
        """
        let c = class {};
        {
          let x = 1;
        }
        """);

    test(
        """
        class A {}
        A.c = class {
          static {
            let x = 1
          }
        }
        """,
        """
        class A {}
        A.c = class {};
        {
          let x = 1;
        }
        """);

    test(
        """
        class A {}
        A[1] = class {
          static {
            let x = 1
          }
        }
        """,
        """
        class A {}
        A[1] = (() => {
          const CLASS_DECL$0 = class {};
          {
            let x = 1;
          }
          return CLASS_DECL$0;
        })();
        """);
  }

  @Test
  public void testNonClassDeclarationsStaticNoncomputedFields() {
    test(
        """
        let c = class {
          static x = 1
        }
        """,
        """
        let c = class {};
        c.x = 1;
        """);

    test(
        """
        class A {}
        A.c = class {
          static x = 1
        }
        """,
        """
        class A {}
        A.c = class {};
        A.c.x = 1;
        """);

    test(
        """
        class A {}
        A[1] = class {
          static x = 1
        }
        """,
        """
        class A {}
        const CLASS_DECL$0 = class {};
        CLASS_DECL$0.x = 1;
        A[1] = CLASS_DECL$0;
        """);

    test(
        """
        let c = class C {
          static y = 2;
          static x = C.y
        }
        """,
        """
        let c = class {};
        c.y = 2;
        c.x = c.y;
        """);

    test(
        """
        foo(class C {
          static y = 2;
          static x = C.y
        })
        """,
        """
        const CLASS_DECL$0 = class {};
        CLASS_DECL$0.y = 2;
        CLASS_DECL$0.x = CLASS_DECL$0.y;
        foo(CLASS_DECL$0);
        """);

    test(
        """
        foo(class C {
          static y = 2;
          x = C.y
        })
        """,
        """
        const CLASS_DECL$0 = class {
          constructor() {
            this.x = CLASS_DECL$0.y;
          }
        };
        CLASS_DECL$0.y = 2;
        foo(CLASS_DECL$0);
        """);
  }

  @Test
  public void testNonClassDeclarationsInstanceNoncomputedFields() {
    test(
        """
        let c = class {
          y = 2;
        }
        """,
        """
        let c = class {
          constructor() {
            this.y = 2;
          }
        }
        """);

    test(
        """
        let c = class C {
          y = 2;
        }
        """,
        """
        let c = class {
          constructor() {
            this.y = 2;
          }
        };
        """);

    test(
        """
        class A {}
        A.c = class {
          y = 2;
        }
        """,
        """
        class A {}
        A.c = class {
          constructor() {
            this.y = 2;
          }
        }
        """);

    test(
        """
        A[1] = class {
          y = 2;
        }
        """,
        """
        const CLASS_DECL$0 = class {
          constructor() {
            this.y = 2;
          }
        };
        A[1] = CLASS_DECL$0;
        """);

    test(
        """
        let c = class C {
          y = 2;
        }
        """,
        """
        let c = class {
          constructor() {
            this.y = 2;
          }
        };
        """);

    test(
        """
        class A {}
        A.c = class C {
          y = 2;
        }
        """,
        """
        class A {}
        A.c = class {
          constructor() {
            this.y = 2;
          }
        };

        """);

    test(
        """
        A[1] = class C {
          y = 2;
        }
        """,
        """
        const CLASS_DECL$0 = class {
          constructor() {
            this.y = 2;
          }
        };
        A[1] = CLASS_DECL$0;
        """);

    test(
        """
        foo(class C {
          y = 2;
        })
        """,
        """
        const CLASS_DECL$0 = class {
          constructor() {
            this.y = 2;
          }
        };
        foo(CLASS_DECL$0);
        """);
  }

  @Test
  public void testConstuctorAndStaticFieldDontConflict() {
    test(
        """
        let x = 2;
        class C {
          static y = x
          constructor(x) {}
        }
        """,
        """
        let x = 2;
        class C {
          constructor(x$jscomp$1) {}
        }
        C.y = x;
        """);
  }

  @Test
  public void testInstanceInitializerShadowsConstructorDeclaration() {
    test(
        """
        let x = 2;
        class C {
          y = x;
          constructor(x) {}
        }
        """,
        """
        let x = 2;
        class C {
          constructor(x$jscomp$1) {
            this.y = x;
          }
        }
        """);

    test(
        """
        let x = 2;
        class C {
          y = x;
          constructor() { let x; }
        }
        """,
        """
        let x = 2;
        class C {
          constructor() {
            this.y = x;
            let x$jscomp$1;
          }
        }
        """);

    test(
        """
        let x = 2;
        class C {
          y = x
          constructor() { {var x;} }
        }
        """,
        """
        let x = 2;
        class C {
          constructor() {
            this.y = x;
            {
             var x$jscomp$1;
            }
          }
        }
        """);

    test(
        """
        function f() { return 4; }
        class C {
          y = f();
          constructor() {function f() { return 'str'; }}
        }
        """,
        """
        function f() {
          return 4;
        }
        class C {
          constructor() {
            function f$jscomp$1() {
              return 'str';
            }
            this.y = f();
          }
        }
        """);

    test(
        """
        class Foo {
          constructor(x) {}
          y = (x) => x;
        }
        """,
        """
        class Foo {
          constructor(x) {
            this.y = x$jscomp$1 => {
              return x$jscomp$1;
            };
          }
        }
        """);

    test(
        """
        let x = 2;
        class C {
          y = (x) => x;
          constructor(x) {}
        }
        """,
        """
        let x = 2;
        class C {
          constructor(x$jscomp$2) {
            this.y = x$jscomp$1 => {
              return x$jscomp$1;
            };
          }
        }
        """);
  }

  @Test
  public void testInstanceInitializerDoesntShadowConstructorDeclaration() {
    test(
        """
        let x = 2;
        class C {
          y = x;
          constructor() { {let x;} }
        }
        """,
        """
        let x = 2;
        class C {
          constructor() {
            this.y = x;
            {let x$jscomp$1;}
          }
        }
        """);

    test(
        """
        let x = 2;
        class C {
          y = x
          constructor() {() => { let x; };}
        }
        """,
        """
        let x = 2;
        class C {
          constructor() {
            this.y = x;
            () => { let x$jscomp$1; };
          }
        }
        """);

    test(
        """
        let x = 2;
        class C {
          y = x
          constructor() {(x) => 3;}
        }
        """,
        """
        let x = 2;
        class C {
          constructor() {
            this.y = x;
            (x$jscomp$1) => { return 3; };
          }
        }
        """);
  }

  @Test
  public void testInstanceFieldInitializersDontBleedOut() {
    test(
        """
        class C {
          y = z
          method() { x; }
          constructor(x) {}
        }
        """,
        """
        class C {
          method() { x; }
          constructor(x) {
            this.y = z;
          }
        }
        """);
  }

  @Test
  public void testNestedClassesWithShadowingInstanceFields() {
    test(
        """
        let x = 2;
        class C {
          y = () => {
            class Foo { z = x }
          };
          constructor(x) {}
        }
        """,
        """
        let x = 2;
        class C {
          constructor(x$jscomp$1) {
            this.y = () => {
              class Foo {
                constructor() {
                  this.z = x;
                }
              }
            };
          }
        }
        """);
  }

  // Added when fixing transpilation of real-world code that passed a class expression to a
  // constructor call.
  @Test
  public void testPublicFieldsInClassExpressionInNew() {
    test(
        """
        let foo = new (
            class Bar {
              x;
              static y;
            }
        )();
        """,
        """
        const CLASS_DECL$0 = class {
          constructor() {
            this.x = void 0;
          }
        };
        CLASS_DECL$0.y = void 0;
        let foo = new CLASS_DECL$0();
        """);
  }

  @Test
  public void testNonClassDeclarationsFunctionArgs() {
    test(
        """
        A[foo()] = class {
          static x;
        }
        """,
        """
        A[foo()] = (() => {
          const CLASS_DECL$0 = class {};
          CLASS_DECL$0.x = void 0;
          return CLASS_DECL$0;
        })();
        """);

    test(
        """
        foo(c = class {
          static x;
        })
        """,
        """
        const CLASS_DECL$0 = class {};
        CLASS_DECL$0.x = void 0;
        foo(c = CLASS_DECL$0);
        """);

    test(
        """
        function foo(c = class {
          static x;
        }) {}
        """,
        """
        function foo(c = (() => {
          const CLASS_DECL$0 = class {};
          CLASS_DECL$0.x = void 0;
          return CLASS_DECL$0;
        })()) {}
        """);
  }

  @Test
  public void testAnonymousClassExpression() {
    test(
        """
        function foo() {
          return class {
            y;
            static x;
          }
        }
        """,
        """
        function foo() {
          const CLASS_DECL$0 = class {
            constructor() {
              this.y = void 0;
            }
          };
          CLASS_DECL$0.x = void 0;
          return CLASS_DECL$0;
        }
        """);

    test(
        """
        foo(class {
          y = 2;
        })
        """,
        """
        const CLASS_DECL$0 = class {
          constructor() {
            this.y = 2;
          }
        };
        foo(CLASS_DECL$0);
        """);

    test(
        """
        foo(class {
          static x = 1;
        })
        """,
        """
        const CLASS_DECL$0 = class {};
        CLASS_DECL$0.x = 1;
        foo(CLASS_DECL$0);
        """);
  }

  @Test
  public void testNestedSuperCallWithClassField_inCommaStatement() {
    // We want to support this code pattern in case the compiler is asked to transpile classes that
    // were already minified by some other minifier, as that often leads to combining super()
    // calls with other code in the constructor.
    test(
        """
        class A { constructor(...args) {} }
        class B extends A {
          prop;
          constructor() {
            (super(1, 2, 3), bar());
          }
        }
        """,
        """
        class A { constructor(...args) {} }
        class B extends A {
          constructor() {
            var JSCompiler_inline_result$jscomp$0 = super(1, 2, 3);
            this.prop = void 0;
            (JSCompiler_inline_result$jscomp$0,  bar());
          }
        }
        """);
  }

  @Test
  public void testSuperCallInComplexExpression() {
    test(
        """
        class A { constructor(...args) {} }
        class B extends A {
          prop = 0;
          constructor(a) {
            foo(bar(), super(1, 2, 3), baz());
          }
        }
        """,
        """
        class A { constructor(...args) {} }
        class B extends A {
          constructor(a) {
            var JSCompiler_temp_const$jscomp$1 = foo;
            var JSCompiler_temp_const$jscomp$0 = bar();
            var JSCompiler_inline_result$jscomp$2 = super(1, 2, 3);
            this.prop = 0;
            JSCompiler_temp_const$jscomp$1(
                JSCompiler_temp_const$jscomp$0, JSCompiler_inline_result$jscomp$2, baz());
          }
        }
        """);
  }

  @Test
  public void testSuperCallInComplexExpression_cannotDecompose() {
    var ex =
        assertThrows(
            RuntimeException.class,
            () ->
                test(
                    """
                    class A { constructor(...args) {} }
                    class B extends A {
                      prop = 0;
                      constructor(a) {
                        for (const x = foo(), y = super();;) {}
                      }
                    }
                    """,
                    """
                    """));
    assertThat(ex)
        .hasMessageThat()
        .contains(
            "Cannot decompose super() call in a class with class fields. Move super() call to the"
                + " root of the constructor.");
  }

  @Test
  public void testConditionalSuperCalls() {
    // See: BUG-1 in go/tsjs-private-elements-bugs
    //
    // Compiler Rejection on Branching super() with Fields (Won't fix now):
    // Under ES2022, derived class constructors are allowed to call super() conditionally in
    // separate branches (e.g. if (cond) super(-1) else super(1)). Class field initializations are
    // defined by the specification to run immediately after super() returns and binds `this`.
    //
    // Current Compiler Behavior:
    // Es6NormalizeClasses assumes that constructors with fields have at most one super() call at
    // the root of the constructor body. When addTemporaryInsertionPoint() detects more than one
    // super() call, it throws an IllegalStateException:
    // "classes with public fields must have only one super() call at the constructor root".
    //
    // Reviewer Decision & Potential Future Fix:
    // This will not be fixed now because the compiler error message is clear and descriptive. We
    // will re-evaluate if necessary later if user requests arise. If revisited later, field and
    // private initializations can be extracted into an arrow function assigned to a local variable
    // and invoked immediately after each super() call.
    var ex =
        assertThrows(
            RuntimeException.class,
            () ->
                test(
                    """
                    class A { constructor(...args) {} }
                    class B extends A {
                      prop = 0;
                      #x = 1;
                      constructor(a) {
                        if (a < 0) {
                          // In ES2022, calling super() in branches is valid and binds `this`.
                          // Compiler rejects multiple super() calls in branches at compile time.
                          super(-1);
                        } else {
                          super(1);
                        }
                      }
                    }
                    """,
                    ""));
    assertThat(ex)
        .hasMessageThat()
        .contains(
            "classes with public fields must have only one super() call at the constructor root");
  }

  @Test
  public void testForwardPrivateFieldAccess_evaluatesToUndefined() {
    // TODO(b/236744850): Fix in a future CL by deferring property definition on the shadow instance
    // record until initialization and wrapping forward reads with $jscomp.checkPrivateGet.
    // See: BUG-2 in go/tsjs-private-elements-bugs
    //
    // Forward Private Field Access Evaluates to undefined instead of throwing TypeError:
    // ES2022 Specification:
    // Under ES2022 § 10.2.1.2 (InitializeInstanceElements), private field slots are installed
    // sequentially as each field definition executes. Reading a private field whose initializer
    // has not yet run must throw a TypeError at runtime because its private slot is uninitialized.
    //
    // Current Compiler Behavior:
    // Closure Compiler allocates a single shared record object (PRIVATE$1 = Object.create(null))
    // and registers the instance in the PrivateMap upon constructor entry:
    //   PRIVATE_MAP$0.set(this, PRIVATE$1);
    // When `#first = this.#later` executes, `#later` accesses the unassigned property on
    // PRIVATE$1:
    //   PRIVATE$1.first = PRIVATE_MAP$0.get(this).later;
    // Because in JavaScript reading an unassigned property on an object returns undefined,
    // this.#first silently evaluates to undefined instead of throwing a TypeError.
    test(
        """
        class Foo {
          // BUG: Evaluating `this.#later` here should throw a runtime TypeError per
          // ES2022 § 10.2.1.2 because `#later` has not yet been initialized.
          // In Closure Compiler, it silently evaluates to `undefined`.
          #first = this.#later;
          #later = 1;
          getFirst() {
            return this.#first;
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          constructor() {
            const PRIVATE$1 = Object.create(null);
            PRIVATE_MAP$0.set(this, PRIVATE$1);
            // BUG: `later` property is unassigned on PRIVATE$1, so this evaluates to undefined
            // instead of throwing a runtime TypeError.
            PRIVATE$1.first = PRIVATE_MAP$0.get(this).later;
            PRIVATE$1.later = 1;
          }
          getFirst() {
            return PRIVATE_MAP$0.get(this).first;
          }
        }
        """);
  }

  @Test
  public void testAbruptConstructorExecution_leaksBrandedInstance() {
    // TODO(b/236744850): Fix in a future CL by updating field brand checks (#prop in x) to check
    // property existence on the shadow record (key in privateMap.get(x)) using non-throwing lookup.
    // See: BUG-3 in go/tsjs-private-elements-bugs
    //
    // Abrupt Constructor Execution Leaks Fully-Branded Instance:
    // ES2022 Specification:
    // If an error is thrown during instance field initialization, constructor execution aborts.
    // Any subsequent private fields that were never reached must NOT be branded on the leaked
    // instance, so brand checks (`#later in leaked`) must return false.
    //
    // Current Compiler Behavior:
    // Closure Compiler brands the instance at the very beginning of constructor execution by
    // setting the instance in PRIVATE_MAP$0 before any field initializers run:
    //   PRIVATE_MAP$0.set(this, PRIVATE$1);
    // Even though `#later` throws an exception, `this` is already present in PRIVATE_MAP$0.
    // Brand check `#later in x` transpiles to `PRIVATE_MAP$0.has(x)`, which returns true for
    // the partially-constructed leaked instance.
    test(
        """
        let leaked;
        class Foo {
          #first = (leaked = this, 1);
          // Constructor aborts abruptly when evaluating this throw expression.
          #later = (() => { throw new Error('fail'); })();
          static isLaterBranded(x) {
            // BUG: Under ES2022, `#later in leaked` should return false because construction
            // aborted before #later was reached. In Closure Compiler, it returns true.
            return #later in x;
          }
        }

        // Code that triggers the bug at runtime:
        try {
          // 1. Attempting construction throws while initializing #later, leaking `this`.
          new Foo();
        } catch (e) {
          // Expected: constructor aborted due to error in #later initializer.
        }

        // 2. Evaluate brand check on the leaked partially-constructed instance:
        // - ES2022 Spec Requirement: Foo.isLaterBranded(leaked) === false (never reached #later).
        // - Current Compiler Behavior: Evaluates to `true` because PRIVATE_MAP$0.set(this, ...)
        //   ran at constructor entry before #later threw.
        const leakedIsBranded = Foo.isLaterBranded(leaked);
        """,
        """
        let leaked;
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        class Foo {
          constructor() {
            const PRIVATE$1 = Object.create(null);
            // BUG: The instance is branded in PRIVATE_MAP$0 before field initializers run.
            // When #later throws below, leaked instance remains branded.
            PRIVATE_MAP$0.set(this, PRIVATE$1);
            PRIVATE$1.first = (leaked = this, 1);
            PRIVATE$1.later = (() => { throw new Error('fail'); })();
          }
          static isLaterBranded(x) {
            // BUG: Returns true on the leaked instance because PRIVATE_MAP$0.has(x) is true.
            return PRIVATE_MAP$0.has(x);
          }
        }

        try {
          new Foo();
        } catch (e) {
        }
        const leakedIsBranded = Foo.isLaterBranded(leaked);
        """);
  }

  @Test
  public void testSelfIdentifierCollision_overwritesInstancePointer() {
    // TODO(b/236744850): Fix in a future CL by disambiguating private member property names on the
    // shadow record with a class-unique prefix (and dedicated prefix for $self).
    // See: BUG-4 in go/tsjs-private-elements-bugs
    //
    // Private Identifier #$self Name Collision with Compiler Internal Pointer:
    // ES2022 Specification:
    // `#$self` is a valid ECMAScript private identifier (PrivateIdentifier starts with # followed
    // by IdentifierName, where $ is a valid identifier character).
    //
    // Current Compiler Behavior:
    // In downlevel transpilation, private accessors/methods access the receiver instance via an
    // internal property named `$self` attached to the private record:
    //   PRIVATE$2.$self = this;
    // When a user declares a private field named `#$self`, the compiler strips the `#` and emits:
    //   PRIVATE$2.$self = 1;
    // which directly overwrites the internal instance back-pointer with `1`.
    // Subsequently, invoking the getter attempts to read `PRIVATE_MAP$0.get(this.$self).$self`,
    // which evaluates to `PRIVATE_MAP$0.get(1).$self` and throws a runtime TypeError:
    // "Cannot read properties of undefined".
    test(
        """
        class Foo {
          #$self = 1;
          get #val() {
            return this.#$self;
          }
          getVal() {
            // BUG: Invoking getVal() throws a runtime TypeError ("Cannot read properties of undefined").
            // The user field #$self overwrites the compiler's internal $self receiver pointer.
            return this.#val;
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          val: {
            get: function() {
              // BUG: this.$self was overwritten with 1 below; PRIVATE_MAP$0.get(1) returns undefined,
              // and accessing .$self on undefined throws a runtime TypeError.
              return PRIVATE_MAP$0.get(this.$self).$self;
            }
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE$2.$self = this;
            PRIVATE_MAP$0.set(this, PRIVATE$2);
            // BUG: User field assignment `#$self = 1` overwrites `PRIVATE$2.$self = this` set above.
            PRIVATE$2.$self = 1;
          }
          getVal() {
            return PRIVATE_MAP$0.get(this).val;
          }
        }
        """);
  }

  @Test
  public void testPrivateMethodAssignment_reportsCompileTimeError() {
    // See: BUG-5 in go/tsjs-private-elements-bugs
    //
    // Private Method Assignment Triggers Compile-Time Error (Won't fix now):
    // ES2022 Specification:
    // Under ES2022 § 13.15.2, assigning to a private method (e.g. `this.#connect = null`) is
    // syntactically valid code in ECMAScript grammar. At runtime, evaluating the assignment must
    // throw a TypeError: "Cannot assign to private method #connect".
    //
    // Current Compiler Behavior & Reviewer Decision:
    // Es6NormalizeClasses treats assignment to a private method as a compile-time build failure:
    //   compiler.report(JSError.make(getPropNode, ILLEGAL_PRIVATE_MEMBER_ASSIGNMENT, ...));
    // This will not be fixed now because catching illegal assignments at compile time intentionally
    // shifts detectable bugs left to build time for Google codebases. We will re-evaluate if
    // strict runtime parity is required later.
    testError(
        """
        class Service {
          #connect() {}
          tamper() {
            // BUG: Under ES2022 § 13.15.2, assigning to a private method is valid syntax that
            // should throw a TypeError at runtime when tamper() executes.
            // Instead, Closure Compiler raises a compile-time error:
            // JSC_ILLEGAL_PRIVATE_MEMBER_ASSIGNMENT.
            this.#connect = null;
          }
        }
        """,
        Es6NormalizeClasses.ILLEGAL_PRIVATE_MEMBER_ASSIGNMENT);
  }

  @Test
  public void testSetterOnlyPrivatePropertyRead_evaluatesToUndefined() {
    // TODO(b/236744850): Fix in a future CL by defining a throwing getter stub on the prototype
    // record (or reporting a compile-time diagnostic).
    // See: BUG-5 in go/tsjs-private-elements-bugs
    //
    // Reading Setter-Only Private Member Evaluates to undefined instead of TypeError:
    // ES2022 Specification:
    // Under ES2022 § 13.3.7.1, reading a private member that only defines a setter (and no getter)
    // must throw a runtime TypeError: "Cannot read private member #val without a getter".
    //
    // Current Compiler Behavior:
    // Private property reads rewrite to `PRIVATE_MAP$0.get(this).val`.
    // Because PRIVATE_PROTO$1 defines a setter on `val` but no getter:
    //   val: { set: function(v) {} }
    // reading `PRIVATE_MAP$0.get(this).val` returns undefined without throwing any error.
    test(
        """
        class Foo {
          set #val(v) {}
          getVal() {
            // BUG: Under ES2022 § 13.3.7.1, reading a private property that has only a setter
            // must throw a runtime TypeError ("Cannot read private member without a getter").
            // Instead, reading this.#val evaluates to undefined.
            return this.#val;
          }
        }
        """,
        """
        const PRIVATE_MAP$0 = new $jscomp.PrivateMap();
        const PRIVATE_PROTO$1 = Object.create(null, {
          val: {
            set: function(v) {}
          }
        });
        class Foo {
          constructor() {
            const PRIVATE$2 = Object.create(PRIVATE_PROTO$1);
            PRIVATE_MAP$0.set(this, PRIVATE$2);
          }
          getVal() {
            // BUG: PRIVATE_PROTO$1 has no getter, so reading .val evaluates to undefined
            // instead of throwing a runtime TypeError.
            return PRIVATE_MAP$0.get(this).val;
          }
        }
        """);
  }
}
