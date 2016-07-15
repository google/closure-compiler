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
 * @fileoverview Rolls together all ES6 Reflect polyfills.
 */
'require es6/reflect/apply es6/reflect/construct es6/reflect/defineproperty';
'require es6/reflect/deleteproperty es6/reflect/get';
'require es6/reflect/getownpropertydescriptor es6/reflect/getprototypeof';
'require es6/reflect/has es6/reflect/isextensible es6/reflect/ownkeys';
'require es6/reflect/preventextensions es6/reflect/set';
'require es6/reflect/setprototypeof';
