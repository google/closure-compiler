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

package com.google.javascript.jscomp;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.protobuf.TextFormat;

/**
 * Provides a framework for checking code against a set of user configured
 * conformance rules.  The rules are specified by the ConformanceConfig
 * proto, which allows for both standard checks (forbidden properties,
 * variables, or dependencies) and allow for more complex checks using
 * custom rules than specify
 *
 */
public final class CheckConformance extends AbstractPostOrderCallback
    implements CompilerPass {

  static final DiagnosticType CONFORMANCE_VIOLATION =
      DiagnosticType.warning(
          "JSC_CONFORMANCE_VIOLATION",
          "Violation: {0}");

  static final DiagnosticType CONFORMANCE_POSSIBLE_VIOLATION =
      DiagnosticType.warning(
          "JSC_CONFORMANCE_POSSIBLE_VIOLATION",
          "Possible violation: {0}");

  static final DiagnosticType INVALID_REQUIREMENT_SPEC =
      DiagnosticType.error(
          "JSC_INVALID_REQUIREMENT_SPEC",
          "Invalid requirement. Reason: {0}\nRequirement spec:\n{1}");

  private final AbstractCompiler compiler;
  private final ImmutableList<Rule> rules;

  public static interface Rule {
    /** Perform conformance check */
    void check(NodeTraversal t, Node n);
  }

  /**
   * @param configs The rules to check.
   */
  CheckConformance(
      AbstractCompiler compiler,
      ImmutableList<ConformanceConfig> configs) {
    this.compiler = compiler;
    // Initialize the map of functions to inspect for renaming candidates.
    this.rules = initRules(compiler, configs);
  }

  @Override
  public void process(Node externs, Node root) {
    if (!rules.isEmpty()) {
      NodeTraversal.traverse(compiler, root, this);
    }
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    for (Rule rule : rules) {
      rule.check(t, n);
    }
  }

  /**
   * Build the data structures need by this pass from the provided
   * configurations.
   */
  private static ImmutableList<Rule> initRules(
      AbstractCompiler compiler, ImmutableList<ConformanceConfig> configs) {
    ImmutableList.Builder<Rule> builder = ImmutableList.builder();
    for (ConformanceConfig config : configs) {
      for (Requirement requirement : config.getRequirementList()) {
        Rule rule = initRule(compiler, requirement);
        if (rule != null) {
          builder.add(rule);
        }
      }
    }
    return builder.build();
  }

  private static Rule initRule(
      AbstractCompiler compiler, Requirement requirement) {
    try {
      switch (requirement.getType()) {
        case CUSTOM:
          return new ConformanceRules.CustomRuleProxy(compiler, requirement);
        case BANNED_CODE_PATTERN:
          return new ConformanceRules.BannedCodePattern(compiler, requirement);
        case BANNED_DEPENDENCY:
          return new ConformanceRules.BannedDependency(compiler, requirement);
        case BANNED_NAME:
          return new ConformanceRules.BannedName(compiler, requirement);
        case BANNED_PROPERTY:
        case BANNED_PROPERTY_READ:
        case BANNED_PROPERTY_WRITE:
        case BANNED_PROPERTY_CALL:
          return new ConformanceRules.BannedProperty(compiler, requirement);
        case RESTRICTED_NAME_CALL:
          return new ConformanceRules.RestrictedNameCall(
              compiler, requirement);
        case RESTRICTED_METHOD_CALL:
          return new ConformanceRules.RestrictedMethodCall(
              compiler, requirement);
        default:
          reportInvalidRequirement(
              compiler, requirement, "unknown requirement type");
          return null;
      }
    } catch (InvalidRequirementSpec e){
      reportInvalidRequirement(compiler, requirement, e.getMessage());
      return null;
    }
  }

  public static class InvalidRequirementSpec extends Exception {
    InvalidRequirementSpec(String message) {
      super(message);
    }
  }

  /**
   * @param requirement
   */
  private static void reportInvalidRequirement(
      AbstractCompiler compiler, Requirement requirement, String reason) {
    compiler.report(JSError.make(INVALID_REQUIREMENT_SPEC,
        reason,
        TextFormat.printToString(requirement)));
  }
}
