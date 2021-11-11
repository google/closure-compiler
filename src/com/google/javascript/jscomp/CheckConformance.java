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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.Node;
import com.google.protobuf.Descriptors;
import com.google.protobuf.TextFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Provides a framework for checking code against a set of user configured conformance rules. The
 * rules are specified by the ConformanceConfig proto, which allows for both standard checks
 * (forbidden properties, variables, or dependencies) and allow for more complex checks using custom
 * rules than specify
 *
 * <p>Conformance violations are both reported as compiler errors, and are also reported separately
 * to the {cI gue@link ErrorManager}
 */
@GwtIncompatible("com.google.protobuf")
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
    @Nullable
    default Precondition getPrecondition() {
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

  /** @param configs The rules to check. */
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
    // Don't inspect extern files
    return !n.isScript() || !t.getInput().getSourceFile().isExtern();
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    /**
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
   * Gets requirements from all configs. Merges allowlists/whitelists of requirements with 'extends'
   * equal to 'rule_id' of other rule.
   */
  static List<Requirement> mergeRequirements(
      AbstractCompiler compiler, List<ConformanceConfig> configs) {
    List<Requirement.Builder> builders = new ArrayList<>();
    Map<String, Requirement.Builder> extendable = new HashMap<>();
    for (ConformanceConfig config : configs) {
      for (Requirement requirement : config.getRequirementList()) {
        Requirement.Builder builder = requirement.toBuilder();
        if (requirement.hasRuleId()) {
          if (requirement.getRuleId().isEmpty()) {
            reportInvalidRequirement(compiler, requirement, "empty rule_id");
            continue;
          }
          if (extendable.containsKey(requirement.getRuleId())) {
            reportInvalidRequirement(
                compiler,
                requirement,
                "two requirements with the same rule_id: " + requirement.getRuleId());
            continue;
          }
          extendable.put(requirement.getRuleId(), builder);
        }
        if (!requirement.hasExtends()) {
          builders.add(builder);
        }
      }
    }

    for (ConformanceConfig config : configs) {
      for (Requirement requirement : config.getRequirementList()) {
        if (requirement.hasExtends()) {
          Requirement.Builder existing = extendable.get(requirement.getExtends());
          if (existing == null) {
            reportInvalidRequirement(
                compiler, requirement, "no requirement with rule_id: " + requirement.getExtends());
            continue;
          }
          for (Descriptors.FieldDescriptor field : requirement.getAllFields().keySet()) {
            if (!EXTENDABLE_FIELDS.contains(field.getName())) {
              reportInvalidRequirement(
                  compiler, requirement, "extending rules allow only " + EXTENDABLE_FIELDS);
            }
          }
          if (requirement.getValueCount() > 0 && !existing.getAllowExtendingValue()) {
            reportInvalidRequirement(
                compiler,
                requirement,
                "extending rule may not specify 'value' if base rule does not allow it");
          }
          existing
              .addAllWhitelist(requirement.getWhitelistList())
              .addAllWhitelistRegexp(requirement.getWhitelistRegexpList())
              .addAllAllowlist(requirement.getAllowlistList())
              .addAllAllowlistRegexp(requirement.getAllowlistRegexpList())
              .addAllOnlyApplyTo(requirement.getOnlyApplyToList())
              .addAllOnlyApplyToRegexp(requirement.getOnlyApplyToRegexpList())
              .addAllWhitelistEntry(requirement.getWhitelistEntryList())
              .addAllAllowlistEntry(requirement.getAllowlistEntryList())
              .addAllValue(requirement.getValueList())
              .addAllConfigFile(requirement.getConfigFileList());
        }
      }
    }

    List<Requirement> requirements = new ArrayList<>(builders.size());
    for (Requirement.Builder builder : builders) {
      removeDuplicates(builder);
      requirements.add(builder.build());
    }
    return requirements;
  }

  private static void removeDuplicates(Requirement.Builder requirement) {
    final Set<String> list1 = ImmutableSet.copyOf(requirement.getWhitelistList());
    requirement.clearWhitelist().addAllWhitelist(list1);

    final Set<String> allowlist = ImmutableSet.copyOf(requirement.getAllowlistList());
    requirement.clearAllowlist().addAllAllowlist(allowlist);

    final Set<String> list2 = ImmutableSet.copyOf(requirement.getWhitelistRegexpList());
    requirement.clearWhitelistRegexp().addAllWhitelistRegexp(list2);

    final Set<String> allowlistRegexp = ImmutableSet.copyOf(requirement.getAllowlistRegexpList());
    requirement.clearAllowlistRegexp().addAllAllowlistRegexp(allowlistRegexp);

    final Set<String> list3 = ImmutableSet.copyOf(requirement.getOnlyApplyToList());
    requirement.clearOnlyApplyTo().addAllOnlyApplyTo(list3);

    final Set<String> list4 = ImmutableSet.copyOf(requirement.getOnlyApplyToRegexpList());
    requirement.clearOnlyApplyToRegexp().addAllOnlyApplyToRegexp(list4);
  }

  private static Rule initRule(AbstractCompiler compiler, Requirement requirement) {
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
        default:
          reportInvalidRequirement(compiler, requirement, "unknown requirement type");
          return null;
      }
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
        JSError.make(INVALID_REQUIREMENT_SPEC, reason, TextFormat.printToString(requirement)));
  }
}
