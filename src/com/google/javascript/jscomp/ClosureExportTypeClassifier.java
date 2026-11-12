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

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.TsExportType;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Syntactically classifies whether each goog.provide or goog.module namespace in a file is seen
 * from TypeScript as a set of "named" exports or as a single "default" export.
 *
 * <p>This approximates the type-based logic originally implemented in Clutz. So most of the
 * (potentially surprising) logic is in place to mirror Clutz & allow migration to this syntactic
 * classifier.
 *
 * <p>The core rules:
 *
 * <ul>
 *   <li>Object literals with at least one non-computed, non-spread key are NAMED.
 *   <li>Properties assigned or declared ({@code /** @type {T} *\/ ns.x;}) on the namespace, or on a
 *       local alias of it, make an empty object NAMED.
 *   <li>{@code exports = {a, b};} in a goog.module is NAMED even with a declared type, since module
 *       rewriting turns it into {@code exports.a = a; exports.b = b;} and drops the JSDoc.
 *   <li>A dotted goog.provide namespace assigned without {@code @const} or a type isn't declared by
 *       the typechecker, so it's DEFAULT.
 *   <li>A goog.provide namespace with a nested provide in this file has a property for it.
 * </ul>
 *
 * <p>Besides top-level statements, it only scans the bodies of top-level goog.scope callbacks.
 *
 * <p>Users can make a namespace `named` with {@code @legacyNamedTsExport} on its {@code
 * goog.provide}/{@code goog.module} call. The annotation has no effect elsewhere.
 *
 * <p>This only runs when {@link CompilerOptions#setClassifyTsExportTypes} is enabled.
 */
final class ClosureExportTypeClassifier {

  private static final QualifiedName GOOG_SCOPE = QualifiedName.of("goog.scope");
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

  /** Which kind of top-level name a {@link NameInfo} is about. */
  private enum NameRole {
    /** A goog.module's {@code exports}. */
    GOOG_MODULE_EXPORTS,
    /**
     * A dotted namespace provided by this goog.provide file, e.g. 'a.b' but not 'a'. goog.provide
     * implicitly initializes it to {}. An explicit assignment to one only counts with declaring
     * JSDoc: JSCompiler doesn't declare {@code a.b = {x: 1};} (no {@code @const} or type).
     */
    DOTTED_PROVIDE,
    /**
     * An undotted namespace provided by this goog.provide file, e.g. 'a'. goog.provide implicitly
     * initializes it to {}.
     */
    UNDOTTED_PROVIDE,
    /** A local variable. */
    LOCAL
  }

  /** Facts gathered about a top-level name: {@code exports}, a provide, or a local variable. */
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

    /** Whether this name is a class or function declaration annotated {@code @nodts}. */
    boolean isNoDtsDeclaration;

    /** Number of properties assigned or declared on this name ({@code name.x = ...;}). */
    int propertyCount;

    final NameRole role;

    /** Whether this is a namespace provided by this goog.provide file. */
    boolean isProvide() {
      return role == NameRole.DOTTED_PROVIDE || role == NameRole.UNDOTTED_PROVIDE;
    }

    /**
     * Whether {@link #value} is assigned as {@code exports = {a, b};} in a goog.module.
     * JSCompiler's module rewriting turns that into {@code exports.a = a; exports.b = b;} (see
     * {@link NodeUtil#isNamedExportsLiteral}), dropping any JSDoc on the assignment, so it's always
     * NAMED.
     */
    boolean isNamedExportsLiteral;

    private NameInfo(NameRole role) {
      this.role = role;
    }
  }

  private final Map<String, NameInfo> topLevelNames = new LinkedHashMap<>();

  /**
   * Provided namespaces that are not simple names, e.g. 'a.b.c' but not 'a', keyed by their last
   * segment ('c').
   *
   * <p>This index is just for performance: on any GETPROP node, {@code n.getString()} gives the
   * last property name without allocating a new String. If that name isn't in this map, we
   * immediately know the node is not one of our provided namespaces, avoiding both heap allocations
   * from {@code n.getQualifiedName()} and O(N_provides) scans via {@code
   * n.matchesQualifiedName(...)} on every property assignment in files with many provides (e.g.
   * generated i18n data).
   */
  private final Map<String, List<String>> dottedProvidesByLastSegment = new LinkedHashMap<>();

  /**
   * Local names aliasing a provided namespace or a strict prefix of a dotted provide, mapped to the
   * aliased qualified name, e.g. {@code const xhr = goog.labs.net.xhr;}, {@code const ns =
   * confusion;}, or {@code const alignment = aplos.data.alignment;} for a provide
   * 'aplos.data.alignment.Aligner', usually inside goog.scope. Qualified names rooted at an alias
   * resolve to the aliased name, so properties assigned via an alias count as properties of the
   * provide.
   */
  private final Map<String, String> aliases = new LinkedHashMap<>();

  /**
   * All strict prefixes of the dotted provides, e.g. 'a' and 'a.b' for 'a.b.c', to find {@link
   * #aliases} with a single lookup.
   */
  private final Set<String> dottedProvidePrefixes = new LinkedHashSet<>();

  /**
   * Namespaces whose goog.provide or goog.module call is annotated {@code @legacyNamedTsExport}.
   */
  private final Set<String> forcedNamedExport = new LinkedHashSet<>();

  /**
   * Classifies each of {@code googNamespaces} as named, default, or {@code @nodts}.
   *
   * @param moduleRoot the SCRIPT node for this goog.provide or goog.module file
   * @param googNamespaces the namespaces provided by this file
   * @return the export type of each of {@code googNamespaces}, in the same order
   */
  static ImmutableMap<String, TsExportType> classify(Node moduleRoot, Set<String> googNamespaces) {
    if (googNamespaces.isEmpty()) {
      return ImmutableMap.of();
    }
    boolean isGoogModule = NodeUtil.isGoogModuleFile(moduleRoot);
    ClosureExportTypeClassifier classifier = new ClosureExportTypeClassifier();
    if (isGoogModule) {
      classifier.addGoogModuleExports();
    } else {
      for (String namespace : googNamespaces) {
        classifier.addProvide(namespace);
      }
    }
    classifier.forEachTopLevelStatement(moduleRoot, isGoogModule, classifier::visitStatement);
    if (!isGoogModule && googNamespaces.size() > 1) {
      classifier.addImplicitPropertiesFromNestedProvides(googNamespaces);
    }

    ImmutableMap.Builder<String, TsExportType> exportTypes = ImmutableMap.builder();
    for (String namespace : googNamespaces) {
      NameInfo info = classifier.topLevelNames.get(isGoogModule ? EXPORTS : namespace);
      if ((info.jsdoc != null && info.jsdoc.isNoDts()) || classifier.hasNoDtsType(info, 0)) {
        exportTypes.put(namespace, TsExportType.NO_DTS);
        continue;
      }

      boolean isNamed =
          classifier.forcedNamedExport.contains(namespace)
              || classifier.kindOfName(info, 0) == Kind.NAMESPACE;
      exportTypes.put(namespace, isNamed ? TsExportType.NAMED : TsExportType.DEFAULT);
    }
    return exportTypes.buildOrThrow();
  }

  private void addGoogModuleExports() {
    NameInfo info = new NameInfo(NameRole.GOOG_MODULE_EXPORTS);
    topLevelNames.put(EXPORTS, info);
  }

  private void addProvide(String namespace) {
    int lastDot = namespace.lastIndexOf('.');
    var role = lastDot != -1 ? NameRole.DOTTED_PROVIDE : NameRole.UNDOTTED_PROVIDE;
    NameInfo info = new NameInfo(role);
    topLevelNames.put(namespace, info);
    if (lastDot != -1) {
      // Index by last segment (e.g. 'c' for 'a.b.c') so getInfoForQualifiedName can quickly
      // filter GETPROP nodes via n.getString() without allocating strings.
      dottedProvidesByLastSegment
          .computeIfAbsent(namespace.substring(lastDot + 1), unused -> new ArrayList<>(1))
          .add(namespace);
      for (int dot = lastDot; dot != -1; dot = namespace.lastIndexOf('.', dot - 1)) {
        dottedProvidePrefixes.add(namespace.substring(0, dot));
      }
    }
  }

  /**
   * goog.provide('a.b'); goog.provide('a.b.c'); implicitly creates a property 'c' on 'a.b'. This
   * cannot see nested provides in other files.
   */
  private void addImplicitPropertiesFromNestedProvides(Set<String> provides) {
    // In sorted order, any namespace nested under 'a.b' sorts in ['a.b.', 'a.b/'), since '/' is
    // the character after '.'.
    TreeSet<String> sorted = new TreeSet<>(provides);
    for (String namespace : provides) {
      int nestedCount = sorted.subSet(namespace + ".", namespace + "/").size();
      if (nestedCount > 0) {
        topLevelNames.get(namespace).propertyCount += nestedCount;
      }
    }
  }

  /**
   * Whether the value held by {@code info} has a type annotated {@code @nodts}: Clutz's {@code
   * ClosureTypes.isNoDts} checks the JSDoc of the exported symbol's type, which is the JSDoc of the
   * class, function, or object literal declaration that created it.
   */
  private boolean hasNoDtsType(NameInfo info, int depth) {
    if (info.isNoDtsDeclaration) {
      return true; // `/** @nodts */ class C {}`
    }
    if (info.isNeverNamespace) {
      return false;
    }
    Node value = info.value;
    if (value == null) {
      return false;
    }
    if (info.jsdoc != null
        && info.jsdoc.isNoDts()
        && (value.isObjectLit() || value.isClass() || value.isFunction())) {
      return true; // `/** @nodts */ const C = class {};` or `/** @nodts */ const x = {...};`
    }
    if (value.isName() && depth < MAX_ALIAS_DEPTH) {
      NameInfo aliased = topLevelNames.get(value.getString());
      return aliased != null && hasNoDtsType(aliased, depth + 1);
    }
    return false;
  }

  // ---------------------------------------------------------------------------------------------
  // Gathering facts from top-level statements.
  // ---------------------------------------------------------------------------------------------

  /**
   * Calls {@code callback} on each top-level statement of the file, including statements directly
   * inside a top-level goog.scope callback.
   */
  private void forEachTopLevelStatement(
      Node moduleRoot, boolean isGoogModule, Consumer<Node> callback) {
    checkArgument(moduleRoot.isScript(), moduleRoot);
    // A goog.provide file is a script, so its statements are directly under the SCRIPT.
    checkArgument(
        isGoogModule || !moduleRoot.hasChildren() || !moduleRoot.getFirstChild().isModuleBody(),
        moduleRoot);
    Node body = isGoogModule ? moduleRoot.getFirstChild() : moduleRoot;
    for (Node statement = body.getFirstChild();
        statement != null;
        statement = statement.getNext()) {
      Node scopeBody = getGoogScopeBody(statement);
      if (scopeBody != null) {
        Map<String, String> savedAliases = aliases.isEmpty() ? null : new LinkedHashMap<>(aliases);
        for (Node inner = scopeBody.getFirstChild(); inner != null; inner = inner.getNext()) {
          callback.accept(inner);
        }
        aliases.clear();
        if (savedAliases != null) {
          aliases.putAll(savedAliases);
        }
      } else {
        callback.accept(statement);
      }
    }
  }

  /** Returns the body of the callback of a {@code goog.scope(function() { ... });} statement. */
  private @Nullable Node getGoogScopeBody(Node statement) {
    if (!NodeUtil.isExprCall(statement)) {
      return null;
    }
    Node call = statement.getFirstChild();
    if (!GOOG_SCOPE.matches(call.getFirstChild())) {
      return null;
    }
    Node fn = call.getSecondChild();
    if (fn != null && fn.isFunction() && !fn.isGeneratorFunction() && !fn.isAsyncFunction()) {
      Node body = NodeUtil.getFunctionBody(fn);
      if (body.isBlock()) { // ignore invalid code passing an arrow function
        return body;
      }
    }
    return null;
  }

  private void visitStatement(Node statement) {
    switch (statement.getToken()) {
      case VAR, LET, CONST -> visitNameDeclaration(statement);
      case CLASS, FUNCTION -> {
        Node name = statement.getFirstChild();
        NameInfo info = getOrCreate(name.getString());
        info.isNeverNamespace = true;
        JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(statement);
        info.isNoDtsDeclaration = jsdoc != null && jsdoc.isNoDts();
      }
      case EXPR_RESULT -> {
        maybeRecordLegacyNamedTsExport(statement);
        visitExpression(statement.getFirstChild());
      }
      default -> {}
    }
  }

  /**
   * Records an explicit @legacyNamedTsExport annotation on a goog.provide or goog.module call
   * statement.
   */
  private void maybeRecordLegacyNamedTsExport(Node statement) {
    if (!NodeUtil.isGoogModuleCall(statement) && !NodeUtil.isGoogProvideCall(statement)) {
      return;
    }
    Node call = statement.getFirstChild();
    JSDocInfo jsdoc = call.getJSDocInfo();
    if (jsdoc == null || !jsdoc.isLegacyNamedTsExport()) {
      return;
    }
    Node arg = call.getSecondChild();
    if (arg == null || !arg.isStringLit()) {
      return;
    }
    forcedNamedExport.add(arg.getString());
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
    if (initializer != null) {
      maybeRecordAlias(lhs.getString(), initializer);
    }
    NameInfo info = getOrCreate(lhs.getString());
    if (initializer != null && initializer.isAssign()) {
      // e.g. `const fromJson = a.b.fromJson = function() {};`
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

  /**
   * Records {@code name} in {@link #aliases} if {@code initializer} is a qualified name that is a
   * provided namespace or a strict prefix of a dotted provide; or an alias thereof.
   *
   * <p>For example, these are all treated as aliases: {@code const ns = confusion;}, {@code const a
   * = aplos;}, or {@code const alignment = aplos.data.alignment;}.
   */
  private void maybeRecordAlias(String name, Node initializer) {
    String target = null;
    if (initializer.isName()) {
      target = aliases.getOrDefault(initializer.getString(), initializer.getString());
    } else if (!dottedProvidePrefixes.isEmpty()) {
      // Only dotted provides and their prefixes can match, so skip files without any like
      // `goog.module` files.
      target = resolveAliasedQualifiedName(initializer);
      if (target == null) {
        target = initializer.getQualifiedName();
      }
    }
    if (target != null
        && (dottedProvidePrefixes.contains(target) || getProvideInfo(target) != null)) {
      aliases.put(name, target);
    } else {
      aliases.remove(name);
    }
  }

  private void visitExpression(Node expr) {
    switch (expr.getToken()) {
      case ASSIGN -> visitAssign(expr);
      case GETPROP -> {
        // Stub declarations like `/** @typedef {number} */ exports.T;`
        NameInfo declared = getInfoForQualifiedName(expr);
        JSDocInfo jsdoc = expr.getJSDocInfo();
        if (declared != null) {
          // Only a @typedef stub (before any assignment) suppresses goog.provide's implicit `{}`.
          if (declared.value == null && jsdoc != null && jsdoc.hasTypedefType()) {
            declared.isNeverNamespace = true;
          }
          return;
        }
        if (jsdoc != null
            && (jsdoc.hasType()
                || isDefiningType(jsdoc)
                || NodeUtil.isFunctionTypeDeclaration(jsdoc))) {
          recordProperty(expr.getFirstChild());
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
      aliases.remove(lhs.getString());
    }
    NameInfo assigned = getInfoForQualifiedName(lhs);
    if (assigned != null) {
      // `exports = ...;`, `a.b.Provide = ...;`, or `localName = ...;`
      assigned.value = rhs;
      assigned.jsdoc = jsdoc;
      assigned.isNeverNamespace = false;
      assigned.isNoDtsDeclaration = false;
      assigned.isNamedExportsLiteral =
          assigned.role == NameRole.GOOG_MODULE_EXPORTS && NodeUtil.isNamedExportsLiteral(rhs);
      return;
    }
    if (lhs.isGetProp()) {
      // `exports.x = ...;`, `a.b.Provide.x = ...;`, or `localName.x = ...;`
      recordProperty(lhs.getFirstChild());
    }
  }

  private void recordProperty(Node owner) {
    NameInfo ownerInfo = null;
    if (owner.isName() && aliases.containsKey(owner.getString())) {
      // `alias.x = ...;` where the alias is of a provide.
      ownerInfo = getProvideInfo(aliases.get(owner.getString()));
    }
    if (ownerInfo == null) {
      ownerInfo = getInfoForQualifiedName(owner);
    }
    if (ownerInfo != null) {
      ownerInfo.propertyCount++;
    }
  }

  /**
   * Returns the info for the given node if it is a simple name or a provided namespace (possibly
   * via an alias, e.g. {@code alignment.Aligner}), or null.
   *
   * <p>Avoids allocating qualified name strings (via {@code n.getQualifiedName()}) by first
   * checking the unallocated last-segment string ({@code n.getString()}) against {@link
   * #dottedProvidesByLastSegment}, and only then testing candidates with zero-allocation {@code
   * n.matchesQualifiedName(provide)}.
   */
  private @Nullable NameInfo getInfoForQualifiedName(Node n) {
    if (n.isName()) {
      return getOrCreate(n.getString());
    }
    if (!n.isGetProp() || dottedProvidesByLastSegment.isEmpty()) {
      return null;
    }
    List<String> candidates = dottedProvidesByLastSegment.get(n.getString());
    if (candidates == null) {
      return null;
    }
    String resolved = resolveAliasedQualifiedName(n);
    if (resolved != null) {
      return getProvideInfo(resolved);
    }
    for (String provide : candidates) {
      if (n.matchesQualifiedName(provide)) {
        return topLevelNames.get(provide);
      }
    }
    return null;
  }

  /**
   * Returns the qualified name of {@code n} with its root replaced by the aliased name, e.g.
   * 'aplos.data.Aligner' for {@code a.data.Aligner} after {@code const a = aplos;}, or null if
   * {@code n} isn't a qualified name rooted at an alias.
   */
  @SuppressWarnings("ReferenceEquality") // Rhino AST is designed to use reference equality.
  private @Nullable String resolveAliasedQualifiedName(Node n) {
    if (aliases.isEmpty() || !n.isQualifiedName()) {
      return null;
    }
    Node root = NodeUtil.getRootOfQualifiedName(n);
    String target = root.isName() ? aliases.get(root.getString()) : null;
    if (target == null || n == root) {
      return target;
    }
    return target + n.getQualifiedName().substring(root.getString().length());
  }

  /** Returns the info for {@code qname} if it is provided in this file, or null. */
  private @Nullable NameInfo getProvideInfo(String qname) {
    NameInfo info = topLevelNames.get(qname);
    return info != null && info.isProvide() ? info : null;
  }

  private NameInfo getOrCreate(String name) {
    return topLevelNames.computeIfAbsent(name, unused -> new NameInfo(NameRole.LOCAL));
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
    if (info.role == NameRole.DOTTED_PROVIDE
        && info.value != null
        && !isDeclaringJsDoc(info.jsdoc)) {
      return Kind.NOT_NAMESPACE;
    }
    Kind kind;
    if (info.value != null || info.jsdoc != null) {
      kind = kindOfValue(info.value, info.jsdoc, depth);
    } else {
      // goog.module and goog.provide implicitly initialize exports and provides to {}.
      kind = info.role != NameRole.LOCAL ? Kind.EMPTY_OBJECT : Kind.NOT_NAMESPACE;
    }
    if (kind == Kind.EMPTY_OBJECT && info.propertyCount > 0) {
      return Kind.NAMESPACE;
    }
    return kind;
  }

  private static boolean isDeclaringJsDoc(@Nullable JSDocInfo jsdoc) {
    return isDefiningType(jsdoc) || (jsdoc != null && (jsdoc.isConstant() || jsdoc.hasType()));
  }

  private static boolean isDefiningType(@Nullable JSDocInfo jsdoc) {
    // Anything defining a new type (i.e. some name that can be referred to in a type annotation)
    // is never a namespace.
    return jsdoc != null
        && (jsdoc.hasEnumParameterType()
            || jsdoc.hasTypedefType()
            || jsdoc.isConstructorOrInterface());
  }

  private Kind kindOfValue(@Nullable Node value, @Nullable JSDocInfo jsdoc, int depth) {
    if (jsdoc != null) {
      if (isDefiningType(jsdoc)) {
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
