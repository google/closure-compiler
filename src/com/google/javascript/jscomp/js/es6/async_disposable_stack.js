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
'require es6/promise/promise';

$jscomp.polyfill('AsyncDisposableStack', function(orig) {
  if (orig) {
    return orig;
  }
  var ILLEGAL_AFTER_DISPOSAL = 'Forbidden after disposed.';
  var INVALID_DISPOSABLE = 'Invalid Disposable';
  /**
   * @constructor
   * @implements {AsyncDisposable}
   * @nosideeffects
   */
  function AsyncDisposableStack() {
    /** @private {!Array<function(): (void|Promise<void>)>} */
    this.stack_ = [];
    /** @private {boolean} */
    this.actuallyDisposed_ = false;
  }
  $jscomp.defineProperty(AsyncDisposableStack.prototype, 'disposed', {
    configurable: true,
    get: function() {
      return this.actuallyDisposed_;
    }
  });
  // https://tc39.es/proposal-explicit-resource-management/#sec-asyncdisposablestack.prototype.disposeAsync
  AsyncDisposableStack.prototype.disposeAsync = function() {
    // Steps 3-4
    if (this.disposed) {
      return Promise.resolve();
    }
    this.actuallyDisposed_ = true;  // Step 5
    // For steps 6-7 we're moving into
    // https://tc39.es/proposal-explicit-resource-management/#sec-disposeresources
    var errorEncountered = false;
    var err;
    var result = Promise.resolve();

    var makeClosure = function(onDispose) {
      return function() {
        return onDispose();
      };
    };
    // Step 1 – note moving backwards through the stack
    for (var i = this.stack_.length - 1; i >= 0; i--) {
      // "hint" is always async in an AsyncDisposableStack
      // https://tc39.es/proposal-explicit-resource-management/#sec-dispose
      result = result.then(makeClosure(this.stack_[i])).catch(function(e) {
        // Step 1.b.i
        if (errorEncountered) {
          err = new SuppressedError(e, err, '');
        } else {
          errorEncountered = true;
          err = e;

        }
      });
    }
    // Steps 2-3
    this.stack_.length = 0;
    return result.then(function() {
      // Step 4
      if (errorEncountered) {
        throw err;
      }
      // Back out to disposeAsync
      // Step 8-9
      return undefined;
    });
  };
  // https://tc39.es/proposal-explicit-resource-management/#sec-asyncdisposablestack.prototype-@@asyncDispose
  AsyncDisposableStack.prototype[Symbol.asyncDispose] =
      AsyncDisposableStack.prototype.disposeAsync;

  // https://tc39.es/proposal-explicit-resource-management/#sec-asyncdisposablestack.prototype.use
  AsyncDisposableStack.prototype.use = function(disposable) {
    // Step 2-3
    if (this.disposed) {
      throw new ReferenceError(ILLEGAL_AFTER_DISPOSAL);
    }
    // In Step 4 we're moving into
    // https://tc39.es/proposal-explicit-resource-management/#sec-adddisposableresource
    // which then call create disposable resource and get dispose method
    if (disposable == null) {
      // 1.b - gotta force an await in this case.
      this.stack_.push(function() {
        return Promise.resolve();
      });
    } else if (
        disposable[Symbol.asyncDispose] != null &&
        typeof disposable[Symbol.asyncDispose] === 'function') {
      // Per get dispose method with an async hint, first we check for
      // asyncDispose, then we check for dispose.
      var disposeMethod = disposable[Symbol.asyncDispose];
      // According to get dispose method, we define a promise-returning closure
      // that resolves or rejects based on the result of the dispose method.
      this.stack_.push(function() {
        return Promise.resolve().then(function() {
          return disposeMethod.call(disposable);
        });
      });
    } else if (typeof disposable[Symbol.dispose] === 'function') {
      var disposeMethod = disposable[Symbol.dispose];
      this.stack_.push(function() {
        return Promise.resolve().then(function() {
          // Note that we don't return the result of disposeMethod.
          // A promise returned from a sync dispose method must be ignored,
          // and must not be awaited. However we do want to reject if the
          // dispose method synchronously throws.
          // See 1.b.ii of
          // https://tc39.es/proposal-explicit-resource-management/#sec-getdisposemethod
          disposeMethod.call(disposable);
        });
      });
    } else {
      // See i.b.iii of
      // https://tc39.es/proposal-explicit-resource-management/#sec-createdisposableresource
      throw new TypeError(INVALID_DISPOSABLE);
    }
    // Step 5
    return disposable;
  };

  // https://tc39.es/proposal-explicit-resource-management/#sec-asyncdisposablestack.prototype.adopt
  AsyncDisposableStack.prototype.adopt = function(value, onDispose) {
    // Step 2-3
    if (this.disposed) {
      throw new ReferenceError(ILLEGAL_AFTER_DISPOSAL);
    }
    // Step 4
    if (typeof onDispose !== 'function') {
      throw new TypeError(INVALID_DISPOSABLE);
    }
    // Step 5-7
    this.stack_.push(function() {
      return Promise.resolve().then(function() {
        return onDispose.call(undefined, value);
      });
    });
    // Step 8
    return value;
  };

  // https://tc39.es/proposal-explicit-resource-management/#sec-asyncdisposablestack.prototype.defer
  AsyncDisposableStack.prototype.defer = function(onDispose) {
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

  // https://tc39.es/proposal-explicit-resource-management/#sec-asyncdisposablestack.prototype.move
  AsyncDisposableStack.prototype.move = function() {
    // Steps 2-3
    if (this.disposed) {
      throw new ReferenceError(ILLEGAL_AFTER_DISPOSAL);
    }
    // Step 4-5
    var newDisposableStack = new AsyncDisposableStack();
    // Step 6
    newDisposableStack.stack_ = this.stack_;
    // Step 7
    this.stack_ = [];
    // Step 8
    this.actuallyDisposed_ = true;
    // Step 9
    return newDisposableStack;
  };

  return AsyncDisposableStack;
  // probably ES2026
}, 'es_next', 'es5');
