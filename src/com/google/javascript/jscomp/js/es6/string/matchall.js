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
 * @fileoverview
 * @suppress {uselessCode}
 */
'require es6/symbol';
'require util/polyfill';

$jscomp.polyfill('String.prototype.matchAll', function(orig) {
  if (orig) return orig;

  /**
   * Returns an iterator of all results matching a string against a
   * regular expression, including capturing groups.
   *
   * Polyfills the instance method String.prototype.matchAll().
   *
   * The spec calls for any non-RegExp object to be automatically converted,
   * so we accept anything here, even though our externs only allow
   * RegExp|string.
   *
   * @this {string}
   * @param {*} regexp
   * A regular expression object. If a non-RegExp object obj is passed,
   * it is implicitly converted to a RegExp with a global tag by using
   * new RegExp(obj).
   * @return {!IteratorIterable<!RegExpResult>}
   */
  var polyfill = function(regexp) {
    if (regexp instanceof RegExp && !regexp.global) {
      throw new TypeError('RegExp passed into String.prototype.matchAll() must have global tag.');
    }
    var /** !RegExp */ regexCopy =
        new RegExp(regexp, regexp instanceof RegExp ? undefined : 'g');
    var matchString = this;
    var /** boolean */ finished = false;
    var matchAllIterator = {
      next: function() {
        if (finished) {
          return {value: undefined, done: true};
        }

        var match = regexCopy.exec(matchString);
        if (!match) {
          finished = true;
          return {value: undefined, done: true};
        }
        if (match[0] === '') {
          /**
           * See https://262.ecma-international.org/10.0/#sec-advancestringindex
           * and
           * https://github.com/ljharb/String.prototype.matchAll/blob/5e1a234e65d03e5312ea1d3cb617444f4ffa6e23/helpers/RegExpStringIterator.js#L71
           *
           * matchAll() is not allowed to get "stuck" returning an empty
           * string match infinitely, so we must make sure lastIndex always
           * increases.
           *
           * Also assume that `fullUnicode === false`. Any browser that supports
           * unicode regexes should not need this polyfill.
           */
          regexCopy.lastIndex += 1;
        }

        return {value: match, done: false};
      }
    };
    matchAllIterator[Symbol.iterator] = function() { return matchAllIterator; };
    return /**@type {!IteratorIterable<!RegExpResult>}> */ (matchAllIterator);
  };
  return polyfill;
}, 'es_2020', 'es3');
