/*
 * Copyright 2008 Google Inc.
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

import com.google.javascript.rhino.Node;

import junit.framework.TestCase;


/**
 * Tests for {@link RecordFunctionInformation}
 *
 *
 */
public class RecordFunctionInformationTest extends TestCase {
  private void test(String js, FunctionInformationMap expected) {
    Compiler compiler = new Compiler();
    compiler.init(new JSSourceFile[] { JSSourceFile.fromCode("externs", "") },
                  new JSSourceFile[] { JSSourceFile.fromCode("testcode", js) },
                  new CompilerOptions());
    test(compiler, expected);
  }

  private void test(JSModule[] modules, FunctionInformationMap expected) {
    Compiler compiler = new Compiler();
    compiler.init(new JSSourceFile[] { JSSourceFile.fromCode("externs", "") },
                  modules,
                  new CompilerOptions());
    test(compiler, expected);
  }

  private void test(Compiler compiler, FunctionInformationMap expected) {
    Node root = compiler.parseInputs();
    Node externsRoot = root.getFirstChild();
    Node mainRoot = externsRoot.getNext();

    FunctionNames functionNames = new FunctionNames(compiler);
    functionNames.process(externsRoot, mainRoot);

    RecordFunctionInformation processor =
        new RecordFunctionInformation(compiler, functionNames);
    processor.process(externsRoot, mainRoot);
    FunctionInformationMap result = processor.getMap();
    assertTrue("\nExpected: " + expected.toString() + "\nResult: " +
               result.toString() + "\n", result.equals(expected));
  }

  public void testFunction() {
    String g = "function g(){}";
    String fAndG = "function f(){" + g + "}";
    String js = "var h=" + fAndG + ";h()";

    FunctionInformationMap.Builder expected =
        FunctionInformationMap.newBuilder();
    expected.addEntry(
        FunctionInformationMap.Entry.newBuilder()
        .setId(0)
        .setSourceName("testcode")
        .setLineNumber(1)
        .setModuleName("")
        .setSize(g.length())
        .setName("f::g")
        .setCompiledSource(g).build());
    expected.addEntry(
        FunctionInformationMap.Entry.newBuilder()
        .setId(1)
        .setSourceName("testcode")
        .setLineNumber(1)
        .setModuleName("")
        .setSize(fAndG.length())
        .setName("f")
        .setCompiledSource(fAndG).build());
    expected.addModule(
        FunctionInformationMap.Module.newBuilder()
        .setName("")
        .setCompiledSource(js + ";").build());

    test(js, expected.build());
  }

  public void testModule() {
    String g = "function g(){}";
    String fAndG = "function f(){" + g + "}";
    String m0_js = "var h=" + fAndG + ";h()";
    String sum = "function(a,b){return a+b}";
    String m1_js = "var x=" + sum + "(1,2)";

    FunctionInformationMap.Builder expected =
        FunctionInformationMap.newBuilder();
    expected.addEntry(
        FunctionInformationMap.Entry.newBuilder()
        .setId(0)
        .setSourceName("i0")
        .setLineNumber(1)
        .setModuleName("m0")
        .setSize(g.length())
        .setName("f::g")
        .setCompiledSource(g).build());
    expected.addEntry(
        FunctionInformationMap.Entry.newBuilder()
        .setId(1)
        .setSourceName("i0")
        .setLineNumber(1)
        .setModuleName("m0")
        .setSize(fAndG.length())
        .setName("f")
        .setCompiledSource(fAndG).build());
    expected.addEntry(
        FunctionInformationMap.Entry.newBuilder()
        .setId(2)
        .setSourceName("i1")
        .setLineNumber(1)
        .setModuleName("m1")
        .setSize(sum.length())
        .setName("<anonymous>")
        .setCompiledSource(sum).build());
    expected.addModule(
        FunctionInformationMap.Module.newBuilder()
        .setName("m0")
        .setCompiledSource(m0_js + ";").build());
    expected.addModule(
        FunctionInformationMap.Module.newBuilder()
        .setName("m1")
        .setCompiledSource(m1_js + ";").build());

    test(CompilerTestCase.createModules(m0_js, m1_js), expected.build());
  }
}
