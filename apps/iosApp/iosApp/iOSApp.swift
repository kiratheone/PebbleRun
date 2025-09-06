import SwiftUI

@main
struct iOSApp: App {
    
    @StateObject private var permissionManager = PermissionManager()
    @State private var showPermissionSetup = false
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .onAppear {
                    checkPermissionsOnLaunch()
                }
                .sheet(isPresented: $showPermissionSetup) {
                    PermissionSetupView()
                }
                .environmentObject(permissionManager)
        }
    }
    
    private func checkPermissionsOnLaunch() {
        // Check if this is the first launch or if permissions are missing
        let hasShownPermissionSetup = UserDefaults.standard.bool(forKey: "hasShownPermissionSetup")
        
        if !hasShownPermissionSetup || !permissionManager.allPermissionsGranted {
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                showPermissionSetup = true
                UserDefaults.standard.set(true, forKey: "hasShownPermissionSetup")
            }
        }
    }
}