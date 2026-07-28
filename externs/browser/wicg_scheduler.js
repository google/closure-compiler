/*
 * Copyright 2023 The Closure Compiler Authors
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
 * @fileoverview The current draft spec of Prioritized Task Scheduling API.
 * @see https://wicg.github.io/scheduling-apis/
 * @externs
 */

/**
 * @typedef {string}
 * @see https://wicg.github.io/scheduling-apis/#enumdef-taskpriority
 */
var TaskPriority;

/**
 * @typedef {function()}
 * @see https://wicg.github.io/scheduling-apis/#callbackdef-schedulerposttaskcallback
 */
var SchedulerPostTaskCallback;

/**
 * @typedef {{
 *   signal: (!AbortSignal|undefined),
 *   priority: (!TaskPriority|undefined),
 *   delay: (number|undefined)
 * }}
 * @see https://wicg.github.io/scheduling-apis/#dictdef-schedulerposttaskoptions
 */
var SchedulerPostTaskOptions;

/**
 * @record
 * @see https://wicg.github.io/scheduling-apis/#dictdef-taskcontrollerinit
 */
function TaskControllerInit() {}

/** @type {!TaskPriority|undefined} */
TaskControllerInit.prototype.priority;

/**
 * @record
 * @extends {EventInit}
 * @see https://wicg.github.io/scheduling-apis/#dictdef-taskprioritychangeeventinit
 */
function TaskPriorityChangeEventInit() {}

/** @type {!TaskPriority} */
TaskPriorityChangeEventInit.prototype.previousPriority;

/**
 * @record
 * @see https://wicg.github.io/scheduling-apis/#dictdef-tasksignalanyinit
 */
function TaskSignalAnyInit() {}

/** @type {(!TaskPriority|!TaskSignal|undefined)} */
TaskSignalAnyInit.prototype.priority;

/**
 * @constructor
 * @extends {AbortController}
 * @param {!TaskControllerInit=} opt_init
 * @see https://developer.mozilla.org/en-US/docs/Web/API/TaskController
 */
function TaskController(opt_init) {}

/**
 * @param {!TaskPriority} priority
 * @return {undefined}
 */
TaskController.prototype.setPriority = function(priority) {};

/**
 * @constructor
 * @extends {Event}
 * @param {string} type
 * @param {!TaskPriorityChangeEventInit} eventInitDict
 * @see https://developer.mozilla.org/en-US/docs/Web/API/TaskPriorityChangeEvent
 */
function TaskPriorityChangeEvent(type, eventInitDict) {}

/** @type {!TaskPriority} */
TaskPriorityChangeEvent.prototype.previousPriority;

/**
 * @constructor
 * @extends {AbortSignal}
 * @see https://developer.mozilla.org/en-US/docs/Web/API/TaskSignal
 */
function TaskSignal() {}

/** @type {?function(!TaskPriorityChangeEvent)} */
TaskSignal.prototype.onprioritychange;

/** @type {!TaskPriority} */
TaskSignal.prototype.priority;

/**
 * @param {!Array<!AbortSignal>} signals
 * @param {!TaskSignalAnyInit=} opt_init
 * @return {!TaskSignal}
 * @see https://developer.mozilla.org/en-US/docs/Web/API/TaskSignal/any_static
 */
TaskSignal.any = function(signals, opt_init) {};

/**
 * @see https://wicg.github.io/scheduling-apis/#sec-scheduler
 * @interface
 */
function Scheduler() {}

/**
 * @param {!SchedulerPostTaskCallback} callback
 * @param {!SchedulerPostTaskOptions=} options
 * @see https://developer.mozilla.org/en-US/docs/Web/API/Scheduler/postTask
 */
Scheduler.prototype.postTask = function(callback, options) {};

/**
 * @return {!Promise<undefined>}
 * @see https://developer.mozilla.org/en-US/docs/Web/API/Scheduler/yield
 */
Scheduler.prototype.yield = function() {};

/** @const {!Scheduler} */
Window.prototype.scheduler;

/** @const {!Scheduler} */
WorkerGlobalScope.prototype.scheduler;

/** @const {!Scheduler} */
var scheduler;
