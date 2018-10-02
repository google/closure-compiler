/*
 * Copyright 2018 The Closure Compiler Authors.
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

/** Common errors for Closure primitives that are reported in multiple passses. */
final class ClosurePrimitiveErrors {

  private ClosurePrimitiveErrors() {}

  static final DiagnosticType INVALID_DESTRUCTURING_FORWARD_DECLARE =
      DiagnosticType.error(
          "JSC_INVALID_DESTRUCTURING_FORWARD_DECLARE",
          "Cannot destructure a forward-declared type");

  static final DiagnosticType MODULE_USES_GOOG_MODULE_GET =
      DiagnosticType.error(
          "JSC_MODULE_USES_GOOG_MODULE_GET",
          "It's illegal to use a 'goog.module.get' at the module top-level."
              + " Did you mean to use goog.require instead?");

  static final DiagnosticType INVALID_FORWARD_DECLARE_NAMESPACE =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_INVALID_FORWARD_DECLARE_NAMESPACE",
          "goog.forwardDeclare parameter must be a string literal.");

  static final DiagnosticType INVALID_GET_NAMESPACE =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_INVALID_GET_NAMESPACE",
          "goog.module.get parameter must be a string literal.");

  static final DiagnosticType INVALID_REQUIRE_NAMESPACE =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_INVALID_REQUIRE_NAMESPACE",
          "goog.require parameter must be a string literal.");

  static final DiagnosticType INVALID_REQUIRE_TYPE_NAMESPACE =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_INVALID_REQUIRE_TYPE_NAMESPACE",
          "goog.requireType parameter must be a string literal.");

  static final DiagnosticType MISSING_MODULE_OR_PROVIDE =
      DiagnosticType.error(
          "JSC_MISSING_MODULE_OR_PROVIDE", "Required namespace \"{0}\" never defined.");

  static final DiagnosticType INVALID_GET_CALL_SCOPE =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_INVALID_GET_CALL_SCOPE",
          "goog.module.get can not be called in global scope.");

  static final DiagnosticType INVALID_CLOSURE_CALL_ERROR =
      DiagnosticType.error(
          "JSC_INVALID_CLOSURE_CALL_ERROR",
          "Closure dependency methods(goog.provide, goog.require, etc) must be called at file "
              + "scope.");
}
