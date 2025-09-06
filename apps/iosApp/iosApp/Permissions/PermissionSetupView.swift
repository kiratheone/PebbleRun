import SwiftUI

/**
 * iOS-specific permission setup view with native user flows.
 * Implements TASK-017: Add iOS-specific permission handling (Location, Bluetooth).
 * Follows GUD-003: Platform-specific navigation patterns and user flows.
 */
struct PermissionSetupView: View {
    @StateObject private var permissionManager = PermissionManager()
    @Environment(\.dismiss) private var dismiss
    @State private var currentStep = 0
    
    private let permissions: [PermissionType] = [.location, .bluetooth, .notifications]
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Progress Indicator
                progressIndicator
                
                // Permission Content
                TabView(selection: $currentStep) {
                    ForEach(0..<permissions.count, id: \.self) { index in
                        PermissionStepView(
                            permission: permissions[index],
                            permissionManager: permissionManager,
                            onNext: {
                                withAnimation {
                                    if index < permissions.count - 1 {
                                        currentStep = index + 1
                                    } else {
                                        // All permissions handled, dismiss
                                        dismiss()
                                    }
                                }
                            }
                        )
                        .tag(index)
                    }
                }
                .tabViewStyle(PageTabViewStyle(indexDisplayMode: .never))
                
                // Navigation Buttons
                navigationButtons
            }
            .navigationTitle("App Permissions")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Skip") {
                        dismiss()
                    }
                    .foregroundColor(.secondary)
                }
            }
        }
        .interactiveDismissDisabled()
    }
    
    // MARK: - UI Components
    
    private var progressIndicator: some View {
        VStack(spacing: 16) {
            HStack {
                ForEach(0..<permissions.count, id: \.self) { index in
                    Circle()
                        .fill(index <= currentStep ? Color.accentColor : Color.secondary.opacity(0.3))
                        .frame(width: 12, height: 12)
                    
                    if index < permissions.count - 1 {
                        Rectangle()
                            .fill(index < currentStep ? Color.accentColor : Color.secondary.opacity(0.3))
                            .frame(height: 2)
                    }
                }
            }
            .padding(.horizontal, 40)
            
            Text("Step \(currentStep + 1) of \(permissions.count)")
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .padding(.top, 20)
        .padding(.bottom, 30)
    }
    
    private var navigationButtons: some View {
        HStack(spacing: 16) {
            if currentStep > 0 {
                Button("Previous") {
                    withAnimation {
                        currentStep -= 1
                    }
                }
                .buttonStyle(.bordered)
                .frame(maxWidth: .infinity)
            }
            
            Button(currentStep == permissions.count - 1 ? "Done" : "Next") {
                withAnimation {
                    if currentStep < permissions.count - 1 {
                        currentStep += 1
                    } else {
                        dismiss()
                    }
                }
            }
            .buttonStyle(.borderedProminent)
            .frame(maxWidth: .infinity)
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 30)
    }
}

struct PermissionStepView: View {
    let permission: PermissionType
    @ObservedObject var permissionManager: PermissionManager
    let onNext: () -> Void
    
    @State private var hasRequestedPermission = false
    
    var body: some View {
        ScrollView {
            VStack(spacing: 32) {
                // Permission Icon
                VStack(spacing: 16) {
                    Image(systemName: permission.icon)
                        .font(.system(size: 80))
                        .foregroundColor(.accentColor)
                    
                    Text(permission.displayName)
                        .font(.largeTitle)
                        .fontWeight(.bold)
                }
                
                // Description
                VStack(spacing: 16) {
                    Text("Why we need this permission")
                        .font(.headline)
                        .foregroundColor(.primary)
                    
                    Text(permission.description)
                        .font(.body)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 20)
                }
                
                // Permission Status Card
                permissionStatusCard
                
                // Action Button
                actionButton
                
                Spacer(minLength: 40)
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
        }
    }
    
    private var permissionStatusCard: some View {
        VStack(spacing: 12) {
            HStack {
                Text("Current Status")
                    .font(.headline)
                    .foregroundColor(.primary)
                
                Spacer()
                
                statusIndicator
            }
            
            Text(permissionManager.getPermissionStatusText(for: permission))
                .font(.body)
                .foregroundColor(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(20)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
    }
    
    private var statusIndicator: some View {
        HStack(spacing: 8) {
            Circle()
                .fill(isPermissionGranted ? .green : .orange)
                .frame(width: 12, height: 12)
            
            Text(isPermissionGranted ? "Granted" : "Pending")
                .font(.caption)
                .foregroundColor(isPermissionGranted ? .green : .orange)
        }
    }
    
    private var actionButton: some View {
        VStack(spacing: 12) {
            if !isPermissionGranted {
                Button(action: {
                    requestPermission()
                }) {
                    HStack {
                        Image(systemName: "checkmark.circle")
                        Text(permissionManager.getPermissionActionText(for: permission))
                    }
                    .font(.headline)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(Color.accentColor)
                    .cornerRadius(12)
                }
            }
            
            if hasRequestedPermission || isPermissionGranted {
                Button("Continue") {
                    onNext()
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .frame(maxWidth: .infinity)
            }
        }
    }
    
    private var isPermissionGranted: Bool {
        switch permission {
        case .location:
            return permissionManager.hasLocationPermission
        case .bluetooth:
            return permissionManager.hasBluetoothPermission
        case .notifications:
            return permissionManager.hasNotificationPermission
        }
    }
    
    private func requestPermission() {
        hasRequestedPermission = true
        
        switch permission {
        case .location:
            permissionManager.requestLocationPermission()
        case .bluetooth:
            permissionManager.requestBluetoothPermission()
        case .notifications:
            permissionManager.requestNotificationPermission()
        }
        
        // Auto-advance after a short delay if permission was granted
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            if isPermissionGranted {
                onNext()
            }
        }
    }
}

struct PermissionSummaryView: View {
    @ObservedObject var permissionManager: PermissionManager
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 24) {
                    // Header
                    VStack(spacing: 16) {
                        Image(systemName: permissionManager.allPermissionsGranted ? "checkmark.circle.fill" : "exclamationmark.triangle.fill")
                            .font(.system(size: 60))
                            .foregroundColor(permissionManager.allPermissionsGranted ? .green : .orange)
                        
                        Text(permissionManager.allPermissionsGranted ? "All Set!" : "Setup Incomplete")
                            .font(.title)
                            .fontWeight(.bold)
                        
                        Text(permissionManager.allPermissionsGranted ? 
                             "Your app is ready to track workouts with your Pebble." :
                             "Some permissions are still needed for full functionality.")
                            .font(.body)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 20)
                    }
                    
                    // Permission Status List
                    VStack(spacing: 12) {
                        ForEach(PermissionType.allCases, id: \.self) { permission in
                            PermissionStatusRow(
                                permission: permission,
                                permissionManager: permissionManager
                            )
                        }
                    }
                    
                    // Action Buttons
                    VStack(spacing: 12) {
                        if !permissionManager.allPermissionsGranted {
                            Button("Fix Permissions") {
                                permissionManager.openAppSettings()
                            }
                            .buttonStyle(.borderedProminent)
                            .controlSize(.large)
                            .frame(maxWidth: .infinity)
                        }
                        
                        Button("Continue") {
                            dismiss()
                        }
                        .buttonStyle(permissionManager.allPermissionsGranted ? .borderedProminent : .bordered)
                        .controlSize(.large)
                        .frame(maxWidth: .infinity)
                    }
                    .padding(.top, 20)
                    
                    Spacer()
                }
                .padding(20)
            }
            .navigationTitle("Permission Summary")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

struct PermissionStatusRow: View {
    let permission: PermissionType
    @ObservedObject var permissionManager: PermissionManager
    
    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: permission.icon)
                .font(.system(size: 24))
                .foregroundColor(.accentColor)
                .frame(width: 30)
            
            VStack(alignment: .leading, spacing: 4) {
                Text(permission.displayName)
                    .font(.headline)
                    .foregroundColor(.primary)
                
                Text(permissionManager.getPermissionStatusText(for: permission))
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            Image(systemName: isPermissionGranted ? "checkmark.circle.fill" : "xmark.circle.fill")
                .foregroundColor(isPermissionGranted ? .green : .red)
                .font(.system(size: 20))
        }
        .padding(16)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
    }
    
    private var isPermissionGranted: Bool {
        switch permission {
        case .location:
            return permissionManager.hasLocationPermission
        case .bluetooth:
            return permissionManager.hasBluetoothPermission
        case .notifications:
            return permissionManager.hasNotificationPermission
        }
    }
}

#Preview {
    PermissionSetupView()
}
