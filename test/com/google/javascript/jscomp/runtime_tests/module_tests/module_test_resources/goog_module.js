/*
 * Copyright 2016 The Closure Compiler Authors.
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
 * @fileoverview A file exporting properties as a goog.module().
 */
goog.module('test.goog.module');
goog.module.declareLegacyNamespace();
// This file is imported from an ES6 module and the ES6 module handling at the
// moment can only successfully dep upon on legacy namespaces (or other ES6
// modules of course).

/** @type {number} */
exports.foo = 23;
/** @type {number} */
exports.bar = 42;
