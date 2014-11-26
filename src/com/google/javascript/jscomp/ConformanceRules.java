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

import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;
import com.google.javascript.jscomp.CheckConformance.InvalidRequirementSpec;
import com.google.javascript.jscomp.CheckConformance.Rule;
import com.google.javascript.jscomp.CodingConvention.AssertionFunctionSpec;
import com.google.javascript.jscomp.Requirement.Type;
import com.google.javascript.jscomp.parsing.JsDocInfoParser;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.Property;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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
public final class ConformanceRules {

  private ConformanceRules() {}

  /** Possible check check results */
  public static enum ConformanceResult {
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
    final ImmutableList<String> whitelist;
    final ImmutableList<String> onlyApplyTo;
    @Nullable final Pattern whitelistRegexp;
    @Nullable final Pattern onlyApplyToRegexp;

    public AbstractRule(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      if (!requirement.hasErrorMessage()) {
        throw new InvalidRequirementSpec("missing message");
      }
      this.compiler = compiler;
      message = requirement.getErrorMessage();
      whitelist = ImmutableList.copyOf(requirement.getWhitelistList());
      whitelistRegexp = buildPattern(
          requirement.getWhitelistRegexpList());
      onlyApplyTo = ImmutableList.copyOf(requirement.getOnlyApplyToList());
      onlyApplyToRegexp = buildPattern(
          requirement.getOnlyApplyToRegexpList());

      boolean hasWhitelist = !whitelist.isEmpty()
          || whitelistRegexp != null;
      boolean hasOnlyApplyTo = !onlyApplyTo.isEmpty()
          || onlyApplyToRegexp != null;

      if (hasWhitelist && hasOnlyApplyTo) {
        throw new IllegalArgumentException(
            "It is an error to specify both whitelist and only_apply_to");
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
    private boolean shouldCheckConformance(Node n) {
      String srcfile = NodeUtil.getSourceName(n);
      if (srcfile == null) {
        return true;
      } else if (!onlyApplyTo.isEmpty() || onlyApplyToRegexp != null) {
        return pathIsInListOrRegexp(srcfile, onlyApplyTo, onlyApplyToRegexp);
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
      ConformanceResult confidence = checkConformance(t, n);
      if (confidence != ConformanceResult.CONFORMANCE
          && shouldCheckConformance(n)) {
        report(t, n, confidence);
      }
    }

    /**
     * Report a conformance warning for the given node.
     * @param n The node representing the violating code.
     * @param result The result representing the confidence of the violation.
     */
    protected void report(
        NodeTraversal t, Node n, ConformanceResult result) {
      DiagnosticType msg = (result == ConformanceResult.VIOLATION)
          ? CheckConformance.CONFORMANCE_VIOLATION
          : CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION;
      t.report(n, msg, message);
    }
  }

  /**
   * Banned name rule
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
    private final List<String> names;

    BannedName(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
      if (requirement.getValueCount() == 0) {
        throw new InvalidRequirementSpec("missing value");
      }
      names = requirement.getValueList();
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      if (n.isGetProp() || n.isName()) {
        // TODO(johnlenz): restrict to global names
        if (n.isQualifiedName()) {
          for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (n.matchesQualifiedName(name)) {
              return ConformanceResult.VIOLATION;
            }
          }
        }
      }
      return ConformanceResult.CONFORMANCE;
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

      Preconditions.checkArgument(requirement.getType() == Type.BANNED_PROPERTY
          || requirement.getType() == Type.BANNED_PROPERTY_READ
          || requirement.getType() == Type.BANNED_PROPERTY_WRITE
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
      for (int i = 0; i < props.size(); i++) {
        Property prop = props.get(i);
        ConformanceResult result = checkConformance(t, n, prop);
        if (result != ConformanceResult.CONFORMANCE) {
          return result;
        }
      }
      return ConformanceResult.CONFORMANCE;
    }

    private ConformanceResult checkConformance(NodeTraversal t, Node n, Property prop) {
      if (isCandidatePropUse(n, prop)) {
        JSTypeRegistry registry = t.getCompiler().getTypeRegistry();
        JSType methodClassType = registry.getType(prop.type);
        Node lhs = n.getFirstChild();
        if (methodClassType != null && lhs.getJSType() != null) {
          JSType targetType = lhs.getJSType().restrictByNotNullOrUndefined();
          if (targetType.isUnknownType()
             || targetType.isAllType()
             || targetType.isEquivalentTo(
                 registry.getNativeType(JSTypeNative.OBJECT_TYPE))) {
            return ConformanceResult.POSSIBLE_VIOLATION;
          } else if (targetType.isSubtype(methodClassType)) {
            return ConformanceResult.VIOLATION;
          }
        }
      }
      return ConformanceResult.CONFORMANCE;
    }

    /**
     * Determines if {@code n} is a potentially banned use of {@code prop}.
     *
     * Specifically this is the case if {@code n} is a use of a property with
     * the name specified by {@code prop}. Furthermore, if the conformance
     * requirement under consideration only bans assignment to the property,
     * {@code n} is only a candidate if it is an l-value.
     */
    private boolean isCandidatePropUse(Node n, Property prop) {
      if (NodeUtil.isGet(n) && n.getLastChild().isString()
          && n.getLastChild().getString().equals(prop.property)) {
        if (requirementType == Type.BANNED_PROPERTY_WRITE) {
          return NodeUtil.isLValue(n);
        } else if (requirementType == Type.BANNED_PROPERTY_READ) {
          return !NodeUtil.isLValue(n) && NodeUtil.isExpressionResultUsed(n);
        } else if (requirementType == Type.BANNED_PROPERTY_CALL) {
          return ConformanceUtil.isCallTarget(n);
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
      Preconditions.checkState(parts.length == 1 || parts.length == 2);
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
      Preconditions.checkState(parts.length == 1 || parts.length == 2);
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

    static JSType evaluateTypeString(
        AbstractCompiler compiler, String expression)
        throws InvalidRequirementSpec {
      Node typeNodes = JsDocInfoParser.parseTypeString(expression);
      if (typeNodes == null) {
        throw new InvalidRequirementSpec("bad type expression");
      }
      JSTypeExpression typeExpr = new JSTypeExpression(
          typeNodes, "conformance");
      return typeExpr.evaluate(null, compiler.getTypeRegistry());
    }

    /**
     * Validate the parameters and the 'this' type, of a new or call.
     * @see TypeCheck#visitParameterList
     */
    static boolean validateCall(
        AbstractCompiler compiler,
        Node callOrNew,
        FunctionType functionType,
        boolean isCallInvocation) {
      Preconditions.checkState(callOrNew.isCall() || callOrNew.isNew());

      return validateParameterList(compiler, callOrNew, functionType, isCallInvocation)
          && validateThis(callOrNew, functionType, isCallInvocation);
    }

    private static boolean validateThis(
        Node callOrNew,
        FunctionType functionType,
        boolean isCallInvocation) {

      if (callOrNew.isNew()) {
        return true;
      }

      JSType thisType = functionType.getTypeOfThis();
      if (thisType.isUnknownType()) {
        return true;
      }

      Node thisNode = isCallInvocation
          ? callOrNew.getFirstChild().getNext()
          : callOrNew.getFirstChild().getFirstChild();
      JSType thisNodeType =
          thisNode.getJSType().restrictByNotNullOrUndefined();
      return thisNodeType.isSubtype(thisType);
    }

    private static boolean validateParameterList(
        AbstractCompiler compiler,
        Node callOrNew,
        FunctionType functionType,
        boolean isCallInvocation) {
      Iterator<Node> arguments = callOrNew.children().iterator();
      arguments.next(); // skip the function name
      if (isCallInvocation && arguments.hasNext()) {
        arguments.next();
      }

      Iterator<Node> parameters = functionType.getParameters().iterator();
      Node parameter = null;
      Node argument = null;
      while (arguments.hasNext() &&
             (parameters.hasNext() ||
              parameter != null && parameter.isVarArgs())) {
        // If there are no parameters left in the list, then the while loop
        // above implies that this must be a var_args function.
        if (parameters.hasNext()) {
          parameter = parameters.next();
        }
        argument = arguments.next();

        if (!validateParameter(
            getJSType(compiler, argument), getJSType(compiler, parameter))) {
          return false;
        }
      }

      int numArgs = callOrNew.getChildCount() - 1;
      if (isCallInvocation && numArgs > 0) {
        numArgs -= 1;
      }
      int minArgs = functionType.getMinArguments();
      int maxArgs = functionType.getMaxArguments();
      return minArgs <= numArgs && numArgs <= maxArgs;
    }

    /**
     * Expect that the type of an argument matches the type of the parameter
     * that it's fulfilling.
     *
     * @param argType The type of the argument.
     * @param paramType The type of the parameter.
     */
    static boolean validateParameter(JSType argType, JSType paramType) {
      return argType.isSubtype(paramType);
    }

    /**
     * This method gets the JSType from the Node argument and verifies that it is
     * present.
     */
    static JSType getJSType(AbstractCompiler compiler, Node n) {
      JSType jsType = n.getJSType();
      if (jsType == null) {
        return getNativeType(compiler, UNKNOWN_TYPE);
      } else {
        return jsType;
      }
    }

    static JSType getNativeType(AbstractCompiler compiler, JSTypeNative typeId) {
      return compiler.getTypeRegistry().getNativeType(typeId);
    }

  }

  /**
   * Restricted name call rule
   */
  static class RestrictedNameCall extends AbstractRule {
    private static class Restriction {
      final String name;
      final FunctionType restrictedCallType;

      Restriction(String name, FunctionType restrictedCallType) {
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
        String name = getNameFromValue(value);
        String restrictedDecl = getTypeFromValue(value);
        if (name == null || restrictedDecl == null) {
          throw new InvalidRequirementSpec("bad prop value");
        }

        FunctionType restrictedCallType = ConformanceUtil.evaluateTypeString(
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
      final FunctionType restrictedCallType;

      Restriction(
          String type, String property, FunctionType restrictedCallType) {
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

        FunctionType restrictedCallType = ConformanceUtil.evaluateTypeString(
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
          if (result != ConformanceResult.CONFORMANCE) {
            return result;
          }
        }
      }
      return ConformanceResult.CONFORMANCE;
    }

    private boolean matchesProp(Node n, Restriction r) {
      return n.isGetProp() && n.getLastChild().getString().equals(r.property);
    }

    private ConformanceResult checkConformance(
        NodeTraversal t, Node n, Restriction r, boolean isCallInvocation) {
      JSTypeRegistry registry = t.getCompiler().getTypeRegistry();
      JSType methodClassType = registry.getType(r.type);
      Node lhs = isCallInvocation
          ? n.getFirstChild().getFirstChild()
          : n.getFirstChild();
      if (methodClassType != null && lhs.getJSType() != null) {
        JSType targetType = lhs.getJSType().restrictByNotNullOrUndefined();
        if (targetType.isUnknownType()
           || targetType.isNoResolvedType()
           || targetType.isAllType()
           || targetType.isEquivalentTo(
               registry.getNativeType(JSTypeNative.OBJECT_TYPE))) {
          if (!ConformanceUtil.validateCall(
              compiler, n.getParent(), r.restrictedCallType,
              isCallInvocation)) {
            return ConformanceResult.POSSIBLE_VIOLATION;
          }
        } else if (targetType.isSubtype(methodClassType)) {
          if (!ConformanceUtil.validateCall(
              compiler, n.getParent(), r.restrictedCallType,
              isCallInvocation)) {
            return ConformanceResult.VIOLATION;
          }
        }
      }
      return ConformanceResult.CONFORMANCE;
    }

    /**
     * From a provide name extract the method name.
     */
    private static String getPropertyFromDeclarationName(String specName)
        throws InvalidRequirementSpec {
      String[] parts = removeTypeDecl(specName).split("\\.prototype\\.");
      Preconditions.checkState(parts.length == 1 || parts.length == 2);
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
      Preconditions.checkState(parts.length == 1 || parts.length == 2);
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
        TemplateAstMatcher astMatcher = new TemplateAstMatcher(
            compiler, templateRoot);
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
      return possibleViolation
          ? ConformanceResult.POSSIBLE_VIOLATION
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
          throw e;
        }
        return rule;
      } catch (InvalidRequirementSpec e) {
        throw e;
      } catch (Exception e) {
        throw Throwables.propagate(e);
      }
    }

    private Constructor<?> getRuleConstructor(Class<Rule> cls)
        throws InvalidRequirementSpec {
      for (Constructor<?> ctor : cls.getConstructors()) {
        Class<?> paramClasses[] = ctor.getParameterTypes();
        if (paramClasses.length == 2) {
          TypeToken<?> param1 = TypeToken.of(paramClasses[0]);
          TypeToken<?> param2 = TypeToken.of(paramClasses[1]);
          if (param1.isAssignableFrom(COMPILER_TYPE)
              && param2.isAssignableFrom(REQUIREMENT_TYPE)) {
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
      if (RULE_TYPE.isAssignableFrom(TypeToken.of(customClass))) {
        @SuppressWarnings("unchecked")  // Assignable to Rule;
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
   * Banned throw of non-error object types.
   */
  public static final class BanThrowOfNonErrorTypes extends AbstractRule {
    final JSType errorObjType;
    public BanThrowOfNonErrorTypes(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
      errorObjType = compiler.getTypeRegistry().getType("Error");
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
              && !thrown.isSubtype(errorObjType)) {
            return ConformanceResult.VIOLATION;
          }
        }
      }
      return ConformanceResult.CONFORMANCE;
    }
  }

  /**
   * Banned unknown "this" types.
   */
  public static final class BanUnknownThis extends AbstractRule {
    private final Set<Node> reports = Sets.newIdentityHashSet();
    private final ImmutableList<AssertionFunctionSpec> assertions;
    public BanUnknownThis(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
      assertions = ImmutableList.copyOf(
          compiler.getCodingConvention().getAssertionFunctions());
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      if (n.isThis()) {
        JSType type = n.getJSType();
        if (type != null && type.isUnknownType() && !isWhiteListed(n)) {
          Node root = t.getScopeRoot();
          if (!reports.contains(root)) {
            reports.add(root);
            return ConformanceResult.VIOLATION;
          }
        }
      }
      return ConformanceResult.CONFORMANCE;
    }

    private boolean isWhiteListed(Node n) {
      return n.getParent().isCast() || isAssertionCall(n.getParent());
    }

    private boolean isAssertionCall(Node n) {
      if (n.isCall() && n.getFirstChild().isQualifiedName()) {
        Node target = n.getFirstChild();
        for (int i = 0; i < assertions.size(); i++) {
          if (target.matchesQualifiedName(
              assertions.get(i).getFunctionName())) {
            return true;
          }
        }
      }
      return false;
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
          && isDeclaration(n)
          && !n.getBooleanProp(Node.IS_NAMESPACE)
          && !isWhitelisted(n)) {
        return ConformanceResult.VIOLATION;
      }
      return ConformanceResult.CONFORMANCE;
    }

    private boolean isDeclaration(Node n) {
      return NodeUtil.isNameDeclaration(n)
          || NodeUtil.isFunctionDeclaration(n)
          || NodeUtil.isClassDeclaration(n);
    }

    private boolean isWhitelisted(Node n) {
      return n.isVar() && n.getFirstChild().getString().equals("$jscomp");
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
   * Requires top-level Closure-style "declarations"
   * (example: {@code foo.bar.Baz = ...;}) to have explicit visibility
   * annotations, either at the declaration site or in the {@code @fileoverview}
   * block.
   */
  public static final class NoImplicitlyPublicDecls extends AbstractRule {
    public NoImplicitlyPublicDecls(
        AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      if (!t.inGlobalScope()
          || !n.isExprResult()
          || !n.getFirstChild().isAssign()
          || n.getFirstChild().getLastChild() == null
          || n.getFirstChild().getLastChild().isObjectLit()
          || isWizDeclaration(n)) {
        return ConformanceResult.CONFORMANCE;
      }
      JSDocInfo ownJsDoc = n.getFirstChild().getJSDocInfo();
      if (ownJsDoc != null && ownJsDoc.isConstructor()) {
        FunctionType functionType = n.getFirstChild()
            .getJSType()
            .toMaybeFunctionType();
        if (functionType == null) {
          return ConformanceResult.CONFORMANCE;
        }
        ObjectType instanceType = functionType.getInstanceType();
        if (instanceType == null) {
          return ConformanceResult.CONFORMANCE;
        }
        ConformanceResult result = checkCtorProperties(instanceType);
        if (result != ConformanceResult.CONFORMANCE) {
          return result;
        }
      }

      return visibilityAtDeclarationOrFileoverview(ownJsDoc, getScriptNode(n));
    }

    /**
     * Do not check Wiz-style declarations for implicit public visibility.
     * Example:
     * <code>
     * foo.Bar = wiz.service(...);
     * </code>
     * {@link WizPass} rewrites portions of the AST, and I believe it
     * does not propagate the constructor JsDoc properly. Until I have time
     * to investigate, this seems like a reasonable workaround.
     * TODO(brndn): get to the bottom of this. See b/18436759.
     */
    private static boolean isWizDeclaration(Node n) {
      Node lastChild = n.getFirstChild().getLastChild();
      if (!lastChild.isCall()) {
        return false;
      }
      Node getprop = lastChild.getFirstChild();
      if (getprop == null || !getprop.isGetProp()) {
        return false;
      }
      Node name = getprop.getFirstChild();
      if (name == null || !name.isName()) {
        return false;
      }
      return "wiz".equals(name.getString());
    }

    private static ConformanceResult checkCtorProperties(ObjectType type) {
      for (String propertyName : type.getOwnPropertyNames()) {
        Property prop = type.getOwnSlot(propertyName);
        JSDocInfo docInfo = prop.getJSDocInfo();
        Node scriptNode = getScriptNode(prop.getNode());
        ConformanceResult result = visibilityAtDeclarationOrFileoverview(
            docInfo, scriptNode);
        if (result != ConformanceResult.CONFORMANCE) {
          return result;
        }
      }
      return ConformanceResult.CONFORMANCE;
    }

    @Nullable private static Node getScriptNode(Node start) {
      for (Node up : start.getAncestors()) {
        if (up.isScript()) {
          return up;
        }
      }
      return null;
    }

    private static ConformanceResult visibilityAtDeclarationOrFileoverview(
        @Nullable JSDocInfo declaredJsDoc, @Nullable Node scriptNode) {
      if (declaredJsDoc != null
          && (declaredJsDoc.getVisibility() != Visibility.INHERITED
              || declaredJsDoc.isOverride())) {
        return ConformanceResult.CONFORMANCE;
      } else if (scriptNode != null
          && scriptNode.getJSDocInfo() != null
          && scriptNode.getJSDocInfo().getVisibility() !=
          Visibility.INHERITED) {
        return ConformanceResult.CONFORMANCE;
      } else {
        return ConformanceResult.VIOLATION;
      }
    }
  }
}
