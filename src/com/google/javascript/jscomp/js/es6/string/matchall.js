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
    // The spec says it should be an error to pass a non-global RegExp to
    // matchAll, but MDN and Chrome agree that a single match should be done
    // instead.
    var matchOnlyOnce = regexp instanceof RegExp && !regexp.global;
    // Get a fresh RegExp object for doing the matches.
    // 1. Conversion from non-regex may be required
    // 2. Avoid modifying lastIndex on the original RegExp
    // If non RegExp objects are passed they should be converted to a RegExp
    // with a global flag. However we are not able to pass in RegExp into
    // new RegExp() with a flag field in older browers so we do this check
    var /** !RegExp */ regexCopy = new RegExp(regexp, regexp instanceof RegExp ? undefined : 'g');
    var matchString = this;
    var /** boolean */ finished = false;
    var matchAllIterator = {
      next: function() {
        var result = {};
        var previousIndex = regexCopy.lastIndex;
        if (finished) {
          return {value: undefined, done: true};
        } else {
          var /** ?RegExpResult */ match = regexCopy.exec(matchString);
          if (!match) {
            finished = true;
            return {value: undefined, done: true};
          }
          if (matchOnlyOnce) {
            finished = true;
          } else {
            if (regexCopy.lastIndex === previousIndex) {
              // matchAll() is not allowed to get "stuck" returning an empty
              // string match infinitely, so we must make sure lastIndex always
              // increases.
              regexCopy.lastIndex += 1;
            }
          }
          result.value = match;
          result.done = false;
          return result;
        }
      }
    };
    matchAllIterator[Symbol.iterator] = function() { return matchAllIterator; };
    return /**@type {!IteratorIterable<!RegExpResult>}> */ (matchAllIterator);
  };
  return polyfill;
}, 'es_next', 'es3');
