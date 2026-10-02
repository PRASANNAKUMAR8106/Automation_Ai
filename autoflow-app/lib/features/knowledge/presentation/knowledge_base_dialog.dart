import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/app_theme.dart';
import '../data/knowledge_repository.dart';

class KnowledgeBaseDialog extends ConsumerStatefulWidget {
  const KnowledgeBaseDialog({super.key});

  static Future<void> show(BuildContext context) {
    return showDialog(
      context: context,
      builder: (context) => const KnowledgeBaseDialog(),
    );
  }

  @override
  ConsumerState<KnowledgeBaseDialog> createState() => _KnowledgeBaseDialogState();
}

class _KnowledgeBaseDialogState extends ConsumerState<KnowledgeBaseDialog> {
  final TextEditingController _searchController = TextEditingController();
  String _selectedCategory = 'ALL';
  String _searchQuery = '';

  final List<String> _categories = [
    'ALL',
    'FAQ',
    'PRODUCT_SPECS',
    'POLICY',
    'PROMOTIONS',
    'TROUBLESHOOTING',
    'GENERAL',
  ];

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Color _getCategoryColor(String category) {
    switch (category) {
      case 'FAQ':
        return Colors.blueAccent;
      case 'PRODUCT_SPECS':
        return Colors.tealAccent;
      case 'POLICY':
        return Colors.amberAccent;
      case 'PROMOTIONS':
        return Colors.purpleAccent;
      case 'TROUBLESHOOTING':
        return Colors.orangeAccent;
      default:
        return Colors.grey;
    }
  }

  void _showAddArticleDialog() {
    final titleController = TextEditingController();
    final contentController = TextEditingController();
    final tagsController = TextEditingController();
    String newCategory = 'FAQ';

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          backgroundColor: AppTheme.cardDark,
          title: const Row(
            children: [
              Icon(Icons.library_add, color: AppTheme.primaryLight, size: 20),
              SizedBox(width: 8),
              Text('Add Knowledge Article', style: TextStyle(color: Colors.white, fontSize: 18)),
            ],
          ),
          content: SizedBox(
            width: 500,
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Knowledge articles are chunked into 600-character segments and vectorized for RAG retrieval during customer conversations on WhatsApp & Telegram.',
                    style: TextStyle(color: AppTheme.textMuted, fontSize: 12),
                  ),
                  const SizedBox(height: 16),
                  TextField(
                    key: const Key('article_title_input'),
                    controller: titleController,
                    decoration: const InputDecoration(
                      labelText: 'Title / Subject',
                      hintText: 'e.g. Return Policy or Consultation Pricing',
                    ),
                  ),
                  const SizedBox(height: 12),
                  DropdownButtonFormField<String>(
                    initialValue: newCategory,
                    dropdownColor: AppTheme.surfaceDark,
                    decoration: const InputDecoration(labelText: 'Category'),
                    items: _categories.where((c) => c != 'ALL').map((c) {
                      return DropdownMenuItem(value: c, child: Text(c, style: const TextStyle(fontSize: 13)));
                    }).toList(),
                    onChanged: (val) {
                      if (val != null) {
                        setDialogState(() => newCategory = val);
                      }
                    },
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    key: const Key('article_tags_input'),
                    controller: tagsController,
                    decoration: const InputDecoration(
                      labelText: 'Tags (comma separated)',
                      hintText: 'e.g. pricing, refund, consultation',
                    ),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    key: const Key('article_content_input'),
                    controller: contentController,
                    maxLines: 5,
                    decoration: const InputDecoration(
                      labelText: 'Article Content / Answer',
                      hintText: 'Provide the exact information for the AI Co-Pilot to cite...',
                    ),
                  ),
                ],
              ),
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              key: const Key('save_article_button'),
              onPressed: () async {
                final title = titleController.text.trim();
                final content = contentController.text.trim();
                if (title.isEmpty || content.isEmpty) return;

                final tags = tagsController.text
                    .split(',')
                    .map((s) => s.trim())
                    .where((s) => s.isNotEmpty)
                    .toList();

                final navigator = Navigator.of(ctx);
                final messenger = ScaffoldMessenger.of(context);

                await ref.read(knowledgeRepositoryProvider).createArticle(
                      title: title,
                      content: content,
                      category: newCategory,
                      tags: tags,
                    );

                navigator.pop();
                ref.invalidate(knowledgeArticlesProvider);
                messenger.showSnackBar(
                  const SnackBar(
                    content: Text('Article added and embedded into Knowledge Base!'),
                    backgroundColor: Colors.green,
                  ),
                );
              },
              child: const Text('Save & Embed'),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final articlesAsync = ref.watch(knowledgeArticlesProvider(_searchQuery.isEmpty ? null : _searchQuery));

    return Dialog(
      backgroundColor: AppTheme.cardDark,
      insetPadding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 880, maxHeight: 720),
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
                          padding: const EdgeInsets.all(8),
                          decoration: BoxDecoration(
                            color: AppTheme.primaryLight.withValues(alpha: 0.15),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: const Icon(Icons.menu_book, color: AppTheme.primaryLight, size: 22),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text(
                                'Knowledge Base (RAG)',
                                style: TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold),
                              ),
                              Text(
                                'True vector embeddings (1536-d) & hybrid retrieval powering WhatsApp & Telegram Co-Pilot',
                                style: TextStyle(color: Colors.white.withValues(alpha: 0.6), fontSize: 12),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 12),
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      ElevatedButton.icon(
                        key: const Key('add_article_button'),
                        onPressed: _showAddArticleDialog,
                        icon: const Icon(Icons.add, size: 16),
                        label: const Text('New Article'),
                      ),
                      const SizedBox(width: 8),
                      IconButton(
                        onPressed: () => Navigator.of(context).pop(),
                        icon: const Icon(Icons.close, color: AppTheme.textMuted),
                      ),
                    ],
                  ),
                ],
              ),
              const SizedBox(height: 16),

              // Search & Filter Bar
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      key: const Key('knowledge_search_field'),
                      controller: _searchController,
                      decoration: InputDecoration(
                        hintText: 'Search knowledge base via hybrid semantic & lexical match...',
                        prefixIcon: const Icon(Icons.search, size: 18),
                        suffixIcon: _searchQuery.isNotEmpty
                            ? IconButton(
                                icon: const Icon(Icons.clear, size: 16),
                                onPressed: () {
                                  _searchController.clear();
                                  setState(() => _searchQuery = '');
                                },
                              )
                            : null,
                        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                      ),
                      onChanged: (val) {
                        setState(() => _searchQuery = val.trim());
                      },
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),

              // Category Pills
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(
                  children: _categories.map((category) {
                    final isSelected = _selectedCategory == category;
                    return Padding(
                      padding: const EdgeInsets.only(right: 8),
                      child: FilterChip(
                        selected: isSelected,
                        label: Text(category),
                        labelStyle: TextStyle(
                          color: isSelected ? Colors.white : AppTheme.textMuted,
                          fontSize: 11,
                          fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                        ),
                        backgroundColor: AppTheme.surfaceDark,
                        selectedColor: AppTheme.primary,
                        onSelected: (selected) {
                          setState(() {
                            _selectedCategory = category;
                          });
                        },
                      ),
                    );
                  }).toList(),
                ),
              ),
              const SizedBox(height: 16),

              // Articles List
              Expanded(
                child: articlesAsync.when(
                  loading: () => const Center(child: CircularProgressIndicator()),
                  error: (err, _) => Center(child: Text('Error loading articles: $err')),
                  data: (articles) {
                    final filtered = _selectedCategory == 'ALL'
                        ? articles
                        : articles.where((a) => a.category == _selectedCategory).toList();

                    if (filtered.isEmpty) {
                      return Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(Icons.search_off, size: 48, color: Colors.white.withValues(alpha: 0.3)),
                            const SizedBox(height: 8),
                            const Text('No articles found matching criteria.', style: TextStyle(color: AppTheme.textMuted)),
                          ],
                        ),
                      );
                    }

                    return ListView.separated(
                      itemCount: filtered.length,
                      separatorBuilder: (context, index) => const SizedBox(height: 10),
                      itemBuilder: (context, index) {
                        final article = filtered[index];
                        final catColor = _getCategoryColor(article.category);

                        return Container(
                          padding: const EdgeInsets.all(14),
                          decoration: BoxDecoration(
                            color: AppTheme.surfaceDark,
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(color: AppTheme.borderDark),
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                                    decoration: BoxDecoration(
                                      color: catColor.withValues(alpha: 0.15),
                                      borderRadius: BorderRadius.circular(6),
                                      border: Border.all(color: catColor.withValues(alpha: 0.4)),
                                    ),
                                    child: Text(
                                      article.category,
                                      style: TextStyle(color: catColor, fontSize: 10, fontWeight: FontWeight.bold),
                                    ),
                                  ),
                                  const SizedBox(width: 8),
                                  Expanded(
                                    child: Text(
                                      article.title,
                                      style: const TextStyle(color: Colors.white, fontSize: 14, fontWeight: FontWeight.w600),
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                  Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                    decoration: BoxDecoration(
                                      color: Colors.white.withValues(alpha: 0.05),
                                      borderRadius: BorderRadius.circular(4),
                                    ),
                                    child: Row(
                                      mainAxisSize: MainAxisSize.min,
                                      children: [
                                        const Icon(Icons.bolt, size: 12, color: Colors.amberAccent),
                                        const SizedBox(width: 4),
                                        Text(
                                          '${article.usageCount} citations',
                                          style: const TextStyle(color: AppTheme.textMuted, fontSize: 11),
                                        ),
                                      ],
                                    ),
                                  ),
                                  const SizedBox(width: 8),
                                  IconButton(
                                    icon: const Icon(Icons.delete_outline, size: 16, color: Colors.redAccent),
                                    onPressed: () async {
                                      await ref.read(knowledgeRepositoryProvider).deleteArticle(article.id);
                                      ref.invalidate(knowledgeArticlesProvider);
                                    },
                                    tooltip: 'Delete Article',
                                  ),
                                ],
                              ),
                              const SizedBox(height: 8),
                              Text(
                                article.content,
                                style: TextStyle(color: Colors.white.withValues(alpha: 0.8), fontSize: 13, height: 1.4),
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                              ),
                              if (article.tags.isNotEmpty) ...[
                                const SizedBox(height: 8),
                                Wrap(
                                  spacing: 6,
                                  runSpacing: 4,
                                  children: article.tags.map((tag) {
                                    return Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                      decoration: BoxDecoration(
                                        color: Colors.white.withValues(alpha: 0.06),
                                        borderRadius: BorderRadius.circular(4),
                                      ),
                                      child: Text(
                                        '#$tag',
                                        style: TextStyle(color: Colors.white.withValues(alpha: 0.6), fontSize: 10),
                                      ),
                                    );
                                  }).toList(),
                                ),
                              ],
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
      ),
    );
  }
}
