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
package com.google.javascript.jscomp.modules;

import com.google.auto.value.AutoValue;

/**
 * Simple class to keep track of which modules and exports have been visited when resolving exports.
 * It is invalid to visit the same (module, name) pair more than once when resolving an export
 * (invalid cycle).
 *
 * <p>This is an AutoValue used for its hashCode / equals implementation and used in a Set for
 * equality checks. So fields may appear to be "unused".
 */
@AutoValue
abstract class ExportTrace {
  static ExportTrace create(UnresolvedModule module, String exportName) {
    return new AutoValue_ExportTrace(module, exportName);
  }

  abstract UnresolvedModule module();

  abstract String exportName();
}
