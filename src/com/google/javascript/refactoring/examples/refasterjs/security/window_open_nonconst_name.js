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
 * @fileoverview RefasterJS templates for replacing calls to
 * Window#open that have a non-constant window name with invalid calls
 * to the goog.dom.safe.openInWindow wrapper.
 *
 * The safe wrapper only allows compile-time constant values, so we
 * handle these separately.
 */

goog.require('goog.dom.safe');
goog.require('goog.string.Const');

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
 * @param {?Window|undefined} win
 * @param {string} url
 * @param {string} string_literal_name
 */
function do_not_change_windowOpenStringLiteralName(
    win, url, string_literal_name) {
  win.open(url, string_literal_name);
}

/**
 * @param {?Window|undefined} win
 * @param {string} url
 * @param {string} string_literal_name
 * @param {string} spec
 */
function do_not_change_windowOpenStringLiteralNameSpec(
    win, url, string_literal_name, spec) {
  win.open(url, string_literal_name, spec);
}

/**
 * +require {goog.dom.safe}
 * +require {goog.string.Const}
 * @param {?Window|undefined} win
 * @param {string} url
 * @param {string} name
 */
function before_windowOpenVariableName(win, url, name) {
  win.open(url, name);
}

/**
 * @param {?Window|undefined} win
 * @param {string} url
 * @param {string} name
 */
function after_windowOpenVariableName(win, url, name) {
  goog.dom.safe.openInWindow(url, win, goog.string.Const.from('FIXME' + name));
}

/**
 * +require {goog.dom.safe}
 * +require {goog.string.Const}
 * @param {?Window|undefined} win
 * @param {string} url
 * @param {string} name
 * @param {string} spec
 */
function before_windowOpenVariableNameSpec(win, url, name, spec) {
  win.open(url, name, spec);
}

/**
 * @param {?Window|undefined} win
 * @param {string} url
 * @param {string} name
 * @param {string} spec
 */
function after_windowOpenVariableNameSpec(win, url, name, spec) {
  goog.dom.safe.openInWindow(
      url, win, goog.string.Const.from('FIXME' + name), spec);
}
