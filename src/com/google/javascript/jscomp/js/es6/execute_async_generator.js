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
'require es6/promise';
'require es6/generator_engine';

/**
 * Handles the execution of an async function.
 *
 * An async function, foo(a, b), will be rewritten as:
 *
 * ```
 * function foo(a, b) {
 *   let $jscomp$async$this = this;
 *   let $jscomp$async$arguments = arguments;
 *   let $jscomp$async$super$get$x = () => super.x;
 *   function* $jscomp$async$generator() {
 *     // original body of foo() with:
 *     // - await (x) replaced with yield (x)
 *     // - arguments replaced with $jscomp$async$arguments
 *     // - this replaced with $jscomp$async$this
 *     // - super.x replaced with $jscomp$async$super$get$x()
 *     // - super.x(5) replaced with  $jscomp$async$super$get$x()
 *     //      .call($jscomp$async$this, 5)
 *   }
 *   return $jscomp.executeAsyncGenerator($jscomp$async$generator());
 * }
 * ```
 * @param {!Generator<?>} generator
 * @return {!Promise<?>}
 * @suppress {reportUnknownTypes}
 */
$jscomp.asyncExecutePromiseGenerator = function(generator) {
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

/**
 * Handles the execution of a generator function returning promises.
 *
 * An async function, foo(a, b), will be rewritten as:
 *
 * ```
 * function foo(a, b) {
 *   let $jscomp$async$this = this;
 *   let $jscomp$async$arguments = arguments;
 *   let $jscomp$async$super$get$x = () => super.x;
 *   return $jscomp.asyncExecutePromiseGeneratorFunction(
 *       function* () {
 *         // original body of foo() with:
 *         // - await (x) replaced with yield (x)
 *         // - arguments replaced with $jscomp$async$arguments
 *         // - this replaced with $jscomp$async$this
 *         // - super.x replaced with $jscomp$async$super$get$x()
 *         // - super.x(5) replaced with  $jscomp$async$super$get$x()
 *         //      .call($jscomp$async$this, 5)
 *       });
 * }
 * ```
 * @param {function(): !Generator<?>} generatorFunction
 * @return {!Promise<?>}
 * @suppress {reportUnknownTypes}
 */
$jscomp.asyncExecutePromiseGeneratorFunction = function(generatorFunction) {
  return $jscomp.asyncExecutePromiseGenerator(generatorFunction());
};

/**
 * Handles the execution of a state machine program that represents transpiled
 * async function.
 *
 * @final
 * @param {function(!$jscomp.generator.Context<?>): (void|{value: ?})} program
 * @return {!Promise<?>}
 * @suppress {reportUnknownTypes, visibility}
 */
$jscomp.asyncExecutePromiseGeneratorProgram = function(program) {
  return $jscomp.asyncExecutePromiseGenerator(
      new $jscomp.generator.Generator_(
          new $jscomp.generator.Engine_(
              program)));
};
