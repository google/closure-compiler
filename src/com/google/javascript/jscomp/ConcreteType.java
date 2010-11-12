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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionPrototypeType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticScope;
import com.google.javascript.rhino.jstype.StaticSlot;
import com.google.javascript.rhino.jstype.UnknownType;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Represents a reference type for which the exact definition in the source is
 * known.  Unlike a {@code JSType} reference type, a concrete instance type of A
 * indicates that an instance of A -- not a subclass of A -- is a possible
 * value.  Other concrete types are functions (whose definitions are known),
 * arrays containing concrete types, and unions of concrete types.
 *
 * These types are computed by {@code TightenTypes}.
 *
 */
abstract class ConcreteType implements LatticeElement {
  /** Static instance of the empty set of concrete types. */
  static final ConcreteType NONE = new ConcreteNoneType();

  /** Static instance of the set of all concrete types. */
  static final ConcreteType ALL = new ConcreteAll();

  /** Constant empty list of function types. */
  private static final List<ConcreteFunctionType> NO_FUNCTIONS =
      Lists.<ConcreteFunctionType>newArrayList();

  /** Constant empty list of instance types. */
  private static final List<ConcreteInstanceType> NO_INSTANCES =
      Lists.<ConcreteInstanceType>newArrayList();

  /** Constant empty list of slots. */
  private static final List<StaticSlot<ConcreteType>> NO_SLOTS =
      Lists.<StaticSlot<ConcreteType>>newArrayList();

  protected static ConcreteType createForTypes(Collection<ConcreteType> types) {
    if (types == null || types.size() == 0) {
      return NONE;
    } else if (types.size() == 1) {
      return types.iterator().next();
    } else {
      return new ConcreteUnionType(Sets.newHashSet(types));
    }
  }

  /** Indicates whether this is an empty set of types. */
  boolean isNone() { return false; }

  /** Indicates whether this type is a function. */
  boolean isFunction() { return false; }

  /**
   * Indicates whether this type is an instance of some type (or a prototype
   * instance of a type).
   * */
  boolean isInstance() { return false; }

  /** Indicates whether this type is a union of concrete types. */
  boolean isUnion() { return false; }

  /** Indicates whether this type is the set of all types. */
  boolean isAll() { return false; }

  /** Indicates whether this represents exactly one type. */
  boolean isSingleton() { return !isNone() && !isUnion() && !isAll(); }

  /** Returns this as a function, if it is one, or null, if not. */
  ConcreteFunctionType toFunction() { return null; }

  /** Returns this as an instance, if it is one, or null, if not. */
  ConcreteInstanceType toInstance() { return null; }

  /** Returns this as a union, if it is one, or null, if not. */
  ConcreteUnionType toUnion() { return null; }

  /** Returns the scope for the type, or null if not applicable. */
  StaticScope<ConcreteType> getScope() { return null; }

  /** Returns the union of this type with the given one. */
  ConcreteType unionWith(ConcreteType other) {
    Preconditions.checkState(this.isSingleton());  // Sets must override.
    if (!other.isSingleton()) {
      return other.unionWith(this);
    } else if (equals(other)) {
      return this;
    } else {
      return new ConcreteUnionType(this, other);
    }
  }

  /** Returns the intersection of this type with the given one. */
  ConcreteType intersectWith(ConcreteType other) {
    if (!other.isSingleton()) {
      return other.intersectWith(this);
    } else if (equals(other)) {
      return this;
    } else {
      return NONE;
    }
  }

  /**
   * Calls {@code filter()} on each type, adding it to the returned list if it
   * is not null.
   */
  private <C> List<C> getMatchingTypes(TypeFilter<C> filter) {
    C type = null;
    if (isUnion()) {
      List<C> list = Lists.newArrayList();
      for (ConcreteType alt : toUnion().getAlternatives()) {
        if ((type = filter.filter(alt)) != null) {
          list.add(type);
        }
      }
      return list;
    } else if ((type = filter.filter(this)) != null) {
      List<C> list = Lists.newArrayList();
      list.add(type);
      return list;
    } else {
      return filter.emptyList;
    }
  }

  /**
   * Provides one function to filter an input, either returning the filtered
   * version of the input, or null if the input does not have a corresponding
   * output.
   */
  abstract class TypeFilter<C> {
    /** The empty list for a caller to use if there are no non-null outputs. */
    final List<C> emptyList;

    TypeFilter(List<C> emptyList) {
      this.emptyList = emptyList;
    }

    abstract protected C filter(ConcreteType type);
  }

  /** Returns all function types in this set. */
  List<ConcreteFunctionType> getFunctions() {
    return getMatchingTypes(new TypeFilter<ConcreteFunctionType>(NO_FUNCTIONS) {
      @Override public ConcreteFunctionType filter(ConcreteType type) {
        return type.isFunction() ? type.toFunction() : null;
      }
    });
  }

  /** Returns all instance types in this set. */
  List<ConcreteInstanceType> getInstances() {
    return getMatchingTypes(new TypeFilter<ConcreteInstanceType>(NO_INSTANCES) {
      @Override public ConcreteInstanceType filter(ConcreteType type) {
        return type.isInstance() ? type.toInstance() : null;
      }
    });
  }

  /** Returns the (non-null) instance types of all functions in this set. */
  List<ConcreteInstanceType> getFunctionInstanceTypes() {
    return getMatchingTypes(new TypeFilter<ConcreteInstanceType>(NO_INSTANCES) {
      @Override public ConcreteInstanceType filter(ConcreteType type) {
        if (type.isFunction()) {
          return type.toFunction().getInstanceType();
        }
        return null;
      }
    });
  }

  /** Returns all (non-null) function prototype types in this set. */
  List<ConcreteInstanceType> getPrototypeTypes() {
    return getMatchingTypes(new TypeFilter<ConcreteInstanceType>(NO_INSTANCES) {
      @Override public ConcreteInstanceType filter(ConcreteType type) {
        if (type.isInstance()
          && type.toInstance().isFunctionPrototype()) {
          return type.toInstance();
        }
        return null;
      }
    });
  }

  /** Returns the (non-null) superclasses of all functions in this set. */
  List<ConcreteFunctionType> getSuperclassTypes() {
    return getMatchingTypes(new TypeFilter<ConcreteFunctionType>(NO_FUNCTIONS) {
      @Override public ConcreteFunctionType filter(ConcreteType type) {
        return type.isFunction()
          && type.toFunction().getSuperclassType() != null
          ? type.toFunction().getSuperclassType() : null;
      }
    });
  }

  /** Returns the (non-null) index-th parameters of functions in this set. */
  List<StaticSlot<ConcreteType>> getParameterSlots(final int index) {
    return getMatchingTypes(new TypeFilter<StaticSlot<ConcreteType>>(NO_SLOTS) {
      @Override public StaticSlot<ConcreteType> filter(ConcreteType type) {
        return type.isFunction()
            && toFunction().getParameterSlot(index) != null
            ? toFunction().getParameterSlot(index) : null;
      }
    });
  }

  /**
   * Returns the (non-null) slots for properties with the given name in all
   * instance types in this set.
   */
  List<StaticSlot<ConcreteType>> getPropertySlots(final String name) {
    return getMatchingTypes(new TypeFilter<StaticSlot<ConcreteType>>(NO_SLOTS) {
      @Override public StaticSlot<ConcreteType> filter(ConcreteType type) {
        StaticSlot<ConcreteType> slot = null;
        if (type.isInstance()) {
          slot = type.toInstance().getPropertySlot(name);
        }
        return slot;
      }
    });
  }

  /**
   * Returns the concrete type for the given property from the given type.
   * If the given type is a union type, returns the union of types for the slots
   * of the property.
   */
  ConcreteType getPropertyType(final String name) {
    ConcreteType ret = NONE;
    for (StaticSlot<ConcreteType> slot : getPropertySlots(name)) {
      ret = ret.unionWith(slot.getType());
    }
    return ret;
  }

  /** Implements the empty set of types. */
  private static class ConcreteNoneType extends ConcreteType {
    @Override boolean isNone() { return true; }

    @Override ConcreteType unionWith(ConcreteType other) { return other; }

    @Override ConcreteType intersectWith(ConcreteType other) { return NONE; }

    @Override public String toString() { return "()"; }
  }

  /**
   * Represents a specific function in the source code.  Note that we assume the
   * factory creates only a single instance of this class for a given
   * declaration, so we do not need to override {@code Object.equals}.
   *
   * {@code bodyScope} contains a slot for each local variable in the function
   * body's scope as well as special slots to keep track of whether the
   * function is called, the this type, and the return type.
   */
  static class ConcreteFunctionType extends ConcreteType {
    /** Name used for the call slot (see {@code getCallSlot}). */
    static final String CALL_SLOT_NAME = ":call";

    /** Name used for the this slot (see {@code getThisSlot}). */
    static final String THIS_SLOT_NAME = ":this";

    /** Name used for the return slot (see {@code getReturnSlot}). */
    static final String RETURN_SLOT_NAME = ":return";

    private final Factory factory;
    private final Node declaration;
    private final StaticScope<ConcreteType> parentScope;
    private StaticScope<ConcreteType> bodyScope;
    private ConcreteInstanceType instanceType;
    private ConcreteInstanceType prototypeType;

    ConcreteFunctionType(Factory factory,
                         Node declaration,
                         StaticScope<ConcreteType> parentScope) {
      this.factory = factory;
      this.declaration = declaration;
      this.parentScope = parentScope;

      Preconditions.checkArgument(declaration.getType() == Token.FUNCTION);
      Preconditions.checkArgument(declaration.getJSType() != null);
      Preconditions.checkArgument(declaration.getJSType().isFunctionType());
    }

    @Override boolean isFunction() { return true; }

    @Override ConcreteFunctionType toFunction() { return this; }

    /**
     * Returns the slot representing that a call to it occured.  This is
     * assigned a type if the function is called.  This ensures that the body of
     * the function is processed even if it has no arguments or if the arguments
     * do not take any concrete types as arguments.
     */
    StaticSlot<ConcreteType> getCallSlot() {
      return getScope().getOwnSlot(CALL_SLOT_NAME);
    }

    /** Returns the slot representing the value of 'this' in the body. */
    StaticSlot<ConcreteType> getThisSlot() {
      return getScope().getOwnSlot(THIS_SLOT_NAME);
    }

    /** Returns the slot representing the values returned. */
    StaticSlot<ConcreteType> getReturnSlot() {
      return getScope().getOwnSlot(RETURN_SLOT_NAME);
    }

    /** Returns the slot representing the index-th parameter. */
    StaticSlot<ConcreteType> getParameterSlot(int index) {
      return getScope().getOwnSlot(getParameterName(index));
    }

    /** Returns the name for the index-th parameter within the function. */
    private String getParameterName(int index) {
      int count = 0;
      for (Node n = getFirstParameter(); n != null; n = n.getNext()) {
        if (count++ == index) {
          return n.getString();
        }
      }
      return null;
    }

    /** Returns the node containing the first parameter's name. */
    private Node getFirstParameter() {
      return declaration.getFirstChild().getNext().getFirstChild();
    }

    /** Returns the JSType of this function. */
    public FunctionType getJSType() {
      return (FunctionType) declaration.getJSType();
    }

    /**
     * Returns the concrete type representing instances of this type or null if
     * it has none.
     */
    ConcreteInstanceType getInstanceType() {
      if (instanceType == null) {
        if (getJSType().isConstructor()) {
          instanceType =
              factory.createConcreteInstance(getJSType().getInstanceType());
        }
      }
      return instanceType;
    }

    /** Returns the concrete type representing the prototype of this type. */
    ConcreteInstanceType getPrototypeType() {
      if (prototypeType == null) {
        prototypeType =
            factory.createConcreteInstance(getJSType().getPrototype());
      }
      return prototypeType;
    }

    /** Returns the type of the superclass (or null if none exists). */
    ConcreteFunctionType getSuperclassType() {
      FunctionType superConstructor = getJSType().getSuperClassConstructor();
      return (superConstructor != null)
          ? factory.getConcreteFunction(superConstructor) : null;
    }

    /** Returns the scope for the body of this function. */
    @Override StaticScope<ConcreteType> getScope() {
      if (bodyScope == null) {
        bodyScope = factory.createFunctionScope(declaration, parentScope);
      }
      return bodyScope;
    }

    /**
     * Informally, a function is represented by
     * {@code function (params): returnType} where the {@code params} is a comma
     * separated list of types, the first one being a special
     * {@code this:T} if the function expects a known type for {@code this}.
     */
    @Override public String toString() {
      StringBuilder b = new StringBuilder(32);
      b.append("function (");
      boolean hasKnownTypeOfThis = !getThisSlot().getType().isNone();
      if (hasKnownTypeOfThis) {
        b.append("this:");
        b.append(getThisSlot().getType().toString());
      }

      Node n = getFirstParameter();
      if (hasKnownTypeOfThis && n != null) {
        b.append(", ");
      }
      for (int i = 0; n != null; ++i, n = n.getNext()) {
        String paramName = n.getString();
        StaticSlot<ConcreteType> var = getScope().getOwnSlot(paramName);
        b.append(var.getType());
        getParameterSlot(i).getType();
        if (n.getNext() != null) {
          b.append(", ");
        }
      }

      b.append(")");
      if (getReturnSlot().getType() != null) {
        b.append(": ");
        b.append(getReturnSlot().getType().toString());
      }
      return b.toString();
    }
  }

  /**
   * Represents a specific constructor in the source code.  Note that we assume
   * the factory creates only a single instance of this class for a given
   * declaration, so we do not need to override {@code Object.equals}.
   *
   * The {@code StaticScope} contains a slot for each property defined on the
   * instance type and the scope parent chain follows the prototype chain
   * hierarchy.
   */
  static class ConcreteInstanceType extends ConcreteType {
    /** Factory for creating types and scopes. */
    private final Factory factory;

    /** Stores the normal type information for this instance. */
    public final ObjectType instanceType;

    /** The tyep information for the implicit prototype of this type, if any. */
    private ConcreteInstanceType prototype;

    /**
     * A scope containing the properties of this instance, created on demand.
     * Its parent scope corresponds to the scope of the implicit prototype.
     */
    private StaticScope<ConcreteType> scope;

    ConcreteInstanceType(Factory factory, ObjectType instanceType) {
      this.factory = factory;
      this.instanceType = instanceType;

      Preconditions.checkArgument(!(instanceType instanceof UnknownType));
    }

    @Override boolean isInstance() { return true; }

    @Override ConcreteInstanceType toInstance() { return this; }

    /** Determines whether this is a function prototype type. */
    boolean isFunctionPrototype() {
      return instanceType.isFunctionPrototypeType();
    }

    /** Returns the slot representing the property with the given name. */
    StaticSlot<ConcreteType> getPropertySlot(String propName) {
      return getScope().getSlot(propName);
    }

    /**
     * Returns the closest instance type in the prototype chain that contains
     * the given property.
     */
    ConcreteInstanceType getInstanceTypeWithProperty(String propName) {
      if (getScope().getOwnSlot(propName) != null) {
        // Normalize the instance type into the prototype, to be as
        // consistent as possible with non-type tightened behavior.
        //
        // TODO(nicksantos|user): There's a larger issue here.
        // When JSCompiler infers property types on instance types,
        // that means that someone is just assigning a property
        // without declaring it. In this case, we can't meaningfully
        // tell when the property is being pulled off the subtype
        // vs. when it's being pulled off the supertype.  So we should
        // probably invalidate properties of this sort.
        if (instanceType.getConstructor() != null) {
          return getConstructorType().getPrototypeType();
        }
        return this;
      } else if (getImplicitPrototype() != null) {
        return getImplicitPrototype().getInstanceTypeWithProperty(propName);
      } else {
        return null;
      }
    }

    /** Returns the type representing the implicit prototype. */
    ConcreteInstanceType getImplicitPrototype() {
      if ((prototype == null)
          && (instanceType.getImplicitPrototype() != null)) {
        ObjectType proto = instanceType.getImplicitPrototype();
        if ((proto != instanceType) && !(proto instanceof UnknownType)) {
          prototype = factory.createConcreteInstance(proto);
        }
      }
      return prototype;
    }

    /** Returns the type of the constructor or null if this has none. */
    ConcreteFunctionType getConstructorType() {
      if (instanceType.isFunctionPrototypeType()) {
        FunctionPrototypeType protoType = (FunctionPrototypeType) instanceType;
        return factory.getConcreteFunction(protoType.getOwnerFunction());
      } else {
        FunctionType constructor = instanceType.getConstructor();
        return (constructor != null)
            ? factory.getConcreteFunction(constructor) : null;
      }
    }

    /** Returns the scope of this type in the prototype chain. */
    @Override StaticScope<ConcreteType> getScope() {
      if (scope == null) {
        scope = factory.createInstanceScope(instanceType);
      }
      return scope;
    }

    @Override public String toString() { return instanceType.toString(); }
  }

  /**
   * Represents a finite set of possible alternatives for this type.  Note that
   * we make no effort to merge different array types into one array type, so
   * clients should not assume there is only one array in a set.
   */
  static class ConcreteUnionType extends ConcreteType {
    private final Set<ConcreteType> alternatives;

    ConcreteUnionType(ConcreteType... alternatives) {
      this(Sets.newHashSet(alternatives));
    }

    ConcreteUnionType(Set<ConcreteType> alternatives) {
      Preconditions.checkArgument(alternatives.size() > 1);
      this.alternatives = alternatives;
    }

    @Override boolean isUnion() { return true; }

    @Override ConcreteUnionType toUnion() { return this; }

    @Override ConcreteType unionWith(ConcreteType other) {
      if (other.isSingleton()) {
        if (alternatives.contains(other)) {
          return this;
        } else {
          Set<ConcreteType> alts = Sets.newHashSet(alternatives);
          alts.add(other);
          return new ConcreteUnionType(alts);
        }
      } else if (other.isUnion()) {
        ConcreteUnionType otherUnion = other.toUnion();
        if (alternatives.containsAll(otherUnion.alternatives)) {
          return this;
        } else if (otherUnion.alternatives.containsAll(alternatives)) {
          return otherUnion;
        } else {
          Set<ConcreteType> alts = Sets.newHashSet(alternatives);
          alts.addAll(otherUnion.alternatives);
          return new ConcreteUnionType(alts);
        }
      } else {
        Preconditions.checkArgument(other.isNone() || other.isAll());
        return other.unionWith(this);
      }
    }

    @Override ConcreteType intersectWith(ConcreteType other) {
      if (other.isSingleton()) {
        if (alternatives.contains(other)) {
          return other;
        } else {
          return NONE;
        }
      } else if (other.isUnion()) {
        Set<ConcreteType> types = Sets.newHashSet(alternatives);
        types.retainAll(other.toUnion().alternatives);
        return createForTypes(types);
      } else {
        Preconditions.checkArgument(other.isNone() || other.isAll());
        return other.intersectWith(this);
      }
    }

    /** Returns all of the types in this set of alternatives. */
    Set<ConcreteType> getAlternatives() { return alternatives; }

    @Override public boolean equals(Object obj) {
      return (obj instanceof ConcreteUnionType)
             && alternatives.equals(((ConcreteUnionType) obj).alternatives);
    }

    @Override public int hashCode() {
      return alternatives.hashCode() ^ 0x5f6e7d8c;
    }

    @Override public String toString() {
      List<String> names = Lists.newArrayList();
      for (ConcreteType type : alternatives) {
        names.add(type.toString());
      }
      Collections.sort(names);

      return "(" + Joiner.on(",").join(names) + ")";
    }
  }

  /** Implements the set of all concrete types. */
  private static class ConcreteAll extends ConcreteType {
    @Override boolean isAll() { return true; }

    @Override ConcreteType unionWith(ConcreteType other) { return this; }

    @Override ConcreteType intersectWith(ConcreteType other) { return other; }

    @Override public String toString() { return "*"; }
  }

  /**
   * Represents an opaque singleton type that is different from any other.
   * This is used by DisambiguteProperties to rename GETPROP nodes that are
   * never reached in the TightenTypes flow analysis. This helps subsequent
   * passes remove unreferenced properties and functions.  ID passed to the
   * constructor should be unique per-instance as it is used for generating
   * nice, unique, names in {@code toString()}.
   */
  static class ConcreteUniqueType extends ConcreteType {
    private final int id;

    ConcreteUniqueType(int id) {
      this.id = id;

      Preconditions.checkArgument(id >= 0);
    }

    @Override public boolean equals(Object o) {
      return (o instanceof ConcreteUniqueType)
          && (id == ((ConcreteUniqueType) o).id);
    }

    @Override public int hashCode() {
      return ConcreteUniqueType.class.hashCode() ^ id;
    }

    @Override public String toString() { return "Unique$" + id; }
  }

  /**
   * Factory for function and instance (singleton) types and scopes.  It is
   * important that both function and instance types are singletons because
   * callers may try to create the same one multiple times, and if multiple
   * exist, they will not necessarily all receive the same type information.
   */
  interface Factory {
    /** Returns the singleton concrete type for the given function. */
    ConcreteFunctionType createConcreteFunction(
        Node declaration, StaticScope<ConcreteType> parent);

    /** Returns the singleton concrete type for the given instance type. */
    ConcreteInstanceType createConcreteInstance(ObjectType instanceType);

    /**
     * Returns the already created concrete function type for the given type or
     * null if none exists.
     */
    ConcreteFunctionType getConcreteFunction(FunctionType function);

    /**
     * Returns the already created concrete instance type for the given type or
     * null if none exists.
     */
    ConcreteInstanceType getConcreteInstance(ObjectType instance);

    /**
     * Returns a (nested) scope for the given function.  This will include
     * slots for $call, $return, each parameter, and the slots declared in the
     * body of the function.
     */
    StaticScope<ConcreteType> createFunctionScope(
        Node declaration, StaticScope<ConcreteType> parent);

    /**
     * Returns a scope for the given instance type, nested inside the given
     * scope of the prototype.  This will include slots for each of the
     * properties on our type.
     */
    StaticScope<ConcreteType> createInstanceScope(ObjectType instanceType);

    /** Returns the type registry used by this factory. */
    JSTypeRegistry getTypeRegistry();
  }
}
