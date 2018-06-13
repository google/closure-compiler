/*
 * Copyright 2018 The Closure Compiler Authors.
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
 * @fileoverview
 * Node integration test for the js runner
 *
 * This is currently written to require no test framework dependencies
 */
const jscomp = require('../target/closure-compiler-gwt-1.0-SNAPSHOT/jscomp/jscomp.js');
const result = jscomp({
  compilationLevel: 'SIMPLE',
  warningLevel: 'VERBOSE'
}, [{
  src: 'alert("hello world");',
  path: 'input0'
}]);
const warnings = Array.prototype.slice.call(result.warnings);
const errors = Array.prototype.slice.call(result.errors);

if (warnings.length > 0 || errors.length > 0) {
  warnings.concat(errors).forEach(err => {
    console.error(err);
  });
  process.exit(1);
}

if (result.compiledFiles.length === 1 && /alert/.test(result.compiledFiles[0].src)) {
  console.log('GWT version successfully ran.');
} else {
  console.error('GWT version produced no output.');
  process.exit(1);
}
