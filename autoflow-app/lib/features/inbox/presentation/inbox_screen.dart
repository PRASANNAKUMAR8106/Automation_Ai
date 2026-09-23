import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/app_theme.dart';
import '../../contacts_crm/data/crm_repository.dart';

class InboxScreen extends ConsumerStatefulWidget {
  const InboxScreen({super.key});

  @override
  ConsumerState<InboxScreen> createState() => _InboxScreenState();
}

class _InboxScreenState extends ConsumerState<InboxScreen> {
  int _selectedChatIndex = 0;
  final TextEditingController _replyController = TextEditingController();
  bool _useHumanAgentTag = false;

  final List<Map<String, dynamic>> _threads = [
    {
      'id': 'conv-1',
      'name': 'Sneha Kapoor',
      'handle': '@snehak_designs',
      'channel': 'INSTAGRAM',
      'lastMessage': 'Just downloaded the PDF, thank you so much!',
      'time': '3m ago',
      'unread': true,
      'isResolved': false,
      'windowStatus': 'ACTIVE_24H',
      'remainingSeconds': 79200,
      'messages': [
        {'sender': 'contact', 'text': 'GUIDE', 'time': '10:14 AM'},
        {'sender': 'bot', 'text': 'Hey Sneha! Here is the PDF you requested 🎁', 'time': '10:14 AM'},
        {'sender': 'contact', 'text': 'Just downloaded the PDF, thank you so much!', 'time': '10:16 AM'},
      ]
    },
    {
      'id': 'conv-2',
      'name': 'Vikram Rathore',
      'handle': '+91 98765 43210',
      'channel': 'WHATSAPP',
      'lastMessage': 'Can I book a 1-on-1 coaching call?',
      'time': '25m ago',
      'unread': false,
      'isResolved': false,
      'windowStatus': 'ACTIVE_24H',
      'remainingSeconds': 43200,
      'messages': [
        {'sender': 'contact', 'text': 'DEMO', 'time': '09:45 AM'},
        {'sender': 'bot', 'text': 'Welcome to AutoFlow! How can we help your business today?', 'time': '09:45 AM'},
        {'sender': 'contact', 'text': 'Can I book a 1-on-1 coaching call?', 'time': '09:48 AM'},
      ]
    },
    {
      'id': 'conv-3',
      'name': 'Ananya Roy',
      'handle': '@ananya_fitness',
      'channel': 'INSTAGRAM',
      'lastMessage': 'Does the discount code expire today?',
      'time': '1h ago',
      'unread': false,
      'isResolved': false,
      'windowStatus': 'HUMAN_AGENT_EXTENDED_7D',
      'remainingSeconds': 432000,
      'messages': [
        {'sender': 'contact', 'text': 'Does the discount code expire today?', 'time': '08:30 AM'},
      ]
    },
    {
      'id': 'conv-4',
      'name': 'Devin Vance',
      'handle': '@devin_leads',
      'channel': 'INSTAGRAM',
      'lastMessage': 'Need help with setup from last week',
      'time': '8d ago',
      'unread': false,
      'isResolved': true,
      'windowStatus': 'EXPIRED',
      'remainingSeconds': 0,
      'messages': [
        {'sender': 'contact', 'text': 'Need help with setup from last week', 'time': '8d ago'},
      ]
    },
  ];

  @override
  void dispose() {
    _replyController.dispose();
    super.dispose();
  }

  void _sendReply() {
    final text = _replyController.text.trim();
    if (text.isEmpty) return;

    final activeThread = _threads[_selectedChatIndex];
    final currentConvoId = activeThread['id'] as String? ?? 'conv-1';
    final status = activeThread['windowStatus'] as String? ?? 'ACTIVE_24H';

    if (status == 'EXPIRED') {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Cannot send message: Meta 24-hour window has expired.'),
          backgroundColor: Colors.redAccent,
        ),
      );
      return;
    }

    if (status == 'HUMAN_AGENT_EXTENDED_7D' && !_useHumanAgentTag) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Meta 24-hour window passed: Please attach Human Agent Tag.'),
          backgroundColor: Colors.orangeAccent,
        ),
      );
      return;
    }

    ref.read(crmRepositoryProvider).sendReply(
          currentConvoId,
          text,
          humanAgentTag: _useHumanAgentTag,
        );

    setState(() {
      final messages = activeThread['messages'] as List;
      messages.add({
        'sender': 'agent',
        'text': text,
        'time': 'Just now',
      });
      activeThread['lastMessage'] = text;
      _replyController.clear();
    });
  }

  Widget _buildComplianceBadge(Map<String, dynamic> thread) {
    final status = thread['windowStatus'] as String? ?? 'ACTIVE_24H';
    final channel = thread['channel'] as String? ?? 'INSTAGRAM';
    final seconds = thread['remainingSeconds'] as int? ?? 86400;

    Color badgeColor;
    Color textColor;
    String text;
    IconData icon;

    if (channel == 'TELEGRAM' || status == 'UNRESTRICTED') {
      badgeColor = const Color(0xFF0088CC).withValues(alpha: 0.15);
      textColor = const Color(0xFF29B6F6);
      text = 'Telegram: Unrestricted Window';
      icon = Icons.lock_open_rounded;
    } else if (status == 'ACTIVE_24H') {
      badgeColor = Colors.green.withValues(alpha: 0.15);
      textColor = Colors.greenAccent;
      final hours = seconds ~/ 3600;
      text = '24h Window Active (${hours}h remaining)';
      icon = Icons.check_circle_outline;
    } else if (status == 'HUMAN_AGENT_EXTENDED_7D') {
      badgeColor = Colors.orange.withValues(alpha: 0.15);
      textColor = Colors.orangeAccent;
      final days = seconds ~/ 86400;
      text = 'Human Agent Tag Required (${days}d remaining)';
      icon = Icons.support_agent;
    } else {
      badgeColor = Colors.red.withValues(alpha: 0.15);
      textColor = Colors.redAccent;
      text = '24h Window Closed';
      icon = Icons.warning_amber_rounded;
    }

    return Container(
      key: const Key('messaging_window_badge'),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: badgeColor,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: textColor.withValues(alpha: 0.3)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: textColor),
          const SizedBox(width: 6),
          Text(
            text,
            style: TextStyle(color: textColor, fontSize: 11, fontWeight: FontWeight.w600),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final activeThread = _threads[_selectedChatIndex];
    final messages = activeThread['messages'] as List;
    final isResolved = activeThread['isResolved'] == true;

    return Scaffold(
      backgroundColor: Colors.transparent,
      body: Row(
        children: [
          // Left Pane: Conversation Thread Directory
          SizedBox(
            width: 340,
            child: Container(
              decoration: const BoxDecoration(
                border: Border(right: BorderSide(color: AppTheme.borderDark)),
              ),
              child: Column(
                children: [
                  Padding(
                    padding: const EdgeInsets.all(16),
                    child: TextField(
                      decoration: InputDecoration(
                        hintText: 'Search conversations...',
                        prefixIcon: const Icon(Icons.search, size: 18),
                        contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                        fillColor: AppTheme.cardDark,
                      ),
                    ),
                  ),
                  Expanded(
                    child: ListView.separated(
                      itemCount: _threads.length,
                      separatorBuilder: (context, index) => const Divider(height: 1, color: AppTheme.borderDark),
                      itemBuilder: (context, index) {
                        final thread = _threads[index];
                        final isSelected = index == _selectedChatIndex;
                        final isItemResolved = thread['isResolved'] == true;

                        return ListTile(
                          selected: isSelected,
                          selectedTileColor: AppTheme.cardDark,
                          leading: CircleAvatar(
                            backgroundColor: thread['channel'] == 'INSTAGRAM' ? const Color(0xFFE1306C) : const Color(0xFF25D366),
                            radius: 18,
                            child: Text(
                              (thread['name'] as String)[0],
                              style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
                            ),
                          ),
                          title: Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Expanded(
                                child: Text(
                                  thread['name'] as String,
                                  style: TextStyle(
                                    fontWeight: thread['unread'] ? FontWeight.bold : FontWeight.w500,
                                    fontSize: 14,
                                    decoration: isItemResolved ? TextDecoration.lineThrough : null,
                                  ),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                              Text(
                                thread['time'] as String,
                                style: const TextStyle(color: AppTheme.textMuted, fontSize: 11),
                              ),
                            ],
                          ),
                          subtitle: Row(
                            children: [
                              if (isItemResolved)
                                const Padding(
                                  padding: EdgeInsets.only(right: 4),
                                  child: Icon(Icons.check_circle, size: 12, color: Colors.greenAccent),
                                ),
                              Expanded(
                                child: Text(
                                  thread['lastMessage'] as String,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: TextStyle(
                                    color: thread['unread'] ? AppTheme.textLight : AppTheme.textMuted,
                                    fontSize: 12,
                                  ),
                                ),
                              ),
                            ],
                          ),
                          onTap: () => setState(() {
                            _selectedChatIndex = index;
                            _useHumanAgentTag = false;
                          }),
                        );
                      },
                    ),
                  ),
                ],
              ),
            ),
          ),

          // Right Pane: Active Chat Conversation
          Expanded(
            child: Column(
              children: [
                // Conversation Header
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
                  decoration: const BoxDecoration(
                    color: AppTheme.surfaceDark,
                    border: Border(bottom: BorderSide(color: AppTheme.borderDark)),
                  ),
                  child: Row(
                    children: [
                      CircleAvatar(
                        backgroundColor: activeThread['channel'] == 'INSTAGRAM' ? const Color(0xFFE1306C) : const Color(0xFF25D366),
                        radius: 16,
                        child: Text((activeThread['name'] as String)[0], style: const TextStyle(color: Colors.white)),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Flexible(
                                  child: Text(
                                    activeThread['name'] as String,
                                    style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ),
                                if (isResolved) ...[
                                  const SizedBox(width: 8),
                                  Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                    decoration: BoxDecoration(
                                      color: Colors.green.withValues(alpha: 0.2),
                                      borderRadius: BorderRadius.circular(4),
                                    ),
                                    child: const Text(
                                      'RESOLVED',
                                      style: TextStyle(color: Colors.greenAccent, fontSize: 10, fontWeight: FontWeight.bold),
                                    ),
                                  ),
                                ],
                              ],
                            ),
                            Text(
                              activeThread['handle'] as String,
                              style: const TextStyle(color: AppTheme.textMuted, fontSize: 12),
                              overflow: TextOverflow.ellipsis,
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 12),
                      _buildComplianceBadge(activeThread),
                      const SizedBox(width: 12),
                      OutlinedButton.icon(
                        key: const Key('resolve_button'),
                        onPressed: () async {
                          final newResolved = !isResolved;
                          final convoId = activeThread['id'] as String? ?? 'conv-1';
                          setState(() {
                            activeThread['isResolved'] = newResolved;
                          });
                          await ref.read(crmRepositoryProvider).resolveConversation(convoId, newResolved);
                        },
                        icon: Icon(
                          isResolved ? Icons.undo : Icons.check,
                          size: 16,
                          color: isResolved ? Colors.orangeAccent : Colors.greenAccent,
                        ),
                        label: Text(
                          isResolved ? 'Reopen' : 'Resolve',
                          style: TextStyle(
                            color: isResolved ? Colors.orangeAccent : Colors.greenAccent,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),

                // Message Transcript
                Expanded(
                  child: ListView.builder(
                    padding: const EdgeInsets.all(20),
                    itemCount: messages.length,
                    itemBuilder: (context, idx) {
                      final msg = messages[idx] as Map<String, dynamic>;
                      final isContact = msg['sender'] == 'contact';

                      return Align(
                        alignment: isContact ? Alignment.centerLeft : Alignment.centerRight,
                        child: Container(
                          margin: const EdgeInsets.symmetric(vertical: 4),
                          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                          constraints: const BoxConstraints(maxWidth: 480),
                          decoration: BoxDecoration(
                            color: isContact ? AppTheme.cardDark : AppTheme.primary,
                            borderRadius: BorderRadius.circular(16),
                            border: isContact ? Border.all(color: AppTheme.borderDark) : null,
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                msg['text'] as String,
                                style: const TextStyle(color: Colors.white, fontSize: 14),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                msg['time'] as String,
                                style: TextStyle(
                                  color: Colors.white.withValues(alpha: 0.6),
                                  fontSize: 10,
                                ),
                              ),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
                ),

                // Reply Composer
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: const BoxDecoration(
                    color: AppTheme.surfaceDark,
                    border: Border(top: BorderSide(color: AppTheme.borderDark)),
                  ),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (activeThread['channel'] == 'INSTAGRAM')
                        Padding(
                          padding: const EdgeInsets.only(bottom: 8),
                          child: Row(
                            children: [
                              Checkbox(
                                key: const Key('human_agent_tag_checkbox'),
                                value: _useHumanAgentTag,
                                activeColor: AppTheme.primary,
                                onChanged: (val) => setState(() => _useHumanAgentTag = val ?? false),
                              ),
                              const Expanded(
                                child: Text(
                                  'Attach Meta HUMAN_AGENT Tag (for customer care inquiries within 7 days)',
                                  style: TextStyle(color: AppTheme.textMuted, fontSize: 12),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                            ],
                          ),
                        ),
                      Row(
                        children: [
                          Expanded(
                            child: TextField(
                              controller: _replyController,
                              decoration: const InputDecoration(
                                hintText: 'Type your message or response...',
                                contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                              ),
                              onChanged: (val) {
                                final convoId = activeThread['id'] as String? ?? 'conv-1';
                                ref.read(crmRepositoryProvider).sendTyping(convoId, val.isNotEmpty);
                              },
                              onSubmitted: (_) => _sendReply(),
                            ),
                          ),
                          const SizedBox(width: 12),
                          ElevatedButton(
                            key: const Key('send_reply_button'),
                            onPressed: _sendReply,
                            child: const Icon(Icons.send, size: 18),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
