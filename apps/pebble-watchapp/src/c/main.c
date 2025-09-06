#include <pebble.h>
#include "common.h"
#include "ui.h"
#include "hr.h"
#include "appmsg.h"

// Global app state
AppState g_app_state = {
    .is_active = false,
    .current_hr = 0,
    .pace_text = "--:--/km",
    .time_text = "00:00:00"
};

static void init(void) {
    // Initialize UI
    ui_init();
    
    // Initialize heart rate monitoring
    hr_init();
    
    // Initialize AppMessage
    appmsg_init();
    
    APP_LOG(APP_LOG_LEVEL_INFO, "PebbleRun initialized");
}

static void deinit(void) {
    // Cleanup resources
    appmsg_deinit();
    hr_deinit();
    ui_deinit();
    
    APP_LOG(APP_LOG_LEVEL_INFO, "PebbleRun deinitialized");
}

int main(void) {
    init();
    app_event_loop();
    deinit();
    
    return 0;
}
