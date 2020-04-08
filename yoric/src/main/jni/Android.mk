LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE     := yoric
LOCAL_SRC_FILES  := yoric.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_LDLIBS     := -L$(SYSROOT)/usr/lib -llog

include $(BUILD_SHARED_LIBRARY)