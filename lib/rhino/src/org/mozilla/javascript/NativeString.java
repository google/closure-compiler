/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.text.Collator;

/**
 * This class implements the String native object.
 *
 * See ECMA 15.5.
 *
 * String methods for dealing with regular expressions are
 * ported directly from C. Latest port is from version 1.40.12.19
 * in the JSFUN13_BRANCH.
 *
 */
final class NativeString extends IdScriptableObject
{
    static final long serialVersionUID = 920268368584188687L;

    private static final Object STRING_TAG = "String";

    static void init(Scriptable scope, boolean sealed)
    {
        NativeString obj = new NativeString("");
        obj.exportAsJSClass(MAX_PROTOTYPE_ID, scope, sealed);
    }

    NativeString(CharSequence s) {
        string = s;
    }

    @Override
    public String getClassName() {
        return "String";
    }

    private static final int
        Id_length                    =  1,
        MAX_INSTANCE_ID              =  1;

    @Override
    protected int getMaxInstanceId()
    {
        return MAX_INSTANCE_ID;
    }

    @Override
    protected int findInstanceIdInfo(String s)
    {
        if (s.equals("length")) {
            return instanceIdInfo(DONTENUM | READONLY | PERMANENT, Id_length);
        }
        return super.findInstanceIdInfo(s);
    }

    @Override
    protected String getInstanceIdName(int id)
    {
        if (id == Id_length) { return "length"; }
        return super.getInstanceIdName(id);
    }

    @Override
    protected Object getInstanceIdValue(int id)
    {
        if (id == Id_length) {
            return ScriptRuntime.wrapInt(string.length());
        }
        return super.getInstanceIdValue(id);
    }

    @Override
    protected void fillConstructorProperties(IdFunctionObject ctor)
    {
        addIdFunctionProperty(ctor, STRING_TAG, ConstructorId_fromCharCode,
                "fromCharCode", 1);
        addIdFunctionProperty(ctor, STRING_TAG,
                ConstructorId_charAt, "charAt", 2);
        addIdFunctionProperty(ctor, STRING_TAG,
                ConstructorId_charCodeAt, "charCodeAt", 2);
        addIdFunctionProperty(ctor, STRING_TAG,
                ConstructorId_indexOf, "indexOf", 2);
        addIdFunctionProperty(ctor, STRING_TAG,
                ConstructorId_lastIndexOf, "lastIndexOf", 2);
        addIdFunctionProperty(ctor, STRING_TAG,
                ConstructorId_split, "split", 3);
        addIdFunctionProperty(ctor, STRING_TAG,
                ConstructorId_substring, "substring", 3);
        addIdFunctionProperty(ctor, STRING_TAG,
                ConstructorId_toLowerCase, "toLowerCase", 1);
        addIdFunctionProperty(ctor, STRING_TAG,
                ConstructorId_toUpperCase, "toUpperCase", 1);
        addIdFunctionProperty(ctor, STRING_TAG,
                ConstructorId_substr, "substr", 3);
        addIdFunctionProperty(ctor, STRING_TAG,
                ConstructorId_concat, "concat", 2);
        addIdFunctionProperty(ctor, STRING_TAG,
                ConstructorId_slice, "slice", 3);
        addIdFunctionProperty(ctor, STRING_TAG,
                ConstructorId_equalsIgnoreCase, "equalsIgnoreCase", 2);
        addIdFunctionProperty(ctor, STRING_TAG,
                ConstructorId_match, "match", 2);
        addIdFunctionProperty(ctor, STRING_TAG,
                ConstructorId_search, "search", 2);
        addIdFunctionProperty(ctor, STRING_TAG,
                ConstructorId_replace, "replace", 2);
        addIdFunctionProperty(ctor, STRING_TAG,
                ConstructorId_localeCompare, "localeCompare", 2);
        addIdFunctionProperty(ctor, STRING_TAG,
                ConstructorId_toLocaleLowerCase, "toLocaleLowerCase", 1);
        super.fillConstructorProperties(ctor);
    }

    @Override
    protected void initPrototypeId(int id)
    {
        String s;
        int arity;
        switch (id) {
          case Id_constructor:       arity=1; s="constructor";       break;
          case Id_toString:          arity=0; s="toString";          break;
          case Id_toSource:          arity=0; s="toSource";          break;
          case Id_valueOf:           arity=0; s="valueOf";           break;
          case Id_charAt:            arity=1; s="charAt";            break;
          case Id_charCodeAt:        arity=1; s="charCodeAt";        break;
          case Id_indexOf:           arity=1; s="indexOf";           break;
          case Id_lastIndexOf:       arity=1; s="lastIndexOf";       break;
          case Id_split:             arity=2; s="split";             break;
          case Id_substring:         arity=2; s="substring";         break;
          case Id_toLowerCase:       arity=0; s="toLowerCase";       break;
          case Id_toUpperCase:       arity=0; s="toUpperCase";       break;
          case Id_substr:            arity=2; s="substr";            break;
          case Id_concat:            arity=1; s="concat";            break;
          case Id_slice:             arity=2; s="slice";             break;
          case Id_bold:              arity=0; s="bold";              break;
          case Id_italics:           arity=0; s="italics";           break;
          case Id_fixed:             arity=0; s="fixed";             break;
          case Id_strike:            arity=0; s="strike";            break;
          case Id_small:             arity=0; s="small";             break;
          case Id_big:               arity=0; s="big";               break;
          case Id_blink:             arity=0; s="blink";             break;
          case Id_sup:               arity=0; s="sup";               break;
          case Id_sub:               arity=0; s="sub";               break;
          case Id_fontsize:          arity=0; s="fontsize";          break;
          case Id_fontcolor:         arity=0; s="fontcolor";         break;
          case Id_link:              arity=0; s="link";              break;
          case Id_anchor:            arity=0; s="anchor";            break;
          case Id_equals:            arity=1; s="equals";            break;
          case Id_equalsIgnoreCase:  arity=1; s="equalsIgnoreCase";  break;
          case Id_match:             arity=1; s="match";             break;
          case Id_search:            arity=1; s="search";            break;
          case Id_replace:           arity=1; s="replace";           break;
          case Id_localeCompare:     arity=1; s="localeCompare";     break;
          case Id_toLocaleLowerCase: arity=0; s="toLocaleLowerCase"; break;
          case Id_toLocaleUpperCase: arity=0; s="toLocaleUpperCase"; break;
          case Id_trim:              arity=0; s="trim";              break;
          default: throw new IllegalArgumentException(String.valueOf(id));
        }
        initPrototypeMethod(STRING_TAG, id, s, arity);
    }

    @Override
    public Object execIdCall(IdFunctionObject f, Context cx, Scriptable scope,
                             Scriptable thisObj, Object[] args)
    {
        if (!f.hasTag(STRING_TAG)) {
            return super.execIdCall(f, cx, scope, thisObj, args);
        }
        int id = f.methodId();
      again:
        for(;;) {
            switch (id) {
              case ConstructorId_charAt:
              case ConstructorId_charCodeAt:
              case ConstructorId_indexOf:
              case ConstructorId_lastIndexOf:
              case ConstructorId_split:
              case ConstructorId_substring:
              case ConstructorId_toLowerCase:
              case ConstructorId_toUpperCase:
              case ConstructorId_substr:
              case ConstructorId_concat:
              case ConstructorId_slice:
              case ConstructorId_equalsIgnoreCase:
              case ConstructorId_match:
              case ConstructorId_search:
              case ConstructorId_replace:
              case ConstructorId_localeCompare:
              case ConstructorId_toLocaleLowerCase: {
                if (args.length > 0) {
                    thisObj = ScriptRuntime.toObject(scope,
                            ScriptRuntime.toCharSequence(args[0]));
                    Object[] newArgs = new Object[args.length-1];
                    for (int i=0; i < newArgs.length; i++)
                        newArgs[i] = args[i+1];
                    args = newArgs;
                } else {
                    thisObj = ScriptRuntime.toObject(scope,
                            ScriptRuntime.toCharSequence(thisObj));
                }
                id = -id;
                continue again;
              }

              case ConstructorId_fromCharCode: {
                int N = args.length;
                if (N < 1)
                    return "";
                StringBuffer sb = new StringBuffer(N);
                for (int i = 0; i != N; ++i) {
                    sb.append(ScriptRuntime.toUint16(args[i]));
                }
                return sb.toString();
              }

              case Id_constructor: {
                CharSequence s = (args.length >= 1)
                    ? ScriptRuntime.toCharSequence(args[0]) : "";
                if (thisObj == null) {
                    // new String(val) creates a new String object.
                    return new NativeString(s);
                }
                // String(val) converts val to a string value.
                return s instanceof String ? s : s.toString();
              }

              case Id_toString:
              case Id_valueOf:
                // ECMA 15.5.4.2: 'the toString function is not generic.
                CharSequence cs = realThis(thisObj, f).string;
                return cs instanceof String ? cs : cs.toString();

              case Id_toSource: {
                CharSequence s = realThis(thisObj, f).string;
                return "(new String(\""+ScriptRuntime.escapeString(s.toString())+"\"))";
              }

              case Id_charAt:
              case Id_charCodeAt: {
                 // See ECMA 15.5.4.[4,5]
                CharSequence target = ScriptRuntime.toCharSequence(thisObj);
                double pos = ScriptRuntime.toInteger(args, 0);
                if (pos < 0 || pos >= target.length()) {
                    if (id == Id_charAt) return "";
                    else return ScriptRuntime.NaNobj;
                }
                char c = target.charAt((int)pos);
                if (id == Id_charAt) return String.valueOf(c);
                else return ScriptRuntime.wrapInt(c);
              }

              case Id_indexOf:
                return ScriptRuntime.wrapInt(js_indexOf(
                    ScriptRuntime.toString(thisObj), args));

              case Id_lastIndexOf:
                return ScriptRuntime.wrapInt(js_lastIndexOf(
                    ScriptRuntime.toString(thisObj), args));

              case Id_split:
                return ScriptRuntime.checkRegExpProxy(cx).
                  js_split(cx, scope, ScriptRuntime.toString(thisObj),
                        args);

              case Id_substring:
                return js_substring(cx, ScriptRuntime.toCharSequence(thisObj), args);

              case Id_toLowerCase:
                // See ECMA 15.5.4.11
                return ScriptRuntime.toString(thisObj).toLowerCase(
                         ScriptRuntime.ROOT_LOCALE);

              case Id_toUpperCase:
                // See ECMA 15.5.4.12
                return ScriptRuntime.toString(thisObj).toUpperCase(
                         ScriptRuntime.ROOT_LOCALE);

              case Id_substr:
                return js_substr(ScriptRuntime.toCharSequence(thisObj), args);

              case Id_concat:
                return js_concat(ScriptRuntime.toString(thisObj), args);

              case Id_slice:
                return js_slice(ScriptRuntime.toCharSequence(thisObj), args);

              case Id_bold:
                return tagify(thisObj, "b", null, null);

              case Id_italics:
                return tagify(thisObj, "i", null, null);

              case Id_fixed:
                return tagify(thisObj, "tt", null, null);

              case Id_strike:
                return tagify(thisObj, "strike", null, null);

              case Id_small:
                return tagify(thisObj, "small", null, null);

              case Id_big:
                return tagify(thisObj, "big", null, null);

              case Id_blink:
                return tagify(thisObj, "blink", null, null);

              case Id_sup:
                return tagify(thisObj, "sup", null, null);

              case Id_sub:
                return tagify(thisObj, "sub", null, null);

              case Id_fontsize:
                return tagify(thisObj, "font", "size", args);

              case Id_fontcolor:
                return tagify(thisObj, "font", "color", args);

              case Id_link:
                return tagify(thisObj, "a", "href", args);

              case Id_anchor:
                return tagify(thisObj, "a", "name", args);

              case Id_equals:
              case Id_equalsIgnoreCase: {
                String s1 = ScriptRuntime.toString(thisObj);
                String s2 = ScriptRuntime.toString(args, 0);
                return ScriptRuntime.wrapBoolean(
                    (id == Id_equals) ? s1.equals(s2)
                                      : s1.equalsIgnoreCase(s2));
              }

              case Id_match:
              case Id_search:
              case Id_replace:
                {
                    int actionType;
                    if (id == Id_match) {
                        actionType = RegExpProxy.RA_MATCH;
                    } else if (id == Id_search) {
                        actionType = RegExpProxy.RA_SEARCH;
                    } else {
                        actionType = RegExpProxy.RA_REPLACE;
                    }
                    return ScriptRuntime.checkRegExpProxy(cx).
                        action(cx, scope, thisObj, args, actionType);
                }
                // ECMA-262 1 5.5.4.9
              case Id_localeCompare:
                {
                    // For now, create and configure a collator instance. I can't
                    // actually imagine that this'd be slower than caching them
                    // a la ClassCache, so we aren't trying to outsmart ourselves
                    // with a caching mechanism for now.
                    Collator collator = Collator.getInstance(cx.getLocale());
                    collator.setStrength(Collator.IDENTICAL);
                    collator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
                    return ScriptRuntime.wrapNumber(collator.compare(
                            ScriptRuntime.toString(thisObj),
                            ScriptRuntime.toString(args, 0)));
                }
              case Id_toLocaleLowerCase:
                {
                    return ScriptRuntime.toString(thisObj)
                            .toLowerCase(cx.getLocale());
                }
              case Id_toLocaleUpperCase:
                {
                    return ScriptRuntime.toString(thisObj)
                            .toUpperCase(cx.getLocale());
                }
              case Id_trim:
                {
                    String str = ScriptRuntime.toString(thisObj);
                    char[] chars = str.toCharArray();

                    int start = 0;
                    while (start < chars.length && ScriptRuntime.isJSWhitespaceOrLineTerminator(chars[start])) {
                      start++;
                    }
                    int end = chars.length;
                    while (end > start && ScriptRuntime.isJSWhitespaceOrLineTerminator(chars[end-1])) {
                      end--;
                    }

                    return str.substring(start, end);
                }
            }
            throw new IllegalArgumentException(String.valueOf(id));
        }
    }

    private static NativeString realThis(Scriptable thisObj, IdFunctionObject f)
    {
        if (!(thisObj instanceof NativeString))
            throw incompatibleCallError(f);
        return (NativeString)thisObj;
    }

    /*
     * HTML composition aids.
     */
    private static String tagify(Object thisObj, String tag,
                                 String attribute, Object[] args)
    {
        String str = ScriptRuntime.toString(thisObj);
        StringBuffer result = new StringBuffer();
        result.append('<');
        result.append(tag);
        if (attribute != null) {
            result.append(' ');
            result.append(attribute);
            result.append("=\"");
            result.append(ScriptRuntime.toString(args, 0));
            result.append('"');
        }
        result.append('>');
        result.append(str);
        result.append("</");
        result.append(tag);
        result.append('>');
        return result.toString();
    }

    public CharSequence toCharSequence() {
        return string;
    }

    @Override
    public String toString() {
        return string instanceof String ? (String)string : string.toString();
    }

    /* Make array-style property lookup work for strings.
     * XXX is this ECMA?  A version check is probably needed. In js too.
     */
    @Override
    public Object get(int index, Scriptable start) {
        if (0 <= index && index < string.length()) {
            return String.valueOf(string.charAt(index));
        }
        return super.get(index, start);
    }

    @Override
    public void put(int index, Scriptable start, Object value) {
        if (0 <= index && index < string.length()) {
            return;
        }
        super.put(index, start, value);
    }

    /*
     *
     * See ECMA 15.5.4.6.  Uses Java String.indexOf()
     * OPT to add - BMH searching from jsstr.c.
     */
    private static int js_indexOf(String target, Object[] args) {
        String search = ScriptRuntime.toString(args, 0);
        double begin = ScriptRuntime.toInteger(args, 1);

        if (begin > target.length()) {
            return -1;
        } else {
            if (begin < 0)
                begin = 0;
            return target.indexOf(search, (int)begin);
        }
    }

    /*
     *
     * See ECMA 15.5.4.7
     *
     */
    private static int js_lastIndexOf(String target, Object[] args) {
        String search = ScriptRuntime.toString(args, 0);
        double end = ScriptRuntime.toNumber(args, 1);

        if (Double.isNaN(end) || end > target.length())
            end = target.length();
        else if (end < 0)
            end = 0;

        return target.lastIndexOf(search, (int)end);
    }


    /*
     * See ECMA 15.5.4.15
     */
    private static CharSequence js_substring(Context cx, CharSequence target,
                                       Object[] args)
    {
        int length = target.length();
        double start = ScriptRuntime.toInteger(args, 0);
        double end;

        if (start < 0)
            start = 0;
        else if (start > length)
            start = length;

        if (args.length <= 1 || args[1] == Undefined.instance) {
            end = length;
        } else {
            end = ScriptRuntime.toInteger(args[1]);
            if (end < 0)
                end = 0;
            else if (end > length)
                end = length;

            // swap if end < start
            if (end < start) {
                if (cx.getLanguageVersion() != Context.VERSION_1_2) {
                    double temp = start;
                    start = end;
                    end = temp;
                } else {
                    // Emulate old JDK1.0 java.lang.String.substring()
                    end = start;
                }
            }
        }
        return target.subSequence((int)start, (int)end);
    }

    int getLength() {
        return string.length();
    }

    /*
     * Non-ECMA methods.
     */
    private static CharSequence js_substr(CharSequence target, Object[] args) {
        if (args.length < 1)
            return target;

        double begin = ScriptRuntime.toInteger(args[0]);
        double end;
        int length = target.length();

        if (begin < 0) {
            begin += length;
            if (begin < 0)
                begin = 0;
        } else if (begin > length) {
            begin = length;
        }

        if (args.length == 1) {
            end = length;
        } else {
            end = ScriptRuntime.toInteger(args[1]);
            if (end < 0)
                end = 0;
            end += begin;
            if (end > length)
                end = length;
        }

        return target.subSequence((int)begin, (int)end);
    }

    /*
     * Python-esque sequence operations.
     */
    private static String js_concat(String target, Object[] args) {
        int N = args.length;
        if (N == 0) { return target; }
        else if (N == 1) {
            String arg = ScriptRuntime.toString(args[0]);
            return target.concat(arg);
        }

        // Find total capacity for the final string to avoid unnecessary
        // re-allocations in StringBuffer
        int size = target.length();
        String[] argsAsStrings = new String[N];
        for (int i = 0; i != N; ++i) {
            String s = ScriptRuntime.toString(args[i]);
            argsAsStrings[i] = s;
            size += s.length();
        }

        StringBuffer result = new StringBuffer(size);
        result.append(target);
        for (int i = 0; i != N; ++i) {
            result.append(argsAsStrings[i]);
        }
        return result.toString();
    }

    private static CharSequence js_slice(CharSequence target, Object[] args) {
        if (args.length != 0) {
            double begin = ScriptRuntime.toInteger(args[0]);
            double end;
            int length = target.length();
            if (begin < 0) {
                begin += length;
                if (begin < 0)
                    begin = 0;
            } else if (begin > length) {
                begin = length;
            }

            if (args.length == 1) {
                end = length;
            } else {
                end = ScriptRuntime.toInteger(args[1]);
                if (end < 0) {
                    end += length;
                    if (end < 0)
                        end = 0;
                } else if (end > length) {
                    end = length;
                }
                if (end < begin)
                    end = begin;
            }
            return target.subSequence((int) begin, (int) end);
        }
        return target;
    }

// #string_id_map#

    @Override
    protected int findPrototypeId(String s)
    {
        int id;
// #generated# Last update: 2009-07-23 07:32:39 EST
        L0: { id = 0; String X = null; int c;
            L: switch (s.length()) {
            case 3: c=s.charAt(2);
                if (c=='b') { if (s.charAt(0)=='s' && s.charAt(1)=='u') {id=Id_sub; break L0;} }
                else if (c=='g') { if (s.charAt(0)=='b' && s.charAt(1)=='i') {id=Id_big; break L0;} }
                else if (c=='p') { if (s.charAt(0)=='s' && s.charAt(1)=='u') {id=Id_sup; break L0;} }
                break L;
            case 4: c=s.charAt(0);
                if (c=='b') { X="bold";id=Id_bold; }
                else if (c=='l') { X="link";id=Id_link; }
                else if (c=='t') { X="trim";id=Id_trim; }
                break L;
            case 5: switch (s.charAt(4)) {
                case 'd': X="fixed";id=Id_fixed; break L;
                case 'e': X="slice";id=Id_slice; break L;
                case 'h': X="match";id=Id_match; break L;
                case 'k': X="blink";id=Id_blink; break L;
                case 'l': X="small";id=Id_small; break L;
                case 't': X="split";id=Id_split; break L;
                } break L;
            case 6: switch (s.charAt(1)) {
                case 'e': X="search";id=Id_search; break L;
                case 'h': X="charAt";id=Id_charAt; break L;
                case 'n': X="anchor";id=Id_anchor; break L;
                case 'o': X="concat";id=Id_concat; break L;
                case 'q': X="equals";id=Id_equals; break L;
                case 't': X="strike";id=Id_strike; break L;
                case 'u': X="substr";id=Id_substr; break L;
                } break L;
            case 7: switch (s.charAt(1)) {
                case 'a': X="valueOf";id=Id_valueOf; break L;
                case 'e': X="replace";id=Id_replace; break L;
                case 'n': X="indexOf";id=Id_indexOf; break L;
                case 't': X="italics";id=Id_italics; break L;
                } break L;
            case 8: c=s.charAt(4);
                if (c=='r') { X="toString";id=Id_toString; }
                else if (c=='s') { X="fontsize";id=Id_fontsize; }
                else if (c=='u') { X="toSource";id=Id_toSource; }
                break L;
            case 9: c=s.charAt(0);
                if (c=='f') { X="fontcolor";id=Id_fontcolor; }
                else if (c=='s') { X="substring";id=Id_substring; }
                break L;
            case 10: X="charCodeAt";id=Id_charCodeAt; break L;
            case 11: switch (s.charAt(2)) {
                case 'L': X="toLowerCase";id=Id_toLowerCase; break L;
                case 'U': X="toUpperCase";id=Id_toUpperCase; break L;
                case 'n': X="constructor";id=Id_constructor; break L;
                case 's': X="lastIndexOf";id=Id_lastIndexOf; break L;
                } break L;
            case 13: X="localeCompare";id=Id_localeCompare; break L;
            case 16: X="equalsIgnoreCase";id=Id_equalsIgnoreCase; break L;
            case 17: c=s.charAt(8);
                if (c=='L') { X="toLocaleLowerCase";id=Id_toLocaleLowerCase; }
                else if (c=='U') { X="toLocaleUpperCase";id=Id_toLocaleUpperCase; }
                break L;
            }
            if (X!=null && X!=s && !X.equals(s)) id = 0;
            break L0;
        }
// #/generated#
        return id;
    }

    private static final int
        ConstructorId_fromCharCode   = -1,

        Id_constructor               = 1,
        Id_toString                  = 2,
        Id_toSource                  = 3,
        Id_valueOf                   = 4,
        Id_charAt                    = 5,
        Id_charCodeAt                = 6,
        Id_indexOf                   = 7,
        Id_lastIndexOf               = 8,
        Id_split                     = 9,
        Id_substring                 = 10,
        Id_toLowerCase               = 11,
        Id_toUpperCase               = 12,
        Id_substr                    = 13,
        Id_concat                    = 14,
        Id_slice                     = 15,
        Id_bold                      = 16,
        Id_italics                   = 17,
        Id_fixed                     = 18,
        Id_strike                    = 19,
        Id_small                     = 20,
        Id_big                       = 21,
        Id_blink                     = 22,
        Id_sup                       = 23,
        Id_sub                       = 24,
        Id_fontsize                  = 25,
        Id_fontcolor                 = 26,
        Id_link                      = 27,
        Id_anchor                    = 28,
        Id_equals                    = 29,
        Id_equalsIgnoreCase          = 30,
        Id_match                     = 31,
        Id_search                    = 32,
        Id_replace                   = 33,
        Id_localeCompare             = 34,
        Id_toLocaleLowerCase         = 35,
        Id_toLocaleUpperCase         = 36,
        Id_trim                      = 37,
        MAX_PROTOTYPE_ID             = Id_trim;

// #/string_id_map#

    private static final int
        ConstructorId_charAt         = -Id_charAt,
        ConstructorId_charCodeAt     = -Id_charCodeAt,
        ConstructorId_indexOf        = -Id_indexOf,
        ConstructorId_lastIndexOf    = -Id_lastIndexOf,
        ConstructorId_split          = -Id_split,
        ConstructorId_substring      = -Id_substring,
        ConstructorId_toLowerCase    = -Id_toLowerCase,
        ConstructorId_toUpperCase    = -Id_toUpperCase,
        ConstructorId_substr         = -Id_substr,
        ConstructorId_concat         = -Id_concat,
        ConstructorId_slice          = -Id_slice,
        ConstructorId_equalsIgnoreCase = -Id_equalsIgnoreCase,
        ConstructorId_match          = -Id_match,
        ConstructorId_search         = -Id_search,
        ConstructorId_replace        = -Id_replace,
        ConstructorId_localeCompare  = -Id_localeCompare,
        ConstructorId_toLocaleLowerCase = -Id_toLocaleLowerCase;

    private CharSequence string;
}
