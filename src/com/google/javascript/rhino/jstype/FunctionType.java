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

import static com.google.javascript.rhino.jstype.JSTypeNative.U2U_CONSTRUCTOR_TYPE;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Collections;
import java.util.List;
import java.util.Set;


/**
 * This derived type provides extended information about a function, including
 * its return type and argument types.<p>
 *
 * Note: the parameters list is the LP node that is the parent of the
 * actual NAME node containing the parsed argument list (annotated with
 * JSDOC_TYPE_PROP's for the compile-time type of each argument.
*
*
 */
public class FunctionType extends PrototypeObjectType {
  private static final long serialVersionUID = 1L;

  private enum Kind {
    ORDINARY,
    CONSTRUCTOR,
    INTERFACE
  }

  /**
   * {@code [[Call]]} property.
   */
  private final ArrowType call;

  /**
   * The {@code prototype} property. This field is lazily initialized by
   * {@code #getPrototype()}. The most important reason for lazily
   * initializing this field is that there are cycles in the native types
   * graph, so some prototypes must temporarily be {@code null} during
   * the construction of the graph.
   */
  private FunctionPrototypeType prototype;

  /**
   * Whether a function is a constructor, an interface, or just an ordinary
   * function.
   */
  private final Kind kind;

  /**
   * The type of {@code this} in the scope of this function.
   */
  private ObjectType typeOfThis;

  /**
   * The function node which this type represents. It may be {@code null}.
   */
  private Node source;

  /**
   * The interfaces directly implemented by this function.
   * It is only relevant for constructors. May not be {@code null}.
   */
  private List<ObjectType> implementedInterfaces = ImmutableList.of();

  /**
   * The types which are subtypes of this function. It is only relevant for
   * constructors and may be {@code null}.
   */
  private List<FunctionType> subTypes;

  /**
   * The template type name. May be {@code null}.
   */
  private String templateTypeName;

  /**
   * Creates a function type.
   * @param registry the owner registry for this type
   * @param name the function's name or {@code null} to indicate that the
   *        function is anonymous.
   * @param source the node defining this function. Its type
   *        ({@link Node#getType()}) must be {@link Token#FUNCTION}.
   * @param parameters the function's parameters or {@code null}
   *        to indicate that the parameter types are unknown.
   * @param returnType the function's return type or {@code null} to indicate
   *        that the return type is unknown.
   */
  @VisibleForTesting
  public FunctionType(JSTypeRegistry registry, String name, Node source,
      Node parameters, JSType returnType) {
    this(registry, name, source, parameters, returnType, null, null, false,
         false);
  }

  /**
   * Creates a function type.
   * @param registry the owner registry for this type
   * @param name the function's name or {@code null} to indicate that the
   *        function is anonymous.
   * @param source the node defining this function. Its type
   *        ({@link Node#getType()}) must be {@link Token#FUNCTION}.
   * @param parameters the function's parameters or {@code null}
   *        to indicate that the parameter types are unknown.
   * @param returnType the function's return type or {@code null} to indicate
   *        that the return type is unknown.
   * @param typeOfThis The type of {@code this} in non-constructors.  May be
   *        {@code null} to indicate that the type of {@code this} is unknown.
   */
  public FunctionType(JSTypeRegistry registry, String name, Node source,
      Node parameters, JSType returnType, ObjectType typeOfThis) {
    this(registry, name, source, parameters, returnType, typeOfThis,
        null, false, false);
  }

  /**
   * Creates a function type.
   * @param registry the owner registry for this type
   * @param name the function's name or {@code null} to indicate that the
   *        function is anonymous.
   * @param source the node defining this function. Its type
   *        ({@link Node#getType()}) must be {@link Token#FUNCTION}.
   * @param parameters the function's parameters or {@code null}
   *        to indicate that the parameter types are unknown.
   * @param returnType the function's return type or {@code null} to indicate
   *        that the return type is unknown.
   * @param typeOfThis The type of {@code this} in non-constructors.  May be
   *        {@code null} to indicate that the type of {@code this} is unknown.
   * @param templateTypeName The template type name or {@code null}.
   */
  public FunctionType(JSTypeRegistry registry, String name, Node source,
      Node parameters, JSType returnType, ObjectType typeOfThis,
      String templateTypeName) {
    this(registry, name, source, parameters, returnType, typeOfThis,
        templateTypeName, false, false);
  }

  /** Creates an instance for a function that might be a constructor. */
  FunctionType(JSTypeRegistry registry, String name, Node source,
      Node parameters, JSType returnType, ObjectType typeOfThis,
      String templateTypeName,  boolean isConstructor, boolean nativeType) {
    super(registry, name,
        registry.getNativeObjectType(JSTypeNative.FUNCTION_INSTANCE_TYPE),
        nativeType);
    Preconditions.checkArgument(source == null ||
        Token.FUNCTION == source.getType());
    this.source = source;
    this.kind = isConstructor ? Kind.CONSTRUCTOR : Kind.ORDINARY;
    if (isConstructor) {
      this.typeOfThis = typeOfThis != null && typeOfThis.isNoObjectType() ?
          typeOfThis : new InstanceObjectType(registry, this, nativeType);
    } else {
      this.typeOfThis = typeOfThis != null ?
          typeOfThis :
          registry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE);
    }
    // The call type should be set up last because we are calling getReturnType,
    // which may be overloaded and depend on other properties being set.
    this.call = new ArrowType(registry, parameters,
        (returnType == null ? getReturnType() : returnType));
    this.templateTypeName = templateTypeName;
  }

  /** Creates an instance for a function that is an interface. */
  FunctionType(JSTypeRegistry registry, String name, Node source) {
    super(registry, name,
        registry.getNativeObjectType(JSTypeNative.FUNCTION_INSTANCE_TYPE));
    Preconditions.checkArgument(source == null ||
        Token.FUNCTION == source.getType());
    Preconditions.checkArgument(name != null);
    this.source = source;
    this.call = null;
    this.kind = Kind.INTERFACE;
    this.typeOfThis = new InstanceObjectType(registry, this);
  }

  @Override
  public boolean isInstanceType() {
    // The universal constructor is its own instance, bizarrely.
    return equals(registry.getNativeType(U2U_CONSTRUCTOR_TYPE));
  }

  @Override
  public boolean isConstructor() {
    return kind == Kind.CONSTRUCTOR;
  }

  @Override
  public boolean isInterface() {
    return kind == Kind.INTERFACE;
  }

  @Override
  public boolean isOrdinaryFunction() {
    return kind == Kind.ORDINARY;
  }

  @Override
  public boolean isFunctionType() {
    return true;
  }

  @Override
  public boolean canBeCalled() {
    return true;
  }

  public Iterable<Node> getParameters() {
    Node n = getParametersNode();
    if (n != null) {
      return n.children();
    } else {
      return Collections.emptySet();
    }
  }

  /** Gets an LP node that contains all params. May be null. */
  public Node getParametersNode() {
    return call == null ? null : call.parameters;
  }

  /** Gets the minimum number of arguments that this function requires. */
  public int getMinArguments() {
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
   * Gets the maximum number of arguments that this function requires,
   * or Integer.MAX_VALUE if this is a variable argument function.
   */
  public int getMaxArguments() {
    Node params = getParametersNode();
    if (params != null) {
      Node lastParam = params.getLastChild();
      if (lastParam == null || !lastParam.isVarArgs()) {
        return params.getChildCount();
      }
    }

    return Integer.MAX_VALUE;
  }

  public JSType getReturnType() {
    return call == null ? null : call.returnType;
  }

  /**
   * Gets the {@code prototype} property of this function type. This is
   * equivalent to {@code (ObjectType) getPropertyType("prototype")}.
   */
  public FunctionPrototypeType getPrototype() {
    // lazy initialization of the prototype field
    if (prototype == null) {
      setPrototype(new FunctionPrototypeType(registry, this, null));
    }
    return prototype;
  }

  /**
   * Sets the prototype, creating the prototype object from the given
   * base type.
   * @param baseType The base type.
   */
  public void setPrototypeBasedOn(ObjectType baseType) {
    if (prototype == null) {
      setPrototype(
          new FunctionPrototypeType(
              registry, this, baseType, isNativeObjectType()));
    } else {
      prototype.setImplicitPrototype(baseType);
    }
  }

  /**
   * Sets the prototype.
   * @param prototype the prototype. If this value is {@code null} it will
   *        silently be discarded.
   */
  public boolean setPrototype(FunctionPrototypeType prototype) {
    if (prototype == null) {
      return false;
    }
    // getInstanceType fails if the function is not a constructor
    if (isConstructor() && prototype == getInstanceType()) {
      return false;
    }

    this.prototype = prototype;

    if (isConstructor() || isInterface()) {
      FunctionType superClass = getSuperClassConstructor();
      if (superClass != null) {
        superClass.addSubType(this);
      }
    }
    return true;
  }

  /**
   * Returns all interfaces implemented by a class or its superclass and any
   * superclasses for any of those interfaces. If this is called before all
   * types are resolved, it may return an incomplete set.
   */
  public Iterable<ObjectType> getAllImplementedInterfaces() {
    Set<ObjectType> interfaces = Sets.newHashSet();

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

      set.add(instance);

      if (constructor.getSuperClassConstructor() != null) {
        addRelatedInterfaces(
            constructor.getSuperClassConstructor().getInstanceType(), set);
      }
    }
  }

  /** Returns interfaces implemented directly by a class or its superclass. */
  public Iterable<ObjectType> getImplementedInterfaces() {
    return implementedInterfaces;
  }

  public void setImplementedInterfaces(List<ObjectType> implementedInterfaces) {
    // Records this type for each implemented interface.
    for (ObjectType type : implementedInterfaces) {
      registry.registerTypeImplementingInterface(this, type);
    }
    this.implementedInterfaces = ImmutableList.copyOf(implementedInterfaces);
  }

  @Override
  public boolean hasProperty(String name) {
    return super.hasProperty(name) || "prototype".equals(name);
  }

  @Override
  public JSType getPropertyType(String name) {
    if ("prototype".equals(name)) {
      return getPrototype();
    } else {
      if (!hasOwnProperty(name)) {
        if ("call".equals(name)) {
          // Define the "call" function lazily.
          Node params = getParametersNode();
          if (params == null) {
            // If there's no params array, don't do any type-checking
            // in this CALL function.
            defineDeclaredProperty(name,
                new FunctionType(registry, null, null,
                    null, getReturnType()),
                false);
          } else {
            params = params.cloneTree();
            Node thisTypeNode = Node.newString(Token.NAME, "thisType");
            thisTypeNode.setJSType(
                registry.createOptionalNullableType(getTypeOfThis()));
            params.addChildToFront(thisTypeNode);
            thisTypeNode.setOptionalArg(true);

            defineDeclaredProperty(name,
                new FunctionType(registry, null, null,
                    params, getReturnType()),
                false);
          }
        } else if ("apply".equals(name)) {
          // Define the "apply" function lazily.
          FunctionParamBuilder builder = new FunctionParamBuilder(registry);

          // Ecma-262 says that apply's second argument must be an Array
          // or an arguments object. We don't model the arguments object,
          // so let's just be forgiving for now.
          // TODO(nicksantos): Model the Arguments object.
          builder.addOptionalParams(
              registry.createNullableType(getTypeOfThis()),
              registry.createNullableType(
                  registry.getNativeType(JSTypeNative.OBJECT_TYPE)));

          defineDeclaredProperty(name,
              new FunctionType(registry, null, null,
                  builder.build(), getReturnType()),
              false);
        }
      }

      return super.getPropertyType(name);
    }
  }

  @Override
  boolean defineProperty(String name, JSType type,
      boolean inferred, boolean inExterns) {
    if ("prototype".equals(name)) {
      ObjectType objType = type.toObjectType();
      if (objType != null) {
        return setPrototype(
            new FunctionPrototypeType(
                registry, this, objType, isNativeObjectType()));
      } else {
        return false;
      }
    }
    return super.defineProperty(name, type, inferred, inExterns);
  }

  @Override
  public boolean isPropertyTypeInferred(String property) {
    return "prototype".equals(property) ||
        super.isPropertyTypeInferred(property);
  }

  @Override
  public JSType getLeastSupertype(JSType that) {
    // NOTE(nicksantos): When we remove the unknown type, the function types
    // form a lattice with the universal constructor at the top of the lattice,
    // and the NoObject type at the bottom of the lattice.
    //
    // When we introduce the unknown type, it's much more difficult to make
    // heads or tails of the partial ordering of types, because there's no
    // clear hierarchy between the different components (parameter types and
    // return types) in the ArrowType.
    //
    // Rather than make the situation more complicated by introducing new
    // types (like unions of functions), we just fallback on the simpler
    // approach of using the universal constructor and the AnyObject as
    // the supremum and infinum of all function types.
    if (isFunctionType() && that.isFunctionType()) {
      if (equals(that)) {
        return this;
      }

      JSType functionInstance = registry.getNativeType(
          JSTypeNative.FUNCTION_INSTANCE_TYPE);
      if (functionInstance.equals(that)) {
        return that;
      } else if (functionInstance.equals(this)) {
        return this;
      }

      return registry.getNativeType(JSTypeNative.U2U_CONSTRUCTOR_TYPE);
    }

    return super.getLeastSupertype(that);
  }

  @Override
  public JSType getGreatestSubtype(JSType that) {
    if (isFunctionType() && that.isFunctionType()) {
      if (equals(that)) {
        return this;
      }

      JSType functionInstance = registry.getNativeType(
          JSTypeNative.FUNCTION_INSTANCE_TYPE);
      if (functionInstance.equals(that)) {
        return this;
      } else if (functionInstance.equals(this)) {
        return that;
      }

      return registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE);
    }

    return super.getGreatestSubtype(that);
  }

  /**
   * Given a constructor or an interface type, get its superclass constructor
   * or {@code null} if none exists.
   */
  public FunctionType getSuperClassConstructor() {
    Preconditions.checkArgument(isConstructor() || isInterface());
    ObjectType maybeSuperInstanceType = getPrototype().getImplicitPrototype();
    if (maybeSuperInstanceType == null) {
      return null;
    }
    return maybeSuperInstanceType.getConstructor();
  }

  /**
   * Given a constructor or an interface type, find out whether the unknown
   * type is a supertype of the current type.
   */
  public boolean hasUnknownSupertype() {
    Preconditions.checkArgument(isConstructor() || isInterface());
    Preconditions.checkArgument(!this.isUnknownType());
    // Potential infinite loop if our type system messes up or someone defines
    // a bad type. Otherwise the loop should always end.
    FunctionType ctor = this;
    while (true) {
      ObjectType maybeSuperInstanceType =
          ctor.getPrototype().getImplicitPrototype();
      if (maybeSuperInstanceType == null) {
        return false;
      }
      if (maybeSuperInstanceType.isUnknownType()) {
        return true;
      }
      ctor = maybeSuperInstanceType.getConstructor();
      if (ctor == null) {
        return false;
      }
      Preconditions.checkState(ctor.isConstructor() || ctor.isInterface());
    }
  }

  /**
   * Given a constructor or an interface type and a property, finds the
   * top-most superclass that has the property defined (including this
   * constructor).
   */
  public JSType getTopMostDefiningType(String propertyName) {
    Preconditions.checkState(isConstructor() || isInterface());
    Preconditions.checkArgument(getPrototype().hasProperty(propertyName));
    FunctionType ctor = this;
    JSType topInstanceType;
    do {
      topInstanceType = ctor.getInstanceType();
      ctor = ctor.getSuperClassConstructor();
    } while (ctor != null && ctor.getPrototype().hasProperty(propertyName));
    return topInstanceType;
  }

  /**
   * Two function types are equal if their signatures match. Since they don't
   * have signatures, two interfaces are equal if their names match.
   */
  @Override
  public boolean equals(Object otherType) {
    if (!(otherType instanceof FunctionType)) {
      return false;
    }
    FunctionType that = (FunctionType) otherType;
    if (!that.isFunctionType()) {
      return false;
    }
    if (this.isConstructor()) {
      if (that.isConstructor()) {
        return this == that;
      }
      return false;
    }
    if (this.isInterface()) {
      if (that.isInterface()) {
        return this.getReferenceName().equals(that.getReferenceName());
      }
      return false;
    }
    if (that.isInterface()) {
      return false;
    }
    return this.typeOfThis.equals(that.typeOfThis) &&
        this.call.equals(that.call);
  }

  @Override
  public int hashCode() {
    return isInterface() ? getReferenceName().hashCode() : call.hashCode();
  }

  public boolean hasEqualCallType(FunctionType otherType) {
    return this.call.equals(otherType.call);
  }

  /**
   * Informally, a function is represented by
   * {@code function (params): returnType} where the {@code params} is a comma
   * separated list of types, the first one being a special
   * {@code this:T} if the function expects a known type for {@code this}.
   */
  @Override
  public String toString() {
    if (this == registry.getNativeType(JSTypeNative.FUNCTION_INSTANCE_TYPE)) {
      return "Function";
    }

    StringBuilder b = new StringBuilder(32);
    b.append("function (");
    int paramNum = (call == null || call.parameters == null) ?
        0 : call.parameters.getChildCount();
    boolean hasKnownTypeOfThis = !typeOfThis.isUnknownType();
    if (hasKnownTypeOfThis) {
      b.append("this:");
      b.append(typeOfThis.toString());
    }
    if (paramNum > 0) {
      if (hasKnownTypeOfThis) {
        b.append(", ");
      }
      Node p = call.parameters.getFirstChild();
      if (p.isVarArgs()) {
        appendVarArgsString(b, p.getJSType());
      } else {
        b.append(p.getJSType().toString());
      }
      p = p.getNext();
      while (p != null) {
        b.append(", ");
        if (p.isVarArgs()) {
          appendVarArgsString(b, p.getJSType());
        } else {
          b.append(p.getJSType().toString());
        }
        p = p.getNext();
      }
    }
    b.append(")");
    if (call != null && call.returnType != null) {
      b.append(": ");
      b.append(call.returnType);
    }
    return b.toString();
  }

  /** Gets the string representation of a var args param. */
  private void appendVarArgsString(StringBuilder builder, JSType paramType) {
    if (paramType.isUnionType()) {
      // Remove the optionalness from the var arg.
      paramType = ((UnionType) paramType).getRestrictedUnion(
          registry.getNativeType(JSTypeNative.VOID_TYPE));
    }
    builder.append("...[").append(paramType.toString()).append("]");
  }

  /**
   * A function is a subtype of another if their call methods are related via
   * subtyping and {@code this} is a subtype of {@code that} with regard to
   * the prototype chain.
   */
  @Override
  public boolean isSubtype(JSType that) {
    if (this.equals(that)) {
      return true;
    }
    if (that.isFunctionType()) {
      if (((FunctionType) that).isInterface()) {
        // Any function can be assigned to an interface function.
        return true;
      }
      if (this.isInterface()) {
        // An interface function cannot be assigned to anything.
        return false;
      }
      // If functionA is a subtype of functionB, then their "this" types
      // should be contravariant. However, this causes problems because
      // of the way we enforce overrides. Because function(this:SubFoo)
      // is not a subtype of function(this:Foo), our override check treats
      // this as an error. It also screws up out standard method
      // for aliasing constructors. Let's punt on all this for now.
      // TODO(nicksantos): fix this.
      FunctionType other = (FunctionType) that;
      return (this.isConstructor() || other.isConstructor() ||
              other.typeOfThis.isSubtype(this.typeOfThis) ||
              this.typeOfThis.isSubtype(other.typeOfThis)) &&
          this.call.isSubtype(other.call);
    }
    if (that instanceof UnionType) {
      UnionType union = (UnionType) that;
      for (JSType element : union.alternates) {
        if (this.isSubtype(element)) {
          return true;
        }
      }
    }
    return getNativeType(JSTypeNative.FUNCTION_PROTOTYPE).isSubtype(that);
  }

  @Override
  public <T> T visit(Visitor<T> visitor) {
    return visitor.caseFunctionType(this);
  }

  /**
   * Gets the type of instance of this function.
   * @throws IllegalStateException if this function is not a constructor
   *         (see {@link #isConstructor()}).
   */
  public ObjectType getInstanceType() {
    Preconditions.checkState(hasInstanceType());
    return typeOfThis;
  }

  /** Sets the instance type. This should only be used for special native types. */
  void setInstanceType(ObjectType instanceType) {
    typeOfThis = instanceType;
  }

  /**
   * Returns whether this function type has an instance type.
   */
  public boolean hasInstanceType() {
    return isConstructor() || isInterface();
  }

  /**
   * Gets the type of {@code this} in this function.
   */
  public ObjectType getTypeOfThis() {
    return typeOfThis.isNoObjectType() ?
        registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE) : typeOfThis;
  }

  /**
   * Gets the source node.
   */
  public Node getSource() {
    return source;
  }

  /**
   * Sets the source node.
   */
  public void setSource(Node source) {
    this.source = source;
  }

  /** Adds a type to the list of subtypes for this type. */
  private void addSubType(FunctionType subType) {
    if (subTypes == null) {
      subTypes = Lists.newArrayList();
    }
    subTypes.add(subType);
  }

  /**
   * Returns a list of types that are subtypes of this type. This is only valid
   * for constructor functions, and may be null. This allows a downward
   * traversal of the subtype graph.
   */
  public List<FunctionType> getSubTypes() {
    return subTypes;
  }

  @Override
  public boolean hasCachedValues() {
    return prototype != null || super.hasCachedValues();
  }

  /**
   * Gets the template type name.
   */
  public String getTemplateTypeName() {
    return templateTypeName;
  }
}
