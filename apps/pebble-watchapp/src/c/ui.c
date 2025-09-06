#include "ui.h"
#include "common.h"

// UI elements
static Window *s_main_window;
static Layer *s_canvas_layer;

// Text elements for display
static GFont s_font_hr;
static GFont s_font_data;

// Colors and styling
#define COLOR_HR GColorRed
#define COLOR_PACE GColorWhite
#define COLOR_TIME GColorLightGray
#define COLOR_BACKGROUND GColorBlack

static void canvas_update_proc(Layer *layer, GContext *ctx) {
    GRect bounds = layer_get_bounds(layer);
    
    // Set background
    graphics_context_set_fill_color(ctx, COLOR_BACKGROUND);
    graphics_fill_rect(ctx, bounds, 0, GCornerNone);
    
    // HR display (large, center-top)
    graphics_context_set_text_color(ctx, COLOR_HR);
    char hr_text[16];
    if (g_app_state.current_hr > 0) {
        snprintf(hr_text, sizeof(hr_text), "%d BPM", g_app_state.current_hr);
    } else {
        snprintf(hr_text, sizeof(hr_text), "-- BPM");
    }
    
    GRect hr_rect = GRect(0, 20, bounds.size.w, 40);
    graphics_draw_text(ctx, hr_text, s_font_hr, hr_rect,
                      GTextOverflowModeWordWrap, GTextAlignmentCenter, NULL);
    
    // Pace display (medium, center-middle)
    graphics_context_set_text_color(ctx, COLOR_PACE);
    GRect pace_rect = GRect(0, 70, bounds.size.w, 30);
    graphics_draw_text(ctx, g_app_state.pace_text, s_font_data, pace_rect,
                      GTextOverflowModeWordWrap, GTextAlignmentCenter, NULL);
    
    // Time display (medium, center-bottom)
    graphics_context_set_text_color(ctx, COLOR_TIME);
    GRect time_rect = GRect(0, 110, bounds.size.w, 30);
    graphics_draw_text(ctx, g_app_state.time_text, s_font_data, time_rect,
                      GTextOverflowModeWordWrap, GTextAlignmentCenter, NULL);
    
    // Status indicator
    if (g_app_state.is_active) {
        graphics_context_set_fill_color(ctx, GColorGreen);
        graphics_fill_circle(ctx, GPoint(bounds.size.w - 10, 10), 3);
    }
}

static void main_window_load(Window *window) {
    Layer *window_layer = window_get_root_layer(window);
    GRect bounds = layer_get_bounds(window_layer);
    
    // Create canvas layer
    s_canvas_layer = layer_create(bounds);
    layer_set_update_proc(s_canvas_layer, canvas_update_proc);
    layer_add_child(window_layer, s_canvas_layer);
    
    // Load fonts
    s_font_hr = fonts_get_system_font(FONT_KEY_GOTHIC_28_BOLD);
    s_font_data = fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD);
}

static void main_window_unload(Window *window) {
    // Destroy canvas layer
    layer_destroy(s_canvas_layer);
}

void ui_init(void) {
    // Create main window
    s_main_window = window_create();
    
    // Set window properties
    window_set_background_color(s_main_window, COLOR_BACKGROUND);
    window_set_window_handlers(s_main_window, (WindowHandlers) {
        .load = main_window_load,
        .unload = main_window_unload,
    });
    
    APP_LOG(APP_LOG_LEVEL_INFO, "UI initialized");
}

void ui_deinit(void) {
    // Destroy main window
    if (s_main_window) {
        window_destroy(s_main_window);
    }
}

void ui_update_hr(uint16_t hr) {
    g_app_state.current_hr = hr;
    if (s_canvas_layer) {
        layer_mark_dirty(s_canvas_layer);
    }
}

void ui_update_pace(const char* pace) {
    if (pace) {
        strncpy(g_app_state.pace_text, pace, sizeof(g_app_state.pace_text) - 1);
        g_app_state.pace_text[sizeof(g_app_state.pace_text) - 1] = '\0';
        if (s_canvas_layer) {
            layer_mark_dirty(s_canvas_layer);
        }
    }
}

void ui_update_time(const char* time) {
    if (time) {
        strncpy(g_app_state.time_text, time, sizeof(g_app_state.time_text) - 1);
        g_app_state.time_text[sizeof(g_app_state.time_text) - 1] = '\0';
        if (s_canvas_layer) {
            layer_mark_dirty(s_canvas_layer);
        }
    }
}

void ui_show_window(void) {
    if (s_main_window) {
        window_stack_push(s_main_window, true);
        g_app_state.is_active = true;
        if (s_canvas_layer) {
            layer_mark_dirty(s_canvas_layer);
        }
    }
}

void ui_hide_window(void) {
    if (s_main_window) {
        window_stack_remove(s_main_window, true);
        g_app_state.is_active = false;
    }
}
