#include "appmsg.h"
#include "common.h"
#include "ui.h"
#include "hr.h"

// Buffer sizes for AppMessage
#define OUTBOX_SIZE 64
#define INBOX_SIZE 128

static void inbox_received_callback(DictionaryIterator *iterator, void *context) {
    APP_LOG(APP_LOG_LEVEL_INFO, "AppMessage received");
    
    // Process incoming messages
    Tuple *pace_tuple = dict_find(iterator, KEY_PACE);
    if (pace_tuple && pace_tuple->type == TUPLE_CSTRING) {
        appmsg_handle_pace_update(pace_tuple->value->cstring);
    }
    
    Tuple *time_tuple = dict_find(iterator, KEY_TIME);
    if (time_tuple && time_tuple->type == TUPLE_CSTRING) {
        appmsg_handle_time_update(time_tuple->value->cstring);
    }
    
    Tuple *cmd_tuple = dict_find(iterator, KEY_CMD);
    if (cmd_tuple && cmd_tuple->type == TUPLE_UINT8) {
        appmsg_handle_command(cmd_tuple->value->uint8);
    }
}

static void inbox_dropped_callback(AppMessageResult reason, void *context) {
    APP_LOG(APP_LOG_LEVEL_ERROR, "AppMessage inbox dropped: %d", reason);
}

static void outbox_sent_callback(DictionaryIterator *iterator, void *context) {
    APP_LOG(APP_LOG_LEVEL_DEBUG, "AppMessage sent successfully");
}

static void outbox_failed_callback(DictionaryIterator *iterator, AppMessageResult reason, void *context) {
    APP_LOG(APP_LOG_LEVEL_ERROR, "AppMessage send failed: %d", reason);
}

void appmsg_init(void) {
    // Open AppMessage with defined buffer sizes
    app_message_register_inbox_received(inbox_received_callback);
    app_message_register_inbox_dropped(inbox_dropped_callback);
    app_message_register_outbox_sent(outbox_sent_callback);
    app_message_register_outbox_failed(outbox_failed_callback);
    
    AppMessageResult result = app_message_open(INBOX_SIZE, OUTBOX_SIZE);
    if (result == APP_MSG_OK) {
        APP_LOG(APP_LOG_LEVEL_INFO, "AppMessage initialized successfully");
    } else {
        APP_LOG(APP_LOG_LEVEL_ERROR, "AppMessage initialization failed: %d", result);
    }
}

void appmsg_deinit(void) {
    app_message_deregister_callbacks();
    APP_LOG(APP_LOG_LEVEL_INFO, "AppMessage deinitialized");
}

void appmsg_send_hr(uint16_t hr_bpm) {
    DictionaryIterator *iter;
    AppMessageResult result = app_message_outbox_begin(&iter);
    
    if (result == APP_MSG_OK) {
        result = dict_write_uint16(iter, KEY_HR, hr_bpm);
        if (result == DICT_OK) {
            result = app_message_outbox_send();
            if (result != APP_MSG_OK) {
                APP_LOG(APP_LOG_LEVEL_ERROR, "Failed to send HR message: %d", result);
            }
        } else {
            APP_LOG(APP_LOG_LEVEL_ERROR, "Failed to write HR to dictionary: %d", result);
        }
    } else {
        APP_LOG(APP_LOG_LEVEL_ERROR, "Failed to begin outbox: %d", result);
    }
}

void appmsg_handle_command(uint8_t cmd) {
    APP_LOG(APP_LOG_LEVEL_INFO, "Received command: %d", cmd);
    
    switch (cmd) {
        case CMD_START:
            APP_LOG(APP_LOG_LEVEL_INFO, "Starting workout session");
            ui_show_window();
            hr_start_monitoring();
            break;
            
        case CMD_STOP:
            APP_LOG(APP_LOG_LEVEL_INFO, "Stopping workout session");
            hr_stop_monitoring();
            ui_hide_window();
            // Return to default watchface by removing all windows
            window_stack_pop_all(false);
            break;
            
        default:
            APP_LOG(APP_LOG_LEVEL_WARNING, "Unknown command: %d", cmd);
            break;
    }
}

void appmsg_handle_pace_update(const char* pace) {
    if (pace) {
        APP_LOG(APP_LOG_LEVEL_DEBUG, "Pace update: %s", pace);
        ui_update_pace(pace);
    }
}

void appmsg_handle_time_update(const char* time) {
    if (time) {
        APP_LOG(APP_LOG_LEVEL_DEBUG, "Time update: %s", time);
        ui_update_time(time);
    }
}
