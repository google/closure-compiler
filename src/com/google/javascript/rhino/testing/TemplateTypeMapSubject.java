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

package com.google.javascript.rhino.testing;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.TemplateType;
import com.google.javascript.rhino.jstype.TemplateTypeMap;
import javax.annotation.CheckReturnValue;

/** A Truth Subject for {@link TemplateTypeMap}s. */
public final class TemplateTypeMapSubject extends Subject {
  @CheckReturnValue
  public static TemplateTypeMapSubject assertThat(TemplateTypeMap actual) {
    return assertAbout(typeMaps()).that(actual);
  }

  public static Subject.Factory<TemplateTypeMapSubject, TemplateTypeMap> typeMaps() {
    return TemplateTypeMapSubject::new;
  }

  private final TemplateTypeMap actual;

  private TemplateTypeMapSubject(FailureMetadata failureMetadata, TemplateTypeMap actual) {
    super(failureMetadata, actual);
    this.actual = actual;
  }

  public void hasKeysAndValues(
      ImmutableList<TemplateType> keys, ImmutableList<? extends JSType> values) {
    check("getTemplateKeys()").that(actual.getTemplateKeys()).isEqualTo(keys);
    check("getTemplateValues()").that(actual.getTemplateValues()).isEqualTo(values);
  }
}
