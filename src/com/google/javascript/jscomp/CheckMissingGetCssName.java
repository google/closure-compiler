/*
 * Copyright 2009 The Closure Compiler Authors.
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

import com.google.common.annotations.GwtIncompatible;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ensures string literals matching certain patterns are only used as
 * goog.getCssName parameters.
 *
 * @author mkretzschmar@google.com (Martin Kretzschmar)
 */
@GwtIncompatible("java.util.regex")
class CheckMissingGetCssName
    extends AbstractPostOrderCallback implements CompilerPass {
  private final AbstractCompiler compiler;
  private final CheckLevel level;
  private final Matcher blacklist;

  static final String GET_CSS_NAME_FUNCTION = "goog.getCssName";
  static final String GET_UNIQUE_ID_FUNCTION = ".getUniqueId";

  static final DiagnosticType MISSING_GETCSSNAME =
      DiagnosticType.disabled(
          "JSC_MISSING_GETCSSNAME",
          "missing goog.getCssName around literal ''{0}''");

  CheckMissingGetCssName(AbstractCompiler compiler, CheckLevel level,
      String blacklistRegex) {
    this.compiler = compiler;
    this.level = level;
    this.blacklist =
        Pattern.compile("\\b(?:" + blacklistRegex + ")").matcher("");
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if ((n.isString() || n.isTemplateLitString()) && !parent.isGetProp() && !parent.isRegExp()) {
      String s = n.isString() ? n.getString() : n.getCookedString();

      for (blacklist.reset(s); blacklist.find();) {
        if (parent.isTemplateLit()) {
          if (parent.hasMoreThanOneChild()) {
            // Ignore template string with substitutions
            continue;
          } else {
            n = parent;
          }
        }
        if (insideGetCssNameCall(n)) {
          continue;
        }
        if (insideGetUniqueIdCall(n)) {
          continue;
        }
        if (insideAssignmentToIdConstant(n)) {
          continue;
        }
        compiler.report(t.makeError(n, level, MISSING_GETCSSNAME,
                blacklist.group()));
      }
    }
  }

  /** Returns whether the node is an argument of a goog.getCssName call. */
  private static boolean insideGetCssNameCall(Node n) {
    Node parent = n.getParent();
    return parent.isCall() &&
        parent.getFirstChild().matchesQualifiedName(GET_CSS_NAME_FUNCTION);
  }

  /**
   * Returns whether the node is an argument of a function that returns
   * a unique id (the last part of the qualified name matches
   * GET_UNIQUE_ID_FUNCTION).
   */
  private static boolean insideGetUniqueIdCall(Node n) {
    Node parent = n.getParent();
    String name = parent.isCall() ?
        parent.getFirstChild().getQualifiedName() : null;

    return name != null && name.endsWith(GET_UNIQUE_ID_FUNCTION);
  }

  /**
   * Returns whether the node is the right hand side of an assignment or
   * initialization of a variable named *_ID of *_ID_.
   */
  private boolean insideAssignmentToIdConstant(Node n) {
    Node parent = n.getParent();
    if (parent.isAssign()) {
      String qname = parent.getFirstChild().getQualifiedName();
      return qname != null && isIdName(qname);
    } else if (parent.isName()) {
      Node grandParent = parent.getParent();
      if (grandParent != null && NodeUtil.isNameDeclaration(grandParent)) {
        String name = parent.getString();
        return isIdName(name);
      } else {
        return false;
      }
    } else {
      return false;
    }
  }

  private static boolean isIdName(String name) {
    return name.endsWith("ID") || name.endsWith("ID_");
  }
}
