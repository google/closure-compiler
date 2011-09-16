/*
 * Copyright 2011 Google Inc.
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
 * @fileoverview Definitions for W3C's Navigation Timing specification.
 *
 * Created from
 * http://dvcs.w3.org/hg/webperf/raw-file/tip/specs/NavigationTiming/Overview.html
 *
 * @externs
 */

/** @constructor */
function PerformanceTiming() {}
/** @type {number} */ PerformanceTiming.prototype.navigationStart;
/** @type {number} */ PerformanceTiming.prototype.unloadEventStart;
/** @type {number} */ PerformanceTiming.prototype.unloadEventEnd;
/** @type {number} */ PerformanceTiming.prototype.redirectStart;
/** @type {number} */ PerformanceTiming.prototype.redirectEnd;
/** @type {number} */ PerformanceTiming.prototype.fetchStart;
/** @type {number} */ PerformanceTiming.prototype.domainLookupStart;
/** @type {number} */ PerformanceTiming.prototype.domainLookupEnd;
/** @type {number} */ PerformanceTiming.prototype.connectStart;
/** @type {number} */ PerformanceTiming.prototype.connectEnd;
/** @type {number} */ PerformanceTiming.prototype.secureConnectionStart;
/** @type {number} */ PerformanceTiming.prototype.requestStart;
/** @type {number} */ PerformanceTiming.prototype.responseStart;
/** @type {number} */ PerformanceTiming.prototype.responseEnd;
/** @type {number} */ PerformanceTiming.prototype.domLoading;
/** @type {number} */ PerformanceTiming.prototype.domInteractive;
/** @type {number} */ PerformanceTiming.prototype.domContentLoadedEventStart;
/** @type {number} */ PerformanceTiming.prototype.domContentLoadedEventEnd;
/** @type {number} */ PerformanceTiming.prototype.domComplete;
/** @type {number} */ PerformanceTiming.prototype.loadEventStart;
/** @type {number} */ PerformanceTiming.prototype.loadEventEnd;

/** @constructor */
function PerformanceNavigation() {}
/** @type {number} */ PerformanceNavigation.prototype.TYPE_NAVIGATE = 0;
/** @type {number} */ PerformanceNavigation.prototype.TYPE_RELOAD = 1;
/** @type {number} */ PerformanceNavigation.prototype.TYPE_BACK_FORWARD = 2;
/** @type {number} */ PerformanceNavigation.prototype.TYPE_RESERVED = 255;
/** @type {number} */ PerformanceNavigation.prototype.type;
/** @type {number} */ PerformanceNavigation.prototype.redirectCount;

/** @constructor */
function Performance() {}
/** @type {PerformanceTiming} */ Performance.prototype.timing;
/** @type {PerformanceNavigation} */ Performance.prototype.navigation;

/** @type {Performance} */
Window.prototype.performance;
