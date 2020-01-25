package com.epam.drill.hook.http

import com.epam.drill.hook.gen.funchook_prepare
import com.epam.drill.hook.gen.wsaSend_func
import com.epam.drill.hook.gen.wsaSend_func_point
import com.epam.drill.hook.gen.wsaRecv_func
import com.epam.drill.hook.gen.wsaRecv_func_point
import kotlinx.cinterop.*
import platform.posix.LPWSAOVERLAPPED_COMPLETION_ROUTINE
import platform.posix.SOCKET
import platform.posix.WSABUF
import platform.posix._WSABUF
import platform.windows.LPDWORD
import platform.windows._OVERLAPPED

actual fun configureHttpHooks() {
    configureHttpHooksBuild {
        println("Configuration for mingw")
        funchook_prepare(httpHook, wsaSend_func_point, staticCFunction(::drillWsaSend))
        funchook_prepare(httpHook, wsaRecv_func_point, staticCFunction(::drillWsaRecv))
    }
}


fun drillWsaSend(
    fd: SOCKET,
    buff: CPointer<_WSABUF>?,
    buffersSize: UInt,
    written: LPDWORD?,
    p5: UInt,
    p6: CPointer<_OVERLAPPED>?,
    p7: LPWSAOVERLAPPED_COMPLETION_ROUTINE?
): Int {
    initRuntimeIfNeeded()
    val buffer = buff!![0]
    val size = buffer.len
    val buf = buffer.buf
    return memScoped {
        val (finalBuf, finalSize, injectedSize) = processWriteEvent(buf, size.convert())
        buff[0].buf = finalBuf
        buff[0].len = finalSize.convert()
        val wsasendFunc = wsaSend_func!!(fd, buff, buffersSize, written, p5, p6, p7)
        written!!.pointed.value -= injectedSize.convert()
        (wsasendFunc).convert()
    }
}

fun drillWsaRecv(
    fd: SOCKET,
    buff: CPointer<_WSABUF>?,
    p3: UInt,
    read: LPDWORD?,
    p5: LPDWORD?,
    p6: CPointer<_OVERLAPPED>?,
    p7: LPWSAOVERLAPPED_COMPLETION_ROUTINE?
): Int {
    initRuntimeIfNeeded()
    val wsarecvFunc: Int = wsaRecv_func!!(fd, buff, p3, read, p5, p6, p7)
    val finalBuf = buff!!.pointed
    tryDetectHttp(fd, finalBuf.buf, read!!.pointed.value.convert())
    return wsarecvFunc

}