/*
 * Copyright 2025 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.js;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.jscomp.ChangeTracker;
import com.google.javascript.jscomp.resources.ResourceLoader;
import com.google.javascript.rhino.Node;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Injects runtime libraries from the jscomp/js directory into an AST
 *
 * <p>Supports injecting libraries either based on the library path name, or by the specific
 * `$jscomp.*` field/class name.
 */
public final class RuntimeJsLibManager {

  /**
   * What to do with the runtime libraries under the js/ directory: the compiler can ignore them
   * completely; can validate any attempted library usage is correct but not modify the AST; or both
   * validate & add to the AST.
   */
  public enum RuntimeLibraryMode {
    INJECT,
    // mode where the compiler is building a TypedAST for the runtime libraries & can't use them,
    // or where the compiler is in transpile-only mode and running per-file.
    NO_OP,
    // for testing - compiler records that it was asked to inject a runtime library, but does not
    // modify the AST
    RECORD_ONLY,
    // for testing - a stricter version of RECORD_ONLY. The compiler records that it was asked to
    // inject a runtime library, and will also validate injection in JsLibField::assertInjected, but
    // does not modify the AST.
    RECORD_AND_VALIDATE_FIELDS,
  }

  /** Loads /js resources into AST format */
  @FunctionalInterface
  public interface ResourceProvider {
    /**
     * Returns an AST for the given resource name, with a synthetic source file with the given path
     *
     * @param resourceName for example, es6/set
     * @param path for example, /my/filesystem/jscomp/js/es6/set
     */
    Node parse(String resourceName, String path);
  }

  public static final String RUNTIME_LIB_DIR =
  "src/com/google/javascript/jscomp/js/";

  private final ChangeTracker changeTracker;
  private final RuntimeLibraryMode mode;
  private final ResourceProvider resourceProvider;
  private final Supplier<Node> nodeForCodeInsertion;
  private final Set<String> injectedLibs = new LinkedHashSet<>();
  private final Map<String, InternalField> internedFields = new LinkedHashMap<>();

  private @Nullable Node lastInjectedLibrary = null;

  private static final class FieldsTable {
    // Holds the names of all possible $jscomp fields & the file in which they're defined.
    // For example, "inherits" -> "es6/inherits".
    // (This doesn't include nested properties - so no "$jscomp.a.b.c")
    private static final ImmutableMap<String, String> INSTANCE = loadFields();

    private static ImmutableMap<String, String> loadFields() {
      String transpilationLibsTxt =
          ResourceLoader.loadTextResource(RuntimeJsLibManager.class, "transpilation_libs.txt");

      ImmutableMap.Builder<String, String> allFields = ImmutableMap.builder();
      for (String line : Splitter.on('\n').omitEmptyStrings().split(transpilationLibsTxt)) {
        List<String> tokens = Splitter.on(',').limit(2).splitToList(line);
        checkState(tokens.size() == 2, tokens);
        String fieldName = tokens.get(0);
        String resourcePath = tokens.get(1);
        allFields.put(fieldName, resourcePath);
      }
      return allFields.buildOrThrow();
    }
  }

  private RuntimeJsLibManager(
      ChangeTracker changeTracker,
      RuntimeLibraryMode mode,
      ResourceProvider resourceProvider,
      Supplier<Node> nodeForCodeInsertion) {
    this.mode = mode;
    this.changeTracker = changeTracker;
    this.resourceProvider = resourceProvider;
    this.nodeForCodeInsertion = nodeForCodeInsertion;
  }

  public static RuntimeJsLibManager create(
      RuntimeLibraryMode mode,
      ResourceProvider resourceProvider,
      ChangeTracker changeTracker,
      Supplier<Node> nodeForCodeInsertion) {

    return new RuntimeJsLibManager(changeTracker, mode, resourceProvider, nodeForCodeInsertion);
  }

  /**
   * Records that the given library has already been injected into the compilation.
   *
   * <p>This doesn't actually modify the AST - it can be used if e.g. restoring compilation state
   * after serialization/deserialization.
   *
   * @param resourceName The name of the library. For example, if "base" is is specified, then we
   *     record js/base.js
   */
  public void recordLibraryInjected(String resourceName) {
    injectedLibs.add(resourceName);
  }

  /** Returns true if the given library has already been injected into the compilation. */
  public boolean hasInjectedLibrary(String resourceName) {
    return injectedLibs.contains(resourceName);
  }

  /**
   * Returns a list of all library paths previously injected, via one of the other methods on this
   * class.
   */
  public ImmutableList<String> getInjectedLibraries() {
    return ImmutableList.copyOf(injectedLibs);
  }

  private InternalField createField(String fieldName) {
    checkArgument(!fieldName.isEmpty(), fieldName);

    List<String> parts = Splitter.on('.').splitToList(fieldName);
    checkArgument(parts.size() >= 2, "Field name must start with $jscomp., found %s", fieldName);
    checkArgument(
        parts.get(0).equals("$jscomp"), "Field name must start with $jscomp, found %s", fieldName);
    String propName = parts.get(1);

    String resourceName =
        checkNotNull(FieldsTable.INSTANCE.get(propName), "Cannot find definition of %s", fieldName);
    // TODO: b/421971366 - allow @closureUnaware transpilation to use a different local name for
    // fields.
    return new InternalField(resourceName, fieldName);
  }

  /**
   * A $jscomp.* field (could be a method, class, or any arbitrary value) that exists in some
   * runtime library under the js/ directory.
   */
  private final class InternalField implements InjectedJsLibField {
    /** The file containing this field, e.g. "es6/generator". */
    private final String resourceName;

    /** The name by which compiler passes should refer to this field. */
    private final String qualifiedName;

    private boolean injected = false;

    private InternalField(String resourceName, String qualifiedName) {
      this.resourceName = resourceName;
      this.qualifiedName = qualifiedName;
    }

    private void markInjected() {
      this.injected = true;
    }

    @Override
    @CanIgnoreReturnValue
    public InternalField assertInjected() {
      switch (mode) {
        case NO_OP, RECORD_ONLY -> {}
        case RECORD_AND_VALIDATE_FIELDS, INJECT ->
            checkState(this.injected, "Field %s is not injected", this.qualifiedName);
      }
      return this;
    }

    @Override
    public boolean matches(Node node) {
      return node.matchesQualifiedName(this.qualifiedName);
    }

    // Don't override .equals/.hashCode and just use reference equality: we already intern Fields
    // by qualifiedName.

    @Override
    public String toString() {
      return Strings.lenientFormat("Field<%s, %s>", qualifiedName, resourceName);
    }

    @Override
    public String qualifiedName() {
      return this.qualifiedName;
    }
  }

  /**
   * Returns a field definition for the given name, without actually asserting or requiring its
   * definition to be injected.
   *
   * @throws an IllegalArgumentException if passed a nonexistent field name.
   */
  public JsLibField getJsLibField(String fieldName) {
    // wrapper around getJsLibFieldInternal that returns the broader JsLibField interface
    return getJsLibFieldInternal(fieldName);
  }

  private InternalField getJsLibFieldInternal(String fieldName) {
    return internedFields.computeIfAbsent(fieldName, this::createField);
  }

  /**
   * A $jscomp.* field (could be a method, class, or any arbitrary value) that exists in some
   * runtime library under the js/ directory.
   */
  public interface JsLibField {
    boolean matches(Node node);

    InjectedJsLibField assertInjected();
  }

  /**
   * A {@link JsLibField} that is statically guaranteed to be available at runtime, i.e. that has
   * been injected into the AST.
   *
   * <p>Only {@link InjectedJsLibField} instances provide direct access to the field name - this is
   * to prevent code from creating new AST references to a field that's not actually injected (yet).
   */
  public interface InjectedJsLibField extends JsLibField {
    String qualifiedName();
  }

  /** Injects the runtime library that defines the given $jscomp.* field. */
  public void injectLibForField(String fieldName) {
    InternalField field = getJsLibFieldInternal(fieldName);
    field.markInjected();
    ensureLibraryInjected(field.resourceName, /* force= */ false);
  }

  /**
   * The subdir js/ contains libraries of code that we inject at compile-time only if requested by
   * this function.
   *
   * <p>Notice that these libraries will almost always create global symbols.
   *
   * @param resourceName The name of the library. For example, if "base" is is specified, then we
   *     load js/base.js
   * @param force Inject the library even if compiler options say not to.
   * @return The last node of the most-recently-injected runtime library. If new code was injected,
   *     this will be the last expression node of the library. If the caller needs to add additional
   *     code, they should add it as the next sibling of this node. If no runtime libraries have
   *     been injected, then null is returned.
   */
  @CanIgnoreReturnValue
  public @Nullable Node ensureLibraryInjected(String resourceName, boolean force) {
    if (!force) {
      switch (mode) {
        case NO_OP -> {
          return lastInjectedLibrary;
        }
        case RECORD_ONLY, RECORD_AND_VALIDATE_FIELDS -> {
          this.recordLibraryInjected(resourceName);
          return lastInjectedLibrary;
        }
        case INJECT -> {} // Keep going.
      }
    }

    if (this.injectedLibs.contains(resourceName)) {
      return lastInjectedLibrary;
    }
    recordLibraryInjected(resourceName);

    String path = String.join("", RUNTIME_LIB_DIR, resourceName, ".js");
    Node ast = resourceProvider.parse(resourceName, path);

    injectLibraryDependencies(ast, force); // may also modify 'ast'
    return injectLibrary(ast);
  }

  private Node injectLibrary(Node ast) {
    if (!ast.hasChildren()) {
      // Require-only libraries may be empty at this point. Nothing to do here.
      return lastInjectedLibrary;
    }

    for (Node child = ast.getFirstChild(); child != null; child = child.getNext()) {
      changeTracker.markNewScopesChanged(child);
    }
    Node endOfLib = ast.getLastChild();
    Node firstChild = ast.removeChildren();

    // Insert the code immediately after the last-inserted runtime library, if any.
    Node parent = nodeForCodeInsertion.get();
    if (lastInjectedLibrary == null) {
      parent.addChildrenToFront(firstChild);
    } else {
      parent.addChildrenAfter(firstChild, lastInjectedLibrary);
    }
    lastInjectedLibrary = endOfLib;

    changeTracker.reportChangeToEnclosingScope(parent);
    return endOfLib;
  }

  private void injectLibraryDependencies(Node ast, boolean force) {
    // Look for string literals of the form 'require foo bar'
    // As we process each one, remove it from its parent.
    for (Node node = ast.getFirstChild();
        node != null && node.isExprResult() && node.getFirstChild().isStringLit();
        node = ast.getFirstChild()) {
      String directive = node.getFirstChild().getString();
      List<String> words = Splitter.on(' ').limit(2).splitToList(directive);
      switch (words.get(0)) {
        // 'use strict' is ignored (and deleted).
        case "use" -> {}
        // 'require lib'; pulls in the named library before this one.
        case "require" -> ensureLibraryInjected(words.get(1), force);
        default -> throw new IllegalStateException("Bad directive: " + directive);
      }
      node.detach();
    }
  }

  public void setLastInjectedLibrary(@Nullable Node lastInjectedLibrary) {
    this.lastInjectedLibrary = lastInjectedLibrary;
  }

  public @Nullable Node getLastInjectedLibrary() {
    return lastInjectedLibrary;
  }
}
