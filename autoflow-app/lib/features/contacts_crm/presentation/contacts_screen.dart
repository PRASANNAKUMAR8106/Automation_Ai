import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/app_theme.dart';
import '../data/crm_repository.dart';

class ContactsScreen extends ConsumerStatefulWidget {
  const ContactsScreen({super.key});

  @override
  ConsumerState<ContactsScreen> createState() => _ContactsScreenState();
}

class _ContactsScreenState extends ConsumerState<ContactsScreen> {
  bool _isExporting = false;

  Future<void> _handleExport() async {
    setState(() => _isExporting = true);
    try {
      final csv = await ref.read(crmRepositoryProvider).exportContactsCsv();
      if (mounted) {
        final lineCount = csv.split('\n').where((l) => l.trim().isNotEmpty).length;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Exported $lineCount contacts successfully to CSV'),
            backgroundColor: AppTheme.success,
            duration: const Duration(seconds: 3),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Failed to export contacts CSV'),
            backgroundColor: AppTheme.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isExporting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final contactsAsync = ref.watch(crmContactsProvider);
    final contacts = contactsAsync.value ?? ContactModel.defaultContacts;

    return Scaffold(
      backgroundColor: Colors.transparent,
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Top Row
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Contacts & CRM Directory', style: Theme.of(context).textTheme.titleLarge),
                    const SizedBox(height: 4),
                    const Text('Manage audience leads, captured emails, and interaction tags.', style: TextStyle(color: AppTheme.textMuted, fontSize: 14)),
                  ],
                ),
                OutlinedButton.icon(
                  onPressed: _isExporting ? null : _handleExport,
                  icon: _isExporting
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.download, size: 18),
                  label: Text(_isExporting ? 'Exporting...' : 'Export CSV'),
                ),
              ],
            ),
            const SizedBox(height: 24),

            // Contacts Table Card
            Expanded(
              child: Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: SingleChildScrollView(
                      child: DataTable(
                        headingRowColor: WidgetStateProperty.all(AppTheme.surfaceDark),
                        columns: const [
                          DataColumn(label: Text('Contact', style: TextStyle(fontWeight: FontWeight.bold))),
                          DataColumn(label: Text('Channel', style: TextStyle(fontWeight: FontWeight.bold))),
                          DataColumn(label: Text('Email', style: TextStyle(fontWeight: FontWeight.bold))),
                          DataColumn(label: Text('Lead Status', style: TextStyle(fontWeight: FontWeight.bold))),
                          DataColumn(label: Text('Tags', style: TextStyle(fontWeight: FontWeight.bold))),
                          DataColumn(label: Text('Last Active', style: TextStyle(fontWeight: FontWeight.bold))),
                        ],
                        rows: contacts.map((c) {
                          Color statusColor;
                          switch (c.leadStatus) {
                            case 'CUSTOMER':
                              statusColor = AppTheme.success;
                              break;
                            case 'QUALIFIED':
                            case 'HOT_LEAD':
                              statusColor = AppTheme.primaryLight;
                              break;
                            case 'LEAD':
                              statusColor = AppTheme.warning;
                              break;
                            default:
                              statusColor = AppTheme.textMuted;
                          }

                          final displayName = c.fullName ?? c.username ?? 'Contact';
                          final displayInitial = displayName.isNotEmpty ? displayName[0] : 'C';

                          return DataRow(
                            cells: [
                              DataCell(
                                Row(
                                  children: [
                                    CircleAvatar(
                                      radius: 14,
                                      backgroundColor: AppTheme.primaryDark,
                                      child: Text(
                                        displayInitial,
                                        style: const TextStyle(fontSize: 12, color: Colors.white),
                                      ),
                                    ),
                                    const SizedBox(width: 10),
                                    Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      mainAxisAlignment: MainAxisAlignment.center,
                                      children: [
                                        Text(displayName, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                                        Text(c.username ?? c.externalId, style: const TextStyle(color: AppTheme.textMuted, fontSize: 11)),
                                      ],
                                    ),
                                  ],
                                ),
                              ),
                              DataCell(
                                Text(c.channel, style: const TextStyle(fontSize: 12)),
                              ),
                              DataCell(
                                Text(c.email ?? '—', style: const TextStyle(fontSize: 13)),
                              ),
                              DataCell(
                                Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                  decoration: BoxDecoration(
                                    color: statusColor.withValues(alpha: 0.12),
                                    borderRadius: BorderRadius.circular(6),
                                  ),
                                  child: Text(
                                    c.leadStatus,
                                    style: TextStyle(color: statusColor, fontWeight: FontWeight.bold, fontSize: 11),
                                  ),
                                ),
                              ),
                              DataCell(
                                Wrap(
                                  spacing: 4,
                                  children: c.tags.map((t) {
                                    return Chip(
                                      label: Text(t, style: const TextStyle(fontSize: 10)),
                                      padding: EdgeInsets.zero,
                                      materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                                      backgroundColor: AppTheme.surfaceDark,
                                    );
                                  }).toList(),
                                ),
                              ),
                              DataCell(
                                Text(c.lastInteraction, style: const TextStyle(color: AppTheme.textMuted, fontSize: 12)),
                              ),
                            ],
                          );
                        }).toList(),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
