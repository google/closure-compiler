/*
 * Copyright 2019 The Closure Compiler Authors.
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

import org.jspecify.nullness.Nullable;

/** A warnings guard that suppresses some warnings incompatible with J2CL. */
public final class J2clSuppressWarningsGuard extends WarningsGuard {

  // TODO(b/128554878): Cleanup and document all file level suppressions for J2CL generated code.
  private static final DiagnosticGroup DEFAULT_J2CL_SUPRRESIONS =
      new DiagnosticGroup(
          "j2clIncomaptible",
          // Do not warn when static overrides do not match. This is safe, because J2CL generated
          // code directly points to declaration and this is also required with collapse properties
          // pass, which does not support dynamic dispatch for static methods.
          DiagnosticGroups.CHECK_STATIC_OVERRIDES,
          // Do not warn on valid Java constructs like "if(false) {...}" and other situations that
          // may arise from transormation of complex Kotlin constructs.
          DiagnosticGroups.CHECK_USELESS_CODE,
          DiagnosticGroups.CONST,
          DiagnosticGroups.EXTRA_REQUIRE,
          // Kotlin allows provably invalid casts so long as it's via a safe cast. This causes J2CL
          // to generate code of the form:
          //   Foo.$isInstance(value) ? /**@type {!Foo}*/ (value) : null
          // However, if value is obviously never a instance of Foo then this will cause invalid
          // cast error. At runtime it's guarded so it would be safe regardless.
          // This also suppresses casts from non-lambda JsFunction implementations to functions.
          // This particular feature is deprecated and will be removed (b/159954752).
          DiagnosticGroups.INVALID_CASTS,
          DiagnosticGroups.LATE_PROVIDE,
          DiagnosticGroups.MISSING_OVERRIDE,
          DiagnosticGroups.MISSING_REQUIRE,
          DiagnosticGroups.STRICT_MODULE_DEP_CHECK,
          DiagnosticGroups.SUSPICIOUS_CODE,
          DiagnosticGroups.UNUSED_LOCAL_VARIABLE,
          // TODO(b/78521031): J2CL targets are not strict missing property compatible.
          DiagnosticGroups.STRICT_MISSING_PROPERTIES,
          DiagnosticGroups.forName("transitionalSuspiciousCodeWarnings"));

  @Override
  public @Nullable CheckLevel level(JSError error) {
    if (error.getSourceName() == null || !error.getSourceName().endsWith(".java.js")) {
      return null;
    }

    return DEFAULT_J2CL_SUPRRESIONS.matches(error) ? CheckLevel.OFF : null;
  }

  @Override
  protected int getPriority() {
    return Priority.MAX.getValue();
  }
}
