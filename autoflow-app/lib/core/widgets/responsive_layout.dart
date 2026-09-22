import 'package:flutter/material.dart';

enum ScreenType { mobile, tablet, desktop }

class ResponsiveLayout extends StatelessWidget {
  final Widget mobile;
  final Widget? tablet;
  final Widget desktop;

  const ResponsiveLayout({
    super.key,
    required this.mobile,
    this.tablet,
    required this.desktop,
  });

  static ScreenType getScreenType(BuildContext context) {
    final width = MediaQuery.of(context).size.width;
    if (width >= 1024) return ScreenType.desktop;
    if (width >= 768) return ScreenType.tablet;
    return ScreenType.mobile;
  }

  static bool isDesktop(BuildContext context) => getScreenType(context) == ScreenType.desktop;
  static bool isTablet(BuildContext context) => getScreenType(context) == ScreenType.tablet;
  static bool isMobile(BuildContext context) => getScreenType(context) == ScreenType.mobile;

  @override
  Widget build(BuildContext context) {
    final type = getScreenType(context);
    if (type == ScreenType.desktop) {
      return desktop;
    } else if (type == ScreenType.tablet) {
      return tablet ?? desktop;
    } else {
      return mobile;
    }
  }
}
