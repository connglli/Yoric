//
// Created by Simon on 2020/4/7.
//

#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <sys/file.h>
#include <sys/wait.h>
#include <unistd.h>

#include <jni.h>

#include <android/log.h>

#define TAG "Yoric"
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, TAG, ## __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, ## __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, ## __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, ## __VA_ARGS__)

#define MAX_COUNT 128

static char global_self_flag[MAX_COUNT];

void set_process_name(JNIEnv* env, const char* name) {
    jclass process = (*env)->FindClass(env, "android/os/Process");
    jmethodID setArgV0 = (*env)->GetStaticMethodID(env, process, "setArgV0", "(Ljava/lang/String;)V");
    (*env)->CallStaticVoidMethod(env, process, setArgV0, (*env)->NewStringUTF(env, name));
}

void touch_file(const char* path) {
    FILE* fp = fopen(path, "ab+");
    if (fp) {
        fclose(fp);
    }
}

int lock_file(const char* path) {
    int fd = open(path, O_RDONLY);
    if (fd < 0 ) {
        LOGE("open >> %s << failed", path);
        return 0;
    }
    int try = 0, count = 3;
    while (flock(fd, LOCK_EX) && try < count) {
        LOGW("lock (%d/%d) >> %s << failed", try + 1, count, path);
        try += 1;
    }
    if (try >= count) {
        LOGE("lock (%d/%d) exceeded max count", try, count);
        return 1;
    }
    LOGI("file >> %s << locked", path);
    return 1;
}

void watch_file(const char* path) {
    int fd = open(path, O_RDONLY);
    while (flock(fd, LOCK_EX)) {
        usleep(10000);
    }
}

void delete_file(const char* path) {
    remove(path);
}

void tell_and_wait4_him(const char* self_name, const char* self_flag,
                        const char* his_name, const char* his_flag) {
    // tell him we are ready
    touch_file(self_flag);
    LOGI("watcher >> %s << ready", self_name);

    // wait until he is ready
    int fd;
    while ((fd = open(his_flag, O_RDONLY)) < 0) {
        LOGW("waiting for >> %s <<", his_name);
        usleep(10000);
    }
    close(fd);

    // as we know he is ready, delete his flag
    // to maintain atomic characteristic
    delete_file(his_flag);
}

void setup_and_watch(const char* self_name, const char* self_path, const char* self_flag,
                     const char* his_name, const char* his_path, const char* his_flag) {
    // create self
    touch_file(self_path);

    // lock self
    if (!lock_file(self_path)) {
        LOGE("cannot lock  >> %s <<", self_name);
        return;
    }

    // tell and wait for him
    tell_and_wait4_him(self_name, self_flag, his_name, his_flag);
    LOGI("watching: %s :=> %s", self_name, his_name);

    // watch him
    watch_file(his_path);
    LOGE("process >> %s <<  killed (watcher >> %s <<)", his_name, self_name);

    // delete self
    delete_file(self_flag);
}

void callback_to_restart(JNIEnv* env, jobject yoric_native,
                         const char* method_name, const char* method_sig) {
    jclass YoricNative = (*env)->GetObjectClass(env, yoric_native);
    jmethodID method_id = (*env)->GetMethodID(env, YoricNative, method_name, method_sig);
    (*env)->CallVoidMethod(env, yoric_native, method_id);
}

JNIEXPORT void JNICALL
Java_com_example_yoric_YoricNative_doPrepare(JNIEnv *env, jobject thiz,
        jstring selfName, jstring hisName,
        jstring selfPath, jstring hisPath,
        jstring selfFlag, jstring hisFlag) {
    pid_t ret = fork();
    if (ret == 0) { // child
        ret = fork();
        if (ret > 0) { // self
            exit(0);
        } else if (ret < 0) { // assert
            LOGE(TAG, "Fork failed");
            exit(-1);
        }

        // child
        const char* parent_name = (*env)->GetStringUTFChars(env, selfName, 0);
        const char* parent_his_name = (*env)->GetStringUTFChars(env, hisName, 0);
        const char* parent_path = (*env)->GetStringUTFChars(env, selfPath, 0);
        const char* parent_his_path = (*env)->GetStringUTFChars(env, hisPath, 0);
        const char* parent_flag = (*env)->GetStringUTFChars(env, selfFlag, 0);
        const char* parent_his_flag = (*env)->GetStringUTFChars(env, hisFlag, 0);

        char self_name[MAX_COUNT];
        char his_name[MAX_COUNT];
        char self_path[MAX_COUNT];
        char his_path[MAX_COUNT];
        char self_flag[MAX_COUNT];
        char his_flag[MAX_COUNT];

        strcpy(self_name, parent_name);
        strcat(self_name, "-c");
        strcpy(his_name, parent_his_name);
        strcat(his_name, "-c");
        strcpy(self_path, parent_path);
        strcat(self_path, "-c");
        strcpy(his_path, parent_his_path);
        strcat(his_path, "-c");
        strcpy(self_flag, parent_flag);
        strcat(self_flag, "-c");
        strcpy(his_flag, parent_his_flag);
        strcat(his_flag, "-c");

        strcpy(global_self_flag, self_flag);

        set_process_name(env, self_name);
        setup_and_watch(self_name, self_path, self_flag, his_name, his_path, his_flag);
        callback_to_restart(env, thiz, "onProcessDead", "()V");

        return;
    } else if (ret < 0) { // assert
        LOGE(TAG, "fork failed");
        exit(-1);
    }

    // self
    const char* self_name = (*env)->GetStringUTFChars(env, selfName, 0);
    const char* his_name = (*env)->GetStringUTFChars(env, hisName, 0);
    const char* self_path = (*env)->GetStringUTFChars(env, selfPath, 0);
    const char* his_path = (*env)->GetStringUTFChars(env, hisPath, 0);
    const char* self_flag = (*env)->GetStringUTFChars(env, selfFlag, 0);
    const char* his_flag = (*env)->GetStringUTFChars(env, hisFlag, 0);

    strcpy(global_self_flag, self_flag);

    if (waitpid(ret, NULL, 0) != ret) {
        LOGW("waitpid failed");
    }

    setup_and_watch(self_name, self_path, self_flag, his_name, his_path, his_flag);
    callback_to_restart(env, thiz, "onProcessDead", "()V");
}
