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
const path = require('path');

/*
 * Known resource roots. Resources outside these roots will throw an Error.
 * Files found here will have the root removed, and the value prefixed (e.g.
 * "javascript/externs/foo.js" => "externs/foo.js".
 */
const roots = {
  'com/google/javascript/jscomp': '',
  'com/google/javascript': '',
  'javascript/externs': 'externs',
};

/**
 * Filter filenames that should not be included.
 *
 * @param {string} filename
 * @return {boolean} Whether to include the file.
 */
function filter(filename) {
  // TODO(thorogood): This file contains Google-internal code that we can't
  // strip out right now. Remove this when we can build resources.json in
  // open-source land (b/30904071).
  return !filename.endsWith('externs/chrome.js');
}

/**
 * Finds a matching root for the given filename, and flattens into a common
 * namespace.
 *
 * @param {string} filename
 * @return {string} Local filename for use in the resources JSON.
 */
function relativize(filename) {
  for (let root in roots) {
    const index = filename.indexOf(root + '/');
    if (index === -1) {
      continue;
    } else if (index !== 0 && filename[index-1] !== '/') {
      continue;  // not at start, or after a seperator
    }
    const tail = filename.substr(index + root.length + 1);  // remove root
    return path.join(roots[root], tail);
  }
  throw new Error('file not matched by build_resources.js known roots: '
      + filename);
}

const reads = process.argv.slice(2).filter(filter).map(filename => {
  const local = relativize(filename);
  return new Promise((fulfill, reject) =>
    fs.readFile(filename, 'utf8', (err, data) => {
      if (err) {
        reject(err);
      } else {
        fulfill({file: local, contents: data});
      }
    }));
});

Promise.all(reads).then(
  results => {
    // Builds an object in the form {path: {name: contents}}. This allows quick
    // enumeration over contents of a directory.
    const files = {};
    for (let result of results) {
      files[result.file] = result.contents;
    }
    console.log(JSON.stringify(files));
  }).catch(err => {
    console.error(err.message);
    process.exit(1);
  });
