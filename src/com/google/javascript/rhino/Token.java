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
   *
   * TODO(bradfordcsmith): Add instructions for assigning new values.
   */
  public enum Kind {
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

      IMPORT, // modules
      IMPORT_SPECS,
      IMPORT_SPEC,
      IMPORT_STAR, // "* as name", called NameSpaceImport in the spec.
      EXPORT,
      EXPORT_SPECS,
      EXPORT_SPEC,

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

      // Token.Kinds to use for internal bookkeeping,
      // an AST is invalid while these are present.
      PLACEHOLDER1,
      PLACEHOLDER2
  }

  /** Deprecated. Remove these after removing references to them */
  public static final Kind
      RETURN = Kind.RETURN,
      BITOR = Kind.BITOR,
      BITXOR = Kind.BITXOR,
      BITAND = Kind.BITAND,
      EQ = Kind.EQ,
      NE = Kind.NE,
      LT = Kind.LT,
      LE = Kind.LE,
      GT = Kind.GT,
      GE = Kind.GE,
      LSH = Kind.LSH,
      RSH = Kind.RSH,
      URSH = Kind.URSH,
      ADD = Kind.ADD,
      SUB = Kind.SUB,
      MUL = Kind.MUL,
      DIV = Kind.DIV,
      MOD = Kind.MOD,
      NOT = Kind.NOT,
      BITNOT = Kind.BITNOT,
      POS = Kind.POS,
      NEG = Kind.NEG,
      NEW = Kind.NEW,
      DELPROP = Kind.DELPROP,
      TYPEOF = Kind.TYPEOF,
      GETPROP = Kind.GETPROP,
      GETELEM = Kind.GETELEM,
      CALL = Kind.CALL,
      NAME = Kind.NAME,
      NUMBER = Kind.NUMBER,
      STRING = Kind.STRING,
      NULL = Kind.NULL,
      THIS = Kind.THIS,
      FALSE = Kind.FALSE,
      TRUE = Kind.TRUE,
      SHEQ = Kind.SHEQ, // shallow equality (===)
      SHNE = Kind.SHNE, // shallow inequality (!==)
      REGEXP = Kind.REGEXP,
      THROW = Kind.THROW,
      IN = Kind.IN,
      INSTANCEOF = Kind.INSTANCEOF,
      ARRAYLIT = Kind.ARRAYLIT, // array literal
      OBJECTLIT = Kind.OBJECTLIT, // object literal

      TRY = Kind.TRY,
      PARAM_LIST = Kind.PARAM_LIST,
      COMMA = Kind.COMMA, // comma operator

      ASSIGN = Kind.ASSIGN, // simple assignment  (=)
      ASSIGN_BITOR = Kind.ASSIGN_BITOR, // |=
      ASSIGN_BITXOR = Kind.ASSIGN_BITXOR, // ^=
      ASSIGN_BITAND = Kind.ASSIGN_BITAND, // &=
      ASSIGN_LSH = Kind.ASSIGN_LSH, // <<=
      ASSIGN_RSH = Kind.ASSIGN_RSH, // >>=
      ASSIGN_URSH = Kind.ASSIGN_URSH, // >>>=
      ASSIGN_ADD = Kind.ASSIGN_ADD, // +=
      ASSIGN_SUB = Kind.ASSIGN_SUB, // -=
      ASSIGN_MUL = Kind.ASSIGN_MUL, // *=
      ASSIGN_DIV = Kind.ASSIGN_DIV, // /=
      ASSIGN_MOD = Kind.ASSIGN_MOD, // %=

      HOOK = Kind.HOOK, // conditional (?:)
      OR = Kind.OR, // logical or (||)
      AND = Kind.AND, // logical and (&&)
      INC = Kind.INC, // increment (++)
      DEC = Kind.DEC, // decrement (--)
      FUNCTION = Kind.FUNCTION, // function keyword
      IF = Kind.IF, // if keyword
      SWITCH = Kind.SWITCH, // switch keyword
      CASE = Kind.CASE, // case keyword
      DEFAULT_CASE = Kind.DEFAULT_CASE, // default keyword
      WHILE = Kind.WHILE, // while keyword
      DO = Kind.DO, // do keyword
      FOR = Kind.FOR, // for keyword
      BREAK = Kind.BREAK, // break keyword
      CONTINUE = Kind.CONTINUE, // continue keyword
      VAR = Kind.VAR, // var keyword
      WITH = Kind.WITH, // with keyword
      CATCH = Kind.CATCH, // catch keyword
      VOID = Kind.VOID, // void keyword

      EMPTY = Kind.EMPTY,

      BLOCK = Kind.BLOCK, // statement block
      LABEL = Kind.LABEL, // label
      EXPR_RESULT = Kind.EXPR_RESULT, // expression statement in scripts
      SCRIPT = Kind.SCRIPT, // top-level node for entire script

      GETTER_DEF = Kind.GETTER_DEF,
      SETTER_DEF = Kind.SETTER_DEF,

      CONST = Kind.CONST, // JS 1.5 const keyword
      DEBUGGER = Kind.DEBUGGER,

      // JSCompiler introduced tokens
      LABEL_NAME = Kind.LABEL_NAME,
      STRING_KEY = Kind.STRING_KEY, // object literal key
      CAST = Kind.CAST,

      // ES6
      ARRAY_PATTERN = Kind.ARRAY_PATTERN, // destructuring patterns
      OBJECT_PATTERN = Kind.OBJECT_PATTERN,
      DESTRUCTURING_LHS = Kind.DESTRUCTURING_LHS,

      CLASS = Kind.CLASS, // classes
      CLASS_MEMBERS = Kind.CLASS_MEMBERS, // class member container
      MEMBER_FUNCTION_DEF = Kind.MEMBER_FUNCTION_DEF,
      SUPER = Kind.SUPER,

      LET = Kind.LET, // block scoped vars

      FOR_OF = Kind.FOR_OF, // for-of

      YIELD = Kind.YIELD, // generators

      IMPORT = Kind.IMPORT, // modules
      IMPORT_SPECS = Kind.IMPORT_SPECS,
      IMPORT_SPEC = Kind.IMPORT_SPEC,
      IMPORT_STAR = Kind.IMPORT_STAR, // "* as name", called NameSpaceImport in the spec.
      EXPORT = Kind.EXPORT,
      EXPORT_SPECS = Kind.EXPORT_SPECS,
      EXPORT_SPEC = Kind.EXPORT_SPEC,

      REST = Kind.REST, // "..." in formal parameters, or an array pattern.
      SPREAD = Kind.SPREAD, // "..." in a call expression, or an array literal.

      COMPUTED_PROP = Kind.COMPUTED_PROP,

      TAGGED_TEMPLATELIT = Kind.TAGGED_TEMPLATELIT, // tagged template literal, e.g. foo`bar`
      TEMPLATELIT = Kind.TEMPLATELIT, // template literal
      TEMPLATELIT_SUB = Kind.TEMPLATELIT_SUB, // template literal substitution

      DEFAULT_VALUE = Kind.DEFAULT_VALUE,
      NEW_TARGET = Kind.NEW_TARGET, // new.target

      // Used by type declaration ASTs
      STRING_TYPE = Kind.STRING_TYPE,
      BOOLEAN_TYPE = Kind.BOOLEAN_TYPE,
      NUMBER_TYPE = Kind.NUMBER_TYPE,
      FUNCTION_TYPE = Kind.FUNCTION_TYPE,
      PARAMETERIZED_TYPE = Kind.PARAMETERIZED_TYPE,
      UNION_TYPE = Kind.UNION_TYPE,
      ANY_TYPE = Kind.ANY_TYPE,
      NULLABLE_TYPE = Kind.NULLABLE_TYPE,
      VOID_TYPE = Kind.VOID_TYPE,
      REST_PARAMETER_TYPE = Kind.REST_PARAMETER_TYPE,
      NAMED_TYPE = Kind.NAMED_TYPE,
      OPTIONAL_PARAMETER = Kind.OPTIONAL_PARAMETER,
      RECORD_TYPE = Kind.RECORD_TYPE,
      UNDEFINED_TYPE = Kind.UNDEFINED_TYPE,
      ARRAY_TYPE = Kind.ARRAY_TYPE,
      GENERIC_TYPE = Kind.GENERIC_TYPE,
      GENERIC_TYPE_LIST = Kind.GENERIC_TYPE_LIST,

      // JSDoc-only tokens
      ANNOTATION = Kind.ANNOTATION,
      PIPE = Kind.PIPE,
      STAR = Kind.STAR,
      EOC = Kind.EOC,
      QMARK = Kind.QMARK,
      ELLIPSIS = Kind.ELLIPSIS,
      BANG = Kind.BANG,
      EQUALS = Kind.EQUALS,
      LB = Kind.LB, // left brackets
      LC = Kind.LC, // left curly braces
      COLON = Kind.COLON,

      // Kind
      INTERFACE = Kind.INTERFACE,
      INTERFACE_EXTENDS = Kind.INTERFACE_EXTENDS,
      INTERFACE_MEMBERS = Kind.INTERFACE_MEMBERS,
      ENUM = Kind.ENUM,
      ENUM_MEMBERS = Kind.ENUM_MEMBERS,
      IMPLEMENTS = Kind.IMPLEMENTS,
      TYPE_ALIAS = Kind.TYPE_ALIAS,
      DECLARE = Kind.DECLARE,
      MEMBER_VARIABLE_DEF = Kind.MEMBER_VARIABLE_DEF,
      INDEX_SIGNATURE = Kind.INDEX_SIGNATURE,
      CALL_SIGNATURE = Kind.CALL_SIGNATURE,
      NAMESPACE = Kind.NAMESPACE,
      NAMESPACE_ELEMENTS = Kind.NAMESPACE_ELEMENTS,

      // Token.Kinds to use for internal bookkeeping,
      // an AST is invalid while these are present.
      PLACEHOLDER1 = Kind.PLACEHOLDER1,
      PLACEHOLDER2 = Kind.PLACEHOLDER2;

  @Deprecated
  public static String name(Kind token) {
    switch (token) {
      case RETURN:
        return "RETURN";
      case BITOR:
        return "BITOR";
      case BITXOR:
        return "BITXOR";
      case BITAND:
        return "BITAND";
      case EQ:
        return "EQ";
      case NE:
        return "NE";
      case LT:
        return "LT";
      case LE:
        return "LE";
      case GT:
        return "GT";
      case GE:
        return "GE";
      case LSH:
        return "LSH";
      case RSH:
        return "RSH";
      case URSH:
        return "URSH";
      case ADD:
        return "ADD";
      case SUB:
        return "SUB";
      case MUL:
        return "MUL";
      case DIV:
        return "DIV";
      case MOD:
        return "MOD";
      case NOT:
        return "NOT";
      case BITNOT:
        return "BITNOT";
      case POS:
        return "POS";
      case NEG:
        return "NEG";
      case NEW:
        return "NEW";
      case DELPROP:
        return "DELPROP";
      case TYPEOF:
        return "TYPEOF";
      case GETPROP:
        return "GETPROP";
      case GETELEM:
        return "GETELEM";
      case CALL:
        return "CALL";
      case NAME:
        return "NAME";
      case LABEL_NAME:
        return "LABEL_NAME";
      case NUMBER:
        return "NUMBER";
      case STRING:
        return "STRING";
      case STRING_KEY:
        return "STRING_KEY";
      case NULL:
        return "NULL";
      case THIS:
        return "THIS";
      case FALSE:
        return "FALSE";
      case TRUE:
        return "TRUE";
      case SHEQ:
        return "SHEQ";
      case SHNE:
        return "SHNE";
      case REGEXP:
        return "REGEXP";
      case THROW:
        return "THROW";
      case IN:
        return "IN";
      case INSTANCEOF:
        return "INSTANCEOF";
      case ARRAYLIT:
        return "ARRAYLIT";
      case OBJECTLIT:
        return "OBJECTLIT";
      case TAGGED_TEMPLATELIT:
        return "TAGGED_TEMPLATELIT";
      case TEMPLATELIT:
        return "TEMPLATELIT";
      case TEMPLATELIT_SUB:
        return "TEMPLATELIT_SUB";
      case TRY:
        return "TRY";
      case PARAM_LIST:
        return "PARAM_LIST";
      case COMMA:
        return "COMMA";
      case ASSIGN:
        return "ASSIGN";
      case ASSIGN_BITOR:
        return "ASSIGN_BITOR";
      case ASSIGN_BITXOR:
        return "ASSIGN_BITXOR";
      case ASSIGN_BITAND:
        return "ASSIGN_BITAND";
      case ASSIGN_LSH:
        return "ASSIGN_LSH";
      case ASSIGN_RSH:
        return "ASSIGN_RSH";
      case ASSIGN_URSH:
        return "ASSIGN_URSH";
      case ASSIGN_ADD:
        return "ASSIGN_ADD";
      case ASSIGN_SUB:
        return "ASSIGN_SUB";
      case ASSIGN_MUL:
        return "ASSIGN_MUL";
      case ASSIGN_DIV:
        return "ASSIGN_DIV";
      case ASSIGN_MOD:
        return "ASSIGN_MOD";
      case HOOK:
        return "HOOK";
      case OR:
        return "OR";
      case AND:
        return "AND";
      case INC:
        return "INC";
      case DEC:
        return "DEC";
      case FUNCTION:
        return "FUNCTION";
      case IF:
        return "IF";
      case SWITCH:
        return "SWITCH";
      case CASE:
        return "CASE";
      case DEFAULT_CASE:
        return "DEFAULT_CASE";
      case WHILE:
        return "WHILE";
      case DO:
        return "DO";
      case FOR:
        return "FOR";
      case BREAK:
        return "BREAK";
      case CONTINUE:
        return "CONTINUE";
      case VAR:
        return "VAR";
      case WITH:
        return "WITH";
      case CATCH:
        return "CATCH";
      case EMPTY:
        return "EMPTY";
      case BLOCK:
        return "BLOCK";
      case LABEL:
        return "LABEL";
      case EXPR_RESULT:
        return "EXPR_RESULT";
      case SCRIPT:
        return "SCRIPT";
      case GETTER_DEF:
        return "GETTER_DEF";
      case SETTER_DEF:
        return "SETTER_DEF";
      case CONST:
        return "CONST";
      case DEBUGGER:
        return "DEBUGGER";
      case CAST:
        return "CAST";
      case ANNOTATION:
        return "ANNOTATION";
      case PIPE:
        return "PIPE";
      case STAR:
        return "STAR";
      case EOC:
        return "EOC";
      case QMARK:
        return "QMARK";
      case ELLIPSIS:
        return "ELLIPSIS";
      case BANG:
        return "BANG";
      case VOID:
        return "VOID";
      case EQUALS:
        return "EQUALS";
      case LB:
        return "LB";
      case LC:
        return "LC";
      case COLON:
        return "COLON";
      case STRING_TYPE:
        return "STRING_TYPE";
      case ANY_TYPE:
        return "ANY_TYPE";
      case NULLABLE_TYPE:
        return "NULLABLE_TYPE";
      case VOID_TYPE:
        return "VOID_TYPE";
      case BOOLEAN_TYPE:
        return "BOOLEAN_TYPE";
      case NUMBER_TYPE:
        return "NUMBER_TYPE";
      case PARAMETERIZED_TYPE:
        return "PARAMETERIZED_TYPE";
      case ARRAY_TYPE:
        return "ARRAY_TYPE";
      case UNION_TYPE:
        return "UNION_TYPE";
      case FUNCTION_TYPE:
        return "FUNCTION_TYPE";
      case REST_PARAMETER_TYPE:
        return "REST_PARAMETER_TYPE";
      case NAMED_TYPE:
        return "NAMED_TYPE";
      case OPTIONAL_PARAMETER:
        return "OPTIONAL_PARAMETER";
      case RECORD_TYPE:
        return "RECORD_TYPE";
      case UNDEFINED_TYPE:
        return "UNDEFINED_TYPE";
      case GENERIC_TYPE:
        return "GENERIC_TYPE";
      case GENERIC_TYPE_LIST:
        return "GENERIC_TYPE_LIST";

      case ARRAY_PATTERN:
        return "ARRAY_PATTERN";
      case OBJECT_PATTERN:
        return "OBJECT_PATTERN";
      case DESTRUCTURING_LHS:
        return "DESTRUCTURING_LHS";
      case CLASS:
        return "CLASS";
      case CLASS_MEMBERS:
        return "CLASS_MEMBERS";
      case MEMBER_FUNCTION_DEF:
        return "MEMBER_FUNCTION_DEF";
      case SUPER:
        return "SUPER";
      case LET:
        return "LET";
      case FOR_OF:
        return "FOR_OF";
      case YIELD:
        return "YIELD";
      case IMPORT:
        return "IMPORT";
      case IMPORT_SPECS:
        return "IMPORT_SPECS";
      case IMPORT_SPEC:
        return "IMPORT_SPEC";
      case IMPORT_STAR:
        return "IMPORT_STAR";
      case EXPORT:
        return "EXPORT";
      case EXPORT_SPECS:
        return "EXPORT_SPECS";
      case EXPORT_SPEC:
        return "EXPORT_SPEC";
      case NAMESPACE:
        return "NAMESPACE";
      case REST:
        return "REST";
      case SPREAD:
        return "SPREAD";
      case COMPUTED_PROP:
        return "COMPUTED_PROP";
      case DEFAULT_VALUE:
        return "DEFAULT_VALUE";
      case NEW_TARGET:
        return "NEW_TARGET";
      case MEMBER_VARIABLE_DEF:
        return "MEMBER_VARIABLE_DEF";

      case PLACEHOLDER1:
        return "PLACEHOLDER1";
      case PLACEHOLDER2:
        return "PLACEHOLDER2";

      case INTERFACE:
        return "INTERFACE";
      case INTERFACE_EXTENDS:
        return "INTERFACE_EXTENDS";
      case INTERFACE_MEMBERS:
        return "INTERFACE_MEMBERS";
      case ENUM:
        return "ENUM";
      case ENUM_MEMBERS:
        return "ENUM_MEMBERS";
      case NAMESPACE_ELEMENTS:
        return "NAMESPACE_ELEMENTS";
      case IMPLEMENTS:
        return "IMPLEMENTS";
      case TYPE_ALIAS:
        return "TYPE_ALIAS";
      case DECLARE:
        return "DECLARE";
      case INDEX_SIGNATURE:
        return "INDEX_SIGNATURE";
      case CALL_SIGNATURE:
        return "CALL_SIGNATURE";
    }

    // Token without name
    throw new IllegalStateException("No name defined for " + token);
  }

  /** If the arity isn't always the same, this function returns -1 */
  public static int arity(Kind token) {
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
    }
    throw new IllegalStateException(
        "No arity defined for " + Token.name(token));
  }
}
