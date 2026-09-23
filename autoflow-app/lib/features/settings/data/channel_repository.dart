import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/constants/api_constants.dart';
import '../../../../core/network/api_client.dart';

class ConnectedAccountModel {
  final String id;
  final String organizationId;
  final String channel;
  final String externalAccountId;
  final String accountName;
  final String accountHandle;
  final String status;
  final String? tokenExpiresAt;
  final bool isExpired;

  const ConnectedAccountModel({
    required this.id,
    required this.organizationId,
    required this.channel,
    required this.externalAccountId,
    required this.accountName,
    required this.accountHandle,
    required this.status,
    this.tokenExpiresAt,
    this.isExpired = false,
  });

  factory ConnectedAccountModel.fromJson(Map<String, dynamic> json) {
    return ConnectedAccountModel(
      id: json['id']?.toString() ?? '',
      organizationId: json['organizationId']?.toString() ?? '',
      channel: json['channel']?.toString() ?? 'INSTAGRAM',
      externalAccountId: json['externalAccountId']?.toString() ?? '',
      accountName: json['accountName']?.toString() ?? '',
      accountHandle: json['accountHandle']?.toString() ?? '',
      status: json['status']?.toString() ?? 'CONNECTED',
      tokenExpiresAt: json['tokenExpiresAt']?.toString(),
      isExpired: json['isExpired'] == true,
    );
  }

  static const defaultAccounts = [
    ConnectedAccountModel(
      id: 'acc-ig-1',
      organizationId: 'org-1',
      channel: 'INSTAGRAM',
      externalAccountId: '178414001',
      accountName: 'AutoFlow Official',
      accountHandle: '@autoflow_ai',
      status: 'CONNECTED',
    ),
    ConnectedAccountModel(
      id: 'acc-wa-1',
      organizationId: 'org-1',
      channel: 'WHATSAPP',
      externalAccountId: '+919876543210',
      accountName: 'AutoFlow Support',
      accountHandle: '+91 98765 43210',
      status: 'CONNECTED',
    ),
    ConnectedAccountModel(
      id: 'acc-tg-1',
      organizationId: 'org-1',
      channel: 'TELEGRAM',
      externalAccountId: 'bot_8901234',
      accountName: 'AutoFlow Bot',
      accountHandle: '@AutoFlowAIBot',
      status: 'DISCONNECTED',
    ),
  ];
}

final channelRepositoryProvider = Provider<ChannelRepository>((ref) {
  return ChannelRepository(ref.watch(apiClientProvider));
});

class ConnectedChannelsNotifier extends StateNotifier<AsyncValue<List<ConnectedAccountModel>>> {
  final ChannelRepository _repository;

  ConnectedChannelsNotifier(this._repository) : super(const AsyncValue.loading()) {
    loadAccounts();
  }

  Future<void> loadAccounts() async {
    state = const AsyncValue.loading();
    try {
      final accounts = await _repository.getConnectedAccounts();
      state = AsyncValue.data(accounts);
    } catch (e, st) {
      state = AsyncValue.error(e, st);
    }
  }

  Future<bool> disconnect(String id) async {
    final success = await _repository.disconnectAccount(id);
    if (success) {
      final current = state.value ?? [];
      state = AsyncValue.data(current.map((a) {
        if (a.id == id) {
          return ConnectedAccountModel(
            id: a.id,
            organizationId: a.organizationId,
            channel: a.channel,
            externalAccountId: a.externalAccountId,
            accountName: a.accountName,
            accountHandle: a.accountHandle,
            status: 'DISCONNECTED',
            isExpired: false,
          );
        }
        return a;
      }).toList());
    }
    return success;
  }

  Future<void> connectMock(String channel) async {
    final current = state.value ?? [];
    final updated = current.map((a) {
      if (a.channel.toUpperCase() == channel.toUpperCase()) {
        return ConnectedAccountModel(
          id: a.id,
          organizationId: a.organizationId,
          channel: a.channel,
          externalAccountId: a.externalAccountId,
          accountName: a.accountName,
          accountHandle: a.accountHandle,
          status: 'CONNECTED',
          isExpired: false,
        );
      }
      return a;
    }).toList();
    state = AsyncValue.data(updated);
  }
}

final connectedChannelsProvider = StateNotifierProvider<ConnectedChannelsNotifier, AsyncValue<List<ConnectedAccountModel>>>((ref) {
  return ConnectedChannelsNotifier(ref.watch(channelRepositoryProvider));
});

class ChannelRepository {
  final ApiClient _apiClient;

  ChannelRepository([ApiClient? apiClient]) : _apiClient = apiClient ?? ApiClient();

  Future<List<ConnectedAccountModel>> getConnectedAccounts() async {
    try {
      final response = await _apiClient.dio.get(ApiConstants.channelsConnected);
      if (response.statusCode == 200 && response.data['success'] == true) {
        final list = response.data['data'] as List<dynamic>? ?? [];
        if (list.isNotEmpty) {
          return list.map((e) => ConnectedAccountModel.fromJson(e as Map<String, dynamic>)).toList();
        }
      }
    } catch (_) {}
    return ConnectedAccountModel.defaultAccounts;
  }

  Future<bool> disconnectAccount(String id) async {
    try {
      final response = await _apiClient.dio.delete(ApiConstants.channelDisconnect(id));
      return response.statusCode == 200;
    } catch (_) {
      return true; // Fallback for local mock flow
    }
  }

  Future<String?> initiateOAuth(String channel) async {
    try {
      final response = await _apiClient.dio.get(ApiConstants.channelOAuthConnect(channel));
      if (response.statusCode == 200 && response.data['success'] == true) {
        return response.data['data']?['authorizationUrl']?.toString();
      }
    } catch (_) {}
    return null;
  }
}
