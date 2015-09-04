/*
 * Copyright 2015 The Closure Compiler Authors.
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

package java.util;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayInteger;

/**
 * This implementation uses bit groups of size 32 to keep track of when bits are
 * set to true or false.  This implementation also uses the sparse nature of
 * JavaScript arrays to speed up cases when very few bits are set in a large bit
 * set.
 *
 * Since there is no speed advantage to pre-allocating array sizes in JavaScript
 * the underlying array's length is shrunk to Sun's "logical length" whenever
 * length() is called.  This length is the index of the highest true bit, plus
 * one, or zero if there are aren't any.  This may cause the size() method to
 * return a different size than in a true Java VM.
 *
 * TODO(moz): Add this to GWT. Pending changelist at:
 * https://gwt-review.googlesource.com/#/c/5771/
 */
public class BitSet {
  // To speed up certain operations this class also uses the index properties
  // of arrays as described in section 15.4 of "Standard ECMA-262" (June 1997),
  // which can currently be found here:
  // http://www.mozilla.org/js/language/E262.pdf
  //
  // 15.4 Array Objects
  // Array objects give special treatment to a certain class of property names.
  // A property name P (in the form of a string value) is an array index if and
  // only if ToString(ToUint32(P)) is equal to P and ToUint32(P) is not equal
  // to (2^32)-1.

  // checks the index range
  private static void checkIndex(int bitIndex) {
    // we only need to test for negatives, as there is no bit index too high.
    if (bitIndex < 0) {
      throw new IndexOutOfBoundsException("bitIndex < 0: " + bitIndex);
    }
  }

  // checks to ensure indexes are not negative and not in reverse order
  private static void checkRange(int fromIndex, int toIndex) {
    if (fromIndex < 0) {
      throw new IndexOutOfBoundsException("fromIndex < 0: " + fromIndex);
    }
    if (toIndex < 0) {
      throw new IndexOutOfBoundsException("toIndex < 0: " + toIndex);
    }
    if (fromIndex > toIndex) {
      throw new IndexOutOfBoundsException("fromIndex: " + fromIndex
          + " > toIndex: " + toIndex);
    }
  }

  // converts from a bit index to a word index
  private static int wordIndex(int bitIndex) {
    // 32 bits per index
    return bitIndex >>> 5;
  }

  // converts from a word index to a bit index
  private static int bitIndex(int wordIndex) {
    // 1 word index for every 32 bit indexes
    return wordIndex << 5;
  }

  // gives the word offset for a bit index
  private static int bitOffset(int bitIndex) {
    return bitIndex & 0x1f;
  }

  //
  // none of the following static method perform any bounds checking
  //

  // clears one bit
  private static void clear(JsArrayInteger array, int bitIndex) {
    int index = wordIndex(bitIndex);
    int word = getWord(array, index);
    if (word != 0) {
      // mask the correct bit out
      setWord(array, index, word & ~(1 << (bitOffset(bitIndex))));
    }
  }

  // clones the JSArrayInteger array
  private static native JsArrayInteger clone(JsArrayInteger array) /*-{
    return array.slice(0);
  }-*/;

  // flips one bit
  private static void flip(JsArrayInteger array, int bitIndex) {
    // calculate index and offset
    int index = wordIndex(bitIndex);
    int offset = bitOffset(bitIndex);

    // figure out if the bit is on or off
    int word = getWord(array, index);
    if (((word >>> offset) & 1) == 1) {
      // if on, turn it off
      setWord(array, index, word & ~(1 << offset));
    } else {
      // if off, turn it on
      array.set(index, word | (1 << offset));
    }
  }

  // gets one bit
  private static boolean get(JsArrayInteger array, int bitIndex) {
    // retrieve the bits for the given index
    int word = getWord(array, wordIndex(bitIndex));

    // shift and mask the bit out
    return ((word >>> (bitOffset(bitIndex))) & 1) == 1;
  }

  // sets one bit to true
  private static void set(JsArrayInteger array, int bitIndex) {
    int index = wordIndex(bitIndex);
    array.set(index, getWord(array, index) | (1 << (bitOffset(bitIndex))));
  }

  // sets all bits to true within the given range
  private static void set(JsArrayInteger array, int fromIndex, int toIndex) {
    int first = wordIndex(fromIndex);
    int last = wordIndex(toIndex);
    int startBit = bitOffset(fromIndex);
    int endBit = bitOffset(toIndex);

    if (first == last) {
      // set the bits in between first and last
      maskInWord(array, first, startBit, endBit);

    } else {
      // set the bits from fromIndex to the next 32 bit boundary
      if (startBit != 0) {
        maskInWord(array, first++, startBit, 32);
      }

      // set the bits from the last 32 bit boundary to the toIndex
      if (endBit != 0) {
        maskInWord(array, last, 0, endBit);
      }

      //
      // set everything in between
      //
      for (int i = first; i < last; i++) {
        array.set(i, 0xffffffff);
      }
    }
  }

  // copies a subset of the array
  private static native JsArrayInteger slice(JsArrayInteger array,
      int fromIndex, int toIndex) /*-{
    return array.slice(fromIndex, toIndex);
  }-*/;

  // trims the array to the minimum size it can without losing data
  // returns index of the last element in the array, or -1 if empty
  private static native int trimToSize(JsArrayInteger array) /*-{
    var length = array.length;
    if (length === 0) {
      return -1;
    }

    // check if the last bit is false
    var last = length - 1;
    if (array[last] !== undefined) {
      return last;
    }

    // interleave property checks and linear index checks from the end
    var biggestSeen = -1;
    for (var property in array) {

      // test the index first
      if (--last === -1) {
        return -1;
      }
      if (array[last] !== undefined) {
        return last;
      }

      // now check the property
      var number = property >>> 0;
      if (String(number) == property && number !== 0xffffffff) {
        if (number > biggestSeen) {
          biggestSeen = number;
        }
      }
    }
    array.length = biggestSeen + 1;

    return biggestSeen;
  }-*/;

  //
  // word methods use the literal index into the array, not the bit index
  //

  // deletes an element from the array
  private static native void deleteWord(JsArrayInteger array, int index) /*-{
    delete array[index];
  }-*/;

  // flips all bits stored at a certain index
  private static void flipWord(JsArrayInteger array, int index) {
    int word = getWord(array, index);
    if (word == 0) {
      array.set(index, 0xffffffff);
    } else {
      word = ~word;
      setWord(array, index, word);
    }
  }

  // flips all bits stored at a certain index within the given range
  private static void flipMaskedWord(JsArrayInteger array, int index,
      int from, int to) {
    if (from == to) {
      return;
    }
    // get the bits
    int word = getWord(array, index);
    // adjust "to" so it will shift out those bits
    to = 32 - to;
    // create a mask and XOR it in
    word ^= (((0xffffffff >>> from) << from) << to) >>> to;
    setWord(array, index, word);
  }

  // returns all bits stored at a certain index
  private static native int getWord(JsArrayInteger array, int index) /*-{
    // OR converts an undefined to 0
    return array[index] | 0;
  }-*/;

  // sets all bits to true at a certain index within the given bit range
  private static void maskInWord(JsArrayInteger array, int index,
      int from, int to) {
    // shifting by 32 is the same as shifting by 0, this check prevents that
    // from happening in addition to the obvious avoidance of extra work
    if (from != to) {
      // adjust "to" so it will shift out those bits
      to = 32 - to;
      // create a mask and OR it in
      int value = getWord(array, index);
      value |= ((0xffffffff >>> from) << (from + to)) >>> to;
      array.set(index, value);
    }
  }

  // sets all bits to false at a certain index within the given bit range
  private static void maskOutWord(JsArrayInteger array, int index,
      int from, int to) {
    int word = getWord(array, index);
    // something only happens if word has bits set
    if (word != 0) {
      // create a mask
      int mask;
      if (from != 0) {
        mask = 0xffffffff >>> (32 - from);
      } else {
        mask = 0;
      }
      // shifting by 32 is the same as shifting by 0
      if (to != 32) {
        mask |= 0xffffffff << to;
      }

      // mask it out
      word &= mask;
      setWord(array, index, word);
    }
  }

  private static native int nextSetWord(JsArrayInteger array, int index) /*-{
    // interleave property checks and linear "index" checks
    var length = array.length;
    var localMinimum = @java.lang.Integer::MAX_VALUE;
    for (var property in array) {

      // test the index first
      if (array[index] !== undefined) {
        return index;
      }
      if (++index >= length) {
        return -1;
      }

      // now check the property
      var number = property >>> 0;
      if (String(number) == property && number !== 0xffffffff) {
        if (number >= index && number < localMinimum) {
          localMinimum = number;
        }
      }
    }

    // if local minimum is what we started at, we found nothing
    if (localMinimum === @java.lang.Integer::MAX_VALUE) {
      return -1;
    }

    return localMinimum;
  }-*/;

  // sets all bits at a certain index to the given value
  private static void setWord(JsArrayInteger array, int index, int value) {
    // keep 0s out of the array
    if (value == 0) {
      deleteWord(array, index);
    } else {
      array.set(index, value);
    }
  }

  // sets the array length
  private static native void setLengthWords(JsArrayInteger array, int length) /*-{
    array.length = length;
  }-*/;

  // our array of bits
  private JsArrayInteger array;

  public BitSet() {
    // create a new array
    array = JavaScriptObject.createArray().cast();
  }

  public BitSet(int nbits) {
    this();

    // throw an exception to be consistent
    // but (do we want to be consistent?)
    if (nbits < 0) {
      throw new NegativeArraySizeException("nbits < 0: " + nbits);
    }

    // even though the array's length is loosely kept to that of Sun's "logical
    // length," this might help in some cases where code uses size() to fill in
    // bits after constructing a BitSet, or after having one passed in as a
    // parameter.
    setLengthWords(array, wordIndex(nbits + 31));
  }

  private BitSet(JsArrayInteger array) {
    this.array = array;
  }

  public void and(BitSet set) {
    // a & a is just a
    if (this == set) {
      return;
    }

    // trim the second set to avoid extra work
    trimToSize(set.array);

    // check if the length is longer than otherLength
    int otherLength = set.array.length();
    if (array.length() > otherLength) {
      // shrink the array, effectively ANDing those bits to false
      setLengthWords(array, otherLength);
    }

    // truth table
    //
    // case | a     | b     | a & b | change?
    // 1    | false | false | false | a is already false
    // 2    | false | true  | false | a is already false
    // 3    | true  | false | false | set a to false
    // 4    | true  | true  | true  | a is already true
    //
    // we only need to change something in case 3, so iterate over set a
    int index = 0;
    while ((index = nextSetWord(array, index)) != -1) {
      setWord(array, index,
          array.get(index) & getWord(set.array, index));
      index++;
    }
  }

  public void andNot(BitSet set) {
    // a & !a is false
    if (this == set) {
      // all falses result in an empty BitSet
      clear();
      return;
    }

    // trim the second set to avoid extra work
    trimToSize(array);
    int length = array.length();

    // truth table
    //
    // case | a     | b     | !b    | a & !b | change?
    // 1    | false | false | true  | false  | a is already false
    // 2    | false | true  | false | false  | a is already false
    // 3    | true  | false | true  | true   | a is already true
    // 4    | true  | true  | false | false  | set a to false
    //
    // we only need to change something in case 4
    // whenever b is true, a should be false, so iterate over set b
    int index = 0;
    while ((index = nextSetWord(set.array, index)) != -1) {
      setWord(array, index,
          getWord(array, index) & ~set.array.get(index));
      if (++index >= length) {
        // nothing further will affect anything
        break;
      }
    }
  }

  public native int cardinality() /*-{
    var count = 0;
    var array = this.@java.util.BitSet::array;
    for (var property in array) {
      var number = property >>> 0;
      if (String(number) == property && number !== 0xffffffff) {
        count += @java.lang.Integer::bitCount(I)(array[number]);
      }
    }
    return count;
  }-*/;

  public void clear() {
    // create a new array
    array = JavaScriptObject.createArray().cast();
  }

  public void clear(int bitIndex) {
    checkIndex(bitIndex);
    clear(array, bitIndex);
  }

  public void clear(int fromIndex, int toIndex) {
    checkRange(fromIndex, toIndex);

    int length = length();
    if (fromIndex >= length) {
      // nothing to do
      return;
    }

    // check to see if toIndex is greater than our array length
    if (toIndex >= length) {
      // truncate the array by setting it's length
      int newLength = wordIndex(fromIndex + 31);
      setLengthWords(array, newLength);

      // remove the extra bits off the end
      if ((bitIndex(newLength)) - fromIndex != 0) {
        maskOutWord(array, newLength - 1, bitOffset(fromIndex), 32);
      }

    } else {
      int first = wordIndex(fromIndex);
      int last = wordIndex(toIndex);
      int startBit = bitOffset(fromIndex);
      int endBit = bitOffset(toIndex);

      if (first == last) {
        // clear the bits in between first and last
        maskOutWord(array, first, startBit, endBit);

      } else {
        // clear the bits from fromIndex to the next 32 bit boundary
        if (startBit != 0) {
          maskOutWord(array, first++, startBit, 32);
        }

        // clear the bits from the last 32 bit boundary to the toIndex
        if (endBit != 0) {
          maskOutWord(array, last, 0, endBit);
        }

        //
        // delete everything in between
        //
        for (int i = first; i < last; i++) {
          deleteWord(array, i);
        }
      }
    }
  }

  public Object clone() {
    return new BitSet(clone(array));
  }

  @Override
  public boolean equals(Object obj) {
    if (this != obj) {

      if (!(obj instanceof BitSet)) {
        return false;
      }

      BitSet other = (BitSet) obj;

      int last = trimToSize(array);
      if (last != trimToSize(other.array)) {
        return false;
      }

      int index = 0;
      while ((index = nextSetWord(array, index)) != -1) {
        if (getWord(array, index) != getWord(other.array, index)) {
          return false;
        }
        index++;
      }
    }

    return true;
  }

  public void flip(int bitIndex) {
    checkIndex(bitIndex);
    flip(array, bitIndex);
  }

  public void flip(int fromIndex, int toIndex) {
    checkRange(fromIndex, toIndex);

    int length = length();

    // if we are flipping bits beyond our length, we are setting them to true
    if (fromIndex >= length) {
      set(array, fromIndex, toIndex);
      return;
    }

    // check to see if toIndex is greater than our array length
    if (toIndex >= length) {
      set(array, length, toIndex);
      toIndex = length;
    }

    int first = wordIndex(fromIndex);
    int last = wordIndex(toIndex);
    int startBit = bitOffset(fromIndex);
    int end = bitOffset(toIndex);

    if (first == last) {
      // flip the bits in between first and last
      flipMaskedWord(array, first, startBit, end);

    } else {
      // clear the bits from fromIndex to the next 32 bit boundary
      if (startBit != 0) {
        flipMaskedWord(array, first++, startBit, 32);
      }

      // clear the bits from the last 32 bit boundary to the toIndex
      if (end != 0) {
        flipMaskedWord(array, last, 0, end);
      }

      // flip everything in between
      for (int i = first; i < last; i++) {
        flipWord(array, i);
      }
    }
  }

  public boolean get(int bitIndex) {
    checkIndex(bitIndex);
    return get(array, bitIndex);
  }

  public BitSet get(int fromIndex, int toIndex) {
    checkRange(fromIndex, toIndex);

    // no need to go past our length
    toIndex = Math.min(toIndex, length());

    // this is the bit shift offset for each group of bits
    int rightShift = bitOffset(fromIndex);

    if (rightShift == 0) {
      int subFrom = wordIndex(fromIndex);
      int subTo = wordIndex(toIndex + 31);
      JsArrayInteger subSet = slice(array, subFrom, subTo);
      int leftOvers = bitOffset(toIndex);
      if (leftOvers != 0) {
        maskOutWord(subSet, subTo - subFrom - 1, leftOvers, 32);
      }
      return new BitSet(subSet);
    }

    BitSet subSet = new BitSet();

    int first = wordIndex(fromIndex);
    int last = wordIndex(toIndex);

    if (first == last) {
      // number of bits to cut from the end
      int end = 32 - (bitOffset(toIndex));
      // raw bits
      int word = getWord(array, first);
      // shift out those bits
      word = ((word << end) >>> end) >>> rightShift;
      // set it
      if (word != 0) {
        subSet.set(0, word);
      }

    } else {
      // this will hold the newly packed bits
      int current = 0;

      // this is the raw index into the sub set
      int subIndex = 0;

      // fence post, carry over initial bits
      int word = getWord(array, first++);
      current = word >>> rightShift;

      // a left shift will be used to shift our bits to the top of "current"
      int leftShift = 32 - rightShift;

      // loop through everything in the middle
      for (int i = first; i <= last; i++) {
        word = getWord(array, i);

        // shift out the bits from the top, OR them into current bits
        current |= word << leftShift;

        // flush it out
        if (current != 0) {
          subSet.array.set(subIndex, current);
        }

        // keep track of our index
        subIndex++;

        // carry over the unused bits
        current = word >>> rightShift;
      }

      // fence post, flush out the extra bits, but don't go past the "end"
      int end = 32 - (bitOffset(toIndex));
      current = (current << (rightShift + end)) >>> (rightShift + end);
      if (current != 0) {
        subSet.array.set(subIndex, current);
      }
    }

    return subSet;
  }

  /**
   * This hash is different than the one described in Sun's documentation.  The
   * described hash uses 64 bit integers and that's not practical in JavaScript.
   */
  @Override
  public int hashCode() {
    // FNV constants
    final int fnvOffset = 0x811c9dc5;
    final int fnvPrime = 0x1000193;

    // initialize
    final int last = trimToSize(array);
    int hash = fnvOffset ^ last;

    // loop over the data
    for (int i = 0; i <= last; i++) {
      int value = getWord(array, i);
      // hash one byte at a time using FNV1
      hash = (hash * fnvPrime) ^ (value & 0xff);
      hash = (hash * fnvPrime) ^ ((value >>> 8) & 0xff);
      hash = (hash * fnvPrime) ^ ((value >>> 16) & 0xff);
      hash = (hash * fnvPrime) ^ (value >>> 24);
    }

    return hash;
  }

  public boolean intersects(BitSet set) {
    int last = trimToSize(array);

    if (this == set) {
      // if it has any bits then it intersects itself
      return last != -1;
    }

    int length = set.array.length();
    int index = 0;
    while ((index = nextSetWord(array, index)) != -1) {
      if ((array.get(index) & getWord(set.array, index)) != 0) {
        return true;
      }
      if (++index >= length) {
        // nothing further can intersect
        break;
      }
    }

    return false;
  }

  public boolean isEmpty() {
    return length() == 0;
  }

  public int length() {
    int last = trimToSize(array);
    if (last == -1) {
      return 0;
    }

    // compute the position of the leftmost bit's index
    int offsets[] = { 16, 8, 4, 2, 1 };
    int bitMasks[] = { 0xffff0000, 0xff00, 0xf0, 0xc, 0x2 };
    int position = bitIndex(last) + 1;
    int word = getWord(array, last);
    for (int i = 0; i < offsets.length; i++) {
      if ((word & bitMasks[i]) != 0) {
        word >>>= offsets[i];
        position += offsets[i];
      }
    }
    return position;
  }

  public int nextClearBit(int fromIndex) {
    checkIndex(fromIndex);
    int index = wordIndex(fromIndex);

    // special case for first index
    int fromBit = fromIndex - (bitIndex(index));
    int word = getWord(array, index);
    for (int i = fromBit; i < 32; i++) {
      if ((word & (1 << i)) == 0) {
        return (bitIndex(index)) + i;
      }
    }

    // loop through the rest
    do {
      index++;
      word = getWord(array, index);
    } while (word == 0xffffffff);
    return bitIndex(index) + Integer.numberOfTrailingZeros(~word);
  }

  public int nextSetBit(int fromIndex) {
    checkIndex(fromIndex);

    int index = wordIndex(fromIndex);

    // check the current word
    int word = getWord(array, index);
    if (word != 0) {
      for (int i = bitOffset(fromIndex); i < 32; i++) {
        if ((word & (1 << i)) != 0) {
          return (bitIndex(index)) + i;
        }
      }
    }
    index++;

    // find the next set word
    trimToSize(array);
    index = nextSetWord(array, index);
    if (index == -1) {
      return -1;
    }

    // return the next set bit
    return (bitIndex(index)) + Integer.numberOfTrailingZeros(array.get(index));
  }

  public void or(BitSet set) {
    // a | a is just a
    if (this == set) {
      return;
    }

    // truth table
    //
    // case | a     | b     | a | b | change?
    // 1    | false | false | false | a is already false
    // 2    | false | true  | true  | set a to true
    // 3    | true  | false | true  | a is already true
    // 4    | true  | true  | true  | a is already true
    //
    // we only need to change something in case 2
    // case 2 only happens when b is true, so iterate over set b
    int index = 0;
    while ((index = nextSetWord(set.array, index)) != -1) {
      setWord(array, index,
          getWord(array, index) | set.array.get(index));
      index++;
    }
  }

  public void set(int bitIndex) {
    checkIndex(bitIndex);
    set(array, bitIndex);
  }

  public void set(int bitIndex, boolean value) {
    if (value == true) {
      set(bitIndex);
    } else {
      clear(bitIndex);
    }
  }

  public void set(int fromIndex, int toIndex) {
    checkRange(fromIndex, toIndex);
    set(array, fromIndex, toIndex);
  }

  public void set(int fromIndex, int toIndex, boolean value) {
    if (value == true) {
      set(fromIndex, toIndex);
    } else {
      clear(fromIndex, toIndex);
    }
  }

  public int size() {
    // the number of bytes that can fit without using "more" memory
    return bitIndex(array.length());
  }

  @Override
  public String toString() {
    // possibly faster if done in JavaScript and all numerical properties are
    // put into an array and sorted

    int length = length();
    if (length == 0) {
      // a "length" of 0 means there are no bits set to true
      return "{}";
    }

    StringBuilder sb = new StringBuilder("{");

    // at this point, there is at least one true bit, nextSetBit can not fail
    int next = nextSetBit(0);
    sb.append(next);

    // loop until nextSetBit returns -1
    while ((next = nextSetBit(next + 1)) != -1) {
      sb.append(", ");
      sb.append(next);
    }

    sb.append("}");
    return sb.toString();
  }

  public void xor(BitSet set) {
    // a ^ a is false
    if (this == set) {
      // this results in an empty BitSet
      clear();
      return;
    }

    // truth table
    //
    // case | a     | b     | a ^ b | change?
    // 1    | false | false | false | a is already false
    // 2    | false | true  | true  | set a to true
    // 3    | true  | false | true  | a is already true
    // 4    | true  | true  | false | set a to false
    //
    // we need to change something in cases 2 and 4
    // cases 2 and 4 only happen when b is true, so iterate over set b
    int index = 0;
    while ((index = nextSetWord(set.array, index)) != -1) {
      setWord(array, index,
          getWord(array, index) ^ set.array.get(index));
      index++;
    }
  }
}

