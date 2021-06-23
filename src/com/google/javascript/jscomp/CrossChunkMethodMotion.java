/*
 * Copyright 2008 The Closure Compiler Authors.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.javascript.jscomp.AnalyzePrototypeProperties.ClassMemberFunction;
import com.google.javascript.jscomp.AnalyzePrototypeProperties.NameInfo;
import com.google.javascript.jscomp.AnalyzePrototypeProperties.Property;
import com.google.javascript.jscomp.AnalyzePrototypeProperties.PrototypeProperty;
import com.google.javascript.jscomp.AnalyzePrototypeProperties.Symbol;
import com.google.javascript.rhino.Node;
import java.util.Collection;
import java.util.Iterator;

/** Move prototype methods into later chunks. */
public class CrossChunkMethodMotion implements CompilerPass {

  // Internal errors
  static final DiagnosticType NULL_COMMON_MODULE_ERROR = DiagnosticType.error(
      "JSC_INTERNAL_ERROR_MODULE_DEPEND",
      "null deepest common module");

  private final AbstractCompiler compiler;
  private final IdGenerator idGenerator;
  private final AnalyzePrototypeProperties analyzer;
  private final JSChunkGraph moduleGraph;
  private final boolean noStubFunctions;
  private final AstFactory astFactory;

  static final String STUB_METHOD_NAME = "JSCompiler_stubMethod";
  static final String UNSTUB_METHOD_NAME = "JSCompiler_unstubMethod";

  @VisibleForTesting
  public static final String STUB_DECLARATIONS =
      "var JSCompiler_stubMap = [];"
          + "function JSCompiler_stubMethod(JSCompiler_stubMethod_id) {"
          + "  return function() {"
          + "    return JSCompiler_stubMap[JSCompiler_stubMethod_id].apply("
          + "        this, arguments);"
          + "  };"
          + "}"
          + "function JSCompiler_unstubMethod("
          + "    JSCompiler_unstubMethod_id, JSCompiler_unstubMethod_body) {"
          + "  return JSCompiler_stubMap[JSCompiler_unstubMethod_id] = "
          + "      JSCompiler_unstubMethod_body;"
          + "}";

  /**
   * Creates a new pass for moving prototype properties.
   *
   * @param compiler The compiler.
   * @param idGenerator An id generator for method stubs.
   * @param canModifyExterns If true, then we can move prototype properties that are declared in the
   *     externs file.
   * @param noStubFunctions if true, we can move methods without stub functions in the parent
   *     chunk.
   */
  CrossChunkMethodMotion(
      AbstractCompiler compiler,
      IdGenerator idGenerator,
      boolean canModifyExterns,
      boolean noStubFunctions) {
    this.compiler = compiler;
    this.idGenerator = idGenerator;
    this.moduleGraph = compiler.getModuleGraph();
    this.analyzer =
        new AnalyzePrototypeProperties(
            compiler, moduleGraph, canModifyExterns, false /* anchorUnusedVars */, noStubFunctions);
    this.noStubFunctions = noStubFunctions;
    this.astFactory = compiler.createAstFactoryWithoutTypes();
  }

  @Override
  public void process(Node externRoot, Node root) {
    // If there are < 2 chunks, then we will never move anything,
    // so we're done.
    if (moduleGraph.getModuleCount() > 1) {
      analyzer.process(externRoot, root);
      moveMethods(analyzer.getAllNameInfo());
    }
  }

  /**
   * Move methods deeper in the chunk graph when possible.
   */
  private void moveMethods(Collection<NameInfo> allNameInfo) {
    boolean hasStubDeclaration = idGenerator.hasGeneratedAnyIds();
    for (NameInfo nameInfo : allNameInfo) {
      if (!nameInfo.isReferenced()) {
        // The code below can't do anything with unreferenced name
        // infos.  They should be skipped to avoid NPE since their
        // deepestCommonModuleRef is null.
        continue;
      }

      if (nameInfo.readsClosureVariables()) {
        continue;
      }

      JSChunk deepestCommonModuleRef = nameInfo.getDeepestCommonModuleRef();
      if (deepestCommonModuleRef == null) {
        compiler.report(JSError.make(NULL_COMMON_MODULE_ERROR));
        continue;
      }

      Iterator<Symbol> declarations =
          nameInfo.getDeclarations().descendingIterator();
      while (declarations.hasNext()) {
        Symbol symbol = declarations.next();
        if (symbol instanceof PrototypeProperty) {
          tryToMovePrototypeMethod(nameInfo, deepestCommonModuleRef, (PrototypeProperty) symbol);
        } else if (symbol instanceof ClassMemberFunction) {
          tryToMoveMemberFunction(nameInfo, deepestCommonModuleRef, (ClassMemberFunction) symbol);
        } // else it's a variable definition, and we don't move those.
      }
    }

    if (!noStubFunctions && !hasStubDeclaration && idGenerator
        .hasGeneratedAnyIds()) {
      // Declare stub functions in the top-most chunk.
      Node declarations = compiler.parseSyntheticCode(STUB_DECLARATIONS);
      NodeUtil.markNewScopesChanged(declarations, compiler);
      Node firstScript = compiler.getNodeForCodeInsertion(null);
      firstScript.addChildrenToFront(declarations.removeChildren());
      compiler.reportChangeToEnclosingScope(firstScript);
    }
  }

  private void tryToMovePrototypeMethod(
      NameInfo nameInfo, JSChunk deepestCommonModuleRef, PrototypeProperty prop) {

    // We should only move a property across chunks if:
    // 1) We can move it deeper in the chunk graph, and
    // 2) it's a function, and
    // 3) it is not a GETTER_DEF or a SETTER_DEF, and
    // 4) it does not refer to `super`
    // 5) the class is available in the global scope.
    //
    // #1 should be obvious. #2 is more subtle. It's possible
    // to copy off of a prototype, as in the code:
    // for (var k in Foo.prototype) {
    //   doSomethingWith(Foo.prototype[k]);
    // }
    // This is a common way to implement pseudo-multiple inheritance in JS.
    //
    // So if we move a prototype method into a deeper chunk, we must
    // replace it with a stub function so that it preserves its original
    // behavior.
    if (prop.getRootVar() == null || !prop.getRootVar().isGlobal()) {
      return;
    }

    if (nameInfo.referencesSuper()) {
      // It is illegal to move `super` outside of a member function def.
      return;
    }

    Node value = prop.getValue();
    Node valueParent = value.getParent();
    // Only attempt to move normal functions.
    if (!value.isFunction()
        // A GET or SET can't be deferred like a normal
        // FUNCTION property definition as a mix-in would get the result
        // of a GET instead of the function itself.
        || valueParent.isGetterDef()
        || valueParent.isSetterDef()) {
      return;
    }

    if (moduleGraph.dependsOn(deepestCommonModuleRef, prop.getModule())) {
      if (hasUnmovableRedeclaration(nameInfo, prop)) {
        // If it has been redeclared on the same object, skip it.
        return;
      }
      Node destParent = compiler.getNodeForCodeInsertion(deepestCommonModuleRef);
      if (valueParent.isMemberFunctionDef()) {
        movePrototypeObjectLiteralMethodShorthand(nameInfo.name, destParent, value);
      } else if (valueParent.isStringKey()) {
        movePrototypeObjectLiteralProperty(nameInfo.name, destParent, value);
      } else {
        // Note that computed properties should have been filtered out by
        // AnalyzePrototypeProperties, because they don't have a recognizable property name.
        // Getters and setters are filtered out by the code above
        checkState(valueParent.isAssign(), valueParent);
        movePrototypeDotMethodAssignment(destParent, value);
      }
    }
  }

  /**
   * Move a property defined by object literal assigned to `.prototype`.
   *
   * <pre><code>
   *     Foo.prototype = { propName: function() {}};
   * </code></pre>
   */
  private void movePrototypeObjectLiteralProperty(
      String propName, Node destParent, Node functionNode) {
    checkState(functionNode.isFunction(), functionNode);
    Node stringKey = functionNode.getParent();
    checkState(stringKey.isStringKey(), stringKey);
    Node prototypeObjectLiteral = stringKey.getParent();
    checkState(prototypeObjectLiteral.isObjectLit(), prototypeObjectLiteral);
    Node assignNode = prototypeObjectLiteral.getParent();
    checkState(
        assignNode.isAssign() && prototypeObjectLiteral.isSecondChildOf(assignNode), assignNode);
    Node ownerDotPrototypeNode = assignNode.getFirstChild();
    checkState(
        ownerDotPrototypeNode.isQualifiedName()
            && ownerDotPrototypeNode.getString().equals("prototype"),
        ownerDotPrototypeNode);

    if (noStubFunctions) {
      // Remove the definition from the object literal
      stringKey.detach();
      compiler.reportChangeToEnclosingScope(prototypeObjectLiteral);

      // Prepend definition to new chunk
      // Foo.prototype.propName = function() {};
      Node ownerDotPrototypeDotPropName =
          astFactory.createGetProp(ownerDotPrototypeNode.cloneTree(), propName);
      functionNode.detach();
      Node definitionStatement =
          astFactory
              .createAssignStatement(ownerDotPrototypeDotPropName, functionNode)
              .srcrefTreeIfMissing(stringKey);
      destParent.addChildToFront(definitionStatement);
      compiler.reportChangeToEnclosingScope(destParent);
    } else {
      int stubId = idGenerator.newId();
      // { propName: function() {} } => { propName: JSCompiler_stubMethod(0) }
      Node stubCall = createStubCall(functionNode, stubId);
      functionNode.replaceWith(stubCall);
      compiler.reportChangeToEnclosingScope(prototypeObjectLiteral);

      // Prepend definition to new chunk
      // Foo.prototype.propName = function() {};
      Node ownerDotPrototypeDotPropName =
          astFactory.createGetProp(ownerDotPrototypeNode.cloneTree(), propName);
      Node unstubCall = createUnstubCall(functionNode, stubId);
      Node definitionStatement =
          astFactory
              .createAssignStatement(ownerDotPrototypeDotPropName, unstubCall)
              .srcrefTreeIfMissing(stringKey);
      destParent.addChildToFront(definitionStatement);
      compiler.reportChangeToEnclosingScope(destParent);
    }
  }

  /**
   * Move a property defined by assignment to `.prototype` or `.prototype.propName`.
   *
   * <pre><code>
   *     Foo.prototype.propName = function() {};
   * </code></pre>
   */
  private void movePrototypeDotMethodAssignment(Node destParent, Node functionNode) {
    checkState(functionNode.isFunction(), functionNode);
    Node assignNode = functionNode.getParent();
    checkState(assignNode.isAssign() && functionNode.isSecondChildOf(assignNode), assignNode);
    Node definitionStatement = assignNode.getParent();
    checkState(definitionStatement.isExprResult(), assignNode);

    if (noStubFunctions) {
      // Remove the definition statement from its current location
      Node assignStatementParent = definitionStatement.getParent();
      definitionStatement.detach();
      compiler.reportChangeToEnclosingScope(assignStatementParent);

      // Prepend definition to new chunk
      // Foo.prototype.propName = function() {};
      destParent.addChildToFront(definitionStatement);
      compiler.reportChangeToEnclosingScope(destParent);
    } else {
      int stubId = idGenerator.newId();

      // replace function definition with temporary placeholder so we can clone the whole
      // assignment statement without cloning the function definition itself.
      Node originalDefinitionPlaceholder = astFactory.createEmpty();
      functionNode.replaceWith(originalDefinitionPlaceholder);
      Node newDefinitionStatement = definitionStatement.cloneTree();
      Node newDefinitionPlaceholder =
          newDefinitionStatement // EXPR_RESULT
              .getOnlyChild() // ASSIGN
              .getLastChild(); // EMPTY RHS node

      // convert original assignment statement to
      // owner.prototype.propName = JSCompiler_stubMethod(0);
      Node stubCall = createStubCall(functionNode, stubId);
      originalDefinitionPlaceholder.replaceWith(stubCall);
      compiler.reportChangeToEnclosingScope(definitionStatement);

      // Prepend new definition to new chunk
      // Foo.prototype.propName = JSCompiler_unstubMethod(0, function() {});
      Node unstubCall = createUnstubCall(functionNode, stubId);
      newDefinitionPlaceholder.replaceWith(unstubCall);
      destParent.addChildToFront(newDefinitionStatement);
      compiler.reportChangeToEnclosingScope(destParent);
    }
  }

  /**
   * Move a property defined by assignment to `.prototype` or `.prototype.propName`.
   *
   * <pre><code>
   *     Foo.prototype = { propName() {}};
   * </code></pre>
   */
  private void movePrototypeObjectLiteralMethodShorthand(
      String propName, Node destParent, Node functionNode) {
    checkState(functionNode.isFunction(), functionNode);
    Node memberFunctionDef = functionNode.getParent();
    checkState(memberFunctionDef.isMemberFunctionDef(), memberFunctionDef);
    Node prototypeObjectLiteral = memberFunctionDef.getParent();
    checkState(prototypeObjectLiteral.isObjectLit(), prototypeObjectLiteral);
    Node assignNode = prototypeObjectLiteral.getParent();
    checkState(
        assignNode.isAssign() && prototypeObjectLiteral.isSecondChildOf(assignNode), assignNode);
    Node ownerDotPrototypeNode = assignNode.getFirstChild();
    checkState(
        ownerDotPrototypeNode.isQualifiedName()
            && ownerDotPrototypeNode.getString().equals("prototype"),
        ownerDotPrototypeNode);

    if (noStubFunctions) {
      // Remove the definition from the object literal
      memberFunctionDef.detach();
      compiler.reportChangeToEnclosingScope(prototypeObjectLiteral);

      // Prepend definition to new chunk
      // Foo.prototype.propName = function() {};
      Node ownerDotPrototypeDotPropName =
          astFactory.createGetProp(ownerDotPrototypeNode.cloneTree(), propName);
      Node definitionStatement =
          astFactory
              .createAssignStatement(ownerDotPrototypeDotPropName, functionNode.detach())
              .srcrefTreeIfMissing(memberFunctionDef);
      destParent.addChildToFront(definitionStatement);
      compiler.reportChangeToEnclosingScope(destParent);
    } else {
      int stubId = idGenerator.newId();
      // { propName() {} } => { propName: JSCompiler_stubMethod(0) }
      Node stubCall = createStubCall(functionNode, stubId);
      memberFunctionDef.replaceWith(astFactory.createStringKey(propName, stubCall));
      compiler.reportChangeToEnclosingScope(prototypeObjectLiteral);

      // Prepend definition to new chunk
      // Foo.prototype.propName = function() {};
      Node ownerDotPrototypeDotPropName =
          astFactory.createGetProp(ownerDotPrototypeNode.cloneTree(), propName);
      Node unstubCall = createUnstubCall(functionNode.detach(), stubId);
      Node definitionStatement =
          astFactory
              .createAssignStatement(ownerDotPrototypeDotPropName, unstubCall)
              .srcrefTreeIfMissing(memberFunctionDef);
      destParent.addChildToFront(definitionStatement);
      compiler.reportChangeToEnclosingScope(destParent);
    }
  }

  /**
   * Returns a new Node to be used as the stub definition for a method.
   *
   * @param originalDefinition function Node whose definition is being stubbed
   * @param stubId ID to use for stubbing and unstubbing
   * @return a Node that looks like <code>JSCompiler_stubMethod(0)</code>
   */
  private Node createStubCall(Node originalDefinition, int stubId) {
    return astFactory
        .createCall(
            // We can't look up the type of the stub creating method, because we add its
            // definition after type checking.
            astFactory.createNameWithUnknownType(STUB_METHOD_NAME), astFactory.createNumber(stubId))
        .srcrefTreeIfMissing(originalDefinition);
  }

  /**
   * Returns a new Node to be used as the stub definition for a method.
   *
   * @param functionNode actual function definition to be attached. Must be detached now.
   * @param stubId ID to use for stubbing and unstubbing
   * @return a Node that looks like <code>JSCompiler_unstubMethod(0, function() {})</code>
   */
  private Node createUnstubCall(Node functionNode, int stubId) {
    return astFactory
        .createCall(
            // We can't look up the type of the stub creating method, because we add its
            // definition after type checking.
            astFactory.createNameWithUnknownType(UNSTUB_METHOD_NAME),
            astFactory.createNumber(stubId),
            functionNode)
        .srcrefTreeIfMissing(functionNode);
  }

  /**
   * If possible, move a class instance member function definition to the deepest chunk common to
   * all uses of the method.
   *
   * @param nameInfo information about all definitions of the given property name
   * @param deepestCommonModuleRef all uses of the method are either in this chunk or in chunks that
   *     depend on it
   * @param classMemberFunction definition of the method within its class body
   */
  private void tryToMoveMemberFunction(
      NameInfo nameInfo, JSChunk deepestCommonModuleRef, ClassMemberFunction classMemberFunction) {

    // We should only move a property across chunks if:
    // 1) We can move it deeper in the chunk graph,
    // 2) and it's a normal member function, and not a GETTER_DEF or a SETTER_DEF, or
    //    or the class constructor.
    // 3) and it does not refer to `super`, which would be invalid outside of the class.
    // 4) and the class is available in the global scope.
    Var rootVar = classMemberFunction.getRootVar();
    if (rootVar == null || !rootVar.isGlobal()) {
      return;
    }

    Node definitionNode = classMemberFunction.getDefinitionNode();

    // Only attempt to move normal member functions.
    // A getter or setter cannot be as easily defined outside of the class to which it belongs.
    if (!definitionNode.isMemberFunctionDef()) {
      return;
    }

    if (NodeUtil.isEs6ConstructorMemberFunctionDef(definitionNode)) {
      // Constructor functions cannot be moved.
      return;
    }

    if (nameInfo.referencesSuper()) {
      // Do not move methods containing `super`, because it doesn't work outside of a
      // class method or object literal method.
      return;
    }

    if (moduleGraph.dependsOn(deepestCommonModuleRef, classMemberFunction.getModule())) {
      if (hasUnmovableRedeclaration(nameInfo, classMemberFunction)) {
        // If it has been redeclared on the same object, skip it.
        return;
      }

      Node destinationParent = compiler.getNodeForCodeInsertion(deepestCommonModuleRef);
      String className = rootVar.getName();
      if (noStubFunctions) {
        moveClassInstanceMethodWithoutStub(className, definitionNode, destinationParent);
      } else {
        moveClassInstanceMethodWithStub(className, definitionNode, destinationParent);
      }
    }
  }

  private void moveClassInstanceMethodWithoutStub(
      String className, Node methodDefinition, Node destinationParent) {
    checkArgument(methodDefinition.isMemberFunctionDef(), methodDefinition);
    Node classMembers = checkNotNull(methodDefinition.getParent());
    checkState(classMembers.isClassMembers(), classMembers);
    Node classNode = classMembers.getParent();
    checkState(classNode.isClass(), classNode);

    methodDefinition.detach();
    compiler.reportChangeToEnclosingScope(classMembers);

    // ClassName.prototype.propertyName = function() {};
    Node classNameDotPrototypeDotPropName =
        astFactory.createGetProps(
            astFactory.createName(className, classNode.getJSType()),
            "prototype",
            methodDefinition.getString());
    Node functionNode = checkNotNull(methodDefinition.getOnlyChild());
    functionNode.detach();
    Node definitionStatementNode =
        astFactory
            .createAssignStatement(classNameDotPrototypeDotPropName, functionNode)
            .srcrefTreeIfMissing(methodDefinition);
    destinationParent.addChildToFront(definitionStatementNode);
    compiler.reportChangeToEnclosingScope(destinationParent);
  }

  private void moveClassInstanceMethodWithStub(
      String className, Node methodDefinition, Node destinationParent) {
    checkArgument(methodDefinition.isMemberFunctionDef(), methodDefinition);
    Node classMembers = checkNotNull(methodDefinition.getParent());
    checkState(classMembers.isClassMembers(), classMembers);
    Node classNode = classMembers.getParent();
    checkState(classNode.isClass(), classNode);

    int stubId = idGenerator.newId();

    // Put a stub definition after the class
    // ClassName.prototype.propertyName = JSCompiler_stubMethod(id);
    Node classNameDotPrototypeDotPropName =
        astFactory.createGetProps(
            astFactory.createName(className, classNode.getJSType()),
            "prototype",
            methodDefinition.getString());
    Node stubCall = createStubCall(methodDefinition, stubId);
    Node stubDefinitionStatement =
        astFactory
            .createAssignStatement(classNameDotPrototypeDotPropName, stubCall)
            .srcrefTreeIfMissing(methodDefinition);
    Node classDefiningStatement = NodeUtil.getEnclosingStatement(classMembers);
    stubDefinitionStatement.insertAfter(classDefiningStatement);

    // remove the definition from the class
    methodDefinition.detach();

    compiler.reportChangeToEnclosingScope(classMembers);

    // Prepend unstub definition to the new location.
    // ClassName.prototype.propertyName = JSCompiler_unstubMethod(id, function(...) {...});
    Node classNameDotPrototypeDotPropName2 = classNameDotPrototypeDotPropName.cloneTree();
    Node functionNode = checkNotNull(methodDefinition.getOnlyChild());
    functionNode.detach();
    Node unstubCall = createUnstubCall(functionNode, stubId);
    Node statementNode =
        astFactory
            .createAssignStatement(classNameDotPrototypeDotPropName2, unstubCall)
            .srcrefTreeIfMissing(methodDefinition);
    destinationParent.addChildToFront(statementNode);
    compiler.reportChangeToEnclosingScope(destinationParent);
  }

  static boolean hasUnmovableRedeclaration(NameInfo nameInfo, Property prop) {
    for (Symbol symbol : nameInfo.getDeclarations()) {
      if (symbol instanceof Property) {
        Property otherProp = (Property) symbol;
        // It is possible to do better here if the dependencies are well defined
        // but redefinitions are usually in optional chunks so it isn't likely
        // worth the effort to check.
        if (prop != otherProp
            && prop.getRootVar() == otherProp.getRootVar()
            && prop.getModule() != otherProp.getModule()) {
          return true;
        }
      }
    }
    return false;
  }
}
