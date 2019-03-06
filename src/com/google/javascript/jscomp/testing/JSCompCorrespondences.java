/*
 * Copyright 2010 The Closure Compiler Authors.
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

import com.google.common.truth.Correspondence;
import com.google.javascript.jscomp.CompilerInput;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;
import java.util.function.Function;

/** Well known {@link Correspondence} instances for use in tests. */
public final class JSCompCorrespondences {

  public static final Correspondence<JSError, DiagnosticType> DIAGNOSTIC_EQUALITY =
      transforming(JSError::getType, "has diagnostic type equal to");

  public static final Correspondence<JSError, String> DESCRIPTION_EQUALITY =
      transforming((e) -> e.description, "has description equal to");

  public static final Correspondence<CompilerInput, String> INPUT_NAME_EQUALITY =
      transforming(CompilerInput::getName, "has name equal to");

  private static final <A, E> Correspondence<A, E> transforming(
      Function<? super A, ? extends E> transformation, String description) {
    return new Correspondence<A, E>() {
      @Override
      public boolean compare(A actual, E expected) {
        return transformation.apply(actual).equals(expected);
      }

      @Override
      public String toString() {
        return description;
      }
    };
  }

  // Not instantiable.
  private JSCompCorrespondences() {}
}
