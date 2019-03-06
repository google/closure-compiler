/*
 * Copyright 2019 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.modules;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.javascript.rhino.Node;
import javax.annotation.Nullable;

/**
 * The result of resolving an export, which can be a valid binding, ambiguous, not found, or an
 * error.
 */
final class ResolveExportResult {
  private enum State {
    RESOLVED,
    AMBIGUOUS,
    NOT_FOUND,
    ERROR,
  }

  @Nullable private final Binding binding;
  private final State state;

  private ResolveExportResult(@Nullable Binding binding, State state) {
    this.binding = binding;
    this.state = state;
  }

  /**
   * Creates a new result that has the given node for the source of the binding and given type of
   * binding.
   */
  ResolveExportResult copy(Node sourceNode, Binding.CreatedBy createdBy) {
    checkNotNull(sourceNode);

    if (binding == null) {
      return this;
    }

    return new ResolveExportResult(binding.copy(sourceNode, createdBy), state);
  }

  /** True if there was an error resolving the export, false otherwise. */
  boolean hadError() {
    return state == State.ERROR;
  }

  /** True if the export is ambiguous, false otherwise. */
  boolean isAmbiguous() {
    return state == State.AMBIGUOUS;
  }

  /** True if the export was successfully resolved, false otherwise. */
  boolean resolved() {
    return state == State.RESOLVED;
  }

  /** True if the export key exists on the given module, even if it is ambiguous or had an error. */
  public boolean found() {
    return state != State.NOT_FOUND;
  }

  @Nullable
  public Binding getBinding() {
    return binding;
  }

  /**
   * The result of resolving the export was ambiguous.
   *
   * <p>This happens when there are multiple {@code export * from} statements that end up causing
   * the same key to be re-exported.
   *
   * <p>When resolving an import or transitive export, if the result is ambiguous, an error should
   * be reported at the import / transitive export site, an then {@link #ERROR} returned so that
   * more ambiguous errors are not reported.
   */
  static final ResolveExportResult AMBIGUOUS = new ResolveExportResult(null, State.AMBIGUOUS);

  /**
   * The export was not found because the module never exported the key.
   *
   * <p>When resolving an import or transitive export, if the result is not found, an error should
   * be reported at the import / transitive export site, an then {@link #ERROR} returned so that
   * more ambiguous errors are not reported.
   */
  static final ResolveExportResult NOT_FOUND = new ResolveExportResult(null, State.NOT_FOUND);

  /**
   * There was an error resolving the export.
   *
   * <p>This can mean that:
   *
   * <ol>
   *   <li>When resolving a transitive export, the transitive export was not found.
   *   <li>When resolving a transitive export, the transitive export was ambiguous.
   *   <li>There was a cycle resolving an export.
   *   <li>The requested module does not exist.
   * </ol>
   *
   * <p>When resolving an import or transitive export, if the result is {@code ERROR}, then
   * resolving should also return {@code ERROR}. No error needs to be reported, this is an
   * indication that something has already been reported.
   */
  static final ResolveExportResult ERROR = new ResolveExportResult(null, State.ERROR);

  static ResolveExportResult of(Binding binding) {
    checkNotNull(binding);
    return new ResolveExportResult(binding, State.RESOLVED);
  }
}
