/*
 * Copyright 2022 The Closure Compiler Authors.
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

package com.google.gson.stream;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;

/**
 * A minimal supersource API for the real `JsonWriter` class.
 *
 * <p>This class is not intended to really work. Methods are added as needed.
 */
public abstract class JsonWriter {

  /**
   * Begins encoding a new array. Each call to this method must be paired with a call to {@link
   * #endArray}.
   */
  @CanIgnoreReturnValue
  public abstract JsonWriter beginArray() throws IOException;

  /** Ends encoding the current array. */
  @CanIgnoreReturnValue
  public abstract JsonWriter endArray() throws IOException;

  /**
   * Begins encoding a new object. Each call to this method must be paired with a call to {@link
   * #endObject}.
   */
  @CanIgnoreReturnValue
  public abstract JsonWriter beginObject() throws IOException;

  /** Ends encoding the current object. */
  @CanIgnoreReturnValue
  public abstract JsonWriter endObject() throws IOException;

  /**
   * Encodes the property name.
   *
   * @param name the name of the forthcoming value. May not be null.
   */
  @CanIgnoreReturnValue
  public abstract JsonWriter name(String name) throws IOException;

  /** Encodes a number. */
  @CanIgnoreReturnValue
  public abstract JsonWriter value(Number n) throws IOException;

  /**
   * Encodes a string, escaping it according to RFC 4627 as well as for HTML ({@code <} and {@code
   * >} for example).
   */
  @CanIgnoreReturnValue
  public abstract JsonWriter value(String s) throws IOException;
}
