/*
 * Copyright 2016 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.testing;

import static com.google.common.truth.Truth.THROW_ASSERTION_ERROR;
import static org.junit.Assert.assertEquals;

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;

/**
 * A Truth Subject for the JSError class. Usage:
 * <pre>
 *   import static com.google.javascript.jscomp.testing.JSErrorSubject.assertNode;
 *   ...
 *   assertError(error).hasType(TypeValidator.TYPE_MISMATCH);
 * </pre>
 * TODO(tbreisacher): Add assertions on the message text, line number, etc.
 */
public final class JSErrorSubject extends Subject<JSErrorSubject, JSError> {
  public static JSErrorSubject assertError(JSError error) {
    return new JSErrorSubject(THROW_ASSERTION_ERROR, error);
  }

  public JSErrorSubject(FailureStrategy fs, JSError error) {
    super(fs, error);
  }

  public void hasType(DiagnosticType type) {
    String message = "Expected an error of type " + type + " but got: " + getSubject();
    assertEquals(message, type, getSubject().getType());
  }
}
