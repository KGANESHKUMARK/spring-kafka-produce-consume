package com.example.kafkaavro.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/registry")
@Tag(name = "Schema Registry", description = "APIs to verify and inspect Schema Registry")
public class SchemaRegistryController {

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/subjects")
    @Operation(summary = "List all subjects", description = "Returns all registered subjects in Schema Registry")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved subjects")
    public ResponseEntity<?> listSubjects() {
        try {
            String url = schemaRegistryUrl + "/subjects";
            List<String> subjects = restTemplate.getForObject(url, List.class);
            
            Map<String, Object> response = new HashMap<>();
            response.put("schemaRegistryUrl", schemaRegistryUrl);
            response.put("totalSubjects", subjects != null ? subjects.size() : 0);
            response.put("subjects", subjects);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to retrieve subjects",
                "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/subjects/{subject}/versions")
    @Operation(summary = "Get all versions for a subject", description = "Returns all version numbers for the specified subject")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved versions")
    @ApiResponse(responseCode = "404", description = "Subject not found")
    public ResponseEntity<?> getVersions(
            @Parameter(description = "Subject name (e.g., test-topic-key)") 
            @PathVariable String subject) {
        try {
            String url = schemaRegistryUrl + "/subjects/" + subject + "/versions";
            List<Integer> versions = restTemplate.getForObject(url, List.class);
            
            Map<String, Object> response = new HashMap<>();
            response.put("subject", subject);
            response.put("totalVersions", versions != null ? versions.size() : 0);
            response.put("versions", versions);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of(
                "error", "Subject not found or failed to retrieve versions",
                "subject", subject,
                "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/subjects/{subject}/versions/{version}")
    @Operation(summary = "Get specific schema version", description = "Returns the schema for a specific version. Use 'latest' for the latest version")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved schema")
    @ApiResponse(responseCode = "404", description = "Subject or version not found")
    public ResponseEntity<?> getSchema(
            @Parameter(description = "Subject name (e.g., test-topic-value)") 
            @PathVariable String subject,
            @Parameter(description = "Version number or 'latest'") 
            @PathVariable String version) {
        try {
            String url = schemaRegistryUrl + "/subjects/" + subject + "/versions/" + version;
            Map<String, Object> schemaInfo = restTemplate.getForObject(url, Map.class);
            
            return ResponseEntity.ok(schemaInfo);
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of(
                "error", "Schema not found",
                "subject", subject,
                "version", version,
                "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/subjects/{subject}/versions/latest")
    @Operation(summary = "Get latest schema", description = "Returns the latest schema version for the subject")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved latest schema")
    @ApiResponse(responseCode = "404", description = "Subject not found")
    public ResponseEntity<?> getLatestSchema(
            @Parameter(description = "Subject name (e.g., test-topic-key)") 
            @PathVariable String subject) {
        return getSchema(subject, "latest");
    }

    @GetMapping("/schemas/ids/{id}")
    @Operation(summary = "Get schema by ID", description = "Returns the schema for a specific schema ID")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved schema")
    @ApiResponse(responseCode = "404", description = "Schema ID not found")
    public ResponseEntity<?> getSchemaById(
            @Parameter(description = "Schema ID") 
            @PathVariable int id) {
        try {
            String url = schemaRegistryUrl + "/schemas/ids/" + id;
            Map<String, Object> schemaInfo = restTemplate.getForObject(url, Map.class);
            
            return ResponseEntity.ok(schemaInfo);
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of(
                "error", "Schema ID not found",
                "schemaId", id,
                "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/config")
    @Operation(summary = "Get global compatibility config", description = "Returns the global compatibility level configuration")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved config")
    public ResponseEntity<?> getGlobalConfig() {
        try {
            String url = schemaRegistryUrl + "/config";
            Map<String, Object> config = restTemplate.getForObject(url, Map.class);
            
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to retrieve config",
                "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/config/{subject}")
    @Operation(summary = "Get subject compatibility config", description = "Returns the compatibility level for a specific subject")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved config")
    @ApiResponse(responseCode = "404", description = "Subject not found")
    public ResponseEntity<?> getSubjectConfig(
            @Parameter(description = "Subject name") 
            @PathVariable String subject) {
        try {
            String url = schemaRegistryUrl + "/config/" + subject;
            Map<String, Object> config = restTemplate.getForObject(url, Map.class);
            
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of(
                "error", "Subject config not found",
                "subject", subject,
                "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/health")
    @Operation(summary = "Schema Registry health check", description = "Checks if Schema Registry is accessible and returns basic info")
    @ApiResponse(responseCode = "200", description = "Schema Registry is healthy")
    @ApiResponse(responseCode = "503", description = "Schema Registry is unavailable")
    public ResponseEntity<?> healthCheck() {
        try {
            String url = schemaRegistryUrl + "/subjects";
            List<String> subjects = restTemplate.getForObject(url, List.class);
            
            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("schemaRegistryUrl", schemaRegistryUrl);
            health.put("totalSubjects", subjects != null ? subjects.size() : 0);
            health.put("accessible", true);
            
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "DOWN");
            health.put("schemaRegistryUrl", schemaRegistryUrl);
            health.put("accessible", false);
            health.put("error", e.getMessage());
            
            return ResponseEntity.status(503).body(health);
        }
    }

    @GetMapping("/summary")
    @Operation(summary = "Get Schema Registry summary", description = "Returns a comprehensive summary of all subjects and their versions")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved summary")
    public ResponseEntity<?> getSummary() {
        try {
            String subjectsUrl = schemaRegistryUrl + "/subjects";
            List<String> subjects = restTemplate.getForObject(subjectsUrl, List.class);
            
            Map<String, Object> summary = new HashMap<>();
            summary.put("schemaRegistryUrl", schemaRegistryUrl);
            summary.put("totalSubjects", subjects != null ? subjects.size() : 0);
            
            Map<String, Object> subjectDetails = new HashMap<>();
            if (subjects != null) {
                for (String subject : subjects) {
                    try {
                        String versionsUrl = schemaRegistryUrl + "/subjects/" + subject + "/versions";
                        List<Integer> versions = restTemplate.getForObject(versionsUrl, List.class);
                        
                        String latestUrl = schemaRegistryUrl + "/subjects/" + subject + "/versions/latest";
                        Map<String, Object> latestSchema = restTemplate.getForObject(latestUrl, Map.class);
                        
                        Map<String, Object> details = new HashMap<>();
                        details.put("versions", versions);
                        details.put("latestVersion", latestSchema.get("version"));
                        details.put("latestSchemaId", latestSchema.get("id"));
                        
                        subjectDetails.put(subject, details);
                    } catch (Exception e) {
                        subjectDetails.put(subject, Map.of("error", e.getMessage()));
                    }
                }
            }
            
            summary.put("subjects", subjectDetails);
            
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to retrieve summary",
                "message", e.getMessage()
            ));
        }
    }
}
