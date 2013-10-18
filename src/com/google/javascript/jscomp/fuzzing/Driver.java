/*
 * Copyright 2013 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.fuzzing;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerInput;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.JSModule;
import com.google.javascript.jscomp.JsAst;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 * @author zplin@google.com (Zhongpeng Lin)
 */
public class Driver {

  public Compiler compile(String code) {
    Compiler compiler = new Compiler();
    compiler.compile(Arrays.asList(SourceFile.fromCode("[externs]", "")),
        Arrays.asList(SourceFile.fromCode("[fuzzedCode]", code)), getOptions());
    return compiler;
  }

  public Compiler compile(Node root) {
    Node script = new Node(Token.SCRIPT, root);
    script.setSourceFileForTesting("fuzzedFile");
    InputId inputId = new InputId("fuzzedInput");
    script.setInputId(inputId);

    CompilerInput input = new CompilerInput(new JsAst(script));
    JSModule jsModule = new JSModule("fuzzedModule");
    jsModule.add(input);

    Compiler compiler = new Compiler();
    compiler.compileModules(
        new ArrayList<SourceFile>(), Arrays.asList(jsModule), getOptions());
    return compiler;
  }

  private CompilerOptions getOptions() {
    CompilerOptions options = new CompilerOptions();
    return options;
  }

  public static void main(String[] args) {
    int numberOfRuns = Integer.valueOf(args[0]);
    int maxASTSize = Integer.valueOf(args[1]);
    Driver driver = new Driver();
    for (int i = 0; i < numberOfRuns; i++) {
      long seed = System.currentTimeMillis();
      Random random = new Random(seed);
      System.out.println("Seed: " + seed);
      Fuzzer fuzzer = new Fuzzer(random);
      Node root = new Node(Token.EXPR_RESULT,
          fuzzer.generateExpression(maxASTSize));
      String code = Fuzzer.getPrettyCode(root);
      System.out.print(code);

      Compiler compiler = driver.compile(root);
      Result result = compiler.getResult();
      if (result.success) {
        System.out.println("Success!\n");
      }
    }
  }

}
