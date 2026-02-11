/*
 * Copyright 2007 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import com.google.javascript.rhino.ClosurePrimitive;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.NominalTypeBuilder;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * CodingConvention defines a set of hooks to customize the behavior of the
 * Compiler for a specific team/company.
 */
@Immutable
public interface CodingConvention extends Serializable {

  /**
   * This checks whether a given variable name, such as a name in all-caps
   * should be treated as if it had the @const annotation.
   *
   * @param variableName potentially constant variable name
   * @return {@code true} if the name should be treated as a constant.
   */
  public boolean isConstant(String variableName);

  /**
   * This checks whether a given key of an object literal, such as a
   * name in all-caps should be treated as if it had the @const
   * annotation.
   */
  public boolean isConstantKey(String keyName);

  /**
   * This checks that a given {@code key} may be used as a key for an enum.
   *
   * @param key the potential key to an enum
   * @return {@code true} if the {@code key} may be used as an enum key,
   *     {@code false} otherwise
   */
  public boolean isValidEnumKey(String key);

  /**
   * This checks whether a given parameter name should be treated as an
   * optional parameter as far as type checking or function call arg count
   * checking is concerned. Note that an optional function parameter may be
   * declared as a simple type and is automatically converted to a union of the
   * declared type and Undefined.
   *
   * @param parameter The parameter's node.
   * @return {@code true} if the parameter should be treated as an optional
   * parameter.
   */
  public boolean isOptionalParameter(Node parameter);

  /**
   * This checks whether a given parameter should be treated as a marker
   * for a variable argument list function. A VarArgs parameter must be the
   * last parameter in a function declaration.
   *
   * @param parameter The parameter's node.
   * @return {@code true} if the parameter should be treated as a variable
   * length parameter.
   */
  public boolean isVarArgsParameter(Node parameter);

  /**
   * Used by CheckMissingReturn. When a function call always throws an error,
   * it can be the last stm of a block and we don't warn about missing return.
   */
  public boolean isFunctionCallThatAlwaysThrows(Node n);

  /**
   * Checks whether a global variable or function name should be treated as
   * exported, or externally referenceable.
   *
   * @param name A global variable or function name.
   * @param local {@code true} if the name is a local variable.
   * @return {@code true} if the name should be considered exported.
   */
  public boolean isExported(String name, boolean local);

  /**
   * Equivalent to `isExported(name, true) || isExported(name, false);`
   *
   * <p>Should only be used to check if a property is exported. Variables should always use {@link
   * #isExported(String, boolean)}, as in most cases local variables should not be treated as
   * exported.
   *
   * <p>Do not override! Unfortunately, that cannot be enforced without making this an abstract
   * class.
   */
  default boolean isExported(String name) {
    return isExported(name, true) || isExported(name, false);
  }

  /**
   * No-op convention that is never used.
   *
   * @deprecated since this has a default value and is never checked, you can safely remove any
   *     overrides of this method from your coding conventions.
   */
  @Deprecated
  public default boolean blockRenamingForProperty(String name) {
    return false;
  }

  /**
   * @return the package name for the given source file, or null if
   *     no package name is known.
   */
  public String getPackageName(StaticSourceFile source);

  /**
   * Checks if the given method defines a subclass relationship,
   * and if it does, returns information on that relationship. By default,
   * always returns null. Meant to be overridden by subclasses.
   *
   * @param callNode A CALL node.
   */
  public SubclassRelationship getClassesDefinedByCall(Node callNode);

  /**
   * Checks if the given method is a call to a class factory, such a factory returns a
   * unique class.
   *
   * @param callNode A CALL node.
   */
  public boolean isClassFactoryCall(Node callNode);

  /**
   * Returns true if passed a string referring to the superclass.  The string
   * will usually be from the string node at the right of a GETPROP, e.g.
   * this.superClass_.
   */
  public boolean isSuperClassReference(String propertyName);

  /**
   * Convenience method for determining if the node indicates the file
   * is a "module" file (a file whose top level symbols are not in global
   * scope).
   */
  boolean extractIsModuleFile(Node node, Node parent);

  /**
   * Convenience method for determining provided dependencies amongst different
   * JS scripts.
   */
  public String extractClassNameIfProvide(Node node, Node parent);

  /**
   * Convenience method for determining required dependencies amongst different
   * JS scripts.
   */
  public String extractClassNameIfRequire(Node node, Node parent);

  /**
   * Function name used when exporting properties.
   * Signature: fn(object, publicName, symbol).
   * @return function name.
   */
  public String getExportPropertyFunction();

  /**
   * Function name used when exporting symbols.
   * Signature: fn(publicPath, object).
   * @return function name.
   */
  public String getExportSymbolFunction();

  /**
   * Checks if the given CALL node is forward-declaring any types,
   * and returns the name of the types if it is.
   */
  public List<String> identifyTypeDeclarationCall(Node n);

  /**
   * In many JS libraries, the function that produces inheritance also
   * adds properties to the superclass and/or subclass.
   */
  public void applySubclassRelationship(
      NominalTypeBuilder parent, NominalTypeBuilder child, SubclassType type);

  /**
   * Function name for abstract methods. An abstract method can be assigned to
   * an interface method instead of an function expression in order to avoid
   * linter warnings produced by assigning a function without a return value
   * where a return value is expected.
   * @return function name.
   */
  public String getAbstractMethodName();

  /**
   * Checks if the given method defines a singleton getter, and if it does,
   * returns the name of the class with the singleton getter. By default, always
   * returns null. Meant to be overridden by subclasses.
   *
   * addSingletonGetter needs a coding convention because in the general case,
   * it can't be inlined. The function inliner sees that it creates an alias
   * to the given class in an inner closure, and bails out.
   *
   * @param callNode A CALL node.
   */
  public String getSingletonGetterClassName(Node callNode);

  /**
   * In many JS libraries, the function that adds a singleton getter to a class
   * adds properties to the class.
   */
  public void applySingletonGetter(
      NominalTypeBuilder classType, FunctionType getterType);

  /**
   * A Bind instance or null.
   *
   * <p>When seeing an expression exp1.bind(recv, arg1, ...); we only know that it's a function bind
   * if exp1 has type function. W/out type info, exp1 has certainly a function type only if it's a
   * function literal.
   *
   * <p>If type checking has already happened, exp1's type is attached to the AST node. When
   * checkTypes is true, describeFunctionBind looks for that type.
   *
   * @param checkTypes Check that the bound node has a function type.
   */
  public Bind describeFunctionBind(Node n, boolean checkTypes);

  /** Bind class */
  public static class Bind {
    // The target of the bind action
    final Node target;
    // The node representing the "this" value, maybe null
    final Node thisValue;
    // The head of a Node list representing the parameters
    final Node parameters;

    public Bind(Node target, Node thisValue, Node parameters) {
      this.target = target;
      this.thisValue = thisValue;
      this.parameters = parameters;
    }

    /**
     * The number of parameters bound (not including the 'this' value).
     */
    int getBoundParameterCount() {
      if (parameters == null) {
        return 0;
      }
      Node paramParent = parameters.getParent();
      return paramParent.getChildCount() - paramParent.getIndexOfChild(parameters);
    }
  }

  /**
   * Builds a {@link Cache} instance from the given call node and returns that instance, or null
   * if the {@link Node} does not resemble a cache utility call.
   *
   * <p>This should match calls to a cache utility method. This type of node is specially considered
   * for side-effects since conventionally storing something on a cache object would be seen as a
   * side-effect.
   *
   */
  public Cache describeCachingCall(Node node);

  /** Cache class */
  public static class Cache {
    final Node cacheObj;
    final Node key;
    final Node valueFn;
    final Node keyFn;

    public Cache(Node cacheObj, Node key, Node valueFn, Node keyFn) {
      this.cacheObj = cacheObj;
      this.key = key;
      this.valueFn = valueFn;
      this.keyFn = keyFn;
    }
  }

  /**
   * Whether this CALL function is testing for the existence of a property.
   */
  public boolean isPropertyTestFunction(Node call);

  /**
   * Whether this GETPROP node is an alias for an object prototype.
   */
  public boolean isPrototypeAlias(Node getProp);

  /**
   * Whether this GETPROP or NAME node is the function is returning the string name for a property,
   * but allows renaming.
   */
  public boolean isPropertyRenameFunction(Node nameNode);

  /**
   * Checks if the given method performs a object literal cast, and if it does,
   * returns information on the cast. By default, always returns null. Meant
   * to be overridden by subclasses.
   *
   * @param callNode A CALL node.
   */
  public ObjectLiteralCast getObjectLiteralCast(Node callNode);

  /**
   * Returns the set of AssertionFunction.
   */
  public Collection<AssertionFunctionSpec> getAssertionFunctions();

  /** Specify the kind of inheritance */
  static enum SubclassType {
    INHERITS,
    MIXIN
  }

  /** Record subclass relations */
  static class SubclassRelationship {
    final SubclassType type;
    final String subclassName;
    final String superclassName;

    public SubclassRelationship(
        SubclassType type, Node subclassNode, Node superclassNode) {
      checkArgument(
          subclassNode.isQualifiedName(), "Expected qualified name, found: %s", subclassNode);
      checkArgument(
          superclassNode.isQualifiedName(), "Expected qualified name, found: %s", superclassNode);
      this.type = type;
      this.subclassName = subclassNode.getQualifiedName();
      this.superclassName = superclassNode.getQualifiedName();
    }
  }

  /**
   * An object literal cast provides a mechanism to cast object literals to
   * other types without a warning.
   */
  static class ObjectLiteralCast {
    /** Type to cast to. */
    final String typeName;

    /** Object to cast. */
    final Node objectNode;

    /** Error message */
    final DiagnosticType diagnosticType;

    ObjectLiteralCast(String typeName, Node objectNode,
        DiagnosticType diagnosticType) {
      this.typeName = typeName;
      this.objectNode = objectNode;
      this.diagnosticType = diagnosticType;
    }
  }

  /**
   * A description of a JavaScript function that will throw an exception when either:
   *
   * <ul>
   *   <li>One of its parameters does not match the return type of the function
   *   <li>One of its parameters is falsy. This has some special handling for expressions that the
   *       match-return-type handling does not have.
   * </ul>
   */
  @Immutable
  @AutoValue
  abstract class AssertionFunctionSpec {
    // TODO(b/126254920): remove this field and always use ClosurePrimitive
    abstract @Nullable String getFunctionName();

    abstract @Nullable ClosurePrimitive getClosurePrimitive();

    abstract AssertionKind getAssertionKind();

    abstract int getParamIndex(); // the index of the formal parameter that is actually asserted

    public enum AssertionKind {
      TRUTHY, // an assertion that the parameter is 'truthy' and returns the parameter
      MATCHES_RETURN_TYPE // an assertion that the parameter matches the inferred return kind
    }

    static Builder builder() {
      return new AutoValue_CodingConvention_AssertionFunctionSpec.Builder().setParamIndex(0);
    }

    public static Builder forTruthy() {
      return builder().setAssertionKind(AssertionKind.TRUTHY);
    }

    public static Builder forMatchesReturn() {
      return builder().setAssertionKind(AssertionKind.MATCHES_RETURN_TYPE);
    }

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setFunctionName(String name);

      abstract Builder setClosurePrimitive(ClosurePrimitive primitive);

      public abstract Builder setParamIndex(int paramIndex);

      abstract Builder setAssertionKind(AssertionKind kind);

      abstract AssertionFunctionSpec autoBuild();

      public AssertionFunctionSpec build() {
        AssertionFunctionSpec spec = autoBuild();
        Preconditions.checkState(
            spec.getFunctionName() != null || spec.getClosurePrimitive() != null,
            "Must provide a function name or ClosurePrimitive for each spec");
        return spec;
      }
    }

    private Object getId() {
      return getClosurePrimitive() != null ? getClosurePrimitive() : getFunctionName();
    }

    /** Returns which argument is actually being asserted, or null if fewer args than expected */
    @Nullable
    Node getAssertedArg(Node firstArg) {
      for (int i = 0; i < getParamIndex(); i++) {
        if (firstArg == null) {
          // If there are fewer arguments than expected, return null instead of crashing in this
          // function.
          return null;
        }
        firstArg = firstArg.getNext();
      }
      return firstArg;
    }
  }

  /** This stores a relation from either name or Closure Primitive to assertion function */
  @Immutable
  final class AssertionFunctionLookup {
    // the key type 'Object' is mutable, but at runtime is only ever a string or ClosurePrimitive
    @SuppressWarnings("Immutable")
    private final ImmutableMap<Object, AssertionFunctionSpec> internal;

    private AssertionFunctionLookup(ImmutableMap<Object, AssertionFunctionSpec> internal) {
      this.internal = internal;
    }

    /**
     * Returns a new map containing all the given {@link AssertionFunctionSpec}s
     *
     * <p>Assumes that in the input, there is a unique mapping from string name to spec and closure
     * primitive to spec.
     */
    static AssertionFunctionLookup of(Collection<AssertionFunctionSpec> specs) {
      ImmutableMap<Object, AssertionFunctionSpec> idToSpecMap =
          specs.stream().collect(toImmutableMap(AssertionFunctionSpec::getId, Function.identity()));

      return new AssertionFunctionLookup(idToSpecMap);
    }

    /**
     * Returns the {@link AssertionFunctionSpec} matching the given function reference.
     *
     * <p>This first looks up specs by their ClosurePrimitive, then falls back to qualified name
     */
    @Nullable
    AssertionFunctionSpec lookupByCallee(Node callee) {
      FunctionType fnType = JSType.toMaybeFunctionType(callee.getJSType());
      if (fnType != null && fnType.getClosurePrimitive() != null) {
        AssertionFunctionSpec spec = internal.get(fnType.getClosurePrimitive());
        if (spec != null) {
          return spec;
        }
      }

      // TODO(b/126254920): remove this
      if (callee.isQualifiedName()) {
        return internal.get(callee.getQualifiedName());
      }

      return null;
    }
  }
}
