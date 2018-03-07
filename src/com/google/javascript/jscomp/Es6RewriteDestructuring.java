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
import static com.google.javascript.jscomp.Es6ToEs3Util.arrayFromIterator;
import static com.google.javascript.jscomp.Es6ToEs3Util.makeIterator;

import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;

/**
 * Rewrites ES6 destructuring patterns and default parameters to valid ES3 code.
 */
public final class Es6RewriteDestructuring implements NodeTraversal.Callback, HotSwapCompilerPass {
  private final AbstractCompiler compiler;
  private static final FeatureSet transpiledFeatures =
      FeatureSet.BARE_MINIMUM.with(Feature.DEFAULT_PARAMETERS, Feature.DESTRUCTURING);

  static final String DESTRUCTURING_TEMP_VAR = "$jscomp$destructuring$var";

  private int destructuringVarCounter = 0;

  public Es6RewriteDestructuring(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, externs, transpiledFeatures, this);
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
    TranspilationPasses.markFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(compiler, scriptRoot, transpiledFeatures, this);
    TranspilationPasses.markFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case FUNCTION:
        visitFunction(t, n);
        break;
      case PARAM_LIST:
        visitParamList(n, parent);
        break;
      case FOR_OF:
        visitForOf(n);
        break;
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
        break;
      default:
        break;
    }
  }

  /**
   * If the function is an arrow function, wrap the body in a block if it is not already a block.
   */
  private void visitFunction(NodeTraversal t, Node function) {
    Node body = function.getLastChild();
    if (!body.isNormalBlock()) {
      body.detach();
      Node replacement = IR.block(IR.returnNode(body)).useSourceInfoIfMissingFromForTree(body);
      function.addChildToBack(replacement);
      t.reportCodeChange();
    }
  }

  /**
   * Processes trailing default and rest parameters.
   */
  private void visitParamList(Node paramList, Node function) {
    Node insertSpot = null;
    Node body = function.getLastChild();
    int i = 0;
    Node next = null;
    for (Node param = paramList.getFirstChild(); param != null; param = next, i++) {
      next = param.getNext();
      if (param.isDefaultValue()) {
        JSDocInfo jsDoc = param.getJSDocInfo();
        Node nameOrPattern = param.removeFirstChild();
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
                  : IR.name(getTempParameterName(function, i));
          Node lhs = nameOrPattern.cloneTree();
          Node rhs = defaultValueHook(newParam.cloneTree(), defaultValue);
          Node newStatement =
              nameOrPattern.isName() ? IR.exprResult(IR.assign(lhs, rhs)) : IR.var(lhs, rhs);
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
    Node newParam = IR.name(tempVarName);
    newParam.setJSDocInfo(patternParam.getJSDocInfo());
    patternParam.replaceWith(newParam);
    Node newDecl = IR.var(patternParam, IR.name(tempVarName));
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

  private void visitForOf(Node node) {
    Node lhs = node.getFirstChild();
    if (lhs.isDestructuringLhs()) {
      visitDestructuringPatternInEnhancedFor(lhs.getFirstChild());
    }
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
        || parent.isDefaultValue()) {
      // Nested pattern; do nothing. We will visit it after rewriting the parent.
    } else if (NodeUtil.isEnhancedFor(parent) || NodeUtil.isEnhancedFor(parent.getParent())) {
      visitDestructuringPatternInEnhancedFor(pattern);
    } else if (parent.isCatch()) {
      visitDestructuringPatternInCatch(pattern);
    } else {
      throw new IllegalStateException("unexpected parent");
    }
  }

  private void replacePattern(
      NodeTraversal t, Node pattern, Node rhs, Node parent, Node nodeToDetach) {
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
    String tempVarName = DESTRUCTURING_TEMP_VAR + (destructuringVarCounter++);
    Node tempDecl = IR.var(IR.name(tempVarName), rhs.detach())
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

      Node newLHS;
      Node newRHS;
      if (child.isStringKey()) {
        if (!child.hasChildren()) { // converting shorthand
          Node name = IR.name(child.getString());
          name.useSourceInfoIfMissingFrom(child);
          child.addChildToBack(name);
        }
        Node getprop =
            new Node(
                child.isQuotedString() ? Token.GETELEM : Token.GETPROP,
                IR.name(tempVarName),
                IR.string(child.getString()));

        Node value = child.removeFirstChild();
        if (!value.isDefaultValue()) {
          newLHS = value;
          newRHS = getprop;
        } else {
          newLHS = value.removeFirstChild();
          Node defaultValue = value.removeFirstChild();
          newRHS = defaultValueHook(getprop, defaultValue);
        }
      } else if (child.isComputedProp()) {
        if (child.getLastChild().isDefaultValue()) {
          newLHS = child.getLastChild().removeFirstChild();
          Node getelem = IR.getelem(IR.name(tempVarName), child.removeFirstChild());

          String intermediateTempVarName = DESTRUCTURING_TEMP_VAR + (destructuringVarCounter++);
          Node intermediateDecl = IR.var(IR.name(intermediateTempVarName), getelem);
          intermediateDecl.useSourceInfoIfMissingFromForTree(child);
          nodeToDetach.getParent().addChildBefore(intermediateDecl, nodeToDetach);

          newRHS =
              defaultValueHook(
                  IR.name(intermediateTempVarName), child.getLastChild().removeFirstChild());
        } else {
          newRHS = IR.getelem(IR.name(tempVarName), child.removeFirstChild());
          newLHS = child.removeFirstChild();
        }
      } else if (child.isDefaultValue()) {
        newLHS = child.removeFirstChild();
        Node defaultValue = child.removeFirstChild();
        Node getprop = IR.getprop(IR.name(tempVarName), IR.string(newLHS.getString()));
        newRHS = defaultValueHook(getprop, defaultValue);
      } else {
        throw new IllegalStateException("unexpected child");
      }

      Node newNode;
      if (NodeUtil.isNameDeclaration(parent)) {
        newNode = IR.declaration(newLHS, newRHS, parent.getToken());
      } else if (parent.isAssign()) {
        newNode = IR.exprResult(IR.assign(newLHS, newRHS));
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
   * Convert <pre>var [x, y] = rhs<pre> to:
   * <pre>
   *   var temp = $jscomp.makeIterator(rhs);
   *   var x = temp.next().value;
   *   var y = temp.next().value;
   * </pre>
   */
  private void replaceArrayPattern(
      NodeTraversal t, Node arrayPattern, Node rhs, Node parent, Node nodeToDetach) {
    String tempVarName = DESTRUCTURING_TEMP_VAR + (destructuringVarCounter++);
    Node tempDecl = IR.var(
        IR.name(tempVarName),
        makeIterator(compiler, rhs.detach()));
    tempDecl.useSourceInfoIfMissingFromForTree(arrayPattern);
    nodeToDetach.getParent().addChildBefore(tempDecl, nodeToDetach);

    for (Node child = arrayPattern.getFirstChild(), next; child != null; child = next) {
      next = child.getNext();
      if (child.isEmpty()) {
        // Just call the next() method to advance the iterator, but throw away the value.
        Node nextCall = IR.exprResult(IR.call(IR.getprop(IR.name(tempVarName), IR.string("next"))));
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
        Node var = IR.var(
            IR.name(nextVarName),
            IR.getprop(
                IR.call(IR.getprop(IR.name(tempVarName), IR.string("next"))),
                IR.string("value")));
        var.useSourceInfoIfMissingFromForTree(child);
        nodeToDetach.getParent().addChildBefore(var, nodeToDetach);

        newLHS = child.getFirstChild().detach();
        newRHS = defaultValueHook(IR.name(nextVarName), child.getLastChild().detach());
      } else if (child.isRest()) {
        //   [...x] = rhs;
        // becomes
        //   var temp = $jscomp.makeIterator(rhs);
        //   x = $jscomp.arrayFromIterator(temp);
        newLHS = child.getFirstChild().detach();
        newRHS = arrayFromIterator(compiler, IR.name(tempVarName));
      } else {
        // LHS is just a name (or a nested pattern).
        //   var [x] = rhs;
        // becomes
        //   var temp = $jscomp.makeIterator(rhs);
        //   var x = temp.next().value;
        newLHS = child.detach();
        newRHS = IR.getprop(
            IR.call(IR.getprop(IR.name(tempVarName), IR.string("next"))),
            IR.string("value"));
      }
      Node newNode;
      if (parent.isAssign()) {
        Node assignment = IR.assign(newLHS, newRHS);
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
    Node rhs = assignment.getLastChild().detach();
    Node newAssignment = IR.let(IR.name(tempVarName), rhs);
    Node replacementExpr = IR.assign(assignment.getFirstChild().detach(), IR.name(tempVarName));
    Node exprResult = IR.exprResult(replacementExpr);
    Node returnNode = IR.returnNode(IR.name(tempVarName));
    Node block = IR.block(newAssignment, exprResult, returnNode);
    Node call = IR.call(IR.arrowFunction(IR.name(""), IR.paramList(), block));
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
      Node forNode = pattern.getParent();
      Node block = forNode.getLastChild();
      Node decl = IR.var(IR.name(tempVarName));
      decl.useSourceInfoIfMissingFromForTree(pattern);
      forNode.replaceChild(pattern, decl);
      Node exprResult = IR.exprResult(IR.assign(pattern, IR.name(tempVarName)));
      exprResult.useSourceInfoIfMissingFromForTree(pattern);
      block.addChildToFront(exprResult);
    } else {
      Node destructuringLhs = pattern.getParent();
      checkState(destructuringLhs.isDestructuringLhs());
      Node declarationNode = destructuringLhs.getParent();
      Node forNode = declarationNode.getParent();
      checkState(NodeUtil.isEnhancedFor(forNode));
      Node block = forNode.getLastChild();
      declarationNode.replaceChild(
          destructuringLhs, IR.name(tempVarName).useSourceInfoFrom(pattern));
      Token declarationType = declarationNode.getToken();
      Node decl = IR.declaration(pattern.detach(), IR.name(tempVarName), declarationType);
      decl.useSourceInfoIfMissingFromForTree(pattern);
      block.addChildToFront(decl);
    }
  }

  private void visitDestructuringPatternInCatch(Node pattern) {
    String tempVarName = DESTRUCTURING_TEMP_VAR + (destructuringVarCounter++);
    Node catchBlock = pattern.getNext();

    pattern.replaceWith(IR.name(tempVarName));
    catchBlock.addChildToFront(IR.declaration(pattern, IR.name(tempVarName), Token.LET));
  }

  /**
   * Helper for transpiling DEFAULT_VALUE trees.
   */
  private static Node defaultValueHook(Node getprop, Node defaultValue) {
    return IR.hook(IR.sheq(getprop, IR.name("undefined")), defaultValue, getprop.cloneTree());
  }
}
