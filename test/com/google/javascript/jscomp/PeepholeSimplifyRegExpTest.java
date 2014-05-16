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

package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;


public class PeepholeSimplifyRegExpTest extends CompilerTestCase {

  public final void testWaysOfMatchingEmptyString() {
    testSame("/(?:)/");
    test("/(?:)/i", "/(?:)/");  // We can get rid of i flag when no letters.
    test("/.{0}/i", "/(?:)/");
    test("/[^\\0-\\uffff]{0}/", "/(?:)/");
    // Cannot get rid of capturing groups.
    testSame("/(){0}/");
  }

  public final void testCharsetOptimizations() {
    testSame("/./");
    test("/[\\0-\\uffff]/", "/[\\S\\s]/");
    test("/[^\\0-\\uffff]/", "/(?!)/");
    test("/[^\\0-\\x40\\x42-\\uffff]/", "/A/");
    test("/[0-9a-fA-F]/i", "/[\\da-f]/i");
    test("/[0-9a-zA-Z_$]/i", "/[\\w$]/");
    test("/[()*+\\-,]/g", "/[(--]/g");
    test("/[()*+\\-,z]/g", "/[(--z]/g");
    test("/[\\-\\.\\/0]/g", "/[--0]/g");
    test("/[\\-\\.\\/0\\n]/g", "/[\\n\\--0]/g");
    test("/[\\[\\\\\\]]/g", "/[[-\\]]/g");
    test("/[\\[\\\\\\]\\^]/g", "/[[-^]/g");
    test("/[\\^`_]/g", "/[\\^-`]/g");
    test("/[^\\^`_]/g", "/[^^-`]/g");
    test("/^(?=[^a-z])/i", "/^(?=[\\W\\d_])/");
    test("/^[^a-z0-9]/i", "/^[\\W_]/");
    test("/[0-FA-Z]/", "/[0-Z]/");
    test("/[0-9]/", "/\\d/");
    test("/[^0-9]/", "/\\D/");
    testSame("/\\D/");
    test("/[_a-z0-9]/i", "/\\w/");
    test("/[0-9_a-z]/i", "/\\w/");
    test("/[_a-z0-9]/", "/[\\d_a-z]/");
    test("/[_E-Za-f0-9]/i", "/\\w/");
    test("/[E-Za-f]/i", "/[a-z]/i");
    test("/[_E-Za-f0-9]/", "/[\\dE-Z_a-f]/");
    // Test case normalization.
    // U+00CA and U+00EA are E and e with ^ above
    test("/[\\u00ca\\u00ea]/", "/[\\xca\\xea]/");
    test("/[\\u00ca\\u00ea]/i", "/\\xca/i");
    // IE (at least 6, 7, and 8) do not include \xA0 in \s so when an author
    // explicitly includes it make sure it appears in the output.
    testSame("/^[\\s\\xa0]*$/");
    test("/^(?:\\s|\\xA0)*$/", "/^[\\s\\xa0]*$/");
  }

  public final void testCharsetFixup() {
    testSame("/[a-z]/i");
    // This is the case.  The below produces no output in squarefree.
    // (function () {
    //   // Runs to just before the letter 'a' and starts right after 'z'.
    //   var re = /[^\0-`{-\uffff]/i
    //   for (var i = 0; i < 0x10000; ++i) {
    //     var s = String.fromCharCode(i);
    //     if (re.test(s)) { print(s + ' : ' + s.charCodeAt(0).toString(16)); }
    //   }
    // })()
    test("/[^\\0-`{-\\uffff]/i", "/(?!)/");
    // This looks a bit odd, but
    // /[^a-z]/i is the same as all non-word characters, all digits, and _ and
    // /[\W\d_]/ is the same length.
    test("/[^a-z]/i", "/[\\W\\d_]/");
  }

  public final void testGroups() {
    testSame("/foo(bar)baz/");
  }

  public final void testBackReferences() {
    testSame("/foo(bar)baz(?:\\1|\\x01)boo/");
    // But when there is no group to refer to, then the back-reference *is*
    // the same as an octal escape.
    test("/foo(?:bar)baz(?:\\1|\\x01)boo/", "/foobarbaz\\x01boo/");
    // \\8 is never an octal escape.  If there is no 8th group, then it
    // is the literal character '8'
    test("/foo(?:bar)baz(?:\\8|8)boo/", "/foobarbaz8boo/");
    // \10 can be a capturing group.
    test("/(1?)(2?)(3?)(4?)(5?)(6?)(7?)(8?)(9?)(A?)(B?)"
         + "\\12\\11\\10\\9\\8\\7\\6\\5\\4\\3\\2\\1\\0/",
         "/(1?)(2?)(3?)(4?)(5?)(6?)(7?)(8?)(9?)(A?)(B?)"
         // \\12 does not match any group, so is treated as group 1 followed
         // by literal 2.
         + "\\1(?:2)\\11\\10\\9\\8\\7\\6\\5\\4\\3\\2\\1\\0/");
    // But \1 should not be emitted followed by a digit un-parenthesized.
    test("/(1?)(2?)(3?)(4?)(5?)(6?)(7?)(8?)(9?)(A?)(B?)(?:\\1)0/",
         "/(1?)(2?)(3?)(4?)(5?)(6?)(7?)(8?)(9?)(A?)(B?)\\1(?:0)/");
    // \012 is never treated as a group even when there are 12 groups.
    test("/(1?)(2?)(3?)(4?)(5?)(6?)(7?)(8?)(9?)(A?)(B?)(C?)"
         + "\\012\\11\\10\\9\\8\\7\\6\\5\\4\\3\\2\\1\\0/",
         "/(1?)(2?)(3?)(4?)(5?)(6?)(7?)(8?)(9?)(A?)(B?)(C?)"
         + "\\n\\11\\10\\9\\8\\7\\6\\5\\4\\3\\2\\1\\0/");
  }

  public final void testSingleCharAlterations() {
    test("/a|B|c|D/i", "/[a-d]/i");
    test("/a|B|c|D/", "/[BDac]/");
    test("/a|[Bc]|D/", "/[BDac]/");
    test("/[aB]|[cD]/", "/[BDac]/");
    test("/a|B|c|D|a|B/i", "/[a-d]/i");  // Duplicates.
    test("/a|A|/i", "/a?/i");
  }

  public final void testAlterations() {
    testSame("/foo|bar/");
    test("/Foo|BAR/i", "/foo|bar/i");
    test("/Foo||BAR/", "/Foo||BAR/");
    test("/Foo|BAR|/", "/Foo|BAR|/");
  }

  public final void testNestedAlterations() {
    test("/foo|bar|(?:baz|boo)|far/", "/foo|bar|baz|boo|far/");
  }

  public final void testEscapeSequencesAndNonLatinChars() {
    test("/\u1234/i", "/\\u1234/");
    testSame("/\\u1234/");
    test("/\u00A0/", "/\\xa0/");
    test("/\\u00A0/", "/\\xa0/");
    test("/\\u00a0/", "/\\xa0/");
  }

  public final void testAnchors() {
    // m changes the meaning of anchors which is useless if there are none.
    testSame("/foo(?!$)/gm");
    test("/./m", "/./");
    test("/\\^/m", "/\\^/");
    test("/[\\^]/m", "/\\^/");
    testSame("/(^|foo)bar/");
    testSame("/^.|.$/gm");
    test("/foo(?=)$/m", "/foo$/m");
    // We can get rid of the g when there are no capturing groups and the
    // pattern is fully anchored.
    test("/^foo$/g", "/^foo$/");
  }

  public final void testRepetitions() {
    testSame("/a*/");
    testSame("/a+/");
    testSame("/a+?/");
    testSame("/a?/");
    testSame("/a{6}/");
    testSame("/a{4,}/");
    test("/a{3,}/",
         "/aaa+/");
    testSame("/a{4,6}/");
    testSame("/a{4,6}?/");
    test("/(?:a?)?/", "/a?/");
    test("/(?:a?)*/", "/a*/");
    test("/(?:a*)?/", "/a*/");
    test("/a(?:a*)?/", "/a+/");
    test("/(?:a{2,3}){3,4}/", "/a{6,12}/");
    test("/a{2,3}a{3,4}/", "/a{5,7}/");
    testSame("/a{5,7}b{5,6}/");
    test("/a{2,3}b{3,4}/",
         "/aaa?bbbb?/");
    test("/a{3}b{3,4}/",
         "/aaabbbb?/");
    testSame("/[a-z]{1,2}/");
    test("/\\d{1,2}/",
         "/\\d\\d?/");
    test("/a*a*/", "/a*/");
    test("/a+a+/", "/aa+/");
    test("/a+a*/", "/a+/");
    // We don't conflate literal curly brackets with repetitions.
    testSame("/a\\{3,1}/");
    test("/a(?:{3,1})/", "/a\\{3,1}/");
    test("/a{3\\,1}/", "/a\\{3,1}/");
    testSame("/a\\{3}/");
    testSame("/a\\{3,}/");
    testSame("/a\\{1,3}/");
    // We don't over-escape curly brackets.
    testSame("/a{/");
    testSame("/a{}/");
    testSame("/a{x}/");
    testSame("/a{-1}/");
    testSame("/a{,3}/");
    testSame("/{{[a-z]+}}/");
    testSame("/{\\{0}}/");
    testSame("/{\\{0?}}/");
  }

  public final void testMoreCharsets() {
    test("var a = /[\\x00\\x22\\x26\\x27\\x3c\\x3e]/g",
         "var a = /[\\0\"&'<>]/g");
    test("var b = /[\\x00\\x22\\x27\\x3c\\x3e]/g",
         "var b = /[\\0\"'<>]/g");
    test("var c = /[\\x00\\x09-\\x0d \\x22\\x26\\x27\\x2d\\/\\x3c-\\x3e`"
         + "\\x85\\xa0\\u2028\\u2029]/g",
         "var c = /[\\0\\t-\\r \"&'/<->`\\x85\\xa0\\u2028\\u2029-]/g");
    test("var d = /[\\x00\\x09-\\x0d \\x22\\x27\\x2d\\/\\x3c-\\x3e`"
         + "\\x85\\xa0\\u2028\\u2029]/g",
         "var d = /[\\0\\t-\\r \"'/<->`\\x85\\xa0\\u2028\\u2029-]/g");
    test("var e = /[\\x00\\x08-\\x0d\\x22\\x26\\x27\\/\\x3c-\\x3e\\\\"
         + "\\x85\\u2028\\u2029]/g",
         "var e = /[\\0\\b-\\r\"&'/<->\\\\\\x85\\u2028\\u2029]/g");
    test("var f = /[\\x00\\x08-\\x0d\\x22\\x24\\x26-\\/\\x3a\\x3c-\\x3f"
         + "\\x5b-\\x5e\\x7b-\\x7d\\x85\\u2028\\u2029]/g",
         "var f = /[\\0\\b-\\r\"$&-/:<-?[-^{-}\\x85\\u2028\\u2029]/g");
    test("var g = /[\\x00\\x08-\\x0d\\x22\\x26-\\x2a\\/\\x3a-\\x3e@\\\\"
         + "\\x7b\\x7d\\x85\\xa0\\u2028\\u2029]/g",
         "var g = /[\\0\\b-\\r\"&-*/:->@\\\\{}\\x85\\xa0\\u2028\\u2029]/g");
    test("var h = /^(?!-*(?:expression|(?:moz-)?binding))(?:[.#]?-?"
         + "(?:[_a-z0-9][_a-z0-9-]*)(?:-[_a-z][_a-z0-9-]*)*-?|-?"
         + "(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9])(?:[a-z]{1,2}|%)?|!important|)$/i",
         "var h = /^(?!-*(?:expression|(?:moz-)?binding))(?:[#.]?-?"
         + "\\w[\\w-]*(?:-[_a-z][\\w-]*)*-?|-?"
         + "(?:\\d+(?:\\.\\d*)?|\\.\\d)(?:[a-z]{1,2}|%)?|!important|)$/i");
    test("var i = /^(?:(?:https?|mailto):|[^&:\\/?#]*(?:[\\/?#]|$))/i",
         "var i = /^(?:(?:https?|mailto):|[^#&/:?]*(?:[#/?]|$))/i");
    test("var j = /^(?!style|on|action|archive|background|cite|classid"
         + "|codebase|data|dsync|href|longdesc|src|usemap)(?:[a-z0-9_$:-]*"
         + "|dir=(?:ltr|rtl))$/i",
         "var j = /^(?!style|on|action|archive|background|cite|classid"
         + "|codebase|data|dsync|href|longdesc|src|usemap)(?:[\\w$:-]*"
         + "|dir=(?:ltr|rtl))$/i");
    test("var k = /^(?!script|style|title|textarea|xmp|no)[a-z0-9_$:-]*$/i",
         "var k = /^(?!script|style|title|textarea|xmp|no)[\\w$:-]*$/i");
    test("var l = /<(?:!|\\/?[a-z])(?:[^>'\"]|\"[^\"]*\"|'[^']*')*>/gi",
         "var l = /<(?:!|\\/?[a-z])(?:[^\"'>]|\"[^\"]*\"|'[^']*')*>/gi");
  }

  public final void testMoreRegularExpression() {
    testSame("/\"/");
    testSame("/'/");
    test("/(?:[^<\\/\"'\\s\\\\]|<(?!\\/script))+/i",
         "/(?:[^\\s\"'/<\\\\]|<(?!\\/script))+/i");
    testSame("/-->/");
    testSame("/<!--/");
    testSame("/<\\/(\\w+)\\b/");
    testSame("/<\\/?/");
    test("/<script(?=[\\s>\\/]|$)/i", "/<script(?=[\\s/>]|$)/i");
    test("/<style(?=[\\s>\\/]|$)/i", "/<style(?=[\\s/>]|$)/i");
    test("/<textarea(?=[\\s>\\/]|$)/i", "/<textarea(?=[\\s/>]|$)/i");
    test("/<title(?=[\\s>\\/]|$)/i", "/<title(?=[\\s/>]|$)/i");
    test("/<xmp(?=[\\s>\\/]|$)/i", "/<xmp(?=[\\s/>]|$)/i");
    testSame("/[\"']/");
    test("/[\\\\)\\s]/", "/[\\s)\\\\]/");
    test("/[\\f\\r\\n\\u2028\\u2029]/", "/[\\n\\f\\r\\u2028\\u2029]/");
    test("/[\\n\\r\\f]/", "/[\\n\\f\\r]/");
    testSame("/\\*\\//");
    testSame("/\\//");
    testSame("/\\/\\*/");
    testSame("/\\/\\//");
    testSame("/\\\\(?:\\r\\n?|[\\n\\f\"])/");
    testSame("/\\\\(?:\\r\\n?|[\\n\\f'])/");
    testSame("/\\burl\\s*\\(\\s*([\"']?)/i");
    testSame("/\\s+/");
    test("/^(?:[^'\\\\\\n\\r\\u2028\\u2029<]|\\\\(?:\\r\\n?|[^\\r<]"
         + "|<(?!\\/script))|<(?!\\/script))/i",
         "/^(?:[^\\n\\r'<\\\\\\u2028\\u2029]|\\\\(?:\\r\\n?|[^\\r<]"
         + "|<(?!\\/script))|<(?!\\/script))/i");
    test("/^(?:[^\\\"\\\\\\n\\r\\u2028\\u2029<]|\\\\(?:\\r\\n?"
         + "|[^\\r<]|<(?!\\/script))|<(?!\\/script))/i",
         "/^(?:[^\\n\\r\"<\\\\\\u2028\\u2029]|\\\\(?:\\r\\n?"
         + "|[^\\r<]|<(?!\\/script))|<(?!\\/script))/i");
    test("/^(?:[^\\[\\\\\\/<\\n\\r\\u2028\\u2029]|\\\\[^\\n\\r\\u2028\\u2029]"
         + "|\\\\?<(?!\\/script)|\\[(?:[^\\]\\\\<\\n\\r\\u2028\\u2029]|"
         + "\\\\(?:[^\\n\\r\\u2028\\u2029]))*|\\\\?<(?!\\/script)\\])/i",
         "/^(?:[^\\n\\r/<[\\\\\\u2028\\u2029]|\\\\."
         + "|\\\\?<(?!\\/script)|\\[(?:[^\\n\\r<\\\\\\]\\u2028\\u2029]|"
         + "\\\\.)*|\\\\?<(?!\\/script)])/i");
    testSame("/^(?=>|\\s+[\\w-]+\\s*=)/");
    test("/^(?=[\\/\\s>])/", "/^(?=[\\s/>])/");
    test("/^(?=[^\"'\\s>])/", "/^(?=[^\\s\"'>])/");
    testSame("/^/");
    testSame("/^[^<]+/");
    test("/^[a-z0-9:-]*(?:[a-z0-9]|$)/i", "/^[\\d:a-z-]*(?:[^\\W_]|$)/i");
    testSame("/^[a-z]+/i");
    testSame("/^\\s*\"/");
    testSame("/^\\s*'/");
    testSame("/^\\s*([a-z][\\w-]*)/i");
    testSame("/^\\s*=/");
    testSame("/^\\s*\\/?>/");
    testSame("/^\\s+$/");
    testSame("/^\\s+/");
  }

  public final void testPrecedence() {
    // Repetition binds more tightly than concatenation.
    testSame("/ab?/");
    testSame("/(?:ab)?/");
    // Concatenation bind more tightly than alterations.
    testSame("/foo|bar/");
    testSame("/f(?:oo|ba)r/");
  }

  public final void testMalformedRegularExpressions() {
    test(
        "/(?<!foo)/", "/(?<!foo)/",  // Lookbehind not valid in ES.
        null,  // No error.
        CheckRegExp.MALFORMED_REGEXP);  // Warning.
    test(
        "/(/", "/(/",
        null,  // No error.
        CheckRegExp.MALFORMED_REGEXP);  // Warning.
    test(
        "/)/", "/)/",
        null,  // No error.
        CheckRegExp.MALFORMED_REGEXP);  // Warning.
    test(
        "/\\uabc/", "/\\uabc/",
        null,  // No error.
        CheckRegExp.MALFORMED_REGEXP);  // Warning.
    test(
        "/\\uabcg/", "/\\uabcg/",
        null,  // No error.
        CheckRegExp.MALFORMED_REGEXP);  // Warning.
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    final CompilerPass simplifier = new PeepholeOptimizationsPass(
        compiler, new PeepholeSimplifyRegExp());
    final CompilerPass checker = new CheckRegExp(compiler);

    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        checker.process(externs, root);
        simplifier.process(externs, root);
      }
    };
  }
}
