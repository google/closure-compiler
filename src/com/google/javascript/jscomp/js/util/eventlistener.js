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

/**
 * @fileoverview Patches to EventTarget's addEventListener and
 * removeEventListener to allow wrapping the listener.  This is subtle and needs
 * to be done in a single place so that there is a single map from user-provided
 * listeners to wrapped listener (for removal).
 */

'require base';
'require es6/weakmap';

/**
 * @typedef {function(!Function, string, !Object): !Function}
 * @private
 */
$jscomp.EventListenerWrapper;

/**
 * Map from event type to the list of wrappers to be called when adding a
 * listener for that event type.
 * @const {!Object<string, !Array<!$jscomp.EventListenerWrapper>>}
 */
$jscomp.eventListenerWrappers = {};

/**
 * Patches the `addEventListener` and `removeEventListener` methods on the
 * `EventTarget`.  Even though we only need this for a small subset of target
 * classes, it's still necessary to patch the top-level `EventTarget.prototype`
 * because of Zone.js, which also patches it here.  If we install our patches
 * lower on the prototype chain, then we end up breaking Zone.js because its
 * patches get lost.
 */
$jscomp.patchEventTargetMethods = function() {
  // First we need to _find_ the `EventTarget` prototype, which is complicated
  // because some older browsers don't expose `EventTarget` as a constructor.
  var proto = '__proto__';
  var eventTargetPrototype;
  if (typeof $jscomp.global.EventTarget === 'function') {
    eventTargetPrototype = $jscomp.global.EventTarget.prototype;
  } else {
    // pull it from some other object's prototype chain
    eventTargetPrototype = $jscomp.global;
    while (eventTargetPrototype &&
           !$jscomp.owns(eventTargetPrototype, 'addEventListener')) {
      eventTargetPrototype = eventTargetPrototype[proto];
    }
  }
  if (!eventTargetPrototype) return;
  var remove = eventTargetPrototype['removeEventListener'];
  var add = eventTargetPrototype['addEventListener'];

  /**
   * Map from (listener, target) to wrapped listener.
   * @const {!WeakMap<!Function|!Object,
   *     !WeakMap<!Object, !Object<!Function>>>}
   */
  var eventListenerMap = new WeakMap();

  /**
   * A non-null key to be used when the listener is null.
   * @const {!Object}
   */
  var nullKey = {};

  eventTargetPrototype['removeEventListener'] = function(type, listener, opts) {
    var listenerKey = listener != null ? listener : nullKey;
    var targetMap = eventListenerMap.get(listenerKey);
    var eventMap = targetMap && targetMap.get(this);
    var bubble = !(typeof opts === 'boolean' ? opts : opts && opts.capture);
    var key = bubble + type;
    if (eventMap && eventMap[key]) {
      listener = eventMap[key];
      delete eventMap[key];
    }
    return remove.call(this, type, listener, opts);
  };

  eventTargetPrototype['addEventListener'] = function(type, listener, opts) {
    var wrappers = $jscomp.eventListenerWrappers[type];
    if (!wrappers) return add.call(this, type, listener, opts);

    // NOTE: addEventListener needs to look at (fn, target, capture)
    //       it's a no-op if that set is already unique.
    var listenerKey = listener != null ? listener : nullKey;
    var targetMap = eventListenerMap.get(listenerKey);
    if (!targetMap) {
      targetMap = new WeakMap();
      eventListenerMap.set(listenerKey, targetMap);
    }
    var eventMap = targetMap.get(this);
    if (!eventMap) {
      eventMap = {};
      targetMap.set(this, eventMap);
    }
    var bubble = !(typeof opts === 'boolean' ? opts : opts && opts.capture);
    var key = bubble + type;
    // NOTE: If a listener is added twice with different (possibly conflicting)
    // options, the first set of options wins and any later addition is a no-op.
    if (eventMap[key]) return;
    var delegate = listener;
    if (delegate && typeof delegate === 'object') {
      // NOTE: Listener can be an object with a handleEvent() method.  In that
      // case, we wrap it into a function that calls that method, so that we
      // don't need to worry about the distinction.
      delegate = function() {
        var h = listener.handleEvent;
        if (typeof h === 'function') return h.apply(listener, arguments);
      };
    }
    if (delegate) {
      if (opts && opts.once) {
        // Add a hook to remove the listener when it's called.
        delegate = function(delegate) {
          return function() {
            if (typeof delegate !== 'function')
              throw new Error('Unexpected listener type: ' + delegate);
            delete eventMap[key];
            return delegate.apply(this, arguments);
          };
        }(delegate);
      }
      for (var i = 0; i < wrappers.length; i++) {
        delegate = wrappers[i](delegate, type, this);
      }
      if (opts && $jscomp.global['AbortSignal'] &&
          opts.signal instanceof $jscomp.global['AbortSignal']) {
        if (opts.signal.aborted) return;
        opts.signal.addEventListener('abort', function() {
          delete eventMap[key];
        }, {once: true});
      }
    }
    eventMap[key] = delegate;
    return add.call(this, type, delegate, opts);
  };

  $jscomp.patchEventTargetMethods = function() {};
};

/**
 * Patch event listeners for the given event types.  Listeners for these events
 * will be wrapped by the wrapper function.
 *
 * @param {!Array<string>} events The events to instrument
 * @param {!$jscomp.EventListenerWrapper} wrapper Wrapper to apply to this event
 */
$jscomp.patchEventListeners = function(events, wrapper) {
  $jscomp.patchEventTargetMethods();
  for (let i = 0; i < events.length; i++) {
    ($jscomp.eventListenerWrappers[events[i]] ||
     ($jscomp.eventListenerWrappers[events[i]] = []))
        .push(wrapper);
  }
};
