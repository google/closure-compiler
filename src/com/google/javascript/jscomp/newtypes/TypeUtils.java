/*
 * Copyright 2013 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.newtypes;

import com.google.common.base.Preconditions;

/**
 * Static helper methods for new type inference.
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public class TypeUtils {

  public static String getQnameRoot(String qName) {
    // Preconditions.checkArgument(!isIdentifier(qName));
    int firstDot = qName.indexOf('.');
    return (firstDot == -1) ? qName : qName.substring(0, firstDot);
  }

  public static String getPropPath(String qName) {
    Preconditions.checkArgument(!isIdentifier(qName));
    return qName.substring(qName.indexOf('.') + 1);
  }

  public static boolean isIdentifier(String qName) {
    return qName.indexOf('.') == -1;
  }
}
