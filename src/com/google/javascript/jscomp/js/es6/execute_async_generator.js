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

'require base';

/**
 * Handle the execution of an async function.
 *
 * An async function, foo(a, b), will be rewritten as:
 *
 * ```
 * function foo(a, b) {
 *   let $jscomp$async$arguments = arguments;
 *   let $jscomp$async$this = this;
 *   function* $jscomp$async$generator() {
 *     // original body of foo() with:
 *     // - await (x) replaced with yield (x)
 *     // - arguments replaced with $jscomp$async$arguments
 *     // - this replaced with $jscomp$async$this
 *   }
 *   return $jscomp.executeAsyncGenerator($jscomp$async$generator());
 * }
 * ```
 * @param {!Generator<?>} generator
 * @return {!Promise<?>}
 */
$jscomp.executeAsyncGenerator = function(generator) {
  function passValueToGenerator(value) {
    return generator.next(value);
  }

  function passErrorToGenerator(error) {
    return generator.throw(error);
  }

  return new Promise(function(resolve, reject) {
    function handleGeneratorRecord(/** !IIterableResult<*> */ genRec) {
      if (genRec.done) {
        resolve(genRec.value);
      } else {
        // One can await a non-promise, so genRec.value
        // might not already be a promise.
        Promise.resolve(genRec.value)
            .then(passValueToGenerator, passErrorToGenerator)
            .then(handleGeneratorRecord, reject);
      }
    }

    handleGeneratorRecord(generator.next());
  });
};
