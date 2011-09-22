/*
 * Copyright 2011 The Closure Compiler Authors. All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *  * Neither the name of Google Inc. nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.google.debugging.sourcemap;

import junit.framework.TestCase;

/**
 * @author johnlenz@google.com (John Lenz)
 */
public class Base64VLQTest extends TestCase {
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

  // Disable this test if it is flaky.
  public void testSpeed() {
    long start = System.currentTimeMillis();
    CharIteratorImpl ci = new CharIteratorImpl();
    try {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < 1000000; i++) {
        Base64VLQ.encode(sb, i);
        ci.set(sb);
        int result = Base64VLQ.decode(ci);
        assertEquals(i, result);
        sb.setLength(0);
      }
    } catch (Exception e) {
      throw new RuntimeException("failed.", e);
    }
    long end = System.currentTimeMillis();
    // Was 200ms or less, use a larger number to prevent flakiness
    assertTrue("too slow", end-start < 1000);
  }

  private void testValue(int value) {
    try {
      StringBuilder sb = new StringBuilder();
      Base64VLQ.encode(sb, value);
      CharIteratorImpl ci = new CharIteratorImpl();
      ci.set(sb);
      int result = Base64VLQ.decode(ci);
      assertEquals(value, result);
    } catch (Exception e) {
      throw new RuntimeException("failed for value " + value, e);
    }
  }


}