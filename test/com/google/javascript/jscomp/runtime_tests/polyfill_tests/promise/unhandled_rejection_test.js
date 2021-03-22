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
 * @fileoverview Support code for testing the Promise unhandledrejection event
 * polyfill.
 */
goog.module('jscomp.runtime_tests.polyfill_tests.unhandled_rejection_test');
goog.setTestOnly();

const Timer = goog.require('goog.Timer');
const testSuite = goog.require('goog.testing.testSuite');

const expectedError = new Error('expected error');

const TIMEOUT = 400;

/**
 * Expects `unhandledrejection` handler to be triggered.
 * @param {function()} runTest A function that starts the test case when called.
 * @return {!Promise<*>}
 */
function expectUnhandledRejectionEvent(runTest) {
  return new Promise((resolve, reject) => {
    let id = -1;
    const handler = (ev) => {
      assertTrue(ev.promise instanceof Promise);
      assertEquals(ev.reason, expectedError);
      window.removeEventListener('unhandledrejection', handler);
      Timer.clear(id);
      resolve();
    };
    window.addEventListener('unhandledrejection', handler);
    runTest();
    id = Timer.callOnce(() => {
      window.removeEventListener('unhandledrejection', handler);
      reject('unhandledrejection is not triggered before timeout.');
    }, TIMEOUT);
  });
}

/**
 * Expects `unhandledrejection` handler not to be triggered.
 * @param {function()} runTest A function that starts the test case when called.
 * @return {!Promise<*>}
 */
function expectNoUnhandledRejectionEvent(runTest) {
  return new Promise((resolve, reject) => {
    const handler = () => {
      window.removeEventListener('unhandledrejection', handler);
      reject(new Error(
          'unhandledrejection handler is not supposed to be called.'));
    };
    window.addEventListener('unhandledrejection', handler);
    runTest();
    Timer.callOnce(() => {
      window.removeEventListener('unhandledrejection', handler);
      resolve();
    }, TIMEOUT);
  });
}

/**
 * Waits for a mutation observer microtask then executes the callback.
 * @param {function()} callback
 */
function mutationObserverMicrotask(callback) {
  const observer = new MutationObserver(() => {
    callback();
  });
  const node = document.createTextNode('');
  observer.observe(node, {characterData: true});
  node.data = 'foo';
}

/** @return {boolean} Whether Promise is natively supported. */
function hasNativePromise() {
  return Promise.toString().includes('[native code]');
}

/** @return {boolean} Whether PromiseRejectionEvent is natively supported. */
function hasPromiseRejectionEvent() {
  return typeof PromiseRejectionEvent !== 'undefined';
}

testSuite({
  shouldRunTests() {
    // Skip browsers that natively support promise but not unhandledrejection.
    return !hasNativePromise() || hasPromiseRejectionEvent();
  },
  testPromiseReject() {
    // `unhandledrejection` thrown from Promise.reject.
    return expectUnhandledRejectionEvent(() => {
      Promise.reject(expectedError);
    });
  },
  testHandledPromiseReject() {
    // `unhandledrejection` is not thrown when a rejection handler is attached
    // synchronously to a promise from Promise.reject.
    return expectNoUnhandledRejectionEvent(() => {
      Promise.reject(expectedError).then(null, (reason) => {
        assertEquals(reason, expectedError);
      });
    });
  },
  testWithFulfilledHandler() {
    // `unhandledrejection` is dispatched when the thennable only has a
    // fulfilled handler.
    return expectUnhandledRejectionEvent(() => {
      Promise.reject(expectedError).then(() => {
        throw new Error('onFulfilled handler should not be triggered.');
      });
    });
  },
  testDelayedRejection() {
    // `unhandledrejection` thrown from a delayed rejection.
    return expectUnhandledRejectionEvent(() => {
      new Promise((_, reject) => {
        setTimeout(() => {
          reject(expectedError);
        }, 0);
      });
    });
  },
  testHandledDelayedRejection() {
    // `unhandledrejection` is not thrown when a rejection handler is attached
    // synchronously to a promise from a delayed rejection.
    return expectNoUnhandledRejectionEvent(() => {
      new Promise((_, reject) => {
        setTimeout(() => {
          reject(expectedError);
        }, 0);
      }).then(null, (reason) => {
        assertEquals(reason, expectedError);
      });
    });
  },
  testThrowInRejectionHandler() {
    // `unhandledrejection` thrown from a throw in a rejection handler.
    return expectUnhandledRejectionEvent(() => {
      const firstError = new Error('first error');
      Promise.reject(firstError).then(null, (firstReason) => {
        assertEquals(firstReason, firstError);
        throw expectedError;
      });
    });
  },
  testHandledThrowInRejectionHandler() {
    // `unhandledrejection` is not thrown when a rejection handler is attached
    // synchronously to a throw in a rejection handler.
    return expectNoUnhandledRejectionEvent(() => {
      const firstError = new Error('first error');
      Promise.reject(firstError)
          .then(
              null,
              (firstReason) => {
                assertEquals(firstReason, firstError);
                throw expectedError;
              })
          .then(null, (secondReason) => {
            assertEquals(secondReason, expectedError);
          });
    });
  },
  testReturningPromiseRejectInFulfillmentHandler() {
    // `unhandledrejection` thrown from returning a Promise.reject in a
    // fulfillment handler.
    return expectUnhandledRejectionEvent(() => {
      Promise.resolve().then(() => Promise.reject(expectedError));
    });
  },
  testHandledReturningPromiseRejectInFulfillmentHandler() {
    // `unhandledrejection` is not thrown when a rejection handler is attached
    // synchronously to a Promise.reject in a fulfillment handler.
    return expectNoUnhandledRejectionEvent(() => {
      Promise.resolve()
          .then(() => Promise.reject(expectedError))
          .then(null, (secondReason) => {
            assertEquals(secondReason, expectedError);
          });
    });
  },
  testThrowInFulfillmentHandler() {
    // `unhandledrejection` thrown from a throw in a fulfillment handler.
    return expectUnhandledRejectionEvent(() => {
      Promise.resolve().then(() => {
        throw expectedError;
      });
    });
  },
  testHandledThrowInFulfillmentHandler() {
    // `unhandledrejection` is not thrown when a rejection handler is attached
    // synchronously to a throw in a fulfillment handler.
    return expectNoUnhandledRejectionEvent(() => {
      Promise.resolve()
          .then(() => {
            throw expectedError;
          })
          .then(null, (secondReason) => {
            assertEquals(secondReason, expectedError);
          });
    });
  },
  testReturningDelayedRejectionInFulfillmentHandler() {
    // `unhandledrejection` thrown from a delayed rejection in a fulfillment
    // handler.
    return expectUnhandledRejectionEvent(() => {
      Promise.resolve().then(() => {
        return new Promise((_, reject) => {
          setTimeout(() => {
            reject(expectedError);
          }, 0);
        });
      });
    });
  },
  testHandledReturningDelayedRejectionInFulfillmentHandler() {
    // `unhandledrejection` is not thrown when a rejection handler is attached
    // synchronously to a delayed rejection in a fulfillment handler.
    return expectNoUnhandledRejectionEvent(() => {
      Promise.resolve()
          .then(() => {
            return new Promise((_, reject) => {
              setTimeout(() => {
                reject(expectedError);
              }, 0);
            });
          })
          .then(null, (secondReason) => {
            assertEquals(secondReason, expectedError);
          });
    });
  },
  testPromiseRejectInPromiseAll() {
    // `unhandledrejection` thrown from Promise.reject through Promise.all.
    return expectUnhandledRejectionEvent(() => {
      Promise.all([Promise.reject(expectedError)]);
    });
  },
  testHandledPromiseRejectInPromiseAll() {
    // `unhandledrejection` is not thrown when a rejection handler is attached
    // synchronously to a promise from Promise.reject through Promise.all.
    return expectNoUnhandledRejectionEvent(() => {
      Promise.all([Promise.reject(expectedError)]).then(null, (reason) => {
        assertEquals(reason, expectedError);
      });
    });
  },
  testDelayedHandlingAfterMicroTask() {
    // `unhandledrejection` is not thrown when a rejection handler is attached
    // after a microtask to Promise.reject.
    if (typeof MutationObserver === 'undefined') {
      // Skip this test if MutationObserver is not supported.
      return;
    }
    return expectNoUnhandledRejectionEvent(() => {
      const promise = Promise.reject(expectedError);
      mutationObserverMicrotask(() => {
        promise.then(null, (reason) => {
          assertEquals(reason, expectedError);
        });
      });
    });
  },
  testThrowInDelayedRejectionHandler() {
    // `unhandledrejection` thrown from a throw in a delayed rejection
    // handler.
    if (typeof MutationObserver === 'undefined') {
      // Skip this test if MutationObserver is not supported.
      return;
    }
    return expectUnhandledRejectionEvent(() => {
      const firstError = new Error('first error');
      const firstPromise = new Promise((_, firstReject) => {
        firstReject(firstError);
        mutationObserverMicrotask(() => {
          firstPromise.then(null, (firstReason) => {
            assertEquals(firstReason, firstError);
            throw expectedError;
          });
        });
      });
    });
  },
  testHandledThrowInDelayedRejectionHandler() {
    // `unhandledrejection` is not thrown when a rejection handler is attached
    // synchronously to a throw in a delayed rejection handler.
    if (typeof MutationObserver === 'undefined') {
      // Skip this test if MutationObserver is not supported.
      return;
    }
    return expectNoUnhandledRejectionEvent(() => {
      const firstError = new Error('first error');
      const firstPromise = new Promise((_, firstReject) => {
        firstReject(firstError);
        mutationObserverMicrotask(() => {
          firstPromise
              .then(
                  null,
                  (firstReason) => {
                    assertEquals(firstReason, firstError);
                    throw expectedError;
                  })
              .then(null, (secondReason) => {
                assertEquals(secondReason, expectedError);
              });
        });
      });
    });
  },
});
