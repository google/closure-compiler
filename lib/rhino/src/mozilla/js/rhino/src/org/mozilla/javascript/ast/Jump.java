/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
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
 *   Roshan James
 *   Roger Lawrence
 *   Mike McCabe
 *   Steve Yegge
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

package org.mozilla.javascript.ast;

import org.mozilla.javascript.Node;
import org.mozilla.javascript.Token;

/**
 * Used for code generation.  During codegen, the AST is transformed
 * into an Intermediate Representation (IR) in which loops, ifs, switches
 * and other control-flow statements are rewritten as labeled jumps.
 * If the parser is set to IDE-mode, the resulting AST will not contain
 * any instances of this class.
 */
public class Jump extends AstNode {

    public Node target;
    private Node target2;
    private Jump jumpNode;

    public Jump() {
        type = Token.ERROR;
    }

    public Jump(int nodeType) {
        type = nodeType;
    }

    public Jump(int type, int lineno) {
        this(type);
        setLineno(lineno);
    }

    public Jump(int type, Node child) {
        this(type);
        addChildToBack(child);
    }

    public Jump(int type, Node child, int lineno) {
        this(type, child);
        setLineno(lineno);
    }

    public Jump getJumpStatement()
    {
        if (type != Token.BREAK && type != Token.CONTINUE) codeBug();
        return jumpNode;
    }

    public void setJumpStatement(Jump jumpStatement)
    {
        if (type != Token.BREAK && type != Token.CONTINUE) codeBug();
        if (jumpStatement == null) codeBug();
        if (this.jumpNode != null) codeBug(); //only once
        this.jumpNode = jumpStatement;
    }

    public Node getDefault()
    {
        if (type != Token.SWITCH) codeBug();
        return target2;
    }

    public void setDefault(Node defaultTarget)
    {
        if (type != Token.SWITCH) codeBug();
        if (defaultTarget.getType() != Token.TARGET) codeBug();
        if (target2 != null) codeBug(); //only once
        target2 = defaultTarget;
    }

    public Node getFinally()
    {
        if (type != Token.TRY) codeBug();
        return target2;
    }

    public void setFinally(Node finallyTarget)
    {
        if (type != Token.TRY) codeBug();
        if (finallyTarget.getType() != Token.TARGET) codeBug();
        if (target2 != null) codeBug(); //only once
        target2 = finallyTarget;
    }

    public Jump getLoop()
    {
        if (type != Token.LABEL) codeBug();
        return jumpNode;
    }

    public void setLoop(Jump loop)
    {
        if (type != Token.LABEL) codeBug();
        if (loop == null) codeBug();
        if (jumpNode != null) codeBug(); //only once
        jumpNode = loop;
    }

    public Node getContinue()
    {
        if (type != Token.LOOP) codeBug();
        return target2;
    }

    public void setContinue(Node continueTarget)
    {
        if (type != Token.LOOP) codeBug();
        if (continueTarget.getType() != Token.TARGET) codeBug();
        if (target2 != null) codeBug(); //only once
        target2 = continueTarget;
    }

    /**
     * Jumps are only used directly during code generation, and do
     * not support this interface.
     * @throws UnsupportedOperationException
     */
    @Override
    public void visit(NodeVisitor visitor) {
        throw new UnsupportedOperationException(this.toString());
    }

    @Override
    public String toSource(int depth) {
        throw new UnsupportedOperationException(this.toString());
    }
}
