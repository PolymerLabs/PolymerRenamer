/*
 * Copyright (c) 2015 The Polymer Project Authors. All rights reserved.
 * This code may only be used under the BSD style license found at http://polymer.github.io/LICENSE
 * The complete set of authors may be found at http://polymer.github.io/AUTHORS
 * The complete set of contributors may be found at http://polymer.github.io/CONTRIBUTORS
 * Code distributed by Google as part of the polymer project is also
 * subject to an additional IP rights grant found at http://polymer.github.io/PATENTS
 */

package com.google.polymer;

import com.google.common.collect.ImmutableMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

/**
 * The Polymer Property and Databinding Renamer.
 */
public class PolymerRenamer {

  private PolymerRenamer() {}

  private static String getFileContent(String inputFilename) throws FileNotFoundException {
    try (Scanner s = new Scanner(new File(inputFilename))) {
      return s.useDelimiter("\\Z").next();
    }
  }

  private static ImmutableMap<String, String> getRenameMap(String inputFilename)
      throws FileNotFoundException {
    try (Scanner s = new Scanner(new File(inputFilename))) {
      ImmutableMap.Builder<String, String> renameMapBuilder = ImmutableMap.builder();
      while (s.hasNextLine()) {
        String line = s.nextLine();
        String[] components = line.split(":");
        if (components.length == 2) {
          renameMapBuilder.put(components[0], components[1]);
        }
      }
      return renameMapBuilder.build();
    }
  }

  /**
   * Invokes the Polymer Property Renamer.
   * @param args Two expected arguments, the first being the Closure property map filename and the
   *        second being the input file.
   */
  public static void main(String[] args) {
    if (args.length < 2 || args.length > 3) {
      printUsage();
      return;
    }

    boolean prettyPrint = false;
    prettyPrint = args[0].equals("--jsPrettyPrint");
    if ((prettyPrint && (args.length != 3))
        || (!prettyPrint && (args.length == 3))) {
      printUsage();
      return;
    }

    String propertyMapFilename = args[args.length - 2];
    ImmutableMap<String, String> renameMap;
    try {
      renameMap = getRenameMap(propertyMapFilename);
    } catch (FileNotFoundException e) {
      System.err.println("Unable to read property map file: " + propertyMapFilename);
      return;
    }

    String inputFilename = args[args.length - 1];
    String inputFileContent;
    try {
      inputFileContent = getFileContent(inputFilename);
    } catch (FileNotFoundException e) {
      System.err.println("Unable to read input file: " + inputFilename);
      return;
    }

    if (inputFilename.endsWith("html")) {
      System.out.print(HtmlRenamer.rename(renameMap, inputFileContent));
    } else if (inputFilename.endsWith("js")) {
      try {
        JsRenamer.OutputFormat outputFormat =
            prettyPrint ? JsRenamer.OutputFormat.PRETTY : JsRenamer.OutputFormat.MINIFIED;
        System.out.print(JsRenamer.rename(renameMap, inputFileContent, outputFormat));
      } catch (JavaScriptParsingException e) {
        System.err.printf("Error encountered parsing %s.%n", inputFilename);
        System.err.println(e);
        System.exit(1);
      }
    }
  }

  private static void printUsage() {
    System.out.println("The Polymer Renamer");
    System.out.println("Args: [--jsPrettyPrint] <Property Map Filename> <Input Filename>");
    System.out.println("  --jsPrettyPrint    For JavaScript files, output in indented form.");
  }
}
