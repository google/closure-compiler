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

import org.mozilla.javascript.Token;

/**
 * Node representing comments.
 * Node type is {@link Token#COMMENT}.<p>
 *
 * <p>JavaScript effectively has five comment types:
 *   <ol>
 *     <li>// line comments</li>
 *     <li>/<span class="none">* block comments *\/</li>
 *     <li>/<span class="none">** jsdoc comments *\/</li>
 *     <li>&lt;!-- html-open line comments</li>
 *     <li>^\\s*--&gt; html-close line comments</li>
 *   </ol>
 *
 * <p>The first three should be familiar to Java programmers.  JsDoc comments
 * are really just block comments with some conventions about the formatting
 * within the comment delimiters.  Line and block comments are described in the
 * Ecma-262 specification. <p>
 *
 * <p>SpiderMonkey and Rhino also support HTML comment syntax, but somewhat
 * counterintuitively, the syntax does not produce a block comment.  Instead,
 * everything from the string &lt;!-- through the end of the line is considered
 * a comment, and if the token --&gt; is the first non-whitespace on the line,
 * then the line is considered a line comment.  This is to support parsing
 * JavaScript in &lt;script&gt; HTML tags that has been "hidden" from very old
 * browsers by surrounding it with HTML comment delimiters. <p>
 *
 * Note the node start position for Comment nodes is still relative to the
 * parent, but Comments are always stored directly in the AstRoot node, so
 * they are also effectively absolute offsets.
 */
public class Comment extends AstNode {

    private String value;
    private Token.CommentType commentType;

    {
        type = Token.COMMENT;
    }

    /**
     * Constructs a new Comment
     * @param pos the start position
     * @param len the length including delimiter(s)
     * @param type the comment type
     * @param value the value of the comment, as a string
     */
    public Comment(int pos, int len, Token.CommentType type, String value) {
        super(pos, len);
        commentType = type;
        this.value = value;
    }

    /**
     * Returns the comment style
     */
    public Token.CommentType getCommentType() {
        return commentType;
    }

    /**
     * Sets the comment style
     * @param type the comment style, a
     * {@link org.mozilla.javascript.Token.CommentType}
     */
    public void setCommentType(Token.CommentType type) {
        this.commentType = type;
    }

    /**
     * Returns a string of the comment value.
     */
    public String getValue() {
        return value;
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder(getLength() + 10);
        sb.append(makeIndent(depth));
        sb.append(value);
        return sb.toString();
    }

    /**
     * Comment nodes are not visited during normal visitor traversals,
     * but comply with the {@link AstNode#visit} interface.
     */
    @Override
    public void visit(NodeVisitor v) {
        v.visit(this);
    }
}
