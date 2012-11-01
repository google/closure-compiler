/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * Tests for the Object.getOwnPropertyNames(obj) method
 */
package org.mozilla.javascript.tests.es5;
import org.mozilla.javascript.*;
import static org.mozilla.javascript.tests.Evaluator.eval;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ObjectGetOwnPropertyNamesTest {

  @Test
  public void testShouldReturnAllPropertiesOfArg() {
    NativeObject object = new NativeObject();
    object.defineProperty("a", "1", ScriptableObject.EMPTY);
    object.defineProperty("b", "2", ScriptableObject.DONTENUM);

    Object result = eval("Object.getOwnPropertyNames(obj)", "obj", object);

    NativeArray names = (NativeArray) result;

    assertEquals(2, names.getLength());
    assertEquals("a", names.get(0, names));
    assertEquals("b", names.get(1, names));
  }

}
