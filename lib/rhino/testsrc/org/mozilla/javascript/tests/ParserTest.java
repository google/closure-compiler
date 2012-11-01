/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests;

import org.mozilla.javascript.ast.*;

import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.Token;
import org.mozilla.javascript.testing.TestErrorReporter;

import junit.framework.TestCase;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

public class ParserTest extends TestCase {
    CompilerEnvirons environment;

    @Override
    protected void setUp() throws Exception {
      super.setUp();
      environment = new CompilerEnvirons();
    }

    public void testAutoSemiColonBetweenNames() {
        AstRoot root = parse("\nx\ny\nz\n");
        AstNode first = ((ExpressionStatement)
            root.getFirstChild()).getExpression();
        assertEquals("x", first.getString());
        AstNode second = ((ExpressionStatement)
            root.getFirstChild().getNext()).getExpression();
        assertEquals("y", second.getString());
        AstNode third = ((ExpressionStatement)
            root.getFirstChild().getNext().getNext()).getExpression();
        assertEquals("z", third.getString());
    }

    public void testParseAutoSemiColonBeforeNewlineAndComments() throws IOException {
        AstRoot root = parseAsReader(
        		"var s = 3\n"
        		+ "/* */var t = 1;");
        assertNotNull(root.getComments());
        assertEquals(1, root.getComments().size());

        assertEquals("var s = 3;\nvar t = 1;\n", root.toSource());
    }

    public void testAutoSemiBeforeComment1() {
        parse("var a = 1\n/** a */ var b = 2");
    }

    public void testAutoSemiBeforeComment2() {
        parse("var a = 1\n/** a */\n var b = 2");
    }

    public void testAutoSemiBeforeComment3() {
        parse("var a = 1\n/** a */\n /** b */ var b = 2");
    }

    public void testLinenoAssign() {
        AstRoot root = parse("\n\na = b");
        ExpressionStatement st = (ExpressionStatement) root.getFirstChild();
        AstNode n = st.getExpression();

        assertTrue(n instanceof Assignment);
        assertEquals(Token.ASSIGN, n.getType());
        assertEquals(2, n.getLineno());
    }

    public void testLinenoCall() {
        AstRoot root = parse("\nfoo(123);");
        ExpressionStatement st = (ExpressionStatement) root.getFirstChild();
        AstNode n = st.getExpression();

        assertTrue(n instanceof FunctionCall);
        assertEquals(Token.CALL, n.getType());
        assertEquals(1, n.getLineno());
    }

    public void testLinenoGetProp() {
        AstRoot root = parse("\nfoo.bar");
        ExpressionStatement st = (ExpressionStatement) root.getFirstChild();
        AstNode n = st.getExpression();

        assertTrue(n instanceof PropertyGet);
        assertEquals(Token.GETPROP, n.getType());
        assertEquals(1, n.getLineno());

        PropertyGet getprop = (PropertyGet) n;
        AstNode m = getprop.getRight();

        assertTrue(m instanceof Name);
        assertEquals(Token.NAME, m.getType()); // used to be Token.STRING!
        assertEquals(1, m.getLineno());
    }

    public void testLinenoGetElem() {
        AstRoot root = parse("\nfoo[123]");
        ExpressionStatement st = (ExpressionStatement) root.getFirstChild();
        AstNode n = st.getExpression();

        assertTrue(n instanceof ElementGet);
        assertEquals(Token.GETELEM, n.getType());
        assertEquals(1, n.getLineno());
    }

    public void testLinenoComment() {
        AstRoot root = parse("\n/** a */");
        assertNotNull(root.getComments());
        assertEquals(1, root.getComments().size());
        assertEquals(1, root.getComments().first().getLineno());
    }

    public void testLinenoComment2() {
        AstRoot root = parse("\n/**\n\n a */");
        assertNotNull(root.getComments());
        assertEquals(1, root.getComments().size());
        assertEquals(1, root.getComments().first().getLineno());
    }

    public void testLinenoComment3() {
        AstRoot root = parse("\n  \n\n/**\n\n a */");
        assertNotNull(root.getComments());
        assertEquals(1, root.getComments().size());
        assertEquals(3, root.getComments().first().getLineno());
    }

    public void testLinenoComment4() {
        AstRoot root = parse("\n  \n\n  /**\n\n a */");
        assertNotNull(root.getComments());
        assertEquals(1, root.getComments().size());
        assertEquals(3, root.getComments().first().getLineno());
    }

    public void testLineComment5() {
        AstRoot root = parse("  /**\n* a.\n* b.\n* c.*/\n");
        assertNotNull(root.getComments());
        assertEquals(1, root.getComments().size());
        assertEquals(0, root.getComments().first().getLineno());
    }

    public void testLineComment6() {
        AstRoot root = parse("  \n/**\n* a.\n* b.\n* c.*/\n");
        assertNotNull(root.getComments());
        assertEquals(1, root.getComments().size());
        assertEquals(1, root.getComments().first().getLineno());
    }

    public void testLinenoComment7() {
        AstRoot root = parse("var x;\n/**\n\n a */");
        assertNotNull(root.getComments());
        assertEquals(1, root.getComments().size());
        assertEquals(1, root.getComments().first().getLineno());
    }

    public void testLinenoComment8() {
        AstRoot root = parse("\nvar x;/**\n\n a */");
        assertNotNull(root.getComments());
        assertEquals(1, root.getComments().size());
        assertEquals(1, root.getComments().first().getLineno());
    }

    public void testLinenoLiteral() {
        AstRoot root = parse(
            "\nvar d =\n" +
            "    \"foo\";\n" +
            "var e =\n" +
            "    1;\n" +
            "var f = \n" +
            "    1.2;\n" +
            "var g = \n" +
            "    2e5;\n" +
            "var h = \n" +
            "    'bar';\n");

        VariableDeclaration stmt1 = (VariableDeclaration) root.getFirstChild();
        List<VariableInitializer> vars1 = stmt1.getVariables();
        VariableInitializer firstVar = vars1.get(0);
        Name firstVarName = (Name) firstVar.getTarget();
        AstNode firstVarLiteral = firstVar.getInitializer();

        VariableDeclaration stmt2 = (VariableDeclaration) stmt1.getNext();
        List<VariableInitializer> vars2 = stmt2.getVariables();
        VariableInitializer secondVar = vars2.get(0);
        Name secondVarName = (Name) secondVar.getTarget();
        AstNode secondVarLiteral = secondVar.getInitializer();

        VariableDeclaration stmt3 = (VariableDeclaration) stmt2.getNext();
       List<VariableInitializer> vars3 = stmt3.getVariables();
        VariableInitializer thirdVar = vars3.get(0);
        Name thirdVarName = (Name) thirdVar.getTarget();
        AstNode thirdVarLiteral = thirdVar.getInitializer();

        VariableDeclaration stmt4 = (VariableDeclaration) stmt3.getNext();
        List<VariableInitializer> vars4 = stmt4.getVariables();
        VariableInitializer fourthVar = vars4.get(0);
        Name fourthVarName = (Name) fourthVar.getTarget();
        AstNode fourthVarLiteral = fourthVar.getInitializer();

        VariableDeclaration stmt5 = (VariableDeclaration) stmt4.getNext();
        List<VariableInitializer> vars5 = stmt5.getVariables();
        VariableInitializer fifthVar = vars5.get(0);
        Name fifthVarName = (Name) fifthVar.getTarget();
        AstNode fifthVarLiteral = fifthVar.getInitializer();

        assertEquals(2, firstVarLiteral.getLineno());
        assertEquals(4, secondVarLiteral.getLineno());
        assertEquals(6, thirdVarLiteral.getLineno());
        assertEquals(8, fourthVarLiteral.getLineno());
        assertEquals(10, fifthVarLiteral.getLineno());
    }

    public void testLinenoSwitch() {
        AstRoot root = parse(
            "\nswitch (a) {\n" +
            "   case\n" +
            "     1:\n" +
            "     b++;\n" +
            "   case 2:\n" +
            "   default:\n" +
            "     b--;\n" +
            "  }\n");

        SwitchStatement switchStmt = (SwitchStatement) root.getFirstChild();
        AstNode switchVar = switchStmt.getExpression();
        List<SwitchCase> cases = switchStmt.getCases();
        SwitchCase firstCase = cases.get(0);
        AstNode caseArg = firstCase.getExpression();
        List<AstNode> caseBody = firstCase.getStatements();
        ExpressionStatement exprStmt = (ExpressionStatement) caseBody.get(0);
        UnaryExpression incrExpr = (UnaryExpression) exprStmt.getExpression();
        AstNode incrVar = incrExpr.getOperand();

        SwitchCase secondCase = cases.get(1);
        AstNode defaultCase = cases.get(2);
        AstNode returnStmt = (AstNode) switchStmt.getNext();

        assertEquals(1, switchStmt.getLineno());
        assertEquals(1, switchVar.getLineno());
        assertEquals(2, firstCase.getLineno());
        assertEquals(3, caseArg.getLineno());
        assertEquals(4, exprStmt.getLineno());
        assertEquals(4, incrExpr.getLineno());
        assertEquals(4, incrVar.getLineno());
        assertEquals(5, secondCase.getLineno());
        assertEquals(6, defaultCase.getLineno());
    }


    public void testLinenoFunctionParams() {
        AstRoot root = parse(
            "\nfunction\n" +
            "    foo(\n" +
            "    a,\n" +
            "    b,\n" +
            "    c) {\n" +
            "}\n");
        FunctionNode function = (FunctionNode) root.getFirstChild();
        Name functionName = function.getFunctionName();

        AstNode body = function.getBody();
        List<AstNode> params = function.getParams();
        AstNode param1 = params.get(0);
        AstNode param2 = params.get(1);
        AstNode param3 = params.get(2);

        assertEquals(1, function.getLineno());
        assertEquals(2, functionName.getLineno());
        assertEquals(3, param1.getLineno());
        assertEquals(4, param2.getLineno());
        assertEquals(5, param3.getLineno());
        assertEquals(5, body.getLineno());
    }

    public void testLinenoVarDecl() {
      AstRoot root = parse(
          "\nvar\n" +
          "    a =\n" +
          "    3\n");

      VariableDeclaration decl = (VariableDeclaration) root.getFirstChild();
      List<VariableInitializer> vars = decl.getVariables();
      VariableInitializer init = vars.get(0);
      AstNode declName = init.getTarget();
      AstNode expr = init.getInitializer();

      assertEquals(1, decl.getLineno());
      assertEquals(2, init.getLineno());
      assertEquals(2, declName.getLineno());
      assertEquals(3, expr.getLineno());
    }

    public void testLinenoReturn() {
      AstRoot root = parse(
          "\nfunction\n" +
          "    foo(\n" +
          "    a,\n" +
          "    b,\n" +
          "    c) {\n" +
          "    return\n" +
          "    4;\n" +
          "}\n");
      FunctionNode function = (FunctionNode) root.getFirstChild();
      Name functionName = function.getFunctionName();

      AstNode body = function.getBody();
      ReturnStatement returnStmt = (ReturnStatement) body.getFirstChild();
      ExpressionStatement exprStmt = (ExpressionStatement) returnStmt.getNext();
      AstNode returnVal = exprStmt.getExpression();

      assertEquals(6, returnStmt.getLineno());
      assertEquals(7, exprStmt.getLineno());
      assertEquals(7, returnVal.getLineno());
    }

    public void testLinenoFor() {
        AstRoot root = parse(
            "\nfor(\n" +
            ";\n" +
            ";\n" +
            ") {\n" +
            "}\n");

        ForLoop forLoop = (ForLoop) root.getFirstChild();
        AstNode initClause = forLoop.getInitializer();
        AstNode condClause = forLoop.getCondition();
        AstNode incrClause = forLoop.getIncrement();

      assertEquals(1, forLoop.getLineno());
      assertEquals(2, initClause.getLineno());
      assertEquals(3, condClause.getLineno());
      assertEquals(4, incrClause.getLineno());
    }

    public void testLinenoInfix() {
        AstRoot root = parse(
            "\nvar d = a\n" +
            "    + \n" +
            "    b;\n" +
            "var\n" +
            "    e =\n" +
            "    a +\n" +
            "    c;\n" +
            "var f = b\n" +
            "    / c;\n");

        VariableDeclaration stmt1 = (VariableDeclaration) root.getFirstChild();
        List<VariableInitializer> vars1 = stmt1.getVariables();
        VariableInitializer var1 = vars1.get(0);
        Name firstVarName = (Name) var1.getTarget();
        InfixExpression var1Add = (InfixExpression) var1.getInitializer();

        VariableDeclaration stmt2 = (VariableDeclaration) stmt1.getNext();
        List<VariableInitializer> vars2 = stmt2.getVariables();
        VariableInitializer var2 = vars2.get(0);
        Name secondVarName = (Name) var2.getTarget();
        InfixExpression var2Add = (InfixExpression) var2.getInitializer();

        VariableDeclaration stmt3 = (VariableDeclaration) stmt2.getNext();
        List<VariableInitializer> vars3 = stmt3.getVariables();
        VariableInitializer var3 = vars3.get(0);
        Name thirdVarName = (Name) var3.getTarget();
        InfixExpression thirdVarDiv = (InfixExpression) var3.getInitializer();

        ReturnStatement returnStmt = (ReturnStatement) stmt3.getNext();

        assertEquals(1, var1.getLineno());
        assertEquals(1, firstVarName.getLineno());
        assertEquals(1, var1Add.getLineno());
        assertEquals(1, var1Add.getLeft().getLineno());
        assertEquals(3, var1Add.getRight().getLineno());

        // var directive with name on next line wrong --
        // should be 6.
        assertEquals(5, var2.getLineno());
        assertEquals(5, secondVarName.getLineno());
        assertEquals(6, var2Add.getLineno());
        assertEquals(6, var2Add.getLeft().getLineno());
        assertEquals(7, var2Add.getRight().getLineno());

        assertEquals(8, var3.getLineno());
        assertEquals(8, thirdVarName.getLineno());
        assertEquals(8, thirdVarDiv.getLineno());
        assertEquals(8, thirdVarDiv.getLeft().getLineno());
        assertEquals(9, thirdVarDiv.getRight().getLineno());
    }

    public void testLinenoPrefix() {
        AstRoot root = parse(
            "\na++;\n" +
            "   --\n" +
            "   b;\n");

        ExpressionStatement first = (ExpressionStatement) root.getFirstChild();
        ExpressionStatement secondStmt = (ExpressionStatement) first.getNext();
        UnaryExpression firstOp = (UnaryExpression) first.getExpression();
        UnaryExpression secondOp = (UnaryExpression) secondStmt.getExpression();
        AstNode firstVarRef = firstOp.getOperand();
        AstNode secondVarRef = secondOp.getOperand();

        assertEquals(1, firstOp.getLineno());
        assertEquals(2, secondOp.getLineno());
        assertEquals(1, firstVarRef.getLineno());
        assertEquals(3, secondVarRef.getLineno());
      }

    public void testLinenoIf() {
        AstRoot root = parse(
            "\nif\n" +
            "   (a == 3)\n" +
            "   {\n" +
            "     b = 0;\n" +
            "   }\n" +
            "     else\n" +
            "   {\n" +
            "     c = 1;\n" +
            "   }\n");

        IfStatement ifStmt = (IfStatement) root.getFirstChild();
        AstNode condClause = ifStmt.getCondition();
        AstNode thenClause = ifStmt.getThenPart();
        AstNode elseClause = ifStmt.getElsePart();

        assertEquals(1, ifStmt.getLineno());
        assertEquals(2, condClause.getLineno());
        assertEquals(3, thenClause.getLineno());
        assertEquals(7, elseClause.getLineno());
    }

    public void testLinenoTry() {
        AstRoot root = parse(
            "\ntry {\n" +
            "    var x = 1;\n" +
            "} catch\n" +
            "    (err)\n" +
            "{\n" +
            "} finally {\n" +
            "    var y = 2;\n" +
            "}\n");

        TryStatement tryStmt = (TryStatement) root.getFirstChild();
        AstNode tryBlock = tryStmt.getTryBlock();
        List<CatchClause> catchBlocks = tryStmt.getCatchClauses();
        CatchClause catchClause= catchBlocks.get(0);
        Block catchVarBlock = catchClause.getBody();
        Name catchVar = catchClause.getVarName();
        AstNode finallyBlock = tryStmt.getFinallyBlock();
        AstNode finallyStmt = (AstNode) finallyBlock.getFirstChild();

        assertEquals(1, tryStmt.getLineno());
        assertEquals(1, tryBlock.getLineno());
        assertEquals(5, catchVarBlock.getLineno());
        assertEquals(4, catchVar.getLineno());
        assertEquals(3, catchClause.getLineno());
        assertEquals(6, finallyBlock.getLineno());
        assertEquals(7, finallyStmt.getLineno());
      }

    public void testLinenoConditional() {
         AstRoot root = parse(
            "\na\n" +
            "    ?\n" +
            "    b\n" +
            "    :\n" +
            "    c\n" +
            "    ;\n");

        ExpressionStatement ex = (ExpressionStatement) root.getFirstChild();
        ConditionalExpression hook = (ConditionalExpression) ex.getExpression();
        AstNode condExpr = hook.getTestExpression();
        AstNode thenExpr = hook.getTrueExpression();
        AstNode elseExpr = hook.getFalseExpression();

        assertEquals(2, hook.getLineno());
        assertEquals(1, condExpr.getLineno());
        assertEquals(3, thenExpr.getLineno());
        assertEquals(5, elseExpr.getLineno());
    }

    public void testLinenoLabel() {
        AstRoot root = parse(
            "\nfoo:\n" +
            "a = 1;\n" +
            "bar:\n" +
            "b = 2;\n");

        LabeledStatement firstStmt = (LabeledStatement) root.getFirstChild();
        LabeledStatement secondStmt = (LabeledStatement) firstStmt.getNext();

        assertEquals(1, firstStmt.getLineno());
        assertEquals(3, secondStmt.getLineno());
    }

    public void testLinenoCompare() {
        AstRoot root = parse(
            "\na\n" +
            "<\n" +
            "b\n");

        ExpressionStatement expr = (ExpressionStatement) root.getFirstChild();
        InfixExpression compare = (InfixExpression) expr.getExpression();
        AstNode lhs = compare.getLeft();
        AstNode rhs = compare.getRight();

        assertEquals(1, lhs.getLineno());
        assertEquals(1, compare.getLineno());
        assertEquals(3, rhs.getLineno());
    }

    public void testLinenoEq() {
        AstRoot root = parse(
            "\na\n" +
            "==\n" +
            "b\n");
        ExpressionStatement expr = (ExpressionStatement) root.getFirstChild();
        InfixExpression compare = (InfixExpression) expr.getExpression();
        AstNode lhs = compare.getLeft();
        AstNode rhs = compare.getRight();

        assertEquals(1, lhs.getLineno());
        assertEquals(1, compare.getLineno());
        assertEquals(3, rhs.getLineno());
    }

    public void testLinenoPlusEq() {
        AstRoot root = parse(
            "\na\n" +
            "+=\n" +
            "b\n");
        ExpressionStatement expr = (ExpressionStatement) root.getFirstChild();
        Assignment assign = (Assignment) expr.getExpression();
        AstNode lhs = assign.getLeft();
        AstNode rhs = assign.getRight();

        assertEquals(1, lhs.getLineno());
        assertEquals(1, assign.getLineno());
        assertEquals(3, rhs.getLineno());
    }

    public void testLinenoComma() {
        AstRoot root = parse(
            "\na,\n" +
            "    b,\n" +
            "    c;\n");

        ExpressionStatement stmt = (ExpressionStatement) root.getFirstChild();
        InfixExpression comma1 = (InfixExpression) stmt.getExpression();
        InfixExpression comma2 = (InfixExpression) comma1.getLeft();
        AstNode cRef = comma1.getRight();
        AstNode aRef = comma2.getLeft();
        AstNode bRef = comma2.getRight();

        assertEquals(1, comma1.getLineno());
        assertEquals(1, comma2.getLineno());
        assertEquals(1, aRef.getLineno());
        assertEquals(2, bRef.getLineno());
        assertEquals(3, cRef.getLineno());
    }

    public void testRegexpLocation() {
      AstNode root = parse(
          "\nvar path =\n" +
          "      replace(\n" +
          "/a/g," +
          "'/');\n");

      VariableDeclaration firstVarDecl =
          (VariableDeclaration) root.getFirstChild();
      List<VariableInitializer> vars1 = firstVarDecl.getVariables();
      VariableInitializer firstInitializer = vars1.get(0);
      Name firstVarName = (Name) firstInitializer.getTarget();
      FunctionCall callNode =(FunctionCall) firstInitializer.getInitializer();
      AstNode fnName = callNode.getTarget();
      List<AstNode> args = callNode.getArguments();
      RegExpLiteral regexObject = (RegExpLiteral) args.get(0);
      AstNode aString = args.get(1);

      assertEquals(1, firstVarDecl.getLineno());
      assertEquals(1, firstVarName.getLineno());
      assertEquals(2, callNode.getLineno());
      assertEquals(2, fnName.getLineno());
      assertEquals(3, regexObject.getLineno());
      assertEquals(3, aString.getLineno());
    }

    public void testNestedOr() {
      AstNode root = parse(
          "\nif (a && \n" +
          "    b() || \n" +
          "    /* comment */\n" +
          "    c) {\n" +
          "}\n"
                           );

      IfStatement ifStmt = (IfStatement) root.getFirstChild();
      InfixExpression orClause = (InfixExpression) ifStmt.getCondition();
      InfixExpression andClause = (InfixExpression) orClause.getLeft();
      AstNode cName = orClause.getRight();

      assertEquals(1, ifStmt.getLineno());
      assertEquals(1, orClause.getLineno());
      assertEquals(1, andClause.getLineno());
      assertEquals(4, cName.getLineno());

    }

    public void testObjectLitGetterAndSetter() {
        AstNode root = parse(
            "'use strict';\n" +
            "function App() {}\n" +
            "App.prototype = {\n" +
            "  get appData() { return this.appData_; },\n" +
            "  set appData(data) { this.appData_ = data; }\n" +
            "};");
        assertNotNull(root);
    }

    public void testObjectLitLocation() {
      AstNode root = parse(
          "\nvar foo =\n" +
          "{ \n" +
          "'A' : 'A', \n" +
          "'B' : 'B', \n" +
          "'C' : \n" +
          "      'C' \n" +
          "};\n");

      VariableDeclaration firstVarDecl =
          (VariableDeclaration) root.getFirstChild();
      List<VariableInitializer> vars1 = firstVarDecl.getVariables();
      VariableInitializer firstInitializer = vars1.get(0);
      Name firstVarName = (Name) firstInitializer.getTarget();

      ObjectLiteral objectLiteral =
          (ObjectLiteral) firstInitializer.getInitializer();
      List<ObjectProperty> props = objectLiteral.getElements();
      ObjectProperty firstObjectLit = props.get(0);
      ObjectProperty secondObjectLit = props.get(1);
      ObjectProperty thirdObjectLit = props.get(2);

      AstNode firstKey = firstObjectLit.getLeft();
      AstNode firstValue = firstObjectLit.getRight();
      AstNode secondKey = secondObjectLit.getLeft();
      AstNode secondValue = secondObjectLit.getRight();
      AstNode thirdKey = thirdObjectLit.getLeft();
      AstNode thirdValue = thirdObjectLit.getRight();

      assertEquals(1, firstVarName.getLineno());
      assertEquals(2, objectLiteral.getLineno());
      assertEquals(3, firstObjectLit.getLineno());
      assertEquals(3, firstKey.getLineno());
      assertEquals(3, firstValue.getLineno());

      assertEquals(4, secondKey.getLineno());
      assertEquals(4, secondValue.getLineno());

      assertEquals(5, thirdKey.getLineno());
      assertEquals(6, thirdValue.getLineno());
    }

    public void testTryWithoutCatchLocation() {
      AstNode root = parse(
          "\ntry {\n" +
          "  var x = 1;\n" +
          "} finally {\n" +
          "  var y = 2;\n" +
          "}\n");

      TryStatement tryStmt = (TryStatement) root.getFirstChild();
      AstNode tryBlock = tryStmt.getTryBlock();
      List<CatchClause> catchBlocks = tryStmt.getCatchClauses();
      Scope finallyBlock = (Scope) tryStmt.getFinallyBlock();
      AstNode finallyStmt = (AstNode) finallyBlock.getFirstChild();

      assertEquals(1, tryStmt.getLineno());
      assertEquals(1, tryBlock.getLineno());
      assertEquals(3, finallyBlock.getLineno());
      assertEquals(4, finallyStmt.getLineno());
    }

    public void testTryWithoutFinallyLocation() {
      AstNode root = parse(
          "\ntry {\n" +
          "  var x = 1;\n" +
          "} catch (ex) {\n" +
          "  var y = 2;\n" +
          "}\n");

      TryStatement tryStmt = (TryStatement) root.getFirstChild();
      Scope tryBlock = (Scope) tryStmt.getTryBlock();
      List<CatchClause> catchBlocks = tryStmt.getCatchClauses();
      CatchClause catchClause = catchBlocks.get(0);
      AstNode catchStmt = catchClause.getBody();
      AstNode exceptionVar = catchClause.getVarName();
      AstNode varDecl = (AstNode) catchStmt.getFirstChild();

      assertEquals(1, tryStmt.getLineno());
      assertEquals(1, tryBlock.getLineno());
      assertEquals(3, catchClause.getLineno());
      assertEquals(3, catchStmt.getLineno());
      assertEquals(3, exceptionVar.getLineno());
      assertEquals(4, varDecl.getLineno());
    }

    public void testLinenoMultilineEq() {
      AstRoot root = parse(
          "\nif\n" +
          "    (((a == \n" +
          "  3) && \n" +
          "  (b == 2)) || \n" +
          " (c == 1)) {\n" +
          "}\n");
      IfStatement ifStmt = (IfStatement) root.getFirstChild();
      InfixExpression orTest = (InfixExpression) ifStmt.getCondition();
      ParenthesizedExpression cTestParen =
          (ParenthesizedExpression) orTest.getRight();
      InfixExpression cTest = (InfixExpression) cTestParen.getExpression();
      ParenthesizedExpression andTestParen =
          (ParenthesizedExpression) orTest.getLeft();
      InfixExpression andTest = (InfixExpression) andTestParen.getExpression();
      AstNode aTest = andTest.getLeft();
      AstNode bTest = andTest.getRight();

      assertEquals(1, ifStmt.getLineno());
      assertEquals(2, orTest.getLineno());
      assertEquals(2, andTest.getLineno());
      assertEquals(2, aTest.getLineno());
      assertEquals(4, bTest.getLineno());
      assertEquals(5, cTest.getLineno());
      assertEquals(5, cTestParen.getLineno());
      assertEquals(2, andTestParen.getLineno());
    }

    public void testLinenoMultilineBitTest() {
      AstRoot root = parse(
          "\nif (\n" +
          "      ((a \n" +
          "        | 3 \n" +
          "       ) == \n" +
          "       (b \n" +
          "        & 2)) && \n" +
          "      ((a \n" +
          "         ^ 0xffff) \n" +
          "       != \n" +
          "       (c \n" +
          "        << 1))) {\n" +
          "}\n");

      IfStatement ifStmt = (IfStatement) root.getFirstChild();
      InfixExpression andTest = (InfixExpression) ifStmt.getCondition();
      ParenthesizedExpression bigLHSExpr =
          (ParenthesizedExpression) andTest.getLeft();
      ParenthesizedExpression bigRHSExpr =
          (ParenthesizedExpression) andTest.getRight();

      InfixExpression eqTest = (InfixExpression) bigLHSExpr.getExpression();
      InfixExpression notEqTest = (InfixExpression) bigRHSExpr.getExpression();

      ParenthesizedExpression test1Expr =
          (ParenthesizedExpression) eqTest.getLeft();
      ParenthesizedExpression test2Expr =
          (ParenthesizedExpression) eqTest.getRight();

      ParenthesizedExpression test3Expr =
          (ParenthesizedExpression) notEqTest.getLeft();
      ParenthesizedExpression test4Expr =
          (ParenthesizedExpression) notEqTest.getRight();

      InfixExpression bitOrTest = (InfixExpression) test1Expr.getExpression();
      InfixExpression bitAndTest = (InfixExpression) test2Expr.getExpression();
      InfixExpression bitXorTest = (InfixExpression) test3Expr.getExpression();
      InfixExpression bitShiftTest = (InfixExpression) test4Expr.getExpression();

      assertEquals(1, ifStmt.getLineno());

      assertEquals(2, bigLHSExpr.getLineno());
      assertEquals(7, bigRHSExpr.getLineno());
      assertEquals(2, eqTest.getLineno());
      assertEquals(7, notEqTest.getLineno());

      assertEquals(2, test1Expr.getLineno());
      assertEquals(5, test2Expr.getLineno());
      assertEquals(7, test3Expr.getLineno());
      assertEquals(10, test4Expr.getLineno());

      assertEquals(2, bitOrTest.getLineno());
      assertEquals(5, bitAndTest.getLineno());
      assertEquals(7, bitXorTest.getLineno());
      assertEquals(10, bitShiftTest.getLineno());
    }

    public void testLinenoFunctionCall() {
      AstNode root = parse(
          "\nfoo.\n" +
          "bar.\n" +
          "baz(1);");

      ExpressionStatement stmt = (ExpressionStatement) root.getFirstChild();
      FunctionCall fc = (FunctionCall) stmt.getExpression();
      // Line number should get closest to the actual paren.
      assertEquals(3, fc.getLineno());
    }

    public void testLinenoName() {
      AstNode root = parse(
          "\na;\n" +
          "b.\n" +
          "c;\n");

      ExpressionStatement exprStmt = (ExpressionStatement) root.getFirstChild();
      AstNode aRef = exprStmt.getExpression();
      ExpressionStatement bExprStmt = (ExpressionStatement) exprStmt.getNext();
      AstNode bRef = bExprStmt.getExpression();

      assertEquals(1, aRef.getLineno());
      assertEquals(2, bRef.getLineno());
    }

    public void testLinenoDeclaration() {
      AstNode root = parse(
          "\na.\n" +
          "b=\n" +
          "function() {};\n");

      ExpressionStatement exprStmt = (ExpressionStatement) root.getFirstChild();
      Assignment fnAssignment = (Assignment) exprStmt.getExpression();
      PropertyGet aDotbName = (PropertyGet) fnAssignment.getLeft();
      AstNode aName = aDotbName.getLeft();
      AstNode bName = aDotbName.getRight();
      FunctionNode fnNode = (FunctionNode) fnAssignment.getRight();

      assertEquals(1, fnAssignment.getLineno());
      assertEquals(1, aDotbName.getLineno());
      assertEquals(1, aName.getLineno());
      assertEquals(2, bName.getLineno());
      assertEquals(3, fnNode.getLineno());
    }

    public void testInOperatorInForLoop1() {
        parse("var a={};function b_(p){ return p;};" +
              "for(var i=b_(\"length\" in a);i<0;) {}");
    }

    public void testInOperatorInForLoop2() {
        parse("var a={}; for (;(\"length\" in a);) {}");
    }

    public void testInOperatorInForLoop3() {
        parse("for (x in y) {}");
    }

    public void testJSDocAttachment1() {
        AstRoot root = parse("/** @type number */var a;");
        assertNotNull(root.getComments());
        assertEquals(1, root.getComments().size());
        assertEquals("/** @type number */",
                     root.getComments().first().getValue());
        assertNotNull(root.getFirstChild().getJsDoc());
    }

    public void testJSDocAttachment2() {
        AstRoot root = parse("/** @type number */a.b;");
        assertNotNull(root.getComments());
        assertEquals(1, root.getComments().size());
        assertEquals("/** @type number */",
                     root.getComments().first().getValue());
        ExpressionStatement st = (ExpressionStatement) root.getFirstChild();
        assertNotNull(st.getExpression().getJsDoc());
    }

    public void testJSDocAttachment3() {
        AstRoot root = parse("var a = /** @type number */(x);");
        assertNotNull(root.getComments());
        assertEquals(1, root.getComments().size());
        assertEquals("/** @type number */",
                     root.getComments().first().getValue());
        VariableDeclaration vd = (VariableDeclaration) root.getFirstChild();
        VariableInitializer vi = vd.getVariables().get(0);
        assertNotNull(vi.getInitializer().getJsDoc());
    }

    public void testJSDocAttachment4() {
        AstRoot root = parse("(function() {/** should not be attached */})()");
        assertNotNull(root.getComments());
        assertEquals(1, root.getComments().size());
        ExpressionStatement st = (ExpressionStatement) root.getFirstChild();
        FunctionCall fc = (FunctionCall) st.getExpression();
        ParenthesizedExpression pe = (ParenthesizedExpression) fc.getTarget();
        assertNull(pe.getJsDoc());
    }

    public void testJSDocAttachment5() {
        AstRoot root = parse("({/** attach me */ 1: 2});");
        assertNotNull(root.getComments());
        assertEquals(1, root.getComments().size());
        ExpressionStatement st = (ExpressionStatement) root.getFirstChild();
        ParenthesizedExpression pt = (ParenthesizedExpression) st.getExpression();
        ObjectLiteral lit = (ObjectLiteral) pt.getExpression();
        NumberLiteral number =
            (NumberLiteral) lit.getElements().get(0).getLeft();
        assertNotNull(number.getJsDoc());
    }

    public void testJSDocAttachment6() {
        AstRoot root = parse("({1: /** don't attach me */ 2, 3: 4});");
        assertNotNull(root.getComments());
        assertEquals(1, root.getComments().size());
        ExpressionStatement st = (ExpressionStatement) root.getFirstChild();
        ParenthesizedExpression pt = (ParenthesizedExpression) st.getExpression();
        ObjectLiteral lit = (ObjectLiteral) pt.getExpression();
        for (ObjectProperty el : lit.getElements()) {
          assertNull(el.getLeft().getJsDoc());
          assertNull(el.getRight().getJsDoc());
        }
    }

    public void testJSDocAttachment7() {
        AstRoot root = parse("({/** attach me */ '1': 2});");
        assertNotNull(root.getComments());
        assertEquals(1, root.getComments().size());
        ExpressionStatement st = (ExpressionStatement) root.getFirstChild();
        ParenthesizedExpression pt = (ParenthesizedExpression) st.getExpression();
        ObjectLiteral lit = (ObjectLiteral) pt.getExpression();
        StringLiteral stringLit =
            (StringLiteral) lit.getElements().get(0).getLeft();
        assertNotNull(stringLit.getJsDoc());
    }

    public void testJSDocAttachment8() {
        AstRoot root = parse("({'1': /** attach me */ (foo())});");
        assertNotNull(root.getComments());
        assertEquals(1, root.getComments().size());
        ExpressionStatement st = (ExpressionStatement) root.getFirstChild();
        ParenthesizedExpression pt = (ParenthesizedExpression) st.getExpression();
        ObjectLiteral lit = (ObjectLiteral) pt.getExpression();
        ParenthesizedExpression parens =
            (ParenthesizedExpression) lit.getElements().get(0).getRight();
        assertNotNull(parens.getJsDoc());
    }

    public void testJSDocAttachment9() {
        AstRoot root = parse("({/** attach me */ foo: 2});");
        assertNotNull(root.getComments());
        assertEquals(1, root.getComments().size());
        ExpressionStatement st = (ExpressionStatement) root.getFirstChild();
        ParenthesizedExpression pt = (ParenthesizedExpression) st.getExpression();
        ObjectLiteral lit = (ObjectLiteral) pt.getExpression();
        Name objLitKey =
            (Name) lit.getElements().get(0).getLeft();
        assertNotNull(objLitKey.getJsDoc());
    }

    public void testJSDocAttachment10() {
        AstRoot root = parse("({foo: /** attach me */ (bar)});");
        assertNotNull(root.getComments());
        assertEquals(1, root.getComments().size());
        ExpressionStatement st = (ExpressionStatement) root.getFirstChild();
        ParenthesizedExpression pt = (ParenthesizedExpression) st.getExpression();
        ObjectLiteral lit = (ObjectLiteral) pt.getExpression();
        ParenthesizedExpression parens =
            (ParenthesizedExpression) lit.getElements().get(0).getRight();
        assertNotNull(parens.getJsDoc());
    }

    public void testJSDocAttachment11() {
      AstRoot root = parse("({/** attach me */ get foo() {}});");
      assertNotNull(root.getComments());
      assertEquals(1, root.getComments().size());
      ExpressionStatement st = (ExpressionStatement) root.getFirstChild();
      ParenthesizedExpression pt = (ParenthesizedExpression) st.getExpression();
      ObjectLiteral lit = (ObjectLiteral) pt.getExpression();
      Name objLitKey =
          (Name) lit.getElements().get(0).getLeft();
      assertNotNull(objLitKey.getJsDoc());
  }

    public void testJSDocAttachment12() {
      AstRoot root = parse("({/** attach me */ get 1() {}});");
      assertNotNull(root.getComments());
      assertEquals(1, root.getComments().size());
      ExpressionStatement st = (ExpressionStatement) root.getFirstChild();
      ParenthesizedExpression pt = (ParenthesizedExpression) st.getExpression();
      ObjectLiteral lit = (ObjectLiteral) pt.getExpression();
      NumberLiteral number =
          (NumberLiteral) lit.getElements().get(0).getLeft();
      assertNotNull(number.getJsDoc());
  }

  public void testJSDocAttachment13() {
      AstRoot root = parse("({/** attach me */ get 'foo'() {}});");
      assertNotNull(root.getComments());
      assertEquals(1, root.getComments().size());
      ExpressionStatement st = (ExpressionStatement) root.getFirstChild();
      ParenthesizedExpression pt = (ParenthesizedExpression) st.getExpression();
      ObjectLiteral lit = (ObjectLiteral) pt.getExpression();
      StringLiteral stringLit =
          (StringLiteral) lit.getElements().get(0).getLeft();
      assertNotNull(stringLit.getJsDoc());
  }

    public void testJSDocAttachment14() {
        AstRoot root = parse("var a = (/** @type {!Foo} */ {});");
        assertNotNull(root.getComments());
        assertEquals(1, root.getComments().size());
        assertEquals("/** @type {!Foo} */",
                     root.getComments().first().getValue());
        VariableDeclaration vd = (VariableDeclaration) root.getFirstChild();
        VariableInitializer vi = vd.getVariables().get(0);
        assertNotNull(((ParenthesizedExpression)vi.getInitializer())
                       .getExpression().getJsDoc());
    }

    public void testJSDocAttachment15() {
        AstRoot root = parse("/** @private */ x(); function f() {}");
        assertNotNull(root.getComments());
        assertEquals(1, root.getComments().size());

        ExpressionStatement st = (ExpressionStatement) root.getFirstChild();
        assertNotNull(st.getExpression().getJsDoc());
    }

    public void testJSDocAttachment16() {
        AstRoot root = parse(
        "/** @suppress {with} */ with (context) {\n" +
        "  eval('[' + expr + ']');\n" +
        "}\n");
        assertNotNull(root.getComments());
        assertEquals(1, root.getComments().size());

        WithStatement st = (WithStatement) root.getFirstChild();
        assertNotNull(st.getJsDoc());
    }

    public void testParsingWithoutJSDoc() {
        AstRoot root = parse("var a = /** @type number */(x);", false);
        assertNotNull(root.getComments());
        assertEquals(1, root.getComments().size());
        assertEquals("/** @type number */",
                     root.getComments().first().getValue());
        VariableDeclaration vd = (VariableDeclaration) root.getFirstChild();
        VariableInitializer vi = vd.getVariables().get(0);
        assertTrue(vi.getInitializer() instanceof ParenthesizedExpression);
    }

    public void testParseCommentsAsReader() throws IOException {
        AstRoot root = parseAsReader(
            "/** a */var a;\n /** b */var b; /** c */var c;");
        assertNotNull(root.getComments());
        assertEquals(3, root.getComments().size());
        Comment[] comments = new Comment[3];
        comments = root.getComments().toArray(comments);
        assertEquals("/** a */", comments[0].getValue());
        assertEquals("/** b */", comments[1].getValue());
        assertEquals("/** c */", comments[2].getValue());
    }

    public void testParseCommentsAsReader2() throws IOException {
        String js = "";
        for (int i = 0; i < 100; i++) {
            String stri = Integer.toString(i);
            js += "/** Some comment for a" + stri + " */" +
                  "var a" + stri + " = " + stri + ";\n";
        }
        AstRoot root = parseAsReader(js);
    }

    public void testLinenoCommentsWithJSDoc() throws IOException {
        AstRoot root = parseAsReader(
            "/* foo \n" +
            " bar \n" +
            "*/\n" +
            "/** @param {string} x */\n" +
            "function a(x) {};\n");
        assertNotNull(root.getComments());
        assertEquals(2, root.getComments().size());
        Comment[] comments = new Comment[2];
        comments = root.getComments().toArray(comments);
        assertEquals(0, comments[0].getLineno());
        assertEquals(3, comments[1].getLineno());
    }

    public void testParseUnicodeFormatStringLiteral() {
        AstRoot root = parse("'A\u200DB'");
        ExpressionStatement st = (ExpressionStatement) root.getFirstChild();
        StringLiteral stringLit = (StringLiteral) st.getExpression();
        assertEquals("A\u200DB", stringLit.getValue());
    }

    public void testParseUnicodeFormatName() {
        AstRoot root = parse("A\u200DB");
        AstNode first = ((ExpressionStatement)
            root.getFirstChild()).getExpression();
        assertEquals("AB", first.getString());
    }

    public void testParseUnicodeReservedKeywords1() {
        AstRoot root = parse("\\u0069\\u0066");
        AstNode first = ((ExpressionStatement)
            root.getFirstChild()).getExpression();
        assertEquals("i\\u0066", first.getString());
    }

    public void testParseUnicodeReservedKeywords2() {
        AstRoot root = parse("v\\u0061\\u0072");
        AstNode first = ((ExpressionStatement)
            root.getFirstChild()).getExpression();
        assertEquals("va\\u0072", first.getString());
    }

    public void testParseUnicodeReservedKeywords3() {
        // All are keyword "while"
        AstRoot root = parse("w\\u0068\\u0069\\u006C\\u0065;" +
            "\\u0077\\u0068il\\u0065; \\u0077h\\u0069le;");
        AstNode first = ((ExpressionStatement)
            root.getFirstChild()).getExpression();
        AstNode second = ((ExpressionStatement)
            root.getFirstChild().getNext()).getExpression();
        AstNode third = ((ExpressionStatement)
            root.getFirstChild().getNext().getNext()).getExpression();
        assertEquals("whil\\u0065", first.getString());
        assertEquals("whil\\u0065", second.getString());
        assertEquals("whil\\u0065", third.getString());
    }

    public void testParseObjectLiteral1() {
      environment.setReservedKeywordAsIdentifier(true);

      parse("({a:1});");
      parse("({'a':1});");
      parse("({0:1});");

      // property getter and setter definitions accept string and number
      parse("({get a() {return 1}});");
      parse("({get 'a'() {return 1}});");
      parse("({get 0() {return 1}});");

      parse("({set a(a) {return 1}});");
      parse("({set 'a'(a) {return 1}});");
      parse("({set 0(a) {return 1}});");

      // keywords ok
      parse("({function:1});");
      // reserved words ok
      parse("({float:1});");
    }

    public void testParseObjectLiteral2() {
      // keywords, fail
      environment.setReservedKeywordAsIdentifier(false);
      expectParseErrors("({function:1});",
          new String[] { "invalid property id" });

      environment.setReservedKeywordAsIdentifier(true);

      // keywords ok
      parse("({function:1});");
    }

    public void testParseObjectLiteral3() {
      environment.setLanguageVersion(Context.VERSION_1_8);
      environment.setReservedKeywordAsIdentifier(true);
      parse("var {get} = {get:1};");

      environment.setReservedKeywordAsIdentifier(false);
      parse("var {get} = {get:1};");
      expectParseErrors("var {get} = {if:1};",
          new String[] { "invalid property id" });
    }

    public void testParseKeywordPropertyAccess() {
      environment.setReservedKeywordAsIdentifier(true);

      // keywords ok
      parse("({function:1}).function;");

      // reserved words ok.
      parse("({import:1}).import;");
    }

    private void expectParseErrors(String string, String [] errors) {
      parse(string, errors, null, false);
    }

    private AstRoot parse(String string) {
        return parse(string, true);
    }

    private AstRoot parse(String string, boolean jsdoc) {
       return parse(string, null, null, jsdoc);
    }

    private AstRoot parse(
        String string, final String [] errors, final String [] warnings,
        boolean jsdoc) {
        TestErrorReporter testErrorReporter =
            new TestErrorReporter(errors, warnings) {
          @Override
          public EvaluatorException runtimeError(
               String message, String sourceName, int line, String lineSource,
               int lineOffset) {
             if (errors == null) {
               throw new UnsupportedOperationException();
             }
             return new EvaluatorException(
               message, sourceName, line, lineSource, lineOffset);
           }
        };
        environment.setErrorReporter(testErrorReporter);

        environment.setRecordingComments(true);
        environment.setRecordingLocalJsDocComments(jsdoc);

        Parser p = new Parser(environment, testErrorReporter);
        AstRoot script = null;
        try {
          script = p.parse(string, null, 0);
        } catch (EvaluatorException e) {
          if (errors == null) {
            // EvaluationExceptions should not occur when we aren't expecting
            // errors.
            throw e;
          }
        }

        assertTrue(testErrorReporter.hasEncounteredAllErrors());
        assertTrue(testErrorReporter.hasEncounteredAllWarnings());

        return script;
    }

    private AstRoot parseAsReader(String string) throws IOException {
        TestErrorReporter testErrorReporter = new TestErrorReporter(null, null);
        environment.setErrorReporter(testErrorReporter);

        environment.setRecordingComments(true);
        environment.setRecordingLocalJsDocComments(true);

        Parser p = new Parser(environment, testErrorReporter);
        AstRoot script = p.parse(new StringReader(string), null, 0);

        assertTrue(testErrorReporter.hasEncounteredAllErrors());
        assertTrue(testErrorReporter.hasEncounteredAllWarnings());

        return script;
    }
}
