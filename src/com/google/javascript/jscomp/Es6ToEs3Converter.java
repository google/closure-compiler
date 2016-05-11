/*
 * Copyright 2014 The Closure Compiler Authors.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts ES6 code to valid ES5 code. This class does most of the transpilation, and
 * https://github.com/google/closure-compiler/wiki/ECMAScript6 lists which ES6 features are
 * supported. Other classes that start with "Es6" do other parts of the transpilation.
 *
 * <p>In most cases, the output is valid as ES3 (hence the class name) but in some cases, if
 * the output language is set to ES5, we rely on ES5 features such as getters, setters,
 * and Object.defineProperties.
 *
 * @author tbreisacher@google.com (Tyler Breisacher)
 */
public final class Es6ToEs3Converter implements NodeTraversal.Callback, HotSwapCompilerPass {
  private final AbstractCompiler compiler;

  static final DiagnosticType CANNOT_CONVERT = DiagnosticType.error(
      "JSC_CANNOT_CONVERT",
      "This code cannot be converted from ES6. {0}");

  // TODO(tbreisacher): Remove this once we have implemented transpilation for all the features
  // we intend to support.
  static final DiagnosticType CANNOT_CONVERT_YET = DiagnosticType.error(
      "JSC_CANNOT_CONVERT_YET",
      "ES6 transpilation of ''{0}'' is not yet implemented.");

  static final DiagnosticType DYNAMIC_EXTENDS_TYPE = DiagnosticType.error(
      "JSC_DYNAMIC_EXTENDS_TYPE",
      "The class in an extends clause must be a qualified name.");

  static final DiagnosticType CLASS_REASSIGNMENT = DiagnosticType.error(
      "CLASS_REASSIGNMENT",
      "Class names defined inside a function cannot be reassigned.");

  static final DiagnosticType CONFLICTING_GETTER_SETTER_TYPE = DiagnosticType.error(
      "CONFLICTING_GETTER_SETTER_TYPE",
      "The types of the getter and setter for property ''{0}'' do not match.");

  static final DiagnosticType BAD_REST_PARAMETER_ANNOTATION = DiagnosticType.warning(
      "BAD_REST_PARAMETER_ANNOTATION",
      "Missing \"...\" in type annotation for rest parameter.");

  // The name of the index variable for populating the rest parameter array.
  private static final String REST_INDEX = "$jscomp$restIndex";

  // The name of the placeholder for the rest parameters.
  private static final String REST_PARAMS = "$jscomp$restParams";

  private static final String FRESH_SPREAD_VAR = "$jscomp$spread$args";

  private static final String FRESH_COMP_PROP_VAR = "$jscomp$compprop";

  private static final String ITER_BASE = "$jscomp$iter$";

  private static final String ITER_RESULT = "$jscomp$key$";

  // These functions are defined in js/es6_runtime.js
  static final String INHERITS = "$jscomp.inherits";

  public Es6ToEs3Converter(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, externs, this);
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverseEs6(compiler, scriptRoot, this);
  }

  /**
   * Some nodes must be visited pre-order in order to rewrite the
   * references to {@code this} correctly.
   * Everything else is translated post-order in {@link #visit}.
   */
  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.REST:
        visitRestParam(n, parent);
        break;
      case Token.GETTER_DEF:
      case Token.SETTER_DEF:
        if (compiler.getOptions().getLanguageOut() == LanguageMode.ECMASCRIPT3) {
          cannotConvert(n, "ES5 getters/setters (consider using --language_out=ES5)");
          return false;
        }
        break;
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.NAME:
        if (!n.isFromExterns() && isGlobalSymbol(t, n)) {
          initSymbolBefore(n);
        }
        break;
      case Token.GETPROP:
        if (!n.isFromExterns()) {
          visitGetprop(t, n);
        }
        break;
      case Token.OBJECTLIT:
        visitObject(n);
        break;
      case Token.MEMBER_FUNCTION_DEF:
        if (parent.isObjectLit()) {
          visitMemberFunctionDefInObjectLit(n, parent);
        }
        break;
      case Token.FOR_OF:
        visitForOf(n, parent);
        break;
      case Token.STRING_KEY:
        visitStringKey(n);
        break;
      case Token.CLASS:
        visitClass(n, parent);
        break;
      case Token.ARRAYLIT:
      case Token.NEW:
      case Token.CALL:
        for (Node child : n.children()) {
          if (child.isSpread()) {
            visitArrayLitOrCallWithSpread(n, parent);
            break;
          }
        }
        break;
      case Token.TAGGED_TEMPLATELIT:
        Es6TemplateLiterals.visitTaggedTemplateLiteral(t, n);
        break;
      case Token.TEMPLATELIT:
        if (!parent.isTaggedTemplateLit()) {
          Es6TemplateLiterals.visitTemplateLiteral(t, n);
        }
        break;
    }
  }

  /**
   * @return Whether {@code n} is a reference to the global "Symbol" function.
   */
  private boolean isGlobalSymbol(NodeTraversal t, Node n) {
    if (!n.matchesQualifiedName("Symbol")) {
      return false;
    }
    Var var = t.getScope().getVar("Symbol");
    return var == null || var.isGlobal();
  }

  /**
   * Inserts a call to $jscomp.initSymbol() before {@code n}.
   */
  private void initSymbolBefore(Node n) {
    compiler.ensureLibraryInjected("es6_runtime", false);
    Node statement = NodeUtil.getEnclosingStatement(n);
    Node initSymbol = IR.exprResult(IR.call(NodeUtil.newQName(compiler, "$jscomp.initSymbol")));
    statement.getParent().addChildBefore(initSymbol.useSourceInfoFromForTree(statement), statement);
    compiler.reportCodeChange();
  }

  // TODO(tbreisacher): Do this for all well-known symbols.
  private void visitGetprop(NodeTraversal t, Node n) {
    if (!n.matchesQualifiedName("Symbol.iterator")) {
      return;
    }
    if (isGlobalSymbol(t, n.getFirstChild())) {
      compiler.ensureLibraryInjected("es6_runtime", false);
      Node statement = NodeUtil.getEnclosingStatement(n);
      Node init = IR.exprResult(IR.call(NodeUtil.newQName(compiler, "$jscomp.initSymbolIterator")));
      statement.getParent().addChildBefore(init.useSourceInfoFromForTree(statement), statement);
      compiler.reportCodeChange();
    }
  }

  /**
   * Converts a member definition in an object literal to an ES3 key/value pair.
   * Member definitions in classes are handled in {@link #visitClass}.
   */
  private void visitMemberFunctionDefInObjectLit(Node n, Node parent) {
    String name = n.getString();
    Node stringKey = IR.stringKey(name, n.getFirstChild().detachFromParent());
    stringKey.setJSDocInfo(n.getJSDocInfo());
    parent.replaceChild(n, stringKey);
    compiler.reportCodeChange();
  }

  /**
   * Converts extended object literal {a} to {a:a}.
   */
  private void visitStringKey(Node n) {
    if (!n.hasChildren()) {
      Node name = IR.name(n.getString());
      name.useSourceInfoIfMissingFrom(n);
      n.addChildToBack(name);
      compiler.reportCodeChange();
    }
  }

  private void visitForOf(Node node, Node parent) {
    Node variable = node.removeFirstChild();
    Node iterable = node.removeFirstChild();
    Node body = node.removeFirstChild();

    Node iterName = IR.name(ITER_BASE + compiler.getUniqueNameIdSupplier().get());
    iterName.makeNonIndexable();
    Node getNext = IR.call(IR.getprop(iterName.cloneTree(), IR.string("next")));
    String variableName;
    int declType;
    if (variable.isName()) {
      declType = Token.NAME;
      variableName = variable.getQualifiedName();
    } else {
      Preconditions.checkState(NodeUtil.isNameDeclaration(variable),
          "Expected var, let, or const. Got %s", variable);
      declType = variable.getType();
      variableName = variable.getFirstChild().getQualifiedName();
    }
    Node iterResult = IR.name(ITER_RESULT + variableName);
    iterResult.makeNonIndexable();

    Node init = IR.var(iterName.cloneTree(), makeIterator(compiler, iterable));
    Node initIterResult = iterResult.cloneTree();
    initIterResult.addChildToFront(getNext.cloneTree());
    init.addChildToBack(initIterResult);

    Node cond = IR.not(IR.getprop(iterResult.cloneTree(), IR.string("done")));
    Node incr = IR.assign(iterResult.cloneTree(), getNext.cloneTree());

    Node declarationOrAssign;
    if (declType == Token.NAME) {
      declarationOrAssign = IR.exprResult(IR.assign(
          IR.name(variableName).useSourceInfoFrom(variable),
          IR.getprop(iterResult.cloneTree(), IR.string("value"))));
    } else {
      declarationOrAssign = new Node(
          declType,
          IR.name(variableName).useSourceInfoFrom(variable.getFirstChild()));
      declarationOrAssign.getFirstChild().addChildToBack(
          IR.getprop(iterResult.cloneTree(), IR.string("value")));
    }
    body.addChildToFront(declarationOrAssign);

    Node newFor = IR.forNode(init, cond, incr, body);
    newFor.useSourceInfoIfMissingFromForTree(node);
    parent.replaceChild(node, newFor);
    compiler.reportCodeChange();
  }

  private void checkClassReassignment(Node clazz) {
    Node name = NodeUtil.getNameNode(clazz);
    Node enclosingFunction = NodeUtil.getEnclosingFunction(clazz);
    if (enclosingFunction == null) {
      return;
    }
    CheckClassAssignments checkAssigns = new CheckClassAssignments(name);
    NodeTraversal.traverseEs6(compiler, enclosingFunction, checkAssigns);
  }

  /**
   * Processes a rest parameter
   */
  private void visitRestParam(Node restParam, Node paramList) {
    Node functionBody = paramList.getLastSibling();
    int restIndex = paramList.getIndexOfChild(restParam);
    String paramName = restParam.getFirstChild().getString();

    Node nameNode = IR.name(paramName);
    nameNode.setVarArgs(true);
    nameNode.setJSDocInfo(restParam.getJSDocInfo());
    paramList.replaceChild(restParam, nameNode);

    // Make sure rest parameters are typechecked
    JSTypeExpression type = null;
    JSDocInfo info = restParam.getJSDocInfo();
    if (info != null) {
      type = info.getType();
    } else {
      JSDocInfo functionInfo = NodeUtil.getBestJSDocInfo(paramList.getParent());
      if (functionInfo != null) {
        type = functionInfo.getParameterType(paramName);
      }
    }
    if (type != null && type.getRoot().getType() != Token.ELLIPSIS) {
      compiler.report(JSError.make(restParam, BAD_REST_PARAMETER_ANNOTATION));
    }

    if (!functionBody.hasChildren()) {
      // If function has no body, we are done!
      compiler.reportCodeChange();
      return;
    }

    Node newBlock = IR.block().useSourceInfoFrom(functionBody);
    Node name = IR.name(paramName);
    Node let = IR.let(name, IR.name(REST_PARAMS))
        .useSourceInfoIfMissingFromForTree(functionBody);
    newBlock.addChildToFront(let);

    for (Node child : functionBody.children()) {
      newBlock.addChildToBack(child.detachFromParent());
    }

    if (type != null) {
      Node arrayType = IR.string("Array");
      Node typeNode = type.getRoot();
      Node memberType =
          typeNode.getType() == Token.ELLIPSIS
              ? typeNode.getFirstChild().cloneTree()
              : typeNode.cloneTree();
      arrayType.addChildToFront(
          new Node(Token.BLOCK, memberType).useSourceInfoIfMissingFrom(typeNode));
      JSDocInfoBuilder builder = new JSDocInfoBuilder(false);
      builder.recordType(
          new JSTypeExpression(new Node(Token.BANG, arrayType), restParam.getSourceFileName()));
      name.setJSDocInfo(builder.build());
    }

    Node newArr = IR.var(IR.name(REST_PARAMS), IR.arraylit());
    functionBody.addChildToFront(newArr.useSourceInfoIfMissingFromForTree(restParam));
    Node init = IR.var(IR.name(REST_INDEX), IR.number(restIndex));
    Node cond = IR.lt(IR.name(REST_INDEX), IR.getprop(IR.name("arguments"), IR.string("length")));
    Node incr = IR.inc(IR.name(REST_INDEX), false);
    Node body = IR.block(IR.exprResult(IR.assign(
        IR.getelem(IR.name(REST_PARAMS), IR.sub(IR.name(REST_INDEX), IR.number(restIndex))),
        IR.getelem(IR.name("arguments"), IR.name(REST_INDEX)))));
    functionBody.addChildAfter(IR.forNode(init, cond, incr, body)
        .useSourceInfoIfMissingFromForTree(restParam), newArr);
    functionBody.addChildToBack(newBlock);
    compiler.reportCodeChange();

    // For now, we are running transpilation before type-checking, so we'll
    // need to make sure changes don't invalidate the JSDoc annotations.
    // Therefore we keep the parameter list the same length and only initialize
    // the values if they are set to undefined.
  }

  /**
   * Processes array literals or calls containing spreads. Examples:
   * [1, 2, ...x, 4, 5] => [].concat([1, 2], $jscomp.arrayFromIterable(x), [4, 5])
   *
   * f(...arr) => f.apply(null, [].concat($jscomp.arrayFromIterable(arr)))
   *
   * new F(...args) =>
   *     new Function.prototype.bind.apply(F, [].concat($jscomp.arrayFromIterable(args)))
   */
  private void visitArrayLitOrCallWithSpread(Node node, Node parent) {
    Preconditions.checkArgument(node.isCall() || node.isArrayLit() || node.isNew());
    List<Node> groups = new ArrayList<>();
    Node currGroup = null;
    Node callee = node.isArrayLit() ? null : node.removeFirstChild();
    Node currElement = node.removeFirstChild();
    while (currElement != null) {
      if (currElement.isSpread()) {
        if (currGroup != null) {
          groups.add(currGroup);
          currGroup = null;
        }
        groups.add(arrayFromIterable(compiler, currElement.removeFirstChild()));
      } else {
        if (currGroup == null) {
          currGroup = IR.arraylit();
        }
        currGroup.addChildToBack(currElement);
      }
      currElement = node.removeFirstChild();
    }
    if (currGroup != null) {
      groups.add(currGroup);
    }
    Node result = null;
    Node firstGroup = node.isNew() ? IR.arraylit(IR.nullNode()) : IR.arraylit();
    Node joinedGroups =
        IR.call(IR.getprop(firstGroup, IR.string("concat")), groups.toArray(new Node[0]));
    if (node.isArrayLit()) {
      result = joinedGroups;
    } else if (node.isCall()) {
      if (NodeUtil.mayHaveSideEffects(callee) && callee.isGetProp()) {
        Node statement = node;
        while (!NodeUtil.isStatement(statement)) {
          statement = statement.getParent();
        }
        Node freshVar = IR.name(FRESH_SPREAD_VAR + compiler.getUniqueNameIdSupplier().get());
        Node n = IR.var(freshVar.cloneTree());
        n.useSourceInfoIfMissingFromForTree(statement);
        statement.getParent().addChildBefore(n, statement);
        callee.addChildToFront(IR.assign(freshVar.cloneTree(), callee.removeFirstChild()));
        result = IR.call(
            IR.getprop(callee, IR.string("apply")),
            freshVar,
            joinedGroups);
      } else {
        Node context = callee.isGetProp() ? callee.getFirstChild().cloneTree() : IR.nullNode();
        result = IR.call(IR.getprop(callee, IR.string("apply")), context, joinedGroups);
      }
    } else {
      if (compiler.getOptions().getLanguageOut() == LanguageMode.ECMASCRIPT3) {
        // TODO(tbreisacher): Support this in ES3 too by not relying on Function.bind.
        cannotConvert(node, "\"...\" passed to a constructor (consider using --language_out=ES5)");
      }
      Node bindApply = NodeUtil.newQName(compiler,
          "Function.prototype.bind.apply");
      result = IR.newNode(IR.call(bindApply, callee, joinedGroups));
    }
    result.useSourceInfoIfMissingFromForTree(node);
    parent.replaceChild(node, result);
    compiler.reportCodeChange();
  }

  private void visitObject(Node obj) {
    for (Node child : obj.children()) {
      if (child.isComputedProp()) {
        visitObjectWithComputedProperty(obj);
        return;
      }
    }
  }

  private void visitObjectWithComputedProperty(Node obj) {
    Preconditions.checkArgument(obj.isObjectLit());
    List<Node> props = new ArrayList<>();
    Node currElement = obj.getFirstChild();

    while (currElement != null) {
      if (currElement.getBooleanProp(Node.COMPUTED_PROP_GETTER)
          || currElement.getBooleanProp(Node.COMPUTED_PROP_SETTER)) {
        cannotConvertYet(currElement, "computed getter/setter in an object literal");
        return;
      } else if (currElement.isGetterDef() || currElement.isSetterDef()) {
        currElement = currElement.getNext();
      } else {
        Node nextNode = currElement.getNext();
        obj.removeChild(currElement);
        props.add(currElement);
        currElement = nextNode;
      }
    }

    String objName = FRESH_COMP_PROP_VAR + compiler.getUniqueNameIdSupplier().get();

    props = Lists.reverse(props);
    Node result = IR.name(objName);
    for (Node propdef : props) {
      if (propdef.isComputedProp()) {
        Node propertyExpression = propdef.removeFirstChild();
        Node value = propdef.removeFirstChild();
        result = IR.comma(
            IR.assign(
                IR.getelem(
                    IR.name(objName),
                    propertyExpression),
                value),
            result);
      } else {
        if (!propdef.hasChildren()) {
          Node name = IR.name(propdef.getString()).useSourceInfoIfMissingFrom(propdef);
          propdef.addChildToBack(name);
        }
        Node val = propdef.removeFirstChild();
        propdef.setType(Token.STRING);
        int type = propdef.isQuotedString() ? Token.GETELEM : Token.GETPROP;
        Node access = new Node(type, IR.name(objName), propdef);
        result = IR.comma(IR.assign(access, val), result);
      }
    }

    Node statement = obj;
    while (!NodeUtil.isStatement(statement)) {
      statement = statement.getParent();
    }

    result.useSourceInfoIfMissingFromForTree(obj);
    obj.getParent().replaceChild(obj, result);

    Node var = IR.var(IR.name(objName), obj);
    var.useSourceInfoIfMissingFromForTree(statement);
    statement.getParent().addChildBefore(var, statement);
    compiler.reportCodeChange();
  }

  /**
   * Classes are processed in 3 phases:
   * <ol>
   *   <li>The class name is extracted.
   *   <li>Class members are processed and rewritten.
   *   <li>The constructor is built.
   * </ol>
   */
  private void visitClass(final Node classNode, final Node parent) {
    checkClassReassignment(classNode);
    // Collect Metadata
    ClassDeclarationMetadata metadata = ClassDeclarationMetadata.create(classNode, parent);

    if (metadata == null || metadata.fullClassName == null) {
      cannotConvert(parent, "Can only convert classes that are declarations or the right hand"
          + " side of a simple assignment.");
      return;
    }
    if (metadata.hasSuperClass() && !metadata.superClassNameNode.isQualifiedName()) {
      compiler.report(JSError.make(metadata.superClassNameNode, DYNAMIC_EXTENDS_TYPE));
      return;
    }

    Preconditions.checkState(NodeUtil.isStatement(metadata.insertionPoint),
        "insertion point must be a statement: %s", metadata.insertionPoint);

    Node constructor = null;
    JSDocInfo ctorJSDocInfo = null;
    // Process all members of the class
    Node classMembers = classNode.getLastChild();
    for (Node member : classMembers.children()) {
      if ((member.isComputedProp()
              && (member.getBooleanProp(Node.COMPUTED_PROP_GETTER)
                  || member.getBooleanProp(Node.COMPUTED_PROP_SETTER)))
          || (member.isGetterDef() || member.isSetterDef())) {
        visitComputedPropInClass(member, metadata);
      } else if (member.isMemberFunctionDef() && member.getString().equals("constructor")) {
        ctorJSDocInfo = member.getJSDocInfo();
        constructor = member.getFirstChild().detachFromParent();
        if (!metadata.anonymous) {
          // Turns class Foo { constructor: function() {} } into function Foo() {},
          // i.e. attaches the name to the ctor function.
          constructor.replaceChild(
              constructor.getFirstChild(), metadata.classNameNode.cloneNode());
        }
      } else if (member.isEmpty()) {
        // Do nothing.
      } else {
        Preconditions.checkState(member.isMemberFunctionDef() || member.isComputedProp(),
            "Unexpected class member:", member);
        Preconditions.checkState(!member.getBooleanProp(Node.COMPUTED_PROP_VARIABLE),
            "Member variables should have been transpiled earlier:", member);
        visitClassMember(member, metadata);
      }
    }

    if (metadata.definePropertiesObjForPrototype.hasChildren()) {
      Node definePropsCall =
          IR.exprResult(
              IR.call(
                  NodeUtil.newQName(compiler, "Object.defineProperties"),
                  NodeUtil.newQName(compiler, metadata.fullClassName + ".prototype"),
                  metadata.definePropertiesObjForPrototype));
      definePropsCall.useSourceInfoIfMissingFromForTree(classNode);
      metadata.insertNodeAndAdvance(definePropsCall);

      visitObject(metadata.definePropertiesObjForPrototype);
    }

    if (metadata.definePropertiesObjForClass.hasChildren()) {
      Node definePropsCall =
          IR.exprResult(
              IR.call(
                  NodeUtil.newQName(compiler, "Object.defineProperties"),
                  NodeUtil.newQName(compiler, metadata.fullClassName),
                  metadata.definePropertiesObjForClass));
      definePropsCall.useSourceInfoIfMissingFromForTree(classNode);
      metadata.insertNodeAndAdvance(definePropsCall);

      visitObject(metadata.definePropertiesObjForClass);
    }


    Preconditions.checkNotNull(constructor);

    JSDocInfo classJSDoc = NodeUtil.getBestJSDocInfo(classNode);
    JSDocInfoBuilder newInfo = JSDocInfoBuilder.maybeCopyFrom(classJSDoc);

    newInfo.recordConstructor();

    Node enclosingStatement = NodeUtil.getEnclosingStatement(classNode);
    if (metadata.hasSuperClass()) {
      String superClassString = metadata.superClassNameNode.getQualifiedName();
      if (newInfo.isInterfaceRecorded()) {
        newInfo.recordExtendedInterface(new JSTypeExpression(new Node(Token.BANG,
            IR.string(superClassString)),
            metadata.superClassNameNode.getSourceFileName()));
      } else {
        if (!classNode.isFromExterns()) {
          Node inherits = IR.call(
              NodeUtil.newQName(compiler, INHERITS),
              NodeUtil.newQName(compiler, metadata.fullClassName),
              NodeUtil.newQName(compiler, superClassString));
          Node inheritsCall = IR.exprResult(inherits);
          compiler.ensureLibraryInjected("es6_runtime", false);

          inheritsCall.useSourceInfoIfMissingFromForTree(classNode);
          enclosingStatement.getParent().addChildAfter(inheritsCall, enclosingStatement);
        }
        newInfo.recordBaseType(new JSTypeExpression(new Node(Token.BANG,
            IR.string(superClassString)),
            metadata.superClassNameNode.getSourceFileName()));
      }
    }

    addTypeDeclarations(metadata, enclosingStatement);

    // Classes are @struct by default.
    if (!newInfo.isUnrestrictedRecorded() && !newInfo.isDictRecorded()
        && !newInfo.isStructRecorded()) {
      newInfo.recordStruct();
    }

    if (ctorJSDocInfo != null) {
      if (!ctorJSDocInfo.getSuppressions().isEmpty()) {
        newInfo.recordSuppressions(ctorJSDocInfo.getSuppressions());
      }

      for (String param : ctorJSDocInfo.getParameterNames()) {
        newInfo.recordParameter(param, ctorJSDocInfo.getParameterType(param));
        newInfo.recordParameterDescription(param, ctorJSDocInfo.getDescriptionForParameter(param));
      }

      for (JSTypeExpression thrown : ctorJSDocInfo.getThrownTypes()) {
        newInfo.recordThrowType(thrown);
        newInfo.recordThrowDescription(thrown, ctorJSDocInfo.getThrowsDescriptionForType(thrown));
      }

      JSDocInfo.Visibility visibility = ctorJSDocInfo.getVisibility();
      if (visibility != null && visibility != JSDocInfo.Visibility.INHERITED) {
        newInfo.recordVisibility(visibility);
      }

      if (ctorJSDocInfo.isDeprecated()) {
        newInfo.recordDeprecated();
      }

      if (ctorJSDocInfo.getDeprecationReason() != null
          && !newInfo.isDeprecationReasonRecorded()) {
        newInfo.recordDeprecationReason(ctorJSDocInfo.getDeprecationReason());
      }

      newInfo.mergePropertyBitfieldFrom(ctorJSDocInfo);

      for (String templateType : ctorJSDocInfo.getTemplateTypeNames()) {
        newInfo.recordTemplateTypeName(templateType);
      }
    }

    if (NodeUtil.isStatement(classNode)) {
      constructor.getFirstChild().setString("");
      Node ctorVar = IR.let(metadata.classNameNode.cloneNode(), constructor);
      ctorVar.useSourceInfoIfMissingFromForTree(classNode);
      parent.replaceChild(classNode, ctorVar);
    } else {
      parent.replaceChild(classNode, constructor);
    }

    if (NodeUtil.isStatement(constructor)) {
      constructor.setJSDocInfo(newInfo.build());
    } else if (parent.isName()) {
      // The constructor function is the RHS of a var statement.
      // Add the JSDoc to the VAR node.
      Node var = parent.getParent();
      var.setJSDocInfo(newInfo.build());
    } else if (constructor.getParent().isName()) {
      // Is a newly created VAR node.
      Node var = constructor.getGrandparent();
      var.setJSDocInfo(newInfo.build());
    } else if (parent.isAssign()) {
      // The constructor function is the RHS of an assignment.
      // Add the JSDoc to the ASSIGN node.
      parent.setJSDocInfo(newInfo.build());
    } else {
      throw new IllegalStateException("Unexpected parent node " + parent);
    }

    compiler.reportCodeChange();
  }

  /**
   * @param node A getter or setter node.
   */
  private JSTypeExpression getTypeFromGetterOrSetter(Node node) {
    JSDocInfo info = node.getJSDocInfo();
    if (info != null) {
      boolean getter = node.isGetterDef() || node.getBooleanProp(Node.COMPUTED_PROP_GETTER);
      if (getter && info.getReturnType() != null) {
        return info.getReturnType();
      } else {
        Set<String> paramNames = info.getParameterNames();
        if (paramNames.size() == 1) {
          return info.getParameterType(Iterables.getOnlyElement(info.getParameterNames()));
        }
      }
    }

    return new JSTypeExpression(new Node(Token.QMARK), node.getSourceFileName());
  }

  /**
   * @param member A getter or setter, or a computed property that is a getter/setter.
   */
  private void addToDefinePropertiesObject(ClassDeclarationMetadata metadata, Node member) {
    Node obj =
        member.isStaticMember()
            ? metadata.definePropertiesObjForClass
            : metadata.definePropertiesObjForPrototype;
    Node prop =
        member.isComputedProp()
            ? NodeUtil.getFirstComputedPropMatchingKey(obj, member.getFirstChild())
            : NodeUtil.getFirstPropMatchingKey(obj, member.getString());
    if (prop == null) {
      prop =
          IR.objectlit(
              IR.stringKey("configurable", IR.trueNode()),
              IR.stringKey("enumerable", IR.trueNode()));
      if (member.isComputedProp()) {
        obj.addChildToBack(IR.computedProp(member.getFirstChild().cloneTree(), prop));
      } else {
        obj.addChildToBack(IR.stringKey(member.getString(), prop));
      }
    }

    Node function = member.getLastChild();
    JSDocInfoBuilder info = JSDocInfoBuilder.maybeCopyFrom(
        NodeUtil.getBestJSDocInfo(function));

    info.recordThisType(new JSTypeExpression(new Node(
        Token.BANG, IR.string(metadata.fullClassName)), member.getSourceFileName()));
    Node stringKey =
        IR.stringKey(
            (member.isGetterDef() || member.getBooleanProp(Node.COMPUTED_PROP_GETTER))
                ? "get"
                : "set",
            function.detachFromParent());
    stringKey.setJSDocInfo(info.build());
    prop.addChildToBack(stringKey);
    prop.useSourceInfoIfMissingFromForTree(member);
  }

  private void visitComputedPropInClass(Node member, ClassDeclarationMetadata metadata) {
    if (member.isComputedProp() && member.isStaticMember()) {
      cannotConvertYet(member, "Static computed property");
      return;
    }
    if (member.isComputedProp() && !member.getFirstChild().isQualifiedName()) {
      cannotConvert(member.getFirstChild(), "Computed property with non-qualified-name key");
      return;
    }

    JSTypeExpression typeExpr = getTypeFromGetterOrSetter(member).clone();
    addToDefinePropertiesObject(metadata, member);

    Map<String, JSDocInfo> membersToDeclare;
    String memberName;
    if (member.isComputedProp()) {
      Preconditions.checkState(!member.isStaticMember());
      membersToDeclare = metadata.prototypeComputedPropsToDeclare;
      memberName = member.getFirstChild().getQualifiedName();
    } else {
      membersToDeclare = member.isStaticMember()
          ? metadata.classMembersToDeclare
          : metadata.prototypeMembersToDeclare;
      memberName = member.getString();
    }
    JSDocInfo existingJSDoc = membersToDeclare.get(memberName);
    JSTypeExpression existingType = existingJSDoc == null ? null : existingJSDoc.getType();
    if (existingType != null && !existingType.equals(typeExpr)) {
      compiler.report(JSError.make(member, CONFLICTING_GETTER_SETTER_TYPE, memberName));
    } else {
      JSDocInfoBuilder jsDoc = new JSDocInfoBuilder(false);
      jsDoc.recordType(typeExpr);
      if (member.getJSDocInfo() != null && member.getJSDocInfo().isExport()) {
        jsDoc.recordExport();
      }
      if (member.isStaticMember() && !member.isComputedProp()) {
        jsDoc.recordNoCollapse();
      }
      membersToDeclare.put(memberName, jsDoc.build());
    }
  }

  /**
   * Handles transpilation of a standard class member function. Getters, setters, and the
   * constructor are not handled here.
   */
  private void visitClassMember(
      Node member, ClassDeclarationMetadata metadata) {
    Node qualifiedMemberAccess = getQualifiedMemberAccess(
        member,
        NodeUtil.newQName(compiler, metadata.fullClassName),
        NodeUtil.newQName(compiler, metadata.fullClassName + ".prototype"));
    Node method = member.getLastChild().detachFromParent();

    Node assign = IR.assign(qualifiedMemberAccess, method);
    assign.useSourceInfoIfMissingFromForTree(member);

    JSDocInfo info = member.getJSDocInfo();
    if (member.isStaticMember() && NodeUtil.referencesThis(assign.getLastChild())) {
      JSDocInfoBuilder memberDoc = JSDocInfoBuilder.maybeCopyFrom(info);
      memberDoc.recordThisType(
          new JSTypeExpression(new Node(Token.BANG, new Node(Token.QMARK)),
          member.getSourceFileName()));
      info = memberDoc.build();
    }
    if (info != null) {
      assign.setJSDocInfo(info);
    }

    Node newNode = NodeUtil.newExpr(assign);
    metadata.insertNodeAndAdvance(newNode);
  }

  /**
   * Add declarations for properties that were defined with a getter and/or setter,
   * so that the typechecker knows those properties exist on the class.
   * This is a temporary solution. Eventually, the type checker should understand
   * Object.defineProperties calls directly.
   */
  private void addTypeDeclarations(ClassDeclarationMetadata metadata, Node insertionPoint) {
    for (Map.Entry<String, JSDocInfo> entry : metadata.prototypeMembersToDeclare.entrySet()) {
      String declaredMember = entry.getKey();
      Node declaration = IR.getprop(
          NodeUtil.newQName(compiler, metadata.fullClassName + ".prototype"),
          IR.string(declaredMember));
      declaration.setJSDocInfo(entry.getValue());
      declaration =
          IR.exprResult(declaration).useSourceInfoIfMissingFromForTree(metadata.classNameNode);
      insertionPoint.getParent().addChildAfter(declaration, insertionPoint);
      insertionPoint = declaration;
    }
    for (Map.Entry<String, JSDocInfo> entry : metadata.classMembersToDeclare.entrySet()) {
      String declaredMember = entry.getKey();
      Node declaration = IR.getprop(
          NodeUtil.newQName(compiler, metadata.fullClassName),
          IR.string(declaredMember));
      declaration.setJSDocInfo(entry.getValue());
      declaration =
          IR.exprResult(declaration).useSourceInfoIfMissingFromForTree(metadata.classNameNode);
      insertionPoint.getParent().addChildAfter(declaration, insertionPoint);
      insertionPoint = declaration;
    }
    for (Map.Entry<String, JSDocInfo> entry : metadata.prototypeComputedPropsToDeclare.entrySet()) {
      String declaredMember = entry.getKey();
      Node declaration = IR.getelem(
          NodeUtil.newQName(compiler, metadata.fullClassName + ".prototype"),
          NodeUtil.newQName(compiler, declaredMember));
      declaration.setJSDocInfo(entry.getValue());
      declaration =
          IR.exprResult(declaration).useSourceInfoIfMissingFromForTree(metadata.classNameNode);
      insertionPoint.getParent().addChildAfter(declaration, insertionPoint);
      insertionPoint = declaration;
    }
  }

  /**
   * Constructs a Node that represents an access to the given class member, qualified by either the
   * static or the instance access context, depending on whether the member is static.
   *
   * <p><b>WARNING:</b> {@code member} may be modified/destroyed by this method, do not use it
   * afterwards.
   */
  private static Node getQualifiedMemberAccess(Node member,
      Node staticAccess, Node instanceAccess) {
    Node context = member.isStaticMember() ? staticAccess : instanceAccess;
    context = context.cloneTree();
    if (member.isComputedProp()) {
      return IR.getelem(context, member.removeFirstChild());
    } else {
      Node methodName = member.getFirstChild().getFirstChild();
      return IR.getprop(context, IR.string(member.getString()).useSourceInfoFrom(methodName));
    }
  }

  private class CheckClassAssignments extends NodeTraversal.AbstractPostOrderCallback {
    private Node className;

    public CheckClassAssignments(Node className) {
      this.className = className;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!n.isAssign() || n.getFirstChild() == className) {
        return;
      }
      if (className.matchesQualifiedName(n.getFirstChild())) {
        compiler.report(JSError.make(n, CLASS_REASSIGNMENT));
      }
    }

  }

  private void cannotConvert(Node n, String message) {
    compiler.report(JSError.make(n, CANNOT_CONVERT, message));
  }

  /**
   * Warns the user that the given ES6 feature cannot be converted to ES3
   * because the transpilation is not yet implemented. A call to this method
   * is essentially a "TODO(tbreisacher): Implement {@code feature}" comment.
   */
  private void cannotConvertYet(Node n, String feature) {
    compiler.report(JSError.make(n, CANNOT_CONVERT_YET, feature));
  }

  /**
   * Returns a call to {@code $jscomp.makeIterator} with {@code iterable} as its argument.
   */
  static Node makeIterator(AbstractCompiler compiler, Node iterable) {
    return callEs6RuntimeFunction(compiler, iterable, "makeIterator");
  }

  /**
   * Returns a call to $jscomp.arrayFromIterable with {@code iterable} as its argument.
   */
  private static Node arrayFromIterable(AbstractCompiler compiler, Node iterable) {
    return callEs6RuntimeFunction(compiler, iterable, "arrayFromIterable");
  }

  private static Node callEs6RuntimeFunction(
      AbstractCompiler compiler, Node iterable, String function) {
    compiler.ensureLibraryInjected("es6_runtime", false);
    return IR.call(
        NodeUtil.newQName(compiler, "$jscomp." + function),
        iterable);
  }

  /**
   * Represents static metadata on a class declaration expression - i.e. the qualified name that a
   * class declares (directly or by assignment), whether it's anonymous, and where transpiled code
   * should be inserted (i.e. which object will hold the prototype after transpilation).
   */
  static class ClassDeclarationMetadata {
    /** A statement node. Transpiled methods etc of the class are inserted after this node. */
    private Node insertionPoint;

    /**
     * An object literal node that will be used in a call to Object.defineProperties, to add getters
     * and setters to the prototype.
     */
    private final Node definePropertiesObjForPrototype;

    /**
     * An object literal node that will be used in a call to Object.defineProperties, to add getters
     * and setters to the class.
     */
    private final Node definePropertiesObjForClass;

    // Normal declarations to be added to the prototype: Foo.prototype.bar
    private final Map<String, JSDocInfo> prototypeMembersToDeclare;

    // Computed property declarations to be added to the prototype: Foo.prototype[bar]
    private final Map<String, JSDocInfo> prototypeComputedPropsToDeclare;

    // Normal declarations to be added to the class: Foo.bar
    private final Map<String, JSDocInfo> classMembersToDeclare;

    /**
     * The fully qualified name of the class, which will be used in the output. May come from the
     * class itself or the LHS of an assignment.
     */
    final String fullClassName;
    /** Whether the constructor function in the output should be anonymous. */
    final boolean anonymous;
    final Node classNameNode;
    final Node superClassNameNode;

    private ClassDeclarationMetadata(Node insertionPoint, String fullClassName,
        boolean anonymous, Node classNameNode, Node superClassNameNode) {
      this.insertionPoint = insertionPoint;
      this.definePropertiesObjForClass = IR.objectlit();
      this.definePropertiesObjForPrototype = IR.objectlit();
      this.prototypeMembersToDeclare = new LinkedHashMap<>();
      this.prototypeComputedPropsToDeclare = new LinkedHashMap<>();
      this.classMembersToDeclare = new LinkedHashMap<>();
      this.fullClassName = fullClassName;
      this.anonymous = anonymous;
      this.classNameNode = classNameNode;
      this.superClassNameNode = superClassNameNode;
    }

    static ClassDeclarationMetadata create(Node classNode, Node parent) {
      Node classNameNode = classNode.getFirstChild();
      Node superClassNameNode = classNameNode.getNext();

      // If this is a class statement, or a class expression in a simple
      // assignment or var statement, convert it. In any other case, the
      // code is too dynamic, so return null.
      if (NodeUtil.isClassDeclaration(classNode)) {
        return new ClassDeclarationMetadata(classNode, classNameNode.getString(), false,
            classNameNode, superClassNameNode);
      } else if (parent.isAssign() && parent.getParent().isExprResult()) {
        // Add members after the EXPR_RESULT node:
        // example.C = class {}; example.C.prototype.foo = function() {};
        String fullClassName = parent.getFirstChild().getQualifiedName();
        if (fullClassName == null) {
          return null;
        }
        return new ClassDeclarationMetadata(parent.getParent(), fullClassName, true, classNameNode,
            superClassNameNode);
      } else if (parent.isName()) {
        // Add members after the 'var' statement.
        // var C = class {}; C.prototype.foo = function() {};
        return new ClassDeclarationMetadata(parent.getParent(), parent.getString(), true,
            classNameNode, superClassNameNode);
      } else {
        // Cannot handle this class declaration.
        return null;
      }
    }

    void insertNodeAndAdvance(Node newNode) {
      insertionPoint.getParent().addChildAfter(newNode, insertionPoint);
      insertionPoint = newNode;
    }

    boolean hasSuperClass() {
      return !superClassNameNode.isEmpty();
    }
  }
}
