/*
 * Copyright 2011 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.parsing.parser;

/**
 * The set of all non-keyword, non-reserved words used in javascript.
 */
public final class PredefinedName {

  private PredefinedName() {}

  public static final String ADD_CONTINUATION = "addContinuation";
  public static final String APPLY = "apply";
  public static final String ARGUMENTS = "arguments";
  public static final String ARRAY = "Array";
  public static final String AS = "as";
  public static final String BIND = "bind";
  public static final String CALL = "call";
  public static final String CALLBACK = "callback";
  public static final String CAPTURED_ARGUMENTS = "$arguments";
  public static final String CAPTURED_THIS = "$this";
  public static final String CAUGHT_EXCEPTION = "$caughtException";
  public static final String CLOSE = "close";
  public static final String CONFIGURABLE = "configurable";
  public static final String CONSTRUCTOR = "constructor";
  public static final String CONTINUATION = "$continuation";
  public static final String CREATE = "create";
  public static final String CREATE_CALLBACK = "$createCallback";
  public static final String CREATE_CLASS = "createClass";
  public static final String CREATE_ERRBACK = "$createErrback";
  public static final String CREATE_PROMISE = "createPromise";
  public static final String CURRENT = "current";
  public static final String DEFERRED = "Deferred";
  public static final String DEFINE_GETTER = "__defineGetter__";
  public static final String DEFINE_PROPERTY = "defineProperty";
  public static final String DEFINE_SETTER = "__defineSetter__";
  public static final String ENUMERABLE = "enumerable";
  public static final String ERR = "$err";
  public static final String ERRBACK = "errback";
  public static final String FINALLY_FALL_THROUGH = "$finallyFallThrough";
  public static final String FIELD_INITIALIZER_METHOD = "$field_initializer_";
  public static final String FREEZE = "freeze";
  public static final String FROM = "from";
  public static final String GET = "get";
  public static final String INIT = "$init";
  public static final String IS_DONE = "isDone";
  public static final String ITERATOR = "__iterator__";
  public static final String JSPP = "jspp";
  public static final String LENGTH = "length";
  public static final String LOOKUP_GETTER = "__lookupGetter__";
  public static final String LOOKUP_SETTER = "__lookupSetter__";
  public static final String MIXIN = "mixin";
  public static final String MODULE = "module";
  public static final String MOVE_NEXT = "moveNext";
  public static final String NEW_FACTORY = "$new";
  public static final String NEW_STATE = "$newState";
  public static final String OBJECT = "Object";
  public static final String OBJECT_NAME = "Object";
  public static final String OF = "of";
  public static final String PARAM = "$param";
  public static final String PROTO = "__proto__";
  public static final String PROTOTYPE = "prototype";
  public static final String PUSH = "push";
  public static final String REQUIRES = "requires";
  public static final String RESULT = "$result";
  public static final String SET = "set";
  public static final String SLICE = "slice";
  public static final String STATE = "$state";
  public static final String STATIC = "$static";
  public static final String STORED_EXCEPTION = "$storedException";
  public static final String SUPER_CALL = "superCall";
  public static final String SUPER_GET = "superGet";
  public static final String THAT = "$that";
  public static final String THEN = "then";
  public static final String TRAIT = "trait";
  public static final String TYPE_ERROR = "TypeError";
  public static final String UNDEFINED = "undefined";
  public static final String VALUE = "value";
  public static final String $VALUE = "$value";
  public static final String WAIT_TASK = "$waitTask";
  public static final String WRITABLE = "writable";

  public static String getParameterName(int index) {
    // TODO: consider caching these
    return "$" + index;
  }
}
