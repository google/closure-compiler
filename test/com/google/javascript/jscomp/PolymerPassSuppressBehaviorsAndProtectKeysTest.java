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
package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link PolymerPassSuppressBehaviorsAndProtectKeys} */
@RunWith(JUnit4.class)
public class PolymerPassSuppressBehaviorsAndProtectKeysTest extends CompilerTestCase {

  private static final String EXTERNS =
      MINIMAL_EXTERNS
          + """
          /** @constructor */
          var HTMLElement = function() {};
          /** @constructor @extends {HTMLElement} */
          var HTMLInputElement = function() {};
          /** @constructor @extends {HTMLElement} */
          var PolymerElement = function() {};
          /** @type {!Object} */
          PolymerElement.prototype.$;
          PolymerElement.prototype.created = function() {};
          PolymerElement.prototype.ready = function() {};
          PolymerElement.prototype.attached = function() {};
          PolymerElement.prototype.domReady = function() {};
          PolymerElement.prototype.detached = function() {};
          /**
           * Call the callback after a timeout. Calling job again with the same name
           * resets the timer but will not result in additional calls to callback.
           *
           * @param {string} name
           * @param {Function} callback
           * @param {number} timeoutMillis The minimum delay in milliseconds before
           *     calling the callback.
           */
          PolymerElement.prototype.job = function(name, callback, timeoutMillis) {};
          /**
           * @param a {!Object}
           * @return {!function()}
           */
          var Polymer = function(a) {};
          var alert = function(msg) {};
          """;

  public PolymerPassSuppressBehaviorsAndProtectKeysTest() {
    super(EXTERNS);
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        PolymerPassSuppressBehaviorsAndProtectKeys suppressBehaviorsCallback =
            new PolymerPassSuppressBehaviorsAndProtectKeys(compiler);
        NodeTraversal.traverse(compiler, root, suppressBehaviorsCallback);
      }
    };
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
    allowExternsChanges();
    enableRunTypeCheckAfterProcessing();
    enableParseTypeInfo();
  }

  @Test
  public void testPropertyTypeRemoval() {
    test(
        """
        /** @polymerBehavior */
        var FunBehavior = {
          properties: {
            /** @type {boolean} */
            isFun: {
              type: Boolean,
              value: true,
            },
            /** @type {!Element} */
            funObject: {
              type: Object,
            },
            /** @type {!Array<String>} */
            funArray: {
              type: Array,
            },
          },
        };
        """,
        """
        /** @polymerBehavior @nocollapse */
        var FunBehavior = {
          properties: {
            isFun: {
              type: Boolean,
              value: true,
            },
            funObject: {
              type: Object,
            },
            funArray: {
              type: Array,
            },
          },
        };
        """);
  }

  @Test
  public void testDefaultValueSuppression() {
    test(
        """
        /** @polymerBehavior */
        var FunBehavior = {
          properties: {
            isFun: {
              type: Boolean,
              value: true,
            },
            funObject: {
              type: Object,
              value: function() { return {fun: this.isFun }; },
            },
            funArray: {
              type: Array,
              value: function() { return [this.isFun]; },
            },
          },
        };
        """,
        """
        /** @polymerBehavior @nocollapse */
        var FunBehavior = {
          properties: {
            isFun: {
              type: Boolean,
              value: true,
            },
            funObject: {
              type: Object,
              /** @suppress {checkTypes|globalThis|visibility} */
              value: function() { return {fun: this.isFun }; },
            },
            funArray: {
              type: Array,
              /** @suppress {checkTypes|globalThis|visibility} */
              value: function() { return [this.isFun]; },
            },
          },
        };
        """);
  }

  @Test
  public void testConstBehaviours() {
    disableTypeCheck();

    test(
        """
        /** @polymerBehavior */
        const FunBehavior = {
        };
        """,
        """
        /** @polymerBehavior @nocollapse */
        const FunBehavior = {
        };
        """);
  }

  @Test
  public void testLetBehaviours() {
    disableTypeCheck();

    test(
        """
        /** @polymerBehavior */
        let FunBehavior = {
        };
        """,
        """
        /** @polymerBehavior @nocollapse */
        let FunBehavior = {
        };
        """);
  }

  @Test
  public void testPolymerBehaviorWithoutRhsDoesntCrash() {
    disableTypeCheck();

    testError(
        "/** @polymerBehavior */ let FunBehavior;", PolymerPassErrors.POLYMER_UNQUALIFIED_BEHAVIOR);
  }

  @Test
  public void testMethodsInPropertiesAndObserversAreProtected() {
    test(
        """
        /** @polymerBehavior */
        var FunBehavior = {
          properties: {
            computedProp: {
              computed: 'quotedProp(prop1, prop2)',
            },
            observedProp: {
              observer: 'observeProp',
            },
          },
          observers: [
            'observeList(item1, item2)',
            'observeAnotherList(item3)',
          ],
          'quotedProp': function(prop1, prop2) {},
          observeProp() {},
          observeList: function(item1, item2) {},
          observeAnotherList(item3) {},
          unreferencedMethod: function() {},
        };
        """,
        """
        /**
         * @polymerBehavior
         * @nocollapse
         * @polymerBehavior
         */
        var FunBehavior = {
          properties: {
            computedProp: {
              computed: 'quotedProp(prop1, prop2)',
            },
            observedProp: {
              observer: 'observeProp',
            },
          },
          observers: [
            'observeList(item1, item2)',
            'observeAnotherList(item3)',
          ],
          /**
           * @export
           * @nocollapse
           * @suppress {checkTypes,globalThis,visibility}
           */
          'quotedProp': function(prop1, prop2) {},
          /**
           * @export
           * @nocollapse
           * @suppress {checkTypes,globalThis,visibility}
           */
          observeProp() {},
          /**
           * @export
           * @nocollapse
           * @suppress {checkTypes,globalThis,visibility}
           */
          observeList: function(item1, item2) {},
          /**
           * @export
           * @nocollapse
           * @suppress {checkTypes,globalThis,visibility}
           */
          observeAnotherList(item3) {},
          /**
           * @suppress {checkTypes,globalThis,visibility}
           */
          unreferencedMethod: function() {},
        };
        """);
  }
}
