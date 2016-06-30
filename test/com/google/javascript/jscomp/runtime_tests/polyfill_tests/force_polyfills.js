/*
 * Copyright 2015 The Closure Compiler Authors.
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

var CLOSURE_DEFINED = {
  'jscomp.TRANSPILE': 'always',
};

/**
 * @fileoverview Deletes the browser's implementation of all ES6 libraries,
 * thus forcing the polyfills to be used unconditionally.
 */

delete Array.of;
delete Array.from;
delete Array.prototype.entries;
delete Array.prototype.keys;
delete Array.prototype.values;
delete Array.prototype.copyWithin;
delete Array.prototype.fill;
delete Array.prototype.find;
delete Array.prototype.findIndex;

delete Map;

delete Math.acosh;
delete Math.asinh;
delete Math.atanh;
delete Math.cbrt;
delete Math.clz32;
delete Math.cosh;
delete Math.expm1;
delete Math.hypot;
delete Math.imul;
delete Math.log10;
delete Math.log1p;
delete Math.log2;
delete Math.sign;
delete Math.sinh;
delete Math.tanh;
delete Math.trunc;

delete Number.EPSILON;
delete Number.MAX_SAFE_INTEGER;
delete Number.MIN_SAFE_INTEGER;
delete Number.isFinite;
delete Number.isInteger;
delete Number.isNaN;
delete Number.isSafeInteger;

delete Object.assign;
delete Object.getOwnPropertySymbols;
delete Object.is;

delete Set;

delete String.fromCodePoint;
delete String.prototype.repeat;
delete String.prototype.codePointAt;
delete String.prototype.includes;
delete String.prototype.startsWith;
delete String.prototype.endsWith;

delete Symbol;
