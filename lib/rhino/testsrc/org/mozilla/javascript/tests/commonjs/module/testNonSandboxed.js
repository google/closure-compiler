/**
 * @version $Id: testNonSandboxed.js,v 1.1 2011/04/07 22:24:37 hannes%helma.at Exp $
 */
var assert = require("assert");
function isUndefined(x) {var u; return x === u;}
assert.ok(isUndefined(require.paths));
assert.ok(isUndefined(module.uri));