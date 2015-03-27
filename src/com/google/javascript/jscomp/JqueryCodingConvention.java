/*
 * Copyright 2011 The Closure Compiler Authors.
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
import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.Node;

import java.util.Set;

/**
 * This describes the jQuery specific JavaScript coding conventions.
 */
public final class JqueryCodingConvention extends CodingConventions.Proxy {
  private static final long serialVersionUID = 1L;

  public JqueryCodingConvention() {
    this(CodingConventions.getDefault());
  }

  public JqueryCodingConvention(CodingConvention wrapped) {
    super(wrapped);
  }

  @Override
  public String getGlobalObject() {
    return "window";
  }

  private static final Set<String> propertyTestFunctions = ImmutableSet.of(
      "jQuery.isPlainObject", "jQuery.isFunction", "jQuery.isNumeric",
      "jQuery.isEmptyObject");

  @Override
  public boolean isPropertyTestFunction(Node call) {
    Preconditions.checkArgument(call.isCall());
    return propertyTestFunctions.contains(
        call.getFirstChild().getQualifiedName());
  }

  private static final Set<String> prototypeAliases = ImmutableSet.of(
      "jQuery.fn", "jQuerySub.fn");

  @Override
  public boolean isPrototypeAlias(Node getProp) {
    Preconditions.checkArgument(getProp.isGetProp());
    return prototypeAliases.contains(getProp.getQualifiedName());
  }
}
