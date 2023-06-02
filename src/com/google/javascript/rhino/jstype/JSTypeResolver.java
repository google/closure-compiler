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
import static com.google.javascript.jscomp.base.JSCompObjects.identical;

import com.google.errorprone.annotations.MustBeClosed;
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
public final class JSTypeResolver {

  /**
   * A signal to resolve all types known to and close the owning resolver.
   *
   * <p>This is intended to be used in a try-with-resources statement. This approach was selected
   * over accepting a callback because it supports some level of compile-time enforcement while
   * allowing that enforcement to be suppressed in tests.
   */
  public final class Closer implements AutoCloseable {

    private boolean hasRun = false;

    @Override
    public void close() {
      checkState(!this.hasRun);
      this.hasRun = true;
      JSTypeResolver.this.resolveAll();
    }
  }

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

  /**
   * The sequence of instantiated types.
   *
   * <p>This allows verification that every new type is captured by this resolver. In general this
   * stack should never be more than a handful of types.
   */
  private final ArrayDeque<JSType> captureStack = new ArrayDeque<>();

  /** The sequence of types to resolve when the resolver is closed. */
  private ArrayDeque<JSType> resolutionQueue = new ArrayDeque<>();

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
  void addUnresolved(JSType captured) {
    /*
     * This method exists to make it easier to debug the source of unresolved types in the future.
     *
     * <p>In theory we should be able to capture all types using `resolveIfClosed`, but this way we
     * get some guarantees against misuse. Separating capturing and resolving allows us to verify
     * our invariants.
     */
    checkState(!captured.isResolved());
    this.captureStack.addLast(captured);
  }

  /**
   * If {@code captured} is finished construction, and the resolver is closed, resolve it.
   *
   * <p>{@code caller} is used to cooperatively determine whether this call came from the final
   * constructor for {@code type}.
   *
   * <p>This should only be called at the end of {@link JSType} constructors which invoke super. It
   * should be called from all such constructors. Some constructors (e.g. {@code AllType}) may
   * choose to delegate that call through {@link JSType#eagerlyResolveToSelf}.
   */
  void resolveIfClosed(JSType captured, JSTypeClass caller) {
    if (!caller.isTypeOf(captured)) {
      return;
    }

    JSType expected = this.captureStack.removeLast();
    checkState(identical(captured, expected), "Captured %s; Expected %s", captured, expected);

    switch (this.state) {
      case CLOSED:
      case CLOSING:
        this.doResolve(captured);
        break;

      case OPEN:
        this.resolutionQueue.addLast(captured);
        break;
    }
  }

  /**
   * Allow new types to be created without eagerly resolving them.
   *
   * <p>This is required by, and only really useful for, type definition. Types being derived from
   * previously defined types should not contain reference cycles, and so should be eagerly
   * resolvable.
   */
  @MustBeClosed
  public Closer openForDefinition() {
    checkState(this.state.equals(State.CLOSED));
    checkState(this.captureStack.isEmpty());

    this.state = State.OPEN;
    return new Closer();
  }

  private void resolveAll() {
    checkState(this.state.equals(State.OPEN));
    checkState(this.captureStack.isEmpty());

    this.state = State.CLOSING;

    while (!this.resolutionQueue.isEmpty()) {
      this.doResolve(this.resolutionQueue.removeFirst());
    }

    // resolutionQueue scales with the size of the application, so it needs to be GC'd.
    this.resolutionQueue = new ArrayDeque<>();

    this.state = State.CLOSED;

    // TODO(sdh): Stop doing this here. It's obviously the wrong place.
    // By default, the global "this" type is just an anonymous object.
    // If the user has defined a Window type, make the Window the
    // implicit prototype of "this".
    PrototypeObjectType globalThis =
        (PrototypeObjectType) this.registry.getNativeType(JSTypeNative.GLOBAL_THIS);
    JSType windowType = this.registry.getGlobalType("Window");
    if (globalThis.isUnknownType()) {
      ObjectType windowObjType = ObjectType.cast(windowType);
      if (windowObjType != null) {
        globalThis.setImplicitPrototype(windowObjType);
      } else {
        globalThis.setImplicitPrototype(
            this.registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE));
      }
    }
  }

  private void doResolve(JSType type) {
    type.resolve(this.registry.getErrorReporter());
  }

  /**
   * Asserts that it's legal to call {@link JSType#resolve}
   *
   * <p>The intent is to enforce that while a type is being resolved, any new types synthesized will
   * be immediately resolved.
   */
  void assertLegalToResolveTypes() {
    checkState(
        !this.state.equals(State.OPEN), "Types cannot be resolved while the registry is open");
  }
}
