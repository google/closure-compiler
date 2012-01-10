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

public class CachedPowers {


    static final double kD_1_LOG2_10 = 0.30102999566398114;  //  1 / lg(10)

    static class CachedPower {
        long significand;
        short binaryExponent;
        short decimalExponent;

        CachedPower(long significand, short binaryExponent, short decimalExponent) {
            this.significand = significand;
            this.binaryExponent = binaryExponent;
            this.decimalExponent = decimalExponent;
        }
    }


    static int getCachedPower(int e, int alpha, int gamma, DiyFp c_mk) {
        int kQ = DiyFp.kSignificandSize;
        double k = Math.ceil((alpha - e + kQ - 1) * kD_1_LOG2_10);
        int index = (GRISU_CACHE_OFFSET + (int)k - 1) / CACHED_POWERS_SPACING + 1;
        CachedPower cachedPower = CACHED_POWERS[index];

        c_mk.setF(cachedPower.significand);
        c_mk.setE(cachedPower.binaryExponent);
        assert((alpha <= c_mk.e() + e) && (c_mk.e() + e <= gamma));
        return cachedPower.decimalExponent;
    }

    // Code below is converted from GRISU_CACHE_NAME(8) in file "powers-ten.h"
    // Regexp to convert this from original C++ source:
    // \{GRISU_UINT64_C\((\w+), (\w+)\), (\-?\d+), (\-?\d+)\}

    // interval between entries  of the powers cache below
    static final int CACHED_POWERS_SPACING = 8;

    static final CachedPower[] CACHED_POWERS = {
            new CachedPower(0xe61acf033d1a45dfL, (short)-1087, (short)-308),
            new CachedPower(0xab70fe17c79ac6caL, (short)-1060, (short)-300),
            new CachedPower(0xff77b1fcbebcdc4fL, (short)-1034, (short)-292),
            new CachedPower(0xbe5691ef416bd60cL, (short)-1007, (short)-284),
            new CachedPower(0x8dd01fad907ffc3cL, (short)-980, (short)-276),
            new CachedPower(0xd3515c2831559a83L, (short)-954, (short)-268),
            new CachedPower(0x9d71ac8fada6c9b5L, (short)-927, (short)-260),
            new CachedPower(0xea9c227723ee8bcbL, (short)-901, (short)-252),
            new CachedPower(0xaecc49914078536dL, (short)-874, (short)-244),
            new CachedPower(0x823c12795db6ce57L, (short)-847, (short)-236),
            new CachedPower(0xc21094364dfb5637L, (short)-821, (short)-228),
            new CachedPower(0x9096ea6f3848984fL, (short)-794, (short)-220),
            new CachedPower(0xd77485cb25823ac7L, (short)-768, (short)-212),
            new CachedPower(0xa086cfcd97bf97f4L, (short)-741, (short)-204),
            new CachedPower(0xef340a98172aace5L, (short)-715, (short)-196),
            new CachedPower(0xb23867fb2a35b28eL, (short)-688, (short)-188),
            new CachedPower(0x84c8d4dfd2c63f3bL, (short)-661, (short)-180),
            new CachedPower(0xc5dd44271ad3cdbaL, (short)-635, (short)-172),
            new CachedPower(0x936b9fcebb25c996L, (short)-608, (short)-164),
            new CachedPower(0xdbac6c247d62a584L, (short)-582, (short)-156),
            new CachedPower(0xa3ab66580d5fdaf6L, (short)-555, (short)-148),
            new CachedPower(0xf3e2f893dec3f126L, (short)-529, (short)-140),
            new CachedPower(0xb5b5ada8aaff80b8L, (short)-502, (short)-132),
            new CachedPower(0x87625f056c7c4a8bL, (short)-475, (short)-124),
            new CachedPower(0xc9bcff6034c13053L, (short)-449, (short)-116),
            new CachedPower(0x964e858c91ba2655L, (short)-422, (short)-108),
            new CachedPower(0xdff9772470297ebdL, (short)-396, (short)-100),
            new CachedPower(0xa6dfbd9fb8e5b88fL, (short)-369, (short)-92),
            new CachedPower(0xf8a95fcf88747d94L, (short)-343, (short)-84),
            new CachedPower(0xb94470938fa89bcfL, (short)-316, (short)-76),
            new CachedPower(0x8a08f0f8bf0f156bL, (short)-289, (short)-68),
            new CachedPower(0xcdb02555653131b6L, (short)-263, (short)-60),
            new CachedPower(0x993fe2c6d07b7facL, (short)-236, (short)-52),
            new CachedPower(0xe45c10c42a2b3b06L, (short)-210, (short)-44),
            new CachedPower(0xaa242499697392d3L, (short)-183, (short)-36),
            new CachedPower(0xfd87b5f28300ca0eL, (short)-157, (short)-28),
            new CachedPower(0xbce5086492111aebL, (short)-130, (short)-20),
            new CachedPower(0x8cbccc096f5088ccL, (short)-103, (short)-12),
            new CachedPower(0xd1b71758e219652cL, (short)-77, (short)-4),
            new CachedPower(0x9c40000000000000L, (short)-50, (short)4),
            new CachedPower(0xe8d4a51000000000L, (short)-24, (short)12),
            new CachedPower(0xad78ebc5ac620000L, (short)3, (short)20),
            new CachedPower(0x813f3978f8940984L, (short)30, (short)28),
            new CachedPower(0xc097ce7bc90715b3L, (short)56, (short)36),
            new CachedPower(0x8f7e32ce7bea5c70L, (short)83, (short)44),
            new CachedPower(0xd5d238a4abe98068L, (short)109, (short)52),
            new CachedPower(0x9f4f2726179a2245L, (short)136, (short)60),
            new CachedPower(0xed63a231d4c4fb27L, (short)162, (short)68),
            new CachedPower(0xb0de65388cc8ada8L, (short)189, (short)76),
            new CachedPower(0x83c7088e1aab65dbL, (short)216, (short)84),
            new CachedPower(0xc45d1df942711d9aL, (short)242, (short)92),
            new CachedPower(0x924d692ca61be758L, (short)269, (short)100),
            new CachedPower(0xda01ee641a708deaL, (short)295, (short)108),
            new CachedPower(0xa26da3999aef774aL, (short)322, (short)116),
            new CachedPower(0xf209787bb47d6b85L, (short)348, (short)124),
            new CachedPower(0xb454e4a179dd1877L, (short)375, (short)132),
            new CachedPower(0x865b86925b9bc5c2L, (short)402, (short)140),
            new CachedPower(0xc83553c5c8965d3dL, (short)428, (short)148),
            new CachedPower(0x952ab45cfa97a0b3L, (short)455, (short)156),
            new CachedPower(0xde469fbd99a05fe3L, (short)481, (short)164),
            new CachedPower(0xa59bc234db398c25L, (short)508, (short)172),
            new CachedPower(0xf6c69a72a3989f5cL, (short)534, (short)180),
            new CachedPower(0xb7dcbf5354e9beceL, (short)561, (short)188),
            new CachedPower(0x88fcf317f22241e2L, (short)588, (short)196),
            new CachedPower(0xcc20ce9bd35c78a5L, (short)614, (short)204),
            new CachedPower(0x98165af37b2153dfL, (short)641, (short)212),
            new CachedPower(0xe2a0b5dc971f303aL, (short)667, (short)220),
            new CachedPower(0xa8d9d1535ce3b396L, (short)694, (short)228),
            new CachedPower(0xfb9b7cd9a4a7443cL, (short)720, (short)236),
            new CachedPower(0xbb764c4ca7a44410L, (short)747, (short)244),
            new CachedPower(0x8bab8eefb6409c1aL, (short)774, (short)252),
            new CachedPower(0xd01fef10a657842cL, (short)800, (short)260),
            new CachedPower(0x9b10a4e5e9913129L, (short)827, (short)268),
            new CachedPower(0xe7109bfba19c0c9dL, (short)853, (short)276),
            new CachedPower(0xac2820d9623bf429L, (short)880, (short)284),
            new CachedPower(0x80444b5e7aa7cf85L, (short)907, (short)292),
            new CachedPower(0xbf21e44003acdd2dL, (short)933, (short)300),
            new CachedPower(0x8e679c2f5e44ff8fL, (short)960, (short)308),
            new CachedPower(0xd433179d9c8cb841L, (short)986, (short)316),
            new CachedPower(0x9e19db92b4e31ba9L, (short)1013, (short)324),
            new CachedPower(0xeb96bf6ebadf77d9L, (short)1039, (short)332),
            new CachedPower(0xaf87023b9bf0ee6bL, (short)1066, (short)340)
    };

    static final int GRISU_CACHE_MAX_DISTANCE = 27;
    // nb elements (8): 82

    static final int GRISU_CACHE_OFFSET = 308;


}
