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

import static com.google.javascript.jscomp.TypeCheck.ENUM_NOT_CONSTANT;
import static com.google.javascript.jscomp.TypeCheck.MULTIPLE_VAR_DEF;
import static com.google.javascript.rhino.jstype.JSTypeNative.ARRAY_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.ARRAY_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_OBJECT_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.DATE_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.ERROR_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.EVAL_ERROR_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.FUNCTION_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.FUNCTION_INSTANCE_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.GLOBAL_THIS;
import static com.google.javascript.rhino.jstype.JSTypeNative.NO_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NULL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_OBJECT_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.RANGE_ERROR_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.REFERENCE_ERROR_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.REGEXP_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.REGEXP_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_OBJECT_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.SYNTAX_ERROR_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.TYPE_ERROR_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.U2U_CONSTRUCTOR_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.URI_ERROR_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.VOID_TYPE;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
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
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.EnumType;
import com.google.javascript.rhino.jstype.FunctionParamBuilder;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.Property;
import com.google.javascript.rhino.jstype.TemplateType;
import com.google.javascript.rhino.jstype.TemplateTypeMap;
import com.google.javascript.rhino.jstype.TemplateTypeMapReplacer;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
final class TypedScopeCreator implements ScopeCreator {
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

  static final DiagnosticType CANNOT_INFER_CONST_TYPE =
      DiagnosticType.disabled(
          "JSC_CANNOT_INFER_CONST_TYPE",
          "Unable to infer type of constant.");

  private final AbstractCompiler compiler;
  private final ErrorReporter typeParsingErrorReporter;
  private final TypeValidator validator;
  private final CodingConvention codingConvention;
  private final JSTypeRegistry typeRegistry;
  private final List<ObjectType> delegateProxyPrototypes = Lists.newArrayList();
  private final Map<String, String> delegateCallingConventions =
      Maps.newHashMap();

  // Simple properties inferred about functions.
  private final Map<Node, AstFunctionContents> functionAnalysisResults =
      Maps.newHashMap();

  // For convenience
  private final ObjectType unknownType;

  /**
   * Defer attachment of types to nodes until all type names
   * have been resolved. Then, we can resolve the type and attach it.
   */
  private class DeferredSetType {
    final Node node;
    final JSType type;

    DeferredSetType(Node node, JSType type) {
      Preconditions.checkNotNull(node);
      Preconditions.checkNotNull(type);
      this.node = node;
      this.type = type;

      // Other parts of this pass may read off the node.
      // (like when we set the LHS of an assign with a typed RHS function.)
      node.setJSType(type);
    }

    void resolve(TypedScope scope) {
      node.setJSType(type.resolve(typeParsingErrorReporter, scope));
    }
  }

  TypedScopeCreator(AbstractCompiler compiler) {
    this(compiler, compiler.getCodingConvention());
  }

  TypedScopeCreator(AbstractCompiler compiler,
      CodingConvention codingConvention) {
    this.compiler = compiler;
    this.validator = compiler.getTypeValidator();
    this.codingConvention = codingConvention;
    this.typeRegistry = compiler.getTypeRegistry();
    this.typeParsingErrorReporter = typeRegistry.getErrorReporter();
    this.unknownType = typeRegistry.getNativeObjectType(UNKNOWN_TYPE);
  }

  /**
   * Creates a scope with all types declared. Declares newly discovered types
   * and type properties in the type registry.
   */
  @Override
  public TypedScope createScope(Node root, Scope parent) {
    Preconditions.checkArgument(parent == null || parent instanceof TypedScope);
    TypedScope typedParent = (TypedScope) parent;
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
      (new FirstOrderFunctionAnalyzer(compiler, functionAnalysisResults))
          .process(root.getFirstChild(), root.getLastChild());

      // Find all the classes in the global scope.
      newScope = createInitialScope(root);

      GlobalScopeBuilder globalScopeBuilder = new GlobalScopeBuilder(newScope);
      scopeBuilder = globalScopeBuilder;
      NodeTraversal.traverseTyped(compiler, root, scopeBuilder);
    } else {
      newScope = new TypedScope(typedParent, root);
      LocalScopeBuilder localScopeBuilder = new LocalScopeBuilder(newScope);
      scopeBuilder = localScopeBuilder;
      localScopeBuilder.build();
    }

    scopeBuilder.resolveStubDeclarations();

    if (typedParent == null) {
      codingConvention.defineDelegateProxyPrototypeProperties(
          typeRegistry, newScope, delegateProxyPrototypes,
          delegateCallingConventions);
    }

    newScope.setTypeResolver(scopeBuilder);
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
    Preconditions.checkState(scriptRoot.isScript());
    Preconditions.checkNotNull(globalScope);
    Preconditions.checkState(globalScope.isGlobal());

    String scriptName = NodeUtil.getSourceName(scriptRoot);
    Preconditions.checkNotNull(scriptName);
    for (Node node : ImmutableList.copyOf(functionAnalysisResults.keySet())) {
      if (scriptName.equals(NodeUtil.getSourceName(node))) {
        functionAnalysisResults.remove(node);
      }
    }

    (new FirstOrderFunctionAnalyzer(
        compiler, functionAnalysisResults)).process(null, scriptRoot);

    // TODO(bashir): Variable declaration is not the only side effect of last
    // global scope generation but here we only wipe that part off!

    // Remove all variables that were previously declared in this scripts.
    // First find all vars to remove then remove them because of iterator!
    Iterator<TypedVar> varIter = globalScope.getVars();
    List<TypedVar> varsToRemove = Lists.newArrayList();
    while (varIter.hasNext()) {
      TypedVar oldVar = varIter.next();
      if (scriptName.equals(oldVar.getInputName())) {
        varsToRemove.add(oldVar);
      }
    }
    for (TypedVar var : varsToRemove) {
      globalScope.undeclare(var);
      globalScope.getTypeOfThis().toObjectType().removeProperty(var.getName());
    }

    // Now re-traverse the given script.
    GlobalScopeBuilder scopeBuilder = new GlobalScopeBuilder(globalScope);
    NodeTraversal.traverseTyped(compiler, scriptRoot, scopeBuilder);
  }

  /**
   * Create the outermost scope. This scope contains native binding such as
   * {@code Object}, {@code Date}, etc.
   */
  @VisibleForTesting
  TypedScope createInitialScope(Node root) {

    NodeTraversal.traverseTyped(
        compiler, root, new DiscoverEnumsAndTypedefs(typeRegistry));

    TypedScope s = TypedScope.createGlobalScope(root);
    declareNativeFunctionType(s, ARRAY_FUNCTION_TYPE);
    declareNativeFunctionType(s, BOOLEAN_OBJECT_FUNCTION_TYPE);
    declareNativeFunctionType(s, DATE_FUNCTION_TYPE);
    declareNativeFunctionType(s, ERROR_FUNCTION_TYPE);
    declareNativeFunctionType(s, EVAL_ERROR_FUNCTION_TYPE);
    declareNativeFunctionType(s, FUNCTION_FUNCTION_TYPE);
    declareNativeFunctionType(s, NUMBER_OBJECT_FUNCTION_TYPE);
    declareNativeFunctionType(s, OBJECT_FUNCTION_TYPE);
    declareNativeFunctionType(s, RANGE_ERROR_FUNCTION_TYPE);
    declareNativeFunctionType(s, REFERENCE_ERROR_FUNCTION_TYPE);
    declareNativeFunctionType(s, REGEXP_FUNCTION_TYPE);
    declareNativeFunctionType(s, STRING_OBJECT_FUNCTION_TYPE);
    declareNativeFunctionType(s, SYNTAX_ERROR_FUNCTION_TYPE);
    declareNativeFunctionType(s, TYPE_ERROR_FUNCTION_TYPE);
    declareNativeFunctionType(s, URI_ERROR_FUNCTION_TYPE);
    declareNativeValueType(s, "undefined", VOID_TYPE);

    // There is no longer a need to special case ActiveXObject
    // but this remains here until we can get the extern forks
    // cleaned up.
    declareNativeValueType(s, "ActiveXObject", FUNCTION_INSTANCE_TYPE);

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

  private static class DiscoverEnumsAndTypedefs
      extends AbstractShallowStatementCallback {
    private final JSTypeRegistry registry;

    DiscoverEnumsAndTypedefs(JSTypeRegistry registry) {
      this.registry = registry;
    }

    @Override
    public void visit(NodeTraversal t, Node node, Node parent) {
      switch (node.getType()) {
        case Token.VAR:
          for (Node child = node.getFirstChild();
               child != null; child = child.getNext()) {
            identifyNameNode(
                child, NodeUtil.getBestJSDocInfo(child));
          }
          break;
        case Token.EXPR_RESULT:
          Node firstChild = node.getFirstChild();
          if (firstChild.isAssign()) {
            identifyNameNode(
                firstChild.getFirstChild(), firstChild.getJSDocInfo());
          } else {
            identifyNameNode(
                firstChild, firstChild.getJSDocInfo());
          }
          break;
      }
    }

    private void identifyNameNode(
        Node nameNode, JSDocInfo info) {
      if (nameNode.isQualifiedName()) {
        if (info != null) {
          if (info.hasEnumParameterType()) {
            registry.identifyNonNullableName(nameNode.getQualifiedName());
          } else if (info.hasTypedefType()) {
            registry.identifyNonNullableName(nameNode.getQualifiedName());
          }
        }
      }
    }
  }

  private JSType getNativeType(JSTypeNative nativeType) {
    return typeRegistry.getNativeType(nativeType);
  }

  private abstract class AbstractScopeBuilder
      implements NodeTraversal.Callback, TypedScope.TypeResolver {

    /**
     * The scope that we're building.
     */
    final TypedScope scope;

    private final List<DeferredSetType> deferredSetTypes =
        Lists.newArrayList();

    /**
     * Functions that we found in the global scope and not in externs.
     */
    private final List<Node> nonExternFunctions = Lists.newArrayList();

    /**
     * Object literals with a @lends annotation aren't analyzed until we
     * reach the root of the statement they're defined in.
     *
     * This ensures that if there are any @lends annotations on the object
     * literals, the type on the @lends annotation resolves correctly.
     *
     * For more information, see
     * http://code.google.com/p/closure-compiler/issues/detail?id=314
     */
    private List<Node> lentObjectLiterals = null;

    /**
     * Type-less stubs.
     *
     * If at the end of traversal, we still don't have types for these
     * stubs, then we should declare UNKNOWN types.
     */
    private final List<StubDeclaration> stubDeclarations =
        Lists.newArrayList();

    /**
     * The current source file that we're in.
     */
    private String sourceName = null;

    /**
     * The InputId of the current node.
     */
    private InputId inputId;

    private AbstractScopeBuilder(TypedScope scope) {
      this.scope = scope;
    }

    void setDeferredType(Node node, JSType type) {
      deferredSetTypes.add(new DeferredSetType(node, type));
    }

    @Override
    public void resolveTypes() {
      // Resolve types and attach them to nodes.
      for (DeferredSetType deferred : deferredSetTypes) {
        deferred.resolve(scope);
      }

      // Resolve types and attach them to scope slots.
      Iterator<TypedVar> vars = scope.getVars();
      while (vars.hasNext()) {
        vars.next().resolveType(typeParsingErrorReporter);
      }

      // Tell the type registry that any remaining types
      // are unknown.
      typeRegistry.resolveTypesInScope(scope);
    }

    @Override
    public final boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      inputId = t.getInputId();
      if (n.isFunction() ||
          n.isScript()) {
        Preconditions.checkNotNull(inputId);
        sourceName = NodeUtil.getSourceName(n);
      }

      // We do want to traverse the name of a named function, but we don't
      // want to traverse the arguments or body.
      boolean descend = parent == null || !parent.isFunction() ||
          n == parent.getFirstChild() || parent == scope.getRootNode();

      if (descend) {
        // Handle hoisted functions on pre-order traversal, so that they
        // get hit before other things in the scope.
        if (NodeUtil.isStatementParent(n)) {
          for (Node child = n.getFirstChild();
               child != null;
               child = child.getNext()) {
            if (NodeUtil.isHoistedFunctionDeclaration(child)) {
              defineFunctionLiteral(child);
            }
          }
        }
      }

      return descend;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      inputId = t.getInputId();
      attachLiteralTypes(n);

      switch (n.getType()) {
        case Token.CALL:
          checkForClassDefiningCalls(n);
          checkForCallingConventionDefiningCalls(n, delegateCallingConventions);
          break;

        case Token.FUNCTION:
          if (t.getInput() == null || !t.getInput().isExtern()) {
            nonExternFunctions.add(n);
          }

          // Hoisted functions are handled during pre-traversal.
          if (!NodeUtil.isHoistedFunctionDeclaration(n)) {
            defineFunctionLiteral(n);
          }
          break;

        case Token.ASSIGN:
          // Handle initialization of properties.
          Node firstChild = n.getFirstChild();
          if (firstChild.isGetProp() &&
              firstChild.isQualifiedName()) {
            maybeDeclareQualifiedName(t, n.getJSDocInfo(),
                firstChild, n, firstChild.getNext());
          }
          break;

        case Token.CATCH:
          defineCatch(n);
          break;

        case Token.VAR:
          defineVar(n);
          break;

        case Token.GETPROP:
          // Handle stubbed properties.
          if (parent.isExprResult() &&
              n.isQualifiedName()) {
            maybeDeclareQualifiedName(t, n.getJSDocInfo(), n, parent, null);
          }
          break;
      }

      // Analyze any @lends object literals in this statement.
      if (n.getParent() != null && NodeUtil.isStatement(n) &&
          lentObjectLiterals != null) {
        for (Node objLit : lentObjectLiterals) {
          defineObjectLiteral(objLit);
        }
        lentObjectLiterals.clear();
      }
    }

    private void attachLiteralTypes(Node n) {
      switch (n.getType()) {
        case Token.NULL:
          n.setJSType(getNativeType(NULL_TYPE));
          break;

        case Token.VOID:
          n.setJSType(getNativeType(VOID_TYPE));
          break;

        case Token.STRING:
          n.setJSType(getNativeType(STRING_TYPE));
          break;

        case Token.NUMBER:
          n.setJSType(getNativeType(NUMBER_TYPE));
          break;

        case Token.TRUE:
        case Token.FALSE:
          n.setJSType(getNativeType(BOOLEAN_TYPE));
          break;

        case Token.REGEXP:
          n.setJSType(getNativeType(REGEXP_TYPE));
          break;

        case Token.OBJECTLIT:
          JSDocInfo info = n.getJSDocInfo();
          if (info != null &&
              info.getLendsName() != null) {
            if (lentObjectLiterals == null) {
              lentObjectLiterals = Lists.newArrayList();
            }
            lentObjectLiterals.add(n);
          } else {
            defineObjectLiteral(n);
          }
          break;

        // NOTE(johnlenz): If we ever support Array tuples,
        // we will need to handle them here as we do object literals
        // above.
        case Token.ARRAYLIT:
          n.setJSType(getNativeType(ARRAY_TYPE));
          break;
      }
    }

    private void defineObjectLiteral(Node objectLit) {
      // Handle the @lends annotation.
      JSType type = null;
      JSDocInfo info = objectLit.getJSDocInfo();
      if (info != null && info.getLendsName() != null) {
        String lendsName = info.getLendsName();
        TypedVar lendsVar = scope.getVar(lendsName);
        if (lendsVar == null) {
          compiler.report(
              JSError.make(objectLit, UNKNOWN_LENDS, lendsName));
        } else {
          type = lendsVar.getType();
          if (type == null) {
            type = unknownType;
          }
          if (!type.isSubtype(typeRegistry.getNativeType(OBJECT_TYPE))) {
            compiler.report(
                JSError.make(objectLit, LENDS_ON_NON_OBJECT,
                    lendsName, type.toString()));
            type = null;
          } else {
            objectLit.setJSType(type);
          }
        }
      }

      info = NodeUtil.getBestJSDocInfo(objectLit);
      Node lValue = NodeUtil.getBestLValue(objectLit);
      String lValueName = NodeUtil.getBestLValueName(lValue);
      boolean createdEnumType = false;
      if (info != null && info.hasEnumParameterType()) {
        type = createEnumTypeFromNodes(objectLit, lValueName, info, lValue);
        createdEnumType = true;
      }

      if (type == null) {
        type = typeRegistry.createAnonymousObjectType(info);
      }

      setDeferredType(objectLit, type);

      // If this is an enum, the properties were already taken care of above.
      processObjectLitProperties(
          objectLit, ObjectType.cast(objectLit.getJSType()), !createdEnumType);
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
      for (Node keyNode = objLit.getFirstChild(); keyNode != null;
           keyNode = keyNode.getNext()) {
        Node value = keyNode.getFirstChild();
        String memberName = NodeUtil.getObjectLitKeyName(keyNode);
        JSDocInfo info = keyNode.getJSDocInfo();
        JSType valueType = getDeclaredType(info, keyNode, value);
        JSType keyType =  objLitType.isEnumType() ?
            objLitType.toMaybeEnumType().getElementsType() :
            TypeCheck.getObjectLitKeyTypeFromValueType(keyNode, valueType);

        // Try to declare this property in the current scope if it
        // has an authoritative name.
        String qualifiedName = NodeUtil.getBestLValueName(keyNode);
        if (qualifiedName != null) {
          boolean inferred = keyType == null;
          defineSlot(keyNode, objLit, qualifiedName, keyType, inferred);
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
     * Returns the type specified in a JSDoc annotation near a GETPROP or NAME.
     *
     * Extracts type information from either the {@code @type} tag or from
     * the {@code @return} and {@code @param} tags.
     */
    private JSType getDeclaredTypeInAnnotation(Node node, JSDocInfo info) {
      JSType jsType = null;
      if (info != null) {
        if (info.hasType()) {

          ImmutableList<TemplateType> ownerTypeKeys = ImmutableList.of();
          Node ownerNode = NodeUtil.getBestLValueOwner(node);
          String ownerName = NodeUtil.getBestLValueName(ownerNode);
          ObjectType ownerType = null;
          if (ownerName != null) {
            TypedVar ownerVar = scope.getVar(ownerName);
            if (ownerVar != null) {
              ownerType = getPrototypeOwnerType(
                  ObjectType.cast(ownerVar.getType()));
              if (ownerType != null) {
                ownerTypeKeys =
                    ownerType.getTemplateTypeMap().getTemplateKeys();
              }
            }
          }

          if (!ownerTypeKeys.isEmpty()) {
            typeRegistry.setTemplateTypeNames(ownerTypeKeys);
          }

          jsType = info.getType().evaluate(scope, typeRegistry);

          if (!ownerTypeKeys.isEmpty()) {
            typeRegistry.clearTemplateTypeNames();
          }
        } else if (FunctionTypeBuilder.isFunctionTypeDeclaration(info)) {
          String fnName = node.getQualifiedName();
          jsType = createFunctionTypeFromNodes(
              null, fnName, info, node);
        }
      }
      return jsType;
    }

    /**
     * Asserts that it's OK to define this node's name.
     * The node should have a source name and be of the specified type.
     */
    void assertDefinitionNode(Node n, int type) {
      Preconditions.checkState(sourceName != null);
      Preconditions.checkState(n.getType() == type);
    }

    /**
     * Defines a catch parameter.
     */
    void defineCatch(Node n) {
      assertDefinitionNode(n, Token.CATCH);
      Node catchName = n.getFirstChild();
      defineSlot(catchName, n,
          getDeclaredType(
              catchName.getJSDocInfo(), catchName, null));
    }

    /**
     * Defines a VAR initialization.
     */
    void defineVar(Node n) {
      assertDefinitionNode(n, Token.VAR);
      JSDocInfo info = n.getJSDocInfo();
      if (n.hasMoreThanOneChild()) {
        if (info != null) {
          // multiple children
          compiler.report(JSError.make(n, MULTIPLE_VAR_DEF));
        }
        for (Node name : n.children()) {
          defineName(name, n, name.getJSDocInfo());
        }
      } else {
        Node name = n.getFirstChild();
        defineName(name, n, (info != null) ? info : name.getJSDocInfo());
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
      FunctionType functionType =
          createFunctionTypeFromNodes(n, functionName, info, lValue);

      // Assigning the function type to the function node
      setDeferredType(n, functionType);

      // Declare this symbol in the current scope iff it's a function
      // declaration. Otherwise, the declaration will happen in other
      // code paths.
      if (NodeUtil.isFunctionDeclaration(n)) {
        defineSlot(n.getFirstChild(), n, functionType);
      }
    }

    /**
     * Defines a variable based on the {@link Token#NAME} node passed.
     * @param name The {@link Token#NAME} node.
     * @param var The parent of the {@code name} node, which must be a
     *     {@link Token#VAR} node.
     * @param info the {@link JSDocInfo} information relating to this
     *     {@code name} node.
     */
    private void defineName(Node name, Node var, JSDocInfo info) {
      Node value = name.getFirstChild();

      // variable's type
      JSType type = getDeclaredType(info, name, value);
      if (type == null) {
        // The variable's type will be inferred.
        type = name.isFromExterns() ? unknownType : null;
      }
      defineSlot(name, var, type);
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
      if (lValue != null &&
          NodeUtil.isObjectLitKey(lValue)) {
        return false;
      }
      return scope.isGlobal() || !type.isReturnTypeInferred();
    }

    /**
     * Creates a new function type, based on the given nodes.
     *
     * This handles two cases that are semantically very different, but
     * are not mutually exclusive:
     * - A function literal that needs a type attached to it.
     * - An assignment expression with function-type info in the JsDoc.
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
      FunctionType functionType = null;
      if (rValue != null && rValue.isQualifiedName() && scope.isGlobal()) {
        TypedVar var = scope.getVar(rValue.getQualifiedName());
        if (var != null && var.getType() != null &&
            var.getType().isFunctionType()) {
          FunctionType aliasedType  = var.getType().toMaybeFunctionType();
          if ((aliasedType.isConstructor() || aliasedType.isInterface())
              && !isGoogAbstractMethod(rValue)) {
            functionType = aliasedType;

            // TODO(nick): Remove this. This should already be handled by
            // normal type resolution.
            if (name != null && scope.isGlobal()) {
              typeRegistry.declareType(name, functionType.getInstanceType());
            }
          }
        }
      }

      if (functionType == null) {
        Node errorRoot = rValue == null ? lvalueNode : rValue;
        boolean isFnLiteral =
            rValue != null && rValue.isFunction();
        Node fnRoot = isFnLiteral ? rValue : null;
        Node parametersNode = isFnLiteral ?
            rValue.getFirstChild().getNext() : null;

        if (info != null && info.hasType()) {
          JSType type = info.getType().evaluate(scope, typeRegistry);

          // Known to be not null since we have the FUNCTION token there.
          type = type.restrictByNotNullOrUndefined();
          if (type.isFunctionType()) {
            functionType = type.toMaybeFunctionType();
            functionType.setJSDocInfo(info);
          }
        }

        if (functionType == null) {
          // Find the type of any overridden function.
          Node ownerNode = NodeUtil.getBestLValueOwner(lvalueNode);
          String ownerName = NodeUtil.getBestLValueName(ownerNode);
          TypedVar ownerVar = null;
          String propName = null;
          ObjectType ownerType = null;
          if (ownerName != null) {
            ownerVar = scope.getVar(ownerName);
            if (ownerVar != null) {
              ownerType = ObjectType.cast(ownerVar.getType());
            }
            if (name != null) {
              propName = name.substring(ownerName.length() + 1);
            }
          }

          ObjectType prototypeOwner = getPrototypeOwnerType(ownerType);
          TemplateTypeMap prototypeOwnerTypeMap = null;
          if (prototypeOwner != null &&
              prototypeOwner.getTypeOfThis() != null) {
            prototypeOwnerTypeMap =
                prototypeOwner.getTypeOfThis().getTemplateTypeMap();
          }

          FunctionType overriddenType = null;
          if (ownerType != null && propName != null) {
            overriddenType = findOverriddenFunction(
                ownerType, propName, prototypeOwnerTypeMap);
          }

          FunctionTypeBuilder builder =
              new FunctionTypeBuilder(name, compiler, errorRoot,
                  scope)
              .setContents(getFunctionAnalysisResults(fnRoot))
              .inferFromOverriddenFunction(overriddenType, parametersNode)
              .inferTemplateTypeName(info, prototypeOwner)
              .inferInheritance(info);

          if (info == null || !info.hasReturnType()) {
            /**
             * when there is no {@code @return} annotation, look for inline
             * return type declaration
             */
            if (rValue != null && rValue.isFunction() &&
                rValue.getFirstChild() != null) {
              JSDocInfo nameDocInfo = rValue.getFirstChild().getJSDocInfo();
              builder.inferReturnType(nameDocInfo, true);
            }
          } else {
            builder.inferReturnType(info, false);
          }

          // Infer the context type.
          boolean searchedForThisType = false;
          if (ownerType != null && ownerType.isFunctionPrototypeType() &&
              ownerType.getOwnerFunction().hasInstanceType()) {
            builder.inferThisType(
                info, ownerType.getOwnerFunction().getInstanceType());
            searchedForThisType = true;
          } else if (ownerNode != null && ownerNode.isThis()) {
            // If we have a 'this' node, use the scope type.
            builder.inferThisType(info, scope.getTypeOfThis());
            searchedForThisType = true;
          }

          if (!searchedForThisType) {
            builder.inferThisType(info);
          }

          functionType = builder
              .inferParameterTypes(parametersNode, info)
              .buildAndRegister();
        }
      }

      // all done
      return functionType;
    }

    /**
     * We have to special-case goog.abstractMethod in createFunctionTypeFromNodes,
     * because some people use it (incorrectly) for interfaces:
     *
     * /* @interface * /
     * var example.MyInterface = goog.abstractMethod;
     */
    private boolean isGoogAbstractMethod(Node n) {
      return n.matchesQualifiedName("goog.abstractMethod");
    }

    private ObjectType getPrototypeOwnerType(ObjectType ownerType) {
      if (ownerType != null && ownerType.isFunctionPrototypeType()) {
        return ownerType.getOwnerFunction();
      }
      return null;
    }

    /**
     * Find the function that's being overridden on this type, if any.
     */
    private FunctionType findOverriddenFunction(
        ObjectType ownerType, String propName, TemplateTypeMap typeMap) {
      FunctionType result = null;

      // First, check to see if the property is implemented
      // on a superclass.
      JSType propType = ownerType.getPropertyType(propName);
      if (propType != null && propType.isFunctionType()) {
        result =  propType.toMaybeFunctionType();
      } else {
        // If it's not, then check to see if it's implemented
        // on an implemented interface.
        for (ObjectType iface :
                 ownerType.getCtorImplementedInterfaces()) {
          propType = iface.getPropertyType(propName);
          if (propType != null && propType.isFunctionType()) {
            result = propType.toMaybeFunctionType();
            break;
          }
        }
      }

      if (result != null && typeMap != null && !typeMap.isEmpty()) {
        result = result.visit(
            new TemplateTypeMapReplacer(typeRegistry, typeMap))
            .toMaybeFunctionType();
      }

      return result;
    }

    /**
     * Creates a new enum type, based on the given nodes.
     *
     * This handles two cases that are semantically very different, but
     * are not mutually exclusive:
     * - An object literal that needs an enum type attached to it.
     * - An assignment expression with an enum tag in the JsDoc.
     *
     * This function will always create an enum type, so only call it if
     * you're sure that's what you want.
     *
     * @param rValue The node of the enum.
     * @param name The enum's name
     * @param info The {@link JSDocInfo} attached to the enum definition.
     * @param lValueNode The node where this function is being
     *     assigned.
     */
    private EnumType createEnumTypeFromNodes(Node rValue, String name,
        JSDocInfo info, Node lValueNode) {
      Preconditions.checkNotNull(info);
      Preconditions.checkState(info.hasEnumParameterType());

      EnumType enumType = null;
      if (rValue != null && rValue.isQualifiedName()) {
        // Handle an aliased enum.
        TypedVar var = scope.getVar(rValue.getQualifiedName());
        if (var != null && var.getType() instanceof EnumType) {
          enumType = (EnumType) var.getType();
        }
      }

      if (enumType == null) {
        JSType elementsType =
            info.getEnumParameterType().evaluate(scope, typeRegistry);
        enumType = typeRegistry.createEnumType(name, rValue, elementsType);

        if (rValue != null && rValue.isObjectLit()) {
          // collect enum elements
          Node key = rValue.getFirstChild();
          while (key != null) {
            String keyName = NodeUtil.getStringValue(key);
            if (keyName == null) {
              // GET and SET don't have a String value;
              compiler.report(
                  JSError.make(key, ENUM_NOT_CONSTANT, keyName));
            } else if (!codingConvention.isValidEnumKey(keyName)) {
              compiler.report(
                  JSError.make(key, ENUM_NOT_CONSTANT, keyName));
            } else {
              enumType.defineElement(keyName, key);
            }
            key = key.getNext();
          }
        }
      }

      if (name != null && scope.isGlobal()) {
        typeRegistry.declareType(name, enumType.getElementsType());
      }

      return enumType;
    }

    /**
     * Defines a typed variable. The defining node will be annotated with the
     * variable's type or {@code null} if its type is inferred.
     * @param name the defining node. It must be a {@link Token#NAME}.
     * @param parent the {@code name}'s parent.
     * @param type the variable's type. It may be {@code null}, in which case
     *     the variable's type will be inferred.
     */
    private void defineSlot(Node name, Node parent, JSType type) {
      defineSlot(name, parent, type, type == null);
    }

    /**
     * Defines a typed variable. The defining node will be annotated with the
     * variable's type of {@link JSTypeNative#UNKNOWN_TYPE} if its type is
     * inferred.
     *
     * Slots may be any variable or any qualified name in the global scope.
     *
     * @param n the defining NAME or GETPROP node.
     * @param parent the {@code n}'s parent.
     * @param type the variable's type. It may be {@code null} if
     *     {@code inferred} is {@code true}.
     */
    void defineSlot(Node n, Node parent, JSType type, boolean inferred) {
      Preconditions.checkArgument(inferred || type != null);

      // Only allow declarations of NAMEs and qualified names.
      // Object literal keys will have to compute their names themselves.
      if (n.isName()) {
        Preconditions.checkArgument(
            parent.isFunction() ||
            parent.isVar() ||
            parent.isParamList() ||
            parent.isCatch());
      } else {
        Preconditions.checkArgument(
            n.isGetProp() &&
            (parent.isAssign() ||
             parent.isExprResult()));
      }
      defineSlot(n, parent, n.getQualifiedName(), type, inferred);
    }


    /**
     * Defines a symbol in the current scope.
     *
     * @param n the defining NAME or GETPROP or object literal key node.
     * @param parent the {@code n}'s parent.
     * @param variableName The name that this should be known by.
     * @param type the variable's type. It may be {@code null} if
     *     {@code inferred} is {@code true}.
     * @param inferred Whether the type is inferred or declared.
     */
    void defineSlot(Node n, Node parent, String variableName,
        JSType type, boolean inferred) {
      Preconditions.checkArgument(!variableName.isEmpty());

      boolean isGlobalVar = n.isName() && scope.isGlobal();
      boolean shouldDeclareOnGlobalThis =
          isGlobalVar &&
          (parent.isVar() ||
           parent.isFunction());

      // If n is a property, then we should really declare it in the
      // scope where the root object appears. This helps out people
      // who declare "global" names in an anonymous namespace.
      TypedScope scopeToDeclareIn = scope;
      if (n.isGetProp() && !scope.isGlobal() &&
          isQnameRootedInGlobalScope(n)) {
        TypedScope globalScope = scope.getGlobalScope();

        // don't try to declare in the global scope if there's
        // already a symbol there with this name.
        if (!globalScope.isDeclared(variableName, false)) {
          scopeToDeclareIn = scope.getGlobalScope();
        }
      }

      // The input may be null if we are working with a AST snippet. So read
      // the extern info from the node.
      TypedVar newVar = null;

      // declared in closest scope?
      CompilerInput input = compiler.getInput(inputId);
      if (scopeToDeclareIn.isDeclared(variableName, false)) {
        TypedVar oldVar = scopeToDeclareIn.getVar(variableName);
        newVar = validator.expectUndeclaredVariable(
            sourceName, input, n, parent, oldVar, variableName, type);
      } else {
        if (type != null) {
          setDeferredType(n, type);
        }

        newVar =
          scopeToDeclareIn.declare(variableName, n, type, input, inferred);

        if (type instanceof EnumType) {
          Node initialValue = newVar.getInitialValue();
          boolean isValidValue = initialValue != null &&
              (initialValue.isObjectLit() ||
               initialValue.isQualifiedName());
          if (!isValidValue) {
            compiler.report(JSError.make(n, ENUM_INITIALIZER));
          }
        }
      }

      // We need to do some additional work for constructors and interfaces.
      FunctionType fnType = JSType.toMaybeFunctionType(type);
      if (fnType != null &&
          // We don't want to look at empty function types.
          !type.isEmptyType()) {

        // We want to make sure that when we declare a new instance type
        // (with @constructor) that there's actually a ctor for it.
        // This doesn't apply to structural constructors (like
        // function(new:Array). Checking the constructed type against
        // the variable name is a sufficient check for this.
        if ((fnType.isConstructor() || fnType.isInterface()) &&
            variableName.equals(fnType.getReferenceName())) {
          finishConstructorDefinition(n, variableName, fnType, scopeToDeclareIn,
                                      input, newVar);
        }
      }

      if (shouldDeclareOnGlobalThis) {
        ObjectType globalThis =
            typeRegistry.getNativeObjectType(GLOBAL_THIS);
        if (inferred) {
          globalThis.defineInferredProperty(variableName,
              type == null ?
              getNativeType(JSTypeNative.NO_TYPE) :
              type,
              n);
        } else {
          globalThis.defineDeclaredProperty(variableName, type, n);
        }
      }

      if (isGlobalVar && "Window".equals(variableName)
          && type != null
          && type.isFunctionType()
          && type.isConstructor()) {
        FunctionType globalThisCtor =
            typeRegistry.getNativeObjectType(GLOBAL_THIS).getConstructor();
        globalThisCtor.getInstanceType().clearCachedValues();
        globalThisCtor.getPrototype().clearCachedValues();
        globalThisCtor
            .setPrototypeBasedOn((type.toMaybeFunctionType()).getInstanceType());
      }
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

      scopeToDeclareIn.declare(prototypeName,
          n, prototypeSlot.getType(), input,
          /* declared iff there's an explicit supertype */
          superClassCtor == null ||
          superClassCtor.getInstanceType().isEquivalentTo(
              getNativeType(OBJECT_TYPE)));

      // Make sure the variable is initialized to something if
      // it constructs itself.
      if (newVar.getInitialValue() == null &&
          !n.isFromExterns()) {
        compiler.report(
            JSError.make(n,
                fnType.isConstructor() ?
                CTOR_INITIALIZER : IFACE_INITIALIZER,
                variableName));
      }
    }

    /**
     * Check if the given node is a property of a name in the global scope.
     */
    private boolean isQnameRootedInGlobalScope(Node n) {
      TypedScope scope = getQnameRootScope(n);
      return scope != null && scope.isGlobal();
    }

    /**
     * Return the scope for the name of the given node.
     */
    private TypedScope getQnameRootScope(Node n) {
      Node root = NodeUtil.getRootOfQualifiedName(n);
      if (root.isName()) {
        TypedVar var = scope.getVar(root.getString());
        if (var != null) {
          return var.getScope();
        }
      }
      return null;
    }

    /**
     * Look for a type declaration on a property assignment
     * (in an ASSIGN or an object literal key).
     * @param info The doc info for this property.
     * @param lValue The l-value node.
     * @param rValue The node that {@code n} is being initialized to,
     *     or {@code null} if this is a stub declaration.
     */
    JSType getDeclaredType(JSDocInfo info, Node lValue,
        @Nullable Node rValue) {
      if (info != null && info.hasType()) {
        return getDeclaredTypeInAnnotation(lValue, info);
      } else if (rValue != null && rValue.isFunction() &&
          shouldUseFunctionLiteralType(
              JSType.toMaybeFunctionType(rValue.getJSType()), info, lValue)) {
        return rValue.getJSType();
      } else if (info != null) {
        if (info.hasEnumParameterType()) {
          if (rValue != null && rValue.isObjectLit()) {
            return rValue.getJSType();
          } else {
            return createEnumTypeFromNodes(
                rValue, lValue.getQualifiedName(), info, lValue);
          }
        } else if (info.isConstructor() || info.isInterface()) {
          return createFunctionTypeFromNodes(
              rValue, lValue.getQualifiedName(), info, lValue);
        }
      }

      // Check if this is constant, and if it has a known type.
      if (NodeUtil.isConstantDeclaration(
              compiler.getCodingConvention(), info, lValue)) {
        if (rValue != null) {
          JSType rValueType = getDeclaredRValueType(lValue, rValue);
          if (rValueType == null) {
            // Only warn if the user has explicitly declared a value as
            // const and they have explicitly not provided a type.
            boolean isTypelessConstDecl = info != null
                && info.isConstant()
                && !info.hasType();
            if (isTypelessConstDecl) {
              compiler.report(JSError.make(lValue, CANNOT_INFER_CONST_TYPE));
            }
          } else {
            return rValueType;
          }
        }
      }

      return getDeclaredTypeInAnnotation(lValue, info);
    }

    /**
     * Check for common idioms of a typed R-value assigned to a const L-value.
     *
     * Normally, we would only want this sort of propagation to happen under
     * type inference. But we want a declared const to be nameable in a type
     * annotation, so we need to figure out the type before we try to resolve
     * the annotation.
     */
    private JSType getDeclaredRValueType(Node lValue, Node rValue) {
      // If rValue has a type-cast, we use the type in the type-cast.
      JSDocInfo rValueInfo = rValue.getJSDocInfo();
      if (rValue.isCast() && rValueInfo != null && rValueInfo.hasType()) {
        return rValueInfo.getType().evaluate(scope, typeRegistry);
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
        return lookupQualifiedName(rValue);
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
        JSType targetType = lookupQualifiedName(rValue.getFirstChild());
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
        boolean namesMatch = firstClause.isName()
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

    private JSType lookupQualifiedName(Node n) {
      String name = n.getQualifiedName();
      TypedVar slot = scope.getVar(name);
      if (slot != null && !slot.isTypeInferred()) {
        JSType type = slot.getType();
        if (type != null && !type.isUnknownType()) {
          return type;
        }
      } else if (n.isGetProp()) {
        JSType type = lookupQualifiedName(n.getFirstChild());
        if (type != null && type.isRecordType()) {
          JSType propType = type.findPropertyType(
             n.getLastChild().getString());
          return propType;
        }
      }
      return null;
    }

    /**
     * Look for calls that set a delegate method's calling convention.
     */
    private void checkForCallingConventionDefiningCalls(
        Node n, Map<String, String> delegateCallingConventions) {
      codingConvention.checkForCallingConventionDefiningCalls(n,
          delegateCallingConventions);
    }

    /**
     * Look for class-defining calls.
     * Because JS has no 'native' syntax for defining classes,
     * this is often very coding-convention dependent and business-logic heavy.
     */
    private void checkForClassDefiningCalls(Node n) {
      SubclassRelationship relationship =
          codingConvention.getClassesDefinedByCall(n);
      if (relationship != null) {
        ObjectType superClass = TypeValidator.getInstanceOfCtor(
            scope.getVar(relationship.superclassName));
        ObjectType subClass = TypeValidator.getInstanceOfCtor(
            scope.getVar(relationship.subclassName));
        if (superClass != null && subClass != null) {
          // superCtor and subCtor might be structural constructors
          // (like {function(new:Object)}) so we need to resolve them back
          // to the original ctor objects.
          FunctionType superCtor = superClass.getConstructor();
          FunctionType subCtor = subClass.getConstructor();
          if (superCtor != null && subCtor != null) {
            codingConvention.applySubclassRelationship(
                superCtor, subCtor, relationship.type);
          }
        }
      }

      String singletonGetterClassName =
          codingConvention.getSingletonGetterClassName(n);
      if (singletonGetterClassName != null) {
        ObjectType objectType = ObjectType.cast(
            typeRegistry.getType(singletonGetterClassName));
        if (objectType != null) {
          FunctionType functionType = objectType.getConstructor();

          if (functionType != null) {
            FunctionType getterType =
                typeRegistry.createFunctionType(objectType);
            codingConvention.applySingletonGetter(
                functionType, getterType, objectType);
          }
        }
      }

      DelegateRelationship delegateRelationship =
          codingConvention.getDelegateRelationship(n);
      if (delegateRelationship != null) {
        applyDelegateRelationship(delegateRelationship);
      }

      ObjectLiteralCast objectLiteralCast =
          codingConvention.getObjectLiteralCast(n);
      if (objectLiteralCast != null) {
        if (objectLiteralCast.diagnosticType == null) {
          ObjectType type = ObjectType.cast(
              typeRegistry.getType(objectLiteralCast.typeName));
          if (type != null && type.getConstructor() != null) {
            setDeferredType(objectLiteralCast.objectNode, type);
            objectLiteralCast.objectNode.putBooleanProp(
                Node.REFLECTED_OBJECT, true);
          } else {
            compiler.report(JSError.make(n, CONSTRUCTOR_EXPECTED));
          }
        } else {
          compiler.report(JSError.make(n, objectLiteralCast.diagnosticType));
        }
      }
    }

    /**
     * Apply special properties that only apply to delegates.
     */
    private void applyDelegateRelationship(
        DelegateRelationship delegateRelationship) {
      ObjectType delegatorObject = ObjectType.cast(
          typeRegistry.getType(delegateRelationship.delegator));
      ObjectType delegateBaseObject = ObjectType.cast(
          typeRegistry.getType(delegateRelationship.delegateBase));
      ObjectType delegateSuperObject = ObjectType.cast(
          typeRegistry.getType(codingConvention.getDelegateSuperclassName()));
      if (delegatorObject != null &&
          delegateBaseObject != null &&
          delegateSuperObject != null) {
        FunctionType delegatorCtor = delegatorObject.getConstructor();
        FunctionType delegateBaseCtor = delegateBaseObject.getConstructor();
        FunctionType delegateSuperCtor = delegateSuperObject.getConstructor();

        if (delegatorCtor != null && delegateBaseCtor != null &&
            delegateSuperCtor != null) {
          FunctionParamBuilder functionParamBuilder =
              new FunctionParamBuilder(typeRegistry);
          functionParamBuilder.addRequiredParams(
              getNativeType(U2U_CONSTRUCTOR_TYPE));
          FunctionType findDelegate = typeRegistry.createFunctionType(
              typeRegistry.createDefaultObjectUnion(delegateBaseObject),
              functionParamBuilder.build());

          FunctionType delegateProxy = typeRegistry.createConstructorType(
              delegateBaseObject.getReferenceName() + DELEGATE_PROXY_SUFFIX,
              null, null, null, null);
          delegateProxy.setPrototypeBasedOn(delegateBaseObject);

          codingConvention.applyDelegateRelationship(
              delegateSuperObject, delegateBaseObject, delegatorObject,
              delegateProxy, findDelegate);
          delegateProxyPrototypes.add(delegateProxy.getPrototype());
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
      Node ownerNode = n.getFirstChild();
      String ownerName = ownerNode.getQualifiedName();
      String qName = n.getQualifiedName();
      String propName = n.getLastChild().getString();
      Preconditions.checkArgument(qName != null && ownerName != null);

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
      JSType valueType = getDeclaredType(info, n, rhsValue);
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
        TypedVar qVar = scope.getVar(qName);
        if (qVar != null) {
          // If the programmer has declared that F inherits from Super,
          // and they assign F.prototype to an object literal,
          // then they are responsible for making sure that the object literal's
          // implicit prototype is set up appropriately. We just obey
          // the @extends tag.
          ObjectType qVarType = ObjectType.cast(qVar.getType());
          if (qVarType != null &&
              rhsValue != null &&
              rhsValue.isObjectLit()) {
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
          stubDeclarations.add(new StubDeclaration(
              n,
              t.getInput() != null && t.getInput().isExtern(),
              ownerName));
        }

        return;
      }

      boolean inferred = isQualifiedNameInferred(
          qName, n, info, rhsValue, valueType);
      if (!inferred) {
        ObjectType ownerType = getObjectSlot(ownerName);
        if (ownerType != null) {
          // Only declare this as an official property if it has not been
          // declared yet.
          boolean isExtern = t.getInput() != null && t.getInput().isExtern();
          if ((!ownerType.hasOwnProperty(propName) ||
               ownerType.isPropertyTypeInferred(propName)) &&
              ((isExtern && !ownerType.isNativeObjectType()) ||
               !ownerType.isInstanceType())) {
            // If the property is undeclared or inferred, declare it now.
            ownerType.defineDeclaredProperty(propName, valueType, n);
          }
        }

        // If the property is already declared, the error will be
        // caught when we try to declare it in the current scope.
        defineSlot(n, parent, valueType, inferred);
      } else if (rhsValue != null && rhsValue.isTrue()) {
        // We declare these for delegate proxy method properties.
        ObjectType ownerType = getObjectSlot(ownerName);
        FunctionType ownerFnType = JSType.toMaybeFunctionType(ownerType);
        if (ownerFnType != null) {
          JSType ownerTypeOfThis = ownerFnType.getTypeOfThis();
          String delegateName = codingConvention.getDelegateSuperclassName();
          JSType delegateType = delegateName == null ?
              null : typeRegistry.getType(delegateName);
          if (delegateType != null &&
              ownerTypeOfThis.isSubtype(delegateType)) {
            defineSlot(n, parent, getNativeType(BOOLEAN_TYPE), true);
          }
        }
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
        String qName, Node n, JSDocInfo info,
        Node rhsValue, JSType valueType) {
      if (valueType == null) {
        return true;
      }

      // Prototypes of constructors and interfaces are always declared.
      if (qName != null && qName.endsWith(".prototype")) {
        String className = qName.substring(0, qName.lastIndexOf(".prototype"));
        TypedVar slot = scope.getSlot(className);
        JSType classType = slot == null ? null : slot.getType();
        if (classType != null
            && (classType.isConstructor() || classType.isInterface())) {
          return false;
        }
      }

      boolean inferred = true;
      if (info != null) {
        inferred = !(info.hasType()
            || info.hasEnumParameterType()
            || (NodeUtil.isConstantDeclaration(
                    compiler.getCodingConvention(), info, n)
                && valueType != null
                && !valueType.isUnknownType())
            || FunctionTypeBuilder.isFunctionTypeDeclaration(info));
      }

      if (inferred && rhsValue != null && rhsValue.isFunction()) {
        if (info != null) {
          return false;
        } else if (!scope.isDeclared(qName, false) &&
            n.isUnscopedQualifiedName()) {

          // Check if this is in a conditional block.
          // Functions assigned in conditional blocks are inferred.
          for (Node current = n.getParent();
               !(current.isScript() || current.isFunction());
               current = current.getParent()) {
            if (NodeUtil.isControlStructure(current)) {
              return true;
            }
          }

          // Check if this is assigned in an inner scope.
          // Functions assigned in inner scopes are inferred.
          AstFunctionContents contents =
              getFunctionAnalysisResults(scope.getRootNode());
          if (contents == null ||
              !contents.getEscapedQualifiedNames().contains(qName)) {
            return false;
          }
        }
      }
      return inferred;
    }

    /**
     * Find the ObjectType associated with the given slot.
     * @param slotName The name of the slot to find the type in.
     * @return An object type, or null if this slot does not contain an object.
     */
    private ObjectType getObjectSlot(String slotName) {
      TypedVar ownerVar = scope.getVar(slotName);
      if (ownerVar != null) {
        JSType ownerVarType = ownerVar.getType();
        return ObjectType.cast(ownerVarType == null ?
            null : ownerVarType.restrictByNotNullOrUndefined());
      }
      return null;
    }

    /**
     * Resolve any stub declarations to unknown types if we could not
     * find types for them during traversal.
     */
    void resolveStubDeclarations() {
      for (StubDeclaration stub : stubDeclarations) {
        Node n = stub.node;
        Node parent = n.getParent();
        String qName = n.getQualifiedName();
        String propName = n.getLastChild().getString();
        String ownerName = stub.ownerName;
        boolean isExtern = stub.isExtern;

        if (scope.isDeclared(qName, false)) {
          continue;
        }

        // If we see a stub property, make sure to register this property
        // in the type registry.
        ObjectType ownerType = getObjectSlot(ownerName);
        defineSlot(n, parent, unknownType, true);

        if (ownerType != null &&
            (isExtern || ownerType.isFunctionPrototypeType())) {
          // If this is a stub for a prototype, just declare it
          // as an unknown type. These are seen often in externs.
          ownerType.defineInferredProperty(
              propName, unknownType, n);
        } else {
          typeRegistry.registerPropertyOnType(
              propName, ownerType == null ? unknownType : ownerType);
        }
      }
    }
  }

  /**
   * A stub declaration without any type information.
   */
  private static final class StubDeclaration {
    private final Node node;
    private final boolean isExtern;
    private final String ownerName;

    private StubDeclaration(Node node, boolean isExtern, String ownerName) {
      this.node = node;
      this.isExtern = isExtern;
      this.ownerName = ownerName;
    }
  }

  /**
   * A shallow traversal of the global scope to build up all classes,
   * functions, and methods.
   */
  private final class GlobalScopeBuilder extends AbstractScopeBuilder {

    private GlobalScopeBuilder(TypedScope scope) {
      super(scope);
    }

    /**
     * Visit a node in the global scope, and add anything it declares to the
     * global symbol table.
     *
     * @param t The current traversal.
     * @param n The node being visited.
     * @param parent The parent of n
     */
    @Override public void visit(NodeTraversal t, Node n, Node parent) {
      super.visit(t, n, parent);

      switch (n.getType()) {

        case Token.VAR:
          // Handle typedefs.
          if (n.hasOneChild()) {
            checkForTypedef(n.getFirstChild(), n.getJSDocInfo());
          }
          break;
      }
    }

    @Override
    void maybeDeclareQualifiedName(
        NodeTraversal t, JSDocInfo info,
        Node n, Node parent, Node rhsValue) {
      checkForTypedef(n, info);
      super.maybeDeclareQualifiedName(t, info, n, parent, rhsValue);
    }

    /**
     * Handle typedefs.
     * @param candidate A qualified name node.
     * @param info JSDoc comments.
     */
    private void checkForTypedef(Node candidate, JSDocInfo info) {
      if (info == null || !info.hasTypedefType()) {
        return;
      }

      String typedef = candidate.getQualifiedName();
      if (typedef == null) {
        return;
      }

      // TODO(nicksantos|user): This is a terrible, terrible hack
      // to bail out on recursive typedefs. We'll eventually need
      // to handle these properly.
      typeRegistry.declareType(typedef, unknownType);

      JSType realType = info.getTypedefType().evaluate(scope, typeRegistry);
      if (realType == null) {
        compiler.report(
            JSError.make(candidate, MALFORMED_TYPEDEF, typedef));
      }

      typeRegistry.overwriteDeclaredType(typedef, realType);
      if (candidate.isGetProp()) {
        defineSlot(candidate, candidate.getParent(),
            getNativeType(NO_TYPE), false);
      }
    }
  } // end GlobalScopeBuilder

  /**
   * A shallow traversal of a local scope to find all arguments and
   * local variables.
   */
  private final class LocalScopeBuilder extends AbstractScopeBuilder {
    private final ObjectType thisTypeForProperties;

    /**
     * @param scope The scope that we're building.
     */
    private LocalScopeBuilder(TypedScope scope) {
      super(scope);
      thisTypeForProperties = getThisTypeForCollectingProperties();
    }

    /**
     * Traverse the scope root and build it.
     */
    void build() {
      NodeTraversal.traverseTyped(compiler, scope.getRootNode(), this);

      AstFunctionContents contents =
          getFunctionAnalysisResults(scope.getRootNode());
      if (contents != null) {
        for (String varName : contents.getEscapedVarNames()) {
          TypedVar v = scope.getVar(varName);
          Preconditions.checkState(v.getScope() == scope);
          v.markEscaped();
        }

        for (Multiset.Entry<String> entry :
                 contents.getAssignedNameCounts().entrySet()) {
          TypedVar v = scope.getVar(entry.getElement());
          Preconditions.checkState(v.getScope() == scope);
          if (entry.getCount() == 1) {
            v.markAssignedExactlyOnce();
          }
        }
      }
    }

    /**
     * Visit a node in a local scope, and add any local variables or catch
     * parameters into the local symbol table.
     *
     * @param t The node traversal.
     * @param n The node being visited.
     * @param parent The parent of n
     */
    @Override public void visit(NodeTraversal t, Node n, Node parent) {
      if (n == scope.getRootNode()) {
        return;
      }

      if (n.isParamList() && parent == scope.getRootNode()) {
        handleFunctionInputs(parent);
        return;
      }

      // Gather the properties declared in the function,
      // if that function has a @this type that we can
      // build properties on.
      // TODO(nick): It's not clear to me why this is necessary;
      // it appears to be papering over bugs in the main analyzer.
      if (thisTypeForProperties != null && n.getParent().isExprResult()) {
        if (n.isAssign()) {
          maybeCollectMember(n.getFirstChild(), n, n.getLastChild());
        } else if (n.isGetProp()) {
          maybeCollectMember(n, n, null);
        }
      }

      super.visit(t, n, parent);
    }

    private ObjectType getThisTypeForCollectingProperties() {
      Node rootNode = scope.getRootNode();
      if (rootNode.isFromExterns()) return null;

      JSType type = rootNode.getJSType();
      if (type == null || !type.isFunctionType()) return null;

      FunctionType fnType = type.toMaybeFunctionType();
      JSType fnThisType = fnType.getTypeOfThis();
      return fnThisType.isUnknownType() ? null : fnThisType.toObjectType();
    }

    private void maybeCollectMember(Node member,
        Node nodeWithJsDocInfo, @Nullable Node value) {
      JSDocInfo info = nodeWithJsDocInfo.getJSDocInfo();

      // Do nothing if there is no JSDoc type info, or
      // if the node is not a member expression, or
      // if the member expression is not of the form: this.someProperty.
      if (info == null ||
          !member.isGetProp() ||
          !member.getFirstChild().isThis()) {
        return;
      }

      JSType jsType = getDeclaredType(info, member, value);
      Node name = member.getLastChild();
      if (jsType != null) {
        thisTypeForProperties.defineDeclaredProperty(
            name.getString(),
            jsType,
            member);
      }
    }

    /** Handle bleeding functions and function parameters. */
    private void handleFunctionInputs(Node fnNode) {
      // Handle bleeding functions.
      Node fnNameNode = fnNode.getFirstChild();
      String fnName = fnNameNode.getString();
      if (!fnName.isEmpty()) {
        TypedVar fnVar = scope.getVar(fnName);
        if (fnVar == null ||
            // Make sure we're not touching a native function. Native
            // functions aren't bleeding, but may not have a declaration
            // node.
            (fnVar.getNameNode() != null &&
                // Make sure that the function is actually bleeding by checking
                // if has already been declared.
                fnVar.getInitialValue() != fnNode)) {
          defineSlot(fnNameNode, fnNode, fnNode.getJSType(), false);
        }
      }

      declareArguments(fnNode);
    }

    /**
     * Declares all of a function's arguments.
     */
    private void declareArguments(Node functionNode) {
      Node astParameters = functionNode.getFirstChild().getNext();
      Node iifeArgumentNode = null;

      if (NodeUtil.isCallOrNewTarget(functionNode)) {
        iifeArgumentNode = functionNode.getNext();
      }

      FunctionType functionType =
          JSType.toMaybeFunctionType(functionNode.getJSType());
      if (functionType != null) {
        Node jsDocParameters = functionType.getParametersNode();
        if (jsDocParameters != null) {
          Node jsDocParameter = jsDocParameters.getFirstChild();
          for (Node astParameter : astParameters.children()) {
            JSType paramType = jsDocParameter == null ?
                unknownType : jsDocParameter.getJSType();
            boolean inferred = paramType == null || paramType == unknownType;

            if (iifeArgumentNode != null && inferred) {
              String argumentName = iifeArgumentNode.getQualifiedName();
              TypedVar argumentVar =
                  argumentName == null || scope.getParent() == null
                  ? null : scope.getParent().getVar(argumentName);
              if (argumentVar != null && !argumentVar.isTypeInferred()) {
                paramType = argumentVar.getType();
              }
            }

            if (paramType == null) {
              paramType = unknownType;
            }

            defineSlot(astParameter, functionNode, paramType, inferred);

            if (jsDocParameter != null) {
              jsDocParameter = jsDocParameter.getNext();
            }
            if (iifeArgumentNode != null) {
              iifeArgumentNode = iifeArgumentNode.getNext();
            }
          }
        }
      }
    } // end declareArguments
  } // end LocalScopeBuilder

  /**
   * Does a first-order function analysis that just looks at simple things
   * like what variables are escaped, and whether 'this' is used.
   */
  private static class FirstOrderFunctionAnalyzer
      extends AbstractScopedCallback implements CompilerPass {
    private final AbstractCompiler compiler;
    private final Map<Node, AstFunctionContents> data;

    FirstOrderFunctionAnalyzer(
        AbstractCompiler compiler, Map<Node, AstFunctionContents> outParam) {
      this.compiler = compiler;
      this.data = outParam;
    }

    @Override public void process(Node externs, Node root) {
      if (externs == null) {
        NodeTraversal.traverseTyped(compiler, root, this);
      } else {
        NodeTraversal.traverseRootsTyped(compiler, this, externs, root);
      }
    }

    @Override public void enterScope(NodeTraversal t) {
      if (!t.inGlobalScope()) {
        Node n = t.getScopeRoot();
        data.put(n, new AstFunctionContents(n));
      }
    }

    @Override public void visit(NodeTraversal t, Node n, Node parent) {
      if (t.inGlobalScope()) {
        return;
      }

      if (n.isReturn() && n.getFirstChild() != null) {
        data.get(t.getScopeRoot()).recordNonEmptyReturn();
      }

      if (t.getScopeDepth() <= 1) {
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

      if (n.isName() && NodeUtil.isLValue(n) &&
          // Be careful of bleeding functions, which create variables
          // in the inner scope, not the scope where the name appears.
          !NodeUtil.isBleedingFunctionName(n)) {
        String name = n.getString();
        TypedScope scope = t.getTypedScope();
        TypedVar var = scope.getVar(name);
        if (var != null) {
          TypedScope ownerScope = var.getScope();
          if (ownerScope.isLocal()) {
            data.get(ownerScope.getRootNode()).recordAssignedName(name);
          }

          if (scope != ownerScope && ownerScope.isLocal()) {
            data.get(ownerScope.getRootNode()).recordEscapedVarName(name);
          }
        }
      } else if (n.isGetProp() && n.isUnscopedQualifiedName() &&
          NodeUtil.isLValue(n)) {
        String name = NodeUtil.getRootOfQualifiedName(n).getString();
        TypedScope scope = t.getTypedScope();
        TypedVar var = scope.getVar(name);
        if (var != null) {
          TypedScope ownerScope = var.getScope();
          if (scope != ownerScope && ownerScope.isLocal()) {
            data.get(ownerScope.getRootNode())
                .recordEscapedQualifiedName(n.getQualifiedName());
          }
        }
      }
    }
  }

  private AstFunctionContents getFunctionAnalysisResults(@Nullable Node n) {
    if (n == null) {
      return null;
    }

    // Sometimes this will return null in things like
    // NameReferenceGraphConstruction that build partial scopes.
    return functionAnalysisResults.get(n);
  }

  @Override
  public boolean hasBlockScope() {
    return false;
  }
}
