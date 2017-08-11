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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
// TODO(tbreisacher): This class does too many things. Break it into smaller passes.
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

  public Es6ToEs3Converter(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, externs, this);
    TranspilationPasses.processTranspile(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(compiler, scriptRoot, this);
  }

  /**
   * Some nodes must be visited pre-order in order to rewrite the
   * references to {@code this} correctly.
   * Everything else is translated post-order in {@link #visit}.
   */
  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case REST:
        visitRestParam(t, n, parent);
        break;
      case GETTER_DEF:
      case SETTER_DEF:
        if (compiler.getOptions().getLanguageOut() == LanguageMode.ECMASCRIPT3) {
          cannotConvert(n, "ES5 getters/setters (consider using --language_out=ES5)");
          return false;
        }
        break;
      case FUNCTION:
        if (n.isAsyncFunction()) {
          throw new IllegalStateException("async functions should have already been converted");
        }
        break;
      default:
        break;
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case NAME:
        if (!n.isFromExterns() && isGlobalSymbol(t, n)) {
          initSymbolBefore(n);
        }
        break;
      case GETPROP:
        if (!n.isFromExterns()) {
          visitGetprop(t, n);
        }
        break;
      case OBJECTLIT:
        visitObject(n);
        break;
      case MEMBER_FUNCTION_DEF:
        if (parent.isObjectLit()) {
          visitMemberFunctionDefInObjectLit(n, parent);
        }
        break;
      case FOR_OF:
        visitForOf(n, parent);
        break;
      case STRING_KEY:
        visitStringKey(n);
        break;
      case ARRAYLIT:
      case NEW:
      case CALL:
        for (Node child : n.children()) {
          if (child.isSpread()) {
            visitArrayLitOrCallWithSpread(n, parent);
            break;
          }
        }
        break;
      case TAGGED_TEMPLATELIT:
        Es6TemplateLiterals.visitTaggedTemplateLiteral(t, n);
        break;
      case TEMPLATELIT:
        if (!parent.isTaggedTemplateLit()) {
          Es6TemplateLiterals.visitTemplateLiteral(t, n);
        }
        break;
      default:
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
    compiler.ensureLibraryInjected("es6/symbol", false);
    Node statement = NodeUtil.getEnclosingStatement(n);
    Node initSymbol = IR.exprResult(IR.call(NodeUtil.newQName(compiler, "$jscomp.initSymbol")));
    statement.getParent().addChildBefore(initSymbol.useSourceInfoFromForTree(statement), statement);
    compiler.reportChangeToEnclosingScope(initSymbol);
  }

  // TODO(tbreisacher): Do this for all well-known symbols.
  private void visitGetprop(NodeTraversal t, Node n) {
    if (!n.matchesQualifiedName("Symbol.iterator")) {
      return;
    }
    if (isGlobalSymbol(t, n.getFirstChild())) {
      compiler.ensureLibraryInjected("es6/symbol", false);
      Node statement = NodeUtil.getEnclosingStatement(n);
      Node init = IR.exprResult(IR.call(NodeUtil.newQName(compiler, "$jscomp.initSymbolIterator")));
      statement.getParent().addChildBefore(init.useSourceInfoFromForTree(statement), statement);
      compiler.reportChangeToEnclosingScope(init);
    }
  }

  /**
   * Converts a member definition in an object literal to an ES3 key/value pair.
   * Member definitions in classes are handled in {@link #Es6RewriteClass}.
   */
  private void visitMemberFunctionDefInObjectLit(Node n, Node parent) {
    String name = n.getString();
    Node nameNode = n.getFirstFirstChild();
    Node stringKey = IR.stringKey(name, n.getFirstChild().detach());
    stringKey.setJSDocInfo(n.getJSDocInfo());
    parent.replaceChild(n, stringKey);
    stringKey.useSourceInfoFrom(nameNode);
    compiler.reportChangeToEnclosingScope(stringKey);
  }

  /**
   * Converts extended object literal {a} to {a:a}.
   */
  // TODO(blickly): Separate this so it can be part of the normalization early transpilation passes.
  private void visitStringKey(Node n) {
    if (!n.hasChildren()) {
      Node name = IR.name(n.getString());
      name.useSourceInfoIfMissingFrom(n);
      n.addChildToBack(name);
      compiler.reportChangeToEnclosingScope(name);
    }
  }

  private void visitForOf(Node node, Node parent) {
    Node variable = node.removeFirstChild();
    Node iterable = node.removeFirstChild();
    Node body = node.removeFirstChild();
    JSDocInfo varJSDocInfo = variable.getJSDocInfo();

    Node iterName = IR.name(ITER_BASE + compiler.getUniqueNameIdSupplier().get());
    iterName.makeNonIndexable();
    Node getNext = IR.call(IR.getprop(iterName.cloneTree(), IR.string("next")));
    String variableName;
    Token declType;
    if (variable.isName()) {
      declType = Token.NAME;
      variableName = variable.getQualifiedName();
    } else {
      Preconditions.checkState(NodeUtil.isNameDeclaration(variable),
          "Expected var, let, or const. Got %s", variable);
      declType = variable.getToken();
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
      declarationOrAssign = IR.assign(
          IR.name(variableName).useSourceInfoFrom(variable),
          IR.getprop(iterResult.cloneTree(), IR.string("value")));
      declarationOrAssign.setJSDocInfo(varJSDocInfo);
      declarationOrAssign = IR.exprResult(declarationOrAssign);
    } else {
      declarationOrAssign = new Node(
          declType,
          IR.name(variableName).useSourceInfoFrom(variable.getFirstChild()));
      declarationOrAssign.getFirstChild().addChildToBack(
          IR.getprop(iterResult.cloneTree(), IR.string("value")));
      declarationOrAssign.setJSDocInfo(varJSDocInfo);
    }
    Node newBody = IR.block(declarationOrAssign, body).useSourceInfoFrom(body);
    Node newFor = IR.forNode(init, cond, incr, newBody);
    newFor.useSourceInfoIfMissingFromForTree(node);
    parent.replaceChild(node, newFor);
    compiler.reportChangeToEnclosingScope(newFor);
  }

  /**
   * Processes a rest parameter
   */
  private void visitRestParam(NodeTraversal t, Node restParam, Node paramList) {
    Node functionBody = paramList.getNext();
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
    if (type != null && type.getRoot().getToken() != Token.ELLIPSIS) {
      compiler.report(JSError.make(restParam, BAD_REST_PARAMETER_ANNOTATION));
    }

    if (!functionBody.hasChildren()) {
      // If function has no body, we are done!
      t.reportCodeChange();
      return;
    }

    Node newBlock = IR.block().useSourceInfoFrom(functionBody);
    Node name = IR.name(paramName);
    Node let = IR.let(name, IR.name(REST_PARAMS))
        .useSourceInfoIfMissingFromForTree(functionBody);
    newBlock.addChildToFront(let);

    for (Node child : functionBody.children()) {
      newBlock.addChildToBack(child.detach());
    }

    if (type != null) {
      Node arrayType = IR.string("Array");
      Node typeNode = type.getRoot();
      Node memberType =
          typeNode.getToken() == Token.ELLIPSIS
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
    compiler.reportChangeToEnclosingScope(newBlock);

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
    checkArgument(node.isCall() || node.isArrayLit() || node.isNew());
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
    compiler.reportChangeToEnclosingScope(result);
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
    checkArgument(obj.isObjectLit());
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
        propdef.setToken(Token.STRING);
        Token type = propdef.isQuotedString() ? Token.GETELEM : Token.GETPROP;
        Node access = new Node(type, IR.name(objName), propdef);
        result = IR.comma(IR.assign(access, val), result);
      }
    }

    Node statement = obj;
    while (!NodeUtil.isStatement(statement)) {
      statement = statement.getParent();
    }

    result.useSourceInfoIfMissingFromForTree(obj);
    obj.replaceWith(result);

    Node var = IR.var(IR.name(objName), obj);
    var.useSourceInfoIfMissingFromForTree(statement);
    statement.getParent().addChildBefore(var, statement);
    compiler.reportChangeToEnclosingScope(var);
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
    compiler.ensureLibraryInjected("es6/util/" + function.toLowerCase(Locale.US), false);
    return IR.call(
        NodeUtil.newQName(compiler, "$jscomp." + function),
        iterable);
  }
}
