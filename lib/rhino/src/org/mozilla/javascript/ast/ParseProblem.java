/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

/**
 * Encapsulates information for a JavaScript parse error or warning.
 */
public class ParseProblem {

    public static enum Type {Error, Warning}

    private Type type;
    private String message;
    private String sourceName;
    private int offset;
    private int length;

    /**
     * Constructs a new ParseProblem.
     */
    public ParseProblem(ParseProblem.Type type, String message,
                        String sourceName, int offset, int length) {
        setType(type);
        setMessage(message);
        setSourceName(sourceName);
        setFileOffset(offset);
        setLength(length);
    }

    public ParseProblem.Type getType() {
        return type;
    }

    public void setType(ParseProblem.Type type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String msg) {
        this.message = msg;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String name) {
        this.sourceName = name;
    }

    public int getFileOffset() {
        return offset;
    }

    public void setFileOffset(int offset) {
        this.offset = offset;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(200);
        sb.append(sourceName).append(":");
        sb.append("offset=").append(offset).append(",");
        sb.append("length=").append(length).append(",");
        sb.append(type == Type.Error ? "error: " : "warning: ");
        sb.append(message);
        return sb.toString();
    }
}
