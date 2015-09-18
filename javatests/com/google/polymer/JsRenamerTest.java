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

import org.junit.Assert;
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
  public void testRenamePropertiesEmptyMap() throws Exception {
    assertEquals("no;", JsRenamer.renameProperties(emptyMap, "no;"));
    assertEquals("no.renames;", JsRenamer.renameProperties(emptyMap, "no.renames;"));
    assertEquals("no.renames.here;", JsRenamer.renameProperties(emptyMap, "no.renames.here;"));
    assertEquals("no.renames().here;", JsRenamer.renameProperties(emptyMap, "no.renames().here;"));
    assertEquals("no.renames.here();", JsRenamer.renameProperties(emptyMap, "no.renames.here();"));
  }

  @Test
  public void testRenamePropertiesSingleRename() throws Exception {
    assertEquals("longName;", JsRenamer.renameProperties(testMap, "longName;"));
    assertEquals("exp.renamedA;", JsRenamer.renameProperties(testMap, "exp.a;"));
    assertEquals("exp.rb.A;", JsRenamer.renameProperties(testMap, "exp.longName.A;"));
    assertEquals("no[\"three\"].renamed3().LONGNAME;",
        JsRenamer.renameProperties(testMap, "no['three'].three().LONGNAME;"));
    assertEquals("rb.renamed3.renamedA();",
        JsRenamer.renameProperties(testMap, "rb.renamed3.a();"));
  }

  @Test
  public void testRenamePropertiesMultipleRename() throws Exception {
    assertEquals("exp.rb.renamedA;", JsRenamer.renameProperties(testMap, "exp.longName.a;"));
    assertEquals("no[\"three\"].renamed3().renamedA;",
        JsRenamer.renameProperties(testMap, "no['three'].three().a;"));
    assertEquals("rb.rb.renamedA();", JsRenamer.renameProperties(testMap, "rb.longName.a();"));
  }

  @Test
  public void testRenamePropertiesPropertyChanged() throws Exception {
    assertEquals("exp.rbChanged;", JsRenamer.renameProperties(testMap, "exp.longNameChanged;"));
  }

  @Test
  public void testRenamePolymerJsExpressionEmptyMap() throws Exception {
    assertEquals("no", JsRenamer.renamePolymerJsExpression(emptyMap, "no"));
    assertEquals("no.renames", JsRenamer.renamePolymerJsExpression(emptyMap, "no.renames"));
    assertEquals("no.renames.here",
        JsRenamer.renamePolymerJsExpression(emptyMap, "no.renames.here"));
    assertEquals("no.renames().here",
        JsRenamer.renamePolymerJsExpression(emptyMap, "no.renames().here"));
    assertEquals("no.renames.here()",
        JsRenamer.renamePolymerJsExpression(emptyMap, "no.renames.here()"));
  }

  @Test
  public void testRenamePolymerJsExpressionSingleRename() throws Exception {
    assertEquals("rb", JsRenamer.renamePolymerJsExpression(testMap, "longName"));
    assertEquals("exp.renamedA", JsRenamer.renamePolymerJsExpression(testMap, "exp.a"));
    assertEquals("exp.rb.A", JsRenamer.renamePolymerJsExpression(testMap, "exp.longName.A"));
    assertEquals("no[\'three\'].renamed3().LONGNAME",
        JsRenamer.renamePolymerJsExpression(testMap, "no['three'].three().LONGNAME"));
    assertEquals("rb.renamed3.renamedA()",
        JsRenamer.renamePolymerJsExpression(testMap, "rb.renamed3.a()"));
  }

  @Test
  public void testRenamePolymerJsExpressionMultipleRename() throws Exception {
    assertEquals("exp.rb.renamedA",
        JsRenamer.renamePolymerJsExpression(testMap, "exp.longName.a"));
    assertEquals("no[\'three\'].renamed3().renamedA",
        JsRenamer.renamePolymerJsExpression(testMap, "no['three'].three().a"));
    assertEquals("rb.rb.renamedA()",
        JsRenamer.renamePolymerJsExpression(testMap, "rb.longName.a()"));
    assertEquals("renamedA.foo", JsRenamer.renamePolymerJsExpression(testMap, "a.foo"));
    assertEquals("renamedA.rb", JsRenamer.renamePolymerJsExpression(testMap, "a.longName"));
  }

  @Test
  public void testRenamePolymerJsExpressionPropertyChanged() throws Exception {
    assertEquals("exp.rbChanged",
        JsRenamer.renamePolymerJsExpression(testMap, "exp.longNameChanged"));
  }

  @Test
  public void testError() {
    JavaScriptParsingException exception = null;
    try {
      JsRenamer.renamePolymerJsExpression(testMap, "InvalidJs(Foo.*)");
    } catch (JavaScriptParsingException e) {
      exception = e;
    }
    Assert.assertNotNull(exception);
  }
}
