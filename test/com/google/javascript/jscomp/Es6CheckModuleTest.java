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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link Es6CheckModule} */
@RunWith(JUnit4.class)
public final class Es6CheckModuleTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new Es6CheckModule(compiler);
  }

  /** Specify EcmaScript 2015 (ES6) for tests */
  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.MODULE_LOAD, CheckLevel.OFF);
    return options;
  }

  @Test
  public void testEs6ThisWithExportModule() {
    testWarning("export {};\nfoo.call(this, 1, 2, 3);", Es6CheckModule.ES6_MODULE_REFERENCES_THIS);
  }

  @Test
  public void testEs6ThisWithImportModule() {
    testWarning(
        """
        import ln from './other/x'
        if (x) {
          alert(this);
        }
        """,
        Es6CheckModule.ES6_MODULE_REFERENCES_THIS);
  }

  @Test
  public void testEs6ThisWithConstructor() {
    testSame(
        """
        class Foo {
          constructor() {
            this.x = 5;
          }
        }

        exports = Foo;
        """);
  }

  @Test
  public void testThisWithStaticMethod() {
    testSame("class Foo { static h() {var x = this.y;} }; exports = Foo;");
    testSame("class Foo {static h() {this.x = 2; }}; exports = Foo;");
    testSame("class Foo {static h() {this[this.x] = 3;}}; exports = Foo;");
    testSame(
        """
        class Foo {
          static h() {
            function g() {
              return this.f() + 1;
            }
            var y = g() + 1;
          }
          static f() {return 1;}
        }
        exports = Foo;
        """);
    testSame(
        """
        class Foo {
          static h() {
            button.addEventListener('click', function () {
              this.click();
            });
          }
          static click() {}
        };
        exports = Foo;
        """);
  }

  @Test
  public void testThisWithStaticBlock() {
    testSame("class Foo { static {var x = this.y;} }; exports = Foo;");
    testSame("class Foo {static {this.x = 2; }}; exports = Foo;");
    testSame("class Foo {static {this[this.x] = 3;}}; exports = Foo;");
    testSame(
        """
        class Foo {
          static {
            function g() {
              return this.f() + 1;
            }
            var y = g() + 1;
          }
          static f() {return 1;}
        }
        exports = Foo;
        """);
    testSame(
        """
        class Foo {
          static {
            button.addEventListener('click', function () {
              this.click();
            });
          }
          static click() {}
        };
        exports = Foo;
        """);
  }

  // just here to make sure import.meta doesn't break anything
  @Test
  public void testImportMeta() {
    testSame(
        """
        class Foo {
          constructor() {
            this.url = import.meta.url
          }
        }

        exports = Foo;
        """);
  }

  @Test
  public void testCannotRenameImport() {
    testError("import { p } from '/other'; p = 2;", Es6CheckModule.IMPORT_CANNOT_BE_REASSIGNED);

    testError(
        "import { p } from '/other'; ({p} = {});", Es6CheckModule.IMPORT_CANNOT_BE_REASSIGNED);
    testError(
        "import { p } from '/other'; ({z:p} = {});", Es6CheckModule.IMPORT_CANNOT_BE_REASSIGNED);
    testSame("import { p } from '/other'; ({p:z} = {});");

    testError("import { p } from '/other'; [p] = [];", Es6CheckModule.IMPORT_CANNOT_BE_REASSIGNED);

    testSame("import { p } from '/other'; p.x = 2;");
    testSame("import { p } from '/other'; p['x'] = 2;");

    testError(
        "import Default from '/other'; Default = 2;", Es6CheckModule.IMPORT_CANNOT_BE_REASSIGNED);

    testSame("import Default from '/other'; Default.x = 2;");
    testSame("import Default from '/other'; Default['x'] = 2;");

    testError(
        "import * as Module from '/other'; Module = 2;",
        Es6CheckModule.IMPORT_CANNOT_BE_REASSIGNED);

    testError(
        "import * as Module from '/other'; Module.x = 2;",
        Es6CheckModule.IMPORT_CANNOT_BE_REASSIGNED);

    testError(
        "import * as Module from '/other'; Module['x'] = 2;",
        Es6CheckModule.IMPORT_CANNOT_BE_REASSIGNED);

    testSame("import * as Module from '/other'; Module.x.y = 2;");

    testSame("import * as Module from '/other'; Module['x'].y = 2;");

    // Handled by VariableReferenceCheck.
    testSame("import { p } from '/other'; let p = 0;");
    testSame("import { p } from '/other'; var {p} = {};");
    testSame("import { p } from '/other'; var [p] = [];");
    testSame("import { p } from '/other'; function p() {};");
    testSame("import { p } from '/other'; class p {};");
  }
}
