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

import com.google.common.collect.Maps;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.*;
import java.util.logging.*;

/**
 * Gives anonymous fuction names that are optimized to be small and provides a
 * mapping back to the original names. This makes it way easier to debug because
 * debuggers and stack traces use the function names. So if you have
 *
 * goog.string.htmlEscape = function(str) {
 * }
 *
 * It will become
 *
 * goog.string.htmlEscape = function $qv(str) {
 * }
 *
 * And there will be mapping from $qv to goog.string.htmlEscape
 *
 */
class NameAnonymousFunctionsMapped implements CompilerPass {

  private static Logger logger = Logger.getLogger(
      NameAnonymousFunctionsMapped.class.getName());

  static final char PREFIX = '$';
  static final String PREFIX_STRING = "$";

  private final AbstractCompiler compiler;
  private final NameGenerator nameGenerator;
  private final Map<String, String> renameMap;

  private int namedCount = 0;
  private int bytesUsed = 0;

  NameAnonymousFunctionsMapped(AbstractCompiler compiler) {
    this.compiler = compiler;
    nameGenerator = new NameGenerator(
        Collections.<String>emptySet(), PREFIX_STRING, null);
    renameMap = Maps.newHashMap();
  }

  @Override
  public void process(Node externs, Node root) {
    AnonymousFunctionNamingCallback namingCallback =
        new AnonymousFunctionNamingCallback(new MappedFunctionNamer());
    NodeTraversal.traverse(compiler, root, namingCallback);
    logger.info("Named " + namedCount + " anon functions using " +
        bytesUsed + " bytes");
    if (namedCount > 0) {
      compiler.reportCodeChange();
    }
  }

  /**
   * Names anonymous functions. The function names don't have to be globally
   * unique or even locally unique. We make them somewhat unique because of a
   * bug in IE (and there may be other bugs we haven't found). See unit test for
   * more info.
   */
  private class MappedFunctionNamer
      implements AnonymousFunctionNamingCallback.FunctionNamer {
    static final char DELIMITER = '.';

    @Override
    public final String getName(Node node) {
      switch (node.getType()) {
        case Token.NAME:
        case Token.STRING:
          return node.getString();
        default:
          return new CodePrinter.Builder(node).build();
      }
    }

    @Override
    public final void setFunctionName(String name, Node fnNode) {
      Node fnNameNode = fnNode.getFirstChild();
      String newName = renameMap.get(name);
      if (newName == null) {
        newName = nameGenerator.generateNextName();
        renameMap.put(name, newName);
      }
      fnNameNode.setString(newName);
      namedCount++;
      bytesUsed += newName.length();
    }

    @Override
    public final String getCombinedName(String lhs, String rhs) {
      return lhs + DELIMITER + rhs;
    }
  }

  /**
   * Gets the function renaming map (the "answer key").
   *
   * @return A mapping from original names to new names
   */
  VariableMap getFunctionMap() {
    return new VariableMap(renameMap);
  }
}
