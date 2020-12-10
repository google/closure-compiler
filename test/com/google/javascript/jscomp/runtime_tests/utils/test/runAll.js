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
 * @fileoverview
 * Crawl over all ../*_test.html files and execute them in a JSDOM context,
 * fail the test if any do not succeed.
 */

const { JSDOM, VirtualConsole } = require('jsdom');
const { fail } = require('assert');
const chalk = require('chalk');
const fs = require('fs');
const FutureEvent = require('future-event');
const glob = require('glob');
const path = require('path');

/**
 * The absolute path of test.com.google.javascript.jscomp.runtime_tests.
 */
const RUNTIME_DIR = path.resolve(__dirname, '../..');

/**
 * All test files in the test.com.google.javascript.jscomp.runtime_tests.build
 * directory.
 */
const TEST_FILES = glob.sync(
    `${RUNTIME_DIR}/**/build/*_test.html`,
);

/**
 * Iterate over all found test files and execute them in JSDOM.
 */
describe('Runtime tests', () => {
  for (const testFile of TEST_FILES) {
    const logs = [];
    const passed = /PASSED/i;
    const failed = /FAILED/i;

    const allLogs = () => logs.join('\n');
    const chalkMsg = (msg) => {
      /**
       * Check whether or not this message is a PASSED or FAILED message.
       */
      const isPass = passed.test(msg);
      const isFail = failed.test(msg);

      /**
       * Highlight PASSED and FAILED in messages to help with accessibility.
       */
      return !(isPass || isFail)
        ? msg
        : msg
            .replace(passed, chalk.green('PASSED'))
            .replace(failed, chalk.red('FAILED'));
    };

    /**
     * Get filename, i.e. /path/to/file.ext -> file.ext
     */
    const testName = path.basename(testFile);

    /**
     * A promise that will resolve when JSDOM is done executing.
     */
    const testIsFinished = new FutureEvent();

    /**
     * A virtual console which will receive messages from JSDOM's `console.log`.
     */
    const virtualConsole = new VirtualConsole()
        .on('log', (msg) => {
          logs.push(chalkMsg(msg));
          if (/Tests complete/i.test(msg)) testIsFinished.ready(allLogs());
          else if (/Tests failed/i.test(msg)) testIsFinished.cancel(allLogs());
        });

    /**
     * Load the generated test file for consumption by the JSDOM environment.
     * This will be a raw HTML document.
     */
    const testDocument = fs.readFileSync(
        path.resolve(testFile),
        'utf-8',
    );

    it(`should pass test suite ${path.basename(testFile)}`, async () => {
      new JSDOM(testDocument, {
        /**
         * This does not actually run a server of any kind, it only informs the
         * DOM what to put in `window.location.origin`. By default, this is
         * `null`, which throws an "unsafe URL" error in the test suite. This is
         * purely for accurately mocking a browser for `goog.testing.testsuite`
         * tests, and any valid HTTPS URL will work here.
         */
        url: 'https://localhost:42',
        /**
         * This flag will allow the execution of `<script>` tags that are added
         * to the DOM after the `onload` event, i.e., those that are added by
         * `goog.module` and `goog.require`.
         */
        runScripts: 'dangerously',
        /**
         * Pipe `console.log` to our virtual console.
         */
        virtualConsole,
      });

      try {
        /**
         * Wait for test to finish, resume if no errors thrown.
         */
        await testIsFinished;
      } catch (e) {
        /**
         * Print error and fail if any occurred.
         */
        fail(`Failed test in suite ${testName}: \n${e}\n`);
      }
      /**
       * Otherwise, everything passed.
       */
      console.log(`Passed all tests in suite ${testName}`);
    });
  }
});
