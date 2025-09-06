#include "hr.h"
#include "common.h"
#include "ui.h"
#include "appmsg.h"

static bool s_hr_monitoring = false;

static void hr_event_handler(HealthEventType event, void *context) {
    if (event == HealthEventHeartRateUpdate) {
        HealthValue hr_value = health_service_peek_current_value(HealthMetricHeartRateBPM);
        
        if (hr_value != HealthValueInvalid) {
            uint16_t hr_bpm = (uint16_t)hr_value;
            
            // Update UI
            ui_update_hr(hr_bpm);
            
            // Send HR data to mobile app
            appmsg_send_hr(hr_bpm);
            
            APP_LOG(APP_LOG_LEVEL_INFO, "HR: %d BPM", hr_bpm);
        } else {
            APP_LOG(APP_LOG_LEVEL_WARNING, "Invalid HR reading");
        }
    }
}

void hr_init(void) {
    // Check if health service is available
    if (!health_service_events_subscribe(hr_event_handler, NULL)) {
        APP_LOG(APP_LOG_LEVEL_ERROR, "Failed to subscribe to health events");
        return;
    }
    
    APP_LOG(APP_LOG_LEVEL_INFO, "HR monitoring initialized");
}

void hr_deinit(void) {
    if (s_hr_monitoring) {
        hr_stop_monitoring();
    }
    
    health_service_events_unsubscribe();
    APP_LOG(APP_LOG_LEVEL_INFO, "HR monitoring deinitialized");
}

void hr_start_monitoring(void) {
    if (s_hr_monitoring) {
        APP_LOG(APP_LOG_LEVEL_WARNING, "HR monitoring already active");
        return;
    }
    
    // Set HR sample period to 1 second for active monitoring
    if (health_service_set_heart_rate_sample_period(1)) {
        s_hr_monitoring = true;
        APP_LOG(APP_LOG_LEVEL_INFO, "HR monitoring started (1s interval)");
    } else {
        APP_LOG(APP_LOG_LEVEL_ERROR, "Failed to set HR sample period");
    }
}

void hr_stop_monitoring(void) {
    if (!s_hr_monitoring) {
        APP_LOG(APP_LOG_LEVEL_WARNING, "HR monitoring not active");
        return;
    }
    
    // Reset HR sample period to default (less frequent)
    health_service_set_heart_rate_sample_period(0);
    s_hr_monitoring = false;
    
    // Clear HR display
    ui_update_hr(0);
    
    APP_LOG(APP_LOG_LEVEL_INFO, "HR monitoring stopped");
}
