/*
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
 *   John Lenz
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

package com.google.javascript.rhino;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** @author johnlenz@google.com (John Lenz) */
@RunWith(JUnit4.class)
public class IRTest {
  private static final Joiner LINE_JOINER = Joiner.on('\n');

  @Test
  public void testEmpty() {
    testIR(IR.empty(), "EMPTY\n");
  }

  @Test
  public void testFunction() {
    testIR(
        IR.function(IR.name("hi"), IR.paramList(), IR.block()),
        """
        FUNCTION hi
            NAME hi
            PARAM_LIST
            BLOCK
        """);
  }

  @Test
  public void testArrowFunction() {
    testIR(
        IR.arrowFunction(IR.name("hi"), IR.paramList(), IR.block()),
        """
        FUNCTION hi [arrow_fn: 1]
            NAME hi
            PARAM_LIST
            BLOCK
        """);
  }

  @Test
  public void testParamList() {
    testIR(IR.paramList(),
        "PARAM_LIST\n");

    testIR(
        IR.paramList(IR.name("a"), IR.name("b")),
        """
        PARAM_LIST
            NAME a
            NAME b
        """);
  }

  @Test
  public void testBlock() {
    testIR(IR.block(),
        "BLOCK\n");

    testIR(
        IR.block(IR.empty(), IR.empty()),
        """
        BLOCK
            EMPTY
            EMPTY
        """);

    testIR(
        IR.block(ImmutableList.of(IR.empty(), IR.empty())),
        """
        BLOCK
            EMPTY
            EMPTY
        """);
  }

  @Test
  public void testScript() {
    testIR(IR.script(),
        "SCRIPT\n");

    testIR(
        IR.script(IR.empty(), IR.empty()),
        """
        SCRIPT
            EMPTY
            EMPTY
        """);

    testIR(
        IR.script(ImmutableList.of(IR.empty(), IR.empty())),
        """
        SCRIPT
            EMPTY
            EMPTY
        """);
  }

  @Test
  public void testScriptThrows() {
    boolean caught = false;
    try {
      IR.script(IR.returnNode());
    } catch(IllegalStateException e) {
      caught = true;
    }
    assertWithMessage("expected exception was not seen").that(caught).isTrue();
  }

  @Test
  public void testVar() {
    testIR(
        IR.var(IR.name("a")),
        """
        VAR
            NAME a
        """);

    testIR(
        IR.var(IR.name("a"), IR.trueNode()),
        """
        VAR
            NAME a
                TRUE
        """);
  }

  @Test
  public void testReturn() {
    testIR(IR.returnNode(),
        "RETURN\n");

    testIR(
        IR.returnNode(IR.name("a")),
        """
        RETURN
            NAME a
        """);
  }

  @Test
  public void testThrow() {
    testIR(
        IR.throwNode(IR.name("a")),
        """
        THROW
            NAME a
        """);
  }

  @Test
  public void testExprResult() {
    testIR(
        IR.exprResult(IR.name("a")),
        """
        EXPR_RESULT
            NAME a
        """);
  }

  @Test
  public void testIf() {
    testIR(
        IR.ifNode(IR.name("a"), IR.block()),
        """
        IF
            NAME a
            BLOCK
        """);

    testIR(
        IR.ifNode(IR.name("a"), IR.block(), IR.block()),
        """
        IF
            NAME a
            BLOCK
            BLOCK
        """);
  }

  @Test
  public void testIssue727_1() {
    testIR(
        IR.tryFinally(IR.block(), IR.block()),
        """
        TRY
            BLOCK
            BLOCK
            BLOCK
        """);
  }

  @Test
  public void testIssue727_2() {
    testIR(
        IR.tryCatch(IR.block(), IR.catchNode(IR.name("e"), IR.block())),
        """
        TRY
            BLOCK
            BLOCK
                CATCH
                    NAME e
                    BLOCK
        """);
  }

  @Test
  public void testIssue727_3() {
    testIR(
        IR.tryCatchFinally(IR.block(), IR.catchNode(IR.name("e"), IR.block()), IR.block()),
        """
        TRY
            BLOCK
            BLOCK
                CATCH
                    NAME e
                    BLOCK
            BLOCK
        """);
  }

  @Test
  public void testAdd() {
    testIR(
        IR.add(IR.cast(IR.number(1), null), IR.number(2)),
        """
        ADD
            CAST
                NUMBER 1.0
            NUMBER 2.0
        """);
  }

  @Test
  public void testVarWithTemplateLitOnRHS() {
    testIR(
        IR.var(IR.name("x"), new Node(Token.TEMPLATELIT, IR.string(""))),
        LINE_JOINER.join(
            "VAR", //
            "    NAME x",
            "        TEMPLATELIT",
            "            STRINGLIT ",
            ""));

    testIR(
        IR.var(IR.name("x"), new Node(Token.TAGGED_TEMPLATELIT, IR.name("y"), IR.string(""))),
        LINE_JOINER.join(
            "VAR", //
            "    NAME x",
            "        TAGGED_TEMPLATELIT",
            "            NAME y",
            "            STRINGLIT ",
            ""));
  }

  private void testIR(Node node, String expectedStructure) {
    assertThat(node.toStringTree()).isEqualTo(expectedStructure);
  }
}
