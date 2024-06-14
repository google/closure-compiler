/*
 * Copyright 2024 The Closure Compiler Authors.
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
 * @fileoverview Provides the main working implementation of the AsyncContext
 * polyfill, along with the `$jscomp.ContextController` class, which is used
 * for instrumenting async generator and/or async functions.
 * @suppress {uselessCode}
 */

'require util/defines';
'require util/owns';
'require util/patch';
'require util/polyfill';
'require es6/asynccontext/namespace';
'require es6/asynccontext/runtime';
'require es6/util/setprototypeof';
'require es6/weakmap';

/** @typedef {!Array<*>} */
$jscomp.Context;

/**
 * Global variable holding the current thread's context object.
 * TODO - consider switching to a linked list?
 * @type {!$jscomp.Context}
 */
$jscomp.currentContext = [];

/**
 * Map from promises to the context they were created in, so that unhandled
 * rejection events can restore the correct context.
 * @type {!WeakMap<!Promise<?>, !$jscomp.Context>}
 */
$jscomp.rejectionContext;

/**
 * Class to instantiate at the top of every generator and/or async function.
 * @interface
 */
$jscomp.AsyncContext = function() {
  /**
   * The local context for the currently instrumented function.  This will be
   * captured at the start of the function body and restored back to the global
   * $jscomp.currentContext at every reentrance.
   * @type {!$jscomp.Context}
   */
  this.$functionContext;
  /**
   * The most recent context found before reenter() restored the function
   * context.  This is restored back to the global $jscomp.currentContext at
   * every function exit.
   * @type {!$jscomp.Context}
   */
  this.$outerContext;
  /**
   * Whether the outer context has been captured yet.  This ensures we only
   * capture it once per reentry (since sometimes we need to call reenter() a
   * second time, e.g. in a `finally` clause, and the second time it would
   * otherwise clobber the correctly captured outer context with the function
   * context that replaced it in the first reenter() call).
   * @type {boolean}
   */
  this.$capturedOuter;
};

/**
 * Class to instantiate at the top of every generator and/or async function.
 * @interface
 */
$jscomp.AsyncContextSnapshot = function() {
  /**
   * The local context for the currently instrumented function.  This will be
   * captured at the start of the function body and restored back to the global
   * $jscomp.currentContext at every reentrance.
   * @type {!$jscomp.Context}
   */
  this.$savedContext;
};

$jscomp.asyncContextShouldInstall = false;
$jscomp.polyfill('AsyncContext.Variable', function(original) {
  if (original) return original;
  $jscomp.asyncContextShouldInstall = true;

  var index = 0;
  /**
   * @constructor
   * @extends {AsyncContext.Variable}
   * @template T
   * @param {{name: (string|undefined), defaultValue: (T|undefined)}} options
   */
  function Variable(options) {
    /**
     * @suppress {const}
     * @const {string}
     */
    this.name = options && options.name || '';
    /** @const {number} */
    this.index = index++;
    /** @const {T|undefined} */
    this.defaultValue = options ? options.defaultValue : undefined;
  }

  /**
   * @override
   * @return {T|undefined}
   */
  Variable.prototype.get = function() {
    return this.index in $jscomp.currentContext ?
        $jscomp.currentContext[this.index] :
        this.defaultValue;
  };

  /**
   * @override
   * @template U
   * @param {T} value
   * @param {function(): U} callback
   * @param {...*} var_args
   * @return {U}
   */
  Variable.prototype.run = function(value, callback, var_args) {
    if (!$jscomp.INSTRUMENT_ASYNC_CONTEXT) {
      throw new Error('AsyncContext.Variable does not work unless compiled ' +
                      'with --instrument_async_context');
    }
    var save = $jscomp.currentContext;
    var ctx = $jscomp.currentContext = save.slice();
    ctx[this.index] = value;
    try {
      return callback.apply(null, Array.prototype.slice.call(arguments, 2));
    } finally {
      $jscomp.currentContext = save;
    }
  };
  return Variable;
}, 'es_unstable', 'es3');

if ($jscomp.INSTRUMENT_ASYNC_CONTEXT && $jscomp.asyncContextShouldInstall) {
  (function() {
    // Override the runtime shims.
    $jscomp.asyncContextEnter = /** @type {?} */ (
        /** @return {!$jscomp.AsyncContext} */
        function() {
          return /** @type {!$jscomp.AsyncContext} */ ({
            $functionContext: $jscomp.currentContext,
            $outerContext: $jscomp.currentContext,
            $capturedOuter: true,
          });
        });

    $jscomp.asyncContextReenter = /** @type {?} */ (
        /**
         * Restore the global context to the function context, saving the
         * previous global context if this is the first time reenter was called
         * since the last exit.  This is called immediately outside every
         * `await` or `yield`.
         * @template T
         * @param {T} value
         * @param {!$jscomp.AsyncContext} context
         * @return {T}
         */
        function(value, context) {
          if (!context.$capturedOuter) {
            context.$outerContext = $jscomp.currentContext;
          }
          context.$capturedOuter = true;
          $jscomp.currentContext = context.$functionContext;
          return value;
        });

    $jscomp.asyncContextExit = /** @type {?} */ (
        /**
         * Restore the global context to the outer context in preparation to
         * cede control from the instrumented function.  This is called
         * immediately inside every `await` or `yield`.
         * @template T
         * @param {T} value
         * @param {!$jscomp.AsyncContext} context
         * @return {T}
         */
        function(value, context) {
          context.$capturedOuter = false;
          $jscomp.currentContext = context.$outerContext;
          return value;
        });

    $jscomp.asyncContextSnapshot = /** @type {?} */ (
        /** @param {!$jscomp.AsyncContextSnapshot} snapshot */
        function(snapshot) {
          snapshot.$savedContext = $jscomp.currentContext;
        });

    $jscomp.asyncContextRun = /** @type {?} */ (
        /**
         * Override the no-op version in snapshot.js
         * @param {!Function} callback
         * @param {!$jscomp.Context} args
         * @param {!$jscomp.AsyncContextSnapshot} snapshot
         * @return {*}
         */
        function(callback, args, snapshot) {
          var save = $jscomp.currentContext;
          $jscomp.currentContext = snapshot.$savedContext;
          try {
            return callback.apply(null, args);
          } finally {
            $jscomp.currentContext = save;
          }
        });

    /** @type {!WeakMap<!Function, *>} */
    var wrappedFunctions = new WeakMap();

    /**
     * Override the no-op version in snapshot.js
     * @param {!Function} callback
     * @return {!Function}
     */
    $jscomp.asyncContextWrap = function(callback) {
      // If this function is already wrapped then there's no point in wrapping
      // it a second time, since the innermost wrapper will always win.  Any
      // wrappers added outside will have no observable effect (similar to how
      // the `b` is never observable in `fn.bind(a).bind(b)`).
      if (wrappedFunctions.has(callback)) return callback;
      var context = $jscomp.currentContext;
      var wrapped = function() {
        var save = $jscomp.currentContext;
        $jscomp.currentContext = context;
        try {
          return callback.apply(this, arguments);
        } finally {
          $jscomp.currentContext = save;
        }
      };
      wrappedFunctions.set(wrapped, 1);
      return wrapped;
    };

    // Patch various methods to wrap their callbacks.

    /**
     * @param {...number} var_args Indices of arguments to wrap.
     * @return {function(*): *}
     */
    var wrap = function(var_args) {
      var indices = arguments;
      return function(fn) {
        return fn && function() {
          for (var i = 0; i < indices.length; i++) {
            var index = indices[i];
            var arg = arguments[index];
            if (typeof arg === 'function') {
              arguments[index] = $jscomp.asyncContextWrap(arg);
            }
          }
          return fn.apply(this, arguments);
        };
      };
    };

    /** @const {function(*): *} */
    var wrapFirst = wrap(0);

    $jscomp.patch('Promise', function(original) {
      var Promise = original;
      var p = Promise.prototype;
      p.then = wrap(0, 1)(p.then);
      p.catch = wrapFirst(p.catch);
      p.finally = wrapFirst(p.finally);
      return Promise;
    });

    $jscomp.patch('queueMicrotask', wrapFirst);
    $jscomp.patch('requestAnimationFrame', wrapFirst);
    $jscomp.patch('requestIdleCallback', wrapFirst);
    $jscomp.patch('setImmediate', wrapFirst);
    $jscomp.patch('setInterval', wrapFirst);
    $jscomp.patch('setTimeout', wrapFirst);
    $jscomp.patch('scheduler.postTask', wrapFirst);

    $jscomp.patch('HTMLCanvasElement.prototype.toBlob', wrapFirst);
    $jscomp.patch('Database.prototype.transaction', wrap(0, 1, 2));
    $jscomp.patch('Database.prototype.readTransaction', wrap(0, 1, 2));
    $jscomp.patch('Database.prototype.changeVersion', wrap(2, 3, 4));
    $jscomp.patch('DataTransferItem.prototype.getAsString', wrapFirst);
    $jscomp.patch('BaseAudioContext.prototype.decodeAudioData', wrap(1, 2));
    $jscomp.patch('FileSystemEntry.prototype.getParent', wrap(0, 1));
    $jscomp.patch('FileSystemDirectoryEntry.prototype.getFile', wrap(2, 3));
    $jscomp.patch(
        'FileSystemDirectoryEntry.prototype.getDirectory', wrap(2, 3));
    $jscomp.patch(
        'FileSystemDirectoryReader.prototype.readEntries', wrap(0, 1));
    $jscomp.patch('FileSystemFileEntry.prototype.file', wrap(0, 1));
    $jscomp.patch('MediaSession.prototype.setActionHandler', wrap(1));

    // APIs we don't patch:
    //  - Object.definePropert(y|ies) for getter/setter
    //     - we'd rather inherit the caller's context in this case
    //  - CustomElementRegistry.register
    //     - feels wrong to wrap registration, but e.g. connectedCallback
    //       and other lifecycle methods maybe should be wrapped?
    //  - Deprecated APIs like old IE event handlers, webkit notifications, etc

    // NOTE: EventTarget needs some special handling for several reasons:
    //  1. Older browsers don't expose EventTarget as a constructor
    //  2. Listener can be an object with a handleEvent() method
    //  3. One event (unhandledrejection) shouldn't do any extra wrapping
    //     (not yet implemented).
    //  4. removeEventListener needs to get the exact same instance as was
    //     passed to addEventListener, so we need to save it on the passed
    //     function.

    var proto = '__proto__';
    var eventTargetProto;
    if (typeof $jscomp.global.EventTarget === 'function') {
      eventTargetProto = $jscomp.global.EventTarget.prototype;
    } else {
      // pull it from some other object's prototype chain
      eventTargetProto = $jscomp.global;
      while (eventTargetProto &&
             !$jscomp.owns(eventTargetProto, 'addEventListener')) {
        eventTargetProto = eventTargetProto[proto];
      }
    }
    if (eventTargetProto) {
      // NOTE: The nested WeakMap will leak memory if it's polyfilled and the
      // listener has a longer lifetime than the event target.  This seems very
      // unlikely to be a problem.
      // This maps (listener -> (event target -> (key -> delegate)))
      /**
       * @type {!WeakMap<!Function|!Object, !WeakMap<!Object,
       *     !Object<!Function>>>}
       */
      var listenerMap = new WeakMap();

      var add = eventTargetProto.addEventListener;
      var remove = eventTargetProto.removeEventListener;
      var nullKey = {};
      eventTargetProto.addEventListener = function(type, listener, opts) {
        // NOTE: addEventListener needs to look at (fn, target, capture)
        //       it's a no-op if that set is already unique.
        var listenerKey = listener != null ? listener : nullKey;
        var targetMap = listenerMap.get(listenerKey);
        if (!targetMap) listenerMap.set(listenerKey, targetMap = new WeakMap());
        var eventMap = targetMap.get(this);
        if (!eventMap) targetMap.set(this, eventMap = {});
        var bubble = !(typeof opts === 'boolean' ? opts : opts && opts.capture);
        var key = bubble + type;
        if (eventMap[key]) return;
        var delegate = listener;
        if (delegate && typeof delegate !== 'function' &&
            delegate.handleEvent) {
          delegate = function() {
            return listener.handleEvent.apply(listener, arguments);
          };
        }
        if (delegate) {
          delegate = $jscomp.asyncContextWrap(delegate);
        }
        return add.call(this, type, eventMap[key] = delegate, opts);
      };
      eventTargetProto.removeEventListener = function(type, listener, opts) {
        var targetMap = listenerMap.get(listener);
        var eventMap = targetMap && targetMap.get(this);
        var bubble = !(typeof opts === 'boolean' ? opts : opts && opts.capture);
        var key = bubble + type;
        if (eventMap && eventMap[key]) {
          listener = eventMap[key];
          delete eventMap[key];
        }
        return remove.call(this, type, listener, opts);
      };
    }
  })();
}
