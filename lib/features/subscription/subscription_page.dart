import 'package:flutter/material.dart';

class SubscriptionPage extends StatefulWidget {
  const SubscriptionPage({super.key});

  @override
  State<SubscriptionPage> createState() => _SubscriptionPageState();
}

class _SubscriptionPageState extends State<SubscriptionPage> {
  final List<String> _subscriptions = ['FBox 演示订阅'];

  Future<void> _add() async {
    final value = await _editDialog(title: '添加订阅');
    if (value == null || value.isEmpty || !mounted) return;
    setState(() => _subscriptions.add(value));
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
    switch (action) {
      case 'refresh':
        _showMessage('订阅已刷新');
      case 'edit':
        final value = await _editDialog(
          title: '编辑订阅',
          initialValue: _subscriptions[index],
        );
        if (value != null && value.isNotEmpty && mounted) {
          setState(() => _subscriptions[index] = value);
        }
      case 'delete':
        final confirmed = await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            title: const Text('删除订阅'),
            content: Text('确定删除“${_subscriptions[index]}”吗？'),
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
        );
        if (confirmed == true && mounted) {
          setState(() => _subscriptions.removeAt(index));
        }
    }
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('订阅管理'),
        actions: [IconButton(onPressed: _add, icon: const Icon(Icons.add))],
      ),
      body: _subscriptions.isEmpty
          ? const Center(child: Text('暂无订阅，点击下方按钮添加'))
          : ListView.separated(
              padding: const EdgeInsets.fromLTRB(14, 10, 14, 24),
              itemCount: _subscriptions.length,
              separatorBuilder: (_, _) => const SizedBox(height: 10),
              itemBuilder: (context, index) => Material(
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
                      color: Theme.of(context).colorScheme.primary
                          .withValues(alpha: 0.12),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Icon(
                      Icons.storage_rounded,
                      color: Theme.of(context).colorScheme.primary,
                    ),
                  ),
                  title: Text(_subscriptions[index]),
                  subtitle: const Padding(
                    padding: EdgeInsets.only(top: 5),
                    child: Text('已启用 · 刚刚更新'),
                  ),
                  trailing: PopupMenuButton<String>(
                    key: Key('subscription-menu-$index'),
                    onSelected: (value) => _handleAction(value, index),
                    itemBuilder: (_) => const [
                      PopupMenuItem(value: 'refresh', child: Text('刷新')),
                      PopupMenuItem(value: 'edit', child: Text('编辑')),
                      PopupMenuItem(value: 'delete', child: Text('删除')),
                    ],
                  ),
                ),
              ),
            ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _add,
        icon: const Icon(Icons.add),
        label: const Text('添加订阅'),
      ),
    );
  }
}
