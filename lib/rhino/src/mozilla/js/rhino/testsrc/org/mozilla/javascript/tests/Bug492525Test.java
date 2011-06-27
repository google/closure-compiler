package org.mozilla.javascript.tests;

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import org.mozilla.javascript.NativeArray;

public class Bug492525Test {
  @Test
  public void getAllIdsShouldIncludeArrayIndices() {
    NativeArray array = new NativeArray(new String[]{"a", "b"});
    Object[] expectedIds = new Object[] {0, 1, "length"};
    Object[] actualIds = array.getAllIds();
    assertArrayEquals(expectedIds, actualIds);
  }
}
