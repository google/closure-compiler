/*
 * Copyright 2016 The Closure Compiler Authors.
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

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.Immutable;
import com.google.javascript.jscomp.deps.ModuleNames;
import java.io.Serializable;

/**
 * Basic information on an entry point module.
 *
 * <p>Closure entry points are namespace names, while ES and CommonJS entry points are file paths
 * which are normalized to a namespace name.
 *
 * <p>This class allows error messages to be based on the user-provided name rather than the
 * normalized name.
 */
@AutoValue
@Immutable
public abstract class ModuleIdentifier implements Serializable {
  /** Returns the user-provided name. */
  public abstract String getName();

  /** Returns the Closure namespace name. */
  public abstract String getClosureNamespace();

  /** Returns the module name. */
  public abstract String getModuleName();

  @Override
  public final String toString() {
    if (getClosureNamespace().equals(getModuleName())) {
      return getClosureNamespace();
    }
    return getModuleName() + ":" + getClosureNamespace();
  }

  /**
   * Returns an identifier for a Closure namespace.
   *
   * @param name The Closure namespace. It may be in one of the formats `name.space`,
   *     `goog:name.space` or `goog:moduleName:name.space`, where the latter specifies that the
   *     module and namespace names are different.
   */
  public static ModuleIdentifier forClosure(String name) {
    String normalizedName = name;
    if (normalizedName.startsWith("goog:")) {
      normalizedName = normalizedName.substring("goog:".length());
    }

    String namespace = normalizedName;
    String moduleName = normalizedName;
    int splitPoint = normalizedName.indexOf(':');
    if (splitPoint != -1) {
      moduleName = normalizedName.substring(0, splitPoint);
      namespace = normalizedName.substring(Math.min(splitPoint + 1, normalizedName.length() - 1));
    }

    return new AutoValue_ModuleIdentifier(normalizedName, namespace, moduleName);
  }

  /**
   * Returns an identifier for an ES or CommonJS module.
   *
   * @param filepath Path to the ES or CommonJS module.
   */
  public static ModuleIdentifier forFile(String filepath) {
    String normalizedName = ModuleNames.fileToModuleName(filepath);
    return new AutoValue_ModuleIdentifier(filepath, normalizedName, normalizedName);
  }

  /**
   * Returns an identifier for an --entry_point flag value.
   *
   * @param flagValue The flag value. If it is in one of the formats `goog:name.space` or
   *     `goog:moduleName:name.space`, it is interpreted as a Closure namespace. Otherwise, it is
   *     interpreted as the path to an ES or CommonJS module.
   */
  public static ModuleIdentifier forFlagValue(String flagValue) {
    if (flagValue.startsWith("goog:")) {
      return ModuleIdentifier.forClosure(flagValue);
    } else {
      return ModuleIdentifier.forFile(flagValue);
    }
  }
}
