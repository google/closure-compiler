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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;
import com.google.javascript.jscomp.CheckConformance.InvalidRequirementSpec;
import com.google.javascript.jscomp.CheckConformance.Precondition;
import com.google.javascript.jscomp.CheckConformance.Rule;
import com.google.javascript.jscomp.CodingConvention.AssertionFunctionLookup;
import com.google.javascript.jscomp.ConformanceConfig.LibraryLevelNonAllowlistedConformanceViolationsBehavior;
import com.google.javascript.jscomp.Requirement.Severity;
import com.google.javascript.jscomp.base.LinkedIdentityHashSet;
import com.google.javascript.jscomp.parsing.JsDocInfoParser;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.EnumElementType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.Property;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.jspecify.annotations.Nullable;

/**
 * Standard conformance rules. See
 * third_party/java_src/jscomp/java/com/google/javascript/jscomp/conformance.proto
 */
public final class ConformanceRules {

  private static final AllowList ALL_TS_ALLOWLIST = createTsAllowlist();

  private static final Splitter ON_PROTOTYPE = Splitter.on(".prototype.");
  private static final Splitter ON_DOT = Splitter.on(".");

  private static AllowList createTsAllowlist() {
    try {
      return new AllowList(
          ImmutableList.of(), ImmutableList.of(".*\\.closure\\.js", ".*\\.tsx\\.cl\\.js"));
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }

  private ConformanceRules() {}

  /**
   * Classes extending AbstractRule must return ConformanceResult from their checkConformance
   * implementation. For simple rules, the constants CONFORMANCE, POSSIBLE_VIOLATION, VIOLATION are
   * sufficient. However, for some rules additional clarification specific to the violation instance
   * is helpful, for that, an instance of this class can be created to associate a note with the
   * violation.
   */
  public static class ConformanceResult {
    ConformanceResult(ConformanceLevel level) {
      this(level, "");
    }

    ConformanceResult(ConformanceLevel level, String note) {
      this.level = level;
      this.note = note;
    }

    public final ConformanceLevel level;
    public final String note;

    // For CONFORMANCE rules that don't generate notes:
    public static final ConformanceResult CONFORMANCE =
        new ConformanceResult(ConformanceLevel.CONFORMANCE);
    public static final ConformanceResult POSSIBLE_VIOLATION =
        new ConformanceResult(ConformanceLevel.POSSIBLE_VIOLATION);
    private static final ConformanceResult POSSIBLE_VIOLATION_DUE_TO_LOOSE_TYPES =
        new ConformanceResult(
            ConformanceLevel.POSSIBLE_VIOLATION,
            "The type information available for this expression is too loose "
                + "to ensure conformance.");
    public static final ConformanceResult VIOLATION =
        new ConformanceResult(ConformanceLevel.VIOLATION);
  }

  /** Possible check check results */
  public static enum ConformanceLevel {
    // Nothing interesting detected.
    CONFORMANCE,
    // In the optionally typed world of the Closure Compiler type system
    // it is possible that detect patterns that match with looser types
    // that the target pattern.
    POSSIBLE_VIOLATION,
    // Definitely a violation.
    VIOLATION,
  }

  private static @Nullable Pattern buildPattern(List<String> reqPatterns)
      throws InvalidRequirementSpec {
    if (reqPatterns == null || reqPatterns.isEmpty()) {
      return null;
    }

    // validate the patterns
    for (String reqPattern : reqPatterns) {
      try {
        Pattern.compile(reqPattern);
      } catch (PatternSyntaxException e) {
        throw new InvalidRequirementSpec("invalid regex pattern", e);
      }
    }

    Pattern pattern = null;
    try {
      String jointRegExp = "(" + Joiner.on("|").join(reqPatterns) + ")";
      pattern = Pattern.compile(jointRegExp);
    } catch (PatternSyntaxException e) {
      throw new RuntimeException("bad joined regexp", e);
    }
    return pattern;
  }

  private static class AllowList {
    final @Nullable ImmutableList<String> prefixes;
    final @Nullable Pattern regexp;
    final @Nullable RequirementScopeEntry allowlistEntry;

    AllowList(List<String> prefixes, List<String> regexps) throws InvalidRequirementSpec {
      this.prefixes = ImmutableList.<String>copyOf(prefixes);
      this.regexp = buildPattern(regexps);
      this.allowlistEntry = null;
    }

    AllowList(RequirementScopeEntry allowlistEntry) throws InvalidRequirementSpec {
      this.prefixes = ImmutableList.copyOf(allowlistEntry.getPrefixList());
      this.regexp = buildPattern(allowlistEntry.getRegexpList());
      this.allowlistEntry = allowlistEntry;
    }

    /**
     * Returns true if the given path matches one of the prefixes or regexps, and false otherwise
     */
    boolean matches(String path) {
      // If the path ends with .closure.js or .tsx.cl.js, it is probably a tsickle-generated file,
      // and there may be entries in the allow list for the TypeScript path
      String tsPath =
          path.endsWith(".closure.js")
              ? path.substring(0, path.length() - ".closure.js".length()) + ".ts"
              // TSX
              : (path.endsWith(".tsx.cl.js")
                  ? path.substring(0, path.length() - ".cl.js".length())
                  : null);
      if (prefixes != null) {
        for (String prefix : prefixes) {
          if (!path.isEmpty()
              && (path.startsWith(prefix) || (tsPath != null && tsPath.startsWith(prefix)))) {
            return true;
          }
        }
      }

      return regexp != null
          && (regexp.matcher(path).find() || (tsPath != null && regexp.matcher(tsPath).find()));
    }
  }

  /**
   * A conformance rule implementation to support things common to all rules such as allowlisting
   * and reporting.
   */
  public abstract static class AbstractRule implements Rule {
    final AbstractCompiler compiler;
    final String message;
    final Severity severity;
    final ImmutableList<AllowList> allowlists;
    final @Nullable AllowList onlyApplyTo;
    final boolean reportLooseTypeViolations;
    final TypeMatchingStrategy typeMatchingStrategy;
    final Requirement requirement;

    public AbstractRule(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      if (!requirement.hasErrorMessage()) {
        throw new InvalidRequirementSpec("missing message");
      }
      this.compiler = compiler;
      String message = requirement.getErrorMessage();
      if (requirement.getConfigFileCount() > 0) {
        message +=
            "\n  defined in " + Joiner.on("\n  extended by ").join(requirement.getConfigFileList());
      }
      this.message = message;
      if (requirement.getSeverity() == Severity.UNSPECIFIED) {
        severity = Severity.WARNING;
      } else {
        severity = requirement.getSeverity();
      }

      // build allowlists
      ImmutableList.Builder<AllowList> allowlistsBuilder = new ImmutableList.Builder<>();
      for (RequirementScopeEntry entry : requirement.getWhitelistEntryList()) {
        allowlistsBuilder.add(new AllowList(entry));
      }
      for (RequirementScopeEntry entry : requirement.getAllowlistEntryList()) {
        allowlistsBuilder.add(new AllowList(entry));
      }

      if (this.tsIsAllowlisted()) {
        allowlistsBuilder.add(ALL_TS_ALLOWLIST);
      }

      if (requirement.getWhitelistCount() > 0 || requirement.getWhitelistRegexpCount() > 0) {
        AllowList allowlist =
            new AllowList(requirement.getWhitelistList(), requirement.getWhitelistRegexpList());
        allowlistsBuilder.add(allowlist);
      }
      if (requirement.getAllowlistCount() > 0 || requirement.getAllowlistRegexpCount() > 0) {
        AllowList allowlist =
            new AllowList(requirement.getAllowlistList(), requirement.getAllowlistRegexpList());
        allowlistsBuilder.add(allowlist);
      }
      allowlists = allowlistsBuilder.build();

      if (requirement.getOnlyApplyToCount() > 0 || requirement.getOnlyApplyToRegexpCount() > 0) {
        onlyApplyTo =
            new AllowList(requirement.getOnlyApplyToList(), requirement.getOnlyApplyToRegexpList());
      } else {
        onlyApplyTo = null;
      }
      reportLooseTypeViolations = requirement.getReportLooseTypeViolations();
      typeMatchingStrategy = getTypeMatchingStrategy(requirement);
      this.requirement = requirement;
    }

    protected boolean tsIsAllowlisted() {
      return false;
    }

    private static TypeMatchingStrategy getTypeMatchingStrategy(Requirement requirement) {
      return switch (requirement.getTypeMatchingStrategy()) {
        case LOOSE -> TypeMatchingStrategy.LOOSE;
        case STRICT_NULLABILITY -> TypeMatchingStrategy.STRICT_NULLABILITY;
        case SUBTYPES -> TypeMatchingStrategy.SUBTYPES;
        case EXACT -> TypeMatchingStrategy.EXACT;
        default -> throw new IllegalStateException("Unknown TypeMatchingStrategy");
      };
    }

    /**
     * @return Whether the code represented by the Node conforms to the rule.
     */
    protected abstract ConformanceResult checkConformance(NodeTraversal t, Node n);

    /** Returns the first AllowList entry that matches the given path, and null otherwise. */
    private @Nullable AllowList findAllowListForPath(String path) {
      Optional<Pattern> pathRegex = compiler.getOptions().getConformanceRemoveRegexFromPath();
      if (pathRegex.isPresent()) {
        path = pathRegex.get().matcher(path).replaceFirst("");
      }

      for (AllowList allowlist : allowlists) {
        if (allowlist.matches(path)) {
          return allowlist;
        }
      }
      return null;
    }

    @Override
    public final void check(
        NodeTraversal t, Node n, LibraryLevelNonAllowlistedConformanceViolationsBehavior behavior) {
      ConformanceResult result = checkConformance(t, n);
      if (result.level != ConformanceLevel.CONFORMANCE) {
        report(n, result, behavior);
      }
    }

    /**
     * Report a conformance warning for the given node.
     *
     * @param n The node representing the violating code.
     * @param result The result representing the confidence of the violation.
     */
    protected void report(
        Node n,
        ConformanceResult result,
        LibraryLevelNonAllowlistedConformanceViolationsBehavior behavior) {
      DiagnosticType msg;
      if (severity == Severity.ERROR) {
        // Always report findings that are errors, even if the types are too loose to be certain.
        // TODO(bangert): If this causes problems, add another severity category that only
        // errors when certain.
        msg = CheckConformance.CONFORMANCE_ERROR;
      } else {
        if (result.level == ConformanceLevel.VIOLATION) {
          msg = CheckConformance.CONFORMANCE_VIOLATION;
        } else {
          msg = CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION;
        }
      }
      String separator = (result.note.isEmpty()) ? "" : "\n";
      JSError err = JSError.make(requirement, n, msg, message, separator, result.note);

      String path = NodeUtil.getSourceName(n);
      AllowList allowlist = path != null ? findAllowListForPath(path) : null;
      boolean isAllowlisted =
          !(allowlist == null && (onlyApplyTo == null || onlyApplyTo.matches(path)));
      boolean shouldReport =
          compiler
              .getErrorManager()
              // returns true even if the violation is allowlisted
              .shouldReportConformanceViolation(
                  requirement,
                  allowlist != null
                      ? Optional.fromNullable(allowlist.allowlistEntry)
                      : Optional.absent(),
                  err,
                  behavior,
                  isAllowlisted);

      // if the violation is not in a gencode or we're not in library level reporting mode,
      // determined by `shouldReport` above, then check the allowlists to decide whether to actually
      // report the violation or not.
      if (shouldReport && !isAllowlisted) {
        compiler.report(err);
      }
    }
  }

  /**
   * No-op rule that never reports any violations.
   *
   * <p>This exists so that, if a requirement becomes obsolete but is extended by other requirements
   * that can't all be simultaneously deleted, it can be changed to this rule, allowing it to be
   * effectively removed without breaking downstream builds.
   */
  static final class NoOp extends AbstractRule {
    NoOp(AbstractCompiler compiler, Requirement requirement) throws InvalidRequirementSpec {
      super(compiler, requirement);
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      return ConformanceResult.CONFORMANCE;
    }
  }

  abstract static class AbstractTypeRestrictionRule extends AbstractRule {
    private final JSType nativeObjectType;
    private final JSType allowlistedTypes;
    private final AssertionFunctionLookup assertionFunctions;

    public AbstractTypeRestrictionRule(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
      nativeObjectType = compiler.getTypeRegistry().getNativeType(JSTypeNative.OBJECT_TYPE);
      List<String> allowlistedTypeNames = requirement.getValueList();
      allowlistedTypes = union(allowlistedTypeNames);

      assertionFunctions =
          AssertionFunctionLookup.of(compiler.getCodingConvention().getAssertionFunctions());
    }

    protected boolean isAllowlistedType(Node n) {
      if (allowlistedTypes != null && n.getJSType() != null) {
        JSType targetType = n.getJSType().restrictByNotNullOrUndefined();
        if (targetType.isSubtypeOf(allowlistedTypes)) {
          return true;
        }
      }
      return false;
    }

    protected static boolean isKnown(Node n) {
      return !isUnknown(n)
          && !isBottom(n)
          && !isTemplateType(n); // TODO(johnlenz): Remove this restriction
    }

    protected boolean isNativeObjectType(Node n) {
      JSType type = n.getJSType().restrictByNotNullOrUndefined();
      return type.equals(nativeObjectType);
    }

    protected static boolean isTop(Node n) {
      JSType type = n.getJSType();
      return type != null && type.isAllType();
    }

    protected static boolean isUnknown(Node n) {
      JSType type = n.getJSType();
      return (type == null || type.isUnknownType());
    }

    protected static boolean isTemplateType(Node n) {
      JSType type = n.getJSType();
      if (type.isUnionType()) {
        for (JSType member : type.getUnionMembers()) {
          if (member.isTemplateType()) {
            return true;
          }
        }
      }
      return type.isTemplateType();
    }

    private static boolean isBottom(Node n) {
      JSType type = n.getJSType().restrictByNotNullOrUndefined();
      return type.isEmptyType();
    }

    protected @Nullable JSType union(List<String> typeNames) {
      JSTypeRegistry registry = compiler.getTypeRegistry();
      List<JSType> types = new ArrayList<>();

      for (String typeName : typeNames) {
        JSType type = registry.getGlobalType(typeName);
        if (type == null) {
          // For a few types like `google3.javascript.apps.wiz.events.wiz_event.WizEvent`, we need
          // to resolve via closure namespace. This is because the class type WizEvent is exported
          // from a goog.module, so is not a global type name in the global type registry.
          type = registry.resolveViaClosureNamespace(typeName);
        }
        if (type != null) {
          types.add(type);
        }
      }
      if (types.isEmpty()) {
        return null;
      } else {
        return registry.createUnionType(types);
      }
    }

    protected boolean isAssertionCall(Node n) {
      if (n.isCall() && n.getFirstChild().isQualifiedName()) {
        Node target = n.getFirstChild();
        return assertionFunctions.lookupByCallee(target) != null;
      }
      return false;
    }

    protected boolean isTypeImmediatelyTightened(Node n) {
      return isAssertionCall(n.getParent())
          || n.getParent().isTypeOf()
          || /* casted node */ n.getJSTypeBeforeCast() != null;
    }

    protected boolean isUsed(Node n) {
      if (n.getParent().isName() || NodeUtil.isLhsByDestructuring(n)) {
        return false;
      }

      // Consider lvalues in assignment operations to be used iff the actual assignment
      // operation's result is used. e.g. for `a.b.c`:
      //     USED: `alert(x = a.b.c);`
      //   UNUSED: `x = a.b.c;`
      if (NodeUtil.isAssignmentOp(n.getParent())) {
        return NodeUtil.isExpressionResultUsed(n.getParent());
      }

      return NodeUtil.isExpressionResultUsed(n);
    }
  }

  /**
   * Check that variables annotated as @const have an inferred type, if there is no type given
   * explicitly.
   */
  static class InferredConstCheck extends AbstractRule {
    public InferredConstCheck(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      JSDocInfo jsDoc = n.getJSDocInfo();
      if (jsDoc != null && jsDoc.hasConstAnnotation() && jsDoc.getType() == null) {
        if (n.isAssign()) {
          n = n.getFirstChild();
        }
        JSType type = n.getJSType();
        if (type != null && type.isUnknownType() && !NodeUtil.isNamespaceDecl(n)) {
          return ConformanceResult.VIOLATION;
        }
      }
      return ConformanceResult.CONFORMANCE;
    }
  }

  /** Banned dependency rule */
  static class BannedDependency extends AbstractRule {
    private final List<String> paths;

    BannedDependency(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
      paths = requirement.getValueList();
      if (paths.isEmpty()) {
        throw new InvalidRequirementSpec("missing value");
      }
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      if (n.isScript()) {
        String srcFile = n.getSourceFileName();
        for (int i = 0; i < paths.size(); i++) {
          String path = paths.get(i);
          if (srcFile.startsWith(path)) {
            return ConformanceResult.VIOLATION;
          }
        }
      }
      return ConformanceResult.CONFORMANCE;
    }
  }

  /** Banned dependency via regex rule */
  static class BannedDependencyRegex extends AbstractRule {
    private final @Nullable Pattern pathRegexp;

    BannedDependencyRegex(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
      List<String> pathRegexpList = requirement.getValueList();
      if (pathRegexpList.isEmpty()) {
        throw new InvalidRequirementSpec("missing value (no banned dependency regexps)");
      }
      pathRegexp = buildPattern(pathRegexpList);
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      if (n.isScript()) {
        String srcFile = n.getSourceFileName();
        if (pathRegexp != null && pathRegexp.matcher(srcFile).find()) {
          return ConformanceResult.VIOLATION;
        }
      }
      return ConformanceResult.CONFORMANCE;
    }
  }

  /** Banned name rule */
  static final class BannedName extends AbstractRule {
    // LINT.ThenChange(//depot/google3/javascript/typescript/compiler/conformance/rules/ban_third_party_script.ts)
    private final Requirement.Type requirementType;
    private final ImmutableList<Node> qualifiedNames;
    private final ImmutableSet<String> shortNames;

    BannedName(AbstractCompiler compiler, Requirement requirement) throws InvalidRequirementSpec {
      super(compiler, requirement);
      if (requirement.getValueCount() == 0) {
        throw new InvalidRequirementSpec("missing value");
      }
      this.requirementType = requirement.getType();

      ImmutableList.Builder<Node> qualifiedBuilder = ImmutableList.builder();
      ImmutableSet.Builder<String> shortBuilder = ImmutableSet.builder();
      for (String name : requirement.getValueList()) {
        Node qualifiedName = NodeUtil.newQName(compiler, name);
        qualifiedBuilder.add(qualifiedName);
        shortBuilder.add(qualifiedName.getString());
      }
      this.qualifiedNames = qualifiedBuilder.build();
      this.shortNames = shortBuilder.build();
    }

    private static final Precondition IS_CANDIDATE_NODE =
        (Node n) ->
            switch (n.getToken()) {
              case GETPROP -> n.getFirstChild().isQualifiedName();
              case NAME -> !n.getString().isEmpty();
              default -> false;
            };

    @Override
    public final Precondition getPrecondition() {
      return IS_CANDIDATE_NODE;
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      if (requirementType == Requirement.Type.BANNED_NAME_CALL
          && !ConformanceUtil.isCallTarget(n)) {
        return ConformanceResult.CONFORMANCE;
      }

      /*
       * Efficiently filter out nearly all candidates.
       *
       * <p>Ideally we could use a hashset containing the qualified names, but it turns out that
       * creating wrapper a object for every Node is more expensive than the loop below.
       */
      if (!this.shortNames.contains(n.getString())) {
        return ConformanceResult.CONFORMANCE;
      }

      /*
       * Defer expensive infrequent checks.
       *
       * <p>These could be a precondition, but they don't usually need to be checked. Since they're
       * kind of expensive, it's cheaper overall to defer them.
       */
      if (NodeUtil.isInSyntheticScript(n) || !isRootOfQualifiedNameGlobal(t, n)) {
        return ConformanceResult.CONFORMANCE;
      }

      for (int i = this.qualifiedNames.size() - 1; i >= 0; i--) {
        if (n.matchesQualifiedName(this.qualifiedNames.get(i))) {
          return ConformanceResult.VIOLATION;
        }
      }

      return ConformanceResult.CONFORMANCE;
    }

    private static boolean isRootOfQualifiedNameGlobal(NodeTraversal t, Node n) {
      String rootName = NodeUtil.getRootOfQualifiedName(n).getQualifiedName();
      Var v = t.getScope().getVar(rootName);
      // TODO(b/189382837): Turn the nullness check back into an assertion once the bug is fixed.
      return v != null && v.isGlobal();
    }
  }

  /** Banned property rule */
  static final class BannedProperty extends AbstractRule {

    private enum RequirementPrecondition implements Precondition {
      BANNED_PROPERTY() {
        @Override
        public boolean shouldCheck(Node n) {
          return switch (n.getToken()) {
            case STRING_KEY, GETPROP, GETELEM, COMPUTED_PROP -> true;
            default -> false;
          };
        }
      },
      BANNED_PROPERTY_WRITE() {
        @Override
        public boolean shouldCheck(Node n) {
          return NodeUtil.isLValue(n);
        }
      },
      BANNED_PROPERTY_NON_CONSTANT_WRITE() {
        @Override
        public boolean shouldCheck(Node n) {
          if (!NodeUtil.isLValue(n)) {
            return false;
          }
          if (NodeUtil.isLhsOfAssign(n)
              && (NodeUtil.isLiteralValue(n.getNext(), /* includeFunctions= */ false)
                  || NodeUtil.isSomeCompileTimeConstStringValue(n.getNext()))) {
            return false;
          }
          return true;
        }
      },
      BANNED_PROPERTY_READ() {
        @Override
        public boolean shouldCheck(Node n) {
          return !NodeUtil.isLValue(n) && NodeUtil.isExpressionResultUsed(n);
        }
      },
      BANNED_PROPERTY_CALL() {
        @Override
        public boolean shouldCheck(Node n) {
          return ConformanceUtil.isCallTarget(n);
        }
      },
    }

    private final JSTypeRegistry registry;
    private final ImmutableSetMultimap<String, JSType> props;
    private final RequirementPrecondition requirementPrecondition;

    BannedProperty(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);

      if (requirement.getValueCount() == 0) {
        throw new InvalidRequirementSpec("missing value");
      }

      this.requirementPrecondition =
          switch (requirement.getType()) {
            case BANNED_PROPERTY -> RequirementPrecondition.BANNED_PROPERTY;
            case BANNED_PROPERTY_READ -> RequirementPrecondition.BANNED_PROPERTY_READ;
            case BANNED_PROPERTY_WRITE -> RequirementPrecondition.BANNED_PROPERTY_WRITE;
            case BANNED_PROPERTY_NON_CONSTANT_WRITE ->
                RequirementPrecondition.BANNED_PROPERTY_NON_CONSTANT_WRITE;
            case BANNED_PROPERTY_CALL -> RequirementPrecondition.BANNED_PROPERTY_CALL;
            default -> throw new AssertionError(requirement.getType());
          };

      this.registry = compiler.getTypeRegistry();

      ImmutableSetMultimap.Builder<String, JSType> builder = ImmutableSetMultimap.builder();
      for (String value : requirement.getValueList()) {
        String typename = ConformanceUtil.getClassFromDeclarationName(value);
        String property = ConformanceUtil.getPropertyFromDeclarationName(value);
        if (typename == null || property == null) {
          throw new InvalidRequirementSpec("bad prop value");
        }

        JSType type = registry.getGlobalType(typename);
        if (type == null) {
          type = registry.resolveViaClosureNamespace(typename);
        }

        // If type doesn't exist in the copmilation, it can't be a violation. Also, bottom types
        // match everything, which is almost surely not what the check is intended to check against.
        if (type == null || type.isUnknownType() || type.isEmptyType()) {
          continue;
        }

        builder.put(property, type);
      }
      this.props = builder.build();
    }

    @Override
    public final Precondition getPrecondition() {
      return this.requirementPrecondition;
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      ImmutableSet<JSType> checkTypes = this.props.get(this.extractName(n));
      if (checkTypes.isEmpty()) {
        return ConformanceResult.CONFORMANCE;
      }

      /*
       * Avoid type operations when possible.
       *
       * <p>checkTypes is almost always empty, and operations on unions can be expensive.
       */
      JSType foundType = this.extractType(n);
      if (foundType == null) {
        return ConformanceResult.CONFORMANCE;
      }
      foundType = foundType.restrictByNotNullOrUndefined();

      for (JSType checkType : checkTypes) {
        ConformanceResult result = this.matchTypes(foundType, checkType);
        if (result.level != ConformanceLevel.CONFORMANCE) {
          return result;
        }
      }

      return ConformanceResult.CONFORMANCE;
    }

    private ConformanceResult matchTypes(JSType foundType, JSType checkType) {
      ObjectType foundObj = foundType.toMaybeObjectType();
      if (foundObj != null) {
        if (foundObj.isFunctionPrototypeType()) {
          FunctionType ownerFun = foundObj.getOwnerFunction();
          if (ownerFun.isConstructor()) {
            foundType = ownerFun.getInstanceType();
          }
        } else if (foundObj.isTemplatizedType()) {
          foundType = foundObj.getRawType();
        }
      }

      if (foundType.isUnknownType()
          || foundType.isTemplateType()
          || foundType.isEmptyType()
          || foundType.isAllType()
          || isLooseObject(foundType, this.registry)) {
        if (reportLooseTypeViolations) {
          return ConformanceResult.POSSIBLE_VIOLATION_DUE_TO_LOOSE_TYPES;
        }
      } else if (foundType.isSubtypeOf(checkType)) {
        return ConformanceResult.VIOLATION;
      } else if (checkType.isSubtypeWithoutStructuralTyping(foundType)) {
        if (matchesPrototype(checkType, foundType)) {
          return ConformanceResult.VIOLATION;
        } else if (reportLooseTypeViolations) {
          // Access of a banned property through a super class may be a violation
          return ConformanceResult.POSSIBLE_VIOLATION_DUE_TO_LOOSE_TYPES;
        }
      }

      return ConformanceResult.CONFORMANCE;
    }

    private boolean matchesPrototype(JSType type, JSType maybePrototype) {
      ObjectType methodClassObjectType = type.toMaybeObjectType();
      if (methodClassObjectType != null) {
        if (methodClassObjectType.getImplicitPrototype().equals(maybePrototype)) {
          return true;
        }
      }
      return false;
    }

    private @Nullable JSType extractType(Node n) {
      switch (n.getToken()) {
        case GETELEM, GETPROP -> {
          return n.getFirstChild().getJSType();
        }
        case STRING_KEY, COMPUTED_PROP -> {
          Node parent = n.getParent();
          return switch (parent.getToken()) {
            case OBJECT_PATTERN, OBJECTLIT -> parent.getJSType();
            case CLASS_MEMBERS -> null;
            default -> throw new AssertionError();
          };
        }
        default -> {
          return null;
        }
      }
    }

    private @Nullable String extractName(Node n) {

      switch (n.getToken()) {
        case GETPROP, STRING_KEY -> {
          return n.getString();
        }
        case GETELEM -> {
          Node string = n.getSecondChild();
          return string.isStringLit() ? string.getString() : null;
        }
        case COMPUTED_PROP -> {
          Node string = n.getFirstChild();
          return string.isStringLit() ? string.getString() : null;
        }
        default -> {
          return null;
        }
      }
    }
  }

  /** Banned string rule */
  static final class BannedStringRegex extends AbstractRule {
    private final Pattern stringPattern;

    public BannedStringRegex(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);

      if (requirement.getValueCount() == 0) {
        throw new InvalidRequirementSpec("missing value");
      }

      ImmutableList.Builder<String> builder = ImmutableList.builder();
      for (String value : requirement.getValueList()) {
        if (value.trim().isEmpty()) {
          throw new InvalidRequirementSpec("empty strings or whitespace are not allowed");
        }
        builder.add(value);
      }

      ImmutableList<String> values = builder.build();
      if (values.isEmpty()) {
        throw new InvalidRequirementSpec("missing value");
      }
      Pattern stringRegex = buildPattern(values);
      this.stringPattern = stringRegex;
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal traversal, Node node) {
      if (node == null) {
        return ConformanceResult.CONFORMANCE;
      }
      if (node.isStringLit()) {
        if (this.stringPattern.matcher(node.getString()).matches()) {
          return ConformanceResult.VIOLATION;
        }
      } else if (node.isTemplateLitString()) {
        String cookedString = node.getCookedString();
        if (cookedString != null) {
          if (this.stringPattern.matcher(cookedString).matches()) {
            return ConformanceResult.VIOLATION;
          }
        } else {
          checkState(
              isTaggedTemplateLitString(node),
              "Found untagged template literal with invalid (uncookable) escape sequence: %s",
              node.getRawString());
          String rawString = node.getRawString();
          if (this.stringPattern.matcher(rawString).matches()) {
            return ConformanceResult.VIOLATION;
          }
        }
      }
      return ConformanceResult.CONFORMANCE;
    }
  }

  /** Is this template literal string a tagged template literal string? */
  private static boolean isTaggedTemplateLitString(Node node) {
    checkState(node.isTemplateLitString());
    return node.getGrandparent() != null && node.getGrandparent().isTaggedTemplateLit();
  }

  private static class ConformanceUtil {

    static boolean isCallTarget(Node n) {
      Node parent = n.getParent();
      return (parent.isCall() || parent.isNew()) && parent.getFirstChild() == n;
    }

    static boolean isLooseType(JSType type) {
      return type.isUnknownType() || type.isNoResolvedType() || type.isAllType();
    }

    static JSType evaluateTypeString(AbstractCompiler compiler, String expression)
        throws InvalidRequirementSpec {
      Node typeNodes = JsDocInfoParser.parseTypeString(expression);
      if (typeNodes == null) {
        throw new InvalidRequirementSpec("bad type expression");
      }
      JSTypeExpression typeExpr = new JSTypeExpression(typeNodes, "conformance");
      return compiler.getTypeRegistry().evaluateTypeExpressionInGlobalScope(typeExpr);
    }

    /**
     * Validate the parameters and the 'this' type, of a new or call.
     *
     * @see TypeCheck#visitParameterList
     */
    static boolean validateCall(
        AbstractCompiler compiler,
        Node callOrNew,
        FunctionType functionType,
        boolean isCallInvocation) {
      checkState(callOrNew.isCall() || callOrNew.isNew());

      return validateParameterList(compiler, callOrNew, functionType, isCallInvocation)
          && validateThis(callOrNew, functionType, isCallInvocation);
    }

    private static boolean validateThis(
        Node callOrNew, FunctionType functionType, boolean isCallInvocation) {

      if (callOrNew.isNew()) {
        return true;
      }

      JSType thisType = functionType.getTypeOfThis();
      if (thisType == null || thisType.isUnknownType()) {
        return true;
      }

      Node thisNode =
          isCallInvocation ? callOrNew.getSecondChild() : callOrNew.getFirstFirstChild();
      JSType thisNodeType = thisNode.getJSType().restrictByNotNullOrUndefined();
      return thisNodeType.isSubtypeOf(thisType);
    }

    private static boolean validateParameterList(
        AbstractCompiler compiler,
        Node callOrNew,
        FunctionType functionType,
        boolean isCallInvocation) {
      Node arg = callOrNew.getSecondChild(); // skip the function name
      if (isCallInvocation && arg != null) {
        arg = arg.getNext();
      }

      // Get all the annotated types of the argument nodes
      ImmutableList.Builder<JSType> argumentTypes = ImmutableList.builder();
      for (; arg != null; arg = arg.getNext()) {
        JSType argType = arg.getJSType();
        if (argType == null) {
          argType = compiler.getTypeRegistry().getNativeType(JSTypeNative.UNKNOWN_TYPE);
        }
        argumentTypes.add(argType);
      }
      return functionType.acceptsArguments(argumentTypes.build());
    }

    /** Extracts the method name from a provided name. */
    private static @Nullable String getPropertyFromDeclarationName(String specName) {
      List<String> parts = ON_PROTOTYPE.splitToList(specName);
      checkState(parts.size() == 1 || parts.size() == 2);
      if (parts.size() == 2) {
        return parts.get(1);
      }
      return null;
    }

    /** Extracts the class name from a provided name. */
    private static @Nullable String getClassFromDeclarationName(String specName) {
      String tmp = specName;
      List<String> parts = ON_PROTOTYPE.splitToList(tmp);
      checkState(parts.size() == 1 || parts.size() == 2);
      if (parts.size() == 2) {
        return parts.get(0);
      }
      return null;
    }

    private static String removeTypeDecl(String specName) throws InvalidRequirementSpec {
      int index = specName.indexOf(':');
      if (index < 1) {
        throw new InvalidRequirementSpec("value should be in the form NAME:TYPE");
      }
      return specName.substring(0, index);
    }

    private static @Nullable String getTypeFromValue(String specName) {
      int index = specName.indexOf(':');
      if (index < 1) {
        return null;
      }
      return specName.substring(index + 1);
    }

    private static boolean isAnnotatedAsConst(JSDocInfo jsdoc) {
      return jsdoc != null && jsdoc.isConstant();
    }

    private static @Nullable String getValueFromGlobalName(
        @Nullable Scope scope, GlobalNamespace gns, String qualifiedName) {
      GlobalNamespace.Name gn = gns.getOwnSlot(qualifiedName);
      if (gn == null
          || gn.getGlobalSets() != 1
          || gn.getTotalSets() != 1
          || !isAnnotatedAsConst(gn.getJSDocInfo())) {
        return null;
      }

      return inferStringValue(
          scope, NodeUtil.getRValueOfLValue(gn.getDeclaration().getNode()), () -> gns);
    }

    private static @Nullable String inferStringValue(
        @Nullable Scope scope, Node node, Supplier<GlobalNamespace> globalNamespaceSupplier) {
      if (node == null) {
        return null;
      }

      switch (node.getToken()) {
        case STRING_KEY, STRINGLIT, TEMPLATELIT, TEMPLATELIT_STRING -> {
          return NodeUtil.getStringValue(node);
        }
        case NAME -> {
          if (scope == null) {
            return null;
          }
          String name = node.getString();
          Var var = scope.getVar(name);
          if (var == null) {
            return null;
          }
          if (!var.isConst() && !isAnnotatedAsConst(var.getJSDocInfo())) {
            return null;
          }
          Node initialValue = var.getInitialValue();
          return inferStringValue(var.getScope(), initialValue, globalNamespaceSupplier);
        }
        case GETPROP -> {
          JSType type = node.getJSType();
          if (type == null) {
            return null;
          }

          // The JS style guide requires enums to be effectively immutable and all enum items should
          // be statically known. See go/js-style#features-objects-enums.
          if (type.isEnumElementType()) {
            Node enumSource = type.toMaybeEnumElementType().getEnumType().getSource();
            if (enumSource == null) {
              return null;
            }
            if (!enumSource.isObjectLit() && !enumSource.isClassMembers()) {
              return null;
            }
            return inferStringValue(
                null,
                NodeUtil.getFirstPropMatchingKey(enumSource, node.getString()),
                globalNamespaceSupplier);
          } else if (type.isString()) {
            GlobalNamespace gns = globalNamespaceSupplier.get();
            if (gns == null) {
              return null;
            }
            return getValueFromGlobalName(scope, gns, node.getQualifiedName());
          }
          return null;
        }
        default -> {
          // Do nothing
        }
      }
      return null;
    }

    private static boolean isXid(JSType type) {
      if (type == null) {
        return false;
      }
      EnumElementType enumElTy = type.toMaybeEnumElementType();
      if (enumElTy != null
          && enumElTy.getEnumType().getReferenceName().equals("enum{xid.String}")) {
        return true;
      }
      return false;
    }

    private static boolean isEventHandlerAttrName(String attr) {
      return !attr.equals("on") && attr.startsWith("on");
    }
  }

  /** Restricted name call rule */
  static class RestrictedNameCall extends AbstractRule {
    private static class Restriction {
      final Node name;
      final FunctionType restrictedCallType;

      Restriction(Node name, FunctionType restrictedCallType) {
        this.name = name;
        this.restrictedCallType = restrictedCallType;
      }
    }

    private final ImmutableList<Restriction> restrictions;

    RestrictedNameCall(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
      if (requirement.getValueCount() == 0) {
        throw new InvalidRequirementSpec("missing value");
      }

      ImmutableList.Builder<Restriction> builder = ImmutableList.builder();
      for (String value : requirement.getValueList()) {
        Node name = NodeUtil.newQName(compiler, getNameFromValue(value));
        String restrictedDecl = ConformanceUtil.getTypeFromValue(value);
        if (name == null || restrictedDecl == null) {
          throw new InvalidRequirementSpec("bad prop value");
        }

        FunctionType restrictedCallType =
            ConformanceUtil.evaluateTypeString(compiler, restrictedDecl).toMaybeFunctionType();
        if (restrictedCallType == null) {
          throw new InvalidRequirementSpec("invalid conformance type");
        }
        builder.add(new Restriction(name, restrictedCallType));
      }
      restrictions = builder.build();
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      if (ConformanceUtil.isCallTarget(n) && n.isQualifiedName()) {
        // TODO(johnlenz): restrict to global names
        for (int i = 0; i < restrictions.size(); i++) {
          Restriction r = restrictions.get(i);

          if (n.matchesQualifiedName(r.name)) {
            if (!ConformanceUtil.validateCall(
                compiler, n.getParent(), r.restrictedCallType, false)) {
              return ConformanceResult.VIOLATION;
            }
          } else if (n.isGetProp()
              && n.getString().equals("call")
              && n.getFirstChild().matchesQualifiedName(r.name)) {
            if (!ConformanceUtil.validateCall(
                compiler, n.getParent(), r.restrictedCallType, true)) {
              return ConformanceResult.VIOLATION;
            }
          }
        }
      }
      return ConformanceResult.CONFORMANCE;
    }

    private static @Nullable String getNameFromValue(String specName) {
      int index = specName.indexOf(':');
      if (index < 1) {
        return null;
      }
      return specName.substring(0, index);
    }
  }

  /** Banned property call rule */
  static class RestrictedMethodCall extends AbstractRule {
    private static class Restriction {
      final JSType type;
      final String property;
      final FunctionType restrictedCallType;

      Restriction(JSType type, String property, FunctionType restrictedCallType) {
        this.type = type;
        this.property = property;
        this.restrictedCallType = restrictedCallType;
      }
    }

    private final ImmutableList<Restriction> restrictions;

    RestrictedMethodCall(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);

      if (requirement.getValueCount() == 0) {
        throw new InvalidRequirementSpec("missing value");
      }

      JSTypeRegistry registry = compiler.getTypeRegistry();
      ImmutableList.Builder<Restriction> builder = ImmutableList.builder();
      for (String value : requirement.getValueList()) {
        String type =
            ConformanceUtil.getClassFromDeclarationName(ConformanceUtil.removeTypeDecl(value));
        String property =
            ConformanceUtil.getPropertyFromDeclarationName(ConformanceUtil.removeTypeDecl(value));
        String restrictedDecl = ConformanceUtil.getTypeFromValue(value);
        if (type == null || property == null || restrictedDecl == null) {
          throw new InvalidRequirementSpec("bad prop value");
        }

        FunctionType restrictedCallType =
            ConformanceUtil.evaluateTypeString(compiler, restrictedDecl).toMaybeFunctionType();
        if (restrictedCallType == null) {
          throw new InvalidRequirementSpec("invalid conformance type");
        }
        builder.add(new Restriction(registry.getGlobalType(type), property, restrictedCallType));
      }

      restrictions = builder.build();
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      if (!n.isGetProp() || !ConformanceUtil.isCallTarget(n)) {
        return ConformanceResult.CONFORMANCE;
      }

      for (int i = 0; i < restrictions.size(); i++) {
        Restriction r = restrictions.get(i);
        ConformanceResult result = ConformanceResult.CONFORMANCE;

        if (matchesProp(n, r)) {
          result = checkConformance(t, n, r, false);
        } else if (n.getString().equals("call") && matchesProp(n.getFirstChild(), r)) {
          // handle .call invocation
          result = checkConformance(t, n, r, true);
        }

        // TODO(johnlenz): should "apply" always be a possible violation?
        if (result.level != ConformanceLevel.CONFORMANCE) {
          return result;
        }
      }

      return ConformanceResult.CONFORMANCE;
    }

    private ConformanceResult checkConformance(
        NodeTraversal t, Node n, Restriction r, boolean isCallInvocation) {
      JSTypeRegistry registry = t.getCompiler().getTypeRegistry();
      JSType methodClassType = r.type;
      Node lhs = isCallInvocation ? n.getFirstFirstChild() : n.getFirstChild();
      if (methodClassType != null && lhs.getJSType() != null) {
        JSType targetType = lhs.getJSType().restrictByNotNullOrUndefined();
        if (ConformanceUtil.isLooseType(targetType) || isLooseObject(targetType, registry)) {
          if (reportLooseTypeViolations
              && !ConformanceUtil.validateCall(
                  compiler, n.getParent(), r.restrictedCallType, isCallInvocation)) {
            return ConformanceResult.POSSIBLE_VIOLATION_DUE_TO_LOOSE_TYPES;
          }
        } else if (targetType.isSubtypeOf(methodClassType)) {
          if (!ConformanceUtil.validateCall(
              compiler, n.getParent(), r.restrictedCallType, isCallInvocation)) {
            return ConformanceResult.VIOLATION;
          }
        }
      }
      return ConformanceResult.CONFORMANCE;
    }

    private boolean matchesProp(Node n, Restriction r) {
      return n.isGetProp() && n.getString().equals(r.property);
    }
  }

  /** Restricted property write. */
  static class RestrictedPropertyWrite extends AbstractRule {
    private static class Restriction {
      final JSType type;
      final String property;
      final JSType restrictedType;

      Restriction(JSType type, String property, JSType restrictedType) {
        this.type = type;
        this.property = property;
        this.restrictedType = restrictedType;
      }
    }

    private final ImmutableList<Restriction> restrictions;

    RestrictedPropertyWrite(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);

      if (requirement.getValueCount() == 0) {
        throw new InvalidRequirementSpec("missing value");
      }

      JSTypeRegistry registry = compiler.getTypeRegistry();
      ImmutableList.Builder<Restriction> builder = ImmutableList.builder();
      for (String value : requirement.getValueList()) {
        String type =
            ConformanceUtil.getClassFromDeclarationName(ConformanceUtil.removeTypeDecl(value));
        String property =
            ConformanceUtil.getPropertyFromDeclarationName(ConformanceUtil.removeTypeDecl(value));
        String restrictedDecl = ConformanceUtil.getTypeFromValue(value);
        if (type == null || property == null || restrictedDecl == null) {
          throw new InvalidRequirementSpec("bad prop value");
        }
        JSType restrictedType = ConformanceUtil.evaluateTypeString(compiler, restrictedDecl);
        builder.add(new Restriction(registry.getGlobalType(type), property, restrictedType));
      }

      restrictions = builder.build();
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      if (n.isGetProp() && NodeUtil.isLhsOfAssign(n)) {
        JSType rhsType = n.getNext().getJSType();
        JSType targetType = n.getFirstChild().getJSType();
        if (rhsType != null && targetType != null) {
          JSType targetNotNullType = null;
          for (Restriction r : restrictions) {
            if (n.getString().equals(r.property)) {
              if (!rhsType.isSubtypeOf(r.restrictedType)) {
                if (ConformanceUtil.isLooseType(targetType)) {
                  if (reportLooseTypeViolations) {
                    return ConformanceResult.POSSIBLE_VIOLATION_DUE_TO_LOOSE_TYPES;
                  }
                } else {
                  if (targetNotNullType == null) {
                    targetNotNullType = targetType.restrictByNotNullOrUndefined();
                  }
                  if (targetNotNullType.isSubtypeOf(r.type)) {
                    return ConformanceResult.VIOLATION;
                  }
                }
              }
            }
          }
        }
      }
      return ConformanceResult.CONFORMANCE;
    }
  }

  /** Banned Code Pattern rule */
  static class BannedCodePattern extends AbstractRule {
    private final ImmutableList<TemplateAstMatcher> restrictions;

    BannedCodePattern(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);

      if (requirement.getValueCount() == 0) {
        throw new InvalidRequirementSpec("missing value");
      }

      ImmutableList.Builder<TemplateAstMatcher> builder = ImmutableList.builder();
      for (String value : requirement.getValueList()) {
        Node parseRoot =
            new CompilerInput(SourceFile.fromCode("<template>", value)).getAstRoot(compiler);
        if (!parseRoot.hasOneChild() || !parseRoot.getFirstChild().isFunction()) {
          throw new InvalidRequirementSpec("invalid conformance template: " + value);
        }
        Node templateRoot = parseRoot.getFirstChild();
        TemplateAstMatcher astMatcher =
            new TemplateAstMatcher(compiler, templateRoot, typeMatchingStrategy);
        builder.add(astMatcher);
      }

      restrictions = builder.build();
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      boolean possibleViolation = false;
      for (int i = 0; i < restrictions.size(); i++) {
        TemplateAstMatcher matcher = restrictions.get(i);
        if (matcher.matches(n)) {
          if (matcher.isLooseMatch()) {
            possibleViolation = true;
          } else {
            return ConformanceResult.VIOLATION;
          }
        }
      }
      return possibleViolation && reportLooseTypeViolations
          ? ConformanceResult.POSSIBLE_VIOLATION_DUE_TO_LOOSE_TYPES
          : ConformanceResult.CONFORMANCE;
    }
  }

  /** A custom rule proxy, for rules that we load dynamically. */
  static class CustomRuleProxy implements Rule {
    final Rule customRule;

    CustomRuleProxy(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      if (!requirement.hasJavaClass()) {
        throw new InvalidRequirementSpec("missing java_class");
      }
      customRule = createRule(compiler, requirement);
    }

    @Override
    public void check(
        NodeTraversal t, Node n, LibraryLevelNonAllowlistedConformanceViolationsBehavior behavior) {
      customRule.check(t, n, behavior);
    }

    private Rule createRule(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      try {
        Class<Rule> custom = getRuleClass(requirement.getJavaClass());
        Constructor<?> ctor = getRuleConstructor(custom);
        Rule rule;
        try {
          rule = (Rule) (ctor.newInstance(compiler, requirement));
        } catch (InvocationTargetException e) {
          Throwable cause = e.getCause();
          if (cause instanceof InvalidRequirementSpec invalidRequirementSpec) {
            throw invalidRequirementSpec;
          }
          throw new RuntimeException(cause);
        }
        return rule;
      } catch (InstantiationException | IllegalAccessException | IllegalArgumentException e) {
        throw new RuntimeException(e);
      }
    }

    private Constructor<?> getRuleConstructor(Class<Rule> cls) throws InvalidRequirementSpec {
      for (Constructor<?> ctor : cls.getConstructors()) {
        Class<?>[] paramClasses = ctor.getParameterTypes();
        if (paramClasses.length == 2) {
          TypeToken<?> param1 = TypeToken.of(paramClasses[0]);
          TypeToken<?> param2 = TypeToken.of(paramClasses[1]);
          if (param1.isSupertypeOf(COMPILER_TYPE) && param2.isSupertypeOf(REQUIREMENT_TYPE)) {
            return ctor;
          }
        }
      }

      throw new InvalidRequirementSpec("No valid class constructors found.");
    }

    private static final TypeToken<Rule> RULE_TYPE = new TypeToken<Rule>() {};

    private static final TypeToken<AbstractCompiler> COMPILER_TYPE =
        new TypeToken<AbstractCompiler>() {};

    private static final TypeToken<Requirement> REQUIREMENT_TYPE = new TypeToken<Requirement>() {};

    private Class<Rule> getRuleClass(String className) throws InvalidRequirementSpec {
      Class<?> customClass;
      try {
        customClass = Class.forName(className);
      } catch (ClassNotFoundException e) {
        throw new InvalidRequirementSpec("JavaClass not found.", e);
      }
      if (RULE_TYPE.isSupertypeOf(TypeToken.of(customClass))) {
        @SuppressWarnings("unchecked") // Assignable to Rule;
        Class<Rule> ruleClass = (Class<Rule>) customClass;
        return ruleClass;
      }
      throw new InvalidRequirementSpec("JavaClass is not a rule.");
    }
  }

  /** Banned for/of loops */
  public static final class BanForOf extends AbstractRule {
    public BanForOf(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      if (n.isForOf() || n.isForAwaitOf()) {
        return ConformanceResult.VIOLATION;
      }
      return ConformanceResult.CONFORMANCE;
    }
  }

  /** Require "use strict" rule */
  public static class RequireUseStrict extends AbstractRule {

    public RequireUseStrict(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
      if (!requirement.getValueList().isEmpty()) {
        throw new InvalidRequirementSpec("invalid value");
      }
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      return (n.isScript() && !n.isUseStrict())
          ? ConformanceResult.VIOLATION
          : ConformanceResult.CONFORMANCE;
    }
  }

  /** Banned throw of non-error object types. */
  public static final class BanThrowOfNonErrorTypes extends AbstractRule {
    final JSType errorObjType;

    public BanThrowOfNonErrorTypes(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
      errorObjType = compiler.getTypeRegistry().getGlobalType("Error");
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      if (errorObjType != null && n.isThrow()) {
        JSType thrown = n.getFirstChild().getJSType();
        if (thrown != null) {
          // Allow vague types, as is typical of re-throws of exceptions
          if (!thrown.isUnknownType()
              && !thrown.isAllType()
              && !thrown.isEmptyType()
              && !thrown.isSubtypeOf(errorObjType)) {
            return ConformanceResult.VIOLATION;
          }
        }
      }
      return ConformanceResult.CONFORMANCE;
    }
  }

  /** Banned dereferencing null or undefined types. */
  public static final class BanNullDeref extends AbstractTypeRestrictionRule {
    public BanNullDeref(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {

      boolean violation =
          switch (n.getToken()) {
            case GETPROP, GETELEM, NEW, CALL -> report(n.getFirstChild());
            case IN -> report(n.getLastChild());
            default -> false;
          };

      return violation ? ConformanceResult.VIOLATION : ConformanceResult.CONFORMANCE;
    }

    boolean report(Node n) {
      return n.getJSType() != null && isKnown(n) && invalidDeref(n) && !isAllowlistedType(n);
    }

    // Whether the type is known to be invalid to dereference.
    private boolean invalidDeref(Node n) {
      JSType type = n.getJSType();
      // TODO(johnlenz): top type should not be allowed here
      return !type.isAllType() && (type.isNullable() || type.isVoidable());
    }
  }

  /** Banned unknown "this" types. */
  public static final class BanUnknownThis extends AbstractTypeRestrictionRule {
    private final LinkedIdentityHashSet<Node> reports = new LinkedIdentityHashSet<>();

    public BanUnknownThis(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
    }

    @Override
    protected boolean tsIsAllowlisted() {
      // We expect TypeScript to already check it.
      return true;
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      if (n.isThis()) {
        JSType type = n.getJSType();
        if (type != null && type.isUnknownType() && !isTypeImmediatelyTightened(n)) {
          Node root = t.getScopeRoot();
          if (!reports.contains(root)) {
            reports.add(root);
            return ConformanceResult.VIOLATION;
          }
        }
      }
      return ConformanceResult.CONFORMANCE;
    }
  }

  /**
   * Banned unknown type references of the form "this.prop" unless
   * <li>it is immediately cast,
   * <li>it is a @template type (until template type restricts are enabled) or
   * <li>the value is unused.
   * <li>the "this" type is unknown (as this is expected to be used with BanUnknownThis which would
   *     have already reported the root cause).
   */
  public static final class BanUnknownDirectThisPropsReferences
      extends AbstractTypeRestrictionRule {
    public BanUnknownDirectThisPropsReferences(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      if (n.isGetProp()
          && isKnownThis(n.getFirstChild()) // not a cascading unknown
          && isUnknown(n)
          && !isTemplateType(n)
          && isUsed(n) // skip most assignments, etc
          && !isTypeImmediatelyTightened(n)
          && !isExplicitlyUnknown(n)) {
        return ConformanceResult.VIOLATION;
      }
      return ConformanceResult.CONFORMANCE;
    }

    private static boolean isKnownThis(Node n) {
      return n.isThis() && !isUnknown(n);
    }

    private static boolean isExplicitlyUnknown(Node n) {
      ObjectType owner = ObjectType.cast(n.getFirstChild().getJSType());
      Property prop = owner != null ? owner.getSlot(n.getString()) : null;
      return prop != null && !prop.isTypeInferred();
    }
  }

  /**
   * Banned non-literal arguments passed to {@code goog.string.Const.from}. Possible calls to
   * goog.string.Const in user code could look like:
   *
   * <pre>
   * `goog.string.Const.from('foo');`
   * `const alias = goog.string.Const.from; alias(foo);`
   * `const {from} = goog.require('goog.string.Const'); from(foo);`
   * `const alias = goog.require('goog.string.Const'); alias.from(foo);`
   * </pre>
   */
  public static final class BanNonLiteralArgsToGoogStringConstFrom extends AbstractRule {

    private static final QualifiedName GOOG_STRING_CONST_FROM =
        QualifiedName.of("goog.string.Const.from");

    public BanNonLiteralArgsToGoogStringConstFrom(
        AbstractCompiler compiler, Requirement requirement) throws InvalidRequirementSpec {
      super(compiler, requirement);
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node node) {
      if (node.isCall()) {
        Node name = node.getFirstChild();
        Node argument = name.getNext();
        if (argument == null) {
          return ConformanceResult.CONFORMANCE;
        }

        // If the name is a qualified name, it must be goog.string.Const.from or an alias of it.
        if (name.isName()) {
          Scope scope = t.getScope();
          Var var = scope.getVar(name.getString());
          if (var == null) {
            return ConformanceResult.CONFORMANCE;
          }
          name = var.getInitialValue();
          if (name == null) {
            return ConformanceResult.CONFORMANCE;
          }
        }

        if (GOOG_STRING_CONST_FROM.matches(name)) {
          if (!isAllowed(argument)) {
            return ConformanceResult.VIOLATION;
          }
        }
      }
      return ConformanceResult.CONFORMANCE;
    }

    private boolean isAllowed(Node argument) {
      return argument.isStringLit() || (argument.isTemplateLit() && argument.hasOneChild());
    }
  }

  /**
   * Banned unknown type references of the form "instance.prop" unless
   * <li>(a) it is immediately cast/asserted, or
   * <li>(b) it is a @template type (until template type restrictions are enabled), or
   * <li>(c) the value is unused, or
   * <li>(d) the source object type is unknown (to avoid error cascades)
   */
  public static final class BanUnknownTypedClassPropsReferences
      extends AbstractTypeRestrictionRule {

    public BanUnknownTypedClassPropsReferences(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
    }

    @Override
    protected boolean tsIsAllowlisted() {
      return true;
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node getprop) {
      if (getprop.isGetProp()
          && isUnknown(getprop)
          && isUsed(getprop) // skip most assignments, etc
          && !isTypeImmediatelyTightened(getprop)
          && isCheckablePropertySource(getprop.getFirstChild()) // not a cascading unknown
          && !isTemplateType(getprop)
          && !isDeclaredUnknown(getprop)) {
        String propName = getprop.getString();
        String typeName = getprop.getFirstChild().getJSType().toString();
        return new ConformanceResult(
            ConformanceLevel.VIOLATION,
            "The property \"" + propName + "\" on type \"" + typeName + "\"");
      }
      return ConformanceResult.CONFORMANCE;
    }

    private boolean isCheckablePropertySource(Node n) {
      return isKnown(n)
          && !isTop(n)
          && isClassType(n)
          && !isNativeObjectType(n)
          && !isAllowlistedType(n);
    }

    private boolean isClassType(Node n) {
      ObjectType type = n.getJSType().restrictByNotNullOrUndefined().toMaybeObjectType();
      if (type == null || !type.isInstanceType()) {
        return false;
      }

      FunctionType ctor = type.getConstructor();
      if (ctor == null) {
        return false;
      }

      JSDocInfo info = ctor.getJSDocInfo();
      Node source = ctor.getSource();

      return (info != null && info.isConstructorOrInterface())
          || (source != null && source.isClass());
    }

    private boolean isDeclaredUnknown(Node n) {
      Node target = n.getFirstChild();
      ObjectType targetType = target.getJSType().restrictByNotNullOrUndefined().toMaybeObjectType();
      if (targetType == null) {
        return false;
      }

      JSDocInfo info = targetType.getPropertyJSDocInfo(n.getString());
      if (info == null || !info.hasType()) {
        return false;
      }

      JSTypeExpression expr = info.getType();
      Node typeExprNode = expr.getRoot();
      if (typeExprNode.getToken() == Token.QMARK && !typeExprNode.hasChildren()) {
        return true;
      } else if (typeExprNode.getToken() == Token.PIPE) {
        // Might be a union type including ? that's collapsed during checking.
        for (Node child = typeExprNode.getFirstChild(); child != null; child = child.getNext()) {
          if (child.getToken() == Token.QMARK) {
            return true;
          }
        }
      }

      return false;
    }
  }

  /**
   * Banned accessing properties from objects that are unresolved forward-declared type names. For
   * legacy reasons this is allowed but causes unexpected weaknesses in the type inference.
   */
  public static final class BanUnresolvedType extends AbstractTypeRestrictionRule {
    public BanUnresolvedType(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      if (n.isGetProp()) {
        Node target = n.getFirstChild();
        JSType type = target.getJSType();
        String nonConformingPart = getNonConformingPart(type);
        if (nonConformingPart != null && !isTypeImmediatelyTightened(n)) {
          return new ConformanceResult(
              ConformanceLevel.VIOLATION,
              "Reference to type '" + nonConformingPart + "' never resolved.");
        }
      }
      return ConformanceResult.CONFORMANCE;
    }

    private static @Nullable String getNonConformingPart(JSType type) {
      if (type == null) {
        return null;
      }
      if (type.isUnionType()) {
        // unwrap union types which might contain unresolved type name
        // references for example {Foo|undefined}
        ArrayList<String> nonConformingParts = null;
        for (JSType part : type.getUnionMembers()) {
          String nonConformingPart = getNonConformingPart(part);
          if (nonConformingPart != null) {
            nonConformingParts =
                nonConformingParts != null ? nonConformingParts : new ArrayList<>();
            nonConformingParts.add(nonConformingPart);
          }
        }
        if (nonConformingParts != null) {
          return Joiner.on('|').join(nonConformingParts);
        }
      } else if (type.isNoResolvedType()) {
        ObjectType noResolvedType = type.toObjectType();
        return noResolvedType.getReferenceName();
      }
      return null;
    }
  }

  /** Ban any use of unresolved forward-declared types */
  public static final class StrictBanUnresolvedType extends AbstractTypeRestrictionRule {
    public StrictBanUnresolvedType(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      String nonConformingPart = BanUnresolvedType.getNonConformingPart(n.getJSType());
      if (nonConformingPart != null && !isTypeImmediatelyTightened(n)) {
        return new ConformanceResult(
            ConformanceLevel.VIOLATION,
            "Reference to type '" + nonConformingPart + "' never resolved.");
      }
      return ConformanceResult.CONFORMANCE;
    }
  }

  /** Banned global var declarations. */
  public static final class BanGlobalVars extends AbstractRule {
    private final ImmutableSet<String> allowlistedNames;

    public BanGlobalVars(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);

      this.allowlistedNames = ImmutableSet.copyOf(requirement.getValueList());
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      if (t.inGlobalScope()
          && NodeUtil.isDeclaration(n)
          && !n.getBooleanProp(Node.IS_NAMESPACE)
          && !isAllowlisted(n)) {
        Node enclosingScript = NodeUtil.getEnclosingScript(n);
        if (enclosingScript != null
            && (enclosingScript.getBooleanProp(Node.GOOG_MODULE)
                || enclosingScript.getBooleanProp(Node.ES6_MODULE)
                || enclosingScript.getInputId().equals(compiler.getSyntheticCodeInputId()))) {
          return ConformanceResult.CONFORMANCE;
        }
        return ConformanceResult.VIOLATION;
      }
      return ConformanceResult.CONFORMANCE;
    }

    private boolean isAllowlisted(Node n) {
      if (n.isFromExterns()) {
        return true;
      }

      if (n.isFunction()) {
        return isAllowlistedName(n.getFirstChild().getString());
      }

      if (NodeUtil.isNameDeclaration(n)) {
        boolean[] allowlisted = {true}; // for lambda access
        NodeUtil.visitLhsNodesInNode(
            n,
            (name) -> {
              if (!isAllowlistedName(name.getString())) {
                allowlisted[0] = false;
              }
            });
        return allowlisted[0];
      }

      return false;
    }

    private boolean isAllowlistedName(String name) {
      if (this.allowlistedNames.contains(name)) {
        return true;
      }
      // names created by JSCompiler internals
      return name.equals("$jscomp")
          || name.startsWith("$jscomp$compprop")
          || ClosureRewriteModule.isModuleContent(name)
          || ClosureRewriteModule.isModuleExport(name)
          || ScopedAliases.isScopedAliases(name);
    }
  }

  /** Checks that file does not include an @enhance annotation for a banned namespace. */
  public static final class BannedEnhance extends AbstractRule {
    private final ImmutableSet<String> bannedEnhancedNamespaces;

    public BannedEnhance(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
      if (requirement.getValueCount() == 0) {
        throw new InvalidRequirementSpec("missing value");
      }
      this.bannedEnhancedNamespaces = ImmutableSet.copyOf(requirement.getValueList());
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      JSDocInfo docInfo = n.getJSDocInfo();

      if (docInfo == null || !docInfo.hasEnhance()) {
        return ConformanceResult.CONFORMANCE;
      }

      for (String banned : this.bannedEnhancedNamespaces) {
        if (docInfo.getEnhance().equals(banned)) {
          return new ConformanceResult(
              ConformanceLevel.VIOLATION, "The enhanced namespace \"" + banned + "\"");
        }
      }
      return ConformanceResult.CONFORMANCE;
    }

    @Override
    public final Precondition getPrecondition() {
      return Node::isScript;
    }
  }

  /** Checks that file does not include an @mods annotation for a banned namespace regex. */
  public static final class BannedModsRegex extends AbstractRule {
    private final Pattern bannedModsRegex;

    public BannedModsRegex(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);

      List<String> bannedModsRegexList = requirement.getValueList();
      if (requirement.getValueCount() == 0) {
        throw new InvalidRequirementSpec("missing value");
      }
      bannedModsRegex = buildPattern(bannedModsRegexList);
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      JSDocInfo docInfo = n.getJSDocInfo();

      if (docInfo == null || !docInfo.hasMods()) {
        return ConformanceResult.CONFORMANCE;
      }

      if (bannedModsRegex.matcher(docInfo.getMods()).find()) {
        return ConformanceResult.VIOLATION;
      }
      return ConformanceResult.CONFORMANCE;
    }

    @Override
    public final Precondition getPrecondition() {
      return Node::isScript;
    }
  }

  private static final QualifiedName GOOG_DOM = QualifiedName.of("goog.dom");
  private static final QualifiedName GOOG_DOM_TAGNAME = QualifiedName.of("goog.dom.TagName");

  /**
   * Bans {@code document.createElement} and similar methods with string literal parameter specified
   * in {@code value}, e.g. {@code value: 'script'}. The purpose of banning these is that they don't
   * provide the type information which hinders other rules. Authors should use e.g. {@code
   * goog.dom.createElement(goog.dom.TagName.SCRIPT)} which returns HTMLScriptElement.
   */
  public static final class BanCreateElement extends AbstractRule {
    private final Set<String> bannedTags;
    private final JSType domHelperType;
    private final JSType documentType;

    public BanCreateElement(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
      bannedTags = new LinkedHashSet<>();
      for (String value : requirement.getValueList()) {
        bannedTags.add(Ascii.toLowerCase(value));
      }
      if (bannedTags.isEmpty()) {
        throw new InvalidRequirementSpec("Specify one or more values.");
      }
      domHelperType = compiler.getTypeRegistry().getGlobalType("goog.dom.DomHelper");
      documentType = compiler.getTypeRegistry().getGlobalType("Document");
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      if (n.isCall()) {
        Node tag = n.getSecondChild();
        if (tag != null
            && tag.isStringLit()
            && bannedTags.contains(Ascii.toLowerCase(tag.getString()))) {
          return checkCreateElement(n);
        }
      }
      return ConformanceResult.CONFORMANCE;
    }

    private ConformanceResult checkCreateElement(Node n) {
      Node target = n.getFirstChild();
      if (!target.isGetProp()) {
        return ConformanceResult.CONFORMANCE;
      }
      String functionName = target.getString();
      if (!"createElement".equals(functionName) && !"createDom".equals(functionName)) {
        return ConformanceResult.CONFORMANCE;
      }

      Node srcObj = target.getFirstChild();
      if (GOOG_DOM.matches(srcObj)) {
        return ConformanceResult.VIOLATION;
      }
      JSType type = srcObj.getJSType();
      if (type == null || ConformanceUtil.isLooseType(type)) {
        return reportLooseTypeViolations
            ? ConformanceResult.POSSIBLE_VIOLATION_DUE_TO_LOOSE_TYPES
            : ConformanceResult.CONFORMANCE;
      }
      if ((domHelperType != null && domHelperType.isSubtypeOf(type))
          || (documentType != null && documentType.isSubtypeOf(type))) {
        return ConformanceResult.VIOLATION;
      }
      return ConformanceResult.CONFORMANCE;
    }
  }

  /**
   * Ban {@code goog.dom.createDom} and {@code goog.dom.DomHelper#createDom} with parameters
   * specified in {@code value} in the format tagname.attribute, e.g. {@code value: 'iframe.src'}.
   * Tag name might be also {@code *} to ban the attribute in any tag. Note that string literal
   * values assigned to banned attributes are allowed as they couldn't be attacker controlled.
   */
  public static final class BanCreateDom extends AbstractRule {
    private final ImmutableList<String[]> bannedTagAttrs;
    private final JSType domHelperType;
    private final JSType classNameTypes;

    public BanCreateDom(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
      ImmutableList.Builder<String[]> bannedTagAttrs = ImmutableList.builder();
      for (String value : requirement.getValueList()) {
        List<String> tagAttr = ON_DOT.splitToList(value);
        if (tagAttr.size() != 2 || tagAttr.get(0).isEmpty() || tagAttr.get(1).isEmpty()) {
          throw new InvalidRequirementSpec("Values must be in the format tagname.attribute.");
        }
        String lowercasedTag = tagAttr.get(0).toLowerCase(Locale.ROOT);
        bannedTagAttrs.add(new String[] {lowercasedTag, tagAttr.get(1)});
      }
      this.bannedTagAttrs = bannedTagAttrs.build();
      if (this.bannedTagAttrs.isEmpty()) {
        throw new InvalidRequirementSpec("Specify one or more values.");
      }
      domHelperType = compiler.getTypeRegistry().getGlobalType("goog.dom.DomHelper");
      classNameTypes =
          compiler
              .getTypeRegistry()
              .createUnionType(
                  compiler.getTypeRegistry().getNativeType(JSTypeNative.STRING_TYPE),
                  compiler.getTypeRegistry().getNativeType(JSTypeNative.ARRAY_TYPE),
                  compiler.getTypeRegistry().getNativeType(JSTypeNative.NULL_TYPE),
                  compiler.getTypeRegistry().getNativeType(JSTypeNative.VOID_TYPE));
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      if (!isCreateDomCall(n)) {
        return ConformanceResult.CONFORMANCE;
      }
      if (n.getChildCount() < 3) {
        // goog.dom.createDom('iframe') is fine.
        return ConformanceResult.CONFORMANCE;
      }

      ImmutableCollection<String> tagNames = getTagNames(n.getSecondChild());
      Node attrs = n.getChildAtIndex(2);
      JSType attrsType = attrs.getJSType();

      // TODO(tbreisacher): Remove this after the typechecker understands ES6.
      if (attrsType == null) {
        // Type information is not available; don't run this check.
        return ConformanceResult.CONFORMANCE;
      }

      // String or array attribute sets the class.
      boolean isClassName = !attrsType.isUnknownType() && attrsType.isSubtypeOf(classNameTypes);

      if (attrs.isNull() || (attrsType != null && attrsType.isVoidType())) {
        // goog.dom.createDom('iframe', null) is fine.
        return ConformanceResult.CONFORMANCE;
      }

      for (String[] tagAttr : bannedTagAttrs) {
        if (tagNames != null && !tagNames.contains(tagAttr[0]) && !tagAttr[0].equals("*")) {
          continue;
        }
        ConformanceResult violation;
        if (tagNames != null || tagAttr[0].equals("*")) {
          violation = ConformanceResult.VIOLATION;
        } else if (reportLooseTypeViolations) {
          violation = ConformanceResult.POSSIBLE_VIOLATION;
        } else {
          violation = ConformanceResult.CONFORMANCE;
        }
        if (isClassName) {
          if (!tagAttr[1].equals("class")) {
            continue;
          }
          return violation;
        }
        if (tagAttr[1].equals("textContent")
            && n.getChildCount() > 3
            && violation != ConformanceResult.CONFORMANCE) {
          return violation;
        }
        if (!attrs.isObjectLit()) {
          // Attrs is not an object literal and tagName matches or is unknown.
          return reportLooseTypeViolations
              ? ConformanceResult.POSSIBLE_VIOLATION
              : ConformanceResult.CONFORMANCE;
        }
        Node prop = NodeUtil.getFirstPropMatchingKey(attrs, tagAttr[1]);
        if (prop != null) {
          if (NodeUtil.isSomeCompileTimeConstStringValue(prop)) {
            // Ignore string literal values.
            continue;
          }
          return violation;
        }

        for (Node attr = attrs.getFirstChild(); attr != null; attr = attr.getNext()) {
          if (attr.isComputedProp()) {
            // We don't know if the computed property matches 'src' or not
            return reportLooseTypeViolations
                ? ConformanceResult.POSSIBLE_VIOLATION_DUE_TO_LOOSE_TYPES
                : ConformanceResult.CONFORMANCE;
          }
        }
      }

      return ConformanceResult.CONFORMANCE;
    }

    private @Nullable ImmutableCollection<String> getTagNames(Node tag) {
      if (tag.isStringLit()) {
        return ImmutableSet.of(tag.getString().toLowerCase(Locale.ROOT));
      } else if (tag.isGetProp() && GOOG_DOM_TAGNAME.matches(tag.getFirstChild())) {
        return ImmutableSet.of(tag.getString().toLowerCase(Locale.ROOT));
      }
      // TODO(jakubvrana): Support union, e.g. {!TagName<!HTMLDivElement>|!TagName<!HTMLBRElement>}.
      JSType type = tag.getJSType();
      if (type == null || !type.isTemplatizedType()) {
        return null;
      }
      ObjectType typeAsObj = type.toMaybeObjectType();
      if (typeAsObj.getRawType().getDisplayName().equals("goog.dom.TagName")) {
        JSType tagType = Iterables.getOnlyElement(typeAsObj.getTemplateTypes());
        return ELEMENT_TAG_NAMES.get(tagType.getDisplayName());
      }
      return null;
    }

    private static final ImmutableMultimap<String, String> ELEMENT_TAG_NAMES =
        ImmutableMultimap.<String, String>builder()
            .put("HTMLAnchorElement", "a")
            .put("HTMLAppletElement", "applet")
            .put("HTMLAreaElement", "area")
            .put("HTMLAudioElement", "audio")
            .put("HTMLBRElement", "br")
            .put("HTMLBaseElement", "base")
            .put("HTMLBaseFontElement", "basefont")
            .put("HTMLBodyElement", "body")
            .put("HTMLButtonElement", "button")
            .put("HTMLCanvasElement", "canvas")
            .put("HTMLDListElement", "dl")
            .put("HTMLDataListElement", "datalist")
            .put("HTMLDetailsElement", "details")
            .put("HTMLDialogElement", "dialog")
            .put("HTMLDirectoryElement", "dir")
            .put("HTMLDivElement", "div")
            .put("HTMLEmbedElement", "embed")
            .put("HTMLFieldSetElement", "fieldset")
            .put("HTMLFontElement", "font")
            .put("HTMLFormElement", "form")
            .put("HTMLFrameElement", "frame")
            .put("HTMLFrameSetElement", "frameset")
            .put("HTMLHRElement", "hr")
            .put("HTMLHeadElement", "head")
            .put("HTMLHeadingElement", "h1")
            .put("HTMLHeadingElement", "h2")
            .put("HTMLHeadingElement", "h3")
            .put("HTMLHeadingElement", "h4")
            .put("HTMLHeadingElement", "h5")
            .put("HTMLHeadingElement", "h6")
            .put("HTMLHtmlElement", "html")
            .put("HTMLIFrameElement", "iframe")
            .put("HTMLImageElement", "img")
            .put("HTMLInputElement", "input")
            .put("HTMLIsIndexElement", "isindex")
            .put("HTMLLIElement", "li")
            .put("HTMLLabelElement", "label")
            .put("HTMLLegendElement", "legend")
            .put("HTMLLinkElement", "link")
            .put("HTMLMapElement", "map")
            .put("HTMLMenuElement", "menu")
            .put("HTMLMetaElement", "meta")
            .put("HTMLMeterElement", "meter")
            .put("HTMLModElement", "del")
            .put("HTMLModElement", "ins")
            .put("HTMLOListElement", "ol")
            .put("HTMLObjectElement", "object")
            .put("HTMLOptGroupElement", "optgroup")
            .put("HTMLOptionElement", "option")
            .put("HTMLOutputElement", "output")
            .put("HTMLParagraphElement", "p")
            .put("HTMLParamElement", "param")
            .put("HTMLPreElement", "pre")
            .put("HTMLProgressElement", "progress")
            .put("HTMLQuoteElement", "blockquote")
            .put("HTMLQuoteElement", "q")
            .put("HTMLScriptElement", "script")
            .put("HTMLSelectElement", "select")
            .put("HTMLSourceElement", "source")
            .put("HTMLSpanElement", "span")
            .put("HTMLStyleElement", "style")
            .put("HTMLTableCaptionElement", "caption")
            .put("HTMLTableCellElement", "td")
            .put("HTMLTableCellElement", "th")
            .put("HTMLTableColElement", "col")
            .put("HTMLTableColElement", "colgroup")
            .put("HTMLTableElement", "table")
            .put("HTMLTableRowElement", "tr")
            .put("HTMLTableSectionElement", "tbody")
            .put("HTMLTableSectionElement", "tfoot")
            .put("HTMLTableSectionElement", "thead")
            .put("HTMLTemplateElement", "template")
            .put("HTMLTextAreaElement", "textarea")
            .put("HTMLTitleElement", "title")
            .put("HTMLTrackElement", "track")
            .put("HTMLUListElement", "ul")
            .put("HTMLVideoElement", "video")
            .build();

    private boolean isCreateDomCall(Node n) {
      if (!n.isCall()) {
        return false;
      }
      Node target = n.getFirstChild();
      if (!target.isGetProp()) {
        return false;
      }
      if (!"createDom".equals(target.getString())) {
        return false;
      }

      Node srcObj = target.getFirstChild();
      if (GOOG_DOM.matches(srcObj)) {
        return true;
      }
      JSType type = srcObj.getJSType();
      if (type == null) {
        return false;
      }
      if (type.equals(domHelperType)) {
        return true;
      }
      return false;
    }
  }

  /**
   * Checks nodes for conformance with banning the setting of attributes that are on the blocklist.
   */
  public static final class SecuritySensitiveAttributes {
    /**
     * Security-sensitive attributes that are banned from being set.
     *
     * <p>Making updates to these attributes requires a new JSCompiler release. You must test the
     * change using a global presubmit "at head" and update any affected allowlists. See
     * go/jscompiler-global-presubmit and go/tsjs-conformance-team-docs.
     */
    @VisibleForTesting
    public static final ImmutableSet<String> ALL_BANNED_ATTRS =
        ImmutableSet.of(
            "href",
            "rel",
            "src",
            "srcdoc",
            "action",
            "formaction",
            "sandbox",
            "icon",
            "codebase",
            "data");

    private final ImmutableSet<String> bannedAtrrs;
    private final Supplier<GlobalNamespace> globalNamespaceSupplier;

    public SecuritySensitiveAttributes() {
      this(ALL_BANNED_ATTRS, () -> null);
    }

    public SecuritySensitiveAttributes(Supplier<GlobalNamespace> globalNamespaceSupplier) {
      this(ALL_BANNED_ATTRS, globalNamespaceSupplier);
    }

    public SecuritySensitiveAttributes(Collection<String> bannedAtrrs) {
      this(bannedAtrrs, () -> null);
    }

    public SecuritySensitiveAttributes(
        Collection<String> bannedAtrrs, Supplier<GlobalNamespace> globalNamespaceSupplier) {
      this.bannedAtrrs =
          bannedAtrrs.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(toImmutableSet());
      this.globalNamespaceSupplier = globalNamespaceSupplier;
    }

    /**
     * Checks if a attribute name is on the security banlist. Callers should make sure the attribute
     * name is lower-cased, as attribute names are case-insensitve in HTML.
     */
    public boolean contains(String attributeName) {
      return bannedAtrrs.contains(attributeName);
    }

    /**
     * Given a {@code NodeTraversal} and {@code Node}, check if the attribute violates conformance.
     *
     * <p>A violation is returned if the attribute name cannot be determined (and it is not an xid),
     * if the attribute is on a list of banned attributes, or if it begins with the letters "on".
     * Otherwise, it is a conforming attribute.
     */
    public ConformanceResult checkConformanceForAttributeName(
        NodeTraversal traversal, Node attrName) {
      String literalName =
          ConformanceUtil.inferStringValue(traversal.getScope(), attrName, globalNamespaceSupplier);
      if (literalName == null) {
        // xid() obfuscates attribute names, thus never clashing with security-sensitive attributes.
        return ConformanceUtil.isXid(attrName.getJSType())
            ? ConformanceResult.CONFORMANCE
            : ConformanceResult.VIOLATION;
      }

      literalName = literalName.toLowerCase(Locale.ROOT);

      if (bannedAtrrs.contains(literalName)
          || ConformanceUtil.isEventHandlerAttrName(literalName)) {
        return ConformanceResult.VIOLATION;
      }
      return ConformanceResult.CONFORMANCE;
    }

    /**
     * Given a {@code NodeTraversal} and {@code Node}, check if the attribute violates conformance.
     *
     * <p>A violation is returned only if the attribute name can be statically determined and is on
     * the list of banned attributes.
     */
    public ConformanceResult checkConformanceForAttributeNameWithHighConfidence(
        NodeTraversal traversal, Node attrName) {
      String literalName =
          ConformanceUtil.inferStringValue(traversal.getScope(), attrName, globalNamespaceSupplier);
      if (literalName == null) {
        return ConformanceResult.CONFORMANCE;
      }
      return contains(literalName.toLowerCase(Locale.ROOT))
          ? ConformanceResult.VIOLATION
          : ConformanceResult.CONFORMANCE;
    }
  }

  /**
   * Ban {@code Element#setAttribute} with attribute names specified in {@code value} or any dynamic
   * string.
   *
   * <p>Using the element access syntax to set properties of {@code Element} is considered to be
   * equivalent to calling {@code Element#setAttribute}. E.g., {@code element[prop] = value} is
   * treated as {@code element.setAttribute(prop, value)} when {@code element} has the type {@code
   * Element}.
   */
  public static final class BanElementSetAttribute extends AbstractRule {
    private static final String ELEMENT_TYPE_NAME = "Element";
    private static final String SET_ATTRIBUTE = "setAttribute";
    private static final String SET_ATTRIBUTE_NS = "setAttributeNS";
    private static final String SET_ATTRIBUTE_NODE = "setAttributeNode";
    private static final String SET_ATTRIBUTE_NODE_NS = "setAttributeNodeNS";
    private static final ImmutableSet<String> BANNED_PROPERTIES =
        ImmutableSet.of(SET_ATTRIBUTE, SET_ATTRIBUTE_NS, SET_ATTRIBUTE_NODE, SET_ATTRIBUTE_NODE_NS);

    private boolean performGlobalNamespaceAnalysis = true;
    private GlobalNamespace globalNamespace;

    /**
     * The cap for script size, beyond which we give up performing global namespace analysis due to
     * exccessive performance cost. The value is purely herustic and subject to future regulation.
     */
    private static final int GLOBAL_NAMESPACE_ANALYSIS_LIMIT = 10000;

    private final JSType elementType;
    private final SecuritySensitiveAttributes securitySensitiveAttributes;
    private final ConformanceResult defaultDecisionForUncertainCases;

    /**
     * Create a custom checker to ban {@code Element#setAttribute} based on conformance a
     * requirement spec.
     *
     * @throws InvalidRequirementSpec If the requirement is malformed.
     */
    public BanElementSetAttribute(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
      securitySensitiveAttributes =
          new SecuritySensitiveAttributes(requirement.getValueList(), this::getGlobalNamespace);
      defaultDecisionForUncertainCases =
          requirement.getReportLooseTypeViolations()
              ? ConformanceResult.VIOLATION
              : ConformanceResult.CONFORMANCE;
      elementType = compiler.getTypeRegistry().getGlobalType(ELEMENT_TYPE_NAME);
    }

    /**
     * Build GlobalNamespace starting from the Root node of the input, excluding externs. Skip the
     * build if the input AST is too large.
     */
    @Nullable GlobalNamespace getGlobalNamespace() {
      if (performGlobalNamespaceAnalysis && globalNamespace == null) {
        if (NodeUtil.countAstSizeUpToLimit(compiler.getJsRoot(), GLOBAL_NAMESPACE_ANALYSIS_LIMIT)
            >= GLOBAL_NAMESPACE_ANALYSIS_LIMIT) {
          performGlobalNamespaceAnalysis = false;
        } else {
          globalNamespace = new GlobalNamespace(compiler, compiler.getJsRoot());
        }
      }
      return globalNamespace;
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal traversal, Node node) {
      if (node.isCall()) {
        return checkConformanceOnPropertyCall(traversal, node);
      } else if (node.isGetElem() && NodeUtil.isLValue(node)) {
        return checkConformanceOnGetElement(traversal, node);
      }
      return ConformanceResult.CONFORMANCE;
    }

    private ConformanceResult checkConformanceOnPropertyCall(
        NodeTraversal traversal, Node callNode) {
      Optional<String> calledProperty = getBannedPropertyName(callNode);
      if (!calledProperty.isPresent()) {
        return ConformanceResult.CONFORMANCE;
      }

      if (calledProperty.get().equals(SET_ATTRIBUTE)) {
        // If the setAttribute function is called with less than two arguments it's either a false
        // positive or uncompilable code.
        if (callNode.getChildCount() < 3) {
          return ConformanceResult.CONFORMANCE;
        }

        if (requirement.getReportLooseTypeViolations()) {
          return securitySensitiveAttributes.checkConformanceForAttributeName(
              traversal, callNode.getSecondChild());
        } else {
          return securitySensitiveAttributes.checkConformanceForAttributeNameWithHighConfidence(
              traversal, callNode.getSecondChild());
        }
      }

      if (calledProperty.get().equals(SET_ATTRIBUTE_NS)) {
        // If the setAttributeNS function is called with less than three arguments it's either a
        // false positive or uncompilable code.
        if (callNode.getChildCount() < 4) {
          return ConformanceResult.CONFORMANCE;
        }

        if (callNode.getSecondChild().isNull()) {
          // A null namespace makes this equivalent to a regular setAttribute call.
          if (requirement.getReportLooseTypeViolations()) {
            return securitySensitiveAttributes.checkConformanceForAttributeName(
                traversal, callNode.getChildAtIndex(2));
          } else {
            return securitySensitiveAttributes.checkConformanceForAttributeNameWithHighConfidence(
                traversal, callNode.getChildAtIndex(2));
          }
        }
      }

      // We haven't defined a security contract on setAttributeNode and setAttributeNodeNS yet, so
      // flag them as risky if report_loose_type_violations is set.
      return defaultDecisionForUncertainCases;
    }

    private Optional<String> getBannedPropertyName(Node callNode) {
      Node target = callNode.getFirstChild();
      if (!target.isGetProp()) {
        return Optional.absent();
      }
      String propertyName = target.getString();
      if (!BANNED_PROPERTIES.contains(propertyName)) {
        return Optional.absent();
      }
      JSType type = target.getFirstChild().getJSType();
      if (type == null) {
        return Optional.absent();
      }
      if (elementType != null && type.restrictByNotNullOrUndefined().isSubtypeOf(elementType)) {
        return Optional.of(propertyName);
      }
      return Optional.absent();
    }

    /**
     * Checks if there is any security implication in a GetElem node.
     *
     * <p>Returns {@code true} if the checked {@link Node} is setting a security sensitive property
     * on an {@code Element} object through the bracket syntax.
     */
    private ConformanceResult checkConformanceOnGetElement(
        NodeTraversal traversal, Node getElementNode) {
      Node key = getElementNode.getSecondChild();
      String keyName =
          ConformanceUtil.inferStringValue(traversal.getScope(), key, this::getGlobalNamespace);
      if (keyName != null) {
        if (securitySensitiveAttributes.contains(keyName.toLowerCase(Locale.ROOT))
            || (requirement.getReportLooseTypeViolations()
                // innerHTML and outerHTML are not attributes but properties. For simplicity we
                // check them here instead of having another rule.
                && (keyName.equals("innerHTML") || keyName.equals("outerHTML")))) {
          if (hasElementType(getElementNode)) {
            return ConformanceResult.VIOLATION;
          }
        }
        return ConformanceResult.CONFORMANCE;
      }

      if (!requirement.getReportLooseTypeViolations()) {
        return ConformanceResult.CONFORMANCE;
      }

      // We have seen code using other types of keys (e.g., numbers) for element access. Only
      // report violations if the key is explicitly typed as string or the union of string and
      // other types.
      JSType keyType = key.getJSType();
      if (keyType == null || ConformanceUtil.isXid(keyType)) {
        return ConformanceResult.CONFORMANCE;
      }
      if (hasElementType(getElementNode)) {
        if (keyType.isString()) {
          return ConformanceResult.VIOLATION;
        } else if (keyType.isUnionType()) {
          for (JSType alternate : keyType.toMaybeUnionType().getAlternates()) {
            if (alternate.isString()) {
              return ConformanceResult.VIOLATION;
            }
          }
        }
      }

      return ConformanceResult.CONFORMANCE;
    }

    private boolean hasElementType(Node getElementNode) {
      JSType objType = getElementNode.getFirstChild().getJSType();

      // Do not further check the node if there's no type information available.
      if (objType == null || elementType == null) {
        return false;
      }
      objType = objType.restrictByNotNullOrUndefined();
      // If the type of the object is not known to be a subtype of Element, the code is not
      // equivalent to setAttribute. Without this check, we will get overwhelmed by false
      // positives.
      if (objType.isUnknownType() || objType.isEmptyType() || !objType.isSubtypeOf(elementType)) {
        return false;
      }

      return true;
    }
  }

  /**
   * Can be used to ban any function like {@code Element#setAttribute} which takes two parameters,
   * an attribute name and a value.
   *
   * <p>The specific attribute names that are banned are specified in {@code
   * SecuritySensitiveAttributes.BANNED_ATTRS}.
   */
  public static final class BanSettingAttributes extends AbstractRule {
    private static class AttributeSettingRestriction {
      final JSType type;
      final String property;

      AttributeSettingRestriction(JSType type, String property) {
        this.type = type;
        this.property = property;
      }
    }

    private static final SecuritySensitiveAttributes BANNED_ATTRS =
        new SecuritySensitiveAttributes();
    private final ImmutableList<AttributeSettingRestriction> restrictions;

    /**
     * Create a custom checker to ban a function like {@code Element#setAttribute} based on a
     * conformance requirement spec.
     *
     * <p>Names in {@code value} fields indicate the functions that should be blocked. Throws {@link
     * InvalidRequirementSpec} if the requirement is malformed.
     */
    public BanSettingAttributes(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);

      if (requirement.getValueList().isEmpty()) {
        throw new InvalidRequirementSpec("missing value");
      }

      JSTypeRegistry registry = compiler.getTypeRegistry();
      ImmutableList.Builder<AttributeSettingRestriction> builder = ImmutableList.builder();
      for (String value : requirement.getValueList()) {
        String type = ConformanceUtil.getClassFromDeclarationName(value);
        String property = ConformanceUtil.getPropertyFromDeclarationName(value);
        if (type == null || property == null) {
          throw new InvalidRequirementSpec("bad prop value");
        }
        builder.add(new AttributeSettingRestriction(registry.getGlobalType(type), property));
      }

      restrictions = builder.build();
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal traversal, Node node) {
      if (node.isCall()) {
        return checkConformanceOnPropertyCall(traversal, node);
      }
      return ConformanceResult.CONFORMANCE;
    }

    private ConformanceResult checkConformanceOnPropertyCall(NodeTraversal traversal, Node node) {
      Optional<String> calledProperty = getBannedPropertyName(node);
      if (!calledProperty.isPresent()) {
        return ConformanceResult.CONFORMANCE;
      }

      // This node is conformant if it has less than three children (the function and two
      // arguments). It's either reading the attribute, a false positive, or uncompilable code.
      if (node.getChildCount() < 3) {
        return ConformanceResult.CONFORMANCE;
      }

      return BANNED_ATTRS.checkConformanceForAttributeName(traversal, node.getSecondChild());
    }

    private Optional<String> getBannedPropertyName(Node node) {
      Node target = node.getFirstChild();
      if (!target.isGetProp()) {
        return Optional.absent();
      }
      JSType type = target.getFirstChild().getJSType();
      if (type == null) {
        return Optional.absent();
      }
      String propertyName = target.getString();
      for (AttributeSettingRestriction restricted : restrictions) {
        if (propertyName.equals(restricted.property)
            && restricted.type != null
            && type.isSubtypeOf(restricted.type)) {
          return Optional.of(propertyName);
        }
      }
      return Optional.absent();
    }
  }

  /**
   * Ban {@code Document#execCommand} with command names specified in {@code value} or any dynamic
   * string.
   */
  public static final class BanExecCommand extends AbstractRule {
    private final ImmutableList<String> bannedAttrs;

    /**
     * Creates a custom checker to ban {@code Document#execCommand} based on a conformance
     * requirement spec. Names in {@code value} fields indicate the attribute names that should be
     * blocked.
     *
     * @throws InvalidRequirementSpec if the requirement is malformed
     */
    public BanExecCommand(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
      bannedAttrs =
          requirement.getValueList().stream()
              .map(v -> v.toLowerCase(Locale.ROOT))
              .collect(toImmutableList());
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal traversal, Node node) {
      if (!isBannedProperty(node)) {
        return ConformanceResult.CONFORMANCE;
      }

      // If the function is called without arguments then it's either a false positive or
      // uncompilable code.
      if (node.getChildCount() < 2) {
        return ConformanceResult.CONFORMANCE;
      }

      Node attr = node.getSecondChild();
      if (!attr.isStringLit()) {
        return ConformanceResult.VIOLATION;
      }

      String attrName = attr.getString();
      if (bannedAttrs.contains(attrName.toLowerCase(Locale.ROOT))) {
        return ConformanceResult.VIOLATION;
      }
      return ConformanceResult.CONFORMANCE;
    }

    private boolean isBannedProperty(Node node) {
      if (!node.isCall()) {
        return false;
      }
      Node target = node.getFirstChild();
      if (!target.isGetProp()) {
        return false;
      }
      String propertyName = target.getString();
      if (!propertyName.equals("execCommand")) {
        return false;
      }
      JSType type = target.getFirstChild().getJSType();
      if (type == null) {
        return false;
      }
      JSType documentType = compiler.getTypeRegistry().getGlobalType("Document");
      if (documentType == null || !type.restrictByNotNullOrUndefined().isSubtypeOf(documentType)) {
        return false;
      }
      return true;
    }
  }

  /** Checks that {@code this} is not being referenced directly within a static member function. */
  public static final class BanStaticThis extends AbstractTypeRestrictionRule {
    public BanStaticThis(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      if (!n.isThis()) {
        return ConformanceResult.CONFORMANCE;
      }

      Node enclosingFunction = NodeUtil.getEnclosingNonArrowFunction(n);
      if (enclosingFunction != null && isStaticMethod(enclosingFunction)) {
        return ConformanceResult.VIOLATION;
      }
      return ConformanceResult.CONFORMANCE;
    }

    private static boolean isStaticMethod(Node n) {
      checkArgument(n.isFunction());
      Node parent = n.getParent();
      return parent != null && parent.isMemberFunctionDef() && parent.isStaticMember();
    }
  }

  private static boolean isLooseObject(JSType type, JSTypeRegistry registry) {
    return type.equals(registry.getNativeType(JSTypeNative.OBJECT_TYPE));
  }
}
