package tv.danmaku.ijk.media.player.exceptions

class IjkMediaException : Exception {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)

    companion object {
        private const val serialVersionUID = 7234796519009099506L
    }
}
