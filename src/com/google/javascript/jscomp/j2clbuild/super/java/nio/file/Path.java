/*
 * Copyright 2015 The Closure Compiler Authors.
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

package java.nio.file;

/** GWT compatible no-op replacement for {@code Path} */
public interface Path {
  Path getParent();

  Path resolveSibling(String other);

  Path normalize();

  Path relativize(Path other);

  Path resolve(String other);

  Path resolve(Path other);

  static Path of(String first, String... more) {
    throw new UnsupportedOperationException("Operation not available in JavaScript.");
  }
}
