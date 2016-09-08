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

import com.google.common.base.Preconditions;
import com.google.javascript.rhino.Node;

/**
 * Checks whether there are J2CL generated source files with pattern:
 * "path/foo.js.zip!path/bar.java.js".
 */
final class J2clSourceFileChecker implements CompilerPass {

  private AbstractCompiler compiler;
  // The Annotation value type should be Boolean.
  static final String HAS_J2CL_ANNOTATION_KEY = "HAS_J2CL";

  J2clSourceFileChecker(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  private static Boolean hasJ2cl(Node root) {
    for (Node script : root.children()) {
      Preconditions.checkState(script.isScript());
      if (script.getSourceFileName() != null
          && script.getSourceFileName().endsWith(".java.js")
          && script.getSourceFileName().contains(".js.zip!")) {
        return Boolean.TRUE;
      }
    }
    return Boolean.FALSE;
  }

  @Override
  public void process(Node externs, Node root) {
    compiler.setAnnotation(HAS_J2CL_ANNOTATION_KEY, hasJ2cl(root));
  }

  /**
   * Indicates whether it should run future J2CL passes with information from the compiler. For
   * example, if the compiler's HAS_J2CL annotation is false, it should.
   */
  static boolean shouldRunJ2clPasses(AbstractCompiler compiler) {
    return compiler.getOptions().j2clPassMode.isExplicitlyOn()
        || Boolean.TRUE.equals(compiler.getAnnotation(HAS_J2CL_ANNOTATION_KEY));
  }
}
