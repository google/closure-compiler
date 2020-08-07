/**
 * @license Apache-2.0
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
 * @fileoverview
 * Crawl over all test/com/google/.../runtime_tests/*_test.html files and
 * execute them in a JSDOM context, fail the test if any do not succeed.
 */

const { JSDOM, VirtualConsole } = require('jsdom');
const { fail } = require('assert');
const chalk = require('chalk');
const fs = require('fs');
const FutureEvent = require('future-event');
const glob = require('glob');
const path = require('path');

const TEST_FILES = glob.sync(path.resolve(
  __dirname,
  '../../test/com/google/javascript/jscomp/runtime_tests/**/build/*_test.html'
));

describe('Runtime tests', () => {
  for (let testUrl of TEST_FILES) {
    const logs = [];
    const passed = /PASSED/i;
    const failed = /FAILED/i;

    const allLogs = () => logs.join('\n');
    const chalkMsg = (msg) => {
      const isPass = passed.test(msg);
      const isFail = failed.test(msg);
      
      if (isPass || isFail) {
        return msg.replace(
          passed, chalk.green('PASSED')
        ).replace(
          failed, chalk.red('FAILED')
        );
      }
      else return msg;
    }
    
    const testName = path.basename(testUrl);
    const TestIsFinished = new FutureEvent();
    const virtualConsole = new VirtualConsole()
      .on('log', (msg) => {
        logs.push(chalkMsg(msg));
        if (/Tests complete/i.test(msg)) TestIsFinished.ready(allLogs());
        else if (/Tests failed/i.test(msg)) TestIsFinished.cancel(allLogs());
      });

    const TEST_DOC = fs.readFileSync(
      path.resolve(
        __dirname,
        '../../',
        testUrl
      ), 
      'utf-8'
    );

    it(`should pass test suite ${path.basename(testUrl)}`, async () => {
      const { window } = new JSDOM(TEST_DOC, {
        /**
         * This does not actually run a server of any kind, it only informs the
         * DOM what to put in `window.location.origin`. By default, this is
         * `null`, and any non-HTTPS URL field here will throw an error in the
         * test suite
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
        virtualConsole
      });

      try {
        await TestIsFinished;
      } catch (e) {
        // Format the error a bit: first line red, rest unformatted.
        const errorLines = e.toString().split('\n');
        let formattedError;
        for (let i = 0; i < errorLines.length; i++) {
          formattedError += i === 0 
            ? chalk.red(errorLines[i])
            : errorLines[i];
        }
        fail(`Failed test in suite ${testName}: \n${formattedError}\n`);
      }
      console.log(`Passed all tests in suite ${testName}`);
    });
  }
});