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

import com.google.common.collect.Lists;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
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
  private static final FeatureSet transpiledFeatures =
      FeatureSet.BARE_MINIMUM.with(
          Feature.COMPUTED_PROPERTIES,
          Feature.MEMBER_DECLARATIONS,
          Feature.TEMPLATE_LITERALS);
  // addTypes indicates whether we should add type information when transpiling.
  private final boolean addTypes;
  private final JSTypeRegistry registry;
  private final JSType unknownType;
  private final JSType stringType;

  private static final String FRESH_COMP_PROP_VAR = "$jscomp$compprop";

  public LateEs6ToEs3Converter(AbstractCompiler compiler) {
    this.compiler = compiler;
    // Only add type information if NTI has been run.
    this.addTypes = compiler.hasTypeCheckingRun();
    this.registry = compiler.getTypeRegistry();
    this.unknownType = createType(addTypes, registry, JSTypeNative.UNKNOWN_TYPE);
    this.stringType = createType(addTypes, registry, JSTypeNative.STRING_TYPE);
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, externs, transpiledFeatures, this);
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(compiler, scriptRoot, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case GETTER_DEF:
      case SETTER_DEF:
        if (FeatureSet.ES3.contains(compiler.getOptions().getOutputFeatureSet())) {
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
    Node stringKey = withType(IR.stringKey(name, n.getFirstChild().detach()), n.getJSType());
    stringKey.setJSDocInfo(n.getJSDocInfo());
    parent.replaceChild(n, stringKey);
    stringKey.useSourceInfoFrom(nameNode);
    compiler.reportChangeToEnclosingScope(stringKey);
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
    JSType objectType = obj.getJSType();

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
        JSType valueType = value.getJSType();
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
        JSType valueType = val.getJSType();
        propdef.setToken(Token.STRING);
        propdef.setJSType(stringType);
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

    JSType simpleObjectType = createType(
        addTypes, registry, JSTypeNative.EMPTY_OBJECT_LITERAL_TYPE);
    Node var = IR.var(withType(IR.name(objName), objectType), withType(obj, simpleObjectType));
    var.useSourceInfoIfMissingFromForTree(statement);
    statement.getParent().addChildBefore(var, statement);
    compiler.reportChangeToEnclosingScope(var);
  }

  private Node withUnknownType(Node n) {
    return withType(n, unknownType);
  }
}
