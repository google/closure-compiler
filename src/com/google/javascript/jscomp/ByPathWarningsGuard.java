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

import com.google.common.base.Preconditions;

import java.util.List;

/**
 * An implementation of a {@link WarningsGuard} that can modify the
 * {@link CheckLevel} based on the file that caused the warning, and whether
 * this file matches a set of paths (specified either as include or exclude
 * of path name parts).
 *
 * <p>For example:
 * <pre>
 * List<String> paths = new ArrayList<String>();
 * paths.add("foo");
 * WarningsGuard guard =
 *     ByPathWarningsGuard.forPath(paths, CheckLevel.ERROR, 1);
 * </pre>
 *
 * This guard will convert any warning that came from a file that contains "foo"
 * in its path to an error.
 *
 */
public class ByPathWarningsGuard extends WarningsGuard {
  private static final long serialVersionUID = 1L;

  private final List<String> paths;
  private final boolean include;
  private final int priority;
  private CheckLevel level;

  /**
   * Constructs a new instance. The priority is determined by the
   * {@link CheckLevel}: ERROR have Priority.STRICT, and OFF have priority
   * FILTER_BY_PATH.
   *
   * Use {@link #forPath} or {@link #exceptPath} to actually create a new
   * instance.
   */
  private ByPathWarningsGuard(
      List<String> paths, boolean include, CheckLevel level) {
    Preconditions.checkArgument(paths != null);
    Preconditions.checkArgument(
        level == CheckLevel.OFF || level == CheckLevel.ERROR);
    this.paths = paths;
    this.include = include;
    this.level = level;
    this.priority = level == CheckLevel.ERROR ?
        WarningsGuard.Priority.STRICT.value :
        WarningsGuard.Priority.FILTER_BY_PATH.value;
  }

  /**
   * @param paths Paths for matching.
   * @param level The {@link CheckLevel} to apply on affected files.
   * @return a new {@link ByPathWarningsGuard} that would affect any file in the
   *     given set of paths.
   */
  public static ByPathWarningsGuard forPath(
      List<String> paths, CheckLevel level) {
    return new ByPathWarningsGuard(paths, true, level);
  }

  /**
   * @param paths Paths for matching.
   * @param level The {@link CheckLevel} to apply on affected files.
   * @return a new {@link ByPathWarningsGuard} that would affect any file not
   *     in the given set of paths.
   */
  public static ByPathWarningsGuard exceptPath(
      List<String> paths, CheckLevel level) {
    return new ByPathWarningsGuard(paths, false, level);
  }

  @Override
  public CheckLevel level(JSError error) {
    final String errorPath = error.sourceName;
    CheckLevel defaultLevel = error.getDefaultLevel();
    if (defaultLevel != CheckLevel.ERROR && errorPath != null) {
      boolean inPath = false;
      for (String path : paths) {
        inPath |= errorPath.contains(path);
      }
      if (inPath == include) {
        return level;
      }
    }
    return null;
  }

  @Override
  protected int getPriority() {
    return priority;
  }
}
