package com.google.javascript.rhino;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

public class TokenStreamTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Rule public final Timeout globalTimeout = new Timeout(10000);

  /* testedClasses: TokenStream */
  // Test written by Diffblue Cover.

  @Test
  public void constructorOutputVoid() {

    // Act, creating object to test constructor
    final TokenStream objectUnderTest = new TokenStream();

    // Method returns void, testing that no exception is thrown
  }

  // Test written by Diffblue Cover.
  @Test
  public void isJSIdentifierInputNotNullOutputFalse() {

    // Arrange
    final String s = "2";

    // Act
    final boolean actual = TokenStream.isJSIdentifier(s);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse() {

    // Arrange
    final String name = "3";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse2() {

    // Arrange
    final String name = "a\'b\'c";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse3() {

    // Arrange
    final String name = "1a 2b 3c";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse4() {

    // Arrange
    final String name = "A1B2C3";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse5() {

    // Arrange
    final String name = "Bar";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse6() {

    // Arrange
    final String name = "1234";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse7() {

    // Arrange
    final String name = "foo";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse8() {

    // Arrange
    final String name = "\u0001\u0001";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse9() {

    // Arrange
    final String name = "\u0000o";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse10() {

    // Arrange
    final String name = "v\u106fi";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse11() {

    // Arrange
    final String name = "v\u106f\u1069dc\u206ceefe";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse12() {

    // Arrange
    final String name = "vyeyc\u206cddd";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse13() {

    // Arrange
    final String name = "vmemk\u206cddim";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse14() {

    // Arrange
    final String name = "vnemk\u206cddim";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse15() {

    // Arrange
    final String name = "vnemk\u206cd";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse16() {

    // Arrange
    final String name = "voemj\u206defzldo";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse17() {

    // Arrange
    final String name = "voemj\u206d\u0001";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse18() {

    // Arrange
    final String name = "peimf\u2061\r";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse19() {

    // Arrange
    final String name = "pximf\u2060\r";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse20() {

    // Arrange
    final String name = "piimf\u2060\r";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse21() {

    // Arrange
    final String name = "pii\u8069i";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse22() {

    // Arrange
    final String name = "pn";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse23() {

    // Arrange
    final String name = "\u8069f";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse24() {

    // Arrange
    final String name = "toe";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse25() {

    // Arrange
    final String name = "ccnnc";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse26() {

    // Arrange
    final String name = "ccnn";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse27() {

    // Arrange
    final String name = "vmr";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse28() {

    // Arrange
    final String name = "ffonff\u00e6g";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse29() {

    // Arrange
    final String name = "ixbfgx";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse30() {

    // Arrange
    final String name = "exix";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse31() {

    // Arrange
    final String name = "etse";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse32() {

    // Arrange
    final String name = "eesege";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse33() {

    // Arrange
    final String name = "tuy";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse34() {

    // Arrange
    final String name = "nrr";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse35() {

    // Arrange
    final String name = "nrw";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse36() {

    // Arrange
    final String name = "p\u8070wupxclp";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse37() {

    // Arrange
    final String name = "irwuixcli";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse38() {

    // Arrange
    final String name = "irw";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse39() {

    // Arrange
    final String name = "irt";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse40() {

    // Arrange
    final String name = "\u025dm\\\\\\]";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse41() {

    // Arrange
    final String name = "QaD\u0000hU";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse42() {

    // Arrange
    final String name = "QaD\u0000hUU";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse43() {

    // Arrange
    final String name = "QrD\u0000kVV";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse44() {

    // Arrange
    final String name = "fhorf";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse45() {

    // Arrange
    final String name = "c\u2065re";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse46() {

    // Arrange
    final String name = "c\u2065se";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse47() {

    // Arrange
    final String name = "f\u2065r";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse48() {

    // Arrange
    final String name = "fyresy";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse49() {

    // Arrange
    final String name = "cyrr";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse50() {

    // Arrange
    final String name = "cyar";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputFalse51() {

    // Arrange
    final String name = "cuarsr";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue() {

    // Arrange
    final String name = "true";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue2() {

    // Arrange
    final String name = "this";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue3() {

    // Arrange
    final String name = "with";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue4() {

    // Arrange
    final String name = "goto";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue5() {

    // Arrange
    final String name = "void";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue6() {

    // Arrange
    final String name = "in";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue7() {

    // Arrange
    final String name = "if";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue8() {

    // Arrange
    final String name = "do";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue9() {

    // Arrange
    final String name = "double";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue10() {

    // Arrange
    final String name = "volatile";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue11() {

    // Arrange
    final String name = "catch";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue12() {

    // Arrange
    final String name = "throw";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue13() {

    // Arrange
    final String name = "super";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue14() {

    // Arrange
    final String name = "false";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue15() {

    // Arrange
    final String name = "var";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue16() {

    // Arrange
    final String name = "continue";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue17() {

    // Arrange
    final String name = "abstract";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue18() {

    // Arrange
    final String name = "break";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue19() {

    // Arrange
    final String name = "final";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue20() {

    // Arrange
    final String name = "short";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue21() {

    // Arrange
    final String name = "class";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue22() {

    // Arrange
    final String name = "transient";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue23() {

    // Arrange
    final String name = "debugger";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue24() {

    // Arrange
    final String name = "return";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue25() {

    // Arrange
    final String name = "try";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue26() {

    // Arrange
    final String name = "int";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue27() {

    // Arrange
    final String name = "enum";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue28() {

    // Arrange
    final String name = "null";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue29() {

    // Arrange
    final String name = "long";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue30() {

    // Arrange
    final String name = "byte";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue31() {

    // Arrange
    final String name = "else";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue32() {

    // Arrange
    final String name = "switch";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue33() {

    // Arrange
    final String name = "static";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue34() {

    // Arrange
    final String name = "throws";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue35() {

    // Arrange
    final String name = "char";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue36() {

    // Arrange
    final String name = "new";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue37() {

    // Arrange
    final String name = "case";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue38() {

    // Arrange
    final String name = "delete";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isKeywordInputNotNullOutputTrue39() {

    // Arrange
    final String name = "for";

    // Act
    final boolean actual = TokenStream.isKeyword(name);

    // Assert result
    Assert.assertTrue(actual);
  }
}
