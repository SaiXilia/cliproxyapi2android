#include "jni_bridge.h"
#include <stdlib.h>
#include <string.h>

JNIEXPORT jint JNICALL Java_com_cliproxy_CLIProxy_nativeStartServer(
    JNIEnv *env, jclass clazz, jstring configDir, jstring host, jint port) {
    if (configDir == NULL) {
        return -1;
    }

    const char *c_dir = (*env)->GetStringUTFChars(env, configDir, NULL);
    if (c_dir == NULL) {
        return -1;
    }

    const char *c_host = NULL;
    if (host != NULL) {
        c_host = (*env)->GetStringUTFChars(env, host, NULL);
    }

    int ret = StartServer((char *)c_dir, (char *)c_host, (int)port);

    (*env)->ReleaseStringUTFChars(env, configDir, c_dir);
    if (c_host != NULL) {
        (*env)->ReleaseStringUTFChars(env, host, c_host);
    }

    return (jint)ret;
}

JNIEXPORT void JNICALL Java_com_cliproxy_CLIProxy_nativeStopServer(
    JNIEnv *env, jclass clazz) {
    StopServer();
}

JNIEXPORT jint JNICALL Java_com_cliproxy_CLIProxy_nativeGetServerStatus(
    JNIEnv *env, jclass clazz) {
    return (jint)GetServerStatus();
}

JNIEXPORT jstring JNICALL Java_com_cliproxy_CLIProxy_nativePollOAuthURL(
    JNIEnv *env, jclass clazz, jint timeout_ms) {
    char *c_url = PollOAuthURL((int)timeout_ms);
    if (c_url == NULL) {
        return NULL;
    }

    jstring j_url = (*env)->NewStringUTF(env, c_url);
    FreeCString(c_url);
    return j_url;
}
