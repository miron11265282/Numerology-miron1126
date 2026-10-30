import SwiftUI
import WebKit
import UIKit

@main
struct NumerologyApp: App {
    var body: some Scene {
        WindowGroup {
            WebContainer()
                .ignoresSafeArea()
                .background(Color.black)
        }
    }
}

struct WebContainer: UIViewRepresentable {
    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> WKWebView {
        let cfg = WKWebViewConfiguration()
        let uc = WKUserContentController()
        for name in ["copy", "share", "shareImage"] {
            uc.add(context.coordinator, name: name)
        }
        cfg.userContentController = uc
        let web = WKWebView(frame: .zero, configuration: cfg)
        web.isOpaque = false
        web.backgroundColor = .black
        web.scrollView.backgroundColor = .black
        web.scrollView.bounces = false
        web.navigationDelegate = context.coordinator
        web.uiDelegate = context.coordinator
        if let url = Bundle.main.url(forResource: "index", withExtension: "html") {
            web.loadFileURL(url, allowingReadAccessTo: url.deletingLastPathComponent())
        }
        return web
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    final class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate, WKScriptMessageHandler {
        func userContentController(_ c: WKUserContentController, didReceive message: WKScriptMessage) {
            switch message.name {
            case "copy":
                if let s = message.body as? String { UIPasteboard.general.string = s }
            case "share":
                if let s = message.body as? String { present([s]) }
            case "shareImage":
                if let b = message.body as? String, let d = Data(base64Encoded: b), let img = UIImage(data: d) {
                    present([img])
                }
            default:
                break
            }
        }

        private func present(_ items: [Any]) {
            let vc = UIActivityViewController(activityItems: items, applicationActivities: nil)
            guard let root = UIApplication.shared.connectedScenes
                .compactMap({ ($0 as? UIWindowScene)?.keyWindow })
                .first?.rootViewController else { return }
            var top = root
            while let next = top.presentedViewController { top = next }
            vc.popoverPresentationController?.sourceView = top.view
            top.present(vc, animated: true)
        }

        func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction,
                     decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            if let u = action.request.url, u.scheme != "file", action.navigationType == .linkActivated {
                UIApplication.shared.open(u)
                decisionHandler(.cancel)
            } else {
                decisionHandler(.allow)
            }
        }

        func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration,
                     for action: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
            if let u = action.request.url { UIApplication.shared.open(u) }
            return nil
        }
    }
}
