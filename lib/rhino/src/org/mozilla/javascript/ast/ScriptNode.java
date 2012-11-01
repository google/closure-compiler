/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

import org.mozilla.javascript.Node;
import org.mozilla.javascript.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base type for {@link AstRoot} and {@link FunctionNode} nodes, which need to
 * collect much of the same information.
 */
public class ScriptNode extends Scope {

    private int encodedSourceStart = -1;
    private int encodedSourceEnd = -1;
    private String sourceName;
    private String encodedSource;
    private int endLineno = -1;

    private List<FunctionNode> functions;
    private List<RegExpLiteral> regexps;
    private List<FunctionNode> EMPTY_LIST = Collections.emptyList();

    private List<Symbol> symbols = new ArrayList<Symbol>(4);
    private int paramCount = 0;
    private String[] variableNames;
    private boolean[] isConsts;

    private Object compilerData;
    private int tempNumber = 0;

    {
        // during parsing, a ScriptNode or FunctionNode's top scope is itself
        this.top = this;
        this.type = Token.SCRIPT;
    }

    public ScriptNode() {
    }

    public ScriptNode(int pos) {
        super(pos);
    }

    /**
     * Returns the URI, path or descriptive text indicating the origin
     * of this script's source code.
     */
    public String getSourceName() {
        return sourceName;
    }

    /**
     * Sets the URI, path or descriptive text indicating the origin
     * of this script's source code.
     */
    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    /**
     * Returns the start offset of the encoded source.
     * Only valid if {@link #getEncodedSource} returns non-{@code null}.
     */
    public int getEncodedSourceStart() {
        return encodedSourceStart;
    }

    /**
     * Used by code generator.
     * @see #getEncodedSource
     */
    public void setEncodedSourceStart(int start) {
        this.encodedSourceStart = start;
    }

    /**
     * Returns the end offset of the encoded source.
     * Only valid if {@link #getEncodedSource} returns non-{@code null}.
     */
    public int getEncodedSourceEnd() {
        return encodedSourceEnd;
    }

    /**
     * Used by code generator.
     * @see #getEncodedSource
     */
    public void setEncodedSourceEnd(int end) {
        this.encodedSourceEnd = end;
    }

    /**
     * Used by code generator.
     * @see #getEncodedSource
     */
    public void setEncodedSourceBounds(int start, int end) {
        this.encodedSourceStart = start;
        this.encodedSourceEnd = end;
    }

    /**
     * Used by the code generator.
     * @see #getEncodedSource
     */
    public void setEncodedSource(String encodedSource) {
        this.encodedSource = encodedSource;
    }

    /**
     * Returns a canonical version of the source for this script or function,
     * for use in implementing the {@code Object.toSource} method of
     * JavaScript objects.  This source encoding is only recorded during code
     * generation.  It must be passed back to
     * {@link org.mozilla.javascript.Decompiler#decompile} to construct the
     * human-readable source string.<p>
     *
     * Given a parsed AST, you can always convert it to source code using the
     * {@link AstNode#toSource} method, although it's not guaranteed to produce
     * exactly the same results as {@code Object.toSource} with respect to
     * formatting, parenthesization and other details.
     *
     * @return the encoded source, or {@code null} if it was not recorded.
     */
    public String getEncodedSource() {
        return encodedSource;
    }

    public int getBaseLineno() {
        return lineno;
    }

    /**
     * Sets base (starting) line number for this script or function.
     * This is a one-time operation, and throws an exception if the
     * line number has already been set.
     */
    public void setBaseLineno(int lineno) {
        if (lineno < 0 || this.lineno >= 0) codeBug();
        this.lineno = lineno;
    }

    public int getEndLineno() {
        return endLineno;
    }

    public void setEndLineno(int lineno) {
        // One time action
        if (lineno < 0 || endLineno >= 0) codeBug();
        endLineno = lineno;
    }

    public int getFunctionCount() {
        return functions == null ? 0 : functions.size();
    }

    public FunctionNode getFunctionNode(int i) {
        return functions.get(i);
    }

    public List<FunctionNode> getFunctions() {
        return functions == null ? EMPTY_LIST : functions;
    }

    /**
     * Adds a {@link FunctionNode} to the functions table for codegen.
     * Does not set the parent of the node.
     * @return the index of the function within its parent
     */
    public int addFunction(FunctionNode fnNode) {
        if (fnNode == null) codeBug();
        if (functions == null)
            functions = new ArrayList<FunctionNode>();
        functions.add(fnNode);
        return functions.size() - 1;
    }

    public int getRegexpCount() {
        return regexps == null ? 0 : regexps.size();
    }

    public String getRegexpString(int index) {
        return regexps.get(index).getValue();
    }

    public String getRegexpFlags(int index) {
        return regexps.get(index).getFlags();
    }

    /**
     * Called by IRFactory to add a RegExp to the regexp table.
     */
    public void addRegExp(RegExpLiteral re) {
        if (re == null) codeBug();
        if (regexps == null)
            regexps = new ArrayList<RegExpLiteral>();
        regexps.add(re);
        re.putIntProp(REGEXP_PROP, regexps.size() - 1);
    }

    public int getIndexForNameNode(Node nameNode) {
        if (variableNames == null) codeBug();
        Scope node = nameNode.getScope();
        Symbol symbol = node == null
            ? null
            : node.getSymbol(((Name)nameNode).getIdentifier());
        return (symbol == null) ? -1 : symbol.getIndex();
    }

    public String getParamOrVarName(int index) {
        if (variableNames == null) codeBug();
        return variableNames[index];
    }

    public int getParamCount() {
        return paramCount;
    }

    public int getParamAndVarCount() {
        if (variableNames == null) codeBug();
        return symbols.size();
    }

    public String[] getParamAndVarNames() {
        if (variableNames == null) codeBug();
        return variableNames;
    }

    public boolean[] getParamAndVarConst() {
        if (variableNames == null) codeBug();
        return isConsts;
    }

    void addSymbol(Symbol symbol) {
        if (variableNames != null) codeBug();
        if (symbol.getDeclType() == Token.LP) {
            paramCount++;
        }
        symbols.add(symbol);
    }

    public List<Symbol> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<Symbol> symbols) {
        this.symbols = symbols;
    }

    /**
     * Assign every symbol a unique integer index. Generate arrays of variable
     * names and constness that can be indexed by those indices.
     *
     * @param flattenAllTables if true, flatten all symbol tables,
     * included nested block scope symbol tables. If false, just flatten the
     * script's or function's symbol table.
     */
    public void flattenSymbolTable(boolean flattenAllTables) {
        if (!flattenAllTables) {
            List<Symbol> newSymbols = new ArrayList<Symbol>();
            if (this.symbolTable != null) {
                // Just replace "symbols" with the symbols in this object's
                // symbol table. Can't just work from symbolTable map since
                // we need to retain duplicate parameters.
                for (int i = 0; i < symbols.size(); i++) {
                    Symbol symbol = symbols.get(i);
                    if (symbol.getContainingTable() == this) {
                        newSymbols.add(symbol);
                    }
                }
            }
            symbols = newSymbols;
        }
        variableNames = new String[symbols.size()];
        isConsts = new boolean[symbols.size()];
        for (int i = 0; i < symbols.size(); i++) {
            Symbol symbol = symbols.get(i);
            variableNames[i] = symbol.getName();
            isConsts[i] = symbol.getDeclType() == Token.CONST;
            symbol.setIndex(i);
        }
    }

    public Object getCompilerData() {
        return compilerData;
    }

    public void setCompilerData(Object data) {
        assertNotNull(data);
        // Can only call once
        if (compilerData != null)
            throw new IllegalStateException();
        compilerData = data;
    }

    public String getNextTempName() {
        return "$" + tempNumber++;
    }

    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            for (Node kid : this) {
                ((AstNode)kid).visit(v);
            }
        }
    }
}
