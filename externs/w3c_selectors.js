/*
 * Copyright 2008 Google Inc.
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
 * @fileoverview Definitions for W3C's Selectors API.
 *  This file depends on w3c_dom1.js.
 *  Created from http://www.w3.org/TR/selectors-api/
 *
 * @externs
 */

/**
 * @param {string} selectors
 * @return {Element}
 * @override
 */
Document.prototype.querySelector = function(selectors) {};

/**
 * @param {string} selectors
 * @return {!NodeList}
 * @override
 */
Document.prototype.querySelectorAll = function(selectors) {};

/**
 * @param {string} selectors
 * @return {Element}
 * @override
 */
Element.prototype.querySelector = function(selectors) {};

/**
 * @param {string} selectors
 * @return {!NodeList}
 * @override
 */
Element.prototype.querySelectorAll = function(selectors) {};
