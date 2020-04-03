/*
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Bob Jervis
 *   Google Inc.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */

package com.google.javascript.rhino.jstype;

import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;

import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.Node;
import java.util.Objects;

/**
 * Models a "bare" function type: from some parameter types to a return type.
 *
 * <p>JavaScript functions include more things like properties, type of THIS, etc, and are modeled
 * by {@link FunctionType}.
 */
final class ArrowType extends JSType {
  private static final long serialVersionUID = 1L;

  private static final JSTypeClass TYPE_CLASS = JSTypeClass.ARROW;

  final Node parameters;
  private JSType returnType;

  // Whether the return type is inferred.
  final boolean returnTypeInferred;

  ArrowType(JSTypeRegistry registry, Node parameters, JSType returnType) {
    this(registry, parameters, returnType, false);
  }

  ArrowType(JSTypeRegistry registry, Node parameters,
      JSType returnType, boolean returnTypeInferred) {
    super(registry);

    this.parameters = parameters == null ?
        registry.createParametersWithVarArgs(getNativeType(UNKNOWN_TYPE)) :
        parameters;
    this.returnType = returnType == null ?
        getNativeType(UNKNOWN_TYPE) : returnType;
    this.returnTypeInferred = returnTypeInferred;

    registry.getResolver().resolveIfClosed(this, TYPE_CLASS);
  }

  @Override
  JSTypeClass getTypeClass() {
    return TYPE_CLASS;
  }

  @Override
  int recursionUnsafeHashCode() {
    int hashCode = Objects.hashCode(returnType);
    if (parameters != null) {
      Node param = parameters.getFirstChild();
      while (param != null) {
        hashCode = hashCode * 31 + Objects.hashCode(param.getJSType());
        param = param.getNext();
      }
    }
    return hashCode;
  }

  public JSType getReturnType() {
    return this.returnType;
  }

  @Override
  public JSType getLeastSupertype(JSType that) {
    throw new UnsupportedOperationException();
  }

  @Override
  public JSType getGreatestSubtype(JSType that) {
    throw new UnsupportedOperationException();
  }

  @Override
  public TernaryValue testForEquality(JSType that) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T visit(Visitor<T> visitor) {
    throw new UnsupportedOperationException();
  }

  @Override <T> T visit(RelationshipVisitor<T> visitor, JSType that) {
    throw new UnsupportedOperationException();
  }

  @Override
  public BooleanLiteralSet getPossibleToBooleanOutcomes() {
    return BooleanLiteralSet.TRUE;
  }

  @Override
  JSType resolveInternal(ErrorReporter reporter) {
    returnType = safeResolve(returnType, reporter);
    if (parameters != null) {
      for (Node paramNode = parameters.getFirstChild();
           paramNode != null; paramNode = paramNode.getNext()) {
        paramNode.setJSType(paramNode.getJSType().resolve(reporter));
      }
    }
    return this;
  }

  boolean hasUnknownParamsOrReturn() {
    if (parameters != null) {
      for (Node paramNode = parameters.getFirstChild();
           paramNode != null; paramNode = paramNode.getNext()) {
        JSType type = paramNode.getJSType();
        if (type == null || type.isUnknownType()) {
          return true;
        }
      }
    }
    return returnType == null || returnType.isUnknownType();
  }

  @Override
  void appendTo(TypeStringBuilder sb) {
    sb.append("[ArrowType]");
  }

  @Override
  public boolean hasAnyTemplateTypesInternal() {
    return returnType.hasAnyTemplateTypes()
        || hasTemplatedParameterType();
  }

  private boolean hasTemplatedParameterType() {
    if (parameters != null) {
      for (Node paramNode = parameters.getFirstChild();
           paramNode != null; paramNode = paramNode.getNext()) {
        JSType type = paramNode.getJSType();
        if (type != null && type.hasAnyTemplateTypes()) {
          return true;
        }
      }
    }
    return false;
  }
}
