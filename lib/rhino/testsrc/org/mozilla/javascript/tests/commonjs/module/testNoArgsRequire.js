/**
 * @version $Id: testNoArgsRequire.js,v 1.1 2011/04/07 22:24:37 hannes%helma.at Exp $
 */
var assert = require("assert");
try {
    require();
    assert.fail("require() succeeded with no arguments");
}
catch(e) {
    assert.equal(e.message, "require() needs one argument");
}

try {
    new require();
    assert.fail("require() succeeded as a constructor");
}
catch(e) {
    assert.equal(e.message, "require() can not be invoked as a constructor");
}