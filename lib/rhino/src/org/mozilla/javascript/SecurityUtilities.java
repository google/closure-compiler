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
 *   Norris Boyd
 *   Igor Bukanov
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

package org.mozilla.javascript;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;

/**
 */
public class SecurityUtilities
{
    /**
     * Retrieves a system property within a privileged block. Use it only when
     * the property is used from within Rhino code and is not passed out of it.
     * @param name the name of the system property
     * @return the value of the system property
     */
    public static String getSystemProperty(final String name)
    {
        return AccessController.doPrivileged(
            new PrivilegedAction<String>()
            {
                public String run()
                {
                    return System.getProperty(name);
                }
            });
    }

    public static ProtectionDomain getProtectionDomain(final Class<?> clazz)
    {
        return AccessController.doPrivileged(
                new PrivilegedAction<ProtectionDomain>()
                {
                    public ProtectionDomain run()
                    {
                        return clazz.getProtectionDomain();
                    }
                });
    }

    /**
     * Look up the top-most element in the current stack representing a
     * script and return its protection domain. This relies on the system-wide
     * SecurityManager being an instance of {@link RhinoSecurityManager},
     * otherwise it returns <code>null</code>.
     * @return The protection of the top-most script in the current stack, or null
     */
    public static ProtectionDomain getScriptProtectionDomain() {
        final SecurityManager securityManager = System.getSecurityManager();
        if (securityManager instanceof RhinoSecurityManager) {
            return AccessController.doPrivileged(
                new PrivilegedAction<ProtectionDomain>() {
                    public ProtectionDomain run() {
                        Class c = ((RhinoSecurityManager) securityManager)
                                    .getCurrentScriptClass();
                        return c == null ? null : c.getProtectionDomain();
                    }
                }
            );
        }
        return null;
    }
}
