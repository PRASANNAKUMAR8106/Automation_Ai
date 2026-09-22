import 'package:flutter/material.dart';

enum NodeCategory { trigger, condition, action }

enum NodeType {
  // Triggers
  triggerInstagramComment,
  triggerInstagramDm,
  triggerWhatsAppMessage,
  triggerStoryReply,
  triggerWebhook,

  // Conditions
  conditionKeywordMatch,
  conditionContactTag,
  conditionLeadStatus,
  conditionSubscriptionActive,

  // Actions
  actionPublicCommentReply,
  actionSendDm,
  actionSendMediaAsset,
  actionCollectEmail,
  actionCollectPhone,
  actionAddContactTag,
  actionGoogleSheetsSync,
  actionDelayWait,
}

class WorkflowNodeModel {
  final String id;
  final NodeType type;
  final String label;
  final NodeCategory category;
  final Map<String, dynamic> config;
  Offset position;

  WorkflowNodeModel({
    required this.id,
    required this.type,
    required this.label,
    required this.category,
    Map<String, dynamic>? config,
    required this.position,
  }) : config = config ?? {};

  Color get categoryColor {
    switch (category) {
      case NodeCategory.trigger:
        return const Color(0xFFF59E0B); // Amber for triggers
      case NodeCategory.condition:
        return const Color(0xFF3B82F6); // Blue for conditions
      case NodeCategory.action:
        return const Color(0xFF10B981); // Emerald for actions
    }
  }

  IconData get icon {
    switch (type) {
      case NodeType.triggerInstagramComment:
        return Icons.comment_outlined;
      case NodeType.triggerInstagramDm:
        return Icons.mail_outline;
      case NodeType.triggerWhatsAppMessage:
        return Icons.chat_bubble_outline;
      case NodeType.triggerStoryReply:
        return Icons.auto_stories_outlined;
      case NodeType.triggerWebhook:
        return Icons.webhook_outlined;
      case NodeType.conditionKeywordMatch:
        return Icons.search;
      case NodeType.conditionContactTag:
        return Icons.label_outline;
      case NodeType.conditionLeadStatus:
        return Icons.verified_user_outlined;
      case NodeType.conditionSubscriptionActive:
        return Icons.credit_card_outlined;
      case NodeType.actionPublicCommentReply:
        return Icons.reply;
      case NodeType.actionSendDm:
        return Icons.send_outlined;
      case NodeType.actionSendMediaAsset:
        return Icons.file_present_outlined;
      case NodeType.actionCollectEmail:
        return Icons.alternate_email;
      case NodeType.actionCollectPhone:
        return Icons.phone_outlined;
      case NodeType.actionAddContactTag:
        return Icons.bookmark_add_outlined;
      case NodeType.actionGoogleSheetsSync:
        return Icons.table_chart_outlined;
      case NodeType.actionDelayWait:
        return Icons.timer_outlined;
    }
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'type': type.name,
      'label': label,
      'category': category.name,
      'config': config,
      'position': {'x': position.dx, 'y': position.dy},
    };
  }

  factory WorkflowNodeModel.fromJson(Map<String, dynamic> json) {
    final pos = json['position'] as Map<String, dynamic>;
    return WorkflowNodeModel(
      id: json['id'] as String,
      type: NodeType.values.firstWhere((e) => e.name == json['type']),
      label: json['label'] as String,
      category: NodeCategory.values.firstWhere((e) => e.name == json['category']),
      config: Map<String, dynamic>.from(json['config'] as Map),
      position: Offset((pos['x'] as num).toDouble(), (pos['y'] as num).toDouble()),
    );
  }
}
