import 'package:flutter_test/flutter_test.dart';
import 'package:autoflow_app/features/workflow_builder/domain/workflow_edge_model.dart';
import 'package:autoflow_app/features/workflow_builder/domain/workflow_graph_model.dart';
import 'package:autoflow_app/features/workflow_builder/domain/workflow_node_model.dart';

void main() {
  group('WorkflowGraphModel Tests', () {
    test('Valid linear DAG produces zero validation errors', () {
      final graph = WorkflowGraphModel(
        id: 'wf-test-1',
        name: 'Instagram Auto DM',
        nodes: [
          WorkflowNodeModel(
            id: 'n1',
            type: NodeType.triggerInstagramComment,
            label: 'Comment Trigger',
            category: NodeCategory.trigger,
            config: {'keywords': ['GUIDE']},
            position: const Offset(100, 100),
          ),
          WorkflowNodeModel(
            id: 'n2',
            type: NodeType.actionSendDm,
            label: 'Send DM',
            category: NodeCategory.action,
            config: {'message': 'Hello from AutoFlow'},
            position: const Offset(100, 300),
          ),
        ],
        edges: [
          const WorkflowEdgeModel(id: 'e1', fromNodeId: 'n1', toNodeId: 'n2'),
        ],
      );

      final errors = graph.validate();
      expect(errors, isEmpty);
    });

    test('Graph without trigger node fails validation', () {
      final graph = WorkflowGraphModel(
        id: 'wf-no-trigger',
        name: 'Broken Flow',
        nodes: [
          WorkflowNodeModel(
            id: 'n1',
            type: NodeType.actionSendDm,
            label: 'Send DM',
            category: NodeCategory.action,
            config: {'message': 'Hello'},
            position: const Offset(100, 100),
          ),
        ],
        edges: [],
      );

      final errors = graph.validate();
      expect(errors, contains('Workflow must have at least one trigger node to start'));
    });

    test('Cycle in graph is detected by Kahn topological sort algorithm', () {
      final graph = WorkflowGraphModel(
        id: 'wf-cycle',
        name: 'Cyclic Flow',
        nodes: [
          WorkflowNodeModel(
            id: 'n1',
            type: NodeType.triggerInstagramComment,
            label: 'Trigger',
            category: NodeCategory.trigger,
            config: {'keywords': ['START']},
            position: const Offset(100, 100),
          ),
          WorkflowNodeModel(
            id: 'n2',
            type: NodeType.conditionKeywordMatch,
            label: 'Condition',
            category: NodeCategory.condition,
            position: const Offset(100, 200),
          ),
          WorkflowNodeModel(
            id: 'n3',
            type: NodeType.actionSendDm,
            label: 'Action',
            category: NodeCategory.action,
            config: {'message': 'Looped action'},
            position: const Offset(100, 300),
          ),
        ],
        edges: [
          const WorkflowEdgeModel(id: 'e1', fromNodeId: 'n1', toNodeId: 'n2'),
          const WorkflowEdgeModel(id: 'e2', fromNodeId: 'n2', toNodeId: 'n3'),
          const WorkflowEdgeModel(id: 'e3', fromNodeId: 'n3', toNodeId: 'n2'), // Cycle: n2 -> n3 -> n2
        ],
      );

      final errors = graph.validate();
      expect(errors, contains('Workflow graph contains an invalid circular loop (cycle detected)'));
    });

    test('Disconnected node without edges produces validation warning', () {
      final graph = WorkflowGraphModel(
        id: 'wf-orphan',
        name: 'Orphan Flow',
        nodes: [
          WorkflowNodeModel(
            id: 'n1',
            type: NodeType.triggerInstagramComment,
            label: 'Trigger',
            category: NodeCategory.trigger,
            config: {'keywords': ['START']},
            position: const Offset(100, 100),
          ),
          WorkflowNodeModel(
            id: 'n2',
            type: NodeType.actionSendDm,
            label: 'Send DM',
            category: NodeCategory.action,
            config: {'message': 'Hello'},
            position: const Offset(100, 300),
          ),
          WorkflowNodeModel(
            id: 'n3',
            type: NodeType.actionDelayWait,
            label: 'Orphan Wait',
            category: NodeCategory.action,
            position: const Offset(400, 100),
          ),
        ],
        edges: [
          const WorkflowEdgeModel(id: 'e1', fromNodeId: 'n1', toNodeId: 'n2'),
        ],
      );

      final errors = graph.validate();
      expect(errors, anyElement(contains('is disconnected from the flow')));
    });

    test('Serialization to and from JSON preserves graph structure', () {
      final original = WorkflowGraphModel(
        id: 'wf-json-test',
        name: 'JSON Test',
        status: 'ACTIVE',
        nodes: [
          WorkflowNodeModel(
            id: 'n1',
            type: NodeType.triggerInstagramComment,
            label: 'Comment Trigger',
            category: NodeCategory.trigger,
            config: {'keywords': ['PRICE', 'INFO']},
            position: const Offset(150, 250),
          ),
        ],
        edges: [],
      );

      final json = original.toJson();
      expect(json['id'], 'wf-json-test');
      expect(json['name'], 'JSON Test');
      expect(json['nodes'], hasLength(1));

      final restored = WorkflowGraphModel.fromJson(json);
      expect(restored.id, original.id);
      expect(restored.name, original.name);
      expect(restored.nodes.length, 1);
      expect(restored.nodes.first.config['keywords'], contains('PRICE'));
      expect(restored.nodes.first.position.dx, 150);
      expect(restored.nodes.first.position.dy, 250);
    });
  });
}
