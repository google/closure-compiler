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

/** A warnings guard that suppresses some warnings incompatible with J2CL. */
public class J2clSuppressWarningsGuard extends DiagnosticGroupWarningsGuard {

  // TODO(b/128554878): Cleanup and document all file level suppressions for J2CL generated code.
  private static final DiagnosticGroup DEFAULT_J2CL_SUPRRESIONS =
      new DiagnosticGroup(
          "j2clIncomaptible",
          // Do not warn when static overrides do not match. This is safe, because J2CL generated
          // code directly points to declaration and this is also required with collapse properties
          // pass, which does not support dynamic dispatch for static methods.
          DiagnosticGroups.CHECK_STATIC_OVERRIDES,
          DiagnosticGroups.CHECK_USELESS_CODE,
          DiagnosticGroups.CONST,
          DiagnosticGroups.EXTRA_REQUIRE,
          DiagnosticGroups.LATE_PROVIDE,
          DiagnosticGroups.MISSING_OVERRIDE,
          DiagnosticGroups.MISSING_REQUIRE,
          DiagnosticGroups.STRICT_MODULE_DEP_CHECK,
          DiagnosticGroups.SUSPICIOUS_CODE,
          DiagnosticGroups.UNUSED_LOCAL_VARIABLE,
          DiagnosticGroups.forName("transitionalSuspiciousCodeWarnings"));

  public J2clSuppressWarningsGuard() {
    super(DEFAULT_J2CL_SUPRRESIONS, CheckLevel.OFF);
  }

  @Override
  public boolean disables(DiagnosticGroup type) {
    // Do not suppress all warnings of any type.
    return false;
  }

  @Override
  public CheckLevel level(JSError error) {
    boolean isJ2clSource = error.sourceName != null && error.sourceName.endsWith(".java.js");
    return isJ2clSource ? super.level(error) /* suppress */ : null /* proceed */;
  }

  @Override
  protected int getPriority() {
    return Priority.MAX.getValue();
  }
}
