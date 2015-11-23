package com.google.javascript.rhino.dtoa;

import com.google.javascript.rhino.dtoa.DToA;

import junit.framework.TestCase;

public class NumberToStringTest extends TestCase {

  public NumberToStringTest() {
  }

  private void assertNumberToString(double d, String expected) {
    assertEquals(DToA.numberToString(d), expected);
  }

  public void testSpecialNumbers() {
    assertNumberToString(0.0, "0");
    assertNumberToString(-0.0, "0");
    assertNumberToString(Double.NaN, "NaN");
    assertNumberToString(Double.POSITIVE_INFINITY, "Infinity");
    assertNumberToString(Double.NEGATIVE_INFINITY, "-Infinity");
  }

  public void testIntegers() {
    assertNumberToString(1, "1");
    assertNumberToString(2, "2");
    assertNumberToString(5, "5");
    assertNumberToString(10, "10");
    assertNumberToString(200, "200");
    assertNumberToString(2147483647, "2147483647");

    assertNumberToString(-3, "-3");
    assertNumberToString(-7, "-7");
    assertNumberToString(-19, "-19");
    assertNumberToString(-300, "-300");
    assertNumberToString(-2147483648, "-2147483648");
  }

  public void testFullPrecision() {
    assertNumberToString(1.42, "1.42");
    assertNumberToString(12.42, "12.42");
    assertNumberToString(123.42, "123.42");
    assertNumberToString(1234.42, "1234.42");
    assertNumberToString(12345.42, "12345.42");
    assertNumberToString(123456.42, "123456.42");
    assertNumberToString(1234567.42, "1234567.42");
    assertNumberToString(12345678.42, "12345678.42");
    assertNumberToString(123456789.42, "123456789.42");
    assertNumberToString(1234567890.42, "1234567890.42");
    assertNumberToString(12345678901.42, "12345678901.42");
    assertNumberToString(123456789012.42, "123456789012.42");
    assertNumberToString(1234567890123.42, "1234567890123.42");
    assertNumberToString(12345678901234.42, "12345678901234.42");
    assertNumberToString(123456789012345.42, "123456789012345.42");
  }

  public void testScientificNotation() {
    assertNumberToString(1E21, "1e+21");
    assertNumberToString(2.345E50, "2.345e+50");
    assertNumberToString(3E-200, "3e-200");
    assertNumberToString(7.42E307, "7.42e+307");
  }

  public void testUnitLastPlaceGreaterThanOne() {
    assertNumberToString(18271179521433728.0, "18271179521433730");
    assertNumberToString(1152921504606846853.0, "1152921504606846800");
    assertNumberToString(149170297077708820000.0, "149170297077708800000");
  }
}
