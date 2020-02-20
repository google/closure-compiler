/*
 * Copyright 2020 The Closure Compiler Authors.
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
 * @fileoverview Polyfill for a tagged template's argument
 * @suppress {uselessCode}
 */
'require base';

/**
 * Simply accepts an ITemplateArray and returns it after setting its raw
 * property
 * @param {!ITemplateArray} arrayStrings
 * @return {!ITemplateArray}
 * @noinline
 */
$jscomp.createTemplateTagFirstArg = function(arrayStrings) {
  arrayStrings.raw = arrayStrings;
  return /** @type {!ITemplateArray} */ (arrayStrings);
};

/**
 * Simply accepts an ITemplateArray and returns it after setting its raw
 * property
 * @param {!ITemplateArray} arrayStrings
 * @param {!ITemplateArray} rawArrayStrings raw string values of arrayString
 * @return {!ITemplateArray}
 * @noinline
 */
$jscomp.createTemplateTagFirstArgWithRaw = function(
    arrayStrings, rawArrayStrings) {
  arrayStrings.raw = rawArrayStrings;
  return /** @type {!ITemplateArray} */ (arrayStrings);
};
