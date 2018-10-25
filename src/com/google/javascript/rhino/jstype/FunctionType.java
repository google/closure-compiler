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
 *   Bob Jervis
 *   Google Inc.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.U2U_CONSTRUCTOR_TYPE;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.javascript.rhino.CyclicSerializableLinkedHashSet;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType.EqCache;
import com.google.javascript.rhino.jstype.JSType.SubtypingMode;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * This derived type provides extended information about a function, including its return type and
 * argument types.
 *
 * <p>Note: the parameters list is the PARAM_LIST node that is the parent of the actual NAME node
 * containing the parsed argument list (annotated with JSDOC_TYPE_PROP's for the compile-time type
 * of each argument.
 *
 */
public class FunctionType extends PrototypeObjectType implements Serializable {
  private static final long serialVersionUID = 1L;

  enum Kind {
    ORDINARY,
    CONSTRUCTOR,
    INTERFACE
  }

  // relevant only for constructors
  private enum PropAccess {
    ANY,
    STRUCT,
    DICT
  }

  enum ConstructorAmbiguity {
    UNKNOWN,
    CONSTRUCTS_AMBIGUOUS_OBJECTS,
    CONSTRUCTS_UNAMBIGUOUS_OBJECTS
  }

  private ConstructorAmbiguity constructorAmbiguity = ConstructorAmbiguity.UNKNOWN;

  /** {@code [[Call]]} property. */
  private ArrowType call;

  /**
   * The {@code prototype} property. This field is lazily initialized by {@code #getPrototype()}.
   * The most important reason for lazily initializing this field is that there are cycles in the
   * native types graph, so some prototypes must temporarily be {@code null} during the construction
   * of the graph.
   *
   * <p>If non-null, the type must be a PrototypeObjectType.
   */
  private Property prototypeSlot;

  /** Whether a function is a constructor, an interface, or just an ordinary function. */
  private final Kind kind;

  /** Whether the instances are structs, dicts, or unrestricted. */
  private PropAccess propAccess;

  /** The type of {@code this} in the scope of this function. */
  private JSType typeOfThis;

  /** The function node which this type represents. It may be {@code null}. */
  private Node source;

  /** if this is an interface, indicate whether or not it supports structural interface matching */
  private boolean isStructuralInterface;

  /**
   * If true, the function type represents an abstract method or the constructor of an abstract
   * class
   */
  private final boolean isAbstract;

  /**
   * The interfaces directly implemented by this function (for constructors) It is only relevant for
   * constructors. May not be {@code null}.
   */
  private ImmutableList<ObjectType> implementedInterfaces = ImmutableList.of();

  /**
   * The interfaces directly extended by this function (for interfaces) It is only relevant for
   * constructors. May not be {@code null}.
   */
  private ImmutableList<ObjectType> extendedInterfaces = ImmutableList.of();

  /**
   * For the instance type of this ctor, the ctor types of all known subtypes of that instance type.
   *
   * <p>This field is only applicable to ctor functions.
   */
  @Nullable
  // @MonotonicNonNull
  private CyclicSerializableLinkedHashSet<FunctionType> knownSubtypeCtors;

  /** Creates an instance for a function that might be a constructor. */
  FunctionType(
      JSTypeRegistry registry,
      String name,
      Node source,
      ArrowType arrowType,
      JSType typeOfThis,
      TemplateTypeMap templateTypeMap,
      Kind kind,
      boolean nativeType,
      boolean isAbstract) {
    super(
        registry,
        name,
        registry.getNativeObjectType(JSTypeNative.FUNCTION_INSTANCE_TYPE),
        nativeType,
        templateTypeMap);
    setPrettyPrint(true);

    checkArgument(source == null || source.isFunction() || source.isClass());
    checkNotNull(arrowType);
    this.source = source;
    this.kind = kind;
    switch (kind) {
      case CONSTRUCTOR:
        this.propAccess = PropAccess.ANY;
        this.typeOfThis =
            typeOfThis != null ? typeOfThis : new InstanceObjectType(registry, this, nativeType);
        break;
      case ORDINARY:
        this.typeOfThis =
            typeOfThis != null
                ? typeOfThis
                : registry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE);
        break;
      case INTERFACE:
        this.typeOfThis =
            typeOfThis != null ? typeOfThis : new InstanceObjectType(registry, this, nativeType);
        break;
    }
    this.call = arrowType;
    this.isStructuralInterface = false;
    this.isAbstract = isAbstract;
  }

  @Override
  public final boolean isInstanceType() {
    // The universal constructor is its own instance, bizarrely. It overrides
    // getConstructor() appropriately when it's declared.
    return this == registry.getNativeType(U2U_CONSTRUCTOR_TYPE);
  }

  @Override
  public boolean isConstructor() {
    return kind == Kind.CONSTRUCTOR;
  }

  @Override
  public final boolean isInterface() {
    return kind == Kind.INTERFACE;
  }

  @Override
  public final boolean isOrdinaryFunction() {
    return kind == Kind.ORDINARY;
  }

  final Kind getKind() {
    return kind;
  }

  /**
   * When a class B inherits from A and A is annotated as a struct, then B automatically gets the
   * annotation, even if B's constructor is not explicitly annotated.
   */
  public final boolean makesStructs() {
    if (!hasInstanceType()) {
      return false;
    }
    if (propAccess == PropAccess.STRUCT) {
      return true;
    }
    FunctionType superc = getSuperClassConstructor();
    if (superc != null && superc.makesStructs()) {
      setStruct();
      return true;
    }
    return false;
  }

  /**
   * When a class B inherits from A and A is annotated as a dict, then B automatically gets the
   * annotation, even if B's constructor is not explicitly annotated.
   */
  public final boolean makesDicts() {
    if (!isConstructor()) {
      return false;
    }
    if (propAccess == PropAccess.DICT) {
      return true;
    }
    FunctionType superc = getSuperClassConstructor();
    if (superc != null && superc.makesDicts()) {
      setDict();
      return true;
    }
    return false;
  }

  public final void setStruct() {
    propAccess = PropAccess.STRUCT;
  }

  public final void setDict() {
    propAccess = PropAccess.DICT;
  }

  @Override
  public FunctionType toMaybeFunctionType() {
    return this;
  }

  @Override
  public final boolean canBeCalled() {
    return true;
  }

  public final boolean hasImplementedInterfaces() {
    if (!implementedInterfaces.isEmpty()) {
      return true;
    }
    FunctionType superCtor = isConstructor() ? getSuperClassConstructor() : null;
    if (superCtor != null) {
      return superCtor.hasImplementedInterfaces();
    }
    return false;
  }

  public final Iterable<Node> getParameters() {
    Node n = getParametersNode();
    if (n != null) {
      return n.children();
    } else {
      return Collections.emptySet();
    }
  }

  public final Iterable<JSType> getParameterTypes() {
    List<JSType> types = new ArrayList<>();
    for (Node n : getParameters()) {
      types.add(n.getJSType());
    }
    return types;
  }

  /** Gets a PARAM_LIST node that contains all params. May be null. */
  public final Node getParametersNode() {
    return call.parameters;
  }

  /** Gets the minimum number of arguments that this function requires. */
  public final int getMinArity() {
    // NOTE(nicksantos): There are some native functions that have optional
    // parameters before required parameters. This algorithm finds the position
    // of the last required parameter.
    int i = 0;
    int min = 0;
    for (Node n : getParameters()) {
      i++;
      if (!n.isOptionalArg() && !n.isVarArgs()) {
        min = i;
      }
    }
    return min;
  }

  /**
   * Gets the maximum number of arguments that this function requires, or Integer.MAX_VALUE if this
   * is a variable argument function.
   */
  public final int getMaxArity() {
    Node params = getParametersNode();
    if (params != null) {
      Node lastParam = params.getLastChild();
      if (lastParam == null || !lastParam.isVarArgs()) {
        return params.getChildCount();
      }
    }

    return Integer.MAX_VALUE;
  }

  public final JSType getReturnType() {
    return call.returnType;
  }

  public final boolean isReturnTypeInferred() {
    return call.returnTypeInferred;
  }

  /** Gets the internal arrow type. For use by subclasses only. */
  final ArrowType getInternalArrowType() {
    return call;
  }

  @Override
  public final Property getSlot(String name) {
    if ("prototype".equals(name)) {
      // Lazy initialization of the prototype field.
      getPrototype();
      return prototypeSlot;
    } else {
      return super.getSlot(name);
    }
  }

  /**
   * Includes the prototype iff someone has created it. We do not want to expose the prototype for
   * ordinary functions.
   */
  @Override
  public final Set<String> getOwnPropertyNames() {
    if (prototypeSlot == null) {
      return super.getOwnPropertyNames();
    } else {
      ImmutableSet.Builder<String> names = ImmutableSet.builder();
      names.add("prototype");
      names.addAll(super.getOwnPropertyNames());
      return names.build();
    }
  }

  public final ObjectType getPrototypeProperty() {
    return getPrototype();
  }

  /**
   * Gets the {@code prototype} property of this function type. This is equivalent to {@code
   * (ObjectType) getPropertyType("prototype")}.
   */
  public final ObjectType getPrototype() {
    // lazy initialization of the prototype field
    if (prototypeSlot == null) {
      String refName = getReferenceName();
      if (refName == null) {
        // Someone is trying to access the prototype of a structural function.
        // We don't want to give real properties to this prototype, because
        // then it would propagate to all structural functions.
        setPrototypeNoCheck(registry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE), null);
      } else {
        setPrototype(
            new PrototypeObjectType(
                registry,
                getReferenceName() + ".prototype",
                registry.getNativeObjectType(OBJECT_TYPE),
                isNativeObjectType(),
                null),
            null);
      }
    }
    return (ObjectType) prototypeSlot.getType();
  }

  /**
   * Sets the prototype, creating the prototype object from the given base type.
   *
   * @param baseType The base type.
   */
  public final void setPrototypeBasedOn(ObjectType baseType) {
    setPrototypeBasedOn(baseType, null);
  }

  final void setPrototypeBasedOn(ObjectType baseType, Node propertyNode) {
    // First handle class-side inheritance for ES6 classes, before reassigning baseType.
    if (source != null && source.isClass()) {
      FunctionType superCtor = baseType.getConstructor();
      if (superCtor != null) {
        setImplicitPrototype(superCtor);
      }
    }
    // This is a bit weird. We need to successfully handle these
    // two cases:
    // Foo.prototype = new Bar();
    // and
    // Foo.prototype = {baz: 3};
    // In the first case, we do not want new properties to get
    // added to Bar. In the second case, we do want new properties
    // to get added to the type of the anonymous object.
    //
    // We handle this by breaking it into two cases:
    //
    // In the first case, we create a new PrototypeObjectType and set
    // its implicit prototype to the type being assigned. This ensures
    // that Bar will not get any properties of Foo.prototype, but properties
    // later assigned to Bar will get inherited properly.
    //
    // In the second case, we just use the anonymous object as the prototype.
    if (baseType.hasReferenceName() || isNativeObjectType() || baseType.isFunctionPrototypeType()) {
      if (prototypeSlot != null && hasInstanceType() && baseType.equals(getInstanceType())) {
        // Bail out for cases like Foo.prototype = new Foo();
        return;
      }
      baseType = new PrototypeObjectType(registry, getReferenceName() + ".prototype", baseType);
    }
    setPrototype(baseType, propertyNode);
  }

  /**
   * Extends the TemplateTypeMap of the function's this type, based on the specified type.
   *
   * @param type
   */
  public final void extendTemplateTypeMapBasedOn(ObjectType type) {
    typeOfThis.extendTemplateTypeMap(type.getTemplateTypeMap());
  }

  /**
   * Sets the prototype.
   *
   * @param prototype the prototype. If this value is {@code null} it will silently be discarded.
   */
  final boolean setPrototype(ObjectType prototype, Node propertyNode) {
    if (prototype == null) {
      return false;
    }
    // getInstanceType fails if the function is not a constructor
    if (isConstructor() && prototype == getInstanceType()) {
      return false;
    }
    return setPrototypeNoCheck(prototype, propertyNode);
  }

  /** Set the prototype without doing any sanity checks. */
  private boolean setPrototypeNoCheck(ObjectType prototype, Node propertyNode) {
    ObjectType oldPrototype = prototypeSlot == null ? null : (ObjectType) prototypeSlot.getType();
    boolean replacedPrototype = oldPrototype != null;

    this.prototypeSlot =
        new Property("prototype", prototype, true, propertyNode == null ? source : propertyNode);
    prototype.setOwnerFunction(this);

    if (oldPrototype != null) {
      // Disassociating the old prototype makes this easier to debug--
      // we don't have to worry about two prototypes running around.
      oldPrototype.setOwnerFunction(null);
    }

    if (isConstructor() || isInterface()) {
      FunctionType superClass = getSuperClassConstructor();
      if (superClass != null) {
        superClass.addSubTypeIfNotPresent(this);
      }

      if (isInterface()) {
        for (ObjectType interfaceType : getExtendedInterfaces()) {
          if (interfaceType.getConstructor() != null) {
            interfaceType.getConstructor().addSubTypeIfNotPresent(this);
          }
        }
      }
    }

    if (replacedPrototype) {
      clearCachedValues();
    }

    return true;
  }

  /**
   * Returns all interfaces implemented by a class or its superclass and any superclasses for any of
   * those interfaces. If this is called before all types are resolved, it may return an incomplete
   * set.
   */
  public final Iterable<ObjectType> getAllImplementedInterfaces() {
    // Store them in a linked hash set, so that the compile job is
    // deterministic.
    Set<ObjectType> interfaces = new LinkedHashSet<>();

    for (ObjectType type : getImplementedInterfaces()) {
      addRelatedInterfaces(type, interfaces);
    }
    return interfaces;
  }

  private void addRelatedInterfaces(ObjectType instance, Set<ObjectType> set) {
    FunctionType constructor = instance.getConstructor();
    if (constructor != null) {
      if (!constructor.isInterface()) {
        return;
      }

      if (!set.add(instance)) {
        return;
      }

      for (ObjectType interfaceType : instance.getCtorExtendedInterfaces()) {
        addRelatedInterfaces(interfaceType, set);
      }
    }
  }

  public final Collection<ObjectType> getAncestorInterfaces() {
    Set<ObjectType> result = new HashSet<>();
    if (isConstructor()) {
      result.addAll((Collection<? extends ObjectType>) getImplementedInterfaces());
    } else {
      result.addAll((Collection<? extends ObjectType>) getExtendedInterfaces());
    }
    return result;
  }

  /** Returns interfaces implemented directly by a class or its superclass. */
  public final ImmutableList<ObjectType> getImplementedInterfaces() {
    FunctionType superCtor = isConstructor() ? getSuperClassConstructor() : null;
    if (superCtor == null) {
      return implementedInterfaces;
    }
    ImmutableList.Builder<ObjectType> builder = ImmutableList.builder();
    builder.addAll(implementedInterfaces);
    while (superCtor != null) {
      builder.addAll(superCtor.implementedInterfaces);
      superCtor = superCtor.getSuperClassConstructor();
    }
    return builder.build();
  }

  /** Returns interfaces directly implemented by the class. */
  public final ImmutableList<ObjectType> getOwnImplementedInterfaces() {
    return implementedInterfaces;
  }

  public final void setImplementedInterfaces(List<ObjectType> implementedInterfaces) {
    if (isConstructor()) {
      // Records this type for each implemented interface.
      for (ObjectType type : implementedInterfaces) {
        registry.registerTypeImplementingInterface(this, type);
        typeOfThis.extendTemplateTypeMap(type.getTemplateTypeMap());
      }
      this.implementedInterfaces = ImmutableList.copyOf(implementedInterfaces);
    } else {
      throw new UnsupportedOperationException("An interface cannot implement other inferfaces");
    }
  }

  /** Returns interfaces directly extended by an interface */
  public final ImmutableList<ObjectType> getExtendedInterfaces() {
    return extendedInterfaces;
  }

  /** Returns the number of interfaces directly extended by an interface */
  public final int getExtendedInterfacesCount() {
    return extendedInterfaces.size();
  }

  public final void setExtendedInterfaces(List<ObjectType> extendedInterfaces) {
    if (isInterface()) {
      this.extendedInterfaces = ImmutableList.copyOf(extendedInterfaces);
      for (ObjectType extendedInterface : this.extendedInterfaces) {
        typeOfThis.extendTemplateTypeMap(extendedInterface.getTemplateTypeMap());
      }
    } else {
      throw new UnsupportedOperationException();
    }
  }

  @Override
  public final JSType getPropertyType(String name) {
    if (!hasOwnProperty(name)) {
      // Define the "call", "apply", and "bind" functions lazily.
      boolean isCall = "call".equals(name);
      boolean isBind = "bind".equals(name);
      if (isCall || isBind) {
        defineDeclaredProperty(name, getCallOrBindSignature(isCall), source);
      } else if ("apply".equals(name)) {
        // Define the "apply" function lazily.
        FunctionParamBuilder builder = new FunctionParamBuilder(registry);

        // ECMA-262 says that apply's second argument must be an Array
        // or an arguments object. We don't model the arguments object,
        // so let's just be forgiving for now.
        // TODO(nicksantos): Model the Arguments object.
        builder.addOptionalParams(
            registry.createNullableType(getTypeOfThis()),
            registry.createNullableType(registry.getNativeType(JSTypeNative.OBJECT_TYPE)));

        defineDeclaredProperty(
            name,
            new FunctionBuilder(registry)
                .withParamsNode(builder.build())
                .withReturnType(getReturnType())
                .withTemplateKeys(getTemplateTypeMap().getTemplateKeys())
                .build(),
            source);
      }
    }

    return super.getPropertyType(name);
  }

  /**
   * Get the return value of calling "bind" on this function with the specified number of arguments.
   *
   * <p>If -1 is passed, then we will return a result that accepts any parameters.
   */
  public final FunctionType getBindReturnType(int argsToBind) {
    FunctionBuilder builder =
        new FunctionBuilder(registry)
            .withReturnType(getReturnType())
            .withTemplateKeys(getTemplateTypeMap().getTemplateKeys());
    if (argsToBind >= 0) {
      Node origParams = getParametersNode();
      if (origParams != null) {
        Node params = origParams.cloneTree();
        for (int i = 1; i < argsToBind && params.getFirstChild() != null; i++) {
          if (params.getFirstChild().isVarArgs()) {
            break;
          }
          params.removeFirstChild();
        }
        builder.withParamsNode(params);
      }
    }
    return builder.build();
  }

  /**
   * Notice that "call" and "bind" have the same argument signature, except that all the arguments
   * of "bind" (except the first) are optional.
   */
  private FunctionType getCallOrBindSignature(boolean isCall) {
    boolean isBind = !isCall;
    FunctionBuilder builder =
        new FunctionBuilder(registry)
            .withReturnType(isCall ? getReturnType() : getBindReturnType(-1))
            .withTemplateKeys(getTemplateTypeMap().getTemplateKeys());

    Node origParams = getParametersNode();
    if (origParams != null) {
      Node params = origParams.cloneTree();

      Node thisTypeNode = Node.newString(Token.NAME, "thisType");
      thisTypeNode.setJSType(registry.createOptionalNullableType(getTypeOfThis()));
      params.addChildToFront(thisTypeNode);

      if (isBind) {
        // The arguments of bind() are unique in that they are all
        // optional but not undefinable.
        for (Node current = thisTypeNode.getNext(); current != null; current = current.getNext()) {
          current.setOptionalArg(true);
        }
      } else if (isCall) {
        // The first argument of call() is optional iff all the arguments
        // are optional. It's sufficient to check the first argument.
        Node firstArg = thisTypeNode.getNext();
        if (firstArg == null || firstArg.isOptionalArg() || firstArg.isVarArgs()) {
          thisTypeNode.setOptionalArg(true);
        }
      }

      builder.withParamsNode(params);
    }

    return builder.build();
  }

  @Override
  boolean defineProperty(String name, JSType type, boolean inferred, Node propertyNode) {
    if ("prototype".equals(name)) {
      ObjectType objType = type.toObjectType();
      if (objType != null) {
        if (prototypeSlot != null && objType.isEquivalentTo(prototypeSlot.getType())) {
          return true;
        }
        setPrototypeBasedOn(objType, propertyNode);
        return true;
      } else {
        return false;
      }
    }
    return super.defineProperty(name, type, inferred, propertyNode);
  }

  /**
   * Computes the supremum or infimum of two functions. Because sup() and inf() share a lot of logic
   * for functions, we use a single helper.
   *
   * @param leastSuper If true, compute the supremum of {@code this} with {@code that}. Otherwise,
   *     compute the infimum.
   * @return The least supertype or greatest subtype.
   */
  final FunctionType supAndInfHelper(FunctionType that, boolean leastSuper) {
    // NOTE(nicksantos): When we remove the unknown type, the function types
    // form a lattice with the universal constructor at the top of the lattice,
    // and the LEAST_FUNCTION_TYPE type at the bottom of the lattice.
    //
    // When we introduce the unknown type, it's much more difficult to make
    // heads or tails of the partial ordering of types, because there's no
    // clear hierarchy between the different components (parameter types and
    // return types) in the ArrowType.
    //
    // Rather than make the situation more complicated by introducing new
    // types (like unions of functions), we just fallback on the simpler
    // approach of getting things right at the top and the bottom of the
    // lattice.
    //
    // If there are unknown parameters or return types making things
    // ambiguous, then sup(A, B) is always the top function type, and
    // inf(A, B) is always the bottom function type.
    checkNotNull(that);

    if (isEquivalentTo(that)) {
      return this;
    }

    // If these are ordinary functions, then merge them.
    // Don't do this if any of the params/return
    // values are unknown, because then there will be cycles in
    // their local lattice and they will merge in weird ways.
    if (isOrdinaryFunction()
        && that.isOrdinaryFunction()
        && !this.call.hasUnknownParamsOrReturn()
        && !that.call.hasUnknownParamsOrReturn()) {

      // Check for the degenerate case, but double check
      // that there's not a cycle.
      boolean isSubtypeOfThat = isSubtype(that);
      boolean isSubtypeOfThis = that.isSubtype(this);
      if (isSubtypeOfThat && !isSubtypeOfThis) {
        return leastSuper ? that : this;
      } else if (isSubtypeOfThis && !isSubtypeOfThat) {
        return leastSuper ? this : that;
      }

      // Merge the two functions component-wise.
      FunctionType merged = tryMergeFunctionPiecewise(that, leastSuper);
      if (merged != null) {
        return merged;
      }
    }

    // The function instance type is a special case
    // that lives above the rest of the lattice.
    JSType functionInstance = registry.getNativeType(JSTypeNative.FUNCTION_INSTANCE_TYPE);
    if (functionInstance.isEquivalentTo(that)) {
      return leastSuper ? that : this;
    } else if (functionInstance.isEquivalentTo(this)) {
      return leastSuper ? this : that;
    }

    // In theory, we should be using the GREATEST_FUNCTION_TYPE as the
    // greatest function. In practice, we don't because it's way too
    // broad. The greatest function takes var_args None parameters, which
    // means that all parameters register a type warning.
    //
    // Instead, we use the U2U ctor type, which has unknown type args.
    FunctionType greatestFn = registry.getNativeFunctionType(JSTypeNative.U2U_CONSTRUCTOR_TYPE);
    FunctionType leastFn = registry.getNativeFunctionType(JSTypeNative.LEAST_FUNCTION_TYPE);
    return leastSuper ? greatestFn : leastFn;
  }

  /** Try to get the sup/inf of two functions by looking at the piecewise components. */
  private FunctionType tryMergeFunctionPiecewise(FunctionType other, boolean leastSuper) {
    Node newParamsNode = null;
    if (call.hasEqualParameters(other.call, EquivalenceMethod.IDENTITY, EqCache.create())) {
      newParamsNode = call.parameters;
    } else {
      // If the parameters are not equal, don't try to merge them.
      // Someday, we should try to merge the individual params.
      return null;
    }

    JSType newReturnType =
        leastSuper
            ? call.returnType.getLeastSupertype(other.call.returnType)
            : call.returnType.getGreatestSubtype(other.call.returnType);

    JSType newTypeOfThis = null;
    if (isEquivalent(typeOfThis, other.typeOfThis)) {
      newTypeOfThis = typeOfThis;
    } else {
      JSType maybeNewTypeOfThis =
          leastSuper
              ? typeOfThis.getLeastSupertype(other.typeOfThis)
              : typeOfThis.getGreatestSubtype(other.typeOfThis);
      newTypeOfThis = maybeNewTypeOfThis;
    }

    boolean newReturnTypeInferred = call.returnTypeInferred || other.call.returnTypeInferred;

    return new FunctionBuilder(registry)
        .withParamsNode(newParamsNode)
        .withReturnType(newReturnType, newReturnTypeInferred)
        .withTypeOfThis(newTypeOfThis)
        .build();
  }

  /**
   * Given a constructor or an interface type, get its superclass constructor or {@code null} if
   * none exists.
   */
  @Override
  public final FunctionType getSuperClassConstructor() {
    checkArgument(isConstructor() || isInterface());
    ObjectType maybeSuperInstanceType = getPrototype().getImplicitPrototype();
    if (maybeSuperInstanceType == null) {
      return null;
    }
    return maybeSuperInstanceType.getConstructor();
  }

  /**
   * Given a constructor or an interface type and a property, finds the top-most superclass that has
   * the property defined (including this constructor).
   */
  public final ObjectType getTopMostDefiningType(String propertyName) {
    checkState(isConstructor() || isInterface());
    checkArgument(getInstanceType().hasProperty(propertyName));
    FunctionType ctor = this;

    if (isInterface()) {
      return getInstanceType().getTopDefiningInterface(propertyName);
    }

    ObjectType topInstanceType = null;
    do {
      topInstanceType = ctor.getInstanceType();
      ctor = ctor.getSuperClassConstructor();
    } while (ctor != null && ctor.getPrototype().hasProperty(propertyName));

    return topInstanceType;
  }

  /**
   * Two function types are equal if their signatures match. Since they don't have signatures, two
   * interfaces are equal if their names match.
   */
  final boolean checkFunctionEquivalenceHelper(
      FunctionType that, EquivalenceMethod eqMethod, EqCache eqCache) {
    if (this == that) {
      return true;
    }
    if (kind != that.kind) {
      return false;
    }
    switch (kind) {
      case CONSTRUCTOR:
        return false; // constructors use identity semantics, which we already checked for above.
      case INTERFACE:
        return getReferenceName().equals(that.getReferenceName());
      case ORDINARY:
        return typeOfThis.checkEquivalenceHelper(that.typeOfThis, eqMethod, eqCache)
            && call.checkArrowEquivalenceHelper(that.call, eqMethod, eqCache);
      default:
        throw new AssertionError();
    }
  }

  @Override
  int recursionUnsafeHashCode() {
    int hc = kind.hashCode();
    switch (kind) {
      case CONSTRUCTOR:
        return 31 * hc + System.identityHashCode(this); // constructors use identity semantics
      case INTERFACE:
        return 31 * hc + getReferenceName().hashCode();
      case ORDINARY:
        hc = 31 * hc + typeOfThis.hashCode();
        hc = 31 * hc + call.hashCode();
        return hc;
      default:
        throw new AssertionError();
    }
  }

  public final boolean hasEqualCallType(FunctionType otherType) {
    return this.call.checkArrowEquivalenceHelper(
        otherType.call, EquivalenceMethod.IDENTITY, EqCache.create());
  }

  /**
   * Informally, a function is represented by {@code function (params): returnType} where the {@code
   * params} is a comma separated list of types, the first one being a special {@code this:T} if the
   * function expects a known type for {@code this}.
   */
  @Override
  StringBuilder appendTo(StringBuilder sb, boolean forAnnotations) {
    if (!isPrettyPrint() || this == registry.getNativeType(JSTypeNative.FUNCTION_INSTANCE_TYPE)) {
      return sb.append(forAnnotations ? "!Function" : "Function");
    }

    setPrettyPrint(false);

    sb.append("function(");
    int paramNum = call.parameters.getChildCount();
    boolean hasKnownTypeOfThis = !(typeOfThis instanceof UnknownType);
    if (hasKnownTypeOfThis) {
      if (isConstructor()) {
        sb.append("new:");
      } else {
        sb.append("this:");
      }
      typeOfThis.appendTo(sb, forAnnotations);
    }
    if (paramNum > 0) {
      if (hasKnownTypeOfThis) {
        sb.append(", ");
      }
      Node p = call.parameters.getFirstChild();
      appendArgString(sb, p, forAnnotations);

      p = p.getNext();
      while (p != null) {
        sb.append(", ");
        appendArgString(sb, p, forAnnotations);
        p = p.getNext();
      }
    }
    sb.append("): ");
    call.returnType.appendAsNonNull(sb, forAnnotations);

    setPrettyPrint(true);
    return sb;
  }

  private void appendArgString(StringBuilder sb, Node p, boolean forAnnotations) {
    if (p.isVarArgs()) {
      appendVarArgsString(sb, p.getJSType(), forAnnotations);
    } else if (p.isOptionalArg()) {
      appendOptionalArgString(sb, p.getJSType(), forAnnotations);
    } else {
      p.getJSType().appendAsNonNull(sb, forAnnotations);
    }
  }

  /** Gets the string representation of a var args param. */
  private void appendVarArgsString(StringBuilder sb, JSType paramType, boolean forAnnotations) {
    sb.append("...");
    paramType.appendAsNonNull(sb, forAnnotations);
  }

  /** Gets the string representation of an optional param. */
  private void appendOptionalArgString(StringBuilder sb, JSType paramType, boolean forAnnotations) {
    if (paramType.isUnionType()) {
      // Remove the optionality from the var arg.
      paramType =
          paramType
              .toMaybeUnionType()
              .getRestrictedUnion(registry.getNativeType(JSTypeNative.VOID_TYPE));
    }
    paramType.appendAsNonNull(sb, forAnnotations).append("=");
  }

  /**
   * A function is a subtype of another if their call methods are related via subtyping and {@code
   * this} is a subtype of {@code that} with regard to the prototype chain.
   */
  @Override
  public boolean isSubtype(JSType that) {
    return isSubtype(that, ImplCache.create(), SubtypingMode.NORMAL);
  }

  @Override
  protected boolean isSubtype(
      JSType that, ImplCache implicitImplCache, SubtypingMode subtypingMode) {
    if (JSType.isSubtypeHelper(this, that, implicitImplCache, subtypingMode)) {
      return true;
    }

    if (that.isFunctionType()) {
      FunctionType other = that.toMaybeFunctionType();
      if (other.isInterface()) {
        // Any function can be assigned to an interface function.
        return true;
      }
      if (isInterface()) {
        // An interface function cannot be assigned to anything.
        return false;
      }

      return shouldTreatThisTypesAsCovariant(other, implicitImplCache)
          && this.call.isSubtype(other.call, implicitImplCache, subtypingMode);
    }

    return getNativeType(JSTypeNative.FUNCTION_PROTOTYPE)
        .isSubtype(that, implicitImplCache, subtypingMode);
  }

  private boolean shouldTreatThisTypesAsCovariant(FunctionType other, ImplCache implicitImplCache) {
    // If functionA is a subtype of functionB, then their "this" types
    // should be contravariant. However, this causes problems because
    // of the way we enforce overrides. Because function(this:SubFoo)
    // is not a subtype of function(this:Foo), our override check treats
    // this as an error. Let's punt on all this for now.
    // TODO(nicksantos): fix this.
    boolean shouldTreatThisTypesAsCovariant =
        // An interface 'this'-type is non-restrictive.
        // In practical terms, if C implements I, and I has a method m,
        // then any m doesn't necessarily have to C#m's 'this'
        // type doesn't need to match I.
        (other.typeOfThis.toObjectType() != null
                && other.typeOfThis.toObjectType().getConstructor() != null
                && other.typeOfThis.toObjectType().getConstructor().isInterface())

            // If one of the 'this' types is covariant of the other,
            // then we'll treat them as covariant (see comment above).
            || other.typeOfThis.isSubtype(this.typeOfThis, implicitImplCache, SubtypingMode.NORMAL)
            || this.typeOfThis.isSubtype(other.typeOfThis, implicitImplCache, SubtypingMode.NORMAL);
    return shouldTreatThisTypesAsCovariant;
  }

  @Override
  public <T> T visit(Visitor<T> visitor) {
    return visitor.caseFunctionType(this);
  }

  @Override
  <T> T visit(RelationshipVisitor<T> visitor, JSType that) {
    return visitor.caseFunctionType(this, that);
  }

  /**
   * Gets the type of instance of this function.
   *
   * @throws IllegalStateException if this function is not a constructor (see {@link
   *     #isConstructor()}).
   */
  public final ObjectType getInstanceType() {
    Preconditions.checkState(hasInstanceType(), "Expected a constructor; got %s", this);
    return typeOfThis.toObjectType();
  }

  /** Sets the instance type. This should only be used for special native types. */
  final void setInstanceType(ObjectType instanceType) {
    typeOfThis = instanceType;
  }

  /** Returns whether this function type has an instance type. */
  public final boolean hasInstanceType() {
    return isConstructor() || isInterface();
  }

  /** Gets the type of {@code this} in this function. */
  @Override
  public final JSType getTypeOfThis() {
    return typeOfThis.isEmptyType()
        ? registry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE)
        : typeOfThis;
  }

  /** Gets the source node or null if this is an unknown function. */
  public final Node getSource() {
    return source;
  }

  /** Sets the source node. */
  public final void setSource(Node source) {
    if (prototypeSlot != null) {
      // NOTE(bashir): On one hand when source is null we want to drop any
      // references to old nodes retained in prototypeSlot. On the other hand
      // we cannot simply drop prototypeSlot, so we retain all information
      // except the propertyNode for which we use an approximation! These
      // details mostly matter in hot-swap passes.
      if (source == null || prototypeSlot.getNode() == null) {
        prototypeSlot =
            new Property(
                prototypeSlot.getName(),
                prototypeSlot.getType(),
                prototypeSlot.isTypeInferred(),
                source);
      }
    }
    this.source = source;
  }

  /** Adds a type to the set of known subtype ctors for this type. */
  final void addSubTypeIfNotPresent(FunctionType subtype) {
    checkState(isConstructor() || isInterface());

    if (knownSubtypeCtors == null) {
      knownSubtypeCtors = new CyclicSerializableLinkedHashSet<>();
    }

    knownSubtypeCtors.add(subtype);
  }

  // NOTE(sdh): One might assume that immediately after calling this, hasCachedValues() should
  // always return false.  This is not the case, since hasCachedValues() will return true if
  // prototypeSlot is non-null, and this override does nothing to change that state.
  @Override
  public final void clearCachedValues() {
    super.clearCachedValues();

    if (knownSubtypeCtors != null) {
      for (FunctionType subType : knownSubtypeCtors) {
        subType.clearCachedValues();
      }
    }

    if (!isNativeObjectType()) {
      if (hasInstanceType()) {
        getInstanceType().clearCachedValues();
      }

      if (prototypeSlot != null) {
        ((ObjectType) prototypeSlot.getType()).clearCachedValues();
      }
    }
  }

  public final Iterable<FunctionType> getDirectSubTypes() {
    return Iterables.concat(
        knownSubtypeCtors != null ? knownSubtypeCtors : ImmutableList.of(),
        this.registry.getDirectImplementors(this));
  }

  @Override
  public final boolean hasCachedValues() {
    return prototypeSlot != null || super.hasCachedValues();
  }

  @Override
  JSType resolveInternal(ErrorReporter reporter) {
    setResolvedTypeInternal(this);

    call = (ArrowType) safeResolve(call, reporter);
    if (prototypeSlot != null) {
      prototypeSlot.setType(safeResolve(prototypeSlot.getType(), reporter));
    }

    // Warning about typeOfThis if it doesn't resolve to an ObjectType
    // is handled further upstream.
    //
    // TODO(nicksantos): Handle this correctly if we have a UnionType.
    JSType maybeTypeOfThis = safeResolve(typeOfThis, reporter);
    if (maybeTypeOfThis != null) {
      if (maybeTypeOfThis.isNullType() || maybeTypeOfThis.isVoidType()) {
        typeOfThis = maybeTypeOfThis;
      } else {
        maybeTypeOfThis = ObjectType.cast(maybeTypeOfThis.restrictByNotNullOrUndefined());
        if (maybeTypeOfThis != null) {
          typeOfThis = maybeTypeOfThis;
        }
      }
    }

    ImmutableList<ObjectType> resolvedImplemented =
        resolveTypeListHelper(implementedInterfaces, reporter);
    if (resolvedImplemented != null) {
      implementedInterfaces = resolvedImplemented;
    }

    ImmutableList<ObjectType> resolvedExtended =
        resolveTypeListHelper(extendedInterfaces, reporter);
    if (resolvedExtended != null) {
      extendedInterfaces = resolvedExtended;
    }

    if (knownSubtypeCtors != null) {
      resolveKnownSubtypeCtors(reporter);
    }

    return super.resolveInternal(reporter);
  }

  /**
   * Resolve each item in the list, and return a new list if any references changed. Otherwise,
   * return null.
   */
  private ImmutableList<ObjectType> resolveTypeListHelper(
      ImmutableList<ObjectType> list, ErrorReporter reporter) {
    boolean changed = false;
    ImmutableList.Builder<ObjectType> resolvedList = ImmutableList.builder();
    for (ObjectType type : list) {
      JSType rt = type.resolve(reporter);
      if (!rt.isObjectType()) {
        reporter.warning(
            "not an object type: " + rt + " (at " + toString() + ")",
            source.getSourceFileName(),
            source.getLineno(),
            source.getCharno());
        continue;
      }
      ObjectType resolved = rt.toObjectType();
      resolvedList.add(resolved);
      changed |= (resolved != type);
    }
    return changed ? resolvedList.build() : null;
  }

  private void resolveKnownSubtypeCtors(ErrorReporter reporter) {
    // We want to resolve all of the known subtypes-ctors so we can store those resolved types
    // instead.
    ImmutableList<FunctionType> setCopy;
    do {
      setCopy =
          // However, resolving a type may cause more subtype-ctors to be registered. To avoid a
          // `ConcurrentModificationException` we operate on a copy of the original set. We leave
          // the original set in place in case resolution would re-add a previously known type.
          ImmutableList.copyOf(knownSubtypeCtors).stream()
              .map((t) -> JSType.toMaybeFunctionType(t.resolve(reporter)))
              .collect(toImmutableList());

      // We do this iteratively until resolving adds no more subtypes.
    } while (setCopy.size() != knownSubtypeCtors.size());

    // Additionally, resolving a type may change its `hashCode`, so we have to rebuild the set of
    // subtype-ctors.
    knownSubtypeCtors = null; // Reset
    for (FunctionType subtypeCtor : setCopy) {
      addSubTypeIfNotPresent(subtypeCtor);
    }
  }

  @Override
  public final String toDebugHashCodeString() {
    if (this == registry.getNativeType(JSTypeNative.FUNCTION_INSTANCE_TYPE)) {
      return super.toDebugHashCodeString();
    }

    StringBuilder b = new StringBuilder(32);
    b.append("function (");
    int paramNum = call.parameters.getChildCount();
    boolean hasKnownTypeOfThis = !typeOfThis.isUnknownType();
    if (hasKnownTypeOfThis) {
      b.append("this:");
      b.append(getDebugHashCodeStringOf(typeOfThis));
    }
    if (paramNum > 0) {
      if (hasKnownTypeOfThis) {
        b.append(", ");
      }
      Node p = call.parameters.getFirstChild();
      b.append(getDebugHashCodeStringOf(p.getJSType()));
      p = p.getNext();
      while (p != null) {
        b.append(", ");
        b.append(getDebugHashCodeStringOf(p.getJSType()));
        p = p.getNext();
      }
    }
    b.append(")");
    b.append(": ");
    b.append(getDebugHashCodeStringOf(call.returnType));
    return b.toString();
  }

  private String getDebugHashCodeStringOf(JSType type) {
    if (type == this) {
      return "me";
    } else {
      return type.toDebugHashCodeString();
    }
  }

  @Override
  final boolean hasAnyTemplateTypesInternal() {
    return getTemplateTypeMap().numUnfilledTemplateKeys() > 0
        || typeOfThis.hasAnyTemplateTypes()
        || call.hasAnyTemplateTypes();
  }

  public final boolean hasProperties() {
    return !super.getOwnPropertyNames().isEmpty();
  }

  /**
   * sets the current interface type to support structural interface matching (abbr. SMI)
   *
   * @param flag indicates whether or not it should support SMI
   */
  public final void setImplicitMatch(boolean flag) {
    checkState(isInterface());
    isStructuralInterface = flag;
  }

  @Override
  public final boolean isStructuralInterface() {
    return isInterface() && isStructuralInterface;
  }

  public final boolean isAbstract() {
    return isAbstract;
  }

  /**
   * get the map of properties to types covered in a function type
   *
   * @return a Map that maps the property's name to the property's type
   */
  @Override
  public final Map<String, JSType> getPropertyTypeMap() {
    Map<String, JSType> propTypeMap = new LinkedHashMap<>();
    updatePropertyTypeMap(this, propTypeMap, new HashSet<FunctionType>());
    return propTypeMap;
  }

  // cache is added to prevent infinite recursion when retrieving
  // the super type: see testInterfaceExtendsLoop in TypeCheckTest.java
  private static void updatePropertyTypeMap(
      FunctionType type, Map<String, JSType> propTypeMap, HashSet<FunctionType> cache) {
    if (type == null) {
      return;
    }
    // retrieve all property types on the prototype of this class
    ObjectType prototype = type.getPrototype();
    if (prototype != null) {
      Set<String> propNames = prototype.getOwnPropertyNames();
      for (String name : propNames) {
        if (!propTypeMap.containsKey(name)) {
          JSType propType = prototype.getPropertyType(name);
          propTypeMap.put(name, propType);
        }
      }
    }
    // retrieve all property types from its super class
    Iterable<ObjectType> iterable = type.getExtendedInterfaces();
    if (iterable != null) {
      for (ObjectType interfaceType : iterable) {
        FunctionType superConstructor = interfaceType.getConstructor();
        if (superConstructor == null || cache.contains(superConstructor)) {
          continue;
        }
        cache.add(superConstructor);
        updatePropertyTypeMap(superConstructor, propTypeMap, cache);
        cache.remove(superConstructor);
      }
    }
  }

  /**
   * check if there is a loop in the type extends chain
   *
   * @return an array of all functions in the loop chain if a loop exists, otherwise returns null
   */
  public final List<FunctionType> checkExtendsLoop() {
    return checkExtendsLoop(new HashSet<FunctionType>(), new ArrayList<FunctionType>());
  }

  private List<FunctionType> checkExtendsLoop(Set<FunctionType> cache, List<FunctionType> path) {
    Iterable<ObjectType> iterable = this.getExtendedInterfaces();
    if (iterable != null) {
      for (ObjectType interfaceType : iterable) {
        FunctionType superConstructor = interfaceType.getConstructor();
        if (superConstructor == null) {
          continue;
        }
        if (cache.contains(superConstructor)) {
          // after detecting a loop, prune and return the path, e.g.,:
          // A -> B -> C -> D -> C, will be pruned into:
          // c -> D -> C
          path.add(superConstructor);
          while (path.get(0) != superConstructor) {
            path.remove(0);
          }
          return path;
        }
        cache.add(superConstructor);
        path.add(superConstructor);
        List<FunctionType> result = superConstructor.checkExtendsLoop(cache, path);
        if (result != null) {
          return result;
        }
        cache.remove(superConstructor);
        path.remove(path.size() - 1);
      }
    }
    return null;
  }

  public final boolean acceptsArguments(List<? extends JSType> argumentTypes) {
    // NOTE(aravindpg): This code is essentially lifted from TypeCheck::visitParameterList,
    // but what small differences there are make it very painful to refactor out the shared code.
    Iterator<? extends JSType> arguments = argumentTypes.iterator();
    Iterator<Node> parameters = this.getParameters().iterator();
    Node parameter = null;
    JSType argument = null;
    while (arguments.hasNext()
        && (parameters.hasNext() || parameter != null && parameter.isVarArgs())) {
      // If there are no parameters left in the list, then the while loop
      // above implies that this must be a var_args function.
      if (parameters.hasNext()) {
        parameter = parameters.next();
      }
      argument = arguments.next();

      if (!argument.isSubtypeOf(parameter.getJSType())) {
        return false;
      }
    }

    int numArgs = argumentTypes.size();
    return this.getMinArity() <= numArgs && numArgs <= this.getMaxArity();
  }

  /** Create a new constructor with the parameters and return type stripped. */
  public final FunctionType forgetParameterAndReturnTypes() {
    FunctionType result =
        new FunctionBuilder(registry)
            .withName(getReferenceName())
            .withSourceNode(source)
            .withTypeOfThis(getInstanceType())
            .withKind(kind)
            .build();
    result.setPrototypeBasedOn(getInstanceType());
    return result;
  }

  /** Returns a list of template types present on the constructor but not on the instance. */
  public final ImmutableList<TemplateType> getConstructorOnlyTemplateParameters() {
    TemplateTypeMap ctorMap = getTemplateTypeMap();
    TemplateTypeMap instanceMap = getInstanceType().getTemplateTypeMap();
    if (ctorMap == instanceMap) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<TemplateType> ctorKeys = ImmutableList.builder();
    Set<TemplateType> instanceKeys = ImmutableSet.copyOf(instanceMap.getUnfilledTemplateKeys());
    for (TemplateType ctorKey : ctorMap.getUnfilledTemplateKeys()) {
      if (!instanceKeys.contains(ctorKey)) {
        ctorKeys.add(ctorKey);
      }
    }
    return ctorKeys.build();
  }

  boolean createsAmbiguousObjects() {
    if (this.constructorAmbiguity == ConstructorAmbiguity.UNKNOWN) {
      constructorAmbiguity = calculateConstructorAmbiguity();
    }
    return constructorAmbiguity == ConstructorAmbiguity.CONSTRUCTS_AMBIGUOUS_OBJECTS;
  }

  private ConstructorAmbiguity calculateConstructorAmbiguity() {
    final ConstructorAmbiguity constructorAmbiguity;
    if (isUnknownType()) {
      constructorAmbiguity = ConstructorAmbiguity.CONSTRUCTS_AMBIGUOUS_OBJECTS;
    } else if (isNativeObjectType()) {
      // native types other than unknown are never ambiguous
      constructorAmbiguity = ConstructorAmbiguity.CONSTRUCTS_UNAMBIGUOUS_OBJECTS;
    } else {
      FunctionType superConstructor = getSuperClassConstructor();
      if (superConstructor == null) {
        // TODO(bradfordcsmith): Why is superConstructor ever null here?
        constructorAmbiguity = ConstructorAmbiguity.CONSTRUCTS_AMBIGUOUS_OBJECTS;
      } else if (superConstructor.createsAmbiguousObjects()) {
        // Subclasses of ambiguous objects are also ambiguous
        constructorAmbiguity = ConstructorAmbiguity.CONSTRUCTS_AMBIGUOUS_OBJECTS;
      } else if (source != null) {
        // We can see the definition of the class, so we know all properties it directly declares
        // or references.
        // The same is true for its superclass (previous condition).
        constructorAmbiguity = ConstructorAmbiguity.CONSTRUCTS_UNAMBIGUOUS_OBJECTS;
      } else if (isDelegateProxy()) {
        // Type was created by the compiler as a proxy that inherits from the real type that was in
        // the code.
        // Since we've made it this far, we know the real type creates unambiguous objects.
        // Therefore, the proxy does, too.
        constructorAmbiguity = ConstructorAmbiguity.CONSTRUCTS_UNAMBIGUOUS_OBJECTS;
      } else {
        // Type was created directly from JSDoc without a function or class literal.
        // e.g.
        // /**
        //  * @constructor
        //  * @param {string} x
        //  * @implements {SomeInterface}
        //  */
        // const MyImpl = createMyImpl();
        // The actual properties on this class are hidden from us, so we must consider it ambiguous.
        constructorAmbiguity = ConstructorAmbiguity.CONSTRUCTS_AMBIGUOUS_OBJECTS;
      }
    }
    return constructorAmbiguity;
  }

  // See also TypedScopeCreator.DELEGATE_PROXY_SUFFIX
  // Unfortunately we cannot use that constant here.
  private static final String DELEGATE_SUFFIX = ObjectType.createDelegateSuffix("Proxy");

  private boolean isDelegateProxy() {
    // TODO(bradfordcsmith): There should be a better way to determine that we have a proxy type.
    return hasReferenceName() && getReferenceName().endsWith(DELEGATE_SUFFIX);
  }
}
