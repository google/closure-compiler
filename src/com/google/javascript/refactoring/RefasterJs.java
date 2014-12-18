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
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CommandLineRunner;
import com.google.javascript.jscomp.ErrorManager;
import com.google.javascript.jscomp.SourceFile;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.BooleanOptionHandler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

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

  @Option(
      name = "--inputs",
      usage = "List of input files for the refactoring. You may also use glob patterns to "
          + "match files. For example, use --js='**.js' --js='!**_test.js' "
          + "to recursively include all js files that do not end in _test.js")
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

  @Option(name = "--verbose", usage = "Use this to print verbose statements from RefasterJS.")
  private boolean verbose = false;

  @Argument
  private List<String> arguments = new ArrayList<>();

  private void doMain(String[] args) throws Exception {
    CmdLineParser parser = new CmdLineParser(this);
    parser.parseArgument(args);
    if (args.length < 1 || displayHelp) {
      CmdLineParser p = new CmdLineParser(this);
      p.printUsage(System.out);
      return;
    }
    Preconditions.checkArgument(
        !Strings.isNullOrEmpty(refasterJsTemplate), "--refasterjs_template must be provided");
    List<String> fileInputs = getInputs();
    Preconditions.checkArgument(
        !fileInputs.isEmpty(), "At least one input must be provided in the --inputs flag.");
    for (String input : fileInputs) {
      Preconditions.checkArgument(
          new File(input).exists(), "Input file %s does not exist.", input);
    }

    if (!verbose) {
      // This is done here instead of using the Compiler#setLoggingLevel function since the
      // Compiler is created and then run inside of RefactoringDriver.
      Logger errorManagerLogger = Logger.getLogger("com.google.javascript.jscomp");
      errorManagerLogger.setLevel(Level.OFF);
    }

    RefasterJsScanner scanner = new RefasterJsScanner();
    scanner.loadRefasterJsTemplate(refasterJsTemplate);
    RefactoringDriver driver = new RefactoringDriver.Builder(scanner)
        .addExterns(includeDefaultExterns
            ? CommandLineRunner.getDefaultExterns() : ImmutableList.<SourceFile>of())
        .addExternsFromFile(externs)
        .addInputsFromFile(fileInputs)
        .build();
    System.out.println("Compiling JavaScript code and searching for suggested fixes.");
    List<SuggestedFix> fixes = driver.drive();

    if (!verbose) {
      // When running in quiet mode, the Compiler's error manager will not have printed
      // this information itself.
      ErrorManager errorManager = driver.getCompiler().getErrorManager();
      System.out.println("Compiler results: " + errorManager.getErrorCount()
          + " errors and " + errorManager.getWarningCount() + " warnings.");
    }
    System.out.println("Found " + fixes.size() + " suggested fixes.");
    if (dryRun) {
      if (!fixes.isEmpty()) {
        System.out.println("SuggestedFixes: " + fixes);
      }
    } else {
      Set<String> affectedFiles = new TreeSet<>();
      for (SuggestedFix fix : fixes) {
        affectedFiles.addAll(fix.getReplacements().keySet());
      }
      System.out.println("Modifying affected files: " + affectedFiles);
      ApplySuggestedFixes.applySuggestedFixesToFiles(fixes);
    }
  }

  private List<String> getInputs() throws IOException {
    Set<String> patterns = new HashSet<>();
    // The args4j library can't handle multiple files provided within the same flag option,
    // like --inputs=file1.js,file2.js so handle that here.
    Splitter commaSplitter = Splitter.on(',');
    for (String input : inputs) {
      patterns.addAll(commaSplitter.splitToList(input));
    }
    patterns.addAll(arguments);
    return CommandLineRunner.findJsFiles(patterns);
  }

  public static void main(String[] args) throws Exception {
    new RefasterJs().doMain(args);
  }
}
