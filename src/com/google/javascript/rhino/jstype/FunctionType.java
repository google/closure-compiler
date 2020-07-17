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
import static com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_TYPE;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.javascript.rhino.ClosurePrimitive;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.EqualityChecker.EqMethod;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * This derived type provides extended information about a function, including its return type and
 * argument types.
 *
 * <p>Note: the parameters list is the PARAM_LIST node that is the parent of the actual NAME node
 * containing the parsed argument list (annotated with JSDOC_TYPE_PROP's for the compile-time type
 * of each argument.
 */
public class FunctionType extends PrototypeObjectType implements Serializable {
  private static final long serialVersionUID = 1L;

  private static final JSTypeClass TYPE_CLASS = JSTypeClass.FUNCTION;

  enum Kind {
    ORDINARY,
    CONSTRUCTOR,
    INTERFACE,
    NONE;
  }

  // relevant only for constructors
  private enum PropAccess {
    // An implicit any behavior (default of ES5 classes)
    ANY,
    // An explicit @unrestricted tag
    ANY_EXPLICIT,
    // An explicit @struct or the implicit behavior of ES6 classes
    STRUCT,
    // An explicit @dict or the implicit behavior of ES5 classes
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
   * The types which are subtypes of this function. It is lazily initialized and only relevant for
   * constructors. In all other cases it is {@code null}.
   */
  private List<FunctionType> subTypes = null;

  /**
   * Whether this constructor was added to its superclass constructor's subtypes list, to avoid a
   * limited amount of duplication that can happen from unresolved supertypes. This only tracks
   * classes extending classes (no interfaces), since there is no way to duplicate interfaces via
   * methods accessible outside this class.
   */
  private boolean wasAddedToExtendedConstructorSubtypes = false;

  /** The primitive id associated with this FunctionType, or null if none. */
  private final ClosurePrimitive closurePrimitive;

  /** If non-null, the original canonical variant of this function; only used for constructors. */
  private final FunctionType canonicalRepresentation;

  /**
   * Creates an instance for a function that might be a constructor.
   *
   * <p>Non-subclasses must go through {@link Builder} to create a new FunctionType.
   */
  FunctionType(Builder builder) {
    super(builder);
    setPrettyPrint(true);

    Node source = builder.sourceNode;
    checkArgument(source == null || source.isFunction() || source.isClass());
    this.source = source;
    this.kind = builder.kind;

    if (builder.typeOfThis != null) {
      this.typeOfThis = builder.typeOfThis;
    } else if (this instanceof NoResolvedType) {
      /**
       * TODO(b/112425334): Delete this special case if NO_RESOLVED_TYPE is deleted.
       *
       * <p>Despite being a subclass of `NoType`, `NoResolvedType` should behave more like `?`.
       * There's no reason to believe its properties are of its own type.
       */
      this.typeOfThis = this.registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
    } else {
      switch (kind) {
        case CONSTRUCTOR:
        case INTERFACE:
          InstanceObjectType.Builder typeOfThisBuilder = InstanceObjectType.builderForCtor(this);

          Set<TemplateType> ctorKeys = builder.constructorOnlyKeys;
          if (!ctorKeys.isEmpty()) {
            typeOfThisBuilder
                .setTemplateTypeMap(this.templateTypeMap.copyWithoutKeys(ctorKeys))
                .setTemplateParamCount(this.getTemplateParamCount() - ctorKeys.size());
          }

          this.typeOfThis = typeOfThisBuilder.build();
          break;

        case ORDINARY:
          this.typeOfThis = this.registry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE);
          break;

        case NONE:
          this.typeOfThis = this;
          break;
      }
    }

    if (this.kind == Kind.CONSTRUCTOR) {
      this.propAccess = PropAccess.ANY;
    }

    this.call =
        new ArrowType(
            this.registry,
            builder.parameters,
            builder.returnsOwnInstanceType ? this.typeOfThis : builder.returnType,
            builder.returnTypeIsInferred);

    this.closurePrimitive = builder.primitiveId;
    this.isStructuralInterface = false;
    this.isAbstract = builder.isAbstract;
    FunctionType canonicalRepresentation = builder.canonicalRepresentation;
    checkArgument(
        canonicalRepresentation == null || kind == Kind.CONSTRUCTOR,
        "Only constructors should have canonical representations");
    this.canonicalRepresentation = canonicalRepresentation;

    if (builder.setPrototypeBasedOn != null) {
      this.setPrototypeBasedOn(builder.setPrototypeBasedOn);
    }

    this.registry.getResolver().resolveIfClosed(this, TYPE_CLASS);
  }

  @Override
  JSTypeClass getTypeClass() {
    return TYPE_CLASS;
  }

  @Override
  public FunctionType getConstructor() {
    // Every function type, including `Function`, is constructed by `(typeof Function)`.
    return checkNotNull(this.registry.getNativeFunctionType(JSTypeNative.FUNCTION_FUNCTION_TYPE));
  }

  @Override
  public final boolean isInstanceType() {
    // Only `Function` is both a function type and the intance type of a nominal constructor.
    return JSType.areIdentical(this, this.registry.getNativeType(JSTypeNative.FUNCTION_TYPE));
  }

  @Override
  public final boolean isConstructor() {
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
   * annotation, if B's constructor is not explicitly annotated.
   */
  public final boolean makesStructs() {
    if (!hasInstanceType()) {
      return false;
    }
    if (propAccess == PropAccess.STRUCT) {
      return true;
    }
    if (propAccess == PropAccess.ANY_EXPLICIT) {
      // For anything EXPLICITLY marked as @unresticted do not look to the super type.
      return false;
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
   * annotation, if B's constructor is not explicitly annotated.
   */
  public final boolean makesDicts() {
    if (!isConstructor()) {
      return false;
    }
    if (propAccess == PropAccess.DICT) {
      return true;
    }
    if (propAccess == PropAccess.ANY_EXPLICIT) {
      // For anything EXPLICITLY marked as @unresticted do not look to the super type.
      return false;
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

  public final void setExplicitUnrestricted() {
    propAccess = PropAccess.ANY_EXPLICIT;
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

  public final ImmutableList<Parameter> getParameters() {
    return getInternalArrowType().getParameterList();
  }

  /** Gets the minimum number of arguments that this function requires. */
  public final int getMinArity() {
    // NOTE(nicksantos): There are some native functions that have optional
    // parameters before required parameters. This algorithm finds the position
    // of the last required parameter.
    int i = 0;
    int min = 0;
    for (Parameter parameter : getParameters()) {
      i++;
      if (!parameter.isOptional() && !parameter.isVariadic()) {
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
    ImmutableList<Parameter> params = getParameters();
    if (params.isEmpty()) {
      return 0;
    }
    Parameter lastParam = Iterables.getLast(params);
    if (!lastParam.isVariadic()) {
      return params.size();
    }

    return Integer.MAX_VALUE;
  }

  public final JSType getReturnType() {
    return call.getReturnType();
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
            PrototypeObjectType.builder(registry)
                .setName(getReferenceName() + ".prototype")
                .setImplicitPrototype(registry.getNativeObjectType(OBJECT_TYPE))
                .setNative(isNativeObjectType())
                .build(),
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

  private void setPrototypeBasedOn(ObjectType baseType, Node propertyNode) {
    // First handle class-side inheritance for ES6 classes, before reassigning baseType.
    if (source != null && source.isClass()) {
      FunctionType superCtor = baseType.getConstructor();
      if (superCtor != null) {
        this.setImplicitPrototype(superCtor);
      }
      maybeLoosenTypecheckingDueToForwardReferencedSupertype(baseType);
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
      baseType =
          PrototypeObjectType.builder(registry)
              .setName(getReferenceName() + ".prototype")
              .setImplicitPrototype(baseType)
              .build();
    }
    setPrototype(baseType, propertyNode);
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
    if (isConstructor() && JSType.areIdentical(prototype, getInstanceType())) {
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
        superClass.addSubType(this);
        wasAddedToExtendedConstructorSubtypes = true;
      }

      if (isInterface()) {
        for (ObjectType interfaceType : getExtendedInterfaces()) {
          if (interfaceType.getConstructor() != null) {
            interfaceType.getConstructor().addSubType(this);
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
    checkState(isConstructor());

    this.implementedInterfaces = ImmutableList.copyOf(implementedInterfaces);
    for (ObjectType type : implementedInterfaces) {
      registry.registerTypeImplementingInterface(this, type);
      typeOfThis.mergeSupertypeTemplateTypes(type);
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
    checkState(isInterface());

    this.extendedInterfaces = ImmutableList.copyOf(extendedInterfaces);
    for (ObjectType extendedInterface : extendedInterfaces) {
      typeOfThis.mergeSupertypeTemplateTypes(extendedInterface);
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
            builder(registry)
                .withParameters(builder.build())
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
    Builder builder =
        builder(registry)
            .withReturnType(getReturnType())
            .withTemplateKeys(getTemplateTypeMap().getTemplateKeys());
    if (argsToBind < 0) {
      return builder.build();
    }
    ImmutableList<Parameter> origParams = call.getParameterList();
    List<Parameter> params = new ArrayList<>(origParams);
    for (int i = 1; i < argsToBind && !params.isEmpty(); i++) {
      if (params.get(0).isVariadic()) {
        break;
      }
      params.remove(0);
    }
    builder.withParameters(params);

    return builder.build();
  }

  /**
   * Notice that "call" and "bind" have the same argument signature, except that all the arguments
   * of "bind" (except the first) are optional.
   */
  private FunctionType getCallOrBindSignature(boolean isCall) {
    boolean isBind = !isCall;
    Builder builder =
        builder(registry)
            .withReturnType(isCall ? getReturnType() : getBindReturnType(-1))
            .withTemplateKeys(getTemplateTypeMap().getTemplateKeys());

    List<Parameter> origParams = getInternalArrowType().getParameterList();

    List<Parameter> params = new ArrayList<>(origParams);

    Parameter thisType =
        Parameter.create(
            registry.createOptionalNullableType(getTypeOfThis()),
            /* isOptional= */ false,
            /* isVariadic= */ false);
    params.add(0, thisType);

    if (isBind) {
      // The arguments of bind() are unique in that they are all
      // optional but not undefinable.
      for (int i = 1; i < params.size(); i++) {
        Parameter current = params.get(i);
        Parameter optionalCopy =
            Parameter.create(current.getJSType(), /* isOptional= */ true, current.isVariadic());
        params.set(i, optionalCopy);
      }
    } else if (isCall) {
      // The first argument of call() is optional iff all the arguments
      // are optional. It's sufficient to check the first argument.
      Parameter firstArg = params.size() > 1 ? params.get(1) : null;
      if (firstArg == null || firstArg.isOptional() || firstArg.isVariadic()) {
        Parameter optionalThisType =
            Parameter.create(thisType.getJSType(), /* isOptional= */ true, /* isVariadic= */ false);
        params.set(0, optionalThisType);
      }
    }

    builder.withParameters(params);

    return builder.build();
  }

  @Override
  boolean defineProperty(String name, JSType type, boolean inferred, Node propertyNode) {
    if ("prototype".equals(name)) {
      ObjectType objType = type.toObjectType();
      if (objType != null) {
        if (prototypeSlot != null && objType.equals(prototypeSlot.getType())) {
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

    if (equals(that)) {
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
    JSType functionInstance = registry.getNativeType(JSTypeNative.FUNCTION_TYPE);
    if (functionInstance.equals(that)) {
      return leastSuper ? that : this;
    } else if (functionInstance.equals(this)) {
      return leastSuper ? this : that;
    }

    // In theory, we should be using the GREATEST_FUNCTION_TYPE as the
    // greatest function. In practice, we don't because it's way too
    // broad. The greatest function takes var_args None parameters, which
    // means that all parameters register a type warning.
    //
    // Instead, we use the U2U ctor type, which has unknown type args.
    FunctionType greatestFn = registry.getNativeFunctionType(JSTypeNative.FUNCTION_TYPE);
    FunctionType leastFn = registry.getNativeFunctionType(JSTypeNative.LEAST_FUNCTION_TYPE);
    return leastSuper ? greatestFn : leastFn;
  }

  /** Try to get the sup/inf of two functions by looking at the piecewise components. */
  private FunctionType tryMergeFunctionPiecewise(FunctionType other, boolean leastSuper) {
    List<Parameter> newParamsNode = null;
    if (new EqualityChecker()
        .setEqMethod(EqMethod.IDENTITY)
        .checkParameters(this.call, other.call)) {
      newParamsNode = call.getParameterList();
    } else {
      // If the parameters are not equal, don't try to merge them.
      // Someday, we should try to merge the individual params.
      return null;
    }

    JSType newReturnType =
        leastSuper
            ? call.getReturnType().getLeastSupertype(other.call.getReturnType())
            : call.getReturnType().getGreatestSubtype(other.call.getReturnType());

    JSType newTypeOfThis = null;
    if (Objects.equals(typeOfThis, other.typeOfThis)) {
      newTypeOfThis = typeOfThis;
    } else {
      JSType maybeNewTypeOfThis =
          leastSuper
              ? typeOfThis.getLeastSupertype(other.typeOfThis)
              : typeOfThis.getGreatestSubtype(other.typeOfThis);
      newTypeOfThis = maybeNewTypeOfThis;
    }

    boolean newReturnTypeInferred = call.returnTypeInferred || other.call.returnTypeInferred;

    return builder(registry)
        .withParameters(newParamsNode)
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

  @Override
  int recursionUnsafeHashCode() {
    int hc = kind.hashCode();
    switch (kind) {
      case CONSTRUCTOR:
      case INTERFACE:
        return 31 * hc + System.identityHashCode(this); // constructors use identity semantics
      case ORDINARY:
        hc = 31 * hc + typeOfThis.hashCode();
        hc = 31 * hc + call.hashCode();
        return hc;
      default:
        throw new AssertionError();
    }
  }

  public final boolean hasEqualCallType(FunctionType that) {
    return new EqualityChecker()
        .setEqMethod(EqMethod.IDENTITY)
        .check(this.call, that.call);
  }

  /**
   * Informally, a function is represented by {@code function (params): returnType} where the {@code
   * params} is a comma separated list of types, the first one being a special {@code this:T} if the
   * function expects a known type for {@code this}.
   */
  @Override
  void appendTo(TypeStringBuilder sb) {
    if (!isPrettyPrint()
        || JSType.areIdentical(this, registry.getNativeType(JSTypeNative.FUNCTION_TYPE))) {
      sb.append(sb.isForAnnotations() ? "!Function" : "Function");
      return;
    }

    if (hasInstanceType() && getSource() != null) {
      // Render function types known to be type definitions as "(typeof Foo)". This includes types
      // defined like "/** @constructor */ function Foo() { }" but not to those defined like "@param
      // {function(new:Foo)}". Only the former will have a source node.
      sb.append("(typeof ").append(this.getInstanceType()).append(")");
      return;
    }

    setPrettyPrint(false);

    sb.append("function(");
    int paramNum = call.getParameterList().size();
    boolean hasKnownTypeOfThis = !(typeOfThis instanceof UnknownType);
    if (hasKnownTypeOfThis) {
      if (isConstructor()) {
        sb.append("new:");
      } else {
        sb.append("this:");
      }
      sb.append(typeOfThis);
    }
    if (paramNum > 0) {
      if (hasKnownTypeOfThis) {
        sb.append(", ");
      }
      Parameter p = call.getParameterList().get(0);
      appendArgString(sb, p);

      for (int i = 1; i < paramNum; i++) {
        p = call.getParameterList().get(i);
        sb.append(", ");
        appendArgString(sb, p);
      }
    }
    sb.append("): ");
    sb.appendNonNull(call.getReturnType());

    setPrettyPrint(true);
    return;
  }

  private void appendArgString(TypeStringBuilder sb, Parameter p) {
    if (p.isVariadic()) {
      appendVarArgsString(sb, p.getJSType());
    } else if (p.isOptional()) {
      appendOptionalArgString(sb, p.getJSType());
    } else {
      sb.appendNonNull(p.getJSType());
    }
  }

  /** Gets the string representation of a var args param. */
  private void appendVarArgsString(TypeStringBuilder sb, JSType paramType) {
    sb.append("...").appendNonNull(paramType);
  }

  /** Gets the string representation of an optional param. */
  private void appendOptionalArgString(TypeStringBuilder sb, JSType paramType) {
    if (paramType.isUnionType()) {
      // Remove the optionality from the var arg.
      paramType =
          paramType
              .toMaybeUnionType()
              .getRestrictedUnion(registry.getNativeType(JSTypeNative.VOID_TYPE));
    }
    sb.appendNonNull(paramType).append("=");
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
    checkState(this.hasInstanceType());

    return typeOfThis.toObjectType();
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

  /** Adds a type to the list of subtypes for this type. */
  private void addSubType(FunctionType subType) {
    if (subTypes == null) {
      subTypes = new ArrayList<>();
    }
    subTypes.add(subType);
  }

  /**
   * Restricted package-accessible version of {@link #addSubType}, which ensures subtypes are not
   * duplicated. Generally subtypes are added internally and are guaranteed not to be duplicated,
   * but this has the possibility of missing unresolved supertypes (typically from externs). To
   * handle that case, {@link PrototypeObjectType} also adds subclasses after resolution. This
   * method only adds a subclass to the list if it didn't already add itself to its superclass in
   * the earlier pass. Ideally, "subclass" here would only refer to classes, but there's an edge
   * case where interfaces have the {@code Object} constructor added as its "superclass".
   */
  final void addSubClassAfterResolution(FunctionType subClass) {
    checkArgument(JSType.areIdentical(this, subClass.getSuperClassConstructor()));
    if (!subClass.wasAddedToExtendedConstructorSubtypes) {
      addSubType(subClass);
    }
  }

  // NOTE(sdh): One might assume that immediately after calling this, hasCachedValues() should
  // always return false.  This is not the case, since hasCachedValues() will return true if
  // prototypeSlot is non-null, and this override does nothing to change that state.
  @Override
  public final void clearCachedValues() {
    super.clearCachedValues();

    if (subTypes != null) {
      for (FunctionType subType : subTypes) {
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
        subTypes != null ? subTypes : ImmutableList.<FunctionType>of(),
        this.registry.getDirectImplementors(this));
  }

  @Override
  public final boolean hasCachedValues() {
    return prototypeSlot != null || super.hasCachedValues();
  }

  @Override
  JSType resolveInternal(ErrorReporter reporter) {
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

    if (subTypes != null) {
      for (int i = 0; i < subTypes.size(); i++) {
        FunctionType subType = subTypes.get(i);
        subTypes.set(i, JSType.toMaybeFunctionType(subType.resolve(reporter)));
      }
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
      changed |= !JSType.areIdentical(resolved, type);
    }
    return changed ? resolvedList.build() : null;
  }

  @Override
  final boolean hasAnyTemplateTypesInternal() {
    return this.getTemplateParamCount() > 0
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
          while (!JSType.areIdentical(path.get(0), superConstructor)) {
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
    Iterator<Parameter> parameters = this.getParameters().iterator();
    Parameter parameter = null;
    JSType argument = null;
    while (arguments.hasNext()
        && (parameters.hasNext() || (parameter != null && parameter.isVariadic()))) {
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
        builder(registry)
            .withName(getReferenceName())
            .withSourceNode(source)
            .withTypeOfThis(getInstanceType())
            .withKind(kind)
            .withCanonicalRepresentation(this)
            .build();
    result.setPrototypeBasedOn(getInstanceType());
    return result;
  }

  /** Returns a list of template types present on the constructor but not on the instance. */
  public final ImmutableList<TemplateType> getConstructorOnlyTemplateParameters() {
    checkState(this.isConstructor(), this);

    // Within the `TemplateTypeMap` of a ctor type, the ctor only keys always appear after the
    // instance type keys.
    TemplateTypeMap map = getTemplateTypeMap();
    int ctorOnlyKeyCount =
        this.getTemplateParamCount() - this.getInstanceType().getTemplateParamCount();
    return map.getTemplateKeys().subList(map.size() - ctorOnlyKeyCount, map.size());
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

  /** Returns the {@code @closurePrimitive} identifier associated with this function */
  public final ClosurePrimitive getClosurePrimitive() {
    return this.closurePrimitive;
  }

  public static Builder builder(JSTypeRegistry registry) {
    return new Builder(registry);
  }

  /**
   * A builder class for function and arrow types.
   *
   * <p>If you need to build an interface constructor, use {@link
   * JSTypeRegistry#createInterfaceType}.
   *
   * @author nicksantos@google.com (Nick Santos)
   */
  public static final class Builder extends PrototypeObjectType.Builder<Builder> {

    private Node sourceNode = null;
    private List<Parameter> parameters = null;
    private JSType returnType = null;
    private JSType typeOfThis = null;
    private ObjectType setPrototypeBasedOn = null;
    private Set<TemplateType> constructorOnlyKeys = ImmutableSet.of();
    private Kind kind = Kind.ORDINARY;
    private boolean isAbstract;
    private boolean returnTypeIsInferred;
    private boolean returnsOwnInstanceType;
    private ClosurePrimitive primitiveId = null;
    private FunctionType canonicalRepresentation = null;

    private Builder(JSTypeRegistry registry) {
      super(registry);

      this.setImplicitPrototype(
          checkNotNull(registry.getNativeObjectType(JSTypeNative.FUNCTION_PROTOTYPE)));
    }

    /** Set the name of the function type. */
    public Builder withName(String name) {
      return setName(name);
    }

    /** Set the source node of the function type. */
    public Builder withSourceNode(Node sourceNode) {
      this.sourceNode = sourceNode;
      return this;
    }

    /** Set the parameters of the function type. */
    public Builder withParameters(List<Parameter> parameters) {
      this.parameters = parameters;
      return this;
    }

    /** Set the function to take zero parameters. */
    public Builder withParameters() {
      this.parameters = ImmutableList.of();
      return this;
    }

    /** Set the return type. */
    public Builder withReturnType(JSType returnType) {
      this.returnType = returnType;
      return this;
    }

    /** Set the return type and whether it's inferred. */
    public Builder withReturnType(JSType returnType, boolean inferred) {
      this.returnType = returnType;
      this.returnTypeIsInferred = inferred;
      return this;
    }

    /** Set the return type to be a constructor's own instance type. */
    Builder withReturnsOwnInstanceType() {
      this.returnsOwnInstanceType = true;
      return this;
    }

    /** Sets an inferred return type. */
    public Builder withInferredReturnType(JSType returnType) {
      this.returnType = returnType;
      this.returnTypeIsInferred = true;
      return this;
    }

    /** Set the "this" type. */
    public Builder withTypeOfThis(JSType typeOfThis) {
      this.typeOfThis = typeOfThis;
      return this;
    }

    /** Set the template name. */
    public Builder withTemplateKeys(ImmutableList<TemplateType> templateKeys) {
      return this.setTemplateTypeMap(
              registry
                  .getEmptyTemplateTypeMap()
                  .copyWithExtension(templateKeys, ImmutableList.of()))
          // TODO(nickreid): This value should only consider ctor only keys.
          .setTemplateParamCount(templateKeys.size());
    }

    /** Set the template name. */
    public Builder withTemplateKeys(TemplateType... templateKeys) {
      return withTemplateKeys(ImmutableList.copyOf(templateKeys));
    }

    /**
     * Specifies a subset of the template keys that only apply to the constructor, and should be
     * removed from the instance type. These keys must still be passed to {@link #withTemplateKeys}.
     */
    public Builder withConstructorTemplateKeys(Iterable<TemplateType> constructorOnlyKeys) {
      this.constructorOnlyKeys = ImmutableSet.copyOf(constructorOnlyKeys);
      return this;
    }

    /** Set the function kind. */
    Builder withKind(Kind kind) {
      this.kind = kind;
      return this;
    }
    /** Make this a constructor. */
    public Builder forConstructor() {
      this.kind = Kind.CONSTRUCTOR;
      return this;
    }

    /** Make this an interface. */
    public Builder forInterface() {
      this.kind = Kind.INTERFACE;
      this.parameters = ImmutableList.of();
      return this;
    }

    /** Make this a native type. */
    Builder forNativeType() {
      return this.setNative(true);
    }

    /** Mark abstract method. */
    public Builder withIsAbstract(boolean isAbstract) {
      this.isAbstract = isAbstract;
      return this;
    }

    /** Set the prototype property of a constructor. */
    public Builder withPrototypeBasedOn(ObjectType setPrototypeBasedOn) {
      this.setPrototypeBasedOn = setPrototypeBasedOn;
      return this;
    }

    /** Sets the {@link ClosurePrimitive} corresponding to this function */
    public Builder withClosurePrimitiveId(ClosurePrimitive id) {
      this.primitiveId = id;
      return this;
    }

    /** Sets the canonical representation of a constructor, if any */
    private Builder withCanonicalRepresentation(FunctionType representation) {
      this.canonicalRepresentation = representation;
      return this;
    }

    /** Copies all the information from another function type. */
    public Builder copyFromOtherFunction(FunctionType otherType) {
      this.setName(otherType.getReferenceName())
          .setNative(otherType.isNativeObjectType())
          .setTemplateTypeMap(otherType.getTemplateTypeMap())
          .setTemplateParamCount(otherType.getTemplateParamCount());
      this.sourceNode = otherType.getSource();
      this.parameters = otherType.getParameters();
      this.returnType = otherType.getReturnType();
      this.typeOfThis = otherType.getTypeOfThis();
      this.kind = otherType.getKind();
      this.returnTypeIsInferred = otherType.isReturnTypeInferred();
      this.isAbstract = otherType.isAbstract();
      this.primitiveId = otherType.getClosurePrimitive();
      return this;
    }

    /** Constructs a new function type. */
    @Override
    public FunctionType build() {
      // Verify that the builder is an a sensible state before instantiating a function.

      switch (this.kind) {
        case CONSTRUCTOR:
        case INTERFACE:
          /**
           * These kinds have no implication on whether `returnsOwnInstanceType` is reasonable. This
           * configuration may be intended to synthesize an instance type. The return type and
           * instance type are independent.
           */
          break;
        case NONE:
          checkState(this.returnsOwnInstanceType);
          break;
        case ORDINARY:
          checkState(!this.returnsOwnInstanceType);
          break;
      }

      if (this.returnsOwnInstanceType) {
        // If the return type or instance type was available to set, there's no need to use
        // `returnsOwnInstanceType`.
        checkState(this.returnType == null);
        checkState(this.typeOfThis == null);
      }

      switch (this.kind) {
        case CONSTRUCTOR:
        case INTERFACE:
          break;
        case NONE:
        case ORDINARY:
          checkState(this.constructorOnlyKeys.isEmpty());
          break;
      }

      return new FunctionType(this);
    }

    public FunctionType buildAndResolve() {
      return checkNotNull(build().toMaybeFunctionType());
    }
  }

  public final FunctionType getCanonicalRepresentation() {
    return canonicalRepresentation;
  }

  /**
   * Models a single JavaScript parameter.
   *
   * <p>This parameter has a type; optionality; and may be var_args (variadic).
   */
  @AutoValue
  public abstract static class Parameter implements Serializable {
    private static final long serialVersionUID = 1L;

    public static Parameter create(JSType type, boolean isOptional, boolean isVariadic) {
      return new AutoValue_FunctionType_Parameter(type, isOptional, isVariadic);
    }

    public abstract JSType getJSType();

    public abstract boolean isOptional();

    public abstract boolean isVariadic();
  }
}
