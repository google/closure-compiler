/*
 * Copyright 2014 The Closure Compiler Authors.
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

package com.google.javascript.refactoring;

import com.google.common.base.Preconditions;

import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.util.ArrayList;
import java.util.List;

/**
 * Main binary that drives a RefasterJS refactoring.
 *
 * @author mknichel@google.com (Mark Knichel)
 */
final class RefasterJs {

  @Option(name = "--inputs", usage = "List of input files for the refactoring.")
  private List<String> inputs = new ArrayList<>();

  @Option(name = "--externs", usage = "List of externs files to use in the compilation.")
  private List<String> externs = new ArrayList<>();

  @Option(
      name = "--refasterjs_template",
      usage = "Location of the JS file to use as the RefasterJS template.")
  private String refasterJsTemplate = null;

  private void doMain(String[] args) throws Exception {
    CmdLineParser parser = new CmdLineParser(this);
    parser.parseArgument(args);
    Preconditions.checkArgument(!inputs.isEmpty(), "At least one input must be provided.");
    RefasterJsScanner scanner = new RefasterJsScanner();
    scanner.loadRefasterJsTemplate(refasterJsTemplate);
    RefactoringDriver driver = new RefactoringDriver(scanner, inputs, externs);
    List<SuggestedFix> fixes = driver.drive();
    System.out.println("SuggestedFixes: " + fixes);
  }

  public static void main(String[] args) throws Exception {
    new RefasterJs().doMain(args);
  }
}
