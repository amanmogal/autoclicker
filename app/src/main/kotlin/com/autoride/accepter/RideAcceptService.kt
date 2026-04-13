package com.autoride.accepter

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.util.Log
import android.view.MotionEvent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.ImageView
import android.widget.TextView
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class RideAcceptService : AccessibilityService() {

    private val filterEngine = FilterEngine()
    private var isAutoAcceptEnabled = false
    private var lastActionAtMs = 0L
    private var lastActionSignature: String? = null
    private var lastLoggedRidePackage: String? = null
    private var lastSettingsSnapshot: String? = null
    private var lastScrapeLogAtMs = 0L
    private var lastScrapeSnapshot: String? = null
    private var lastFullTreeScanAtMs = 0L
    private var lastOverlayToggleAtMs = 0L
    private var lastNoTargetLogAtMs = 0L
    private var lastEventFare = 0.0
    private var lastEventDistance = 0.0
    private var lastEventDataAtMs = 0L

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private var overlayAttached = false
    private var settingsPrefs: SharedPreferences? = null
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key.startsWith("service") || key.contains("Fare") || key.contains("Distance") || key.contains("fare") || key.contains("distance")) {
            updateSettings()
        }
    }

    companion object {
        private const val TAG = "RideAcceptService"
        private const val ACTION_COOLDOWN_MS = 1L
        private const val REPEAT_ACTION_SUPPRESS_MS = 400L
        private const val SCRAPE_LOG_THROTTLE_MS = 1500L
        private const val FULL_TREE_SCAN_THROTTLE_MS = 80L
        private const val OVERLAY_TOGGLE_DEBOUNCE_MS = 700L
        private const val OVERLAY_TAP_MAX_DURATION_MS = 350L
        private const val NO_TARGET_LOG_THROTTLE_MS = 2000L
        private const val EVENT_DATA_TTL_MS = 2500L

        private val ACCEPT_KEYWORDS = setOf(
            // Keep this strict to avoid false positives like "Go To".
            "accept",
            "confirm",
            "match",
            "agree",
            "swipe to accept",
            "accept ride",
            "book now",
            // Indic language variants seen in the legacy APK.
            "स्वीकार", "स्वीकार करें", "पुष्टि", "पुष्टि करें", "मिलान", "मेल खाना",
            "स्वीकार करा", "स्वीकारा", "पुष्टी करा", "पुष्टी", "जुळवा", "जुळणे", "जुळणी",
            "ஏற்க", "ஒப்புக் கொள்", "ஏற்றுக்கொள்", "உறுதிப்படுத்து", "பொருந்து", "பொருத்தம்",
            "స్వీకరించు", "అంగీకరించు", "నిర్ధారించు", "ధృవీకరించు", "సరిపోల్చు", "సరిపోవు", "సరిపోలిక",
            "ಸ್ವೀಕರಿಸಿ", "ಒಪ್ಪಿಕೊ", "ದೃಢಪಡಿಸಿ", "ಖಚಿತಪಡಿಸು", "ಹೊಂದಿಕೆ", "ಹೊಂದಿಸು", "ಹೊಂದಾಣಿಕೆ"
        )

        private val BLOCKED_ACTION_PHRASES = setOf(
            "go to",
            "start ride",
            "navigate",
            "pickup",
            "drop",
            "arrived",
            "arrive",
            "call rider",
            "chat",
            "cancel ride",
            "online",
            "offline"
        )

        private val SWIPE_KEYWORDS = setOf("swipe", "slide", "drag", "खींच", "ಸ್ಲೈಡ್")
        private val CURRENCY_TOKEN_REGEX = Regex("""(?:₹|\b(?:rs|inr)\.?\b)""", RegexOption.IGNORE_CASE)
        private val DISTANCE_TOKEN_REGEX = Regex(
            """\d+([.,]\d+)?\s*(km|kilometer|kilometre|किमी|ಕಿ\.?ಮೀ|meter|metre|mtr|मीटर|ಮೀಟರ್)\b""",
            RegexOption.IGNORE_CASE
        )
        private val METER_DISTANCE_TOKEN_REGEX = Regex("""\d+([.,]\d+)?\s*(meter|metre|mtr|मीटर|ಮೀಟರ್)\b""", RegexOption.IGNORE_CASE)

        // Vendor token plus a driver-role token is required.
        // This avoids matching consumer app IDs like *.customer.
        private val RIDE_VENDOR_TOKENS = setOf("ola", "rapido", "uber", "namma", "yatri")
        private val DRIVER_ROLE_TOKENS = setOf("driver", "rider", "captain", "partner", "sathi", "pilot")
        private val BLOCKED_ROLE_TOKENS = setOf("customer", "passenger", "consumer", "client", "rental")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service Connected - Listening for target apps")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        settingsPrefs = getSharedPreferences("RideFilters", Context.MODE_PRIVATE).also {
            it.registerOnSharedPreferenceChangeListener(settingsListener)
        }
        showOverlay()
        updateSettings()
    }

    private fun showOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_status, null)
        overlayLayoutParams = params
        attachOverlayTouchController()
        runCatching {
            windowManager.addView(overlayView, params)
            overlayAttached = true
        }.onFailure { error ->
            Log.e(TAG, "Overlay attach failed", error)
        }
        updateOverlayStatus()
    }

    private fun attachOverlayTouchController() {
        val view = overlayView ?: return
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var downAtMs = 0L
        var moved = false

        view.setOnTouchListener { _, event ->
            val params = overlayLayoutParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    downAtMs = System.currentTimeMillis()
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (!moved && (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop)) {
                        moved = true
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    if (overlayAttached) {
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val pressDuration = System.currentTimeMillis() - downAtMs
                    if (!moved) {
                        view.performClick()
                        if (pressDuration >= 650L) {
                            launchMainActivityFromOverlay()
                        } else if (pressDuration <= OVERLAY_TAP_MAX_DURATION_MS) {
                            val now = System.currentTimeMillis()
                            if (now - lastOverlayToggleAtMs >= OVERLAY_TOGGLE_DEBOUNCE_MS) {
                                lastOverlayToggleAtMs = now
                                toggleServiceEnabledFromOverlay()
                            } else {
                                Log.d(TAG, "Overlay tap ignored (debounced)")
                            }
                        }
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun toggleServiceEnabledFromOverlay() {
        val prefs = getSharedPreferences("RideFilters", Context.MODE_PRIVATE)
        if (isAutoAcceptEnabled) {
            Log.d(TAG, "Overlay tap ignored: service already enabled")
            return
        }
        val nextEnabled = true
        prefs.edit()
            .putBoolean("serviceEnabled", nextEnabled)
            .putBoolean("service_enabled", nextEnabled)
            .apply()
        updateSettings()
        Log.d(TAG, "Overlay toggle: serviceEnabled=$nextEnabled")
    }

    private fun launchMainActivityFromOverlay() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { startActivity(intent) }.onFailure { error ->
            Log.e(TAG, "Unable to open MainActivity from overlay", error)
        }
    }

    private fun updateOverlayStatus() {
        overlayView?.let {
            val icon = it.findViewById<ImageView>(R.id.statusIcon)
            val statusText = it.findViewById<TextView>(R.id.statusText)
            if (isAutoAcceptEnabled) {
                icon.setColorFilter(Color.GREEN)
                it.background.setTint(Color.parseColor("#CC000000"))
                statusText.text = getString(R.string.overlay_status_on)
            } else {
                icon.setColorFilter(Color.RED)
                it.background.setTint(Color.parseColor("#CC444444"))
                statusText.text = getString(R.string.overlay_status_off)
            }
        }
    }

    private fun updateSettings() {
        val prefs = settingsPrefs ?: getSharedPreferences("RideFilters", Context.MODE_PRIVATE)
        isAutoAcceptEnabled = readBooleanPref(
            prefs,
            "serviceEnabled",
            "service_enabled",
            true
        )

        filterEngine.minFare = readFloatPref(prefs, "minFare", "min_fare", 100.0f)
        filterEngine.maxFare = readFloatPref(prefs, "maxFare", "max_fare", 5000.0f)
        filterEngine.minDistance = readFloatPref(prefs, "minDistance", "min_pickup_km", 0.0f)
        filterEngine.maxDistance = readFloatPref(prefs, "maxDistance", "max_pickup_km", 5.0f)
        filterEngine.minFarePerKm = readFloatPref(prefs, "minFarePerKm", "min_fare_per_km", 15.0f)
        filterEngine.allowFareOnlyWhenDistanceMissing = readBooleanPref(
            prefs,
            "allowFareOnlyWhenDistanceMissing",
            "distance_filter_enabled",
            true
        )

        val settingsSnapshot =
            "Enabled=$isAutoAcceptEnabled|Fare=${filterEngine.minFare}-${filterEngine.maxFare}|Distance=${filterEngine.minDistance}-${filterEngine.maxDistance}|MinFarePerKm=${filterEngine.minFarePerKm}|AllowFareOnly=${filterEngine.allowFareOnlyWhenDistanceMissing}"
        if (lastSettingsSnapshot != settingsSnapshot) {
            lastSettingsSnapshot = settingsSnapshot
            Log.d(TAG, "Settings Updated: $settingsSnapshot")
        }
        updateOverlayStatus()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            updateSettings()
        }

        if (!isAutoAcceptEnabled) return

        val packageName = event.packageName?.toString() ?: return

        if (!isRideApp(packageName)) return
        captureEventData(event)

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) {
            processAllWindows()
        }
    }

    private fun processAllWindows() {
        var processedWindow = false

        for (window in windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val rootNode = window.root ?: continue
            processedWindow = true
            processScreenContent(rootNode, windowTypeToString(window.type))
        }

        if (!processedWindow) {
            rootInActiveWindow?.let { processScreenContent(it, "ACTIVE_WINDOW") }
        }
    }

    private fun processScreenContent(rootNode: AccessibilityNodeInfo, windowType: String) {
        val scraperData = ScraperData()
        val fastNode = findAcceptNodeByText(rootNode)
        if (fastNode != null) {
            scraperData.targetNode = fastNode
            scraperData.isSwipe = isSwipeText(extractNodeText(fastNode))
            extractMetricsNearTarget(fastNode, scraperData)
        }

        // Full tree scan is slower; run when fast path misses data or target.
        if (scraperData.targetNode == null || scraperData.fare <= 0.0 || scraperData.distance <= 0.0) {
            val now = System.currentTimeMillis()
            if (now - lastFullTreeScanAtMs >= FULL_TREE_SCAN_THROTTLE_MS) {
                lastFullTreeScanAtMs = now
                val fullData = ScraperData()
                extractData(rootNode, fullData)
                mergeScraperData(scraperData, fullData)
            }
        }

        if (isEventDataFresh()) {
            if (scraperData.fare <= 0.0 && lastEventFare > 0.0) {
                scraperData.fare = lastEventFare
            }
            if (scraperData.distance <= 0.0 && lastEventDistance > 0.0) {
                scraperData.distance = lastEventDistance
            }
        }

        if (scraperData.targetNode == null && scraperData.fare > 0.0 && scraperData.fallbackTargetNode != null) {
            scraperData.targetNode = scraperData.fallbackTargetNode
            scraperData.isSwipe = true
            Log.d(TAG, "[$windowType] Using fallback clickable target (score=${scraperData.fallbackTargetScore})")
        }

        val targetNode = scraperData.targetNode ?: run {
            maybeLogNoTarget(windowType, scraperData)
            return
        }
        val fare = scraperData.fare
        val distance = scraperData.distance

        maybeLogScrapedData(windowType, fare, distance)

        if (!filterEngine.shouldAccept(fare, distance)) return
        val actionSignature = buildActionSignature(windowType, targetNode, fare, distance)
        if (!canAttemptAction(actionSignature)) return

        Log.d(TAG, "[$windowType] Acceptance Logic Match - Triggering Automation")
        if (scraperData.isSwipe) {
            performSwipeAction(targetNode, windowType, actionSignature)
        } else {
            clickNode(targetNode, windowType, actionSignature)
        }
    }

    private class ScraperData {
        var targetNode: AccessibilityNodeInfo? = null
        var fare: Double = 0.0
        var distance: Double = 0.0
        var isSwipe: Boolean = false
        var fallbackTargetNode: AccessibilityNodeInfo? = null
        var fallbackTargetScore: Int = Int.MIN_VALUE
    }

    private fun extractData(node: AccessibilityNodeInfo, data: ScraperData) {
        val text = extractNodeText(node)

        if (text.isNotBlank()) {
            if (isAcceptText(text) && data.targetNode == null) {
                data.targetNode = node
                data.isSwipe = isSwipeText(text)
            } else if (isAcceptText(text) && node.isClickable && data.targetNode?.isClickable != true) {
                data.targetNode = node
                data.isSwipe = isSwipeText(text)
            }

            if (containsCurrency(text)) {
                val parsedFare = parseFareAmount(text)
                if (parsedFare > data.fare) data.fare = parsedFare
            }

            if (containsDistance(text)) {
                val parsedDist = filterEngine.parseAmount(text)
                if (parsedDist > 0.0) {
                    val normalized = text.lowercase()
                    val isPickupDistance = normalized.contains("pickup") ||
                        normalized.contains("pick up") ||
                        normalized.contains("pick-up")

                    if (isPickupDistance) {
                        data.distance = parsedDist
                    } else if (data.distance == 0.0) {
                        data.distance = parsedDist
                    }
                }
            }
        }

        maybeTrackFallbackTarget(node, data)

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            extractData(child, data)
        }
    }

    private fun clickNode(node: AccessibilityNodeInfo, windowType: String, actionSignature: String): Boolean {
        val nodeText = extractNodeText(node).ifBlank { "<no-text>" }
        Log.d(TAG, "[$windowType] Clicking node: $nodeText")

        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            Log.d(TAG, "[$windowType] Direct click success")
            markActionExecuted(actionSignature)
            return true
        }

        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 10) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(TAG, "[$windowType] Parent click success at depth $depth")
                markActionExecuted(actionSignature)
                return true
            }
            parent = parent.parent
            depth += 1
        }

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() > 0 && bounds.height() > 0) {
            val path = Path().apply {
                moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat())
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 1))
                .build()
            val dispatched = dispatchGesture(gesture, gestureCallback(windowType), null)
            if (dispatched) {
                markActionExecuted(actionSignature)
            }
            return dispatched
        }

        val forced = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.d(TAG, "[$windowType] Force click ${if (forced) "success" else "failed"}")
        if (forced) {
            markActionExecuted(actionSignature)
        }
        return forced
    }

    private fun performSwipeAction(
        node: AccessibilityNodeInfo,
        windowType: String,
        actionSignature: String
    ): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return clickNode(node, windowType, actionSignature)
        }

        val startX = bounds.left + (bounds.width() * 0.2f)
        val endX = bounds.right - (bounds.width() * 0.2f)
        val y = bounds.centerY().toFloat()

        val path = Path().apply {
            moveTo(startX, y)
            lineTo(endX, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 200))
            .build()

        val dispatched = dispatchGesture(gesture, gestureCallback(windowType), null)
        if (dispatched) {
            markActionExecuted(actionSignature)
            Log.d(TAG, "[$windowType] Swipe gesture dispatched")
            return true
        }
        return clickNode(node, windowType, actionSignature)
    }

    private fun gestureCallback(windowType: String): GestureResultCallback {
        return object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "[$windowType] Gesture success")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.e(TAG, "[$windowType] Gesture failed")
            }
        }
    }

    private fun canAttemptAction(actionSignature: String): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastActionAtMs < ACTION_COOLDOWN_MS) {
            return false
        }
        if (actionSignature == lastActionSignature && now - lastActionAtMs < REPEAT_ACTION_SUPPRESS_MS) {
            return false
        }
        return true
    }

    private fun buildActionSignature(
        windowType: String,
        node: AccessibilityNodeInfo,
        fare: Double,
        distance: Double
    ): String {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val label = extractNodeText(node).lowercase().trim().ifBlank { "<no-text>" }
        val fareBucket = String.format(Locale.US, "%.0f", fare)
        val distanceBucket = String.format(Locale.US, "%.1f", distance)
        return "$windowType|$label|$fareBucket|$distanceBucket|${bounds.centerX()}|${bounds.centerY()}"
    }

    private fun markActionExecuted(actionSignature: String) {
        lastActionAtMs = System.currentTimeMillis()
        lastActionSignature = actionSignature
    }

    private fun maybeLogScrapedData(windowType: String, fare: Double, distance: Double) {
        val now = System.currentTimeMillis()
        val snapshot = String.format(Locale.US, "%.2f|%.2f", fare, distance)
        if (snapshot != lastScrapeSnapshot || now - lastScrapeLogAtMs >= SCRAPE_LOG_THROTTLE_MS) {
            lastScrapeSnapshot = snapshot
            lastScrapeLogAtMs = now
            Log.d(TAG, "[$windowType] Scraped Data - Fare: $fare, Distance: $distance")
        }
    }

    private fun maybeLogNoTarget(windowType: String, data: ScraperData) {
        val now = System.currentTimeMillis()
        if (now - lastNoTargetLogAtMs >= NO_TARGET_LOG_THROTTLE_MS) {
            lastNoTargetLogAtMs = now
            Log.d(
                TAG,
                "[$windowType] No accept target found (fare=${data.fare}, distance=${data.distance})"
            )
        }
    }

    private fun isEventDataFresh(): Boolean {
        return System.currentTimeMillis() - lastEventDataAtMs <= EVENT_DATA_TTL_MS
    }

    private fun captureEventData(event: AccessibilityEvent) {
        var bestFare = 0.0
        var bestDistance = 0.0
        val candidates = mutableListOf<String>()

        event.text?.forEach { value ->
            value?.toString()?.let { candidates.add(it) }
        }
        event.contentDescription?.toString()?.let { candidates.add(it) }
        event.source?.let { source ->
            val sourceText = extractNodeText(source)
            if (sourceText.isNotBlank()) {
                candidates.add(sourceText)
            }
        }

        for (candidate in candidates) {
            if (candidate.isBlank()) continue
            if (containsCurrency(candidate)) {
                val fare = parseFareAmount(candidate)
                if (fare > bestFare) bestFare = fare
            }
            if (containsDistance(candidate)) {
                val distance = parseDistanceKm(candidate)
                if (distance > bestDistance) bestDistance = distance
            }
        }

        if (bestFare > 0.0 || bestDistance > 0.0) {
            if (bestFare > 0.0) lastEventFare = bestFare
            if (bestDistance > 0.0) lastEventDistance = bestDistance
            lastEventDataAtMs = System.currentTimeMillis()
        }
    }

    private fun findAcceptNodeByText(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val probeKeywords = listOf("Accept", "accept", "Confirm", "confirm", "Match", "match", "Swipe", "swipe")
        for (keyword in probeKeywords) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)
            val candidate = nodes.firstOrNull { isAcceptText(extractNodeText(it)) }
            if (candidate != null) {
                return candidate
            }
        }
        return null
    }

    private fun extractMetricsNearTarget(targetNode: AccessibilityNodeInfo, data: ScraperData) {
        val textCandidates = linkedSetOf<String>()

        fun collectAround(node: AccessibilityNodeInfo?) {
            if (node == null) return
            collectTextCandidates(node, depth = 0, maxDepth = 2, out = textCandidates)
            val parent = node.parent
            if (parent != null) {
                collectTextCandidates(parent, depth = 0, maxDepth = 2, out = textCandidates)
                collectTextCandidates(parent.parent, depth = 0, maxDepth = 1, out = textCandidates)
            }
        }

        collectAround(targetNode)

        var bestDistanceScore = Int.MIN_VALUE
        var bestDistance = data.distance

        for (candidateText in textCandidates) {
            if (candidateText.isBlank()) continue

            if (containsCurrency(candidateText)) {
                val parsedFare = parseFareAmount(candidateText)
                if (parsedFare > data.fare) {
                    data.fare = parsedFare
                }
            }

            if (containsDistance(candidateText)) {
                val (distanceValue, score) = updateDistanceCandidate(candidateText)
                if (distanceValue > 0.0 && score >= bestDistanceScore) {
                    bestDistanceScore = score
                    bestDistance = distanceValue
                }
            }
        }

        if (bestDistance > 0.0) {
            data.distance = bestDistance
        }
    }

    private fun mergeScraperData(primary: ScraperData, fallback: ScraperData) {
        if (primary.targetNode == null) {
            primary.targetNode = fallback.targetNode
            primary.isSwipe = fallback.isSwipe
        }
        if (fallback.fare > primary.fare) {
            primary.fare = fallback.fare
        }
        if (primary.distance <= 0.0 && fallback.distance > 0.0) {
            primary.distance = fallback.distance
        }
        if (primary.fallbackTargetNode == null || fallback.fallbackTargetScore > primary.fallbackTargetScore) {
            primary.fallbackTargetNode = fallback.fallbackTargetNode
            primary.fallbackTargetScore = fallback.fallbackTargetScore
        }
    }

    private fun collectTextCandidates(
        node: AccessibilityNodeInfo?,
        depth: Int,
        maxDepth: Int,
        out: MutableSet<String>
    ) {
        if (node == null || depth > maxDepth) return
        val text = extractNodeText(node).trim()
        if (text.isNotEmpty()) {
            out.add(text)
        }
        for (i in 0 until node.childCount) {
            collectTextCandidates(node.getChild(i), depth + 1, maxDepth, out)
        }
    }

    private fun updateDistanceCandidate(text: String): Pair<Double, Int> {
        val normalized = text.lowercase()
        val distanceKm = parseDistanceKm(text)
        if (distanceKm <= 0.0) return 0.0 to Int.MIN_VALUE

        var score = 0
        if (normalized.contains("pickup") || normalized.contains("pick up") || normalized.contains("pick-up")) {
            score += 5
        }
        if (normalized.contains("distance")) {
            score += 2
        }
        if (DISTANCE_TOKEN_REGEX.containsMatchIn(normalized)) {
            score += 2
        }
        if (normalized.contains("drop")) {
            score -= 1
        }
        return distanceKm to score
    }

    private fun parseDistanceKm(text: String): Double {
        val normalized = text.lowercase()
        val tokenMatch = DISTANCE_TOKEN_REGEX.find(normalized)
        if (tokenMatch != null) {
            val raw = tokenMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
            if (raw <= 0.0) return 0.0
            val unit = tokenMatch.groupValues[2].lowercase()
            return if (unit.startsWith("m") || unit.contains("मीटर") || unit.contains("ಮೀಟರ್")) {
                raw / 1000.0
            } else {
                raw
            }
        }

        val fallback = filterEngine.parseAmount(normalized)
        if (fallback <= 0.0) return 0.0
        return if (METER_DISTANCE_TOKEN_REGEX.containsMatchIn(normalized)) fallback / 1000.0 else fallback
    }

    private fun maybeTrackFallbackTarget(node: AccessibilityNodeInfo, data: ScraperData) {
        if (!node.isClickable || !node.isEnabled) return
        if (data.targetNode != null) return

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val width = bounds.width()
        val height = bounds.height()
        if (width <= 0 || height <= 0) return
        if (width < 260 || height < 48 || height > 420) return

        val text = extractNodeText(node).lowercase().trim()
        if (text.contains("home") || text.contains("orders") || text.contains("earnings") || text.contains("quick actions")) {
            return
        }
        if (text.contains("go to") || text.contains("arrived") || text.contains("cancel ride")) {
            return
        }

        val activeRoot = rootInActiveWindow
        val screenHeight = if (activeRoot != null) {
            val r = Rect()
            activeRoot.getBoundsInScreen(r)
            max(r.bottom, bounds.bottom)
        } else {
            bounds.bottom
        }

        var score = 0
        if (width >= 420) score += 3
        if (width >= 600) score += 3
        if (bounds.centerY() >= (screenHeight * 0.45).toInt()) score += 2
        if (bounds.centerY() >= (screenHeight * 0.60).toInt()) score += 2
        if (height in 60..260) score += 2
        if (text.isBlank()) score += 1
        if (isAcceptText(text)) score += 6

        if (score > data.fallbackTargetScore) {
            data.fallbackTargetScore = score
            data.fallbackTargetNode = node
        }
    }

    private fun isRideApp(packageName: String): Boolean {
        val normalized = packageName.lowercase()
        val segments = normalized.split('.')
        val hasVendor = segments.any { segment ->
            RIDE_VENDOR_TOKENS.any { vendor -> segment.startsWith(vendor) }
        }
        val hasDriverRole = segments.any { segment ->
            DRIVER_ROLE_TOKENS.any { role -> segment.contains(role) }
        }
        val hasBlockedRole = segments.any { segment ->
            BLOCKED_ROLE_TOKENS.any { blocked -> segment.contains(blocked) }
        }

        val matched = hasVendor && hasDriverRole && !hasBlockedRole
        if (matched && lastLoggedRidePackage != packageName) {
            lastLoggedRidePackage = packageName
            Log.d(TAG, "Ride app detected: $packageName")
        }
        return matched
    }

    private fun isAcceptText(text: String): Boolean {
        val normalized = text
            .lowercase()
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (normalized.isBlank()) return false
        if (BLOCKED_ACTION_PHRASES.any { blocked -> normalized.contains(blocked) }) return false
        return ACCEPT_KEYWORDS.any { keyword -> normalized.contains(keyword) }
    }

    private fun isSwipeText(text: String): Boolean {
        val normalized = text.lowercase()
        return SWIPE_KEYWORDS.any { normalized.contains(it.lowercase()) }
    }

    private fun containsCurrency(text: String): Boolean {
        return CURRENCY_TOKEN_REGEX.containsMatchIn(text)
    }

    private fun containsDistance(text: String): Boolean {
        val normalized = text.lowercase()
        val hasKmToken = DISTANCE_TOKEN_REGEX.containsMatchIn(normalized)
        if (hasKmToken) {
            return true
        }
        val hasContextWord = normalized.contains("pickup") || normalized.contains("drop") || normalized.contains("distance")
        val hasNumber = Regex("""\d+([.,]\d+)?""").containsMatchIn(normalized)
        return hasContextWord && hasNumber
    }

    private fun parseFareAmount(text: String): Double {
        val normalized = text.replace(",", ".")
        val compact = normalized.replace(Regex("""[^0-9+.]"""), "")
        if (compact.isBlank()) {
            return 0.0
        }
        if (compact.contains('+')) {
            val total = compact
                .split('+')
                .mapNotNull { token -> token.toDoubleOrNull() }
                .sum()
            if (total > 0.0) {
                return total
            }
        }
        return filterEngine.parseAmount(normalized)
    }

    private fun readFloatPref(
        prefs: SharedPreferences,
        primaryKey: String,
        legacyKey: String,
        defaultValue: Float
    ): Double {
        if (prefs.contains(primaryKey)) {
            return runCatching { prefs.getFloat(primaryKey, defaultValue) }
                .getOrElse {
                    prefs.getString(primaryKey, defaultValue.toString())?.toFloatOrNull() ?: defaultValue
                }
                .toDouble()
        }

        if (prefs.contains(legacyKey)) {
            return runCatching { prefs.getFloat(legacyKey, defaultValue) }
                .getOrElse {
                    prefs.getString(legacyKey, defaultValue.toString())?.toFloatOrNull() ?: defaultValue
                }
                .toDouble()
        }

        return defaultValue.toDouble()
    }

    private fun readBooleanPref(
        prefs: SharedPreferences,
        primaryKey: String,
        legacyKey: String,
        defaultValue: Boolean
    ): Boolean {
        if (prefs.contains(primaryKey)) {
            return prefs.getBoolean(primaryKey, defaultValue)
        }
        if (prefs.contains(legacyKey)) {
            return prefs.getBoolean(legacyKey, defaultValue)
        }
        return defaultValue
    }

    private fun extractNodeText(node: AccessibilityNodeInfo): String {
        return node.text?.toString() ?: node.contentDescription?.toString() ?: ""
    }

    private fun windowTypeToString(windowType: Int): String {
        return when (windowType) {
            AccessibilityWindowInfo.TYPE_APPLICATION -> "APP"
            AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "KEYBOARD"
            AccessibilityWindowInfo.TYPE_SYSTEM -> "SYSTEM"
            AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "A11Y_OVERLAY"
            else -> "WINDOW_$windowType"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        settingsPrefs?.unregisterOnSharedPreferenceChangeListener(settingsListener)
        settingsPrefs = null
        overlayView?.let {
            runCatching { windowManager.removeView(it) }
            overlayAttached = false
        }
    }

    override fun onInterrupt() {}
}
