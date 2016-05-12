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
 * @param {string} publishableKey
 */
Stripe.setPublishableKey = function(publishableKey) {};

/**
 * @param {Object} cardDetails
 * @param {Function} callback
 */
Stripe.card.createToken = function(cardDetails, callback) {};

/**
 * @param {string} cardNumber
 */
Stripe.card.validateCardNumber = function(cardNumber) {};

/**
 * @param {string} month
 * @param {string} year
 */
Stripe.card.validateExpiry = function(month, year) {};

/**
 * @param {string} cvc
 */
Stripe.card.validateCVC = function(cvc) {};

/**
 * @param {string} cardType
 */
Stripe.card.cardType = function(cardType) {};

/**
 * @param {Object} accountDetails
 * @param {Function} callback
 */
Stripe.bankAccount.createToken = function(accountDetails, callback) {};

/**
 * @param {string} routingNumber
 * @param {string} country
 */
Stripe.bankAccount.validateRoutingNumber = function(routingNumber, country) {};

/**
 * @param {string} accountNumber
 * @param {string} country
 */
Stripe.bankAccount.validateAccountNumber = function(accountNumber, country) {};
