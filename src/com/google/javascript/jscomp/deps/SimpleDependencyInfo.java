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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A class to hold JS dependency information for a single .js file.
 *
 * @author agrieve@google.com (Andrew Grieve)
 */
public final class SimpleDependencyInfo implements DependencyInfo {

  /** A list of provided symbols. */
  private final ImmutableList<String> provides;

  /** A list of required symbols. */
  private final ImmutableList<String> requires;

  /** A map of flags required to load this file. */
  private final ImmutableMap<String, String> loadFlags;

  /** The path of the file relative to closure. */
  private final String srcPathRelativeToClosure;

  /** The path to the file from which we extracted the dependency information.*/
  private final String pathOfDefiningFile;

  // TODO(sdh): migrate callers away and deprecate this constructor
  public SimpleDependencyInfo(
      String srcPathRelativeToClosure, String pathOfDefiningFile,
      List<String> provides, List<String> requires, boolean isModule) {
    this(srcPathRelativeToClosure, pathOfDefiningFile, provides, requires, loadFlags(isModule));
  }

  /**
   * Constructs a DependencyInfo object with the given list of provides and
   * requires. This does *not* copy the given collections, but uses them directly.
   *
   * @param srcPathRelativeToClosure The closure-relative path of the file
   *     associated with this DependencyInfo.
   * @param pathOfDefiningFile The path to the file from which this dependency
   *     information was extracted.
   * @param provides List of provided symbols.
   * @param requires List of required symbols.
   * @param loadFlags Map of file-loading information.
   */
  public SimpleDependencyInfo(
      String srcPathRelativeToClosure, String pathOfDefiningFile,
      Collection<String> provides, Collection<String> requires, Map<String, String> loadFlags) {
    this.srcPathRelativeToClosure = srcPathRelativeToClosure;
    this.pathOfDefiningFile = pathOfDefiningFile;
    this.provides = provides != null ? ImmutableList.copyOf(provides) : ImmutableList.<String>of();
    this.requires = requires != null ? ImmutableList.copyOf(requires) : ImmutableList.<String>of();
    this.loadFlags = ImmutableMap.copyOf(loadFlags);
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
  public ImmutableMap<String, String> getLoadFlags() {
    return loadFlags;
  }

  private static Map<String, String> loadFlags(boolean isModule) {
    return isModule ? ImmutableMap.of("module", "goog") : ImmutableMap.<String, String>of();
  }

  @Override
  public boolean isModule() {
    return "goog".equals(getLoadFlags().get("module"));
  }

  @Override
  public ImmutableList<String> getProvides() {
    return provides;
  }

  @Override
  public ImmutableList<String> getRequires() {
    return requires;
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
        Objects.equals(other.loadFlags, this.loadFlags);
  }

  @Override
  public String toString() {
    return SimpleFormat.format("DependencyInfo(relativePath='%1$s', path='%2$s', "
        + "provides=%3$s, requires=%4$s, loadFlags=%5$s)", srcPathRelativeToClosure,
        pathOfDefiningFile, provides, requires, loadFlags);
  }

  @Override
  public int hashCode() {
    return Objects.hash(provides, requires,
        srcPathRelativeToClosure, pathOfDefiningFile, loadFlags);
  }

  public static final SimpleDependencyInfo EMPTY = new SimpleDependencyInfo(
      "",
      "",
      ImmutableList.<String>of(),
      ImmutableList.<String>of(),
      ImmutableMap.<String, String>of());
}
