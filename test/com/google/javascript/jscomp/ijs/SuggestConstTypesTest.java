/*
 * Copyright 2015 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.ijs;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link ConvertToTypedInterface} when running after type-checking. This is only
 * really useful for checking the wording of the CONST_WITH_SUGGESTION diagnostic.
 */
@RunWith(JUnit4.class)
public final class SuggestConstTypesTest extends CompilerTestCase {
  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    allowExternsChanges();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
    enableTypeCheck();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new ConvertToTypedInterface(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  private void suggests(String code, String type) {
    test(
        srcs(code),
        warning(ConvertToTypedInterface.CONSTANT_WITH_SUGGESTED_TYPE).withMessageContaining(type));
  }

  @Test
  public void testSimpleSuggestConstJsdoc() {
    suggests("/** @const */ var x = cond ? true : 5;", "{(boolean|number)}");
    suggests("/** @constructor */ function Foo() {} /** @const */ var x = new Foo;", "{!Foo}");
  }

}
