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

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.TemplateAstMatcher;
import com.google.javascript.rhino.Node;

import java.util.Map;

/**
 * A {@link Matcher} that can take arbitrary JS source code and use it as a
 * template to find matches in other source code.
 *
 * @author mknichel@google.com (Mark Knichel)
 */
public final class JsSourceMatcher implements Matcher {

  private final TemplateAstMatcher matcher;

  /**
   * Constructs this matcher with a Function node that serves as the template
   * to match all other nodes against. The body of the function will be used
   * to match against.
   */
  public JsSourceMatcher(AbstractCompiler compiler, Node templateNode) {
    matcher = new TemplateAstMatcher(compiler, templateNode);
  }

  @Override public boolean matches(Node n, NodeMetadata metadata) {
    return matcher.matches(n);
  }

  /**
   * Returns a map from named template node strings to Nodes that were the
   * equivalent matches from the last matched template.
   */
  public Map<String, Node> getTemplateNodeToMatchMap() {
    return matcher.getTemplateNodeToMatchMap();
  }
}
