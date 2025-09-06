import SwiftUI
import shared

/**
 * Native iOS SwiftUI view for app settings screen.
 * Implements TASK-015: Create iOS-specific SwiftUI views with proper navigation.
 * Follows GUD-002: iOS Human Interface Guidelines with native SwiftUI.
 */
struct SettingsView: View {
    @StateObject private var viewModel = SettingsViewModel()
    @State private var showingAbout = false
    @State private var showingExportOptions = false
    
    var body: some View {
        NavigationView {
            Form {
                // Pebble Connection Section
                Section("Pebble Connection") {
                    HStack {
                        Image(systemName: "applewatch")
                            .foregroundColor(.blue)
                            .frame(width: 24)
                        
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Pebble Status")
                                .font(.body)
                            Text(viewModel.pebbleStatusText)
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        
                        Spacer()
                        
                        Circle()
                            .fill(viewModel.isPebbleConnected ? .green : .red)
                            .frame(width: 12, height: 12)
                    }
                    
                    if !viewModel.isPebbleConnected {
                        Button("Connect to Pebble") {
                            viewModel.connectToPebble()
                        }
                        .foregroundColor(.accentColor)
                    } else {
                        Button("Disconnect") {
                            viewModel.disconnectFromPebble()
                        }
                        .foregroundColor(.red)
                    }
                    
                    Button("Scan for Devices") {
                        viewModel.scanForPebbles()
                    }
                    .foregroundColor(.accentColor)
                }
                
                // Workout Settings
                Section("Workout Settings") {
                    HStack {
                        Image(systemName: "location.fill")
                            .foregroundColor(.green)
                            .frame(width: 24)
                        
                        Toggle("GPS Tracking", isOn: $viewModel.gpsEnabled)
                    }
                    
                    HStack {
                        Image(systemName: "heart.fill")
                            .foregroundColor(.red)
                            .frame(width: 24)
                        
                        Toggle("Heart Rate Monitoring", isOn: $viewModel.heartRateEnabled)
                    }
                    
                    HStack {
                        Image(systemName: "bell.fill")
                            .foregroundColor(.orange)
                            .frame(width: 24)
                        
                        Toggle("Workout Notifications", isOn: $viewModel.notificationsEnabled)
                    }
                    
                    HStack {
                        Image(systemName: "speaker.wave.2.fill")
                            .foregroundColor(.purple)
                            .frame(width: 24)
                        
                        Toggle("Audio Cues", isOn: $viewModel.audioCuesEnabled)
                    }
                }
                
                // Units and Display
                Section("Units & Display") {
                    HStack {
                        Image(systemName: "ruler.fill")
                            .foregroundColor(.blue)
                            .frame(width: 24)
                        
                        Text("Distance Unit")
                        
                        Spacer()
                        
                        Picker("Distance", selection: $viewModel.distanceUnit) {
                            Text("Kilometers").tag(DistanceUnit.kilometers)
                            Text("Miles").tag(DistanceUnit.miles)
                        }
                        .pickerStyle(.menu)
                    }
                    
                    HStack {
                        Image(systemName: "speedometer")
                            .foregroundColor(.orange)
                            .frame(width: 24)
                        
                        Text("Pace Unit")
                        
                        Spacer()
                        
                        Picker("Pace", selection: $viewModel.paceUnit) {
                            Text("min/km").tag(PaceUnit.minPerKm)
                            Text("min/mi").tag(PaceUnit.minPerMile)
                            Text("km/h").tag(PaceUnit.kmPerHour)
                            Text("mph").tag(PaceUnit.milesPerHour)
                        }
                        .pickerStyle(.menu)
                    }
                }
                
                // Privacy & Security
                Section("Privacy & Security") {
                    HStack {
                        Image(systemName: "lock.fill")
                            .foregroundColor(.gray)
                            .frame(width: 24)
                        
                        Toggle("Require Authentication", isOn: $viewModel.requireAuthentication)
                    }
                    
                    HStack {
                        Image(systemName: "location.slash.fill")
                            .foregroundColor(.red)
                            .frame(width: 24)
                        
                        Toggle("Anonymous Data Sharing", isOn: $viewModel.anonymousDataSharing)
                    }
                    
                    Button("Clear All Data") {
                        viewModel.showClearDataAlert()
                    }
                    .foregroundColor(.red)
                }
                
                // Data Management
                Section("Data Management") {
                    Button("Export Workout Data") {
                        showingExportOptions = true
                    }
                    .foregroundColor(.accentColor)
                    
                    Button("Import Workout Data") {
                        viewModel.importWorkoutData()
                    }
                    .foregroundColor(.accentColor)
                    
                    HStack {
                        Text("Storage Used")
                        Spacer()
                        Text(viewModel.storageUsedText)
                            .foregroundColor(.secondary)
                    }
                }
                
                // Background App Settings
                Section("Background Activity") {
                    HStack {
                        Image(systemName: "app.badge.checkmark.fill")
                            .foregroundColor(.green)
                            .frame(width: 24)
                        
                        Toggle("Background App Refresh", isOn: $viewModel.backgroundRefreshEnabled)
                    }
                    
                    HStack {
                        Image(systemName: "location.circle.fill")
                            .foregroundColor(.blue)
                            .frame(width: 24)
                        
                        Toggle("Background Location", isOn: $viewModel.backgroundLocationEnabled)
                    }
                    
                    Button("Open iOS Settings") {
                        viewModel.openiOSSettings()
                    }
                    .foregroundColor(.accentColor)
                }
                
                // About Section
                Section("About") {
                    HStack {
                        Text("Version")
                        Spacer()
                        Text(viewModel.appVersion)
                            .foregroundColor(.secondary)
                    }
                    
                    HStack {
                        Text("Build")
                        Spacer()
                        Text(viewModel.buildNumber)
                            .foregroundColor(.secondary)
                    }
                    
                    Button("About PebbleRun") {
                        showingAbout = true
                    }
                    .foregroundColor(.accentColor)
                    
                    Button("Privacy Policy") {
                        viewModel.openPrivacyPolicy()
                    }
                    .foregroundColor(.accentColor)
                    
                    Button("Terms of Service") {
                        viewModel.openTermsOfService()
                    }
                    .foregroundColor(.accentColor)
                }
                
                // Debug Section (only in debug builds)
                #if DEBUG
                Section("Debug") {
                    Button("Reset All Settings") {
                        viewModel.resetAllSettings()
                    }
                    .foregroundColor(.orange)
                    
                    Button("Generate Test Data") {
                        viewModel.generateTestData()
                    }
                    .foregroundColor(.blue)
                    
                    HStack {
                        Text("Debug Mode")
                        Spacer()
                        Toggle("", isOn: $viewModel.debugModeEnabled)
                    }
                }
                #endif
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.large)
        }
        .sheet(isPresented: $showingAbout) {
            AboutView()
        }
        .sheet(isPresented: $showingExportOptions) {
            ExportOptionsView(viewModel: viewModel)
        }
        .onAppear {
            viewModel.loadSettings()
        }
        .alert("Clear All Data", isPresented: $viewModel.showClearDataAlert) {
            Button("Cancel", role: .cancel) { }
            Button("Clear All", role: .destructive) {
                viewModel.clearAllData()
            }
        } message: {
            Text("This will permanently delete all your workout data. This action cannot be undone.")
        }
        .alert("Error", isPresented: $viewModel.showError) {
            Button("OK") {
                viewModel.dismissError()
            }
        } message: {
            Text(viewModel.errorMessage)
        }
    }
}

struct AboutView: View {
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 24) {
                    // App Icon and Title
                    VStack(spacing: 16) {
                        Image("AppIcon") // You'll need to add this to Assets
                            .resizable()
                            .frame(width: 100, height: 100)
                            .cornerRadius(20)
                        
                        VStack(spacing: 4) {
                            Text("PebbleRun")
                                .font(.title)
                                .fontWeight(.bold)
                            
                            Text("Pebble 2 HR Companion")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }
                    }
                    
                    // Description
                    VStack(alignment: .leading, spacing: 12) {
                        Text("About")
                            .font(.headline)
                        
                        Text("PebbleRun brings modern workout tracking to your classic Pebble 2 HR smartwatch. Track your runs with real-time heart rate monitoring, GPS tracking, and pace calculation.")
                            .font(.body)
                            .lineLimit(nil)
                    }
                    
                    // Features
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Features")
                            .font(.headline)
                        
                        VStack(alignment: .leading, spacing: 8) {
                            FeatureRow(icon: "heart.fill", text: "Real-time heart rate monitoring", color: .red)
                            FeatureRow(icon: "location.fill", text: "GPS tracking and route mapping", color: .blue)
                            FeatureRow(icon: "speedometer", text: "Live pace and distance calculation", color: .orange)
                            FeatureRow(icon: "applewatch", text: "Seamless Pebble integration", color: .purple)
                            FeatureRow(icon: "chart.line.uptrend.xyaxis", text: "Workout history and statistics", color: .green)
                        }
                    }
                    
                    Spacer()
                }
                .padding(24)
            }
            .navigationTitle("About")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
    }
}

struct FeatureRow: View {
    let icon: String
    let text: String
    let color: Color
    
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundColor(color)
                .frame(width: 20)
            
            Text(text)
                .font(.body)
        }
    }
}

struct ExportOptionsView: View {
    @ObservedObject var viewModel: SettingsViewModel
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        NavigationView {
            Form {
                Section("Export Format") {
                    Picker("Format", selection: $viewModel.exportFormat) {
                        Text("GPX").tag(ExportFormat.gpx)
                        Text("CSV").tag(ExportFormat.csv)
                        Text("JSON").tag(ExportFormat.json)
                    }
                    .pickerStyle(.segmented)
                }
                
                Section("Date Range") {
                    DatePicker("From", selection: $viewModel.exportFromDate, displayedComponents: .date)
                    DatePicker("To", selection: $viewModel.exportToDate, displayedComponents: .date)
                }
                
                Section("Options") {
                    Toggle("Include Heart Rate Data", isOn: $viewModel.includeHeartRateData)
                    Toggle("Include GPS Data", isOn: $viewModel.includeGPSData)
                    Toggle("Include Statistics", isOn: $viewModel.includeStatistics)
                }
                
                Section {
                    Button("Export Workouts") {
                        viewModel.exportWorkouts()
                        dismiss()
                    }
                    .frame(maxWidth: .infinity)
                    .foregroundColor(.white)
                    .listRowBackground(Color.accentColor)
                }
            }
            .navigationTitle("Export Options")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
            }
        }
        .presentationDetents([.medium])
    }
}

#Preview {
    SettingsView()
}
