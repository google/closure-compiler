/*
 * Copyright 2006 The Closure Compiler Authors.
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


public class RemoveTryCatchTest extends CompilerTestCase {

  public RemoveTryCatchTest() {
    // do not compare as tree because we use synthetic blocks
    super("", false);
  }

  @Override public CompilerPass getProcessor(Compiler compiler) {
    return new RemoveTryCatch(compiler);
  }

  @Override public int getNumRepetitions() {
    // Use only one repetition because test code contains JSDoc comments.
    return 1;
  }

  public void testRemoveTryCatch() {
    test("try{var a=1;}catch(ex){var b=2;}",
         "var b;var a=1");
    test("try{var a=1;var b=2}catch(ex){var c=3;var d=4;}",
         "var d;var c;var a=1;var b=2");
    test("try{var a=1;var b=2}catch(ex){}",
         "var a=1;var b=2");
  }

  public void testRemoveTryFinally() {
    test("try{var a=1;}finally{var c=3;}",
         "var a=1;var c=3");
    test("try{var a=1;var b=2}finally{var e=5;var f=6;}",
         "var a=1;var b=2;var e=5;var f=6");
  }

  public void testRemoveTryCatchFinally() {
    test("try{var a=1;}catch(ex){var b=2;}finally{var c=3;}",
         "var b;var a=1;var c=3");
    test("try{var a=1;var b=2}catch(ex){var c=3;var d=4;}finally{var e=5;" +
         "var f=6;}",
         "var d;var c;var a=1;var b=2;var e=5;var f=6");
  }

  public void testPreserveTryBlockContainingReturnStatement() {
    testSame("function f(){var a;try{a=1;return}finally{a=2}}");
  }

  public void testPreserveAnnotatedTryBlock() {
    test("/** @preserveTry */try{var a=1;}catch(ex){var b=2;}",
         "try{var a=1}catch(ex){var b=2}");
  }

  public void testIfTryFinally() {
    test("if(x)try{y}finally{z}", "if(x){y;z}");
  }

  public void testIfTryCatch() {
    test("if(x)try{y;z}catch(e){}", "if(x){y;z}");
  }
}
