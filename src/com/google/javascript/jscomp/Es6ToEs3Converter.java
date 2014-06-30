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
      "This code cannot be converted from ES6 to ES3. {0}");

  // TODO(tbreisacher): Remove this once all ES6 features are transpilable.
  static final DiagnosticType CANNOT_CONVERT_YET = DiagnosticType.error(
      "JSC_CANNOT_CONVERT_YET",
      "ES6-to-ES3 conversion of ''{0}'' is not yet implemented.");

  static final DiagnosticType DYNAMIC_EXTENDS_TYPE = DiagnosticType.error(
      "JSC_DYNAMIC_EXTENDS_TYPE",
      "The class in an extends clause must be a qualified name.");

  static final DiagnosticType NO_SUPERTYPE = DiagnosticType.error(
      "JSC_NO_SUPERTYPE",
      "The super keyword may only appear in classes with an extends clause.");

  static final DiagnosticType CLASS_REASSIGNMENT = DiagnosticType.error(
      "CLASS_REASSIGNMENT",
      "Class names defined inside a function cannot be reassigned.");

  // The name of the var that captures 'this' for converting arrow functions.
  private static final String THIS_VAR = "$jscomp$this";

  private static final String FRESH_SPREAD_VAR = "$jscomp$spread$args";

  private int freshSpreadVarCounter = 0;

  private static final String FRESH_COMP_PROP_VAR = "$jscomp$compprop";

  private int freshPropVarCounter = 0;

  // The name of the property-copying function, defined in runtime_lib.js
  public static final String COPY_PROP = "$jscomp$copy$properties";

  private static final String INHERITS = "$jscomp$inherits";

  private static final String ITER_BASE = "$jscomp$iter$";

  private int iterCounter = 0;

  private static final String ITER_RESULT = "$jscomp$key$";

  private static final String MAKE_ITER = "$jscomp$make$iterator";

  public Es6ToEs3Converter(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
    new Es6RewriteLetConst(compiler).process(externs, root);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
    new Es6RewriteLetConst(compiler).hotSwapScript(scriptRoot, originalRoot);
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
      case Token.THIS:
        visitThis(n, parent);
        break;
      case Token.CLASS:
        // Need to check for super references before they get rewritten.
        checkClassSuperReferences(n);
        break;
      case Token.ARRAY_COMP:
      case Token.ARRAY_PATTERN:
      case Token.OBJECT_PATTERN:
        cannotConvertYet(n, Token.name(n.getType()));
        // Don't bother visiting the children of a node if we
        // already know we can't convert the node itself.
        return false;
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
      case Token.SUPER:
        visitSuper(n);
        break;
      case Token.STRING_KEY:
        visitStringKey(n);
        break;
      case Token.CLASS:
        visitClass(n, parent);
        break;
      case Token.PARAM_LIST:
        visitParamList(n, parent);
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
        visitTemplateLiteral(t, n);
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

  private void visitThis(Node node, Node parent) {
    Node enclosingMemberDef = NodeUtil.getEnclosingClassMember(node);
    if (enclosingMemberDef == null) {
      return;
    }
    Node enclosingClass = NodeUtil.getEnclosingClass(node);
    if (enclosingMemberDef.isStaticMember()) {
      parent.replaceChild(node,
          IR.name(NodeUtil.getClassName(enclosingClass)).srcref(node));
      compiler.reportCodeChange();
    }
  }

  private void visitForOf(Node node, Node parent) {
    Node variable = node.removeFirstChild();
    Node iterable = node.removeFirstChild();
    Node body = node.removeFirstChild();

    Node iterName = IR.name(ITER_BASE + (iterCounter++));
    Node getNext = IR.call(IR.getprop(iterName.cloneTree(), IR.string("next")));
    String variableName = variable.isName() ? variable.getQualifiedName()
        : variable.getFirstChild().getQualifiedName(); // var or let
    Node iterResult = IR.name(ITER_RESULT + variableName);

    Node makeIter = IR.call(IR.name(MAKE_ITER), iterable);
    makeIter.putBooleanProp(Node.FREE_CALL, true);
    Node init = IR.var(iterName.cloneTree(), makeIter);
    Node initIterResult = iterResult.cloneTree();
    initIterResult.addChildToFront(getNext.cloneTree());
    init.addChildToBack(initIterResult);

    Node cond = IR.not(IR.getprop(iterResult.cloneTree(), IR.string("done")));
    Node incr = IR.assign(iterResult.cloneTree(), getNext.cloneTree());
    body.addChildToFront(IR.var(IR.name(variableName),
        IR.getprop(iterResult.cloneTree(), IR.string("value"))));

    Node newFor = IR.forNode(init, cond, incr, body);
    newFor.useSourceInfoIfMissingFromForTree(node);
    parent.replaceChild(node, newFor);
    compiler.reportCodeChange();
  }

  private void checkClassReassignment(Node clazz) {
    Node name = NodeUtil.getClassNameNode(clazz);
    Node enclosingFunction = getEnclosingFunction(clazz);
    if (enclosingFunction == null) {
      return;
    }
    CheckClassAssignments checkAssigns = new CheckClassAssignments(name);
    NodeTraversal.traverse(compiler, enclosingFunction, checkAssigns);
  }

  private void visitSuper(Node node) {
    Node enclosing = node.getParent();
    Node potentialCallee = node;
    if (!enclosing.isCall()) {
      enclosing = enclosing.getParent();
      potentialCallee = potentialCallee.getParent();
    }
    if (!(enclosing.isCall() && enclosing.getFirstChild() == potentialCallee)) {
      cannotConvertYet(node, "Only calls to super or to a method of super are supported.");
      return;
    }
    Node clazz = NodeUtil.getEnclosingClass(node);
    if (clazz == null) {
      compiler.report(JSError.make(node, NO_SUPERTYPE));
      return;
    }
    if (NodeUtil.getClassNameNode(clazz) == null) {
      // Unnamed classes of the form:
      //   f(class extends D { ... });
      // give the problem that there is no name to be used in the call to goog.base for the
      // translation of super calls.
      // This will throw an error when the class is processed.
      return;
    }

    Node methodName;
    Node callName = enclosing.removeFirstChild();
    if (callName.isSuper()) {
      Node enclosingMember = NodeUtil.getEnclosingClassMember(enclosing);
      methodName = IR.string(enclosingMember.getString()).srcref(enclosing);
    } else {
      methodName = IR.string(callName.getLastChild().getString()).srcref(enclosing);
    }
    boolean useUnique = NodeUtil.isStatement(clazz) && !isInFunction(clazz);
    String uniqueClassString = useUnique ? getUniqueClassName(NodeUtil.getClassName(clazz))
        : NodeUtil.getClassName(clazz);
    Node uniqueClassName = NodeUtil.newQualifiedNameNode(compiler.getCodingConvention(),
        uniqueClassString);
    uniqueClassName.useSourceInfoIfMissingFromForTree(enclosing);
    Node base = IR.getprop(uniqueClassName,
        IR.string("base").srcref(enclosing)).srcref(enclosing);
    enclosing.addChildToFront(methodName);
    enclosing.addChildToFront(IR.thisNode().srcref(enclosing));
    enclosing.addChildToFront(base);
    enclosing.putBooleanProp(Node.FREE_CALL, false);
    compiler.reportCodeChange();
  }

  /**
   * Processes trailing default and rest parameters.
   */
  private void visitParamList(Node paramList, Node function) {
    Node insertSpot = null;
    Node block = function.getLastChild();
    for (int i = 0; i < paramList.getChildCount(); i++) {
      Node param = paramList.getChildAtIndex(i);
      if (param.hasChildren()) { // default parameter
        param.setOptionalArg(true);
        Node defaultValue = param.removeFirstChild();
        // Transpile to: param === undefined && (param = defaultValue);
        Node name = IR.name(param.getString());
        Node undefined = IR.name("undefined");
        Node stm = IR.exprResult(IR.and(IR.sheq(name, undefined),
            IR.assign(name.cloneNode(), defaultValue)));
        block.addChildAfter(stm.useSourceInfoIfMissingFromForTree(param), insertSpot);
        insertSpot = stm;
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
        Node freshVar = IR.name(FRESH_SPREAD_VAR + freshSpreadVarCounter++);
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
      Node bindApply = NodeUtil.newQualifiedNameNode(compiler.getCodingConvention(),
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
      if (currElement.isGetterDef() || currElement.isSetterDef()) {
        currElement = currElement.getNext();
      } else {
        Node nextNode = currElement.getNext();
        obj.removeChild(currElement);
        props.add(currElement);
        currElement = nextNode;
      }
    }

    String objName = FRESH_COMP_PROP_VAR + freshPropVarCounter++;

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

  private void checkClassSuperReferences(Node classNode) {
    Node className = classNode.getFirstChild();
    Node superClassName = className.getNext();
    if (NodeUtil.referencesSuper(classNode) && superClassName.isEmpty()) {
      compiler.report(JSError.make(classNode, NO_SUPERTYPE));
    }
  }

  /**
   * Classes are processed in 4 phases.
   *
   * 1) Metadata about the class is computed including the name and unique name (used to
   * support mocking).
   *
   * 2) Class members are processed and rewritten.
   *
   * 3) The constructor is built.
   *
   * 4) Class is reassigned using a unique name to support mocking
   *   ex) function foo() {} is rewritten to var unique$foo = function() {}; var foo = unique$foo;
   *
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
    boolean useUnique = NodeUtil.isStatement(classNode) && !isInFunction(classNode);
    String uniqueFullClassName = useUnique ? getUniqueClassName(fullClassName) : fullClassName;
    String superClassString = superClassName.getQualifiedName();

    Verify.verify(NodeUtil.isStatement(insertionPoint));

    className.detachFromParent();
    Node constructor = null;
    JSDocInfo ctorJSDocInfo = null;
    // Process all members of the class
    for (Node member : classMembers.children()) {
      if (member.getString().equals("constructor")) {
        ctorJSDocInfo = member.getJSDocInfo();
        constructor = member.getFirstChild().detachFromParent();
        if (!anonymous) {
          constructor.replaceChild(constructor.getFirstChild(), className);
        }
      } else {
        String qualifiedMemberName;
        if (member.isStaticMember()) {
          qualifiedMemberName = Joiner.on(".").join(
              uniqueFullClassName,
              member.getString());
        } else {
          qualifiedMemberName = Joiner.on(".").join(
              uniqueFullClassName,
              "prototype",
              member.getString());
        }
        Node assign = IR.assign(
            NodeUtil.newQualifiedNameNode(
                compiler.getCodingConvention(),
                qualifiedMemberName,
                /* basis node */ member,
                /* original name */ member.getString()),
            member.getFirstChild().detachFromParent());
        assign.srcref(member);

        JSDocInfo info = member.getJSDocInfo();
        if (info != null) {
          info.setAssociatedNode(assign);
          assign.setJSDocInfo(info);
        }

        Node newNode = NodeUtil.newExpr(assign);
        insertionPoint.getParent().addChildAfter(newNode, insertionPoint);
        insertionPoint = newNode;
      }
    }

    // Rewrite constructor
    if (constructor == null) {
      Node name = anonymous ? IR.name("").srcref(className) : className;
      constructor = IR.function(
          name,
          IR.paramList().srcref(classNode),
          IR.block().srcref(classNode));
    }
    JSDocInfo classJSDoc = classNode.getJSDocInfo();
    JSDocInfoBuilder newInfo = (classJSDoc != null) ?
        JSDocInfoBuilder.copyFrom(classJSDoc) :
        new JSDocInfoBuilder(true);

    newInfo.recordConstructor();
    if (!superClassName.isEmpty()) {

      if (newInfo.isInterfaceRecorded()) {
        newInfo.recordExtendedInterface(new JSTypeExpression(new Node(Token.BANG,
            IR.string(superClassString)),
            superClassName.getSourceFileName()));
      } else {
        Node inherits = IR.call(IR.name(INHERITS),
            NodeUtil.newQualifiedNameNode(compiler.getCodingConvention(), fullClassName),
            NodeUtil.newQualifiedNameNode(compiler.getCodingConvention(), superClassString));
        inherits.putBooleanProp(Node.FREE_CALL, true);
        Node inheritsCall = IR.exprResult(inherits);
        inheritsCall.useSourceInfoIfMissingFromForTree(classNode);
        Node enclosingStatement = NodeUtil.getEnclosingStatement(classNode);
        enclosingStatement.getParent().addChildAfter(inheritsCall, enclosingStatement);
        newInfo.recordBaseType(new JSTypeExpression(new Node(Token.BANG,
            IR.string(superClassString)),
            superClassName.getSourceFileName()));
        Node copyProps = IR.call(IR.name(COPY_PROP).srcref(classNode),
            NodeUtil.newQualifiedNameNode(compiler.getCodingConvention(), fullClassName),
            NodeUtil.newQualifiedNameNode(compiler.getCodingConvention(), superClassString));
        copyProps.useSourceInfoIfMissingFromForTree(classNode);
        copyProps.putBooleanProp(Node.FREE_CALL, true);
        enclosingStatement.getParent().addChildAfter(
            IR.exprResult(copyProps).srcref(classNode),  inheritsCall);
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
      Node newBody = IR.block(IR.returnNode(body).srcref(body)).srcref(body);
      n.addChildToBack(newBody);
    }

    UpdateThisNodes thisUpdater = new UpdateThisNodes();
    NodeTraversal.traverse(compiler, body, thisUpdater);
    if (thisUpdater.changed) {
      addThisVar(t);
    }

    compiler.reportCodeChange();
  }

  private void addThisVar(NodeTraversal t) {
    Scope scope = t.getScope();
    if (scope.isDeclared(THIS_VAR, false)) {
      return;
    }

    Node parent = t.getScopeRoot();
    if (parent.isFunction()) {
      // Add the new node at the beginning of the function body.
      parent = parent.getLastChild();
    }
    if (parent.isSyntheticBlock()) {
      // Add the new node inside the SCRIPT node instead of the
      // synthetic block that contains it.
      parent = parent.getFirstChild();
    }

    Node name = IR.name(THIS_VAR).srcref(parent);
    Node thisVar = IR.var(name, IR.thisNode().srcref(parent));
    thisVar.srcref(parent);
    parent.addChildToFront(thisVar);
    scope.declare(THIS_VAR, name, null, compiler.getInput(parent.getInputId()));
  }

  private static String getUniqueClassName(String qualifiedName) {
    return qualifiedName;
  }

  //TODO(mattloring) move this functionality to scopes once class scopes are computed.
  private static Node getEnclosingFunction(Node n) {
    return NodeUtil.getEnclosingType(n, Token.FUNCTION);
  }

  private static boolean isInFunction(Node n) {
    return getEnclosingFunction(n) != null;
  }

  private static class UpdateThisNodes implements NodeTraversal.Callback {
    private boolean changed = false;

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isThis()) {
        Node name = IR.name(THIS_VAR).srcref(n);
        parent.replaceChild(n, name);
        changed = true;
      }
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return !n.isFunction() || n.isArrowFunction();
    }
  }

  private void visitTemplateLiteral(NodeTraversal t, Node n) {
    if (!n.getFirstChild().isName()) {
      createDefaultTemplateLiteral(n);
    } else {
      //TODO(moz): Handle tagged template literals.
      cannotConvertYet(n, "Tagged template literals are not supported yet.");
    }
    compiler.reportCodeChange();
  }

  /**
   * Converts `${a} b ${c} d ${e}` to (a + " b " + c + " d " + e)
   *
   * @param n A TEMPLATELIT node that is not prefixed with a tag
   */
  private void createDefaultTemplateLiteral(Node n) {
    int length = n.getChildCount();
    if (length == 0) {
      n.getParent().replaceChild(n, IR.string("\"\""));
    } else {
      Node first = n.removeFirstChild(); // first is always a STRING node
      if (length == 1) {
        n.getParent().replaceChild(n, first);
      } else {
        // Add the first string with the first substitution expression
        Node add = IR.add(first, n.removeFirstChild().removeFirstChild());
        // Process the rest of the template literal
        for (int i = 2; i < length; i++) {
          Node child = n.removeFirstChild();
          if (child.isString()) {
            if (child.getString().isEmpty()) {
              continue;
            } else if (i == 2 && first.getString().isEmpty()) {
              // So that `${hello} world` gets translated into (hello + " world")
              // instead of ("" + hello + " world").
              add = add.getChildAtIndex(1).detachFromParent();
            }
          }
          add = IR.add(add, child.isString() ? child : child.removeFirstChild());
        }
        n.getParent().replaceChild(n, add.useSourceInfoIfMissingFromForTree(n));
      }
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
