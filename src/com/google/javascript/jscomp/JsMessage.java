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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

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
 */
@AutoValue
public abstract class JsMessage {

  private static final String PH_JS_PREFIX = "{$";
  private static final String PH_JS_SUFFIX = "}";

  /**
   * Thrown when parsing a message string into parts fails because of a misformatted place holder.
   */
  public static final class PlaceholderFormatException extends Exception {

    public PlaceholderFormatException() {}
  }

  /**
   * Message style that could be used for JS code parsing. The enum order is from most relaxed to
   * most restricted.
   */
  public enum Style {
    LEGACY, // All legacy code is completely OK
    RELAX, // You allowed to use legacy code but it would be reported as warn
    CLOSURE; // Any legacy code is prohibited

  }

  private static final String MESSAGE_REPRESENTATION_FORMAT = "{$%s}";

  /** Gets the message's sourceName. */
  @Nullable
  public abstract String getSourceName();

  /** Gets the message's key, or name (e.g. {@code "MSG_HELLO"}). */
  public abstract String getKey();

  public abstract boolean isAnonymous();

  public abstract boolean isExternal();

  /** Gets the message's id, or name (e.g. {@code "92430284230902938293"}). */
  public abstract String getId();

  /**
   * Gets a read-only list of the parts of this message. Each part is either a {@link String} or a
   * {@link PlaceholderReference}.
   */
  public abstract ImmutableList<CharSequence> getParts();

  /**
   * Gets the message's alternate ID (e.g. {@code "92430284230902938293"}), if available. This will
   * be used if a translation for `id` is not available.
   */
  @Nullable
  public abstract String getAlternateId();

  /**
   * Gets the description associated with this message, intended to help translators, or null if
   * this message has no description.
   */
  @Nullable
  public abstract String getDesc();

  /** Gets the meaning annotated to the message, intended to force different translations. */
  @Nullable
  public abstract String getMeaning();

  /**
   * Gets whether this message should be hidden from volunteer translators (to reduce the chances of
   * a new feature leak).
   */
  public abstract boolean isHidden();

  /**
   * Gets a list of the parts of this message. Each part is either a {@link String} or a {@link
   * PlaceholderReference}.
   *
   * @deprecated use {@link #getParts()}} instead
   */
  @Deprecated
  public final ImmutableList<CharSequence> parts() {
    return getParts();
  }

  /** Gets a set of the registered placeholders in this message. */
  public abstract ImmutableSet<String> placeholders();

  /** Returns a String containing the original message text. */
  @Override
  public final String toString() {
    StringBuilder sb = new StringBuilder();
    for (CharSequence p : getParts()) {
      sb.append(p.toString());
    }
    return sb.toString();
  }

  /** @return false iff the message is represented by empty string. */
  public final boolean isEmpty() {
    for (CharSequence part : getParts()) {
      if (part.length() > 0) {
        return false;
      }
    }

    return true;
  }

  /** A reference to a placeholder in a translatable message. */
  @AutoValue
  public abstract static class PlaceholderReference implements CharSequence {

    static PlaceholderReference create(String name) {
      return new AutoValue_JsMessage_PlaceholderReference(name);
    }

    @Override
    public int length() {
      return getName().length();
    }

    @Override
    public char charAt(int index) {
      return getName().charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
      return getName().subSequence(start, end);
    }

    public abstract String getName();

    @Override
    public final String toString() {
      return SimpleFormat.format(MESSAGE_REPRESENTATION_FORMAT, getName());
    }
  }

  /**
   * Contains functionality for creating JS messages. Generates authoritative keys and fingerprints
   * for a message that must stay constant over time.
   *
   * <p>This implementation correctly processes unnamed messages and creates a key for them that
   * looks like {@code MSG_<fingerprint value>};.
   */
  @GwtIncompatible("java.util.regex")
  public static final class Builder {

    // Allow arbitrary suffixes to allow for local variable disambiguation.
    private static final String MSG_EXTERNAL_PREFIX = "MSG_EXTERNAL_";

    /** @return an external message id or null if this is not an external message identifier */
    private static String getExternalMessageId(String identifier) {
      if (identifier.startsWith(MSG_EXTERNAL_PREFIX)) {
        int start = MSG_EXTERNAL_PREFIX.length();
        int end = start;
        for (; end < identifier.length(); end++) {
          char c = identifier.charAt(end);
          if (c > '9' || c < '0') {
            break;
          }
        }
        if (end > start) {
          return identifier.substring(start, end);
        }
      }
      return null;
    }

    private String key;

    private String meaning;

    private String desc;
    private boolean hidden;

    private String alternateId;

    private final List<CharSequence> parts = new ArrayList<>();
    private final Set<String> placeholders = new HashSet<>();

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
     * @param key a key that should uniquely identify this message; typically it is the message's
     *     name (e.g. {@code "MSG_HELLO"}).
     */
    public Builder setKey(String key) {
      this.key = key;
      return this;
    }

    /** @param sourceName The message's sourceName. */
    public Builder setSourceName(String sourceName) {
      this.sourceName = sourceName;
      return this;
    }

    public Builder setMsgText(String msgText) throws PlaceholderFormatException {
      checkState(this.parts.isEmpty(), "cannot parse msg text after adding parts");
      parseMsgTextIntoParts(msgText);
      return this;
    }

    private void parseMsgTextIntoParts(String msgText) throws PlaceholderFormatException {
      while (true) {
        int phBegin = msgText.indexOf(PH_JS_PREFIX);
        if (phBegin < 0) {
          // Just a string literal
          appendStringPart(msgText);
          return;
        } else {
          if (phBegin > 0) {
            // A string literal followed by a placeholder
            appendStringPart(msgText.substring(0, phBegin));
          }

          // A placeholder. Find where it ends
          int phEnd = msgText.indexOf(PH_JS_SUFFIX, phBegin);
          if (phEnd < 0) {
            throw new PlaceholderFormatException();
          }

          String phName = msgText.substring(phBegin + PH_JS_PREFIX.length(), phEnd);
          appendPlaceholderReference(phName);
          int nextPos = phEnd + PH_JS_SUFFIX.length();
          if (nextPos < msgText.length()) {
            // Iterate on the rest of the message value
            msgText = msgText.substring(nextPos);
          } else {
            // The message is parsed
            return;
          }
        }
      }
    }

    /** Appends a placeholder reference to the message */
    public Builder appendPlaceholderReference(String name) {
      checkNotNull(name, "Placeholder name could not be null");
      parts.add(PlaceholderReference.create(name));
      placeholders.add(name);
      return this;
    }

    /** Appends a translatable string literal to the message. */
    public Builder appendStringPart(String part) {
      checkNotNull(part, "String part of the message could not be null");
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
     * Sets the programmer-specified meaning of this message, which forces this message to translate
     * differently.
     */
    public Builder setMeaning(String meaning) {
      this.meaning = meaning;
      return this;
    }

    /** Sets the alternate message ID, to be used if the primary ID is not yet translated. */
    public Builder setAlternateId(String alternateId) {
      this.alternateId = alternateId;
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
        id = idGenerator == null ? defactoMeaning : idGenerator.generateId(defactoMeaning, parts);
      }

      // An alternate ID that points to itself is a no-op, so just omit it.
      if ((alternateId != null) && alternateId.equals(id)) {
        alternateId = null;
      }

      return new AutoValue_JsMessage(
          sourceName,
          key,
          isAnonymous,
          isExternal,
          id,
          ImmutableList.copyOf(parts),
          alternateId,
          desc,
          meaning,
          hidden,
          ImmutableSet.copyOf(placeholders));
    }

    /**
     * Generates a compact uppercase alphanumeric text representation of a 63-bit fingerprint of the
     * content parts of a message.
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
      return Ascii.toUpperCase(Long.toString(nonnegativeHash, 36));
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
    @SuppressWarnings("fallthrough")
    private static long hash64(byte[] value, int offset, int length, long seed) {
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
    String generateId(String meaning, List<CharSequence> messageParts);
  }
}
