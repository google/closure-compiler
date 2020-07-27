/**
 * @license
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
 * @file
 * Crawl over all test/com/google/.../runtime_tests/*_test.html files and
 * execute them in a JSDOM context, fail the test if any do not succeed.
 */

const { JSDOM, VirtualConsole } = require('jsdom');
const fs = require('fs');
const path = require('path');
const FutureEvent = require('future-event');
const { fail } = require('assert');
const glob = require('glob');

const TEST_FILES = glob.sync(path.resolve(
  __dirname,
  '../../test/com/google/javascript/jscomp/runtime_tests/**/build/*_test.html'
));

describe('Runtime tests', () => {
  for (const TEST_URL of TEST_FILES) {
    const logs = [];
    const allLogs = () => logs.join('\n');
    
    const TEST_NAME = path.basename(TEST_URL);
    const TestIsFinished = new FutureEvent();
    const virtualConsole = new VirtualConsole()
      .on('log', (msg) => {
        logs.push(msg);
        if (/Tests complete/i.test(msg)) TestIsFinished.ready(allLogs());
        else if (/Tests failed/i.test(msg)) TestIsFinished.cancel(allLogs());
      });

    const TEST_DOC = fs.readFileSync(
      path.resolve(
        __dirname,
        '../../',
        TEST_URL
      ), 
      'utf-8'
    );

    it(`should pass test suite ${path.basename(TEST_URL)}`, async () => {
      const { window } = new JSDOM(TEST_DOC, {
        url: 'https://localhost:42',
        runScripts: 'dangerously',
        virtualConsole
      });

      try {
        await TestIsFinished;
      } catch(e) {
        fail(`Failed test in suite ${TEST_NAME}: \n\n${e}`);
      }
      console.log(`Passed all tests in suite ${TEST_NAME}`);
    });
  }
});