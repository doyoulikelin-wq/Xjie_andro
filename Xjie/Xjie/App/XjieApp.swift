import SwiftUI

@main
struct XjieApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var authManager = AuthManager.shared
    @StateObject private var networkMonitor = NetworkMonitor.shared
    @StateObject private var pushManager = PushNotificationManager.shared
    @State private var showSplash = true

    var body: some Scene {
        WindowGroup {
            ZStack {
                if authManager.isLoggedIn {
                    MainTabView()
                        .environmentObject(authManager)
                        .environmentObject(networkMonitor)
                        .onAppear {
                            pushManager.requestPermission()
                            Task { await FeatureFlagService.shared.fetchIfNeeded() }
                        }
                } else {
                    LoginView()
                        .environmentObject(authManager)
                        .environmentObject(networkMonitor)
                }

                if showSplash {
                    SplashView { showSplash = false }
                        .transition(.opacity)
                        .zIndex(1)
                }
            }
        }
    }
}
