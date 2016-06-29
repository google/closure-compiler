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
    FOR, // for keyword
    BREAK, // break keyword
    CONTINUE, // continue keyword
    VAR, // var keyword
    WITH, // with keyword
    CATCH, // catch keyword
    VOID, // void keyword

    EMPTY,

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
    QMARK,
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
    PLACEHOLDER2;

  @Deprecated
  public static String name(Token token) {
    return token.toString();
  }

  /** If the arity isn't always the same, this function returns -1 */
  public static int arity(Token token) {
    switch (token) {
      case RETURN:
      case NEW:
      case CALL:
      case STRING_KEY:
      case REGEXP:
      case ARRAYLIT:
      case OBJECTLIT:
      case TEMPLATELIT:
      case TRY:
      case PARAM_LIST:
      case IF:
      case SWITCH:
      case FOR:
      case BREAK:
      case CONTINUE:
      case VAR:
      case BLOCK:
      case SCRIPT:
      case CONST:
      case DEBUGGER:
      case ANNOTATION:
      case PIPE:
      case STAR:
      case EOC:
      case QMARK:
      case ELLIPSIS:
      case BANG:
      case EQUALS:
      case LB:
      case LC:
      case COLON:
      case YIELD:
        return -1;
      case NAME:
      case LABEL_NAME:
      case NUMBER:
      case STRING:
      case NULL:
      case THIS:
      case FALSE:
      case TRUE:
      case EMPTY:
      case IMPORT_STAR:
      case MEMBER_VARIABLE_DEF:
        return 0;
      case NOT:
      case BITNOT:
      case POS:
      case NEG:
      case DELPROP:
      case TYPEOF:
      case THROW:
      case TEMPLATELIT_SUB:
      case MEMBER_FUNCTION_DEF:
      case INC:
      case DEC:
      case DEFAULT_CASE:
      case EXPR_RESULT:
      case GETTER_DEF:
      case SETTER_DEF:
      case CAST:
      case SPREAD:
      case VOID:
      case NAMED_TYPE:
      case TYPE_ALIAS:
      case INDEX_SIGNATURE:
      case CALL_SIGNATURE:
      case REST:
        return 1;
      case BITOR:
      case BITXOR:
      case BITAND:
      case EQ:
      case NE:
      case LT:
      case LE:
      case GT:
      case GE:
      case LSH:
      case RSH:
      case URSH:
      case ADD:
      case SUB:
      case MUL:
      case DIV:
      case MOD:
      case GETPROP:
      case GETELEM:
      case SHEQ:
      case SHNE:
      case IN:
      case INSTANCEOF:
      case TAGGED_TEMPLATELIT:
      case DEFAULT_VALUE:
      case COMMA:
      case ASSIGN:
      case ASSIGN_BITOR:
      case ASSIGN_BITXOR:
      case ASSIGN_BITAND:
      case ASSIGN_LSH:
      case ASSIGN_RSH:
      case ASSIGN_URSH:
      case ASSIGN_ADD:
      case ASSIGN_SUB:
      case ASSIGN_MUL:
      case ASSIGN_DIV:
      case ASSIGN_MOD:
      case OR:
      case AND:
      case CASE:
      case WHILE:
      case DO:
      case WITH:
      case CATCH:
      case LABEL:
      case COMPUTED_PROP:
      case ENUM:
      case NAMESPACE:
        return 2;
      case CLASS:
      case HOOK:
      case FUNCTION:
      case FOR_OF:
      case IMPORT:
      case INTERFACE:
        return 3;
      default:
        throw new IllegalStateException("No arity defined for " + token);
    }
  }
}
