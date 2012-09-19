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

package com.google.javascript.jscomp.webservice.common;

/**
 * Enum of all the possible error described in the Web Service protocol.
 *
 */
public enum ErrorCode {
  UNKNOWN_OUTPUT_MODE(2),
  UNKNOWN_API_KEY(3),
  UNKNOWN_COMPILATION_LEVEL(4),
  UNKNOWN_CHARSET(5),
  POST_DATA_TOO_LARGE(8),
  FILE_TOO_LARGE(9),
  UNREACHABLE_URL(10),
  MALFORMED_URL(12),
  NO_OUTPUT_INFO(13),
  UNKNOWN_OUTPUT_INFO(14),
  MISSING_API_KEY(15),
  UNKNOWN_WARNING_LEVEL(16),
  UNKNOWN_FORMATTING_OPTION(17),
  UNKNOWN_PARAMETER(18),
  ILLEGAL_OUTPUT_FILE_NAME(19),
  HASH_MISMATCH(20),
  NO_CODE_FOUND_IN_CACHE(21),
  ACCOUNT_OVER_QUOTA(22),
  COMPILER_EXCEPTION(23),
  UNSUPPORTED_INPUT_RESOURCE_TYPE(24),
  DOWNLOAD_OVER_QUOTA(25),
  ;

  private final int code;
  ErrorCode(int code) {
    this.code = code;
  }

  public int getCode() {
    return code;
  }
}
