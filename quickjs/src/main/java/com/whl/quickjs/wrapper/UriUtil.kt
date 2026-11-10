package com.whl.quickjs.wrapper

import android.net.Uri
import android.text.TextUtils

/**
 * Utility methods for manipulating URIs.
 */
object UriUtil {

    private const val INDEX_COUNT = 4
    private const val SCHEME_COLON = 0
    private const val PATH = 1
    private const val QUERY = 2
    private const val FRAGMENT = 3

    @JvmStatic
    fun resolveToUri(baseUri: String?, referenceUri: String?): Uri {
        return Uri.parse(resolve(baseUri, referenceUri))
    }

    @JvmStatic
    fun resolve(baseUri: String?, referenceUri: String?): String {
        val uri = StringBuilder()

        val safeBaseUri = baseUri ?: ""
        val safeReferenceUri = referenceUri ?: ""

        val refIndices = getUriIndices(safeReferenceUri)
        if (refIndices[SCHEME_COLON] != -1) {
            uri.append(safeReferenceUri)
            removeDotSegments(uri, refIndices[PATH], refIndices[QUERY])
            return uri.toString()
        }

        val baseIndices = getUriIndices(safeBaseUri)
        if (refIndices[FRAGMENT] == 0) {
            return uri.append(safeBaseUri, 0, baseIndices[FRAGMENT]).append(safeReferenceUri).toString()
        }

        if (refIndices[QUERY] == 0) {
            return uri.append(safeBaseUri, 0, baseIndices[QUERY]).append(safeReferenceUri).toString()
        }

        if (refIndices[PATH] != 0) {
            val baseLimit = baseIndices[SCHEME_COLON] + 1
            uri.append(safeBaseUri, 0, baseLimit).append(safeReferenceUri)
            return removeDotSegments(uri, baseLimit + refIndices[PATH], baseLimit + refIndices[QUERY])
        }

        if (safeReferenceUri.isNotEmpty() && safeReferenceUri[refIndices[PATH]] == '/') {
            uri.append(safeBaseUri, 0, baseIndices[PATH]).append(safeReferenceUri)
            return removeDotSegments(uri, baseIndices[PATH], baseIndices[PATH] + refIndices[QUERY])
        }

        return if (baseIndices[SCHEME_COLON] + 2 < baseIndices[PATH] &&
            baseIndices[PATH] == baseIndices[QUERY]
        ) {
            uri.append(safeBaseUri, 0, baseIndices[PATH]).append('/').append(safeReferenceUri)
            removeDotSegments(uri, baseIndices[PATH], baseIndices[PATH] + refIndices[QUERY] + 1)
        } else {
            val lastSlashIndex = safeBaseUri.lastIndexOf('/', baseIndices[QUERY] - 1)
            val baseLimit = if (lastSlashIndex == -1) baseIndices[PATH] else lastSlashIndex + 1
            uri.append(safeBaseUri, 0, baseLimit).append(safeReferenceUri)
            removeDotSegments(uri, baseIndices[PATH], baseLimit + refIndices[QUERY])
        }
    }

    @JvmStatic
    fun isAbsolute(uri: String?): Boolean {
        return uri != null && getUriIndices(uri)[SCHEME_COLON] != -1
    }

    @JvmStatic
    fun removeQueryParameter(uri: Uri, queryParameterName: String): Uri {
        val builder = uri.buildUpon()
        builder.clearQuery()
        for (key in uri.queryParameterNames) {
            if (key != queryParameterName) {
                for (value in uri.getQueryParameters(key)) {
                    builder.appendQueryParameter(key, value)
                }
            }
        }
        return builder.build()
    }

    private fun removeDotSegments(uri: StringBuilder, offset: Int, limit: Int): String {
        var currentOffset = offset
        var currentLimit = limit
        if (currentOffset >= currentLimit) {
            return uri.toString()
        }
        if (uri[currentOffset] == '/') {
            currentOffset++
        }
        var segmentStart = currentOffset
        var i = currentOffset
        while (i <= currentLimit) {
            val nextSegmentStart = if (i == currentLimit) {
                i
            } else if (uri[i] == '/') {
                i + 1
            } else {
                i++
                continue
            }
            if (i == segmentStart + 1 && uri[segmentStart] == '.') {
                uri.delete(segmentStart, nextSegmentStart)
                currentLimit -= nextSegmentStart - segmentStart
                i = segmentStart
            } else if (i == segmentStart + 2 && uri[segmentStart] == '.' && uri[segmentStart + 1] == '.') {
                val prevSegmentStart = uri.lastIndexOf("/", segmentStart - 2) + 1
                val removeFrom = if (prevSegmentStart > currentOffset) prevSegmentStart else currentOffset
                uri.delete(removeFrom, nextSegmentStart)
                currentLimit -= nextSegmentStart - removeFrom
                segmentStart = prevSegmentStart
                i = prevSegmentStart
            } else {
                i++
                segmentStart = i
            }
        }
        return uri.toString()
    }

    private fun getUriIndices(uriString: String): IntArray {
        val indices = IntArray(INDEX_COUNT)
        if (TextUtils.isEmpty(uriString)) {
            indices[SCHEME_COLON] = -1
            return indices
        }

        val length = uriString.length
        var fragmentIndex = uriString.indexOf('#')
        if (fragmentIndex == -1) {
            fragmentIndex = length
        }
        var queryIndex = uriString.indexOf('?')
        if (queryIndex == -1 || queryIndex > fragmentIndex) {
            queryIndex = fragmentIndex
        }
        var schemeIndexLimit = uriString.indexOf('/')
        if (schemeIndexLimit == -1 || schemeIndexLimit > queryIndex) {
            schemeIndexLimit = queryIndex
        }
        var schemeIndex = uriString.indexOf(':')
        if (schemeIndex > schemeIndexLimit) {
            schemeIndex = -1
        }

        val hasAuthority = (schemeIndex + 2 < queryIndex &&
                uriString[schemeIndex + 1] == '/' &&
                uriString[schemeIndex + 2] == '/')
        val pathIndex = if (hasAuthority) {
            val idx = uriString.indexOf('/', schemeIndex + 3)
            if (idx == -1 || idx > queryIndex) queryIndex else idx
        } else {
            schemeIndex + 1
        }

        indices[SCHEME_COLON] = schemeIndex
        indices[PATH] = pathIndex
        indices[QUERY] = queryIndex
        indices[FRAGMENT] = fragmentIndex
        return indices
    }
}
