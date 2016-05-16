/*
 * Copyright 2016 The Closure Compiler Authors.
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

/**
 * A "class" (ES3 constructor function) exported from a module.
 * You would probably see this in code that was originally written in ES3,
 * and then switched to using modules, but didn't yet switch to other ES6
 * features such as classes.
 *
 * @constructor
 */
export default function ExampleCtor() {};

/**
 * A regular method.
 */
ExampleCtor.prototype.method = function() {
  return 'method';
};

/**
 * A static method.
 */
ExampleCtor.staticMethod = function() {
  return 'staticMethod';
};
