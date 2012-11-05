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

import static com.google.javascript.jscomp.TypeCheck.BAD_IMPLEMENTED_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.FUNCTION_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.VOID_TYPE;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.Scope.Var;
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
 * @author pascallouis@google.com (Pascal-Louis Perez)
 */
final class FunctionTypeBuilder {

  private final String fnName;
  private final AbstractCompiler compiler;
  private final CodingConvention codingConvention;
  private final JSTypeRegistry typeRegistry;
  private final Node errorRoot;
  private final String sourceName;
  private final Scope scope;

  private FunctionContents contents = UnknownFunctionContents.get();

  private JSType returnType = null;
  private boolean returnTypeInferred = false;
  private List<ObjectType> implementedInterfaces = null;
  private List<ObjectType> extendedInterfaces = null;
  private ObjectType baseType = null;
  private JSType thisType = null;
  private boolean isConstructor = false;
  private boolean makesStructs = false;
  private boolean makesDicts = false;
  private boolean isInterface = false;
  private Node parametersNode = null;
  private ImmutableList<String> templateTypeNames = ImmutableList.of();

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

  static final DiagnosticType INEXISTANT_PARAM = DiagnosticType.warning(
      "JSC_INEXISTANT_PARAM",
      "parameter {0} does not appear in {1}''s parameter list");

  static final DiagnosticType TYPE_REDEFINITION = DiagnosticType.warning(
      "JSC_TYPE_REDEFINITION",
      "attempted re-definition of type {0}\n"
      + "found   : {1}\n"
      + "expected: {2}");

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

  private class ExtendedTypeValidator implements Predicate<JSType> {
    @Override
    public boolean apply(JSType type) {
      ObjectType objectType = ObjectType.cast(type);
      if (objectType == null) {
        reportWarning(EXTENDS_NON_OBJECT, fnName, type.toString());
        return false;
      } else if (objectType.isEmptyType()) {
        reportWarning(RESOLVED_TAG_EMPTY, "@extends", fnName);
        return false;
      } else if (objectType.isUnknownType()) {
        if (hasMoreTagsToResolve(objectType)) {
          return true;
        } else {
          reportWarning(RESOLVED_TAG_EMPTY, "@extends", fnName);
          return false;
        }
      } else {
        return true;
      }
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
   * @param sourceName A source name for associating any warnings that
   *     we have to emit.
   * @param scope The syntactic scope.
   */
  FunctionTypeBuilder(String fnName, AbstractCompiler compiler,
      Node errorRoot, String sourceName, Scope scope) {
    Preconditions.checkNotNull(errorRoot);

    this.fnName = fnName == null ? "" : fnName;
    this.codingConvention = compiler.getCodingConvention();
    this.typeRegistry = compiler.getTypeRegistry();
    this.errorRoot = errorRoot;
    this.sourceName = sourceName;
    this.compiler = compiler;
    this.scope = scope;
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
   * Infer the parameter and return types of a function from
   * the parameter and return types of the function it is overriding.
   *
   * @param oldType The function being overridden. Does nothing if this is null.
   * @param paramsParent The LP node of the function that we're assigning to.
   *     If null, that just means we're not initializing this to a function
   *     literal.
   */
  FunctionTypeBuilder inferFromOverriddenFunction(
      @Nullable FunctionType oldType, @Nullable Node paramsParent) {
    if (oldType == null) {
      return this;
    }

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

          oldParamsListHitOptArgs = oldParamsListHitOptArgs ||
              oldParam.isVarArgs() ||
              oldParam.isOptionalArg();

          // The subclass method might write its var_args as individual
          // arguments.
          if (currentParam.getNext() != null && newParam.isVarArgs()) {
            newParam.setVarArgs(false);
            newParam.setOptionalArg(true);
          }
        } else {
          warnedAboutArgList |= addParameter(
              paramBuilder,
              typeRegistry.getNativeType(UNKNOWN_TYPE),
              warnedAboutArgList,
              codingConvention.isOptionalParameter(currentParam) ||
                  oldParamsListHitOptArgs,
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
   */
  FunctionTypeBuilder inferReturnType(@Nullable JSDocInfo info) {
    if (info != null && info.hasReturnType()) {
      returnType = info.getReturnType().evaluate(scope, typeRegistry);
      returnTypeInferred = false;
    }

    return this;
  }

  /**
   * Infer the role of the function (whether it's a constructor or interface)
   * and what it inherits from in JSDocInfo.
   */
  FunctionTypeBuilder inferInheritance(@Nullable JSDocInfo info) {
    if (info != null) {
      isConstructor = info.isConstructor();
      makesStructs = info.makesStructs();
      makesDicts = info.makesDicts();
      isInterface = info.isInterface();

      if (makesStructs && !isConstructor) {
        reportWarning(CONSTRUCTOR_REQUIRED, "@struct", fnName);
      } else if (makesDicts && !isConstructor) {
        reportWarning(CONSTRUCTOR_REQUIRED, "@dict", fnName);
      }

      // base type
      if (info.hasBaseType()) {
        if (isConstructor) {
          JSType maybeBaseType =
              info.getBaseType().evaluate(scope, typeRegistry);
          if (maybeBaseType != null &&
              maybeBaseType.setValidator(new ExtendedTypeValidator())) {
            baseType = (ObjectType) maybeBaseType;
          }
        } else {
          reportWarning(EXTENDS_WITHOUT_TYPEDEF, fnName);
        }
      }

      // Implemented interfaces (for constructors only).
      if (info.getImplementedInterfaceCount() > 0) {
        if (isConstructor) {
          implementedInterfaces = Lists.newArrayList();
          for (JSTypeExpression t : info.getImplementedInterfaces()) {
            JSType maybeInterType = t.evaluate(scope, typeRegistry);
            if (maybeInterType != null &&
                maybeInterType.setValidator(new ImplementedTypeValidator())) {
              implementedInterfaces.add((ObjectType) maybeInterType);
            }
          }
        } else if (isInterface) {
          reportWarning(
              TypeCheck.CONFLICTING_IMPLEMENTED_TYPE, fnName);
        } else {
          reportWarning(CONSTRUCTOR_REQUIRED, "@implements", fnName);
        }
      }

      // extended interfaces (for interfaces only)
      // We've already emitted a warning if this is not an interface.
      if (isInterface) {
        extendedInterfaces = Lists.newArrayList();
        for (JSTypeExpression t : info.getExtendedInterfaces()) {
          JSType maybeInterfaceType = t.evaluate(scope, typeRegistry);
          if (maybeInterfaceType != null &&
              maybeInterfaceType.setValidator(new ExtendedTypeValidator())) {
            extendedInterfaces.add((ObjectType) maybeInterfaceType);
          }
        }
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
    JSType maybeThisType = null;
    if (info != null && info.hasThisType()) {
      // TODO(johnlenz): In ES5 strict mode a function can have a null or
      // undefined "this" value, but all the existing "@this" annotations
      // don't declare restricted types.
      maybeThisType = info.getThisType().evaluate(scope, typeRegistry)
          .restrictByNotNullOrUndefined();
    }
    if (maybeThisType != null) {
      thisType = maybeThisType;
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

  /**
   * Infer the parameter types from the list of argument names and
   * the doc info.
   */
  FunctionTypeBuilder inferParameterTypes(@Nullable Node argsParent,
      @Nullable JSDocInfo info) {
    if (argsParent == null) {
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
    Set<String> allJsDocParams = (info == null) ?
        Sets.<String>newHashSet() :
        Sets.newHashSet(info.getParameterNames());
    boolean foundTemplateType = false;
    boolean isVarArgs = false;
    for (Node arg : argsParent.children()) {
      String argumentName = arg.getString();
      allJsDocParams.remove(argumentName);

      // type from JSDocInfo
      JSType parameterType = null;
      boolean isOptionalParam = isOptionalParameter(arg, info);
      isVarArgs = isVarArgsParameter(arg, info);

      if (info != null && info.hasParameterType(argumentName)) {
        parameterType =
            info.getParameterType(argumentName).evaluate(scope, typeRegistry);
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
    }

    // Copy over any old parameters that aren't in the param list.
    if (!isVarArgs) {
      while (oldParameterType != null && !isVarArgs) {
        builder.newParameterFromNode(oldParameterType);
        oldParameterType = oldParameterType.getNext();
      }
    }

    for (String inexistentName : allJsDocParams) {
      reportWarning(INEXISTANT_PARAM, inexistentName, fnName);
    }

    parametersNode = builder.build();
    return this;
  }

  /**
   * @return Whether the given param is an optional param.
   */
  private boolean isOptionalParameter(
      Node param, @Nullable JSDocInfo info) {
    if (codingConvention.isOptionalParameter(param)) {
      return true;
    }

    String paramName = param.getString();
    return info != null && info.hasParameterType(paramName) &&
        info.getParameterType(paramName).isOptionalArg();
  }

  /**
   * Determine whether this is a var args parameter.
   * @return Whether the given param is a var args param.
   */
  private boolean isVarArgsParameter(
      Node param, @Nullable JSDocInfo info) {
    if (codingConvention.isVarArgsParameter(param)) {
      return true;
    }

    String paramName = param.getString();
    return info != null && info.hasParameterType(paramName) &&
        info.getParameterType(paramName).isVarArgs();
  }

  /**
   * Infer the template type from the doc info.
   */
  FunctionTypeBuilder inferTemplateTypeName(@Nullable JSDocInfo info) {
    if (info != null) {
      templateTypeNames = info.getTemplateTypeNames();
      typeRegistry.setTemplateTypeNames(templateTypeNames);
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

  /**
   * Builds the function type, and puts it in the registry.
   */
  FunctionType buildAndRegister() {
    if (returnType == null) {
      // Infer return types.
      // We need to be extremely conservative about this, because of two
      // competing needs.
      // 1) If we infer the return type of f too widely, then we won't be able
      //    to assign f to other functions.
      // 2) If we infer the return type of f too narrowly, then we won't be
      //    able to override f in subclasses.
      // So we only infer in cases where the user doesn't expect to write
      // @return annotations--when it's very obvious that the function returns
      // nothing.
      if (!contents.mayHaveNonEmptyReturns() &&
          !contents.mayHaveSingleThrow() &&
          !contents.mayBeFromExterns()) {
        returnType = typeRegistry.getNativeType(VOID_TYPE);
        returnTypeInferred = true;
      }
    }

    if (returnType == null) {
      returnType = typeRegistry.getNativeType(UNKNOWN_TYPE);
    }

    if (parametersNode == null) {
      throw new IllegalStateException(
          "All Function types must have params and a return type");
    }

    FunctionType fnType;
    if (isConstructor) {
      fnType = getOrCreateConstructor();
    } else if (isInterface) {
      fnType = typeRegistry.createInterfaceType(
          fnName, contents.getSourceNode());
      if (getScopeDeclaredIn().isGlobal() && !fnName.isEmpty()) {
        typeRegistry.declareType(fnName, fnType.getInstanceType());
      }
      maybeSetBaseType(fnType);
    } else {
      fnType = new FunctionBuilder(typeRegistry)
          .withName(fnName)
          .withSourceNode(contents.getSourceNode())
          .withParamsNode(parametersNode)
          .withReturnType(returnType, returnTypeInferred)
          .withTypeOfThis(thisType)
          .withTemplateKeys(templateTypeNames)
          .build();
      maybeSetBaseType(fnType);
    }

    if (implementedInterfaces != null) {
      fnType.setImplementedInterfaces(implementedInterfaces);
    }

    if (extendedInterfaces != null) {
      fnType.setExtendedInterfaces(extendedInterfaces);
    }

    typeRegistry.clearTemplateTypeNames();

    return fnType;
  }

  private void maybeSetBaseType(FunctionType fnType) {
    if (!fnType.isInterface() && baseType != null) {
      fnType.setPrototypeBasedOn(baseType);
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
    FunctionType fnType = typeRegistry.createConstructorType(
        fnName, contents.getSourceNode(), parametersNode, returnType, null);
    JSType existingType = typeRegistry.getType(fnName);

    if (makesStructs) {
      fnType.setStruct();
    } else if (makesDicts) {
      fnType.setDict();
    }
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
          reportWarning(TYPE_REDEFINITION, fnName,
              fnType.toString(), existingFn.toString());
        }

        return existingFn;
      } else {
        // We fall through and return the created type, even though it will fail
        // to register. We have no choice as we have to return a function. We
        // issue an error elsewhere though, so the user should fix it.
      }
    }

    maybeSetBaseType(fnType);

    if (getScopeDeclaredIn().isGlobal() && !fnName.isEmpty()) {
      typeRegistry.declareType(fnName, fnType.getInstanceType());
    }
    return fnType;
  }

  private void reportWarning(DiagnosticType warning, String ... args) {
    compiler.report(JSError.make(sourceName, errorRoot, warning, args));
  }

  private void reportError(DiagnosticType error, String ... args) {
    compiler.report(JSError.make(sourceName, errorRoot, error, args));
  }

  /**
   * Determines whether the given JsDoc info declares a function type.
   */
  static boolean isFunctionTypeDeclaration(JSDocInfo info) {
    return info.getParameterCount() > 0 ||
        info.hasReturnType() ||
        info.hasThisType() ||
        info.isConstructor() ||
        info.isInterface();
  }

  /**
   * The scope that we should declare this function in, if it needs
   * to be declared in a scope. Notice that TypedScopeCreator takes
   * care of most scope-declaring.
   */
  private Scope getScopeDeclaredIn() {
    int dotIndex = fnName.indexOf(".");
    if (dotIndex != -1) {
      String rootVarName = fnName.substring(0, dotIndex);
      Var rootVar = scope.getVar(rootVarName);
      if (rootVar != null) {
        return rootVar.getScope();
      }
    }
    return scope;
  }

  /**
   * Check whether a type is resolvable in the future
   * If this has a supertype that hasn't been resolved yet, then we can assume
   * this type will be OK once the super type resolves.
   * @param objectType
   * @return true if objectType is resolvable in the future
   */
  private static boolean hasMoreTagsToResolve(ObjectType objectType) {
    Preconditions.checkArgument(objectType.isUnknownType());
    if (objectType.getImplicitPrototype() != null) {
      // constructor extends class
      if (objectType.getImplicitPrototype().isResolved()) {
        return false;
      } else {
        return true;
      }
    } else {
      // interface extends interfaces
      FunctionType ctor = objectType.getConstructor();
      if (ctor != null) {
        for (ObjectType interfaceType : ctor.getExtendedInterfaces()) {
          if (!interfaceType.isResolved()) {
            return true;
          }
        }
      }
      return false;
    }
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

    /** Gets a list of variables in this scope that are escaped. */
    Iterable<String> getEscapedVarNames();

    /** Gets a list of variables whose properties are escaped. */
    Set<String> getEscapedQualifiedNames();

    /** Gets the number of times each variable has been assigned. */
    Multiset<String> getAssignedNameCounts();
  }

  static class UnknownFunctionContents implements FunctionContents {
    private static UnknownFunctionContents singleton =
        new UnknownFunctionContents();

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

    @Override
    public Iterable<String> getEscapedVarNames() {
      return ImmutableList.of();
    }

    @Override
    public Set<String> getEscapedQualifiedNames() {
      return ImmutableSet.of();
    }

    @Override
    public Multiset<String> getAssignedNameCounts() {
      return ImmutableMultiset.of();
    }
  }

  static class AstFunctionContents implements FunctionContents {
    private final Node n;
    private boolean hasNonEmptyReturns = false;
    private Set<String> escapedVarNames;
    private Set<String> escapedQualifiedNames;
    private final Multiset<String> assignedVarNames = HashMultiset.create();

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

    @Override
    public Iterable<String> getEscapedVarNames() {
      return escapedVarNames == null
          ? ImmutableList.<String>of() : escapedVarNames;
    }

    void recordEscapedVarName(String name) {
      if (escapedVarNames == null) {
        escapedVarNames = Sets.newHashSet();
      }
      escapedVarNames.add(name);
    }

    @Override
    public Set<String> getEscapedQualifiedNames() {
      return escapedQualifiedNames == null
          ? ImmutableSet.<String>of() : escapedQualifiedNames;
    }

    void recordEscapedQualifiedName(String name) {
      if (escapedQualifiedNames == null) {
        escapedQualifiedNames = Sets.newHashSet();
      }
      escapedQualifiedNames.add(name);
    }

    @Override
    public Multiset<String> getAssignedNameCounts() {
      return assignedVarNames;
    }

    void recordAssignedName(String name) {
      assignedVarNames.add(name);
    }
  }
}
