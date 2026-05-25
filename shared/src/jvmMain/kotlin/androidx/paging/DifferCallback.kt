package androidx.paging

/**
 * JVM 平台上的 DifferCallback 桩实现
 *
 * androidx.paging:paging-compose-desktop 引用了 androidx.paging.DifferCallback，
 * 但该类在 androidx.paging:paging-common-desktop 中不存在（仅在 Android 版中存在）。
 * 这里提供一个空实现以满足运行时类加载需求。
 */
interface DifferCallback {
    fun onChanged(position: Int, count: Int)
    fun onInserted(position: Int, count: Int)
    fun onRemoved(position: Int, count: Int)
    fun onPreDispatchAllUpdates()
    fun onPostDispatchAllUpdates()
}
