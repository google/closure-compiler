/*
 * Copyright 2018 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for {@link RewriteObjectSpread} */
@RunWith(JUnit4.class)
public final class RewriteObjectSpreadTest extends CompilerTestCase {

  public RewriteObjectSpreadTest() {
    super(MINIMAL_EXTERNS);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_NEXT);
    setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    enableRunTypeCheckAfterProcessing();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new RewriteObjectSpread(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Test
  public void testObjectLiteralWithSpread() {
    test("({first, ...spread});", "Object.assign({}, {first}, spread)");
    test("({first, second, ...spread});", "Object.assign({}, {first, second}, spread)");
    test("({...spread, last});", "Object.assign({}, spread, {last})");
    test("({...spread, penultimate, last});", "Object.assign({}, spread, {penultimate, last})");
    test(
        "({before, ...spread1, mid1, mid2, ...spread2, after});",
        "Object.assign({}, {before}, spread1, {mid1, mid2}, spread2, {after})");
    test("({first, ...{...nested}});", "Object.assign({}, {first}, Object.assign({}, nested))");
    test(
        "({first, [foo()]: baz(), ...spread});",
        "Object.assign({}, {first, [foo()]: baz()}, spread)");
  }
}
