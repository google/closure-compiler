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
assert.ok(assert === require("assert"));
assert.ok(assert === require("x/modx").assertThroughX);
assert.ok(assert === require("x/y/mody").assertThroughXAndY);
assert.ok(assert === require("x/y/mody").assertThroughY);