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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.javascript.jscomp.OptimizeCalls.ReferenceMap;
import com.google.javascript.jscomp.diagnostic.LogFile;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import org.jspecify.nullness.Nullable;

/**
 * A compiler pass to optimize function return results. Currently this pass looks for results that
 * are completely unused and rewrites them as: "return x()" --> "x(); return"
 *
 * <p>Future work: expand this to look for use context to avoid unneeded type coercion: \
 *
 * <p>"return x.toString()" --> "return x" \
 *
 * <p>"return !!x" --> "return x"
 */
class OptimizeReturns implements OptimizeCalls.CallGraphCompilerPass, CompilerPass {

  private final AbstractCompiler compiler;

  // Allocated & cleaned up by process()
  private @Nullable LogFile decisionsLog;

  OptimizeReturns(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  @VisibleForTesting
  public void process(Node externs, Node root) {
    OptimizeCalls.builder()
        .setCompiler(compiler)
        .setConsiderExterns(false)
        .addPass(this)
        .build()
        .process(externs, root);
  }

  @Override
  public void process(Node externs, Node root, ReferenceMap definitions) {
    try (LogFile logFile = compiler.createOrReopenIndexedLog(this.getClass(), "decisions.log")) {
      decisionsLog = logFile; // avoid passing the log file through a bunch of methods
      // Find all function nodes whose callers ignore the return values.
      List<ArrayList<Node>> toOptimize = new ArrayList<>();

      // Find all the candidates before modifying the AST.
      for (Entry<String, ArrayList<Node>> entry : definitions.getNameReferences()) {
        String key = entry.getKey();
        ArrayList<Node> refs = entry.getValue();
        if (isCandidate(key, refs)) {
          decisionsLog.log("name %s\tremoving return value", key);
          toOptimize.add(refs);
        }
      }

      for (Entry<String, ArrayList<Node>> entry : definitions.getPropReferences()) {
        String key = entry.getKey();
        ArrayList<Node> refs = entry.getValue();
        if (isCandidate(key, refs)) {
          decisionsLog.log("property %s\tremoving return value", key);
          toOptimize.add(refs);
        }
      }

      // Now modify the AST
      for (ArrayList<Node> refs : toOptimize) {
        for (Node fn : ReferenceMap.getFunctionNodes(refs).values()) {
          rewriteReturns(fn);
        }
      }
    } finally {
      decisionsLog = null;
    }
  }

  /**
   * This reference set is a candidate for return-value-removal if: - if the all call sites are
   * known (not aliased, not exported) - if all call sites do not use the return value - if there is
   * at least one known function definition - if there is at least one use NOTE: unknown definitions
   * are allowed, as only known definitions will be removed.
   */
  private boolean isCandidate(String name, List<Node> refs) {
    if (!OptimizeCalls.mayBeOptimizableName(compiler, name)) {
      decisionsLog.log("%s\tnot an optimizable name", name);
      return false;
    }

    boolean seenCandidateDefiniton = false;
    boolean seenUse = false;
    for (Node n : refs) {
      // Assume indirect definitions references use the result
      if (ReferenceMap.isCallTarget(n) || ReferenceMap.isOptChainCallTarget(n)) {
        Node callNode = ReferenceMap.getCallOrNewNodeForTarget(n);
        if (NodeUtil.isExpressionResultUsed(callNode)) {
          // At least one call site uses the return value, this
          // is not a candidate.
          if (decisionsLog.isLogging()) { // avoid build location string when not logging
            decisionsLog.log("%s\treturn value used: %s", name, callNode.getLocation());
          }
          return false;
        }
        seenUse = true;
      } else if (isCandidateDefinition(n)) {
        // NOTE: While is is possible to optimize calls to functions for which we know
        // only some of the definition are candidates but to keep things simple, only
        // optimize if all of the definitions are known.
        seenCandidateDefiniton = true;
      } else {
        // If this isn't an non-aliasing reference (typeof, instanceof, etc)
        // then there is nothing that can be done.
        if (!OptimizeCalls.isAllowedReference(n)) {
          if (decisionsLog.isLogging()) { // avoid build location string when not logging
            decisionsLog.log("%s\tdisallowed reference: %s", name, n.getLocation());
          }
          return false;
        }
      }
    }

    if (!seenUse) {
      decisionsLog.log("%s\tno usage seen", name);
      return false;
    }
    if (!seenCandidateDefiniton) {
      decisionsLog.log("%s\tno definition seen", name);
      return false;
    }
    return true;
  }

  private boolean isCandidateDefinition(Node n) {
    Node parent = n.getParent();
    if (parent.isFunction() && NodeUtil.isFunctionDeclaration(parent)) {
      return true;
    } else if (ReferenceMap.isSimpleAssignmentTarget(n)) {
      if (isCandidateFunction(parent.getLastChild())) {
        return true;
      }
    } else if (n.isName()) {
      if (n.hasChildren() && isCandidateFunction(n.getFirstChild())) {
        return true;
      }
    } else if (isClassMemberDefinition(n)) {
      return true;
    }

    return false;
  }

  private boolean isClassMemberDefinition(Node n) {
    return n.isMemberFunctionDef() && n.getParent().isClassMembers();
  }

  private static boolean isCandidateFunction(Node n) {
    switch (n.getToken()) {
      case FUNCTION:
        // Named function expression can be recursive, this creates an alias of the name, meaning
        // it might be used in an unexpected way.
        return !NodeUtil.isNamedFunctionExpression(n);
      case COMMA:
      case CAST:
        return isCandidateFunction(n.getLastChild());
      case HOOK:
        return isCandidateFunction(n.getSecondChild()) && isCandidateFunction(n.getLastChild());
      case OR:
      case AND:
      case COALESCE:
        return isCandidateFunction(n.getFirstChild()) && isCandidateFunction(n.getLastChild());
      default:
        return false;
    }
  }

  /**
   * For the supplied function node, rewrite all the return expressions so that: return foo();
   * becomes: foo(); return; Useless return will be removed later by the peephole optimization
   * passes.
   */
  private void rewriteReturns(Node fnNode) {
    checkState(fnNode.isFunction());
    final Node body = fnNode.getLastChild();
    NodeUtil.visitPostOrder(
        body,
        new NodeUtil.Visitor() {
          @Override
          public void visit(Node n) {
            if (n.isReturn() && n.hasOneChild()) {
              Node result = n.getFirstChild();
              boolean keepValue = !isRemovableValue(result);
              result.detach();
              if (keepValue) {
                IR.exprResult(result).srcref(result).insertBefore(n);
              } else {
                NodeUtil.markFunctionsDeleted(result, compiler);
              }
              compiler.reportChangeToEnclosingScope(body);
            }
          }
        },
        new NodeUtil.MatchShallowStatement());
  }

  // Just remove objects that don't reference properties (object literals) or names (functions)
  // So we don't need to update the graph.
  private boolean isRemovableValue(Node n) {
    switch (n.getToken()) {
      case TEMPLATELIT:
      case ARRAYLIT:
        for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
          if ((!child.isEmpty()) && !isRemovableValue(child)) {
            return false;
          }
        }
        return true;

      case REGEXP:
      case STRINGLIT:
      case NUMBER:
      case NULL:
      case TRUE:
      case FALSE:
      case TEMPLATELIT_STRING:
        return true;
      case TEMPLATELIT_SUB:
      case CAST:
      case NOT:
      case VOID:
      case NEG:
        return isRemovableValue(n.getFirstChild());

      default:
        return false;
    }
  }
}
