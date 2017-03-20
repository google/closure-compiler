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
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.ReplaceStrings.Result;
import com.google.javascript.rhino.Node;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests for {@link ReplaceStrings}.
 *
 */
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

  private static final String EXTERNS =
    "var goog = {};\n" +
    "goog.debug = {};\n" +
    "/** @constructor */\n" +
    "goog.debug.Trace = function() {};\n" +
    "goog.debug.Trace.startTracer = function (var_args) {};\n" +
    "/** @constructor */\n" +
    "goog.debug.Logger = function() {};\n" +
    "goog.debug.Logger.prototype.info = function(msg, opt_ex) {};\n" +
    "/**\n" +
    " * @param {string} name\n" +
    " * @return {!goog.debug.Logger}\n" +
    " */\n" +
    "goog.debug.Logger.getLogger = function(name){};\n" +
    "goog.log = {}\n" +
    "goog.log.getLogger = function(name){};\n" +
    "goog.log.info = function(logger, msg, opt_ex) {};\n" +
    "goog.log.multiString = function(logger, replace1, replace2, keep) {};\n"
    ;

  public ReplaceStringsTest() {
    super(EXTERNS, true);
    enableNormalize();
    parseTypeInfo = true;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(
        DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.OFF);
    return options;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    super.enableTypeCheck();
    functionsToInspect = defaultFunctionsToInspect;
    reserved = Collections.emptySet();
    previous = null;
    runDisambiguateProperties = false;
    rename = false;
  }

  private static class Renamer extends AbstractPostOrderCallback {
    final AbstractCompiler compiler;

    Renamer(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isName()) {
        String originalName = n.getString();
        n.setOriginalName(originalName);
        n.setString("renamed_" + originalName);
        compiler.reportCodeChange();
      } else if (n.isGetProp()) {
        String originalName = n.getLastChild().getString();
        n.getLastChild().setOriginalName(originalName);
        n.getLastChild().setString("renamed_" + originalName);
        compiler.reportCodeChange();
      }
    }
  }

  @Override
  public CompilerPass getProcessor(final Compiler compiler) {
    pass = new ReplaceStrings(
        compiler, "`", functionsToInspect, reserved, previous);

    return new CompilerPass() {
      @Override
      public void process(Node externs, Node js) {
        Map<String, CheckLevel> propertiesToErrorFor = new HashMap<>();
        propertiesToErrorFor.put("foobar", CheckLevel.ERROR);

        if (rename) {
          NodeTraversal.traverseEs6(compiler, js, new Renamer(compiler));
        }
        new CollapseProperties(compiler).process(externs, js);
        if (runDisambiguateProperties) {
          SourceInformationAnnotator sia =
              new SourceInformationAnnotator("test", false /* doSanityChecks */);
          NodeTraversal.traverseEs6(compiler, js, sia);

          new DisambiguateProperties(compiler, propertiesToErrorFor).process(externs, js);
        }
        pass.process(externs, js);
      }
    };
  }

  @Override
  public int getNumRepetitions() {
    // This compiler pass is not idempotent and should only be run over a
    // parse tree once.
    return 1;
  }

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

  public void testRenameName() {
    rename = true;
    testDebugStrings(
        "Error('xyz');",
        "renamed_Error('a');",
        (new String[] { "a", "xyz" }));
  }

  public void testRenameStaticProp() {
    rename = true;
    testDebugStrings(
        "goog.debug.Trace.startTracer('HistoryManager.updateHistory');",
        "renamed_goog.renamed_debug.renamed_Trace.renamed_startTracer('a');",
        (new String[] { "a", "HistoryManager.updateHistory" }));
  }

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

  public void testThrowError2() {
    testDebugStrings(
        "throw Error('x' +\n    'yz');",
        "throw Error('a');",
        (new String[] { "a", "xyz" }));
  }

  public void testThrowError3() {
    testDebugStrings(
        "throw Error('Unhandled mail' + ' search type ' + type);",
        "throw Error('a' + '`' + type);",
        (new String[] { "a", "Unhandled mail search type `" }));
  }

  public void testThrowError4() {
    testDebugStrings(
    	LINE_JOINER.join(
        "/** @constructor */\n",
        "var A = function() {};\n",
        "A.prototype.m = function(child) {\n",
        "  if (this.haveChild(child)) {\n",
        "    throw Error('Node: ' + this.getDataPath() +\n",
        "                ' already has a child named ' + child);\n",
        "  } else if (child.parentNode) {\n",
        "    throw Error('Node: ' + child.getDataPath() +\n",
        "                ' already has a parent');\n",
        "  }\n",
        "  child.parentNode = this;\n", 
        "};"),
    	LINE_JOINER.join(
    	"/** @constructor */\n",
        "var A = function(){};\n",
        "A.prototype.m = function(child) {\n",
        "  if (this.haveChild(child)) {\n",
        "    throw Error('a' + '`' + this.getDataPath() + '`' + child);\n",
        "  } else if (child.parentNode) {\n",
        "    throw Error('b' + '`' + child.getDataPath());\n",
        "  }\n",
        "  child.parentNode = this;\n",
        "};"),
        (new String[] {
            "a",
            "Node: ` already has a child named `",
            "b",
            "Node: ` already has a parent",
            }));
  }

  public void testThrowNonStringError() {
    // No replacement is done when an error is neither a string literal nor
    // a string concatenation expression.
    testDebugStrings(
        "throw Error(x('abc'));",
        "throw Error(x('abc'));",
        (new String[] { }));
  }

  public void testThrowConstStringError() {
    testDebugStrings(
        "var AA = 'uvw', AB = 'xyz'; throw Error(AB);",
        "var AA = 'uvw', AB = 'xyz'; throw Error('a');",
        (new String [] { "a", "xyz" }));
  }

  public void testThrowNewError1() {
    testDebugStrings(
        "throw new Error('abc');",
        "throw new Error('a');",
        (new String[] { "a", "abc" }));
  }

  public void testThrowNewError2() {
    testDebugStrings(
        "throw new Error();",
        "throw new Error();",
        new String[] {});
  }

  public void testStartTracer1() {
    testDebugStrings(
        "goog.debug.Trace.startTracer('HistoryManager.updateHistory');",
        "goog.debug.Trace.startTracer('a');",
        (new String[] { "a", "HistoryManager.updateHistory" }));
  }

  public void testStartTracer2() {
    testDebugStrings(
        "goog$debug$Trace.startTracer('HistoryManager', 'updateHistory');",
        "goog$debug$Trace.startTracer('a', 'b');",
        (new String[] {
            "a", "HistoryManager",
            "b", "updateHistory" }));
  }

  public void testStartTracer3() {
    testDebugStrings(
        "goog$debug$Trace.startTracer('ThreadlistView',\n" +
        "                             'Updating ' + array.length + ' rows');",
        "goog$debug$Trace.startTracer('a', 'b' + '`' + array.length);",
        new String[] { "a", "ThreadlistView", "b", "Updating ` rows" });
  }

  public void testStartTracer4() {
    testDebugStrings(
        "goog.debug.Trace.startTracer(s, 'HistoryManager.updateHistory');",
        "goog.debug.Trace.startTracer(s, 'a');",
        (new String[] { "a", "HistoryManager.updateHistory" }));
  }

  public void testLoggerInitialization() {
    testDebugStrings(
        "goog$debug$Logger$getLogger('my.app.Application');",
        "goog$debug$Logger$getLogger('a');",
        (new String[] { "a", "my.app.Application" }));
  }

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
  public void testLoggerOnObject2() {
    test(
        "var x = {};" +
        "x.info = function(a) {};" +
        "x.info('Some message');",
        "var x$info = function(a) {};" +
        "x$info('Some message');");
  }

  // Non-matching "info" prototype property.
  public void testLoggerOnObject3a() {
    testSame(
        "/** @constructor */\n" +
        "var x = function() {};\n" +
        "x.prototype.info = function(a) {};" +
        "(new x).info('Some message');");
  }

  // Non-matching "info" prototype property.
  public void testLoggerOnObject3b() {
    testSame(
      "/** @constructor */\n" +
      "var x = function() {};\n" +
      "x.prototype.info = function(a) {};" +
      "var y = (new x); this.info('Some message');");
  }

  // Non-matching "info" property on "NoObject" type.
  public void testLoggerOnObject4() {
    testSame("(new x).info('Some message');");
  }

  // Non-matching "info" property on "UnknownObject" type.
  public void testLoggerOnObject5() {
    testSame("my$Thing.logger_.info('Some message');");
  }

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

  public void testRepeatedErrorString1() {
    testDebugStrings(
        "Error('abc');Error('def');Error('abc');",
        "Error('a');Error('b');Error('a');",
        (new String[] { "a", "abc", "b", "def" }));
  }

  public void testRepeatedErrorString2() {
    testDebugStrings(
        "Error('a:' + u + ', b:' + v); Error('a:' + x + ', b:' + y);",
        "Error('a' + '`' + u + '`' + v); Error('a' + '`' + x + '`' + y);",
        (new String[] { "a", "a:`, b:`" }));
  }

  public void testRepeatedErrorString3() {
    testDebugStrings(
        "var AB = 'b'; throw Error(AB); throw Error(AB);",
        "var AB = 'b'; throw Error('a'); throw Error('a');",
        (new String[] { "a", "b" }));
  }

  public void testRepeatedTracerString() {
    testDebugStrings(
        "goog$debug$Trace.startTracer('A', 'B', 'A');",
        "goog$debug$Trace.startTracer('a', 'b', 'a');",
        (new String[] { "a", "A", "b", "B" }));
  }

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

  public void testWithDisambiguateProperties() throws Exception {
    runDisambiguateProperties = true;

    ImmutableList.Builder<String> builder = ImmutableList.builder();
    builder.addAll(defaultFunctionsToInspect);
    builder.add("A.prototype.f(?)");
    builder.add("C.prototype.f(?)");
    functionsToInspect = builder.build();

    testDebugStrings(
    	LINE_JOINER.join(
    	"/** @constructor */",
        "function A() {}\n",
        "/** @param {string} p\n",
        "  * @return {string} */\n",
        "A.prototype.f = function(p) {return 'a' + p;};\n",
        "/** @constructor */",
        "function B() {}\n",
        "/** @param {string} p\n",
        "  * @return {string} */\n",
        "B.prototype.f = function(p) {return p + 'b';};\n",
        "/** @constructor */",
        "function C() {}\n",
        "/** @param {string} p\n",
        "  * @return {string} */\n",
        "C.prototype.f = function(p) {return 'c' + p + 'c';};\n",
        "/** @type {A|B} */",
        "var ab = 1 ? new B : new A;\n",
        "/** @type {string} */",
        "var n = ab.f('not replaced');\n",
        "(new A).f('replaced with a');",
        "(new C).f('replaced with b');"),
    	
    	LINE_JOINER.join(
    	"/** @constructor */",
    	"function A() {}\n",
    	"/** @param {string} p\n",
    	"  * @return {string} */\n",
    	"A.prototype.A_prototype$f = function(p) { return'a'+p; };\n",
    	"/** @constructor */",
    	"function B() {}\n",
    	"/** @param {string} p\n",
    	"  * @return {string} */\n",
    	"B.prototype.A_prototype$f = function(p) { return p+'b'; };\n",
    	"/** @constructor */",
    	"function C() {}\n",
    	"/** @param {string} p\n",
    	"  * @return {string} */\n",
    	"C.prototype.C_prototype$f = function(p) { return'c'+p+'c'; };\n",
    	"/** @type {A|B} */",
    	"var ab = 1 ? new B : new A;\n",
    	"/** @type {string} */",
    	"var n = ab.A_prototype$f('not replaced');\n",
    	"(new A).A_prototype$f('a');",
    	"(new C).C_prototype$f('b');"),
        
        new String[] {
            "a", "replaced with a",
            "b", "replaced with b"});
  }

  public void testExcludedFile() {
    testDebugStrings("Excluded('xyz');", "Excluded('xyz');", new String[0]);
    testDebugStrings("NotExcluded('xyz');", "NotExcluded('a');", (new String[] { "a", "xyz" }));
  }

  private void testDebugStrings(String js, String expected,
                                String[] substitutedStrings) {
    // Verify that the strings are substituted correctly in the JS code.
    test(js, expected);

    List<Result> results = pass.getResult();
    assertEquals(0, substitutedStrings.length % 2);
    assertThat(results).hasSize(substitutedStrings.length / 2);

    // Verify that substituted strings are decoded correctly.
    for (int i = 0; i < substitutedStrings.length; i += 2) {
      Result result = results.get(i / 2);
      String original = substitutedStrings[i + 1];
      assertEquals(original, result.original);

      String replacement = substitutedStrings[i];
      assertEquals(replacement, result.replacement);
    }
  }
}
