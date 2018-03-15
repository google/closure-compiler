/*
 * Copyright 2016 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.rhino.Node;

/**
 * Checks whether there are J2CL generated source files with pattern "*.java.js".
 */
final class J2clSourceFileChecker implements CompilerPass {

  static final String HAS_J2CL_ANNOTATION_KEY = "HAS_J2CL";

  private final AbstractCompiler compiler;

  J2clSourceFileChecker(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  private static boolean hasJ2cl(Node root) {
    for (Node script : root.children()) {
      checkState(script.isScript());
      if (script.getSourceFileName() != null && script.getSourceFileName().endsWith(".java.js")) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void process(Node externs, Node root) {
    if (hasJ2cl(root)) {
      markToRunJ2clPasses(compiler);
    }
  }

  static void markToRunJ2clPasses(AbstractCompiler compiler) {
    compiler.setAnnotation(HAS_J2CL_ANNOTATION_KEY, true);
  }

  /**
   * Indicates whether it should run future J2CL passes with information from the compiler. For
   * example, if the compiler's HAS_J2CL annotation is false, it should.
   */
  static boolean shouldRunJ2clPasses(AbstractCompiler compiler) {
    return Boolean.TRUE.equals(compiler.getAnnotation(HAS_J2CL_ANNOTATION_KEY));
  }
}
