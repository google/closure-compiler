/*
 * Copyright 2011 The Closure Compiler Authors.
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

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;

/**
 * Process goog.tweak primitives. Checks that:
 * <ul>
 * <li>parameters to goog.tweak.register* are literals of the correct type.
 * <li>the parameter to goog.tweak.get* is a string literal.
 * <li>parameters to goog.tweak.overrideDefaultValue are literals of the correct
 *     type.
 * <li>tweak IDs passed to goog.tweak.get* and goog.tweak.overrideDefaultValue
 *     correspond to registered tweaks.
 * <li>all calls to goog.tweak.register* and goog.tweak.overrideDefaultValue are
 *     within the top-level context.
 * <li>each tweak is registered only once.
 * <li>calls to goog.tweak.overrideDefaultValue occur before the call to the
 *     corresponding goog.tweak.register* function.
 * </ul>
 * @author agrieve@google.com (Andrew Grieve)
 */
class ProcessTweaks implements CompilerPass {

  private final AbstractCompiler compiler;
  private final boolean stripTweaks;
  private final SortedMap<String, Node> compilerDefaultValueOverrides;

  private static final CharMatcher ID_MATCHER = CharMatcher.inRange('a', 'z').
      or(CharMatcher.inRange('A', 'Z')).or(CharMatcher.anyOf("0123456789_."));

  // Warnings and Errors.
  static final DiagnosticType UNKNOWN_TWEAK_WARNING =
      DiagnosticType.warning(
          "JSC_UNKNOWN_TWEAK_WARNING",
          "no tweak registered with ID {0}");

  static final DiagnosticType TWEAK_MULTIPLY_REGISTERED_ERROR =
      DiagnosticType.error(
          "JSC_TWEAK_MULTIPLY_REGISTERED_ERROR",
          "Tweak {0} has already been registered.");

  static final DiagnosticType NON_LITERAL_TWEAK_ID_ERROR =
      DiagnosticType.error(
          "JSC_NON_LITERAL_TWEAK_ID_ERROR",
          "tweak ID must be a string literal");

  static final DiagnosticType INVALID_TWEAK_DEFAULT_VALUE_WARNING =
      DiagnosticType.warning(
          "JSC_INVALID_TWEAK_DEFAULT_VALUE_WARNING",
          "tweak {0} registered with {1} must have a default value that is a " +
          "literal of type {2}");

  static final DiagnosticType NON_GLOBAL_TWEAK_INIT_ERROR =
      DiagnosticType.error(
          "JSC_NON_GLOBAL_TWEAK_INIT_ERROR",
          "tweak declaration {0} must occur in the global scope");

  static final DiagnosticType TWEAK_OVERRIDE_AFTER_REGISTERED_ERROR =
      DiagnosticType.error(
          "JSC_TWEAK_OVERRIDE_AFTER_REGISTERED_ERROR",
          "Cannot override the default value of tweak {0} after it has been " +
          "registered");

  static final DiagnosticType TWEAK_WRONG_GETTER_TYPE_WARNING =
      DiagnosticType.warning(
          "JSC_TWEAK_WRONG_GETTER_TYPE_WARNING",
          "tweak getter function {0} used for tweak registered using {1}");

  static final DiagnosticType INVALID_TWEAK_ID_ERROR =
      DiagnosticType.error(
          "JSC_INVALID_TWEAK_ID_ERROR",
          "tweak ID contains illegal characters. Only letters, numbers, _ " +
          "and . are allowed");

  /**
   * An enum of goog.tweak functions.
   */
  private static enum TweakFunction {
    REGISTER_BOOLEAN("goog.tweak.registerBoolean", "boolean", Token.TRUE,
        Token.FALSE),
    REGISTER_NUMBER("goog.tweak.registerNumber", "number", Token.NUMBER),
    REGISTER_STRING("goog.tweak.registerString", "string", Token.STRING),
    OVERRIDE_DEFAULT_VALUE("goog.tweak.overrideDefaultValue"),
    GET_COMPILER_OVERRIDES("goog.tweak.getCompilerOverrides_"),
    GET_BOOLEAN("goog.tweak.getBoolean", REGISTER_BOOLEAN),
    GET_NUMBER("goog.tweak.getNumber", REGISTER_NUMBER),
    GET_STRING("goog.tweak.getString", REGISTER_STRING);

    final String name;
    final String expectedTypeName;
    final int validNodeTypeA;
    final int validNodeTypeB;
    final TweakFunction registerFunction;

    TweakFunction(String name) {
      this(name, null, Token.ERROR, Token.ERROR, null);
    }

    TweakFunction(String name, String expectedTypeName,
        int validNodeTypeA) {
      this(name, expectedTypeName, validNodeTypeA, Token.ERROR, null);
    }

    TweakFunction(String name, String expectedTypeName,
        int validNodeTypeA, int validNodeTypeB) {
      this(name, expectedTypeName, validNodeTypeA, validNodeTypeB, null);
    }

    TweakFunction(String name, TweakFunction registerFunction) {
      this(name, null, Token.ERROR, Token.ERROR, registerFunction);
    }

    TweakFunction(String name, String expectedTypeName,
        int validNodeTypeA, int validNodeTypeB,
        TweakFunction registerFunction) {
      this.name = name;
      this.expectedTypeName = expectedTypeName;
      this.validNodeTypeA = validNodeTypeA;
      this.validNodeTypeB = validNodeTypeB;
      this.registerFunction = registerFunction;
    }

    boolean isValidNodeType(int type) {
      return type == validNodeTypeA || type == validNodeTypeB;
    }

    boolean isCorrectRegisterFunction(TweakFunction registerFunction) {
      Preconditions.checkNotNull(registerFunction);
      return this.registerFunction == registerFunction;
    }

    boolean isGetterFunction() {
      return registerFunction != null;
    }

    String getName() {
      return name;
    }

    String getExpectedTypeName() {
      return expectedTypeName;
    }

    Node createDefaultValueNode() {
      switch (this) {
        case REGISTER_BOOLEAN:
          return IR.falseNode();
        case REGISTER_NUMBER:
          return IR.number(0);
        case REGISTER_STRING:
          return IR.string("");
        default:
          throw new IllegalStateException();
      }
    }
  }

  // A map of function name -> TweakFunction.
  private static final Map<String, TweakFunction> TWEAK_FUNCTIONS_MAP;
  static {
    TWEAK_FUNCTIONS_MAP = Maps.newHashMap();
    for (TweakFunction func : TweakFunction.values()) {
      TWEAK_FUNCTIONS_MAP.put(func.getName(), func);
    }
  }

  ProcessTweaks(AbstractCompiler compiler, boolean stripTweaks,
      Map<String, Node> compilerDefaultValueOverrides) {
    this.compiler = compiler;
    this.stripTweaks = stripTweaks;
    // Having the map sorted is required for the unit tests to be deterministic.
    this.compilerDefaultValueOverrides = Maps.newTreeMap();
    this.compilerDefaultValueOverrides.putAll(compilerDefaultValueOverrides);
  }

  @Override
  public void process(Node externs, Node root) {
    CollectTweaksResult result = collectTweaks(root);
    applyCompilerDefaultValueOverrides(result.tweakInfos);

    boolean changed = false;

    if (stripTweaks) {
      changed = stripAllCalls(result.tweakInfos);
    } else if (!compilerDefaultValueOverrides.isEmpty()) {
      changed = replaceGetCompilerOverridesCalls(result.getOverridesCalls);
    }
    if (changed) {
      compiler.reportCodeChange();
    }
  }

  /**
   * Passes the compiler default value overrides to the JS by replacing calls
   * to goog.tweak.getCompilerOverrids_ with a map of tweak ID->default value;
   */
  private boolean replaceGetCompilerOverridesCalls(
      List<TweakFunctionCall> calls) {
    for (TweakFunctionCall call : calls) {
      Node callNode = call.callNode;
      Node objNode = createCompilerDefaultValueOverridesVarNode(callNode);
      callNode.getParent().replaceChild(callNode, objNode);
    }
    return !calls.isEmpty();
  }

  /**
   * Removes all CALL nodes in the given TweakInfos, replacing calls to getter
   * functions with the tweak's default value.
   */
  private boolean stripAllCalls(Map<String, TweakInfo> tweakInfos) {
    for (TweakInfo tweakInfo : tweakInfos.values()) {
      boolean isRegistered = tweakInfo.isRegistered();
      for (TweakFunctionCall functionCall : tweakInfo.functionCalls) {
        Node callNode = functionCall.callNode;
        Node parent = callNode.getParent();
        if (functionCall.tweakFunc.isGetterFunction()) {
          Node newValue;
          if (isRegistered) {
            newValue = tweakInfo.getDefaultValueNode().cloneNode();
          } else {
            // When we find a getter of an unregistered tweak, there has
            // already been a warning about it, so now just use a default
            // value when stripping.
            TweakFunction registerFunction =
                functionCall.tweakFunc.registerFunction;
            newValue = registerFunction.createDefaultValueNode();
          }
          parent.replaceChild(callNode, newValue);
        } else {
          Node voidZeroNode = IR.voidNode(IR.number(0).srcref(callNode))
              .srcref(callNode);
          parent.replaceChild(callNode, voidZeroNode);
        }
      }
    }
    return !tweakInfos.isEmpty();
  }

  /**
   * Creates a JS object that holds a map of tweakId -> default value override.
   */
  private Node createCompilerDefaultValueOverridesVarNode(
      Node sourceInformationNode) {
    Node objNode = IR.objectlit().srcref(sourceInformationNode);
    for (Entry<String, Node> entry : compilerDefaultValueOverrides.entrySet()) {
      Node objKeyNode = IR.stringKey(entry.getKey())
          .copyInformationFrom(sourceInformationNode);
      Node objValueNode = entry.getValue().cloneNode()
          .copyInformationFrom(sourceInformationNode);
      objKeyNode.addChildToBack(objValueNode);
      objNode.addChildToBack(objKeyNode);
    }
    return objNode;
  }

  /** Sets the default values of tweaks based on compiler options. */
  private void applyCompilerDefaultValueOverrides(
      Map<String, TweakInfo> tweakInfos) {
    for (Entry<String, Node> entry : compilerDefaultValueOverrides.entrySet()) {
      String tweakId = entry.getKey();
      TweakInfo tweakInfo = tweakInfos.get(tweakId);
      if (tweakInfo == null) {
        compiler.report(JSError.make(UNKNOWN_TWEAK_WARNING, tweakId));
      } else {
        TweakFunction registerFunc = tweakInfo.registerCall.tweakFunc;
        Node value = entry.getValue();
        if (!registerFunc.isValidNodeType(value.getType())) {
          compiler.report(JSError.make(INVALID_TWEAK_DEFAULT_VALUE_WARNING,
              tweakId, registerFunc.getName(),
              registerFunc.getExpectedTypeName()));
        } else {
          tweakInfo.defaultValueNode = value;
        }
      }
    }
  }

  /**
   * Finds all calls to goog.tweak functions and emits warnings/errors if any
   * of the calls have issues.
   * @return A map of {@link TweakInfo} structures, keyed by tweak ID.
   */
  private CollectTweaksResult collectTweaks(Node root) {
    CollectTweaks pass = new CollectTweaks();
    NodeTraversal.traverse(compiler, root, pass);

    Map<String, TweakInfo> tweakInfos = pass.allTweaks;
    for (TweakInfo tweakInfo : tweakInfos.values()) {
      tweakInfo.emitAllWarnings();
    }
    return new CollectTweaksResult(tweakInfos, pass.getOverridesCalls);
  }

  private static final class CollectTweaksResult {
    final Map<String, TweakInfo> tweakInfos;
    final List<TweakFunctionCall> getOverridesCalls;

    CollectTweaksResult(Map<String, TweakInfo> tweakInfos,
        List<TweakFunctionCall> getOverridesCalls) {
      this.tweakInfos = tweakInfos;
      this.getOverridesCalls = getOverridesCalls;
    }
  }

  /**
   * Processes all calls to goog.tweak functions.
   */
  private final class CollectTweaks extends AbstractPostOrderCallback {
    final Map<String, TweakInfo> allTweaks = Maps.newHashMap();
    final List<TweakFunctionCall> getOverridesCalls = Lists.newArrayList();

    @SuppressWarnings("incomplete-switch")
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!n.isCall()) {
        return;
      }

      String callName = n.getFirstChild().getQualifiedName();
      TweakFunction tweakFunc = TWEAK_FUNCTIONS_MAP.get(callName);
      if (tweakFunc == null) {
        return;
      }

      if (tweakFunc == TweakFunction.GET_COMPILER_OVERRIDES) {
        getOverridesCalls.add(
            new TweakFunctionCall(tweakFunc, n));
        return;
      }

      // Ensure the first parameter (the tweak ID) is a string literal.
      Node tweakIdNode = n.getFirstChild().getNext();
      if (!tweakIdNode.isString()) {
        compiler.report(t.makeError(tweakIdNode, NON_LITERAL_TWEAK_ID_ERROR));
        return;
      }
      String tweakId = tweakIdNode.getString();

      // Make sure there is a TweakInfo structure for it.
      TweakInfo tweakInfo = allTweaks.get(tweakId);
      if (tweakInfo == null) {
        tweakInfo = new TweakInfo(tweakId);
        allTweaks.put(tweakId, tweakInfo);
      }

      switch (tweakFunc) {
        case REGISTER_BOOLEAN:
        case REGISTER_NUMBER:
        case REGISTER_STRING:
          // Ensure the ID contains only valid characters.
          if (!ID_MATCHER.matchesAllOf(tweakId)) {
            compiler.report(t.makeError(tweakIdNode, INVALID_TWEAK_ID_ERROR));
          }

          // Ensure tweaks are registered in the global scope.
          if (!t.inGlobalScope()) {
            compiler.report(
                t.makeError(n, NON_GLOBAL_TWEAK_INIT_ERROR, tweakId));
            break;
          }

          // Ensure tweaks are registered only once.
          if (tweakInfo.isRegistered()) {
            compiler.report(
                t.makeError(n, TWEAK_MULTIPLY_REGISTERED_ERROR, tweakId));
            break;
          }

          Node tweakDefaultValueNode = tweakIdNode.getNext().getNext();
          tweakInfo.addRegisterCall(t.getSourceName(), tweakFunc, n,
              tweakDefaultValueNode);
          break;
        case OVERRIDE_DEFAULT_VALUE:
          // Ensure tweaks overrides occur in the global scope.
          if (!t.inGlobalScope()) {
            compiler.report(
                t.makeError(n, NON_GLOBAL_TWEAK_INIT_ERROR, tweakId));
            break;
          }
          // Ensure tweak overrides occur before the tweak is registered.
          if (tweakInfo.isRegistered()) {
            compiler.report(
                t.makeError(n, TWEAK_OVERRIDE_AFTER_REGISTERED_ERROR, tweakId));
            break;
          }

          tweakDefaultValueNode = tweakIdNode.getNext();
          tweakInfo.addOverrideDefaultValueCall(t.getSourceName(), tweakFunc, n,
              tweakDefaultValueNode);
          break;
        case GET_BOOLEAN:
        case GET_NUMBER:
        case GET_STRING:
          tweakInfo.addGetterCall(t.getSourceName(), tweakFunc, n);
      }
    }
  }

  /**
   * Holds information about a call to a goog.tweak function.
   */
  private static final class TweakFunctionCall {
    final TweakFunction tweakFunc;
    final Node callNode;
    final Node valueNode;

    TweakFunctionCall(TweakFunction tweakFunc, Node callNode) {
      this(tweakFunc, callNode, null);
    }

    TweakFunctionCall(TweakFunction tweakFunc, Node callNode, Node valueNode) {
      this.callNode = callNode;
      this.tweakFunc = tweakFunc;
      this.valueNode = valueNode;
    }

    Node getIdNode() {
      return callNode.getFirstChild().getNext();
    }
  }

  /**
   * Stores information about a single tweak.
   */
  private final class TweakInfo {
    final String tweakId;
    final List<TweakFunctionCall> functionCalls;
    TweakFunctionCall registerCall;
    Node defaultValueNode;

    TweakInfo(String tweakId) {
      this.tweakId = tweakId;
      functionCalls = Lists.newArrayList();
    }

    /**
     * If this tweak is registered, then looks for type warnings in default
     * value parameters and getter functions. If it is not registered, emits an
     * error for each function call.
     */
    void emitAllWarnings() {
      if (isRegistered()) {
        emitAllTypeWarnings();
      } else {
        emitUnknownTweakErrors();
      }
    }

    /**
     * Emits a warning for each default value parameter that has the wrong type
     * and for each getter function that was used for the wrong type of tweak.
     */
    void emitAllTypeWarnings() {
      for (TweakFunctionCall call : functionCalls) {
        Node valueNode = call.valueNode;
        TweakFunction tweakFunc = call.tweakFunc;
        TweakFunction registerFunc = registerCall.tweakFunc;
        if (valueNode != null) {
          // For register* and overrideDefaultValue calls, ensure the default
          // value is a literal of the correct type.
          if (!registerFunc.isValidNodeType(valueNode.getType())) {
            compiler.report(JSError.make(
                valueNode, INVALID_TWEAK_DEFAULT_VALUE_WARNING,
                tweakId, registerFunc.getName(),
                registerFunc.getExpectedTypeName()));
          }
        } else if (tweakFunc.isGetterFunction()) {
          // For getter calls, ensure the correct getter was used.
          if (!tweakFunc.isCorrectRegisterFunction(registerFunc)) {
            compiler.report(JSError.make(
                call.callNode, TWEAK_WRONG_GETTER_TYPE_WARNING,
                tweakFunc.getName(), registerFunc.getName()));
          }
        }
      }
    }

    /**
     * Emits an error for each function call that was found.
     */
    void emitUnknownTweakErrors() {
      for (TweakFunctionCall call : functionCalls) {
        compiler.report(JSError.make(
            call.getIdNode(), UNKNOWN_TWEAK_WARNING, tweakId));
      }
    }

    void addRegisterCall(String sourceName, TweakFunction tweakFunc,
        Node callNode, Node defaultValueNode) {
      registerCall = new TweakFunctionCall(tweakFunc, callNode,
          defaultValueNode);
      functionCalls.add(registerCall);
    }

    void addOverrideDefaultValueCall(String sourceName,
        TweakFunction tweakFunc, Node callNode, Node defaultValueNode) {
      functionCalls.add(new TweakFunctionCall(tweakFunc, callNode,
          defaultValueNode));
      this.defaultValueNode = defaultValueNode;
    }

    void addGetterCall(String sourceName, TweakFunction tweakFunc,
        Node callNode) {
      functionCalls.add(new TweakFunctionCall(tweakFunc, callNode));
    }

    boolean isRegistered() {
      return registerCall != null;
    }

    Node getDefaultValueNode() {
      Preconditions.checkState(isRegistered());
      // Use calls to goog.tweak.overrideDefaultValue() first.
      if (defaultValueNode != null) {
        return defaultValueNode;
      }
      // Use the value passed to the register function next.
      if (registerCall.valueNode != null) {
        return registerCall.valueNode;
      }
      // Otherwise, use the default value for the tweak's type.
      return registerCall.tweakFunc.createDefaultValueNode();
    }
  }
}
