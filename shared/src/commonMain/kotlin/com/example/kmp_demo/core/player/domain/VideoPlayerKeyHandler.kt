package com.example.kmp_demo.core.player.domain

/**
 * 桌面端视频播放器键盘动作定义。
 *
 * 定义所有支持的键盘快捷键动作，后续扩展方向键时只需在此添加新动作。
 *
 * 当前支持：
 * - [TogglePlayPause]：空格键，切换播放/暂停
 * - [SeekForward]：右方向键，快进（预留）
 * - [SeekBackward]：左方向键，快退（预留）
 * - [VolumeUp]：上方向键，增加音量（预留）
 * - [VolumeDown]：下方向键，减小音量（预留）
 */
sealed interface PlayerKeyAction {
    /** 切换播放/暂停 */
    data object TogglePlayPause : PlayerKeyAction
    /** 快进 */
    data object SeekForward : PlayerKeyAction
    /** 快退 */
    data object SeekBackward : PlayerKeyAction
    /** 增加音量 */
    data object VolumeUp : PlayerKeyAction
    /** 减小音量 */
    data object VolumeDown : PlayerKeyAction
    /** 退出全屏 / 返回 */
    data object ExitFullscreen : PlayerKeyAction
}
