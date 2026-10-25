package android.util

@Suppress("UNUSED_PARAMETER")
object Log {
    @JvmStatic
    fun d(tag: String?, message: String?): Int = 0

    @JvmStatic
    fun d(tag: String?, message: String?, throwable: Throwable?): Int = 0

    @JvmStatic
    fun i(tag: String?, message: String?): Int = 0

    @JvmStatic
    fun w(tag: String?, message: String?): Int = 0

    @JvmStatic
    fun w(tag: String?, message: String?, throwable: Throwable?): Int = 0

    @JvmStatic
    fun e(tag: String?, message: String?): Int = 0

    @JvmStatic
    fun e(tag: String?, message: String?, throwable: Throwable?): Int = 0
}