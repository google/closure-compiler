/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tools.shell;

import java.io.InputStream;
import java.nio.charset.Charset;

import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.tools.shell.ShellConsole;

/**
 * Provides a specialized input stream for consoles to handle line
 * editing, history and completion. Relies on the JLine library (see
 * <http://jline.sourceforge.net>).
 */
@Deprecated
public class ShellLine {
    @Deprecated
    public static InputStream getStream(Scriptable scope) {
        ShellConsole console = ShellConsole.getConsole(scope,
                Charset.defaultCharset());
        return (console != null ? console.getIn() : null);
    }
}
