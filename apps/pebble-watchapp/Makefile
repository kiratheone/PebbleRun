# PebbleRun Watchapp Makefile

# Project configuration
PROJECT_NAME = pebblerun-watchapp
UUID = a8c7e0f1-2d4e-4a6b-8c9e-1f3a5b7d9e0f

# Pebble SDK configuration
PEBBLE_SDK ?= $(HOME)/pebble-dev/pebble-sdk
PLATFORM ?= basalt

# Source files
SRC_DIR = src/c
SOURCES = $(wildcard $(SRC_DIR)/*.c)

# Build directory
BUILD_DIR = build

# Default target
all: build

# Build the watchapp
build:
	@echo "Building PebbleRun watchapp..."
	@if command -v pebble >/dev/null 2>&1; then \
		pebble build; \
	else \
		echo "Pebble CLI not found. Please install Pebble SDK and CLI tools."; \
		echo "Visit: https://developer.pebble.com/sdk/"; \
		exit 1; \
	fi

# Install on connected device
install:
	@echo "Installing PebbleRun watchapp..."
	@pebble install

# Clean build artifacts
clean:
	@echo "Cleaning build artifacts..."
	@rm -rf $(BUILD_DIR)
	@if command -v pebble >/dev/null 2>&1; then \
		pebble clean; \
	fi

# Show logs from connected device
logs:
	@echo "Showing logs from Pebble device..."
	@pebble logs

# Help
help:
	@echo "PebbleRun Watchapp Build System"
	@echo ""
	@echo "Available targets:"
	@echo "  build    - Build the watchapp"
	@echo "  install  - Install on connected Pebble device"
	@echo "  clean    - Clean build artifacts"
	@echo "  logs     - Show logs from connected device"
	@echo "  help     - Show this help message"
	@echo ""
	@echo "Requirements:"
	@echo "  - Pebble SDK and CLI tools"
	@echo "  - Connected Pebble device (for install/logs)"

.PHONY: all build install clean logs help
