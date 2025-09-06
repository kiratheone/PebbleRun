import SwiftUI
import shared

/**
 * Main iOS ContentView with native SwiftUI navigation.
 * Implements TASK-015: Create iOS-specific SwiftUI views with proper navigation.
 * Follows GUD-002: iOS Human Interface Guidelines with native SwiftUI.
 */
struct ContentView: View {
    @State private var selectedTab = 0
    
    var body: some View {
        TabView(selection: $selectedTab) {
            WorkoutView()
                .tabItem {
                    Image(systemName: "figure.run")
                    Text("Workout")
                }
                .tag(0)
            
            HistoryView()
                .tabItem {
                    Image(systemName: "chart.line.uptrend.xyaxis")
                    Text("History")
                }
                .tag(1)
            
            SettingsView()
                .tabItem {
                    Image(systemName: "gear")
                    Text("Settings")
                }
                .tag(2)
        }
        .accentColor(.blue)
        .onAppear {
            setupTabBarAppearance()
        }
    }
    
    private func setupTabBarAppearance() {
        let appearance = UITabBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor.systemBackground
        
        UITabBar.appearance().standardAppearance = appearance
        UITabBar.appearance().scrollEdgeAppearance = appearance
    }
}



