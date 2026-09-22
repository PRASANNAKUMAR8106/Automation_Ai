package com.autoflow.modules.workflow.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DAG Model Topology & Cycle Detection Tests")
class DagModelTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Should successfully validate acyclic DAG with trigger and actions")
    void shouldValidateValidDag() {
        DagModel.DagNode node1 = DagModel.DagNode.builder().id("n1").type("TRIGGER_INSTAGRAM_COMMENT").build();
        DagModel.DagNode node2 = DagModel.DagNode.builder().id("n2").type("ACTION_PUBLIC_COMMENT_REPLY").build();
        DagModel.DagNode node3 = DagModel.DagNode.builder().id("n3").type("ACTION_SEND_DM").build();

        DagModel.DagEdge edge1 = DagModel.DagEdge.builder().from("n1").to("n2").build();
        DagModel.DagEdge edge2 = DagModel.DagEdge.builder().from("n2").to("n3").build();

        DagModel model = DagModel.builder()
                .nodes(List.of(node1, node2, node3))
                .edges(List.of(edge1, edge2))
                .build();

        assertDoesNotThrow(model::validate);

        List<DagModel.DagNode> order = model.getTopologicalOrder();
        assertEquals(3, order.size());
        assertEquals("n1", order.get(0).getId());
        assertEquals("n2", order.get(1).getId());
        assertEquals("n3", order.get(2).getId());
    }

    @Test
    @DisplayName("Should reject DAG with no trigger node")
    void shouldRejectDagWithoutTrigger() {
        DagModel.DagNode node1 = DagModel.DagNode.builder().id("n1").type("ACTION_SEND_DM").build();
        DagModel model = DagModel.builder().nodes(List.of(node1)).build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, model::validate);
        assertTrue(ex.getMessage().contains("TRIGGER"));
    }

    @Test
    @DisplayName("Should reject DAG with circular dependency (cycle)")
    void shouldRejectCyclicDag() {
        DagModel.DagNode node1 = DagModel.DagNode.builder().id("n1").type("TRIGGER_INSTAGRAM_COMMENT").build();
        DagModel.DagNode node2 = DagModel.DagNode.builder().id("n2").type("ACTION_PUBLIC_COMMENT_REPLY").build();
        DagModel.DagNode node3 = DagModel.DagNode.builder().id("n3").type("ACTION_SEND_DM").build();

        // n1 -> n2 -> n3 -> n2 (cycle between n2 and n3)
        DagModel.DagEdge edge1 = DagModel.DagEdge.builder().from("n1").to("n2").build();
        DagModel.DagEdge edge2 = DagModel.DagEdge.builder().from("n2").to("n3").build();
        DagModel.DagEdge edge3 = DagModel.DagEdge.builder().from("n3").to("n2").build();

        DagModel model = DagModel.builder()
                .nodes(List.of(node1, node2, node3))
                .edges(List.of(edge1, edge2, edge3))
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, model::validate);
        assertTrue(ex.getMessage().contains("cycle"));
    }

    @Test
    @DisplayName("Should parse graph definition from JSON accurately")
    void shouldParseFromJson() {
        String json = """
                {
                    "nodes": [
                        {"id": "node-1", "type": "TRIGGER_INSTAGRAM_COMMENT", "config": {"keywords": ["GUIDE"]}},
                        {"id": "node-2", "type": "ACTION_PUBLIC_COMMENT_REPLY", "config": {"reply": "Check DM!"}}
                    ],
                    "edges": [
                        {"from": "node-1", "to": "node-2"}
                    ]
                }
                """;

        DagModel model = DagModel.fromJson(json, objectMapper);
        assertEquals(2, model.getNodes().size());
        assertEquals(1, model.getEdges().size());
        assertEquals("TRIGGER_INSTAGRAM_COMMENT", model.getNodes().get(0).getType());
    }
}
