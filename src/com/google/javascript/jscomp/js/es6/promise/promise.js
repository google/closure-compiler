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
'require es6/util/makeiterator';
'require util/global';
'require util/polyfill';

/**
 * Should we unconditionally override a native Promise implementation with our
 * own?
 * @define {boolean}
 */
$jscomp.FORCE_POLYFILL_PROMISE = false;


$jscomp.polyfill('Promise',
    /**
     * @param {*} NativePromise
     * @return {*}
     * @suppress {reportUnknownTypes}
     */
    function(NativePromise) {
  // TODO(bradfordcsmith): Do we need to add checks for standards conformance?
  //     e.g. The version of FireFox we currently use for testing has a Promise
  //     that fails to reject attempts to fulfill it with itself, but that
  //     isn't reasonably testable here.
  if (NativePromise && !$jscomp.FORCE_POLYFILL_PROMISE) {
    return NativePromise;
  }

  /**
    * Schedules code to be executed asynchronously.
    * @constructor
    * @struct
    */
  function AsyncExecutor() {
    /**
     * Batch of functions to execute.
     *
     * Will be `null` initially and immediately after a batch finishes
     * executing.
     * @private {?Array<function()>}
     */
    this.batch_ = null;
  }

  /**
   * Schedule a function to execute asynchronously.
   *
   * -   The function will execute:
   *     -   After the current call stack has completed executing.
   *     -   After any functions previously scheduled using this object.
   * -   The return value will be ignored.
   * -   An exception thrown by the method will be caught and asynchronously
   *     rethrown when it cannot interrupt any other code. This class provides
   *     no way to catch such exceptions.
   * @param {function():?} f
   * @return {!AsyncExecutor} this object
   */
  AsyncExecutor.prototype.asyncExecute = function(f) {
    if (this.batch_ == null) {
      // no batch created yet, or last batch was fully executed
      this.batch_ = [];
      this.asyncExecuteBatch_();
    }
    this.batch_.push(f);
    return this;
  };

  /**
   * Schedule execution of the jobs in `this.batch_`.
   * @private
   */
  AsyncExecutor.prototype.asyncExecuteBatch_ = function() {
    var self = this;
    this.asyncExecuteFunction(function() { self.executeBatch_(); });
  };

  // NOTE: We want to make sure AsyncExecutor will work as expected even if
  // testing code should override setTimeout()
  /** @const {function(!Function, number)} */
  var nativeSetTimeout = $jscomp.global['setTimeout'];

  /**
   * Schedule a function to execute asynchronously as soon as possible.
   *
   * NOTE: May be overridden for testing.
   * @package
   * @param {function()} f
   */
  AsyncExecutor.prototype.asyncExecuteFunction = function(f) {
    nativeSetTimeout(f, 0);
  };

  /**
   * Execute scheduled jobs in a batch until all are executed or the batch
   * execution time limit has been reached.
   * @private
   */
  AsyncExecutor.prototype.executeBatch_ = function() {
    while (this.batch_ && this.batch_.length) {
      var /** !Array<?function()> */ executingBatch = this.batch_;
      // Executions scheduled while executing this batch go into a new one to
      // avoid the batch array getting too big.
      this.batch_ = [];
      for (var i = 0; i < executingBatch.length; ++i) {
        var f = /** @type {function()} */ (executingBatch[i]);
        executingBatch[i] = null;  // free memory
        try {
          f();
        } catch (error) {
          this.asyncThrow_(error);
        }
      }
    }
    // All jobs finished executing, so force scheduling a new batch next
    // time asyncExecute() is called.
    this.batch_ = null;
  };

  /**
   * @private
   * @param {*} exception
   */
  AsyncExecutor.prototype.asyncThrow_ = function(exception) {
    this.asyncExecuteFunction(function() { throw exception; });
  };

  /**
   * @enum {number}
   */
  var PromiseState = {
    /** The Promise is waiting for resolution. */
    PENDING: 0,

    /** The Promise has been resolved with a fulfillment value. */
    FULFILLED: 1,

    /** The Promise has been resolved with a rejection reason. */
    REJECTED: 2
  };


  /**
   * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Promise
   * @param {function(
   *             function((TYPE|IThenable<TYPE>|Thenable|null)=),
   *             function(*=))} executor
   * @constructor
   * @extends {Promise<TYPE>}
   * @template TYPE
   */
  var PolyfillPromise = function(executor) {
    /** @private {PromiseState} */
    this.state_ = PromiseState.PENDING;

    /**
     * The settled result of the Promise. Immutable once set with either a
     * fulfillment value or rejection reason.
     * @private {*}
     */
    this.result_ = undefined;

    /**
     * These functions must be asynchronously executed when this promise
     * settles.
     * @private {?Array<function()>}
     */
    this.onSettledCallbacks_ = [];

    var resolveAndReject = this.createResolveAndReject_();
    try {
      executor(resolveAndReject.resolve, resolveAndReject.reject);
    } catch (e) {
      resolveAndReject.reject(e);
    }
  };


  /**
   * Create a pair of functions for resolving or rejecting this Promise.
   *
   * <p>After the resolve or reject function has been called once, later calls
   * do nothing.
   * @private
   * @return {{
   *     resolve: function((TYPE|IThenable<TYPE>|Thenable|null)=),
   *     reject:  function(*=)
   * }}
   */
  PolyfillPromise.prototype.createResolveAndReject_ = function() {
    var thisPromise = this;
    var alreadyCalled = false;
    /**
     * @param {function(this:PolyfillPromise<TYPE>, T)} method
     * @return {function(T)}
     * @template T
     */
    function firstCallWins(method) {
      return function(x) {
        if (!alreadyCalled) {
          alreadyCalled = true;
          method.call(thisPromise, x);
        }
      };
    }
    return {
      resolve: firstCallWins(this.resolveTo_),
      reject: firstCallWins(this.reject_)
    };
  };


  /**
   * @private
   * @param {*} value
   */
  PolyfillPromise.prototype.resolveTo_ = function(value) {
    if (value === this) {
      this.reject_(new TypeError('A Promise cannot resolve to itself'));
    } else if (value instanceof PolyfillPromise) {
      this.settleSameAsPromise_(/** @type {!PolyfillPromise} */ (value));
    } else if (isObject(value)) {
      this.resolveToNonPromiseObj_(/** @type {!Object} */ (value));
    } else {
      this.fulfill_(value);
    }
  };


  /**
   * @private
   * @param {!Object} obj
   * @suppress {strictMissingProperties} obj.then
   */
  PolyfillPromise.prototype.resolveToNonPromiseObj_ = function(obj) {
    var thenMethod = undefined;

    try {
      thenMethod = obj.then;
    } catch (error) {
      this.reject_(error);
      return;
    }
    if (typeof thenMethod == 'function') {
      this.settleSameAsThenable_(thenMethod, /** @type {!Thenable} */ (obj));
    } else {
      this.fulfill_(obj);
    }
  };


  /**
   * @param {*} value anything
   * @return {boolean}
   */
  function isObject(value) {
    switch (typeof value) {
      case 'object':
        return value != null;
      case 'function':
        return true;
      default:
        return false;
    }
  }

  /**
   * Reject this promise for the given reason.
   * @private
   * @param {*} reason
   * @throws {!Error} if this promise is already fulfilled or rejected.
   */
  PolyfillPromise.prototype.reject_ = function(reason) {
    this.settle_(PromiseState.REJECTED, reason);
  };

  /**
   * Fulfill this promise with the given value.
   * @private
   * @param {!TYPE} value
   * @throws {!Error} when this promise is already fulfilled or rejected.
   */
  PolyfillPromise.prototype.fulfill_ = function(value) {
    this.settle_(PromiseState.FULFILLED, value);
  };

  /**
   * Fulfill or reject this promise with the given value/reason.
   * @private
   * @param {!PromiseState} settledState (FULFILLED or REJECTED)
   * @param {*} valueOrReason
   * @throws {!Error} when this promise is already fulfilled or rejected.
   */
  PolyfillPromise.prototype.settle_ = function(settledState, valueOrReason) {
    if (this.state_ != PromiseState.PENDING) {
      throw new Error(
          'Cannot settle(' + settledState + ', ' + valueOrReason +
          '): Promise already settled in state' + this.state_);
    }
    this.state_ = settledState;
    this.result_ = valueOrReason;
    this.executeOnSettledCallbacks_();
  };

  PolyfillPromise.prototype.executeOnSettledCallbacks_ = function() {
    if (this.onSettledCallbacks_ != null) {
      for (var i = 0; i < this.onSettledCallbacks_.length; ++i) {
        asyncExecutor.asyncExecute(this.onSettledCallbacks_[i]);
      }
      this.onSettledCallbacks_ = null;  // free memory
    }
  };

  /**
   * All promise async execution is managed by a single executor for the
   * sake of efficiency.
   * @const {!AsyncExecutor}
   */
  var asyncExecutor = new AsyncExecutor();

  /**
   * Arrange to settle this promise in the same way as the given thenable.
   * @private
   * @param {!PolyfillPromise} promise
   */
  PolyfillPromise.prototype.settleSameAsPromise_ = function(promise) {
    var methods = this.createResolveAndReject_();

    // Calling then() would create an unnecessary extra promise.
    promise.callWhenSettled_(methods.resolve, methods.reject);
  };

  /**
   * Arrange to settle this promise in the same way as the given thenable.
   * @private
   * @param {function(
   *     function((TYPE|IThenable<TYPE>|Thenable|null)=),
   *     function(*=))
   * } thenMethod
   * @param {!Thenable} thenable
   */
  PolyfillPromise.prototype.settleSameAsThenable_ = function(
      thenMethod, thenable) {
    var methods = this.createResolveAndReject_();

    // Don't trust an unknown thenable implementation not to throw exceptions.
    try {
      thenMethod.call(thenable, methods.resolve, methods.reject);
    } catch (error) {
      methods.reject(error);
    }
  };

  /** @override */
  PolyfillPromise.prototype.then = function(onFulfilled, onRejected) {
    var resolveChild;
    var rejectChild;
    var childPromise = new PolyfillPromise(function(resolve, reject) {
      resolveChild = resolve;
      rejectChild = reject;
    });
    function createCallback(paramF, defaultF) {
      // The spec says to ignore non-function values for onFulfilled and
      // onRejected
      if (typeof paramF == 'function') {
        return function(x) {
          try {
            resolveChild(paramF(x));
          } catch (error) {
            rejectChild(error);
          }
        };
      } else {
        return defaultF;
      }
    }

    this.callWhenSettled_(
        createCallback(onFulfilled, resolveChild),
        createCallback(onRejected, rejectChild));
    return childPromise;
  };

  /** @override */
  PolyfillPromise.prototype.catch = function(onRejected) {
    return this.then(undefined, onRejected);
  };


  PolyfillPromise.prototype.callWhenSettled_ = function(
      onFulfilled, onRejected) {
    var /** !PolyfillPromise */ thisPromise = this;
    function callback() {
      switch (thisPromise.state_) {
        case PromiseState.FULFILLED:
          onFulfilled(thisPromise.result_);
          break;
        case PromiseState.REJECTED:
          onRejected(thisPromise.result_);
          break;
        default:
          throw new Error('Unexpected state: ' + thisPromise.state_);
      }
    }
    if (this.onSettledCallbacks_ == null) {
      // we've already settled
      asyncExecutor.asyncExecute(callback);
    } else {
      this.onSettledCallbacks_.push(callback);
    }
  };

  // called locally, so give it a name
  function resolvingPromise(opt_value) {
    if (opt_value instanceof PolyfillPromise) {
      return opt_value;
    } else {
      return new PolyfillPromise(function(resolve, reject) {
        resolve(opt_value);
      });
    }
  }
  PolyfillPromise['resolve'] = resolvingPromise;


  PolyfillPromise['reject'] = function(opt_reason) {
    return new PolyfillPromise(function(resolve, reject) {
      reject(opt_reason);
    });
  };


  PolyfillPromise['race'] = function(thenablesOrValues) {
    return new PolyfillPromise(function(resolve, reject) {
      var /** !Iterator<*> */ iterator =
          $jscomp.makeIterator(thenablesOrValues);
      for (var /** !IIterableResult<*> */ iterRec = iterator.next();
           !iterRec.done;
           iterRec = iterator.next()) {
        // Using resolvingPromise() allows us to treat all elements the same
        // way.
        // NOTE: resolvingPromise(promise) always returns the argument
        // unchanged.
        // Using .callWhenSettled_() instead of .then() avoids creating an
        // unnecessary extra promise.
        resolvingPromise(iterRec.value).callWhenSettled_(resolve, reject);
      }
    });
  };


  PolyfillPromise['all'] = function(thenablesOrValues) {
    var /** !Iterator<*> */ iterator = $jscomp.makeIterator(thenablesOrValues);
    var /** !IIterableResult<*> */ iterRec = iterator.next();

    if (iterRec.done) {
      return resolvingPromise([]);
    } else {
      return new PolyfillPromise(function(resolveAll, rejectAll) {
        var resultsArray = [];
        var unresolvedCount = 0;

        function onFulfilled(i) {
          return function(ithResult) {
            resultsArray[i] = ithResult;
            unresolvedCount--;
            if (unresolvedCount == 0) {
              resolveAll(resultsArray);
            }
          };
        }

        do {
          resultsArray.push(undefined);
          unresolvedCount++;
          // Using resolvingPromise() allows us to treat all elements the same
          // way.
          // NOTE: resolvingPromise(promise) always returns the argument
          // unchanged. Using .callWhenSettled_() instead of .then() avoids
          // creating an unnecessary extra promise.
          resolvingPromise(iterRec.value)
              .callWhenSettled_(
                  onFulfilled(resultsArray.length - 1), rejectAll);
          iterRec = iterator.next();
        } while (!iterRec.done);
      });
    }
  };

  return PolyfillPromise;
}, 'es6', 'es3');
