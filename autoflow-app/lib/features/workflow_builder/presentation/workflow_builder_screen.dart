import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:autoflow_app/core/theme/app_theme.dart';
import '../../workflows/presentation/workflow_funnel_dialog.dart';
import '../domain/workflow_edge_model.dart';
import '../domain/workflow_graph_model.dart';
import '../domain/workflow_node_model.dart';
import 'widgets/ai_media_generator_dialog.dart';
import 'widgets/node_config_drawer.dart';
import 'widgets/workflow_edge_painter.dart';
import 'widgets/workflow_node_widget.dart';

class WorkflowBuilderScreen extends StatefulWidget {
  final String? workflowId;

  const WorkflowBuilderScreen({super.key, this.workflowId});

  @override
  State<WorkflowBuilderScreen> createState() => _WorkflowBuilderScreenState();
}

class _WorkflowBuilderScreenState extends State<WorkflowBuilderScreen> {
  late WorkflowGraphModel _graph;
  final TransformationController _transformationController = TransformationController();
  String? _selectedNodeId;

  @override
  void initState() {
    super.initState();
    _initDefaultGraph();
  }

  void _initDefaultGraph() {
    _graph = WorkflowGraphModel(
      id: widget.workflowId ?? 'wf-new',
      name: 'Instagram Comment -> DM Lead Magnet',
      status: 'DRAFT',
      nodes: [
        WorkflowNodeModel(
          id: 'node-1',
          type: NodeType.triggerInstagramComment,
          label: 'Instagram Comment Trigger',
          category: NodeCategory.trigger,
          config: {'keywords': ['GUIDE', 'PDF']},
          position: const Offset(300, 100),
        ),
        WorkflowNodeModel(
          id: 'node-2',
          type: NodeType.actionPublicCommentReply,
          label: 'Public Comment Reply',
          category: NodeCategory.action,
          config: {'reply': 'Just sent the guide to your DMs! Check your requests 🎁'},
          position: const Offset(300, 260),
        ),
        WorkflowNodeModel(
          id: 'node-3',
          type: NodeType.actionSendDm,
          label: 'Send DM with PDF Link',
          category: NodeCategory.action,
          config: {
            'message': 'Here is your exclusive guide! Click below to view and download.',
            'buttonUrl': 'https://autoflow.ai/guide.pdf',
          },
          position: const Offset(300, 420),
        ),
      ],
      edges: [
        const WorkflowEdgeModel(id: 'edge-1', fromNodeId: 'node-1', toNodeId: 'node-2'),
        const WorkflowEdgeModel(id: 'edge-2', fromNodeId: 'node-2', toNodeId: 'node-3'),
      ],
    );
  }

  void _showAddNodeDialog() {
    showModalBottomSheet(
      context: context,
      backgroundColor: AppTheme.cardDark,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (context) {
        return Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Add Workflow Node', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
              const SizedBox(height: 16),
              ListTile(
                leading: const Icon(Icons.timer_outlined, color: AppTheme.primaryLight),
                title: const Text('Time Delay / Wait'),
                subtitle: const Text('Wait 5 minutes, 2 hours, or days before executing next action'),
                onTap: () {
                  Navigator.pop(context);
                  _addNode(NodeType.actionDelayWait, 'Wait Delay', NodeCategory.action, {'duration': '5 minutes'});
                },
              ),
              ListTile(
                leading: const Icon(Icons.alternate_email, color: AppTheme.secondary),
                title: const Text('Collect Email Input'),
                subtitle: const Text('Ask user for email address and validate format'),
                onTap: () {
                  Navigator.pop(context);
                  _addNode(NodeType.actionCollectEmail, 'Collect Email', NodeCategory.action, {});
                },
              ),
              ListTile(
                leading: const Icon(Icons.table_chart_outlined, color: AppTheme.success),
                title: const Text('Append to Google Sheet'),
                subtitle: const Text('Save collected contact details to Google Sheets'),
                onTap: () {
                  Navigator.pop(context);
                  _addNode(NodeType.actionGoogleSheetsSync, 'Google Sheets Sync', NodeCategory.action, {});
                },
              ),
              ListTile(
                leading: const Icon(Icons.perm_media_outlined, color: Color(0xFF6366F1)),
                title: const Text('Send Media / Lead Magnet Asset'),
                subtitle: const Text('Send images, guides, or AI generated promo coupons directly via DM'),
                onTap: () {
                  Navigator.pop(context);
                  _addNode(NodeType.actionSendMediaAsset, 'Send Media Asset', NodeCategory.action, {
                    'media_url': '{{lastGeneratedMediaUrl}}',
                    'asset_type': 'IMAGE',
                  });
                },
              ),
              ListTile(
                leading: const Icon(Icons.auto_awesome, color: Color(0xFFF43F5E)),
                title: const Text('AI Media Studio Generator'),
                subtitle: const Text('Synthesize custom dynamic vouchers or lead magnet graphics'),
                onTap: () async {
                  Navigator.pop(context);
                  final generatedUrl = await showDialog<String>(
                    context: context,
                    builder: (context) => const AiMediaGeneratorDialog(),
                  );
                  _addNode(NodeType.actionSendMediaAsset, 'Deliver AI Asset', NodeCategory.action, {
                    'media_url': generatedUrl ?? '{{lastGeneratedMediaUrl}}',
                    'asset_type': 'IMAGE',
                  });
                },
              ),
            ],
          ),
        );
      },
    );
  }

  void _showFunnelDialog() {
    showDialog(
      context: context,
      builder: (context) => WorkflowFunnelDialog(
        workflowId: _graph.id,
        workflowName: _graph.name,
      ),
    );
  }

  void _addNode(NodeType type, String label, NodeCategory category, Map<String, dynamic> config) {
    setState(() {
      final newNode = WorkflowNodeModel(
        id: 'node-${DateTime.now().millisecondsSinceEpoch}',
        type: type,
        label: label,
        category: category,
        config: config,
        position: const Offset(300, 580),
      );
      _graph.addNode(newNode);

      // Connect from last node if exists
      if (_graph.nodes.length > 1) {
        final lastNode = _graph.nodes[_graph.nodes.length - 2];
        _graph.addEdge(WorkflowEdgeModel(
          id: 'edge-${DateTime.now().millisecondsSinceEpoch}',
          fromNodeId: lastNode.id,
          toNodeId: newNode.id,
        ));
      }
    });
  }

  void _publishWorkflow() {
    final validationErrors = _graph.validate();
    if (validationErrors.isNotEmpty) {
      showDialog(
        context: context,
        builder: (context) => AlertDialog(
          backgroundColor: AppTheme.cardDark,
          title: const Row(
            children: [
              Icon(Icons.warning_amber_rounded, color: AppTheme.error),
              SizedBox(width: 8),
              Text('Validation Errors'),
            ],
          ),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: validationErrors.map((err) => Padding(
              padding: const EdgeInsets.symmetric(vertical: 4),
              child: Text('• $err', style: const TextStyle(fontSize: 13, color: AppTheme.textLight)),
            )).toList(),
          ),
          actions: [
            ElevatedButton(onPressed: () => Navigator.pop(context), child: const Text('Fix Issues')),
          ],
        ),
      );
      return;
    }

    setState(() {
      _graph.status = 'PUBLISHED';
    });

    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Workflow published successfully! Active and listening for triggers.'),
        backgroundColor: AppTheme.success,
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final selectedNode = _selectedNodeId != null
        ? _graph.nodes.cast<WorkflowNodeModel?>().firstWhere((n) => n?.id == _selectedNodeId, orElse: () => null)
        : null;

    return Scaffold(
      backgroundColor: AppTheme.bgDark,
      body: Stack(
        children: [
          // Infinite Canvas
          InteractiveViewer(
            transformationController: _transformationController,
            constrained: false,
            boundaryMargin: const EdgeInsets.all(2000),
            minScale: 0.2,
            maxScale: 2.5,
            child: SizedBox(
              width: 3000,
              height: 3000,
              child: Stack(
                children: [
                  // Cubic Bezier Edges
                  CustomPaint(
                    size: const Size(3000, 3000),
                    painter: WorkflowEdgePainter(
                      nodes: _graph.nodes,
                      edges: _graph.edges,
                      selectedNodeId: _selectedNodeId,
                    ),
                  ),

                  // Nodes
                  ..._graph.nodes.map(
                    (node) => WorkflowNodeWidget(
                      node: node,
                      isSelected: node.id == _selectedNodeId,
                      onSelect: () => setState(() => _selectedNodeId = node.id),
                      onDelete: () => setState(() {
                        _graph.removeNode(node.id);
                        if (_selectedNodeId == node.id) _selectedNodeId = null;
                      }),
                      onDrag: (delta) => setState(() => node.position += delta),
                    ),
                  ),
                ],
              ),
            ),
          ),

          // Top Canvas Toolbar
          Positioned(
            top: 20,
            left: 20,
            right: 20,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              decoration: BoxDecoration(
                color: AppTheme.cardDark.withValues(alpha: 0.95),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: AppTheme.borderDark),
                boxShadow: const [BoxShadow(color: Colors.black38, blurRadius: 10)],
              ),
              child: LayoutBuilder(
                builder: (context, constraints) {
                  return SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: ConstrainedBox(
                      constraints: BoxConstraints(minWidth: constraints.maxWidth),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              IconButton(
                                icon: const Icon(Icons.arrow_back, size: 20),
                                onPressed: () {
                                  if (context.canPop()) {
                                    context.pop();
                                  } else {
                                    context.go('/workflows');
                                  }
                                },
                              ),
                              const SizedBox(width: 8),
                              Text(_graph.name, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                              const SizedBox(width: 12),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                                decoration: BoxDecoration(
                                  color: _graph.status == 'PUBLISHED' ? AppTheme.success.withValues(alpha: 0.15) : AppTheme.warning.withValues(alpha: 0.15),
                                  borderRadius: BorderRadius.circular(6),
                                ),
                                child: Text(
                                  _graph.status,
                                  style: TextStyle(
                                    color: _graph.status == 'PUBLISHED' ? AppTheme.success : AppTheme.warning,
                                    fontSize: 10,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(width: 24),
                          Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              // Zoom Controls
                              IconButton(
                                icon: const Icon(Icons.zoom_in, size: 20),
                                onPressed: () => _transformationController.value *= Matrix4.diagonal3Values(1.2, 1.2, 1),
                                tooltip: 'Zoom In',
                              ),
                              IconButton(
                                icon: const Icon(Icons.zoom_out, size: 20),
                                onPressed: () => _transformationController.value *= Matrix4.diagonal3Values(0.8, 0.8, 1),
                                tooltip: 'Zoom Out',
                              ),
                              IconButton(
                                icon: const Icon(Icons.restart_alt, size: 20),
                                onPressed: () => _transformationController.value = Matrix4.identity(),
                                tooltip: 'Reset Zoom',
                              ),
                              const SizedBox(height: 24, child: VerticalDivider(color: AppTheme.borderDark)),
                              const SizedBox(width: 8),

                              // Funnel Stats Button
                              IconButton(
                                onPressed: _showFunnelDialog,
                                icon: const Icon(Icons.insights_rounded, size: 20),
                                tooltip: 'Funnel Stats',
                              ),
                              const SizedBox(width: 8),

                              // Add Node Button
                              OutlinedButton.icon(
                                onPressed: _showAddNodeDialog,
                                icon: const Icon(Icons.add, size: 16),
                                label: const Text('Add Step'),
                              ),
                              const SizedBox(width: 12),

                              // Publish CTA
                              ElevatedButton.icon(
                                onPressed: _publishWorkflow,
                                icon: const Icon(Icons.cloud_upload_outlined, size: 16),
                                label: const Text('Publish Automation'),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  );
                },
              ),
            ),
          ),

          // Node Configuration Drawer (Right Side)
          if (selectedNode != null)
            Positioned(
              top: 0,
              bottom: 0,
              right: 0,
              child: NodeConfigDrawer(
                node: selectedNode,
                onConfigChanged: () => setState(() {}),
                onClose: () => setState(() => _selectedNodeId = null),
              ),
            ),
        ],
      ),
    );
  }
}
