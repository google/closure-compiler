/*
 * Copyright 2018 The Closure Compiler Authors.
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
 * @fileoverview Runtime logic for transpiled Async Generators.
 * @suppress {uselessCode}
 */
'require base';
'require es6/promise';
'require es6/symbol';
'require es6/util/makeasynciterator';


/** @enum {number} */
$jscomp.AsyncGeneratorWrapper$ActionEnum = {
  /** Yield the value from the wrapper generator */
  YIELD_VALUE: 0,
  /** Yield each value from a delegate generator */
  YIELD_STAR: 1,
  /** Resolve the value as a Promise and continue execution */
  AWAIT_VALUE: 2,
};

/**
 * @param {!$jscomp.AsyncGeneratorWrapper$ActionEnum} action
 * @param {VALUE} value
 * @constructor
 * @template VALUE
 * @struct
 * @suppress {reportUnknownTypes}
 */
$jscomp.AsyncGeneratorWrapper$ActionRecord = function(action, value) {
  /**
   * @public
   * @const
   * @type {!$jscomp.AsyncGeneratorWrapper$ActionEnum}
   */
  this.action = action;

  /**
   * @public
   * @const
   * @type {VALUE}
   */
  this.value = /** @type {VALUE} */ (value);
};

/** @enum {string} */
$jscomp.AsyncGeneratorWrapper$GeneratorMethod = {
  NEXT: 'next', THROW: 'throw', RETURN: 'return',
};

/**
 * Records the details of a call to `next()`, `throw()`, or `return()`.
 *
 * One of these objects will be created for each call.
 *
 * @param {$jscomp.AsyncGeneratorWrapper$GeneratorMethod} method
 *    Method to call on generator
 * @param {?} param
 *    Parameter for method called on generator
 *
 * @param {function(!IIterableResult<VALUE>)} resolve
 *    Function to resolve the Promise associated with this frame.
 * @param {function(?)} reject
 *    Function to reject the Promise associated with this frame.
 *
 * @constructor
 * @private
 * @template VALUE
 * @suppress {reportUnknownTypes}
 */
$jscomp.AsyncGeneratorWrapper$ExecutionFrame_ = function(
    method, param, resolve, reject) {
  /** @type {$jscomp.AsyncGeneratorWrapper$GeneratorMethod} */
  this.method = method;
  /** @type {?} */
  this.param = param;
  /** @type {function(!IIterableResult<VALUE>)} */
  this.resolve = resolve;
  /** @type {function(?)} */
  this.reject = reject;
};

/**
 * @param {!$jscomp.AsyncGeneratorWrapper$ExecutionFrame_<VALUE>} frame
 *    The frame at this position in the queue
 * @param {$jscomp.AsyncGeneratorWrapper$ExecutionNode_<VALUE>} next
 *    The node containing the frame to be executed after this one completes
 *
 * @constructor
 * @private
 * @template VALUE
 */
$jscomp.AsyncGeneratorWrapper$ExecutionNode_ = function(frame, next) {
  /** @type {!$jscomp.AsyncGeneratorWrapper$ExecutionFrame_<VALUE>} */
  this.frame = frame;
  /** @type {$jscomp.AsyncGeneratorWrapper$ExecutionNode_<VALUE>} */
  this.next = next;
};

/**
 * A minimalistic queue backed by a linked-list.
 *
 * @constructor
 * @private
 * @template VALUE
 */
$jscomp.AsyncGeneratorWrapper$ExecutionQueue_ = function() {
  /**
   * @type {$jscomp.AsyncGeneratorWrapper$ExecutionNode_<VALUE>}
   * @private
   */
  this.head_ = null;

  /**
   *
   * @type {$jscomp.AsyncGeneratorWrapper$ExecutionNode_<VALUE>}
   * @private
   */
  this.tail_ = null;
};

/**
 * @return {boolean}
 */
$jscomp.AsyncGeneratorWrapper$ExecutionQueue_.prototype.isEmpty = function() {
  return this.head_ === null;
};

/**
 * Returns the current head frame if it exists, otherwise throws Error.
 *
 * @return {!$jscomp.AsyncGeneratorWrapper$ExecutionFrame_<VALUE>}
 * @throws {Error} if the queue is empty
 */
$jscomp.AsyncGeneratorWrapper$ExecutionQueue_.prototype.first = function() {
  if (this.head_) {
    return this.head_.frame;
  } else {
    throw new Error('no frames in executionQueue');
  }
};

/**
 * Drops the current head frame off the head of the queue. Performs same
 * operations as a theoretical "pop", but saves time by not storing or returning
 * the popped frame.
 *
 * If the queue is empty, no operation is performed.
 */
$jscomp.AsyncGeneratorWrapper$ExecutionQueue_.prototype.drop = function() {
  if (this.head_) {
    this.head_ = this.head_.next;
    if (!this.head_) {
      this.tail_ = null;
    }
  }
};

/**
 * @param {!$jscomp.AsyncGeneratorWrapper$ExecutionFrame_<VALUE>} newFrame
 *    the new frame to be executed after all frames currently in the queue
 */
$jscomp.AsyncGeneratorWrapper$ExecutionQueue_.prototype.enqueue = function(
    newFrame) {
  var node = new $jscomp.AsyncGeneratorWrapper$ExecutionNode_(newFrame, null);
  if (this.tail_) {
    this.tail_.next = node;
    this.tail_ = node;
  } else {
    this.head_ = node;
    this.tail_ = node;
  }
};

/**
 * @constructor
 * @implements {AsyncGenerator<VALUE>}
 * @implements {AsyncIterable<VALUE>}
 * @template VALUE
 * @suppress {reportUnknownTypes}
 */
$jscomp.AsyncGeneratorWrapper = function(
    /** @type {!Generator<$jscomp.AsyncGeneratorWrapper$ActionRecord<VALUE>>} */
    generator) {
  /** @private */
  this.generator_ = generator;

  /**
   * @private
   * @type {AsyncIterator<VALUE>}
   */
  this.delegate_ = null;

  /**
   * @type {!$jscomp.AsyncGeneratorWrapper$ExecutionQueue_<VALUE>}
   * @private
   */
  this.executionQueue_ = new $jscomp.AsyncGeneratorWrapper$ExecutionQueue_();

  $jscomp.initSymbolAsyncIterator();

  /** @type {$jscomp.AsyncGeneratorWrapper<VALUE>} */
  this[Symbol.asyncIterator] =
      /** @return {$jscomp.AsyncGeneratorWrapper<VALUE>} */ function() {
        return this;
      };

  var self = this;

  /**
   * @this {undefined}
   * @param {!IIterableResult<VALUE>} record
   * @private
   */
  this.boundHandleDelegateResult_ = function(record) {
    self.handleDelegateResult_(record);
  };

  /**
   * @this {undefined}
   * @param {*} thrownError
   * @private
   */
  this.boundHandleDelegateError_ = function(thrownError) {
    self.handleDelegateError_(thrownError);
  };

  /**
   * @this {undefined}
   * @param {*} err
   * @private
   */
  this.boundRejectAndClose_ = function(err) {
    self.rejectAndClose_(err);
  };
};

/**
 * @param {!$jscomp.AsyncGeneratorWrapper$GeneratorMethod} method
 * @param {?} param
 * @return {!Promise<!IIterableResult<VALUE>>}
 * @private
 * @suppress {reportUnknownTypes}
 */
$jscomp.AsyncGeneratorWrapper.prototype.enqueueMethod_ = function(
    method, param) {
  var self = this;
  return new Promise(function(resolve, reject) {
    var wasEmpty = self.executionQueue_.isEmpty();
    self.executionQueue_.enqueue(
        new $jscomp.AsyncGeneratorWrapper$ExecutionFrame_(
            method, param, resolve, reject));
    if (wasEmpty) {
      self.runFrame_();
    }
  });
};

/**
 * @override
 * @param {?=} opt_value
 * @return {!Promise<!IIterableResult<VALUE>>}
 * @suppress {reportUnknownTypes}
 */
$jscomp.AsyncGeneratorWrapper.prototype.next = function(opt_value) {
  return this.enqueueMethod_(
      $jscomp.AsyncGeneratorWrapper$GeneratorMethod.NEXT, opt_value);
};

/**
 * @override
 * @param {VALUE} value
 * @return {!Promise<!IIterableResult<VALUE>>}
 * @suppress {reportUnknownTypes}
 */
$jscomp.AsyncGeneratorWrapper.prototype.return = function(value) {
  return this.enqueueMethod_(
      $jscomp.AsyncGeneratorWrapper$GeneratorMethod.RETURN,
      new $jscomp.AsyncGeneratorWrapper$ActionRecord(
          $jscomp.AsyncGeneratorWrapper$ActionEnum.YIELD_VALUE, value));
};

/**
 * @override
 * @param {*=} exception
 * @return {!Promise<!IIterableResult<VALUE>>}
 * @suppress {reportUnknownTypes}
 */
$jscomp.AsyncGeneratorWrapper.prototype.throw = function(exception) {
  return this.enqueueMethod_(
      $jscomp.AsyncGeneratorWrapper$GeneratorMethod.THROW, exception);
};

/**
 * Recursively executes all frames in the executionQueue until it is empty.
 * Frames that are added to the queue while execution is being performed will
 * be executed when they are reached.
 *
 * In order to guarantee each frame in the entire queue will be processed
 * exactly once, each branch in runDelegateFrame and runGeneratorFrame should
 * conclude with the following specification:
 *
 * If the frame is ready to be resolved/rejected:
 *
 *  1. Resolve or reject the frame.
 *  2. Drop the frame from the head of the queue.
 *  3. End with a call to runFrame.
 *
 * Otherwise, if another action must be performed:
 *
 *  1. Mutate the frame's method and param to reflect the next action.
 *  2. End with a call to runFrame.
 *
 * @private
 * @suppress {reportUnknownTypes}
 */
$jscomp.AsyncGeneratorWrapper.prototype.runFrame_ = function() {
  if (!this.executionQueue_.isEmpty()) {
    try {
      if (this.delegate_) {
        this.runDelegateFrame_();
      } else {
        this.runGeneratorFrame_();
      }
    } catch (err) {
      this.rejectAndClose_(err);
    }
  }
};

/**
 * For safety, all branches should meet invariants listed in runFrame.
 *
 * @private
 * @suppress {reportUnknownTypes}
 */
$jscomp.AsyncGeneratorWrapper.prototype.runGeneratorFrame_ = function() {
  var self = this;
  var frame = this.executionQueue_.first();
  try {
    var genRec = this.generator_[frame.method](frame.param);
    if (genRec.value instanceof $jscomp.AsyncGeneratorWrapper$ActionRecord) {
      switch (genRec.value.action) {
        case $jscomp.AsyncGeneratorWrapper$ActionEnum.YIELD_VALUE:
          Promise.resolve(genRec.value.value)
              .then(
                  function(resolvedValue) {
                    frame.resolve({value: resolvedValue, done: genRec.done});
                    self.executionQueue_.drop();
                    self.runFrame_();
                  },
                  function(e) {
                    frame.reject(e);
                    self.executionQueue_.drop();
                    self.runFrame_();
                  })
              .catch(this.boundRejectAndClose_);
          return;

        case $jscomp.AsyncGeneratorWrapper$ActionEnum.YIELD_STAR:
          self.delegate_ = $jscomp.makeAsyncIterator(genRec.value.value);
          frame.method = $jscomp.AsyncGeneratorWrapper$GeneratorMethod.NEXT;
          frame.param = undefined;
          self.runFrame_();
          return;

        case $jscomp.AsyncGeneratorWrapper$ActionEnum.AWAIT_VALUE:
          Promise.resolve(genRec.value.value)
              .then(
                  function(resolvedValue) {
                    frame.method =
                        $jscomp.AsyncGeneratorWrapper$GeneratorMethod.NEXT;
                    frame.param = resolvedValue;
                    self.runFrame_();
                  },
                  function(thrownErr) {
                    frame.method =
                        $jscomp.AsyncGeneratorWrapper$GeneratorMethod.THROW;
                    frame.param = thrownErr;
                    self.runFrame_();
                  })
              .catch(this.boundRejectAndClose_);
          return;

        default:
          throw new Error('Unrecognized AsyncGeneratorWrapper$ActionEnum');
      }
    }
    else {
      frame.resolve(genRec);
      self.executionQueue_.drop();
      self.runFrame_();
    }
  } catch (e) {
    frame.reject(e);
    self.executionQueue_.drop();
    self.runFrame_();
  }
};


/**
 * For safety, all branches should meet invariants listed in runFrame.
 *
 * @private
 * @suppress {reportUnknownTypes}
 */
$jscomp.AsyncGeneratorWrapper.prototype.runDelegateFrame_ = function() {
  if (!this.delegate_) {
    throw new Error("no delegate to perform execution");
  }
  var frame = this.executionQueue_.first();
  if (frame.method in this.delegate_) {
    try {
      this.delegate_[frame.method](frame.param)
          .then(this.boundHandleDelegateResult_, this.boundHandleDelegateError_)
          .catch(this.boundRejectAndClose_);
    } catch (err) {
      this.handleDelegateError_(err);
    }
  } else {
    this.delegate_ = null;
    this.runFrame_();
  }
};

/**
 * @param {!IIterableResult<VALUE>} record
 * @private
 * @suppress {reportUnknownTypes}
 */
$jscomp.AsyncGeneratorWrapper.prototype.handleDelegateResult_ = function(
    record) {
  var frame = this.executionQueue_.first();
  if (record.done === true) {
    // Delegate is done. Its return value becomes the value of the `yield*`
    // expression. We must continue the async generator as if next() were called
    // with that value here.
    this.delegate_ = null;
    frame.method = $jscomp.AsyncGeneratorWrapper$GeneratorMethod.NEXT;
    frame.param = record.value;
    this.runFrame_();
  } else {
    frame.resolve({value: record.value, done: false});
    this.executionQueue_.drop();
    this.runFrame_();
  }
};

/**
 * @param {*} thrownError
 * @private
 * @suppress {reportUnknownTypes}
 */
$jscomp.AsyncGeneratorWrapper.prototype.handleDelegateError_ = function(
    thrownError) {
  var frame = this.executionQueue_.first();
  // The delegate threw an exception or rejected a promise, so we must continue
  // our generator as if the `yield *` threw the exception.
  this.delegate_ = null;
  frame.method = $jscomp.AsyncGeneratorWrapper$GeneratorMethod.THROW;
  frame.param = thrownError;
  this.runFrame_();
};

/**
 * Rejects the current frame and closes the generator.
 *
 * @param {*} err Error causing the rejection
 * @private
 */
$jscomp.AsyncGeneratorWrapper.prototype.rejectAndClose_ = function(err) {
  if (!this.executionQueue_.isEmpty()) {
    this.executionQueue_.first().reject(err);
    this.executionQueue_.drop();
  }

  if (this.delegate_ && 'return' in this.delegate_) {
    this.delegate_['return'](undefined);
    this.delegate_ = null;
  }
  this.generator_['return'](undefined);

  // Keep processing all frames remaining in the queue.
  // Note: Some of these frames might be throw requests, but our backing
  // generator will handle these appropriately.
  this.runFrame_();
};
