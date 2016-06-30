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

package com.google.javascript.jscomp;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.primitives.UnsignedBytes;
import com.google.javascript.rhino.Node;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Holds instrumentation details related to a file, namely, the filename,
 * the array name used in instrumentation, and the lines which were
 * instrumented (in encoded form).
 * @author praveenk@google.com (Praveen Kumashi)
 */
@GwtIncompatible("com.google.common.primitives.UnsignedBytes")
class FileInstrumentationData {
  private final BitSet instrumentedBits; // Instrumented lines, a bit per line
  private final String arrayName;
  private final String fileName;

  //
  public static final class BranchIndexPair {
    private final int line;
    private final int branch;

    public int getLine() {
      return line;
    }

    public int getBranch() {
      return branch;
    }

    public BranchIndexPair(int line, int branch) {
      this.line = line;
      this.branch = branch;
    }

    public static BranchIndexPair of(int line, int branch) {
      return new BranchIndexPair(line, branch);
    }

    @Override
    public int hashCode() {
      return 31 * line + branch;
    }

    @Override
    public boolean equals(Object object) {
      if (object instanceof BranchIndexPair) {
        BranchIndexPair that = (BranchIndexPair) object;
        return this.getLine() == that.getLine()
            && this.getBranch() == that.getBranch();
      }
      return false;
    }
  }

  // branchPresent denotes the lines containing at least one branch
  // bit[i] set to 1 denotes the (i+1)-th line containing at least one branch
  // bit[i] bit set to 0 denotes the (i+1)-th line containing no branches
  private final BitSet branchPresent;
  // Number of branches in line, the index is zero-based.
  private final Map<Integer, Integer> branchesInLine;
  // Map of (line-no, branch-idx) to nodes of blocks of conditional branches.
  // The two indices are all zero-based.
  //
  // For example, if the source code has an 'if' statement on line 3 with an if and else branch,
  // the two branches will be indexed as (2, 0) and (2, 1).
  private final Map<BranchIndexPair, Node> branchNodes;

  FileInstrumentationData(String fileName, String arrayName) {
    this.fileName = fileName;
    this.arrayName = arrayName;

    instrumentedBits = new BitSet();

    branchPresent = new BitSet();
    branchesInLine = new HashMap<>();
    branchNodes = new HashMap<>();
  }

   String getArrayName() {
    return arrayName;
  }

  String getFileName() {
    return fileName;
  }
  int maxInstrumentedLine() {
    return instrumentedBits.length();
  }

  /**
   * Store a node to be instrumented later for branch coverage.
   * @param lineNumber 1-based line number
   * @param branchNumber 1-based branch number
   * @param block the node of the conditional block.
   */
  void putBranchNode(int lineNumber, int branchNumber, Node block) {
    Preconditions.checkArgument(
        lineNumber > 0, "Expected non-zero positive integer as line number: %s", lineNumber);
    Preconditions.checkArgument(
        branchNumber > 0, "Expected non-zero positive integer as branch number: %s", branchNumber);

    branchNodes.put(BranchIndexPair.of(lineNumber - 1, branchNumber - 1), block);
  }

  /**
   * Get the block node to be instrumented for branch coverage.
   * @param lineNumber 1-based line number
   * @param branchNumber 1-based branch number
   * @return the node of the conditional block.
   */
  Node getBranchNode(int lineNumber, int branchNumber) {
    Preconditions.checkArgument(
        lineNumber > 0, "Expected non-zero positive integer as line number: %s", lineNumber);
    Preconditions.checkArgument(
        branchNumber > 0, "Expected non-zero positive integer as branch number: %s", branchNumber);

    return branchNodes.get(BranchIndexPair.of(lineNumber - 1, branchNumber - 1));
  }

  /**
   * Returns a byte-wise hex string representation of the BitField from
   * MSB (Most Significant Byte) to LSB (Least Significant Byte).
   * Eg. Single byte: a setting of "0001 1111", returns "1f"
   * Eg. Multiple bytes: a setting of "0000 0010 0001 1111", returns "1f02"
   *
   * @return string representation of bits set
   */
  private static String getHexString(BitSet bitSet) {
    StringBuilder builder = new StringBuilder();

    // Build the hex string.
    for (byte byteEntry : bitSet.toByteArray()) {
      // Java bytes are signed, but we want the value as if it were unsigned.
      int value = UnsignedBytes.toInt(byteEntry);
      String hexString = Integer.toHexString(value);

      // Pad string to be two characters (if it isn't already).
      hexString = Strings.padStart(hexString, 2, '0');

      builder.append(hexString);
    }

    return builder.toString();
  }

  /** Get a hex string representation of the instrumentedBits bit vector. */
  String getInstrumentedLinesAsHexString() {
    return getHexString(instrumentedBits);
  }

  /** Get a hex string representation of the branchPresent bit vector. */
  String getBranchPresentAsHexString() {
    return getHexString(branchPresent);
  }

  /**
   * Mark given 1-based line number as instrumented. Zero, Negative numbers
   * are not allowed.
   * @param lineNumber the line number which was instrumented
   */
  void setLineAsInstrumented(int lineNumber) {
    Preconditions.checkArgument(
        lineNumber > 0, "Expected non-zero positive integer as line number: %s", lineNumber);

    // Map the 1-based line number to 0-based bit position
    instrumentedBits.set(lineNumber - 1);
  }

  /**
   * Mark a given 1-based line number has branch presented.
   * @param lineNumber the line number which has conditional branches.
   */
  void setBranchPresent(int lineNumber) {
    Preconditions.checkArgument(
        lineNumber > 0, "Expected non-zero positive integer as line number: %s", lineNumber);

    // Map the 1-based line number to 0-based bit position
    branchPresent.set(lineNumber - 1);
  }

  /**
   * Add a number of branches to a line.
   * @param lineNumber the line number that contains the branch statement.
   * @param numberOfBranches the number of branches to add to the record.
   */
  void addBranches(int lineNumber, int numberOfBranches) {
    int lineIdx = lineNumber - 1;
    Integer currentValue = branchesInLine.get(Integer.valueOf(lineIdx));
    if (currentValue == null) {
      branchesInLine.put(lineIdx, numberOfBranches);
    } else {
      branchesInLine.put(lineIdx, currentValue + numberOfBranches);
    }
  }

  /**
   * Get the number of branches on a line
   * @param lineNumber - the 1-based line number
   * @return the number of branches on the line.
   */
  int getNumBranches(int lineNumber) {
    Integer numBranches = branchesInLine.get(lineNumber - 1);
    if (numBranches == null) {
      return 0;
    } else {
      return numBranches;
    }
  }

}
