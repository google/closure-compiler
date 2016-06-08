/*
 * Copyright 2016 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableMap;

/**
 * A decoder for strings encoded by the ReplaceStrings JS compiler pass. This class is immutable.
 *
 */
public final class ReplacedStringsDecoder {

  // The default place holder for replaced string arguments.
  // Note: We're not allowing this to be changed here because it's present in
  // the replacement strings in the js_storage protocol buffer.
  public static final String ARGUMENT_PLACE_HOLDER = "`";

  private final ImmutableMap<String, String> originalToNewNameMap;

  /** A null decoder that does no mapping. */
  public static final ReplacedStringsDecoder NULL_DECODER =
      new ReplacedStringsDecoder(VariableMap.fromMap(
          ImmutableMap.<String, String>of())
      );

  public ReplacedStringsDecoder(VariableMap variableMap) {
    // VariableMap is not an immutable type, so we extract the map instead of directly using it.
    this.originalToNewNameMap = ImmutableMap.copyOf(variableMap.getOriginalNameToNewNameMap());
  }

  /**
   * Decodes an encoded string from the JS Compiler ReplaceStrings pass.
   *
   * <p>An original string with args might look like this:
   * <pre>  Error('Some ' + arg1 + ' error ' + arg2 + ' message.');</pre>
   * Which gets replaced with:
   * <pre>  Error('key' + '`' + arg1 + '`' + arg2);</pre>
   * Where ` is the argument place holder. The replacement mapping would be:
   * <pre>  key → 'Some ` error ` message.'</pre>
   * Where key is some arbitrary replacement string. An encoded string,
   * with args, from the client will look like:
   * <pre>  'key`arg1`arg2'</pre>
   *
   * @param encodedStr An encoded string.
   * @return The decoded string, or the encoded string if it fails to decode.
   * @see com.google.javascript.jscomp.ReplaceStrings
   */
  public String decode(String encodedStr) {
    String[] suppliedBits = encodedStr.split(ARGUMENT_PLACE_HOLDER, -1);
    String originalStr = originalToNewNameMap.get(suppliedBits[0]);
    if (originalStr == null) {
      return encodedStr; // Failed to decode.
    }
    String[] originalBits = originalStr.split(ARGUMENT_PLACE_HOLDER, -1);
    StringBuilder sb = new StringBuilder(originalBits[0]);
    for (int i = 1; i < Math.max(originalBits.length, suppliedBits.length); i++) {
      // Replace missing bits with "-". Shouldn't happen except that we aren't
      // escaping the replacement token at the moment.
      sb.append(i < suppliedBits.length ? suppliedBits[i] : "-");
      sb.append(i < originalBits.length ? originalBits[i] : "-");
    }
    return sb.toString();
  }

}
