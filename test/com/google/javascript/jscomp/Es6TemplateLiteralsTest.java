/*
 * Copyright 2026 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class Es6TemplateLiteralsTest extends CompilerTestCase {

  private static final ImmutableMap<String, String> REPLACEMENT_PREFIXES =
      ImmutableMap.of("TAGGED_TEMPLATE_TMP_VAR", "$jscomp$templatelit$");

  private static final String RUNTIME_STUBS =
      """
      /** @const */
      var $jscomp = {};
      $jscomp.createTemplateTagFirstArg = function(arrayStrings) {};
      $jscomp.createTemplateTagFirstArgWithRaw = function(anotherArray, rawArrayStrings) {};
      """;

  public Es6TemplateLiteralsTest() {
    super(MINIMAL_EXTERNS + RUNTIME_STUBS);
  }

  @Before
  public void customSetUp() throws Exception {
    enableNormalize();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    setLanguageOut(LanguageMode.ECMASCRIPT3);
    enableTypeCheck();
    enableTypeInfoValidation();
    replaceTypesWithColors();
    enableMultistageCompilation();
    setGenericNameReplacements(REPLACEMENT_PREFIXES);
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    PhaseOptimizer optimizer = new PhaseOptimizer(compiler, null);
    optimizer.addOneTimePass(
        makePassFactory(
            "injectTranspilationRuntimeLibraries", InjectTranspilationRuntimeLibraries::new));
    optimizer.addOneTimePass(makePassFactory("lateEs6ToEs3Converter", LateEs6ToEs3Converter::new));
    return optimizer;
  }

  @Test
  public void testTaggedTemplateLiteral_nameTag() {
    test(
        "tag`hello`",
        """
        /** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =
            $jscomp.createTemplateTagFirstArg(['hello']);
        tag(TAGGED_TEMPLATE_TMP_VAR$0);
        """);
  }

  @Test
  public void testTaggedTemplateLiteral_getPropTag() {
    test(
        externs(RUNTIME_STUBS, "var obj = {}; obj.tag;"),
        srcs("obj.tag`hello`"),
        expected(
            """
            /** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =
                $jscomp.createTemplateTagFirstArg(['hello']);
            obj.tag(TAGGED_TEMPLATE_TMP_VAR$0);
            """));
  }

  // TODO(b/538150835): Do not mark property tagged template calls as FREE_CALL in
  // Es6TemplateLiterals
  @Test
  public void testTaggedTemplateLiteral_getElemTag() {
    test(
        externs(RUNTIME_STUBS, "var obj = {}; var tag; obj[tag];"),
        srcs("obj[tag]`hello`"),
        expected(
            """
            /** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =
                $jscomp.createTemplateTagFirstArg(['hello']);
            (0, obj[tag])(TAGGED_TEMPLATE_TMP_VAR$0);
            """));
  }
}
