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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.Config;
import com.google.javascript.rhino.Node;
import java.nio.charset.Charset;
import org.junit.Before;

/** Base class for tests that exercise {@link CodePrinter}. */
public abstract class CodePrinterTestBase {
  // If this is set, ignore parse warnings and only fail the test
  // for parse errors.
  protected boolean allowWarnings = false;
  protected boolean trustedStrings = true;
  protected boolean preserveTypeAnnotations = false;
  protected boolean preserveNonJSDocComments = false;
  protected LanguageMode languageMode = LanguageMode.ECMASCRIPT5;
  protected Compiler lastCompiler = null;
  protected Charset outputCharset = null;

  @Before
  public void setUp() throws Exception {
    allowWarnings = false;
    preserveTypeAnnotations = false;
    preserveNonJSDocComments = false;
    trustedStrings = true;
    lastCompiler = null;
    languageMode = LanguageMode.UNSUPPORTED;
    outputCharset = null;
  }

  Node parse(String js) {
    return parse(js, /* typeChecked= */ false);
  }

  Node parse(String js, boolean typeChecked) {
    Compiler compiler = new Compiler();
    lastCompiler = compiler;
    CompilerOptions options = new CompilerOptions();
    options.setTrustedStrings(trustedStrings);
    options.preserveTypeAnnotations = preserveTypeAnnotations;
    options.setOutputCharset(outputCharset);
    options.setLanguageIn(languageMode);
    if (preserveNonJSDocComments) {
      options.setParseJsDocDocumentation(Config.JsDocParsing.INCLUDE_ALL_COMMENTS);
      options.setPreserveNonJSDocComments(true);
    }
    compiler.init(
        ImmutableList.of(SourceFile.fromCode("externs", CompilerTestCase.MINIMAL_EXTERNS)),
        ImmutableList.of(SourceFile.fromCode("testcode", js)),
        options);
    Node externsAndJs = compiler.parseInputs();
    checkUnexpectedErrorsOrWarnings(compiler, 0);
    Node root = externsAndJs.getLastChild();
    Node externs = externsAndJs.getFirstChild();

    if (typeChecked) {
      DefaultPassConfig passConfig = new DefaultPassConfig(null);
      CompilerPass inferTypes = passConfig.inferTypes.create(compiler);
      inferTypes.process(externs, root);
    }

    checkUnexpectedErrorsOrWarnings(compiler, 0);
    return root.getFirstChild();
  }

  private void checkUnexpectedErrorsOrWarnings(
      Compiler compiler, int expected) {
    int actual = compiler.getErrors().size();
    if (!allowWarnings) {
      actual += compiler.getWarnings().size();
    }

    if (actual != expected) {
      String msg = "";
      for (JSError err : compiler.getErrors()) {
        msg += "Error:" + err + "\n";
      }
      if (!allowWarnings) {
        for (JSError err : compiler.getWarnings()) {
          msg += "Warning:" + err + "\n";
        }
      }
      assertWithMessage("Unexpected warnings or errors.\n " + msg).that(actual).isEqualTo(expected);
    }
  }

  String parsePrint(String js, CompilerOptions options) {
    return new CodePrinter.Builder(parse(js)).setCompilerOptions(options).build();
  }

  abstract static class CompilerOptionBuilder {
    abstract void setOptions(CompilerOptions options);
  }

  CompilerOptions newCompilerOptions(CompilerOptionBuilder builder) {
    CompilerOptions options = new CompilerOptions();
    options.setOutputCharset(outputCharset);
    options.setTrustedStrings(trustedStrings);
    options.preserveTypeAnnotations = preserveTypeAnnotations;
    options.setPreserveNonJSDocComments(preserveNonJSDocComments);
    options.setLanguageOut(languageMode);
    builder.setOptions(options);
    return options;
  }

  String printNode(Node n) {
    CompilerOptions options = new CompilerOptions();
    options.setLineLengthThreshold(CompilerOptions.DEFAULT_LINE_LENGTH_THRESHOLD);
    options.setLanguageOut(languageMode);
    options.setOutputCharset(outputCharset);
    return new CodePrinter.Builder(n).setCompilerOptions(options).build();
  }

  void assertPrintNode(String expectedJs, Node ast) {
    assertThat(printNode(ast)).isEqualTo(expectedJs);
  }

  String prettyPrintNode(Node n) {
    CompilerOptions options = new CompilerOptions();
    options.setLineLengthThreshold(CompilerOptions.DEFAULT_LINE_LENGTH_THRESHOLD);
    options.setLanguageOut(languageMode);
    options.setOutputCharset(outputCharset);
    options.setPrettyPrint(true);
    return new CodePrinter.Builder(n).setCompilerOptions(options).build();
  }

  void assertPrettyPrintNode(String expectedJs, Node ast) {
    assertThat(prettyPrintNode(ast)).isEqualTo(expectedJs);
  }

  protected void assertPrint(String js, String expected) {
    parse(expected); // validate the expected string is valid JS
    assertThat(
            parsePrint(
                js,
                newCompilerOptions(
                    new CompilerOptionBuilder() {
                      @Override
                      void setOptions(CompilerOptions options) {
                        options.setPrettyPrint(false);
                        options.setLineLengthThreshold(
                            CompilerOptions.DEFAULT_LINE_LENGTH_THRESHOLD);
                      }
                    })))
        .isEqualTo(expected);
  }

  protected void assertPrintSame(String js) {
    assertPrint(js, js);
  }
}
