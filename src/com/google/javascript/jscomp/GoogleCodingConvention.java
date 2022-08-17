/*
 * Copyright 2007 The Closure Compiler Authors.
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

import com.google.common.annotations.GwtIncompatible;
import com.google.errorprone.annotations.Immutable;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This describes the Google-specific JavaScript coding conventions. Within Google, variable names
 * are semantically significant.
 */
@Immutable
public class GoogleCodingConvention extends CodingConventions.Proxy {

  private static final long serialVersionUID = 1L;

  private static final String OPTIONAL_ARG_PREFIX = "opt_";

  private static final String VAR_ARGS_NAME = "var_args";

  private static final Pattern ENUM_KEY_PATTERN =
    Pattern.compile("[A-Z0-9][A-Z0-9_]*");

  private static final Pattern PACKAGE_WITH_TEST_DIR =
    Pattern.compile("^(.*)/(?:test|tests|testing)/(?:[^/]+)$");

  private static final Pattern GENFILES_DIR = Pattern.compile("-out/.*/(bin|genfiles)/(.*)$");

  /** By default, decorate the ClosureCodingConvention. */
  public GoogleCodingConvention() {
    this(new ClosureCodingConvention());
  }

  /** Decorates a wrapped CodingConvention. */
  public GoogleCodingConvention(CodingConvention convention) {
    super(convention);
  }

  /**
   * {@inheritDoc}
   *
   * <p>This enforces the Google const name convention, that the first character
   * after the last $ must be an upper-case letter and all subsequent letters
   * must be upper case. The name must be at least 2 characters long.
   *
   * <p>Examples:
   * <pre>
   *      aaa          Not constant - lower-case letters in the name
   *      A            Not constant - too short
   *      goog$A       Constant - letters after the $ are upper-case.
   *      AA17         Constant - digits can appear after the first letter
   *      goog$7A      Not constant - first character after the $ must be
   *                   upper case.
   *      $A           Constant - doesn't have to be anything in front of the $
   * </pre>
   */
  @Override
  public boolean isConstant(String name) {
    if (name.length() <= 1) {
      return false;
    }

    // In compiled code, '$' is often a namespace delimiter. To allow inlining
    // of namespaced constants, we strip off any namespaces here.
    int pos = name.lastIndexOf('$');
    int start = pos >= 0 ? pos + 1 : 0;

    return isConstantKey(name, start, name.length());
  }

  @Override
  public boolean isConstantKey(String name) {
    return isConstantKey(name, 0, name.length());
  }

  /**
   * Returns true iff the substring of name from [start, end) is a constant key.
   *
   * <p>This method takes start and end indices, rather than checking an entire string, to avoid the
   * need for unnecessary .substring(start, end) calls
   */
  private static final boolean isConstantKey(String name, int start, int end) {
    if (start >= end || !Character.isUpperCase(name.charAt(start))) {
      return false;
    }
    for (int i = start + 1; i < end; i++) {
      // check this instead of Character.isUpperCase - they have different results for some
      // characters like symbols. symbols are allowed unless at the start index.
      if (Character.toUpperCase(name.charAt(i)) != name.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  /**
   * {@inheritDoc}
   *
   * <p>This enforces Google's convention about enum key names. They must match
   * the regular expression {@code [A-Z0-9][A-Z0-9_]*}.
   *
   * <p>Examples:
   * <ul>
   * <li>A</li>
   * <li>213</li>
   * <li>FOO_BAR</li>
   * </ul>
   */
  @Override
  public boolean isValidEnumKey(String key) {
    return ENUM_KEY_PATTERN.matcher(key).matches();
  }

  /**
   * {@inheritDoc}
   *
   * <p>In Google code, parameter names beginning with {@code opt_} are
   * treated as optional arguments.
   */
  @Override
  public boolean isOptionalParameter(Node parameter) {
    return super.isOptionalParameter(parameter)
        || (parameter.isName() && parameter.getString().startsWith(OPTIONAL_ARG_PREFIX));
  }

  @Override
  public boolean isVarArgsParameter(Node parameter) {
    return super.isVarArgsParameter(parameter)
        || (parameter.isName() && VAR_ARGS_NAME.equals(parameter.getString()));
  }

  /**
   * {@inheritDoc}
   *
   * <p>In Google code, any global name starting with an underscore is
   * considered exported.
   */
  @Override
  public boolean isExported(String name, boolean local) {
    return super.isExported(name, local) || (!local && name.startsWith("_"));
  }

  @Override
  public boolean isClassFactoryCall(Node callNode) {
    Node callTarget = callNode.getFirstChild();
    return super.isClassFactoryCall(callNode)
        || (callTarget.isName() && callTarget.toString().equals("Polymer"));
  }

  /**
   * {@inheritDoc}
   *
   * <p>In Google code, the package name of a source file is its file path.
   * Exceptions: if a source file's parent directory is "test", "tests", or
   * "testing", that directory is stripped from the package name.
   * If a file is generated, strip the "genfiles" prefix to try
   * to match the package of the generating file.
   */
  @Override
  @GwtIncompatible // TODO(tdeegan): Remove use of Matcher#group to make this fully GWT compatible.
  public String getPackageName(StaticSourceFile source) {
    String name = source.getName();
    Matcher genfilesMatcher = GENFILES_DIR.matcher(name);
    if (genfilesMatcher.find()) {
      name = genfilesMatcher.group(2);
    }

    Matcher m = PACKAGE_WITH_TEST_DIR.matcher(name);
    if (m.find()) {
      return m.group(1);
    } else {
      int lastSlash = name.lastIndexOf('/');
      return lastSlash == -1 ? "" : name.substring(0, lastSlash);
    }
  }
}
