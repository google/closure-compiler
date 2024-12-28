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
 * https://wicg.github.io/web-locks/
 *
 * @externs
 * @author ohsnapitscolin@google.com (Colin Dunn)
 */

/**
 * Possible values are "shared" and "exclusive".
 * @typedef {string}
 * [Web Locks API Spec](https://wicg.github.io/web-locks/#idl-index)
 */
var LockMode;


/**
 * @interface
 * [Web Locks API Spec](https://wicg.github.io/web-locks/#idl-index)
 */
function Lock() {}

/** @const {string} */
Lock.prototype.name;

/** @const {!LockMode} */
Lock.prototype.mode;


/**
 * @typedef {{
 *   name: string,
 *   mode: !LockMode,
 *   clientId: string
 * }}
 * [Web Locks API Spec](https://wicg.github.io/web-locks/#idl-index)
 */
var LockInfo;


/**
 * @typedef {{
 *   mode: (!LockMode|undefined),
 *   ifAvailable: (boolean|undefined),
 *   steal: (boolean|undefined),
 *   signal: (!AbortSignal|undefined)
 * }}
 * [Web Locks API Spec](https://wicg.github.io/web-locks/#idl-index)
 */
var LockOptions;


/**
 * @typedef {{
 *   held: !Array<!LockInfo>,
 *   pending: !Array<!LockInfo>
 * }}
 * [Web Locks API Spec](https://wicg.github.io/web-locks/#idl-index)
 */
var LockManagerSnapshot;


/**
 * @typedef {(function(?Lock) : !Promise<*>)}
 * [Web Locks API Spec](https://wicg.github.io/web-locks/#idl-index)
 */
var LockGrantedCallback;


/**
 * @interface
 * [Web Locks API Spec](https://wicg.github.io/web-locks/#idl-index)
 */
function LockManager() {}
/**
 * @param {string} name
 * @param {(!LockOptions|!LockGrantedCallback)} optionsOrCallback
 * @param {!LockGrantedCallback=} callback
 * @return {!Promise<*>}
 */
LockManager.prototype.request = function(name, optionsOrCallback, callback) {};

/**
 * @return {!Promise<!LockManagerSnapshot>}
 */
LockManager.prototype.query = function() {};


/**
 * @type {!LockManager}
 * [Web Locks API Spec](https://wicg.github.io/web-locks/#idl-index)
 */
Navigator.prototype.locks;


/**
 * @type {!LockManager}
 * [Web Locks API Spec](https://wicg.github.io/web-locks/#idl-index)
 */
WorkerNavigator.prototype.locks;
