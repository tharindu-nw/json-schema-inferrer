package com.saasquatch.json_schema_inferrer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class JsonSchemaInferrerTest {

  private static final JsonNodeFactory jnf = JsonNodeFactory.instance;
  private final ObjectMapper mapper = new ObjectMapper();

  private JsonNode loadJson(String fileName) {
    try (InputStream in = this.getClass().getResourceAsStream(fileName)) {
      return mapper.readTree(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Test
  public void testBasic() {
    assertDoesNotThrow(() -> JsonSchemaInferrer.newBuilder().build().inferFromSample(null));
  }

  @Test
  public void testFormatInference() {
    assertEquals("email", JsonSchemaInferrer.newBuilder().build().inferFromSample(jnf.textNode("foo@bar.com"))
        .path("format").textValue());
    assertNull(JsonSchemaInferrer.newBuilder().withFormatInferrer(FormatInferrer.noOp()).build()
        .inferFromSample(jnf.textNode("foo@bar.com")).path("format").textValue());
    assertNull(JsonSchemaInferrer.newBuilder().withSpecVersion(SpecVersion.DRAFT_07).build()
        .inferFromSample(jnf.textNode("aaaaaaaaa")).path("format").textValue());
    assertEquals("ipv4", JsonSchemaInferrer.newBuilder().build().inferFromSample(jnf.textNode("1.2.3.4"))
        .path("format").textValue());
    assertEquals("ipv6", JsonSchemaInferrer.newBuilder().build().inferFromSample(jnf.textNode("1::1"))
        .path("format").textValue());
    assertEquals("uri", JsonSchemaInferrer.newBuilder().build()
        .inferFromSample(jnf.textNode("https://saasquat.ch")).path("format").textValue());
    assertEquals("date-time", JsonSchemaInferrer.newBuilder().build()
        .inferFromSample(jnf.textNode(Instant.now().toString())).path("format").textValue());
    assertNull(JsonSchemaInferrer.newBuilder().withSpecVersion(SpecVersion.DRAFT_06).build()
        .inferFromSample(jnf.textNode("1900-01-01")).path("format").textValue());
    assertEquals("date", JsonSchemaInferrer.newBuilder().withSpecVersion(SpecVersion.DRAFT_07)
        .build().inferFromSample(jnf.textNode("1900-01-01")).path("format").textValue());
    assertNull(JsonSchemaInferrer.newBuilder().withSpecVersion(SpecVersion.DRAFT_06).build()
        .inferFromSample(jnf.textNode("20:20:39")).path("format").textValue());
    assertEquals("time", JsonSchemaInferrer.newBuilder().withSpecVersion(SpecVersion.DRAFT_07)
        .build().inferFromSample(jnf.textNode("20:20:39")).path("format").textValue());
  }

  @Test
  public void testSimpleExample() throws Exception {
    final JsonNode simple = loadJson("simple.json");
    {
      final ObjectNode schema = JsonSchemaInferrer.newBuilder().build().inferFromSample(simple);
      assertTrue(schema.hasNonNull("$schema"));
      assertTrue(schema.path("$schema").textValue().contains("-04"));
      assertTrue(schema.hasNonNull("type"));
    }
    {
      final ObjectNode schema = JsonSchemaInferrer.newBuilder()
          .withSpecVersion(SpecVersion.DRAFT_06).build().inferFromSample(simple);
      assertTrue(schema.hasNonNull("$schema"));
      assertTrue(schema.path("$schema").textValue().contains("-06"));
      assertTrue(schema.hasNonNull("type"));
    }
    {
      final ObjectNode schema = JsonSchemaInferrer.newBuilder()
          .withSpecVersion(SpecVersion.DRAFT_06).includeMetaSchemaUrl(false).build().inferFromSample(simple);
      assertFalse(schema.hasNonNull("$schema"));
      assertTrue(schema.hasNonNull("type"));
    }
    {
      final ObjectNode schema = JsonSchemaInferrer.newBuilder().build().inferFromSample(simple);
      assertTrue(schema.hasNonNull("properties"));
      assertTrue(schema.path("properties").isObject());
      assertEquals("integer", schema.path("properties").path("id").path("type").textValue());
      assertEquals("string", schema.path("properties").path("slug").path("type").textValue());
      assertEquals("boolean", schema.path("properties").path("admin").path("type").textValue());
      assertEquals("null", schema.path("properties").path("avatar").path("type").textValue());
      assertEquals("string", schema.path("properties").path("date").path("type").textValue());
      assertEquals("date-time", schema.path("properties").path("date").path("format").textValue());
      assertEquals("object", schema.path("properties").path("article").path("type").textValue());
      assertTrue(schema.path("properties").path("article").isObject());
      assertEquals("string", schema.path("properties").path("article").path("properties")
          .path("title").path("type").textValue());
      assertEquals("string", schema.path("properties").path("article").path("properties")
          .path("description").path("type").textValue());
      assertEquals("string", schema.path("properties").path("article").path("properties")
          .path("body").path("type").textValue());
      assertEquals("array", schema.path("properties").path("comments").path("type").textValue());
      assertTrue(schema.path("properties").path("comments").path("items").isObject());
      assertEquals(new HashSet<>(Arrays.asList("string", "null")),
          toStringSet(schema.path("properties").path("comments").path("items").path("properties")
              .path("body").path("type")));
    }
  }

  @Test
  public void testAdvancedExample() throws Exception {
    final JsonNode advanced = loadJson("advanced.json");
    {
      final ObjectNode schema = JsonSchemaInferrer.newBuilder().build().inferFromSample(advanced);
      assertTrue(schema.path("items").isObject());
      assertTrue(schema.path("items").path("properties").path("tags").isObject());
      assertEquals("integer",
          schema.path("items").path("properties").path("id").path("type").textValue());
      assertEquals("number",
          schema.path("items").path("properties").path("price").path("type").textValue());
      assertEquals(new HashSet<>(Arrays.asList("integer", "number")),
          toStringSet(schema.path("items").path("properties").path("dimensions").path("properties")
              .path("length").path("type")));
    }
  }

  private static Set<String> toStringSet(JsonNode arrayNode) {
    final Set<String> result = new HashSet<>();
    arrayNode.forEach(j -> result.add(j.textValue()));
    return result;
  }

}
