package com.suncanvas.app

import org.junit.Test

class ClasspathDebugTest {
    @Test
    fun printClasspath() {
        val cp = System.getProperty("java.class.path").orEmpty().split(java.io.File.pathSeparator)
        val res = ClassLoader.getSystemResource("com/suncanvas/app/SunCanvasApplication.class")
        val msg = "CPDEBUG entries=${cp.size} app=${cp.filter { it.contains("/app/build") }.joinToString(" | ")} res=$res"
        throw AssertionError(msg)
    }
}
