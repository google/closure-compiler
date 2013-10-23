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

/**
 * The types of concrete parse trees.
 *
 * The name of the ParseTreeType must match the name of the class that it applies to.
 * For example the DerivedTree class should use ParseTreeType.DERIVED.
 */
public enum ParseTreeType {
  PROGRAM,
  FUNCTION_DECLARATION,
  BLOCK,
  VARIABLE_STATEMENT,
  VARIABLE_DECLARATION,
  EMPTY_STATEMENT,
  EXPRESSION_STATEMENT,
  IF_STATEMENT,
  DO_WHILE_STATEMENT,
  WHILE_STATEMENT,
  FOR_IN_STATEMENT,
  FOR_STATEMENT,
  VARIABLE_DECLARATION_LIST,
  CONTINUE_STATEMENT,
  BREAK_STATEMENT,
  RETURN_STATEMENT,
  WITH_STATEMENT,
  CASE_CLAUSE,
  DEFAULT_CLAUSE,
  SWITCH_STATEMENT,
  LABELLED_STATEMENT,
  THROW_STATEMENT,
  CATCH,
  TRY_STATEMENT,
  DEBUGGER_STATEMENT,
  THIS_EXPRESSION,
  IDENTIFIER_EXPRESSION,
  LITERAL_EXPRESSION,
  ARRAY_LITERAL_EXPRESSION,
  OBJECT_LITERAL_EXPRESSION,
  GET_ACCESSOR,
  SET_ACCESSOR,
  PROPERTY_NAME_ASSIGNMENT,
  MISSING_PRIMARY_EXPRESSION,
  COMMA_EXPRESSION,
  BINARY_OPERATOR,
  CONDITIONAL_EXPRESSION,
  UNARY_EXPRESSION,
  POSTFIX_EXPRESSION,
  MEMBER_EXPRESSION,
  NEW_EXPRESSION,
  ARGUMENT_LIST,
  CALL_EXPRESSION,
  CLASS_DECLARATION,
  MEMBER_LOOKUP_EXPRESSION,
  PAREN_EXPRESSION,
  FINALLY,
  TRAIT_DECLARATION,
  MIXIN,
  MIXIN_RESOLVE,
  MIXIN_RESOLVE_LIST,
  FIELD_DECLARATION,
  REQUIRES_MEMBER,
  SUPER_EXPRESSION,
  ARRAY_PATTERN,
  SPREAD_PATTERN_ELEMENT,
  OBJECT_PATTERN,
  OBJECT_PATTERN_FIELD,
  FORMAL_PARAMETER_LIST,
  SPREAD_EXPRESSION,
  NULL,
  CLASS_EXPRESSION,
  REST_PARAMETER,
  MODULE_DEFINITION,
  EXPORT_DECLARATION,
  IMPORT_SPECIFIER,
  IMPORT_PATH,
  IMPORT_DECLARATION,
  FOR_EACH_STATEMENT,
  YIELD_STATEMENT,
  AWAIT_STATEMENT,
  DEFAULT_PARAMETER,
}
