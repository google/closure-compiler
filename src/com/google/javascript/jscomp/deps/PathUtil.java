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

package com.google.javascript.jscomp.deps;

import static com.google.common.base.StandardSystemProperty.USER_DIR;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.List;

/**
 * Utility methods for manipulation of UNIX-like paths.
 * NOTE: According to kevinb, equivalent methods will be in the standard library once
 * jsr203 is ready.
 *
 */
public final class PathUtil {

  private static final CharMatcher SLASH_MATCHER = CharMatcher.is('/');
  private static final CharMatcher NON_SLASH_MATCHER = CharMatcher.isNot('/');

  private PathUtil() {
  }

  /**
   * Removes all ../ and ./ entries from within the given path. If there are extra ..s that move
   * beyond the first directory given, they are removed.
   *
   * Examples:
   *   "a/b/../c" results in "a/c"
   *   "./foo/./../bar" results in "bar"
   *   "a/.." results in ""
   *   "a/../../foo" results in "foo"
   *
   * @param path The path to remove dots from.
   * @return The path with all dots collapsed.
   */
  public static String collapseDots(String path) {
    path = removeExtraneousSlashes(path);
    // Optimization: Most paths don't contain dots.
    if (!path.contains(".")) {
      return path;
    }

    List<String> dstFragments = Lists.newArrayList();
    for (String fragment : Splitter.on('/').split(path)) {
      if (fragment.equals("..")) {
        if (!dstFragments.isEmpty()) {
          dstFragments.remove(dstFragments.size() - 1);
        }
      } else if (!fragment.equals(".")) {
        dstFragments.add(fragment);
      }
    }

    // Special case for Join.join([""]); -> "/"
    if (dstFragments.size() == 1 && dstFragments.get(0).isEmpty()) {
      return "/";
    }
    return Joiner.on("/").join(dstFragments);
  }

  /**
   * Determines if a path is absolute or not by testing for the presence of "/"
   * at the front of the string.
   *
   * @param path The path to test
   * @return true if the path starts with DELIMITER, false otherwise.
   */
  static boolean isAbsolute(String path) {
    return path.startsWith("/");
  }

  /**
   * Removes extra slashes from a path.  Leading slash is preserved, trailing
   * slash is stripped, and any runs of more than one slash in the middle is
   * replaced by a single slash.
   */
  static String removeExtraneousSlashes(String s) {
    int lastNonSlash = NON_SLASH_MATCHER.lastIndexIn(s);
    if (lastNonSlash != -1) {
      s = s.substring(0, lastNonSlash + 1);
    }

    return SLASH_MATCHER.collapseFrom(s, '/');
  }


  /**
   * Converts the given path into an absolute one. This prepends the current
   * working directory and removes all .'s from the path. If an absolute path
   * is given, it will not be prefixed.
   *
   * <p>Unlike File.getAbsolutePath(), this function does remove .'s from the
   * path and unlike File.getCanonicalPath(), this function does not resolve
   * symlinks and does not use filesystem calls.</p>
   *
   * @param path The path to make absolute.
   * @return The path made absolute.
   */
  public static String makeAbsolute(String path) {
    return makeAbsolute(path, USER_DIR.value());
  }

  /**
   * Converts the given path into an absolute one. This prepends the given
   * rootPath and removes all .'s from the path. If an absolute path is given,
   * it will not be prefixed.
   *
   * <p>Unlike File.getAbsolutePath(), this function does remove .'s from the
   * path and unlike File.getCanonicalPath(), this function does not resolve
   * symlinks and does not use filesystem calls.</p>
   *
   * @param rootPath The path to prefix to path if path is not already absolute.
   * @param path The path to make absolute.
   * @return The path made absolute.
   */
  public static String makeAbsolute(String path, String rootPath) {
    if (!isAbsolute(path)) {
      path = rootPath + "/" + path;
    }
    return collapseDots(path);
  }

  /**
   * Returns targetPath relative to basePath.
   *
   * <p>basePath and targetPath must either both be relative, or both be
   * absolute paths.</p>
   *
   * <p>This function is different from makeRelative
   * in that it is able to add in ../ components and collapse existing ones as well.</p>
   *
   * Examples:
   *   base="some/relative/path" target="some/relative/path/foo" return="foo"
   *   base="some/relative/path" target="some/relative" return=".."
   *   base="some/relative/path" target="foo/bar" return="../../../foo/bar"
   *   base="/some/abs/path" target="/foo/bar" return="../../../foo/bar"
   *
   * @param basePath The path to make targetPath relative to.
   * @param targetPath The path to make relative.
   * @return A path relative to targetPath. The returned value will never start
   *     with a slash.
   */
  public static String makeRelative(String basePath, String targetPath) {
    // Ensure the paths are both absolute or both relative.
    if (isAbsolute(basePath) !=
        isAbsolute(targetPath)) {
      throw new IllegalArgumentException(
          "Paths must both be relative or both absolute.\n" +
          "  basePath: " + basePath + "\n" +
          "  targetPath: " + targetPath);
    }

    basePath = collapseDots(basePath);
    targetPath = collapseDots(targetPath);
    String[] baseFragments = basePath.split("/");
    String[] targetFragments = targetPath.split("/");

    int i = -1;
    do {
      i += 1;
      if (i == baseFragments.length && i == targetFragments.length) {
        // Eg) base: /java/com/google
        //   target: /java/com/google
        //   result: .  <-- . is better than "" since "" + "/path" = "/path"
        return ".";
      } else if (i == baseFragments.length) {
        // Eg) base: /java/com/google
        //   target: /java/com/google/c/ui
        //   result: c/ui
        return Joiner.on("/").join(
            Lists.newArrayList(
                Arrays.asList(targetFragments).listIterator(i)));
      } else if (i == targetFragments.length) {
        // Eg) base: /java/com/google/c/ui
        //   target: /java/com/google
        //   result: ../..
        return Strings.repeat("../", baseFragments.length - i - 1) + "..";
      }

    } while (baseFragments[i].equals(targetFragments[i]));

    // Eg) base: /java/com/google/c
    //   target: /java/com/google/common/base
    //   result: ../common/base
    return Strings.repeat("../", baseFragments.length - i) +
        Joiner.on("/").join(
            Lists.newArrayList(Arrays.asList(targetFragments).listIterator(i)));
  }
}
