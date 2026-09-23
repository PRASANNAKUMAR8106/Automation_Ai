import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/constants/api_constants.dart';
import '../../../../core/network/api_client.dart';

class WorkflowListItemModel {
  final String id;
  final String name;
  final String? description;
  final String status;
  final int? activeVersionNumber;
  final String? latestGraphDefinition;

  const WorkflowListItemModel({
    required this.id,
    required this.name,
    this.description,
    required this.status,
    this.activeVersionNumber,
    this.latestGraphDefinition,
  });

  factory WorkflowListItemModel.fromJson(Map<String, dynamic> json) {
    return WorkflowListItemModel(
      id: json['id']?.toString() ?? '',
      name: json['name']?.toString() ?? '',
      description: json['description']?.toString(),
      status: json['status']?.toString() ?? 'DRAFT',
      activeVersionNumber: (json['activeVersionNumber'] as num?)?.toInt(),
      latestGraphDefinition: json['latestGraphDefinition']?.toString(),
    );
  }

  static const defaultWorkflows = [
    WorkflowListItemModel(
      id: 'wf-1',
      name: 'Reel Comment GUIDE -> Deliver Free PDF',
      description: 'Auto reply to comments and send direct message lead magnet',
      status: 'PUBLISHED',
      activeVersionNumber: 1,
    ),
    WorkflowListItemModel(
      id: 'wf-2',
      name: 'Story Reply -> 20% Discount Coupon Delivery',
      description: 'Send discount promo code on story interaction',
      status: 'PUBLISHED',
      activeVersionNumber: 1,
    ),
    WorkflowListItemModel(
      id: 'wf-3',
      name: 'WhatsApp New Inbound -> Lead Qualification AI',
      description: 'AI qualification agent for inbound WhatsApp inquiries',
      status: 'PUBLISHED',
      activeVersionNumber: 2,
    ),
    WorkflowListItemModel(
      id: 'wf-4',
      name: 'Giveaway Funnel -> Collect Email',
      description: 'Collect email address and sync to lead database',
      status: 'DRAFT',
      activeVersionNumber: null,
    ),
  ];
}

final workflowRepositoryProvider = Provider<WorkflowRepository>((ref) {
  return WorkflowRepository(ref.watch(apiClientProvider));
});

final workflowListProvider = FutureProvider<List<WorkflowListItemModel>>((ref) async {
  return ref.watch(workflowRepositoryProvider).getWorkflows();
});

class WorkflowRepository {
  final ApiClient _apiClient;

  WorkflowRepository([ApiClient? apiClient]) : _apiClient = apiClient ?? ApiClient();

  Future<List<WorkflowListItemModel>> getWorkflows() async {
    try {
      final response = await _apiClient.dio.get(ApiConstants.workflows);
      if (response.statusCode == 200 && response.data['success'] == true) {
        final list = response.data['data'] as List<dynamic>? ?? [];
        if (list.isNotEmpty) {
          return list.map((e) => WorkflowListItemModel.fromJson(e as Map<String, dynamic>)).toList();
        }
      }
    } catch (_) {}
    return WorkflowListItemModel.defaultWorkflows;
  }

  Future<WorkflowListItemModel?> createWorkflow(String name, String? description) async {
    try {
      final response = await _apiClient.dio.post(
        ApiConstants.workflows,
        data: {
          'name': name,
          'description': description ?? '',
        },
      );
      if (response.statusCode == 200 && response.data['success'] == true) {
        return WorkflowListItemModel.fromJson(response.data['data'] as Map<String, dynamic>);
      }
    } catch (_) {}
    return null;
  }

  Future<bool> saveVersion(String workflowId, String graphDefinitionJson) async {
    try {
      final response = await _apiClient.dio.post(
        ApiConstants.workflowVersions(workflowId),
        data: {'graphDefinition': graphDefinitionJson},
      );
      return response.statusCode == 200;
    } catch (_) {
      return false;
    }
  }

  Future<bool> publishWorkflow(String workflowId) async {
    try {
      final response = await _apiClient.dio.post(ApiConstants.publishWorkflow(workflowId));
      return response.statusCode == 200;
    } catch (_) {
      return false;
    }
  }

  Future<List<WorkflowExecutionModel>> getExecutions(String workflowId) async {
    try {
      final response = await _apiClient.dio.get(ApiConstants.workflowExecutions(workflowId));
      if (response.statusCode == 200 && response.data['success'] == true) {
        final list = response.data['data'] as List<dynamic>? ?? [];
        if (list.isNotEmpty) {
          return list.map((e) => WorkflowExecutionModel.fromJson(e as Map<String, dynamic>)).toList();
        }
      }
    } catch (_) {}
    return WorkflowExecutionModel.defaultExecutions(workflowId);
  }

  Future<WorkflowExecutionModel?> retryExecution(String executionId) async {
    try {
      final response = await _apiClient.dio.post(ApiConstants.workflowExecutionRetry(executionId));
      if (response.statusCode == 200 && response.data['success'] == true) {
        return WorkflowExecutionModel.fromJson(response.data['data'] as Map<String, dynamic>);
      }
    } catch (_) {}
    return null;
  }
}

class WorkflowExecutionModel {
  final String id;
  final String workflowId;
  final String? workflowName;
  final String? triggerType;
  final String? triggerEventId;
  final String status;
  final String? currentNodeId;
  final String? errorMessage;
  final int retryCount;
  final DateTime? startedAt;
  final DateTime? completedAt;

  const WorkflowExecutionModel({
    required this.id,
    required this.workflowId,
    this.workflowName,
    this.triggerType,
    this.triggerEventId,
    required this.status,
    this.currentNodeId,
    this.errorMessage,
    this.retryCount = 0,
    this.startedAt,
    this.completedAt,
  });

  factory WorkflowExecutionModel.fromJson(Map<String, dynamic> json) {
    return WorkflowExecutionModel(
      id: json['id']?.toString() ?? '',
      workflowId: json['workflowId']?.toString() ?? '',
      workflowName: json['workflowName']?.toString(),
      triggerType: json['triggerType']?.toString(),
      triggerEventId: json['triggerEventId']?.toString(),
      status: json['status']?.toString() ?? 'PENDING',
      currentNodeId: json['currentNodeId']?.toString(),
      errorMessage: json['errorMessage']?.toString(),
      retryCount: (json['retryCount'] as num?)?.toInt() ?? 0,
      startedAt: json['startedAt'] != null ? DateTime.tryParse(json['startedAt'].toString()) : null,
      completedAt: json['completedAt'] != null ? DateTime.tryParse(json['completedAt'].toString()) : null,
    );
  }

  static List<WorkflowExecutionModel> defaultExecutions(String workflowId) => [
    WorkflowExecutionModel(
      id: 'exec-1',
      workflowId: workflowId,
      workflowName: 'Lead Magnet Funnel',
      triggerType: 'TRIGGER_INSTAGRAM_COMMENT',
      status: 'SUCCESS',
      retryCount: 0,
      startedAt: DateTime.now().subtract(const Duration(minutes: 15)),
      completedAt: DateTime.now().subtract(const Duration(minutes: 14)),
    ),
    WorkflowExecutionModel(
      id: 'exec-2',
      workflowId: workflowId,
      workflowName: 'Lead Magnet Funnel',
      triggerType: 'TRIGGER_INSTAGRAM_COMMENT',
      status: 'FAILED',
      errorMessage: 'Instagram Graph API rate limit exceeded (code 429)',
      retryCount: 1,
      startedAt: DateTime.now().subtract(const Duration(hours: 1)),
      completedAt: DateTime.now().subtract(const Duration(minutes: 59)),
    ),
  ];
}

final workflowExecutionsProvider = FutureProvider.family<List<WorkflowExecutionModel>, String>((ref, workflowId) async {
  return ref.watch(workflowRepositoryProvider).getExecutions(workflowId);
});

