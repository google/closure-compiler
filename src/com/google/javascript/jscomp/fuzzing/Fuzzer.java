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
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.javascript.jscomp.CodePrinter;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Stack;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 * @author zplin@google.com (Zhongpeng Lin)
 */
public class Fuzzer {
  // the largest number for random number literals
  private static final int LARGEST_NUMBER = 1000;
  protected final Random random;
  private int loopNesting = 0;
  private int switchNesting = 0;
  Stack<String> currentLabels = new Stack<String>();
  SymbolTable symbolTable;
  private int counter = 0;

  public Fuzzer(Random random) {
    this.random = random;
    symbolTable = new SymbolTable(random);
    symbolTable.addScope();
  }

  Node generateExpression(int budget) {
    Preconditions.checkArgument(budget >= 1);
    Map<Expression, Double> pmf = Maps.newHashMap();
    for (Expression expr : Expression.values()) {
      if (expr.minBudget <= budget) {
        if (expr == Expression.FUNCTION_CALL &&
            symbolTable.getSize() == 0 && budget < 4) {
          continue;
        } else if (expr == Expression.IDENTIFIER &&
            symbolTable.getSize() == 0) {
          continue;
        } else {
          pmf.put(expr, expr.weight);
        }
      }
    }
    DiscreteDistribution<Expression> dd =
        new DiscreteDistribution<Expression>(random, pmf);
    Expression expr = dd.nextItem();
    switch (expr) {
      case THIS: return generateThis(budget);
      case IDENTIFIER: return generateIdentifier(budget, true);
      case LITERAL: return generateLiteral(budget);
      case FUNCTION_CALL: return generateFunctionCall(budget);
      case UNARY_EXPR: return generateUnaryExpression(budget);
      case BINARY_EXPR: return generateBinaryExpression(budget);
      case FUNCTION_EXPR: return generateFunctionExpression(budget);
      case TERNARY_EXPR: return generateTernaryExpression(budget);
    }
    return null;
  }

  Node generateThis(int budget) {
    Preconditions.checkArgument(budget >= 1);
    return new Node(Token.THIS);
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

  Node generateIdentifier(int budget, boolean getExistingOne) {
    Preconditions.checkArgument(!getExistingOne || symbolTable.getSize() > 0);
    Preconditions.checkArgument(budget >= 1);
    if (getExistingOne) {
      return Node.newString(Token.NAME, symbolTable.getRandomSymbol(false));
    } else {
      String identifier;
      // allow 1/10 chance of variable shadowing
      if (symbolTable.hasNonLocals() && random.nextInt(10) < 1) {
        identifier = symbolTable.getRandomSymbol(true);
      } else {
        identifier = "x_" + nextNumber();
      }
      symbolTable.addSymbol(identifier);
      return Node.newString(Token.NAME, identifier);
    }
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
      Node[] children = {Node.newString(StringGenerator.getString(random))};
      Node node = new Node(Token.REGEXP, children);
      return node;
    } else {
      Node[] children = {Node.newString(StringGenerator.getString(random)), Node.newString("g")};
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
    int[] propertyBudgets = distribute(remainingBudget, objectLength, 1);
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
      int[] itemBudgets = distribute(budget - 1, arraySize, 1);
      for (int i = 0; i < arraySize; i++) {
        Node item = generateExpression(itemBudgets[i]);
        node.addChildToBack(item);
      }
    }
    return node;
  }

  Node generateAssignableExpression(int budget) {
    Preconditions.checkArgument(budget >= 3 || symbolTable.getSize() > 0);
    if (budget < 3) {
      return generateIdentifier(budget, true);
    } else {
      int rand;
      if (symbolTable.getSize() > 0) {
        rand = random.nextInt(3);
      } else {
        rand = random.nextInt(2);
      }
      switch (rand) {
        case 0: return generateGetProp(budget);
        case 1: return generateGetElem(budget);
        case 2: return generateIdentifier(budget, true);
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
    int[] subBudgets = distribute(budget - 1, 2, 1);
    Node left = generateExpression(subBudgets[0]);
    Node right = generateExpression(subBudgets[1]);
    return new Node(Token.GETELEM, left, right);
  }

  Node generateFunctionCall(int budget) {
    return generateFunctionCall(budget, random.nextInt(2) == 0);
  }

  Node generateFunctionCall(int budget, boolean isNew) {
    // A function call needs at least two nodes: one for the CALL node and one
    // for the callee (when the symbol table isn't empty).
    Preconditions.checkArgument((budget >= 2 && symbolTable.getSize() > 0) ||
        budget >= 4);
    int remainingBudget;
    if (symbolTable.getSize() == 0) {
      // allocate enough budget to callable expression when the symbol table is
      // empty
      remainingBudget = budget - 3;
    } else {
      remainingBudget = budget - 1;
    }
    int numArgs = random.nextInt(remainingBudget);
    int[] subBudgets = distribute(remainingBudget, numArgs + 1, 1);

    Node target = generateCallableExpression(
        subBudgets[0] + budget - 1 - remainingBudget);
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

  private Node generateCallableExpression(int budget) {
    Preconditions.checkArgument(budget >= 3 || symbolTable.getSize() > 0);
    if (budget < 3) {
      return generateAssignableExpression(budget);
    }
    if (random.nextInt(2) == 0) {
      return generateAssignableExpression(budget);
    } else {
      return generateFunctionExpression(budget);
    }
  }

  Node generateUnaryExpression(int budget) {
    Preconditions.checkArgument(budget >= 2);
    Node node = null;
    Node target;
    int rand;
    if (symbolTable.getSize() == 0 && budget < 4) {
      rand = random.nextInt(6);
    } else {
      rand = random.nextInt(11);
    }
    switch (rand) {
      case 0:
        target = generateExpression(budget - 1);
        node = new Node(Token.VOID, target);
        break;
      case 1:
        target = generateExpression(budget - 1);
        node = new Node(Token.TYPEOF, target);
        break;
      case 2:
        target = generateExpression(budget - 1);
        node = new Node(Token.POS, target);
        break;
      case 3:
        target = generateExpression(budget - 1);
        node = new Node(Token.NEG, target);
        break;
      case 4:
        target = generateExpression(budget - 1);
        node = new Node(Token.BITNOT, target);
        break;
      case 5:
        target = generateExpression(budget - 1);
        node = new Node(Token.NOT, target);
        break;
      case 6:
        target = generateAssignableExpression(budget - 1);
        node = new Node(Token.INC, target);
        break;
      case 7:
        target = generateAssignableExpression(budget - 1);
        node = new Node(Token.DEC, target);
        break;
      case 8:
        target = generateAssignableExpression(budget - 1);
        node = new Node(Token.DELPROP, target);
        break;
      case 9:
        target = generateAssignableExpression(budget - 1);
        node = new Node(Token.INC, target);
        node.putBooleanProp(Node.INCRDECR_PROP, true);
        break;
      case 10:
        target = generateAssignableExpression(budget - 1);
        node = new Node(Token.DEC, target);
        node.putBooleanProp(Node.INCRDECR_PROP, true);
        break;
    }
    return node;
  }

  Node generateBinaryExpression(int budget) {
    Preconditions.checkArgument(budget >= 3);
    List<Integer> operators = Lists.newArrayList(
        Token.MUL, Token.DIV, Token.MOD, Token.ADD, Token.SUB,
        Token.LSH, Token.RSH, Token.URSH,
        Token.LT, Token.GT, Token.LE, Token.GE, Token.INSTANCEOF, Token.IN,
        Token.EQ, Token.NE, Token.SHEQ, Token.SHNE,
        Token.BITAND, Token.BITXOR, Token.BITOR, Token.AND, Token.OR,
        Token.ASSIGN, Token.ASSIGN_MUL, Token.ASSIGN_DIV, Token.ASSIGN_MOD,
        Token.ASSIGN_ADD, Token.ASSIGN_SUB, Token.ASSIGN_LSH, Token.ASSIGN_RSH,
        Token.ASSIGN_URSH, Token.ASSIGN_BITAND, Token.ASSIGN_BITXOR,
        Token.ASSIGN_BITOR);

    int[] subBudgets = distribute(budget - 1, 2, 1);
    int index;
    int firstAssignIndex = operators.indexOf(Token.ASSIGN);
    if (subBudgets[0] < 3 && symbolTable.getSize() == 0) {
      index = random.nextInt(firstAssignIndex);
    } else {
      index = random.nextInt(operators.size());
    }
    Node left, right;
    if (index < firstAssignIndex) {
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
    int[] subBudgets = distribute(budget - 1, 3, 1);
    Node condition = generateExpression(subBudgets[0]),
        choice1 = generateExpression(subBudgets[1]),
        choice2 = generateExpression(subBudgets[2]);
    return new Node(Token.HOOK, condition, choice1, choice2);
  }

  Node generateFunctionExpression(int budget) {
    Preconditions.checkArgument(budget >= 3);
    return generateFunction(budget, true);
  }

  Node generateStatement(int budget) {
    // probability mass function (pmf) for the discrete distribution
    Map<Statement, Double> pmf = Maps.newHashMap();
    for (Statement stmt : Statement.values()) {
      if (stmt.minBudget <= budget) {
        if (stmt == Statement.RETURN && symbolTable.getNumScopes() < 2) {
          continue;
        } else if (stmt == Statement.BREAK
            && loopNesting == 0 && switchNesting == 0) {
          continue;
        } else if (stmt == Statement.CONTINUE && loopNesting == 0) {
          continue;
        } else {
          pmf.put(stmt, stmt.weight);
        }
      }
    }
    DiscreteDistribution<Statement> dd =
        new DiscreteDistribution<Statement>(random, pmf);
    Statement stmt = dd.nextItem();
    switch (stmt) {
      case BLOCK: return generateBlock(budget);
      case VAR: return generateVariableStatement(budget);
      case EMPTY: return generateEmptyStatement(budget);
      case EXPR: return generateExpressionStatement(budget);
      case IF: return generateIfStatement(budget);
      case WHILE: return generateWhile(budget);
      case DO_WHILE: return generateDoWhile(budget);
      case FOR: return generateFor(budget);
      case FOR_IN: return generateForIn(budget);
      case CONTINUE: return generateContinue(budget);
      case BREAK: return generateBreak(budget);
      case RETURN: return generateReturn(budget);
      case SWITCH: return generateSwitch(budget);
      case LABEL: return generateLabelledStatement(budget);
      case THROW: return generateThrow(budget);
      case TRY: return generateTry(budget);
      default: throw new RuntimeException("Shouldn never reach this!");
    }
  }

  Node generateBlock(int budget) {
    Preconditions.checkArgument(budget >= 1);
    Node node = new Node(Token.BLOCK);
    int numStmt = random.nextInt(budget);
    if (numStmt > 0) {
      int[] stmtBudgets = distribute(budget - 1, numStmt, 1);
      for (int b : stmtBudgets) {
        node.addChildToBack(generateStatement(b));
      }
    }
    return node;
  }

  /**
   * Only generate var statements with single child, i.e., we are NOT generating
   * var x = expr1, y = expr2, z = expr3;
   */
  Node generateVariableStatement(int budget) {
    Preconditions.checkArgument(budget >= 1);
    Node identifier = generateIdentifier(budget, false);
    Node node = new Node(Token.VAR, identifier);
    if (budget > 1) {
      Node assn = generateExpression(budget - 1);
      identifier.addChildToBack(assn);
    }
    return node;
  }

  Node generateEmptyStatement(int budget) {
    Preconditions.checkArgument(budget >= 1);
    return new Node(Token.EMPTY);
  }

  Node generateExpressionStatement(int budget) {
    Preconditions.checkArgument(budget >= 2);
    Node expr = generateExpression(budget - 1);
    return new Node(Token.EXPR_RESULT, expr);
  }

  Node generateIfStatement(int budget) {
    Preconditions.checkArgument(budget >= 3);
    int numComponents;
    if (budget == 3) {
      numComponents = 2;
    } else {
      numComponents = random.nextInt(2) == 0 ? 2 : 3;
    }
    int size = 1;
    int[] componentBudgets = distribute(budget - size, numComponents, 1);
    Node condition = generateExpression(componentBudgets[0]);
    Node node = new Node(Token.IF, condition);
    // the children of IF statements have to be blocks
    node.addChildToBack(generateBlock(componentBudgets[1]));
    if (numComponents == 3) {
      node.addChildToBack(generateBlock(componentBudgets[2]));
    }
    return node;
  }

  Node generateWhile(int budget) {
    Preconditions.checkArgument(budget >= 3);
    int[] componentBudgets = distribute(budget - 1, 2, 1);
    Node expr = generateExpression(componentBudgets[0]);
    loopNesting++;
    Node stmt = generateBlock(componentBudgets[1]);
    loopNesting--;
    return new Node(Token.WHILE, expr, stmt);
  }

  Node generateDoWhile(int budget) {
    Preconditions.checkArgument(budget >= 3);
    int[] componentBudgets = distribute(budget - 1, 2, 1);
    loopNesting++;
    Node stmt = generateBlock(componentBudgets[0]);
    loopNesting--;
    Node expr = generateExpression(componentBudgets[1]);
    return new Node(Token.DO, stmt, expr);
  }

  Node generateFor(int budget) {
    Preconditions.checkArgument(budget >= 2);
    // heuristically assign 1/3 budget to header
    int totalHeaderBudget = (budget - 1) / 3;
    int bodyBudget = budget - 1 - totalHeaderBudget;
    int[] headerBudgets = distribute(totalHeaderBudget, 3, 0);
    Node initializer, condition, increment, body;
    if (headerBudgets[0] == 0) {
      initializer = new Node(Token.EMPTY);
    } else {
      initializer = random.nextInt(2) == 0 ?
          generateVariableStatement(headerBudgets[0]) :
            generateExpression(headerBudgets[0]);
    }
    condition = headerBudgets[1] == 0 ?
        new Node(Token.EMPTY) : generateExpression(headerBudgets[1]);
    increment = headerBudgets[2] == 0 ?
        new Node(Token.EMPTY) : generateExpression(headerBudgets[2]);
    loopNesting++;
    body = generateBlock(bodyBudget);
    loopNesting--;
    return new Node(Token.FOR, initializer, condition, increment, body);
  }

  Node generateForIn(int budget) {
    Preconditions.checkArgument(budget >= 4);
    loopNesting++;
    int[] componentBudgets = distribute(budget, 3, 1);
    Node iterator;
    if (componentBudgets[0] < 3 && symbolTable.getSize() == 0) {
      iterator = generateVariableStatement(componentBudgets[0]);
    } else {
      iterator = random.nextInt(2) == 0 ?
          generateAssignableExpression(componentBudgets[0]) :
            generateVariableStatement(componentBudgets[0]);
    }
    Node expr = generateExpression(componentBudgets[1]);
    Node block = generateBlock(componentBudgets[2]);
    loopNesting--;
    return new Node(Token.FOR, iterator, expr, block);
  }

  Node generateContinue(int budget) {
    Preconditions.checkArgument(budget >= 1);
    Node node = new Node(Token.CONTINUE);
    int index = random.nextInt(currentLabels.size() + 1);
    if (index < currentLabels.size()) {
      node.addChildToBack(
          Node.newString(Token.LABEL_NAME, currentLabels.get(index)));
    }
    return node;
  }

  Node generateBreak(int budget) {
    Preconditions.checkArgument(budget >= 1);
    Node node = new Node(Token.BREAK);
    int index = random.nextInt(currentLabels.size() + 1);
    if (index < currentLabels.size()) {
      node.addChildToBack(
          Node.newString(Token.LABEL_NAME, currentLabels.get(index)));
    }
    return node;
  }

  Node generateReturn(int budget) {
    Preconditions.checkArgument(budget >= 1);
    Node node = new Node(Token.RETURN);
    if (budget > 1) {
      node.addChildToBack(generateExpression(budget - 1));
    }
    return node;
  }

  Node generateSwitch(int budget) {
    Preconditions.checkArgument(budget >= 2);
    switchNesting++;
    int numCases = budget > 2 ? random.nextInt(budget - 2) : 0;
    /* use budget instead of budget -1 because expression node will get 1 more
     * than its minimal requirement. */
    int[] componentBudgets = distribute(budget, numCases + 1, 2);
    Node switchStmt = new Node(
        Token.SWITCH,
        // deduct the extra budget it gets
        generateExpression(componentBudgets[0] - 1));
    int defaultClauseIndex = -1;
    if (numCases > 1) {
      defaultClauseIndex = random.nextInt(numCases + 1);
    }
    for (int i = 0; i < numCases; i++) {
      Node clause, block;
      int remainingBudget = componentBudgets[i + 1] - 1;
      if (i == defaultClauseIndex) {
        // increase by one to generate the synthetic block node for free
        block = generateBlock(remainingBudget + 1);
        clause = new Node(Token.DEFAULT);
      } else {
        int exprBudget = remainingBudget / 3;
        if (exprBudget == 0) {
          exprBudget = 1;
        }
        // increase by one to generate the synthetic block node for free
        int blockBudget = remainingBudget - exprBudget + 1;
        block = generateBlock(blockBudget);
        clause = new Node(Token.CASE,
            generateExpression(exprBudget));
      }
      // set it synthetic to conform the requirement from the compiler
      block.setIsSyntheticBlock(true);
      clause.addChildrenToBack(block);
      switchStmt.addChildToBack(clause);
    }
    switchNesting--;
    return switchStmt;
  }

  Node generateLabelledStatement(int budget) {
    Preconditions.checkArgument(budget >= 3);
    String labelName;
    do {
      labelName = "x_" + nextNumber();
    } while (currentLabels.search(labelName) != -1);
    Node name = Node.newString(
        Token.LABEL_NAME, labelName);
    Node node = new Node(Token.LABEL, name);
    currentLabels.push(labelName);
    Node statement = generateStatement(budget - 2);
    node.addChildToBack(statement);
    currentLabels.pop();
    return node;
  }

  Node generateThrow(int budget) {
    Preconditions.checkArgument(budget >= 2);
    return new Node(Token.THROW, generateExpression(budget - 1));
  }

  Node generateTry(int budget) {
    Preconditions.checkArgument(budget >= 3);
    int bodyBudget = (budget - 1) / 2;
    Node tryStmt = new Node(Token.TRY, generateBlock(bodyBudget));
    int[] catchAndFinallyBudgets = distribute(budget - 1 - bodyBudget, 2, 0);
    Node catchBlock = new Node(Token.BLOCK);
    if (catchAndFinallyBudgets[0] > 3) {
      catchBlock.addChildToBack(
          new Node(Token.CATCH, generateIdentifier(1, false),
          generateBlock(catchAndFinallyBudgets[0] - 1)));
    } else {
      // not enough budget for catch block, give all budget to finally block
      catchAndFinallyBudgets[1] = budget - 1 - bodyBudget;
    }
    tryStmt.addChildToBack(catchBlock);
    // finally block
    if (catchAndFinallyBudgets[1] > 0) {
      tryStmt.addChildToBack(generateBlock(catchAndFinallyBudgets[1]));
    }
    return tryStmt;
  }

  private Node generateFunction(int budget, boolean isExpression) {
    int remainingBudget;
    Node name;
    if (isExpression) {
      Preconditions.checkArgument(budget >= 3);
      symbolTable.addScope();
      if (budget >= 4 && random.nextInt(2) == 0) {
        // the name of function expression is only visible in the function
        name = generateIdentifier(1, false);
        remainingBudget = budget - 4;
      } else {
        name = Node.newString(Token.NAME, "");
        remainingBudget = budget - 3;
      }
    } else {
      Preconditions.checkArgument(budget >= 4);
      name = generateIdentifier(1, false);
      remainingBudget = budget - 4;
      symbolTable.addScope();
    }
    // param list is in the new scope
    Node paramList = new Node(Token.PARAM_LIST);
    Node body = new Node(Token.BLOCK);
    int numComponents = random.nextInt(remainingBudget + 1);
    /*
     * Decide the length of param list in the same way as the size of each
     * source element. However, param list can have the size 0, which source
     * elements cannot. So remainingBudget + 1 is used to allocate budgets, then
     * that extra 1 is deducted from componentBudgets[0]
     */
    if (numComponents > 0) {
      int[] componentBudgets = distribute(remainingBudget + 1, numComponents, 1);
      componentBudgets[0]--;
      for (int i = 0; i < componentBudgets[0]; i++) {
        paramList.addChildToBack(generateIdentifier(1, false));
      }
      for (int i = 1; i < numComponents; i++) {
        body.addChildToBack(generateSourceElement(componentBudgets[i]));
      }
    }
    symbolTable.removeScope();
    Node function = new Node(Token.FUNCTION,
        name, paramList, body);
    return function;
  }

  Node generateFunctionDeclaration(int budget) {
    return generateFunction(budget, false);
  }

  Node generateSourceElement(int budget) {
    if (budget < 4) {
      return generateStatement(budget);
    }
    if (random.nextInt(2) == 0) {
      return generateStatement(budget);
    } else {
      return generateFunctionDeclaration(budget);
    }
  }

  Node[] generateProgram(int budget) {
    // Heuristically limit the length of program to 1/5 of the budget to
    // increase the complexity of the program
    int numElements = random.nextInt(budget) / 5 + 1;
    if (numElements > 0) {
      int[] elemBudgets = distribute(budget, numElements, 1);
      Node[] elements = new Node[numElements];
      for (int i = 0; i < numElements; i++) {
        elements[i] = generateSourceElement(elemBudgets[i]);
      }
      return elements;
    } else {
      return new Node[]{generateEmptyStatement(budget)};
    }
  }

  private Node generatePropertyName() {
    String name;
    if (random.nextInt(2) == 0) {
      name = StringGenerator.getPropertyName(random);
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

  public static Node buildScript(Node ast) {
    return buildScript(new Node[]{ast});
  }

  public static Node buildScript(Node[] elements) {
    Node script = new Node(Token.SCRIPT, elements);
    InputId inputId = new InputId("fuzzedInput");
    script.setInputId(inputId);
    script.setSourceFileForTesting(inputId.getIdName());
    return script;
  }

  /**
   * Divide budget randomly into n shares, guarantee that each share has at
   * least min budget.
   */
  int[] distribute(int budget, int n, int min) {
    int[] subBudgets = new int[n];
    for (int i = 0; i < n; i++) {
      subBudgets[i] = min;
    }
    budget -= n * min;
    if (budget > 3 * n) {
      double[] rands = new double[n];
      double sum = 0;
      for (int i = 0; i < n; i++) {
        rands[i] = random.nextDouble();
        sum += rands[i];
      }
      for (int i = 0; i < n; i++) {
        double additionalBudget = budget / sum * rands[i];
        subBudgets[i] += additionalBudget;
        budget -= additionalBudget;
      }
    }
    while (budget > 0) {
      subBudgets[random.nextInt(n)]++;
      budget--;
    }
    return subBudgets;
  }

  private int nextNumber() {
    return counter++;
  }

}
