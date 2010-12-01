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
import static com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.VOID_TYPE;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionBuilder;
import com.google.javascript.rhino.jstype.FunctionParamBuilder;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.InstanceObjectType;
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

  private JSType returnType = null;
  private boolean returnTypeInferred = false;
  private List<ObjectType> implementedInterfaces = null;
  private ObjectType baseType = null;
  private ObjectType thisType = null;
  private boolean isConstructor = false;
  private boolean isInterface = false;
  private Node parametersNode = null;
  private Node sourceNode = null;
  private String templateTypeName = null;

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

  static final DiagnosticType TEMPLATE_TYPE_DUPLICATED = DiagnosticType.error(
      "JSC_TEMPLATE_TYPE_DUPLICATED",
      "Only one parameter type must be the template type");

  static final DiagnosticType TEMPLATE_TYPE_EXPECTED = DiagnosticType.error(
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
      } else if (objectType.isUnknownType() &&
          // If this has a supertype that hasn't been resolved yet,
          // then we can assume this type will be ok once the super
          // type resolves.
          (objectType.getImplicitPrototype() == null ||
           objectType.getImplicitPrototype().isResolved())) {
        reportWarning(RESOLVED_TAG_EMPTY, "@extends", fnName);
      } else {
        return true;
      }
      return false;
    }
  }

  private class ImplementedTypeValidator implements Predicate<JSType> {
    @Override
    public boolean apply(JSType type) {
      ObjectType objectType = ObjectType.cast(type);
      if (objectType == null) {
        reportError(BAD_IMPLEMENTED_TYPE, fnName);
      } else if (objectType.isUnknownType() &&
          // If this has a supertype that hasn't been resolved yet,
          // then we can assume this type will be ok once the super
          // type resolves.
          (objectType.getImplicitPrototype() == null ||
           objectType.getImplicitPrototype().isResolved())) {
        reportWarning(RESOLVED_TAG_EMPTY, "@implements", fnName);
      } else {
        return true;
      }
      return false;
    }
  }

  private class ThisTypeValidator implements Predicate<JSType> {
    @Override
    public boolean apply(JSType type) {
      // TODO(user): Doing an instanceof check here is too
      // restrictive as (Date,Error) is, for instance, an object type
      // even though its implementation is a UnionType. Would need to
      // create interfaces JSType, ObjectType, FunctionType etc and have
      // separate implementation instead of the class hierarchy, so that
      // union types can also be object types, etc.
      if (!type.restrictByNotNullOrUndefined().isSubtype(
              typeRegistry.getNativeType(OBJECT_TYPE))) {
        reportWarning(THIS_TYPE_NON_OBJECT, type.toString());
        return false;
      }
      return true;
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
   * Sets the FUNCTION node of this function.
   */
  FunctionTypeBuilder setSourceNode(@Nullable Node sourceNode) {
    this.sourceNode = sourceNode;
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

          // The subclass method might right its var_args as individual
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

    if (templateTypeName != null &&
        returnType != null &&
        returnType.restrictByNotNullOrUndefined().isTemplateType()) {
      reportError(TEMPLATE_TYPE_EXPECTED, fnName);
    }
    return this;
  }

  /**
   * If we haven't found a return value yet, try to look at the "return"
   * statements in the function.
   */
  FunctionTypeBuilder inferReturnStatementsAsLastResort(
      @Nullable Node functionBlock) {
    if (functionBlock == null || compiler.getInput(sourceName).isExtern()) {
      return this;
    }
    Preconditions.checkArgument(functionBlock.getType() == Token.BLOCK);
    if (returnType == null) {
      boolean hasNonEmptyReturns = false;
      List<Node> worklist = Lists.newArrayList(functionBlock);
      while (!worklist.isEmpty()) {
        Node current = worklist.remove(worklist.size() - 1);
        int cType = current.getType();
        if (cType == Token.RETURN && current.getFirstChild() != null ||
            cType == Token.THROW) {
          hasNonEmptyReturns = true;
          break;
        } else if (NodeUtil.isStatementBlock(current) ||
            NodeUtil.isControlStructure(current)) {
          for (Node child = current.getFirstChild();
               child != null; child = child.getNext()) {
            worklist.add(child);
          }
        }
      }

      if (!hasNonEmptyReturns) {
        returnType = typeRegistry.getNativeType(VOID_TYPE);
        returnTypeInferred = true;
      }
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
      isInterface = info.isInterface();

      // base type
      if (info.hasBaseType()) {
        if (isConstructor || isInterface) {
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

      // implemented interfaces
      if (isConstructor || isInterface) {
        implementedInterfaces = Lists.newArrayList();
        for (JSTypeExpression t : info.getImplementedInterfaces()) {
          JSType maybeInterType = t.evaluate(scope, typeRegistry);
          if (maybeInterType != null &&
              maybeInterType.setValidator(new ImplementedTypeValidator())) {
            implementedInterfaces.add((ObjectType) maybeInterType);
          }
        }
        if (baseType != null) {
          JSType maybeFunctionType = baseType.getConstructor();
          if (maybeFunctionType instanceof FunctionType) {
            FunctionType functionType = baseType.getConstructor();
            Iterables.addAll(
                implementedInterfaces,
                functionType.getImplementedInterfaces());
          }
        }
      } else if (info.getImplementedInterfaceCount() > 0) {
        reportWarning(IMPLEMENTS_WITHOUT_CONSTRUCTOR, fnName);
      }
    }

    return this;
  }

  /**
   * Infers the type of {@code this}.
   * @param type The type of this.
   */
  FunctionTypeBuilder inferThisType(JSDocInfo info, JSType type) {
    ObjectType objType = ObjectType.cast(type);
    if (objType != null && (info == null || !info.hasType())) {
      thisType = objType;
    }
    return this;
  }

  /**
   * Infers the type of {@code this}.
   * @param info The JSDocInfo for this function.
   * @param owner The node for the object whose prototype "owns" this function.
   *     For example, {@code A} in the expression {@code A.prototype.foo}. May
   *     be null to indicate that this is not a prototype property.
   */
  FunctionTypeBuilder inferThisType(JSDocInfo info,
      @Nullable Node owner) {
    ObjectType maybeThisType = null;
    if (info != null && info.hasThisType()) {
      maybeThisType = ObjectType.cast(
          info.getThisType().evaluate(scope, typeRegistry));
    }
    if (maybeThisType != null) {
      thisType = maybeThisType;
      thisType.setValidator(new ThisTypeValidator());
    } else if (owner != null &&
               (info == null || !info.hasType())) {
      // If the function is of the form:
      // x.prototype.y = function() {}
      // then we can assume "x" is the @this type. On the other hand,
      // if it's of the form:
      // /** @type {Function} */ x.prototype.y;
      // then we should not give it a @this type.
      String ownerTypeName = owner.getQualifiedName();
      ObjectType ownerType = ObjectType.cast(
          typeRegistry.getForgivingType(
              scope, ownerTypeName, sourceName,
              owner.getLineno(), owner.getCharno()));
      if (ownerType != null) {
        thisType = ownerType;
      }
    }

    return this;
  }

  /**
   * Infer the parameter types from the doc info alone.
   */
  FunctionTypeBuilder inferParameterTypes(JSDocInfo info) {
    // Create a fake args parent.
    Node lp = new Node(Token.LP);
    for (String name : info.getParameterNames()) {
      lp.addChildToBack(Node.newString(Token.NAME, name));
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
    for (Node arg : argsParent.children()) {
      String argumentName = arg.getString();
      allJsDocParams.remove(argumentName);

      // type from JSDocInfo
      JSType parameterType = null;
      boolean isOptionalParam = isOptionalParameter(arg, info);
      boolean isVarArgs = isVarArgsParameter(arg, info);
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

      if (templateTypeName != null &&
          parameterType.restrictByNotNullOrUndefined().isTemplateType()) {
        if (foundTemplateType) {
          reportError(TEMPLATE_TYPE_DUPLICATED, fnName);
        }
        foundTemplateType = true;
      }
      warnedAboutArgList |= addParameter(
          builder, parameterType, warnedAboutArgList,
          isOptionalParam,
          isVarArgs);

      if (oldParameterType != null) {
        oldParameterType = oldParameterType.getNext();
      }
    }

    if (templateTypeName != null && !foundTemplateType) {
      reportError(TEMPLATE_TYPE_EXPECTED, fnName);
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
      templateTypeName = info.getTemplateTypeName();
      typeRegistry.setTemplateTypeName(templateTypeName);
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
      fnType = typeRegistry.createInterfaceType(fnName, sourceNode);
      if (getScopeDeclaredIn().isGlobal() && !fnName.isEmpty()) {
        typeRegistry.declareType(fnName, fnType.getInstanceType());
      }
      maybeSetBaseType(fnType);
    } else {
      fnType = new FunctionBuilder(typeRegistry)
          .withName(fnName)
          .withSourceNode(sourceNode)
          .withParamsNode(parametersNode)
          .withReturnType(returnType, returnTypeInferred)
          .withTypeOfThis(thisType)
          .withTemplateName(templateTypeName)
          .build();
      maybeSetBaseType(fnType);
    }

    if (implementedInterfaces != null) {
      fnType.setImplementedInterfaces(implementedInterfaces);
    }

    typeRegistry.clearTemplateTypeName();

    return fnType;
  }

  private void maybeSetBaseType(FunctionType fnType) {
    if (baseType != null) {
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
        fnName, sourceNode, parametersNode, returnType);
    JSType existingType = typeRegistry.getType(fnName);

    if (existingType != null) {
      boolean isInstanceObject = existingType instanceof InstanceObjectType;
      if (isInstanceObject || fnName.equals("Function")) {
        FunctionType existingFn =
            isInstanceObject ?
            ((InstanceObjectType) existingType).getConstructor() :
            typeRegistry.getNativeFunctionType(FUNCTION_FUNCTION_TYPE);

        if (existingFn.getSource() == null) {
          existingFn.setSource(sourceNode);
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
   * Determines whether the given jsdoc info declares a function type.
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
}
