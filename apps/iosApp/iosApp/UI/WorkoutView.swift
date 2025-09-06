import SwiftUI
import shared

/**
 * Native iOS SwiftUI view for workout tracking screen.
 * Implements TASK-015: Create iOS-specific SwiftUI views with proper navigation.
 * Follows GUD-002: iOS Human Interface Guidelines with native SwiftUI.
 */
struct WorkoutView: View {
    @StateObject private var viewModel = WorkoutViewModel()
    @Environment(\.colorScheme) var colorScheme
    
    var body: some View {
        NavigationView {
            ZStack {
                // Background gradient
                LinearGradient(
                    gradient: Gradient(colors: [
                        Color(.systemBackground),
                        Color(.secondarySystemBackground)
                    ]),
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()
                
                ScrollView {
                    VStack(spacing: 24) {
                        // Status Header
                        workoutStatusHeader
                        
                        // Main Stats Card
                        if viewModel.isWorkoutActive {
                            workoutStatsCard
                        } else {
                            startWorkoutCard
                        }
                        
                        // Heart Rate Section
                        if viewModel.isWorkoutActive {
                            heartRateSection
                        }
                        
                        // Pebble Connection Status
                        pebbleConnectionStatus
                        
                        Spacer(minLength: 100)
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 20)
                }
            }
            .navigationTitle("Workout")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Settings") {
                        // Navigate to settings
                    }
                    .foregroundColor(.accentColor)
                }
            }
        }
        .onAppear {
            viewModel.onViewAppear()
        }
        .alert("Error", isPresented: $viewModel.showError) {
            Button("OK") {
                viewModel.dismissError()
            }
        } message: {
            Text(viewModel.errorMessage)
        }
    }
    
    // MARK: - UI Components
    
    private var workoutStatusHeader: some View {
        VStack(spacing: 8) {
            Image(systemName: viewModel.isWorkoutActive ? "figure.run" : "figure.stand")
                .font(.system(size: 40, weight: .medium))
                .foregroundColor(viewModel.isWorkoutActive ? .green : .secondary)
            
            Text(viewModel.workoutStatusText)
                .font(.title2)
                .fontWeight(.semibold)
                .foregroundColor(.primary)
            
            if viewModel.isWorkoutActive {
                Text("Started: \(viewModel.workoutStartTime)")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 16)
    }
    
    private var startWorkoutCard: some View {
        VStack(spacing: 20) {
            VStack(spacing: 12) {
                Image(systemName: "play.circle.fill")
                    .font(.system(size: 60))
                    .foregroundColor(.accentColor)
                
                Text("Ready to Start")
                    .font(.title2)
                    .fontWeight(.semibold)
                
                Text("Connect your Pebble and start tracking your workout")
                    .font(.body)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
            }
            
            Button(action: {
                viewModel.startWorkout()
            }) {
                HStack {
                    Image(systemName: "play.fill")
                    Text("Start Workout")
                }
                .font(.headline)
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(
                    LinearGradient(
                        gradient: Gradient(colors: [.green, .blue]),
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                )
                .cornerRadius(12)
            }
            .disabled(!viewModel.isPebbleConnected)
        }
        .padding(24)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(16)
        .shadow(color: .black.opacity(0.1), radius: 8, x: 0, y: 4)
    }
    
    private var workoutStatsCard: some View {
        VStack(spacing: 20) {
            // Duration and Control
            VStack(spacing: 12) {
                Text(viewModel.workoutDuration)
                    .font(.system(size: 48, weight: .bold, design: .monospaced))
                    .foregroundColor(.primary)
                
                Text("Duration")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            // Stats Grid
            LazyVGrid(columns: [
                GridItem(.flexible()),
                GridItem(.flexible())
            ], spacing: 16) {
                StatCard(
                    title: "Distance",
                    value: viewModel.distance,
                    icon: "location.fill",
                    color: .blue
                )
                
                StatCard(
                    title: "Pace",
                    value: viewModel.pace,
                    icon: "speedometer",
                    color: .orange
                )
            }
            
            // Control Buttons
            HStack(spacing: 16) {
                Button(action: {
                    viewModel.pauseWorkout()
                }) {
                    HStack {
                        Image(systemName: "pause.fill")
                        Text("Pause")
                    }
                    .font(.headline)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Color.orange)
                    .cornerRadius(10)
                }
                
                Button(action: {
                    viewModel.stopWorkout()
                }) {
                    HStack {
                        Image(systemName: "stop.fill")
                        Text("Stop")
                    }
                    .font(.headline)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Color.red)
                    .cornerRadius(10)
                }
            }
        }
        .padding(24)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(16)
        .shadow(color: .black.opacity(0.1), radius: 8, x: 0, y: 4)
    }
    
    private var heartRateSection: some View {
        VStack(spacing: 16) {
            HStack {
                Image(systemName: "heart.fill")
                    .foregroundColor(.red)
                Text("Heart Rate")
                    .font(.headline)
                Spacer()
            }
            
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(viewModel.currentHeartRate)
                        .font(.system(size: 36, weight: .bold))
                        .foregroundColor(.red)
                    Text("BPM")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                
                Spacer()
                
                VStack(alignment: .trailing, spacing: 8) {
                    HStack {
                        Text("Avg:")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Text(viewModel.averageHeartRate)
                            .font(.body)
                            .fontWeight(.medium)
                    }
                    
                    HStack {
                        Text("Max:")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Text(viewModel.maxHeartRate)
                            .font(.body)
                            .fontWeight(.medium)
                    }
                }
            }
        }
        .padding(20)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(16)
        .shadow(color: .black.opacity(0.1), radius: 8, x: 0, y: 4)
    }
    
    private var pebbleConnectionStatus: some View {
        HStack {
            Image(systemName: viewModel.isPebbleConnected ? "checkmark.circle.fill" : "xmark.circle.fill")
                .foregroundColor(viewModel.isPebbleConnected ? .green : .red)
            
            Text(viewModel.pebbleStatusText)
                .font(.body)
                .foregroundColor(.primary)
            
            Spacer()
            
            if !viewModel.isPebbleConnected {
                Button("Connect") {
                    viewModel.connectToPebble()
                }
                .font(.caption)
                .foregroundColor(.accentColor)
            }
        }
        .padding(16)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.05), radius: 4, x: 0, y: 2)
    }
}

struct StatCard: View {
    let title: String
    let value: String
    let icon: String
    let color: Color
    
    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 24))
                .foregroundColor(color)
            
            Text(value)
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(.primary)
            
            Text(title)
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .background(Color(.tertiarySystemBackground))
        .cornerRadius(12)
    }
}

#Preview {
    WorkoutView()
}
