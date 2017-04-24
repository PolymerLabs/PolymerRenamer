/*
 * Copyright (c) 2015 The Polymer Project Authors. All rights reserved.
 * This code may only be used under the BSD style license found at http://polymer.github.io/LICENSE
 * The complete set of authors may be found at http://polymer.github.io/AUTHORS
 * The complete set of contributors may be found at http://polymer.github.io/CONTRIBUTORS
 * Code distributed by Google as part of the polymer project is also
 * subject to an additional IP rights grant found at http://polymer.github.io/PATENTS
 */

package com.google.polymer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Scanner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for PolymerRenamer.
 */
@RunWith(JUnit4.class)
public class PolymerRenamerTest {

  private static final PrintStream originalOut = System.out;
  private static final PrintStream originalErr = System.err;

  private final ByteArrayOutputStream errStream = new ByteArrayOutputStream();
  private final ByteArrayOutputStream outStream = new ByteArrayOutputStream();

  @Before
  public void setUpStreams() {
    System.setOut(new PrintStream(outStream));
    System.setErr(new PrintStream(errStream));
  }

  @After
  public void cleanUpStreams() {
    System.setErr(originalErr);
    System.setOut(originalOut);
  }

  @Test
  public void testRunPolymer0_5() {
    PolymerRenamer.main(
        new String[] {
          "--propertyMapFilename",
          getFilePathFromTestData("rename.map"),
          "--inputFilename",
          getFilePathFromTestData("source.html")
        });
    try {
      assertEquals(getFileContent(getFilePathFromTestData("source_expected.html")),
          outStream.toString());
    } catch (FileNotFoundException e) {
      fail(e.toString());
    }
    assertEquals("", errStream.toString());
  }

  @Test
  public void testRunPolymer0_8() {
    PolymerRenamer.main(
        new String[] {
          "--propertyMapFilename",
          getFilePathFromTestData("rename.map"),
          "--inputFilename",
          getFilePathFromTestData("polymer_v0.8.html")
        });
    try {
      assertEquals(getFileContent(getFilePathFromTestData("polymer_v0.8_expected.html")),
          outStream.toString());
    } catch (FileNotFoundException e) {
      fail(e.toString());
    }
    assertEquals("", errStream.toString());
  }

  @Test
  public void testRunPolymerCall() {
    PolymerRenamer.main(
        new String[] {
          "--propertyMapFilename",
          getFilePathFromTestData("rename.map"),
          "--inputFilename",
          getFilePathFromTestData("polymer_call.js")
        });
    try {
      assertEquals(getFileContent(getFilePathFromTestData("polymer_call_expected.js")),
          outStream.toString());
    } catch (FileNotFoundException e) {
      fail(e.toString());
    }
    assertEquals("", errStream.toString());
  }

  @Test
  public void testRunPolymerPrettyCall() {
    PolymerRenamer.main(
        new String[] {
          "--jsPrettyPrint",
          "--propertyMapFilename",
          getFilePathFromTestData("rename.map"),
          "--inputFilename",
          getFilePathFromTestData("polymer_call_pretty.js")
        });
    try {
      assertEquals(getFileContent(getFilePathFromTestData("polymer_call_pretty_expected.js")),
          outStream.toString());
    } catch (FileNotFoundException e) {
      fail(e.toString());
    }
    assertEquals("", errStream.toString());
  }

  @Test
  public void testRunPolymerLegacyCall() {
    PolymerRenamer.main(
        new String[] {
          "--propertyMapFilename",
          getFilePathFromTestData("rename.map"),
          "--inputFilename",
          getFilePathFromTestData("polymer_legacy_call.js")
        });
    try {
      assertEquals(getFileContent(getFilePathFromTestData("polymer_legacy_call_expected.js")),
          outStream.toString());
    } catch (FileNotFoundException e) {
      fail(e.toString());
    }
    assertEquals("", errStream.toString());
  }

  private static String getFilePathFromTestData(String filename) {
    return "javatests/com/google/polymer/testdata/" + filename;
  }

  private static String getFileContent(String inputFilename) throws FileNotFoundException {
    try (Scanner s = new Scanner(new File(inputFilename))) {
      return s.useDelimiter("\\Z").next();
    }
  }
}
