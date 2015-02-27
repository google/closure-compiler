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
 *   Nick Santos
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
 * The {@code StaticSourceFile} contains information about a compiler input.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public interface StaticSourceFile {
  /**
   * The name of the file. Must be unique across all files in the compilation.
   */
  String getName();

  /**
   * Returns whether this is an externs file.
   */
  boolean isExtern();

  /**
   * Returns the offset of the given line number relative to the file start.
   * Line number should be 1-based.
   *
   * If the source file doesn't have line information, it should return
   * Integer.MIN_VALUE. The negative offsets will make it more obvious
   * what happened.
   *
   * @param lineNumber the line of the input to get the absolute offset of.
   * @return the absolute offset of the start of the provided line.
   * @throws IllegalArgumentException if lineno is less than 1 or greater than
   *         the number of lines in the source.
   */
  int getLineOffset(int lineNumber);

  /**
   * Gets the 1-based line number of the given source offset.
   *
   * @param offset An absolute file offset.
   * @return The 1-based line number of that offset. The behavior is
   *     undefined if this offset does not exist in the source file.
   */
  int getLineOfOffset(int offset);

  /**
   * Gets the 0-based column number of the given source offset.
   *
   * @param offset An absolute file offset.
   * @return The 0-based column number of that offset. The behavior is
   *     undefined if this offset does not exist in the source file.
   */
  int getColumnOfOffset(int offset);
}
