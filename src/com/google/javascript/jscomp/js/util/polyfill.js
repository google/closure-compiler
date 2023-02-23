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
 * @fileoverview Provides methods to polyfill native objects.
 * @suppress {uselessCode}
 */
'require util/defineproperty';
'require util/defines';
'require util/global';
'require util/shouldpolyfill';

/** @const {!Object<string, ?>} map from classes (Map) to polyfills */
$jscomp.polyfills = {};

/**
 * Maps a property to an obfuscated look-up string for that property. Only used
 * when `$jscomp.ISOLATE_POLYFILLS is enabled.
 *
 * When compiling with polyfill isolation enabled, to polyfill an
 * `owner.prototype.method` the `$jscomp.polyfill` library function:
 * <ol>
 * <li> creates an obfuscated string for `method`
 * <li> adds the obfuscated string as a new property on the `owner` object
 * <li> assigns the polyfill implementation of `method` to this new obfuscated
 * property on the `owner` object. </li>
 *</ol>
 *
 * This maps the original property name `method` to the new obfuscated property
 * name.
 * @const {!Object<string, symbol>}
 */
$jscomp.propertyToPolyfillSymbol = {};

/** @const {string} */
$jscomp.POLYFILL_PREFIX = '$jscp$';

/**
 * Returns the polyfill function of the given property if present, otherwise
 * returns the actual property.
 *
 * This is a variable instead of a $jscomp.* property to make it simpler for
 * the compiler to avoid prematurely deleting it during optimizations.
 *
 * @param {*} target the receiver for the property access, e.g. `my.str` in
 *     `my.str.includes`
 * @param {string} property the name of the property, e.g. `includes` in
 *     `my.str.includes`
 * @param {boolean=} isOptionalAccess whether the access to property is via the
 *     optional chaining operator, e.g. `my.str?.includes`
 * @return {?} if an obfuscated symbol for the property was added onto the
 *     target, then this function returns the polyfill for the property.
 *     Otherwise simply looks up the property on the target and returns it as
 *     `target[key]`.
 * @noinline prevent inlining so IsolatePolyfills can find this declaration.
 * @suppress {reportUnknownTypes}
 */
var $jscomp$lookupPolyfilledValue = function(
    target, property, isOptionalAccess) {
  if (isOptionalAccess && target == null) {
    // For optional chain accesses such as `my.str?.includes`, we should not
    // crash and instead return undefined.
    return undefined;
  }

  /** @const */
  var obfuscatedName = $jscomp.propertyToPolyfillSymbol[property];
  if (obfuscatedName == null) {
    return target[property];
  }

  // NOTE: this will throw if `target` is null or undefined.
  /** @const */
  var polyfill = target[obfuscatedName];

  // Checking `polyfill !== undefined` is sufficient because $jscomp.polyfill
  // never installs nullish polyfills. Otherwise we'd have to check something
  // like `key in target`.
  return polyfill !== undefined ? polyfill : target[property];
};

/**
 * @param {string} target Qualified name of the class or method to polyfill,
 *     e.g. 'Array.prototype.includes' or 'Map'.
 * @param {?function(*): *} polyfill A function that takes the current browser
 *     implementation of the target and returns an optional new polyfill
 *     implementation.  If null is returned, then no polyfill will be added.  A
 *     null argument for this parameter indicates that the function will not be
 *     polyfilled, and is only useful for `build_polyfill_table.js` bookkeeping.
 * @param {string} fromLang The language level in which the target is expected
 *     to already be present in the browser.  The compiler requires that
 *     `languageOut < fromLang` before injecting a polyfill (i.e. if the
 *     specified output language already includes the feature then there's no
 *     need to polyfill it).
 * @param {string} toLang The language level required by the polyfill
 *     implementation.  The compiler will issue an error if a polyfill is
 *     required, but `languageOut < toLang`.  Additionally, the
 *     `build_polyfill_table.js` script audits the polyfill dependency tree to
 *     ensure that no polyfill with a lower `toLang` depends on one with a
 *     higher `toLang`.
 * @noinline
 * NOTE: We prevent inlining so RemoveUnusedPolyfills can always recognize this
 * call.
 */
$jscomp.polyfill = function(target, polyfill, fromLang, toLang) {
  if (!polyfill) return;
  if ($jscomp.ISOLATE_POLYFILLS) {
    $jscomp.polyfillIsolated(target, polyfill, fromLang, toLang);
  } else {
    $jscomp.polyfillUnisolated(target, polyfill, fromLang, toLang);
  }
};

/**
 * @param {string} target Qualified name of the class or method to polyfill,
 * @param {function(*): *} polyfill A function that takes the current browser
 *     implementation of the target and returns an optional new polyfill
 *     implementation.
 * @param {string} fromLang The language level in which the target is expected
 *     to already be present in the browser.
 * @param {string} toLang The language level required by the polyfill
 *     implementation.
 * @suppress {reportUnknownTypes}
 * @see $jscomp.polyfill for more documentation
 */
$jscomp.polyfillUnisolated = function(target, polyfill, fromLang, toLang) {
  var obj = $jscomp.global;
  var split = target.split('.');
  for (var i = 0; i < split.length - 1; i++) {
    var key = split[i];
    if (!(key in obj)) return;
    obj = obj[key];
  }
  var property = split[split.length - 1];
  var orig = obj[property];
  var impl = polyfill(orig);
  if (impl == orig || impl == null) return;
  $jscomp.defineProperty(
      obj, property, {configurable: true, writable: true, value: impl});
};

/**
 * Defines a polyfill for the given class or method under an obfuscated name.
 *
 * The main differences between this method and $jscomp.polyfillUnisolated are:
 *  - classes are defined on $jscomp.polyfills, not window.
 *  - methods (Array.prototype.includes) are defined on Array.prototype under
 *    an obfuscated name Array.prototype.$jscp$907312$includes or a Symbol, if
 * native.
 *  - this method installs our polyfill even when an existing implementation is
 *    found, as it might be an untrusted polyfill. The exception is that if
 *    Symbol is detected to be native, we assume any implementations of ES6
 *    classes/methods are also native and not polyfills.
 *
 * @param {string} target Qualified name of the class or method to polyfill,
 * @param {function(*): *} polyfill A function that takes the current browser
 *     implementation of the target and returns an optional new polyfill
 *     implementation.
 * @param {string} fromLang The language level in which the target is expected
 *     to already be present in the browser.
 * @param {string} toLang The language level required by the polyfill
 *     implementation.
 * @suppress {reportUnknownTypes}
 * @see $jscomp.polyfill for more parameter documentation
 */
$jscomp.polyfillIsolated = function(target, polyfill, fromLang, toLang) {
  var split = target.split('.');
  var isSimpleName = split.length === 1;
  var root = split[0];

  // Look up the parent object. For classes this is just $jscomp.global.
  // For methods/properties this may be a polyfill (Promise) or may a native
  // object (Array.prototype).
  var ownerObject;
  if (!isSimpleName && root in $jscomp.polyfills) {
    // Handle Symbol.asyncIterator and Promise.prototype.finally
    ownerObject = $jscomp.polyfills;
  } else {
    // Handle Promise, Array.of, and String.prototype.startsWith
    ownerObject = $jscomp.global;
  }

  for (var i = 0; i < split.length - 1; i++) {
    var key = split[i];
    if (!(key in ownerObject)) return;
    ownerObject = ownerObject[key];
  }

  var property = split[split.length - 1];
  // If Symbol is native and the target is in the ES6 spec, use the native imp.
  // We assume the method/class was not polyfilled since polyfills typically
  // back off in the presence of an existing implementation.
  var nativeImpl = $jscomp.IS_SYMBOL_NATIVE && fromLang === 'es6' ?
      ownerObject[property] :
      null;
  var impl = polyfill(nativeImpl);

  if (impl == null) {
    return;
  }

  if (isSimpleName) {
    // Note: `impl` may be the actual native class instead of a
    // polyfill. Add it to $jscomp.polyfills anyway. The IsolatePolyfills pass
    // unconditionally replaces `Symbol` with `$jscomp.polyfills['Symbol']`.

    // Use $jscomp.polyfills instead of ownerObject. For simple names like
    // Promise and Symbol, ownerObject is the global object, and we want to
    // avoid adding new global variables.
    $jscomp.defineProperty(
        $jscomp.polyfills, property,
        {configurable: true, writable: true, value: impl});
  } else if (impl !== nativeImpl) {
    // Skip installing an obfuscated property if we have found a native version
    // of the method we're polyfilling. $jscomp$lookupPolyfilledValue will fall
    // back to the native version anyway.
    if ($jscomp.propertyToPolyfillSymbol[property] === undefined) {
      var BIN_ID = (Math.random() * 1e9) >>> 0;
      $jscomp.propertyToPolyfillSymbol[property] = $jscomp.IS_SYMBOL_NATIVE ?
          // use bracket access to avoid injecting the Symbol polyfill
          $jscomp.global['Symbol'](property) :
          $jscomp.POLYFILL_PREFIX + BIN_ID + '$' + property;
    }

    /** @const {symbol} */
    var obfuscatedName = $jscomp.propertyToPolyfillSymbol[property];

    // Define the polyfilled method on its owner but under an obfuscated
    // name to avoid collisions. The owner will be a native class like `Promise`
    // or a native class's prototype like `Array.prototype`.
    $jscomp.defineProperty(
        ownerObject, obfuscatedName,
        {configurable: true, writable: true, value: impl});
  }
};
