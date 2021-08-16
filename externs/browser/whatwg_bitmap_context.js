/*
 * Copyright 2021 The Closure Compiler Authors
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
 * @fileoverview Definitions for ImageBitmapRenderingContext.
 *     https://html.spec.whatwg.org/multipage/canvas.html#the-imagebitmaprenderingcontext-interface
 * @externs
 */

/**
 * @constructor
 * @see https://html.spec.whatwg.org/multipage/canvas.html#the-imagebitmaprenderingcontext-interface
 */
function ImageBitmapRenderingContext() {}

/**
 * @param {?ImageBitmap} bitmap
 * @return {undefined}
 */
ImageBitmapRenderingContext.prototype.transferFromImageBitmap = function(
    bitmap) {};

/** @const {!HTMLCanvasElement|!OffscreenCanvas} */
ImageBitmapRenderingContext.prototype.canvas;
