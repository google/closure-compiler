function expectTypeError(code) { expectError(code, TypeError); }

function expectError(code, error) {
  try {
    code();
    throw (code.toSource() + ' should have thrown a '+error);
  } catch (e if e instanceof error) {
    // all good
  }
}
