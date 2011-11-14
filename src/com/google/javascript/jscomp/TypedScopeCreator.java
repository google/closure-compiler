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
import static com.google.javascript.rhino.jstype.JSTypeNative.ARRAY_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_OBJECT_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.DATE_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.ERROR_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.EVAL_ERROR_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.FUNCTION_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.GLOBAL_THIS;
import static com.google.javascript.rhino.jstype.JSTypeNative.NO_OBJECT_TYPE;
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
import com.google.javascript.jscomp.CodingConvention.DelegateRelationship;
import com.google.javascript.jscomp.CodingConvention.ObjectLiteralCast;
import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.jscomp.CodingConvention.SubclassType;
import com.google.javascript.jscomp.FunctionTypeBuilder.AstFunctionContents;
import com.google.javascript.jscomp.NodeTraversal.AbstractScopedCallback;
import com.google.javascript.jscomp.NodeTraversal.AbstractShallowStatementCallback;
import com.google.javascript.jscomp.Scope.Var;
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

    void resolve(Scope scope) {
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
  }

  /**
   * Creates a scope with all types declared. Declares newly discovered types
   * and type properties in the type registry.
   */
  @Override
  public Scope createScope(Node root, Scope parent) {
    // Constructing the global scope is very different than constructing
    // inner scopes, because only global scopes can contain named classes that
    // show up in the type registry.
    Scope newScope = null;
    AbstractScopeBuilder scopeBuilder = null;
    if (parent == null) {
      // Run a first-order analysis over the syntax tree.
      (new FirstOrderFunctionAnalyzer(compiler, functionAnalysisResults))
          .process(root.getFirstChild(), root.getLastChild());

      // Find all the classes in the global scope.
      newScope = createInitialScope(root);

      GlobalScopeBuilder globalScopeBuilder = new GlobalScopeBuilder(newScope);
      scopeBuilder = globalScopeBuilder;
      NodeTraversal.traverse(compiler, root, scopeBuilder);
    } else {
      newScope = new Scope(parent, root);
      LocalScopeBuilder localScopeBuilder = new LocalScopeBuilder(newScope);
      scopeBuilder = localScopeBuilder;
      localScopeBuilder.build();
    }

    scopeBuilder.resolveStubDeclarations();
    scopeBuilder.resolveTypes();

    // Gather the properties in each function that we found in the
    // global scope, if that function has a @this type that we can
    // build properties on.
    for (Node functionNode : scopeBuilder.nonExternFunctions) {
      JSType type = functionNode.getJSType();
      if (type != null && type.isFunctionType()) {
        FunctionType fnType = type.toMaybeFunctionType();
        ObjectType fnThisType = fnType.getTypeOfThis();
        if (!fnThisType.isUnknownType()) {
          NodeTraversal.traverse(compiler, functionNode.getLastChild(),
              scopeBuilder.new CollectProperties(fnThisType));
        }
      }
    }

    if (parent == null) {
      codingConvention.defineDelegateProxyPrototypeProperties(
          typeRegistry, newScope, delegateProxyPrototypes,
          delegateCallingConventions);
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
  void patchGlobalScope(Scope globalScope, Node scriptRoot) {
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
    Iterator<Var> varIter = globalScope.getVars();
    List<Var> varsToRemove = Lists.newArrayList();
    while (varIter.hasNext()) {
      Var oldVar = varIter.next();
      if (scriptName.equals(oldVar.getInputName())) {
        varsToRemove.add(oldVar);
      }
    }
    for (Var var : varsToRemove) {
      globalScope.undeclare(var);
      globalScope.getTypeOfThis().removeProperty(var.getName());
    }

    // Now re-traverse the given script.
    GlobalScopeBuilder scopeBuilder = new GlobalScopeBuilder(globalScope);
    NodeTraversal.traverse(compiler, scriptRoot, scopeBuilder);
  }

  /**
   * Create the outermost scope. This scope contains native binding such as
   * {@code Object}, {@code Date}, etc.
   */
  @VisibleForTesting
  Scope createInitialScope(Node root) {

    NodeTraversal.traverse(
        compiler, root, new DiscoverEnumsAndTypedefs(typeRegistry));

    Scope s = new Scope(root, compiler);
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

    // ActiveXObject is unqiuely special, because it can be used to construct
    // any type (the type that it creates is related to the arguments you
    // pass to it).
    declareNativeValueType(s, "ActiveXObject", NO_OBJECT_TYPE);

    return s;
  }

  private void declareNativeFunctionType(Scope scope, JSTypeNative tId) {
    FunctionType t = typeRegistry.getNativeFunctionType(tId);
    declareNativeType(scope, t.getInstanceType().getReferenceName(), t);
    declareNativeType(
        scope, t.getPrototype().getReferenceName(), t.getPrototype());
  }

  private void declareNativeValueType(Scope scope, String name,
      JSTypeNative tId) {
    declareNativeType(scope, name, typeRegistry.getNativeType(tId));
  }

  private void declareNativeType(Scope scope, String name, JSType t) {
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
      Node nameNode = null;
      switch (node.getType()) {
        case Token.VAR:
          for (Node child = node.getFirstChild();
               child != null; child = child.getNext()) {
            identifyNameNode(
                child, child.getFirstChild(),
                NodeUtil.getBestJSDocInfo(child));
          }
          break;
        case Token.EXPR_RESULT:
          Node firstChild = node.getFirstChild();
          if (firstChild.isAssign()) {
            identifyNameNode(
                firstChild.getFirstChild(), firstChild.getLastChild(),
                firstChild.getJSDocInfo());
          } else {
            identifyNameNode(
                firstChild, null, firstChild.getJSDocInfo());
          }
          break;
      }
    }

    private void identifyNameNode(
        Node nameNode, Node valueNode, JSDocInfo info) {
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
      implements NodeTraversal.Callback {

    /**
     * The scope that we're builidng.
     */
    final Scope scope;

    private final List<DeferredSetType> deferredSetTypes =
        Lists.newArrayList();

    /**
     * Functions that we found in the global scope and not in externs.
     */
    private final List<Node> nonExternFunctions = Lists.newArrayList();

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

    private AbstractScopeBuilder(Scope scope) {
      this.scope = scope;
    }

    void setDeferredType(Node node, JSType type) {
      deferredSetTypes.add(new DeferredSetType(node, type));
    }

    void resolveTypes() {
      // Resolve types and attach them to nodes.
      for (DeferredSetType deferred : deferredSetTypes) {
        deferred.resolve(scope);
      }

      // Resolve types and attach them to scope slots.
      Iterator<Var> vars = scope.getVars();
      while (vars.hasNext()) {
        vars.next().resolveType(typeParsingErrorReporter);
      }

      // Tell the type registry that any remaining types
      // are unknown.
      typeRegistry.resolveTypesInScope(scope);
    }

    @Override
    public final boolean shouldTraverse(NodeTraversal t, Node n,
        Node parent) {
      inputId = t.getInputId();
      if (n.isFunction() ||
          n.isScript()) {
        Preconditions.checkNotNull(inputId);
        sourceName = NodeUtil.getSourceName(n);
      }

      // We do want to traverse the name of a named function, but we don't
      // want to traverse the arguments or body.
      boolean descend = parent == null || parent.getType() != Token.FUNCTION ||
          n == parent.getFirstChild() || parent == scope.getRootNode();

      if (descend) {
        // Handle hoisted functions on pre-order traversal, so that they
        // get hit before other things in the scope.
        if (NodeUtil.isStatementParent(n)) {
          for (Node child = n.getFirstChild();
               child != null;
               child = child.getNext()) {
            if (NodeUtil.isHoistedFunctionDeclaration(child)) {
              defineFunctionLiteral(child, n);
            }
          }
        }
      }

      return descend;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      inputId = t.getInputId();
      attachLiteralTypes(t, n);

      switch (n.getType()) {
        case Token.CALL:
          checkForClassDefiningCalls(t, n, parent);
          checkForCallingConventionDefiningCalls(n, delegateCallingConventions);
          break;

        case Token.FUNCTION:
          if (t.getInput() == null || !t.getInput().isExtern()) {
            nonExternFunctions.add(n);
          }

          // Hoisted functions are handled during pre-traversal.
          if (!NodeUtil.isHoistedFunctionDeclaration(n)) {
            defineFunctionLiteral(n, parent);
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
          defineCatch(n, parent);
          break;

        case Token.VAR:
          defineVar(n, parent);
          break;

        case Token.GETPROP:
          // Handle stubbed properties.
          if (parent.isExprResult() &&
              n.isQualifiedName()) {
            maybeDeclareQualifiedName(t, n.getJSDocInfo(), n, parent, null);
          }
          break;
      }
    }

    private void attachLiteralTypes(NodeTraversal t, Node n) {
      switch (n.getType()) {
        case Token.NULL:
          n.setJSType(getNativeType(NULL_TYPE));
          break;

        case Token.VOID:
          n.setJSType(getNativeType(VOID_TYPE));
          break;

        case Token.STRING:
          // Defer keys to the Token.OBJECTLIT case
          if (!NodeUtil.isObjectLitKey(n, n.getParent())) {
            n.setJSType(getNativeType(STRING_TYPE));
          }
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
          defineObjectLiteral(t, n);
          break;

          // NOTE(nicksantos): If we ever support Array tuples,
          // we will need to put ARRAYLIT here as well.
      }
    }

    private void defineObjectLiteral(NodeTraversal t, Node objectLit) {
      // Handle the @lends annotation.
      JSType type = null;
      JSDocInfo info = objectLit.getJSDocInfo();
      if (info != null &&
          info.getLendsName() != null) {
        String lendsName = info.getLendsName();
        Var lendsVar = scope.getVar(lendsName);
        if (lendsVar == null) {
          compiler.report(
              JSError.make(sourceName, objectLit, UNKNOWN_LENDS, lendsName));
        } else {
          type = lendsVar.getType();
          if (type == null) {
            type = typeRegistry.getNativeType(UNKNOWN_TYPE);
          }
          if (!type.isSubtype(typeRegistry.getNativeType(OBJECT_TYPE))) {
            compiler.report(
                JSError.make(sourceName, objectLit, LENDS_ON_NON_OBJECT,
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
        type = typeRegistry.createAnonymousObjectType();
      }

      setDeferredType(objectLit, type);

      // If this is an enum, the properties were already taken care of above.
      processObjectLitProperties(
          t, objectLit, ObjectType.cast(objectLit.getJSType()), !createdEnumType);
    }

    /**
     * Process an object literal and all the types on it.
     * @param objLit The OBJECTLIT node.
     * @param objLitType The type of the OBJECTLIT node. This might be a named
     *     type, because of the lends annotation.
     * @param declareOnOwner If true, declare properties on the objLitType as
     *     well. If false, the caller should take crae of this.
     */
    void processObjectLitProperties(
        NodeTraversal t, Node objLit, ObjectType objLitType,
        boolean declareOnOwner) {
      for (Node keyNode = objLit.getFirstChild(); keyNode != null;
           keyNode = keyNode.getNext()) {
        Node value = keyNode.getFirstChild();
        String memberName = NodeUtil.getObjectLitKeyName(keyNode);
        JSDocInfo info = keyNode.getJSDocInfo();
        JSType valueType =
            getDeclaredType(t.getSourceName(), info, keyNode, value);
        JSType keyType =  objLitType.isEnumType() ?
            objLitType.toMaybeEnumType().getElementsType() :
            NodeUtil.getObjectLitKeyTypeFromValueType(keyNode, valueType);
        if (keyType != null) {
          // Try to declare this property in the current scope if it
          // has an authoritative name.
          String qualifiedName = NodeUtil.getBestLValueName(keyNode);
          if (qualifiedName != null) {
            defineSlot(keyNode, objLit, qualifiedName, keyType, false);
          } else {
            setDeferredType(keyNode, keyType);
          }

          if (objLitType != null && declareOnOwner) {
            // Declare this property on its object literal.
            boolean isExtern = t.getInput() != null && t.getInput().isExtern();
            objLitType.defineDeclaredProperty(memberName, keyType, keyNode);
          }
        }
      }
    }

    /**
     * Returns the type specified in a JSDoc annotation near a GETPROP or NAME.
     *
     * Extracts type information from either the {@code @type} tag or from
     * the {@code @return} and {@code @param} tags.
     */
    private JSType getDeclaredTypeInAnnotation(String sourceName,
        Node node, JSDocInfo info) {
      JSType jsType = null;
      Node objNode =
          node.isGetProp() ? node.getFirstChild() :
          NodeUtil.isObjectLitKey(node, node.getParent()) ? node.getParent() :
          null;
      if (info != null) {
        if (info.hasType()) {
          jsType = info.getType().evaluate(scope, typeRegistry);
        } else if (FunctionTypeBuilder.isFunctionTypeDeclaration(info)) {
          String fnName = node.getQualifiedName();
          jsType = createFunctionTypeFromNodes(
              null, fnName, info, node);
        }
      }
      return jsType;
    }

    /**
     * Asserts that it's ok to define this node's name.
     * The node should have a source name and be of the specified type.
     */
    void assertDefinitionNode(Node n, int type) {
      Preconditions.checkState(sourceName != null);
      Preconditions.checkState(n.getType() == type);
    }

    /**
     * Defines a catch parameter.
     */
    void defineCatch(Node n, Node parent) {
      assertDefinitionNode(n, Token.CATCH);
      Node catchName = n.getFirstChild();
      defineSlot(catchName, n, null);
    }

    /**
     * Defines a VAR initialization.
     */
    void defineVar(Node n, Node parent) {
      assertDefinitionNode(n, Token.VAR);
      JSDocInfo info = n.getJSDocInfo();
      if (n.hasMoreThanOneChild()) {
        if (info != null) {
          // multiple children
          compiler.report(JSError.make(sourceName, n, MULTIPLE_VAR_DEF));
        }
        for (Node name : n.children()) {
          defineName(name, n, parent, name.getJSDocInfo());
        }
      } else {
        Node name = n.getFirstChild();
        defineName(name, n, parent,
            (info != null) ? info : name.getJSDocInfo());
      }
    }

    /**
     * Defines a function literal.
     */
    void defineFunctionLiteral(Node n, Node parent) {
      assertDefinitionNode(n, Token.FUNCTION);

      // Determine the name and JSDocInfo and lvalue for the function.
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
     * @param parent {@code var}'s parent.
     * @param info the {@link JSDocInfo} information relating to this
     *     {@code name} node.
     */
    private void defineName(Node name, Node var, Node parent, JSDocInfo info) {
      Node value = name.getFirstChild();

      // variable's type
      JSType type = getDeclaredType(sourceName, info, name, value);
      if (type == null) {
        // The variable's type will be inferred.
        type = name.isFromExterns() ?
            getNativeType(UNKNOWN_TYPE) : null;
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
          NodeUtil.isObjectLitKey(lValue, lValue.getParent())) {
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
     * - An assignment expression with function-type info in the jsdoc.
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

      // Global ctor aliases should be registered with the type registry.
      if (rValue != null && rValue.isQualifiedName() && scope.isGlobal()) {
        Var var = scope.getVar(rValue.getQualifiedName());
        if (var != null && var.getType() != null &&
            var.getType().isFunctionType()) {
          FunctionType aliasedType  = var.getType().toMaybeFunctionType();
          if ((aliasedType.isConstructor() || aliasedType.isInterface()) &&
              !aliasedType.isNativeObjectType()) {
            functionType = aliasedType;

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
        Node fnBlock = isFnLiteral ? parametersNode.getNext() : null;

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
          Var ownerVar = null;
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

          FunctionType overriddenPropType = null;
          if (ownerType != null && propName != null) {
            overriddenPropType =
                findOverriddenFunction(ownerType, propName);
          }

          FunctionTypeBuilder builder =
              new FunctionTypeBuilder(name, compiler, errorRoot, sourceName,
                  scope)
              .setContents(getFunctionAnalysisResults(fnRoot))
              .inferFromOverriddenFunction(overriddenPropType, parametersNode)
              .inferTemplateTypeName(info)
              .inferReturnType(info)
              .inferInheritance(info);


          // Infer the context type.
          boolean searchedForThisType = false;
          if (ownerType != null && ownerType.isFunctionPrototypeType()) {
            builder.inferThisType(
                info, ownerType.getOwnerFunction().getInstanceType());
            searchedForThisType = true;
          } else if (ownerNode != null && ownerNode.isThis()) {
            builder.inferThisType(info, ownerNode.getJSType());
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
     * Find the function that's being overridden on this type, if any.
     */
    private FunctionType findOverriddenFunction(
        ObjectType ownerType, String propName) {
      // First, check to see if the property is implemented
      // on a superclass.
      JSType propType = ownerType.getPropertyType(propName);
      if (propType != null && propType.isFunctionType()) {
        return propType.toMaybeFunctionType();
      } else {
        // If it's not, then check to see if it's implemented
        // on an implemented interface.
        for (ObjectType iface :
                 ownerType.getCtorImplementedInterfaces()) {
          propType = iface.getPropertyType(propName);
          if (propType != null && propType.isFunctionType()) {
            return propType.toMaybeFunctionType();
          }
        }
      }

      return null;
    }

    /**
     * Creates a new enum type, based on the given nodes.
     *
     * This handles two cases that are semantically very different, but
     * are not mutually exclusive:
     * - An object literal that needs an enum type attached to it.
     * - An assignment expression with an enum tag in the jsdoc.
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
        Var var = scope.getVar(rValue.getQualifiedName());
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
                  JSError.make(sourceName, key, ENUM_NOT_CONSTANT, keyName));
            } else if (!codingConvention.isValidEnumKey(keyName)) {
              compiler.report(
                  JSError.make(sourceName, key, ENUM_NOT_CONSTANT, keyName));
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

      // Only allow declarations of NAMEs and qualfied names.
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
      Scope scopeToDeclareIn = scope;
      if (n.isGetProp() && !scope.isGlobal() &&
          isQnameRootedInGlobalScope(n)) {
        Scope globalScope = scope.getGlobalScope();

        // don't try to declare in the global scope if there's
        // already a symbol there with this name.
        if (!globalScope.isDeclared(variableName, false)) {
          scopeToDeclareIn = scope.getGlobalScope();
        }
      }

      // declared in closest scope?
      CompilerInput input = compiler.getInput(inputId);
      if (scopeToDeclareIn.isDeclared(variableName, false)) {
        Var oldVar = scopeToDeclareIn.getVar(variableName);
        validator.expectUndeclaredVariable(
            sourceName, input, n, parent, oldVar, variableName, type);
      } else {
        if (!inferred) {
          setDeferredType(n, type);
        }

        // The input may be null if we are working with a AST snippet.
        boolean isExtern = n.isFromExterns();
        Var newVar =
            scopeToDeclareIn.declare(variableName, n, type, input, inferred);

        if (type instanceof EnumType) {
          Node initialValue = newVar.getInitialValue();
          boolean isValidValue = initialValue != null &&
              (initialValue.isObjectLit() ||
               initialValue.isQualifiedName());
          if (!isValidValue) {
            compiler.report(JSError.make(sourceName, n, ENUM_INITIALIZER));
          }
        }

        // We need to do some additional work for constructors and interfaces.
        FunctionType fnType = JSType.toMaybeFunctionType(type);
        if (fnType != null &&
            // We don't want to look at empty function types.
            !type.isEmptyType()) {
          if ((fnType.isConstructor() || fnType.isInterface()) &&
              !fnType.equals(getNativeType(U2U_CONSTRUCTOR_TYPE))) {
            // Declare var.prototype in the scope chain.
            FunctionType superClassCtor = fnType.getSuperClassConstructor();
            ObjectType.Property prototypeSlot = fnType.getSlot("prototype");

            String prototypeName = variableName + ".prototype";

            // There are some rare cases where the prototype will already
            // be declared. See TypedScopeCreatorTest#testBogusPrototypeInit.
            // Fortunately, other warnings will complain if this happens.
            if (scopeToDeclareIn.getOwnSlot(prototypeName) == null) {
              // When we declare the function prototype implicitly, we
              // want to make sure that the function and its prototype
              // are declared at the same node. We also want to make sure
              // that the if a symbol has both a Var and a JSType, they have
              // the same node.
              //
              // This consistency is helpful to users of SymbolTable,
              // because everything gets declared at the same place.
              prototypeSlot.setNode(n);

              scopeToDeclareIn.declare(prototypeName,
                  n, prototypeSlot.getType(), input,
                  /* declared iff there's an explicit supertype */
                  superClassCtor == null ||
                  superClassCtor.getInstanceType().equals(
                      getNativeType(OBJECT_TYPE)));
            }

            // Make sure the variable is initialized to something if
            // it constructs itself.
            if (newVar.getInitialValue() == null &&
                !isExtern &&
                // We want to make sure that when we declare a new instance
                // type (with @constructor) that there's actually a ctor for it.
                // This doesn't apply to structural constructors
                // (like function(new:Array). Checking the constructed
                // type against the variable name is a sufficient check for
                // this.
                variableName.equals(
                    fnType.getInstanceType().getReferenceName())) {
              compiler.report(
                  JSError.make(sourceName, n,
                      fnType.isConstructor() ?
                          CTOR_INITIALIZER : IFACE_INITIALIZER,
                      variableName));
            }
          }
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

    /**
     * Check if the given node is a property of a name in the global scope.
     */
    private boolean isQnameRootedInGlobalScope(Node n) {
      Scope scope = getQnameRootScope(n);
      return scope != null && scope.isGlobal();
    }

    /**
     * Return the scope for the name of the given node.
     */
    private Scope getQnameRootScope(Node n) {
      Node root = NodeUtil.getRootOfQualifiedName(n);
      if (root.isName()) {
        Var var = scope.getVar(root.getString());
        if (var != null) {
          return var.getScope();
        }
      }
      return null;
    }

    /**
     * Look for a type declaration on a property assignment
     * (in an ASSIGN or an object literal key).
     *
     * @param info The doc info for this property.
     * @param lValue The l-value node.
     * @param rValue The node that {@code n} is being initialized to,
     *     or {@code null} if this is a stub declaration.
     */
    private JSType getDeclaredType(String sourceName, JSDocInfo info,
        Node lValue, @Nullable Node rValue) {
      if (info != null && info.hasType()) {
        return getDeclaredTypeInAnnotation(sourceName, lValue, info);
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
        } else {
          // Check if this is constant, and if it has a known type.
          if (info.isConstant()) {
            JSType knownType = null;
            if (rValue != null) {
              if (rValue.getJSType() != null
                  && !rValue.getJSType().isUnknownType()) {
                return rValue.getJSType();
              } else if (rValue.isOr()) {
                // Check for a very specific JS idiom:
                // var x = x || TYPE;
                // This is used by Closure's base namespace for esoteric
                // reasons.
                Node firstClause = rValue.getFirstChild();
                Node secondClause = firstClause.getNext();
                boolean namesMatch = firstClause.isName()
                    && lValue.isName()
                    && firstClause.getString().equals(lValue.getString());
                if (namesMatch && secondClause.getJSType() != null
                    && !secondClause.getJSType().isUnknownType()) {
                  return secondClause.getJSType();
                }
              }
            }
          }
        }
      }

      return getDeclaredTypeInAnnotation(sourceName, lValue, info);
    }

    private FunctionType getFunctionType(@Nullable Var v) {
      JSType t = v == null ? null : v.getType();
      ObjectType o = t == null ? null : t.dereference();
      return JSType.toMaybeFunctionType(o);
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
    private void checkForClassDefiningCalls(
        NodeTraversal t, Node n, Node parent) {
      SubclassRelationship relationship =
          codingConvention.getClassesDefinedByCall(n);
      if (relationship != null) {
        FunctionType superCtor = getFunctionType(
            scope.getVar(relationship.superclassName));
        FunctionType subCtor = getFunctionType(
            scope.getVar(relationship.subclassName));
        if (superCtor != null && superCtor.isConstructor() &&
            subCtor != null && subCtor.isConstructor()) {
          ObjectType superClass = superCtor.getInstanceType();
          ObjectType subClass = subCtor.getInstanceType();

          // superCtor and subCtor might be structural constructors
          // (like {function(new:Object)}) so we need to resolve them back
          // to the original ctor objects.
          superCtor = superClass.getConstructor();
          subCtor = subClass.getConstructor();

          if (relationship.type == SubclassType.INHERITS &&
              !superClass.isEmptyType() && !subClass.isEmptyType()) {
            validator.expectSuperType(t, n, superClass, subClass);
          }

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
            codingConvention.applySingletonGetter(functionType, getterType,
                objectType);
          }
        }
      }

      DelegateRelationship delegateRelationship =
          codingConvention.getDelegateRelationship(n);
      if (delegateRelationship != null) {
        applyDelegateRelationship(delegateRelationship);
      }

      ObjectLiteralCast objectLiteralCast =
          codingConvention.getObjectLiteralCast(t, n);
      if (objectLiteralCast != null) {
        ObjectType type = ObjectType.cast(
            typeRegistry.getType(objectLiteralCast.typeName));
        if (type != null && type.getConstructor() != null) {
          setDeferredType(objectLiteralCast.objectNode, type);
        } else {
          compiler.report(JSError.make(t.getSourceName(), n,
                  CONSTRUCTOR_EXPECTED));
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
              null, null, null);
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
      // 1) @type annnotation / @enum annotation
      // 2) ASSIGN to FUNCTION literal
      // 3) @param/@return annotation (with no function literal)
      // 4) ASSIGN to something marked @const
      // 5) ASSIGN to anything else
      //
      // 1, 3, and 4 are declarations, 5 is inferred, and 2 is a declaration iff
      // the function has jsdoc or has not been declared before.
      //
      // FUNCTION literals are special because TypedScopeCreator is very smart
      // about getting as much type information as possible for them.

      // Determining type for #1 + #2 + #3 + #4
      JSType valueType = getDeclaredType(t.getSourceName(), info, n, rhsValue);
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
        Var qVar = scope.getVar(qName);
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
          if (qVar.getScope() == scope) {
            scope.undeclare(qVar);
          }
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

      // NOTE(nicksantos): Determining whether a property is declared or not
      // is really really obnoxious.
      //
      // The problem is that there are two (equally valid) coding styles:
      //
      // (function() {
      //   /* The authoritative definition of goog.bar. */
      //   goog.bar = function() {};
      // })();
      //
      // function f() {
      //   goog.bar();
      //   /* Reset goog.bar to a no-op. */
      //   goog.bar = function() {};
      // }
      //
      // In a dynamic language with first-class functions, it's very difficult
      // to know which one the user intended without looking at lots of
      // contextual information (the second example demonstrates a small case
      // of this, but there are some really pathological cases as well).
      //
      // The current algorithm checks if either the declaration has
      // jsdoc type information, or @const with a known type,
      // or a function literal with a name we haven't seen before.
      boolean inferred = true;
      if (info != null) {
        // Determining declaration for #1 + #3 + #4
        inferred = !(info.hasType()
            || info.hasEnumParameterType()
            || (info.isConstant() && valueType != null
                && !valueType.isUnknownType())
            || FunctionTypeBuilder.isFunctionTypeDeclaration(info));
      }

      if (inferred && rhsValue != null && rhsValue.isFunction()) {
        // Determining declaration for #2
        if (info != null) {
          inferred = false;
        } else if (!scope.isDeclared(qName, false) &&
                   n.isUnscopedQualifiedName()) {
          inferred = false;
        }
      }

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
        FunctionType ownerType =
            JSType.toMaybeFunctionType(getObjectSlot(ownerName));
        if (ownerType != null) {
          JSType ownerTypeOfThis = ownerType.getTypeOfThis();
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
     * Find the ObjectType associated with the given slot.
     * @param slotName The name of the slot to find the type in.
     * @return An object type, or null if this slot does not contain an object.
     */
    private ObjectType getObjectSlot(String slotName) {
      Var ownerVar = scope.getVar(slotName);
      if (ownerVar != null) {
        JSType ownerVarType = ownerVar.getType();
        return ObjectType.cast(ownerVarType == null ?
            null : ownerVarType.restrictByNotNullOrUndefined());
      }
      return null;
    }

    /**
     * Resolve any stub delcarations to unknown types if we could not
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
        ObjectType unknownType = typeRegistry.getNativeObjectType(UNKNOWN_TYPE);
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

    /**
     * Collects all declared properties in a function, and
     * resolves them relative to the global scope.
     */
    private final class CollectProperties
        extends AbstractShallowStatementCallback {
      private final ObjectType thisType;

      CollectProperties(ObjectType thisType) {
        this.thisType = thisType;
      }

      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        if (n.isExprResult()) {
          Node child = n.getFirstChild();
          switch (child.getType()) {
            case Token.ASSIGN:
              maybeCollectMember(t, child.getFirstChild(), child,
                  child.getLastChild());
              break;
            case Token.GETPROP:
              maybeCollectMember(t, child, child, null);
              break;
          }
        }
      }

      private void maybeCollectMember(NodeTraversal t,
          Node member, Node nodeWithJsDocInfo, @Nullable Node value) {
        JSDocInfo info = nodeWithJsDocInfo.getJSDocInfo();

        // Do nothing if there is no JSDoc type info, or
        // if the node is not a member expression, or
        // if the member expression is not of the form: this.someProperty.
        if (info == null ||
            member.getType() != Token.GETPROP ||
            member.getFirstChild().getType() != Token.THIS) {
          return;
        }

        member.getFirstChild().setJSType(thisType);
        JSType jsType = getDeclaredType(t.getSourceName(), info, member, value);
        Node name = member.getLastChild();
        if (jsType != null &&
            (name.isName() || name.isString())) {
          thisType.defineDeclaredProperty(
              name.getString(),
              jsType,
              member);
        }
      }
    } // end CollectProperties
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

    private GlobalScopeBuilder(Scope scope) {
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
            checkForTypedef(t, n.getFirstChild(), n.getJSDocInfo());
          }
          break;
      }
    }

    @Override
    void maybeDeclareQualifiedName(
        NodeTraversal t, JSDocInfo info,
        Node n, Node parent, Node rhsValue) {
      checkForTypedef(t, n, info);
      super.maybeDeclareQualifiedName(t, info, n, parent, rhsValue);
    }

    /**
     * Handle typedefs.
     * @param t The current traversal.
     * @param candidate A qualified name node.
     * @param info JSDoc comments.
     */
    private void checkForTypedef(
        NodeTraversal t, Node candidate, JSDocInfo info) {
      if (info == null || !info.hasTypedefType()) {
        return;
      }

      String typedef = candidate.getQualifiedName();
      if (typedef == null) {
        return;
      }

      // TODO(nicksantos|user): This is a terrible, terrible hack
      // to bail out on recusive typedefs. We'll eventually need
      // to handle these properly.
      typeRegistry.declareType(typedef, getNativeType(UNKNOWN_TYPE));

      JSType realType = info.getTypedefType().evaluate(scope, typeRegistry);
      if (realType == null) {
        compiler.report(
            JSError.make(
                t.getSourceName(), candidate, MALFORMED_TYPEDEF, typedef));
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
    /**
     * @param scope The scope that we're builidng.
     */
    private LocalScopeBuilder(Scope scope) {
      super(scope);
    }

    /**
     * Traverse the scope root and build it.
     */
    void build() {
      NodeTraversal.traverse(compiler, scope.getRootNode(), this);

      AstFunctionContents contents =
          getFunctionAnalysisResults(scope.getRootNode());
      if (contents != null) {
        for (String varName : contents.getEscapedVarNames()) {
          Var v = scope.getVar(varName);
          Preconditions.checkState(v.getScope() == scope);
          v.markEscaped();
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
      if (n == scope.getRootNode()) return;

      if (n.isParamList() && parent == scope.getRootNode()) {
        handleFunctionInputs(parent);
        return;
      }

      super.visit(t, n, parent);
    }

    /** Handle bleeding functions and function parameters. */
    private void handleFunctionInputs(Node fnNode) {
      // Handle bleeding functions.
      Node fnNameNode = fnNode.getFirstChild();
      String fnName = fnNameNode.getString();
      if (!fnName.isEmpty()) {
        Scope.Var fnVar = scope.getVar(fnName);
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
      Node body = astParameters.getNext();
      FunctionType functionType =
          JSType.toMaybeFunctionType(functionNode.getJSType());
      if (functionType != null) {
        Node jsDocParameters = functionType.getParametersNode();
        if (jsDocParameters != null) {
          Node jsDocParameter = jsDocParameters.getFirstChild();
          for (Node astParameter : astParameters.children()) {
            if (jsDocParameter != null) {
              defineSlot(astParameter, functionNode,
                  jsDocParameter.getJSType(), false);
              jsDocParameter = jsDocParameter.getNext();
            } else {
              defineSlot(astParameter, functionNode, null, true);
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
        NodeTraversal.traverse(compiler, root, this);
      } else {
        NodeTraversal.traverseRoots(
            compiler, ImmutableList.of(externs, root), this);
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
      } else if (n.isName() && NodeUtil.isLValue(n)) {
        String name = n.getString();
        Scope scope = t.getScope();
        Var var = scope.getVar(name);
        if (var != null) {
          Scope ownerScope = var.getScope();
          if (scope != ownerScope && ownerScope.isLocal()) {
            data.get(ownerScope.getRootNode()).recordEscapedVarName(name);
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
}
