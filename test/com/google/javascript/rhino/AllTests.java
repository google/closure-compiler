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

import com.google.javascript.rhino.jstype.BooleanLiteralSetTest;
import com.google.javascript.rhino.jstype.FunctionParamBuilderTest;
import com.google.javascript.rhino.jstype.JSTypeRegistryTest;
import com.google.javascript.rhino.jstype.JSTypeTest;
import com.google.javascript.rhino.jstype.TernaryValueTest;
import com.google.javascript.rhino.jstype.UnionTypeBuilderTest;

import junit.framework.Test;
import junit.framework.TestSuite;

import java.util.logging.Level;
import java.util.logging.Logger;

public class AllTests {
  public static Test suite() throws Exception {
    // suspend logging
    Logger.getLogger("com.google").setLevel(Level.OFF);

    // The testing framework is lame and doesn't work in third_party.
    // Manually load the classes.
    TestSuite suite = new TestSuite();

    suite.addTestSuite(JSDocInfoTest.class);
    suite.addTestSuite(NodeTest.class);
    suite.addTestSuite(ParserTest.class);
    suite.addTestSuite(TokenStreamTest.class);

    suite.addTestSuite(BooleanLiteralSetTest.class);
    suite.addTestSuite(FunctionParamBuilderTest.class);
    suite.addTestSuite(JSTypeRegistryTest.class);
    suite.addTestSuite(JSTypeTest.class);
    suite.addTestSuite(TernaryValueTest.class);
    suite.addTestSuite(UnionTypeBuilderTest.class);

    return suite;
  }
}
