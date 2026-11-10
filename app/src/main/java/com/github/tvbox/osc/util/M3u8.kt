package com.github.tvbox.osc.util

import com.github.tvbox.osc.util.RegexUtils.getPattern
import com.google.android.exoplayer2.util.UriUtil
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.regex.Pattern

/**
 * @author asdfgh, FongMi
 * Based on FongMi/TV.
 * https://github.com/FongMi/TV
 */
object M3u8 {
    private const val TAG_DISCONTINUITY = "#EXT-X-DISCONTINUITY"
    private const val TAG_MEDIA_DURATION = "#EXTINF"
    private const val TAG_ENDLIST = "#EXT-X-ENDLIST"
    private const val TAG_KEY = "#EXT-X-KEY"
    private const val TAG_MAP = "#EXT-X-MAP"
    private const val TAG_CUE_OUT = "#EXT-X-CUE-OUT"
    private const val TAG_CUE_IN = "#EXT-X-CUE-IN"
    private const val TAG_DATERANGE = "#EXT-X-DATERANGE"

    private val REGEX_X_DISCONTINUITY = Pattern.compile("#EXT-X-DISCONTINUITY[\\s\\S]*?(?=#EXT-X-DISCONTINUITY|$)")
    private val REGEX_MEDIA_DURATION = Pattern.compile("$TAG_MEDIA_DURATION:([\\d\\.]+)\\b")
    private val REGEX_URI = Pattern.compile("URI=\"(.+?)\"")

    // 增强：广告片段 URL 特征识别（去广告接口常用规则）
    private val REGEX_AD_SEGMENT_URI = Pattern.compile("(?i)(^|[/?&=_.-])(ads?|adv|advert(ise(ment)?)?|commercial|preroll|pre-roll|midroll|mid-roll|postroll|post-roll|sponsor|scte|vast|vmap|interstitial|bumper)([/?&=_.-]|$)")

    // 增强：广告域名特征（常见广告CDN）
    private val AD_DOMAIN_KEYWORDS = arrayOf(
        "adservice", "adserver", "adsystem", "doubleclick", "googlesyndication",
        "advertising", "2mdn.net", "moatads", "scorecardresearch", "quantserve"
    )

    private const val MAX_FRAME_RATE_AD_BLOCK_SIZE = 12
    private val FRAME_RATE_FEATURES: Map<Int, Set<BigDecimal>> = prepareFrameRateFeatures()

    @JvmField
    var currentAdCount = 0

    @JvmStatic
    fun isAd(regex: String): Boolean {
        return regex.contains(TAG_DISCONTINUITY) || regex.contains(TAG_MEDIA_DURATION) || regex.contains(TAG_ENDLIST) || regex.contains(TAG_KEY) || regex.contains(TAG_CUE_OUT) || regex.contains(TAG_CUE_IN) || regex.contains(TAG_DATERANGE) || isDouble(regex)
    }

    @JvmStatic
    fun purify(tsUrlPre: String, m3u8contentParam: String?): String? {
        val start = System.currentTimeMillis()
        currentAdCount = 0
        if (m3u8contentParam.isNullOrEmpty()) return null
        var m3u8content = m3u8contentParam
        if (m3u8content.startsWith("\ufeff")) m3u8content = m3u8content.substring(1)
        if (!m3u8content.startsWith("#EXTM3U")) return null

        // Count total segments for final safety check
        var totalSegments = 0
        val lines = m3u8content.split(if (m3u8content.contains("\r\n")) "\r\n" else "\n").toTypedArray()
        for (line in lines) {
            if (line.isNotEmpty() && line[0] != '#') {
                totalSegments++
            }
        }

        var result = removeMinorityUrl(tsUrlPre, m3u8content)
        result = if (result != null && currentAdCount > 0) get(tsUrlPre, result)
        else get(tsUrlPre, m3u8content)
        result = keepVodEndList(m3u8content, result)

        // Final safety check: if too many segments removed, return original content
        if (totalSegments > 0 && currentAdCount > totalSegments * 0.5) {
            LOG.e("echo-fixAdM3u8 ERROR: removed too many segments $currentAdCount/$totalSegments, using original content")
            currentAdCount = 0
            result = m3u8content
        }
        if (currentAdCount > 0 && !isPlayableMediaPlaylist(result)) {
            LOG.e("echo-fixAdM3u8 ERROR: invalid playlist after ad removal, using original content")
            currentAdCount = 0
            result = m3u8content
        }

        val cost = System.currentTimeMillis() - start
        LOG.i("echo-fixAdM3u8 cost: ${cost}ms, removed: $currentAdCount segments")
        return result
    }

    private fun maxPercent(preUrlMap: HashMap<String, Int>): Double {
        var maxTimes = 0
        var totalTimes = 0
        for ((_, value) in preUrlMap) {
            if (value > maxTimes) {
                maxTimes = value
            }
            totalTimes += value
        }
        return maxTimes * 1.0 / (totalTimes * 1.0)
    }

    private var timesNoAd = 15

    private fun removeMinorityUrl(tsUrlPre: String, m3u8content: String): String? {
        var linesplit = "\n"
        if (m3u8content.contains("\r\n")) linesplit = "\r\n"
        val lines = m3u8content.split(linesplit).toTypedArray()

        // Count total segments for safety check
        var totalSegments = 0
        for (line in lines) {
            if (line.isNotEmpty() && line[0] != '#') {
                totalSegments++
            }
        }

        // First pass: count normalized media path prefixes.
        val preUrlMap = HashMap<String, Int>()
        for (line in lines) {
            if (line.isEmpty() || line[0] == '#') {
                continue
            }
            val absoluteUrl = toAbsoluteUrl(tsUrlPre, line)
            val ilast = absoluteUrl.lastIndexOf('.')
            if (ilast <= 4) {
                continue
            }
            val preUrl = absoluteUrl.substring(0, ilast - 4)
            val cnt = preUrlMap[preUrl]
            if (cnt != null) {
                preUrlMap[preUrl] = cnt + 1
            } else {
                preUrlMap[preUrl] = 1
            }
        }
        if (preUrlMap.size <= 1) return null
        var domainFiltering = false
        if (maxPercent(preUrlMap) < 0.8) {
            // Fallback to dominant host filtering.
            preUrlMap.clear()
            for (line in lines) {
                if (line.isEmpty() || line[0] == '#') {
                    continue
                }
                val absoluteUrl = toAbsoluteUrl(tsUrlPre, line)
                if (!absoluteUrl.startsWith("http://") && !absoluteUrl.startsWith("https://")) {
                    return null
                }
                val ifirst = absoluteUrl.indexOf('/', 9)
                if (ifirst <= 0) {
                    continue
                }
                val preUrl = absoluteUrl.substring(0, ifirst)
                val cnt = preUrlMap[preUrl]
                if (cnt != null) {
                    preUrlMap[preUrl] = cnt + 1
                } else {
                    preUrlMap[preUrl] = 1
                }
            }
            if (preUrlMap.size <= 1) return null
            if (maxPercent(preUrlMap) < 0.8) {
                return null
            }
            var allDomainsExceedThreshold = true
            for (count in preUrlMap.values) {
                if (count <= 15) {
                    allDomainsExceedThreshold = false
                    break
                }
            }
            if (allDomainsExceedThreshold) return null
            domainFiltering = true
        }

        // Keep the most common media prefix or host.
        var maxTimes = 0
        var maxTimesPreUrl = ""
        for ((key, value) in preUrlMap) {
            if (value > maxTimes) {
                maxTimesPreUrl = key
                maxTimes = value
            }
        }
        if (maxTimes == 0) return null

        // Diagnostic logging
        LOG.i("echo-fixAdM3u8 URL pattern count: ${preUrlMap.size}, maxTimes: $maxTimes, total: $totalSegments")

        val filtered = java.lang.StringBuilder()
        val pendingSegmentTags = ArrayList<String>()
        for (i in lines.indices) {
            val item = lines[i].trim()
            if (item.isEmpty()) {
                if (pendingSegmentTags.isEmpty()) appendLine(filtered, lines[i], linesplit)
                else pendingSegmentTags.add(lines[i])
                continue
            }
            if (item[0] == '#') {
                val output = if (hasUriAttribute(item)) resolveUriLine(tsUrlPre, lines[i]) else lines[i]
                if (isSegmentTag(item)) pendingSegmentTags.add(output)
                else {
                    flush(filtered, pendingSegmentTags, linesplit)
                    appendLine(filtered, output, linesplit)
                }
                continue
            }

            val absoluteUrl = toAbsoluteUrl(tsUrlPre, lines[i])
            if (shouldKeepMediaUrl(absoluteUrl, domainFiltering, maxTimesPreUrl, preUrlMap)) {
                flush(filtered, pendingSegmentTags, linesplit)
                appendLine(filtered, absoluteUrl, linesplit)
            } else {
                pendingSegmentTags.clear()
                currentAdCount += 1
            }
        }

        // Safety check: if removal ratio is too high, likely a false positive
        if (totalSegments > 0 && currentAdCount > totalSegments * 0.3) {
            LOG.i("echo-fixAdM3u8 suspicious ad count: $currentAdCount/$totalSegments, skipping URL filtering")
            currentAdCount = 0 // Reset to avoid affecting subsequent filters
            return null // Skip this filtering method
        }

        return normalizeMediaPlaylist(filtered.toString())
    }

    private fun get(tsUrlPre: String, m3u8Content: String): String {
        var line = resolveContent(tsUrlPre, m3u8Content)
        val ads = getRegex(tsUrlPre)
        if (ads.isNotEmpty()) line = clean(line, ads)
        line = cleanCommonAdMarkers(line)
        if (hasEndList(line) && line.contains(TAG_DISCONTINUITY)) {
            line = cleanDecimalPrecisionGroups(line)
            line = cleanFrameRateGroups(line)
        }
        return cleanDiscontinuityGroups(line)
    }

    /**
     * 正片切片的 EXTINF 小数位数通常稳定，广告素材拼接后经常出现另一种精度。
     * 只删除精度完全不同的短块，不处理块内混合精度，避免误删正常切片。
     */
    private fun cleanDecimalPrecisionGroups(m3u8Content: String): String {
        val groups = buildDiscontinuityGroups(m3u8Content.split("\\n".toRegex()).toTypedArray())
        if (groups.size < 2) return m3u8Content

        val precisionCounts = HashMap<Int, Int>()
        var totalSegments = 0
        for (group in groups) {
            for (raw in group.lines) {
                val precision = getDecimalPrecision(raw)
                if (precision < 0) continue
                totalSegments += 1
                val count = precisionCounts[precision]
                precisionCounts[precision] = if (count == null) 1 else count + 1
            }
        }
        if (totalSegments < 8 || precisionCounts.size < 2) return m3u8Content

        var majorPrecision = -1
        var majorCount = 0
        for ((key, value) in precisionCounts) {
            if (value > majorCount) {
                majorPrecision = key
                majorCount = value
            }
        }
        if (majorPrecision < 0 || majorCount * 1.0 / totalSegments < 0.7) return m3u8Content

        val removeGroups = BooleanArray(groups.size)
        var removableSegments = 0
        for (i in groups.indices) {
            val group = groups[i]
            if (i == groups.size - 1 || group.segmentCount == 0 || group.segmentCount > MAX_FRAME_RATE_AD_BLOCK_SIZE) continue

            val stats = getDecimalPrecisionStats(group, majorPrecision)
            if (stats.total > 0 && stats.mismatched == stats.total) {
                removeGroups[i] = true
                removableSegments += group.segmentCount
            }
        }

        if (removableSegments == 0 || removableSegments > getAdSegmentLimit(m3u8Content) ||
            removableSegments > totalSegments * 0.3
        ) return m3u8Content

        val sb = java.lang.StringBuilder()
        var removedBlocks = 0
        for (i in groups.indices) {
            if (removeGroups[i]) {
                currentAdCount += groups[i].segmentCount
                removedBlocks += 1
            } else {
                groups[i].appendTo(sb)
            }
        }
        LOG.i("echo-fixAdM3u8 decimal precision detected: major=$majorPrecision, blocks=$removedBlocks, removed=$removableSegments")
        return normalizeMediaPlaylist(sb.toString())
    }

    private fun getDecimalPrecisionStats(group: Group, majorPrecision: Int): DecimalPrecisionStats {
        val stats = DecimalPrecisionStats()
        for (raw in group.lines) {
            val precision = getDecimalPrecision(raw)
            if (precision < 0) continue
            stats.total += 1
            if (precision != majorPrecision) stats.mismatched += 1
        }
        return stats
    }

    private fun getDecimalPrecision(line: String): Int {
        val start = getExtInfValueStart(line)
        if (start < 0) return -1
        val end = getExtInfValueEnd(line, start)
        val dot = line.indexOf('.', start)
        return if (dot < 0 || dot >= end) 0 else end - dot - 1
    }

    /**
     * 广告通常由不同素材拼接，切片时长的小数部分会呈现不同的帧率特征。
     * 仅对点播播放列表中的短不连续块执行，避免影响直播和正常长片段。
     */
    private fun cleanFrameRateGroups(m3u8Content: String): String {
        val groups = buildDiscontinuityGroups(m3u8Content.split("\\n".toRegex()).toTypedArray())
        if (groups.size < 2) return m3u8Content

        val masterFrameRate = findDominantFrameRate(groups)
        if (masterFrameRate == 0) return m3u8Content

        var removableSegments = 0
        val removeGroups = BooleanArray(groups.size)
        for (i in groups.indices) {
            val group = groups[i]
            if (i == groups.size - 1 || group.segmentCount == 0 || group.segmentCount > MAX_FRAME_RATE_AD_BLOCK_SIZE) continue

            val stats = getFrameRateStats(group, masterFrameRate)
            if (stats.mismatched > 0 && stats.mismatched >= stats.matched) {
                removeGroups[i] = true
                removableSegments += group.segmentCount
            }
        }

        val segmentLimit = getAdSegmentLimit(m3u8Content)
        if (removableSegments == 0 || removableSegments > segmentLimit) return m3u8Content

        val sb = java.lang.StringBuilder()
        var removedBlocks = 0
        for (i in groups.indices) {
            if (removeGroups[i]) {
                currentAdCount += groups[i].segmentCount
                removedBlocks += 1
            } else {
                groups[i].appendTo(sb)
            }
        }
        LOG.i("echo-fixAdM3u8 frame rate detected: master=$masterFrameRate, blocks=$removedBlocks, removed=$removableSegments")
        return normalizeMediaPlaylist(sb.toString())
    }

    private fun findDominantFrameRate(groups: List<Group>): Int {
        var count30 = 0
        var count25 = 0
        var count24 = 0
        for (group in groups) {
            for (raw in group.lines) {
                if (getExtInfValueStart(raw) < 0) continue
                val frameRate = getExclusiveFrameRate(parseExtInfDuration(raw))
                if (frameRate == 30) count30 += 1
                else if (frameRate == 25) count25 += 1
                else if (frameRate == 24) count24 += 1
            }
        }

        val max = Math.max(count30, Math.max(count25, count24))
        if (max < 2) return 0
        if ((if (count30 == max) 1 else 0) + (if (count25 == max) 1 else 0) + (if (count24 == max) 1 else 0) != 1) return 0
        return if (count30 == max) 30 else (if (count25 == max) 25 else 24)
    }

    private fun getFrameRateStats(group: Group, masterFrameRate: Int): FrameRateStats {
        val stats = FrameRateStats()
        for (raw in group.lines) {
            if (getExtInfValueStart(raw) < 0) continue
            val frameRate = getExclusiveFrameRate(parseExtInfDuration(raw))
            if (frameRate == masterFrameRate) stats.matched += 1
            else if (frameRate != 0) stats.mismatched += 1
        }
        return stats
    }

    private fun getExclusiveFrameRate(duration: BigDecimal): Int {
        val is30 = isFrameAligned(duration, 30)
        val is25 = isFrameAligned(duration, 25)
        val is24 = isFrameAligned(duration, 24)
        if (is30 && !is25 && !is24) return 30
        if (is25 && !is30 && !is24) return 25
        if (is24 && !is30 && !is25) return 24
        return 0
    }

    private fun isFrameAligned(duration: BigDecimal?, frameRate: Int): Boolean {
        if (duration == null) return false
        val features = FRAME_RATE_FEATURES[frameRate] ?: return false
        val fraction = duration.remainder(BigDecimal.ONE).abs().stripTrailingZeros()
        return features.contains(fraction)
    }

    private fun prepareFrameRateFeatures(): Map<Int, Set<BigDecimal>> {
        val features = HashMap<Int, Set<BigDecimal>>()
        features[30] = createFrameRateFeatures(30, true)
        features[25] = createFrameRateFeatures(25, false)
        features[24] = createFrameRateFeatures(24, true)
        return features
    }

    private fun createFrameRateFeatures(frameRate: Int, includeNtsc: Boolean): Set<BigDecimal> {
        val features = HashSet<BigDecimal>()
        addFrameRateFeatures(features, frameRate.toDouble(), frameRate)
        if (includeNtsc) addFrameRateFeatures(features, frameRate / 1.001, frameRate * 10)
        return features
    }

    private fun addFrameRateFeatures(features: MutableSet<BigDecimal>, frameRate: Double, maxFrames: Int) {
        val rate = BigDecimal.valueOf(frameRate)
        for (frame in 1..maxFrames) {
            val fraction = BigDecimal.valueOf(frame.toLong()).divide(rate, 10, RoundingMode.HALF_UP).remainder(BigDecimal.ONE)
            for (scale in 3..6) {
                val value = fraction.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros()
                if (value.compareTo(BigDecimal.ZERO) != 0) features.add(value)
            }
        }
    }

    private fun parseExtInfDuration(line: String): BigDecimal {
        val start = getExtInfValueStart(line)
        if (start < 0) return BigDecimal.ZERO
        val end = getExtInfValueEnd(line, start)
        try {
            return BigDecimal(line.substring(start, end)).stripTrailingZeros()
        } catch (ignored: Exception) {
        }
        return BigDecimal.ZERO
    }

    private fun getExtInfValueStart(line: String?): Int {
        if (line == null) return -1
        val length = line.length
        var start = 0
        while (start < length && line[start] <= ' ') start += 1
        if (!line.startsWith(TAG_MEDIA_DURATION, start)) return -1
        start += TAG_MEDIA_DURATION.length
        if (start >= length || line[start] != ':') return -1
        start += 1
        while (start < length && line[start] <= ' ') start += 1
        return if (start < length) start else -1
    }

    private fun getExtInfValueEnd(line: String, start: Int): Int {
        var end = line.indexOf(',', start)
        if (end < 0) end = line.length
        while (end > start && line[end - 1] <= ' ') end -= 1
        return end
    }

    private fun getAdSegmentLimit(m3u8Content: String): Int {
        var totalDuration = BigDecimal.ZERO
        for (raw in m3u8Content.split("\\n".toRegex())) totalDuration = totalDuration.add(parseExtInfDuration(raw))
        val totalMinutes = totalDuration.toDouble() / 60
        if (totalMinutes <= 30) return 18
        if (totalMinutes <= 60) return 24
        if (totalMinutes <= 90) return 30
        return 36
    }

    private class FrameRateStats {
        var matched = 0
        var mismatched = 0
    }

    private class DecimalPrecisionStats {
        var total = 0
        var mismatched = 0
    }

    private fun resolveContent(tsUrlPre: String, m3u8ContentParam: String): String {
        val m3u8Content = m3u8ContentParam.replace("\r\n", "\n")
        val sb = java.lang.StringBuilder()
        for (line in m3u8Content.split("\n".toRegex())) {
            sb.append(if (shouldResolve(line)) resolve(tsUrlPre, line.trim()) else line).append("\n")
        }
        return sb.toString()
    }

    private fun getRegex(tsUrlPre: String): List<String> {
        val hostsRegex = VideoParseRuler.getHostsRegex()
        var list: List<String> = ArrayList()
        for (host in hostsRegex.keys) {
            if (!tsUrlPre.contains(host)) continue
            val found = hostsRegex[host] ?: continue
            list = found
            break
        }
        return list
    }

    private fun clean(lineParam: String, ads: List<String>): String {
        var line = lineParam
        var scan = false
        for (ad in ads) {
            if (ad.contains(TAG_DISCONTINUITY) || ad.contains(TAG_MEDIA_DURATION)) line = scanAd(line, ad)
            else if (isDouble(ad)) scan = true
        }
        return if (scan) scan(line, ads) else line
    }

    private fun cleanCommonAdMarkers(line: String): String {
        val sb = java.lang.StringBuilder()
        val pending = ArrayList<String>()
        var inAdBreak = false
        var changed = false

        for (raw in line.split("\n", limit = -1)) {
            val item = raw.trim()
            if (item.isEmpty()) {
                if (pending.isEmpty()) sb.append(raw).append("\n")
                else pending.add(raw)
                continue
            }
            if (item.startsWith("#")) {
                if (item.startsWith(TAG_CUE_IN)) {
                    if (inAdBreak || hasAdSignal(pending)) {
                        inAdBreak = false
                        pending.clear()
                        changed = true
                        continue
                    }
                }
                if (isAdBreakStart(item)) {
                    flush(sb, pending)
                    inAdBreak = true
                    pending.add(raw)
                    changed = true
                    continue
                }
                if (inAdBreak) {
                    pending.add(raw)
                    changed = true
                    continue
                }
                if (isStandaloneAdTag(item)) {
                    flush(sb, pending)
                    currentAdCount += 1
                    changed = true
                    continue
                }
                if (isSegmentTag(item) || isAdSignalTag(item)) {
                    pending.add(raw)
                } else {
                    flush(sb, pending)
                    sb.append(raw).append("\n")
                }
                continue
            }

            // 增强：检查 URL 特征和域名特征
            if (inAdBreak || hasAdSignal(pending) || isAdSegmentUri(item) || hasAdDomain(item)) {
                pending.clear()
                currentAdCount += 1
                changed = true
                continue
            }
            flush(sb, pending)
            sb.append(raw).append("\n")
        }

        if (!inAdBreak) flush(sb, pending)
        return if (changed) sb.toString() else line
    }

    private fun flush(sb: java.lang.StringBuilder, pending: MutableList<String>) {
        for (line in pending) sb.append(line).append("\n")
        pending.clear()
    }

    private fun flush(sb: java.lang.StringBuilder, pending: MutableList<String>, linesplit: String) {
        for (line in pending) appendLine(sb, line, linesplit)
        pending.clear()
    }

    private fun appendLine(sb: java.lang.StringBuilder, line: String, linesplit: String) {
        sb.append(line).append(linesplit)
    }

    private fun hasAdSignal(pending: List<String>): Boolean {
        for (line in pending) {
            if (isAdBreakStart(line.trim()) || isAdSignalTag(line.trim())) return true
        }
        return false
    }

    private fun isAdBreakStart(line: String): Boolean {
        return line.startsWith(TAG_CUE_OUT)
    }

    private fun isAdSignalTag(line: String): Boolean {
        // 增强：支持更多广告信号标记
        if (line.startsWith("#EXT-OATCLS-SCTE35")) return true
        if (line.startsWith("#EXT-X-SCTE35")) return true
        if (line.startsWith("#EXT-X-SPLICEPOINT-SCTE35")) return true
        if (line.startsWith("#EXT-X-CUE")) return true
        if (line.startsWith("#EXT-X-ASSET")) return true
        if (line.startsWith("#EXT-X-VMAP-AD-BREAK")) return true
        if (line.startsWith("#EXT-X-AD")) return true
        if (line.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE")) return false
        return false
    }

    private fun isSegmentTag(line: String): Boolean {
        if (line.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE")) return false
        return line.startsWith(TAG_MEDIA_DURATION) || line.startsWith("#EXT-X-BYTERANGE") || line.startsWith("#EXT-X-PROGRAM-DATE-TIME") || line.startsWith("#EXT-X-DISCONTINUITY") || line.startsWith("#EXT-X-PART") || line.startsWith("#EXT-X-PRELOAD-HINT")
    }

    private fun isStandaloneAdTag(line: String): Boolean {
        if (!line.startsWith(TAG_DATERANGE)) return false
        return isAdLikeText(line) || line.contains("X-ASSET-URI") || line.contains("X-ASSET-LIST")
    }

    private fun isAdLikeText(line: String): Boolean {
        val lower = line.toLowerCase()
        return lower.contains("scte") || lower.contains("cue") || lower.contains("interstitial") ||
                lower.contains("vmap") || lower.contains("vast") || lower.contains("advert") ||
                lower.contains("commercial") || lower.contains("ad-") || lower.contains("ad_") ||
                lower.contains("ad.") || lower.contains("preroll") || lower.contains("midroll") ||
                lower.contains("postroll") || lower.contains("bumper")
    }

    private fun isAdSegmentUri(line: String): Boolean {
        return REGEX_AD_SEGMENT_URI.matcher(line).find()
    }

    // 增强：检查是否包含广告域名特征
    private fun hasAdDomain(url: String): Boolean {
        val lower = url.toLowerCase()
        for (keyword in AD_DOMAIN_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true
            }
        }
        return false
    }

    private fun cleanDiscontinuityGroups(m3u8Content: String): String {
        val lines = m3u8Content.split("\n".toRegex()).toTypedArray()
        val groups = buildDiscontinuityGroups(lines)
        if (groups.size < 3) return m3u8Content
        val main = findMainGroup(groups)
        if (main == null || main.segmentCount < 3) return m3u8Content

        val sb = java.lang.StringBuilder()
        var changed = false
        for (group in groups) {
            if (shouldDropGroup(group, main)) {
                currentAdCount += group.segmentCount
                changed = true
                continue
            }
            group.appendTo(sb)
        }
        return if (changed) sb.toString() else m3u8Content
    }

    private fun buildDiscontinuityGroups(lines: Array<String>): List<Group> {
        val groups = ArrayList<Group>()
        var group = Group()
        for (raw in lines) {
            val line = raw.trim()
            if (line.startsWith(TAG_DISCONTINUITY) && group.hasMedia()) {
                groups.add(group)
                group = Group()
            }
            group.add(raw)
        }
        if (group.hasMedia() || group.lines.isNotEmpty()) groups.add(group)
        return groups
    }

    private fun findMainGroup(groups: List<Group>): Group? {
        var main: Group? = null
        for (group in groups) {
            if (group.segmentCount == 0) continue
            if (main == null || group.score() > main.score()) main = group
        }
        return main
    }

    private fun shouldDropGroup(group: Group, main: Group): Boolean {
        if (group == main || group.segmentCount == 0) return false

        // 增强：更严格的广告识别条件
        val shortGroup = group.segmentCount <= 2 ||
                (main.totalDuration > 0 && group.totalDuration > 0 &&
                        group.totalDuration < main.totalDuration * 0.18)

        val differentHost = main.host.isNotEmpty() && group.host.isNotEmpty() &&
                main.host != group.host

        val differentPath = main.pathPrefix.isNotEmpty() && group.pathPrefix.isNotEmpty() &&
                main.pathPrefix != group.pathPrefix

        // 增强：检查广告域名和URL特征
        val hasAdFeature = group.adLikeCount > 0 || hasAdDomain(group.host) ||
                isAdSegmentUri(group.pathPrefix)

        val adLike = hasAdFeature || differentHost || (group.segmentCount <= 2 && differentPath)

        return shortGroup && adLike
    }

    private fun hostOf(url: String): String {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return ""
        val start = url.indexOf("://") + 3
        val end = url.indexOf('/', start)
        return if (end > start) url.substring(start, end) else url.substring(start)
    }

    private fun pathPrefixOf(url: String): String {
        var clean = url
        val query = clean.indexOf('?')
        if (query >= 0) clean = clean.substring(0, query)
        val slash = clean.lastIndexOf('/')
        return if (slash > 0) clean.substring(0, slash + 1) else ""
    }

    private fun toAbsoluteUrl(base: String, url: String?): String {
        if (url == null) return ""
        val line = url.trim()
        if (line.isEmpty() || line.startsWith("http://") || line.startsWith("https://")) return line
        return UriUtil.resolve(base, line)
    }

    private fun shouldKeepMediaUrl(absoluteUrl: String, domainFiltering: Boolean, maxTimesPreUrl: String, preUrlMap: HashMap<String, Int>): Boolean {
        if (!domainFiltering) return absoluteUrl.startsWith(maxTimesPreUrl)
        val ifirst = absoluteUrl.indexOf('/', 9)
        val domain = if (ifirst > 0) absoluteUrl.substring(0, ifirst) else absoluteUrl
        val cnt = preUrlMap[domain]
        return domain == maxTimesPreUrl || (cnt != null && cnt > timesNoAd)
    }

    private fun hasUriAttribute(line: String): Boolean {
        return line.startsWith(TAG_KEY) || line.startsWith(TAG_MAP)
    }

    private fun resolveUriLine(base: String, line: String): String {
        val matcher = REGEX_URI.matcher(line)
        val value = if (matcher.find()) matcher.group(1) else null
        return if (value == null) line else line.replace(value, UriUtil.resolve(base, value))
    }

    private fun normalizeMediaPlaylist(content: String): String {
        val sb = java.lang.StringBuilder()
        var seenMedia = false
        var hasPendingDiscontinuity = false
        var pendingDiscontinuity = ""
        for (raw in content.replace("\r\n", "\n").split("\n", limit = -1)) {
            val item = raw.trim()
            if (isDiscontinuityTag(item)) {
                if (seenMedia && !hasPendingDiscontinuity) {
                    pendingDiscontinuity = raw
                    hasPendingDiscontinuity = true
                }
                continue
            }
            if (hasPendingDiscontinuity) {
                if (item.isEmpty()) continue
                if (!item.startsWith(TAG_ENDLIST)) sb.append(pendingDiscontinuity).append("\n")
                hasPendingDiscontinuity = false
            }
            if (item.isEmpty() && sb.length == 0) continue
            sb.append(raw).append("\n")
            if (isMediaUriLine(item)) seenMedia = true
        }
        return sb.toString()
    }

    private fun isPlayableMediaPlaylist(content: String?): Boolean {
        if (content == null || !content.startsWith("#EXTM3U")) return false
        var mediaCount = 0
        var pendingExtInf = false
        for (raw in content.replace("\r\n", "\n").split("\n".toRegex())) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith(TAG_MEDIA_DURATION)) {
                if (pendingExtInf) return false
                pendingExtInf = true
            } else if (isMediaUriLine(line)) {
                mediaCount += 1
                pendingExtInf = false
            } else if (line.startsWith(TAG_ENDLIST) && pendingExtInf) {
                return false
            }
        }
        return mediaCount > 0 && !pendingExtInf
    }

    private fun keepVodEndList(original: String, result: String?): String? {
        if (result == null) return null
        if (!hasEndList(original) || hasEndList(result)) return result
        return result + (if (result.endsWith("\n")) "" else "\n") + TAG_ENDLIST + "\n"
    }

    private fun hasEndList(content: String?): Boolean {
        if (content == null) return false
        for (raw in content.replace("\r\n", "\n").split("\n".toRegex())) {
            if (raw.trim().startsWith(TAG_ENDLIST)) return true
        }
        return false
    }

    private fun isMediaUriLine(line: String): Boolean {
        return line.isNotEmpty() && !line.startsWith("#")
    }

    private fun isDiscontinuityTag(line: String): Boolean {
        return line.startsWith(TAG_DISCONTINUITY) && !line.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE")
    }

    private class Group {
        val lines = ArrayList<String>()
        var segmentCount = 0
        var adLikeCount = 0
        var totalDuration = 0.0
        var host = ""
        var pathPrefix = ""

        fun add(raw: String) {
            lines.add(raw)
            val line = raw.trim()
            val durationStart = getExtInfValueStart(line)
            if (durationStart >= 0) {
                val durationEnd = getExtInfValueEnd(line, durationStart)
                try {
                    totalDuration += line.substring(durationStart, durationEnd).toDouble()
                } catch (ignored: Exception) {
                }
            }
            if (line.isEmpty() || line.startsWith("#")) {
                if (isAdSignalTag(line) || isStandaloneAdTag(line)) adLikeCount += 1
                return
            }
            segmentCount += 1
            if (isAdSegmentUri(line) || hasAdDomain(line)) adLikeCount += 1
            if (host.isEmpty()) host = hostOf(line)
            if (pathPrefix.isEmpty()) pathPrefix = pathPrefixOf(line)
        }

        fun hasMedia(): Boolean {
            return segmentCount > 0
        }

        fun appendTo(sb: java.lang.StringBuilder) {
            for (line in lines) sb.append(line).append("\n")
        }

        fun score(): Double {
            return if (totalDuration > 0) totalDuration else segmentCount.toDouble()
        }
    }

    private fun scanAd(lineParam: String, TAG_AD: String): String {
        var line = lineParam
        val m1 = getPattern(TAG_AD).matcher(line)
        val needRemoveAd = ArrayList<String>()
        while (m1.find()) {
            val group = m1.group()
            val groupCleaned = group.replace(TAG_ENDLIST, "")
            val m2 = REGEX_MEDIA_DURATION.matcher(group)
            var tCount = 0
            while (m2.find()) {
                tCount += 1
            }
            needRemoveAd.add(groupCleaned)
            currentAdCount += tCount
        }
        for (rem in needRemoveAd) {
            line = line.replace(rem, "")
        }
        return line
    }

    private fun scan(lineParam: String, ads: List<String>): String {
        var line = lineParam
        val m1 = REGEX_X_DISCONTINUITY.matcher(line)
        val needRemoveAd = ArrayList<String>()
        while (m1.find()) {
            val group = m1.group()
            val groupCleaned = group.replace(TAG_ENDLIST, "")
            val m2 = REGEX_MEDIA_DURATION.matcher(group)
            var ft = BigDecimal.ZERO
            var lt = BigDecimal.ZERO
            var t = BigDecimal.ZERO
            var tCount = 0
            while (m2.find()) {
                val dur = BigDecimal(m2.group(1))
                if (ft == BigDecimal.ZERO) ft = dur
                lt = dur
                t = t.add(lt)
                tCount += 1
            }

            val ftStr = ft.toString()
            val ltStr = lt.toString()
            val tStr = t.toString()
            for (ad in ads) {
                if (ad.startsWith("-")) {
                    val adClean = ad.substring(1)
                    // Match the last segment duration.
                    if (ltStr.startsWith(adClean)) {
                        needRemoveAd.add(groupCleaned)
                        currentAdCount += tCount
                        break
                    }
                } else {
                    // Match the first segment duration or total ad duration.
                    if (ftStr.startsWith(ad) || tStr.startsWith(ad)) {
                        needRemoveAd.add(groupCleaned)
                        currentAdCount += tCount
                        break
                    }
                }
            }
        }
        for (rem in needRemoveAd) {
            line = line.replace(rem, "")
        }
        return line
    }

    private fun isDouble(ad: String): Boolean {
        try {
            return ad.toDouble() != 0.0
        } catch (e: Exception) {
            return false
        }
    }

    private fun shouldResolve(line: String): Boolean {
        val item = line.trim()
        if (item.isEmpty()) return false
        return (!item.startsWith("#") && !item.startsWith("http")) || hasUriAttribute(item)
    }

    private fun resolve(base: String, line: String): String {
        return if (hasUriAttribute(line)) {
            resolveUriLine(base, line)
        } else {
            UriUtil.resolve(base, line)
        }
    }
}
