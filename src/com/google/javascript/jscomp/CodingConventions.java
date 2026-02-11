/*
 * Copyright 2011 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.javascript.rhino.ClosurePrimitive;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.NominalTypeBuilder;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import java.util.Collection;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Helper classes for dealing with coding conventions.
 */
public final class CodingConventions {

  private CodingConventions() {}

  /** Gets the default coding convention. */
  public static CodingConvention getDefault() {
    return new DefaultCodingConvention();
  }

  /**
   * @param n The last statement of a block to check for an always throws
   *     function call. Used by CheckMissingReturn.
   * @param alwaysThrowsFunctionName The name of a function that always throws.
   * @return {@code true} if n is call to alwaysThrowsFunctionName, otherwise
   *     {@code false}.
   */
  public static boolean defaultIsFunctionCallThatAlwaysThrows(
      Node n, String alwaysThrowsFunctionName) {
    if (n.isExprResult()) {
      if (!n.getFirstChild().isCall()) {
        return false;
      }
    } else if (!n.isCall()) {
      return false;
    }
    if (n.isExprResult()) {
      n = n.getFirstChild();
    }
    // n is a call
    return n.getFirstChild().matchesQualifiedName(alwaysThrowsFunctionName);
  }

  /**
   * A convention that wraps another.
   *
   * When you want to support a new library, you should subclass this
   * delegate, and override the methods that you want to customize.
   *
   * This way, a person using jQuery and Closure Library can create a new
   * coding convention by creating a jQueryCodingConvention that delegates
   * to a ClosureCodingConvention that delegates to a DefaultCodingConvention.
   */
  @Immutable
  public static class Proxy implements CodingConvention {

    protected final CodingConvention nextConvention;

    protected Proxy(CodingConvention convention) {
      this.nextConvention = convention;
    }

    @Override
    public boolean isConstant(String variableName) {
      return nextConvention.isConstant(variableName);
    }

    @Override public boolean isConstantKey(String keyName) {
      return nextConvention.isConstantKey(keyName);
    }

    @Override
    public boolean isValidEnumKey(String key) {
      return nextConvention.isValidEnumKey(key);
    }

    @Override
    public boolean isOptionalParameter(Node parameter) {
      return nextConvention.isOptionalParameter(parameter);
    }

    @Override
    public boolean isVarArgsParameter(Node parameter) {
      return nextConvention.isVarArgsParameter(parameter);
    }

    @Override
    public boolean isFunctionCallThatAlwaysThrows(Node n) {
      return nextConvention.isFunctionCallThatAlwaysThrows(n);
    }

    @Override
    public boolean isExported(String name, boolean local) {
      return nextConvention.isExported(name, local);
    }

    @Override
    public final boolean isExported(String name) {
      return CodingConvention.super.isExported(name);
    }

    @Override
    public String getPackageName(StaticSourceFile source) {
      return nextConvention.getPackageName(source);
    }

    @Override
    public SubclassRelationship getClassesDefinedByCall(Node callNode) {
      return nextConvention.getClassesDefinedByCall(callNode);
    }

    @Override
    public boolean isClassFactoryCall(Node callNode) {
      return nextConvention.isClassFactoryCall(callNode);
    }

    @Override
    public boolean isSuperClassReference(String propertyName) {
      return nextConvention.isSuperClassReference(propertyName);
    }

    @Override
    public boolean extractIsModuleFile(Node node, Node parent) {
      return nextConvention.extractIsModuleFile(node, parent);
    }

    @Override
    public String extractClassNameIfProvide(Node node, Node parent) {
      return nextConvention.extractClassNameIfProvide(node, parent);
    }

    @Override
    public String extractClassNameIfRequire(Node node, Node parent) {
      return nextConvention.extractClassNameIfRequire(node, parent);
    }

    @Override
    public String getExportPropertyFunction() {
      return nextConvention.getExportPropertyFunction();
    }

    @Override
    public String getExportSymbolFunction() {
      return nextConvention.getExportSymbolFunction();
    }

    @Override
    public List<String> identifyTypeDeclarationCall(Node n) {
      return nextConvention.identifyTypeDeclarationCall(n);
    }

    @Override
    public void applySubclassRelationship(
        NominalTypeBuilder parent, NominalTypeBuilder child, SubclassType type) {
      nextConvention.applySubclassRelationship(parent, child, type);
    }

    @Override
    public String getAbstractMethodName() {
      return nextConvention.getAbstractMethodName();
    }

    @Override
    public String getSingletonGetterClassName(Node callNode) {
      return nextConvention.getSingletonGetterClassName(callNode);
    }

    @Override
    public void applySingletonGetter(
        NominalTypeBuilder classType, FunctionType getterType) {
      nextConvention.applySingletonGetter(classType, getterType);
    }

    @Override
    public Collection<AssertionFunctionSpec> getAssertionFunctions() {
      return nextConvention.getAssertionFunctions();
    }

    @Override
    public Bind describeFunctionBind(Node n, boolean checkTypes) {
      return nextConvention.describeFunctionBind(n, checkTypes);
    }

    @Override
    public Cache describeCachingCall(Node node) {
      return nextConvention.describeCachingCall(node);
    }

    @Override
    public boolean isPropertyTestFunction(Node call) {
      return nextConvention.isPropertyTestFunction(call);
    }

    @Override
    public boolean isPropertyRenameFunction(Node nameNode) {
      return nextConvention.isPropertyRenameFunction(nameNode);
    }

    @Override
    public boolean isPrototypeAlias(Node getProp) {
      return false;
    }

    @Override
    public ObjectLiteralCast getObjectLiteralCast(Node callNode) {
      return nextConvention.getObjectLiteralCast(callNode);
    }
  }


  /**
   * The default coding convention.
   * Should be at the bottom of all proxy chains.
   */
  @Immutable
  private static class DefaultCodingConvention implements CodingConvention {

    private static final long serialVersionUID = 1L;

    @Override
    public boolean isConstant(String variableName) {
      return false;
    }

    @Override
    public boolean isConstantKey(String variableName) {
      return false;
    }

    @Override
    public boolean isValidEnumKey(String key) {
      return key != null && key.length() > 0;
    }

    @Override
    public boolean isOptionalParameter(Node parameter) {
      return false;
    }

    @Override
    public boolean isVarArgsParameter(Node parameter) {
      // be as lax as possible
      return parameter.isRest();
    }

    @Override
    public boolean isFunctionCallThatAlwaysThrows(Node n) {
      if (NodeUtil.isExprCall(n)) {
        FunctionType fnType = FunctionType.toMaybeFunctionType(n.getFirstFirstChild().getJSType());
        return fnType != null && ClosurePrimitive.ASSERTS_FAIL == fnType.getClosurePrimitive();
      }
      return false;
    }

    @Override
    public String getPackageName(StaticSourceFile source) {
      // The package name of a source file is its file path.
      String name = source.getName();
      int lastSlash = name.lastIndexOf('/');
      return lastSlash == -1 ? "" : name.substring(0, lastSlash);
    }

    @Override
    public boolean isExported(String name, boolean local) {
      return local && name.startsWith("$super");
    }

    @Override
    public final boolean isExported(String name) {
      return CodingConvention.super.isExported(name);
    }

    private static final QualifiedName JSCOMP_INHERITS = QualifiedName.of("$jscomp.inherits");

    @Override
    public @Nullable SubclassRelationship getClassesDefinedByCall(Node callNode) {
      Node callName = callNode.getFirstChild();
      if ((JSCOMP_INHERITS.matches(callName) || callName.matchesName("$jscomp$inherits"))
          && callNode.hasXChildren(3)) {
        Node subclass = callName.getNext();
        Node superclass = subclass.getNext();
        // The StripCode pass may create $jscomp.inherits calls with NULL arguments.
        if (subclass.isQualifiedName() && superclass.isQualifiedName()) {
          return new SubclassRelationship(SubclassType.INHERITS, subclass, superclass);
        }
      }
      return null;
    }

    @Override
    public boolean isClassFactoryCall(Node callNode) {
      return false;
    }

    @Override
    public boolean isSuperClassReference(String propertyName) {
      return false;
    }

    @Override
    public boolean extractIsModuleFile(Node node, Node parent) {
      String message = "only implemented in ClosureCodingConvention";
      throw new UnsupportedOperationException(message);
    }

    @Override
    public String extractClassNameIfProvide(Node node, Node parent) {
      String message = "only implemented in ClosureCodingConvention";
      throw new UnsupportedOperationException(message);
    }

    @Override
    public String extractClassNameIfRequire(Node node, Node parent) {
      String message = "only implemented in ClosureCodingConvention";
      throw new UnsupportedOperationException(message);
    }

    @Override
    public String getExportPropertyFunction() {
      return null;
    }

    @Override
    public String getExportSymbolFunction() {
      return null;
    }

    @Override
    public List<String> identifyTypeDeclarationCall(Node n) {
      return ImmutableList.of();
    }

    @Override
    public void applySubclassRelationship(
        NominalTypeBuilder parent, NominalTypeBuilder child, SubclassType type) {
      // do nothing
    }

    @Override
    public String getAbstractMethodName() {
      return null;
    }

    @Override
    public String getSingletonGetterClassName(Node callNode) {
      return null;
    }

    @Override
    public void applySingletonGetter(
        NominalTypeBuilder classType, FunctionType getterType) {
      // do nothing.
    }

    @Override
    public boolean isPropertyTestFunction(Node call) {
      // Avoid building the qualified name and check for
      // "goog.isArray"
      Node target = call.getFirstChild();
      if (target.isGetProp()) {
        Node src = target.getFirstChild();
        String prop = target.getString();
        if (src.isName() && src.getString().equals("Array") && prop.equals("isArray")) {
          return true;
        }
      }
      return false;
    }

    @Override
    public boolean isPropertyRenameFunction(Node nameNode) {
      return nameNode.matchesName(NodeUtil.JSC_PROPERTY_NAME_FN);
    }

    @Override
    public boolean isPrototypeAlias(Node getProp) {
      return false;
    }

    @Override
    public ObjectLiteralCast getObjectLiteralCast(Node callNode) {
      return null;
    }

    @Override
    public ImmutableSet<AssertionFunctionSpec> getAssertionFunctions() {
      return ImmutableSet.of(
          AssertionFunctionSpec.forTruthy()
              .setClosurePrimitive(ClosurePrimitive.ASSERTS_TRUTHY)
              .build(),
          AssertionFunctionSpec.forMatchesReturn()
              .setClosurePrimitive(ClosurePrimitive.ASSERTS_MATCHES_RETURN)
              .build());
    }

    private static final QualifiedName FUNCTION_PROTOTYPE_BIND_CALL =
        QualifiedName.of("Function.prototype.bind.call");

    @Override
    public @Nullable Bind describeFunctionBind(Node n, boolean checkTypes) {
      if (!n.isCall()) {
        return null;
      }

      Node callTarget = n.getFirstChild();
      if (callTarget.isQualifiedName()) {
        if (FUNCTION_PROTOTYPE_BIND_CALL.matches(callTarget)) {
          // goog.bind(fn, self, args...);
          Node fn = callTarget.getNext();
          if (fn == null) {
            return null;
          }
          Node thisValue = safeNext(fn);
          Node parameters = safeNext(thisValue);
          return new Bind(fn, thisValue, parameters);
        }
      }

      if (callTarget.isGetProp() && callTarget.getString().equals("bind")) {
        Node maybeFn = callTarget.getFirstChild();
        JSType maybeFnType = maybeFn.getJSType();
        FunctionType fnType = null;
        if (checkTypes && maybeFnType != null) {
          fnType = maybeFnType.restrictByNotNullOrUndefined()
              .toMaybeFunctionType();
        }

        if (fnType != null || maybeFn.isFunction()) {
          // (function(){}).bind(self, args...);
          Node thisValue = callTarget.getNext();
          Node parameters = safeNext(thisValue);
          return new Bind(maybeFn, thisValue, parameters);
        }
      }

      return null;
    }

    @Override
    public Cache describeCachingCall(Node node) {
      return null;
    }

    private static @Nullable Node safeNext(Node n) {
      if (n != null) {
        return n.getNext();
      }
      return null;
    }
  }
}
