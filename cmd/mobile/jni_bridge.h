#ifndef JNI_BRIDGE_H
#define JNI_BRIDGE_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

// Go 导出的 C 符号（定义在 main.go 中）
int StartServer(char *cConfigDir, char *cHost, int port);
void StopServer(void);
int GetServerStatus(void);
char* PollOAuthURL(int timeoutMs);
void FreeCString(char* ptr);

// JNI 导出函数（供 Kotlin/Java 层通过 System.loadLibrary("cliproxy") 调用）
JNIEXPORT jint JNICALL Java_com_cliproxy_CLIProxy_nativeStartServer(
    JNIEnv *env, jclass clazz, jstring configDir, jstring host, jint port);

JNIEXPORT void JNICALL Java_com_cliproxy_CLIProxy_nativeStopServer(
    JNIEnv *env, jclass clazz);

JNIEXPORT jint JNICALL Java_com_cliproxy_CLIProxy_nativeGetServerStatus(
    JNIEnv *env, jclass clazz);

JNIEXPORT jstring JNICALL Java_com_cliproxy_CLIProxy_nativePollOAuthURL(
    JNIEnv *env, jclass clazz, jint timeout_ms);

#ifdef __cplusplus
}
#endif

#endif // JNI_BRIDGE_H
