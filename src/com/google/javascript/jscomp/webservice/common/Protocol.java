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

import com.google.common.collect.Sets;

import java.util.Set;

/**
 * All the strings used by the webservice protocol.
 *
 */
public class Protocol {

  private Protocol() {}

  /**
   * All enums that need to be shared between the Java and JS code should
   * implement this interface.
   */
  public static interface ProtocolEnum {
    /**
     * @return A string representing the key or value specified by the
     * protocol.
     */
    public String getValue();
  }

  /**
   * All the keys that can be part of the http request.
   */
  public static enum RequestKey implements ProtocolEnum {
    CODE_URL("code_url"),
    JS_CODE("js_code"),
    EXCLUDE_DEFAULT_EXTERNS("exclude_default_externs"),
    EXTERNS_URL("externs_url"),
    EXTERNS_CODE("js_externs"),
    COMPILATION_LEVEL("compilation_level"),
    OUTPUT_FORMAT("output_format"),
    OUTPUT_INFO("output_info"),
    OUTPUT_FILE_NAME("output_file_name"),
    OUTPUT_WRAPPER("output_wrapper"),
    API_KEY("api_key"),
    FORMATTING("formatting"),
    WARNING_LEVEL("warning_level"),
    USER_ID("uid"),
    USE_CLOSURE("use_closure_library"),
    BUILD_DEBUG("debug"),
    CHARSET("charset"),
    LANGUAGE("language"),
    USE_TYPES_FOR_OPTIMIZATIONS("use_types_for_optimization"),
    ANGULAR_PASS("angular_pass"),
    GENERATE_EXPORTS("generate_exports"),
    DISABLE_PROPERTY_RENAMING("disable_property_renaming"),

    // Old ROBOCOMP urls.
    RAWJS("rawjs"),
    BASE("base"),
    MODE("mode"),
    SCRIPT("script"),
    NOCACHE("nocache") // Ignored.
    ;

    private static final Set<String> permittedKeys = getPermittedKeys();

    private static Set<String> getPermittedKeys() {
      Set<String> keys = Sets.newHashSet();

      for (RequestKey key : RequestKey.values()) {
        keys.add(key.asGetParameter());
      }
      return keys;
    }

    private final String asGetParameter;

    private RequestKey(String asGetParameter) {
      this.asGetParameter = asGetParameter;
    }

    public String asGetParameter() {
      return asGetParameter;
    }

    @Override
    public String toString() {
      return asGetParameter;
    }

    public static boolean isKeyValid(String key) {
      return permittedKeys.contains(key.toLowerCase());
    }

    @Override
    public String getValue() {
      return asGetParameter;
    }
  }

  /**
   * All the possible values for the OUTPUT_INFO key.
   */
  public static enum OutputInfoKey implements ProtocolEnum {
    VARIABLE_MAP("variable_map"),
    COMPILED_CODE("compiled_code"),
    WARNINGS("warnings"),
    ERRORS("errors"),
    STATISTICS("statistics"),
    ;

    private final String value;

    private OutputInfoKey(String value) {
      this.value = value;
    }

    @Override
    public String getValue() {
      return value;
    }
  }

  /**
   * All the possible values for the FORMATTING key.
   */
  public static enum FormattingKey implements ProtocolEnum {
    PRETTY_PRINT("pretty_print"),
    PRINT_INPUT_DELIMITER("print_input_delimiter"),
    ;

    private final String value;

    private FormattingKey(String value) {
      this.value = value;
    }

    @Override
    public String getValue() {
      return value;
    }
  }

  public static enum CompilationLevelKey implements ProtocolEnum {
    WHITESPACE_ONLY("whitespace_only"),
    SIMPLE_OPTIMIZATIONS("simple_optimizations"),
    ADVANCED_OPTIMIZATIONS("advanced_optimizations"),
    ;

    private final String value;

    CompilationLevelKey(String value) {
      this.value = value;
    }

    @Override
    public String getValue() {
      return value;
    }
  }

  public static enum WarningLevelKey implements ProtocolEnum {
    QUIET("quiet"),
    DEFAULT("default"),
    VERBOSE("verbose"),
    ;

    private final String value;

    private WarningLevelKey(String value) {
      this.value = value;
    }

    @Override
    public String getValue() {
      return value;
    }
  }

  public static enum OutputFormatKey implements ProtocolEnum {
    TEXT("text"),
    XML("xml"),
    JSON("json"),
    ;

    private final String value;

    private OutputFormatKey(String value) {
      this.value = value;
    }

    @Override
    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return getValue();
    }
  }

  /**
   * Fields in the JSON response from the ApiKeyGenerationServlet.
   */
  public static enum ApiKeyResponse implements ProtocolEnum {
    API_KEY("api_key"),
    ;

    private final String responseParam;

    ApiKeyResponse(String responseParam) {
      this.responseParam = responseParam;
    }

    /**
     * Name of the key as it appears in the JSON.
     */
    public String getResponseParam() {
      return responseParam;
    }

    @Override
    public String toString() {
      return getResponseParam();
    }

    @Override
    public String getValue() {
      return getResponseParam();
    }
  }

  /**
   * All the xml/json tags that can be returned by the backend if xml or json is
   * selected as the output mode.
   */
  public static enum ResponseTag implements ProtocolEnum {
    ROOT_TAG("compilationResult"),
    COMPILED_CODE_TAG("compiledCode"),
    WARNINGS_TAG("warnings"),
    WARNING_TAG("warning"),
    ERRORS_TAG("errors"),
    ERROR_TAG("error"),
    ERROR_LINE_NO_ATTR("lineno"),
    ERROR_LINE_ATTR("line"),
    // Charno is negative if error occurred outside range of columns that
    // JSCompiler records.  Change the sign of the value to find the
    // maximum column represented.
    // Note that JSCompiler uses -1 as an "I don't know" state, and it can
    // also turn up occasionally.
    ERROR_CHAR_ATTR("charno"),
    ERROR_FILE_ATTR("file"),
    ERROR_TYPE_ATTR("type"),
    STATS_TAG("statistics"),
    ORIGINAL_SIZE_TAG("originalSize"),
    ORIGINAL_GZIP_SIZE_TAG("originalGzipSize"),
    COMPRESSED_SIZE_TAG("compressedSize"),
    COMPRESSED_GZIP_SIZE_TAG("compressedGzipSize"),
    COMPILE_TIME_TAG("compileTime"),
    SERVER_ERRORS_TAG("serverErrors"),
    SERVER_ERROR_TAG("error"),
    SERVER_ERROR_CODE_ATTR("code"),
    VARIABLE_MAP("variableMap"),
    VARIABLE_MAP_ENTRY("variableMapEntry"),
    ORIGINAL_NAME_ATTR("originalName"),
    NEW_NAME_ATTR("newName"),
    OUTPUT_FILE_PATH("outputFilePath"),
    ;

    private final String responseTag;

    private ResponseTag(String responseTag) {
      this.responseTag = responseTag;
    }

    public String getResponseTag() {
      return responseTag;
    }

    @Override
    public String toString() {
      return getResponseTag();
    }

    @Override
    public String getValue() {
      return getResponseTag();
    }
  }

  /**
   * Properties key for getting the maximum input file size that may be
   * compiled by the service.  This is parameterized so we can have different
   * values for inside and outside Google.
   * The value should be a string representation of an integer representing
   * the maximum input size in bytes.
   */
  public static final String MAX_INPUT_SIZE_KEY =
      "com.google.javascript.jscomp.webservice.maximumInputSize";

  /**
   * Fallback value in case no setting is provided.
   */
  public static final int FALLBACK_MAX_INPUT_SIZE =
      500 * 1024;

  /**
   * Hard limit on input size set at execution time from the MAX_INPUT_SIZE_KEY
   * property.
   */
  private static int maxInputSize;

  /**
   * Initialize maxInputSize to the value from the MAX_INPUT_SIZE_KEY property
   * at startup.
   */
  static {
    resetMaximumInputSize();
  }

  /**
   * Find the maximum input size that this configuration of the web service
   * allows.
   * @return maximum input size permitted (in bytes)
   */
  public static final int maximumInputSize() {
    // Limit the number of files downloaded if they are too big to compile.
    return maxInputSize;
  }

  /**
   * Reset the maximum input size so that the property key is rechecked.
   * This is needed for testing code because we are caching the maximum
   * input size value.
   */
  public static final void resetMaximumInputSize() {
    String maxInputSizeStr = System.getProperty(Protocol.MAX_INPUT_SIZE_KEY);

    if (maxInputSizeStr == null) {
      maxInputSize = Protocol.FALLBACK_MAX_INPUT_SIZE;
    } else {
      maxInputSize = Integer.parseInt(maxInputSizeStr);
    }
  }
}
