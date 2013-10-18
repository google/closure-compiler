/*
 * Copyright 2013 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.fuzzing;

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.CodePrinter;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 * @author zplin@google.com (Zhongpeng Lin)
 */
public class Fuzzer {
  private static final int LARGEST_NUMBER = 1000;
  protected final Random random;


  public Fuzzer(Random random) {
    this.random = random;
  }

  Node generateExpression(int budget) {
    int rand;
    Preconditions.checkArgument(budget >= 1);
    if (budget < 2) {
      rand = 0;
    } else if (budget < 3) {
      rand = random.nextInt(4);
    } else if (budget < 4) {
      rand = random.nextInt(5);
    } else {
      rand = random.nextInt(6);
    }
    switch (rand) {
      case 0: return generateLiteral(budget);
      case 1: return generateCallableExpression(budget);
      case 2: return generateFunctionCall(budget);
      case 3: return generateUnaryExpression(budget);
      case 4: return generateBinaryExpression(budget);
      case 5: return generateTernaryExpression(budget);
      // TODO(zplin) function expression
      default: return null;

    }
  }

  Node generateLiteral(int budget) {
    int rand;
    if (budget > 1) {
      rand = random.nextInt(7);
    } else if (budget == 1) {
      rand = random.nextInt(6);
    } else {
      rand = -1;
    }
    switch (rand) {
      case 0: return generateNullLiteral(budget);
      case 1: return generateBooleanLiteral(budget);
      case 2: return generateNumericLiteral(budget);
      case 3: return generateStringLiteral(budget);
      case 4: return generateArrayLiteral(budget);
      case 5: return generateObjectLiteral(budget);
      case 6: return generateRegularExpressionLiteral(budget);
      default: return null;
    }
  }

  Node generateNumericLiteral(int budget) {
    Preconditions.checkArgument(budget >= 1);
    return Node.newNumber(random.nextInt(LARGEST_NUMBER));
  }

  Node generateStringLiteral(int budget) {
    Preconditions.checkArgument(budget >= 1);
    return Node.newString(StringGenerator.getString(random));
  }

  Node generateIdentifier(int budget) {
    Preconditions.checkArgument(budget >= 1);
    return Node.newString(Token.NAME, StringGenerator.getString(random));
  }

  Node generateNullLiteral(int budget) {
    Preconditions.checkArgument(budget >= 1);
    return new Node(Token.NULL);
  }

  Node generateBooleanLiteral(int budget) {
    Preconditions.checkArgument(budget >= 1);
    return random.nextInt(2) == 0 ?
          new Node(Token.FALSE) : new Node(Token.TRUE);
  }

  Node generateRegularExpressionLiteral(int budget) {
    Preconditions.checkArgument(budget >= 2);
    if (budget < 3) {
      Node[] children = {Node.newString("abc")};
      Node node = new Node(Token.REGEXP, children);
      return node;
    } else {
      Node[] children = {Node.newString("abc"), Node.newString("g")};
      Node node = new Node(Token.REGEXP, children);
      return node;
    }
  }

  Node generateObjectLiteral(int budget) {
    Preconditions.checkArgument(budget >= 1);
    Node objectLit = new Node(Token.OBJECTLIT);
    int remainingBudget = budget - 1;
    // an object property needs at least two nodes
    int objectLength = remainingBudget < 2 ?
        0 : random.nextInt(remainingBudget / 2 + 1);
    if (objectLength == 0) {
      return objectLit;
    }
    remainingBudget -= objectLength;
    int[] propertyBudgets = distribute(remainingBudget, objectLength);
    for (int i = 0; i < objectLength; i++) {
      Node key = generatePropertyName();

      Node value = generateExpression(propertyBudgets[i]);
      key.addChildrenToFront(value);
      objectLit.addChildToBack(key);
    }
    return objectLit;
  }

  Node generateArrayLiteral(int budget) {
    Preconditions.checkArgument(budget >= 1);
    Node node = new Node(Token.ARRAYLIT);
    int arraySize = random.nextInt(budget);
    if (arraySize > 0) {
      int[] itemBudgets = distribute(budget - 1, arraySize);
      for (int i = 0; i < arraySize; i++) {
        Node item = generateExpression(itemBudgets[i]);
        node.addChildToBack(item);
      }
    }
    return node;
  }

  Node generateAssignableExpression(int budget) {
    if (budget < 3) {
      return generateIdentifier(budget);
    } else {
      switch (random.nextInt(3)) {
        case 0: return generateIdentifier(budget);
        case 1: return generateGetProp(budget);
        case 2: return generateGetElem(budget);
        default: return null;
      }
    }
  }

  Node generateGetProp(int budget) {
    Preconditions.checkArgument(budget >= 3);
    // GetProp itself is size one, right expression is size one
    Node left = generateExpression(budget - 2);
    Node right = generateStringLiteral(1);
    return new Node(Token.GETPROP, left, right);
  }

  Node generateGetElem(int budget) {
    Preconditions.checkArgument(budget >= 3);
    int[] subBudgets = distribute(budget - 1, 2);
    Node left = generateExpression(subBudgets[0]);
    Node right = generateExpression(subBudgets[1]);
    return new Node(Token.GETELEM, left, right);
  }

  Node generateFunctionCall(int budget) {
    return generateFunctionCall(budget, random.nextInt(2) == 0);
  }

  Node generateFunctionCall(int budget, boolean isNew) {
    // a function call at least needs two nodes: one for the root, one for the
    // function name
    Preconditions.checkArgument(budget >= 2);
    int numArgs = random.nextInt(budget - 1);
    int[] subBudgets = distribute(budget - 1, numArgs + 1);

    Node target = generateCallableExpression(subBudgets[0]);
    Node node;
    if (isNew) {
      node = new Node(Token.NEW, target);
    } else {
      node = new Node(Token.CALL, target);
    }
    for (int i = 0; i < numArgs; i++) {
      Node arg = generateExpression(subBudgets[i + 1]);
      node.addChildToBack(arg);
    }
    return node;
  }

  Node generateCallableExpression(int budget) {
    // TODO(zplin): turn on FunctionExpressionFuzzer once it's implemented
    return generateAssignableExpression(budget);
  }

  Node generateUnaryExpression(int budget) {
    Preconditions.checkArgument(budget >= 2);
    Node node = null;
    Node target;
    switch (random.nextInt(11)) {
      case 0:
        target = generateAssignableExpression(budget - 1);
        node = new Node(Token.INC, target);
        node.putBooleanProp(Node.INCRDECR_PROP, true);
        break;
      case 1:
        target = generateAssignableExpression(budget - 1);
        node = new Node(Token.DEC, target);
        node.putBooleanProp(Node.INCRDECR_PROP, true);
        break;
      case 2:
        target = generateAssignableExpression(budget - 1);
        node = new Node(Token.DELPROP, target);
        break;
      case 3:
        target = generateExpression(budget - 1);
        node = new Node(Token.VOID, target);
        break;
      case 4:
        target = generateExpression(budget - 1);
        node = new Node(Token.TYPEOF, target);
        break;
      case 5:
        target = generateAssignableExpression(budget - 1);
        node = new Node(Token.INC, target);
        break;
      case 6:
        target = generateAssignableExpression(budget - 1);
        node = new Node(Token.DEC, target);
        break;
      case 7:
        target = generateExpression(budget - 1);
        node = new Node(Token.POS, target);
        break;
      case 8:
        target = generateExpression(budget - 1);
        node = new Node(Token.NEG, target);
        break;
      case 9:
        target = generateExpression(budget - 1);
        node = new Node(Token.BITNOT, target);
        break;
      case 10:
        target = generateExpression(budget - 1);
        node = new Node(Token.NOT, target);
        break;
    }
    return node;
  }

  Node generateBinaryExpression(int budget) {
    Preconditions.checkArgument(budget >= 3);
    int[] subBudgets = distribute(budget - 1, 2);
    List<Integer> operators = Arrays.asList(Token.MUL, Token.DIV, Token.MOD, Token.ADD, Token.SUB,
        Token.LSH, Token.RSH, Token.URSH,
        Token.LT, Token.GT, Token.LE, Token.GE, Token.INSTANCEOF, Token.IN,
        Token.EQ, Token.NE, Token.SHEQ, Token.SHNE,
        Token.BITAND, Token.BITXOR, Token.BITOR, Token.AND, Token.OR,
        Token.ASSIGN, Token.ASSIGN_MUL, Token.ASSIGN_DIV, Token.ASSIGN_MOD,
        Token.ASSIGN_ADD, Token.ASSIGN_SUB, Token.ASSIGN_LSH, Token.ASSIGN_RSH,
        Token.ASSIGN_URSH, Token.ASSIGN_BITAND, Token.ASSIGN_BITXOR,
        Token.ASSIGN_BITOR);
    int index = random.nextInt(operators.size());
    Node left, right;
    if (index < operators.indexOf(Token.ASSIGN)) {
      left = generateExpression(subBudgets[0]);
    } else {
      // Assignments
      left = generateAssignableExpression(subBudgets[0]);
    }
    right = generateExpression(subBudgets[1]);
    return new Node(operators.get(index), left, right);
  }

  Node generateTernaryExpression(int budget) {
    Preconditions.checkArgument(budget >= 4);
    int[] subBudgets = distribute(budget - 1, 3);
    Node condition = generateExpression(subBudgets[0]),
        choice1 = generateExpression(subBudgets[1]),
        choice2 = generateExpression(subBudgets[2]);
    return new Node(Token.HOOK, condition, choice1, choice2);
  }

  private Node generatePropertyName() {
    String name;
    if (random.nextInt(2) == 0) {
      name = StringGenerator.getString(random);
    } else {
      name = String.valueOf(random.nextInt(LARGEST_NUMBER));
    }
    Node node = Node.newString(Token.STRING_KEY, name);
    return node;
  }

  public static String getPrettyCode(Node root) {
    CodePrinter.Builder builder = new CodePrinter.Builder(root);
    builder.setPrettyPrint(true);
    builder.setLineBreak(true);
    return builder.build();
  }

  /**
   * Divide budget randomly into n shares, guarantee that each share has at
   * least one.
   */
  private int[] distribute(int budget, int n) {
    int[] subBudgets = new int[n];
    for (int i = 0; i < n; i++) {
      subBudgets[i] = 1;
    }
    budget -= n;
    while (budget > 0) {
      subBudgets[random.nextInt(n)]++;
      budget--;
    }
    return subBudgets;
  }
}
