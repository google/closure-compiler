/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tools.shell;

import java.io.IOException;
import java.security.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;

import org.mozilla.javascript.*;

public class JavaPolicySecurity extends SecurityProxy
{

    @Override
    public Class<?> getStaticSecurityDomainClassInternal() {
        return ProtectionDomain.class;
    }

    private static class Loader extends ClassLoader
        implements GeneratedClassLoader
    {
        private ProtectionDomain domain;

        Loader(ClassLoader parent, ProtectionDomain domain) {
            super(parent != null ? parent : getSystemClassLoader());
            this.domain = domain;
        }

        public Class<?> defineClass(String name, byte[] data) {
            return super.defineClass(name, data, 0, data.length, domain);
        }

        public void linkClass(Class<?> cl) {
            resolveClass(cl);
        }
    }

    private static class ContextPermissions extends PermissionCollection
    {
        static final long serialVersionUID = -1721494496320750721L;

// Construct PermissionCollection that permits an action only
// if it is permitted by staticDomain and by security context of Java stack on
// the moment of constructor invocation
        ContextPermissions(ProtectionDomain staticDomain) {
            _context = AccessController.getContext();
            if (staticDomain != null) {
                _statisPermissions = staticDomain.getPermissions();
            }
            setReadOnly();
        }

        @Override
        public void add(Permission permission) {
            throw new RuntimeException("NOT IMPLEMENTED");
        }

        @Override
        public boolean implies(Permission permission) {
            if (_statisPermissions != null) {
                if (!_statisPermissions.implies(permission)) {
                    return false;
                }
            }
            try {
                _context.checkPermission(permission);
                return true;
            }catch (AccessControlException ex) {
                return false;
            }
        }

        @Override
        public Enumeration<Permission> elements()
        {
            return new Enumeration<Permission>() {
                public boolean hasMoreElements() { return false; }
                public Permission nextElement() { return null; }
            };
        }

        @Override
        public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append(getClass().getName());
            sb.append('@');
            sb.append(Integer.toHexString(System.identityHashCode(this)));
            sb.append(" (context=");
            sb.append(_context);
            sb.append(", static_permitions=");
            sb.append(_statisPermissions);
            sb.append(')');
            return sb.toString();
        }

        AccessControlContext _context;
        PermissionCollection _statisPermissions;
    }

    public JavaPolicySecurity()
    {
        // To trigger error on jdk-1.1 with lazy load
        new CodeSource(null,  (java.security.cert.Certificate[])null);
    }

    @Override
    protected void callProcessFileSecure(final Context cx,
                                         final Scriptable scope,
                                         final String filename)
    {
        AccessController.doPrivileged(new PrivilegedAction<Object>() {
            public Object run() {
                URL url = getUrlObj(filename);
                ProtectionDomain staticDomain = getUrlDomain(url);
                try {
                    Main.processFileSecure(cx, scope, url.toExternalForm(),
                                           staticDomain);
                } catch (IOException ioex) {
                    throw new RuntimeException(ioex);
                }
                return null;
            }
        });
    }

    private URL getUrlObj(String url)
    {
        URL urlObj;
        try {
            urlObj = new URL(url);
        } catch (MalformedURLException ex) {
            // Assume as Main.processFileSecure it is file, need to build its
            // URL
            String curDir = System.getProperty("user.dir");
            curDir = curDir.replace('\\', '/');
            if (!curDir.endsWith("/")) {
                curDir = curDir+'/';
            }
            try {
                URL curDirURL = new URL("file:"+curDir);
                urlObj = new URL(curDirURL, url);
            } catch (MalformedURLException ex2) {
                throw new RuntimeException
                    ("Can not construct file URL for '"+url+"':"
                     +ex2.getMessage());
            }
        }
        return urlObj;
    }

    private ProtectionDomain getUrlDomain(URL url)
    {
        CodeSource cs;
        cs = new CodeSource(url, (java.security.cert.Certificate[])null);
        PermissionCollection pc = Policy.getPolicy().getPermissions(cs);
        return new ProtectionDomain(cs, pc);
    }

    @Override
    public GeneratedClassLoader
    createClassLoader(final ClassLoader parentLoader, Object securityDomain)
    {
        final ProtectionDomain domain = (ProtectionDomain)securityDomain;
        return AccessController.doPrivileged(new PrivilegedAction<Loader>() {
            public Loader run() {
                return new Loader(parentLoader, domain);
            }
        });
    }

    @Override
    public Object getDynamicSecurityDomain(Object securityDomain)
    {
        ProtectionDomain staticDomain = (ProtectionDomain)securityDomain;
        return getDynamicDomain(staticDomain);
    }

    private ProtectionDomain getDynamicDomain(ProtectionDomain staticDomain) {
        ContextPermissions p = new ContextPermissions(staticDomain);
        ProtectionDomain contextDomain = new ProtectionDomain(null, p);
        return contextDomain;
    }

    @Override
    public Object callWithDomain(Object securityDomain,
                                 final Context cx,
                                 final Callable callable,
                                 final Scriptable scope,
                                 final Scriptable thisObj,
                                 final Object[] args)
    {
        ProtectionDomain staticDomain = (ProtectionDomain)securityDomain;
        // There is no direct way in Java to intersect permissions according
        // stack context with additional domain.
        // The following implementation first constructs ProtectionDomain
        // that allows actions only allowed by both staticDomain and current
        // stack context, and then constructs AccessController for this dynamic
        // domain.
        // If this is too slow, alternative solution would be to generate
        // class per domain with a proxy method to call to infect
        // java stack.
        // Another optimization in case of scripts coming from "world" domain,
        // that is having minimal default privileges is to construct
        // one AccessControlContext based on ProtectionDomain
        // with least possible privileges and simply call
        // AccessController.doPrivileged with this untrusted context

        ProtectionDomain dynamicDomain = getDynamicDomain(staticDomain);
        ProtectionDomain[] tmp = { dynamicDomain };
        AccessControlContext restricted = new AccessControlContext(tmp);

        PrivilegedAction<Object> action = new PrivilegedAction<Object>() {
            public Object run() {
                return callable.call(cx, scope, thisObj, args);
            }
        };

        return AccessController.doPrivileged(action, restricted);
    }
}
