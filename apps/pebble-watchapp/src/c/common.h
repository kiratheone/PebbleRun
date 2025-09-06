#pragma once

// AppMessage keys (must match mobile app)
typedef enum {
    KEY_PACE = 0,
    KEY_TIME = 1,
    KEY_HR = 2,
    KEY_CMD = 3
} AppMessageKey;

// Commands
typedef enum {
    CMD_START = 1,
    CMD_STOP = 2
} Command;

// App state
typedef struct {
    bool is_active;
    uint16_t current_hr;
    char pace_text[16];
    char time_text[16];
} AppState;

// Global app state
extern AppState g_app_state;
