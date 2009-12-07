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

import static com.google.javascript.rhino.Token.ADD;
import static com.google.javascript.rhino.Token.ANNOTATION;
import static com.google.javascript.rhino.Token.BANG;
import static com.google.javascript.rhino.Token.COLON;
import static com.google.javascript.rhino.Token.COMMA;
import static com.google.javascript.rhino.Token.DOT;
import static com.google.javascript.rhino.Token.ELLIPSIS;
import static com.google.javascript.rhino.Token.ELSE;
import static com.google.javascript.rhino.Token.EOC;
import static com.google.javascript.rhino.Token.EOF;
import static com.google.javascript.rhino.Token.EOL;
import static com.google.javascript.rhino.Token.EQUALS;
import static com.google.javascript.rhino.Token.GT;
import static com.google.javascript.rhino.Token.IF;
import static com.google.javascript.rhino.Token.LB;
import static com.google.javascript.rhino.Token.LC;
import static com.google.javascript.rhino.Token.LP;
import static com.google.javascript.rhino.Token.LT;
import static com.google.javascript.rhino.Token.NAME;
import static com.google.javascript.rhino.Token.PIPE;
import static com.google.javascript.rhino.Token.QMARK;
import static com.google.javascript.rhino.Token.RB;
import static com.google.javascript.rhino.Token.RC;
import static com.google.javascript.rhino.Token.RP;
import static com.google.javascript.rhino.Token.STAR;
import static com.google.javascript.rhino.Token.STRING;
import static com.google.javascript.rhino.Token.name;

import com.google.common.collect.ImmutableList;

import junit.framework.TestCase;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

public class TokenStreamTest extends TestCase {

  public void testLinenoCharnoGetUngetchar() throws Exception {
    TokenStream stream = stream("some\nrandom\nstring");

    int c;
    assertLinenoOffset(stream, 0, -1);
    c = getAndTestChar(stream, 's');
    assertLinenoOffset(stream, 0, 0);
    stream.ungetChar(c);
    assertLinenoOffset(stream, 0, -1);
    c = getAndTestChar(stream, 's');
    c = getAndTestChar(stream, 'o');
    assertLinenoOffset(stream, 0, 1);
    stream.ungetChar(c);
    assertLinenoOffset(stream, 0, 0);
    c = getAndTestChar(stream, 'o');
    assertLinenoOffset(stream, 0, 1);
    c = getAndTestChar(stream, 'm');
    assertLinenoOffset(stream, 0, 2);
    stream.ungetChar(c);
    assertLinenoOffset(stream, 0, 1);
    c = getAndTestChar(stream, 'm');
    c = getAndTestChar(stream, 'e');
    assertLinenoOffset(stream, 0, 3);
    c = getAndTestChar(stream, '\n');
    assertLinenoOffset(stream, 0, 4);
    c = getAndTestChar(stream, 'r');
    assertLinenoOffset(stream, 1, 0);
    c = getAndTestChar(stream, 'a');
    assertLinenoOffset(stream, 1, 1);
    c = getAndTestChar(stream, 'n');
    assertLinenoOffset(stream, 1, 2);
    c = getAndTestChar(stream, 'd');
    assertLinenoOffset(stream, 1, 3);
    stream.ungetChar(c);
    stream.ungetChar('n');
    c = getAndTestChar(stream, 'n');
    assertLinenoOffset(stream, 1, 2);
    c = getAndTestChar(stream, 'd');
    assertLinenoOffset(stream, 1, 3);
    c = getAndTestChar(stream, 'o');
    assertLinenoOffset(stream, 1, 4);
    c = getAndTestChar(stream, 'm');
    assertLinenoOffset(stream, 1, 5);
    c = getAndTestChar(stream, '\n');
    assertLinenoOffset(stream, 1, 6);
    c = getAndTestChar(stream, 's');
    assertLinenoOffset(stream, 2, 0);
    c = getAndTestChar(stream, 't');
    assertLinenoOffset(stream, 2, 1);
    c = getAndTestChar(stream, 'r');
    assertLinenoOffset(stream, 2, 2);
    c = getAndTestChar(stream, 'i');
    assertLinenoOffset(stream, 2, 3);
    c = getAndTestChar(stream, 'n');
    assertLinenoOffset(stream, 2, 4);
    c = getAndTestChar(stream, 'g');
    assertLinenoOffset(stream, 2, 5);
  }

  private int getAndTestChar(TokenStream stream, char e) throws IOException {
    int c = stream.getChar();
    assertEquals(e, (char) c);
    return c;
  }

  public void testLinenoCharno1() throws Exception {
    TokenStream stream = stream("if else");
    testNextTokenPosition(stream, IF, 0, 0);
    testNextTokenPosition(stream, ELSE, 0, 3);
  }

  public void testLinenoCharno2() throws Exception {
    TokenStream stream = stream(" if   else");
    testNextTokenPosition(stream, IF, 0, 1);
    testNextTokenPosition(stream, ELSE, 0, 6);
  }

  public void testLinenoCharno3() throws Exception {
    TokenStream stream = stream(" if \n  else");
    testNextTokenPosition(stream, IF, 0, 1);
    testNextTokenPosition(stream, EOL, 0, 4);
    testNextTokenPosition(stream, ELSE, 1, 2);
  }

  public void testLinenoCharno4() throws Exception {
    TokenStream stream = stream("foo.bar");
    testNextTokenPosition(stream, NAME, 0, 0);
    testNextTokenPosition(stream, DOT, 0, 3);
    testNextTokenPosition(stream, NAME, 0, 4);
  }

  public void testLinenoCharno5() throws Exception {
    TokenStream stream = stream("   foo  \n + \n  (");
    testNextTokenPosition(stream, NAME, 0, 3);
    testNextTokenPosition(stream, EOL, 0, 8);
    testNextTokenPosition(stream, ADD, 1, 1);
    testNextTokenPosition(stream, EOL, 1, 3);
    testNextTokenPosition(stream, LP, 2, 2);
  }

  public void testJSDocTokenization1() throws Exception {
    List<Integer> tokens = ImmutableList.of(
        STAR, ANNOTATION, LC, STRING, RC, EOL, STAR, ANNOTATION);
    List<String> strings = ImmutableList.of("type", "string", "private");
    testJSDocTokenStream(" * @type {string}\n * @private", tokens, strings);
    testJSDocTokenStream(" *    @type { string } \n * @private",
        tokens, strings);
    testJSDocTokenStream(" * @type   {  string}\n * @private", tokens, strings);
    testJSDocTokenStream(" * @type {string  }\n * @private", tokens, strings);
    testJSDocTokenStream(" * @type {string}\n *   @private", tokens, strings);
    testJSDocTokenStream(" * @type {string}   \n * @private", tokens, strings);
  }

  public void testJSDocTokenization2() throws Exception {
    List<Integer> tokens = ImmutableList.of(
        ANNOTATION, LC, STRING, LT, STRING, PIPE, STRING, GT, RC);
    List<String> strings = ImmutableList.of("param", "Array", "string", "null");
    testJSDocTokenStream("@param {Array.<string|null>}", tokens, strings);
    testJSDocTokenStream("@param {Array.<string|null>}", tokens, strings);
    testJSDocTokenStream("@param {Array.<string |null>}", tokens, strings);
    testJSDocTokenStream(" @param {Array.<string |  null>}", tokens, strings);
    testJSDocTokenStream(" @param {Array.<string|null  >}", tokens, strings);
    testJSDocTokenStream("@param {Array  .<string|null>}", tokens, strings);
    testJSDocTokenStream("@param   {Array.<string|null>}", tokens, strings);
    testJSDocTokenStream("@param {  Array.<string|null>}", tokens, strings);
    testJSDocTokenStream("@param {Array.<string|   null>}  ", tokens, strings);
    testJSDocTokenStream("@param {Array.<string|null>}", tokens, strings);
    testJSDocTokenStream("     @param { Array .< string |null > } ",
        tokens, strings);
  }

  public void testJSDocTokenization3() throws Exception {
    List<Integer> tokens = ImmutableList.of(
        ANNOTATION, LC, STRING, LT, STRING, PIPE, STRING, GT, RC);
    List<String> strings = ImmutableList.of("param", "Array", "string", "null");
    testJSDocTokenStream("@param {Array.<string||null>}", tokens, strings);
    testJSDocTokenStream("@param {Array.< string || null> }", tokens, strings);
    testJSDocTokenStream("@param {Array.<string || null >  } ",
        tokens, strings);
    testJSDocTokenStream("@param {Array .<string   ||null>}", tokens, strings);
    testJSDocTokenStream("@param {Array.< string||null>}", tokens, strings);
    testJSDocTokenStream("@param {  Array.<string||null>}", tokens, strings);
    testJSDocTokenStream(" @param   {Array.<string||null>}", tokens, strings);
    testJSDocTokenStream("@param   {   Array.<string|| null> }",
        tokens, strings);
  }

  public void testJSDocTokenization4() throws Exception {
    List<Integer> tokens = ImmutableList.of(
        ANNOTATION, LC, STRING, LT, LP, STRING, COMMA, STRING, RP, GT, RC, EOF);
    List<String> strings = ImmutableList.of("param", "Array", "string", "null");
    testJSDocTokenStream("@param {Array.<(string,null)>}", tokens, strings);
    testJSDocTokenStream("@param {Array  .<(string,null)> } ", tokens, strings);
    testJSDocTokenStream(" @param {Array.<  (  string,null)>}",
        tokens, strings);
    testJSDocTokenStream("@param {Array.<(string  , null)>}", tokens, strings);
    testJSDocTokenStream("@param {Array.<(string,   null)  > }  ",
        tokens, strings);
    testJSDocTokenStream("@param {  Array  .<  (string,null)>}   ",
        tokens, strings);
  }

  public void testJSDocTokenization5() throws Exception {
    List<Integer> tokens = ImmutableList.of(ANNOTATION, STRING, EOC, EOF);
    List<String> strings = ImmutableList.of("param", "foo.Bar");
    testJSDocTokenStream("@param foo.Bar*/", tokens, strings);
    testJSDocTokenStream(" @param   foo.Bar*/", tokens, strings);
    testJSDocTokenStream(" @param foo.Bar   */", tokens, strings);
  }

  public void testJSDocTokenization6() throws Exception {
    List<Integer> tokens = ImmutableList.of(
        ANNOTATION, EOL, ANNOTATION, EOL, ANNOTATION, EOC);
    List<String> strings = ImmutableList.of("hidden", "static", "desc");
    testJSDocTokenStream("@hidden\n@static\n@desc*/", tokens, strings);
    testJSDocTokenStream("@hidden\n @static\n@desc*/", tokens, strings);
    testJSDocTokenStream("@hidden\n@static\n @desc*/", tokens, strings);
    testJSDocTokenStream("@hidden\n@static\n@desc */", tokens, strings);
    testJSDocTokenStream(" @hidden \n@static\n @desc*/", tokens, strings);
    testJSDocTokenStream("@hidden\n@static    \n @desc  */", tokens, strings);
    testJSDocTokenStream("@hidden\n@static\n@desc*/", tokens, strings);
    testJSDocTokenStream("@hidden   \n@static   \n @desc*/", tokens, strings);
  }

  public void testJSDocTokenization7() throws Exception {
    List<Integer> tokens = ImmutableList.of(
        ELLIPSIS, ELLIPSIS, ELLIPSIS, ELLIPSIS, ELLIPSIS, LT, EOC);
    List<String> strings = ImmutableList.of();

    testJSDocTokenStream("................<*/", tokens, strings);
    testJSDocTokenStream("............... .<*/", tokens, strings);
    testJSDocTokenStream("................< */", tokens, strings);
    testJSDocTokenStream("............... .< */", tokens, strings);
    testJSDocTokenStream("............... .< */ ", tokens, strings);
    testJSDocTokenStream(" ............... .< */ ", tokens, strings);
  }

  public void testJSDocTokenization8() throws Exception {
    List<Integer> tokens = ImmutableList.of(
        STAR, ANNOTATION, STRING, STRING, STRING, STRING, STRING, STRING,
        STRING, EOL, EOC);
    List<String> strings = ImmutableList.of(
        "param", "foo.Bar", "opt_name", "this", "parameter", "is", "a", "name");
    testJSDocTokenStream(
        " * @param foo.Bar opt_name this parameter is a name\n" +
        " */", tokens, strings);
    testJSDocTokenStream(
        "  *  @param foo.Bar opt_name this parameter is a name \n" +
        " */ ", tokens, strings);
  }

  public void testJSDocTokenization9() throws Exception {
    List<Integer> tokens = ImmutableList.of(
        STAR, ANNOTATION, STRING, STRING, STRING, STRING, STRING, ANNOTATION,
        STRING, EOL, EOC);
    List<String> strings = ImmutableList.of(
        "param", "foo.Bar", "opt_name", "this", "parameter", "does",
        "media", "blah");
    testJSDocTokenStream(
        " * @param foo.Bar opt_name this parameter does @media blah\n" +
        " */", tokens, strings);
  }

  public void testJSDocTokenization10() throws Exception {
    List<Integer> tokens = ImmutableList.of(STRING, GT, EOC);
    List<String> strings = ImmutableList.of("Array<String");
    testJSDocTokenStream("Array<String>*/", tokens, strings);
  }

  public void testJSDocTokenization11() throws Exception {
    List<Integer> tokens = ImmutableList.of(
        ANNOTATION, LC, STRING, QMARK, RC, EOC, EOF);
    List<String> strings = ImmutableList.of("param", "string");
    testJSDocTokenStream("@param {string?}*/", tokens, strings);
    testJSDocTokenStream(" @param {string?}*/", tokens, strings);
    testJSDocTokenStream("@param { string?}*/", tokens, strings);
    testJSDocTokenStream("@param {string ?}*/", tokens, strings);
    testJSDocTokenStream("@param  {string ?  } */", tokens, strings);
    testJSDocTokenStream("@param { string  ?  }*/", tokens, strings);
    testJSDocTokenStream("@param {string?  }*/", tokens, strings);
  }

  public void testJSDocTokenization12() throws Exception {
    List<Integer> tokens = ImmutableList.of(STRING, ELLIPSIS, EOC);
    List<String> strings = ImmutableList.of("function");

    testJSDocTokenStream("function ...*/", tokens, strings);
  }

  public void testJSDocTokenization13() throws Exception {
    List<Integer> tokens = ImmutableList.of(ELLIPSIS, LB, STRING, RB, EOC);
    List<String> strings = ImmutableList.of("number");

    testJSDocTokenStream("...[number]*/", tokens, strings);
  }

  public void testJSDocTokenization14() throws Exception {
    // Since ES4 type parsing only requires to parse an ellispis when it is
    // followed by a comma (,) we are allowing this case to parse this way.
    // This is a simplification of the tokenizer, but the extra complexity is
    // never used.
    List<Integer> tokens = ImmutableList.of(STRING, LB, STRING, EOC);
    List<String> strings = ImmutableList.of("foo", "bar...");

    testJSDocTokenStream("foo[ bar...*/", tokens, strings);
  }

  public void testJSDocTokenization15() throws Exception {
    List<Integer> tokens = ImmutableList.of(
        STRING, LB, STRING, COMMA, ELLIPSIS, EOC);
    List<String> strings = ImmutableList.of("foo", "bar");

    testJSDocTokenStream("foo[ bar,...*/", tokens, strings);
    testJSDocTokenStream("foo[ bar ,...*/", tokens, strings);
    testJSDocTokenStream("foo[bar, ...*/", tokens, strings);
    testJSDocTokenStream("foo[ bar  ,   ...  */", tokens, strings);
    testJSDocTokenStream("foo [bar,... */", tokens, strings);
  }

  public void testJSDocTokenization16() throws Exception {
    List<Integer> tokens = ImmutableList.of(
        STRING, COLON, COLON, COLON, ELLIPSIS, STRING, COLON, STRING, EOC);
    List<String> strings = ImmutableList.of("foo", "bar", "bar2");

    testJSDocTokenStream("foo:::...bar:bar2*/", tokens, strings);
  }

  public void testJSDocTokenization17() throws Exception {
    List<Integer> tokens = ImmutableList.of(STRING, EOL, EOC);
    List<String> strings = ImmutableList.of("..");

    testJSDocTokenStream("..\n*/", tokens, strings);
  }

  public void testJSDocTokenization18() throws Exception {
    List<Integer> tokens = ImmutableList.of(STRING, EOL, EOC);
    List<String> strings = ImmutableList.of(".");

    testJSDocTokenStream(".\n*/", tokens, strings);
  }

  public void testJSDocTokenization19() throws Exception {
    List<Integer> tokens = ImmutableList.of(ANNOTATION, LC, STAR, RC, EOC);
    List<String> strings = ImmutableList.of("type", "*");

    testJSDocTokenStream("@type {*}*/", tokens, strings);
  }

  public void testJSDocTokenization20() throws Exception {
    List<Integer> tokens = ImmutableList.of(
        ANNOTATION, LC, BANG, STRING, RC, EOC, EOF);
    List<String> strings = ImmutableList.of("param", "Object");
    testJSDocTokenStream("@param {!Object}*/", tokens, strings);
    testJSDocTokenStream(" @param {!Object}*/", tokens, strings);
    testJSDocTokenStream("@param {! Object}*/", tokens, strings);
    testJSDocTokenStream("@param { !Object}*/", tokens, strings);
    testJSDocTokenStream("@param  {!Object  } */", tokens, strings);
    testJSDocTokenStream("@param {  ! Object  }*/", tokens, strings);
    testJSDocTokenStream("@param {!Object  }*/", tokens, strings);
  }

  public void testJSDocTokenization21() throws Exception {
    List<Integer> tokens = ImmutableList.of(
        ANNOTATION, LC, STRING, EQUALS, RC, EOC, EOF);
    List<String> strings = ImmutableList.of("param", "Object");
    testJSDocTokenStream("@param {Object=}*/", tokens, strings);
    testJSDocTokenStream(" @param {Object=}*/", tokens, strings);
    testJSDocTokenStream("@param { Object =}*/", tokens, strings);
    testJSDocTokenStream("@param { Object=}*/", tokens, strings);
    testJSDocTokenStream("@param  {Object=  } */", tokens, strings);
    testJSDocTokenStream("@param { Object = }*/", tokens, strings);
    testJSDocTokenStream("@param {Object=  }*/", tokens, strings);
  }
  
  public void testJSDocLinenoCharno1() throws Exception {
    TokenStream stream = stream(" * @type {string}\n  *   @private");
    testNextJSDocTokenPosition(stream, STAR, 0, 1);
    testNextJSDocTokenPosition(stream, ANNOTATION, 0, 3);
    testNextJSDocTokenPosition(stream, LC, 0, 9);
    testNextJSDocTokenPosition(stream, STRING, 0, 10);
    testNextJSDocTokenPosition(stream, RC, 0, 16);
    testNextJSDocTokenPosition(stream, EOL, 0, 17);
    testNextJSDocTokenPosition(stream, STAR, 1, 2);
    testNextJSDocTokenPosition(stream, ANNOTATION, 1, 6);
  }

  public void testJSDocLinenoCharno2() throws Exception {
    TokenStream stream = stream("@param \n  {Array .<string\n  | null>}");
    testNextJSDocTokenPosition(stream, ANNOTATION, 0, 0);
    testNextJSDocTokenPosition(stream, EOL, 0, 7);
    testNextJSDocTokenPosition(stream, LC, 1, 2);
    testNextJSDocTokenPosition(stream, STRING, 1, 3);
    testNextJSDocTokenPosition(stream, LT, 1, 9);
    testNextJSDocTokenPosition(stream, STRING, 1, 11);
    testNextJSDocTokenPosition(stream, EOL, 1, 17);
    testNextJSDocTokenPosition(stream, PIPE, 2, 2);
    testNextJSDocTokenPosition(stream, STRING, 2, 4);
    testNextJSDocTokenPosition(stream, GT, 2, 8);
    testNextJSDocTokenPosition(stream, RC, 2, 9);
  }

  private void testJSDocTokenStream(String comment, List<Integer> tokens,
      List<String> strings) throws IOException {
    TokenStream stream = stream(comment);
    int stringsIndex = 0;
    for (int token : tokens) {
      int readToken = stream.getJSDocToken();

      // token equality
      if (token != readToken) {
        assertEquals(name(token), name(readToken));
      }

      // string equality
      if (token == ANNOTATION || token == STRING) {
        assertEquals(strings.get(stringsIndex++), stream.getString());
      }
    }
  }

  private void testNextTokenPosition(TokenStream stream, int token,
      int lineno, int charno) throws IOException {
    assertEquals(token, stream.getToken());
    assertLinenoCharno(stream, lineno, charno);
  }

  private void testNextJSDocTokenPosition(TokenStream stream, int token,
      int lineno, int charno) throws IOException {
    assertEquals(token, stream.getJSDocToken());
    assertLinenoCharno(stream, lineno, charno);
  }

  private void assertLinenoCharno(TokenStream stream, int lineno, int charno) {
    assertEquals("lineno", lineno, stream.getLineno());
    assertEquals("charno", charno, stream.getCharno());
  }

  private void assertLinenoOffset(TokenStream stream, int lineno,
      int charnoCursor) {
    assertEquals("lineno", lineno, stream.getLineno());
    assertEquals("offset", charnoCursor, stream.getOffset());
  }

  private TokenStream stream(String source) {
    return new TokenStream(new Parser(null, null),
        new StringReader(source), null, 0);
  }
}
