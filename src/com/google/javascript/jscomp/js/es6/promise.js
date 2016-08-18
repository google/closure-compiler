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
'require util/polyfill';

/**
 * Should we expose AsyncExecutor for testing?
 * TODO(bradfordcsmith): Set this false here & arrange for it to be set to true
 * only for tests.
 * @define {boolean}
 */
$jscomp.EXPOSE_ASYNC_EXECUTOR = true;

$jscomp.polyfill('JscPromise', function(NativePromise) {
  /**
    * Schedules code to be executed asynchronously.
    * @constructor
    * @struct
    */
  function AsyncExecutor() {
    /**
     * Batch of functions to execute.
     *
     * Will be {@code null} initially and immediately after a batch finishes
     * executing.
     * @private {?Array<function():?>}
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
   * Schedule execution of the jobs in {@code this.batch_}.
   * @private
   */
  AsyncExecutor.prototype.asyncExecuteBatch_ = function() {
    var self = this;
    this.asyncExecuteFunction(function() { self.executeBatch_(); });
  };

  // NOTE: We want to make sure AsyncExecutor will work as expected even if
  // testing code should override setTimeout()
  /** @const */ var nativeSetTimeout = setTimeout;

  /**
   * Schedule a function to execute asynchronously as soon as possible.
   *
   * NOTE: May be overridden for testing.
   * @package
   * @param {!Function} f
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
      var executingBatch = this.batch_;
      // Executions scheduled while executing this batch go into a new one to
      // avoid the batch array getting too big.
      this.batch_ = [];
      for (var i = 0; i < executingBatch.length; ++i) {
        var f = executingBatch[i];
        delete executingBatch[i];  // free memory
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

  // TODO(bradfordcsmith): Actually implement PolyfillPromise
  var PolyfillPromise = {};

  if ($jscomp.EXPOSE_ASYNC_EXECUTOR) {
    // expose AsyncExecutor so it can be tested independently.
    PolyfillPromise['$jscomp$new$AsyncExecutor'] = function() {
      return new AsyncExecutor();
    };
  }

  return PolyfillPromise;
}, 'es6-impl', 'es3');
