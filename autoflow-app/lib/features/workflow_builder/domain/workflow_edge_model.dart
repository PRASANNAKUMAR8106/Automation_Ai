class WorkflowEdgeModel {
  final String id;
  final String fromNodeId;
  final String toNodeId;
  final String fromPort;
  final String toPort;

  const WorkflowEdgeModel({
    required this.id,
    required this.fromNodeId,
    required this.toNodeId,
    this.fromPort = 'output',
    this.toPort = 'input',
  });

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'fromNodeId': fromNodeId,
      'toNodeId': toNodeId,
      'fromPort': fromPort,
      'toPort': toPort,
    };
  }

  factory WorkflowEdgeModel.fromJson(Map<String, dynamic> json) {
    return WorkflowEdgeModel(
      id: json['id'] as String,
      fromNodeId: json['fromNodeId'] as String,
      toNodeId: json['toNodeId'] as String,
      fromPort: json['fromPort'] as String? ?? 'output',
      toPort: json['toPort'] as String? ?? 'input',
    );
  }
}
