/*
 * Copyright 2008 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp;

import static com.google.javascript.jscomp.ConcreteType.ALL;
import static com.google.javascript.jscomp.ConcreteType.NONE;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.ConcreteType.ConcreteFunctionType;
import com.google.javascript.jscomp.ConcreteType.ConcreteInstanceType;
import com.google.javascript.jscomp.ConcreteType.ConcreteUnionType;
import com.google.javascript.jscomp.ConcreteType.Factory;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticScope;
import com.google.javascript.rhino.jstype.StaticSlot;
import com.google.javascript.rhino.testing.TestErrorReporter;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;


/**
 * Unit test for the the subclasses of ConcreteType.
 *
 */
public class ConcreteTypeTest extends TestCase {
  private JSTypeRegistry typeRegistry;
  private JSType unknownType;
  private Factory factory;

  @Override
  public void setUp() {
    typeRegistry = new JSTypeRegistry(new TestErrorReporter(null, null));
    unknownType = typeRegistry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
    factory = new FakeFactory();
  }

  private void checkEquality(List<ConcreteType> types) {
    for (int i = 0; i < types.size(); ++i) {
      for (int j = 0; j < types.size(); ++j) {
        if (i == j) {
          assertEquals(types.get(i), types.get(j));
        } else {
          assertFalse(types.get(i).equals(types.get(j)));
        }
      }
    }
  }

  public void testEquals() {
    ConcreteFunctionType fun1 = createFunction("fun1");
    ConcreteFunctionType fun2 = createFunction("fun2");
    ConcreteType obj1 = fun1.getInstanceType();
    ConcreteType obj2 = fun2.getInstanceType();
    ConcreteType union1 = new ConcreteUnionType(fun1, fun2);
    ConcreteType union2 = new ConcreteUnionType(fun1, obj1);
    ConcreteType union3 = new ConcreteUnionType(fun1, obj1);

    checkEquality(Lists.newArrayList(fun1, fun2, obj1, obj2,
                                     union1, union2));

    assertEquals(union2, union3);
  }

  public void testUnionWith() {
    ConcreteFunctionType fun = createFunction("fun");
    ConcreteType obj = fun.getInstanceType();
    ConcreteType both = new ConcreteUnionType(fun, obj);

    assertTrue(fun.isSingleton());
    assertTrue(obj.isSingleton());
    assertFalse(both.isSingleton());
    assertFalse(NONE.isSingleton());
    assertFalse(ALL.isSingleton());

    checkUnionWith(fun, NONE, fun);
    checkUnionWith(fun, ALL, ALL);

    checkUnionWith(fun, obj, both);
    checkUnionWith(both, NONE, both);
    checkUnionWith(both, ALL, ALL);
  }

  private void checkUnionWith(ConcreteType a, ConcreteType b, ConcreteType c) {
    assertEquals(a, a.unionWith(a));
    assertEquals(b, b.unionWith(b));
    assertEquals(c, a.unionWith(b));
    assertEquals(c, b.unionWith(a));
  }

  public void testIntersectionWith() {
    ConcreteFunctionType fun = createFunction("fun");
    ConcreteFunctionType fun2 = createFunction("fun2");
    ConcreteType obj = fun.getInstanceType();
    ConcreteType both = new ConcreteUnionType(fun, obj);

    assertEquals(NONE, fun.intersectWith(obj));
    assertEquals(NONE, obj.intersectWith(fun));

    assertEquals(fun, both.intersectWith(fun));
    assertEquals(fun, fun.intersectWith(both));

    assertEquals(NONE, NONE.intersectWith(both));
    assertEquals(NONE, both.intersectWith(NONE));
    assertEquals(NONE, fun.intersectWith(NONE));
    assertEquals(NONE, NONE.intersectWith(fun));

    assertEquals(NONE, both.intersectWith(fun2));

    assertEquals(both, ALL.intersectWith(both));
    assertEquals(both, both.intersectWith(ALL));
    assertEquals(fun, ALL.intersectWith(fun));
    assertEquals(fun, fun.intersectWith(ALL));
    assertEquals(NONE, ALL.intersectWith(NONE));
    assertEquals(NONE, NONE.intersectWith(ALL));
  }

  public void testFunction() {
    ConcreteFunctionType fun = createFunction("fun", "a", "b");
    assertTrue(fun.isFunction());
    assertNotNull(fun.getCallSlot());
    assertNotNull(fun.getReturnSlot());
    assertNotNull(fun.getParameterSlot(0));
    assertNotNull(fun.getParameterSlot(1));
    assertNull(fun.getParameterSlot(2));
    assertTrue(fun.getInstanceType().isInstance());
  }

  public void testInstance() {
    ConcreteInstanceType obj = createInstance("MyObj", "a", "b");
    assertTrue(obj.isInstance());
    assertNotNull(obj.getPropertySlot("a"));
    assertNotNull(obj.getPropertySlot("b"));
    assertNull(obj.getPropertySlot("c"));

    // The prototype chain should be: MyObj -> MyObj.prototype -> Object ->
    // Object.prototype -> {...}.prototype -> null.
    for (int i = 0; i < 4; ++i) {
      assertNotNull(obj = obj.getImplicitPrototype());
      assertTrue(obj.isInstance());
    }
    assertNull(obj.getImplicitPrototype());
  }

  public void testGetX() {
    ConcreteFunctionType fun1 = createFunction("fun1");
    ConcreteFunctionType fun2 = createFunction("fun2");
    ConcreteInstanceType obj1 = fun1.getInstanceType();
    ConcreteInstanceType obj2 = fun2.getInstanceType();
    ConcreteType union1 = fun1.unionWith(obj1);
    ConcreteType union2 =
        union1.unionWith(fun2).unionWith(obj2);

    assertEqualSets(Lists.newArrayList(), NONE.getFunctions());
    assertEqualSets(Lists.newArrayList(), NONE.getInstances());
    assertEqualSets(Lists.newArrayList(fun1), fun1.getFunctions());
    assertEqualSets(Lists.newArrayList(), fun1.getInstances());
    assertEqualSets(Lists.newArrayList(), obj1.getFunctions());
    assertEqualSets(Lists.newArrayList(obj1), obj1.getInstances());

    assertEqualSets(Lists.newArrayList(fun1), union1.getFunctions());
    assertEqualSets(Lists.newArrayList(obj1), union1.getInstances());

    assertEqualSets(Lists.newArrayList(fun1, fun2), union2.getFunctions());
    assertEqualSets(Lists.newArrayList(obj1, obj2), union2.getInstances());
  }

  /** Checks that the two collections are equal as sets. */
  private void assertEqualSets(Collection<?> first, Collection<?> second) {
    assertEquals(Sets.newHashSet(first), Sets.newHashSet(second));
  }

  /** Creates a fake function with the given description. */
  private ConcreteFunctionType createFunction(
      String name, String... paramNames) {
    Node args = new Node(Token.LP);
    for (int i = 0; i < paramNames.length; ++i) {
      args.addChildToBack(Node.newString(Token.NAME, paramNames[i]));
    }

    Node decl = new Node(Token.FUNCTION,
                         Node.newString(Token.NAME, name),
                         args,
                         new Node(Token.BLOCK));

    JSType[] paramTypes = new JSType[paramNames.length];
    Arrays.fill(paramTypes, unknownType);
    decl.setJSType(
        typeRegistry.createConstructorType(name, decl, args, unknownType));

    return new ConcreteFunctionType(factory, decl, null);
  }

  /** Creates a fake instance with the given description. */
  private ConcreteInstanceType createInstance(
      String name, String... propNames) {
    ObjectType objType = typeRegistry.createObjectType(name, null,
        typeRegistry.createObjectType(name + ".prototype", null, null));
    for (int i = 0; i < propNames.length; ++i) {
      objType.defineDeclaredProperty(propNames[i], unknownType, false, null);
    }
    return new ConcreteInstanceType(factory, objType);
  }

  private class FakeFactory implements Factory {
    private final Map<Node, ConcreteFunctionType> functionByDeclaration =
        Maps.newHashMap();
    private final Map<FunctionType, ConcreteFunctionType> functionByJSType =
        Maps.newHashMap();
    private final Map<ObjectType, ConcreteInstanceType> instanceByJSType =
        Maps.newHashMap();

    private final JSTypeRegistry registry = new JSTypeRegistry(
        new TestErrorReporter(null, null));

    public JSTypeRegistry getTypeRegistry() {
      return registry;
    }

    /** {@inheritDoc} */
    public ConcreteFunctionType createConcreteFunction(
        Node decl, StaticScope<ConcreteType> parent) {
      ConcreteFunctionType funcType = functionByDeclaration.get(decl);
      if (funcType == null) {
        functionByDeclaration.put(decl, funcType =
            new ConcreteFunctionType(this, decl, parent));
        if (decl.getJSType() != null) {
          functionByJSType.put((FunctionType) decl.getJSType(), funcType);
        }
      }
      return funcType;
    }

    /** {@inheritDoc} */
    public ConcreteInstanceType createConcreteInstance(
        ObjectType instanceType) {
      ConcreteInstanceType instType = instanceByJSType.get(instanceType);
      if (instType == null) {
        instanceByJSType.put(instanceType,
            instType = new ConcreteInstanceType(this, instanceType));
      }
      return instType;
    }

    /** {@inheritDoc} */
    public ConcreteFunctionType getConcreteFunction(FunctionType functionType) {
      return functionByJSType.get(functionType);
    }

    /** {@inheritDoc} */
    public ConcreteInstanceType getConcreteInstance(ObjectType instanceType) {
      return instanceByJSType.get(instanceType);
    }

    /** {@inheritDoc} */
    public StaticScope<ConcreteType> createFunctionScope(
        Node decl, StaticScope<ConcreteType> parent) {
      FakeScope scope = new FakeScope((FakeScope) parent);
      scope.addSlot(ConcreteFunctionType.CALL_SLOT_NAME);
      scope.addSlot(ConcreteFunctionType.THIS_SLOT_NAME);
      scope.addSlot(ConcreteFunctionType.RETURN_SLOT_NAME);
      for (Node n = decl.getFirstChild().getNext().getFirstChild();
           n != null;
           n = n.getNext()) {
        scope.addSlot(n.getString());
      }
      return scope;
    }

    /** {@inheritDoc} */
    public StaticScope<ConcreteType> createInstanceScope(
        ObjectType instanceType) {
      FakeScope parentScope = null;
      if (instanceType.getImplicitPrototype() != null) {
        ConcreteInstanceType prototype =
            createConcreteInstance(instanceType.getImplicitPrototype());
        parentScope = (FakeScope) prototype.getScope();
      }

      FakeScope scope = new FakeScope(parentScope);
      for (String propName : instanceType.getOwnPropertyNames()) {
        scope.addSlot(propName);
      }
      return scope;
    }
  }

  // TODO(user): move to a common place if it can be used elsewhere
  private class FakeScope implements StaticScope<ConcreteType> {
    private final FakeScope parent;
    private final Map<String, FakeSlot> slots = Maps.newHashMap();

    FakeScope(FakeScope parent) {
      this.parent = parent;
    }

    /** {@inheritDoc} */
    public StaticScope<ConcreteType> getParentScope() { return parent; }

    /** {@inheritDoc} */
    public StaticSlot<ConcreteType> getOwnSlot(String name) {
      return slots.get(name);
    }

    /** {@inheritDoc} */
    public StaticSlot<ConcreteType> getSlot(String name) {
      if (slots.containsKey(name)) {
        return slots.get(name);
      } else if (parent != null) {
        return parent.getSlot(name);
      } else {
        return null;
      }
    }

    /** {@inheritDoc} */
    public ConcreteType getTypeOfThis() { return ConcreteType.ALL; }

    void addSlot(String name) {
      slots.put(name, new FakeSlot(name));
    }
  }

  // TODO(user): move to a common place if it can be used elsewhere
  private class FakeSlot implements StaticSlot<ConcreteType> {
    private final String name;

    FakeSlot(String name) {
      this.name = name;
    }

    /* {@inheritDoc} */
    public String getName() { return name; }

    /* {@inheritDoc} */
    public ConcreteType getType() { return ConcreteType.ALL; }

    /* {@inheritDoc} */
    public boolean isTypeInferred() { return true; }
  }
}
