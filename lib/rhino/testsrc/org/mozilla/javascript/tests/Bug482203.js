/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

var __terminate_interpreter__ = new Continuation();
var c;

function fib(x) {
    c = getContinuation();
    if(c != null) {
    	this.__terminate_interpreter__(null);
    }
    return x < 2 ? 1 : (fib(x-1) + fib(x-2));
}

function getContinuation() {
	return new Continuation();
}

var result = fib(3);