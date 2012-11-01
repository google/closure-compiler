/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

// unique.js: read the contents of a file and print out the unique lines

defineClass("File")

// "arguments[0]" refers to the first argument at the command line to the
// script, if present. If not present, "arguments[0]" will be undefined,
// which will cause f to read from System.in.
var f = new File(arguments[0]);
var o = {}
var line;
while ((line = f.readLine()) != null) {
	// Use JavaScript objects' inherent nature as an associative
	// array to provide uniqueness
	o[line] = true;
}
for (i in o) {
	print(i);
}
