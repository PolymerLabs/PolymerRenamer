/*
 * Copyright (c) 2015 The Polymer Project Authors. All rights reserved.
 * This code may only be used under the BSD style license found at http://polymer.github.io/LICENSE
 * The complete set of authors may be found at http://polymer.github.io/AUTHORS
 * The complete set of contributors may be found at http://polymer.github.io/CONTRIBUTORS
 * Code distributed by Google as part of the polymer project is also
 * subject to an additional IP rights grant found at http://polymer.github.io/PATENTS
 */

package com.google.polymer;

import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.polymer.PolymerDatabindingLexer.Token;

import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Document.OutputSettings;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities.EscapeMode;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * Static methods that perform HTML code transformations based off of a rename map.
 */
public final class HtmlRenamer {

  /**
   * HTML tags of interest to the HtmlRenamer.
   */
  private enum HtmlTags {
    DOM_MODULE("dom-module"),
    POLYMER_ELEMENT("polymer-element"),
    SCRIPT("script");

    private String tagName;

    private HtmlTags(String tagName) {
      this.tagName = tagName;
    }

    public String getName() {
      return tagName;
    }
  }

  // Joiner for HTML attribute lists.
  private static final Joiner ATTRIBUTE_LIST_JOINER = Joiner.on(" ");

  // Splitter for HTML attribute lists.
  private static final Splitter ATTRIBUTE_LIST_SPLITTER = Splitter.on(" ");

  // Joiner for Polymer style event expressions.
  private static final Joiner EVENT_EXPRESSION_JOINER = Joiner.on("::");

  // Splitter for Polymer style event expressions.
  private static final Splitter EVENT_EXPRESSION_SPLITTER = Splitter.on("::");

  private HtmlRenamer() {}

  private static enum RenameMode {
    POLYMER_0_5,
    POLYMER_0_8,
  }

  private static class DatabindingRenamer implements NodeVisitor {

    private final ImmutableMap<String, String> renameMap;
    private final HtmlRenamer.RenameMode renameMode;

    /** true if we are inside a script element. */
    private boolean insideScriptElement = false;

    /**
     * Constructs the DatabindingRenamer to rename according to {@code renameMap}.
     * @param renameMap A mapping from symbol to renamed symbol.
     */
    public DatabindingRenamer(
        ImmutableMap<String, String> renameMap, HtmlRenamer.RenameMode renameMode) {
      this.renameMap = Preconditions.checkNotNull(renameMap);
      this.renameMode = renameMode;
    }

    @Override
    public void head(Node node, int depth) {
      if (node instanceof Element) {
        Element element = (Element) node;
        String tagName = element.tag().getName();
        if (tagName.equals(HtmlTags.POLYMER_ELEMENT.getName())) {
          renameAttributesAttributeValue(element);
        } else if (tagName.equals("script")) {
          insideScriptElement = true;
        } else {
          renameAllAnnotatedEventAttributes(element);
          renameAllAttributeValues(element);
        }
      } else if (node instanceof TextNode) {
        TextNode textNode = (TextNode) node;
        textNode.text(renameStringWithDatabindingDirectives(textNode.getWholeText()));
      } else if (insideScriptElement && node instanceof DataNode) {
        DataNode dataNode = (DataNode) node;
        String js = dataNode.getWholeData();
        try {
          js = JsRenamer.renameProperties(renameMap, js);
        } catch (JavaScriptParsingException e) {
          System.err.println(e);
        }
        dataNode.setWholeData(js);
      }
    }

    @Override
    public void tail(Node node, int depth) {
      if (node instanceof Element) {
        Element element = (Element) node;
        if (element.tag().equals(HtmlTags.SCRIPT.getName())) {
          insideScriptElement = false;
        }
      }
    }

    private void renameAttributesAttributeValue(Element element) {
      String attributesValue = element.attr("attributes");
      boolean modified = false;
      String properties[] =
          ATTRIBUTE_LIST_SPLITTER.splitToList(attributesValue).toArray(new String[0]);
      for (int i = 0; i < properties.length; i++) {
        String property = properties[i];
        if (renameMap.containsKey(property)) {
          properties[i] = renameMap.get(property);
          modified = true;
        }
      }
      if (modified) {
        element.attr("attributes", ATTRIBUTE_LIST_JOINER.join(properties));
      }
    }

    private void renameAllAttributeValues(Element element) {
      Attributes attributes = element.attributes();
      if (attributes != null) {
        for (Attribute attribute : attributes) {
          attribute.setValue(renameStringWithDatabindingDirectives(attribute.getValue()));
        }
      }
    }

    private void renameAllAnnotatedEventAttributes(Element element) {
      Attributes attributes = element.attributes();
      if (attributes != null) {
        for (Attribute attribute : attributes) {
          String key = attribute.getKey();
          if (key.startsWith("on-")) {
            String renamedEventHandler = renameMap.get(attribute.getValue());
            if (renamedEventHandler != null) {
              attribute.setValue(renamedEventHandler);
            }
          }
        }
      }
    }

    private String renameStringWithDatabindingDirectives(String input) {
      Token tokens[] = PolymerDatabindingLexer.lex(input);
      StringBuilder sb = new StringBuilder();
      boolean insideBraces = false;
      for (Token t : tokens) {
        switch (t.type) {
          case STRING:
            if (insideBraces) {
              sb.append(renameDatabindingExpression(t.value));
            } else {
              sb.append(t.value);
            }
            break;
          case OPENCURLYBRACES:
            insideBraces = true;
            sb.append(t.value);
            break;
          case CLOSECURLYBRACES:
            insideBraces = false;
            sb.append(t.value);
            break;
          case OPENSQUAREBRACES:
            if (renameMode == HtmlRenamer.RenameMode.POLYMER_0_8) {
              insideBraces = true;
            }
            sb.append(t.value);
            break;
          case CLOSESQUAREBRACES:
            if (renameMode == HtmlRenamer.RenameMode.POLYMER_0_8) {
              insideBraces = false;
            }
            sb.append(t.value);
            break;
        }
      }
      return sb.toString();
    }

    private String renameDatabindingExpression(String expression) {
      // Polymer 1.0 has two-way native element binding syntax which isn't legal Javascript.
      // See https://www.polymer-project.org/1.0/docs/devguide/data-binding.html#two-way-native.
      // Expression Format: {{expression::eventName}}
      // We'll treat this as {{expression::notRenamed}}
      String[] components =
          EVENT_EXPRESSION_SPLITTER.splitToList(expression).toArray(new String[0]);
      try {
        components[0] = JsRenamer.renamePolymerJsExpression(renameMap, components[0]);
      } catch (JavaScriptParsingException e) {
        System.err.println(e);
      }
      return EVENT_EXPRESSION_JOINER.join(components);
    }
  }

  public static String rename(ImmutableMap<String, String> renameMap, String htmlString) {
    Preconditions.checkNotNull(renameMap);
    Document document = Parser.parse(htmlString, "");
    OutputSettings outputSettings = document.outputSettings();
    outputSettings.prettyPrint(false);
    outputSettings.escapeMode(EscapeMode.extended);
    RenameMode renameMode = RenameMode.POLYMER_0_8;
    Elements polymerDomElements = document.getElementsByTag(HtmlTags.DOM_MODULE.getName());
    if (polymerDomElements.isEmpty()) {
      renameMode = HtmlRenamer.RenameMode.POLYMER_0_5;
      polymerDomElements = document.getElementsByTag(HtmlTags.POLYMER_ELEMENT.getName());
    }
    List<String> polymerCustomElements = new ArrayList<String>();
    NodeTraversor polymerDomElementTraversor =
        new NodeTraversor(new DatabindingRenamer(renameMap, renameMode));
    for (Element polymerDomElement : polymerDomElements) {
      if (renameMode == RenameMode.POLYMER_0_8) {
        String polymerElementTagName = polymerDomElement.attr("name");
        if (!polymerElementTagName.isEmpty()) {
          polymerCustomElements.add(polymerElementTagName);
        }
      }
      polymerDomElementTraversor.traverse(polymerDomElement);
    }

    for (String polymerElementTagName : polymerCustomElements) {
      Elements customElements = document.getElementsByTag(polymerElementTagName);
      for (Element customElement : customElements) {
        renameAllAttributeKeys(renameMap, customElement);
      }
    }

    return document.toString();
  }

  private static void renameAllAttributeKeys(
      ImmutableMap<String, String> renameMap, Element element) {
    Attributes attributes = element.attributes();
    for (Attribute attribute : attributes) {
      String key = attribute.getKey();
      // Polymer events are referenced as strings. As a result they do not participate in renaming.
      // Additionally, it is not valid to have a Polymer property start with "on".
      if (!key.startsWith("on-")) {
        String renamedProperty = renameMap.get(
            CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, key));
        if (renamedProperty != null) {
          attribute.setKey(CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, renamedProperty));
        }
      }
    }
  }
}
