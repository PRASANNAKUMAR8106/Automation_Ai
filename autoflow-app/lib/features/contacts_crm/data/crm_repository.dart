import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/constants/api_constants.dart';
import '../../../../core/network/api_client.dart';

class ContactModel {
  final String id;
  final String channel;
  final String externalId;
  final String? username;
  final String? fullName;
  final String? email;
  final String leadStatus;
  final List<String> tags;
  final String lastInteraction;

  const ContactModel({
    required this.id,
    required this.channel,
    required this.externalId,
    this.username,
    this.fullName,
    this.email,
    required this.leadStatus,
    required this.tags,
    required this.lastInteraction,
  });

  factory ContactModel.fromJson(Map<String, dynamic> json) {
    return ContactModel(
      id: json['id']?.toString() ?? '',
      channel: json['channel']?.toString() ?? 'INSTAGRAM',
      externalId: json['externalId']?.toString() ?? '',
      username: json['username']?.toString(),
      fullName: json['fullName']?.toString(),
      email: json['email']?.toString(),
      leadStatus: json['leadStatus']?.toString() ?? 'NEW',
      tags: (json['tags'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? [],
      lastInteraction: json['lastInteractionAt'] != null ? 'Active' : 'Recently',
    );
  }

  static const defaultContacts = [
    ContactModel(
      id: 'c-1',
      channel: 'INSTAGRAM',
      externalId: '17841400',
      username: '@priya_sharma',
      fullName: 'Priya Sharma',
      email: 'priya@example.com',
      leadStatus: 'HOT_LEAD',
      tags: ['guide_downloaded', 'high_intent'],
      lastInteraction: '10m ago',
    ),
    ContactModel(
      id: 'c-2',
      channel: 'INSTAGRAM',
      externalId: '17841401',
      username: '@rahul_tech',
      fullName: 'Rahul Verma',
      email: 'rahul@verma.dev',
      leadStatus: 'QUALIFIED',
      tags: ['pricing_requested'],
      lastInteraction: '1h ago',
    ),
    ContactModel(
      id: 'c-3',
      channel: 'WHATSAPP',
      externalId: '+919876543210',
      username: '+91 98765 43210',
      fullName: 'Ananya Roy',
      email: null,
      leadStatus: 'NEW',
      tags: ['whatsapp_lead'],
      lastInteraction: '3h ago',
    ),
  ];
}

class MessagingWindowStatusModel {
  final String conversationId;
  final String channel;
  final String windowStatus; // ACTIVE_24H, HUMAN_AGENT_EXTENDED_7D, EXPIRED, UNRESTRICTED
  final int remainingSeconds;
  final DateTime? lastCustomerMessageAt;
  final DateTime? windowExpiresAt;
  final bool canSendFreeform;
  final bool canSendHumanAgent;
  final String policyDescription;

  const MessagingWindowStatusModel({
    required this.conversationId,
    required this.channel,
    required this.windowStatus,
    required this.remainingSeconds,
    this.lastCustomerMessageAt,
    this.windowExpiresAt,
    required this.canSendFreeform,
    required this.canSendHumanAgent,
    required this.policyDescription,
  });

  factory MessagingWindowStatusModel.fromJson(Map<String, dynamic> json) {
    return MessagingWindowStatusModel(
      conversationId: json['conversationId']?.toString() ?? '',
      channel: json['channel']?.toString() ?? 'INSTAGRAM',
      windowStatus: json['windowStatus']?.toString() ?? 'ACTIVE_24H',
      remainingSeconds: (json['remainingSeconds'] as num?)?.toInt() ?? 86400,
      lastCustomerMessageAt: json['lastCustomerMessageAt'] != null
          ? DateTime.tryParse(json['lastCustomerMessageAt'].toString())
          : null,
      windowExpiresAt: json['windowExpiresAt'] != null
          ? DateTime.tryParse(json['windowExpiresAt'].toString())
          : null,
      canSendFreeform: json['canSendFreeform'] as bool? ?? true,
      canSendHumanAgent: json['canSendHumanAgent'] as bool? ?? true,
      policyDescription: json['policyDescription']?.toString() ?? '',
    );
  }

  String get formattedRemainingTime {
    if (windowStatus == 'UNRESTRICTED') return 'Unrestricted';
    if (windowStatus == 'EXPIRED' || remainingSeconds <= 0) return 'Window Closed';
    final hours = remainingSeconds ~/ 3600;
    final minutes = (remainingSeconds % 3600) ~/ 60;
    if (hours > 0) {
      return '${hours}h ${minutes}m left';
    } else {
      return '${minutes}m left';
    }
  }
}

class ConversationModel {
  final String id;
  final String name;
  final String handle;
  final String channel;
  final String lastMessage;
  final String time;
  final bool unread;
  final bool isResolved;
  final String windowStatus;
  final int windowRemainingSeconds;
  final List<Map<String, dynamic>> messages;

  const ConversationModel({
    required this.id,
    required this.name,
    required this.handle,
    required this.channel,
    required this.lastMessage,
    required this.time,
    required this.unread,
    this.isResolved = false,
    this.windowStatus = 'ACTIVE_24H',
    this.windowRemainingSeconds = 86400,
    required this.messages,
  });

  static const defaultConversations = [
    ConversationModel(
      id: 'conv-1',
      name: 'Sneha Kapoor',
      handle: '@snehak_designs',
      channel: 'INSTAGRAM',
      lastMessage: 'Just downloaded the PDF, thank you so much!',
      time: '3m ago',
      unread: true,
      isResolved: false,
      windowStatus: 'ACTIVE_24H',
      windowRemainingSeconds: 79200,
      messages: [
        {'sender': 'contact', 'text': 'GUIDE', 'time': '10:14 AM'},
        {'sender': 'bot', 'text': 'Hey Sneha! Here is the PDF you requested 🎁', 'time': '10:14 AM'},
        {'sender': 'contact', 'text': 'Just downloaded the PDF, thank you so much!', 'time': '10:16 AM'},
      ],
    ),
    ConversationModel(
      id: 'conv-2',
      name: 'Vikram Rathore',
      handle: '+91 98765 43210',
      channel: 'WHATSAPP',
      lastMessage: 'Can I book a 1-on-1 coaching call?',
      time: '25m ago',
      unread: false,
      isResolved: false,
      windowStatus: 'ACTIVE_24H',
      windowRemainingSeconds: 43200,
      messages: [
        {'sender': 'contact', 'text': 'DEMO', 'time': '09:45 AM'},
        {'sender': 'bot', 'text': 'Welcome to AutoFlow! How can we help your business today?', 'time': '09:45 AM'},
        {'sender': 'contact', 'text': 'Can I book a 1-on-1 coaching call?', 'time': '09:48 AM'},
      ],
    ),
  ];
}

final crmRepositoryProvider = Provider<CrmRepository>((ref) {
  return CrmRepository(ref.watch(apiClientProvider));
});

final crmContactsProvider = FutureProvider<List<ContactModel>>((ref) async {
  return ref.watch(crmRepositoryProvider).getContacts();
});

final crmConversationsProvider = FutureProvider<List<ConversationModel>>((ref) async {
  return ref.watch(crmRepositoryProvider).getConversations();
});

final crmWindowStatusProvider = FutureProvider.family<MessagingWindowStatusModel, String>((ref, conversationId) async {
  return ref.watch(crmRepositoryProvider).getWindowStatus(conversationId);
});

class CrmRepository {
  final ApiClient _apiClient;

  CrmRepository([ApiClient? apiClient]) : _apiClient = apiClient ?? ApiClient();

  Future<List<ContactModel>> getContacts({String? search, String? tag}) async {
    try {
      final response = await _apiClient.dio.get(
        ApiConstants.crmContacts,
        queryParameters: {
          if (search != null && search.isNotEmpty) 'search': search,
          if (tag != null && tag.isNotEmpty) 'tag': tag,
        },
      );
      if (response.statusCode == 200 && response.data['success'] == true) {
        final list = response.data['data'] as List<dynamic>? ?? [];
        if (list.isNotEmpty) {
          return list.map((e) => ContactModel.fromJson(e as Map<String, dynamic>)).toList();
        }
      }
    } catch (_) {}
    return ContactModel.defaultContacts;
  }

  Future<String> exportContactsCsv({String? search, String? tag}) async {
    try {
      final response = await _apiClient.dio.get<String>(
        ApiConstants.crmContactsExport,
        queryParameters: {
          if (search != null && search.isNotEmpty) 'search': search,
          if (tag != null && tag.isNotEmpty) 'tag': tag,
        },
      );
      if (response.statusCode == 200 && response.data != null) {
        return response.data!;
      }
    } catch (_) {}
    final buffer = StringBuffer();
    buffer.writeln('Contact ID,Channel,External ID,Username,Full Name,Email,Phone,Lead Status,Tags,Created At,Last Interaction');
    for (final c in ContactModel.defaultContacts) {
      buffer.writeln('${c.id},${c.channel},${c.externalId},${c.username ?? ""},${c.fullName ?? ""},${c.email ?? ""},,+15550000,${c.leadStatus},${c.tags.join(";")},2026-09-23T00:00:00Z,2026-09-23T00:00:00Z');
    }
    return buffer.toString();
  }

  Future<void> addTag(String contactId, String tag) async {
    try {
      await _apiClient.dio.post(
        ApiConstants.crmContactTags(contactId),
        data: {'tags': [tag]},
      );
    } catch (_) {}
  }

  Future<void> removeTag(String contactId, String tag) async {
    try {
      await _apiClient.dio.delete(ApiConstants.crmContactTag(contactId, tag));
    } catch (_) {}
  }

  Future<List<ConversationModel>> getConversations() async {
    try {
      final response = await _apiClient.dio.get(ApiConstants.crmConversations);
      if (response.statusCode == 200 && response.data['success'] == true) {
        final list = response.data['data'] as List<dynamic>? ?? [];
        if (list.isNotEmpty) {
          return list.map((item) {
            final json = item as Map<String, dynamic>;
            final contact = json['contact'] as Map<String, dynamic>? ?? {};
            return ConversationModel(
              id: json['id']?.toString() ?? '',
              name: contact['fullName']?.toString() ?? contact['username']?.toString() ?? 'Contact',
              handle: contact['username']?.toString() ?? contact['externalId']?.toString() ?? '',
              channel: json['channel']?.toString() ?? 'INSTAGRAM',
              lastMessage: json['lastMessageSnippet']?.toString() ?? '',
              time: 'Recent',
              unread: (json['unreadCount'] as num?)?.toInt() != 0,
              isResolved: json['resolved'] == true || json['isResolved'] == true,
              windowStatus: json['windowStatus']?.toString() ?? 'ACTIVE_24H',
              windowRemainingSeconds: (json['windowRemainingSeconds'] as num?)?.toInt() ?? 86400,
              messages: [],
            );
          }).toList();
        }
      }
    } catch (_) {}
    return ConversationModel.defaultConversations;
  }

  Future<MessagingWindowStatusModel> getWindowStatus(String conversationId) async {
    try {
      final response = await _apiClient.dio.get(ApiConstants.crmConversationWindowStatus(conversationId));
      if (response.statusCode == 200 && response.data['success'] == true) {
        return MessagingWindowStatusModel.fromJson(response.data['data'] as Map<String, dynamic>);
      }
    } catch (_) {}
    return MessagingWindowStatusModel(
      conversationId: conversationId,
      channel: 'INSTAGRAM',
      windowStatus: 'ACTIVE_24H',
      remainingSeconds: 86400,
      canSendFreeform: true,
      canSendHumanAgent: true,
      policyDescription: 'Customer message is within standard 24-hour care window.',
    );
  }

  Future<bool> resolveConversation(String conversationId, bool resolved) async {
    try {
      final response = await _apiClient.dio.post(
        ApiConstants.crmConversationResolve(conversationId),
        data: {'resolved': resolved},
      );
      if (response.statusCode == 200 && response.data['success'] == true) {
        final data = response.data['data'] as Map<String, dynamic>? ?? {};
        return data['resolved'] == true || data['isResolved'] == true;
      }
    } catch (_) {}
    return resolved;
  }

  Future<void> sendTyping(String conversationId, bool isTyping) async {
    try {
      await _apiClient.dio.post(
        ApiConstants.crmConversationTyping(conversationId),
        data: {'isTyping': isTyping},
      );
    } catch (_) {}
  }

  Future<List<Map<String, dynamic>>> getMessages(String conversationId) async {
    try {
      final response = await _apiClient.dio.get(ApiConstants.crmConversationMessages(conversationId));
      if (response.statusCode == 200 && response.data['success'] == true) {
        final list = response.data['data'] as List<dynamic>? ?? [];
        return list.map((m) {
          final json = m as Map<String, dynamic>;
          final isAgent = json['senderType'] == 'AGENT' || json['direction'] == 'OUTBOUND';
          return {
            'id': json['id']?.toString(),
            'sender': isAgent ? 'agent' : 'contact',
            'text': json['content']?.toString() ?? '',
            'time': json['sentAt'] != null ? 'Recent' : 'Now',
          };
        }).toList();
      }
    } catch (_) {}
    return [];
  }

  Future<void> sendReply(String conversationId, String content, {bool humanAgentTag = false}) async {
    try {
      await _apiClient.dio.post(
        ApiConstants.crmConversationMessages(conversationId),
        data: {
          'content': content,
          'humanAgentTag': humanAgentTag,
        },
      );
    } catch (_) {}
  }
}
