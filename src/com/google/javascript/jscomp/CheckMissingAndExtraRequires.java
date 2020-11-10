/*
 * Copyright 2008 The Closure Compiler Authors.
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

/**
 * @deprecated The compiler pass that provides "missing require" checking is {@link
 *     #com.google.javascript.jscomp.CheckMissingRequires}
 */
@Deprecated
public class CheckMissingAndExtraRequires {
  @Deprecated
  public static final DiagnosticType MISSING_REQUIRE_WARNING =
      DiagnosticType.disabled("JSC_MISSING_REQUIRE_WARNING", "missing require: ''{0}''");

  @Deprecated
  static final DiagnosticType MISSING_REQUIRE_FOR_GOOG_SCOPE =
      DiagnosticType.disabled("JSC_MISSING_REQUIRE_FOR_GOOG_SCOPE", "missing require: ''{0}''");

  @Deprecated
  public static final DiagnosticType MISSING_REQUIRE_STRICT_WARNING =
      DiagnosticType.disabled("JSC_MISSING_REQUIRE_STRICT_WARNING", "missing require: ''{0}''");

  private CheckMissingAndExtraRequires() {}
}
