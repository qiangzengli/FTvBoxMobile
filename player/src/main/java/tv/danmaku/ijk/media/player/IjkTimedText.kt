package tv.danmaku.ijk.media.player

import android.graphics.Rect

class IjkTimedText {
    private var mTextBounds: Rect? = null
    private var mTextChars: String? = null
    private var mBitmapData: IntArray? = null

    constructor(bounds: Rect?, text: String?) {
        mTextBounds = bounds
        mTextChars = text
    }

    constructor(bitmap: IntArray?) {
        mBitmapData = bitmap
    }

    fun getBounds(): Rect? = mTextBounds

    fun getBitmapData(): IntArray? = mBitmapData

    fun getText(): String? = mTextChars
}
