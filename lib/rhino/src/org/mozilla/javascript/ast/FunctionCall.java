/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

import org.mozilla.javascript.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AST node for a function call.  Node type is {@link Token#CALL}.<p>
 */
public class FunctionCall extends AstNode {

    protected static final List<AstNode> NO_ARGS =
        Collections.unmodifiableList(new ArrayList<AstNode>());

    protected AstNode target;
    protected List<AstNode> arguments;
    protected int lp = -1;
    protected int rp = -1;

    {
        type = Token.CALL;
    }

    public FunctionCall() {
    }

    public FunctionCall(int pos) {
        super(pos);
    }

    public FunctionCall(int pos, int len) {
        super(pos, len);
    }

    /**
     * Returns node evaluating to the function to call
     */
    public AstNode getTarget() {
        return target;
    }

    /**
     * Sets node evaluating to the function to call, and sets
     * its parent to this node.
     * @param target node evaluating to the function to call.
     * @throws IllegalArgumentException} if target is {@code null}
     */
    public void setTarget(AstNode target) {
        assertNotNull(target);
        this.target = target;
        target.setParent(this);
    }

    /**
     * Returns function argument list
     * @return function argument list, or an empty immutable list if
     *         there are no arguments.
     */
    public List<AstNode> getArguments() {
        return arguments != null ? arguments : NO_ARGS;
    }

    /**
     * Sets function argument list
     * @param arguments function argument list.  Can be {@code null},
     *        in which case any existing args are removed.
     */
    public void setArguments(List<AstNode> arguments) {
        if (arguments == null) {
            this.arguments = null;
        } else {
            if (this.arguments != null)
                this.arguments.clear();
            for (AstNode arg : arguments) {
                addArgument(arg);
            }
        }
    }

    /**
     * Adds an argument to the list, and sets its parent to this node.
     * @param arg the argument node to add to the list
     * @throws IllegalArgumentException} if arg is {@code null}
     */
    public void addArgument(AstNode arg) {
        assertNotNull(arg);
        if (arguments == null) {
            arguments = new ArrayList<AstNode>();
        }
        arguments.add(arg);
        arg.setParent(this);
    }

    /**
     * Returns left paren position, -1 if missing
     */
    public int getLp() {
        return lp;
    }

    /**
     * Sets left paren position
     * @param lp left paren position
     */
    public void setLp(int lp) {
        this.lp = lp;
    }

    /**
     * Returns right paren position, -1 if missing
     */
    public int getRp() {
        return rp;
    }

    /**
     * Sets right paren position
     */
    public void setRp(int rp) {
        this.rp = rp;
    }

    /**
     * Sets both paren positions
     */
    public void setParens(int lp, int rp) {
        this.lp = lp;
        this.rp = rp;
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        sb.append(target.toSource(0));
        sb.append("(");
        if (arguments != null) {
            printList(arguments, sb);
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Visits this node, the target object, and the arguments.
     */
    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            target.visit(v);
            for (AstNode arg : getArguments()) {
                arg.visit(v);
            }
        }
    }
}
