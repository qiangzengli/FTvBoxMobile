import 'package:fbox/app/fbox_store.dart';
import 'package:fbox/domain/source_models.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';

class SubscriptionPage extends StatefulWidget {
  const SubscriptionPage({super.key});

  @override
  State<SubscriptionPage> createState() => _SubscriptionPageState();
}

class _SubscriptionPageState extends State<SubscriptionPage> {
  final List<String> _fallbackSubscriptions = [];
  bool _busy = false;

  Future<void> _add() async {
    final value = await _editDialog(title: '添加订阅');
    if (value == null || value.isEmpty || !mounted) return;
    final store = FBoxScope.maybeOf(context);
    if (store == null) {
      setState(() => _fallbackSubscriptions.add(value));
      return;
    }
    await _run(() => store.addSubscription(name: value, url: value));
  }

  Future<String?> _editDialog({
    required String title,
    String initialValue = '',
  }) async {
    var fieldValue = initialValue;
    return showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        scrollable: true,
        title: Text(title),
        content: TextFormField(
          initialValue: initialValue,
          onChanged: (value) => fieldValue = value,
          autofocus: true,
          decoration: const InputDecoration(
            labelText: '订阅地址',
            hintText: 'https://example.com/config.json',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('取消'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, fieldValue.trim()),
            child: const Text('确定'),
          ),
        ],
      ),
    );
  }

  Future<void> _handleAction(String action, int index) async {
    final store = FBoxScope.maybeOf(context);
    if (store == null) {
      await _handleFallbackAction(action, index);
      return;
    }
    final subscription = store.subscriptions[index];
    switch (action) {
      case 'refresh':
        await _run(() => store.refreshSubscription(subscription.id));
        if (mounted) _showMessage('订阅已刷新');
      case 'edit':
        final value = await _editDialog(
          title: '编辑订阅',
          initialValue: subscription.url,
        );
        if (value != null && value.isNotEmpty && mounted) {
          await _run(
            () => store.updateSubscription(
              subscription.id,
              name: value,
              url: value,
            ),
          );
        }
      case 'lines':
        await _chooseLine(store, subscription);
      case 'delete':
        if (await _confirmDelete(subscription.name) && mounted) {
          await store.removeSubscription(subscription.id);
        }
    }
  }

  Future<void> _chooseLine(
    FBoxStore store,
    FBoxSubscription subscription,
  ) async {
    await _run(() async {
      final lines = await store.subscriptionLines(subscription.id);
      if (!mounted) return;
      if (lines.isEmpty) {
        _showMessage('该地址不是多线路配置集合');
        return;
      }
      final selected = await showModalBottomSheet<String>(
        context: context,
        showDragHandle: true,
        builder: (context) => SafeArea(
          child: ListView(
            shrinkWrap: true,
            children: [
              const ListTile(
                title: Text(
                  '切换配置线路',
                  style: TextStyle(fontWeight: FontWeight.w700),
                ),
              ),
              for (final line in lines)
                ListTile(
                  title: Text(line.name),
                  subtitle: Text(
                    line.url,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  trailing: line.url == subscription.selectedUrl
                      ? const Icon(Icons.check_rounded)
                      : null,
                  onTap: () => Navigator.pop(context, line.url),
                ),
            ],
          ),
        ),
      );
      if (selected != null) {
        await store.switchSubscriptionLine(subscription.id, selected);
        if (mounted) _showMessage('配置线路已切换');
      }
    });
  }

  Future<void> _handleFallbackAction(String action, int index) async {
    switch (action) {
      case 'refresh':
        _showMessage('订阅已刷新');
      case 'edit':
        final value = await _editDialog(
          title: '编辑订阅',
          initialValue: _fallbackSubscriptions[index],
        );
        if (value != null && value.isNotEmpty && mounted) {
          setState(() => _fallbackSubscriptions[index] = value);
        }
      case 'delete':
        if (await _confirmDelete(_fallbackSubscriptions[index]) && mounted) {
          setState(() => _fallbackSubscriptions.removeAt(index));
        }
    }
  }

  Future<bool> _confirmDelete(String name) async =>
      await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('删除订阅'),
          content: Text('确定删除“$name”吗？'),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('取消'),
            ),
            TextButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('删除'),
            ),
          ],
        ),
      ) ??
      false;

  Future<void> _pickLocalConfig() async {
    final store = FBoxScope.maybeOf(context);
    if (store == null) return;
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: const ['json', 'txt'],
    );
    final filePath = result?.files.single.path;
    if (filePath == null || !mounted) return;
    await _run(
      () => store.addLocalSubscription(
        name: result!.files.single.name,
        filePath: filePath,
      ),
    );
  }

  Future<void> _run(Future<dynamic> Function() action) async {
    setState(() => _busy = true);
    try {
      await action();
    } catch (error) {
      if (mounted) _showMessage('$error');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final store = FBoxScope.maybeOf(context);
    final subscriptions = store?.subscriptions;
    final itemCount = subscriptions?.length ?? _fallbackSubscriptions.length;
    return Scaffold(
      appBar: AppBar(
        title: const Text('订阅管理'),
        actions: [
          if (store != null)
            IconButton(
              tooltip: '导入本地配置',
              onPressed: _busy ? null : _pickLocalConfig,
              icon: const Icon(Icons.folder_open_outlined),
            ),
          IconButton(
            onPressed: _busy ? null : _add,
            icon: const Icon(Icons.add),
          ),
        ],
      ),
      body: Stack(
        children: [
          if (itemCount == 0)
            const Center(child: Text('暂无订阅，点击下方按钮添加'))
          else
            ListView.separated(
              padding: const EdgeInsets.fromLTRB(14, 10, 14, 24),
              itemCount: itemCount,
              separatorBuilder: (_, _) => const SizedBox(height: 10),
              itemBuilder: (context, index) {
                final subscription = subscriptions?[index];
                final title =
                    subscription?.name ?? _fallbackSubscriptions[index];
                final subtitle = subscription == null
                    ? '已启用 · 刚刚更新'
                    : _subtitle(subscription);
                return Material(
                  color: Theme.of(context).cardColor,
                  borderRadius: BorderRadius.circular(12),
                  child: ListTile(
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 8,
                    ),
                    leading: Container(
                      width: 42,
                      height: 42,
                      decoration: BoxDecoration(
                        color: Theme.of(
                          context,
                        ).colorScheme.primary.withValues(alpha: 0.12),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Icon(
                        subscription?.lastError.isNotEmpty == true
                            ? Icons.error_outline
                            : Icons.storage_rounded,
                        color: subscription?.lastError.isNotEmpty == true
                            ? Theme.of(context).colorScheme.error
                            : Theme.of(context).colorScheme.primary,
                      ),
                    ),
                    title: Text(title),
                    subtitle: Padding(
                      padding: const EdgeInsets.only(top: 5),
                      child: Text(
                        subtitle,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    trailing: PopupMenuButton<String>(
                      key: Key('subscription-menu-$index'),
                      onSelected: (value) => _handleAction(value, index),
                      itemBuilder: (_) => const [
                        PopupMenuItem(value: 'refresh', child: Text('刷新')),
                        PopupMenuItem(value: 'edit', child: Text('编辑')),
                        PopupMenuItem(value: 'lines', child: Text('切换线路')),
                        PopupMenuItem(value: 'delete', child: Text('删除')),
                      ],
                    ),
                  ),
                );
              },
            ),
          if (_busy)
            const Positioned.fill(
              child: ColoredBox(
                color: Color(0x22000000),
                child: Center(child: CircularProgressIndicator()),
              ),
            ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _busy ? null : _add,
        icon: const Icon(Icons.add),
        label: const Text('添加订阅'),
      ),
    );
  }

  static String _subtitle(FBoxSubscription subscription) {
    if (subscription.lastError.isNotEmpty) return subscription.lastError;
    final updated = subscription.lastUpdated;
    return '${subscription.url}\n${updated == null ? '尚未更新' : '更新于 ${updated.toLocal()}'}';
  }
}
