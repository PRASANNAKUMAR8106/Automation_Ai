import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/theme/app_theme.dart';
import '../data/template_repository.dart';
import '../data/workflow_repository.dart';

class TemplateGalleryDialog extends ConsumerStatefulWidget {
  const TemplateGalleryDialog({super.key});

  static Future<void> show(BuildContext context) {
    return showDialog<void>(
      context: context,
      barrierDismissible: true,
      builder: (context) => const TemplateGalleryDialog(),
    );
  }

  @override
  ConsumerState<TemplateGalleryDialog> createState() => _TemplateGalleryDialogState();
}

class _TemplateGalleryDialogState extends ConsumerState<TemplateGalleryDialog> {
  String _selectedCategory = 'ALL';
  bool _instantiating = false;
  String? _instantiatingTemplateId;

  static const _categories = [
    'ALL',
    'LEAD_MAGNET',
    'ECOMMERCE',
    'SUPPORT',
    'WEBINAR',
  ];

  String _formatCategory(String cat) {
    switch (cat) {
      case 'LEAD_MAGNET':
        return 'Lead Magnet';
      case 'ECOMMERCE':
        return 'E-Commerce';
      case 'SUPPORT':
        return 'Support & AI';
      case 'WEBINAR':
        return 'Webinar';
      default:
        return 'All Templates';
    }
  }

  IconData _iconForCategory(String cat) {
    switch (cat.toUpperCase()) {
      case 'LEAD_MAGNET':
        return Icons.file_download_outlined;
      case 'ECOMMERCE':
        return Icons.shopping_bag_outlined;
      case 'SUPPORT':
        return Icons.support_agent_outlined;
      case 'WEBINAR':
        return Icons.video_camera_front_outlined;
      default:
        return Icons.auto_awesome_outlined;
    }
  }

  Future<void> _useTemplate(TemplateModel template) async {
    setState(() {
      _instantiating = true;
      _instantiatingTemplateId = template.id;
    });

    try {
      final repo = ref.read(templateRepositoryProvider);
      final newWf = await repo.instantiateTemplate(
        template.id,
        workflowName: '${template.name} (Cloned)',
        description: template.description,
      );

      if (!mounted) return;

      if (newWf != null) {
        ref.invalidate(workflowListProvider);
        Navigator.of(context).pop();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Template "${template.name}" cloned into a new workflow!'),
            backgroundColor: AppTheme.success,
          ),
        );
        context.push('/workflows/${newWf.id}/builder');
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Failed to instantiate template. Please try again.'),
            backgroundColor: AppTheme.error,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Error: $e'),
            backgroundColor: AppTheme.error,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() {
          _instantiating = false;
          _instantiatingTemplateId = null;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final filter = TemplateFilter(
      category: _selectedCategory == 'ALL' ? null : _selectedCategory,
    );
    final templatesAsync = ref.watch(templatesProvider(filter));

    return Dialog(
      backgroundColor: AppTheme.cardDark,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: AppTheme.borderDark),
      ),
      insetPadding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 880, maxHeight: 680),
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Header
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Expanded(
                    child: Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.all(10),
                          decoration: BoxDecoration(
                            color: AppTheme.primaryLight.withValues(alpha: 0.15),
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: const Icon(Icons.hub_outlined, color: AppTheme.primaryLight, size: 22),
                        ),
                        const SizedBox(width: 14),
                        const Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Workflow Templates Marketplace',
                                style: TextStyle(
                                  fontSize: 20,
                                  fontWeight: FontWeight.bold,
                                  color: AppTheme.textLight,
                                ),
                                overflow: TextOverflow.ellipsis,
                              ),
                              SizedBox(height: 2),
                              Text(
                                'One-click clone proven high-converting social automation funnels.',
                                style: TextStyle(fontSize: 13, color: AppTheme.textMuted),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 12),
                  IconButton(
                    icon: const Icon(Icons.close, color: AppTheme.textMuted),
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                ],
              ),
              const SizedBox(height: 20),

              // Category Filter Pills
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(
                  children: _categories.map((cat) {
                    final isSelected = _selectedCategory == cat;
                    return Padding(
                      padding: const EdgeInsets.only(right: 8),
                      child: ChoiceChip(
                        label: Text(_formatCategory(cat)),
                        selected: isSelected,
                        onSelected: (selected) {
                          if (selected) {
                            setState(() => _selectedCategory = cat);
                          }
                        },
                        selectedColor: AppTheme.primaryLight,
                        backgroundColor: AppTheme.surfaceDark,
                        labelStyle: TextStyle(
                          color: isSelected ? Colors.white : AppTheme.textMuted,
                          fontSize: 12,
                          fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                        ),
                      ),
                    );
                  }).toList(),
                ),
              ),
              const SizedBox(height: 20),

              // Templates Grid
              Expanded(
                child: templatesAsync.when(
                  loading: () => const Center(
                    child: CircularProgressIndicator(),
                  ),
                  error: (err, _) => Center(
                    child: Text(
                      'Failed to load templates: $err',
                      style: const TextStyle(color: AppTheme.error),
                    ),
                  ),
                  data: (templates) {
                    if (templates.isEmpty) {
                      return Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            const Icon(Icons.folder_open, size: 48, color: AppTheme.textMuted),
                            const SizedBox(height: 12),
                            Text(
                              'No templates found in ${_formatCategory(_selectedCategory)}',
                              style: const TextStyle(color: AppTheme.textMuted),
                            ),
                          ],
                        ),
                      );
                    }

                    return GridView.builder(
                      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                        crossAxisCount: 2,
                        crossAxisSpacing: 16,
                        mainAxisSpacing: 16,
                        childAspectRatio: 1.45,
                      ),
                      itemCount: templates.length,
                      itemBuilder: (context, index) {
                        final tpl = templates[index];
                        final isBusy = _instantiating && _instantiatingTemplateId == tpl.id;

                        return Card(
                          color: AppTheme.surfaceDark,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12),
                            side: BorderSide(
                              color: tpl.isFeatured
                                  ? AppTheme.primaryLight.withValues(alpha: 0.5)
                                  : AppTheme.borderDark,
                            ),
                          ),
                          child: Padding(
                            padding: const EdgeInsets.all(16),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                // Top row: Category badge + Featured chip
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                  children: [
                                    Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                                      decoration: BoxDecoration(
                                        color: AppTheme.primaryLight.withValues(alpha: 0.12),
                                        borderRadius: BorderRadius.circular(6),
                                      ),
                                      child: Row(
                                        mainAxisSize: MainAxisSize.min,
                                        children: [
                                          Icon(
                                            _iconForCategory(tpl.category),
                                            size: 13,
                                            color: AppTheme.primaryLight,
                                          ),
                                          const SizedBox(width: 4),
                                          Text(
                                            tpl.category,
                                            style: const TextStyle(
                                              color: AppTheme.primaryLight,
                                              fontSize: 10,
                                              fontWeight: FontWeight.bold,
                                            ),
                                          ),
                                        ],
                                      ),
                                    ),
                                    if (tpl.isFeatured)
                                      Container(
                                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                        decoration: BoxDecoration(
                                          color: AppTheme.warning.withValues(alpha: 0.15),
                                          borderRadius: BorderRadius.circular(6),
                                        ),
                                        child: const Row(
                                          mainAxisSize: MainAxisSize.min,
                                          children: [
                                            Icon(Icons.star, size: 11, color: AppTheme.warning),
                                            SizedBox(width: 3),
                                            Text(
                                              'FEATURED',
                                              style: TextStyle(
                                                color: AppTheme.warning,
                                                fontSize: 9,
                                                fontWeight: FontWeight.bold,
                                              ),
                                            ),
                                          ],
                                        ),
                                      ),
                                  ],
                                ),
                                const SizedBox(height: 10),

                                // Title
                                Text(
                                  tpl.name,
                                  style: const TextStyle(
                                    fontWeight: FontWeight.bold,
                                    fontSize: 15,
                                    color: AppTheme.textLight,
                                  ),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                                const SizedBox(height: 6),

                                // Description
                                Expanded(
                                  child: Text(
                                    tpl.description ?? '',
                                    style: const TextStyle(
                                      color: AppTheme.textMuted,
                                      fontSize: 12,
                                      height: 1.3,
                                    ),
                                    maxLines: 2,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ),

                                // Tags
                                if (tpl.tags.isNotEmpty)
                                  Padding(
                                    padding: const EdgeInsets.only(bottom: 10),
                                    child: Wrap(
                                      spacing: 4,
                                      runSpacing: 4,
                                      children: tpl.tags.take(3).map((tag) {
                                        return Container(
                                          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                          decoration: BoxDecoration(
                                            color: AppTheme.borderDark,
                                            borderRadius: BorderRadius.circular(4),
                                          ),
                                          child: Text(
                                            '#$tag',
                                            style: const TextStyle(color: AppTheme.textMuted, fontSize: 10),
                                          ),
                                        );
                                      }).toList(),
                                    ),
                                  ),

                                // CTA Button
                                SizedBox(
                                  width: double.infinity,
                                  child: ElevatedButton.icon(
                                    onPressed: _instantiating ? null : () => _useTemplate(tpl),
                                    style: ElevatedButton.styleFrom(
                                      padding: const EdgeInsets.symmetric(vertical: 10),
                                      backgroundColor: AppTheme.primaryLight,
                                      foregroundColor: Colors.white,
                                    ),
                                    icon: isBusy
                                        ? const SizedBox(
                                            width: 14,
                                            height: 14,
                                            child: CircularProgressIndicator(
                                              strokeWidth: 2,
                                              color: Colors.white,
                                            ),
                                          )
                                        : const Icon(Icons.content_copy, size: 14),
                                    label: Text(
                                      isBusy ? 'Cloning...' : 'Use Template',
                                      style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold),
                                    ),
                                  ),
                                ),
                              ],
                            ),
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
      ),
    );
  }
}
