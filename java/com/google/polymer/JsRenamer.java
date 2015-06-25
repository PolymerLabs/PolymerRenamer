/*
 * Copyright (c) 2015 The Polymer Project Authors. All rights reserved.
 * This code may only be used under the BSD style license found at http://polymer.github.io/LICENSE
 * The complete set of authors may be found at http://polymer.github.io/AUTHORS
 * The complete set of contributors may be found at http://polymer.github.io/CONTRIBUTORS
 * Code distributed by Google as part of the polymer project is also
 * subject to an additional IP rights grant found at http://polymer.github.io/PATENTS
 */

package com.google.polymer;

import static com.google.javascript.rhino.Token.CALL;
import static com.google.javascript.rhino.Token.GETPROP;
import static com.google.javascript.rhino.Token.NAME;
import static com.google.javascript.rhino.Token.OBJECTLIT;
import static com.google.javascript.rhino.Token.STRING_KEY;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.parsing.Config;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.jscomp.parsing.ParserRunner;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SimpleSourceFile;
import com.google.javascript.rhino.StaticSourceFile;

/**
 * Handles all JavaScript renaming.
 */
public class JsRenamer {
  private enum RenameMode {
    /**
     * Allow for more aggressive renaming in EXPR_RESULT nodes. This used in single expression
     * scripts like Polymer databinding directives.
     */
    RENAME_VARIABLES,

    /**
     * Perform renaming of GetProp nodes. This is used for Polymer databinding expressions and
     * Polymer 0.5 Legacy JavaScript code that predates the Closure Polymer Pass.
     */
    RENAME_PROPERTIES,
  }

  private JsRenamer() {}

  /**
   * Performs renames on JavaScript supplied from a JavaScript file.
   * @param renameMap A mapping from symbol to renamed symbol.
   * @param js The JavaScript code.
   * @return JavaScript code with renames applied.
   */
  public static String rename(ImmutableMap<String, String> renameMap, String js) {
    Node jsAst = parse(js);
    ImmutableSet<RenameMode> renameMode = isPolymer05Javascript(jsAst)
        ? ImmutableSet.<RenameMode>of(RenameMode.RENAME_PROPERTIES)
        : ImmutableSet.<RenameMode>of();
    return toSource(renameNode(renameMap, jsAst, renameMode));
  }

  /**
   * Renames JavaScript with Property Renaming. This is primarily used for code that predated the
   * Closure Polymer Pass.
   * @param renameMap A mapping from symbol to renamed symbol.
   * @param js The JavaScript code.
   * @return JavaScript code with renames applied.
   */
  public static String renameProperties(ImmutableMap<String, String> renameMap, String js) {
    return toSource(renameNode(
        renameMap,
        parse(js),
        ImmutableSet.<RenameMode>of(RenameMode.RENAME_PROPERTIES)));
  }

  /**
   * Renames properties, renames variables, and reformats Polymer JavaScript-like expressions.
   * @param renameMap A mapping from symbol to renamed symbol.
   * @param js The JavaScript code.
   * @return The JavaScript-like expression with renames applied.
   */
  public static String renamePolymerJsExpression(
      ImmutableMap<String, String> renameMap, String js) {
    // Add parenthesis to convince the parser that the input is a value expression.
    String renamed = toSource(renameNode(
        renameMap,
        parse("(" + js + ")"),
        ImmutableSet.of(RenameMode.RENAME_PROPERTIES, RenameMode.RENAME_VARIABLES)));
    if (renamed.length() > 0) {
      // Trim trailing semicolon since Polymer JavaScript-like expressions don't have this.
      renamed = renamed.substring(0, renamed.length() - 1);
    }
    return renamed;
  }

  /**
   * Parses the given JavaScript string into an abstract syntax tree.
   * @param js The JavaScript code.
   * @return An abstract syntax tree.
   */
  private static Node parse(String js) {
    StaticSourceFile file = new SimpleSourceFile("input", false);
    Config config = ParserRunner.createConfig(false, LanguageMode.ECMASCRIPT6, false, null);
    Node script = ParserRunner.parse(file, js, config, new MutedErrorReporter()).ast;
    return script;
  }

  /**
   * Returns true if the supplied node is Polymer 0.5 style JavaScript.
   * @param node The JavaScript abstract syntax tree to check.
   */
  private static boolean isPolymer05Javascript(Node node) {
    if (isPolymerCall(node) && node.hasMoreThanOneChild()) {
      Node firstArgument = node.getChildAtIndex(1);
      if (firstArgument.isString()) {
        return true;
      } else if (firstArgument.isObjectLit()) {
        for (Node stringKey = firstArgument.getFirstChild();
            stringKey != null;
            stringKey = stringKey.getNext()) {
          if (stringKey.isStringKey() && stringKey.getString().equals("is")) {
            return false;
          }
        }
        return true;
      }
    }

    for (Node current = node.getFirstChild(); current != null; current = current.getNext()) {
      if (isPolymer05Javascript(current)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Outputs the source equivalent of the abstract syntax tree.
   * @param node The JavaScript abstract syntax tree.
   * @return The equivalent JavaScript source.
   */
  private static String toSource(Node node) {
    CompilerOptions options = new CompilerOptions();
    options.prettyPrint = false;
    options.skipAllCompilerPasses();
    Compiler compiler = new Compiler();
    compiler.disableThreads();
    compiler.initOptions(options);
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    compiler.toSource(cb, 0, node);
    return cb.toString();
  }

  /**
   * Applies the rename map to the provided JavaScript abstract syntax tree.
   * @param renameMap A mapping from symbol to renamed symbol.
   * @param current The JavaScript abstract syntax tree to rename. Note that this method will mutate
   *        |current| with the renames.
   * @param renameMode Variable renaming mode to use.
   * @return The renamed abstract syntax tree.
   */
  private static Node renameNode(ImmutableMap<String, String> renameMap, Node current,
      ImmutableSet<RenameMode> renameMode) {
    int type = current.getType();
    switch (type) {
      case CALL:
        if (isInPolymerCall(current)) {
          renameCallWithinPolymerCall(renameMap, current);
        }
        break;
      case GETPROP:
        if (renameMode.contains(RenameMode.RENAME_PROPERTIES)) {
          if (current.hasMoreThanOneChild()) {
            Node secondChild = current.getChildAtIndex(1);
            if (secondChild.isString()) {
              renameNodeWithString(renameMap, secondChild);
            }
          }
        }
        break;
      case NAME:
        if (renameMode.contains(RenameMode.RENAME_VARIABLES)) {
          renameNodeWithString(renameMap, current);
        }
        break;
      case OBJECTLIT:
        renameObjectLiteral(renameMap, current);
        break;
      case STRING_KEY:
        if (renameMode.contains(RenameMode.RENAME_PROPERTIES)) {
          renameNodeWithString(renameMap, current);
        }
        break;
    }
    for (Node child : current.children()) {
      renameNode(renameMap, child, renameMode);
    }
    return current;
  }

  /**
   * Renames Polymer property changed object property identifiers (*Changed properties).
   * @param renameMap A mapping from symbol to renamed symbol.
   * @param node The string node containing the property changed identifier.
   */
  private static void renameNodeWithString(ImmutableMap<String, String> renameMap, Node node) {
    String name = node.getString();
    if (renameMap.containsKey(name)) {
      node.setString(renameMap.get(name));
    } else if (name.endsWith("Changed")) {
      String basename = name.substring(0, name.length() - 7);
      if (renameMap.containsKey(basename)) {
        node.setString(renameMap.get(basename) + "Changed");
      }
    }
  }

  private static boolean isInPolymerCall(Node node) {
    while (node != null) {
      if (isPolymerCall(node)) {
        return true;
      }
      node = node.getParent();
    }
    return false;
  }

  private static boolean isPolymerCall(Node node) {
    if (node.isCall() && node.hasMoreThanOneChild()) {
      Node firstChild = node.getFirstChild();
      return firstChild.isName() && firstChild.getString().equals("Polymer");
    }
    return false;
  }

  /**
   * Renames calls in a Polymer element definition.
   * @param renameMap A mapping from symbol to renamed symbol.
   * @param call The call node to rename.
   */
  private static void renameCallWithinPolymerCall(
      ImmutableMap<String, String> renameMap, Node call) {
    /* Rename PolymerElement.prototype.listen(node, eventName, methodName). */
    if (call.getChildCount() == 4) {
      Node maybeThisListenGetProp = call.getFirstChild();
      if (maybeThisListenGetProp.isGetProp()
          && maybeThisListenGetProp.hasMoreThanOneChild()
          && maybeThisListenGetProp.getFirstChild().isThis()) {
        Node maybeName = maybeThisListenGetProp.getChildAtIndex(1);
        if (maybeName.isString() && maybeName.getString().equals("listen")) {
          Node arg0 = call.getChildAtIndex(2);
          Node arg1 = call.getChildAtIndex(3);
          if (arg0.isString() && arg1.isString()) {
            String arg1String = arg1.getString();
            if (renameMap.containsKey(arg1String)) {
              arg1.setString(renameMap.get(arg1String));
            }
          }
        }
      }
    }
  }

  /**
   * Renames all object literals that are standalone or contained in a Polymer v0.8 style call.
   * This allows behaviors coverage, which are indistinguishable from regular JavaScript objects.
   * @param renameMap A mapping from symbol to renamed symbol.
   * @param objectLit Object literal node.
   */
  private static void renameObjectLiteral(ImmutableMap<String, String> renameMap, Node objectLit) {
    ImmutableMap<String, Node> objectMap = convertObjectLitNodeToMap(objectLit);
    if (isInPolymerCall(objectLit) && !objectMap.containsKey("is")) {
      // This object map is not in a non-Polymer v0.8 or newer call.
      return;
    }
    renameObjectMap(renameMap, objectMap);
  }

  /**
   * Forward renames to Polymer-relevant properties in the specified object map.
   * @param renameMap A mapping from symbol to renamed symbol.
   * @param objectMap A map of keys as property string names to values as nodes.
   */
  private static void renameObjectMap(
      ImmutableMap<String, String> renameMap, ImmutableMap<String, Node> objectMap) {
    // Rename 'computed' and 'observer' property description references.
    Node propertiesNode = objectMap.get("properties");
    if ((propertiesNode != null) && propertiesNode.isObjectLit()) {
      ImmutableMap<String, Node> propertiesMap = convertObjectLitNodeToMap(propertiesNode);
      for (Node propertyDescriptorNode : propertiesMap.values()) {
        if (propertyDescriptorNode.isObjectLit()) {
          ImmutableMap<String, Node> propertyDescriptorMap =
              convertObjectLitNodeToMap(propertyDescriptorNode);
          renamePolymerJsStringNode(renameMap, propertyDescriptorMap.get("computed"));
          renamePolymerJsStringNode(renameMap, propertyDescriptorMap.get("observer"));
        }
      }
    }

    // Rename all JavaScript-like expressions in the 'observers' array.
    Node observersNode = objectMap.get("observers");
    if ((observersNode != null) && observersNode.isArrayLit()) {
      for (Node observerItem : observersNode.children()) {
        renamePolymerJsStringNode(renameMap, observerItem);
      }
    }

    // Rename all JavaScript-like expressions in the listeners descriptor.
    Node listenersNode = objectMap.get("listeners");
    if ((listenersNode != null) && listenersNode.isObjectLit()) {
      ImmutableMap<String, Node> listenersMap = convertObjectLitNodeToMap(listenersNode);
      for (Node listenerDescriptorNode : listenersMap.values()) {
        renamePolymerJsStringNode(renameMap, listenerDescriptorNode);
      }
    }
  }

  /**
   * Renames a string node under variable naming rules similar to Polymer databinding expressions.
   * @param renameMap A mapping from symbol to renamed symbol.
   * @param node String node to rename under variable renaming rules. Can be null. Will not attempt
   *     a rename if the node is not a string node.
   */
  private static void renamePolymerJsStringNode(
      ImmutableMap<String, String> renameMap, Node node) {
    if (node == null || !node.isString()) {
      return;
    }

    node.setString(renamePolymerJsExpression(renameMap, node.getString()));
  }

  private static ImmutableMap<String, Node> convertObjectLitNodeToMap(Node objectLiteralNode) {
    ImmutableMap.Builder<String, Node> builder = ImmutableMap.builder();
    for (Node keyNode : objectLiteralNode.children()) {
      if (keyNode.isStringKey() && keyNode.hasOneChild()) {
        builder.put(keyNode.getString(), keyNode.getFirstChild());
      }
    }
    return builder.build();
  }

  /**
   * JavaScript syntax checking is a non-goal for the renamer since other tools like Closure will
   * catch issues at compile time and the rest of the issues will be found by the interpreter at
   * runtime. As a result, this error reporter reports nothing.
   */
  private static class MutedErrorReporter implements ErrorReporter {
    public MutedErrorReporter() {}

    @Override
    public void warning(String message, String sourceName, int line, int lineOffset) {}

    @Override
    public void error(String message, String sourceName, int line, int lineOffset) {}
  }
}
