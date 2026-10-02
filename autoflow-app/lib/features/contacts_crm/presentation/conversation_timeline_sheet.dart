import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../../core/theme/app_theme.dart';
import '../../crm/data/agent_productivity_repository.dart';

class ConversationTimelineSheet extends ConsumerWidget {
  final String conversationId;

  const ConversationTimelineSheet({
    super.key,
    required this.conversationId,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final timelineAsync = ref.watch(conversationTimelineProvider(conversationId));

    return Dialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      backgroundColor: AppTheme.surfaceDark,
      child: Container(
        width: 620,
        height: 560,
        padding: const EdgeInsets.all(22),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: Colors.cyan.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Icon(Icons.history_rounded, color: Colors.cyan, size: 22),
                ),
                const SizedBox(width: 12),
                const Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Unified Conversation Audit Timeline',
                        style: TextStyle(color: Colors.white, fontSize: 17, fontWeight: FontWeight.bold),
                      ),
                      Text(
                        'Chronological feed of messages, internal notes, SLA alerts, AI actions, and CSAT',
                        style: TextStyle(color: AppTheme.textMuted, fontSize: 12),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  onPressed: () => Navigator.of(context).pop(),
                  icon: const Icon(Icons.close, color: AppTheme.textMuted),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Expanded(
              child: timelineAsync.when(
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (err, _) => Center(child: Text('Error loading timeline: $err', style: const TextStyle(color: Colors.redAccent))),
                data: (events) {
                  if (events.isEmpty) {
                    return const Center(
                      child: Text('No timeline activity recorded yet', style: TextStyle(color: AppTheme.textMuted)),
                    );
                  }

                  final dateFormat = DateFormat('MMM d, h:mm a');

                  return ListView.separated(
                    itemCount: events.length,
                    separatorBuilder: (context, index) => const SizedBox(height: 10),
                    itemBuilder: (context, index) {
                      final event = events[index];
                      return Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: AppTheme.cardDark,
                          borderRadius: BorderRadius.circular(10),
                          border: Border.all(color: _getCategoryColor(event.category).withValues(alpha: 0.3)),
                        ),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Container(
                              padding: const EdgeInsets.all(7),
                              decoration: BoxDecoration(
                                color: _getCategoryColor(event.category).withValues(alpha: 0.15),
                                shape: BoxShape.circle,
                              ),
                              child: Icon(_getCategoryIcon(event.category), color: _getCategoryColor(event.category), size: 16),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Row(
                                    children: [
                                      Container(
                                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                        decoration: BoxDecoration(
                                          color: _getCategoryColor(event.category).withValues(alpha: 0.2),
                                          borderRadius: BorderRadius.circular(4),
                                        ),
                                        child: Text(
                                          event.category,
                                          style: TextStyle(color: _getCategoryColor(event.category), fontSize: 10, fontWeight: FontWeight.bold),
                                        ),
                                      ),
                                      const SizedBox(width: 8),
                                      Expanded(
                                        child: Text(
                                          '${event.actor} • ${event.eventType}',
                                          style: const TextStyle(color: AppTheme.textMuted, fontSize: 11),
                                          overflow: TextOverflow.ellipsis,
                                        ),
                                      ),
                                      Text(
                                        dateFormat.format(event.timestamp),
                                        style: const TextStyle(color: AppTheme.textMuted, fontSize: 11),
                                      ),
                                    ],
                                  ),
                                  const SizedBox(height: 6),
                                  Text(
                                    event.summary,
                                    style: const TextStyle(color: Colors.white, fontSize: 13),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      );
                    },
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }

  Color _getCategoryColor(String category) {
    switch (category.toUpperCase()) {
      case 'MESSAGE':
        return Colors.blueAccent;
      case 'INTERNAL_NOTE':
        return Colors.amber;
      case 'SLA_EVENT':
        return Colors.redAccent;
      case 'CSAT':
        return Colors.purpleAccent;
      case 'LEAD_SCORE':
        return Colors.greenAccent;
      default:
        return Colors.cyan;
    }
  }

  IconData _getCategoryIcon(String category) {
    switch (category.toUpperCase()) {
      case 'MESSAGE':
        return Icons.chat_bubble_outline_rounded;
      case 'INTERNAL_NOTE':
        return Icons.lock_outline_rounded;
      case 'SLA_EVENT':
        return Icons.timer_outlined;
      case 'CSAT':
        return Icons.star_rounded;
      case 'LEAD_SCORE':
        return Icons.trending_up_rounded;
      default:
        return Icons.info_outline_rounded;
    }
  }
}
