# End-to-End Testing with Real Pebble Device

This document outlines the procedures for testing the PebbleRun companion app with actual Pebble 2 HR devices.

## Prerequisites

### Hardware Requirements
- **Pebble 2 HR device** with firmware 4.3 or later
- **Android device** running Android 8.0+ OR **iOS device** running iOS 13.0+
- **USB cable** for Pebble charging and development
- **Computer** with Pebble SDK 4.3+ installed

### Software Requirements
- Pebble SDK development environment set up
- PebbleRun companion app installed on mobile device
- PebbleRun watchapp compiled and ready for installation
- Testing framework and logging tools

## Pre-Test Setup

### 1. Pebble Device Preparation
```bash
# Ensure Pebble is in developer mode
pebble ping

# Install the PebbleRun watchapp
cd pebble-watchapp
pebble install --phone YOUR_PHONE_IP
```

### 2. Mobile App Preparation
- Install the PebbleRun companion app
- Grant all required permissions:
  - Location access (precise location)
  - Bluetooth permissions
  - Background app refresh (iOS)
  - Battery optimization exemption (Android)

### 3. Environment Setup
- Ensure stable Bluetooth connection
- Have GPS signal available (outdoor testing recommended)
- Charge both devices to at least 80%
- Clear any previous test data

## Test Scenarios

### Test 1: Basic Connection and Communication

**Objective**: Verify Pebble device connects and communicates with companion app.

**Steps**:
1. Open PebbleRun companion app
2. Verify Pebble appears in device list
3. Tap to connect
4. Confirm connection status in app
5. Launch PebbleRun watchapp from companion app
6. Verify watchapp launches on Pebble
7. Send test message from app to watch
8. Verify message appears on watch display

**Expected Results**:
- Connection establishes within 10 seconds
- Watchapp launches successfully
- Messages transmit in both directions
- No connection errors in logs

**Success Criteria**:
- [ ] Pebble connects successfully
- [ ] Watchapp launches from companion app
- [ ] Bidirectional communication works
- [ ] Connection remains stable for 2+ minutes

### Test 2: Heart Rate Monitoring

**Objective**: Verify accurate heart rate data collection and transmission.

**Steps**:
1. Ensure Pebble 2 HR is properly positioned on wrist
2. Connect and launch watchapp
3. Start a workout session in companion app
4. Wait for HR sensor to initialize (30-60 seconds)
5. Monitor HR readings on both watch and phone
6. Perform light exercise to vary HR
7. Record readings for 5 minutes
8. Stop workout and analyze data

**Expected Results**:
- HR sensor activates within 60 seconds
- Readings appear every 1-2 seconds
- Values are within reasonable range (40-220 BPM)
- Data synchronizes between watch and phone
- No significant gaps in data

**Success Criteria**:
- [ ] HR sensor initializes successfully
- [ ] Readings are physiologically reasonable
- [ ] Data transmission rate ≥ 0.5 Hz
- [ ] <5% data loss during 5-minute test
- [ ] Readings match manual pulse check (±10 BPM)

### Test 3: GPS Integration and Pace Calculation

**Objective**: Verify GPS tracking and real-time pace calculation.

**Prerequisites**: Outdoor location with clear sky view

**Steps**:
1. Start in outdoor location with good GPS signal
2. Connect Pebble and launch watchapp
3. Start workout session
4. Wait for GPS lock (companion app indicator)
5. Begin walking/running at steady pace
6. Monitor real-time pace on both devices
7. Change pace and verify updates
8. Complete 1km route and verify distance
9. Stop workout and analyze GPS track

**Expected Results**:
- GPS lock within 60 seconds
- Pace updates every 10-15 seconds
- Distance calculation accuracy within 5%
- Route tracking follows actual path
- Pace values are reasonable for activity

**Success Criteria**:
- [ ] GPS lock achieved within 60 seconds
- [ ] Real-time pace updates visible
- [ ] Distance accuracy within 5% of known route
- [ ] GPS track follows actual path
- [ ] No significant GPS drift or jumps

### Test 4: Background Operation and Battery Life

**Objective**: Verify app continues functioning when backgrounded.

**Steps**:
1. Start workout session with HR and GPS active
2. Background the companion app
3. Continue workout for 30 minutes
4. Check watch periodically for updates
5. Return to app and verify data continuity
6. Monitor battery drain on both devices
7. Test with phone screen off
8. Test with other apps running

**Expected Results**:
- Background operation continues uninterrupted
- Data collection maintains accuracy
- Battery drain is reasonable
- No performance degradation
- Notifications work properly

**Success Criteria**:
- [ ] Background operation works for 30+ minutes
- [ ] Data collection continues seamlessly
- [ ] Battery drain <20% per hour
- [ ] No missed HR or GPS readings
- [ ] App resumes properly when foregrounded

### Test 5: Error Recovery and Edge Cases

**Objective**: Test system robustness under adverse conditions.

**Steps**:
1. **Connection Interruption Test**:
   - Start workout, then walk away from phone
   - Verify recovery when returning
   
2. **Low Battery Test**:
   - Test behavior when Pebble battery <20%
   - Test behavior when phone battery <10%
   
3. **Poor GPS Test**:
   - Test in challenging GPS environment (buildings)
   - Verify graceful degradation
   
4. **HR Sensor Interference**:
   - Test with loose watch fit
   - Test with tattoos/dark skin
   - Verify confidence reporting

**Expected Results**:
- Graceful handling of all error conditions
- Appropriate user notifications
- Data integrity maintained
- System recovery without data loss

**Success Criteria**:
- [ ] Bluetooth reconnection works automatically
- [ ] Low battery warnings appear appropriately
- [ ] Poor GPS handled gracefully
- [ ] HR confidence tracking works
- [ ] No data corruption during errors

### Test 6: Long Duration Workout

**Objective**: Verify system stability during extended use.

**Prerequisites**: Devices charged to 100%

**Steps**:
1. Start workout session
2. Maintain activity for 60+ minutes
3. Monitor for memory leaks
4. Check data quality over time
5. Verify battery usage is sustainable
6. Test stop/resume functionality
7. Analyze final workout data

**Expected Results**:
- Stable operation for full duration
- Consistent data quality
- Reasonable battery consumption
- All data successfully saved

**Success Criteria**:
- [ ] 60-minute workout completes successfully
- [ ] Memory usage remains stable
- [ ] Data quality doesn't degrade over time
- [ ] Battery allows for 2+ hour workouts
- [ ] All workout data saves correctly

## Data Quality Verification

### Heart Rate Data Quality
- **Accuracy**: Compare against chest strap HR monitor
- **Consistency**: No sudden spikes or dropouts
- **Coverage**: <5% missing readings during active periods
- **Confidence**: Majority of readings with confidence >0.8

### GPS Data Quality
- **Accuracy**: Distance within 5% of known routes
- **Precision**: Track follows actual path
- **Continuity**: No significant gaps or jumps
- **Performance**: Updates every 5-10 seconds

### Pace Calculation Quality
- **Accuracy**: Compare against stopwatch over known distance
- **Responsiveness**: Updates reflect actual pace changes
- **Smoothing**: Reasonable filtering of GPS noise
- **Display**: Values update on watch within 15 seconds

## Performance Benchmarks

### Connection Performance
- Initial connection: <10 seconds
- Watchapp launch: <5 seconds
- Message latency: <2 seconds
- Reconnection after interruption: <15 seconds

### Data Collection Performance
- HR reading frequency: ≥0.5 Hz
- GPS update frequency: ≥0.1 Hz (every 10 seconds)
- Data transmission latency: <5 seconds
- Background data collection: Continuous

### Battery Performance
- Pebble 2 HR: 6+ hours continuous use
- Companion app: <15% battery per hour
- Standby drain: <2% per hour
- Total workout capacity: 4+ hours

## Test Documentation

### Required Logs
- Bluetooth connection logs
- HR sensor data with timestamps
- GPS coordinate logs with accuracy
- App performance metrics
- Battery usage statistics
- Error logs and recovery actions

### Test Report Template
```
Date: [Date]
Duration: [Duration]
Devices: [Pebble S/N] + [Phone Model]
Weather: [Conditions]
Location: [GPS coordinates]

Results:
- Connection Success: [Pass/Fail]
- HR Data Quality: [Pass/Fail] - [Average confidence]
- GPS Accuracy: [Pass/Fail] - [Distance error %]
- Battery Performance: [Pass/Fail] - [Usage %]
- Error Recovery: [Pass/Fail] - [Incidents]

Issues Found:
[List any problems encountered]

Data Files:
[Paths to saved workout data, logs, screenshots]
```

## Automation Scripts

### Setup Script
```bash
#!/bin/bash
# Pre-test setup automation
echo "Setting up PebbleRun test environment..."
pebble kill
pebble install
adb logcat -c  # Clear Android logs
# iOS: xcrun simctl spawn booted log erase
echo "Test environment ready"
```

### Data Collection Script
```bash
#!/bin/bash
# Automated data collection during tests
mkdir -p test_results/$(date +%Y%m%d_%H%M%S)
adb logcat > test_results/android_logs.txt &
# Start screen recording for manual verification
adb shell screenrecord /sdcard/test_recording.mp4 &
echo "Data collection started"
```

## Success Criteria Summary

A test passes if:
- ✅ All basic functionality works as specified
- ✅ Data quality meets accuracy requirements
- ✅ Performance meets benchmark targets
- ✅ Error conditions are handled gracefully
- ✅ Battery life meets usage requirements
- ✅ User experience is smooth and responsive

## Known Limitations

- GPS accuracy varies by location and weather
- HR sensor accuracy depends on fit and skin tone
- Bluetooth range limited to ~10 meters
- Battery life varies with screen brightness and usage
- Cold weather can affect touch responsiveness

## Troubleshooting

### Common Issues
1. **Connection fails**: Check Bluetooth settings, restart both devices
2. **HR not reading**: Ensure proper watch fit, clean sensor
3. **GPS poor accuracy**: Move to open area, wait for satellite lock
4. **App crashes**: Check logs, verify permissions granted
5. **Battery drains quickly**: Check background app settings

### Debug Commands
```bash
# Check Pebble connection
pebble ping

# View Pebble logs
pebble logs

# Android debugging
adb logcat | grep PebbleRun

# iOS debugging
xcrun simctl spawn booted log stream --predicate 'subsystem contains "PebbleRun"'
```
