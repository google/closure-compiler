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

package com.google.javascript.rhino.jstype;

import static com.google.common.truth.Truth.assertThat;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for FunctionParamBuilder.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
@RunWith(JUnit4.class)
public class FunctionParamBuilderTest extends BaseJSTypeTestCase {

  @Test
  public void testBuild() {
    FunctionParamBuilder builder = new FunctionParamBuilder(registry);
    assertThat(builder.addRequiredParams(NUMBER_TYPE)).isTrue();
    assertThat(builder.addOptionalParams(BOOLEAN_TYPE)).isTrue();
    assertThat(builder.addVarArgs(STRING_TYPE)).isTrue();

    Node params = builder.build();
    assertTypeEquals(NUMBER_TYPE, params.getFirstChild().getJSType());
    assertTypeEquals(registry.createOptionalType(BOOLEAN_TYPE),
        params.getSecondChild().getJSType());
    assertTypeEquals(STRING_TYPE, params.getLastChild().getJSType());

    assertThat(params.getSecondChild().isOptionalArg()).isTrue();
    assertThat(params.getLastChild().isVarArgs()).isTrue();
  }
}
