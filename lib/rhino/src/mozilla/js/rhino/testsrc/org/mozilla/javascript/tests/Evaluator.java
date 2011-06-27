package org.mozilla.javascript.tests;
import org.mozilla.javascript.*;
import java.util.Collections;
import java.util.Map;

public class Evaluator {

  public static Object eval(String source) {
    return eval(source, Collections.EMPTY_MAP);
  }

  public static Object eval(String source, String id, Scriptable object) {
    return eval(source, Collections.singletonMap(id, object));
  }

  public static Object eval(String source, Map<String, Scriptable> bindings) {
    Context cx = ContextFactory.getGlobal().enterContext();
    try {
      Scriptable scope = cx.initStandardObjects();
      for (String id : bindings.keySet()) {
        Scriptable object = bindings.get(id);
        object.setParentScope(scope);
        scope.put(id, scope, object);
      }
      return cx.evaluateString(scope, source, "source", 1, null);
    } finally {
      Context.exit();
    }
  }
}
