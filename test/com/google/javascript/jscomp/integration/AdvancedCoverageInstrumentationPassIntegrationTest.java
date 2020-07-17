package com.google.javascript.jscomp.integration;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.common.base.Joiner;
import com.google.javascript.jscomp.BlackHoleErrorManager;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.InstrumentOption;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.testing.NoninjectingCompiler;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AdvancedCoverageInstrumentationPassIntegrationTest extends IntegrationTestCase {


  private final String instrumentCodeSource = lines("goog.module('instrument.code'); ",
      "class InstrumentCode {",
      "instrumentCode(a,b) {} ",
      "}",
      "const instrumentCodeInstance = new InstrumentCode();",
      "exports = {instrumentCodeInstance};");

  private final String instrumentCodeExpected = lines(
      "var module$exports$instrument$code = {}",
      "var module$contents$instrument$code_InstrumentCode = function() {};",
      "module$contents$instrument$code_InstrumentCode.prototype.instrumentCode = function(a, b) {};",
      "module$exports$instrument$code.instrumentCodeInstance = ",
      "new module$contents$instrument$code_InstrumentCode;");

  @Test
  public void testFunctionInstrumentation() {
    CompilerOptions options = createCompilerOptions();

    String source =
        lines("function foo() { ",
            "console.log('Hello'); ",
            "}");

    String[] sourceArr = {instrumentCodeSource, source};

    String expected =
        lines(
            "function foo() { ",
            "module$exports$instrument$code.instrumentCodeInstance.instrumentCode(\"C\", 1);",
            "console.log('Hello'); ",
            "}");

    String[] expectedArr = {instrumentCodeExpected, expected};
    test(options, sourceArr, expectedArr);
  }

  @Test
  public void testNoFunctionInstrumentation() {
    CompilerOptions options = createCompilerOptions();

    String source =
        lines("var global = 23; console.log(global);");

    String[] sourceArr = {instrumentCodeSource, source};

    String expected =
        lines("var global = 23; console.log(global);");

    String[] expectedArr = {instrumentCodeExpected, expected};
    test(options, sourceArr, expectedArr);
  }

  protected void test(CompilerOptions options,
      String[] original, String[] compiled) {
    Compiler compiler = compile(options, original);

    Node root = compiler.getRoot().getLastChild();

    // Verify that there are no unexpected errors before checking the compiled output
    assertWithMessage(
        "Expected no warnings or errors\n"
            + "Errors: \n"
            + Joiner.on("\n").join(compiler.getErrors())
            + "\n"
            + "Warnings: \n"
            + Joiner.on("\n").join(compiler.getWarnings()))
        .that(compiler.getErrors().size() + compiler.getWarnings().size())
        .isEqualTo(0);

    if (compiled != null) {
      Node expectedRoot = parseExpectedCode(compiled, options);
      assertNode(root).usingSerializer(compiler::toSource).isEqualTo(expectedRoot);
    }
  }

  protected Compiler compile(CompilerOptions options, String[] original) {
    Compiler compiler =
        useNoninjectingCompiler
            ? new NoninjectingCompiler(new BlackHoleErrorManager())
            : new Compiler(new BlackHoleErrorManager());

    lastCompiler = compiler;

    List<SourceFile> inputs = new ArrayList<>();

    inputs.add(SourceFile.fromCode("InstrumentCode.js", original[0]));

    for (int i = 1; i < original.length; i++) {
      inputs.add(SourceFile.fromCode(inputFileNamePrefix + i + inputFileNameSuffix, original[i]));
    }

    compiler.compile(
        externs,
        inputs,
        options);

    return compiler;
  }

  @Override
  public CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();

    options.setLanguageOut(LanguageMode.ECMASCRIPT5_STRICT);

    options.setClosurePass(true);

    options.setInstrumentForCoverageOption(InstrumentOption.ADVANCED);
    options.setPrettyPrint(true);
    options.preserveTypeAnnotations = true;
    return options;
  }

}