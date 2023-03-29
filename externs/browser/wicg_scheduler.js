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

/** @type {!Scheduler} */
Window.prototype.scheduler;

/** @type {!Scheduler} */
WorkerGlobalScope.prototype.scheduler;
