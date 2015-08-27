/*
 * Copyright 2011 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.debugging.sourcemap;

import static com.google.common.truth.Truth.assertThat;

import junit.framework.TestCase;

/**
 * @author johnlenz@google.com (John Lenz)
 */
public final class Base64VLQTest extends TestCase {
  public void testBase64VLQSelectedValues1() {
    for (int i = 0; i < 63; i++) {
      testValue(i);
    }
  }

  public void testBase64VLQSelectedValues2() {
    int base = 1;
    for (int i = 0; i < 30; i++) {
      testValue(base-1);
      testValue(base);
      base *= 2;
    }
  }

  public void testBase64VLQSelectedSignedValues1() {
    for (int i = -(64*64-1); i < (64*64-1); i++) {
      testValue(i);
    }
  }

  public void testBase64VLQSelectedSignedValues2() {
    int base = 1;
    for (int i = 0; i < 30; i++) {
      testValue(base-1);
      testValue(base);
      base *= 2;
    }
    base = -1;
    for (int i = 0; i < 30; i++) {
      testValue(base-1);
      testValue(base);
      base *= 2;
    }
  }

  static class CharIteratorImpl implements Base64VLQ.CharIterator {
    private int current;
    private int length;
    private CharSequence cs;

    void set(CharSequence sb) {
      this.current = 0;
      this.length = sb.length();
      this.cs = sb;
    }

    @Override
    public boolean hasNext() {
      return current < length;
    }

    @Override
    public char next() {
      return cs.charAt(current++);
    }
  }


  private void testValue(int value) {
    try {
      StringBuilder sb = new StringBuilder();
      Base64VLQ.encode(sb, value);
      CharIteratorImpl ci = new CharIteratorImpl();
      ci.set(sb);
      int result = Base64VLQ.decode(ci);
      assertThat(result).isEqualTo(value);
    } catch (Exception e) {
      throw new RuntimeException("failed for value " + value, e);
    }
  }


}
