/*
 * Copyright 2021 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.lint;

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.Node;
import java.util.HashSet;
import java.util.Set;

/**
 * Checks that goog.module statement matches the generated TypeScript module namespace, which is
 * based on the file path.
 */
public final class CheckGoogModuleTypeScriptName implements NodeTraversal.Callback, CompilerPass {
  public static final DiagnosticType MODULE_NAMESPACE_MISMATCHES_TYPESCRIPT_NAMESPACE =
      DiagnosticType.disabled(
          "JSC_MODULE_NAMESPACE_MISMATCHES_TYPESCRIPT_NAMESPACE",
          "goog.module namespace does not match the future TypeScript namespace, which is generated"
              + " from the file path."
              + " The correct namespace is: \"{0}\"");
  private static final Set<String> allowedDirectories = new HashSet<>();

  // MOE::begin_strip
  static {
    allowedDirectories.add("google3/gws/");
    allowedDirectories.add("google3/java/com/google/gws/");
    allowedDirectories.add("google3/javascript/search/");
  }
  // MOE::end_strip

  private final AbstractCompiler compiler;

  private boolean finished = false;

  public CheckGoogModuleTypeScriptName(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    // Traverse top-level statements until a `goog.module` statement is found.
    return !finished
        && (parent == null || parent.isRoot() || parent.isScript() || parent.isModuleBody());
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (NodeUtil.isGoogModuleCall(n)) {
      checkGoogModuleNamespace(t, n);
      finished = true;
    }
  }

  private void checkGoogModuleNamespace(NodeTraversal t, Node n) {
    String originalNamespace = n.getFirstChild().getSecondChild().getString();
    String sourceName = t.getSourceName();
    if (originalNamespace == null || sourceName == null) {
      return;
    }
    // MOE::begin_strip
    int google3Index = sourceName.indexOf("google3");
    if (google3Index == -1) {
      sourceName = "google3/" + sourceName;
    } else if (google3Index != 0) {
      sourceName = sourceName.substring(google3Index);
    }
    // MOE::end_strip
    String replacementNamespace =
        sourceName.replace('/', '.').substring(0, sourceName.length() - ".js".length());
    if (!originalNamespace.equals(replacementNamespace)) {
      for (String allowedDirectory : allowedDirectories) {
        if (sourceName.startsWith(allowedDirectory)) {
          t.report(n, MODULE_NAMESPACE_MISMATCHES_TYPESCRIPT_NAMESPACE, replacementNamespace);
          return;
        }
      }
    }
  }
}
