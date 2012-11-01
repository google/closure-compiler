/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

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
