/*
 * Copyright 2006 Google Inc.
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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.base.Hash;
import com.google.common.base.Preconditions;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A representation of a translatable message in JavaScript source code.
 *
 * <p>Instances are created using a {@link JsMessage.Builder},
 * like this:
 * <pre>
 * JsMessage m = new JsMessage.Builder(key)
 *     .appendPart("Hi ")
 *     .appendPlaceholderReference("firstName")
 *     .appendPart("!")
 *     .setDesc("A welcome message")
 *     .build();
 * </pre>
 *
*
 * @author anatol@google.com (Anatol Pomazau)
 */
public class JsMessage {

  /**
   * Message style that could be used for JS code parsing.
   * The enum order is from most relaxed to most restricted.
   */
  public enum Style {
    LEGACY, // All legacy code is completely OK
    RELAX,  // You allowed to use legacy code but it would be reported as warn
    CLOSURE; // Any legacy code is prohibited

    /**
     * Calculates current messages {@link Style} based on the given arguments.
     *
     * @param useClosure if true then use closure style, otherwise not
     * @param allowLegacyMessages if true then allow legacy messages otherwise
     *        not
     * @return the message style based on the given arguments
     */
    static Style getFromParams(boolean useClosure,
        boolean allowLegacyMessages) {
      if (useClosure) {
        return allowLegacyMessages ? RELAX : CLOSURE;
      } else {
        return LEGACY;
      }
    }
  }

  private static final String MESSAGE_REPRESENTATION_FORMAT = "{$%s}";

  private final String key;
  private final String id;
  private final List<CharSequence> parts;
  private final Set<String> placeholders;
  private final String desc;
  private final boolean hidden;

  private final String sourceName;
  private final boolean isAnonymous;
  private final boolean isExternal;

  /**
   * Creates an instance. Client code should use a {@link JsMessage.Builder}.
   *
   * @param key a key that should identify this message in sources; typically
   *     it is the message's name (e.g. {@code "MSG_HELLO"}).
   * @param id an id that *uniquely* identifies the message in the bundle.
   *     It could be either the message name or id generated from the message
   *     content.
   */
  private JsMessage(String sourceName, String key,
      boolean isAnonymous, boolean isExternal,
      String id, List<CharSequence> parts, Set<String> placeholders,
      String desc, boolean hidden) {

    Preconditions.checkState(key != null);
    Preconditions.checkState(id != null);

    this.key = key;
    this.id = id;
    this.parts = Collections.unmodifiableList(parts);
    this.placeholders = Collections.unmodifiableSet(placeholders);
    this.desc = desc;
    this.hidden = hidden;

    this.sourceName = sourceName;
    this.isAnonymous = isAnonymous;
    this.isExternal = isExternal;
  }

  /**
   * Gets the message's sourceName.
   */
  public String getSourceName() {
    return sourceName;
  }

  /**
   * Gets the message's key, or name (e.g. {@code "MSG_HELLO"}).
   */
  public String getKey() {
    return key;
  }

  public boolean isAnonymous() {
    return isAnonymous;
  }

  public boolean isExternal() {
    return isExternal;
  }

  /**
   * Gets the message's id, or name (e.g. {@code "92430284230902938293"}).
   */
  public String getId() {
    return id;
  }

  /**
   * Gets the description associated with this message, intended to help
   * translators, or null if this message has no description.
   */
  public String getDesc() {
    return desc;
  }

  /**
   * Gets whether this message should be hidden from volunteer translators (to
   * reduce the chances of a new feature leak).
   */
  public boolean isHidden() {
    return hidden;
  }

  /**
   * Gets a read-only list of the parts of this message. Each part is either a
   * {@link String} or a {@link PlaceholderReference}.
   */
  public List<CharSequence> parts() {
    return parts;
  }

  /** Gets a read-only set of the registered placeholders in this message. */
  public Set<String> placeholders() {
    return placeholders;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (CharSequence p : parts) {
      sb.append(p.toString());
    }
    return sb.toString();
  }

  /** @return false iff the message is represented by empty string. */
  public boolean isEmpty() {
    for (CharSequence part : parts) {
      if (part.length() > 0) {
        return false;
      }
    }

    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof JsMessage)) return false;

    JsMessage m = (JsMessage) o;
    return id.equals(m.id) &&
           key.equals(m.key) &&
           isAnonymous == m.isAnonymous &&
           parts.equals(m.parts) &&
           placeholders.equals(m.placeholders) &&
           (desc == null ? m.desc == null : desc.equals(m.desc)) &&
           (sourceName == null
               ? m.sourceName == null
               : sourceName.equals(m.sourceName)) &&
           hidden == m.hidden;
  }

  @Override
  public int hashCode() {
    int hash = key.hashCode();
    hash = 31 * hash + (isAnonymous ? 1 : 0);
    hash = 31 * hash + id.hashCode();
    hash = 31 * hash + parts.hashCode();
    hash = 31 * hash + (desc != null ? desc.hashCode() : 0);
    hash = 31 * hash + (hidden ? 1 : 0);
    hash = 31 * hash + (sourceName != null ? sourceName.hashCode() : 0);
    return hash;
  }

  /** A reference to a placeholder in a translatable message. */
  public static class PlaceholderReference implements CharSequence {

    private final String name;

    PlaceholderReference(String name) {
      this.name = name;
    }

    @Override
    public int length() {
      return name.length();
    }

    @Override
    public char charAt(int index) {
      return name.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
      return name.subSequence(start, end);
    }

    public String getName() {
      return name;
    }

    @Override
    public String toString() {
      return String.format(MESSAGE_REPRESENTATION_FORMAT, name);
    }

    @Override
    public boolean equals(Object o) {
      return o == this ||
             o instanceof PlaceholderReference &&
             name.equals(((PlaceholderReference) o).name);
    }

    @Override
    public int hashCode() {
      return 31 * name.hashCode();
    }
  }

  /**
   * Contains functionality for creating js messages. Generates authoritative
   * keys and fingerprints for a message that must stay constant over time.
   *
   * This implementation correctly processes unnamed messages and creates a key
   * for them that looks like MSG_<fingerprint value>.
   */
  public static class Builder {

    private static final Pattern MSG_EXTERNAL_PATTERN =
        Pattern.compile("MSG_EXTERNAL_(\\d+)");

    /**
     * @return an external message id or null if this is not an
     * external message identifier
     */
    private static String getExternalMessageId(String identifier) {
      Matcher m = MSG_EXTERNAL_PATTERN.matcher(identifier);
      return m.matches() ? m.group(1) : null;
    }

    private String key;
    private String desc;
    private boolean hidden;

    private List<CharSequence> parts = Lists.newLinkedList();
    private Set<String> placeholders = Sets.newHashSet();

    private String sourceName;

    public Builder() {
      this(null);
    }

    /** Creates an instance. */
    public Builder(String key) {
      this.key = key;
    }

    /** Gets the message's key (e.g. {@code "MSG_HELLO"}). */
    public String getKey() {
      return key;
    }

    /**
     * @param key a key that should uniquely identify this message; typically
     *     it is the message's name (e.g. {@code "MSG_HELLO"}).
     */
    public Builder setKey(String key) {
      this.key = key;
      return this;
    }

    /**
     * @param sourceName The message's sourceName.
     */
    public Builder setSourceName(String sourceName) {
      this.sourceName = sourceName;
      return this;
    }

    /**
     * Appends a placeholder reference to the message
     */
    public Builder appendPlaceholderReference(String name) {
      Preconditions.checkNotNull(name, "Placeholder name could not be null");
      parts.add(new PlaceholderReference(name));
      placeholders.add(name);
      return this;
    }

    /** Appends a translatable string literal to the message. */
    public Builder appendStringPart(String part) {
      Preconditions.checkNotNull(part,
          "String part of the message could not be null");
      parts.add(part);
      return this;
    }

    /** Returns the message registred placeholders */
    public Set<String> getPlaceholders() {
      return placeholders;
    }

    /** Sets the description of the message, which helps translators. */
    public Builder setDesc(String desc) {
      this.desc = desc;
      return this;
    }

    /** Sets whether the message should be hidden from volunteer translators. */
    public Builder setIsHidden(boolean hidden) {
      this.hidden = hidden;
      return this;
    }

    /** Gets whether at least one part has been appended. */
    public boolean hasParts() {
      return !parts.isEmpty();
    }

    public List<CharSequence> getParts() {
      return parts;
    }

    public JsMessage build() {
      return build(null);
    }

    public JsMessage build(IdGenerator idGenerator) {
      boolean isAnonymous = false;
      boolean isExternal = false;
      String id = null;

      if (getKey() == null) {
        // Before constructing a message we need to change unnamed messages name
        // to the unique one.
        key = JsMessageVisitor.MSG_PREFIX + fingerprint(getParts());
        isAnonymous = true;
      }

      if (!isAnonymous) {
        String externalId = getExternalMessageId(key);
        if (externalId != null) {
          isExternal = true;
          id = externalId;
        }
      }

      if (!isExternal) {
        id = idGenerator == null ? key : idGenerator.generateId(key, parts);
      }

      return new JsMessage(sourceName, key, isAnonymous, isExternal, id, parts,
          placeholders, desc, hidden);
    }

    /**
     * Generates a compact uppercase alphanumeric text representation of a
     * 63-bit fingerprint of the content parts of a message.
     */
    private static String fingerprint(List<CharSequence> messageParts) {
      StringBuilder sb = new StringBuilder();
      for (CharSequence part : messageParts) {
        if (part instanceof JsMessage.PlaceholderReference) {
          sb.append(part.toString());
        } else {
          sb.append(part);
        }
      }
      long nonnegativeHash = Long.MAX_VALUE & Hash.hash64(sb.toString());
      return Long.toString(nonnegativeHash, 36).toUpperCase();
    }
  }
  public interface IdGenerator {

    String generateId(String key, List<CharSequence> messageParts);
  }
}
