/*
 * Copyright 2014 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.newtypes;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Preconditions;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import java.io.Serializable;

/**
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public final class Typedef implements Serializable {

  private enum State {
    NOT_RESOLVED,
    DURING_RESOLUTION,
    RESOLVED
  }

  private final Node defSite;
  private State state;
  private JSTypeExpression typeExpr;
  private JSType type;

  private Typedef(Node defSite, JSTypeExpression typeExpr) {
    checkState(defSite.isQualifiedName(), defSite);
    this.defSite = defSite;
    this.state = State.NOT_RESOLVED;
    // Non-null iff the typedef is resolved
    this.type = null;
    // Non-null iff the typedef is not resolved
    this.typeExpr = typeExpr;
  }

  public static Typedef make(Node defSite, JSTypeExpression typeExpr) {
    return new Typedef(defSite, typeExpr);
  }

  public boolean isResolved() {
    return state == State.RESOLVED;
  }

  public JSType getType() {
    checkState(state == State.RESOLVED);
    return type;
  }

  // Returns null iff there is a typedef cycle
  public JSTypeExpression getTypeExpr() {
    checkState(state != State.RESOLVED);
    if (state == State.DURING_RESOLUTION) {
      return null;
    }
    state = State.DURING_RESOLUTION;
    return typeExpr;
  }

  public JSTypeExpression getTypeExprForErrorReporting() {
    checkState(state == State.DURING_RESOLUTION);
    return typeExpr;
  }

  void resolveTypedef(JSType t) {
    checkNotNull(t);
    if (state == State.RESOLVED) {
      return;
    }
    Preconditions.checkState(state == State.DURING_RESOLUTION,
        "Expected state DURING_RESOLUTION but found %s", state.toString());
    state = State.RESOLVED;
    typeExpr = null;
    type = t;
  }

  @Override
  public String toString() {
    return this.defSite.getQualifiedName();
  }
}
