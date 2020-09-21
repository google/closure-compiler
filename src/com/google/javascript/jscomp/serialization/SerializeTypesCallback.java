package com.google.javascript.jscomp.serialization;

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.InvalidatingTypes;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.serialization.TypePoolCreator;
import java.util.IdentityHashMap;

/** Grab a TypePointer for each JSType on the AST. */
final class SerializeTypesCallback extends AbstractPostOrderCallback {

  private final TypePoolCreator<JSType> typePoolCreator;
  private final JSTypeSerializer jstypeSerializer;
  private final IdentityHashMap<JSType, TypePointer> typePointersByJstype = new IdentityHashMap<>();

  private SerializeTypesCallback(
      TypePoolCreator<JSType> typePoolCreator, JSTypeSerializer jstypeSerializer) {
    this.typePoolCreator = typePoolCreator;
    this.jstypeSerializer = jstypeSerializer;
  }

  static SerializeTypesCallback create(AbstractCompiler compiler) {
    TypePoolCreator<JSType> typePoolCreator = TypePoolCreator.create();
    InvalidatingTypes invalidatingTypes =
        new InvalidatingTypes.Builder(compiler.getTypeRegistry())
            .addAllTypeMismatches(compiler.getTypeMismatches())
            .addAllTypeMismatches(compiler.getImplicitInterfaceUses())
            .build();
    JSTypeSerializer jsTypeSerializer =
        JSTypeSerializer.create(typePoolCreator, compiler.getTypeRegistry(), invalidatingTypes);
    return new SerializeTypesCallback(typePoolCreator, jsTypeSerializer);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    JSType type = n.getJSType();
    if (type != null && !typePointersByJstype.containsKey(type)) {
      typePointersByJstype.put(type, jstypeSerializer.serializeType(type));
    }
  }

  IdentityHashMap<JSType, TypePointer> getTypePointersByJstype() {
    return typePointersByJstype;
  }

  TypePool generateTypePool() {
    return typePoolCreator.generateTypePool();
  }
}
