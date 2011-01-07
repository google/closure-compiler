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

import static com.google.javascript.jscomp.TypeCheck.ENUM_DUP;
import static com.google.javascript.jscomp.TypeCheck.ENUM_NOT_CONSTANT;
import static com.google.javascript.jscomp.TypeCheck.MULTIPLE_VAR_DEF;
import static com.google.javascript.rhino.jstype.JSTypeNative.ARRAY_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_OBJECT_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.DATE_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.ERROR_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.EVAL_ERROR_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.FUNCTION_FUNCTION_TYPE;
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
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.CodingConvention.DelegateRelationship;
import com.google.javascript.jscomp.CodingConvention.ObjectLiteralCast;
import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.jscomp.CodingConvention.SubclassType;
import com.google.javascript.jscomp.NodeTraversal.AbstractShallowStatementCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.ErrorReporter;
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
 */
final class TypedScopeCreator implements ScopeCreator {
  /**
   * A suffix for naming delegate proxies differently from their base.
   */
  static final String DELEGATE_PROXY_SUFFIX =
      ObjectType.createDelegateSuffix("Proxy");

  private static final String LEGACY_TYPEDEF = "goog.typedef";

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
  public Scope createScope(Node root, Scope parent) {
    // Constructing the global scope is very different than constructing
    // inner scopes, because only global scopes can contain named classes that
    // show up in the type registry.
    Scope newScope = null;
    AbstractScopeBuilder scopeBuilder = null;
    if (parent == null) {
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
      if (type != null && type instanceof FunctionType) {
        FunctionType fnType = (FunctionType) type;
        ObjectType fnThisType = fnType.getTypeOfThis();
        if (!fnThisType.isUnknownType()) {
          NodeTraversal.traverse(compiler, functionNode.getLastChild(),
              scopeBuilder.new CollectProperties(fnThisType));
        }
      }
    }

    if (parent == null) {
      codingConvention.defineDelegateProxyPrototypeProperties(
          typeRegistry, newScope, delegateProxyPrototypes);
    }
    return newScope;
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

    // The typedef construct needs the any type, so that it can be assigned
    // to anything. This is kind of a hack, and an artifact of the typedef
    // syntax we've chosen.
    declareNativeValueType(s, LEGACY_TYPEDEF, NO_TYPE);

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
                NodeUtil.getInfoForNameNode(child));
          }
          break;
        case Token.EXPR_RESULT:
          Node firstChild = node.getFirstChild();
          if (firstChild.getType() == Token.ASSIGN) {
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

        if (valueNode != null &&
            LEGACY_TYPEDEF.equals(valueNode.getQualifiedName())) {
          registry.identifyNonNullableName(nameNode.getQualifiedName());
        }
      }
    }
  }

  /**
   * Given a node, determines whether that node names a prototype
   * property, and if so, returns the qualified name node representing
   * the owner of that property. Otherwise, returns null.
   */
  private static Node getPrototypePropertyOwner(Node n) {
    if (n.getType() == Token.GETPROP) {
      Node firstChild = n.getFirstChild();
      if (firstChild.getType() == Token.GETPROP &&
          firstChild.getLastChild().getString().equals("prototype")) {
        Node maybeOwner = firstChild.getFirstChild();
        if (maybeOwner.isQualifiedName()) {
          return maybeOwner;
        }
      }
    }
    return null;
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
    public final boolean shouldTraverse(NodeTraversal nodeTraversal, Node n,
        Node parent) {
      if (n.getType() == Token.FUNCTION ||
          n.getType() == Token.SCRIPT) {
        sourceName = (String) n.getProp(Node.SOURCENAME_PROP);
      }

      // We do want to traverse the name of a named function, but we don't
      // want to traverse the arguments or body.
      return parent == null || parent.getType() != Token.FUNCTION ||
          n == parent.getFirstChild() || parent == scope.getRootNode();
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      attachLiteralTypes(n);

      switch (n.getType()) {
        case Token.CALL:
          checkForClassDefiningCalls(t, n, parent);
          break;

        case Token.FUNCTION:
          if (t.getInput() == null || !t.getInput().isExtern()) {
            nonExternFunctions.add(n);
          }

          // VARs and ASSIGNs are handled in different branches of this
          // switch statement.
          if (parent.getType() != Token.ASSIGN &&
              parent.getType() != Token.NAME) {
            defineDeclaredFunction(n, parent);
          }

          break;

        case Token.ASSIGN:
          // Handle constructor and enum definitions.
          defineNamedTypeAssign(n, parent);

          // Handle initialization of properties.
          Node firstChild = n.getFirstChild();
          if (firstChild.getType() == Token.GETPROP &&
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
          if (parent.getType() == Token.EXPR_RESULT &&
              n.isQualifiedName()) {
            maybeDeclareQualifiedName(t, n.getJSDocInfo(), n, parent, null);
          }
          break;
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

        case Token.REF_SPECIAL:
          n.setJSType(getNativeType(UNKNOWN_TYPE));
          break;

        case Token.OBJECTLIT:
          processObjectLit(n);
          break;

          // NOTE(nicksantos): If we ever support Array tuples,
          // we will need to put ARRAYLIT here as well.
      }
    }

    private void processObjectLit(Node objectLit) {
      JSDocInfo info = objectLit.getJSDocInfo();
      if (info != null &&
          info.getLendsName() != null) {
        String lendsName = info.getLendsName();
        Var lendsVar = scope.getVar(lendsName);
        if (lendsVar == null) {
          compiler.report(
              JSError.make(sourceName, objectLit, UNKNOWN_LENDS, lendsName));
        } else {
          JSType type = lendsVar.getType();
          if (type == null) {
            type = typeRegistry.getNativeType(UNKNOWN_TYPE);
          }
          if (!type.isSubtype(typeRegistry.getNativeType(OBJECT_TYPE))) {
            compiler.report(
                JSError.make(sourceName, objectLit, LENDS_ON_NON_OBJECT,
                    lendsName, type.toString()));
          } else {
            objectLit.setJSType(type);
          }
        }
      }

      if (objectLit.getJSType() == null) {
        objectLit.setJSType(typeRegistry.createAnonymousObjectType());
      }
    }

    /**
     * Returns the type specified in a JSDoc annotation near a GETPROP or NAME.
     *
     * Extracts type information from either the {@code @type} tag or from
     * the {@code @return} and {@code @param} tags.
     */
    JSType getDeclaredTypeInAnnotation(
        NodeTraversal t, Node node, JSDocInfo info) {
      return getDeclaredTypeInAnnotation(t.getSourceName(), node, info);
    }

    JSType getDeclaredTypeInAnnotation(String sourceName,
        Node node, JSDocInfo info) {
      JSType jsType = null;
      Node objNode = node.getType() == Token.GETPROP ?
          node.getFirstChild() : null;
      if (info != null) {
        if (info.hasType()) {
          jsType = info.getType().evaluate(scope, typeRegistry);
        } else if (FunctionTypeBuilder.isFunctionTypeDeclaration(info)) {
          String fnName = node.getQualifiedName();

          // constructors are often handled separately.
          if (info.isConstructor() && typeRegistry.getType(fnName) != null) {
            return null;
          }

          FunctionTypeBuilder builder =
              new FunctionTypeBuilder(
                  fnName, compiler, node, sourceName, scope)
              .inferTemplateTypeName(info)
              .inferReturnType(info)
              .inferParameterTypes(info)
              .inferInheritance(info);

          // Infer the context type.
          boolean searchedForThisType = false;
          if (objNode != null) {
            if (objNode.getType() == Token.GETPROP &&
                objNode.getLastChild().getString().equals("prototype")) {
              builder.inferThisType(info, objNode.getFirstChild());
              searchedForThisType = true;
            } else if (objNode.getType() == Token.THIS) {
              builder.inferThisType(info, objNode.getJSType());
              searchedForThisType = true;
            }
          }

          if (!searchedForThisType) {
            builder.inferThisType(info, (Node) null);
          }

          jsType = builder.buildAndRegister();
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
     * Defines a declared function.
     */
    void defineDeclaredFunction(Node n, Node parent) {
      assertDefinitionNode(n, Token.FUNCTION);

      JSDocInfo info = n.getJSDocInfo();
      int parentType = parent.getType();
      Preconditions.checkState(
          (scope.isLocal() || parentType != Token.ASSIGN) &&
          parentType != Token.NAME,
          "function defined as standalone function when it is being " +
          "assigned");
      String functionName = n.getFirstChild().getString();
      FunctionType functionType = getFunctionType(functionName, n, info,
          null);
      if (NodeUtil.isFunctionDeclaration(n)) {
        defineSlot(n.getFirstChild(), n, functionType);
      }
    }

    /**
     * Defines a qualified name assign to an enum or constructor.
     */
    void defineNamedTypeAssign(Node n, Node parent) {
      assertDefinitionNode(n, Token.ASSIGN);
      JSDocInfo info = n.getJSDocInfo();

      // TODO(nicksantos): We should support direct assignment to a
      // prototype, as in:
      // Foo.prototype = {
      //   a: function() { ... },
      //   b: function() { ... }
      // };
      // Right now (6/23/08), we understand most of this syntax, but we
      // don't tie the "a" and "b" methods to the context of Foo.

      Node rvalue = n.getLastChild();
      Node lvalue = n.getFirstChild();
      info = (info != null) ? info : rvalue.getJSDocInfo();
      if (rvalue.getType() == Token.FUNCTION ||
          info != null && info.isConstructor()) {
        getFunctionType(lvalue.getQualifiedName(), rvalue, info,
            lvalue);
      } else if (info != null && info.hasEnumParameterType()) {
        JSType type = getEnumType(lvalue.getQualifiedName(), n, rvalue,
            info.getEnumParameterType().evaluate(scope, typeRegistry));
        if (type != null) {
          setDeferredType(lvalue, type);
        }
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

      if (value != null && value.getType() == Token.FUNCTION) {
        // function
        String functionName = name.getString();
        FunctionType functionType =
            getFunctionType(functionName, value, info, null);
        if (functionType.isReturnTypeInferred() &&
            scope.isLocal()) {
          defineSlot(name, var, null);
        } else {
          defineSlot(name, var, functionType);
        }
      } else {
        // variable's type
        JSType type = null;
        if (info == null) {
          // the variable's type will be inferred
          CompilerInput input = compiler.getInput(sourceName);
          Preconditions.checkNotNull(input, sourceName);
          type = input.isExtern() ?
              getNativeType(UNKNOWN_TYPE) : null;
        } else if (info.hasEnumParameterType()) {
          type = getEnumType(name.getString(), var, value,
              info.getEnumParameterType().evaluate(scope, typeRegistry));
        } else if (info.isConstructor()) {
          type = getFunctionType(name.getString(), value, info, name);
        } else {
          type = getDeclaredTypeInAnnotation(sourceName, name, info);
        }

        defineSlot(name, var, type);
      }
    }

    /**
     * Gets the function type from the function node and its attached
     * {@link JSDocInfo}.
     * @param name the function's name
     * @param rValue the function node. It must be a {@link Token#FUNCTION}.
     * @param info the {@link JSDocInfo} attached to the function definition
     * @param lvalueNode The node where this function is being
     *     assigned. For example, {@code A.prototype.foo = ...} would be used to
     *     determine that this function is a method of A.prototype. May be
     *     null to indicate that this is not being assigned to a qualified name.
     */
    private FunctionType getFunctionType(String name,
        Node rValue, JSDocInfo info, @Nullable Node lvalueNode) {
      FunctionType functionType = null;

      // Global function aliases should be registered with the type registry.
      if (rValue != null && rValue.isQualifiedName() && scope.isGlobal()) {
        Var var = scope.getVar(rValue.getQualifiedName());
        if (var != null && var.getType() instanceof FunctionType) {
          functionType = (FunctionType) var.getType();
          if (functionType != null &&
              (functionType.isConstructor() || functionType.isInterface())) {
            typeRegistry.declareType(name, functionType.getInstanceType());
          }
        }
        return functionType;
      }

      Node owner = null;
      if (lvalueNode != null) {
        owner = getPrototypePropertyOwner(lvalueNode);
      }

      Node errorRoot = rValue == null ? lvalueNode : rValue;
      boolean isFnLiteral =
          rValue != null && rValue.getType() == Token.FUNCTION;
      Node fnRoot = isFnLiteral ? rValue : null;
      Node parametersNode = isFnLiteral ?
          rValue.getFirstChild().getNext() : null;
      Node fnBlock = isFnLiteral ? parametersNode.getNext() : null;

      if (functionType == null && info != null && info.hasType()) {
        JSType type = info.getType().evaluate(scope, typeRegistry);

        // Known to be not null since we have the FUNCTION token there.
        type = type.restrictByNotNullOrUndefined();
        if (type.isFunctionType()) {
          functionType = (FunctionType) type;
          functionType.setJSDocInfo(info);
        }
      }

      if (functionType == null) {
        // Find the type of any overridden function.
        FunctionType overriddenPropType = null;
        if (lvalueNode != null && lvalueNode.getType() == Token.GETPROP &&
            lvalueNode.isQualifiedName()) {
          Var var = scope.getVar(
              lvalueNode.getFirstChild().getQualifiedName());
          if (var != null) {
            ObjectType ownerType = ObjectType.cast(var.getType());
            if (ownerType != null) {
              String propName = lvalueNode.getLastChild().getString();
              overriddenPropType = findOverriddenFunction(ownerType, propName);
            }
          }
        }

        functionType =
            new FunctionTypeBuilder(name, compiler, errorRoot, sourceName,
                scope)
            .setSourceNode(fnRoot)
            .inferFromOverriddenFunction(overriddenPropType, parametersNode)
            .inferTemplateTypeName(info)
            .inferReturnType(info)
            .inferInheritance(info)
            .inferThisType(info, owner)
            .inferParameterTypes(parametersNode, info)
            .inferReturnStatementsAsLastResort(fnBlock)
            .buildAndRegister();
      }

      // assigning the function type to the function node
      if (rValue != null) {
        setDeferredType(rValue, functionType);
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
      if (propType instanceof FunctionType) {
        return (FunctionType) propType;
      } else {
        // If it's not, then check to see if it's implemented
        // on an implemented interface.
        for (ObjectType iface :
                 ownerType.getCtorImplementedInterfaces()) {
          propType = iface.getPropertyType(propName);
          if (propType instanceof FunctionType) {
            return (FunctionType) propType;
          }
        }
      }

      return null;
    }

    /**
     * Gets an enum type. If the definition is correct, the object literal used
     * to define the enum is traversed to gather the elements name, and this
     * method checks for duplicates. This method also enforces that all
     * elements' name be syntactic constants according to the
     * {@link CodingConvention} used.
     *
     * @param name the enum's name such as {@code HELLO} or {@code goog.foo.BAR}
     * @param value the enum's original value. This value may be {@code null}.
     * @param parent the value's parent
     * @param elementsType the type of the elements of this enum
     * @return the enum type
     */
    private EnumType getEnumType(String name, Node parent,
        Node value, JSType elementsType) {
      EnumType enumType = null;

      // no value with @enum
      if (value != null) {
        if (value.getType() == Token.OBJECTLIT) {
          // collect enum elements
          enumType = typeRegistry.createEnumType(name, elementsType);

          // populate the enum type.
          Node key = value.getFirstChild();
          while (key != null) {
            String keyName = NodeUtil.getStringValue(key);

            if (enumType.hasOwnProperty(keyName)) {
              compiler.report(JSError.make(sourceName, key, ENUM_DUP, keyName));
            } else if (!codingConvention.isValidEnumKey(keyName)) {
              compiler.report(
                  JSError.make(sourceName, key, ENUM_NOT_CONSTANT, keyName));
            } else {
              enumType.defineElement(keyName, key);
            }
            key = key.getNext();
          }
        } else if (value.isQualifiedName()) {
          Var var = scope.getVar(value.getQualifiedName());
          if (var != null && var.getType() instanceof EnumType) {
            enumType = (EnumType) var.getType();
          }
        }
      }

      if (enumType == null) {
        compiler.report(JSError.make(sourceName, parent, ENUM_INITIALIZER));
      } else if (scope.isGlobal()) {
        if (name != null && !name.isEmpty()) {
          typeRegistry.declareType(name, enumType.getElementsType());
        }
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
      boolean shouldDeclareOnGlobalThis = false;
      if (n.getType() == Token.NAME) {
        Preconditions.checkArgument(
            parent.getType() == Token.FUNCTION ||
            parent.getType() == Token.VAR ||
            parent.getType() == Token.LP ||
            parent.getType() == Token.CATCH);
        shouldDeclareOnGlobalThis = scope.isGlobal() &&
            (parent.getType() == Token.VAR ||
             parent.getType() == Token.FUNCTION);
      } else {
        Preconditions.checkArgument(
            n.getType() == Token.GETPROP &&
            (parent.getType() == Token.ASSIGN ||
             parent.getType() == Token.EXPR_RESULT));
      }
      String variableName = n.getQualifiedName();
      Preconditions.checkArgument(!variableName.isEmpty());

      // If n is a property, then we should really declare it in the
      // scope where the root object appears. This helps out people
      // who declare "global" names in an anonymous namespace.
      Scope scopeToDeclareIn = scope;
      if (n.getType() == Token.GETPROP && !scope.isGlobal() &&
          isQnameRootedInGlobalScope(n)) {
        Scope globalScope = scope.getGlobalScope();

        // don't try to declare in the global scope if there's
        // already a symbol there with this name.
        if (!globalScope.isDeclared(variableName, false)) {
          scopeToDeclareIn = scope.getGlobalScope();
        }
      }

      // declared in closest scope?
      if (scopeToDeclareIn.isDeclared(variableName, false)) {
        Var oldVar = scopeToDeclareIn.getVar(variableName);
        validator.expectUndeclaredVariable(
            sourceName, n, parent, oldVar, variableName, type);
      } else {
        if (!inferred) {
          setDeferredType(n, type);
        }
        CompilerInput input = compiler.getInput(sourceName);
        boolean isExtern = input.isExtern();
        Var newVar =
            scopeToDeclareIn.declare(variableName, n, type, input, inferred);

        if (shouldDeclareOnGlobalThis) {
          ObjectType globalThis =
              typeRegistry.getNativeObjectType(JSTypeNative.GLOBAL_THIS);
          if (inferred) {
            globalThis.defineInferredProperty(variableName,
                type == null ?
                    getNativeType(JSTypeNative.NO_TYPE) :
                    type,
                isExtern, n);
          } else {
            globalThis.defineDeclaredProperty(variableName, type, isExtern, n);
          }
        }

        // We need to do some additional work for constructors and interfaces.
        if (type instanceof FunctionType &&
            // We don't want to look at empty function types.
            !type.isEmptyType()) {
          FunctionType fnType = (FunctionType) type;
          if ((fnType.isConstructor() || fnType.isInterface()) &&
              !fnType.equals(getNativeType(U2U_CONSTRUCTOR_TYPE))) {
            // Declare var.prototype in the scope chain.
            FunctionType superClassCtor = fnType.getSuperClassConstructor();
            scopeToDeclareIn.declare(variableName + ".prototype", n,
                fnType.getPrototype(), input,
                /* declared iff there's an explicit supertype */
                superClassCtor == null ||
                superClassCtor.getInstanceType().equals(
                    getNativeType(OBJECT_TYPE)));

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
    }

    /**
     * Check if the given node is a property of a name in the global scope.
     */
    private boolean isQnameRootedInGlobalScope(Node n) {
      Node root = NodeUtil.getRootOfQualifiedName(n);
      if (root.getType() == Token.NAME) {
        Var var = scope.getVar(root.getString());
        if (var != null) {
          return var.isGlobal();
        }
      }
      return false;
    }

    /**
     * Look for a type declaration on a GETPROP node.
     *
     * @param info The doc info for this property.
     * @param n A top-level GETPROP node (it should not be contained inside
     *     another GETPROP).
     * @param rhsValue The node that {@code n} is being initialized to,
     *     or {@code null} if this is a stub declaration.
     */
    private JSType getDeclaredGetPropType(NodeTraversal t, JSDocInfo info,
        Node n, @Nullable Node rhsValue) {
      if (info != null && info.hasType()) {
        return getDeclaredTypeInAnnotation(t, n, info);
      } else if (info != null && info.hasEnumParameterType()) {
        return n.getJSType();
      } else if (rhsValue != null &&
          rhsValue.getType() == Token.FUNCTION) {
        return rhsValue.getJSType();
      } else {
        return getDeclaredTypeInAnnotation(t, n, info);
      }
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
        ObjectType superClass = ObjectType.cast(
            typeRegistry.getType(relationship.superclassName));
        ObjectType subClass = ObjectType.cast(
            typeRegistry.getType(relationship.subclassName));
        if (superClass != null && subClass != null) {
          FunctionType superCtor = superClass.getConstructor();
          FunctionType subCtor = subClass.getConstructor();

          if (relationship.type == SubclassType.INHERITS) {
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

      // Function prototypes are special.
      // It's a common JS idiom to do:
      // F.prototype = { ... };
      // So if F does not have an explicitly declared super type,
      // allow F.prototype to be redefined arbitrarily.
      if ("prototype".equals(propName)) {
        Var qVar = scope.getVar(qName);
        if (qVar != null) {
          if (!qVar.isTypeInferred()) {
            // Just ignore assigns to declared prototypes.
            return;
          }
          if (qVar.getScope() == scope) {
            scope.undeclare(qVar);
          }
        }
      }

      // Precedence of type information on GETPROPs:
      // 1) @type annnotation / @enum annotation
      // 2) ASSIGN to FUNCTION literal
      // 3) @param/@return annotation (with no function literal)
      // 4) ASSIGN to anything else
      //
      // 1 and 3 are declarations, 4 is inferred, and 2 is a declaration iff
      // the function has not been declared before.
      //
      // FUNCTION literals are special because TypedScopeCreator is very smart
      // about getting as much type information as possible for them.

      // Determining type for #1 + #2 + #3
      JSType valueType = getDeclaredGetPropType(t, info, n, rhsValue);
      if (valueType == null && rhsValue != null) {
        // Determining type for #4
        valueType = rhsValue.getJSType();
      }

      if (valueType == null) {
        if (parent.getType() == Token.EXPR_RESULT) {
          stubDeclarations.add(new StubDeclaration(
              n,
              t.getInput() != null && t.getInput().isExtern(),
              ownerName));
        }

        return;
      }

      boolean inferred = true;
      if (info != null) {
        // Determining declaration for #1 + #3
        inferred = !(info.hasType() || info.hasEnumParameterType() ||
            FunctionTypeBuilder.isFunctionTypeDeclaration(info));
      }

      if (inferred) {
        // Determining declaration for #2
        inferred = !(rhsValue != null &&
            rhsValue.getType() == Token.FUNCTION &&
            !scope.isDeclared(qName, false));
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
            ownerType.defineDeclaredProperty(propName, valueType, isExtern, n);
          }
        }

        // If the property is already declared, the error will be
        // caught when we try to declare it in the current scope.
        defineSlot(n, parent, valueType, inferred);
      } else if (rhsValue != null &&
          rhsValue.getType() == Token.TRUE) {
        // We declare these for delegate proxy method properties.
        ObjectType ownerType = getObjectSlot(ownerName);
        if (ownerType instanceof FunctionType) {
          JSType ownerTypeOfThis = ((FunctionType) ownerType).getTypeOfThis();
          String delegateName = codingConvention.getDelegateSuperclassName();
          JSType delegateType = delegateName == null ?
              null : typeRegistry.getType(delegateName);
          if (delegateType != null &&
              ownerTypeOfThis.isSubtype(delegateType)) {
            defineSlot(n, parent, getNativeType(BOOLEAN_TYPE),
                true);
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
              propName, unknownType, isExtern, n);
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

      public void visit(NodeTraversal t, Node n, Node parent) {
        if (n.getType() == Token.EXPR_RESULT) {
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
        JSType jsType = getDeclaredGetPropType(t, info, member, value);
        Node name = member.getLastChild();
        if (jsType != null &&
            (name.getType() == Token.NAME || name.getType() == Token.STRING)) {
          thisType.defineDeclaredProperty(
              name.getString(),
              jsType,
              false /* functions with implementations are not in externs */,
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

        case Token.ASSIGN:
          // Handle typedefs.
          checkForOldStyleTypedef(t, n);
          break;

        case Token.VAR:
          // Handle typedefs.
          if (n.hasOneChild()) {
            checkForOldStyleTypedef(t, n);
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
      if (candidate.getType() == Token.GETPROP) {
        defineSlot(candidate, candidate.getParent(),
            getNativeType(NO_TYPE), false);
      }
    }

    /**
     * Handle typedefs.
     * @param t The current traversal.
     * @param candidate An ASSIGN or VAR node.
     */
    // TODO(nicksantos): Kill this.
    private void checkForOldStyleTypedef(NodeTraversal t, Node candidate) {
      // old-style typedefs
      String typedef = codingConvention.identifyTypeDefAssign(candidate);
      if (typedef != null) {
        // TODO(nicksantos|user): This is a terrible, terrible hack
        // to bail out on recusive typedefs. We'll eventually need
        // to handle these properly.
        typeRegistry.declareType(typedef, getNativeType(UNKNOWN_TYPE));

        JSDocInfo info = candidate.getJSDocInfo();
        JSType realType = null;
        if (info != null && info.getType() != null) {
          realType = info.getType().evaluate(scope, typeRegistry);
        }

        if (realType == null) {
          compiler.report(
              JSError.make(
                  t.getSourceName(), candidate, MALFORMED_TYPEDEF, typedef));
        }

        typeRegistry.overwriteDeclaredType(typedef, realType);

        // Duplicate typedefs get handled when we try to register
        // this typedef in the scope.
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

      if (n.getType() == Token.LP && parent == scope.getRootNode()) {
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
      FunctionType functionType = (FunctionType) functionNode.getJSType();
      if (functionType != null) {
        Node jsDocParameters = functionType.getParametersNode();
        if (jsDocParameters != null) {
          Node jsDocParameter = jsDocParameters.getFirstChild();
          for (Node astParameter : astParameters.children()) {
            if (jsDocParameter != null) {
              defineSlot(astParameter, functionNode,
                  jsDocParameter.getJSType(), true);
              jsDocParameter = jsDocParameter.getNext();
            } else {
              defineSlot(astParameter, functionNode, null, true);
            }
          }
        }
      }
    } // end declareArguments
  } // end LocalScopeBuilder
}
