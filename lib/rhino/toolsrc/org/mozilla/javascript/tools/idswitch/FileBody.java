/* -*- Mode: java; tab-width: 4; indent-tabs-mode: 1; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.javascript.tools.idswitch;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

public class FileBody {

    private static class ReplaceItem {
        ReplaceItem next;
        int begin;
        int end;
        String replacement;

        ReplaceItem(int begin, int end, String text) {
            this.begin = begin;
            this.end = end;
            this.replacement = text;
        }
    }

    private char[] buffer = new char[1 << 14]; // 16K
    private int bufferEnd;
    private int lineBegin;
    private int lineEnd;
    private int nextLineStart;

    private int lineNumber;

    ReplaceItem firstReplace;
    ReplaceItem lastReplace;


    public char[] getBuffer() { return buffer; }

    public void readData(Reader r) throws IOException {
        int capacity = buffer.length;
        int offset = 0;
        for (;;) {
            int n_read = r.read(buffer, offset, capacity - offset);
            if (n_read < 0) { break; }
            offset += n_read;
            if (capacity == offset) {
                capacity *= 2;
                char[] tmp = new char[capacity];
                System.arraycopy(buffer, 0, tmp, 0, offset);
                buffer = tmp;
            }
        }
        bufferEnd = offset;
    }

    public void writeInitialData(Writer w) throws IOException {
        w.write(buffer, 0, bufferEnd);
    }

    public void writeData(Writer w) throws IOException {
        int offset = 0;
        for (ReplaceItem x = firstReplace; x != null; x = x.next) {
            int before_replace = x.begin - offset;
            if (before_replace > 0) {
                w.write(buffer, offset, before_replace);
            }
            w.write(x.replacement);
            offset = x.end;
        }
        int tail = bufferEnd - offset;
        if (tail != 0) {
            w.write(buffer, offset, tail);
        }
    }

    public boolean wasModified() { return firstReplace != null; }

    public boolean setReplacement(int begin, int end, String text) {
        if (equals(text, buffer, begin, end)) { return false; }

        ReplaceItem item = new ReplaceItem(begin, end, text);
        if (firstReplace == null) {
            firstReplace = lastReplace = item;
        }
        else if (begin < firstReplace.begin) {
            item.next = firstReplace;
            firstReplace = item;
        }
        else {
            ReplaceItem cursor = firstReplace;
            ReplaceItem next = cursor.next;
            while (next != null) {
                if (begin < next.begin) {
                    item.next = next;
                    cursor.next = item;
                    break;
                }
                cursor = next;
                next = next.next;
            }
            if (next == null) {
                lastReplace.next = item;
            }
        }

        return true;
    }

    public int getLineNumber() { return lineNumber; }

    public int getLineBegin() { return lineBegin; }

    public int getLineEnd() { return lineEnd; }

    public void startLineLoop() {
        lineNumber = 0;
        lineBegin = lineEnd = nextLineStart = 0;
    }

    public boolean nextLine() {
        if (nextLineStart == bufferEnd) {
            lineNumber = 0; return false;
        }
        int i; int c = 0;
        for (i = nextLineStart; i != bufferEnd; ++i) {
            c = buffer[i];
            if (c == '\n' || c == '\r') { break; }
        }
        lineBegin = nextLineStart;
        lineEnd = i;
        if (i == bufferEnd) {
            nextLineStart = i;
        }
        else if (c == '\r' && i + 1 != bufferEnd && buffer[i + 1] == '\n') {
            nextLineStart = i + 2;
        }
        else {
            nextLineStart = i + 1;
        }
        ++lineNumber;
        return true;
    }

    private static boolean equals(String str, char[] array, int begin, int end)
    {
        if (str.length() == end - begin) {
            for (int i = begin, j = 0; i != end; ++i, ++j) {
                if (array[i] != str.charAt(j)) { return false; }
            }
            return true;
        }
        return false;
    }

}


