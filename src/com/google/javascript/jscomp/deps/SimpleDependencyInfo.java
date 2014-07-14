/*
 * Copyright 2009 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.deps;


import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A class to hold JS dependency information for a single .js file.
 *
 * @author agrieve@google.com (Andrew Grieve)
 */
public class SimpleDependencyInfo implements DependencyInfo {

  /** A list of provided symbols. */
  private final List<String> provides;

  /** A list of required symbols. */
  private final List<String> requires;

  private final boolean isModule;

  /** The path of the file relative to closure. */
  private final String srcPathRelativeToClosure;

  /** The path to the file from which we extracted the dependency information.*/
  private final String pathOfDefiningFile;

  /**
   * Constructs a DependencyInfo object with the given list of provides &
   * requires. This does *not* copy the given lists, but uses them directly.
   *
   * @param srcPathRelativeToClosure The closure-relative path of the file
   *     associated with this DependencyInfo.
   * @param pathOfDefiningFile The path to the file from which this dependency
   *     information was extracted.
   * @param provides List of provided symbols.
   * @param requires List of required symbols.
   */
  public SimpleDependencyInfo(
      String srcPathRelativeToClosure, String pathOfDefiningFile,
      List<String> provides, List<String> requires, boolean isModule) {
    this.srcPathRelativeToClosure = srcPathRelativeToClosure;
    this.pathOfDefiningFile = pathOfDefiningFile;
    this.provides = provides;
    this.requires = requires;
    this.isModule = isModule;
  }

  @Override
  public String getName() {
    return pathOfDefiningFile;
  }

  @Override
  public String getPathRelativeToClosureBase() {
    return srcPathRelativeToClosure;
  }

  @Override
  public boolean isModule() {
    return this.isModule;
  }

  @Override
  public Collection<String> getProvides() {
    return Collections.unmodifiableList(provides);
  }

  @Override
  public Collection<String> getRequires() {
    return Collections.unmodifiableList(requires);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof SimpleDependencyInfo)) {
      return false;
    }
    SimpleDependencyInfo other = (SimpleDependencyInfo) obj;
    return Objects.equals(other.srcPathRelativeToClosure,
            srcPathRelativeToClosure) &&
        Objects.equals(other.pathOfDefiningFile, pathOfDefiningFile) &&
        Objects.equals(other.requires, this.requires) &&
        Objects.equals(other.provides, this.provides) &&
        other.isModule == this.isModule;
  }

  @Override
  public String toString() {
    return String.format("DependencyInfo(relativePath='%1$s', path='%2$s', "
        + "provides=%3$s, requires=%4$s, module=%5$b)", srcPathRelativeToClosure,
        pathOfDefiningFile, provides, requires, isModule);
  }

  @Override
  public int hashCode() {
    return Objects.hash(provides, requires,
        srcPathRelativeToClosure, pathOfDefiningFile, isModule);
  }
}
