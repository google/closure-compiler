/*
 * Copyright 2018 The Closure Compiler Authors
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
 * @fileoverview Definitions for objects in the Web Locks API. Details of the
 * API are at:
 * @see https://w3c.github.io/web-locks/
 *
 * @externs
 * @author ohsnapitscolin@google.com (Colin Dunn)
 */

/**
 * Possible values are "shared" and "exclusive".
 * @typedef {string}
 * @see https://w3c.github.io/web-locks/#enumdef-lockmode
 */
var LockMode;


/**
 * @interface
 * @see https://w3c.github.io/web-locks/#lock
 */
function Lock() {}

/**
 * @const {string}
 * @see https://w3c.github.io/web-locks/#lock-concept-name
 */
Lock.prototype.name;

/**
 * @const {!LockMode}
 * @see https://w3c.github.io/web-locks/#lock-concept-mode
 */
Lock.prototype.mode;


/**
 * @typedef {{
 *   name: string,
 *   mode: !LockMode,
 *   clientId: string
 * }}
 * @see https://w3c.github.io/web-locks/#dictdef-lockinfo
 */
var LockInfo;


/**
 * @typedef {{
 *   mode: (!LockMode|undefined),
 *   ifAvailable: (boolean|undefined),
 *   steal: (boolean|undefined),
 *   signal: (!AbortSignal|undefined)
 * }}
 * @see https://w3c.github.io/web-locks/#dictdef-lockoptions
 */
var LockOptions;


/**
 * @typedef {{
 *   held: !Array<!LockInfo>,
 *   pending: !Array<!LockInfo>
 * }}
 * @see https://w3c.github.io/web-locks/#dictdef-lockmanagersnapshot
 */
var LockManagerSnapshot;


/**
 * @typedef {(function(?Lock) : !Promise<*>)}
 * @see https://w3c.github.io/web-locks/#callbackdef-lockgrantedcallback
 */
var LockGrantedCallback;


/**
 * @interface
 * @see https://w3c.github.io/web-locks/#lockmanager
 */
function LockManager() {}

/**
 * @see https://w3c.github.io/web-locks/#dom-lockmanager-request
 * @param {string} name
 * @param {(!LockOptions|!LockGrantedCallback)} optionsOrCallback
 * @param {!LockGrantedCallback=} callback
 * @return {!Promise<*>}
 */
LockManager.prototype.request = function(name, optionsOrCallback, callback) {};

/**
 * @see https://w3c.github.io/web-locks/#dom-lockmanager-query
 * @return {!Promise<!LockManagerSnapshot>}
 */
LockManager.prototype.query = function() {};


/**
 * @type {!LockManager}
 * @see https://w3c.github.io/web-locks/#lockmanager
 */
Navigator.prototype.locks;


/**
 * @type {!LockManager}
 * @see https://w3c.github.io/web-locks/#lockmanager
 */
WorkerNavigator.prototype.locks;
