import 'package:flutter/material.dart';
import '../../domain/workflow_node_model.dart';
import '../../../../core/theme/app_theme.dart';

class NodeConfigDrawer extends StatefulWidget {
  final WorkflowNodeModel node;
  final VoidCallback onConfigChanged;
  final VoidCallback onClose;

  const NodeConfigDrawer({
    super.key,
    required this.node,
    required this.onConfigChanged,
    required this.onClose,
  });

  @override
  State<NodeConfigDrawer> createState() => _NodeConfigDrawerState();
}

class _NodeConfigDrawerState extends State<NodeConfigDrawer> {
  late TextEditingController _labelController;
  late TextEditingController _primaryTextController;
  late TextEditingController _secondaryTextController;

  @override
  void initState() {
    super.initState();
    _labelController = TextEditingController(text: widget.node.label);

    if (widget.node.type == NodeType.triggerInstagramComment) {
      final kw = widget.node.config['keywords'] as List?;
      _primaryTextController = TextEditingController(text: kw?.join(', ') ?? '');
      _secondaryTextController = TextEditingController();
    } else if (widget.node.type == NodeType.actionPublicCommentReply) {
      _primaryTextController = TextEditingController(text: widget.node.config['reply'] ?? '');
      _secondaryTextController = TextEditingController();
    } else if (widget.node.type == NodeType.actionSendDm) {
      _primaryTextController = TextEditingController(text: widget.node.config['message'] ?? '');
      _secondaryTextController = TextEditingController(text: widget.node.config['buttonUrl'] ?? '');
    } else {
      _primaryTextController = TextEditingController();
      _secondaryTextController = TextEditingController();
    }
  }

  @override
  void dispose() {
    _labelController.dispose();
    _primaryTextController.dispose();
    _secondaryTextController.dispose();
    super.dispose();
  }

  void _save() {
    if (widget.node.type == NodeType.triggerInstagramComment) {
      final text = _primaryTextController.text.trim();
      widget.node.config['keywords'] = text.split(',').map((e) => e.trim().toUpperCase()).where((e) => e.isNotEmpty).toList();
    } else if (widget.node.type == NodeType.actionPublicCommentReply) {
      widget.node.config['reply'] = _primaryTextController.text.trim();
    } else if (widget.node.type == NodeType.actionSendDm) {
      widget.node.config['message'] = _primaryTextController.text.trim();
      if (_secondaryTextController.text.trim().isNotEmpty) {
        widget.node.config['buttonUrl'] = _secondaryTextController.text.trim();
      }
    }
    widget.onConfigChanged();
    widget.onClose();
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 380,
      padding: const EdgeInsets.all(24),
      decoration: const BoxDecoration(
        color: AppTheme.cardDark,
        border: Border(left: BorderSide(color: AppTheme.borderDark)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Icon(widget.node.icon, color: widget.node.categoryColor, size: 20),
                  const SizedBox(width: 8),
                  Text('Configure Node', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontSize: 18)),
                ],
              ),
              IconButton(onPressed: widget.onClose, icon: const Icon(Icons.close)),
            ],
          ),
          const SizedBox(height: 20),

          // Node Label Field
          const Text('Node Title', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
          const SizedBox(height: 6),
          TextField(controller: _labelController, decoration: const InputDecoration(hintText: 'Node Title')),
          const SizedBox(height: 20),

          // Contextual Fields based on Node Type
          if (widget.node.type == NodeType.triggerInstagramComment) ...[
            const Text('Trigger Keywords (Comma separated)', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
            const SizedBox(height: 4),
            const Text('Comments matching any of these will trigger the workflow.', style: TextStyle(color: AppTheme.textMuted, fontSize: 11)),
            const SizedBox(height: 8),
            TextField(controller: _primaryTextController, decoration: const InputDecoration(hintText: 'GUIDE, PDF, PRICE, EBOOK')),
          ] else if (widget.node.type == NodeType.actionPublicCommentReply) ...[
            const Text('Public Comment Reply Text', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
            const SizedBox(height: 4),
            const Text('This response is published directly as a reply to the user comment.', style: TextStyle(color: AppTheme.textMuted, fontSize: 11)),
            const SizedBox(height: 8),
            TextField(controller: _primaryTextController, maxLines: 3, decoration: const InputDecoration(hintText: 'Just sent you a DM with the guide! Check your requests 🎁')),
          ] else if (widget.node.type == NodeType.actionSendDm) ...[
            const Text('Direct Message Text', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
            const SizedBox(height: 8),
            TextField(controller: _primaryTextController, maxLines: 4, decoration: const InputDecoration(hintText: 'Hey! Here is your download link for the guide.')),
            const SizedBox(height: 16),
            const Text('Download / External Button URL', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
            const SizedBox(height: 8),
            TextField(controller: _secondaryTextController, decoration: const InputDecoration(hintText: 'https://example.com/guide.pdf')),
          ],

          const Spacer(),

          // Action Buttons
          Row(
            children: [
              Expanded(
                child: OutlinedButton(onPressed: widget.onClose, child: const Text('Cancel')),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: ElevatedButton(onPressed: _save, child: const Text('Save Properties')),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
