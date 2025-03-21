/*
 * Copyright 2018 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** A normalization pass to re-write Util.$getDefine calls to make them work in compiled mode. */
public class J2clUtilGetDefineRewriterPass extends AbstractPostOrderCallback
    implements CompilerPass {
  private final AbstractCompiler compiler;
  private ImmutableSet<String> defines;

  public J2clUtilGetDefineRewriterPass(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    if (!J2clSourceFileChecker.shouldRunJ2clPasses(compiler)) {
      return;
    }
    defines = compiler.getDefineNames();

    var checkGetDefineCalls = new CheckGetDefineCalls();
    NodeTraversal.traverse(compiler, root, checkGetDefineCalls);
    checkGetDefineCalls.validateRegisteredDefines();

    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (isUtilGetDefineCall(n)) {
      rewriteUtilGetDefine(t, n);
    }
  }

  private void rewriteUtilGetDefine(NodeTraversal t, Node callNode) {
    Node firstExpr = callNode.getSecondChild();
    Node secondExpr = callNode.getLastChild();

    if (secondExpr != firstExpr) {
      secondExpr.detach();
    } else {
      // There is no secondExpr; default to null.
      secondExpr = IR.nullNode();
    }

    Node replacement = getDefineReplacement(firstExpr, secondExpr);
    replacement.srcrefTreeIfMissing(callNode);
    callNode.replaceWith(replacement);
    t.reportCodeChange();
  }

  private Node getDefineReplacement(Node firstExpr, Node secondExpr) {
    String defineName = firstExpr.getString();
    if (defines.contains(defineName)) {
      Node define = NodeUtil.newQName(compiler, ProcessDefines.getGlobalDefineAlias(defineName));
      Node defineStringValue = NodeUtil.newCallNode(IR.name("String"), define);
      return IR.comma(secondExpr, defineStringValue);
    } else {
      return secondExpr;
    }
  }

  private static boolean isUtilGetDefineCall(Node n) {
    return n.isCall() && isUtilGetDefineMethodName(n.getFirstChild().getQualifiedName());
  }

  private static boolean isUtilGetDefineMethodName(String fnName) {
    // TODO: Switch this to the filename + property name heuristic which is less brittle.
    return fnName != null && fnName.endsWith(".$getDefine") && fnName.contains("Util");
  }

  static final DiagnosticType J2CL_SYSTEM_GET_PROPERTY_CONSTANT_NAME =
      DiagnosticType.error(
          "JSC_J2CL_SYSTEM_GET_PROPERTY_CONSTANT_NAME",
          "Calls to System.getProperty must use a String literal for the property name");

  static final DiagnosticType J2CL_SYSTEM_GET_PROPERTY_UNKNOWN_PROPERTY =
      DiagnosticType.error(
          "JSC_J2CL_SYSTEM_GET_PROPERTY_UNKNOWN_PROPERTY",
          "Unknown system property \"{0}\", are you missing a call to"
              + " jre.addSystemPropertyFromGoogDefine?");

  static final DiagnosticType J2CL_ADD_SYSTEM_PROPERTY_CONSTANT_NAME =
      DiagnosticType.error(
          "JSC_J2CL_ADD_SYSTEM_PROPERTY_CONSTANT_NAME",
          "Calls to addSystemPropertyFromGoogDefine must use a String literal for the define"
              + " name");

  static final DiagnosticType J2CL_ADD_SYSTEM_PROPERTY_UNKNOWN_DEFINE =
      DiagnosticType.error(
          "JSC_J2CL_ADD_SYSTEM_PROPERTY_UNKNOWN_DEFINE", "Unknown goog.define with name: {0}");

  private final class CheckGetDefineCalls extends AbstractPostOrderCallback {
    private final Map<String, Node> defineNameToAddSystemPropertyCall = new LinkedHashMap<>();
    private final Map<String, Node> defineNameToGetDefineCall = new LinkedHashMap<>();

    @Override
    public void visit(NodeTraversal t, Node n, @Nullable Node parent) {
      if (isAddSystemPropertyFromGoogDefine(n)) {
        String defineName = getDefineNameParameter(n);
        if (defineName != null) {
          defineNameToAddSystemPropertyCall.put(defineName, n);
        } else {
          compiler.report(JSError.make(n, J2CL_ADD_SYSTEM_PROPERTY_CONSTANT_NAME));
        }
      } else if (isUtilGetDefineCall(n)) {
        String defineName = getDefineNameParameter(n);
        if (defineName != null) {
          defineNameToGetDefineCall.put(defineName, n);
        } else {
          compiler.report(JSError.make(n, J2CL_SYSTEM_GET_PROPERTY_CONSTANT_NAME));
        }
      }
    }

    private void validateRegisteredDefines() {
      // Validate that all addSystemPropertyFromGoogDefine calls reference a known define.
      for (var entry : defineNameToAddSystemPropertyCall.entrySet()) {
        if (!defines.contains(entry.getKey())) {
          compiler.report(
              JSError.make(
                  entry.getValue(), J2CL_ADD_SYSTEM_PROPERTY_UNKNOWN_DEFINE, entry.getKey()));
        }
      }

      // Validate that Util.$getDefine() calls reference a define registered with
      // addSystemPropertyFromGoogDefine.
      for (var entry : defineNameToGetDefineCall.entrySet()) {
        // Tolerate System.getProperty() calls that reference something that is never goog.define'd.
        // This provides compatibility with code that never intended reference a define (ex. third
        // party code).
        if (!defines.contains(entry.getKey())) {
          continue;
        }

        if (!defineNameToAddSystemPropertyCall.containsKey(entry.getKey())) {
          compiler.report(
              JSError.make(
                  entry.getValue(), J2CL_SYSTEM_GET_PROPERTY_UNKNOWN_PROPERTY, entry.getKey()));
        }
      }
    }

    private static @Nullable String getDefineNameParameter(Node callNode) {
      Node nameNode = callNode.getSecondChild();
      if (nameNode == null || !nameNode.isString()) {
        return null;
      }
      return nameNode.getString();
    }

    private static boolean isAddSystemPropertyFromGoogDefine(Node n) {
      return n.isCall()
          && n.getFirstChild().getQualifiedName() != null
          && n.getFirstChild().getQualifiedName().endsWith("addSystemPropertyFromGoogDefine");
    }
  }
}
