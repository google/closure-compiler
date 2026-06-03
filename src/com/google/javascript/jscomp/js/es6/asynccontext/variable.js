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

'require util/defineproperty';
'require util/defines';
'require util/eventlistener';
'require util/owns';
'require util/patch';
'require util/polyfill';
'require es6/asynccontext/namespace';
'require es6/asynccontext/runtime';
'require es6/util/setprototypeof';
'require es6/weakmap';

/**
 * Map from promises to the context they were created in, so that unhandled
 * rejection events can restore the correct context.
 * @type {!WeakMap<!Promise<?>, !$jscomp.Context>}
 */
$jscomp.rejectionContext;

$jscomp.asyncContextShouldInstall = false;
$jscomp.polyfill('AsyncContext.Variable', function(original) {

  if (original) {
    var start = original['ᵃᶜstart'];
    if (typeof start === 'function') {
      $jscomp.asyncContextStart = start;
    }
    // TODO(sdh): This should no longer be used, see if we can delete it.
    var enter = original['_JSC'];
    if (typeof enter === 'function') {
      $jscomp.asyncContextEnter = enter;
    }
    return original;
  }

  $jscomp.asyncContextShouldInstall = true;

  // NOTE: We redefine this here so that the compiler will no longer inline it.
  // This ensures that the "exit" and "reenter" functions are aliased to
  // different function-local variables, which is necessary to work around
  // https://bugs.webkit.org/show_bug.cgi?id=291386 in Safari.
  $jscomp.asyncContextIdentity = function(x) {
    return x;
  };
  var state = $jscomp.asyncContextState;
  state[0] = state[0] || [];
  state[1] = state[1] || 0;

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
    this.index = state[1]++;
    /** @const {T|undefined} */
    this.defaultValue = options ? options.defaultValue : undefined;
  }

  /**
   * @override
   * @return {T|undefined}
   */
  Variable.prototype.get = function() {
    return this.index in state[0] ?
        state[0][this.index] :
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
    var save = state[0];
    var ctx = state[0] = save.slice();
    ctx[this.index] = value;
    try {
      return callback.apply(null, Array.prototype.slice.call(arguments, 2));
    } finally {
      state[0] = save;
    }
  };

  Variable['ᵃᶜstart'] = $jscomp.asyncContextStart = function(suspend) {
    /**
     * The local context for the currently instrumented function.  This
     * will be captured at the start of the function body and restored
     * back to the global context (state[0]) at every reentrance.
     * @type {!$jscomp.Context}
     */
    var functionContext = state[0];
    /**
     * The most recent context found before reenter() restored the
     * function context.  This is restored back to the global
     * context (state[0]) at every function exit.
     * @type {!$jscomp.Context|undefined}
     */
    var outerContext = suspend ? undefined : state[0];
    return /** @type {?} */ (function(reenter) {
      return reenter ? function(value) {
        outerContext = outerContext || state[0];
        state[0] = functionContext;
        return value;
      } : function(value) {
        if (outerContext) {
          state[0] = outerContext;
          outerContext = undefined;
        }
        return value;
      };
    });
  };

  // TODO(sdh): Remove this after tsickle update
  Variable['_JSC'] = $jscomp.asyncContextEnter =
      /** @type {?} */ (function(exit) {
        var swapper = $jscomp.asyncContextStart(exit);
        return /** @type {?} */ (function(value, reenter) {
          return swapper(reenter)(value);
        });
      });
  return Variable;
}, 'es_unstable', 'es3');

if ($jscomp.INSTRUMENT_ASYNC_CONTEXT && $jscomp.asyncContextShouldInstall) {
  (function() {
    // Patch various methods to wrap their callbacks.
    /**
     * @param {...number} var_args Indices of arguments to wrap.
     * @return {function(*): *}
     */
    var wrap = function(var_args) {
      var indices = arguments;
      return function(fn) {
        if (!fn) return fn;
        function wrapped() {
          for (var i = 0; i < indices.length; i++) {
            var index = indices[i];
            var arg = arguments[index];
            if (typeof arg === 'function') {
              arguments[index] = $jscomp.asyncContextWrap(arg);
            }
          }
          return fn.apply(this, arguments);
        }
        // TODO: b/353565216 - remove try-catch when we drop Cobalt 9 support
        // or else have a bootstrap in place to fix Cobalt.
        try {
          $jscomp.defineProperty(wrapped, 'name', {value: fn.name});
          $jscomp.defineProperty(wrapped, 'length', {value: fn.length});
          $jscomp.defineProperty(wrapped, 'toString', {
            enumerable: false,
            configurable: true,
            writable: true,
            value: function() {
              return fn.toString();
            }
          });
        } catch (ignored) {
        }
        return wrapped;
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
    //  - Deprecated APIs like old IE event handlers (including the `onfoo`
    //    style), webkit notifications, etc

    // Map to save the context that needs to be restored before a callback runs.
    // Currently this is only used for XMLHttpRequest.send(), but we may need
    // to reuse it for other cases as well.
    /** @type {!WeakMap<!Object, *>} */
    var snapshotMap = new WeakMap();
    $jscomp.patch('XMLHttpRequest.prototype.send', function(originalSend) {
      $jscomp.patchEventListeners(['readystatechange'], function(listener) {
        return function() {
          var save = $jscomp.asyncContextState[0];
          try {
            $jscomp.asyncContextState[0] = snapshotMap.get(this) || save;
            return listener.apply(this, arguments);
          } finally {
            $jscomp.asyncContextState[0] = save;
          }
        };
      });
      return function() {
        snapshotMap.set(this, $jscomp.asyncContextState[0]);
        return originalSend.apply(this, arguments);
      }
    });
  })();
}
