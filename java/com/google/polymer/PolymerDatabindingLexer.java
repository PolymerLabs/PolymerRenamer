/*
 * Copyright (c) 2015 The Polymer Project Authors. All rights reserved.
 * This code may only be used under the BSD style license found at http://polymer.github.io/LICENSE
 * The complete set of authors may be found at http://polymer.github.io/AUTHORS
 * The complete set of contributors may be found at http://polymer.github.io/CONTRIBUTORS
 * Code distributed by Google as part of the polymer project is also
 * subject to an additional IP rights grant found at http://polymer.github.io/PATENTS
 */

package com.google.polymer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lexes an input string according to the Polymer Databinding syntax.
 * @href https://www.polymer-project.org/docs/polymer/databinding.html
 */
public class PolymerDatabindingLexer {

  private static final Pattern lexingPattern;

  static {
    StringBuilder sb = new StringBuilder();
    for (NamedPattern p : NamedPattern.values()) {
      sb.append(String.format("(?<%s>%s)|", p.name(), p.getRegex()));
    }
    // Remove Last "|"
    sb.setLength(sb.length() - 1);
    lexingPattern = Pattern.compile(sb.toString(), Pattern.DOTALL);
  }

  /**
   * The available tokens emitted from this lexer.
   */
  public static enum TokenType {
    OPENCURLYBRACES, CLOSECURLYBRACES, STRING;
  }

  /**
   * The patterns of interest in the string.
   */
  private static enum NamedPattern {
    OPENCURLYBRACES("\\{\\{"), CLOSECURLYBRACES("\\}\\}"), CHARACTER(".");

    private String regex;

    private NamedPattern(String regex) {
      this.regex = regex;
    }

    public String getRegex() {
      return regex;
    }
  }

  /**
   * A lexical token output from PolymerDatabindingLexer.lex.
   */
  public static class Token {
    public final TokenType type;
    public final String value;

    public Token(TokenType type, String value) {
      this.type = type;
      this.value = value;
    }
  }

  private PolymerDatabindingLexer() {}

  /**
   * Lexes a string into tokens based off of Polymer's databinding syntax.
   * @param input Input string to lex.
   * @return Array of tokens.
   */
  public static Token[] lex(String input) {
    List<Token> tokens = new ArrayList<>();
    Matcher matcher = lexingPattern.matcher(input);
    StringBuilder currentString = new StringBuilder();
    while (matcher.find()) {
      if (matcher.group(NamedPattern.CHARACTER.name()) != null) {
        currentString.append(matcher.group(NamedPattern.CHARACTER.name()));
      } else {
        if (currentString.length() > 0) {
          tokens.add(new Token(TokenType.STRING, currentString.toString()));
          currentString.setLength(0);
        }

        if (matcher.group(NamedPattern.OPENCURLYBRACES.name()) != null) {
          tokens.add(new Token(TokenType.OPENCURLYBRACES,
              matcher.group(NamedPattern.OPENCURLYBRACES.name())));
        } else if (matcher.group(NamedPattern.CLOSECURLYBRACES.name()) != null) {
          tokens.add(new Token(TokenType.CLOSECURLYBRACES,
              matcher.group(NamedPattern.CLOSECURLYBRACES.name())));
        }
      }
    }
    if (currentString.length() > 0) {
      tokens.add(new Token(TokenType.STRING, currentString.toString()));
      currentString.setLength(0);
    }
    return tokens.toArray(new Token[] {});
  }
}
