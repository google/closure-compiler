/*
 * Copyright 2008 The Closure Compiler Authors.
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
import com.google.javascript.rhino.Node;
import com.google.protobuf.TextFormat;

import java.io.IOException;
import java.util.List;

/**
 * Tests for {@link InstrumentFunctions}
 *
 */
public final class InstrumentFunctionsTest extends CompilerTestCase {
  private String instrumentationPb;

  public InstrumentFunctionsTest() {
    this.instrumentationPb = null;
  }

  @Override
  protected void setUp() {
    this.instrumentationPb = null;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new NameAndInstrumentFunctions(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    // This pass is not idempotent.
    return 1;
  }

  public void testInstrument() {
    final String kPreamble =
        "var $$toRemoveDefinition1, $$notToRemove;\n"
        + "var $$toRemoveDefinition2, $$toRemoveDefinition3;\n";

    // build instrumentation template and init code strings for use in
    // tests below.
    List<String> initCodeList = ImmutableList.of(
        "var $$Table = [];",
        "function $$TestDefine(id) {",
        "  $$Table[id] = 0;",
        "};",
        "function $$TestInstrument(id) {",
        "  $$Table[id]++;",
        "};");
    StringBuilder initCodeBuilder = new StringBuilder();
    StringBuilder pbBuilder = new StringBuilder();
    for (String line : initCodeList) {
      initCodeBuilder.append(line).append("\n");
      pbBuilder.append("init: \"").append(line).append("\"\n");
    }

    pbBuilder.append("report_call: \"$$testInstrument\"")
        .append("report_defined: \"$$testDefine\"")
        .append("declaration_to_remove: \"$$toRemoveDefinition1\"")
        .append("declaration_to_remove: \"$$toRemoveDefinition2\"")
        .append("declaration_to_remove: \"$$toRemoveDefinition3\"");

    final String initCode = initCodeBuilder.toString();
    this.instrumentationPb = pbBuilder.toString();

    // Test basic instrumentation
    test("function a(){b}",
         initCode + "$$testDefine(0,\"a\");"
         + "function a(){$$testInstrument(0,\"a\",arguments);b}");

    // Test declaration_to_remove
    test(kPreamble + "function a(){b}",
         initCode
         + "$$testDefine(0,\"a\");"
         + "var $$notToRemove;"
         + "function a(){$$testInstrument(0,\"a\",arguments);b}");

    // Test object literal declarations
    test(kPreamble + "var a = { b: function(){c} }",
         initCode
         + "var $$notToRemove;"
         + "$$testDefine(0,\"<anonymous>\");"
         + "var a = { b: function(){"
         + "$$testInstrument(0,\"<anonymous>\",arguments);c} }");

    // Test multiple object literal declarations
    test(kPreamble
         + "var a = { b: function(){c}, d: function(){e} }",
         initCode
         + "var $$notToRemove;"
         + "$$testDefine(0,\"<anonymous>\");"
         + "$$testDefine(1,\"<anonymous>\");"
         + "var a={b:function(){"
         + "$$testInstrument(0,\"<anonymous>\",arguments);c},"
         + "d:function(){$$testInstrument(1,\"<anonymous>\",arguments);e}}");

    // Test recursive object literal declarations
    test(kPreamble
         + "var a = { b: { f: function(){c} }, d: function(){e} }",
         initCode
         + "var $$notToRemove;"
         + "$$testDefine(0,\"<anonymous>\");"
         + "$$testDefine(1,\"<anonymous>\");"
         + "var a={b:{f:function(){"
         + "$$testInstrument(0,\"<anonymous>\",arguments);c}},"
         + "d:function(){$$testInstrument(1,\"<anonymous>\",arguments);e}}");
  }

  public void testEmpty() {
    this.instrumentationPb = "";
    test("function a(){b}", "function a(){b}");
  }

  public void testAppNameSetter() {
    this.instrumentationPb = "app_name_setter: \"setAppName\"";
    test("function a(){b}", "setAppName(\"testfile.js\");function a(){b}");
  }

  public void testInit() {
    this.instrumentationPb = "init: \"var foo = 0;\"\n"
        + "init: \"function f(){g();}\"\n";
    test("function a(){b}",
         "var foo = 0;function f(){g()}function a(){b}");
  }

  public void testDeclare() {
    this.instrumentationPb = "report_defined: \"$$testDefine\"";
    test("function a(){b}", "$$testDefine(0,\"a\");function a(){b}");
  }

  public void testCall() {
    this.instrumentationPb = "report_call: \"$$testCall\"";
    test("function a(){b}", "function a(){$$testCall(0,\"a\",arguments);b}");
  }

  public void testNested() {
    this.instrumentationPb = "report_call: \"$$testCall\"\n"
        + "report_defined: \"$$testDefine\"";
    test("function a(){ function b(){}}",
         "$$testDefine(1,\"a\");$$testDefine(0,\"a::b\");"
         + "function a(){$$testCall(1,\"a\",arguments);"
         + "function b(){$$testCall(0,\"a::b\",arguments)}}");
  }

  public void testExitPaths() {
    this.instrumentationPb = "report_exit: \"$$testExit\"";
    test("function a(){return}",
         "function a(){return $$testExit(0,undefined,\"a\")}");

    test("function b(){return 5}",
         "function b(){return $$testExit(0,5,\"b\")}");

    test("function a(){if(2 != 3){return}else{return 5}}",
         "function a(){if(2!=3){return $$testExit(0,undefined,\"a\")}"
         + "else{return $$testExit(0,5,\"a\")}}");

    test("function a(){if(2 != 3){return}else{return 5}}b()",
         "function a(){if(2!=3){return $$testExit(0,undefined,\"a\")}"
         + "else{return $$testExit(0,5,\"a\")}}b()");

    test("function a(){if(2 != 3){return}else{return 5}}",
         "function a(){if(2!=3){return $$testExit(0,undefined,\"a\")}"
         + "else{return $$testExit(0,5,\"a\")}}");
  }

  public void testExitNoReturn() {
    this.instrumentationPb = "report_exit: \"$$testExit\"";
    test("function a(){}",
         "function a(){$$testExit(0,undefined,\"a\");}");

    test("function a(){b()}",
         "function a(){b();$$testExit(0,undefined,\"a\");}");
  }

  public void testPartialExitPaths() {
    this.instrumentationPb = "report_exit: \"$$testExit\"";
    test("function a(){if (2 != 3) {return}}",
         "function a(){if (2 != 3){return $$testExit(0,undefined,\"a\")}"
         + "$$testExit(0,undefined,\"a\")}");
  }

  public void testExitTry() {
    this.instrumentationPb = "report_exit: \"$$testExit\"";
    test("function a(){try{return}catch(err){}}",
         "function a(){try{return $$testExit(0,undefined,\"a\")}catch(err){}"
         + "$$testExit(0,undefined,\"a\")}");

    test("function a(){try{}catch(err){return}}",
         "function a(){try{}catch(err){return $$testExit(0,undefined,\"a\")}"
         + "$$testExit(0,undefined,\"a\")}");

    test("function a(){try{return}finally{}}",
         "function a(){try{return $$testExit(0,undefined,\"a\")}finally{}"
         + "$$testExit(0,undefined,\"a\")}");

    test("function a(){try{return}catch(err){}finally{}}",
         "function a(){try{return $$testExit(0,undefined,\"a\")}catch(err){}"
         + "finally{}$$testExit(0,undefined,\"a\")}");

    test("function a(){try{return 1}catch(err){return 2}}",
         "function a(){try{return $$testExit(0,1,\"a\")}"
         + "catch(err){return $$testExit(0,2,\"a\")}}");

    test("function a(){try{return 1}catch(err){return 2}finally{}}",
         "function a(){try{return $$testExit(0,1,\"a\")}"
         + "catch(err){return $$testExit(0,2,\"a\")}"
         + "finally{}$$testExit(0,undefined,\"a\")}");

    test("function a(){try{return 1}catch(err){return 2}finally{return}}",
         "function a(){try{return $$testExit(0,1,\"a\")}"
         + "catch(err){return $$testExit(0,2,\"a\")}finally{"
         + "return $$testExit(0,undefined,\"a\")}}");

    test("function a(){try{}catch(err){}finally{return}}",
         "function a(){try{}catch(err){}finally{"
         + "return $$testExit(0,undefined,\"a\")}}");
  }

  public void testNestedExit() {
    this.instrumentationPb = "report_exit: \"$$testExit\"\n"
        + "report_defined: \"$$testDefine\"";
    test("function a(){ return function(){ return c;}}",
         "$$testDefine(1,\"a\");"
         + "function a(){$$testDefine(0,\"a::<anonymous>\");"
         + "return $$testExit(1,function(){"
         + "return $$testExit(0,c,\"a::<anonymous>\");},\"a\");}");
  }

  private class NameAndInstrumentFunctions implements CompilerPass {
    private final Compiler compiler;
    NameAndInstrumentFunctions(Compiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node root) {
      FunctionNames functionNames = new FunctionNames(compiler);
      functionNames.process(externs, root);

      Instrumentation.Builder builder = Instrumentation.newBuilder();
      try {
        TextFormat.merge(instrumentationPb, builder);
      } catch (IOException e) {
        e.printStackTrace();
      }

      InstrumentFunctions instrumentation = new InstrumentFunctions(
          compiler, functionNames, builder.build(), "testfile.js");
      instrumentation.process(externs, root);
    }
  }
}
