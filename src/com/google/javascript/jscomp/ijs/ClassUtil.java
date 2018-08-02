/*
 * Copyright 2017 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.ijs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import javax.annotation.Nullable;

/**
 * Static utility methods for dealing with classes.  The primary benefit is for papering over
 * the differences between ES6 class and goog.defineClass syntax.
 */
final class ClassUtil {
  private ClassUtil() {}

  static boolean isThisProp(Node getprop) {
    return getClassNameOfThisProp(getprop) != null;
  }

  static String getPrototypeNameOfThisProp(Node getprop) {
    String className = checkNotNull(getClassNameOfThisProp(getprop));
    return className + ".prototype." + getprop.getLastChild().getString();
  }

  @Nullable
  private static String getClassNameOfThisProp(Node getprop) {
    if (!getprop.isGetProp() || !getprop.getFirstChild().isThis()) {
      return null;
    }
    Node function = NodeUtil.getEnclosingFunction(getprop);
    if (function == null) {
      return null;
    }
    String className = getClassName(function);
    if (isNullOrEmpty(className)) {
      return null;
    }
    return className;
  }

  static String getFullyQualifiedNameOfMethod(Node function) {
    checkArgument(isClassMethod(function));
    String className = getClassName(function);
    checkState(className != null && !className.isEmpty());
    Node memberFunctionDef = function.getParent();
    String methodName = memberFunctionDef.getString();
    return memberFunctionDef.isStaticMember()
        ? className + "." + methodName
        : className + ".prototype." + methodName;
  }

  static boolean isClassMethod(Node functionNode) {
    checkArgument(functionNode.isFunction());
    Node parent = functionNode.getParent();
    if (parent.isMemberFunctionDef()
        && parent.getParent().isClassMembers()) {
      // ES6 class
      return true;
    }
    // goog.defineClass
    return parent.isStringKey()
        && parent.getParent().isObjectLit()
        && parent.getGrandparent().isCall()
        && parent.getGrandparent().getFirstChild().matchesQualifiedName("goog.defineClass");
  }

  /**
   * Checks whether the given constructor/member function belongs to a named class, as
   * opposed to an anonymous class.
   */
  static boolean hasNamedClass(Node functionNode) {
    checkArgument(functionNode.isFunction());
    return getClassName(functionNode) != null;
  }

  private static String getClassName(Node functionNode) {
    checkArgument(functionNode.isFunction());
    if (isClassMethod(functionNode)) {
      Node parent = functionNode.getParent();
      if (parent.isMemberFunctionDef()) {
        // ES6 class
        Node classNode = functionNode.getGrandparent().getParent();
        checkState(classNode.isClass());
        return NodeUtil.getName(classNode);
      }
      // goog.defineClass
      checkState(parent.isStringKey());
      Node defineClassCall = parent.getGrandparent();
      checkState(defineClassCall.isCall());
      return NodeUtil.getBestLValue(defineClassCall).getQualifiedName();
    }
    return NodeUtil.getName(functionNode);
  }

  static boolean isConstructor(Node functionNode) {
    if (isClassMethod(functionNode)) {
      return "constructor".equals(functionNode.getParent().getString());
    }
    JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(functionNode);
    return jsdoc != null && jsdoc.isConstructor();
  }
}
