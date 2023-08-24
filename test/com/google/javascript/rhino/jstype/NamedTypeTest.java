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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.testing.MapBasedScope.emptyScope;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.common.testing.EqualsTester;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.jstype.NamedType.ResolutionKind;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import com.google.javascript.rhino.testing.MapBasedScope;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class NamedTypeTest extends BaseJSTypeTestCase {

  private FunctionType fooCtorType; // The type of the constructor of "Foo".
  private ObjectType fooType; // A realized type with the canonical name "Foo".

  @Before
  public void setUp() throws Exception {
    try (JSTypeResolver.Closer closer = this.registry.getResolver().openForDefinition()) {
      this.fooCtorType = FunctionType.builder(registry).forConstructor().withName("Foo").build();
      this.fooType = fooCtorType.getInstanceType();
    }
  }

  @Test
  public void testResolutionPropagatesNamedTypePropertiesToResolvedType() {
    // Given
    StaticTypedScope fooToFooScope = new MapBasedScope(ImmutableMap.of("Foo", fooCtorType));
    NamedType namedFooType;
    try (JSTypeResolver.Closer closer = this.registry.getResolver().openForDefinition()) {
      namedFooType = namedTypeBuilder("Foo").setScope(fooToFooScope).build();

      namedFooType.defineDeclaredProperty("myProperty", NUMBER_TYPE, null);

      // The property should not be typed yet.
      assertTypeNotEquals(NUMBER_TYPE, fooType.getPropertyType("myProperty"));
    }

    // Then
    assertTypeEquals(NUMBER_TYPE, fooType.getPropertyType("myProperty"));
  }

  @Test
  public void testStateOfForwardDeclaredType_Unresolved() {
    resetRegistryWithForwardDeclaredName("forwardDeclared");

    try (JSTypeResolver.Closer closer = this.registry.getResolver().openForDefinition()) {
      // Given
      NamedType type = namedTypeBuilder("forwardDeclared").build();

      // Then
      assertThat(type.isResolved()).isFalse();
      assertThat(type.isEmptyType()).isFalse();
      assertTypeNotEquals(UNKNOWN_TYPE, type);
      assertTypeEquals(UNKNOWN_TYPE, type.getReferencedType());
    }
  }

  @Test
  public void testStateOfForwardDeclaredType_UnsuccesfullyResolved() {
    resetRegistryWithForwardDeclaredName("forwardDeclared");

    // Given
    NamedType type = namedTypeBuilder("forwardDeclared").build();

    // Then
    assertThat(type.isUnsuccessfullyResolved()).isTrue();
    assertThat(type.isUnknownType()).isFalse();
  }

  @Test
  public void testStateOfForwardDeclaredType_SuccessfullyResolved() {
    // Given
    StaticTypedScope fooToFooScope = new MapBasedScope(ImmutableMap.of("Foo", fooCtorType));
    NamedType namedFooType = namedTypeBuilder("Foo").setScope(fooToFooScope).build();

    // When
    namedFooType.resolve(ErrorReporter.NULL_INSTANCE);

    // Then
    assertThat(namedFooType.isSuccessfullyResolved()).isTrue();
    assertTypeEquals(namedFooType, fooType);
  }

  @Test
  @SuppressWarnings({"MustBeClosedChecker"})
  public void testEquality() {
    // Given
    ObjectType barType = createNominalType("Bar"/* resolve= */ );
    FunctionType anonType = FunctionType.builder(registry).build();

    JSTypeResolver.Closer closer = this.registry.getResolver().openForDefinition();

    NamedType.Builder namedFooBuilder = namedTypeBuilder("Foo");
    NamedType namedFooUnsuccessfullyResolved =
        forceResolutionWith(new NoResolvedType(registry, "Foo", null), namedFooBuilder);
    NamedType namedFooResolvedToFoo = forceResolutionWith(fooType, namedFooBuilder);
    NamedType namedFooResolvedToAnon = forceResolutionWith(anonType, namedFooBuilder);
    NamedType namedFooResolvedToBar = forceResolutionWith(barType, namedFooBuilder);

    NamedType.Builder namedBarBuilder = namedTypeBuilder("Bar");
    NamedType namedBarUnsuccessfullyResolved =
        forceResolutionWith(new NoResolvedType(registry, "Bar", null), namedBarBuilder);
    NamedType namedBarResolvedToFoo = forceResolutionWith(fooType, namedBarBuilder);
    NamedType namedBarResolvedToAnon = forceResolutionWith(anonType, namedBarBuilder);
    NamedType namedBarResolvedToBar = forceResolutionWith(barType, namedBarBuilder);

    closer.close();

    // Then
    new EqualsTester()
        .addEqualityGroup(fooType, namedFooResolvedToFoo, namedBarResolvedToFoo)
        .addEqualityGroup(barType, namedFooResolvedToBar, namedBarResolvedToBar)
        .addEqualityGroup(anonType, namedFooResolvedToAnon, namedBarResolvedToAnon)
        .addEqualityGroup(namedFooUnsuccessfullyResolved)
        .addEqualityGroup(namedBarUnsuccessfullyResolved)
        // TODO(b/112425334): Either re-enable this equality group or remove the NO_RESOLVED_TYPE
        // from the typesystem.
        // .addEqualityGroup(NO_RESOLVED_TYPE)
        .testEquals();
  }

  @Test
  @SuppressWarnings({"MustBeClosedChecker"})
  public void testEqualityOfTypesWithSameReferenceName_postResolution() {
    // Given
    ObjectType barTypeA = createNominalType("Bar");
    ObjectType barTypeB = createNominalType("Bar");
    FunctionType anonType = FunctionType.builder(registry).build();

    JSTypeResolver.Closer closer = this.registry.getResolver().openForDefinition();

    NamedType.Builder namedFooBuilder = NamedType.builder(registry, "Foo");
    NamedType namedFooUnsuccessfullyResolved =
        forceResolutionWith(new NoResolvedType(registry, "Foo", null), namedFooBuilder);
    NamedType namedFooResolvedToFoo = forceResolutionWith(fooType, namedFooBuilder);
    NamedType namedFooResolvedToAnon = forceResolutionWith(anonType, namedFooBuilder);
    NamedType namedFooResolvedToBarA = forceResolutionWith(barTypeA, namedFooBuilder);
    NamedType namedFooResolvedToBarB = forceResolutionWith(barTypeB, namedFooBuilder);
    NamedType namedFooResolvedToNumber = forceResolutionWith(NUMBER_TYPE, namedFooBuilder);
    NamedType namedFooResolvedToString = forceResolutionWith(STRING_TYPE, namedFooBuilder);

    NamedType.Builder namedBarBuilder = NamedType.builder(registry, "Bar");
    NamedType namedBarUnsuccessfullyResolved =
        forceResolutionWith(new NoResolvedType(registry, "Bar", null), namedBarBuilder);
    NamedType namedBarResolvedToFoo = forceResolutionWith(fooType, namedBarBuilder);
    NamedType namedBarResolvedToAnon = forceResolutionWith(anonType, namedBarBuilder);
    NamedType namedBarResolvedToBarA = forceResolutionWith(barTypeA, namedBarBuilder);
    NamedType namedBarResolvedToBarB = forceResolutionWith(barTypeB, namedBarBuilder);
    NamedType namedBarResolvedToNumber = forceResolutionWith(NUMBER_TYPE, namedBarBuilder);
    NamedType namedBarResolvedToString = forceResolutionWith(STRING_TYPE, namedBarBuilder);

    closer.close();

    // Then
    new EqualsTester()
        .addEqualityGroup(fooType, namedFooResolvedToFoo, namedBarResolvedToFoo)
        .addEqualityGroup(barTypeA, namedFooResolvedToBarA, namedBarResolvedToBarA)
        .addEqualityGroup(barTypeB, namedFooResolvedToBarB, namedBarResolvedToBarB)
        .addEqualityGroup(anonType, namedFooResolvedToAnon, namedBarResolvedToAnon)
        .addEqualityGroup(NUMBER_TYPE, namedFooResolvedToNumber, namedBarResolvedToNumber)
        .addEqualityGroup(STRING_TYPE, namedFooResolvedToString, namedBarResolvedToString)
        .addEqualityGroup(namedFooUnsuccessfullyResolved)
        .addEqualityGroup(namedBarUnsuccessfullyResolved)
        // TODO(b/112425334): Either re-enable this equality group or remove the NO_RESOLVED_TYPE
        // from the typesystem.
        // .addEqualityGroup(NO_RESOLVED_TYPE)
        .testEquals();
  }

  @Test
  public void testForwardDeclaredNamedType() {
    this.errorReporter.expectAllWarnings("Bad type annotation. Unknown type Unresolvable");
    NamedType a = namedTypeBuilder("Unresolvable").build();

    assertTypeEquals(UNKNOWN_TYPE, a.getLeastSupertype(UNKNOWN_TYPE));
    assertTypeEquals(CHECKED_UNKNOWN_TYPE, a.getLeastSupertype(CHECKED_UNKNOWN_TYPE));
    assertTypeEquals(UNKNOWN_TYPE, UNKNOWN_TYPE.getLeastSupertype(a));
    assertTypeEquals(CHECKED_UNKNOWN_TYPE, CHECKED_UNKNOWN_TYPE.getLeastSupertype(a));
  }

  @Test
  public void testActiveXObjectResolve() {
    NamedType activeXObject;
    try (JSTypeResolver.Closer closer = this.registry.getResolver().openForDefinition()) {
      MapBasedScope scope = new MapBasedScope(ImmutableMap.of("ActiveXObject", NO_OBJECT_TYPE));
      activeXObject = namedTypeBuilder("ActiveXObject").setScope(scope).build();

      assertThat(activeXObject.toString()).isEqualTo("ActiveXObject");
    }
    assertThat(activeXObject.toString()).isEqualTo("NoObject");
    assertTypeEquals(NO_OBJECT_TYPE, activeXObject.getReferencedType());
  }

  @Test
  public void testResolveToGoogModule() {
    registry.registerNonLegacyClosureNamespace("mod.Foo", null, fooCtorType);
    NamedType modDotFoo = namedTypeBuilder("mod.Foo").build();

    modDotFoo.resolve(ErrorReporter.NULL_INSTANCE);
    assertTypeEquals(fooType, modDotFoo.getReferencedType());
  }

  @Test
  public void testResolveToPropertyOnGoogModule() {
    JSType exportsType = registry.createRecordType(ImmutableMap.of("Foo", fooCtorType));
    registry.registerNonLegacyClosureNamespace("mod.Bar", null, exportsType);
    NamedType modBarFoo = namedTypeBuilder("mod.Bar.Foo").build();

    modBarFoo.resolve(ErrorReporter.NULL_INSTANCE);
    assertTypeEquals(fooType, modBarFoo.getReferencedType());
  }

  @Test
  public void testResolveToGoogModule_failsIfLegacyNamespaceIsLongerPrefix() {
    errorReporter.expectAllWarnings("Bad type annotation. Unknown type mod.Bar.Foo");
    NamedType modDotFoo;
    try (JSTypeResolver.Closer closer = this.registry.getResolver().openForDefinition()) {
      JSType exportsType = registry.createRecordType(ImmutableMap.of("Foo", fooCtorType));
      registry.registerNonLegacyClosureNamespace("mod.Bar", null, exportsType);
      modDotFoo = namedTypeBuilder("mod.Bar.Foo").build();
      registry.registerLegacyClosureNamespace("mod.Bar.Foo");
    }

    assertType(modDotFoo.getReferencedType()).isUnknown();
  }

  @Test
  public void testResolveToGoogModule_usesLongestModulePrefix() {
    ObjectType barType = createNominalType("Bar"/* resolve= */ );
    registry.registerNonLegacyClosureNamespace(
        "mod.Foo", null, registry.createRecordType(ImmutableMap.of("Bar", fooCtorType)));
    registry.registerNonLegacyClosureNamespace("mod.Foo.Bar", null, barType.getConstructor());
    MapBasedScope scope = new MapBasedScope(ImmutableMap.of());
    NamedType modDotFoo = namedTypeBuilder("mod.Foo.Bar").setScope(scope).build();

    modDotFoo.resolve(ErrorReporter.NULL_INSTANCE);
    assertTypeEquals(barType, modDotFoo.getReferencedType());
  }

  @Test
  public void testReferenceGoogModuleByType_resolvesToModuleRatherThanRegistryType() {
    // Note: this logic was put in place to mimic the semantics of type resolution when
    // modules are rewritten before typechecking.
    registry.registerNonLegacyClosureNamespace("mod.Foo", null, fooCtorType);
    MapBasedScope scope = new MapBasedScope(ImmutableMap.of());
    NamedType modDotFoo = namedTypeBuilder("mod.Foo").setScope(scope).build();
    registry.declareType(scope, "mod.Foo", createNominalType("other.mod.Foo"));

    assertTypeEquals(fooType, modDotFoo.getReferencedType());
  }

  @Test
  public void testGetBangType_onUnresolvedType() {
    JSType barNonNull;
    try (JSTypeResolver.Closer closer = registry.getResolver().openForDefinition()) {
      NamedType barType = namedTypeBuilder("Bar").build();

      barNonNull = barType.getBangType();
      assertThat(barNonNull.isResolved()).isFalse();

      registry.declareType(null, "Bar", registry.createUnionType(fooType, NULL_TYPE));
    }

    assertType(barNonNull).toStringIsEqualTo("Foo");
  }

  @Test
  public void testGetBangType_onResolvedType() {
    NamedType barType;
    try (JSTypeResolver.Closer closer = registry.getResolver().openForDefinition()) {
      barType = namedTypeBuilder("Bar").build();
      registry.declareType(null, "Bar", registry.createUnionType(fooType, NULL_TYPE));
    }

    JSType barNonNull = barType.getBangType();
    assertType(barNonNull).toStringIsEqualTo("Foo");
  }

  @Test
  public void testBuilderForTypeof_emitsUnrecognizedTypeError() {
    NamedType.Builder typeofFooBuilder = NamedType.builder(registry, "typeof Foo")
        .setScope(emptyScope())
        .setResolutionKind(ResolutionKind.TYPEOF);

    assertType(typeofFooBuilder.build()).isUnknown();
    errorReporter.expectAllWarnings(
        "Missing type for `typeof` value. The value must be declared and const.");
  }

  @Test
  public void testBuilderForTypeof_yieldsNoResolvedTypeWithForwardDeclaredName() {
    resetRegistryWithForwardDeclaredName("Foo");

    NamedType.Builder typeofFooBuilder = NamedType
        .builder(registry, "typeof Foo")
        .setScope(emptyScope())
        .setResolutionKind(ResolutionKind.TYPEOF);

    assertType(typeofFooBuilder.build()).isNoResolvedType("typeof Foo");
  }

  @Test
  public void testBuilderForTypeof_requiresReferenceToStartWithTypeof() {
    NamedType.Builder typeofFooBuilder = NamedType.builder(registry, "Foo")
        .setScope(emptyScope())
        .setResolutionKind(ResolutionKind.TYPEOF);

    assertThrows(Exception.class, typeofFooBuilder::build);
  }

  private static NamedType forceResolutionWith(JSType type, NamedType.Builder proxy) {
    return proxy.setReferencedType(type).setResolutionKind(ResolutionKind.NONE).build();
  }

  private ObjectType createNominalType(String name) {
    try (JSTypeResolver.Closer closer = registry.getResolver().openForDefinition()) {
      FunctionType ctorType =
          FunctionType.builder(registry).forConstructor().withName(name).build();
      return ctorType.getInstanceType();
    }
  }

  private NamedType.Builder namedTypeBuilder(String name) {
    return NamedType.builder(registry, name)
        .setResolutionKind(ResolutionKind.TYPE_NAME)
        .setScope(emptyScope())
        .setErrorReportingLocation("source", 1, 0);
  }
}
