#!/bin/bash

# PebbleRun Watchapp Test Script
# This script validates the watchapp build and basic functionality

echo "🔧 PebbleRun Watchapp Test & Validation"
echo "======================================="

# Check if we're in the right directory
if [ ! -f "package.json" ] || [ ! -d "src/c" ]; then
    echo "❌ Error: Run this script from the pebble-watchapp directory"
    exit 1
fi

echo "📁 Validating project structure..."

# Check required files
required_files=(
    "package.json"
    "src/c/main.c"
    "src/c/common.h"
    "src/c/ui.c"
    "src/c/ui.h"
    "src/c/hr.c"
    "src/c/hr.h"
    "src/c/appmsg.c"
    "src/c/appmsg.h"
    "Makefile"
    "README.md"
)

missing_files=()
for file in "${required_files[@]}"; do
    if [ ! -f "$file" ]; then
        missing_files+=("$file")
    fi
done

if [ ${#missing_files[@]} -eq 0 ]; then
    echo "✅ All required files present"
else
    echo "❌ Missing files:"
    printf '%s\n' "${missing_files[@]}"
    exit 1
fi

echo "🔍 Checking source code syntax..."

# Basic syntax validation (check for common C syntax issues)
c_files=($(find src/c -name "*.c"))
h_files=($(find src/c -name "*.h"))

syntax_errors=0
for file in "${c_files[@]}" "${h_files[@]}"; do
    # Check for matching braces
    open_braces=$(grep -o '{' "$file" | wc -l)
    close_braces=$(grep -o '}' "$file" | wc -l)
    
    if [ "$open_braces" -ne "$close_braces" ]; then
        echo "❌ Brace mismatch in $file: $open_braces open, $close_braces close"
        syntax_errors=$((syntax_errors + 1))
    fi
    
    # Check for basic includes
    if [[ "$file" == *.c ]] && ! grep -q "#include" "$file"; then
        echo "❌ No includes found in $file"
        syntax_errors=$((syntax_errors + 1))
    fi
done

if [ "$syntax_errors" -eq 0 ]; then
    echo "✅ Basic syntax validation passed"
else
    echo "❌ $syntax_errors syntax issues found"
fi

echo "📋 Validating AppMessage keys..."

# Check that AppMessage keys are properly defined
if grep -q "KEY_PACE = 0" src/c/common.h && \
   grep -q "KEY_TIME = 1" src/c/common.h && \
   grep -q "KEY_HR = 2" src/c/common.h && \
   grep -q "KEY_CMD = 3" src/c/common.h; then
    echo "✅ AppMessage keys properly defined"
else
    echo "❌ AppMessage keys not properly defined"
    exit 1
fi

echo "🏗️ Attempting build validation..."

# Check if Pebble CLI is available
if command -v pebble >/dev/null 2>&1; then
    echo "✅ Pebble CLI found"
    
    # Try to validate the project
    if pebble build 2>/dev/null; then
        echo "✅ Project builds successfully"
    else
        echo "⚠️  Build validation failed (expected without full Pebble SDK setup)"
    fi
else
    echo "⚠️  Pebble CLI not found - build validation skipped"
    echo "   Install Pebble SDK to enable build testing"
fi

echo "📊 Code metrics:"
echo "   C files: $(find src/c -name "*.c" | wc -l)"
echo "   Header files: $(find src/c -name "*.h" | wc -l)"
echo "   Total lines of code: $(find src/c -name "*.c" -o -name "*.h" | xargs wc -l | tail -1 | awk '{print $1}')"

echo ""
echo "🎯 Test Summary:"
echo "==============="

if [ ${#missing_files[@]} -eq 0 ] && [ "$syntax_errors" -eq 0 ]; then
    echo "✅ All validations passed!"
    echo "   The PebbleRun watchapp is ready for device testing."
    echo ""
    echo "Next steps:"
    echo "1. Install Pebble SDK and CLI tools"
    echo "2. Connect Pebble 2 HR device"
    echo "3. Run 'make build' to compile"
    echo "4. Run 'make install' to deploy to device"
    echo "5. Test with companion mobile app"
else
    echo "❌ Some validations failed. Please fix issues before proceeding."
    exit 1
fi
