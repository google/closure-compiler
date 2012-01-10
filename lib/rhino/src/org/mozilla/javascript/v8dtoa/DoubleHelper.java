// Copyright 2010 the V8 project authors. All rights reserved.
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

// Ported to Java from Mozilla's version of V8-dtoa by Hannes Wallnoefer.
// The original revision was 67d1049b0bf9 from the mozilla-central tree.

package org.mozilla.javascript.v8dtoa;

// Helper functions for doubles.
public class DoubleHelper {

    static final long kSignMask = 0x8000000000000000L;
    static final long kExponentMask = 0x7FF0000000000000L;
    static final long kSignificandMask = 0x000FFFFFFFFFFFFFL;
    static final long kHiddenBit = 0x0010000000000000L;

    static DiyFp asDiyFp(long d64) {
        assert(!isSpecial(d64));
        return new DiyFp(significand(d64), exponent(d64));
    }

    // this->Significand() must not be 0.
    static DiyFp asNormalizedDiyFp(long d64) {
        long f = significand(d64);
        int e = exponent(d64);

        assert(f != 0);

        // The current double could be a denormal.
        while ((f & kHiddenBit) == 0) {
            f <<= 1;
            e--;
        }
        // Do the final shifts in one go. Don't forget the hidden bit (the '-1').
        f <<= DiyFp.kSignificandSize - kSignificandSize - 1;
        e -= DiyFp.kSignificandSize - kSignificandSize - 1;
        return new DiyFp(f, e);
    }

    static int exponent(long d64) {
        if (isDenormal(d64)) return kDenormalExponent;

        int biased_e = (int)(((d64 & kExponentMask) >>> kSignificandSize) & 0xffffffffL);
        return biased_e - kExponentBias;
    }

    static long significand(long d64) {
        long significand = d64 & kSignificandMask;
        if (!isDenormal(d64)) {
            return significand + kHiddenBit;
        } else {
            return significand;
        }
    }

    // Returns true if the double is a denormal.
    static boolean isDenormal(long d64) {
        return (d64 & kExponentMask) == 0L;
    }

    // We consider denormals not to be special.
    // Hence only Infinity and NaN are special.
    static boolean isSpecial(long d64) {
        return (d64 & kExponentMask) == kExponentMask;
    }

    static boolean isNan(long d64) {
        return ((d64 & kExponentMask) == kExponentMask) &&
                ((d64 & kSignificandMask) != 0L);
    }


    static boolean isInfinite(long d64) {
        return ((d64 & kExponentMask) == kExponentMask) &&
                ((d64 & kSignificandMask) == 0L);
    }


    static int sign(long d64) {
        return (d64 & kSignMask) == 0L? 1: -1;
    }


    // Returns the two boundaries of first argument.
    // The bigger boundary (m_plus) is normalized. The lower boundary has the same
    // exponent as m_plus.
    static void normalizedBoundaries(long d64, DiyFp m_minus, DiyFp m_plus) {
        DiyFp v = asDiyFp(d64);
        boolean significand_is_zero = (v.f() == kHiddenBit);
        m_plus.setF((v.f() << 1) + 1);
        m_plus.setE(v.e() - 1);
        m_plus.normalize();
        if (significand_is_zero && v.e() != kDenormalExponent) {
            // The boundary is closer. Think of v = 1000e10 and v- = 9999e9.
            // Then the boundary (== (v - v-)/2) is not just at a distance of 1e9 but
            // at a distance of 1e8.
            // The only exception is for the smallest normal: the largest denormal is
            // at the same distance as its successor.
            // Note: denormals have the same exponent as the smallest normals.
            m_minus.setF((v.f() << 2) - 1);
            m_minus.setE(v.e() - 2);
        } else {
            m_minus.setF((v.f() << 1) - 1);
            m_minus.setE(v.e() - 1);
        }
        m_minus.setF(m_minus.f() << (m_minus.e() - m_plus.e()));
        m_minus.setE(m_plus.e());
    }

    private static final int kSignificandSize = 52;  // Excludes the hidden bit.
    private static final int kExponentBias = 0x3FF + kSignificandSize;
    private static final int kDenormalExponent = -kExponentBias + 1;

}
