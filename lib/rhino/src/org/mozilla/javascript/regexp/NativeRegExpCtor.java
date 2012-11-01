/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.regexp;

import org.mozilla.javascript.*;

/**
 * This class implements the RegExp constructor native object.
 *
 * Revision History:
 * Implementation in C by Brendan Eich
 * Initial port to Java by Norris Boyd from jsregexp.c version 1.36
 * Merged up to version 1.38, which included Unicode support.
 * Merged bug fixes in version 1.39.
 * Merged JSFUN13_BRANCH changes up to 1.32.2.11
 *
 */
class NativeRegExpCtor extends BaseFunction
{
    static final long serialVersionUID = -5733330028285400526L;

    NativeRegExpCtor()
    {
    }

    @Override
    public String getFunctionName()
    {
        return "RegExp";
    }

    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj,
                       Object[] args)
    {
        if (args.length > 0 && args[0] instanceof NativeRegExp &&
            (args.length == 1 || args[1] == Undefined.instance))
        {
            return args[0];
        }
        return construct(cx, scope, args);
    }

    @Override
    public Scriptable construct(Context cx, Scriptable scope, Object[] args)
    {
        NativeRegExp re = new NativeRegExp();
        re.compile(cx, scope, args);
        ScriptRuntime.setBuiltinProtoAndParent(re, scope, TopLevel.Builtins.RegExp);
        return re;
    }

    private static RegExpImpl getImpl()
    {
        Context cx = Context.getCurrentContext();
        return (RegExpImpl) ScriptRuntime.getRegExpProxy(cx);
    }

// #string_id_map#

    private static final int
        Id_multiline     = 1,
        Id_STAR          = 2,  // #string=$*#

        Id_input         = 3,
        Id_UNDERSCORE    = 4,  // #string=$_#

        Id_lastMatch     = 5,
        Id_AMPERSAND     = 6,  // #string=$&#

        Id_lastParen     = 7,
        Id_PLUS          = 8,  // #string=$+#

        Id_leftContext   = 9,
        Id_BACK_QUOTE    = 10, // #string=$`#

        Id_rightContext  = 11,
        Id_QUOTE         = 12, // #string=$'#

        DOLLAR_ID_BASE   = 12;

    private static final int
        Id_DOLLAR_1 = DOLLAR_ID_BASE + 1, // #string=$1#
        Id_DOLLAR_2 = DOLLAR_ID_BASE + 2, // #string=$2#
        Id_DOLLAR_3 = DOLLAR_ID_BASE + 3, // #string=$3#
        Id_DOLLAR_4 = DOLLAR_ID_BASE + 4, // #string=$4#
        Id_DOLLAR_5 = DOLLAR_ID_BASE + 5, // #string=$5#
        Id_DOLLAR_6 = DOLLAR_ID_BASE + 6, // #string=$6#
        Id_DOLLAR_7 = DOLLAR_ID_BASE + 7, // #string=$7#
        Id_DOLLAR_8 = DOLLAR_ID_BASE + 8, // #string=$8#
        Id_DOLLAR_9 = DOLLAR_ID_BASE + 9, // #string=$9#

        MAX_INSTANCE_ID = DOLLAR_ID_BASE + 9;

    @Override
    protected int getMaxInstanceId()
    {
        return super.getMaxInstanceId() + MAX_INSTANCE_ID;
    }

    @Override
    protected int findInstanceIdInfo(String s) {
        int id;
// #generated# Last update: 2001-05-24 16:09:31 GMT+02:00
        L0: { id = 0; String X = null; int c;
            L: switch (s.length()) {
            case 2: switch (s.charAt(1)) {
                case '&': if (s.charAt(0)=='$') {id=Id_AMPERSAND; break L0;} break L;
                case '\'': if (s.charAt(0)=='$') {id=Id_QUOTE; break L0;} break L;
                case '*': if (s.charAt(0)=='$') {id=Id_STAR; break L0;} break L;
                case '+': if (s.charAt(0)=='$') {id=Id_PLUS; break L0;} break L;
                case '1': if (s.charAt(0)=='$') {id=Id_DOLLAR_1; break L0;} break L;
                case '2': if (s.charAt(0)=='$') {id=Id_DOLLAR_2; break L0;} break L;
                case '3': if (s.charAt(0)=='$') {id=Id_DOLLAR_3; break L0;} break L;
                case '4': if (s.charAt(0)=='$') {id=Id_DOLLAR_4; break L0;} break L;
                case '5': if (s.charAt(0)=='$') {id=Id_DOLLAR_5; break L0;} break L;
                case '6': if (s.charAt(0)=='$') {id=Id_DOLLAR_6; break L0;} break L;
                case '7': if (s.charAt(0)=='$') {id=Id_DOLLAR_7; break L0;} break L;
                case '8': if (s.charAt(0)=='$') {id=Id_DOLLAR_8; break L0;} break L;
                case '9': if (s.charAt(0)=='$') {id=Id_DOLLAR_9; break L0;} break L;
                case '_': if (s.charAt(0)=='$') {id=Id_UNDERSCORE; break L0;} break L;
                case '`': if (s.charAt(0)=='$') {id=Id_BACK_QUOTE; break L0;} break L;
                } break L;
            case 5: X="input";id=Id_input; break L;
            case 9: c=s.charAt(4);
                if (c=='M') { X="lastMatch";id=Id_lastMatch; }
                else if (c=='P') { X="lastParen";id=Id_lastParen; }
                else if (c=='i') { X="multiline";id=Id_multiline; }
                break L;
            case 11: X="leftContext";id=Id_leftContext; break L;
            case 12: X="rightContext";id=Id_rightContext; break L;
            }
            if (X!=null && X!=s && !X.equals(s)) id = 0;
        }
// #/generated#

        if (id == 0) return super.findInstanceIdInfo(s);

        int attr;
        switch (id) {
          case Id_multiline:
          case Id_STAR:
          case Id_input:
          case Id_UNDERSCORE:
            attr = PERMANENT;
            break;
          default:
            attr = PERMANENT | READONLY;
            break;
        }

        return instanceIdInfo(attr, super.getMaxInstanceId() + id);
    }

// #/string_id_map#

    @Override
    protected String getInstanceIdName(int id)
    {
        int shifted = id - super.getMaxInstanceId();
        if (1 <= shifted && shifted <= MAX_INSTANCE_ID) {
            switch (shifted) {
                case Id_multiline:    return "multiline";
                case Id_STAR:         return "$*";

                case Id_input:        return "input";
                case Id_UNDERSCORE:   return "$_";

                case Id_lastMatch:    return "lastMatch";
                case Id_AMPERSAND:    return "$&";

                case Id_lastParen:    return "lastParen";
                case Id_PLUS:         return "$+";

                case Id_leftContext:  return "leftContext";
                case Id_BACK_QUOTE:   return "$`";

                case Id_rightContext: return "rightContext";
                case Id_QUOTE:        return "$'";
            }
            // Must be one of $1..$9, convert to 0..8
            int substring_number = shifted - DOLLAR_ID_BASE - 1;
            char[] buf = { '$', (char)('1' + substring_number) };
            return new String(buf);
        }
        return super.getInstanceIdName(id);
    }

    @Override
    protected Object getInstanceIdValue(int id)
    {
        int shifted = id - super.getMaxInstanceId();
        if (1 <= shifted && shifted <= MAX_INSTANCE_ID) {
            RegExpImpl impl = getImpl();
            Object stringResult;
            switch (shifted) {
              case Id_multiline:
              case Id_STAR:
                return ScriptRuntime.wrapBoolean(impl.multiline);

              case Id_input:
              case Id_UNDERSCORE:
                stringResult = impl.input;
                break;

              case Id_lastMatch:
              case Id_AMPERSAND:
                stringResult = impl.lastMatch;
                break;

              case Id_lastParen:
              case Id_PLUS:
                stringResult = impl.lastParen;
                break;

              case Id_leftContext:
              case Id_BACK_QUOTE:
                stringResult = impl.leftContext;
                break;

              case Id_rightContext:
              case Id_QUOTE:
                stringResult = impl.rightContext;
                break;

              default:
                {
                    // Must be one of $1..$9, convert to 0..8
                    int substring_number = shifted - DOLLAR_ID_BASE - 1;
                    stringResult = impl.getParenSubString(substring_number);
                    break;
                }
            }
            return (stringResult == null) ? "" : stringResult.toString();
        }
        return super.getInstanceIdValue(id);
    }

    @Override
    protected void setInstanceIdValue(int id, Object value)
    {
        int shifted = id - super.getMaxInstanceId();
        switch (shifted) {
            case Id_multiline:
            case Id_STAR:
                getImpl().multiline = ScriptRuntime.toBoolean(value);
                return;

            case Id_input:
            case Id_UNDERSCORE:
                getImpl().input = ScriptRuntime.toString(value);
                return;

            case Id_lastMatch:
            case Id_AMPERSAND:
            case Id_lastParen:
            case Id_PLUS:
            case Id_leftContext:
            case Id_BACK_QUOTE:
            case Id_rightContext:
            case Id_QUOTE:
                return;
            default:
                int substring_number = shifted - DOLLAR_ID_BASE - 1;
                if (0 <= substring_number && substring_number <= 8) {
                  return;
                }
        }
        super.setInstanceIdValue(id, value);
    }

}
