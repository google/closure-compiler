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
 *   Roger Lawrence
 *   Mike McCabe
 *   Igor Bukanov
 *   Milen Nankov
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

/**
 * This class implements the JavaScript scanner.
 *
 * <p>It is based on the C source files jsscan.c and jsscan.h in the jsref package.
 *
 */
public enum Token {
  RETURN,
  BITOR,
  BITXOR,
  BITAND,
  EQ,
  NE,
  LT,
  LE,
  GT,
  GE,
  LSH,
  RSH,
  URSH,
  ADD,
  SUB,
  MUL,
  DIV,
  MOD,
  EXPONENT,
  NOT,
  BITNOT,
  POS,
  NEG,
  NEW,
  DELPROP,
  TYPEOF,
  GETPROP,
  GETELEM,
  CALL,

  // Part of optional chain (?.)
  OPTCHAIN_GETPROP,
  OPTCHAIN_GETELEM,
  OPTCHAIN_CALL,

  NAME,
  NUMBER,
  BIGINT,
  STRINGLIT,
  NULL,
  THIS,
  FALSE,
  TRUE,
  SHEQ, // shallow equality (===)
  SHNE, // shallow inequality (!==)
  REGEXP,
  THROW,
  IN,
  INSTANCEOF,
  ARRAYLIT, // array literal
  OBJECTLIT, // object literal

  TRY,
  PARAM_LIST,
  COMMA, // comma operator

  ASSIGN, // simple assignment  (=)
  ASSIGN_BITOR, // |=
  ASSIGN_BITXOR, // ^=
  ASSIGN_BITAND, // &=
  ASSIGN_LSH, // <<=
  ASSIGN_RSH, // >>=
  ASSIGN_URSH, // >>>=
  ASSIGN_ADD, // +=
  ASSIGN_SUB, // -=
  ASSIGN_MUL, // *=
  ASSIGN_DIV, // /=
  ASSIGN_MOD, // %=
  ASSIGN_EXPONENT, // **=

  ASSIGN_OR, // logical or assignment (||=)
  ASSIGN_AND, // logical and assignment (&&=)
  ASSIGN_COALESCE, // logical nullish coalesce assignment (??=)

  HOOK, // conditional (?:)
  OR, // logical or (||)
  AND, // logical and (&&)
  COALESCE, // Nullish coalesce (??)
  INC, // increment (++)
  DEC, // decrement (--)
  FUNCTION, // function keyword
  IF, // if keyword
  SWITCH, // switch keyword
  CASE, // case keyword
  DEFAULT_CASE, // default keyword
  WHILE, // while keyword
  DO, // do keyword
  FOR, // for(;;) statement
  FOR_IN, // for-in
  BREAK, // break keyword
  CONTINUE, // continue keyword
  VAR, // var keyword
  WITH, // with keyword
  CATCH, // catch keyword
  VOID, // void keyword

  EMPTY,

  ROOT, // Used only for the 3 root nodes of the AST: externsRoot, jsRoot, and externsAndJsRoot
  BLOCK, // statement block
  SWITCH_BODY, // holds case statements within a switch
  LABEL, // label
  EXPR_RESULT, // expression statement in scripts
  SCRIPT, // top-level node for entire script

  GETTER_DEF,
  SETTER_DEF,

  CONST, // JS 1.5 const keyword
  DEBUGGER,

  // JSCompiler introduced tokens
  LABEL_NAME,
  STRING_KEY, // object literal key
  CAST,

  // ES6
  ARRAY_PATTERN, // destructuring patterns
  OBJECT_PATTERN,
  DESTRUCTURING_LHS, // The node inside a var/let/const with a destructuring LHS

  CLASS, // classes
  CLASS_MEMBERS, // class member container
  MEMBER_FUNCTION_DEF,
  MEMBER_FIELD_DEF, // class field general case
  COMPUTED_FIELD_DEF, // class field computed property case
  SUPER,

  LET, // block scoped vars

  FOR_OF, // for-of
  FOR_AWAIT_OF, // for-await-of

  YIELD, // generators

  AWAIT, // async functions

  IMPORT, // modules
  IMPORT_SPECS,
  IMPORT_SPEC,
  IMPORT_STAR, // "* as name", called NameSpaceImport in the spec.
  EXPORT,
  EXPORT_SPECS,
  EXPORT_SPEC,
  MODULE_BODY,
  DYNAMIC_IMPORT,

  ITER_REST, // Rests that use the iterator protocol.
  OBJECT_REST, // Rests that get object properties.
  ITER_SPREAD, // Spreads that use the iterator protocol.
  OBJECT_SPREAD, // Spreads that get object properties.

  COMPUTED_PROP,

  TAGGED_TEMPLATELIT, // tagged template literal, e.g. foo`bar`
  TEMPLATELIT, // template literal, e.g: `bar`
  TEMPLATELIT_SUB, // template literal substitution
  TEMPLATELIT_STRING, // template literal string

  DEFAULT_VALUE, // Formal parameter or destructuring element with a default value
  NEW_TARGET, // new.target
  IMPORT_META, // import.meta

  // Used by type declaration ASTs
  STRING_TYPE,
  BOOLEAN_TYPE,
  NUMBER_TYPE,
  FUNCTION_TYPE,
  PARAMETERIZED_TYPE,
  UNION_TYPE,
  ANY_TYPE,
  NULLABLE_TYPE,
  VOID_TYPE,
  REST_PARAMETER_TYPE,
  NAMED_TYPE,
  OPTIONAL_PARAMETER,
  RECORD_TYPE,
  UNDEFINED_TYPE,
  ARRAY_TYPE,
  GENERIC_TYPE,
  GENERIC_TYPE_LIST,

  // JSDoc-only tokens
  ANNOTATION,
  PIPE,
  STAR,
  EOC,
  QMARK, // type is nullable or unknown
  BANG,
  EQUALS,
  LB, // left brackets
  LC, // left curly braces
  COLON,

  // TypeScript
  INTERFACE,
  INTERFACE_EXTENDS,
  INTERFACE_MEMBERS,
  ENUM,
  ENUM_MEMBERS,
  IMPLEMENTS,
  TYPE_ALIAS,
  DECLARE,
  MEMBER_VARIABLE_DEF,
  INDEX_SIGNATURE,
  CALL_SIGNATURE,
  NAMESPACE,
  NAMESPACE_ELEMENTS,

  // Tokens to use for internal bookkeeping,
  // an AST is invalid while these are present.
  PLACEHOLDER1,
  PLACEHOLDER2,
  PLACEHOLDER3;

  /** If the arity isn't always the same, this function returns -1 */
  public static int arity(Token token) {
    return switch (token) {
      case ANNOTATION,
          ARRAYLIT,
          BANG,
          BLOCK,
          ROOT,
          BREAK,
          CALL,
          OPTCHAIN_CALL,
          COLON,
          CONST,
          CONTINUE,
          DEBUGGER,
          EOC,
          EQUALS,
          FOR,
          IF,
          LB,
          LC,
          NEW,
          OBJECTLIT,
          PARAM_LIST,
          PIPE,
          QMARK,
          REGEXP,
          RETURN,
          SCRIPT,
          STAR,
          STRING_KEY,
          SWITCH_BODY,
          TEMPLATELIT,
          TRY,
          VAR,
          YIELD,
          MEMBER_FIELD_DEF,
          COMPUTED_FIELD_DEF ->
          -1;
      case EMPTY,
          FALSE,
          IMPORT_STAR,
          LABEL_NAME,
          MEMBER_VARIABLE_DEF,
          NAME,
          NULL,
          NUMBER,
          BIGINT,
          STRINGLIT,
          TEMPLATELIT_STRING,
          THIS,
          TRUE ->
          0;
      case AWAIT,
          BITNOT,
          CALL_SIGNATURE,
          CAST,
          DEC,
          DEFAULT_CASE,
          DELPROP,
          EXPR_RESULT,
          GETPROP,
          GETTER_DEF,
          INC,
          INDEX_SIGNATURE,
          ITER_REST,
          ITER_SPREAD,
          MEMBER_FUNCTION_DEF,
          NAMED_TYPE,
          NEG,
          NOT,
          OBJECT_REST,
          OBJECT_SPREAD,
          OPTCHAIN_GETPROP,
          POS,
          SETTER_DEF,
          TEMPLATELIT_SUB,
          THROW,
          TYPEOF,
          TYPE_ALIAS,
          VOID ->
          1;
      case ADD,
          AND,
          ASSIGN,
          ASSIGN_ADD,
          ASSIGN_BITAND,
          ASSIGN_BITOR,
          ASSIGN_BITXOR,
          ASSIGN_DIV,
          ASSIGN_LSH,
          ASSIGN_MOD,
          ASSIGN_MUL,
          ASSIGN_EXPONENT,
          ASSIGN_RSH,
          ASSIGN_SUB,
          ASSIGN_URSH,
          ASSIGN_OR,
          ASSIGN_AND,
          ASSIGN_COALESCE,
          BITAND,
          BITOR,
          BITXOR,
          CASE,
          COALESCE,
          CATCH,
          COMMA,
          COMPUTED_PROP,
          DEFAULT_VALUE,
          DIV,
          DO,
          ENUM,
          EQ,
          EXPONENT,
          GE,
          GETELEM,
          OPTCHAIN_GETELEM,
          GT,
          IN,
          INSTANCEOF,
          LABEL,
          LE,
          LSH,
          LT,
          MOD,
          MUL,
          NAMESPACE,
          NE,
          OR,
          RSH,
          SWITCH,
          SHEQ,
          SHNE,
          SUB,
          TAGGED_TEMPLATELIT,
          URSH,
          WHILE,
          WITH ->
          2;
      case CLASS, FOR_IN, FOR_OF, FOR_AWAIT_OF, FUNCTION, HOOK, IMPORT, INTERFACE -> 3;
      default -> throw new IllegalStateException("No arity defined for " + token);
    };
  }
}
