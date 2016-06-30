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
'require util/global util/patch';


/** @const {string} */
$jscomp.SYMBOL_PREFIX = 'jscomp_symbol_';


/**
 * Initializes the Symbol function.
 * @suppress {reportUnknownTypes}
 */
$jscomp.initSymbol = function() {
  // Only need to do this once. All future calls are no-ops.
  $jscomp.initSymbol = function() {};

  if ($jscomp.global.Symbol) return;

  $jscomp.global.Symbol = $jscomp.Symbol;

  /**
   * @param {string} name
   * @return {boolean}
   */
  var isSymbol = function(name) {
    if (name.length < $jscomp.SYMBOL_PREFIX.length) return false;
    for (var i = 0; i < $jscomp.SYMBOL_PREFIX.length; i++) {
      if (name[i] != $jscomp.SYMBOL_PREFIX[i]) return false;
    }
    return true;
  };

  // Need to monkey-patch Object.getOwnPropertyNames to not return symbols.
  // Note that we use this extra array populated by getOwnPropertyNames
  // because there's no way to access the *unpatched* getOwnPropertyNames
  // from the getOwnPropertySymbols patch.
  var symbols = [];
  var removeSymbolsPatch = function(orig) {
    return function(target) {
      symbols = [];
      var names = orig(target);
      var result = [];
      for (var i = 0, len = names.length; i < len; i++) {
        if (!isSymbol(names[i])) {
          result.push(names[i]);
        } else {
          symbols.push(names[i]);
        }
      }
      return result;
    };
  };

  $jscomp.patch('Object.keys', removeSymbolsPatch);
  $jscomp.patch('Object.getOwnPropertyNames', removeSymbolsPatch);
  $jscomp.patch('Object.getOwnPropertySymbols', function(orig) {
    return function(target) {
      // First call the patched getOwnPropertyNames to reset and fill the array.
      // Store the result somewhere to prevent nosideeffect removal.
      removeSymbolsPatch.unused = Object.getOwnPropertyNames(target);
      // In case the original function actually returned something, append that.
      symbols.push.apply(orig(target));
      return symbols;
    };
  });
  // Note: shouldn't need to patch Reflect.ownKeys.
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
      $jscomp.SYMBOL_PREFIX + description + ($jscomp.symbolCounter_++));
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
