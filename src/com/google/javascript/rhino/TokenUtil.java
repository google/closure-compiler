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
 *   Ethan Hugg
 *   Bob Jervis
 *   Terry Lucas
 *   Milen Nankov
 *   Pascal-Louis Perez
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

import com.google.javascript.jscomp.base.Tri;

/**
 * Helper methods for parsing JavaScript.
 *
 */
public final class TokenUtil {
  /* As defined in ECMA.  jsscan.c uses C isspace() (which allows
   * \v, I think.)  note that code in getChar() implicitly accepts
   * '\r' == \u000D as well.
   */
  public static boolean isJSSpace(int c) {
    if (c <= 127) {
      return c == 0x20 || c == 0x9 || c == 0xC || c == 0xB;
    } else {
      return c == 0xA0
          || Character.getType((char) c) == Character.SPACE_SEPARATOR;
    }
  }

  public static boolean isJSFormatChar(int c) {
    return c > 127 && Character.getType(c) == Character.FORMAT;
  }

  public static boolean isWhitespace(int c) {
    return Character.isWhitespace(c);
  }

  /** Copied from Rhino's ScriptRuntime */
  public static Tri isStrWhiteSpaceChar(int c) {
    return switch (c) {
      case '\u000B' -> // <VT>
          Tri.UNKNOWN; // IE says "no", ECMAScript says "yes"
      case ' ', // <SP>
          '\n', // <LF>
          '\r', // <CR>
          '\t', // <TAB>
          '\u00A0', // <NBSP>
          '\u000C', // <FF>
          '\u2028', // <LS>
          '\u2029', // <PS>
          '\uFEFF' -> // <BOM>
          Tri.TRUE;
      default -> (Character.getType(c) == Character.SPACE_SEPARATOR) ? Tri.TRUE : Tri.FALSE;
    };
  }

  private TokenUtil() {}
}
