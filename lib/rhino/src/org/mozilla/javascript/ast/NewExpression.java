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
 * New expression. Node type is {@link Token#NEW}.<p>
 *
 * <pre><i>NewExpression</i> :
 *      MemberExpression
 *      <b>new</b> NewExpression</pre>
 *
 * This node is a subtype of {@link FunctionCall}, mostly for internal code
 * sharing.  Structurally a {@code NewExpression} node is very similar to a
 * {@code FunctionCall}, so it made a certain amount of sense.
 */
public class NewExpression extends FunctionCall {

    private ObjectLiteral initializer;

    {
        type = Token.NEW;
    }

    public NewExpression() {
    }

    public NewExpression(int pos) {
        super(pos);
    }

    public NewExpression(int pos, int len) {
        super(pos, len);
    }

    /**
     * Returns initializer object, if any.
     * @return extra initializer object-literal expression, or {@code null} if
     * not specified.
     */
    public ObjectLiteral getInitializer() {
      return initializer;
    }

    /**
     * Sets initializer object.  Rhino supports an experimental syntax
     * of the form {@code new expr [ ( arglist ) ] [initializer]},
     * in which initializer is an object literal that is used to set
     * additional properties on the newly-created {@code expr} object.
     *
     * @param initializer extra initializer object.
     * Can be {@code null}.
     */
    public void setInitializer(ObjectLiteral initializer) {
      this.initializer = initializer;
      if (initializer != null)
          initializer.setParent(this);
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        sb.append("new ");
        sb.append(target.toSource(0));
        sb.append("(");
        if (arguments != null) {
            printList(arguments, sb);
        }
        sb.append(")");
        if (initializer != null) {
            sb.append(" ");
            sb.append(initializer.toSource(0));
        }
        return sb.toString();
    }

    /**
     * Visits this node, the target, and each argument.  If there is
     * a trailing initializer node, visits that last.
     */
    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            target.visit(v);
            for (AstNode arg : getArguments()) {
                arg.visit(v);
            }
            if (initializer != null) {
                initializer.visit(v);
            }
        }
    }
}
