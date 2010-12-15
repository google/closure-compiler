/*
 * Copyright 2010 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp.jsonml;

import com.google.javascript.jscomp.jsonml.JsonML;
import com.google.javascript.jscomp.jsonml.JsonMLUtil;
import com.google.javascript.jscomp.jsonml.TagAttr;
import com.google.javascript.jscomp.jsonml.TagType;
import com.google.javascript.jscomp.jsonml.Validator;

import junit.framework.TestCase;

/**
 * Tests validation of particular JsonML elements.
 *
 * @author dhans@google.com (Daniel Hans)
 */
public class JsonMLValidationTest extends TestCase {

  // Used for correct statements - error message should be null
  private void testValidation(String jsonml) throws Exception {
    JsonML jsonMLRoot = JsonMLUtil.parseString(jsonml);
    String msg = Validator.validate(jsonMLRoot);
    if (msg != null) {
      String errorMsg = String.format(
          "Validation error for %s.\n Received: %s\n", jsonml, msg);
    }
  }

  private void testValidation(String jsonml, String expected)
      throws Exception {
    JsonML jsonMLRoot = JsonMLUtil.parseString(jsonml);
    String msg = Validator.validate(jsonMLRoot);
    if (!msg.equals(expected)) {
      String errorMsg = String.format(
          "Validation error for %s.\n Received: %s\n Expected: %s\n",
          jsonml, msg, expected);
      assertEquals(errorMsg, expected, msg);
    }
  }

  private void testNotEnoughChildrenValidation(String jsonml, TagType type,
      int expected, int actual) throws Exception {
    testValidation(jsonml,
        String.format(Validator.NOT_ENOUGH_CHILDREN_FMT,
        type, expected, actual));
  }

  private void testTooManyChildrenValidation(String jsonml, TagType type,
      int expected, int actual) throws Exception {
    testValidation(jsonml,
        String.format(Validator.TOO_MANY_CHILDREN_FMT,
        type, expected, actual));
  }

  private void testWrongChildTypeValidation(String jsonml, TagType type,
      TagType expected, TagType actual, int index) throws Exception {
    testWrongChildTypeValidation(jsonml, type, new TagType[] { expected },
        actual, index);
  }

  private void testWrongChildTypeValidation(String jsonml, TagType type,
      TagType[] expected, TagType actual, int index) throws Exception {
    testValidation(jsonml,
        String.format(Validator.WRONG_CHILD_TYPE_FMT,
        index, type, Validator.printList(expected), actual));
  }

  private void testMissingArgument(String jsonml, TagAttr attr, TagType type)
      throws Exception {
    testValidation(jsonml,
        String.format(Validator.MISSING_ARGUMENT, attr, type));
  }

  public void testAssignExpr() throws Exception {
    // correct statement
    testValidation("" +
        "['AssignExpr',{'op':'='}," +
            "['IdExpr',{'name':'x'}]," +
            "['LiteralExpr',{'type':'number','value':1}]]");
    // wrong number of children
    testNotEnoughChildrenValidation("" +
        "['AssignExpr',{'op':'='}," +
            "['IdExpr',{'name':'x'}]]",
        TagType.AssignExpr, 2, 1);
    testTooManyChildrenValidation("" +
        "['AssignExpr',{'op':'='}," +
            "['IdExpr',{'name':'x'}]," +
            "['IdExpr',{'name':'y'}]," +
            "['IdExpr',{'name':'z'}]]",
        TagType.AssignExpr, 2, 3);
    // missing attribute
    testMissingArgument("" +
        "['AssignExpr',{}," +
            "['IdExpr',{'name':'x'}]," +
            "['LiteralExpr',{'type':'number','value':1}]]",
        TagAttr.OP, TagType.AssignExpr);
  }

  public void testBinaryExpr() throws Exception {
    // correct statement
    testValidation("" +
        "['BinaryExpr',{'op':'+'}," +
            "['IdExpr',{'name':'a'}]," +
            "['LiteralExpr',{'type':'number','value':1}]]");
    // wrong number of children
    testNotEnoughChildrenValidation("" +
        "['BinaryExpr',{'op':'+'}," +
            "['IdExpr',{'name':'a'}]]",
        TagType.BinaryExpr, 2, 1);
    testTooManyChildrenValidation("" +
        "['BinaryExpr',{'op':'&&'}," +
            "['IdExpr',{'name':'a'}]," +
            "['IdExpr',{'name':'b'}]," +
            "['IdExpr',{'name':'c'}]]",
        TagType.BinaryExpr, 2, 3);
    // missing attribute
    testMissingArgument("" +
        "['BinaryExpr',{}," +
            "['IdExpr',{'name':'a'}]," +
            "['LiteralExpr',{'type':'number','value':1}]]",
        TagAttr.OP, TagType.BinaryExpr);
  }

  public void testCaseValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['Case',{}," +
            "['IdExpr',{'name':'a'}]]");
    testValidation("" +
        "['Case',{}," +
            "['IdExpr',{'name':'a'}]," +
            "['CallExpr',{}," +
                "['IdExpr',{'name':'foo'}]]]");
    // wrong number of children
    testNotEnoughChildrenValidation("" +
        "['Case',{}]",
        TagType.Case, 1, 0);
  }

  public void testCatchValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['CatchClause',{}," +
            "['IdPatt',{'name':'e'}]," +
            "['BlockStmt',{}]]");
    // wrong number of children
    testNotEnoughChildrenValidation("" +
        "['CatchClause',{}," +
            "['IdPatt',{'name':'e'}]]",
        TagType.CatchClause, 2, 1);
    // wrong children types
    testWrongChildTypeValidation("" +
        "['CatchClause',{}," +
            "['IdExpr',{'name':'e'}]," +
            "['BlockStmt',{}]]",
        TagType.CatchClause, TagType.IdPatt, TagType.IdExpr, 0);
  }

  public void testConditionalExprValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['ConditionalExpr',{}," +
           "['BinaryExpr',{'op':'=='}," +
                "['IdExpr',{'name':'x'}]," +
                "['LiteralExpr',{'type':'number','value':0}]]," +
           "['LiteralExpr',{'type':'number','value':0}]," +
           "['LiteralExpr',{'type':'number','value':1}]]");
    // wrong number of children
    testNotEnoughChildrenValidation("" +
        "['ConditionalExpr',{}," +
            "['BinaryExpr',{'op':'=='}," +
                "['IdExpr',{'name':'x'}]," +
                "['LiteralExpr',{'type':'number','value':0}]]]",
        TagType.ConditionalExpr, 3, 1);
    testNotEnoughChildrenValidation("" +
        "['ConditionalExpr',{}," +
            "['BinaryExpr',{'op':'=='}," +
                "['IdExpr',{'name':'x'}]," +
                "['LiteralExpr',{'type':'number','value':0}]]," +
            "['LiteralExpr',{'type':'number','value':1}]]",
        TagType.ConditionalExpr, 3, 2);
  }

  public void testCountExprValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['CountExpr',{'isPrefix':false,'op':'++'}," +
            "['IdExpr',{'name':'x'}]]");
    // wrong number of children
    testTooManyChildrenValidation("" +
        "['CountExpr',{'isPrefix':false,'op':'++'}," +
            "['IdExpr',{'name':'x'}]," +
            "['IdExpr',{'name':'y'}]]",
        TagType.CountExpr, 1, 2);
    // missing attribute
    testMissingArgument("" +
        "['CountExpr',{'op':'++'}," +
            "['IdExpr',{'name':'x'}]]",
         TagAttr.IS_PREFIX, TagType.CountExpr);
    testMissingArgument("" +
        "['CountExpr',{'isPrefix':false}," +
            "['IdExpr',{'name':'x'}]]",
        TagAttr.OP, TagType.CountExpr);
  }

  public void testDataProp() throws Exception {
    // correct statement
    testValidation("" +
        "['DataProp',{'name':'x'}," +
            "['LiteralExpr',{'type':'number','value':1}]]");
    // wrong number of children
    testNotEnoughChildrenValidation("" +
        "['DataProp',{'name':'x'}]",
        TagType.DataProp, 1, 0);
    // missing argument
    testMissingArgument("" +
        "['DataProp', {}," +
            "['LiteralExpr',{'type':'number','value':1}]]",
        TagAttr.NAME, TagType.DataProp);
  }

  public void testDeleteExpr() throws Exception {
    // correct statement
    testValidation("" +
        "['DeleteExpr',{}," +
            "['IdExpr',{'name':'x'}]]");
    //wrong number of children
    testNotEnoughChildrenValidation("" +
        "['DeleteExpr',{}]",
        TagType.DeleteExpr, 1, 0);
    testTooManyChildrenValidation("" +
        "['DeleteExpr',{}," +
            "['IdExpr',{'name':'x'}]," +
            "['IdExpr',{'name':'y'}]]",
        TagType.DeleteExpr, 1, 2);
  }

  public void testDoWhileStmtValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['DoWhileStmt',{}," +
            "['BlockStmt',{}]," +
            "['LiteralExpr',{'type':'boolean','value':true}]]");
    // wrong number of children
    testNotEnoughChildrenValidation("" +
        "['DoWhileStmt',{}]",
        TagType.DoWhileStmt, 2, 0);
    testTooManyChildrenValidation("" +
        "['DoWhileStmt',{}," +
            "['BlockStmt',{}]," +
            "['BlockStmt',{}]," +
            "['LiteralExpr',{'type':'boolean','value':true}]]",
            TagType.DoWhileStmt, 2, 3);
  }

  public void testEmptyStmtValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['EmptyStmt',{}]");
    // wrong number of children
    testTooManyChildrenValidation("" +
        "['EmptyStmt',{}," +
            "['BlockStmt',{}]]",
        TagType.EmptyStmt, 0, 1);
  }

  public void testForInStmtValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['ForInStmt',{}," +
            "['IdExpr',{'name':'x'}]," +
            "['ObjectExpr',{}]," +
            "['BlockStmt',{}]]");
    // wrong number of children
    testNotEnoughChildrenValidation("" +
        "['ForInStmt',{}," +
            "['IdExpr',{'name':'x'}]," +
            "['ObjectExpr',{}]],",
        TagType.ForInStmt, 3, 2);
    testTooManyChildrenValidation("" +
        "['ForInStmt',{}," +
            "['IdExpr',{'name':'x'}]," +
            "['ObjectExpr',{}]," +
            "['BlockStmt',{}]," +
            "['BlockStmt',{}]]",
        TagType.ForInStmt, 3, 4);
  }

  public void testForStmtValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['ForStmt',{}," +
            "['AssignExpr',{'op':'='}," +
                "['IdExpr',{'name':'i'}]," +
                "['LiteralExpr',{'type':'number','value':0}]]," +
            "['BinaryExpr',{'op':'<'}," +
                "['IdExpr',{'name':'i'}]," +
                "['IdExpr',{'name':'n'}]]," +
            "['CountExpr',{'isPrefix':true,'op':'++'}," +
                "['IdExpr',{'name':'i'}]]," +
            "['BlockStmt',{}]]");
    // wrong number of children
    testNotEnoughChildrenValidation("" +
        "['ForStmt',{}," +
            "['BinaryExpr',{'op':'<'}," +
                "['IdExpr',{'name':'i'}]," +
                "['IdExpr',{'name':'n'}]]," +
            "['CountExpr',{'isPrefix':true,'op':'++'}," +
                "['IdExpr',{'name':'i'}]]," +
            "['BlockStmt',{}]]",
        TagType.ForStmt, 4, 3);
    testTooManyChildrenValidation("" +
        "['ForStmt',{}," +
            "['AssignExpr',{'op':'='}," +
                "['IdExpr',{'name':'i'}]," +
                "['LiteralExpr',{'type':'number','value':0}]]," +
            "['BinaryExpr',{'op':'<'}," +
                "['IdExpr',{'name':'i'}]," +
                "['IdExpr',{'name':'n'}]]," +
            "['CountExpr',{'isPrefix':true,'op':'++'}," +
                "['IdExpr',{'name':'i'}]]," +
            "['BlockStmt',{}]," +
            "['BlockStmt',{}]]",
        TagType.ForStmt, 4, 5);
  }

  public void testFunctionDeclValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['FunctionDecl',{}," +
            "['IdPatt',{'name':'f'}]," +
            "['ParamDecl',{}]]");
    testValidation("" +
        "['FunctionDecl',{}," +
            "['IdPatt',{'name':'f'}]," +
            "['ParamDecl',{}]," +
            "['IdExpr',{'name':'foo'}]]");
    // wrong number of children
    testNotEnoughChildrenValidation("" +
        "['FunctionDecl',{}," +
            "['IdPatt',{'name':'f'}]]",
        TagType.FunctionDecl, 2, 1);
    // function name not specified
    testWrongChildTypeValidation("" +
        "['FunctionDecl',{}," +
            "['Empty', {}]," +
            "['ParamDecl',{}]]",
        TagType.FunctionDecl, TagType.IdPatt, TagType.Empty, 0);
    // list of formal arguments not specified
    testWrongChildTypeValidation("" +
        "['FunctionDecl',{}," +
            "['IdPatt',{'name':'f'}]," +
            "['Empty',{}]]",
        TagType.FunctionDecl, TagType.ParamDecl, TagType.Empty, 1);
  }

  public void testFunctionExprValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['FunctionExpr',{}," +
            "['IdPatt',{'name':'f'}]," +
            "['ParamDecl',{}]]");
    testValidation("" +
        "['FunctionExpr',{}," +
            "['IdPatt',{'name':'f'}]," +
            "['ParamDecl',{}]," +
            "['IdExpr',{'name':'foo'}]]");
    testValidation("" +
        "['FunctionExpr',{}," +
            "['Empty', {}]," +
            "['ParamDecl',{}]]");
  }

  public void testIdExprValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['IdExpr',{'name':'x'}]");
    // wrong number of children
    testTooManyChildrenValidation("" +
        "['IdExpr',{'name':'x'}," +
            "['BlockStmt',{}]]",
        TagType.IdExpr, 0, 1);
    // missing name argument
    testMissingArgument("" +
        "['IdExpr', {}]",
        TagAttr.NAME, TagType.IdExpr);
  }

  public void testIdPattValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['IdPatt',{'name':'x'}]");
    // wrong number of children
    testTooManyChildrenValidation("" +
        "['IdPatt',{'name':'x'}," +
            "['BlockStmt',{}]]",
        TagType.IdPatt, 0, 1);
    // missing name argument
    testMissingArgument("" +
        "['IdPatt', {}]",
        TagAttr.NAME, TagType.IdPatt);
  }

  public void testIfStmtValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['IfStmt',{}," +
            "['LiteralExpr',{'type':'boolean','value':true}]," +
            "['BlockStmt',{}]," +
            "['EmptyStmt',{}]]");
    testValidation("" +
        "['IfStmt',{}," +
            "['LiteralExpr',{'type':'boolean','value':true}]," +
            "['BlockStmt',{}]," +
            "['BlockStmt',{}]]");
    // wrong number of children
    testNotEnoughChildrenValidation("" +
        "['IfStmt',{}," +
            "['LiteralExpr',{'type':'boolean','value':true}]," +
            "['BlockStmt',{}]]",
        TagType.IfStmt, 3, 2);
  }

  public void testInvokeExprValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['InvokeExpr',{'op':'.'}," +
            "['IdExpr',{'name':'x'}]," +
            "['LiteralExpr',{'type':'string','value':'foo'}]]");
    // wrong number of children
    testNotEnoughChildrenValidation("" +
        "['InvokeExpr',{'op':'[]'}," +
            "['IdExpr',{'name':'x'}]]",
        TagType.InvokeExpr, 2, 1);
    // missing attribute
    testMissingArgument("" +
        "['InvokeExpr',{}," +
            "['IdExpr',{'name':'x'}]," +
            "['LiteralExpr',{'type':'string','value':'foo'}]]",
        TagAttr.OP, TagType.InvokeExpr);
  }

  public void testJmpStmtValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['BreakStmt',{}]");
    testValidation("" +
        "['BreakStmt',{'label':'s'}]");
    testValidation("" +
        "['ContinueStmt',{}]");
    testValidation("" +
        "['ContinueStmt',{'label':'s'}]");
    // wrong number of children
    testTooManyChildrenValidation("" +
        "['BreakStmt',{}," +
            "['IdExpr',{'name':'s'}]]",
        TagType.BreakStmt, 0, 1);
    testTooManyChildrenValidation("" +
        "['ContinueStmt',{}," +
            "['IdExpr',{'name':'s'}]]",
        TagType.ContinueStmt, 0, 1);
  }

  public void testLabelledStmtValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['LabelledStmt',{'label':'s'}," +
            "['IdExpr',{'name':'x'}]]");
    // wrong number of children
    testNotEnoughChildrenValidation("" +
        "['LabelledStmt',{'label':'s'}]",
        TagType.LabelledStmt, 1, 0);
    testTooManyChildrenValidation("" +
        "['LabelledStmt',{'label':'s'}," +
            "['IdExpr',{'name':'x'}]," +
            "['IdExpr',{'name':'y'}]]",
        TagType.LabelledStmt, 1, 2);
    // missing attribute
    testMissingArgument("" +
        "['LabelledStmt',{}," +
            "['IdExpr',{'name':'x'}]]",
        TagAttr.LABEL, TagType.LabelledStmt);
  }

  public void testLiteralExprValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['LiteralExpr',{'type':'string','value':'x'}]");
    testValidation("" +
        "['LiteralExpr',{'type':'boolean','value':'true'}]");
    testValidation("" +
        "['LiteralExpr',{'type':'number','value':'1.0'}]");
    // wrong number of children
    testTooManyChildrenValidation("" +
        "['LiteralExpr',{'type':'number','value':'1.0'}," +
            "['BlockStmt',{}]]",
        TagType.LiteralExpr, 0, 1);
    // missing attribute
    testMissingArgument("" +
        "['LiteralExpr',{'type':'string'}]",
        TagAttr.VALUE, TagType.LiteralExpr);
    testMissingArgument("" +
        "['LiteralExpr',{'value':'1.0'}]",
        TagAttr.TYPE, TagType.LiteralExpr);
  }

  public void testLogicalExprValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['LogicalAndExpr',{}," +
            "['IdExpr',{'name':'a'}]," +
            "['IdExpr',{'name':'b'}]]");
    testValidation("" +
        "['LogicalOrExpr',{}," +
            "['IdExpr',{'name':'a'}]," +
            "['IdExpr',{'name':'b'}]]");
    // wrong number of children
    testTooManyChildrenValidation("" +
        "['LogicalAndExpr',{}," +
            "['IdExpr',{'name':'a'}]," +
            "['IdExpr',{'name':'b'}]," +
            "['IdExpr',{'name':'c'}]]",
            TagType.LogicalAndExpr, 2, 3);
    testNotEnoughChildrenValidation("" +
        "['LogicalAndExpr',{}," +
            "['IdExpr',{'name':'a'}]]",
        TagType.LogicalAndExpr, 2, 1);
  }

  public void testNewExprValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['NewExpr',{}," +
            "['IdExpr',{'name':'A'}]," +
            "['IdExpr',{'name':'x'}]]");
    // wrong number of children
    testNotEnoughChildrenValidation("" +
        "['NewExpr',{}]",
        TagType.NewExpr, 1, 0);
  }

  public void testObjectExprValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['ObjectExpr',{}]");
    testValidation("" +
        "['ObjectExpr',{}," +
            "['DataProp',{'name':'x'}," +
                "['LiteralExpr',{'type':'number','value':1}]]," +
            "['DataProp',{'name':'y'}," +
                "['LiteralExpr',{'type':'number','value':2}]]]");
    // wrong types of children
    TagType[] tags = 
        {TagType.DataProp, TagType.GetterProp, TagType.SetterProp };
    testWrongChildTypeValidation("" +
        "['ObjectExpr',{}," +
            "['DataProp',{'name':'x'}," +
                "['LiteralExpr',{'type':'number','value':1}]]," +
            "['IdExpr',{'name':'y'}]]",
        TagType.ObjectExpr, tags, TagType.IdExpr, 1);
  }

  public void testParamDeclValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['ParamDecl',{}]");
    testValidation("" +
        "['ParamDecl',{}," +
            "['IdPatt',{'name':'x'}]," +
            "['IdPatt',{'name':'y'}]]");
    // wrong types of children
    testWrongChildTypeValidation("" +
        "['ParamDecl',{}," +
            "['IdPatt',{'name':'x'}]," +
            "['IdExpr',{'name':'y'}]]",
        TagType.ParamDecl, TagType.IdPatt, TagType.IdExpr, 1);
  }

  public void testRegExpExprValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['RegExpExpr',{'body':'abc','flags':''}]");
    testValidation("" +
        "['RegExpExpr',{'body':'abc','flags':'g'}]");
    // wrong number of children
    testTooManyChildrenValidation("" +
        "['RegExpExpr',{'body':'abc','flags':'g'}," +
            "['IdExpr',{'name':'a'}]]",
        TagType.RegExpExpr, 0, 1);
    // missing attribute
    testMissingArgument("" +
        "['RegExpExpr',{'body':'abc'}]",
        TagAttr.FLAGS, TagType.RegExpExpr);
    testMissingArgument("" +
        "['RegExpExpr',{'flags':'g'}]",
        TagAttr.BODY, TagType.RegExpExpr);
  }

  public void testReturnStmtValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['ReturnStmt',{}]");
    testValidation("" +
        "['ReturnStmt',{}," +
            "['LiteralExpr',{'type':'number','value':1}]]");
    // wrong number of children
    testTooManyChildrenValidation("" +
        "['ReturnStmt',{}," +
            "['IdExpr',{'name':'a'}]," +
            "['IdExpr',{'name':'b'}]]",
        TagType.ReturnStmt, 1, 2);
  }

  public void testSwitchStmtValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['SwitchStmt',{}," +
            "['IdExpr',{'name':'x'}]," +
            "['Case',{}," +
                "['LiteralExpr',{'type':'number','value':1}]," +
                "['CallExpr',{}," +
                    "['IdExpr',{'name':'foo'}]]]," +
            "['DefaultCase',{}," +
                "['CallExpr',{}," +
                    "['IdExpr',{'name':'bar'}]]]]");
    testValidation("" +
        "['SwitchStmt',{}," +
            "['IdExpr',{'name':'x'}]]");
    // wrong number of children
    testNotEnoughChildrenValidation("" +
        "['SwitchStmt',{}]",
        TagType.SwitchStmt, 1, 0);
    // wrong types of children
    testWrongChildTypeValidation("" +
        "['SwitchStmt',{}," +
            "['IdExpr',{'name':'x'}]," +
            "['AssignExpr',{'op': '='}," +
                "['LiteralExpr',{'type':'number','value':1}]," +
                "['CallExpr',{}," +
                    "['IdExpr',{'name':'foo'}]]]," +
            "['DefaultCase',{}," +
                "['CallExpr',{}," +
                    "['IdExpr',{'name':'bar'}]]]]",
        TagType.SwitchStmt,
        new TagType[] { TagType.Case, TagType.DefaultCase },
        TagType.AssignExpr, 1);
    testWrongChildTypeValidation("" +
        "['SwitchStmt',{}," +
            "['IdExpr',{'name':'x'}]," +
            "['DefaultCase',{}," +
                "['CallExpr',{}," +
                    "['IdExpr',{'name':'foo'}]]]," +
            "['DefaultCase',{}," +
                "['CallExpr',{}," +
                    "['IdExpr',{'name':'bar'}]]]]",
        TagType.SwitchStmt, TagType.Case, TagType.DefaultCase, 2);
  }

  public void testThisExprValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['ThisExpr',{}]");
    // wrong number of children
    testTooManyChildrenValidation("" +
        "['ThisExpr',{}," +
            "['IdExpr',{'name':'a'}]]",
        TagType.ThisExpr, 0, 1);
  }

  public void testThrowStmtValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['ThrowStmt',{}," +
            "['IdExpr',{'name':'e'}]]");
    // wrong number of children
    testTooManyChildrenValidation("" +
        "['ThrowStmt',{}," +
            "['IdExpr',{'name':'a'}]," +
            "['IdExpr',{'name':'b'}]]",
        TagType.ThrowStmt, 1, 2);
  }

  public void testTryStmtValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['TryStmt',{}," +
            "['BlockStmt',{}]," +
            "['CatchClause',{}," +
                "['IdPatt',{'name':'e'}]," +
                "['BlockStmt',{}]]]");
    testValidation("" +
        "['TryStmt',{}," +
            "['BlockStmt',{}]," +
            "['CatchClause',{}," +
                "['IdPatt',{'name':'e'}]," +
                "['BlockStmt',{}]]," +
            "['BlockStmt',{}]]");
    // wrong number of children
    testNotEnoughChildrenValidation("" +
        "['TryStmt',{}," +
            "['CatchClause',{}," +
                "['IdPatt',{'name':'e'}]," +
                "['BlockStmt',{}]]]",
        TagType.TryStmt, 2, 1);
    testTooManyChildrenValidation("" +
        "['TryStmt',{}," +
            "['BlockStmt',{}]," +
            "['CatchClause',{}," +
                "['IdPatt',{'name':'e'}]," +
                "['BlockStmt',{}]]," +
            "['BlockStmt',{}]," +
            "['BlockStmt',{}]]",
        TagType.TryStmt, 3, 4);
    // wrong type of children
    testWrongChildTypeValidation("" +
        "['TryStmt',{}," +
            "['BlockStmt',{}]," +
            "['BlockStmt',{}," +
                "['IdPatt',{'name':'e'}]," +
                "['BlockStmt',{}]]," +
            "['BlockStmt',{}]]",
        TagType.TryStmt,
        new TagType[] { TagType.CatchClause, TagType.Empty },
        TagType.BlockStmt, 1);
    testWrongChildTypeValidation("" +
        "['TryStmt',{}," +
            "['BlockStmt',{}]," +
            "['CatchClause',{}," +
                "['IdPatt',{'name':'e'}]," +
                "['BlockStmt',{}]]," +
            "['IdExpr',{'name': 'x'}]]",
        TagType.TryStmt, TagType.BlockStmt, TagType.IdExpr, 2);
  }

  public void testUnaryExprValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['UnaryExpr',{'op':'-'}," +
            "['IdExpr',{'name':'x'}]]");
    testValidation("" +
        "['UnaryExpr',{'op':'!'}," +
            "['CallExpr',{}," +
                "['IdExpr',{'name':'f'}]," +
                    "['IdExpr',{'name':'x'}]]]");
    // wrong number of children
    testNotEnoughChildrenValidation("" +
        "['UnaryExpr',{'op':'-'}]",
        TagType.UnaryExpr, 1, 0);
    testTooManyChildrenValidation("" +
        "['UnaryExpr',{'op':'+'}," +
            "['IdExpr',{'name':'x'}]," +
            "['IdExpr',{'name':'y'}]]",
        TagType.UnaryExpr, 1, 2);
    // missing attribute
    testMissingArgument("" +
        "['UnaryExpr',{}," +
            "['IdExpr',{'name':'x'}]]",
        TagAttr.OP, TagType.UnaryExpr);
  }

  public void testVarDeclValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['VarDecl',{}," +
            "['IdPatt',{'name':'x'}]]");
    testValidation("" +
        "['VarDecl',{}," +
            "['InitPatt',{}," +
                "['IdPatt',{'name':'x'}]," +
                "['LiteralExpr',{'type':'number','value':0}]]]");
    testValidation("" +
        "['VarDecl',{}," +
            "['InitPatt',{}," +
                "['IdPatt',{'name':'x'}]," +
                "['LiteralExpr',{'type':'number','value':0}]]," +
            "['IdPatt',{'name':'y'}]]");
    // wrong number of children
    testNotEnoughChildrenValidation("" +
        "['VarDecl',{}]",
        TagType.VarDecl, 1, 0);
    // wrong type of children
    testWrongChildTypeValidation("" +
        "['VarDecl',{}," +
            "['InitPatt',{}," +
                "['IdPatt',{'name':'x'}]," +
                "['LiteralExpr',{'type':'number','value':0}]]," +
            "['IdExpr',{'name':'y'}]," +
            "['IdPatt',{'name':'z'}]]",
        TagType.VarDecl,
        new TagType[] { TagType.InitPatt, TagType.IdPatt },
        TagType.IdExpr, 1);
  }

  public void testWhileStmtValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['WhileStmt',{}," +
            "['LiteralExpr',{'type':'boolean','value':true}]," +
            "['BlockStmt',{}]]");
    testValidation("" +
        "['WhileStmt',{}," +
            "['LiteralExpr',{'type':'boolean','value':true}]," +
            "['IdExpr',{'name':'x'}]]");
    // wrong number of children
    testNotEnoughChildrenValidation("" +
        "['WhileStmt',{}," +
            "['BlockStmt',{}]]",
        TagType.WhileStmt, 2, 1);
    testTooManyChildrenValidation("" +
        "['WhileStmt',{}," +
            "['LiteralExpr',{'type':'boolean','value':true}]," +
            "['IdExpr',{'name':'x'}]," +
            "['IdExpr',{'name':'y'}]]",
        TagType.WhileStmt, 2, 3);
  }

  public void testWithStmtValidation() throws Exception {
    // correct statement
    testValidation("" +
        "['WithStmt',{}," +
            "['IdExpr',{'name':'x'}]," +
            "['BlockStmt',{}]]");
    // wrong number of children
    testNotEnoughChildrenValidation("" +
        "['WithStmt',{}," +
            "['BlockStmt',{}]]",
        TagType.WithStmt, 2, 1);
    testTooManyChildrenValidation("" +
        "['WithStmt',{}," +
            "['IdExpr',{'name':'A'}]," +
            "['IdExpr',{'name':'x'}]," +
            "['IdExpr',{'name':'y'}]]",
        TagType.WithStmt, 2, 3);
  }
}
