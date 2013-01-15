/*
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   John Lenz
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */

package com.google.javascript.rhino;

import com.google.common.base.Preconditions;

import java.util.List;

/**
 * An AST construction helper class
 * @author johnlenz@google.com (John Lenz)
 */
public class IR {

  private IR() {}

  public static Node empty() {
    return new Node(Token.EMPTY);
  }

  public static Node function(Node name, Node params, Node body) {
    Preconditions.checkState(name.isName());
    Preconditions.checkState(params.isParamList());
    Preconditions.checkState(body.isBlock());
    return new Node(Token.FUNCTION, name, params, body);
  }

  public static Node paramList() {
    return new Node(Token.PARAM_LIST);
  }

  public static Node paramList(Node param) {
    Preconditions.checkState(param.isName());
    return new Node(Token.PARAM_LIST, param);
  }

  public static Node paramList(Node ... params) {
    Node paramList = paramList();
    for (Node param : params) {
      Preconditions.checkState(param.isName());
      paramList.addChildToBack(param);
    }
    return paramList;
  }

  public static Node paramList(List<Node> params) {
    Node paramList = paramList();
    for (Node param : params) {
      Preconditions.checkState(param.isName());
      paramList.addChildToBack(param);
    }
    return paramList;
  }

  public static Node block() {
    Node block = new Node(Token.BLOCK);
    return block;
  }

  public static Node block(Node stmt) {
    Preconditions.checkState(mayBeStatement(stmt));
    Node block = new Node(Token.BLOCK, stmt);
    return block;
  }

  public static Node block(Node ... stmts) {
    Node block = block();
    for (Node stmt : stmts) {
      Preconditions.checkState(mayBeStatement(stmt));
      block.addChildToBack(stmt);
    }
    return block;
  }

  public static Node block(List<Node> stmts) {
    Node paramList = block();
    for (Node stmt : stmts) {
      Preconditions.checkState(mayBeStatement(stmt));
      paramList.addChildToBack(stmt);
    }
    return paramList;
  }

  private static Node blockUnchecked(Node stmt) {
    return new Node(Token.BLOCK, stmt);
  }

  public static Node script() {
    // TODO(johnlenz): finish setting up the SCRIPT node
    Node block = new Node(Token.SCRIPT);
    return block;
  }

  public static Node script(Node ... stmts) {
    Node block = script();
    for (Node stmt : stmts) {
      Preconditions.checkState(mayBeStatementNoReturn(stmt));
      block.addChildToBack(stmt);
    }
    return block;
  }

  public static Node script(List<Node> stmts) {
    Node paramList = script();
    for (Node stmt : stmts) {
      Preconditions.checkState(mayBeStatementNoReturn(stmt));
      paramList.addChildToBack(stmt);
    }
    return paramList;
  }

  public static Node var(Node name, Node value) {
    Preconditions.checkState(name.isName() && !name.hasChildren());
    Preconditions.checkState(mayBeExpression(value));
    name.addChildToFront(value);
    return var(name);
  }

  public static Node var(Node name) {
    Preconditions.checkState(name.isName());
    return new Node(Token.VAR, name);
  }

  public static Node returnNode() {
    return new Node(Token.RETURN);
  }

  public static Node returnNode(Node expr) {
    Preconditions.checkState(mayBeExpression(expr));
    return new Node(Token.RETURN, expr);
  }

  public static Node throwNode(Node expr) {
    Preconditions.checkState(mayBeExpression(expr));
    return new Node(Token.THROW, expr);
  }

  public static Node exprResult(Node expr) {
    Preconditions.checkState(mayBeExpression(expr));
    return new Node(Token.EXPR_RESULT, expr);
  }

  public static Node ifNode(Node cond, Node then) {
    Preconditions.checkState(mayBeExpression(cond));
    Preconditions.checkState(then.isBlock());
    return new Node(Token.IF, cond, then);
  }

  public static Node ifNode(Node cond, Node then, Node elseNode) {
    Preconditions.checkState(mayBeExpression(cond));
    Preconditions.checkState(then.isBlock());
    Preconditions.checkState(elseNode.isBlock());
    return new Node(Token.IF, cond, then, elseNode);
  }

  public static Node doNode(Node body, Node cond) {
    Preconditions.checkState(body.isBlock());
    Preconditions.checkState(mayBeExpression(cond));
    return new Node(Token.DO, body, cond);
  }

  public static Node forIn(Node target, Node cond, Node body) {
    Preconditions.checkState(target.isVar() || mayBeExpression(target));
    Preconditions.checkState(mayBeExpression(cond));
    Preconditions.checkState(body.isBlock());
    return new Node(Token.FOR, target, cond, body);
  }

  public static Node forNode(Node init, Node cond, Node incr, Node body) {
    Preconditions.checkState(init.isVar() || mayBeExpressionOrEmpty(init));
    Preconditions.checkState(mayBeExpressionOrEmpty(cond));
    Preconditions.checkState(mayBeExpressionOrEmpty(incr));
    Preconditions.checkState(body.isBlock());
    return new Node(Token.FOR, init, cond, incr, body);
  }

  public static Node switchNode(Node cond, Node ... cases) {
    Preconditions.checkState(mayBeExpression(cond));
    Node switchNode = new Node(Token.SWITCH, cond);
    for (Node caseNode : cases) {
      Preconditions.checkState(caseNode.isCase() || caseNode.isDefaultCase());
      switchNode.addChildToBack(caseNode);
    }
    return switchNode;
  }

  public static Node caseNode(Node expr, Node body) {
    Preconditions.checkState(mayBeExpression(expr));
    Preconditions.checkState(body.isBlock());
    body.putBooleanProp(Node.SYNTHETIC_BLOCK_PROP, true);
    return new Node(Token.CASE, expr, body);
  }

  public static Node defaultCase(Node body) {
    Preconditions.checkState(body.isBlock());
    body.putBooleanProp(Node.SYNTHETIC_BLOCK_PROP, true);
    return new Node(Token.DEFAULT_CASE, body);
  }

  public static Node label(Node name, Node stmt) {
    // TODO(johnlenz): additional validation here.
    Preconditions.checkState(name.isLabelName());
    Preconditions.checkState(mayBeStatement(stmt));
    Node block = new Node(Token.LABEL, name, stmt);
    return block;
  }

  public static Node labelName(String name) {
    Preconditions.checkState(!name.isEmpty());
    return Node.newString(Token.LABEL_NAME, name);
  }

  public static Node tryFinally(Node tryBody, Node finallyBody) {
    Preconditions.checkState(tryBody.isBlock());
    Preconditions.checkState(finallyBody.isBlock());
    Node catchBody = block().copyInformationFrom(tryBody);
    return new Node(Token.TRY, tryBody, catchBody, finallyBody);
  }

  public static Node tryCatch(Node tryBody, Node catchNode) {
    Preconditions.checkState(tryBody.isBlock());
    Preconditions.checkState(catchNode.isCatch());
    Node catchBody = blockUnchecked(catchNode).copyInformationFrom(catchNode);
    return new Node(Token.TRY, tryBody, catchBody);
  }

  public static Node tryCatchFinally(
      Node tryBody, Node catchNode, Node finallyBody) {
    Preconditions.checkState(finallyBody.isBlock());
    Node tryNode = tryCatch(tryBody, catchNode);
    tryNode.addChildToBack(finallyBody);
    return tryNode;
  }

  public static Node catchNode(Node expr, Node body) {
    Preconditions.checkState(expr.isName());
    Preconditions.checkState(body.isBlock());
    return new Node(Token.CATCH, expr, body);
  }

  public static Node breakNode() {
    return new Node(Token.BREAK);
  }

  public static Node breakNode(Node name) {
    // TODO(johnlenz): additional validation here.
    Preconditions.checkState(name.isLabelName());
    return new Node(Token.BREAK, name);
  }

  public static Node continueNode() {
    return new Node(Token.CONTINUE);
  }

  public static Node continueNode(Node name) {
    // TODO(johnlenz): additional validation here.
    Preconditions.checkState(name.isLabelName());
    return new Node(Token.CONTINUE, name);
  }


  //

  public static Node call(Node target, Node ... args) {
    Node call = new Node(Token.CALL, target);
    for (Node arg : args) {
      Preconditions.checkState(mayBeExpression(arg));
      call.addChildToBack(arg);
    }
    return call;
  }

  public static Node newNode(Node target, Node ... args) {
    Node newcall = new Node(Token.NEW, target);
    for (Node arg : args) {
      Preconditions.checkState(mayBeExpression(arg));
      newcall.addChildToBack(arg);
    }
    return newcall;
  }

  public static Node name(String name) {
    return Node.newString(Token.NAME, name);
  }

  public static Node getprop(Node target, Node prop) {
    Preconditions.checkState(mayBeExpression(target));
    Preconditions.checkState(prop.isString());
    return new Node(Token.GETPROP, target, prop);
  }

  public static Node getelem(Node target, Node elem) {
    Preconditions.checkState(mayBeExpression(target));
    Preconditions.checkState(mayBeExpression(elem));
    return new Node(Token.GETELEM, target, elem);
  }

  public static Node assign(Node target, Node expr) {
    Preconditions.checkState(isAssignmentTarget(target));
    Preconditions.checkState(mayBeExpression(expr));
    return new Node(Token.ASSIGN, target, expr);
  }

  public static Node hook(Node cond, Node trueval, Node falseval) {
    Preconditions.checkState(mayBeExpression(cond));
    Preconditions.checkState(mayBeExpression(trueval));
    Preconditions.checkState(mayBeExpression(falseval));
    return new Node(Token.HOOK, cond, trueval, falseval);
  }

  public static Node comma(Node expr1, Node expr2) {
    return binaryOp(Token.COMMA, expr1, expr2);
  }

  public static Node and(Node expr1, Node expr2) {
    return binaryOp(Token.AND, expr1, expr2);
  }

  public static Node or(Node expr1, Node expr2) {
    return binaryOp(Token.OR, expr1, expr2);
  }

  public static Node not(Node expr1) {
    return unaryOp(Token.NOT, expr1);
  }

  /**
   * "=="
   */
  public static Node eq(Node expr1, Node expr2) {
    return binaryOp(Token.EQ, expr1, expr2);
  }

  /**
   * "==="
   */
  public static Node sheq(Node expr1, Node expr2) {
    return binaryOp(Token.SHEQ, expr1, expr2);
  }

  public static Node voidNode(Node expr1) {
    return unaryOp(Token.VOID, expr1);
  }

  public static Node neg(Node expr1) {
    return unaryOp(Token.NEG, expr1);
  }

  public static Node pos(Node expr1) {
    return unaryOp(Token.POS, expr1);
  }

  public static Node cast(Node expr1) {
    return unaryOp(Token.CAST, expr1);
  }

  public static Node add(Node expr1, Node expr2) {
    return binaryOp(Token.ADD, expr1, expr2);
  }

  public static Node sub(Node expr1, Node expr2) {
    return binaryOp(Token.SUB, expr1, expr2);
  }

  // TODO(johnlenz): the rest of the ops

  // literals
  public static Node objectlit(Node ... propdefs) {
    Node objectlit = new Node(Token.OBJECTLIT);
    for (Node propdef : propdefs) {
      Preconditions.checkState(
          propdef.isStringKey() ||
          propdef.isGetterDef() || propdef.isSetterDef());
      Preconditions.checkState(propdef.hasOneChild());
      objectlit.addChildToBack(propdef);
    }
    return objectlit;
  }

  // TODO(johnlenz): quoted props

  public static Node propdef(Node string, Node value) {
    Preconditions.checkState(string.isStringKey());
    Preconditions.checkState(!string.hasChildren());
    Preconditions.checkState(mayBeExpression(value));
    string.addChildToFront(value);
    return string;
  }

  public static Node arraylit(Node ... exprs) {
    Node arraylit = new Node(Token.ARRAYLIT);
    for (Node expr : exprs) {
      Preconditions.checkState(mayBeExpressionOrEmpty(expr));
      arraylit.addChildToBack(expr);
    }
    return arraylit;
  }

  public static Node regexp(Node expr) {
    Preconditions.checkState(expr.isString());
    return new Node(Token.REGEXP, expr);
  }

  public static Node regexp(Node expr, Node flags) {
    Preconditions.checkState(expr.isString());
    Preconditions.checkState(flags.isString());
    return new Node(Token.REGEXP, expr, flags);
  }

  public static Node string(String s) {
    return Node.newString(s);
  }

  public static Node stringKey(String s) {
    return Node.newString(Token.STRING_KEY, s);
  }

  public static Node number(double d) {
    return Node.newNumber(d);
  }

  public static Node thisNode() {
    return new Node(Token.THIS);
  }

  public static Node trueNode() {
    return new Node(Token.TRUE);
  }

  public static Node falseNode() {
    return new Node(Token.FALSE);
  }

  public static Node nullNode() {
    return new Node(Token.NULL);
  }

  // helper methods

  private static Node binaryOp(int token, Node expr1, Node expr2) {
    Preconditions.checkState(mayBeExpression(expr1));
    Preconditions.checkState(mayBeExpression(expr2));
    return new Node(token, expr1, expr2);
  }

  private static Node unaryOp(int token, Node expr) {
    Preconditions.checkState(mayBeExpression(expr));
    return new Node(token, expr);
  }

  private static boolean mayBeExpressionOrEmpty(Node n) {
    return n.isEmpty() || mayBeExpression(n);
  }

  private static boolean isAssignmentTarget(Node n) {
    return n.isName() || n.isGetProp() || n.isGetElem();
  }

  // NOTE: some nodes are neither statements nor expression nodes:
  //   SCRIPT, LABEL_NAME, PARAM_LIST, CASE, DEFAULT_CASE, CATCH
  //   GETTER_DEF, SETTER_DEF

  /**
   * It isn't possible to always determine if a detached node is a expression,
   * so make a best guess.
   */
  private static boolean mayBeStatementNoReturn(Node n) {
    switch (n.getType()) {
      case Token.EMPTY:
      case Token.FUNCTION:
        // EMPTY and FUNCTION are used both in expression and statement
        // contexts
        return true;

      case Token.BLOCK:
      case Token.BREAK:
      case Token.CONST:
      case Token.CONTINUE:
      case Token.DEBUGGER:
      case Token.DO:
      case Token.EXPR_RESULT:
      case Token.FOR:
      case Token.IF:
      case Token.LABEL:
      case Token.SWITCH:
      case Token.THROW:
      case Token.TRY:
      case Token.VAR:
      case Token.WHILE:
      case Token.WITH:
        return true;

      default:
        return false;
    }
  }

  /**
   * It isn't possible to always determine if a detached node is a expression,
   * so make a best guess.
   */
  private static boolean mayBeStatement(Node n) {
    if (!mayBeStatementNoReturn(n)) {
      return n.isReturn();
    }
    return true;
  }

  /**
   * It isn't possible to always determine if a detached node is a expression,
   * so make a best guess.
   */
  private static boolean mayBeExpression(Node n) {
    switch (n.getType()) {
      case Token.FUNCTION:
        // FUNCTION is used both in expression and statement
        // contexts.
        return true;

      case Token.ADD:
      case Token.AND:
      case Token.ARRAYLIT:
      case Token.ASSIGN:
      case Token.ASSIGN_BITOR:
      case Token.ASSIGN_BITXOR:
      case Token.ASSIGN_BITAND:
      case Token.ASSIGN_LSH:
      case Token.ASSIGN_RSH:
      case Token.ASSIGN_URSH:
      case Token.ASSIGN_ADD:
      case Token.ASSIGN_SUB:
      case Token.ASSIGN_MUL:
      case Token.ASSIGN_DIV:
      case Token.ASSIGN_MOD:
      case Token.BITAND:
      case Token.BITOR:
      case Token.BITNOT:
      case Token.BITXOR:
      case Token.CALL:
      case Token.CAST:
      case Token.COMMA:
      case Token.DEC:
      case Token.DELPROP:
      case Token.DIV:
      case Token.EQ:
      case Token.FALSE:
      case Token.GE:
      case Token.GETPROP:
      case Token.GETELEM:
      case Token.GT:
      case Token.HOOK:
      case Token.IN:
      case Token.INC:
      case Token.INSTANCEOF:
      case Token.LE:
      case Token.LSH:
      case Token.LT:
      case Token.MOD:
      case Token.MUL:
      case Token.NAME:
      case Token.NE:
      case Token.NEG:
      case Token.NEW:
      case Token.NOT:
      case Token.NUMBER:
      case Token.NULL:
      case Token.OBJECTLIT:
      case Token.OR:
      case Token.POS:
      case Token.REGEXP:
      case Token.RSH:
      case Token.SHEQ:
      case Token.SHNE:
      case Token.STRING:
      case Token.SUB:
      case Token.THIS:
      case Token.TYPEOF:
      case Token.TRUE:
      case Token.URSH:
      case Token.VOID:
        return true;

      default:
        return false;
    }
  }
}
