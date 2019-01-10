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
import static com.google.common.truth.Truth.assertWithMessage;
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class JSDocInfoTest {
  private final TestErrorReporter errorReporter = new TestErrorReporter(null, null);
  private final JSTypeRegistry registry = new JSTypeRegistry(errorReporter);

  private JSType getNativeType(JSTypeNative typeId) {
    return registry.getNativeType(typeId);
  }

  /** Tests the assigned ordinal of the elements of the {@link JSDocInfo.Visibility} enum. */
  @Test
  public void testVisibilityOrdinal() {
    assertThat(PRIVATE.ordinal()).isEqualTo(0);
    assertThat(PACKAGE.ordinal()).isEqualTo(1);
    assertThat(PROTECTED.ordinal()).isEqualTo(2);
    assertThat(PUBLIC.ordinal()).isEqualTo(3);
  }

  @Test
  public void testSetType() {
    JSDocInfo info = new JSDocInfo();
    info.setType(fromString("string"));

    assertThat(info.getBaseType()).isNull();
    assertThat(info.getDescription()).isNull();
    assertThat(info.getEnumParameterType()).isNull();
    assertThat(info.getParameterCount()).isEqualTo(0);
    assertThat(info.getReturnType()).isNull();
    assertType(resolve(info.getType())).isStructurallyEqualTo(getNativeType(STRING_TYPE));
    assertThat(info.getVisibility()).isNull();
    assertThat(info.hasType()).isTrue();
    assertThat(info.isConstant()).isFalse();
    assertThat(info.isConstructor()).isFalse();
    assertThat(info.isHidden()).isFalse();
  }

  @Test
  public void testSetTypeAndVisibility() {
    JSDocInfo info = new JSDocInfo();
    info.setType(fromString("string"));
    info.setVisibility(PROTECTED);

    assertThat(info.getBaseType()).isNull();
    assertThat(info.getDescription()).isNull();
    assertThat(info.getEnumParameterType()).isNull();
    assertThat(info.getParameterCount()).isEqualTo(0);
    assertThat(info.getReturnType()).isNull();
    assertType(resolve(info.getType())).isStructurallyEqualTo(getNativeType(STRING_TYPE));
    assertThat(info.getVisibility()).isEqualTo(PROTECTED);
    assertThat(info.hasType()).isTrue();
    assertThat(info.isConstant()).isFalse();
    assertThat(info.isConstructor()).isFalse();
    assertThat(info.isHidden()).isFalse();
  }

  @Test
  public void testSetReturnType() {
    JSDocInfo info = new JSDocInfo();
    info.setReturnType(fromString("string"));

    assertThat(info.getBaseType()).isNull();
    assertThat(info.getDescription()).isNull();
    assertThat(info.getEnumParameterType()).isNull();
    assertThat(info.getParameterCount()).isEqualTo(0);
    assertType(resolve(info.getReturnType())).isStructurallyEqualTo(getNativeType(STRING_TYPE));
    assertThat(info.getType()).isNull();
    assertThat(info.getVisibility()).isNull();
    assertThat(info.hasType()).isFalse();
    assertThat(info.isConstant()).isFalse();
    assertThat(info.isConstructor()).isFalse();
    assertThat(info.isHidden()).isFalse();
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
    assertThat(info.getDescription()).isNull();
    assertThat(info.getEnumParameterType()).isNull();
    assertThat(info.getParameterCount()).isEqualTo(0);
    assertType(resolve(info.getReturnType())).isStructurallyEqualTo(getNativeType(STRING_TYPE));
    assertThat(info.getType()).isNull();
    assertThat(info.getVisibility()).isNull();
    assertThat(info.hasType()).isFalse();
    assertThat(info.isConstant()).isFalse();
    assertThat(info.isConstructor()).isFalse();
    assertThat(info.isHidden()).isFalse();
  }

  @Test
  public void testSetEnumParameterType() {
    JSDocInfo info = new JSDocInfo();
    info.setEnumParameterType(fromString("string"));

    assertThat(info.getBaseType()).isNull();
    assertThat(info.getDescription()).isNull();
    assertType(resolve(info.getEnumParameterType()))
        .isStructurallyEqualTo(getNativeType(STRING_TYPE));
    assertThat(info.getParameterCount()).isEqualTo(0);
    assertThat(info.getReturnType()).isNull();
    assertThat(info.getType()).isNull();
    assertThat(info.getVisibility()).isNull();
    assertThat(info.hasType()).isFalse();
    assertThat(info.isConstant()).isFalse();
    assertThat(info.isConstructor()).isFalse();
    assertThat(info.isHidden()).isFalse();
  }

  @Test
  public void testMultipleSetType() {
    JSDocInfo info = new JSDocInfo();
    info.setType(fromString("number"));

    try {
      info.setReturnType(fromString("boolean"));
      assertWithMessage("Expected exception").fail();
    } catch (IllegalStateException e) {
      // expected
    }

    try {
      info.setEnumParameterType(fromString("string"));
      assertWithMessage("Expected exception").fail();
    } catch (IllegalStateException e) {
      // expected
    }

    try {
      info.declareTypedefType(fromString("string"));
      assertWithMessage("Expected exception").fail();
    } catch (IllegalStateException e) {
      // expected
    }

    assertType(resolve(info.getType())).isStructurallyEqualTo(getNativeType(NUMBER_TYPE));
    assertThat(info.getReturnType()).isNull();
    assertThat(info.getEnumParameterType()).isNull();
    assertThat(info.getTypedefType()).isNull();
    assertThat(info.hasType()).isTrue();
  }

  @Test
  public void testMultipleSetType2() {
    JSDocInfo info = new JSDocInfo();

    info.setReturnType(fromString("boolean"));

    try {
      info.setType(fromString("number"));
      assertWithMessage("Expected exception").fail();
    } catch (IllegalStateException e) {
      // expected
    }

    try {
      info.setEnumParameterType(fromString("string"));
      assertWithMessage("Expected exception").fail();
    } catch (IllegalStateException e) {
      // expected
    }

    try {
      info.declareTypedefType(fromString("string"));
      assertWithMessage("Expected exception").fail();
    } catch (IllegalStateException e) {
      // expected
    }

    assertType(resolve(info.getReturnType())).isStructurallyEqualTo(getNativeType(BOOLEAN_TYPE));
    assertThat(info.getEnumParameterType()).isNull();
    assertThat(info.getType()).isNull();
    assertThat(info.getTypedefType()).isNull();
    assertThat(info.hasType()).isFalse();
  }

  @Test
  public void testMultipleSetType3() {
    JSDocInfo info = new JSDocInfo();
    info.setEnumParameterType(fromString("boolean"));

    try {
      info.setType(fromString("number"));
      assertWithMessage("Expected exception").fail();
    } catch (IllegalStateException e) {
      // expected
    }

    try {
      info.setReturnType(fromString("string"));
      assertWithMessage("Expected exception").fail();
    } catch (IllegalStateException e) {
      // expected
    }

    try {
      info.declareTypedefType(fromString("string"));
      assertWithMessage("Expected exception").fail();
    } catch (IllegalStateException e) {
      // expected
    }

    assertThat(info.getType()).isNull();
    assertThat(info.getTypedefType()).isNull();
    assertThat(info.getReturnType()).isNull();
    assertType(resolve(info.getEnumParameterType()))
        .isStructurallyEqualTo(getNativeType(BOOLEAN_TYPE));
  }

  @Test
  public void testSetTypedefType() {
    JSDocInfo info = new JSDocInfo();
    info.declareTypedefType(fromString("boolean"));

    assertType(resolve(info.getTypedefType())).isStructurallyEqualTo(getNativeType(BOOLEAN_TYPE));
    assertThat(info.hasTypedefType()).isTrue();
    assertThat(info.hasType()).isFalse();
    assertThat(info.hasEnumParameterType()).isFalse();
    assertThat(info.hasReturnType()).isFalse();
  }

  @Test
  public void testSetConstant() {
    JSDocInfo info = new JSDocInfo();
    info.setConstant(true);

    assertThat(info.hasType()).isFalse();
    assertThat(info.isConstant()).isTrue();
    assertThat(info.isConstructor()).isFalse();
    assertThat(info.isDefine()).isFalse();
    assertThat(info.isHidden()).isFalse();
  }

  @Test
  public void testSetConstructor() {
    JSDocInfo info = new JSDocInfo();
    info.setConstructor(true);

    assertThat(info.isConstant()).isFalse();
    assertThat(info.isConstructor()).isTrue();
    assertThat(info.isDefine()).isFalse();
    assertThat(info.isHidden()).isFalse();
  }

  @Test
  public void testSetDefine() {
    JSDocInfo info = new JSDocInfo();
    info.setDefine(true);

    assertThat(info.isConstant()).isTrue();
    assertThat(info.isConstructor()).isFalse();
    assertThat(info.isDefine()).isTrue();
    assertThat(info.isHidden()).isFalse();
  }

  @Test
  public void testSetHidden() {
    JSDocInfo info = new JSDocInfo();
    info.setHidden(true);

    assertThat(info.hasType()).isFalse();
    assertThat(info.isConstant()).isFalse();
    assertThat(info.isConstructor()).isFalse();
    assertThat(info.isDefine()).isFalse();
    assertThat(info.isHidden()).isTrue();
  }

  @Test
  public void testSetOverride() {
    JSDocInfo info = new JSDocInfo();
    info.setOverride(true);

    assertThat(info.isDeprecated()).isFalse();
    assertThat(info.isOverride()).isTrue();
  }

  @Test
  public void testSetExport() {
    JSDocInfo info = new JSDocInfo();
    info.setExport(true);

    assertThat(info.isExport()).isTrue();
  }

  @Test
  public void testSetPolymerBehavior() {
    JSDocInfo info = new JSDocInfo();
    assertThat(info.isPolymerBehavior()).isFalse();
    info.setPolymerBehavior(true);

    assertThat(info.isPolymerBehavior()).isTrue();
  }

  @Test
  public void testSetPolymer() {
    JSDocInfo info = new JSDocInfo();
    assertThat(info.isPolymer()).isFalse();
    info.setPolymer(true);

    assertThat(info.isPolymer()).isTrue();
  }

  @Test
  public void testSetCustomElement() {
    JSDocInfo info = new JSDocInfo();
    assertThat(info.isCustomElement()).isFalse();
    info.setCustomElement(true);

    assertThat(info.isCustomElement()).isTrue();
  }

  @Test
  public void testSetMixinClass() {
    JSDocInfo info = new JSDocInfo();
    assertThat(info.isMixinClass()).isFalse();
    info.setMixinClass(true);

    assertThat(info.isMixinClass()).isTrue();
  }

  @Test
  public void testSetMixinFunction() {
    JSDocInfo info = new JSDocInfo();
    assertThat(info.isMixinFunction()).isFalse();
    info.setMixinFunction(true);

    assertThat(info.isMixinFunction()).isTrue();
  }

  @Test
  public void testSetNoAlias() {
    JSDocInfo info = new JSDocInfo();

    assertThat(info.isDeprecated()).isFalse();
    assertThat(info.isOverride()).isFalse();
  }

  @Test
  public void testSetDeprecated() {
    JSDocInfo info = new JSDocInfo();
    info.setDeprecated(true);

    assertThat(info.isOverride()).isFalse();
    assertThat(info.isDeprecated()).isTrue();
  }

  @Test
  public void testMultipleSetFlags1() {
    JSDocInfo info = new JSDocInfo();
    info.setConstant(true);
    info.setConstructor(true);
    info.setHidden(true);

    assertThat(info.hasType()).isFalse();
    assertThat(info.isConstant()).isTrue();
    assertThat(info.isConstructor()).isTrue();
    assertThat(info.isDefine()).isFalse();
    assertThat(info.isHidden()).isTrue();

    info.setHidden(false);

    assertThat(info.isConstant()).isTrue();
    assertThat(info.isConstructor()).isTrue();
    assertThat(info.isDefine()).isFalse();
    assertThat(info.isHidden()).isFalse();

    info.setConstant(false);
    info.setConstructor(false);

    assertThat(info.isConstant()).isFalse();
    assertThat(info.isConstructor()).isFalse();
    assertThat(info.isDefine()).isFalse();
    assertThat(info.isHidden()).isFalse();

    info.setConstructor(true);

    assertThat(info.isConstant()).isFalse();
    assertThat(info.isConstructor()).isTrue();
    assertThat(info.isDefine()).isFalse();
    assertThat(info.isHidden()).isFalse();
  }

  @Test
  public void testDescriptionContainsAtSignCode() {
    JSDocInfo info = new JSDocInfo(true);
    info.setOriginalCommentString("Blah blah {@code blah blah} blah blah.");

    assertThat(info.isAtSignCodePresent()).isTrue();
  }

  @Test
  public void testDescriptionDoesNotContainAtSignCode() {
    JSDocInfo info = new JSDocInfo(true);
    info.setOriginalCommentString("Blah blah `blah blah` blah blah.");

    assertThat(info.isAtSignCodePresent()).isFalse();
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
    assertThat(cloned.getDescription()).isEqualTo("The source info");
    assertType(resolve(cloned.getReturnType())).isStructurallyEqualTo(getNativeType(STRING_TYPE));
    assertThat(cloned.isConstant()).isTrue();
    assertThat(cloned.isConstructor()).isTrue();
    assertThat(cloned.isHidden()).isTrue();

    cloned.setDescription("The cloned info");
    cloned.setHidden(false);
    cloned.setBaseType(fromString("string"));

    assertType(resolve(cloned.getBaseType())).isStructurallyEqualTo(getNativeType(STRING_TYPE));
    assertThat(cloned.getDescription()).isEqualTo("The cloned info");
    assertThat(cloned.isHidden()).isFalse();

    // Original info should be unchanged.
    assertType(resolve(info.getBaseType()))
        .isStructurallyEqualTo(getNativeType(NUMBER_OBJECT_TYPE));
    assertThat(info.getDescription()).isEqualTo("The source info");
    assertType(resolve(info.getReturnType())).isStructurallyEqualTo(getNativeType(STRING_TYPE));
    assertThat(info.isConstant()).isTrue();
    assertThat(info.isConstructor()).isTrue();
    assertThat(info.isHidden()).isTrue();
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

    assertThat(cloned.getBaseType().getRoot()).isNotSameAs(info.getBaseType().getRoot());
    assertType(resolve(cloned.getBaseType()))
        .isStructurallyEqualTo(getNativeType(NUMBER_OBJECT_TYPE));
    assertThat(cloned.getDescription()).isEqualTo("The source info");
    assertThat(cloned.getReturnType().getRoot()).isNotSameAs(info.getReturnType().getRoot());
    assertType(resolve(cloned.getReturnType())).isStructurallyEqualTo(getNativeType(STRING_TYPE));
    assertThat(cloned.getParameterType("a").getRoot())
        .isNotSameAs(info.getParameterType("a").getRoot());
    assertType(resolve(cloned.getParameterType("a")))
        .isStructurallyEqualTo(getNativeType(STRING_TYPE));
  }

  @Test
  public void testCloneTypeExpressions2() {
    JSDocInfo info = new JSDocInfo();
    info.declareParam(null, "a");
    JSDocInfo cloned = info.clone(true);

    assertThat(cloned.getParameterType("a")).isNull();
  }

  @Test
  public void testSetFileOverviewWithDocumentationOff() {
    JSDocInfo info = new JSDocInfo();
    info.documentFileOverview("hi bob");
    assertThat(info.getFileOverview()).isNull();
  }

  @Test
  public void testSetFileOverviewWithDocumentationOn() {
    JSDocInfo info = new JSDocInfo(true);
    info.documentFileOverview("hi bob");
    assertThat(info.getFileOverview()).isEqualTo("hi bob");
  }

  @Test
  public void testSetSuppressions() {
    JSDocInfo info = new JSDocInfo(true);
    info.addSuppressions(ImmutableSet.of("sam", "bob"));
    assertThat(info.getSuppressions()).isEqualTo(ImmutableSet.of("bob", "sam"));
  }

  @Test
  public void testSetModifies() {
    JSDocInfo info = new JSDocInfo(true);
    info.setModifies(ImmutableSet.of("this"));
    assertThat(info.getModifies()).isEqualTo(ImmutableSet.of("this"));

    info = new JSDocInfo(true);
    info.setModifies(ImmutableSet.of("arguments"));
    assertThat(info.getModifies()).isEqualTo(ImmutableSet.of("arguments"));
  }

  @Test
  public void testAddSingleTemplateTypeName() {
    JSDocInfo info = new JSDocInfo(true);
    ImmutableList<String> typeNames = ImmutableList.of("T");
    assertThat(info.declareTemplateTypeName("T")).isTrue();
    assertThat(info.getTemplateTypeNames()).isEqualTo(typeNames);
  }

  @Test
  public void testAddMultipleTemplateTypeName() {
    JSDocInfo info = new JSDocInfo(true);
    ImmutableList<String> typeNames = ImmutableList.of("T", "R");
    info.declareTemplateTypeName("T");
    info.declareTemplateTypeName("R");
    assertThat(info.getTemplateTypeNames()).isEqualTo(typeNames);
  }

  @Test
  public void testFailToAddTemplateTypeName() {
    JSDocInfo info = new JSDocInfo(true);
    info.declareTemplateTypeName("T");
    assertThat(info.declareTemplateTypeName("T")).isFalse();
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
    assertThat(info.getThrowsDescriptionForType(errorType)).isEqualTo("Because it does.");
    assertThat(info.getThrowsDescriptionForType(otherType)).isEmpty();
    assertThat(info.getThrowsDescriptionForType(fromString("NeverSeen"))).isNull();
  }

  // https://github.com/google/closure-compiler/issues/2328
  @Test
  public void testIssue2328() {
    JSDocInfo info = new JSDocInfo();

    // should be added to implemented interfaces
    assertThat(info.addImplementedInterface(null)).isTrue();

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
