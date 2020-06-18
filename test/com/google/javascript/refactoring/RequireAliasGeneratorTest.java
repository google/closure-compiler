/*
 * Copyright 2014 The Closure Compiler Authors.
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

package com.google.javascript.refactoring;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Streams.stream;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public final class RequireAliasGeneratorTest {

  @Parameterized.Parameters(name = "{0}")
  public static Object[][] cases() {
    return new Object[][] {
      {"ns.Name", ImmutableList.of("Name", "NsName", "NsName0", "NsName1")},
      {"ns.name", ImmutableList.of("name", "nsName", "nsName0", "nsName1")},
      // Skiplisted aliases
      {"Array", ImmutableList.of("Array0")},
      {"array", ImmutableList.of("array0")},
      {"goog.Promise", ImmutableList.of("GoogPromise")},
      {"goog.Thenable", ImmutableList.of("GoogThenable")},
      {"goog.array", ImmutableList.of("googArray")},
      {"goog.events.Event", ImmutableList.of("EventsEvent")},
      {"goog.math", ImmutableList.of("googMath")},
      {"goog.object", ImmutableList.of("googObject")},
      {"goog.string", ImmutableList.of("googString")},
      {"goog.structs.Map", ImmutableList.of("StructsMap")},
      {"goog.structs.Set", ImmutableList.of("StructsSet")},
      {"wiz.Object", ImmutableList.of("WizObject")},
      {"xid.String", ImmutableList.of("XidString")},
    };
  }

  @Parameterized.Parameter(0)
  public String namespace;

  @Parameterized.Parameter(1)
  public ImmutableList<String> aliases;

  @Test
  public void testGetShortNameForRequire() {
    ImmutableList<String> actual =
        stream(RequireAliasGenerator.over(this.namespace))
            .limit(this.aliases.size())
            .collect(toImmutableList());

    assertThat(actual).containsExactlyElementsIn(this.aliases);
  }
}
