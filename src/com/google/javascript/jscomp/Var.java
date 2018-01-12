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

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticRef;
import com.google.javascript.rhino.StaticSlot;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.Token;

/**
 * Used by {@code Scope} to store information about variables.
 */
public class Var extends AbstractVar<Scope, Var> implements StaticSlot, StaticRef {

  static final String ARGUMENTS = "arguments";

  Var(String name, Node nameNode, Scope scope, int index, CompilerInput input) {
    super(name, nameNode, scope, index, input);
  }

  static Var makeArgumentsVar(Scope scope) {
    return new Arguments(scope);
  }

  @Override
  public String toString() {
    return "Var " + name + " @ " + nameNode;
  }

  /**
   * A special subclass of Var used to distinguish "arguments" in the current
   * scope.
   */
  private static class Arguments extends Var {
    Arguments(Scope scope) {
      super(
          ARGUMENTS, // always arguments
          null,  // no declaration node
          scope,
          -1,    // no variable index
          null   // input
      );
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
