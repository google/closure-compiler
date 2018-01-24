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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import junit.framework.TestCase;

/**
 * Tests for {@link CompilerOptions}.
 * @author nicksantos@google.com (Nick Santos)
 */
public final class CompilerOptionsTest extends TestCase {

  public void testDefines() throws Exception {
    CompilerOptions options = new CompilerOptions();
    options.setDefineToBooleanLiteral("trueVar", true);
    options.setDefineToBooleanLiteral("falseVar", false);
    options.setDefineToNumberLiteral("threeVar", 3);
    options.setDefineToStringLiteral("strVar", "str");

    Map<String, Node> actual = options.getDefineReplacements();
    assertEquivalent(new Node(Token.TRUE), actual.get("trueVar"));
    assertEquivalent(new Node(Token.FALSE), actual.get("falseVar"));
    assertEquivalent(Node.newNumber(3), actual.get("threeVar"));
    assertEquivalent(Node.newString("str"), actual.get("strVar"));
  }

  public void assertEquivalent(Node a, Node b) {
    assertTrue(a.isEquivalentTo(b));
  }

  public void testLanguageModeFromString() {
    assertEquals(LanguageMode.ECMASCRIPT3, LanguageMode.fromString("ECMASCRIPT3"));
    // Whitespace should be trimmed, characters converted to uppercase and leading 'ES' replaced
    // with 'ECMASCRIPT'.
    assertEquals(LanguageMode.ECMASCRIPT3, LanguageMode.fromString("  es3  "));
    assertNull(LanguageMode.fromString("junk"));
  }

  public void testEmitUseStrictWorksInEs3() {
    CompilerOptions options = new CompilerOptions();
    options.setEmitUseStrict(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);

    assertTrue(options.shouldEmitUseStrict());
  }

  public void testSerialization() throws Exception {
    CompilerOptions options = new CompilerOptions();
    options.setDefineToBooleanLiteral("trueVar", true);
    options.setDefineToBooleanLiteral("falseVar", false);
    options.setDefineToNumberLiteral("threeVar", 3);
    options.setDefineToStringLiteral("strVar", "str");
    options.setAliasableStrings(new HashSet<>(Arrays.asList("AliasA", "AliasB")));
    options.setOptimizeArgumentsArray(true);
    options.setAmbiguateProperties(false);
    options.setOutputCharset(StandardCharsets.US_ASCII);

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    options.serialize(byteArrayOutputStream);

    options =
        CompilerOptions.deserialize(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));

    Map<String, Node> actual = options.getDefineReplacements();
    assertEquivalent(new Node(Token.TRUE), actual.get("trueVar"));
    assertEquivalent(new Node(Token.FALSE), actual.get("falseVar"));
    assertEquivalent(Node.newNumber(3), actual.get("threeVar"));
    assertEquivalent(Node.newString("str"), actual.get("strVar"));
    assertEquals(new HashSet<>(Arrays.asList("AliasA", "AliasB")), options.aliasableStrings);
    assertFalse(options.shouldAmbiguateProperties());
    assertTrue(options.optimizeArgumentsArray);
    assertEquals(StandardCharsets.US_ASCII, options.getOutputCharset());
  }

}
