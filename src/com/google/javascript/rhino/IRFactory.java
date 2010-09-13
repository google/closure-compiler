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
 *   Norris Boyd
 *   Igor Bukanov
 *   Ethan Hugg
 *   Terry Lucas
 *   Milen Nankov
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

/**
 * This class allows the creation of nodes, and follows the Factory pattern.
 *
 * @see Node
 */
final class IRFactory
{
    IRFactory(Parser parser)
    {
        this.parser = parser;
    }

    ScriptOrFnNode createScript()
    {
        return new ScriptOrFnNode(Token.SCRIPT);
    }

    /**
     * Script (for associating file/url names with toplevel scripts.)
     */
    void initScript(ScriptOrFnNode scriptNode, Node body)
    {
        Node children = body.removeChildren();
        if (children != null) { scriptNode.addChildrenToBack(children); }
    }

    /**
     * Leaf
     */
    Node createLeaf(int nodeType)
    {
        return new Node(nodeType);
    }

    /**
     * Leaf
     */
    Node createLeaf(int nodeType, int lineno, int charno)
    {
        return new Node(nodeType, lineno, charno);
    }

    /**
     * Statement leaf nodes.
     */

    Node createSwitch(int lineno, int charno)
    {
        return new Node(Token.SWITCH, lineno, charno);
    }

    /**
     * If caseExpression argument is null it indicate default label.
     */
    void addSwitchCase(Node switchNode, Node caseExpression, Node statements,
                       int lineno, int charno)
    {
        if (switchNode.getType() != Token.SWITCH) throw Kit.codeBug();

        Node caseNode;
        if (caseExpression != null) {
            caseNode = new Node(
                Token.CASE, caseExpression, lineno, charno);
        } else {
            caseNode = new Node(Token.DEFAULT, lineno, charno);
        }
        caseNode.addChildToBack(statements);
        switchNode.addChildToBack(caseNode);
    }

    void closeSwitch(Node switchBlock)
    {
    }

    Node createVariables(int token, int lineno, int charno)
    {
        return new Node(token, lineno, charno);
    }

    Node createExprStatement(Node expr, int lineno, int charno)
    {
        int type;
        if (parser.insideFunction()) {
            type = Token.EXPR_VOID;
        } else {
            type = Token.EXPR_RESULT;
        }
        return new Node(type, expr, lineno, charno);
    }

    Node createExprStatementNoReturn(Node expr, int lineno, int charno)
    {
        return new Node(Token.EXPR_VOID, expr, lineno, charno);
    }

    Node createDefaultNamespace(Node expr, int lineno, int charno)
    {
        // default xml namespace requires activation
        setRequiresActivation();
        Node n = createUnary(Token.DEFAULTNAMESPACE, expr, lineno, charno);
        Node result = createExprStatement(n, lineno, charno);
        return result;
    }

    public Node createErrorName() {
        return Node.newString(Token.NAME, "error");
    }

    /**
     * Name
     */
    Node createName(String name, int lineno, int charno)
    {
        checkActivationName(name, Token.NAME);
        return Node.newString(Token.NAME, name, lineno, charno);
    }

    public Node createTaggedName(String name, JSDocInfo info,
            int lineno, int charno) {
        Node n = createName(name, lineno, charno);
        if (info != null) {
            n.setJSDocInfo(info);
        }
        return n;
    }

    /**
     * String (for literals)
     */
    Node createString(String string)
    {
        return Node.newString(string);
    }

    Node createString(String string, int lineno, int charno)
    {
        return Node.newString(string, lineno, charno);
    }

    /**
     * Number (for literals)
     */
    Node createNumber(double number)
    {
        return Node.newNumber(number);
    }

    Node createNumber(double number, int lineno, int charno)
    {
        return Node.newNumber(number, lineno, charno);
    }

    /**
     * Catch clause of try/catch/finally
     * @param varName the name of the variable to bind to the exception
     * @param nameLineno the starting line number of the exception clause
     * @param nameCharno the starting char number of the exception clause
     * @param catchCond the condition under which to catch the exception.
     *                  May be null if no condition is given.
     * @param stmts the statements in the catch clause
     * @param catchLineno the starting line number of the catch clause
     * @param catchCharno the starting char number of the catch clause
     */
    Node createCatch(String varName, int nameLineno, int nameCharno,
            Node catchCond, Node stmts, int catchLineno, int catchCharno)
    {
        if (catchCond == null) {
            catchCond = new Node(Token.EMPTY, nameLineno, nameCharno);
        }
        return new Node(Token.CATCH,
                createName(varName, nameLineno, nameCharno),
                catchCond, stmts, catchLineno, catchCharno);
    }

    /**
     * Throw
     */
    Node createThrow(Node expr, int lineno, int charno)
    {
        return new Node(Token.THROW, expr, lineno, charno);
    }

    /**
     * Return
     */
    Node createReturn(Node expr, int lineno, int charno)
    {
        return expr == null
            ? new Node(Token.RETURN, lineno, charno)
            : new Node(Token.RETURN, expr, lineno, charno);
    }

    /**
     * Label
     */
    Node createLabel(String name, int lineno, int charno)
    {
        return new Node(Token.LABEL,
                Node.newString(Token.NAME, name, lineno, charno),
                lineno, charno);
    }

    /**
     * Break (possibly labeled)
     */
    Node createBreak(String label, int lineno, int charno)
    {
        Node result = new Node(Token.BREAK, lineno, charno);
        if (label == null) {
            return result;
        } else {
            Node name = Node.newString(Token.NAME, label, lineno, charno);
            result.addChildToBack(name);
            return result;
        }
    }

    /**
     * Continue (possibly labeled)
     */
    Node createContinue(String label, int lineno, int charno)
    {
        Node result = new Node(Token.CONTINUE, lineno, charno);
        if (label == null) {
            return result;
        } else {
            Node name = Node.newString(Token.NAME, label, lineno, charno);
            result.addChildToBack(name);
            return result;
        }
    }

    /**
     * debugger
     */
    Node createDebugger(int lineno, int charno) {
        return new Node(Token.DEBUGGER, lineno, charno);
    }

    /**
     * Statement block
     * Creates the empty statement block
     * Must make subsequent calls to add statements to the node
     */
    Node createBlock(int lineno, int charno)
    {
        return new Node(Token.BLOCK, lineno, charno);
    }

    FunctionNode createFunction(String name, int lineno, int charno)
    {
        FunctionNode fnNode = new FunctionNode(name, lineno, charno);
        // A hack to preserve the existing JSCompiler code that depends on
        // having the first child node being a NAME node.
        // TODO(user): Remove this when the JSCompiler code has been fixed.
        fnNode.addChildToBack(createName(name, lineno, charno));
        return fnNode;
    }

    Node initFunction(FunctionNode fnNode, int functionIndex,
                      Node args, JSDocInfo info,
                      Node statements, int functionType)
    {
        fnNode.itsFunctionType = functionType;
        fnNode.addChildToBack(args);
        fnNode.addChildToBack(statements);
        if (parser.getSourceName() != null) {
            fnNode.putProp(Node.SOURCENAME_PROP, parser.getSourceName());
        }
        if (info != null) {
            fnNode.setJSDocInfo(info);
        }

        int functionCount = fnNode.getFunctionCount();
        if (functionCount != 0) {
            // Functions containing other functions require activation objects
            fnNode.itsNeedsActivation = true;
            for (int i = 0; i != functionCount; ++i) {
                FunctionNode fn = fnNode.getFunctionNode(i);
                // nested function expression statements overrides var
                if (fn.getFunctionType()
                        == FunctionNode.FUNCTION_EXPRESSION_STATEMENT)
                {
                    String name = fn.getFunctionName();
                    if (name != null && name.length() != 0) {
                        fnNode.removeParamOrVar(name);
                    }
                }
            }
        }

        fnNode.putIntProp(Node.FUNCTION_PROP, functionIndex);
        return fnNode;
    }

    /**
     * Add a child to the back of the given node.  This function
     * breaks the Factory abstraction, but it removes a requirement
     * from implementors of Node.
     */
    void addChildToBack(Node parent, Node child)
    {
        parent.addChildToBack(child);
    }

    /**
     * While
     */
    Node createWhile(Node cond, Node body, int lineno, int charno)
    {
        return new Node(Token.WHILE, cond, body, lineno, charno);
    }

    /**
     * DoWhile
     */
    Node createDoWhile(Node body, Node cond, int lineno, int charno)
    {
        return new Node(Token.DO, body, cond, lineno, charno);
    }

    /**
     * For
     */
    Node createFor(Node init, Node test, Node incr, Node body,
            int lineno, int charno)
    {
        return new Node(Token.FOR, init, test, incr, body, lineno, charno);
    }

    /**
     * For .. In
     *
     */
    Node createForIn(Node lhs, Node obj, Node body,
                     int lineno, int charno)
    {
        return new Node(Token.FOR, lhs, obj, body, lineno, charno);
    }

    /**
     * Try/Catch/Finally
     */
    Node createTryCatchFinally(Node tryBlock, Node catchBlocks,
                               Node finallyBlock, int lineno, int charno)
    {
        if (finallyBlock == null) {
            return new Node(
                Token.TRY, tryBlock, catchBlocks, lineno, charno);
        }
        return new Node(Token.TRY, tryBlock, catchBlocks, finallyBlock,
                lineno, charno);
    }

    /**
     * Throw, Return, Label, Break and Continue are defined in ASTFactory.
     */

    /**
     * With
     */
    Node createWith(Node obj, Node body, int lineno, int charno)
    {
        return new Node(Token.WITH, obj, body, lineno, charno);
    }

    /**
     * DOTQUERY
     */
    public Node createDotQuery (Node obj, Node body, int lineno, int charno)
    {
        setRequiresActivation();
        Node result = new Node(Token.DOTQUERY, obj, body, lineno, charno);
        return result;
    }

    Node createArrayLiteral(ObjArray elems, int skipCount,
            int lineno, int charno)
    {
        int length = elems.size();
        int[] skipIndexes = null;
        if (skipCount != 0) {
            skipIndexes = new int[skipCount];
        }
        Node array = new Node(Token.ARRAYLIT, lineno, charno);
        for (int i = 0, j = 0; i != length; ++i) {
            Node elem = (Node)elems.get(i);
            if (elem != null) {
                array.addChildToBack(elem);
            } else {
                skipIndexes[j] = i;
                ++j;
            }
        }
        if (skipCount != 0) {
            array.putProp(Node.SKIP_INDEXES_PROP, skipIndexes);
        }
        return array;
    }

    /**
     * Object Literals
     */
    Node createObjectLiteral(ObjArray obj, int lineno, int charno)
    {
        Node object = new Node(Token.OBJECTLIT, lineno, charno);
        for (int i = 0; i < obj.size(); i += 2) {
            Node n = (Node)obj.get(i);
            object.addChildToBack(n);
            n = (Node)obj.get(i + 1);
            object.addChildToBack(n);
        }

        return object;
    }

    /**
     * Regular expressions
     */
    Node createRegExp(String string, String flags,
            int lineno, int charno) {
        return flags.length() == 0
               ? new Node(Token.REGEXP,
                          Node.newString(string, lineno, charno),
                          lineno, charno)
               : new Node(Token.REGEXP,
                          Node.newString(string, lineno, charno),
                          Node.newString(flags, lineno, charno),
                          lineno, charno);
    }

    /**
     * If statement
     */
    Node createIf(Node cond, Node ifTrue, Node ifFalse, int lineno, int charno)
    {
        if (ifFalse == null)
            return new Node(Token.IF, cond, ifTrue, lineno, charno);
        return new Node(Token.IF, cond, ifTrue, ifFalse, lineno, charno);
    }

    Node createCondExpr(Node cond, Node ifTrue, Node ifFalse,
            int lineno, int charno)
    {
        return new Node(Token.HOOK, cond, ifTrue, ifFalse, lineno, charno);
    }

    /**
     * Unary
     */
    Node createUnary(int nodeType, Node child, int lineno, int charno)
    {
        return new Node(nodeType, child, lineno, charno);
    }

    Node createCallOrNew(int nodeType, Node child, int lineno, int charno)
    {
        int type = Node.NON_SPECIALCALL;
        if (child.getType() == Token.NAME) {
            String name = child.getString();
            if (name.equals("eval")) {
                type = Node.SPECIALCALL_EVAL;
            } else if (name.equals("With")) {
                type = Node.SPECIALCALL_WITH;
            }
        } else if (child.getType() == Token.GETPROP) {
            String name = child.getLastChild().getString();
            if (name.equals("eval")) {
                type = Node.SPECIALCALL_EVAL;
            }
        }
        Node node = new Node(nodeType, child, lineno, charno);
        if (type != Node.NON_SPECIALCALL) {
            // Calls to these functions require activation objects.
            setRequiresActivation();
            node.putIntProp(Node.SPECIALCALL_PROP, type);
        }
        return node;
    }

    Node createIncDec(int nodeType, boolean post, Node child,
            int lineno, int charno)
    {
        child = makeReference(child);
        if (child == null) {
            String msg;
            if (nodeType == Token.DEC) {
                msg = "msg.bad.decr";
            } else {
                msg = "msg.bad.incr";
            }
            parser.reportError(msg);
            return null;
        }

        int childType = child.getType();

        switch (childType) {
          case Token.NAME:
          case Token.GETPROP:
          case Token.GETELEM:
          case Token.GET_REF:
          case Token.CALL: {
            Node n = new Node(nodeType, child, lineno, charno);
            n.putIntProp(Node.INCRDECR_PROP, post ? 1 : 0);
            return n;
          }
        }
        throw Kit.codeBug();
    }

    Node createPropertyGet(Node target, String namespace, String name,
                           int memberTypeFlags, int dotLineno, int dotCharno,
                           int nameLineno, int nameCharno)
    {
        if (namespace == null && memberTypeFlags == 0) {
            if (target == null) {
                return createName(name, nameLineno, nameCharno);
            }
            checkActivationName(name, Token.GETPROP);
            if (ScriptRuntime.isSpecialProperty(name)) {
                Node ref = new Node(Token.REF_SPECIAL, target);
                ref.putProp(Node.NAME_PROP, name);
                return new Node(Token.GET_REF, ref, dotLineno, dotCharno);
            }
            return new Node(
                Token.GETPROP, target,
                createString(name, nameLineno, nameCharno),
                dotLineno, dotCharno);
        }
        Node elem = createString(name);
        memberTypeFlags |= Node.PROPERTY_FLAG;
        return createMemberRefGet(target, namespace, elem, memberTypeFlags,
                                  dotLineno, dotCharno);
    }

    Node createElementGet(Node target, String namespace, Node elem,
                          int memberTypeFlags, int lineno, int charno)
    {
        // OPT: could optimize to createPropertyGet
        // iff elem is string that can not be number
        if (namespace == null && memberTypeFlags == 0) {
            // stand-alone [aaa] as primary expression is array literal
            // declaration and should not come here!
            if (target == null) throw Kit.codeBug();
            return new Node(Token.GETELEM, target, elem, lineno, charno);
        }
        return createMemberRefGet(target, namespace, elem, memberTypeFlags,
                                  lineno, charno);
    }

    private Node createMemberRefGet(Node target, String namespace, Node elem,
                                    int memberTypeFlags, int lineno, int charno)
    {
        Node nsNode = null;
        if (namespace != null) {
            // See 11.1.2 in ECMA 357
            if (namespace.equals("*")) {
                nsNode = new Node(Token.NULL, lineno, charno);
            } else {
                nsNode = createName(namespace, lineno, charno);
            }
        }
        Node ref;
        if (target == null) {
            if (namespace == null) {
                ref = new Node(Token.REF_NAME, elem, lineno, charno);
            } else {
                ref = new Node(Token.REF_NS_NAME, nsNode, elem, lineno, charno);
            }
        } else {
            if (namespace == null) {
                ref = new Node(Token.REF_MEMBER, target, elem, lineno, charno);
            } else {
                ref = new Node(Token.REF_NS_MEMBER, target, nsNode, elem,
                        lineno, charno);
            }
        }
        if (memberTypeFlags != 0) {
            ref.putIntProp(Node.MEMBER_TYPE_PROP, memberTypeFlags);
        }
        return new Node(Token.GET_REF, ref, lineno, charno);
    }

    /**
     * Binary
     */
    Node createBinary(int nodeType, Node left, Node right,
            int lineno, int charno)
    {
        Node temp;
        switch (nodeType) {

          case Token.DOT:
            nodeType = Token.GETPROP;
            Node idNode = right;
            idNode.setType(Token.STRING);
            break;

          case Token.LB:
            // OPT: could optimize to GETPROP iff string can't be a number
            nodeType = Token.GETELEM;
            break;
        }
        return new Node(nodeType, left, right, lineno, charno);
    }

    Node createAssignment(int nodeOp, Node left, Node right,
            int lineno, int charno) throws JavaScriptException
    {
        int nodeType = left.getType();
        switch (nodeType) {
            case Token.NAME:
            case Token.GETPROP:
            case Token.GETELEM:
                break;
            default:
                // TODO: This should be a ReferenceError--but that's a runtime
                //  exception. Should we compile an exception into the code?
                parser.reportError("msg.bad.assign.left");
        }

        return new Node(Token.ASSIGN, left, right, lineno, charno);
    }

    private Node makeReference(Node node)
    {
        int type = node.getType();
        switch (type) {
          case Token.NAME:
          case Token.GETPROP:
          case Token.GETELEM:
          case Token.GET_REF:
          case Token.CALL:
            return node;
        }
        // Signal caller to report error
        return null;
    }

// Commented-out: no longer used
//     private static boolean hasSideEffects(Node exprTree)
//     {
//         switch (exprTree.getType()) {
//           case Token.INC:
//           case Token.DEC:
//           case Token.SETPROP:
//           case Token.SETELEM:
//           case Token.SETNAME:
//           case Token.CALL:
//           case Token.NEW:
//             return true;
//           default:
//             Node child = exprTree.getFirstChild();
//             while (child != null) {
//                 if (hasSideEffects(child))
//                     return true;
//                 child = child.getNext();
//             }
//             break;
//         }
//         return false;
//     }

    private void checkActivationName(String name, int token)
    {
        if (parser.insideFunction()) {
            boolean activation = false;
            if ("arguments".equals(name)
                || (parser.compilerEnv.activationNames != null
                    && parser.compilerEnv.activationNames.containsKey(name)))
            {
                activation = true;
            } else if ("length".equals(name)) {
                if (token == Token.GETPROP
                    && parser.compilerEnv.getLanguageVersion()
                       == Context.VERSION_1_2)
                {
                    // Use of "length" in 1.2 requires an activation object.
                    activation = true;
                }
            }
            if (activation) {
                setRequiresActivation();
            }
        }
    }

    private void setRequiresActivation()
    {
        if (parser.insideFunction()) {
            ((FunctionNode)parser.currentScriptOrFn).itsNeedsActivation = true;
        }
    }

    private Parser parser;
}
