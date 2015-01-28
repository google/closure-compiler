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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
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

/**
 * Converts ES6 code to valid ES3 code.
 *
 * @author tbreisacher@google.com (Tyler Breisacher)
 */
public class Es6ToEs3Converter implements NodeTraversal.Callback, HotSwapCompilerPass {
  private final AbstractCompiler compiler;

  static final DiagnosticType CANNOT_CONVERT = DiagnosticType.error(
      "JSC_CANNOT_CONVERT",
      "This code cannot be converted from ES6. {0}");

  // TODO(tbreisacher): Remove this once all ES6 features are transpilable.
  static final DiagnosticType CANNOT_CONVERT_YET = DiagnosticType.error(
      "JSC_CANNOT_CONVERT_YET",
      "ES6-to-ES3 conversion of ''{0}'' is not yet implemented.");

  static final DiagnosticType DYNAMIC_EXTENDS_TYPE = DiagnosticType.error(
      "JSC_DYNAMIC_EXTENDS_TYPE",
      "The class in an extends clause must be a qualified name.");

  static final DiagnosticType CLASS_REASSIGNMENT = DiagnosticType.error(
      "CLASS_REASSIGNMENT",
      "Class names defined inside a function cannot be reassigned.");

  // The name of the vars that capture 'this' and 'arguments'
  // for converting arrow functions.
  private static final String THIS_VAR = "$jscomp$this";
  private static final String ARGUMENTS_VAR = "$jscomp$arguments";

  private static final String FRESH_SPREAD_VAR = "$jscomp$spread$args";

  private static final String DESTRUCTURING_TEMP_VAR = "$jscomp$destructuring$var";

  private int destructuringVarCounter = 0;

  private static final String FRESH_COMP_PROP_VAR = "$jscomp$compprop";

  private static final String ITER_BASE = "$jscomp$iter$";

  private static final String ITER_RESULT = "$jscomp$key$";

  // These functions are defined in js/es6_runtime.js
  public static final String COPY_PROP = "$jscomp.copyProperties";
  private static final String INHERITS = "$jscomp.inherits";
  static final String MAKE_ITER = "$jscomp.makeIterator";

  public Es6ToEs3Converter(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }

  /**
   * Some nodes (such as arrow functions) must be visited pre-order in order to rewrite the
   * references to {@code this} correctly.
   * Everything else is translated post-order in {@link #visit}.
   */
  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.FUNCTION:
        if (n.isArrowFunction()) {
          visitArrowFunction(t, n);
        }
        break;
      case Token.PARAM_LIST:
        visitParamList(n, parent);
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
      case Token.OBJECTLIT:
        for (Node child : n.children()) {
          if (child.isComputedProp()) {
            visitObjectWithComputedProperty(n, parent);
            break;
          }
        }
        break;
      case Token.MEMBER_DEF:
        if (parent.isObjectLit()) {
          visitMemberDefInObjectLit(n, parent);
        }
        break;
      case Token.FOR_OF:
        visitForOf(n, parent);
        break;
      case Token.STRING_KEY:
        visitStringKey(n);
        break;
      case Token.CLASS:
        for (Node member = n.getLastChild().getFirstChild();
            member != null;
            member = member.getNext()) {
          if (member.isGetterDef() || member.isSetterDef()
              || member.getBooleanProp(Node.COMPUTED_PROP_GETTER)
              || member.getBooleanProp(Node.COMPUTED_PROP_SETTER)) {
            cannotConvert(member, "getters or setters in class definitions");
            return;
          }
        }
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
      case Token.TEMPLATELIT:
        Es6TemplateLiterals.visitTemplateLiteral(t, n);
        break;
      case Token.ARRAY_PATTERN:
        visitArrayPattern(t, n, parent);
        break;
      case Token.OBJECT_PATTERN:
        visitObjectPattern(t, n, parent);
        break;
    }
  }

  private void visitObjectPattern(NodeTraversal t, Node objectPattern, Node parent) {
    Node rhs, nodeToDetach;
    if (NodeUtil.isNameDeclaration(parent) && !NodeUtil.isEnhancedFor(parent.getParent())) {
      rhs = objectPattern.getLastChild();
      nodeToDetach = parent;
    } else if (parent.isAssign() && parent.getParent().isExprResult()) {
      rhs = parent.getLastChild();
      nodeToDetach = parent.getParent();
    } else if (parent.isStringKey() || parent.isArrayPattern()
        || parent.isDefaultValue()) {
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
    // var temp = rhs;
    // var b = temp.a;
    // var d = temp.c;
    String tempVarName = DESTRUCTURING_TEMP_VAR + (destructuringVarCounter++);
    Node tempDecl = IR.var(IR.name(tempVarName), rhs.detachFromParent())
        .useSourceInfoIfMissingFromForTree(objectPattern);
    nodeToDetach.getParent().addChildBefore(tempDecl, nodeToDetach);

    for (Node child = objectPattern.getFirstChild(), next;
        child != null;
        child = next) {
      next = child.getNext();

      Node newLHS, newRHS;
      if (child.isStringKey()) {
        Preconditions.checkState(child.hasChildren());
        Node getprop = new Node(child.isQuotedString() ? Token.GETELEM : Token.GETPROP,
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
          Node getelem = IR.getelem(
              IR.name(tempVarName),
              child.removeFirstChild());

          String intermediateTempVarName = DESTRUCTURING_TEMP_VAR + (destructuringVarCounter++);
          Node intermediateDecl = IR.var(IR.name(intermediateTempVarName), getelem);
          intermediateDecl.useSourceInfoIfMissingFromForTree(child);
          nodeToDetach.getParent().addChildBefore(intermediateDecl, nodeToDetach);

          newRHS = defaultValueHook(
              IR.name(intermediateTempVarName),
              child.getLastChild().removeFirstChild());
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
      // The array pattern is the only child, because Es6SplitVariableDeclarations
      // has already run.
      Preconditions.checkState(arrayPattern.getNext() == null);
      rhs = arrayPattern.getLastChild();
      nodeToDetach = parent;
    } else if (parent.isAssign()) {
      rhs = arrayPattern.getNext();
      nodeToDetach = parent.getParent();
      Preconditions.checkState(nodeToDetach.isExprResult());
    } else if (parent.isArrayPattern() || parent.isDefaultValue()
        || parent.isStringKey()) {
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
    // var temp = rhs;
    // var x = temp[0];
    // var y = temp[1];
    String tempVarName = DESTRUCTURING_TEMP_VAR + (destructuringVarCounter++);
    Node tempDecl = IR.var(IR.name(tempVarName), rhs.detachFromParent())
        .useSourceInfoIfMissingFromForTree(arrayPattern);
    nodeToDetach.getParent().addChildBefore(tempDecl, nodeToDetach);

    int i = 0;
    for (Node child = arrayPattern.getFirstChild(), next;
        child != null;
        child = next, i++) {
      next = child.getNext();
      if (child.isEmpty()) {
        continue;
      }

      Node newLHS, newRHS;
      if (child.isDefaultValue()) {
        Node getElem = IR.getelem(IR.name(tempVarName), IR.number(i));
        //   [x = defaultValue] = rhs;
        // becomes
        //   var temp = rhs;
        //   x = (temp[0] === undefined) ? defaultValue : temp[0];
        newLHS = child.getFirstChild().detachFromParent();
        newRHS = defaultValueHook(getElem, child.getLastChild().detachFromParent());
      } else if (child.isRest()) {
        newLHS = child.detachFromParent();
        newLHS.setType(Token.NAME);
        // [].slice.call(temp, i)
        newRHS = IR.call(
            IR.getprop(IR.getprop(IR.arraylit(), IR.string("slice")), IR.string("call")),
            IR.name(tempVarName), IR.number(i));
      } else {
        newLHS = child.detachFromParent();
        newRHS = IR.getelem(IR.name(tempVarName), IR.number(i));
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
    compiler.reportCodeChange();
  }

  private void visitDestructuringPatternInCatch(Node pattern) {
    String tempVarName = DESTRUCTURING_TEMP_VAR + (destructuringVarCounter++);
    Node catchBlock = pattern.getNext();

    pattern.getParent().replaceChild(pattern, IR.name(tempVarName));
    catchBlock.addChildToFront(
        IR.declaration(pattern, IR.name(tempVarName), Token.LET));
  }

  private void visitDestructuringPatternInEnhancedFor(Node pattern) {
    Node forNode;
    int declarationType;
    if (NodeUtil.isEnhancedFor(pattern.getParent())) {
      forNode = pattern.getParent();
      declarationType = Token.ASSIGN;
    } else {
      forNode = pattern.getParent().getParent();
      declarationType = pattern.getParent().getType();
      Preconditions.checkState(NodeUtil.isEnhancedFor(forNode));
    }

    String tempVarName = DESTRUCTURING_TEMP_VAR + (destructuringVarCounter++);
    Node block = forNode.getLastChild();
    if (declarationType == Token.ASSIGN) {
      pattern.getParent().replaceChild(pattern,
          IR.declaration(IR.name(tempVarName), Token.LET));
      block.addChildToFront(
          IR.exprResult(IR.assign(pattern, IR.name(tempVarName))));
    } else {
      pattern.getParent().replaceChild(pattern, IR.name(tempVarName));
      block.addChildToFront(
          IR.declaration(pattern, IR.name(tempVarName), declarationType));
    }
  }

  /**
   * Converts a member definition in an object literal to an ES3 key/value pair.
   * Member definitions in classes are handled in {@link #visitClass}.
   */
  private void visitMemberDefInObjectLit(Node n, Node parent) {
    String name = n.getString();
    Node stringKey = IR.stringKey(name, n.getFirstChild().detachFromParent());
    parent.replaceChild(n, stringKey);
    compiler.reportCodeChange();
  }

  /**
   * Converts extended object literal {a} to {a:a}.
   */
  private void visitStringKey(Node n) {
    if (!n.hasChildren()) {
      Node name = IR.name(n.getString());
      name.copyInformationFrom(n);
      n.addChildToBack(name);
      compiler.reportCodeChange();
    }
  }

  private void visitForOf(Node node, Node parent) {
    Node variable = node.removeFirstChild();
    Node iterable = node.removeFirstChild();
    Node body = node.removeFirstChild();

    Node iterName = IR.name(ITER_BASE + compiler.getUniqueNameIdSupplier().get());
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

    Node makeIter = IR.call(
        NodeUtil.newQName(
            compiler, MAKE_ITER),
        iterable);
    compiler.needsEs6Runtime = true;

    Node init = IR.var(iterName.cloneTree(), makeIter);
    Node initIterResult = iterResult.cloneTree();
    initIterResult.addChildToFront(getNext.cloneTree());
    init.addChildToBack(initIterResult);

    Node cond = IR.not(IR.getprop(iterResult.cloneTree(), IR.string("done")));
    Node incr = IR.assign(iterResult.cloneTree(), getNext.cloneTree());

    Node declarationOrAssign;
    if (declType == Token.NAME) {
      declarationOrAssign = IR.exprResult(IR.assign(
          IR.name(variableName),
          IR.getprop(iterResult.cloneTree(), IR.string("value"))));
    } else {
      declarationOrAssign = new Node(declType, IR.name(variableName));
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
    Node name = NodeUtil.getClassNameNode(clazz);
    Node enclosingFunction = NodeUtil.getEnclosingFunction(clazz);
    if (enclosingFunction == null) {
      return;
    }
    CheckClassAssignments checkAssigns = new CheckClassAssignments(name);
    NodeTraversal.traverse(compiler, enclosingFunction, checkAssigns);
  }

  /**
   * Processes trailing default and rest parameters.
   */
  private void visitParamList(Node paramList, Node function) {
    Node insertSpot = null;
    Node block = function.getLastChild();
    for (int i = 0; i < paramList.getChildCount(); i++) {
      Node param = paramList.getChildAtIndex(i);
      if (param.isDefaultValue()) {
        Node nameOrPattern = param.removeFirstChild();
        Node defaultValue = param.removeFirstChild();
        Node newParam = nameOrPattern.isName() ? nameOrPattern
            : IR.name(DESTRUCTURING_TEMP_VAR + (destructuringVarCounter++));

        Node lhs = nameOrPattern.cloneTree();
        Node rhs = defaultValueHook(newParam.cloneTree(), defaultValue);
        Node newStatement = nameOrPattern.isName()
            ? IR.exprResult(IR.assign(lhs, rhs))
            : IR.var(lhs, rhs);
        newStatement.useSourceInfoIfMissingFromForTree(param);
        block.addChildAfter(newStatement, insertSpot);
        insertSpot = newStatement;

        paramList.replaceChild(param, newParam);
        newParam.setOptionalArg(true);

        compiler.reportCodeChange();
      } else if (param.isRest()) { // rest parameter
        param.setType(Token.NAME);
        param.setVarArgs(true);
        // Transpile to: param = [].slice.call(arguments, i);
        Node newArr = IR.exprResult(IR.assign(IR.name(param.getString()),
            IR.call(IR.getprop(IR.getprop(IR.arraylit(), IR.string("slice")),
                IR.string("call")), IR.name("arguments"), IR.number(i))));
        block.addChildAfter(newArr.useSourceInfoIfMissingFromForTree(param), insertSpot);
        compiler.reportCodeChange();
      } else if (param.isDestructuringPattern()) {
        String tempVarName = DESTRUCTURING_TEMP_VAR + (destructuringVarCounter++);
        paramList.replaceChild(param, IR.name(tempVarName));
        Node newDecl = IR.var(param, IR.name(tempVarName));
        block.addChildAfter(newDecl, insertSpot);
        insertSpot = newDecl;
      }
    }
    // For now, we are running transpilation before type-checking, so we'll
    // need to make sure changes don't invalidate the JSDoc annotations.
    // Therefore we keep the parameter list the same length and only initialize
    // the values if they are set to undefined.
  }

  /**
   * Processes array literals or calls containing spreads.
   * Eg.: [1, 2, ...x, 4, 5] => [1, 2].concat(x, [4, 5]);
   * Eg.: f(...arr) => f.apply(null, arr)
   * Eg.: new F(...args) => new Function.prototype.bind.apply(F, [].concat(args))
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
        groups.add(currElement.removeFirstChild());
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
    Node joinedGroups = IR.call(IR.getprop(IR.arraylit(), IR.string("concat")),
            groups.toArray(new Node[groups.size()]));
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
      Node bindApply = NodeUtil.newQName(compiler,
          "Function.prototype.bind.apply");
      result = IR.newNode(bindApply, callee, joinedGroups);
    }
    result.useSourceInfoIfMissingFromForTree(node);
    parent.replaceChild(node, result);
    compiler.reportCodeChange();
  }

  private void visitObjectWithComputedProperty(Node obj, Node parent) {
    Preconditions.checkArgument(obj.isObjectLit());
    List<Node> props = new ArrayList<>();
    Node currElement = obj.getFirstChild();

    while (currElement != null) {
      if (currElement.getBooleanProp(Node.COMPUTED_PROP_GETTER)
          || currElement.getBooleanProp(Node.COMPUTED_PROP_SETTER)) {
        cannotConvertYet(currElement, "computed getter/setter");
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
          Node name = IR.name(propdef.getString()).copyInformationFrom(propdef);
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
    parent.replaceChild(obj, result);

    Node var = IR.var(IR.name(objName), obj);
    var.useSourceInfoIfMissingFromForTree(statement);
    statement.getParent().addChildBefore(var, statement);
    compiler.reportCodeChange();
  }

  /**
   * Classes are processed in 3 phases:
   *   1) The class name is extracted.
   *   2) Class members are processed and rewritten.
   *   3) The constructor is built.
   */
  private void visitClass(Node classNode, Node parent) {
    checkClassReassignment(classNode);
    // Collect Metadata
    Node className = classNode.getFirstChild();
    Node superClassName = className.getNext();
    Node classMembers = classNode.getLastChild();

    // This is a statement node. We insert methods of the
    // transpiled class after this node.
    Node insertionPoint;

    if (!superClassName.isEmpty() && !superClassName.isQualifiedName()) {
      compiler.report(JSError.make(superClassName, DYNAMIC_EXTENDS_TYPE));
      return;
    }

    // The fully qualified name of the class, which will be used in the output.
    // May come from the class itself or the LHS of an assignment.
    String fullClassName = null;

    // Whether the constructor function in the output should be anonymous.
    boolean anonymous;

    // If this is a class statement, or a class expression in a simple
    // assignment or var statement, convert it. In any other case, the
    // code is too dynamic, so just call cannotConvert.
    if (NodeUtil.isStatement(classNode)) {
      fullClassName = className.getString();
      anonymous = false;
      insertionPoint = classNode;
    } else if (parent.isAssign() && parent.getParent().isExprResult()) {
      // Add members after the EXPR_RESULT node:
      // example.C = class {}; example.C.prototype.foo = function() {};
      fullClassName = parent.getFirstChild().getQualifiedName();
      if (fullClassName == null) {
        cannotConvert(parent, "Can only convert classes that are declarations or the right hand"
            + " side of a simple assignment.");
        return;
      }
      anonymous = true;
      insertionPoint = parent.getParent();
    } else if (parent.isName()) {
      // Add members after the 'var' statement.
      // var C = class {}; C.prototype.foo = function() {};
      fullClassName =  parent.getString();
      anonymous = true;
      insertionPoint = parent.getParent();
    } else {
      cannotConvert(parent, "Can only convert classes that are declarations or the right hand"
            + " side of a simple assignment.");
      return;
    }

    boolean useUnique = NodeUtil.isStatement(classNode) && !NodeUtil.isInFunction(classNode);
    String uniqueFullClassName = useUnique ? getUniqueClassName(fullClassName) : fullClassName;
    String superClassString = superClassName.getQualifiedName();

    Verify.verify(NodeUtil.isStatement(insertionPoint));

    Node constructor = null;
    JSDocInfo ctorJSDocInfo = null;
    // Process all members of the class
    for (Node member : classMembers.children()) {
      if (member.isEmpty()) {
        continue;
      }

      if (member.isMemberDef() && member.getString().equals("constructor")) {
        ctorJSDocInfo = member.getJSDocInfo();
        constructor = member.getFirstChild().detachFromParent();
        if (!anonymous) {
          constructor.replaceChild(
              constructor.getFirstChild(), className.cloneNode());
        }
      } else {
        Node qualifiedMemberName;
        Node method;
        if (member.isMemberDef()) {
          if (member.isStaticMember()) {
            qualifiedMemberName = NodeUtil.newQName(
                compiler,
                Joiner.on(".").join(
                    uniqueFullClassName,
                    member.getString()));
          } else {
            qualifiedMemberName = NodeUtil.newQName(
                compiler,
                Joiner.on(".").join(
                    uniqueFullClassName,
                    "prototype",
                    member.getString()));
          }
          method = member.getFirstChild().detachFromParent();
        } else if (member.isComputedProp()) {
          if (member.isStaticMember()) {
            qualifiedMemberName = IR.getelem(
                NodeUtil.newQName(
                    compiler,
                    uniqueFullClassName),
                member.removeFirstChild());
          } else {
            qualifiedMemberName = IR.getelem(
                NodeUtil.newQName(
                    compiler,
                    Joiner.on('.').join(uniqueFullClassName, "prototype")),
                member.removeFirstChild());
          }
          method = member.getLastChild().detachFromParent();
        } else {
          throw new IllegalStateException("Unexpected class member: " + member);
        }
        Node assign = IR.assign(qualifiedMemberName, method);
        assign.useSourceInfoIfMissingFromForTree(member);

        JSDocInfo info = member.getJSDocInfo();
        if (member.isStaticMember() && NodeUtil.referencesThis(assign.getLastChild())) {
          JSDocInfoBuilder memberDoc;
          if (info == null) {
            memberDoc = new JSDocInfoBuilder(true);
          } else {
            memberDoc = JSDocInfoBuilder.copyFrom(info);
          }
          memberDoc.recordThisType(
              new JSTypeExpression(new Node(Token.BANG, new Node(Token.QMARK)),
              member.getSourceFileName()));
          info = memberDoc.build(assign);
        }
        if (info != null) {
          info.setAssociatedNode(assign);
          assign.setJSDocInfo(info);
        }

        Node newNode = NodeUtil.newExpr(assign);
        insertionPoint.getParent().addChildAfter(newNode, insertionPoint);
        insertionPoint = newNode;
      }
    }

    Preconditions.checkNotNull(constructor);
    JSDocInfo classJSDoc = classNode.getJSDocInfo();
    JSDocInfoBuilder newInfo = (classJSDoc != null)
        ? JSDocInfoBuilder.copyFrom(classJSDoc)
        : new JSDocInfoBuilder(true);

    newInfo.recordConstructor();
    if (!superClassName.isEmpty()) {

      if (newInfo.isInterfaceRecorded()) {
        newInfo.recordExtendedInterface(new JSTypeExpression(new Node(Token.BANG,
            IR.string(superClassString)),
            superClassName.getSourceFileName()));
      } else {
        Node inherits = IR.call(
            NodeUtil.newQName(compiler, INHERITS),
            NodeUtil.newQName(compiler, fullClassName),
            NodeUtil.newQName(compiler, superClassString));
        Node inheritsCall = IR.exprResult(inherits);
        compiler.needsEs6Runtime = true;

        inheritsCall.useSourceInfoIfMissingFromForTree(classNode);
        Node enclosingStatement = NodeUtil.getEnclosingStatement(classNode);
        enclosingStatement.getParent().addChildAfter(inheritsCall, enclosingStatement);
        newInfo.recordBaseType(new JSTypeExpression(new Node(Token.BANG,
            IR.string(superClassString)),
            superClassName.getSourceFileName()));

        Node copyProps = IR.call(
            NodeUtil.newQName(compiler, COPY_PROP),
            NodeUtil.newQName(compiler, fullClassName),
            NodeUtil.newQName(compiler, superClassString));
        compiler.needsEs6Runtime = true;

        copyProps.useSourceInfoIfMissingFromForTree(classNode);
        enclosingStatement.getParent().addChildAfter(
            IR.exprResult(copyProps).srcref(classNode), enclosingStatement);
      }
    }

    // Classes are @struct by default.
    if (!newInfo.isUnrestrictedRecorded() && !newInfo.isDictRecorded() &&
        !newInfo.isStructRecorded()) {
      newInfo.recordStruct();
    }

    if (ctorJSDocInfo != null) {
      newInfo.recordSuppressions(ctorJSDocInfo.getSuppressions());
      for (String param : ctorJSDocInfo.getParameterNames()) {
        newInfo.recordParameter(param, ctorJSDocInfo.getParameterType(param));
      }
    }
    insertionPoint = constructor;

    if (NodeUtil.isStatement(classNode)) {
      constructor.getFirstChild().setString("");
      Node ctorVar = IR.var(IR.name(fullClassName), constructor);
      ctorVar.useSourceInfoIfMissingFromForTree(classNode);
      parent.replaceChild(classNode, ctorVar);
    } else {
      parent.replaceChild(classNode, constructor);
    }

    if (NodeUtil.isStatement(constructor)) {
      insertionPoint.setJSDocInfo(newInfo.build(insertionPoint));
    } else if (parent.isName()) {
      // The constructor function is the RHS of a var statement.
      // Add the JSDoc to the VAR node.
      Node var = parent.getParent();
      var.setJSDocInfo(newInfo.build(var));
    } else if (constructor.getParent().isName()) {
      // Is a newly created VAR node.
      Node var = constructor.getParent().getParent();
      var.setJSDocInfo(newInfo.build(var));
    } else if (parent.isAssign()) {
      // The constructor function is the RHS of an assignment.
      // Add the JSDoc to the ASSIGN node.
      parent.setJSDocInfo(newInfo.build(parent));
    } else {
      throw new IllegalStateException("Unexpected parent node " + parent);
    }

    compiler.reportCodeChange();
  }

  /**
   * Converts ES6 arrow functions to standard anonymous ES3 functions.
   */
  private void visitArrowFunction(NodeTraversal t, Node n) {
    n.setIsArrowFunction(false);
    Node body = n.getLastChild();
    if (!body.isBlock()) {
      body.detachFromParent();
      body = IR.block(IR.returnNode(body).srcref(body)).srcref(body);
      n.addChildToBack(body);
    }

    UpdateThisAndArgumentsReferences updater =
        new UpdateThisAndArgumentsReferences();
    NodeTraversal.traverse(compiler, body, updater);
    addVarDecls(t, updater.changedThis, updater.changedArguments);

    compiler.reportCodeChange();
  }

  private void addVarDecls(
      NodeTraversal t, boolean addThis, boolean addArguments) {
    Scope scope = t.getScope();
    if (scope.isDeclared(THIS_VAR, false)) {
      addThis = false;
    }
    if (scope.isDeclared(ARGUMENTS_VAR, false)) {
      addArguments = false;
    }

    Node parent = t.getScopeRoot();
    if (parent.isFunction()) {
      // Add the new node at the beginning of the function body.
      parent = parent.getLastChild();
    }
    if (parent.isSyntheticBlock() && parent.getFirstChild().isScript()) {
      // Add the new node inside the SCRIPT node instead of the
      // synthetic block that contains it.
      parent = parent.getFirstChild();
    }

    CompilerInput input = compiler.getInput(parent.getInputId());
    if (addArguments) {
      Node name = IR.name(ARGUMENTS_VAR).srcref(parent);
      Node argumentsVar = IR.var(name, IR.name("arguments").srcref(parent));
      argumentsVar.srcref(parent);
      parent.addChildToFront(argumentsVar);
      scope.declare(ARGUMENTS_VAR, name, null, input);
    }
    if (addThis) {
      Node name = IR.name(THIS_VAR).srcref(parent);
      Node thisVar = IR.var(name, IR.thisNode().srcref(parent));
      thisVar.srcref(parent);
      parent.addChildToFront(thisVar);
      scope.declare(THIS_VAR, name, null, input);
    }
  }

  static String getUniqueClassName(String qualifiedName) {
    return qualifiedName;
  }

  /**
   * Helper for transpiling DEFAULT_VALUE trees.
   */
  private static Node defaultValueHook(Node getprop, Node defaultValue) {
    return IR.hook(
        IR.sheq(getprop, IR.name("undefined")),
        defaultValue,
        getprop.cloneTree());
  }

  private static class UpdateThisAndArgumentsReferences
      implements NodeTraversal.Callback {
    private boolean changedThis = false;
    private boolean changedArguments = false;

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isThis()) {
        Node name = IR.name(THIS_VAR).srcref(n);
        parent.replaceChild(n, name);
        changedThis = true;
      } else if (n.isName() && n.getString().equals("arguments")) {
        Node name = IR.name(ARGUMENTS_VAR).srcref(n);
        parent.replaceChild(n, name);
        changedArguments = true;
      }
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return !n.isFunction() || n.isArrowFunction();
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
}
