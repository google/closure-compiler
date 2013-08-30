/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import org.mozilla.javascript.ast.*;

import java.util.List;

/**
 * After parsing is done and the jsdocs are collected, this class takes the AST
 * and the jsdocs and attaches the jsdocs to the appropriate AST nodes.
 * <p>
 * The jsdoc comments are not part of the syntax of JavaScript, so we cannot
 * give parse errors for misplaced jsdocs. We should not modify the grammar
 * recognized by the parser in order to attach jsdocs.
 * On the other hand, in Closure-style JavaScript the placement of jsdocs is
 * syntax-driven; there is intuitively some grammar that dictates where in the
 * AST each comment should be attached. In most cases, a comment C should be
 * attached to the topmost AST node that follows C in the source program.
 * For example, in this code:
 * some_jsdoc bar(baz);
 * the comment gets attached to the call node, not to bar.
 * There are some exceptions, eg, some infix expressions such as +:
 * some_jsdoc 2 + 3;
 * In this code, the comment gets attached to 2, not to +.
 * <p>
 * For legacy reasons, we distinguish between two operators with different
 * precedence rules.
 * CAST: a jsdoc followed by a left paren
 * JSDOC: a jsdoc not followed by a left paren
 * Some operators have higher precedence than JSDOC but lower than CAST;
 * see the method betweenJsdocAndCast.
 * For example, in this code
 * some_jsdoc obj.exp
 * the jsdoc is attached to GETPROP, and in this code
 * some_jsdoc (obj).exp
 * the jsdoc is attached to obj.
 * <p>
 * Algorithmically, we want to make a single pass over the AST and attach all
 * comments, not start from the root and search for each comment.
 * This is possible b/c the parser hands us the list of comments sorted by their
 * starting position. So, when we attach a comment C, we know that the next one
 * can't go to the nodes we already rejected for C.
 *
 */
public class AttachJsDocs {

  private Comment currentJsdoc;
  private int capos; // The absolute (starting) position of currentJsdoc
  private boolean attachOnlyToParen = false;
  // In some cases we recur to the left child of a node N looking for a paren.
  // If we don't find a paren, we save N's info here to attach the jsdoc to N.
  private AstNode possibleJsdocTarget;
  private int possibleApos;

  // A pair of a node and its absolute position
  private static class NodePos {
    AstNode n;
    int apos;

    NodePos(AstNode n, int apos) {
      this.n = n;
      this.apos = apos;
    }
  }

  /** @param napos The absolute position of n. */
  private boolean finishesAfterJsdoc(AstNode n, int napos) {
    return capos < napos + n.getLength() - 1;
  }

  /**
   * Returns the leftmost right sibling of n that satisfies finishesAfterJsdoc,
   * o/w null.
   * @param parapos The absolute position of n's parent.
   */
  private AstNode siblingFinishesAfterJsdoc(AstNode n, int parapos) {
    AstNode next = (AstNode) n.getNext();
    while (next != null) {
      if (finishesAfterJsdoc(next, parapos + next.getPosition())) {
        break;
      }
      next = (AstNode) next.getNext();
    }
    return next;
  }

  /** Attaches the comments to the AST. */
  public void attachComments(AstRoot root, List<Comment> comments) {
    if (comments == null) {
      return;
    }

    AstNode n = root;
    int napos = root.getPosition();
    int parapos = 0;
    int lastcapos = -1;

    // At each loop entry, n is some AST node, napos is its absolute position,
    // and parapos is the absolute position of its parent.
    for (Comment c : comments) {
      currentJsdoc = c;
      // The parser doesn't attach comments to nodes, so this call isn't slow.
      capos = currentJsdoc.getAbsolutePosition();
      // The list of comments should be sorted.
      if (capos <= lastcapos) {
        throw new RuntimeException("The list of jsdoc comments isn't sorted.");
      }
      lastcapos = capos;

      while (n != root) {
        // Check if the next comment should be attached to n.
        if (finishesAfterJsdoc(n, napos)) {
          break;
        }
        // Check if n's parent contains the comment. If so, check if it can be
        // attached to one of n's right siblings.
        AstNode tmp;
        tmp = n.getParent();
        if (tmp != null && finishesAfterJsdoc(tmp, parapos)) {
          tmp = siblingFinishesAfterJsdoc(n, parapos);
          if (tmp != null) {
            n = tmp;
            break;
          }
        }
        // Otherwise, go to n's parent and keep looking.
        n = n.getParent();
        napos = parapos;
        parapos = n == root ? 0 : napos - n.getPosition();
      }
      // At this point, n satisfies finishesAfterJsdoc, so the comment should be
      // attached to n or its descendants. This call never returns null.
      NodePos np = attachComment(n, parapos);
      n = np.n;
      napos = np.apos;
      parapos = n == root ? 0 : napos - n.getPosition();
    }
  }

  /**
   * Tries to attach currentJsdoc to n or one of its descendants.
   * If the jsdoc starts after n, we return null.
   * If the jsdoc is attached, we return the node on which it was attached.
   * Last, if it was contained in n but didn't get attached, we return some node
   * close to where the comment would get attached.
   *
   * The node that gets returned from this function is where the search for
   * attaching the next comment will start from.
   *
   * A comment never gets attached to a node that appears before it in the code.
   *
   * @param parapos n's parent's absolute position; we calculate it manually
   *        b/c it's slow to call getAbsolutePosition for every node.
   */
  private NodePos attachComment(AstNode n, int parapos) {
    int napos = parapos + n.getPosition();

    if (finishesAfterJsdoc(n, napos)) {
      if (capos < napos && !attachToChildren(n, napos)) {
        return setJsdoc(n, napos);
      }
      // n contains the jsdoc, recur to the children
    } else {
      return null;
    }

    int ntype = n.getType();
    NodePos res;

    // All the cases can assume that the jsdoc appears inside or before n,
    // not after n, so they all return a non-null result.
    switch (ntype) {
      // Statements first, then expressions.

      // We don't need to handle nodes that don't contain other nodes here; they
      // can't contain a jsdoc.
      // These are: FALSE, NAME, NULL, NUMBER, REGEXP, STRING, THIS, TRUE.

      case Token.BREAK:
      case Token.CONTINUE:
        return new NodePos(n, napos);

      case Token.CASE:
        SwitchCase cas = (SwitchCase) n;
        if (!cas.isDefault()) {
          res = attachComment(cas.getExpression(), napos);
          if (res != null) {
            return res;
          }
        }
        if (cas.getStatements() != null) {
          for (AstNode stm : cas.getStatements()) {
            res = attachComment(stm, napos);
            if (res != null) {
              return res;
            }
          }
        }
        return new NodePos(n, napos);

      case Token.DO:
        DoLoop dl = (DoLoop) n;
        res = attachComment(dl.getBody(), napos);
        if (res != null) {
          return res;
        }
        if (capos < dl.getWhilePosition()) {
            return new NodePos(n, napos);
        }
        res = attachComment(dl.getCondition(), napos);
        if (res != null) {
          return res;
        }
        return new NodePos(n, napos);

      case Token.EXPR_RESULT:
      case Token.EXPR_VOID:
        if (n instanceof ExpressionStatement) {
          res = attachComment(((ExpressionStatement) n).getExpression(), napos);
        } else /* instanceof LabeledStatement */ {
          res = attachComment(((LabeledStatement) n).getStatement(), napos);
        }
        if (res != null) {
          return res;
        }
        return new NodePos(n, napos);

      case Token.FOR:
        Loop loop = (Loop) n;
        if (n instanceof ForInLoop) {
          res = attachForInHeader((ForInLoop) loop, napos);
        } else {
          res = attachForLoopHeader((ForLoop) loop, napos);
        }
        if (res != null) {
          return res;
        }
        res = attachComment(loop.getBody(), napos);
        if (res != null) {
          return res;
        }
        return new NodePos(n, napos);

      case Token.IF:
        IfStatement ifstm = (IfStatement) n;
        res = attachComment(ifstm.getCondition(), napos);
        if (res != null) {
          return res;
        }
        res = attachComment(ifstm.getThenPart(), napos);
        if (res != null) {
          return res;
        }
        if (capos < ifstm.getElsePosition()) {
          return new NodePos(n, napos);
        }
        res = attachComment(ifstm.getElsePart(), napos);
        if (res != null) {
          return res;
        }
        return new NodePos(n, napos);

      case Token.FUNCTION:
        FunctionNode fun = (FunctionNode) n;
        Name nam = fun.getFunctionName();
        if (nam != null) {
          res = attachComment(nam, napos);
          if (res != null) {
            return res;
          }
        }
        for (AstNode param : fun.getParams()) {
          res = attachComment(param, napos);
          if (res != null) {
            return res;
          }
        }
        res = attachComment(fun.getBody(), napos);
        if (res != null) {
          return res;
        }
        return new NodePos(n, napos);

      case Token.RETURN:
        AstNode retValue = ((ReturnStatement) n).getReturnValue();
        if (retValue != null) {
          res = attachComment(retValue, napos);
          if (res != null) {
            return res;
          }
        }
        return new NodePos(n, napos);

      case Token.SWITCH:
        SwitchStatement sw = (SwitchStatement) n;
        res = attachComment(sw.getExpression(), napos);
        if (res != null) {
          return res;
        }
        for (SwitchCase c : sw.getCases()) {
          res = attachComment(c, napos);
        }
        return new NodePos(n, napos);

      case Token.THROW:
        res = attachComment(((ThrowStatement ) n).getExpression(), napos);
        if (res != null) {
          return res;
        }
        return new NodePos(n, napos);

      case Token.TRY:
        TryStatement t = (TryStatement) n;
        res = attachComment(t.getTryBlock(), napos);
        if (res != null) {
          return res;
        }
        for (CatchClause cc : t.getCatchClauses()) {
          int catchstart = napos + cc.getPosition();
          if (capos < catchstart) {
            return new NodePos(n, napos);
          }
          res = attachComment(cc.getVarName(), catchstart);
          if (res != null) {
            return res;
          }
          res = attachComment(cc.getBody(), catchstart);
          if (res != null) {
            return res;
          }
        }
        int finpos = t.getFinallyPosition();
        if (finpos != -1) {
          if (capos < finpos) {
            return new NodePos(n, napos);
          }
          res = attachComment(t.getFinallyBlock(), napos);
          if (res != null) {
            return res;
          }
        }
        return new NodePos(n, napos);

      case Token.VAR:
        if (n instanceof VariableDeclaration) {
          for (VariableInitializer vi :
                   ((VariableDeclaration) n).getVariables()) {
            res = attachComment(vi, napos);
            if (res != null) {
              return res;
            }
          }
        } else /* n instanceof VariableInitializer */{
          res = attachComment(((VariableInitializer) n).getInitializer(),
                              napos);
          if (res != null) {
            return res;
          }
        }
        return new NodePos(n, napos);

      case Token.WHILE:
        WhileLoop wh = (WhileLoop) n;
        res = attachComment(wh.getCondition(), napos);
        if (res != null) {
          return res;
        }
        if (capos < wh.getRp()) {
          return new NodePos(n, napos);
        }
        res = attachComment(wh.getBody(), napos);
        if (res != null) {
          return res;
        }
        return new NodePos(n, napos);

      case Token.WITH:
        WithStatement w = (WithStatement) n;
        res = attachComment(w.getExpression(), napos);
        if (res != null) {
          return res;
        }
        if (capos < w.getRp()) {
          return new NodePos(n, napos);
        }
        res = attachComment(w.getStatement(), napos);
        if (res != null) {
          return res;
        }
        return new NodePos(n, napos);

      case Token.ADD:
      case Token.AND:
      case Token.ASSIGN:
      case Token.ASSIGN_ADD:
      case Token.ASSIGN_BITAND:
      case Token.ASSIGN_BITOR:
      case Token.ASSIGN_BITXOR:
      case Token.ASSIGN_DIV:
      case Token.ASSIGN_LSH:
      case Token.ASSIGN_MOD:
      case Token.ASSIGN_MUL:
      case Token.ASSIGN_RSH:
      case Token.ASSIGN_SUB:
      case Token.ASSIGN_URSH:
      case Token.BITAND:
      case Token.BITOR:
      case Token.BITXOR:
      case Token.COLON:
      case Token.COMMA:
      case Token.DIV:
      case Token.EQ:
      case Token.GE:
      case Token.GET:
      case Token.GETPROP:
      case Token.GT:
      case Token.IN:
      case Token.INSTANCEOF:
      case Token.LE:
      case Token.LSH:
      case Token.LT:
      case Token.MOD:
      case Token.MUL:
      case Token.NE:
      case Token.OR:
      case Token.RSH:
      case Token.SET:
      case Token.SHEQ:
      case Token.SHNE:
      case Token.SUB:
      case Token.URSH:
        InfixExpression ie = (InfixExpression) n;
        res = attachComment(ie.getLeft(), napos);
        if (res != null) {
          return res;
        }
        if (capos < ie.getOperatorPosition()) {
          return new NodePos(n, napos);
        }
        res = attachComment(ie.getRight(), napos);
        if (res != null) {
          return res;
        }
        return new NodePos(n, napos);

      case Token.ARRAYLIT:
        for (AstNode elm : ((ArrayLiteral) n).getElements()) {
          res = attachComment(elm, napos);
          if (res != null) {
            return res;
          }
        }
        return new NodePos(n, napos);

      case Token.BITNOT:
      case Token.DEC:
      case Token.DELPROP:
      case Token.INC:
      case Token.NEG:
      case Token.NOT:
      case Token.POS:
      case Token.TYPEOF:
      case Token.VOID:
        UnaryExpression ue = (UnaryExpression) n;
        res = attachComment(ue.getOperand(), napos);
        if (res != null) {
          return res;
        }
        return new NodePos(n, napos);

      case Token.CALL:
      case Token.NEW:
        FunctionCall call = (FunctionCall) n;
        res = attachComment(call.getTarget(), napos);
        if (res != null) {
          return res;
        }
        if (capos < call.getLp()) {
          return new NodePos(n, napos);
        }
        for (AstNode param : call.getArguments()) {
          res = attachComment(param, napos);
          if (res != null) {
            return res;
          }
        }
        return new NodePos(n, napos);

      case Token.GETELEM:
        ElementGet elm = (ElementGet) n;
        res = attachComment(elm.getTarget(), napos);
        if (res != null) {
          return res;
        }
        if (capos < elm.getLb()) {
          return new NodePos(n, napos);
        }
        res = attachComment(elm.getElement(), napos);
        if (res != null) {
          return res;
        }
        return new NodePos(n, napos);

      case Token.HOOK:
        ConditionalExpression hook = (ConditionalExpression) n;
        res = attachComment(hook.getTestExpression(), napos);
        if (res != null) {
          return res;
        }
        if (capos < hook.getQuestionMarkPosition()) {
          return new NodePos(n, napos);
        }
        res = attachComment(hook.getTrueExpression(), napos);
        if (res != null) {
          return res;
        }
        if (capos < hook.getColonPosition()) {
          return new NodePos(n, napos);
        }
        res = attachComment(hook.getFalseExpression(), napos);
        if (res != null) {
          return res;
        }
        return new NodePos(n, napos);

      case Token.LP:
        res = attachComment(((ParenthesizedExpression) n).getExpression(),
                            napos);
        if (res != null) {
          return res;
        }
        return new NodePos(n, napos);

      case Token.OBJECTLIT:
        for (InfixExpression prop : ((ObjectLiteral) n).getElements()) {
          res = attachComment(prop, napos);
          if (res != null) {
            return res;
          }
        }
        return new NodePos(n, napos);

      case Token.BLOCK:
      case Token.SCRIPT:
        AstNode kid = (AstNode) n.getFirstChild();
        while (kid != null) {
          res = attachComment(kid, napos);
          if (res != null) {
            return res;
          }
          kid = (AstNode) kid.getNext();
        }
        return new NodePos(n, napos);

      default:
        throw new RuntimeException(
            "Can't attach jsdoc to unknown node: " + ntype);
    }
  }

  private NodePos attachForInHeader(ForInLoop fi, int fipos) {
    if (capos < fi.getLp()) {
      return new NodePos(fi, fipos);
    }
    NodePos res = attachComment(fi.getIterator(), fipos);
    if (res != null) {
      return res;
    }
    if (capos < fi.getInPosition()) {
      return new NodePos(fi, fipos);
    }
    res = attachComment(fi.getIteratedObject(), fipos);
    if (res != null) {
      return res;
    }
    if (capos < fi.getRp()) {
      return new NodePos(fi, fipos);
    }
    return null;
  }

  private NodePos attachForLoopHeader(ForLoop fl, int flpos) {
    if (capos < fl.getLp()) {
      return new NodePos(fl, flpos);
    }
    NodePos res = attachComment(fl.getInitializer(), flpos);
    if (res != null) {
      return res;
    }
    res = attachComment(fl.getCondition(), flpos);
    if (res != null) {
      return res;
    }
    res = attachComment(fl.getIncrement(), flpos);
    if (res != null) {
      return res;
    }
    if (capos < fl.getRp()) {
      return new NodePos(fl, flpos);
    }
    return null;
  }

  // Returns true iff n binds tighter than non-cast jsdocs & looser than casts.
  private boolean betweenJsdocAndCast(AstNode n) {
    int ntype = n.getType();
    return (ntype == Token.GETPROP || ntype == Token.GETELEM ||
        ntype == Token.CALL ||
        n instanceof Assignment ||
        ((ntype == Token.INC || ntype == Token.DEC) &&
            ((UnaryExpression) n).isPostfix()));
  }

  private boolean attachToChildren(AstNode n, int napos) {
    int ntype = n.getType();
    if (betweenJsdocAndCast(n)) {
      if (!attachOnlyToParen) {
        attachOnlyToParen = true;
        possibleJsdocTarget = n;
        possibleApos = napos;
      }
      return true;
    }
    if (ntype == Token.SCRIPT ||
        ntype == Token.HOOK ||
        ntype == Token.RETURN ||
        n instanceof ExpressionStatement ||
        n instanceof InfixExpression) {
      return true;
    }
    return false;
  }

  private NodePos setJsdoc(AstNode n, int napos) {
    int ntype = n.getType();

    if (attachOnlyToParen) {
      attachOnlyToParen = false;
      if (ntype != Token.LP) {
        possibleJsdocTarget.setJsDocNode(currentJsdoc);
        return new NodePos(possibleJsdocTarget, possibleApos);
      }
    } else {
      switch (ntype) {
        // For most stms, it doesn't make sense to attach a comment on them.
        case Token.BLOCK:
        case Token.CASE:
        case Token.DO:
        case Token.FOR:
        case Token.IF:
        case Token.SWITCH:
        case Token.THROW:
        case Token.WHILE:
          return new NodePos(n, napos);

        case Token.VAR:
          if (n instanceof VariableInitializer) {
            AstNode n2 = ((VariableInitializer) n).getTarget();
            n2.setJsDocNode(currentJsdoc);
            return new NodePos(n2, napos + n2.getPosition());
          }
          break;
      }
    }
    n.setJsDocNode(currentJsdoc);
    return new NodePos(n, napos);
  }
}
