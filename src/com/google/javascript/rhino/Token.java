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
     * Token types.
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

        // ES6
        ARRAY_PATTERN  = 156, // destructuring patterns
        OBJECT_PATTERN = 157,

        CLASS          = 158, // classes
        CLASS_MEMBERS  = 159, // class member container
        MEMBER_DEF     = 160,
        SUPER          = 161,

        LET            = 162, // block scoped vars

        FOR_OF         = 163, // for-of

        YIELD          = 164, // generators

        IMPORT         = 165, // modules
        IMPORT_SPECS   = 166,
        IMPORT_SPEC    = 167,
        IMPORT_STAR    = 168, // "* as name", called NameSpaceImport in the spec.
        EXPORT         = 169,
        EXPORT_SPECS   = 170,
        EXPORT_SPEC    = 171,
        MODULE         = 172,

        REST           = 173, // "..." in formal parameters, or an array pattern.
        SPREAD         = 174, // "..." in a call expression, or an array literal.

        COMPUTED_PROP  = 175,

        TEMPLATELIT     = 176, // template literal
        TEMPLATELIT_SUB = 177, // template literal substitution

        DEFAULT_VALUE   = 178, // Formal parameter or destructuring element
                               // with a default value

        // Used by type declaration ASTs
        STRING_TYPE        = 200,
        BOOLEAN_TYPE       = 201,
        NUMBER_TYPE        = 202,
        FUNCTION_TYPE      = 203,
        PARAMETERIZED_TYPE = 204,
        UNION_TYPE         = 205,
        ANY_TYPE           = 206,
        UNKNOWN_TYPE       = 207,
        NULL_TYPE          = 208,
        VOID_TYPE          = 209,
        REST_PARAMETER_TYPE = 210,
        NAMED_TYPE         = 211,
        OPTIONAL_PARAMETER = 212,
        RECORD_TYPE        = 213,
        UNDEFINED_TYPE     = 214,

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
        COLON          = 310,

        // Token Types to use for internal bookkeeping,
        // an AST is invalid while these are present.
        PLACEHOLDER1   = 1001,
        PLACEHOLDER2   = 1002;

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
          case TEMPLATELIT:      return "TEMPLATELIT";
          case TEMPLATELIT_SUB:    return "TEMPLATELIT_SUB";
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
          case STRING_TYPE:     return "STRING_TYPE";
          case ANY_TYPE:        return "ANY_TYPE";
          case UNKNOWN_TYPE:    return "UNKNOWN_TYPE";
          case NULL_TYPE:       return "NULL_TYPE";
          case VOID_TYPE:       return "VOID_TYPE";
          case BOOLEAN_TYPE:       return "BOOLEAN_TYPE";
          case NUMBER_TYPE:        return "NUMBER_TYPE";
          case PARAMETERIZED_TYPE: return "PARAMETERIZED_TYPE";
          case UNION_TYPE:         return "UNION_TYPE";
          case FUNCTION_TYPE:      return "FUNCTION_TYPE";
          case REST_PARAMETER_TYPE: return "REST_PARAMETER_TYPE";
          case NAMED_TYPE:         return "NAMED_TYPE";
          case OPTIONAL_PARAMETER: return "OPTIONAL_PARAMETER";
          case RECORD_TYPE:        return "RECORD_TYPE";
          case UNDEFINED_TYPE:     return "UNDEFINED_TYPE";

          case ARRAY_PATTERN:   return "ARRAY_PATTERN";
          case OBJECT_PATTERN:  return "OBJECT_PATTERN";
          case CLASS:           return "CLASS";
          case CLASS_MEMBERS:   return "CLASS_MEMBERS";
          case MEMBER_DEF:      return "MEMBER_DEF";
          case SUPER:           return "SUPER";
          case LET:             return "LET";
          case FOR_OF:          return "FOR_OF";
          case YIELD:           return "YIELD";
          case IMPORT:          return "IMPORT";
          case IMPORT_SPECS:    return "IMPORT_SPECS";
          case IMPORT_SPEC:     return "IMPORT_SPEC";
          case IMPORT_STAR:     return "IMPORT_STAR";
          case EXPORT:          return "EXPORT";
          case EXPORT_SPECS:    return "EXPORT_SPECS";
          case EXPORT_SPEC:     return "EXPORT_SPEC";
          case MODULE:          return "MODULE";
          case REST:            return "REST";
          case SPREAD:          return "SPREAD";
          case COMPUTED_PROP:   return "COMPUTED_PROP";
          case DEFAULT_VALUE:   return "DEFAULT_VALUE";

          case PLACEHOLDER1:        return "PLACEHOLDER1";
          case PLACEHOLDER2:        return "PLACEHOLDER2";
        }

        // Token without name
        throw new IllegalStateException("No name defined for " + token);
    }

  /** If the arity isn't always the same, this function returns -1 */
  public static int arity(int token) {
    switch (token) {
      case ERROR:           return -1;
      case RETURN:          return -1;
      case BITOR:           return 2;
      case BITXOR:          return 2;
      case BITAND:          return 2;
      case EQ:              return 2;
      case NE:              return 2;
      case LT:              return 2;
      case LE:              return 2;
      case GT:              return 2;
      case GE:              return 2;
      case LSH:             return 2;
      case RSH:             return 2;
      case URSH:            return 2;
      case ADD:             return 2;
      case SUB:             return 2;
      case MUL:             return 2;
      case DIV:             return 2;
      case MOD:             return 2;
      case NOT:             return 1;
      case BITNOT:          return 1;
      case POS:             return 1;
      case NEG:             return 1;
      case NEW:             return -1;
      case DELPROP:         return 1;
      case TYPEOF:          return 1;
      case GETPROP:         return 2;
      case GETELEM:         return 2;
      case CALL:            return -1;
      case NAME:            return 0;
      case LABEL_NAME:      return 0;
      case NUMBER:          return 0;
      case STRING:          return 0;
      case STRING_KEY:      return -1;
      case NULL:            return 0;
      case THIS:            return 0;
      case FALSE:           return 0;
      case TRUE:            return 0;
      case SHEQ:            return 2;
      case SHNE:            return 2;
      case REGEXP:          return -1;
      case THROW:           return 1;
      case IN:              return 2;
      case INSTANCEOF:      return 2;
      case ARRAYLIT:        return -1;
      case OBJECTLIT:       return -1;
      case TEMPLATELIT:     return -1;
      case TEMPLATELIT_SUB: return 1;
      case TRY:             return -1;
      case CLASS:           return 3;
      case MEMBER_DEF:      return 1;
      case PARAM_LIST:      return -1;
      case DEFAULT_VALUE:   return 2;
      case COMMA:           return 2;
      case ASSIGN:          return 2;
      case ASSIGN_BITOR:    return 2;
      case ASSIGN_BITXOR:   return 2;
      case ASSIGN_BITAND:   return 2;
      case ASSIGN_LSH:      return 2;
      case ASSIGN_RSH:      return 2;
      case ASSIGN_URSH:     return 2;
      case ASSIGN_ADD:      return 2;
      case ASSIGN_SUB:      return 2;
      case ASSIGN_MUL:      return 2;
      case ASSIGN_DIV:      return 2;
      case ASSIGN_MOD:      return 2;
      case HOOK:            return 3;
      case OR:              return 2;
      case AND:             return 2;
      case INC:             return 1;
      case DEC:             return 1;
      case FUNCTION:        return 3;
      case IF:              return -1;
      case SWITCH:          return -1;
      case CASE:            return 2;
      case DEFAULT_CASE:    return 1;
      case WHILE:           return 2;
      case DO:              return 2;
      case FOR:             return -1;
      case FOR_OF:          return 3;
      case BREAK:           return -1;
      case CONTINUE:        return -1;
      case VAR:             return -1;
      case WITH:            return 2;
      case CATCH:           return 2;
      case EMPTY:           return 0;
      case BLOCK:           return -1;
      case LABEL:           return 2;
      case EXPR_RESULT:     return 1;
      case SCRIPT:          return -1;
      case GETTER_DEF:      return 1;
      case SETTER_DEF:      return 1;
      case CONST:           return -1;
      case DEBUGGER:        return -1;
      case CAST:            return 1;
      case ANNOTATION:      return -1;
      case PIPE:            return -1;
      case STAR:            return -1;
      case EOC:             return -1;
      case QMARK:           return -1;
      case ELLIPSIS:        return -1;
      case REST:            return 0;
      case SPREAD:          return 1;
      case BANG:            return -1;
      case VOID:            return 1;
      case EQUALS:          return -1;
      case LB:              return -1;
      case LC:              return -1;
      case COLON:           return -1;
      case COMPUTED_PROP:   return 2;
      case IMPORT:          return 3;
      case IMPORT_STAR:     return 0;
      case YIELD:           return -1;
    }
    throw new IllegalStateException(
        "No arity defined for " + Token.name(token));
  }
}
