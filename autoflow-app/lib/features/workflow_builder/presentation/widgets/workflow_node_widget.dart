import 'package:flutter/material.dart';
import '../../domain/workflow_node_model.dart';
import '../../../../core/theme/app_theme.dart';

class WorkflowNodeWidget extends StatelessWidget {
  final WorkflowNodeModel node;
  final bool isSelected;
  final VoidCallback onSelect;
  final VoidCallback onDelete;
  final Function(Offset delta) onDrag;

  const WorkflowNodeWidget({
    super.key,
    required this.node,
    required this.isSelected,
    required this.onSelect,
    required this.onDelete,
    required this.onDrag,
  });

  @override
  Widget build(BuildContext context) {
    return Positioned(
      left: node.position.dx,
      top: node.position.dy,
      child: GestureDetector(
        onTap: onSelect,
        onPanUpdate: (details) => onDrag(details.delta),
        child: Container(
          width: 240,
          height: 120,
          decoration: BoxDecoration(
            color: AppTheme.cardDark,
            borderRadius: BorderRadius.circular(14),
            border: Border.all(
              color: isSelected ? AppTheme.primaryLight : AppTheme.borderDark,
              width: isSelected ? 2.5 : 1,
            ),
            boxShadow: isSelected
                ? [
                    BoxShadow(
                      color: AppTheme.primary.withValues(alpha: 0.35),
                      blurRadius: 16,
                      spreadRadius: 2,
                    ),
                  ]
                : [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.2),
                      blurRadius: 8,
                      offset: const Offset(0, 4),
                    ),
                  ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Node Header Bar
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                decoration: BoxDecoration(
                  color: AppTheme.surfaceDark,
                  borderRadius: const BorderRadius.vertical(top: Radius.circular(12)),
                  border: const Border(bottom: BorderSide(color: AppTheme.borderDark)),
                ),
                child: Row(
                  children: [
                    Icon(node.icon, size: 16, color: node.categoryColor),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        node.category.name.toUpperCase(),
                        style: TextStyle(
                          color: node.categoryColor,
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                          letterSpacing: 1,
                        ),
                      ),
                    ),
                    GestureDetector(
                      onTap: onDelete,
                      child: const Padding(
                        padding: EdgeInsets.all(2.0),
                        child: Icon(Icons.close, size: 14, color: AppTheme.textMuted),
                      ),
                    ),
                  ],
                ),
              ),

              // Node Body
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      node.label,
                      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      _getConfigSummary(node),
                      style: const TextStyle(color: AppTheme.textMuted, fontSize: 11),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  String _getConfigSummary(WorkflowNodeModel node) {
    if (node.type == NodeType.triggerInstagramComment) {
      final kw = node.config['keywords'] as List?;
      return 'Keywords: ${(kw != null && kw.isNotEmpty) ? kw.join(", ") : "None set"}';
    }
    if (node.type == NodeType.actionSendDm) {
      return (node.config['message'] as String?) ?? 'No message configured';
    }
    if (node.type == NodeType.actionPublicCommentReply) {
      return (node.config['reply'] as String?) ?? 'No reply configured';
    }
    if (node.type == NodeType.actionDelayWait) {
      return 'Wait: ${node.config['duration'] ?? "5 minutes"}';
    }
    return 'Click to configure properties';
  }
}
