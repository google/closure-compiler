/*
 * Copyright 2011 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.parsing.parser.codegeneration;

import com.google.javascript.jscomp.parsing.parser.Keywords;
import com.google.javascript.jscomp.parsing.parser.ParseTreeVisitor;
import com.google.javascript.jscomp.parsing.parser.PredefinedName;
import com.google.javascript.jscomp.parsing.parser.Token;
import com.google.javascript.jscomp.parsing.parser.TokenType;
import com.google.javascript.jscomp.parsing.parser.trees.*;

import java.util.List;

/**
 * Converts a ParseTree to text.
 */
public final class ParseTreeWriter extends ParseTreeVisitor {
  private final StringBuilder result;
  private StringBuilder currentLine;
  private String currentLineComment;
  private static final String NEW_LINE = "\n";
  private int indentDepth;
  private final boolean PRETTY_PRINT = true;
  private final boolean SHOW_LINE_NUMBERS;
  private final ParseTree HIGHLIGHTED;

  private ParseTreeWriter(ParseTree highlighted, boolean showLineNumbers) {
    SHOW_LINE_NUMBERS = showLineNumbers;
    result = new StringBuilder();
    indentDepth = 0;
    currentLine = new StringBuilder();
    currentLineComment = null;
    HIGHLIGHTED = highlighted;
  }

  public static String write(ParseTree tree) {
    return write(tree, null, true);
  }

  public static String write(ParseTree tree, boolean showLineNumbers) {
    return write(tree, null, showLineNumbers);
  }

  public static String write(ParseTree tree, ParseTree highlighted, boolean showLineNumbers) {
    ParseTreeWriter writer = new ParseTreeWriter(highlighted, showLineNumbers);
    writer.visitAny(tree);
    if (writer.currentLine.length() > 0) {
      writer.writeln();
    }
    return writer.result.toString();
  }

  @Override
  protected void visitAny(ParseTree tree) {
    // set background color to red if tree is highlighted
    if (tree != null && tree == HIGHLIGHTED) {
      write("\033[41m");
    }

    if (tree != null && tree.location != null && tree.location.start != null && SHOW_LINE_NUMBERS) {
      currentLineComment = "Line: " + (tree.location.start.line + 1);
    }
    super.visitAny(tree);

    // set background color to normal
    if (tree != null && tree == HIGHLIGHTED) {
      write("\033[0m");
    }
  }

  @Override
  protected void visit(ArgumentListTree tree) {
    write(TokenType.OPEN_PAREN);
    writeList(tree.arguments, TokenType.COMMA, false);
    write(TokenType.CLOSE_PAREN);
  }

  @Override
  protected void visit(ArrayLiteralExpressionTree tree) {
    write(TokenType.OPEN_SQUARE);
    writeList(tree.elements, TokenType.COMMA, false);
    write(TokenType.CLOSE_SQUARE);
  }

  @Override
  public void visit(ArrayPatternTree tree) {
    write(TokenType.OPEN_SQUARE);
    writeList(tree.elements, TokenType.COMMA, false);
    write(TokenType.CLOSE_SQUARE);
  }

  @Override
  public void visit(AwaitStatementTree tree) {
    write(TokenType.AWAIT);
    if (tree.identifier != null) {
      write(tree.identifier);
      write(TokenType.EQUAL);
    }
    visitAny(tree.expression);
    write(TokenType.SEMI_COLON);
  }

  @Override
  protected void visit(BinaryOperatorTree tree) {
    visitAny(tree.left);
    write(tree.operator);
    visitAny(tree.right);
  }

  @Override
  protected void visit(BlockTree tree) {
    write(TokenType.OPEN_CURLY);
    writelnList(tree.statements);
    write(TokenType.CLOSE_CURLY);
  }

  @Override
  protected void visit(BreakStatementTree tree) {
    write(TokenType.BREAK);
    if (tree.name != null) {
      write(tree.name);
    }
    write(TokenType.SEMI_COLON);
  }

  @Override
  protected void visit(CallExpressionTree tree) {
    visitAny(tree.operand);
    visitAny(tree.arguments);
  }

  @Override
  protected void visit(CaseClauseTree tree) {
    write(TokenType.CASE);
    visitAny(tree.expression);
    write(TokenType.COLON);
    indentDepth++;
    writelnList(tree.statements);
    indentDepth--;
  }

  @Override
  protected void visit(CatchTree tree) {
    write(TokenType.CATCH);
    write(TokenType.OPEN_PAREN);
    write(tree.exceptionName);
    write(TokenType.CLOSE_PAREN);
    visitAny(tree.catchBody);
  }

  @Override
  protected void visit(ClassDeclarationTree tree) {
    write(TokenType.CLASS);
    write(tree.name);
    if (tree.superClass != null) {
      write(TokenType.COLON);
      visitAny(tree.superClass);
    }
    write(TokenType.OPEN_CURLY);
    writelnList(tree.elements);
    write(TokenType.CLOSE_CURLY);
  }

  @Override
  protected void visit(ClassExpressionTree tree) {
    write(TokenType.CLASS);
  }

  @Override
  protected void visit(CommaExpressionTree tree) {
    writeList(tree.expressions, TokenType.COMMA, false);
  }

  @Override
  protected void visit(ConditionalExpressionTree tree) {
    visitAny(tree.condition);
    write(TokenType.QUESTION);
    visitAny(tree.left);
    write(TokenType.COLON);
    visitAny(tree.right);
  }

  @Override
  protected void visit(ContinueStatementTree tree) {
    write(TokenType.CONTINUE);
    if (tree.name != null) {
      write(tree.name);
    }
    write(TokenType.SEMI_COLON);
  }

  @Override
  protected void visit(DebuggerStatementTree tree) {
    write(TokenType.DEBUGGER);
    write(TokenType.SEMI_COLON);
  }

  @Override
  protected void visit(DefaultClauseTree tree) {
    write(TokenType.DEFAULT);
    write(TokenType.COLON);
    indentDepth++;
    writelnList(tree.statements);
    indentDepth--;
  }

  @Override
  protected void visit(DefaultParameterTree tree) {
    visitAny(tree.identifier);
    write(TokenType.EQUAL);
    visitAny(tree.expression);
  }

  @Override
  protected void visit(DoWhileStatementTree tree) {
    write(TokenType.DO);
    visitAny(tree.body);
    write(TokenType.WHILE);
    write(TokenType.OPEN_PAREN);
    visitAny(tree.condition);
    write(TokenType.CLOSE_PAREN);
    write(TokenType.SEMI_COLON);
  }

  @Override
  protected void visit(EmptyStatementTree tree) {
    write(TokenType.SEMI_COLON);
  }

  @Override
  protected void visit(ExportDeclarationTree tree) {
    write(TokenType.EXPORT);
    visitAny(tree.declaration);
  }

  @Override
  protected void visit(ExpressionStatementTree tree) {
    visitAny(tree.expression);
    write(TokenType.SEMI_COLON);
  }

  @Override
  protected void visit(FieldDeclarationTree tree) {
    if (tree.isStatic) {
      write(TokenType.STATIC);
    }
    if (tree.isConst) {
      write(TokenType.CONST);
    }
    writeList(tree.declarations, TokenType.COMMA, false);
    write(TokenType.SEMI_COLON);
  }

  @Override
  protected void visit(FinallyTree tree) {
    write(TokenType.FINALLY);
    visitAny(tree.block);
  }

  @Override
  protected void visit(ForEachStatementTree tree) {
    write(TokenType.FOR);
    write(TokenType.OPEN_PAREN);
    visitAny(tree.initializer);
    write(TokenType.COLON);
    visitAny(tree.collection);
    write(TokenType.CLOSE_PAREN);
    visitAny(tree.body);
  }

  @Override
  protected void visit(ForInStatementTree tree) {
    write(TokenType.FOR);
    write(TokenType.OPEN_PAREN);
    visitAny(tree.initializer);
    write(TokenType.IN);
    visitAny(tree.collection);
    write(TokenType.CLOSE_PAREN);
    visitAny(tree.body);
  }

  @Override
  protected void visit(ForStatementTree tree) {
    write(TokenType.FOR);
    write(TokenType.OPEN_PAREN);
    visitAny(tree.initializer);
    write(TokenType.SEMI_COLON);
    visitAny(tree.condition);
    write(TokenType.SEMI_COLON);
    visitAny(tree.increment);
    write(TokenType.CLOSE_PAREN);
    visitAny(tree.body);
  }

  @Override
  protected void visit(FormalParameterListTree tree) {
    boolean first = true;

    for (ParseTree parameter : tree.parameters) {
      if (first) {
        first = false;
      } else {
        write(TokenType.COMMA);
      }

      visitAny(parameter);
    }
  }

  @Override
  protected void visit(FunctionDeclarationTree tree) {
    if (tree.isStatic) {
      write(TokenType.STATIC);
    }
    write(Keywords.FUNCTION);
    if (tree.name != null) {
      write(tree.name);
    }
    write(TokenType.OPEN_PAREN);
    visitAny(tree.formalParameterList);
    write(TokenType.CLOSE_PAREN);
    visitAny(tree.functionBody);
  }

  @Override
  protected void visit(GetAccessorTree tree) {
    if (tree.isStatic) {
      write(TokenType.STATIC);
    }
    write(PredefinedName.GET);
    write(tree.propertyName);
    write(TokenType.OPEN_PAREN);
    write(TokenType.CLOSE_PAREN);
    visitAny(tree.body);
  }

  @Override
  protected void visit(IdentifierExpressionTree tree) {
    write(tree.identifierToken);
  }

  @Override
  protected void visit(IfStatementTree tree) {
    write(TokenType.IF);
    write(TokenType.OPEN_PAREN);
    visitAny(tree.condition);
    write(TokenType.CLOSE_PAREN);
    visitAny(tree.ifClause);
    if (tree.elseClause != null) {
      write(TokenType.ELSE);
      visitAny(tree.elseClause);
    }
  }

  @Override
  protected void visit(ImportDeclarationTree tree) {
    write(TokenType.IMPORT);
    writeList(tree.importPathList, TokenType.COMMA, false);
    write(TokenType.SEMI_COLON);
  }

  @Override
  protected void visit(ImportPathTree tree) {
    writeTokenList(tree.qualifiedPath, TokenType.PERIOD, false);
    switch (tree.kind) {
    case ALL:
      write(TokenType.PERIOD);
      write(TokenType.STAR);
      break;
    case NONE:
      break;
    case SET:
      write(TokenType.PERIOD);
      write(TokenType.OPEN_CURLY);
      writeList(tree.importSpecifierSet, TokenType.COMMA, false);
      write(TokenType.CLOSE_CURLY);
      break;
    }
  }

  @Override
  protected void visit(ImportSpecifierTree tree) {
    write(tree.importedName);
    if (tree.destinationName != null) {
      write(TokenType.COLON);
      write(tree.destinationName);
    }
  }

  @Override
  protected void visit(LabelledStatementTree tree) {
    write(tree.name);
    write(TokenType.COLON);
    visitAny(tree.statement);
  }

  @Override
  protected void visit(LiteralExpressionTree tree) {
    write(tree.literalToken);
  }

  @Override
  protected void visit(MemberExpressionTree tree) {
    visitAny(tree.operand);
    write(TokenType.PERIOD);
    write(tree.memberName);
  }

  @Override
  protected void visit(MemberLookupExpressionTree tree) {
    visitAny(tree.operand);
    write(TokenType.OPEN_SQUARE);
    visitAny(tree.memberExpression);
    write(TokenType.CLOSE_SQUARE);
  }

  @Override
  protected void visit(MissingPrimaryExpressionTree tree) {
    write("MissingPrimaryExpressionTree");
  }

  @Override
  protected void visit(MixinTree tree) {
    write(PredefinedName.MIXIN);
    write(tree.name);
    visitAny(tree.mixinResolves);
    write(TokenType.SEMI_COLON);
  }

  @Override
  protected void visit(MixinResolveTree tree) {
    write(tree.from);
    write(TokenType.COLON);
    write(tree.to);
  }

  @Override
  protected void visit(MixinResolveListTree tree) {
    write(TokenType.OPEN_CURLY);
    writeList(tree.resolves, TokenType.COMMA, false);
    write(TokenType.CLOSE_CURLY);
  }

  @Override
  protected void visit(ModuleDefinitionTree tree) {
    write(PredefinedName.MODULE);
    write(tree.name);
    write(TokenType.OPEN_CURLY);
    writeln();
    writeList(tree.elements, null, true);
    write(TokenType.CLOSE_CURLY);
    writeln();
  }

  @Override
  protected void visit(NewExpressionTree tree) {
    write(TokenType.NEW);
    visitAny(tree.operand);
    visitAny(tree.arguments);
  }

  @Override
  protected void visit(NullTree tree) {
  }

  @Override
  protected void visit(ObjectLiteralExpressionTree tree) {
    write(TokenType.OPEN_CURLY);
    if (tree.propertyNameAndValues.size() > 1) {
      writeln();
    }
    writelnList(tree.propertyNameAndValues, TokenType.COMMA);
    if (tree.propertyNameAndValues.size() > 1) {
      writeln();
    }
    write(TokenType.CLOSE_CURLY);
  }

  @Override
  protected void visit(ObjectPatternTree tree) {
    write(TokenType.OPEN_CURLY);
    writelnList(tree.fields, TokenType.COMMA);
    write(TokenType.CLOSE_CURLY);
  }

  @Override
  protected void visit(ObjectPatternFieldTree tree) {
    write(tree.identifier);
    if (tree.element != null) {
      write(TokenType.COLON);
      visitAny(tree.element);
    }
  }

  @Override
  protected void visit(ParenExpressionTree tree) {
    write(TokenType.OPEN_PAREN);
    super.visit(tree);
    write(TokenType.CLOSE_PAREN);
  }

  @Override
  protected void visit(PostfixExpressionTree tree) {
    visitAny(tree.operand);
    write(tree.operator);
  }

  @Override
  protected void visit(ProgramTree tree) {
    writelnList(tree.sourceElements);
  }

  @Override
  protected void visit(PropertyNameAssignmentTree tree) {
    write(tree.name);
    write(TokenType.COLON);
    visitAny(tree.value);
  }

  @Override
  protected void visit(RequiresMemberTree tree) {
    write(PredefinedName.REQUIRES);
    write(tree.name);
    write(TokenType.SEMI_COLON);
  }

  @Override
  protected void visit(ReturnStatementTree tree) {
    write(TokenType.RETURN);
    visitAny(tree.expression);
    write(TokenType.SEMI_COLON);
  }

  @Override
  protected void visit(RestParameterTree tree) {
    write(TokenType.SPREAD);
    write(tree.identifier);
  }

  @Override
  protected void visit(SetAccessorTree tree) {
    if (tree.isStatic) {
      write(TokenType.STATIC);
    }
    write(PredefinedName.SET);
    write(tree.propertyName);
    write(TokenType.OPEN_PAREN);
    write(tree.parameter);
    write(TokenType.CLOSE_PAREN);
    visitAny(tree.body);
  }

  @Override
  protected void visit(SpreadExpressionTree tree) {
    write(TokenType.SPREAD);
    visitAny(tree.expression);
  }

  @Override
  protected void visit(SpreadPatternElementTree tree) {
    write(TokenType.SPREAD);
    visitAny(tree.lvalue);
  }

  @Override
  protected void visit(SuperExpressionTree tree) {
    write(TokenType.SUPER);
  }

  @Override
  protected void visit(SwitchStatementTree tree) {
    write(TokenType.SWITCH);
    write(TokenType.OPEN_PAREN);
    visitAny(tree.expression);
    write(TokenType.CLOSE_PAREN);
    write(TokenType.OPEN_CURLY);
    writelnList(tree.caseClauses);
    write(TokenType.CLOSE_CURLY);
  }

  @Override
  protected void visit(ThisExpressionTree tree) {
    write(TokenType.THIS);
  }

  @Override
  protected void visit(TraitDeclarationTree tree) {
    write(PredefinedName.TRAIT);
    write(tree.name);
    write(TokenType.OPEN_CURLY);
    visitList(tree.elements);
    write(TokenType.CLOSE_CURLY);
  }

  @Override
  protected void visit(ThrowStatementTree tree) {
    write(TokenType.THROW);
    visitAny(tree.value);
    write(TokenType.SEMI_COLON);
  }

  @Override
  protected void visit(TryStatementTree tree) {
    write(TokenType.TRY);
    visitAny(tree.body);
    visitAny(tree.catchBlock);
    visitAny(tree.finallyBlock);
  }

  @Override
  protected void visit(UnaryExpressionTree tree) {
    write(tree.operator);
    visitAny(tree.operand);
  }

  @Override
  protected void visit(VariableDeclarationListTree tree) {
    write(tree.declarationType);
    writeList(tree.declarations, TokenType.COMMA, false);
  }

  @Override
  protected void visit(VariableDeclarationTree tree) {
    visitAny(tree.lvalue);
    if (tree.initializer != null) {
      write(TokenType.EQUAL);
      visitAny(tree.initializer);
    }
  }

  @Override
  protected void visit(VariableStatementTree tree) {
    super.visit(tree);
    write(TokenType.SEMI_COLON);
  }

  @Override
  protected void visit(WhileStatementTree tree) {
    write(TokenType.WHILE);
    write(TokenType.OPEN_PAREN);
    visitAny(tree.condition);
    write(TokenType.CLOSE_PAREN);
    visitAny(tree.body);
  }

  @Override
  protected void visit(WithStatementTree tree) {
    write(TokenType.WITH);
    write(TokenType.OPEN_PAREN);
    visitAny(tree.expression);
    write(TokenType.CLOSE_PAREN);
    visitAny(tree.body);
  }

  @Override
  protected void visit(YieldStatementTree tree) {
    write(TokenType.YIELD);
    visitAny(tree.expression);
    write(TokenType.SEMI_COLON);
  }

  private void writelnList(List<? extends ParseTree> list) {
    if (!list.isEmpty()) {
      writeln();
    }
    writelnList(list, null);
    if (!list.isEmpty()) {
      writeln();
    }
  }

  private void writelnList(List<? extends ParseTree> list,
      TokenType delimiter) {
    writeList(list, delimiter, true);
  }

  private void writeln() {
    if (currentLineComment != null) {
      while (currentLine.length() < 80) {
        currentLine.append(' ');
      }
      currentLine.append(" // ").append(currentLineComment);
      currentLineComment = null;
    }
    result.append(currentLine.toString());
    result.append(NEW_LINE);
    currentLine = new StringBuilder();
  }

  private void writeList(List<? extends ParseTree> list,
      TokenType delimiter, boolean writeNewLine) {
    boolean first = true;
    for (ParseTree element : list) {
      if (first) {
        first = false;
      } else {
        if (delimiter != null) {
          write(delimiter);
        }
        if (writeNewLine) {
          writeln();
        }
      }
      visitAny(element);
    }
  }

  private void writeTokenList(List<? extends Token> list,
      TokenType delimiter, boolean writeNewLine) {
    boolean first = true;
    for (Token element : list) {
      if (first) {
        first = false;
      } else {
        if (delimiter != null) {
          write(delimiter);
        }
        if (writeNewLine) {
          writeln();
        }
      }
      write(element);
    }
  }

  private void write(Keywords keyword) {
    write(keyword.toString());
  }

  private void write(TokenType type) {
    if (type == TokenType.CLOSE_CURLY) {
      indentDepth--;
    }

    // Imperfect but good enough spacing rules to make output readable.
    boolean spaceBefore = true;
    boolean spaceAfter = true;
    switch(type) {
      case PERIOD:
      case OPEN_SQUARE:
      case OPEN_PAREN:
      case CLOSE_SQUARE:
        spaceBefore = false;
        spaceAfter = false;
        break;
      case COLON:
      case COMMA:
      case SEMI_COLON:
      case CLOSE_PAREN:
        spaceBefore = false;
        break;
    }
    write(type.toString(), spaceBefore, spaceAfter);

    if (type == TokenType.OPEN_CURLY) {
      indentDepth++;
    }
  }

  private void write(Token token) {
    write(token.toString());
  }

  private void write(String value) {
    write(value, true, true);
  }

  private void write(String value, boolean spaceBefore, boolean spaceAfter) {
    if (value != null) {
      if (PRETTY_PRINT) {
        if (currentLine.length() == 0) {
          for (int i = 0, indent = indentDepth * 2; i < indent; ++i) {
            currentLine.append(' ');
          }
        } else {
          int lastIndex = currentLine.length() - 1;
          if (spaceBefore == false && currentLine.charAt(lastIndex) == ' ') {
            currentLine.deleteCharAt(lastIndex);
          }
        }
      }
      currentLine.append(value);
      if (spaceAfter) {
        currentLine.append(' ');
      }
    }
  }
}
