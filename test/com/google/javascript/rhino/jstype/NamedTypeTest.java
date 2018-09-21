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
 *   Nick Santos
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.javascript.rhino.testing.MapBasedScope.emptyScope;

import com.google.common.collect.ImmutableMap;
import com.google.common.testing.EqualsTester;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import com.google.javascript.rhino.testing.MapBasedScope;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** @author nicksantos@google.com (Nick Santos) */
@RunWith(JUnit4.class)
public class NamedTypeTest extends BaseJSTypeTestCase {

  private FunctionType fooCtorType; // The type of the constructor of "Foo".
  private ObjectType fooType; // A realized type with the canonical name "Foo".

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    fooCtorType =
        forceResolutionOf(new FunctionBuilder(registry).forConstructor().withName("Foo").build());
    fooType = forceResolutionOf(fooCtorType.getInstanceType());
  }

  @Test
  public void testResolutionPropagatesNamedTypePropertiesToResolvedType() {
    // Given
    StaticTypedScope fooToFooScope = new MapBasedScope(ImmutableMap.of("Foo", fooCtorType));
    NamedType namedFooType = new NamedTypeBuilder().setScope(fooToFooScope).setName("Foo").build();

    namedFooType.defineDeclaredProperty("myProperty", NUMBER_TYPE, null);

    // The property should not be typed yet.
    assertTypeNotEquals(NUMBER_TYPE, fooType.getPropertyType("myProperty"));

    // When
    namedFooType.resolve(ErrorReporter.NULL_INSTANCE);

    // Then
    assertTypeEquals(NUMBER_TYPE, fooType.getPropertyType("myProperty"));
  }

  @Test
  public void testStateOfForwardDeclaredType_Unresolved() {
    // Given
    NamedType type = new NamedTypeBuilder().setName(FORWARD_DECLARED_TYPE_NAME).build();

    // Then
    assertFalse(type.isResolved());
    assertFalse(type.isEmptyType());
    assertTypeNotEquals(UNKNOWN_TYPE, type);
    assertTypeEquals(UNKNOWN_TYPE, type.getReferencedType());
  }

  @Test
  public void testStateOfForwardDeclaredType_UnsuccesfullyResolved() {
    // Given
    NamedType type = new NamedTypeBuilder().setName(FORWARD_DECLARED_TYPE_NAME).build();

    // When
    type.resolve(ErrorReporter.NULL_INSTANCE);

    // Then
    assertTrue(type.isUnsuccessfullyResolved());
    assertFalse(type.isUnknownType());
  }

  @Test
  public void testStateOfForwardDeclaredType_SuccessfullyResolved() {
    // Given
    StaticTypedScope fooToFooScope = new MapBasedScope(ImmutableMap.of("Foo", fooCtorType));
    NamedType namedFooType = new NamedTypeBuilder().setScope(fooToFooScope).setName("Foo").build();

    // When
    namedFooType.resolve(ErrorReporter.NULL_INSTANCE);

    // Then
    assertTrue(namedFooType.isSuccessfullyResolved());
    assertTypeEquals(namedFooType, fooType);
  }

  @Test
  public void testEquality() {
    // Given
    ObjectType barType = createNominalType("Bar");
    FunctionType anonType = forceResolutionOf(new FunctionBuilder(registry).build());

    NamedTypeBuilder namedFooBuilder = new NamedTypeBuilder().setName("Foo");
    NamedType namedFooUnresolved = namedFooBuilder.build();
    NamedType namedFooUnsuccessfullyResolved =
        forceResolutionWith(NO_RESOLVED_TYPE, namedFooBuilder.build());
    NamedType namedFooResolvedToFoo = forceResolutionWith(fooType, namedFooBuilder.build());
    NamedType namedFooResolvedToAnon = forceResolutionWith(anonType, namedFooBuilder.build());
    NamedType namedFooResolvedToBar = forceResolutionWith(barType, namedFooBuilder.build());

    NamedTypeBuilder namedBarBuilder = new NamedTypeBuilder().setName("Bar");
    NamedType namedBarUnresolved = namedBarBuilder.build();
    NamedType namedBarUnsuccessfullyResolved =
        forceResolutionWith(NO_RESOLVED_TYPE, namedBarBuilder.build());
    NamedType namedBarResolvedToFoo = forceResolutionWith(fooType, namedBarBuilder.build());
    NamedType namedBarResolvedToAnon = forceResolutionWith(anonType, namedBarBuilder.build());
    NamedType namedBarResolvedToBar = forceResolutionWith(barType, namedBarBuilder.build());

    // Then
    new EqualsTester()
        .addEqualityGroup(fooType, namedFooUnresolved, namedFooResolvedToFoo, namedBarResolvedToFoo)
        .addEqualityGroup(barType, namedFooResolvedToBar, namedBarUnresolved, namedBarResolvedToBar)
        .addEqualityGroup(anonType, namedFooResolvedToAnon, namedBarResolvedToAnon)
        .addEqualityGroup(namedFooUnsuccessfullyResolved)
        .addEqualityGroup(namedBarUnsuccessfullyResolved)
        // TODO(b/112425334): Either re-enable this equality group or remove the NO_RESOLVED_TYPE
        // from the typesystem.
        // .addEqualityGroup(NO_RESOLVED_TYPE)
        .testEquals();
  }

  @Test
  public void testForwardDeclaredNamedType() {
    NamedType a = new NamedTypeBuilder().setName("Unresolvable").build();

    assertTypeEquals(UNKNOWN_TYPE, a.getLeastSupertype(UNKNOWN_TYPE));
    assertTypeEquals(CHECKED_UNKNOWN_TYPE, a.getLeastSupertype(CHECKED_UNKNOWN_TYPE));
    assertTypeEquals(UNKNOWN_TYPE, UNKNOWN_TYPE.getLeastSupertype(a));
    assertTypeEquals(CHECKED_UNKNOWN_TYPE, CHECKED_UNKNOWN_TYPE.getLeastSupertype(a));
  }

  @Test
  public void testActiveXObjectResolve() {
    MapBasedScope scope = new MapBasedScope(ImmutableMap.of("ActiveXObject", NO_OBJECT_TYPE));
    NamedType activeXObject =
        new NamedTypeBuilder().setScope(scope).setName("ActiveXObject").build();

    assertEquals("ActiveXObject", activeXObject.toString());
    activeXObject.resolve(null);
    assertEquals("NoObject", activeXObject.toString());
    assertTypeEquals(NO_OBJECT_TYPE, activeXObject.getReferencedType());
  }

  private static NamedType forceResolutionWith(JSType type, NamedType proxy) {
    proxy.setResolvedTypeInternal(type);
    proxy.setReferencedType(type);
    return proxy;
  }

  private static <T extends JSType> T forceResolutionOf(T type) {
    type.setResolvedTypeInternal(type);
    return type;
  }

  private ObjectType createNominalType(String name) {
    FunctionType ctorType =
        forceResolutionOf(new FunctionBuilder(registry).forConstructor().withName(name).build());
    return forceResolutionOf(ctorType.getInstanceType());
  }

  private class NamedTypeBuilder {
    private StaticTypedScope scope = emptyScope();
    @Nullable private String name;

    public NamedTypeBuilder setScope(StaticTypedScope scope) {
      this.scope = scope;
      return this;
    }

    public NamedTypeBuilder setName(String name) {
      this.name = name;
      return this;
    }

    public NamedType build() {
      checkNotNull(name, "NamedType requires a name");
      return new NamedType(scope, registry, name, "source", 1, 0);
    }
  }
}
