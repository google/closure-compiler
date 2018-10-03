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
import static com.google.javascript.jscomp.DiagnosticType.error;

import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;

/**
 * Rewrites destructuring patterns and default parameters to valid ES3 code or to a different form
 * of destructuring.
 */
public final class Es6RewriteDestructuring implements NodeTraversal.Callback, HotSwapCompilerPass {

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

  private final FeatureSet featuresToTriggerRunningPass;
  private final FeatureSet featuresToMarkAsRemoved;

  private final Deque<PatternNestingLevel> patternNestingStack = new ArrayDeque<>();

  static final String DESTRUCTURING_TEMP_VAR = "$jscomp$destructuring$var";

  private int destructuringVarCounter = 0;

  private Es6RewriteDestructuring(Builder builder) {
    this.compiler = builder.compiler;
    this.rewriteMode = builder.rewriteMode;
    this.astFactory = compiler.createAstFactory();

    switch (this.rewriteMode) {
      case REWRITE_ALL_OBJECT_PATTERNS:
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
        break;
      case REWRITE_OBJECT_REST:
        // TODO(bradfordcsmith): We shouldn't really need to remove default parameters for this
        // case.
        this.featuresToTriggerRunningPass =
            FeatureSet.BARE_MINIMUM.with(Feature.OBJECT_PATTERN_REST);
        this.featuresToMarkAsRemoved = this.featuresToTriggerRunningPass;
        break;
      default:
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
    TranspilationPasses.processTranspile(compiler, externs, featuresToTriggerRunningPass, this);
    TranspilationPasses.processTranspile(compiler, root, featuresToTriggerRunningPass, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, featuresToMarkAsRemoved);
    checkState(patternNestingStack.isEmpty());
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    checkState(patternNestingStack.isEmpty());
    TranspilationPasses.hotSwapTranspile(compiler, scriptRoot, featuresToTriggerRunningPass, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, featuresToMarkAsRemoved);
    checkState(patternNestingStack.isEmpty());
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case FUNCTION:
        ensureArrowFunctionsHaveBlockBodies(t, n);
        break;
      case PARAM_LIST:
        pullDestructuringOutOfParams(n, parent);
        break;
      case ARRAY_PATTERN:
      case OBJECT_PATTERN:
        {
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
          break;
        }
      default:
        break;
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (parent != null && parent.isDestructuringLhs()) {
      parent = parent.getParent();
    }
    switch (n.getToken()) {
      case ARRAY_PATTERN:
      case OBJECT_PATTERN:
        visitPattern(t, n, parent);
        if (n == this.patternNestingStack.getLast().pattern) {
          this.patternNestingStack.removeLast();
        }
        break;
      default:
        break;
    }
  }

  /**
   * If the function is an arrow function, wrap the body in a block if it is not already a block.
   */
  // TODO(bradfordcsmith): This should be separated from this pass.
  private void ensureArrowFunctionsHaveBlockBodies(NodeTraversal t, Node function) {
    Node body = function.getLastChild();
    if (!body.isBlock()) {
      body.detach();
      Node replacement = IR.block(IR.returnNode(body)).useSourceInfoIfMissingFromForTree(body);
      function.addChildToBack(replacement);
      t.reportCodeChange();
    }
  }

  /** Pulls all default and destructuring parameters out of function parameters. */
  // TODO(bradfordcsmith): Ideally if we're only removing OBJECT_REST, we should only do this when
  // the parameter list contains a usage of OBJECT_REST.
  private void pullDestructuringOutOfParams(Node paramList, Node function) {
    Node insertSpot = null;
    Node body = function.getLastChild();
    int i = 0;
    Node next = null;
    for (Node param = paramList.getFirstChild(); param != null; param = next, i++) {
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
                  : astFactory.createName(
                      getTempParameterName(function, i), nameOrPattern.getJSType());
          Node lhs = nameOrPattern.cloneTree();
          Node rhs = defaultValueHook(newParam.cloneTree(), defaultValue);
          Node newStatement =
              nameOrPattern.isName()
                  ? IR.exprResult(astFactory.createAssign(lhs, rhs))
                  : IR.var(lhs, rhs);
          newStatement.useSourceInfoIfMissingFromForTree(param);
          body.addChildAfter(newStatement, insertSpot);
          insertSpot = newStatement;
        }

        paramList.replaceChild(param, newParam);
        newParam.setOptionalArg(true);
        newParam.setJSDocInfo(jsDoc);

        compiler.reportChangeToChangeScope(function);
      } else if (param.isDestructuringPattern()) {
        insertSpot =
            replacePatternParamWithTempVar(
                function, insertSpot, param, getTempParameterName(function, i));
        compiler.reportChangeToChangeScope(function);
      } else if (param.isRest() && param.getFirstChild().isDestructuringPattern()) {
        insertSpot =
            replacePatternParamWithTempVar(
                function, insertSpot, param.getFirstChild(), getTempParameterName(function, i));
        compiler.reportChangeToChangeScope(function);
      }
    }
  }

  /**
   * Replace a destructuring pattern parameter with a a temporary parameter name and add a new
   * local variable declaration to the function assigning the temporary parameter to the pattern.
   *
   * <p> Note: Rewrites of variable declaration destructuring will happen later to rewrite
   * this declaration as non-destructured code.
   * @param function
   * @param insertSpot The local variable declaration will be inserted after this statement.
   * @param patternParam
   * @param tempVarName the name to use for the temporary variable
   * @return the declaration statement that was generated for the local variable
   */
  private Node replacePatternParamWithTempVar(
      Node function, Node insertSpot, Node patternParam, String tempVarName) {
    // Convert `function f([a, b]) {}` to `function f(tempVar) { var [a, b] = tempVar; }`
    JSType paramType = patternParam.getJSType();
    Node newParam = astFactory.createName(tempVarName, paramType);
    newParam.setJSDocInfo(patternParam.getJSDocInfo());
    patternParam.replaceWith(newParam);
    Node newDecl = IR.var(patternParam, astFactory.createName(tempVarName, paramType));
    function.getLastChild().addChildAfter(newDecl, insertSpot);
    return newDecl;
  }

  /**
   * Find or create the best name to use for a parameter we need to rewrite.
   *
   * <ol>
   * <li> Use the JS Doc function parameter name at the given index, if possible.
   * <li> Otherwise, build one of our own.
   * </ol>
   * @param function
   * @param parameterIndex
   * @return name to use for the given parameter
   */
  private String getTempParameterName(Node function, int parameterIndex) {
    String tempVarName;
    JSDocInfo fnJSDoc = NodeUtil.getBestJSDocInfo(function);
    if (fnJSDoc != null && fnJSDoc.getParameterNameAt(parameterIndex) != null) {
      tempVarName = fnJSDoc.getParameterNameAt(parameterIndex);
    } else {
      tempVarName = DESTRUCTURING_TEMP_VAR + (destructuringVarCounter++);
    }
    checkState(TokenStream.isJSIdentifier(tempVarName));
    return tempVarName;
  }

  private void visitPattern(NodeTraversal t, Node pattern, Node parent) {
    if (NodeUtil.isNameDeclaration(parent) && !NodeUtil.isEnhancedFor(parent.getParent())) {
      replacePattern(t, pattern, pattern.getNext(), parent, parent);
    } else if (parent.isAssign()) {
      if (parent.getParent().isExprResult()) {
        replacePattern(t, pattern, pattern.getNext(), parent, parent.getParent());
      } else {
        wrapAssignmentInCallToArrow(t, parent);
      }
    } else if (parent.isRest()
        || parent.isStringKey()
        || parent.isArrayPattern()
        || parent.isDefaultValue()
        || parent.isComputedProp()) {
      // Nested pattern; do nothing. We will visit it after rewriting the parent.
    } else if (NodeUtil.isEnhancedFor(parent) || NodeUtil.isEnhancedFor(parent.getParent())) {
      visitDestructuringPatternInEnhancedFor(pattern);
    } else if (parent.isCatch()) {
      visitDestructuringPatternInCatch(t, pattern);
    } else {
      throw new IllegalStateException("unexpected parent");
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
      case ARRAY_PATTERN:
        replaceArrayPattern(t, pattern, rhs, parent, nodeToDetach);
        break;
      case OBJECT_PATTERN:
        replaceObjectPattern(t, pattern, rhs, parent, nodeToDetach);
        break;
      default:
        throw new IllegalStateException("unexpected");
    }
  }

  /**
   * Convert 'var {a: b, c: d} = rhs' to:
   *
   * @const var temp = rhs; var b = temp.a; var d = temp.c;
   */
  private void replaceObjectPattern(
      NodeTraversal t, Node objectPattern, Node rhs, Node parent, Node nodeToDetach) {
    final Scope scope = t.getScope(); // get scope here, AST may be in temporary invalid state later
    String tempVarName = DESTRUCTURING_TEMP_VAR + (destructuringVarCounter++);
    final JSType tempVarType = objectPattern.getJSType();

    String restTempVarName = null;
    // If the last child is a rest node we will want a list of the stated properties so we can
    // exclude them from being written to the rest variable.
    ArrayList<Node> propsToDeleteForRest = null;
    if (objectPattern.hasChildren() && objectPattern.getLastChild().isRest()) {
      propsToDeleteForRest = new ArrayList<>();
      restTempVarName = DESTRUCTURING_TEMP_VAR + destructuringVarCounter++;
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
        IR.var(astFactory.createName(tempVarName, tempVarType), rhs.detach())
            .useSourceInfoIfMissingFromForTree(objectPattern);
    // TODO(tbreisacher): Remove the "if" and add this JSDoc unconditionally.
    if (parent.isConst()) {
      JSDocInfoBuilder jsDoc = new JSDocInfoBuilder(false);
      jsDoc.recordConstancy();
      tempDecl.setJSDocInfo(jsDoc.build());
    }
    nodeToDetach.getParent().addChildBefore(tempDecl, nodeToDetach);

    for (Node child = objectPattern.getFirstChild(), next; child != null; child = next) {
      next = child.getNext();

      final Node newLHS;
      final Node newRHS;
      if (child.isStringKey()) {
        // const {a: b} = obj;
        Node tempVarNameNode = astFactory.createName(tempVarName, tempVarType);
        Node getprop =
            child.isQuotedString()
                ? astFactory.createGetElem(
                    tempVarNameNode, astFactory.createString(child.getString()))
                : astFactory.createGetProp(tempVarNameNode, child.getString());

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
          Node propName = astFactory.createString(child.getString());
          if (child.isQuotedString()) {
            propName.setQuotedString();
          }
          propsToDeleteForRest.add(propName);
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
          String exprEvalTempVarName = DESTRUCTURING_TEMP_VAR + destructuringVarCounter++;
          Node exprEvalTempVarModel =
              astFactory.createName(exprEvalTempVarName, propExpr.getJSType()); // clone this node
          Node exprEvalDecl = IR.var(exprEvalTempVarModel.cloneNode(), propExpr);
          exprEvalDecl.useSourceInfoIfMissingFromForTree(child);
          nodeToDetach.getParent().addChildBefore(exprEvalDecl, nodeToDetach);
          propExpr = exprEvalTempVarModel.cloneNode();
          propsToDeleteForRest.add(exprEvalTempVarModel.cloneNode());
        }
        if (hasDefault) {
          // tempVarName[propExpr]
          Node getelem =
              astFactory.createGetElem(astFactory.createName(tempVarName, tempVarType), propExpr);

          // var tempVarName2 = tempVarName1[propExpr]
          String intermediateTempVarName = DESTRUCTURING_TEMP_VAR + (destructuringVarCounter++);
          Node intermediateDecl =
              IR.var(astFactory.createName(intermediateTempVarName, getelem.getJSType()), getelem);
          intermediateDecl.useSourceInfoIfMissingFromForTree(child);
          nodeToDetach.getParent().addChildBefore(intermediateDecl, nodeToDetach);

          // tempVarName2 === undefined ? defaultValue : tempVarName2
          newRHS =
              defaultValueHook(
                  astFactory.createName(intermediateTempVarName, getelem.getJSType()),
                  defaultValue);
        } else {
          newRHS =
              astFactory.createGetElem(
                  astFactory.createName(tempVarName, newLHS.getJSType()), propExpr);
        }
      } else if (child.isRest()) {
        if (next != null) {
          throw new IllegalStateException("object rest may not be followed by any properties");
        }
        // TODO(b/116532470): see if casting this to a more specific type fixes disambiguation
        Node assignCall = astFactory.createCall(astFactory.createQName(scope, "Object.assign"));
        assignCall.addChildToBack(astFactory.createEmptyObjectLit());
        assignCall.addChildToBack(astFactory.createName(tempVarName, tempVarType));

        Node restTempDecl = IR.var(astFactory.createName(restTempVarName, tempVarType), assignCall);
        restTempDecl.useSourceInfoIfMissingFromForTree(objectPattern);
        nodeToDetach.getParent().addChildAfter(restTempDecl, tempDecl);

        Node restName = child.getOnlyChild(); // e.g. get `rest` from `const {...rest} = {};`
        newLHS = astFactory.createName(restName.getString(), restName.getJSType());
        newRHS = objectPatternRestRHS(objectPattern, child, restTempVarName, propsToDeleteForRest);
      } else {
        throw new IllegalStateException("unexpected child");
      }

      Node newNode;
      if (NodeUtil.isNameDeclaration(parent)) {
        newNode = IR.declaration(newLHS, newRHS, parent.getToken());
      } else if (parent.isAssign()) {
        newNode = IR.exprResult(astFactory.createAssign(newLHS, newRHS));
      } else {
        throw new IllegalStateException("not reached");
      }
      newNode.useSourceInfoIfMissingFromForTree(child);

      nodeToDetach.getParent().addChildBefore(newNode, nodeToDetach);

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
    Node restTempVarModel = astFactory.createName(restTempVarName, objectPattern.getJSType());
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
    result.useSourceInfoIfMissingFromForTree(rest);
    return result;
  }

  private Node deletionNodeForRestProperty(Node restTempVarNameNode, Node property) {
    boolean useSquareBrackets = !property.isString() || property.isQuotedString();
    Node get =
        useSquareBrackets
            ? astFactory.createGetElem(restTempVarNameNode, property)
            : astFactory.createGetProp(restTempVarNameNode, property.getString());
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

    String tempVarName = DESTRUCTURING_TEMP_VAR + (destructuringVarCounter++);
    // TODO(b/77597706): delete the runtime injection after moving this pass post-typechecking
    Es6ToEs3Util.preloadEs6RuntimeFunction(compiler, "makeIterator");
    Node makeIteratorCall = astFactory.createJSCompMakeIteratorCall(rhs.detach(), t.getScope());
    Node tempVarModel = astFactory.createName(tempVarName, makeIteratorCall.getJSType());
    Node tempDecl = IR.var(tempVarModel.cloneNode(), makeIteratorCall);
    tempDecl.useSourceInfoIfMissingFromForTree(arrayPattern);
    nodeToDetach.getParent().addChildBefore(tempDecl, nodeToDetach);

    for (Node child = arrayPattern.getFirstChild(), next; child != null; child = next) {
      next = child.getNext();
      if (child.isEmpty()) {
        // Just call the next() method to advance the iterator, but throw away the value.
        Node nextCall =
            IR.exprResult(
                astFactory.createCall(astFactory.createGetProp(tempVarModel.cloneNode(), "next")));
        nextCall.useSourceInfoIfMissingFromForTree(child);
        nodeToDetach.getParent().addChildBefore(nextCall, nodeToDetach);
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
        String nextVarName = DESTRUCTURING_TEMP_VAR + (destructuringVarCounter++);
        // `temp.next().value`
        Node nextCallDotValue =
            astFactory.createGetProp(
                astFactory.createCall(astFactory.createGetProp(tempVarModel.cloneNode(), "next")),
                "value");
        JSType nextVarType = nextCallDotValue.getJSType();
        // `var temp1 = temp.next().value`
        Node var = IR.var(astFactory.createName(nextVarName, nextVarType), nextCallDotValue);
        var.useSourceInfoIfMissingFromForTree(child);
        nodeToDetach.getParent().addChildBefore(var, nodeToDetach);

        // `x`
        newLHS = child.getFirstChild().detach();
        // `(temp1 === undefined) ? defaultValue : temp1;
        newRHS =
            defaultValueHook(
                astFactory.createName(nextVarName, nextVarType), child.getLastChild().detach());
      } else if (child.isRest()) {
        //   [...x] = rhs;
        // becomes
        //   var temp = $jscomp.makeIterator(rhs);
        //   x = $jscomp.arrayFromIterator(temp);
        newLHS = child.getFirstChild().detach();
        // TODO(b/77597706): delete the runtime injection after moving this pass post-typechecking
        Es6ToEs3Util.preloadEs6RuntimeFunction(compiler, "arrayFromIterator");
        newRHS =
            astFactory.createJscompArrayFromIteratorCall(tempVarModel.cloneNode(), t.getScope());
      } else {
        // LHS is just a name (or a nested pattern).
        //   var [x] = rhs;
        // becomes
        //   var temp = $jscomp.makeIterator(rhs);
        //   var x = temp.next().value;
        newLHS = child.detach();
        newRHS =
            astFactory.createGetProp(
                astFactory.createCall(astFactory.createGetProp(tempVarModel.cloneNode(), "next")),
                "value");
      }
      Node newNode;
      if (parent.isAssign()) {
        Node assignment = astFactory.createAssign(newLHS, newRHS);
        newNode = IR.exprResult(assignment);
      } else {
        newNode = IR.declaration(newLHS, newRHS, parent.getToken());
      }
      newNode.useSourceInfoIfMissingFromForTree(arrayPattern);

      nodeToDetach.getParent().addChildBefore(newNode, nodeToDetach);
      // Explicitly visit the LHS of the new node since it may be a nested
      // destructuring pattern.
      visit(t, newLHS, newLHS.getParent());
    }

    nodeToDetach.detach();
    t.reportCodeChange();
  }

  /**
   * Convert the assignment '[x, y] = rhs' that is used as an expression and not an expr result to:
   * (() => let temp0 = rhs; var temp1 = $jscomp.makeIterator(temp0); var x = temp0.next().value;
   * var y = temp0.next().value; return temp0; }) And the assignment '{x: a, y: b} = rhs' used as an
   * expression and not an expr result to: (() => let temp0 = rhs; var temp1 = temp0; var a =
   * temp0.x; var b = temp0.y; return temp0; })
   */
  private void wrapAssignmentInCallToArrow(NodeTraversal t, Node assignment) {
    String tempVarName = DESTRUCTURING_TEMP_VAR + (destructuringVarCounter++);
    Node tempVarModel = astFactory.createName(tempVarName, assignment.getJSType());
    Node rhs = assignment.getLastChild().detach();
    // let temp0 = rhs;
    Node newAssignment = IR.let(tempVarModel.cloneNode(), rhs);
    NodeUtil.addFeatureToScript(t.getCurrentFile(), Feature.LET_DECLARATIONS);
    // [x, y] = temp0;
    Node replacementExpr =
        astFactory.createAssign(assignment.getFirstChild().detach(), tempVarModel.cloneNode());
    Node exprResult = IR.exprResult(replacementExpr);
    // return temp0;
    Node returnNode = IR.returnNode(tempVarModel.cloneNode());

    // Create a function to hold these assignments:
    Node block = IR.block(newAssignment, exprResult, returnNode);
    Node arrowFn = astFactory.createZeroArgFunction(/* name= */ "", block, assignment.getJSType());
    arrowFn.setIsArrowFunction(true);

    // Create a call to the function, and replace the pattern with the call.
    Node call = astFactory.createCall(arrowFn);
    NodeUtil.addFeatureToScript(t.getCurrentFile(), Feature.ARROW_FUNCTIONS);
    call.useSourceInfoIfMissingFromForTree(assignment);
    call.putBooleanProp(Node.FREE_CALL, true);
    assignment.getParent().replaceChild(assignment, call);
    NodeUtil.markNewScopesChanged(call, compiler);
    replacePattern(
        t,
        replacementExpr.getFirstChild(),
        replacementExpr.getLastChild(),
        replacementExpr,
        exprResult);
  }

  private void visitDestructuringPatternInEnhancedFor(Node pattern) {
    checkArgument(pattern.isDestructuringPattern());
    String tempVarName = DESTRUCTURING_TEMP_VAR + (destructuringVarCounter++);
    if (NodeUtil.isEnhancedFor(pattern.getParent())) {
      // for ([a, b, c] of arr) {
      Node forNode = pattern.getParent();
      Node block = forNode.getLastChild();
      Node decl = IR.var(astFactory.createName(tempVarName, pattern.getJSType()));
      decl.useSourceInfoIfMissingFromForTree(pattern);
      forNode.replaceChild(pattern, decl);
      Node exprResult =
          IR.exprResult(
              astFactory.createAssign(
                  pattern, astFactory.createName(tempVarName, pattern.getJSType())));
      exprResult.useSourceInfoIfMissingFromForTree(pattern);
      block.addChildToFront(exprResult);
    } else {
      // for (const [a, b, c] of arr) {
      Node destructuringLhs = pattern.getParent();
      checkState(destructuringLhs.isDestructuringLhs());
      Node declarationNode = destructuringLhs.getParent();
      Node forNode = declarationNode.getParent();
      checkState(NodeUtil.isEnhancedFor(forNode));
      Node block = forNode.getLastChild();
      declarationNode.replaceChild(
          destructuringLhs,
          astFactory.createName(tempVarName, pattern.getJSType()).useSourceInfoFrom(pattern));
      Token declarationType = declarationNode.getToken();
      Node decl =
          IR.declaration(
              pattern.detach(),
              astFactory.createName(tempVarName, pattern.getJSType()),
              declarationType);
      decl.useSourceInfoIfMissingFromForTree(pattern);
      // Move the body into an inner block to handle cases where declared variables in the for
      // loop initializer are shadowed by variables in the for loop body. e.g.
      //   for (const [value] of []) { const value = 1; }
      Node newBlock = IR.block(decl);
      block.replaceWith(newBlock);
      newBlock.addChildToBack(block);
    }
  }

  private void visitDestructuringPatternInCatch(NodeTraversal t, Node pattern) {
    String tempVarName = DESTRUCTURING_TEMP_VAR + (destructuringVarCounter++);
    Node catchBlock = pattern.getNext();

    pattern.replaceWith(astFactory.createName(tempVarName, pattern.getJSType()));
    catchBlock.addChildToFront(
        IR.declaration(
            pattern, astFactory.createName(tempVarName, pattern.getJSType()), Token.LET));
    NodeUtil.addFeatureToScript(t.getCurrentFile(), Feature.LET_DECLARATIONS);
  }

  /** Helper for transpiling DEFAULT_VALUE trees. */
  private Node defaultValueHook(Node getprop, Node defaultValue) {
    Node undefined = astFactory.createName("undefined", JSTypeNative.VOID_TYPE);
    undefined.makeNonIndexable();
    Node getpropClone = getprop.cloneTree().setJSType(getprop.getJSType());
    return astFactory.createHook(
        astFactory.createSheq(getprop, undefined), defaultValue, getpropClone);
  }
}
