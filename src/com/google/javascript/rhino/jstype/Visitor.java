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

import com.google.errorprone.annotations.ForOverride;
import javax.annotation.Nullable;

/**
 * A vistor for {@code JSType}s.
 *
 * <p>During visitation, an instance may calculate a result value.
 */
public interface Visitor<T> {
  /**
   * Bottom type's case.
   */
  T caseNoType(NoType type);

  /**
   * Enum element type's case.
   */
  T caseEnumElementType(EnumElementType type);

  /**
   * All type's case.
   */
  T caseAllType();

  /**
   * Boolean value type's case.
   */
  T caseBooleanType();

  /**
   * Bottom Object type's case.
   */
  T caseNoObjectType();

  /**
   * Function type's case.
   */
  T caseFunctionType(FunctionType type);

  /**
   * Object type's case.
   */
  T caseObjectType(ObjectType type);

  /**
   * Unknown type's case.
   */
  T caseUnknownType();

  /**
   * Null type's case.
   */
  T caseNullType();

  /**
   * Named type's case.
   */
  T caseNamedType(NamedType type);

  /**
   * Proxy type's case.
   */
  T caseProxyObjectType(ProxyObjectType type);

  /**
   * Number value type's case.
   */
  T caseNumberType();

  /** BigInt value type's case. */
  T caseBigIntType();

  /** String value type's case. */
  T caseStringType();

  /**
   * Symbol value type's case.
   */
  T caseSymbolType();

  /**
   * Void type's case.
   */
  T caseVoidType();

  /**
   * Union type's case.
   */
  T caseUnionType(UnionType type);

  /**
   * Templatized type's case.
   */
  T caseTemplatizedType(TemplatizedType type);

  /**
   * Template type's case.
   */
  T caseTemplateType(TemplateType templateType);

  /** A type visitor with a default behaviour. */
  public abstract class WithDefaultCase<T> implements Visitor<T> {

    /**
     * Called for all cases unless the specific case is overridden in the concrete subclass.
     *
     * <p>{@code null} is passed iff the caller is a spcific case that has no {@code JSType}
     * argument, example {@link #caseAllType()}.
     */
    @ForOverride
    protected abstract T caseDefault(@Nullable JSType type);

    @Override
    public T caseNoType(NoType type) {
      return this.caseDefault(type);
    }

    @Override
    public T caseEnumElementType(EnumElementType type) {
      return this.caseDefault(type);
    }

    @Override
    public T caseAllType() {
      return this.caseDefault(null);
    }

    @Override
    public T caseBooleanType() {
      return this.caseDefault(null);
    }

    @Override
    public T caseNoObjectType() {
      return this.caseDefault(null);
    }

    @Override
    public T caseFunctionType(FunctionType type) {
      return this.caseDefault(type);
    }

    @Override
    public T caseObjectType(ObjectType type) {
      return this.caseDefault(type);
    }

    @Override
    public T caseUnknownType() {
      return this.caseDefault(null);
    }

    @Override
    public T caseNullType() {
      return this.caseDefault(null);
    }

    @Override
    public T caseNamedType(NamedType type) {
      return this.caseDefault(type);
    }

    @Override
    public T caseProxyObjectType(ProxyObjectType type) {
      return this.caseDefault(type);
    }

    @Override
    public T caseNumberType() {
      return this.caseDefault(null);
    }

    @Override
    public T caseBigIntType() {
      return this.caseDefault(null);
    }

    @Override
    public T caseStringType() {
      return this.caseDefault(null);
    }

    @Override
    public T caseSymbolType() {
      return this.caseDefault(null);
    }

    @Override
    public T caseVoidType() {
      return this.caseDefault(null);
    }

    @Override
    public T caseUnionType(UnionType type) {
      return this.caseDefault(type);
    }

    @Override
    public T caseTemplatizedType(TemplatizedType type) {
      return this.caseDefault(type);
    }

    @Override
    public T caseTemplateType(TemplateType type) {
      return this.caseDefault(type);
    }
  }
}
