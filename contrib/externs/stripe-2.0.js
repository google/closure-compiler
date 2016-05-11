/**
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
 * @fileoverview Externs for Stripe.js
 *
 * @see https://stripe.com/docs/stripe.js
 * @externs
 */

/** @const */
var Stripe = {};

/** @const */
Stripe.card = {};

/** @const */
Stripe.bankAccount = {};

/**
 * @param {string} t
 */
Stripe.setPublishableKey = function(t) { };

/**
 * @param {Object} t
 * @param {Function} r
 */
Stripe.card.createToken = function(t,r) {};

/**
 * @param {string} t
 */
Stripe.card.validateCardNumber = function(t) { };

/**
 * @param {string} t
 * @param {string} n
 */
Stripe.card.validateExpiry = function(t, n) { };

/**
 * @param {string} t
 */
Stripe.card.validateCVC = function(t) { };

/**
 * @param {string} t
 */
Stripe.card.cardType = function(t) { };

/**
 * @param {Object} t
 * @param {Function} r
 */
Stripe.bankAccount.createToken = function(t,r) {};

/**
 * @param {string} t
 * @param {string} r
 */
Stripe.bankAccount.validateRoutingNumber = function(t, r) { };

/**
 * @param {string} t
 * @param {string} r
 */
Stripe.bankAccount.validateAccountNumber = function(t, r) { };
