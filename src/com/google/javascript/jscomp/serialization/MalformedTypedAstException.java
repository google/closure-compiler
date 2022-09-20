/*
 * Copyright 2020 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.serialization;

import org.jspecify.nullness.Nullable;

/**
 * Emitted when deserialization sees a TypedAst with semantic errors.
 *
 * <p>This excpetion means a TypedAst is flawed in some way. It is distinct from other kinds of
 * expections because it describes a user error, rather than a bug in deserialization. Most likely,
 * the tool that generated the serialized data is faulty.
 *
 * <p>This exception says nothing about the byte-level encoding or parsing of the input data.
 * Instead, it means that TypedAst parsed from those bytes can't be translated into the compiler's
 * internal representation.
 */
public final class MalformedTypedAstException extends RuntimeException {

  static void checkWellFormed(boolean condition, String description) {
    if (!condition) {
      throw new MalformedTypedAstException(description);
    }
  }

  static void checkWellFormed(boolean condition, String description, @Nullable Object param) {
    if (!condition) {
      String message = description + ": " + param;
      throw new MalformedTypedAstException(message);
    }
  }

  public MalformedTypedAstException(Object msg) {
    super(msg.toString());
  }

  public MalformedTypedAstException(Object msg, Throwable cause) {
    super(msg.toString(), cause);
  }
}
