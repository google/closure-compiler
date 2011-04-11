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




import com.google.javascript.rhino.Node;

import java.util.regex.Pattern;

/**
 * This describes the Google-specific JavaScript coding conventions.
 * Within Google, variable names are semantically significant.
 *
 */
public class GoogleCodingConvention extends ClosureCodingConvention {

  private static final long serialVersionUID = 1L;

  private static final String OPTIONAL_ARG_PREFIX = "opt_";

  private static final String VAR_ARGS_NAME = "var_args";

  private static final Pattern ENUM_KEY_PATTERN =
    Pattern.compile("[A-Z0-9][A-Z0-9_]*");

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
    if (pos >= 0) {
      name = name.substring(pos + 1);
      if (name.length() == 0) {
        return false;
      }
    }

    return isConstantKey(name);
  }

  @Override
  public boolean isConstantKey(String name) {
    if (name.isEmpty() || !Character.isUpperCase(name.charAt(0))) {
      return false;
    }

    // hack way of checking that there aren't any lower-case letters
    return name.toUpperCase().equals(name);
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
    return parameter.getString().startsWith(OPTIONAL_ARG_PREFIX);
  }

  @Override
  public boolean isVarArgsParameter(Node parameter) {
    return VAR_ARGS_NAME.equals(parameter.getString());
  }

  /**
   * {@inheritDoc}
   *
   * <p>In Google code, any global name starting with an underscore is
   * considered exported.
   */
  @Override
  public boolean isExported(String name, boolean local) {
    return super.isExported(name, local) ||
        (!local && name.startsWith("_"));
  }

  /**
   * {@inheritDoc}
   *
   * <p>In Google code, private names end with an underscore, and exported
   * names are never considered private (see {@link #isExported}).
   */
  @Override
  public boolean isPrivate(String name) {
    return name.endsWith("_") && !isExported(name);
  }
}
