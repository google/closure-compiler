/*
 * Copyright 2013 The Closure Compiler Authors.
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

/**
 * Unit test for the Compiler DisambiguatPrivateeProperties pass.
 *
 * @author johnlenz@google.com (John Lenz)
 */
public final class DisambiguatePrivatePropertiesTest extends CompilerTestCase {

  private boolean useGoogleCodingConvention = true;

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new DisambiguatePrivateProperties(compiler);
  }

  @Override
  protected CodingConvention getCodingConvention() {
    if (useGoogleCodingConvention) {
      return new GoogleCodingConvention();
    } else {
      return new ClosureCodingConvention();
    }
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  protected void setUp() throws Exception {
    useGoogleCodingConvention = true;
  }

  public void testNoRenaming1() {
    useGoogleCodingConvention = true;

    // Not variables
    testSame("var x_");

    // Not standard props
    testSame("x.prop");

    // Not annotated props (we don't use type information)
    testSame("var x = {};\n/** @private */ x.prop;");


    // Not quoted props
    testSame("({})['prop_'];");
    testSame("({'prop_': 1});");
    testSame("({get 'prop_'(){ return 1} });");
    testSame("({set 'prop_'(a){ this.a = 1} });");

    useGoogleCodingConvention = false;

    // Not when the coding convention doesn't understand it.
    testSame("({}).prop_;");
    testSame("({prop_: 1});");
    testSame("({get prop_(){ return 1} });");
    testSame("({set prop_(a){ this.a = 1} });");
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    testSame("({prop_(){ return 1} });");
    testSame("class A{method_(){return 1} }");
  }

  public void testRenaming1() {
    useGoogleCodingConvention = true;

    test(
        "({}).prop_;",
        "({}).prop_$0;");

    test(
        "({prop_: 1});",
        "({prop_$0: 1});");

    test(
        "({get prop_(){ return 1} });",
        "({get prop_$0(){ return 1} });");

    test(
        "({set prop_(a){ this.a = 1} });",
        "({set prop_$0(a){ this.a = 1} });");
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    test(
        "({prop_(){ return 1} });",
        "({prop_$0(){ return 1} });");
    test(
        "class A{method_(){return 1} }",
        "class A{method_$0(){return 1} }");
  }

  public void testNoRenameIndirectProps() {
    useGoogleCodingConvention = true;

    testSame("({}).superClass_;");
    testSame("({superClass_: 1});");
    testSame("({get superClass_(){ return 1} });");
    testSame("({set superClass_(a){this.a = 1} });");
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    testSame("({superClass_(){ return 1} });");
  }
}
