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

'require util/objectcreate';
'require util/polyfill';
'require util/shouldpolyfill';

/**
 * Find or create a method that implements the behavior of `Reflect.construct`.
 *
 * @return {!Function}
 */
$jscomp.getConstructImplementation = function() {
  // Check for https://github.com/Microsoft/ChakraCore/issues/3217
  /** @return {boolean} */
  function reflectConstructWorks() {
    /** @constructor */ function Base() {}
    /** @constructor */ function Derived() {}
    new Base();
    Reflect.construct(Base, [], Derived);
    return new Base() instanceof Base;
  }

  if ($jscomp.TRUST_ES6_POLYFILLS && typeof Reflect != 'undefined' &&
      Reflect.construct) {
    if (reflectConstructWorks()) return Reflect.construct;
    var brokenConstruct = Reflect.construct;
    /**
     * @param {function(new: ?, ...?)} target The constructor to call.
     * @param {!Array} argList The arguments as a list.
     * @param {function(new: TARGET, ...?)=} opt_newTarget The constructor to
     *     instantiate.
     * @return {TARGET} The result of the function call.
     * @template TARGET
     * @suppress {reportUnknownTypes}
     */
    var patchedConstruct = function(target, argList, opt_newTarget) {
      var out = brokenConstruct(target, argList);
      if (opt_newTarget) Reflect.setPrototypeOf(out, opt_newTarget.prototype);
      return out;
    };
    return patchedConstruct;
  }

  /**
   * @param {function(new: ?, ...?)} target The constructor to call.
   * @param {!Array} argList The arguments as a list.
   * @param {function(new: TARGET, ...?)=} opt_newTarget The constructor to
   *     instantiate.
   * @return {TARGET} The result of the function call.
   * @template TARGET
   * @suppress {reportUnknownTypes}
   */
  function construct(target, argList, opt_newTarget) {
    if (opt_newTarget === undefined) opt_newTarget = target;
    var proto = opt_newTarget.prototype || Object.prototype;
    var obj = $jscomp.objectCreate(proto);
    var apply = Function.prototype.apply;
    var out = apply.call(target, obj, argList);
    return out || obj;
  }
  return construct;
};

/**
 * Polyfill for Reflect.construct() method:
 * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Reflect/construct
 *
 * The call to `$jscomp.getConstructImplementation()` is wrapped up as an object
 * literal `valueOf()` call in order to hide the (meaningless) side-effects
 * it contains that would otherwise prevent its definition from being removed,
 * even when nothing refers to `$jscomp.construct`.
 * @const
 */
$jscomp.construct =
    /** @type {typeof Reflect.construct} */ (
        {valueOf: $jscomp.getConstructImplementation}.valueOf());
