import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/app_theme.dart';
import '../../dashboard/data/funnel_repository.dart';

class WorkflowFunnelDialog extends ConsumerWidget {
  final String workflowId;
  final String workflowName;

  const WorkflowFunnelDialog({
    super.key,
    required this.workflowId,
    required this.workflowName,
  });

  static Future<void> show(BuildContext context, String workflowId, String workflowName) {
    return showDialog<void>(
      context: context,
      builder: (context) => WorkflowFunnelDialog(
        workflowId: workflowId,
        workflowName: workflowName,
      ),
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final funnelAsync = ref.watch(workflowFunnelProvider(workflowId));

    return Dialog(
      backgroundColor: AppTheme.cardDark,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: AppTheme.borderDark),
      ),
      insetPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 760, maxHeight: 820),
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
                            color: AppTheme.primary.withValues(alpha: 0.15),
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: const Icon(Icons.insights_rounded, color: AppTheme.primaryLight, size: 22),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text(
                                'Conversion Funnel Intelligence',
                                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18, color: AppTheme.textLight),
                                overflow: TextOverflow.ellipsis,
                              ),
                              const SizedBox(height: 2),
                              Text(
                                workflowName,
                                style: const TextStyle(color: AppTheme.textMuted, fontSize: 13),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close, color: AppTheme.textMuted),
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                ],
              ),
              const SizedBox(height: 20),

              // Content Body
              Expanded(
                child: funnelAsync.when(
                  loading: () => const Center(
                    child: CircularProgressIndicator(),
                  ),
                  error: (err, _) => Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.error_outline, color: AppTheme.error, size: 40),
                        const SizedBox(height: 12),
                        Text('Failed to load funnel telemetry: $err', style: const TextStyle(color: AppTheme.textMuted)),
                      ],
                    ),
                  ),
                  data: (funnel) => _buildFunnelContent(context, funnel),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildFunnelContent(BuildContext context, WorkflowFunnelModel funnel) {
    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // KPI Metric Summary Bar
          LayoutBuilder(
            builder: (context, constraints) {
              final isWide = constraints.maxWidth > 500;
              final cards = [
                _KpiCard(
                  label: 'Total Triggers',
                  value: '${funnel.totalRuns}',
                  icon: Icons.play_arrow_outlined,
                  color: AppTheme.primaryLight,
                ),
                _KpiCard(
                  label: 'Conversions',
                  value: '${funnel.successfulRuns}',
                  icon: Icons.check_circle_outline,
                  color: AppTheme.success,
                ),
                _KpiCard(
                  label: 'Funnel Rate',
                  value: '${funnel.overallConversionRate}%',
                  icon: Icons.auto_graph,
                  color: const Color(0xFF6366F1),
                ),
              ];

              return isWide
                  ? Row(
                      children: [
                        Expanded(child: cards[0]),
                        const SizedBox(width: 12),
                        Expanded(child: cards[1]),
                        const SizedBox(width: 12),
                        Expanded(child: cards[2]),
                      ],
                    )
                  : Column(
                      children: [
                        cards[0],
                        const SizedBox(height: 8),
                        cards[1],
                        const SizedBox(height: 8),
                        cards[2],
                      ],
                    );
            },
          ),
          const SizedBox(height: 20),

          // Bottleneck Diagnosis Alert
          if (funnel.bottleneckNodeId != null && funnel.bottleneckDropOffRate > 0)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              margin: const EdgeInsets.only(bottom: 20),
              decoration: BoxDecoration(
                color: AppTheme.warning.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: AppTheme.warning.withValues(alpha: 0.3)),
              ),
              child: Row(
                children: [
                  const Icon(Icons.warning_amber_rounded, color: AppTheme.warning, size: 20),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      'Primary Bottleneck Detected: ${funnel.bottleneckDropOffRate}% drop-off occurred at step [${funnel.bottleneckNodeId}]. Consider optimizing DM copy or incentives.',
                      style: const TextStyle(fontSize: 12, color: AppTheme.textLight, height: 1.4),
                    ),
                  ),
                ],
              ),
            ),

          // Steps Progression Header
          const Text(
            'Step-by-Step DAG Pipeline Conversion',
            style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14, color: AppTheme.textLight),
          ),
          const SizedBox(height: 12),

          // Step Cards
          if (funnel.steps.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 40),
              child: Center(
                child: Text('No node traversal telemetry recorded yet.', style: TextStyle(color: AppTheme.textMuted)),
              ),
            )
          else
            ...funnel.steps.map((step) => _StepProgressCard(step: step, isBottleneck: step.nodeId == funnel.bottleneckNodeId)),
        ],
      ),
    );
  }
}

class _KpiCard extends StatelessWidget {
  final String label;
  final String value;
  final IconData icon;
  final Color color;

  const _KpiCard({
    required this.label,
    required this.value,
    required this.icon,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppTheme.bgDark.withValues(alpha: 0.6),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppTheme.borderDark),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(label, style: const TextStyle(fontSize: 11, color: AppTheme.textMuted)),
              Icon(icon, size: 16, color: color),
            ],
          ),
          const SizedBox(height: 6),
          Text(value, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 18, color: AppTheme.textLight)),
        ],
      ),
    );
  }
}

class _StepProgressCard extends StatelessWidget {
  final FunnelStepMetricModel step;
  final bool isBottleneck;

  const _StepProgressCard({
    required this.step,
    required this.isBottleneck,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppTheme.bgDark.withValues(alpha: 0.4),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isBottleneck ? AppTheme.warning.withValues(alpha: 0.5) : AppTheme.borderDark,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              CircleAvatar(
                radius: 12,
                backgroundColor: isBottleneck ? AppTheme.warning.withValues(alpha: 0.2) : AppTheme.primary.withValues(alpha: 0.2),
                child: Text(
                  '${step.stepIndex}',
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.bold,
                    color: isBottleneck ? AppTheme.warning : AppTheme.primaryLight,
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      step.nodeLabel,
                      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: AppTheme.textLight),
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 2),
                    Text(
                      '${step.nodeType} • avg ${step.avgDurationMs}ms',
                      style: const TextStyle(fontSize: 11, color: AppTheme.textMuted),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: isBottleneck ? AppTheme.warning.withValues(alpha: 0.15) : AppTheme.success.withValues(alpha: 0.15),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  '${step.conversionPercentage}% Retention',
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.bold,
                    color: isBottleneck ? AppTheme.warning : AppTheme.success,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Visual Progress Bar
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: (step.conversionPercentage / 100.0).clamp(0.0, 1.0),
              backgroundColor: AppTheme.cardDark,
              valueColor: AlwaysStoppedAnimation<Color>(
                isBottleneck ? AppTheme.warning : AppTheme.primaryLight,
              ),
              minHeight: 6,
            ),
          ),
          const SizedBox(height: 10),

          // Reached vs Drop-off counts
          Wrap(
            alignment: WrapAlignment.spaceBetween,
            runSpacing: 4,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              Text(
                'Reached: ${step.reachedCount} users (${step.stepConversionPercentage}% from prev step)',
                style: const TextStyle(fontSize: 11, color: AppTheme.textMuted),
              ),
              if (step.dropOffCount > 0)
                Text(
                  '-${step.dropOffCount} dropped off (${step.dropOffPercentage}%)',
                  style: const TextStyle(fontSize: 11, color: AppTheme.error, fontWeight: FontWeight.w500),
                )
              else
                const Text(
                  '0 drop-offs',
                  style: TextStyle(fontSize: 11, color: AppTheme.success, fontWeight: FontWeight.w500),
                ),
            ],
          ),
        ],
      ),
    );
  }
}
