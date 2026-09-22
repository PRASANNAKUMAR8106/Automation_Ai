import 'package:flutter/material.dart';
import '../../domain/workflow_edge_model.dart';
import '../../domain/workflow_node_model.dart';
import '../../../../core/theme/app_theme.dart';

class WorkflowEdgePainter extends CustomPainter {
  final List<WorkflowNodeModel> nodes;
  final List<WorkflowEdgeModel> edges;
  final String? selectedNodeId;

  const WorkflowEdgePainter({
    required this.nodes,
    required this.edges,
    this.selectedNodeId,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final nodeMap = {for (var n in nodes) n.id: n};

    final paint = Paint()
      ..color = AppTheme.borderDark
      ..strokeWidth = 2.5
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;

    final activePaint = Paint()
      ..color = AppTheme.primaryLight
      ..strokeWidth = 3.0
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;

    for (final edge in edges) {
      final fromNode = nodeMap[edge.fromNodeId];
      final toNode = nodeMap[edge.toNodeId];
      if (fromNode == null || toNode == null) continue;

      // Calculate anchor connection points (standard node dimensions: width 240, height 120)
      final start = Offset(fromNode.position.dx + 120, fromNode.position.dy + 120);
      final end = Offset(toNode.position.dx + 120, toNode.position.dy);

      final isHighlighted = edge.fromNodeId == selectedNodeId || edge.toNodeId == selectedNodeId;
      final currentPaint = isHighlighted ? activePaint : paint;

      // Smooth cubic Bezier curve
      final path = Path();
      path.moveTo(start.dx, start.dy);

      final controlPointY1 = start.dy + (end.dy - start.dy).abs() * 0.5;
      final controlPointY2 = end.dy - (end.dy - start.dy).abs() * 0.5;

      path.cubicTo(
        start.dx,
        controlPointY1,
        end.dx,
        controlPointY2,
        end.dx,
        end.dy,
      );

      canvas.drawPath(path, currentPaint);

      // Draw connection circle port at end
      final portPaint = Paint()
        ..color = isHighlighted ? AppTheme.primaryLight : AppTheme.borderDark
        ..style = PaintingStyle.fill;
      canvas.drawCircle(end, 5, portPaint);
    }
  }

  @override
  bool shouldRepaint(covariant WorkflowEdgePainter oldDelegate) {
    return oldDelegate.nodes != nodes ||
        oldDelegate.edges != edges ||
        oldDelegate.selectedNodeId != selectedNodeId;
  }
}
