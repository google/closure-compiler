/*
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Bob Jervis
 *   Google Inc.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */

package com.google.javascript.rhino.jstype;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.javascript.rhino.jstype.FunctionType.Parameter;
import java.util.ArrayList;

/** A builder for the list representing FunctionType Parameters */
public final class FunctionParamBuilder {

  private final JSTypeRegistry registry;
  private final ArrayList<Parameter> parameters;

  public FunctionParamBuilder(JSTypeRegistry registry) {
    this.registry = registry;
    this.parameters = new ArrayList<>();
  }

  public FunctionParamBuilder(JSTypeRegistry registry, int initialParameterCapacity) {
    this.registry = registry;
    this.parameters = new ArrayList<>(initialParameterCapacity);
  }

  /**
   * Add parameters of the given type to the end of the param list.
   * @return False if this is called after optional params are added.
   */
  public boolean addRequiredParams(JSType ...types) {
    if (hasOptionalOrVarArgs()) {
      return false;
    }

    for (JSType type : types) {
      newParameter(type, /* isOptional= */ false, /* isVariadic= */ false);
    }
    return true;
  }

  /**
   * Add optional parameters of the given type to the end of the param list.
   * @param types Types for each optional parameter. The builder will make them
   *     undefine-able.
   * @return False if this is called after var args are added.
   */
  public boolean addOptionalParams(JSType ...types) {
    if (hasVarArgs()) {
      return false;
    }

    for (JSType type : types) {
      newParameter(
          registry.createOptionalType(type), /* isOptional= */ true, /* isVariadic= */ false);
    }
    return true;
  }

  /**
   * Add variable arguments to the end of the parameter list.
   * @return False if this is called after var args are added.
   */
  public boolean addVarArgs(JSType type) {
    if (hasVarArgs()) {
      return false;
    }

    newParameter(type, /* isOptional= */ false, /* isVariadic= */ true);
    return true;
  }

  /** Copies the existing parameter into this builder */
  public void newParameterFrom(Parameter n) {
    parameters.add(n);
  }

  /** Copies the parameter specification from the given parameter, but makes sure it's optional. */
  public void newOptionalParameterFrom(Parameter p) {
    boolean isOptional = p.isOptional() || !p.isVariadic();
    if (isOptional != p.isOptional()) {
      newParameter(p.getJSType(), isOptional, p.isVariadic());
    } else {
      newParameterFrom(p);
    }
  }

  /** Adds a parameter with the given type */
  private void newParameter(JSType type, boolean isOptional, boolean isVariadic) {
    Parameter parameter = Parameter.create(type, isOptional, isVariadic);
    parameters.add(parameter);
  }

  private boolean hasOptionalOrVarArgs() {
    if (parameters.isEmpty()) {
      return false;
    }
    Parameter lastParam = Iterables.getLast(parameters);
    return lastParam.isOptional() || lastParam.isVariadic();
  }

  public boolean hasVarArgs() {
    return !parameters.isEmpty() && Iterables.getLast(parameters).isVariadic();
  }

  public ImmutableList<Parameter> build() {
    return ImmutableList.copyOf(parameters);
  }
}
