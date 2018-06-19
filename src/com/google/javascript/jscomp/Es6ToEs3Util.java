/*
 * Copyright 2014 The Closure Compiler Authors.
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
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import java.util.Locale;

/**
 * Util functions for converting Es6 to Es5
 *
 * @author tbreisacher@google.com (Tyler Breisacher)
 */
public final class Es6ToEs3Util {

  static final DiagnosticType CANNOT_CONVERT = DiagnosticType.error(
      "JSC_CANNOT_CONVERT",
      "This code cannot be converted from ES6. {0}");

  // TODO(tbreisacher): Remove this once we have implemented transpilation for all the features
  // we intend to support.
  static final DiagnosticType CANNOT_CONVERT_YET = DiagnosticType.error(
      "JSC_CANNOT_CONVERT_YET",
      "ES6 transpilation of ''{0}'' is not yet implemented.");

  static void cannotConvert(AbstractCompiler compiler, Node n, String message) {
    compiler.report(JSError.make(n, CANNOT_CONVERT, message));
  }

  /**
   * Warns the user that the given ES6 feature cannot be converted to ES3
   * because the transpilation is not yet implemented. A call to this method
   * is essentially a "TODO(tbreisacher): Implement {@code feature}" comment.
   */
  static void cannotConvertYet(AbstractCompiler compiler, Node n, String feature) {
    compiler.report(JSError.make(n, CANNOT_CONVERT_YET, feature));
  }

  /**
   * Returns a call to {@code $jscomp.makeIterator} with {@code iterable} as its argument.
   */
  static Node makeIterator(AbstractCompiler compiler, Node iterable) {
    return callEs6RuntimeFunction(compiler, iterable, "makeIterator");
  }

  /**
   * Returns a call to {@code $jscomp.arrayFromIterator} with {@code iterator} as its argument.
   */
  static Node arrayFromIterator(AbstractCompiler compiler, Node iterator) {
    return callEs6RuntimeFunction(compiler, iterator, "arrayFromIterator");
  }

  /**
   * Returns a call to $jscomp.arrayFromIterable with {@code iterable} as its argument.
   */
  static Node arrayFromIterable(AbstractCompiler compiler, Node iterable) {
    JSTypeRegistry registry = compiler.getTypeRegistry();
    JSType arrayType = registry.getNativeType(JSTypeNative.ARRAY_TYPE);

    Node call =
        callEs6RuntimeFunction(compiler, iterable, "arrayFromIterable").setJSType(arrayType);
    call.getFirstChild().setJSType(registry.createFunctionTypeWithVarArgs(arrayType));
    return call;
  }

  static void preloadEs6RuntimeFunction(AbstractCompiler compiler, String function) {
    compiler.ensureLibraryInjected("es6/util/" + function.toLowerCase(Locale.US), false);
  }

  static Node callEs6RuntimeFunction(
      AbstractCompiler compiler, Node iterable, String function) {
    preloadEs6RuntimeFunction(compiler, function);
    return IR.call(
        NodeUtil.newQName(compiler, "$jscomp." + function),
        iterable);
  }

  /** Adds the type t to Node n, and returns n. Does nothing if t is null. */
  static Node withType(Node n, JSType t) {
    if (t != null) {
      n.setJSType(t);
    }
    return n;
  }

  /**
   * Returns the JSType as specified by the typeName.
   * Returns null if shouldCreate is false.
   */
  static JSType createType(boolean shouldCreate, JSTypeRegistry registry, JSTypeNative typeName) {
    if (!shouldCreate) {
      return null;
    }
    return registry.getNativeType(typeName);
  }

  /**
   * Returns the JSType as specified by the typeName and instantiated by the typeArg.
   * Returns null if shouldCreate is false.
   */
  static JSType createGenericType(
      boolean shouldCreate, JSTypeRegistry registry, JSTypeNative typeName, JSType typeArg) {
    if (!shouldCreate) {
      return null;
    }
    ObjectType genericType = (ObjectType) (registry.getNativeType(typeName));
    ObjectType uninstantiated = genericType.getRawType();
    return registry.instantiateGenericType(uninstantiated, ImmutableList.of(typeArg));
  }
}
