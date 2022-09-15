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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.JSDocInfo.Visibility.INHERITED;
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
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class JSDocInfoTest {
  private final TestErrorReporter errorReporter = new TestErrorReporter();
  private final JSTypeRegistry registry = new JSTypeRegistry(errorReporter);

  private JSType getNativeType(JSTypeNative typeId) {
    return registry.getNativeType(typeId);
  }

  @After
  public void validateWarningsAndErrors() {
    errorReporter.verifyHasEncounteredAllWarningsAndErrors();
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
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordType(fromString("string"));
    JSDocInfo info = builder.build();

    assertThat(info.getBaseType()).isNull();
    assertThat(info.getDescription()).isNull();
    assertThat(info.getEnumParameterType()).isNull();
    assertThat(info.getParameterCount()).isEqualTo(0);
    assertThat(info.getReturnType()).isNull();
    assertType(resolve(info.getType())).isEqualTo(getNativeType(STRING_TYPE));
    assertThat(info.getVisibility()).isEqualTo(INHERITED);
    assertThat(info.hasType()).isTrue();
    assertThat(info.isConstant()).isFalse();
    assertThat(info.isConstructor()).isFalse();
    assertThat(info.isHidden()).isFalse();
  }

  @Test
  public void testSetTypeAndVisibility() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordType(fromString("string"));
    builder.recordVisibility(PROTECTED);
    JSDocInfo info = builder.build();

    assertThat(info.getBaseType()).isNull();
    assertThat(info.getDescription()).isNull();
    assertThat(info.getEnumParameterType()).isNull();
    assertThat(info.getParameterCount()).isEqualTo(0);
    assertThat(info.getReturnType()).isNull();
    assertType(resolve(info.getType())).isEqualTo(getNativeType(STRING_TYPE));
    assertThat(info.getVisibility()).isEqualTo(PROTECTED);
    assertThat(info.hasType()).isTrue();
    assertThat(info.isConstant()).isFalse();
    assertThat(info.isConstructor()).isFalse();
    assertThat(info.isHidden()).isFalse();
  }

  @Test
  public void testSetReturnType() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordReturnType(fromString("string"));
    JSDocInfo info = builder.build();

    assertThat(info.getBaseType()).isNull();
    assertThat(info.getDescription()).isNull();
    assertThat(info.getEnumParameterType()).isNull();
    assertThat(info.getParameterCount()).isEqualTo(0);
    assertType(resolve(info.getReturnType())).isEqualTo(getNativeType(STRING_TYPE));
    assertThat(info.getType()).isNull();
    assertThat(info.getVisibility()).isEqualTo(INHERITED);
    assertThat(info.hasType()).isFalse();
    assertThat(info.isConstant()).isFalse();
    assertThat(info.isConstructor()).isFalse();
    assertThat(info.isHidden()).isFalse();
  }

  /** Tests that all module local names get correctly removed from a JSTypeExpression */
  @Test
  public void testRemovesModuleLocalNames() {
    JSTypeExpression jsTypeExpression = createSampleTypeExpression();
    Set<String> mockedModuleLocals = new LinkedHashSet<>();
    mockedModuleLocals.add("Item");
    mockedModuleLocals.add("AnotherItem");

    JSTypeExpression newExpr = jsTypeExpression.replaceNamesWithUnknownType(mockedModuleLocals);
    ImmutableSet<String> replacedNames = newExpr.getAllTypeNames();
    assertThat(replacedNames).doesNotContain("Item");
    assertThat(replacedNames).contains("string");
    assertThat(replacedNames).contains("boolean");
    assertThat(replacedNames).doesNotContain("AnotherItem");
  }

  @Test
  public void testSetReturnTypeAndBaseType() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordBaseType(
        new JSTypeExpression(new Node(Token.BANG, Node.newString("Number")), ""));
    builder.recordReturnType(fromString("string"));
    JSDocInfo info = builder.build();

    assertType(resolve(info.getBaseType()))
        .isEqualTo(getNativeType(NUMBER_OBJECT_TYPE));
    assertThat(info.getDescription()).isNull();
    assertThat(info.getEnumParameterType()).isNull();
    assertThat(info.getParameterCount()).isEqualTo(0);
    assertType(resolve(info.getReturnType())).isEqualTo(getNativeType(STRING_TYPE));
    assertThat(info.getType()).isNull();
    assertThat(info.getVisibility()).isEqualTo(INHERITED);
    assertThat(info.hasType()).isFalse();
    assertThat(info.isConstant()).isFalse();
    assertThat(info.isConstructor()).isFalse();
    assertThat(info.isHidden()).isFalse();
  }

  @Test
  public void testSetEnumParameterType() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordEnumParameterType(fromString("string"));
    JSDocInfo info = builder.build();

    assertThat(info.getBaseType()).isNull();
    assertThat(info.getDescription()).isNull();
    assertType(resolve(info.getEnumParameterType()))
        .isEqualTo(getNativeType(STRING_TYPE));
    assertThat(info.getParameterCount()).isEqualTo(0);
    assertThat(info.getReturnType()).isNull();
    assertThat(info.getType()).isNull();
    assertThat(info.getVisibility()).isEqualTo(INHERITED);
    assertThat(info.hasType()).isFalse();
    assertThat(info.isConstant()).isFalse();
    assertThat(info.isConstructor()).isFalse();
    assertThat(info.isHidden()).isFalse();
  }

  @Test
  public void testMultipleSetType() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordType(fromString("number"));

    assertThat(builder.recordReturnType(fromString("boolean"))).isFalse();
    assertThat(builder.recordEnumParameterType(fromString("string"))).isFalse();
    assertThat(builder.recordTypedef(fromString("string"))).isFalse();
    JSDocInfo info = builder.build();

    assertType(resolve(info.getType())).isEqualTo(getNativeType(NUMBER_TYPE));
    assertThat(info.getReturnType()).isNull();
    assertThat(info.getEnumParameterType()).isNull();
    assertThat(info.getTypedefType()).isNull();
    assertThat(info.hasType()).isTrue();
  }

  @Test
  public void testMultipleSetType2() {
    JSDocInfo.Builder builder = JSDocInfo.builder();

    builder.recordReturnType(fromString("boolean"));

    assertThat(builder.recordType(fromString("number"))).isFalse();
    assertThat(builder.recordEnumParameterType(fromString("string"))).isFalse();
    assertThat(builder.recordTypedef(fromString("string"))).isFalse();
    JSDocInfo info = builder.build();

    assertType(resolve(info.getReturnType())).isEqualTo(getNativeType(BOOLEAN_TYPE));
    assertThat(info.getEnumParameterType()).isNull();
    assertThat(info.getType()).isNull();
    assertThat(info.getTypedefType()).isNull();
    assertThat(info.hasType()).isFalse();
  }

  @Test
  public void testMultipleSetType3() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordEnumParameterType(fromString("boolean"));

    assertThat(builder.recordType(fromString("number"))).isFalse();
    assertThat(builder.recordReturnType(fromString("string"))).isFalse();
    assertThat(builder.recordTypedef(fromString("string"))).isFalse();
    JSDocInfo info = builder.build();

    assertThat(info.getType()).isNull();
    assertThat(info.getTypedefType()).isNull();
    assertThat(info.getReturnType()).isNull();
    assertType(resolve(info.getEnumParameterType()))
        .isEqualTo(getNativeType(BOOLEAN_TYPE));
  }

  @Test
  public void testSetTypedefType() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordTypedef(fromString("boolean"));
    JSDocInfo info = builder.build();

    assertType(resolve(info.getTypedefType())).isEqualTo(getNativeType(BOOLEAN_TYPE));
    assertThat(info.hasTypedefType()).isTrue();
    assertThat(info.hasType()).isFalse();
    assertThat(info.hasEnumParameterType()).isFalse();
    assertThat(info.hasReturnType()).isFalse();
  }

  @Test
  public void testSetConstant() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordConstancy();
    JSDocInfo info = builder.build();

    assertThat(info.hasType()).isFalse();
    assertThat(info.isConstant()).isTrue();
    assertThat(info.isConstructor()).isFalse();
    assertThat(info.isDefine()).isFalse();
    assertThat(info.isHidden()).isFalse();
  }

  @Test
  public void testSetConstructor() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordConstructor();
    JSDocInfo info = builder.build();

    assertThat(info.isConstant()).isFalse();
    assertThat(info.isConstructor()).isTrue();
    assertThat(info.isDefine()).isFalse();
    assertThat(info.isHidden()).isFalse();
  }

  @Test
  public void testSetDefine() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordDefineType(fromString("string"));
    JSDocInfo info = builder.build();

    assertThat(info.isConstant()).isTrue();
    assertThat(info.isConstructor()).isFalse();
    assertThat(info.isDefine()).isTrue();
    assertThat(info.isHidden()).isFalse();
  }

  @Test
  public void testSetHidden() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordHiddenness();
    JSDocInfo info = builder.build();

    assertThat(info.hasType()).isFalse();
    assertThat(info.isConstant()).isFalse();
    assertThat(info.isConstructor()).isFalse();
    assertThat(info.isDefine()).isFalse();
    assertThat(info.isHidden()).isTrue();
  }

  @Test
  public void testSetTypeSummary() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordTypeSummary();
    JSDocInfo info = builder.build();

    assertThat(info.isTypeSummary()).isTrue();
  }

  @Test
  public void testSetOverride() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordOverride();
    JSDocInfo info = builder.build();

    assertThat(info.isDeprecated()).isFalse();
    assertThat(info.isOverride()).isTrue();
  }

  @Test
  public void testSetExport() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordExport();
    JSDocInfo info = builder.build();

    assertThat(info.isExport()).isTrue();
  }

  @Test
  public void testSetPolymerBehavior() {
    assertThat(EMPTY.isPolymerBehavior()).isFalse();

    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordPolymerBehavior();
    JSDocInfo info = builder.build();
    assertThat(info.isPolymerBehavior()).isTrue();
  }

  @Test
  public void testSetPolymer() {
    assertThat(EMPTY.isPolymer()).isFalse();

    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordPolymer();
    JSDocInfo info = builder.build();
    assertThat(info.isPolymer()).isTrue();
  }

  @Test
  public void testSetCustomElement() {
    assertThat(EMPTY.isCustomElement()).isFalse();

    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordCustomElement();
    JSDocInfo info = builder.build();
    assertThat(info.isCustomElement()).isTrue();

  }

  @Test
  public void testSetMixinClass() {
    assertThat(EMPTY.isMixinClass()).isFalse();

    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordMixinClass();
    JSDocInfo info = builder.build();
    assertThat(info.isMixinClass()).isTrue();
  }

  @Test
  public void testSetMixinFunction() {
    assertThat(EMPTY.isMixinFunction()).isFalse();

    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordMixinFunction();
    JSDocInfo info = builder.build();
    assertThat(info.isMixinFunction()).isTrue();
  }

  @Test
  public void testSetNoAlias() {
    assertThat(EMPTY.isDeprecated()).isFalse();
    assertThat(EMPTY.isOverride()).isFalse();
  }

  @Test
  public void testSetDeprecated() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordDeprecated();
    JSDocInfo info = builder.build();

    assertThat(info.isOverride()).isFalse();
    assertThat(info.isDeprecated()).isTrue();
  }

  @Test
  public void testMultipleSetFlags1() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordConstancy();
    builder.recordConstructor();
    builder.recordHiddenness();
    JSDocInfo info = builder.build();

    assertThat(info.hasType()).isFalse();
    assertThat(info.isConstant()).isTrue();
    assertThat(info.isConstructor()).isTrue();
    assertThat(info.isDefine()).isFalse();
    assertThat(info.isHidden()).isTrue();

    builder = info.toBuilder();
    builder.recordMutable();
    info = builder.build();

    assertThat(info.isConstant()).isFalse();
    assertThat(info.isDefine()).isFalse();
    assertThat(info.isHidden()).isTrue();
  }

  @Test
  public void testDescriptionContainsAtSignCode() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.parseDocumentation();
    builder.recordOriginalCommentString("Blah blah {@code blah blah} blah blah.");
    JSDocInfo info = builder.build();

    assertThat(info.isAtSignCodePresent()).isTrue();
  }

  @Test
  public void testDescriptionDoesNotContainAtSignCode() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.parseDocumentation();
    builder.recordOriginalCommentString("Blah blah `blah blah` blah blah.");
    JSDocInfo info = builder.build();

    assertThat(info.isAtSignCodePresent()).isFalse();
  }

  @Test
  public void testClone() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordDescription("The source info");
    builder.recordConstancy();
    builder.recordConstructor();
    builder.recordHiddenness();
    builder.recordBaseType(
        new JSTypeExpression(new Node(Token.BANG, Node.newString("Number")), ""));
    builder.recordReturnType(fromString("string"));
    JSDocInfo info = builder.build();

    JSDocInfo cloned = info.clone();

    assertType(resolve(cloned.getBaseType()))
        .isEqualTo(getNativeType(NUMBER_OBJECT_TYPE));
    assertThat(cloned.getDescription()).isEqualTo("The source info");
    assertType(resolve(cloned.getReturnType())).isEqualTo(getNativeType(STRING_TYPE));
    assertThat(cloned.isConstant()).isTrue();
    assertThat(cloned.isConstructor()).isTrue();
    assertThat(cloned.isHidden()).isTrue();

    // TODO - cannot change these things!
    // cloned.recordDescription("The cloned info");
    // cloned.setHidden(false);
    // cloned.recordBaseType(fromString("string"));

    // assertType(resolve(cloned.getBaseType())).isEqualTo(getNativeType(STRING_TYPE));
    // assertThat(cloned.getDescription()).isEqualTo("The cloned info");
    // assertThat(cloned.isHidden()).isFalse();

    // // Original info should be unchanged.
    // assertType(resolve(info.getBaseType()))
    //     .isEqualTo(getNativeType(NUMBER_OBJECT_TYPE));
    // assertThat(info.getDescription()).isEqualTo("The source info");
    // assertType(resolve(info.getReturnType())).isEqualTo(getNativeType(STRING_TYPE));
    // assertThat(info.isConstant()).isTrue();
    // assertThat(info.isConstructor()).isTrue();
    // assertThat(info.isHidden()).isTrue();
  }

  @Test
  public void testCloneTypeExpressions1() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordDescription("The source info");
    builder.recordConstancy();
    builder.recordConstructor();
    builder.recordHiddenness();
    builder.recordBaseType(
        new JSTypeExpression(new Node(Token.BANG, Node.newString("Number")), ""));
    builder.recordReturnType(fromString("string"));
    builder.recordParameter("a", fromString("string"));
    JSDocInfo info = builder.build();

    JSDocInfo cloned = info.clone(true);

    assertThat(cloned.getBaseType().getRoot()).isNotSameInstanceAs(info.getBaseType().getRoot());
    assertType(resolve(cloned.getBaseType()))
        .isEqualTo(getNativeType(NUMBER_OBJECT_TYPE));
    assertThat(cloned.getDescription()).isEqualTo("The source info");
    assertThat(cloned.getReturnType().getRoot())
        .isNotSameInstanceAs(info.getReturnType().getRoot());
    assertType(resolve(cloned.getReturnType())).isEqualTo(getNativeType(STRING_TYPE));
    assertThat(cloned.getParameterType("a").getRoot())
        .isNotSameInstanceAs(info.getParameterType("a").getRoot());
    assertType(resolve(cloned.getParameterType("a")))
        .isEqualTo(getNativeType(STRING_TYPE));
  }

  @Test
  public void testCloneTypeExpressions2() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordParameter("a", null);
    JSDocInfo info = builder.build();

    JSDocInfo cloned = info.clone(true);

    assertThat(cloned.getParameterType("a")).isNull();
  }

  /** Test names in {@code @param} get replaced */
  @Test
  public void testJSDocInfoCloneAndReplaceNames_params() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    JSTypeExpression jsTypeExpression = createSampleTypeExpression();
    builder.recordParameter("a", jsTypeExpression);
    JSDocInfo info = builder.build();

    Set<String> mockedModuleLocals = new LinkedHashSet<>();
    mockedModuleLocals.add("Item");
    mockedModuleLocals.add("AnotherItem");

    JSDocInfo cloned = info.cloneAndReplaceTypeNames(mockedModuleLocals);

    assertThat(cloned.getParameterCount()).isEqualTo(1);
    assertThat(cloned.getParameterNameAt(0)).isEqualTo("a");
    assertThat(cloned.getParameterType("a"))
        .isNotEqualTo(jsTypeExpression); // not same reference after cloning
    assertThat(cloned.getParameterType("a").getAllTypeNames()).doesNotContain("Item");
    assertThat(cloned.getParameterType("a").getAllTypeNames()).doesNotContain("AnotherItem");
  }

  /** Test names in {@code @type} get replaced */
  //              BANG                                        BANG
  //               |                                           |
  //              Item                                         ?
  //            /  |   \                                    /  |   \
  //       QMARK QMARK QMARK          -------->        QMARK QMARK QMARK
  //          /    |    \                                 /    |     \
  //    string boolean  AnotherItem                 string   boolean  ?
  @Test
  public void testJSDocInfoCloneAndReplaceNames_Type() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    JSTypeExpression jsTypeExpression = createSampleTypeExpression();
    builder.recordType(jsTypeExpression);
    JSDocInfo info = builder.build();

    Set<String> mockedModuleLocals = new LinkedHashSet<>();
    mockedModuleLocals.add("Item");
    mockedModuleLocals.add("AnotherItem");

    JSDocInfo cloned = info.cloneAndReplaceTypeNames(mockedModuleLocals);

    assertThat(cloned.getType()).isNotEqualTo(jsTypeExpression);
    assertThat(cloned.getType().getAllTypeNames()).doesNotContain("Item");
    assertThat(cloned.getType().getAllTypeNames()).doesNotContain("AnotherItem");
  }

  /** Test names in {@code @type} get replaced */
  //              Item                                       ?
  //            /  |   \                                  /  |  \
  //       QMARK QMARK QMARK       --------->        QMARK QMARK QMARK
  //          /    |    \                               /    |    \
  //    string boolean  AnotherItem               string   boolean  ?
  @Test
  public void testJSDocInfoCloneAndReplaceNames_Type_rootReplacement() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    JSTypeExpression jsTypeExpression = createSampleTypeExpression_rootReplacement();
    builder.recordType(jsTypeExpression);
    JSDocInfo info = builder.build();

    Set<String> mockedModuleLocals = new LinkedHashSet<>();
    mockedModuleLocals.add("Item");
    mockedModuleLocals.add("AnotherItem");

    JSDocInfo cloned = info.cloneAndReplaceTypeNames(mockedModuleLocals);

    assertThat(cloned.getType()).isNotEqualTo(jsTypeExpression);
    assertThat(cloned.getType().getRoot().getToken()).isEqualTo(Token.QMARK);
    assertThat(cloned.getType().getAllTypeNames()).doesNotContain("Item");
    assertThat(cloned.getType().getAllTypeNames()).doesNotContain("AnotherItem");
  }

  @Test
  public void testJSDocInfoCloneAndReplaceNames_Type_SingleNode() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    Node root = Node.newString("Item");
    JSTypeExpression jsTypeExpression = new JSTypeExpression(root, "");
    builder.recordType(jsTypeExpression);
    JSDocInfo info = builder.build();

    Set<String> mockedModuleLocals = new LinkedHashSet<>();
    mockedModuleLocals.add("Item");
    mockedModuleLocals.add("AnotherItem");

    JSDocInfo cloned = info.cloneAndReplaceTypeNames(mockedModuleLocals);

    assertThat(cloned.getType()).isNotEqualTo(jsTypeExpression);
    assertThat(cloned.getType().getRoot().getToken()).isEqualTo(Token.QMARK);
    assertThat(cloned.getType().getAllTypeNames()).doesNotContain("Item");
    assertThat(cloned.getType().getAllTypeNames()).doesNotContain("AnotherItem");
  }

  @Test
  public void testSetFileOverviewWithDocumentationOff() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordFileOverview("hi bob");
    JSDocInfo info = builder.build(true);
    assertThat(info.getFileOverview()).isNull();
  }

  @Test
  public void testSetFileOverviewWithDocumentationOn() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.parseDocumentation();
    builder.recordFileOverview("hi bob");
    JSDocInfo info = builder.build();
    assertThat(info.getFileOverview()).isEqualTo("hi bob");
  }

  @Test
  public void testSetSuppressions() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.parseDocumentation();
    builder.recordSuppressions(ImmutableSet.of("sam", "bob"));
    builder.recordSuppression("fred");
    JSDocInfo info = builder.build();
    assertThat(info.getSuppressions()).isEqualTo(ImmutableSet.of("bob", "sam", "fred"));
  }

  @Test
  public void testSetModifies() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.parseDocumentation();
    builder.recordModifies(ImmutableSet.of("this"));
    JSDocInfo info = builder.build();
    assertThat(info.getModifies()).isEqualTo(ImmutableSet.of("this"));

    builder = JSDocInfo.builder();
    builder.parseDocumentation();
    builder.recordModifies(ImmutableSet.of("arguments"));
    info = builder.build();
    assertThat(info.getModifies()).isEqualTo(ImmutableSet.of("arguments"));
  }

  @Test
  public void testAddSingleTemplateTypeName() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.parseDocumentation();
    assertThat(builder.recordTemplateTypeName("T")).isTrue();
    JSDocInfo info = builder.build();

    ImmutableList<String> typeNames = ImmutableList.of("T");
    assertThat(info.getTemplateTypeNames()).isEqualTo(typeNames);
  }

  @Test
  public void testAddMultipleTemplateTypeName() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.parseDocumentation();
    ImmutableList<String> typeNames = ImmutableList.of("T", "R");
    builder.recordTemplateTypeName("T");
    builder.recordTemplateTypeName("R");
    JSDocInfo info = builder.build();
    assertThat(info.getTemplateTypeNames()).isEqualTo(typeNames);
  }

  @Test
  public void testFailToAddTemplateTypeName() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.parseDocumentation();
    builder.recordTemplateTypeName("T");
    assertThat(builder.recordTemplateTypeName("T")).isFalse();
  }

  @Test
  public void testGetThrowsDescription() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.parseDocumentation();

    // Set a description so that builder is initialized.
    builder.recordDescription("Lorem");
    builder.recordThrowsAnnotation("{Error} Because it does.");
    builder.recordThrowsAnnotation("{not a type}");
    JSDocInfo info = builder.build();
    assertThat(info.getThrowsAnnotations())
        .containsExactly("{Error} Because it does.", "{not a type}")
        .inOrder();
  }

  // https://github.com/google/closure-compiler/issues/2328
  @Test
  public void testGetTypeNodes_excludesNull() {
    JSDocInfo.Builder builder = JSDocInfo.builder();

    // no way to add to implemented interfaces
    assertThat(builder.recordImplementedInterface(null)).isFalse();
    JSDocInfo info = builder.build(true);

    Collection<Node> nodes = info.getTypeNodes();
    assertThat(nodes).isEmpty();
  }

  @Test
  public void testGetTypeNodes_includesTemplateTypeBounds() {
    // Given
    JSDocInfo.Builder builder = JSDocInfo.builder();

    // When
    builder.recordTemplateTypeName("A", null); // Uses `?` as the bounding expression by default.
    builder.recordTemplateTypeName("B", fromString("Foo"));
    builder.recordTemplateTypeName("C", fromString("Bar"));
    JSDocInfo info = builder.build();

    // Then
    ImmutableList<Node> upperBoundRoots =
        info.getTemplateTypes().values().stream()
            .map(JSTypeExpression::getRoot)
            .collect(toImmutableList());
    Collection<Node> nodes = info.getTypeNodes();

    assertThat(nodes).hasSize(3);
    assertThat(nodes).containsExactlyElementsIn(upperBoundRoots);
  }

  @Test
  public void testContainsDeclaration_implements() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordVisibility(INHERITED);
    builder.recordImplementedInterface(fromString("MyInterface"));
    JSDocInfo info = builder.build();

    assertThat(info.getImplementedInterfaceCount()).isEqualTo(1);
    assertThat(info.containsDeclaration()).isTrue();
  }

  @Test
  public void testContainsDeclaration_extends() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordVisibility(INHERITED);
    builder.recordBaseType(fromString("MyBaseClass"));
    JSDocInfo info = builder.build();

    assertThat(info.hasBaseType()).isTrue();
    assertThat(info.containsDeclaration()).isTrue();
  }

  @Test
  public void testClosurePrimitiveId_affectsEquality() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordClosurePrimitiveId("asserts.fail");
    JSDocInfo assertsFailJSDoc = builder.build();

    assertThat(JSDocInfo.areEquivalent(assertsFailJSDoc, EMPTY)).isFalse();

    builder = JSDocInfo.builder();
    builder.recordClosurePrimitiveId("asserts.fail");
    assertThat(JSDocInfo.areEquivalent(assertsFailJSDoc, builder.build())).isTrue();
  }

  /** Gets the type expression for a simple type name. */
  private static JSTypeExpression fromString(String s) {
    return new JSTypeExpression(Node.newString(s), "");
  }

  private JSType resolve(JSTypeExpression n, String... warnings) {
    errorReporter.expectAllWarnings(warnings);
    return n.evaluate(null, registry);
  }

  /** Generates a sample type expression tree */
  //
  //              BANG
  //               |
  //              Item
  //            /  |   \
  //       QMARK QMARK QMARK
  //          /    |    \
  //    string boolean  AnotherItem
  private static JSTypeExpression createSampleTypeExpression() {
    Node root = new Node(Token.BANG, Node.newString("Item"));
    Node child1 = new Node(Token.QMARK, Node.newString("string"));
    Node child2 = new Node(Token.QMARK, Node.newString("boolean"));
    Node child3 = new Node(Token.QMARK, Node.newString("AnotherItem"));
    root.addChildToBack(child1);
    root.addChildToBack(child2);
    root.addChildToBack(child3);
    return new JSTypeExpression(root, "");
  }

  /** Generates a sample type expression tree with root node as a typename */
  //              Item
  //            /  |   \
  //       QMARK QMARK QMARK
  //          /    |    \
  //    string boolean  AnotherItem
  private static JSTypeExpression createSampleTypeExpression_rootReplacement() {
    Node root = Node.newString("Item");
    Node child1 = new Node(Token.QMARK, Node.newString("string"));
    Node child2 = new Node(Token.QMARK, Node.newString("boolean"));
    Node child3 = new Node(Token.QMARK, Node.newString("AnotherItem"));
    root.addChildToBack(child1);
    root.addChildToBack(child2);
    root.addChildToBack(child3);
    return new JSTypeExpression(root, "");
  }

  private static final JSDocInfo EMPTY = JSDocInfo.builder().build(true);
}
