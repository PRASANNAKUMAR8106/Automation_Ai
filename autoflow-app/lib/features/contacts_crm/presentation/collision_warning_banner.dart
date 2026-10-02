import 'package:flutter/material.dart';
import '../../crm/data/agent_productivity_repository.dart';

class CollisionWarningBanner extends StatelessWidget {
  final List<AgentPresenceModel> activeViewers;
  final String? currentUserId;

  const CollisionWarningBanner({
    super.key,
    required this.activeViewers,
    this.currentUserId,
  });

  @override
  Widget build(BuildContext context) {
    final otherViewers = activeViewers.where((v) {
      if (currentUserId == null || currentUserId!.isEmpty) return true;
      return v.userId != currentUserId;
    }).toList();

    if (otherViewers.isEmpty) {
      return const SizedBox.shrink();
    }

    final isTyping = otherViewers.any((v) => v.action.toUpperCase() == 'TYPING');
    final names = otherViewers.map((v) {
      if (v.userEmail.contains('@')) {
        return v.userEmail.split('@').first;
      }
      return v.userEmail;
    }).join(', ');

    final message = isTyping
        ? '⚠️ Collision Warning: $names is actively drafting a reply'
        : '⚠️ Agent Collision: $names is currently viewing this conversation';

    return Container(
      width: double.infinity,
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
      decoration: BoxDecoration(
        color: Colors.amber.withValues(alpha: 0.15),
        border: Border.all(color: Colors.amber.withValues(alpha: 0.5)),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          Icon(
            isTyping ? Icons.edit_note_rounded : Icons.visibility_rounded,
            color: Colors.amber,
            size: 20,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              message,
              style: const TextStyle(
                color: Colors.amber,
                fontSize: 12,
                fontWeight: FontWeight.w600,
              ),
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
  }
}
