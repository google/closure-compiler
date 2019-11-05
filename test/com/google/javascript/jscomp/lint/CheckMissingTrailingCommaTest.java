/*
 * Copyright 2019 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.lint;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.DiagnosticGroups;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CheckMissingTrailingCommaTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckMissingTrailingComma(compiler);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    return options;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2019);
  }

  private void testWarning(String js) {
    testWarning(js, CheckMissingTrailingComma.MISSING_TRAILING_COMMA);
  }

  @Test
  public void testWarning_array() {
    testWarning("use([a\n]);");
    testWarning("use([a\r\n]);");
    testWarning("use([a,\n  b\n  ]);");
  }

  @Test
  public void testWarning_object() {
    testWarning("use({a: b\n});");
    testWarning("use({a: b\r\n});");
    testWarning("use({a: b,\n  c: d\n  });");
    testWarning("use({a\n});");
    testWarning("use({a() {}\n});");
    testWarning("use({get a() {}\n});");
    testWarning("use({set a(b) {}\n});");
    testWarning("use({[a]: b\n});");
    testWarning("use({[a]() {}\n});");
    testWarning("use({get [a]() {}\n});");
    testWarning("use({set [a](b) {}\n});");
    testWarning("use({...a\n});");
  }

  @Test
  public void testNoWarning_inapplicableNode() {
    testSame("use(void 0);");
  }

  @Test
  public void testNoWarning_arrayWithoutNewline() {
    testSame("use([a]);");
    testSame("use([a    ]);");
    testSame("use([a, b    ]);");
  }

  @Test
  public void testNoWarning_objectWithoutNewline() {
    testSame("use({a: b});");
    testSame("use({a: b    });");
    testSame("use({a: b, c: d    });");
    testSame("use({a});");
    testSame("use({a() {}});");
    testSame("use({get a() {}});");
    testSame("use({set a(b) {}});");
    testSame("use({[a]: b});");
    testSame("use({[a]() {}});");
    testSame("use({get [a]() {}});");
    testSame("use({set [a](b) {}});");
    testSame("use({...a});");
  }

  @Test
  public void testNoWarning_arrayWithTrailingComma() {
    testSame("use([a,\n]);");
    testSame("use([a,\r\n]);");
    testSame("use([a,\n  b,\n  ]);");
  }

  @Test
  public void testNoWarning_objectWithTrailingComma() {
    testSame("use({a: b,\n});");
    testSame("use({a: b,\r\n});");
    testSame("use({a: b,\n  c: d,\n  });");
    testSame("use({a,\n});");
    testSame("use({a() {},\n});");
    testSame("use({get a() {},\n});");
    testSame("use({set a(b) {},\n});");
    testSame("use({[a]: b,\n});");
    testSame("use({[a]() {},\n});");
    testSame("use({get [a]() {},\n});");
    testSame("use({set [a](b) {},\n});");
    testSame("use({...a,\n});");
  }

  @Test
  public void testNoWarning_empty() {
    testSame("use([\n]);");
    testSame("use({\n});");
  }

  @Test
  public void testNoWarning_arrayWithTrailingHole() {
    testSame("use([,\n]);");
  }
}
