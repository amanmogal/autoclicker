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
import android.os.Handler
import android.os.Looper
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
import android.widget.TextView
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class RideAcceptService : AccessibilityService() {

    private val filterEngine = FilterEngine()
    private var isAutoAcceptEnabled = false
    private var lastActionAtMs = 0L
    private var lastActionSignature: String? = null
    private var pendingActionSignature: String? = null
    private var pendingActionAtMs = 0L
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
    private val scanHandler = Handler(Looper.getMainLooper())
    private val periodicScanRunnable = object : Runnable {
        override fun run() {
            if (!isAutoAcceptEnabled) return
            ensureOverlayAttached()
            if (!authManager.hasActiveAccess()) {
                Log.w(TAG, "Access expired or revoked while service was running; disabling automation.")
                AppAccessController.disableAutomation(this@RideAcceptService)
                updateSettings()
                return
            }
            runCatching { processAllWindows() }
                .onFailure { error -> Log.e(TAG, "Periodic scan failed", error) }
            scanHandler.postDelayed(this, PERIODIC_SCAN_MS)
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var authManager: AuthManager
    private var overlayView: View? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private var overlayRestoreRunnable: Runnable? = null
    private var overlayAttached = false
    private var settingsPrefs: SharedPreferences? = null
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key.startsWith("service") || key.contains("Fare") || key.contains("Distance") || key.contains("fare") || key.contains("distance")) {
            updateSettings()
        }
    }

    companion object {
        private const val TAG = "RideAcceptService"
        private const val ACTION_COOLDOWN_MS = 12L
        private const val REPEAT_ACTION_SUPPRESS_MS = 400L
        private const val PENDING_ACTION_TIMEOUT_MS = 1200L
        private const val SCRAPE_LOG_THROTTLE_MS = 1500L
        private const val FULL_TREE_SCAN_THROTTLE_MS = 80L
        private const val OVERLAY_TOGGLE_DEBOUNCE_MS = 700L
        private const val OVERLAY_TAP_MAX_DURATION_MS = 350L
        private const val OVERLAY_GESTURE_RESTORE_DELAY_MS = 250L
        private const val NO_TARGET_LOG_THROTTLE_MS = 2000L
        private const val EVENT_DATA_TTL_MS = 2500L
        private const val PERIODIC_SCAN_MS = 1000L
        private const val MAX_OFFER_CONTAINER_CANDIDATES = 12

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

        private val ACCEPT_TEXT_PROBE_KEYWORDS = ACCEPT_KEYWORDS +
            ACCEPT_KEYWORDS.map { keyword ->
                keyword.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString()
                }
            }

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
            """(\d+(?:[.,]\d+)?)\s*(km|kilometer|kilometre|किमी|ಕಿ\.?ಮೀ|meter|metre|mtr|मीटर|ಮೀಟರ್)\b""",
            RegexOption.IGNORE_CASE
        )

    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service Connected - Listening for target apps")
        authManager = AuthManager(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        settingsPrefs = getSharedPreferences("RideFilters", Context.MODE_PRIVATE).also {
            it.registerOnSharedPreferenceChangeListener(settingsListener)
        }
        showOverlay()
        updateSettings()
        schedulePeriodicScan()
    }

    private fun showOverlay() {
        if (ensureOverlayAttached()) return

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

    private fun ensureOverlayAttached(): Boolean {
        val view = overlayView ?: return false
        val params = overlayLayoutParams ?: return false

        if (overlayAttached && view.isAttachedToWindow) {
            if (pendingActionSignature == null && overlayRestoreRunnable == null && view.visibility != View.VISIBLE) {
                view.visibility = View.VISIBLE
            }
            return true
        }

        overlayAttached = false
        runCatching {
            windowManager.addView(view, params)
            overlayAttached = true
            if (pendingActionSignature == null && overlayRestoreRunnable == null) {
                view.visibility = View.VISIBLE
            }
            Log.d(TAG, "Accessibility overlay reattached")
        }.onFailure { error ->
            Log.e(TAG, "Overlay reattach failed", error)
        }
        return overlayAttached
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
        val nextEnabled = !isAutoAcceptEnabled
        if (nextEnabled) {
            if (!authManager.isLoggedIn()) {
                Log.w(TAG, "Overlay toggle blocked: login required")
                return
            }
            if (!authManager.hasActiveAccess()) {
                Log.w(TAG, "Overlay toggle blocked: manual renewal required")
                AppAccessController.disableAutomation(this)
                updateSettings()
                return
            }
        }

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
            val statusText = it.findViewById<TextView>(R.id.statusText)
            if (isAutoAcceptEnabled) {
                it.background?.setTint(Color.parseColor("#CC15233F"))
                statusText.text = getString(R.string.overlay_status_on)
            } else {
                it.background?.setTint(Color.parseColor("#CC2A3344"))
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
        if (isAutoAcceptEnabled && !authManager.hasActiveAccess()) {
            Log.w(TAG, "Stored automation flag ignored because access is not active.")
            AppAccessController.disableAutomation(this)
            isAutoAcceptEnabled = false
        }

        filterEngine.minFare = readFloatPref(prefs, "minFare", "min_fare", 100.0f).toDouble()
        filterEngine.maxFare = readFloatPref(prefs, "maxFare", "max_fare", 5000.0f).toDouble()
        filterEngine.minDistance = readFloatPref(prefs, "minDistance", "min_pickup_km", 0.0f).toDouble()
        filterEngine.maxDistance = readFloatPref(prefs, "maxDistance", "max_pickup_km", 5.0f).toDouble()
        filterEngine.minFarePerKm = readFloatPref(prefs, "minFarePerKm", "min_fare_per_km", 15.0f).toDouble()
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
        ensureOverlayAttached()
        if (isAutoAcceptEnabled) {
            schedulePeriodicScan()
        } else {
            scanHandler.removeCallbacks(periodicScanRunnable)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            updateSettings()
            ensureOverlayAttached()
        }

        if (!isAutoAcceptEnabled) return

        val packageName = event.packageName?.toString()
        val visibleRideWindow = hasRideAppWindow()
        val eventLooksLikeRideOffer = eventContainsRideOfferSignals(event)
        if ((packageName == null || !isRideApp(packageName)) && !visibleRideWindow && !eventLooksLikeRideOffer) return
        captureEventData(event)

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) {
            processAllWindows()
        }
    }

    private fun schedulePeriodicScan() {
        scanHandler.removeCallbacks(periodicScanRunnable)
        if (isAutoAcceptEnabled) {
            scanHandler.postDelayed(periodicScanRunnable, PERIODIC_SCAN_MS)
        }
    }

    private fun hasRideAppWindow(): Boolean {
        val matchedWindow = windows.firstOrNull { window ->
            val rootPackage = window.root?.packageName?.toString() ?: return@firstOrNull false
            isRideApp(rootPackage)
        }
        if (matchedWindow != null) return true
        val activePackage = rootInActiveWindow?.packageName?.toString() ?: return false
        return isRideApp(activePackage)
    }

    private fun processAllWindows() {
        val candidates = mutableListOf<RideCandidate>()
        var scannedAnyRideWindow = false

        for (window in windows) {
            val rootNode = window.root ?: continue
            val rootPackage = rootNode.packageName?.toString().orEmpty()
            val rootHasOfferSignals = rootHasRideOfferSignals(rootNode)
            if (!RideWindowPolicy.shouldScanWindow(
                    windowType = window.type,
                    rootPackage = rootPackage,
                    appPackage = applicationContext.packageName,
                    rootHasOfferSignals = rootHasOfferSignals
                )
            ) {
                continue
            }
            scannedAnyRideWindow = true
            processScreenContent(
                rootNode = rootNode,
                windowType = windowTypeToString(window.type),
                packageName = rootPackage
            ).let { candidates.addAll(it) }
        }

        if (!scannedAnyRideWindow) {
            rootInActiveWindow?.let { activeRoot ->
                val activePackage = activeRoot.packageName?.toString().orEmpty()
                if (isRideApp(activePackage) ||
                    (isSystemOverlayHostPackage(activePackage) && rootHasRideOfferSignals(activeRoot))
                ) {
                    processScreenContent(
                        rootNode = activeRoot,
                        windowType = "ACTIVE_WINDOW",
                        packageName = activePackage
                    ).let { candidates.addAll(it) }
                }
            }
        }

        val bestCandidate = candidates.maxByOrNull { it.rankScore } ?: return
        if (!canAttemptAction(bestCandidate.actionSignature)) return

        Log.d(
            TAG,
            "[${bestCandidate.windowType}] Prioritized ride from ${bestCandidate.packageName}: " +
                "fare=${bestCandidate.fare}, distance=${bestCandidate.distance}, score=${bestCandidate.rankScore}"
        )
        if (bestCandidate.isSwipe) {
            performSwipeAction(bestCandidate.targetNode, bestCandidate.windowType, bestCandidate.actionSignature)
        } else {
            clickNode(bestCandidate.targetNode, bestCandidate.windowType, bestCandidate.actionSignature)
        }
    }

    private fun processScreenContent(
        rootNode: AccessibilityNodeInfo,
        windowType: String,
        packageName: String
    ): List<RideCandidate> {
        val acceptNodes = findAcceptNodesByText(rootNode)
        if (acceptNodes.isNotEmpty()) {
            val candidates = acceptNodes.mapNotNull { targetNode ->
                buildCandidateFromTarget(
                    targetNode = targetNode,
                    windowType = windowType,
                    packageName = packageName,
                    allowEventMetricFallback = acceptNodes.size == 1
                )
            }
            if (candidates.isNotEmpty()) return candidates
            Log.d(TAG, "[$windowType] Accept target found without valid metrics; trying bounded fallback scan")

            val targetContainerCandidates = buildCandidatesFromAcceptContainers(
                acceptNodes = acceptNodes,
                windowType = windowType,
                packageName = packageName
            )
            if (targetContainerCandidates.isNotEmpty()) return targetContainerCandidates
        }

        val containerCandidates = buildCandidatesFromOfferContainers(
            rootNode = rootNode,
            windowType = windowType,
            packageName = packageName
        )
        if (containerCandidates.isNotEmpty()) return containerCandidates
        if (acceptNodes.size > 1) return emptyList()

        val scraperData = ScraperData()

        val now = System.currentTimeMillis()
        if (now - lastFullTreeScanAtMs >= FULL_TREE_SCAN_THROTTLE_MS) {
            lastFullTreeScanAtMs = now
            val fullData = ScraperData()
            extractData(rootNode, fullData)
            mergeScraperData(scraperData, fullData)
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
            return emptyList()
        }
        val fare = scraperData.fare
        val distance = scraperData.distance

        maybeLogScrapedData(windowType, fare, distance)

        if (!filterEngine.shouldAccept(fare, distance)) return emptyList()
        val rankScore = filterEngine.rankScore(fare, distance)
        val actionSignature = buildActionSignature(windowType, targetNode, fare, distance)
        return listOf(RideCandidate(
            windowType = windowType,
            packageName = packageName,
            targetNode = targetNode,
            fare = fare,
            distance = distance,
            isSwipe = scraperData.isSwipe,
            rankScore = rankScore,
            actionSignature = actionSignature
        ))
    }

    private fun buildCandidateFromTarget(
        targetNode: AccessibilityNodeInfo,
        windowType: String,
        packageName: String,
        allowEventMetricFallback: Boolean
    ): RideCandidate? {
        val scraperData = ScraperData().apply {
            this.targetNode = targetNode
            this.isSwipe = isSwipeText(extractNodeText(targetNode))
        }
        extractMetricsNearTarget(targetNode, scraperData)

        if (allowEventMetricFallback && isEventDataFresh()) {
            if (scraperData.fare <= 0.0 && lastEventFare > 0.0) {
                scraperData.fare = lastEventFare
            }
            if (scraperData.distance <= 0.0 && lastEventDistance > 0.0) {
                scraperData.distance = lastEventDistance
            }
        }

        val fare = scraperData.fare
        val distance = scraperData.distance
        maybeLogScrapedData(windowType, fare, distance)
        if (!filterEngine.shouldAccept(fare, distance)) return null

        return RideCandidate(
            windowType = windowType,
            packageName = packageName,
            targetNode = targetNode,
            fare = fare,
            distance = distance,
            isSwipe = scraperData.isSwipe,
            rankScore = filterEngine.rankScore(fare, distance),
            actionSignature = buildActionSignature(windowType, targetNode, fare, distance)
        )
    }

    private fun buildCandidatesFromOfferContainers(
        rootNode: AccessibilityNodeInfo,
        windowType: String,
        packageName: String
    ): List<RideCandidate> {
        val rootBounds = Rect()
        rootNode.getBoundsInScreen(rootBounds)

        val candidates = mutableListOf<RideCandidate>()
        collectOfferContainerCandidates(
            node = rootNode,
            rootBounds = rootBounds,
            windowType = windowType,
            packageName = packageName,
            candidates = candidates
        )

        return candidates
            .distinctBy { it.actionSignature }
            .sortedByDescending { it.rankScore }
    }

    private fun buildCandidatesFromAcceptContainers(
        acceptNodes: List<AccessibilityNodeInfo>,
        windowType: String,
        packageName: String
    ): List<RideCandidate> {
        return acceptNodes
            .mapNotNull { targetNode ->
                findBoundedOfferContainerForTarget(targetNode)?.let { containerNode ->
                    buildCandidateFromOfferContainer(containerNode, windowType, packageName)
                }
            }
            .distinctBy { it.actionSignature }
            .sortedByDescending { it.rankScore }
    }

    private fun findBoundedOfferContainerForTarget(targetNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val targetBounds = Rect()
        targetNode.getBoundsInScreen(targetBounds)
        var ancestor: AccessibilityNodeInfo? = targetNode.parent
        var depth = 0
        var fallback: AccessibilityNodeInfo? = null

        while (ancestor != null && depth <= 6) {
            val ancestorBounds = Rect()
            ancestor.getBoundsInScreen(ancestorBounds)
            if (isLikelyOfferContainer(ancestorBounds, targetBounds)) {
                fallback = ancestor
                if (rootHasRideOfferSignals(ancestor)) {
                    return ancestor
                }
            }
            ancestor = ancestor.parent
            depth += 1
        }

        return fallback
    }

    private fun collectOfferContainerCandidates(
        node: AccessibilityNodeInfo,
        rootBounds: Rect,
        windowType: String,
        packageName: String,
        candidates: MutableList<RideCandidate>
    ): Boolean {
        var childProducedCandidate = false
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (collectOfferContainerCandidates(child, rootBounds, windowType, packageName, candidates)) {
                childProducedCandidate = true
            }
            if (candidates.size >= MAX_OFFER_CONTAINER_CANDIDATES) {
                return true
            }
        }

        if (childProducedCandidate || candidates.size >= MAX_OFFER_CONTAINER_CANDIDATES) {
            return childProducedCandidate
        }

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!isPotentialOfferContainer(bounds, rootBounds)) return false
        if (!rootHasRideOfferSignals(node)) return false

        val candidate = buildCandidateFromOfferContainer(node, windowType, packageName) ?: return false
        candidates.add(candidate)
        return true
    }

    private fun buildCandidateFromOfferContainer(
        containerNode: AccessibilityNodeInfo,
        windowType: String,
        packageName: String
    ): RideCandidate? {
        val scraperData = ScraperData()
        extractData(containerNode, scraperData)

        if (scraperData.targetNode == null && scraperData.fare > 0.0 && scraperData.fallbackTargetNode != null) {
            scraperData.targetNode = scraperData.fallbackTargetNode
            scraperData.isSwipe = true
        }

        val targetNode = scraperData.targetNode ?: return null
        val fare = scraperData.fare
        val distance = scraperData.distance
        if (!filterEngine.shouldAccept(fare, distance)) return null

        return RideCandidate(
            windowType = windowType,
            packageName = packageName,
            targetNode = targetNode,
            fare = fare,
            distance = distance,
            isSwipe = scraperData.isSwipe,
            rankScore = filterEngine.rankScore(fare, distance),
            actionSignature = buildActionSignature(windowType, targetNode, fare, distance)
        )
    }

    private class ScraperData {
        var targetNode: AccessibilityNodeInfo? = null
        var fare: Double = 0.0
        var distance: Double = 0.0
        var isSwipe: Boolean = false
        var fallbackTargetNode: AccessibilityNodeInfo? = null
        var fallbackTargetScore: Int = Int.MIN_VALUE
    }

    private data class RideCandidate(
        val windowType: String,
        val packageName: String,
        val targetNode: AccessibilityNodeInfo,
        val fare: Double,
        val distance: Double,
        val isSwipe: Boolean,
        val rankScore: Double,
        val actionSignature: String
    )

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
                val parsedDist = parseDistanceKm(text)
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
            if (shouldSwipeFallback(node, bounds)) {
                val dispatched = dispatchSwipeGesture(bounds, windowType, actionSignature)
                if (dispatched) {
                    Log.d(TAG, "[$windowType] Swipe fallback gesture dispatched")
                    return true
                }
            }

            val path = Path().apply {
                moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat())
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 1))
                .build()
            return dispatchGestureWithOverlayGuard(gesture, windowType, actionSignature)
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

        return dispatchSwipeGesture(bounds, windowType, actionSignature).also { dispatched ->
            if (dispatched) {
                Log.d(TAG, "[$windowType] Swipe gesture dispatched")
            }
        }
    }

    private fun dispatchSwipeGesture(
        bounds: Rect,
        windowType: String,
        actionSignature: String
    ): Boolean {
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

        return dispatchGestureWithOverlayGuard(gesture, windowType, actionSignature)
    }

    private fun shouldSwipeFallback(node: AccessibilityNodeInfo, bounds: Rect): Boolean {
        if (bounds.width() < 420 || bounds.height() !in 48..260) return false
        val text = extractNodeText(node).lowercase().trim()
        return text.isBlank() || isAcceptText(text) || isSwipeText(text)
    }

    private fun dispatchGestureWithOverlayGuard(
        gesture: GestureDescription,
        windowType: String,
        actionSignature: String
    ): Boolean {
        markActionPending(actionSignature)
        hideOverlayForGesture()
        val dispatched = dispatchGesture(gesture, gestureCallback(windowType, actionSignature), null)
        if (!dispatched) {
            clearPendingAction(actionSignature)
            restoreOverlayAfterGesture(delayMs = 0L)
        }
        return dispatched
    }

    private fun gestureCallback(windowType: String, actionSignature: String): GestureResultCallback {
        return object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "[$windowType] Gesture success")
                markActionExecuted(actionSignature)
                restoreOverlayAfterGesture()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.e(TAG, "[$windowType] Gesture failed")
                clearPendingAction(actionSignature)
                restoreOverlayAfterGesture()
            }
        }
    }

    private fun canAttemptAction(actionSignature: String): Boolean {
        val now = System.currentTimeMillis()
        val pendingSignature = pendingActionSignature
        if (pendingSignature != null) {
            if (now - pendingActionAtMs < PENDING_ACTION_TIMEOUT_MS) {
                return false
            }
            clearPendingAction(pendingSignature)
        }
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

    private fun markActionPending(actionSignature: String) {
        pendingActionSignature = actionSignature
        pendingActionAtMs = System.currentTimeMillis()
    }

    private fun clearPendingAction(actionSignature: String) {
        if (pendingActionSignature == actionSignature) {
            pendingActionSignature = null
            pendingActionAtMs = 0L
        }
    }

    private fun markActionExecuted(actionSignature: String) {
        lastActionAtMs = System.currentTimeMillis()
        lastActionSignature = actionSignature
        clearPendingAction(actionSignature)
    }

    private fun hideOverlayForGesture() {
        val view = overlayView ?: return
        overlayRestoreRunnable?.let { scanHandler.removeCallbacks(it) }
        overlayRestoreRunnable = null
        if (view.visibility != View.INVISIBLE) {
            view.visibility = View.INVISIBLE
        }
    }

    private fun restoreOverlayAfterGesture(delayMs: Long = OVERLAY_GESTURE_RESTORE_DELAY_MS) {
        val view = overlayView ?: return
        overlayRestoreRunnable?.let { scanHandler.removeCallbacks(it) }
        val restore = Runnable {
            view.visibility = View.VISIBLE
            overlayRestoreRunnable = null
        }
        overlayRestoreRunnable = restore
        scanHandler.postDelayed(restore, delayMs)
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

        event.text.forEach { value ->
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

    private fun findAcceptNodesByText(rootNode: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        val seenBounds = mutableSetOf<String>()
        for (keyword in ACCEPT_TEXT_PROBE_KEYWORDS) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)
            for (node in nodes) {
                if (!isAcceptText(extractNodeText(node))) continue
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val key = "${bounds.left}:${bounds.top}:${bounds.right}:${bounds.bottom}:${extractNodeText(node)}"
                if (seenBounds.add(key)) {
                    candidates.add(node)
                }
            }
        }
        return candidates
    }

    private fun extractMetricsNearTarget(targetNode: AccessibilityNodeInfo, data: ScraperData) {
        val textCandidates = linkedSetOf<String>()

        val targetBounds = Rect()
        targetNode.getBoundsInScreen(targetBounds)
        var ancestor: AccessibilityNodeInfo? = targetNode
        var ancestorDepth = 0
        while (ancestor != null && ancestorDepth <= 6) {
            val ancestorBounds = Rect()
            ancestor.getBoundsInScreen(ancestorBounds)
            val maxDepth = when {
                ancestorDepth <= 2 -> 3
                isLikelyOfferContainer(ancestorBounds, targetBounds) -> 4
                else -> 1
            }
            collectTextCandidates(ancestor, depth = 0, maxDepth = maxDepth, out = textCandidates)
            ancestor = ancestor.parent
            ancestorDepth += 1
        }

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

    private fun isLikelyOfferContainer(bounds: Rect, targetBounds: Rect): Boolean {
        if (bounds.width() <= 0 || bounds.height() <= 0) return false
        if (!Rect.intersects(bounds, targetBounds)) return false

        val displayHeight = resources.displayMetrics.heightPixels.takeIf { it > 0 } ?: return true
        val displayWidth = resources.displayMetrics.widthPixels.takeIf { it > 0 } ?: return true
        val coversMostScreen = bounds.width() >= displayWidth * 0.95f && bounds.height() >= displayHeight * 0.85f
        if (coversMostScreen) return false

        val isBottomSheetLike = bounds.width() >= displayWidth * 0.75f &&
            bounds.top >= displayHeight * 0.25f
        val isCardLike = bounds.height() <= displayHeight * 0.70f &&
            bounds.width() >= min(260, displayWidth / 2)
        return isBottomSheetLike || isCardLike
    }

    private fun isPotentialOfferContainer(bounds: Rect, rootBounds: Rect): Boolean {
        if (bounds.width() <= 0 || bounds.height() <= 0) return false
        if (!Rect.intersects(bounds, rootBounds)) return false

        val displayHeight = resources.displayMetrics.heightPixels.takeIf { it > 0 } ?: rootBounds.height()
        val displayWidth = resources.displayMetrics.widthPixels.takeIf { it > 0 } ?: rootBounds.width()
        val coversMostScreen = bounds.width() >= displayWidth * 0.95f && bounds.height() >= displayHeight * 0.85f
        if (coversMostScreen) return false
        if (bounds.width() < min(240, max(120, displayWidth / 3))) return false
        if (bounds.height() < 72 || bounds.height() > displayHeight * 0.78f) return false

        return bounds.width() >= displayWidth * 0.55f || bounds.height() >= 120
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
        return filterEngine.parseDistanceKm(text)
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
        val matched = RideWindowPolicy.isRideApp(packageName)
        if (matched && lastLoggedRidePackage != packageName) {
            lastLoggedRidePackage = packageName
            Log.d(TAG, "Ride app detected: $packageName")
        }
        return matched
    }

    private fun isSystemOverlayHostPackage(packageName: String): Boolean {
        return RideWindowPolicy.isSystemOverlayHostPackage(packageName)
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

    private data class RideOfferSignals(
        var hasAcceptAction: Boolean = false,
        var hasCurrency: Boolean = false,
        var hasDistance: Boolean = false
    ) {
        val isLikelyRideOffer: Boolean
            get() = RideOfferSignalPolicy.isLikelyRideOffer(
                hasAcceptAction = hasAcceptAction,
                hasCurrency = hasCurrency,
                hasDistance = hasDistance
            )
    }

    private fun eventContainsRideOfferSignals(event: AccessibilityEvent): Boolean {
        val signals = RideOfferSignals()
        event.text.forEach { value ->
            updateRideOfferSignals(value?.toString().orEmpty(), signals)
        }
        updateRideOfferSignals(event.contentDescription?.toString().orEmpty(), signals)
        event.source?.let { collectRideOfferSignals(it, signals) }
        return signals.isLikelyRideOffer
    }

    private fun rootHasRideOfferSignals(rootNode: AccessibilityNodeInfo): Boolean {
        val signals = RideOfferSignals()
        collectRideOfferSignals(rootNode, signals)
        return signals.isLikelyRideOffer
    }

    private fun collectRideOfferSignals(node: AccessibilityNodeInfo, signals: RideOfferSignals) {
        updateRideOfferSignals(extractNodeText(node), signals)
        if (signals.isLikelyRideOffer && signals.hasDistance) return

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectRideOfferSignals(child, signals)
            if (signals.isLikelyRideOffer && signals.hasDistance) return
        }
    }

    private fun updateRideOfferSignals(text: String, signals: RideOfferSignals) {
        if (text.isBlank()) return
        if (isAcceptText(text)) signals.hasAcceptAction = true
        if (containsCurrency(text)) signals.hasCurrency = true
        if (containsDistance(text)) signals.hasDistance = true
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
        scanHandler.removeCallbacks(periodicScanRunnable)
        overlayRestoreRunnable?.let { scanHandler.removeCallbacks(it) }
        overlayRestoreRunnable = null
        overlayView?.let {
            runCatching { windowManager.removeView(it) }
            overlayAttached = false
        }
        overlayView = null
        overlayLayoutParams = null
    }

    override fun onInterrupt() {}
}
