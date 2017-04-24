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
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

/** The Polymer Property and Databinding Renamer. */
public final class PolymerRenamer {
  /** The command line arguments accepted by the PolymerRenamer. */
  private static class Args {
    @Option(name = "--inputFilename", usage = "The input file to rename", required = true)
    private String inputFilename;

    @Option(
      name = "--propertyMapFilename",
      usage = "The property map to use for renaming",
      required = true
    )
    private String propertyMapFilename;

    @Option(name = "--jsPrettyPrint", usage = "Whether to pretty print the output JS")
    private boolean prettyPrint = false;
  }

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

  /** Invokes the Polymer Property Renamer. */
  public static void main(String[] args) {
    Args renamerArgs = new Args();
    CmdLineParser parser = new CmdLineParser(renamerArgs);

    try {
      parser.parseArgument(args);
    } catch (CmdLineException e) {
      System.out.println(e.getLocalizedMessage());
      System.out.println();
      System.out.println("The Polymer Renamer");
      System.out.println("Arguments:");
      parser.printUsage(System.out);
      return;
    }

    ImmutableMap<String, String> renameMap;
    try {
      renameMap = getRenameMap(renamerArgs.propertyMapFilename);
    } catch (FileNotFoundException e) {
      System.err.println("Unable to read property map file: " + renamerArgs.propertyMapFilename);
      return;
    }

    String inputFileContent;
    try {
      inputFileContent = getFileContent(renamerArgs.inputFilename);
    } catch (FileNotFoundException e) {
      System.err.println("Unable to read input file: " + renamerArgs.inputFilename);
      return;
    }

    if (renamerArgs.inputFilename.endsWith("html")) {
      System.out.print(HtmlRenamer.rename(renameMap, inputFileContent));
    } else if (renamerArgs.inputFilename.endsWith("js")) {
      try {
        ImmutableSet<JsRenamer.OutputFormat> outputFormat =
            renamerArgs.prettyPrint
                ? ImmutableSet.<JsRenamer.OutputFormat>of(JsRenamer.OutputFormat.PRETTY)
                : ImmutableSet.<JsRenamer.OutputFormat>of();
        System.out.print(JsRenamer.rename(renameMap, inputFileContent, outputFormat));
      } catch (JavaScriptParsingException e) {
        System.err.printf("Error encountered parsing %s.%n", renamerArgs.inputFilename);
        System.err.println(e);
        System.exit(1);
      }
    }
  }
}
