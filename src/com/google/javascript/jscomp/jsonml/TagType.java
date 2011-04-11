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

/**
 * List of types allowed for JsonML elements.
 *
 * @author dhans@google.com (Daniel Hans)
 */
public enum TagType {

  // *Expr types
  ArrayExpr,
  AssignExpr,
  BinaryExpr,
  CallExpr,
  ConditionalExpr,
  CountExpr,
  DeleteExpr,
  EvalExpr,
  FunctionExpr,
  IdExpr,
  InvokeExpr,
  LiteralExpr,
  LogicalAndExpr,
  LogicalOrExpr,
  MemberExpr,
  NewExpr,
  ObjectExpr,
  RegExpExpr,
  ThisExpr,
  TypeofExpr,
  UnaryExpr,

  // *Stmt types
  BlockStmt,
  BreakStmt,
  ContinueStmt,
  DebuggerStmt,
  DoWhileStmt,
  EmptyStmt,
  ForInStmt,
  ForStmt,
  IfStmt,
  LabelledStmt,
  ReturnStmt,
  SwitchStmt,
  ThrowStmt,
  TryStmt,
  WhileStmt,
  WithStmt,

  // *Decl types
  FunctionDecl,
  ParamDecl,
  PrologueDecl,  // TODO
  VarDecl,

  // *Prop types
  DataProp,
  GetterProp,
  SetterProp,

  // *Patt types
  IdPatt,
  InitPatt,

  // *Case types
  Case,
  DefaultCase,

  // CatchClause type
  CatchClause,

  // Empty type
  Empty,

  // Program type (root)
  Program,
}