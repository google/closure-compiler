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
 *   John Lenz
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;

import com.google.javascript.rhino.jstype.NamedType.ResolutionKind;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the semantics of {@link TemplateType} */
@RunWith(JUnit4.class)
public final class TemplateTypeTest extends BaseJSTypeTestCase {

  @Test
  public void templateTypes_boundedByUnknown_areNotEqual() {
    JSType unknownType = registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
    TemplateType t1 = new TemplateType(registry, "T", unknownType);
    TemplateType t2 = new TemplateType(registry, "T", unknownType);

    t1.resolve(registry.getErrorReporter());
    t2.resolve(registry.getErrorReporter());

    assertType(t1).isNotEqualTo(t2);
    assertType(t1).isSubtypeOf(t2);
  }

  @Test
  public void templateTypes_boundedByNominalTypes_areNotEqual() {
    JSType fooType =
        registry.createObjectType("Foo", registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE));
    TemplateType t1 = new TemplateType(registry, "T", fooType);
    TemplateType t2 = new TemplateType(registry, "T", fooType);

    t1.resolve(registry.getErrorReporter());
    t2.resolve(registry.getErrorReporter());

    assertType(t1).isNotEqualTo(t2);
    assertType(t1).isNotSubtypeOf(t2);
  }

  @Test
  public void proxiesOf_templateTypes_boundedByNominalTypes_areNotEqual() {
    JSType fooType =
        registry.createObjectType("Foo", registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE));

    TemplateType t1 = new TemplateType(registry, "T", fooType);
    TemplateType t2 = new TemplateType(registry, "T", fooType);

    NamedType t1Proxy =
        NamedType.builder(registry, "T")
            .setResolutionKind(ResolutionKind.NONE)
            .setReferencedType(t1)
            .build();
    NamedType t2Proxy =
        NamedType.builder(registry, "T")
            .setResolutionKind(ResolutionKind.NONE)
            .setReferencedType(t2)
            .build();

    assertType(t1Proxy).isNotEqualTo(t2Proxy);
    assertType(t1Proxy).isNotSubtypeOf(t2Proxy);
  }

  @Test
  public void templateTypes_areNotEqualToUnknown() {
    // Given
    TemplateType t = new TemplateType(registry, "T");
    assertThat(t.isUnknownType()).isTrue();

    // Then
    assertType(t).isNotEqualTo(UNKNOWN_TYPE);
    assertType(t).isNotEqualTo(CHECKED_UNKNOWN_TYPE);
  }

  @Test
  public void templateTypes_withBound_areNotEqualToBound() {
    // Given
    TemplateType t = new TemplateType(registry, "T", NUMBER_TYPE);

    // Then
    assertType(t).isNotEqualTo(NUMBER_TYPE);
  }
}
