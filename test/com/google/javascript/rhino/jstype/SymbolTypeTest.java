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
 *   John Lenz
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;

import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the semantics of {@link SymbolType} */
@RunWith(JUnit4.class)
public final class SymbolTypeTest extends BaseJSTypeTestCase {

  @Test
  public void symbolType_isSymbolValueType() {
    JSType symbolType = registry.getNativeType(JSTypeNative.SYMBOL_TYPE);
    assertThat(symbolType.isSymbolValueType()).isTrue();
  }

  @Test
  public void symbolType_isNotKnownSymbolValueType() {
    JSType symbolType = registry.getNativeType(JSTypeNative.SYMBOL_TYPE);
    assertThat(symbolType.isKnownSymbolValueType()).isFalse();
  }

  @Test
  public void symbolType_autoBoxesToSymbolObject() {
    JSType symbolType = registry.getNativeType(JSTypeNative.SYMBOL_TYPE);
    assertThat(symbolType.autoboxesTo())
        .isEqualTo(registry.getNativeType(JSTypeNative.SYMBOL_OBJECT_TYPE));
  }

  @Test
  public void knownSymbolType_isKnownSymbolValueType() {
    KnownSymbolType knownSymbolType = new KnownSymbolType(registry, "Symbol.iterator");
    assertThat(knownSymbolType.isKnownSymbolValueType()).isTrue();
  }

  @Test
  public void knownSymbolType_isSubtypeOfSymbolType() {
    KnownSymbolType knownSymbolType = new KnownSymbolType(registry, "Symbol.iterator");
    assertThat(knownSymbolType.isSubtypeOf(SYMBOL_TYPE)).isTrue();
  }

  @Test
  public void knownSymbolType_reflexiveEqualityAndSubtyping() {
    KnownSymbolType knownSymbolType = new KnownSymbolType(registry, "Symbol.iterator");

    assertType(knownSymbolType).isSubtypeOf(knownSymbolType);
    assertType(knownSymbolType).isEqualTo(knownSymbolType);
  }

  @Test
  public void knownSymbolType_notSubtypeOfOtherKnownSymbolTypes() {
    KnownSymbolType foo1 = new KnownSymbolType(registry, "foo");
    KnownSymbolType foo2 = new KnownSymbolType(registry, "foo");
    KnownSymbolType bar = new KnownSymbolType(registry, "bar");

    assertType(foo1).isNotSubtypeOf(foo2);
    assertType(foo1).isNotSubtypeOf(bar);
    assertType(foo2).isNotSubtypeOf(bar);
    assertType(bar).isNotSubtypeOf(foo1);
    assertType(bar).isNotSubtypeOf(foo2);
  }

  @Test
  public void defineSymbolProperty() {
    Property.SymbolKey foo = new Property.SymbolKey(new KnownSymbolType(registry, "foo"));

    ARRAY_TYPE.defineDeclaredProperty(foo, STRING_TYPE, null);

    assertType(ARRAY_TYPE).withTypeOfProp(foo.symbol()).isString();
  }

  @Test
  public void defineSymbolProperty_structuralType() {
    ObjectType structural = registry.createAnonymousObjectType(null);
    Property.SymbolKey foo = new Property.SymbolKey(new KnownSymbolType(registry, "foo"));

    structural.defineDeclaredProperty(foo, STRING_TYPE, null);
    structural.defineDeclaredProperty("bar", NUMBER_TYPE, null);
    structural.defineDeclaredProperty("raz", NUMBER_TYPE, null);

    assertType(structural).withTypeOfProp(foo.symbol()).isString();
    assertType(structural)
        .toStringIsEqualTo(
            """
            {
              bar: number,
              [foo]: string,
              raz: number
            }\
            """);
  }

  @Test
  public void defineSymbolProperty_overlappingSymbolAndStringKeyNames() {
    ObjectType structural = registry.createAnonymousObjectType(null);
    Property.SymbolKey foo1 = new Property.SymbolKey(new KnownSymbolType(registry, "foo"));
    Property.SymbolKey foo2 = new Property.SymbolKey(new KnownSymbolType(registry, "foo"));
    Property.SymbolKey foo3 = new Property.SymbolKey(new KnownSymbolType(registry, "foo"));

    structural.defineDeclaredProperty(foo1, STRING_TYPE, null);
    structural.defineDeclaredProperty(foo2, NUMBER_TYPE, null);
    // add a symbol key recursively referencing the type itself, to verify we don't get infinite
    // recursion.
    structural.defineDeclaredProperty(foo3, structural, null);
    structural.defineDeclaredProperty("foo", STRING_TYPE, null);

    assertType(structural)
        .toStringIsEqualTo(
            """
            {
              foo: string,
              [foo]: number,
              [foo]: string,
              [foo]: {...}
            }\
            """);
  }

  @Test
  public void defineSymbolProperty_templatized() {
    Property.SymbolKey foo = new Property.SymbolKey(new KnownSymbolType(registry, "Symbol.foo"));
    TemplateType arrayKey = registry.getArrayElementKey();
    ObjectType promiseType = registry.getNativeObjectType(JSTypeNative.PROMISE_TYPE);
    JSType promiseOfArrayKey = registry.createTemplatizedType(promiseType, arrayKey);

    // /** @type {T|!Promise<T>} */
    // Array.prototype[Symbol.foo];
    JSType fooPropType = registry.createUnionType(arrayKey, promiseOfArrayKey);
    JSType stringArray = registry.createTemplatizedType(ARRAY_TYPE, STRING_TYPE);
    ARRAY_TYPE.defineDeclaredProperty(foo, fooPropType, null);

    assertType(ARRAY_TYPE).withTypeOfProp(foo.symbol()).isUnionOf(arrayKey, promiseOfArrayKey);
    // for an Array<string>, Symbol.foo is a (string|Promise<string>)
    assertType(stringArray)
        .withTypeOfProp(foo.symbol())
        .isUnionOf(STRING_TYPE, registry.createTemplatizedType(promiseType, STRING_TYPE));
    assertType(stringArray.findPropertyType(foo))
        .isUnionOf(STRING_TYPE, registry.createTemplatizedType(promiseType, STRING_TYPE));
  }

  @Test
  public void defineSymbolProperty_symbolsWithSameNameAreStillUnique() {
    Property.SymbolKey foo1 = new Property.SymbolKey(new KnownSymbolType(registry, "Symbol.foo"));
    Property.SymbolKey foo2 = new Property.SymbolKey(new KnownSymbolType(registry, "Symbol.foo"));

    ARRAY_TYPE.defineDeclaredProperty(foo1, registry.getNativeType(JSTypeNative.STRING_TYPE), null);

    assertType(ARRAY_TYPE).withTypeOfProp(foo1.symbol()).isString();
    assertThat(ARRAY_TYPE.hasProperty(foo2)).isFalse();
  }

  @Test
  public void defineSymbolProperty_hasOwnPropertyVsHasProperty() {
    ObjectType iterable = registry.getNativeObjectType(JSTypeNative.ITERABLE_TYPE);
    // IterableIterator extends Iterable
    ObjectType iterableIterator = registry.getNativeObjectType(JSTypeNative.ITERATOR_ITERABLE_TYPE);

    Property.SymbolKey foo = new Property.SymbolKey(new KnownSymbolType(registry, "foo"));
    iterable.defineDeclaredProperty(foo, STRING_TYPE, null);

    assertType(iterableIterator).hasProperty(foo.symbol());
    assertThat(iterableIterator.hasOwnProperty(foo)).isFalse();

    assertType(iterable).hasProperty(foo.symbol());
    assertThat(iterable.hasOwnProperty(foo)).isTrue();
  }

  @Test
  public void symbolPropertiesDoNotConflictWithStrings() {
    KnownSymbolType foo = new KnownSymbolType(registry, "Symbol.foo");
    ARRAY_TYPE.defineDeclaredProperty(new Property.SymbolKey(foo), STRING_TYPE, null);

    assertThat(ARRAY_TYPE.hasProperty("foo")).isFalse();
  }

  @Test
  public void structuralSubtypingAccountsForSymbolProps() {
    // /** @record */
    // class String {
    //    /** @type {string} */
    //   [Symbol.foo];
    // }
    FunctionType stringPropCtor =
        FunctionType.builder(registry).forInterface().setName("String").buildAndResolve();
    stringPropCtor.setImplicitMatch(true);
    Property.Key foo = new Property.SymbolKey(new KnownSymbolType(registry, "Symbol.foo"));
    stringPropCtor.getPrototype().defineDeclaredProperty(foo, STRING_TYPE, null);
    ObjectType stringProp = stringPropCtor.getInstanceType();
    // /** @record */
    // class StringOrNumber {
    //    /** @type {string|number} */
    //   [Symbol.foo];
    // }
    FunctionType stringOrNumberPropCtor =
        FunctionType.builder(registry).forInterface().setName("StringOrNumber").buildAndResolve();
    stringOrNumberPropCtor.setImplicitMatch(true);
    stringOrNumberPropCtor
        .getPrototype()
        .defineDeclaredProperty(foo, createUnionType(NUMBER_TYPE, STRING_TYPE), null);
    ObjectType stringOrNumberProp = stringOrNumberPropCtor.getInstanceType();

    // The subtyping chain is
    //  String < StringOrNumber < Object
    assertType(OBJECT_TYPE).isNotSubtypeOf(stringProp);
    assertType(OBJECT_TYPE).isNotSubtypeOf(stringOrNumberProp);

    assertType(stringOrNumberProp).isNotSubtypeOf(stringProp);
    assertType(stringOrNumberProp)
        .isSubtypeOf(stringOrNumberProp);
    assertType(stringOrNumberProp).isSubtypeOf(OBJECT_TYPE);

    assertType(stringProp).isSubtypeOf(stringOrNumberProp);
    assertType(stringProp).isSubtypeOf(stringProp);
    assertType(stringProp).isSubtypeOf(OBJECT_TYPE);
  }
}
