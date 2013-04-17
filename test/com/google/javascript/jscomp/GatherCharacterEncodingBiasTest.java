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

import java.util.HashSet;

/**
 * Tests for {@link GatherCharacterEncodingBias}.
 */
public class GatherCharacterEncodingBiasTest extends CompilerTestCase {

  private NameGenerator generator;
  private boolean renameGlobalVars;
  private boolean renameProperties;

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    generator = new NameGenerator(new HashSet<String>(0), "", null);
    return new GatherCharacterEncodingBias(
        compiler, generator, renameGlobalVars, renameProperties);
  }

  @Override
  protected void setUp() {
    renameGlobalVars = true;
    renameProperties = true;
  }

  @Override
  protected int getNumRepetitions() {
    return 1; // Once only.
  }

  public void testGathering() {
    testSame("function j() { return j()}");
    generator.restartNaming();
    assertEquals("n", generator.generateNextName());
    assertEquals("r", generator.generateNextName());
    assertEquals("t", generator.generateNextName());
    assertEquals("u", generator.generateNextName());
    assertEquals("c", generator.generateNextName());
    assertEquals("e", generator.generateNextName());
    assertEquals("f", generator.generateNextName());
    assertEquals("i", generator.generateNextName());
    assertEquals("o", generator.generateNextName());
    assertEquals("a", generator.generateNextName());
    assertEquals("b", generator.generateNextName());
  }

  public void testGathering2() {
    testSame("if(a){}else{}");
    generator.restartNaming();
    assertEquals("e", generator.generateNextName());
  }

  public void testGathering3() {
    testSame("switch(a){default:}");
    generator.restartNaming();
    assertEquals("t", generator.generateNextName());
  }

  public void testGathering4() {
    testSame("a instanceof b");
    generator.restartNaming();
    assertEquals("n", generator.generateNextName());
  }

  public void testGathering5() {
    testSame("a['zzzz']");
    generator.restartNaming();
    assertEquals("z", generator.generateNextName());
  }

  public void testGathering6() {
    testSame("this");
    generator.restartNaming();
    assertEquals("h", generator.generateNextName());
    assertEquals("i", generator.generateNextName());
    assertEquals("s", generator.generateNextName());
    assertEquals("t", generator.generateNextName());
    assertEquals("a", generator.generateNextName());
  }

  public void testGatheringGetterSetter() {
    testSame("var x = { get y(){}, set y(val){} }");
    generator.restartNaming();
    assertEquals("e", generator.generateNextName()); // Twice each
    assertEquals("t", generator.generateNextName());

    assertEquals("a", generator.generateNextName()); // Once each
    assertEquals("g", generator.generateNextName());
  }

  public void testGatheringDebugger() {
    testSame("debugger;");
    generator.restartNaming();
    assertEquals("e", generator.generateNextName());
    assertEquals("g", generator.generateNextName());
    assertEquals("b", generator.generateNextName());
    assertEquals("d", generator.generateNextName());
    assertEquals("r", generator.generateNextName());
    assertEquals("u", generator.generateNextName());
    assertEquals("a", generator.generateNextName());
  }

  public void testGatheringNames1() {
    testSame("var MMMMMM");
    generator.restartNaming();
    assertEquals("a", generator.generateNextName());
  }

  public void testGatheringNames2() {
    renameGlobalVars = false;
    testSame("var MMMMMM");
    generator.restartNaming();
    assertEquals("M", generator.generateNextName());
    assertEquals("a", generator.generateNextName());
  }

  public void testGatheringNames3() {
    testSame("var x = function() { var MMMMMMMMMMMMMMMMMMM }");
    generator.restartNaming();
    assertNotSame("M", generator.generateNextName());
  }

  public void testGatheringProperties1() {
    renameProperties = false;
    testSame("a.mmm.MMMMM");
    generator.restartNaming();
    assertEquals("M", generator.generateNextName());
    assertEquals("m", generator.generateNextName());
    assertEquals("a", generator.generateNextName());
  }

  public void testGatheringProperties2() {
    renameProperties = true;
    testSame("mmm.MMMMM");
    generator.restartNaming();
    assertNotSame("M", generator.generateNextName());
  }

  public void testGatheringMixed() {
    renameGlobalVars = false;
    renameProperties = true;
    testSame("mmm.MMMMMMMMMMMMMMM");
    generator.restartNaming();
    assertEquals("m", generator.generateNextName());
    assertNotSame("M", generator.generateNextName());
  }

  public void testGatheringGetter() {
    renameProperties = false;
    testSame("({get ZZZZZ(){return 1}})");
    generator.restartNaming();
    assertEquals("Z", generator.generateNextName());
  }

  public void testGatheringSetter() {
    renameProperties = false;
    testSame("({set ZZZZZ(z){}})");
    generator.restartNaming();
    assertEquals("Z", generator.generateNextName());
  }
}
