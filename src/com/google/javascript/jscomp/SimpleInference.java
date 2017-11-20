/*
 * Copyright 2013 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.newtypes.Declaration;
import com.google.javascript.jscomp.newtypes.DeclaredFunctionType;
import com.google.javascript.jscomp.newtypes.EnumType;
import com.google.javascript.jscomp.newtypes.FunctionNamespace;
import com.google.javascript.jscomp.newtypes.FunctionType;
import com.google.javascript.jscomp.newtypes.JSType;
import com.google.javascript.jscomp.newtypes.JSTypes;
import com.google.javascript.jscomp.newtypes.Namespace;
import com.google.javascript.jscomp.newtypes.QualifiedName;
import com.google.javascript.jscomp.newtypes.RawNominalType;
import com.google.javascript.rhino.Node;

/**
 * Used during phase 1 of the new type inference.
 * While collecting types and building the inheritance hierarchy, we need to infer the types
 * of expressions using only information from type annotations, without doing flow analysis.
 * This class performs this simple inference.
 * The inferred types are used to infer types for unannotated constants, to annotate externs,
 * and to infer signatures of unannotated functions passed as callbacks.
 */
final class SimpleInference {
  // A property of this name is used as a marker during const inference,
  // to avoid misuse of constructor types.
  private static final QualifiedName CONST_INFERENCE_MARKER =
      new QualifiedName("jscomp$infer$const$property");

  private final JSTypes commonTypes;
  private final GlobalTypeInfo gti;
  private boolean scopesAreFrozen = false;

  SimpleInference(GlobalTypeInfo gti) {
    this.gti = gti;
    this.commonTypes = gti.getCommonTypes();
  }

  void setScopesAreFrozen() {
    this.scopesAreFrozen = true;
  }

  JSType inferDeclaration(Declaration decl) {
    if (decl == null) {
      return null;
    }
    if (decl.getNamespace() != null && this.scopesAreFrozen) {
      return decl.getNamespace().toJSType();
    }
    // Namespaces (literals, enums, constructors) get populated during ProcessScope,
    // so it's generally NOT safe to convert them to jstypes until after ProcessScope is done.
    // However, we've seen examples where it is useful to use the constructor type
    // during inference, e.g., to get the type of the instance from it.
    // We allow this use case but add a marker property to make sure that the constructor type
    // itself doesn't leak into the result.
    if (decl.getNominal() != null) {
      FunctionType ctorFn = decl.getNominal().getConstructorFunction();
      if (ctorFn == null) {
        return null;
      }
      if (this.scopesAreFrozen) {
        return this.commonTypes.fromFunctionType(ctorFn);
      }
      return this.commonTypes.fromFunctionType(ctorFn)
          .withProperty(CONST_INFERENCE_MARKER, this.commonTypes.UNKNOWN);
    }
    if (decl.getTypeOfSimpleDecl() != null) {
      return decl.getTypeOfSimpleDecl();
    }
    NTIScope funScope = (NTIScope) decl.getFunctionScope();
    if (funScope != null) {
      DeclaredFunctionType dft = funScope.getDeclaredFunctionType();
      if (dft == null) {
        return null;
      }
      return this.commonTypes.fromFunctionType(dft.toFunctionType());
    }
    return null;
  }

  JSType inferExpr(Node n, NTIScope scope) {
    JSType t = inferExprRecur(n, scope);
    // If the inferred type has the marker property, discard it.
    // Note that when the marker is nested somewhere in the type, this heuristic breaks,
    // and the marker leaks into the result.
    // Hopefully this is rare in practice, but I'm not sure; try it out.
    if (t == null || t.mayHaveProp(CONST_INFERENCE_MARKER)) {
      return null;
    }
    return t;
  }

  private FunctionType inferFunction(Node n, NTIScope scope) {
    if (n.isQualifiedName()) {
      Declaration decl = scope.getDeclaration(QualifiedName.fromNode(n), false);
      if (decl == null) {
        JSType t = inferExprRecur(n, scope);
        if (t != null) {
          return t.getFunTypeIfSingletonObj();
        }
      } else if (decl.getNominal() != null) {
        return decl.getNominal().getConstructorFunction();
      } else if (decl.getFunctionScope() != null) {
        DeclaredFunctionType funType = decl.getFunctionScope().getDeclaredFunctionType();
        if (funType != null) {
          return funType.toFunctionType();
        }
      } else if (decl.getNamespace() != null) {
        Namespace ns = decl.getNamespace();
        if (ns instanceof FunctionNamespace) {
          DeclaredFunctionType funType =
              ((FunctionNamespace) ns).getScope().getDeclaredFunctionType();
          return checkNotNull(funType).toFunctionType();
        }
      } else if (decl.getTypeOfSimpleDecl() != null) {
        return decl.getTypeOfSimpleDecl().getFunTypeIfSingletonObj();
      }
    }
    JSType t = inferExprRecur(n, scope);
    return t == null ? null : t.getFunTypeIfSingletonObj();
  }

  private JSType inferCallNew(Node n, NTIScope scope) {
    Node callee = n.getFirstChild();
    // We special-case the function goog.getMsg, which is used by the
    // compiler for i18n.
    if (callee.matchesQualifiedName("goog.getMsg")) {
      return this.commonTypes.STRING;
    }
    FunctionType funType = inferFunction(callee, scope);
    if (funType == null) {
      return null;
    }
    if (funType.isGeneric()) {
      funType = inferInstantiatedCallee(n, funType, true, scope);
      if (funType == null) {
        return null;
      }
    }
    JSType retType = n.isNew() ? funType.getThisType() : funType.getReturnType();
    return retType;
  }

  private JSType inferExprRecur(Node n, NTIScope scope) {
    switch (n.getToken()) {
      case REGEXP:
        return this.commonTypes.getRegexpType();
      case CAST:
        return (JSType) n.getTypeI();
      case ARRAYLIT: {
        if (!n.hasChildren()) {
          return this.commonTypes.getArrayInstance();
        }
        Node child = n.getFirstChild();
        JSType arrayType = inferExprRecur(child, scope);
        if (arrayType == null) {
          return null;
        }
        while (null != (child = child.getNext())) {
          if (!arrayType.equals(inferExprRecur(child, scope))) {
            return null;
          }
        }
        return this.commonTypes.getArrayInstance(arrayType);
      }
      case TRUE:
      case FALSE:
        return this.commonTypes.BOOLEAN;
      case THIS:
        return scope.getDeclaredTypeOf("this");
      case NAME:
        return inferDeclaration(
            scope.getDeclaration(n.getString(), false));
      case OBJECTLIT: {
        JSType objLitType = this.commonTypes.getEmptyObjectLiteral();
        for (Node prop : n.children()) {
          JSType propType = null;
          if (prop.hasChildren()) {
            propType = inferExprRecur(prop.getFirstChild(), scope);
          }
          if (propType == null || prop.isComputedProp()) {
            return null;
          }
          objLitType = objLitType.withProperty(
              new QualifiedName(NodeUtil.getObjectLitKeyName(prop)),
              propType);
        }
        return objLitType;
      }
      case GETPROP:
        return inferPropAccess(n.getFirstChild(), n.getLastChild().getString(), scope);
      case GETELEM:
        return inferGetelem(n, scope);
      case COMMA:
      case ASSIGN:
        return inferExprRecur(n.getLastChild(), scope);
      case CALL:
      case NEW:
        return inferCallNew(n, scope);
      case AND:
      case OR:
        return inferAndOr(n, scope);
      case HOOK: {
        JSType lhs = inferExprRecur(n.getSecondChild(), scope);
        JSType rhs = inferExprRecur(n.getLastChild(), scope);
        return lhs == null || rhs == null ? null : JSType.join(lhs, rhs);
      }
      case FUNCTION: {
        NTIScope s = scope.getScope(this.gti.getFunInternalName(n));
        DeclaredFunctionType dft = s.getDeclaredFunctionType();
        return dft == null ? null
            : this.commonTypes.fromFunctionType(dft.toFunctionType());
      }
      default:
        switch (NodeUtil.getKnownValueType(n)) {
          case NULL:
            return this.commonTypes.NULL;
          case VOID:
            return this.commonTypes.UNDEFINED;
          case NUMBER:
            return this.commonTypes.NUMBER;
          case STRING:
            return this.commonTypes.STRING;
          case BOOLEAN:
            return this.commonTypes.BOOLEAN;
          default:
            return null;
        }
    }
  }

  private JSType inferPrototypeProperty(Node recv, String pname, NTIScope scope) {
    QualifiedName recvQname = QualifiedName.fromNode(recv);
    Declaration decl = scope.getDeclaration(recvQname, false);
    if (decl != null) {
      Namespace ns = decl.getNamespace();
      if (ns instanceof RawNominalType) {
        return ((RawNominalType) ns).getProtoPropDeclaredType(pname);
      }
    }
    return null;
  }

  private JSType inferPropAccess(Node recv, String pname, NTIScope scope) {
    if (recv.isGetProp() && recv.getLastChild().getString().equals("prototype")) {
      return inferPrototypeProperty(recv.getFirstChild(), pname, scope);
    }
    QualifiedName propQname = new QualifiedName(pname);
    JSType recvType = null;
    if (recv.isQualifiedName()) {
      QualifiedName recvQname = QualifiedName.fromNode(recv);
      Declaration decl = scope.getDeclaration(recvQname, false);
      if (decl != null) {
        EnumType et = decl.getEnum();
        if (et != null && et.enumLiteralHasKey(pname)) {
          return et.getPropType();
        }
        Namespace ns = decl.getNamespace();
        if (ns != null) {
          return inferDeclaration(ns.getDeclaration(propQname));
        }
        recvType = decl.getTypeOfSimpleDecl();
      }
    }
    if (recvType == null) {
      recvType = inferExprRecur(recv, scope);
    }
    if (recvType == null) {
      return null;
    }
    if (recvType.isScalar()) {
      recvType = recvType.autobox();
    }
    FunctionType ft = recvType.getFunTypeIfSingletonObj();
    if (ft != null && pname.equals("call")) {
      return this.commonTypes.fromFunctionType(ft.transformByCallProperty());
    } else if (ft != null && pname.equals("apply")) {
      return this.commonTypes.fromFunctionType(ft.transformByApplyProperty());
    }
    if (recvType.mayHaveProp(propQname)) {
      return recvType.getProp(propQname);
    }
    return null;
  }

  private JSType inferGetelem(Node n, NTIScope scope) {
    checkState(n.isGetElem());
    Node recv = n.getFirstChild();
    Node propNode = n.getLastChild();
    // As in NewTypeInference.java, we try to treat bracket accesses with a
    // string literal as precisely as dot accesses.
    if (propNode.isString()) {
      JSType propType = inferPropAccess(recv, propNode.getString(), scope);
      if (propType != null) {
        return propType;
      }
    }
    JSType recvType = inferExprRecur(recv, scope);
    if (recvType != null) {
      JSType indexType = recvType.getIndexType();
      if (indexType != null) {
        JSType propType = inferExprRecur(propNode, scope);
        if (propType != null && propType.isSubtypeOf(indexType)) {
          return recvType.getIndexedType();
        }
      }
    }
    return null;
  }

  private JSType inferAndOr(Node n, NTIScope scope) {
    checkState(n.isOr() || n.isAnd());
    JSType lhs = inferExprRecur(n.getFirstChild(), scope);
    if (lhs == null) {
      return null;
    }
    JSType rhs = inferExprRecur(n.getSecondChild(), scope);
    if (rhs == null) {
      return null;
    }
    if (lhs.equals(rhs)) {
      return lhs;
    }
    if (n.isAnd()) {
      return JSType.join(lhs.specialize(this.commonTypes.FALSY), rhs);
    }
    return JSType.join(lhs.specialize(this.commonTypes.TRUTHY), rhs);
  }

  FunctionType inferInstantiatedCallee(
      Node call, FunctionType calleeType, boolean bailForUntypedArguments, NTIScope scope) {
    Node callee = call.getFirstChild();
    Preconditions.checkArgument(calleeType.isGeneric(),
        "Expected generic type for %s but found %s", callee, calleeType);
    // The receiver type is useful for inference when calleeType has a @this annotation
    // that includes a type variable.
    JSType recvType = null;
    if (callee.isGetProp() && callee.getFirstChild().isQualifiedName()) {
      Node recv = callee.getFirstChild();
      QualifiedName recvQname = QualifiedName.fromNode(recv);
      Declaration decl = scope.getDeclaration(recvQname, false);
      if (decl != null) {
        recvType = decl.getTypeOfSimpleDecl();
      }
    }
    ImmutableList.Builder<JSType> argTypes = ImmutableList.builder();
    for (Node argNode = call.getSecondChild(); argNode != null; argNode = argNode.getNext()) {
      JSType t = inferExprRecur(argNode, scope);
      if (t == null) {
        if (bailForUntypedArguments && !argNode.isFunction()) {
          // Used for @const inference, where we want to be strict.
          return null;
        } else {
          // Used when inferring a signature for unannotated callbacks passed to generic
          // functions. Whatever type variable we can't infer will become unknown.
          t = this.commonTypes.BOTTOM;
        }
      }
      argTypes.add(t);
    }
    return calleeType.instantiateGenericsFromArgumentTypes(recvType, argTypes.build());
  }
}
