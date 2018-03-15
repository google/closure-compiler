/*
 * Copyright 2018 The Closure Compiler Authors.
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
 * @fileoverview Externs for Microsoft office.js
 * (https://appsforoffice.microsoft.com/lib/1.1/hosted/office.js)
 *
 * @externs
 */


/**
 * Namespace.
 * See https://dev.office.com/reference/add-ins/outlook/1.1/Office
 * @const
 */
var Office = {};

/**
 * Namespace.
 * @const
 */
Office.AsyncResultStatus = {};

/** @type {string} */
Office.AsyncResultStatus.Succeeded;

/** @type {string} */
Office.AsyncResultStatus.Failed;

/**
 * Namespace.
 * @const
 */
Office.ItemType = {};

/** @type {string} */
Office.ItemType.Email;

/** @type {string} */
Office.ItemType
    .Appoinment;  // Note: Intentionally misspelled according to docs.

/**
 * Namespace.
 * @const
 */
Office.EventType = {};

/** @type {string} */
Office.EventType.DialogMessageReceived;

/** @type {string} */
Office.EventType.DialogEventReceived;

/**
 * Namespace.
 * @const
 */
Office.CoercionType = {};

/** @type {string} */
Office.CoercionType.Html;

/** @type {string} */
Office.CoercionType.Text;

/**
 * Namespace.
 * @const
 */
Office.SourceProperty = {};

/** @type {string} */
Office.SourceProperty.Body;

/** @type {string} */
Office.SourceProperty.Subject;

/** @type {!Context} */
Office.context;


/**
 * Office events.
 * @interface
 * @const
 */
var OfficeEvent = function() {};

/**
 * Marks an event as completed.
 */
OfficeEvent.prototype.completed = function() { };

/**
 * @type {{id: string}}
 */
OfficeEvent.prototype.source;


/**
 * Roaming Settings object.
 * @interface
 * @const
 */
var RoamingSettings = function() {};

/**
 * @param {string} name
 * @return {?string|number|boolean|Object|Array}
 */
RoamingSettings.prototype.get = function(name) {};

/**
 * @param {string} name
 */
RoamingSettings.prototype.remove = function(name) {};

/**
 * @param {function(!AsyncResult)} callback
 */
RoamingSettings.prototype.saveAsync = function(callback) {};

/**
 * @param {string} name
 * @param {string|number|boolean|!Object|!Array} value
 */
RoamingSettings.prototype.set = function(name, value) {};


/**
 * Async Result object
 * @interface
 * @const
 */
var AsyncResult = function(){};

/** @type {!Object} */
AsyncResult.prototype.asyncContext;

/** @type {!Error} */
AsyncResult.prototype.error;

/** @type {string} */
AsyncResult.prototype.status;

/** @type {!Dialog} */
AsyncResult.prototype.value;


/**
 * See https://dev.office.com/reference/add-ins/shared/context
 * @interface
 * @const
 */
var Context = function(){};

/** @type {boolean} */
Context.prototype.commerceAllowed;

/** @type {string} */
Context.prototype.contentLanguage;

/** @type {string} */
Context.prototype.displayLanguage;

/** @type {string} */
Context.prototype.host;

/** @type {string} */
Context.prototype.platform;

/** @type {!Object} */
Context.prototype.requirements;

/** @type {!RoamingSettings} */
Context.prototype.roamingSettings;

/** @type {boolean} */
Context.prototype.touchEnabled;

/** @type {!OfficeUi} */
Context.prototype.ui;

/** @type {!OfficeMailbox} */
Context.prototype.mailbox;


/**
 * See
 * https://dev.office.com/reference/add-ins/outlook/1.1/Office.context.mailbox
 * @interface
 * @const
 */
var OfficeMailbox = function() {};

/**
 * @type {!MailboxItem}
 */
OfficeMailbox.prototype.item;


/**
 * See
 * https://dev.office.com/reference/add-ins/outlook/1.1/Office.context.mailbox.item
 * @interface
 * @const
 */
var MailboxItem = function() {};

/**
 * @type {!MailboxItemBody}
 */
MailboxItem.prototype.body;


/**
 * See https://dev.office.com/reference/add-ins/outlook/1.1/Body
 * @interface
 * @const
 */
var MailboxItemBody = function() {};

/**
 * @param {{
 *   asyncContext: (!Object|undefined)
 * }} params
 * @param {!function(!AsyncResult)} callback
 */
MailboxItemBody.prototype.getTypeAsync = function(params, callback) {};

/**
 * @param {!string} content
 * @param {{
 *   coercionType: string,
 *   asyncContet: (!Object|undefined)
 * }} params
 * @param {!function(!AsyncResult)} callback
 */
MailboxItemBody.prototype.prependAsync = function(content, params, callback) {};

/**
 * @param {string} data
 * @param {{
 *   coercionType: string,
 * }} options
 * @param {!function(!AsyncResult)} callback
 */
MailboxItemBody.prototype.setSelectedDataAsync = function(
    data, options, callback) {};

/** @type {string} */
MailboxItemBody.prototype.itemType;


/**
 * See https://dev.office.com/reference/add-ins/shared/officeui
 * @interface
 * @const
 */
var OfficeUi = function() {};

/**
 * No params, no returns.
 */
OfficeUi.prototype.closeContainer = function() {};

/**
 * @param {string} startAddress
 * @param {!Object.<{
 *   width: number,
 *   height: number,
 *   displayInIframe: boolean
 * }>} options
 * @param {function(!AsyncResult)} callback
 */
OfficeUi.prototype.displayDialogAsync = function(
    startAddress, options, callback) {};

/**
 * @param {string|boolean} messageObject
 */
OfficeUi.prototype.messageParent = function(messageObject) {};


/**
 * See https://dev.office.com/reference/add-ins/shared/officeui.dialog
 * @interface
 * @const
 */
var Dialog = function() {};

/**
 * @param {string} event
 * @param {function(!Object)} callback
 */
Dialog.prototype.addEventHandler = function(event, callback) {};

/**
 * No params, no returns.
 */
Dialog.prototype.close = function() {};
