package org.mozilla.javascript.tests;

import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Context;
import junit.framework.TestCase;

import java.util.*;


/**
 * See https://bugzilla.mozilla.org/show_bug.cgi?id=448816
 */
public class Bug448816Test extends TestCase {

    Map<Object, Object> map, reference;

    @SuppressWarnings("unchecked")
    @Override
    protected void setUp() {
        // set up a reference map
        reference = new LinkedHashMap<Object, Object>();
        reference.put("a", "a");
        reference.put("b", Boolean.TRUE);
        reference.put("c", new HashMap<Object, Object>());
        reference.put(new Integer(1), new Integer(42));
        // get a js object as map
        Context context = Context.enter();
        ScriptableObject scope = context.initStandardObjects();
        map = (Map<Object, Object>) context.evaluateString(scope,
                "({ a: 'a', b: true, c: new java.util.HashMap(), 1: 42});",
                "testsrc", 1, null);
        Context.exit();
    }

    public void testEqual() {
        // FIXME we do not override equals() and hashCode() in ScriptableObject
        // so calling this with swapped argument fails. This breaks symmetry
        // of equals(), but overriding these methods might be risky.
        assertEquals(reference, map);
    }

    public void testBasicAccess() {
        assertTrue(map.size() == 4);
        assertEquals(map.get("a"), reference.get("a"));
        assertEquals(map.get("b"), reference.get("b"));
        assertEquals(map.get("c"), reference.get("c"));
        assertEquals(map.get(new Integer(1)), reference.get(new Integer(1)));
        assertEquals(map.get("notfound"), reference.get("notfound"));
        assertTrue(map.containsKey("b"));
        assertTrue(map.containsValue(Boolean.TRUE));
        assertFalse(map.containsKey("x"));
        assertFalse(map.containsValue(Boolean.FALSE));
        assertFalse(map.containsValue(null));
    }

    public void testCollections() {
        assertEquals(map.keySet(), reference.keySet());
        assertEquals(map.entrySet(), reference.entrySet());
        // java.util.Collection does not imply overriding equals(), so:
        assertTrue(map.values().containsAll(reference.values()));
        assertTrue(reference.values().containsAll(map.values()));
    }

    public void testRemoval() {
        // the only update we implement is removal
        assertTrue(map.size() == 4);
        assertEquals(map.remove("b"), Boolean.TRUE);
        reference.remove("b");
        assertTrue(map.size() == 3);
        assertEquals(reference, map);
        testCollections();
    }

    public void testKeyIterator() {
        compareIterators(map.keySet().iterator(), reference.keySet().iterator());
    }

    public void testEntryIterator() {
        compareIterators(map.entrySet().iterator(), reference.entrySet().iterator());
    }

    public void testValueIterator() {
        compareIterators(map.values().iterator(), reference.values().iterator());
    }

    private void compareIterators(Iterator it1, Iterator it2) {
        assertTrue(map.size() == 4);
        while (it1.hasNext()) {
            assertEquals(it1.next(), it2.next());
            it1.remove();
            it2.remove();
        }
        assertTrue(map.isEmpty());
    }
}
