# Performance Testing and Battery Optimization Guide

## Overview

This guide provides comprehensive instructions for performance testing and battery optimization validation for the PebbleRun Pebble 2 HR companion app. This documentation satisfies **TASK-053**: Conduct performance testing and battery usage optimization.

## Performance Testing Framework

### Test Categories

#### 1. Core Performance Tests
- **Session Creation Performance**: Validates workout session creation time ≤100ms average, ≤500ms maximum
- **Data Update Scalability**: Ensures linear performance scaling with workout duration (≤10ms per update)
- **Memory Usage Stability**: Monitors memory growth during extended workouts (≤50MB for 2-hour session)
- **Background Operation Efficiency**: Validates background processing performance (≤5ms average)

#### 2. Battery Optimization Tests
- **Efficient Workout Mode**: GPS background + HR monitoring ≤15% battery per hour
- **Intensive Workout Mode**: GPS active + screen on ≤25% battery per hour  
- **Background Tracking Mode**: Minimal power usage ≤8% battery per hour
- **Workout Duration Estimation**: Realistic battery life predictions for different usage patterns

#### 3. Power Optimization Strategies
- **Adaptive GPS Frequency**: Reduce GPS updates when stationary
- **HR Confidence-Based Sampling**: Adjust sampling rate based on sensor reliability
- **Movement State Detection**: Optimize power usage based on user activity
- **Battery Level Response**: Automatic mode switching based on remaining charge

## Performance Benchmarks

### Response Time Requirements

| Operation | Target (Average) | Maximum | Critical Threshold |
|-----------|------------------|---------|-------------------|
| Session Creation | <100ms | <500ms | 1000ms |
| Data Update | <10ms | <50ms | 100ms |
| Background Process | <5ms | <20ms | 50ms |
| Database Query | <25ms | <100ms | 200ms |

### Memory Usage Limits

| Duration | Memory Limit | Growth Rate | Critical Threshold |
|----------|-------------|-------------|-------------------|
| 30 minutes | <10MB | <200KB/min | 50MB |
| 1 hour | <20MB | <300KB/min | 100MB |
| 2 hours | <50MB | <400KB/min | 200MB |
| 4+ hours | <100MB | <500KB/min | 500MB |

### Battery Usage Targets

| Mode | Power Consumption | Target Duration | Conditions |
|------|------------------|----------------|------------|
| Power Saver | ≤8%/hour | 12+ hours | GPS background, reduced sampling |
| Balanced | ≤15%/hour | 6+ hours | Normal GPS, standard sampling |
| Performance | ≤25%/hour | 4+ hours | GPS active, high-frequency sampling |
| Intensive | ≤35%/hour | 3+ hours | All features active, screen on |

## Test Execution Procedures

### 1. Running Performance Tests

```bash
# Navigate to project directory
cd /path/to/PebbleRun

# Run performance test suite
./gradlew :shared:domain:testDebugUnitTest --tests "*PerformanceAndBatteryTest*"

# Run with detailed output
./gradlew :shared:domain:testDebugUnitTest --tests "*PerformanceAndBatteryTest*" --info
```

### 2. Memory Profiling

#### Kotlin Multiplatform Memory Testing
```kotlin
// Example memory monitoring in tests
@Test
fun memoryUsageTest() {
    val monitor = PerformanceMonitor()
    monitor.setMemoryBaseline()
    
    // Execute workout simulation
    repeat(1000) { /* workout operations */ }
    
    val memoryIncrease = monitor.getMemoryIncrease()
    assertTrue(memoryIncrease < MEMORY_LIMIT)
}
```

#### Platform-Specific Profiling
- **Android**: Use Android Studio Memory Profiler during real device testing
- **iOS**: Use Xcode Instruments for memory analysis during device testing

### 3. Battery Usage Analysis

#### Simulation Testing
```kotlin
@Test
fun batteryOptimizationTest() {
    val simulator = BatteryUsageSimulator()
    val optimizer = BatteryOptimizationManager()
    
    // Test different usage patterns
    val operations = listOf("gps_active", "hr_monitoring", "bluetooth_active")
    simulator.simulateUsage(operations, 1.0) // 1 hour
    
    val remainingBattery = simulator.getBatteryLevel()
    assertTrue(remainingBattery >= TARGET_BATTERY_LEVEL)
}
```

#### Real Device Testing
1. **Preparation**:
   - Fully charge device
   - Close all other apps
   - Disable unnecessary features (WiFi, cellular if using airplane mode + Bluetooth)

2. **Test Execution**:
   - Start workout session
   - Monitor battery drain every 15 minutes
   - Record GPS accuracy and HR sensor performance
   - Test different optimization modes

3. **Data Collection**:
   - Battery percentage at regular intervals
   - GPS accuracy measurements
   - HR confidence levels
   - App responsiveness metrics

## Battery Optimization Strategies

### 1. Adaptive GPS Management

#### Movement State Detection
```kotlin
enum class MovementState {
    STATIONARY,     // GPS updates every 30-60 seconds
    WALKING,        // GPS updates every 10-15 seconds  
    RUNNING,        // GPS updates every 2-5 seconds
    UNKNOWN         // Default to balanced approach
}
```

#### GPS Update Intervals by Mode
- **Power Saver**: 30-60 seconds (stationary), 20 seconds (moving)
- **Balanced**: 15 seconds (stationary), 10 seconds (moving) 
- **Performance**: 5 seconds (stationary), 2 seconds (moving)
- **Adaptive**: Dynamically adjusts based on movement and battery level

### 2. Heart Rate Optimization

#### Confidence-Based Sampling
```kotlin
fun adjustHRSampling(confidence: Float): Duration {
    return when {
        confidence > 0.8f -> 3.seconds    // High confidence: frequent sampling
        confidence > 0.5f -> 5.seconds    // Medium confidence: normal sampling
        confidence > 0.3f -> 10.seconds   // Low confidence: reduced sampling
        else -> 15.seconds                // Very low: minimal sampling
    }
}
```

#### Smart Sensor Management
- Automatically reduce sampling frequency when confidence is low
- Increase sampling when confidence improves
- Switch to alternative algorithms when primary sensor fails

### 3. Bluetooth Power Management

#### Connection Optimization
- **Active Workout**: Maintain constant connection for real-time data
- **Background Mode**: Reduce connection frequency to 30-60 seconds
- **Power Saver**: Use cached data, sync only when necessary

#### Data Transmission Efficiency
- Batch data updates to reduce transmission overhead
- Compress workout data before transmission
- Use efficient serialization formats

## Real Device Testing Guide

### Pre-Test Setup

1. **Device Preparation**:
   ```bash
   # Android: Enable developer options
   adb shell settings put global stay_on_while_plugged_in 0
   
   # Clear app data for fresh start
   adb shell pm clear com.arikachmad.pebblerun
   ```

2. **Pebble Watch Setup**:
   - Ensure Pebble is fully charged
   - Install PebbleRun watchapp
   - Verify Bluetooth connection stability

3. **Testing Environment**:
   - GPS-friendly outdoor location
   - Stable Bluetooth environment
   - Consistent temperature conditions

### Test Scenarios

#### Scenario 1: Short Workout (30 minutes)
- **Objective**: Validate basic performance and battery usage
- **Setup**: Balanced optimization mode, normal GPS accuracy
- **Measurements**: Battery drain, GPS accuracy, HR confidence
- **Expected Results**: ≤8% battery usage, ≥5m GPS accuracy

#### Scenario 2: Extended Workout (2 hours)
- **Objective**: Test memory stability and long-term battery performance
- **Setup**: Adaptive optimization mode
- **Measurements**: Memory growth, performance degradation, battery optimization effectiveness
- **Expected Results**: ≤30% battery usage, stable performance

#### Scenario 3: Power Saver Mode
- **Objective**: Validate maximum battery conservation
- **Setup**: Power saver mode, reduced GPS accuracy
- **Measurements**: Minimum battery drain while maintaining basic functionality
- **Expected Results**: ≤16% battery usage over 2 hours

#### Scenario 4: Performance Mode
- **Objective**: Test maximum accuracy with acceptable battery usage
- **Setup**: Performance mode, high GPS accuracy, frequent HR sampling
- **Measurements**: Data accuracy vs. battery consumption trade-offs
- **Expected Results**: ≤50% battery usage over 2 hours, <5m GPS accuracy

### Data Collection Templates

#### Battery Usage Log
```
Time    | Battery % | GPS Mode  | HR Interval | Movement  | Notes
--------|-----------|-----------|-------------|-----------|--------
00:00   | 100%      | Balanced  | 5s          | Stationary| Start
00:15   | 97%       | Balanced  | 5s          | Walking   | 
00:30   | 94%       | Balanced  | 5s          | Running   |
...
```

#### Performance Metrics Log
```
Operation        | Count | Avg Time | Max Time | Memory MB | Notes
-----------------|-------|----------|----------|-----------|--------
Session Start    | 1     | 45ms     | 45ms     | 2.1       |
Data Update      | 1800  | 3ms      | 12ms     | 15.3      | 30min
GPS Update       | 180   | 8ms      | 25ms     | 0.5       |
HR Update        | 360   | 2ms      | 8ms      | 0.2       |
```

## Optimization Recommendations

### Development Guidelines

1. **Efficient Data Structures**:
   - Use appropriate collection types for different data access patterns
   - Implement data pooling for frequently created objects
   - Consider compression for large datasets

2. **Background Processing**:
   - Minimize background thread usage
   - Use appropriate thread priorities
   - Implement efficient work scheduling

3. **Memory Management**:
   - Implement proper cleanup in lifecycle methods
   - Use weak references where appropriate
   - Monitor memory leaks during development

### User Experience Guidelines

1. **Battery Notifications**:
   - Alert users when battery optimization is activated
   - Provide clear explanations of mode changes
   - Offer manual override options

2. **Performance Feedback**:
   - Display real-time battery usage estimates
   - Show GPS accuracy and HR confidence levels
   - Provide optimization recommendations

3. **Graceful Degradation**:
   - Maintain core functionality even in power saver mode
   - Clearly communicate feature limitations
   - Provide upgrade paths when battery allows

## Troubleshooting Performance Issues

### Common Performance Problems

#### High Memory Usage
- **Symptoms**: App becomes sluggish, frequent garbage collection
- **Causes**: Memory leaks, inefficient data structures, excessive caching
- **Solutions**: Profile memory usage, implement proper cleanup, optimize data structures

#### Poor Battery Life
- **Symptoms**: Rapid battery drain, overheating
- **Causes**: Excessive GPS polling, inefficient Bluetooth usage, background processing
- **Solutions**: Implement adaptive polling, optimize connection management, reduce background work

#### Slow Response Times
- **Symptoms**: UI lag, delayed data updates
- **Causes**: Main thread blocking, inefficient algorithms, excessive database operations
- **Solutions**: Move work to background threads, optimize algorithms, implement database indexing

### Performance Debugging Tools

#### Built-in Monitoring
```kotlin
class PerformanceMonitor {
    fun measureOperation(name: String, operation: suspend () -> Unit) {
        val start = Clock.System.now()
        operation()
        val duration = Clock.System.now() - start
        logPerformance(name, duration)
    }
}
```

#### Platform-Specific Tools
- **Android**: Systrace, Method Tracing, GPU Profiler
- **iOS**: Instruments, Time Profiler, Energy Impact
- **Kotlin**: Built-in coroutine debugging, memory profiler

## Continuous Performance Monitoring

### Automated Testing
```kotlin
// Performance regression tests
@Test
fun performanceRegressionTest() {
    val baseline = loadPerformanceBaseline()
    val current = measureCurrentPerformance()
    
    assertTrue(current.responseTime <= baseline.responseTime * 1.1) // ≤10% regression
    assertTrue(current.memoryUsage <= baseline.memoryUsage * 1.05)  // ≤5% regression
}
```

### Metrics Collection
- Implement performance metrics collection in release builds
- Monitor key performance indicators in production
- Set up alerts for performance degradation

### Performance Budgets
- Establish performance budgets for key operations
- Monitor budget compliance in CI/CD pipeline
- Block releases that exceed performance thresholds

---

## Performance Test Results Summary

When running the complete performance test suite, expect the following results:

✅ **Session Creation**: Average <100ms, Maximum <500ms  
✅ **Data Updates**: Linear scaling, <10ms per update  
✅ **Memory Stability**: <50MB growth over 2 hours  
✅ **Battery Efficiency**: Meets all optimization targets  
✅ **Background Performance**: <5ms average processing time  

This comprehensive testing ensures PebbleRun meets all performance and battery optimization requirements for reliable workout tracking.
