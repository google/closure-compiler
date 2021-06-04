/*
 * Copyright 2021 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.Node;

/** Constants that need to be shared between `ReplaceMessages` and other passes. */
public final class ReplaceMessagesConstants {

  /**
   * `goog.getMsg()` calls will be converted into a call to this method which is defined in
   * synthetic externs.
   */
  static final String DEFINE_MSG_CALLEE = "__jscomp_define_msg__";

  /**
   * `goog.getMsgWithFallback(MSG_NEW, MSG_OLD)` will be converted into a call to this method which
   * is defined in * synthetic externs.
   */
  static final String FALLBACK_MSG_CALLEE = "__jscomp_msg_fallback__";

  private static final ImmutableSet<String> PROTECTED_FUNCTION_NAMES =
      ImmutableSet.of(DEFINE_MSG_CALLEE, FALLBACK_MSG_CALLEE);

  static boolean isProtectedMessage(Node n) {
    if (!n.isCall()) {
      return false;
    }
    final Node callee = n.getFirstChild();
    if (!callee.isName()) {
      return false;
    }
    return PROTECTED_FUNCTION_NAMES.contains(callee.getString());
  }

  // There's no reason to instantiate this class
  private ReplaceMessagesConstants() {}
}
