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

package com.google.javascript.jscomp.lint;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.errorprone.annotations.Immutable;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Checks that Closure import statements (goog.require, goog.requireType, and goog.forwardDeclare)
 * are sorted and deduplicated, exposing the necessary information to produce a suggested fix.
 */
public final class CheckRequiresSorted implements NodeTraversal.Callback {
  public static final DiagnosticType REQUIRES_NOT_SORTED =
      DiagnosticType.warning(
          "JSC_REQUIRES_NOT_SORTED",
          "goog.require() and goog.requireType() statements are not sorted."
              + " The correct order is:\n\n{0}\n");

  /** Operation modes. */
  public enum Mode {
    /** Collect information to determine whether a fix is required, but do not report a warning. */
    COLLECT_ONLY,
    /** Additionally report a warning. */
    COLLECT_AND_REPORT
  };

  /** Primitives that may be called in an import statement. */
  enum ImportPrimitive {
    REQUIRE("goog.require"),
    REQUIRE_TYPE("goog.requireType"),
    FORWARD_DECLARE("goog.forwardDeclare");

    private final String name;

    private ImportPrimitive(String name) {
      this.name = name;
    }

    /** Returns the primitive with the given name. */
    static ImportPrimitive fromName(String name) {
      for (ImportPrimitive primitive : values()) {
        if (primitive.name.equals(name)) {
          return primitive;
        }
      }
      throw new IllegalArgumentException("Invalid primitive name " + name);
    }

    static ImportPrimitive WEAKEST = FORWARD_DECLARE;

    /**
     * Returns the stronger of two primitives.
     *
     * <p>`goog.require` is stronger than `goog.requireType`, which is stronger than
     * `goog.forwardDeclare`.
     */
    @Nullable
    static ImportPrimitive stronger(ImportPrimitive p1, ImportPrimitive p2) {
      return p1.ordinal() < p2.ordinal() ? p1 : p2;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  /**
   * One of the bindings of a destructuring pattern.
   *
   * <p>{@code exportedName} and {@code localName} are equal in the case where the binding does not
   * explicitly specify a local name.
   */
  @AutoValue
  @Immutable
  abstract static class DestructuringBinding implements Comparable<DestructuringBinding> {
    abstract String exportedName();

    abstract String localName();

    static DestructuringBinding of(String exportedName, String localName) {
      return new AutoValue_CheckRequiresSorted_DestructuringBinding(exportedName, localName);
    }

    /** Compares two bindings according to the style guide sort order. */
    @Override
    public int compareTo(DestructuringBinding other) {
      return ComparisonChain.start()
          .compare(this.exportedName(), other.exportedName())
          .compare(this.localName(), other.localName())
          .result();
    }
  }

  /**
   * An import statement, which may have been merged from several import statements for the same
   * namespace in the original code.
   *
   * <p>An import statement has exactly one of three shapes:
   *
   * <ul>
   *   <li>Standalone: has no LHS, as in `goog.require('namespace')`.
   *   <li>Aliasing: has an LHS with an alias, as in `const alias = goog.require('namespace')`.
   *   <li>Destructuring: has an LHS with a destructuring pattern, as in `const {name: localName} =
   *       goog.require('namespace')`.
   * </ul>
   */
  @AutoValue
  abstract static class ImportStatement implements Comparable<ImportStatement> {
    /** Returns the nodes this import statement was merged from, in source order. */
    abstract ImmutableList<Node> nodes();

    /** Returns the import primitive being called. */
    abstract ImportPrimitive primitive();

    /** Returns the namespace being imported. */
    abstract String namespace();

    /** Returns the alias for an aliasing import, or null if the import isn't aliasing. */
    abstract @Nullable String alias();

    /**
     * Returns the destructures for a destructuring import in source order, or null if the import
     * isn't destructuring.
     *
     * <p>If the import is destructuring but the pattern is empty, the value is non-null but empty.
     */
    abstract @Nullable ImmutableList<DestructuringBinding> destructures();

    /** Creates a new import statement. */
    static ImportStatement of(
        ImmutableList<Node> nodes,
        ImportPrimitive primitive,
        String namespace,
        @Nullable String alias,
        @Nullable ImmutableList<DestructuringBinding> destructures) {
      Preconditions.checkArgument(
          alias == null || destructures == null,
          "Import statement cannot be simultaneously aliasing and destructuring");
      return new AutoValue_CheckRequiresSorted_ImportStatement(
          nodes, primitive, namespace, alias, destructures);
    }

    /** Returns whether the import is standalone. */
    boolean isStandalone() {
      return !isAliasing() && !isDestructuring();
    }

    /** Returns whether the import is aliasing. */
    boolean isAliasing() {
      return alias() != null;
    }

    /** Returns whether the import is destructuring. */
    boolean isDestructuring() {
      return destructures() != null;
    }

    /**
     * Returns an import statement identical to the current one, except for its primitive, which is
     * upgraded to the given one if stronger.
     */
    ImportStatement upgrade(ImportPrimitive otherPrimitive) {
      if (ImportPrimitive.stronger(primitive(), otherPrimitive) != primitive()) {
        return new AutoValue_CheckRequiresSorted_ImportStatement(
            nodes(), otherPrimitive, namespace(), alias(), destructures());
      }
      return this;
    }

    private String formatWithoutDoc() {
      StringBuilder sb = new StringBuilder();

      if (!isStandalone()) {
        sb.append("const ");
      }
      if (alias() != null) {
        sb.append(alias());
      }
      if (destructures() != null) {
        sb.append("{");
        boolean first = true;
        for (DestructuringBinding binding : destructures()) {
          String exportedName = binding.exportedName();
          String localName = binding.localName();
          if (first) {
            first = false;
          } else {
            sb.append(", ");
          }
          sb.append(exportedName);
          if (!exportedName.equals(localName)) {
            sb.append(": ");
            sb.append(localName);
          }
        }
        sb.append("}");
      }
      if (!isStandalone()) {
        sb.append(" = ");
      }
      sb.append(primitive().toString());
      sb.append("('");
      sb.append(namespace());
      sb.append("');");

      return sb.toString();
    }

    /** Formats the import statement into code. */
    public String format() {
      StringBuilder sb = new StringBuilder();

      for (Node node : nodes()) {
        JSDocInfo jsDoc = NodeUtil.getBestJSDocInfo(node);
        if (jsDoc != null) {
          sb.append(jsDoc.getOriginalCommentString()).append("\n");
        }
      }

      sb.append(formatWithoutDoc());

      return sb.toString();
    }

    /** Compares two import statements according to the style guide sort order. */
    @Override
    public int compareTo(ImportStatement other) {
      return this.formatWithoutDoc().compareTo(other.formatWithoutDoc());
    }
  }

  private final Mode mode;

  // Maps each namespace into the existing import statements for that namespace.
  // Use an ArrayListMultimap so that values for a key are iterated in a deterministic order.
  private final Multimap<String, ImportStatement> importsByNamespace = ArrayListMultimap.create();

  // The import statements in the order they appear.
  private final List<ImportStatement> originalImports = new ArrayList<>();

  // The import statements in canonical order.
  @Nullable private List<ImportStatement> canonicalImports = null;

  @Nullable private Node firstNode = null;
  @Nullable private Node lastNode = null;
  private boolean finished = false;

  private boolean needsFix = false;
  @Nullable private String replacement = null;

  public CheckRequiresSorted(Mode mode) {
    this.mode = mode;
  }

  /** Returns the node for the first recognized import statement. */
  public Node getFirstNode() {
    return firstNode;
  }

  /** Returns the node for the last recognized import statement. */
  public Node getLastNode() {
    return lastNode;
  }

  /** Returns a textual replacement yielding a canonical version of the imports. */
  public String getReplacement() {
    return replacement;
  }

  /**
   * Returns whether the imports need to be fixed, i.e., whether they are *not* already canonical.
   */
  public boolean needsFix() {
    return needsFix;
  }

  @Override
  public final boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    // Traverse top-level statements until a block of contiguous requires is found.
    return !finished
        && (parent == null || parent.isRoot() || parent.isScript() || parent.isModuleBody());
  }

  @Override
  public final void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isScript()) {
      checkCanonical(t);
      return;
    }

    Node callNode = null;
    if (n.isExprResult()) {
      callNode = n.getFirstChild();
    } else if (NodeUtil.isNameDeclaration(n)) {
      callNode = n.getFirstChild().getLastChild();
    }
    if (callNode != null && isValidImportCall(callNode)) {
      ImportStatement stmt = parseImport(callNode);
      originalImports.add(stmt);
      importsByNamespace.put(stmt.namespace(), stmt);
      if (firstNode == null) {
        firstNode = lastNode = n;
      } else {
        lastNode = n;
      }
    } else if (!importsByNamespace.isEmpty()) {
      finished = true;
    }
  }

  private static boolean isValidImportCall(Node n) {
    return n.isCall()
        && n.hasTwoChildren()
        && (n.getFirstChild().matchesQualifiedName("goog.require")
            || n.getFirstChild().matchesQualifiedName("goog.requireType")
            || n.getFirstChild().matchesQualifiedName("goog.forwardDeclare"))
        && n.getSecondChild().isString();
  }

  private static ImportStatement parseImport(Node callNode) {
    ImportPrimitive primitive =
        ImportPrimitive.fromName(callNode.getFirstChild().getQualifiedName());
    String namespace = callNode.getSecondChild().getString();

    Node parent = callNode.getParent();
    if (parent.isExprResult()) {
      // goog.require('a');
      return ImportStatement.of(
          ImmutableList.of(parent),
          primitive,
          namespace,
          /* alias= */ null,
          /* destructures= */ null);
    }

    Node grandparent = parent.getParent();
    if (parent.isName()) {
      // const a = goog.require('a');
      String alias = parent.getString();
      return ImportStatement.of(
          ImmutableList.of(grandparent), primitive, namespace, alias, /* destructures= */ null);
    }
    // const {a: b, c} = goog.require('a');
    ImmutableList.Builder<DestructuringBinding> destructures = ImmutableList.builder();
    for (Node name : parent.getFirstChild().children()) {
      String exportedName = name.getString();
      String localName = name.getFirstChild().getString();
      destructures.add(DestructuringBinding.of(exportedName, localName));
    }
    return ImportStatement.of(
        ImmutableList.of(grandparent),
        primitive,
        namespace,
        /* alias= */ null,
        destructures.build());
  }

  private void checkCanonical(NodeTraversal t) {
    canonicalImports = canonicalizeImports(importsByNamespace);
    if (!originalImports.equals(canonicalImports)) {
      needsFix = true;
      replacement =
          String.join("\n", Iterables.transform(canonicalImports, ImportStatement::format));
      if (mode == Mode.COLLECT_AND_REPORT) {
        t.report(firstNode, REQUIRES_NOT_SORTED, replacement);
      }
    }
  }

  /**
   * Canonicalizes a list of import statements by deduplicating and merging imports for the same
   * namespace, and sorting the result.
   */
  private static List<ImportStatement> canonicalizeImports(
      Multimap<String, ImportStatement> importsByNamespace) {
    List<ImportStatement> canonicalImports = new ArrayList<>();

    for (String namespace : importsByNamespace.keySet()) {
      Collection<ImportStatement> allImports = importsByNamespace.get(namespace);

      // Find the strongest primitive across all existing imports. Every emitted import for this
      // namespace will use this primitive. This makes the logic simpler and cannot change runtime
      // behavior, but may produce spurious changes when multiple aliasing imports of differing
      // strength exist (which are already in violation of the style guide).
      ImportPrimitive strongestPrimitive =
          allImports.stream()
              .map(ImportStatement::primitive)
              .reduce(ImportPrimitive.WEAKEST, ImportPrimitive::stronger);

      // Emit each aliasing import separately, as deduplicating them would require code references
      // to be rewritten.
      boolean hasAliasing = false;
      for (ImportStatement stmt : Iterables.filter(allImports, ImportStatement::isAliasing)) {
        canonicalImports.add(stmt.upgrade(strongestPrimitive));
        hasAliasing = true;
      }

      // Emit a single destructuring import with a non-empty pattern, merged from the existing
      // destructuring imports.
      boolean hasDestructuring = false;
      ImmutableList<Node> destructuringNodes =
          allImports.stream()
              .filter(ImportStatement::isDestructuring)
              .flatMap(i -> i.nodes().stream())
              .collect(toImmutableList());
      ImmutableList<DestructuringBinding> destructures =
          allImports.stream()
              .filter(ImportStatement::isDestructuring)
              .flatMap(i -> i.destructures().stream())
              .distinct()
              .sorted()
              .collect(toImmutableList());
      if (!destructures.isEmpty()) {
        canonicalImports.add(
            ImportStatement.of(
                destructuringNodes,
                strongestPrimitive,
                namespace,
                /* alias= */ null,
                destructures));
        hasDestructuring = true;
      }

      // Emit a standalone import unless an aliasing or destructuring one already exists.
      if (!hasAliasing && !hasDestructuring) {
        ImmutableList<Node> standaloneNodes =
            allImports.stream()
                .filter(ImportStatement::isStandalone)
                .flatMap(i -> i.nodes().stream())
                .collect(toImmutableList());
        canonicalImports.add(
            ImportStatement.of(
                standaloneNodes,
                strongestPrimitive,
                namespace,
                /* alias= */ null,
                /* destructures= */ null));
      }
    }

    // Sorting by natural order yields the correct result due to the implementation of
    // ImportStatement#compareTo.
    Collections.sort(canonicalImports);

    return canonicalImports;
  }
}
