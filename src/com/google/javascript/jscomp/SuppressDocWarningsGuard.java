/*
 * Copyright 2010 The Closure Compiler Authors.
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
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Map;

/**
 * Filters warnings based on in-code {@code @suppress} annotations.
 * @author nicksantos@google.com (Nick Santos)
 */
class SuppressDocWarningsGuard extends WarningsGuard {
  private static final long serialVersionUID = 1L;

  /** Warnings guards for each suppressible warnings group, indexed by name. */
  private final Map<String, DiagnosticGroupWarningsGuard> suppressors =
      Maps.newHashMap();

  /**
   * The suppressible groups, indexed by name.
   */
  SuppressDocWarningsGuard(Map<String, DiagnosticGroup> suppressibleGroups) {
    for (Map.Entry<String, DiagnosticGroup> entry :
             suppressibleGroups.entrySet()) {
      suppressors.put(
          entry.getKey(),
          new DiagnosticGroupWarningsGuard(
              entry.getValue(),
              CheckLevel.OFF));
    }
  }

  @Override
  public CheckLevel level(JSError error) {
    Node node = error.node;
    if (node != null) {
      for (Node current = node;
           current != null;
           current = current.getParent()) {
        int type = current.getType();
        JSDocInfo info = null;

        // We only care about function annotations at the FUNCTION and SCRIPT
        // level. Otherwise, the @suppress annotation has an implicit
        // dependency on the exact structure of our AST, and that seems like
        // a bad idea.
        if (type == Token.FUNCTION) {
          info = NodeUtil.getFunctionJSDocInfo(current);
        } else if (type == Token.SCRIPT) {
          info = current.getJSDocInfo();
        } else if (type == Token.ASSIGN) {
          Node rhs = current.getLastChild();
          if (rhs.isFunction()) {
            info = NodeUtil.getFunctionJSDocInfo(rhs);
          }
        }

        if (info != null) {
          for (String suppressor : info.getSuppressions()) {
            WarningsGuard guard = suppressors.get(suppressor);

            // Some @suppress tags are for other tools, and
            // may not have a warnings guard.
            if (guard != null) {
              CheckLevel newLevel = guard.level(error);
              if (newLevel != null) {
                return newLevel;
              }
            }
          }
        }
      }
    }
    return null;
  }

  @Override
  public int getPriority() {
    // Happens after path-based filtering, but before other times
    // of filtering.
    return WarningsGuard.Priority.SUPPRESS_DOC.value;
  }
}
