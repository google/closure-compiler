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

import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Node for the root of a parse tree.  It contains the statements and functions
 * in the script, and a list of {@link Comment} nodes associated with the script
 * as a whole.  Node type is {@link Token#SCRIPT}. <p>
 *
 * Note that the tree itself does not store errors.  To collect the parse errors
 * and warnings, pass an {@link org.mozilla.javascript.ErrorReporter} to the
 * {@link org.mozilla.javascript.Parser} via the
 * {@link org.mozilla.javascript.CompilerEnvirons}.
 */
public class AstRoot extends ScriptNode {

    private SortedSet<Comment> comments;
    private boolean inStrictMode;

    {
        type = Token.SCRIPT;
    }

    public AstRoot() {
    }

    public AstRoot(int pos) {
        super(pos);
    }

    /**
     * Returns comment set
     * @return comment set, sorted by start position. Can be {@code null}.
     */
    public SortedSet<Comment> getComments() {
        return comments;
    }

    /**
     * Sets comment list, and updates the parent of each entry to point
     * to this node.  Replaces any existing comments.
     * @param comments comment list.  can be {@code null}.
     */
    public void setComments(SortedSet<Comment> comments) {
        if (comments == null) {
            this.comments = null;
        } else {
            if (this.comments != null)
                this.comments.clear();
            for (Comment c : comments)
                addComment(c);
        }
    }

    /**
     * Add a comment to the comment set.
     * @param comment the comment node.
     * @throws IllegalArgumentException if comment is {@code null}
     */
    public void addComment(Comment comment) {
        assertNotNull(comment);
        if (comments == null) {
            comments = new TreeSet<Comment>(new AstNode.PositionComparator());
        }
        comments.add(comment);
        comment.setParent(this);
    }

    public void setInStrictMode(boolean inStrictMode) {
        this.inStrictMode = inStrictMode;
    }

    public boolean isInStrictMode() {
        return inStrictMode;
    }

    /**
     * Visits the comment nodes in the order they appear in the source code.
     * The comments are not visited by the {@link #visit} function - you must
     * use this function to visit them.
     * @param visitor the callback object.  It is passed each comment node.
     * The return value is ignored.
     */
    public void visitComments(NodeVisitor visitor) {
        if (comments != null) {
            for (Comment c : comments) {
                visitor.visit(c);
            }
        }
    }

    /**
     * Visits the AST nodes, then the comment nodes.
     * This method is equivalent to calling {@link #visit}, then
     * {@link #visitComments}.  The return value
     * is ignored while visiting comment nodes.
     * @param visitor the callback object.
     */
    public void visitAll(NodeVisitor visitor) {
        visit(visitor);
        visitComments(visitor);
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        for (Node node : this) {
            sb.append(((AstNode)node).toSource(depth));
        }
        return sb.toString();
    }

    /**
     * A debug-printer that includes comments (at the end).
     */
    @Override
    public String debugPrint() {
        DebugPrintVisitor dpv = new DebugPrintVisitor(new StringBuilder(1000));
        visitAll(dpv);
        return dpv.toString();
    }

    /**
     * Debugging function to check that the parser has set the parent
     * link for every node in the tree.
     * @throws IllegalStateException if a parent link is missing
     */
    public void checkParentLinks() {
        this.visit(new NodeVisitor() {
            public boolean visit(AstNode node) {
                int type = node.getType();
                if (type == Token.SCRIPT)
                    return true;
                if (node.getParent() == null)
                    throw new IllegalStateException
                            ("No parent for node: " + node
                             + "\n" + node.toSource(0));
                return true;
            }
        });
    }
}
