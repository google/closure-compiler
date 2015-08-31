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

import com.google.common.collect.ImmutableSet;
import java.util.Set;

/**
 * Tests for {@link GatherRawExports}.
 *
 * @author johnlenz@google.com (John Lenz)
 */
public final class GatherRawExportsTest extends CompilerTestCase {

  private static final String EXTERNS = "var window;";
  private GatherRawExports last;

  public GatherRawExportsTest() {
    super(EXTERNS);
    super.enableNormalize();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    last = new GatherRawExports(compiler);
    return last;
  }

  public void testExportsFound1() {
    assertExported("var a");
  }

  public void testExportsFound2() {
    assertExported("window['a']", "a");
  }

  public void testExportsFound3() {
    assertExported("window.a", "a");
  }

  public void testExportsFound4() {
    assertExported("this['a']", "a");
  }

  public void testExportsFound5() {
    assertExported("this.a", "a");
  }

  public void testExportsFound6() {
    assertExported("function f() { this['a'] }");
  }

  public void testExportsFound7() {
    assertExported("function f() { this.a }");
  }

  public void testExportsFound8() {
    assertExported("window['foo']", "foo");
  }

  public void testExportsFound9() {
    assertExported("window['a'] = 1;", "a");
  }

  public void testExportsFound10() {
    assertExported("window['a']['b']['c'] = 1;", "a");
  }

  public void testExportsFound11() {
    assertExported("if (window['a'] = 1) alert(x);", "a");
  }

  public void testExportsFound12() {
    assertExported("function foo() { window['a'] = 1; }", "a");
  }

  public void testExportsFound13() {
    assertExported("function foo() {var window; window['a'] = 1; }");
  }

  public void testExportsFound14() {
    assertExported("var a={window:{}}; a.window['b']");
  }

  public void testExportsFound15() {
    assertExported("window.window['b']", "window");
  }

  public void testExportsFound16() {
    // It would be nice to handle this case, hopefully inlining will take care
    // of it for us.
    assertExported("var a = window; a['b']");
  }

  public void testExportsFound17() {
    // Gather "this" reference in a global if block.
    assertExported("if (true) { this.a }", "a");
    // Does not gather "this" reference in a local if block.
    assertExported("function f() { if (true) { this.a } }");
  }

  public void testExportOnTopFound1() {
    assertExported("top['a']", "a");
  }

  public void testExportOntopFound2() {
    assertExported("top.a", "a");
  }

  public void testExportOnGoogGlobalFound1() {
    assertExported("goog.global['a']", "a");
  }

  public void testExportOnGoogGlobalFound2() {
    assertExported("goog.global.a", "a");
  }

  public void testExportOnGoogGlobalFound3() {
    assertExported("goog$global['a']", "a");
  }

  public void testExportOnGoogGlobalFound4() {
    assertExported("goog$global.a", "a");
  }

  private void assertExported(String js, String ... names) {
    Set<String> setNames = ImmutableSet.copyOf(names);
    testSame(js);
    assertEquals(setNames, last.getExportedVariableNames());
  }
}
