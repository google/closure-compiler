/*
 * Copyright 2016 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * Tests for hotswap functionality of the ClosureRewriteModule pass.
 */

public final class GoogModuleReplaceScriptTest extends BaseReplaceScriptTestCase {
  private void runNoOpReplaceScriptNoWarnings(List<String> sources) {
    for (int i = 0; i < sources.size(); i++) {
      runReplaceScriptNoWarnings(sources, sources.get(i), i);
    }
  }

  public void testGoogModule1() {
    String source0 =
        LINE_JOINER.join(
            "goog.module('ns.Bar');", "", "/** @constructor */ exports = function() {};");
    String source1 =
        LINE_JOINER.join(
            "goog.module('ns.Baz');",
            "var Bar = goog.require('ns.Bar');",
            "",
            "var a = new Bar();");
    runNoOpReplaceScriptNoWarnings(ImmutableList.of(source0, source1));
  }

  public void testGoogModule2() {
    String source0 =
        LINE_JOINER.join(
            "goog.module('ns.Bar');", "", "/** @constructor */ exports = function() {};");
    String source1 =
        LINE_JOINER.join(
            "goog.module('ns.Baz');",
            "var Bar = goog.require('ns.Bar');",
            "",
            "var a = new Bar();");
    String newSource1 =
        LINE_JOINER.join(
            "goog.module('ns.Baf');",
            "var Bar = goog.require('ns.Bar');",
            "",
            "var a = new Bar();");
    runReplaceScriptNoWarnings(ImmutableList.of(source0, source1), newSource1, 1);
  }

  public void testGoogModule3() {
    String source0 =
        LINE_JOINER.join(
            "goog.module('ns.Bar');",
            "goog.module.declareLegacyNamespace();",
            "",
            "/** @constructor */ exports = function() {};");
    String source1 =
        LINE_JOINER.join(
            "goog.module('ns.Baz');",
            "var Bar = goog.require('ns.Bar');",
            "",
            "var a = new Bar();");
    runNoOpReplaceScriptNoWarnings(ImmutableList.of(source0, source1));
  }

  public void testGoogModule4() {
    String source0 =
        LINE_JOINER.join(
            "goog.module('ns.Bar');",
            "goog.module.declareLegacyNamespace();",
            "",
            "/** @constructor */ exports = function() {};");
    String source1 =
        LINE_JOINER.join(
            "goog.provide('ns.Baz');", "goog.require('ns.Bar');", "", "var a = new ns.Bar();");
    runNoOpReplaceScriptNoWarnings(ImmutableList.of(source0, source1));
  }
}
