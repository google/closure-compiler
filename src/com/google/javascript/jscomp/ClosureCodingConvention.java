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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.ObjectType;

import java.util.Collection;
import java.util.List;
import java.util.Set;


/**
 * This describes the Closure-specific JavaScript coding conventions.
 *
 */
public class ClosureCodingConvention extends DefaultCodingConvention {

  private static final long serialVersionUID = 1L;

  static final DiagnosticType OBJECTLIT_EXPECTED = DiagnosticType.warning(
      "JSC_REFLECT_OBJECTLIT_EXPECTED",
      "Object literal expected as second argument");

  /**
   * Closure's goog.inherits adds a {@code superClass_} property to the
   * subclass, and a {@code constructor} property.
   */
  @Override
  public void applySubclassRelationship(FunctionType parentCtor,
      FunctionType childCtor, SubclassType type) {
    if (type == SubclassType.INHERITS) {
      childCtor.defineDeclaredProperty("superClass_",
          parentCtor.getPrototype(), parentCtor.getSource());
      childCtor.getPrototype().defineDeclaredProperty("constructor",
          childCtor, parentCtor.getSource());
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Understands several different inheritance patterns that occur in
   * Google code (various uses of {@code inherits} and {@code mixin}).
   */
  @Override
  public SubclassRelationship getClassesDefinedByCall(Node callNode) {
    Node callName = callNode.getFirstChild();
    SubclassType type = typeofClassDefiningName(callName);
    if (type != null) {
      Node subclass = null;
      Node superclass = callNode.getLastChild();

      // There are six possible syntaxes for a class-defining method:
      // SubClass.inherits(SuperClass)
      // goog.inherits(SubClass, SuperClass)
      // goog$inherits(SubClass, SuperClass)
      // SubClass.mixin(SuperClass.prototype)
      // goog.mixin(SubClass.prototype, SuperClass.prototype)
      // goog$mixin(SubClass.prototype, SuperClass.prototype)
      boolean isDeprecatedCall = callNode.getChildCount() == 2 &&
          callName.getType() == Token.GETPROP;
      if (isDeprecatedCall) {
        // SubClass.inherits(SuperClass)
        subclass = callName.getFirstChild();
      } else if (callNode.getChildCount() == 3) {
        // goog.inherits(SubClass, SuperClass)
        subclass = callName.getNext();
      } else {
        return null;
      }

      if (type == SubclassType.MIXIN) {
        // Only consider mixins that mix two prototypes as related to
        // inheritance.
        if (!endsWithPrototype(superclass)) {
          return null;
        }
        if (!isDeprecatedCall) {
          if (!endsWithPrototype(subclass)) {
            return null;
          }
          // Strip off the prototype from the name.
          subclass = subclass.getFirstChild();
        }
        superclass = superclass.getFirstChild();
      }

      // bail out if either of the side of the "inherits"
      // isn't a real class name. This prevents us from
      // doing something weird in cases like:
      // goog.inherits(MySubClass, cond ? SuperClass1 : BaseClass2)
      if (subclass != null &&
          subclass.isUnscopedQualifiedName() &&
          superclass.isUnscopedQualifiedName()) {
        return new SubclassRelationship(type, subclass, superclass);
      }
    }

    return null;
  }

  /**
   * Determines whether the given node is a class-defining name, like
   * "inherits" or "mixin."
   * @return The type of class-defining name, or null.
   */
  private SubclassType typeofClassDefiningName(Node callName) {
    // Check if the method name matches one of the class-defining methods.
    String methodName = null;
    if (callName.getType() == Token.GETPROP) {
      methodName = callName.getLastChild().getString();
    } else if (callName.getType() == Token.NAME) {
      String name = callName.getString();
      int dollarIndex = name.lastIndexOf('$');
      if (dollarIndex != -1) {
        methodName = name.substring(dollarIndex + 1);
      }
    }

    if (methodName != null) {
      if (methodName.equals("inherits")) {
        return SubclassType.INHERITS;
      } else if (methodName.equals("mixin")) {
        return SubclassType.MIXIN;
      }
    }
    return null;
  }

  @Override
  public boolean isSuperClassReference(String propertyName) {
    return "superClass_".equals(propertyName);
  }

  /**
   * Given a qualified name node, returns whether "prototype" is at the end.
   * For example:
   * a.b.c => false
   * a.b.c.prototype => true
   */
  private boolean endsWithPrototype(Node qualifiedName) {
    return qualifiedName.getType() == Token.GETPROP &&
        qualifiedName.getLastChild().getString().equals("prototype");
  }

  /**
   * Exctracts X from goog.provide('X'), if the applied Node is goog.
   *
   * @return The extracted class name, or null.
   */
  @Override
  public String extractClassNameIfProvide(Node node, Node parent){
    return extractClassNameIfGoog(node, parent, "goog.provide");
  }

  /**
   * Exctracts X from goog.require('X'), if the applied Node is goog.
   *
   * @return The extracted class name, or null.
   */
  @Override
  public String extractClassNameIfRequire(Node node, Node parent){
    return extractClassNameIfGoog(node, parent, "goog.require");
  }

  private static String extractClassNameIfGoog(Node node, Node parent,
      String functionName){
    String className = null;
    if (NodeUtil.isExprCall(parent)) {
      Node callee = node.getFirstChild();
      if (callee != null && callee.getType() == Token.GETPROP) {
        String qualifiedName = callee.getQualifiedName();
        if (functionName.equals(qualifiedName)) {
          Node target = callee.getNext();
          if (target != null && target.getType() == Token.STRING) {
            className = target.getString();
          }
        }
      }
    }
    return className;
  }

  /**
   * Use closure's implementation.
   * @return closure's function name for exporting properties.
   */
  @Override
  public String getExportPropertyFunction() {
    return "goog.exportProperty";
  }

  /**
   * Use closure's implementation.
   * @return closure's function name for exporting symbols.
   */
  @Override
  public String getExportSymbolFunction() {
    return "goog.exportSymbol";
  }

  @Override
  public List<String> identifyTypeDeclarationCall(Node n) {
    Node callName = n.getFirstChild();
    if ("goog.addDependency".equals(callName.getQualifiedName()) &&
        n.getChildCount() >= 3) {
      Node typeArray = callName.getNext().getNext();
      if (typeArray.getType() == Token.ARRAYLIT) {
        List<String> typeNames = Lists.newArrayList();
        for (Node name = typeArray.getFirstChild(); name != null;
             name = name.getNext()) {
          if (name.getType() == Token.STRING) {
            typeNames.add(name.getString());
          }
        }
        return typeNames;
      }
    }
    return null;
  }

  @Override
  public String getAbstractMethodName() {
    return "goog.abstractMethod";
  }

  @Override
  public String getSingletonGetterClassName(Node callNode) {
    Node callArg = callNode.getFirstChild();
    String callName = callArg.getQualifiedName();

    // Use both the original name and the post-CollapseProperties name.
    if (!("goog.addSingletonGetter".equals(callName) ||
          "goog$addSingletonGetter".equals(callName)) ||
        callNode.getChildCount() != 2) {
      return null;
    }

    return callArg.getNext().getQualifiedName();
  }

  @Override
  public void applySingletonGetter(FunctionType functionType,
      FunctionType getterType, ObjectType objectType) {
    functionType.defineDeclaredProperty("getInstance", getterType,
        functionType.getSource());
    functionType.defineDeclaredProperty("instance_", objectType,
        functionType.getSource());
  }

  @Override
  public String getGlobalObject() {
    return "goog.global";
  }

  private final Set<String> propertyTestFunctions = ImmutableSet.of(
      "goog.isDef", "goog.isNull", "goog.isDefAndNotNull",
      "goog.isString", "goog.isNumber", "goog.isBoolean",
      "goog.isFunction", "goog.isArray", "goog.isObject");

  @Override
  public boolean isPropertyTestFunction(Node call) {
    Preconditions.checkArgument(call.getType() == Token.CALL);
    return propertyTestFunctions.contains(
        call.getFirstChild().getQualifiedName());
  }

  @Override
  public ObjectLiteralCast getObjectLiteralCast(NodeTraversal t,
      Node callNode) {
    Preconditions.checkArgument(callNode.getType() == Token.CALL);
    Node callName = callNode.getFirstChild();
    if (!"goog.reflect.object".equals(callName.getQualifiedName()) ||
        callNode.getChildCount() != 3) {
      return null;
    }

    Node typeNode = callName.getNext();
    if (!typeNode.isQualifiedName()) {
      return null;
    }

    Node objectNode = typeNode.getNext();
    if (objectNode.getType() != Token.OBJECTLIT) {
      // TODO(johnlenz): The coding convention should not be performing checks.
      t.getCompiler().report(JSError.make(t.getSourceName(), callNode,
                                          OBJECTLIT_EXPECTED));
      return null;
    }

    return new ObjectLiteralCast(typeNode.getQualifiedName(),
                                 typeNode.getNext());
  }

  @Override
  public boolean isOptionalParameter(Node parameter) {
    return false;
  }

  @Override
  public boolean isVarArgsParameter(Node parameter) {
    return false;
  }

  @Override
  public boolean isPrivate(String name) {
    return false;
  }

  @Override
  public Collection<AssertionFunctionSpec> getAssertionFunctions() {
    return ImmutableList.<AssertionFunctionSpec>of(
        new AssertionFunctionSpec("goog.asserts.assert"),
        new AssertionFunctionSpec("goog.asserts.assertNumber",
            JSTypeNative.NUMBER_TYPE),
        new AssertionFunctionSpec("goog.asserts.assertString",
            JSTypeNative.STRING_TYPE),
        new AssertionFunctionSpec("goog.asserts.assertFunction",
            JSTypeNative.FUNCTION_INSTANCE_TYPE),
        new AssertionFunctionSpec("goog.asserts.assertObject",
            JSTypeNative.OBJECT_TYPE),
        new AssertionFunctionSpec("goog.asserts.assertArray",
            JSTypeNative.ARRAY_TYPE),
        // TODO(agrieve): It would be better if this could make the first
        // parameter the type of the second parameter.
        new AssertionFunctionSpec("goog.asserts.assertInstanceof",
            JSTypeNative.OBJECT_TYPE)
    );
  }

  @Override
  public Bind describeFunctionBind(Node n) {
    Bind result = super.describeFunctionBind(n);
    if (result != null) {
      return result;
    }

    // It would be nice to be able to identify a fn.bind call
    // but that requires knowing the type of "fn".

    if (n.getType() != Token.CALL) {
      return null;
    }

    Node callTarget = n.getFirstChild();
    String name = callTarget.getQualifiedName();
    if (name != null) {
      if (name.equals("goog.bind")
          || name.equals("goog$bind")) {
        // goog.bind(fn, self, args...);
        Node fn = callTarget.getNext();
        if (fn == null) {
          return null;
        }
        Node thisValue = safeNext(fn);
        Node parameters = safeNext(thisValue);
        return new Bind(fn, thisValue, parameters);
      }

      if (name.equals("goog.partial") || name.equals("goog$partial")) {
        // goog.partial(fn, args...);
        Node fn = callTarget.getNext();
        if (fn == null) {
          return null;
        }
        Node thisValue = null;
        Node parameters = safeNext(fn);
        return new Bind(fn, thisValue, parameters);
      }
    }

    return null;
  }

  private Node safeNext(Node n) {
    if (n != null) {
      return n.getNext();
    }
    return null;
  }
}
