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

package com.google.javascript.jscomp;

/**
 * Tests for {@link ObjectLitAssignmentShortening}.
 *
 */
public final class ObjectLitAssignmentShorteningTest extends Es6CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ObjectLitAssignmentShortening(compiler);
  }

  public void testSimpleShortening() {
    testEs6("var a = {x: x}", "var a = {x}");
    testEs6("var a = {x: function(){}}", "var a = {x(){}}");
  }

  public void testMultipleShortening() {
    testEs6("var a = {x: x, y: y, z: z}", "var a = {x, y, z}");
    testEs6("var a = {x: x, y: y, z: function(){}}", "var a = {x, y, z(){}}");
    testEs6("var a = {x: function(){}, y: y, z: z}", "var a = {x(){}, y, z}");
  }

  public void testNoShortening() {
    testSame("var a = {x: 1}");
    testSame("var a = {x: y}");
    testSame("var a = {x: x + 1}");
    testSame("var a = {x: x - 1}");
    testSame("var a = {x: x * 1}");
    testSame("var a = {x: x / 1}");
    testSame("var a = {x: c()}");
    testSame("var a = {x: {y: 3}}");
    testSame("var a = {x: {y: z}}");

    testSameEs6("var a = {__proto__:x}");
    testSameEs6("var a = {['prop' + (()=>42)()]: x}");
    testSameEs6("var a = {x: ()=>42}");
    testSameEs6("var a = {x: (()=>42)()}");

    // Destructuring is not shortened
    testSameEs6("var { a: b, c: d } = options");
  }

  public void testNestedShortening() {
    testEs6("var a = {x: {y: y}}", "var a = {x: {y}}");
    testEs6("var a = {x: {y: function(){}}}", "var a = {x: {y(){}}}");
    testEs6(LINE_JOINER.join(
        "var a = {",
        "  x: {",
        "    y: function(){",
        "      return {z:z}",
        "    }",
        "  }",
        "}"), LINE_JOINER.join(
        "var a = {",
        "  x: {",
        "    y(){",
        "      return {z}",
        "    }",
        "  }",
        "}"));
    testEs6(LINE_JOINER.join(
        "var a = {",
        "  x: {",
        "    y: function(){",
        "      return {z: function(){}}",
        "    }",
        "  }",
        "}"), LINE_JOINER.join(
        "var a = {",
        "  x: {",
        "    y(){",
        "      return {z(){}}",
        "    }",
        "  }",
        "}"));
  }

  public void testNestedMultipleShortening() {
    testEs6(LINE_JOINER.join(
        "var a = {",
        "  x: {",
        "    y: function(){",
        "      return {z:z}",
        "    }",
        "  },",
        "  p: {",
        "    q: function(){",
        "      return {r:r}",
        "    },",
        "    s: function(){}",
        "  }",
        "}"), LINE_JOINER.join(
        "var a = {",
        "  x: {",
        "    y(){",
        "      return {z}",
        "    }",
        "  },",
        "  p: {",
        "    q(){",
        "      return {r}",
        "    },",
        "    s(){}",
        "  }",
        "}"));
  }

  public void testObjLitInFunction() {
    testEs6(LINE_JOINER.join(
        "var a = function(){",
        "  x = {",
        "    y: function(){",
        "      return {z: function(){}}",
        "    }",
        "  }",
        "}"), LINE_JOINER.join(
        "var a = function(){",
        "  x = {",
        "    y(){",
        "      return {z(){}}",
        "    }",
        "  }",
        "}"));
  }
}