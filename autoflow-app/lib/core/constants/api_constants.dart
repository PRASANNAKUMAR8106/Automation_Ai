class ApiConstants {
  ApiConstants._();

  // For Android Emulator use 10.0.2.2, for Web / iOS simulator / Desktop use localhost
  static const String defaultBaseUrl = 'http://localhost:8080';

  static String baseUrl = const String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: defaultBaseUrl,
  );

  // Auth Endpoints
  static const String register = '/api/v1/auth/register';
  static const String login = '/api/v1/auth/login';
  static const String refreshToken = '/api/v1/auth/refresh';
  static const String logout = '/api/v1/auth/logout';
  static const String me = '/api/v1/auth/me';
  static const String switchOrg = '/api/v1/auth/switch-org';

  // System
  static const String health = '/api/v1/health';

  // Workflows
  static const String workflows = '/api/v1/workflows';
  static String workflow(String id) => '/api/v1/workflows/$id';
  static String workflowVersions(String id) => '/api/v1/workflows/$id/versions';
  static String publishWorkflow(String id) => '/api/v1/workflows/$id/publish';
  static String workflowExecutions(String id) => '/api/v1/workflows/$id/executions';
  static String workflowExecutionRetry(String executionId) => '/api/v1/workflows/executions/$executionId/retry';

  // Templates
  static const String templates = '/api/v1/templates';
  static String template(String id) => '/api/v1/templates/$id';
  static String templateInstantiate(String id) => '/api/v1/templates/$id/instantiate';

  // CRM & Live Chat Inbox
  static const String crmContacts = '/api/v1/crm/contacts';
  static const String crmContactsExport = '/api/v1/crm/contacts/export';
  static String crmContact(String id) => '/api/v1/crm/contacts/$id';
  static String crmContactTags(String id) => '/api/v1/crm/contacts/$id/tags';
  static String crmContactTag(String id, String tag) => '/api/v1/crm/contacts/$id/tags/$tag';
  static const String crmConversations = '/api/v1/crm/conversations';
  static String crmConversationMessages(String id) => '/api/v1/crm/conversations/$id/messages';
  static String crmConversationStream(String id) => '/api/v1/crm/conversations/$id/stream';
  static const String crmInboxStream = '/api/v1/crm/stream/inbox';
  static String crmConversationWindowStatus(String id) => '/api/v1/crm/conversations/$id/window-status';
  static String crmConversationTyping(String id) => '/api/v1/crm/conversations/$id/typing';
  static String crmConversationResolve(String id) => '/api/v1/crm/conversations/$id/resolve';

  // Campaigns & Broadcasts
  static const String campaigns = '/api/v1/campaigns';
  static const String campaignEstimateAudience = '/api/v1/campaigns/estimate-audience';
  static String campaign(String id) => '/api/v1/campaigns/$id';
  static String campaignCancel(String id) => '/api/v1/campaigns/$id/cancel';

  // Analytics & Funnels
  static const String analyticsOverview = '/api/v1/analytics/overview';
  static String analyticsWorkflowFunnel(String workflowId) => '/api/v1/analytics/funnels/$workflowId';
  static const String analyticsFunnelsOverview = '/api/v1/analytics/funnels/overview';

  // AI Media Generation
  static const String mediaAiGenerate = '/api/v1/media/ai/generate';

  // Webhook DLQ
  static const String webhooksDlq = '/api/v1/webhooks/dlq';
  static String webhookDlqReplay(String id) => '/api/v1/webhooks/dlq/$id/replay';
  static String webhookDlqDiscard(String id) => '/api/v1/webhooks/dlq/$id/discard';

  // Influencer / Affiliate Portal
  static const String influencerPortalStats = '/api/v1/influencer/portal/stats';

  // Billing
  static const String billingSubscription = '/api/v1/billing/subscription';
  static const String billingPlans = '/api/v1/billing/plans';
  static const String billingCreateOrder = '/api/v1/billing/create-order';

  // Channels
  static const String channelAccounts = '/api/v1/channels/accounts';
  static const String channelsConnected = '/api/v1/channels/connected';
  static String channelDisconnect(String id) => '/api/v1/channels/connected/$id';
  static String channelOAuth(String channel) => '/api/v1/channels/oauth/initiate/$channel';
  static String channelOAuthConnect(String channel) => '/api/v1/channels/oauth/connect/$channel';

  // Settings & API Keys
  static const String apiKeys = '/api/v1/settings/api-keys';
  static String apiKeyRotate(String id) => '/api/v1/settings/api-keys/$id/rotate';
  static String apiKeyRevoke(String id) => '/api/v1/settings/api-keys/$id';

  // Timeouts
  static const Duration connectTimeout = Duration(seconds: 15);
  static const Duration receiveTimeout = Duration(seconds: 15);
}
