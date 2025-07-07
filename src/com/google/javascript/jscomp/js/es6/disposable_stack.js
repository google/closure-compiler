/*
 * Copyright 2025 The Closure Compiler Authors.
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

'require util/global';
'require util/polyfill';
'require util/defineproperty';
'require es6/dispose';

$jscomp.polyfill('DisposableStack', function(orig) {
  if (orig) {
    return orig;
  }
  var ILLEGAL_AFTER_DISPOSAL = 'Forbidden after disposed.';
  var INVALID_DISPOSABLE = 'Invalid Disposable';
  /**
   * @constructor
   * @implements {Disposable}
   * @nosideeffects
   */
  function DisposableStack() {
    /** @private {!Array<function(): void>} */
    this.stack_ = [];
    /** @private {boolean} */
    this.actuallyDisposed_ = false;
  }
  $jscomp.defineProperty(DisposableStack.prototype, 'disposed', {
    configurable: true,
    get: function() {
      return this.actuallyDisposed_;
    }
  });
  // https://tc39.es/proposal-explicit-resource-management/#sec-disposablestack.prototype.dispose
  DisposableStack.prototype.dispose = function() {
    // Step 2-3
    if (this.disposed) {
      return;
    }
    this.actuallyDisposed_ = true;  // Step 4
    // For the rest, we're now inside DisposeResources:
    // https://tc39.es/proposal-explicit-resource-management/#sec-disposeresources
    var errorEncountered = false;
    var err;
    // Step 3. Note: *reverse order*
    for (var i = this.stack_.length - 1; i >= 0; i--) {
      var onDispose = this.stack_[i];
      try {
        // Step 3.e.i only we've already bound the 'value' into the function.
        onDispose();
      } catch (e) {
        // Step 3.e.iii
        if (errorEncountered) {
          err = new SuppressedError(e, err, '');
        } else {
          errorEncountered = true;
          err = e;
        }
      }
    }
    // Step 5-6
    this.stack_.length = 0;
    // Step 7
    if (errorEncountered) {
      throw err;
    }
    // Step 7
    return;
  };
  // https://tc39.es/proposal-explicit-resource-management/#sec-disposablestack.prototype-@@dispose)
  /** @const */
  DisposableStack.prototype[Symbol.dispose] = DisposableStack.prototype.dispose;

  // https://tc39.es/proposal-explicit-resource-management/#sec-disposablestack.prototype.use
  DisposableStack.prototype.use = function(disposable) {
    // Step 2-3
    if (this.disposed) {
      throw new ReferenceError(ILLEGAL_AFTER_DISPOSAL);
    }
    // Step 4, stepping into https://tc39.es/proposal-explicit-resource-management/#sec-adddisposableresource
    // with args AddDisposableResource(this, disposable, sync-dispose, undefined)
    // 1.a. early exit
    if (disposable === null || disposable === undefined) {
      // Step 5 of `use`.
      return disposable;
    }
    // Step 1.c. now stepping into https://tc39.es/proposal-explicit-resource-management/#sec-createdisposableresource
    // with args CreateDisposableResource(disposable, sync-dispose, undefined)
    // We just checked that V is not nullish, so we can skip 1.a.
    // Combining 1.b.i. and i.b.iii of https://tc39.es/proposal-explicit-resource-management/#sec-createdisposableresource
    // by just checking the type of the Symbol.dispose method, since a
    // non-object shouldn't have a Symbol.dispose method.
    var disposeMethod = disposable[Symbol.dispose];
    if (typeof disposeMethod !== 'function') {
      // See i.b.iii of
      // https://tc39.es/proposal-explicit-resource-management/#sec-createdisposableresource
      throw new TypeError(INVALID_DISPOSABLE);
    }
    // Perhaps surprising here, but DisposableStack locks in the dispose
    // method at the time `use` is called.
    var resource = disposeMethod.bind(/** @type {!Object} */ (disposable));
    // Step 3 of https://tc39.es/proposal-explicit-resource-management/#sec-adddisposableresource
    this.stack_.push(resource);
    // Back up to the `use` spec.
    // Step 5
    return disposable;
  };

  // https://tc39.es/proposal-explicit-resource-management/#sec-disposablestack.prototype.adopt
  DisposableStack.prototype.adopt = function(value, onDispose) {
    // Step 2-3
    if (this.disposed) {
      throw new ReferenceError(ILLEGAL_AFTER_DISPOSAL);
    }
    // Step 4
    if (typeof onDispose !== 'function') {
      throw new TypeError(INVALID_DISPOSABLE);
    }
    // Step 5-6
    var closure = function() {
      onDispose.call(undefined, value);
    };
    // Step 7
    this.stack_.push(closure);
    // Step 8
    return value;
  };

  // https://tc39.es/proposal-explicit-resource-management/#sec-disposablestack.prototype.defer
  DisposableStack.prototype.defer = function(onDispose) {
    // Step 2-3
    if (this.disposed) {
      throw new ReferenceError(ILLEGAL_AFTER_DISPOSAL);
    }
    // Step 4
    if (typeof onDispose !== 'function') {
      throw new TypeError(INVALID_DISPOSABLE);
    }
    // Step 5
    this.stack_.push(onDispose.bind(undefined));
    // Step 6
    return undefined;
  };

  // https://tc39.es/proposal-explicit-resource-management/#sec-disposablestack.prototype.move
  DisposableStack.prototype.move = function() {
    // Step 2-3
    if (this.disposed) {
      throw new ReferenceError(ILLEGAL_AFTER_DISPOSAL);
    }
    // Step 4-5
    var newDisposableStack = new DisposableStack();
    // Step 6
    newDisposableStack.stack_ = this.stack_;
    // Step 7
    this.stack_ = [];
    // Step 8
    this.actuallyDisposed_ = true;
    // Step 9
    return newDisposableStack;
  };

  return DisposableStack;
  // probably ES2026
}, 'es_next', 'es5');
