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
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.List;

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
public final class LateEs6ToEs3Converter implements NodeTraversal.Callback, HotSwapCompilerPass {
  private final AbstractCompiler compiler;

  private static final String FRESH_COMP_PROP_VAR = "$jscomp$compprop";

  private static final String ITER_BASE = "$jscomp$iter$";

  private static final String ITER_RESULT = "$jscomp$key$";

  public LateEs6ToEs3Converter(AbstractCompiler compiler) {
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

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case GETTER_DEF:
      case SETTER_DEF:
        if (compiler.getOptions().getLanguageOut() == LanguageMode.ECMASCRIPT3) {
          Es6ToEs3Util.cannotConvert(
              compiler, n, "ES5 getters/setters (consider using --language_out=ES5)");
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
   * Converts a member definition in an object literal to an ES3 key/value pair.
   * Member definitions in classes are handled in {@link Es6RewriteClass}.
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

    Node init = IR.var(iterName.cloneTree(), Es6ToEs3Util.makeIterator(compiler, iterable));
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
        Es6ToEs3Util.cannotConvertYet(
            compiler, currElement, "computed getter/setter in an object literal");
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
}
