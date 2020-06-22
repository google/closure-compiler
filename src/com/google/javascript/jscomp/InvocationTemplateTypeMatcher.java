/*
 * Copyright 2007 The Closure Compiler Authors.
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

import static com.google.javascript.rhino.jstype.JSTypeNative.I_TEMPLATE_ARRAY_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.FunctionType.Parameter;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.TemplateType;
import com.google.javascript.rhino.jstype.TemplateTypeMap;
import com.google.javascript.rhino.jstype.TemplatizedType;
import com.google.javascript.rhino.jstype.UnionType;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Determines the types that fill any template parameters at a function invocation.
 *
 * <p>Given an invocation of some function with type `F` templated on `T`, this class traverses the
 * arguments of the invocation to determine what type `T` should be. Argument types and `F`'s
 * paramater types are traversed/recursed in parallel to match template types at any depth inside
 * `F`'s signature.
 *
 * <p>Instances of this class are single use. They provide a "scope" for the matching but accumulate
 * state as the matching progresses.
 */
final class InvocationTemplateTypeMatcher {

  private final Map<TemplateType, JSType> matchedTypes = Maps.newIdentityHashMap();
  private final Set<JSType> seenTypes = Sets.newIdentityHashSet();

  private final JSTypeRegistry registry;
  private final FunctionType calleeType;
  private final JSType localThisType;
  private final Node invocation;

  private final JSType unknownType;

  InvocationTemplateTypeMatcher(
      JSTypeRegistry registry, FunctionType calleeType, JSType localThisType, Node invocation) {
    this.registry = registry;
    this.calleeType = calleeType;
    this.localThisType = localThisType;
    this.invocation = invocation;

    this.unknownType = registry.getNativeType(UNKNOWN_TYPE);
  }

  ImmutableMap<TemplateType, JSType> match() {
    if (this.calleeType.getTemplateTypeMap().isEmpty()) {
      return ImmutableMap.of();
    }

    Node target = this.invocation.getFirstChild();
    if (NodeUtil.isNormalGet(target)) {
      Node obj = target.getFirstChild();
      JSType typeOfThisRequiredByTheFunction = this.calleeType.getTypeOfThis();
      // The type placed on a SUPER node is the superclass type, which allows us to infer the right
      // property types for the GETPROP or GETELEM nodes built on it.
      // However, the type actually passed as `this` when making calls this way is the `this`
      // of the scope where the `super` appears.
      JSType typeOfThisProvidedByTheCall =
          obj.isSuper() ? this.localThisType : this.getTypeOrUnknown(obj);
      // We're looking at a call made as `obj['method']()` or `obj.method()` (see enclosing if),
      // so if the call is successfully made, then the object through which it is made isn't null
      // or undefined.
      typeOfThisProvidedByTheCall = typeOfThisProvidedByTheCall.restrictByNotNullOrUndefined();
      this.matchTemplateTypesRecursive(
          typeOfThisRequiredByTheFunction, typeOfThisProvidedByTheCall);
    }

    if (this.invocation.isTaggedTemplateLit()) {
      Iterator<Parameter> calleeParameters = this.calleeType.getParameters().iterator();
      if (!calleeParameters.hasNext()) {
        // TypeCheck will warn if there are too few function parameters
        return ImmutableMap.copyOf(this.matchedTypes);
      }

      // The first argument to the tag function is an array of strings (typed as ITemplateArray)
      // but not an actual AST node
      this.matchTemplateTypesRecursive(
          calleeParameters.next().getJSType(), this.registry.getNativeType(I_TEMPLATE_ARRAY_TYPE));

      // Resolve the remaining template types from the template literal substitutions.
      this.matchTemplateTypesFromNodes(
          Iterables.skip(this.calleeType.getParameters(), 1),
          NodeUtil.getInvocationArgsAsIterable(this.invocation));
    } else if (this.invocation.hasMoreThanOneChild()) {
      this.matchTemplateTypesFromNodes(
          this.calleeType.getParameters(), NodeUtil.getInvocationArgsAsIterable(this.invocation));
    }

    return ImmutableMap.copyOf(this.matchedTypes);
  }

  private void matchTemplateTypesRecursive(JSType paramType, JSType argType) {
    if (paramType.isTemplateType()) {
      // Recursive base case.
      // example: @param {T}
      this.recordTemplateMatch(paramType.toMaybeTemplateType(), argType);
      return;
    }

    // Unpack unions.
    if (paramType.isUnionType()) {
      // example: @param {Array.<T>|NodeList|Arguments|{length:number}}
      UnionType unionType = paramType.toMaybeUnionType();
      for (JSType alternate : unionType.getAlternates()) {
        this.matchTemplateTypesRecursive(alternate, argType);
      }
      return;
    } else if (argType.isUnionType()) {
      UnionType unionType = argType.toMaybeUnionType();
      for (JSType alternate : unionType.getAlternates()) {
        this.matchTemplateTypesRecursive(paramType, alternate);
      }
      return;
    }

    if (paramType.isFunctionType()) {
      FunctionType paramFunctionType = paramType.toMaybeFunctionType();
      FunctionType argFunctionType =
          argType.restrictByNotNullOrUndefined().collapseUnion().toMaybeFunctionType();
      if (argFunctionType != null && argFunctionType.isSubtype(paramType)) {
        // infer from return type of the function type
        this.matchTemplateTypesRecursive(
            paramFunctionType.getTypeOfThis(), argFunctionType.getTypeOfThis());
        // infer from return type of the function type
        this.matchTemplateTypesRecursive(
            paramFunctionType.getReturnType(), argFunctionType.getReturnType());
        // infer from parameter types of the function type
        this.matchTemplateTypesFromParameters(
            paramFunctionType.getParameters().iterator(),
            argFunctionType.getParameters().iterator());
      }
    } else if (paramType.isRecordType() && !paramType.isNominalType()) {
      // example: @param {{foo:T}}
      if (this.seenTypes.add(paramType)) {
        ObjectType paramRecordType = paramType.toObjectType();
        ObjectType argObjectType = argType.restrictByNotNullOrUndefined().toObjectType();
        if (argObjectType != null
            && !argObjectType.isUnknownType()
            && !argObjectType.isEmptyType()) {
          Set<String> names = paramRecordType.getPropertyNames();
          for (String name : names) {
            if (paramRecordType.hasOwnProperty(name) && argObjectType.hasProperty(name)) {
              this.matchTemplateTypesRecursive(
                  paramRecordType.getPropertyType(name), argObjectType.getPropertyType(name));
            }
          }
        }
        this.seenTypes.remove(paramType);
      }
    } else if (paramType.isTemplatizedType()) {
      // example: @param {Array<T>}
      TemplatizedType templatizedParamType = paramType.toMaybeTemplatizedType();
      int keyCount = templatizedParamType.getTemplateTypes().size();
      // TODO(johnlenz): determine why we are creating TemplatizedTypes for
      // types with no type arguments.
      if (keyCount == 0) {
        return;
      }

      ObjectType referencedParamType = templatizedParamType.getReferencedType();
      JSType argObjectType = argType.restrictByNotNullOrUndefined().collapseUnion();

      if (argObjectType.isSubtypeOf(referencedParamType)) {
        // If the argument type is a subtype of the parameter type, resolve any
        // template types amongst their templatized types.
        TemplateTypeMap paramTypeMap = paramType.getTemplateTypeMap();

        ImmutableList<TemplateType> keys = paramTypeMap.getTemplateKeys();
        TemplateTypeMap argTypeMap = argObjectType.getTemplateTypeMap();
        for (int index = keys.size() - keyCount; index < keys.size(); index++) {
          TemplateType key = keys.get(index);
          this.matchTemplateTypesRecursive(
              paramTypeMap.getResolvedTemplateType(key), argTypeMap.getResolvedTemplateType(key));
        }
      }
    }
  }

  private void matchTemplateTypesFromNodes(
      Iterable<Parameter> declParams, Iterable<Node> callParams) {
    this.matchTemplateTypesFromNodes(declParams.iterator(), callParams.iterator());
  }

  private void matchTemplateTypesFromNodes(
      Iterator<Parameter> declParams, Iterator<Node> callParams) {
    while (declParams.hasNext() && callParams.hasNext()) {
      Parameter declParam = declParams.next();
      this.matchTemplateTypesRecursive(
          declParam.getJSType(), this.getTypeOrUnknown(callParams.next()));

      if (declParam.isVariadic()) {
        while (callParams.hasNext()) {
          this.matchTemplateTypesRecursive(
              declParam.getJSType(), this.getTypeOrUnknown(callParams.next()));
        }
      }
    }
  }

  private void matchTemplateTypesFromParameters(
      Iterator<Parameter> declParams, Iterator<Parameter> callParams) {
    while (declParams.hasNext() && callParams.hasNext()) {
      Parameter declParam = declParams.next();
      this.matchTemplateTypesRecursive(declParam.getJSType(), callParams.next().getJSType());

      if (declParam.isVariadic()) {
        while (callParams.hasNext()) {
          this.matchTemplateTypesRecursive(declParam.getJSType(), callParams.next().getJSType());
        }
      }
    }
  }

  private void recordTemplateMatch(TemplateType template, JSType match) {
    if (match.isUnknownType()) {
      return;
    }

    // Don't worry about checking bounds here. We'll validate them once they're all collected.
    this.matchedTypes.merge(template, match, JSType::getLeastSupertype);
  }

  private final JSType getTypeOrUnknown(Node n) {
    JSType type = n.getJSType();
    return (type == null) ? this.unknownType : type;
  }
}
