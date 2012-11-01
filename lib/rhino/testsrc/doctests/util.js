/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

function expectTypeError(code) { expectError(code, TypeError); }

function expectError(code, error) {
  try {
    code();
    throw (code.toSource() + ' should have thrown a '+error);
  } catch (e if e instanceof error) {
    // all good
  }
}
