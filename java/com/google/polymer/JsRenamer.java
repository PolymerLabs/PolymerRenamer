/*
 * Copyright (c) 2015 The Polymer Project Authors. All rights reserved.
 * This code may only be used under the BSD style license found at http://polymer.github.io/LICENSE
 * The complete set of authors may be found at http://polymer.github.io/AUTHORS
 * The complete set of contributors may be found at http://polymer.github.io/CONTRIBUTORS
 * Code distributed by Google as part of the polymer project is also
 * subject to an additional IP rights grant found at http://polymer.github.io/PATENTS
 */

package com.google.polymer;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.SourceMapInput;
import com.google.javascript.jscomp.parsing.Config;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.jscomp.parsing.Config.StrictMode;
import com.google.javascript.jscomp.parsing.ParserRunner;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SimpleSourceFile;
import com.google.javascript.rhino.StaticSourceFile;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Static methods that perform JavaScript code transformations based off of a rename map. */
public final class JsRenamer {

  /** Specifies the JavaScript output format. */
  public enum OutputFormat {
    /** Output JavaScript in a "pretty" format. If unspecified, the output will be minified. */
    PRETTY,

    /**
     * Output string literals using single quotes. If unspecified, the output will use double quotes
     * for strings.
     */
    SINGLE_QUOTE_STRINGS,
  }

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

  // Joiner for JavaScript function argument strings.
  private static final Joiner ARGUMENT_JOINER = Joiner.on(",");

  // Splitter for JavaScript function argument strings.
  private static final Splitter ARGUMENT_SPLITTER = Splitter.on(",").trimResults();

  // Polymer property change name string suffix.
  private static final String CHANGED_SUFFIX = "Changed";

  // Pattern that identifies JavaScript methods consistent with Polymer.Base._parseMethod.
  // See https://github.com/Polymer/polymer/blob/master/src/standard/effectBuilder.html
  private static final Pattern METHOD_PATTERN = Pattern.compile("([^\\s]+)\\((.*)\\)");

  // Joiner for JavaScript property expressions.
  private static final Joiner PROPERTY_EXPRESSION_JOINER = Joiner.on(".");

  // Splitter for JavaScript property expressions.
  private static final Splitter PROPERTY_EXPRESSION_SPLITTER = Splitter.on(".");

  // A placeholder file name when we don't have a real file backing the JS.
  private static final String PLACEHOLDER_FILE_NAME = "input";

  private JsRenamer() {}

  /**
   * Performs renames on JavaScript as an entire string typically supplied from a file.
   *
   * @param renameMap A mapping from symbol to renamed symbol.
   * @param js The JavaScript code.
   * @param outputFormat The source output format options.
   * @param inputFileName The name of the input source file being renamed.
   * @param inputSourceMap The content of the input sourcemap.
   * @param sourceMapOutputFileName The name of the output sourcemap.
   * @return JavaScript code with renames applied.
   * @throws JavaScriptParsingException if parse errors were encountered.
   */
  public static String rename(
      ImmutableMap<String, String> renameMap,
      String js,
      ImmutableSet<OutputFormat> outputFormat,
      String inputFileName,
      String inputSourceMap,
      String sourceMapOutputFileName)
      throws JavaScriptParsingException {
    Preconditions.checkNotNull(renameMap);
    Node jsAst = parse(js, inputFileName);
    ImmutableSet<RenameMode> renameMode =
        isPolymer05Javascript(jsAst)
            ? ImmutableSet.<RenameMode>of(RenameMode.RENAME_PROPERTIES)
            : ImmutableSet.<RenameMode>of();

    return outputSource(
        renameNode(renameMap, jsAst, renameMode),
        outputFormat,
        inputFileName,
        inputSourceMap,
        sourceMapOutputFileName);
  }

  /**
   * Renames JavaScript with Property Renaming. This is primarily used for code that predated the
   * Closure Polymer Pass.
   *
   * @param renameMap A mapping from symbol to renamed symbol.
   * @param js The JavaScript code.
   * @return JavaScript code with renames applied.
   * @throws JavaScriptParsingException if parse errors were encountered.
   */
  public static String renameProperties(ImmutableMap<String, String> renameMap, String js)
      throws JavaScriptParsingException {
    Preconditions.checkNotNull(renameMap);
    return toSource(
        renameNode(renameMap, parse(js), ImmutableSet.<RenameMode>of(RenameMode.RENAME_PROPERTIES)),
        ImmutableSet.<OutputFormat>of());
  }

  /**
   * Renames properties, renames variables, and reformats Polymer JavaScript-like expressions.
   *
   * @param renameMap A mapping from symbol to renamed symbol.
   * @param js The JavaScript code.
   * @return The JavaScript-like expression with renames applied.
   * @throws JavaScriptParsingException if parse errors were encountered.
   */
  public static String renamePolymerJsExpression(ImmutableMap<String, String> renameMap, String js)
      throws JavaScriptParsingException {
    Preconditions.checkNotNull(renameMap);
    try {
      // Add parenthesis to convince the parser that the input is a value expression.
      String renamed =
          toSource(
              renameNode(
                  renameMap,
                  parse("(" + js + ")"),
                  ImmutableSet.of(RenameMode.RENAME_PROPERTIES, RenameMode.RENAME_VARIABLES)),
              ImmutableSet.<OutputFormat>of(OutputFormat.SINGLE_QUOTE_STRINGS));
      // Trim trailing semicolon since Polymer JavaScript-like expressions don't have this.
      return renamed.substring(0, renamed.length() - 1);
    } catch (JavaScriptParsingException javaScriptParsingException) {
      // If we're here, the Closure Compiler couldn't quite figure it out. Fallback to Polymer
      // style expression parsing to see if we can fix it up manually. If not, forward the error.
      Matcher methodMatcher = METHOD_PATTERN.matcher(js);
      if (methodMatcher.matches()) {
        String methodName = renamePolymerPathExpression(renameMap, methodMatcher.group(1));
        String[] arguments =
            ARGUMENT_SPLITTER.splitToList(methodMatcher.group(2)).toArray(new String[0]);
        for (int i = 0; i < arguments.length; i++) {
          arguments[i] = renamePolymerPathExpression(renameMap, arguments[i]);
        }
        return String.format("%s(%s)", methodName, ARGUMENT_JOINER.join(arguments));
      } else if (js.contains(".")) {
        return renamePolymerPathExpression(renameMap, js);
      }
      throw javaScriptParsingException;
    }
  }

  /**
   * Renames path expressions without using the Closure Compiler for parsing.
   *
   * @param renameMap A mapping from symbol to renamed symbol.
   * @param pathExpression The path expression to rename.
   * @return The renamed path expression.
   */
  private static String renamePolymerPathExpression(
      ImmutableMap<String, String> renameMap, String pathExpression) {
    if (renameMap.containsKey(pathExpression)) {
      return renameMap.get(pathExpression);
    } else if (pathExpression.contains(".")) {
      String[] components =
          PROPERTY_EXPRESSION_SPLITTER.splitToList(pathExpression).toArray(new String[0]);
      for (int i = 0; i < components.length; i++) {
        components[i] = renamePolymerPathExpression(renameMap, components[i]);
      }
      return PROPERTY_EXPRESSION_JOINER.join(components);
    }
    return pathExpression;
  }

  private static Node parse(String js) throws JavaScriptParsingException {
    return parse(js, PLACEHOLDER_FILE_NAME);
  }

  /**
   * Parses the given JavaScript string into an abstract syntax tree.
   *
   * @param js The JavaScript code.
   * @param inputFileName The source filename to associate with the input JS.
   * @return An abstract syntax tree.
   * @throws JavaScriptParsingException if parse errors were encountered.
   */
  private static Node parse(String js, String inputFileName) throws JavaScriptParsingException {
    StaticSourceFile file = new SimpleSourceFile(inputFileName, false);
    Config config = ParserRunner.createConfig(LanguageMode.ECMASCRIPT6, null, StrictMode.SLOPPY);
    JavaScriptErrorReporter errorReporter = new JavaScriptErrorReporter(js);
    Node script = ParserRunner.parse(file, js, config, errorReporter).ast;
    if (script == null) {
      throw new JavaScriptParsingException(errorReporter.getWarningAndErrorOutput());
    }
    return script;
  }

  /**
   * Returns true if the supplied node is Polymer 0.5 style JavaScript.
   *
   * @param node The JavaScript abstract syntax tree to check.
   */
  private static boolean isPolymer05Javascript(Node node) {
    if (isPolymerCall(node) && node.hasMoreThanOneChild()) {
      Node firstArgument = node.getSecondChild();
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
   *
   * @param node The JavaScript abstract syntax tree.
   * @param outputFormat The source output format options.
   * @return The equivalent JavaScript source.
   */
  private static String toSource(Node node, ImmutableSet<OutputFormat> outputFormat) {
    return outputSource(node, outputFormat, PLACEHOLDER_FILE_NAME, null, null);
  }

  /**
   * Outputs the source equivalent of the abstract syntax tree, optionally generating a sourcemap.
   *
   * @param node The JavaScript abstract syntax tree.
   * @param outputFormat The source output format options.
   * @param inputFileName The source file name to associate with the input node, used for sourcemap
   *     generation.
   * @param inputSourceMap The content of the input sourcemap.
   * @param sourceMapOutputFileName The name of the output sourcemap.
   * @return The equivalent JavaScript source.
   */
  private static String outputSource(
      Node node,
      ImmutableSet<OutputFormat> outputFormat,
      String inputFileName,
      String inputSourceMap,
      String sourceMapOutputFileName) {
    CompilerOptions options = new CompilerOptions();
    options.setPrettyPrint(outputFormat.contains(OutputFormat.PRETTY));
    options.setPreferSingleQuotes(outputFormat.contains(OutputFormat.SINGLE_QUOTE_STRINGS));
    // The Closure Compiler treats the 'use strict' directive as a property of a node. CodeBuilder
    // doesn't consider directives during its code generation. Instead, it inserts the 'use strict'
    // directive if it is in a strict language mode.
    Set<String> directives = node.getDirectives();
    if ((directives != null) && directives.contains("use strict")) {
      options.setLanguage(CompilerOptions.LanguageMode.ECMASCRIPT6_STRICT);
    }
    options.skipAllCompilerPasses();

    if (inputSourceMap != null) {
      SourceFile sourceMapSourceFile = SourceFile.fromCode("input.sourcemap", inputSourceMap);
      ImmutableMap<String, SourceMapInput> inputSourceMaps =
          ImmutableMap.of(inputFileName, new SourceMapInput(sourceMapSourceFile));
      options.setInputSourceMaps(inputSourceMaps);
      options.setApplyInputSourceMaps(true);
      // Simply setting the path to any non-null value will trigger source map generation.
      // Since sourceMapOutputPath is handled by AbstractCommandLineRunner and not the Compiler
      // itself, we manually output the final sourcemap below.
      options.setSourceMapOutputPath("/dev/null");
    }

    Compiler compiler = new Compiler();
    compiler.disableThreads();
    compiler.initOptions(options);
    compiler.initBasedOnOptions();
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    compiler.toSource(cb, 0, node);

    if (inputFileName != null && inputSourceMap != null && sourceMapOutputFileName != null) {
      try {
        FileOutputStream fileOut = new FileOutputStream(sourceMapOutputFileName);
        OutputStreamWriter out = new OutputStreamWriter(fileOut, UTF_8);
        compiler.getSourceMap().appendTo(out, "renamed.js");
        out.close();
      } catch (Exception e) {
        System.err.println(e + "Error writing output sourcemap.");
      }
    }

    return cb.toString();
  }

  /**
   * Applies the rename map to the provided JavaScript abstract syntax tree.
   *
   * @param renameMap A mapping from symbol to renamed symbol.
   * @param current The JavaScript abstract syntax tree to rename. Note that this method will mutate
   *     |current| with the renames.
   * @param renameMode Variable renaming mode to use.
   * @return The renamed abstract syntax tree.
   */
  private static Node renameNode(
      ImmutableMap<String, String> renameMap, Node current, ImmutableSet<RenameMode> renameMode) {
    switch (current.getToken()) {
      case CALL:
        if (isInObjectLit(current)) {
          renameCall(renameMap, current);
        }
        break;
      case GETPROP:
        if (renameMode.contains(RenameMode.RENAME_PROPERTIES)) {
          if (current.hasMoreThanOneChild()) {
            Node secondChild = current.getSecondChild();
            if (secondChild.isString()) {
              renamePolymerPropertyStringNode(renameMap, secondChild);
            }
          }
        }
        break;
      case NAME:
        if (renameMode.contains(RenameMode.RENAME_VARIABLES)) {
          renamePolymerPropertyStringNode(renameMap, current);
        }
        break;
      case OBJECTLIT:
        renameObjectLiteral(renameMap, current);
        break;
      case STRING_KEY:
        if (renameMode.contains(RenameMode.RENAME_PROPERTIES)) {
          renamePolymerPropertyStringNode(renameMap, current);
        }
        break;
      default:
        // Ignore the other node types.
    }
    for (Node child : current.children()) {
      renameNode(renameMap, child, renameMode);
    }
    return current;
  }

  /**
   * Renames Polymer property changed object property identifiers (*Changed properties).
   *
   * @param renameMap A mapping from symbol to renamed symbol.
   * @param node The string node containing the property changed identifier.
   */
  private static void renamePolymerPropertyStringNode(
      ImmutableMap<String, String> renameMap, Node node) {
    String name = node.getString();
    if (renameMap.containsKey(name)) {
      node.setString(renameMap.get(name));
    } else if (name.endsWith(CHANGED_SUFFIX)) {
      String basename = name.substring(0, name.length() - CHANGED_SUFFIX.length());
      if (renameMap.containsKey(basename)) {
        node.setString(renameMap.get(basename) + CHANGED_SUFFIX);
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

  private static boolean isInObjectLit(Node node) {
    while (node != null) {
      if (node.isObjectLit()) {
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
   * Renames calls that could include property string references.
   *
   * @param renameMap A mapping from symbol to renamed symbol.
   * @param call The call node to rename.
   */
  private static void renameCall(ImmutableMap<String, String> renameMap, Node call) {
    if (call.getChildCount() == 3) {
      /* Rename Polymer.IronA11yKeysBehavior.addOwnKeyBinding(eventString, methodName). */
      if (isThisCallWithMethodName(renameMap, call, "addOwnKeyBinding")) {
        // Children [0=this.addOwnKeyBinding, 1=eventString, 2=methodName]
        renameStringNode(renameMap, call.getChildAtIndex(2));
      }
    } else if (call.getChildCount() == 4) {
      /* Rename PolymerElement.prototype.{un}listen(node, eventName, methodName). */
      if (isThisCallWithMethodName(renameMap, call, "listen")
          || isThisCallWithMethodName(renameMap, call, "unlisten")) {
        // Children [0=this.{un}listen, 1=node, 2=eventName, 3=methodName]
        renameStringNode(renameMap, call.getChildAtIndex(3));
      }
    }
  }

  private static boolean isThisCallWithMethodName(
      ImmutableMap<String, String> renameMap, Node call, String methodName) {
    Node maybeMethodNameGetProp = call.getFirstChild();
    if (maybeMethodNameGetProp.isGetProp()
        && maybeMethodNameGetProp.hasMoreThanOneChild()
        && maybeMethodNameGetProp.getFirstChild().isThis()) {
      Node maybeMethodName = maybeMethodNameGetProp.getSecondChild();
      if (maybeMethodName.isString()) {
        String maybeMethodNameString = maybeMethodName.getString();
        return maybeMethodNameString.equals(methodName)
            || (renameMap.containsKey(methodName)
                && maybeMethodNameString.equals(renameMap.get(methodName)));
      }
    }
    return false;
  }

  /**
   * Renames all object literals that are standalone or contained in a Polymer v0.8 style call. This
   * allows behaviors coverage, which are indistinguishable from regular JavaScript objects.
   *
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
   * Forwards renames to Polymer-relevant properties in the specified object map.
   *
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

    // Rename the keyBindings string to method string map using in Polymer.IronA11yKeysBehavior.
    Node keyBindingsNode = objectMap.get("keyBindings");
    if ((keyBindingsNode != null) && keyBindingsNode.isObjectLit()) {
      renameKeyBindingsNode(renameMap, keyBindingsNode);
    }

    if (renameMap.containsKey("keyBindings")) {
      Node renamedKeyBindingsNode = objectMap.get(renameMap.get("keyBindings"));
      if ((renamedKeyBindingsNode != null) && renamedKeyBindingsNode.isObjectLit()) {
        renameKeyBindingsNode(renameMap, renamedKeyBindingsNode);
      }
    }
  }

  private static void renameKeyBindingsNode(ImmutableMap<String, String> renameMap, Node node) {
    ImmutableMap<String, Node> keyBindingsMap = convertObjectLitNodeToMap(node);
    for (Node keyBindingMethodStringNode : keyBindingsMap.values()) {
      if (!keyBindingMethodStringNode.isString()) {
        // A non-string means it's a map we don't expect.
        break;
      }
      renameStringNode(renameMap, keyBindingMethodStringNode);
    }
  }

  /**
   * Renames a string node under variable naming rules similar to Polymer databinding expressions.
   *
   * @param renameMap A mapping from symbol to renamed symbol.
   * @param node String node to rename under variable renaming rules. Can be null. Will not attempt
   *     a rename if the node is not a string node.
   */
  private static void renamePolymerJsStringNode(ImmutableMap<String, String> renameMap, Node node) {
    if (node == null || !node.isString()) {
      return;
    }

    String js = node.getString();
    try {
      js = renamePolymerJsExpression(renameMap, node.getString());
    } catch (JavaScriptParsingException e) {
      System.err.println(e);
    }
    node.setString(js);
  }

  /**
   * Renames a string node as if the entire string contained the symbol.
   *
   * @param renameMap A mapping from symbol to renamed symbol.
   * @param node String node to rename in entirety. Can be null. Will not attempt a rename if the
   *     node is not a string node.
   */
  private static void renameStringNode(ImmutableMap<String, String> renameMap, Node node) {
    if (node == null || !node.isString()) {
      return;
    }

    String symbolName = node.getString();
    if (renameMap.containsKey(symbolName)) {
      node.setString(renameMap.get(symbolName));
    }
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
   * While most of the JavaScript will pass through the Closure Compiler with syntax checking, the
   * Polymer HTML databinding expressions will not. This outputs errors directly from the Closure
   * Compiler to System.err.
   */
  private static class JavaScriptErrorReporter implements ErrorReporter {
    private final String[] jsLines;
    private final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    private final PrintStream outputStream = new PrintStream(byteArrayOutputStream);

    /**
     * Constructs a JavaScriptErrorReporter that outputs warnings and errors using |js| as context.
     *
     * @param js JavaScript source for line context.
     */
    public JavaScriptErrorReporter(String js) {
      this.jsLines = js.split("\\r\\n|\\r|\\n");
    }

    /** Returns the stream output of warnings and errors as a string. */
    public String getWarningAndErrorOutput() {
      return byteArrayOutputStream.toString();
    }

    @Override
    public void warning(String message, String sourceName, int line, int lineOffset) {
      outputStream.printf("WARNING: (%d:%d) %s%n", line, lineOffset, message);
      printSource(9, line, lineOffset);
    }

    @Override
    public void error(String message, String sourceName, int line, int lineOffset) {
      outputStream.printf("ERROR: (%d:%d) %s%n", line, lineOffset, message);
      printSource(7, line, lineOffset);
    }

    private void printSource(int columnPadding, int line, int lineOffset) {
      if (line <= jsLines.length) {
        printSpaces(columnPadding);
        outputStream.printf("%s%n", jsLines[line - 1]);

        printSpaces(columnPadding + lineOffset - 1);
        outputStream.println("^");
      }
    }

    private void printSpaces(int numberOfSpaces) {
      for (int i = 0; i < numberOfSpaces; i++) {
        outputStream.print(" ");
      }
    }
  }
}
