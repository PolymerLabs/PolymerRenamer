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

import com.google.common.collect.ImmutableMap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for JsRenamer.
 */
@RunWith(JUnit4.class)
public class JsRenamerTest {
  private static final ImmutableMap<String, String> emptyMap = ImmutableMap.of();

  private static final ImmutableMap<String, String> testMap =
      ImmutableMap.of(
          "a", "renamedA",
          "longName", "rb",
          "three", "renamed3");

  @Test
  public void testRenamePropertiesEmptyMap() {
    assertEquals("no;", JsRenamer.renameProperties(emptyMap, "no;"));
    assertEquals("no.renames;", JsRenamer.renameProperties(emptyMap, "no.renames;"));
    assertEquals("no.renames.here;", JsRenamer.renameProperties(emptyMap, "no.renames.here;"));
    assertEquals("no.renames().here;", JsRenamer.renameProperties(emptyMap, "no.renames().here;"));
    assertEquals("no.renames.here();", JsRenamer.renameProperties(emptyMap, "no.renames.here();"));
  }

  @Test
  public void testRenamePropertiesSingleRename() {
    assertEquals("longName;", JsRenamer.renameProperties(testMap, "longName;"));
    assertEquals("exp.renamedA;", JsRenamer.renameProperties(testMap, "exp.a;"));
    assertEquals("exp.rb.A;", JsRenamer.renameProperties(testMap, "exp.longName.A;"));
    assertEquals("no[\"three\"].renamed3().LONGNAME;",
        JsRenamer.renameProperties(testMap, "no['three'].three().LONGNAME;"));
    assertEquals("rb.renamed3.renamedA();",
        JsRenamer.renameProperties(testMap, "rb.renamed3.a();"));
  }

  @Test
  public void testRenamePropertiesMultipleRename() {
    assertEquals("exp.rb.renamedA;", JsRenamer.renameProperties(testMap, "exp.longName.a;"));
    assertEquals("no[\"three\"].renamed3().renamedA;",
        JsRenamer.renameProperties(testMap, "no['three'].three().a;"));
    assertEquals("rb.rb.renamedA();", JsRenamer.renameProperties(testMap, "rb.longName.a();"));
  }

  @Test
  public void testRenamePropertiesPropertyChanged() {
    assertEquals("exp.rbChanged;", JsRenamer.renameProperties(testMap, "exp.longNameChanged;"));
  }

  @Test
  public void testRenamePropertiesAndVariablesEmptyMap() {
    assertEquals("no;", JsRenamer.renamePropertiesAndVariables(emptyMap, "no;"));
    assertEquals("no.renames;", JsRenamer.renamePropertiesAndVariables(emptyMap, "no.renames;"));
    assertEquals("no.renames.here;",
        JsRenamer.renamePropertiesAndVariables(emptyMap, "no.renames.here;"));
    assertEquals("no.renames().here;",
        JsRenamer.renamePropertiesAndVariables(emptyMap, "no.renames().here;"));
    assertEquals("no.renames.here();",
        JsRenamer.renamePropertiesAndVariables(emptyMap, "no.renames.here();"));
  }

  @Test
  public void testRenamePropertiesAndVariablesSingleRename() {
    assertEquals("rb;", JsRenamer.renamePropertiesAndVariables(testMap, "longName;"));
    assertEquals("exp.renamedA;", JsRenamer.renamePropertiesAndVariables(testMap, "exp.a;"));
    assertEquals("exp.rb.A;", JsRenamer.renamePropertiesAndVariables(testMap, "exp.longName.A;"));
    assertEquals("no[\"three\"].renamed3().LONGNAME;",
        JsRenamer.renamePropertiesAndVariables(testMap, "no['three'].three().LONGNAME;"));
    assertEquals("rb.renamed3.renamedA();",
        JsRenamer.renamePropertiesAndVariables(testMap, "rb.renamed3.a();"));
  }

  @Test
  public void testRenamePropertiesAndVariablesMultipleRename() {
    assertEquals("exp.rb.renamedA;",
        JsRenamer.renamePropertiesAndVariables(testMap, "exp.longName.a;"));
    assertEquals("no[\"three\"].renamed3().renamedA;",
        JsRenamer.renamePropertiesAndVariables(testMap, "no['three'].three().a;"));
    assertEquals("rb.rb.renamedA();",
        JsRenamer.renamePropertiesAndVariables(testMap, "rb.longName.a();"));
    assertEquals("renamedA.foo;", JsRenamer.renamePropertiesAndVariables(testMap, "a.foo;"));
    assertEquals("renamedA.rb;", JsRenamer.renamePropertiesAndVariables(testMap, "a.longName;"));
  }

  @Test
  public void testRenamePropertiesAndVariablesPropertyChanged() {
    assertEquals("exp.rbChanged;",
        JsRenamer.renamePropertiesAndVariables(testMap, "exp.longNameChanged;"));
  }
}
