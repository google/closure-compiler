/*
 * Copyright 2010 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.jsonml;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Traverse JsonML source tree and generates AST.
 *
 * @author dhans@google.com (Daniel Hans)
 */
public class Reader {

  static final DiagnosticType JSONML_SYNTAX = DiagnosticType.error(
      "JSONML_SYNTAX", "Syntax error: {0}");

  /** Root element of JsonML tree which contains JavaScript source. */
  private JsonML rootElement;

  /** Name of JavaScript source file */
  private String sourceName;

  /** Error reporter */
  private ErrorReporter errorReporter;

  /** List of ES5 directives supported by JsonML */
  private final Set<String> ALLOWED_DIRECTIVES = Sets.newHashSet("use strict");

  /** Number of node in JsonML order which is currently processed */
  private int nodeIndex;

  /**
   * Inner class which is responsible for passing reader errors
   * to the JS compiler.
   */
  private class ErrorReporter {
    private AbstractCompiler compiler;

    ErrorReporter(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    private void report(JsonML element, String...arguments)
        throws JsonMLException {
      report(JSONML_SYNTAX, element, arguments);
    }

    private void report(DiagnosticType type, JsonML element,
        String... arguments) throws JsonMLException {
      // nodeIndex is the number of the node in which the error occurred
      // we will store it in line number
      int lineno = nodeIndex;
      int charno = -1;

      report(JSError.make(sourceName, lineno, charno, type, arguments));
    }

    /**
     * Reports a new parser error to the compiler and terminates the job.
     * @param error JSError instance to be passed to the compiler
     */
    private void report(JSError error) throws JsonMLException {
      report(error, true);
    }

    /**
     * Reports a new parser error to the compiler and terminates the job
     * if the error is fatal.
     * @param error JSError instance to be passed to the compiler
     * @param terminal if true, parsing is terminated by throwing exception
     */
    private void report(JSError error, boolean terminal)
        throws JsonMLException {
      compiler.report(error);
      if (terminal) {
        throw new JsonMLException();
      }
    }
  }

  // TODO(dhans): Maybe this state can be replaced with a simpler check
  /**
   * Stores state if EXPR_RESULT node should be inserted. The reason why
   * we have to keep track on that is JsonML representation does not have this
   * information.
   */
  private boolean insertExprResultState = true;

  public void setRootElement(JsonML rootElement) {
    this.rootElement = rootElement;
  }

  /**
   * Generates AST for a specified JsonML source file.
   * @return root node of the generated AST
   * @throws JsonMLException if an error occurs
   */
  public Node parse(AbstractCompiler compiler) throws JsonMLException {
    if (compiler == null) {
      // TODO(dhans): Review error handling
      // maybe throw an exception that compiler is required for errors.
      return null;
    }

    errorReporter = this.new ErrorReporter(compiler);
    Node root = IR.block();
    nodeIndex = -1;

    Preconditions.checkState(rootElement.getType() == TagType.Program);
    transformElement(rootElement, root);
    return root.removeFirstChild();
  }

  /**
   * Retrieves value of an attribute, but does not throw an exception if
   * the attribute is not present for a specified JsonML element.
   * @param type desired type of the attribute
   * @return value of the attribute or null if it is not specified
   * @throws JsonMLException i.e. when the value has a wrong type
   */
  private <T> T getOptionalAttribute(JsonML element, TagAttr attr,
      Class<T> type) throws JsonMLException {
    return getAttribute(element, attr, type, true);
  }

  /**
   * Retrieves value of an attribute and throws an exception if
   * the attribute is not present for a specified JsonML element.
   * @param type desired type of the attribute
   * @return value of the attribute
   * @throws JsonMLException i.e. when the attribute does not exist
   */
  private <T> T getAttribute(JsonML element, TagAttr attr, Class<T> type)
      throws JsonMLException {
    return getAttribute(element, attr, type, false);
  }

  private <T> T getAttribute(JsonML element, TagAttr attr, Class<T> type,
      boolean optional) throws JsonMLException {
    Object value = element.getAttribute(attr);

    if (value == null) {
      if (type == null || optional) {
        return null;
      }

      throw new JsonMLException(
          "Missing " + attr.name() + " attribute for "
          + element.getType().name() + " element.");
    }

    // Double type is a special case, as it might be represented by all
    // Number types or even by certain strings which contain only digit chars
    if (type.equals(Double.class)) {
      if (value instanceof Number) {
        return type.cast(((Number) value).doubleValue());
      }
      if (value instanceof String) {
        return type.cast(Double.valueOf((String) value));
      }

      throw new JsonMLException(
          "Wrong type of " + attr.name() + " attribute. "
          + "Received: " + value.getClass() + ". Expected: " + type.getName());
    }

    if (type.isInstance(value)) {
      return type.cast(value);
    }

    throw new JsonMLException(
        "Wrong type of " + attr.name() + "attribute. "
        + "Received: " + value.getClass() + ". Expected: " + type.getName());
  }

  /**
   * Retrieves an attribute whose type should be Object.
   */
  private Object getObjectAttribute(JsonML element, TagAttr attr)
      throws JsonMLException {
    return getAttribute(element, attr, Object.class);
  }

  /**
   * Retrieves an attribute whose type should be String.
   */
  private String getStringAttribute(JsonML element, TagAttr attr)
      throws JsonMLException {
    return getAttribute(element, attr, String.class);
  }

  private void validate(JsonML element) throws JsonMLException {
    String errorMessage = Validator.validate(element);
    if (errorMessage != null) {
      errorReporter.report(element, errorMessage);
    }
  }

  /**
   * Recursively transforms JsonML tree into AST.
   *
   * @param element JsonML element to transform
   * @param parent current parent AST node, i.e. when the element is
   * transformed
   * to a new AST node, it should be added as a last child to the parent Node.
   */
  private void transformElement(JsonML element, Node parent)
      throws JsonMLException {
    // next element is transformed
    nodeIndex++;

    // the element has to be validated
    validate(element);

    // determine if EXPR_RESULT should be inserted
    if (insertExprResultState && JsonMLUtil.isExpression(element)) {
      transformExpr(element, parent);
      return;
    }

    switch (element.getType()) {
      case ArrayExpr:
        transformArrayExpr(element, parent);
        break;
      case AssignExpr:
        transformAssignExpr(element, parent);
        break;
      case BinaryExpr:
        transformBinaryExpr(element, parent);
        break;
      case BlockStmt:
        transformBlock(element, parent);
        break;
      case BreakStmt:
        transformBreakStmt(element, parent);
        break;
      case CallExpr:
        transformCallExpr(element, parent);
        break;
      case Case:
        transformCase(element, parent);
        break;
      case CatchClause:
        transformCatchClause(element, parent);
        break;
      case ConditionalExpr:
        transformConditionalExpr(element, parent);
        break;
      case ContinueStmt:
        transformContinueStmt(element, parent);
        break;
      case CountExpr:
        transformCountExpr(element, parent);
        break;
      case DataProp:
        transformDataProp(element, parent);
        break;
      case GetterProp:
        transformGetterProp(element, parent);
        break;
      case SetterProp:
        transformSetterProp(element, parent);
        break;
      case DefaultCase:
        transformDefaultCase(element, parent);
        break;
      case DeleteExpr:
        transformDeleteExpr(element, parent);
        break;
      case DoWhileStmt:
        transformDoWhileStmt(element, parent);
        break;
      case Empty:
        transformEmpty(element, parent);
        break;
      case EmptyStmt:
        transformEmptyStmt(element, parent);
        break;
      case EvalExpr:
        transformEvalExpr(element, parent);
        break;
      case ForInStmt:
        transformForInStmt(element, parent);
        break;
      case ForStmt:
        transformForStmt(element, parent);
        break;
      case FunctionDecl:
        transformFunctionDecl(element, parent);
        break;
      case FunctionExpr:
        transformFunctionExpr(element, parent);
        break;
      case IdExpr:
        transformIdExpr(element, parent);
        break;
      case IdPatt:
        transformIdPatt(element, parent);
        break;
      case IfStmt:
        transformIfStmt(element, parent);
        break;
      case InitPatt:
        transformInitPatt(element, parent);
        break;
      case InvokeExpr:
        transformInvokeExpr(element, parent);
        break;
      case LabelledStmt:
        transformLabelledStmt(element, parent);
        break;
      case LiteralExpr:
        transformLiteralExpr(element, parent);
        break;
      case LogicalAndExpr:
        transformLogicalAndExpr(element, parent);
        break;
      case LogicalOrExpr:
        transformLogicalOrExpr(element, parent);
        break;
      case MemberExpr:
        transformMemberExpr(element, parent);
        break;
      case NewExpr:
        transformNewExpr(element, parent);
        break;
      case ObjectExpr:
        transformObjectExpr(element, parent);
        break;
      case ParamDecl:
        transformParamDecl(element, parent);
        break;
      case Program:
        transformProgram(element, parent);
        break;
      case PrologueDecl:
        transformPrologueDecl(element, parent);
        break;
      case RegExpExpr:
        transformRegExpExpr(element, parent);
        break;
      case ReturnStmt:
        transformReturnStmt(element, parent);
        break;
      case SwitchStmt:
        transformSwitchStmt(element, parent);
        break;
      case ThisExpr:
        transformThisExpr(element, parent);
        break;
      case ThrowStmt:
        transformThrowStmt(element, parent);
        break;
      case TryStmt:
        transformTryStmt(element, parent);
        break;
      case TypeofExpr:
        transformTypeofExpr(element, parent);
        break;
      case UnaryExpr:
        transformUnaryExpr(element, parent);
        break;
      case VarDecl:
        transformVarDecl(element, parent);
        break;
      case WhileStmt:
        transformWhileStmt(element, parent);
        break;
      case WithStmt:
        transformWithStmt(element, parent);
        break;
    }
  }

  /*
   * Helper functions.
   * Usually called by functions which process particular JsonML elements.
   */

  private void transformAllChildren(JsonML element, Node parent,
      boolean newState) throws JsonMLException {
    transformElements(element.getChildren(), parent, newState);
  }

  private void transformAllChildren(JsonML element, Node parent)
      throws JsonMLException {
    transformElements(element.getChildren(), parent);
  }

  private void transformAllChildrenFromIndex(JsonML element, Node parent,
      int fromIndex, boolean newState) throws JsonMLException {
    transformElements(element.getChildren().subList(
        fromIndex, element.childrenSize()), parent, newState);
  }

  private void transformElements(List<JsonML> elements, Node parent,
      boolean newState) throws JsonMLException {
    boolean oldState = insertExprResultState;
    insertExprResultState = newState;
    transformElements(elements, parent);
    insertExprResultState = oldState;
  }

  private void transformElements(List<JsonML> elements, Node parent)
      throws JsonMLException {
    for (JsonML element : elements) {
      transformElement(element, parent);
    }
  }

  /**
   * Responsible for inserting EXPR_RESULT nodes.
   */
  private boolean transformExpr(JsonML element, Node parent)
      throws JsonMLException {
    boolean result = false;
    if (insertExprResultState) {
      Node node = new Node(Token.EXPR_RESULT);
      parent.addChildToBack(node);
      insertExprResultState = false;
      nodeIndex--; // the same node will be transformed again
      transformElement(element, node);
      insertExprResultState = true;
      result = true;
    }
    return result;
  }

  /**
   * Generic function responsible for dealing with JsonML elements describing
   * for loop (ForStmt and ForInStmt).
   */
  private void transformForLoop(JsonML element, Node parent, int childno)
      throws JsonMLException {
    Preconditions.checkState(insertExprResultState == true);
    insertExprResultState = false;

    Node node = createNode(Token.FOR, element);
    parent.addChildToBack(node);

    JsonML child;
    for (int i = 0; i < childno; ++i) {
      child = element.getChild(i);
      if (child.getType() == TagType.EmptyStmt ||
          child.getType() == TagType.Empty) {
        nodeIndex++;
        node.addChildToBack(IR.empty());
      } else {
        transformElement(child, node);
      }
    }

    transformPotentiallyUnwrappedBlock(element.getChild(childno), node);
    insertExprResultState = true;
  }

  /**
   * Generic function responsible for dealing with the following JsonML
   * elements: BreakStmt and ContinueStmt.
   */
  private void transformJumpStmt(JsonML element, Node parent, int type)
      throws JsonMLException {
    Node node = createNode(type, element);
    parent.addChildToBack(node);

    String label = getOptionalAttribute(element, TagAttr.LABEL, String.class);
    if (label != null) {
      node.addChildToBack(IR.labelName(label));
    }
  }

  /**
   * Generic function responsible for dealing with JsonML elements describing
   * logical two arguments expressions: LogicalAndExpr and LogicalOrExpr.
   */
  private void transformLogicalExpr(JsonML element, Node parent, int type)
      throws JsonMLException {
    transformTwoArgumentExpr(element, parent, type);
  }

  /**
   * Generic function responsible for dealing with all kind of expressions
   * which are passed exactly two arguments.
   */
  private void transformTwoArgumentExpr(JsonML element, Node parent,
      int type) throws JsonMLException {
    Node node = createNode(type, element);
    parent.addChildToBack(node);
    transformAllChildren(element, node);
  }

  /**
   * Transforms an element which should be transformed into a BLOCK node, but
   * may not be represented by BlockStmt. In this case, additional BLOCK node
   * is created.
   */
  private void transformPotentiallyUnwrappedBlock(JsonML element, Node parent)
      throws JsonMLException {

    // in theory it should be always EmptyStmt, but due to possible
    // compatibility issues Empty element is allowed as well
    if (element.getType() == TagType.EmptyStmt ||
        element.getType() == TagType.Empty) {
      nodeIndex++;
      // Empty elements are only replaced by BLOCK node
      Node block = IR.block();
      parent.addChildToBack(block);
      block.putBooleanProp(Node.EMPTY_BLOCK, true);
    } else if (element.getType() != TagType.BlockStmt) {
      Node block = IR.block();
      parent.addChildToBack(block);
      boolean state = insertExprResultState;
      insertExprResultState = true;
      transformElement(element, block);
      insertExprResultState = state;
    } else {
      nodeIndex++;
      transformBlock(element, parent);
    }
  }

  /*
   * Main functions.
   * Functions responsible for handling particular JsonML elements. Depending
   * on type, transformElement function dispatches actual work to
   * the corresponding function below.
   */

  private void transformArrayExpr(JsonML element, Node parent)
      throws JsonMLException {

    Node node = createNode(Token.ARRAYLIT, element);
    parent.addChildToBack(node);

    // iterate through all the children and look for empty elements
    for (JsonML child : element.getChildren()) {
      transformElement(child, node);
    }
  }

  private void transformAssignExpr(JsonML element, Node parent)
      throws JsonMLException {
    String op = getStringAttribute(element, TagAttr.OP);
    int type = Operator.getNodeTypeForAssignOp(op);
    transformTwoArgumentExpr(element, parent, type);
  }

  private void transformBinaryExpr(JsonML element, Node parent)
      throws JsonMLException {
    String op = getStringAttribute(element, TagAttr.OP);
    int type = Operator.getNodeTypeForBinaryOp(op);
    transformTwoArgumentExpr(element, parent, type);
  }

  private void transformBlock(JsonML element, Node parent)
      throws JsonMLException {
    transformBlock(element, parent, 0, element.childrenSize());
  }

  private void transformBlock(JsonML element, Node parent, int start)
      throws JsonMLException {
    transformBlock(element, parent, start, element.childrenSize());
  }

  private void transformBlock(JsonML element, Node parent, int start, int end)
      throws JsonMLException {
    Node node = createNode(Token.BLOCK, element);
    parent.addChildToBack(node);
    transformElements(element.getChildren(start, end), node, true);
  }

  private void transformBreakStmt(JsonML element, Node parent)
      throws JsonMLException {
    transformJumpStmt(element, parent, Token.BREAK);
  }

  private void transformCallExpr(JsonML element, Node parent)
      throws JsonMLException {

    Node node = createNode(Token.CALL, element);
    parent.addChildToBack(node);

    transformAllChildren(element, node);

    // Keep track of of the "this" context of a call.  A call without an
    // explicit "this" is a free call.
    Node first = node.getFirstChild();
    if (first.getType() != Token.GETPROP && first.getType() != Token.GETELEM) {
      node.putBooleanProp(Node.FREE_CALL, true);
    }
  }

  private void transformCase(JsonML element, Node parent)
      throws JsonMLException {

    Node node = createNode(Token.CASE, element);
    parent.addChildToBack(node);

    // the first element represents case id
    JsonML child = element.getChild(0);
    transformElement(child, node);

    // always insert an extra BLOCK node
    Node block = IR.block();
    block.setIsSyntheticBlock(true);
    node.addChildToBack(block);

    transformAllChildrenFromIndex(element, block, 1, true);
  }

  private void transformCatchClause(JsonML element, Node parent)
      throws JsonMLException {

    Node node = createNode(Token.CATCH, element);
    parent.addChildToBack(node);

    JsonML child = element.getChild(0);
    transformElement(child, node);

    // the second child represents actual block
    child = element.getChild(1);
    transformElement(child, node);
  }

  private void transformConditionalExpr(JsonML element, Node parent)
      throws JsonMLException {
    Node node = createNode(Token.HOOK, element);
    parent.addChildToBack(node);

    transformAllChildren(element, node);
  }

  private void transformContinueStmt(JsonML element, Node parent)
      throws JsonMLException {
    transformJumpStmt(element, parent, Token.CONTINUE);
  }

  /*
   * CountExpr are both incrementing and decrementing expressions (++x, --x)
   */
  private void transformCountExpr(JsonML element, Node parent)
      throws JsonMLException {
    String op = getStringAttribute(element, TagAttr.OP);

    int type = Operator.getNodeTypeForCountOp(op);

    Boolean isPrefix = getAttribute(element, TagAttr.IS_PREFIX, Boolean.class);
    Node node = createNode(type, element);
    node.putIntProp(Node.INCRDECR_PROP, isPrefix ? 0 : 1);
    parent.addChildToBack(node);

    transformElement(element.getChild(0), node);
  }

  /*
   * DataProp is the name for an object property which is initialized
   * when the object is created by object literal.
   * For example, in {x: 1, y: 2} each property is represented by its own
   * DataProp.
   */
  private void transformDataProp(JsonML element, Node parent)
      throws JsonMLException {
    Object name = getObjectAttribute(element, TagAttr.NAME);

    Node node = null;
    if (name instanceof Number) {
      node = IR.stringKey(getStringValue(((Number) name).doubleValue()));
    } else if (name instanceof String) {
      node = IR.stringKey((String) name);
    } else {
      throw new IllegalStateException(
          "The name of the property has invalid type.");
    }

    setPosition(node);
    parent.addChildToBack(node);

    transformElement(element.getChild(0), node);
  }

  private static String getStringValue(double value) {
    long longValue = (long) value;

    // Return "1" instead of "1.0"
    if (longValue == value) {
      return Long.toString(longValue);
    } else {
      return Double.toString(value);
    }
  }

  /*
   * GetterProp is a object literal entry for a getter.
   * For example, {get x() {return 1}}
   */
  private void transformGetterProp(JsonML element, Node parent)
      throws JsonMLException {
    transformProp(Token.GETTER_DEF, element, parent);
  }

  /*
   * GetterProp is a object literal entry for a getter.
   * For example, {set x() {return 1}}
   */
  private void transformSetterProp(JsonML element, Node parent)
      throws JsonMLException {
    transformProp(Token.SETTER_DEF, element, parent);
  }

  private void transformProp(int tokenType, JsonML element, Node parent)
      throws JsonMLException {
    Object name = getObjectAttribute(element, TagAttr.NAME);

    Node node = null;
    if (name instanceof Number) {
      // TODO(johnlenz): convert the number to a quoted string.
      throw new IllegalStateException(
         "Not yet supported.");
    } else if (name instanceof String) {
      node = Node.newString(tokenType, (String) name);
    } else {
      throw new IllegalStateException(
          "The name of the property has invalid type.");
    }

    setPosition(node);
    parent.addChildToBack(node);

    transformElement(element.getChild(0), node);
  }


  private void transformDefaultCase(JsonML element, Node parent)
      throws JsonMLException {
    Node node = createNode(Token.DEFAULT_CASE, element);
    parent.addChildToBack(node);

    // the first child represent body
    Node block = IR.block();
    block.setIsSyntheticBlock(true);
    node.addChildToBack(block);

    transformAllChildren(element, block, true);
  }

  private void transformDeleteExpr(JsonML element, Node parent)
      throws JsonMLException {

    Node node = createNode(Token.DELPROP, element);
    parent.addChildToBack(node);

    transformElement(element.getChild(0), node);
  }

  private void transformDoWhileStmt(JsonML element, Node parent)
      throws JsonMLException {
    Preconditions.checkState(insertExprResultState == true);
    insertExprResultState = false;

    Node node = createNode(Token.DO, element);
    parent.addChildToBack(node);

    // the first child represents body
    JsonML child = element.getChild(0);
    transformPotentiallyUnwrappedBlock(child, node);

    // the second child represents condition
    child = element.getChild(1);
    transformElement(child, node);

    insertExprResultState = true;
  }

  private void transformEmpty(JsonML element, Node parent) {
    switch (parent.getType()) {
      case Token.ARRAYLIT:
        parent.addChildToBack(IR.empty());
        break;
      case Token.FUNCTION:
        parent.addChildToBack(IR.name(""));
        break;
      default:
        throw new IllegalArgumentException("Unexpected Empty element.");
    }
  }

  private void transformEmptyStmt(JsonML element, Node parent) {
    Preconditions.checkState(
        parent.getType() == Token.BLOCK || parent.getType() == Token.SCRIPT);
    parent.addChildToBack(IR.empty());
  }

  private void transformEvalExpr(JsonML element, Node parent)
      throws JsonMLException {

    Node node = createNode(Token.CALL, element);
    node.putBooleanProp(Node.FREE_CALL, true);
    parent.addChildToBack(node);

    Node child = IR.name("eval");
    child.putBooleanProp(Node.DIRECT_EVAL, true);
    node.addChildToBack(child);

    transformAllChildren(element, node);
  }

  private void transformForInStmt(JsonML element, Node parent)
      throws JsonMLException {
    transformForLoop(element, parent, 2);
  }

  private void transformForStmt(JsonML element, Node parent)
      throws JsonMLException {
    transformForLoop(element, parent, 3);
  }

  private void transformFunction(JsonML element, Node parent,
      boolean needsName) throws JsonMLException {
    Node node = createNode(Token.FUNCTION, element);
    parent.addChildToBack(node);

    JsonML child = element.getChild(0);
    String name = "";

    // it be already validated at this point that a non empty name exists
    // if it is a function declaration
    transformElement(element.getChild(0), node);

    transformElement(element.getChild(1), node);

    // other children represents function body which should be
    // wrapped inside a block node
    transformBlock(element, node, 2);
  }

  private void transformFunctionDecl(JsonML element, Node parent)
      throws JsonMLException {
    transformFunction(element, parent, true);
  }

  private void transformFunctionExpr(JsonML element, Node parent)
      throws JsonMLException {
    transformFunction(element, parent, false);
  }

  private void transformIdExpr(JsonML element, Node parent)
      throws JsonMLException {
    String name = getStringAttribute(element, TagAttr.NAME);
    Node node = IR.name(name);
    setPosition(node);
    parent.addChildToBack(node);
  }

  /*
   * InitPatt represents all variable declarations value initialization.
   * It has two children: name of the variable and the initial value.
   */
  private void transformInitPatt(JsonML element, Node parent)
      throws JsonMLException {
    JsonML child = element.getChild(0);
    nodeIndex++;
    Node node = IR.name(
        getAttribute(child, TagAttr.NAME, String.class));
    setPosition(node);
    parent.addChildToBack(node);

    child = element.getChild(1);
    transformElement(child, node);
  }

  private void transformIdPatt(JsonML element, Node parent)
      throws JsonMLException {
    Node node = IR.name(
        getStringAttribute(element, TagAttr.NAME));
    setPosition(node);
    parent.addChildToBack(node);
  }

  private void transformIfStmt(JsonML element, Node parent)
      throws JsonMLException {
    Preconditions.checkState(insertExprResultState == true);
    insertExprResultState = false;

    Node node = createNode(Token.IF, element);
    parent.addChildToBack(node);

    // the first child represents condition
    JsonML child = element.getChild(0);
    transformElement(child, node);

    // the second child is required
    child = element.getChild(1);
    transformPotentiallyUnwrappedBlock(child, node);

    // the third child represents else part and is not required by AST
    child = element.getChild(2);
    if (child.getType() != TagType.EmptyStmt &&
        child.getType() != TagType.Empty) {
      transformPotentiallyUnwrappedBlock(child, node);
    } else {
      nodeIndex++;
    }
    insertExprResultState = true;
  }

  private void transformInvokeExpr(JsonML element, Node parent)
      throws JsonMLException {
    Node node = createNode(Token.CALL, element);
    parent.addChildToBack(node);

    transformMemberExpr(element, node);

    transformElements(element.getChildren(2, element.childrenSize()), node);
  }

  private void transformLabelledStmt(JsonML element, Node parent)
      throws JsonMLException {
    String label = getStringAttribute(element, TagAttr.LABEL);
    Node node = createNode(Token.LABEL, element);
    node.addChildToBack(IR.labelName(label));
    parent.addChildToBack(node);

    JsonML child = element.getChild(0);
    if (child.getType() == TagType.EmptyStmt) {
      nodeIndex++;
      node.addChildToBack(IR.empty());
    } else {
      transformElement(element.getChild(0), node);
    }
  }

  private void transformLiteralExpr(JsonML element, Node parent)
      throws JsonMLException {

    Node node = null;
    Type type = Type.get(getStringAttribute(element, TagAttr.TYPE));
    switch (type) {
      case BOOLEAN: {
        Boolean value = getAttribute(element, TagAttr.VALUE, Boolean.class);
        if (value) {
          node = IR.trueNode();
        } else {
          node = IR.falseNode();
        }
        break;
      }

      case NULL: {
        // needed to throw an exception if value is not null
        getAttribute(element, TagAttr.VALUE, null);
        node = IR.nullNode();
        break;
      }

      case NUMBER: {
        Double value = getAttribute(element, TagAttr.VALUE, Double.class);
        node = IR.number(value);
        break;
      }

      case STRING: {
        String value = getStringAttribute(element, TagAttr.VALUE);
        node = IR.string(value);
        break;
      }

      default:
        throw new JsonMLException("Unrecognized type attribute.");
    }

    setPosition(node);
    parent.addChildToBack(node);
  }

  private void transformLogicalAndExpr(JsonML element, Node parent)
      throws JsonMLException {
    transformLogicalExpr(element, parent, Token.AND);
  }

  private void transformLogicalOrExpr(JsonML element, Node parent)
      throws JsonMLException {
    transformLogicalExpr(element, parent, Token.OR);
  }

  private void transformMemberExpr(JsonML element, Node parent)
      throws JsonMLException {

    String op = getAttribute(element, TagAttr.OP, String.class);
    int type;
    if (op.equals(".")) {
      type = Token.GETPROP;
    } else if (op.equals("[]")) {
      type = Token.GETELEM;
    } else {
      throw new JsonMLException("Invalid OP argument: " + op);
    }

    Node node = createNode(type, element);
    parent.addChildToBack(node);

    transformElement(element.getChild(0), node);
    transformElement(element.getChild(1), node);
  }

  private void transformNewExpr(JsonML element, Node parent)
      throws JsonMLException {
    Node node = createNode(Token.NEW, element);
    parent.addChildToBack(node);
    transformAllChildren(element, node);
  }

  private void transformObjectExpr(JsonML element, Node parent)
      throws JsonMLException {
    Node node = createNode(Token.OBJECTLIT, element);
    parent.addChildToBack(node);

    transformAllChildren(element, node);
  }

  private void transformParamDecl(JsonML element, Node parent)
      throws JsonMLException {
    // formal arguments should be wrapped by LP node
    Node node = createNode(Token.PARAM_LIST, element);
    parent.addChildToBack(node);

    transformAllChildren(element, node);
  }

  private void transformProgram(JsonML element, Node parent)
      throws JsonMLException {
    Preconditions.checkNotNull(parent);
    insertExprResultState = true;

    Node script = IR.script();
    parent.addChildToBack(script);

    for (JsonML child : element.getChildren()) {
      transformElement(child, script);
    }
  }

  private void transformPrologueDecl(JsonML element, Node parent)
      throws JsonMLException {
    String directive = getStringAttribute(element, TagAttr.DIRECTIVE);

    if (ALLOWED_DIRECTIVES.contains(directive)) {
      Set<String> directives = parent.getDirectives();
      if (directives == null) {
        directives = Sets.newHashSet();
      }
      directives.add(directive);
      parent.setDirectives(directives);
    } else {
      // for a directive which is not supported, we create a regular node
      Node node = IR.exprResult(IR.string(directive));
      parent.addChildToBack(node);
    }
  }

  private void transformRegExpExpr(JsonML element, Node parent)
      throws JsonMLException {
    Node node = createNode(Token.REGEXP, element);
    parent.addChildToBack(node);

    String body = getStringAttribute(element, TagAttr.BODY);
    node.addChildToBack(IR.string(body));

    String flags = getStringAttribute(element, TagAttr.FLAGS);
    if (!(flags.equals(""))) {
      node.addChildToBack(IR.string(flags));
    }
  }

  private void transformReturnStmt(JsonML element, Node parent)
      throws JsonMLException {
    Preconditions.checkState(insertExprResultState == true);
    Node node = createNode(Token.RETURN, element);
    parent.addChildToBack(node);

    if (element.hasChildren()) {
      insertExprResultState = false;
      transformElement(element.getChild(0), node);
      insertExprResultState = true;
    }
  }

  private void transformSwitchStmt(JsonML element, Node parent)
      throws JsonMLException {
    Preconditions.checkState(insertExprResultState == true);
    insertExprResultState = false;

    Node node = createNode(Token.SWITCH, element);
    parent.addChildToBack(node);

    // make sure it has at least one child
    // the first child represents switch param
    JsonML child = element.getChild(0);
    transformElement(child, node);

    // the rest of the children represent cases
    for (int i = 1; i < element.childrenSize(); ++i) {
      child = element.getChild(i);
      // make sure it is case or default
      transformElement(child, node);
    }

    insertExprResultState = true;
  }

  /**
   * @throws JsonMLException
   */
  private void transformThisExpr(JsonML element, Node parent)
      throws JsonMLException {
    parent.addChildToBack(createNode(Token.THIS, element));
  }

  private void transformThrowStmt(JsonML element, Node parent)
      throws JsonMLException {
    Preconditions.checkState(insertExprResultState == true);
    Node node = createNode(Token.THROW, element);
    parent.addChildToBack(node);

    insertExprResultState = false;
    transformElement(element.getChild(0), node);
    insertExprResultState = true;
  }

  private void transformTryStmt(JsonML element, Node parent)
      throws JsonMLException {
    Preconditions.checkState(insertExprResultState == true);
    Node node = createNode(Token.TRY, element);
    parent.addChildToBack(node);

    // the first child represents try body
    JsonML child = element.getChild(0);
    transformElement(child, node);

    // the second child represents catch
    Node block = IR.block();
    node.addChildToBack(block);
    child = element.getChild(1);

    if (child.getType() == TagType.CatchClause) {
      transformElement(child, block);
    } else {
      // catch clause is not present, but the element has to be counted
      nodeIndex++;
    }

    // if the third child is present, it represents finally
    if (element.childrenSize() == 3) {
      child = element.getChild(2);
      transformElement(child, node);
    }
  }

  private void transformTypeofExpr(JsonML element, Node parent)
      throws JsonMLException {
    Node node = createNode(Token.TYPEOF, element);
    parent.addChildToBack(node);
    transformElement(element.getChild(0), node);
  }

  private void transformUnaryExpr(JsonML element, Node parent)
      throws JsonMLException {
    String op = getStringAttribute(element, TagAttr.OP);
    int type = Operator.getNodeTypeForUnaryOp(op);

    Node node = createNode(type, element);
    parent.addChildToBack(node);

    transformAllChildren(element, node);
  }

  private void transformVarDecl(JsonML element, Node parent)
      throws JsonMLException {
    Node node = createNode(Token.VAR, element);
    parent.addChildToBack(node);

    transformAllChildren(element, node, false);
  }

  private void transformWhileStmt(JsonML element, Node parent)
      throws JsonMLException {
    Preconditions.checkState(insertExprResultState == true);
    insertExprResultState = false;

    Node node = createNode(Token.WHILE, element);
    parent.addChildToBack(node);

    // the first child represents loop condition
    JsonML child = element.getChild(0);
    transformElement(child, node);

    // the second child represents loop body
    child = element.getChild(1);
    transformPotentiallyUnwrappedBlock(child, node);

    insertExprResultState = true;
  }

  private void transformWithStmt(JsonML element, Node parent)
      throws JsonMLException {
    Preconditions.checkState(insertExprResultState == true);
    insertExprResultState = false;

    Node node = createNode(Token.WITH, element);
    parent.addChildToBack(node);

    // the first child represents object
    JsonML child = element.getChild(0);
    transformElement(child, node);

    // the second child represents body
    child = element.getChild(1);
    transformPotentiallyUnwrappedBlock(child, node);

    insertExprResultState = true;
  }

  /**
   * Creates a node which refers to a particular JsonML element.
   */
  private Node createNode(int type, JsonML element) {
    return new Node(type, nodeIndex, -1);
  }

  /**
   * Sets position for a node which refers to a particular JsonML element.
   * The position says which number (in pre-order) has the corresponding
   * JsonML element in the tree.
   */
  private void setPosition(Node node) {
    node.setLineno(nodeIndex);
  }

  /**
   * Internal representation for operators which are used by JsonML as
   * attributes for various elements.
   */
  private enum Operator {
    // Assign Operators
    ASSIGN("="),
    ASSIGN_BITOR("|="),
    ASSIGN_BITXOR("^="),
    ASSIGN_BITAND("&="),
    ASSIGN_LSH("<<="),
    ASSIGN_RSH(">>="),
    ASSIGN_URSH(">>>="),
    ASSIGN_ADD("+="),
    ASSIGN_SUB("-="),
    ASSIGN_MUL("*="),
    ASSIGN_DIV("/="),
    ASSIGN_MOD("%="),

    // Binary Operators
    BITOR("|"),
    BITXOR("^"),
    BITAND("&"),
    EQ("=="),
    NE("!="),
    LT("<"),
    LE("<="),
    GT(">"),
    GE(">="),
    LSH("<<"),
    RSH(">>"),
    URSH(">>>"),
    ADD("+"),
    SUB("-"),
    MUL("*"),
    DIV("/"),
    MOD("%"),
    SHEQ("==="),
    SHNE("!=="),
    COMMA(","),
    INSTANCEOF("instanceof"),
    IN("in"),

    // Count Operators
    DEC("--"),
    INC("++"),

    // Unary Operators
    NOT("!"),
    BITNOT("~"),
    POS("+_unary"), // "+" would be a duplicate with ADD
    NEG("-_unary"), // "-" would be a duplicate with SUB
    VOID("void");

    private final String name;
    private static Map<String, Operator> lookup = Maps.newHashMap();

    // Maps string representation of operators with corresponding enums
    static {
      for (Operator op : Operator.values()) {
        lookup.put(op.getName(), op);
      }
    }

    private String getName() {
      return this.name;
    }

    private Operator(String name) {
      this.name = name;
    }

    private static Operator get(String name) {
      return lookup.get(name);
    }

    /**
     * Returns assign operator associated with a specified name.
     */
    private static int getNodeTypeForAssignOp(String name) {
      Operator op = get(name);
      if (op == null) {
        return Token.ERROR;
      }

      int type;
      switch (op) {
        case ASSIGN:
          type = Token.ASSIGN;
          break;
        case ASSIGN_BITOR:
          type = Token.ASSIGN_BITOR;
          break;
        case ASSIGN_BITXOR:
          type = Token.ASSIGN_BITXOR;
          break;
        case ASSIGN_BITAND:
          type = Token.ASSIGN_BITAND;
          break;
        case ASSIGN_LSH:
          type = Token.ASSIGN_LSH;
          break;
        case ASSIGN_RSH:
          type = Token.ASSIGN_RSH;
          break;
        case ASSIGN_URSH:
          type = Token.ASSIGN_URSH;
          break;
        case ASSIGN_ADD:
          type = Token.ASSIGN_ADD;
          break;
        case ASSIGN_SUB:
          type = Token.ASSIGN_SUB;
          break;
        case ASSIGN_MUL:
          type = Token.ASSIGN_MUL;
          break;
        case ASSIGN_DIV:
          type = Token.ASSIGN_DIV;
          break;
        case ASSIGN_MOD:
          type = Token.ASSIGN_MOD;
          break;
        default:
          throw new IllegalArgumentException(""
              + "Invalid type of assign expression.");
      }
      return type;
    }

    /**
     * Returns binary operator associated with a specified name.
     */
    private static int getNodeTypeForBinaryOp(String name) {
      Operator op = get(name);

      int type;
      switch (op) {
        case BITOR:
          type = Token.BITOR;
          break;
        case BITXOR:
          type = Token.BITXOR;
          break;
        case BITAND:
          type = Token.BITAND;
          break;
        case EQ:
          type = Token.EQ;
          break;
        case NE:
          type = Token.NE;
          break;
        case LT:
          type = Token.LT;
          break;
        case LE:
          type = Token.LE;
          break;
        case GT:
          type = Token.GT;
          break;
        case GE:
          type = Token.GE;
          break;
        case LSH:
          type = Token.LSH;
          break;
        case RSH:
          type = Token.RSH;
          break;
        case URSH:
          type = Token.URSH;
          break;
        case ADD:
          type = Token.ADD;
          break;
        case SUB:
          type = Token.SUB;
          break;
        case MUL:
          type = Token.MUL;
          break;
        case DIV:
          type = Token.DIV;
          break;
        case MOD:
          type = Token.MOD;
          break;
        case SHEQ:
          type = Token.SHEQ;
          break;
        case SHNE:
          type = Token.SHNE;
          break;
        case COMMA:
          type = Token.COMMA;
          break;
        case INSTANCEOF:
          type = Token.INSTANCEOF;
          break;
        case IN:
          type = Token.IN;
          break;
        default:
          throw new IllegalArgumentException(""
              + "Invalid type of binary expression.");
      }
      return type;
    }

    /**
     * Returns count operator(++, --)  associated with a specified name.
     */
    private static int getNodeTypeForCountOp(String name) {
      Operator op = get(name);
      if (op == null) {
        return Token.ERROR;
      }

      int type;
      switch (op) {
        case DEC:
          type = Token.DEC;
          break;
        case INC:
          type = Token.INC;
          break;
        default:
          throw new IllegalArgumentException(""
              + "Invalid type of count expression.");
      }
      return type;
    }

    /**
     * Returns assign operator associated with a specified name.
     */
    private static int getNodeTypeForUnaryOp(String name) {
      String realName = new String(name);
      if (name.equals("+") || name.equals("-")) {
        realName += "_unary";
      }
      Operator op = get(realName);

      int type;
      switch (op) {
        case NOT:
          type = Token.NOT;
          break;
        case BITNOT:
          type = Token.BITNOT;
          break;
        case POS:
          type = Token.POS;
          break;
        case NEG:
          type = Token.NEG;
          break;
        case VOID:
          type = Token.VOID;
          break;
        default:
          throw new IllegalArgumentException(""
              + "Invalid type of unary expression.");
      }
      return type;
    }
  }

  /**
   * Internal representation of possible types of arguments of JsonML.
   */
  private enum Type {
    BOOLEAN("boolean"),
    NULL("null"),
    NUMBER("number"),
    STRING("string");

    private final String name;
    private static Map<String, Type> lookup = new HashMap<String, Type>();

    static {
      for (Type type : Type.values()) {
        lookup.put(type.getName(), type);
      }
    }

    private String getName() {
      return this.name;
    }

    private Type(String name) {
      this.name = name;
    }

    private static Type get(String name) {
      return lookup.get(name);
    }
  }
}
