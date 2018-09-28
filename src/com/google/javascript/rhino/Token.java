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
 * It is based on the C source files jsscan.c and jsscan.h
 * in the jsref package.
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
    NAME,
    NUMBER,
    STRING,
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

    HOOK, // conditional (?:)
    OR, // logical or (||)
    AND, // logical and (&&)
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

    REST, // "..." in formal parameters, or an array pattern.
    SPREAD, // "..." in a call expression, or an array literal.

    COMPUTED_PROP,

    TAGGED_TEMPLATELIT, // tagged template literal, e.g. foo`bar`
    TEMPLATELIT, // template literal
    TEMPLATELIT_SUB, // template literal substitution
    TEMPLATELIT_STRING, // template literal string

    DEFAULT_VALUE, // Formal parameter or destructuring element with a default value
    NEW_TARGET, // new.target

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
    ELLIPSIS,
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
    switch (token) {
      case ANNOTATION:
      case ARRAYLIT:
      case BANG:
      case BLOCK:
      case ROOT:
      case BREAK:
      case CALL:
      case COLON:
      case CONST:
      case CONTINUE:
      case DEBUGGER:
      case ELLIPSIS:
      case EOC:
      case EQUALS:
      case FOR:
      case IF:
      case LB:
      case LC:
      case NEW:
      case OBJECTLIT:
      case PARAM_LIST:
      case PIPE:
      case QMARK:
      case REGEXP:
      case RETURN:
      case SCRIPT:
      case STAR:
      case STRING_KEY:
      case SWITCH:
      case TEMPLATELIT:
      case TRY:
      case VAR:
      case YIELD:
        return -1;
      case EMPTY:
      case FALSE:
      case IMPORT_STAR:
      case LABEL_NAME:
      case MEMBER_VARIABLE_DEF:
      case NAME:
      case NULL:
      case NUMBER:
      case STRING:
      case TEMPLATELIT_STRING:
      case THIS:
      case TRUE:
        return 0;
      case BITNOT:
      case CALL_SIGNATURE:
      case CAST:
      case DEC:
      case DEFAULT_CASE:
      case DELPROP:
      case EXPR_RESULT:
      case GETTER_DEF:
      case INC:
      case INDEX_SIGNATURE:
      case MEMBER_FUNCTION_DEF:
      case NAMED_TYPE:
      case NEG:
      case NOT:
      case POS:
      case REST:
      case SETTER_DEF:
      case SPREAD:
      case TEMPLATELIT_SUB:
      case THROW:
      case TYPEOF:
      case TYPE_ALIAS:
      case VOID:
        return 1;
      case ADD:
      case AND:
      case ASSIGN:
      case ASSIGN_ADD:
      case ASSIGN_BITAND:
      case ASSIGN_BITOR:
      case ASSIGN_BITXOR:
      case ASSIGN_DIV:
      case ASSIGN_LSH:
      case ASSIGN_MOD:
      case ASSIGN_MUL:
      case ASSIGN_EXPONENT:
      case ASSIGN_RSH:
      case ASSIGN_SUB:
      case ASSIGN_URSH:
      case BITAND:
      case BITOR:
      case BITXOR:
      case CASE:
      case CATCH:
      case COMMA:
      case COMPUTED_PROP:
      case DEFAULT_VALUE:
      case DIV:
      case DO:
      case ENUM:
      case EQ:
      case EXPONENT:
      case GE:
      case GETELEM:
      case GETPROP:
      case GT:
      case IN:
      case INSTANCEOF:
      case LABEL:
      case LE:
      case LSH:
      case LT:
      case MOD:
      case MUL:
      case NAMESPACE:
      case NE:
      case OR:
      case RSH:
      case SHEQ:
      case SHNE:
      case SUB:
      case TAGGED_TEMPLATELIT:
      case URSH:
      case WHILE:
      case WITH:
        return 2;
      case CLASS:
      case FOR_IN:
      case FOR_OF:
      case FOR_AWAIT_OF:
      case FUNCTION:
      case HOOK:
      case IMPORT:
      case INTERFACE:
        return 3;
      default:
        throw new IllegalStateException("No arity defined for " + token);
    }
  }
}
