package me.kavishdevar.librepods

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo

/** Platform accessibility checks against mock callbacks. No testing dependency or device commands. */
class NativeUiInstrumentation : Instrumentation() {
    private val checks = mutableListOf<String>()
    private var preview: Activity? = null
    private var checkApple = false
    private var toolbarOnly = false
    private var detentOnly = false
    private var batteryOnly = false
    private var batteryLayoutOnly = false
    private var auditOnly = false
    private var navigationOnly = false

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        checkApple = arguments?.getString("checkApple") == "true"
        toolbarOnly = arguments?.getString("toolbarOnly") == "true"
        detentOnly = arguments?.getString("detentOnly") == "true"
        batteryOnly = arguments?.getString("batteryOnly") == "true"
        batteryLayoutOnly = arguments?.getString("batteryLayoutOnly") == "true"
        auditOnly = arguments?.getString("auditOnly") == "true"
        navigationOnly = arguments?.getString("navigationOnly") == "true"
        start()
    }

    override fun onStart() {
        val results = Bundle()
        try {
            if (navigationOnly) {
                checkSettingsNavigation()
                results.putString("stream", "\n${checks.joinToString("\n")}\n${checks.size} settings navigation checks passed\n")
                finish(Activity.RESULT_OK, results)
                return
            }
            if (auditOnly) {
                checkUiAudit()
                checkAppleModalAndInput()
                results.putString("stream", "\n${checks.joinToString("\n")}\n${checks.size} UI repair checks passed\n")
                finish(Activity.RESULT_OK, results)
                return
            }
            if (batteryLayoutOnly) {
                checkBatteryImageLayout()
                results.putString("stream", "\n${checks.joinToString("\n")}\n${checks.size} battery layout checks passed\n")
                finish(Activity.RESULT_OK, results)
                return
            }
            if (batteryOnly) {
                checkBattery()
                results.putString("stream", "\n${checks.joinToString("\n")}\n${checks.size} battery checks passed\n")
                finish(Activity.RESULT_OK, results)
                return
            }
            if (detentOnly) {
                checkDetent()
                results.putString("stream", "\n${checks.joinToString("\n")}\n${checks.size} detent checks passed\n")
                finish(Activity.RESULT_OK, results)
                return
            }
            if (toolbarOnly) {
                checkToolbar()
                results.putString("stream", "\n${checks.joinToString("\n")}\n${checks.size} toolbar checks passed\n")
                finish(Activity.RESULT_OK, results)
                return
            }
            open("panel")
            val zeroBattery = preview!!.getString(R.string.popup_battery_charging, preview!!.getString(R.string.left), 0)
            verify("Zero battery and charging state remain accessible", text(root()).contains(zeroBattery))
            val slider = find(root()) { it.rangeInfo != null } ?: error("Volume has no native range")
            verify("Volume range is 0..1", slider.rangeInfo.min == 0f && slider.rangeInfo.max == 1f)
            verify("Volume has an accessible name", text(slider).contains(preview!!.getString(R.string.media_volume)))
            val progress = Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 0.2f) }
            verify("SetProgress action is supported", slider.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, progress))
            await { find(root()) { it.rangeInfo != null }?.rangeInfo?.current == 0.2f }
            verify("Accessible progress reaches 20 percent", find(root()) { it.rangeInfo != null }!!.rangeInfo.current == 0.2f)
            val modeLabel = preview!!.getString(R.string.noise_cancellation)
            val choice = target(modeLabel)
            verify("Mode has a checkable native state", choice.isCheckable)
            verify("Mode accessibility click succeeds", choice.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await { target(modeLabel).isChecked }
            verify("Mode selected state changes", target(modeLabel).isChecked)
            val conversationLabel = preview!!.getString(R.string.conversational_awareness)
            val conversation = target(conversationLabel)
            verify("Conversation starts checked", conversation.isChecked)
            conversation.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            await { !target(conversationLabel).isChecked }
            verify("Conversation native toggle changes", !target(conversationLabel).isChecked)

            open("controls")
            var seslSeekBar = false
            runOnMainSync {
                fun visit(view: android.view.View) {
                    if (view is androidx.appcompat.widget.SeslSeekBar) seslSeekBar = true
                    if (view is android.view.ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
                }
                visit(preview!!.window.decorView)
            }
            verify("SESL9 SeekBar is instantiated", seslSeekBar)
            target("알림").performAction(AccessibilityNodeInfo.ACTION_CLICK)
            await { target("알림").isChecked }
            verify("Switch row changes once", text(root()).contains("샘플 변경: 1"))
            val disabled = target("비활성 설정")
            verify("Disabled switch reports disabled", !disabled.isEnabled)
            disabled.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            SystemClock.sleep(200)
            verify("Disabled switch does not invoke callback", text(root()).contains("샘플 변경: 1"))
            target("선택 항목").performAction(AccessibilityNodeInfo.ACTION_CLICK)
            await { target("선택 항목").isChecked }
            verify("Radio row exposes selected state", target("선택 항목").isChecked)
            target("체크 항목").performAction(AccessibilityNodeInfo.ACTION_CLICK)
            await { !target("체크 항목").isChecked }
            verify("Checkbox row exposes checked state", !target("체크 항목").isChecked)
            verify("Input has a native edit field", find(root()) { it.isEditable } != null)
            val nativeSlider = find(root()) { it.rangeInfo != null && text(it).contains("샘플 조절") } ?: error("No framework setting SeekBar")
            verify("SESL SeekBar exposes native accessibility class", nativeSlider.className == "android.widget.SeekBar")
            val settingProgress = Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 40f) }
            verify("Framework SeekBar supports SetProgress", nativeSlider.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, settingProgress))
            await { find(root()) { it.rangeInfo != null && text(it).contains("샘플 조절") }?.rangeInfo?.current == 40f }
            verify("Framework SeekBar updates to 40", find(root()) { it.rangeInfo != null && text(it).contains("샘플 조절") }!!.rangeInfo.current == 40f)

            // Apple was restored independently; its separate audit can be explicitly requested.
            if (checkApple) {
            open("controls", "apple")
            target("알림").performAction(AccessibilityNodeInfo.ACTION_CLICK)
            await { target("알림").isChecked }
            verify("Apple switch changes once", text(root()).contains("샘플 변경: 1"))
            val appleDisabled = target("비활성 설정")
            verify("Apple disabled row reports disabled", !appleDisabled.isEnabled)
            appleDisabled.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            SystemClock.sleep(200)
            verify("Apple disabled row does not change state", text(root()).contains("샘플 변경: 1"))
            target("선택 항목").performAction(AccessibilityNodeInfo.ACTION_CLICK)
            await { target("선택 항목").isChecked }
            verify("Apple selected row exposes checked state", target("선택 항목").isChecked)
            target("체크 항목").performAction(AccessibilityNodeInfo.ACTION_CLICK)
            await { !target("체크 항목").isChecked }
            verify("Apple checkbox exposes checked state", !target("체크 항목").isChecked)
            verify("Apple input is editable and named", find(root()) { it.isEditable && text(it).contains("기기 이름") } != null)
            verify("Material is absent from visible UI", !text(root()).contains("Material"))
            }

            open("fractional")
            val fractional = find(root()) { it.rangeInfo != null } ?: error("No fractional SeekBar")
            verify("Fractional value starts at 25 percent", fractional.rangeInfo.current == 25f)
            val fractionalProgress = Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 75f) }
            verify("Fractional SeekBar supports intermediate progress", fractional.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, fractionalProgress))
            await { text(root()).contains("소수 값: 0.75") }
            verify("Fractional change maps to 0.75 once", text(root()).contains("샘플 변경: 1"))

            open("widgets")
            val nativeClasses = mutableSetOf<String>()
            var nativeSwitchHaptic = true
            runOnMainSync {
                fun visit(view: android.view.View) {
                    nativeClasses.add(view.javaClass.name)
                    if (view is androidx.appcompat.widget.SwitchCompat) nativeSwitchHaptic = view.isHapticFeedbackEnabled
                    if (view is android.view.ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
                }
                visit(preview!!.window.decorView)
            }
            verify("SESL SwitchCompat is instantiated", nativeClasses.contains("androidx.appcompat.widget.SwitchCompat"))
            verify("SESL automatic switch haptics are disabled", !nativeSwitchHaptic)
            verify("SESL radio and checkbox are instantiated", nativeClasses.contains("androidx.appcompat.widget.AppCompatRadioButton") && nativeClasses.contains("androidx.appcompat.widget.AppCompatCheckBox"))
            verify("SESL AppCompatButton is instantiated", nativeClasses.contains("androidx.appcompat.widget.AppCompatButton"))
            val button = find(root()) { it.isClickable && text(it).contains("샘플 버튼") && !text(it).contains("샘플 변경") } ?: error("No sample button")
            button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            await { text(root()).contains("샘플 변경: 1") }
            verify("SESL button invokes one callback", text(root()).contains("샘플 변경: 1"))
            val disabledButton = find(root()) { text(it).contains("비활성 버튼") && !text(it).contains("샘플 변경") } ?: error("No disabled button")
            verify("Disabled SESL button reports disabled", !disabledButton.isEnabled)
            disabledButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            SystemClock.sleep(100)
            verify("Disabled SESL button invokes no callback", text(root()).contains("샘플 변경: 1"))
            val syncButton = find(root()) { it.isClickable && text(it).contains("상태 동기화") && !text(it).contains("샘플 변경") } ?: error("No sync button")
            verify("Programmatic state button supports accessibility click", syncButton.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await { !target("스위치 샘플").isChecked }
            verify("Programmatic SESL switch state is applied", !target("스위치 샘플").isChecked)
            verify("Programmatic SESL switch update invokes no user callback", text(root()).contains("샘플 변경: 1"))
            val editor = find(root()) { it.isEditable } ?: error("No native SESL editor")
            val input = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Native QA") }
            verify("SESL input supports SetText", editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, input))
            await { text(root()).contains("입력 값: Native QA") }
            verify("SESL input updates the shared text state", text(root()).contains("입력 값: Native QA"))

            open("dialog")
            verify("SESL confirmation is a separate modal window", text(root()).contains("저장") && !text(root()).contains("샘플 변경:"))
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            await { text(root()).contains("샘플 변경: 0") }
            verify("SESL modal dismisses on Back without confirming", !text(root()).contains("저장"))

            open("sheet")
            verify("SESL contact sheet owns a separate modal window", !text(root()).contains("샘플 변경:"))
            val sendLabel = preview!!.getString(R.string.email)
            val send = find(root()) { it.isClickable && text(it).contains(sendLabel) } ?: error("No sheet send button")
            verify("Empty contact sheet cannot send", !send.isEnabled)
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            await { text(root()).contains("샘플 변경: 0") }
            verify("SESL sheet dismisses on Back without sending", text(root()).contains("샘플 변경: 0"))
            open("sheet")
            for (label in listOf(R.string.subject, R.string.describe_your_issue)) {
                val name = preview!!.getString(label)
                val field = find(root()) { it.isEditable && text(it).contains(name) } ?: error("No sheet field: $name")
                val value = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Native QA") }
                verify("Sheet field $name supports native input", field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, value))
            }
            await { find(root()) { it.isClickable && text(it).contains(sendLabel) }?.isEnabled == true }
            find(root()) { it.isClickable && text(it).contains(sendLabel) }!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            await { text(root()).contains("샘플 변경: 1") }
            verify("Filled SESL sheet invokes one sample send callback", text(root()).contains("샘플 변경: 1"))
            results.putString("stream", "\n${checks.joinToString("\n")}\n${checks.size} native UI checks passed\n")
            finish(Activity.RESULT_OK, results)
        } catch (error: Throwable) {
            val screen = uiAutomation.rootInActiveWindow
            checks += "Diagnostic: scenario=${preview?.intent?.getStringExtra("scenario")}, locale=${preview?.resources?.configuration?.locales}, package=${screen?.packageName}"
            if (screen?.packageName == targetContext.packageName) checks += "Fixture: ${text(screen)}"
            results.putString("stream", "\n${checks.joinToString("\n")}\nFAILED: ${error.stackTraceToString()}\n")
            finish(Activity.RESULT_CANCELED, results)
        } finally {
            runOnMainSync { preview?.finish() }
        }
    }

    private fun open(scenario: String, design: String = "oneui", dark: Boolean = false, fontScale: Float = 1f) {
        runOnMainSync { preview?.finish() }
        preview = startActivitySync(Intent(targetContext, NativeUiPreviewActivity::class.java)
            .putExtra("scenario", scenario).putExtra("design", design).putExtra("dark", dark)
            .putExtra("font_scale", fontScale).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        val expected = when (scenario) {
            "dialog" -> "저장"
            "sheet" -> preview!!.getString(R.string.describe_your_issue)
            "panel" -> preview!!.getString(R.string.popup_battery_charging, preview!!.getString(R.string.left), 0)
            "widgets" -> "상태 동기화"
            "fractional" -> "소수 값: 0.25"
            "toolbar" -> "스크롤 항목 1"
            "detent" -> "기본값 스냅"
            "battery" -> "배터리 샘플: 0"
            "images" -> "이미지 배치: 0"
            "audit" -> "UI 재점검 샘플"
            "navigation" -> preview!!.getString(R.string.companion_registration)
            "companion" -> "관리 샘플:"
            // Apple rows are taller; the slider can legitimately start below the viewport.
            else -> "알림"
        }
        await {
            val screen = text(root())
            screen.contains(expected) && (scenario in listOf("dialog", "sheet", "battery", "images", "audit", "navigation", "companion") || screen.contains("샘플 변경: 0"))
        }
    }

    private fun checkSettingsNavigation() {
        fun leaf(label: String) = find(root()) { it.text?.toString() == label } ?: error("No text: $label")
        fun bounds(node: AccessibilityNodeInfo) = android.graphics.Rect().apply { node.getBoundsInScreen(this) }
        fun click(label: String) {
            val node = find(root()) { it.isClickable && it.isEnabled && text(it).contains(label) }
                ?: error("No row: $label")
            check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        }
        for (design in listOf("apple", "oneui")) {
            open("navigation", design, dark = design == "apple")
            val language = preview!!.getString(R.string.language)
            val feature = preview!!.getString(R.string.feature_status_title)
            val companion = preview!!.getString(R.string.companion_registration)
            val description = preview!!.getString(R.string.companion_registration_active)
            val prefix = "$design: "
            verify(prefix + "general entries keep their order", bounds(leaf(language)).bottom < bounds(leaf(feature)).top &&
                bounds(leaf(feature)).bottom < bounds(leaf(companion)).top)
            verify(prefix + "companion summary sits below its title", bounds(leaf(companion)).bottom <= bounds(leaf(description)).top)
            click(companion)
            await { text(root()).contains("관리 호출: 1") }
            verify(prefix + "management callback runs once", text(root()).contains("관리 호출: 1"))
            for (summary in listOf(R.string.companion_detection_failed, R.string.companion_registration_description,
                R.string.companion_registration_active)) {
                click("등록 상태 변경")
                await { text(root()).contains(preview!!.getString(summary)) }
                verify(prefix + "registration summary $summary remains visible", text(root()).contains(preview!!.getString(summary)))
            }
            repeat(3) {
                click(feature)
                await { text(root()).contains(preview!!.getString(R.string.diagnostics_title)) }
                SystemClock.sleep(450)
                verify(prefix + "destination excludes previous settings", !text(root()).contains(companion))
                val diagnosticBounds = bounds(leaf(preview!!.getString(R.string.diagnostics_title)))
                // The feature section repeats the page title further down; identify the toolbar by position.
                val toolbar = find(root()) { it.text?.toString() == feature && bounds(it).top < diagnosticBounds.top }
                verify(prefix + "diagnostic title follows the toolbar", toolbar != null && bounds(toolbar).bottom < diagnosticBounds.top)
                sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                await { text(root()).contains(companion) }
                SystemClock.sleep(450)
                verify(prefix + "back returns to the settings group", text(root()).contains(description) && text(root()).contains("관리 호출: 1"))
            }
            open("companion", design, dark = design == "apple")
            val remove = preview!!.getString(R.string.companion_remove_registration)
            click(remove)
            await { text(root()).contains(preview!!.getString(R.string.companion_remove_confirm)) }
            verify(prefix + "removal waits for confirmation", !text(root()).contains("해제 1"))
            click(preview!!.getString(android.R.string.cancel))
            await { text(root()).contains("해제 0") }
            verify(prefix + "cancel preserves registration", text(root()).contains("해제 0"))
            click(remove)
            await { text(root()).contains(preview!!.getString(R.string.companion_remove_confirm)) }
            click(remove)
            await { text(root()).contains("해제 1") }
            verify(prefix + "confirmed removal callback runs once", text(root()).contains("해제 1"))
            click(preview!!.getString(R.string.companion_enable_detection))
            await { text(root()).contains("감지 1") }
            verify(prefix + "enable callback runs once", text(root()).contains("감지 1"))
        }
    }

    private fun checkAppleModalAndInput() {
        fun action(label: String) = find(root()) { it.isClickable && it.isEnabled && text(it).contains(label) }
            ?: error("No alert action: $label")
        open("dialog", "apple")
        verify("Apple alert isolates modal accessibility", !text(root()).contains("샘플 변경:"))
        sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        await { text(root()).contains("샘플 변경: 0") }
        verify("Apple alert Back dismisses without confirming", !text(root()).contains("저장"))
        open("dialog", "apple")
        verify("Apple cancel supports accessibility click", action("취소").performAction(AccessibilityNodeInfo.ACTION_CLICK))
        await { text(root()).contains("샘플 변경: 0") }
        verify("Apple cancel invokes no confirm callback", !text(root()).contains("저장"))
        open("dialog", "apple")
        verify("Apple confirm supports accessibility click", action("저장").performAction(AccessibilityNodeInfo.ACTION_CLICK))
        await { text(root()).contains("샘플 변경: 1") }
        verify("Apple confirm invokes one callback", !text(root()).contains("저장"))
        open("sheet", "apple")
        val subject = preview!!.getString(R.string.subject)
        val field = find(root()) { it.isEditable && text(it).contains(subject) } ?: error("No Apple subject field")
        verify("Apple input exposes its label", text(field).contains(subject))
        verify("Apple empty input shows a visible hint", find(root()) { it.text?.toString() == subject } != null)
        verify("Apple input supports SetText", field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Native QA") }))
        await { text(root()).contains("Native QA") }
        val clearLabel = preview!!.getString(R.string.clear)
        val clear = find(root()) { it.isClickable && !it.isEditable && text(it).contains(clearLabel) }
            ?: error("No named Apple clear control")
        verify("Apple clear control has a localized name", clear.isEnabled)
        verify("Apple clear action succeeds", clear.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        await { !text(root()).contains("Native QA") }
        verify("Apple clear removes the text", text(root()).contains(subject))
        sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        await { text(root()).contains("샘플 변경: 0") }
        verify("Apple sheet Back dismisses without sending", !text(root()).contains(subject))
    }

    private fun checkUiAudit() {
        for (design in listOf("oneui", "apple")) {
            open("audit", design)
            fun slider(label: String) = find(root()) { it.rangeInfo != null && text(it).contains(label) }
                ?: error("No $label slider")
            fun progress(value: Float) = Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, value) }
            fun button(label: String) = find(root()) { it.isClickable && text(it) == label }
                ?: error("No $label button")
            fun tap(node: AccessibilityNodeInfo) {
                val rect = android.graphics.Rect().apply { node.getBoundsInScreen(this) }
                val now = SystemClock.uptimeMillis()
                for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                    val event = android.view.MotionEvent.obtain(now, SystemClock.uptimeMillis(), action,
                        rect.exactCenterX(), rect.exactCenterY(), 0)
                    event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                    verify("$design: touch event $action is delivered", uiAutomation.injectInputEvent(event, true))
                    event.recycle()
                    SystemClock.sleep(80)
                }
            }
            val active = slider("접근성 조절")
            verify("$design: slider exposes its range", active.rangeInfo.min == 0f && active.rangeInfo.max == 100f && active.rangeInfo.current == 25f)
            verify("$design: accessible change succeeds", active.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, progress(30f)))
            await { text(root()).contains("조절값: 30.0") }
            verify("$design: change fires once", text(root()).contains("슬라이더 변경: 1"))
            verify("$design: detent accessibility change succeeds", slider("접근성 조절").performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, progress(45f)))
            await { text(root()).contains("조절값: 43.0") }
            verify("$design: detent ticks once", text(root()).contains("진동: 1"))
            verify("$design: disabled slider reports disabled", !slider("비활성 조절").isEnabled)
            slider("비활성 조절").performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, progress(80f))
            SystemClock.sleep(150)
            verify("$design: disabled slider does not change", text(root()).contains("슬라이더 변경: 2"))
            val disabled = find(root()) { text(it) == "비활성 버튼" } ?: error("No disabled button")
            verify("$design: disabled button reports disabled", !disabled.isEnabled)
            disabled.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            SystemClock.sleep(150)
            verify("$design: disabled button invokes no callback", text(root()).contains("버튼 변경: 0"))
            tap(button("유리 버튼"))
            await { text(root()).contains("버튼 변경: 1") }
            verify("$design: interactive button invokes once", text(root()).contains("버튼 변경: 1"))
            tap(button("일반 누름"))
            await { text(root()).contains("버튼 변경: 2") }
            verify("$design: plain press invokes once", text(root()).contains("버튼 변경: 2"))
            verify("$design: button accessibility click succeeds", button("유리 버튼").performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await { text(root()).contains("버튼 변경: 3") }
            verify("$design: accessibility click invokes once", text(root()).contains("버튼 변경: 3"))
            val ticksBeforeRestore = Regex("진동: (\\d+)").find(text(root()))!!.groupValues[1]
            button("상태 복원").performAction(AccessibilityNodeInfo.ACTION_CLICK)
            await { text(root()).contains("조절값: 25.0") }
            val ticksAfterRestore = Regex("진동: (\\d+)").find(text(root()))!!.groupValues[1].toInt()
            // Clicking the restore button is a user action; restoring the slider adds no extra tick.
            val expected = ticksBeforeRestore.toInt() + if (design == "apple") 1 else 0
            verify("$design: programmatic restore adds no slider tick", ticksAfterRestore == expected)
        }
    }

    private fun checkBatteryImageLayout() {
        for (design in listOf("oneui", "apple")) {
            open("images", design)
            val buds = preview!!.getString(R.string.buds)
            val left = preview!!.getString(R.string.left) + " " + buds
            val right = preview!!.getString(R.string.right) + " " + buds
            val case = preview!!.getString(R.string.case_alt)
            fun image(description: String) = find(root()) { it.contentDescription?.toString() == description }
            fun bounds(node: AccessibilityNodeInfo) = android.graphics.Rect().apply { node.getBoundsInScreen(this) }
            fun align(description: String, percent: String) {
                val photo = bounds(image(description) ?: error("No $description image"))
                val label = find(root()) { it.text?.toString()?.endsWith(percent) == true } ?: error("No $percent reading")
                val tolerance = preview!!.resources.displayMetrics.density * 2
                verify("$design: $description aligns with $percent", kotlin.math.abs(photo.centerX() - bounds(label).centerX()) <= tolerance)
            }
            fun next(sample: Int) {
                val button = find(root()) { it.isClickable && text(it) == "이미지 배치 변경" } ?: error("No image fixture button")
                button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                await { text(root()).contains("이미지 배치: $sample") }
            }
            verify("$design: merged reading shows a paired image", image(buds) != null && image(left) == null && image(right) == null)
            val mergedPhoto = bounds(image(buds)!!)
            val mergedCenter = mergedPhoto.centerX()
            val casePhoto = bounds(image(case)!!)
            val groupWidth = casePhoto.centerX() - mergedCenter
            verify("$design: paired product is enlarged within its group", mergedPhoto.width() > groupWidth * 0.5f)
            verify("$design: paired image preserves its aspect ratio", mergedPhoto.width().toFloat() / mergedPhoto.height() in 1.3f..1.45f)
            verify("$design: case product fills its group", casePhoto.width() > groupWidth * 0.8f)
            next(1)
            verify("$design: split readings show separate images", image(left) != null && image(right) != null && image(buds) == null)
            align(left, "85%")
            align(right, "40%")
            align(case, "60%")
            val l = bounds(image(left)!!)
            val r = bounds(image(right)!!)
            val c = bounds(image(case)!!)
            verify("$design: split buds straddle the merged center", l.centerX() < mergedCenter && mergedCenter < r.centerX())
            verify("$design: photos share a vertical center", kotlin.math.abs(l.centerY() - r.centerY()) <= 2 && kotlin.math.abs(r.centerY() - c.centerY()) <= 2)
            verify("$design: separated product images do not overlap", l.right < r.left && r.right < c.left)
            next(2)
            verify("$design: missing right reading has no right image", image(left) != null && image(right) == null)
            align(left, "85%")
            verify("$design: lone left bud stays at group center", kotlin.math.abs(bounds(image(left)!!).centerX() - mergedCenter) <= 2)
            next(3)
            verify("$design: missing left reading has no left image", image(right) != null && image(left) == null)
            align(right, "40%")
            verify("$design: lone right bud stays at group center", kotlin.math.abs(bounds(image(right)!!).centerX() - mergedCenter) <= 2)
            next(4)
            verify("$design: different charging states keep images separated", image(left) != null && image(right) != null && image(buds) == null)
        }
    }

    private fun checkBattery() {
        open("battery")
        val unavailable = preview!!.getString(R.string.battery_unavailable)
        fun zeroCount() = Regex("(?<![0-9])0%").findAll(text(root())).count()
        verify("Absent readings show unavailable", text(root()).contains(unavailable))
        verify("Absent readings never show zero percent", zeroCount() == 0)
        fun next(sample: Int) {
            val button = find(root()) { it.isClickable && text(it) == "배터리 샘플 변경" } ?: error("No battery fixture button")
            button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            await { text(root()).contains("배터리 샘플: $sample") }
        }
        next(1)
        verify("Disconnected readings show unavailable", text(root()).contains(unavailable) && zeroCount() == 0)
        next(2)
        verify("Reported empty buds and case keep real zero percent", zeroCount() == 2)
        verify("Other bud keeps its reported level", text(root()).contains("80%"))
        next(3)
        verify("Invalid readings cannot override a valid bud", text(root()).contains("42%") &&
            !text(root()).contains("-1%") && !text(root()).contains("255%") && zeroCount() == 0)
    }

    private fun checkDetent() {
        open("detent")
        fun bar() = find(root()) { it.rangeInfo != null } ?: error("No detent slider")
        fun setProgress(progress: Float) {
            verify("Slider accepts progress $progress", bar().performAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
                Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, progress) }))
        }
        setProgress(46f) // raw value 44.5; 43% is native progress 44 in the 10..85 range.
        await { text(root()).contains("기준값: 43.0 / 진동: 1") }
        verify("Reference snaps exactly and ticks once", bar().rangeInfo.current == 44f)
        setProgress(47f) // raw 45.25 remains inside the wider release band.
        await { bar().rangeInfo.current == 44f }
        verify("Holding near default neither moves thumb nor repeats callback", text(root()).contains("샘플 변경: 1") && text(root()).contains("진동: 1"))
        setProgress(51f) // raw 48.25 releases the thumb.
        await { bar().rangeInfo.current == 51f }
        verify("Continued input leaves the reference", text(root()).contains("진동: 1"))
        setProgress(46f)
        await { text(root()).contains("기준값: 43.0 / 진동: 2") }
        verify("Returning to default ticks once again", bar().rangeInfo.current == 44f)
        setProgress(60f)
        await { bar().rangeInfo.current == 60f }
        val restore = find(root()) { it.isClickable && text(it) == "기본값 복원" } ?: error("No restore button")
        restore.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        await { bar().rangeInfo.current == 44f }
        verify("Programmatic default restore emits no haptic", text(root()).contains("기준값: 43.0 / 진동: 2"))
    }

    private fun checkToolbar() {
        open("toolbar")
        fun surface(): android.view.View {
            var result: android.view.View? = null
            runOnMainSync { result = preview!!.window.decorView.findViewWithTag("oneui-toolbar-surface") }
            return result ?: error("No native toolbar surface")
        }
        fun alpha(): Float {
            var value = -1f
            val view = surface()
            runOnMainSync { value = view.alpha }
            return value
        }
        fun back(): AccessibilityNodeInfo = find(root()) {
            it.isClickable && text(it) == preview!!.getString(me.kavishdevar.librepods.R.string.back)
        } ?: error("No back button")
        fun backBounds() = android.graphics.Rect().apply { back().getBoundsInScreen(this) }
        fun scroll(action: Int) {
            val page = find(root()) { it.isScrollable } ?: error("No scrollable page")
            verify("Page accepts scroll action $action", page.performAction(action))
        }
        await { alpha() == 0f }
        val bounds = backBounds()
        val elevation = surface().elevation
        verify("Back has a 48dp touch target", bounds.width() == (48 * preview!!.resources.displayMetrics.density).toInt())
        verify("Toolbar surface is hidden at top", alpha() == 0f)
        scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        await { alpha() == 1f }
        verify("Scrolled page reveals native surface", alpha() == 1f)
        verify("Back stays in place after scroll", backBounds() == bounds)
        verify("Shadow elevation stays fixed after scroll", surface().elevation == elevation)
        scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        await { alpha() == 1f }
        scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        verify("Partial return keeps the floating surface", alpha() == 1f)

        val monitor = addMonitor(NativeUiPreviewActivity::class.java.name, null, false)
        runOnMainSync { preview!!.recreate() }
        preview = monitor.waitForActivityWithTimeout(5_000) ?: error("Fixture did not recreate")
        removeMonitor(monitor)
        await { alpha() == 1f }
        verify("Restored scrolled page keeps its surface", alpha() == 1f)
        verify("Recreation preserves back bounds", backBounds() == bounds)
        scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        await { alpha() == 0f }
        verify("Returning to top removes the surface", alpha() == 0f)
        verify("Back stays in place at top", backBounds() == bounds)
        back().performAction(AccessibilityNodeInfo.ACTION_CLICK)
        await { text(root()).contains("샘플 변경: 1") }
        verify("Back invokes its callback exactly once", text(root()).contains("샘플 변경: 1"))
    }

    private fun root(): AccessibilityNodeInfo {
        // Interop recomposition can leave UiAutomation's cached Compose parent state stale.
        if (android.os.Build.VERSION.SDK_INT >= 34) uiAutomation.clearCache()
        return (uiAutomation.rootInActiveWindow ?: error("No active UI")).apply { refresh() }
    }

    private fun target(label: String): AccessibilityNodeInfo =
        find(root()) { it.isCheckable && text(it).contains(label) } ?: error("No accessible control: $label")

    private fun text(node: AccessibilityNodeInfo): String = buildString {
        append(node.text ?: "")
        append(node.contentDescription ?: "")
        for (index in 0 until node.childCount) node.getChild(index)?.let { append(text(it)) }
    }

    private fun find(node: AccessibilityNodeInfo, matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (matches(node)) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            find(child, matches)?.let { return it }
        }
        return null
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        error("Timed out waiting for UI state")
    }

    private fun verify(name: String, passed: Boolean) {
        check(passed) { name }
        checks += "PASS: $name"
    }
}
