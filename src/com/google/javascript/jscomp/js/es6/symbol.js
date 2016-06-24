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
 * @fileoverview Polyfill for ES6 Symbol.
 */
'require util/global';


/**
 * Initializes the Symbol function.
 * @suppress {reportUnknownTypes}
 */
$jscomp.initSymbol = function() {
  if (!$jscomp.global.Symbol) {
    $jscomp.global.Symbol = $jscomp.Symbol;
  }

  // Only need to do this once. All future calls are no-ops.
  $jscomp.initSymbol = function() {};
};


/** @private {number} */
$jscomp.symbolCounter_ = 0;


/**
 * Produces "symbols" (actually just unique strings).
 * @param {string} description
 * @return {symbol}
 * @suppress {reportUnknownTypes}
 */
$jscomp.Symbol = function(description) {
  return /** @type {symbol} */ (
      'jscomp_symbol_' + description + ($jscomp.symbolCounter_++));
};


/**
 * Initializes Symbol.iterator, if it's not already defined.
 * @suppress {reportUnknownTypes}
 */
$jscomp.initSymbolIterator = function() {
  $jscomp.initSymbol();
  if (!$jscomp.global.Symbol.iterator) {
    $jscomp.global.Symbol.iterator = $jscomp.global.Symbol('iterator');
  }

  // Only need to do this once. All future calls are no-ops.
  $jscomp.initSymbolIterator = function() {};
};
