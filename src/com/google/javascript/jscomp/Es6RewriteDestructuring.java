/*
 * Copyright 2015 The Closure Compiler Authors.
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
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.AstFactory.type;
import static com.google.javascript.jscomp.DiagnosticType.error;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.jscomp.parsing.ParsingUtil;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticScope;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSTypeNative;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;

/**
 * Rewrites destructuring patterns and default parameters to valid ES3 code or to a different form
 * of destructuring.
 */
public final class Es6RewriteDestructuring implements NodeTraversal.Callback, CompilerPass {

  public static final DiagnosticType UNEXPECTED_DESTRUCTURING_REST_PARAMETER =
      error(
          "JSC_UNEXPECTED_DESTRUCTURING_REST_PARAMETER",
          "Es6RewriteDestructuring not expecting object pattern rest parameter");

  enum ObjectDestructuringRewriteMode {
    /**
     * Rewrite all object destructuring patterns. This is the default mode used if no
     * ObjectDestructuringRewriteMode is provided to the Builder.
     *
     * <p>Used to transpile ES2018 -> ES5
     */
    REWRITE_ALL_OBJECT_PATTERNS,

    /**
     * Rewrite only destructuring patterns that contain object pattern rest properties (whether the
     * rest is on the top level or nested within a property).
     *
     * <p>Used to transpile ES2018 -> ES2017
     */
    REWRITE_OBJECT_REST,
  }

  private final AbstractCompiler compiler;
  private final ObjectDestructuringRewriteMode rewriteMode;
  private final AstFactory astFactory;
  private final StaticScope namespace;

  private final FeatureSet featuresToTriggerRunningPass;
  private final FeatureSet featuresToMarkAsRemoved;

  private final Deque<PatternNestingLevel> patternNestingStack = new ArrayDeque<>();

  static final String DESTRUCTURING_TEMP_VAR = "$jscomp$destructuring$var";

  private int destructuringVarCounter = 0;

  private Es6RewriteDestructuring(Builder builder) {
    this.compiler = builder.compiler;
    this.rewriteMode = builder.rewriteMode;
    this.astFactory = compiler.createAstFactory();
    this.namespace = compiler.getTranspilationNamespace();

    switch (this.rewriteMode) {
      case REWRITE_ALL_OBJECT_PATTERNS -> {
        this.featuresToTriggerRunningPass =
            FeatureSet.BARE_MINIMUM.with(
                Feature.DEFAULT_PARAMETERS,
                Feature.ARRAY_DESTRUCTURING,
                Feature.ARRAY_PATTERN_REST,
                Feature.OBJECT_DESTRUCTURING);

        // If OBJECT_PATTERN_REST were to be present in featuresToTriggerRunningPass and not the
        // input language featureSet (such as ES6=>ES5) the pass would be skipped.
        this.featuresToMarkAsRemoved =
            featuresToTriggerRunningPass.with(Feature.OBJECT_PATTERN_REST);
      }
      case REWRITE_OBJECT_REST -> {
        // TODO(bradfordcsmith): We shouldn't really need to remove default parameters for this
        // case.
        this.featuresToTriggerRunningPass =
            FeatureSet.BARE_MINIMUM.with(Feature.OBJECT_PATTERN_REST);
        this.featuresToMarkAsRemoved = this.featuresToTriggerRunningPass;
      }
      default ->
          throw new AssertionError(
              "Es6RewriteDestructuring cannot handle ObjectDestructuringRewriteMode "
                  + this.rewriteMode);
    }
  }

  static class Builder {

    private final AbstractCompiler compiler;
    private ObjectDestructuringRewriteMode rewriteMode =
        ObjectDestructuringRewriteMode.REWRITE_ALL_OBJECT_PATTERNS;

    public Builder(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @CanIgnoreReturnValue
    public Builder setDestructuringRewriteMode(ObjectDestructuringRewriteMode rewriteMode) {
      this.rewriteMode = rewriteMode;
      return this;
    }

    public Es6RewriteDestructuring build() {
      return new Es6RewriteDestructuring(this);
    }
  }

  private static final class PatternNestingLevel {

    final Node pattern;
    boolean hasNestedObjectRest;

    public PatternNestingLevel(Node pattern, boolean hasNestedRest) {
      this.pattern = pattern;
      this.hasNestedObjectRest = hasNestedRest;
    }
  }

  @Override
  public void process(Node externs, Node root) {
    checkState(patternNestingStack.isEmpty());
    NodeTraversal.traverse(compiler, root, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, root, featuresToMarkAsRemoved);
    checkState(patternNestingStack.isEmpty());
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    if (n.isScript()) {
      FeatureSet scriptFeatures = NodeUtil.getFeatureSetOfScript(n);
      // This will ensure that we run this only when features exist in the script
      return scriptFeatures.containsAtLeastOneOf(featuresToTriggerRunningPass);
    }
    switch (n.getToken()) {
      case PARAM_LIST -> pullDestructuringOutOfParams(n, parent);
      case ARRAY_PATTERN, OBJECT_PATTERN -> {
        boolean hasRest = n.isObjectPattern() && n.hasChildren() && n.getLastChild().isRest();
        if (!this.patternNestingStack.isEmpty() && hasRest) {
          for (PatternNestingLevel level : patternNestingStack) {
            if (level.hasNestedObjectRest) {
              break;
            }
            level.hasNestedObjectRest = true;
          }
          this.patternNestingStack.peekLast().hasNestedObjectRest = true;
        }
        this.patternNestingStack.addLast(new PatternNestingLevel(n, hasRest));
      }
      default -> {}
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case ARRAY_PATTERN, OBJECT_PATTERN -> {
        visitPattern(t, n);
        if (n == this.patternNestingStack.getLast().pattern) {
          this.patternNestingStack.removeLast();
        }
      }
      default -> {}
    }
  }

  /** Pulls all default and destructuring parameters out of function parameters. */
  // TODO(bradfordcsmith): Ideally if we're only removing OBJECT_REST, we should only do this when
  // the parameter list contains a usage of OBJECT_REST.
  private void pullDestructuringOutOfParams(Node paramList, Node function) {
    Node insertSpot = null;
    Node body = function.getLastChild();
    Node next = null;
    for (Node param = paramList.getFirstChild(); param != null; param = next) {
      next = param.getNext();
      if (param.isDefaultValue()) {
        Node nameOrPattern = param.removeFirstChild();
        // We'll be cloning nameOrPattern below, and we don't want to clone the JSDoc info with it
        JSDocInfo jsDoc = nameOrPattern.getJSDocInfo();
        nameOrPattern.setJSDocInfo(null);
        Node defaultValue = param.removeFirstChild();
        Node newParam;

        // Treat name=undefined (and equivalent) as if it was just name.  There
        // is no need to generate a (name===void 0?void 0:name) statement for
        // such arguments.
        boolean isNoop = false;
        if (!nameOrPattern.isName()) {
          // Do not try to optimize unless nameOrPattern is a simple name.
        } else if (defaultValue.isName()) {
          isNoop = "undefined".equals(defaultValue.getString());
        } else if (defaultValue.isVoid()) {
          // Any kind of 'void literal' is fine, but 'void fun()' or anything
          // else with side effects isn't.  We're not trying to be particularly
          // smart here and treat 'void {}' for example as if it could cause side effects.
          isNoop = NodeUtil.isImmutableValue(defaultValue.getFirstChild());
        }

        if (isNoop) {
          newParam = nameOrPattern.cloneTree();
        } else {
          newParam =
              nameOrPattern.isName()
                  ? nameOrPattern
                  : createTempVarNameNode(getTempVariableName(), type(nameOrPattern));
          Node lhs = nameOrPattern.cloneTree();
          Node rhs = defaultValueHook(newParam.cloneTree(), defaultValue);
          Node newStatement =
              nameOrPattern.isName()
                  ? IR.exprResult(astFactory.createAssign(lhs, rhs))
                  : IR.var(lhs, rhs);
          newStatement.srcrefTreeIfMissing(param);
          if (insertSpot == null) {
            // insert new declarations only after all inner function declarations in that function
            // body to preserve normalization
            insertSpot = NodeUtil.getInsertionPointAfterAllInnerFunctionDeclarations(body);
            if (insertSpot != null) {
              newStatement.insertBefore(insertSpot);
            } else {
              // functionBody only contains hoisted function declarations
              body.addChildToBack(newStatement);
            }
          } else {
            newStatement.insertAfter(insertSpot);
          }
          insertSpot = newStatement;
          if (newStatement.getFirstChild().isDestructuringLhs()) {
            insertSpot = rewriteVarDestructuringDeclaration(newStatement.getFirstChild());
          }
        }

        param.replaceWith(newParam);
        newParam.setJSDocInfo(jsDoc);

        compiler.reportChangeToChangeScope(function);
      } else if (param.isDestructuringPattern()) {
        insertSpot =
            replacePatternParamWithTempVar(function, insertSpot, param, getTempVariableName());
        compiler.reportChangeToChangeScope(function);
      } else if (param.isRest() && param.getFirstChild().isDestructuringPattern()) {
        insertSpot =
            replacePatternParamWithTempVar(
                function, insertSpot, param.getFirstChild(), getTempVariableName());
        compiler.reportChangeToChangeScope(function);
      }
    }
  }

  /**
   * Replace a destructuring pattern parameter with a a temporary parameter name. To do this, this
   * function adds a new local assignment in the function body assigning the temporary parameter to
   * the pattern and creates stub declarations for the individual names in the destructuring
   * pattern.
   *
   * <p>For example, given the function `function f([a, b]) {}`, this function will convert it to
   * `function f(tempVar) { var a; var b; [a, b] = tempVar; }`
   *
   * <p>Note: Rewrites of the destructuring pattern will happen later to rewrite this destructuring
   * pattern as non-destructured code.
   *
   * @param insertSpot The local variable declaration will be inserted after this statement.
   * @param tempVarName the name to use for the temporary variable
   * @return the last stub declaration that was generated for the local variable
   */
  private Node replacePatternParamWithTempVar(
      Node function, Node insertSpot, Node patternParam, String tempVarName) {
    // Convert `function f([a, b]) {}` to `function f(tempVar) { var [a, b] = tempVar; }`
    AstFactory.Type paramType = type(patternParam);
    Node newParam = createTempVarNameNode(tempVarName, paramType);
    newParam.setJSDocInfo(patternParam.getJSDocInfo());
    patternParam.replaceWith(newParam);
    Node newDecl = IR.var(patternParam, createTempVarNameNode(tempVarName, paramType));
    newDecl.srcrefTreeIfMissing(patternParam);

    if (insertSpot == null) {
      // insert new declarations only after all inner function declarations in that function body to
      // preserve normalization
      insertSpot =
          NodeUtil.getInsertionPointAfterAllInnerFunctionDeclarations(function.getLastChild());
      if (insertSpot != null) {
        newDecl.insertBefore(insertSpot);
      } else {
        // functionBody only contains hoisted function declarations
        function.getLastChild().addChildToBack(newDecl);
      }
    } else {
      newDecl.insertAfter(insertSpot);
    }

    return rewriteVarDestructuringDeclaration(newDecl.getFirstChild());
  }

  /**
   * Rewrites a destructuring declaration to a destructuring assignment.
   *
   * <p>For example, given the code `var [a, b] = ...;`, this function will convert it to `var a;
   * var b; [a, b] = ...;`
   *
   * @param destructuringLhs the destructuring declaration to rewrite
   * @return the expression result containing the assignment
   */
  private Node rewriteVarDestructuringDeclaration(Node destructuringLhs) {
    checkState(destructuringLhs.isDestructuringLhs(), destructuringLhs);
    checkState(
        !destructuringLhs.getGrandparent().isExport(),
        "Export destructuring declarations not expected inside a function body.");
    Node var = destructuringLhs.getParent();

    // create a stub declaration for each name in the destructuring pattern
    // e.g. generate `var a; var b;` from `var [a, b] = ...;`
    NodeUtil.visitLhsNodesInNode(
        destructuringLhs,
        (name) -> {
          // Add a declaration outside the destructuring pattern for the given name.
          checkState(
              name.isName(),
              "lhs in destructuring declaration should be a simple name. (%s)",
              name);
          Node newName = IR.name(name.getString()).srcref(name);
          if (name.getBooleanProp(Node.IS_CONSTANT_NAME)) {
            // if old name was a const, new name should be too
            // e.g. when rewriting `{VALUE} = ...` the `VALUE` is const by coding convention
            newName.putBooleanProp(Node.IS_CONSTANT_NAME, true);
          }
          Node newVar = IR.var(newName).srcref(name);
          newVar.insertBefore(var);
        });

    // Transform destructuring var declaration to assignment. That is, `var [a, b] = ...` to `[a,
    // b] = ...` and `var {a, b} = ...` to `({a, b} = ...);`
    Node destructuringPattern = destructuringLhs.removeFirstChild();
    checkState(
        destructuringPattern.isDestructuringPattern(),
        "Expected destructuring pattern but got %s",
        destructuringPattern.toStringTree());

    Node rhs = destructuringLhs.removeFirstChild();
    Node assign = astFactory.createAssign(destructuringPattern, rhs);
    assign.srcref(var);
    Node expr = astFactory.exprResult(assign);
    expr.srcref(var);

    var.replaceWith(expr);
    return expr;
  }

  /**
   * Creates a new name node and adds the constant name property because a constant variable is used
   * (the compiler only assigns $jscomp$destructuring$var[num] once)
   */
  private Node createTempVarNameNode(String name, AstFactory.Type type) {
    // NOTE: This does not really create a constant node as this pass runs before normalization. See
    // b/322009741.
    return astFactory.createConstantName(name, type);
  }

  /** Creates a new unique name to use for a pattern we need to rewrite. */
  private String getTempVariableName() {
    return DESTRUCTURING_TEMP_VAR + destructuringVarCounter++;
  }

  private void visitPattern(NodeTraversal t, Node pattern) {
    Node parent = pattern.getParent();

    switch (parent.getToken()) {
      case DESTRUCTURING_LHS -> {
        {
          Node declaration = parent.getParent();
          Node declarationParent = declaration.getParent();
          if (declarationParent.isVanillaFor()) {
            visitDestructuringPatternInVanillaForInnerVars(t, pattern);
          } else if (NodeUtil.isEnhancedFor(declarationParent)) {
            visitDestructuringPatternInEnhancedForInnerVars(pattern);
          } else {
            replacePattern(t, pattern, pattern.getNext(), declaration, declaration);
          }
        }
      }
      case ASSIGN -> {
        if (parent.getParent().isExprResult()) {
          replacePattern(t, pattern, pattern.getNext(), parent, parent.getParent());
        } else {
          wrapAssignOrDestructuringInCallToArrow(t, parent);
        }
      }
      case OBJECT_REST, ITER_REST, STRING_KEY, ARRAY_PATTERN, DEFAULT_VALUE, COMPUTED_PROP -> {
        // Nested pattern; do nothing. We will visit it after rewriting the parent.
      }
      case FOR_OF, FOR_IN, FOR_AWAIT_OF ->
          visitDestructuringPatternInEnhancedForWithOuterVars(pattern);
      case CATCH -> visitDestructuringPatternInCatch(t, pattern);
      default -> throw new IllegalStateException("unexpected parent");
    }
  }

  /**
   * Transpiles a destructuring pattern in a declaration or assignment to ES5
   *
   * @param nodeToDetach a statement node containing the pattern. This method will replace the node
   *     with one or more other statements.
   */
  private void replacePattern(
      NodeTraversal t, Node pattern, Node rhs, Node parent, Node nodeToDetach) {
    checkArgument(NodeUtil.isStatement(nodeToDetach), nodeToDetach);
    switch (pattern.getToken()) {
      case ARRAY_PATTERN -> replaceArrayPattern(t, pattern, rhs, parent, nodeToDetach);
      case OBJECT_PATTERN -> replaceObjectPattern(t, pattern, rhs, parent, nodeToDetach);
      default -> throw new IllegalStateException("unexpected");
    }
  }

  /**
   * Convert 'var {a: b, c: d} = rhs' to:
   *
   * @const var temp = rhs; var b = temp.a; var d = temp.c;
   */
  private void replaceObjectPattern(
      NodeTraversal t, Node objectPattern, Node rhs, Node parent, Node nodeToDetach) {
    String tempVarName = getTempVariableName();
    final AstFactory.Type tempVarType = type(objectPattern);

    String restTempVarName = null;
    // If the last child is a rest node we will want a list of the stated properties so we can
    // exclude them from being written to the rest variable.
    ArrayList<Node> propsToDeleteForRest = null;
    if (objectPattern.hasChildren() && objectPattern.getLastChild().isRest()) {
      propsToDeleteForRest = new ArrayList<>();
      restTempVarName = getTempVariableName();
    } else if (rewriteMode == ObjectDestructuringRewriteMode.REWRITE_OBJECT_REST) {
      // We are configured to only break object pattern rest, but this destructure has none.
      if (!this.patternNestingStack.peekLast().hasNestedObjectRest) {
        // Replacement is performed after the post-order visit has reached the root pattern node, so
        // peeking last represents if there is a rest property anywhere in the entire pattern. All
        // nesting levels of lower levels have already been popped.
        destructuringVarCounter--;
        return;
      }
    }

    // create the declaration `var temp = rhs;`
    Node tempDecl =
        IR.var(createTempVarNameNode(tempVarName, tempVarType), rhs.detach())
            .srcrefTreeIfMissing(objectPattern);
    // TODO(tbreisacher): Remove the "if" and add this JSDoc unconditionally.
    if (parent.isConst()) {
      JSDocInfo.Builder jsDoc = JSDocInfo.builder();
      jsDoc.recordConstancy();
      tempDecl.setJSDocInfo(jsDoc.build());
    }
    tempDecl.insertBefore(nodeToDetach);

    for (Node child = objectPattern.getFirstChild(), next; child != null; child = next) {
      next = child.getNext();

      final Node newLHS;
      final Node newRHS;
      if (child.isStringKey()) {
        // const {a: b} = obj;
        Node tempVarNameNode = createTempVarNameNode(tempVarName, tempVarType);
        Node getprop =
            child.isQuotedStringKey()
                ? astFactory.createGetElem(
                    tempVarNameNode, astFactory.createString(child.getString()))
                : astFactory.createGetProp(tempVarNameNode, child.getString(), tempVarType);

        Node value = child.removeFirstChild();
        if (!value.isDefaultValue()) {
          newLHS = value;
          newRHS = getprop;
        } else {
          newLHS = value.removeFirstChild();
          Node defaultValue = value.removeFirstChild();
          newRHS = defaultValueHook(getprop, defaultValue);
        }
        if (propsToDeleteForRest != null) {
          propsToDeleteForRest.add(child);
        }
      } else if (child.isComputedProp()) {
        // const {[propExpr]: newLHS = defaultValue} = newRHS;
        boolean hasDefault = child.getLastChild().isDefaultValue();
        final Node defaultValue;
        Node propExpr = child.removeFirstChild();
        if (hasDefault) {
          Node defaultNode = child.getLastChild();
          newLHS = defaultNode.removeFirstChild();
          defaultValue = defaultNode.removeFirstChild();
        } else {
          newLHS = child.removeFirstChild();
          defaultValue = null;
        }
        if (propsToDeleteForRest != null) {
          // A "...rest" variable is present and result of computation must be cached
          String exprEvalTempVarName = getTempVariableName();
          Node exprEvalTempVarModel =
              createTempVarNameNode(exprEvalTempVarName, type(propExpr)); // clone this node
          Node exprEvalDecl = IR.var(exprEvalTempVarModel.cloneNode(), propExpr);
          exprEvalDecl.srcrefTreeIfMissing(child);
          exprEvalDecl.insertBefore(nodeToDetach);
          propExpr = exprEvalTempVarModel.cloneNode();
          propsToDeleteForRest.add(exprEvalTempVarModel.cloneNode());
        }
        if (hasDefault) {
          // tempVarName[propExpr]
          Node getelem =
              astFactory.createGetElem(createTempVarNameNode(tempVarName, tempVarType), propExpr);

          // var tempVarName2 = tempVarName1[propExpr]
          String intermediateTempVarName = getTempVariableName();
          Node intermediateDecl =
              IR.var(createTempVarNameNode(intermediateTempVarName, type(getelem)), getelem);
          intermediateDecl.srcrefTreeIfMissing(child);
          intermediateDecl.insertBefore(nodeToDetach);

          // tempVarName2 === undefined ? defaultValue : tempVarName2
          newRHS =
              defaultValueHook(
                  createTempVarNameNode(intermediateTempVarName, type(getelem)), defaultValue);
        } else {
          newRHS =
              astFactory.createGetElem(createTempVarNameNode(tempVarName, type(newLHS)), propExpr);
        }
      } else if (child.isRest()) {
        if (next != null) {
          throw new IllegalStateException("object rest may not be followed by any properties");
        }
        // TODO(b/116532470): see if casting this to a more specific type fixes disambiguation
        Node assignCall =
            astFactory.createCall(
                astFactory.createQName(this.namespace, "Object.assign"),
                type(StandardColors.TOP_OBJECT));
        assignCall.addChildToBack(astFactory.createObjectLit());
        assignCall.addChildToBack(createTempVarNameNode(tempVarName, tempVarType));

        Node restTempDecl = IR.var(createTempVarNameNode(restTempVarName, tempVarType), assignCall);
        restTempDecl.srcrefTreeIfMissing(objectPattern);
        restTempDecl.insertAfter(tempDecl);

        Node restName = child.getOnlyChild(); // e.g. get `rest` from `const {...rest} = {};`
        if (restName.getString().startsWith(DESTRUCTURING_TEMP_VAR)) {
          checkState(restName.isName(), restName);
          newLHS = createTempVarNameNode(restName.getString(), type(restName));
        } else {
          newLHS = restName.detach();
        }
        newRHS = objectPatternRestRHS(objectPattern, child, restTempVarName, propsToDeleteForRest);
      } else {
        throw new IllegalStateException("unexpected child");
      }

      Node newNode;
      if (NodeUtil.isNameDeclaration(parent)) {
        if (parent.isConst()) {
          newLHS.putBooleanProp(Node.IS_CONSTANT_NAME, true);
        }
        newNode = IR.declaration(newLHS, newRHS, parent.getToken());
      } else if (parent.isAssign()) {
        newNode = IR.exprResult(astFactory.createAssign(newLHS, newRHS));
      } else {
        throw new IllegalStateException("not reached");
      }
      newNode.srcrefTreeIfMissing(child);

      newNode.insertBefore(nodeToDetach);

      // Explicitly visit the LHS of the new node since it may be a nested
      // destructuring pattern.
      visit(t, newLHS, newLHS.getParent());
    }

    nodeToDetach.detach();
    t.reportCodeChange();
  }

  /**
   * Convert "rest" of object destructuring lhs by making a clone and deleting any properties that
   * were stated in the original object pattern.
   *
   * <p>Nodes in statedProperties that are a stringKey will be used in a getprop when deleting. All
   * other types will be used in a getelem such as what is done for computed properties.
   *
   * <pre>
   *   {a, [foo()]:b, ...x} = rhs;
   * becomes
   *   var temp = rhs;
   *   var temp1 = Object.assign({}, temp);
   *   var temp2 = foo()
   *   a = temp.a
   *   b = temp[foo()]
   *   x = (delete temp1.a, delete temp1[temp2], temp1);
   * </pre>
   *
   * @param rest node representing the "...rest" of objectPattern
   * @param restTempVarName name of var containing clone of result of rhs evaluation
   * @param statedProperties list of properties to delete from the clone
   */
  private Node objectPatternRestRHS(
      Node objectPattern, Node rest, String restTempVarName, ArrayList<Node> statedProperties) {
    checkArgument(objectPattern.getLastChild() == rest);
    Node restTempVarModel = createTempVarNameNode(restTempVarName, type(objectPattern));
    Node result = restTempVarModel.cloneNode();
    if (!statedProperties.isEmpty()) {
      Iterator<Node> propItr = statedProperties.iterator();
      Node comma = deletionNodeForRestProperty(restTempVarModel.cloneNode(), propItr.next());
      while (propItr.hasNext()) {
        comma =
            astFactory.createComma(
                comma, deletionNodeForRestProperty(restTempVarModel.cloneNode(), propItr.next()));
      }
      result = astFactory.createComma(comma, result);
    }
    result.srcrefTreeIfMissing(rest);
    return result;
  }

  private Node deletionNodeForRestProperty(Node restTempVarNameNode, Node property) {
    final Node get =
        switch (property.getToken()) {
          case STRING_KEY ->
              property.isQuotedStringKey()
                  ? astFactory.createGetElem(
                      restTempVarNameNode, astFactory.createString(property.getString()))
                  : astFactory.createGetPropWithUnknownType(
                      restTempVarNameNode, property.getString());
          case NAME -> astFactory.createGetElem(restTempVarNameNode, property);
          default ->
              throw new IllegalStateException(
                  "Unexpected property to delete node: " + property.toStringTree());
        };

    return astFactory.createDelProp(get);
  }

  /**
   * Convert <pre>var [x, y] = rhs<pre> to:
   * <pre>
   *   var temp = $jscomp.makeIterator(rhs);
   *   var x = temp.next().value;
   *   var y = temp.next().value;
   * </pre>
   */
  private void replaceArrayPattern(
      NodeTraversal t, Node arrayPattern, Node rhs, Node parent, Node nodeToDetach) {

    if (rewriteMode == ObjectDestructuringRewriteMode.REWRITE_OBJECT_REST) {
      if (patternNestingStack.isEmpty() || !patternNestingStack.peekLast().hasNestedObjectRest) {
        return;
      }
    }

    String tempVarName = getTempVariableName();
    Node makeIteratorCall = astFactory.createJSCompMakeIteratorCall(rhs.detach(), this.namespace);
    Node tempDecl = astFactory.createSingleVarNameDeclaration(tempVarName, makeIteratorCall);
    Node tempVarModel = tempDecl.getFirstChild();
    tempDecl.srcrefTreeIfMissing(arrayPattern);
    tempDecl.insertBefore(nodeToDetach);

    for (Node child = arrayPattern.getFirstChild(), next; child != null; child = next) {
      next = child.getNext();
      if (child.isEmpty()) {
        // Just call the next() method to advance the iterator, but throw away the value.
        Node nextCall =
            IR.exprResult(
                astFactory.createCallWithUnknownType(
                    astFactory.createGetProp(
                        tempVarModel.cloneNode(), "next", type(StandardColors.TOP_OBJECT))));
        nextCall.srcrefTreeIfMissing(child);
        nextCall.insertBefore(nodeToDetach);
        continue;
      }

      Node newLHS;
      Node newRHS;
      if (child.isDefaultValue()) {
        //   [x = defaultValue] = rhs;
        // becomes
        //   var temp0 = $jscomp.makeIterator(rhs);
        //   var temp1 = temp.next().value
        //   x = (temp1 === undefined) ? defaultValue : temp1;
        String nextVarName = getTempVariableName();
        // `temp.next().value`
        Node nextCallDotValue =
            astFactory.createGetPropWithUnknownType(
                astFactory.createCallWithUnknownType(
                    astFactory.createGetPropWithUnknownType(tempVarModel.cloneNode(), "next")),
                "value");
        AstFactory.Type nextVarType = type(nextCallDotValue);
        // `var temp1 = temp.next().value`
        Node var = IR.var(createTempVarNameNode(nextVarName, nextVarType), nextCallDotValue);
        var.srcrefTreeIfMissing(child);
        var.insertBefore(nodeToDetach);

        // `x`
        newLHS = child.removeFirstChild();
        // `(temp1 === undefined) ? defaultValue : temp1;
        newRHS =
            defaultValueHook(
                createTempVarNameNode(nextVarName, nextVarType), child.getLastChild().detach());
      } else if (child.isRest()) {
        //   [...x] = rhs;
        // becomes
        //   var temp = $jscomp.makeIterator(rhs);
        //   x = $jscomp.arrayFromIterator(temp);
        newLHS = child.removeFirstChild();
        newRHS = astFactory.createJscompArrayFromIteratorCall(tempVarModel.cloneNode(), namespace);
      } else {
        // LHS is just a name (or a nested pattern).
        //   var [x] = rhs;
        // becomes
        //   var temp = $jscomp.makeIterator(rhs);
        //   var x = temp.next().value;
        newLHS = child.detach();
        newRHS =
            astFactory.createGetProp(
                astFactory.createCallWithUnknownType(
                    astFactory.createGetPropWithUnknownType(tempVarModel.cloneNode(), "next")),
                "value",
                type(child));
      }
      Node newNode;
      if (parent.isAssign()) {
        Node assignment = astFactory.createAssign(newLHS, newRHS);
        newNode = IR.exprResult(assignment);
      } else {
        newNode = IR.declaration(newLHS, newRHS, parent.getToken());
      }
      newNode.srcrefTreeIfMissing(arrayPattern);

      newNode.insertBefore(nodeToDetach);
      // Explicitly visit the LHS of the new node since it may be a nested
      // destructuring pattern.
      visit(t, newLHS, newLHS.getParent());
    }

    nodeToDetach.detach();
    t.reportCodeChange();
  }

  /**
   * Replace ASSIGN or DESTRUCTURING_LHS with a IIFE that contains the transpiled destructuring.
   *
   * <pre>
   * Transform
   *   [x, y] = rhs
   * into
   *   {@code ((temp0) => {
   *     var temp1 = $jscomp.makeIterator(temp0);
   *     var x = temp0.next().value;
   *     var y = temp0.next().value;
   *     return temp0;
   *   })(rhs)}
   *
   * Transform
   *   {x: a, y: b} = rhs
   * into
   *   {@code ((temp0) => {
   *     var temp1 = temp0;
   *     var a = temp0.x;
   *     var b = temp0.y;
   *     return temp0;
   *   })(rhs)}
   * </pre>
   */
  private void wrapAssignOrDestructuringInCallToArrow(NodeTraversal t, Node assignment) {
    Node lhs = assignment.getFirstChild();
    Node rhs = assignment.getLastChild().detach();
    // NOTE: we do not presently support await/yield in the LHS of nested destructuring assignments.
    // See b/475296868.
    validateNoAwaitOrYieldInNestedAssignmentPattern(lhs);

    String tempVarName = getTempVariableName();

    Node tempVarModel = createTempVarNameNode(tempVarName, type(rhs));
    //  ((temp0) => {...})
    Node paramList = IR.paramList(tempVarModel.cloneNode());
    // [x, y] = temp0;
    Node replacementExpr =
        astFactory.createAssign(assignment.removeFirstChild(), tempVarModel.cloneNode());
    Node exprResult = IR.exprResult(replacementExpr);
    // return temp0;
    Node returnNode = IR.returnNode(tempVarModel.cloneNode());

    // Create a function to hold these assignments:
    Node block = IR.block(exprResult, returnNode);
    Node arrowFn =
        astFactory.createFunction(
            /* name= */ "",
            paramList,
            block,
            type(JSTypeNative.UNKNOWN_TYPE, StandardColors.UNKNOWN));
    arrowFn.setIsArrowFunction(true);

    // Create a call to the function, and replace the pattern with the call.
    Node call = astFactory.createCall(arrowFn, type(rhs), rhs);
    NodeUtil.addFeatureToScript(t.getCurrentScript(), Feature.ARROW_FUNCTIONS, compiler);
    call.srcrefTreeIfMissing(assignment);
    call.putBooleanProp(Node.FREE_CALL, true);
    assignment.replaceWith(call);
    NodeUtil.markNewScopesChanged(call, compiler);

    replacePattern(
        t,
        replacementExpr.getFirstChild(),
        replacementExpr.getLastChild(),
        replacementExpr,
        exprResult);
  }

  private void validateNoAwaitOrYieldInNestedAssignmentPattern(Node pattern) {
    boolean hasAwaitOrYieldInLhs =
        NodeUtil.findPreorder(
                pattern, (n) -> n.isAwait() || n.isYield(), NodeUtil.MATCH_NOT_FUNCTION)
            != null;
    checkState(
        !hasAwaitOrYieldInLhs,
        "Cannot transpile yet: destructuring assignment referencing await or yield in lhs, in"
            + " nested sub-expression: %s",
        pattern);
  }

  /** for (let [a, b, c] = arr; a < b; a++) */
  private void visitDestructuringPatternInVanillaForInnerVars(NodeTraversal t, Node pattern) {
    checkArgument(pattern.isDestructuringPattern());
    Node destructuringLhs = pattern.getParent();
    Node declaration = destructuringLhs.getParent();

    Node insertionPoint = declaration.getParent();
    while (insertionPoint.getParent().isLabel()) {
      insertionPoint = insertionPoint.getParent();
    }

    switch (declaration.getToken()) {
      case CONST:
        {
          Node block = IR.block().srcref(insertionPoint);
          insertionPoint.replaceWith(block);
          block.addChildToBack(insertionPoint);
        }
      // Fall through

      case VAR:
        {
          // Move any earlier variables out of the loop initializer
          for (Node c = declaration.getFirstChild();
              c != destructuringLhs;
              c = declaration.getFirstChild()) {
            Node newDeclaration = declaration.cloneNode();
            newDeclaration.addChildToBack(c.detach());
            newDeclaration.insertBefore(insertionPoint);
          }

          // Move the pattern out of the initializer and transpile it
          Node newDeclaration = declaration.cloneNode();
          newDeclaration.addChildToBack(destructuringLhs.detach());
          newDeclaration.insertBefore(insertionPoint);
          this.replacePattern(t, pattern, pattern.getNext(), newDeclaration, newDeclaration);

          if (!declaration.hasChildren()) {
            declaration.replaceWith(IR.empty());
          }
        }
        break;

      case LET:
        // See https://tc39.es/ecma262/#sec-createperiterationenvironment
        // for (let a, b, c, unusedTmp = (() => [a, b, c] = arr); a < b; a++)
        {
          ParsingUtil.getParamOrPatternNames(
              pattern, (name) -> name.cloneNode().insertBefore(destructuringLhs));

          Node unusedVar =
              this.astFactory
                  .createNameWithUnknownType(getTempVariableName() + "$unused")
                  .srcref(destructuringLhs);
          destructuringLhs.replaceWith(unusedVar);
          unusedVar.addChildToBack(destructuringLhs);
          this.wrapAssignOrDestructuringInCallToArrow(t, destructuringLhs);
        }
        break;

      default:
        throw new IllegalStateException(declaration.toString());
    }

    this.compiler.reportChangeToEnclosingScope(insertionPoint);
  }

  /** for (const [a, b, c] of arr) */
  private void visitDestructuringPatternInEnhancedForInnerVars(Node pattern) {
    checkArgument(pattern.isDestructuringPattern());
    String tempVarName = getTempVariableName();

    Node destructuringLhs = pattern.getParent();
    checkState(destructuringLhs.isDestructuringLhs());
    Node declarationNode = destructuringLhs.getParent();
    Node forNode = declarationNode.getParent();
    checkState(NodeUtil.isEnhancedFor(forNode));
    Node block = forNode.getLastChild();

    destructuringLhs.replaceWith(createTempVarNameNode(tempVarName, type(pattern)).srcref(pattern));
    Token declarationType = declarationNode.getToken();
    Node decl =
        IR.declaration(
            pattern.detach(), createTempVarNameNode(tempVarName, type(pattern)), declarationType);
    decl.srcrefTreeIfMissing(pattern);
    // Move the body into an inner block to handle cases where declared variables in the for
    // loop initializer are shadowed by variables in the for loop body. e.g.
    //   for (const [value] of []) { const value = 1; }
    Node newBlock = IR.block(decl);
    block.replaceWith(newBlock);
    newBlock.addChildToBack(block);
  }

  /**
   * for ([a, b, c] of arr)
   *
   * <pre>
   * Transform
   *   for ({x} of y) {}
   * into
   *   {@code var TEMP_VAR0;
   *   for (TEMP_VAR0 of y) {
   *     var TEMP_VAR1 = TEMP_VAR0;
   *     x = TEMP_VAR1.x;
   *   }}
   * </pre>
   */
  private void visitDestructuringPatternInEnhancedForWithOuterVars(Node pattern) {
    checkArgument(pattern.isDestructuringPattern());
    String tempVarName = getTempVariableName();

    Node forNode = pattern.getParent();
    Node block = forNode.getLastChild();

    Node name = createTempVarNameNode(tempVarName, type(pattern));
    Node decl = IR.var(name);
    decl.srcrefTreeIfMissing(pattern);
    decl.insertBefore(forNode);
    Node clonedName = name.cloneNode();
    clonedName.srcrefTreeIfMissing(pattern);
    pattern.replaceWith(clonedName);
    Node exprResult =
        IR.exprResult(
            astFactory.createAssign(pattern, createTempVarNameNode(tempVarName, type(pattern))));
    exprResult.srcrefTreeIfMissing(pattern);
    block.addChildToFront(exprResult);
  }

  private void visitDestructuringPatternInCatch(NodeTraversal t, Node pattern) {
    String tempVarName = getTempVariableName();
    Node catchBlock = pattern.getNext();
    AstFactory.Type patternType = type(pattern);

    pattern.replaceWith(createTempVarNameNode(tempVarName, patternType));
    catchBlock.addChildToFront(
        IR.declaration(pattern, createTempVarNameNode(tempVarName, patternType), Token.LET));
    NodeUtil.addFeatureToScript(t.getCurrentScript(), Feature.LET_DECLARATIONS, compiler);
  }

  /** Helper for transpiling DEFAULT_VALUE trees. */
  private Node defaultValueHook(Node getprop, Node defaultValue) {
    Node undefined = astFactory.createUndefinedValue();
    undefined.makeNonIndexable();
    Node getpropClone = getprop.cloneTree().setColor(getprop.getColor());
    return astFactory.createHook(
        astFactory.createSheq(getprop, undefined), defaultValue, getpropClone);
  }
}
