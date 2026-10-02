import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/app_theme.dart';
import '../../contacts_crm/data/crm_repository.dart';
import '../../contacts_crm/presentation/canned_response_dialog.dart';
import '../../contacts_crm/presentation/collision_warning_banner.dart';
import '../../contacts_crm/presentation/conversation_timeline_sheet.dart';
import '../../contacts_crm/presentation/macro_runner_dialog.dart';
import '../../contacts_crm/presentation/performance_telemetry_dialog.dart';
import '../../crm/data/agent_productivity_repository.dart';
import '../../knowledge/presentation/knowledge_base_dialog.dart';

class InboxScreen extends ConsumerStatefulWidget {
  const InboxScreen({super.key});

  @override
  ConsumerState<InboxScreen> createState() => _InboxScreenState();
}

class _InboxScreenState extends ConsumerState<InboxScreen> {
  int _selectedChatIndex = 0;
  final TextEditingController _replyController = TextEditingController();
  bool _useHumanAgentTag = false;
  bool _isInternalNoteMode = false;
  AiSuggestionModel? _aiSuggestion;
  bool _isLoadingAiSuggestion = false;

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
      'priority': 'NORMAL',
      'sentiment': 'POSITIVE',
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
      'priority': 'HIGH',
      'sentiment': 'NEUTRAL',
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
      'priority': 'NORMAL',
      'sentiment': 'NEUTRAL',
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
      'priority': 'NORMAL',
      'sentiment': 'NEUTRAL',
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

  Future<void> _fetchAiSuggestion() async {
    final activeThread = _threads[_selectedChatIndex];
    final convoId = activeThread['id'] as String? ?? 'conv-1';
    setState(() {
      _isLoadingAiSuggestion = true;
    });

    try {
      final suggestion = await ref.read(crmRepositoryProvider).getAiSuggestion(convoId);
      if (mounted) {
        setState(() {
          _aiSuggestion = suggestion;
          _isLoadingAiSuggestion = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _isLoadingAiSuggestion = false;
        });
      }
    }
  }

  void _sendReply() {
    final text = _replyController.text.trim();
    if (text.isEmpty) return;

    final activeThread = _threads[_selectedChatIndex];
    final currentConvoId = activeThread['id'] as String? ?? 'conv-1';

    if (_isInternalNoteMode) {
      ref.read(agentProductivityRepositoryProvider).postInternalNote(currentConvoId, text);
      setState(() {
        final messages = activeThread['messages'] as List;
        messages.add({
          'sender': 'internal_note',
          'text': text,
          'time': 'Just now',
        });
        _replyController.clear();
        _isInternalNoteMode = false;
      });
      return;
    }

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
      _aiSuggestion = null;
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
                            _aiSuggestion = null;
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
                            Wrap(
                              crossAxisAlignment: WrapCrossAlignment.center,
                              spacing: 8,
                              children: [
                                Text(
                                  activeThread['name'] as String,
                                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                                  overflow: TextOverflow.ellipsis,
                                ),
                                if (isResolved)
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
                            ),
                            Text(
                              activeThread['handle'] as String,
                              style: const TextStyle(color: AppTheme.textMuted, fontSize: 12),
                              overflow: TextOverflow.ellipsis,
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 8),
                      _buildComplianceBadge(activeThread),
                      const SizedBox(width: 4),
                      IconButton(
                        key: const Key('knowledge_base_button'),
                        visualDensity: VisualDensity.compact,
                        padding: const EdgeInsets.all(4),
                        constraints: const BoxConstraints(),
                        onPressed: () => KnowledgeBaseDialog.show(context),
                        icon: const Icon(Icons.menu_book_outlined, size: 20, color: AppTheme.primaryLight),
                        tooltip: 'Knowledge Base (RAG)',
                      ),
                      const SizedBox(width: 4),
                      IconButton(
                        key: const Key('performance_telemetry_button'),
                        visualDensity: VisualDensity.compact,
                        padding: const EdgeInsets.all(4),
                        constraints: const BoxConstraints(),
                        onPressed: () => showDialog(
                          context: context,
                          builder: (_) => const PerformanceTelemetryDialog(),
                        ),
                        icon: const Icon(Icons.insights_rounded, size: 20, color: Colors.cyanAccent),
                        tooltip: 'SLA & Performance Telemetry',
                      ),
                      const SizedBox(width: 4),
                      IconButton(
                        key: const Key('macro_runner_button'),
                        visualDensity: VisualDensity.compact,
                        padding: const EdgeInsets.all(4),
                        constraints: const BoxConstraints(),
                        onPressed: () => showDialog(
                          context: context,
                          builder: (_) => MacroRunnerDialog(
                            conversationId: activeThread['id'] as String? ?? 'conv-1',
                            onMacroApplied: () {
                              setState(() {});
                            },
                          ),
                        ),
                        icon: const Icon(Icons.bolt_rounded, size: 20, color: Colors.amberAccent),
                        tooltip: 'Run Macro',
                      ),
                      const SizedBox(width: 4),
                      IconButton(
                        key: const Key('conversation_timeline_button'),
                        visualDensity: VisualDensity.compact,
                        padding: const EdgeInsets.all(4),
                        constraints: const BoxConstraints(),
                        onPressed: () => showDialog(
                          context: context,
                          builder: (_) => ConversationTimelineSheet(
                            conversationId: activeThread['id'] as String? ?? 'conv-1',
                          ),
                        ),
                        icon: const Icon(Icons.history_rounded, size: 20, color: Colors.lightGreenAccent),
                        tooltip: 'Audit Timeline',
                      ),
                      const SizedBox(width: 4),
                      OutlinedButton.icon(
                        key: const Key('resolve_button'),
                        style: OutlinedButton.styleFrom(
                          visualDensity: VisualDensity.compact,
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                        ),
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

                // Collision Detection Banner
                CollisionWarningBanner(
                  activeViewers: ref.watch(conversationPresenceProvider(activeThread['id'] as String? ?? 'conv-1')).value ?? [],
                ),

                // Message Transcript
                Expanded(
                  child: ListView.builder(
                    padding: const EdgeInsets.all(20),
                    itemCount: messages.length,
                    itemBuilder: (context, idx) {
                      final msg = messages[idx] as Map<String, dynamic>;
                      final isInternal = msg['sender'] == 'internal_note';
                      if (isInternal) {
                        return Align(
                          alignment: Alignment.center,
                          child: Container(
                            margin: const EdgeInsets.symmetric(vertical: 6),
                            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                            constraints: const BoxConstraints(maxWidth: 500),
                            decoration: BoxDecoration(
                              color: Colors.amber.withValues(alpha: 0.12),
                              borderRadius: BorderRadius.circular(10),
                              border: Border.all(color: Colors.amber.withValues(alpha: 0.4)),
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                const Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    Icon(Icons.lock_rounded, size: 12, color: Colors.amber),
                                    SizedBox(width: 4),
                                    Text(
                                      'Private Team Note (Hidden from Customer)',
                                      style: TextStyle(color: Colors.amber, fontSize: 11, fontWeight: FontWeight.bold),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  msg['text'] as String? ?? '',
                                  style: const TextStyle(color: Colors.white, fontSize: 13),
                                ),
                              ],
                            ),
                          ),
                        );
                      }

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
                      // AI Co-Pilot & Channel Controls Toolbar
                      Padding(
                        padding: const EdgeInsets.only(bottom: 8),
                        child: Row(
                          children: [
                            ElevatedButton.icon(
                              key: const Key('ai_copilot_suggest_button'),
                              style: ElevatedButton.styleFrom(
                                backgroundColor: const Color(0xFF8B5CF6),
                                foregroundColor: Colors.white,
                                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                                textStyle: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold),
                              ),
                              onPressed: _isLoadingAiSuggestion ? null : _fetchAiSuggestion,
                              icon: _isLoadingAiSuggestion
                                  ? const SizedBox(
                                      width: 14,
                                      height: 14,
                                      child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                                    )
                                  : const Icon(Icons.auto_awesome, size: 14),
                              label: Text(_isLoadingAiSuggestion ? 'Synthesizing...' : '✨ AI Co-Pilot Suggest'),
                            ),
                            const SizedBox(width: 8),
                            OutlinedButton.icon(
                              key: const Key('canned_responses_button'),
                              style: OutlinedButton.styleFrom(
                                foregroundColor: Colors.orangeAccent,
                                side: const BorderSide(color: Colors.orangeAccent),
                                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                                textStyle: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold),
                              ),
                              onPressed: () => showDialog(
                                context: context,
                                builder: (_) => CannedResponseDialog(
                                  conversationId: activeThread['id'] as String? ?? 'conv-1',
                                  onInsert: (text) {
                                    setState(() {
                                      _replyController.text = text;
                                    });
                                  },
                                ),
                              ),
                              icon: const Icon(Icons.flash_on_rounded, size: 14),
                              label: const Text('Canned (#)'),
                            ),
                            const SizedBox(width: 8),
                            FilterChip(
                              key: const Key('internal_note_toggle'),
                              selected: _isInternalNoteMode,
                              selectedColor: Colors.amber.withValues(alpha: 0.25),
                              checkmarkColor: Colors.amber,
                              label: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Icon(
                                    _isInternalNoteMode ? Icons.lock_rounded : Icons.lock_open_rounded,
                                    size: 13,
                                    color: _isInternalNoteMode ? Colors.amber : AppTheme.textMuted,
                                  ),
                                  const SizedBox(width: 4),
                                  Text(
                                    'Internal Note',
                                    style: TextStyle(
                                      color: _isInternalNoteMode ? Colors.amber : AppTheme.textMuted,
                                      fontSize: 11,
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                ],
                              ),
                              onSelected: (selected) => setState(() => _isInternalNoteMode = selected),
                            ),
                            if (activeThread['channel'] == 'INSTAGRAM') ...[
                              const SizedBox(width: 16),
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
                          ],
                        ),
                      ),

                      if (_aiSuggestion != null)
                        Container(
                          key: const Key('ai_suggestion_card'),
                          margin: const EdgeInsets.only(bottom: 12),
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: const Color(0xFF1E1B4B).withValues(alpha: 0.6),
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(color: const Color(0xFF8B5CF6).withValues(alpha: 0.5)),
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  Row(
                                    children: [
                                      const Icon(Icons.auto_awesome, size: 16, color: Color(0xFFA78BFA)),
                                      const SizedBox(width: 6),
                                      const Text(
                                        'AI Co-Pilot Suggestion',
                                        style: TextStyle(color: Color(0xFFA78BFA), fontSize: 12, fontWeight: FontWeight.bold),
                                      ),
                                      const SizedBox(width: 8),
                                      Container(
                                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                        decoration: BoxDecoration(
                                          color: Colors.green.withValues(alpha: 0.2),
                                          borderRadius: BorderRadius.circular(4),
                                        ),
                                        child: Text(
                                          '${(_aiSuggestion!.confidenceScore * 100).toInt()}% Match',
                                          style: const TextStyle(color: Colors.greenAccent, fontSize: 10, fontWeight: FontWeight.bold),
                                        ),
                                      ),
                                    ],
                                  ),
                                  IconButton(
                                    icon: const Icon(Icons.close, size: 14, color: AppTheme.textMuted),
                                    padding: EdgeInsets.zero,
                                    constraints: const BoxConstraints(),
                                    onPressed: () => setState(() => _aiSuggestion = null),
                                  ),
                                ],
                              ),
                              if (_aiSuggestion!.requiresHumanHandoff) ...[
                                const SizedBox(height: 6),
                                Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                  decoration: BoxDecoration(
                                    color: Colors.amber.withValues(alpha: 0.15),
                                    borderRadius: BorderRadius.circular(6),
                                    border: Border.all(color: Colors.amber.withValues(alpha: 0.4)),
                                  ),
                                  child: Row(
                                    children: [
                                      const Icon(Icons.warning_amber_rounded, size: 14, color: Colors.amberAccent),
                                      const SizedBox(width: 6),
                                      Expanded(
                                        child: Text(
                                          'Human Review Recommended: ${_aiSuggestion!.humanHandoffReason ?? "Escalation signal detected."}',
                                          style: const TextStyle(color: Colors.amberAccent, fontSize: 11),
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                              const SizedBox(height: 6),
                              Text(
                                _aiSuggestion!.suggestedReply,
                                style: const TextStyle(color: Colors.white, fontSize: 13, height: 1.3),
                              ),
                              if (_aiSuggestion!.sourceArticleTitles.isNotEmpty) ...[
                                const SizedBox(height: 8),
                                Wrap(
                                  spacing: 6,
                                  runSpacing: 4,
                                  children: _aiSuggestion!.sourceArticleTitles.map((title) {
                                    return Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                      decoration: BoxDecoration(
                                        color: Colors.white.withValues(alpha: 0.08),
                                        borderRadius: BorderRadius.circular(4),
                                      ),
                                      child: Row(
                                        mainAxisSize: MainAxisSize.min,
                                        children: [
                                          const Icon(Icons.menu_book, size: 10, color: Colors.lightBlueAccent),
                                          const SizedBox(width: 4),
                                          Text(
                                            title,
                                            style: const TextStyle(color: Colors.lightBlueAccent, fontSize: 10),
                                          ),
                                        ],
                                      ),
                                    );
                                  }).toList(),
                                ),
                              ],
                              const SizedBox(height: 8),
                              Row(
                                mainAxisAlignment: MainAxisAlignment.end,
                                children: [
                                  TextButton.icon(
                                    key: const Key('insert_suggestion_button'),
                                    style: TextButton.styleFrom(
                                      foregroundColor: const Color(0xFFA78BFA),
                                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                                      textStyle: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold),
                                    ),
                                    onPressed: () {
                                      setState(() {
                                        _replyController.text = _aiSuggestion!.suggestedReply;
                                        _aiSuggestion = null;
                                      });
                                    },
                                    icon: const Icon(Icons.input_rounded, size: 14),
                                    label: const Text('Insert into Reply'),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      Row(
                        children: [
                          Expanded(
                            child: TextField(
                              controller: _replyController,
                              decoration: InputDecoration(
                                hintText: _isInternalNoteMode
                                    ? 'Add private team note / whisper (hidden from customer)...'
                                    : 'Type your message or response...',
                                contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                                fillColor: _isInternalNoteMode ? Colors.amber.withValues(alpha: 0.08) : null,
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
                            style: _isInternalNoteMode
                                ? ElevatedButton.styleFrom(backgroundColor: Colors.amber.shade700)
                                : null,
                            onPressed: _sendReply,
                            child: Icon(_isInternalNoteMode ? Icons.lock : Icons.send, size: 18),
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
