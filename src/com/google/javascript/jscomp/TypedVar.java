/*
 * Copyright 2015 The Closure Compiler Authors.
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

import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.StaticTypedRef;
import com.google.javascript.rhino.jstype.StaticTypedSlot;

/**
 * {@link AbstractVar} subclass for use with {@link TypedScope}.
 */
public class TypedVar extends AbstractVar<TypedScope, TypedVar>
    implements StaticTypedSlot<JSType>, StaticTypedRef<JSType> {

  private JSType type;
  // The next two fields and the associated methods are only used by
  // TypeInference.java. Maybe there is a way to avoid having them in all typed variable instances.
  private boolean markedEscaped = false;
  private boolean markedAssignedExactlyOnce = false;

  /**
   * Whether the variable's type has been inferred or is declared. An inferred
   * type may change over time (as more code is discovered), whereas a
   * declared type is a static contract that must be matched.
   */
  private final boolean typeInferred;

  TypedVar(boolean inferred, String name, Node nameNode, JSType type,
      TypedScope scope, int index, CompilerInput input) {
    super(name, nameNode, scope, index, input);
    this.type = type;
    this.typeInferred = inferred;
  }

  /**
   * Gets this variable's type. To know whether this type has been inferred,
   * see {@code #isTypeInferred()}.
   */
  @Override
  public JSType getType() {
    return type;
  }

  void setType(JSType type) {
    this.type = type;
  }

  void resolveType(ErrorReporter errorReporter) {
    if (type != null) {
      type = type.resolve(errorReporter, scope);
    }
  }

  /**
   * Returns whether this variable's type is inferred. To get the variable's
   * type, see {@link #getType()}.
   */
  @Override
  public boolean isTypeInferred() {
    return typeInferred;
  }

  public String getInputName() {
    if (input == null) {
      return "<non-file>";
    }
    return input.getName();
  }

  @Override
  public String toString() {
    return "Var " + name + "{" + type + "}";
  }

  void markEscaped() {
    markedEscaped = true;
  }

  boolean isMarkedEscaped() {
    return markedEscaped;
  }

  void markAssignedExactlyOnce() {
    markedAssignedExactlyOnce = true;
  }

  boolean isMarkedAssignedExactlyOnce() {
    return markedAssignedExactlyOnce;
  }

  static TypedVar makeArguments(TypedScope scope) {
    // Look for an extern named "arguments" and use its type if available.
    // TODO(sdh): consider looking for "Arguments" ctor rather than "arguments" var: this could
    // allow deleting the variable, which doesn't really belong in externs in the first place.
    TypedVar globalArgs = scope.getGlobalScope().getVar(Var.ARGUMENTS);
    JSType type = globalArgs != null && globalArgs.isExtern() ? globalArgs.getType() : null;
    return new TypedArguments(type, scope);
  }

  private static final class TypedArguments extends TypedVar {
    TypedArguments(JSType type, TypedScope scope) {
      super(false, Var.ARGUMENTS, null, type, scope, -1, null);
    }

    @Override
    public boolean isArguments() {
      return true;
    }

    @Override
    public StaticSourceFile getSourceFile() {
      return scope.getRootNode().getStaticSourceFile();
    }

    @Override
    public boolean isBleedingFunction() {
      return false;
    }

    @Override
    protected Token declarationType() {
      return null;
    }
  }
}
