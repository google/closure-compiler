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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link ChromePass}. */
@RunWith(JUnit4.class)
public class ChromePassTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ChromePass(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    // This pass isn't idempotent and only runs once.
    return 1;
  }

  @Test
  public void testCrDefineCreatesObjectsForQualifiedName() {
    test(
        """
        cr.define('my.namespace.name', function() {
          return {};
        });
        """,
        """
        var my = my || {};
        my.namespace = my.namespace || {};
        my.namespace.name = my.namespace.name || {};
        cr.define('my.namespace.name', function() {
          return {};
        });
        """);
  }

  @Test
  public void testChromePassIgnoresModules() {
    testSame("export var x;");
  }

  @Test
  public void testCrDefineAssignsExportedFunctionByQualifiedName() {
    test(
        """
        cr.define('namespace', function() {
          function internalStaticMethod() {
            alert(42);
          }
          return {
            externalStaticMethod: internalStaticMethod
          };
        });
        """,
        """
        var namespace = namespace || {};
        cr.define('namespace', function() {
          namespace.externalStaticMethod = function internalStaticMethod() {
            alert(42);
          }
          return {
            externalStaticMethod: namespace.externalStaticMethod
          };
        });
        """);
  }

  @Test
  public void testCrDefineCopiesJSDocForExportedFunction() {
    test(
        """
        cr.define('namespace', function() {
          /** I'm function's JSDoc */
          function internalStaticMethod() {
            alert(42);
          }
          return {
            externalStaticMethod: internalStaticMethod
          };
        });
        """,
        """
        var namespace = namespace || {};
        cr.define('namespace', function() {
          /** I'm function's JSDoc */
          namespace.externalStaticMethod = function internalStaticMethod() {
            alert(42);
          }
          return {
            externalStaticMethod: namespace.externalStaticMethod
          };
        });
        """);
  }

  @Test
  public void testCrDefineReassignsExportedVarByQualifiedName() {
    test(
        """
        cr.define('namespace', function() {
          var internalStaticMethod = function() {
            alert(42);
          }
          return {
            externalStaticMethod: internalStaticMethod
          };
        });
        """,
        """
        var namespace = namespace || {};
        cr.define('namespace', function() {
          namespace.externalStaticMethod = function() {
            alert(42);
          }
          return {
            externalStaticMethod: namespace.externalStaticMethod
          };
        });
        """);
  }

  @Test
  public void testCrDefineExportsVarsWithoutAssignment() {
    test(
        """
        cr.define('namespace', function() {
          var a;
          return {
            a: a
          };
        });
        """,
        """
        var namespace = namespace || {};
        cr.define('namespace', function() {
          namespace.a;
          return {
            a: namespace.a
          };
        });
        """);
  }

  @Test
  public void testCrDefineExportsVarsWithoutAssignmentWithJSDoc() {
    test(
        """
        cr.define('namespace', function() {
          /** @type {number} */
          var a;
          return {
            a: a
          };
        });
        """,
        """
        var namespace = namespace || {};
        cr.define('namespace', function() {
          /** @type {number} */
          namespace.a;
          return {
            a: namespace.a
          };
        });
        """);
  }

  @Test
  public void testCrDefineCopiesJSDocForExportedVariable() {
    test(
        """
        cr.define('namespace', function() {
          /** I'm function's JSDoc */
          var internalStaticMethod = function() {
            alert(42);
          }
          return {
            externalStaticMethod: internalStaticMethod
          };
        });
        """,
        """
        var namespace = namespace || {};
        cr.define('namespace', function() {
          /** I'm function's JSDoc */
          namespace.externalStaticMethod = function() {
            alert(42);
          }
          return {
            externalStaticMethod: namespace.externalStaticMethod
          };
        });
        """);
  }

  @Test
  public void testCrDefineDoesNothingWithNonExportedFunction() {
    test(
        """
        cr.define('namespace', function() {
          function internalStaticMethod() {
            alert(42);
          }
          return {};
        });
        """,
        """
        var namespace = namespace || {};
        cr.define('namespace', function() {
          function internalStaticMethod() {
            alert(42);
          }
          return {};
        });
        """);
  }

  @Test
  public void testCrDefineDoesNothingWithNonExportedVar() {
    test(
        """
        cr.define('namespace', function() {
          var a;
          var b;
          return {
            a: a
          };
        });
        """,
        """
        var namespace = namespace || {};
        cr.define('namespace', function() {
          namespace.a;
          var b;
          return {
            a: namespace.a
          };
        });
        """);
  }

  @Test
  public void testCrDefineDoesNothingWithExportedNotAName() {
    test(
        """
        cr.define('namespace', function() {
          return {
            a: 42
          };
        });
        """,
        """
        var namespace = namespace || {};
        cr.define('namespace', function() {
          return {
            a: 42
          };
        });
        """);
  }

  @Test
  public void testCrDefineChangesReferenceToExportedFunction() {
    test(
        """
        cr.define('namespace', function() {
          function internalStaticMethod() {
            alert(42);
          }
          function letsUseIt() {
            internalStaticMethod();
          }
          return {
            externalStaticMethod: internalStaticMethod
          };
        });
        """,
        """
        var namespace = namespace || {};
        cr.define('namespace', function() {
          namespace.externalStaticMethod = function internalStaticMethod() {
            alert(42);
          }
          function letsUseIt() {
            namespace.externalStaticMethod();
          }
          return {
            externalStaticMethod: namespace.externalStaticMethod
          };
        });
        """);
  }

  @Test
  public void testCrDefineConstEnum() {
    test(
        """
        cr.define('foo', function() {
          /**
           * @enum {string}
           */
          const DangerType = {
            NOT_DANGEROUS: 'NOT_DANGEROUS',
            DANGEROUS: 'DANGEROUS',
          };

          return {
            DangerType: DangerType,
          };
        });
        """,
        """
        var foo = foo || {};
        cr.define('foo', function() {
          /** @enum {string} */
          foo.DangerType = {
            NOT_DANGEROUS:'NOT_DANGEROUS',
            DANGEROUS:'DANGEROUS',
          };

          return {
            DangerType: foo.DangerType
          }
        })
        """);
  }

  @Test
  public void testCrDefineLetEnum() {
    test(
        """
        cr.define('foo', function() {
          /**
           * @enum {string}
           */
          let DangerType = {
            NOT_DANGEROUS: 'NOT_DANGEROUS',
            DANGEROUS: 'DANGEROUS',
          };

          return {
            DangerType: DangerType,
          };
        });
        """,
        """
        var foo = foo || {};
        cr.define('foo', function() {
          /** @enum {string} */
          foo.DangerType = {
            NOT_DANGEROUS:'NOT_DANGEROUS',
            DANGEROUS:'DANGEROUS',
          };

          return {
            DangerType: foo.DangerType
          }
        })
        """);
  }

  @Test
  public void testCrDefineWrongNumberOfArguments() {
    testError(
        "cr.define('namespace', function() { return {}; }, 'invalid argument')\n",
        ChromePass.CR_DEFINE_WRONG_NUMBER_OF_ARGUMENTS);
  }

  @Test
  public void testCrDefineInvalidFirstArgument() {
    testError(
        "cr.define(42, function() { return {}; })\n", ChromePass.CR_DEFINE_INVALID_FIRST_ARGUMENT);
  }

  @Test
  public void testCrDefineInvalidSecondArgument() {
    testError("cr.define('namespace', 42)\n", ChromePass.CR_DEFINE_INVALID_SECOND_ARGUMENT);
  }

  @Test
  public void testCrDefineInvalidReturnInFunction() {
    testError(
        "cr.define('namespace', function() {})\n", ChromePass.CR_DEFINE_INVALID_RETURN_IN_FUNCTION);
  }

  @Test
  public void testObjectDefinePropertyWithoutArgsDoesntThrow() {
    test("Object.defineProperty();", "Object.defineProperty();");
    test("Object.defineProperty(a);", "Object.defineProperty(a);");
    test("Object.defineProperty(a, 'b');", "Object.defineProperty(a, 'b');");
  }

  @Test
  public void testObjectDefinePropertyDefinesUnquotedProperty() {
    test(
        "Object.defineProperty(a.b, 'c', {});",
        """
        Object.defineProperty(a.b, 'c', {});
        /** @type {?} */
        a.b.c;
        """);
  }

  @Test
  public void testCrDefinePropertyTooFewArguments() {
    testError("cr.defineProperty();\n", ChromePass.CR_DEFINE_PROPERTY_TOO_FEW_ARGUMENTS);
    testError("cr.defineProperty(a.prototype);\n", ChromePass.CR_DEFINE_PROPERTY_TOO_FEW_ARGUMENTS);
  }

  @Test
  public void testCrDefinePropertyDefinesUnquotedPropertyWithStringTypeForPropertyKindAttr()
      throws Exception {
    test(
        "cr.defineProperty(a.prototype, 'c', cr.PropertyKind.ATTR);",
        """
        cr.defineProperty(a.prototype, 'c', cr.PropertyKind.ATTR);
        /** @type {string} */
        a.prototype.c;
        """);
  }

  @Test
  public void testCrDefinePropertyDefinesUnquotedPropertyWithBooleanTypeForPropertyKindBoolAttr()
      throws Exception {
    test(
        "cr.defineProperty(a.prototype, 'c', cr.PropertyKind.BOOL_ATTR);",
        """
        cr.defineProperty(a.prototype, 'c', cr.PropertyKind.BOOL_ATTR);
        /** @type {boolean} */
        a.prototype.c;
        """);
  }

  @Test
  public void testCrDefinePropertyDefinesUnquotedPropertyWithAnyTypeForPropertyKindJs()
      throws Exception {
    test(
        "cr.defineProperty(a.prototype, 'c', cr.PropertyKind.JS);",
        """
        cr.defineProperty(a.prototype, 'c', cr.PropertyKind.JS);
        /** @type {?} */
        a.prototype.c;
        """);
  }

  @Test
  public void testCrDefinePropertyDefinesUnquotedPropertyWithTypeInfoForPropertyKindJs()
      throws Exception {
    test(
        """
        /** @type {!Object} */
        cr.defineProperty(a.prototype, 'c', cr.PropertyKind.JS);
        """,
        """
        cr.defineProperty(a.prototype, 'c', cr.PropertyKind.JS);
        // But gets moved here.
        /** @type {!Object} */
        a.prototype.c;
        """);
  }

  @Test
  public void testCrDefinePropertyDefinesUnquotedPropertyIgnoringJsDocWhenBoolAttrIsPresent()
      throws Exception {
    test(
        """
        /** @type {!Object} */
        cr.defineProperty(a.prototype, 'c', cr.PropertyKind.BOOL_ATTR);
        """,
        """
        cr.defineProperty(a.prototype, 'c', cr.PropertyKind.BOOL_ATTR);
        // @type is now {boolean}. Earlier, manually-specified @type is ignored.
        /** @type {boolean} */
        a.prototype.c;
        """);
  }

  @Test
  public void testCrDefinePropertyDefinesUnquotedPropertyIgnoringJsDocWhenAttrIsPresent()
      throws Exception {
    test(
        """
        /** @type {!Array} */
        cr.defineProperty(a.prototype, 'c', cr.PropertyKind.ATTR);
        """,
        """
        cr.defineProperty(a.prototype, 'c', cr.PropertyKind.ATTR);
        // @type is now {string}. Earlier, manually-specified @type is ignored.
        /** @type {string} */
        a.prototype.c;
        """);
  }

  @Test
  public void testCrDefinePropertyCalledWithouthThirdArgumentMeansCrPropertyKindJs()
      throws Exception {
    test(
        "cr.defineProperty(a.prototype, 'c');",
        """
        cr.defineProperty(a.prototype, 'c');
        /** @type {?} */
        a.prototype.c;
        """);
  }

  @Test
  public void testCrDefinePropertyDefinesUnquotedPropertyOnPrototypeWhenFunctionIsPassed()
      throws Exception {
    test(
        "cr.defineProperty(a, 'c', cr.PropertyKind.JS);",
        """
        cr.defineProperty(a, 'c', cr.PropertyKind.JS);
        /** @type {?} */
        a.prototype.c;
        """);
  }

  @Test
  public void testCrDefinePropertyInvalidPropertyKind() {
    testError(
        "cr.defineProperty(a.b, 'c', cr.PropertyKind.INEXISTENT_KIND);",
        ChromePass.CR_DEFINE_PROPERTY_INVALID_PROPERTY_KIND);
  }

  @Test
  public void testCrDefineCreatesEveryObjectOnlyOnce() {
    test(
        """
        cr.define('a.b.c.d', function() {
          return {};
        });
        cr.define('a.b.e.f', function() {
          return {};
        });
        """,
        """
        var a = a || {};
        a.b = a.b || {};
        a.b.c = a.b.c || {};
        a.b.c.d = a.b.c.d || {};
        cr.define('a.b.c.d', function() {
          return {};
        });
        a.b.e = a.b.e || {};
        a.b.e.f = a.b.e.f || {};
        cr.define('a.b.e.f', function() {
          return {};
        });
        """);
  }

  @Test
  public void testCrDefineDoesntRedefineCrVar() {
    test(
        """
        cr.define('cr.ui', function() {
          return {};
        });
        """,
        """
        cr.ui = cr.ui || {};
        cr.define('cr.ui', function() {
          return {};
        });
        """);
  }

  @Test
  public void testCrDefineFunction() {
    test(
        """
        cr.define('settings', function() {
          var x = 0;
          function C() {}
          return { C: C };
        });
        """,
        """
        var settings = settings || {};
        cr.define('settings', function() {
          var x = 0;
          settings.C = function C() {};
          return { C: settings.C };
        });
        """);
  }

  @Test
  public void testCrDefineClassStatement() {
    test(
        """
        cr.define('settings', function() {
          var x = 0;
          class C {}
          return { C: C };
        });
        """,
        """
        var settings = settings || {};
        cr.define('settings', function() {
          var x = 0;
          settings.C = class {}
          return { C: settings.C };
        });
        """);
  }

  @Test
  public void testCrDefineClassExpression() {
    test(
        """
        cr.define('settings', function() {
          var x = 0;
          var C = class {}
          return { C: C };
        });
        """,
        """
        var settings = settings || {};
        cr.define('settings', function() {
          var x = 0;
          settings.C = class {}
          return { C: settings.C };
        });
        """);
  }

  @Test
  public void testCrDefineClassWithInternalSelfReference() {
    test(
        """
        cr.define('settings', function() {
          var x = 0;
          class C {
            static create() { return new C; }
          }
          return { C: C };
        });
        """,
        """
        var settings = settings || {};
        cr.define('settings', function() {
          var x = 0;
          settings.C = class {
            static create() { return new settings.C; }
          }
          return { C: settings.C };
        });
        """);
  }
}
