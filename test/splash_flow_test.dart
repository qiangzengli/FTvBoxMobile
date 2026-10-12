import 'package:fbox/app/fbox_app.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('shows the branded splash before the home screen', (
    tester,
  ) async {
    await tester.pumpWidget(const FBoxApp());

    expect(find.text('自由 · 聚合 · 畅享'), findsOneWidget);
    expect(find.text('搜索'), findsNothing);

    await tester.pump(const Duration(milliseconds: 800));
    await tester.pumpAndSettle();

    expect(find.text('搜索'), findsOneWidget);
  });
}
