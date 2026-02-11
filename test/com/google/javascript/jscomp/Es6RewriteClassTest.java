/*
 * Copyright 2014 The Closure Compiler Authors.
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
import static com.google.javascript.jscomp.TranspilationUtil.CANNOT_CONVERT_YET;
import static com.google.javascript.jscomp.TypedScopeCreator.DYNAMIC_EXTENDS_WITHOUT_JSDOC;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.javascript.jscomp.CompilerOptions.Es6SubclassTranspilation;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class Es6RewriteClassTest extends CompilerTestCase {

  private static final String EXTERNS_BASE =
      new TestExternsBuilder()
          .addArguments()
          .addArray()
          .addFunction()
          .addConsole()
          .addAlert()
          .addObject()
          .addClosureExterns()
          .addJSCompLibraries()
          .build();

  private Es6SubclassTranspilation es6SubclassTranspilation;

  public Es6RewriteClassTest() {
    super(EXTERNS_BASE);
  }

  @Before
  public void customSetUp() throws Exception {
    enableNormalize();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    setLanguageOut(LanguageMode.ECMASCRIPT3);
    enableTypeCheck();
    enableTypeInfoValidation();
    enableScriptFeatureValidation();
    replaceTypesWithColors();
    enableMultistageCompilation();
    setGenericNameReplacements(Es6NormalizeClasses.GENERIC_NAME_REPLACEMENTS);
    es6SubclassTranspilation = Es6SubclassTranspilation.CONCISE_UNSAFE;
  }

  private static PassFactory makePassFactory(
      String name, Function<AbstractCompiler, CompilerPass> pass) {
    return PassFactory.builder().setName(name).setInternalFactory(pass).build();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    PhaseOptimizer optimizer = new PhaseOptimizer(compiler, null);
    optimizer.addOneTimePass(makePassFactory("es6NormalizeClasses", Es6NormalizeClasses::new));
    optimizer.addOneTimePass(makePassFactory("es6ConvertSuper", Es6ConvertSuper::new));
    optimizer.addOneTimePass(
        makePassFactory(
            "es6RewriteClass", (c) -> new Es6RewriteClass(compiler, es6SubclassTranspilation)));
    return optimizer;
  }

  @Test
  public void testSimpleClassStatement() {
    test("class C { }", "/** @constructor */ let C = function() {};");

    // `C` from `let C = function() {};`
    Node cName = getNodeWithName(getLastCompiler().getJsRoot(), "C");
    assertNode(cName).hasToken(Token.NAME);
    assertNode(cName).hasColorThat().isConstructor();

    // function() {}
    Node function = cName.getOnlyChild();
    assertNode(function).hasToken(Token.FUNCTION);
    assertNode(function).hasColorThat().isEqualTo(cName.getColor());
  }

  @Test
  public void testClassStatementWithConstructor() {
    test("class C { constructor() {} }", "/** @constructor */ let C = function() {};");

    // `C` from `let C = function() {};`
    Node cName = getNodeWithName(getLastCompiler().getJsRoot(), "C");
    assertNode(cName).hasToken(Token.NAME);
    assertNode(cName).hasColorThat().isConstructor();

    // function() {}
    Node function = cName.getOnlyChild();
    assertNode(function).hasToken(Token.FUNCTION);
    assertNode(function).hasColorThat().isEqualTo(cName.getColor());
  }

  @Test
  public void testClassStatementWithMethod() {
    test(
        "class C { method() {}; }",
        """
        /** @constructor */
        let C = function() {};
        C.prototype.method = function() {};
        """);

    Node c = getNodeWithName(getLastCompiler().getJsRoot(), "C");

    // `C.prototype.method`
    Node cPrototypeMethod = getNodeWithName(getLastCompiler().getJsRoot(), "method");

    // `C.prototype`
    Node cPrototype = cPrototypeMethod.getFirstChild();
    assertNode(cPrototype).matchesQualifiedName("C.prototype");
    assertThat(c.getColor().getPrototypes()).containsExactly(cPrototype.getColor());

    // `C`
    Node cName = cPrototype.getFirstChild();
    assertNode(cName).matchesQualifiedName("C");
    assertNode(cName).hasColorThat().isEqualTo(c.getColor());
    assertNode(cName).hasColorThat().isConstructor();

    // `function() {}`
    Node function = cPrototypeMethod.getNext();
    assertNode(function).hasToken(Token.FUNCTION);
    assertNode(function).hasColorThat().isEqualTo(cPrototypeMethod.getColor());
  }

  @Test
  public void testClassStatementWithConstructorReferencingThis() {
    test(
        "class C { constructor(a) { this.a = a; } }",
        "/** @constructor */ let C = function(a) { this.a = a; };");
  }

  @Test
  public void testClassStatementWithConstructorAndMethod() {
    test(
        "class C { constructor() {} foo() {} }",
        """
        /** @constructor */
        let C = function() {};
        C.prototype.foo = function() {};
        """);
  }

  @Test
  public void testClassStatementWithConstructorAndMethods() {
    test(
        "class C { constructor() {}; foo() {}; bar() {} }",
        """
        /** @constructor */
        let C = function() {};
        C.prototype.foo = function() {};
        C.prototype.bar = function() {};
        """);
  }

  @Test
  public void testClassStatementWithTwoMethods() {
    test(
        "class C { foo() {}; bar() {} }",
        """
        /** @constructor */
        let C = function() {};
        C.prototype.foo = function() {};
        C.prototype.bar = function() {};
        """);
  }

  @Test
  public void testClassStatementWithNonEmptyConstructorAndMethods() {
    test(
        """
        class C {
          constructor(a) { this.a = a; }

          foo() { console.log(this.a); }

          bar() { alert(this.a); }
        }
        """,
        """
        /** @constructor */
        let C = function(a) { this.a = a; };
        C.prototype.foo = function() { console.log(this.a); };
        C.prototype.bar = function() { alert(this.a); };
        """);
  }

  @Test
  public void testClassStatementsInDifferentBlockScopes() {
    test(
        """
        if (true) {
          class Foo{}
        } else {
          class FooBar{}
        }
        """,
        """
        if (true) {
           /** @constructor */
           let Foo = function() {};
        } else {
           /** @constructor */
           let FooBar = function() {};
        }
        """);
  }

  @Test
  public void testAnonymousWithSuper() {
    enableClosurePass();
    ignoreWarnings(FunctionTypeBuilder.RESOLVED_TAG_EMPTY, TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);
    test(
        """
        goog.forwardDeclare('D')
        f(class extends D { f() { super.g() } })
        """,
        """
        goog.forwardDeclare('D')
        /** @constructor
         */
        const CLASS_DECL$0 = function() {
          return D.apply(this, arguments) || this;
        };
        $jscomp.inherits(CLASS_DECL$0, D);
        CLASS_DECL$0.prototype.f = function() { D.prototype.g.call(this); };
        f(CLASS_DECL$0)
        """);
  }

  @Test
  public void testAnonymousWithSuper_reflectConstruct() {
    es6SubclassTranspilation = Es6SubclassTranspilation.SAFE_REFLECT_CONSTRUCT;
    enableClosurePass();
    ignoreWarnings(FunctionTypeBuilder.RESOLVED_TAG_EMPTY, TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);
    test(
        """
        goog.forwardDeclare('D')
        f(class extends D { f() { super.g() } })
        """,
        """
        goog.forwardDeclare('D')
        /** @constructor
         */
        const CLASS_DECL$0 = function() {
          return $jscomp.construct(D, arguments, this.constructor);
        };
        $jscomp.inherits(CLASS_DECL$0, D);
        CLASS_DECL$0.prototype.f = function() { D.prototype.g.call(this); };
        f(CLASS_DECL$0)
        """);
  }

  @Test
  public void testComplexBaseWithConditional() {
    test(
        """
        /** @define {boolean} */
        const COND = goog.define('COND', false);
        class Base1 {}
        class Base2 extends Base1 {}
        /** @type {typeof Base1} */
        const ActualBase = COND ? Base1 : Base2;
        class Sub extends ActualBase {}
        """,
        """
        /** @define {!JSDocSerializer_placeholder_type} */
        const COND = goog.define('COND', false);
        /** @constructor */ let Base1 = function() {
        };
        /**
         * @constructor
         */
        let Base2 = function() {
          Base1.apply(this, arguments);
        };
        $jscomp.inherits(Base2, Base1);
        const ActualBase = COND ? Base1 : Base2;
        /**
         * @constructor
         */
        let Sub = function() {
          ActualBase.apply(this, arguments);
        };
        $jscomp.inherits(Sub, ActualBase);
        """);
  }

  @Test
  public void testClassWithNoJsDoc() {
    test("class C { }", "/** @constructor */ let C = function() { };");
  }

  @Test
  public void testClassWithSuppressJsDoc() {
    test(
        "/** @suppress {partialAlias} */ class C { }",
        "/** @constructor @suppress {partialAlias} */ let C = function() {};");
  }

  @Test
  public void testInterfaceWithJsDoc() {
    enableClosurePass();
    test(
        """
        goog.forwardDeclare('X');
        goog.forwardDeclare('Y');
        /**
         * Converts Xs to Ys.
         * @interface
         */
        class Converter {
          /**
           * @param {X} x
           * @return {Y}
           */
          convert(x) {}
        }
        """,
        """
        goog.forwardDeclare('X');
        goog.forwardDeclare('Y');
        /**
         * Converts Xs to Ys.
         * @interface
         */
        let Converter = function() { };

        Converter.prototype.convert = function(x) {};
        """);
  }

  @Test
  public void testNoTypeForParam() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        """
        class MdMenu {
          /**
           * @param c
           */
          set classList(c) {}
        }
        """,
        """
        /** @constructor */
        let MdMenu=function(){};
        $jscomp.global.Object.defineProperties(
            MdMenu.prototype,
            {
              classList: {
                configurable:true,
                enumerable:true,
                set:function(c){}
              }
            })
        """);
  }

  @Test
  public void testParamNameMismatch() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    ignoreWarnings(FunctionTypeBuilder.INEXISTENT_PARAM);

    test(
        """
        class MdMenu {
        /**
        * @param {boolean} classes
        */
        set classList(c) {}
        }
        """,
        """
        /** @constructor */
        let MdMenu=function(){};
        $jscomp.global.Object.defineProperties(
            MdMenu.prototype,
            {
              classList: {
                configurable:true,
                enumerable:true,
               set:function(c){}
             }
           })
        """);
  }

  @Test
  public void testParamNameMismatchAndNoParamType() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    ignoreWarnings(FunctionTypeBuilder.INEXISTENT_PARAM);

    test(
        """
        class MdMenu {
        /**
        * @param classes
        */
        set classList(c) {}
        }
        """,
        """
        /** @constructor */
        let MdMenu=function(){};
        $jscomp.global.Object.defineProperties(
            MdMenu.prototype,
            {
              classList: {
                configurable:true,
                enumerable:true,
                set:function(c){}
              }
           })
        """);
  }

  @Test
  public void testRecordWithJsDoc() {
    enableClosurePass();
    test(
        """
        goog.forwardDeclare('X');
        goog.forwardDeclare('Y');
        /**
         * @record
         */
        class Converter {
          /**
           * @param {X} x
           * @return {Y}
           */
          convert(x) {}
        }
        """,
        """
        goog.forwardDeclare('X');
        goog.forwardDeclare('Y');
        /**
         * @interface
         */
        let Converter = function() { };

        Converter.prototype.convert = function(x) {};
        """);
  }

  @Test
  public void testMemberWithJsDoc() {
    test(
        "class C { /** @param {boolean} b */ foo(b) {} }",
        """
        /**
         * @constructor
         */
        let C = function() {};

        C.prototype.foo = function(b) {};
        """);
  }

  @Test
  public void testClassStatementInsideIf() {
    test("if (foo) { class C { } }", "if (foo) { /** @constructor */ let C = function() {}; }");
  }

  @Test
  public void testClassStatementInsideBlocklessIf() {
    test("if (foo) class C {}", "if (foo) { /** @constructor */ let C = function() {}; }");
  }

  /** Class expressions that are the RHS of a 'var' statement. */
  @Test
  public void testClassExpressionInVar() {
    test("var C = class { }", "/** @constructor */ var C = function() {}");
  }

  @Test
  public void testClassExpressionInVarWithMethod() {
    test(
        "var C = class { foo() {} }",
        """
        /** @constructor */ var C = function() {}

        C.prototype.foo = function() {}
        """);
  }

  @Test
  public void testNamedClassExpressionInVar() {
    test(
        "var C = class C { }",
        """
        /** @constructor */
        var C = function() {};
        """);
  }

  @Test
  public void testNamedClassExpressionInVarWithMethod() {
    test(
        "var C = class C { foo() {} }",
        """
        /** @constructor */
        var C = function() {};
        C.prototype.foo = function() {};
        """);
  }

  /** Class expressions that are the RHS of an assignment. */
  @Test
  public void testClassExpressionInAssignment() {
    test(
        """
        /** @const */ goog.example = {};
        goog.example.C = class { }
        """,
        """
        /** @const */ goog.example = {};
        /** @constructor */ goog.example.C = function() {}
        """);
  }

  @Test
  public void testClassExpressionInAssignmentWithMethod() {
    test(
        """
        /** @const */ goog.example = {};
        goog.example.C = class { foo() {} }
        """,
        """
        /** @const */ goog.example = {};
        /** @constructor */ goog.example.C = function() {}
        goog.example.C.prototype.foo = function() {};
        """);
  }

  @Test
  public void testClassExpressionInAssignment_getElem() {
    test(
        "window['MediaSource'] = class {};",
        """
        /** @constructor */
        const CLASS_DECL$0 = function() {};
        window['MediaSource'] = CLASS_DECL$0;
        """);
  }

  @Test
  public void testClassExpressionImmediatelyInstantiated() {
    test(
        "var C = new (class {})();",
        """
        /** @constructor */
        const CLASS_DECL$0=function(){};
        var C=new CLASS_DECL$0
        """);
  }

  @Test
  public void testClassExpressionAssignedToComplexLhs() {
    test(
        "(condition ? obj1 : obj2).prop = class C { };",
        """
        /** @constructor */
        const CLASS_DECL$0 = function(){};
        (condition ? obj1 : obj2).prop = CLASS_DECL$0;
        """);
  }

  @Test
  public void testClassExpression_cannotConvert() {
    test(
        "var C = new (foo || (foo = class { }))();",
        """
        var C = new (foo || (foo = (() => {
          /** @constructor */
          const CLASS_DECL$0 = function() {};
          return CLASS_DECL$0;
        })()))();
        """);
  }

  @Test
  public void testClassExpressionInDefaultParamValue_canConvert() {
    test(
        "function bar(foo = class { }) {};",
        """
        function bar(foo = (() => {
          /** @constructor */ const CLASS_DECL$0 = function() {};
          return CLASS_DECL$0; })()) {};
        """);
  }

  @Test
  public void testExtendsWithImplicitConstructor() {
    test(
        "class D {} class C extends D {}",
        """
        /** @constructor */
        let D = function() {};
        /** @constructor
         */
        let C = function() { D.apply(this, arguments); };
        $jscomp.inherits(C, D);
        """);
  }

  @Test
  public void testExtendsWithImplicitConstructorNoTypeInfo() {
    disableTypeCheck();
    disableTypeInfoValidation();
    test(
        srcs("class D {} class C extends D {}"),
        expected(
            """
            /** @constructor */
            let D = function() {};
            /** @constructor
             */
            let C = function() {
            // even without type information we recognize `arguments` and avoid generating
            // `[...arguments]`
              D.apply(this, arguments);
            };
            $jscomp.inherits(C, D);
            """));
  }

  @Test
  public void testExtendsWithImplicitConstructor_knownSuperclass_doesNotEs6SubclassTranspilation() {
    es6SubclassTranspilation = Es6SubclassTranspilation.SAFE_REFLECT_CONSTRUCT;
    test(
        "class D {} class C extends D {}",
        """
        /** @constructor */
        let D = function() {};
        /** @constructor
         */
        let C = function() { D.apply(this, arguments); };
        $jscomp.inherits(C, D);
        """);
  }

  @Test
  public void testExtendsWithSpreadsPassedToSuper() {
    test(
        """
        class D {
          /** @param {...string|number} args*/
          constructor(...args) {
            /** @type {!Arguments} */
            this.args = arguments;
          }
        }
        class C extends D {
          /**
           * @param {!Array<number>} numbers
           * @param {!Array<string>} strings
           */
          constructor(numbers, strings) {
            super(...numbers, ...strings);
          }
        }
        """,
        """
        /**
         * @constructor
         */
        let D = function(...args) {
          this.args = arguments;
        };
        /**
         * @constructor
         */
        let C = function(numbers, strings) {
          D.apply(this, [...numbers, ...strings]);
        };
        $jscomp.inherits(C, D);
        """);
  }

  @Test
  public void testExtendsWithExplicitSuperCall() {
    test(
        "class D {} class C extends D { constructor() { super(); } }",
        """
        /** @constructor */
        let D = function() {};
        /** @constructor */
        let C = function() {
          D.call(this);
        }
        $jscomp.inherits(C, D);
        """);
  }

  @Test
  public void testExtendsWithExplicitSuperCallWithArguments() {
    test(
        """
        class D { constructor(strArg) {} }
        class C extends D { constructor(word) { super(word); } }
        """,
        """
        /** @constructor */
        let D = function(strArg) {};
        /** @constructor */
        let C = function(word) {
          D.call(this, word);
        }
        $jscomp.inherits(C, D);
        """);
  }

  @Test
  public void testExtends_nonGlobalClass() {
    test(
        srcs(
            """
            function f() {
              class C {}
              class D extends C {}
            }
            """),
        expected(
            """
            function f() {
              /** @constructor */
              let C = function() {};
              /** @constructor */
              let D = function() {
                return C.apply(this, arguments) || this;
              };
              $jscomp.inherits(D, C);
            }
            """));
  }

  @Test
  public void testExtends_nonGlobalClass_usingReflectConstruct() {
    es6SubclassTranspilation = Es6SubclassTranspilation.SAFE_REFLECT_CONSTRUCT;
    test(
        srcs(
            """
            function f() {
              class C {}
              class D extends C {}
            }
            """),
        expected(
            """
            function f() {
              /** @constructor */
              let C = function() {};
              /** @constructor */
              let D = function() {
                return $jscomp.construct(C, arguments, this.constructor);
              };
              $jscomp.inherits(D, C);
            }
            """));
  }

  @Test
  public void testExtendsExternsClass() {
    test(
        externs(EXTERNS_BASE, "const ns = {}; /** @constructor */ ns.D = function() {};"),
        srcs("class C extends ns.D { }"),
        expected(
            """
            /** @constructor
             */
            let C = function() {
             return ns.D.apply(this, arguments) || this;
            };
            $jscomp.inherits(C, ns.D);
            """));
  }

  @Test
  public void testExtendForwardDeclaredClass() {
    enableClosurePass();
    testWarning(
        """
        goog.forwardDeclare('ns.D');
        class C extends ns.D { }
        """,
        TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);
  }

  @Test
  public void testImplementForwardDeclaredInterface() {
    ignoreWarnings(TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);
    enableClosurePass();
    testWarning(
        """
        goog.forwardDeclare('ns.D');
        /** @implements {ns.D} */
        class C { }
        """,
        FunctionTypeBuilder.RESOLVED_TAG_EMPTY);
  }

  @Test
  public void testExtendForwardDeclaredInterface() {
    ignoreWarnings(TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);
    enableClosurePass();
    testWarning(
        """
        goog.forwardDeclare('ns.D');
        /** @interface @extends {ns.D} */ class C { }
        """,
        FunctionTypeBuilder.RESOLVED_TAG_EMPTY);
  }

  @Test
  public void testExtendNonNativeErrorWithAutogeneratedConstructor() {
    test(
        """
        class Error {
          /** @param {string} msg */
          constructor(msg) {
            /** @const */ this.message = msg;
          }
        }
        class C extends Error {}
        """, // autogenerated constructor
        """
        /** @constructor */
        let Error = function(msg) {
          /** @const */ this.message = msg;
        };
        /** @constructor
         */
        let C = function() { Error.apply(this, arguments); };
        $jscomp.inherits(C, Error);
        """);
  }

  @Test
  public void testExtendNonNativeErrorWithExplicitSuperCall() {
    test(
        """
        class Error {
          /** @param {string} msg */
          constructor(msg) {
            /** @const */ this.message = msg;
          }
        }
        class C extends Error {
          constructor() {
            super('C error'); // explicit super() call
          }
        }
        """,
        """
        /** @constructor */
        let Error = function(msg) {
          /** @const */ this.message = msg;
        };
        /** @constructor
         */
        let C = function() { Error.call(this, 'C error'); };
        $jscomp.inherits(C, Error);
        """);
  }

  @Test
  public void testExtendNativeErrorWithAutogeneratedConstructor() {
    ignoreWarnings(RhinoErrorReporter.UNRECOGNIZED_TYPE_ERROR);
    test(
        "class C extends Error {}", // autogenerated constructor
"""
/** @constructor
 */
let C = function() {
  var $jscomp$tmp$error$m1146332801$1;
  $jscomp$tmp$error$m1146332801$1 = Error.apply(this, arguments),
      this.message = $jscomp$tmp$error$m1146332801$1.message,
      ('stack' in $jscomp$tmp$error$m1146332801$1) && (this.stack = $jscomp$tmp$error$m1146332801$1.stack),
      this;
};
$jscomp.inherits(C, Error);
""");
  }

  @Test
  public void testExtendNativeAggregateErrorWithAutogeneratedConstructor() {
    ignoreWarnings(RhinoErrorReporter.UNRECOGNIZED_TYPE_ERROR);
    test(
        "class C extends AggregateError {}", // autogenerated constructor
"""
/** @constructor
 */
let C = function() {
  var $jscomp$tmp$error$m1146332801$1;
  $jscomp$tmp$error$m1146332801$1 = AggregateError.apply(this, arguments),
      this.message = $jscomp$tmp$error$m1146332801$1.message,
      ('stack' in $jscomp$tmp$error$m1146332801$1) && (this.stack = $jscomp$tmp$error$m1146332801$1.stack),
      this;
};
$jscomp.inherits(C, AggregateError);
""");
  }

  @Test
  public void testExtendNativeErrorExplicitSuperCall() {
    ignoreWarnings(RhinoErrorReporter.UNRECOGNIZED_TYPE_ERROR);
    test(
        """
        class C extends Error {
          constructor() {
            var self = super('C error') || this; // explicit super() call in an expression
          }
        }
        """,
"""
/** @constructor
 */
let C = function() {
  var $jscomp$tmp$error$m1146332801$1;
  var self =
      ($jscomp$tmp$error$m1146332801$1 = Error.call(this, 'C error'),
          this.message = $jscomp$tmp$error$m1146332801$1.message,
          ('stack' in $jscomp$tmp$error$m1146332801$1) && (this.stack = $jscomp$tmp$error$m1146332801$1.stack),
          this)
      || this;
};
$jscomp.inherits(C, Error);
""");
  }

  @Test
  public void testExtendNativeAggregateErrorExplicitSuperCall() {
    ignoreWarnings(RhinoErrorReporter.UNRECOGNIZED_TYPE_ERROR);
    test(
        """
        class C extends AggregateError {
          constructor() {
            var self = super([new Error('msg')]) || this; // explicit super() call in an
        // expression
          }
        }
        """,
"""
/** @constructor
 */
let C = function() {
  var $jscomp$tmp$error$m1146332801$1;
  var self =
      ($jscomp$tmp$error$m1146332801$1 = AggregateError.call(this, [new Error('msg')]),
          this.message = $jscomp$tmp$error$m1146332801$1.message,
          ('stack' in $jscomp$tmp$error$m1146332801$1) && (this.stack = $jscomp$tmp$error$m1146332801$1.stack),
          this)
      || this;
};
$jscomp.inherits(C, AggregateError);
""");
  }

  @Test
  public void testDynamicExtends_callWithoutJSDoc() {
    testWarning("class C extends foo() {}", DYNAMIC_EXTENDS_WITHOUT_JSDOC);
  }

  @Test
  public void testDynamicExtends_callWithJSDoc() {
    test(
        "/** @extends {Object} */ class C extends foo() {}",
        """
        const CLASS_EXTENDS$0 = foo();
        /** @constructor */
        let C = function() {
          return CLASS_EXTENDS$0.apply(this, arguments) || this;
        };
        $jscomp.inherits(C, CLASS_EXTENDS$0);
        """);
  }

  @Test
  public void testDynamicExtends_functionExpression_suppressCheckTypes() {
    test(
        "/** @suppress {checkTypes} */ class C extends function(){} {}",
        """
        const CLASS_EXTENDS$0 = function() {};
        /** @constructor */
        let C = function() {
          CLASS_EXTENDS$0.apply(this, arguments);
        };
        $jscomp.inherits(C, CLASS_EXTENDS$0);
        """);
  }

  @Test
  public void testDynamicExtends_logicalExpressionWithJSDoc() {
    test(
        "class A {} class B {} /** @extends {Object} */ class C extends (foo ? A : B) {}",
        """
        /** @constructor */
        let A = function() {};
        /** @constructor */
        let B = function() {};
        const CLASS_EXTENDS$0 = foo ? A : B;

        /** @constructor */
        let C = function() {
          CLASS_EXTENDS$0.apply(this, arguments);
        };
        $jscomp.inherits(C, CLASS_EXTENDS$0);
        """);
  }

  @Test
  public void testExtendsInterface() {
    test(
        """
        /** @interface */
        class D {
          f() {}
        }
        /** @interface */
        class C extends D {
          g() {}
        }
        """,
        """
        /** @interface */
        let D = function() {};
        D.prototype.f = function() {};
        /** @interface */
        let C = function() {};
        $jscomp.inherits(C,D);
        C.prototype.g = function() {};
        """);
  }

  @Test
  public void testExtendsRecord() {
    test(
        """
        /** @record */
        class D {
          f() {}
        }
        /** @record */
        class C extends D {
          g() {}
        }
        """,
        """
        /** @interface */
        let D = function() {};
        D.prototype.f = function() {};
        /**
         * @interface */
        let C = function() {};
        $jscomp.inherits(C,D);
        C.prototype.g = function() {};
        """);
  }

  @Test
  public void testImplementsInterface() {
    test(
        """
        /** @interface */
        class D {
          f() {}
        }
        /** @implements {D} */
        class C {
          f() {console.log('hi');}
        }
        """,
        """
        /** @interface */
        let D = function() {};
        D.prototype.f = function() {};
        /** @constructor */
        let C = function() {};
        C.prototype.f = function() {console.log('hi');};
        """);
  }

  @Test
  public void testSuperCall() {
    test(
        """
        class D {}
        class C extends D {
          constructor() { SUPER: super(); }
        }
        """,
        """
        /** @constructor */
        let D = function() {};
        /** @constructor */
        let C = function() {
          SUPER: { D.call(this); }
        }
        $jscomp.inherits(C, D);
        """);

    Color dType = getNodeWithName(getLastCompiler().getJsRoot(), "D").getColor();
    Color cType = getNodeWithName(getLastCompiler().getJsRoot(), "C").getColor();

    // D.call(this);
    Node callNode =
        getNodeMatchingLabel(getLastCompiler().getJsRoot(), "SUPER").getOnlyChild().getOnlyChild();
    assertNode(callNode).hasToken(Token.CALL);
    assertNode(callNode).hasColorThat().isEqualTo(StandardColors.NULL_OR_VOID);

    // D.call
    Node callee = callNode.getFirstChild();
    assertNode(callee).matchesQualifiedName("D.call");
    assertNode(callee).hasColorThat().isEqualTo(StandardColors.TOP_OBJECT);

    // D
    Node superDotGReplacement = callee.getFirstChild();
    assertNode(superDotGReplacement).matchesQualifiedName("D");
    assertNode(superDotGReplacement).hasColorThat().isEqualTo(dType);

    // `this` node from `D.call(this)`
    Node thisNode = callee.getNext();
    assertNode(thisNode).hasToken(Token.THIS);
    assertThat(cType.getInstanceColors()).containsExactly(thisNode.getColor());
  }

  @Test
  public void testSuperCall_withArgument() {
    ignoreWarnings(TypeCheck.WRONG_ARGUMENT_COUNT);
    test(
        "class D {} class C extends D { constructor(string) { super(string); } }",
        """
        /** @constructor */
        let D = function() {}
        /** @constructor */
        let C = function(string) {
          D.call(this,string);
        }
        $jscomp.inherits(C, D);
        """);
  }

  @Test
  public void testSuperCall_withArgumentAndSubsequentCode() {
    ignoreWarnings(TypeCheck.WRONG_ARGUMENT_COUNT);
    test(
        "class D {} class C extends D { constructor(strArg, n) { super(strArg); this.n = n; } }",
        """
        /** @constructor */
        let D = function() {}
        /** @constructor */
        let C = function(strArg, n) {
          D.call(this,strArg);
          this.n = n;
        }
        $jscomp.inherits(C, D);
        """);
  }

  @Test
  public void testSuperMethodCall() {
    ignoreWarnings(TypeCheck.INEXISTENT_PROPERTY);
    test(
        """
        class D {}
        class C extends D {
          constructor() { }
          foo() { return super.foo(); }
        }
        """,
        """
        /** @constructor */
        let D = function() {}
        /** @constructor */
        let C = function() { }
        $jscomp.inherits(C, D);
        C.prototype.foo = function() {
          return D.prototype.foo.call(this);
        }
        """);
  }

  @Test
  public void testSuperMethodCall_withArgument() {
    ignoreWarnings(TypeCheck.INEXISTENT_PROPERTY);
    test(
        """
        class D {}
        class C extends D {
          constructor() {}
          foo(bar) { return super.foo(bar); }
        }
        """,
        """
        /** @constructor */
        let D = function() {}
        /** @constructor */
        let C = function() {};
        $jscomp.inherits(C, D);
        C.prototype.foo = function(bar) {
          return D.prototype.foo.call(this, bar);
        }
        """);
  }

  @Test
  public void testSuperCall_inSubclassInsideMethod() {
    test(
        "class C { method() { class D extends C { constructor() { super(); }}}}",
        """
        /** @constructor */
        let C = function() {}
        C.prototype.method = function() {
          /** @constructor */
          let D = function() {
            C.call(this);
          }
          $jscomp.inherits(D, C);
        };
        """);
  }

  @Test
  public void testSuperKnownNotToChangeThis() {
    test(
        """
        class D {
          /** @param {string} word */
          constructor(word) {
            this.str = word;
            /** @type {?} */ this.n;
            return; // Empty return should not trigger this-changing behavior.
          }
        }
        class C extends D {
          /**
           * @param {string} strArg
           * @param {number} n
           */
          constructor(strArg, n) {
        // This is nuts, but confirms that super() used in an expression works.
            super(strArg).n = n;
        // Also confirm that an existing empty return is handled correctly.
            return;
          }
        }
        """,
        """
        /**
         * @constructor
         */
        let D = function(word) {
          this.str = word;
          this.n;
          return;
        }
        /**
         * @constructor
         */
        let C = function(strArg, n) {
          (D.call(this,strArg), this).n = n; // super() returns `this`.
          return;
        }
        $jscomp.inherits(C, D);
        """);
  }

  @Test
  public void testSuperMightChangeThis() {
    // Class D is unknown, so we must assume its constructor could change `this`.
    enableClosurePass();
    ignoreWarnings(FunctionTypeBuilder.RESOLVED_TAG_EMPTY);
    test(
        """
        goog.forwardDeclare('D');
        class C extends D {
          constructor(strArg, n) {
        // This is nuts, but confirms that super() used in an expression works.
            super(strArg).n = n;
        // Also confirm that an existing empty return is handled correctly.
            return;
          }
        }
        """,
        """
        goog.forwardDeclare('D');
        /** @constructor */
        let C = function(strArg, n) {
          var $jscomp$super$this$m1146332801$0;
          ($jscomp$super$this$m1146332801$0 = D.call(this,strArg) || this).n = n;
          return $jscomp$super$this$m1146332801$0; // Duplicate because of existing return
        // statement.
          return $jscomp$super$this$m1146332801$0;
        }
        $jscomp.inherits(C, D);
        """);
  }

  @Test
  public void testAlternativeSuperCalls_withExplicitSuperclassCtor() {
    test(
        """
        class D {
          /** @param {string} nameStr */
          constructor(nameStr) {
            this.name = nameStr;
          }
        }
        class C extends D {
          /** @param {string} strArg
           * @param {number} n */
          constructor(strArg, n) {
            if (n >= 0) {
              super('positive: ' + strArg);
            } else {
              super('negative: ' + strArg);
            }
            this.n = n;
          }
        }
        """,
        """
        /** @constructor */
        let D = function(nameStr) {
          this.name = nameStr;
        }
        /** @constructor */
        let C = function(strArg, n) {
          if (n >= 0) {
            D.call(this, 'positive: ' + strArg);
          } else {
            D.call(this, 'negative: ' + strArg);
          }
          this.n = n;
        }
        $jscomp.inherits(C, D);
        """);
  }

  @Test
  public void testAlternativeSuperCalls_withUnknkownSuperclass() {
    // Class being extended is unknown, so we must assume super() could change the value of `this`.
    enableClosurePass();
    ignoreWarnings(FunctionTypeBuilder.RESOLVED_TAG_EMPTY);
    test(
        """
        goog.forwardDeclare('D');
        class C extends D {
          /** @param {string} strArg
           * @param {number} n */
          constructor(strArg, n) {
            if (n >= 0) {
              super('positive: ' + strArg);
            } else {
              super('negative: ' + strArg);
            }
            this.n = n;
          }
        }
        """,
        """
        goog.forwardDeclare('D');
        /** @constructor */
        let C = function(strArg, n) {
          var $jscomp$super$this$m1146332801$0;
          if (n >= 0) {
            $jscomp$super$this$m1146332801$0 = D.call(this, 'positive: ' + strArg) || this;
          } else {
            $jscomp$super$this$m1146332801$0 = D.call(this, 'negative: ' + strArg) || this;
          }
          $jscomp$super$this$m1146332801$0.n = n;
          return $jscomp$super$this$m1146332801$0;
        }
        $jscomp.inherits(C, D);
        """);
  }

  @Test
  public void testAlternativeSuperCalls_withUnknownSuperclass_es6SubclassTranspilation() {
    // Class being extended is unknown, so we must assume super() could change the value of `this`.
    enableClosurePass();
    ignoreWarnings(FunctionTypeBuilder.RESOLVED_TAG_EMPTY);
    es6SubclassTranspilation = Es6SubclassTranspilation.SAFE_REFLECT_CONSTRUCT;
    test(
        """
        goog.forwardDeclare('D');
        class C extends D {
          /** @param {string} strArg
           * @param {number} n */
          constructor(strArg, n) {
            if (n >= 0) {
              super('positive: ' + strArg);
            } else {
              super('negative: ' + strArg);
            }
            this.n = n;
          }
        }
        """,
        """
        goog.forwardDeclare('D');
        /** @constructor */
        let C = function(strArg, n) {
          var $jscomp$super$this$m1146332801$0;
          if (n >= 0) {
            $jscomp$super$this$m1146332801$0 =
                $jscomp.construct(D, ["positive: " + strArg], this.constructor);
          } else {
            $jscomp$super$this$m1146332801$0 =
                $jscomp.construct(D, ["negative: " + strArg], this.constructor);
          }
          $jscomp$super$this$m1146332801$0.n = n;
          return $jscomp$super$this$m1146332801$0;
        }
        $jscomp.inherits(C, D);
        """);
  }

  @Test
  public void testComputedSuper() {
    test(
        """
        /** @unrestricted */
        class Foo {
          ['m']() { return 1; }
        }

        /** @unrestricted */
        class Bar extends Foo {
          ['m']() {
            RETURN: return super['m']() + 1;
          }
        }
        """,
        """
        /** @constructor */
        let Foo = function() {};
        Foo.prototype['m'] = function() { return 1; };
        /** @constructor */
        let Bar = function() { Foo.apply(this, arguments); };
        $jscomp.inherits(Bar, Foo);
        Bar.prototype['m'] = function () {
          RETURN: { return Foo.prototype['m'].call(this) + 1; }
        };
        """);

    Color fooType = getNodeWithName(getLastCompiler().getJsRoot(), "Foo").getColor();
    Color barType = getNodeWithName(getLastCompiler().getJsRoot(), "Bar").getColor();

    // Foo.prototype['m'].call(this)
    Node callNode =
        getNodeMatchingLabel(getLastCompiler().getJsRoot(), "RETURN")
            .getFirstFirstChild()
            .getFirstChild();
    assertNode(callNode).hasToken(Token.CALL);
    assertNode(callNode).hasColorThat().isEqualTo(StandardColors.UNKNOWN);

    // Foo.prototype['m'].call
    Node callee = callNode.getFirstChild();
    assertNode(callee).hasToken(Token.GETPROP);
    assertNode(callee).hasColorThat().isEqualTo(StandardColors.UNKNOWN);

    // Foo.prototype['m']
    Node property = callee.getFirstChild();
    assertNode(property).hasToken(Token.GETELEM);
    assertNode(callee).hasColorThat().isEqualTo(StandardColors.UNKNOWN);

    // Foo.prototype
    Node prototype = property.getFirstChild();
    assertNode(prototype).matchesQualifiedName("Foo.prototype").hasOriginalName("super");
    assertThat(fooType.getPrototypes()).containsExactly(prototype.getColor());

    // Foo
    Node superDotGReplacement = prototype.getFirstChild();
    assertNode(superDotGReplacement).matchesQualifiedName("Foo");
    assertNode(superDotGReplacement).hasColorThat().isEqualTo(fooType);

    // `this` node from `Foo.prototype['m'].call(this)`
    Node thisNode = callee.getNext();
    assertNode(thisNode).hasToken(Token.THIS);
    assertThat(barType.getInstanceColors()).containsExactly(thisNode.getColor());
  }

  @Test
  public void testSuperMethodInGetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        """
        class Base {
          method() {
            return 5;
          }
        }

        class Subclass extends Base {
          constructor() {
            super();
          }

          get x() {
            return super.method();
          }
        }
        """,
        """
        /** @constructor */
        let Base = function() {};
        Base.prototype.method = function() { return 5; };

        /** @constructor */
        let Subclass = function() { Base.call(this); };

        $jscomp.inherits(Subclass, Base);
        $jscomp.global.Object.defineProperties(Subclass.prototype, {
          x: {
            configurable:true,
            enumerable:true,
            get: function() { return Base.prototype.method.call(this); },
          }
        });
        """);
  }

  @Test
  public void testOverrideOfGetterFromInterface() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        """
        /** @interface */
        class Int {
          /** @return {number} */
          get x() {}
        }

        /** @implements {Int} */
        class C {
          /** @override @return {number} */
          get x() {}
        }
        """,
        """
        /** @interface */
        let Int = function() {};
        $jscomp.global.Object.defineProperties(Int.prototype, {
          x: {
            configurable:true,
            enumerable:true,
            get: function() {},
          }
        });

        /** @constructor */
        let C = function() {};

        $jscomp.global.Object.defineProperties(C.prototype, {
          x: {
            configurable:true,
            enumerable:true,
            get: function() {},
          }
        });
        """);
  }

  @Test
  public void testSuperMethodInSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        """
        class Base {
          constructor() {}
          method() {
            var x = 5;
          }
        }

        class Subclass extends Base {
          constructor() {
            super();
          }

          set x(num) {
            super.method();
          }
        }
        """,
        """
        /** @constructor */
        let Base = function() {};
        Base.prototype.method = function() { var x = 5; };

        /** @constructor */
        let Subclass = function() { Base.call(this); };

        $jscomp.inherits(Subclass, Base);
        $jscomp.global.Object.defineProperties(Subclass.prototype, {
          x: {
            configurable:true,
            enumerable:true,
            set: function(num) { Base.prototype.method.call(this); }
          }
        });
        """);
  }

  @Test
  public void testExtendNativeClass_withExplicitConstructor() {
    // Function and other native classes cannot be correctly extended in transpiled form.
    // Test both explicit and automatically generated constructors.
    test(
        """
        class FooPromise extends Promise {
          /** @param {string} msg */
          constructor(callbackArg, msg) {
            super(callbackArg);
            this.msg = msg;
          }
        }
        """,
"""
/**
 * @constructor
 */
let FooPromise = function(callbackArg, msg) {
  var $jscomp$super$this$m1146332801$0;
  $jscomp$super$this$m1146332801$0 = $jscomp.construct(Promise, [callbackArg], this.constructor)
  $jscomp$super$this$m1146332801$0.msg = msg;
  return $jscomp$super$this$m1146332801$0;
}
$jscomp.inherits(FooPromise, Promise);

""");
  }

  @Test
  public void testExtendNativeClass_withExplicitConstructor_withInnerFunction() {
    test(
        """
        class FooPromise extends Promise {
          /** @param {string} msg */
          constructor(callbackArg, msg) {
            function inner() {} // hoisted, normalized
            super(callbackArg);
            this.msg = msg;
          }
        }
        """,
        """
        /**
         * @constructor
         */
        let FooPromise = function(callbackArg, msg) {
        // stays hoisted
          function inner() {}
        // declaration created after function declaration
          var $jscomp$super$this$m1146332801$0;
          $jscomp$super$this$m1146332801$0 =
              $jscomp.construct(Promise, [callbackArg], this.constructor);
          $jscomp$super$this$m1146332801$0.msg = msg;
          return $jscomp$super$this$m1146332801$0;
        }
        $jscomp.inherits(FooPromise, Promise);
        """);
  }

  @Test
  public void testExtendNativeClass_withImplicitConstructor() {
    test(
        "class FooPromise extends Promise {}",
        """
        /**
         * @constructor
         */
        let FooPromise = function() {
          return $jscomp.construct(Promise, arguments, this.constructor)
        }
        $jscomp.inherits(FooPromise, Promise);
        """);
  }

  @Test
  public void testExtendObject_replaceSuperCallWithThis() {
    // Object can be correctly extended in transpiled form, but we don't want or need to call
    // the `Object()` constructor in place of `super()`. Just replace `super()` with `this` instead.
    // Test both explicit and automatically generated constructors.
    test(
        """
        class Foo extends Object {
          /** @param {string} msg */
          constructor(msg) {
            super();
            this.msg = msg;
          }
        }
        """,
        """
        /**
         * @constructor
         */
        let Foo = function(msg) {
          this; // super() replaced with its return value
          this.msg = msg;
        };
        $jscomp.inherits(Foo, Object);
        """);
  }

  @Test
  public void testExtendObject_withImplicitConstructor() {
    test(
        "class Foo extends Object {}",
        """
        /**
         * @constructor
         */
        let Foo = function() {
          this; // super.apply(this, arguments) replaced with its return value
        };
        $jscomp.inherits(Foo, Object);
        """);
  }

  @Test
  public void testExtendNonNativeObject_withSuperCall() {
    // No special handling when Object is redefined.
    ignoreWarnings(TypeCheck.ES5_CLASS_EXTENDING_ES6_CLASS, FunctionTypeBuilder.TYPE_REDEFINITION);
    test(
        externs("/** @const */ var $jscomp = {}; $jscomp.inherits = function (a, b) {};"),
        srcs(
            """
            class Object {}
            class Foo extends Object {
              /** @param {string} msg */
              constructor(msg) {
                super();
                this.msg = msg;
              }
            }
            """),
        expected(
            """
            /**
             * @constructor
             */
            let Object = function() {
            };
            /**
             * @constructor
             */
            let Foo = function(msg) {
              Object.call(this);
              this.msg = msg;
            };
            $jscomp.inherits(Foo, Object);
            """));
  }

  @Test
  public void testExtendNonNativeObject_withImplicitConstructor() {
    ignoreWarnings(TypeCheck.ES5_CLASS_EXTENDING_ES6_CLASS, FunctionTypeBuilder.TYPE_REDEFINITION);
    test(
        externs("/** @const */ var $jscomp = {}; $jscomp.inherits = function (a, b) {};"),
        srcs(
            """
            class Object {}
            class Foo extends Object {}
            """), // autogenerated constructor
        expected(
            """
            /**
             * @constructor
             */
            let Object = function() {
            };
            /**
             * @constructor
             */
            let Foo = function() {
              Object.apply(this, arguments); // all arguments passed on to super()
            };
            $jscomp.inherits(Foo, Object);
            """));
  }

  @Test
  public void testMultiNameClass_inVarDeclaration() {
    test(
        "var F = class G {}",
        """
        /** @constructor */
        var F = function() {};
        """);
  }

  @Test
  public void testMultiNameClass_inAssignment() {
    test(
        "F = class G {}",
        """
        /** @constructor */
        F = function() {};
        """);
  }

  @Test
  public void testClassNested() {
    test(
        "class C { f() { class D {} } }",
        """
        /** @constructor */
        let C = function() {};
        C.prototype.f = function() {
          /** @constructor */
          let D = function() {}
        };
        """);
  }

  @Test
  public void testClassNestedWithSuperclass() {
    test(
        "class C { f() { class D extends C {} } }",
        """
        /** @constructor */
        let C = function() {};
        C.prototype.f = function() {
          /** @constructor */
          let D = function() {
            C.apply(this, arguments);
          };
          $jscomp.inherits(D, C);
        };
        """);
  }

  @Test
  public void testSuperGetProp() {
    test(
        "class D { d() {} } class C extends D { f() {var i = super.d;} }",
        """
        /** @constructor */
        let D = function() {};
        D.prototype.d = function() {};
        /** @constructor */
        let C = function() {
          D.apply(this, arguments);
        };
        $jscomp.inherits(C, D);
        C.prototype.f = function() {
          var i = D.prototype.d;
        };
        """);
  }

  @Test
  public void testSuperGetElem() {
    test(
        """
        /** @unrestricted */
        class D { ['d']() {} }
        /** @unrestricted */
        class C extends D { f() {var i = super['d'];} }
        """,
        """
        /** @constructor */
        let D = function() {};
        D.prototype['d'] = function() {};
        /** @constructor */
        let C = function() {
          D.apply(this, arguments);
        };
        $jscomp.inherits(C, D);
        C.prototype.f = function() {
          var i = D.prototype['d'];
        };
        """);
  }

  @Test
  public void testSuperGetPropInStaticMethod() {
    // NOTE: super.d refers to a *static* property D.d (which is not defined), rather than the
    // instance propery D.prototype.d, which is defined.
    ignoreWarnings(TypeCheck.INEXISTENT_PROPERTY);
    test(
        """
        class D { d() {} }
        class C extends D { static f() {var i = super.d;} }
        """,
        """
        /** @constructor */
        let D = function() {};
        D.prototype.d = function() {};
        /** @constructor */
        let C = function() {
          D.apply(this, arguments);
        };
        $jscomp.inherits(C, D);
        C.f = function() {
          var i = D.d;
        };
        """);
  }

  @Test
  public void testSuperGetElemInStaticMethod() {
    test(
        """
        /** @unrestricted */
        class D { ['d']() {}}
        /** @unrestricted */
        class C extends D { static f() {var i = super['d'];} }
        """,
        """
        /** @constructor */
        let D = function() {};
        D.prototype['d'] = function() {};
        /**
         * @constructor */
        let C = function() {
          D.apply(this, arguments);
        };
        $jscomp.inherits(C, D);
        C.f = function() {
          var i = D['d'];
        };
        """);
  }

  @Test
  public void testSuperGetProp_returnUndefinedProperty() {
    ignoreWarnings(TypeCheck.INEXISTENT_PROPERTY);
    test(
        """
        class D {}
        class C extends D { f() {return super.s;} }
        """,
        """
        /** @constructor */
        let D = function() {};
        /** @constructor */
        let C = function() {
          D.apply(this, arguments);
        };
        $jscomp.inherits(C, D);
        C.prototype.f = function() {
          return D.prototype.s;
        };
        """);
  }

  @Test
  public void testSuperGetProp_useUndefinedPropInCall() {
    ignoreWarnings(TypeCheck.INEXISTENT_PROPERTY);
    test(
        """
        class D {}
        class C extends D { f() { m(super.s);} }
        """,
        """
        /** @constructor */
        let D = function() {};
        /**  @constructor */
        let C = function() {
          D.apply(this, arguments);
        };
        $jscomp.inherits(C, D);
        C.prototype.f = function() {
          m(D.prototype.s);
        };
        """);
  }

  @Test
  public void testSuperGetProp_dereferenceUndefinedProperty() {
    ignoreWarnings(TypeCheck.INEXISTENT_PROPERTY);
    test(
        """
        class D {}
        class C extends D { foo() { return super.m.foo();} }
        """,
        """
        /** @constructor */
        let D = function() {};
        /** @constructor */
        let C = function() {
          D.apply(this, arguments);
        };
        $jscomp.inherits(C, D);
        C.prototype.foo = function() {
          return D.prototype.m.foo();
        };
        """);
  }

  @Test
  public void testSuperGet_dereferenceUndefinedPropertyInStaticMethod() {
    ignoreWarnings(TypeCheck.INEXISTENT_PROPERTY);
    test(
        """
        class D {}
        class C extends D { static foo() { return super.m.foo();} }
        """,
        """
        /** @constructor */
        let D = function() {};
        /** @constructor */
        let C = function() {
          D.apply(this, arguments);
        };
        $jscomp.inherits(C, D);
        C.foo = function() {
          return D.m.foo();
        };
        """);
  }

  @Test
  public void testStaticThis() {
    test(
        "class F { static f() { return this; } }",
        """
        /** @constructor */ let F = function() {}
        /** @this {?} */ F.f = function() { return this; };
        """);
  }

  @Test
  public void testStaticMethods() {
    test(
        "class C { static foo() {} }",
        "/** @constructor */ let C = function() {}; C.foo = function() {};");
  }

  @Test
  public void testStaticMethods_withSameNamedInstanceMethod() {
    test(
        "class C { static foo() {}; foo() {} }",
        """
        /** @constructor */
        let C = function() {};

        C.foo = function() {};

        C.prototype.foo = function() {};
        """);
  }

  @Test
  public void testStaticMethods_withCallInsideInstanceMethod() {
    test(
        "class C { static foo() {}; bar() { C.foo(); } }",
        """
        /** @constructor */
        let C = function() {};

        C.foo = function() {};

        C.prototype.bar = function() { C.foo(); };
        """);
  }

  @Test
  public void testStaticInheritance() {
    test(
        """
        class D {
          static f() {}
        }
        class C extends D { constructor() {} }
        C.f();
        """,
        """
        /** @constructor */
        let D = function() {};
        D.f = function () {};
        /** @constructor */
        let C = function() {};
        $jscomp.inherits(C, D);
        C.f();
        """);
  }

  @Test
  public void testStaticInheritance_withSameNamedInstanceMethod() {
    test(
        """
        class D {
          static f() {}
        }
        class C extends D {
          constructor() {}
          f() {}
        }
        C.f();
        """,
        """
        /** @constructor */
        let D = function() {};
        D.f = function() {};
        /** @constructor */
        let C = function() { };
        $jscomp.inherits(C, D);
        C.prototype.f = function() {};
        C.f();
        """);
  }

  @Test
  public void testStaticInheritance_overrideStaticMethod() {
    test(
        """
        class D {
          static f() {}
        }
        class C extends D {
          constructor() {}
          static f() {}
          g() {}
        }
        """,
        """
        /** @constructor */
        let D = function() {};
        D.f = function() {};
        /** @constructor */
        let C = function() { };
        $jscomp.inherits(C, D);
        C.f = function() {};
        C.prototype.g = function() {};
        """);
  }

  @Test
  public void testInheritFromExterns() {
    test(
        externs(
            EXTERNS_BASE
                + """
                /** @constructor */ function ExternsClass() {}
                ExternsClass.m = function() {};
                """),
        srcs("class CodeClass extends ExternsClass {}"),
        expected(
            """
            /** @constructor */
            let CodeClass = function() {
              return ExternsClass.apply(this, arguments) || this;
            };
            $jscomp.inherits(CodeClass,ExternsClass)
            """));
  }

  @Test
  public void testInheritFromExterns_withReflectConstruct() {
    es6SubclassTranspilation = Es6SubclassTranspilation.SAFE_REFLECT_CONSTRUCT;
    test(
        externs(
            EXTERNS_BASE
                + """
                /** @constructor */ function ExternsClass() {}
                ExternsClass.m = function() {};
                """),
        srcs("class CodeClass extends ExternsClass {}"),
        expected(
            """
            /** @constructor */
            let CodeClass = function() {
              return $jscomp.construct(ExternsClass, arguments, this.constructor);
            };
            $jscomp.inherits(CodeClass,ExternsClass)
            """));
  }

  // Make sure we don't crash on this code.
  // https://github.com/google/closure-compiler/issues/752
  @Test
  public void testGithub752() {
    test(
        "function f() { var a = b = class {};}",
        """
        function f() {
          /** @constructor */
          const CLASS_DECL$0 = function() {};
          var a = b = CLASS_DECL$0;
        }
        """);
  }

  @Test
  public void testGithub752b() {
    test(
        "var ns = {}; function f() { var self = ns.Child = class {};}",
        """
        var ns = {};
        function f() {
          /** @constructor */
          const CLASS_DECL$0 = function() {};
          var self = ns.Child = CLASS_DECL$0
        }
        """);
  }

  /**
   * Getters and setters are supported, both in object literals and in classes, but only if the
   * output language is ES5.
   */
  @Test
  public void testEs5GettersAndSettersClasses_withSingleGetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "class C { get value() { return 0; } }",
        """
        /** @constructor */
        let C = function() {};
        $jscomp.global.Object.defineProperties(C.prototype, {
          value: {
            configurable: true,
            enumerable: true,
            get: function() {
              return 0;
            }
          }
        });
        """);
  }

  @Test
  public void testEs5GettersAndSettersClasses_withSingleSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "class C { set value(val) { var internalVal = val; } }",
        """
        /** @constructor */
        let C = function() {};
        $jscomp.global.Object.defineProperties(C.prototype, {
          value: {
            configurable: true,
            enumerable: true,
            set: function(val) {
              var internalVal = val;
            }
          }
        });
        """);
  }

  @Test
  public void testEs5GettersAndSettersClasses_withGetterSetterPair() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        """
        /** @unrestricted */
        class C {
          set value(val) {
            this.internalVal = val;
          }
          get value() {
            return this.internalVal;
          }
        }
        """,
        """
        /** @constructor */
        let C = function() {};
        $jscomp.global.Object.defineProperties(C.prototype, {
          value: {
            configurable: true,
            enumerable: true,
            set: function(val) {
              this.internalVal = val;
            },
            get: function() {
              return this.internalVal;
            }
          }
        });
        """);
  }

  @Test
  public void testEs5GettersAndSettersClasses_withTwoGetters() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        """
        class C {
          get alwaysTwo() {
            return 2;
          }

          get alwaysThree() {
            return 3;
          }
        }
        """,
        """
        /** @constructor */
        let C = function() {};
        $jscomp.global.Object.defineProperties(C.prototype, {
          alwaysTwo: {
            configurable: true,
            enumerable: true,
            get: function() {
              return 2;
            }
          },
          alwaysThree: {
            configurable: true,
            enumerable: true,
            get: function() {
              return 3;
            }
          },
        });
        """);
  }

  @Test
  public void testEs5GettersAndSettersOnClassesWithClassSideInheritance() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "class C { static get value() {} }  class D extends C { static get value() {} }",
        """
        /** @constructor */
        let C = function() {};
        /** @nocollapse */
        C.value;
        $jscomp.global.Object.defineProperties(C, {
          value: {
            configurable: true,
            enumerable: true,
            get: function() {}
          }
        });
        /** @constructor */
        let D = function() {
          C.apply(this, arguments);
        };
        /** @nocollapse */
        D.value;
        $jscomp.inherits(D, C);
        $jscomp.global.Object.defineProperties(D, {
          value: {
            configurable: true,
            enumerable: true,
            get: function() {}
          }
        });
        """);
  }

  /** Check that the types from the getter/setter are copied to the declaration on the prototype. */
  @Test
  public void testEs5GettersAndSettersClassesWithTypes_withSingleGetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "class C { /** @return {number} */ get value() { return 0; } }",
        """
        /** @constructor */
        let C = function() {};
        $jscomp.global.Object.defineProperties(C.prototype, {
          value: {
            configurable: true,
            enumerable: true,
            get: function() {
              return 0;
            }
          }
        });
        """);
  }

  @Test
  public void testEs5GettersAndSettersClassesWithTypes_withSingleSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "class C { /** @param {string} v */ set value(v) { } }",
        """
        /** @constructor */
        let C = function() {};
        $jscomp.global.Object.defineProperties(C.prototype, {
          value: {
            configurable: true,
            enumerable: true,
            set: function(v) {}
          }
        });
        """);
  }

  @Test
  public void testEs5GettersAndSettersClassesWithTypes_withConflictingGetterSetterType() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    testWarning(
        """
        class C {
          /** @return {string} */
          get value() { }

          /** @param {number} v */
          set value(v) { }
        }
        """,
        TypeCheck.CONFLICTING_GETTER_SETTER_TYPE);

    // Also verify what the actual output is
    disableTypeCheck();
    disableTypeInfoValidation();

    test(
        """
        class C {
          /** @return {string} */
          get value() { }

          /** @param {number} v */
          set value(v) { }
        }
        """,
        """
        /** @constructor */
        let C = function() {};
        $jscomp.global.Object.defineProperties(C.prototype, {
          value: {
            configurable: true,
            enumerable: true,
            get: function() {},
            set:function(v){}
          }
        });
        """);
  }

  @Test
  public void testEs5GetterWithExport() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "class C { /** @export @return {string} */ get foo() {} }",
        """
        /**
         * @constructor
         */
        let C = function() {}

        $jscomp.global.Object.defineProperties(C.prototype, {
          foo: {
            configurable: true,
            enumerable: true,
            get: function() {},
          }
        });
        """);
  }

  @Test
  public void testEs5SetterWithExport() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "class C { /** @export @param {string} x */ set foo(x) {} }",
        """
        /**
         * @constructor
         */
        let C = function() {}

        $jscomp.global.Object.defineProperties(C.prototype, {
          foo: {
            configurable: true,
            enumerable: true,
            set: function(x) {},
          }
        });
        """);
  }

  /** b/20536614 */
  @Test
  public void testStaticGetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "class C { static get foo() {} }",
        """
        /** @constructor */
        let C = function() {};
        /** @nocollapse */
        C.foo;
        $jscomp.global.Object.defineProperties(C, {
          foo: {
            configurable: true,
            enumerable: true,
            get: function() {}
          }
        })
        """);
  }

  @Test
  public void testStaticGetter_withClassSideInheritance() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        """
        class C { static get foo() {} }
        class Sub extends C {}
        """,
        """
        /** @constructor */
        let C = function() {};
        /** @nocollapse */
        C.foo;
        $jscomp.global.Object.defineProperties(C, {
          foo: {
            configurable: true,
            enumerable: true,
            get: function() {}
          }
        })

        /** @constructor */
        let Sub = function() {
          C.apply(this, arguments);
        };
        $jscomp.inherits(Sub, C)
        """);
  }

  @Test
  public void testStaticSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "class C { static set foo(x) {} }",
        """
        /** @constructor */
        let C = function() {};
        /** @nocollapse */
        C.foo;
        $jscomp.global.Object.defineProperties(C, {
          foo: {
            configurable: true,
            enumerable: true,
            set: function(x) {}
          }
        });
        """);
  }

  @Test
  public void testClassStaticComputedProps_withSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "/** @unrestricted */ class C { static set [se()](val) {}}",
        """
        var COMP_FIELD$0 = se();
        /** @constructor */
        let C = function() {};
        $jscomp.global.Object.defineProperty(C, COMP_FIELD$0, {
          configurable: true,
          enumerable: true,
          set: function(val) {},
        });
        """);
  }

  @Test
  public void testClassStaticComputedProps_withGetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "/** @unrestricted */ class C { static get [se()]() {}}",
        """
        var COMP_FIELD$0 = se();
        /** @constructor */
        let C = function() {};
        $jscomp.global.Object.defineProperty(C, COMP_FIELD$0, {
          configurable: true,
          enumerable: true,
          get: function() {},
        });
        """);
  }

  @Test
  public void testClassStaticComputedProps_withGetterSetterPair() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "/** @unrestricted */ class C { static get [se()]() {} static set [se()](val) {}}",
        """
        var COMP_FIELD$0 = se();
        var COMP_FIELD$1 = se();
        /** @constructor */
        let C = function() {};
        $jscomp.global.Object.defineProperty(C, COMP_FIELD$0, {
          configurable: true,
          enumerable: true,
          get: function() {},
        });
        $jscomp.global.Object.defineProperty(C, COMP_FIELD$1, {
          configurable: true,
          enumerable: true,
          set: function(val) {},
        });
        """);
  }

  @Test
  public void testSuperDotPropWithoutExtends() {
    testError(
        """
        class C {
          foo() { return super.x; }
        }
        """,
        CANNOT_CONVERT_YET);
  }

  @Test
  public void testClassComputedPropGetterAndSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        """
        /** @unrestricted */
        class C {
          /** @return {boolean} */
          get [foo]() {}
          /** @param {boolean} val */
          set [foo](val) {}
        }
        """,
        """
        var COMP_FIELD$0 = foo;
        var COMP_FIELD$1 = foo;
        /** @constructor */
        let C = function() {};
        $jscomp.global.Object.defineProperty(C.prototype, COMP_FIELD$0, {
          configurable: true,
          enumerable: true,
          get: function() {},
        });
        $jscomp.global.Object.defineProperty(C.prototype, COMP_FIELD$1, {
          configurable: true,
          enumerable: true,
          set: function(val) {},
        });
        """);
  }

  @Test
  public void testClassComputedPropGetterAndSetter_withConflictingGetterSetterType() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    testNoWarning(
        """
        /** @unrestricted */
        class C {
          /** @return {boolean} */
          get [foo]() {}
          /** @param {string} val */
          set [foo](val) {}
        }
        """);
  }

  @Test
  public void testClassComputedPropGetterAndSetter_mixedWithOtherMembers() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        externs(
            new TestExternsBuilder()
                .addObject()
                .addReflect()
                .addJSCompLibraries()
                .addExtra(
                    """
                    function one() {}
                    function two() {}
                    function three() {}
                    """)
                .build()),
        srcs(
            """
            /** @unrestricted */
            class C {
              get [one()]() { return true; }
              get a() {}
              [two()]() {}
              get b() {}
              c() {}
              set [three()](val) { return true; }
            }
            """),
        expected(
            """
            var COMP_FIELD$0 = one();
            var COMP_FIELD$1 = two();
            var COMP_FIELD$2 = three();
            /** @constructor */
            let C = function() {};
            $jscomp.global.Object.defineProperty(C.prototype, COMP_FIELD$0, {
              configurable: true,
              enumerable: true,
              get: function() {
                return true;
              },
            });
            C.prototype[COMP_FIELD$1] = function() {};
            C.prototype.c = function() {};
            $jscomp.global.Object.defineProperty(C.prototype, COMP_FIELD$2, {
              configurable: true,
              enumerable: true,
              set: function(val) {
                return true;
              },
            });
            $jscomp.global.Object.defineProperties(C.prototype, {
              a: {configurable: true, enumerable: true, get: function() {}},
              b: {configurable: true, enumerable: true, get: function() {}},
            });
            """));
  }

  @Test
  public void testComputedPropClass() {
    test(
        "/** @unrestricted */ class C { [foo]() { alert(1); } }",
        """
        var COMP_FIELD$0 = foo;
        /** @constructor */
        let C = function() {
        };
        C.prototype[COMP_FIELD$0] = function() {
          alert(1);
        };
        """);
  }

  @Test
  public void testStaticComputedPropClass() {
    test(
        "/** @unrestricted */ class C { static [foo]() { alert(2); } }",
        """
        var COMP_FIELD$0 = foo;
        /** @constructor */
        let C = function() {
        };
        C[COMP_FIELD$0] = function() {
          alert(2);
        };
        """);
  }

  @Test
  public void testComputedPropGeneratorMethods() {
    test(
        "/** @unrestricted */ class C { *[foo]() { yield 1; } }",
        """
        var COMP_FIELD$0 = foo;
        /** @constructor */
        let C = function() {
        };
        C.prototype[COMP_FIELD$0] = function*() {
          yield 1;
        };
        """);
  }

  @Test
  public void testStaticComputedPropGeneratorMethods() {
    test(
        "/** @unrestricted */ class C { static *[foo]() { yield 2; } }",
        """
        var COMP_FIELD$0 = foo;
        /** @constructor */
        let C = function() {
        };
        C[COMP_FIELD$0] = function*() {
          yield 2;
        };
        """);
  }

  @Test
  public void testClassGenerator() {
    test(
        "class C { *foo() { yield 1; } }",
        """
        /** @constructor */
        let C = function() {};
        C.prototype.foo = function*() { yield 1;};
        """);
    assertThat(getLastCompiler().getRuntimeJsLibManager().getInjectedLibraries()).isEmpty();
  }

  @Test
  public void testClassInsideCast() {
    test(
        "const Foo = /** @type {?} */ (class {});",
        "/** @constructor */ const Foo = function() {};");
  }

  @Test
  public void testClassWithSuperclassInsideCast() {
    test(
        """
        function mixin(baseClass) {
          return /** @type {?} */ (class extends baseClass {
            constructor() {
              super();
            }
          });
        }
        """,
        """
        function mixin(baseClass){
          /**
           * @constructor
           */
          const CLASS_DECL$0 = function(){
            return baseClass.call(this) || this
          };
          $jscomp.inherits(CLASS_DECL$0, baseClass);
          return CLASS_DECL$0;
        }
        """);
  }

  /**
   * Tests that we have reasonable source information on the transpiled nodes.
   *
   * <p>We do this by comparing the source information of nodes on the transpiled AST with nodes on
   * a different parsed-but-not-transpiled AST.
   */
  @Test
  public void testSimpleClassStatement_hasCorrectSourceInfo() {
    String source = "class C { }";
    String expected = "/** @constructor */ let C = function() {};";

    AstPair asts = testAndReturnAsts(source, expected);
    Node sourceRoot = asts.sourceRoot;
    Node expectedRoot = asts.expectedRoot;

    // Get nodes from the original, pre-transpiled AST
    // `C` from `class C { }`
    Node sourceCName = getNodeWithName(sourceRoot, "C");
    Node sourceClass = sourceCName.getParent();

    // Multistage compilation does not preserve length in the actual output, so remove it from the
    // expected output as well.
    removeLengthFromSubtree(sourceRoot);

    // `C` from `let C = function() {};` matches `C` from `class C { }`
    Node expectedCName = getNodeWithName(expectedRoot, "C");
    assertNode(expectedCName).matchesQualifiedName("C").hasEqualSourceInfoTo(sourceCName);

    // function() {}
    Node expectedFunction = expectedCName.getOnlyChild();
    assertNode(expectedFunction).hasToken(Token.FUNCTION).hasEqualSourceInfoTo(sourceClass);
  }

  @Test
  public void testClassStatementWithConstructor_hasCorrectSourceInfo() {
    String source = "class C { constructor() {} }";
    String expected = "/** @constructor */ let C = function() {};";

    AstPair asts = testAndReturnAsts(source, expected);
    Node sourceRoot = asts.sourceRoot;
    Node expectedRoot = asts.expectedRoot;

    // Get nodes from the original, pre-transpiled AST
    // `C` from `class C {`
    Node sourceCName = getNodeWithName(sourceRoot, "C");
    // `constructor() {}`
    Node sourceConstructorFunction = getNodeWithName(sourceRoot, "constructor").getOnlyChild();
    assertNode(sourceConstructorFunction).hasToken(Token.FUNCTION);

    // Multistage compilation does not preserve length in the actual output, so remove it from the
    // expected output as well.
    removeLengthFromSubtree(sourceRoot);

    // `C` from `let C = function() {};` matches `C` from `class C {`
    Node expectedCName = getNodeWithName(expectedRoot, "C");
    assertNode(expectedCName).matchesQualifiedName("C").hasEqualSourceInfoTo(sourceCName);

    // `function() {}` matches `constructor() {}`
    Node expectedFunction = expectedCName.getOnlyChild();
    assertNode(expectedFunction)
        .hasToken(Token.FUNCTION)
        .hasEqualSourceInfoTo(sourceConstructorFunction);
  }

  @Test
  public void testClassStatementWithMethod_hasCorrectSourceInfo() {
    String source = "class C { method() {}; }";
    String expected =
        """
        /** @constructor */
        let C = function() {};
        C.prototype.method = function() {};
        """;

    AstPair asts = testAndReturnAsts(source, expected);
    Node sourceRoot = asts.sourceRoot;
    Node expectedRoot = asts.expectedRoot;

    // Get nodes from the original, pre-transpiled AST
    // The MEMBER_FUNCTION_DEF for `method`
    Node sourceMethodMemberDef = getNodeWithName(sourceRoot, "method");
    // The FUNCTION node for `method() {}`
    Node sourceMethodFunction = sourceMethodMemberDef.getOnlyChild();

    // Multistage compilation does not preserve length in the actual output, so remove it from the
    // expected output as well.
    removeLengthFromSubtree(sourceRoot);

    // `C.prototype.method` has source info matching `method`
    Node cPrototypeMethod = getNodeWithName(expectedRoot, "method");
    assertNode(cPrototypeMethod).hasEqualSourceInfoTo(sourceMethodMemberDef);

    // `C.prototype` has source info matching `method`
    Node cPrototype = cPrototypeMethod.getFirstChild();
    assertNode(cPrototype)
        .matchesQualifiedName("C.prototype")
        .hasEqualSourceInfoTo(sourceMethodMemberDef);

    // `C` has source info matching `method`
    Node cName = cPrototype.getFirstChild();
    assertNode(cName).matchesQualifiedName("C").hasEqualSourceInfoTo(sourceMethodMemberDef);

    // `function() {}` has source info matching `method() {}`
    assertNode(cPrototypeMethod.getNext())
        .hasToken(Token.FUNCTION)
        .hasEqualSourceInfoTo(sourceMethodFunction);
  }

  @Test
  public void testSuperCall_hasCorrectSourceInfo() {
    String source =
        """
        class D {}
        class C extends D {
          constructor() {
            SUPER: super();
          }
        }
        """;
    String expected =
        """
        /** @constructor */
        let D = function() {};
        /** @constructor */
        let C = function() {
          SUPER: { D.call(this); }
        }
        $jscomp.inherits(C, D);
        """;

    AstPair asts = testAndReturnAsts(source, expected);
    Node sourceRoot = asts.sourceRoot;
    Node expectedRoot = asts.expectedRoot;

    // Get nodes from the original, pre-transpiled AST
    Node sourceSuperCall = getNodeMatchingLabel(sourceRoot, "SUPER").getOnlyChild();
    assertNode(sourceSuperCall).hasToken(Token.CALL);
    Node sourceSuper = sourceSuperCall.getFirstChild();
    assertNode(sourceSuper).hasToken(Token.SUPER);

    // Multistage compilation does not preserve length in the actual output, so remove it from the
    // expected output as well.
    removeLengthFromSubtree(sourceRoot);

    // D.call(this); has the position and length of `super()`
    Node callNode = getNodeMatchingLabel(expectedRoot, "SUPER").getOnlyChild().getOnlyChild();
    assertNode(callNode).hasToken(Token.CALL).hasEqualSourceInfoTo(sourceSuperCall);

    // D.call has the position and length of `super`
    Node callee = callNode.getFirstChild();
    assertNode(callee).matchesQualifiedName("D.call").hasEqualSourceInfoTo(sourceSuper);

    // D has the position and length of `super`
    Node superDotGReplacement = callee.getFirstChild();
    assertNode(superDotGReplacement).matchesQualifiedName("D").hasEqualSourceInfoTo(sourceSuper);

    // `this` node from `D.call(this)` has the position and length of `super`
    Node thisNode = callee.getNext();
    assertNode(thisNode).hasToken(Token.THIS).hasEqualSourceInfoTo(sourceSuper);
  }

  @Test
  public void testComputedSuper_hasCorrectSourceInfo() {
    String source =
        """
        /** @unrestricted */
        class Foo {
          ['m']() { return 1; }
        }

        /** @unrestricted */
        class Bar extends Foo {
          ['m']() {
            RETURN: return super['m']();
          }
        }
        """;
    String expected =
        """
        /** @constructor */
        let Foo = function() {};
        Foo.prototype['m'] = function() { return 1; };
        /** @constructor */
        let Bar = function() { Foo.apply(this, arguments); };
        $jscomp.inherits(Bar, Foo);
        Bar.prototype['m'] = function () {
          RETURN: { return Foo.prototype['m'].call(this); }
        };
        """;

    AstPair asts = testAndReturnAsts(source, expected);
    Node sourceRoot = asts.sourceRoot;
    Node expectedRoot = asts.expectedRoot;

    // Get nodes from the original, pre-transpiled AST
    Node sourceSuperCall = getNodeMatchingLabel(sourceRoot, "RETURN").getOnlyChild();
    assertNode(sourceSuperCall).hasToken(Token.CALL);
    Node sourceSuperGet = sourceSuperCall.getFirstChild();
    assertNode(sourceSuperGet).hasToken(Token.GETELEM);
    Node sourceSuper = sourceSuperGet.getFirstChild();
    assertNode(sourceSuper).hasToken(Token.SUPER);

    // Multistage compilation does not preserve length in the actual output, so remove it from the
    // expected output as well.
    removeLengthFromSubtree(sourceRoot);

    // Foo.prototype['m'].call(this) matches source info of `super['m']()`
    Node callNode = getNodeMatchingLabel(expectedRoot, "RETURN").getOnlyChild().getOnlyChild();
    assertNode(callNode).hasToken(Token.CALL).hasEqualSourceInfoTo(sourceSuperCall);

    // Foo.prototype['m'].call has the position and length of `super['m']'`
    Node callee = callNode.getFirstChild();
    assertNode(callee).hasToken(Token.GETPROP).hasEqualSourceInfoTo(sourceSuperGet);

    // Foo.prototype['m'] has the position and length of `super['m']'`
    Node property = callee.getFirstChild();
    assertNode(property).hasToken(Token.GETELEM).hasEqualSourceInfoTo(sourceSuperGet);

    // Foo.prototype has the position and length of `super`
    Node prototype = property.getFirstChild();
    assertNode(prototype).matchesQualifiedName("Foo.prototype").hasEqualSourceInfoTo(sourceSuper);

    // Foo has the position and length of `super`
    Node superDotGReplacement = prototype.getFirstChild();
    assertNode(superDotGReplacement).matchesQualifiedName("Foo").hasEqualSourceInfoTo(sourceSuper);

    // `this` node from `Foo.prototype['m'].call(this)` has position and length of `super['m']`
    Node thisNode = callee.getNext();
    assertNode(thisNode).hasToken(Token.THIS).hasEqualSourceInfoTo(sourceSuperGet);
  }

  @Test
  public void testStringKeyGetterAndSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    // Can't add a property to a struct
    disableTypeCheck();
    disableTypeInfoValidation();

    test(
        """
        class MyClass {
          /** @param {number} v */
          set 'setter'(v) {}
          /** @return {number} */
          get 'getter'() { return 1; }
        }
        """,
        """
        /** @constructor */
        let MyClass=function(){};

        $jscomp.global.Object.defineProperties(
            MyClass.prototype,
            {
              'setter': {
                configurable:true,
                enumerable:true,
                set:function(v){ }
              },
              'getter': {
                configurable:true,
                enumerable:true,
                get:function(){ return 1; }
              },
            })
        """);
  }

  @Test
  public void testNumericGetterAndSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    // Can't add a property to a struct
    disableTypeCheck();
    disableTypeInfoValidation();

    test(
        """
        class MyClass {
          /** @param {number} v */
          set 1(v) {}
          /** @return {number} */
          get 1.5() { return 1; }
        }
        """,
        """
        /** @constructor */
        let MyClass=function(){};
        $jscomp.global.Object.defineProperties(
            MyClass.prototype,
            {
              1: {
                configurable:true,
                enumerable:true,
                set:function(v){ }
              },
              1.5: {
                configurable:true,
                enumerable:true,
                get:function(){ return 1; }
              },
            })
        """);
  }

  /** Returns the first node (preorder) in the given AST labeled as {@code label} */
  private Node getNodeMatchingLabel(Node root, String label) {
    if (root.isLabel() && root.getFirstChild().getString().equals(label)) {
      // "FOO: return foo;"
      //   becomes
      // LABEL
      //   LABEL_NAME FOO
      //   RETURN
      //   NAME foo
      return root.getSecondChild();
    }
    for (Node child = root.getFirstChild(); child != null; child = child.getNext()) {
      Node result = getNodeMatchingLabel(child, label);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  /** Returns the first node (preorder) in the given AST that matches the given qualified name */
  private Node getNodeWithName(Node root, String name) {
    switch (root.getToken()) {
      case GETPROP, OPTCHAIN_GETPROP, NAME, STRINGLIT, MEMBER_FUNCTION_DEF, STRING_KEY -> {
        if (root.getString().equals(name)) {
          return root;
        }
      }
      default -> {}
    }

    for (Node child = root.getFirstChild(); child != null; child = child.getNext()) {
      Node result = getNodeWithName(child, name);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  /**
   * Tests that the transpiled source matches the expected code and then returns the AST roots for
   * the source and the transpiled source respectively.
   */
  private AstPair testAndReturnAsts(String source, String expected) {
    Node originalRoot = createCompiler().parseTestCode(source);

    test(source, expected);

    return new AstPair(originalRoot, getLastCompiler().getJsRoot());
  }

  private static class AstPair {
    final Node sourceRoot;

    final Node expectedRoot;

    AstPair(Node sourceRoot, Node expectedRoot) {
      this.sourceRoot = sourceRoot;
      this.expectedRoot = expectedRoot;
    }
  }

  /** Sets n.getLength() to 0 for every node in the given subtree */
  private static void removeLengthFromSubtree(Node root) {
    root.setLength(0);
    for (Node child = root.getFirstChild(); child != null; child = child.getNext()) {
      removeLengthFromSubtree(child);
    }
  }
}
