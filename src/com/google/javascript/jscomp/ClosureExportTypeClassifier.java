/*
 * Copyright 2026 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Syntactically classifies whether each goog.module namespace in a file is seen from TypeScript as
 * a set of "named" exports or as a single "default" export.
 *
 * <p>This approximates the type-based logic originally implemented in Clutz. So most of the
 * (potentially surprising) logic is in place to mirror Clutz & allow migration to this syntactic
 * classifier.
 *
 * <p>The core rules:
 *
 * <ul>
 *   <li>Object literals with at least one non-computed, non-spread key are NAMED.
 *   <li>Properties assigned or declared ({@code /** @type {T} *\/ exports.x;}) on {@code exports},
 *       or on a local alias of it, make an empty object NAMED.
 *   <li>{@code exports = {a, b};} in a goog.module is NAMED even with a declared type, since module
 *       rewriting turns it into {@code exports.a = a; exports.b = b;} and drops the JSDoc.
 * </ul>
 *
 * <p>Users can make a namespace `named` with {@code @legacyNamedTsExport} on its {@code
 * goog.module} call. The annotation has no effect elsewhere.
 */
final class ClosureExportTypeClassifier {

  private static final String EXPORTS = "exports";

  /**
   * How many local aliases to follow, e.g. {@code const a = {}; const b = a; exports = b;}.
   *
   * <p>Clutz follows aliases without a limit; this is an arbitrary cap to keep the work bounded.
   * Comparing against Clutz on a large codebase found no namespace that needs a longer chain.
   */
  private static final int MAX_ALIAS_DEPTH = 4;

  /** The syntactic "shape" of a value, as relevant for determining its export type. */
  private enum Kind {
    /** Classes, functions, primitives, etc. */
    NOT_NAMESPACE,
    /**
     * An object with no known properties, like {@code {}}. May be treated as either NAMED or
     * DEFAULT depending on whether properties are added to it later on in the file.
     */
    EMPTY_OBJECT,
    /** An object with at least one property. */
    NAMESPACE
  }

  /** Facts gathered about a top-level name: {@code exports} or a local variable. */
  private static final class NameInfo {
    /** The last value assigned to this name, if any. */
    @Nullable Node value;

    /** The JSDoc on the declaration or assignment of {@link #value}. */
    @Nullable JSDocInfo jsdoc;

    /**
     * Whether this name is declared or assigned in a way that is never a namespace (class,
     * function, or stub typedef).
     */
    boolean isNeverNamespace;

    /** Number of properties assigned or declared on this name ({@code name.x = ...;}). */
    int propertyCount;

    /** Whether this is a goog.module's {@code exports}. */
    boolean isGoogModuleExports;

    /**
     * Whether {@link #value} is assigned as {@code exports = {a, b};} in a goog.module.
     * JSCompiler's module rewriting turns that into {@code exports.a = a; exports.b = b;} (see
     * {@link NodeUtil#isNamedExportsLiteral}), dropping any JSDoc on the assignment, so it's always
     * NAMED.
     */
    boolean isNamedExportsLiteral;
  }

  private final Map<String, NameInfo> topLevelNames = new LinkedHashMap<>();

  /** Namespaces whose goog.module call is annotated {@code @legacyNamedTsExport}. */
  private final Set<String> forcedNamedExport = new LinkedHashSet<>();

  enum TsExportType {
    /** The module exports a single default value. */
    DEFAULT,
    /** The module exports multiple named values. */
    NAMED
  }

  /**
   * Classifies a single {@code goog.module} namespace as named or default.
   *
   * @param moduleRoot the SCRIPT node for this module
   * @param googNamespace the namespace of this module
   */
  static TsExportType classify(Node moduleRoot, String googNamespace) {
    ClosureExportTypeClassifier classifier = new ClosureExportTypeClassifier();
    classifier.addGoogModuleExports();
    forEachTopLevelStatement(moduleRoot, classifier::visitStatement);

    NameInfo info = classifier.topLevelNames.get(EXPORTS);
    if (classifier.forcedNamedExport.contains(googNamespace)
        || classifier.kindOfName(info, 0) == Kind.NAMESPACE) {
      return TsExportType.NAMED;
    }
    return TsExportType.DEFAULT;
  }

  private void addGoogModuleExports() {
    NameInfo info = new NameInfo();
    info.isGoogModuleExports = true;
    topLevelNames.put(EXPORTS, info);
  }

  // ---------------------------------------------------------------------------------------------
  // Gathering facts from top-level statements.
  // ---------------------------------------------------------------------------------------------

  /** Calls {@code callback} on each top-level statement of the module. */
  private static void forEachTopLevelStatement(Node moduleRoot, Consumer<Node> callback) {
    checkArgument(moduleRoot.isScript() && moduleRoot.getFirstChild().isModuleBody(), moduleRoot);
    Node body = moduleRoot.getFirstChild();
    for (Node statement = body.getFirstChild();
        statement != null;
        statement = statement.getNext()) {
      callback.accept(statement);
    }
  }

  private void visitStatement(Node statement) {
    switch (statement.getToken()) {
      case VAR, LET, CONST -> visitNameDeclaration(statement);
      case CLASS, FUNCTION -> {
        Node name = statement.getFirstChild();
        NameInfo info = getOrCreate(name.getString());
        info.isNeverNamespace = true;
      }
      case EXPR_RESULT -> {
        maybeRecordLegacyNamedTsExport(statement);
        visitExpression(statement.getFirstChild());
      }
      default -> {}
    }
  }

  /** Records an explicit @legacyNamedTsExport annotation on a goog.module call statement. */
  private void maybeRecordLegacyNamedTsExport(Node statement) {
    if (!NodeUtil.isGoogModuleCall(statement)) {
      return;
    }
    Node call = statement.getFirstChild();
    JSDocInfo jsdoc = call.getJSDocInfo();
    if (jsdoc == null || !jsdoc.isLegacyNamedTsExport()) {
      return;
    }
    forcedNamedExport.add(call.getSecondChild().getString());
  }

  private void visitNameDeclaration(Node declaration) {
    for (Node lhs = declaration.getFirstChild(); lhs != null; lhs = lhs.getNext()) {
      if (lhs.isName()) {
        visitDeclaredName(lhs);
      }
    }
  }

  private void visitDeclaredName(Node lhs) {
    Node initializer = lhs.getFirstChild();
    NameInfo info = getOrCreate(lhs.getString());
    if (initializer != null && initializer.isAssign()) {
      visitExpression(initializer);
    }
    JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(lhs);
    if (initializer != null) {
      info.value = initializer;
      info.jsdoc = jsdoc;
    } else if (info.value == null && jsdoc != null) {
      // A stub like `/** @type {T} */ let x;`. A bare redeclaration like `var x;` keeps any
      // earlier value.
      info.jsdoc = jsdoc;
    }
  }

  private void visitExpression(Node expr) {
    switch (expr.getToken()) {
      case ASSIGN -> visitAssign(expr);
      case GETPROP -> {
        // Stub declarations like `/** @typedef {number} */ exports.T;`
        if (expr.getJSDocInfo() != null && expr.getFirstChild().isName()) {
          String variableName = expr.getFirstChild().getString();
          getOrCreate(variableName).propertyCount++;
        }
      }
      default -> {}
    }
  }

  private void visitAssign(Node expr) {
    Node lhs = expr.getFirstChild();
    Node rhs = expr.getSecondChild();
    JSDocInfo jsdoc = expr.getJSDocInfo();
    if (lhs.isName()) {
      // `exports = ...;` or `localName = ...;`
      NameInfo assigned = getOrCreate(lhs.getString());
      assigned.value = rhs;
      assigned.jsdoc = jsdoc;
      assigned.isNeverNamespace = false;
      assigned.isNamedExportsLiteral =
          assigned.isGoogModuleExports && NodeUtil.isNamedExportsLiteral(rhs);
      return;
    }
    if (lhs.isGetProp() && lhs.getFirstChild().isName()) {
      // `exports.x = ...;` or `localName.x = ...;`
      getOrCreate(lhs.getFirstChild().getString()).propertyCount++;
    }
  }

  private NameInfo getOrCreate(String name) {
    return topLevelNames.computeIfAbsent(name, (unused) -> new NameInfo());
  }

  // ---------------------------------------------------------------------------------------------
  // Classifying values.
  // ---------------------------------------------------------------------------------------------

  private Kind kindOfName(NameInfo info, int depth) {
    if (info.isNeverNamespace) {
      return Kind.NOT_NAMESPACE;
    }
    if (info.isNamedExportsLiteral) {
      return Kind.NAMESPACE;
    }
    Kind kind;
    if (info.value != null || info.jsdoc != null) {
      kind = kindOfValue(info.value, info.jsdoc, depth);
    } else {
      kind = info.isGoogModuleExports ? Kind.EMPTY_OBJECT : Kind.NOT_NAMESPACE;
    }
    if (kind == Kind.EMPTY_OBJECT && info.propertyCount > 0) {
      return Kind.NAMESPACE;
    }
    return kind;
  }

  private Kind kindOfValue(@Nullable Node value, @Nullable JSDocInfo jsdoc, int depth) {
    if (jsdoc != null) {
      if (jsdoc.hasEnumParameterType()
          || jsdoc.hasTypedefType()
          || jsdoc.isConstructorOrInterface()) {
        return Kind.NOT_NAMESPACE;
      }
      if (jsdoc.hasType()) {
        return kindOfDeclaredType(jsdoc.getType());
      }
    }
    if (value == null) {
      return Kind.NOT_NAMESPACE;
    }
    return switch (value.getToken()) {
      case OBJECTLIT -> {
        // Computed properties and spreads do not create named own properties.
        for (Node member = value.getFirstChild(); member != null; member = member.getNext()) {
          if (!member.isComputedProp() && !member.isSpread()) {
            yield Kind.NAMESPACE;
          }
        }
        yield Kind.EMPTY_OBJECT;
      }
      case NAME -> {
        if (depth >= MAX_ALIAS_DEPTH) {
          yield Kind.NOT_NAMESPACE;
        }
        NameInfo aliased = topLevelNames.get(value.getString());
        yield aliased == null ? Kind.NOT_NAMESPACE : kindOfName(aliased, depth + 1);
      }
      case CAST -> kindOfValue(value.getFirstChild(), value.getJSDocInfo(), depth);
      default ->
          // Classes, functions, calls, `new`, literals, expressions, etc.
          Kind.NOT_NAMESPACE;
    };
  }

  /**
   * Only record type literals like {@code {a: number}} are namespaces. Any named type (e.g. {@code
   * !Object<string, number>} or {@code Foo}) is an instance type or a typedef, which the syntactic
   * analysis cannot resolve, so is not a namespace.
   */
  private static Kind kindOfDeclaredType(JSTypeExpression type) {
    Node root = type.getRoot();
    return root.getToken() == Token.LC ? Kind.NAMESPACE : Kind.NOT_NAMESPACE;
  }
}
