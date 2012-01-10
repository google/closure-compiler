/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Hannes Wallnoefer
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.mozilla.javascript.v8dtoa;

import java.util.Arrays;

public class FastDtoaBuilder {

    // allocate buffer for generated digits + extra notation + padding zeroes
    final char[] chars = new char[FastDtoa.kFastDtoaMaximalLength + 8];
    int end = 0;
    int point;
    boolean formatted = false;

    void append(char c) {
        chars[end++] = c;
    }

    void decreaseLast() {
        chars[end - 1]--;
    }

    public void reset() {
        end = 0;
        formatted = false;
    }

    @Override
    public String toString() {
        return "[chars:" + new String(chars, 0, end) + ", point:" + point + "]";
    }

    public String format() {
        if (!formatted) {
            // check for minus sign
            int firstDigit = chars[0] == '-' ? 1 : 0;
            int decPoint = point - firstDigit;
            if (decPoint < -5 || decPoint > 21) {
                toExponentialFormat(firstDigit, decPoint);
            } else {
                toFixedFormat(firstDigit, decPoint);
            }
            formatted = true;
        }
        return new String(chars, 0, end);

    }

    private void toFixedFormat(int firstDigit, int decPoint) {
        if (point < end) {
            // insert decimal point
            if (decPoint > 0) {
                // >= 1, split decimals and insert point
                System.arraycopy(chars, point, chars, point + 1, end - point);
                chars[point] = '.';
                end++;
            } else {
                // < 1,
                int target = firstDigit + 2 - decPoint;
                System.arraycopy(chars, firstDigit, chars, target, end - firstDigit);
                chars[firstDigit] = '0';
                chars[firstDigit + 1] = '.';
                if (decPoint < 0) {
                    Arrays.fill(chars, firstDigit + 2, target, '0');
                }
                end += 2 - decPoint;
            }
        } else if (point > end) {
            // large integer, add trailing zeroes
            Arrays.fill(chars, end, point, '0');
            end += point - end;
        }
    }

    private void toExponentialFormat(int firstDigit, int decPoint) {
        if (end - firstDigit > 1) {
            // insert decimal point if more than one digit was produced
            int dot = firstDigit + 1;
            System.arraycopy(chars, dot, chars, dot + 1, end - dot);
            chars[dot] = '.';
            end++;
        }
        chars[end++] = 'e';
        char sign = '+';
        int exp = decPoint - 1;
        if (exp < 0) {
            sign = '-';
            exp = -exp;
        }
        chars[end++] = sign;

        int charPos = exp > 99 ? end + 2 : exp > 9 ? end + 1 : end;
        end = charPos + 1;

        // code below is needed because Integer.getChars() is not public
        for (;;) {
            int r = exp % 10;
            chars[charPos--] = digits[r];
            exp = exp / 10;
            if (exp == 0) break;
        }
    }

    final static char[] digits = {
        '0' , '1' , '2' , '3' , '4' , '5' , '6' , '7' , '8' , '9'
    };
}
