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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.CaseFormat;
import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.JsMessage.IdGenerator;
import com.google.javascript.jscomp.JsMessage.PlaceholderReference;

import java.util.List;

/**
 * An {@link IdGenerator} designed to play nicely with Google's Translation
 * systems. Each message is scoped to a project id, so that it does
 * not conflict with other messages at Google.
 * <p>
 * Just as reminder what key type used in different formats:
 * <ol>
 * <li>XMB - id. We export using this format.
 * <li>XTB - id. Internal, result of translation.
 * <li>XLB - name. External, use it if we need to share translation with third
 *     part.
 * <li>PROPERTIES - name.
 * </ol>
 *
 * @see <a href="http://cldr.unicode.org/development/development-process/design-proposals/xmb">xmb</a>
 */
public class GoogleJsMessageIdGenerator implements IdGenerator {

  private final String projectId;

  /**
   * Creates an instance.
   *
   * @param projectId A TC project name (e.g. "MyProject")
   */
  public GoogleJsMessageIdGenerator(String projectId) {
    this.projectId = projectId;
  }

  @Override
  public String generateId(String meaning, List<CharSequence> messageParts) {
    Preconditions.checkState(meaning != null);

    StringBuilder sb = new StringBuilder();
    for (CharSequence part : messageParts) {
      if (part instanceof PlaceholderReference) {
        sb.append(CaseFormat.LOWER_CAMEL.to(
            CaseFormat.UPPER_UNDERSCORE,
            ((PlaceholderReference) part).getName()));
      } else {
        sb.append(part);
      }
    }
    String tcValue = sb.toString();

    String projectScopedMeaning =
        (projectId != null ? (projectId + ": ") : "") + meaning;
    return String.valueOf(
        MessageId.generateId(tcValue, projectScopedMeaning));
  }


  /**
   * 64-bit fingerprint support.
   *
   * Forked from the guava-internal library.
   */
  private static final class FP {
    private FP() {}

    /** Generate fingerprint of "byte[start,limit-1]". */
    private static long fingerprint(byte[] str, int start, int limit) {
      int hi = hash32(str, start, limit, 0);
      int lo = hash32(str, start, limit, 102072);
      if ((hi == 0) && (lo == 0 || lo == 1)) {
        // Turn 0/1 into another fingerprint
        hi ^= 0x130f9bef;
        lo ^= 0x94a0a928;
      }
      return (((long) hi) << 32) | (lo & 0xffffffffL);
    }

    /**
     * Generate fingerprint of "str". Equivalent to UTF-encoding "str" into
     * bytes and then fingerprinting those bytes.
     */
    private static long fingerprint(String str) {
      byte[] tmp = str.getBytes(UTF_8);
      return FP.fingerprint(tmp, 0, tmp.length);
    }

    @SuppressWarnings("fallthrough")
    private static int hash32(byte[] str, int start, int limit, int c) {
      int a = 0x9e3779b9;
      int b = 0x9e3779b9;
      int i;
      for (i = start; i + 12 <= limit; i += 12) {
        a += (((str[i + 0] & 0xff) << 0)
            | ((str[i + 1] & 0xff) << 8)
            | ((str[i + 2] & 0xff) << 16)
            | ((str[i + 3] & 0xff) << 24));
        b += (((str[i + 4] & 0xff) << 0)
            | ((str[i + 5] & 0xff) << 8)
            | ((str[i + 6] & 0xff) << 16)
            | ((str[i + 7] & 0xff) << 24));
        c += (((str[i + 8] & 0xff) << 0)
            | ((str[i + 9] & 0xff) << 8) | ((str[i + 10] & 0xff) << 16)
            | ((str[i + 11] & 0xff) << 24));

        // Mix
        a -= b;
        a -= c;
        a ^= (c >>> 13);
        b -= c;
        b -= a;
        b ^= (a << 8);
        c -= a;
        c -= b;
        c ^= (b >>> 13);
        a -= b;
        a -= c;
        a ^= (c >>> 12);
        b -= c;
        b -= a;
        b ^= (a << 16);
        c -= a;
        c -= b;
        c ^= (b >>> 5);
        a -= b;
        a -= c;
        a ^= (c >>> 3);
        b -= c;
        b -= a;
        b ^= (a << 10);
        c -= a;
        c -= b;
        c ^= (b >>> 15);
      }

      c += limit - start;
      int tmp = limit - i;
      if (tmp == 11) {
        c += (str[i + 10] & 0xff) << 24;
      }
      if (tmp >= 10) {
        c += (str[i + 9] & 0xff) << 16;
      }
      if (tmp >= 9) {
        c += (str[i + 8] & 0xff) << 8;
        // the first byte of c is reserved for the length
      }
      if (tmp >= 8) {
        b += (str[i + 7] & 0xff) << 24;
      }
      if (tmp >= 7) {
        b += (str[i + 6] & 0xff) << 16;
      }
      if (tmp >= 6) {
        b += (str[i + 5] & 0xff) << 8;
      }
      if (tmp >= 5) {
        b += (str[i + 4] & 0xff);
      }
      if (tmp >= 4) {
        a += (str[i + 3] & 0xff) << 24;
      }
      if (tmp >= 3) {
        a += (str[i + 2] & 0xff) << 16;
      }
      if (tmp >= 2) {
        a += (str[i + 1] & 0xff) << 8;
      }
      if (tmp >= 1) {
        a += (str[i + 0] & 0xff);
        // case 0 : nothing left to add
      }

      // Mix
      a -= b;
      a -= c;
      a ^= (c >>> 13);
      b -= c;
      b -= a;
      b ^= (a << 8);
      c -= a;
      c -= b;
      c ^= (b >>> 13);
      a -= b;
      a -= c;
      a ^= (c >>> 12);
      b -= c;
      b -= a;
      b ^= (a << 16);
      c -= a;
      c -= b;
      c ^= (b >>> 5);
      a -= b;
      a -= c;
      a ^= (c >>> 3);
      b -= c;
      b -= a;
      b ^= (a << 10);
      c -= a;
      c -= b;
      c ^= (b >>> 15);
      return c;
    }
  }

  /**
   * Generates fingerprint for an English message using the FP package.
   * This supersedes the message id generation using C fingerprint
   * functions and JNI.  This is slower than the C implementation (
   * we're talking about microseconds here) but it avoids using JNI and
   * shared libraries.<p>
   *
   * Forked from the i18n library.
   */
  private static class MessageId {
    private static final long generateId(String message, String meaning) {
      long fp = FP.fingerprint(message);
      if (null != meaning && meaning.length() > 0) {
        // combine the fingerprints of message and meaning
        long fp2 = FP.fingerprint(meaning);
        fp = fp2 + (fp << 1) + (fp < 0 ? 1 : 0);
      }
      // To avoid negative ids we strip the high-order bit
      return fp & 0x7fffffffffffffffL;
    }
  }
}
