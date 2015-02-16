/*
 * Copyright 2009 The Closure Compiler Authors.
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

/**
 * @author mkretzschmar@google.com (Martin Kretzschmar)
 */
public class CheckMissingGetCssNameTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CombinedCompilerPass(
        compiler,
        new CheckMissingGetCssName(compiler, CheckLevel.ERROR, "goog-[a-z-]*"));
  }

  public void testMissingGetCssName() {
    testMissing("var s = 'goog-inline-block'");
    testMissing("var s = 'CSS_FOO goog-menu'");
    testMissing("alert('goog-inline-block ' + goog.getClassName('CSS_FOO'))");
    testMissing("html = '<div class=\"goog-special-thing\">Hello</div>'");
  }

  public void testRecognizeGetCssName() {
    testNotMissing("var s = goog.getCssName('goog-inline-block')");
  }

  public void testIgnoreGetUniqueIdArguments() {
    testNotMissing("var s = goog.events.getUniqueId('goog-some-event')");
    testNotMissing("var s = joe.random.getUniqueId('joe-is-a-goob')");
  }

  public void testIgnoreAssignmentsToIdConstant() {
    testNotMissing("SOME_ID = 'goog-some-id'");
    testNotMissing("SOME_PRIVATE_ID_ = 'goog-some-id'");
    testNotMissing("var SOME_ID_ = 'goog-some-id'");
  }

  public void testNotMissingGetCssName() {
    testNotMissing("s = 'not-a-css-name'");
    testNotMissing("s = 'notagoog-css-name'");
  }

  public void testDontCrashIfTheresNoQualifiedName() {
    testMissing("things[2].DONT_CARE_ABOUT_THIS_KIND_OF_ID = "
                + "'goog-inline-block'");
    testMissing("objects[3].doSomething('goog-inline-block')");
  }

  private void testMissing(String js) {
    testError(js, CheckMissingGetCssName.MISSING_GETCSSNAME);
  }

  private void testNotMissing(String js) {
    test(js, js);
  }
}
