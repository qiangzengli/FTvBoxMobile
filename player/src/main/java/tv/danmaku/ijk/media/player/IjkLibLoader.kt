package tv.danmaku.ijk.media.player

interface IjkLibLoader {
    @Throws(UnsatisfiedLinkError::class, SecurityException::class)
    fun loadLibrary(libName: String?)
}
