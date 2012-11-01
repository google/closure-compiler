/* -*- tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 8 -*-
 *      
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
Implementing the interface java.util.Enumeration passing the object
with JavaScript implementation directly to the constructor.
This is a shorthand for JavaAdapter constructor:

elements = new JavaAdapter(java.util.Enumeration, {
        index: 0, 
        elements: array,
	hasMoreElements: function ...
        nextElement: function ...
		});
 */

// an array to enumerate.
var array = [0, 1, 2];

// create an array enumeration.
var elements = new java.util.Enumeration({
        index: 0,
        elements: array,
        hasMoreElements: function() {
                return (this.index < this.elements.length);
	},      
        nextElement: function() {
                return this.elements[this.index++];
	}
    });

// now print out the array by enumerating through the Enumeration
while (elements.hasMoreElements())
	print(elements.nextElement());
