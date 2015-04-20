/*
 * Copyright (c) 2015 The Polymer Project Authors. All rights reserved.
 * This code may only be used under the BSD style license found at http://polymer.github.io/LICENSE
 * The complete set of authors may be found at http://polymer.github.io/AUTHORS
 * The complete set of contributors may be found at http://polymer.github.io/CONTRIBUTORS
 * Code distributed by Google as part of the polymer project is also
 * subject to an additional IP rights grant found at http://polymer.github.io/PATENTS
 */

package com.google.polymer;

import static com.google.javascript.rhino.Token.GETPROP;
import static com.google.javascript.rhino.Token.NAME;
import static com.google.javascript.rhino.Token.STRING;
import static com.google.javascript.rhino.Token.STRING_KEY;

import com.google.common.collect.ImmutableMap;
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
 * Handles all JavaScript Parsing Coordination.
 */
public class JsRenamer {

  private JsRenamer() {}

  /**
   * Parses, renames properties, and reformats JavaScript.
   * @param renameMap A mapping from symbol to renamed symbol.
   * @param js The JavaScript code.
   * @return JavaScript code with renames applied.
   */
  public static String renameProperties(ImmutableMap<String, String> renameMap, String js) {
    return toSource(renameNode(renameMap, parse(js), false));
  }

  public static String renamePropertiesAndVariables(ImmutableMap<String, String> renameMap,
      String js) {
    return toSource(renameNode(renameMap, parse(js), true));
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
   * @param allowVariableRenaming Allow for more aggressive renaming in EXPR_RESULT nodes. This used
   *        in single expression scripts like Polymer databinding directives.
   * @return The renamed abstract syntax tree.
   */
  private static Node renameNode(ImmutableMap<String, String> renameMap, Node current,
      boolean allowVariableRenaming) {
    int type = current.getType();
    switch (type) {
      case GETPROP:
        if (current.hasMoreThanOneChild()) {
          Node secondChild = current.getChildAtIndex(1);
          if (secondChild.getType() == STRING) {
            renameNodeWithString(renameMap, secondChild);
          }
        }
        break;
      case STRING_KEY:
        renameNodeWithString(renameMap, current);
        break;
      case NAME:
        if (allowVariableRenaming) {
          renameNodeWithString(renameMap, current);
        }
        break;
    }
    for (Node child : current.children()) {
      renameNode(renameMap, child, allowVariableRenaming);
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

  /**
   * JavaScript syntax checking is a non-goal for the renamer since other tools like Closure will
   * catch issues at compile time and the rest of the issues will be found by the interpreter at
   * runtime. As a result, this error reporter reports noting. In the future, the renamer will not
   * handle JavaScript files and only process Polymer Databinding Expressions.
   */
  private static class MutedErrorReporter implements ErrorReporter {
    public MutedErrorReporter() {}

    @Override
    public void warning(String message, String sourceName, int line, int lineOffset) {}

    @Override
    public void error(String message, String sourceName, int line, int lineOffset) {}
  }
}
