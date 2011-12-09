/*
 * Copyright 2011 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.regex.RegExpTree;
import com.google.javascript.rhino.Node;

/**
 * Simplifies regular expression patterns and flags.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
class PeepholeSimplifyRegExp extends AbstractPeepholeOptimization {

  @Override
  Node optimizeSubtree(Node subtree) {
    if (subtree.isRegExp()) {
      // Split regexp into pattern and flags.
      String pattern = subtree.getFirstChild().getString();
      String flags = subtree.getChildCount() == 2
          ? subtree.getLastChild().getString() : "";
      // Parse to an AST and optimize.
      RegExpTree regexTree;
      try {
        regexTree = RegExpTree.parseRegExp(pattern, flags);
      } catch (IllegalArgumentException ex) {
        // Warnings are propagated in the CheckRegExp pass.
        return subtree;
      }
      regexTree = regexTree.simplify(flags);
      // Decompose the AST.
      String literal = regexTree.toString();
      String newPattern = literal.substring(1, literal.length() - 1);
      // Remove unnecessary flags and order them consistently for gzip.
      String newFlags = (
          // The g flags cannot match or replace more than one instance if it is
          // anchored at the front and back as in /^foo$/ and if the anchors are
          // relative to the whole string.
          // But if the regex has capturing groups, then the match operator
          // would return capturing groups without the g flag.
          (flags.contains("g")
           && (!RegExpTree.matchesWholeInput(regexTree, flags)
               || regexTree.hasCapturingGroup())
           ? "g" : "")
          // Remove the i flag if it doesn't have any effect.
          // E.g. /[a-z0-9_]/i -> /\w/
          + (flags.contains("i") && regexTree.isCaseSensitive() ? "i" : "")
          // If the regular expression contains no anchors, then the m flag has
          // no effect.
          + (flags.contains("m") && regexTree.containsAnchor() ? "m" : ""));
      // Update the original if something was done.
      if (!(newPattern.equals(pattern) && newFlags.equals(flags))) {
        subtree.getFirstChild().setString(newPattern);
        if (!"".equals(newFlags)) {
          subtree.getLastChild().setString(newFlags);
        } else if (subtree.getChildCount() == 2) {
          subtree.getLastChild().detachFromParent();
        }
        reportCodeChange();
      }
    }
    return subtree;
  }
}
