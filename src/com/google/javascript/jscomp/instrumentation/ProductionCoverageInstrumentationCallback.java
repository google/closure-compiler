/*
 * Copyright 2020 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.instrumentation;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.ImmutableMap;
import com.google.debugging.sourcemap.Base64VLQ;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.AstManipulations;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.VariableMap;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Instrument production coverage for javascript. This type of coverage will instrument different
 * levels of source code such as function and branch instrumentation. This instrumentation differs
 * from the previous implementations ({@link CoverageInstrumentationCallback} and {@link
 * BranchCoverageInstrumentationCallback}) in that it is properly optimized and obfuscated so that
 * it can be run on client browsers with the goal of better detecting dead code. The callback will
 * instrument by pushing a string onto an array which identifies what piece of code was executed.
 */
@GwtIncompatible
final class ProductionCoverageInstrumentationCallback implements NodeTraversal.Callback {

  /**
   * The name of the global array to which at every instrumentation point a new encoded param will
   * be added. This is dynamically set by the command line flag --prod_instr_array_name.
   */
  private final String instrumentationArrayName;

  private final AbstractCompiler compiler;
  private final ParameterMapping parameterMapping;

  private static final String ANONYMOUS_FUNCTION_NAME = "<Anonymous>";

  private enum Type {
    FUNCTION,
    BRANCH,
    BRANCH_DEFAULT;
  }

  /**
   * Stores a stack of function names that encapsulates the children nodes being instrumented. The
   * function name is popped off the stack when the function node, and the entire subtree rooted at
   * the function node have been visited.
   */
  private final Deque<String> functionNameStack = new ArrayDeque<>();

  public ProductionCoverageInstrumentationCallback(
      AbstractCompiler compiler, String instrumentationArrayName) {
    this.compiler = compiler;
    this.parameterMapping = new ParameterMapping();

    this.instrumentationArrayName = instrumentationArrayName;
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {

    // If origin of node is not from sourceFile, do not instrument. This typically occurs when
    // polyfill code is injected into the sourceFile AST and this check avoids instrumenting it. We
    // avoid instrumentation as this callback does not distinguish between sourceFile code and
    // injected code and can result in an error.
    if (!n.isRoot() && !Objects.equals(t.getSourceName(), n.getSourceFileName())) {
      return false;
    }

    if (n.isFunction()) {
      String fnName = NodeUtil.getBestLValueName(NodeUtil.getBestLValue(n));
      fnName = (fnName == null) ? ANONYMOUS_FUNCTION_NAME : fnName;
      functionNameStack.push(fnName);
    }

    if (functionNameStack.isEmpty()) {
      functionNameStack.push(ANONYMOUS_FUNCTION_NAME);
    }

    return true;
  }

  @Override
  public void visit(NodeTraversal traversal, Node node, Node parent) {
    String fileName = traversal.getSourceName();
    String sourceFileName = node.getSourceFileName();

    String functionName = functionNameStack.peek();

    switch (node.getToken()) {
      case FUNCTION:
        // If the function node has been visited by visit() then we can be assured that all its
        // children nodes have been visited and properly instrumented.
        functionNameStack.pop();
        instrumentBlockNode(node.getLastChild(), fileName, functionName, Type.FUNCTION);
        break;
      case IF:
        Node ifTrueNode = node.getSecondChild();
        instrumentBlockNode(ifTrueNode, sourceFileName, functionName, Type.BRANCH);
        if (node.getChildCount() == 2) {
          addElseBlock(node);
        }
        Node ifFalseNode = node.getLastChild();
        // The compiler converts all sets of If-Else if-Else blocks into a combination of nested
        // If-Else blocks. This case checks if the else blocks first child is an If statement, and
        // if it is it will not instrument. This avoids adding multiple instrumentation calls.
        // Since we also make sure an Else case is added to every If statement, we are still
        // assured that the else statement is being reached through a later instrumentation call.
        if (NodeUtil.isEmptyBlock(ifFalseNode)
            || (ifFalseNode.hasChildren() && !ifFalseNode.getFirstChild().isIf())) {
          instrumentBlockNode(ifFalseNode, sourceFileName, functionName, Type.BRANCH_DEFAULT);
        }
        break;
      case SWITCH:
        boolean hasDefaultCase = false;
        for (Node c = node.getSecondChild(); c != null; c = c.getNext()) {
          if (c.isDefaultCase()) {
            instrumentBlockNode(
                c.getLastChild(), sourceFileName, functionName, Type.BRANCH_DEFAULT);
            hasDefaultCase = true;
          } else {
            instrumentBlockNode(c.getLastChild(), sourceFileName, functionName, Type.BRANCH);
          }
        }
        if (!hasDefaultCase) {
          Node defaultBlock = IR.block();
          defaultBlock.srcrefTreeIfMissing(node);
          Node defaultCase = IR.defaultCase(defaultBlock).srcrefTreeIfMissing(node);
          node.addChildToBack(defaultCase);
          instrumentBlockNode(defaultBlock, sourceFileName, functionName, Type.BRANCH_DEFAULT);
        }
        break;
      case HOOK:
        Node ifTernaryIsTrueExpression = node.getSecondChild();
        Node ifTernaryIsFalseExpression = node.getLastChild();

        addInstrumentationNodeWithComma(
            ifTernaryIsTrueExpression, sourceFileName, functionName, Type.BRANCH);
        addInstrumentationNodeWithComma(
            ifTernaryIsFalseExpression, sourceFileName, functionName, Type.BRANCH);

        compiler.reportChangeToEnclosingScope(node);
        break;
      case OR:
      case AND:
      case COALESCE:
        // Only instrument the second child of the binary operation because the first child will
        // always execute, or the first child is part of a chain of binary operations and would have
        // already been instrumented.
        Node secondExpression = node.getLastChild();
        addInstrumentationNodeWithComma(
            secondExpression, sourceFileName, functionName, Type.BRANCH);

        compiler.reportChangeToEnclosingScope(node);
        break;
      default:
        if (NodeUtil.isLoopStructure(node)) {
          Node blockNode = NodeUtil.getLoopCodeBlock(node);
          checkNotNull(blockNode);
          instrumentBlockNode(blockNode, sourceFileName, functionName, Type.BRANCH);
        }
    }
  }

  /**
   * Given a node, this function will create a new instrumentationNode and combine it with the
   * original node using a COMMA node.
   */
  private void addInstrumentationNodeWithComma(
      Node originalNode, String fileName, String functionName, Type type) {
    Node cloneOfOriginal = originalNode.cloneTree();
    Node newInstrumentationNode =
        newInstrumentationNode(cloneOfOriginal, fileName, functionName, type);

    Node childOfInstrumentationNode = newInstrumentationNode.removeFirstChild();
    Node infusedExp = AstManipulations.fuseExpressions(childOfInstrumentationNode, cloneOfOriginal);
    originalNode.replaceWith(infusedExp);
  }

  /**
   * Consumes a block node and adds a new child to the front of the block node which is the
   * instrumentation Node
   *
   * @param block The block node to be instrumented.
   * @param fileName The file name of the node being instrumented.
   * @param fnName The function name of the node being instrumented.
   * @param type The type of the node being instrumented.
   */
  private void instrumentBlockNode(Node block, String fileName, String fnName, Type type) {
    block.addChildToFront(newInstrumentationNode(block, fileName, fnName, type));
    compiler.reportChangeToEnclosingScope(block);
  }

  /**
   * Create a function call to the Instrument Code function with properly encoded parameters. The
   * instrumented function call will be of the following form: instrumentationArrayName.push(param).
   * Where instrumentationArrayName is the name of the global array and param is the encoded param
   * which will be pushed onto the array.
   *
   * @param node The node to be instrumented.
   * @param fileName The file name of the node being instrumented.
   * @param fnName The function name of the node being instrumented.
   * @param type The type of the node being instrumented.
   * @return The newly constructed function call node.
   */
  private Node newInstrumentationNode(Node node, String fileName, String fnName, Type type) {

    int lineNo = node.getLineno();
    int columnNo = node.getCharno();

    if (node.isBlock()) {
      lineNo = node.getParent().getLineno();
      columnNo = node.getParent().getCharno();
    }

    String encodedParam =
        parameterMapping.getEncodedParam(fileName, fnName, type, lineNo, columnNo);

    Node prop = IR.getprop(IR.name(instrumentationArrayName), "push");
    Node functionCall = IR.call(prop, IR.string(encodedParam));
    Node exprNode = IR.exprResult(functionCall);

    return exprNode.srcrefTreeIfMissing(node);
  }

  /** Add an else block for If statements if one is not already present. */
  private Node addElseBlock(Node node) {
    Node defaultBlock = IR.block();
    node.addChildToBack(defaultBlock);
    return defaultBlock.srcrefTreeIfMissing(node);
  }

  public VariableMap getInstrumentationMapping() {
    return parameterMapping.getParamMappingAsVariableMap();
  }

  /**
   * A class the maintains a mapping of unique identifiers to parameter values. It also generates
   * unique identifiers by creating a counter starting form 0 and increments this value when
   * assigning a new unique identifier. It converts the mapping to a VariableMap as well so that it
   * can later be saved as a file.
   */
  private static final class ParameterMapping {

    // Values are stored as a mapping of the String of the encoded array indices to a String of the
    // encoded uniqueIdentifier. This is so we can check if an encoded param has already been
    // defined so that we do not create a duplicate. (Ex. Key: ACA (Base64 VLQ encoding of [0,1,0]),
    // Value: C (Base64 VLQ encoding of the uniqueIdentifier). This map will later be inversed so
    // that it is printed in the following form: C:ACA.
    private final Map<String, String> paramValueEncodings;

    // Values are stored as a mapping of String to Integers so that we can lookup the index of the
    // encoded (file|function|type) name and also check if it is present in constant time. These
    // mappings are added to the VariableMap once instrumentation is complete.
    private final Map<String, Integer> fileNameToIndex;
    private final Map<String, Integer> functionNameToIndex;
    private final Map<String, Integer> typeToIndex;

    private long nextUniqueIdentifier;

    ParameterMapping() {
      nextUniqueIdentifier = 0;

      paramValueEncodings = new HashMap<>();

      // A LinkedHashMap is used so that when keys are printed, keySet() will obtain them in the
      // insertion order which corroborates to the index. This helps to avoid the need of sorting
      // by the Integer value and is more convenient.
      fileNameToIndex = new LinkedHashMap<>();
      functionNameToIndex = new LinkedHashMap<>();
      typeToIndex = new LinkedHashMap<>();
    }

    private String getEncodedParam(
        String fileName, String functionName, Type type, int lineNo, int colNo) {

      fileNameToIndex.putIfAbsent(fileName, fileNameToIndex.size());
      functionNameToIndex.putIfAbsent(functionName, functionNameToIndex.size());
      typeToIndex.putIfAbsent(type.name(), typeToIndex.size());

      StringBuilder sb = new StringBuilder();

      try {
        Base64VLQ.encode(sb, fileNameToIndex.get(fileName));
        Base64VLQ.encode(sb, functionNameToIndex.get(functionName));
        Base64VLQ.encode(sb, typeToIndex.get(type.name()));
        Base64VLQ.encode(sb, lineNo);
        Base64VLQ.encode(sb, colNo);
      } catch (IOException e) {
        throw new AssertionError(e);
      }

      String encodedParam = sb.toString();

      if (!paramValueEncodings.containsKey(encodedParam)) {
        long uniqueIdentifier = generateUniqueIdentifier();
        if (uniqueIdentifier > Integer.MAX_VALUE) {
          throw new ArithmeticException(
              "Unique Identifier exceeds value of Integer.MAX_VALUE, could not encode with Base 64"
                  + " VLQ");
        }

        sb = new StringBuilder();

        try {
          Base64VLQ.encode(sb, Math.toIntExact(uniqueIdentifier));
        } catch (IOException e) {
          throw new AssertionError(e);
        }

        paramValueEncodings.put(encodedParam, sb.toString());
      }

      return paramValueEncodings.get(encodedParam);
    }

    private long generateUniqueIdentifier() {
      nextUniqueIdentifier++;
      return nextUniqueIdentifier;
    }

    private VariableMap getParamMappingAsVariableMap() {
      Gson gson = new GsonBuilder().disableHtmlEscaping().create();

      // Array names are given a " " (space) prefix since when writing to file, VariableMap.java
      // sorts the map by key values. This space will place the arrays at the top of the file.
      // The key and value entry are put in this order because the map will be inversed.
      paramValueEncodings.put(gson.toJson(fileNameToIndex.keySet()), " FileNames");
      paramValueEncodings.put(gson.toJson(functionNameToIndex.keySet()), " FunctionNames");
      paramValueEncodings.put(gson.toJson(typeToIndex.keySet()), " Types");

      VariableMap preInversedMap = new VariableMap(paramValueEncodings);
      ImmutableMap<String, String> inversedMap = preInversedMap.getNewNameToOriginalNameMap();
      return new VariableMap(inversedMap);
    }
  }
}
