/*
 * Copyright 2010 The Closure Compiler Authors.
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
 * @fileoverview Externs for the Google Analytics API. Including Universal
 * Analytics.
 * @see http://code.google.com/intl/en/apis/analytics/docs/gaJS/gaJSApi.html
 * @see https://developers.google.com/analytics/devguides/collection/analyticsjs
 * @externs
 */

var _gat = {};

/**
 * @param {string=} opt_account
 * @param {string=} opt_name
 * @return {_ga_tracker}
 */
_gat._createTracker = function(opt_account, opt_name) {};

/**
 * @param {string=} opt_name
 * @return {_ga_tracker}
 */
_gat._getTrackerByName = function(opt_name) {};

/**
 * @return {undefined}
 */
_gat._anonymizeIp = function() {};

var _gaq = {};

/**
 * @see http://code.google.com/apis/analytics/docs/gaJS/gaJSApi_gaq.html#_gaq.push
 * @param {...(function()|Array.<Object>)} commandArray
 * @return {number}
 */
_gaq.push = function(commandArray) {};

/**
 * @interface
 */
var _ga_tracker = function() {};

/**
 * @param {string} newIgnoredOrganicKeyword
 * @return {undefined}
 */
_ga_tracker.prototype._addIgnoredOrganic =
    function(newIgnoredOrganicKeyword) {};

/**
 * @param {string} newIgnoredReferrer
 * @return {undefined}
 */
_ga_tracker.prototype._addIgnoredRef = function(newIgnoredReferrer) {};

/**
 * @param {string} orderId
 * @param {string} sku
 * @param {string} name
 * @param {string} category
 * @param {string} price
 * @param {string} quantity
 * @return {undefined}
 */
_ga_tracker.prototype._addItem = function(orderId, sku, name, category, price,
    quantity) {};

/**
 * @param {string} newOrganicEngine
 * @param {string} newOrganicKeyword
 * @param {boolean=} opt_prepend
 * @return {undefined}
 */
_ga_tracker.prototype._addOrganic = function(newOrganicEngine,
    newOrganicKeyword, opt_prepend) {};

/**
 * The docs here (http://goo.gl/HUdY) say that the return type is
 * `_gat.GA_EComm_.Transactions_`, but that type is never defined.
 * @param {string} orderId
 * @param {string} affiliation
 * @param {string} total
 * @param {string=} opt_tax
 * @param {string=} opt_shipping
 * @param {string=} opt_city
 * @param {string=} opt_state
 * @param {string=} opt_country
 * @return {Object}
 */
_ga_tracker.prototype._addTrans = function(orderId, affiliation, total,
    opt_tax, opt_shipping, opt_city, opt_state, opt_country) {};

/**
 * @return {undefined}
 */
_ga_tracker.prototype._clearIgnoredOrganic = function() {};

/**
 * @return {undefined}
 */
_ga_tracker.prototype._clearIgnoredRef = function() {};

/**
 * @return {undefined}
 */
_ga_tracker.prototype._clearOrganic = function() {};

/**
 * @param {string} newPath
 * @return {undefined}
 */
_ga_tracker.prototype._cookiePathCopy = function(newPath) {};

/**
 * @param {number} index
 * @return {undefined}
 */
_ga_tracker.prototype._deleteCustomVar = function(index) {};

/**
 * @return {string}
 */
_ga_tracker.prototype._getName = function() {};

/**
 * @return {undefined}
 */
_ga_tracker.prototype._setAccount = function() {};

/**
 * @return {string}
 */
_ga_tracker.prototype._getAccount = function() {};

/**
 * @return {boolean}
 */
_ga_tracker.prototype._getClientInfo = function() {};

/**
 * @return {boolean}
 */
_ga_tracker.prototype._getDetectFlash = function() {};

/**
 * @return {boolean}
 */
_ga_tracker.prototype._getDetectTitle = function() {};

/**
 * @param {string} targetUrl
 * @param {boolean=} opt_useHash
 * @return {string}
 */
_ga_tracker.prototype._getLinkerUrl = function(targetUrl, opt_useHash) {};

/**
 * @return {string}
 */
_ga_tracker.prototype._getLocalGifPath = function() {};

/**
 * @return {number}
 */
_ga_tracker.prototype._getServiceMode = function() {};

/**
 * @return {string}
 */
_ga_tracker.prototype._getVersion = function() {};

/**
 * @param {number} index
 * @return {string|undefined}
 */
_ga_tracker.prototype._getVisitorCustomVar = function(index) {};

// docs wrong
/**
 * @param {string} targetUrl
 * @param {boolean=} opt_useHash
 * @return {undefined}
 */
_ga_tracker.prototype._link = function(targetUrl, opt_useHash) {};

// docs wrong
/**
 * @param {HTMLFormElement} formObject
 * @param {boolean=} opt_useHash
 * @return {undefined}
 */
_ga_tracker.prototype._linkByPost = function(formObject, opt_useHash) {};

/**
 * @param {boolean} enable
 * @return {undefined}
 */
_ga_tracker.prototype._setAllowAnchor = function(enable) {};

/**
 * @param {boolean} enable
 * @return {undefined}
 */
_ga_tracker.prototype._setAllowHash = function(enable) {};

/**
 * @param {boolean} enable
 * @return {undefined}
 */
_ga_tracker.prototype._setAllowLinker = function(enable) {};

/**
 * @param {string} newCampContentKey
 * @return {undefined}
 */
_ga_tracker.prototype._setCampContentKey = function(newCampContentKey) {};

/**
 * @param {string} newCampMedKey
 * @return {undefined}
 */
_ga_tracker.prototype._setCampMediumKey = function(newCampMedKey) {};

/**
 * @param {string} newCampNameKey
 * @return {undefined}
 */
_ga_tracker.prototype._setCampNameKey = function(newCampNameKey) {};

/**
 * @param {string} newCampNOKey
 * @return {undefined}
 */
_ga_tracker.prototype._setCampNOKey = function(newCampNOKey) {};

/**
 * @param {string} newCampSrcKey
 * @return {undefined}
 */
_ga_tracker.prototype._setCampSourceKey = function(newCampSrcKey) {};

/**
 * @param {string} newCampTermKey
 * @return {undefined}
 */
_ga_tracker.prototype._setCampTermKey = function(newCampTermKey) {};

/**
 * @param {number} cookieTimeoutMillis
 * @return {undefined}
 */
_ga_tracker.prototype._setCampaignCookieTimeout = function(cookieTimeoutMillis)
    {};

/**
 * @param {boolean} enable
 * @return {undefined}
 */
_ga_tracker.prototype._setCampaignTrack = function(enable) {};

/**
 * @param {boolean} enable
 * @return {undefined}
 */
_ga_tracker.prototype._setClientInfo = function(enable) {};

/**
 * @param {string} newCookiePath
 * @return {undefined}
 */
_ga_tracker.prototype._setCookiePath = function(newCookiePath) {};

/**
 * @param {number} index
 * @param {string} name
 * @param {string} value
 * @param {number=} opt_scope
 * @return {boolean}
 */
_ga_tracker.prototype._setCustomVar = function(index, name, value, opt_scope)
    {};

/**
 * @param {boolean} enable
 * @return {undefined}
 */
_ga_tracker.prototype._setDetectFlash = function(enable) {};

/**
 * @param {boolean} enable
 * @return {undefined}
 */
_ga_tracker.prototype._setDetectTitle = function(enable) {};

/**
 * @param {string} newDomainName
 * @return {undefined}
 */
_ga_tracker.prototype._setDomainName = function(newDomainName) {};

/**
 * @param {string} newLocalGifPath
 * @return {undefined}
 */
_ga_tracker.prototype._setLocalGifPath = function(newLocalGifPath) {};

/**
 * @return {undefined}
 */
_ga_tracker.prototype._setLocalRemoteServerMode = function() {};

/**
 * @return {undefined}
 */
_ga_tracker.prototype._setLocalServerMode = function() {};

/**
 * @param {string} newReferrerUrl
 * @return {undefined}
 */
_ga_tracker.prototype._setReferrerOverride = function(newReferrerUrl) {};

/**
 * @return {undefined}
 */
_ga_tracker.prototype._setRemoteServerMode = function() {};

/**
 * @param {string} newRate
 * @return {undefined}
 */
_ga_tracker.prototype._setSampleRate = function(newRate) {};

/**
 * @param {number} cookieTimeoutMillis
 * @return {undefined}
 */
_ga_tracker.prototype._setSessionCookieTimeout = function(cookieTimeoutMillis)
    {};

/**
 * @param {number} cookieTimeoutMillis
 * @return {undefined}
 */
_ga_tracker.prototype._setVisitorCookieTimeout = function(cookieTimeoutMillis)
    {};

/**
 * @param {string} category
 * @param {string} action
 * @param {string=} opt_label
 * @param {number=} opt_value
 * @param {boolean=} opt_noninteraction
 * @return {undefined}
 */
_ga_tracker.prototype._trackEvent = function(category, action, opt_label,
    opt_value, opt_noninteraction) {};

/**
 * @param {string=} opt_pageUrl
 * @return {undefined}
 */
_ga_tracker.prototype._trackPageview = function(opt_pageUrl) {};

/**
 * @return {undefined}
 */
_ga_tracker.prototype._trackTrans = function() {};

/* deprecated methods below */

/**
 * @param {string} account
 * @return {_ga_tracker}
 * @deprecated
 */
_gat._getTracker = function(account) {};

/**
 * @param {string} accountId
 * @param {string=} opt_name
 * @return {_ga_tracker}
 * @deprecated
 */
_gaq._createAsyncTracker = function(accountId, opt_name) {};

/**
 * @param {string=} opt_name
 * @return {_ga_tracker}
 * @deprecated
 */
_gaq._getAsyncTracker = function(opt_name) {};

/**
 * @return {undefined}
 * @deprecated
 */
_ga_tracker.prototype._initData = function() {};

/**
 * @param {number} milliseconds
 * @return {undefined}
 * @deprecated
 */
_ga_tracker.prototype._setCookiePersistence = function(milliseconds) {};

/**
 * @param {number} newDefaultTimeout
 * @return {undefined}
 * @deprecated
 */
_ga_tracker.prototype._setCookieTimeout = function(newDefaultTimeout) {};

/**
 * @param {number} newTimeout
 * @return {undefined}
 * @deprecated
 */
_ga_tracker.prototype._setSessionTimeout = function(newTimeout) {};

/**
 * @param {string} newVal
 * @return {undefined}
 * @deprecated
 */
_ga_tracker.prototype._setVar = function(newVal) {};


/**
 * Universal Analytics externs.
 * @see https://developers.google.com/analytics/devguides/collection/analyticsjs/method-reference
 */

/**
 * @interface
 */
var _ua_tracker = function() {};

/**
 * Retrieves a field from this tracker's model.
 *
 * @param {string} name Name of the field to return.
 * @return {*} The value associated with the given name.
 */
_ua_tracker.prototype.get = function(name) {};

/**
 * Updates one of more fields. This method accepts two formats for its
 * parameter list. To update a single field, the following form may be used:
 *
 *     set(name, value)
 *
 * To update multiple fields at once, an Object form is also available:
 *
 *     set({'field1': 'value1', 'field2', 'value2'});
 *
 * @param {string|!Object} nameOrObj Name of the field to update or an Object
 *     containing multiple field names and values to update.
 * @param {*} value The value to set. This parameter is ignored when the first
 *     parameter is an Object.
 */
_ua_tracker.prototype.set = function(nameOrObj, value) {};

/**
 * Sends a tracking beacon.
 *
 * This method provides a variety of acceptable parameters. In the simplest
 * format, users may invoke this method with a single object parameter and all
 * fields from that object will be copied into the model as temporary fields.
 *
 * Alternatively, users may call this method using positional parameters. In
 * that case, the first parameter is always interpreted as hitType, while the
 * remaining parameters are interpreted based on the provided hitType.
 *
 * All positional parameters are optional. Additionally the final parameter in
 * any call is allowed to be an object of fields to copy.
 *
 * @param {...} var_args Arguments.
 */
_ua_tracker.prototype.send = function(var_args) {};

/**
 * Universal Analytics main ga object.
 * @param {...*} var_args
 * @return {undefined}
 */
var ga = function(var_args) {};

/**
 * Creates a new Tracker object. If a tracker with the same name already
 * exists, this method will return the existing tracker instead.
 *
 * @param {...*} var_args Argument list.
 * @return {!_ua_tracker} The newly created tracker object.
 */
ga.create = function(var_args) {};

/**
 * Returns a tracker object with the given name, or undefined if no matching
 * tracker object was found.
 *
 * @param {string} name Name of the tracker object to retrieve.
 * @return {!_ua_tracker|undefined} The tracker object with the given name.
 */
ga.getByName = function(name) {};

/**
 * Returns an array of Tracker objects in the order they were created.
 *
 * @return {!Array.<!_ua_tracker>} An array of tracker objects.
 */
ga.getAll = function() {};

/**
 * Utility function that prints the contents of each tracker's model.
 * @return {undefined}
 */
ga.dump = function() {};

