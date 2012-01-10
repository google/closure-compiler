/**
 * @version $Id: testSandboxed.js,v 1.1 2011/04/07 22:24:37 hannes%helma.at Exp $
 */
var assert = require("assert");

assert.strictEqual(require.paths, undefined);
assert.strictEqual(module.uri, undefined);