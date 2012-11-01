/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.commonjs.module.provider;

import java.io.Serializable;
import java.util.StringTokenizer;

/**
 * Breaks a "contentType; charset=encoding" MIME type into content type and
 * encoding parts.
 * @version $Id: ParsedContentType.java,v 1.3 2011/04/07 20:26:12 hannes%helma.at Exp $
 */
public final class ParsedContentType implements Serializable
{
    private static final long serialVersionUID = 1L;

    private final String contentType;
    private final String encoding;

    /**
     * Creates a new parsed content type.
     * @param mimeType the full MIME type; typically the value of the
     * "Content-Type" header of some MIME-compliant message. Can be null.
     */
    public ParsedContentType(String mimeType) {
        String contentType = null;
        String encoding = null;
        if(mimeType != null) {
            StringTokenizer tok = new StringTokenizer(mimeType, ";");
            if(tok.hasMoreTokens()) {
                contentType = tok.nextToken().trim();
                while(tok.hasMoreTokens()) {
                    String param = tok.nextToken().trim();
                    if(param.startsWith("charset=")) {
                        encoding = param.substring(8).trim();
                        int l = encoding.length();
                        if(l > 0) {
                            if(encoding.charAt(0) == '"') {
                                encoding = encoding.substring(1);
                            }
                            if(encoding.charAt(l - 1) == '"') {
                                encoding = encoding.substring(0, l - 1);
                            }
                        }
                        break;
                    }
                }
            }
        }
        this.contentType = contentType;
        this.encoding = encoding;
    }

    /**
     * Returns the content type (without charset declaration) of the MIME type.
     * @return the content type (without charset declaration) of the MIME type.
     * Can be null if the MIME type was null.
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * Returns the character encoding of the MIME type.
     * @return the character encoding of the MIME type. Can be null when it is
     * not specified.
     */
    public String getEncoding() {
        return encoding;
    }
}