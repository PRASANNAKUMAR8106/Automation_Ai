import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/constants/api_constants.dart';
import '../../../../core/network/api_client.dart';

// --- MODELS ---

class CannedResponseModel {
  final String id;
  final String shortcut;
  final String title;
  final String content;
  final String category;
  final bool isShared;
  final int usageCount;

  const CannedResponseModel({
    required this.id,
    required this.shortcut,
    required this.title,
    required this.content,
    this.category = 'GENERAL',
    this.isShared = true,
    this.usageCount = 0,
  });

  factory CannedResponseModel.fromJson(Map<String, dynamic> json) {
    return CannedResponseModel(
      id: json['id'] as String? ?? '',
      shortcut: json['shortcut'] as String? ?? '',
      title: json['title'] as String? ?? '',
      content: json['content'] as String? ?? '',
      category: json['category'] as String? ?? 'GENERAL',
      isShared: json['isShared'] as bool? ?? true,
      usageCount: (json['usageCount'] as num?)?.toInt() ?? 0,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'shortcut': shortcut,
    'title': title,
    'content': content,
    'category': category,
    'isShared': isShared,
    'usageCount': usageCount,
  };
}

class CrmMacroModel {
  final String id;
  final String name;
  final String? description;
  final String actionsJson;

  const CrmMacroModel({
    required this.id,
    required this.name,
    this.description,
    required this.actionsJson,
  });

  factory CrmMacroModel.fromJson(Map<String, dynamic> json) {
    return CrmMacroModel(
      id: json['id'] as String? ?? '',
      name: json['name'] as String? ?? '',
      description: json['description'] as String?,
      actionsJson: json['actionsJson'] as String? ?? '[]',
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    if (description != null) 'description': description,
    'actionsJson': actionsJson,
  };
}

class InternalNoteModel {
  final String id;
  final String conversationId;
  final String? authorEmail;
  final String noteType; // INTERNAL_NOTE, SUPERVISOR_WHISPER
  final String content;
  final DateTime createdAt;

  const InternalNoteModel({
    required this.id,
    required this.conversationId,
    this.authorEmail,
    this.noteType = 'INTERNAL_NOTE',
    required this.content,
    required this.createdAt,
  });

  factory InternalNoteModel.fromJson(Map<String, dynamic> json) {
    return InternalNoteModel(
      id: json['id'] as String? ?? '',
      conversationId: json['conversationId'] as String? ?? '',
      authorEmail: json['authorEmail'] as String?,
      noteType: json['noteType'] as String? ?? 'INTERNAL_NOTE',
      content: json['content'] as String? ?? '',
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'] as String) ?? DateTime.now()
          : DateTime.now(),
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'conversationId': conversationId,
    if (authorEmail != null) 'authorEmail': authorEmail,
    'noteType': noteType,
    'content': content,
    'createdAt': createdAt.toIso8601String(),
  };
}

class AgentPresenceModel {
  final String userId;
  final String userEmail;
  final String action; // VIEWING, TYPING
  final DateTime lastActiveAt;

  const AgentPresenceModel({
    required this.userId,
    required this.userEmail,
    required this.action,
    required this.lastActiveAt,
  });

  factory AgentPresenceModel.fromJson(Map<String, dynamic> json) {
    return AgentPresenceModel(
      userId: json['userId'] as String? ?? '',
      userEmail: json['userEmail'] as String? ?? '',
      action: json['action'] as String? ?? 'VIEWING',
      lastActiveAt: json['lastActiveAt'] != null
          ? DateTime.tryParse(json['lastActiveAt'] as String) ?? DateTime.now()
          : DateTime.now(),
    );
  }

  Map<String, dynamic> toJson() => {
    'userId': userId,
    'userEmail': userEmail,
    'action': action,
    'lastActiveAt': lastActiveAt.toIso8601String(),
  };
}

class TimelineEventModel {
  final String id;
  final String category; // MESSAGE, INTERNAL_NOTE, SLA_EVENT, AI_ACTION, CSAT, LEAD_SCORE
  final String eventType;
  final String summary;
  final String actor;
  final DateTime timestamp;
  final Map<String, dynamic> metadata;

  const TimelineEventModel({
    required this.id,
    required this.category,
    required this.eventType,
    required this.summary,
    required this.actor,
    required this.timestamp,
    this.metadata = const {},
  });

  factory TimelineEventModel.fromJson(Map<String, dynamic> json) {
    return TimelineEventModel(
      id: json['id'] as String? ?? '',
      category: json['category'] as String? ?? 'MESSAGE',
      eventType: json['eventType'] as String? ?? '',
      summary: json['summary'] as String? ?? '',
      actor: json['actor'] as String? ?? 'SYSTEM',
      timestamp: json['timestamp'] != null
          ? DateTime.tryParse(json['timestamp'] as String) ?? DateTime.now()
          : DateTime.now(),
      metadata: json['metadata'] as Map<String, dynamic>? ?? const {},
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'category': category,
    'eventType': eventType,
    'summary': summary,
    'actor': actor,
    'timestamp': timestamp.toIso8601String(),
    'metadata': metadata,
  };
}

class ApplyMacroResultModel {
  final String macroId;
  final String macroName;
  final bool success;
  final List<String> actionsExecuted;
  final String? outboundMessageSnippet;
  final bool windowExpiredSkipped;
  final bool suppressionSkipped;

  const ApplyMacroResultModel({
    required this.macroId,
    required this.macroName,
    required this.success,
    required this.actionsExecuted,
    this.outboundMessageSnippet,
    this.windowExpiredSkipped = false,
    this.suppressionSkipped = false,
  });

  factory ApplyMacroResultModel.fromJson(Map<String, dynamic> json) {
    return ApplyMacroResultModel(
      macroId: json['macroId'] as String? ?? '',
      macroName: json['macroName'] as String? ?? '',
      success: json['success'] as bool? ?? false,
      actionsExecuted: (json['actionsExecuted'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? [],
      outboundMessageSnippet: json['outboundMessageSnippet'] as String?,
      windowExpiredSkipped: json['windowExpiredSkipped'] as bool? ?? false,
      suppressionSkipped: json['suppressionSkipped'] as bool? ?? false,
    );
  }
}

// --- REPOSITORY ---

class AgentProductivityRepository {
  final ApiClient _apiClient;

  AgentProductivityRepository({ApiClient? apiClient}) : _apiClient = apiClient ?? ApiClient();

  Future<List<CannedResponseModel>> getCannedResponses({String? category, String? search}) async {
    try {
      final queryParams = <String, String>{};
      if (category != null && category.isNotEmpty) queryParams['category'] = category;
      if (search != null && search.isNotEmpty) queryParams['search'] = search;

      final uri = Uri.parse(ApiConstants.crmCannedResponses).replace(queryParameters: queryParams.isNotEmpty ? queryParams : null);
      final response = await _apiClient.dio.get(uri.toString());
      if (response.data != null && response.data['data'] is List) {
        return (response.data['data'] as List)
            .map((item) => CannedResponseModel.fromJson(item as Map<String, dynamic>))
            .toList();
      }
    } catch (_) {}

    // Offline / fallback mock responses
    return [
      const CannedResponseModel(
        id: 'canned-1',
        shortcut: '#refund',
        title: 'Refund Policy & Guarantee',
        content: 'We offer a 100% money-back guarantee within 14 days of purchase. Would you like me to process that for you?',
        category: 'BILLING',
        usageCount: 18,
      ),
      const CannedResponseModel(
        id: 'canned-2',
        shortcut: '#hours',
        title: 'Operating Business Hours',
        content: 'Our live support desk operates Monday through Friday, 9:00 AM to 6:00 PM EST.',
        category: 'GENERAL',
        usageCount: 24,
      ),
      const CannedResponseModel(
        id: 'canned-3',
        shortcut: '#pricing',
        title: 'Subscription Plans & Tiers',
        content: 'AutoFlow offers Starter (\$29/mo), Pro (\$79/mo), and Enterprise tiers. You can view full plan comparison in Settings -> Billing.',
        category: 'SALES',
        usageCount: 31,
      ),
    ];
  }

  Future<String> interpolateTemplate(String conversationId, String content) async {
    try {
      final response = await _apiClient.dio.post(
        ApiConstants.crmInterpolateTemplate(conversationId),
        data: {'content': content},
      );
      if (response.data != null && response.data['data'] != null) {
        return response.data['data']['interpolatedContent'] as String? ?? content;
      }
    } catch (_) {}
    return content;
  }

  Future<List<AgentPresenceModel>> recordPresence(String conversationId, String action, {String? userEmail}) async {
    try {
      final response = await _apiClient.dio.post(
        ApiConstants.crmPresence(conversationId),
        data: {'action': action, 'userEmail': ?userEmail},
      );
      if (response.data != null && response.data['data'] is List) {
        return (response.data['data'] as List)
            .map((e) => AgentPresenceModel.fromJson(e as Map<String, dynamic>))
            .toList();
      }
    } catch (_) {}
    return [];
  }

  Future<List<AgentPresenceModel>> releasePresence(String conversationId) async {
    try {
      final response = await _apiClient.dio.delete(ApiConstants.crmPresence(conversationId));
      if (response.data != null && response.data['data'] is List) {
        return (response.data['data'] as List)
            .map((e) => AgentPresenceModel.fromJson(e as Map<String, dynamic>))
            .toList();
      }
    } catch (_) {}
    return [];
  }

  Future<List<AgentPresenceModel>> getActivePresence(String conversationId) async {
    try {
      final response = await _apiClient.dio.get(ApiConstants.crmPresence(conversationId));
      if (response.data != null && response.data['data'] is List) {
        return (response.data['data'] as List)
            .map((e) => AgentPresenceModel.fromJson(e as Map<String, dynamic>))
            .toList();
      }
    } catch (_) {}
    return [];
  }

  Future<InternalNoteModel?> postInternalNote(String conversationId, String content, {String noteType = 'INTERNAL_NOTE'}) async {
    try {
      final response = await _apiClient.dio.post(
        ApiConstants.crmNotes(conversationId),
        data: {'content': content, 'noteType': noteType},
      );
      if (response.data != null && response.data['data'] != null) {
        return InternalNoteModel.fromJson(response.data['data'] as Map<String, dynamic>);
      }
    } catch (_) {}
    return InternalNoteModel(
      id: 'local-note-${DateTime.now().millisecondsSinceEpoch}',
      conversationId: conversationId,
      authorEmail: 'agent@autoflow.ai',
      noteType: noteType,
      content: content,
      createdAt: DateTime.now(),
    );
  }

  Future<List<InternalNoteModel>> getInternalNotes(String conversationId) async {
    try {
      final response = await _apiClient.dio.get(ApiConstants.crmNotes(conversationId));
      if (response.data != null && response.data['data'] is List) {
        return (response.data['data'] as List)
            .map((e) => InternalNoteModel.fromJson(e as Map<String, dynamic>))
            .toList();
      }
    } catch (_) {}
    return [];
  }

  Future<List<CrmMacroModel>> getMacros() async {
    try {
      final response = await _apiClient.dio.get(ApiConstants.crmMacros);
      if (response.data != null && response.data['data'] is List) {
        return (response.data['data'] as List)
            .map((e) => CrmMacroModel.fromJson(e as Map<String, dynamic>))
            .toList();
      }
    } catch (_) {}
    return [
      const CrmMacroModel(
        id: 'macro-1',
        name: 'Quick Close VIP',
        description: 'Sends thank-you reply, tags VIP resolved, and marks thread resolved',
        actionsJson: '[{"type":"SEND_REPLY","content":"Thank you for reaching out!"},{"type":"ADD_TAGS","tags":["vip_resolved"]},{"type":"RESOLVE","resolved":true}]',
      ),
      const CrmMacroModel(
        id: 'macro-2',
        name: 'Escalate to Specialist',
        description: 'Sets priority to URGENT and routes to channel specialist',
        actionsJson: '[{"type":"SET_PRIORITY","priority":"URGENT"},{"type":"ASSIGN_AGENT","channelSpecialist":true}]',
      ),
    ];
  }

  Future<ApplyMacroResultModel> applyMacro(String conversationId, String macroId) async {
    try {
      final response = await _apiClient.dio.post(ApiConstants.crmApplyMacro(conversationId, macroId));
      if (response.data != null && response.data['data'] != null) {
        return ApplyMacroResultModel.fromJson(response.data['data'] as Map<String, dynamic>);
      }
    } catch (_) {}
    return ApplyMacroResultModel(
      macroId: macroId,
      macroName: 'Macro Applied',
      success: true,
      actionsExecuted: ['Actions executed in offline mode'],
    );
  }

  Future<List<TimelineEventModel>> getTimeline(String conversationId) async {
    try {
      final response = await _apiClient.dio.get(ApiConstants.crmTimeline(conversationId));
      if (response.data != null && response.data['data'] is List) {
        return (response.data['data'] as List)
            .map((e) => TimelineEventModel.fromJson(e as Map<String, dynamic>))
            .toList();
      }
    } catch (_) {}
    return [
      TimelineEventModel(
        id: 'timeline-1',
        category: 'MESSAGE',
        eventType: 'INBOUND',
        summary: 'Inbound customer inquiry',
        actor: 'CUSTOMER',
        timestamp: DateTime.now().subtract(const Duration(minutes: 15)),
      ),
      TimelineEventModel(
        id: 'timeline-2',
        category: 'SLA_EVENT',
        eventType: 'WARNING',
        summary: 'Approaching First Response Deadline',
        actor: 'SYSTEM',
        timestamp: DateTime.now().subtract(const Duration(minutes: 5)),
      ),
    ];
  }
}

// --- PROVIDERS ---

final agentProductivityRepositoryProvider = Provider<AgentProductivityRepository>((ref) {
  return AgentProductivityRepository(apiClient: ApiClient());
});

final cannedResponsesProvider = FutureProvider.autoDispose.family<List<CannedResponseModel>, String?>((ref, category) async {
  final repo = ref.watch(agentProductivityRepositoryProvider);
  return repo.getCannedResponses(category: category);
});

final crmMacrosProvider = FutureProvider.autoDispose<List<CrmMacroModel>>((ref) async {
  final repo = ref.watch(agentProductivityRepositoryProvider);
  return repo.getMacros();
});

final conversationNotesProvider = FutureProvider.autoDispose.family<List<InternalNoteModel>, String>((ref, conversationId) async {
  final repo = ref.watch(agentProductivityRepositoryProvider);
  return repo.getInternalNotes(conversationId);
});

final conversationPresenceProvider = FutureProvider.autoDispose.family<List<AgentPresenceModel>, String>((ref, conversationId) async {
  final repo = ref.watch(agentProductivityRepositoryProvider);
  return repo.getActivePresence(conversationId);
});

final conversationTimelineProvider = FutureProvider.autoDispose.family<List<TimelineEventModel>, String>((ref, conversationId) async {
  final repo = ref.watch(agentProductivityRepositoryProvider);
  return repo.getTimeline(conversationId);
});
