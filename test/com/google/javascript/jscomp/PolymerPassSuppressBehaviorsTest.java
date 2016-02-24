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

/**
 * Unit tests for {@link PolymerPassSuppressBehaviors}
 */
public class PolymerPassSuppressBehaviorsTest extends Es6CompilerTestCase {

  private static final String EXTERNS =
      LINE_JOINER.join(
          "/** @constructor */",
          "var HTMLElement = function() {};",
          "/** @constructor @extends {HTMLElement} */",
          "var HTMLInputElement = function() {};",
          "/** @constructor @extends {HTMLElement} */",
          "var PolymerElement = function() {};",
          "/** @type {!Object} */",
          "PolymerElement.prototype.$;",
          "PolymerElement.prototype.created = function() {};",
          "PolymerElement.prototype.ready = function() {};",
          "PolymerElement.prototype.attached = function() {};",
          "PolymerElement.prototype.domReady = function() {};",
          "PolymerElement.prototype.detached = function() {};",
          "/**",
          " * Call the callback after a timeout. Calling job again with the same name",
          " * resets the timer but will not result in additional calls to callback.",
          " *",
          " * @param {string} name",
          " * @param {Function} callback",
          " * @param {number} timeoutMillis The minimum delay in milliseconds before",
          " *     calling the callback.",
          " */",
          "PolymerElement.prototype.job = function(name, callback, timeoutMillis) {};",
          "/**",
          " * @param a {!Object}",
          " * @return {!function()}",
          " */",
          "var Polymer = function(a) {};",
          "var alert = function(msg) {};");

  public PolymerPassSuppressBehaviorsTest() {
    super(EXTERNS);
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        PolymerPassSuppressBehaviors suppressBehaviorsCallback =
            new PolymerPassSuppressBehaviors(compiler);
        NodeTraversal.traverseEs6(compiler, root, suppressBehaviorsCallback);
      }
    };
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    allowExternsChanges(true);
    enableTypeCheck();
    runTypeCheckAfterProcessing = true;
    parseTypeInfo = true;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testPropertyTypeRemoval() {
    test(
        LINE_JOINER.join(
            "/** @polymerBehavior */",
            "var FunBehavior = {",
            "  properties: {",
            "    /** @type {boolean} */",
            "    isFun: {",
            "      type: Boolean,",
            "      value: true,",
            "    },",
            "    /** @type {!Element} */",
            "    funObject: {",
            "      type: Object,",
            "    },",
            "    /** @type {!Array<String>} */",
            "    funArray: {",
            "      type: Array,",
            "    },",
            "  },",
            "};"),

        LINE_JOINER.join(
            "/** @polymerBehavior @nocollapse */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: {",
            "      type: Boolean,",
            "      value: true,",
            "    },",
            "    funObject: {",
            "      type: Object,",
            "    },",
            "    funArray: {",
            "      type: Array,",
            "    },",
            "  },",
            "};"));
  }

  public void testDefaultValueSuppression() {
    test(
        LINE_JOINER.join(
            "/** @polymerBehavior */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: {",
            "      type: Boolean,",
            "      value: true,",
            "    },",
            "    funObject: {",
            "      type: Object,",
            "      value: function() { return {fun: this.isFun }; },",
            "    },",
            "    funArray: {",
            "      type: Array,",
            "      value: function() { return [this.isFun]; },",
            "    },",
            "  },",
            "};"),

        LINE_JOINER.join(
            "/** @polymerBehavior @nocollapse */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: {",
            "      type: Boolean,",
            "      value: true,",
            "    },",
            "    funObject: {",
            "      type: Object,",
            "      /** @suppress {checkTypes|globalThis} */",
            "      value: function() { return {fun: this.isFun }; },",
            "    },",
            "    funArray: {",
            "      type: Array,",
            "      /** @suppress {checkTypes|globalThis} */",
            "      value: function() { return [this.isFun]; },",
            "    },",
            "  },",
            "};"));
  }

}
