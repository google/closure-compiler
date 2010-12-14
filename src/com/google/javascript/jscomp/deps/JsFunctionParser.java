/*
 * Copyright 2008 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.deps;

import com.google.common.base.CharMatcher;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.ErrorManager;

import java.io.Reader;
import java.io.StringReader;
import java.util.Collection;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parser that can extract dependency information from a .js file.
 * 
 * @author agrieve@google.com (Andrew Grieve)
 * @author ielashi@google.com (Islam El-Ashi)
 */
public class JsFunctionParser extends JsFileLineParser {
  
  public static class SymbolInfo {
    public final String functionName;
    public final String symbol;
    
    private SymbolInfo(String functionName, String symbol) {
      this.functionName = functionName;
      this.symbol = symbol;
    }
  }

  private static Logger logger = Logger.getLogger(JsFunctionParser.class.getName());

  /** Pattern for matching functions. */
  private Pattern pattern;

  /** Matcher used in the parsing. */
  private Matcher matcher;

  /** Symbols parsed. */
  private Collection<SymbolInfo> symbols;
  
  /** Functions to parse */
  private Collection<String> functionsToParse;

  /**
   * Constructor
   *
   * @param functions Functions to parse.
   * @param errorManager Handles parse errors.
   */
  public JsFunctionParser(Collection<String> functions, ErrorManager errorManager) {
    super(errorManager);
    functionsToParse = functions;
    pattern = getPattern(functions);
    matcher = pattern.matcher("");
  }

  /**
   * Constructs a pattern to extract the arguments of the given functions.
   * 
   * @param functions Functions to parse.
   * @return A pattern to extract {@code functions}' arguments.
   */
  private Pattern getPattern(Collection<String> functions) {
    StringBuilder sb = new StringBuilder("(?:^|;)\\s*(");
    
    for (String function : functions) {
      sb.append(Pattern.quote(function) + "|");
    }
    
    // remove last '|'
    sb.deleteCharAt(sb.length() - 1);
    sb.append(")\\s*\\((.*?)\\)");
    
    return Pattern.compile(sb.toString());
  }
  
  /**
   * Parses the given file and returns the dependency information that it
   * contained.
   *
   * @param filePath Path to the file to parse.
   * @param fileContents The contents to parse.
   * @return A collection containing all symbols found in the
   *     file.
   */
  public Collection<SymbolInfo> parseFile(String filePath, String fileContents) {
    return parseReader(filePath, new StringReader(fileContents));
  }

  private Collection<SymbolInfo> parseReader(String filePath, Reader fileContents) {
    symbols = Lists.newArrayList();

    logger.fine("Parsing Source: " + filePath);
    doParse(filePath, fileContents);

    return symbols;
  }
  
  /**
   * Parses a line of javascript, extracting dependency information.
   */
  @Override
  protected boolean parseLine(String line) throws ParseException {
    boolean hasFunctions = false;
    boolean parseLine = false;

    // Quick sanity check that will catch most cases. This is a performance
    // win for people with a lot of JS.
    for (String function : functionsToParse) {
      if (line.indexOf(function) != -1) {
        parseLine = true;
        break;
      }
    }
    
    if (parseLine) {
      matcher.reset(line);
      while (matcher.find()) {
        hasFunctions = true;
        String functionName = matcher.group(1);
        String arg = parseJsString(matcher.group(2)); // Parse the param.  
        symbols.add(new SymbolInfo(functionName, arg));
      }
    }

    return !shortcutMode || hasFunctions ||
        CharMatcher.WHITESPACE.matchesAllOf(line);
  }
}
