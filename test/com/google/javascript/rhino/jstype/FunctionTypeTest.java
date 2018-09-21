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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.testing.Asserts;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for FunctionTypes.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
@RunWith(JUnit4.class)
public class FunctionTypeTest extends BaseJSTypeTestCase {
  @Test
  public void testDefaultReturnType() {
    FunctionType f = new FunctionBuilder(registry).build();
    assertEquals(UNKNOWN_TYPE, f.getReturnType());
  }

  @Test
  public void testSupAndInfOfReturnTypes() {
    FunctionType retString = new FunctionBuilder(registry)
        .withParamsNode(registry.createParameters())
        .withInferredReturnType(STRING_TYPE).build();
    FunctionType retNumber = new FunctionBuilder(registry)
        .withParamsNode(registry.createParameters())
        .withReturnType(NUMBER_TYPE).build();

    assertLeastSupertype(
        "function(): (number|string)", retString, retNumber);
    assertGreatestSubtype(
        "function(): None", retString, retNumber);

    assertTrue(retString.isReturnTypeInferred());
    assertFalse(retNumber.isReturnTypeInferred());
    assertTrue(
        ((FunctionType) retString.getLeastSupertype(retNumber))
        .isReturnTypeInferred());
    assertTrue(
        ((FunctionType) retString.getGreatestSubtype(retString))
        .isReturnTypeInferred());
  }

  @Test
  public void testSupAndInfOfReturnTypesWithDifferentParams() {
    FunctionType retString = new FunctionBuilder(registry)
        .withParamsNode(registry.createParameters(NUMBER_TYPE))
        .withInferredReturnType(STRING_TYPE).build();
    FunctionType retNumber = new FunctionBuilder(registry)
        .withParamsNode(registry.createParameters())
        .withReturnType(NUMBER_TYPE).build();

    assertLeastSupertype(
        "Function", retString, retNumber);
    assertGreatestSubtype(
        "function(...*): None", retString, retNumber);
  }

  @Test
  public void testSupAndInfWithDifferentParams() {
    FunctionType retString = new FunctionBuilder(registry)
        .withParamsNode(registry.createParameters(NUMBER_TYPE))
        .withReturnType(STRING_TYPE).build();
    FunctionType retNumber = new FunctionBuilder(registry)
        .withParamsNode(registry.createParameters(STRING_TYPE))
        .withReturnType(NUMBER_TYPE).build();

    assertLeastSupertype(
        "Function", retString, retNumber);
    assertGreatestSubtype(
        "function(...*): None", retString, retNumber);
  }

  @Test
  public void testSupAndInfWithDifferentThisTypes() {
    FunctionType retString = new FunctionBuilder(registry)
        .withParamsNode(registry.createParameters())
        .withTypeOfThis(OBJECT_TYPE)
        .withReturnType(STRING_TYPE).build();
    FunctionType retNumber = new FunctionBuilder(registry)
        .withParamsNode(registry.createParameters())
        .withTypeOfThis(DATE_TYPE)
        .withReturnType(NUMBER_TYPE).build();

    assertLeastSupertype(
        "function(this:Object): (number|string)", retString, retNumber);
    assertGreatestSubtype(
        "function(this:Date): None", retString, retNumber);
  }

  @Test
  public void testSupAndInfWithDifferentThisTypes2() {
    FunctionType retString = new FunctionBuilder(registry)
        .withParamsNode(registry.createParameters())
        .withTypeOfThis(ARRAY_TYPE)
        .withReturnType(STRING_TYPE).build();
    FunctionType retNumber = new FunctionBuilder(registry)
        .withParamsNode(registry.createParameters())
        .withTypeOfThis(DATE_TYPE)
        .withReturnType(NUMBER_TYPE).build();

    assertLeastSupertype(
        "function(this:(Array|Date)): (number|string)", retString, retNumber);
    assertGreatestSubtype(
        "function(this:NoObject): None", retString, retNumber);
  }

  @Test
  public void testSupAndInfOfReturnTypesWithNumOfParams() {
    FunctionType twoNumbers = new FunctionBuilder(registry)
        .withParamsNode(registry.createParameters(NUMBER_TYPE, NUMBER_TYPE))
        .withReturnType(BOOLEAN_TYPE).build();
    FunctionType oneNumber = new FunctionBuilder(registry)
        .withParamsNode(registry.createParameters(NUMBER_TYPE))
        .withReturnType(BOOLEAN_TYPE).build();

    assertLeastSupertype(
        "function(number, number): boolean", twoNumbers, oneNumber);
    assertGreatestSubtype(
        "function(number): boolean", twoNumbers, oneNumber);
  }

  @Test
  public void testSubtypeWithInterfaceThisType() {
    FunctionType iface = registry.createInterfaceType("I", null,
        ImmutableList.<TemplateType>of(), false);
    FunctionType ifaceReturnBoolean = new FunctionBuilder(registry)
        .withParamsNode(registry.createParameters())
        .withTypeOfThis(iface.getInstanceType())
        .withReturnType(BOOLEAN_TYPE).build();
    FunctionType objReturnBoolean = new FunctionBuilder(registry)
        .withParamsNode(registry.createParameters())
        .withTypeOfThis(OBJECT_TYPE)
        .withReturnType(BOOLEAN_TYPE).build();
    assertTrue(objReturnBoolean.isSubtype(ifaceReturnBoolean));
  }

  @Test
  public void testOrdinaryFunctionPrototype() {
    FunctionType oneNumber = new FunctionBuilder(registry)
        .withParamsNode(registry.createParameters(NUMBER_TYPE))
        .withReturnType(BOOLEAN_TYPE).build();
    assertEquals(ImmutableSet.<String>of(), oneNumber.getOwnPropertyNames());
  }

  @Test
  public void testCtorWithPrototypeSet() {
    FunctionType ctor = registry.createConstructorType("Foo", null, null, null, null, false);
    assertFalse(ctor.getInstanceType().isUnknownType());

    Node node = new Node(Token.OBJECTLIT);
    ctor.defineDeclaredProperty("prototype", UNKNOWN_TYPE, node);
    assertTrue(ctor.getInstanceType().isUnknownType());

    assertEquals(ImmutableSet.<String>of("prototype"),
        ctor.getOwnPropertyNames());
    assertTrue(ctor.isPropertyTypeInferred("prototype"));
    assertTrue(ctor.getPropertyType("prototype").isUnknownType());

    assertEquals(node, ctor.getPropertyNode("prototype"));
  }

  @Test
  public void testCtorWithInstanceInheritance() {
    FunctionType fooCtor = new FunctionBuilder(registry).forConstructor().withName("Foo").build();
    FunctionType barCtor = new FunctionBuilder(registry).forConstructor().withName("Bar").build();
    barCtor.setPrototypeBasedOn(fooCtor.getInstanceType());
    fooCtor.getPrototype().defineDeclaredProperty("bar", STRING_TYPE, null);

    assertEquals(fooCtor.getInstanceType(), barCtor.getPrototype().getImplicitPrototype());
    assertEquals(STRING_TYPE, fooCtor.getInstanceType().getSlot("bar").getType());
  }

  @Test
  public void testCtorWithClassSideInheritance() {
    FunctionType fooCtor = new FunctionBuilder(registry).forConstructor().withName("Foo").build();
    // NOTE: FunctionType does not look into the node, only at its token.
    Node source = new Node(Token.CLASS);
    FunctionType barCtor =
        new FunctionBuilder(registry)
            .withSourceNode(source)
            .forConstructor()
            .withName("Bar")
            .build();
    barCtor.setPrototypeBasedOn(fooCtor.getInstanceType());
    fooCtor.defineDeclaredProperty("foo", NUMBER_TYPE, null);

    assertEquals(fooCtor, barCtor.getImplicitPrototype());
    assertEquals(NUMBER_TYPE, barCtor.getSlot("foo").getType());
  }

  @Test
  public void testEmptyFunctionTypes() {
    assertTrue(LEAST_FUNCTION_TYPE.isEmptyType());
    assertFalse(GREATEST_FUNCTION_TYPE.isEmptyType());
  }

  @Test
  public void testInterfacePrototypeChain1() {
    FunctionType iface = registry.createInterfaceType("I", null,
        ImmutableList.<TemplateType>of(), false);
    assertTypeEquals(
        iface.getPrototype(),
        iface.getInstanceType().getImplicitPrototype());
    assertTypeEquals(
        OBJECT_TYPE,
        iface.getPrototype().getImplicitPrototype());
  }

  @Test
  public void testInterfacePrototypeChain2() {
    FunctionType iface = registry.createInterfaceType("I", null,
        ImmutableList.<TemplateType>of(), false);
    iface.getPrototype().defineDeclaredProperty(
        "numberProp", NUMBER_TYPE, null);

    FunctionType subIface = registry.createInterfaceType("SubI", null,
        ImmutableList.<TemplateType>of(), false);
    subIface.setExtendedInterfaces(
        Lists.<ObjectType>newArrayList(iface.getInstanceType()));
    assertTypeEquals(
        subIface.getPrototype(),
        subIface.getInstanceType().getImplicitPrototype());
    assertTypeEquals(
        OBJECT_TYPE,
        subIface.getPrototype().getImplicitPrototype());

    ObjectType subIfaceInst = subIface.getInstanceType();
    assertTrue(subIfaceInst.hasProperty("numberProp"));
    assertTrue(subIfaceInst.isPropertyTypeDeclared("numberProp"));
    assertFalse(subIfaceInst.isPropertyTypeInferred("numberProp"));
  }

  @Test
  public void testInterfacePrototypeChain3() {
    TemplateType templateT = registry.createTemplateType("T");
    FunctionType iface = registry.createInterfaceType("I", null,
        ImmutableList.of(templateT), false);
    iface.getPrototype().defineDeclaredProperty(
        "genericProp", templateT, null);

    FunctionType subIface = registry.createInterfaceType("SubI", null,
        ImmutableList.<TemplateType>of(), false);
    subIface.setExtendedInterfaces(
        Lists.<ObjectType>newArrayList(iface.getInstanceType()));
    assertTypeEquals(
        subIface.getPrototype(),
        subIface.getInstanceType().getImplicitPrototype());
    assertTypeEquals(
        OBJECT_TYPE,
        subIface.getPrototype().getImplicitPrototype());

    ObjectType subIfaceInst = subIface.getInstanceType();
    assertTrue(subIfaceInst.hasProperty("genericProp"));
    assertTrue(subIfaceInst.isPropertyTypeDeclared("genericProp"));
    assertFalse(subIfaceInst.isPropertyTypeInferred("genericProp"));
    assertEquals(templateT, subIfaceInst.getPropertyType("genericProp"));
  }

  private void assertLeastSupertype(String s, JSType t1, JSType t2) {
    assertEquals(s, t1.getLeastSupertype(t2).toString());
    assertEquals(s, t2.getLeastSupertype(t1).toString());
  }

  private void assertGreatestSubtype(String s, JSType t1, JSType t2) {
    assertEquals(s, t1.getGreatestSubtype(t2).toString());
    assertEquals(s, t2.getGreatestSubtype(t1).toString());
  }

  @Test
  public void testIsEquivalentTo() {
    FunctionType type = new FunctionBuilder(registry).build();
    assertFalse(type.equals(null));
    assertTrue(type.isEquivalentTo(type));
  }

  @Test
  public void testIsEquivalentToParams() {
    FunctionType oneNum = new FunctionBuilder(registry)
        .withParamsNode(registry.createParameters(NUMBER_TYPE))
        .build();
    FunctionType optNum = new FunctionBuilder(registry)
        .withParamsNode(registry.createOptionalParameters(NUMBER_TYPE))
        .build();
    FunctionType varNum = new FunctionBuilder(registry)
        .withParamsNode(registry.createParametersWithVarArgs(NUMBER_TYPE))
        .build();
    Asserts.assertEquivalenceOperations(oneNum, oneNum);
    Asserts.assertEquivalenceOperations(optNum, optNum);
    Asserts.assertEquivalenceOperations(varNum, varNum);
    assertFalse(oneNum.isEquivalentTo(optNum));
    assertFalse(oneNum.isEquivalentTo(varNum));
    assertFalse(optNum.isEquivalentTo(varNum));
  }

  @Test
  public void testIsEquivalentOptAndVarArgs() {
    FunctionType varNum = new FunctionBuilder(registry)
        .withParamsNode(registry.createParametersWithVarArgs(NUMBER_TYPE))
        .build();

    FunctionParamBuilder builder = new FunctionParamBuilder(registry);
    builder.addOptionalParams(NUMBER_TYPE);
    builder.addVarArgs(NUMBER_TYPE);
    FunctionType optAndVarNum = new FunctionBuilder(registry)
        .withParamsNode(builder.build())
        .build();

    // We currently do not consider function(T=, ...T) and function(...T)
    // equivalent. This may change.
    assertFalse(varNum.isEquivalentTo(optAndVarNum));
    assertFalse(optAndVarNum.isEquivalentTo(varNum));
  }

  @Test
  public void testRecursiveFunction() {
    ProxyObjectType loop = new ProxyObjectType(registry, NUMBER_TYPE);
    FunctionType fn = new FunctionBuilder(registry)
        .withParamsNode(registry.createParameters(loop))
        .withReturnType(loop).build();

    loop.setReferencedType(fn);
    assertEquals("function(Function): Function", fn.toString());

    Asserts.assertEquivalenceOperations(fn, loop);
  }

  @Test
  public void testBindSignature() {
    FunctionType fn = new FunctionBuilder(registry)
        .withTypeOfThis(DATE_TYPE)
        .withParamsNode(registry.createParameters(STRING_TYPE, NUMBER_TYPE))
        .withReturnType(BOOLEAN_TYPE).build();

    assertEquals(
        "function((Date|null|undefined), string=, number=):" +
        " function(...?): boolean",
        fn.getPropertyType("bind").toString());
  }

  @Test
  public void testCallSignature1() {
    FunctionType fn = new FunctionBuilder(registry)
        .withTypeOfThis(DATE_TYPE)
        .withParamsNode(registry.createParameters(STRING_TYPE, NUMBER_TYPE))
        .withReturnType(BOOLEAN_TYPE).build();

    assertEquals(
        "function((Date|null|undefined), string, number): boolean",
        fn.getPropertyType("call").toString());
  }

  @Test
  public void testCallSignature2() {
    FunctionType fn = new FunctionBuilder(registry)
        .withTypeOfThis(DATE_TYPE)
        .withParamsNode(registry.createParameters())
        .withReturnType(BOOLEAN_TYPE).build();

    assertEquals(
        "function((Date|null)=): boolean",
        fn.getPropertyType("call").toString());
  }

  @Test
  public void testTemplatedFunctionDerivedFunctions() {
    TemplateType template = registry.createTemplateType("T");
    FunctionType fn = new FunctionBuilder(registry)
      .withTypeOfThis(template)
      .withTemplateKeys(ImmutableList.of(template))
      .withReturnType(BOOLEAN_TYPE).build();

    assertEquals("[T]", fn.getPropertyType("call").getTemplateTypeMap()
        .getTemplateKeys().toString());
    assertEquals("[T]", fn.getPropertyType("apply").getTemplateTypeMap()
        .getTemplateKeys().toString());
    assertEquals("[T]", fn.getPropertyType("bind").getTemplateTypeMap()
        .getTemplateKeys().toString());
    assertEquals("[T]", fn.getBindReturnType(0).getTemplateTypeMap()
        .getTemplateKeys().toString());
  }

  @Test
  public void testPrint() {
    FunctionType fn = new FunctionBuilder(registry)
      .withTypeOfThis(new TemplateType(registry, "T"))
      .withReturnType(BOOLEAN_TYPE).build();
    assertEquals("function(this:T, ...?): boolean", fn.toString());
  }

  @Test
  public void testSetImplementsOnInterface() {
    FunctionType iface = registry.createInterfaceType("I", null,
        ImmutableList.<TemplateType>of(), false);
    FunctionType subIface = registry.createInterfaceType("SubI", null,
        ImmutableList.<TemplateType>of(), false);
    try {
      subIface.setImplementedInterfaces(
          ImmutableList.of(iface.getInstanceType()));
      fail("Expected exception");
    } catch (UnsupportedOperationException e) {
      // OK
    }
  }
}
