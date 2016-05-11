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

import static com.google.javascript.rhino.JSDocInfo.Visibility.PACKAGE;
import static com.google.javascript.rhino.JSDocInfo.Visibility.PRIVATE;
import static com.google.javascript.rhino.JSDocInfo.Visibility.PROTECTED;
import static com.google.javascript.rhino.JSDocInfo.Visibility.PUBLIC;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.testing.Asserts;
import com.google.javascript.rhino.testing.TestErrorReporter;

import junit.framework.TestCase;

public class JSDocInfoTest extends TestCase {
  private final TestErrorReporter errorReporter = new TestErrorReporter(null, null);
  private final JSTypeRegistry registry = new JSTypeRegistry(errorReporter);

  private JSType getNativeType(JSTypeNative typeId) {
    return registry.getNativeType(typeId);
  }

  /**
   * Tests the assigned ordinal of the elements of the
   * {@link JSDocInfo.Visibility} enum.
   */
  public void testVisibilityOrdinal() {
    assertEquals(0, PRIVATE.ordinal());
    assertEquals(1, PACKAGE.ordinal());
    assertEquals(2, PROTECTED.ordinal());
    assertEquals(3, PUBLIC.ordinal());
  }

  public void testSetType() {
    JSDocInfo info = new JSDocInfo();
    info.setType(fromString("string"));

    assertNull(info.getBaseType());
    assertNull(info.getDescription());
    assertNull(info.getEnumParameterType());
    assertEquals(0, info.getParameterCount());
    assertNull(info.getReturnType());
    assertTypeEquals(STRING_TYPE, resolve(info.getType()));
    assertNull(info.getVisibility());
    assertTrue(info.hasType());
    assertFalse(info.isConstant());
    assertFalse(info.isConstructor());
    assertFalse(info.isHidden());
    assertFalse(info.shouldPreserveTry());
  }

  public void testSetTypeAndVisibility() {
    JSDocInfo info = new JSDocInfo();
    info.setType(fromString("string"));
    info.setVisibility(PROTECTED);

    assertNull(info.getBaseType());
    assertNull(info.getDescription());
    assertNull(info.getEnumParameterType());
    assertEquals(0, info.getParameterCount());
    assertNull(info.getReturnType());
    assertTypeEquals(STRING_TYPE, resolve(info.getType()));
    assertEquals(PROTECTED, info.getVisibility());
    assertTrue(info.hasType());
    assertFalse(info.isConstant());
    assertFalse(info.isConstructor());
    assertFalse(info.isHidden());
    assertFalse(info.shouldPreserveTry());
  }

  public void testSetReturnType() {
    JSDocInfo info = new JSDocInfo();
    info.setReturnType(fromString("string"));

    assertNull(info.getBaseType());
    assertNull(info.getDescription());
    assertNull(info.getEnumParameterType());
    assertEquals(0, info.getParameterCount());
    assertTypeEquals(STRING_TYPE, resolve(info.getReturnType()));
    assertNull(info.getType());
    assertNull(info.getVisibility());
    assertFalse(info.hasType());
    assertFalse(info.isConstant());
    assertFalse(info.isConstructor());
    assertFalse(info.isHidden());
    assertFalse(info.shouldPreserveTry());
  }

  public void testSetReturnTypeAndBaseType() {
    JSDocInfo info = new JSDocInfo();
    info.setBaseType(
        new JSTypeExpression(
            new Node(Token.BANG, Node.newString("Number")), ""));
    info.setReturnType(fromString("string"));

    assertTypeEquals(NUMBER_OBJECT_TYPE,
        resolve(info.getBaseType()));
    assertNull(info.getDescription());
    assertNull(info.getEnumParameterType());
    assertEquals(0, info.getParameterCount());
    assertTypeEquals(STRING_TYPE, resolve(info.getReturnType()));
    assertNull(info.getType());
    assertNull(info.getVisibility());
    assertFalse(info.hasType());
    assertFalse(info.isConstant());
    assertFalse(info.isConstructor());
    assertFalse(info.isHidden());
    assertFalse(info.shouldPreserveTry());
  }

  public void testSetEnumParameterType() {
    JSDocInfo info = new JSDocInfo();
    info.setEnumParameterType(fromString("string"));

    assertNull(info.getBaseType());
    assertNull(info.getDescription());
    assertTypeEquals(STRING_TYPE,
        resolve(info.getEnumParameterType()));
    assertEquals(0, info.getParameterCount());
    assertNull(info.getReturnType());
    assertNull(info.getType());
    assertNull(info.getVisibility());
    assertFalse(info.hasType());
    assertFalse(info.isConstant());
    assertFalse(info.isConstructor());
    assertFalse(info.isHidden());
    assertFalse(info.shouldPreserveTry());
  }

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

    assertTypeEquals(NUMBER_TYPE, resolve(info.getType()));
    assertNull(info.getReturnType());
    assertNull(info.getEnumParameterType());
    assertNull(info.getTypedefType());
    assertTrue(info.hasType());
  }

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

    assertTypeEquals(BOOLEAN_TYPE,
        resolve(info.getReturnType()));
    assertNull(info.getEnumParameterType());
    assertNull(info.getType());
    assertNull(info.getTypedefType());
    assertFalse(info.hasType());
  }

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
    assertTypeEquals(BOOLEAN_TYPE,
        resolve(info.getEnumParameterType()));
  }

  public void testSetTypedefType() {
    JSDocInfo info = new JSDocInfo();
    info.declareTypedefType(fromString("boolean"));

    assertTypeEquals(BOOLEAN_TYPE,
        resolve(info.getTypedefType()));
    assertTrue(info.hasTypedefType());
    assertFalse(info.hasType());
    assertFalse(info.hasEnumParameterType());
    assertFalse(info.hasReturnType());
  }

  public void testSetConstant() {
    JSDocInfo info = new JSDocInfo();
    info.setConstant(true);

    assertFalse(info.hasType());
    assertTrue(info.isConstant());
    assertFalse(info.isConstructor());
    assertFalse(info.isDefine());
    assertFalse(info.isHidden());
    assertFalse(info.shouldPreserveTry());
  }

  public void testSetConstructor() {
    JSDocInfo info = new JSDocInfo();
    info.setConstructor(true);

    assertFalse(info.isConstant());
    assertTrue(info.isConstructor());
    assertFalse(info.isDefine());
    assertFalse(info.isHidden());
    assertFalse(info.shouldPreserveTry());
  }

  public void testSetDefine() {
    JSDocInfo info = new JSDocInfo();
    info.setDefine(true);

    assertTrue(info.isConstant());
    assertFalse(info.isConstructor());
    assertTrue(info.isDefine());
    assertFalse(info.isHidden());
    assertFalse(info.shouldPreserveTry());
  }

  public void testSetHidden() {
    JSDocInfo info = new JSDocInfo();
    info.setHidden(true);

    assertFalse(info.hasType());
    assertFalse(info.isConstant());
    assertFalse(info.isConstructor());
    assertFalse(info.isDefine());
    assertTrue(info.isHidden());
    assertFalse(info.shouldPreserveTry());
  }

  public void testSetShouldPreserveTry() {
    JSDocInfo info = new JSDocInfo();
    info.setShouldPreserveTry(true);

    assertFalse(info.isConstant());
    assertFalse(info.isConstructor());
    assertFalse(info.isDefine());
    assertFalse(info.isHidden());
    assertTrue(info.shouldPreserveTry());
  }

  public void testSetOverride() {
    JSDocInfo info = new JSDocInfo();
    info.setOverride(true);

    assertFalse(info.isDeprecated());
    assertFalse(info.isNoAlias());
    assertTrue(info.isOverride());
  }

  public void testSetExport() {
    JSDocInfo info = new JSDocInfo();
    info.setExport(true);

    assertTrue(info.isExport());
  }

  public void testSetPolymerBehavior() {
    JSDocInfo info = new JSDocInfo();
    assertFalse(info.isPolymerBehavior());
    info.setPolymerBehavior(true);

    assertTrue(info.isPolymerBehavior());
  }

  public void testSetNoAlias() {
    JSDocInfo info = new JSDocInfo();
    info.setNoAlias(true);

    assertFalse(info.isDeprecated());
    assertFalse(info.isOverride());
    assertTrue(info.isNoAlias());
  }

  public void testSetDeprecated() {
    JSDocInfo info = new JSDocInfo();
    info.setDeprecated(true);

    assertFalse(info.isNoAlias());
    assertFalse(info.isOverride());
    assertTrue(info.isDeprecated());
  }

  public void testMultipleSetFlags1() {
    JSDocInfo info = new JSDocInfo();
    info.setConstant(true);
    info.setConstructor(true);
    info.setHidden(true);
    info.setShouldPreserveTry(true);

    assertFalse(info.hasType());
    assertTrue(info.isConstant());
    assertTrue(info.isConstructor());
    assertFalse(info.isDefine());
    assertTrue(info.isHidden());
    assertTrue(info.shouldPreserveTry());

    info.setHidden(false);

    assertTrue(info.isConstant());
    assertTrue(info.isConstructor());
    assertFalse(info.isDefine());
    assertFalse(info.isHidden());
    assertTrue(info.shouldPreserveTry());

    info.setConstant(false);
    info.setConstructor(false);

    assertFalse(info.isConstant());
    assertFalse(info.isConstructor());
    assertFalse(info.isDefine());
    assertFalse(info.isHidden());
    assertTrue(info.shouldPreserveTry());

    info.setConstructor(true);

    assertFalse(info.isConstant());
    assertTrue(info.isConstructor());
    assertFalse(info.isDefine());
    assertFalse(info.isHidden());
    assertTrue(info.shouldPreserveTry());
  }

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

    assertTypeEquals(NUMBER_OBJECT_TYPE, resolve(cloned.getBaseType()));
    assertEquals("The source info", cloned.getDescription());
    assertTypeEquals(STRING_TYPE, resolve(cloned.getReturnType()));
    assertTrue(cloned.isConstant());
    assertTrue(cloned.isConstructor());
    assertTrue(cloned.isHidden());

    cloned.setDescription("The cloned info");
    cloned.setHidden(false);
    cloned.setBaseType(fromString("string"));

    assertTypeEquals(STRING_TYPE, resolve(cloned.getBaseType()));
    assertEquals("The cloned info", cloned.getDescription());
    assertFalse(cloned.isHidden());

    // Original info should be unchanged.
    assertTypeEquals(NUMBER_OBJECT_TYPE, resolve(info.getBaseType()));
    assertEquals("The source info", info.getDescription());
    assertTypeEquals(STRING_TYPE, resolve(info.getReturnType()));
    assertTrue(info.isConstant());
    assertTrue(info.isConstructor());
    assertTrue(info.isHidden());
  }

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
    assertTypeEquals(NUMBER_OBJECT_TYPE, resolve(cloned.getBaseType()));
    assertEquals("The source info", cloned.getDescription());
    assertNotSame(info.getReturnType().getRoot(), cloned.getReturnType().getRoot());
    assertTypeEquals(STRING_TYPE, resolve(cloned.getReturnType()));
    assertNotSame(info.getParameterType("a").getRoot(), cloned.getParameterType("a").getRoot());
    assertTypeEquals(STRING_TYPE, resolve(cloned.getParameterType("a")));
  }

  public void testCloneTypeExpressions2() {
    JSDocInfo info = new JSDocInfo();
    info.declareParam(null, "a");
    JSDocInfo cloned = info.clone(true);

    assertNull(cloned.getParameterType("a"));
  }

  public void testSetFileOverviewWithDocumentationOff() {
    JSDocInfo info = new JSDocInfo();
    info.documentFileOverview("hi bob");
    assertNull(info.getFileOverview());
  }

  public void testSetFileOverviewWithDocumentationOn() {
    JSDocInfo info = new JSDocInfo(true);
    info.documentFileOverview("hi bob");
    assertEquals("hi bob", info.getFileOverview());
  }

  public void testSetSuppressions() {
    JSDocInfo info = new JSDocInfo(true);
    info.setSuppressions(ImmutableSet.of("sam", "bob"));
    assertEquals(ImmutableSet.of("bob", "sam"), info.getSuppressions());
  }

  public void testSetModifies() {
    JSDocInfo info = new JSDocInfo(true);
    info.setModifies(ImmutableSet.of("this"));
    assertEquals(ImmutableSet.of("this"), info.getModifies());

    info = new JSDocInfo(true);
    info.setModifies(ImmutableSet.of("arguments"));
    assertEquals(ImmutableSet.of("arguments"), info.getModifies());
  }

  public void testAddSingleTemplateTypeName(){
    JSDocInfo info = new JSDocInfo(true);
    ImmutableList<String> typeNames = ImmutableList.of("T");
    assertTrue(info.declareTemplateTypeName("T"));
    assertEquals(typeNames, info.getTemplateTypeNames());
  }

  public void testAddMultipleTemplateTypeName(){
    JSDocInfo info = new JSDocInfo(true);
    ImmutableList<String> typeNames = ImmutableList.of("T", "R");
    info.declareTemplateTypeName("T");
    info.declareTemplateTypeName("R");
    assertEquals(typeNames, info.getTemplateTypeNames());
  }

  public void testFailToAddTemplateTypeName(){
    JSDocInfo info = new JSDocInfo(true);
    info.declareTemplateTypeName("T");
    assertFalse(info.declareTemplateTypeName("T"));
  }

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

  /** Gets the type expression for a simple type name. */
  private JSTypeExpression fromString(String s) {
    return new JSTypeExpression(Node.newString(s), "");
  }

  private JSType resolve(JSTypeExpression n, String... warnings) {
    errorReporter.setWarnings(warnings);
    return n.evaluate(null, registry);
  }

  private void assertTypeEquals(JSTypeNative a, JSType b) {
    assertTypeEquals(getNativeType(a), b);
  }

  private void assertTypeEquals(JSType a, JSType b) {
    Asserts.assertTypeEquals(a, b);
  }
}
