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

/**
 * Basic information on an entry point module.
 * While closure entry points are namespaces,
 * ES6 and CommonJS entry points are file paths
 * which are normalized to a closure namespace.
 *
 * This class allows error messages to the user to
 * be based on the input name rather than the
 * normalized version.
 */
public class ModuleIdentifier {
  private final String name;
  private final String closureNamespace;
  private final String moduleName;

  /**
   * @param name as provided by the user
   * @param closureNamespace entry point normalized to a closure namespace
   * @param moduleName For closure namespaces, the module name may be different than
   *     the namespace
   */
  ModuleIdentifier(String name, String closureNamespace, String moduleName) {
    this.name = name;
    this.closureNamespace = closureNamespace;
    this.moduleName = moduleName;
  }

  public String getName() {
    return name;
  }

  public String getClosureNamespace() {
    return closureNamespace;
  }

  public String getModuleName() {
    return moduleName;
  }

  @Override
  public String toString() {
    if (closureNamespace.equals(moduleName)) {
      return closureNamespace;
    }
    return moduleName + ":" + closureNamespace;
  }

  /**
   * @param name Closure namespace used as an entry point. May start
   *     "goog:" when provided as a flag from the command line.
   *
   * Closure entry points may also be formatted as:
   *     'goog:moduleName:name.space'
   * which specifies that the module name and provided namespace
   * are different
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

    return new ModuleIdentifier(normalizedName, namespace, moduleName);
  }

  /**
   * @param filepath ES6 or CommonJS module used as an entry point.
   */
  public static ModuleIdentifier forFile(String filepath) {
    String normalizedName = ES6ModuleLoader.toModuleName(ES6ModuleLoader.createUri(filepath));
    return new ModuleIdentifier(filepath, normalizedName, normalizedName);
  }
}
