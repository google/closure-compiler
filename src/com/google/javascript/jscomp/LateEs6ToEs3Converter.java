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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.AstFactory.type;

import com.google.common.collect.Lists;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Converts ES6 code to valid ES5 code. This class does most of the transpilation, and
 * https://github.com/google/closure-compiler/wiki/ECMAScript6 lists which ES6 features are
 * supported. Other classes that start with "Es6" do other parts of the transpilation.
 *
 * <p>In most cases, the output is valid as ES3 (hence the class name) but in some cases, if the
 * output language is set to ES5, we rely on ES5 features such as getters, setters, and
 * Object.defineProperties.
 */
// TODO(tbreisacher): This class does too many things. Break it into smaller passes.
public final class LateEs6ToEs3Converter implements NodeTraversal.Callback, CompilerPass {
  private final AbstractCompiler compiler;
  private final AstFactory astFactory;
  private final Es6TemplateLiterals templateLiteralConverter;
  private static final FeatureSet transpiledFeatures =
      FeatureSet.BARE_MINIMUM.with(
          Feature.COMPUTED_PROPERTIES,
          Feature.MEMBER_DECLARATIONS,
          Feature.TEMPLATE_LITERALS);

  // We want to insert the call to `var tagFnFirstArg = $jscomp.createTemplateTagFirstArg...` just
  // before this node. For the first script, this node is right after the runtime injected function
  // definition as injecting to the top of the script causes runtime errors
  // https://github.com/google/closure-compiler/issues/3589. For the subsequent script(s), the call
  // is injected to the top of that script.
  private @Nullable Node templateLitInsertionPoint = null;

  private static final String FRESH_COMP_PROP_VAR = "$jscomp$compprop";

  public LateEs6ToEs3Converter(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
    this.templateLiteralConverter = new Es6TemplateLiterals(compiler);
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, root, transpiledFeatures);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    if (n.isScript()) {
      templateLitInsertionPoint = findTemplateLitInsertionPoint(n);
    }
    return true;
  }

  private static Node findTemplateLitInsertionPoint(Node script) {
    if (script.getIsInClosureUnawareSubtree()) {
      // For all closure-unaware scripts, initialize the injection point within the closure
      // aware function.
      Node closureUnawareBlock = NodeUtil.findClosureUnawareScriptRoot(script);
      return NodeUtil.getInsertionPointAfterAllInnerFunctionDeclarations(closureUnawareBlock);
    }
    // For all other scripts, initialize the injection point to be top of the script. The spec
    // requires a single unique array per template literal, so for a template literal in a function
    // e.g. it would be incorrect to initialize a new array per every function call.
    return script.getFirstChild();
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case ASSIGN -> {
        // Find whether this script contains the `$jscomp.createTemplateTagFirstArgWithRaw =
        // function(..) {..}` node. If yes, update the templateLitInsertionPoint.
        Node lhs = n.getFirstChild();
        Node rhs = n.getSecondChild();
        if (lhs.isGetProp() && rhs.isFunction() && lhs.getFirstChild().isName()) {
          QualifiedName qName = QualifiedName.of("$jscomp.createTemplateTagFirstArgWithRaw");
          if (qName.matches(lhs)) {
            checkNotNull(n.getParent(), n);
            checkState(n.getParent().isExprResult(), n);
            templateLitInsertionPoint = n.getParent().getNext();
          }
        }
      }
      case OBJECTLIT -> visitObject(n);
      case MEMBER_FUNCTION_DEF -> {
        if (parent.isObjectLit()) {
          visitMemberFunctionDefInObjectLit(n);
        }
      }
      case TAGGED_TEMPLATELIT ->
          templateLiteralConverter.visitTaggedTemplateLiteral(t, n, templateLitInsertionPoint);
      case TEMPLATELIT -> {
        if (!parent.isTaggedTemplateLit()) {
          templateLiteralConverter.visitTemplateLiteral(t, n);
        }
      }
      default -> {}
    }
  }

  /**
   * Converts a member definition in an object literal to an ES3 key/value pair. Member definitions
   * in classes are handled in {@link Es6RewriteClass}.
   */
  private void visitMemberFunctionDefInObjectLit(Node n) {
    String name = n.getString();
    Node nameNode = n.getFirstFirstChild();
    Node stringKey = astFactory.createStringKey(name, n.removeFirstChild());
    stringKey.setJSDocInfo(n.getJSDocInfo());
    n.replaceWith(stringKey);
    stringKey.srcref(nameNode);
    compiler.reportChangeToEnclosingScope(stringKey);
  }

  private void visitObject(Node obj) {
    for (Node child = obj.getFirstChild(); child != null; child = child.getNext()) {
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
    AstFactory.Type objectType = type(obj);

    while (currElement != null) {
      if (currElement.getBooleanProp(Node.COMPUTED_PROP_GETTER)
          || currElement.getBooleanProp(Node.COMPUTED_PROP_SETTER)) {
        compiler.report(
            JSError.make(
                currElement,
                ReportUntranspilableFeatures.UNTRANSPILABLE_FEATURE_PRESENT,
                "computed getter/setter in an object literal",
                "ES2015",
                ""));
        return;
      } else if (currElement.isGetterDef() || currElement.isSetterDef()) {
        currElement = currElement.getNext();
      } else {
        Node nextNode = currElement.getNext();
        currElement.detach();
        props.add(currElement);
        currElement = nextNode;
      }
    }

    String objName = FRESH_COMP_PROP_VAR + compiler.getUniqueNameIdSupplier().get();

    props = Lists.reverse(props);
    Node result = astFactory.createName(objName, objectType);
    for (Node propdef : props) {
      if (propdef.isComputedProp()) {
        Node propertyExpression = propdef.removeFirstChild();
        Node value = propdef.removeFirstChild();
        result =
            astFactory.createComma(
                astFactory.createAssign(
                    astFactory.createGetElem(
                        astFactory.createName(objName, objectType), propertyExpression),
                    value),
                result);
      } else {
        Node val = propdef.removeFirstChild();
        boolean isQuotedAccess = propdef.isQuotedStringKey();

        propdef.setToken(Token.STRINGLIT);
        propdef.setColor(StandardColors.STRING);
        propdef.putBooleanProp(Node.QUOTED_PROP, false);

        Node objNameNode = astFactory.createName(objName, objectType);
        Node access =
            isQuotedAccess
                ? astFactory.createGetElem(objNameNode, propdef)
                : astFactory.createGetProp(objNameNode, propdef.getString(), type(propdef));
        result = astFactory.createComma(astFactory.createAssign(access, val), result);
      }
    }

    Node statement = obj;
    while (!NodeUtil.isStatement(statement)) {
      statement = statement.getParent();
    }

    result.srcrefTreeIfMissing(obj);
    obj.replaceWith(result);

    Node var = IR.var(astFactory.createName(objName, objectType), obj);
    var.srcrefTreeIfMissing(statement);
    var.insertBefore(statement);
    compiler.reportChangeToEnclosingScope(var);
  }
}
