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

package com.google.javascript.jscomp.modules;

import com.google.auto.value.AutoValue;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.rhino.Node;

/**
 * An <code>import</code>ed name in a module.
 *
 * <p>See <a href="https://www.ecma-international.org/ecma-262/9.0/index.html#table-39">the
 * ImportEntry in the ECMAScript spec.</a>
 */
@AutoValue
public abstract class Import {
  // Prevent unwanted subclasses.
  Import() {}

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder moduleRequest(String value);

    abstract Builder importName(String value);

    abstract Builder localName(String value);

    abstract Builder modulePath(ModuleLoader.ModulePath value);

    abstract Builder importNode(Node value);

    abstract Builder nameNode(Node value);

    abstract Import build();
  }

  static Builder builder() {
    return new AutoValue_Import.Builder();
  }

  /** Returns the module identifier of this import. */
  public abstract String moduleRequest();

  /**
   * Returns the name that was imported from the requested module.
   *
   * <p>For {@code import *} this will return "*".
   */
  public abstract String importName();

  /** Returns the local name the imported value is bound to. */
  public abstract String localName();

  /** Returns the path of the containing module. */
  public abstract ModuleLoader.ModulePath modulePath();

  /** Returns the import node for source information. */
  public abstract Node importNode();

  /** Returns the name node for source information. */
  public abstract Node nameNode();
}
