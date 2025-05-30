/*
 * Copyright 2006 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.InlineMe;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A representation of a translatable message in JavaScript source code.
 *
 * <p>Instances are created using a {@link JsMessage.Builder}, like this:
 *
 * <pre>
 * JsMessage m = new JsMessage.Builder(key)
 *     .appendPart("Hi ")
 *     .appendPlaceholderReference("firstName")
 *     .appendPart("!")
 *     .setDesc("A welcome message")
 *     .build();
 * </pre>
 *
 * @param getSourceName Gets the message's sourceName.
 * @param getKey Gets the message's key, or name (e.g. {@code "MSG_HELLO"}).
 * @param getId Gets the message's id, or name (e.g. {@code "92430284230902938293"}).
 * @param getParts Gets a read-only list of the parts of this message. Each part is either a {@link
 *     String} or a {@link PlaceholderReference}.
 * @param getAlternateId Gets the message's alternate ID (e.g. {@code "92430284230902938293"}), if
 *     available. This will be used if a translation for `id` is not available.
 * @param getDesc Gets the description associated with this message, intended to help translators,
 *     or null if this message has no description.
 * @param getMeaning Gets the meaning annotated to the message, intended to force different
 *     translations.
 * @param jsPlaceholderNames Gets a set of the registered placeholders in this message in
 *     lowerCamelCase format.
 *     <p>This is the format used in `goog.getMsg()` declarations.
 * @param canonicalPlaceholderNames Gets a set of the registered placeholders in this message in
 *     UPPER_SNAKE_CASE format.
 *     <p>This is the format stored into XMB / XTB files and used in `declareIcuTemplate()
 *     declarations.
 */
public record JsMessage(
    @Nullable String getSourceName,
    String getKey,
    boolean isAnonymous,
    boolean isExternal,
    String getId,
    ImmutableList<Part> getParts,
    ImmutableMap<GrammaticalGenderCase, ImmutableList<Part>> getGenderedMessagesMap,
    @Nullable String getAlternateId,
    @Nullable String getDesc,
    @Nullable String getMeaning,
    ImmutableMap<String, String> getPlaceholderNameToExampleMap,
    ImmutableMap<String, String> getPlaceholderNameToOriginalCodeMap,
    ImmutableSet<String> jsPlaceholderNames,
    ImmutableSet<String> canonicalPlaceholderNames) {
  public JsMessage {
    requireNonNull(getKey, "getKey");
    requireNonNull(getId, "getId");
    requireNonNull(getParts, "getParts");
    requireNonNull(getPlaceholderNameToExampleMap, "getPlaceholderNameToExampleMap");
    requireNonNull(getPlaceholderNameToOriginalCodeMap, "getPlaceholderNameToOriginalCodeMap");
    requireNonNull(jsPlaceholderNames, "jsPlaceholderNames");
    requireNonNull(canonicalPlaceholderNames, "canonicalPlaceholderNames");
  }

  public static final String PH_JS_PREFIX = "{$";
  public static final String PH_JS_SUFFIX = "}";

  /** Enum for grammatical gender cases. */
  public enum GrammaticalGenderCase {
    MASCULINE,
    FEMININE,
    NEUTER,
    OTHER,
  }

  /**
   * Thrown when parsing a message string into parts fails because of a misformatted place holder.
   */
  public static final class PlaceholderFormatException extends Exception {

    public PlaceholderFormatException(String msg) {
      super(msg);
    }
  }

  public String getPlaceholderOriginalCode(PlaceholderReference placeholderReference) {
    return getPlaceholderNameToOriginalCodeMap()
        .getOrDefault(placeholderReference.storedPlaceholderName(), "-");
  }

  public String getPlaceholderExample(PlaceholderReference placeholderReference) {
    return getPlaceholderNameToExampleMap()
        .getOrDefault(placeholderReference.storedPlaceholderName(), "-");
  }

  public ImmutableList<Part> getGenderedMessageParts(GrammaticalGenderCase genderCase) {
    if (getGenderedMessagesMap().isEmpty()) {
      throw new UnsupportedOperationException(
          "Message does not contain grammatical gendered variants.");
    }
    return getGenderedMessagesMap().get(genderCase);
  }

  /** Gets the list of grammatical gender cases for the message. */
  public ImmutableList<GrammaticalGenderCase> getGenderedMessageVariants() {
    return getGenderedMessagesMap().keySet().asList();
  }

  /**
   * Returns a single string representing the message.
   *
   * <p>In the returned string all placeholders are joined with the literal string parts in the form
   * "literal string part {$jsPlaceholderName} more literal string", which is how placeholders are
   * represented in `goog.getMsg()` text strings.
   *
   * <p>If the message has gendered variants, the returned string will be a multiline string with
   * each line containing the grammatical gender case, followed by its message.
   *
   * @throws UnsupportedOperationException if the message has gendered variants.
   */
  public String asJsMessageString() {
    if (!getGenderedMessagesMap().isEmpty()) {
      throw new UnsupportedOperationException(
          "asJsMessageString() is not supported for messages with gendered variants. Please provide"
              + " a grammatical gender case as an argument.");
    }
    StringBuilder sb = new StringBuilder();
    for (Part p : getParts()) {
      if (p.isPlaceholder()) {
        sb.append(PH_JS_PREFIX).append(p.getJsPlaceholderName()).append(PH_JS_SUFFIX);
      } else {
        sb.append(p.getString());
      }
    }

    return sb.toString();
  }

  /**
   * Overloaded method that returns a single string representing the message for a given gender
   * case.
   *
   * <p>In the returned string all placeholders are joined with the literal string parts in the form
   * "literal string part {$jsPlaceholderName} more literal string", which is how placeholders are
   * represented in `goog.getMsg()` text strings.
   *
   * @param genderCase the grammatical gender case for which to return the message string.
   * @throws IllegalArgumentException if the message does not have a message for the given gender
   *     case.
   */
  public String asJsMessageString(GrammaticalGenderCase genderCase) {
    if (!getGenderedMessagesMap().containsKey(genderCase)) {
      throw new IllegalArgumentException("No message for grammtical gender case: " + genderCase);
    }
    StringBuilder sb = new StringBuilder();
    for (Part p : getGenderedMessagesMap().get(genderCase)) {
      if (p.isPlaceholder()) {
        sb.append(PH_JS_PREFIX).append(p.getJsPlaceholderName()).append(PH_JS_SUFFIX);
      } else {
        sb.append(p.getString());
      }
    }
    return sb.toString();
  }

  /**
   * Returns a single string representing the message.
   *
   * <p>In the returned string all placeholders are joined with the literal string parts in the form
   * "literal string part {CANONICAL_PLACEHOLDER_NAME} more literal string", which is how
   * placeholders are represented in ICU messages.
   *
   * @throws UnsupportedOperationException if the message has gendered variants.
   */
  public final String asIcuMessageString() {
    if (!getGenderedMessagesMap().isEmpty()) {
      throw new UnsupportedOperationException(
          "asIcuMessageString() is not supported for messages with gendered variants. Please"
              + " provide a grammatical gender case as an argument.");
    }
    StringBuilder sb = new StringBuilder();
    for (Part p : getParts()) {
      if (p.isPlaceholder()) {
        sb.append('{').append(p.getCanonicalPlaceholderName()).append('}');
      } else {
        sb.append(p.getString());
      }
    }
    return sb.toString();
  }

  /**
   * Overloaded method that returns a single string representing the message for a given gender
   * case.
   *
   * <p>In the returned string all placeholders are joined with the literal string parts in the form
   * "literal string part {CANONICAL_PLACEHOLDER_NAME} more literal string", which is how
   * placeholders are represented in ICU messages.
   *
   * @param genderCase the grammatical gender case for which to return the message string.
   * @throws IllegalArgumentException if the message does not have a message for the given gender
   *     case.
   */
  public final String asIcuMessageString(GrammaticalGenderCase genderCase) {
    if (!getGenderedMessagesMap().containsKey(genderCase)) {
      throw new IllegalArgumentException("No message for grammtical gender case: " + genderCase);
    }
    StringBuilder sb = new StringBuilder();
    for (Part p : getGenderedMessagesMap().get(genderCase)) {
      if (p.isPlaceholder()) {
        sb.append('{').append(p.getCanonicalPlaceholderName()).append('}');
      } else {
        sb.append(p.getString());
      }
    }
    return sb.toString();
  }

  /**
   * @return false iff the message is represented by empty string.
   */
  public final boolean isEmpty() {
    if (getGenderedMessagesMap().isEmpty()) {
      for (Part part : getParts()) {
        if (part.isPlaceholder() || part.getString().length() > 0) {
          return false;
        }
      }
    } else {
      for (ImmutableList<Part> parts : getGenderedMessagesMap().values()) {
        for (Part part : parts) {
          if (part.isPlaceholder() || part.getString().length() > 0) {
            return false;
          }
        }
      }
    }

    return true;
  }

  /** Represents part of a message. */
  public interface Part {
    /** True for placeholders, false for literal string parts. */
    boolean isPlaceholder();

    /**
     * Gets the name of the placeholder as it would appear in JS code.
     *
     * <p>In JS code placeholders are in lower camel case with zero or more trailing numeric
     * suffixes (e.g. myPlaceholderName_123_456).
     *
     * @return the name for a placeholder
     * @throws UnsupportedOperationException if this is not a {@link PlaceholderReference}.
     */
    String getJsPlaceholderName();

    /**
     * Gets the name of the placeholder as it would appear in XMB or XTB files, and also the form it
     * takes when generating message IDs.
     *
     * <p>This format is UPPER_SNAKE_CASE.
     *
     * @return the name for a placeholder
     * @throws UnsupportedOperationException if this is not a {@link PlaceholderReference}.
     */
    String getCanonicalPlaceholderName();

    /**
     * Gets the literal string for this message part.
     *
     * @return the literal string for {@link StringPart}.
     * @throws UnsupportedOperationException if this is not a {@link StringPart}.
     */
    String getString();
  }

  /** Represents a literal string part of a message. */
  public record StringPart(String string) implements Part {
    public StringPart {
      requireNonNull(string, "string");
    }

    @InlineMe(replacement = "this.string()")
    @Override
    public String getString() {
      return string();
    }

    public static StringPart create(String str) {
      return new StringPart(str);
    }

    @Override
    public boolean isPlaceholder() {
      return false;
    }

    @Override
    public String getJsPlaceholderName() {
      throw new UnsupportedOperationException(String.format("not a placeholder: '%s'", string()));
    }

    @Override
    public String getCanonicalPlaceholderName() {
      throw new UnsupportedOperationException(String.format("not a placeholder: '%s'", string()));
    }
  }

  /**
   * In JS code we expect placeholder names to be lowerCamelCase with an optional _123_456 suffix.
   */
  private static final Pattern JS_PLACEHOLDER_NAME_RE = Pattern.compile("[a-z][a-zA-Z\\d]*[_\\d]*");

  /**
   * Returns whether a string is nonempty, begins with a lowercase letter, and contains only digits
   * and underscores after the first underscore.
   */
  public static boolean isLowerCamelCaseWithNumericSuffixes(String input) {
    return JS_PLACEHOLDER_NAME_RE.matcher(input).matches();
  }

  /**
   * Converts the given string from upper-underscore case to lower-camel case, preserving numeric
   * suffixes. For example: "NAME" -> "name" "A4_LETTER" -> "a4Letter" "START_SPAN_1_23" ->
   * "startSpan_1_23".
   */
  public static String toLowerCamelCaseWithNumericSuffixes(String input) {
    // Determine where the numeric suffixes begin
    int suffixStart = input.length();
    while (suffixStart > 0) {
      char ch = '\0';
      int numberStart = suffixStart;
      while (numberStart > 0) {
        ch = input.charAt(numberStart - 1);
        if (Character.isDigit(ch)) {
          numberStart--;
        } else {
          break;
        }
      }
      if ((numberStart > 0) && (numberStart < suffixStart) && (ch == '_')) {
        suffixStart = numberStart - 1;
      } else {
        break;
      }
    }

    if (suffixStart == input.length()) {
      return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, input);
    } else {
      return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, input.substring(0, suffixStart))
          + input.substring(suffixStart);
    }
  }

  /**
   * In XMB and XTB files we expect the placeholder name to be in UPPER_SNAKE_CASE with optional
   * _123_456 suffix.
   *
   * <p>This pattern will allow alpha sections after numeric sections (e.g. NOT_A_1234_VALID_NAME),
   * but it is still probably good enough, since these name are usually automatically generated from
   * the JS formatted lowerCamelCase_with_optional_12_34 names.
   *
   * <p>The pattern also allows leading and trailing underscores. There are known cases of messages
   * containing those.
   */
  private static final Pattern CANONICAL_PLACEHOLDER_NAME_RE = Pattern.compile("[A-Z\\d_]*");

  /** Is the name in the canonical format for placeholder names in XTB and XMB files. */
  public static boolean isCanonicalPlaceholderNameFormat(String name) {
    return CANONICAL_PLACEHOLDER_NAME_RE.matcher(name).matches();
  }

  /** A reference to a placeholder in a translatable message. */
  public record PlaceholderReference(String storedPlaceholderName, boolean canonicalFormat)
      implements Part {
    public PlaceholderReference {
      requireNonNull(storedPlaceholderName, "storedPlaceholderName");
    }

    @InlineMe(replacement = "this.storedPlaceholderName()")
    public String getStoredPlaceholderName() {
      return storedPlaceholderName();
    }

    @InlineMe(replacement = "this.canonicalFormat()")
    public boolean isCanonicalFormat() {
      return canonicalFormat();
    }

    static PlaceholderReference createForJsName(String name) {
      checkArgument(
          isLowerCamelCaseWithNumericSuffixes(name),
          "invalid JS placeholder name format: '%s'",
          name);
      return new PlaceholderReference(name, /* canonicalFormat= */ false);
    }

    public static PlaceholderReference createForCanonicalName(String name) {
      checkArgument(
          isCanonicalPlaceholderNameFormat(name),
          "not a canonical placeholder name format: '%s'",
          name);
      return new PlaceholderReference(name, /* canonicalFormat= */ true);
    }

    @Override
    public boolean isPlaceholder() {
      return true;
    }

    @Override
    public String getJsPlaceholderName() {
      final String storedPlaceholderName = storedPlaceholderName();
      return canonicalFormat()
          ? toLowerCamelCaseWithNumericSuffixes(storedPlaceholderName)
          : storedPlaceholderName;
    }

    @Override
    public String getCanonicalPlaceholderName() {
      final String storedPlaceholderName = storedPlaceholderName();
      return canonicalFormat()
          ? storedPlaceholderName
          : CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, storedPlaceholderName);
    }

    @Override
    public String getString() {
      throw new UnsupportedOperationException(
          String.format("not a string part: '%s'", getJsPlaceholderName()));
    }
  }

  /**
   * Contains functionality for creating JS messages. Generates authoritative keys and fingerprints
   * for a message that must stay constant over time.
   *
   * <p>This implementation correctly processes unnamed messages and creates a key for them that
   * looks like {@code MSG_<fingerprint value>};.
   */
  public static final class Builder {

    private @Nullable String key = null;

    private String meaning;

    private String desc;
    private boolean isAnonymous = false;
    private boolean isExternal = false;

    private @Nullable String id;
    private @Nullable String alternateId;

    private final List<Part> parts = new ArrayList<>();
    private final Map<GrammaticalGenderCase, List<Part>> genderedMessageMap = new LinkedHashMap<>();
    // Placeholder names in JS code format (lowerCamelCase),
    // which is used for `goog.getMsg()` messages
    private final Set<String> jsPlaceholderNames = new LinkedHashSet<>();
    // Placeholder names in canonical format (UPPER_SNAKE_CASE)
    // which is used in XMB / XTB files and `declareIcuTemplate()` messages.
    private final Set<String> canonicalPlaceholderNames = new LinkedHashSet<>();
    private ImmutableMap<String, String> placeholderNameToExampleMap = ImmutableMap.of();
    private ImmutableMap<String, String> placeholderNameToOriginalCodeMap = ImmutableMap.of();

    private String sourceName;

    /** Gets the message's key (e.g. {@code "MSG_HELLO"}). */
    public String getKey() {
      return key;
    }

    /**
     * @param key a key that should uniquely identify this message; typically it is the message's
     *     name (e.g. {@code "MSG_HELLO"}).
     */
    @CanIgnoreReturnValue
    public Builder setKey(String key) {
      this.key = key;
      return this;
    }

    /**
     * @param sourceName The message's sourceName.
     */
    @CanIgnoreReturnValue
    public Builder setSourceName(String sourceName) {
      this.sourceName = sourceName;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder appendPart(Part part) {
      checkNotNull(part);
      parts.add(part);
      if (part.isPlaceholder()) {
        jsPlaceholderNames.add(part.getJsPlaceholderName());
        canonicalPlaceholderNames.add(part.getCanonicalPlaceholderName());
      }
      return this;
    }

    @CanIgnoreReturnValue
    public Builder appendParts(List<Part> parts) {
      for (Part part : parts) {
        appendPart(part);
      }
      return this;
    }

    /**
     * Appends a placeholder reference to the message.
     *
     * @param name placeholder name in the format used in JS code (lowerCamelCaseWithOptional_12_34)
     */
    @CanIgnoreReturnValue
    public Builder appendJsPlaceholderReference(String name) {
      checkNotNull(name, "Placeholder name must not be null");
      parts.add(PlaceholderReference.createForJsName(name));
      jsPlaceholderNames.add(name);
      return this;
    }

    /**
     * Overloaded method that appends a placeholder reference to the corresponding gendered message.
     *
     * @param name placeholder name in the format used in JS code (lowerCamelCaseWithOptional_12_34)
     */
    @CanIgnoreReturnValue
    public Builder appendJsPlaceholderReference(GrammaticalGenderCase genderCase, String name) {
      checkNotNull(name, "Placeholder name must not be null");
      genderedMessageMap.get(genderCase).add(PlaceholderReference.createForJsName(name));
      jsPlaceholderNames.add(name);
      return this;
    }

    /**
     * Appends a placeholder reference to the message.
     *
     * @param name placeholder name in the format used in XML files (UPPER_SNAKE_CASE_12_34)
     */
    @CanIgnoreReturnValue
    public Builder appendCanonicalPlaceholderReference(String name) {
      checkNotNull(name, "Placeholder name must not be null");
      final PlaceholderReference placeholder = PlaceholderReference.createForCanonicalName(name);
      parts.add(placeholder);
      jsPlaceholderNames.add(placeholder.getJsPlaceholderName());
      return this;
    }

    /**
     * Overloaded method that appends a placeholder reference to the corresponding gendered message.
     *
     * @param name placeholder name in the format used in XML files (UPPER_SNAKE_CASE_12_34)
     */
    @CanIgnoreReturnValue
    public Builder appendCanonicalPlaceholderReference(
        GrammaticalGenderCase genderCase, String name) {
      checkNotNull(name, "Placeholder name must not be null");
      final PlaceholderReference placeholder = PlaceholderReference.createForCanonicalName(name);
      genderedMessageMap.get(genderCase).add(placeholder);
      jsPlaceholderNames.add(placeholder.getJsPlaceholderName());
      return this;
    }

    /** Appends a translatable string literal to the message. */
    @CanIgnoreReturnValue
    public Builder appendStringPart(String part) {
      checkNotNull(part, "String part of the message must not be null");
      parts.add(StringPart.create(part));
      return this;
    }

    /**
     * Overloaded method that appends a translatable string literal to the corresponding gendered
     * message.
     */
    @CanIgnoreReturnValue
    public Builder appendStringPart(GrammaticalGenderCase genderCase, String part) {
      checkNotNull(genderCase, "Grammatical gender case must not be null");
      checkNotNull(part, "String part of the message must not be null");
      genderedMessageMap.get(genderCase).add(StringPart.create(part));
      return this;
    }

    /** Adds a gendered message key to genderedMessageParts map. */
    @CanIgnoreReturnValue
    public Builder addGenderedMessageKey(GrammaticalGenderCase part) {
      checkNotNull(part, "Gendered message key must not be null");
      genderedMessageMap.put(part, new ArrayList<>());
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setPlaceholderNameToExampleMap(Map<String, String> map) {
      this.placeholderNameToExampleMap = ImmutableMap.copyOf(map);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setPlaceholderNameToOriginalCodeMap(Map<String, String> map) {
      this.placeholderNameToOriginalCodeMap = ImmutableMap.copyOf(map);
      return this;
    }

    /** Sets the description of the message, which helps translators. */
    @CanIgnoreReturnValue
    public Builder setDesc(@Nullable String desc) {
      this.desc = desc;
      return this;
    }

    /**
     * Sets the programmer-specified meaning of this message, which forces this message to translate
     * differently.
     */
    @CanIgnoreReturnValue
    public Builder setMeaning(@Nullable String meaning) {
      this.meaning = meaning;
      return this;
    }

    /** Sets the alternate message ID, to be used if the primary ID is not yet translated. */
    @CanIgnoreReturnValue
    public Builder setAlternateId(@Nullable String alternateId) {
      this.alternateId = alternateId;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setIsAnonymous(boolean isAnonymous) {
      this.isAnonymous = isAnonymous;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setId(String id) {
      checkState(this.id == null, "id already set to '%s': cannot change it to '%s'", this.id, id);
      this.id = id;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setIsExternalMsg(boolean isExternalMsg) {
      this.isExternal = isExternalMsg;
      return this;
    }

    /** Gets whether at least one part has been appended. */
    public boolean hasParts() {
      return !parts.isEmpty();
    }

    /** Gets whether the message has gendered variants. */
    public boolean hasGenderedVariants() {
      return !genderedMessageMap.isEmpty();
    }

    public List<Part> getParts() {
      return parts;
    }

    public JsMessage build() {
      checkNotNull(key, "key has not been set");
      checkNotNull(id, "id has not been set");
      checkState(!isExternal || !isAnonymous, "a message cannot be both anonymous and external");

      // An alternate ID that points to itself is a no-op, so just omit it.
      if ((alternateId != null) && alternateId.equals(id)) {
        alternateId = null;
      }

      ImmutableMap.Builder<GrammaticalGenderCase, ImmutableList<Part>>
          immutableGenderedPartsBuilder = ImmutableMap.builder();
      for (Map.Entry<GrammaticalGenderCase, List<Part>> entry : genderedMessageMap.entrySet()) {
        immutableGenderedPartsBuilder.put(entry.getKey(), ImmutableList.copyOf(entry.getValue()));
      }
      ImmutableMap<GrammaticalGenderCase, ImmutableList<Part>> immutableGenderedMessageMap =
          immutableGenderedPartsBuilder.buildOrThrow();

      return new JsMessage(
          sourceName,
          key,
          isAnonymous,
          isExternal,
          id,
          ImmutableList.copyOf(parts),
          immutableGenderedMessageMap,
          alternateId,
          desc,
          meaning,
          placeholderNameToExampleMap,
          placeholderNameToOriginalCodeMap,
          ImmutableSet.copyOf(jsPlaceholderNames),
          ImmutableSet.copyOf(canonicalPlaceholderNames));
    }
  }

  /**
   * This class contains routines for hashing.
   *
   * <p>The hash takes a byte array representing arbitrary data (a number, String, or Object) and
   * turns it into a small, hopefully unique, number. There are additional convenience functions
   * which hash int, long, and String types.
   *
   * <p><b>Note</b>: this hash has weaknesses in the two most-significant key bits and in the three
   * least-significant seed bits. The weaknesses are small and practically speaking, will not affect
   * the distribution of hash values. Still, it would be good practice not to choose seeds 0, 1, 2,
   * 3, ..., n to yield n, independent hash functions. Use pseudo-random seeds instead.
   *
   * <p>This code is based on the work of Craig Silverstein and Sanjay Ghemawat in, then forked from
   * com.google.common.
   *
   * <p>The original code for the hash function is courtesy <a
   * href="http://burtleburtle.net/bob/hash/evahash.html">Bob Jenkins</a>.
   *
   * <p>TODO(anatol): Add stream hashing functionality.
   */
  static final class Hash {
    private Hash() {}

    /** Default hash seed (64 bit) */
    private static final long SEED64 = 0x2b992ddfa23249d6L; // part of pi, arbitrary

    /** Hash constant (64 bit) */
    private static final long CONSTANT64 = 0xe08c1d668b756f82L; // part of golden ratio, arbitrary

    /******************
     * STRING HASHING *
     ******************/

    /**
     * Hash a string to a 64 bit value. The digits of pi are used for the hash seed.
     *
     * @param value the string to hash
     * @return 64 bit hash value
     */
    static long hash64(@Nullable String value) {
      return hash64(value, SEED64);
    }

    /**
     * Hash a string to a 64 bit value using the supplied seed.
     *
     * @param value the string to hash
     * @param seed the seed
     * @return 64 bit hash value
     */
    private static long hash64(@Nullable String value, long seed) {
      if (value == null) {
        return hash64(null, 0, 0, seed);
      }
      return hash64(value.getBytes(UTF_8), seed);
    }

    /**
     * Hash byte array to a 64 bit value using the supplied seed.
     *
     * @param value the bytes to hash
     * @param seed the seed
     * @return 64 bit hash value
     */
    private static long hash64(byte[] value, long seed) {
      return hash64(value, 0, value == null ? 0 : value.length, seed);
    }

    /**
     * Hash byte array to a 64 bit value using the supplied seed.
     *
     * @param value the bytes to hash
     * @param offset the starting position of value where bytes are used for the hash computation
     * @param length number of bytes of value that are used for the hash computation
     * @param seed the seed
     * @return 64 bit hash value
     */
    private static long hash64(byte @Nullable [] value, int offset, int length, long seed) {
      long a = CONSTANT64;
      long b = a;
      long c = seed;
      int keylen;

      for (keylen = length; keylen >= 24; keylen -= 24, offset += 24) {
        a += word64At(value, offset);
        b += word64At(value, offset + 8);
        c += word64At(value, offset + 16);

        // Mix
        a -= b;
        a -= c;
        a ^= c >>> 43;
        b -= c;
        b -= a;
        b ^= a << 9;
        c -= a;
        c -= b;
        c ^= b >>> 8;
        a -= b;
        a -= c;
        a ^= c >>> 38;
        b -= c;
        b -= a;
        b ^= a << 23;
        c -= a;
        c -= b;
        c ^= b >>> 5;
        a -= b;
        a -= c;
        a ^= c >>> 35;
        b -= c;
        b -= a;
        b ^= a << 49;
        c -= a;
        c -= b;
        c ^= b >>> 11;
        a -= b;
        a -= c;
        a ^= c >>> 12;
        b -= c;
        b -= a;
        b ^= a << 18;
        c -= a;
        c -= b;
        c ^= b >>> 22;
      }

      c += length;
      if (keylen >= 16) {
        if (keylen == 23) {
          c += ((long) value[offset + 22]) << 56;
        }
        if (keylen >= 22) {
          c += (value[offset + 21] & 0xffL) << 48;
        }
        if (keylen >= 21) {
          c += (value[offset + 20] & 0xffL) << 40;
        }
        if (keylen >= 20) {
          c += (value[offset + 19] & 0xffL) << 32;
        }
        if (keylen >= 19) {
          c += (value[offset + 18] & 0xffL) << 24;
        }
        if (keylen >= 18) {
          c += (value[offset + 17] & 0xffL) << 16;
        }
        if (keylen >= 17) {
          c += (value[offset + 16] & 0xffL) << 8;
          // the first byte of c is reserved for the length
        }
        if (keylen >= 16) {
          b += word64At(value, offset + 8);
          a += word64At(value, offset);
        }
      } else if (keylen >= 8) {
        if (keylen == 15) {
          b += (value[offset + 14] & 0xffL) << 48;
        }
        if (keylen >= 14) {
          b += (value[offset + 13] & 0xffL) << 40;
        }
        if (keylen >= 13) {
          b += (value[offset + 12] & 0xffL) << 32;
        }
        if (keylen >= 12) {
          b += (value[offset + 11] & 0xffL) << 24;
        }
        if (keylen >= 11) {
          b += (value[offset + 10] & 0xffL) << 16;
        }
        if (keylen >= 10) {
          b += (value[offset + 9] & 0xffL) << 8;
        }
        if (keylen >= 9) {
          b += (value[offset + 8] & 0xffL);
        }
        if (keylen >= 8) {
          a += word64At(value, offset);
        }
      } else {
        if (keylen == 7) {
          a += (value[offset + 6] & 0xffL) << 48;
        }
        if (keylen >= 6) {
          a += (value[offset + 5] & 0xffL) << 40;
        }
        if (keylen >= 5) {
          a += (value[offset + 4] & 0xffL) << 32;
        }
        if (keylen >= 4) {
          a += (value[offset + 3] & 0xffL) << 24;
        }
        if (keylen >= 3) {
          a += (value[offset + 2] & 0xffL) << 16;
        }
        if (keylen >= 2) {
          a += (value[offset + 1] & 0xffL) << 8;
        }
        if (keylen >= 1) {
          a += (value[offset + 0] & 0xffL);
          // case 0: nothing left to add
        }
      }
      return mix64(a, b, c);
    }

    private static long word64At(byte[] bytes, int offset) {
      return (bytes[offset + 0] & 0xffL)
          + ((bytes[offset + 1] & 0xffL) << 8)
          + ((bytes[offset + 2] & 0xffL) << 16)
          + ((bytes[offset + 3] & 0xffL) << 24)
          + ((bytes[offset + 4] & 0xffL) << 32)
          + ((bytes[offset + 5] & 0xffL) << 40)
          + ((bytes[offset + 6] & 0xffL) << 48)
          + ((bytes[offset + 7] & 0xffL) << 56);
    }

    /** Mixes longs a, b, and c, and returns the final value of c. */
    private static long mix64(long a, long b, long c) {
      a -= b;
      a -= c;
      a ^= c >>> 43;
      b -= c;
      b -= a;
      b ^= a << 9;
      c -= a;
      c -= b;
      c ^= b >>> 8;
      a -= b;
      a -= c;
      a ^= c >>> 38;
      b -= c;
      b -= a;
      b ^= a << 23;
      c -= a;
      c -= b;
      c ^= b >>> 5;
      a -= b;
      a -= c;
      a ^= c >>> 35;
      b -= c;
      b -= a;
      b ^= a << 49;
      c -= a;
      c -= b;
      c ^= b >>> 11;
      a -= b;
      a -= c;
      a ^= c >>> 12;
      b -= c;
      b -= a;
      b ^= a << 18;
      c -= a;
      c -= b;
      c ^= b >>> 22;
      return c;
    }
  }

  /** ID generator */
  public interface IdGenerator {
    /**
     * Generate the ID for the message. Messages with the same messageParts and meaning will get the
     * same id. Messages with the same id will get the same translation.
     *
     * @param meaning The programmer-specified meaning. If no {@code @meaning} annotation appears,
     *     we will use the name of the variable it's assigned to. If the variable is unnamed, then
     *     we will just use a fingerprint of the message.
     * @param messageParts The parts of the message, including the main message text.
     */
    String generateId(String meaning, List<Part> messageParts);
  }
}
