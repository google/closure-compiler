/*
 * Copyright 2016 The Closure Compiler Authors
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
 * @fileoverview Definitions of the Web Background Synchronization API.
 * 
 * Based on Draft Community Group Report, 2 August 2016
 *
 * @see https://wicg.github.io/BackgroundSync/spec/
 * @externs
 */


/**
 * @interface
 * @see https://wicg.github.io/BackgroundSync/spec/#syncmanager
 */
function SyncManager() {};

/**
 * @param {string} tag
 * @return {!Promise<void>}
 */
SyncManager.prototype.register = function(tag) {};

/** @return {!Promise<!Array<string>>} */
SyncManager.prototype.getTags = function() {};


/**
 * @param {string} type
 * @param {!SyncEventInit} init
 * @constructor
 * @extends {ExtendableEvent}
 * @see https://wicg.github.io/BackgroundSync/spec/#syncevent
 */
function SyncEvent(type, init) {};

/** @type {string} */
SyncEvent.prototype.tag;

/** @type {boolean} */
SyncEvent.prototype.lastChance;


/**
 * @record
 * @extends {ExtendableEventInit}
 * @see https://wicg.github.io/BackgroundSync/spec/#dictdef-synceventinit
 */
function SyncEventInit() {};

/** @type {string} */
SyncEventInit.prototype.tag;

/** @type {(undefined|boolean)} */
SyncEventInit.prototype.lastChance;


/**
 * @type {!SyncManager}
 * @see https://wicg.github.io/BackgroundSync/spec/#service-worker-registration-extensions
 */
ServiceWorkerRegistration.prototype.sync;


/**
 * @type {?function(!SyncEvent)}
 * @see https://wicg.github.io/BackgroundSync/spec/#sync-event
 */
ServiceWorkerGlobalScope.prototype.onsync;
