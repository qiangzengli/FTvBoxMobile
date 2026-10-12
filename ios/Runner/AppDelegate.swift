import AVKit
import Flutter
import UIKit

@main
@objc class AppDelegate: FlutterAppDelegate, FlutterImplicitEngineDelegate {
  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }

  func didInitializeImplicitFlutterEngine(_ engineBridge: FlutterImplicitEngineBridge) {
    GeneratedPluginRegistrant.register(with: engineBridge.pluginRegistry)
    guard let registrar = engineBridge.pluginRegistry.registrar(
      forPlugin: "FBoxPlatformPlugin"
    ) else { return }
    let channel = FlutterMethodChannel(
      name: "com.fbox.app/platform",
      binaryMessenger: registrar.messenger()
    )
    channel.setMethodCallHandler { call, result in
      guard call.method == "showAirPlayPicker" else {
        result(FlutterMethodNotImplemented)
        return
      }
      DispatchQueue.main.async {
        guard let window = UIApplication.shared.connectedScenes
          .compactMap({ $0 as? UIWindowScene })
          .flatMap({ $0.windows })
          .first(where: { $0.isKeyWindow }) else {
          result(FlutterError(code: "no_window", message: "No active window", details: nil))
          return
        }
        let picker = AVRoutePickerView(frame: CGRect(x: -60, y: -60, width: 44, height: 44))
        picker.prioritizesVideoDevices = true
        window.addSubview(picker)
        if let button = picker.subviews.compactMap({ $0 as? UIButton }).first {
          button.sendActions(for: .touchUpInside)
          result(nil)
        } else {
          result(FlutterError(code: "no_picker", message: "AirPlay picker unavailable", details: nil))
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
          picker.removeFromSuperview()
        }
      }
    }
  }
}
