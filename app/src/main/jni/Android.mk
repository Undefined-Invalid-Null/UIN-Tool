# app/src/main/jni/Android.mk
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := pty
LOCAL_SRC_FILES := pty_launcher.c
LOCAL_LDLIBS := -llog
LOCAL_CFLAGS := -std=c17 -Wall -Wextra -Os -Wno-unused-parameter

include $(BUILD_SHARED_LIBRARY)