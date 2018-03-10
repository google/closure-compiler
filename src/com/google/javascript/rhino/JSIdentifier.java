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

import com.google.common.annotations.GwtIncompatible;

/**
 * Utility class to hold isJSIdentifier.
 *
 * <p>Separated into its own class because it is not GWT compatible.
 *
 * IMPORTANT: As of 2018-03-09 it is still not possible to use Java 8 features in this file
 * due to limitations on some internal Google projects that depend on it.
 */
@GwtIncompatible
public class JSIdentifier {
  static boolean isJSIdentifier(String s) {
    int length = s.length();

    if (length == 0
        || Character.isIdentifierIgnorable(s.charAt(0))
        || !Character.isJavaIdentifierStart(s.charAt(0))) {
      return false;
    }

    for (int i = 1; i < length; i++) {
      if (Character.isIdentifierIgnorable(s.charAt(i))
          || !Character.isJavaIdentifierPart(s.charAt(i))) {
        return false;
      }
    }
    return true;
  }
}
