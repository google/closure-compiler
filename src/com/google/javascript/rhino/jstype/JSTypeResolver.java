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

import static com.google.common.base.Preconditions.checkState;

import com.google.errorprone.annotations.MustBeClosed;
import java.io.Serializable;
import java.util.ArrayDeque;

/**
 * A state machine for resolving all {@link JSType} instances.
 *
 * <p>This object shares the lifecycle of a registry. Consider it as a narrow interface into the
 * registry for the purpose of resolving types.
 *
 * <p>Every time a new type is constructed it is (by cooperation of the {@link JSType} subclasses}
 * added to this resolver. Depending on the state of the resolver at the time of addition, the type
 * will either be eagerly resolved, or stored for later resolution.
 */
public final class JSTypeResolver implements Serializable {

  private enum State {
    CLOSED,
    OPEN,
    CLOSING;
  }

  static JSTypeResolver create(JSTypeRegistry registry) {
    if (registry.getResolver() != null) {
      return registry.getResolver();
    }

    return new JSTypeResolver(registry);
  }

  private final JSTypeRegistry registry;
  private final ArrayDeque<JSType> unresolvedTypes = new ArrayDeque<>();

  private State state = State.CLOSED;

  private JSTypeResolver(JSTypeRegistry registry) {
    this.registry = registry;
  }

  /**
   * Record that a new type has been instantiated.
   *
   * <p>This should only be called in the {@code JSType} constructor, with the intention of
   * capturing all new types.
   */
  void addUnresolved(JSType type) {
    checkState(!type.isResolved());
    this.unresolvedTypes.addLast(type);
  }

  /**
   * If {@link type} is finished construction, and the resolver is closed, resolve it.
   *
   * <p>{@code caller} is used to cooperatively determine whether this call came from the final
   * constructor for {@code type}.
   *
   * <p>This should only be called at the end of {@link JSType} constructors which invoke super. It
   * should be called from all such constructors, except those that directly resolve the instance,
   * such as {@code AllType}.
   */
  void resolveIfClosed(JSType type, JSTypeClass caller) {
    checkState(!type.isResolved());
    if (!caller.isTypeOf(type)) {
      return;
    }

    switch (this.state) {
      case CLOSED:
      case CLOSING:
        JSType lastType = this.unresolvedTypes.removeLast();
        checkState(JSType.areIdentical(type, lastType));
        this.doResolve(type);
        break;

      case OPEN:
        break;
    }
  }

  /**
   * Allow new types to be created without eagerly resolving them.
   *
   * <p>This is required by, and only really useful for, type definition. Types being derived from
   * previously defined types should not contain reference cycles, and so should be eagerly
   * resolvable.
   *
   * <p>The return value is a signal which will resolve all types, and close the resolver, when its
   * {@code close} method is called. This is intended to be used in a try-with-resources statement.
   * This approach was selected over accepting a callback because it supports some level of compile
   * -time enforcement while allowing that enforcement to be suppressed in tests.
   */
  @MustBeClosed
  public AutoCloseable openForDefinition() {
    checkState(this.state.equals(State.CLOSED));
    this.state = State.OPEN;
    return this::resolveAll;
  }

  private void resolveAll() {
    checkState(this.state.equals(State.OPEN));
    this.state = State.CLOSING;

    while (!this.unresolvedTypes.isEmpty()) {
      this.doResolve(this.unresolvedTypes.removeLast());
    }

    this.state = State.CLOSED;
  }

  private void doResolve(JSType unused) {
    // TODO(b/145838483): Call resolve here.
  }
}
