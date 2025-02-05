/*
 * Copyright 2020 The Closure Compiler Authors.
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
 * @fileoverview Assorted runtime logic code relied on by transpilation.
 * The JSCompiler transpilation passes sometimes rely on utility functions here,
 * referencing such functions in the transpiled output. Normally, the compiler
 * itself is responsible for adding the specific functions needed; this file is
 * for the case where transpilation is run with library injection disabled, and
 * a separate bundled utility library is necessary.
 *
 * @suppress {uselessCode}
 */
'require es6/async_generator_wrapper';
'require es6/execute_async_generator';
'require es6/generator_engine';
'require es6/util/createtemplatetagfirstarg';
'require es6/util/arrayfromiterable';
'require es6/util/arrayfromiterator';
'require es6/util/inherits';
'require es6/util/iteratorfromarray';
'require es6/util/makeiterator';
'require es6/util/restarguments';
