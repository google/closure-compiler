/* ***** BEGIN LICENSE BLOCK *****
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
 * The Original Code is Rhino code, released May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Attila Szegedi
 *   David P. Caldwell <inonit@inonit.com>
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

package org.mozilla.javascript.tests;

/**
 * A class with private/protected/package private members, to test the Rhino
 * feature Context.FEATURE_ENHANCED_JAVA_ACCESS, that allows bypassing Java
 * member access restrictions.
 */

public class PrivateAccessClass
{
  private PrivateAccessClass() { }
  PrivateAccessClass(String s) { }
  private PrivateAccessClass(int x) { }
  protected PrivateAccessClass(int x, String s) { }

  private static class PrivateNestedClass
  {
    private PrivateNestedClass() { }

    int packagePrivateInt = 0;
    private int privateInt = 1;
    protected int protectedInt = 2;
  }

  static int staticPackagePrivateInt = 0;
  private static int staticPrivateInt = 1;
  protected static int staticProtectedInt = 2;

  String packagePrivateString = "package private";
  private String privateString = "private";
  protected String protectedString = "protected";

  static int staticPackagePrivateMethod() { return 0; }
  static private int staticPrivateMethod() { return 1; }
  static protected int staticProtectedMethod() { return 2; }

  int packagePrivateMethod() { return 3; }
  private int privateMethod() { return 4; }
  protected int protectedMethod() { return 5; }

  private int javaBeanProperty = 6;
  public boolean getterCalled = false;
  public boolean setterCalled = false;
  public int getJavaBeanProperty() {
      getterCalled = true;
      return javaBeanProperty;
  }
  public void setJavaBeanProperty(int i) {
      setterCalled = true;
      javaBeanProperty = i;
  }

  /*
   * Suppress warnings about unused private members.
   */
  public int referenceToPrivateMembers() {
    PrivateAccessClass pac = new PrivateAccessClass();
    PrivateAccessClass pac2 = new PrivateAccessClass(2);
    PrivateNestedClass pnc = new PrivateNestedClass();
    System.out.println(privateString);
    pac2.privateMethod(); // to silence warning
    return pnc.privateInt + staticPrivateInt + staticPrivateMethod() +
           pac.privateMethod() + javaBeanProperty;
  }
}
