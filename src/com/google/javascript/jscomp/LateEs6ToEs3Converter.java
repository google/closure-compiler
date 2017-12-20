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
import static com.google.javascript.jscomp.Es6ToEs3Util.createType;
import static com.google.javascript.jscomp.Es6ToEs3Util.withType;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.AbstractCompiler.MostRecentTypechecker;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.ObjectTypeI;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TypeI;
import com.google.javascript.rhino.TypeIRegistry;
import com.google.javascript.rhino.jstype.JSTypeNative;
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
  private static final FeatureSet transpiledFeatures = FeatureSet.ES6.without(FeatureSet.ES5);
  // addTypes indicates whether we should add type information when transpiling.
  private final boolean addTypes;
  private final TypeIRegistry registry;
  private final TypeI unknownType;
  private final TypeI stringType;
  private final TypeI booleanType;

  private static final String FRESH_COMP_PROP_VAR = "$jscomp$compprop";

  private static final String ITER_BASE = "$jscomp$iter$";

  private static final String ITER_RESULT = "$jscomp$key$";

  public LateEs6ToEs3Converter(AbstractCompiler compiler) {
    this.compiler = compiler;
    // Only add type information if NTI has been run.
    this.addTypes = MostRecentTypechecker.NTI.equals(compiler.getMostRecentTypechecker());
    this.registry = compiler.getTypeIRegistry();
    this.unknownType = createType(addTypes, registry, JSTypeNative.UNKNOWN_TYPE);
    this.stringType = createType(addTypes, registry, JSTypeNative.STRING_TYPE);
    this.booleanType = createType(addTypes, registry, JSTypeNative.BOOLEAN_TYPE);
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, externs, transpiledFeatures, this);
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(compiler, scriptRoot, transpiledFeatures, this);
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
        visitForOf(t, n, parent);
        break;
      case TAGGED_TEMPLATELIT:
        Es6TemplateLiterals.visitTaggedTemplateLiteral(t, n, addTypes);
        break;
      case TEMPLATELIT:
        if (!parent.isTaggedTemplateLit()) {
          Es6TemplateLiterals.visitTemplateLiteral(t, n, addTypes);
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
    Node stringKey = withType(IR.stringKey(name, n.getFirstChild().detach()), n.getTypeI());
    stringKey.setJSDocInfo(n.getJSDocInfo());
    parent.replaceChild(n, stringKey);
    stringKey.useSourceInfoFrom(nameNode);
    compiler.reportChangeToEnclosingScope(stringKey);
  }

  private void visitForOf(NodeTraversal t, Node node, Node parent) {
    Node variable = node.removeFirstChild();
    Node iterable = node.removeFirstChild();
    Node body = node.removeFirstChild();

    TypeI typeParam = unknownType;
    if (addTypes) {
      // TODO(sdh): This is going to be null if the iterable is nullable or unknown. We might want
      // to consider some way of unifying rather than simply looking at the nominal type.
      ObjectTypeI iterableType = iterable.getTypeI().autobox().toMaybeObjectType();
      if (iterableType != null) {
        TypeIRegistry registry = compiler.getTypeIRegistry();
        TypeI iterableBaseType = registry.getNativeType(JSTypeNative.ITERABLE_TYPE);
        typeParam = iterableType.getInstantiatedTypeArgument(iterableBaseType);
      }
    }
    TypeI iteratorType = createGenericType(JSTypeNative.ITERATOR_TYPE, typeParam);
    TypeI iIterableResultType = createGenericType(JSTypeNative.I_ITERABLE_RESULT_TYPE, typeParam);
    TypeI iteratorNextType =
        addTypes ? iteratorType.toMaybeObjectType().getPropertyType("next") : null;

    JSDocInfo varJSDocInfo = variable.getJSDocInfo();
    Node iterName =
        withType(IR.name(ITER_BASE + compiler.getUniqueNameIdSupplier().get()), iteratorType);
    iterName.makeNonIndexable();
    Node getNext =
        withType(
            IR.call(
                withType(
                    IR.getprop(iterName.cloneTree(), withStringType(IR.string("next"))),
                    iteratorNextType)),
            iIterableResultType);
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
    Node iterResult = withType(IR.name(ITER_RESULT + variableName), iIterableResultType);
    iterResult.makeNonIndexable();

    Node call = Es6ToEs3Util.makeIterator(compiler, iterable);
    if (addTypes) {
      TypeI jscompType = t.getScope().getVar("$jscomp").getNode().getTypeI();
      TypeI makeIteratorType = jscompType.toMaybeObjectType().getPropertyType("makeIterator");
      call.getFirstChild().setTypeI(makeIteratorType);
      call.getFirstFirstChild().setTypeI(jscompType);
    }
    Node init = IR.var(iterName.cloneTree(), withType(call, iteratorType));
    Node initIterResult = iterResult.cloneTree();
    initIterResult.addChildToFront(getNext.cloneTree());
    init.addChildToBack(initIterResult);

    Node cond =
        withBooleanType(
            IR.not(
                withBooleanType(
                    IR.getprop(iterResult.cloneTree(), withStringType(IR.string("done"))))));
    Node incr =
        withType(IR.assign(iterResult.cloneTree(), getNext.cloneTree()), iIterableResultType);

    Node declarationOrAssign;
    if (declType == Token.NAME) {
      declarationOrAssign =
          withType(
              IR.assign(
                  withType(IR.name(variableName).useSourceInfoFrom(variable), typeParam),
                  withType(
                      IR.getprop(iterResult.cloneTree(), withStringType(IR.string("value"))),
                      typeParam)),
              typeParam);
      declarationOrAssign.setJSDocInfo(varJSDocInfo);
      declarationOrAssign = IR.exprResult(declarationOrAssign);
    } else {
      declarationOrAssign = new Node(
          declType,
          withType(IR.name(variableName).useSourceInfoFrom(variable.getFirstChild()), typeParam));
      declarationOrAssign.getFirstChild().addChildToBack(
              withType(
                  IR.getprop(iterResult.cloneTree(), withStringType(IR.string("value"))),
                  typeParam));
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

  /**
   * Transpiles an object node with computed property,
   * and add type information to the new nodes if this pass ran after type checking.
   * For example,<pre>   {@code
   *   var obj = {a: 1, [i++]: 2}
   *   is transpiled to
   *   var $jscomp$compprop0 = {};
   *   var obj = ($jscomp$compprop0.a = 1, ($jscomp$compprop0[i++] = 2, $jscomp$compprop0));
   * }</pre>
   * Note that when adding type information to the nodes, the NAME node $jscomp$compprop0
   * would always be assigned the type of the entire object (in the above example {a: number}).
   * This is because we do not have sufficient type information during transpilation to know,
   * for example, $jscomp$compprop0 has type Object{} in the expression $jscomp$compprop0.a = 1
   */
  private void visitObjectWithComputedProperty(Node obj) {
    checkArgument(obj.isObjectLit());
    List<Node> props = new ArrayList<>();
    Node currElement = obj.getFirstChild();
    TypeI objectType = obj.getTypeI();

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
    Node result = withType(IR.name(objName), objectType);
    for (Node propdef : props) {
      if (propdef.isComputedProp()) {
        Node propertyExpression = propdef.removeFirstChild();
        Node value = propdef.removeFirstChild();
        TypeI valueType = value.getTypeI();
        result =
            withType(
                IR.comma(
                    withType(
                        IR.assign(
                            withUnknownType(
                                IR.getelem(
                                    withType(IR.name(objName), objectType), propertyExpression)),
                            value),
                        valueType),
                    result),
                objectType);
      } else {
        Node val = propdef.removeFirstChild();
        TypeI valueType = val.getTypeI();
        propdef.setToken(Token.STRING);
        propdef.setTypeI(null);
        Token token = propdef.isQuotedString() ? Token.GETELEM : Token.GETPROP;
        Node access =
            withType(new Node(token, withType(IR.name(objName), objectType), propdef), valueType);
        result =
            withType(IR.comma(withType(IR.assign(access, val), valueType), result), objectType);
      }
    }

    Node statement = obj;
    while (!NodeUtil.isStatement(statement)) {
      statement = statement.getParent();
    }

    result.useSourceInfoIfMissingFromForTree(obj);
    obj.replaceWith(result);

    TypeI simpleObjectType = createType(addTypes, registry, JSTypeNative.EMPTY_OBJECT_LITERAL_TYPE);
    Node var = IR.var(withType(IR.name(objName), objectType), withType(obj, simpleObjectType));
    var.useSourceInfoIfMissingFromForTree(statement);
    statement.getParent().addChildBefore(var, statement);
    compiler.reportChangeToEnclosingScope(var);
  }

  private TypeI createGenericType(JSTypeNative typeName, TypeI typeArg) {
    return Es6ToEs3Util.createGenericType(addTypes, registry, typeName, typeArg);
  }

  private Node withStringType(Node n) {
    return withType(n, stringType);
  }

  private Node withBooleanType(Node n) {
    return withType(n, booleanType);
  }

  private Node withUnknownType(Node n) {
    return withType(n, unknownType);
  }
}
