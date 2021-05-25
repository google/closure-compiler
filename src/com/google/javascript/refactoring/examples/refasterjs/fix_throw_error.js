/*
 * Copyright 2014 The Closure Compiler Authors.
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
 * @fileoverview RefasterJS template for fixing common mistakes when throwing
 * errors.
 */

/**
 * People should not throw a string as an error since stack traces are not
 * included when strings are thrown.
 * @param {string} msg
 */
function before_DoNotThrowString(msg) {
  throw msg;
}

/**
 * @param {string} msg
 */
function after_DoNotThrowString(msg) {
  throw new Error(msg);
}

/**
 * throw Error and throw new Error are equivalent except that the latter is more
 * clear that a new object is being constructed.
 * @param {*} anyArg
 */
function before_ThrowNewError(anyArg) {
  throw Error(anyArg);
}

/**
 * @param {*} anyArg
 */
function after_ThrowNewError(anyArg) {
  throw new Error(anyArg);
}
