import 'package:flutter/material.dart';
import '../../../core/theme/app_theme.dart';

class WorkflowListScreen extends StatefulWidget {
  const WorkflowListScreen({super.key});

  @override
  State<WorkflowListScreen> createState() => _WorkflowListScreenState();
}

class _WorkflowListScreenState extends State<WorkflowListScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;
  final TextEditingController _searchController = TextEditingController();

  final List<Map<String, dynamic>> _mockWorkflows = [
    {
      'id': 'wf-1',
      'name': 'Reel Comment GUIDE -> Deliver Free PDF',
      'channel': 'INSTAGRAM',
      'status': 'ACTIVE',
      'trigger': 'Comment contains: GUIDE, PDF',
      'actions': 'Public Reply + DM PDF Guide',
      'executions': 1420,
      'conversionRate': '28.4%',
      'lastRun': '10 mins ago',
    },
    {
      'id': 'wf-2',
      'name': 'Story Reply -> 20% Discount Coupon Delivery',
      'channel': 'INSTAGRAM',
      'status': 'ACTIVE',
      'trigger': 'Any Story Reply',
      'actions': 'Send Promo Code SAVE20',
      'executions': 382,
      'conversionRate': '14.2%',
      'lastRun': '2 hours ago',
    },
    {
      'id': 'wf-3',
      'name': 'WhatsApp New Inbound -> Lead Qualification AI',
      'channel': 'WHATSAPP',
      'status': 'ACTIVE',
      'trigger': 'Inbound Message: PRICE or DEMO',
      'actions': 'AI Smart Agent + Book Meeting Link',
      'executions': 89,
      'conversionRate': '41.5%',
      'lastRun': 'Yesterday',
    },
    {
      'id': 'wf-4',
      'name': 'Giveaway Comment Funnel -> Collect Email',
      'channel': 'INSTAGRAM',
      'status': 'DRAFT',
      'trigger': 'Comment: WIN',
      'actions': 'Ask Email -> Save to CRM -> Send Entry Ticket',
      'executions': 0,
      'conversionRate': '0.0%',
      'lastRun': 'Never',
    },
  ];

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.transparent,
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Top Row: Title + Action
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Automations & Workflows', style: Theme.of(context).textTheme.titleLarge),
                    const SizedBox(height: 4),
                    const Text('Manage, test, and monitor your social-media automations.', style: TextStyle(color: AppTheme.textMuted, fontSize: 14)),
                  ],
                ),
                ElevatedButton.icon(
                  onPressed: () {},
                  icon: const Icon(Icons.add, size: 18),
                  label: const Text('New Automation'),
                ),
              ],
            ),
            const SizedBox(height: 24),

            // Filter Tabs and Search Bar
            Row(
              children: [
                SizedBox(
                  width: 320,
                  child: TabBar(
                    controller: _tabController,
                    isScrollable: true,
                    tabAlignment: TabAlignment.start,
                    labelColor: AppTheme.primaryLight,
                    unselectedLabelColor: AppTheme.textMuted,
                    indicatorColor: AppTheme.primaryLight,
                    tabs: const [
                      Tab(text: 'All (4)'),
                      Tab(text: 'Active (3)'),
                      Tab(text: 'Drafts (1)'),
                    ],
                  ),
                ),
                const Spacer(),
                SizedBox(
                  width: 280,
                  height: 42,
                  child: TextField(
                    controller: _searchController,
                    decoration: const InputDecoration(
                      hintText: 'Search workflows...',
                      prefixIcon: Icon(Icons.search, size: 18),
                      contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 20),

            // Workflow Cards List
            Expanded(
              child: ListView.separated(
                itemCount: _mockWorkflows.length,
                separatorBuilder: (context, index) => const SizedBox(height: 12),
                itemBuilder: (context, index) {
                  final wf = _mockWorkflows[index];
                  final isActive = wf['status'] == 'ACTIVE';

                  return Card(
                    child: Padding(
                      padding: const EdgeInsets.all(20),
                      child: Row(
                        children: [
                          // Channel Icon
                          Container(
                            padding: const EdgeInsets.all(12),
                            decoration: BoxDecoration(
                              color: wf['channel'] == 'INSTAGRAM'
                                  ? const Color(0xFFE1306C).withValues(alpha: 0.15)
                                  : const Color(0xFF25D366).withValues(alpha: 0.15),
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: Icon(
                              wf['channel'] == 'INSTAGRAM' ? Icons.camera_alt_outlined : Icons.chat_bubble_outline,
                              color: wf['channel'] == 'INSTAGRAM' ? const Color(0xFFE1306C) : const Color(0xFF25D366),
                              size: 24,
                            ),
                          ),
                          const SizedBox(width: 16),

                          // Workflow Info
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  children: [
                                    Text(
                                      wf['name'] as String,
                                      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                                    ),
                                    const SizedBox(width: 10),
                                    Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                                      decoration: BoxDecoration(
                                        color: isActive ? AppTheme.success.withValues(alpha: 0.12) : AppTheme.borderDark,
                                        borderRadius: BorderRadius.circular(6),
                                      ),
                                      child: Text(
                                        wf['status'] as String,
                                        style: TextStyle(
                                          color: isActive ? AppTheme.success : AppTheme.textMuted,
                                          fontSize: 11,
                                          fontWeight: FontWeight.bold,
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 8),
                                Row(
                                  children: [
                                    const Icon(Icons.bolt, size: 14, color: AppTheme.warning),
                                    const SizedBox(width: 4),
                                    Text(wf['trigger'] as String, style: const TextStyle(color: AppTheme.textMuted, fontSize: 13)),
                                    const SizedBox(width: 16),
                                    const Icon(Icons.arrow_forward, size: 12, color: AppTheme.textMuted),
                                    const SizedBox(width: 8),
                                    Text(wf['actions'] as String, style: const TextStyle(color: AppTheme.textMuted, fontSize: 13)),
                                  ],
                                ),
                              ],
                            ),
                          ),

                          // Stats
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.end,
                            children: [
                              Text('${wf['executions']} runs', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
                              const SizedBox(height: 4),
                              Text('${wf['conversionRate']} conv.', style: const TextStyle(color: AppTheme.success, fontSize: 12)),
                            ],
                          ),
                          const SizedBox(width: 24),

                          // Status Switch Toggle
                          Switch(
                            value: isActive,
                            activeThumbColor: AppTheme.primaryLight,
                            onChanged: (val) {
                              setState(() {
                                wf['status'] = val ? 'ACTIVE' : 'PAUSED';
                              });
                            },
                          ),
                          const SizedBox(width: 8),

                          // Action Menu
                          PopupMenuButton<String>(
                            itemBuilder: (context) => [
                              const PopupMenuItem(value: 'edit', child: Text('Open Canvas')),
                              const PopupMenuItem(value: 'test', child: Text('Test Run')),
                              const PopupMenuItem(value: 'duplicate', child: Text('Duplicate')),
                              const PopupMenuDivider(),
                              const PopupMenuItem(value: 'delete', child: Text('Delete', style: TextStyle(color: AppTheme.error))),
                            ],
                          ),
                        ],
                      ),
                    ),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}
