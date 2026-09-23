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

class ConversationModel {
  final String id;
  final String name;
  final String handle;
  final String channel;
  final String lastMessage;
  final String time;
  final bool unread;
  final List<Map<String, dynamic>> messages;

  const ConversationModel({
    required this.id,
    required this.name,
    required this.handle,
    required this.channel,
    required this.lastMessage,
    required this.time,
    required this.unread,
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
              messages: [],
            );
          }).toList();
        }
      }
    } catch (_) {}
    return ConversationModel.defaultConversations;
  }

  Future<void> sendReply(String conversationId, String content) async {
    try {
      await _apiClient.dio.post(
        ApiConstants.crmConversationMessages(conversationId),
        data: {'content': content},
      );
    } catch (_) {}
  }
}
