/*
 * Copyright 2019 The Closure Compiler Authors.
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

goog.exportSymbol('jscomp.compile', JsRunnerMain.compile);

if (typeof module !== 'undefined') {
  goog.exportSymbol('exports', JsRunnerMain.compile, module);
} else {
  goog.exportSymbol('compile', JsRunnerMain.compile);
}


/**
 * @suppress {missingProperties}
 * @param {!Array<string>} jsFilePaths
 * @return {?Array<?>}
 */
JsRunnerMain.filesFromPaths = function(jsFilePaths) {
  if (!process || !process.version) {
    return null;
  }

  const /** !(typeof fs) */ _fs = require('fs');
  return jsFilePaths.map((path) => ({
                           path,  //
                           src: _fs.readFileSync(path, 'utf8'),
                         }));
};
