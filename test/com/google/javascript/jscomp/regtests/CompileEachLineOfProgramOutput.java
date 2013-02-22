/*
 * Copyright 2009 Peter Burns
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

package com.google.javascript.jscomp.regtests;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;

import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.WarningLevel;

public class CompileEachLineOfProgramOutput {
  private static final SourceFile extern =
      SourceFile.fromCode("externs.js", "");
  private static final CompilerOptions options =
      new CompilerOptions();

  static {
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(
        options);
    WarningLevel.QUIET.setOptionsForWarningLevel(options);
    Compiler.setLoggingLevel(Level.OFF);
  }

  public static void main(String[] args) throws IOException {
    if (args.length == 0){
      usage();
    }
    Runtime r = Runtime.getRuntime();
    Process p = null;
    try {
      p = r.exec(args);
    } catch (IOException e) {
      if (args[0].equals("generatejs")) {
        // assuming that the command wasn't found
        System.out.println("generatejs not found, required for generating " +
            "fuzz test cases");
        System.out.println("See: http://github.com/rictic/generatejs");
        System.exit(2);
      } else {
        throw e;
      }
    }

    BufferedReader br = new BufferedReader(
        new InputStreamReader(p.getInputStream()));
    int programsCompiled = 0, compilerErrors = 0;
    for (String program = br.readLine(); program != null; program =
             br.readLine()) {
      try {
        compile(program, programsCompiled);
      } catch(Exception e) {
        System.out.println("Compiler error on program #" +
            programsCompiled + ":");
        System.out.println(program);
        System.out.println("Details:");
        e.printStackTrace(System.out);
        System.out.println("\n\n\n");
        compilerErrors++;
      }

      programsCompiled++;
    }

    if (compilerErrors == 0){
      System.out.println(programsCompiled +
          " programs compiled without error");
      System.exit(0);
    } else {
      System.out.println("==========FAILURE===========");
      System.out.println(compilerErrors +
          " programs caused an error within the compiler out of " +
          programsCompiled + " tested.");
      System.exit(1);
    }
  }

  public static Result compile(String program, int num) {
    SourceFile input = SourceFile.fromCode(""+num, program);
    Compiler compiler = new Compiler();
    Result result = compiler.compile(extern, input, options);
    return result;
  }

  private static void usage() {
    System.out.println(
        "Usage: pass in a program to execute (with arguments)");
    System.out.println(
        "The program is expected to produce JS programs to stdout, " +
        "one per line");
  }
}
