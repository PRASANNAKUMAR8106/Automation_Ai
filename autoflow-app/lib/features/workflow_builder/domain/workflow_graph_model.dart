import 'workflow_edge_model.dart';
import 'workflow_node_model.dart';

class WorkflowGraphModel {
  String id;
  String name;
  String status;
  final List<WorkflowNodeModel> nodes;
  final List<WorkflowEdgeModel> edges;

  WorkflowGraphModel({
    required this.id,
    required this.name,
    this.status = 'DRAFT',
    List<WorkflowNodeModel>? nodes,
    List<WorkflowEdgeModel>? edges,
  })  : nodes = nodes ?? [],
        edges = edges ?? [];

  void addNode(WorkflowNodeModel node) {
    nodes.add(node);
  }

  void removeNode(String nodeId) {
    nodes.removeWhere((n) => n.id == nodeId);
    edges.removeWhere((e) => e.fromNodeId == nodeId || e.toNodeId == nodeId);
  }

  void addEdge(WorkflowEdgeModel edge) {
    // Avoid duplicate edges
    if (!edges.any((e) => e.fromNodeId == edge.fromNodeId && e.toNodeId == edge.toNodeId)) {
      edges.add(edge);
    }
  }

  void removeEdge(String edgeId) {
    edges.removeWhere((e) => e.id == edgeId);
  }

  List<String> validate() {
    final errors = <String>[];

    if (nodes.isEmpty) {
      errors.add('Workflow must contain at least one node');
      return errors;
    }

    final triggers = nodes.where((n) => n.category == NodeCategory.trigger).toList();
    if (triggers.isEmpty) {
      errors.add('Workflow must have at least one trigger node to start');
    }

    // Check for required configs
    for (final node in nodes) {
      if (node.type == NodeType.triggerInstagramComment) {
        final keywords = node.config['keywords'] as List?;
        if (keywords == null || keywords.isEmpty) {
          errors.add('Trigger "${node.label}" must specify at least one keyword');
        }
      }
      if (node.type == NodeType.actionSendDm) {
        final msg = node.config['message'] as String?;
        if (msg == null || msg.trim().isEmpty) {
          errors.add('Action "${node.label}" must specify message content');
        }
      }
    }

    // Check for disconnected nodes if more than 1 node exists
    if (nodes.length > 1) {
      final connectedNodeIds = <String>{};
      for (final edge in edges) {
        connectedNodeIds.add(edge.fromNodeId);
        connectedNodeIds.add(edge.toNodeId);
      }
      for (final node in nodes) {
        if (!connectedNodeIds.contains(node.id)) {
          errors.add('Node "${node.label}" is disconnected from the flow');
        }
      }
    }

    // Check for cycles (DAG validation)
    if (_hasCycle()) {
      errors.add('Workflow graph contains an invalid circular loop (cycle detected)');
    }

    return errors;
  }

  bool _hasCycle() {
    final visited = <String>{};
    final inStack = <String>{};

    bool dfs(String nodeId) {
      visited.add(nodeId);
      inStack.add(nodeId);

      final children = edges.where((e) => e.fromNodeId == nodeId).map((e) => e.toNodeId);
      for (final child in children) {
        if (!visited.contains(child) && dfs(child)) {
          return true;
        } else if (inStack.contains(child)) {
          return true;
        }
      }

      inStack.remove(nodeId);
      return false;
    }

    for (final node in nodes) {
      if (!visited.contains(node.id)) {
        if (dfs(node.id)) return true;
      }
    }

    return false;
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'name': name,
      'status': status,
      'nodes': nodes.map((n) => n.toJson()).toList(),
      'edges': edges.map((e) => e.toJson()).toList(),
    };
  }

  factory WorkflowGraphModel.fromJson(Map<String, dynamic> json) {
    return WorkflowGraphModel(
      id: json['id'] as String,
      name: json['name'] as String,
      status: json['status'] as String? ?? 'DRAFT',
      nodes: (json['nodes'] as List).map((n) => WorkflowNodeModel.fromJson(n as Map<String, dynamic>)).toList(),
      edges: (json['edges'] as List).map((e) => WorkflowEdgeModel.fromJson(e as Map<String, dynamic>)).toList(),
    );
  }
}
