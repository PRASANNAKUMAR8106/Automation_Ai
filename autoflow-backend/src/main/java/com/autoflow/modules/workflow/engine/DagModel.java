package com.autoflow.modules.workflow.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;

import java.util.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DagModel {

    @Builder.Default
    private List<DagNode> nodes = new ArrayList<>();

    @Builder.Default
    private List<DagEdge> edges = new ArrayList<>();

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DagNode {
        private String id;
        private String type;
        @Builder.Default
        private Map<String, Object> config = new HashMap<>();
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DagEdge {
        private String from;
        private String to;
        private String condition;
    }

    public static DagModel fromJson(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return new DagModel();
        }
        try {
            return objectMapper.readValue(json, DagModel.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid graph definition JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Validates graph topology. Throws IllegalArgumentException if invalid.
     */
    public void validate() {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("Workflow must contain at least one node");
        }

        Set<String> nodeIds = new HashSet<>();
        boolean hasTrigger = false;

        for (DagNode node : nodes) {
            if (node.getId() == null || node.getId().isBlank()) {
                throw new IllegalArgumentException("Node id cannot be null or blank");
            }
            if (!nodeIds.add(node.getId())) {
                throw new IllegalArgumentException("Duplicate node id detected: " + node.getId());
            }
            if (node.getType() != null && node.getType().startsWith("TRIGGER_")) {
                hasTrigger = true;
            }
        }

        if (!hasTrigger) {
            throw new IllegalArgumentException("Workflow must contain at least one TRIGGER node");
        }

        if (edges != null) {
            for (DagEdge edge : edges) {
                if (!nodeIds.contains(edge.getFrom())) {
                    throw new IllegalArgumentException("Edge references non-existent source node: " + edge.getFrom());
                }
                if (!nodeIds.contains(edge.getTo())) {
                    throw new IllegalArgumentException("Edge references non-existent target node: " + edge.getTo());
                }
            }
        }

        // Cycle detection & topological sort
        getTopologicalOrder();
    }

    /**
     * Returns nodes in topological execution order.
     * Throws IllegalStateException if graph contains cycles.
     */
    public List<DagNode> getTopologicalOrder() {
        Map<String, DagNode> nodeMap = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adjList = new HashMap<>();

        for (DagNode node : nodes) {
            nodeMap.put(node.getId(), node);
            inDegree.put(node.getId(), 0);
            adjList.put(node.getId(), new ArrayList<>());
        }

        if (edges != null) {
            for (DagEdge edge : edges) {
                adjList.get(edge.getFrom()).add(edge.getTo());
                inDegree.put(edge.getTo(), inDegree.get(edge.getTo()) + 1);
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<DagNode> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sorted.add(nodeMap.get(current));

            for (String neighbor : adjList.get(current)) {
                int deg = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, deg);
                if (deg == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (sorted.size() != nodes.size()) {
            throw new IllegalStateException("Circular dependency / cycle detected in workflow graph");
        }

        return sorted;
    }

    public Optional<DagNode> findTriggerNode() {
        if (nodes == null) return Optional.empty();
        return nodes.stream()
                .filter(n -> n.getType() != null && n.getType().startsWith("TRIGGER_"))
                .findFirst();
    }

    public List<DagNode> getSuccessors(String nodeId) {
        List<DagNode> successors = new ArrayList<>();
        if (edges == null) return successors;

        Map<String, DagNode> nodeMap = new HashMap<>();
        for (DagNode n : nodes) nodeMap.put(n.getId(), n);

        for (DagEdge edge : edges) {
            if (Objects.equals(edge.getFrom(), nodeId) && nodeMap.containsKey(edge.getTo())) {
                successors.add(nodeMap.get(edge.getTo()));
            }
        }
        return successors;
    }
}
