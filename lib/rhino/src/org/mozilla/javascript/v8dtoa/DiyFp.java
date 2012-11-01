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

// This "Do It Yourself Floating Point" class implements a floating-point number
// with a uint64 significand and an int exponent. Normalized DiyFp numbers will
// have the most significant bit of the significand set.
// Multiplication and Subtraction do not normalize their results.
// DiyFp are not designed to contain special doubles (NaN and Infinity).
class DiyFp {

    private long f;
    private int e;

    static final int kSignificandSize = 64;
    static final long kUint64MSB = 0x8000000000000000L;


    DiyFp() {
        this.f = 0;
        this.e = 0;
    }

    DiyFp(long f, int e) {
        this.f = f;
        this.e = e;
    }

    private static boolean uint64_gte(long a, long b) {
        // greater-or-equal for unsigned int64 in java-style...
        return (a == b) || ((a > b) ^ (a < 0) ^ (b < 0));
    }

    // this = this - other.
    // The exponents of both numbers must be the same and the significand of this
    // must be bigger than the significand of other.
    // The result will not be normalized.
    void subtract(DiyFp other) {
        assert (e == other.e);
        assert uint64_gte(f, other.f);
        f -= other.f;
    }

    // Returns a - b.
    // The exponents of both numbers must be the same and this must be bigger
    // than other. The result will not be normalized.
    static DiyFp minus(DiyFp a, DiyFp b) {
        DiyFp result = new DiyFp(a.f, a.e);
        result.subtract(b);
        return result;
    }


    // this = this * other.
    void multiply(DiyFp other) {
        // Simply "emulates" a 128 bit multiplication.
        // However: the resulting number only contains 64 bits. The least
        // significant 64 bits are only used for rounding the most significant 64
        // bits.
        final long kM32 = 0xFFFFFFFFL;
        long a = f >>> 32;
        long b = f & kM32;
        long c = other.f >>> 32;
        long d = other.f & kM32;
        long ac = a * c;
        long bc = b * c;
        long ad = a * d;
        long bd = b * d;
        long tmp = (bd >>> 32) + (ad & kM32) + (bc & kM32);
        // By adding 1U << 31 to tmp we round the final result.
        // Halfway cases will be round up.
        tmp += 1L << 31;
        long result_f = ac + (ad >>> 32) + (bc >>> 32) + (tmp >>> 32);
        e += other.e + 64;
        f = result_f;
    }

    // returns a * b;
    static DiyFp times(DiyFp a, DiyFp b) {
        DiyFp result = new DiyFp(a.f, a.e);
        result.multiply(b);
        return result;
    }

    void normalize() {
        assert(f != 0);
        long f = this.f;
        int e = this.e;

        // This method is mainly called for normalizing boundaries. In general
        // boundaries need to be shifted by 10 bits. We thus optimize for this case.
        final long k10MSBits = 0xFFC00000L << 32;
        while ((f & k10MSBits) == 0) {
            f <<= 10;
            e -= 10;
        }
        while ((f & kUint64MSB) == 0) {
            f <<= 1;
            e--;
        }
        this.f = f;
        this.e = e;
    }

    static DiyFp normalize(DiyFp a) {
        DiyFp result = new DiyFp(a.f, a.e);
        result.normalize();
        return result;
    }

    long f() { return f; }
    int e() { return e; }

    void setF(long new_value) { f = new_value; }
    void setE(int new_value) { e = new_value; }

    @Override
    public String toString() {
        return "[DiyFp f:" + f + ", e:" + e + "]";
    }

}
