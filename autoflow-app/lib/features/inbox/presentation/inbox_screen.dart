import 'package:flutter/material.dart';
import '../../../core/theme/app_theme.dart';

class InboxScreen extends StatefulWidget {
  const InboxScreen({super.key});

  @override
  State<InboxScreen> createState() => _InboxScreenState();
}

class _InboxScreenState extends State<InboxScreen> {
  int _selectedChatIndex = 0;
  final TextEditingController _replyController = TextEditingController();

  final List<Map<String, dynamic>> _threads = [
    {
      'name': 'Sneha Kapoor',
      'handle': '@snehak_designs',
      'channel': 'INSTAGRAM',
      'lastMessage': 'Just downloaded the PDF, thank you so much!',
      'time': '3m ago',
      'unread': true,
      'messages': [
        {'sender': 'contact', 'text': 'GUIDE', 'time': '10:14 AM'},
        {'sender': 'bot', 'text': 'Hey Sneha! Here is the PDF you requested 🎁', 'time': '10:14 AM'},
        {'sender': 'contact', 'text': 'Just downloaded the PDF, thank you so much!', 'time': '10:16 AM'},
      ]
    },
    {
      'name': 'Vikram Rathore',
      'handle': '+91 98765 43210',
      'channel': 'WHATSAPP',
      'lastMessage': 'Can I book a 1-on-1 coaching call?',
      'time': '25m ago',
      'unread': false,
      'messages': [
        {'sender': 'contact', 'text': 'DEMO', 'time': '09:45 AM'},
        {'sender': 'bot', 'text': 'Welcome to AutoFlow! How can we help your business today?', 'time': '09:45 AM'},
        {'sender': 'contact', 'text': 'Can I book a 1-on-1 coaching call?', 'time': '09:48 AM'},
      ]
    },
    {
      'name': 'Ananya Roy',
      'handle': '@ananya_fitness',
      'channel': 'INSTAGRAM',
      'lastMessage': 'Does the discount code expire today?',
      'time': '1h ago',
      'unread': false,
      'messages': [
        {'sender': 'contact', 'text': 'Does the discount code expire today?', 'time': '08:30 AM'},
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

    setState(() {
      final messages = _threads[_selectedChatIndex]['messages'] as List;
      messages.add({
        'sender': 'agent',
        'text': text,
        'time': 'Just now',
      });
      _replyController.clear();
    });
  }

  @override
  Widget build(BuildContext context) {
    final activeThread = _threads[_selectedChatIndex];
    final messages = activeThread['messages'] as List;

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
                          subtitle: Text(
                            thread['lastMessage'] as String,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color: thread['unread'] ? AppTheme.textLight : AppTheme.textMuted,
                              fontSize: 12,
                            ),
                          ),
                          onTap: () => setState(() => _selectedChatIndex = index),
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
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(activeThread['name'] as String, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
                          Text(activeThread['handle'] as String, style: const TextStyle(color: AppTheme.textMuted, fontSize: 12)),
                        ],
                      ),
                      const Spacer(),
                      OutlinedButton.icon(
                        onPressed: () {},
                        icon: const Icon(Icons.check, size: 16),
                        label: const Text('Resolve'),
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
                  child: Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: _replyController,
                          decoration: const InputDecoration(
                            hintText: 'Type your message or response...',
                            contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                          ),
                          onSubmitted: (_) => _sendReply(),
                        ),
                      ),
                      const SizedBox(width: 12),
                      ElevatedButton(
                        onPressed: _sendReply,
                        child: const Icon(Icons.send, size: 18),
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
