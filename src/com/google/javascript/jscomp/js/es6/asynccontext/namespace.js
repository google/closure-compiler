/*
 * Copyright 2024 The Closure Compiler Authors.
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

'require util/polyfill';

/**
 * We share state across all iframes via the Aᶜstate property on the top-level
 * window (or just the global object, if that's not writable).  It's an array
 * to avoid issues with different compilation units naming properties
 * differently.  The elements are as follows:
 * ```
 *   [0]: the current context, or undefined if variable hasn't been polyfilled
 *   [1]: the next variable index to assign (or undefined), to ensure global
 *        uniqueness
 * ```
 *
 * Note that this structure is an API guarantee and exposes a risk of version
 * skew.  If two binaries are on a page with different APIs for this array, they
 * will not work correctly.  We therefore cannot change this structure without
 * rolling out a compatibility layer to all accesses (specifically in
 * snapshot.js and variable.js) during a transition period.
 *
 * @typedef {!Array<?>}
 */
$jscomp.AsyncContextState;

/**
 * NOTE: This will always be set to non-null inside the polyfill.
 *
 * @type {!$jscomp.AsyncContextState}
 */
$jscomp.asyncContextState;

$jscomp.polyfill('AsyncContext', function(original) {

  /**
   * The name 'Aᶜstate' was chosen (somewhat in contrast to the 'ᵃᶜ' prefix
   * used elsewhere) so that it's (1) short, (2) very unlikely to conflict with
   * anything else, and (3) easy to find in the debugger because it should be
   * sorted near the top of the list of names - the latter is important because
   * of the difficulty for most people typing the superscript character.
   *
   * This function looks for that name on window.top, or else falls back on just
   * window if the former is missing or unwritabele.
   *
   * @param {!Object} target Target on which to write the state property
   * @return {!$jscomp.AsyncContextState|undefined} Truthy if it was successful
   */
  function findState(target) {
    try {
      return target && ($jscomp.asyncContextState =
                        (target['Aᶜstate'] || (target['Aᶜstate'] = [])));
    } catch (err) {
      // If window.top is not same-origin then this can fail with a security
      // exception.  Instead, just fallback to next target.
    }
    return undefined; // prevent "missing return" warning (compiler will elide)
  }
  findState($jscomp.global['top'])
      || findState($jscomp.global)
      || findState({});

  return original || {};
}, 'es_unstable', 'es3');
