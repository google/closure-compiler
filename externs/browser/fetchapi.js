/*
 * Copyright 2014 The Closure Compiler Authors
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
 * @fileoverview Definitions of the fetch api.
 *
 * This api is still in development and not yet stable. Use at your
 * own risk.
 * 
 * Based on Living Standard — Last Updated 17 August 2016
 *
 * @see https://fetch.spec.whatwg.org/
 * @externs
 */


/**
 * @enum {string}
 * @see https://fetch.spec.whatwg.org/#referrerpolicy
 */
var ReferrerPolicy = {
  NONE: '',
  NO_REFERRER: 'no-referrer',
  NO_REFERRER_WHEN_DOWNGRADE: 'no-referrer-when-downgrade',
  SAMEO_RIGIN: 'same-origin',
  ORIGIN: 'origin',
  STRICT_ORIGIN: 'strict-origin',
  ORIGIN_WHEN_CROSS_ORIGIN: 'origin-when-cross-origin',
  STRICT_ORIGIN_WHEN_CROSS_ORIGIN: 'strict-origin-when-cross-origin',
  UNSAFE_URL: 'unsafe-url'
};


/**
 * @typedef {!Headers|!Array<!Array<string>>|!IObject<string,string>}
 * @see https://fetch.spec.whatwg.org/#headersinit
 */
var HeadersInit;


/**
 * @param {!HeadersInit=} opt_headersInit
 * @constructor
 * @implements {Iterable<!Array<string>>}
 * @see https://fetch.spec.whatwg.org/#headers
 */
function Headers(opt_headersInit) {}

/**
 * @param {string} name
 * @param {string} value
 * @return {undefined}
 */
Headers.prototype.append = function(name, value) {};

/**
 * @param {string} name
 * @return {undefined}
 */
Headers.prototype.delete = function(name) {};

/** @return {!Iterator<!Array<string>>} */
Headers.prototype.entries = function() {};

/**
 * @param {string} name
 * @return {?string}
 */
Headers.prototype.get = function(name) {};

/**
 * @param {string} name
 * @return {!Array<string>}
 */
Headers.prototype.getAll = function(name) {};

/**
 * @param {string} name
 * @return {boolean}
 */
Headers.prototype.has = function(name) {};

/** @return {!Iterator<string>} */
Headers.prototype.keys = function() {};

/**
 * @param {string} name
 * @param {string} value
 * @return {undefined}
 */
Headers.prototype.set = function(name, value) {};

/** @return {!Iterator<string>} */
Headers.prototype.values = function() {};

/** @return {!Iterator<!Array<string>>} */
Headers.prototype[Symbol.iterator] = function() {};


/**
 * @typedef {!Blob|!BufferSource|!FormData|string}
 * @see https://fetch.spec.whatwg.org/#bodyinit
 */
var BodyInit;


/**
 * @typedef {!BodyInit|!ReadableStream}
 * @see https://fetch.spec.whatwg.org/#responsebodyinit
 */
var ResponseBodyInit;


/**
 * @interface
 * @see https://fetch.spec.whatwg.org/#body
 */
function Body() {};

/** @type {boolean} */
Body.prototype.bodyUsed;

/** @return {!Promise<!ArrayBuffer>} */
Body.prototype.arrayBuffer = function() {};

/** @return {!Promise<!Blob>} */
Body.prototype.blob = function() {};

/** @return {!Promise<!FormData>} */
Body.prototype.formData = function() {};

/** @return {!Promise<*>} */
Body.prototype.json = function() {};

/** @return {!Promise<string>} */
Body.prototype.text = function() {};


/**
 * @typedef {!Request|string}
 * @see https://fetch.spec.whatwg.org/#requestinfo
 */
var RequestInfo;


/**
 * @param {!RequestInfo} input
 * @param {!RequestInit=} opt_init
 * @constructor
 * @implements {Body}
 * @see https://fetch.spec.whatwg.org/#request
 */
function Request(input, opt_init) {}

/** @inheritDoc */
Request.prototype.bodyUsed;

/** @inheritDoc */
Request.prototype.arrayBuffer = function() {};

/** @inheritDoc */
Request.prototype.blob = function() {};

/** @inheritDoc */
Request.prototype.formData = function() {};

/** @inheritDoc */
Request.prototype.json = function() {};

/** @inheritDoc */
Request.prototype.text = function() {};

/** @type {string} */
Request.prototype.method;

/** @type {string} */
Request.prototype.url;

/** @type {!Headers} */
Request.prototype.headers;

/** @type {!RequestType} */
Request.prototype.type;

/** @type {!RequestDestination} */
Request.prototype.destination;

/** @type {string} */
Request.prototype.referrer;

/** @type {!RequestMode} */
Request.prototype.mode;

/** @type {!RequestCredentials} */
Request.prototype.credentials;

/** @type {!RequestCache} */
Request.prototype.cache;

/** @type {!RequestRedirect} */
Request.prototype.redirect;

/** @type {string} */
Request.prototype.integrity;

/** @return {!Request} */
Request.prototype.clone = function() {};


/**
 * @record
 * @see https://fetch.spec.whatwg.org/#requestinit
 */
function RequestInit() {};

/** @type {(undefined|string)} */
RequestInit.prototype.method;

/** @type {(undefined|!HeadersInit)} */
RequestInit.prototype.headers;

/** @type {(undefined|?BodyInit)} */
RequestInit.prototype.body;

/** @type {(undefined|string)} */
RequestInit.prototype.referrer;

/** @type {(undefined|!ReferrerPolicy)} */
RequestInit.prototype.referrerPolicy;

/** @type {(undefined|!RequestMode)} */
RequestInit.prototype.mode;

/** @type {(undefined|!RequestCredentials)} */
RequestInit.prototype.credentials;

/** @type {(undefined|!RequestCache)} */
RequestInit.prototype.cache;

/** @type {(undefined|!RequestRedirect)} */
RequestInit.prototype.redirect;

/** @type {(undefined|string)} */
RequestInit.prototype.intergrity;

/** @type {(undefined|null)} */
RequestInit.prototype.window;


/**
 * @enum {string}
 * @see https://fetch.spec.whatwg.org/#requesttype
 */
var RequestType = {
  NONE: '',
  AUDIO: 'audio',
  FONT: 'font',
  IMAGE: 'image',
  SCRIPT: 'script',
  STYLE: 'style',
  TRACK: 'track',
  VIDEO: 'video'
};


/**
 * @enum {string}
 * @see https://fetch.spec.whatwg.org/#requestdestination
 */
var RequestDestination = {
  NONE: '',
  DOCUMENT: 'document',
  EMBED: 'embed',
  FONT: 'font',
  IMAGE: 'image',
  MANIFEST: 'manifest',
  MEDIA: 'media',
  OBJECT: 'object',
  REPORT: 'report',
  SCRIPT: 'script',
  SERVICEWORKER: 'serviceworker',
  SHAREDWORKER: 'sharedworker',
  STYLE: 'style',
  WORKER: 'worker',
  XSLT: 'xslt'
};


/**
 * @enum {string}
 * @see https://fetch.spec.whatwg.org/#requestmode
 */
var RequestMode = {
  NAVIGATE: 'navigate',
  SAME_ORIGIN: 'same-origin',
  NO_CORS: 'no-cors',
  CORS: 'cors'
};


/**
 * @enum {string}
 * @see https://fetch.spec.whatwg.org/#requestcredentials
 */
var RequestCredentials = {
  OMIT: 'omit',
  SAME_ORIGIN: 'same-origin',
  INCLUDE: 'include'
};


/**
 * @enum {string}
 * @see https://fetch.spec.whatwg.org/#requestcache
 */
var RequestCache = {
  DEFAULT: 'default',
  NO_STORE: 'no-store',
  RELOAD: 'reload',
  NO_CACHE: 'no-cache',
  FORCE_CACHE: 'force-cache',
  ONLY_IF_CACHED: 'only-if-cached'
};


/**
 * @enum {string}
 * @see https://fetch.spec.whatwg.org/#requestredirect
 */
var RequestRedirect = {
  FOLLOW: 'follow',
  ERROR: 'error',
  MANUAL: 'manual'
};


/**
 * @param {?ResponseBodyInit=} opt_body
 * @param {!ResponseInit=} opt_init
 * @constructor
 * @implements {Body}
 * @see https://fetch.spec.whatwg.org/#response
 */
function Response(opt_body, opt_init) {}

/** @return {!Response} */
Response.error = function() {};

/**
 * @param {string} url
 * @param {number=} opt_status
 * @return {!Response}
 */
Response.redirect = function(url, opt_status) {};

/** @inheritDoc */
Response.prototype.bodyUsed;

/** @inheritDoc */
Response.prototype.arrayBuffer = function() {};

/** @inheritDoc */
Response.prototype.blob = function() {};

/** @inheritDoc */
Response.prototype.formData = function() {};

/** @inheritDoc */
Response.prototype.json = function() {};

/** @inheritDoc */
Response.prototype.text = function() {};

/** @type {!ResponseType} */
Response.prototype.type;

/** @type {string} */
Response.prototype.url;

/** @type {boolean} */
Response.prototype.redirected;

/** @type {number} */
Response.prototype.status;

/** @type {boolean} */
Response.prototype.ok;

/** @type {string} */
Response.prototype.statusText;

/** @type {!Headers} */
Response.prototype.headers;

/** @type {?ReadableStream} */
Response.prototype.body;

/** @type {!Promise<!Headers>} */
Response.prototype.trailer;

/** @return {!Response} */
Response.prototype.clone = function() {};


/**
 * @record
 * @see https://fetch.spec.whatwg.org/#responseinit
 */
function ResponseInit() {};

/** @type {(undefined|number)} */
ResponseInit.prototype.status;

/** @type {(undefined|string)} */
ResponseInit.prototype.statusText;

/** @type {(undefined|!HeadersInit)} */
ResponseInit.prototype.headers;


/**
 * @enum {string}
 * @see https://fetch.spec.whatwg.org/#responsetype
 */
var ResponseType = {
  BASIC: 'basic',
  CORS: 'cors',
  DEFAULT: 'default',
  ERROR: 'error',
  OPAQUE: 'opaque',
  OPAQUEREDIRECT: 'opaqueredirect'
};


/**
 * @param {!RequestInfo} input
 * @param {!RequestInit=} opt_init
 * @return {!Promise<!Response>}
 * @see https://fetch.spec.whatwg.org/#fetch-method
 */
function fetch(input, opt_init) {}

/**
 * @param {!RequestInfo} input
 * @param {!RequestInit=} opt_init
 * @return {!Promise<!Response>}
 * @see https://fetch.spec.whatwg.org/#fetch-method
 */
Window.prototype.fetch = function(input, opt_init) {};

/**
 * @param {!RequestInfo} input
 * @param {!RequestInit=} opt_init
 * @return {!Promise<!Response>}
 * @see https://fetch.spec.whatwg.org/#fetch-method
 */
WorkerGlobalScope.prototype.fetch = function(input, opt_init) {};
