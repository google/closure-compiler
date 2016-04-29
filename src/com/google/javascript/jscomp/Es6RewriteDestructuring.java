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

import static com.google.javascript.jscomp.Es6ToEs3Converter.makeIterator;

import com.google.common.base.Preconditions;
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

  private static final String DESTRUCTURING_TEMP_VAR = "$jscomp$destructuring$var";

  private int destructuringVarCounter = 0;

  public Es6RewriteDestructuring(AbstractCompiler compiler) {
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

  @Override
  public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
    switch (n.getType()) {
      case Token.PARAM_LIST:
        visitParamList(n, parent);
        break;
      case Token.FOR_OF:
        visitForOf(n);
        break;
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (parent != null && parent.isDestructuringLhs()) {
      parent = parent.getParent();
    }
    switch (n.getType()) {
      case Token.ARRAY_PATTERN:
        visitArrayPattern(t, n, parent);
        break;
      case Token.OBJECT_PATTERN:
        visitObjectPattern(t, n, parent);
        break;
    }
  }

  /**
   * Processes trailing default and rest parameters.
   */
  private void visitParamList(Node paramList, Node function) {
    Node insertSpot = null;
    Node body = function.getLastChild();
    for (int i = 0; i < paramList.getChildCount(); i++) {
      Node param = paramList.getChildAtIndex(i);
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
          // smart here and treat 'void {}' for example as if it could cause
          // side effects.  Any sane person will type 'name=undefined' or
          // 'name=void 0' so this should not be an issue.
          isNoop = NodeUtil.isImmutableValue(defaultValue.getFirstChild());
        }

        if (isNoop) {
          newParam = nameOrPattern.cloneTree();
        } else {
          newParam =
              nameOrPattern.isName()
                  ? nameOrPattern
                  : IR.name(DESTRUCTURING_TEMP_VAR + (destructuringVarCounter++));
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

        compiler.reportCodeChange();
      } else if (param.isDestructuringPattern()) {
        String tempVarName;
        JSDocInfo fnJSDoc = NodeUtil.getBestJSDocInfo(function);
        if (fnJSDoc != null && fnJSDoc.getParameterNameAt(i) != null) {
          tempVarName = fnJSDoc.getParameterNameAt(i);
        } else {
          tempVarName = DESTRUCTURING_TEMP_VAR + (destructuringVarCounter++);
        }
        Preconditions.checkState(TokenStream.isJSIdentifier(tempVarName));

        Node newParam = IR.name(tempVarName);
        newParam.setJSDocInfo(param.getJSDocInfo());
        paramList.replaceChild(param, newParam);
        Node newDecl = IR.var(param, IR.name(tempVarName));
        body.addChildAfter(newDecl, insertSpot);
        insertSpot = newDecl;
        compiler.reportCodeChange();
      }
    }
  }

  private void visitForOf(Node node) {
    Node lhs = node.getFirstChild();
    if (lhs.isDestructuringLhs()) {
      visitDestructuringPatternInEnhancedFor(lhs.getFirstChild());
    }
  }

  private void visitObjectPattern(NodeTraversal t, Node objectPattern, Node parent) {
    Node rhs, nodeToDetach;
    if (NodeUtil.isNameDeclaration(parent) && !NodeUtil.isEnhancedFor(parent.getParent())) {
      rhs = objectPattern.getNext();
      nodeToDetach = parent;
    } else if (parent.isAssign() && parent.getParent().isExprResult()) {
      rhs = parent.getLastChild();
      nodeToDetach = parent.getParent();
    } else if (parent.isStringKey() || parent.isArrayPattern() || parent.isDefaultValue()) {
      // Nested object pattern; do nothing. We will visit it after rewriting the parent.
      return;
    } else if (NodeUtil.isEnhancedFor(parent) || NodeUtil.isEnhancedFor(parent.getParent())) {
      visitDestructuringPatternInEnhancedFor(objectPattern);
      return;
    } else if (parent.isCatch()) {
      visitDestructuringPatternInCatch(objectPattern);
      return;
    } else {
      throw new IllegalStateException("Unexpected OBJECT_PATTERN parent: " + parent);
    }

    // Convert 'var {a: b, c: d} = rhs' to:
    // /** @const */ var temp = rhs;
    // var b = temp.a;
    // var d = temp.c;
    String tempVarName = DESTRUCTURING_TEMP_VAR + (destructuringVarCounter++);
    Node tempDecl = IR.var(IR.name(tempVarName), rhs.detachFromParent())
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

      Node newLHS, newRHS;
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
        throw new IllegalStateException("Unexpected OBJECT_PATTERN child: " + child);
      }

      Node newNode;
      if (NodeUtil.isNameDeclaration(parent)) {
        newNode = IR.declaration(newLHS, newRHS, parent.getType());
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

    nodeToDetach.detachFromParent();
    compiler.reportCodeChange();
  }

  private void visitArrayPattern(NodeTraversal t, Node arrayPattern, Node parent) {
    Node rhs, nodeToDetach;
    if (NodeUtil.isNameDeclaration(parent) && !NodeUtil.isEnhancedFor(parent.getParent())) {
      rhs = arrayPattern.getNext();
      nodeToDetach = parent;
    } else if (parent.isAssign()) {
      rhs = arrayPattern.getNext();
      nodeToDetach = parent.getParent();
      Preconditions.checkState(nodeToDetach.isExprResult());
    } else if (parent.isArrayPattern() || parent.isDefaultValue() || parent.isStringKey()) {
      // This is a nested array pattern. Don't do anything now; we'll visit it
      // after visiting the parent.
      return;
    } else if (NodeUtil.isEnhancedFor(parent) || NodeUtil.isEnhancedFor(parent.getParent())) {
      visitDestructuringPatternInEnhancedFor(arrayPattern);
      return;
    } else if (parent.isCatch()) {
      visitDestructuringPatternInCatch(arrayPattern);
      return;
    } else {
      throw new IllegalStateException("Unexpected ARRAY_PATTERN parent: " + parent);
    }

    // Convert 'var [x, y] = rhs' to:
    // var temp = $jscomp.makeIterator(rhs);
    // var x = temp.next().value;
    // var y = temp.next().value;
    String tempVarName = DESTRUCTURING_TEMP_VAR + (destructuringVarCounter++);
    Node tempDecl = IR.var(
        IR.name(tempVarName),
        makeIterator(compiler, rhs.detachFromParent()));
    tempDecl.useSourceInfoIfMissingFromForTree(arrayPattern);
    nodeToDetach.getParent().addChildBefore(tempDecl, nodeToDetach);
    boolean needsRuntime = false;

    for (Node child = arrayPattern.getFirstChild(), next; child != null; child = next) {
      next = child.getNext();
      if (child.isEmpty()) {
        // Just call the next() method to advance the iterator, but throw away the value.
        Node nextCall = IR.exprResult(IR.call(IR.getprop(IR.name(tempVarName), IR.string("next"))));
        nextCall.useSourceInfoIfMissingFromForTree(child);
        nodeToDetach.getParent().addChildBefore(nextCall, nodeToDetach);
        continue;
      }

      Node newLHS, newRHS;
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

        newLHS = child.getFirstChild().detachFromParent();
        newRHS = defaultValueHook(IR.name(nextVarName), child.getLastChild().detachFromParent());
      } else if (child.isRest()) {
        //   [...x] = rhs;
        // becomes
        //   var temp = $jscomp.makeIterator(rhs);
        //   x = $jscomp.arrayFromIterator(temp);
        newLHS = child.getFirstChild().detachFromParent();
        newRHS =
            IR.call(
                NodeUtil.newQName(compiler, "$jscomp.arrayFromIterator"),
                IR.name(tempVarName));
        needsRuntime = true;
      } else {
        // LHS is just a name (or a nested pattern).
        //   var [x] = rhs;
        // becomes
        //   var temp = $jscomp.makeIterator(rhs);
        //   var x = temp.next().value;
        newLHS = child.detachFromParent();
        newRHS = IR.getprop(
            IR.call(IR.getprop(IR.name(tempVarName), IR.string("next"))),
            IR.string("value"));
      }
      Node newNode;
      if (parent.isAssign()) {
        Node assignment = IR.assign(newLHS, newRHS);
        newNode = IR.exprResult(assignment);
      } else {
        newNode = IR.declaration(newLHS, newRHS, parent.getType());
      }
      newNode.useSourceInfoIfMissingFromForTree(arrayPattern);

      nodeToDetach.getParent().addChildBefore(newNode, nodeToDetach);
      // Explicitly visit the LHS of the new node since it may be a nested
      // destructuring pattern.
      visit(t, newLHS, newLHS.getParent());
    }
    nodeToDetach.detachFromParent();

    if (needsRuntime) {
      compiler.ensureLibraryInjected("es6_runtime", false);
    }
    compiler.reportCodeChange();
  }

  private void visitDestructuringPatternInEnhancedFor(Node pattern) {
    Preconditions.checkArgument(pattern.isDestructuringPattern());
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
      Preconditions.checkState(destructuringLhs.isDestructuringLhs());
      Node declarationNode = destructuringLhs.getParent();
      Node forNode = declarationNode.getParent();
      Preconditions.checkState(NodeUtil.isEnhancedFor(forNode));
      Node block = forNode.getLastChild();
      declarationNode.replaceChild(
          destructuringLhs, IR.name(tempVarName).useSourceInfoFrom(pattern));
      int declarationType = declarationNode.getType();
      Node decl = IR.declaration(pattern.detachFromParent(), IR.name(tempVarName), declarationType);
      decl.useSourceInfoIfMissingFromForTree(pattern);
      block.addChildToFront(decl);
    }
  }

  private void visitDestructuringPatternInCatch(Node pattern) {
    String tempVarName = DESTRUCTURING_TEMP_VAR + (destructuringVarCounter++);
    Node catchBlock = pattern.getNext();

    pattern.getParent().replaceChild(pattern, IR.name(tempVarName));
    catchBlock.addChildToFront(IR.declaration(pattern, IR.name(tempVarName), Token.LET));
  }

  /**
   * Helper for transpiling DEFAULT_VALUE trees.
   */
  private static Node defaultValueHook(Node getprop, Node defaultValue) {
    return IR.hook(IR.sheq(getprop, IR.name("undefined")), defaultValue, getprop.cloneTree());
  }
}
