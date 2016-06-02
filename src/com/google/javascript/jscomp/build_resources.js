#!/usr/bin/env node

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

'use strict';

const fs = require('fs');

/**
 * @param {string} filename
 * @return {string} The filename relative to com/google/javascript/jscomp.
 */
function relativize(filename) {
  return filename.replace(/.*com\/google\/javascript\/jscomp\//, '');
}

const reads = process.argv.slice(2).map(filename =>
  new Promise((fulfill, reject) =>
    fs.readFile(filename, 'utf8', (err, data) => {
      if (err) {
        reject(err);
      } else {
        fulfill({file: filename, contents: data});
      }
    })));

Promise.all(reads).then(
  results => {
    const files = {};
    for (let result of results) {
      files[relativize(result.file)] = result.contents;
    }
    console.log(JSON.stringify(files));
  }, err => {
    console.log(err.message);
    process.exit(1);
  });
