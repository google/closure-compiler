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

public class Token {

    /**
     * Token types.  These values correspond to JSTokenType values in
     * jsscan.c.
     */
    public final static int
        ERROR          = -1,

        RETURN         = 4,
        BITOR          = 9,
        BITXOR         = 10,
        BITAND         = 11,
        EQ             = 12,
        NE             = 13,
        LT             = 14,
        LE             = 15,
        GT             = 16,
        GE             = 17,
        LSH            = 18,
        RSH            = 19,
        URSH           = 20,
        ADD            = 21,
        SUB            = 22,
        MUL            = 23,
        DIV            = 24,
        MOD            = 25,
        NOT            = 26,
        BITNOT         = 27,
        POS            = 28,
        NEG            = 29,
        NEW            = 30,
        DELPROP        = 31,
        TYPEOF         = 32,
        GETPROP        = 33,
        GETELEM        = 35,
        CALL           = 37,
        NAME           = 38,
        NUMBER         = 39,
        STRING         = 40,
        NULL           = 41,
        THIS           = 42,
        FALSE          = 43,
        TRUE           = 44,
        SHEQ           = 45,   // shallow equality (===)
        SHNE           = 46,   // shallow inequality (!==)
        REGEXP         = 47,
        THROW          = 49,
        IN             = 51,
        INSTANCEOF     = 52,
        ARRAYLIT       = 63, // array literal
        OBJECTLIT      = 64, // object literal

        TRY            = 77,
        PARAM_LIST     = 83,
        COMMA          = 85,  // comma operator

        ASSIGN         = 86,  // simple assignment  (=)
        ASSIGN_BITOR   = 87,  // |=
        ASSIGN_BITXOR  = 88,  // ^=
        ASSIGN_BITAND  = 89,  // &=
        ASSIGN_LSH     = 90,  // <<=
        ASSIGN_RSH     = 91,  // >>=
        ASSIGN_URSH    = 92,  // >>>=
        ASSIGN_ADD     = 93,  // +=
        ASSIGN_SUB     = 94,  // -=
        ASSIGN_MUL     = 95,  // *=
        ASSIGN_DIV     = 96,  // /=
        ASSIGN_MOD     = 97,  // %=

        HOOK           = 98,  // conditional (?:)
        OR             = 100, // logical or (||)
        AND            = 101, // logical and (&&)
        INC            = 102, // increment (++)
        DEC            = 103, // decrement (--)
        FUNCTION       = 105, // function keyword
        IF             = 108, // if keyword
        SWITCH         = 110, // switch keyword
        CASE           = 111, // case keyword
        DEFAULT_CASE   = 112, // default keyword
        WHILE          = 113, // while keyword
        DO             = 114, // do keyword
        FOR            = 115, // for keyword
        BREAK          = 116, // break keyword
        CONTINUE       = 117, // continue keyword
        VAR            = 118, // var keyword
        WITH           = 119, // with keyword
        CATCH          = 120, // catch keyword
        VOID           = 122, // void keyword

        EMPTY          = 124,

        BLOCK          = 125, // statement block
        LABEL          = 126, // label
        EXPR_RESULT    = 130, // expression statement in scripts
        SCRIPT         = 132, // top-level node for entire script

        GETTER_DEF     = 147,
        SETTER_DEF     = 148,

        CONST          = 149,  // JS 1.5 const keyword
        DEBUGGER       = 152,

        // JSCompiler introduced tokens
        LABEL_NAME     = 153,
        STRING_KEY     = 154, // object literal key
        CAST           = 155,

        // JSDoc-only tokens
        ANNOTATION     = 300,
        PIPE           = 301,
        STAR           = 302,
        EOC            = 303,
        QMARK          = 304,
        ELLIPSIS       = 305,
        BANG           = 306,
        EQUALS         = 307,
        LB             = 308,  // left brackets
        LC             = 309,  // left curly braces
        COLON          = 310;

    // Transitional definitions
    // TODO(johnlenz): remove these
    public final static int
         DEFAULT        = DEFAULT_CASE,
         GET            = GETTER_DEF,
         LP             = PARAM_LIST,
         SET            = SETTER_DEF;

  public static String name(int token) {
        switch (token) {
          case ERROR:           return "ERROR";
          case RETURN:          return "RETURN";
          case BITOR:           return "BITOR";
          case BITXOR:          return "BITXOR";
          case BITAND:          return "BITAND";
          case EQ:              return "EQ";
          case NE:              return "NE";
          case LT:              return "LT";
          case LE:              return "LE";
          case GT:              return "GT";
          case GE:              return "GE";
          case LSH:             return "LSH";
          case RSH:             return "RSH";
          case URSH:            return "URSH";
          case ADD:             return "ADD";
          case SUB:             return "SUB";
          case MUL:             return "MUL";
          case DIV:             return "DIV";
          case MOD:             return "MOD";
          case NOT:             return "NOT";
          case BITNOT:          return "BITNOT";
          case POS:             return "POS";
          case NEG:             return "NEG";
          case NEW:             return "NEW";
          case DELPROP:         return "DELPROP";
          case TYPEOF:          return "TYPEOF";
          case GETPROP:         return "GETPROP";
          case GETELEM:         return "GETELEM";
          case CALL:            return "CALL";
          case NAME:            return "NAME";
          case LABEL_NAME:      return "LABEL_NAME";
          case NUMBER:          return "NUMBER";
          case STRING:          return "STRING";
          case STRING_KEY:      return "STRING_KEY";
          case NULL:            return "NULL";
          case THIS:            return "THIS";
          case FALSE:           return "FALSE";
          case TRUE:            return "TRUE";
          case SHEQ:            return "SHEQ";
          case SHNE:            return "SHNE";
          case REGEXP:          return "REGEXP";
          case THROW:           return "THROW";
          case IN:              return "IN";
          case INSTANCEOF:      return "INSTANCEOF";
          case ARRAYLIT:        return "ARRAYLIT";
          case OBJECTLIT:       return "OBJECTLIT";
          case TRY:             return "TRY";
          case PARAM_LIST:      return "PARAM_LIST";
          case COMMA:           return "COMMA";
          case ASSIGN:          return "ASSIGN";
          case ASSIGN_BITOR:    return "ASSIGN_BITOR";
          case ASSIGN_BITXOR:   return "ASSIGN_BITXOR";
          case ASSIGN_BITAND:   return "ASSIGN_BITAND";
          case ASSIGN_LSH:      return "ASSIGN_LSH";
          case ASSIGN_RSH:      return "ASSIGN_RSH";
          case ASSIGN_URSH:     return "ASSIGN_URSH";
          case ASSIGN_ADD:      return "ASSIGN_ADD";
          case ASSIGN_SUB:      return "ASSIGN_SUB";
          case ASSIGN_MUL:      return "ASSIGN_MUL";
          case ASSIGN_DIV:      return "ASSIGN_DIV";
          case ASSIGN_MOD:      return "ASSIGN_MOD";
          case HOOK:            return "HOOK";
          case OR:              return "OR";
          case AND:             return "AND";
          case INC:             return "INC";
          case DEC:             return "DEC";
          case FUNCTION:        return "FUNCTION";
          case IF:              return "IF";
          case SWITCH:          return "SWITCH";
          case CASE:            return "CASE";
          case DEFAULT_CASE:    return "DEFAULT_CASE";
          case WHILE:           return "WHILE";
          case DO:              return "DO";
          case FOR:             return "FOR";
          case BREAK:           return "BREAK";
          case CONTINUE:        return "CONTINUE";
          case VAR:             return "VAR";
          case WITH:            return "WITH";
          case CATCH:           return "CATCH";
          case EMPTY:           return "EMPTY";
          case BLOCK:           return "BLOCK";
          case LABEL:           return "LABEL";
          case EXPR_RESULT:     return "EXPR_RESULT";
          case SCRIPT:          return "SCRIPT";
          case GETTER_DEF:      return "GETTER_DEF";
          case SETTER_DEF:      return "SETTER_DEF";
          case CONST:           return "CONST";
          case DEBUGGER:        return "DEBUGGER";
          case CAST:            return "CAST";
          case ANNOTATION:      return "ANNOTATION";
          case PIPE:            return "PIPE";
          case STAR:            return "STAR";
          case EOC:             return "EOC";
          case QMARK:           return "QMARK";
          case ELLIPSIS:        return "ELLIPSIS";
          case BANG:            return "BANG";
          case VOID:            return "VOID";
          case EQUALS:          return "EQUALS";
          case LB:              return "LB";
          case LC:              return "LC";
          case COLON:           return "COLON";
        }

        // Token without name
        throw new IllegalStateException(String.valueOf(token));
    }
}
