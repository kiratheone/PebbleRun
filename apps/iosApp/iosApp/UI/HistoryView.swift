import SwiftUI
import shared

/**
 * Native iOS SwiftUI view for workout history screen.
 * Implements TASK-015: Create iOS-specific SwiftUI views with proper navigation.
 * Follows GUD-002: iOS Human Interface Guidelines with native SwiftUI.
 */
struct HistoryView: View {
    @StateObject private var viewModel = HistoryViewModel()
    @State private var showingFilterSheet = false
    
    var body: some View {
        NavigationView {
            ZStack {
                // Background
                Color(.systemBackground)
                    .ignoresSafeArea()
                
                if viewModel.workouts.isEmpty && !viewModel.isLoading {
                    emptyStateView
                } else {
                    workoutListView
                }
                
                if viewModel.isLoading {
                    ProgressView("Loading workouts...")
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(Color(.systemBackground).opacity(0.8))
                }
            }
            .navigationTitle("History")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    Button(action: {
                        showingFilterSheet = true
                    }) {
                        Image(systemName: "line.3.horizontal.decrease.circle")
                            .foregroundColor(.accentColor)
                    }
                    
                    Button(action: {
                        viewModel.exportWorkouts()
                    }) {
                        Image(systemName: "square.and.arrow.up")
                            .foregroundColor(.accentColor)
                    }
                    .disabled(viewModel.workouts.isEmpty)
                }
            }
            .refreshable {
                await viewModel.refreshWorkouts()
            }
            .sheet(isPresented: $showingFilterSheet) {
                FilterSheet(viewModel: viewModel)
            }
        }
        .onAppear {
            viewModel.loadWorkouts()
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
    
    private var emptyStateView: some View {
        VStack(spacing: 20) {
            Image(systemName: "figure.run.circle")
                .font(.system(size: 80))
                .foregroundColor(.secondary)
            
            Text("No Workouts Yet")
                .font(.title2)
                .fontWeight(.semibold)
                .foregroundColor(.primary)
            
            Text("Start your first workout to see your activity history here")
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            
            Button("Start Workout") {
                // Navigate to workout view
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
        }
    }
    
    private var workoutListView: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                // Stats Summary Card
                if !viewModel.workouts.isEmpty {
                    statsOverviewCard
                        .padding(.horizontal, 16)
                        .padding(.top, 8)
                }
                
                // Workout List
                ForEach(viewModel.workouts, id: \.id) { workout in
                    WorkoutCard(workout: workout) {
                        viewModel.deleteWorkout(workout)
                    }
                    .padding(.horizontal, 16)
                }
            }
            .padding(.bottom, 20)
        }
    }
    
    private var statsOverviewCard: some View {
        VStack(spacing: 16) {
            HStack {
                Text("Overview")
                    .font(.headline)
                    .foregroundColor(.primary)
                Spacer()
                Text(viewModel.selectedPeriodText)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            LazyVGrid(columns: [
                GridItem(.flexible()),
                GridItem(.flexible()),
                GridItem(.flexible())
            ], spacing: 12) {
                OverviewStatCard(
                    title: "Total Workouts",
                    value: "\(viewModel.totalWorkouts)",
                    icon: "figure.run",
                    color: .blue
                )
                
                OverviewStatCard(
                    title: "Total Distance",
                    value: viewModel.totalDistance,
                    icon: "location.fill",
                    color: .green
                )
                
                OverviewStatCard(
                    title: "Avg Duration",
                    value: viewModel.averageDuration,
                    icon: "clock.fill",
                    color: .orange
                )
            }
        }
        .padding(20)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(16)
        .shadow(color: .black.opacity(0.05), radius: 8, x: 0, y: 4)
    }
}

struct WorkoutCard: View {
    let workout: WorkoutSessionUI
    let onDelete: () -> Void
    @State private var showingDeleteAlert = false
    
    var body: some View {
        VStack(spacing: 12) {
            // Header
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(workout.dateText)
                        .font(.headline)
                        .foregroundColor(.primary)
                    
                    Text(workout.timeText)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                
                Spacer()
                
                Button(action: {
                    showingDeleteAlert = true
                }) {
                    Image(systemName: "trash")
                        .foregroundColor(.red)
                        .font(.system(size: 16))
                }
            }
            
            // Stats
            HStack(spacing: 0) {
                WorkoutStatColumn(
                    title: "Duration",
                    value: workout.durationText,
                    icon: "clock.fill",
                    color: .blue
                )
                
                Divider()
                    .frame(height: 40)
                
                WorkoutStatColumn(
                    title: "Distance",
                    value: workout.distanceText,
                    icon: "location.fill",
                    color: .green
                )
                
                Divider()
                    .frame(height: 40)
                
                WorkoutStatColumn(
                    title: "Avg Pace",
                    value: workout.avgPaceText,
                    icon: "speedometer",
                    color: .orange
                )
                
                Divider()
                    .frame(height: 40)
                
                WorkoutStatColumn(
                    title: "Avg HR",
                    value: workout.avgHRText,
                    icon: "heart.fill",
                    color: .red
                )
            }
        }
        .padding(16)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.05), radius: 4, x: 0, y: 2)
        .alert("Delete Workout", isPresented: $showingDeleteAlert) {
            Button("Cancel", role: .cancel) { }
            Button("Delete", role: .destructive) {
                onDelete()
            }
        } message: {
            Text("Are you sure you want to delete this workout? This action cannot be undone.")
        }
    }
}

struct WorkoutStatColumn: View {
    let title: String
    let value: String
    let icon: String
    let color: Color
    
    var body: some View {
        VStack(spacing: 6) {
            Image(systemName: icon)
                .font(.system(size: 16))
                .foregroundColor(color)
            
            Text(value)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(.primary)
            
            Text(title)
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
    }
}

struct OverviewStatCard: View {
    let title: String
    let value: String
    let icon: String
    let color: Color
    
    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 20))
                .foregroundColor(color)
            
            Text(value)
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.primary)
            
            Text(title)
                .font(.caption2)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .background(Color(.tertiarySystemBackground))
        .cornerRadius(10)
    }
}

struct FilterSheet: View {
    @ObservedObject var viewModel: HistoryViewModel
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        NavigationView {
            Form {
                Section("Time Period") {
                    Picker("Period", selection: $viewModel.selectedPeriod) {
                        Text("Last Week").tag(TimePeriod.lastWeek)
                        Text("Last Month").tag(TimePeriod.lastMonth)
                        Text("Last 3 Months").tag(TimePeriod.last3Months)
                        Text("Last Year").tag(TimePeriod.lastYear)
                        Text("All Time").tag(TimePeriod.allTime)
                    }
                    .pickerStyle(.wheel)
                }
                
                Section("Sort By") {
                    Picker("Sort", selection: $viewModel.sortOption) {
                        Text("Date (Newest)").tag(SortOption.dateNewest)
                        Text("Date (Oldest)").tag(SortOption.dateOldest)
                        Text("Duration (Longest)").tag(SortOption.durationLongest)
                        Text("Distance (Farthest)").tag(SortOption.distanceFarthest)
                    }
                }
            }
            .navigationTitle("Filter & Sort")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Reset") {
                        viewModel.resetFilters()
                    }
                }
                
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
        .presentationDetents([.medium])
    }
}

#Preview {
    HistoryView()
}
