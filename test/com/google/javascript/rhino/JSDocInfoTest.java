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

package com.google.javascript.rhino;

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.JSDocInfo.Visibility.PACKAGE;
import static com.google.javascript.rhino.JSDocInfo.Visibility.PRIVATE;
import static com.google.javascript.rhino.JSDocInfo.Visibility.PROTECTED;
import static com.google.javascript.rhino.JSDocInfo.Visibility.PUBLIC;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.testing.TestErrorReporter;
import java.util.Collection;
import junit.framework.TestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class JSDocInfoTest extends TestCase {
  private final TestErrorReporter errorReporter = new TestErrorReporter(null, null);
  private final JSTypeRegistry registry = new JSTypeRegistry(errorReporter);

  private JSType getNativeType(JSTypeNative typeId) {
    return registry.getNativeType(typeId);
  }

  /** Tests the assigned ordinal of the elements of the {@link JSDocInfo.Visibility} enum. */
  @Test
  public void testVisibilityOrdinal() {
    assertEquals(0, PRIVATE.ordinal());
    assertEquals(1, PACKAGE.ordinal());
    assertEquals(2, PROTECTED.ordinal());
    assertEquals(3, PUBLIC.ordinal());
  }

  @Test
  public void testSetType() {
    JSDocInfo info = new JSDocInfo();
    info.setType(fromString("string"));

    assertNull(info.getBaseType());
    assertNull(info.getDescription());
    assertNull(info.getEnumParameterType());
    assertEquals(0, info.getParameterCount());
    assertNull(info.getReturnType());
    assertType(resolve(info.getType())).isStructurallyEqualTo(getNativeType(STRING_TYPE));
    assertNull(info.getVisibility());
    assertTrue(info.hasType());
    assertFalse(info.isConstant());
    assertFalse(info.isConstructor());
    assertFalse(info.isHidden());
  }

  @Test
  public void testSetTypeAndVisibility() {
    JSDocInfo info = new JSDocInfo();
    info.setType(fromString("string"));
    info.setVisibility(PROTECTED);

    assertNull(info.getBaseType());
    assertNull(info.getDescription());
    assertNull(info.getEnumParameterType());
    assertEquals(0, info.getParameterCount());
    assertNull(info.getReturnType());
    assertType(resolve(info.getType())).isStructurallyEqualTo(getNativeType(STRING_TYPE));
    assertEquals(PROTECTED, info.getVisibility());
    assertTrue(info.hasType());
    assertFalse(info.isConstant());
    assertFalse(info.isConstructor());
    assertFalse(info.isHidden());
  }

  @Test
  public void testSetReturnType() {
    JSDocInfo info = new JSDocInfo();
    info.setReturnType(fromString("string"));

    assertNull(info.getBaseType());
    assertNull(info.getDescription());
    assertNull(info.getEnumParameterType());
    assertEquals(0, info.getParameterCount());
    assertType(resolve(info.getReturnType())).isStructurallyEqualTo(getNativeType(STRING_TYPE));
    assertNull(info.getType());
    assertNull(info.getVisibility());
    assertFalse(info.hasType());
    assertFalse(info.isConstant());
    assertFalse(info.isConstructor());
    assertFalse(info.isHidden());
  }

  @Test
  public void testSetReturnTypeAndBaseType() {
    JSDocInfo info = new JSDocInfo();
    info.setBaseType(
        new JSTypeExpression(
            new Node(Token.BANG, Node.newString("Number")), ""));
    info.setReturnType(fromString("string"));

    assertType(resolve(info.getBaseType()))
        .isStructurallyEqualTo(getNativeType(NUMBER_OBJECT_TYPE));
    assertNull(info.getDescription());
    assertNull(info.getEnumParameterType());
    assertEquals(0, info.getParameterCount());
    assertType(resolve(info.getReturnType())).isStructurallyEqualTo(getNativeType(STRING_TYPE));
    assertNull(info.getType());
    assertNull(info.getVisibility());
    assertFalse(info.hasType());
    assertFalse(info.isConstant());
    assertFalse(info.isConstructor());
    assertFalse(info.isHidden());
  }

  @Test
  public void testSetEnumParameterType() {
    JSDocInfo info = new JSDocInfo();
    info.setEnumParameterType(fromString("string"));

    assertNull(info.getBaseType());
    assertNull(info.getDescription());
    assertType(resolve(info.getEnumParameterType()))
        .isStructurallyEqualTo(getNativeType(STRING_TYPE));
    assertEquals(0, info.getParameterCount());
    assertNull(info.getReturnType());
    assertNull(info.getType());
    assertNull(info.getVisibility());
    assertFalse(info.hasType());
    assertFalse(info.isConstant());
    assertFalse(info.isConstructor());
    assertFalse(info.isHidden());
  }

  @Test
  public void testMultipleSetType() {
    JSDocInfo info = new JSDocInfo();
    info.setType(fromString("number"));

    try {
      info.setReturnType(fromString("boolean"));
      fail("Expected exception");
    } catch (IllegalStateException e) {
      // expected
    }

    try {
      info.setEnumParameterType(fromString("string"));
      fail("Expected exception");
    } catch (IllegalStateException e) {
      // expected
    }

    try {
      info.declareTypedefType(fromString("string"));
      fail("Expected exception");
    } catch (IllegalStateException e) {
      // expected
    }

    assertType(resolve(info.getType())).isStructurallyEqualTo(getNativeType(NUMBER_TYPE));
    assertNull(info.getReturnType());
    assertNull(info.getEnumParameterType());
    assertNull(info.getTypedefType());
    assertTrue(info.hasType());
  }

  @Test
  public void testMultipleSetType2() {
    JSDocInfo info = new JSDocInfo();

    info.setReturnType(fromString("boolean"));

    try {
      info.setType(fromString("number"));
      fail("Expected exception");
    } catch (IllegalStateException e) {
      // expected
    }

    try {
      info.setEnumParameterType(fromString("string"));
      fail("Expected exception");
    } catch (IllegalStateException e) {
      // expected
    }

    try {
      info.declareTypedefType(fromString("string"));
      fail("Expected exception");
    } catch (IllegalStateException e) {
      // expected
    }

    assertType(resolve(info.getReturnType())).isStructurallyEqualTo(getNativeType(BOOLEAN_TYPE));
    assertNull(info.getEnumParameterType());
    assertNull(info.getType());
    assertNull(info.getTypedefType());
    assertFalse(info.hasType());
  }

  @Test
  public void testMultipleSetType3() {
    JSDocInfo info = new JSDocInfo();
    info.setEnumParameterType(fromString("boolean"));

    try {
      info.setType(fromString("number"));
      fail("Expected exception");
    } catch (IllegalStateException e) {
      // expected
    }

    try {
      info.setReturnType(fromString("string"));
      fail("Expected exception");
    } catch (IllegalStateException e) {
      // expected
    }

    try {
      info.declareTypedefType(fromString("string"));
      fail("Expected exception");
    } catch (IllegalStateException e) {
      // expected
    }

    assertNull(info.getType());
    assertNull(info.getTypedefType());
    assertNull(info.getReturnType());
    assertType(resolve(info.getEnumParameterType()))
        .isStructurallyEqualTo(getNativeType(BOOLEAN_TYPE));
  }

  @Test
  public void testSetTypedefType() {
    JSDocInfo info = new JSDocInfo();
    info.declareTypedefType(fromString("boolean"));

    assertType(resolve(info.getTypedefType())).isStructurallyEqualTo(getNativeType(BOOLEAN_TYPE));
    assertTrue(info.hasTypedefType());
    assertFalse(info.hasType());
    assertFalse(info.hasEnumParameterType());
    assertFalse(info.hasReturnType());
  }

  @Test
  public void testSetConstant() {
    JSDocInfo info = new JSDocInfo();
    info.setConstant(true);

    assertFalse(info.hasType());
    assertTrue(info.isConstant());
    assertFalse(info.isConstructor());
    assertFalse(info.isDefine());
    assertFalse(info.isHidden());
  }

  @Test
  public void testSetConstructor() {
    JSDocInfo info = new JSDocInfo();
    info.setConstructor(true);

    assertFalse(info.isConstant());
    assertTrue(info.isConstructor());
    assertFalse(info.isDefine());
    assertFalse(info.isHidden());
  }

  @Test
  public void testSetDefine() {
    JSDocInfo info = new JSDocInfo();
    info.setDefine(true);

    assertTrue(info.isConstant());
    assertFalse(info.isConstructor());
    assertTrue(info.isDefine());
    assertFalse(info.isHidden());
  }

  @Test
  public void testSetHidden() {
    JSDocInfo info = new JSDocInfo();
    info.setHidden(true);

    assertFalse(info.hasType());
    assertFalse(info.isConstant());
    assertFalse(info.isConstructor());
    assertFalse(info.isDefine());
    assertTrue(info.isHidden());
  }

  @Test
  public void testSetOverride() {
    JSDocInfo info = new JSDocInfo();
    info.setOverride(true);

    assertFalse(info.isDeprecated());
    assertTrue(info.isOverride());
  }

  @Test
  public void testSetExport() {
    JSDocInfo info = new JSDocInfo();
    info.setExport(true);

    assertTrue(info.isExport());
  }

  @Test
  public void testSetPolymerBehavior() {
    JSDocInfo info = new JSDocInfo();
    assertFalse(info.isPolymerBehavior());
    info.setPolymerBehavior(true);

    assertTrue(info.isPolymerBehavior());
  }

  @Test
  public void testSetPolymer() {
    JSDocInfo info = new JSDocInfo();
    assertFalse(info.isPolymer());
    info.setPolymer(true);

    assertTrue(info.isPolymer());
  }

  @Test
  public void testSetCustomElement() {
    JSDocInfo info = new JSDocInfo();
    assertFalse(info.isCustomElement());
    info.setCustomElement(true);

    assertTrue(info.isCustomElement());
  }

  @Test
  public void testSetMixinClass() {
    JSDocInfo info = new JSDocInfo();
    assertFalse(info.isMixinClass());
    info.setMixinClass(true);

    assertTrue(info.isMixinClass());
  }

  @Test
  public void testSetMixinFunction() {
    JSDocInfo info = new JSDocInfo();
    assertFalse(info.isMixinFunction());
    info.setMixinFunction(true);

    assertTrue(info.isMixinFunction());
  }

  @Test
  public void testSetNoAlias() {
    JSDocInfo info = new JSDocInfo();

    assertFalse(info.isDeprecated());
    assertFalse(info.isOverride());
  }

  @Test
  public void testSetDeprecated() {
    JSDocInfo info = new JSDocInfo();
    info.setDeprecated(true);

    assertFalse(info.isOverride());
    assertTrue(info.isDeprecated());
  }

  @Test
  public void testMultipleSetFlags1() {
    JSDocInfo info = new JSDocInfo();
    info.setConstant(true);
    info.setConstructor(true);
    info.setHidden(true);

    assertFalse(info.hasType());
    assertTrue(info.isConstant());
    assertTrue(info.isConstructor());
    assertFalse(info.isDefine());
    assertTrue(info.isHidden());

    info.setHidden(false);

    assertTrue(info.isConstant());
    assertTrue(info.isConstructor());
    assertFalse(info.isDefine());
    assertFalse(info.isHidden());

    info.setConstant(false);
    info.setConstructor(false);

    assertFalse(info.isConstant());
    assertFalse(info.isConstructor());
    assertFalse(info.isDefine());
    assertFalse(info.isHidden());

    info.setConstructor(true);

    assertFalse(info.isConstant());
    assertTrue(info.isConstructor());
    assertFalse(info.isDefine());
    assertFalse(info.isHidden());
  }

  @Test
  public void testDescriptionContainsAtSignCode() {
    JSDocInfo info = new JSDocInfo(true);
    info.setOriginalCommentString("Blah blah {@code blah blah} blah blah.");

    assertTrue(info.isAtSignCodePresent());
  }

  @Test
  public void testDescriptionDoesNotContainAtSignCode() {
    JSDocInfo info = new JSDocInfo(true);
    info.setOriginalCommentString("Blah blah `blah blah` blah blah.");

    assertFalse(info.isAtSignCodePresent());
  }

  @Test
  public void testClone() {
    JSDocInfo info = new JSDocInfo();
    info.setDescription("The source info");
    info.setConstant(true);
    info.setConstructor(true);
    info.setHidden(true);
    info.setBaseType(
        new JSTypeExpression(
            new Node(Token.BANG, Node.newString("Number")), ""));
    info.setReturnType(fromString("string"));

    JSDocInfo cloned = info.clone();

    assertType(resolve(cloned.getBaseType()))
        .isStructurallyEqualTo(getNativeType(NUMBER_OBJECT_TYPE));
    assertEquals("The source info", cloned.getDescription());
    assertType(resolve(cloned.getReturnType())).isStructurallyEqualTo(getNativeType(STRING_TYPE));
    assertTrue(cloned.isConstant());
    assertTrue(cloned.isConstructor());
    assertTrue(cloned.isHidden());

    cloned.setDescription("The cloned info");
    cloned.setHidden(false);
    cloned.setBaseType(fromString("string"));

    assertType(resolve(cloned.getBaseType())).isStructurallyEqualTo(getNativeType(STRING_TYPE));
    assertEquals("The cloned info", cloned.getDescription());
    assertFalse(cloned.isHidden());

    // Original info should be unchanged.
    assertType(resolve(info.getBaseType()))
        .isStructurallyEqualTo(getNativeType(NUMBER_OBJECT_TYPE));
    assertEquals("The source info", info.getDescription());
    assertType(resolve(info.getReturnType())).isStructurallyEqualTo(getNativeType(STRING_TYPE));
    assertTrue(info.isConstant());
    assertTrue(info.isConstructor());
    assertTrue(info.isHidden());
  }

  @Test
  public void testCloneTypeExpressions1() {
    JSDocInfo info = new JSDocInfo();
    info.setDescription("The source info");
    info.setConstant(true);
    info.setConstructor(true);
    info.setHidden(true);
    info.setBaseType(
        new JSTypeExpression(
            new Node(Token.BANG, Node.newString("Number")), ""));
    info.setReturnType(fromString("string"));
    info.declareParam(fromString("string"), "a");

    JSDocInfo cloned = info.clone(true);

    assertNotSame(info.getBaseType().getRoot(), cloned.getBaseType().getRoot());
    assertType(resolve(cloned.getBaseType()))
        .isStructurallyEqualTo(getNativeType(NUMBER_OBJECT_TYPE));
    assertEquals("The source info", cloned.getDescription());
    assertNotSame(info.getReturnType().getRoot(), cloned.getReturnType().getRoot());
    assertType(resolve(cloned.getReturnType())).isStructurallyEqualTo(getNativeType(STRING_TYPE));
    assertNotSame(info.getParameterType("a").getRoot(), cloned.getParameterType("a").getRoot());
    assertType(resolve(cloned.getParameterType("a")))
        .isStructurallyEqualTo(getNativeType(STRING_TYPE));
  }

  @Test
  public void testCloneTypeExpressions2() {
    JSDocInfo info = new JSDocInfo();
    info.declareParam(null, "a");
    JSDocInfo cloned = info.clone(true);

    assertNull(cloned.getParameterType("a"));
  }

  @Test
  public void testSetFileOverviewWithDocumentationOff() {
    JSDocInfo info = new JSDocInfo();
    info.documentFileOverview("hi bob");
    assertNull(info.getFileOverview());
  }

  @Test
  public void testSetFileOverviewWithDocumentationOn() {
    JSDocInfo info = new JSDocInfo(true);
    info.documentFileOverview("hi bob");
    assertEquals("hi bob", info.getFileOverview());
  }

  @Test
  public void testSetSuppressions() {
    JSDocInfo info = new JSDocInfo(true);
    info.addSuppressions(ImmutableSet.of("sam", "bob"));
    assertEquals(ImmutableSet.of("bob", "sam"), info.getSuppressions());
  }

  @Test
  public void testSetModifies() {
    JSDocInfo info = new JSDocInfo(true);
    info.setModifies(ImmutableSet.of("this"));
    assertEquals(ImmutableSet.of("this"), info.getModifies());

    info = new JSDocInfo(true);
    info.setModifies(ImmutableSet.of("arguments"));
    assertEquals(ImmutableSet.of("arguments"), info.getModifies());
  }

  @Test
  public void testAddSingleTemplateTypeName() {
    JSDocInfo info = new JSDocInfo(true);
    ImmutableList<String> typeNames = ImmutableList.of("T");
    assertTrue(info.declareTemplateTypeName("T"));
    assertEquals(typeNames, info.getTemplateTypeNames());
  }

  @Test
  public void testAddMultipleTemplateTypeName() {
    JSDocInfo info = new JSDocInfo(true);
    ImmutableList<String> typeNames = ImmutableList.of("T", "R");
    info.declareTemplateTypeName("T");
    info.declareTemplateTypeName("R");
    assertEquals(typeNames, info.getTemplateTypeNames());
  }

  @Test
  public void testFailToAddTemplateTypeName() {
    JSDocInfo info = new JSDocInfo(true);
    info.declareTemplateTypeName("T");
    assertFalse(info.declareTemplateTypeName("T"));
  }

  @Test
  public void testGetThrowsDescription() {
    JSDocInfo info = new JSDocInfo(true);

    // Set a description so that info is initialized.
    info.setDescription("Lorem");

    JSTypeExpression errorType = fromString("Error");
    JSTypeExpression otherType = fromString("Other");
    info.documentThrows(errorType, "Because it does.");
    info.documentThrows(otherType, "");
    assertEquals("Because it does.", info.getThrowsDescriptionForType(errorType));
    assertEquals("", info.getThrowsDescriptionForType(otherType));
    assertNull(info.getThrowsDescriptionForType(fromString("NeverSeen")));
  }

  // https://github.com/google/closure-compiler/issues/2328
  @Test
  public void testIssue2328() {
    JSDocInfo info = new JSDocInfo();

    // should be added to implemented interfaces
    assertTrue("", info.addImplementedInterface(null));

    Collection<Node> nodes = info.getTypeNodes();
    assertThat(nodes).isEmpty();
  }

  /** Gets the type expression for a simple type name. */
  private JSTypeExpression fromString(String s) {
    return new JSTypeExpression(Node.newString(s), "");
  }

  private JSType resolve(JSTypeExpression n, String... warnings) {
    errorReporter.setWarnings(warnings);
    return n.evaluate(null, registry);
  }
}
