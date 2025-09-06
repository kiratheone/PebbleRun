#pragma once

#include <pebble.h>

// AppMessage functions
void appmsg_init(void);
void appmsg_deinit(void);

// Send functions
void appmsg_send_hr(uint16_t hr_bpm);

// Message handling
void appmsg_handle_command(uint8_t cmd);
void appmsg_handle_pace_update(const char* pace);
void appmsg_handle_time_update(const char* time);
