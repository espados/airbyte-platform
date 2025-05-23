/*
 * Copyright (c) 2020-2025 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.commons.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.api.client.util.Preconditions;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import io.airbyte.commons.json.JsonSchemas.FieldNameOrList;
import io.airbyte.commons.util.MoreIterators;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JSONPath is specification for querying JSON objects. More information about the specification can
 * be found here: https://goessner.net/articles/JsonPath/. For those familiar with jq, JSONPath will
 * be most recognizable as "that DSL that jq uses".
 * <p>
 * We use a java implementation of this specification (repo: https://github.com/json-path/JsonPath).
 * This class wraps that implementation to make it easier to leverage this tool internally.
 * <p>
 * GOTCHA: Keep in mind with JSONPath, depending on the query, 0, 1, or N values may be returned.
 * The pattern for handling return values is very much like writing SQL queries. When using it, you
 * must consider what the number of return values for your query might be. e.g. for this object: {
 * "alpha": [1, 2, 3] }, this JSONPath "$.alpha[*]", would return: [1, 2, 3], but this one
 * "$.alpha[0]" would return: [1]. The Java interface we place over this query system defaults to
 * returning a list for query results. In addition, we provide helper functions that will just
 * return a single value (see: {@link JsonPaths#getSingleValue(JsonNode, String)}). These should
 * only be used if it is not possible for a query to return more than one value.
 */
public class JsonPaths {

  private static final Logger LOGGER = LoggerFactory.getLogger(JsonPaths.class);

  static final String JSON_PATH_START_CHARACTER = "$";
  static final String JSON_PATH_LIST_SPLAT = "[*]";
  static final String JSON_PATH_FIELD_SEPARATOR = ".";

  // set default configurations at start up to match our JSON setup.
  static {
    Configuration.setDefaults(new Configuration.Defaults() {

      // allows us to pass in Jackson JsonNode
      private static final JsonProvider jsonProvider = new JacksonJsonNodeJsonProvider();
      private static final MappingProvider mappingProvider = new JacksonMappingProvider();

      @Override
      public JsonProvider jsonProvider() {
        return jsonProvider;
      }

      @Override
      public MappingProvider mappingProvider() {
        return mappingProvider;
      }

      @Override
      public Set<Option> options() {
        /*
         * All JsonPath queries will return a list of values. This makes parsing the outputs much easier. In
         * cases where it is not a list, helpers in this class can assert that. See
         * https://github.com/json-path/JsonPath in the JsonPath documentation.
         */
        return EnumSet.of(Option.ALWAYS_RETURN_LIST);
      }

    });
  }

  public static String jsonPathPrefix() {
    return JSON_PATH_START_CHARACTER + JSON_PATH_FIELD_SEPARATOR;
  }

  public static String empty() {
    return JSON_PATH_START_CHARACTER;
  }

  public static String appendField(final String jsonPath, final String field) {
    return jsonPath + JSON_PATH_FIELD_SEPARATOR + field;
  }

  public static String appendAppendListSplat(final String jsonPath) {
    return jsonPath + JSON_PATH_LIST_SPLAT;
  }

  /**
   * Map path produced by {@link JsonSchemas} to the JSONPath format.
   *
   * @param jsonSchemaPath - path as described in {@link JsonSchemas}
   * @return path as JSONPath
   */
  public static String mapJsonSchemaPathToJsonPath(final List<FieldNameOrList> jsonSchemaPath) {
    String jsonPath = empty();
    for (final FieldNameOrList fieldNameOrList : jsonSchemaPath) {
      jsonPath = fieldNameOrList.isList() ? appendAppendListSplat(jsonPath) : appendField(jsonPath, fieldNameOrList.getFieldName());
    }
    return jsonPath;
  }

  /*
   * This version of the JsonPath Configuration object allows queries to return to the path of values
   * instead of the values that were found.
   */
  private static final Configuration GET_PATHS_CONFIGURATION = Configuration.defaultConfiguration().addOptions(Option.AS_PATH_LIST);

  /**
   * Attempt to validate if a string is a valid JSONPath string. This assertion does NOT handle all
   * cases, but at least a common on. We can add to it as we detect others.
   *
   * @param jsonPath - path to validate
   */
  private static void assertIsJsonPath(final String jsonPath) {
    Preconditions.checkArgument(jsonPath.startsWith("$"));
  }

  /**
   * Attempt to detect if a JSONPath query could return more than 1 value. This assertion does NOT
   * handle all cases, but at least a common on. We can add to it as we detect others.
   *
   * @param jsonPath - path to validate
   */
  private static void assertIsSingleReturnQuery(final String jsonPath) {
    Preconditions.checkArgument(JsonPath.isPathDefinite(jsonPath), "Cannot accept paths with wildcards because they may return more than one item.");
  }

  /**
   * Given a JSONPath, returns all the values that match that path.
   * <p>
   * e.g. for this object: { "alpha": [1, 2, 3] }, if the input JSONPath were "$.alpha[*]", this
   * function would return: [1, 2, 3].
   *
   * @param json - json object
   * @param jsonPath - path into the json object. must be in the format of JSONPath.
   * @return all values that match the input query
   */
  public static List<JsonNode> getValues(final JsonNode json, final String jsonPath) {
    return getInternal(Configuration.defaultConfiguration(), json, jsonPath);
  }

  /**
   * Given a JSONPath, returns all the path of all values that match that path.
   * <p>
   * e.g. for this object: { "alpha": [1, 2, 3] }, if the input JSONPath were "$.alpha[*]", this
   * function would return: ["$.alpha[0]", "$.alpha[1]", "$.alpha[2]"].
   *
   * @param json - json object
   * @param jsonPath - path into the json object. must be in the format of JSONPath.
   * @return all paths that are present that match the input query. returns a list (instead of a set),
   *         because having a deterministic ordering is valuable for all downstream consumers (i.e. in
   *         most cases if we returned a set, the downstream would then put it in a set and sort it so
   *         that if they are doing replacements using the paths, the behavior is predictable e.g. if
   *         you do replace $.alpha and $.alpha[*], the order you do those replacements in matters).
   *         specifically that said, we do expect that there will be no duplicates in the returned
   *         list.
   */
  private static List<String> getPaths(final JsonNode json, final String jsonPath) {
    return getInternal(GET_PATHS_CONFIGURATION, json, jsonPath)
        .stream()
        .map(JsonNode::asText)
        .collect(Collectors.toList());
  }

  /**
   * Given a JSONPath, returns 1 or 0 values that match the path. Throws if more than 1 value is
   * found.
   * <p>
   * THIS SHOULD ONLY BE USED IF THE JSONPATH CAN ONLY EVER RETURN 0 OR 1 VALUES. e.g. don't do
   * "$.alpha[*]"
   *
   * @param json - json object
   * @param jsonPath - path into the json object. must be in the format of JSONPath.
   * @return value if present, otherwise empty.
   */
  public static Optional<JsonNode> getSingleValue(final JsonNode json, final String jsonPath) {
    assertIsSingleReturnQuery(jsonPath);

    final List<JsonNode> jsonNodes = getValues(json, jsonPath);

    Preconditions.checkState(jsonNodes.size() <= 1, String.format("Path returned more than one item. path: %s items: %s", jsonPath, jsonNodes));
    return jsonNodes.isEmpty() ? Optional.empty() : Optional.of(jsonNodes.get(0));
  }

  /**
   * Retrieves a single text value from the specified JSON node based on the provided JSON path. If
   * the value is not found, returns null.
   *
   * @param json the JSON node to search within
   * @param jsonPath the JSON path to locate the value
   * @return the text value at the specified JSON path, or null if not found
   */
  public static String getSingleValueTextOrNull(final JsonNode json, final String jsonPath) {
    final Optional<JsonNode> jsonNode = getSingleValue(json, jsonPathPrefix() + jsonPath);
    return jsonNode.map(JsonNode::asText).orElse(null);
  }

  /**
   * Extracts the final segment of a dot-separated string. If the input string contains one or more
   * dots, the method splits the string by dots and returns the last segment. If the input string does
   * not contain any dots, the method returns the input string itself.
   *
   * @param string the input string to process
   * @return the final segment of the dot-separated string, or the input string if no dots are present
   */
  public static String getTargetKeyFromJsonPath(final String jsonPath) {
    if (jsonPath.contains(".")) {
      final String[] parts = jsonPath.split("\\.");
      return parts[parts.length - 1];
    }

    return jsonPath;
  }

  /**
   * Traverses into a json object and replaces all values that match the input path with the provided
   * string . Does nothing if no existing fields match the path.
   *
   * @param json - json object
   * @param jsonPath - path into the json object. must be in the format of JSONPath.
   * @param replacement - a string value to replace the current value at the jsonPath
   */
  public static JsonNode replaceAtString(final JsonNode json, final String jsonPath, final String replacement) {
    return replaceAtJsonNode(json, jsonPath, Jsons.jsonNode(replacement));
  }

  /**
   * Traverses into a json object and replaces all values that match the input path with the provided
   * json object. Does nothing if no existing fields match the path.
   *
   * @param json - json object
   * @param jsonPath - path into the json object. must be in the format of JSONPath.
   * @param replacement - a json node to replace the current value at the jsonPath
   */
  public static JsonNode replaceAtJsonNodeLoud(final JsonNode json, final String jsonPath, final JsonNode replacement) {
    assertIsJsonPath(jsonPath);
    return JsonPath.parse(Jsons.clone(json)).set(jsonPath, replacement).json();
  }

  /**
   * Traverses into a json object and replaces all values that match the input path with the provided
   * json object. Does nothing if no existing fields match the path.
   *
   * @param json - json object
   * @param jsonPath - path into the json object. must be in the format of JSONPath.
   * @param replacement - a json node to replace the current value at the jsonPath
   */
  private static JsonNode replaceAtJsonNode(final JsonNode json, final String jsonPath, final JsonNode replacement) {
    try {
      return replaceAtJsonNodeLoud(json, jsonPath, replacement);
    } catch (final PathNotFoundException e) {
      LOGGER.debug("Path not found", e);
      return Jsons.clone(json); // defensive copy in failure case.
    }
  }

  /**
   * Traverses into a json object and replaces all values that match the input path with the output of
   * the provided function. Does nothing if no existing fields match the path.
   *
   * @param json - json object
   * @param jsonPath - path into the json object. must be in the format of JSONPath.
   * @param replacementFunction - a function that takes in a node that matches the path as well as the
   *        path to the node itself. the return of this function will replace the current node.
   */
  public static JsonNode replaceAt(final JsonNode json, final String jsonPath, final BiFunction<JsonNode, String, JsonNode> replacementFunction) {
    JsonNode clone = Jsons.clone(json);
    assertIsJsonPath(jsonPath);
    final List<String> foundPaths = getPaths(clone, jsonPath);
    for (final String foundPath : foundPaths) {
      final Optional<JsonNode> singleValue = getSingleValue(clone, foundPath);
      if (singleValue.isPresent()) {
        final JsonNode replacement = replacementFunction.apply(singleValue.get(), foundPath);
        clone = replaceAtJsonNode(clone, foundPath, replacement);
      }
    }
    return clone;
  }

  /**
   * Get values at a JSONPath.
   *
   * @param conf - JsonPath configuration. Primarily used to reuse code to allow fetching values or
   *        paths from a json object
   * @param json - json object
   * @param jsonPath - path into the json object. must be in the format of JSONPath.
   * @return all values that match the input query (whether the values are paths or actual values in
   *         the json object is determined by the conf)
   */
  private static List<JsonNode> getInternal(final Configuration conf, final JsonNode json, final String jsonPath) {
    assertIsJsonPath(jsonPath);
    try {
      return MoreIterators.toList(JsonPath.using(conf).parse(json).read(jsonPath, ArrayNode.class).iterator());
    } catch (final PathNotFoundException e) {
      return Collections.emptyList();
    }
  }

  /**
   * Given a JSONPath template (which may include wildcards like [*]), returns the expanded list of
   * full JSON paths that match the template in the provided JSON object.
   * <p>
   * For example, if the template is "$.rotating_keys[*].key2" and only the second array element
   * contains "key2", this method will return a list with "$.rotating_keys[1].key2".
   *
   * @param json the JSON object to search
   * @param jsonPathTemplate a JSONPath that may include wildcards (e.g. "$.rotating_keys[*].key2")
   * @return a sorted list of expanded JSONPath strings with wildcards replaced by actual indices
   */
  public static List<String> getExpandedPaths(final JsonNode json, final String jsonPathTemplate) {
    return getInternal(GET_PATHS_CONFIGURATION, json, jsonPathTemplate)
        .stream()
        .map(JsonNode::asText)
        .map(JsonPaths::normalizeJsonPath)
        .distinct()
        .sorted()
        .collect(Collectors.toList());
  }

  /**
   * Normalizes a JSONPath string to use dot notation. For example, it converts
   * $['rotating_keys'][0]['key1'] to $.rotating_keys[0].key1.
   *
   * @param jsonPath the JSONPath string to normalize
   * @return a normalized JSONPath in dot notation
   */
  private static String normalizeJsonPath(String jsonPath) {
    if (jsonPath == null) {
      return null;
    }
    return jsonPath.replaceAll("\\[['\"]([^'\"]+)['\"]]", ".$1");
  }

}
