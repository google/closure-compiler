package org.mozilla.javascript.tests.json;

import static org.junit.Assert.assertEquals;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;

import org.mozilla.javascript.json.JsonParser;
import org.mozilla.javascript.json.JsonParser.ParseException;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;

public class JsonParserTest {
    private JsonParser parser;
    private Context cx;

    @Before
    public void setUp() {
        cx = Context.enter();
        parser = new JsonParser(cx, cx.initStandardObjects());
    }

    @After
    public void tearDown() {
        Context.exit();
    }

    @Test(expected = ParseException.class)
    public void shouldFailToParseIllegalWhitespaceChars() throws Exception {
        parser.parseValue(" \u000b 1");
    }


    @Test
    public void shouldParseJsonNull() throws Exception {
        assertEquals(null, parser.parseValue("null"));
    }

    @Test(expected = ParseException.class)
    public void shouldFailToParseJavaNull() throws Exception {
        parser.parseValue(null);
    }

    @Test
    public void shouldParseJsonBoolean() throws Exception {
        assertEquals(true, parser.parseValue("true"));
        assertEquals(false, parser.parseValue("false"));
    }

    @Test
    public void shouldParseJsonNumbers() throws Exception {
        assertEquals(1, parser.parseValue("1"));
        assertEquals(-1, parser.parseValue("-1"));
        assertEquals(1.5, parser.parseValue("1.5"));
        assertEquals(1.5e13, parser.parseValue("1.5e13"));
        assertEquals(1.0e16, parser.parseValue("9999999999999999"));
        assertEquals(Double.POSITIVE_INFINITY, parser.parseValue("1.5e99999999"));
    }

    @Test(expected = ParseException.class)
    public void shouldFailToParseDoubleNegativeNumbers() throws Exception {
        parser.parseValue("--5");
    }

    @Test(expected = ParseException.class)
    public void shouldFailToParseNumbersWithDecimalExponent() throws Exception {
        parser.parseValue("5e5.5");
    }

    @Test(expected = ParseException.class)
    public void shouldFailToParseNumbersBeginningWithZero() throws Exception {
        parser.parseValue("05");
    }

    @Test
    public void shouldParseJsonString() throws Exception {
        assertEquals("hello", parser.parseValue("\"hello\""));
        assertEquals("Sch\u00f6ne Gr\u00fc\u00dfe",
                parser.parseValue("\"Sch\\u00f6ne Gr\\u00fc\\u00dfe\""));
        assertEquals("", parser.parseValue(str('"', '"')));
        assertEquals(" ", parser.parseValue(str('"', ' ', '"')));
        assertEquals("\r", parser.parseValue(str('"', '\\', 'r', '"')));
        assertEquals("\n", parser.parseValue(str('"', '\\', 'n', '"')));
        assertEquals("\t", parser.parseValue(str('"', '\\', 't', '"')));
        assertEquals("\\", parser.parseValue(str('"', '\\', '\\', '"')));
        assertEquals("/", parser.parseValue(str('"', '/', '"')));
        assertEquals("/", parser.parseValue(str('"', '\\', '/', '"')));
        assertEquals("\"", parser.parseValue(str('"', '\\', '"', '"')));
    }

    @Test(expected = ParseException.class)
    public void shouldFailToParseEmptyJavaString() throws Exception {
        parser.parseValue("");
    }

    @Test(expected = ParseException.class)
    public void shouldFailToParseSingleDoubleQuote() throws Exception {
        parser.parseValue(str('"'));
    }

    @Test(expected = ParseException.class)
    public void shouldFailToParseStringContainingSingleBackslash() throws Exception {
        parser.parseValue(str('"', '\\', '"'));
    }

    @Test(expected = ParseException.class)
    public void shouldFailToParseStringIllegalStringChars() throws Exception {
        parser.parseValue(str('"', '\n', '"'));
    }

    @Test
    public void shouldParseEmptyJsonArray() throws Exception {
        assertEquals(0, ((NativeArray) parser.parseValue("[]")).getLength() );
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldParseHeterogeneousJsonArray() throws Exception {
        NativeArray actual = (NativeArray) parser
                .parseValue("[ \"hello\" , 3, null, [false] ]");
        assertEquals("hello", actual.get(0, actual));
        assertEquals(3, actual.get(1, actual));
        assertEquals(null, actual.get(2, actual));

        NativeArray innerArr = (NativeArray) actual.get(3, actual);
        assertEquals(false, innerArr.get(0, innerArr));

        assertEquals(4, actual.getLength());
    }

    @Test(expected = ParseException.class)
    public void shouldFailToParseArrayWithInvalidElements() throws Exception {
        parser.parseValue("[wtf]");
    }

    @Test
    @SuppressWarnings({ "serial", "unchecked" })
    public void shouldParseJsonObject() throws Exception {
        String json = "{" +
                "\"bool\" : false, " +
                "\"str\"  : \"xyz\", " +
                "\"obj\"  : {\"a\":1} " +
                "}";
    NativeObject actual = (NativeObject) parser.parseValue(json);
    assertEquals(false, actual.get("bool", actual));
    assertEquals("xyz", actual.get("str", actual));

    NativeObject innerObj = (NativeObject) actual.get("obj", actual);
    assertEquals(1, innerObj.get("a", innerObj));
    }

    @Test(expected = ParseException.class)
    public void shouldFailToParseJsonObjectsWithInvalidFormat() throws Exception {
        parser.parseValue("{\"only\", \"keys\"}");
    }

    @Test(expected = ParseException.class)
    public void shouldFailToParseMoreThanOneToplevelValue() throws Exception {
        parser.parseValue("1 2");
    }

    @Test(expected = ParseException.class)
    public void shouldFailToParseStringTruncatedUnicode() throws Exception {
            parser.parseValue("\"\\u00f\"");
    }

    @Test(expected = ParseException.class)
    public void shouldFailToParseStringControlChars1() throws Exception {
            parser.parseValue("\"\u0000\"");
    }

    @Test(expected = ParseException.class)
    public void shouldFailToParseStringControlChars2() throws Exception {
            parser.parseValue("\"\u001f\"");
    }

    @Test
    public void shouldAllowTrailingWhitespace() throws Exception {
        parser.parseValue("1 ");
    }

    @Test(expected = ParseException.class)
    public void shouldThrowParseExceptionWhenIncompleteObject() throws Exception {
        parser.parseValue("{\"a\" ");
    }

    @Test(expected = ParseException.class)
    public void shouldThrowParseExceptionWhenIncompleteArray() throws Exception {
        parser.parseValue("[1 ");
    }

    private String str(char... chars) {
        return new String(chars);
    }
}

