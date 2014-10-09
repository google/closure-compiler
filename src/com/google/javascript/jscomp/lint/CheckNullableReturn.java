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
package com.google.javascript.jscomp.lint;

import com.google.common.base.Predicate;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CheckPathsBetweenNodes;
import com.google.javascript.jscomp.ControlFlowGraph;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.HotSwapCompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.TernaryValue;

/**
 * Checks when a function is annotated as returning {SomeType} (nullable)
 * but actually always returns {!SomeType}, i.e. never returns null.
 *
 */
public class CheckNullableReturn implements HotSwapCompilerPass, NodeTraversal.Callback {
  final AbstractCompiler compiler;

  public static final DiagnosticType NULLABLE_RETURN =
      DiagnosticType.disabled(
          "JSC_NULLABLE_RETURN",
          "This function''s return type is nullable, but it always returns a "
          + "non-null value. Consider making the return type non-nullable.");

  public static final DiagnosticType NULLABLE_RETURN_WITH_NAME =
      DiagnosticType.disabled(
          "JSC_NULLABLE_RETURN_WITH_NAME",
          "The return type of the function \"{0}\" is nullable, but it always "
          + "returns a non-null value. Consider making the return type "
          + "non-nullable.");

  // Skips impossible edges.
  // Based on CheckMissingReturn.GOES_THROUGH_TRUE_CONDITION_PREDICATE
  private static final Predicate<DiGraphEdge<Node, ControlFlowGraph.Branch>>
      GOES_THROUGH_TRUE_CONDITION_PREDICATE =
        new Predicate<DiGraphEdge<Node, ControlFlowGraph.Branch>>() {
    @Override
    public boolean apply(DiGraphEdge<Node, ControlFlowGraph.Branch> input) {
      Branch branch = input.getValue();
      if (branch.isConditional()) {
        Node condition = NodeUtil.getConditionExpression(
            input.getSource().getValue());
        // TODO(user): We CAN make this bit smarter just looking at
        // constants. We DO have a full blown ReverseAbstractInterupter and
        // type system that can evaluate some impressions' boolean value but
        // for now we will keep this pass lightweight.
        if (condition != null) {
          TernaryValue val = NodeUtil.getImpureBooleanValue(condition);
          if (val != TernaryValue.UNKNOWN) {
            return val.toBoolean(true) == (Branch.ON_TRUE == branch);
          }
        }
      }
      return true;
    }
  };

  public CheckNullableReturn(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    // Do the checks when 'n' is the block node and 'parent' is the function
    // node, so that getControlFlowGraph will return the graph inside
    // the function, rather than the graph of the enclosing scope.
    if (n.isBlock() && n.hasChildren() && isReturnTypeNullable(parent)
        && !hasSingleThrow(n) && !isSuperMethodOverride(parent)
        && !canReturnNull(t.getControlFlowGraph())) {
      String fnName = NodeUtil.getNearestFunctionName(parent);
      if (fnName != null && !fnName.isEmpty()) {
        compiler.report(t.makeError(parent, NULLABLE_RETURN_WITH_NAME, fnName));
      } else {
        compiler.report(t.makeError(parent, NULLABLE_RETURN));
      }
    }
  }

  private static boolean isSuperMethodOverride(Node function) {
    String functionName = NodeUtil.getNearestFunctionName(function);
    if (functionName == null) {
      return false;
    }
    FunctionType functionType = function.getJSType().toMaybeFunctionType();
    JSType thisType = functionType.getTypeOfThis();
    if (thisType == null) {
      return false;
    }
    ObjectType objectType = thisType.toObjectType();
    if (objectType == null) {
      return false;
    }
    FunctionType ctorType = objectType.getConstructor();
    if (ctorType == null) {
      return false;
    }
    for (ObjectType interfaceType : ctorType.getAllImplementedInterfaces()) {
      JSType superMethodType = interfaceType.findPropertyType(functionName);
      if (superMethodType != null) {
        // We do not check the actual property type here assuming it is a function, too.
        return true;
      }
    }
    return protoChainHasProperty(functionName, ctorType);
  }

  private static boolean protoChainHasProperty(String functionName, FunctionType ctorType) {
    FunctionType superClassCtor = ctorType.getSuperClassConstructor();
    if (superClassCtor == null) {
      return false;
    }

    JSType extendedType = superClassCtor.getTypeOfThis();
    if (extendedType != null && extendedType.findPropertyType(functionName) != null) {
      // We do not check the actual property type here assuming it is a function, too.
      return true;
    }
    return protoChainHasProperty(functionName, superClassCtor);
  }

  /**
   * @return whether the blockNode contains only a single "throw" child node.
   */
  private static boolean hasSingleThrow(Node blockNode) {
    if (blockNode.getChildCount() == 1
        && blockNode.getFirstChild().getType() == Token.THROW) {
      // Functions consisting of a single "throw FOO" can be actually abstract,
      // so do not check their return type nullability.
      return true;
    }

    return false;
  }

  /**
   * @return True if n is a function node which is explicitly annotated
   * as returning a nullable type, other than {?}.
   */
  private static boolean isReturnTypeNullable(Node n) {
    if (n == null) {
      return false;
    }
    if (!n.isFunction()) {
      return false;
    }
    FunctionType functionType = n.getJSType().toMaybeFunctionType();
    JSType returnType = functionType.getReturnType();
    if (returnType == null
        || returnType.isUnknownType() || !returnType.isNullable()) {
      return false;
    }
    JSDocInfo info = NodeUtil.getBestJSDocInfo(n);
    return info != null && info.hasReturnType();
  }

  /**
   * @return True if the given ControlFlowGraph could return null.
   */
  private static boolean canReturnNull(ControlFlowGraph<Node> graph) {

    Predicate<Node> nullableReturn = new Predicate<Node>() {
      @Override
      public boolean apply(Node input) {
        // Check for null because the control flow graph's implicit return node is
        // represented by null, so this value might be input.
        if (input == null || !input.isReturn()) {
          return false;
        }
        Node returnValue = input.getFirstChild();
        return returnValue != null && isNullable(returnValue);
      }
    };

    CheckPathsBetweenNodes<Node, ControlFlowGraph.Branch> test =
        new CheckPathsBetweenNodes<>(
            graph,
            graph.getEntry(),
            graph.getImplicitReturn(),
            nullableReturn, GOES_THROUGH_TRUE_CONDITION_PREDICATE);

    return test.somePathsSatisfyPredicate();
  }

  /**
   * @return True if the node represents a nullable value. Essentially, this
   *     is just n.getJSType().isNullable(), but for purposes of this pass,
   *     the expression {@code x || null} is considered nullable even if
   *     x is always truthy. This often happens with expressions like
   *     {@code arr[i] || null}: The compiler doesn't know that arr[i] can
   *     be undefined.
   */
  private static boolean isNullable(Node n) {
    return n.getJSType().isNullable()
        || (n.isOr() && n.getLastChild().isNull());
  }

  @Override
  public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
    return true;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, originalRoot, this);
  }
}
