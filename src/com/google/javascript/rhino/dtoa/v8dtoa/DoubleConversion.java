// Copyright 2011 the V8 project authors. All rights reserved.
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above
//       copyright notice, this list of conditions and the following
//       disclaimer in the documentation and/or other materials provided
//       with the distribution.
//     * Neither the name of Google Inc. nor the names of its
//       contributors may be used to endorse or promote products derived
//       from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

// Ported to Java from V8's conversions-inl.h and double.h files.
// The original revision was r12273 from the bleeding_edge branch.

package com.google.javascript.rhino.dtoa.v8dtoa;

public final class DoubleConversion {
    private static final long kSignMask = 0x8000000000000000L;
    private static final long kExponentMask = 0x7FF0000000000000L;
    private static final long kSignificandMask = 0x000FFFFFFFFFFFFFL;
    private static final long kHiddenBit = 0x0010000000000000L;
    private static final int kPhysicalSignificandSize = 52; // Excludes the hidden bit.
    private static final int kSignificandSize = 53;
    private static final int kExponentBias = 0x3FF + kPhysicalSignificandSize;
    private static final int kDenormalExponent = -kExponentBias + 1;

    private DoubleConversion() {
    }

    private static int exponent(long d64) {
        if (isDenormal(d64))
            return kDenormalExponent;

        int biased_e = (int) ((d64 & kExponentMask) >> kPhysicalSignificandSize);
        return biased_e - kExponentBias;
    }

    private static long significand(long d64) {
        long significand = d64 & kSignificandMask;
        if (!isDenormal(d64)) {
            return significand + kHiddenBit;
        } else {
            return significand;
        }
    }

    // Returns true if the double is a denormal.
    private static boolean isDenormal(long d64) {
        return (d64 & kExponentMask) == 0;
    }

    private static int sign(long d64) {
        return (d64 & kSignMask) == 0 ? 1 : -1;
    }

    public static int doubleToInt32(double x) {
        int i = (int) x;
        if ((double) i == x) {
            return i;
        }
        long d64 = Double.doubleToLongBits(x);
        int exponent = exponent(d64);
        if (exponent <= -kSignificandSize || exponent > 31) {
            return 0;
        }
        long s = significand(d64);
        return sign(d64) * (int) (exponent < 0 ? s >> -exponent : s << exponent);
    }
}
