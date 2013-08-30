/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import org.mozilla.javascript.ast.*;  // we use basically every class

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * This class implements the JavaScript parser.<p>
 *
 * It is based on the SpiderMonkey C source files jsparse.c and jsparse.h in the
 * jsref package.<p>
 *
 * The parser generates an {@link AstRoot} parse tree representing the source
 * code.  No tree rewriting is permitted at this stage, so that the parse tree
 * is a faithful representation of the source for frontend processing tools and
 * IDEs.<p>
 *
 * This parser implementation is not intended to be reused after a parse
 * finishes, and will throw an IllegalStateException() if invoked again.<p>
 *
 * @see TokenStream
 *
 */
public class Parser
{
    /**
     * Maximum number of allowed function or constructor arguments,
     * to follow SpiderMonkey.
     */
    public static final int ARGC_LIMIT = 1 << 16;

    // TokenInformation flags : currentFlaggedToken stores them together
    // with token type
    final static int
        CLEAR_TI_MASK    = 0xFFFF,  // mask to clear token information bits
        TI_AFTER_EOL     = 1 << 16, // first token of the source line
        TI_CHECK_LABEL   = 1 << 17; // indicates to check for label

    CompilerEnvirons compilerEnv;
    private ErrorReporter errorReporter;
    private IdeErrorReporter errorCollector;
    private String sourceURI;
    private char[] sourceChars;

    boolean calledByCompileFunction;  // ugly - set directly by Context
    private boolean parseFinished;  // set when finished to prevent reuse

    private TokenStream ts;
    private int currentFlaggedToken = Token.EOF;
    private int currentToken;
    private int syntaxErrorCount;

    private List<Comment> scannedComments;
    private List<Comment> jsdocs;

    protected int nestingOfFunction;
    private LabeledStatement currentLabel;
    private boolean inDestructuringAssignment;
    protected boolean inUseStrictDirective;

    // The following are per function variables and should be saved/restored
    // during function parsing.  See PerFunctionVariables class below.
    ScriptNode currentScriptOrFn;
    Scope currentScope;
    private int endFlags;
    private boolean inForInit;  // bound temporarily during forStatement()
    private Map<String,LabeledStatement> labelSet;
    private List<Loop> loopSet;
    private List<Jump> loopAndSwitchSet;
    // end of per function variables

    // Lacking 2-token lookahead, labels become a problem.
    // These vars store the token info of the last matched name,
    // iff it wasn't the last matched token.
    private int prevNameTokenStart;
    private String prevNameTokenString = "";
    private int prevNameTokenLineno;

    // Exception to unwind
    private static class ParserException extends RuntimeException
    {
        static final long serialVersionUID = 5882582646773765630L;
    }

    public Parser() {
        this(new CompilerEnvirons());
    }

    public Parser(CompilerEnvirons compilerEnv) {
        this(compilerEnv, compilerEnv.getErrorReporter());
    }

    public Parser(CompilerEnvirons compilerEnv, ErrorReporter errorReporter) {
        this.compilerEnv = compilerEnv;
        this.errorReporter = errorReporter;
        if (errorReporter instanceof IdeErrorReporter) {
            errorCollector = (IdeErrorReporter)errorReporter;
        }
    }

    // Add a strict warning on the last matched token.
    void addStrictWarning(String messageId, String messageArg) {
        int beg = -1, end = -1;
        if (ts != null) {
            beg = ts.tokenBeg;
            end = ts.tokenEnd - ts.tokenBeg;
        }
        addStrictWarning(messageId, messageArg, beg, end);
    }

    void addStrictWarning(String messageId, String messageArg,
                          int position, int length) {
        if (compilerEnv.isStrictMode())
            addWarning(messageId, messageArg, position, length);
    }

    void addWarning(String messageId, String messageArg) {
        int beg = -1, end = -1;
        if (ts != null) {
            beg = ts.tokenBeg;
            end = ts.tokenEnd - ts.tokenBeg;
        }
        addWarning(messageId, messageArg, beg, end);
    }

    void addWarning(String messageId, int position, int length) {
        addWarning(messageId, null, position, length);
    }

    void addWarning(String messageId, String messageArg,
                    int position, int length)
    {
        String message = lookupMessage(messageId, messageArg);
        if (compilerEnv.reportWarningAsError()) {
            addError(messageId, messageArg, position, length);
        } else if (errorCollector != null) {
            errorCollector.warning(message, sourceURI, position, length);
        } else {
            errorReporter.warning(message, sourceURI, ts.getLineno(),
                                  ts.getLine(), ts.getOffset());
        }
    }

    void addError(String messageId) {
        addError(messageId, ts.tokenBeg, ts.tokenEnd - ts.tokenBeg);
    }

    void addError(String messageId, int position, int length) {
        addError(messageId, null, position, length);
    }

    void addError(String messageId, String messageArg) {
        addError(messageId, messageArg, ts.tokenBeg,
                 ts.tokenEnd - ts.tokenBeg);
    }

    void addError(String messageId, String messageArg, int position, int length)
    {
        ++syntaxErrorCount;
        String message = lookupMessage(messageId, messageArg);
        if (errorCollector != null) {
            errorCollector.error(message, sourceURI, position, length);
        } else {
            int lineno = 1, offset = 1;
            String line = "";
            if (ts != null) {  // happens in some regression tests
                lineno = ts.getLineno();
                line = ts.getLine();
                offset = ts.getOffset();
            }
            errorReporter.error(message, sourceURI, lineno, line, offset);
        }
    }

    String lookupMessage(String messageId) {
        return lookupMessage(messageId, null);
    }

    String lookupMessage(String messageId, String messageArg) {
        return messageArg == null
            ? ScriptRuntime.getMessage0(messageId)
            : ScriptRuntime.getMessage1(messageId, messageArg);
    }

    void reportError(String messageId) {
        reportError(messageId, null);
    }

    void reportError(String messageId, String messageArg) {
        if (ts == null) {  // happens in some regression tests
            reportError(messageId, messageArg, 1, 1);
        } else {
            reportError(messageId, messageArg, ts.tokenBeg,
                        ts.tokenEnd - ts.tokenBeg);
        }
    }

    void reportError(String messageId, int position, int length)
    {
        reportError(messageId, null, position, length);
    }

    void reportError(String messageId, String messageArg, int position,
                     int length)
    {
        addError(messageId, position, length);

        if (!compilerEnv.recoverFromErrors()) {
            throw new ParserException();
        }
    }

    // Computes the absolute end offset of node N.
    // Use with caution!  Assumes n.getPosition() is -absolute-, which
    // is only true before the node is added to its parent.
    private int getNodeEnd(AstNode n) {
        return n.getPosition() + n.getLength();
    }

    private void recordComment(int lineno, String comment) {
        if (scannedComments == null) {
            scannedComments = new ArrayList<Comment>();
            jsdocs = new ArrayList<Comment>();
        }
        Comment commentNode = new Comment(ts.tokenBeg,
                                          ts.getTokenLength(),
                                          ts.commentType,
                                          comment);
        if (ts.commentType == Token.CommentType.JSDOC &&
            compilerEnv.isRecordingLocalJsDocComments()) {
            jsdocs.add(commentNode);
        }
        commentNode.setLineno(lineno);
        scannedComments.add(commentNode);
    }

    private int getNumberOfEols(String comment) {
      int lines = 0;
      for (int i = comment.length()-1; i >= 0; i--) {
        if (comment.charAt(i) == '\n') {
          lines++;
        }
      }
      return lines;
    }


    // Returns the next token without consuming it.
    // If previous token was consumed, calls scanner to get new token.
    // If previous token was -not- consumed, returns it (idempotent).
    //
    // This function will not return a newline (Token.EOL - instead, it
    // gobbles newlines until it finds a non-newline token, and flags
    // that token as appearing just after a newline.
    //
    // This function will also not return a Token.COMMENT.  Instead, it
    // records comments in the scannedComments list.  If the token
    // returned by this function immediately follows a jsdoc comment,
    // the token is flagged as such.
    //
    // Note that this function always returned the un-flagged token!
    // The flags, if any, are saved in currentFlaggedToken.
    private int peekToken()
        throws IOException
    {
        // By far the most common case:  last token hasn't been consumed,
        // so return already-peeked token.
        if (currentFlaggedToken != Token.EOF) {
            return currentToken;
        }

        int lineno = ts.getLineno();
        int tt = ts.getToken();
        boolean sawEOL = false;

        // process comments and whitespace
        while (tt == Token.EOL || tt == Token.COMMENT) {
            if (tt == Token.EOL) {
                lineno++;
                sawEOL = true;
            } else {
                if (compilerEnv.isRecordingComments()) {
                    String comment = ts.getAndResetCurrentComment();
                    recordComment(lineno, comment);
                    // Comments may contain multiple lines, get the number
                    // of EoLs and increase the lineno
                    lineno += getNumberOfEols(comment);
                }
            }
            tt = ts.getToken();
        }

        currentToken = tt;
        currentFlaggedToken = tt | (sawEOL ? TI_AFTER_EOL : 0);
        return currentToken;  // return unflagged token
    }

    private int peekFlaggedToken()
        throws IOException
    {
        peekToken();
        return currentFlaggedToken;
    }

    private void consumeToken() {
        currentFlaggedToken = Token.EOF;
    }

    private int nextToken()
        throws IOException
    {
        int tt = peekToken();
        consumeToken();
        return tt;
    }

    private int nextFlaggedToken()
        throws IOException
    {
        peekToken();
        int ttFlagged = currentFlaggedToken;
        consumeToken();
        return ttFlagged;
    }

    private boolean matchToken(int toMatch)
        throws IOException
    {
        if (peekToken() != toMatch) {
            return false;
        }
        consumeToken();
        return true;
    }

    // Returns Token.EOL if the current token follows a newline, else returns
    // the current token.  Used in situations where we don't consider certain
    // token types valid if they are preceded by a newline.  One example is the
    // postfix ++ or -- operator, which has to be on the same line as its
    // operand.
    private int peekTokenOrEOL()
        throws IOException
    {
        int tt = peekToken();
        // Check for last peeked token flags
        if ((currentFlaggedToken & TI_AFTER_EOL) != 0) {
            tt = Token.EOL;
        }
        return tt;
    }

    private boolean mustMatchToken(int toMatch, String messageId)
        throws IOException
    {
        return mustMatchToken(toMatch, messageId, ts.tokenBeg,
                              ts.tokenEnd - ts.tokenBeg);
    }

    private boolean mustMatchToken(int toMatch, String msgId, int pos, int len)
        throws IOException
    {
        if (matchToken(toMatch)) {
            return true;
        }
        reportError(msgId, pos, len);
        return false;
    }

    private void mustHaveXML() {
        if (!compilerEnv.isXmlAvailable()) {
            reportError("msg.XML.not.available");
        }
    }

    public boolean eof() {
        return ts.eof();
    }

    boolean insideFunction() {
        return nestingOfFunction != 0;
    }

    void pushScope(Scope scope) {
        Scope parent = scope.getParentScope();
        // During codegen, parent scope chain may already be initialized,
        // in which case we just need to set currentScope variable.
        if (parent != null) {
            if (parent != currentScope)
                codeBug();
        } else {
            currentScope.addChildScope(scope);
        }
        currentScope = scope;
    }

    void popScope() {
        currentScope = currentScope.getParentScope();
    }

    private void enterLoop(Loop loop) {
        if (loopSet == null)
            loopSet = new ArrayList<Loop>();
        loopSet.add(loop);
        if (loopAndSwitchSet == null)
            loopAndSwitchSet = new ArrayList<Jump>();
        loopAndSwitchSet.add(loop);
        pushScope(loop);
        if (currentLabel != null) {
            currentLabel.setStatement(loop);
            currentLabel.getFirstLabel().setLoop(loop);
            // This is the only time during parsing that we set a node's parent
            // before parsing the children.  In order for the child node offsets
            // to be correct, we adjust the loop's reported position back to an
            // absolute source offset, and restore it when we call exitLoop().
            loop.setRelative(-currentLabel.getPosition());
        }
    }

    private void exitLoop() {
        Loop loop = loopSet.remove(loopSet.size() - 1);
        loopAndSwitchSet.remove(loopAndSwitchSet.size() - 1);
        if (loop.getParent() != null) {  // see comment in enterLoop
            loop.setRelative(loop.getParent().getPosition());
        }
        popScope();
    }

    private void enterSwitch(SwitchStatement node) {
        if (loopAndSwitchSet == null)
            loopAndSwitchSet = new ArrayList<Jump>();
        loopAndSwitchSet.add(node);
    }

    private void exitSwitch() {
        loopAndSwitchSet.remove(loopAndSwitchSet.size() - 1);
    }

    /**
     * Builds a parse tree from the given source string.
     *
     * @return an {@link AstRoot} object representing the parsed program.  If
     * the parse fails, {@code null} will be returned.  (The parse failure will
     * result in a call to the {@link ErrorReporter} from
     * {@link CompilerEnvirons}.)
     */
    public AstRoot parse(String sourceString, String sourceURI, int lineno)
    {
        if (parseFinished) throw new IllegalStateException("parser reused");
        this.sourceURI = sourceURI;
        if (compilerEnv.isIdeMode()) {
            this.sourceChars = sourceString.toCharArray();
        }
        this.ts = new TokenStream(this, null, sourceString, lineno);
        try {
            AstRoot r = parse();
            new AttachJsDocs().attachComments(r, jsdocs);
            return r;
        } catch (IOException iox) {
            // Should never happen
            throw new IllegalStateException();
        } finally {
            parseFinished = true;
        }
    }

    /**
     * Builds a parse tree from the given sourcereader.
     * @see #parse(String,String,int)
     * @throws IOException if the {@link Reader} encounters an error
     */
    public AstRoot parse(Reader sourceReader, String sourceURI, int lineno)
        throws IOException
    {
        if (parseFinished) throw new IllegalStateException("parser reused");
        if (compilerEnv.isIdeMode()) {
            return parse(readFully(sourceReader), sourceURI, lineno);
        }
        try {
            this.sourceURI = sourceURI;
            ts = new TokenStream(this, sourceReader, null, lineno);
            AstRoot r = parse();
            new AttachJsDocs().attachComments(r, jsdocs);
            return r;
        } finally {
            parseFinished = true;
        }
    }

    private AstRoot parse() throws IOException
    {
        int pos = 0;
        AstRoot root = new AstRoot(pos);
        currentScope = currentScriptOrFn = root;

        int baseLineno = ts.lineno;  // line number where source starts
        int end = pos;  // in case source is empty

        boolean inDirectivePrologue = true;
        boolean savedStrictMode = inUseStrictDirective;
        // TODO: eval code should get strict mode from invoking code
        inUseStrictDirective = false;

        try {
            for (;;) {
                int tt = peekToken();
                if (tt <= Token.EOF) {
                    break;
                }

                AstNode n;
                if (tt == Token.FUNCTION) {
                    consumeToken();
                    try {
                        n = function(calledByCompileFunction
                                     ? FunctionNode.FUNCTION_EXPRESSION
                                     : FunctionNode.FUNCTION_STATEMENT);
                    } catch (ParserException e) {
                        break;
                    }
                } else {
                    n = statement();
                    if (inDirectivePrologue) {
                        String directive = getDirective(n);
                        if (directive == null) {
                            inDirectivePrologue = false;
                        } else if (directive.equals("use strict")) {
                            inUseStrictDirective = true;
                            root.setInStrictMode(true);
                        }
                    }

                }
                end = getNodeEnd(n);
                root.addChildToBack(n);
                n.setParent(root);
            }
        } catch (StackOverflowError ex) {
            String msg = lookupMessage("msg.too.deep.parser.recursion");
            if (!compilerEnv.isIdeMode())
                throw Context.reportRuntimeError(msg, sourceURI,
                                                 ts.lineno, null, 0);
        } finally {
            inUseStrictDirective = savedStrictMode;
        }

        if (this.syntaxErrorCount != 0) {
            String msg = String.valueOf(this.syntaxErrorCount);
            msg = lookupMessage("msg.got.syntax.errors", msg);
            if (!compilerEnv.isIdeMode())
                throw errorReporter.runtimeError(msg, sourceURI, baseLineno,
                                                 null, 0);
        }

        // add comments to root in lexical order
        if (scannedComments != null) {
            // If we find a comment beyond end of our last statement or
            // function, extend the root bounds to the end of that comment.
            int last = scannedComments.size() - 1;
            end = Math.max(end, getNodeEnd(scannedComments.get(last)));
            for (Comment c : scannedComments) {
                root.addComment(c);
            }
        }

        root.setLength(end - pos);
        root.setSourceName(sourceURI);
        root.setBaseLineno(baseLineno);
        root.setEndLineno(ts.lineno);
        return root;
    }

    private AstNode parseFunctionBody()
        throws IOException
    {
        boolean isExpressionClosure = false;
        if (!matchToken(Token.LC)) {
            if (compilerEnv.getLanguageVersion() < Context.VERSION_1_8) {
                reportError("msg.no.brace.body");
            } else {
                isExpressionClosure = true;
            }
        }
        ++nestingOfFunction;
        int pos = ts.tokenBeg;
        Block pn = new Block(pos);  // starts at LC position

        boolean inDirectivePrologue = true;
        boolean savedStrictMode = inUseStrictDirective;
        // Don't set 'inUseStrictDirective' to false: inherit strict mode.

        pn.setLineno(ts.lineno);
        try {
            if (isExpressionClosure) {
                ReturnStatement n = new ReturnStatement(ts.lineno);
                n.setReturnValue(assignExpr());
                // expression closure flag is required on both nodes
                n.putProp(Node.EXPRESSION_CLOSURE_PROP, Boolean.TRUE);
                pn.putProp(Node.EXPRESSION_CLOSURE_PROP, Boolean.TRUE);
                pn.addStatement(n);
            } else {
                bodyLoop: for (;;) {
                    AstNode n;
                    int tt = peekToken();
                    switch (tt) {
                        case Token.ERROR:
                        case Token.EOF:
                        case Token.RC:
                            break bodyLoop;

                        case Token.FUNCTION:
                            consumeToken();
                            n = function(FunctionNode.FUNCTION_STATEMENT);
                            break;
                        default:
                            n = statement();
                            if (inDirectivePrologue) {
                                String directive = getDirective(n);
                                if (directive == null) {
                                    inDirectivePrologue = false;
                                } else if (directive.equals("use strict")) {
                                    inUseStrictDirective = true;
                                }
                            }
                            break;
                    }
                    pn.addStatement(n);
                }
            }
        } catch (ParserException e) {
            // Ignore it
        } finally {
            --nestingOfFunction;
            inUseStrictDirective = savedStrictMode;
        }

        int end = ts.tokenEnd;
        if (!isExpressionClosure && mustMatchToken(Token.RC, "msg.no.brace.after.body"))
            end = ts.tokenEnd;
        pn.setLength(end - pos);
        return pn;
    }

    private String getDirective(AstNode n) {
        if (n instanceof ExpressionStatement) {
            AstNode e = ((ExpressionStatement) n).getExpression();
            if (e instanceof StringLiteral) {
                return ((StringLiteral) e).getValue();
            }
        }
        return null;
    }

    private void  parseFunctionParams(FunctionNode fnNode)
        throws IOException
    {
        if (matchToken(Token.RP)) {
            fnNode.setRp(ts.tokenBeg - fnNode.getPosition());
            return;
        }
        // Would prefer not to call createDestructuringAssignment until codegen,
        // but the symbol definitions have to happen now, before body is parsed.
        Map<String, Node> destructuring = null;
        Set<String> paramNames = new HashSet<String>();
        do {
            int tt = peekToken();
            if (tt == Token.LB || tt == Token.LC) {
                AstNode expr = destructuringPrimaryExpr();
                markDestructuring(expr);
                fnNode.addParam(expr);
                // Destructuring assignment for parameters: add a dummy
                // parameter name, and add a statement to the body to initialize
                // variables from the destructuring assignment
                if (destructuring == null) {
                    destructuring = new HashMap<String, Node>();
                }
                String pname = currentScriptOrFn.getNextTempName();
                defineSymbol(Token.LP, pname, false);
                destructuring.put(pname, expr);
            } else {
                if (mustMatchToken(Token.NAME, "msg.no.parm")) {
                    fnNode.addParam(createNameNode());
                    String paramName = ts.getString();
                    defineSymbol(Token.LP, paramName);
                    if (this.inUseStrictDirective) {
                        if ("eval".equals(paramName) ||
                            "arguments".equals(paramName))
                        {
                            reportError("msg.bad.id.strict", paramName);
                        }
                        if (paramNames.contains(paramName))
                            addError("msg.dup.param.strict", paramName);
                        paramNames.add(paramName);
                    }
                } else {
                    fnNode.addParam(makeErrorNode());
                }
            }
        } while (matchToken(Token.COMMA));

        if (destructuring != null) {
            Node destructuringNode = new Node(Token.COMMA);
            // Add assignment helper for each destructuring parameter
            for (Map.Entry<String, Node> param: destructuring.entrySet()) {
                Node assign = createDestructuringAssignment(Token.VAR,
                        param.getValue(), createName(param.getKey()));
                destructuringNode.addChildToBack(assign);

            }
            fnNode.putProp(Node.DESTRUCTURING_PARAMS, destructuringNode);
        }

        if (mustMatchToken(Token.RP, "msg.no.paren.after.parms")) {
            fnNode.setRp(ts.tokenBeg - fnNode.getPosition());
        }
    }

    private FunctionNode function(int type)
        throws IOException
    {
        int syntheticType = type;
        int baseLineno = ts.lineno;  // line number where source starts
        int functionSourceStart = ts.tokenBeg;  // start of "function" kwd
        Name name = null;
        AstNode memberExprNode = null;

        if (matchToken(Token.NAME)) {
            name = createNameNode(true, Token.NAME);
            if (inUseStrictDirective) {
                String id = name.getIdentifier();
                if ("eval".equals(id)|| "arguments".equals(id)) {
                    reportError("msg.bad.id.strict", id);
                }
            }
            if (!matchToken(Token.LP)) {
                if (compilerEnv.isAllowMemberExprAsFunctionName()) {
                    AstNode memberExprHead = name;
                    name = null;
                    memberExprNode = memberExprTail(false, memberExprHead);
                }
                mustMatchToken(Token.LP, "msg.no.paren.parms");
            }
        } else if (matchToken(Token.LP)) {
            // Anonymous function:  leave name as null
        } else {
            if (compilerEnv.isAllowMemberExprAsFunctionName()) {
                // Note that memberExpr can not start with '(' like
                // in function (1+2).toString(), because 'function (' already
                // processed as anonymous function
                memberExprNode = memberExpr(false);
            }
            mustMatchToken(Token.LP, "msg.no.paren.parms");
        }
        int lpPos = currentToken == Token.LP ? ts.tokenBeg : -1;

        if (memberExprNode != null) {
            syntheticType = FunctionNode.FUNCTION_EXPRESSION;
        }

        if (syntheticType != FunctionNode.FUNCTION_EXPRESSION
            && name != null && name.length() > 0) {
            // Function statements define a symbol in the enclosing scope
            defineSymbol(Token.FUNCTION, name.getIdentifier());
        }

        FunctionNode fnNode = new FunctionNode(functionSourceStart, name);
        fnNode.setFunctionType(type);
        if (lpPos != -1)
            fnNode.setLp(lpPos - functionSourceStart);

        PerFunctionVariables savedVars = new PerFunctionVariables(fnNode);
        try {
            parseFunctionParams(fnNode);
            fnNode.setBody(parseFunctionBody());
            fnNode.setEncodedSourceBounds(functionSourceStart, ts.tokenEnd);
            fnNode.setLength(ts.tokenEnd - functionSourceStart);

            if (compilerEnv.isStrictMode()
                && !fnNode.getBody().hasConsistentReturnUsage()) {
                String msg = (name != null && name.length() > 0)
                           ? "msg.no.return.value"
                           : "msg.anon.no.return.value";
                addStrictWarning(msg, name == null ? "" : name.getIdentifier());
            }
        } finally {
            savedVars.restore();
        }

        if (memberExprNode != null) {
            // TODO(stevey): fix missing functionality
            Kit.codeBug();
            fnNode.setMemberExprNode(memberExprNode);  // rewrite later
            /* old code:
            if (memberExprNode != null) {
                pn = nf.createAssignment(Token.ASSIGN, memberExprNode, pn);
                if (functionType != FunctionNode.FUNCTION_EXPRESSION) {
                    // XXX check JScript behavior: should it be createExprStatement?
                    pn = nf.createExprStatementNoReturn(pn, baseLineno);
                }
            }
            */
        }

        fnNode.setSourceName(sourceURI);
        fnNode.setBaseLineno(baseLineno);
        fnNode.setEndLineno(ts.lineno);

        // Set the parent scope.  Needed for finding undeclared vars.
        // Have to wait until after parsing the function to set its parent
        // scope, since defineSymbol needs the defining-scope check to stop
        // at the function boundary when checking for redeclarations.
        if (compilerEnv.isIdeMode()) {
            fnNode.setParentScope(currentScope);
        }
        return fnNode;
    }

    // This function does not match the closing RC: the caller matches
    // the RC so it can provide a suitable error message if not matched.
    // This means it's up to the caller to set the length of the node to
    // include the closing RC.  The node start pos is set to the
    // absolute buffer start position, and the caller should fix it up
    // to be relative to the parent node.  All children of this block
    // node are given relative start positions and correct lengths.

    private AstNode statements(AstNode parent) throws IOException {
        if (currentToken != Token.LC  // assertion can be invalid in bad code
            && !compilerEnv.isIdeMode()) codeBug();
        int pos = ts.tokenBeg;
        AstNode block = parent != null ? parent : new Block(pos);
        block.setLineno(ts.lineno);

        int tt;
        while ((tt = peekToken()) > Token.EOF && tt != Token.RC) {
            block.addChild(statement());
        }
        block.setLength(ts.tokenBeg - pos);
        return block;
    }

    private AstNode statements() throws IOException {
        return statements(null);
    }

    private static class ConditionData {
        AstNode condition;
        int lp = -1;
        int rp = -1;
    }

    // parse and return a parenthesized expression
    private ConditionData condition()
        throws IOException
    {
        ConditionData data = new ConditionData();

        if (mustMatchToken(Token.LP, "msg.no.paren.cond"))
            data.lp = ts.tokenBeg;

        data.condition = expr();

        if (mustMatchToken(Token.RP, "msg.no.paren.after.cond"))
            data.rp = ts.tokenBeg;

        // Report strict warning on code like "if (a = 7) ...". Suppress the
        // warning if the condition is parenthesized, like "if ((a = 7)) ...".
        if (data.condition instanceof Assignment) {
            addStrictWarning("msg.equal.as.assign", "",
                             data.condition.getPosition(),
                             data.condition.getLength());
        }
        return data;
    }

    private AstNode statement()
        throws IOException
    {
        int pos = ts.tokenBeg;
        try {
            AstNode pn = statementHelper();
            if (pn != null) {
                if (compilerEnv.isStrictMode() && !pn.hasSideEffects()) {
                    int beg = pn.getPosition();
                    beg = Math.max(beg, lineBeginningFor(beg));
                    addStrictWarning(pn instanceof EmptyStatement
                                     ? "msg.extra.trailing.semi"
                                     : "msg.no.side.effects",
                                     "", beg, nodeEnd(pn) - beg);
                }
                return pn;
            }
        } catch (ParserException e) {
            // an ErrorNode was added to the ErrorReporter
        }

        // error:  skip ahead to a probable statement boundary
        guessingStatementEnd: for (;;) {
            int tt = peekTokenOrEOL();
            consumeToken();
            switch (tt) {
              case Token.ERROR:
              case Token.EOF:
              case Token.EOL:
              case Token.SEMI:
                break guessingStatementEnd;
            }
        }
        // We don't make error nodes explicitly part of the tree;
        // they get added to the ErrorReporter.  May need to do
        // something different here.
        return new EmptyStatement(pos, ts.tokenBeg - pos);
    }

    private AstNode statementHelper()
        throws IOException
    {
        // If the statement is set, then it's been told its label by now.
        if (currentLabel != null && currentLabel.getStatement() != null)
            currentLabel = null;

        AstNode pn = null;
        int tt = peekToken(), pos = ts.tokenBeg;

        switch (tt) {
          case Token.IF:
              return ifStatement();

          case Token.SWITCH:
              return switchStatement();

          case Token.WHILE:
              return whileLoop();

          case Token.DO:
              return doLoop();

          case Token.FOR:
              return forLoop();

          case Token.TRY:
              return tryStatement();

          case Token.THROW:
              pn = throwStatement();
              break;

          case Token.BREAK:
              pn = breakStatement();
              break;

          case Token.CONTINUE:
              pn = continueStatement();
              break;

          case Token.WITH:
              if (this.inUseStrictDirective) {
                  reportError("msg.no.with.strict");
              }
              return withStatement();

          case Token.CONST:
          case Token.VAR:
              consumeToken();
              int lineno = ts.lineno;
              pn = variables(currentToken, ts.tokenBeg, true);
              pn.setLineno(lineno);
              break;

          case Token.LET:
              pn = letStatement();
              if (pn instanceof VariableDeclaration
                  && peekToken() == Token.SEMI)
                  break;
              return pn;

          case Token.RETURN:
          case Token.YIELD:
              pn = returnOrYield(tt, false);
              break;

          case Token.DEBUGGER:
              consumeToken();
              pn = new KeywordLiteral(ts.tokenBeg,
                                      ts.tokenEnd - ts.tokenBeg, tt);
              pn.setLineno(ts.lineno);
              break;

          case Token.LC:
              return block();

          case Token.ERROR:
              consumeToken();
              return makeErrorNode();

          case Token.SEMI:
              consumeToken();
              pos = ts.tokenBeg;
              pn = new EmptyStatement(pos, ts.tokenEnd - pos);
              pn.setLineno(ts.lineno);
              return pn;

          case Token.FUNCTION:
              consumeToken();
              return function(FunctionNode.FUNCTION_EXPRESSION_STATEMENT);

          case Token.DEFAULT :
              pn = defaultXmlNamespace();
              break;

          case Token.NAME:
              pn = nameOrLabel();
              if (pn instanceof ExpressionStatement)
                  break;
              return pn;  // LabeledStatement

          default:
              lineno = ts.lineno;
              pn = new ExpressionStatement(expr(), !insideFunction());
              pn.setLineno(lineno);
              break;
        }

        autoInsertSemicolon(pn);
        return pn;
    }

    private void autoInsertSemicolon(AstNode pn) throws IOException {
        int ttFlagged = peekFlaggedToken();
        int pos = pn.getPosition();
        switch (ttFlagged & CLEAR_TI_MASK) {
          case Token.SEMI:
              // Consume ';' as a part of expression
              consumeToken();
              // extend the node bounds to include the semicolon.
              pn.setLength(ts.tokenEnd - pos);
              break;
          case Token.ERROR:
          case Token.EOF:
          case Token.RC:
              // Autoinsert ;
              warnMissingSemi(pos, nodeEnd(pn));
              break;
          default:
              if ((ttFlagged & TI_AFTER_EOL) == 0) {
                  // Report error if no EOL or autoinsert ; otherwise
                  reportError("msg.no.semi.stmt");
              } else {
                  warnMissingSemi(pos, nodeEnd(pn));
              }
              break;
        }
    }

    private IfStatement ifStatement()
        throws IOException
    {
        if (currentToken != Token.IF) codeBug();
        consumeToken();
        int pos = ts.tokenBeg, lineno = ts.lineno, elsePos = -1;
        ConditionData data = condition();
        AstNode ifTrue = statement(), ifFalse = null;
        if (matchToken(Token.ELSE)) {
            elsePos = ts.tokenBeg - pos;
            ifFalse = statement();
        }
        int end = getNodeEnd(ifFalse != null ? ifFalse : ifTrue);
        IfStatement pn = new IfStatement(pos, end - pos);
        pn.setCondition(data.condition);
        pn.setParens(data.lp - pos, data.rp - pos);
        pn.setThenPart(ifTrue);
        pn.setElsePart(ifFalse);
        pn.setElsePosition(elsePos);
        pn.setLineno(lineno);
        return pn;
    }

    private SwitchStatement switchStatement()
        throws IOException
    {
        if (currentToken != Token.SWITCH) codeBug();
        consumeToken();
        int pos = ts.tokenBeg;

        SwitchStatement pn = new SwitchStatement(pos);
        if (mustMatchToken(Token.LP, "msg.no.paren.switch"))
            pn.setLp(ts.tokenBeg - pos);
        pn.setLineno(ts.lineno);

        AstNode discriminant = expr();
        pn.setExpression(discriminant);
        enterSwitch(pn);

        try {
            if (mustMatchToken(Token.RP, "msg.no.paren.after.switch"))
                pn.setRp(ts.tokenBeg - pos);

            mustMatchToken(Token.LC, "msg.no.brace.switch");

            boolean hasDefault = false;
            int tt;
            switchLoop: for (;;) {
                tt = nextToken();
                int casePos = ts.tokenBeg;
                int caseLineno = ts.lineno;
                AstNode caseExpression = null;
                switch (tt) {
                    case Token.RC:
                        pn.setLength(ts.tokenEnd - pos);
                        break switchLoop;

                    case Token.CASE:
                        caseExpression = expr();
                        mustMatchToken(Token.COLON, "msg.no.colon.case");
                        break;

                    case Token.DEFAULT:
                        if (hasDefault) {
                            reportError("msg.double.switch.default");
                        }
                        hasDefault = true;
                        caseExpression = null;
                        mustMatchToken(Token.COLON, "msg.no.colon.case");
                        break;

                    default:
                        reportError("msg.bad.switch");
                        break switchLoop;
                }

                SwitchCase caseNode = new SwitchCase(casePos);
                caseNode.setExpression(caseExpression);
                caseNode.setLength(ts.tokenEnd - pos);  // include colon
                caseNode.setLineno(caseLineno);

                while ((tt = peekToken()) != Token.RC
                       && tt != Token.CASE
                       && tt != Token.DEFAULT
                       && tt != Token.EOF)
                {
                    caseNode.addStatement(statement());  // updates length
                }
                pn.addCase(caseNode);
            }
        } finally {
            exitSwitch();
        }
        return pn;
    }

    private WhileLoop whileLoop()
        throws IOException
    {
        if (currentToken != Token.WHILE) codeBug();
        consumeToken();
        int pos = ts.tokenBeg;
        WhileLoop pn = new WhileLoop(pos);
        pn.setLineno(ts.lineno);
        enterLoop(pn);
        try {
            ConditionData data = condition();
            pn.setCondition(data.condition);
            pn.setParens(data.lp - pos, data.rp - pos);
            AstNode body = statement();
            pn.setLength(getNodeEnd(body) - pos);
            pn.setBody(body);
        } finally {
            exitLoop();
        }
        return pn;
    }

    private DoLoop doLoop()
        throws IOException
    {
        if (currentToken != Token.DO) codeBug();
        consumeToken();
        int pos = ts.tokenBeg, end;
        DoLoop pn = new DoLoop(pos);
        pn.setLineno(ts.lineno);
        enterLoop(pn);
        try {
            AstNode body = statement();
            mustMatchToken(Token.WHILE, "msg.no.while.do");
            pn.setWhilePosition(ts.tokenBeg - pos);
            ConditionData data = condition();
            pn.setCondition(data.condition);
            pn.setParens(data.lp - pos, data.rp - pos);
            end = getNodeEnd(body);
            pn.setBody(body);
        } finally {
            exitLoop();
        }
        // Always auto-insert semicolon to follow SpiderMonkey:
        // It is required by ECMAScript but is ignored by the rest of
        // world, see bug 238945
        if (matchToken(Token.SEMI)) {
            end = ts.tokenEnd;
        }
        pn.setLength(end - pos);
        return pn;
    }

    private Loop forLoop()
        throws IOException
    {
        if (currentToken != Token.FOR) codeBug();
        consumeToken();
        int forPos = ts.tokenBeg, lineno = ts.lineno;
        boolean isForEach = false, isForIn = false;
        int eachPos = -1, inPos = -1, lp = -1, rp = -1;
        AstNode init = null;  // init is also foo in 'foo in object'
        AstNode cond = null;  // cond is also object in 'foo in object'
        AstNode incr = null;
        Loop pn = null;

        Scope tempScope = new Scope();
        pushScope(tempScope);  // decide below what AST class to use
        try {
            // See if this is a for each () instead of just a for ()
            if (matchToken(Token.NAME)) {
                if ("each".equals(ts.getString())) {
                    isForEach = true;
                    eachPos = ts.tokenBeg - forPos;
                } else {
                    reportError("msg.no.paren.for");
                }
            }

            if (mustMatchToken(Token.LP, "msg.no.paren.for"))
                lp = ts.tokenBeg - forPos;
            int tt = peekToken();

            init = forLoopInit(tt);

            if (matchToken(Token.IN)) {
                isForIn = true;
                inPos = ts.tokenBeg - forPos;
                cond = expr();  // object over which we're iterating
            } else {  // ordinary for-loop
                mustMatchToken(Token.SEMI, "msg.no.semi.for");
                if (peekToken() == Token.SEMI) {
                    // no loop condition
                    cond = new EmptyExpression(ts.tokenBeg, 1);
                    cond.setLineno(ts.lineno);
                } else {
                    cond = expr();
                }

                mustMatchToken(Token.SEMI, "msg.no.semi.for.cond");
                int tmpPos = ts.tokenEnd;
                if (peekToken() == Token.RP) {
                    incr = new EmptyExpression(tmpPos, 1);
                    incr.setLineno(ts.lineno);
                } else {
                    incr = expr();
                }
            }

            if (mustMatchToken(Token.RP, "msg.no.paren.for.ctrl"))
                rp = ts.tokenBeg - forPos;

            if (isForIn) {
                ForInLoop fis = new ForInLoop(forPos);
                if (init instanceof VariableDeclaration) {
                    // check that there was only one variable given
                    if (((VariableDeclaration)init).getVariables().size() > 1) {
                        reportError("msg.mult.index");
                    }
                }
                fis.setIterator(init);
                fis.setIteratedObject(cond);
                fis.setInPosition(inPos);
                fis.setIsForEach(isForEach);
                fis.setEachPosition(eachPos);
                pn = fis;
            } else {
                ForLoop fl = new ForLoop(forPos);
                fl.setInitializer(init);
                fl.setCondition(cond);
                fl.setIncrement(incr);
                pn = fl;
            }

            // replace temp scope with the new loop object
            currentScope.replaceWith(pn);
            popScope();

            // We have to parse the body -after- creating the loop node,
            // so that the loop node appears in the loopSet, allowing
            // break/continue statements to find the enclosing loop.
            enterLoop(pn);
            try {
                AstNode body = statement();
                pn.setLength(getNodeEnd(body) - forPos);
                pn.setBody(body);
            } finally {
                exitLoop();
            }

        } finally {
            if (currentScope == tempScope) {
                popScope();
            }
        }
        pn.setParens(lp, rp);
        pn.setLineno(lineno);
        return pn;
    }

    private AstNode forLoopInit(int tt) throws IOException {
        try {
            inForInit = true;  // checked by variables() and relExpr()
            AstNode init = null;
            if (tt == Token.SEMI) {
                init = new EmptyExpression(ts.tokenBeg, 1);
                init.setLineno(ts.lineno);
            } else if (tt == Token.VAR || tt == Token.LET) {
                consumeToken();
                init = variables(tt, ts.tokenBeg, false);
            } else {
                init = expr();
                markDestructuring(init);
            }
            return init;
        } finally {
            inForInit = false;
        }
    }

    private TryStatement tryStatement()
        throws IOException
    {
        if (currentToken != Token.TRY) codeBug();
        consumeToken();

        int tryPos = ts.tokenBeg, lineno = ts.lineno, finallyPos = -1;
        if (peekToken() != Token.LC) {
            reportError("msg.no.brace.try");
        }
        AstNode tryBlock = statement();
        int tryEnd = getNodeEnd(tryBlock);

        List<CatchClause> clauses = null;

        boolean sawDefaultCatch = false;
        int peek = peekToken();
        if (peek == Token.CATCH) {
            while (matchToken(Token.CATCH)) {
                int catchLineNum = ts.lineno;
                if (sawDefaultCatch) {
                    reportError("msg.catch.unreachable");
                }
                int catchPos = ts.tokenBeg, lp = -1, rp = -1, guardPos = -1;
                if (mustMatchToken(Token.LP, "msg.no.paren.catch"))
                    lp = ts.tokenBeg;

                mustMatchToken(Token.NAME, "msg.bad.catchcond");

                Name varName = createNameNode();
                String varNameString = varName.getIdentifier();
                if (inUseStrictDirective) {
                    if ("eval".equals(varNameString) ||
                        "arguments".equals(varNameString))
                    {
                        reportError("msg.bad.id.strict", varNameString);
                    }
                }

                AstNode catchCond = null;
                if (matchToken(Token.IF)) {
                    guardPos = ts.tokenBeg;
                    catchCond = expr();
                } else {
                    sawDefaultCatch = true;
                }

                if (mustMatchToken(Token.RP, "msg.bad.catchcond"))
                    rp = ts.tokenBeg;
                mustMatchToken(Token.LC, "msg.no.brace.catchblock");

                Block catchBlock = (Block)statements();
                tryEnd = getNodeEnd(catchBlock);
                CatchClause catchNode = new CatchClause(catchPos);
                catchNode.setVarName(varName);
                catchNode.setCatchCondition(catchCond);
                catchNode.setBody(catchBlock);
                if (guardPos != -1) {
                    catchNode.setIfPosition(guardPos - catchPos);
                }
                catchNode.setParens(lp, rp);
                catchNode.setLineno(catchLineNum);

                if (mustMatchToken(Token.RC, "msg.no.brace.after.body"))
                    tryEnd = ts.tokenEnd;
                catchNode.setLength(tryEnd - catchPos);
                if (clauses == null)
                    clauses = new ArrayList<CatchClause>();
                clauses.add(catchNode);
            }
        } else if (peek != Token.FINALLY) {
            mustMatchToken(Token.FINALLY, "msg.try.no.catchfinally");
        }

        AstNode finallyBlock = null;
        if (matchToken(Token.FINALLY)) {
            finallyPos = ts.tokenBeg;
            finallyBlock = statement();
            tryEnd = getNodeEnd(finallyBlock);
        }

        TryStatement pn = new TryStatement(tryPos, tryEnd - tryPos);
        pn.setTryBlock(tryBlock);
        pn.setCatchClauses(clauses);
        pn.setFinallyBlock(finallyBlock);
        if (finallyPos != -1) {
            pn.setFinallyPosition(finallyPos - tryPos);
        }
        pn.setLineno(lineno);
        return pn;
    }

    private ThrowStatement throwStatement()
        throws IOException
    {
        if (currentToken != Token.THROW) codeBug();
        consumeToken();
        int pos = ts.tokenBeg, lineno = ts.lineno;
        if (peekTokenOrEOL() == Token.EOL) {
            // ECMAScript does not allow new lines before throw expression,
            // see bug 256617
            reportError("msg.bad.throw.eol");
        }
        AstNode expr = expr();
        ThrowStatement pn = new ThrowStatement(pos, getNodeEnd(expr) - pos, expr);
        pn.setLineno(lineno);
        return pn;
    }

    // If we match a NAME, consume the token and return the statement
    // with that label.  If the name does not match an existing label,
    // reports an error.  Returns the labeled statement node, or null if
    // the peeked token was not a name.  Side effect:  sets scanner token
    // information for the label identifier (tokenBeg, tokenEnd, etc.)

    private LabeledStatement matchJumpLabelName()
        throws IOException
    {
        LabeledStatement label = null;

        if (peekTokenOrEOL() == Token.NAME) {
            consumeToken();
            if (labelSet != null) {
                label = labelSet.get(ts.getString());
            }
            if (label == null) {
                reportError("msg.undef.label");
            }
        }

        return label;
    }

    private BreakStatement breakStatement()
        throws IOException
    {
        if (currentToken != Token.BREAK) codeBug();
        consumeToken();
        int lineno = ts.lineno, pos = ts.tokenBeg, end = ts.tokenEnd;
        Name breakLabel = null;
        if (peekTokenOrEOL() == Token.NAME) {
            breakLabel = createNameNode();
            end = getNodeEnd(breakLabel);
        }

        // matchJumpLabelName only matches if there is one
        LabeledStatement labels = matchJumpLabelName();
        // always use first label as target
        Jump breakTarget = labels == null ? null : labels.getFirstLabel();

        if (breakTarget == null && breakLabel == null) {
            if (loopAndSwitchSet == null || loopAndSwitchSet.size() == 0) {
                if (breakLabel == null) {
                    reportError("msg.bad.break", pos, end - pos);
                }
            } else {
                breakTarget = loopAndSwitchSet.get(loopAndSwitchSet.size() - 1);
            }
        }

        BreakStatement pn = new BreakStatement(pos, end - pos);
        pn.setBreakLabel(breakLabel);
        // can be null if it's a bad break in error-recovery mode
        if (breakTarget != null)
            pn.setBreakTarget(breakTarget);
        pn.setLineno(lineno);
        return pn;
    }

    private ContinueStatement continueStatement()
        throws IOException
    {
        if (currentToken != Token.CONTINUE) codeBug();
        consumeToken();
        int lineno = ts.lineno, pos = ts.tokenBeg, end = ts.tokenEnd;
        Name label = null;
        if (peekTokenOrEOL() == Token.NAME) {
            label = createNameNode();
            end = getNodeEnd(label);
        }

        // matchJumpLabelName only matches if there is one
        LabeledStatement labels = matchJumpLabelName();
        Loop target = null;
        if (labels == null && label == null) {
            if (loopSet == null || loopSet.size() == 0) {
                reportError("msg.continue.outside");
            } else {
                target = loopSet.get(loopSet.size() - 1);
            }
        } else {
            if (labels == null || !(labels.getStatement() instanceof Loop)) {
                reportError("msg.continue.nonloop", pos, end - pos);
            }
            target = labels == null ? null : (Loop)labels.getStatement();
        }

        ContinueStatement pn = new ContinueStatement(pos, end - pos);
        if (target != null)  // can be null in error-recovery mode
            pn.setTarget(target);
        pn.setLabel(label);
        pn.setLineno(lineno);
        return pn;
    }

    private WithStatement withStatement()
        throws IOException
    {
        if (currentToken != Token.WITH) codeBug();
        consumeToken();

        int lineno = ts.lineno, pos = ts.tokenBeg, lp = -1, rp = -1;
        if (mustMatchToken(Token.LP, "msg.no.paren.with"))
            lp = ts.tokenBeg;

        AstNode obj = expr();

        if (mustMatchToken(Token.RP, "msg.no.paren.after.with"))
            rp = ts.tokenBeg;

        AstNode body = statement();

        WithStatement pn = new WithStatement(pos, getNodeEnd(body) - pos);
        pn.setExpression(obj);
        pn.setStatement(body);
        pn.setParens(lp, rp);
        pn.setLineno(lineno);
        return pn;
    }

    private AstNode letStatement()
        throws IOException
    {
        if (currentToken != Token.LET) codeBug();
        consumeToken();
        int lineno = ts.lineno, pos = ts.tokenBeg;
        AstNode pn;
        if (peekToken() == Token.LP) {
            pn = let(true, pos);
        } else {
            pn = variables(Token.LET, pos, true);  // else, e.g.: let x=6, y=7;
        }
        pn.setLineno(lineno);
        return pn;
    }

    /**
     * Returns whether or not the bits in the mask have changed to all set.
     * @param before bits before change
     * @param after bits after change
     * @param mask mask for bits
     * @return {@code true} if all the bits in the mask are set in "after"
     *          but not in "before"
     */
    private static final boolean nowAllSet(int before, int after, int mask) {
        return ((before & mask) != mask) && ((after & mask) == mask);
    }

    private AstNode returnOrYield(int tt, boolean exprContext)
        throws IOException
    {
        if (!insideFunction()) {
            reportError(tt == Token.RETURN ? "msg.bad.return"
                                           : "msg.bad.yield");
        }
        consumeToken();
        int lineno = ts.lineno, pos = ts.tokenBeg, end = ts.tokenEnd;

        AstNode e = null;
        // This is ugly, but we don't want to require a semicolon.
        switch (peekTokenOrEOL()) {
          case Token.SEMI: case Token.RC:  case Token.RB:    case Token.RP:
          case Token.EOF:  case Token.EOL: case Token.ERROR: case Token.YIELD:
            break;
          default:
            e = expr();
            end = getNodeEnd(e);
        }

        int before = endFlags;
        AstNode ret;

        if (tt == Token.RETURN) {
            endFlags |= e == null ? Node.END_RETURNS : Node.END_RETURNS_VALUE;
            ret = new ReturnStatement(pos, end - pos, e);

            // see if we need a strict mode warning
            if (nowAllSet(before, endFlags,
                    Node.END_RETURNS|Node.END_RETURNS_VALUE))
                addStrictWarning("msg.return.inconsistent", "", pos, end - pos);
        } else {
            if (!insideFunction())
                reportError("msg.bad.yield");
            endFlags |= Node.END_YIELDS;
            ret = new Yield(pos, end - pos, e);
            setRequiresActivation();
            setIsGenerator();
            if (!exprContext) {
                ret = new ExpressionStatement(ret);
            }
        }

        // see if we are mixing yields and value returns.
        if (insideFunction()
            && nowAllSet(before, endFlags,
                    Node.END_YIELDS|Node.END_RETURNS_VALUE)) {
            Name name = ((FunctionNode)currentScriptOrFn).getFunctionName();
            if (name == null || name.length() == 0)
                addError("msg.anon.generator.returns", "");
            else
                addError("msg.generator.returns", name.getIdentifier());
        }

        ret.setLineno(lineno);
        return ret;
    }

    private AstNode block()
        throws IOException
    {
        if (currentToken != Token.LC) codeBug();
        consumeToken();
        int pos = ts.tokenBeg;
        Scope block = new Scope(pos);
        block.setLineno(ts.lineno);
        pushScope(block);
        try {
            statements(block);
            mustMatchToken(Token.RC, "msg.no.brace.block");
            block.setLength(ts.tokenEnd - pos);
            return block;
        } finally {
            popScope();
        }
    }

    private AstNode defaultXmlNamespace()
        throws IOException
    {
        if (currentToken != Token.DEFAULT) codeBug();
        consumeToken();
        mustHaveXML();
        setRequiresActivation();
        int lineno = ts.lineno, pos = ts.tokenBeg;

        if (!(matchToken(Token.NAME) && "xml".equals(ts.getString()))) {
            reportError("msg.bad.namespace");
        }
        if (!(matchToken(Token.NAME) && "namespace".equals(ts.getString()))) {
            reportError("msg.bad.namespace");
        }
        if (!matchToken(Token.ASSIGN)) {
            reportError("msg.bad.namespace");
        }

        AstNode e = expr();
        UnaryExpression dxmln = new UnaryExpression(pos, getNodeEnd(e) - pos);
        dxmln.setOperator(Token.DEFAULTNAMESPACE);
        dxmln.setOperand(e);
        dxmln.setLineno(lineno);

        ExpressionStatement es = new ExpressionStatement(dxmln, true);
        return es;
    }

    private void recordLabel(Label label, LabeledStatement bundle)
        throws IOException
    {
        // current token should be colon that primaryExpr left untouched
        if (peekToken() != Token.COLON) codeBug();
        consumeToken();
        String name = label.getName();
        if (labelSet == null) {
            labelSet = new HashMap<String,LabeledStatement>();
        } else {
            LabeledStatement ls = labelSet.get(name);
            if (ls != null) {
                if (compilerEnv.isIdeMode()) {
                    Label dup = ls.getLabelByName(name);
                    reportError("msg.dup.label",
                                dup.getAbsolutePosition(), dup.getLength());
                }
                reportError("msg.dup.label",
                            label.getPosition(), label.getLength());
            }
        }
        bundle.addLabel(label);
        labelSet.put(name, bundle);
    }

    /**
     * Found a name in a statement context.  If it's a label, we gather
     * up any following labels and the next non-label statement into a
     * {@link LabeledStatement} "bundle" and return that.  Otherwise we parse
     * an expression and return it wrapped in an {@link ExpressionStatement}.
     */
    private AstNode nameOrLabel()
        throws IOException
    {
        if (currentToken != Token.NAME) throw codeBug();
        int pos = ts.tokenBeg;

        // set check for label and call down to primaryExpr
        currentFlaggedToken |= TI_CHECK_LABEL;
        AstNode expr = expr();

        if (expr.getType() != Token.LABEL) {
            AstNode n = new ExpressionStatement(expr, !insideFunction());
            n.lineno = expr.lineno;
            return n;
        }

        LabeledStatement bundle = new LabeledStatement(pos);
        recordLabel((Label)expr, bundle);
        bundle.setLineno(ts.lineno);
        // look for more labels
        AstNode stmt = null;
        while (peekToken() == Token.NAME) {
            currentFlaggedToken |= TI_CHECK_LABEL;
            expr = expr();
            if (expr.getType() != Token.LABEL) {
                stmt = new ExpressionStatement(expr, !insideFunction());
                autoInsertSemicolon(stmt);
                break;
            }
            recordLabel((Label)expr, bundle);
        }

        // no more labels; now parse the labeled statement
        try {
            currentLabel = bundle;
            if (stmt == null) {
                stmt = statementHelper();
            }
        } finally {
            currentLabel = null;
            // remove the labels for this statement from the global set
            for (Label lb : bundle.getLabels()) {
                labelSet.remove(lb.getName());
            }
        }

        // If stmt has parent assigned its position already is relative
        // (See bug #710225)
        bundle.setLength(stmt.getParent() == null
                     ? getNodeEnd(stmt) - pos
                     : getNodeEnd(stmt));
        bundle.setStatement(stmt);
        return bundle;
    }

    /**
     * Parse a 'var' or 'const' statement, or a 'var' init list in a for
     * statement.
     * @param declType A token value: either VAR, CONST, or LET depending on
     * context.
     * @param pos the position where the node should start.  It's sometimes
     * the var/const/let keyword, and other times the beginning of the first
     * token in the first variable declaration.
     * @return the parsed variable list
     */
    private VariableDeclaration variables(int declType, int pos, boolean isStatement)
        throws IOException
    {
        int end;
        VariableDeclaration pn = new VariableDeclaration(pos);
        pn.setType(declType);
        pn.setLineno(ts.lineno);
        // Example:
        // var foo = {a: 1, b: 2}, bar = [3, 4];
        // var {b: s2, a: s1} = foo, x = 6, y, [s3, s4] = bar;
        for (;;) {
            AstNode destructuring = null;
            Name name = null;
            int tt = peekToken(), kidPos = ts.tokenBeg;
            end = ts.tokenEnd;

            if (tt == Token.LB || tt == Token.LC) {
                // Destructuring assignment, e.g., var [a,b] = ...
                destructuring = destructuringPrimaryExpr();
                end = getNodeEnd(destructuring);
                if (!(destructuring instanceof DestructuringForm))
                    reportError("msg.bad.assign.left", kidPos, end - kidPos);
                markDestructuring(destructuring);
            } else {
                // Simple variable name
                mustMatchToken(Token.NAME, "msg.bad.var");
                name = createNameNode();
                name.setLineno(ts.getLineno());
                if (inUseStrictDirective) {
                    String id = ts.getString();
                    if ("eval".equals(id) || "arguments".equals(ts.getString()))
                    {
                        reportError("msg.bad.id.strict", id);
                    }
                }
                defineSymbol(declType, ts.getString(), inForInit);
            }

            int lineno = ts.lineno;
            AstNode init = null;
            if (matchToken(Token.ASSIGN)) {
                init = assignExpr();
                end = getNodeEnd(init);
            }

            VariableInitializer vi = new VariableInitializer(kidPos, end - kidPos);
            if (destructuring != null) {
                if (init == null && !inForInit) {
                    reportError("msg.destruct.assign.no.init");
                }
                vi.setTarget(destructuring);
            } else {
                vi.setTarget(name);
            }
            vi.setInitializer(init);
            vi.setType(declType);
            vi.setLineno(lineno);
            pn.addVariable(vi);

            if (!matchToken(Token.COMMA))
                break;
        }
        pn.setLength(end - pos);
        pn.setIsStatement(isStatement);
        return pn;
    }

    // have to pass in 'let' kwd position to compute kid offsets properly
    private AstNode let(boolean isStatement, int pos)
        throws IOException
    {
        LetNode pn = new LetNode(pos);
        pn.setLineno(ts.lineno);
        if (mustMatchToken(Token.LP, "msg.no.paren.after.let"))
            pn.setLp(ts.tokenBeg - pos);
        pushScope(pn);
        try {
            VariableDeclaration vars = variables(Token.LET, ts.tokenBeg, isStatement);
            pn.setVariables(vars);
            if (mustMatchToken(Token.RP, "msg.no.paren.let")) {
                pn.setRp(ts.tokenBeg - pos);
            }
            if (isStatement && peekToken() == Token.LC) {
                // let statement
                consumeToken();
                int beg = ts.tokenBeg;  // position stmt at LC
                AstNode stmt = statements();
                mustMatchToken(Token.RC, "msg.no.curly.let");
                stmt.setLength(ts.tokenEnd - beg);
                pn.setLength(ts.tokenEnd - pos);
                pn.setBody(stmt);
                pn.setType(Token.LET);
            } else {
                // let expression
                AstNode expr = expr();
                pn.setLength(getNodeEnd(expr) - pos);
                pn.setBody(expr);
                if (isStatement) {
                    // let expression in statement context
                    ExpressionStatement es =
                            new ExpressionStatement(pn, !insideFunction());
                    es.setLineno(pn.getLineno());
                    return es;
                }
            }
        } finally {
            popScope();
        }
        return pn;
    }

    void defineSymbol(int declType, String name) {
        defineSymbol(declType, name, false);
    }

    void defineSymbol(int declType, String name, boolean ignoreNotInBlock) {
        if (name == null) {
            if (compilerEnv.isIdeMode()) {  // be robust in IDE-mode
                return;
            } else {
                codeBug();
            }
        }
        Scope definingScope = currentScope.getDefiningScope(name);
        Symbol symbol = definingScope != null
                        ? definingScope.getSymbol(name)
                        : null;
        int symDeclType = symbol != null ? symbol.getDeclType() : -1;
        if (symbol != null
            && (symDeclType == Token.CONST
                || declType == Token.CONST
                || (definingScope == currentScope && symDeclType == Token.LET)))
        {
            addError(symDeclType == Token.CONST ? "msg.const.redecl" :
                     symDeclType == Token.LET ? "msg.let.redecl" :
                     symDeclType == Token.VAR ? "msg.var.redecl" :
                     symDeclType == Token.FUNCTION ? "msg.fn.redecl" :
                     "msg.parm.redecl", name);
            return;
        }
        switch (declType) {
          case Token.LET:
              if (!ignoreNotInBlock &&
                  ((currentScope.getType() == Token.IF) ||
                   currentScope instanceof Loop)) {
                  addError("msg.let.decl.not.in.block");
                  return;
              }
              currentScope.putSymbol(new Symbol(declType, name));
              return;

          case Token.VAR:
          case Token.CONST:
          case Token.FUNCTION:
              if (symbol != null) {
                  if (symDeclType == Token.VAR)
                      addStrictWarning("msg.var.redecl", name);
                  else if (symDeclType == Token.LP) {
                      addStrictWarning("msg.var.hides.arg", name);
                  }
              } else {
                  currentScriptOrFn.putSymbol(new Symbol(declType, name));
              }
              return;

          case Token.LP:
              if (symbol != null) {
                  // must be duplicate parameter. Second parameter hides the
                  // first, so go ahead and add the second parameter
                  addWarning("msg.dup.parms", name);
              }
              currentScriptOrFn.putSymbol(new Symbol(declType, name));
              return;

          default:
              throw codeBug();
        }
    }

    private AstNode expr()
        throws IOException
    {
        AstNode pn = assignExpr();
        int pos = pn.getPosition();
        while (matchToken(Token.COMMA)) {
            int opPos = ts.tokenBeg;
            if (compilerEnv.isStrictMode() && !pn.hasSideEffects())
                addStrictWarning("msg.no.side.effects", "",
                                 pos, nodeEnd(pn) - pos);
            if (peekToken() == Token.YIELD)
                reportError("msg.yield.parenthesized");
            pn = new InfixExpression(Token.COMMA, pn, assignExpr(), opPos);
        }
        return pn;
    }

    private AstNode assignExpr()
        throws IOException
    {
        int tt = peekToken();
        if (tt == Token.YIELD) {
            return returnOrYield(tt, true);
        }
        AstNode pn = condExpr();
        tt = peekToken();
        if (Token.FIRST_ASSIGN <= tt && tt <= Token.LAST_ASSIGN) {
            consumeToken();
            markDestructuring(pn);
            int opPos = ts.tokenBeg;
            pn = new Assignment(tt, pn, assignExpr(), opPos);
        }
        return pn;
    }

    private AstNode condExpr()
        throws IOException
    {
        AstNode pn = orExpr();
        if (matchToken(Token.HOOK)) {
            int line = ts.lineno;
            int qmarkPos = ts.tokenBeg, colonPos = -1;
            /*
             * Always accept the 'in' operator in the middle clause of a ternary,
             * where it's unambiguous, even if we might be parsing the init of a
             * for statement.
             */
            boolean wasInForInit = inForInit;
            inForInit = false;
            AstNode ifTrue;
            try {
                ifTrue = assignExpr();
            } finally {
                inForInit = wasInForInit;
            }
            if (mustMatchToken(Token.COLON, "msg.no.colon.cond"))
                colonPos = ts.tokenBeg;
            AstNode ifFalse = assignExpr();
            int beg = pn.getPosition(), len = getNodeEnd(ifFalse) - beg;
            ConditionalExpression ce = new ConditionalExpression(beg, len);
            ce.setLineno(line);
            ce.setTestExpression(pn);
            ce.setTrueExpression(ifTrue);
            ce.setFalseExpression(ifFalse);
            ce.setQuestionMarkPosition(qmarkPos - beg);
            ce.setColonPosition(colonPos - beg);
            pn = ce;
        }
        return pn;
    }

    private AstNode orExpr()
        throws IOException
    {
        AstNode pn = andExpr();
        if (matchToken(Token.OR)) {
            int opPos = ts.tokenBeg;
            pn = new InfixExpression(Token.OR, pn, orExpr(), opPos);
        }
        return pn;
    }

    private AstNode andExpr()
        throws IOException
    {
        AstNode pn = bitOrExpr();
        if (matchToken(Token.AND)) {
            int opPos = ts.tokenBeg;
            pn = new InfixExpression(Token.AND, pn, andExpr(), opPos);
        }
        return pn;
    }

    private AstNode bitOrExpr()
        throws IOException
    {
        AstNode pn = bitXorExpr();
        while (matchToken(Token.BITOR)) {
            int opPos = ts.tokenBeg;
            pn = new InfixExpression(Token.BITOR, pn, bitXorExpr(), opPos);
        }
        return pn;
    }

    private AstNode bitXorExpr()
        throws IOException
    {
        AstNode pn = bitAndExpr();
        while (matchToken(Token.BITXOR)) {
            int opPos = ts.tokenBeg;
            pn = new InfixExpression(Token.BITXOR, pn, bitAndExpr(), opPos);
        }
        return pn;
    }

    private AstNode bitAndExpr()
        throws IOException
    {
        AstNode pn = eqExpr();
        while (matchToken(Token.BITAND)) {
            int opPos = ts.tokenBeg;
            pn = new InfixExpression(Token.BITAND, pn, eqExpr(), opPos);
        }
        return pn;
    }

    private AstNode eqExpr()
        throws IOException
    {
        AstNode pn = relExpr();
        for (;;) {
            int tt = peekToken(), opPos = ts.tokenBeg;
            switch (tt) {
              case Token.EQ:
              case Token.NE:
              case Token.SHEQ:
              case Token.SHNE:
                consumeToken();
                int parseToken = tt;
                if (compilerEnv.getLanguageVersion() == Context.VERSION_1_2) {
                    // JavaScript 1.2 uses shallow equality for == and != .
                    if (tt == Token.EQ)
                        parseToken = Token.SHEQ;
                    else if (tt == Token.NE)
                        parseToken = Token.SHNE;
                }
                pn = new InfixExpression(parseToken, pn, relExpr(), opPos);
                continue;
            }
            break;
        }
        return pn;
    }

    private AstNode relExpr()
        throws IOException
    {
        AstNode pn = shiftExpr();
        for (;;) {
            int tt = peekToken(), opPos = ts.tokenBeg;
            switch (tt) {
              case Token.IN:
                if (inForInit)
                    break;
                // fall through
              case Token.INSTANCEOF:
              case Token.LE:
              case Token.LT:
              case Token.GE:
              case Token.GT:
                consumeToken();
                pn = new InfixExpression(tt, pn, shiftExpr(), opPos);
                continue;
            }
            break;
        }
        return pn;
    }

    private AstNode shiftExpr()
        throws IOException
    {
        AstNode pn = addExpr();
        for (;;) {
            int tt = peekToken(), opPos = ts.tokenBeg;
            switch (tt) {
              case Token.LSH:
              case Token.URSH:
              case Token.RSH:
                consumeToken();
                pn = new InfixExpression(tt, pn, addExpr(), opPos);
                continue;
            }
            break;
        }
        return pn;
    }

    private AstNode addExpr()
        throws IOException
    {
        AstNode pn = mulExpr();
        for (;;) {
            int tt = peekToken(), opPos = ts.tokenBeg;
            if (tt == Token.ADD || tt == Token.SUB) {
                consumeToken();
                pn = new InfixExpression(tt, pn, mulExpr(), opPos);
                continue;
            }
            break;
        }
        return pn;
    }

    private AstNode mulExpr()
        throws IOException
    {
        AstNode pn = unaryExpr();
        for (;;) {
            int tt = peekToken(), opPos = ts.tokenBeg;
            switch (tt) {
              case Token.MUL:
              case Token.DIV:
              case Token.MOD:
                consumeToken();
                pn = new InfixExpression(tt, pn, unaryExpr(), opPos);
                continue;
            }
            break;
        }
        return pn;
    }

    private AstNode unaryExpr()
        throws IOException
    {
        AstNode node;
        int tt = peekToken();
        int line = ts.lineno;

        switch(tt) {
          case Token.VOID:
          case Token.NOT:
          case Token.BITNOT:
          case Token.TYPEOF:
              consumeToken();
              node = new UnaryExpression(tt, ts.tokenBeg, unaryExpr());
              node.setLineno(line);
              return node;

          case Token.ADD:
              consumeToken();
              // Convert to special POS token in parse tree
              node = new UnaryExpression(Token.POS, ts.tokenBeg, unaryExpr());
              node.setLineno(line);
              return node;

          case Token.SUB:
              consumeToken();
              // Convert to special NEG token in parse tree
              node = new UnaryExpression(Token.NEG, ts.tokenBeg, unaryExpr());
              node.setLineno(line);
              return node;

          case Token.INC:
          case Token.DEC:
              consumeToken();
              UnaryExpression expr = new UnaryExpression(tt, ts.tokenBeg,
                                                         memberExpr(true));
              expr.setLineno(line);
              checkBadIncDec(expr);
              return expr;

          case Token.DELPROP:
              consumeToken();
              node = new UnaryExpression(tt, ts.tokenBeg, unaryExpr());
              node.setLineno(line);
              return node;

          case Token.ERROR:
              consumeToken();
              return makeErrorNode();

          case Token.LT:
              // XML stream encountered in expression.
              if (compilerEnv.isXmlAvailable()) {
                  consumeToken();
                  return memberExprTail(true, xmlInitializer());
              }
              // Fall thru to the default handling of RELOP

          default:
              AstNode pn = memberExpr(true);
              // Don't look across a newline boundary for a postfix incop.
              tt = peekTokenOrEOL();
              if (!(tt == Token.INC || tt == Token.DEC)) {
                  return pn;
              }
              consumeToken();
              UnaryExpression uexpr =
                      new UnaryExpression(tt, ts.tokenBeg, pn, true);
              uexpr.setLineno(line);
              checkBadIncDec(uexpr);
              return uexpr;
        }
    }

    private AstNode xmlInitializer()
        throws IOException
    {
        if (currentToken != Token.LT) codeBug();
        int pos = ts.tokenBeg, tt = ts.getFirstXMLToken();
        if (tt != Token.XML && tt != Token.XMLEND) {
            reportError("msg.syntax");
            return makeErrorNode();
        }

        XmlLiteral pn = new XmlLiteral(pos);
        pn.setLineno(ts.lineno);

        for (;;tt = ts.getNextXMLToken()) {
            switch (tt) {
              case Token.XML:
                  pn.addFragment(new XmlString(ts.tokenBeg, ts.getString()));
                  mustMatchToken(Token.LC, "msg.syntax");
                  int beg = ts.tokenBeg;
                  AstNode expr = (peekToken() == Token.RC)
                                 ? new EmptyExpression(beg, ts.tokenEnd - beg)
                                 : expr();
                  mustMatchToken(Token.RC, "msg.syntax");
                  XmlExpression xexpr = new XmlExpression(beg, expr);
                  xexpr.setIsXmlAttribute(ts.isXMLAttribute());
                  xexpr.setLength(ts.tokenEnd - beg);
                  pn.addFragment(xexpr);
                  break;

              case Token.XMLEND:
                  pn.addFragment(new XmlString(ts.tokenBeg, ts.getString()));
                  return pn;

              default:
                  reportError("msg.syntax");
                  return makeErrorNode();
            }
        }
    }

    private List<AstNode> argumentList()
        throws IOException
    {
        if (matchToken(Token.RP))
            return null;

        List<AstNode> result = new ArrayList<AstNode>();
        boolean wasInForInit = inForInit;
        inForInit = false;
        try {
            do {
                if (peekToken() == Token.YIELD) {
                    reportError("msg.yield.parenthesized");
                }
                AstNode en = assignExpr();
                if (peekToken() == Token.FOR) {
                    try {
                        result.add(generatorExpression(en, 0, true));
                    }
                    catch(IOException ex) {
                        // #TODO
                    }
                }
                else {
                    result.add(en);
                }
            } while (matchToken(Token.COMMA));
        } finally {
            inForInit = wasInForInit;
        }

        mustMatchToken(Token.RP, "msg.no.paren.arg");
        return result;
    }

    /**
     * Parse a new-expression, or if next token isn't {@link Token#NEW},
     * a primary expression.
     * @param allowCallSyntax passed down to {@link #memberExprTail}
     */
    private AstNode memberExpr(boolean allowCallSyntax)
        throws IOException
    {
        int tt = peekToken(), lineno = ts.lineno;
        AstNode pn;

        if (tt != Token.NEW) {
            pn = primaryExpr();
        } else {
            consumeToken();
            int pos = ts.tokenBeg;
            NewExpression nx = new NewExpression(pos);

            AstNode target = memberExpr(false);
            int end = getNodeEnd(target);
            nx.setTarget(target);

            int lp = -1;
            if (matchToken(Token.LP)) {
                lp = ts.tokenBeg;
                List<AstNode> args = argumentList();
                if (args != null && args.size() > ARGC_LIMIT)
                    reportError("msg.too.many.constructor.args");
                int rp = ts.tokenBeg;
                end = ts.tokenEnd;
                if (args != null)
                    nx.setArguments(args);
                nx.setParens(lp - pos, rp - pos);
            }

            // Experimental syntax: allow an object literal to follow a new
            // expression, which will mean a kind of anonymous class built with
            // the JavaAdapter.  the object literal will be passed as an
            // additional argument to the constructor.
            if (matchToken(Token.LC)) {
                ObjectLiteral initializer = objectLiteral();
                end = getNodeEnd(initializer);
                nx.setInitializer(initializer);
            }
            nx.setLength(end - pos);
            pn = nx;
        }
        pn.setLineno(lineno);
        AstNode tail = memberExprTail(allowCallSyntax, pn);
        return tail;
    }

    /**
     * Parse any number of "(expr)", "[expr]" ".expr", "..expr",
     * or ".(expr)" constructs trailing the passed expression.
     * @param pn the non-null parent node
     * @return the outermost (lexically last occurring) expression,
     * which will have the passed parent node as a descendant
     */
    private AstNode memberExprTail(boolean allowCallSyntax, AstNode pn)
        throws IOException
    {
        // we no longer return null for errors, so this won't be null
        if (pn == null) codeBug();
        int pos = pn.getPosition();
        int lineno;
      tailLoop:
        for (;;) {
            int tt = peekToken();
            switch (tt) {
              case Token.DOT:
              case Token.DOTDOT:
                  lineno = ts.lineno;
                  pn = propertyAccess(tt, pn);
                  pn.setLineno(lineno);
                  break;

              case Token.DOTQUERY:
                  consumeToken();
                  int opPos = ts.tokenBeg, rp = -1;
                  lineno = ts.lineno;
                  mustHaveXML();
                  setRequiresActivation();
                  AstNode filter = expr();
                  int end = getNodeEnd(filter);
                  if (mustMatchToken(Token.RP, "msg.no.paren")) {
                      rp = ts.tokenBeg;
                      end = ts.tokenEnd;
                  }
                  XmlDotQuery q = new XmlDotQuery(pos, end - pos);
                  q.setLeft(pn);
                  q.setRight(filter);
                  q.setOperatorPosition(opPos);
                  q.setRp(rp - pos);
                  q.setLineno(lineno);
                  pn = q;
                  break;

              case Token.LB:
                  consumeToken();
                  int lb = ts.tokenBeg, rb = -1;
                  lineno = ts.lineno;
                  AstNode expr = expr();
                  end = getNodeEnd(expr);
                  if (mustMatchToken(Token.RB, "msg.no.bracket.index")) {
                      rb = ts.tokenBeg;
                      end = ts.tokenEnd;
                  }
                  ElementGet g = new ElementGet(pos, end - pos);
                  g.setTarget(pn);
                  g.setElement(expr);
                  g.setParens(lb, rb);
                  g.setLineno(lineno);
                  pn = g;
                  break;

              case Token.LP:
                  if (!allowCallSyntax) {
                      break tailLoop;
                  }
                  lineno = ts.lineno;
                  consumeToken();
                  checkCallRequiresActivation(pn);
                  FunctionCall f = new FunctionCall(pos);
                  f.setTarget(pn);
                  // Assign the line number for the function call to where
                  // the paren appeared, not where the name expression started.
                  f.setLineno(lineno);
                  f.setLp(ts.tokenBeg - pos);
                  List<AstNode> args = argumentList();
                  if (args != null && args.size() > ARGC_LIMIT)
                      reportError("msg.too.many.function.args");
                  f.setArguments(args);
                  f.setRp(ts.tokenBeg - pos);
                  f.setLength(ts.tokenEnd - pos);
                  pn = f;
                  break;

              default:
                  break tailLoop;
            }
        }
        return pn;
    }

    /**
     * Handles any construct following a "." or ".." operator.
     * @param pn the left-hand side (target) of the operator.  Never null.
     * @return a PropertyGet, XmlMemberGet, or ErrorNode
     */
    private AstNode propertyAccess(int tt, AstNode pn)
            throws IOException
    {
        if (pn == null) codeBug();
        int memberTypeFlags = 0, lineno = ts.lineno, dotPos = ts.tokenBeg;
        consumeToken();

        if (tt == Token.DOTDOT) {
            mustHaveXML();
            memberTypeFlags = Node.DESCENDANTS_FLAG;
        }

        if (!compilerEnv.isXmlAvailable()) {
            int maybeName = nextToken();
            if (maybeName != Token.NAME
                    && !(compilerEnv.isReservedKeywordAsIdentifier()
                    && TokenStream.isKeyword(ts.getString()))) {
              reportError("msg.no.name.after.dot");
            }

            Name name = createNameNode(true, Token.GETPROP);
            PropertyGet pg = new PropertyGet(pn, name, dotPos);
            pg.setLineno(lineno);
            return pg;
        }

        AstNode ref = null;  // right side of . or .. operator

        int token = nextToken();
        switch (token) {
          case Token.THROW:
              // needed for generator.throw();
              saveNameTokenData(ts.tokenBeg, "throw", ts.lineno);
              ref = propertyName(-1, "throw", memberTypeFlags);
              break;

          case Token.NAME:
              // handles: name, ns::name, ns::*, ns::[expr]
              ref = propertyName(-1, ts.getString(), memberTypeFlags);
              break;

          case Token.MUL:
              // handles: *, *::name, *::*, *::[expr]
              saveNameTokenData(ts.tokenBeg, "*", ts.lineno);
              ref = propertyName(-1, "*", memberTypeFlags);
              break;

          case Token.XMLATTR:
              // handles: '@attr', '@ns::attr', '@ns::*', '@ns::*',
              //          '@::attr', '@::*', '@*', '@*::attr', '@*::*'
              ref = attributeAccess();
              break;

          default:
              if (compilerEnv.isReservedKeywordAsIdentifier()) {
                  // allow keywords as property names, e.g. ({if: 1})
                  String name = Token.keywordToName(token);
                  if (name != null) {
                      saveNameTokenData(ts.tokenBeg, name, ts.lineno);
                      ref = propertyName(-1, name, memberTypeFlags);
                      break;
                  }
              }
              reportError("msg.no.name.after.dot");
              return makeErrorNode();
        }

        boolean xml = ref instanceof XmlRef;
        InfixExpression result = xml ? new XmlMemberGet() : new PropertyGet();
        if (xml && tt == Token.DOT)
            result.setType(Token.DOT);
        int pos = pn.getPosition();
        result.setPosition(pos);
        result.setLength(getNodeEnd(ref) - pos);
        result.setOperatorPosition(dotPos - pos);
        result.setLineno(pn.getLineno());
        result.setLeft(pn);  // do this after setting position
        result.setRight(ref);
        return result;
    }

    /**
     * Xml attribute expression:<p>
     *   {@code @attr}, {@code @ns::attr}, {@code @ns::*}, {@code @ns::*},
     *   {@code @*}, {@code @*::attr}, {@code @*::*}, {@code @ns::[expr]},
     *   {@code @*::[expr]}, {@code @[expr]} <p>
     * Called if we peeked an '@' token.
     */
    private AstNode attributeAccess()
        throws IOException
    {
        int tt = nextToken(), atPos = ts.tokenBeg;

        switch (tt) {
          // handles: @name, @ns::name, @ns::*, @ns::[expr]
          case Token.NAME:
              return propertyName(atPos, ts.getString(), 0);

          // handles: @*, @*::name, @*::*, @*::[expr]
          case Token.MUL:
              saveNameTokenData(ts.tokenBeg, "*", ts.lineno);
              return propertyName(atPos, "*", 0);

          // handles @[expr]
          case Token.LB:
              return xmlElemRef(atPos, null, -1);

          default:
              reportError("msg.no.name.after.xmlAttr");
              return makeErrorNode();
        }
    }

    /**
     * Check if :: follows name in which case it becomes a qualified name.
     *
     * @param atPos a natural number if we just read an '@' token, else -1
     *
     * @param s the name or string that was matched (an identifier, "throw" or
     * "*").
     *
     * @param memberTypeFlags flags tracking whether we're a '.' or '..' child
     *
     * @return an XmlRef node if it's an attribute access, a child of a
     * '..' operator, or the name is followed by ::.  For a plain name,
     * returns a Name node.  Returns an ErrorNode for malformed XML
     * expressions.  (For now - might change to return a partial XmlRef.)
     */
    private AstNode propertyName(int atPos, String s, int memberTypeFlags)
        throws IOException
    {
        int pos = atPos != -1 ? atPos : ts.tokenBeg, lineno = ts.lineno;
        int colonPos = -1;
        Name name = createNameNode(true, currentToken);
        Name ns = null;

        if (matchToken(Token.COLONCOLON)) {
            ns = name;
            colonPos = ts.tokenBeg;

            switch (nextToken()) {
              // handles name::name
              case Token.NAME:
                  name = createNameNode();
                  break;

              // handles name::*
              case Token.MUL:
                  saveNameTokenData(ts.tokenBeg, "*", ts.lineno);
                  name = createNameNode(false, -1);
                  break;

              // handles name::[expr] or *::[expr]
              case Token.LB:
                  return xmlElemRef(atPos, ns, colonPos);

              default:
                  reportError("msg.no.name.after.coloncolon");
                  return makeErrorNode();
            }
        }

        if (ns == null && memberTypeFlags == 0 && atPos == -1) {
            return name;
        }

        XmlPropRef ref = new XmlPropRef(pos, getNodeEnd(name) - pos);
        ref.setAtPos(atPos);
        ref.setNamespace(ns);
        ref.setColonPos(colonPos);
        ref.setPropName(name);
        ref.setLineno(lineno);
        return ref;
    }

    /**
     * Parse the [expr] portion of an xml element reference, e.g.
     * @[expr], @*::[expr], or ns::[expr].
     */
    private XmlElemRef xmlElemRef(int atPos, Name namespace, int colonPos)
        throws IOException
    {
        int lb = ts.tokenBeg, rb = -1, pos = atPos != -1 ? atPos : lb;
        AstNode expr = expr();
        int end = getNodeEnd(expr);
        if (mustMatchToken(Token.RB, "msg.no.bracket.index")) {
            rb = ts.tokenBeg;
            end = ts.tokenEnd;
        }
        XmlElemRef ref = new XmlElemRef(pos, end - pos);
        ref.setNamespace(namespace);
        ref.setColonPos(colonPos);
        ref.setAtPos(atPos);
        ref.setExpression(expr);
        ref.setBrackets(lb, rb);
        return ref;
    }

    private AstNode destructuringPrimaryExpr()
        throws IOException, ParserException
    {
        try {
            inDestructuringAssignment = true;
            return primaryExpr();
        } finally {
            inDestructuringAssignment = false;
        }
    }

    private AstNode primaryExpr()
        throws IOException
    {
        int ttFlagged = nextFlaggedToken();
        int tt = ttFlagged & CLEAR_TI_MASK;

        switch(tt) {
          case Token.FUNCTION:
              return function(FunctionNode.FUNCTION_EXPRESSION);

          case Token.LB:
              return arrayLiteral();

          case Token.LC:
              return objectLiteral();

          case Token.LET:
              return let(false, ts.tokenBeg);

          case Token.LP:
              return parenExpr();

          case Token.XMLATTR:
              mustHaveXML();
              return attributeAccess();

          case Token.NAME:
              return name(ttFlagged, tt);

          case Token.NUMBER: {
              String s = ts.getString();
              if (this.inUseStrictDirective && ts.isNumberOctal()) {
                  reportError("msg.no.octal.strict");
              }
              return new NumberLiteral(ts.tokenBeg,
                                       s,
                                       ts.getNumber());
          }

          case Token.STRING:
              return createStringLiteral();

          case Token.DIV:
          case Token.ASSIGN_DIV:
              // Got / or /= which in this context means a regexp
              ts.readRegExp(tt);
              int pos = ts.tokenBeg, end = ts.tokenEnd;
              RegExpLiteral re = new RegExpLiteral(pos, end - pos);
              re.setValue(ts.getString());
              re.setFlags(ts.readAndClearRegExpFlags());
              return re;

          case Token.NULL:
          case Token.THIS:
          case Token.FALSE:
          case Token.TRUE:
              pos = ts.tokenBeg; end = ts.tokenEnd;
              return new KeywordLiteral(pos, end - pos, tt);

          case Token.RESERVED:
              reportError("msg.reserved.id");
              break;

          case Token.ERROR:
              // the scanner or one of its subroutines reported the error.
              break;

          case Token.EOF:
              reportError("msg.unexpected.eof");
              break;

          default:
              reportError("msg.syntax");
              break;
        }
        // should only be reachable in IDE/error-recovery mode
        return makeErrorNode();
    }

    private AstNode parenExpr() throws IOException {
        boolean wasInForInit = inForInit;
        inForInit = false;
        try {
            int lineno = ts.lineno;
            int begin = ts.tokenBeg;
            AstNode e = expr();
            if (peekToken() == Token.FOR) {
                return generatorExpression(e, begin);
            }
            mustMatchToken(Token.RP, "msg.no.paren");
            ParenthesizedExpression pn = new ParenthesizedExpression(
                begin, ts.tokenEnd - begin, e);
            pn.setLineno(lineno);
            return pn;
        } finally {
            inForInit = wasInForInit;
        }
    }

    private AstNode name(int ttFlagged, int tt) throws IOException {
        String nameString = ts.getString();
        int namePos = ts.tokenBeg, nameLineno = ts.lineno;
        if (0 != (ttFlagged & TI_CHECK_LABEL) && peekToken() == Token.COLON) {
            // Do not consume colon.  It is used as an unwind indicator
            // to return to statementHelper.
            Label label = new Label(namePos, ts.tokenEnd - namePos);
            label.setName(nameString);
            label.setLineno(ts.lineno);
            return label;
        }
        // Not a label.  Unfortunately peeking the next token to check for
        // a colon has biffed ts.tokenBeg, ts.tokenEnd.  We store the name's
        // bounds in instance vars and createNameNode uses them.
        saveNameTokenData(namePos, nameString, nameLineno);

        if (compilerEnv.isXmlAvailable()) {
            return propertyName(-1, nameString, 0);
        } else {
            return createNameNode(true, Token.NAME);
        }
    }

    /**
     * May return an {@link ArrayLiteral} or {@link ArrayComprehension}.
     */
    private AstNode arrayLiteral()
        throws IOException
    {
        if (currentToken != Token.LB) codeBug();
        int pos = ts.tokenBeg, end = ts.tokenEnd;
        List<AstNode> elements = new ArrayList<AstNode>();
        ArrayLiteral pn = new ArrayLiteral(pos);
        boolean after_lb_or_comma = true;
        int afterComma = -1;
        int skipCount = 0;
        for (;;) {
            int tt = peekToken();
            if (tt == Token.COMMA) {
                consumeToken();
                afterComma = ts.tokenEnd;
                if (!after_lb_or_comma) {
                    after_lb_or_comma = true;
                } else {
                    elements.add(new EmptyExpression(ts.tokenBeg, 1));
                    skipCount++;
                }
            } else if (tt == Token.RB) {
                consumeToken();
                // for ([a,] in obj) is legal, but for ([a] in obj) is
                // not since we have both key and value supplied. The
                // trick is that [a,] and [a] are equivalent in other
                // array literal contexts. So we calculate a special
                // length value just for destructuring assignment.
                end = ts.tokenEnd;
                pn.setDestructuringLength(elements.size() +
                                          (after_lb_or_comma ? 1 : 0));
                pn.setSkipCount(skipCount);
                if (afterComma != -1)
                    warnTrailingComma(pos, elements, afterComma);
                break;
            } else if (tt == Token.FOR && !after_lb_or_comma
                       && elements.size() == 1) {
                return arrayComprehension(elements.get(0), pos);
            } else if (tt == Token.EOF) {
                reportError("msg.no.bracket.arg");
                break;
            } else {
                if (!after_lb_or_comma) {
                    reportError("msg.no.bracket.arg");
                }
                elements.add(assignExpr());
                after_lb_or_comma = false;
                afterComma = -1;
            }
        }
        for (AstNode e : elements) {
            pn.addElement(e);
        }
        pn.setLength(end - pos);
        return pn;
    }

    /**
     * Parse a JavaScript 1.7 Array comprehension.
     * @param result the first expression after the opening left-bracket
     * @param pos start of LB token that begins the array comprehension
     * @return the array comprehension or an error node
     */
    private AstNode arrayComprehension(AstNode result, int pos)
        throws IOException
    {
        List<ArrayComprehensionLoop> loops =
                new ArrayList<ArrayComprehensionLoop>();
        while (peekToken() == Token.FOR) {
            loops.add(arrayComprehensionLoop());
        }
        int ifPos = -1;
        ConditionData data = null;
        if (peekToken() == Token.IF) {
            consumeToken();
            ifPos = ts.tokenBeg - pos;
            data = condition();
        }
        mustMatchToken(Token.RB, "msg.no.bracket.arg");
        ArrayComprehension pn = new ArrayComprehension(pos, ts.tokenEnd - pos);
        pn.setResult(result);
        pn.setLoops(loops);
        if (data != null) {
            pn.setIfPosition(ifPos);
            pn.setFilter(data.condition);
            pn.setFilterLp(data.lp - pos);
            pn.setFilterRp(data.rp - pos);
        }
        return pn;
    }

    private ArrayComprehensionLoop arrayComprehensionLoop()
        throws IOException
    {
        if (nextToken() != Token.FOR) codeBug();
        int pos = ts.tokenBeg;
        int eachPos = -1, lp = -1, rp = -1, inPos = -1;
        ArrayComprehensionLoop pn = new ArrayComprehensionLoop(pos);

        pushScope(pn);
        try {
            if (matchToken(Token.NAME)) {
                if (ts.getString().equals("each")) {
                    eachPos = ts.tokenBeg - pos;
                } else {
                    reportError("msg.no.paren.for");
                }
            }
            if (mustMatchToken(Token.LP, "msg.no.paren.for")) {
                lp = ts.tokenBeg - pos;
            }

            AstNode iter = null;
            switch (peekToken()) {
              case Token.LB:
              case Token.LC:
                  // handle destructuring assignment
                  iter = destructuringPrimaryExpr();
                  markDestructuring(iter);
                  break;
              case Token.NAME:
                  consumeToken();
                  iter = createNameNode();
                  break;
              default:
                  reportError("msg.bad.var");
            }

            // Define as a let since we want the scope of the variable to
            // be restricted to the array comprehension
            if (iter.getType() == Token.NAME) {
                defineSymbol(Token.LET, ts.getString(), true);
            }

            if (mustMatchToken(Token.IN, "msg.in.after.for.name"))
                inPos = ts.tokenBeg - pos;
            AstNode obj = expr();
            if (mustMatchToken(Token.RP, "msg.no.paren.for.ctrl"))
                rp = ts.tokenBeg - pos;

            pn.setLength(ts.tokenEnd - pos);
            pn.setIterator(iter);
            pn.setIteratedObject(obj);
            pn.setInPosition(inPos);
            pn.setEachPosition(eachPos);
            pn.setIsForEach(eachPos != -1);
            pn.setParens(lp, rp);
            return pn;
        } finally {
            popScope();
        }
    }

    private AstNode generatorExpression(AstNode result, int pos)
        throws IOException
    {
        return generatorExpression(result, pos, false);
    }

    private AstNode generatorExpression(AstNode result, int pos, boolean inFunctionParams)
        throws IOException
    {

        List<GeneratorExpressionLoop> loops =
                new ArrayList<GeneratorExpressionLoop>();
        while (peekToken() == Token.FOR) {
            loops.add(generatorExpressionLoop());
        }
        int ifPos = -1;
        ConditionData data = null;
        if (peekToken() == Token.IF) {
            consumeToken();
            ifPos = ts.tokenBeg - pos;
            data = condition();
        }
        if(!inFunctionParams) {
            mustMatchToken(Token.RP, "msg.no.paren.let");
        }
        GeneratorExpression pn = new GeneratorExpression(pos, ts.tokenEnd - pos);
        pn.setResult(result);
        pn.setLoops(loops);
        if (data != null) {
            pn.setIfPosition(ifPos);
            pn.setFilter(data.condition);
            pn.setFilterLp(data.lp - pos);
            pn.setFilterRp(data.rp - pos);
        }
        return pn;
    }

    private GeneratorExpressionLoop generatorExpressionLoop()
        throws IOException
    {
        if (nextToken() != Token.FOR) codeBug();
        int pos = ts.tokenBeg;
        int lp = -1, rp = -1, inPos = -1;
        GeneratorExpressionLoop pn = new GeneratorExpressionLoop(pos);

        pushScope(pn);
        try {
            if (mustMatchToken(Token.LP, "msg.no.paren.for")) {
                lp = ts.tokenBeg - pos;
            }

            AstNode iter = null;
            switch (peekToken()) {
              case Token.LB:
              case Token.LC:
                  // handle destructuring assignment
                  iter = destructuringPrimaryExpr();
                  markDestructuring(iter);
                  break;
              case Token.NAME:
                  consumeToken();
                  iter = createNameNode();
                  break;
              default:
                  reportError("msg.bad.var");
            }

            // Define as a let since we want the scope of the variable to
            // be restricted to the array comprehension
            if (iter.getType() == Token.NAME) {
                defineSymbol(Token.LET, ts.getString(), true);
            }

            if (mustMatchToken(Token.IN, "msg.in.after.for.name"))
                inPos = ts.tokenBeg - pos;
            AstNode obj = expr();
            if (mustMatchToken(Token.RP, "msg.no.paren.for.ctrl"))
                rp = ts.tokenBeg - pos;

            pn.setLength(ts.tokenEnd - pos);
            pn.setIterator(iter);
            pn.setIteratedObject(obj);
            pn.setInPosition(inPos);
            pn.setParens(lp, rp);
            return pn;
        } finally {
            popScope();
        }
    }

    private static final int PROP_ENTRY = 1;
    private static final int GET_ENTRY  = 2;
    private static final int SET_ENTRY  = 4;

    private ObjectLiteral objectLiteral()
        throws IOException
    {
        int pos = ts.tokenBeg, lineno = ts.lineno;
        int afterComma = -1;
        List<ObjectProperty> elems = new ArrayList<ObjectProperty>();
        Set<String> getterNames = null;
        Set<String> setterNames = null;
        if (this.inUseStrictDirective) {
            getterNames = new HashSet<String>();
            setterNames = new HashSet<String>();
        }

      commaLoop:
        for (;;) {
            String propertyName = null;
            int entryKind = PROP_ENTRY;
            int tt = peekToken();
            switch(tt) {
              case Token.NAME:
                  Name name = createNameNode();
                  propertyName = ts.getString();
                  int ppos = ts.tokenBeg;
                  consumeToken();

                  // This code path needs to handle both destructuring object
                  // literals like:
                  // var {get, b} = {get: 1, b: 2};
                  // and getters like:
                  // var x = {get 1() { return 2; };
                  // So we check a whitelist of tokens to check if we're at the
                  // first case. (Because of keywords, the second case may be
                  // many tokens.)
                  int peeked = peekToken();
                  boolean maybeGetterOrSetter =
                          "get".equals(propertyName)
                          || "set".equals(propertyName);
                  if (maybeGetterOrSetter
                          && peeked != Token.COMMA
                          && peeked != Token.COLON
                          && peeked != Token.RC)
                  {
                      boolean isGet = "get".equals(propertyName);
                      entryKind = isGet ? GET_ENTRY : SET_ENTRY;
                      AstNode pname = objliteralProperty();
                      if (pname == null) {
                          propertyName = null;
                      } else {
                          propertyName = ts.getString();
                          ObjectProperty objectProp = getterSetterProperty(
                                  ppos, pname, isGet);
                          elems.add(objectProp);
                      }
                  } else {
                      elems.add(plainProperty(name, tt));
                  }
                  break;

              case Token.RC:
                  if (afterComma != -1)
                      warnTrailingComma(pos, elems, afterComma);
                  break commaLoop;

              default:
                  AstNode pname = objliteralProperty();
                  if (pname == null) {
                      propertyName = null;
                  } else {
                      propertyName = ts.getString();
                      elems.add(plainProperty(pname, tt));
                  }
                  break;
            }

            if (this.inUseStrictDirective && propertyName != null) {
                switch (entryKind) {
                case PROP_ENTRY:
                    if (getterNames.contains(propertyName)
                            || setterNames.contains(propertyName)) {
                        addError("msg.dup.obj.lit.prop.strict", propertyName);
                    }
                    getterNames.add(propertyName);
                    setterNames.add(propertyName);
                    break;
                case GET_ENTRY:
                    if (getterNames.contains(propertyName)) {
                        addError("msg.dup.obj.lit.prop.strict", propertyName);
                    }
                    getterNames.add(propertyName);
                    break;
                case SET_ENTRY:
                    if (setterNames.contains(propertyName)) {
                        addError("msg.dup.obj.lit.prop.strict", propertyName);
                    }
                    setterNames.add(propertyName);
                    break;
                }
            }

            if (matchToken(Token.COMMA)) {
                afterComma = ts.tokenEnd;
            } else {
                break commaLoop;
            }
        }

        mustMatchToken(Token.RC, "msg.no.brace.prop");
        ObjectLiteral pn = new ObjectLiteral(pos, ts.tokenEnd - pos);
        pn.setElements(elems);
        pn.setLineno(lineno);
        return pn;
    }

    private AstNode objliteralProperty() throws IOException {
        AstNode pname;
        int tt = peekToken();
        switch(tt) {
          case Token.NAME:
              pname = createNameNode();
              break;

          case Token.STRING:
              pname = createStringLiteral();
              break;

          case Token.NUMBER:
              pname = new NumberLiteral(
                      ts.tokenBeg, ts.getString(), ts.getNumber());
              break;

          default:
              if (compilerEnv.isReservedKeywordAsIdentifier()
                      && TokenStream.isKeyword(ts.getString())) {
                  // convert keyword to property name, e.g. ({if: 1})
                  pname = createNameNode();
                  break;
              }
              reportError("msg.bad.prop");
              return null;
        }

        consumeToken();
        return pname;
    }

    private ObjectProperty plainProperty(AstNode property, int ptt)
        throws IOException
    {
        // Support, e.g., |var {x, y} = o| as destructuring shorthand
        // for |var {x: x, y: y} = o|, as implemented in spidermonkey JS 1.8.
        int tt = peekToken();
        if ((tt == Token.COMMA || tt == Token.RC) && ptt == Token.NAME
                && compilerEnv.getLanguageVersion() >= Context.VERSION_1_8) {
            if (!inDestructuringAssignment) {
                reportError("msg.bad.object.init");
            }
            AstNode nn = new Name(property.getPosition(), property.getString());
            ObjectProperty pn = new ObjectProperty();
            pn.putProp(Node.DESTRUCTURING_SHORTHAND, Boolean.TRUE);
            pn.setLeftAndRight(property, nn);
            return pn;
        }
        mustMatchToken(Token.COLON, "msg.no.colon.prop");
        ObjectProperty pn = new ObjectProperty();
        pn.setOperatorPosition(ts.tokenBeg);
        pn.setLeftAndRight(property, assignExpr());
        return pn;
    }

    private ObjectProperty getterSetterProperty(int pos, AstNode propName,
                                                boolean isGetter)
        throws IOException
    {
        FunctionNode fn = function(FunctionNode.FUNCTION_EXPRESSION);
        // We've already parsed the function name, so fn should be anonymous.
        Name name = fn.getFunctionName();
        if (name != null && name.length() != 0) {
            reportError("msg.bad.prop");
        }
        ObjectProperty pn = new ObjectProperty(pos);
        if (isGetter) {
            pn.setIsGetter();
        } else {
            pn.setIsSetter();
        }
        int end = getNodeEnd(fn);
        pn.setLeft(propName);
        pn.setRight(fn);
        pn.setLength(end - pos);
        return pn;
    }

    private Name createNameNode() {
        return createNameNode(false, Token.NAME);
    }

    /**
     * Create a {@code Name} node using the token info from the
     * last scanned name.  In some cases we need to either synthesize
     * a name node, or we lost the name token information by peeking.
     * If the {@code token} parameter is not {@link Token#NAME}, then
     * we use token info saved in instance vars.
     */
    private Name createNameNode(boolean checkActivation, int token) {
        int beg = ts.tokenBeg;
        String s = ts.getString();
        int lineno = ts.lineno;
        if (!"".equals(prevNameTokenString)) {
            beg = prevNameTokenStart;
            s = prevNameTokenString;
            lineno = prevNameTokenLineno;
            prevNameTokenStart = 0;
            prevNameTokenString = "";
            prevNameTokenLineno = 0;
        }
        if (s == null) {
            if (compilerEnv.isIdeMode()) {
                s = "";
            } else {
                codeBug();
            }
        }
        Name name = new Name(beg, s);
        name.setLineno(lineno);
        if (checkActivation) {
            checkActivationName(s, token);
        }
        return name;
    }

    private StringLiteral createStringLiteral() {
        int pos = ts.tokenBeg, end = ts.tokenEnd;
        StringLiteral s = new StringLiteral(pos, end - pos);
        s.setLineno(ts.lineno);
        s.setValue(ts.getString());
        s.setQuoteCharacter(ts.getQuoteChar());
        return s;
    }

    protected void checkActivationName(String name, int token) {
        if (!insideFunction()) {
            return;
        }
        boolean activation = false;
        if ("arguments".equals(name)
            || (compilerEnv.getActivationNames() != null
                && compilerEnv.getActivationNames().contains(name)))
        {
            activation = true;
        } else if ("length".equals(name)) {
            if (token == Token.GETPROP
                && compilerEnv.getLanguageVersion() == Context.VERSION_1_2)
            {
                // Use of "length" in 1.2 requires an activation object.
                activation = true;
            }
        }
        if (activation) {
            setRequiresActivation();
        }
    }

    protected void setRequiresActivation() {
        if (insideFunction()) {
            ((FunctionNode)currentScriptOrFn).setRequiresActivation();
        }
    }

    private void checkCallRequiresActivation(AstNode pn) {
        if ((pn.getType() == Token.NAME
             && "eval".equals(((Name)pn).getIdentifier()))
            || (pn.getType() == Token.GETPROP &&
                "eval".equals(((PropertyGet)pn).getProperty().getIdentifier())))
            setRequiresActivation();
    }

    protected void setIsGenerator() {
        if (insideFunction()) {
            ((FunctionNode)currentScriptOrFn).setIsGenerator();
        }
    }

    private void checkBadIncDec(UnaryExpression expr) {
        AstNode op = removeParens(expr.getOperand());
        int tt = op.getType();
        if (!(tt == Token.NAME
              || tt == Token.GETPROP
              || tt == Token.GETELEM
              || tt == Token.GET_REF
              || tt == Token.CALL))
            reportError(expr.getType() == Token.INC
                        ? "msg.bad.incr"
                        : "msg.bad.decr");
    }

    private ErrorNode makeErrorNode() {
        ErrorNode pn = new ErrorNode(ts.tokenBeg, ts.tokenEnd - ts.tokenBeg);
        pn.setLineno(ts.lineno);
        return pn;
    }

    // Return end of node.  Assumes node does NOT have a parent yet.
    private int nodeEnd(AstNode node) {
        return node.getPosition() + node.getLength();
    }

    private void saveNameTokenData(int pos, String name, int lineno) {
        prevNameTokenStart = pos;
        prevNameTokenString = name;
        prevNameTokenLineno = lineno;
    }

    /**
     * Return the file offset of the beginning of the input source line
     * containing the passed position.
     *
     * @param pos an offset into the input source stream.  If the offset
     * is negative, it's converted to 0, and if it's beyond the end of
     * the source buffer, the last source position is used.
     *
     * @return the offset of the beginning of the line containing pos
     * (i.e. 1+ the offset of the first preceding newline).  Returns -1
     * if the {@link CompilerEnvirons} is not set to ide-mode,
     * and {@link #parse(java.io.Reader,String,int)} was used.
     */
    private int lineBeginningFor(int pos) {
        if (sourceChars == null) {
            return -1;
        }
        if (pos <= 0) {
            return 0;
        }
        char[] buf = sourceChars;
        if (pos >= buf.length) {
            pos = buf.length - 1;
        }
        while (--pos >= 0) {
            char c = buf[pos];
            if (c == '\n' || c == '\r') {
                return pos + 1; // want position after the newline
            }
        }
        return 0;
    }

    private void warnMissingSemi(int pos, int end) {
        // Should probably change this to be a CompilerEnvirons setting,
        // with an enum Never, Always, Permissive, where Permissive means
        // don't warn for 1-line functions like function (s) {return x+2}
        if (compilerEnv.isStrictMode()) {
            int beg = Math.max(pos, lineBeginningFor(end));
            if (end == -1)
                end = ts.cursor;
            addStrictWarning("msg.missing.semi", "",
                             beg, end - beg);
        }
    }

    private void warnTrailingComma(int pos, List<?> elems, int commaPos) {
        if (compilerEnv.getWarnTrailingComma()) {
            // back up from comma to beginning of line or array/objlit
            if (!elems.isEmpty()) {
                pos = ((AstNode)elems.get(0)).getPosition();
            }
            pos = Math.max(pos, lineBeginningFor(commaPos));
            addWarning("msg.extra.trailing.comma", pos, commaPos - pos);
        }
    }


    private String readFully(Reader reader) throws IOException {
        BufferedReader in = new BufferedReader(reader);
        try {
            char[] cbuf = new char[1024];
            StringBuilder sb = new StringBuilder(1024);
            int bytes_read;
            while ((bytes_read = in.read(cbuf, 0, 1024)) != -1) {
                sb.append(cbuf, 0, bytes_read);
            }
            return sb.toString();
        } finally {
            in.close();
        }
    }

    // helps reduce clutter in the already-large function() method
    protected class PerFunctionVariables
    {
        private ScriptNode savedCurrentScriptOrFn;
        private Scope savedCurrentScope;
        private int savedEndFlags;
        private boolean savedInForInit;
        private Map<String,LabeledStatement> savedLabelSet;
        private List<Loop> savedLoopSet;
        private List<Jump> savedLoopAndSwitchSet;

        PerFunctionVariables(FunctionNode fnNode) {
            savedCurrentScriptOrFn = Parser.this.currentScriptOrFn;
            Parser.this.currentScriptOrFn = fnNode;

            savedCurrentScope = Parser.this.currentScope;
            Parser.this.currentScope = fnNode;

            savedLabelSet = Parser.this.labelSet;
            Parser.this.labelSet = null;

            savedLoopSet = Parser.this.loopSet;
            Parser.this.loopSet = null;

            savedLoopAndSwitchSet = Parser.this.loopAndSwitchSet;
            Parser.this.loopAndSwitchSet = null;

            savedEndFlags = Parser.this.endFlags;
            Parser.this.endFlags = 0;

            savedInForInit = Parser.this.inForInit;
            Parser.this.inForInit = false;
        }

        void restore() {
            Parser.this.currentScriptOrFn = savedCurrentScriptOrFn;
            Parser.this.currentScope = savedCurrentScope;
            Parser.this.labelSet = savedLabelSet;
            Parser.this.loopSet = savedLoopSet;
            Parser.this.loopAndSwitchSet = savedLoopAndSwitchSet;
            Parser.this.endFlags = savedEndFlags;
            Parser.this.inForInit = savedInForInit;
        }
    }

    /**
     * Given a destructuring assignment with a left hand side parsed
     * as an array or object literal and a right hand side expression,
     * rewrite as a series of assignments to the variables defined in
     * left from property accesses to the expression on the right.
     * @param type declaration type: Token.VAR or Token.LET or -1
     * @param left array or object literal containing NAME nodes for
     *        variables to assign
     * @param right expression to assign from
     * @return expression that performs a series of assignments to
     *         the variables defined in left
     */
    Node createDestructuringAssignment(int type, Node left, Node right)
    {
        String tempName = currentScriptOrFn.getNextTempName();
        Node result = destructuringAssignmentHelper(type, left, right,
            tempName);
        Node comma = result.getLastChild();
        comma.addChildToBack(createName(tempName));
        return result;
    }

    Node destructuringAssignmentHelper(int variableType, Node left,
                                       Node right, String tempName)
    {
        Scope result = createScopeNode(Token.LETEXPR, left.getLineno());
        result.addChildToFront(new Node(Token.LET,
            createName(Token.NAME, tempName, right)));
        try {
            pushScope(result);
            defineSymbol(Token.LET, tempName, true);
        } finally {
            popScope();
        }
        Node comma = new Node(Token.COMMA);
        result.addChildToBack(comma);
        List<String> destructuringNames = new ArrayList<String>();
        boolean empty = true;
        switch (left.getType()) {
          case Token.ARRAYLIT:
              empty = destructuringArray((ArrayLiteral)left,
                                         variableType, tempName, comma,
                                         destructuringNames);
              break;
          case Token.OBJECTLIT:
              empty = destructuringObject((ObjectLiteral)left,
                                          variableType, tempName, comma,
                                          destructuringNames);
              break;
          case Token.GETPROP:
          case Token.GETELEM:
              switch (variableType) {
                  case Token.CONST:
                  case Token.LET:
                  case Token.VAR:
                      reportError("msg.bad.assign.left");
              }
              comma.addChildToBack(simpleAssignment(left, createName(tempName)));
              break;
          default:
              reportError("msg.bad.assign.left");
        }
        if (empty) {
            // Don't want a COMMA node with no children. Just add a zero.
            comma.addChildToBack(createNumber(0));
        }
        result.putProp(Node.DESTRUCTURING_NAMES, destructuringNames);
        return result;
    }

    boolean destructuringArray(ArrayLiteral array,
                               int variableType,
                               String tempName,
                               Node parent,
                               List<String> destructuringNames)
    {
        boolean empty = true;
        int setOp = variableType == Token.CONST
            ? Token.SETCONST : Token.SETNAME;
        int index = 0;
        for (AstNode n : array.getElements()) {
            if (n.getType() == Token.EMPTY) {
                index++;
                continue;
            }
            Node rightElem = new Node(Token.GETELEM,
                                      createName(tempName),
                                      createNumber(index));
            if (n.getType() == Token.NAME) {
                String name = n.getString();
                parent.addChildToBack(new Node(setOp,
                                              createName(Token.BINDNAME,
                                                         name, null),
                                              rightElem));
                if (variableType != -1) {
                    defineSymbol(variableType, name, true);
                    destructuringNames.add(name);
                }
            } else {
                parent.addChildToBack
                    (destructuringAssignmentHelper
                     (variableType, n,
                      rightElem,
                      currentScriptOrFn.getNextTempName()));
            }
            index++;
            empty = false;
        }
        return empty;
    }

    boolean destructuringObject(ObjectLiteral node,
                                int variableType,
                                String tempName,
                                Node parent,
                                List<String> destructuringNames)
    {
        boolean empty = true;
        int setOp = variableType == Token.CONST
            ? Token.SETCONST : Token.SETNAME;

        for (ObjectProperty prop : node.getElements()) {
            int lineno = 0;
            // This function is sometimes called from the IRFactory when
            // when executing regression tests, and in those cases the
            // tokenStream isn't set.  Deal with it.
            if (ts != null) {
              lineno = ts.lineno;
            }
            AstNode id = prop.getLeft();
            Node rightElem = null;
            if (id instanceof Name) {
                Node s = Node.newString(((Name)id).getIdentifier());
                rightElem = new Node(Token.GETPROP, createName(tempName), s);
            } else if (id instanceof StringLiteral) {
                Node s = Node.newString(((StringLiteral)id).getValue());
                rightElem = new Node(Token.GETPROP, createName(tempName), s);
            } else if (id instanceof NumberLiteral) {
                Node s = createNumber((int)((NumberLiteral)id).getNumber());
                rightElem = new Node(Token.GETELEM, createName(tempName), s);
            } else {
                throw codeBug();
            }
            rightElem.setLineno(lineno);
            AstNode value = prop.getRight();
            if (value.getType() == Token.NAME) {
                String name = ((Name)value).getIdentifier();
                parent.addChildToBack(new Node(setOp,
                                              createName(Token.BINDNAME,
                                                         name, null),
                                              rightElem));
                if (variableType != -1) {
                    defineSymbol(variableType, name, true);
                    destructuringNames.add(name);
                }
            } else {
                parent.addChildToBack
                    (destructuringAssignmentHelper
                     (variableType, value, rightElem,
                      currentScriptOrFn.getNextTempName()));
            }
            empty = false;
        }
        return empty;
    }

    protected Node createName(String name) {
        checkActivationName(name, Token.NAME);
        return Node.newString(Token.NAME, name);
    }

    protected Node createName(int type, String name, Node child) {
        Node result = createName(name);
        result.setType(type);
        if (child != null)
            result.addChildToBack(child);
        return result;
    }

    protected Node createNumber(double number) {
        return Node.newNumber(number);
    }

    /**
     * Create a node that can be used to hold lexically scoped variable
     * definitions (via let declarations).
     *
     * @param token the token of the node to create
     * @param lineno line number of source
     * @return the created node
     */
    protected Scope createScopeNode(int token, int lineno) {
        Scope scope =new Scope();
        scope.setType(token);
        scope.setLineno(lineno);
        return scope;
    }

    // Quickie tutorial for some of the interpreter bytecodes.
    //
    // GETPROP - for normal foo.bar prop access; right side is a name
    // GETELEM - for normal foo[bar] element access; rhs is an expr
    // SETPROP - for assignment when left side is a GETPROP
    // SETELEM - for assignment when left side is a GETELEM
    // DELPROP - used for delete foo.bar or foo[bar]
    //
    // GET_REF, SET_REF, DEL_REF - in general, these mean you're using
    // get/set/delete on a right-hand side expression (possibly with no
    // explicit left-hand side) that doesn't use the normal JavaScript
    // Object (i.e. ScriptableObject) get/set/delete functions, but wants
    // to provide its own versions instead.  It will ultimately implement
    // Ref, and currently SpecialRef (for __proto__ etc.) and XmlName
    // (for E4X XML objects) are the only implementations.  The runtime
    // notices these bytecodes and delegates get/set/delete to the object.
    //
    // BINDNAME:  used in assignments.  LHS is evaluated first to get a
    // specific object containing the property ("binding" the property
    // to the object) so that it's always the same object, regardless of
    // side effects in the RHS.

    protected Node simpleAssignment(Node left, Node right) {
        int nodeType = left.getType();
        switch (nodeType) {
          case Token.NAME:
              if (inUseStrictDirective &&
                  "eval".equals(((Name) left).getIdentifier()))
              {
                  reportError("msg.bad.id.strict",
                              ((Name) left).getIdentifier());
              }
              left.setType(Token.BINDNAME);
              return new Node(Token.SETNAME, left, right);

          case Token.GETPROP:
          case Token.GETELEM: {
              Node obj, id;
              // If it's a PropertyGet or ElementGet, we're in the parse pass.
              // We could alternately have PropertyGet and ElementGet
              // override getFirstChild/getLastChild and return the appropriate
              // field, but that seems just as ugly as this casting.
              if (left instanceof PropertyGet) {
                  obj = ((PropertyGet)left).getTarget();
                  id = ((PropertyGet)left).getProperty();
              } else if (left instanceof ElementGet) {
                  obj = ((ElementGet)left).getTarget();
                  id = ((ElementGet)left).getElement();
              } else {
                  // This branch is called during IRFactory transform pass.
                  obj = left.getFirstChild();
                  id = left.getLastChild();
              }
              int type;
              if (nodeType == Token.GETPROP) {
                  type = Token.SETPROP;
                  // TODO(stevey) - see https://bugzilla.mozilla.org/show_bug.cgi?id=492036
                  // The new AST code generates NAME tokens for GETPROP ids where the old parser
                  // generated STRING nodes. If we don't set the type to STRING below, this will
                  // cause java.lang.VerifyError in codegen for code like
                  // "var obj={p:3};[obj.p]=[9];"
                  id.setType(Token.STRING);
              } else {
                  type = Token.SETELEM;
              }
              return new Node(type, obj, id, right);
          }
          case Token.GET_REF: {
              Node ref = left.getFirstChild();
              checkMutableReference(ref);
              return new Node(Token.SET_REF, ref, right);
          }
        }

        throw codeBug();
    }

    protected void checkMutableReference(Node n) {
        int memberTypeFlags = n.getIntProp(Node.MEMBER_TYPE_PROP, 0);
        if ((memberTypeFlags & Node.DESCENDANTS_FLAG) != 0) {
            reportError("msg.bad.assign.left");
        }
    }

    // remove any ParenthesizedExpression wrappers
    protected AstNode removeParens(AstNode node) {
        while (node instanceof ParenthesizedExpression) {
            node = ((ParenthesizedExpression)node).getExpression();
        }
        return node;
    }

    void markDestructuring(AstNode node) {
        if (node instanceof DestructuringForm) {
            ((DestructuringForm)node).setIsDestructuring(true);
        } else if (node instanceof ParenthesizedExpression) {
            markDestructuring(((ParenthesizedExpression)node).getExpression());
        }
    }

    // throw a failed-assertion with some helpful debugging info
    private RuntimeException codeBug()
        throws RuntimeException
    {
        throw Kit.codeBug("ts.cursor=" + ts.cursor
                          + ", ts.tokenBeg=" + ts.tokenBeg
                          + ", currentToken=" + currentToken);
    }
}
