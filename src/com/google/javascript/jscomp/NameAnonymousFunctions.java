/*
 * Copyright 2004 The Closure Compiler Authors.
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

import java.util.logging.Logger;

/**
 * Gives anonymous function names. This makes it way easier to debug because
 * debuggers and stack traces use the function names. So if you have
 *
 * goog.string.htmlEscape = function(str) {
 * }
 *
 * It will become
 *
 * goog.string.htmlEscape = function $goog$string$htmlEscape$(str) {
 * }
 *
 */
class NameAnonymousFunctions implements CompilerPass {
  private static final Logger logger = Logger.getLogger(
      NameAnonymousFunctions.class.getName());

  static final char DELIMITER = '$';

  private final AbstractCompiler compiler;

  private int namedCount = 0;
  private int bytesUsed = 0;

  NameAnonymousFunctions(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    AnonymousFunctionNamingCallback namingCallback =
        new AnonymousFunctionNamingCallback(new AnonymousFunctionNamer());
    NodeTraversal.traverse(compiler, root, namingCallback);
    logger.fine("Named " + namedCount + " anon functions using " +
        bytesUsed + " bytes");
  }

  /**
   * Names anonymous functions. The function names don't have to be globally
   * unique or even locally unique. We make them somewhat unique because of a
   * bug in IE (and there may be other bugs we haven't found). See unit test for
   * more info.
   */
  private class AnonymousFunctionNamer
      implements AnonymousFunctionNamingCallback.FunctionNamer {
    private NodeNameExtractor nameExtractor;

    AnonymousFunctionNamer() {
      this.nameExtractor = new NodeNameExtractor(DELIMITER);
    }

    /**
     * Returns a likely not conflicting name to make IE happy. See unit test
     * for more info.
     */
    private String getLikelyNonConflictingName(String name) {
      return DELIMITER + name + DELIMITER;
    }

    @Override
    public final String getName(Node node) {
      return nameExtractor.getName(node);
    }

    @Override
    public final void setFunctionName(String name, Node fnNode) {
      Node fnNameNode = fnNode.getFirstChild();
      String uniqueName = getLikelyNonConflictingName(name);
      fnNameNode.setString(uniqueName);
      compiler.reportCodeChange();
      namedCount++;
      bytesUsed += uniqueName.length();
    }

    @Override
    public final String getCombinedName(String lhs, String rhs) {
      return lhs + DELIMITER + rhs;
    }
  }
}
