// app/src/main/jni/pty_launcher.c
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/wait.h>
#include <sys/ioctl.h>
#include <fcntl.h>
#include <pty.h>
#include <termios.h>
#include <errno.h>
#include <android/log.h>
#include <sys/utsname.h>

#define LOG_TAG "PtyLauncher"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 获取 linker 路径
static const char* get_linker_path() {
    struct utsname uts;
    uname(&uts);
    // 64位系统使用 linker64，32位使用 linker
    if (strstr(uts.machine, "aarch64") || strstr(uts.machine, "x86_64")) {
        return "/system/bin/linker64";
    }
    return "/system/bin/linker";
}

JNIEXPORT jintArray JNICALL
Java_com_UIN_Tool_terminal_PtyJNI_createPty(
    JNIEnv* env,
    jobject thiz,
    jstring cmd,
    jstring cwd,
    jobjectArray args,
    jobjectArray envVars,
    jint rows,
    jint cols
) {
    (void)thiz;
    
    const char* cmdStr = (*env)->GetStringUTFChars(env, cmd, NULL);
    const char* cwdStr = (*env)->GetStringUTFChars(env, cwd, NULL);
    
    // 构建 argv (通过 linker 执行)
    int argc = args ? (*env)->GetArrayLength(env, args) : 0;
    // 额外参数: linker + cmd + args
    int totalArgc = argc + 2;  // linker + cmd + args
    char** argv = (char**)malloc((totalArgc + 1) * sizeof(char*));
    
    int idx = 0;
    const char* linker = get_linker_path();
    argv[idx++] = (char*)linker;      // 使用 linker
    argv[idx++] = (char*)cmdStr;       // bash 路径作为参数
    
    for (int i = 0; i < argc; i++) {
        jstring s = (jstring)(*env)->GetObjectArrayElement(env, args, i);
        argv[idx++] = (char*)(*env)->GetStringUTFChars(env, s, NULL);
    }
    argv[idx] = NULL;
    
    LOGI("Linker: %s", linker);
    LOGI("Cmd: %s", cmdStr);
    
    // 构建环境变量
    int envCount = envVars ? (*env)->GetArrayLength(env, envVars) : 0;
    char** envp = (char**)malloc((envCount + 1) * sizeof(char*));
    for (int i = 0; i < envCount; i++) {
        jstring s = (jstring)(*env)->GetObjectArrayElement(env, envVars, i);
        envp[i] = (char*)(*env)->GetStringUTFChars(env, s, NULL);
    }
    envp[envCount] = NULL;
    
    // 创建 PTY
    int master, slave;
    struct winsize ws = { (unsigned short)rows, (unsigned short)cols, 0, 0 };
    struct termios term;
    tcgetattr(STDIN_FILENO, &term);
    
    if (openpty(&master, &slave, NULL, &term, &ws) == -1) {
        LOGE("openpty 失败: %s", strerror(errno));
        return NULL;
    }
    
    LOGI("PTY 创建成功: master=%d, slave=%d", master, slave);
    
    pid_t pid = fork();
    
    if (pid == 0) {
        // 子进程
        setsid();
        dup2(slave, 0);
        dup2(slave, 1);
        dup2(slave, 2);
        close(slave);
        close(master);
        chdir(cwdStr);
        
        // 通过 linker 执行 bash
        execve(argv[0], argv, envp);
        
        // 如果 execve 失败
        LOGE("execve 失败: %s (errno=%d)", strerror(errno), errno);
        _exit(127);
    } else if (pid > 0) {
        // 父进程
        close(slave);
        int flags = fcntl(master, F_GETFL, 0);
        fcntl(master, F_SETFL, flags | O_NONBLOCK);
        
        jintArray result = (*env)->NewIntArray(env, 2);
        jint data[2] = {master, pid};
        (*env)->SetIntArrayRegion(env, result, 0, 2, data);
        
        // 释放内存
        for (int i = 0; i < totalArgc; i++) {
            // 跳过 linker 和 cmd 的释放 (它们是 GetStringUTFChars 的返回值)
            // 对于参数，需要释放
        }
        // 简化内存释放
        for (int i = 0; i < argc; i++) {
            jstring s = (jstring)(*env)->GetObjectArrayElement(env, args, i);
            (*env)->ReleaseStringUTFChars(env, s, argv[i + 2]);
        }
        for (int i = 0; i < envCount; i++) {
            jstring s = (jstring)(*env)->GetObjectArrayElement(env, envVars, i);
            (*env)->ReleaseStringUTFChars(env, s, envp[i]);
        }
        free(argv);
        free(envp);
        (*env)->ReleaseStringUTFChars(env, cmd, cmdStr);
        (*env)->ReleaseStringUTFChars(env, cwd, cwdStr);
        
        return result;
    } else {
        LOGE("fork 失败: %s", strerror(errno));
        return NULL;
    }
}

JNIEXPORT void JNICALL
Java_com_UIN_Tool_terminal_PtyJNI_setPtySize(
    JNIEnv* env,
    jobject thiz,
    jint fd,
    jint rows,
    jint cols
) {
    (void)env;
    (void)thiz;
    struct winsize ws = { (unsigned short)rows, (unsigned short)cols, 0, 0 };
    ioctl(fd, TIOCSWINSZ, &ws);
}

JNIEXPORT jint JNICALL
Java_com_UIN_Tool_terminal_PtyJNI_waitPid(
    JNIEnv* env,
    jobject thiz,
    jint pid
) {
    (void)env;
    (void)thiz;
    int status;
    waitpid(pid, &status, 0);
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return -WTERMSIG(status);
    return 0;
}

JNIEXPORT void JNICALL
Java_com_UIN_Tool_terminal_PtyJNI_closeFd(
    JNIEnv* env,
    jobject thiz,
    jint fd
) {
    (void)env;
    (void)thiz;
    close(fd);
}