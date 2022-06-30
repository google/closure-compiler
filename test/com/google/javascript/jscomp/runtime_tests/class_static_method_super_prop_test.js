/*
 * Copyright 2022 The Closure Compiler Authors.
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

goog.require('goog.testing.jsunit');

function testSuperPropertiesStaticMethod() {
  class Foo {}

  Foo.x = 2;
  Foo[2] = 3;


  class Bar extends Foo {
    /**
     * A dummy function used to test super calls in static methods
     * @return {number}
     */
    static f() {
      Bar.y = super.x;
      Bar[3] = super[2];
      assertEquals(2, super.x);
      assertEquals(3, super[2]);
      assertEquals(2, Bar.y);
      assertEquals(3, Bar[3]);
      return 6;
    }
  }
  assertEquals(6, Bar.f());
}


function testSuperInStaticMethod() {
  class C1 {
    static foo() {
      return 42;
    }
  }
  class D extends C1 {
    static foo() {
      return super.foo() + 1;
    }
  }
  assertEquals(43, D.foo());
}

function testSuperInDifferentStaticMethod() {
  class C2 {
    static foo() {
      return 12;
    }
  }
  class D extends C2 {
    static foo() {
      throw new Error('unexpected');
    }
    static bar() {
      return super.foo() + 2;
    }
  }
  assertEquals(14, D.bar());
}