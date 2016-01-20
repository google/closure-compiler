/*
 * Copyright 2009 The Closure Compiler Authors.
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

package com.google.javascript.jscomp;

/**
 * Policies to determine how properties should be renamed.
 */
public enum PropertyRenamingPolicy {
  /**
   * Rename no properties.
   */
  OFF,

  /**
   * Rename all properties that aren't explicitly quoted and aren't
   * externally defined (i.e. declared in an externs file). This policy
   * achieves better compaction than the others.
   * @see RenameProperties
   */
  ALL_UNQUOTED,

  // for transitioning off old flags. not for public consumption.
  UNSPECIFIED
}
