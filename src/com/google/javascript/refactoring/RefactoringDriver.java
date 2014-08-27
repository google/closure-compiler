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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DependencyOptions;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.Node;

import java.util.List;

/**
 * Primary driver of a refactoring. This class collects the inputs, runs the refactoring over
 * the compiled input, and then collects the suggested fixes based on the refactoring.
 *
 * @author mknichel@google.com (Mark Knichel)
 */
final class RefactoringDriver {

  private final Scanner scanner;
  private final Compiler compiler;
  private final Node rootNode;

  RefactoringDriver(Scanner scanner, List<String> inputs, List<String> externs) {
    this.scanner = scanner;
    this.compiler = createCompiler(inputs, externs);
    this.rootNode = this.compiler.getRoot();
  }

  /**
   * Run the refactoring and return any suggested fixes as a result.
   */
  List<SuggestedFix> drive() {
    JsFlumeCallback callback = new JsFlumeCallback(scanner, null);
    NodeTraversal.traverse(compiler, rootNode, callback);
    return callback.getFixes();
  }

  private Compiler createCompiler(List<String> inputs, List<String> externs) {
    CompilerOptions options = getCompilerOptions();
    Compiler compiler = new Compiler();
    compiler.disableThreads();
    Function<String, SourceFile> toSourceFileFn = new Function<String, SourceFile>() {
      @Override public SourceFile apply(String file) {
        return new SourceFile.Builder().buildFromFile(file);
      }
    };
    List<SourceFile> inputSourceFiles = Lists.transform(inputs, toSourceFileFn);
    List<SourceFile> externSourceFiles = Lists.transform(externs, toSourceFileFn);
    compiler.compile(externSourceFiles, inputSourceFiles, options);
    return compiler;
  }

  @VisibleForTesting
  static CompilerOptions getCompilerOptions() {
    CompilerOptions options = new CompilerOptions();

    DependencyOptions deps = new DependencyOptions();
    deps.setDependencySorting(true);
    options.setDependencyOptions(deps);

    options.ideMode = true;
    options.checkSuspiciousCode = true;
    options.checkSymbols = true;
    options.checkTypes = true;
    options.closurePass = true;
    options.preserveGoogRequires = true;

    options.setAcceptConstKeyword(true);

    return options;
  }
}
