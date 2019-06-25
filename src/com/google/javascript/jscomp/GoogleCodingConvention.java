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
 *
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

  /**
   * {@inheritDoc}
   *
   * <p>In Google code, private names end with an underscore, and exported
   * names are never considered private (see {@link #isExported}).
   */
  @Override
  public boolean isPrivate(String name) {
    return name.endsWith("_") && !name.endsWith("__") && !isExported(name);
  }

  @Override
  public boolean hasPrivacyConvention() {
    return true;
  }
}
