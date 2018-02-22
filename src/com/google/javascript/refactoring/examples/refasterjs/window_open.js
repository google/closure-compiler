/*
 * Copyright 2014 The Closure Compiler Authors.
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
 * @fileoverview RefasterJS templates for replacing calls to Window#open
 * with calls to the goog.dom.safe.openInWindow wrapper.
 *
 * For benign URLs, openInWindow simply calls the underlying API.
 * For malicious URLs (such as 'javascript:evil()') however,
 *  the URL is sanitized and replaced with an innocuous value.
 *
 * As such, using the safe wrapper prevents XSS vulnerabilities that would
 * otherwise be present if the URL is derived from untrusted input.
 *
 * openInWindow requires a compile-time constant window name. Calls which have a
 * variable window name are treated separately.
 */

goog.require('goog.dom.safe');
goog.require('goog.string.Const');

/**
 * goog.dom.safe.openInWindow uses the global window instance by
 * default.  Therefore, we can to Window#open on the
 * global window constant with a more readable alternative

/**
 * @param {?Window|undefined} win
 * @param {string} string_literal_url
 */
function do_not_change_stringLiteral(win, string_literal_url) {
  win.open(string_literal_url);
}

/**
 * @param {?Window|undefined} win
 * @param {string} string_literal_url
 * @param {string} name
 */
function do_not_change_stringLiteralName(win, string_literal_url, name) {
  win.open(string_literal_url, name);
}

/**
 * @param {?Window|undefined} win
 * @param {string} string_literal_url
 * @param {string} name
 * @param {string} spec
 */
function do_not_change_stringLiteralSpec(win, string_literal_url, name, spec) {
  win.open(string_literal_url, name, spec);
}

/**
 * +require {goog.dom.safe}
 * @param {string} url
 */
function before_windowGlobalOpen(url) {
  window.open(url);
}

/**
 * @param {string} url
 */
function after_windowGlobalOpen(url) {
  goog.dom.safe.openInWindow(url);
}


/**
 * +require {goog.dom.safe}
 * @param {?Window|undefined} win
 * @param {string} url
 */
function before_windowOpen(win, url) {
  win.open(url);
}

/**
 * @param {?Window|undefined} win
 * @param {string} url
 */
function after_windowOpen(win, url) {
  goog.dom.safe.openInWindow(url, win);
}

/**
 * Name must be a string literal.
 * TODO(bangert): Change API .
 */
/**
 * +require {goog.dom.safe}
 * +require {goog.string.Const}
 * @param {?Window|undefined} win
 * @param {string} url
 * @param {string} string_literal_name
 */
function before_windowOpenName(win, url, string_literal_name) {
  win.open(url, string_literal_name);
}

/**
 * @param {?Window|undefined} win
 * @param {string} url
 * @param {string} string_literal_name
 */
function after_windowOpenName(win, url, string_literal_name) {
  goog.dom.safe.openInWindow(
      url, win, goog.string.Const.from(string_literal_name));
}

/**
 * +require {goog.dom.safe}
 * +require {goog.string.Const}
 * @param {?Window|undefined} win
 * @param {string} url
 * @param {string} string_literal_name
 * @param {string} spec
 */
function before_windowOpenNameSpec(win, url, string_literal_name, spec) {
  win.open(url, string_literal_name, spec);
}

/**
 * @param {?Window|undefined} win
 * @param {string} url
 * @param {string} string_literal_name
 * @param {string} spec
 */
function after_windowOpenNameSpec(win, url, string_literal_name, spec) {
  goog.dom.safe.openInWindow(
      url, win, goog.string.Const.from(string_literal_name), spec);
}
