/*
 * Copyright 2008 The Closure Compiler Authors.
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

import java.io.Serializable;
import java.text.MessageFormat;

/**
 * The type of a compile or analysis error.
 *
 */
public class DiagnosticType
    implements Comparable<DiagnosticType>, Serializable {
  private static final long serialVersionUID = 1;

  /**
   * The error type. Used as the BugPattern and BugInstance types by
   * BugBot's XML
   */
  public final String key;

  /** The default way to format errors */
  public final MessageFormat format;

  /** Default level */
  public final CheckLevel defaultLevel;

  /** Reporting level, initially the defaultLevel but may be changed. */
  public CheckLevel level;

  /**
   * Create a DiagnosticType at level CheckLevel.ERROR
   *
   * @param name An identifier
   * @param descriptionFormat A format string
   * @return A new DiagnosticType
   */
  public static DiagnosticType error(String name, String descriptionFormat) {
    return make(name, CheckLevel.ERROR, descriptionFormat);
  }

  /**
   * Create a DiagnosticType at level CheckLevel.WARNING
   *
   * @param name An identifier
   * @param descriptionFormat A format string
   * @return A new DiagnosticType
   */
  public static DiagnosticType warning(String name, String descriptionFormat) {
    return make(name, CheckLevel.WARNING, descriptionFormat);
  }

  /**
   * Create a DiagnosticType at level CheckLevel.OFF
   *
   * @param name An identifier
   * @param descriptionFormat A format string
   * @return A new DiagnosticType
   */
  public static DiagnosticType disabled(String name,
      String descriptionFormat) {
    return make(name, CheckLevel.OFF, descriptionFormat);
  }

  /**
   * Create a DiagnosticType at a given CheckLevel.
   *
   * @param name An identifier
   * @param level Either CheckLevel.ERROR or CheckLevel.WARNING
   * @param descriptionFormat A format string
   * @return A new DiagnosticType
   */
  public static DiagnosticType make(String name, CheckLevel level,
                                    String descriptionFormat) {
    return
        new DiagnosticType(name, level, new MessageFormat(descriptionFormat));
  }

  /**
   * Create a DiagnosticType. Private to force use of static factory methods.
   */
  private DiagnosticType(String key, CheckLevel level, MessageFormat format) {
    this.key = key;
    this.defaultLevel = level;
    this.format = format;

    this.level = this.defaultLevel;
  }

  /**
   * Create a description from the MessageFormat and the arguments.
   * Used by unit tests.
   */
  String format(Object ... arguments) {
    return format.format(arguments);
  }

  @Override
  public boolean equals(Object type) {
    return type instanceof DiagnosticType &&
        ((DiagnosticType) type).key.equals(key);
  }

  @Override
  public int hashCode() {
    return key.hashCode();
  }

  @Override
  public int compareTo(DiagnosticType diagnosticType) {
    return key.compareTo(diagnosticType.key);
  }

  @Override
  public String toString() {
    return key + ": " + format.toPattern();
  }
}
