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
 * @see https://fetch.spec.whatwg.org/
 * @externs
 */

/**
 * @typedef {!Headers|!Array<!Array<string>>}
 */
var HeadersInit;

/**
 * @see https://fetch.spec.whatwg.org/#headers
 * @param {HeadersInit=} opt_headersInit
 * @constructor
 */
function Headers(opt_headersInit) {}

/**
 * @param {string} name
 * @param {string} value
 */
Headers.prototype.append = function(name, value) {};

/**
 * @param {string} name
 */
Headers.prototype.delete = function(name) {};

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

/**
 * @param {string} name
 * @param {string} value
 */
Headers.prototype.set = function(name, value) {};

/**
 * @typedef {!Blob|!FormData|string}
 */
var BodyInit;

/**
 * @see https://fetch.spec.whatwg.org/#request
 * @param {!RequestInfo} input
 * @param {RequestInit=} opt_init
 * @constructor
 */
function Request(input, opt_init) {}

/** @type {boolean} */
Request.prototype.bodyUsed;

/** @return {!Promise<!ArrayBuffer>} */
Request.prototype.arrayBuffer = function() {};

/** @return {!Promise<!Blob>} */
Request.prototype.blob = function() {};

/** @return {!Promise<!FormData>} */
Request.prototype.formData = function() {};

/** @return {!Promise<!Object>} */
Request.prototype.json = function() {};

/** @return {!Promise<string>} */
Request.prototype.text = function() {};

/** @type {string} */
Request.prototype.method;

/** @type {string} */
Request.prototype.url;

/** @type {!Headers} */
Request.prototype.headers;

/** @type {RequestContext} */
Request.prototype.context;

/** @type {string} */
Request.prototype.referrer;

/** @type {RequestMode} */
Request.prototype.mode;

/** @type {RequestCredentials} */
Request.prototype.credentials;

/** @type {RequestCache} */
Request.prototype.cache;

/** @return {!Request} */
Request.prototype.clone = function() {};

/** @typedef {!Request|string} */
var RequestInfo;

/**
 * @typedef {{
 *   method: (string|undefined),
 *   headers: (!HeadersInit|undefined),
 *   body: (!BodyInit|undefined),
 *   mode: (RequestMode|undefined),
 *   credentials: (RequestCredentials|undefined),
 *   cache: (RequestCache|undefined)
 * }}
 */
var RequestInit;

/**
 * @enum {string}
 */
var RequestContext = {
  AUDIO: 'audio',
  BEACON: 'beacon',
  CSPREPORT: 'cspreport',
  DOWNLOAD: 'download',
  EMBED: 'embed',
  EVENTSOURCE: 'eventsource',
  FAVICON: 'favicon',
  FETCH: 'fetch',
  FONT: 'font',
  FORM: 'form',
  FRAME: 'frame',
  HYPERLINK: 'hyperlink',
  IFRAME: 'iframe',
  IMAGE: 'image',
  IMAGESET: 'imageset',
  IMPORT: 'import',
  INTERNAL: 'internal',
  LOCATION: 'location',
  MANIFEST: 'manifest',
  OBJECT: 'object',
  PING: 'ping',
  PLUGIN: 'plugin',
  PREFETCH: 'prefetch',
  SCRIPT: 'script',
  SERVICEWORKER: 'serviceworker',
  SHAREDWORKER: 'sharedworker',
  SUBRESOURCE: 'subresource',
  STYLE: 'style',
  TRACK: 'track',
  VIDEO: 'video',
  WORKER: 'worker',
  XMLHTTPREQUEST: 'xmlhttprequest',
  XSLT: 'xslt'
};

/**
 * @enum {string}
 */
var RequestMode = {
  SAME_ORIGIN: 'same-origin',
  NO_CORS: 'no-cors',
  CORS: 'cors'
};

/**
 * @enum {string}
 */
var RequestCredentials = {
  OMIT: 'omit',
  SAME_ORIGIN: 'same-origin',
  INCLUDE: 'include'
};

/**
 * @enum {string}
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
 * @see https://fetch.spec.whatwg.org/#response
 * @param {BodyInit=} opt_body
 * @param {ResponseInit=} opt_init
 * @constructor
 */
function Response(opt_body, opt_init) {}

/** @return {Response} */
Response.error = function() {};

/**
 * @param {string} url
 * @param {number=} opt_status
 * @return {Response}
 */
Response.redirect = function(url, opt_status) {};

/** @type {boolean} */
Response.prototype.bodyUsed;

/** @type {!ReadableByteStream} */
Response.prototype.body;

/** @return {!Promise<!ArrayBuffer>} */
Response.prototype.arrayBuffer = function() {};

/** @return {!Promise<!Blob>} */
Response.prototype.blob = function() {};

/** @return {!Promise<!FormData>} */
Response.prototype.formData = function() {};

/** @return {!Promise<!Object>} */
Response.prototype.json = function() {};

/** @return {!Promise<string>} */
Response.prototype.text = function() {};

/** @type {ResponseType} */
Response.prototype.type;

/** @type {string} */
Response.prototype.url;

/** @type {number} */
Response.prototype.status;

/** @type {boolean} */
Response.prototype.ok;

/** @type {string} */
Response.prototype.statusText;

/** @type {!Headers} */
Response.prototype.headers;

/** @return {!Response} */
Response.prototype.clone = function() {};

/**
 * @typedef {{
 *   status : number,
 *   statusText: string,
 *   headers: !HeadersInit
 * }}
 */
var ResponseInit;

/**
 * @enum {string}
 */
var ResponseType = {
  BASIC: 'basic',
  CORS: 'cors',
  DEFAULT: 'default',
  ERROR: 'error',
  OPAQUE: 'opaque'
};

/**
 * @see https://fetch.spec.whatwg.org/#dom-global-fetch
 * @param {!RequestInfo} input
 * @param {RequestInit=} opt_init
 * @return {!Promise<!Response>}
 */
function fetch(input, opt_init) {}

/**
 * @see https://fetch.spec.whatwg.org/#dom-global-fetch
 * @param {!RequestInfo} input
 * @param {RequestInit=} opt_init
 * @return {!Promise<!Response>}
 */
Window.prototype.fetch = function(input, opt_init) {};


/**
 * @see https://fetch.spec.whatwg.org/#dom-global-fetch
 * @param {!RequestInfo} input
 * @param {RequestInit=} opt_init
 * @return {!Promise<!Response>}
 */
WorkerGlobalScope.prototype.fetch = function(input, opt_init) {};
