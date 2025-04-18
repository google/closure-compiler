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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.Node;
import com.google.protobuf.Descriptors;
import com.google.protobuf.TextFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Provides a framework for checking code against a set of user configured conformance rules. The
 * rules are specified by the ConformanceConfig proto, which allows for both standard checks
 * (forbidden properties, variables, or dependencies) and allow for more complex checks using custom
 * rules than specify
 *
 * <p>Conformance violations are both reported as compiler errors, and are also reported separately
 * to the {cI gue@link ErrorManager}
 */
public final class CheckConformance implements NodeTraversal.Callback, CompilerPass {
  static final DiagnosticType CONFORMANCE_ERROR =
      DiagnosticType.error("JSC_CONFORMANCE_ERROR", "Violation: {0}{1}{2}");

  static final DiagnosticType CONFORMANCE_VIOLATION =
      DiagnosticType.warning("JSC_CONFORMANCE_VIOLATION", "Violation: {0}{1}{2}");

  static final DiagnosticType CONFORMANCE_POSSIBLE_VIOLATION =
      DiagnosticType.warning("JSC_CONFORMANCE_POSSIBLE_VIOLATION", "Possible violation: {0}{1}{2}");

  static final DiagnosticType INVALID_REQUIREMENT_SPEC =
      DiagnosticType.error(
          "JSC_INVALID_REQUIREMENT_SPEC",
          "Invalid requirement. Reason: {0}\nRequirement spec:\n{1}");

  private final AbstractCompiler compiler;
  private final ImmutableList<Category> categories;

  public static interface Rule {

    /**
     * Return a precondition for this rule.
     *
     * <p>This method will only be called once (per rule) during the creation of the
     * CheckConformance pass. Therefore, the return must be constant.
     *
     * <p>Returning null means that there is no precondition. This is convenient, but can be a major
     * performance hit.
     */
    default @Nullable Precondition getPrecondition() {
      return Precondition.CHECK_ALL;
    }

    /** Perform conformance check */
    void check(NodeTraversal t, Node n);
  }

  /**
   * A condition that must be true for a rule to possibly match a node.
   *
   * <p>Instances are used as keys to group rules with common preconditions. Grouping allows shared
   * computation to be done only once per node, which is a substantial performance improvement.
   */
  public static interface Precondition {
    boolean shouldCheck(Node n);

    public static final Precondition CHECK_ALL =
        new Precondition() {
          @Override
          public boolean shouldCheck(Node n) {
            return true;
          }
        };
  }

  private static final class Category {
    final Precondition precondition;
    final ImmutableList<Rule> rules;

    Category(Precondition precondition, ImmutableList<Rule> rules) {
      this.precondition = precondition;
      this.rules = rules;
    }
  }

  /**
   * @param configs The rules to check.
   */
  CheckConformance(AbstractCompiler compiler, ImmutableList<ConformanceConfig> configs) {
    this.compiler = compiler;
    // Initialize the map of functions to inspect for renaming candidates.
    this.categories = initRules(compiler, configs);
  }

  @Override
  public void process(Node externs, Node root) {
    if (!this.categories.isEmpty()) {
      NodeTraversal.traverseRoots(compiler, this, externs, root);
    }
  }

  @Override
  public final boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    // Don't inspect extern files, *.tsmes.closure.js, weak sources, or closureUnaware code.
    return !n.isScript()
        || (isScriptOfInterest(t.getInput().getSourceFile())
            && !t.getSourceName().endsWith("tsmes.closure.js"));
  }

  private boolean isScriptOfInterest(SourceFile sf) {
    return !sf.isWeak()
        && !sf.isExtern()
        && !sf.isClosureUnawareCode();
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    /*
     * Use counted loops and backward iteration for performance.
     *
     * <p>These loops are run a huge number of times. The overhead of enhanced-for loops and even
     * calling size() can add seconds of build time to large projects.
     */
    for (int c = this.categories.size() - 1; c >= 0; c--) {
      Category category = this.categories.get(c);
      if (category.precondition.shouldCheck(n)) {
        for (int r = category.rules.size() - 1; r >= 0; r--) {
          category.rules.get(r).check(t, n);
        }
      }
    }
  }

  /** Build the data structures need by this pass from the provided configurations. */
  private static ImmutableList<Category> initRules(
      AbstractCompiler compiler, ImmutableList<ConformanceConfig> configs) {
    HashMultimap<Precondition, Rule> builder = HashMultimap.create();
    List<Requirement> requirements = mergeRequirements(compiler, configs);
    for (Requirement requirement : requirements) {
      Rule rule = initRule(compiler, requirement);
      if (rule != null) {
        builder.put(rule.getPrecondition(), rule);
      }
    }
    return builder.asMap().entrySet().stream()
        .map((e) -> new Category(e.getKey(), ImmutableList.copyOf(e.getValue())))
        .collect(toImmutableList());
  }

  private static final ImmutableSet<String> EXTENDABLE_FIELDS =
      ImmutableSet.of(
          "config_file",
          "extends",
          "only_apply_to",
          "only_apply_to_regexp",
          "whitelist",
          "whitelist_regexp",
          "allowlist",
          "allowlist_regexp",
          "value");

  /**
   * Gets requirements from all configs, validates them, and merges any extending requirements into
   * their respective requirements from which they're extending. This means merging the
   * allowlists/whitelists of requirements with 'extends' equal to 'rule_id' of other rule.
   *
   * <p>The requirements inheritance can not have a chain of more than 1 level. This means that a
   * requirement that extends another requirement can not itself be extended. This is enforced by
   * this pass and reported as an error if a requirement has both 'extends' and 'rule_id' fields
   * set. Given that, we can safely define the following types of requirements:
   *
   * <p>Root requirements are simply the requirements that do not extend any other requirement. They
   * do not have an `extends` field. They may or may not have a 'rule_id' field. For example,
   * without a 'rule_id' field:
   *
   * <pre>
   *   requirement: {
   *     type: BANNED_NAME
   *     value: 'eval'
   *     error_message: 'eval is not allowed'
   *   }
   * </pre>
   *
   * <p>Root requirements may have a 'rule_id' field. For example:
   *
   * <pre>
   *   requirement: {
   *     rule_id: 'eval'
   *     type: BANNED_NAME
   *     value: 'eval'
   *     error_message: 'eval is not allowed'
   *   }
   * </pre>
   *
   * <p>Extendable root requirements are those root requirements that have a 'rule_id' field, and
   * therefore _can_ be extended. They may or may not getting extended by another requirement. For
   * example:
   *
   * <pre>
   *   requirement: {
   *     rule_id: 'extendable'
   *     type: BANNED_NAME
   *     value: 'eval'
   *     error_message: 'eval is not allowed'
   *   }
   * </pre>
   *
   * <p>Extended root requirements are extendable root requirements that are extended by at least
   * one other requirement. For example:
   *
   * <pre>
   *   requirement: {
   *     rule_id: 'extended'
   *     type: BANNED_NAME
   *     value: 'eval'
   *     error_message: 'eval is not allowed'
   *   }
   *
   *   requirement: {
   *     extends: 'extended'
   *   }
   * </pre>
   *
   * <p>Extending requirements are leaf-requirements that have an 'extends' field. For example:
   *
   * <pre>
   *   requirement: {
   *     extends: 'eval'
   * </pre>
   *
   * <p>The following diagram illustrates the relationship between the different types of
   * requirements that can be defined.
   *
   * <p>
   *
   * <pre>
   *     {@literal
   *
   *             +---------------------------+    +------------------------------+
   *             |    Root Requirements      |    |  Extending Requirements      |
   *             |                           |    | (leaf, with 'extends' field) |
   *             +---------------------------+    +------------------------------+
   *                /                        \
   *               /                          \
   *   +-------------------------------+      +-----------------------------------+
   *   |Extendable Root Requirements   |      | Non-Extendable Root Requirements  |
   *   |     (with rule_id)            |      |        (without rule_id)          |
   *   +-------------------------------+      +-----------------------------------+
   *             |                      \
   *             |                       \
   *   +----------------------------+    +--------------------------------+
   *   | Extended Root Requirements |    | Non-Extended Root Requirements |
   *   +----------------------------+    +--------------------------------+
   *
   *
   *     }
   * </pre>
   *
   * <p>Here's the algorithm:
   *
   * <ol>
   *   <li>Process the root requirements and add them to the rootRequirements list, and process the
   *       extendable requirements and add them to the extendable map.
   *   <li>Process extending-leaf requirements and merge them into their respective extended root
   *       requirements.
   *   <li>Remove duplicates from the allowlists/whitelists of the merged extended root
   *       requirements.
   * </ol>
   *
   * @return a list of root requirements of all configs after merging.
   */
  static List<Requirement> mergeRequirements(
      AbstractCompiler compiler, List<ConformanceConfig> configs) {
    // Root requirements (i.e. requirements that have no 'extends' field).
    List<Requirement.Builder> rootRequirements = new ArrayList<>();
    // Requirements that are extendable (i.e. have a 'rule_id' field).
    Map<String, Requirement.Builder> extendable = new LinkedHashMap<>();

    // 1. Process the root requirements and add them to the rootRequirements list, and process the
    // extendable requirements and add them to the extendable map.
    for (ConformanceConfig config : configs) {
      for (Requirement requirement : config.getRequirementList()) {
        Requirement.Builder builder = requirement.toBuilder();
        if (requirement.hasRuleId()) {
          if (!validateExtendableRequirement(compiler, requirement, extendable)) {
            continue;
          }
          // has a rule_id, so it's extendable
          extendable.put(requirement.getRuleId(), builder);
        }
        if (!requirement.hasExtends()) {
          // does not extend anything, so it's a root requirement
          rootRequirements.add(builder);
        }
      }
    }

    // 2. Process extending requirements and merge them into their respective extended requirements.
    for (ConformanceConfig config : configs) {
      for (Requirement requirement : config.getRequirementList()) {
        if (requirement.hasExtends()) {
          Requirement.Builder extendableRequirement = extendable.get(requirement.getExtends());
          if (!validateExtendingRequirement(compiler, requirement, extendableRequirement)) {
            continue;
          }
          mergeExtendingRequirementIntoExtended(extendableRequirement, requirement);
        }
      }
    }

    // 3. Remove duplicates from the allowlists/whitelists of the merged root requirements.
    List<Requirement> cleanedUpRootRequirements = new ArrayList<>(rootRequirements.size());
    for (Requirement.Builder builder : rootRequirements) {
      removeDuplicates(builder);
      cleanedUpRootRequirements.add(builder.build());
    }
    return cleanedUpRootRequirements;
  }

  /**
   * Merges the allowlists/whitelists of the extending requirement into the extended requirement.
   *
   * @param extendedRequirement the extended requirement to merge the extending requirement into.
   * @param extendingRequirement the extending requirement to merge into the extended requirement.
   */
  private static void mergeExtendingRequirementIntoExtended(
      Requirement.Builder extendedRequirement, Requirement extendingRequirement) {
    checkState(
        extendingRequirement.getExtends().equals(extendedRequirement.getRuleId()),
        "Attempting to merge an requirement with the wrong extended requirement");
    extendedRequirement
        .addAllWhitelist(extendingRequirement.getWhitelistList())
        .addAllWhitelistRegexp(extendingRequirement.getWhitelistRegexpList())
        .addAllAllowlist(extendingRequirement.getAllowlistList())
        .addAllAllowlistRegexp(extendingRequirement.getAllowlistRegexpList())
        .addAllOnlyApplyTo(extendingRequirement.getOnlyApplyToList())
        .addAllOnlyApplyToRegexp(extendingRequirement.getOnlyApplyToRegexpList())
        .addAllWhitelistEntry(extendingRequirement.getWhitelistEntryList())
        .addAllAllowlistEntry(extendingRequirement.getAllowlistEntryList())
        .addAllValue(extendingRequirement.getValueList())
        .addAllConfigFile(extendingRequirement.getConfigFileList());
  }

  /**
   * Validates a extendable requirement.
   *
   * <p>A valid extendable requirement is a requirement that has a unique, non-empty rule_id.
   *
   * @return true if the requirement is valid, false otherwise.
   */
  private static boolean validateExtendableRequirement(
      AbstractCompiler compiler,
      Requirement requirement,
      Map<String, Requirement.Builder> extendable) {
    checkState(requirement.hasRuleId(), "Extendable requirement must have a rule_id");

    if (requirement.getRuleId().isEmpty()) {
      reportInvalidRequirement(compiler, requirement, "empty rule_id");
      return false;
    }
    if (extendable.containsKey(requirement.getRuleId())) {
      reportInvalidRequirement(
          compiler,
          requirement,
          "two requirements with the same rule_id: " + requirement.getRuleId());
      return false;
    }
    return true;
  }

  /**
   * Validates an extending requirement is correctly extending an extendable requirement.
   *
   * <p>A valid extending requirement is a requirement that:
   *
   * <ol>
   *   <li>has a non-empty extends field, and
   *   <li>only extends an existing extendable requirement
   *   <li>does not specify any fields that are not in {@link #EXTENDABLE_FIELDS}.
   *   <li>does not specify a value if the extendable requirement does not allow extending values.
   *
   * @return true if the requirement is valid, false otherwise.
   */
  private static boolean validateExtendingRequirement(
      AbstractCompiler compiler,
      Requirement extendingRequirement,
      Requirement.Builder extendableRequirement) {
    checkState(extendingRequirement.hasExtends(), "Extending requirement must have an extends");
    if (extendableRequirement == null) {
      reportInvalidRequirement(
          compiler,
          extendingRequirement,
          "no extendable requirement with rule_id: " + extendingRequirement.getExtends());
      return false;
    }
    for (Descriptors.FieldDescriptor field : extendingRequirement.getAllFields().keySet()) {
      if (!EXTENDABLE_FIELDS.contains(field.getName())) {
        reportInvalidRequirement(
            compiler, extendingRequirement, "extending rules allow only " + EXTENDABLE_FIELDS);
      }
    }
    if (extendingRequirement.getValueCount() > 0
        && !extendableRequirement.getAllowExtendingValue()) {
      reportInvalidRequirement(
          compiler,
          extendingRequirement,
          "extending rule may not specify 'value' if extendable rule does not allow it");
    }
    return true;
  }

  private static void removeDuplicates(Requirement.Builder requirement) {
    final ImmutableSet<String> list1 = ImmutableSet.copyOf(requirement.getWhitelistList());
    requirement.clearWhitelist().addAllWhitelist(list1);

    final ImmutableSet<String> allowlist = ImmutableSet.copyOf(requirement.getAllowlistList());
    requirement.clearAllowlist().addAllAllowlist(allowlist);

    final ImmutableSet<String> list2 = ImmutableSet.copyOf(requirement.getWhitelistRegexpList());
    requirement.clearWhitelistRegexp().addAllWhitelistRegexp(list2);

    final ImmutableSet<String> allowlistRegexp =
        ImmutableSet.copyOf(requirement.getAllowlistRegexpList());
    requirement.clearAllowlistRegexp().addAllAllowlistRegexp(allowlistRegexp);

    final ImmutableSet<String> list3 = ImmutableSet.copyOf(requirement.getOnlyApplyToList());
    requirement.clearOnlyApplyTo().addAllOnlyApplyTo(list3);

    final ImmutableSet<String> list4 = ImmutableSet.copyOf(requirement.getOnlyApplyToRegexpList());
    requirement.clearOnlyApplyToRegexp().addAllOnlyApplyToRegexp(list4);
  }

  private static @Nullable Rule initRule(AbstractCompiler compiler, Requirement requirement) {
    try {
      switch (requirement.getType()) {
        case CUSTOM:
          return new ConformanceRules.CustomRuleProxy(compiler, requirement);
        case NO_OP:
          return new ConformanceRules.NoOp(compiler, requirement);
        case BANNED_CODE_PATTERN:
          return new ConformanceRules.BannedCodePattern(compiler, requirement);
        case BANNED_DEPENDENCY:
          return new ConformanceRules.BannedDependency(compiler, requirement);
        case BANNED_DEPENDENCY_REGEX:
          return new ConformanceRules.BannedDependencyRegex(compiler, requirement);
        case BANNED_ENHANCE:
          return new ConformanceRules.BannedEnhance(compiler, requirement);
        case BANNED_MODS_REGEX:
          return new ConformanceRules.BannedModsRegex(compiler, requirement);
        case BANNED_NAME:
        case BANNED_NAME_CALL:
          return new ConformanceRules.BannedName(compiler, requirement);
        case BANNED_PROPERTY:
        case BANNED_PROPERTY_READ:
        case BANNED_PROPERTY_WRITE:
        case BANNED_PROPERTY_NON_CONSTANT_WRITE:
        case BANNED_PROPERTY_CALL:
          return new ConformanceRules.BannedProperty(compiler, requirement);
        case RESTRICTED_NAME_CALL:
          return new ConformanceRules.RestrictedNameCall(compiler, requirement);
        case RESTRICTED_METHOD_CALL:
          return new ConformanceRules.RestrictedMethodCall(compiler, requirement);
        case RESTRICTED_PROPERTY_WRITE:
          return new ConformanceRules.RestrictedPropertyWrite(compiler, requirement);
        case BANNED_STRING_REGEX:
          return new ConformanceRules.BannedStringRegex(compiler, requirement);
      }
      throw new AssertionError();
    } catch (InvalidRequirementSpec e) {
      reportInvalidRequirement(compiler, requirement, e.getMessage());
      return null;
    }
  }

  public static class InvalidRequirementSpec extends Exception {
    InvalidRequirementSpec(String message) {
      super(message);
    }

    InvalidRequirementSpec(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private static void reportInvalidRequirement(
      AbstractCompiler compiler, Requirement requirement, String reason) {
    compiler.report(
        JSError.make(
            INVALID_REQUIREMENT_SPEC, reason, TextFormat.printer().printToString(requirement)));
  }
}
