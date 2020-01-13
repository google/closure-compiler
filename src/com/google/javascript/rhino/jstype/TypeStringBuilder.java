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

package com.google.javascript.rhino.jstype;

/**
 * Visits types to assemble an associated stringification.
 *
 * <p>Instances are single use. They irreversibly accumulate state required during traversal.
 */
final class TypeStringBuilder implements Appendable {

  private final StringBuilder builder = new StringBuilder();
  private final boolean isForAnnotations;

  TypeStringBuilder(boolean isForAnnotations) {
    this.isForAnnotations = isForAnnotations;
  }

  boolean isForAnnotations() {
    return this.isForAnnotations;
  }

  @Override
  public TypeStringBuilder append(CharSequence text) {
    this.builder.append(text);
    return this;
  }

  @Override
  public TypeStringBuilder append(char c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public TypeStringBuilder append(CharSequence csq, int start, int end) {
    throw new UnsupportedOperationException();
  }

  TypeStringBuilder append(JSType type) {
    type.appendTo(this);
    return this;
  }

  TypeStringBuilder appendNonNull(JSType type) {
    if (this.isForAnnotations
        && type.isObject()
        && !type.isUnknownType()
        && !type.isTemplateType()
        && !type.isRecordType()
        && !type.isFunctionType()
        && !type.isUnionType()
        && !type.isLiteralObject()) {
      this.append("!");
    }
    return this.append(type);
  }

  String build() {
    return this.builder.toString();
  }
}
