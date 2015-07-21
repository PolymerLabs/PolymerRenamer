package com.google.polymer;

/**
 * Contains all information to output to the user on JavaScript parsing warnings and errors.
 */
public class JavaScriptParsingException extends Exception {

  private final String warningAndErrorOutput;

  /**
   * Constructs a JavaScriptParsingException with the warning and error output available as a
   * string.
   * @param warningAndErrorOutput
   */
  public JavaScriptParsingException(String warningAndErrorOutput) {
    this.warningAndErrorOutput = warningAndErrorOutput;
  }

  @Override
  public String toString() {
    return warningAndErrorOutput;
  }
}

