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

import com.google.common.base.Strings;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A compiler node traversal callback that invokes matchers against every node and
 * keeps track of any suggested fixes from the refactoring.
 *
 * @author mknichel@google.com (Mark Knichel)
 */
final class JsFlumeCallback implements Callback {

  private final Scanner scanner;
  private final Pattern includeFilePattern;
  private final List<Match> matches = new ArrayList<>();
  private final List<SuggestedFix> fixes = new ArrayList<>();

  JsFlumeCallback(Scanner scanner, Pattern includeFilePattern) {
    this.scanner = scanner;
    this.includeFilePattern = includeFilePattern;
  }

  List<Match> getMatches() {
    return matches;
  }

  List<SuggestedFix> getFixes() {
    return fixes;
  }

  @Override
  public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
    if (n.isFromExterns()) {
      return false;
    }
    String filename = n.getSourceFileName();
    if (includeFilePattern != null
        && !Strings.isNullOrEmpty(includeFilePattern.pattern())
        && !Strings.isNullOrEmpty(filename)) {
      return includeFilePattern.matcher(filename).find();
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    NodeMetadata metadata = new NodeMetadata(t.getCompiler());
    if (scanner.matches(n, metadata)) {
      Match match = new Match(n, metadata);
      fixes.addAll(scanner.processMatch(match));
      matches.add(match);
    }
  }
}
