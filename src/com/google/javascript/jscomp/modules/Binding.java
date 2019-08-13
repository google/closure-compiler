/*
 * Copyright 2018 The Closure Compiler Authors.
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
import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.rhino.Node;
import javax.annotation.Nullable;

/**
 * Represents a variable bound by an import or export statement, or goog.require. This can either be
 * a single variable or a entire module namespace created by an import * statement.
 *
 * <p>See {@link Module#namespace()} and {@link Module#boundNames()} for how Bindings are used.
 */
@AutoValue
public abstract class Binding {
  // Prevent unwanted subclasses.
  Binding() {}

  /** Different ways that Bindings can be created. */
  enum CreatedBy {
    /**
     * A binding created by an export in an ES module.
     *
     * <pre>
     *   export const x = 0;
     *   export function x() {}
     *   export {x};
     *   export * from ''; // creates bindings in the namespace
     * </pre>
     */
    EXPORT,
    /**
     * A binding created by an ES import.
     *
     * <pre>
     *   import {x} from '';
     *   import * as x from '';
     *   import x from '';
     * </pre>
     */
    IMPORT,
    /**
     * A binding created by a goog.require statement.
     *
     * <pre>
     *   const x = goog.require();
     *   const {x} = goog.require();
     * </pre>
     */
    GOOG_REQUIRE,
    /**
     * A binding created by a goog.requireType statement.
     *
     * <pre>
     *   const x = goog.requireType();
     *   const {x} = goog.requireType();
     * </pre>
     */
    GOOG_REQUIRE_TYPE,
    /**
     * A binding created by a goog.forwardDeclare statement.
     *
     * <pre>
     *   const x = goog.forwardDeclare();
     *   const {x} = goog.forwardDeclare();
     * </pre>
     */
    GOOG_FORWARD_DECLARE;

    /** Whether this is some goog.* dependency import */
    boolean isClosureImport() {
      return this.equals(GOOG_REQUIRE)
          || this.equals(GOOG_REQUIRE_TYPE)
          || this.equals(GOOG_FORWARD_DECLARE);
    }
  }

  /** Binding for an exported value that is not a module namespace object. */
  static Binding from(Export boundExport, Node sourceNode) {
    return new AutoValue_Binding(
        boundExport.moduleMetadata(),
        sourceNode,
        boundExport,
        /* isModuleNamespace= */ false,
        /* closureNamespace= */ boundExport.closureNamespace(),
        CreatedBy.EXPORT);
  }

  /** Binding for an entire module namespace created by const x = goog.require(Type)('...') */
  static Binding from(
      ModuleMetadata metadata, Node sourceNode, String closureNamespace, CreatedBy createdBy) {
    Preconditions.checkArgument(
        createdBy.isClosureImport(),
        "Expected goog.require(Type) or goog.forwardDeclare, got %s",
        createdBy);
    return new AutoValue_Binding(
        metadata,
        sourceNode,
        /* originatingExport= */ null,
        /* isModuleNamespace= */ true,
        closureNamespace,
        createdBy);
  }

  /** Binding for an entire module namespace created by an <code>import *</code>. */
  static Binding from(
      ModuleMetadata metadataOfBoundModule, @Nullable String closureNamespace, Node sourceNode) {
    return new AutoValue_Binding(
        metadataOfBoundModule,
        sourceNode,
        /* originatingExport= */ null,
        /* isModuleNamespace= */ true,
        closureNamespace,
        CreatedBy.IMPORT);
  }

  /** Copies the binding with a new source node and CreatedBy binding. */
  Binding copy(Node sourceNode, CreatedBy createdBy) {
    checkNotNull(sourceNode);

    return new AutoValue_Binding(
        metadata(),
        sourceNode,
        originatingExport(),
        isModuleNamespace(),
        closureNamespace(),
        createdBy);
  }

  /**
   * Metadata of the module this is bound to.
   *
   * <p>If this was made from an {@code import *} then it is the module that this imported.
   * Otherwise it is the module this binding was created in (e.g. the module of the originating
   * export).
   */
  public abstract ModuleMetadata metadata();

  /**
   * The AST node to use for source location when rewriting.
   *
   * <p>This is generally a NAME or IMPORT_STAR node inside an import or export statement that
   * represents where the name was bound. However as {@code export * from} has no NAME nodes the
   * source node in that instance should be the entire export node.
   *
   * <p>Null for missing ES modules and non-ES modules as they are currently not scanned.
   */
  @Nullable
  public abstract Node sourceNode();

  /**
   * Returns the original export if this binding was created by an export.
   *
   * <p>For transitive exports this will still be the *original* export, not the transitive link.
   */
  @Nullable
  public abstract Export originatingExport();

  /** True if this represents a module namespace, e.g. created by {@code import *} */
  public abstract boolean isModuleNamespace();

  @Nullable
  public abstract String closureNamespace();

  public abstract CreatedBy createdBy();

  /**
   * The name of the variable this export is bound to, assuming it is not a binding of a module
   * namespace.
   */
  public final String boundName() {
    checkState(!isModuleNamespace());
    return originatingExport().localName();
  }

  /**
   * Returns whether or not this export is potentially mutated after module execution (i.e. in a
   * function scope).
   */
  public final boolean isMutated() {
    // Module namespaces can never be mutated. They are always imported, and import bound names
    // are const.
    return !isModuleNamespace() && originatingExport().mutated();
  }

  /**
   * Returns whether this Binding originated from an ES import, as opposed to an export or
   * goog.require.
   */
  public final boolean isCreatedByEsImport() {
    return createdBy().equals(CreatedBy.IMPORT);
  }

  /**
   * Returns whether this Binding originated from an ES import, as opposed to an export or
   * goog.require.
   */
  public final boolean isCreatedByEsExport() {
    return createdBy().equals(CreatedBy.EXPORT);
  }

  /** Returns whether this Binding originated from an ES import or goog.require */
  public final boolean isSomeImport() {
    return !createdBy().equals(CreatedBy.EXPORT);
  }
}
