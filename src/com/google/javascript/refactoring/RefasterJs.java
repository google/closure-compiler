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
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CommandLineRunner;
import com.google.javascript.jscomp.SourceFile;

import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.BooleanOptionHandler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Main binary that drives a RefasterJS refactoring.
 *
 * @author mknichel@google.com (Mark Knichel)
 */
final class RefasterJs {

  @Option(name = "--help",
        hidden = true,
        handler = BooleanOptionHandler.class,
        usage = "Show instructions for how to use RefasterJS")
  private boolean displayHelp = false;

  @Option(name = "--inputs", usage = "List of input files for the refactoring.")
  private List<String> inputs = new ArrayList<>();

  @Option(name = "--externs", usage = "List of externs files to use in the compilation.")
  private List<String> externs = new ArrayList<>();

  @Option(
      name = "--refasterjs_template",
      usage = "Location of the JS file to use as the RefasterJS template.")
  private String refasterJsTemplate = null;

  @Option(name = "--include_default_externs",
      usage = "Whether to include the standard JavaScript externs. Defaults to true.")
  private boolean includeDefaultExterns = true;

  @Option(name = "--dry_run",
      usage = "Use this to display what changes would be made without applying the changes.")
  private boolean dryRun = false;

  private void doMain(String[] args) throws Exception {
    CmdLineParser parser = new CmdLineParser(this);
    parser.parseArgument(args);
    if (displayHelp) {
      CmdLineParser p = new CmdLineParser(this);
      p.printUsage(System.out);
      return;
    }
    Preconditions.checkArgument(
        !inputs.isEmpty(), "At least one input must be provided in the --inputs flag.");
    Preconditions.checkArgument(
        !Strings.isNullOrEmpty(refasterJsTemplate), "--refasterjs_template must be provided");
    for (String input : inputs) {
      Preconditions.checkArgument(
          new File(input).exists(), "Input file %s does not exist.", input);
    }

    RefasterJsScanner scanner = new RefasterJsScanner();
    scanner.loadRefasterJsTemplate(refasterJsTemplate);
    RefactoringDriver driver = new RefactoringDriver.Builder(scanner)
        .addExterns(includeDefaultExterns
            ? CommandLineRunner.getDefaultExterns() : ImmutableList.<SourceFile>of())
        .addExternsFromFile(externs)
        .addInputsFromFile(inputs)
        .build();
    List<SuggestedFix> fixes = driver.drive();
    if (dryRun) {
      System.out.println("SuggestedFixes: " + fixes);
    } else {
      ApplySuggestedFixes.applySuggestedFixesToFiles(fixes);
    }
  }

  public static void main(String[] args) throws Exception {
    new RefasterJs().doMain(args);
  }
}
