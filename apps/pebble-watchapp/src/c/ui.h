#pragma once

#include <pebble.h>

// UI initialization and cleanup
void ui_init(void);
void ui_deinit(void);

// Update display functions
void ui_update_hr(uint16_t hr);
void ui_update_pace(const char* pace);
void ui_update_time(const char* time);

// Window management
void ui_show_window(void);
void ui_hide_window(void);
