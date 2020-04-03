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

package com.google.javascript.jscomp.parsing.parser.trees;

import com.google.javascript.jscomp.parsing.parser.util.SourcePosition;
import com.google.javascript.jscomp.parsing.parser.util.SourceRange;

/**
 * An abstract syntax tree for JavaScript parse trees.
 * Immutable.
 * A plain old data structure. Should include data members and simple accessors only.
 *
 * Derived classes should have a 'Tree' suffix. Each concrete derived class should have a
 * ParseTreeType whose name matches the derived class name.
 *
 * A parse tree derived from source should have a non-null location. A parse tree that is
 * synthesized by the compiler may have a null location.
 *
 * When adding a new subclass of ParseTree you must also do the following:
 *   - add a new entry to ParseTreeType
 *   - add ParseTree.asXTree()
 */
public class ParseTree {
  public final ParseTreeType type;
  public final SourceRange location;

  protected ParseTree(ParseTreeType type, SourceRange location) {
    this.type = type;
    this.location = location;
  }

  public SourcePosition getStart() {
    return location.start;
  }

  public SourcePosition getEnd() {
    return location.end;
  }

  public ArrayLiteralExpressionTree asArrayLiteralExpression() {
    return (ArrayLiteralExpressionTree) this; }
  public ArrayPatternTree asArrayPattern() { return (ArrayPatternTree) this; }
  public BinaryOperatorTree asBinaryOperator() { return (BinaryOperatorTree) this; }
  public BlockTree asBlock() { return (BlockTree) this; }
  public BreakStatementTree asBreakStatement() { return (BreakStatementTree) this; }
  public CallExpressionTree asCallExpression() { return (CallExpressionTree) this; }

  public OptionalCallExpressionTree asOptionalCallExpression() {
    return (OptionalCallExpressionTree) this;
  }

  public CaseClauseTree asCaseClause() { return (CaseClauseTree) this; }
  public CatchTree asCatch() { return (CatchTree) this; }
  public ClassDeclarationTree asClassDeclaration() { return (ClassDeclarationTree) this; }
  public CommaExpressionTree asCommaExpression() { return (CommaExpressionTree) this; }
  public ComprehensionIfTree asComprehensionIf() { return (ComprehensionIfTree) this; }
  public ComprehensionForTree asComprehensionFor() { return (ComprehensionForTree) this; }
  public ComprehensionTree asComprehension() { return (ComprehensionTree) this; }
  public ComputedPropertyDefinitionTree asComputedPropertyDefinition() {
    return (ComputedPropertyDefinitionTree) this; }
  public ComputedPropertyGetterTree asComputedPropertyGetter() {
    return (ComputedPropertyGetterTree) this; }
  public ComputedPropertyMethodTree asComputedPropertyMethod() {
    return (ComputedPropertyMethodTree) this; }
  public ComputedPropertyMemberVariableTree asComputedPropertyMemberVariable() {
    return (ComputedPropertyMemberVariableTree) this; }
  public ComputedPropertySetterTree asComputedPropertySetter() {
    return (ComputedPropertySetterTree) this; }
  public ConditionalExpressionTree asConditionalExpression() {
    return (ConditionalExpressionTree) this; }
  public ContinueStatementTree asContinueStatement() { return (ContinueStatementTree) this; }
  public DebuggerStatementTree asDebuggerStatement() { return (DebuggerStatementTree) this; }
  public DefaultClauseTree asDefaultClause() { return (DefaultClauseTree) this; }
  public DefaultParameterTree asDefaultParameter() { return (DefaultParameterTree) this; }
  public DoWhileStatementTree asDoWhileStatement() { return (DoWhileStatementTree) this; }
  public EmptyStatementTree asEmptyStatement() { return (EmptyStatementTree) this; }
  public ExportDeclarationTree asExportDeclaration() { return (ExportDeclarationTree) this; }
  public ExportSpecifierTree asExportSpecifier() { return (ExportSpecifierTree) this; }
  public ExpressionStatementTree asExpressionStatement() { return (ExpressionStatementTree) this; }
  public FinallyTree asFinally() { return (FinallyTree) this; }
  public ForOfStatementTree asForOfStatement() { return (ForOfStatementTree) this; }
  public ForInStatementTree asForInStatement() { return (ForInStatementTree) this; }
  public FormalParameterListTree asFormalParameterList() { return (FormalParameterListTree) this; }
  public ForStatementTree asForStatement() { return (ForStatementTree) this; }
  public FunctionDeclarationTree asFunctionDeclaration() { return (FunctionDeclarationTree) this; }
  public GetAccessorTree asGetAccessor() { return (GetAccessorTree) this; }
  public IdentifierExpressionTree asIdentifierExpression() {
    return (IdentifierExpressionTree) this; }
  public IfStatementTree asIfStatement() { return (IfStatementTree) this; }
  public ImportDeclarationTree asImportDeclaration() { return (ImportDeclarationTree) this; }
  public ImportSpecifierTree asImportSpecifier() { return (ImportSpecifierTree) this; }

  public DynamicImportTree asDynamicImportExpression() {
    return (DynamicImportTree) this;
  }

  public ImportMetaExpressionTree asImportMetaExpression() {
    return (ImportMetaExpressionTree) this;
  }

  public LabelledStatementTree asLabelledStatement() { return (LabelledStatementTree) this; }
  public LiteralExpressionTree asLiteralExpression() { return (LiteralExpressionTree) this; }
  public MemberExpressionTree asMemberExpression() { return (MemberExpressionTree) this; }

  public OptionalMemberExpressionTree asOptionalMemberExpression() {
    return (OptionalMemberExpressionTree) this;
  }

  public MemberLookupExpressionTree asMemberLookupExpression() {
    return (MemberLookupExpressionTree) this; }

  public OptionalMemberLookupExpressionTree asOptionalMemberLookupExpression() {
    return (OptionalMemberLookupExpressionTree) this;
  }

  public MemberVariableTree asMemberVariable() { return (MemberVariableTree) this; }
  public MissingPrimaryExpressionTree asMissingPrimaryExpression() {
    return (MissingPrimaryExpressionTree) this; }
  public NewExpressionTree asNewExpression() { return (NewExpressionTree) this; }
  public NullTree asNull() { return (NullTree) this; }
  public ObjectLiteralExpressionTree asObjectLiteralExpression() {
    return (ObjectLiteralExpressionTree) this; }
  public ObjectPatternTree asObjectPattern() { return (ObjectPatternTree) this; }
  public ParenExpressionTree asParenExpression() { return (ParenExpressionTree) this; }
  public ProgramTree asProgram() { return (ProgramTree) this; }
  public PropertyNameAssignmentTree asPropertyNameAssignment() {
    return (PropertyNameAssignmentTree) this; }

  public IterRestTree asIterRest() {
    return (IterRestTree) this;
  }

  public ObjectRestTree asObjectRest() {
    return (ObjectRestTree) this;
  }

  public ReturnStatementTree asReturnStatement() { return (ReturnStatementTree) this; }
  public SetAccessorTree asSetAccessor() { return (SetAccessorTree) this; }

  public IterSpreadTree asIterSpread() {
    return (IterSpreadTree) this;
  }

  public ObjectSpreadTree asObjectSpread() {
    return (ObjectSpreadTree) this;
  }

  public SuperExpressionTree asSuperExpression() { return (SuperExpressionTree) this; }
  public SwitchStatementTree asSwitchStatement() { return (SwitchStatementTree) this; }
  public TemplateLiteralExpressionTree asTemplateLiteralExpression() {
    return (TemplateLiteralExpressionTree) this; }
  public TemplateLiteralPortionTree asTemplateLiteralPortion() {
    return (TemplateLiteralPortionTree) this; }
  public TemplateSubstitutionTree asTemplateSubstitution() {
    return (TemplateSubstitutionTree) this; }
  public ThisExpressionTree asThisExpression() { return (ThisExpressionTree) this; }
  public ThrowStatementTree asThrowStatement() { return (ThrowStatementTree) this; }
  public TryStatementTree asTryStatement() { return (TryStatementTree) this; }
  public TypeNameTree asTypeName() { return (TypeNameTree) this; }
  public TypedParameterTree asTypedParameter() { return (TypedParameterTree) this; }
  public OptionalParameterTree asOptionalParameter() { return (OptionalParameterTree) this; }
  public ParameterizedTypeTree asParameterizedType() { return (ParameterizedTypeTree) this; }
  public ArrayTypeTree asArrayType() { return (ArrayTypeTree) this; }
  public RecordTypeTree asRecordType() { return (RecordTypeTree) this; }
  public UnionTypeTree asUnionType() { return (UnionTypeTree) this; }
  public FunctionTypeTree asFunctionType() { return (FunctionTypeTree) this; }
  public TypeQueryTree asTypeQuery() { return (TypeQueryTree) this; }
  public GenericTypeListTree asGenericTypeList() { return (GenericTypeListTree) this; }
  public UnaryExpressionTree asUnaryExpression() { return (UnaryExpressionTree) this; }
  public VariableDeclarationListTree asVariableDeclarationList() {
    return (VariableDeclarationListTree) this; }
  public VariableDeclarationTree asVariableDeclaration() {
    return (VariableDeclarationTree) this; }
  public VariableStatementTree asVariableStatement() { return (VariableStatementTree) this; }
  public WhileStatementTree asWhileStatement() { return (WhileStatementTree) this; }
  public WithStatementTree asWithStatement() { return (WithStatementTree) this; }
  public YieldExpressionTree asYieldStatement() { return (YieldExpressionTree) this; }
  public AwaitExpressionTree asAwaitExpression() {
    return (AwaitExpressionTree) this;
  }
  public InterfaceDeclarationTree asInterfaceDeclaration() {
    return (InterfaceDeclarationTree) this;
  }
  public EnumDeclarationTree asEnumDeclaration() { return (EnumDeclarationTree) this; }
  public TypeAliasTree asTypeAlias() { return (TypeAliasTree) this; }
  public AmbientDeclarationTree asAmbientDeclaration() { return (AmbientDeclarationTree) this; }
  public NamespaceDeclarationTree asNamespaceDeclaration() {
    return (NamespaceDeclarationTree) this;
  }
  public IndexSignatureTree asIndexSignature() { return (IndexSignatureTree) this; }
  public CallSignatureTree asCallSignature() { return (CallSignatureTree) this; }

  public NewTargetExpressionTree asNewTargetExpression() {
    return (NewTargetExpressionTree) this;
  }

  public UpdateExpressionTree asUpdateExpression() {
    return (UpdateExpressionTree) this;
  }

  public ForAwaitOfStatementTree asForAwaitOfStatement() {
    return (ForAwaitOfStatementTree) this;
  }

  public boolean isPattern() {
    ParseTree parseTree = this;
    while (parseTree.type == ParseTreeType.PAREN_EXPRESSION) {
      parseTree = parseTree.asParenExpression().expression;
    }

    switch (parseTree.type) {
      case ARRAY_PATTERN:
      case OBJECT_PATTERN:
        return true;
      default:
        return false;
    }
  }

  public boolean isValidAssignmentTarget() {
    ParseTree parseTree = this;
    while (parseTree.type == ParseTreeType.PAREN_EXPRESSION) {
      parseTree = parseTree.asParenExpression().expression;
    }

    switch(parseTree.type) {
      case IDENTIFIER_EXPRESSION:
      case MEMBER_EXPRESSION:
      case MEMBER_LOOKUP_EXPRESSION:
      case ARRAY_PATTERN:
      case OBJECT_PATTERN:
      case DEFAULT_PARAMETER:
        return true;
      default:
        return false;
    }
  }

  public boolean isRestParameter() {
    return this.type == ParseTreeType.ITER_REST;
  }

  @Override
  public String toString() {
    return type + "@" + location;
  }
}
