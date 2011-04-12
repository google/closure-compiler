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

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.CodingConvention.AssertionFunctionSpec;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.Node;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A compiler pass to run the type inference analysis.
 *
 */
class TypeInferencePass implements CompilerPass {

  static final DiagnosticType DATAFLOW_ERROR = DiagnosticType.warning(
      "JSC_INTERNAL_ERROR_DATAFLOW",
      "non-monotonic data-flow analysis");

  private final AbstractCompiler compiler;
  private final ReverseAbstractInterpreter reverseInterpreter;
  private Scope topScope;
  private ScopeCreator scopeCreator;
  private final Map<String, AssertionFunctionSpec> assertionFunctionsMap;

  /**
   * Local variables that are declared in an outer scope, but are assigned in
   * an inner scope. We cannot do type inference on these vars.
   */
  private final Multimap<Scope, Var> escapedLocalVars = HashMultimap.create();

  TypeInferencePass(AbstractCompiler compiler,
      ReverseAbstractInterpreter reverseInterpreter,
      Scope topScope, ScopeCreator scopeCreator) {
    this.compiler = compiler;
    this.reverseInterpreter = reverseInterpreter;
    this.topScope = topScope;
    this.scopeCreator = scopeCreator;

    assertionFunctionsMap = Maps.newHashMap();
    for (AssertionFunctionSpec assertionFucntion :
        compiler.getCodingConvention().getAssertionFunctions()) {
      assertionFunctionsMap.put(assertionFucntion.getFunctionName(),
          assertionFucntion);
    }
  }

  /**
   * Main entry point for type inference when running over the whole tree.
   *
   * @param externsRoot The root of the externs parse tree.
   * @param jsRoot The root of the input parse tree to be checked.
   */
  public void process(Node externsRoot, Node jsRoot) {
    Node externsAndJs = jsRoot.getParent();
    Preconditions.checkState(externsAndJs != null);
    Preconditions.checkState(
        externsRoot == null || externsAndJs.hasChild(externsRoot));

    inferTypes(externsAndJs);
  }

  /** Entry point for type inference when running over part of the tree. */
  void inferTypes(Node node) {
    NodeTraversal inferTypes = new NodeTraversal(
        compiler, new TypeInferringCallback(), scopeCreator);
    inferTypes.traverseWithScope(node, topScope);
  }

  private Collection<Var> getUnflowableVars(Scope scope) {
    List<Var> vars = Lists.newArrayList();
    for (Scope current = scope;
         current.isLocal(); current = current.getParent()) {
      vars.addAll(escapedLocalVars.get(current));
    }
    return vars;
  }

  void inferTypes(NodeTraversal t, Node n, Scope scope) {
    TypeInference typeInference =
        new TypeInference(
            compiler, computeCfg(n), reverseInterpreter, scope,
            assertionFunctionsMap, getUnflowableVars(scope));
    try {
      typeInference.analyze();
      escapedLocalVars.putAll(typeInference.getAssignedOuterLocalVars());

      // Resolve any new type names found during the inference.
      compiler.getTypeRegistry().resolveTypesInScope(scope);

    } catch (DataFlowAnalysis.MaxIterationsExceededException e) {
      compiler.report(t.makeError(n, DATAFLOW_ERROR));
    }
  }

  private class TypeInferringCallback implements ScopedCallback {
    public void enterScope(NodeTraversal t) {
      Scope scope = t.getScope();
      Node node = t.getCurrentNode();
      if (scope.isGlobal()) {
        inferTypes(t, node, scope);
      }
    }

    public void exitScope(NodeTraversal t) {
      Scope scope = t.getScope();
      Node node = t.getCurrentNode();
      if (scope.isLocal()) {
        inferTypes(t, node, scope);
      }
    }

    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return true;
    }

    public void visit(NodeTraversal t, Node n, Node parent) {
      // Do nothing
    }
  }

  private ControlFlowGraph<Node> computeCfg(Node n) {
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, false, false);
    cfa.process(null, n);
    return cfa.getCfg();
  }
}
