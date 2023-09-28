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
 * @fileoverview The spec of the Attribution Reporting API.
 * @see https://github.com/WICG/attribution-reporting-api
 * @externs
 */

/**
 * @see https://wicg.github.io/attribution-reporting-api/#dictdef-attributionreportingrequestoptions
 * @record
 * @struct
 */
function AttributionReportingRequestOptions() {}

/** @type {boolean} */
AttributionReportingRequestOptions.prototype.eventSourceEligible;

/** @type {boolean} */
AttributionReportingRequestOptions.prototype.triggerEligible;

/**
 * @param {!AttributionReportingRequestOptions=} options The request options to
 *     pass eligibility signal for source or trigger registration via XHR.
 * @return {void}
 * @see https://wicg.github.io/attribution-reporting-api/#dom-xmlhttprequest-setattributionreporting
 */
XMLHttpRequest.prototype.setAttributionReporting = function(options) {};
