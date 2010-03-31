/*
 * Copyright 2009 Google Inc.
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
 * @fileoverview Definitions for all iPhone extensions. Created from:
 * http://developer.apple.com/webapps/docs/documentation/AppleApplications/Reference/SafariJSRef/
 *
 * @author agrieve@google.com (Andrew Grieve)
 */

/**
 * The Touch class represents a single touch on the surface. A touch is the
 * presence or movement of a finger that is part of a unique multi-touch
 * sequence.
 * @see http://developer.apple.com/webapps/docs/documentation/AppleApplications/Reference/SafariJSRef/Touch/Touch.html
 * @constructor
 */
function Touch() {}

/**
 * The x-coordinate of the touch's location relative to the window's viewport.
 * @type {number}
 */
Touch.prototype.clientX;

/**
 * The y-coordinate of the touch's location relative to the window's viewport.
 * @type {number}
 */
Touch.prototype.clientY;

/**
 * The unique identifier for this touch object.
 * @type {number}
 */
Touch.prototype.identifier;

/**
 * The x-coordinate of the touch's location in page coordinates.
 * @type {number}
 */
Touch.prototype.pageX;

/**
 * The y-coordinate of the touch's location in page coordinates.
 * @type {number}
 */
Touch.prototype.pageY;

/**
 * The x-coordinate of the touch's location in screen coordinates.
 * @type {number}
 */
Touch.prototype.screenX;

/**
 * The y-coordinate of the touch's location in screen coordinates.
 * @type {number}
 */
Touch.prototype.screenY;

/**
 * The target of this touch.
 * @type {EventTarget}
 */
Touch.prototype.target;

/**
 * The TouchList class is used to represent a collection of Touch objects.
 * @constructor
 */
function TouchList() {}

/**
 * The number of Touch objects in this TouchList object.
 * @type {number}
 */
TouchList.prototype.length;

/**
 * Returns the Touch object at the given index.
 * @param {number} index
 * @return {!Touch}
 */
TouchList.prototype.item = function(index) {};

/**
 * The TouchEvent class encapsulates information about a touch event.
 *
 * <p>The system continually sends TouchEvent objects to an application as
 * fingers touch and move across a surface. A touch event provides a snapshot of
 * all touches during a multi-touch sequence, most importantly the touches that
 * are new or have changed for a particular target. A multi-touch sequence
 * begins when a finger first touches the surface. Other fingers may
 * subsequently touch the surface, and all fingers may move across the surface.
 * The sequence ends when the last of these fingers is lifted from the surface.
 * An application receives touch event objects during each phase of any touch.
 * </p>
 *
 * <p>The different types of TouchEvent objects that can occur are:
 * <ul>
 *   <li>touchstart - Sent when a finger for a given event touches the surface.
 *   <li>touchmove - Sent when a given event moves on the surface.
 *   <li>touchend - Sent when a given event lifts from the surface.
 *   <li>touchcancel - Sent when the system cancels tracking for the touch.
 * </ul>
 * TouchEvent objects are combined together to form high-level GestureEvent
 * objects that are also sent during a multi-touch sequence.</p>
 *
 * @see http://developer.apple.com/webapps/docs/documentation/AppleApplications/Reference/SafariJSRef/TouchEvent/TouchEvent.html
 * @extends {UIEvent}
 * @constructor
 */
function TouchEvent() {}

/**
 * A collection of Touch objects representing all touches associated with this
 * target.
 * @type {TouchList}
 */
TouchEvent.prototype.touches;

/**
 * A collection of Touch objects representing all touches associated with this
 * target.
 * @type {TouchList}
 */
TouchEvent.prototype.targetTouches;

/**
 * A collection of Touch objects representing all touches that changed in this event.
 * @type {TouchList}
 */
TouchEvent.prototype.changedTouches;

/**
 * The distance between two fingers since the start of an event as a multiplier
 * of the initial distance. The initial value is 1.0. If less than 1.0, the
 * gesture is pinch close (to zoom out). If greater than 1.0, the gesture is
 * pinch open (to zoom in).
 * @type {number}
 */
TouchEvent.prototype.scale;

/**
 * The delta rotation since the start of an event, in degrees, where clockwise
 * is positive and counter-clockwise is negative. The initial value is 0.0.
 * @type {number}
 */
TouchEvent.prototype.rotation;

