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

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;
import com.google.javascript.jscomp.CheckConformance.InvalidRequirementSpec;
import com.google.javascript.jscomp.CheckConformance.Rule;
import com.google.javascript.jscomp.CodingConvention.AssertionFunctionSpec;
import com.google.javascript.jscomp.ConformanceRules.AbstractRule;
import com.google.javascript.jscomp.ConformanceRules.ConformanceResult;
import com.google.javascript.jscomp.Requirement.Severity;
import com.google.javascript.jscomp.Requirement.Type;
import com.google.javascript.jscomp.parsing.JsDocInfoParser;
import com.google.javascript.rhino.FunctionTypeI;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.ObjectTypeI;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TypeI;
import com.google.javascript.rhino.TypeIRegistry;
import com.google.javascript.rhino.jstype.JSTypeNative;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.annotation.Nullable;

/**
 * Standard conformance rules. See
 * third_party/java_src/jscomp/java/com/google/javascript/jscomp/conformance.proto
 */
@GwtIncompatible("java.lang.reflect, java.util.regex")
public final class ConformanceRules {

  private ConformanceRules() {}

  /**
   * Classes extending AbstractRule must return ConformanceResult
   * from their checkConformance implementation. For simple rules, the
   * constants CONFORMANCE, POSSIBLE_VIOLATION, VIOLATION are sufficient.
   * However, for some rules additional clarification specific to the
   * violation instance is helpful, for that, an instance of this class
   * can be created to associate a note with the violation.
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
    public static final ConformanceResult CONFORMANCE = new ConformanceResult(
        ConformanceLevel.CONFORMANCE);
    public static final ConformanceResult POSSIBLE_VIOLATION = new ConformanceResult(
        ConformanceLevel.POSSIBLE_VIOLATION);
    private static final ConformanceResult POSSIBLE_VIOLATION_DUE_TO_LOOSE_TYPES =
        new ConformanceResult(
            ConformanceLevel.POSSIBLE_VIOLATION,
            "The type information available for this expression is too loose "
            + "to ensure conformance.");
    public static final ConformanceResult VIOLATION = new ConformanceResult(
        ConformanceLevel.VIOLATION);
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

  /**
   * A conformance rule implementation to support things common to all rules such as whitelisting
   * and reporting.
   */
  public abstract static class AbstractRule implements Rule {
    final AbstractCompiler compiler;
    final String message;
    final Severity severity;
    final ImmutableList<String> whitelist;
    final ImmutableList<String> onlyApplyTo;
    @Nullable final Pattern whitelistRegexp;
    @Nullable final Pattern onlyApplyToRegexp;
    final boolean reportLooseTypeViolations;
    final TypeMatchingStrategy typeMatchingStrategy;

    public AbstractRule(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      if (!requirement.hasErrorMessage()) {
        throw new InvalidRequirementSpec("missing message");
      }
      this.compiler = compiler;
      message = requirement.getErrorMessage();
      if (requirement.getSeverity() == Severity.UNSPECIFIED) {
        severity = Severity.WARNING;
      } else {
        severity = requirement.getSeverity();
      }
      whitelist = ImmutableList.copyOf(requirement.getWhitelistList());
      whitelistRegexp = buildPattern(
          requirement.getWhitelistRegexpList());
      onlyApplyTo = ImmutableList.copyOf(requirement.getOnlyApplyToList());
      onlyApplyToRegexp = buildPattern(
          requirement.getOnlyApplyToRegexpList());
      reportLooseTypeViolations = requirement.getReportLooseTypeViolations();
      typeMatchingStrategy = getTypeMatchingStrategy(requirement);
    }

    private static TypeMatchingStrategy getTypeMatchingStrategy(Requirement requirement) {
      switch (requirement.getTypeMatchingStrategy()) {
        case LOOSE:
          return TypeMatchingStrategy.LOOSE;
        case STRICT_NULLABILITY:
          return TypeMatchingStrategy.STRICT_NULLABILITY;
        case SUBTYPES:
          return TypeMatchingStrategy.SUBTYPES;
        case EXACT:
          return TypeMatchingStrategy.EXACT;
        default:
          throw new IllegalStateException("Unknown TypeMatchingStrategy");
      }
    }

    @Nullable
    private static Pattern buildPattern(List<String> reqPatterns)
        throws InvalidRequirementSpec {
      if (reqPatterns == null || reqPatterns.isEmpty()) {
        return null;
      }

      // validate the patterns
      for (String reqPattern : reqPatterns) {
        try {
          Pattern.compile(reqPattern);
        } catch (PatternSyntaxException e) {
          throw new InvalidRequirementSpec("invalid regex pattern");
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

    /**
     * @return Whether the code represented by the Node conforms to the
     * rule.
     */
    protected abstract ConformanceResult checkConformance(
        NodeTraversal t, Node n);

    /**
     * @return Whether the specified Node should be checked for conformance,
     *     according to this rule's whitelist configuration.
     */
    protected final boolean shouldCheckConformance(Node n) {
      String srcfile = NodeUtil.getSourceName(n);
      if (srcfile == null) {
        return true;
      } else if (!onlyApplyTo.isEmpty() || onlyApplyToRegexp != null) {
        return pathIsInListOrRegexp(srcfile, onlyApplyTo, onlyApplyToRegexp)
            && !pathIsInListOrRegexp(srcfile, whitelist, whitelistRegexp);
      } else {
        return !pathIsInListOrRegexp(srcfile, whitelist, whitelistRegexp);
      }
    }

    private static boolean pathIsInListOrRegexp(
        String srcfile, ImmutableList<String> list, @Nullable Pattern regexp) {
      for (int i = 0; i < list.size(); i++) {
        String entry = list.get(i);
        if (!entry.isEmpty() && srcfile.startsWith(entry)) {
          return true;
        }
      }
      return regexp != null && regexp.matcher(srcfile).find();
    }

    @Override
    public final void check(NodeTraversal t, Node n) {
      ConformanceResult result = checkConformance(t, n);
      if (result.level != ConformanceLevel.CONFORMANCE
          && shouldCheckConformance(n)) {
        report(t, n, result);
      }
    }

    /**
     * Report a conformance warning for the given node.
     * @param n The node representing the violating code.
     * @param result The result representing the confidence of the violation.
     */
    protected void report(
        NodeTraversal t, Node n, ConformanceResult result) {
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
      String separator = (result.note.isEmpty())
          ? ""
          : "\n";
      t.report(n, msg, message, separator, result.note);
    }
  }




  abstract static class AbstractTypeRestrictionRule extends AbstractRule {
    private final TypeI nativeObjectType;
    private final TypeI whitelistedTypes;
    private final ImmutableList<Node> assertionsFunctionNames;


    public AbstractTypeRestrictionRule(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
      nativeObjectType = compiler.getTypeIRegistry().getNativeType(JSTypeNative.OBJECT_TYPE);
      List<String> whitelistedTypeNames = requirement.getValueList();
      whitelistedTypes = union(whitelistedTypeNames);

      ImmutableList.Builder<Node> builder = ImmutableList.builder();
      for (AssertionFunctionSpec fn : compiler.getCodingConvention().getAssertionFunctions()) {
        builder.add(NodeUtil.newQName(compiler, fn.getFunctionName()));
      }
      assertionsFunctionNames = builder.build();
    }

    protected boolean isWhitelistedType(Node n) {
      if (whitelistedTypes != null && n.getTypeI() != null) {
        TypeI targetType = n.getTypeI().restrictByNotNullOrUndefined();
        if (targetType.isSubtypeOf(whitelistedTypes)) {
          return true;
        }
      }
      return false;
    }

    protected boolean isKnown(Node n) {
      return !isUnknown(n)
          && !isBottom(n)
          && !isTypeVariable(n); // TODO(johnlenz): Remove this restriction
    }

    protected boolean isNativeObjectType(Node n) {
      TypeI type = n.getTypeI().restrictByNotNullOrUndefined();
      return type.isEquivalentTo(nativeObjectType);
    }

    protected boolean isTop(Node n) {
      TypeI type = n.getTypeI();
      return type != null && type.isTop();
    }

    protected boolean isUnknown(Node n) {
      TypeI type = n.getTypeI();
      return (type == null || type.isUnknownType());
    }

    protected boolean isSomeUnknownType(Node n) {
      TypeI type = n.getTypeI();
      return (type == null || type.isSomeUnknownType());
    }

    protected boolean isTypeVariable(Node n) {
      TypeI type = n.getTypeI().restrictByNotNullOrUndefined();
      return type.isTypeVariable();
    }

    private boolean isBottom(Node n) {
      TypeI type = n.getTypeI().restrictByNotNullOrUndefined();
      return type.isBottom();
    }

    protected TypeI union(List<String> typeNames) {
      TypeIRegistry registry = compiler.getTypeIRegistry();
      List<TypeI> types = new ArrayList<>();

      for (String typeName : typeNames) {
        TypeI type = registry.getGlobalType(typeName);
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
        for (int i = 0; i < assertionsFunctionNames.size(); i++) {
          if (target.matchesQualifiedName(assertionsFunctionNames.get(i))) {
            return true;
          }
        }
      }
      return false;
    }

    protected boolean isTypeImmediatelyTightened(Node n) {
      return isAssertionCall(n.getParent()) || /* casted node */ n.getTypeIBeforeCast() != null;
    }

    protected boolean isUsed(Node n) {
      return !n.getParent().isName()
          && ((NodeUtil.isAssignmentOp(n.getParent()))
              ? NodeUtil.isExpressionResultUsed(n.getParent())
              : NodeUtil.isExpressionResultUsed(n));
    }
  }

  /**
   * Check that variables annotated as @const have an inferred type, if there is
   * no type given explicitly.
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
        TypeI type = n.getTypeI();
        if (type != null && type.isUnknownType()
            && !NodeUtil.isNamespaceDecl(n)) {
          return ConformanceResult.VIOLATION;
        }
      }
      return ConformanceResult.CONFORMANCE;
    }
  }

  /**
   * Banned dependency rule
   */
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

  /**
   * Banned name rule
   */
  static class BannedName extends AbstractRule {
    private final Requirement.Type requirementType;
    private final ImmutableList<Node> names;

    BannedName(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
      if (requirement.getValueCount() == 0) {
        throw new InvalidRequirementSpec("missing value");
      }
      requirementType = requirement.getType();
      ImmutableList.Builder<Node> builder = ImmutableList.builder();
      for (String name : requirement.getValueList()) {
        builder.add(NodeUtil.newQName(compiler, name));
      }
      names = builder.build();
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      if (isCandidateNode(n)) {
        if (requirementType == Type.BANNED_NAME_CALL) {
          if (!ConformanceUtil.isCallTarget(n)) {
            return ConformanceResult.CONFORMANCE;
          }
        }
        for (int i = 0; i < names.size(); i++) {
          Node nameNode = names.get(i);
          if (n.matchesQualifiedName(nameNode) && isRootOfQualifiedNameGlobal(t, n)) {
            if (NodeUtil.isInSyntheticScript(n)) {
              return ConformanceResult.CONFORMANCE;
            } else {
              return ConformanceResult.VIOLATION;
            }
          }
        }
      }
      return ConformanceResult.CONFORMANCE;
    }

    private boolean isCandidateNode(Node n) {
      switch(n.getToken()) {
        case GETPROP:
          return n.getFirstChild().isQualifiedName();
        case NAME:
          return !n.getString().isEmpty();
        default:
          return false;
      }
    }

    private static boolean isRootOfQualifiedNameGlobal(NodeTraversal t, Node n) {
      String rootName = NodeUtil.getRootOfQualifiedName(n).getQualifiedName();
      Var v = t.getScope().getVar(rootName);
      return v != null && v.isGlobal();
    }
  }

  /**
   * Banned property rule
   */
  static class BannedProperty extends AbstractRule {
    private static class Property {
      final String type;
      final String property;
      Property(String type, String property) {
        this.type = type;
        this.property = property;
      }
    }
    private final ImmutableList<Property> props;
    private final Requirement.Type requirementType;

    BannedProperty(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);

      if (requirement.getValueCount() == 0) {
        throw new InvalidRequirementSpec("missing value");
      }

      checkArgument(
          requirement.getType() == Type.BANNED_PROPERTY
              || requirement.getType() == Type.BANNED_PROPERTY_READ
              || requirement.getType() == Type.BANNED_PROPERTY_WRITE
              || requirement.getType() == Type.BANNED_PROPERTY_NON_CONSTANT_WRITE
              || requirement.getType() == Type.BANNED_PROPERTY_CALL);
      requirementType = requirement.getType();

      ImmutableList.Builder<Property> builder = ImmutableList.builder();
      List<String> values = requirement.getValueList();
      for (String value : values) {
        String type = getClassFromDeclarationName(value);
        String property = getPropertyFromDeclarationName(value);
        if (type == null || property == null) {
          throw new InvalidRequirementSpec("bad prop value");
        }
        builder.add(new Property(type, property));
      }

      props = builder.build();
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      if (NodeUtil.isGet(n) && n.getLastChild().isString()) {
        // TODO(dimvar): Instead of the for-loop, we could make props be a multi-map from
        // the property name to Property, and then here just pull the relevant Property instances.
        // Won't make much difference to performance, since props usually only has a few elements,
        // but it will make the code clearer.
        for (int i = 0; i < props.size(); i++) {
          Property prop = props.get(i);
          ConformanceResult result = checkConformance(t, n, prop);
          if (result.level != ConformanceLevel.CONFORMANCE) {
            return result;
          }
        }
      }
      return ConformanceResult.CONFORMANCE;
    }

    private ConformanceResult checkConformance(NodeTraversal t, Node propAccess, Property prop) {
      if (isCandidatePropUse(propAccess, prop)) {
        TypeIRegistry registry = t.getCompiler().getTypeIRegistry();
        TypeI typeWithBannedProp = registry.getGlobalType(prop.type);
        Node receiver = propAccess.getFirstChild();
        if (typeWithBannedProp != null && receiver.getTypeI() != null) {
          TypeI foundType = receiver.getTypeI().restrictByNotNullOrUndefined();
          ObjectTypeI foundObj = foundType.toMaybeObjectType();
          if (foundObj != null) {
            if (foundObj.isPrototypeObject()) {
              FunctionTypeI ownerFun = foundObj.getOwnerFunction();
              if (ownerFun.isConstructor()) {
                foundType = ownerFun.getInstanceType();
              }
            } else if (foundObj.isGenericObjectType()) {
              foundType = foundObj.getRawType();
            }
          }
          if (foundType.isSomeUnknownType()
             || foundType.isTypeVariable()
             || foundType.isBottom()
             || foundType.isTop()
             || foundType.isEquivalentTo(registry.getNativeType(JSTypeNative.OBJECT_TYPE))) {
            if (reportLooseTypeViolations) {
              return ConformanceResult.POSSIBLE_VIOLATION_DUE_TO_LOOSE_TYPES;
            }
          } else if (foundType.isSubtypeOf(typeWithBannedProp)) {
            return ConformanceResult.VIOLATION;
          } else if (typeWithBannedProp.isSubtypeWithoutStructuralTyping(foundType)) {
            if (matchesPrototype(typeWithBannedProp, foundType)) {
              return ConformanceResult.VIOLATION;
            } else if (reportLooseTypeViolations) {
              // Access of a banned property through a super class may be a violation
              return ConformanceResult.POSSIBLE_VIOLATION_DUE_TO_LOOSE_TYPES;
            }
          }
        }
      }
      return ConformanceResult.CONFORMANCE;
    }

    private boolean matchesPrototype(TypeI type, TypeI maybePrototype) {
      ObjectTypeI methodClassObjectType = type.toMaybeObjectType();
      if (methodClassObjectType != null) {
        if (methodClassObjectType.getPrototypeObject().isEquivalentTo(maybePrototype)) {
          return true;
        }
      }
      return false;
    }

    /**
     * Determines if {@code n} is a potentially banned use of {@code prop}.
     *
     * Specifically this is the case if {@code n} is a use of a property with
     * the name specified by {@code prop}. Furthermore, if the conformance
     * requirement under consideration only bans assignment to the property,
     * {@code n} is only a candidate if it is an l-value.
     */
    private boolean isCandidatePropUse(Node propAccess, Property prop) {
      Preconditions.checkState(propAccess.isGetProp() || propAccess.isGetElem(),
          "Expected property-access node but found %s", propAccess);
      if (propAccess.getLastChild().getString().equals(prop.property)) {
        if (requirementType == Type.BANNED_PROPERTY_WRITE) {
          return NodeUtil.isLValue(propAccess);
        } else if (requirementType == Type.BANNED_PROPERTY_NON_CONSTANT_WRITE) {
          if (!NodeUtil.isLValue(propAccess)) {
            return false;
          }
          if (NodeUtil.isLhsOfAssign(propAccess)
              && (NodeUtil.isLiteralValue(propAccess.getNext(), false /* includeFunctions */)
                  || NodeUtil.isStringLiteralValue(propAccess.getNext()))) {
            return false;
          }
          return true;
        } else if (requirementType == Type.BANNED_PROPERTY_READ) {
          return !NodeUtil.isLValue(propAccess) && NodeUtil.isExpressionResultUsed(propAccess);
        } else if (requirementType == Type.BANNED_PROPERTY_CALL) {
          return ConformanceUtil.isCallTarget(propAccess);
        } else {
          return true;
        }
      }
      return false;
    }

    /**
     * From a provide name extract the method name.
     */
    private static String getPropertyFromDeclarationName(String specName) {
      String[] parts = specName.split("\\.prototype\\.");
      checkState(parts.length == 1 || parts.length == 2);
      if (parts.length == 2) {
        return parts[1];
      }
      return null;
    }

    /**
     * From a provide name extract the class name.
     */
    private static String getClassFromDeclarationName(String specName) {
      String[] parts = specName.split("\\.prototype\\.");
      checkState(parts.length == 1 || parts.length == 2);
      if (parts.length == 2) {
        return parts[0];
      }
      return null;
    }
  }

  private static class ConformanceUtil {

    static boolean isCallTarget(Node n) {
      Node parent = n.getParent();
      return (parent.isCall() || parent.isNew())
           && parent.getFirstChild() == n;
    }

    static TypeI evaluateTypeString(
        AbstractCompiler compiler, String expression)
        throws InvalidRequirementSpec {
      Node typeNodes = JsDocInfoParser.parseTypeString(expression);
      if (typeNodes == null) {
        throw new InvalidRequirementSpec("bad type expression");
      }
      JSTypeExpression typeExpr = new JSTypeExpression(
          typeNodes, "conformance");
      return compiler.getTypeIRegistry().evaluateTypeExpressionInGlobalScope(typeExpr);
    }

    /**
     * Validate the parameters and the 'this' type, of a new or call.
     * @see TypeCheck#visitParameterList
     */
    static boolean validateCall(
        AbstractCompiler compiler,
        Node callOrNew,
        FunctionTypeI functionType,
        boolean isCallInvocation) {
      checkState(callOrNew.isCall() || callOrNew.isNew());

      return validateParameterList(compiler, callOrNew, functionType, isCallInvocation)
          && validateThis(callOrNew, functionType, isCallInvocation);
    }

    private static boolean validateThis(
        Node callOrNew,
        FunctionTypeI functionType,
        boolean isCallInvocation) {

      if (callOrNew.isNew()) {
        return true;
      }

      TypeI thisType = functionType.getTypeOfThis();
      if (thisType == null || thisType.isUnknownType()) {
        return true;
      }

      Node thisNode = isCallInvocation
          ? callOrNew.getSecondChild()
          : callOrNew.getFirstFirstChild();
      TypeI thisNodeType =
          thisNode.getTypeI().restrictByNotNullOrUndefined();
      return thisNodeType.isSubtypeOf(thisType);
    }

    private static boolean validateParameterList(
        AbstractCompiler compiler,
        Node callOrNew,
        FunctionTypeI functionType,
        boolean isCallInvocation) {
      Iterator<Node> arguments = callOrNew.children().iterator();
      arguments.next(); // skip the function name
      if (isCallInvocation && arguments.hasNext()) {
        arguments.next();
      }

      // Get all the annotated types of the argument nodes
      ImmutableList.Builder<TypeI> argumentTypes = ImmutableList.builder();
      while (arguments.hasNext()) {
        TypeI argType = arguments.next().getTypeI();
        if (argType == null) {
          argType = compiler.getTypeIRegistry().getNativeType(JSTypeNative.UNKNOWN_TYPE);
        }
        argumentTypes.add(argType);
      }
      return functionType.acceptsArguments(argumentTypes.build());
    }
  }

  /**
   * Restricted name call rule
   */
  static class RestrictedNameCall extends AbstractRule {
    private static class Restriction {
      final Node name;
      final FunctionTypeI restrictedCallType;

      Restriction(Node name, FunctionTypeI restrictedCallType) {
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

      ImmutableList.Builder <Restriction> builder = ImmutableList.builder();
      for (String value : requirement.getValueList()) {
        Node name = NodeUtil.newQName(compiler, getNameFromValue(value));
        String restrictedDecl = getTypeFromValue(value);
        if (name == null || restrictedDecl == null) {
          throw new InvalidRequirementSpec("bad prop value");
        }

        FunctionTypeI restrictedCallType = ConformanceUtil.evaluateTypeString(
            compiler, restrictedDecl).toMaybeFunctionType();
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
          } else if (n.isGetProp() && n.getLastChild().getString().equals("call")
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

    private static String getNameFromValue(String specName) {
      int index = specName.indexOf(':');
      if (index < 1) {
        return null;
      }
      return specName.substring(0, index);
    }

    private static String getTypeFromValue(String specName) {
      int index = specName.indexOf(':');
      if (index < 1) {
        return null;
      }
      return specName.substring(index + 1);
    }
  }

  /**
   * Banned property call rule
   */
  static class RestrictedMethodCall extends AbstractRule {
    private static class Restriction {
      final String type;
      final String property;
      final FunctionTypeI restrictedCallType;

      Restriction(
          String type, String property, FunctionTypeI restrictedCallType) {
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

      ImmutableList.Builder <Restriction> builder = ImmutableList.builder();
      for (String value : requirement.getValueList()) {
        String type = getClassFromDeclarationName(value);
        String property = getPropertyFromDeclarationName(value);
        String restrictedDecl = getTypeFromValue(value);
        if (type == null || property == null || restrictedDecl == null) {
          throw new InvalidRequirementSpec("bad prop value");
        }

        FunctionTypeI restrictedCallType = ConformanceUtil.evaluateTypeString(
            compiler, restrictedDecl).toMaybeFunctionType();
        if (restrictedCallType == null) {
          throw new InvalidRequirementSpec("invalid conformance type");
        }
        builder.add(new Restriction(type, property, restrictedCallType));
      }

      restrictions = builder.build();
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      if (NodeUtil.isGet(n)
          && ConformanceUtil.isCallTarget(n)
          && n.getLastChild().isString()) {
        for (int i = 0; i < restrictions.size(); i++) {
          Restriction r = restrictions.get(i);
          ConformanceResult result = ConformanceResult.CONFORMANCE;
          if (matchesProp(n, r)) {
            result = checkConformance(t, n, r, false);
          } else if (n.getLastChild().getString().equals("call")
              && matchesProp(n.getFirstChild(), r)) {
            // handle .call invocation
            result = checkConformance(t, n, r, true);
          }
          // TODO(johnlenz): should "apply" always be a possible violation?
          if (result.level != ConformanceLevel.CONFORMANCE) {
            return result;
          }
        }
      }
      return ConformanceResult.CONFORMANCE;
    }

    private ConformanceResult checkConformance(
        NodeTraversal t, Node n, Restriction r, boolean isCallInvocation) {
      TypeIRegistry registry = t.getCompiler().getTypeIRegistry();
      TypeI methodClassType = registry.getGlobalType(r.type);
      Node lhs = isCallInvocation ? n.getFirstFirstChild() : n.getFirstChild();
      if (methodClassType != null && lhs.getTypeI() != null) {
        TypeI targetType = lhs.getTypeI().restrictByNotNullOrUndefined();
        if (targetType.isUnknownType()
            || targetType.isUnresolved()
            || targetType.isTop()
            || targetType.isEquivalentTo(registry.getNativeType(JSTypeNative.OBJECT_TYPE))) {
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
      return n.isGetProp() && n.getLastChild().getString().equals(r.property);
    }

    /**
     * From a provide name extract the method name.
     */
    private static String getPropertyFromDeclarationName(String specName)
        throws InvalidRequirementSpec {
      String[] parts = removeTypeDecl(specName).split("\\.prototype\\.");
      checkState(parts.length == 1 || parts.length == 2);
      if (parts.length == 2) {
        return parts[1];
      }
      return null;
    }

    /**
     * From a provide name extract the class name.
     */
    private static String getClassFromDeclarationName(String specName)
        throws InvalidRequirementSpec {
      String tmp = removeTypeDecl(specName);
      String[] parts = tmp.split("\\.prototype\\.");
      checkState(parts.length == 1 || parts.length == 2);
      if (parts.length == 2) {
        return parts[0];
      }
      return null;
    }

    private static String removeTypeDecl(String specName)
        throws InvalidRequirementSpec {
      int index = specName.indexOf(':');
      if (index < 1) {
        throw new InvalidRequirementSpec("value should be in the form NAME:TYPE");
      }
      return specName.substring(0, index);
    }

    private static String getTypeFromValue(String specName) {
      int index = specName.indexOf(':');
      if (index < 1) {
        return null;
      }
      return specName.substring(index + 1);
    }
  }


  /**
   * Banned Code Pattern rule
   */
  static class BannedCodePattern extends AbstractRule {
    private final ImmutableList<TemplateAstMatcher> restrictions;

    BannedCodePattern(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);

      if (requirement.getValueCount() == 0) {
        throw new InvalidRequirementSpec("missing value");
      }

      ImmutableList.Builder <TemplateAstMatcher> builder =
          ImmutableList.builder();
      for (String value : requirement.getValueList()) {
        Node parseRoot = new JsAst(SourceFile.fromCode(
            "template", value)).getAstRoot(compiler);
        if (!parseRoot.hasOneChild()
            || !parseRoot.getFirstChild().isFunction()) {
          throw new InvalidRequirementSpec(
              "invalid conformance template: " + value);
        }
        Node templateRoot = parseRoot.getFirstChild();
        TemplateAstMatcher astMatcher =
            new TemplateAstMatcher(compiler.getTypeIRegistry(), templateRoot, typeMatchingStrategy);
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

  /**
   * A custom rule proxy, for rules that we load dynamically.
   */
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
    public void check(NodeTraversal t, Node n) {
      customRule.check(t, n);
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
          if (cause instanceof InvalidRequirementSpec) {
            throw (InvalidRequirementSpec) cause;
          }
          throw new RuntimeException(cause);
        }
        return rule;
      } catch (InstantiationException | IllegalAccessException | IllegalArgumentException e) {
        throw new RuntimeException(e);
      }
    }

    private Constructor<?> getRuleConstructor(Class<Rule> cls)
        throws InvalidRequirementSpec {
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

    private static final TypeToken<Rule> RULE_TYPE =
        new TypeToken<Rule>() {};

    private static final TypeToken<AbstractCompiler> COMPILER_TYPE =
        new TypeToken<AbstractCompiler>() {};

    private static final TypeToken<Requirement> REQUIREMENT_TYPE =
        new TypeToken<Requirement>() {};

    private Class<Rule> getRuleClass(
        String className) throws InvalidRequirementSpec {
      Class<?> customClass;
      try {
        customClass = Class.forName(className);
      } catch (ClassNotFoundException e) {
        throw new InvalidRequirementSpec("JavaClass not found.");
      }
      if (RULE_TYPE.isSupertypeOf(TypeToken.of(customClass))) {
        @SuppressWarnings("unchecked") // Assignable to Rule;
        Class<Rule> ruleClass = (Class<Rule>) customClass;
        return ruleClass;
      }
      throw new InvalidRequirementSpec("JavaClass is not a rule.");
    }
  }

  /**
   * Banned @expose
   */
  public static final class BanExpose extends AbstractRule {
    public BanExpose(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      JSDocInfo info = n.getJSDocInfo();
      if (info != null && info.isExpose()) {
        return ConformanceResult.VIOLATION;
      }
      return ConformanceResult.CONFORMANCE;
    }
  }

  /**
   * Require "use strict" rule
   */
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
      if (n.isScript()) {
        Set<String> directives = n.getDirectives();
        if (directives == null || !directives.contains("use strict")) {
          return ConformanceResult.VIOLATION;
        }
      }
      return ConformanceResult.CONFORMANCE;
    }
  }

  /**
   * Banned throw of non-error object types.
   */
  public static final class BanThrowOfNonErrorTypes extends AbstractRule {
    final TypeI errorObjType;
    public BanThrowOfNonErrorTypes(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
      errorObjType = compiler.getTypeIRegistry().getGlobalType("Error");
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      if (errorObjType != null && n.isThrow()) {
        TypeI thrown = n.getFirstChild().getTypeI();
        if (thrown != null) {
          // Allow vague types, as is typical of re-throws of exceptions
          if (!thrown.isUnknownType()
              && !thrown.isTop()
              && !thrown.isBottom()
              && !thrown.isSubtypeOf(errorObjType)) {
            return ConformanceResult.VIOLATION;
          }
        }
      }
      return ConformanceResult.CONFORMANCE;
    }
  }


  /**
   * Banned dereferencing null or undefined types.
   */
  public static final class BanNullDeref extends AbstractTypeRestrictionRule {
    public BanNullDeref(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      boolean violation;

      switch (n.getToken()) {
          case GETPROP:
          case GETELEM:
          case NEW:
          case CALL:
            violation = report(n.getFirstChild());
            break;
          case IN:
            violation = report(n.getLastChild());
            break;
          default:
            violation = false;
            break;
      }

      return violation ? ConformanceResult.VIOLATION : ConformanceResult.CONFORMANCE;
    }

    boolean report(Node n) {
      return n.getTypeI() != null
          && isKnown(n)
          && invalidDeref(n)
          && !isWhitelistedType(n);
    }

    // Whether the type is known to be invalid to dereference.
    private boolean invalidDeref(Node n) {
      TypeI type = n.getTypeI();
      // TODO(johnlenz): top type should not be allowed here
      return !type.isTop() && (type.isNullable() || type.isVoidable());
    }
  }


  /**
   * Banned unknown "this" types.
   */
  public static final class BanUnknownThis extends AbstractTypeRestrictionRule {
    private final Set<Node> reports = Sets.newIdentityHashSet();
    public BanUnknownThis(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      if (n.isThis()) {
        TypeI type = n.getTypeI();
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
   *  - it is immediately cast,
   *  - it is a @template type (until template type
   * restricts are enabled) or
   *  - the value is unused.
   *  - the "this" type is unknown (as this is expected to be used with
   * BanUnknownThis which would have already reported the root cause).
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
          && !isTypeVariable(n)
          && isUsed(n) // skip most assignments, etc
          && !isTypeImmediatelyTightened(n)) {
        return ConformanceResult.VIOLATION;
      }
      return ConformanceResult.CONFORMANCE;
    }

    private boolean isKnownThis(Node n) {
      return n.isThis() && !isUnknown(n);
    }
  }

  /**
   * Banned unknown type references of the form "instance.prop" unless
   * (a) it is immediately cast/asserted, or
   * (b) it is a @template type (until template type restrictions are enabled), or
   * (c) the value is unused, or
   * (d) the source object type is unknown (to avoid error cascades)
   */
  public static final class BanUnknownTypedClassPropsReferences
      extends AbstractTypeRestrictionRule {

    public BanUnknownTypedClassPropsReferences(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      if (n.isGetProp()
          && isSomeUnknownType(n)
          && isUsed(n) // skip most assignments, etc
          && !isTypeImmediatelyTightened(n)
          && isCheckablePropertySource(n.getFirstChild()) // not a cascading unknown
          && !isTypeVariable(n)
          && !isDeclaredUnknown(n)
          && !isInWhitelistedSyntheticFile(n)) {
        String propName = n.getLastChild().getString();
        String typeName = n.getFirstChild().getTypeI().toString();
        return new ConformanceResult(ConformanceLevel.VIOLATION,
            "The property \"" + propName + "\" on type \"" + typeName + "\"");
      }
      return ConformanceResult.CONFORMANCE;
    }

    private boolean isInWhitelistedSyntheticFile(Node n) {
      // TODO(tbreisacher): Figure out how to remove this restriction.
      String filename = n.getSourceFileName();
      return (filename != null
          && (filename.contains("synthetic:es6/map")
              || filename.contains("synthetic:es6/weakmap")
              || filename.contains("synthetic:es6/weakset")
              || filename.contains("synthetic:runtime_type_check")));
    }

    private boolean isCheckablePropertySource(Node n) {
      return isKnown(n)
          && !isTop(n)
          && isClassType(n)
          && !isNativeObjectType(n)
          && !isWhitelistedType(n);
    }

    private boolean isClassType(Node n) {
      ObjectTypeI type = n.getTypeI().restrictByNotNullOrUndefined().toMaybeObjectType();
      if (type != null && type.isInstanceType()) {
        FunctionTypeI ctor = type.getConstructor();
        if (ctor != null) {
          JSDocInfo info = ctor.getJSDocInfo();
          if (info != null && info.isConstructorOrInterface()) {
            return true;
          }
        }
      }
      return false;
    }

    private boolean isDeclaredUnknown(Node n) {
      Node target = n.getFirstChild();
      ObjectTypeI targetType = target.getTypeI().toMaybeObjectType();
      if (targetType != null) {
        JSDocInfo info = targetType.getPropertyJSDocInfo(n.getLastChild().getString());
        if (info != null && info.hasType()) {
          JSTypeExpression expr = info.getType();
          Node typeExprNode = expr.getRoot();
          if (typeExprNode.getToken() == Token.QMARK && !typeExprNode.hasChildren()) {
            return true;
          }
        }
      }
      return false;
    }
  }


  /**
   * Banned accessing properties from objects that are unresolved
   * forward-declared type names. For legacy reasons this is allowed but
   * causes unexpected weaknesses in the type inference.
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
        TypeI type = target.getTypeI();
        TypeI nonConformingPart = getNonConformingPart(type);
        if (nonConformingPart != null && !isTypeImmediatelyTightened(n)) {
          return new ConformanceResult(
              ConformanceLevel.VIOLATION,
              "Reference to type '" + nonConformingPart + "' never resolved.");
        }
      }
      return ConformanceResult.CONFORMANCE;
    }

    private static @Nullable TypeI getNonConformingPart(TypeI type) {
      if (type == null) {
        return null;
      }
      if (type.isUnionType()) {
        // unwrap union types which might contain unresolved type name
        // references for example {Foo|undefined}
        for (TypeI part : type.getUnionMembers()) {
          TypeI nonConformingPart = getNonConformingPart(part);
          if (nonConformingPart != null) {
            return nonConformingPart;
          }
        }
      } else if (type.isUnresolved()) {
        return type;
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
      TypeI nonConformingPart = BanUnresolvedType.getNonConformingPart(n.getTypeI());
      if (nonConformingPart != null && !isTypeImmediatelyTightened(n)) {
        return new ConformanceResult(
            ConformanceLevel.VIOLATION,
            "Reference to type '" + nonConformingPart + "' never resolved.");
      }
      return ConformanceResult.CONFORMANCE;
    }
  }


  /**
   * Banned global var declarations.
   */
  public static final class BanGlobalVars extends AbstractRule {
    public BanGlobalVars(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      if (t.inGlobalScope()
          && NodeUtil.isDeclaration(n)
          && !n.getBooleanProp(Node.IS_NAMESPACE)
          && !isWhitelisted(n)) {
        Node enclosingScript = NodeUtil.getEnclosingScript(n);
        if (enclosingScript != null && enclosingScript.getBooleanProp(Node.GOOG_MODULE)) {
          return ConformanceResult.CONFORMANCE;
        }
        return ConformanceResult.VIOLATION;
      }
      return ConformanceResult.CONFORMANCE;
    }

    private boolean isWhitelisted(Node n) {
      return (n.isVar() || n.isFunction()) && isWhitelistedName(n.getFirstChild().getString());
    }

    private boolean isWhitelistedName(String name) {
      return name.equals("$jscomp")
          || name.startsWith("$jscomp$compprop")
          || ClosureRewriteModule.isModuleContent(name)
          || ClosureRewriteModule.isModuleExport(name);
    }
  }

  /**
   * Requires source files to contain a top-level {@code @fileoverview} block
   * with an explicit visibility annotation.
   */
  public static final class RequireFileoverviewVisibility extends AbstractRule {
    public RequireFileoverviewVisibility(
        AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      if (!n.isScript()) {
        return ConformanceResult.CONFORMANCE;
      }
      JSDocInfo docInfo = n.getJSDocInfo();
      if (docInfo == null || !docInfo.hasFileOverview()) {
        return ConformanceResult.VIOLATION;
      }
      Visibility v = docInfo.getVisibility();
      if (v == null || v == Visibility.INHERITED) {
        return ConformanceResult.VIOLATION;
      }
      return ConformanceResult.CONFORMANCE;
    }
  }

  /**
   * Bans {@code document.createElement} and similar methods with string literal parameter specified
   * in {@code value}, e.g. {@code value: 'script'}. The purpose of banning these is that they don't
   * provide the type information which hinders other rules. Authors should use e.g. {@code
   * goog.dom.createElement(goog.dom.TagName.SCRIPT)} which returns HTMLScriptElement.
   */
  public static final class BanCreateElement extends AbstractRule {
    private final Set<String> bannedTags;
    private final TypeI domHelperType;
    private final TypeI documentType;

    public BanCreateElement(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
      bannedTags = new HashSet<>();
      for (String value : requirement.getValueList()) {
        bannedTags.add(Ascii.toLowerCase(value));
      }
      if (bannedTags.isEmpty()) {
        throw new InvalidRequirementSpec("Specify one or more values.");
      }
      domHelperType = compiler.getTypeIRegistry().getGlobalType("goog.dom.DomHelper");
      documentType = compiler.getTypeIRegistry().getGlobalType("Document");
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      if (n.isCall()) {
        Node tag = n.getSecondChild();
        if (tag != null
            && tag.isString()
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
      String functionName = target.getLastChild().getString();
      if (!"createElement".equals(functionName) && !"createDom".equals(functionName)) {
        return ConformanceResult.CONFORMANCE;
      }

      Node srcObj = target.getFirstChild();
      if (srcObj.matchesQualifiedName("goog.dom")) {
        return ConformanceResult.VIOLATION;
      }
      TypeI type = srcObj.getTypeI();
      if (type == null || type.isUnknownType() || type.isUnresolved() || type.isTop()) {
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
   * Tag name might be also {@code *} to ban the attribute in any tag.
   * Note that string literal values assigned to banned attributes are allowed as they couldn't be
   * attacker controlled.
   */
  public static final class BanCreateDom extends AbstractRule {
    private final List<String[]> bannedTagAttrs;
    private final TypeI domHelperType;
    private final TypeI classNameTypes;

    public BanCreateDom(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
      bannedTagAttrs = new ArrayList<>();
      for (String value : requirement.getValueList()) {
        String[] tagAttr = value.split("\\.");
        if (tagAttr.length != 2 || tagAttr[0].isEmpty() || tagAttr[1].isEmpty()) {
          throw new InvalidRequirementSpec("Values must be in the format tagname.attribute.");
        }
        tagAttr[0] = tagAttr[0].toLowerCase();
        bannedTagAttrs.add(tagAttr);
      }
      if (bannedTagAttrs.isEmpty()) {
        throw new InvalidRequirementSpec("Specify one or more values.");
      }
      domHelperType = compiler.getTypeIRegistry().getGlobalType("goog.dom.DomHelper");
      classNameTypes = compiler.getTypeIRegistry().createUnionType(ImmutableList.of(
          compiler.getTypeIRegistry().getNativeType(JSTypeNative.STRING_TYPE),
          compiler.getTypeIRegistry().getNativeType(JSTypeNative.ARRAY_TYPE),
          compiler.getTypeIRegistry().getNativeType(JSTypeNative.NULL_TYPE),
          compiler.getTypeIRegistry().getNativeType(JSTypeNative.VOID_TYPE)));
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

      Collection<String> tagNames = getTagNames(n.getSecondChild());
      Node attrs = n.getChildAtIndex(2);
      TypeI attrsType = attrs.getTypeI();

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
          if (NodeUtil.isStringLiteralValue(prop)) {
            // Ignore string literal values.
            continue;
          }
          return violation;
        }
      }

      return ConformanceResult.CONFORMANCE;
    }

    private ImmutableCollection<String> getTagNames(Node tag) {
      if (tag.isString()) {
        return ImmutableSet.of(tag.getString().toLowerCase());
      } else if (tag.isGetProp() && tag.getFirstChild().matchesQualifiedName("goog.dom.TagName")) {
        return ImmutableSet.of(tag.getLastChild().getString().toLowerCase());
      }
      // TODO(jakubvrana): Support union, e.g. {!TagName<!HTMLDivElement>|!TagName<!HTMLBRElement>}.
      TypeI type = tag.getTypeI();
      if (type == null || !type.isGenericObjectType()) {
        return null;
      }
      ObjectTypeI typeAsObj = type.toMaybeObjectType();
      if (typeAsObj.getRawType().getDisplayName().equals("goog.dom.TagName")) {
        TypeI tagType = Iterables.getOnlyElement(typeAsObj.getTemplateTypes());
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
      if (!"createDom".equals(target.getLastChild().getString())) {
        return false;
      }

      Node srcObj = target.getFirstChild();
      if (srcObj.matchesQualifiedName("goog.dom")) {
        return true;
      }
      TypeI type = srcObj.getTypeI();
      if (type == null) {
        return false;
      }
      if (type.isEquivalentTo(domHelperType)) {
        return true;
      }
      return false;
    }
  }
}
