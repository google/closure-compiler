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

package java.net;

import java.io.Serializable;

/** GWT compatible minimal emulation of {@code URI} */
public class URI implements Comparable<URI>, Serializable  {
  private String uriString;

  public URI(String str) {
    uriString = str;
  }

  @Override
  public int compareTo(URI o) {
    return uriString.compareTo(o.toString());
  }

  @Override
  public int hashCode() {
    return uriString.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    URI other = (URI) o;
    return uriString.equals(other.toString());
  }

  @Override
  public String toString() {
    return uriString;
  }

  public static URI create(String str) {
    return new URI(str);
  }

  public URI relativize(URI uri) {
    throw new UnsupportedOperationException("URI.relativize not implemented");
  }

  public URI resolve(URI uri) {
    throw new UnsupportedOperationException("URI.resolve not implemented");
  }

  public URI normalize() {
    return this; // TODO(moz): Implement this.
  }
}
