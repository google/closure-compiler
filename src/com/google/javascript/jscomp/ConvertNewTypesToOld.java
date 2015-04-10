/*
 * Copyright 2015 The Closure Compiler Authors.
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

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.newtypes.FunctionType;
import com.google.javascript.jscomp.newtypes.JSType;
import com.google.javascript.jscomp.newtypes.JSTypes;
import com.google.javascript.jscomp.newtypes.NominalType;
import com.google.javascript.jscomp.newtypes.ObjectType;
import com.google.javascript.jscomp.newtypes.QualifiedName;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.TypeI;
import com.google.javascript.rhino.jstype.FunctionBuilder;
import com.google.javascript.rhino.jstype.FunctionParamBuilder;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.UnionTypeBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * This pass runs after the new type inference and converts type information in
 * the AST and in the compiler instance to the old type system.
 *
 * <p>This conversion allows projects to migrate to the new type inference while
 * we are still switching checks and optimizations to TypeI.
 */
public final class ConvertNewTypesToOld extends AbstractPostOrderCallback implements CompilerPass {
  private final AbstractCompiler compiler;
  private final JSTypeRegistry registry;
  private final JSTypes commonTypes;

  private final com.google.javascript.rhino.jstype.JSType OLD_UNKNOWN_FUNCTION_TYPE;
  private final com.google.javascript.rhino.jstype.JSType OLD_UNKNOWN_TYPE;

  private Node currentNode;

  // We cache all scalar types and all nominal types.
  // We don't cache other function types, record types and unions.
  private final Map<JSType, com.google.javascript.rhino.jstype.JSType> cachedTypes =
      new HashMap<>();
  private final Map<NominalType, com.google.javascript.rhino.jstype.FunctionType> cachedNomTypes =
      new HashMap<>();

  ConvertNewTypesToOld(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.registry = compiler.getTypeRegistry();
    this.commonTypes = compiler.getSymbolTable().getTypesUtilObject();

    this.OLD_UNKNOWN_FUNCTION_TYPE = registry.getNativeType(JSTypeNative.U2U_FUNCTION_TYPE);
    this.OLD_UNKNOWN_TYPE = registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);

    com.google.javascript.rhino.jstype.JSType oldNativeType;

    oldNativeType = registry.getNativeType(JSTypeNative.BOOLEAN_TYPE);
    JSType.BOOLEAN.setOldType(oldNativeType);
    cachedTypes.put(JSType.BOOLEAN, oldNativeType);

    JSType.TRUE_TYPE.setOldType(oldNativeType);
    cachedTypes.put(JSType.TRUE_TYPE, oldNativeType);

    JSType.FALSE_TYPE.setOldType(oldNativeType);
    cachedTypes.put(JSType.FALSE_TYPE, oldNativeType);

    oldNativeType = registry.getNativeType(JSTypeNative.NO_TYPE);
    JSType.BOTTOM.setOldType(oldNativeType);
    cachedTypes.put(JSType.BOTTOM, oldNativeType);

    oldNativeType = registry.getNativeType(JSTypeNative.NULL_TYPE);
    JSType.NULL.setOldType(oldNativeType);
    cachedTypes.put(JSType.NULL, oldNativeType);

    oldNativeType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    JSType.NUMBER.setOldType(oldNativeType);
    cachedTypes.put(JSType.NUMBER, oldNativeType);

    oldNativeType = registry.getNativeType(JSTypeNative.STRING_TYPE);
    JSType.STRING.setOldType(oldNativeType);
    cachedTypes.put(JSType.STRING, oldNativeType);

    oldNativeType = registry.getNativeType(JSTypeNative.ALL_TYPE);
    JSType.TOP.setOldType(oldNativeType);
    cachedTypes.put(JSType.TOP, oldNativeType);

    oldNativeType = registry.getNativeType(JSTypeNative.VOID_TYPE);
    JSType.UNDEFINED.setOldType(oldNativeType);
    cachedTypes.put(JSType.UNDEFINED, oldNativeType);

    JSType.UNKNOWN.setOldType(OLD_UNKNOWN_TYPE);
    cachedTypes.put(JSType.UNKNOWN, OLD_UNKNOWN_TYPE);

    commonTypes.topFunction().setOldType(OLD_UNKNOWN_FUNCTION_TYPE);
    cachedTypes.put(commonTypes.topFunction(), OLD_UNKNOWN_FUNCTION_TYPE);

    commonTypes.qmarkFunction().setOldType(OLD_UNKNOWN_FUNCTION_TYPE);
    cachedTypes.put(commonTypes.qmarkFunction(), OLD_UNKNOWN_FUNCTION_TYPE);
  }

  // NOTE(dimvar): We're missing many type annotations on externs because NTI
  // doesn't run on externs. If we end up needing it, we can mark them in GTI
  // or change NTI to mark them.
  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, externs, this);
    NodeTraversal.traverse(compiler, root, this);
    // Not needed for the rest of the compilation, don't keep it around
    compiler.setSymbolTable(null);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    TypeI ti = n.getTypeI();
    if (ti instanceof com.google.javascript.rhino.jstype.JSType) {
      // Even though the old type checker doesn't run before this pass, we may
      // find an old type because ProcessClosurePrimitives sets a type on object
      // literals that declare namespaces.
      return;
    }
    JSType newType = (JSType) ti;
    if (newType != null) {
      this.currentNode = n;
      n.setTypeI(convertNewTypeToOld(newType));
    }
  }

  // NOTE(dimvar): when debugging a unit test that passes with old TI and fails
  // with NTI+conversion, it's useful to do Node#toStringTree() on the AST, to
  // see where the types differ.
  private com.google.javascript.rhino.jstype.JSType convertNewTypeToOld(JSType newType) {
    if (newType == null) {
      return null;
    }

    com.google.javascript.rhino.jstype.JSType oldType =
        (com.google.javascript.rhino.jstype.JSType) newType.getOldType();

    // NOTE(dimvar): Reusing the oldType here makes 8 unit tests fail.
    // I have not debugged it yet. I am assuming that the first time we
    // convert the type, it gets some jsdoc from currentNode. When we see
    // newType again on a different node, we don't convert, and reuse the old
    // type with the jsdoc, but if we had converted, the result wouldn't get an
    // associated jsdoc.
    // I want to reuse types here and fix this in CheckAccessControls. It is
    // probably an issue where the pass assumes sth about which file the type
    // appears in by grabbing the jsdoc of the type; but the jsdoc shows where
    // the type is defined, not where it's used.
    // If we uncomment this, then we can delete cachedTypes because in the
    // constructor of this pass, we do the conversion for the scalar types.

    // if (oldType != null) {
    //   return oldType;
    // }

    if (cachedTypes.containsKey(newType)) {
      return cachedTypes.get(newType);
    }

    if (newType.isUnion()) {
      oldType = convertUnionType(newType);
    } else {
      // The type is not one of: union, scalar, top or unknown.
      // It is one of: function, object, enum, type variable.
      FunctionType ft = newType.getFunTypeIfSingletonObj();
      if (ft != null) {
        oldType = convertFunctionType(ft);
      } else if (newType.isTypeVariable() || newType.isEnumElement()) {
        return OLD_UNKNOWN_TYPE;
      } else {
        ObjectType ot = newType.getObjectTypeIfSingletonObj();
        Preconditions.checkNotNull(ot, "Null object type for JSType: %s", newType);
        oldType = convertObjectType(ot);
      }
    }
    newType.setOldType(oldType);
    return oldType;
  }

  private com.google.javascript.rhino.jstype.JSType convertUnionType(JSType newType) {
    UnionTypeBuilder builder = new UnionTypeBuilder(this.registry);
    for (JSType alternate : newType.getAlternates()) {
      builder.addAlternate(convertNewTypeToOld(alternate));
    }
    return builder.build();
  }

  private com.google.javascript.rhino.jstype.JSType convertObjectType(ObjectType ot) {
    NominalType nt = ot.getNominalType();
    if (nt == null) {
      com.google.javascript.rhino.jstype.ObjectType oldObject =
          this.registry.createAnonymousObjectType(NodeUtil.getBestJSDocInfo(currentNode));
      for (String pname : ot.getAllOwnProps()) {
        QualifiedName qname = new QualifiedName(pname);
        JSType ptype = ot.getDeclaredProp(qname);
        if (ptype == null) {
          ptype = ot.getProp(qname);
          Preconditions.checkNotNull(ptype);
          oldObject.defineInferredProperty(pname, convertNewTypeToOld(ptype), null);
        } else {
          oldObject.defineDeclaredProperty(pname, convertNewTypeToOld(ptype), null);
        }
        Node defSite = ot.getPropDefsite(qname);
        if (defSite != null) {
          oldObject.setPropertyNode(pname, defSite);
          oldObject.setPropertyJSDocInfo(pname, NodeUtil.getBestJSDocInfo(defSite));
        }
      }
      return oldObject;
    }
    if (cachedNomTypes.containsKey(nt)) {
      return cachedNomTypes.get(nt).getTypeOfThis();
    }
    return OLD_UNKNOWN_TYPE;
  }

  private com.google.javascript.rhino.jstype.JSType convertFunctionType(FunctionType ft) {
    if (ft.isTopFunction() || ft.isQmarkFunction()) {
      return OLD_UNKNOWN_FUNCTION_TYPE;
    }
    if (ft.isConstructor()) {
      return convertConstructorFunction(ft);
    } else {
      FunctionBuilder funBuilder = new FunctionBuilder(this.registry);
      funBuilder.withParamsNode(convertFunctionParams(ft));
      funBuilder.withReturnType(convertNewTypeToOld(ft.getReturnType()));
      funBuilder.withTypeOfThis(convertNewTypeToOld(ft.getThisType()));
      com.google.javascript.rhino.jstype.FunctionType result = funBuilder.build();
      result.setJSDocInfo(NodeUtil.getBestJSDocInfo(currentNode));
      return result;
    }
  }

  private Node convertFunctionParams(FunctionType ft) {
    FunctionParamBuilder paramBuilder = new FunctionParamBuilder(this.registry);

    int minArity = ft.getMinArity();
    com.google.javascript.rhino.jstype.JSType[] requiredFormals =
        new com.google.javascript.rhino.jstype.JSType[minArity];
    for (int i = 0; i < minArity; i++) {
      requiredFormals[i] = convertNewTypeToOld(ft.getFormalType(i));
    }
    paramBuilder.addRequiredParams(requiredFormals);

    int maxArity = ft.getMaxArityWithoutRestFormals();
    if (maxArity > minArity) {
      com.google.javascript.rhino.jstype.JSType[] optionalFormals =
          new com.google.javascript.rhino.jstype.JSType[maxArity - minArity];
      for (int i = minArity; i < maxArity; i++) {
        optionalFormals[i - minArity] = convertNewTypeToOld(ft.getFormalType(i));
      }
      paramBuilder.addOptionalParams(optionalFormals);
    }

    if (ft.hasRestFormals()) {
      paramBuilder.addVarArgs(convertNewTypeToOld(ft.getRestFormalsType()));
    }
    return paramBuilder.build();
  }

  private com.google.javascript.rhino.jstype.JSType convertConstructorFunction(FunctionType ft) {
    NominalType nt = ft.getInstanceTypeOfCtor().getNominalTypeIfSingletonObj();
    if (cachedNomTypes.containsKey(nt)) {
      return cachedNomTypes.get(nt);
    }
    String ntName = nt.getName();
    Node source = nt.getDefsite();
    com.google.javascript.rhino.jstype.FunctionType oldCtor =
        this.registry.createConstructorType(ntName, source,
            convertFunctionParams(ft), convertNewTypeToOld(ft.getReturnType()), null);
    convertClassProperties(nt, oldCtor);
    oldCtor.setSource(source);
    NominalType superNt = nt.getInstantiatedSuperclass();
    if (superNt != null) {
      com.google.javascript.rhino.jstype.ObjectType oldPrototype =
          convertNewTypeToOld(superNt.getInstanceAsJSType()).toObjectType();
      if (oldPrototype != null) {
        oldCtor.setPrototypeBasedOn(oldPrototype);
      }
    }
    JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(source);
    oldCtor.setJSDocInfo(jsdoc);
    oldCtor.getInstanceType().setJSDocInfo(jsdoc);
    cachedNomTypes.put(nt, oldCtor);
    return oldCtor;
  }

  private void convertClassProperties(
      NominalType nt, com.google.javascript.rhino.jstype.FunctionType oldCtor) {
    // TODO(dimvar): copy over the static properties of the constructor
    com.google.javascript.rhino.jstype.ObjectType oldInstance = oldCtor.getInstanceType();
    for (String pname : nt.getAllOwnProps()) {
      JSType ptype = nt.getPropDeclaredType(pname);
      if (ptype == null) {
        oldInstance.defineInferredProperty(pname, OLD_UNKNOWN_TYPE, null);
      } else {
        oldInstance.defineDeclaredProperty(pname, convertNewTypeToOld(ptype), null);
      }
      Node defSite = nt.getPropDefsite(pname);
      if (defSite != null) {
        oldInstance.setPropertyNode(pname, defSite);
        oldInstance.setPropertyJSDocInfo(pname, NodeUtil.getBestJSDocInfo(defSite));
      }
    }
  }
}
