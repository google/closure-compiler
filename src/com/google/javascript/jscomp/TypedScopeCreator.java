/*
 * Copyright 2004 The Closure Compiler Authors.
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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.TypeCheck.MULTIPLE_VAR_DEF;
import static com.google.javascript.rhino.jstype.JSTypeNative.ARRAY_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.ARRAY_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_OBJECT_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.DATE_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.FUNCTION_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.GENERATOR_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.GLOBAL_THIS;
import static com.google.javascript.rhino.jstype.JSTypeNative.ITERABLE_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.ITERATOR_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NO_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NULL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_OBJECT_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.REGEXP_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.REGEXP_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_OBJECT_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.U2U_CONSTRUCTOR_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.VOID_TYPE;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.Table;
import com.google.javascript.jscomp.CodingConvention.DelegateRelationship;
import com.google.javascript.jscomp.CodingConvention.ObjectLiteralCast;
import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.jscomp.FunctionTypeBuilder.AstFunctionContents;
import com.google.javascript.jscomp.NodeTraversal.AbstractScopedCallback;
import com.google.javascript.jscomp.NodeTraversal.AbstractShallowStatementCallback;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.NominalTypeBuilder;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.StaticSymbolTable;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.EnumType;
import com.google.javascript.rhino.jstype.FunctionParamBuilder;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.NominalTypeBuilderOti;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.Property;
import com.google.javascript.rhino.jstype.StaticTypedScope;
import com.google.javascript.rhino.jstype.TemplateType;
import com.google.javascript.rhino.jstype.TemplateTypeMap;
import com.google.javascript.rhino.jstype.TemplateTypeMapReplacer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * Creates the symbol table of variables available in the current scope and
 * their types.
 *
 * Scopes created by this class are very different from scopes created
 * by the syntactic scope creator. These scopes have type information, and
 * include some qualified names in addition to variables
 * (like Class.staticMethod).
 *
 * When building scope information, also declares relevant information
 * about types in the type registry.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
final class TypedScopeCreator implements ScopeCreator, StaticSymbolTable<TypedVar, TypedVar> {
  /**
   * A suffix for naming delegate proxies differently from their base.
   */
  static final String DELEGATE_PROXY_SUFFIX =
      ObjectType.createDelegateSuffix("Proxy");

  static final DiagnosticType MALFORMED_TYPEDEF =
      DiagnosticType.warning(
          "JSC_MALFORMED_TYPEDEF",
          "Typedef for {0} does not have any type information");

  static final DiagnosticType ENUM_INITIALIZER =
      DiagnosticType.warning(
          "JSC_ENUM_INITIALIZER_NOT_ENUM",
          "enum initializer must be an object literal or an enum");

  static final DiagnosticType INVALID_ENUM_KEY =
      DiagnosticType.warning(
          "JSC_INVALID_ENUM_KEY", "enum key must be a string or numeric literal");

  static final DiagnosticType CTOR_INITIALIZER =
      DiagnosticType.warning(
          "JSC_CTOR_INITIALIZER_NOT_CTOR",
          "Constructor {0} must be initialized at declaration");

  static final DiagnosticType IFACE_INITIALIZER =
      DiagnosticType.warning(
          "JSC_IFACE_INITIALIZER_NOT_IFACE",
          "Interface {0} must be initialized at declaration");

  static final DiagnosticType CONSTRUCTOR_EXPECTED =
      DiagnosticType.warning(
          "JSC_REFLECT_CONSTRUCTOR_EXPECTED",
          "Constructor expected as first argument");

  static final DiagnosticType UNKNOWN_LENDS =
      DiagnosticType.warning(
          "JSC_UNKNOWN_LENDS",
          "Variable {0} not declared before @lends annotation.");

  static final DiagnosticType LENDS_ON_NON_OBJECT =
      DiagnosticType.warning(
          "JSC_LENDS_ON_NON_OBJECT",
          "May only lend properties to object types. {0} has type {1}.");

  static final DiagnosticType INCOMPATIBLE_ALIAS_ANNOTATION =
      DiagnosticType.warning(
          "JSC_INCOMPATIBLE_ALIAS_ANNOTATION",
          "Annotation {0} on {1} incompatible with aliased type.");

  static final DiagnosticType DYNAMIC_EXTENDS_WITHOUT_JSDOC =
      DiagnosticType.warning(
          "JSC_DYNAMIC_EXTENDS_WITHOUT_JSDOC",
          "The right-hand side of an extends clause must be a qualified name, or else @extends must"
              + " be specified in JSDoc");

  static final DiagnosticType CONFLICTING_GETTER_SETTER_TYPE =
      DiagnosticType.warning(
          "JSC_CONFLICTING_GETTER_SETTER_TYPE",
          "The types of the getter and setter for property ''{0}'' do not match.\n"
              + "getter type is: {1}\n"
              + "setter type is: {2}");

  static final DiagnosticGroup ALL_DIAGNOSTICS = new DiagnosticGroup(
      DELEGATE_PROXY_SUFFIX,
      MALFORMED_TYPEDEF,
      ENUM_INITIALIZER,
      CTOR_INITIALIZER,
      IFACE_INITIALIZER,
      CONSTRUCTOR_EXPECTED,
      UNKNOWN_LENDS,
      LENDS_ON_NON_OBJECT,
      INCOMPATIBLE_ALIAS_ANNOTATION,
      DYNAMIC_EXTENDS_WITHOUT_JSDOC);

  private final AbstractCompiler compiler;
  private final ErrorReporter typeParsingErrorReporter;
  private final TypeValidator validator;
  private final CodingConvention codingConvention;
  private final JSTypeRegistry typeRegistry;
  private final List<FunctionType> delegateProxyCtors = new ArrayList<>();
  private final Map<String, String> delegateCallingConventions = new HashMap<>();
  private final Map<Node, TypedScope> memoized = new LinkedHashMap<>();

  // Set of functions with non-empty returns, for passing to FunctionTypeBuilder.
  private final Set<Node> functionsWithNonEmptyReturns = new HashSet<>();
  // Includes both simple and qualified names.
  private final Set<ScopedName> escapedVarNames = new HashSet<>();
  // Count of how many times each variable is assigned, for marking effectively final.
  private final Multiset<ScopedName> assignedVarNames = HashMultiset.create();

  // For convenience
  private final ObjectType unknownType;

  private final List<DeferredSetType> deferredSetTypes = new ArrayList<>();

  /**
   * Defer attachment of types to nodes until all type names
   * have been resolved. Then, we can resolve the type and attach it.
   */
  private class DeferredSetType {
    final Node node;
    final JSType type;

    DeferredSetType(Node node, JSType type) {
      checkNotNull(node);
      checkNotNull(type);
      this.node = node;
      this.type = type;
    }

    void resolve() {
      node.setJSType(type.resolve(typeParsingErrorReporter));
    }
  }

  /** Stores the type and qualified name for a destructuring rvalue. */
  private static class RValueInfo {
    @Nullable final JSType type;
    @Nullable final QualifiedName qualifiedName;

    RValueInfo(JSType type, QualifiedName qualifiedName) {
      this.type = type;
      this.qualifiedName = qualifiedName;
    }

    static RValueInfo empty() {
      return new RValueInfo(null, null);
    }
  }

  TypedScopeCreator(AbstractCompiler compiler) {
    this(compiler, compiler.getCodingConvention());
  }

  TypedScopeCreator(AbstractCompiler compiler, CodingConvention codingConvention) {
    this.compiler = compiler;
    this.validator = compiler.getTypeValidator();
    this.codingConvention = codingConvention;
    this.typeRegistry = compiler.getTypeRegistry();
    this.typeParsingErrorReporter = typeRegistry.getErrorReporter();
    this.unknownType = typeRegistry.getNativeObjectType(UNKNOWN_TYPE);
  }

  private void report(JSError error) {
    compiler.report(error);
  }

  @Override
  public ImmutableList<TypedVar> getReferences(TypedVar var) {
    return ImmutableList.of(var);
  }

  @Override
  public TypedScope getScope(TypedVar var) {
    return var.scope;
  }

  @Override
  public Iterable<TypedVar> getAllSymbols() {
    List<TypedVar> vars = new ArrayList<>();
    for (TypedScope s : memoized.values()) {
      Iterables.addAll(vars, s.getAllSymbols());
    }
    return vars;
  }

  Collection<TypedScope> getAllMemoizedScopes() {
    // Return scopes in reverse order of creation so that IIFEs will
    // come before the global scope.
    return Lists.reverse(ImmutableList.copyOf(memoized.values()));
  }

  /**
   * Removes all scopes with root nodes from a given script file.
   *
   * @param scriptName the name of the script file to remove nodes for.
   */
  void removeScopesForScript(String scriptName) {
    memoized.keySet().removeIf(n -> scriptName.equals(NodeUtil.getSourceName(n)));
  }

  /** Create a scope if it doesn't already exist, looking up in the map for the parent scope. */
  TypedScope createScope(Node n) {
    TypedScope s = memoized.get(n);
    return s != null
        ? s
        : createScope(n, createScope(NodeUtil.getEnclosingScopeRoot(n.getParent())));
  }

  /**
   * Creates a scope with all types declared. Declares newly discovered types
   * and type properties in the type registry.
   */
  @Override
  public TypedScope createScope(Node root, AbstractScope<?, ?> parent) {
    checkArgument(parent == null || parent instanceof TypedScope);
    TypedScope typedParent = (TypedScope) parent;

    TypedScope scope = memoized.get(root);
    if (scope != null) {
      checkState(typedParent == scope.getParent());
    } else {
      scope = createScopeInternal(root, typedParent);
      memoized.put(root, scope);
    }
    return scope;
  }

  private TypedScope createScopeInternal(Node root, TypedScope typedParent) {
    // Constructing the global scope is very different than constructing
    // inner scopes, because only global scopes can contain named classes that
    // show up in the type registry.
    TypedScope newScope = null;

    AbstractScopeBuilder scopeBuilder = null;
    if (typedParent == null) {
      JSType globalThis =
          typeRegistry.getNativeObjectType(JSTypeNative.GLOBAL_THIS);

      // Mark the main root, the externs root, and the src root
      // with the global this type.
      root.setJSType(globalThis);
      root.getFirstChild().setJSType(globalThis);
      root.getLastChild().setJSType(globalThis);

      // Run a first-order analysis over the syntax tree.
      new FirstOrderFunctionAnalyzer().process(root.getFirstChild(), root.getLastChild());

      // Find all the classes in the global scope.
      newScope = createInitialScope(root);
    } else {
      newScope = new TypedScope(typedParent, root);
    }
    if (root.isFunction()) {
      scopeBuilder = new FunctionScopeBuilder(newScope);
    } else if (root.isClass()) {
      scopeBuilder = new ClassScopeBuilder(newScope);
    } else {
      scopeBuilder = new NormalScopeBuilder(newScope);
    }
    scopeBuilder.build();

    if (typedParent == null) {
      List<NominalTypeBuilder> delegateProxies = new ArrayList<>();
      for (FunctionType delegateProxyCtor : delegateProxyCtors) {
        delegateProxies.add(
            new NominalTypeBuilderOti(delegateProxyCtor, delegateProxyCtor.getInstanceType()));
      }
      codingConvention.defineDelegateProxyPrototypeProperties(
          typeRegistry, delegateProxies, delegateCallingConventions);
    }

    return newScope;
  }

  /**
   * Patches a given global scope by removing variables previously declared in
   * a script and re-traversing a new version of that script.
   *
   * @param globalScope The global scope generated by {@code createScope}.
   * @param scriptRoot The script that is modified.
   */
  void patchGlobalScope(TypedScope globalScope, Node scriptRoot) {
    // Preconditions: This is supposed to be called only on (named) SCRIPT nodes
    // and a global typed scope should have been generated already.
    checkState(scriptRoot.isScript());
    checkNotNull(globalScope);
    checkState(globalScope.isGlobal());

    String scriptName = NodeUtil.getSourceName(scriptRoot);
    checkNotNull(scriptName);

    Predicate<Node> inScript = n -> scriptName.equals(NodeUtil.getSourceName(n));
    escapedVarNames.removeIf(var -> inScript.test(var.getScopeRoot()));
    assignedVarNames.removeIf(var -> inScript.test(var.getScopeRoot()));
    functionsWithNonEmptyReturns.removeIf(inScript);

    new FirstOrderFunctionAnalyzer().process(null, scriptRoot);

    // TODO(bashir): Variable declaration is not the only side effect of last
    // global scope generation but here we only wipe that part off.

    // Remove all variables that were previously declared in this scripts.
    // First find all vars to remove then remove them because of iterator.
    List<TypedVar> varsToRemove = new ArrayList<>();
    for (TypedVar oldVar : globalScope.getVarIterable()) {
      if (scriptName.equals(oldVar.getInputName())) {
        varsToRemove.add(oldVar);
      }
    }
    for (TypedVar var : varsToRemove) {
      // By removing the type here, we're potentially invalidating any files that contain
      // references to this type. Those files will need to be recompiled. Ideally, this
      // was handled by the compiler (see b/29121507), but in the meantime users of incremental
      // compilation will need to manage it themselves (e.g., by recompiling dependent files
      // based on the dep graph).
      String typeName = var.getName();
      globalScope.undeclare(var);
      globalScope.getTypeOfThis().toObjectType().removeProperty(typeName);
      if (typeRegistry.getType(globalScope, typeName) != null) {
        typeRegistry.removeType(globalScope, typeName);
      }
    }

    // Now re-traverse the given script.
    NormalScopeBuilder scopeBuilder = new NormalScopeBuilder(globalScope);
    NodeTraversal.traverse(compiler, scriptRoot, scopeBuilder);
  }

  /**
   * Create the outermost scope. This scope contains native binding such as
   * {@code Object}, {@code Date}, etc.
   */
  @VisibleForTesting
  TypedScope createInitialScope(Node root) {

    NodeTraversal.traverse(
        compiler, root, new IdentifyGlobalEnumsAndTypedefsAsNonNullable(typeRegistry));

    TypedScope s = TypedScope.createGlobalScope(root);
    declareNativeFunctionType(s, ARRAY_FUNCTION_TYPE);
    declareNativeFunctionType(s, BOOLEAN_OBJECT_FUNCTION_TYPE);
    declareNativeFunctionType(s, DATE_FUNCTION_TYPE);
    declareNativeFunctionType(s, FUNCTION_FUNCTION_TYPE);
    declareNativeFunctionType(s, GENERATOR_FUNCTION_TYPE);
    declareNativeFunctionType(s, ITERABLE_FUNCTION_TYPE);
    declareNativeFunctionType(s, ITERATOR_FUNCTION_TYPE);
    declareNativeFunctionType(s, NUMBER_OBJECT_FUNCTION_TYPE);
    declareNativeFunctionType(s, OBJECT_FUNCTION_TYPE);
    declareNativeFunctionType(s, REGEXP_FUNCTION_TYPE);
    declareNativeFunctionType(s, STRING_OBJECT_FUNCTION_TYPE);
    declareNativeValueType(s, "undefined", VOID_TYPE);

    return s;
  }

  private void declareNativeFunctionType(TypedScope scope, JSTypeNative tId) {
    FunctionType t = typeRegistry.getNativeFunctionType(tId);
    declareNativeType(scope, t.getInstanceType().getReferenceName(), t);
    declareNativeType(
        scope, t.getPrototype().getReferenceName(), t.getPrototype());
  }

  private void declareNativeValueType(TypedScope scope, String name,
      JSTypeNative tId) {
    declareNativeType(scope, name, typeRegistry.getNativeType(tId));
  }

  private static void declareNativeType(TypedScope scope, String name, JSType t) {
    scope.declare(name, null, t, null, false);
  }

  /** Set the type for a node now, and enqueue it to be updated with a resolved type later. */
  void setDeferredType(Node node, JSType type) {
    // Other parts of this pass may read the not-yet-resolved type off the node.
    // (like when we set the LHS of an assign with a typed RHS function.)
    node.setJSType(type);
    deferredSetTypes.add(new DeferredSetType(node, type));
  }

  void resolveTypes() {
    // Resolve types and attach them to nodes.
    for (DeferredSetType deferred : deferredSetTypes) {
      deferred.resolve();
    }

    // Resolve types and attach them to scope slots.
    for (TypedScope scope : getAllMemoizedScopes()) {
      for (TypedVar var : scope.getVarIterable()) {
        var.resolveType(typeParsingErrorReporter);
      }
    }

    // Tell the type registry that any remaining types are unknown.
    typeRegistry.resolveTypes();
  }

  /**
   * Adds all globally-defined enums and typedefs to the registry's list of non-nullable types.
   *
   * TODO(bradfordcsmith): We should also make locally-defined enums and typedefs non-nullable.
   */
  private static class IdentifyGlobalEnumsAndTypedefsAsNonNullable
      extends AbstractShallowStatementCallback {
    private final JSTypeRegistry registry;

    IdentifyGlobalEnumsAndTypedefsAsNonNullable(JSTypeRegistry registry) {
      this.registry = registry;
    }

    @Override
    public void visit(NodeTraversal t, Node node, Node parent) {
      switch (node.getToken()) {
        case LET:
        case CONST:
        case VAR:
          // Note that this class expects to be invoked on the root node and does not traverse into
          // functions.
          Scope scope = t.getScope();
          checkState(scope.isGlobal() || scope.getClosestHoistScope().isGlobal());
          if (node.isVar() || scope.isGlobal()) {
            for (Node child = node.getFirstChild();
                 child != null; child = child.getNext()) {
              identifyNameNode(child, NodeUtil.getBestJSDocInfo(child));
            }
          }
          break;
        case EXPR_RESULT:
          Node firstChild = node.getFirstChild();
          if (firstChild.isAssign()) {
            identifyNameNode(
                firstChild.getFirstChild(), firstChild.getJSDocInfo());
          } else {
            identifyNameNode(
                firstChild, firstChild.getJSDocInfo());
          }
          break;
        default:
          break;
      }
    }

    private void identifyNameNode(
        Node nameNode, JSDocInfo info) {
      if (info != null && nameNode.isQualifiedName()) {
        if (info.hasEnumParameterType()) {
          registry.identifyNonNullableName(nameNode.getQualifiedName());
        } else if (info.hasTypedefType()) {
          registry.identifyNonNullableName(nameNode.getQualifiedName());
        }
      }
    }
  }

  private JSType getNativeType(JSTypeNative nativeType) {
    return typeRegistry.getNativeType(nativeType);
  }

  private abstract class AbstractScopeBuilder implements NodeTraversal.Callback {

    /** The scope that we're building. */
    final TypedScope currentScope;

    /** The current hoist scope. */
    final TypedScope currentHoistScope;

    /** The current source file that we're in. */
    private String sourceName = null;

    /** The InputId of the current node. */
    private InputId inputId;

    /**
     * Some actions need to be deferred, such as analyzing object literals with
     * lends annotations, or resolving type-less stubs.  These actions are added
     * to this map, keyed by the node that should be waited for before running.
     */
    final Multimap<Node, Runnable> deferredActions = HashMultimap.create();

    AbstractScopeBuilder(TypedScope scope) {
      this.currentScope = scope;
      this.currentHoistScope = scope.getClosestHoistScope();
    }

    /** Returns the current compiler input. */
    CompilerInput getCompilerInput() {
      return compiler.getInput(inputId);
    }

    /** Traverse the scope root and build it. */
    void build() {
      new NodeTraversal(compiler, this, ScopeCreator.ASSERT_NO_SCOPES_CREATED)
          .traverseAtScope(currentScope);
    }

    @Override
    public final boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      inputId = t.getInputId();
      if (n.isFunction() || n.isScript() || (parent == null && inputId != null)) {
        checkNotNull(inputId);
        sourceName = NodeUtil.getSourceName(n);
      }
      if (parent == null || inCurrentScope(t)) {
        visitPreorder(t, n, parent);
        return true;
      }
      return false;
    }

    private boolean inCurrentScope(NodeTraversal t) {
      Node traversalScopeRoot = t.getScopeRoot();
      // NOTE: we need special handling for SCRIPT nodes, since Compiler.replaceScript causes a
      // traversal rooted at a SCRIPT but with the global scope whose root node is the ROOT.
      if (traversalScopeRoot.isScript()) {
        return currentScope.isGlobal();
      }
      // Otherwise we're in the current scope as long as the root nodes match up.
      return traversalScopeRoot == currentScope.getRootNode();
    }

    @Override
    public final void visit(NodeTraversal t, Node n, Node parent) {
      inputId = t.getInputId();
      if (parent != null) {
        attachLiteralTypes(n);
        visitPostorder(t, n, parent);
        if (deferredActions.containsKey(n)) { // streams are expensive, only make if needed
          deferredActions.removeAll(n).stream().forEach(Runnable::run);
        }
      } else if (!deferredActions.isEmpty()) {
        // Run *all* remaining deferred actions, in case any were missed.
        deferredActions.values().stream().forEach(Runnable::run);
      }
    }

    /** Called by shouldTraverse on nodes after ensuring the inputId is set. */
    void visitPreorder(NodeTraversal t, Node n, Node parent) {}

    /** Called by visit on nodes after updating the inputId. */
    void visitPostorder(NodeTraversal t, Node n, Node parent) {}

    void attachLiteralTypes(Node n) {
      switch (n.getToken()) {
        case NULL:
          n.setJSType(getNativeType(NULL_TYPE));
          break;

        case VOID:
          n.setJSType(getNativeType(VOID_TYPE));
          break;

        case STRING:
        case TEMPLATELIT_STRING:
          n.setJSType(getNativeType(STRING_TYPE));
          break;

        case NUMBER:
          n.setJSType(getNativeType(NUMBER_TYPE));
          break;

        case TRUE:
        case FALSE:
          n.setJSType(getNativeType(BOOLEAN_TYPE));
          break;

        case REGEXP:
          n.setJSType(getNativeType(REGEXP_TYPE));
          break;

        case OBJECTLIT:
          JSDocInfo info = n.getJSDocInfo();
          if (info != null && info.hasLendsName()) {
            // Defer analyzing object literals with a @lends annotation until we
            // reach the root of the statement they're defined in.
            //
            // This ensures that if there are any @lends annotations on the object
            // literals, the type on the @lends annotation resolves correctly.
            //
            // For more information, see http://blickly.github.io/closure-compiler-issues/#314
            deferredActions.put(NodeUtil.getEnclosingStatement(n), () -> defineObjectLiteral(n));
          } else {
            defineObjectLiteral(n);
          }
          break;

        case CLASS:
          // NOTE(sdh): We can't handle function nodes here because they need special behavior to
          // deal with hoisting.  But since classes aren't hoisted, and may need to be handled in
          // such places as default method initializers (i.e. in a FunctionScope) or class extends
          // clauses (technically part of the ClassScope, but visited instead by the NormalScope),
          // they can be handled consistently in all scopes.
          defineClassLiteral(n);
          break;

        // NOTE(johnlenz): If we ever support Array tuples,
        // we will need to handle them here as we do object literals
        // above.
        case ARRAYLIT:
          n.setJSType(getNativeType(ARRAY_TYPE));
          break;
        default:
          break;
      }
    }

    private void defineObjectLiteral(Node objectLit) {
      // Handle the @lends annotation.
      JSType type = null;
      JSDocInfo info = objectLit.getJSDocInfo();
      if (info != null && info.hasLendsName()) {
        String lendsName = info.getLendsName().getRoot().getString();
        TypedVar lendsVar = currentScope.getVar(lendsName);
        if (lendsVar == null) {
          report(JSError.make(objectLit, UNKNOWN_LENDS, lendsName));
        } else {
          type = lendsVar.getType();
          if (type == null) {
            type = unknownType;
          }
          if (!type.isSubtypeOf(typeRegistry.getNativeType(OBJECT_TYPE))) {
            report(JSError.make(
                objectLit, LENDS_ON_NON_OBJECT, lendsName, type.toString()));
            type = null;
          } else {
            objectLit.setJSType(type);
          }
        }
      }

      info = NodeUtil.getBestJSDocInfo(objectLit);
      boolean createEnumType = info != null && info.hasEnumParameterType();
      if (createEnumType) {
        Node lValue = NodeUtil.getBestLValue(objectLit);
        String lValueName = NodeUtil.getBestLValueName(lValue);
        type = createEnumTypeFromNodes(objectLit, lValueName, info);
      }

      if (type == null) {
        type = typeRegistry.createAnonymousObjectType(info);
      }

      setDeferredType(objectLit, type);

      // If this is an enum, the properties were already taken care of above.
      processObjectLitProperties(
          objectLit, ObjectType.cast(objectLit.getJSType()), !createEnumType);
    }

    /**
     * Process an object literal and all the types on it.
     * @param objLit The OBJECTLIT node.
     * @param objLitType The type of the OBJECTLIT node. This might be a named
     *     type, because of the lends annotation.
     * @param declareOnOwner If true, declare properties on the objLitType as
     *     well. If false, the caller should take care of this.
     */
    void processObjectLitProperties(
        Node objLit, ObjectType objLitType,
        boolean declareOnOwner) {
      for (Node keyNode = objLit.getFirstChild(); keyNode != null; keyNode = keyNode.getNext()) {
        if (keyNode.isComputedProp()) {
          // Don't try defining computed properties on an object.
          continue;
        }
        Node value = keyNode.getFirstChild();
        String memberName = NodeUtil.getObjectLitKeyName(keyNode);
        JSDocInfo info = keyNode.getJSDocInfo();
        JSType valueType = getDeclaredType(info, keyNode, value, null);
        JSType keyType =
            objLitType.isEnumType()
                ? objLitType.toMaybeEnumType().getElementsType()
                : TypeCheck.getObjectLitKeyTypeFromValueType(keyNode, valueType);

        // Try to declare this property in the current scope if it
        // has an authoritative name.
        String qualifiedName = NodeUtil.getBestLValueName(keyNode);
        if (qualifiedName != null) {
          new SlotDefiner()
              .forDeclarationNode(keyNode)
              .forVariableName(qualifiedName)
              .inScope(getLValueRootScope(keyNode))
              .withType(keyType)
              .allowLaterTypeInference(keyType == null)
              .defineSlot();
        } else if (keyType != null) {
          setDeferredType(keyNode, keyType);
        }

        if (keyType != null && objLitType != null && declareOnOwner) {
          // Declare this property on its object literal.
          objLitType.defineDeclaredProperty(memberName, keyType, keyNode);
        }
      }
    }

    /**
     * Returns the type specified in a JSDoc annotation near a GETPROP, NAME, member function, or
     * object literal key.
     *
     * <p>Extracts type information from the {@code @type} tag.
     */
    private JSType getDeclaredTypeInAnnotation(Node node, JSDocInfo info) {
      checkArgument(info.hasType());

      ImmutableList<TemplateType> ownerTypeKeys = ImmutableList.of();
      Node ownerNode = NodeUtil.getBestLValueOwner(node);
      String ownerName = NodeUtil.getBestLValueName(ownerNode);
      ObjectType ownerType = null;
      if (ownerName != null) {
        TypedVar ownerVar = currentScope.getVar(ownerName);
        if (ownerVar != null) {
          ownerType = getPrototypeOwnerType(ObjectType.cast(ownerVar.getType()));
          if (ownerType != null) {
            ownerTypeKeys = ownerType.getTemplateTypeMap().getTemplateKeys();
          }
        }
      }

      StaticTypedScope templateScope =
          !ownerTypeKeys.isEmpty()
              ? typeRegistry.createScopeWithTemplates(currentScope, ownerTypeKeys)
              : currentScope;
      return info.getType().evaluate(templateScope, typeRegistry);
    }

    /**
     * Asserts that it's OK to define this node's name.
     * The node should have a source name and be of the specified type.
     */
    void assertDefinitionNode(Node n, Token type) {
      checkState(sourceName != null);
      checkState(n.getToken() == type, n);
    }

    /**
     * Defines a catch parameter.
     */
    void defineCatch(Node n) {
      assertDefinitionNode(n, Token.CATCH);
      // Though almost certainly a terrible idea, it is possible to do destructuring in
      // the catch declaration.
      // e.g. `} catch ({message, errno}) {`
      for (Node catchName : NodeUtil.findLhsNodesInNode(n)) {
        JSType type = getDeclaredType(catchName.getJSDocInfo(), catchName, null, null);
        new SlotDefiner()
            .forDeclarationNode(catchName)
            .forVariableName(catchName.getString())
            .inScope(currentScope)
            .withType(type)
            .allowLaterTypeInference(type == null)
            .defineSlot();
      }
    }

    /** Defines a variable declared with `var`, `let`, or `const`. */
    void defineVars(Node n) {
      checkState(sourceName != null);
      checkState(NodeUtil.isNameDeclaration(n));
      JSDocInfo info = n.getJSDocInfo();
      // `var` declarations are hoisted, but `let` and `const` are not.
      TypedScope scope = n.isVar() ? currentHoistScope : currentScope;

      if (n.hasMoreThanOneChild() && info != null) {
        report(JSError.make(n, MULTIPLE_VAR_DEF));
      }

      for (Node child : n.children()) {
        defineVarChild(info, child, scope);
      }
    }

    /** Defines a variable declared with `var`, `let`, or `const`. */
    void defineVarChild(JSDocInfo declarationInfo, Node child, TypedScope scope) {
      if (child.isName()) {
        if (declarationInfo == null) {
          declarationInfo = child.getJSDocInfo();
          // TODO(bradfordcsmith): Report an error if both the declaration node and the name itself
          //     have JSDoc.
        }
        defineName(child, scope, declarationInfo);
      } else {
        checkState(child.isDestructuringLhs(), child);
        Node pattern = child.getFirstChild();
        Node value = child.getSecondChild();
        defineDestructuringPatternInVarDeclaration(
            pattern,
            scope,
            () ->
                // Note that value will be null if we are in an enhanced for loop
                //   for (const {x, y} of data) {
                value != null
                    ? new RValueInfo(
                        getDeclaredRValueType(/* lValue= */ null, value),
                        value.getQualifiedNameObject())
                    : new RValueInfo(unknownType, /* qualifiedName= */ null));
      }
    }

    /**
     * Returns information about the qualified name and type of the target, if it exists.
     *
     * <p>Never returns null, but will return an RValueInfo with null `type` and `qualifiedName`
     * slots.
     */
    private RValueInfo inferTypeForDestructuredTarget(
        DestructuredTarget target, Supplier<RValueInfo> patternTypeSupplier) {
      // Currently we only do type inference for string key nodes in object patterns here, to
      // handle aliasing types. e.g
      //   const {Foo} = ns;
      // TypeInference takes care of the rest.
      // Note that although DestructuredTarget includes logic for inferring types, we don't use
      // it here because we only do some very limited type inference during TypedScopeCreation,
      // and only return a non-null type here if we are accessing a declared property on a known
      // type.
      if (!target.hasStringKey() || target.hasDefaultValue()) {
        return RValueInfo.empty();
      }
      RValueInfo rvalue = patternTypeSupplier.get();
      JSType patternType = rvalue.type;
      String propertyName = target.getStringKey().getString();
      QualifiedName qname =
          rvalue.qualifiedName != null ? rvalue.qualifiedName.getprop(propertyName) : null;
      if (patternType == null || patternType.isUnknownType()) {
        return new RValueInfo(null, qname);
      }
      if (patternType.hasProperty(propertyName)) {
        JSType type = patternType.findPropertyType(propertyName);
        return new RValueInfo(type, qname);
      }
      return new RValueInfo(null, qname);
    }

    void defineDestructuringPatternInVarDeclaration(
        Node pattern, TypedScope scope, Supplier<RValueInfo> patternTypeSupplier) {
      for (DestructuredTarget target :
          DestructuredTarget.createAllNonEmptyTargetsInPattern(
              typeRegistry, () -> patternTypeSupplier.get().type, pattern)) {

        Supplier<RValueInfo> typeSupplier =
            () -> inferTypeForDestructuredTarget(target, patternTypeSupplier);

        if (target.getNode().isDestructuringPattern()) {
          defineDestructuringPatternInVarDeclaration(target.getNode(), scope, typeSupplier);
        } else {
          Node name = target.getNode();
          checkState(name.isName(), "This method is only for declaring variables: %s", name);

          // variable's type
          JSType type =
              getDeclaredType(name.getJSDocInfo(), name, /* rValue= */ null, typeSupplier);
          if (type == null) {
            // The variable's type will be inferred.
            type = name.isFromExterns() ? unknownType : null;
          }
          new SlotDefiner()
              .forDeclarationNode(name)
              .forVariableName(name.getString())
              .inScope(scope)
              .withType(type)
              .allowLaterTypeInference(type == null)
              .defineSlot();
        }
      }
    }

    /**
     * Defines a class literal.  Handles any of the following cases:
     * <ul>
     * <li>Class declarations: <code>class Foo { ... }</code>
     * <li>Class assignments: <code>foo.Bar = class { ... }</code>
     * <li>Bleeding names: <code>foo.Bar = class Baz { ... }</code>
     * <li>Properties: <code>{foo: class { ... }}</code>
     * <li>Callbacks: <code>foo(class { ... })</code>
     * </ul>
     */
    void defineClassLiteral(Node n) {
      assertDefinitionNode(n, Token.CLASS);

      // Determine the name and JSDocInfo and l-value for the class.
      // Any of these may be null.
      Node lValue = NodeUtil.getBestLValue(n);
      JSDocInfo info = NodeUtil.getBestJSDocInfo(n);
      String className = NodeUtil.getBestLValueName(lValue);

      // Create the type and assign it on the CLASS node.
      FunctionType classType = createClassTypeFromNodes(n, className, info, lValue);
      setDeferredType(n, classType);

      // Declare this symbol in the current scope iff it's a class
      // declaration. Otherwise, the declaration will happen in other
      // code paths.
      if (NodeUtil.isClassDeclaration(n)) {
        checkNotNull(className);
        new SlotDefiner()
            .forDeclarationNode(n.getFirstChild())
            .forVariableName(className)
            .inScope(currentScope)
            .withType(classType)
            .allowLaterTypeInference(classType == null)
            .defineSlot();
      }
    }

    /**
     * Defines a function literal.
     */
    void defineFunctionLiteral(Node n) {
      assertDefinitionNode(n, Token.FUNCTION);

      // Determine the name and JSDocInfo and l-value for the function.
      // Any of these may be null.
      Node lValue = NodeUtil.getBestLValue(n);
      JSDocInfo info = NodeUtil.getBestJSDocInfo(n);
      String functionName = NodeUtil.getBestLValueName(lValue);
      FunctionType functionType = createFunctionTypeFromNodes(n, functionName, info, lValue);

      // Assigning the function type to the function node
      setDeferredType(n, functionType);

      // Declare this symbol in the current scope iff it's a function
      // declaration. Otherwise, the declaration will happen in other
      // code paths.
      if (NodeUtil.isFunctionDeclaration(n)) {
        new SlotDefiner()
            .forDeclarationNode(n.getFirstChild())
            .forVariableName(functionName)
            .inScope(currentScope)
            .withType(functionType)
            .allowLaterTypeInference(functionType == null)
            .defineSlot();
      }
    }

    /**
     * Defines a variable based on the {@link Token#NAME} node passed.
     *
     * @param name The {@link Token#NAME} node.
     * @param scope
     * @param info the {@link JSDocInfo} information relating to this {@code name} node.
     */
    private void defineName(Node name, TypedScope scope, JSDocInfo info) {
      Node value = name.getFirstChild();

      // variable's type
      JSType type = getDeclaredType(info, name, value, /* declaredRValueTypeSupplier= */ null);
      if (type == null) {
        // The variable's type will be inferred.
        type = name.isFromExterns() ? unknownType : null;
      }
      new SlotDefiner()
          .forDeclarationNode(name)
          .forVariableName(name.getString())
          .inScope(scope)
          .withType(type)
          .allowLaterTypeInference(type == null)
          .defineSlot();
    }

    /**
     * If a variable is assigned a function literal in the global scope,
     * make that a declared type (even if there's no doc info).
     * There's only one exception to this rule:
     * if the return type is inferred, and we're in a local
     * scope, we should assume the whole function is inferred.
     */
    private boolean shouldUseFunctionLiteralType(
        FunctionType type, JSDocInfo info, Node lValue) {
      if (info != null) {
        return true;
      }
      if (lValue != null && NodeUtil.isObjectLitKey(lValue)) {
        return false;
      }
      // TODO(johnlenz): consider unifying global and local behavior
      return isLValueRootedInGlobalScope(lValue) || !type.isReturnTypeInferred();
    }

    /**
     * Creates a new class type from the given class literal.
     * This function does not need to worry about stubs and aliases because
     * they are handled by createFunctionTypeFromNodes instead.
     */
    private FunctionType createClassTypeFromNodes(
        Node n,
        @Nullable String name,
        @Nullable JSDocInfo info,
        @Nullable Node lvalueNode) {

      FunctionTypeBuilder builder =
          new FunctionTypeBuilder(name, compiler, n, currentScope)
              .usingClassSyntax()
              .setContents(new AstFunctionContents(n))
              .setDeclarationScope(lvalueNode != null ? getLValueRootScope(lvalueNode) : null)
              .inferKind(info)
              .inferTemplateTypeName(info, null);

      Node extendsClause = n.getSecondChild();
      Node classMembers = n.getLastChild();

      // Look at the extends clause and/or JSDoc info to find a super class.  Use generics from the
      // JSDoc to supplement the extends type when available.
      ObjectType baseType = findSuperClassFromNodes(extendsClause, info);
      builder.inferInheritance(info, baseType);

      // Look for an explicit constructor.
      Node constructor = NodeUtil.getFirstPropMatchingKey(classMembers, "constructor");
      if (constructor != null) {
        // Note: constructor should have the following structure:
        //   MEMBER_FUNCTION_DEF [jsdoc_info]
        //     FUNCTION
        //       NAME
        //       PARAM_LIST ...
        //       BLOCK ...
        // NodeUtil.getFirstPropMatchingKey returns the FUNCTION node.
        JSDocInfo ctorInfo = NodeUtil.getBestJSDocInfo(constructor);
        builder.inferConstructorParameters(constructor.getSecondChild(), ctorInfo);
      } else if (extendsClause.isEmpty()) {
        // No explicit constructor and no superclass: constructor is no-args.
        builder.inferImplicitConstructorParameters(new Node(Token.PARAM_LIST));
      } else {
        // No explicit constructor, but we have a superclass.  If we know its type, then copy its
        // constructor arguments (and templates).  If not, make the constructor arguments unknown.
        // TODO(sdh): consider allowing attaching constructor @param annotations somewhere else?
        FunctionType extendsCtor = baseType != null ? baseType.getConstructor() : null;
        if (extendsCtor != null) {
          // Known superclass: copy the parameters node.
          builder.inferImplicitConstructorParameters(extendsCtor.getParametersNode().cloneTree());
        } else {
          // Unresolveable extends clause: suppress typechecking.
          builder.inferImplicitConstructorParameters(
              typeRegistry.createParametersWithVarArgs(
                  typeRegistry.getNativeType(JSTypeNative.UNKNOWN_TYPE)));
        }
      }

      // TODO(sdh): Handle template parameters.  The constructor should store all parameters,
      // while the instance type should only have the class-level parameters?

      // Add the type for the "constructor" property.
      FunctionType classType = builder.buildAndRegister();
      if (classType.isConstructor()) {
        // NOTE: This logic is similar to the goog.inherits handling in
        // ClosureCodingConvention#applySubclassRelationship. If this logic is modified
        // it is likely that code needs to be modified as well.

        // Notice that constructor functions do not need to be covariant on the superclass.
        // So if G extends F, new G() and new F() can accept completely different argument
        // types, but G.prototype.constructor needs to be covariant on F.prototype.constructor.
        // To get around this, we just turn off type-checking on arguments and return types
        // of G.prototype.constructor.

        // NOTE: For final classes we could do better here and retain the parameter types.

        FunctionType qmarkCtor = classType.forgetParameterAndReturnTypes();
        ObjectType classPrototypeType = classType.getPrototypeProperty();
        classPrototypeType.defineDeclaredProperty("constructor", qmarkCtor, constructor);
      }

      return classType;
    }

    /**
     * Look at the {@code extends} clause to find the instance type being extended.
     * Returns {@code null} if there is no such clause, and unknown if the type cannot
     * be determined.
     */
    @Nullable
    private ObjectType findSuperClassFromNodes(Node extendsNode, @Nullable JSDocInfo info) {
      if (extendsNode.isEmpty()) {
        // No extends clause: return null.
        return null;
      }
      JSType ctorType = extendsNode.getJSType();
      if (ctorType == null) {
        if (extendsNode.isQualifiedName()) {
          // Look up qualified names in the scope (types won't be set on the AST until inference).
          TypedVar var = currentScope.getVar(extendsNode.getQualifiedName());
          if (var != null) {
            ctorType = var.getType();
          }
          // If that doesn't work, then fall back on the registry
          if (ctorType == null) {
            return ObjectType.cast(
                typeRegistry.getType(
                    currentScope,
                    extendsNode.getQualifiedName(),
                    extendsNode.getSourceFileName(),
                    extendsNode.getLineno(),
                    extendsNode.getCharno()));
          }
        } else {
          // Anything TypedScopeCreator can infer has already been read off the AST.  This is likely
          // a CALL or GETELEM, which are unknown until TypeInference.  Instead, ignore it for now,
          // require an @extends tag in the JSDoc, and verify correctness in TypeCheck.
          if (info == null || !info.hasBaseType()) {
            report(JSError.make(extendsNode, DYNAMIC_EXTENDS_WITHOUT_JSDOC));
          }
        }
      }

      if (ctorType != null) {
        if (ctorType.isConstructor() || ctorType.isInterface()) {
          return ctorType.toMaybeFunctionType().getInstanceType();
        } else if (ctorType.isUnknownType()) {
          // The constructor could have an unknown type for cases where it is dynamically
          // created or passed in from elsewhere.
          // e.g. with a mixin pattern
          // function mixinSomething(ctor) {
          //   return class extends ctor { ... };
          // }
          // In that case consider the super class instance type to be unknown.
          return ctorType.toMaybeObjectType();
        }
      }

      // We couldn't determine the type, so for TypedScope creation purposes we will treat it as if
      // there were no extends clause.  TypeCheck will give a more precise error later.
      return null;
    }

    /**
     * Creates a new function type, based on the given nodes.
     *
     * This handles two cases that are semantically very different, but
     * are not mutually exclusive:
     * - A function literal that needs a type attached to it (called from
     *   defineClassLiteral with a non-null FUNCTION node for rValue).
     * - An assignment expression with function-type info in the JsDoc
     *   (called from getDeclaredType on a stub (rValue == null) or alias
     *   (rValue is a qualified name).
     *
     * All parameters are optional, and we will do the best we can to create
     * a function type.
     *
     * This function will always create a function type, so only call it if
     * you're sure that's what you want.
     *
     * @param rValue The function node.
     * @param name the function's name
     * @param info the {@link JSDocInfo} attached to the function definition
     * @param lvalueNode The node where this function is being
     *     assigned. For example, {@code A.prototype.foo = ...} would be used to
     *     determine that this function is a method of A.prototype. May be
     *     null to indicate that this is not being assigned to a qualified name.
     */
    private FunctionType createFunctionTypeFromNodes(
        @Nullable Node rValue,
        @Nullable String name,
        @Nullable JSDocInfo info,
        @Nullable Node lvalueNode) {
      // Check for an alias.
      if (rValue != null && rValue.isQualifiedName() && lvalueNode != null) {
        TypedVar var = currentScope.getVar(rValue.getQualifiedName());
        if (var != null && var.getType() != null && var.getType().isFunctionType()) {
          FunctionType aliasedType  = var.getType().toMaybeFunctionType();
          if (aliasedType.isConstructor() || aliasedType.isInterface()) {
            // TODO(nick): Remove this. This should already be handled by normal type resolution.
            if (name != null) {
              typeRegistry.declareType(currentScope, name, aliasedType.getInstanceType());
            }
            checkFunctionAliasAnnotations(lvalueNode, aliasedType, info);
            return aliasedType;
          }
        }
      }

      // No alias: look for an explicit @type in JSDocInfo.
      if (info != null && info.hasType()) {
        JSType type = info.getType().evaluate(currentScope, typeRegistry);

        // Known to be not null since we have the FUNCTION token there.
        type = type.restrictByNotNullOrUndefined();
        if (type.isFunctionType()) {
          FunctionType functionType = type.toMaybeFunctionType();
          functionType.setJSDocInfo(info);
          return functionType;
        }
      }

      // No alias or explicit @type, so look for a function literal, or @param/@return.
      Node errorRoot = rValue == null ? lvalueNode : rValue;
      boolean isFnLiteral = rValue != null && rValue.isFunction();
      Node fnRoot = isFnLiteral ? rValue : null;
      Node parametersNode = isFnLiteral ? rValue.getSecondChild() : null;

      // If this function is being assigned as a property on a type, try finding the owner type
      // and the property name.
      // This is easy to do for class members because the owner type is on the CLASS node and
      // the property name is the MEMBER_FUNCTION_DEF/GETTER_DEF/SETTER_DEF string.
      // For other functions, we rely on NodeUtil.getBestLValueOwner.
      Node classRoot =
          lvalueNode != null && lvalueNode.getParent().isClassMembers()
              ? lvalueNode.getGrandparent()
              : null;
      Node ownerNode = NodeUtil.getBestLValueOwner(lvalueNode);

      ObjectType ownerType = null;
      String propName = null;
      if (classRoot != null) {
        // Static members are owned by the constructor, non-statics are owned by the prototype.
        ownerType = JSType.toMaybeFunctionType(classRoot.getJSType());
        if (!lvalueNode.isStaticMember() && ownerType != null) {
          ownerType = ((FunctionType) ownerType).getPrototype();
        }
        propName = lvalueNode.isComputedProp() ? null : lvalueNode.getString();
      } else {
        String ownerName = NodeUtil.getBestLValueName(ownerNode);
        TypedVar ownerVar = ownerName != null ? currentScope.getVar(ownerName) : null;
        if (ownerVar != null) {
          ownerType = ObjectType.cast(ownerVar.getType());
        }

        if (ownerName != null && name != null) {
          // TODO(b/111621092): Use the AST rather than manipulating strings here.
          checkState(
              name.startsWith(ownerName), "Expected \"%s\" to start with \"%s\"", name, ownerName);
          propName = name.substring(ownerName.length() + 1);
        }
      }

      ObjectType prototypeOwner = getPrototypeOwnerType(ownerType);
      TemplateTypeMap prototypeOwnerTypeMap = null;
      if (prototypeOwner != null && prototypeOwner.getTypeOfThis() != null) {
        prototypeOwnerTypeMap = prototypeOwner.getTypeOfThis().getTemplateTypeMap();
      }

      // Find the type of any overridden function.
      FunctionType overriddenType = null;
      if (ownerType != null && propName != null) {
        // the type of the property this overrides, not necessarily a function.
        JSType overriddenPropType =
            findOverriddenProperty(ownerType, propName, prototypeOwnerTypeMap);
        if (overriddenPropType != null) {
          // Overridden getters and setters need special handling because we declare
          // getters/setters as simple properties with their respective return/parameter type. This
          // causes a split during inference where left and right sides of a getter/setter
          // declaration will be inferred to have different types; if the left side has type `T`,
          // the right side will be some function type involving `T`.
          if (lvalueNode.isGetterDef()) {
            // Convert `number` to `function(): number`
            overriddenType = typeRegistry.createFunctionType(overriddenPropType);
          } else if (lvalueNode.isSetterDef()) {
            // Convert `number` to `function(number): undefined`
            overriddenType =
                typeRegistry.createFunctionType(getNativeType(VOID_TYPE), overriddenPropType);
          } else if (overriddenPropType.isFunctionType()) {
            // for cases where we override a non-method (e.g. a number) with a method, don't put the
            // non-method type (e.g. number) on the function.
            // Instead do some basic inference to create a function type.
            // we will warn during typechecking for an invalid override, but we don't want to put a
            // non-function type on this function because that will interfere with type inference
            // inside the function.
            overriddenType = overriddenPropType.toMaybeFunctionType();
          }
        }
      }


      AstFunctionContents contents = fnRoot != null ? new AstFunctionContents(fnRoot) : null;
      if (functionsWithNonEmptyReturns.contains(fnRoot)) {
        contents.recordNonEmptyReturn();
      }

      FunctionTypeBuilder builder =
          new FunctionTypeBuilder(name, compiler, errorRoot, currentScope)
              .setContents(contents)
              .setDeclarationScope(lvalueNode != null ? getLValueRootScope(lvalueNode) : null)
              .inferFromOverriddenFunction(overriddenType, parametersNode)
              .inferKind(info)
              .inferTemplateTypeName(info, prototypeOwner)
              .inferInheritance(info, null);

      if (info == null || !info.hasReturnType()) {
        // when there is no @return annotation, look for inline return type declaration
        if (rValue != null && rValue.isFunction() && rValue.getFirstChild() != null) {
          JSDocInfo nameDocInfo = rValue.getFirstChild().getJSDocInfo();
          builder.inferReturnType(nameDocInfo, true);
        }
      } else {
        builder.inferReturnType(info, false);
      }

      // Infer the context type.
      if (ownerType != null
          && ownerType.isFunctionPrototypeType()
          && ownerType.getOwnerFunction().hasInstanceType()) {
        builder.inferThisType(info, ownerType.getOwnerFunction().getInstanceType());
      } else if (ownerNode != null && ownerNode.isThis()) {
        // If we have a 'this' node, use the scope type.
        builder.inferThisType(info, currentScope.getTypeOfThis());
      } else {
        builder.inferThisType(info);
      }

      return builder.inferParameterTypes(parametersNode, info).buildAndRegister();
    }

    /**
     * Checks that the annotations in {@code info} are compatible with the aliased {@code type}.
     * Any errors will be reported at {@code n}, which should be the qualified name node.
     */
    private void checkFunctionAliasAnnotations(Node n, FunctionType type, JSDocInfo info) {
      if (info == null) {
        return;
      }
      String annotation = null;
      if (info.usesImplicitMatch()) {
        if (!type.isStructuralInterface()) {
          annotation = "@record";
        }
      } else if (info.isInterface()) {
        if (!type.isInterface()) {
          annotation = "@interface";
        }
      } else if (info.isConstructor() && !type.isConstructor()) {
        annotation = "@constructor";
      }
      // TODO(sdh): consider checking @template, @param, @return, and/or @this.
      if (annotation != null
          // TODO(sdh): Remove this extra check once TypeScript stops passing us duplicate
          // conflicting externs.  In particular, TS considers everything an interface, but Closure
          // externs mark most things as @constructor.  The load order is not always the same, so
          // the error can show up in either the generated TS externs file or in our own extern.
          && (!n.isFromExterns() || annotation.equals("@record"))) {
        report(JSError.make(n, INCOMPATIBLE_ALIAS_ANNOTATION, annotation, n.getQualifiedName()));
      }
    }

    private ObjectType getPrototypeOwnerType(ObjectType ownerType) {
      if (ownerType != null && ownerType.isFunctionPrototypeType()) {
        return ownerType.getOwnerFunction();
      }
      return null;
    }

    /**
     * Find the property that's being overridden on this type, if any.
     *
     * <p>Said property could be a method, field, getter, or setter. We don't distinguish between
     * these when looking up a property type.
     */
    private JSType findOverriddenProperty(
        ObjectType ownerType, String propName, TemplateTypeMap typeMap) {
      JSType result = null;

      // First, check to see if the property is implemented
      // on a superclass.
      JSType propType = ownerType.getPropertyType(propName);
      if (propType != null && !propType.isUnknownType()) {
        result = propType;
      } else {
        // If it's not, then check to see if it's implemented
        // on an implemented interface.
        for (ObjectType iface : ownerType.getCtorImplementedInterfaces()) {
          propType = iface.getPropertyType(propName);
          if (propType != null && !propType.isUnknownType()) {
            result = propType;
            break;
          }
        }
      }

      if (result != null && typeMap != null && !typeMap.isEmpty()) {
        result = result.visit(new TemplateTypeMapReplacer(typeRegistry, typeMap));
      }

      return result;
    }

    /**
     * Creates a new enum type, based on the given nodes.
     *
     * <p>This handles two cases that are semantically very different, but are not mutually
     * exclusive: (1) An object literal that needs an enum type attached to it. (2) An assignment
     * expression with an enum tag in the JsDoc.
     *
     * <p>This function will always create an enum type, so only call it if you're sure that's what
     * you want.
     *
     * @param rValue The node of the enum.
     * @param name The enum's name
     * @param info The {@link JSDocInfo} attached to the enum definition.
     */
    private EnumType createEnumTypeFromNodes(Node rValue, String name, JSDocInfo info) {
      checkNotNull(info);
      checkState(info.hasEnumParameterType());

      EnumType enumType = null;
      if (rValue != null && rValue.isQualifiedName()) {
        // Handle an aliased enum.
        TypedVar var = currentScope.getVar(rValue.getQualifiedName());
        if (var != null && var.getType() instanceof EnumType) {
          enumType = (EnumType) var.getType();
        }
      }

      if (enumType == null) {
        JSType elementsType = info.getEnumParameterType().evaluate(currentScope, typeRegistry);
        enumType = typeRegistry.createEnumType(name, rValue, elementsType);

        if (rValue != null && rValue.isObjectLit()) {
          // collect enum elements
          Node key = rValue.getFirstChild();
          while (key != null) {
            if (key.isComputedProp()) {
              report(JSError.make(key, INVALID_ENUM_KEY));
              key = key.getNext();
              continue;
            }
            String keyName = key.getString();
            Preconditions.checkNotNull(keyName, "Invalid enum key: %s", key);
            enumType.defineElement(keyName, key);
            key = key.getNext();
          }
        }
      }

      if (name != null) {
        typeRegistry.declareType(currentScope, name, enumType.getElementsType());
      }

      return enumType;
    }

    /** Responsible for defining typed variable "slots". */
    class SlotDefiner {
      Node declarationNode;
      String variableName;
      TypedScope scope;
      // default is no type and a type may be inferred later
      JSType type = null;
      boolean allowLaterTypeInference = true;

      // TODO(bradfordcsmith): Once all the logic needed for ES_2017 features has been added,
      //     make the API to this class more restrictive to avoid accidental misuse.
      //     e.g. There will probably always be a declarationNode, so make it a constructor
      //     parameter.

      /** @param declarationNode the defining NAME or GETPROP or object literal key node. */
      SlotDefiner forDeclarationNode(Node declarationNode) {
        this.declarationNode = declarationNode;
        return this;
      }

      SlotDefiner readVariableNameFromDeclarationNode() {
        // Only qualified name nodes can use this method to get the variable name
        // Object literal keys will have to compute their names themselves.
        // TODO(bradfordcsmith): Clean up these checks of the parent.
        Node parent = declarationNode.getParent();
        if (declarationNode.isName()) {
          checkArgument(
              parent.isFunction()
                  || parent.isClass()
                  || NodeUtil.isNameDeclaration(parent)
                  || parent.isParamList()
                  || (parent.isRest() && parent.getParent().isParamList())
                  || parent.isCatch());
        } else {
          checkArgument(
              declarationNode.isGetProp() && (parent.isAssign() || parent.isExprResult()));
        }
        variableName = declarationNode.getQualifiedName();
        return this;
      }

      // TODO(bradfordcsmith): maybe change to withVariableName(). Need to make these names more
      //     consistent.
      SlotDefiner forVariableName(String variableName) {
        this.variableName = variableName;
        return this;
      }

      SlotDefiner inScope(TypedScope scope) {
        this.scope = checkNotNull(scope);
        return this;
      }

      SlotDefiner withType(@Nullable JSType type) {
        this.type = type;
        return this;
      }

      SlotDefiner allowLaterTypeInference(boolean allowLaterTypeInference) {
        this.allowLaterTypeInference = allowLaterTypeInference;
        return this;
      }

      /**
       * Define the slot and do related work.
       *
       * <p>At minimum the declaration node and variable name must have been set.
       */
      void defineSlot() {
        checkNotNull(declarationNode, "declarationNode not set");
        checkNotNull(variableName, "variableName not set");
        checkState(allowLaterTypeInference || type != null, "null type but inference not allowed");
        checkState(!variableName.isEmpty());
        checkNotNull(scope);

        Node parent = declarationNode.getParent();

        TypedScope scopeToDeclareIn = scope;

        boolean isGlobalVar = declarationNode.isName() && scopeToDeclareIn.isGlobal();
        boolean shouldDeclareOnGlobalThis = isGlobalVar && (parent.isVar() || parent.isFunction());
        // If n is a property, then we should really declare it in the
        // scope where the root object appears. This helps out people
        // who declare "global" names in an anonymous namespace.
        TypedScope ownerScope = null;
        if (declarationNode.isGetProp()) {
          ownerScope = scopeToDeclareIn;
        } else if (variableName.contains(".")) {
          TypedVar rootVar =
              currentScope.getVar(variableName.substring(0, variableName.indexOf('.')));
          if (rootVar != null) {
            ownerScope = rootVar.getScope();
          }
        }
        // The owner scope could be any of the following:
        //   1. in the same CFG as scopeToDeclareIn
        //   2. in an outer CFG, which this scope closes over
        //   3. null, if this isn't a qualified name
        // In case 1, we need to clobber the declaration in ownerScope.
        // In case 2, we only declare in the outer scope if the qname is not already declared,
        // since qualified names are declare lazily.
        if (ownerScope != null
            && scopeToDeclareIn != ownerScope
            && (!ownerScope.hasOwnSlot(variableName)
                || ownerScope.hasSameContainerScope(scopeToDeclareIn))) {
          scopeToDeclareIn = ownerScope;
        }

        // TODO(sdh): Remove this special case.  It is required to reproduce the original
        // non-block-scoped behavior, which is depended on in several places including
        // https://github.com/angular/tsickle/issues/761.  But it's more correct to always
        // declare on the owner scope.  Once all the bugs are fixed, this should be removed.
        // We may be able to get by with checking a "declared" function's source for jsdoc.
        if (scopeToDeclareIn != currentHoistScope
            && scopeToDeclareIn.isGlobal()
            && scopeToDeclareIn.hasOwnSlot(variableName)) {
          scopeToDeclareIn = currentHoistScope;
        }

        // The input may be null if we are working with a AST snippet. So read
        // the extern info from the node.
        TypedVar newVar = null;

        // declared in closest scope?
        CompilerInput input = compiler.getInput(inputId);
        if (!scopeToDeclareIn.canDeclare(variableName)) {
          TypedVar oldVar = scopeToDeclareIn.getVar(variableName);
          newVar =
              validator.expectUndeclaredVariable(
                  sourceName, input, declarationNode, parent, oldVar, variableName, type);
        } else {
          if (type != null) {
            setDeferredType(declarationNode, type);
          }

          newVar =
              declare(
                  scopeToDeclareIn,
                  variableName,
                  declarationNode,
                  type,
                  input,
                  allowLaterTypeInference);

          if (type instanceof EnumType) {
            validateEnumInitializer(newVar, declarationNode);
          }
        }

        // We need to do some additional work for constructors and interfaces.
        FunctionType fnType = JSType.toMaybeFunctionType(type);
        if (fnType != null
            // We don't want to look at empty function types.
            && !type.isEmptyType()) {

          // We want to make sure that when we declare a new instance type
          // (with @constructor) that there's actually a ctor for it.
          // This doesn't apply to structural constructors (like
          // function(new:Array). Checking the constructed type against
          // the variable name is a sufficient check for this.
          if ((fnType.isConstructor() || fnType.isInterface())
              && variableName.equals(fnType.getReferenceName())) {
            finishConstructorDefinition(
                declarationNode, variableName, fnType, scopeToDeclareIn, input, newVar);
          }
        }

        if (shouldDeclareOnGlobalThis) {
          ObjectType globalThis = typeRegistry.getNativeObjectType(GLOBAL_THIS);
          if (allowLaterTypeInference) {
            globalThis.defineInferredProperty(
                variableName,
                type == null ? getNativeType(JSTypeNative.NO_TYPE) : type,
                declarationNode);
          } else {
            globalThis.defineDeclaredProperty(variableName, type, declarationNode);
          }
        }

        if (isGlobalVar
            && "Window".equals(variableName)
            && type != null
            && type.isFunctionType()
            && type.isConstructor()) {
          FunctionType globalThisCtor =
              typeRegistry.getNativeObjectType(GLOBAL_THIS).getConstructor();
          globalThisCtor.getInstanceType().clearCachedValues();
          globalThisCtor.getPrototype().clearCachedValues();
          globalThisCtor.setPrototypeBasedOn((type.toMaybeFunctionType()).getInstanceType());
        }
      }
    }

    /**
     * Validates that anything typed as an enum is initialized to either a qualified name or another
     * enum.
     *
     * <p>Explicitly allows any name created through destructuring to be of the enum type, because
     * the only way to have a destructuring name typed as an enum if it is aliasing something else.
     * You cannot put the @enum JSDoc on destructuring declarations
     */
    private void validateEnumInitializer(TypedVar var, Node declarationNode) {
      final boolean isValidValue;
      if (NodeUtil.isLhsByDestructuring(var.getNameNode())) {
        isValidValue = true;
      } else {
        Node initialValue = var.getInitialValue();
        isValidValue =
            initialValue != null && (initialValue.isObjectLit() || initialValue.isQualifiedName());
      }
      if (!isValidValue) {
        report(JSError.make(declarationNode, ENUM_INITIALIZER));
      }
    }

    /**
     * Declares a variable with the given {@code name} and {@code type} on the given {@code scope},
     * returning the newly-declared {@link TypedVar}. Additionally checks the {@link
     * #escapedVarNames} and {@link #assignedVarNames} maps (which were populated during the {@link
     * FirstOrderFunctionAnalyzer} and marks the result as escaped or assigned exactly once if
     * appropriate.
     */
    private TypedVar declare(
        TypedScope scope, String name, Node n, JSType type, CompilerInput input, boolean inferred) {
      TypedVar var = scope.declare(name, n, type, input, inferred);
      ScopedName scopedName = ScopedName.of(name, scope.getRootNode());
      if (escapedVarNames.contains(scopedName)) {
        var.markEscaped();
      }
      if (assignedVarNames.count(scopedName) == 1) {
        var.markAssignedExactlyOnce();
      }
      return var;
    }

    private void finishConstructorDefinition(
        Node n, String variableName, FunctionType fnType,
        TypedScope scopeToDeclareIn, CompilerInput input, TypedVar newVar) {
      // Declare var.prototype in the scope chain.
      FunctionType superClassCtor = fnType.getSuperClassConstructor();
      Property prototypeSlot = fnType.getSlot("prototype");

      // When we declare the function prototype implicitly, we
      // want to make sure that the function and its prototype
      // are declared at the same node. We also want to make sure
      // that the if a symbol has both a TypedVar and a JSType, they have
      // the same node.
      //
      // This consistency is helpful to users of SymbolTable,
      // because everything gets declared at the same place.
      prototypeSlot.setNode(n);

      String prototypeName = variableName + ".prototype";

      // There are some rare cases where the prototype will already
      // be declared. See TypedScopeCreatorTest#testBogusPrototypeInit.
      // Fortunately, other warnings will complain if this happens.
      TypedVar prototypeVar = scopeToDeclareIn.getVar(prototypeName);
      if (prototypeVar != null && prototypeVar.scope == scopeToDeclareIn) {
        scopeToDeclareIn.undeclare(prototypeVar);
      }

      scopeToDeclareIn.declare(
          prototypeName,
          n,
          prototypeSlot.getType(),
          input,
          // declared iff there's an explicit supertype
          superClassCtor == null
              || superClassCtor.getInstanceType().isEquivalentTo(getNativeType(OBJECT_TYPE)));

      // Make sure the variable is initialized to something if it constructs itself.
      if (newVar.getInitialValue() == null && !n.isFromExterns()) {
        report(
            JSError.make(
                n, fnType.isConstructor() ? CTOR_INITIALIZER : IFACE_INITIALIZER, variableName));
      }
    }

    /** Check if the given node is a property of a name in the global scope. */
    private boolean isLValueRootedInGlobalScope(Node n) {
      return getLValueRootScope(n).isGlobal();
    }

    /** Return the scope for the name of the given node. */
    private TypedScope getLValueRootScope(Node n) {
      Node root = NodeUtil.getBestLValueRoot(n);
      if (root != null) {
        if (root.isName()) {
          Node nameParent = root.getParent();
          switch (nameParent.getToken()) {
            case VAR:
              return currentHoistScope;
            case LET:
            case CONST:
            case CLASS:
            case FUNCTION:
            case PARAM_LIST:
            case CATCH:
              return currentScope;

            case REST:
              // TODO(bradfordcsmith): Handle array destructuring REST
              checkState(nameParent.getParent().isParamList(), nameParent);
              return currentScope;

            default:
              TypedVar var = currentScope.getVar(root.getString());
              if (var != null) {
                return var.getScope();
              }
          }
        } else if (root.isThis() || root.isSuper()) {
          // TODO(sdh): return currentHoistScope.getScopeOfThis(): b/77159344
          return currentHoistScope;
        }
      }
      return currentHoistScope.getGlobalScope();
    }

    /**
     * Look for a type declaration on a property assignment (in an ASSIGN or an object literal key).
     *
     * @param info The doc info for this property.
     * @param lValue The l-value node.
     * @param rValue The node that {@code n} is being initialized to, or {@code null} if this is a
     *     stub declaration.
     * @param declaredRValueTypeSupplier A supplier for the declared type of the rvalue, used for
     *     destructuring declarations where we have to do additional work on the rvalue.
     */
    JSType getDeclaredType(
        JSDocInfo info,
        Node lValue,
        @Nullable Node rValue,
        @Nullable Supplier<RValueInfo> declaredRValueTypeSupplier) {
      if (info != null && info.hasType()) {
        return getDeclaredTypeInAnnotation(lValue, info);
      } else if (rValue != null
          && rValue.isFunction()
          && shouldUseFunctionLiteralType(
              JSType.toMaybeFunctionType(rValue.getJSType()), info, lValue)) {
        return rValue.getJSType();
      } else if (rValue != null && rValue.isClass()) {
        return rValue.getJSType();
      } else if (info != null) {
        if (info.hasEnumParameterType()) {
          if (rValue != null && rValue.isObjectLit()) {
            return rValue.getJSType();
          } else {
            return createEnumTypeFromNodes(rValue, lValue.getQualifiedName(), info);
          }
        } else if (info.isConstructorOrInterface()) {
          return createFunctionTypeFromNodes(rValue, lValue.getQualifiedName(), info, lValue);
        }
      }

      // Check if this is constant, and if it has a known type.
      if (NodeUtil.isConstantDeclaration(compiler.getCodingConvention(), info, lValue)) {
        if (rValue != null) {
          JSType rValueType = getDeclaredRValueType(lValue, rValue);
          maybeDeclareAliasType(lValue, rValue.getQualifiedNameObject(), rValueType);
          if (rValueType != null) {
            return rValueType;
          }
        } else if (declaredRValueTypeSupplier != null) {
          RValueInfo rvalueInfo = declaredRValueTypeSupplier.get();
          if (rvalueInfo != null) {
            maybeDeclareAliasType(lValue, rvalueInfo.qualifiedName, rvalueInfo.type);
            if (rvalueInfo.type != null) {
              return rvalueInfo.type;
            }
          }
        }
      }

      if (info != null && FunctionTypeBuilder.isFunctionTypeDeclaration(info)) {
        String fnName = lValue.getQualifiedName();
        return createFunctionTypeFromNodes(null, fnName, info, lValue);
      }

      return null;
    }

    /**
     * For a const alias, like `const alias = other.name`, this may declare `alias` as a type name,
     * depending on what other.name is defined to be.
     */
    private void maybeDeclareAliasType(
        Node lValue, @Nullable QualifiedName rValue, @Nullable JSType rValueType) {
      // NOTE: this allows some strange patterns such allowing instance properties
      // to be aliases of constructors, and then creating a local alias of that to be
      // used as a type name.  Consider restricting this.

      if (!lValue.isQualifiedName() || (rValue == null)) {
        return;
      }

      Node definitionNode = getDefinitionNode(rValue);
      if (definitionNode != null) {
        JSType typedefType = definitionNode.getTypedefTypeProp();
        if (typedefType != null) {
          // Propagate typedef type to typedef aliases.
          lValue.setTypedefTypeProp(typedefType);
          String qName = lValue.getQualifiedName();
          typeRegistry.identifyNonNullableName(qName);
          typeRegistry.declareType(currentScope, qName, typedefType);
          return;
        }
      }

      // Treat @const-annotated aliases like @constructor/@interface if RHS has instance type
      if (rValueType != null
          && rValueType.isFunctionType()
          && rValueType.toMaybeFunctionType().hasInstanceType()) {
        FunctionType functionType = rValueType.toMaybeFunctionType();
        typeRegistry.declareType(
            currentScope, lValue.getQualifiedName(), functionType.getInstanceType());
      } else if (rValue != null) {
        // Also infer a type name for aliased @typedef
        JSType rhsNamedType = typeRegistry.getType(currentScope, rValue.join());
        if (rhsNamedType != null) {
          typeRegistry.declareType(currentScope, lValue.getQualifiedName(), rhsNamedType);
        }
      }
    }

    /** Returns the AST node associated with the definition, if any. */
    private Node getDefinitionNode(QualifiedName qname) {
      if (qname.isSimple()) {
        TypedVar var = currentScope.getVar(qname.getComponent());
        return var != null ? var.getNameNode() : null;
      }
      ObjectType parent = ObjectType.cast(lookupQualifiedName(qname.getOwner()));
      return parent != null ? parent.getPropertyDefSite(qname.getComponent()) : null;
    }

    /**
     * Check for common idioms of a typed R-value assigned to a const L-value.
     *
     * <p>Normally, we would only want this sort of propagation to happen under type inference. But
     * we want a declared const to be nameable in a type annotation, so we need to figure out the
     * type before we try to resolve the annotation.
     *
     * @param lValue is the lvalue node if this is a simple assignment, null for destructuring
     */
    private JSType getDeclaredRValueType(@Nullable Node lValue, Node rValue) {
      // If rValue has a type-cast, we use the type in the type-cast.
      JSDocInfo rValueInfo = rValue.getJSDocInfo();
      if (rValue.isCast() && rValueInfo != null && rValueInfo.hasType()) {
        return rValueInfo.getType().evaluate(currentScope, typeRegistry);
      }

      // Check if the type has already been computed during scope-creation.
      // This is mostly useful for literals like BOOLEAN, NUMBER, STRING, and
      // OBJECT_LITERAL
      JSType type = rValue.getJSType();
      if (type != null && !type.isUnknownType()) {
        return type;
      }

      // If rValue is a name, try looking it up in the current scope.
      if (rValue.isQualifiedName()) {
        return lookupQualifiedName(rValue.getQualifiedNameObject());
      }

      // Check for simple invariant operations, such as "!x" or "+x" or "''+x"
      if (NodeUtil.isBooleanResult(rValue)) {
        return getNativeType(BOOLEAN_TYPE);
      }

      if (NodeUtil.isNumericResult(rValue)) {
        return getNativeType(NUMBER_TYPE);
      }

      if (NodeUtil.isStringResult(rValue)) {
        return getNativeType(STRING_TYPE);
      }

      if (rValue.isNew() && rValue.getFirstChild().isQualifiedName()) {
        JSType targetType = lookupQualifiedName(rValue.getFirstChild().getQualifiedNameObject());
        if (targetType != null) {
          FunctionType fnType = targetType
              .restrictByNotNullOrUndefined()
              .toMaybeFunctionType();
          if (fnType != null && fnType.hasInstanceType()) {
            return fnType.getInstanceType();
          }
        }
      }

      // Check for a very specific JS idiom:
      // var x = x || TYPE;
      // This is used by Closure's base namespace for esoteric
      // reasons, so we only really care about that case.
      if (rValue.isOr()) {
        Node firstClause = rValue.getFirstChild();
        Node secondClause = firstClause.getNext();
        boolean namesMatch =
            firstClause.isName()
                && lValue != null
                && lValue.isName()
                && firstClause.getString().equals(lValue.getString());
        if (namesMatch) {
          type = secondClause.getJSType();
          if (type != null && !type.isUnknownType()) {
            return type;
          }
        }
      }

      return null;
    }

    private JSType lookupQualifiedName(QualifiedName qname) {
      TypedVar slot = currentScope.getVar(qname.join());
      if (slot != null && !slot.isTypeInferred()) {
        JSType type = slot.getType();
        if (type != null && !type.isUnknownType()) {
          return type;
        }
      } else if (!qname.isSimple()) {
        JSType type = lookupQualifiedName(qname.getOwner());
        // NOTE: The scope only contains declared types at this
        // point so any property we find is a value type
        // to look up properties on.
        if (type != null) {
          return type.findPropertyType(qname.getComponent());
        }
      }
      return null;
    }

    /**
     * Look for class-defining calls.
     * Because JS has no 'native' syntax for defining classes,
     * this is often very coding-convention dependent and business-logic heavy.
     */
    void checkForClassDefiningCalls(Node n) {
      SubclassRelationship relationship =
          codingConvention.getClassesDefinedByCall(n);
      if (relationship != null) {
        ObjectType superClass =
            TypeValidator.getInstanceOfCtor(currentScope.getVar(relationship.superclassName));
        ObjectType subClass =
            TypeValidator.getInstanceOfCtor(currentScope.getVar(relationship.subclassName));
        if (superClass != null && subClass != null) {
          // superCtor and subCtor might be structural constructors
          // (like {function(new:Object)}) so we need to resolve them back
          // to the original ctor objects.
          FunctionType superCtor = superClass.getConstructor();
          FunctionType subCtor = subClass.getConstructor();
          if (superCtor != null && subCtor != null) {
            codingConvention.applySubclassRelationship(
                new NominalTypeBuilderOti(superCtor, superClass),
                new NominalTypeBuilderOti(subCtor, subClass),
                relationship.type);
          }
        }
      }

      String singletonGetterClassName = codingConvention.getSingletonGetterClassName(n);
      if (singletonGetterClassName != null) {
        ObjectType objectType = ObjectType.cast(
            typeRegistry.getType(currentScope, singletonGetterClassName));
        if (objectType != null) {
          FunctionType functionType = objectType.getConstructor();

          if (functionType != null) {
            FunctionType getterType = typeRegistry.createFunctionType(objectType);
            codingConvention.applySingletonGetter(
                new NominalTypeBuilderOti(functionType, objectType), getterType);
          }
        }
      }

      DelegateRelationship delegateRelationship = codingConvention.getDelegateRelationship(n);
      if (delegateRelationship != null) {
        applyDelegateRelationship(delegateRelationship);
      }

      ObjectLiteralCast objectLiteralCast = codingConvention.getObjectLiteralCast(n);
      if (objectLiteralCast != null) {
        if (objectLiteralCast.diagnosticType == null) {
          ObjectType type = ObjectType.cast(
              typeRegistry.getType(currentScope, objectLiteralCast.typeName));
          if (type != null && type.getConstructor() != null) {
            setDeferredType(objectLiteralCast.objectNode, type);
            objectLiteralCast.objectNode.putBooleanProp(Node.REFLECTED_OBJECT, true);
          } else {
            report(JSError.make(n, CONSTRUCTOR_EXPECTED));
          }
        } else {
          report(JSError.make(n, objectLiteralCast.diagnosticType));
        }
      }
    }

    /**
     * Apply special properties that only apply to delegates.
     */
    private void applyDelegateRelationship(
        DelegateRelationship delegateRelationship) {
      ObjectType delegatorObject =
          ObjectType.cast(typeRegistry.getType(currentScope, delegateRelationship.delegator));
      ObjectType delegateBaseObject =
          ObjectType.cast(typeRegistry.getType(currentScope, delegateRelationship.delegateBase));
      ObjectType delegateSuperObject =
          ObjectType.cast(
              typeRegistry.getType(currentScope, codingConvention.getDelegateSuperclassName()));
      if (delegatorObject != null
          && delegateBaseObject != null
          && delegateSuperObject != null) {
        FunctionType delegatorCtor = delegatorObject.getConstructor();
        FunctionType delegateBaseCtor = delegateBaseObject.getConstructor();
        FunctionType delegateSuperCtor = delegateSuperObject.getConstructor();

        if (delegatorCtor != null && delegateBaseCtor != null && delegateSuperCtor != null) {
          FunctionParamBuilder functionParamBuilder = new FunctionParamBuilder(typeRegistry);
          functionParamBuilder.addRequiredParams(getNativeType(U2U_CONSTRUCTOR_TYPE));
          FunctionType findDelegate = typeRegistry.createFunctionType(
              typeRegistry.createDefaultObjectUnion(delegateBaseObject),
              functionParamBuilder.build());

          FunctionType delegateProxy =
              typeRegistry.createConstructorType(
                  delegateBaseObject.getReferenceName() + DELEGATE_PROXY_SUFFIX /* name */,
                  null /* source */,
                  null /* parameters */,
                  null /* returnType */,
                  null /* templateKeys */,
                  false /* isAbstract */);
          delegateProxy.setPrototypeBasedOn(delegateBaseObject);

          codingConvention.applyDelegateRelationship(
              new NominalTypeBuilderOti(delegateSuperCtor, delegateSuperObject),
              new NominalTypeBuilderOti(delegateBaseCtor, delegateBaseObject),
              new NominalTypeBuilderOti(delegatorCtor, delegatorObject),
              (ObjectType) delegateProxy.getTypeOfThis(),
              findDelegate);
          delegateProxyCtors.add(delegateProxy);
        }
      }
    }

    /**
     * Declare the symbol for a qualified name in the global scope.
     *
     * @param info The doc info for this property.
     * @param n A top-level GETPROP node (it should not be contained inside
     *     another GETPROP).
     * @param parent The parent of {@code n}.
     * @param rhsValue The node that {@code n} is being initialized to,
     *     or {@code null} if this is a stub declaration.
     */
    void maybeDeclareQualifiedName(NodeTraversal t, JSDocInfo info,
        Node n, Node parent, Node rhsValue) {
      checkForTypedef(t, n, info);

      Node ownerNode = n.getFirstChild();
      String ownerName = ownerNode.getQualifiedName();
      String qName = n.getQualifiedName();
      String propName = n.getLastChild().getString();
      checkArgument(qName != null && ownerName != null);

      // Precedence of type information on GETPROPs:
      // 1) @type annotation / @enum annotation
      // 2) ASSIGN to FUNCTION literal
      // 3) @param/@return annotation (with no function literal)
      // 4) ASSIGN to something marked @const
      // 5) ASSIGN to anything else
      //
      // 1, 3, and 4 are declarations, 5 is inferred, and 2 is a declaration iff
      // the function has JsDoc or has not been declared before.
      //
      // FUNCTION literals are special because TypedScopeCreator is very smart
      // about getting as much type information as possible for them.

      // Determining type for #1 + #2 + #3 + #4
      JSType valueType = getDeclaredType(info, n, rhsValue, null);
      if (valueType == null && rhsValue != null) {
        // Determining type for #5
        valueType = rhsValue.getJSType();
      }

      // Function prototypes are special.
      // It's a common JS idiom to do:
      // F.prototype = { ... };
      // So if F does not have an explicitly declared super type,
      // allow F.prototype to be redefined arbitrarily.
      if ("prototype".equals(propName)) {
        TypedVar qVar = currentScope.getVar(qName);
        if (qVar != null) {
          // If the programmer has declared that F inherits from Super,
          // and they assign F.prototype to an object literal,
          // then they are responsible for making sure that the object literal's
          // implicit prototype is set up appropriately. We just obey
          // the @extends tag.
          ObjectType qVarType = ObjectType.cast(qVar.getType());
          if (qVarType != null && rhsValue != null && rhsValue.isObjectLit()) {
            typeRegistry.resetImplicitPrototype(
                rhsValue.getJSType(), qVarType.getImplicitPrototype());
          } else if (!qVar.isTypeInferred()) {
            // If the programmer has declared that F inherits from Super,
            // and they assign F.prototype to some arbitrary expression,
            // there's not much we can do. We just ignore the expression,
            // and hope they've annotated their code in a way to tell us
            // what props are going to be on that prototype.
            return;
          }

          qVar.getScope().undeclare(qVar);
        }
      }

      if (valueType == null) {
        if (parent.isExprResult()) {
          // t is mutable so make sure to capture the current state before the lambda.
          boolean isExtern = t.getInput() != null && t.getInput().isExtern();
          deferredActions.put(
              currentScope.getRootNode(), () -> resolveStubDeclaration(n, isExtern, ownerName));
        }

        return;
      }

      boolean inferred = isQualifiedNameInferred(qName, n, info, rhsValue, valueType);
      if (!inferred) {
        ObjectType ownerType = getObjectSlot(ownerName);
        if (ownerType != null) {

          // need ownernode - is it always just first child of n?
          declarePropertyIfNamespaceType(t, ownerType, n, valueType);
        }

        // If the property is already declared, the error will be
        // caught when we try to declare it in the current scope.
        new SlotDefiner()
            .forDeclarationNode(n)
            .forVariableName(qName)
            .inScope(getLValueRootScope(n))
            .withType(valueType)
            .allowLaterTypeInference(inferred)
            .defineSlot();
      }
    }

    /**
     * Determines whether a qualified name is inferred.
     * NOTE(nicksantos): Determining whether a property is declared or not
     * is really really obnoxious.
     *
     * The problem is that there are two (equally valid) coding styles:
     *
     * (function() {
     *   /* The authoritative definition of goog.bar. /
     *   goog.bar = function() {};
     * })();
     *
     * function f() {
     *   goog.bar();
     *   /* Reset goog.bar to a no-op. /
     *   goog.bar = function() {};
     * }
     *
     * In a dynamic language with first-class functions, it's very difficult
     * to know which one the user intended without looking at lots of
     * contextual information (the second example demonstrates a small case
     * of this, but there are some really pathological cases as well).
     *
     * The current algorithm checks if either the declaration has
     * JsDoc type information, or @const with a known type,
     * or a function literal with a name we haven't seen before.
     */
    private boolean isQualifiedNameInferred(
        @Nullable String qName,
        Node n,
        @Nullable JSDocInfo info,
        @Nullable Node rhsValue,
        JSType valueType) {
      // Prototypes of constructors and interfaces are always declared.
      if (qName != null && qName.endsWith(".prototype")) {
        String className = qName.substring(0, qName.lastIndexOf(".prototype"));
        TypedVar slot = currentScope.getVar(className);
        JSType classType = slot == null ? null : slot.getType();
        if (classType != null && (classType.isConstructor() || classType.isInterface())) {
          return false;
        }
      }

      // If the jsdoc or RHS specifies a concrete type, it's not inferred.
      if (info != null
          && (info.hasType()
              || info.hasEnumParameterType()
              || isConstantDeclarationWithKnownType(info, n, valueType)
              || FunctionTypeBuilder.isFunctionTypeDeclaration(info)
              || (rhsValue != null && rhsValue.isFunction()))) {
        return false;
      }

      // At this point, we're pretty sure it's inferred, since there's neither
      // useful jsdoc info, nor a useful const or doc'd function RHS.  But
      // there's still one case where it may still not be: if the RHS is a
      // class or function that is not
      //   (1) a scoped qualified name (i.e. this.b.c or super.b.c),
      //   (2) already declared in a scope,
      //   (3) assigned in a conditional block, or
      //   (4) escaped to a closure,
      // then we treat it as if it is declared, rather than inferred.
      // Stubs and other values are always considered inferred at this point.
      if (rhsValue == null || (!rhsValue.isFunction() && !rhsValue.isClass())) {
        return true;
      }

      // "Scoped" qualified names (e.g. this.b.c or super.d) are inferred.
      if (!n.isUnscopedQualifiedName()) {
        return true;
      }

      // If this qname is already declared then treat this definition as inferred.
      TypedScope ownerScope = getLValueRootScope(n);
      if (ownerScope != null && ownerScope.hasOwnSlot(qName)) {
        return true;
      }

      // Check if this is in a conditional block.
      // Functions assigned in conditional blocks are inferred.
      if (hasControlStructureAncestor(n.getParent())) {
        return true;
      }

      // Check if this is assigned in an inner scope.
      // Functions assigned in inner scopes are inferred.
      if (ownerScope != null
          && escapedVarNames.contains(ScopedName.of(qName, ownerScope.getRootNode()))) {
        return true;
      }

      return false;
    }

    private boolean isConstantDeclarationWithKnownType(JSDocInfo info, Node n, JSType valueType) {
      return NodeUtil.isConstantDeclaration(compiler.getCodingConvention(), info, n)
          && valueType != null
          && !valueType.isUnknownType();
    }

    private boolean hasControlStructureAncestor(Node n) {
      while (!(n.isScript() || n.isFunction())) {
        if (NodeUtil.isControlStructure(n)) {
          return true;
        }
        n = n.getParent();
      }
      return false;
    }

    /**
     * Find the ObjectType associated with the given slot.
     * @param slotName The name of the slot to find the type in.
     * @return An object type, or null if this slot does not contain an object.
     */
    private ObjectType getObjectSlot(String slotName) {
      TypedVar ownerVar = currentScope.getVar(slotName);
      if (ownerVar != null) {
        JSType ownerVarType = ownerVar.getType();
        return ObjectType.cast(
            ownerVarType == null ? null : ownerVarType.restrictByNotNullOrUndefined());
      }
      return null;
    }

    /**
     * When a class has a stub for a property, and the property exists on a super interface,
     * use that type.
     */
    private JSType getInheritedInterfacePropertyType(ObjectType obj, String propName) {
      if (obj != null && obj.isFunctionPrototypeType()) {
        FunctionType f = obj.getOwnerFunction();
        for (ObjectType i : f.getImplementedInterfaces()) {
          if (i.hasProperty(propName)) {
            return i.getPropertyType(propName);
          }
        }
      }
      return null;
    }

    /**
     * Resolve any type-less stub declarations to unknown types if we could not find types for them
     * during traversal.  This method is only called as a deferred action after the root node is
     * visted.
     */
    void resolveStubDeclaration(Node n, boolean isExtern, String ownerName) {
      String qName = n.getQualifiedName();
      String propName = n.getLastChild().getString();

      // TODO(b/111216910): should this be getLValueRoot(n).hasOwnSlot(qName)?
      if (currentScope.hasOwnSlot(qName)) {
        return;
      }

      // If we see a stub property, make sure to register this property
      // in the type registry.
      ObjectType ownerType = getObjectSlot(ownerName);
      JSType inheritedType = getInheritedInterfacePropertyType(ownerType, propName);
      JSType stubType = inheritedType == null ? unknownType : inheritedType;
      new SlotDefiner()
          .forDeclarationNode(n)
          .readVariableNameFromDeclarationNode()
          .inScope(getLValueRootScope(n))
          .withType(stubType)
          .allowLaterTypeInference(true)
          .defineSlot();

      if (ownerType != null && (isExtern || ownerType.isFunctionPrototypeType())) {
        // If this is a stub for a prototype, just declare it
        // as an unknown type. These are seen often in externs.
        ownerType.defineInferredProperty(
            propName, stubType, n);
      } else {
        typeRegistry.registerPropertyOnType(
            propName, ownerType == null ? stubType : ownerType);
      }
    }

    /**
     * Handle typedefs.
     *
     * @param candidate A qualified name node.
     * @param info JSDoc comments.
     */
    void checkForTypedef(NodeTraversal t, Node candidate, JSDocInfo info) {
      if (info == null || !info.hasTypedefType()) {
        return;
      }

      String typedef = candidate.getQualifiedName();
      if (typedef == null) {
        return;
      }

      // TODO(b/73386087): Remove this check after the extern definitions are cleaned up.
      if (currentScope.isGlobal() && typedef.equals("symbol")) {
        return;
      }

      // TODO(nicksantos|user): This is a terrible, terrible hack
      // to bail out on recursive typedefs. We'll eventually need
      // to handle these properly.
      typeRegistry.declareType(currentScope, typedef, unknownType);

      JSType realType = info.getTypedefType().evaluate(currentScope, typeRegistry);
      if (realType == null) {
        report(JSError.make(candidate, MALFORMED_TYPEDEF, typedef));
      } else {
        candidate.setTypedefTypeProp(realType);
      }

      typeRegistry.overwriteDeclaredType(currentScope, typedef, realType);
      if (candidate.isGetProp()) {
        new SlotDefiner()
            .forDeclarationNode(candidate)
            .forVariableName(typedef)
            .inScope(getLValueRootScope(candidate))
            .withType(getNativeType(NO_TYPE))
            .allowLaterTypeInference(false)
            .defineSlot();
        Node definitionNode = getDefinitionNode(candidate.getFirstChild().getQualifiedNameObject());
        if (definitionNode != null) {
          ObjectType parent = ObjectType.cast(definitionNode.getJSType());
          if (parent != null) {
            JSType valueType = getNativeType(NO_TYPE);
            declarePropertyIfNamespaceType(t, parent, candidate, valueType);
          }
        }
      }
    }

    void declarePropertyIfNamespaceType(
        NodeTraversal t, ObjectType ownerType, Node getpropNode, JSType valueType) {
      checkState(getpropNode.isGetProp());
      String propName = getpropNode.getLastChild().getString();
      // Only declare this as an official property if it has not been
      // declared yet.
      if (ownerType.hasOwnProperty(propName) && !ownerType.isPropertyTypeInferred(propName)) {
        return;
      }
      // Define the property if any of the following are true:
      //   (1) it's a non-native extern type. Native types are excluded here because we don't
      //       want externs of the form "/** @type {!Object} */ var api = {}; api.foo;" to
      //       cause a property "foo" to be declared on Object.
      //   (2) it's a non-instance type. This primarily covers static properties on
      //       constructors (which are FunctionTypes, not InstanceTypes).
      //   (3) it's an assignment to 'this', which covers instance properties assigned in
      //       constructors or other methods.
      boolean isNonNativeExtern =
          t.getInput() != null && t.getInput().isExtern() && !ownerType.isNativeObjectType();
      if (isNonNativeExtern
          || !ownerType.isInstanceType()
          || getpropNode.getFirstChild().isThis()) {
        // If the property is undeclared or inferred, declare it now.
        ownerType.defineDeclaredProperty(propName, valueType, getpropNode);
      }
    }
  } // end AbstractScopeBuilder

  /** A shallow traversal of the global scope to build up all classes, functions, and methods. */
  private final class NormalScopeBuilder extends AbstractScopeBuilder {

    NormalScopeBuilder(TypedScope scope) {
      super(scope);
    }

    @Override
    void visitPreorder(NodeTraversal t, Node n, Node parent) {
      // Handle hoisted functions ahead of time, when preorder-visiting their enclosing block.
      if (NodeUtil.isStatementParent(n)) {
        for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
          if (NodeUtil.isHoistedFunctionDeclaration(child)) {
            defineFunctionLiteral(child);
          }
        }
      }

      // Create any child block scopes "pre-order" as we see them.
      //
      // This is required because hoisted or qualified names defined in earlier blocks might be
      // referred to later outside the block. This isn't a big deal in most cases since a NamedType
      // will be created and resolved later, but if a NamedType is used for a superclass, we lose a
      // lot of valuable checking. Recursing into child blocks immediately prevents this from being
      // a problem.
      //
      // We don't traverse into CLASSes because we haven't yet have created the class-type on which
      // to assign members. We'll do this on the way back up (post-order) instead, after the
      // class-type has been attached to the AST.
      if (parent != null && NodeUtil.createsBlockScope(n) && !n.isClass()) {
        createScope(n, currentScope);
      }

      // All other functions (and classes, etc) are handled when we see the actual function node.
      if (n.isFunction() && !NodeUtil.isHoistedFunctionDeclaration(n)) {
        defineFunctionLiteral(n);
      }
    }

    @Override
    void visitPostorder(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case CALL:
          checkForClassDefiningCalls(n);
          break;

        case ASSIGN:
          // Handle initialization of properties.
          // We only allow qualified name declarations of the form
          //   /** @type {number} */ a.b.c = rhs;
          // TODO(b/77597706): Ensure that CheckJSDoc warns for JSDoc on assignments not to
          // qualified names, e.g.
          //   /** @type {number} */ [a.b.c] = someArr;
          Node firstChild = n.getFirstChild();
          if (firstChild.isGetProp() && firstChild.isQualifiedName()) {
            maybeDeclareQualifiedName(t, n.getJSDocInfo(), firstChild, n, firstChild.getNext());
          }
          break;

        case CATCH:
          defineCatch(n);
          break;

        case VAR:
        case LET:
        case CONST:
          defineVars(n);
          // Handle typedefs.
          if (n.hasOneChild()) {
            checkForTypedef(t, n.getFirstChild(), n.getJSDocInfo());
          }
          break;

        case GETPROP:
          codingConvention.checkForCallingConventionDefinitions(n, delegateCallingConventions);
          // Handle stubbed properties.
          if (parent.isExprResult() && n.isQualifiedName()) {
            maybeDeclareQualifiedName(t, n.getJSDocInfo(), n, parent, null);
          }
          break;

        case CLASS:
          // Analyse CLASS child-scopes now because later code in this scope may assign
          // properties to these class-types. We want to ensure declarations within the CLASS have
          // priority.
          createScope(n, currentScope);
          break;

        default:
          break;
      }
    }
  } // end NormalScopeBuilder

  /**
   * Scope builder subclass for function scopes, which only contain bleeding function names and
   * parameter names.  The main function body is handled by the a NormalScopeBuilder on the function
   * block.
   */
  private final class FunctionScopeBuilder extends AbstractScopeBuilder {

    FunctionScopeBuilder(TypedScope scope) {
      super(scope);
    }

    @Override
    void visitPreorder(NodeTraversal t, Node n, Node parent) {
      if (parent == null) {
        handleFunctionInputs();
      } else if (n.isFunction()) {
        defineFunctionLiteral(n);
      }
    }

    /** Handle bleeding functions and function parameters. */
    void handleFunctionInputs() {
      // Handle bleeding functions.
      Node fnNode = currentScope.getRootNode();
      Node fnNameNode = fnNode.getFirstChild();
      String fnName = fnNameNode.getString();
      if (!fnName.isEmpty()) {
        TypedVar fnVar = currentScope.getVar(fnName);
        if (fnVar == null
            // Make sure we're not touching a native function. Native
            // functions aren't bleeding, but may not have a declaration
            // node.
            || (fnVar.getNameNode() != null
                // Make sure that the function is actually bleeding by checking
                // if has already been declared.
                && fnVar.getInitialValue() != fnNode)) {
          new SlotDefiner()
              .forDeclarationNode(fnNameNode)
              .forVariableName(fnName)
              .inScope(currentScope)
              .withType(fnNode.getJSType())
              .allowLaterTypeInference(false)
              .defineSlot();
        }
      }

      declareParameters();
    }

    /** Declares all of a function's parameters inside the function's scope. */
    void declareParameters() {
      Node functionNode = currentScope.getRootNode();
      Node astParameters = functionNode.getSecondChild();
      Node iifeArgumentNode = null;

      if (NodeUtil.isInvocationTarget(functionNode)) {
        iifeArgumentNode = functionNode.getNext();
      }

      FunctionType functionType = JSType.toMaybeFunctionType(functionNode.getJSType());
      if (functionType != null) {

        Node jsDocParameters = functionType.getParametersNode();
        if (jsDocParameters != null) {
          Node jsDocParameter = jsDocParameters.getFirstChild();
          for (Node astParameter : astParameters.children()) {
            if (iifeArgumentNode != null && iifeArgumentNode.isSpread()) {
              // don't try inferring types from spreads in iifes because we don't know how
              // many items are in the iterable.
              iifeArgumentNode = null;
            }
            JSType declaredType = jsDocParameter == null ? unknownType : jsDocParameter.getJSType();
            declareNamesInPositionalParameter(astParameter, declaredType, iifeArgumentNode);
            if (jsDocParameter != null) {
              jsDocParameter = jsDocParameter.getNext();
            }
            if (iifeArgumentNode != null) {
              iifeArgumentNode = iifeArgumentNode.getNext();
            }
          }
        }

        // Also add template params to the scope so that JSTypeRegistry can find them (they
        // were already registered by FunctionTypeBuilder).
        JSDocInfo info = NodeUtil.getBestJSDocInfo(functionNode);
        if (info != null) {
          Iterable<String> templateNames =
              Iterables.concat(info.getTemplateTypeNames(), info.getTypeTransformations().keySet());
          if (!Iterables.isEmpty(templateNames)) {
            CompilerInput input = getCompilerInput();
            JSType voidType = typeRegistry.getNativeType(VOID_TYPE);
            // Declare any template names in the function scope. This means that if someone shadows
            // an outer variable FOO with a @template FOO and refers to FOO inside the method, we
            // will treat it as undefined, rather than the correct type, which could lead to weird
            // errors. Ideally we'd have a "don't use me" type that gives an error at use.
            for (String name : templateNames) {
              if (!currentScope.canDeclare(name)) {
                validator.expectUndeclaredVariable(
                    NodeUtil.getSourceName(functionNode),
                    input,
                    functionNode,
                    functionNode.getParent(),
                    currentScope.getVar(name),
                    name,
                    voidType);
              }
              currentScope.declare(name, functionNode, voidType, input, /* inferred= */ false);
            }
          }
        }
      }
    } // end declareParameters

    /**
     * Declares the name(s) in a positional AST parameter in the scope.
     *
     * @param astParameter the positional parameter node
     * @param declaredParameterType the declared parameter type, or the unknown type if there is
     *     none
     * @param iifeArgumentNode the corresponding argument from the iife, if in an iife. e.g. for
     *     `(function (x) {}(3);` this would be `3`.
     */
    private void declareNamesInPositionalParameter(
        Node astParameter, JSType declaredParameterType, @Nullable Node iifeArgumentNode) {
      JSType paramType = declaredParameterType;
      boolean isInferred = paramType.equals(unknownType);

      if (iifeArgumentNode != null && isInferred) {
        String argumentName = iifeArgumentNode.getQualifiedName();
        TypedVar argumentVar =
            argumentName == null || currentScope.getParent() == null
                ? null
                : currentScope.getParent().getVar(argumentName);
        if (argumentVar != null && !argumentVar.isTypeInferred()) {
          paramType = argumentVar.getType();
        }
      }

      if (paramType == null) {
        paramType = unknownType;
      }

      switch (astParameter.getToken()) {
        case NAME: // function f(x) {}
          declareSingleParameterName(isInferred, astParameter, paramType);
          break;

        case REST: // function f(...x) {}
          // rest parameter is actually an array of the type specified in the JSDoc
          Node param = astParameter.getFirstChild();
          ObjectType arrayType = typeRegistry.getNativeObjectType(ARRAY_TYPE);
          JSType restParamType = typeRegistry.createTemplatizedType(arrayType, paramType);
          if (param.isName()) {
            declareSingleParameterName(isInferred, astParameter.getFirstChild(), restParamType);
          } else {
            // function f(...{length}) {}
            declareDestructuringParameter(isInferred, param, restParamType);
          }
          break;

        case DEFAULT_VALUE: // function f(x = 3) {} or function f([x] = []) {}
          Node actualParam = astParameter.getFirstChild();
          if (actualParam.isName()) {
            declareSingleParameterName(isInferred, actualParam, paramType);
          } else {
            declareDestructuringParameter(isInferred, actualParam, paramType);
          }
          break;

        case ARRAY_PATTERN: // function f([x]) {}
        case OBJECT_PATTERN: // function f({x}) {}
          declareDestructuringParameter(isInferred, astParameter, paramType);
          break;

        default:
          throw new IllegalStateException("Unexpected function parameter node " + astParameter);
      }
    }

    /**
     * Declares all names inside a destructuring pattern in a parameter list in the scope if we can
     * find a non-unknown type for them.
     *
     * <p>Unknown typed parameters are always treated as inferred, not declared. TypeInference may
     * later give them a better inferred type than unknown, but they will never become declared.
     *
     * <p>NOTE: currently, there are some less-than-ideal aspects to how we do this. If the pattern
     * type is an unresolved NamedType, then we can't lookup properties on it (to find the
     * individual parameter types) until after name resolution. The current state is to defer to
     * TypeInference to type those parameters, with the drawback that they are 'inferred', not
     * 'declared', and so any type can be assigned to them. In the future we will just enforce
     * typing each parameter individually: <a
     * href="https://github.com/google/closure-compiler/issues/1781">relevant issue</a>
     */
    private void declareDestructuringParameter(
        boolean isInferred, Node pattern, JSType patternType) {

      for (DestructuredTarget target :
          DestructuredTarget.createAllNonEmptyTargetsInPattern(
              typeRegistry, patternType, pattern)) {
        JSType parameterType = target.inferTypeWithoutUsingDefaultValue();

        if (target.getNode().isDestructuringPattern()) {
          declareDestructuringParameter(isInferred, target.getNode(), parameterType);
        } else {
          Node paramName = target.getNode();
          checkState(paramName.isName(), "Expected all parameters to be names, got %s", paramName);

          if (parameterType == null || parameterType.isUnknownType()) {
            JSDocInfo paramJSDoc = paramName.getJSDocInfo();
            if (paramJSDoc != null && paramJSDoc.hasType()) {
              // see if the parameter has its own inline JSDoc, and use that unless we already have
              // a type from @param JSDoc.
              // TODO(b/112651122): this should happen inside FunctionTypeBuilder, so that we can
              // check that calls to the function match the inline JSDoc.
              // TODO(b/111523967): we should also report a
              // warning if the inline and non-inline JSDoc conflict.
              parameterType =
                  typeRegistry.evaluateTypeExpression(paramJSDoc.getType(), currentScope);
              isInferred = false;
            } else {
              // note - these parameters may get better types during TypeInference
              isInferred = true;
            }
          }
          declareSingleParameterName(isInferred, paramName, parameterType);
        }
      }
    }

    private void declareSingleParameterName(boolean isInferred, Node name, JSType type) {
      new SlotDefiner()
          .forDeclarationNode(name)
          .forVariableName(name.getString())
          .inScope(currentScope)
          .withType(type)
          .allowLaterTypeInference(isInferred)
          .defineSlot();
    }
  } // end FunctionScopeBuilder

  /**
   * Scope builder subclass for class scopes, which only contain a bleeding class name.  Methods
   * are handled by FunctionScopeBuilder and NormalScopeBuilder for the bodies.
   */
  private final class ClassScopeBuilder extends AbstractScopeBuilder {

    private Table<String, Token, JSType> getterSetterTypes = null;

    ClassScopeBuilder(TypedScope scope) {
      super(scope);
    }

    @Override
    void visitPreorder(NodeTraversal t, Node n, Node parent) {
      // These are not descended into, so must be done preorder
      if (n.isFunction()) {
        if (parent.isMemberFunctionDef() && parent.getString().equals("constructor")) {
          // Constructor has already been analyzed, so pull that here.
          setDeferredType(n, currentScope.getRootNode().getJSType());
        } else {
          defineFunctionLiteral(n);
        }
      }
    }

    @Override
    void visitPostorder(NodeTraversal t, Node n, Node parent) {
      if (n.isName()
          && parent == currentScope.getRootNode()
          && NodeUtil.isClassExpression(parent)) {
        // Declare bleeding class name in scope.  Pull the type off the AST.
        checkState(!n.getString().isEmpty()); // anonymous classes have EMPTY nodes, not NAME
        new SlotDefiner()
            .forDeclarationNode(n)
            .readVariableNameFromDeclarationNode()
            .inScope(currentScope)
            .withType(parent.getJSType())
            .allowLaterTypeInference(false)
            .defineSlot();
      } else if (n.isMemberFunctionDef() && !"constructor".equals(n.getString())) {
        // Ignore "constructor" since it has special handling in `createClassTypeFromNodes()`.
        defineMemberFunction(n);
      } else if (n.isGetterDef() || n.isSetterDef()) {
        defineGetterSetter(n);
      }
    }

    void defineMemberFunction(Node n) {
      ObjectType ownerType = determineOwnerTypeForClassMember(n);
      ownerType.defineDeclaredProperty(n.getString(), n.getLastChild().getJSType(), n);
    }

    void defineGetterSetter(Node n) {
      // GETTER_DEF -> CLASS_MEMBERS -> CLASS
      if (getterSetterTypes == null) {
        this.getterSetterTypes = HashBasedTable.create();
      }

      FunctionType methodType = n.getLastChild().getJSType().toMaybeFunctionType();
      JSType propertyType =
          n.isGetterDef()
              ? determineGetterType(methodType)
              : Iterables.getFirst(methodType.getParameterTypes(), null);
      propertyType = propertyType != null ? propertyType : unknownType;
      String name = n.getString();

      JSType previousType =
          getterSetterTypes.get(name, n.isGetterDef() ? Token.SETTER_DEF : Token.GETTER_DEF);
      if (previousType != null && !previousType.equals(propertyType)) {
        // TODO(sdh): make this not an error - instead, store the getter and setter types separately

        report(
            JSError.make(
                n,
                CONFLICTING_GETTER_SETTER_TYPE,
                name,
                n.isGetterDef() ? propertyType.toString() : previousType.toString(),
                n.isGetterDef() ? previousType.toString() : propertyType.toString()));
      } else if (previousType == null) {
        ObjectType ownerType = determineOwnerTypeForClassMember(n);
        ownerType.defineDeclaredProperty(name, propertyType, n);
        getterSetterTypes.put(name, n.getToken(), propertyType);
      }
    }

    /**
     * Returns the owner type for a class member function, getter, or setter.
     *
     * <p>For a member on class C, this is either `C` for a static member or `C.prototype` for a
     * nonstatic member.
     */
    private ObjectType determineOwnerTypeForClassMember(Node member) {
      // MEMBER_FUNCTION_DEF -> CLASS_MEMBERS -> CLASS  or
      // GETTER_DEF -> CLASS_MEMBERS -> CLASS
      Node ownerNode = member.getGrandparent();
      checkState(ownerNode.isClass());
      ObjectType ownerType = ownerNode.getJSType().toMaybeFunctionType();
      if (!member.isStaticMember()) {
        ownerType = ((FunctionType) ownerType).getPrototype();
      }
      return ownerType;
    }

    /** Returns the type of the getter, falling back on unknown if the return type was inferred. */
    private JSType determineGetterType(FunctionType methodType) {
      // TODO(sdh): consider only falling back on unknown if the function body is empty?  But we
      // need to not report a conflicting type error if there's different unknowns.
      return !methodType.isReturnTypeInferred() ? methodType.getReturnType() : unknownType;
    }
  } // end ClassScopeBuilder

  /**
   * Does a first-order function analysis that just looks at simple things like what variables are
   * escaped, and whether 'this' is used.
   */
  private class FirstOrderFunctionAnalyzer extends AbstractScopedCallback {

    void process(Node externs, Node root) {
      if (externs == null) {
        NodeTraversal.traverse(compiler, root, this);
      } else {
        NodeTraversal.traverseRoots(compiler, this, externs, root);
      }
    }

    @Override public void visit(NodeTraversal t, Node n, Node parent) {
      if (t.inGlobalScope()) {
        // The first-order function analyzer looks at two types of variables:
        //
        // 1) Local variables that are assigned in inner scopes ("escaped vars")
        //
        // 2) Local variables that are assigned more than once.
        //
        // We treat all global variables as escaped by default, so there's
        // no reason to do this extra computation for them.
        return;
      }

      Scope containerScope = (Scope) t.getClosestContainerScope();

      // Record function with returns or arrow functions without bodies
      if ((n.isReturn() && n.hasChildren())
          || (NodeUtil.isBlocklessArrowFunctionResult(n))) {
        functionsWithNonEmptyReturns.add(containerScope.getRootNode());
      }

      // Be careful of bleeding functions, which create variables
      // in the inner scope, not the scope where the name appears.
      if (n.isName() && NodeUtil.isLValue(n) && !NodeUtil.isBleedingFunctionName(n)) {
        String name = n.getString();
        Scope scope = t.getScope();
        Var var = scope.getVar(name);
        // TODO(sdh): consider checking hasSameHoistScope instead of container scope here and
        // below. This will detect function(a) { a.foo = bar } as an escaped qualified name,
        // which seems like the right thing to do (but could possibly break things?)
        // Doing so will allow removing the warning on TypeCheckTest#testIssue1024b.
        if (var != null) {
          Scope ownerScope = var.getScope();
          if (ownerScope.isLocal()) {
            ScopedName scopedName = ScopedName.of(name, ownerScope.getRootNode());
            assignedVarNames.add(scopedName);
            if (!containerScope.hasSameContainerScope(ownerScope)) {
              escapedVarNames.add(scopedName);
            }
          }
        }
      } else if (n.isGetProp() && n.isUnscopedQualifiedName() && NodeUtil.isLValue(n)) {
        String name = NodeUtil.getRootOfQualifiedName(n).getString();
        Scope scope = t.getScope();
        Var var = scope.getVar(name);
        if (var != null) {
          Scope ownerScope = var.getScope();
          if (ownerScope.isLocal() && !containerScope.hasSameContainerScope(ownerScope)) {
            escapedVarNames.add(ScopedName.of(n.getQualifiedName(), ownerScope.getRootNode()));
          }
        }
      }
    }
  }

  @Override
  public boolean hasBlockScope() {
    return true;
  }
}
