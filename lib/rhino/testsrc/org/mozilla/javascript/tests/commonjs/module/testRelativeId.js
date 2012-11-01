var assert = require("assert");
function testRelativeRequire() {
    try {
	    require("./assert");
	    assert.fail("Relative ID without a module succeeded");
	}
	catch(e) {
	    assert.equal(e.message, "Can't resolve relative module ID \"./assert\" when require() is used outside of a module");
	}
}
testRelativeRequire();
assert.strictEqual(assert, require("assert"));
assert.strictEqual(assert, require("x/y/mody").assertThroughXAndY);
assert.strictEqual(assert, require("x/y/mody").assertThroughY);
assert.strictEqual(assert, require("x/modx").assertThroughX);
assert.strictEqual(require("x/y/mody").modz, require("x/modx").modz);
assert.strictEqual(require("x/y/mody").modz.success, true);
