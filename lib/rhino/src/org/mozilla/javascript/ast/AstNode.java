/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

import org.mozilla.javascript.Kit;
import org.mozilla.javascript.Node;
import org.mozilla.javascript.Token;

import java.io.Serializable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for AST node types.  The goal of the AST is to represent the
 * physical source code, to make it useful for code-processing tools such
 * as IDEs or pretty-printers.  The parser must not rewrite the parse tree
 * when producing this representation. <p>
 *
 * The {@code AstNode} hierarchy sits atop the older {@link Node} class,
 * which was designed for code generation.  The {@code Node} class is a
 * flexible, weakly-typed class suitable for creating and rewriting code
 * trees, but using it requires you to remember the exact ordering of the
 * child nodes, which are kept in a linked list.  The {@code AstNode}
 * hierarchy is a strongly-typed facade with named accessors for children
 * and common properties, but under the hood it's still using a linked list
 * of child nodes.  It isn't a very good idea to use the child list directly
 * unless you know exactly what you're doing.</p>
 *
 * Note that {@code AstNode} records additional information, including
 * the node's position, length, and parent node.  Also, some {@code AstNode}
 * subclasses record some of their child nodes in instance members, since
 * they are not needed for code generation.  In a nutshell, only the code
 * generator should be mixing and matching {@code AstNode} and {@code Node}
 * objects.<p>
 *
 * All offset fields in all subclasses of AstNode are relative to their
 * parent.  For things like paren, bracket and keyword positions, the
 * position is relative to the current node.  The node start position is
 * relative to the parent node. <p>
 *
 * During the actual parsing, node positions are absolute; adding the node to
 * its parent fixes up the offsets to be relative.  By the time you see the AST
 * (e.g. using the {@code Visitor} interface), the offsets are relative. <p>
 *
 * {@code AstNode} objects have property lists accessible via the
 * {@link #getProp} and {@link #putProp} methods.  The property lists are
 * integer-keyed with arbitrary {@code Object} values.  For the most part the
 * parser generating the AST avoids using properties, preferring fields for
 * elements that are always set.  Property lists are intended for user-defined
 * annotations to the tree.  The Rhino code generator acts as a client and
 * uses node properties extensively.  You are welcome to use the property-list
 * API for anything your client needs.<p>
 *
 * This hierarchy does not have separate branches for expressions and
 * statements, as the distinction in JavaScript is not as clear-cut as in
 * Java or C++. <p>
 */
public abstract class AstNode extends Node implements Comparable<AstNode> {

    protected int position = -1;
    protected int length = 1;
    protected AstNode parent;

    private static Map<Integer,String> operatorNames =
            new HashMap<Integer,String>();

    static {
        operatorNames.put(Token.IN, "in");
        operatorNames.put(Token.TYPEOF, "typeof");
        operatorNames.put(Token.INSTANCEOF, "instanceof");
        operatorNames.put(Token.DELPROP, "delete");
        operatorNames.put(Token.COMMA, ",");
        operatorNames.put(Token.COLON, ":");
        operatorNames.put(Token.OR, "||");
        operatorNames.put(Token.AND, "&&");
        operatorNames.put(Token.INC, "++");
        operatorNames.put(Token.DEC, "--");
        operatorNames.put(Token.BITOR, "|");
        operatorNames.put(Token.BITXOR, "^");
        operatorNames.put(Token.BITAND, "&");
        operatorNames.put(Token.EQ, "==");
        operatorNames.put(Token.NE, "!=");
        operatorNames.put(Token.LT, "<");
        operatorNames.put(Token.GT, ">");
        operatorNames.put(Token.LE, "<=");
        operatorNames.put(Token.GE, ">=");
        operatorNames.put(Token.LSH, "<<");
        operatorNames.put(Token.RSH, ">>");
        operatorNames.put(Token.URSH, ">>>");
        operatorNames.put(Token.ADD, "+");
        operatorNames.put(Token.SUB, "-");
        operatorNames.put(Token.MUL, "*");
        operatorNames.put(Token.DIV, "/");
        operatorNames.put(Token.MOD, "%");
        operatorNames.put(Token.NOT, "!");
        operatorNames.put(Token.BITNOT, "~");
        operatorNames.put(Token.POS, "+");
        operatorNames.put(Token.NEG, "-");
        operatorNames.put(Token.SHEQ, "===");
        operatorNames.put(Token.SHNE, "!==");
        operatorNames.put(Token.ASSIGN, "=");
        operatorNames.put(Token.ASSIGN_BITOR, "|=");
        operatorNames.put(Token.ASSIGN_BITAND, "&=");
        operatorNames.put(Token.ASSIGN_LSH, "<<=");
        operatorNames.put(Token.ASSIGN_RSH, ">>=");
        operatorNames.put(Token.ASSIGN_URSH, ">>>=");
        operatorNames.put(Token.ASSIGN_ADD, "+=");
        operatorNames.put(Token.ASSIGN_SUB, "-=");
        operatorNames.put(Token.ASSIGN_MUL, "*=");
        operatorNames.put(Token.ASSIGN_DIV, "/=");
        operatorNames.put(Token.ASSIGN_MOD, "%=");
        operatorNames.put(Token.ASSIGN_BITXOR, "^=");
        operatorNames.put(Token.VOID, "void");
    }

    public static class PositionComparator implements Comparator<AstNode>, Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * Sorts nodes by (relative) start position.  The start positions are
         * relative to their parent, so this comparator is only meaningful for
         * comparing siblings.
         */
        public int compare(AstNode n1, AstNode n2) {
            return n1.position - n2.position;
        }
    }

    public AstNode() {
        super(Token.ERROR);
    }

    /**
     * Constructs a new AstNode
     * @param pos the start position
     */
    public AstNode(int pos) {
        this();
        position = pos;
    }

    /**
     * Constructs a new AstNode
     * @param pos the start position
     * @param len the number of characters spanned by the node in the source
     * text
     */
    public AstNode(int pos, int len) {
        this();
        position = pos;
        length = len;
    }

    /**
     * Returns relative position in parent
     */
    public int getPosition() {
        return position;
    }

    /**
     * Sets relative position in parent
     */
    public void setPosition(int position) {
        this.position = position;
    }

    /**
     * Returns the absolute document position of the node.
     * Computes it by adding the node's relative position
     * to the relative positions of all its parents.
     */
    public int getAbsolutePosition() {
        int pos = position;
        AstNode parent = this.parent;
        while (parent != null) {
            pos += parent.getPosition();
            parent = parent.getParent();
        }
        return pos;
    }

    /**
     * Returns node length
     */
    public int getLength() {
        return length;
    }

    /**
     * Sets node length
     */
    public void setLength(int length) {
        this.length = length;
    }

    /**
     * Sets the node start and end positions.
     * Computes the length as ({@code end} - {@code position}).
     */
    public void setBounds(int position, int end) {
        setPosition(position);
        setLength(end - position);
    }

    /**
     * Make this node's position relative to a parent.
     * Typically only used by the parser when constructing the node.
     * @param parentPosition the absolute parent position; the
     * current node position is assumed to be absolute and is
     * decremented by parentPosition.
     */
    public void setRelative(int parentPosition) {
        this.position -= parentPosition;
    }

    /**
     * Returns the node parent, or {@code null} if it has none
     */
    public AstNode getParent() {
        return parent;
    }

    /**
     * Sets the node parent.  This method automatically adjusts the
     * current node's start position to be relative to the new parent.
     * @param parent the new parent. Can be {@code null}.
     */
    public void setParent(AstNode parent) {
        if (parent == this.parent) {
            return;
        }

        // Convert position back to absolute.
        if (this.parent != null) {
            setRelative(-this.parent.getPosition());
        }

        this.parent = parent;
        if (parent != null) {
            setRelative(parent.getPosition());
        }
    }

    /**
     * Adds a child or function to the end of the block.
     * Sets the parent of the child to this node, and fixes up
     * the start position of the child to be relative to this node.
     * Sets the length of this node to include the new child.
     * @param kid the child
     * @throws IllegalArgumentException if kid is {@code null}
     */
    public void addChild(AstNode kid) {
        assertNotNull(kid);
        int end = kid.getPosition() + kid.getLength();
        setLength(end - this.getPosition());
        addChildToBack(kid);
        kid.setParent(this);
    }

    /**
     * Returns the root of the tree containing this node.
     * @return the {@link AstRoot} at the root of this node's parent
     * chain, or {@code null} if the topmost parent is not an {@code AstRoot}.
     */
    public AstRoot getAstRoot() {
        AstNode parent = this;  // this node could be the AstRoot
        while (parent != null && !(parent instanceof AstRoot)) {
            parent = parent.getParent();
        }
        return (AstRoot)parent;
    }

    /**
     * Emits source code for this node.  Callee is responsible for calling this
     * function recursively on children, incrementing indent as appropriate.<p>
     *
     * Note: if the parser was in error-recovery mode, some AST nodes may have
     * {@code null} children that are expected to be non-{@code null}
     * when no errors are present.  In this situation, the behavior of the
     * {@code toSource} method is undefined: {@code toSource}
     * implementations may assume that the AST node is error-free, since it is
     * intended to be invoked only at runtime after a successful parse.<p>
     *
     * @param depth the current recursion depth, typically beginning at 0
     * when called on the root node.
     */
    public abstract String toSource(int depth);

    /**
     * Prints the source indented to depth 0.
     */
    public String toSource() {
        return this.toSource(0);
    }

    /**
     * Constructs an indentation string.
     * @param indent the number of indentation steps
     */
    public String makeIndent(int indent) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            sb.append("  ");
        }
        return sb.toString();
    }

    /**
     * Returns a short, descriptive name for the node, such as
     * "ArrayComprehension".
     */
    public String shortName() {
        String classname = getClass().getName();
        int last = classname.lastIndexOf(".");
        return classname.substring(last + 1);
    }

    /**
     * Returns the string name for this operator.
     * @param op the token type, e.g. {@link Token#ADD} or {@link Token#TYPEOF}
     * @return the source operator string, such as "+" or "typeof"
     */
    public static String operatorToString(int op) {
        String result = operatorNames.get(op);
        if (result == null)
            throw new IllegalArgumentException("Invalid operator: " + op);
        return result;
    }

    /**
     * Visits this node and its children in an arbitrary order. <p>
     *
     * It's up to each node subclass to decide the order for processing
     * its children.  The subclass also decides (and should document)
     * which child nodes are not passed to the {@code NodeVisitor}.
     * For instance, nodes representing keywords like {@code each} or
     * {@code in} may not be passed to the visitor object.  The visitor
     * can simply query the current node for these children if desired.<p>
     *
     * Generally speaking, the order will be deterministic; the order is
     * whatever order is decided by each child node.  Normally child nodes
     * will try to visit their children in lexical order, but there may
     * be exceptions to this rule.<p>
     *
     * @param visitor the object to call with this node and its children
     */
    public abstract void visit(NodeVisitor visitor);

    // subclasses with potential side effects should override this
    public boolean hasSideEffects()
    {
        switch (getType()) {
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
          case Token.BLOCK:
          case Token.BREAK:
          case Token.CALL:
          case Token.CATCH:
          case Token.CATCH_SCOPE:
          case Token.CONST:
          case Token.CONTINUE:
          case Token.DEC:
          case Token.DELPROP:
          case Token.DEL_REF:
          case Token.DO:
          case Token.ELSE:
          case Token.ENTERWITH:
          case Token.ERROR:         // Avoid cascaded error messages
          case Token.EXPORT:
          case Token.EXPR_RESULT:
          case Token.FINALLY:
          case Token.FUNCTION:
          case Token.FOR:
          case Token.GOTO:
          case Token.IF:
          case Token.IFEQ:
          case Token.IFNE:
          case Token.IMPORT:
          case Token.INC:
          case Token.JSR:
          case Token.LABEL:
          case Token.LEAVEWITH:
          case Token.LET:
          case Token.LETEXPR:
          case Token.LOCAL_BLOCK:
          case Token.LOOP:
          case Token.NEW:
          case Token.REF_CALL:
          case Token.RETHROW:
          case Token.RETURN:
          case Token.RETURN_RESULT:
          case Token.SEMI:
          case Token.SETELEM:
          case Token.SETELEM_OP:
          case Token.SETNAME:
          case Token.SETPROP:
          case Token.SETPROP_OP:
          case Token.SETVAR:
          case Token.SET_REF:
          case Token.SET_REF_OP:
          case Token.SWITCH:
          case Token.TARGET:
          case Token.THROW:
          case Token.TRY:
          case Token.VAR:
          case Token.WHILE:
          case Token.WITH:
          case Token.WITHEXPR:
          case Token.YIELD:
            return true;

          default:
            return false;
        }
    }

    /**
     * Bounces an IllegalArgumentException up if arg is {@code null}.
     * @param arg any method argument
     * @throws IllegalArgumentException if the argument is {@code null}
     */
    protected void assertNotNull(Object arg) {
        if (arg == null)
            throw new IllegalArgumentException("arg cannot be null");
    }

    /**
     * Prints a comma-separated item list into a {@link StringBuilder}.
     * @param items a list to print
     * @param sb a {@link StringBuilder} into which to print
     */
    protected <T extends AstNode> void printList(List<T> items,
                                                 StringBuilder sb) {
        int max = items.size();
        int count = 0;
        for (AstNode item : items) {
            sb.append(item.toSource(0));
            if (count++ < max-1) {
                sb.append(", ");
            } else if (item instanceof EmptyExpression) {
                sb.append(",");
            }
        }
    }

    /**
     * @see Kit#codeBug
     */
    public static RuntimeException codeBug()
        throws RuntimeException
    {
        throw Kit.codeBug();
    }

    // TODO(stevey):  think of a way to have polymorphic toString
    // methods while keeping the ability to use Node.toString for
    // dumping the IR with Token.printTrees.  Most likely:  change
    // Node.toString to be Node.dumpTree and change callers to use that.
    // For now, need original toString, to compare output to old Rhino's.

//     @Override
//     public String toString() {
//         return this.getClass().getName() + ": " +
//             Token.typeToName(getType());
//     }

    /**
     * Returns the innermost enclosing function, or {@code null} if not in a
     * function.  Begins the search with this node's parent.
     * @return the {@link FunctionNode} enclosing this node, else {@code null}
     */
    public FunctionNode getEnclosingFunction() {
        AstNode parent = this.getParent();
        while (parent != null && !(parent instanceof FunctionNode)) {
            parent = parent.getParent();
        }
        return (FunctionNode)parent;
    }

    /**
     * Returns the innermost enclosing {@link Scope} node, or {@code null}
     * if we're not nested in a scope.  Begins the search with this node's parent.
     * Note that this is not the same as the defining scope for a {@link Name}.
     *
     * @return the {@link Scope} enclosing this node, else {@code null}
     */
    public Scope getEnclosingScope() {
        AstNode parent = this.getParent();
        while (parent != null && !(parent instanceof Scope)) {
            parent = parent.getParent();
        }
        return (Scope)parent;
    }

    /**
     * Permits AST nodes to be sorted based on start position and length.
     * This makes it easy to sort Comment and Error nodes into a set of
     * other AST nodes:  just put them all into a {@link java.util.SortedSet},
     * for instance.
     * @param other another node
     * @return -1 if this node's start position is less than {@code other}'s
     * start position.  If tied, -1 if this node's length is less than
     * {@code other}'s length.  If the lengths are equal, sorts abitrarily
     * on hashcode unless the nodes are the same per {@link #equals}.
     */
    public int compareTo(AstNode other) {
        if (this.equals(other)) return 0;
        int abs1 = this.getAbsolutePosition();
        int abs2 = other.getAbsolutePosition();
        if (abs1 < abs2) return -1;
        if (abs2 < abs1) return 1;
        int len1 = this.getLength();
        int len2 = other.getLength();
        if (len1 < len2) return -1;
        if (len2 < len1) return 1;
        return this.hashCode() - other.hashCode();
    }

    /**
     * Returns the depth of this node.  The root is depth 0, its
     * children are depth 1, and so on.
     * @return the node depth in the tree
     */
    public int depth() {
        return parent == null ? 0 : 1 + parent.depth();
    }

    protected static class DebugPrintVisitor implements NodeVisitor {
        private StringBuilder buffer;
        private static final int DEBUG_INDENT = 2;
        public DebugPrintVisitor(StringBuilder buf) {
            buffer = buf;
        }
        public String toString() {
            return buffer.toString();
        }
        private String makeIndent(int depth) {
            StringBuilder sb = new StringBuilder(DEBUG_INDENT * depth);
            for (int i = 0; i < (DEBUG_INDENT * depth); i++) {
                sb.append(" ");
            }
            return sb.toString();
        }
        public boolean visit(AstNode node) {
            int tt = node.getType();
            String name = Token.typeToName(tt);
            buffer.append(node.getAbsolutePosition()).append("\t");
            buffer.append(makeIndent(node.depth()));
            buffer.append(name).append(" ");
            buffer.append(node.getPosition()).append(" ");
            buffer.append(node.getLength());
            if (tt == Token.NAME) {
                buffer.append(" ").append(((Name)node).getIdentifier());
            }
            buffer.append("\n");
            return true;  // process kids
        }
    }

    /**
     * Return the line number recorded for this node.
     * If no line number was recorded, searches the parent chain.
     * @return the nearest line number, or -1 if none was found
     */
    @Override
    public int getLineno() {
        if (lineno != -1)
            return lineno;
        if (parent != null)
            return parent.getLineno();
        return -1;
    }

    /**
     * Returns a debugging representation of the parse tree
     * starting at this node.
     * @return a very verbose indented printout of the tree.
     * The format of each line is:  abs-pos  name position length [identifier]
     */
    public String debugPrint() {
        DebugPrintVisitor dpv = new DebugPrintVisitor(new StringBuilder(1000));
        visit(dpv);
        return dpv.toString();
    }
}
