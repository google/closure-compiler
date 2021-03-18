/*
 * Copyright 2008 The Closure Compiler Authors
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
 * @fileoverview The spec of FLoC interface.
 * @see https://github.com/WICG/floc
 * @externs
 */

/**
 * @see https://wicg.github.io/floc/#interest-cohort-section
 * @record
 * @struct
 */
function InterestCohort() {}

/** @type {string} */
InterestCohort.prototype.id;

/** @type {string} */
InterestCohort.prototype.version;


/**
 * @return {!Promise<!InterestCohort>}
 * @see https://wicg.github.io/floc/#the-api
 */
Document.prototype.interestCohort = function() {};
