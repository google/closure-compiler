/*
 * Copyright 2024 The Closure Compiler Authors
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
  * @fileoverview Definitions for W3C's Web Locks API
  * @see https://www.w3.org/TR/web-locks/
  * @see https://www.w3.org/TR/2023/WD-web-locks-20230105/
  *
  * @externs
  * @author kuba@valentine.dev (Kuba Paczyński)
  */


/**
 * @interface
 * @see https://www.w3.org/TR/web-locks/#lockmanager
 */
var LockManager = function() {};


/**
 * @param  {string}                            name
 * @param  {!LockOptions|!LockGrantedCallback} options
 * @param  {!LockGrantedCallback|undefined=}   callback
 * @return {!Promise<*>}
 */
LockManager.prototype.request = function(name, options, callback) {};


/**
 * @see https://www.w3.org/TR/web-locks/#dom-lockmanager-query
 * @return {!Promise<!LockManagerSnapshot>}
 */
LockManager.prototype.query = function() {};


/**
 * @interface
 * @struct
 * @see https://www.w3.org/TR/web-locks/#lock
 */
var Lock = function() {};


/**
 * @see https://www.w3.org/TR/web-locks/#lock-concept-name
 * @type {string}
 */
Lock.prototype.name;


/**
 * @see https://www.w3.org/TR/web-locks/#lock-concept-mode
 * @type {!LockMode}
 */
Lock.prototype.mode;


/**
 * @typedef {function(!Lock): !Promise<*>}
 * @see https://www.w3.org/TR/web-locks/#callbackdef-lockgrantedcallback
 */
var LockGrantedCallback;


/**
 * Enum of:
 * 'shared',
 * 'exclusive',
 * @typedef {string}
 * @see https://www.w3.org/TR/web-locks/#enumdef-lockmode
 */
var LockMode;


/**
 * @typedef {{
 *   mode        : (!LockMode|undefined),
 *   ifAvailable : (boolean|undefined),
 *   steal       : (boolean|undefined),
 *   signal      : (!AbortSignal|undefined)
 * }}
 * @see https://www.w3.org/TR/web-locks/#dictdef-lockoptions
 */
var LockOptions;


/**
 * @typedef {{
 *   mode     : !LockMode,
 *   name     : string,
 *   clientId : string,
 * }}
 * @see https://www.w3.org/TR/web-locks/#dictdef-lockinfo
 */
var LockInfo;


/**
 * @typedef {{
 *   held    : !Array<!LockInfo>,
 *   pending : !Array<!LockInfo>
 * }}
 * @see https://www.w3.org/TR/web-locks/#dictdef-lockmanagersnapshot
 */
var LockManagerSnapshot;


/**
 * @type {!LockManager}
 * @see https://w3c.github.io/keyboard-lock/#API
 */
Navigator.prototype.locks;
