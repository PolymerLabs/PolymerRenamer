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
    if (args.length != 2) {
      System.out.println("The Polymer Renamer");
      System.out.println("Args: <Property Map Filename> <Input Filename>");
      return;
    }

    String propertyMapFilename = args[0];
    ImmutableMap<String, String> renameMap;
    try {
      renameMap = getRenameMap(propertyMapFilename);
    } catch (FileNotFoundException e) {
      System.err.println("Unable to read property map file: " + propertyMapFilename);
      return;
    }

    String inputFilename = args[1];
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
        System.out.print(JsRenamer.rename(renameMap, inputFileContent));
      } catch (JavaScriptParsingException e) {
        System.err.printf("Error encountered parsing %s.%n", inputFilename);
        System.err.println(e);
        System.exit(1);
      }
    }
  }
}
