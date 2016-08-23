# Running the Promises/A+ Compliance Test Suite

The closure-compiler Promises implementation used when transpiling & polyfilling
ES6-level JavaScript code back to ES5 or ES3 has been tested against the
Promises/A+ Compliance Test Suite. The code for these tests and general
instructions for running them may be found at
https://github.com/promises-aplus/promises-tests.

Compliance with the Promises/A+ spec is sufficent for normal use of promises
and for the closure-compiler to use them internally to implement async
functions. However, the [ECMAScript spec](https://tc39.github.io/ecma262/)
defines additional behavior primarily revolving around making subclasses of
Promise behave in predictable ways. The closure-compiler implementation of
Promises does not provide this behavior, so subclassing of Promise is
discouraged if you intend to have your code work in environments that do not
support native-JavaScript Promises.

This directory contains files that were used in order to run the Promise/A+
compliance tests.


*   run-tests.sh


    Run this with no arguments to execute the tests. It requires:

    *   The compiler must be built first.
    *   npm must be installed

*   test-adapter.js

    tells the tests how to get Promise objects for testing.
    run-tests.sh will compile this file.

*   test-externs.js

    Needed for compiling test-adapter.js
