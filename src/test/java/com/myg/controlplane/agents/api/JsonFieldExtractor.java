package com.myg.controlplane.agents.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class JsonFieldExtractor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonFieldExtractor() {
    }

    static String read(String json, String fieldName) throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(json);
        return root.get(fieldName).asText();
    }
}
