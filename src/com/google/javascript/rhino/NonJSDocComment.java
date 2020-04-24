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
 *   Bob Jervis
 *   Google Inc.
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
import java.io.Serializable;

/** Minimal class holding information about a nonJSDoc comment's source location and contents */
public class NonJSDocComment implements Serializable {
  private final int beginOffset;
  private int endOffset;
  private String contents;
  private boolean isTrailing;

  public NonJSDocComment(int begin, int end, String s) {
    beginOffset = begin;
    endOffset = end;
    contents = s;
    this.isTrailing = false;
  }

  public String getCommentString() {
    if (contents == null) {
      return "";
    }
    return contents;
  }

  public int getBeginOffset() {
    return beginOffset;
  }

  public int getEndOffset() {
    return endOffset;
  }

  public void setIsTrailing(boolean b) {
    this.isTrailing = b;
  }

  /**
   * Indicates whether this comment is placed after the source node it is attached to. Currently,
   * this field is set only for comments associated with formal parameters and arguments.
   */
  public boolean isTrailing() {
    return this.isTrailing;
  }

  /*
   * In presence of both non-trailing and trailing comment associated with a node, such as : //
   *   ```
   *   function foo( // first
   *                    x // second
   *                  ) {}
   *   ```
   * We create a single NonJSDocComment `//first //second` and attach it to Node x.
   * The beginOffset of this single comment is the begin offset of the `// first` comment, the end
   * offset of this single comment is the end offset of the `// second` comment.
   */
  public void appendTrailingCommentToNonTrailing(NonJSDocComment trailingComment) {
    this.contents = this.contents + trailingComment.contents;
    this.endOffset = trailingComment.getEndOffset();
  }
}
