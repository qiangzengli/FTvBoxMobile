package xyz.doikki.videoplayer.player

import android.content.Context

/**
 * 此接口使用方法：
 * 1.继承[AbstractPlayer]扩展自己的播放器。
 * 2.继承此接口并实现[createPlayer]，返回步骤1中的播放器。
 * 可参照 IJK 和 EXO 播放器的实现。
 */
abstract class PlayerFactory<P : AbstractPlayer> {

    abstract fun createPlayer(context: Context): P
}
