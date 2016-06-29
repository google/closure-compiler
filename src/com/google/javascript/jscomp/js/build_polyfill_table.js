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
 * Provides an ordering to ensure lower-versioned polyfills don't
 * depend on higher versions.
 */
const ORDER = ['es3', 'es5', 'es6-impl', 'es6'];

/**
 * Prints to stderr and exits.
 * @param {string} message
 */
function fail(message) {
  console.error(message);
  process.exit(1);
}

/**
 * Builds up a table of polyfills.
 */
class PolyfillTable {
  constructor() {
    /** @const {!Map<string, !Array<string>>} */
    this.symbolToFile = new Map();
    /** @const {!Map<string, !Set<string>>} */
    this.deps = new Map();
    /** @const {!Map<string, string>} */
    this.versions = new Map();
    /** @const {!Array<!Array<string>>} */
    this.rows = [];
  }

  /**
   * Returns a shim for $jscomp.polyfill.
   * @param {string} lib Library currently being scanned.
   * @return {function(string, ?Function, string, string)}
   */
  polyfill(lib) {
    return (polyfill, impl, fromLang, toLang) => {
      this.symbolToFile.set(polyfill, this.symbolToFile.get(polyfill) || []);
      this.symbolToFile.get(polyfill).push(lib);
      const row = [polyfill, fromLang, toLang];
      if (impl) {
        row.push(lib);
        this.versions.set(lib, maxVersion(this.versions.get(lib), toLang));
      }
      this.rows.push(row);
    };
  }

  /**
   * Reads a JS file and adds it to the table.
   * @param {string} lib Name of the library.
   * @param {string} data Contents of the file.
   */
  readFile(lib, data) {
    // Look for 'require' directives and add it to the dependency map.
    const deps = new Set();
    this.deps.set(lib, deps);
    const re = /'require ([^']+)'/g;
    let match;
    while (match = re.exec(data)) {
      match[1].split(' ').forEach(dep => deps.add(dep));
    }
    // Now run the file.
    try {
      new Function('$jscomp', data)({
        global: global,
        polyfill: this.polyfill(lib, table),
      });
    } catch (err) {
      throw new Error('Failed to parse file: ' + lib + ': ' + err);
    }
  }

  /**
   * Concatenates the table into a string.  Throws an error if
   * there are any symbols provided by multiple files.
   * @return {string}
   */
  build() {
    const errors = new Set();
    try {
      // First check for duplicate provided symbols.
      for (const entry of this.symbolToFile.entries()) {
        if (entry[1].length != 1) {
          errors.add(
              `ERROR - ${entry[0]} provided by multiple files:${
                  entry[1].map(f => '\n    ' + f).join('')}`);
        }
      }
      // Next ensure all deps have nonincreasing versions.
      checkDeps(errors, this.deps, this.versions);
      // If there are any errors, we should fail; otherwise concatenate.
    } catch (err) {
      errors.add('ERROR - uncaught exception: ' + err.stack);
    }
    if (errors.size) {
      fail(Array.from(errors).join('\n\n'));
    }
    return this.rows.sort().map(row => row.join(' ')).join('\n');
  }
}

/**
 * Checks dependencies for the following issues:
 *   (1) cyclic dependencies
 *   (2) missing dependencies
 *   (3) version mismatches
 * @param {!Set<string>} errors
 * @param {!Map<string, !Set<string>>} deps
 * @param {!Map<string, string>} versions
 */
function checkDeps(errors, deps, versions) {
  for (const file of deps.keys()) {
    const seen = new Set([file]);
    const queue = [file];
    const version = versions.get(file);
    while (queue.length) {
      const next = queue.shift();
      for (const dep of deps.get(next) || []) {
        if (dep == file) errors.add('ERROR - Cyclic dependency:\n    ' + dep);
        if (seen.has(dep)) continue;
        seen.add(dep);
        queue.push(dep);
        if (!deps.has(dep)) {
          errors.add(
              'ERROR - missing dependency:\n    ' + dep +
              ' required from\n    ' + file);
        }
        const depVersion = versions.get(dep);
        if (version && maxVersion(depVersion, version) != version) {
          errors.add(
              'ERROR - lower version depends on higher version:\n    ' +
              version + ': ' + file + '\n    ' + depVersion + ': ' + dep);
        }
      }
    }
  }
}

/**
 * Returns the higher order of the given versions.
 * @param {string} version1
 * @param {string} version2
 * @return {string} The max version.
 */
function maxVersion(version1, version2) {
  return ORDER[Math.max(ORDER.indexOf(version1), ORDER.indexOf(version2))];
}

const table = new PolyfillTable();

const reads = process.argv.slice(2).map(filename =>
  new Promise((fulfill, reject) =>
    fs.readFile(filename, 'utf8', (err, data) => {
      try {
        if (err) {
          reject(err);
        } else {
          const lib = filename.replace(/^.*?\/js\/|\.js$/g, '');
          table.readFile(lib, data);
          fulfill('');
        }
      } catch (err) {
        reject(err);
      }
    })));

Promise.all(reads).then(
    success => console.log(table.build()),
    failure => fail(failure.stack));
