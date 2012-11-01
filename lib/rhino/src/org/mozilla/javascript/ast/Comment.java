/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

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
