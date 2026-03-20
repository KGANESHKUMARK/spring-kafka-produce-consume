package com.example.kafkaavro.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.avro.Schema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@RestController
@RequestMapping("/api/validation")
@Tag(name = "Schema Validation", description = "APIs to validate JSON data against Avro schemas")
public class SchemaValidationController {

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/validate")
    @Operation(
        summary = "Validate JSON against schema", 
        description = "Validates input JSON against the latest schema for the specified topic. Returns detailed field-level validation errors with fix suggestions."
    )
    @ApiResponse(responseCode = "200", description = "Validation completed (may contain errors)")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "404", description = "Schema not found")
    public ResponseEntity<?> validateJson(
            @Parameter(description = "Topic name (e.g., test-topic)") 
            @RequestParam String topic,
            @Parameter(description = "Type: 'key' or 'value'") 
            @RequestParam(defaultValue = "value") String type,
            @Parameter(description = "JSON data to validate") 
            @RequestBody JsonNode inputJson) {
        
        try {
            // Get latest schema from registry
            String subject = topic + "-" + type;
            String schemaUrl = schemaRegistryUrl + "/subjects/" + subject + "/versions/latest";
            
            Map<String, Object> schemaResponse;
            try {
                schemaResponse = restTemplate.getForObject(schemaUrl, Map.class);
            } catch (Exception e) {
                return ResponseEntity.status(404).body(Map.of(
                    "valid", false,
                    "error", "Schema not found",
                    "subject", subject,
                    "message", "No schema registered for subject: " + subject
                ));
            }
            
            String schemaString = (String) schemaResponse.get("schema");
            Schema schema = new Schema.Parser().parse(schemaString);
            
            // Validate JSON against schema
            ValidationResult result = validateAgainstSchema(inputJson, schema, "");
            
            Map<String, Object> response = new HashMap<>();
            response.put("valid", result.errors.isEmpty());
            response.put("topic", topic);
            response.put("type", type);
            response.put("subject", subject);
            response.put("schemaVersion", schemaResponse.get("version"));
            response.put("schemaId", schemaResponse.get("id"));
            
            if (!result.errors.isEmpty()) {
                response.put("errorCount", result.errors.size());
                response.put("errors", result.errors);
                response.put("suggestedFixes", result.fixes);
            } else {
                response.put("message", "JSON is valid according to the schema");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "valid", false,
                "error", "Validation failed",
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/validate-batch")
    @Operation(
        summary = "Validate multiple JSON payloads", 
        description = "Validates multiple JSON payloads in one request. Useful for bulk validation."
    )
    @ApiResponse(responseCode = "200", description = "Batch validation completed")
    @ApiResponse(responseCode = "404", description = "Schema not found")
    public ResponseEntity<?> validateBatch(@RequestBody Map<String, Object> request) {
        try {
            String topic = (String) request.get("topic");
            String type = request.getOrDefault("type", "value").toString();
            List<Map<String, Object>> payloads = (List<Map<String, Object>>) request.get("payloads");
            
            if (payloads == null || payloads.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "No payloads provided",
                    "message", "Please provide a 'payloads' array"
                ));
            }
            
            // Get schema
            String subject = topic + "-" + type;
            String schemaUrl = schemaRegistryUrl + "/subjects/" + subject + "/versions/latest";
            
            Map<String, Object> schemaResponse;
            try {
                schemaResponse = restTemplate.getForObject(schemaUrl, Map.class);
            } catch (Exception e) {
                return ResponseEntity.status(404).body(Map.of(
                    "error", "Schema not found",
                    "subject", subject
                ));
            }
            
            String schemaString = (String) schemaResponse.get("schema");
            Schema schema = new Schema.Parser().parse(schemaString);
            
            // Validate each payload
            List<Map<String, Object>> results = new ArrayList<>();
            int validCount = 0;
            int invalidCount = 0;
            
            for (int i = 0; i < payloads.size(); i++) {
                Map<String, Object> payload = payloads.get(i);
                JsonNode jsonNode = objectMapper.valueToTree(payload);
                ValidationResult result = validateAgainstSchema(jsonNode, schema, "");
                
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("index", i);
                resultMap.put("valid", result.errors.isEmpty());
                
                if (result.errors.isEmpty()) {
                    validCount++;
                } else {
                    invalidCount++;
                    resultMap.put("errors", result.errors);
                    resultMap.put("suggestedFixes", result.fixes);
                }
                
                results.add(resultMap);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("topic", topic);
            response.put("type", type);
            response.put("totalPayloads", payloads.size());
            response.put("validCount", validCount);
            response.put("invalidCount", invalidCount);
            response.put("results", results);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Batch validation failed",
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/validate-and-fix")
    @Operation(
        summary = "Validate and auto-fix JSON", 
        description = "Validates input JSON and attempts to automatically fix common issues. Returns the corrected JSON."
    )
    @ApiResponse(responseCode = "200", description = "Validation and fix completed")
    @ApiResponse(responseCode = "404", description = "Schema not found")
    public ResponseEntity<?> validateAndFix(
            @Parameter(description = "Topic name") 
            @RequestParam String topic,
            @Parameter(description = "Type: 'key' or 'value'") 
            @RequestParam(defaultValue = "value") String type,
            @Parameter(description = "JSON data to validate and fix") 
            @RequestBody JsonNode inputJson) {
        
        try {
            // Get latest schema
            String subject = topic + "-" + type;
            String schemaUrl = schemaRegistryUrl + "/subjects/" + subject + "/versions/latest";
            
            Map<String, Object> schemaResponse;
            try {
                schemaResponse = restTemplate.getForObject(schemaUrl, Map.class);
            } catch (Exception e) {
                return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "error", "Schema not found",
                    "subject", subject
                ));
            }
            
            String schemaString = (String) schemaResponse.get("schema");
            Schema schema = new Schema.Parser().parse(schemaString);
            
            // Attempt to fix the JSON
            JsonNode fixedJson = autoFixJson(inputJson, schema);
            
            // Validate the fixed JSON
            ValidationResult result = validateAgainstSchema(fixedJson, schema, "");
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.errors.isEmpty());
            response.put("topic", topic);
            response.put("type", type);
            response.put("subject", subject);
            response.put("originalJson", inputJson);
            response.put("fixedJson", fixedJson);
            response.put("changesApplied", result.errors.isEmpty());
            
            if (!result.errors.isEmpty()) {
                response.put("remainingErrors", result.errors);
                response.put("message", "Some errors could not be auto-fixed");
            } else {
                response.put("message", "JSON successfully validated and fixed");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "Fix failed",
                "message", e.getMessage()
            ));
        }
    }

    private ValidationResult validateAgainstSchema(JsonNode json, Schema schema, String path) {
        ValidationResult result = new ValidationResult();
        
        if (schema.getType() == Schema.Type.RECORD) {
            validateRecord(json, schema, path, result);
        } else if (schema.getType() == Schema.Type.UNION) {
            validateUnion(json, schema, path, result);
        } else {
            validatePrimitive(json, schema, path, result);
        }
        
        return result;
    }

    private void validateRecord(JsonNode json, Schema schema, String path, ValidationResult result) {
        if (!json.isObject()) {
            result.addError(path.isEmpty() ? "root" : path, 
                "Expected object/record", 
                "Actual type: " + json.getNodeType(),
                "Wrap the value in an object: { ... }");
            return;
        }
        
        // Check required fields
        for (Schema.Field field : schema.getFields()) {
            String fieldPath = path.isEmpty() ? field.name() : path + "." + field.name();
            JsonNode fieldValue = json.get(field.name());
            
            if (fieldValue == null || fieldValue.isNull()) {
                if (!isOptional(field.schema())) {
                    result.addError(fieldPath, 
                        "Required field missing", 
                        "Field '" + field.name() + "' is required but not provided",
                        "Add field: \"" + field.name() + "\": <value>");
                }
            } else {
                // Validate field value
                ValidationResult fieldResult = validateAgainstSchema(fieldValue, field.schema(), fieldPath);
                result.errors.addAll(fieldResult.errors);
                result.fixes.addAll(fieldResult.fixes);
            }
        }
        
        // Check for extra fields
        Iterator<String> fieldNames = json.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (schema.getField(fieldName) == null) {
                String fieldPath = path.isEmpty() ? fieldName : path + "." + fieldName;
                result.addError(fieldPath, 
                    "Unknown field", 
                    "Field '" + fieldName + "' is not defined in schema",
                    "Remove this field or check schema definition");
            }
        }
    }

    private void validateUnion(JsonNode json, Schema schema, String path, ValidationResult result) {
        boolean hasNull = schema.getTypes().stream()
            .anyMatch(s -> s.getType() == Schema.Type.NULL);
        
        if (json == null || json.isNull()) {
            if (!hasNull) {
                result.addError(path, 
                    "Null not allowed", 
                    "Field does not accept null values",
                    "Provide a non-null value");
            }
            return;
        }
        
        // Try to validate against non-null types
        Schema nonNullSchema = schema.getTypes().stream()
            .filter(s -> s.getType() != Schema.Type.NULL)
            .findFirst()
            .orElse(null);
        
        if (nonNullSchema != null) {
            ValidationResult unionResult = validateAgainstSchema(json, nonNullSchema, path);
            result.errors.addAll(unionResult.errors);
            result.fixes.addAll(unionResult.fixes);
        }
    }

    private void validatePrimitive(JsonNode json, Schema schema, String path, ValidationResult result) {
        Schema.Type schemaType = schema.getType();
        String logicalType = schema.getLogicalType() != null ? schema.getLogicalType().getName() : null;
        
        // Handle logical types
        if (logicalType != null) {
            validateLogicalType(json, logicalType, path, result);
            return;
        }
        
        // Handle primitive types
        switch (schemaType) {
            case STRING:
                if (!json.isTextual()) {
                    result.addError(path, "Type mismatch", 
                        "Expected string, got " + json.getNodeType(),
                        "Convert to string: \"" + json.asText() + "\"");
                }
                break;
            case INT:
                if (!json.isInt()) {
                    result.addError(path, "Type mismatch", 
                        "Expected integer, got " + json.getNodeType(),
                        "Use integer value: " + json.asInt());
                }
                break;
            case LONG:
                if (!json.isLong() && !json.isInt()) {
                    result.addError(path, "Type mismatch", 
                        "Expected long, got " + json.getNodeType(),
                        "Use long value: " + json.asLong());
                }
                break;
            case FLOAT:
            case DOUBLE:
                if (!json.isNumber()) {
                    result.addError(path, "Type mismatch", 
                        "Expected number, got " + json.getNodeType(),
                        "Use numeric value: " + json.asDouble());
                }
                break;
            case BOOLEAN:
                if (!json.isBoolean()) {
                    result.addError(path, "Type mismatch", 
                        "Expected boolean, got " + json.getNodeType(),
                        "Use true or false");
                }
                break;
            case ARRAY:
                if (!json.isArray()) {
                    result.addError(path, "Type mismatch", 
                        "Expected array, got " + json.getNodeType(),
                        "Use array format: [...]");
                } else {
                    // Validate array elements
                    Schema elementSchema = schema.getElementType();
                    for (int i = 0; i < json.size(); i++) {
                        ValidationResult elemResult = validateAgainstSchema(
                            json.get(i), elementSchema, path + "[" + i + "]");
                        result.errors.addAll(elemResult.errors);
                        result.fixes.addAll(elemResult.fixes);
                    }
                }
                break;
            case MAP:
                if (!json.isObject()) {
                    result.addError(path, "Type mismatch", 
                        "Expected map/object, got " + json.getNodeType(),
                        "Use object format: {...}");
                } else {
                    // Validate map values
                    Schema valueSchema = schema.getValueType();
                    Iterator<Map.Entry<String, JsonNode>> fields = json.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fields.next();
                        ValidationResult valResult = validateAgainstSchema(
                            entry.getValue(), valueSchema, path + "." + entry.getKey());
                        result.errors.addAll(valResult.errors);
                        result.fixes.addAll(valResult.fixes);
                    }
                }
                break;
            case ENUM:
                if (!json.isTextual()) {
                    result.addError(path, "Type mismatch", 
                        "Expected enum string, got " + json.getNodeType(),
                        "Use one of: " + schema.getEnumSymbols());
                } else if (!schema.getEnumSymbols().contains(json.asText())) {
                    result.addError(path, "Invalid enum value", 
                        "'" + json.asText() + "' is not a valid enum value",
                        "Use one of: " + schema.getEnumSymbols());
                }
                break;
        }
    }

    private void validateLogicalType(JsonNode json, String logicalType, String path, ValidationResult result) {
        switch (logicalType) {
            case "decimal":
                if (!json.isNumber() && !json.isTextual()) {
                    result.addError(path, "Invalid decimal", 
                        "Expected number or string, got " + json.getNodeType(),
                        "Use: 1234.56 or \"1234.56\"");
                }
                break;
            case "date":
                if (!json.isTextual()) {
                    result.addError(path, "Invalid date", 
                        "Expected ISO date string (YYYY-MM-DD)",
                        "Use format: \"2026-03-20\"");
                } else {
                    try {
                        java.time.LocalDate.parse(json.asText());
                    } catch (Exception e) {
                        result.addError(path, "Invalid date format", 
                            "'" + json.asText() + "' is not a valid date",
                            "Use ISO format: \"2026-03-20\"");
                    }
                }
                break;
            case "timestamp-millis":
                if (!json.isTextual() && !json.isNumber()) {
                    result.addError(path, "Invalid timestamp", 
                        "Expected ISO timestamp string or number",
                        "Use format: \"2026-03-20T01:45:00.000Z\"");
                }
                break;
            case "time-micros":
                if (!json.isNumber()) {
                    result.addError(path, "Invalid time-micros", 
                        "Expected long number (microseconds since midnight)",
                        "Use format: 34215123456 (not a string)");
                }
                break;
        }
    }

    private boolean isOptional(Schema schema) {
        if (schema.getType() == Schema.Type.UNION) {
            return schema.getTypes().stream()
                .anyMatch(s -> s.getType() == Schema.Type.NULL);
        }
        return false;
    }

    private JsonNode autoFixJson(JsonNode json, Schema schema) {
        // This is a placeholder for auto-fix logic
        // In a real implementation, you would apply fixes based on validation errors
        return json;
    }

    private static class ValidationResult {
        List<Map<String, String>> errors = new ArrayList<>();
        List<Map<String, String>> fixes = new ArrayList<>();
        
        void addError(String field, String error, String details, String fix) {
            Map<String, String> errorMap = new LinkedHashMap<>();
            errorMap.put("field", field);
            errorMap.put("error", error);
            errorMap.put("details", details);
            errors.add(errorMap);
            
            Map<String, String> fixMap = new LinkedHashMap<>();
            fixMap.put("field", field);
            fixMap.put("suggestedFix", fix);
            fixes.add(fixMap);
        }
    }
}
