/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */


package org.mozilla.javascript.optimizer;

import org.mozilla.javascript.*;
import org.mozilla.javascript.ast.ScriptNode;
import java.util.Map;

/**
 * This class performs node transforms to prepare for optimization.
 *
 * @see NodeTransformer
 */

class OptTransformer extends NodeTransformer {

    OptTransformer(Map<String,OptFunctionNode> possibleDirectCalls, ObjArray directCallTargets)
    {
        this.possibleDirectCalls = possibleDirectCalls;
        this.directCallTargets = directCallTargets;
    }

    @Override
    protected void visitNew(Node node, ScriptNode tree) {
        detectDirectCall(node, tree);
        super.visitNew(node, tree);
    }

    @Override
    protected void visitCall(Node node, ScriptNode tree) {
        detectDirectCall(node, tree);
        super.visitCall(node, tree);
    }

    private void detectDirectCall(Node node, ScriptNode tree)
    {
        if (tree.getType() == Token.FUNCTION) {
            Node left = node.getFirstChild();

            // count the arguments
            int argCount = 0;
            Node arg = left.getNext();
            while (arg != null) {
                arg = arg.getNext();
                argCount++;
            }

            if (argCount == 0) {
                OptFunctionNode.get(tree).itsContainsCalls0 = true;
            }

            /*
             * Optimize a call site by converting call("a", b, c) into :
             *
             *  FunctionObjectFor"a" <-- instance variable init'd by constructor
             *
             *  // this is a DIRECTCALL node
             *  fn = GetProp(tmp = GetBase("a"), "a");
             *  if (fn == FunctionObjectFor"a")
             *      fn.call(tmp, b, c)
             *  else
             *      ScriptRuntime.Call(fn, tmp, b, c)
             */
            if (possibleDirectCalls != null) {
                String targetName = null;
                if (left.getType() == Token.NAME) {
                    targetName = left.getString();
                } else if (left.getType() == Token.GETPROP) {
                    targetName = left.getFirstChild().getNext().getString();
                } else if (left.getType() == Token.GETPROPNOWARN) {
                    throw Kit.codeBug();
                }
                if (targetName != null) {
                    OptFunctionNode ofn;
                    ofn = possibleDirectCalls.get(targetName);
                    if (ofn != null
                        && argCount == ofn.fnode.getParamCount()
                        && !ofn.fnode.requiresActivation())
                    {
                        // Refuse to directCall any function with more
                        // than 32 parameters - prevent code explosion
                        // for wacky test cases
                        if (argCount <= 32) {
                            node.putProp(Node.DIRECTCALL_PROP, ofn);
                            if (!ofn.isTargetOfDirectCall()) {
                                int index = directCallTargets.size();
                                directCallTargets.add(ofn);
                                ofn.setDirectTargetIndex(index);
                            }
                        }
                    }
                }
            }
        }
    }

    private Map<String,OptFunctionNode> possibleDirectCalls;
    private ObjArray directCallTargets;
}
