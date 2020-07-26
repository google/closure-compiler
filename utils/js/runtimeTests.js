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
    const TEST_NAME = path.basename(TEST_URL);
    const logs = [];
    const logAll = () => console.log(logs.join('\n'));
    const TestIsFinished = new FutureEvent();
    const virtualConsole = new VirtualConsole()
      .on('log', (msg) => {
        logs.push(msg);
        if (/Tests complete/i.test(msg)) TestIsFinished.ready();
        else if (/Tests failed/i.test(msg)) TestIsFinished.cancel();
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
        console.log(`Executing tests in suite ${TEST_NAME}`);
        await TestIsFinished;
      } catch(e) {
        logAll();
        fail(`Failed test in suite ${TEST_NAME}: \n\n${e}`);
      }

    });
  }
});