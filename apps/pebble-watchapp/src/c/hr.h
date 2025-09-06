#pragma once

#include <pebble.h>

// HR monitoring functions
void hr_init(void);
void hr_deinit(void);
void hr_start_monitoring(void);
void hr_stop_monitoring(void);

// HR event callback type
typedef void (*HRCallback)(uint16_t hr_bpm);
