/*
 * Copyright 2025 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.function.Function.identity;

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.js.RuntimeJsLibManager;
import com.google.javascript.jscomp.js.RuntimeJsLibManager.ExternedField;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Takes the set of runtime libraries and fields used in a @closureUnaware nested compilation, and
 * then 1) ensures the library definitions are injected in the main AST and 2) passes all specific
 * $jscomp.* fields used into the @closureUnaware shadow AST as an argument.
 *
 * <p>Before:
 *
 * <pre>
 *   /** @closureUnaware * /
 *   (function() {
 *     use($jscomp_someField$$);
 *   }).call(undefined);
 * </pre>
 *
 * <p>After:
 *
 * <pre>
 *   /** @closureUnaware * /
 *   (function($jscomp_someField$$) {
 *     use($jscomp_someField$$);
 *   }).call(undefined, $jscomp.someField);
 * </pre>
 */
final class InjectClosureUnawareRuntimeLibraries implements CompilerPass {
  private final AbstractCompiler mainCompiler;
  private final AbstractCompiler shadowCompiler;
  private final List<ClosureUnawareCallSite> closureUnawareCalls;

  record ClosureUnawareCallSite(Node callNode, Node closureUnawareFunction) {
    ClosureUnawareCallSite {
      checkArgument(callNode.isCall(), callNode);
      checkArgument(closureUnawareFunction.isFunction(), closureUnawareFunction);
    }
  }

  InjectClosureUnawareRuntimeLibraries(
      AbstractCompiler mainCompiler,
      AbstractCompiler shadowCompiler,
      List<ClosureUnawareCallSite> closureUnawareCalls) {
    this.mainCompiler = mainCompiler;
    this.shadowCompiler = shadowCompiler;
    this.closureUnawareCalls = closureUnawareCalls;
  }

  @Override
  public void process(Node externs, Node root) {
    injectLibrariesIntoMainAst();
    injectFieldsIntoCallSites();
  }

  private void injectLibrariesIntoMainAst() {
    RuntimeJsLibManager mainManager = mainCompiler.getRuntimeJsLibManager();
    RuntimeJsLibManager shadowManager = shadowCompiler.getRuntimeJsLibManager();
    for (String runtimeLib : shadowManager.getInjectedLibraries()) {
      mainManager.ensureLibraryInjected(runtimeLib, /* force= */ false);
    }
  }

  private void injectFieldsIntoCallSites() {
    var fieldsByName =
        shadowCompiler.getRuntimeJsLibManager().getExternedFields().stream()
            .collect(toImmutableMap(ExternedField::qualifiedName, identity()));

    for (ClosureUnawareCallSite callSite : closureUnawareCalls) {
      Set<ExternedField> toInject =
          gatherRuntimeFields(callSite.closureUnawareFunction(), fieldsByName);
      injectAll(callSite, toInject);
    }
  }

  private void injectAll(ClosureUnawareCallSite callSite, Set<ExternedField> toInject) {
    if (toInject.isEmpty()) {
      return;
    }
    Node parameterParent = NodeUtil.getFunctionParameters(callSite.closureUnawareFunction());
    Node argumentParent = callSite.callNode();

    if (parameterParent.hasChildren() && parameterParent.getLastChild().isRest()) {
      throw new IllegalStateException(
          "rest parameters not allowed in closureUnaware function parameters, found "
              + parameterParent.getLastChild());
    }

    for (var field : toInject) {
      Node parameter = IR.name(field.qualifiedName()).srcref(parameterParent);
      Node argument =
          NodeUtil.newQName(mainCompiler, field.uncompiledName()).srcrefTree(argumentParent);

      parameterParent.addChildToBack(parameter);
      shadowCompiler.reportChangeToEnclosingScope(parameterParent);

      argumentParent.addChildToBack(argument);
      mainCompiler.reportChangeToEnclosingScope(argumentParent);
    }
  }

  /**
   * Returns the subset of {@code allInjectedFields} that are actually referenced in the given
   * {@code shadowAst}.
   */
  private static Set<ExternedField> gatherRuntimeFields(
      Node root, ImmutableMap<String, ExternedField> allKnownFields) {
    Set<ExternedField> seen = new LinkedHashSet<>();
    NodeUtil.Visitor visitor =
        (Node n) -> {
          if (!n.isName()) {
            return;
          }
          var field = allKnownFields.get(n.getString());
          if (field != null) {
            seen.add(field);
          }
        };
    NodeUtil.visitPreOrder(root, visitor);
    return seen;
  }
}
