/*
 * Copyright 2024 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.jstype.JSType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Rewrites call-sites of functions that have goog.callerLocation as a default parameter.
 *
 * <p>E.g: function signal(val, here: goog.CodeLocation = goog.callerLocation()) {}
 *
 * <p>When a function is called without providing the optional `here` argument, we will rewrite the
 * function call to include the code location.
 *
 * <p>i.e. `signal(0, goog.callerLocationIdInternalDoNotCallOrElse(path/to/file.ts:lineno:charno))`
 */
public final class RewriteCallerCodeLocation implements CompilerPass {

  static final DiagnosticType JSC_CALLER_LOCATION_POSITION_ERROR =
      DiagnosticType.error(
          "JSC_CALLER_LOCATION_POSITION_ERROR",
          "Please make sure there is only one goog.callerLocation argument in your function's"
              + " parameter list, and it is the first optional argument in the list");

  static final DiagnosticType JSC_CALLER_LOCATION_MISUSE_ERROR =
      DiagnosticType.error(
          "JSC_CALLER_LOCATION_MISUSE_ERROR",
          "goog.callerLocation should only be used as a default parameter initializer");

  static final DiagnosticType JSC_UNDEFINED_CODE_LOCATION_ERROR =
      DiagnosticType.error(
          "JSC_UNDEFINED_CODE_LOCATION_ERROR",
          "Do not pass in undefined as an argument to goog.CodeLocation parameter");

  static final DiagnosticType JSC_ANONYMOUS_FUNCTION_CODE_LOCATION_ERROR =
      DiagnosticType.error(
          "JSC_ANONYMOUS_FUNCTION_CODE_LOCATION_ERROR",
          "Do not use goog.callerLocation in an anonymous functions. Functions that use"
              + " goog.callerLocation should be named.");

  public static final QualifiedName GOOG_CALLER_LOCATION_QUALIFIED_NAME =
      QualifiedName.of("goog.callerLocation");

  private final AbstractCompiler compiler;
  private final AstFactory astFactory;
  // Map of function name to a FunctionVarAndParamPosition object which contains:
  // 1. the function variable
  // 2. the position of the param which has goog.callerLocation as default value
  // E.g:
  // `function signal(val, here: goog.CodeLocation = goog.callerLocation()) {}`
  // callerLocationFunctionNames.put("signal", {functionVar: `Var signal @ NAME signal 1:9 ...`,
  // paramPosition: 2}); // 2 because "here" is the second param
  private final Map<String, FunctionVarAndParamPosition> callerLocationFunctionNames;

  /** Creates an instance. */
  public RewriteCallerCodeLocation(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
    this.callerLocationFunctionNames = new LinkedHashMap<>();
  }

  @Override
  public void process(Node externs, Node root) {
    // Find all functions that have goog.callerLocation as a default parameter. We want to traverse
    // the root node as well as the externs because the function that contains `goog.callerLocation`
    // parameter could be an
    // .i.js file in the externs node subtree
    NodeTraversal.traverse(compiler, compiler.getRoot(), new FindCallerLocationFunctions());
    if (!callerLocationFunctionNames.isEmpty()) {
      // Rewrite call-sites of functions that have goog.callerLocation as a default parameter. We
      // want to traverse the root node as well as the externs to ensure the scope of the call-site
      // is the same scope as the function with the `goog.callerLocation` parameter.
      NodeTraversal.traverse(
          compiler, compiler.getRoot(), new RewriteCallerLocationFunctionCalls());
    }
  }

  private class FindCallerLocationFunctions extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (isGoogCallerLocationMisused(n, parent)) {
        compiler.report(JSError.make(parent.getParent(), JSC_CALLER_LOCATION_MISUSE_ERROR));
        return;
      }

      if (n.isParamList()) {
        visitParamListAndAddCallerLocationFunctionNames(n, t);
      }
    }

    /**
     * Checks if `goog.callerLocation` is misused. `goog.callerLocation` should only be used as a
     * default parameter initializer.
     *
     * @param n node to check
     * @param parent parent node
     * @return true if goog.callerLocation is misused, false otherwise
     */
    private boolean isGoogCallerLocationMisused(Node n, Node parent) {
      if (!GOOG_CALLER_LOCATION_QUALIFIED_NAME.matches(n)) {
        // not a `goog.callerLocation` node
        return false;
      }

      // Check for misuse of goog.callerLocation.
      if (parent.isCall() && parent.getParent().isDefaultValue()) {
        if (parent.getGrandparent().isStringKey()) {
          // Throw an error when `goog.callerLocation` is used in an object literal.
          // E.g:
          // function foo({val1, val2, here = goog.callerLocation()}) {}
          // The AST for `here = goog.callerLocation()`looks like:
          // STRING_KEY here (This node tells us we are in an object literal)
          //   DEFAULT_VALUE
          //     NAME here 1:26
          //     CALL 1:33
          //       GETPROP callerLocation (This is the node `n` we are currently at)
          //         NAME goog 1:33
          return true;
        }

        // `goog.callerLocation` is used correctly as a default value in a function's parameter
        // list.
        return false;
      }

      if (parent.isAssign() && n.getNext() != null && n.getNext().isFunction()) {
        // This is the definition of goog.callerLocation, so this is not a misuse.
        // i.e: `goog.callerLocation = function(...) { ... }`
        return false;
      }

      // `goog.callerLocation` is NOT used as a default value in a function's parameter list.
      return true;
    }

    /**
     * Visits the param list of a function and checks if it contains a default value of
     * goog.callerLocation. If it does, `visitParamList` stores the function name and the index of
     * the param in `callerLocationFunctionNames` map.
     *
     * <p>Example: function signal(val, here: goog.CodeLocation = goog.callerLocation()) {}
     *
     * <p>`callerLocationFunctionNames` will have an entry for "signal" -> 2 (because "here" is the
     * second param)
     *
     * @param n param list node
     */
    private void visitParamListAndAddCallerLocationFunctionNames(Node n, NodeTraversal t) {
      checkState(n.isParamList(), n);
      // function foo(<params>) ...
      // Each item in <params> is scanned for having a default value.
      // If there exists a default value, check if it is `goog.callerLocation`.
      // Check if there is another optional argument that comes before the goog.callerLocation arg.
      int defaultValuesCount = 0;
      int paramPosition = 0; // keep track of the position of the param in the param list.
      for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
        paramPosition++;
        if (!c.isDefaultValue()) {
          continue;
        }
        defaultValuesCount++;
        Node call = c.getSecondChild(); // CALL node (E.g: goog.callerLocation())
        if (!call.isCall()) {
          continue;
        }
        Node getProp = call.getFirstChild();
        if (GOOG_CALLER_LOCATION_QUALIFIED_NAME.matches(getProp)) {
          if (defaultValuesCount > 1) {
            compiler.report(JSError.make(c, JSC_CALLER_LOCATION_POSITION_ERROR));
          }
          // E.g: function signal(val, here: goog.CodeLocation = goog.callerLocation()) {}
          // Add ("signal" -> 2) to `callerLocationFunctionNames`
          String functionName = n.getParent().getFirstChild().getQualifiedName();
          if (functionName == null) {
            // Anonymous functions are not allowed to use goog.callerLocation.
            compiler.report(
                JSError.make(
                    n.getParent().getFirstChild(), JSC_ANONYMOUS_FUNCTION_CODE_LOCATION_ERROR));
            return;
          }

          FunctionVarAndParamPosition functionVarAndPosition =
              new FunctionVarAndParamPosition(t.getScope().getVar(functionName), paramPosition);
          callerLocationFunctionNames.put(functionName, functionVarAndPosition);
        }
      }
    }
  }

  private class RewriteCallerLocationFunctionCalls extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isCall()) {
        visitCallNodeAndRewrite(n, t);
      }
    }

    /**
     * Visits call expression nodes and checks if they require transformations. If they do, it
     * rewrites the call expression node to include the code location.
     *
     * <p>E.g: `signal(0)` will be rewritten to `signal(0,
     * goog.callerLocationIdInternalDoNotCallOrElse(path/to/file.ts:lineno:charno))`
     *
     * @param n call node
     */
    private void visitCallNodeAndRewrite(Node n, NodeTraversal t) {
      Node firstChild = n.getFirstChild();
      if (firstChild == null
          || (!firstChild.isName() && !firstChild.isGetProp())
          || firstChild.getQualifiedName() == null) {
        return;
      }

      // Check if the call-site is calling a callerLocation function.
      // E.g: signal(0); name = "signal"
      String name = firstChild.getQualifiedName();

      // ClosureRewriteModule pass will run before this pass and will rename exported function names
      // and aliases in goog.modules.
      // E.g:
      // function name = module$contents$google3$javascript$apps$wiz$signals$signal_signal
      // call node name = module$exports$google3$javascript$apps$wiz$signals$signal.signal
      String moduleContentsName =
          name.replace('.', '_').replace("module$exports$", "module$contents$");

      if (!callerLocationFunctionNames.containsKey(moduleContentsName)) {
        return;
      }

      FunctionVarAndParamPosition functionVarAndPosition =
          callerLocationFunctionNames.get(moduleContentsName);

      Var callerLocationFunction = functionVarAndPosition.getFunctionVar();
      Var calleeFunction = t.getScope().getVar(moduleContentsName);
      if (callerLocationFunction == null
          || calleeFunction == null
          || !Objects.equals(callerLocationFunction.getNameNode(), calleeFunction.getNameNode())) {
        return;
      }

      // Check if the argument is provided.
      // E.g:
      // function signal(val, here: goog.CodeLocation = goog.callerLocation()) {}
      // signal(0, xid('path/to/file.ts:25')); // goog.CodeLocation is provided
      int positionOfCallerLocationArg = functionVarAndPosition.getParamPosition();
      int numberOfArgs = n.getChildCount() - 1; // -1 for the firstChild which is the function name.
      if (numberOfArgs >= positionOfCallerLocationArg) {
        // goog.CodeLocation is provided as an argument, we will not rewrite this call-site.
        // If `undefined` is passed in as an argument to goog.CodeLocation, we will throw an error.
        // Otherwise continue without rewriting.
        JSType jsType = n.getChildAtIndex(positionOfCallerLocationArg).getJSType();
        if (jsType != null && jsType.isExplicitlyVoidable()) {
          // user is passing in undefined as an argument to goog.CodeLocation
          compiler.report(JSError.make(firstChild, JSC_UNDEFINED_CODE_LOCATION_ERROR));
        }

        return;
      }

      // create the goog.callerLocationIdInternalDoNotCallOrElse(path/to/file.ts:lineno:charno) and
      // add it as the last parameter.
      Node xidCall = createGoogXidFilePathNode(n);
      compiler.reportChangeToEnclosingScope(n);
      n.addChildToBack(xidCall);
    }

    /**
     * Creates a call node for
     * "goog.callerLocationIdInternalDoNotCallOrElse(path/to/file.ts:lineno:charno)"
     *
     * @param n call node of a function that needs to be rewritten to include the code location
     * @return call node including code location i.e.
     *     "goog.callerLocationIdInternalDoNotCallOrElse(path/to/file.ts:lineno:charno)"
     */
    private Node createGoogXidFilePathNode(Node n) {
      // googNode is "goog"
      Node googNode = IR.name("goog");
      googNode.srcrefIfMissing(n);

      // googXid is "goog.callerLocationIdInternalDoNotCallOrElse" node
      Node googXid =
          astFactory.createGetPropWithUnknownType(
              googNode, "callerLocationIdInternalDoNotCallOrElse");
      googXid.srcrefIfMissing(n);

      // callNode is "goog.callerLocationIdInternalDoNotCallOrElse()" node
      Node callNode = astFactory.createCallWithUnknownType(googXid);
      callNode.srcrefIfMissing(n);

      // stringNode is "path/to/file.ts:lineno:charno" node
      Node stringNode =
          astFactory.createString(
              n.getSourceFileName() + ":" + n.getLineno() + ":" + n.getCharno());
      stringNode.srcrefIfMissing(n);

      // turns callNode from "goog.callerLocationIdInternalDoNotCallOrElse()" to
      // "goog.callerLocationIdInternalDoNotCallOrElse(path/to/file.ts:lineno:charno)"
      callNode.addChildToBack(stringNode);

      return callNode;
    }
  }

  /**
   * Class to store the function variable and the position of the param which has
   * goog.callerLocation as default value.
   *
   * <p>Since it is not guaranteed that names are unique at this point in compilation, we could have
   * something like (1) a function using goog.callerLocation that's actually nested within another
   * function, and not available globally or (2) a function using goog.callerLocation that's then
   * shadowed by another function locally. We do not want to rewrite the call-site in these cases,
   * so we'll check the scope of the call-site against the function with the `goog.callerLocation`
   * default parameter.
   *
   * <p>E.g: `function signal(val, here: goog.CodeLocation = goog.callerLocation()) {}`
   *
   * <p>This class will have:
   *
   * <p>1. functionVar: `Var signal @ NAME signal 1:9 ...`
   *
   * <p>2. paramPosition: 2 (because "here" is the second param)
   *
   * <p>Storing the functionVar is useful for checking if the call-site is calling the same function
   * as the one with the `goog.callerLocation` default parameter.
   */
  private static final class FunctionVarAndParamPosition {
    final Var functionVar;
    final int paramPosition;

    FunctionVarAndParamPosition(Var functionVar, int paramPosition) {
      this.functionVar = functionVar;
      this.paramPosition = paramPosition;
    }

    Var getFunctionVar() {
      return functionVar;
    }

    int getParamPosition() {
      return paramPosition;
    }
  }
}
