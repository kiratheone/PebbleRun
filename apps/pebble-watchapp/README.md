# PebbleRun Watchapp

A C-based watchapp for Pebble 2 HR that provides real-time heart rate monitoring and workout tracking display.

## Features

- Real-time heart rate monitoring using Health API
- Display of pace and duration from mobile app
- AppMessage communication with companion mobile app
- Auto-launch/close functionality

## Build Requirements

- Pebble SDK 4.3+
- Pebble CLI tools
- Health API support (Pebble 2 HR)

## Development

```bash
# Install dependencies (if using Pebble Tool)
pebble install

# Build for emulator
pebble build

# Install on connected Pebble device
pebble install --phone [phone_ip]

# Check logs
pebble logs --phone [phone_ip]
```

## AppMessage Protocol

| Key | Type | Direction | Description |
|-----|------|-----------|-------------|
| 0 (PACE) | string | Mobile → Pebble | Pace in "mm:ss/km" format |
| 1 (TIME) | string | Mobile → Pebble | Duration in "HH:MM:SS" format |
| 2 (HR) | uint16 | Pebble → Mobile | Heart rate in BPM |
| 3 (CMD) | uint8 | Mobile → Pebble | Commands: 1=START, 2=STOP |

## Architecture

- `main.c` - App lifecycle and initialization
- `ui.c` - User interface and display management
- `hr.c` - Heart rate sensor integration
- `appmsg.c` - AppMessage communication layer
