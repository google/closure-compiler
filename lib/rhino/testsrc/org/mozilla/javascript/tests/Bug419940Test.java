/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests;

import junit.framework.TestCase;

import org.mozilla.javascript.*;

/**
 * See https://bugzilla.mozilla.org/show_bug.cgi?id=419940
 */
public class Bug419940Test extends TestCase {
    final static int value = 12;

    public static abstract class BaseFoo {
        public abstract int doSomething();
    }
    public static class Foo extends BaseFoo {
        @Override
        public int doSomething() {
           return value;
        }
    }

  public void testAdapter() {
      String source =
          "(new JavaAdapter(" + Foo.class.getName() + ", {})).doSomething();";

      Context cx = ContextFactory.getGlobal().enterContext();
      try {
          Scriptable scope = cx.initStandardObjects();
          Object result = cx.evaluateString(scope, source, "source", 1, null);
          assertEquals(new Integer(value), result);
      } finally {
          Context.exit();
      }
  }
}
