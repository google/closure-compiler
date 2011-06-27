package org.mozilla.javascript.tests.json;

import static org.mozilla.javascript.json.JsonLexer.Token.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.mozilla.javascript.json.JsonLexer;
import org.mozilla.javascript.json.JsonLexer.Token;

import org.junit.Test;

public class JsonLexerTest {

	@Test
	public void shouldLexSingleTokens() throws Exception {
		assertLexesSingleToken("null", NULL);
		assertLexesSingleToken("false", BOOLEAN);
		assertLexesSingleToken("true", BOOLEAN);
		assertLexesSingleToken("-1.0e-4", NUMBER);
		assertLexesSingleToken("\"\"", STRING);
		assertLexesSingleToken("[", OPEN_BRACKET);
		assertLexesSingleToken("{", OPEN_BRACE);
		assertLexesSingleToken("\"a\"", STRING);
	}
	
	@Test
	public void shouldLexSequenceOfTokens() throws Exception {
		assertLexesMultipleTokens("[1]", OPEN_BRACKET, NUMBER, CLOSE_BRACKET);
		assertLexesMultipleTokens("[1,false]", OPEN_BRACKET, NUMBER, COMMA, BOOLEAN, CLOSE_BRACKET);
		assertLexesMultipleTokens("{\"a\":1}", OPEN_BRACE, STRING, COLON, NUMBER, CLOSE_BRACE);
		assertLexesMultipleTokens("{\"a\":1,\"b\":false}", OPEN_BRACE, STRING, COLON, NUMBER, COMMA, STRING, COLON, BOOLEAN, CLOSE_BRACE);
	}
	
	@Test
	public void shouldIgnoreWhitespace() throws Exception {
		assertLexesSingleToken("  1\t\n \t", NUMBER);
		assertLexesMultipleTokens(" [ 1 , null\t\n ]\t", OPEN_BRACKET, NUMBER, COMMA, NULL, CLOSE_BRACKET);
	}

	private void assertLexesMultipleTokens(String json, Token... tokens) {
		JsonLexer lexer = new JsonLexer(json);
		for (Token token : tokens) {
			assertTrue("Expected "+token, lexer.moveNext());
			assertEquals(token, lexer.getToken());
		}
		assertFalse(lexer.moveNext());
	}
	
	private void assertLexesSingleToken(String lexeme, Token token) {
		JsonLexer lexer = new JsonLexer(lexeme);
		assertTrue(lexer.moveNext());
		assertEquals(token, lexer.getToken());
		assertFalse(lexer.moveNext());
	}

}

