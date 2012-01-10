// http://wiki.commonjs.org/wiki/Unit_Testing/1.0
// Zachary Carter (zaach)
// Kris Kowal (kriskowal), original skeleton
// Felix Geisendörfer (felixge), editions backported from NodeJS
// Karl Guertin, editions backported from NodeJS
// Ash Berlin (ashb), contributions annotated

var util = require("util");
var system = require("system");

// 1. The assert module provides functions that throw
// AssertionError's when particular conditions are not met. The
// assert module must conform to the following interface.

var assert = exports;

// 2. The AssertionError is defined in assert.
// new assert.AssertionError({message: message, actual: actual, expected: expected})

assert.AssertionError = function (options) {
    if (typeof options == "string")
        options = {"message": options};
    this.name = "AssertionError";
    this.message = options.message;
    this.actual = options.actual;
    this.expected = options.expected;
    this.operator = options.operator;

    // this lets us get a stack trace in Rhino
    if (system.engine == "rhino")
        this.rhinoException = Packages.org.mozilla.javascript.JavaScriptException(this, null, 0);

    // V8 specific
    if (Error.captureStackTrace) {
        Error.captureStackTrace(this, (this.fail || assert.fail));
        // Node specific, removes the node machinery stack frames
        // XXX __filename will probably not always be the best way to detect Node
        if (typeof __filename !== undefined) {
            var stack = this.stack.split("\n");
            for (var i = stack.length - 1; i >= 0; i--) {
                if (stack[i].indexOf(__filename) != -1) {
                    this.stack = stack.slice(0, i + 2).join("\n");
                    break;
                }
            }
        }
    }

};

// XXX extension
// Ash Berlin
assert.AssertionError.prototype.toString = function (){
    if (this.message) {
        return [
            this.name + ":",
            this.message
        ].join(" ");
    } else {
        return [
            this.name + ":",
            util.repr(this.expected),
            this.operator,
            util.repr(this.actual)
        ].join(" ");
    }
}

// XXX extension
// Ash Berlin
assert.AssertionError.prototype.toSource = function () {
    return "new (require('assert').AssertionError)(" + Object.prototype.toSource.call(this) + ")";
};

// assert.AssertionError instanceof Error

assert.AssertionError.prototype = Object.create(Error.prototype);

// At present only the three keys mentioned above are used and
// understood by the spec. Implementations or sub modules can pass
// other keys to the AssertionError's constructor - they will be
// ignored.

// 3. All of the following functions must throw an AssertionError
// when a corresponding condition is not met, with a message that
// may be undefined if not provided.  All assertion methods provide
// both the actual and expected values to the assertion error for
// display purposes.

assert.fail = function (options) {
    throw new assert.AssertionError(options);
};

// XXX extension
// stub for logger protocol
assert.pass = function () {
};

// XXX extension
// stub for logger protocol
assert.error = function () {
};

// XXX extension
// stub for logger protocol
assert.section = function () {
    return this;
};

// 4. Pure assertion tests whether a value is truthy, as determined
// by !!guard.
// assert.ok(guard, message_opt);
// This statement is equivalent to assert.equal(true, guard,
// message_opt);. To test strictly for the value true, use
// assert.strictEqual(true, guard, message_opt);.

assert.ok = function (value, message) {
    if (!!!value)
        (this.fail || assert.fail)({
            "actual": value,
            "expected": true,
            "message": message,
            "operator": "=="
        });
    else
        (this.pass || assert.pass)(message);
};

// 5. The equality assertion tests shallow, coercive equality with
// ==.
// assert.equal(actual, expected, message_opt);

assert.equal = function (actual, expected, message) {
    if (actual != expected)
        (this.fail || assert.fail)({
            "actual": actual,
            "expected": expected,
            "message": message,
            "operator": "=="
        });
    else
        (this.pass || assert.pass)(message);
};


// 6. The non-equality assertion tests for whether two objects are not equal
// with != assert.notEqual(actual, expected, message_opt);

assert.notEqual = function (actual, expected, message) {
    if (actual == expected)
        (this.fail || assert.fail)({
            "actual": actual,
            "expected": expected,
            "message": message,
            "operator": "!="
        });
    else
        (this.pass || assert.pass)(message);
};

// 7. The equivalence assertion tests a deep equality relation.
// assert.deepEqual(actual, expected, message_opt);

assert.deepEqual = function (actual, expected, message) {
    if (!deepEqual(actual, expected))
        (this.fail || assert.fail)({
            "actual": actual,
            "expected": expected,
            "message": message, 
            "operator": "deepEqual"
        });
    else
        (this.pass || assert.pass)(message);
};

function deepEqual(actual, expected) {
    
    // 7.1. All identical values are equivalent, as determined by ===.
    if (actual === expected) {
        return true;

    // 7.2. If the expected value is a Date object, the actual value is
    // equivalent if it is also a Date object that refers to the same time.
    } else if (actual instanceof Date && expected instanceof Date) {
        return actual.getTime() === expected.getTime();

    // 7.3. Other pairs that do not both pass typeof value == "object",
    // equivalence is determined by ==.
    } else if (typeof actual != 'object' && typeof expected != 'object') {
        return actual == expected;

    // XXX specification bug: this should be specified
    } else if (typeof expected == "string" || typeof actual == "string") {
        return expected === actual;

    // 7.4. For all other Object pairs, including Array objects, equivalence is
    // determined by having the same number of owned properties (as verified
    // with Object.prototype.hasOwnProperty.call), the same set of keys
    // (although not necessarily the same order), equivalent values for every
    // corresponding key, and an identical "prototype" property. Note: this
    // accounts for both named and indexed properties on Arrays.
    } else {
        return actual.prototype === expected.prototype && objEquiv(actual, expected);
    }
}

function objEquiv(a, b, stack) {
    return (
        !util.no(a) && !util.no(b) &&
        arrayEquiv(
            util.sort(util.object.keys(a)),
            util.sort(util.object.keys(b))
        ) &&
        util.object.keys(a).every(function (key) {
            return deepEqual(a[key], b[key], stack);
        })
    );
}

function arrayEquiv(a, b, stack) {
    return util.isArrayLike(b) &&
        a.length == b.length &&
        util.zip(a, b).every(util.apply(function (a, b) {
            return deepEqual(a, b, stack);
        }));
}

// 8. The non-equivalence assertion tests for any deep inequality.
// assert.notDeepEqual(actual, expected, message_opt);

assert.notDeepEqual = function (actual, expected, message) {
    if (deepEqual(actual, expected))
        (this.fail || assert.fail)({
            "actual": actual,
            "expected": expected,
            "message": message,
            "operator": "notDeepEqual"
        });
    else
        (this.pass || assert.pass)(message);
};

// 9. The strict equality assertion tests strict equality, as determined by ===.
// assert.strictEqual(actual, expected, message_opt);

assert.strictEqual = function (actual, expected, message) {
    if (actual !== expected)
        (this.fail || assert.fail)({
            "actual": actual,
            "expected": expected,
            "message": message,
            "operator": "==="
        });
    else
        (this.pass || assert.pass)(message);
};

// 10. The strict non-equality assertion tests for strict inequality, as determined by !==.
// assert.notStrictEqual(actual, expected, message_opt);

assert.notStrictEqual = function (actual, expected, message) {
    if (actual === expected)
        (this.fail || assert.fail)({
            "actual": actual,
            "expected": expected,
            "message": message,
            "operator": "!=="
        });
    else
        (this.pass || assert.pass)(message);
};

// 11. Expected to throw an error:
// assert.throws(block, Error_opt, message_opt);

assert["throws"] = function (block, Error, message) {
    var threw = false,
        exception = null;

    // (block)
    // (block, message:String)
    // (block, Error)
    // (block, Error, message)

    if (typeof Error == "string") {
        message = Error;
        Error = undefined;
    }

    try {
        block();
    } catch (e) {
        threw = true;
        exception = e;
    }
    
    if (!threw) {
        (this.fail || assert.fail)({
            "message": message,
            "operator": "throw"
        });
    } else if (Error) {
        if (exception instanceof Error)
            (this.pass || assert.pass)(message);
        else
            throw exception;
    } else {
        (this.pass || assert.pass)(message);
    }

};

// XXX extension
assert.Assert = function (log) {
    var self = Object.create(assert);
    self.pass = log.pass.bind(log);
    self.fail = log.fail.bind(log);
    self.error = log.error.bind(log);
    self.section = log.section.bind(log);
    return self;
};