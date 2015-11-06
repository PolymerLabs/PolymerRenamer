package com.google.polymer;

/**
 * Contains all information to output to the user on JavaScript parsing warnings and errors.
 */
public final class JavaScriptParsingException extends Exception {

  private final String warningAndErrorOutput;

  /**
   * Constructs a JavaScriptParsingException with the warning and error output available as a
   * string.
   * @param warningAndErrorOutput The warning and error output for this parsing exception.
   */
  public JavaScriptParsingException(String warningAndErrorOutput) {
    this.warningAndErrorOutput = warningAndErrorOutput;
  }

  @Override
  public String toString() {
    return warningAndErrorOutput;
  }
}

