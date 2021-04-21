/*
 * Copyright 2010 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CompilerOptions.ChunkOutputType;
import com.google.javascript.jscomp.CompilerOptions.PropertyCollapseLevel;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.ReplaceStrings.Result;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.jscomp.disambiguate.DisambiguateProperties2;
import com.google.javascript.rhino.Node;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ReplaceStrings}. */
@RunWith(JUnit4.class)
public final class ReplaceStringsTest extends CompilerTestCase {
  private ReplaceStrings pass;
  private boolean runDisambiguateProperties;
  private boolean rename;

  private final ImmutableList<String> defaultFunctionsToInspect =
      ImmutableList.of(
          "Error(?)",
          "goog.debug.Trace.startTracer(*)",
          "goog.debug.Logger.getLogger(?)",
          "goog.log.getLogger(?)",
          "goog.log.info(,?)",
          "goog.log.multiString(,?,?,)",
          "Excluded(?):!testcode",
          "NotExcluded(?):!unmatchable");

  private ImmutableList<String> functionsToInspect;

  private static final String EXTERNS =
      lines(
          MINIMAL_EXTERNS,
          "var goog = {};",
          "goog.debug = {};",
          "/** @constructor */",
          "goog.debug.Trace = function() {};",
          "goog.debug.Trace.startTracer = function (var_args) {};",
          "/** @constructor */",
          "goog.debug.Logger = function() {};",
          "goog.debug.Logger.prototype.info = function(msg, opt_ex) {};",
          "/**",
          " * @param {?} name",
          " * @return {!goog.debug.Logger}",
          " */",
          "goog.debug.Logger.getLogger = function(name){};",
          "goog.log = {}",
          "goog.log.getLogger = function(name){};",
          "goog.log.info = function(logger, msg, opt_ex) {};",
          "goog.log.multiString = function(logger, replace1, replace2, keep) {};");

  public ReplaceStringsTest() {
    super(EXTERNS);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.OFF);
    return options;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
    enableNormalize();
    enableParseTypeInfo();
    functionsToInspect = defaultFunctionsToInspect;
    runDisambiguateProperties = false;
    rename = false;
  }

  private static class Renamer extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isName() || n.isGetProp()) {
        String originalName = n.getString();
        n.setOriginalName(originalName);
        n.setString("renamed_" + originalName);
        t.reportCodeChange();
      }
    }
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    pass = new ReplaceStrings(compiler, "`", functionsToInspect);

    return new CompilerPass() {
      @Override
      public void process(Node externs, Node js) {
        if (rename) {
          NodeTraversal.traverse(compiler, js, new Renamer());
        }
        new CollapseProperties(
                compiler,
                PropertyCollapseLevel.ALL,
                ChunkOutputType.GLOBAL_NAMESPACE,
                false,
                ResolutionMode.BROWSER)
            .process(externs, js);
        if (runDisambiguateProperties) {
          SourceInformationAnnotator sia = SourceInformationAnnotator.create();
          NodeTraversal.traverse(compiler, js, sia);

          new DisambiguateProperties2(compiler, ImmutableSet.of("foobar")).process(externs, js);
        }
        pass.process(externs, js);
      }
    };
  }

  @Override
  protected int getNumRepetitions() {
    // This compiler pass is not idempotent and should only be run over a
    // parse tree once.
    return 1;
  }

  @Test
  public void testRenameName() {
    rename = true;
    testDebugStrings("Error('xyz');", "renamed_Error('a');", (new String[] {"a", "xyz"}));
  }

  @Test
  public void testRenameStaticProp() {
    rename = true;
    testDebugStrings(
        "goog.debug.Trace.startTracer('HistoryManager.updateHistory');",
        "renamed_goog.renamed_debug.renamed_Trace.renamed_startTracer('a');",
        (new String[] {"a", "HistoryManager.updateHistory"}));
  }

  @Test
  public void testThrowError2() {
    testDebugStrings(
        "throw Error('x' +\n    'yz');", "throw Error('a');", (new String[] {"a", "xyz"}));
  }

  @Test
  public void testThrowError3() {
    testDebugStrings(
        "throw Error('Unhandled mail' + ' search type ' + type);",
        "throw Error('a' + '`' + type);",
        (new String[] {"a", "Unhandled mail search type `"}));
  }

  @Test
  public void testThrowError3a() {
    testDebugStrings(
        lines(
            "/** @const */ var preposition = 'in';",
            "/** @const */ var action = 'search';",
            "/** @const */ var error = 'Unhandled ' + action;",
            "throw Error(error + ' ' + type + ' ' + preposition + ' ' + search);"),
        lines(
            "/** @const */ var preposition = 'in';",
            "/** @const */ var action = 'search';",
            "/** @const */ var error = 'Unhandled ' + action;",
            "throw Error('a' + '`' + type + '`' + search);"),
        (new String[] {"a", "Unhandled search ` in `"}));
  }

  @Test
  public void testThrowError4() {
    testDebugStrings(
        lines(
            "/** @constructor */",
            "var A = function() {};",
            "A.prototype.m = function(child) {",
            "  if (this.haveChild(child)) {",
            "    throw Error('Node: ' + this.getDataPath() +",
            "                ' already has a child named ' + child);",
            "  } else if (child.parentNode) {",
            "    throw Error('Node: ' + child.getDataPath() +",
            "                ' already has a parent');",
            "  }",
            "  child.parentNode = this;",
            "};"),
        lines(
            "/** @constructor */",
            "var A = function(){};",
            "A.prototype.m = function(child) {",
            "  if (this.haveChild(child)) {",
            "    throw Error('a' + '`' + this.getDataPath() + '`' + child);",
            "  } else if (child.parentNode) {",
            "    throw Error('b' + '`' + child.getDataPath());",
            "  }",
            "  child.parentNode = this;",
            "};"),
        (new String[] {
          "a", "Node: ` already has a child named `", "b", "Node: ` already has a parent",
        }));
  }

  @Test
  public void testThrowError_templateLiteral() {
    testDebugStrings(
        "throw Error(`Unhandled search ${type} in ${search}`);",
        "throw Error('a' + '`' + type + '`' + search);",
        (new String[] {"a", "Unhandled search ` in `"}));
  }

  @Test
  public void testThrowError_templateLiteralWithConstantTerms() {
    testDebugStrings(
        lines(
            "const preposition = 'in';",
            "const action = 'search';",
            "const error = `Unhandled ${action}`;",
            "throw Error(`${error} ${'type ' + getType()} ${preposition} ${search}`);"),
        lines(
            "const preposition = 'in';",
            "const action = 'search';",
            "const error = `Unhandled ${action}`;",
            "throw Error('a' + '`' + getType() + '`' + search);"),
        (new String[] {"a", "Unhandled search type ` in `"}));
  }

  @Test
  public void testThrowError_templateLiteralWithNonconstantTerms() {
    // NOTE: We can only inline error if it is *transitively* constant. Otherwise it is left alone,
    // since any transitive strings may have changed, and we certainly don't want to call functions
    // a second time.
    testDebugStrings(
        lines(
            "const error = `Unhandled ${action}`;",
            "throw Error(`${error} ${type} in ${search}`);"),
        lines(
            "const error = `Unhandled ${action}`;",
            "throw Error('a' + '`' + error + '`' + type + '`' + search);"),
        (new String[] {"a", "` ` in `"}));
  }

  @Test
  public void testThrowError_templateLiteralConcatenation() {
    testDebugStrings(
        "throw Error(`Unhandled mail` + ` search type ${type}`);",
        "throw Error('a' + '`' + type);",
        (new String[] {"a", "Unhandled mail search type `"}));
  }

  @Test
  public void testThrowNonStringError() {
    // No replacement is done when an error is neither a string literal nor
    // a string concatenation expression.
    testDebugStrings("throw Error(x('abc'));", "throw Error(x('abc'));", (new String[] {}));
  }

  @Test
  public void testThrowError_taggedTemplateLiteral() {
    // No replacement is done when there is a tag function.
    testDebugStrings(
        "throw Error(x`abc`);", //
        "throw Error(x`abc`);",
        (new String[] {}));
  }

  @Test
  public void testThrowConstStringError() {
    testDebugStrings(
        "var AA = 'uvw', AB = AA + 'xyz'; throw Error(AB);",
        "var AA = 'uvw', AB = AA + 'xyz'; throw Error('a');",
        (new String[] {"a", "uvwxyz"}));
  }

  @Test
  public void testThrowConstStringError_templateLiteral() {
    testDebugStrings(
        "var AA = 'uvw', AB = `${AA}xyz`; throw Error(AB);",
        "var AA = 'uvw', AB = `${AA}xyz`; throw Error('a');",
        (new String[] {"a", "uvwxyz"}));
  }

  @Test
  public void testThrowNewError1() {
    testDebugStrings(
        "throw new Error('abc');", "throw new Error('a');", (new String[] {"a", "abc"}));
  }

  @Test
  public void testThrowNewError2() {
    testDebugStrings("throw new Error();", "throw new Error();", new String[] {});
  }

  @Test
  public void testStartTracer1() {
    testDebugStrings(
        "goog.debug.Trace.startTracer('HistoryManager.updateHistory');",
        "goog.debug.Trace.startTracer('a');",
        (new String[] {"a", "HistoryManager.updateHistory"}));
  }

  @Test
  public void testStartTracer2() {
    testDebugStrings(
        "goog$debug$Trace.startTracer('HistoryManager', 'updateHistory');",
        "goog$debug$Trace.startTracer('a', 'b');",
        (new String[] {
          "a", "HistoryManager",
          "b", "updateHistory"
        }));
  }

  @Test
  public void testStartTracer3() {
    testDebugStrings(
        "goog$debug$Trace.startTracer('ThreadlistView',\n"
            + "                             'Updating ' + array.length + ' rows');",
        "goog$debug$Trace.startTracer('a', 'b' + '`' + array.length);",
        new String[] {"a", "ThreadlistView", "b", "Updating ` rows"});
  }

  @Test
  public void testStartTracer4() {
    testDebugStrings(
        "goog.debug.Trace.startTracer(s, 'HistoryManager.updateHistory');",
        "goog.debug.Trace.startTracer(s, 'a');",
        (new String[] {"a", "HistoryManager.updateHistory"}));
  }

  @Test
  public void testLoggerInitialization() {
    testDebugStrings(
        "goog$debug$Logger$getLogger('my.app.Application');",
        "goog$debug$Logger$getLogger('a');",
        (new String[] {"a", "my.app.Application"}));
  }

  @Test
  public void testLoggerOnVar() {
    testDebugStrings(
        "var logger = goog.debug.Logger.getLogger('foo');" + "logger.info('Some message');",
        "var logger = goog.debug.Logger.getLogger('a');" + "logger.info('Some message');",
        new String[] {"a", "foo"});
  }

  @Test
  public void testRepeatedErrorString1() {
    testDebugStrings(
        "Error('abc');Error('def');Error('abc');",
        "Error('a');Error('b');Error('a');",
        (new String[] {"a", "abc", "b", "def"}));
  }

  @Test
  public void testRepeatedErrorString2() {
    testDebugStrings(
        "Error('a:' + u + ', b:' + v); Error('a:' + x + ', b:' + y);",
        "Error('a' + '`' + u + '`' + v); Error('a' + '`' + x + '`' + y);",
        (new String[] {"a", "a:`, b:`"}));
  }

  @Test
  public void testRepeatedErrorString3() {
    testDebugStrings(
        "var AB = 'b'; throw Error(AB); throw Error(AB);",
        "var AB = 'b'; throw Error('a'); throw Error('a');",
        (new String[] {"a", "b"}));
  }

  @Test
  public void testRepeatedTracerString() {
    testDebugStrings(
        "goog$debug$Trace.startTracer('A', 'B', 'A');",
        "goog$debug$Trace.startTracer('a', 'b', 'a');",
        (new String[] {"a", "A", "b", "B"}));
  }

  @Test
  public void testRepeatedLoggerString() {
    testDebugStrings(
        "goog$debug$Logger$getLogger('goog.net.XhrTransport');"
            + "goog$debug$Logger$getLogger('my.app.Application');"
            + "goog$debug$Logger$getLogger('my.app.Application');",
        "goog$debug$Logger$getLogger('a');"
            + "goog$debug$Logger$getLogger('b');"
            + "goog$debug$Logger$getLogger('b');",
        new String[] {"a", "goog.net.XhrTransport", "b", "my.app.Application"});
  }

  @Test
  public void testRepeatedStringsWithDifferentMethods() {
    test(
        "throw Error('A');"
            + "goog$debug$Trace.startTracer('B', 'A');"
            + "goog$debug$Logger$getLogger('C');"
            + "goog$debug$Logger$getLogger('B');"
            + "goog$debug$Logger$getLogger('A');"
            + "throw Error('D');"
            + "throw Error('C');"
            + "throw Error('B');"
            + "throw Error('A');",
        "throw Error('a');"
            + "goog$debug$Trace.startTracer('b', 'a');"
            + "goog$debug$Logger$getLogger('c');"
            + "goog$debug$Logger$getLogger('b');"
            + "goog$debug$Logger$getLogger('a');"
            + "throw Error('d');"
            + "throw Error('c');"
            + "throw Error('b');"
            + "throw Error('a');");
  }

  @Test
  public void testLoggerWithNoReplacedParam() {
    testDebugStrings(
        "var x = {};"
            + "x.logger_ = goog.log.getLogger('foo');"
            + "goog.log.info(x.logger_, 'Some message');",
        "var x$logger_ = goog.log.getLogger('a');" + "goog.log.info(x$logger_, 'b');",
        new String[] {
          "a", "foo",
          "b", "Some message"
        });
  }

  @Test
  public void testLoggerWithSomeParametersNotReplaced() {
    testDebugStrings(
        "var x = {};"
            + "x.logger_ = goog.log.getLogger('foo');"
            + "goog.log.multiString(x.logger_, 'Some message', 'Some message2', "
            + "'Do not replace');",
        "var x$logger_ = goog.log.getLogger('a');"
            + "goog.log.multiString(x$logger_, 'b', 'c', 'Do not replace');",
        new String[] {
          "a", "foo",
          "b", "Some message",
          "c", "Some message2"
        });
  }

  @Test
  public void testWarningForTaggedTemplates_unqualifiedName() {
    testWarning(
        "throw Error`Unhandled mail search type ${type}`;",
        ReplaceStrings.STRING_REPLACEMENT_TAGGED_TEMPLATE);
  }

  @Test
  public void testWarningForTaggedTemplates_qualifiedName() {
    testWarning(
        "goog.debug.Logger.getLogger`foo`;", //
        ReplaceStrings.STRING_REPLACEMENT_TAGGED_TEMPLATE);
  }

  @Test
  public void testWarnsIfPassingPrototypeMethod() {
    // ReplaceStrings supported this configuration until November 2020, so make sure users don't
    // pass it thinking it is still supported.
    allowSourcelessWarnings();

    ImmutableList.Builder<String> builder = ImmutableList.builder();
    builder.add("A.prototype.f(?)");
    functionsToInspect = builder.build();
    testError("", ReplaceStrings.BAD_REPLACEMENT_CONFIGURATION);
  }

  @Test
  public void testExcludedFile() {
    testDebugStrings("Excluded('xyz');", "Excluded('xyz');", new String[0]);
    testDebugStrings("NotExcluded('xyz');", "NotExcluded('a');", (new String[] {"a", "xyz"}));
  }

  private void testDebugStrings(String js, String expected, String[] substitutedStrings) {
    // Verify that the strings are substituted correctly in the JS code.
    test(js, expected);

    List<Result> results = pass.getResult();
    assertThat(substitutedStrings.length % 2).isEqualTo(0);
    assertThat(results).hasSize(substitutedStrings.length / 2);

    // Verify that substituted strings are decoded correctly.
    for (int i = 0; i < substitutedStrings.length; i += 2) {
      Result result = results.get(i / 2);
      String original = substitutedStrings[i + 1];
      assertThat(result.original).isEqualTo(original);

      String replacement = substitutedStrings[i];
      assertThat(result.replacement).isEqualTo(replacement);
    }
  }
}
