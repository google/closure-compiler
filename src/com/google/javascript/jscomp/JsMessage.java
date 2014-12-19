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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

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
  private final String meaning;

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
   * @param meaning The user-specified meaning of the message. May be null if
   *     the user did not specify an explicit meaning.
   */
  private JsMessage(String sourceName, String key,
      boolean isAnonymous, boolean isExternal,
      String id, List<CharSequence> parts, Set<String> placeholders,
      String desc, boolean hidden, String meaning) {

    Preconditions.checkState(key != null);
    Preconditions.checkState(id != null);

    this.key = key;
    this.id = id;
    this.parts = Collections.unmodifiableList(parts);
    this.placeholders = Collections.unmodifiableSet(placeholders);
    this.desc = desc;
    this.hidden = hidden;
    this.meaning = meaning;

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
   * Gets the meaning annotated to the message, intended to force different
   * translations.
   */
  String getMeaning() {
    return meaning;
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
    if (o == this) {
      return true;
    }
    if (!(o instanceof JsMessage)) {
      return false;
    }
    JsMessage m = (JsMessage) o;
    return id.equals(m.id) &&
           key.equals(m.key) &&
           isAnonymous == m.isAnonymous &&
           parts.equals(m.parts) &&
           (meaning == null ? m.meaning == null : meaning.equals(m.meaning)) &&
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
   * Contains functionality for creating JS messages. Generates authoritative
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

    private String meaning;

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

    /** Returns the message registered placeholders */
    public Set<String> getPlaceholders() {
      return placeholders;
    }

    /** Sets the description of the message, which helps translators. */
    public Builder setDesc(String desc) {
      this.desc = desc;
      return this;
    }

    /**
     * Sets the programmer-specified meaning of this message, which
     * forces this message to translate differently.
     */
    public Builder setMeaning(String meaning) {
      this.meaning = meaning;
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
        String defactoMeaning = meaning != null ? meaning : key;
        id = idGenerator == null ? defactoMeaning :
            idGenerator.generateId(defactoMeaning, parts);
      }

      return new JsMessage(sourceName, key, isAnonymous, isExternal, id, parts,
          placeholders, desc, hidden, meaning);
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

  /**
   * This class contains routines for hashing.
   *
   * <p>The hash takes a byte array representing arbitrary data (a
   * number, String, or Object) and turns it into a small, hopefully
   * unique, number. There are additional convenience functions which
   * hash int, long, and String types.
   *
   * <p><b>Note</b>: this hash has weaknesses in the two
   * most-significant key bits and in the three least-significant seed
   * bits. The weaknesses are small and practically speaking, will not
   * affect the distribution of hash values. Still, it would be good
   * practice not to choose seeds 0, 1, 2, 3, ..., n to yield n,
   * independent hash functions. Use pseudo-random seeds instead.
   *
   * <p>This code is based on the work of Craig Silverstein and Sanjay
   * Ghemawat in, then forked from com.google.common.
   *
   * <p>The original code for the hash function is courtesy
   * <a href="http://burtleburtle.net/bob/hash/evahash.html">Bob Jenkins</a>.
   *
   * <p>TODO(anatol): Add stream hashing functionality.
   */
  static final class Hash {
    private Hash() {}

    /** Default hash seed (64 bit) */
    private static final long SEED64 =
        0x2b992ddfa23249d6L; // part of pi, arbitrary

    /** Hash constant (64 bit) */
    private static final long CONSTANT64 =
        0xe08c1d668b756f82L; // part of golden ratio, arbitrary


    /******************
     * STRING HASHING *
     ******************/

    /**
     * Hash a string to a 64 bit value. The digits of pi are used for
     * the hash seed.
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
     * @param offset the starting position of value where bytes are
     * used for the hash computation
     * @param length number of bytes of value that are used for the
     * hash computation
     * @param seed the seed
     * @return 64 bit hash value
     */
    @SuppressWarnings("fallthrough")
    private static long hash64(
        byte[] value, int offset, int length, long seed) {
      long a = CONSTANT64;
      long b = a;
      long c = seed;
      int keylen;

      for (keylen = length; keylen >= 24; keylen -= 24, offset += 24) {
        a += word64At(value, offset);
        b += word64At(value, offset + 8);
        c += word64At(value, offset + 16);

        // Mix
        a -= b; a -= c; a ^= c >>> 43;
        b -= c; b -= a; b ^= a << 9;
        c -= a; c -= b; c ^= b >>> 8;
        a -= b; a -= c; a ^= c >>> 38;
        b -= c; b -= a; b ^= a << 23;
        c -= a; c -= b; c ^= b >>> 5;
        a -= b; a -= c; a ^= c >>> 35;
        b -= c; b -= a; b ^= a << 49;
        c -= a; c -= b; c ^= b >>> 11;
        a -= b; a -= c; a ^= c >>> 12;
        b -= c; b -= a; b ^= a << 18;
        c -= a; c -= b; c ^= b >>> 22;
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

    /**
     * Mixes longs a, b, and c, and returns the final value of c.
     */
    private static long mix64(long a, long b, long c) {
      a -= b; a -= c; a ^= c >>> 43;
      b -= c; b -= a; b ^= a << 9;
      c -= a; c -= b; c ^= b >>> 8;
      a -= b; a -= c; a ^= c >>> 38;
      b -= c; b -= a; b ^= a << 23;
      c -= a; c -= b; c ^= b >>> 5;
      a -= b; a -= c; a ^= c >>> 35;
      b -= c; b -= a; b ^= a << 49;
      c -= a; c -= b; c ^= b >>> 11;
      a -= b; a -= c; a ^= c >>> 12;
      b -= c; b -= a; b ^= a << 18;
      c -= a; c -= b; c ^= b >>> 22;
      return c;
    }
  }

  /** ID generator */
  public interface IdGenerator {
    /**
     * Generate the ID for the message. Messages with the same messageParts
     * and meaning will get the same id. Messages with the same id
     * will get the same translation.
     *
     * @param meaning The programmer-specified meaning. If no {@code @meaning}
     *     annotation appears, we will use the name of the variable it's
     *     assigned to. If the variable is unnamed, then we will just
     *     use a fingerprint of the message.
     * @param messageParts The parts of the message, including the main
     *     message text.
     */
    String generateId(String meaning, List<CharSequence> messageParts);
  }
}
