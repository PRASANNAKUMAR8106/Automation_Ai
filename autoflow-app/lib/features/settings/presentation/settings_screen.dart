import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/theme/app_theme.dart';
import '../../auth/presentation/auth_controller.dart';
import '../data/api_key_repository.dart';
import '../data/channel_repository.dart';

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  void _showSecretDialog(BuildContext context, String title, String secretKey) {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (dialogCtx) => AlertDialog(
        backgroundColor: AppTheme.cardDark,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: Row(
          children: [
            const Icon(Icons.key, color: AppTheme.warning),
            const SizedBox(width: 8),
            Text(title, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppTheme.warning.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: AppTheme.warning.withValues(alpha: 0.3)),
              ),
              child: const Row(
                children: [
                  Icon(Icons.shield_outlined, color: AppTheme.warning, size: 20),
                  SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Copy and store this secret securely. For security, it will NEVER be displayed in plaintext again.',
                      style: TextStyle(color: AppTheme.warning, fontSize: 12, fontWeight: FontWeight.w600),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            const Text('Secret Key:', style: TextStyle(color: AppTheme.textMuted, fontSize: 13)),
            const SizedBox(height: 6),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              decoration: BoxDecoration(
                color: AppTheme.surfaceDark,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: AppTheme.borderDark),
              ),
              child: Row(
                children: [
                  Expanded(
                    child: SelectableText(
                      secretKey,
                      style: const TextStyle(
                        fontFamily: 'monospace',
                        fontWeight: FontWeight.bold,
                        fontSize: 13,
                        color: AppTheme.primaryLight,
                      ),
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.copy, size: 18),
                    tooltip: 'Copy to Clipboard',
                    onPressed: () {
                      Clipboard.setData(ClipboardData(text: secretKey));
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(
                          content: Text('Secret copied to clipboard!'),
                          backgroundColor: AppTheme.success,
                          duration: Duration(seconds: 2),
                        ),
                      );
                    },
                  ),
                ],
              ),
            ),
          ],
        ),
        actions: [
          ElevatedButton(
            onPressed: () => Navigator.of(dialogCtx).pop(),
            child: const Text('I Have Saved My Secret'),
          ),
        ],
      ),
    );
  }

  void _showCreateKeyDialog(BuildContext context, WidgetRef ref) {
    final nameController = TextEditingController();
    showDialog(
      context: context,
      builder: (dialogCtx) => AlertDialog(
        backgroundColor: AppTheme.cardDark,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: const Text('Generate New API / Webhook Secret', style: TextStyle(fontWeight: FontWeight.bold)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Enter a descriptive identifier for this API key to manage integrations or webhook signature authentication.',
              style: TextStyle(color: AppTheme.textMuted, fontSize: 13),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: nameController,
              decoration: const InputDecoration(
                labelText: 'Key Label / Integration Name',
                hintText: 'e.g. Production Webhook Listener',
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogCtx).pop(),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () async {
              final name = nameController.text.trim();
              if (name.isEmpty) return;
              Navigator.of(dialogCtx).pop();

              final created = await ref.read(apiKeysNotifierProvider.notifier).createKey(name);
              if (created != null && context.mounted) {
                _showSecretDialog(context, 'New Secret Created', created.secretKey);
              }
            },
            child: const Text('Generate Key'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final authState = ref.watch(authStateNotifierProvider);
    final user = authState.user;
    final channelsAsync = ref.watch(connectedChannelsProvider);
    final channels = channelsAsync.value ?? ConnectedAccountModel.defaultAccounts;
    final keysAsync = ref.watch(apiKeysNotifierProvider);
    final apiKeys = keysAsync.value ?? ApiKeyModel.defaultKeys;

    return Scaffold(
      backgroundColor: Colors.transparent,
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Header
            Text('Settings & Channels Hub', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 4),
            const Text(
              'Manage organization profile, social channel connections, and API webhook secrets.',
              style: TextStyle(color: AppTheme.textMuted, fontSize: 14),
            ),
            const SizedBox(height: 24),

            // Section 1: Profile & Organization
            Card(
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        const Icon(Icons.business_center, color: AppTheme.primaryLight),
                        const SizedBox(width: 8),
                        Text('Organization & Workspace', style: Theme.of(context).textTheme.titleMedium),
                      ],
                    ),
                    const Divider(height: 24),
                    Wrap(
                      spacing: 32,
                      runSpacing: 16,
                      children: [
                        _buildInfoItem('Workspace Name', user?.activeOrganizationName ?? 'AutoFlow Workspace'),
                        _buildInfoItem('Account Email', user?.email ?? 'founder@autoflow.ai'),
                        _buildInfoItem('User Role', user?.role ?? 'OWNER'),
                        _buildInfoItem('Active Tier', 'PRO PLAN', isBadge: true),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 24),

            // Section 2: Social Channels & Webhook Hub
            Card(
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Row(
                          children: [
                            const Icon(Icons.hub_outlined, color: AppTheme.primaryLight),
                            const SizedBox(width: 8),
                            Text('Omnichannel Connections', style: Theme.of(context).textTheme.titleMedium),
                          ],
                        ),
                        IconButton(
                          icon: const Icon(Icons.refresh, size: 20),
                          tooltip: 'Refresh Channel Status',
                          onPressed: () => ref.read(connectedChannelsProvider.notifier).loadAccounts(),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      'Live channel status fetched directly from AutoFlow backend.',
                      style: TextStyle(color: AppTheme.textMuted, fontSize: 13),
                    ),
                    const Divider(height: 24),
                    ...channels.map((channel) {
                      final isConnected = channel.status == 'CONNECTED';
                      Color badgeColor = isConnected ? AppTheme.success : AppTheme.textMuted;
                      IconData channelIcon;
                      switch (channel.channel.toUpperCase()) {
                        case 'INSTAGRAM':
                          channelIcon = Icons.camera_alt_outlined;
                          break;
                        case 'WHATSAPP':
                          channelIcon = Icons.chat_bubble_outline;
                          break;
                        default:
                          channelIcon = Icons.send_outlined;
                      }

                      return Container(
                        margin: const EdgeInsets.only(bottom: 12),
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          color: AppTheme.surfaceDark,
                          borderRadius: BorderRadius.circular(10),
                          border: Border.all(color: AppTheme.borderDark),
                        ),
                        child: Row(
                          children: [
                            Container(
                              padding: const EdgeInsets.all(10),
                              decoration: BoxDecoration(
                                color: AppTheme.cardDark,
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: Icon(channelIcon, color: AppTheme.primaryLight),
                            ),
                            const SizedBox(width: 16),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Row(
                                    children: [
                                      Text(channel.channel, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
                                      const SizedBox(width: 8),
                                      Container(
                                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                        decoration: BoxDecoration(
                                          color: badgeColor.withValues(alpha: 0.12),
                                          borderRadius: BorderRadius.circular(4),
                                        ),
                                        child: Text(
                                          channel.status,
                                          style: TextStyle(color: badgeColor, fontSize: 10, fontWeight: FontWeight.bold),
                                        ),
                                      ),
                                    ],
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    '${channel.accountName} (${channel.accountHandle})',
                                    style: const TextStyle(color: AppTheme.textMuted, fontSize: 12),
                                  ),
                                ],
                              ),
                            ),
                            if (isConnected)
                              OutlinedButton(
                                style: OutlinedButton.styleFrom(foregroundColor: AppTheme.error),
                                onPressed: () async {
                                  final confirm = await showDialog<bool>(
                                    context: context,
                                    builder: (ctx) => AlertDialog(
                                      title: Text('Disconnect ${channel.channel}?'),
                                      content: const Text('Automations responding on this channel will be suspended until reconnected.'),
                                      actions: [
                                        TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
                                        ElevatedButton(
                                          style: ElevatedButton.styleFrom(backgroundColor: AppTheme.error),
                                          onPressed: () => Navigator.pop(ctx, true),
                                          child: const Text('Disconnect'),
                                        ),
                                      ],
                                    ),
                                  );
                                  if (confirm == true) {
                                    await ref.read(connectedChannelsProvider.notifier).disconnect(channel.id);
                                  }
                                },
                                child: const Text('Disconnect'),
                              )
                            else
                              ElevatedButton(
                                onPressed: () {
                                  ref.read(connectedChannelsProvider.notifier).connectMock(channel.channel);
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    SnackBar(content: Text('Connected ${channel.channel} successfully!')),
                                  );
                                },
                                child: const Text('Connect'),
                              ),
                          ],
                        ),
                      );
                    }),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 24),

            // Section 3: Webhook & API Secrets
            Card(
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Row(
                          children: [
                            const Icon(Icons.vpn_key_outlined, color: AppTheme.primaryLight),
                            const SizedBox(width: 8),
                            Text('API & Webhook Secret Tokens', style: Theme.of(context).textTheme.titleMedium),
                          ],
                        ),
                        ElevatedButton.icon(
                          onPressed: () => _showCreateKeyDialog(context, ref),
                          icon: const Icon(Icons.add, size: 18),
                          label: const Text('New Secret'),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      'Secret keys authenticate webhook events and API queries. Secrets are securely hashed and masked on the server.',
                      style: TextStyle(color: AppTheme.textMuted, fontSize: 13),
                    ),
                    const Divider(height: 24),
                    ...apiKeys.map((key) {
                      final isRevoked = key.status == 'REVOKED';

                      return Container(
                        margin: const EdgeInsets.only(bottom: 12),
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          color: AppTheme.surfaceDark,
                          borderRadius: BorderRadius.circular(10),
                          border: Border.all(color: AppTheme.borderDark),
                        ),
                        child: Row(
                          children: [
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Row(
                                    children: [
                                      Text(key.name, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
                                      const SizedBox(width: 8),
                                      Container(
                                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                        decoration: BoxDecoration(
                                          color: (isRevoked ? AppTheme.error : AppTheme.success).withValues(alpha: 0.12),
                                          borderRadius: BorderRadius.circular(4),
                                        ),
                                        child: Text(
                                          key.status,
                                          style: TextStyle(
                                            color: isRevoked ? AppTheme.error : AppTheme.success,
                                            fontSize: 10,
                                            fontWeight: FontWeight.bold,
                                          ),
                                        ),
                                      ),
                                    ],
                                  ),
                                  const SizedBox(height: 4),
                                  SelectableText(
                                    key.maskedKey,
                                    style: const TextStyle(fontFamily: 'monospace', color: AppTheme.textMuted, fontSize: 12),
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    'Scopes: ${key.scopes.join(", ")}  •  Created: ${key.createdAt}',
                                    style: const TextStyle(color: AppTheme.textMuted, fontSize: 11),
                                  ),
                                ],
                              ),
                            ),
                            if (!isRevoked) ...[
                              OutlinedButton.icon(
                                onPressed: () async {
                                  final confirm = await showDialog<bool>(
                                    context: context,
                                    builder: (ctx) => AlertDialog(
                                      title: Text('Rotate "${key.name}"?'),
                                      content: const Text('Rotating invalidates the old secret token immediately and generates a new secret.'),
                                      actions: [
                                        TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
                                        ElevatedButton(
                                          onPressed: () => Navigator.pop(ctx, true),
                                          child: const Text('Rotate Secret'),
                                        ),
                                      ],
                                    ),
                                  );
                                  if (confirm == true) {
                                    final rotated = await ref.read(apiKeysNotifierProvider.notifier).rotateKey(key.id);
                                    if (rotated != null && context.mounted) {
                                      _showSecretDialog(context, 'Secret Rotated Successfully', rotated.secretKey);
                                    }
                                  }
                                },
                                icon: const Icon(Icons.sync, size: 16),
                                label: const Text('Rotate'),
                              ),
                              const SizedBox(width: 8),
                              IconButton(
                                icon: const Icon(Icons.delete_outline, size: 20, color: AppTheme.error),
                                tooltip: 'Revoke Key',
                                onPressed: () async {
                                  final confirm = await showDialog<bool>(
                                    context: context,
                                    builder: (ctx) => AlertDialog(
                                      title: Text('Revoke "${key.name}"?'),
                                      content: const Text('This API key will be permanently deactivated.'),
                                      actions: [
                                        TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
                                        ElevatedButton(
                                          style: ElevatedButton.styleFrom(backgroundColor: AppTheme.error),
                                          onPressed: () => Navigator.pop(ctx, true),
                                          child: const Text('Revoke'),
                                        ),
                                      ],
                                    ),
                                  );
                                  if (confirm == true) {
                                    await ref.read(apiKeysNotifierProvider.notifier).revokeKey(key.id);
                                  }
                                },
                              ),
                            ],
                          ],
                        ),
                      );
                    }),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 24),

            // Section 4: Subscription & Billing
            Card(
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        gradient: const LinearGradient(colors: [AppTheme.primary, AppTheme.secondary]),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: const Icon(Icons.auto_awesome, color: Colors.white),
                    ),
                    const SizedBox(width: 16),
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('AutoFlow Pro Subscription', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
                          SizedBox(height: 2),
                          Text('Unlimited automations, omnichannel webhooks, AI agent responses, and multi-tenant CRM.',
                              style: TextStyle(color: AppTheme.textMuted, fontSize: 13)),
                        ],
                      ),
                    ),
                    ElevatedButton.icon(
                      onPressed: () => context.go('/billing'),
                      icon: const Icon(Icons.credit_card, size: 18),
                      label: const Text('Manage Billing'),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildInfoItem(String label, String value, {bool isBadge = false}) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: const TextStyle(color: AppTheme.textMuted, fontSize: 12)),
        const SizedBox(height: 4),
        if (isBadge)
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            decoration: BoxDecoration(
              color: AppTheme.primary.withValues(alpha: 0.15),
              borderRadius: BorderRadius.circular(6),
              border: Border.all(color: AppTheme.primaryLight.withValues(alpha: 0.3)),
            ),
            child: Text(
              value,
              style: const TextStyle(color: AppTheme.primaryLight, fontWeight: FontWeight.bold, fontSize: 12),
            ),
          )
        else
          Text(value, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14)),
      ],
    );
  }
}
