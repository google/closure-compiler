/*
 * Copyright 2008 The Closure Compiler Authors.
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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.javascript.jscomp.TypeCheck.BAD_IMPLEMENTED_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.FUNCTION_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.GENERATOR_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.PROMISE_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.VOID_TYPE;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionBuilder;
import com.google.javascript.rhino.jstype.FunctionParamBuilder;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticTypedScope;
import com.google.javascript.rhino.jstype.TemplateType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A builder for FunctionTypes, because FunctionTypes are so
 * ridiculously complex. All methods return {@code this} for ease of use.
 *
 * Right now, this mostly uses JSDocInfo to infer type information about
 * functions. In the long term, developers should extend it to use other
 * signals by overloading the various "inferXXX" methods. For example, we
 * might want to use {@code goog.inherits} calls as a signal for inheritance, or
 * {@code return} statements as a signal for return type.
 *
 * NOTE(nicksantos): Organizationally, this feels like it should be in Rhino.
 * But it depends on some coding convention stuff that's really part
 * of JSCompiler.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
final class FunctionTypeBuilder {

  private final String fnName;
  private final AbstractCompiler compiler;
  private final CodingConvention codingConvention;
  private final JSTypeRegistry typeRegistry;
  private final Node errorRoot;
  private final TypedScope enclosingScope;

  private FunctionContents contents = UnknownFunctionContents.get();

  private JSType returnType = null;
  private boolean returnTypeInferred = false;
  private List<ObjectType> implementedInterfaces = null;
  private List<ObjectType> extendedInterfaces = null;
  private ObjectType baseType = null;
  private JSType thisType = null;
  private boolean isClass = false;
  private boolean isConstructor = false;
  private boolean makesStructs = false;
  private boolean makesDicts = false;
  private boolean isInterface = false;
  private boolean isRecord = false;
  private boolean isAbstract = false;
  private Node parametersNode = null;
  private ImmutableList<TemplateType> templateTypeNames = ImmutableList.of();
  private ImmutableList<TemplateType> constructorTemplateTypeNames = ImmutableList.of();
  private TypedScope declarationScope = null;
  private StaticTypedScope templateScope;

  static final DiagnosticType EXTENDS_WITHOUT_TYPEDEF = DiagnosticType.warning(
      "JSC_EXTENDS_WITHOUT_TYPEDEF",
      "@extends used without @constructor or @interface for {0}");

  static final DiagnosticType EXTENDS_NON_OBJECT = DiagnosticType.warning(
      "JSC_EXTENDS_NON_OBJECT",
      "{0} @extends non-object type {1}");

  static final DiagnosticType RESOLVED_TAG_EMPTY = DiagnosticType.warning(
      "JSC_RESOLVED_TAG_EMPTY",
      "Could not resolve type in {0} tag of {1}");

  static final DiagnosticType IMPLEMENTS_WITHOUT_CONSTRUCTOR =
      DiagnosticType.warning(
          "JSC_IMPLEMENTS_WITHOUT_CONSTRUCTOR",
          "@implements used without @constructor or @interface for {0}");

  static final DiagnosticType CONSTRUCTOR_REQUIRED =
      DiagnosticType.warning("JSC_CONSTRUCTOR_REQUIRED",
                             "{0} used without @constructor for {1}");

  static final DiagnosticType VAR_ARGS_MUST_BE_LAST = DiagnosticType.warning(
      "JSC_VAR_ARGS_MUST_BE_LAST",
      "variable length argument must be last");

  static final DiagnosticType OPTIONAL_ARG_AT_END = DiagnosticType.warning(
      "JSC_OPTIONAL_ARG_AT_END",
      "optional arguments must be at the end");

  static final DiagnosticType INEXISTENT_PARAM = DiagnosticType.warning(
      "JSC_INEXISTENT_PARAM",
      "parameter {0} does not appear in {1}''s parameter list");

  static final DiagnosticType TYPE_REDEFINITION = DiagnosticType.warning(
      "JSC_TYPE_REDEFINITION",
      "attempted re-definition of type {0}\n"
      + "found   : {1}\n"
      + "expected: {2}");

  static final DiagnosticType TEMPLATE_TRANSFORMATION_ON_CLASS =
      DiagnosticType.warning(
          "JSC_TEMPLATE_TRANSFORMATION_ON_CLASS",
          "Template type transformation {0} not allowed on classes or interfaces");

  static final DiagnosticType TEMPLATE_TYPE_DUPLICATED = DiagnosticType.warning(
      "JSC_TEMPLATE_TYPE_DUPLICATED",
      "Only one parameter type must be the template type");

  static final DiagnosticType TEMPLATE_TYPE_EXPECTED = DiagnosticType.warning(
      "JSC_TEMPLATE_TYPE_EXPECTED",
      "The template type must be a parameter type");

  static final DiagnosticType THIS_TYPE_NON_OBJECT =
      DiagnosticType.warning(
          "JSC_THIS_TYPE_NON_OBJECT",
          "@this type of a function must be an object\n" +
          "Actual type: {0}");

  static final DiagnosticType SAME_INTERFACE_MULTIPLE_IMPLEMENTS =
      DiagnosticType.warning(
          "JSC_SAME_INTERFACE_MULTIPLE_IMPLEMENTS",
          "Cannot @implement the same interface more than once\n" +
          "Repeated interface: {0}");

  static final DiagnosticGroup ALL_DIAGNOSTICS =
      new DiagnosticGroup(
          EXTENDS_WITHOUT_TYPEDEF,
          EXTENDS_NON_OBJECT,
          RESOLVED_TAG_EMPTY,
          IMPLEMENTS_WITHOUT_CONSTRUCTOR,
          CONSTRUCTOR_REQUIRED,
          VAR_ARGS_MUST_BE_LAST,
          OPTIONAL_ARG_AT_END,
          INEXISTENT_PARAM,
          TYPE_REDEFINITION,
          TEMPLATE_TRANSFORMATION_ON_CLASS,
          TEMPLATE_TYPE_DUPLICATED,
          TEMPLATE_TYPE_EXPECTED,
          THIS_TYPE_NON_OBJECT,
          SAME_INTERFACE_MULTIPLE_IMPLEMENTS);

  private class ExtendedTypeValidator implements Predicate<JSType> {
    @Override
    public boolean apply(JSType type) {
      ObjectType objectType = ObjectType.cast(type);
      if (objectType == null) {
        reportWarning(EXTENDS_NON_OBJECT, formatFnName(), type.toString());
        return false;
      }
      if (objectType.isEmptyType()) {
        reportWarning(RESOLVED_TAG_EMPTY, "@extends", formatFnName());
        return false;
      }
      if (objectType.isUnknownType()) {
        if (hasMoreTagsToResolve(objectType) || type.isTemplateType()) {
          return true;
        } else {
          reportWarning(RESOLVED_TAG_EMPTY, "@extends", fnName);
          return false;
        }
      }
      return true;
    }
  }

  private class ImplementedTypeValidator implements Predicate<JSType> {
    @Override
    public boolean apply(JSType type) {
      ObjectType objectType = ObjectType.cast(type);
      if (objectType == null) {
        reportError(BAD_IMPLEMENTED_TYPE, fnName);
        return false;
      } else if (objectType.isEmptyType()) {
        reportWarning(RESOLVED_TAG_EMPTY, "@implements", fnName);
        return false;
      } else if (objectType.isUnknownType()) {
        if (hasMoreTagsToResolve(objectType)) {
          return true;
        } else {
          reportWarning(RESOLVED_TAG_EMPTY, "@implements", fnName);
          return false;
        }
      } else {
        return true;
      }
    }
  }

  /**
   * @param fnName The function name.
   * @param compiler The compiler.
   * @param errorRoot The node to associate with any warning generated by
   *     this builder.
   * @param scope The syntactic scope.
   */
  FunctionTypeBuilder(String fnName, AbstractCompiler compiler,
      Node errorRoot, TypedScope scope) {
    checkNotNull(errorRoot);
    this.fnName = nullToEmpty(fnName);
    this.codingConvention = compiler.getCodingConvention();
    this.typeRegistry = compiler.getTypeRegistry();
    this.errorRoot = errorRoot;
    this.compiler = compiler;
    this.enclosingScope = scope;
    this.templateScope = scope;
  }

  /** Format the function name for use in warnings. */
  String formatFnName() {
    return fnName.isEmpty() ? "<anonymous>" : fnName;
  }

  /**
   * Sets the contents of this function.
   */
  FunctionTypeBuilder setContents(@Nullable FunctionContents contents) {
    if (contents != null) {
      this.contents = contents;
    }
    return this;
  }

  /**
   * Sets a declaration scope explicitly. This is important with block scopes because a function
   * declared in an inner scope with 'var' needs to use the inner scope to resolve names, but needs
   * to be declared in the outer scope.
   */
  FunctionTypeBuilder setDeclarationScope(TypedScope declarationScope) {
    this.declarationScope = declarationScope;
    return this;
  }

  /**
   * Infer the parameter and return types of a function from
   * the parameter and return types of the function it is overriding.
   *
   * @param oldType The function being overridden. Does nothing if this is null.
   * @param paramsParent The PARAM_LIST node of the function that we're assigning to.
   *     If null, that just means we're not initializing this to a function
   *     literal.
   */
  FunctionTypeBuilder inferFromOverriddenFunction(
      @Nullable FunctionType oldType, @Nullable Node paramsParent) {
    if (oldType == null) {
      return this;
    }

    // Propagate the template types, if they exist.
    this.templateTypeNames = oldType.getTemplateTypeMap().getTemplateKeys();

    returnType = oldType.getReturnType();
    returnTypeInferred = oldType.isReturnTypeInferred();
    if (paramsParent == null) {
      // Not a function literal.
      parametersNode = oldType.getParametersNode();
      if (parametersNode == null) {
        parametersNode = new FunctionParamBuilder(typeRegistry).build();
      }
    } else {
      // We're overriding with a function literal. Apply type information
      // to each parameter of the literal.
      FunctionParamBuilder paramBuilder =
          new FunctionParamBuilder(typeRegistry);
      Iterator<Node> oldParams = oldType.getParameters().iterator();
      boolean warnedAboutArgList = false;
      boolean oldParamsListHitOptArgs = false;
      for (Node currentParam = paramsParent.getFirstChild();
           currentParam != null; currentParam = currentParam.getNext()) {
        if (oldParams.hasNext()) {
          Node oldParam = oldParams.next();
          Node newParam = paramBuilder.newParameterFromNode(oldParam);

          oldParamsListHitOptArgs =
              oldParamsListHitOptArgs || oldParam.isVarArgs() || oldParam.isOptionalArg();

          // The subclass method might write its var_args as individual arguments.
          if (currentParam.getNext() != null && newParam.isVarArgs()) {
            newParam.setVarArgs(false);
            newParam.setOptionalArg(true);
          }
          // The subclass method might also make a required parameter into an optional parameter
          // with a default value
          if (currentParam.isDefaultValue()) {
            newParam.setOptionalArg(true);
          }
        } else {
          warnedAboutArgList |=
              addParameter(
                  paramBuilder,
                  typeRegistry.getNativeType(UNKNOWN_TYPE),
                  warnedAboutArgList,
                  codingConvention.isOptionalParameter(currentParam)
                      || oldParamsListHitOptArgs
                      || currentParam.isDefaultValue(),
                  codingConvention.isVarArgsParameter(currentParam));
        }
      }

      // Clone any remaining params that aren't in the function literal,
      // but make them optional.
      while (oldParams.hasNext()) {
        paramBuilder.newOptionalParameterFromNode(oldParams.next());
      }

      parametersNode = paramBuilder.build();
    }
    return this;
  }

  /**
   * Infer the return type from JSDocInfo.
   * @param fromInlineDoc Indicates whether return type is inferred from inline
   * doc attached to function name
   */
  FunctionTypeBuilder inferReturnType(
      @Nullable JSDocInfo info, boolean fromInlineDoc) {
    if (info != null) {
      JSTypeExpression returnTypeExpr =
          fromInlineDoc ? info.getType() : info.getReturnType();
      if (returnTypeExpr != null) {
        returnType = returnTypeExpr.evaluate(templateScope, typeRegistry);
        returnTypeInferred = false;
      }
    }

    return this;
  }

  FunctionTypeBuilder usingClassSyntax() {
    this.isClass = true;
    return this;
  }

  /** Infer whether the function is a normal function, a constructor, or an interface. */
  FunctionTypeBuilder inferKind(@Nullable JSDocInfo info) {
    if (info != null) {
      isConstructor = info.isConstructor();
      isInterface = info.isInterface();
      isRecord = info.usesImplicitMatch();
      isAbstract = info.isAbstract();
      makesStructs = info.makesStructs();
      makesDicts = info.makesDicts();
    }
    if (isClass) {
      // If a CLASS literal has not been explicitly declared an interface, it's a constructor.
      // If it's not expicitly @dict or @unrestricted then it's @struct.
      isConstructor = !isInterface;
      makesStructs = info == null || (!makesDicts && !info.makesUnrestricted());
    }

    if (makesStructs && !(isConstructor || isInterface)) {
      reportWarning(CONSTRUCTOR_REQUIRED, "@struct", formatFnName());
    } else if (makesDicts && !isConstructor) {
      reportWarning(CONSTRUCTOR_REQUIRED, "@dict", formatFnName());
    }
    return this;
  }

  /** Clobber the templateTypeNames from the JSDoc with builtin ones for native types. */
  private boolean maybeUseNativeClassTemplateNames(JSDocInfo info) {
    // TODO(b/74253232): maybeGetNativeTypesOfBuiltin should also handle cases where a local type
    // declaration shadows a templatized native type.
    ImmutableList<TemplateType> nativeKeys = typeRegistry.maybeGetTemplateTypesOfBuiltin(fnName);
    // TODO(b/73386087): Make infoTemplateTypeNames.size() == nativeKeys.size() a
    // Preconditions check. It currently fails for "var symbol" in the externs.
    if (nativeKeys != null && info.getTemplateTypeNames().size() == nativeKeys.size()) {
      this.templateTypeNames = nativeKeys;
      return true;
    }
    return false;
  }

  /**
   * Infer any supertypes from the JSDocInfo or the passed-in base type.
   *
   * @param info JSDoc info that is attached to the type declaration, if any
   * @param classExtendsType The type of the extends clause in `class C extends SuperClass {}`, if
   *     present.
   * @return this object
   */
  FunctionTypeBuilder inferInheritance(
      @Nullable JSDocInfo info, @Nullable ObjectType classExtendsType) {

    if (info != null && info.hasBaseType()) {
      if (isConstructor) {
        ObjectType infoBaseType =
            info.getBaseType().evaluate(templateScope, typeRegistry).toMaybeObjectType();
        // TODO(sdh): ensure JSDoc's baseType and AST's baseType are compatible if both are set
        if (infoBaseType.setValidator(new ExtendedTypeValidator())) {
          baseType = infoBaseType;
        }
      } else {
        reportWarning(EXTENDS_WITHOUT_TYPEDEF, formatFnName());
      }
    } else if (classExtendsType != null && isConstructor) {
      // This case is:
      // // no JSDoc here
      // class extends astBaseType {...}
      //
      // It may well be that astBaseType is something dynamically created, like a value passed into
      // a function. A common pattern is:
      //
      // function mixinX(superClass) {
      //   return class extends superClass {
      //     ...
      //   };
      // }
      // The ExtendedTypeValidator() used in the JSDocInfo case above will report errors for these
      // cases, and we don't want that.
      // Since astBaseType is an actual value in code rather than an annotation, we can
      // rely on validation elsewhere to ensure it is actually defined.
      baseType = classExtendsType;
    }

    // Implemented interfaces (for constructors only).
    if (info != null && info.getImplementedInterfaceCount() > 0) {
      if (isConstructor) {
        implementedInterfaces = new ArrayList<>();
        Set<JSType> baseInterfaces = new HashSet<>();
        for (JSTypeExpression t : info.getImplementedInterfaces()) {
          JSType maybeInterType = t.evaluate(templateScope, typeRegistry);

          if (maybeInterType != null &&
              maybeInterType.setValidator(new ImplementedTypeValidator())) {
            // Disallow implementing the same base (not templatized) interface
            // type more than once.
            JSType baseInterface = maybeInterType;
            if (baseInterface.toMaybeTemplatizedType() != null) {
              baseInterface = baseInterface.toMaybeTemplatizedType().getReferencedType();
            }
            if (!baseInterfaces.add(baseInterface)) {
              reportWarning(SAME_INTERFACE_MULTIPLE_IMPLEMENTS, baseInterface.toString());
            }

            implementedInterfaces.add((ObjectType) maybeInterType);
          }
        }
      } else if (isInterface) {
        reportWarning(
            TypeCheck.CONFLICTING_IMPLEMENTED_TYPE, formatFnName());
      } else {
        reportWarning(CONSTRUCTOR_REQUIRED, "@implements", formatFnName());
      }
    }

    // extended interfaces (for interfaces only)
    // We've already emitted a warning if this is not an interface.
    if (isInterface) {
      extendedInterfaces = new ArrayList<>();
      if (info != null) {
        for (JSTypeExpression t : info.getExtendedInterfaces()) {
          JSType maybeInterfaceType = t.evaluate(templateScope, typeRegistry);
          if (maybeInterfaceType != null &&
              maybeInterfaceType.setValidator(new ExtendedTypeValidator())) {
            extendedInterfaces.add((ObjectType) maybeInterfaceType);
          }
          // de-dupe baseType (from extends keyword) if it's also in @extends jsdoc.
          if (classExtendsType != null && maybeInterfaceType.isSubtypeOf(classExtendsType)) {
            classExtendsType = null;
          }
        }
      }
      if (classExtendsType != null && classExtendsType.setValidator(new ExtendedTypeValidator())) {
        // case is:
        // /**
        //  * @interface
        //  * @extends {OtherInterface}
        //  */
        // class SomeInterface extends astBaseType {}
        // Add the explicit extends type to the extended interfaces listed in JSDoc.
        extendedInterfaces.add(classExtendsType);
      }
    }

    return this;
  }

  /**
   * Infers the type of {@code this}.
   * @param type The type of this if the info is missing.
   */
  FunctionTypeBuilder inferThisType(JSDocInfo info, JSType type) {
    // Look at the @this annotation first.
    inferThisType(info);

    if (thisType == null) {
      ObjectType objType = ObjectType.cast(type);
      if (objType != null && (info == null || !info.hasType())) {
        thisType = objType;
      }
    }

    return this;
  }

  /**
   * Infers the type of {@code this}.
   * @param info The JSDocInfo for this function.
   */
  FunctionTypeBuilder inferThisType(JSDocInfo info) {
    if (info != null && info.hasThisType()) {
      // TODO(johnlenz): In ES5 strict mode a function can have a null or
      // undefined "this" value, but all the existing "@this" annotations
      // don't declare restricted types.
      JSType maybeThisType =
          info.getThisType().evaluate(templateScope, typeRegistry).restrictByNotNullOrUndefined();
      if (maybeThisType != null) {
        thisType = maybeThisType;
      }
    }

    return this;
  }

  /**
   * Infer the parameter types from the doc info alone.
   */
  FunctionTypeBuilder inferParameterTypes(JSDocInfo info) {
    // Create a fake args parent.
    Node lp = IR.paramList();
    for (String name : info.getParameterNames()) {
      lp.addChildToBack(IR.name(name));
    }

    return inferParameterTypes(lp, info);
  }

  /** Infer the parameter types from the list of parameter names and the JSDoc info. */
  FunctionTypeBuilder inferParameterTypes(@Nullable Node paramsParent, @Nullable JSDocInfo info) {
    if (paramsParent == null) {
      if (info == null) {
        return this;
      } else {
        return inferParameterTypes(info);
      }
    }

    // arguments
    Node oldParameterType = null;
    if (parametersNode != null) {
      oldParameterType = parametersNode.getFirstChild();
    }

    FunctionParamBuilder builder = new FunctionParamBuilder(typeRegistry);
    boolean warnedAboutArgList = false;
    Set<String> allJsDocParams =
        (info == null) ? new HashSet<>() : new HashSet<>(info.getParameterNames());
    boolean isVarArgs = false;
    int paramIndex = 0;
    for (Node param : paramsParent.children()) {
      boolean isOptionalParam = false;

      if (param.isRest()) {
        isVarArgs = true;
        param = param.getOnlyChild();
      } else if (param.isDefaultValue()) {
        // The first child is the actual positional parameter
        param = checkNotNull(param.getFirstChild(), param);
        isOptionalParam = true;
      } else {
        isVarArgs = isVarArgsParameterByConvention(param);
        isOptionalParam = isOptionalParameterByConvention(param);
      }

      String paramName = null;
      if (param.isName()) {
        paramName = param.getString();
      } else {
        checkState(param.isDestructuringPattern());
        // Right now, the only way to match a JSDoc param to a destructuring parameter is through
        // ordering the JSDoc parameters. So the third formal parameter will correspond to the
        // third JSDoc parameter.
        if (info != null) {
          paramName = info.getParameterNameAt(paramIndex);
        }
      }
      allJsDocParams.remove(paramName);

      // type from JSDocInfo
      JSType parameterType = null;
      if (info != null && info.hasParameterType(paramName)) {
        JSTypeExpression parameterTypeExpression = info.getParameterType(paramName);
        parameterType = parameterTypeExpression.evaluate(templateScope, typeRegistry);
        isOptionalParam = isOptionalParam || parameterTypeExpression.isOptionalArg();
        isVarArgs = isVarArgs || parameterTypeExpression.isVarArgs();
      } else if (param.getJSDocInfo() != null && param.getJSDocInfo().hasType()) {
        JSTypeExpression parameterTypeExpression = param.getJSDocInfo().getType();
        parameterType = parameterTypeExpression.evaluate(templateScope, typeRegistry);
        isOptionalParam = parameterTypeExpression.isOptionalArg();
        isVarArgs = parameterTypeExpression.isVarArgs();
      } else if (oldParameterType != null &&
          oldParameterType.getJSType() != null) {
        parameterType = oldParameterType.getJSType();
        isOptionalParam = oldParameterType.isOptionalArg();
        isVarArgs = oldParameterType.isVarArgs();
      } else {
        parameterType = typeRegistry.getNativeType(UNKNOWN_TYPE);
      }

      warnedAboutArgList |= addParameter(
          builder, parameterType, warnedAboutArgList,
          isOptionalParam,
          isVarArgs);

      if (oldParameterType != null) {
        oldParameterType = oldParameterType.getNext();
      }
      paramIndex++;
    }
    // Copy over any old parameters that aren't in the param list.
    if (!isVarArgs) {
      while (oldParameterType != null && !isVarArgs) {
        builder.newParameterFromNode(oldParameterType);
        oldParameterType = oldParameterType.getNext();
      }
    }

    for (String inexistentName : allJsDocParams) {
      reportWarning(INEXISTENT_PARAM, inexistentName, formatFnName());
    }

    parametersNode = builder.build();
    return this;
  }

  /** Register the template keys in a template scope and on the function node. */
  private void registerTemplates(Iterable<TemplateType> templates, @Nullable Node scopeRoot) {
    if (!Iterables.isEmpty(templates)) {
      // Add any templates from JSDoc into our template scope.
      this.templateScope = typeRegistry.createScopeWithTemplates(templateScope, templates);
      // Register the template types on the scope root node, if there is one.
      if (scopeRoot != null) {
        typeRegistry.registerTemplateTypeNamesInScope(templates, scopeRoot);
      }
    }
  }

  /** Infer parameters from the params list and info. Also maybe add extra templates. */
  FunctionTypeBuilder inferConstructorParameters(Node argsParent, @Nullable JSDocInfo info) {
    // Look for template parameters in 'info': these will be added to anything from the class.
    if (info != null) {
      setConstructorTemplateTypeNames(
          buildTemplateTypesFromJSDocInfo(info, true), argsParent.getParent());
    }

    inferParameterTypes(argsParent, info);

    return this;
  }

  /** Infer constructor parameters from the superclass constructor. */
  FunctionTypeBuilder inferConstructorParameters(FunctionType superCtor) {
    inferImplicitConstructorParameters(superCtor.getParametersNode().cloneTree());

    // Look for template parameters in superCtor that are missing from its instance type.
    setConstructorTemplateTypeNames(superCtor.getConstructorOnlyTemplateParameters(), null);

    return this;
  }

  FunctionTypeBuilder inferImplicitConstructorParameters(Node parametersNode) {
    this.parametersNode = parametersNode;
    return this;
  }

  private void setConstructorTemplateTypeNames(List<TemplateType> templates, @Nullable Node ctor) {
    if (!templates.isEmpty()) {
      this.constructorTemplateTypeNames = ImmutableList.copyOf(templates);
      this.templateTypeNames =
          templateTypeNames.isEmpty()
              ? ImmutableList.copyOf(templates)
              : ImmutableList.<TemplateType>builder()
                  .addAll(templateTypeNames)
                  .addAll(constructorTemplateTypeNames)
                  .build();
      registerTemplates(templates, ctor);
    }
  }

  /** @return Whether the given param is an optional param. */
  private boolean isOptionalParameterByConvention(Node param) {
    if (param.isDestructuringPattern()) {
      return false;
    }
    return codingConvention.isOptionalParameter(param);
  }

  /**
   * Determine whether this is a var args parameter.
   *
   * @return Whether the given param is a var args param.
   */
  private boolean isVarArgsParameterByConvention(Node param) {
    if (param.isDestructuringPattern()) {
      return false;
    }

    return codingConvention.isVarArgsParameter(param);
  }

  private ImmutableList<TemplateType> buildTemplateTypesFromJSDocInfo(
      JSDocInfo info, boolean allowTypeTransformations) {
    ImmutableList<String> infoTypeKeys = info.getTemplateTypeNames();
    ImmutableMap<String, Node> infoTypeTransformations = info.getTypeTransformations();
    if (infoTypeKeys.isEmpty() && infoTypeTransformations.isEmpty()) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<TemplateType> templates = ImmutableList.builder();
    for (String key : infoTypeKeys) {
      templates.add(typeRegistry.createTemplateType(key));
    }
    for (String key : infoTypeTransformations.keySet()) {
      if (allowTypeTransformations) {
        templates.add(
            typeRegistry.createTemplateTypeWithTransformation(
                key, infoTypeTransformations.get(key)));
      } else {
        reportWarning(TEMPLATE_TRANSFORMATION_ON_CLASS, key);
      }
    }
    return templates.build();
  }

  /** Infer the template type from the doc info. */
  FunctionTypeBuilder inferTemplateTypeName(@Nullable JSDocInfo info, @Nullable JSType ownerType) {
    // NOTE: these template type names may override a list
    // of inherited ones from an overridden function.

    if (info != null && !maybeUseNativeClassTemplateNames(info)) {
      ImmutableList<TemplateType> templates =
          buildTemplateTypesFromJSDocInfo(info, !(isConstructor || isInterface));
      if (!templates.isEmpty()) {
        this.templateTypeNames = templates;
      }
    }

    ImmutableList<TemplateType> ownerTypeKeys =
        ownerType != null ? ownerType.getTemplateTypeMap().getTemplateKeys() : ImmutableList.of();

    if (!templateTypeNames.isEmpty() || !ownerTypeKeys.isEmpty()) {
      // TODO(sdh): The order of these should be switched to avoid class templates shadowing
      // method templates, but this currently loosens type checking of arrays more than we'd like.
      // See http://github.com/google/closure-compiler/issues/2973
      registerTemplates(
          Iterables.concat(templateTypeNames, ownerTypeKeys), contents.getSourceNode());
    }

    return this;
  }

  /**
   * Add a parameter to the param list.
   * @param builder A builder.
   * @param paramType The parameter type.
   * @param warnedAboutArgList Whether we've already warned about arg ordering
   *     issues (like if optional args appeared before required ones).
   * @param isOptional Is this an optional parameter?
   * @param isVarArgs Is this a var args parameter?
   * @return Whether a warning was emitted.
   */
  private boolean addParameter(FunctionParamBuilder builder,
      JSType paramType, boolean warnedAboutArgList,
      boolean isOptional, boolean isVarArgs) {
    boolean emittedWarning = false;
    if (isOptional) {
      // Remembering that an optional parameter has been encountered
      // so that if a non optional param is encountered later, an
      // error can be reported.
      if (!builder.addOptionalParams(paramType) && !warnedAboutArgList) {
        reportWarning(VAR_ARGS_MUST_BE_LAST);
        emittedWarning = true;
      }
    } else if (isVarArgs) {
      if (!builder.addVarArgs(paramType) && !warnedAboutArgList) {
        reportWarning(VAR_ARGS_MUST_BE_LAST);
        emittedWarning = true;
      }
    } else {
      if (!builder.addRequiredParams(paramType) && !warnedAboutArgList) {
        // An optional parameter was seen and this argument is not an optional
        // or var arg so it is an error.
        if (builder.hasVarArgs()) {
          reportWarning(VAR_ARGS_MUST_BE_LAST);
        } else {
          reportWarning(OPTIONAL_ARG_AT_END);
        }
        emittedWarning = true;
      }
    }
    return emittedWarning;
  }

  /** Sets the returnType for this function using very basic type inference. */
  private void provideDefaultReturnType() {
    if (contents.getSourceNode() != null && contents.getSourceNode().isGeneratorFunction()) {
      // Set the return type of a generator function to:
      //   @return {!Generator<?>}
      ObjectType generatorType = typeRegistry.getNativeObjectType(GENERATOR_TYPE);
      returnType =
          typeRegistry.createTemplatizedType(
              generatorType, typeRegistry.getNativeType(UNKNOWN_TYPE));
      return;
    }

    JSType inferredReturnType = typeRegistry.getNativeType(UNKNOWN_TYPE);
    if (!contents.mayHaveNonEmptyReturns()
        && !contents.mayHaveSingleThrow()
        && !contents.mayBeFromExterns()) {
      // Infer return types for non-generator functions.
      // We need to be extremely conservative about this, because of two
      // competing needs.
      // 1) If we infer the return type of f too widely, then we won't be able
      //    to assign f to other functions.
      // 2) If we infer the return type of f too narrowly, then we won't be
      //    able to override f in subclasses.
      // So we only infer in cases where the user doesn't expect to write
      // @return annotations--when it's very obvious that the function returns
      // nothing.
      inferredReturnType = typeRegistry.getNativeType(VOID_TYPE);
      returnTypeInferred = true;
    }

    if (contents.getSourceNode() != null && contents.getSourceNode().isAsyncFunction()) {
      // Set the return type of an async function:
      //   @return {!Promise<?>} or @return {!Promise<undefined>}
      ObjectType promiseType = typeRegistry.getNativeObjectType(PROMISE_TYPE);
      returnType = typeRegistry.createTemplatizedType(promiseType, inferredReturnType);
    } else {
      returnType = inferredReturnType;
    }
  }

  /**
   * Builds the function type, and puts it in the registry.
   */
  FunctionType buildAndRegister() {
    if (returnType == null) {
      provideDefaultReturnType();
      checkNotNull(returnType);
    }

    if (parametersNode == null) {
      throw new IllegalStateException(
          "All Function types must have params and a return type");
    }

    FunctionType fnType;
    if (isConstructor) {
      fnType = getOrCreateConstructor();
    } else if (isInterface) {
      fnType = getOrCreateInterface();
    } else {
      fnType =
          new FunctionBuilder(typeRegistry)
              .withName(fnName)
              .withSourceNode(contents.getSourceNode())
              .withParamsNode(parametersNode)
              .withReturnType(returnType, returnTypeInferred)
              .withTypeOfThis(thisType)
              .withTemplateKeys(templateTypeNames)
              .withIsAbstract(isAbstract)
              .build();
      maybeSetBaseType(fnType);
    }

    if (implementedInterfaces != null && fnType.isConstructor()) {
      fnType.setImplementedInterfaces(implementedInterfaces);
    }

    if (extendedInterfaces != null) {
      fnType.setExtendedInterfaces(extendedInterfaces);
    }

    if (isRecord) {
      fnType.setImplicitMatch(true);
    }

    return fnType;
  }

  private void maybeSetBaseType(FunctionType fnType) {
    if (!fnType.isInterface() && baseType != null) {
      fnType.setPrototypeBasedOn(baseType);
      fnType.extendTemplateTypeMapBasedOn(baseType);
    }
  }

  /**
   * Returns a constructor function either by returning it from the
   * registry if it exists or creating and registering a new type. If
   * there is already a type, then warn if the existing type is
   * different than the one we are creating, though still return the
   * existing function if possible.  The primary purpose of this is
   * that registering a constructor will fail for all built-in types
   * that are initialized in {@link JSTypeRegistry}.  We a) want to
   * make sure that the type information specified in the externs file
   * matches what is in the registry and b) annotate the externs with
   * the {@link JSType} from the registry so that there are not two
   * separate JSType objects for one type.
   */
  private FunctionType getOrCreateConstructor() {
    FunctionType fnType =
        new FunctionBuilder(typeRegistry)
            .forConstructor()
            .withName(fnName)
            .withSourceNode(contents.getSourceNode())
            .withParamsNode(parametersNode)
            .withReturnType(returnType)
            .withTemplateKeys(templateTypeNames)
            .withConstructorTemplateKeys(constructorTemplateTypeNames)
            .withIsAbstract(isAbstract)
            .build();

    if (makesStructs) {
      fnType.setStruct();
    } else if (makesDicts) {
      fnType.setDict();
    }

    // There are two cases where this type already exists in the current scope:
    //   1. The type is a built-in that we initalized in JSTypeRegistry and is also defined in
    //  externs.
    //   2. Cases like "class C {} C = class {}"
    // See https://github.com/google/closure-compiler/issues/2928 for some related bugs.
    // We use "getTypeForScope" to specifically check if this was defined for getScopeDeclaredIn()
    // so we don't pick up types that are going to be shadowed.
    JSType existingType = typeRegistry.getTypeForScope(getScopeDeclaredIn(), fnName);
    if (existingType != null) {
      boolean isInstanceObject = existingType.isInstanceType();
      if (isInstanceObject || fnName.equals("Function")) {
        FunctionType existingFn =
            isInstanceObject ?
            existingType.toObjectType().getConstructor() :
            typeRegistry.getNativeFunctionType(FUNCTION_FUNCTION_TYPE);

        if (existingFn.getSource() == null) {
          existingFn.setSource(contents.getSourceNode());
        }

        if (!existingFn.hasEqualCallType(fnType)) {
          reportWarning(TYPE_REDEFINITION, formatFnName(),
              fnType.toString(), existingFn.toString());
        }

        // If the existing function is a built-in type, set its base type in case it @extends
        // another function (since we don't set its prototype in JSTypeRegistry)
        if (existingFn.isNativeObjectType()) {
          maybeSetBaseType(existingFn);
        }

        return existingFn;
      } else {
        // We fall through and return the created type, even though it will fail
        // to register. We have no choice as we have to return a function. We
        // issue an error elsewhere though, so the user should fix it.
      }
    }

    maybeSetBaseType(fnType);

    // TODO(johnlenz): determine what we are supposed to do for:
    //   @constructor
    //   this.Foo = ...
    //
    if (!fnName.isEmpty() && !fnName.startsWith("this.")) {
      typeRegistry.declareTypeForExactScope(getScopeDeclaredIn(), fnName, fnType.getInstanceType());
    }
    return fnType;
  }

  private FunctionType getOrCreateInterface() {
    FunctionType fnType = null;

    JSType type = typeRegistry.getType(getScopeDeclaredIn(), fnName);
    if (type != null && type.isInstanceType()) {
      FunctionType ctor = type.toMaybeObjectType().getConstructor();
      if (ctor.isInterface()) {
        fnType = ctor;
        fnType.setSource(contents.getSourceNode());
      }
    }

    if (fnType == null) {
      fnType =
          typeRegistry.createInterfaceType(
              fnName, contents.getSourceNode(), templateTypeNames, makesStructs);
      if (!fnName.isEmpty()) {
        typeRegistry.declareTypeForExactScope(
            getScopeDeclaredIn(), fnName, fnType.getInstanceType());
      }
      maybeSetBaseType(fnType);
    }
    return fnType;
  }
  private void reportWarning(DiagnosticType warning, String ... args) {
    compiler.report(JSError.make(errorRoot, warning, args));
  }

  private void reportError(DiagnosticType error, String ... args) {
    compiler.report(JSError.make(errorRoot, error, args));
  }

  /**
   * Determines whether the given JsDoc info declares a function type.
   */
  static boolean isFunctionTypeDeclaration(JSDocInfo info) {
    return info.getParameterCount() > 0
        || info.hasReturnType()
        || info.hasThisType()
        || info.isConstructor()
        || info.isInterface()
        || info.isAbstract();
  }

  /**
   * The scope that we should declare this function in, if it needs
   * to be declared in a scope. Notice that TypedScopeCreator takes
   * care of most scope-declaring.
   */
  private TypedScope getScopeDeclaredIn() {
    if (declarationScope != null) {
      return declarationScope;
    }

    int dotIndex = fnName.indexOf('.');
    if (dotIndex != -1) {
      String rootVarName = fnName.substring(0, dotIndex);
      TypedVar rootVar = enclosingScope.getVar(rootVarName);
      if (rootVar != null) {
        return rootVar.getScope();
      }
    }
    return enclosingScope;
  }

  /**
   * Check whether a type is resolvable in the future
   * If this has a supertype that hasn't been resolved yet, then we can assume
   * this type will be OK once the super type resolves.
   * @param objectType
   * @return true if objectType is resolvable in the future
   */
  private static boolean hasMoreTagsToResolve(ObjectType objectType) {
    checkArgument(objectType.isUnknownType());
    FunctionType ctor = objectType.getConstructor();
    if (ctor != null) {
      // interface extends interfaces
      for (ObjectType interfaceType : ctor.getExtendedInterfaces()) {
        if (!interfaceType.isResolved()) {
          return true;
        }
      }
    }
    if (objectType.getImplicitPrototype() != null) {
      // constructor extends class
      return !objectType.getImplicitPrototype().isResolved();
    }
    return false;
  }

  /** Holds data dynamically inferred about functions. */
  static interface FunctionContents {
    /** Returns the source node of this function. May be null. */
    Node getSourceNode();

    /** Returns if the function may be in externs. */
    boolean mayBeFromExterns();

    /** Returns if a return of a real value (not undefined) appears. */
    boolean mayHaveNonEmptyReturns();

    /** Returns if this consists of a single throw. */
    boolean mayHaveSingleThrow();
  }

  static class UnknownFunctionContents implements FunctionContents {
    private static final UnknownFunctionContents singleton = new UnknownFunctionContents();

    static FunctionContents get() {
      return singleton;
    }

    @Override
    public Node getSourceNode() {
      return null;
    }

    @Override
    public boolean mayBeFromExterns() {
      return true;
    }

    @Override
    public boolean mayHaveNonEmptyReturns() {
      return true;
    }

    @Override
    public boolean mayHaveSingleThrow() {
      return true;
    }
  }

  static class AstFunctionContents implements FunctionContents {
    private final Node n;
    private boolean hasNonEmptyReturns = false;

    AstFunctionContents(Node n) {
      this.n = n;
    }

    @Override
    public Node getSourceNode() {
      return n;
    }

    @Override
    public boolean mayBeFromExterns() {
      return n.isFromExterns();
    }

    @Override
    public boolean mayHaveNonEmptyReturns() {
      return hasNonEmptyReturns;
    }

    void recordNonEmptyReturn() {
      hasNonEmptyReturns = true;
    }

    @Override
    public boolean mayHaveSingleThrow() {
      Node block = n.getLastChild();
      return block.hasOneChild() && block.getFirstChild().isThrow();
    }
  }
}
