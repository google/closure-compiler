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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Preconditions;
import java.util.List;

/**
 * An AST construction helper class
 *
 * @author johnlenz@google.com (John Lenz)
 */
public class IR {

  private IR() {}

  public static Node empty() {
    return new Node(Token.EMPTY);
  }

  public static Node export(Node declaration) {
    return new Node(Token.EXPORT, declaration);
  }

  public static Node importNode(Node name, Node importSpecs, Node moduleIdentifier) {
    checkState(name.isName() || name.isEmpty(), name);
    checkState(
        importSpecs.isImportSpec() || importSpecs.isImportStar() || importSpecs.isEmpty(),
        importSpecs);
    checkState(moduleIdentifier.isString(), moduleIdentifier);
    return new Node(Token.IMPORT, name, importSpecs, moduleIdentifier);
  }

  public static Node importStar(String name) {
    return Node.newString(Token.IMPORT_STAR, name);
  }

  public static Node function(Node name, Node params, Node body) {
    checkState(name.isName());
    checkState(params.isParamList());
    checkState(body.isBlock());
    return new Node(Token.FUNCTION, name, params, body);
  }

  public static Node arrowFunction(Node name, Node params, Node body) {
    checkState(name.isName());
    checkState(params.isParamList());
    checkState(body.isBlock() || mayBeExpression(body));
    Node func = new Node(Token.FUNCTION, name, params, body);
    func.setIsArrowFunction(true);
    return func;
  }

  public static Node paramList() {
    return new Node(Token.PARAM_LIST);
  }

  public static Node paramList(Node param) {
    checkState(param.isName() || param.isRest());
    return new Node(Token.PARAM_LIST, param);
  }

  public static Node paramList(Node... params) {
    Node paramList = paramList();
    for (Node param : params) {
      checkState(param.isName() || param.isRest());
      paramList.addChildToBack(param);
    }
    return paramList;
  }

  public static Node root(Node ... rootChildren) {
    Node root = new Node(Token.ROOT);
    for (Node child : rootChildren) {
      checkState(child.getToken() == Token.ROOT || child.getToken() == Token.SCRIPT);
      root.addChildToBack(child);
    }
    return root;
  }

  public static Node block() {
    Node block = new Node(Token.BLOCK);
    return block;
  }

  public static Node block(Node stmt) {
    checkState(mayBeStatement(stmt), "Block node cannot contain %s", stmt.getToken());
    Node block = new Node(Token.BLOCK, stmt);
    return block;
  }

  public static Node block(Node ... stmts) {
    Node block = block();
    for (Node stmt : stmts) {
      checkState(mayBeStatement(stmt));
      block.addChildToBack(stmt);
    }
    return block;
  }

  public static Node block(List<Node> stmts) {
    Node paramList = block();
    for (Node stmt : stmts) {
      checkState(mayBeStatement(stmt));
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
      checkState(mayBeStatementNoReturn(stmt));
      block.addChildToBack(stmt);
    }
    return block;
  }

  public static Node script(List<Node> stmts) {
    Node paramList = script();
    for (Node stmt : stmts) {
      checkState(mayBeStatementNoReturn(stmt));
      paramList.addChildToBack(stmt);
    }
    return paramList;
  }

  public static Node var(Node lhs, Node value) {
    return declaration(lhs, value, Token.VAR);
  }

  public static Node var(Node lhs) {
    return declaration(lhs, Token.VAR);
  }

  public static Node let(Node lhs, Node value) {
    return declaration(lhs, value, Token.LET);
  }

  public static Node constNode(Node lhs, Node value) {
    return declaration(lhs, value, Token.CONST);
  }

  public static Node declaration(Node lhs, Token type) {
    checkState(lhs.isName() || lhs.isDestructuringPattern() || lhs.isDestructuringLhs(), lhs);
    if (lhs.isDestructuringPattern()) {
      lhs = new Node(Token.DESTRUCTURING_LHS, lhs);
    }
    return new Node(type, lhs);
  }

  public static Node declaration(Node lhs, Node value, Token type) {
    if (lhs.isName()) {
      checkState(!lhs.hasChildren());
    } else {
      checkState(lhs.isArrayPattern() || lhs.isObjectPattern());
      lhs = new Node(Token.DESTRUCTURING_LHS, lhs);
    }
    Preconditions.checkState(mayBeExpression(value),
        "%s can't be an expression", value);

    lhs.addChildToBack(value);
    return new Node(type, lhs);
  }

  public static Node returnNode() {
    return new Node(Token.RETURN);
  }

  public static Node returnNode(Node expr) {
    checkState(mayBeExpression(expr));
    return new Node(Token.RETURN, expr);
  }

  public static Node yield() {
    return new Node(Token.YIELD);
  }

  public static Node yield(Node expr) {
    checkState(mayBeExpression(expr));
    return new Node(Token.YIELD, expr);
  }

  public static Node await(Node expr) {
    checkState(mayBeExpression(expr));
    return new Node(Token.AWAIT, expr);
  }

  public static Node throwNode(Node expr) {
    checkState(mayBeExpression(expr));
    return new Node(Token.THROW, expr);
  }

  public static Node exprResult(Node expr) {
    checkState(mayBeExpression(expr), expr);
    return new Node(Token.EXPR_RESULT, expr);
  }

  public static Node ifNode(Node cond, Node then) {
    checkState(mayBeExpression(cond));
    checkState(then.isBlock());
    return new Node(Token.IF, cond, then);
  }

  public static Node ifNode(Node cond, Node then, Node elseNode) {
    checkState(mayBeExpression(cond));
    checkState(then.isBlock());
    checkState(elseNode.isBlock());
    return new Node(Token.IF, cond, then, elseNode);
  }

  public static Node doNode(Node body, Node cond) {
    checkState(body.isBlock());
    checkState(mayBeExpression(cond));
    return new Node(Token.DO, body, cond);
  }

  public static Node whileNode(Node cond, Node body) {
    checkState(body.isBlock());
    checkState(mayBeExpression(cond));
    return new Node(Token.WHILE, cond, body);
  }

  public static Node forIn(Node target, Node cond, Node body) {
    checkState(target.isVar() || mayBeExpression(target));
    checkState(mayBeExpression(cond));
    checkState(body.isBlock());
    return new Node(Token.FOR_IN, target, cond, body);
  }

  public static Node forNode(Node init, Node cond, Node incr, Node body) {
    checkState(init.isVar() || init.isLet() || init.isConst() || mayBeExpressionOrEmpty(init));
    checkState(mayBeExpressionOrEmpty(cond));
    checkState(mayBeExpressionOrEmpty(incr));
    checkState(body.isBlock());
    return new Node(Token.FOR, init, cond, incr, body);
  }

  public static Node switchNode(Node cond, Node ... cases) {
    checkState(mayBeExpression(cond));
    Node switchNode = new Node(Token.SWITCH, cond);
    for (Node caseNode : cases) {
      checkState(caseNode.isCase() || caseNode.isDefaultCase());
      switchNode.addChildToBack(caseNode);
    }
    return switchNode;
  }

  public static Node caseNode(Node expr, Node body) {
    checkState(mayBeExpression(expr));
    checkState(body.isBlock());
    body.setIsAddedBlock(true);
    return new Node(Token.CASE, expr, body);
  }

  public static Node defaultCase(Node body) {
    checkState(body.isBlock());
    body.setIsAddedBlock(true);
    return new Node(Token.DEFAULT_CASE, body);
  }

  public static Node label(Node name, Node stmt) {
    // TODO(johnlenz): additional validation here.
    checkState(name.isLabelName());
    checkState(mayBeStatement(stmt));
    Node block = new Node(Token.LABEL, name, stmt);
    return block;
  }

  public static Node labelName(String name) {
    checkState(!name.isEmpty());
    return Node.newString(Token.LABEL_NAME, name);
  }

  public static Node tryFinally(Node tryBody, Node finallyBody) {
    checkState(tryBody.isBlock());
    checkState(finallyBody.isBlock());
    Node catchBody = block().useSourceInfoIfMissingFrom(tryBody);
    return new Node(Token.TRY, tryBody, catchBody, finallyBody);
  }

  public static Node tryCatch(Node tryBody, Node catchNode) {
    checkState(tryBody.isBlock());
    checkState(catchNode.isCatch());
    Node catchBody = blockUnchecked(catchNode).useSourceInfoIfMissingFrom(catchNode);
    return new Node(Token.TRY, tryBody, catchBody);
  }

  public static Node tryCatchFinally(
      Node tryBody, Node catchNode, Node finallyBody) {
    checkState(finallyBody.isBlock());
    Node tryNode = tryCatch(tryBody, catchNode);
    tryNode.addChildToBack(finallyBody);
    return tryNode;
  }

  public static Node catchNode(Node expr, Node body) {
    checkState(expr.isName());
    checkState(body.isBlock());
    return new Node(Token.CATCH, expr, body);
  }

  public static Node breakNode() {
    return new Node(Token.BREAK);
  }

  public static Node breakNode(Node name) {
    // TODO(johnlenz): additional validation here.
    checkState(name.isLabelName());
    return new Node(Token.BREAK, name);
  }

  public static Node continueNode() {
    return new Node(Token.CONTINUE);
  }

  public static Node continueNode(Node name) {
    // TODO(johnlenz): additional validation here.
    checkState(name.isLabelName());
    return new Node(Token.CONTINUE, name);
  }

  public static Node call(Node target, Node ... args) {
    Node call = new Node(Token.CALL, target);
    for (Node arg : args) {
      checkState(mayBeExpression(arg), arg);
      call.addChildToBack(arg);
    }
    return call;
  }

  public static Node newNode(Node target, Node ... args) {
    Node newcall = new Node(Token.NEW, target);
    for (Node arg : args) {
      checkState(mayBeExpression(arg));
      newcall.addChildToBack(arg);
    }
    return newcall;
  }

  public static Node name(String name) {
    Preconditions.checkState(name.indexOf('.') == -1,
        "Invalid name '%s'. Did you mean to use NodeUtil.newQName?", name);
    return Node.newString(Token.NAME, name);
  }

  public static Node getprop(Node target, Node prop) {
    checkState(mayBeExpression(target));
    checkState(prop.isString());
    return new Node(Token.GETPROP, target, prop);
  }

  public static Node getprop(Node target, Node prop, Node ...moreProps) {
    checkState(mayBeExpression(target));
    checkState(prop.isString());
    Node result = new Node(Token.GETPROP, target, prop);
    for (Node moreProp : moreProps) {
      checkState(moreProp.isString());
      result = new Node(Token.GETPROP, result, moreProp);
    }
    return result;
  }

  public static Node getprop(Node target, String prop, String ...moreProps) {
    checkState(mayBeExpression(target));
    Node result = new Node(Token.GETPROP, target, IR.string(prop));
    for (String moreProp : moreProps) {
      result = new Node(Token.GETPROP, result, IR.string(moreProp));
    }
    return result;
  }

  public static Node getelem(Node target, Node elem) {
    checkState(mayBeExpression(target));
    checkState(mayBeExpression(elem));
    return new Node(Token.GETELEM, target, elem);
  }

  public static Node delprop(Node target) {
    checkState(mayBeExpression(target));
    return new Node(Token.DELPROP, target);
  }

  public static Node assign(Node target, Node expr) {
    checkState(target.isValidAssignmentTarget(), target);
    checkState(mayBeExpression(expr), expr);
    return new Node(Token.ASSIGN, target, expr);
  }

  public static Node hook(Node cond, Node trueval, Node falseval) {
    checkState(mayBeExpression(cond));
    checkState(mayBeExpression(trueval));
    checkState(mayBeExpression(falseval));
    return new Node(Token.HOOK, cond, trueval, falseval);
  }

  public static Node in(Node expr1, Node expr2) {
    return binaryOp(Token.IN, expr1, expr2);
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
   * "&lt;"
   */
  public static Node lt(Node expr1, Node expr2) {
    return binaryOp(Token.LT, expr1, expr2);
  }

  /**
   * "=="
   */
  public static Node eq(Node expr1, Node expr2) {
    return binaryOp(Token.EQ, expr1, expr2);
  }

  /**
   * "!="
   */
  public static Node ne(Node expr1, Node expr2) {
    return binaryOp(Token.NE, expr1, expr2);
  }

  /**
   * "==="
   */
  public static Node sheq(Node expr1, Node expr2) {
    return binaryOp(Token.SHEQ, expr1, expr2);
  }

  /**
   * "!=="
   */
  public static Node shne(Node expr1, Node expr2) {
    return binaryOp(Token.SHNE, expr1, expr2);
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

  public static Node cast(Node expr1, JSDocInfo jsdoc) {
    Node op = unaryOp(Token.CAST, expr1);
    op.setJSDocInfo(jsdoc);
    return op;
  }

  public static Node inc(Node exp, boolean isPost) {
    Node op = unaryOp(Token.INC, exp);
    op.putBooleanProp(Node.INCRDECR_PROP, isPost);
    return op;
  }

  public static Node dec(Node exp, boolean isPost) {
    Node op = unaryOp(Token.DEC, exp);
    op.putBooleanProp(Node.INCRDECR_PROP, isPost);
    return op;
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
      checkState(
          propdef.isStringKey()
              || propdef.isMemberFunctionDef()
              || propdef.isGetterDef()
              || propdef.isSetterDef());
      if (!propdef.isStringKey()) {
        checkState(propdef.hasOneChild());
      }
      objectlit.addChildToBack(propdef);
    }
    return objectlit;
  }

  public static Node objectPattern(Node... keys) {
    Node objectPattern = new Node(Token.OBJECT_PATTERN);
    for (Node key : keys) {
      checkState(key.isStringKey() || key.isComputedProp() || key.isRest());
      objectPattern.addChildToBack(key);
    }
    return objectPattern;
  }

  public static Node arrayPattern(Node... keys) {
    Node arrayPattern = new Node(Token.ARRAY_PATTERN);
    for (Node key : keys) {
      checkState(key.isRest() || key.isValidAssignmentTarget());
      arrayPattern.addChildToBack(key);
    }
    return arrayPattern;
  }

  public static Node computedProp(Node key, Node value) {
    checkState(mayBeExpression(key), key);
    checkState(mayBeExpression(value), value);
    return new Node(Token.COMPUTED_PROP, key, value);
  }

  public static Node propdef(Node string, Node value) {
    checkState(string.isStringKey());
    checkState(!string.hasChildren());
    checkState(mayBeExpression(value));
    string.addChildToFront(value);
    return string;
  }

  public static Node arraylit(Node ... exprs) {
    Node arraylit = new Node(Token.ARRAYLIT);
    for (Node expr : exprs) {
      checkState(mayBeExpressionOrEmpty(expr));
      arraylit.addChildToBack(expr);
    }
    return arraylit;
  }

  public static Node regexp(Node expr) {
    checkState(expr.isString());
    return new Node(Token.REGEXP, expr);
  }

  public static Node regexp(Node expr, Node flags) {
    checkState(expr.isString());
    checkState(flags.isString());
    return new Node(Token.REGEXP, expr, flags);
  }

  public static Node string(String s) {
    return Node.newString(s);
  }

  public static Node stringKey(String s) {
    return Node.newString(Token.STRING_KEY, s);
  }

  public static Node stringKey(String s, Node value) {
    checkState(mayBeExpression(value));
    Node stringKey = stringKey(s);
    stringKey.addChildToFront(value);
    return stringKey;
  }

  public static Node quotedStringKey(String s, Node value) {
    Node k = stringKey(s, value);
    k.putBooleanProp(Node.QUOTED_PROP, true);
    return k;
  }

  public static Node rest(Node target) {
    checkState(target.isValidAssignmentTarget(), target);
    return new Node(Token.REST, target);
  }

  public static Node spread(Node expr) {
    checkState(mayBeExpression(expr));
    return new Node(Token.SPREAD, expr);
  }

  public static Node superNode() {
    return new Node(Token.SUPER);
  }

  public static Node memberFunctionDef(String name, Node function) {
    checkState(function.isFunction());
    Node member = Node.newString(Token.MEMBER_FUNCTION_DEF, name);
    member.addChildToBack(function);
    return member;
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

  public static Node typeof(Node expr) {
    return unaryOp(Token.TYPEOF, expr);
  }

  // helper methods

  private static Node binaryOp(Token token, Node expr1, Node expr2) {
    checkState(mayBeExpression(expr1), expr1);
    checkState(mayBeExpression(expr2), expr2);
    return new Node(token, expr1, expr2);
  }

  private static Node unaryOp(Token token, Node expr) {
    checkState(mayBeExpression(expr));
    return new Node(token, expr);
  }

  private static boolean mayBeExpressionOrEmpty(Node n) {
    return n.isEmpty() || mayBeExpression(n);
  }

  // NOTE: some nodes are neither statements nor expression nodes:
  //   SCRIPT, LABEL_NAME, PARAM_LIST, CASE, DEFAULT_CASE, CATCH
  //   GETTER_DEF, SETTER_DEF

  /**
   * It isn't possible to always determine if a detached node is a expression,
   * so make a best guess.
   */
  private static boolean mayBeStatementNoReturn(Node n) {
    switch (n.getToken()) {
      case EMPTY:
      case FUNCTION:
        // EMPTY and FUNCTION are used both in expression and statement
        // contexts
        return true;

      case BLOCK:
      case BREAK:
      case CLASS:
      case CONST:
      case CONTINUE:
      case DEBUGGER:
      case DO:
      case EXPR_RESULT:
      case FOR:
      case FOR_IN:
      case FOR_OF:
      case FOR_AWAIT_OF:
      case IF:
      case LABEL:
      case LET:
      case SWITCH:
      case THROW:
      case TRY:
      case VAR:
      case WHILE:
      case WITH:
        return true;

      default:
        return false;
    }
  }

  /**
   * It isn't possible to always determine if a detached node is a expression,
   * so make a best guess.
   */
  public static boolean mayBeStatement(Node n) {
    if (!mayBeStatementNoReturn(n)) {
      return n.isReturn();
    }
    return true;
  }

  /**
   * It isn't possible to always determine if a detached node is a expression,
   * so make a best guess.
   */
  public static boolean mayBeExpression(Node n) {
    switch (n.getToken()) {
      case FUNCTION:
      case CLASS:
        // FUNCTION and CLASS are used both in expression and statement
        // contexts.
        return true;

      case ADD:
      case AND:
      case ARRAYLIT:
      case ASSIGN:
      case ASSIGN_BITOR:
      case ASSIGN_BITXOR:
      case ASSIGN_BITAND:
      case ASSIGN_LSH:
      case ASSIGN_RSH:
      case ASSIGN_URSH:
      case ASSIGN_ADD:
      case ASSIGN_SUB:
      case ASSIGN_MUL:
      case ASSIGN_EXPONENT:
      case ASSIGN_DIV:
      case ASSIGN_MOD:
      case AWAIT:
      case BITAND:
      case BITOR:
      case BITNOT:
      case BITXOR:
      case CALL:
      case CAST:
      case COMMA:
      case DEC:
      case DELPROP:
      case DIV:
      case EQ:
      case EXPONENT:
      case FALSE:
      case GE:
      case GETPROP:
      case GETELEM:
      case GT:
      case HOOK:
      case IN:
      case INC:
      case INSTANCEOF:
      case LE:
      case LSH:
      case LT:
      case MOD:
      case MUL:
      case NAME:
      case NE:
      case NEG:
      case NEW:
      case NEW_TARGET:
      case NOT:
      case NUMBER:
      case NULL:
      case OBJECTLIT:
      case OR:
      case POS:
      case REGEXP:
      case RSH:
      case SHEQ:
      case SHNE:
      case SPREAD:
      case STRING:
      case SUB:
      case SUPER:
      case TEMPLATELIT:
      case TAGGED_TEMPLATELIT:
      case THIS:
      case TYPEOF:
      case TRUE:
      case URSH:
      case VOID:
      case YIELD:
        return true;

      default:
        return false;
    }
  }
}
