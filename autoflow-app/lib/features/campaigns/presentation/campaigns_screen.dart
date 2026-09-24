import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/app_theme.dart';
import '../data/campaign_repository.dart';

class CampaignsScreen extends ConsumerStatefulWidget {
  const CampaignsScreen({super.key});

  @override
  ConsumerState<CampaignsScreen> createState() => _CampaignsScreenState();
}

class _CampaignsScreenState extends ConsumerState<CampaignsScreen> {
  String _selectedStatusFilter = 'ALL';
  String _searchQuery = '';
  final TextEditingController _searchController = TextEditingController();

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  void _openCreateCampaignDialog() {
    showDialog(
      context: context,
      builder: (ctx) => const CreateCampaignDialog(),
    ).then((_) {
      ref.invalidate(campaignsProvider);
    });
  }

  @override
  Widget build(BuildContext context) {
    final campaignsAsync = ref.watch(campaignsProvider);

    return Scaffold(
      backgroundColor: Colors.transparent,
      body: campaignsAsync.when(
        data: (campaigns) {
          final filtered = campaigns.where((c) {
            final matchesStatus = _selectedStatusFilter == 'ALL' || c.status == _selectedStatusFilter;
            final matchesSearch = _searchQuery.isEmpty || c.name.toLowerCase().contains(_searchQuery.toLowerCase());
            return matchesStatus && matchesSearch;
          }).toList();

          final totalDelivered = campaigns.fold<int>(0, (sum, c) => sum + c.deliveredCount);
          final scheduledCount = campaigns.where((c) => c.status == 'SCHEDULED').length;
          final completed = campaigns.where((c) => c.status == 'COMPLETED').toList();
          final avgDeliveryRate = completed.isNotEmpty
              ? (completed.fold<double>(0.0, (sum, c) => sum + c.deliveryRate) / completed.length * 100.0).toStringAsFixed(1)
              : '0.0';

          return SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Top Header Row
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Scheduled Broadcast Campaigns',
                            style: Theme.of(context).textTheme.titleLarge?.copyWith(
                                  fontWeight: FontWeight.bold,
                                  fontSize: 24,
                                ),
                          ),
                          const SizedBox(height: 4),
                          const Text(
                            'Automated omnichannel outreach with audience segmentation & Meta 24-hour compliance',
                            style: TextStyle(color: AppTheme.textMuted, fontSize: 14),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 16),
                    ElevatedButton.icon(
                      key: const Key('new_campaign_button'),
                      onPressed: _openCreateCampaignDialog,
                      icon: const Icon(Icons.add_rounded, size: 18),
                      label: const Text('New Campaign'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppTheme.primary,
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 24),

                // Metrics Row
                Row(
                  children: [
                    Expanded(child: _buildMetricCard('Total Broadcasts', '${campaigns.length}', Icons.campaign_rounded, AppTheme.info)),
                    const SizedBox(width: 16),
                    Expanded(child: _buildMetricCard('Messages Delivered', '$totalDelivered', Icons.mark_email_read_rounded, AppTheme.success)),
                    const SizedBox(width: 16),
                    Expanded(child: _buildMetricCard('Avg Delivery Rate', '$avgDeliveryRate%', Icons.insights_rounded, Colors.purpleAccent)),
                    const SizedBox(width: 16),
                    Expanded(child: _buildMetricCard('Scheduled Outreaches', '$scheduledCount', Icons.schedule_rounded, AppTheme.warning)),
                  ],
                ),
                const SizedBox(height: 24),

                // Search & Filter Bar
                Row(
                  children: [
                    Expanded(
                      flex: 2,
                      child: TextField(
                        controller: _searchController,
                        decoration: InputDecoration(
                          hintText: 'Search campaigns by name...',
                          prefixIcon: const Icon(Icons.search, size: 18),
                          suffixIcon: _searchQuery.isNotEmpty
                              ? IconButton(
                                  icon: const Icon(Icons.clear, size: 16),
                                  onPressed: () {
                                    setState(() {
                                      _searchController.clear();
                                      _searchQuery = '';
                                    });
                                  },
                                )
                              : null,
                          contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                          fillColor: AppTheme.cardDark,
                        ),
                        onChanged: (val) => setState(() => _searchQuery = val.trim()),
                      ),
                    ),
                    const SizedBox(width: 16),
                    Wrap(
                      spacing: 8,
                      children: ['ALL', 'SCHEDULED', 'RUNNING', 'COMPLETED', 'CANCELLED'].map((status) {
                        final isSelected = _selectedStatusFilter == status;
                        return ChoiceChip(
                          label: Text(status),
                          selected: isSelected,
                          selectedColor: AppTheme.primary.withValues(alpha: 0.3),
                          backgroundColor: AppTheme.cardDark,
                          labelStyle: TextStyle(
                            color: isSelected ? AppTheme.primaryLight : AppTheme.textMuted,
                            fontSize: 12,
                            fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                          ),
                          onSelected: (_) => setState(() => _selectedStatusFilter = status),
                        );
                      }).toList(),
                    ),
                  ],
                ),
                const SizedBox(height: 20),

                // Campaign List
                if (filtered.isEmpty)
                  Center(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(vertical: 48),
                      child: Column(
                        children: [
                          Icon(Icons.campaign_outlined, size: 48, color: AppTheme.textMuted.withValues(alpha: 0.5)),
                          const SizedBox(height: 12),
                          const Text('No campaigns found matching filter criteria', style: TextStyle(color: AppTheme.textMuted)),
                        ],
                      ),
                    ),
                  )
                else
                  ListView.separated(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    itemCount: filtered.length,
                    separatorBuilder: (context, index) => const SizedBox(height: 12),
                    itemBuilder: (context, index) {
                      final camp = filtered[index];
                      return _buildCampaignCard(camp);
                    },
                  ),
              ],
            ),
          );
        },
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (err, stack) => Center(child: Text('Error loading campaigns: $err')),
      ),
    );
  }

  Widget _buildMetricCard(String title, String value, IconData icon, Color color) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppTheme.surfaceDark,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppTheme.borderDark),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.15),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(icon, color: color, size: 24),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  title,
                  style: const TextStyle(color: AppTheme.textMuted, fontSize: 13),
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 4),
                Text(
                  value,
                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 22, color: AppTheme.textLight),
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCampaignCard(CampaignModel camp) {
    Color channelColor;
    IconData channelIcon;
    if (camp.channel == 'INSTAGRAM') {
      channelColor = const Color(0xFFE1306C);
      channelIcon = Icons.camera_alt_outlined;
    } else if (camp.channel == 'WHATSAPP') {
      channelColor = const Color(0xFF25D366);
      channelIcon = Icons.chat_bubble_outline_rounded;
    } else {
      channelColor = const Color(0xFF0088CC);
      channelIcon = Icons.send_rounded;
    }

    Color statusColor;
    if (camp.status == 'COMPLETED') {
      statusColor = AppTheme.success;
    } else if (camp.status == 'RUNNING') {
      statusColor = AppTheme.warning;
    } else if (camp.status == 'SCHEDULED') {
      statusColor = AppTheme.info;
    } else if (camp.status == 'CANCELLED') {
      statusColor = AppTheme.textMuted;
    } else {
      statusColor = AppTheme.error;
    }

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: AppTheme.surfaceDark,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppTheme.borderDark),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header Row
          Row(
            children: [
              CircleAvatar(
                backgroundColor: channelColor.withValues(alpha: 0.15),
                radius: 16,
                child: Icon(channelIcon, color: channelColor, size: 16),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      camp.name,
                      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16, color: AppTheme.textLight),
                    ),
                    Text(
                      '${camp.channel} • Created on ${camp.createdAt != null ? "${camp.createdAt!.month}/${camp.createdAt!.day}" : "Recently"}',
                      style: const TextStyle(color: AppTheme.textMuted, fontSize: 12),
                    ),
                  ],
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: statusColor.withValues(alpha: 0.15),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: statusColor.withValues(alpha: 0.3)),
                ),
                child: Text(
                  camp.status,
                  style: TextStyle(color: statusColor, fontSize: 11, fontWeight: FontWeight.bold),
                ),
              ),
              if (camp.status == 'SCHEDULED') ...[
                const SizedBox(width: 8),
                OutlinedButton(
                  key: Key('cancel_button_${camp.id}'),
                  onPressed: () async {
                    await ref.read(campaignRepositoryProvider).cancelCampaign(camp.id);
                    ref.invalidate(campaignsProvider);
                  },
                  style: OutlinedButton.styleFrom(
                    foregroundColor: Colors.redAccent,
                    side: const BorderSide(color: Colors.redAccent),
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  ),
                  child: const Text('Cancel', style: TextStyle(fontSize: 12)),
                ),
              ],
            ],
          ),
          const SizedBox(height: 12),

          // Message Template Snippet
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: AppTheme.cardDark,
              borderRadius: BorderRadius.circular(10),
            ),
            child: Text(
              camp.messageTemplate,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 13, color: AppTheme.textLight),
            ),
          ),
          const SizedBox(height: 12),

          // Audience Filter Tags
          Wrap(
            spacing: 8,
            runSpacing: 4,
            children: [
              if (camp.targetLeadStatus != null)
                _buildTagChip('Status: ${camp.targetLeadStatus}', Icons.person_search_outlined),
              if (camp.minLeadScore > 0)
                _buildTagChip('Score ≥ ${camp.minLeadScore}', Icons.star_border_rounded),
              ...camp.targetTags.map((tag) => _buildTagChip('#$tag', Icons.tag_rounded)),
              if (camp.skipExpiredWindow)
                _buildTagChip('24h Window Gated', Icons.security_rounded, color: Colors.greenAccent),
            ],
          ),
          const SizedBox(height: 14),

          // Progress Bar & Delivery Counts
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Delivered: ${camp.deliveredCount} / ${camp.totalRecipients} (${(camp.deliveryRate * 100).toStringAsFixed(0)}%)',
                style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AppTheme.textMuted),
              ),
              if (camp.failedCount > 0)
                Text(
                  'Failed: ${camp.failedCount}',
                  style: const TextStyle(fontSize: 12, color: Colors.redAccent),
                ),
            ],
          ),
          const SizedBox(height: 6),
          ClipRRect(
            borderRadius: BorderRadius.circular(6),
            child: LinearProgressIndicator(
              value: camp.totalRecipients > 0 ? camp.progress : 0.0,
              backgroundColor: AppTheme.cardDark,
              color: camp.status == 'COMPLETED' ? AppTheme.success : AppTheme.primary,
              minHeight: 6,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTagChip(String label, IconData icon, {Color? color}) {
    final chipColor = color ?? AppTheme.textMuted;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: chipColor.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: chipColor.withValues(alpha: 0.2)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 12, color: chipColor),
          const SizedBox(width: 4),
          Text(label, style: TextStyle(color: chipColor, fontSize: 11)),
        ],
      ),
    );
  }
}

class CreateCampaignDialog extends ConsumerStatefulWidget {
  const CreateCampaignDialog({super.key});

  @override
  ConsumerState<CreateCampaignDialog> createState() => _CreateCampaignDialogState();
}

class _CreateCampaignDialogState extends ConsumerState<CreateCampaignDialog> {
  final TextEditingController _nameController = TextEditingController();
  final TextEditingController _templateController = TextEditingController();
  final TextEditingController _tagsController = TextEditingController();

  String _channel = 'INSTAGRAM';
  String? _targetLeadStatus;
  final int _minLeadScore = 0;
  bool _skipExpiredWindow = true;
  bool _sendImmediately = true;
  DateTime? _scheduledDate;

  int _matchingContacts = 42;
  int _eligibleContacts = 30;

  @override
  void initState() {
    super.initState();
    _fetchAudienceEstimate();
  }

  @override
  void dispose() {
    _nameController.dispose();
    _templateController.dispose();
    _tagsController.dispose();
    super.dispose();
  }

  Future<void> _fetchAudienceEstimate() async {
    final tags = _tagsController.text.trim().isNotEmpty
        ? _tagsController.text.trim().split(RegExp(r'[\s,]+')).where((e) => e.isNotEmpty).toList()
        : null;

    final estimate = await ref.read(campaignRepositoryProvider).estimateAudience(
          channel: _channel,
          targetTags: tags,
          targetLeadStatus: _targetLeadStatus,
          minLeadScore: _minLeadScore,
        );

    if (mounted) {
      setState(() {
        _matchingContacts = estimate.totalMatchingContacts;
        _eligibleContacts = estimate.eligibleWindowContacts;
      });
    }
  }

  void _insertToken(String token) {
    final text = _templateController.text;
    final selection = _templateController.selection;
    if (selection.start >= 0 && selection.end >= 0) {
      final newText = text.replaceRange(selection.start, selection.end, token);
      _templateController.text = newText;
      _templateController.selection = TextSelection.collapsed(offset: selection.start + token.length);
    } else {
      _templateController.text = '$text$token';
    }
  }

  Future<void> _submit() async {
    final name = _nameController.text.trim();
    final template = _templateController.text.trim();
    if (name.isEmpty || template.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter a campaign name and message template.')),
      );
      return;
    }

    final tags = _tagsController.text.trim().isNotEmpty
        ? _tagsController.text.trim().split(RegExp(r'[\s,]+')).where((e) => e.isNotEmpty).toList()
        : null;

    final scheduled = _sendImmediately ? null : (_scheduledDate ?? DateTime.now().add(const Duration(hours: 1)));

    await ref.read(campaignRepositoryProvider).createCampaign(
          name: name,
          channel: _channel,
          messageTemplate: template,
          targetTags: tags,
          targetLeadStatus: _targetLeadStatus,
          minLeadScore: _minLeadScore,
          skipExpiredWindow: _skipExpiredWindow,
          scheduledAt: scheduled,
        );

    if (mounted) {
      Navigator.of(context).pop();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Campaign "$name" scheduled successfully!'),
          backgroundColor: AppTheme.success,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      backgroundColor: AppTheme.surfaceDark,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Container(
        width: 640,
        padding: const EdgeInsets.all(24),
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              // Dialog Header
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text(
                    'Create Broadcast Campaign',
                    style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: AppTheme.textLight),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close, size: 20),
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                ],
              ),
              const SizedBox(height: 16),

              // Campaign Name
              const Text('Campaign Name', style: TextStyle(color: AppTheme.textMuted, fontSize: 13)),
              const SizedBox(height: 6),
              TextField(
                key: const Key('campaign_name_input'),
                controller: _nameController,
                decoration: const InputDecoration(hintText: 'e.g. Black Friday VIP Announcement'),
              ),
              const SizedBox(height: 16),

              // Channel Selector
              const Text('Outreach Channel', style: TextStyle(color: AppTheme.textMuted, fontSize: 13)),
              const SizedBox(height: 6),
              SegmentedButton<String>(
                key: const Key('channel_selector'),
                segments: const [
                  ButtonSegment(value: 'INSTAGRAM', label: Text('Instagram'), icon: Icon(Icons.camera_alt_outlined, size: 16)),
                  ButtonSegment(value: 'WHATSAPP', label: Text('WhatsApp'), icon: Icon(Icons.chat_bubble_outline, size: 16)),
                  ButtonSegment(value: 'TELEGRAM', label: Text('Telegram'), icon: Icon(Icons.send_rounded, size: 16)),
                ],
                selected: {_channel},
                onSelectionChanged: (set) {
                  setState(() => _channel = set.first);
                  _fetchAudienceEstimate();
                },
              ),
              const SizedBox(height: 16),

              // Audience Segmentation Filters
              const Text('Target Audience Filters', style: TextStyle(color: AppTheme.textMuted, fontSize: 13)),
              const SizedBox(height: 6),
              Row(
                children: [
                  Expanded(
                    child: DropdownButtonFormField<String?>(
                      key: const Key('lead_status_dropdown'),
                      isExpanded: true,
                      initialValue: _targetLeadStatus,
                      decoration: const InputDecoration(labelText: 'Lead Status'),
                      items: const [
                        DropdownMenuItem(value: null, child: Text('Any Lead Status')),
                        DropdownMenuItem(value: 'NEW', child: Text('NEW')),
                        DropdownMenuItem(value: 'LEAD', child: Text('LEAD')),
                        DropdownMenuItem(value: 'QUALIFIED', child: Text('QUALIFIED')),
                        DropdownMenuItem(value: 'CUSTOMER', child: Text('CUSTOMER')),
                      ],
                      onChanged: (val) {
                        setState(() => _targetLeadStatus = val);
                        _fetchAudienceEstimate();
                      },
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: TextField(
                      key: const Key('tags_input'),
                      controller: _tagsController,
                      decoration: const InputDecoration(labelText: 'Target Tags (comma separated)', hintText: 'vip, webinar'),
                      onChanged: (_) => _fetchAudienceEstimate(),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),

              // 24-Hour Policy Compliance Switch
              SwitchListTile(
                key: const Key('skip_window_switch'),
                value: _skipExpiredWindow,
                title: const Text('Skip Ineligible Contacts (Outside Meta 24h Window)', style: TextStyle(fontSize: 13, color: AppTheme.textLight)),
                subtitle: const Text('Guarantees 100% Meta policy compliance and avoids account flags', style: TextStyle(fontSize: 11, color: AppTheme.textMuted)),
                contentPadding: EdgeInsets.zero,
                activeThumbColor: AppTheme.primary,
                onChanged: (val) => setState(() => _skipExpiredWindow = val),
              ),
              const SizedBox(height: 8),

              // Live Audience Reach Preview Badge
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                decoration: BoxDecoration(
                  color: AppTheme.cardDark,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: AppTheme.borderDark),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.people_alt_rounded, size: 16, color: Colors.blueAccent),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'Estimated Reach: $_matchingContacts contacts (${_skipExpiredWindow ? "$_eligibleContacts eligible within 24h window" : "all matching contacts"})',
                        style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AppTheme.textLight),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),

              // Message Template Composer
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text('Message Template', style: TextStyle(color: AppTheme.textMuted, fontSize: 13)),
                  Flexible(
                    child: Wrap(
                      spacing: 6,
                      runSpacing: 4,
                      alignment: WrapAlignment.end,
                      children: [
                        ActionChip(
                          label: const Text('{{name}}', style: TextStyle(fontSize: 11)),
                          onPressed: () => _insertToken('{{name}}'),
                        ),
                        ActionChip(
                          label: const Text('{{username}}', style: TextStyle(fontSize: 11)),
                          onPressed: () => _insertToken('{{username}}'),
                        ),
                        ActionChip(
                          label: const Text('{{channel}}', style: TextStyle(fontSize: 11)),
                          onPressed: () => _insertToken('{{channel}}'),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 6),
              TextField(
                key: const Key('message_template_input'),
                controller: _templateController,
                maxLines: 4,
                decoration: const InputDecoration(
                  hintText: 'Hey {{name}}! We are thrilled to share an exclusive update with you...',
                ),
              ),
              const SizedBox(height: 16),

              // Schedule Timing
              const Text('Broadcast Timing', style: TextStyle(color: AppTheme.textMuted, fontSize: 13)),
              const SizedBox(height: 6),
              SegmentedButton<bool>(
                key: const Key('timing_selector'),
                segments: const [
                  ButtonSegment(
                    value: true,
                    label: Text('Send Immediately'),
                    icon: Icon(Icons.bolt_rounded, size: 16),
                  ),
                  ButtonSegment(
                    value: false,
                    label: Text('Schedule for Later'),
                    icon: Icon(Icons.schedule_rounded, size: 16),
                  ),
                ],
                selected: {_sendImmediately},
                onSelectionChanged: (set) => setState(() => _sendImmediately = set.first),
              ),
              const SizedBox(height: 20),

              // Action Buttons
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  TextButton(
                    onPressed: () => Navigator.of(context).pop(),
                    child: const Text('Cancel'),
                  ),
                  const SizedBox(width: 12),
                  ElevatedButton(
                    key: const Key('schedule_campaign_submit_button'),
                    onPressed: _submit,
                    child: Text(_sendImmediately ? 'Launch Broadcast' : 'Schedule Broadcast'),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
