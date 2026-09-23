import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/app_theme.dart';
import '../data/workflow_repository.dart';

class WorkflowExecutionHistoryDialog extends ConsumerStatefulWidget {
  final String workflowId;
  final String workflowName;

  const WorkflowExecutionHistoryDialog({
    super.key,
    required this.workflowId,
    required this.workflowName,
  });

  static Future<void> show(BuildContext context, String workflowId, String workflowName) {
    return showDialog<void>(
      context: context,
      barrierDismissible: true,
      builder: (context) => WorkflowExecutionHistoryDialog(
        workflowId: workflowId,
        workflowName: workflowName,
      ),
    );
  }

  @override
  ConsumerState<WorkflowExecutionHistoryDialog> createState() => _WorkflowExecutionHistoryDialogState();
}

class _WorkflowExecutionHistoryDialogState extends ConsumerState<WorkflowExecutionHistoryDialog> {
  final Set<String> _retryingExecutionIds = {};

  Future<void> _handleRetry(WorkflowExecutionModel execution) async {
    if (execution.retryCount >= 3) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Maximum retry attempts (3) already reached for this execution.'),
          backgroundColor: AppTheme.warning,
        ),
      );
      return;
    }

    setState(() {
      _retryingExecutionIds.add(execution.id);
    });

    try {
      final repo = ref.read(workflowRepositoryProvider);
      final updated = await repo.retryExecution(execution.id);

      if (!mounted) return;

      if (updated != null) {
        ref.invalidate(workflowExecutionsProvider(widget.workflowId));
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Execution queued for retry (attempt #${updated.retryCount})!'),
            backgroundColor: AppTheme.success,
          ),
        );
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Failed to queue retry. Ensure tenant authorization and failed status.'),
            backgroundColor: AppTheme.error,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Retry failed: $e'),
            backgroundColor: AppTheme.error,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() {
          _retryingExecutionIds.remove(execution.id);
        });
      }
    }
  }

  Color _statusColor(String status) {
    switch (status.toUpperCase()) {
      case 'SUCCESS':
        return AppTheme.success;
      case 'FAILED':
        return AppTheme.error;
      case 'RETRYING':
        return AppTheme.warning;
      case 'RUNNING':
        return Colors.lightBlueAccent;
      default:
        return AppTheme.textMuted;
    }
  }

  IconData _statusIcon(String status) {
    switch (status.toUpperCase()) {
      case 'SUCCESS':
        return Icons.check_circle_outline;
      case 'FAILED':
        return Icons.error_outline;
      case 'RETRYING':
        return Icons.replay;
      case 'RUNNING':
        return Icons.sync;
      default:
        return Icons.help_outline;
    }
  }

  String _formatDate(DateTime? dt) {
    if (dt == null) return 'N/A';
    return '${dt.year}-${dt.month.toString().padLeft(2, '0')}-${dt.day.toString().padLeft(2, '0')} ${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    final executionsAsync = ref.watch(workflowExecutionsProvider(widget.workflowId));

    return Dialog(
      backgroundColor: AppTheme.cardDark,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: AppTheme.borderDark),
      ),
      insetPadding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 820, maxHeight: 660),
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Header
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Expanded(
                    child: Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.all(10),
                          decoration: BoxDecoration(
                            color: AppTheme.primaryLight.withValues(alpha: 0.15),
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: const Icon(Icons.history, color: AppTheme.primaryLight, size: 22),
                        ),
                        const SizedBox(width: 14),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text(
                                'Execution Telemetry & Audit Logs',
                                style: TextStyle(
                                  fontSize: 18,
                                  fontWeight: FontWeight.bold,
                                  color: AppTheme.textLight,
                                ),
                                overflow: TextOverflow.ellipsis,
                              ),
                              const SizedBox(height: 2),
                              Text(
                                'Workflow: ${widget.workflowName}',
                                style: const TextStyle(fontSize: 13, color: AppTheme.textMuted),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 12),
                  Row(
                    children: [
                      IconButton(
                        icon: const Icon(Icons.refresh, color: AppTheme.textMuted, size: 20),
                        tooltip: 'Refresh logs',
                        onPressed: () => ref.invalidate(workflowExecutionsProvider(widget.workflowId)),
                      ),
                      IconButton(
                        icon: const Icon(Icons.close, color: AppTheme.textMuted),
                        onPressed: () => Navigator.of(context).pop(),
                      ),
                    ],
                  ),
                ],
              ),
              const SizedBox(height: 16),

              // Executions Content
              Expanded(
                child: executionsAsync.when(
                  loading: () => const Center(
                    child: CircularProgressIndicator(),
                  ),
                  error: (err, _) => Center(
                    child: Text('Failed to load executions: $err', style: const TextStyle(color: AppTheme.error)),
                  ),
                  data: (executions) {
                    if (executions.isEmpty) {
                      return const Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(Icons.hourglass_empty, size: 48, color: AppTheme.textMuted),
                            SizedBox(height: 12),
                            Text(
                              'No execution runs recorded yet for this workflow.',
                              style: TextStyle(color: AppTheme.textMuted),
                            ),
                          ],
                        ),
                      );
                    }

                    final successCount = executions.where((e) => e.status == 'SUCCESS').length;
                    final failedCount = executions.where((e) => e.status == 'FAILED').length;

                    return Column(
                      children: [
                        // Summary Metrics
                        Row(
                          children: [
                            _buildStatCard('Total Runs', executions.length.toString(), Colors.blueAccent),
                            const SizedBox(width: 12),
                            _buildStatCard('Succeeded', successCount.toString(), AppTheme.success),
                            const SizedBox(width: 12),
                            _buildStatCard('Failed', failedCount.toString(), AppTheme.error),
                          ],
                        ),
                        const SizedBox(height: 16),

                        // List of Execution Runs
                        Expanded(
                          child: ListView.separated(
                            itemCount: executions.length,
                            separatorBuilder: (context, index) => const SizedBox(height: 10),
                            itemBuilder: (context, index) {
                              final exec = executions[index];
                              final color = _statusColor(exec.status);
                              final isRetrying = _retryingExecutionIds.contains(exec.id);

                              return Container(
                                padding: const EdgeInsets.all(14),
                                decoration: BoxDecoration(
                                  color: AppTheme.surfaceDark,
                                  borderRadius: BorderRadius.circular(10),
                                  border: Border.all(color: AppTheme.borderDark),
                                ),
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Row(
                                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                      children: [
                                        Row(
                                          children: [
                                            Icon(_statusIcon(exec.status), size: 18, color: color),
                                            const SizedBox(width: 8),
                                            Container(
                                              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                                              decoration: BoxDecoration(
                                                color: color.withValues(alpha: 0.15),
                                                borderRadius: BorderRadius.circular(6),
                                              ),
                                              child: Text(
                                                exec.status,
                                                style: TextStyle(
                                                  color: color,
                                                  fontWeight: FontWeight.bold,
                                                  fontSize: 11,
                                                ),
                                              ),
                                            ),
                                            const SizedBox(width: 10),
                                            Text(
                                              exec.triggerType ?? 'EVENT_TRIGGER',
                                              style: const TextStyle(
                                                fontWeight: FontWeight.w600,
                                                fontSize: 13,
                                                color: AppTheme.textLight,
                                              ),
                                            ),
                                          ],
                                        ),
                                        Text(
                                          _formatDate(exec.startedAt),
                                          style: const TextStyle(fontSize: 11, color: AppTheme.textMuted),
                                        ),
                                      ],
                                    ),

                                    // Error details + Retry button if failed
                                    if (exec.status == 'FAILED') ...[
                                      const SizedBox(height: 10),
                                      Container(
                                        padding: const EdgeInsets.all(10),
                                        decoration: BoxDecoration(
                                          color: AppTheme.error.withValues(alpha: 0.1),
                                          borderRadius: BorderRadius.circular(8),
                                          border: Border.all(color: AppTheme.error.withValues(alpha: 0.3)),
                                        ),
                                        child: Row(
                                          crossAxisAlignment: CrossAxisAlignment.start,
                                          children: [
                                            const Icon(Icons.warning_amber_rounded, size: 16, color: AppTheme.error),
                                            const SizedBox(width: 8),
                                            Expanded(
                                              child: Text(
                                                exec.errorMessage ?? 'Execution failed due to unhandled runtime exception.',
                                                style: const TextStyle(fontSize: 12, color: AppTheme.textLight),
                                              ),
                                            ),
                                            const SizedBox(width: 12),
                                            ElevatedButton.icon(
                                              onPressed: isRetrying ? null : () => _handleRetry(exec),
                                              style: ElevatedButton.styleFrom(
                                                backgroundColor: AppTheme.warning,
                                                foregroundColor: Colors.black,
                                                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                                                minimumSize: Size.zero,
                                                tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                                              ),
                                              icon: isRetrying
                                                  ? const SizedBox(
                                                      width: 12,
                                                      height: 12,
                                                      child: CircularProgressIndicator(strokeWidth: 2, color: Colors.black),
                                                    )
                                                  : const Icon(Icons.refresh, size: 14),
                                              label: Text(
                                                'Retry (${exec.retryCount}/3)',
                                                style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold),
                                              ),
                                            ),
                                          ],
                                        ),
                                      ),
                                    ],
                                  ],
                                ),
                              );
                            },
                          ),
                        ),
                      ],
                    );
                  },
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildStatCard(String label, String value, Color color) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
        decoration: BoxDecoration(
          color: AppTheme.surfaceDark,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: AppTheme.borderDark),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, style: const TextStyle(fontSize: 11, color: AppTheme.textMuted)),
            const SizedBox(height: 4),
            Text(
              value,
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: color),
            ),
          ],
        ),
      ),
    );
  }
}
