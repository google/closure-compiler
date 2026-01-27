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

'require base';
'require es6/util/setprototypeof';
'require es6/util/makeiterator';

/**
 * @fileoverview Implementation for $jscomp.generator
 *
 * This closure-compiler internal JavaScript library provides an ES3-compatible
 * API for writing generator functions using a minimum of boilerplate.
 *
 * Example:
 * ```javascript
 * // yields numbers starting with the given value, then incrementing by the
 * // value supplied to the next() method until the computed value is <= min or
 * // >= max. Then it returns the total number of times it yielded.
 * // If the client code calls throw(), the error will be logged and then
 * // yielded, but the generator won't terminate.
 * function *es6Definition(start, min, max) {
 *   let currentValue = start;
 *   let yieldCount = 0;
 *   while (currentValue > min && currentValue < max) {
 *     try {
 *       currentValue += yield(currentValue);
 *     } catch (e) {
 *       yield(e);
 *       console.log('client threw error', e);
 *     } finally {
 *       yieldCount++;
 *     }
 *   }
 *   return [yieldCount, currentValue];
 * }
 *
 * function es3Definition(start, min, max) {
 *   var currentValue;
 *   var yieldCount;
 *   var e;
 *
 *   return $jscomp.generator.createGenerator(
 *       es3Definition,
 *       function (context$) {
 *         switch (context$.nextAddress) {
 *           case 1: // execution always starts with 1
 *             currentValue = start;
 *             yieldCount = 0;
 *             // fall-through
 *
 *           case 2:
 *             if (!(currentValue > min && currentValue < max)) {
 *               // exit while loop:
 *               return context$.jumpTo(3);
 *             }
 *             // try {
 *             JSCompiler_temp_const$jscomp$1 = currentValue;
 *             context$.setCatchFinallyBlocks(4, 5);
 *             return context$.yield(currentValue, 7);
 *
 *           case 7:
 *             currentValue =
 *                 JSCompiler_temp_const$jscomp$1 + context$.yieldResult;
 *             // fall-through: execute finally block
 *
 *           case 5: // finally block start
 *             context$.enterFinallyBlock();
 *             yieldCount++;
 *             return context$.leaveFinallyBlock(6);
 *
 *           case 4: // catch block start
 *             e = context$.enterCatchBlock();
 *             return context$.yield(e, 8);
 *
 *           case 8: // finish catch block
 *             console.log('client threw error', e);
 *             return context$.jumpTo(5);
 *
 *           case 6:
 *             context$.jumpTo(2);
 *             break;
 *
 *           case 3:
 *             // come back here when while loop block exits
 *             return context$.return([yieldCount, currentValue]);
 *         }
 *       }
 *   });
 * };
 * ```
 */

/** @const */
$jscomp.generator = {};

/**
 * Ensures that the iterator result is actually an object.
 *
 * @private
 * @final
 * @param {*} result
 * @return {void}
 * @throws {TypeError} if the result is not an instenace of Object.
 */
$jscomp.generator.ensureIteratorResultIsObject_ = function(result) {
  if (result instanceof Object) {
    return;
  }
  throw new TypeError('Iterator result ' + result + ' is not an object');
};


/**
 * Tracks state machine state used by generator.Engine.
 *
 * @template VALUE
 * @constructor
 * @final
 * @struct
 */
$jscomp.generator.Context = function() {
  /**
   * Whether the generator program is being executed at the moment in the
   * current context. Is used to prevent reentrancy.
   *
   * @private
   * @type {boolean}
   */
  this.isRunning_ = false;

  /**
   * An iterator that should yield all its values before the main program can
   * continue.
   *
   * @private
   * @type {?Iterator<VALUE>}
   */
  this.yieldAllIterator_ = null;

  /**
   * The value that will be sent to the program as the result of suspended
   * yield expression.
   *
   * @private {?}
   */
  this.yieldResult = undefined;

  /**
   * The next address where the state machine execution should be resumed.
   *
   * <p>Program execution starts at 1 and ends at 0.
   *
   * @private {number}
   */
  this.nextAddress = 1;

  /**
   * The address that should be executed once an exception is thrown.
   *
   * <p>Value of 0 means no catch block exist that would handles an exception.
   *
   * @private
   * @type {number}
   */
  this.catchAddress_ = 0;

  /**
   * The address that should be executed once the result is being returned
   * or if the exception is thrown and there is no catchAddress specified.
   *
   * <p>Value of 0 means no finally block is set.
   *
   * @private
   * @type {number}
   */
  this.finallyAddress_ = 0;

  /**
   * Stores information for the runtime propagation of values and control
   * flow such as the behaviour of statements (break, continue, return and
   * throw) that perform nonlocal transfers of control.
   *
   * @private
   * @type {null|{return: VALUE}|{exception, isException: boolean}|{jumpTo: number}}.
   */
  this.abruptCompletion_ = null;

  /**
   * The preserved abruptCompletion_ when entering a `finally` block. If
   * the `finally` block completes normally the preserved abruptCompletion_ is
   * restored:
   * <pre>
   * try {
   * } finally {  // nesting level 0
   *   // abruptCompletion_ is saved in finallyContexts_[0]
   *   try {
   *   } finally {  // nesting level 1
   *     // abruptCompletion_ is saved in finallyContexts_[1]
   *     ...
   *     // abruptCompletion_ is restored from finallyContexts_[1]
   *   }
   *   // abruptCompletion_ is restored from finallyContexts_[0]
   * }
   * <pre>
   *
   * @private
   * @type {?Array<null|{return: VALUE}|{exception, isException: boolean}|{jumpTo: number}>}.
   */
  this.finallyContexts_ = null;
};

/**
 * Marks generator program as being run.
 *
 * @private
 * @final
 * @return {void}
 * @throws {TypeError} if generator is already running.
 */
$jscomp.generator.Context.prototype.start_ = function() {
  if (this.isRunning_) {
    throw new TypeError('Generator is already running');
  }
  this.isRunning_ = true;
};

/**
 *
 *
 * @private
 * @final
 * @return {void}
 */
$jscomp.generator.Context.prototype.stop_ = function() {
  this.isRunning_ = false;
};

/**
 * Transfers program execution to an appropriate catch/finally block that
 * should be executed if exception occurs.
 *
 * @private
 * @final
 * @return {void}
 */
$jscomp.generator.Context.prototype.jumpToErrorHandler_ = function() {
  this.nextAddress = this.catchAddress_ || this.finallyAddress_;
};

/**
 * Sets the result of suspended yield expression.
 *
 * @private
 * @final
 * @param {?=} value The value to send to the generator.
 * @return {void}
 * @suppress {reportUnknownTypes}
 */
$jscomp.generator.Context.prototype.next_ = function(value) {
  this.yieldResult = value;
};

/**
 * Throws exception as the result of suspended yield.
 *
 * @private
 * @final
 * @param {?} e
 * @return {void}
 * @suppress {reportUnknownTypes}
 */
$jscomp.generator.Context.prototype.throw_ = function(e) {
  this.abruptCompletion_ = {exception: e, isException: true};
  this.jumpToErrorHandler_();
};

/** Public methods */

/**
 * Returns the next address in the state machine.
 *
 * @final
 * @return {number}
 * @requireInlining
 */
$jscomp.generator.Context.prototype.getNextAddressJsc = function() {
  return this.nextAddress;
};

$jscomp.generator.Context.prototype['getNextAddressJsc'] = function() {
  return this.nextAddress;
};

/**
 * Returns the value that was set by the last yield expression.
 *
 * @final
 * @return {number}
 * @requireInlining
 */
$jscomp.generator.Context.prototype.getYieldResultJsc = function() {
  return this.yieldResult;
};

$jscomp.generator.Context.prototype['getYieldResultJsc'] = function() {
  return this.yieldResult;
};

/**
 * Returns a value as the result of generator function.
 *
 * @final
 * @param {VALUE=} value
 * @return {void}
 * @suppress {reportUnknownTypes}
 */
$jscomp.generator.Context.prototype.return = function(value) {
  this.abruptCompletion_ = {return: /** @type {VALUE} */ (value)};
  this.nextAddress = this.finallyAddress_;
};

$jscomp.generator.Context.prototype['return'] =
    $jscomp.generator.Context.prototype.return;

/**
 * Changes the context so the program execution will continue from the given
 * state after executing nessesary pending finally blocks first.
 *
 * @final
 * @param {number} nextAddress The state that should be run.
 * @return {void}
 */
$jscomp.generator.Context.prototype.jumpThroughFinallyBlocks = function(
    nextAddress) {
  this.abruptCompletion_ = {jumpTo: nextAddress};
  this.nextAddress = this.finallyAddress_;
};
$jscomp.generator.Context.prototype['jumpThroughFinallyBlocks'] =
    $jscomp.generator.Context.prototype.jumpThroughFinallyBlocks;

/**
 * Pauses the state machine program assosiated with generator function to yield
 * a value.
 *
 * @final
 * @param {VALUE} value The value to return from the generator function via
 *     the iterator protocol.
 * @param {number} resumeAddress The address where the program should resume.
 * @return {{value: VALUE}}
 * @suppress {reportUnknownTypes}
 */
$jscomp.generator.Context.prototype.yield = function(value, resumeAddress) {
  this.nextAddress = resumeAddress;
  return {value: value};
};
$jscomp.generator.Context.prototype['yield'] =
    $jscomp.generator.Context.prototype.yield;

/**
 * Causes the state machine program to yield all values from an iterator.
 *
 * @final
 * @param {string|!Iterator<VALUE>|!Iterable<VALUE>|!Arguments} iterable
 *     Iterator to yeild all values from.
 * @param {number} resumeAddress The address where the program should resume.
 * @return {void | {value: VALUE}}
 * @suppress {reportUnknownTypes}
 */
$jscomp.generator.Context.prototype.yieldAll = function(
    iterable, resumeAddress) {
  /** @const @type {!Iterator<VALUE>} */ var iterator =
      $jscomp.makeIterator(iterable);
  /** @const */ var result = iterator.next();
  $jscomp.generator.ensureIteratorResultIsObject_(result);
  if (result.done) {
    // If `someGenerator` in `x = yield *someGenerator` completes immediately,
    // x is the return value of that generator.
    this.yieldResult = result.value;
    this.nextAddress = resumeAddress;
    return;
  }
  this.yieldAllIterator_ = iterator;
  return this.yield(result.value, resumeAddress);
};
$jscomp.generator.Context.prototype['yieldAll'] =
    $jscomp.generator.Context.prototype.yieldAll;

/**
 * Changes the context so the program execution will continue from the given
 * state.
 *
 * @final
 * @param {number} nextAddress The state the program should continue
 * @return {void}
 */
$jscomp.generator.Context.prototype.jumpTo = function(nextAddress) {
  this.nextAddress = nextAddress;
};
$jscomp.generator.Context.prototype['jumpTo'] =
    $jscomp.generator.Context.prototype.jumpTo;

/**
 * Changes the context so the program execution ends.
 *
 * @final
 * @return {void}
 */
$jscomp.generator.Context.prototype.jumpToEnd = function() {
  this.nextAddress = 0;
};
$jscomp.generator.Context.prototype['jumpToEnd'] =
    $jscomp.generator.Context.prototype.jumpToEnd;

/**
 * Sets catch / finally handlers.
 * Used for try statements with catch blocks.
 *
 * @final
 * @param {number} catchAddress The address of the catch block.
 * @param {number=} finallyAddress The address of the finally block.
 * @return {void}
 */
$jscomp.generator.Context.prototype.setCatchFinallyBlocks = function(
    catchAddress, finallyAddress) {
  this.catchAddress_ = catchAddress;
  if (finallyAddress != undefined) {
    this.finallyAddress_ = finallyAddress;
  }
};
$jscomp.generator.Context.prototype['setCatchFinallyBlocks'] =
    $jscomp.generator.Context.prototype.setCatchFinallyBlocks;

/**
 * Sets finally handler.
 * Used for try statements without catch blocks.
 *
 * @const
 * @param {number=} finallyAddress The address of the finally block or 0.
 * @return {void}
 */
$jscomp.generator.Context.prototype.setFinallyBlock = function(finallyAddress) {
  this.catchAddress_ = 0;
  this.finallyAddress_ = finallyAddress || 0;
};
$jscomp.generator.Context.prototype['setFinallyBlock'] =
    $jscomp.generator.Context.prototype.setFinallyBlock;

/**
 * Sets a catch handler and jumps to the next address.
 * Used for try statements without finally blocks.
 *
 * @final
 * @param {number} nextAddress The state that should be run next.
 * @param {number=} catchAddress The address of the catch block or 0.
 * @return {void}
 */
$jscomp.generator.Context.prototype.leaveTryBlock = function(
    nextAddress, catchAddress) {
  this.nextAddress = nextAddress;
  this.catchAddress_ = catchAddress || 0;
};
$jscomp.generator.Context.prototype['leaveTryBlock'] =
    $jscomp.generator.Context.prototype.leaveTryBlock;

/**
 * Initializes exception variable in the beginning of catch block.
 *
 * @final
 * @param {number=} nextCatchBlockAddress The address of the next catch block
 *     that is preceded by no finally blocks.
 * @return {?} Returns an exception that was thrown from "try" block.
 * @suppress {reportUnknownTypes}
 */
$jscomp.generator.Context.prototype.enterCatchBlock = function(
    nextCatchBlockAddress) {
  this.catchAddress_ = nextCatchBlockAddress || 0;
  /** @const */ var exception =
      /** @type {{exception, isException: boolean}} */ (this.abruptCompletion_)
          .exception;
  this.abruptCompletion_ = null;
  return exception;
};
$jscomp.generator.Context.prototype['enterCatchBlock'] =
    $jscomp.generator.Context.prototype.enterCatchBlock;

/**
 * Saves the current throw context which will be restored at the end of finally
 * block.
 *
 * @final
 * @param {number=} nextCatchAddress
 * @param {number=} nextFinallyAddress
 * @param {number=} finallyDepth The nesting level of current "finally" block.
 * @return {void}
 */
$jscomp.generator.Context.prototype.enterFinallyBlock = function(
    nextCatchAddress, nextFinallyAddress, finallyDepth) {
  if (!finallyDepth) {
    this.finallyContexts_ = [this.abruptCompletion_];
  } else {
    /**
     * @type {!Array<null|{return: VALUE}|{exception, isException: boolean}|{jumpTo: number}>}
     */
    (this.finallyContexts_)[finallyDepth] = this.abruptCompletion_;
  }
  this.catchAddress_ = nextCatchAddress || 0;
  this.finallyAddress_ = nextFinallyAddress || 0;
};
$jscomp.generator.Context.prototype['enterFinallyBlock'] =
    $jscomp.generator.Context.prototype.enterFinallyBlock;

/**
 * Figures out whether the program execution should continue normally, or jump
 * to the closest catch/finally block.
 *
 * @final
 * @param {number} nextAddress The state that should be run next.
 * @param {number=} finallyDepth The nesting level of current "finally" block.
 * @return {void}
 * @suppress {strictMissingProperties}
 */
$jscomp.generator.Context.prototype.leaveFinallyBlock = function(
    nextAddress, finallyDepth) {
  // There could be trailing finally contexts if a nested finally throws an
  // exception or return.
  // e.g.
  // try {
  //   ...
  //   return 1;
  // } finally {
  //   // finallyDepth == 0
  //   // finallyContext == [{return: 1}]
  //   try {
  //     ...
  //     try {
  //       throw new Error(2);
  //     } finally {
  //       // finallyDepth == 1
  //       // finallyContext == [{return: 1}, {exception: Error(2)}]
  //       try {
  //         throw new Error(3);
  //       } finally {
  //         // finallyDepth == 2
  //         // finallyContext == [
  //         //     {return: 1},
  //         //     {exception: Error(2)},
  //         //     {exception: Error(3)}
  //         // ]
  //         throw new Error(4); // gets written in abruptCompletion_
  //         // leaveFinallyBlock() never gets called here
  //       }
  //       // leaveFinallyBlock() never gets called here
  //     }
  //   } catch (e) {
  //      // swallow error
  //      // abruptCompletion becomes null
  //   } finally {
  //     // finallyDepth == 1
  //     // finallyContext == [
  //     //     {return: 1},
  //     //     null, // overwritten, because catch swallowed the error
  //     //     {exception: Error(3)}  // left over
  //     // ]
  //     // leaveFinallyBlock() called here
  //     // finallyContext == [{return: 1}]
  //     // abruptCompletion == null
  //   }
  //   // leaveFinallyBlock() called here
  //   // finallyContext = []
  //   // abruptCompletion == {return: 1};
  // }
  /** @const */ var preservedContext =
      /**
       * @type {!Array<null|{return: VALUE}|{exception, isException: boolean}|{jumpTo: number}>}
       */
      (this.finallyContexts_).splice(finallyDepth || 0)[0];
  /** @const */ var abruptCompletion = this.abruptCompletion_ =
      this.abruptCompletion_ || preservedContext;
  if (abruptCompletion) {
    if (abruptCompletion.isException) {
      return this.jumpToErrorHandler_();
    }
    // Check if there is a pending break/continue jump that is not preceded by
    // finally blocks that should be executed before.
    // We always generate case numbers for the start and end of loops before
    // numbers for anything they contain, so any finally blocks within will be
    // guaranteed to have higher addresses than the loop break and continue
    // positions.
    // e.g.
    // l1: while (...) {            // generated addresses: 100: break l1;
    //       try {                  // generated addresses: 101: finally,
    //         try {                // generated addresses: 102: finally,
    //           l2: while (...) {  // generated addresses: 103: break l2;
    //
    //                 if (...) {
    //                   break l1;  // becomes
    //                              // $context.jumpThroughFinallyBlocks(101),
    //                              // since 2 finally blocks must be crossed
    //                 }
    //                 break l2;    // becomes $context.jumpTo(103)
    //               }
    //         } finally {
    //           // When leaving this finally block:
    //           // 1. We keep the abrupt completion indicating 'break l1'
    //           // 2. We jump to the enclosing finally block.
    //         }
    //       } finally {
    //         // When leaving this finally block:
    //         // 1. We complete the abruptCompletion indicating 'break l1' by
    //         //   jumping to the loop start address.
    //         // 2. Abrupt completion is now null, so normal execution
    //         //   continues from there.
    //       }
    //     }
    if (abruptCompletion.jumpTo != undefined &&
        this.finallyAddress_ < abruptCompletion.jumpTo) {
      this.nextAddress = abruptCompletion.jumpTo;
      this.abruptCompletion_ = null;
    } else {
      this.nextAddress = this.finallyAddress_;
    }
  } else {
    this.nextAddress = nextAddress;
  }
};
$jscomp.generator.Context.prototype['leaveFinallyBlock'] =
    $jscomp.generator.Context.prototype.leaveFinallyBlock;

/**
 * Is used in transpilation of `for in` statements.
 *
 * <p><code>for (var i in obj) {...}</code> becomes:
 * <pre>
 * for (var i, $for$in = context$.forIn(obj);
 *      (i = $for$in.getNext()) != null;
 *      ) {
 *   ...
 * }
 * </pre>
 *
 * @final
 * @param {?} object
 * @return {!$jscomp.generator.Context.PropertyIterator}
 * @suppress {reportUnknownTypes}
 */
$jscomp.generator.Context.prototype.forIn = function(object) {
  return new $jscomp.generator.Context.PropertyIterator(object);
};
$jscomp.generator.Context.prototype['forIn'] =
    $jscomp.generator.Context.prototype.forIn;


/** End public methods */

/**
 * @constructor
 * @final
 * @struct
 * @param {?} object
 * @suppress {reportUnknownTypes}
 */
$jscomp.generator.Context.PropertyIterator = function(object) {
  /**
   * @private
   * @const
   * @type {?}
   */
  this.object_ = object;

  /**
   * @private
   * @const
   * @type {!Array<string>}
   */
  this.properties_ = [];

  for (var property in /** @type {!Object} */ (object)) {
    this.properties_.push(property);
  }
  this.properties_.reverse();
};

/**
 * Returns the next object's property that is still valid.
 *
 * @final
 * @return {?string}
 * @suppress {reportUnknownTypes}
 */
$jscomp.generator.Context.PropertyIterator.prototype.getNext = function() {
  // The JS spec does not require that properties added after the loop begins
  // be included in the loop, but it does require that the current property
  // must still exist on the object when the loop iteration starts.
  while (this.properties_.length > 0) {
    /** @const */ var property = this.properties_.pop();
    if (property in /** @type {!Object} */ (this.object_)) {
      return property;
    }
  }
  return null;
};
$jscomp.generator.Context.PropertyIterator.prototype['getNext'] =
    $jscomp.generator.Context.PropertyIterator.prototype.getNext;

/**
 * Engine handling execution of a state machine associated with the generator
 * program and its context.
 *
 * @private
 * @template VALUE
 * @constructor
 * @final
 * @struct
 * @param {function(!$jscomp.generator.Context<VALUE>): (void|{value: VALUE})} program
 */
$jscomp.generator.Engine_ = function(program) {
  /**
   * @private
   * @const
   * @type {!$jscomp.generator.Context<VALUE>}
   */
  this.context_ = new $jscomp.generator.Context();

  /**
   * @private
   * @const
   * @type {function(!$jscomp.generator.Context<VALUE>): (void|{value: VALUE})}
   */
  this.program_ = program;
};

/**
 * Returns an object with two properties done and value.
 * You can also provide a parameter to the next method to send a value to the
 * generator.
 *
 * @private
 * @final
 * @param {?=} value The value to send to the generator.
 * @return {!IIterableResult<VALUE>}
 * @suppress {reportUnknownTypes}
 */
$jscomp.generator.Engine_.prototype.next_ = function(value) {
  this.context_.start_();
  if (this.context_.yieldAllIterator_) {
    return this.yieldAllStep_(
        this.context_.yieldAllIterator_.next, value, this.context_.next_);
  }
  this.context_.next_(value);
  return this.nextStep_();
};

/**
 * Attempts to finish the generator with a given value.
 *
 * @private
 * @final
 * @param {VALUE} value The value to return.
 * @return {!IIterableResult<VALUE>}
 * @suppress {reportUnknownTypes}
 */
$jscomp.generator.Engine_.prototype.return_ = function(value) {
  this.context_.start_();
  /** @const */ var yieldAllIterator = this.context_.yieldAllIterator_;
  if (yieldAllIterator) {
    /** @const @type {function(VALUE): !IIterableResult<VALUE>} */ var
        returnFunction =
            'return' in yieldAllIterator ? yieldAllIterator['return'] :
                                           function(v) {
                                             return {value: v, done: true};
                                           };
    return this.yieldAllStep_(returnFunction, value, this.context_.return);
  }
  this.context_.return(value);
  return this.nextStep_();
};

/**
 * Resumes the execution of a generator by throwing an error into it and
 * returns an object with two properties done and value.
 *
 * @private
 * @final
 * @param {?} exception The exception to throw.
 * @return {!IIterableResult<VALUE>}
 * @suppress {reportUnknownTypes}
 */
$jscomp.generator.Engine_.prototype.throw_ = function(exception) {
  this.context_.start_();
  if (this.context_.yieldAllIterator_) {
    return this.yieldAllStep_(
        this.context_.yieldAllIterator_['throw'], exception,
        this.context_.next_);
  }
  this.context_.throw_(exception);
  return this.nextStep_();
};

/**
 * Redirects next/throw/return method calls to an iterator passed to "yield *".
 *
 * @private
 * @final
 * @template T
 * @param {function(this:Iterator<VALUE>, T): !IIterableResult<VALUE>} action
 * @param {T} value
 * @param {function(this:$jscomp.generator.Context, VALUE): void} nextAction
 * @return {!IIterableResult<VALUE>}
 * @suppress {reportUnknownTypes}
 */
$jscomp.generator.Engine_.prototype.yieldAllStep_ = function(
    action, value, nextAction) {
  try {
    /** @const */ var result = action.call(
        /** @type {!Iterator<VALUE>} */ (this.context_.yieldAllIterator_),
        value);
    $jscomp.generator.ensureIteratorResultIsObject_(result);
    if (!result.done) {
      this.context_.stop_();
      return result;
    }
    // After `x = yield *someGenerator()` x is the return value of the
    // generator, not a value passed to this generator by the next() method.
    /** @const */ var resultValue = result.value;
  } catch (e) {
    this.context_.yieldAllIterator_ = null;
    this.context_.throw_(e);
    return this.nextStep_();
  }
  this.context_.yieldAllIterator_ = null;
  nextAction.call(this.context_, resultValue);
  return this.nextStep_();
};

/**
 * Continues/resumes program execution until the next suspension point (yield).
 *
 * @private
 * @final
 * @return {!IIterableResult<VALUE>}
 * @suppress {reportUnknownTypes, strictMissingProperties}
 */
$jscomp.generator.Engine_.prototype.nextStep_ = function() {
  while (this.context_.nextAddress) {
    try {
      /** @const */ var yieldValue = this.program_(this.context_);
      if (yieldValue) {
        this.context_.stop_();
        return {value: yieldValue.value, done: false};
      }
    } catch (e) {
      this.context_.yieldResult = undefined;
      this.context_.throw_(e);
    }
  }

  this.context_.stop_();
  if (this.context_.abruptCompletion_) {
    /** @const */ var abruptCompletion = this.context_.abruptCompletion_;
    this.context_.abruptCompletion_ = null;
    if (abruptCompletion.isException) {
      throw abruptCompletion.exception;
    }
    return {value: abruptCompletion.return, done: true};
  }
  return {value: /** @type {?} */ (undefined), done: true};
};

/**
 * The Generator object that is returned by a generator function and it
 * conforms to both the iterable protocol and the iterator protocol.
 *
 * @private
 * @template VALUE
 * @constructor
 * @final
 * @implements {Generator<VALUE>}
 * @param {!$jscomp.generator.Engine_<VALUE>} engine
 * @suppress {reportUnknownTypes}
 */
$jscomp.generator.Generator_ = function(engine) {
  /** @const @override */
  this.next = function(opt_value) {
    return engine.next_(opt_value);
  };

  /** @const @override */
  this.throw = function(exception) {
    return engine.throw_(exception);
  };

  /** @const @override */
  this.return = function(value) {
    return engine.return_(value);
  };

  /** @this {$jscomp.generator.Generator_<VALUE>} */
  this[Symbol.iterator] = function() {
    return this;
  };

  // TODO(skill): uncomment once Symbol.toStringTag is polyfilled:
  // this[Symbol.toStringTag] = 'Generator';
};

/**
 * Creates a generator backed up by Engine running a given program.
 *
 * @final
 * @template VALUE
 * @param {function(this:?, ...): (!Iterator<VALUE>|!Iterable<VALUE>)} generator
 * @param {function(!$jscomp.generator.Context<VALUE>): (void|{value: VALUE})} program
 * @return {!Generator<VALUE>}
 * @suppress {reportUnknownTypes}
 */
$jscomp.generator.createGenerator = function(generator, program) {
  /** @const */ var result =
      new $jscomp.generator.Generator_(new $jscomp.generator.Engine_(program));
  // The spec says that `myGenFunc() instanceof myGenFunc` must be true.
  // We'll make this work by setting the prototype before calling the
  // constructor every time. All of the methods of the object are defined on the
  // instance by the constructor, so this does no harm.
  // We also cast Generator_ to Object to hide dynamic inheritance from
  // jscompiler, it makes ConformanceRules$BanUnknownThis happy.
  // In some transpiled cases there may not be an explicit prototype, in which
  // case we skip this step.
  if ($jscomp.setPrototypeOf && generator.prototype) {
    /** @type {function(!Object, ?Object): !Object} */ ($jscomp.setPrototypeOf)(
        result, generator.prototype);
  }
  return result;
};
