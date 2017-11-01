/*
 * Copyright 2017 The Closure Compiler Authors.
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
 * @fileoverview Externs for Typeahead v0.11.1
 *
 * Requires jQuery 1.9+
 *
 * This a work in progress, please add to it / fix it as required.
 *
 * @see https://github.com/twitter/typeahead.js/blob/master/doc/jquery_typeahead.md
 * @externs
 */

/**
 * @typedef {{
 *     input: (undefined | string),
 *     hint: (undefined | string),
 *     menu: (undefined | string),
 *     dataset: (undefined | string),
 *     suggestion: (undefined | string),
 *     empty: (undefined | string),
 *     open: (undefined | string),
 *     cursor: (undefined | string),
 *     highlight: (undefined | string),}}
 */
var TypeaheadClassNames;

/**
 * @typedef {{
 *     highlight: (undefined | boolean),
 *     hint: (undefined | boolean),
 *     minLength: (undefined | number),
 *     classNames: (undefined | TypeaheadClassNames)}}
 */
var TypeaheadOptions;

/**
 * The typeahead "precompiled template". Usually takes an Object that includes
 * the query and produces an HTML string. Expected to return a template string
 * but works when an Element is returned. This behaviour is undocumented, a
 * request for clarification is outstanding here:
 *   https://github.com/twitter/typeahead.js/issues/1677
 * @typedef {function(?): (string | Element)}
 */
var TypeaheadTemplate;

/**
 * @typedef {{
 *     source: function(string, function(?), function(?)=),
 *     async: (undefined | boolean),
 *     name: (undefined | string),
 *     limit: (undefined | number),
 *     display: (undefined | function(?): string),
 *     templates: (undefined | {
 *         notFound: (undefined | string | TypeaheadTemplate),
 *         pending: (undefined | string | TypeaheadTemplate),
 *         header: (undefined | string | TypeaheadTemplate),
 *         footer: (undefined | string | TypeaheadTemplate),
 *         suggestion: (undefined | TypeaheadTemplate)
 *     })
 * }}
 */
var TypeaheadDataset;

/**
 * Really this function can be called:
 *   typeahead('val'): string // Get the input value.
 *   typeahead('val', <new value string>) // Set the input value.
 *   typeahead(TypeaheadOptions, <one or more TypeaheadDataset>) // Initialize.
 * But the following type compromises to create something valid.
 * @type {function((string | TypeaheadOptions), (string | TypeaheadDataset)=,
 *     ...TypeaheadDataset): string}
 */
jQuery.prototype.typeahead;
