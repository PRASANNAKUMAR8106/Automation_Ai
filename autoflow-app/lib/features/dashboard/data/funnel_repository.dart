import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/constants/api_constants.dart';
import '../../../../core/network/api_client.dart';

class FunnelStepMetricModel {
  final String nodeId;
  final String nodeLabel;
  final String nodeType;
  final int stepIndex;
  final int reachedCount;
  final int dropOffCount;
  final double conversionPercentage;
  final double stepConversionPercentage;
  final double dropOffPercentage;
  final int avgDurationMs;

  const FunnelStepMetricModel({
    required this.nodeId,
    required this.nodeLabel,
    required this.nodeType,
    required this.stepIndex,
    required this.reachedCount,
    required this.dropOffCount,
    required this.conversionPercentage,
    required this.stepConversionPercentage,
    required this.dropOffPercentage,
    required this.avgDurationMs,
  });

  factory FunnelStepMetricModel.fromJson(Map<String, dynamic> json) {
    return FunnelStepMetricModel(
      nodeId: json['nodeId'] as String? ?? '',
      nodeLabel: json['nodeLabel'] as String? ?? 'Step',
      nodeType: json['nodeType'] as String? ?? '',
      stepIndex: (json['stepIndex'] as num?)?.toInt() ?? 1,
      reachedCount: (json['reachedCount'] as num?)?.toInt() ?? 0,
      dropOffCount: (json['dropOffCount'] as num?)?.toInt() ?? 0,
      conversionPercentage: (json['conversionPercentage'] as num?)?.toDouble() ?? 0.0,
      stepConversionPercentage: (json['stepConversionPercentage'] as num?)?.toDouble() ?? 0.0,
      dropOffPercentage: (json['dropOffPercentage'] as num?)?.toDouble() ?? 0.0,
      avgDurationMs: (json['avgDurationMs'] as num?)?.toInt() ?? 120,
    );
  }
}

class WorkflowFunnelModel {
  final String workflowId;
  final String workflowName;
  final String status;
  final int totalRuns;
  final int successfulRuns;
  final int failedRuns;
  final double overallConversionRate;
  final String? bottleneckNodeId;
  final double bottleneckDropOffRate;
  final List<FunnelStepMetricModel> steps;

  const WorkflowFunnelModel({
    required this.workflowId,
    required this.workflowName,
    required this.status,
    required this.totalRuns,
    required this.successfulRuns,
    required this.failedRuns,
    required this.overallConversionRate,
    this.bottleneckNodeId,
    required this.bottleneckDropOffRate,
    required this.steps,
  });

  factory WorkflowFunnelModel.fromJson(Map<String, dynamic> json) {
    final stepsRaw = json['steps'] as List<dynamic>? ?? [];
    return WorkflowFunnelModel(
      workflowId: json['workflowId'] as String? ?? '',
      workflowName: json['workflowName'] as String? ?? 'Workflow Funnel',
      status: json['status'] as String? ?? 'DRAFT',
      totalRuns: (json['totalRuns'] as num?)?.toInt() ?? 0,
      successfulRuns: (json['successfulRuns'] as num?)?.toInt() ?? 0,
      failedRuns: (json['failedRuns'] as num?)?.toInt() ?? 0,
      overallConversionRate: (json['overallConversionRate'] as num?)?.toDouble() ?? 0.0,
      bottleneckNodeId: json['bottleneckNodeId'] as String?,
      bottleneckDropOffRate: (json['bottleneckDropOffRate'] as num?)?.toDouble() ?? 0.0,
      steps: stepsRaw.map((s) => FunnelStepMetricModel.fromJson(s as Map<String, dynamic>)).toList(),
    );
  }

  static WorkflowFunnelModel sampleFallback(String id, [String? name]) {
    return WorkflowFunnelModel(
      workflowId: id,
      workflowName: name ?? 'Instagram Lead Magnet Funnel',
      status: 'PUBLISHED',
      totalRuns: 248,
      successfulRuns: 194,
      failedRuns: 54,
      overallConversionRate: 78.2,
      bottleneckNodeId: 'node-3',
      bottleneckDropOffRate: 15.4,
      steps: const [
        FunnelStepMetricModel(
          nodeId: 'node-1',
          nodeLabel: 'Instagram Comment Trigger ("GUIDE")',
          nodeType: 'TRIGGER_INSTAGRAM_COMMENT',
          stepIndex: 1,
          reachedCount: 248,
          dropOffCount: 12,
          conversionPercentage: 100.0,
          stepConversionPercentage: 100.0,
          dropOffPercentage: 4.8,
          avgDurationMs: 85,
        ),
        FunnelStepMetricModel(
          nodeId: 'node-2',
          nodeLabel: 'Public Comment Reply',
          nodeType: 'ACTION_PUBLIC_COMMENT_REPLY',
          stepIndex: 2,
          reachedCount: 236,
          dropOffCount: 16,
          conversionPercentage: 95.2,
          stepConversionPercentage: 95.2,
          dropOffPercentage: 6.8,
          avgDurationMs: 140,
        ),
        FunnelStepMetricModel(
          nodeId: 'node-3',
          nodeLabel: 'Send DM with Lead Magnet Asset',
          nodeType: 'ACTION_SEND_DM',
          stepIndex: 3,
          reachedCount: 220,
          dropOffCount: 26,
          conversionPercentage: 88.7,
          stepConversionPercentage: 93.2,
          dropOffPercentage: 11.8,
          avgDurationMs: 195,
        ),
        FunnelStepMetricModel(
          nodeId: 'node-4',
          nodeLabel: 'Tag Contact as "Lead Magnet"',
          nodeType: 'ACTION_TAG_CONTACT',
          stepIndex: 4,
          reachedCount: 194,
          dropOffCount: 0,
          conversionPercentage: 78.2,
          stepConversionPercentage: 88.2,
          dropOffPercentage: 0.0,
          avgDurationMs: 110,
        ),
      ],
    );
  }
}

final funnelRepositoryProvider = Provider<FunnelRepository>((ref) {
  return FunnelRepository(ref.watch(apiClientProvider));
});

final workflowFunnelProvider = FutureProvider.family<WorkflowFunnelModel, String>((ref, workflowId) async {
  return ref.watch(funnelRepositoryProvider).getWorkflowFunnel(workflowId);
});

class FunnelRepository {
  final ApiClient _apiClient;

  FunnelRepository([ApiClient? apiClient]) : _apiClient = apiClient ?? ApiClient();

  Future<WorkflowFunnelModel> getWorkflowFunnel(String workflowId) async {
    try {
      final response = await _apiClient.dio.get(ApiConstants.analyticsWorkflowFunnel(workflowId));
      if (response.statusCode == 200 && response.data['success'] == true) {
        return WorkflowFunnelModel.fromJson(response.data['data'] as Map<String, dynamic>);
      }
    } catch (_) {
      // Fallback to sample model in offline/preview
    }
    return WorkflowFunnelModel.sampleFallback(workflowId);
  }
}
