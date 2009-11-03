/*
 * Copyright 2004 Google Inc.
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
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


/**
 * Checks that functions have the correct number of arguments.
 *
*
 */
class FunctionCheck implements CompilerPass {

  private final AbstractCompiler compiler;

  /** CheckLevel for reporting {@link #WRONG_ARGUMENT_COUNT_ERROR} */
  private final CheckLevel level;

  // We store the FunctionInfo objects in a map so we do not have to go through
  // them more than once. More importantly, we do that because we remove the
  // var_args argument the first time and therefore we cannot use the same
  // function node to derive a new FunctionInfo.
  private final Map<Node, FunctionInfo> functionInfos;

  // Errors

  static final DiagnosticType WRONG_ARGUMENT_COUNT_ERROR = DiagnosticType.error(
      "JSC_WRONG_ARGUMENT_COUNT",
      "Function {0}: called with {1} argument(s). " +
      "All definitions of this function require at least {2} argument(s)" +
      "{3}.");

  static final DiagnosticType OPTIONAL_ARGS_ERROR = DiagnosticType.error(
      "JSC_OPTIONAL_ARGS_ERROR",
      "Required argument must precede optional argument(s)");

  static final DiagnosticType VAR_ARGS_ERROR = DiagnosticType.error(
      "JSC_VAR_ARGS_ERROR",
      "Argument must precede var_args argument");


  FunctionCheck(AbstractCompiler compiler, CheckLevel level) {
    this.compiler = compiler;
    this.level = level;
    this.functionInfos = new HashMap<Node, FunctionInfo>();
  }

  public void process(Node externs, Node root) {
    NodeTraversal.traverseRoots(compiler, 
        Lists.newArrayList(externs, root), new ArgCheck());
  }

  /**
   * Contains information about the number of args a function accepts.
   */
  static class FunctionInfo {
    final int args;
    final int optionalArgs;
    final boolean hasVarArgs;

    FunctionInfo(int args, int optionalArgs, boolean hasVarArgs) {
      this.args = args;
      this.optionalArgs = optionalArgs;
      this.hasVarArgs = hasVarArgs;
    }

    @Override public boolean equals(Object other) {
      if (!(other instanceof FunctionInfo)) {
        return false;
      }

      FunctionInfo o = (FunctionInfo) other;

      return o.args == args &&
             o.optionalArgs == optionalArgs &&
             o.hasVarArgs == hasVarArgs;
    }

    @Override public int hashCode() {
      int result = 17;
      result = 37 * result + args;
      result = 37 * result + optionalArgs;
      result = 37 * result + (hasVarArgs ? 1 : 0);
      return result;
    }

    @Override public String toString() {
      return args + " total argument(s) " +
             "of which " + optionalArgs + " is/are optional" +
             (hasVarArgs ? ", var_args supported" : "");
    }
  }

  /**
   * Second pass: look at the function calls and check that the number of
   * arguments are okay.
   */
  class ArgCheck extends AbstractPostOrderCallback {
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {

        case Token.NEW:
        case Token.CALL:
          Node fn = n.getFirstChild();
          if (fn.getType() == Token.NAME) {

            String fnName = fn.getString();

            // Lookup the function
            Scope.Var v = t.getScope().getVar(fnName);

            // VarCheck should have caught this undefined function
            if (v == null) {
              return;
            }

            Node fnDef = v.getInitialValue();
            if (fnDef == null ||
                fnDef.getType() != Token.FUNCTION) {
              // It's a variable, can't check this.
              return;
            }

            FunctionInfo f = getFunctionInfo(fnDef, v.getInputName());

            checkCall(n, fnName, Collections.singletonList(f), t, level);
          }
          break;
      }
    }
  }

  static void checkCall(Node n,
                        String fnName,
                        Iterable<FunctionInfo> infos,
                        NodeTraversal t,
                        CheckLevel level) {
    int count = n.getChildCount() - 1;
    boolean matched = false;

    int minArgs = Integer.MAX_VALUE;
    int maxArgs = Integer.MIN_VALUE;

    for (FunctionInfo f : infos) {
      int requiredCount = f.args - f.optionalArgs;
      if (count >= requiredCount && (count <= f.args || f.hasVarArgs)) {
        matched = true;
        break;
      }

      minArgs = Math.min(minArgs, requiredCount);
      maxArgs = Math.max(maxArgs, f.hasVarArgs ? Integer.MAX_VALUE : f.args);
    }

    if (!matched) {
      t.getCompiler().report(
          JSError.make(t, n,
                       level,
                       WRONG_ARGUMENT_COUNT_ERROR, fnName,
                       String.valueOf(count), String.valueOf(minArgs),
                       maxArgs != Integer.MAX_VALUE
                       ? " and no more than " + maxArgs + " argument(s)"
                       : ""));
    }
  }

  /**
   * Gets a {@link FunctionInfo} instance containing information about a
   * particular function's arguments. Caches the result for faster handling of
   * repeated queries.
   *
   * @param fn A FUNCTION node
   * @param fnSourceName The name of the script source in which the function is
   *     defined (for formatting error/warning messages)
   */
  FunctionInfo getFunctionInfo(Node fn, String fnSourceName) {
    FunctionInfo fi = functionInfos.get(fn);
    if (fi == null) {
      fi = createFunctionInfo(compiler, fn, fnSourceName);
      functionInfos.put(fn, fi);
    }
    return fi;
  }

  /**
   * Given a function node, creates a {@link FunctionInfo} instance containing
   * information about the function's arguments.
   *
   * @param compiler The compiler
   * @param fn A FUNCTION node
   * @param fnSourceName The name of the script source in which the function is
   *     defined (for formatting error/warning messages)
   */
  static FunctionInfo createFunctionInfo(
      AbstractCompiler compiler, Node fn, String fnSourceName) {
    Preconditions.checkState(fn.getType() == Token.FUNCTION);

    // Arguments that start with OPTIONAL_ARG_PREFIX are considered
    // optional. Count the number of required and optional args.
    int numArgs = 0, optArgs = 0;
    boolean hasVarArgs = false;
    Node args = fn.getFirstChild().getNext();
    Node varArg = null;
    for (Node a = args.getFirstChild(); a != null; a = a.getNext()) {
      Preconditions.checkState(a.getType() == Token.NAME);

      String argName = a.getString();
      if (hasVarArgs) {
        // var_args must be the last param
        compiler.report(JSError.make(fnSourceName, a, VAR_ARGS_ERROR));
      }

      if (a.getBooleanProp(Node.IS_VAR_ARGS_PARAM)) {
        varArg = a;
        hasVarArgs = true;
        // we don't want to count the var_args as an argument
        numArgs--;
      } else if (a.getBooleanProp(Node.IS_OPTIONAL_PARAM)) {
        optArgs++;
      } else if (optArgs > 0) {
        // Optional args shouldn't precede non-optional ones
        compiler.report(JSError.make(fnSourceName, a, OPTIONAL_ARGS_ERROR));
      }
      numArgs++;
    }

    // create the FunctionInfo
    return new FunctionInfo(numArgs, optArgs, hasVarArgs);
  }
}
