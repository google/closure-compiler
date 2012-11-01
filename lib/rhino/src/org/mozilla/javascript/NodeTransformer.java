/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.FunctionNode;
import org.mozilla.javascript.ast.Jump;
import org.mozilla.javascript.ast.Scope;
import org.mozilla.javascript.ast.ScriptNode;

import java.util.ArrayList;
import java.util.List;

/**
 * This class transforms a tree to a lower-level representation for codegen.
 *
 * @see Node
 */

public class NodeTransformer
{

    public NodeTransformer()
    {
    }

    public final void transform(ScriptNode tree)
    {
        transformCompilationUnit(tree);
        for (int i = 0; i != tree.getFunctionCount(); ++i) {
            FunctionNode fn = tree.getFunctionNode(i);
            transform(fn);
        }
    }

    private void transformCompilationUnit(ScriptNode tree)
    {
        loops = new ObjArray();
        loopEnds = new ObjArray();

        // to save against upchecks if no finally blocks are used.
        hasFinally = false;

        // Flatten all only if we are not using scope objects for block scope
        boolean createScopeObjects = tree.getType() != Token.FUNCTION ||
                                  ((FunctionNode)tree).requiresActivation();
        tree.flattenSymbolTable(!createScopeObjects);

        //uncomment to print tree before transformation
        if (Token.printTrees) System.out.println(tree.toStringTree(tree));
        boolean inStrictMode = tree instanceof AstRoot &&
                               ((AstRoot)tree).isInStrictMode();
        transformCompilationUnit_r(tree, tree, tree, createScopeObjects,
                                   inStrictMode);
    }

    private void transformCompilationUnit_r(final ScriptNode tree,
                                            final Node parent,
                                            Scope scope,
                                            boolean createScopeObjects,
                                            boolean inStrictMode)
    {
        Node node = null;
      siblingLoop:
        for (;;) {
            Node previous = null;
            if (node == null) {
                node = parent.getFirstChild();
            } else {
                previous = node;
                node = node.getNext();
            }
            if (node == null) {
                break;
            }

            int type = node.getType();
            if (createScopeObjects &&
                (type == Token.BLOCK || type == Token.LOOP ||
                 type == Token.ARRAYCOMP) &&
                (node instanceof Scope))
            {
                Scope newScope = (Scope) node;
                if (newScope.getSymbolTable() != null) {
                    // transform to let statement so we get a with statement
                    // created to contain scoped let variables
                    Node let = new Node(type == Token.ARRAYCOMP ? Token.LETEXPR
                                                                : Token.LET);
                    Node innerLet = new Node(Token.LET);
                    let.addChildToBack(innerLet);
                    for (String name: newScope.getSymbolTable().keySet()) {
                        innerLet.addChildToBack(Node.newString(Token.NAME, name));
                    }
                    newScope.setSymbolTable(null); // so we don't transform again
                    Node oldNode = node;
                    node = replaceCurrent(parent, previous, node, let);
                    type = node.getType();
                    let.addChildToBack(oldNode);
                }
            }

            switch (type) {

              case Token.LABEL:
              case Token.SWITCH:
              case Token.LOOP:
                loops.push(node);
                loopEnds.push(((Jump)node).target);
                break;

              case Token.WITH:
              {
                loops.push(node);
                Node leave = node.getNext();
                if (leave.getType() != Token.LEAVEWITH) {
                    Kit.codeBug();
                }
                loopEnds.push(leave);
                break;
              }

              case Token.TRY:
              {
                Jump jump = (Jump)node;
                Node finallytarget = jump.getFinally();
                if (finallytarget != null) {
                    hasFinally = true;
                    loops.push(node);
                    loopEnds.push(finallytarget);
                }
                break;
              }

              case Token.TARGET:
              case Token.LEAVEWITH:
                if (!loopEnds.isEmpty() && loopEnds.peek() == node) {
                    loopEnds.pop();
                    loops.pop();
                }
                break;

              case Token.YIELD:
                ((FunctionNode)tree).addResumptionPoint(node);
                break;

              case Token.RETURN:
              {
                boolean isGenerator = tree.getType() == Token.FUNCTION
                    && ((FunctionNode)tree).isGenerator();
                if (isGenerator) {
                    node.putIntProp(Node.GENERATOR_END_PROP, 1);
                }
                /* If we didn't support try/finally, it wouldn't be
                 * necessary to put LEAVEWITH nodes here... but as
                 * we do need a series of JSR FINALLY nodes before
                 * each RETURN, we need to ensure that each finally
                 * block gets the correct scope... which could mean
                 * that some LEAVEWITH nodes are necessary.
                 */
                if (!hasFinally)
                    break;     // skip the whole mess.
                Node unwindBlock = null;
                for (int i=loops.size()-1; i >= 0; i--) {
                    Node n = (Node) loops.get(i);
                    int elemtype = n.getType();
                    if (elemtype == Token.TRY || elemtype == Token.WITH) {
                        Node unwind;
                        if (elemtype == Token.TRY) {
                            Jump jsrnode = new Jump(Token.JSR);
                            Node jsrtarget = ((Jump)n).getFinally();
                            jsrnode.target = jsrtarget;
                            unwind = jsrnode;
                        } else {
                            unwind = new Node(Token.LEAVEWITH);
                        }
                        if (unwindBlock == null) {
                            unwindBlock = new Node(Token.BLOCK,
                                                   node.getLineno());
                        }
                        unwindBlock.addChildToBack(unwind);
                    }
                }
                if (unwindBlock != null) {
                    Node returnNode = node;
                    Node returnExpr = returnNode.getFirstChild();
                    node = replaceCurrent(parent, previous, node, unwindBlock);
                    if (returnExpr == null || isGenerator) {
                        unwindBlock.addChildToBack(returnNode);
                    } else {
                        Node store = new Node(Token.EXPR_RESULT, returnExpr);
                        unwindBlock.addChildToFront(store);
                        returnNode = new Node(Token.RETURN_RESULT);
                        unwindBlock.addChildToBack(returnNode);
                        // transform return expression
                        transformCompilationUnit_r(tree, store, scope,
                                                   createScopeObjects,
                                                   inStrictMode);
                    }
                    // skip transformCompilationUnit_r to avoid infinite loop
                    continue siblingLoop;
                }
                break;
              }

              case Token.BREAK:
              case Token.CONTINUE:
              {
                Jump jump = (Jump)node;
                Jump jumpStatement = jump.getJumpStatement();
                if (jumpStatement == null) Kit.codeBug();

                for (int i = loops.size(); ;) {
                    if (i == 0) {
                        // Parser/IRFactory ensure that break/continue
                        // always has a jump statement associated with it
                        // which should be found
                        throw Kit.codeBug();
                    }
                    --i;
                    Node n = (Node) loops.get(i);
                    if (n == jumpStatement) {
                        break;
                    }

                    int elemtype = n.getType();
                    if (elemtype == Token.WITH) {
                        Node leave = new Node(Token.LEAVEWITH);
                        previous = addBeforeCurrent(parent, previous, node,
                                                    leave);
                    } else if (elemtype == Token.TRY) {
                        Jump tryNode = (Jump)n;
                        Jump jsrFinally = new Jump(Token.JSR);
                        jsrFinally.target = tryNode.getFinally();
                        previous = addBeforeCurrent(parent, previous, node,
                                                    jsrFinally);
                    }
                }

                if (type == Token.BREAK) {
                    jump.target = jumpStatement.target;
                } else {
                    jump.target = jumpStatement.getContinue();
                }
                jump.setType(Token.GOTO);

                break;
              }

              case Token.CALL:
                visitCall(node, tree);
                break;

              case Token.NEW:
                visitNew(node, tree);
                break;

              case Token.LETEXPR:
              case Token.LET: {
                Node child = node.getFirstChild();
                if (child.getType() == Token.LET) {
                  // We have a let statement or expression rather than a
                  // let declaration
                  boolean createWith = tree.getType() != Token.FUNCTION
                      || ((FunctionNode)tree).requiresActivation();
                  node = visitLet(createWith, parent, previous, node);
                  break;
                } else {
                  // fall through to process let declaration...
                }
              }
              /* fall through */
              case Token.CONST:
              case Token.VAR:
              {
                Node result = new Node(Token.BLOCK);
                for (Node cursor = node.getFirstChild(); cursor != null;) {
                    // Move cursor to next before createAssignment gets chance
                    // to change n.next
                    Node n = cursor;
                    cursor = cursor.getNext();
                    if (n.getType() == Token.NAME) {
                        if (!n.hasChildren())
                            continue;
                        Node init = n.getFirstChild();
                        n.removeChild(init);
                        n.setType(Token.BINDNAME);
                        n = new Node(type == Token.CONST ?
                                         Token.SETCONST :
                                         Token.SETNAME,
                                     n, init);
                    } else {
                        // May be a destructuring assignment already transformed
                        // to a LETEXPR
                        if (n.getType() != Token.LETEXPR)
                            throw Kit.codeBug();
                    }
                    Node pop = new Node(Token.EXPR_VOID, n, node.getLineno());
                    result.addChildToBack(pop);
                }
                node = replaceCurrent(parent, previous, node, result);
                break;
              }

              case Token.TYPEOFNAME: {
                Scope defining = scope.getDefiningScope(node.getString());
                if (defining != null) {
                    node.setScope(defining);
                }
              }
              break;

              case Token.TYPEOF:
              case Token.IFNE: {
                  /* We want to suppress warnings for undefined property o.p
                   * for the following constructs: typeof o.p, if (o.p),
                   * if (!o.p), if (o.p == undefined), if (undefined == o.p)
                   */
            	  Node child = node.getFirstChild();
            	  if (type == Token.IFNE) {
                	  while (child.getType() == Token.NOT) {
                	      child = child.getFirstChild();
                	  }
                	  if (child.getType() == Token.EQ ||
                	      child.getType() == Token.NE)
                	  {
                	      Node first = child.getFirstChild();
                	      Node last = child.getLastChild();
                	      if (first.getType() == Token.NAME &&
                	          first.getString().equals("undefined"))
                	          child = last;
                	      else if (last.getType() == Token.NAME &&
                	               last.getString().equals("undefined"))
                              child = first;
                	  }
            	  }
            	  if (child.getType() == Token.GETPROP)
            		  child.setType(Token.GETPROPNOWARN);
            	  break;
              }

              case Token.SETNAME:
                  if (inStrictMode) {
                      node.setType(Token.STRICT_SETNAME);
                  }
                  /* fall through */
              case Token.NAME:
              case Token.SETCONST:
              case Token.DELPROP:
              {
                // Turn name to var for faster access if possible
                if (createScopeObjects) {
                    break;
                }
                Node nameSource;
                if (type == Token.NAME) {
                    nameSource = node;
                } else {
                    nameSource = node.getFirstChild();
                    if (nameSource.getType() != Token.BINDNAME) {
                        if (type == Token.DELPROP) {
                            break;
                        }
                        throw Kit.codeBug();
                    }
                }
                if (nameSource.getScope() != null) {
                    break; // already have a scope set
                }
                String name = nameSource.getString();
                Scope defining = scope.getDefiningScope(name);
                if (defining != null) {
                    nameSource.setScope(defining);
                    if (type == Token.NAME) {
                        node.setType(Token.GETVAR);
                    } else if (type == Token.SETNAME ||
                               type == Token.STRICT_SETNAME) {
                        node.setType(Token.SETVAR);
                        nameSource.setType(Token.STRING);
                    } else if (type == Token.SETCONST) {
                        node.setType(Token.SETCONSTVAR);
                        nameSource.setType(Token.STRING);
                    } else if (type == Token.DELPROP) {
                        // Local variables are by definition permanent
                        Node n = new Node(Token.FALSE);
                        node = replaceCurrent(parent, previous, node, n);
                    } else {
                        throw Kit.codeBug();
                    }
                }
                break;
              }
            }

            transformCompilationUnit_r(tree, node,
                node instanceof Scope ? (Scope)node : scope,
                createScopeObjects, inStrictMode);
        }
    }

    protected void visitNew(Node node, ScriptNode tree) {
    }

    protected void visitCall(Node node, ScriptNode tree) {
    }

    protected Node visitLet(boolean createWith, Node parent, Node previous,
                            Node scopeNode)
    {
        Node vars = scopeNode.getFirstChild();
        Node body = vars.getNext();
        scopeNode.removeChild(vars);
        scopeNode.removeChild(body);
        boolean isExpression = scopeNode.getType() == Token.LETEXPR;
        Node result;
        Node newVars;
        if (createWith) {
            result = new Node(isExpression ? Token.WITHEXPR : Token.BLOCK);
            result = replaceCurrent(parent, previous, scopeNode, result);
            ArrayList<Object> list = new ArrayList<Object>();
            Node objectLiteral = new Node(Token.OBJECTLIT);
            for (Node v=vars.getFirstChild(); v != null; v = v.getNext()) {
                Node current = v;
                if (current.getType() == Token.LETEXPR) {
                    // destructuring in let expr, e.g. let ([x, y] = [3, 4]) {}
                    List<?> destructuringNames = (List<?>)
                        current.getProp(Node.DESTRUCTURING_NAMES);
                    Node c = current.getFirstChild();
                    if (c.getType() != Token.LET) throw Kit.codeBug();
                    // Add initialization code to front of body
                    if (isExpression) {
                        body = new Node(Token.COMMA, c.getNext(), body);
                    } else {
                        body = new Node(Token.BLOCK,
                            new Node(Token.EXPR_VOID, c.getNext()),
                            body);
                    }
                    // Update "list" and "objectLiteral" for the variables
                    // defined in the destructuring assignment
                    if (destructuringNames != null) {
                        list.addAll(destructuringNames);
                        for (int i=0; i < destructuringNames.size(); i++) {
                            objectLiteral.addChildToBack(
                                new Node(Token.VOID, Node.newNumber(0.0)));
                        }
                    }
                    current = c.getFirstChild(); // should be a NAME, checked below
                }
                if (current.getType() != Token.NAME) throw Kit.codeBug();
                list.add(ScriptRuntime.getIndexObject(current.getString()));
                Node init = current.getFirstChild();
                if (init == null) {
                    init = new Node(Token.VOID, Node.newNumber(0.0));
                }
                objectLiteral.addChildToBack(init);
             }
             objectLiteral.putProp(Node.OBJECT_IDS_PROP, list.toArray());
             newVars = new Node(Token.ENTERWITH, objectLiteral);
             result.addChildToBack(newVars);
             result.addChildToBack(new Node(Token.WITH, body));
             result.addChildToBack(new Node(Token.LEAVEWITH));
        } else {
            result = new Node(isExpression ? Token.COMMA : Token.BLOCK);
            result = replaceCurrent(parent, previous, scopeNode, result);
            newVars = new Node(Token.COMMA);
            for (Node v=vars.getFirstChild(); v != null; v = v.getNext()) {
                Node current = v;
                if (current.getType() == Token.LETEXPR) {
                    // destructuring in let expr, e.g. let ([x, y] = [3, 4]) {}
                    Node c = current.getFirstChild();
                    if (c.getType() != Token.LET) throw Kit.codeBug();
                    // Add initialization code to front of body
                    if (isExpression) {
                        body = new Node(Token.COMMA, c.getNext(), body);
                    } else {
                        body = new Node(Token.BLOCK,
                            new Node(Token.EXPR_VOID, c.getNext()),
                            body);
                    }
                    // We're removing the LETEXPR, so move the symbols
                    Scope.joinScopes((Scope)current,
                                          (Scope)scopeNode);
                    current = c.getFirstChild(); // should be a NAME, checked below
                }
                if (current.getType() != Token.NAME) throw Kit.codeBug();
                Node stringNode = Node.newString(current.getString());
                stringNode.setScope((Scope)scopeNode);
                Node init = current.getFirstChild();
                if (init == null) {
                    init = new Node(Token.VOID, Node.newNumber(0.0));
                }
                newVars.addChildToBack(new Node(Token.SETVAR, stringNode, init));
            }
            if (isExpression) {
                result.addChildToBack(newVars);
                scopeNode.setType(Token.COMMA);
                result.addChildToBack(scopeNode);
                scopeNode.addChildToBack(body);
                if (body instanceof Scope) {
                    Scope scopeParent = ((Scope) body).getParentScope();
                    ((Scope) body).setParentScope((Scope)scopeNode);
                    ((Scope) scopeNode).setParentScope(scopeParent);
                }
            } else {
                result.addChildToBack(new Node(Token.EXPR_VOID, newVars));
                scopeNode.setType(Token.BLOCK);
                result.addChildToBack(scopeNode);
                scopeNode.addChildrenToBack(body);
                if (body instanceof Scope) {
                    Scope scopeParent = ((Scope) body).getParentScope();
                    ((Scope) body).setParentScope((Scope)scopeNode);
                    ((Scope) scopeNode).setParentScope(scopeParent);
                }
            }
        }
        return result;
    }

    private static Node addBeforeCurrent(Node parent, Node previous,
                                         Node current, Node toAdd)
    {
        if (previous == null) {
            if (!(current == parent.getFirstChild())) Kit.codeBug();
            parent.addChildToFront(toAdd);
        } else {
            if (!(current == previous.getNext())) Kit.codeBug();
            parent.addChildAfter(toAdd, previous);
        }
        return toAdd;
    }

    private static Node replaceCurrent(Node parent, Node previous,
                                       Node current, Node replacement)
    {
        if (previous == null) {
            if (!(current == parent.getFirstChild())) Kit.codeBug();
            parent.replaceChild(current, replacement);
        } else if (previous.next == current) {
            // Check cachedPrev.next == current is necessary due to possible
            // tree mutations
            parent.replaceChildAfter(previous, replacement);
        } else {
            parent.replaceChild(current, replacement);
        }
        return replacement;
    }

    private ObjArray loops;
    private ObjArray loopEnds;
    private boolean hasFinally;
}
