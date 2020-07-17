package com.google.javascript.jscomp;

import com.google.common.annotations.GwtIncompatible;
import com.google.debugging.sourcemap.Base64VLQ;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


/**
 * Instrument advanced coverage for javascript.
 */
@GwtIncompatible("AdvancedFileInstrumentationData")
public class AdvancedCoverageInstrumentationCallback extends
    NodeTraversal.AbstractPostOrderCallback {

  private static final String INSTRUMENT_CODE_FUNCTION_NAME = "instrumentCode";
  private static final String INSTRUMENT_CODE_FILE_NAME = "InstrumentCode.js";
  private final AbstractCompiler compiler;
  private final ParameterMapping parameterMapping;
  private final Map<String, AdvancedFileInstrumentationData> instrumentationData;
  private String functionName = "Anonymous";

  public AdvancedCoverageInstrumentationCallback(
      AbstractCompiler compiler, Map<String, AdvancedFileInstrumentationData> instrumentationData) {
    this.compiler = compiler;
    this.instrumentationData = instrumentationData;
    this.parameterMapping = new ParameterMapping();
  }

  @Override
  public void visit(NodeTraversal traversal, Node node, Node parent) {
    String fileName = traversal.getSourceName();
    String sourceFileName = node.getSourceFileName();

    // If origin of node is not from sourceFile, do not instrument. This typically occurs when
    // polyfill code is injected into the sourceFile AST and this check avoids instrumenting it. We
    // avoid instrumentation as this callback does not distinguish between sourceFile code and
    // injected code and can result in an error.
    if (!Objects.equals(fileName, sourceFileName)) {
      return;
    }

    // If Source File is base.js or INSTRUMENT_CODE_FILE_NAME, do not instrument as the instrument
    // function has not yet been defined when base.js will be executing and the implementation file
    // can not call the Code instrumentation function on itself
    if (sourceFileName.endsWith("base.js") || sourceFileName.endsWith(INSTRUMENT_CODE_FILE_NAME)) {
      return;
    }

    if (node.isScript()) {
      if (instrumentationData.get(fileName) != null) {
        compiler.reportChangeToEnclosingScope(node);
        instrumentCode(traversal, instrumentationData.get(fileName));
      }
    }

    if (node.isFunction()) {

      functionName = NodeUtil.getBestLValueName(NodeUtil.getBestLValue(node));

      if (!instrumentationData.containsKey(fileName)) {
        instrumentationData.put(fileName, new AdvancedFileInstrumentationData(fileName));
      }
      addBlockNode(instrumentationData.get(fileName), Arrays.asList(node.getLastChild()),
          functionName);

    }
  }

  /**
   * Iterate over all collected block nodes within a Script node and add a new child to the front of
   * each block node which is the instrumentation Node
   *
   * @param traversal Tne node traversal context which maintains information such as fileName being
   *                  traversed
   * @param data      Data structure that maintains an organized list of block nodes that need to be
   *                  instrumented
   */
  private void instrumentCode(NodeTraversal traversal, AdvancedFileInstrumentationData data) {
    Map<String, List<Node>> functionScopedNodes = data.getFunctionScopeNodes();
    for (String key : functionScopedNodes.keySet()) {
      for (Node block : functionScopedNodes.get(key)) {
        block.addChildToFront(
            newInstrumentationNode(traversal, block, key));
        compiler.reportChangeToEnclosingScope(block);
      }
    }
  }

  /**
   * Create a function call to the Instrument Code function with properly encoded parameters.
   *
   * @param traversal The context of the current traversal.
   * @param node      The block node to be instrumented.
   * @param fnName    The function name that the node exists within.
   * @return The newly constructed function call node.
   */
  private Node newInstrumentationNode(NodeTraversal traversal, Node node, String fnName) {

    String type = "Type.FUNCTION";
    String combinedParam = traversal.getSourceName() + " " + fnName + " " + type;

    BigInteger uniqueIdentifier = parameterMapping.getUniqueIdentifier(combinedParam);
    BigInteger maxInteger = new BigInteger(Integer.toString(Integer.MAX_VALUE));

    if (uniqueIdentifier.compareTo(maxInteger) > 0) {
      throw new ArithmeticException(
          "Unique Identifier exceeds value of Integer.MAX_VALUE, could not encode with Base 64 VLQ");
    }

    StringBuilder sb = new StringBuilder();

    try {
      Base64VLQ.encode(sb, uniqueIdentifier.intValue());
    } catch (IOException e) {
      // If encoding in Base64 VLQ fails, we will use the original identifier as it will still
      // maintain obfuscation and partial optimization
      sb.append(uniqueIdentifier.intValue());
    }

    parameterMapping.addParamMapping(sb.toString(), combinedParam);

    Node inner_prop = IR
        .getprop(IR.name("module$exports$instrument$code"), IR.string("instrumentCodeInstance"));
    Node outer_prop = IR.getprop(inner_prop, IR.string(INSTRUMENT_CODE_FUNCTION_NAME));
    Node functionCall = IR.call(outer_prop, IR.string(sb.toString()), IR.number(node.getLineno()));
    Node exprNode = IR.exprResult(functionCall);

    return exprNode.useSourceInfoIfMissingFromForTree(node);
  }

  private void addBlockNode(AdvancedFileInstrumentationData instrumentationData, List<Node> blocks,
      String functionScope) {
    for (Node block : blocks) {
      instrumentationData.addNode(functionScope, block);
    }
  }

  /**
   * A class the maintains a mapping of unique identifiers to parameter values. It also generates
   * unique identifiers by creating a counter starting form 0 and increments this value when
   * assigning a new unique identifier.
   */
  private static final class ParameterMapping {

    private final List<String> uniqueIdentifier;
    private final List<String> paramValue;
    private BigInteger nextUniqueIdentifier;

    ParameterMapping() {
      nextUniqueIdentifier = new BigInteger("0");
      uniqueIdentifier = new ArrayList<>();
      paramValue = new ArrayList<>();
    }

    public BigInteger getUniqueIdentifier(String param) {

      nextUniqueIdentifier = nextUniqueIdentifier.add(new BigInteger("1"));
      return nextUniqueIdentifier;
    }

    public void addParamMapping(String identifier, String param) {
      uniqueIdentifier.add(identifier);
      paramValue.add(param);
    }

  }

  /**
   * A class that maintains a list of blocks that are to be instrumented and organises these block
   * nodes by the function name and what file they are enclosed within.
   */
  private static final class AdvancedFileInstrumentationData {

    private final String fileName;
    private final Map<String, List<Node>> functionScopedNodes;

    AdvancedFileInstrumentationData(String fileName) {
      this.fileName = fileName;
      this.functionScopedNodes = new HashMap<>();
    }

    void addNode(String functionScope, Node node) {
      if (!functionScopedNodes.containsKey(functionScope)) {
        functionScopedNodes.put(functionScope, new ArrayList<>(Arrays.asList(node)));
      } else {
        functionScopedNodes.get(functionScope).add(node);
      }
    }

    Map<String, List<Node>> getFunctionScopeNodes() {
      return functionScopedNodes;
    }

  }

}
