/*
 * Copyright 2020 The Closure Compiler Authors.
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

package com.google.javascript.jscomp;

import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link AstManipulations} */
@RunWith(JUnit4.class)
public final class AstManipulationsTest {

  @Test
  public void fuseExpressions_fusesTwoNumbers() {
    Node one = IR.number(1);
    Node two = IR.number(2);

    assertNode(AstManipulations.fuseExpressions(one, two))
        .isEqualTo(IR.comma(one.cloneNode(), two.cloneNode()));
  }

  @Test
  public void fuseExpressions_dropsEmptySecondExpression() {
    Node one = IR.number(1);
    Node empty = IR.empty();

    assertNode(AstManipulations.fuseExpressions(one, empty)).isEqualTo(one.cloneNode());
  }

  @Test
  public void fuseExpressions_keepsEmptyFirstExpression() {
    Node empty = IR.empty();
    Node one = IR.number(1);

    // Note: it should be fine to return one instead of this comma
    assertNode(AstManipulations.fuseExpressions(empty, one))
        .isEqualTo(new Node(Token.COMMA, empty.cloneNode(), one.cloneNode()));
  }

  @Test
  public void fuseExpressions_fusesNumberIntoComma() {
    Node zero = IR.number(0);
    Node one = IR.number(1);
    Node zeroCommaOne = IR.comma(zero, one);
    Node two = IR.number(2);

    assertNode(AstManipulations.fuseExpressions(zeroCommaOne, two))
        .isEqualTo(IR.comma(zeroCommaOne.cloneTree(), two.cloneNode()));
  }

  @Test
  public void fuseExpressions_fusesCommaIntoNumber() {
    Node zero = IR.number(0);
    Node one = IR.number(1);
    Node two = IR.number(2);
    Node oneCommaTwo = IR.comma(one, two);

    // Utility keeps commas in the left branch of the comma tree, i.e.
    //  COMMA
    //    COMMA
    //      NUMBER 0
    //      NUMBER 1
    //    NUMBER 2
    // instead of this:
    //  COMMA
    //    NUMBER 0
    //    COMMA
    //      NUMBER 1
    //      NUMBER 2
    // to make unit-testing passes that use this utility easier. Writing 'expected' JS code
    // for the latter tree requires more parentheses.
    assertNode(AstManipulations.fuseExpressions(zero, oneCommaTwo))
        .isEqualTo(IR.comma(IR.comma(zero.cloneNode(), one.cloneNode()), two.cloneNode()));
  }
}
