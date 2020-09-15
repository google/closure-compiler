/*
 * Copyright 2020 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.instrumentation.reporter;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.GwtIncompatible;
import com.google.gson.GsonBuilder;
import com.google.javascript.jscomp.instrumentation.reporter.ProductionInstrumentationReporter.InstrumentationType;
import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.debugging.sourcemap.Base64VLQ;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The class contains the data which is provided by the Production Instrumentation pass in the
 * closure compiler. It will read the data from the file name created with the
 * --instrument_mapping_report and is required to follow the format used by Closure Compiler class
 * VariableMap.
 */
@GwtIncompatible
final class InstrumentationMapping {

  final ImmutableList<String> fileNames;
  final ImmutableMap<String, Location> parameterMapping;

  private InstrumentationMapping(ImmutableList<String> fileNames,  ImmutableMap<String, Location> parameterMapping) {
    this.fileNames = fileNames;
    this.parameterMapping = parameterMapping;
  }


  /**
   * Parses the file found at location mappingFilePath and populates the properties of this class
   */
  public static InstrumentationMapping parse(String mappingFilePath) throws IOException {

    Map<String, Location> localParameterMapping = new HashMap<>();

    String instrumentationMappingFile = ProductionInstrumentationReporter.readFile(mappingFilePath);
    List<String> linesOfFile = Splitter.on('\n').omitEmptyStrings()
        .splitToList(instrumentationMappingFile);

    // Report should contain at least three lines
    checkState(linesOfFile.size() >= 3);

    String fileNamesLine = linesOfFile.get(0).trim();
    String functionNamesLine = linesOfFile.get(1).trim();
    String typesLine = linesOfFile.get(2).trim();

    checkState(fileNamesLine.startsWith("FileNames:"));
    checkState(functionNamesLine.startsWith("FunctionNames:"));
    checkState(typesLine.startsWith("Types:"));

    String fileNamesAsJsonArray = fileNamesLine.substring(fileNamesLine.indexOf(":") + 1);
    String functionNamesAsJsonArray = functionNamesLine
        .substring(functionNamesLine.indexOf(":") + 1);
    String typesAsJsonArray = typesLine.substring(typesLine.indexOf(":") + 1);

    Type stringListType = new TypeToken<List<String>>() {
    }.getType();

    Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    List<String> localFileNames = gson.fromJson(fileNamesAsJsonArray, stringListType);
    List<String> functionNames = gson.fromJson(functionNamesAsJsonArray, stringListType);

    List<String> typesAsStringList = gson.fromJson(typesAsJsonArray, stringListType);
    List<InstrumentationType> types = InstrumentationType.convertFromStringList(typesAsStringList);

    for (int i = 3; i < linesOfFile.size(); ++i) {
      String lineItem = linesOfFile.get(i);
      String id = lineItem.substring(0, lineItem.indexOf(':'));
      String encodedDetails = lineItem.substring(lineItem.indexOf(':') + 1);

      StringCharIterator encodedDetailsAsCharIt = new StringCharIterator(encodedDetails);

      Location temp = Location.create(
          localFileNames.get(Base64VLQ.decode(encodedDetailsAsCharIt)),
          functionNames.get(Base64VLQ.decode(encodedDetailsAsCharIt)),
          types.get(Base64VLQ.decode(encodedDetailsAsCharIt)),
          Base64VLQ.decode(encodedDetailsAsCharIt),
          Base64VLQ.decode(encodedDetailsAsCharIt)
      );
      localParameterMapping.putIfAbsent(id, temp);
    }

    return new InstrumentationMapping(ImmutableList.copyOf(localFileNames),
        ImmutableMap.copyOf(localParameterMapping));

  }

  public String getFileName(String id) {
    return parameterMapping.get(id).fileName();
  }

  public String getFunctionName(String id) {
    return parameterMapping.get(id).functionName();
  }

  public InstrumentationType getType(String id) {
    return parameterMapping.get(id).type();
  }

  public int getLineNo(String id) {
    return parameterMapping.get(id).lineNo();
  }

  public int getColNo(String id) {
    return parameterMapping.get(id).colNo();
  }

  /**
   * This function will return a list of unique parameters which match the criteria specified by the
   * predicate. As an example, it can return a list of all unique parameters which match a specific
   * file name.
   */
  public List<String> getAllMatchingValues(Predicate<String> comparisonPredicate) {
    List<String> result = new ArrayList<>();
    for (String key : parameterMapping.keySet()) {
      if (comparisonPredicate.test(key)) {
        result.add(key);
      }
    }
    return result;
  }

  @AutoValue
  abstract static class Location {

    static Location create(String a, String b, InstrumentationType c, int d, int e) {
      return new AutoValue_InstrumentationMapping_Location(a, b, c, d, e);
    }

    abstract String fileName();

    abstract String functionName();

    abstract InstrumentationType type();

    abstract int lineNo();

    abstract int colNo();
  }

  /**
   * A implementation of the Base64VLQ CharIterator used for decoding the mappings encoded in the
   * JSON string.
   */
  private static class StringCharIterator implements Base64VLQ.CharIterator {

    final String content;
    final int length;
    int current = 0;

    StringCharIterator(String content) {
      this.content = content;
      this.length = content.length();
    }

    @Override
    public char next() {
      return content.charAt(current++);
    }

    @Override
    public boolean hasNext() {
      return current < length;
    }
  }

}
