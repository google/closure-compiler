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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CompilerOptions.PropertyCollapseLevel;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.ReplaceStrings.Result;
import com.google.javascript.rhino.Node;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ReplaceStrings}. */
@RunWith(JUnit4.class)
public final class ReplaceStringsTest extends CompilerTestCase {
  private ReplaceStrings pass;
  private Set<String> reserved;
  private VariableMap previous;
  private boolean runDisambiguateProperties;
  private boolean rename;

  private final ImmutableList<String> defaultFunctionsToInspect = ImmutableList.of(
      "Error(?)",
      "goog.debug.Trace.startTracer(*)",
      "goog.debug.Logger.getLogger(?)",
      "goog.debug.Logger.prototype.info(?)",
      "goog.log.getLogger(?)",
      "goog.log.info(,?)",
      "goog.log.multiString(,?,?,)",
      "Excluded(?):!testcode",
      "NotExcluded(?):!unmatchable"
      );

  private ImmutableList<String> functionsToInspect;

  private static final String EXTERNS = lines(
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
      " * @param {string} name",
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
    options.setWarningLevel(
        DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.OFF);
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
    reserved = ImmutableSet.of();
    previous = null;
    runDisambiguateProperties = false;
    rename = false;
  }

  private static class Renamer extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isName()) {
        String originalName = n.getString();
        n.setOriginalName(originalName);
        n.setString("renamed_" + originalName);
        t.reportCodeChange();
      } else if (n.isGetProp()) {
        String originalName = n.getLastChild().getString();
        n.getLastChild().setOriginalName(originalName);
        n.getLastChild().setString("renamed_" + originalName);
        t.reportCodeChange();
      }
    }
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    pass = new ReplaceStrings(
        compiler, "`", functionsToInspect, reserved, previous);

    return new CompilerPass() {
      @Override
      public void process(Node externs, Node js) {
        Map<String, CheckLevel> propertiesToErrorFor = new HashMap<>();
        propertiesToErrorFor.put("foobar", CheckLevel.ERROR);

        if (rename) {
          NodeTraversal.traverse(compiler, js, new Renamer());
        }
        new CollapseProperties(compiler, PropertyCollapseLevel.ALL).process(externs, js);
        if (runDisambiguateProperties) {
          SourceInformationAnnotator sia =
              new SourceInformationAnnotator("test", false /* checkAnnotated */);
          NodeTraversal.traverse(compiler, js, sia);

          new DisambiguateProperties(compiler, propertiesToErrorFor).process(externs, js);
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
  public void testStable1() {
    previous = VariableMap.fromMap(ImmutableMap.of("previous", "xyz"));
    testDebugStrings(
        "Error('xyz');",
        "Error('previous');",
        (new String[] { "previous", "xyz" }));
    reserved = ImmutableSet.of("a", "b", "previous");
    testDebugStrings(
        "Error('xyz');",
        "Error('c');",
        (new String[] { "c", "xyz" }));
  }

  @Test
  public void testStable2() {
    // Two things happen here:
    // 1) a previously used name "a" is not used for another string, "b" is
    // chosen instead.
    // 2) a previously used name "a" is dropped from the output map if
    // it isn't used.
    previous = VariableMap.fromMap(ImmutableMap.of("a", "unused"));
    testDebugStrings(
        "Error('xyz');",
        "Error('b');",
        (new String[] { "b", "xyz" }));
  }

  @Test
  public void testRenameName() {
    rename = true;
    testDebugStrings(
        "Error('xyz');",
        "renamed_Error('a');",
        (new String[] { "a", "xyz" }));
  }

  @Test
  public void testRenameStaticProp() {
    rename = true;
    testDebugStrings(
        "goog.debug.Trace.startTracer('HistoryManager.updateHistory');",
        "renamed_goog.renamed_debug.renamed_Trace.renamed_startTracer('a');",
        (new String[] { "a", "HistoryManager.updateHistory" }));
  }

  @Test
  public void testThrowError1() {
    testDebugStrings(
        "throw Error('xyz');",
        "throw Error('a');",
        (new String[] { "a", "xyz" }));
    previous = VariableMap.fromMap(ImmutableMap.of("previous", "xyz"));
    testDebugStrings(
        "throw Error('xyz');",
        "throw Error('previous');",
        (new String[] { "previous", "xyz" }));
  }

  @Test
  public void testThrowError2() {
    testDebugStrings(
        "throw Error('x' +\n    'yz');",
        "throw Error('a');",
        (new String[] { "a", "xyz" }));
  }

  @Test
  public void testThrowError3() {
    testDebugStrings(
        "throw Error('Unhandled mail' + ' search type ' + type);",
        "throw Error('a' + '`' + type);",
        (new String[] { "a", "Unhandled mail search type `" }));
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
  public void testThrowNonStringError() {
    // No replacement is done when an error is neither a string literal nor
    // a string concatenation expression.
    testDebugStrings(
        "throw Error(x('abc'));",
        "throw Error(x('abc'));",
        (new String[] { }));
  }

  @Test
  public void testThrowConstStringError() {
    testDebugStrings(
        "var AA = 'uvw', AB = 'xyz'; throw Error(AB);",
        "var AA = 'uvw', AB = 'xyz'; throw Error('a');",
        (new String [] { "a", "xyz" }));
  }

  @Test
  public void testThrowNewError1() {
    testDebugStrings(
        "throw new Error('abc');",
        "throw new Error('a');",
        (new String[] { "a", "abc" }));
  }

  @Test
  public void testThrowNewError2() {
    testDebugStrings(
        "throw new Error();",
        "throw new Error();",
        new String[] {});
  }

  @Test
  public void testStartTracer1() {
    testDebugStrings(
        "goog.debug.Trace.startTracer('HistoryManager.updateHistory');",
        "goog.debug.Trace.startTracer('a');",
        (new String[] { "a", "HistoryManager.updateHistory" }));
  }

  @Test
  public void testStartTracer2() {
    testDebugStrings(
        "goog$debug$Trace.startTracer('HistoryManager', 'updateHistory');",
        "goog$debug$Trace.startTracer('a', 'b');",
        (new String[] {
            "a", "HistoryManager",
            "b", "updateHistory" }));
  }

  @Test
  public void testStartTracer3() {
    testDebugStrings(
        "goog$debug$Trace.startTracer('ThreadlistView',\n" +
        "                             'Updating ' + array.length + ' rows');",
        "goog$debug$Trace.startTracer('a', 'b' + '`' + array.length);",
        new String[] { "a", "ThreadlistView", "b", "Updating ` rows" });
  }

  @Test
  public void testStartTracer4() {
    testDebugStrings(
        "goog.debug.Trace.startTracer(s, 'HistoryManager.updateHistory');",
        "goog.debug.Trace.startTracer(s, 'a');",
        (new String[] { "a", "HistoryManager.updateHistory" }));
  }

  @Test
  public void testLoggerInitialization() {
    testDebugStrings(
        "goog$debug$Logger$getLogger('my.app.Application');",
        "goog$debug$Logger$getLogger('a');",
        (new String[] { "a", "my.app.Application" }));
  }

  @Test
  public void testLoggerOnObject1() {
    testDebugStrings(
        "var x = {};" +
        "x.logger_ = goog.debug.Logger.getLogger('foo');" +
        "x.logger_.info('Some message');",
        "var x$logger_ = goog.debug.Logger.getLogger('a');" +
        "x$logger_.info('b');",
        new String[] {
            "a", "foo",
            "b", "Some message"});
  }

  // Non-matching "info" property.
  @Test
  public void testLoggerOnObject2() {
    test(
        "var x = {};" +
        "x.info = function(a) {};" +
        "x.info('Some message');",
        "var x$info = function(a) {};" +
        "x$info('Some message');");
  }

  // Non-matching "info" prototype property.
  @Test
  public void testLoggerOnObject3a() {
    testSame(
        "/** @constructor */\n" +
        "var x = function() {};\n" +
        "x.prototype.info = function(a) {};" +
        "(new x).info('Some message');");
  }

  // Non-matching "info" prototype property.
  @Test
  public void testLoggerOnObject3b() {
    testSame(
      "/** @constructor */\n" +
      "var x = function() {};\n" +
      "x.prototype.info = function(a) {};" +
      "var y = (new x); this.info('Some message');");
  }

  // Non-matching "info" property on "NoObject" type.
  @Test
  public void testLoggerOnObject4() {
    testSame("(new x).info('Some message');");
  }

  // Non-matching "info" property on "UnknownObject" type.
  @Test
  public void testLoggerOnObject5() {
    testSame("my$Thing.logger_.info('Some message');");
  }

  @Test
  public void testLoggerOnVar() {
    testDebugStrings(
        "var logger = goog.debug.Logger.getLogger('foo');" +
        "logger.info('Some message');",
        "var logger = goog.debug.Logger.getLogger('a');" +
        "logger.info('b');",
        new String[] {
            "a", "foo",
            "b", "Some message"});
  }

  @Test
  public void testLoggerOnThis() {
    testDebugStrings(
        "function f() {" +
        "  this.logger_ = goog.debug.Logger.getLogger('foo');" +
        "  this.logger_.info('Some message');" +
        "}",
        "function f() {" +
        "  this.logger_ = goog.debug.Logger.getLogger('a');" +
        "  this.logger_.info('b');" +
        "}",
        new String[] {
            "a", "foo",
            "b", "Some message"});
  }

  @Test
  public void testLoggerOnThis2() {
    testDebugStrings(
        lines(
            "/** @constructor */",
            "function Foo() {",
            "  /** @type {!goog.debug.Logger} */",
            "  this.logger_;",
            "}",
            "Foo.prototype.f = function() {",
            "  this.logger_.info('Some message');",
            "};"),
        lines(
            "/** @constructor */",
            "function Foo() {",
            "  /** @type {!goog.debug.Logger} */",
            "  this.logger_;",
            "}",
            "Foo.prototype.f = function() {",
            "  this.logger_.info('a');",
            "};"),
        new String[] { "a", "Some message" });
  }

  @Test
  public void testRepeatedErrorString1() {
    testDebugStrings(
        "Error('abc');Error('def');Error('abc');",
        "Error('a');Error('b');Error('a');",
        (new String[] { "a", "abc", "b", "def" }));
  }

  @Test
  public void testRepeatedErrorString2() {
    testDebugStrings(
        "Error('a:' + u + ', b:' + v); Error('a:' + x + ', b:' + y);",
        "Error('a' + '`' + u + '`' + v); Error('a' + '`' + x + '`' + y);",
        (new String[] { "a", "a:`, b:`" }));
  }

  @Test
  public void testRepeatedErrorString3() {
    testDebugStrings(
        "var AB = 'b'; throw Error(AB); throw Error(AB);",
        "var AB = 'b'; throw Error('a'); throw Error('a');",
        (new String[] { "a", "b" }));
  }

  @Test
  public void testRepeatedTracerString() {
    testDebugStrings(
        "goog$debug$Trace.startTracer('A', 'B', 'A');",
        "goog$debug$Trace.startTracer('a', 'b', 'a');",
        (new String[] { "a", "A", "b", "B" }));
  }

  @Test
  public void testRepeatedLoggerString() {
    testDebugStrings(
        "goog$debug$Logger$getLogger('goog.net.XhrTransport');" +
        "goog$debug$Logger$getLogger('my.app.Application');" +
        "goog$debug$Logger$getLogger('my.app.Application');",
        "goog$debug$Logger$getLogger('a');" +
        "goog$debug$Logger$getLogger('b');" +
        "goog$debug$Logger$getLogger('b');",
        new String[] {
            "a", "goog.net.XhrTransport", "b", "my.app.Application" });
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
  public void testReserved() {
    testDebugStrings(
        "throw Error('xyz');",
        "throw Error('a');",
        (new String[] { "a", "xyz" }));
    reserved = ImmutableSet.of("a", "b", "c");
    testDebugStrings(
        "throw Error('xyz');",
        "throw Error('d');",
        (new String[] { "d", "xyz" }));
  }

  @Test
  public void testLoggerWithNoReplacedParam() {
    testDebugStrings(
        "var x = {};" +
        "x.logger_ = goog.log.getLogger('foo');" +
        "goog.log.info(x.logger_, 'Some message');",
        "var x$logger_ = goog.log.getLogger('a');" +
        "goog.log.info(x$logger_, 'b');",
        new String[] {
            "a", "foo",
            "b", "Some message"});
  }

  @Test
  public void testLoggerWithSomeParametersNotReplaced() {
    testDebugStrings(
        "var x = {};" +
        "x.logger_ = goog.log.getLogger('foo');" +
        "goog.log.multiString(x.logger_, 'Some message', 'Some message2', " +
            "'Do not replace');",
        "var x$logger_ = goog.log.getLogger('a');" +
        "goog.log.multiString(x$logger_, 'b', 'c', 'Do not replace');",
        new String[] {
            "a", "foo",
            "b", "Some message",
            "c", "Some message2"});
  }

  @Test
  public void testWithDisambiguateProperties() {
    runDisambiguateProperties = true;

    ImmutableList.Builder<String> builder = ImmutableList.builder();
    builder.addAll(defaultFunctionsToInspect);
    builder.add("A.prototype.f(?)");
    builder.add("C.prototype.f(?)");
    functionsToInspect = builder.build();

    testDebugStrings(
        lines(
            "/** @constructor */",
            "function A() {}",
            "/** @param {string} p",
            "  * @return {string} */",
            "A.prototype.f = function(p) {return 'a' + p;};",
            "/** @constructor */",
            "function B() {}",
            "/** @param {string} p",
            "  * @return {string} */",
            "B.prototype.f = function(p) {return p + 'b';};",
            "/** @constructor */",
            "function C() {}",
            "/** @param {string} p",
            "  * @return {string} */",
            "C.prototype.f = function(p) {return 'c' + p + 'c';};",
            "/** @type {A|B} */",
            "var ab = 1 ? new B : new A;",
            "/** @type {string} */",
            "var n = ab.f('not replaced');",
            "(new A).f('replaced with a');",
            "(new C).f('replaced with b');"),
        lines(
            "/** @constructor */",
            "function A() {}",
            "/** @param {string} p",
            "  * @return {string} */",
            "A.prototype.A_prototype$f = function(p) { return'a'+p; };",
            "/** @constructor */",
            "function B() {}",
            "/** @param {string} p",
            "  * @return {string} */",
            "B.prototype.A_prototype$f = function(p) { return p+'b'; };",
            "/** @constructor */",
            "function C() {}",
            "/** @param {string} p",
            "  * @return {string} */",
            "C.prototype.C_prototype$f = function(p) { return'c'+p+'c'; };",
            "/** @type {A|B} */",
            "var ab = 1 ? new B : new A;",
            "/** @type {string} */",
            "var n = ab.A_prototype$f('not replaced');",
            "(new A).A_prototype$f('a');",
            "(new C).C_prototype$f('b');"),
        new String[] {
          "a", "replaced with a",
          "b", "replaced with b"
        });
  }

  @Test
  public void testExcludedFile() {
    testDebugStrings("Excluded('xyz');", "Excluded('xyz');", new String[0]);
    testDebugStrings("NotExcluded('xyz');", "NotExcluded('a');", (new String[] { "a", "xyz" }));
  }

  private void testDebugStrings(String js, String expected,
                                String[] substitutedStrings) {
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
