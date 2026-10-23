package com.zhaozhao.lingqian

import android.os.Bundle
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONObject
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

private val Lacquer = Color(0xFF741F1B)
private val DeepRed = Color(0xFF3D1213)
private val Gold = Color(0xFFD7B36A)
private val Paper = Color(0xFFF2E7D2)
private val Ink = Color(0xFF2E211D)

private enum class CupOutcome { SHENG, LAUGH, YIN }
private enum class MotionMode { STICK, CUP }

// Each block has two visible faces: flat side up or rounded side up.
private data class CupThrow(val firstFlatUp: Boolean, val secondFlatUp: Boolean) {
    val outcome: CupOutcome
        get() = when {
            firstFlatUp != secondFlatUp -> CupOutcome.SHENG
            firstFlatUp && secondFlatUp -> CupOutcome.LAUGH
            else -> CupOutcome.YIN
        }

    val faceSummary: String
        get() = "第一杯：${if (firstFlatUp) "平面朝上" else "凸面朝上"}；第二杯：${if (secondFlatUp) "平面朝上" else "凸面朝上"}"
}

private data class Fortune(
    val number: Int,
    val title: String,
    val grade: String,
    val poem: String,
    val explanation: String,
    val sourceNote: String,
    val career: String = "",
    val relationship: String = "",
    val wealth: String = "",
    val advice: String = "",
    val loveStart: String = "",
    val reunion: String = "",
    val marriage: String = "",
    val timing: String = ""
)

private fun loadYuelaoLots(activity: ComponentActivity): List<Fortune> {
    return try {
        val jsonText = activity.assets.open("fortunes/yuelao.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(jsonText)
        val lots = root.getJSONArray("lots")
        (0 until lots.length()).map { index ->
            val item = lots.getJSONObject(index)
            val poemArray = item.getJSONArray("poem")
            val poem = (0 until poemArray.length()).joinToString("\n") { poemArray.getString(it) }
            Fortune(
                number = item.getInt("number"), title = item.getString("title"), grade = item.getString("grade"),
                poem = poem, explanation = item.optString("vernacular"), sourceNote = item.optString("interpretation_type"),
                relationship = item.optString("relationship"), advice = item.optString("advice"),
                loveStart = item.optString("love_start"), reunion = item.optString("reunion"),
                marriage = item.optString("marriage"), timing = item.optString("timing")
            )
        }
    } catch (_: Exception) { emptyList() }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val guanyinLots = loadGuanyinLots()
        val yuelaoLots = loadYuelaoLots(this)
        setContent { LingQianApp(guanyinLots, yuelaoLots) }
    }

    private fun loadGuanyinLots(): List<Fortune> {
        return try {
            val jsonText = assets.open("fortunes/guanyin_sensoji.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(jsonText)
            val lots = root.getJSONArray("lots")
            (0 until lots.length()).map { index ->
                val item = lots.getJSONObject(index)
                val poemArray = item.getJSONArray("poem")
                val poem = (0 until poemArray.length()).joinToString("\n") { poemArray.getString(it) }
                Fortune(
                    number = item.getInt("number"),
                    title = "${item.getString("grade")} · 第${item.getInt("number")}签",
                    grade = item.getString("grade"),
                    poem = poem,
                    explanation = item.optString("vernacular").takeIf { it.isNotBlank() }
                        ?: "白话解释尚未整理。当前显示的是浅草寺系汉诗百签原文，不等同于所有寺庙使用的观音灵签版本。",
                    sourceNote = item.optString("interpretation_type", "现代原创参考解读"),
                    career = item.optString("career"),
                    relationship = item.optString("relationship"),
                    wealth = item.optString("wealth"),
                    advice = item.optString("advice")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

private enum class Page { HOME, GUANYIN, YUELAO, CUP }

@Composable
private fun LingQianApp(guanyinLots: List<Fortune>, yuelaoLots: List<Fortune>) {
    var page by remember { mutableStateOf(Page.HOME) }
    var fortune by remember { mutableStateOf<Fortune?>(null) }
    var shaking by remember { mutableStateOf(false) }
    var incenseDone by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf("") }
    var cupResults by remember { mutableStateOf(listOf<CupThrow>()) }
    var cupStreak by remember { mutableIntStateOf(0) }
    var cupDone by remember { mutableStateOf(false) }
    var cupTossing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val shakeRotation by animateFloatAsState(
        targetValue = if (shaking) 7f else 0f,
        animationSpec = tween(120),
        label = "stick-shake"
    )
    val cupRotation by animateFloatAsState(
        targetValue = if (cupTossing) 720f else 0f,
        animationSpec = tween(if (cupTossing) 850 else 180),
        label = "cup-toss"
    )
    val drawFortune: () -> Unit = {
        if (incenseDone && fortune == null && !shaking) {
            shaking = true
            scope.launch {
                delay(900)
                fortune = (if (page == Page.GUANYIN) guanyinLots else yuelaoLots).randomOrNull()
                shaking = false
            }
        }
    }
    val throwCup: () -> Unit = {
        if (!cupDone && !cupTossing) {
            cupTossing = true
            scope.launch {
                delay(850)
                val throw = CupThrow(
                    firstFlatUp = Random.nextBoolean(),
                    secondFlatUp = Random.nextBoolean()
                )
                cupResults = cupResults + throw
                cupStreak = if (throw.outcome == CupOutcome.SHENG) cupStreak + 1 else 0
                if (cupStreak >= 3) cupDone = true
                cupTossing = false
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold,
            background = DeepRed,
            surface = Color(0xFF55201D),
            onPrimary = DeepRed,
            onBackground = Paper,
            onSurface = Paper
        )
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF281011), DeepRed, Color(0xFF5C1B19)))
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("靈 籤", color = Gold, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("一念既起，静心而问", color = Paper.copy(alpha = 0.75f), fontSize = 13.sp)
                Spacer(Modifier.height(26.dp))

                when (page) {
                    Page.HOME -> {
                        Text("山门", color = Gold, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text("心诚而问，所得只作参照。", color = Paper.copy(alpha = 0.8f), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(28.dp))
                        TempleCard("观音百签（浅草寺系）", "汉诗百签 · 静心求问") { page = Page.GUANYIN; fortune = null; incenseDone = false }
                        TempleCard("月老灵签", "姻缘签 · 独立签文体系") { page = Page.YUELAO; fortune = null; incenseDone = false }
                        TempleCard("掷圣杯", "模拟两枚杯筊 · 连续三次圣杯确认") { page = Page.CUP; cupResults = emptyList(); cupStreak = 0; cupDone = false }
                        Spacer(Modifier.height(18.dp))
                        Text("本地运行 · 不登录 · 不保存求签记录", color = Paper.copy(alpha = 0.55f), fontSize = 12.sp)
                    }
                    Page.GUANYIN, Page.YUELAO -> {
                        val isGuanyin = page == Page.GUANYIN
                        MotionDetector(
                            enabled = incenseDone && fortune == null,
                            mode = MotionMode.STICK,
                            onDetected = drawFortune
                        )
                        Text(if (isGuanyin) "观音百签 · 浅草寺系" else "月老殿", color = Gold, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("先静心，再握稳手机。左右摇动签筒约三次，停下后自动抽签。", color = Paper.copy(alpha = 0.8f), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(18.dp))
                        OutlinedTextField(
                            value = question,
                            onValueChange = { question = it },
                            label = { Text("问题（可留空）", color = Gold) },
                            placeholder = { Text("只在本次仪式中使用，不会保存", color = Paper.copy(alpha = 0.45f)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Paper, unfocusedTextColor = Paper,
                                focusedBorderColor = Gold, unfocusedBorderColor = Paper.copy(alpha = 0.45f),
                                cursorColor = Gold
                            )
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { incenseDone = !incenseDone },
                            colors = ButtonDefaults.buttonColors(containerColor = if (incenseDone) Color(0xFF42634A) else Lacquer)
                        ) { Text(if (incenseDone) "已上香 · 静心完成" else "上香 / 静心") }
                        Spacer(Modifier.height(20.dp))
                        Text("  ║║║  ", color = Gold, fontSize = 42.sp, modifier = Modifier.rotate(shakeRotation))
                        Text(if (isGuanyin) "签筒 · 百签本地数据" else "签筒 · 本地101条签文", color = Paper.copy(alpha = 0.7f), fontSize = 13.sp)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            enabled = incenseDone && fortune == null && !shaking,
                            onClick = drawFortune,
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = DeepRed)
                        ) { Text("手动摇签（传感器备用）", fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(6.dp))
                        Text("体感模式：摇动手机约三次后停下即可；不必真的抛出手机。", color = Paper.copy(alpha = 0.58f), fontSize = 12.sp, textAlign = TextAlign.Center)
                        AnimatedVisibility(visible = fortune != null) {
                            fortune?.let { f ->
                                Spacer(Modifier.height(24.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Paper),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(20.dp)) {
                                        Text(if (isGuanyin) "观音百签 · 第${f.number}签" else if (f.number == 0) "月老灵签 · 签王" else "月老灵签 · 第${f.number}签", color = Lacquer, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                        Text(f.title, color = Ink, fontSize = 16.sp)
                                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Gold)
                                        Text(f.poem, color = Ink, fontSize = 18.sp)
                                        Spacer(Modifier.height(12.dp))
                                        Text("解签", color = Lacquer, fontWeight = FontWeight.Bold)
                                        Text(f.explanation, color = Ink, lineHeight = 22.sp)
                                        if (isGuanyin) {
                                            Spacer(Modifier.height(12.dp))
                                            Text("事业", color = Lacquer, fontWeight = FontWeight.Bold)
                                            Text(f.career, color = Ink, lineHeight = 21.sp)
                                            Spacer(Modifier.height(8.dp))
                                            Text("感情 / 姻缘", color = Lacquer, fontWeight = FontWeight.Bold)
                                            Text(f.relationship, color = Ink, lineHeight = 21.sp)
                                            Spacer(Modifier.height(8.dp))
                                            Text("财运", color = Lacquer, fontWeight = FontWeight.Bold)
                                            Text(f.wealth, color = Ink, lineHeight = 21.sp)
                                            Spacer(Modifier.height(8.dp))
                                            Text("本签提醒", color = Lacquer, fontWeight = FontWeight.Bold)
                                            Text(f.advice, color = Ink, lineHeight = 21.sp)
                                        } else {
                                            Spacer(Modifier.height(12.dp))
                                            Text("新缘 / 脱单", color = Lacquer, fontWeight = FontWeight.Bold)
                                            Text(f.loveStart, color = Ink, lineHeight = 21.sp)
                                            Spacer(Modifier.height(8.dp))
                                            Text("现有关系", color = Lacquer, fontWeight = FontWeight.Bold)
                                            Text(f.relationship, color = Ink, lineHeight = 21.sp)
                                            Spacer(Modifier.height(8.dp))
                                            Text("复合 / 旧情", color = Lacquer, fontWeight = FontWeight.Bold)
                                            Text(f.reunion, color = Ink, lineHeight = 21.sp)
                                            Spacer(Modifier.height(8.dp))
                                            Text("婚姻 / 长久关系", color = Lacquer, fontWeight = FontWeight.Bold)
                                            Text(f.marriage, color = Ink, lineHeight = 21.sp)
                                            Spacer(Modifier.height(8.dp))
                                            Text("时机提示", color = Lacquer, fontWeight = FontWeight.Bold)
                                            Text(f.timing, color = Ink, lineHeight = 21.sp)
                                            Spacer(Modifier.height(8.dp))
                                            Text("行动建议", color = Lacquer, fontWeight = FontWeight.Bold)
                                            Text(f.advice, color = Ink, lineHeight = 21.sp)
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text("来源状态：${f.sourceNote}", color = Ink.copy(alpha = 0.65f), fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(22.dp))
                        TextButton(onClick = { page = Page.HOME; fortune = null; question = "" }) { Text("返回山门", color = Gold) }
                    }
                    Page.CUP -> {
                        Text("掷圣杯", color = Gold, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        MotionDetector(
                            enabled = !cupDone && !cupTossing,
                            mode = MotionMode.CUP,
                            onDetected = throwCup
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("默念问题，先摇晃手机两三次，再快速向前轻推并停住。识别到动作后，两枚杯筊会翻滚落地。", color = Paper.copy(alpha = 0.8f), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = question,
                            onValueChange = { question = it },
                            label = { Text("所问之事（可留空）", color = Gold) },
                            placeholder = { Text("只在本轮显示，不会保存", color = Paper.copy(alpha = 0.45f)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Paper, unfocusedTextColor = Paper,
                                focusedBorderColor = Gold, unfocusedBorderColor = Paper.copy(alpha = 0.45f),
                                cursorColor = Gold
                            )
                        )
                        Spacer(Modifier.height(20.dp))
                        Text("☯", color = Gold, fontSize = 64.sp, modifier = Modifier.rotate(cupRotation))
                        Text(if (cupTossing) "杯筊翻滚中……" else "摇两三次，再轻推并停住", color = Paper.copy(alpha = 0.62f), fontSize = 12.sp)
                        Text("已掷 ${cupResults.size} 次 · 当前连续圣杯 ${cupStreak}/3", color = Paper.copy(alpha = 0.8f))
                        Spacer(Modifier.height(14.dp))
                        Button(
                            enabled = !cupDone && !cupTossing,
                            onClick = throwCup,
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = DeepRed)
                        ) { Text(if (cupDone) "三次圣杯已确认" else if (cupTossing) "杯筊翻滚中……" else "手动掷杯（传感器备用）") }
                        Spacer(Modifier.height(18.dp))
                        cupResults.forEachIndexed { index, result ->
                            val label = when (result.outcome) {
                                CupOutcome.SHENG -> "圣杯（一平一凸）"
                                CupOutcome.LAUGH -> "笑杯（双平面朝上）"
                                CupOutcome.YIN -> "阴杯（双凸面朝上）"
                            }
                            val detailColor = if (result.outcome == CupOutcome.SHENG) Gold else Paper
                            Text("第 ${index + 1} 次：$label", color = detailColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(result.faceSummary, color = Paper.copy(alpha = 0.65f), fontSize = 12.sp)
                            Spacer(Modifier.height(5.dp))
                        }
                        if (cupDone) {
                            Spacer(Modifier.height(14.dp))
                            Text("连续三次圣杯，仪式确认完成", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("这是数字模拟结果，不代表真实神意或现实事件的确定预言。重要决定仍应结合现实信息与当事人的真实意愿。", color = Paper.copy(alpha = 0.65f), fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
                            TextButton(onClick = { cupResults = emptyList(); cupStreak = 0; cupDone = false }) { Text("重新开始一轮", color = Gold) }
                        } else if (cupResults.isNotEmpty()) {
                            Text("圣杯：连续计数 +1；笑杯或阴杯：连续计数归零。", color = Paper.copy(alpha = 0.6f), fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(22.dp))
                        TextButton(onClick = { page = Page.HOME; question = ""; cupResults = emptyList(); cupStreak = 0; cupDone = false }) { Text("返回山门", color = Gold) }
                    }
                }
            }
        }
    }
}


@Composable
private fun MotionDetector(enabled: Boolean, mode: MotionMode, onDetected: () -> Unit) {
    val context = LocalContext.current
    val latestCallback by rememberUpdatedState(onDetected)
    DisposableEffect(context, enabled, mode) {
        if (!enabled) {
            onDispose { }
        } else {
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val gyroscope = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            val handler = Handler(Looper.getMainLooper())
            var movementCount = 0
            var lastMovement = 0L
            var armedForThrow = false
            var lastTriggered = 0L
            var pendingStick: Runnable? = null
            var disposed = false

            fun trigger() {
                val now = SystemClock.elapsedRealtime()
                if (now - lastTriggered < 1400L || disposed) return
                lastTriggered = now
                movementCount = 0
                armedForThrow = false
                pendingStick?.let(handler::removeCallbacks)
                pendingStick = null
                handler.post { if (!disposed) latestCallback() }
            }

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val now = SystemClock.elapsedRealtime()
                    if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                        val x = event.values[0].toDouble()
                        val y = event.values[1].toDouble()
                        val z = event.values[2].toDouble()
                        val magnitude = kotlin.math.sqrt(x*x + y*y + z*z)
                        val linear = kotlin.math.abs(magnitude - SensorManager.GRAVITY_EARTH.toDouble())
                        if (mode == MotionMode.CUP && armedForThrow && linear > 7.5) {
                            trigger()
                            return
                        }
                        if (linear > 2.4) {
                            if (now - lastMovement > 650L) movementCount = 0
                            movementCount++
                            lastMovement = now
                            if (mode == MotionMode.CUP && movementCount >= 2) armedForThrow = true
                            if (mode == MotionMode.STICK && movementCount >= 3) {
                                pendingStick?.let(handler::removeCallbacks)
                                val task = Runnable {
                                    if (!disposed && movementCount >= 3 && SystemClock.elapsedRealtime() - lastMovement >= 450L) trigger()
                                }
                                pendingStick = task
                                handler.postDelayed(task, 500L)
                            }
                        }
                    } else if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                        val gx = event.values[0].toDouble()
                        val gy = event.values[1].toDouble()
                        val gz = event.values[2].toDouble()
                        val angularSpeed = kotlin.math.sqrt(gx*gx + gy*gy + gz*gz)
                        if (mode == MotionMode.CUP && armedForThrow && angularSpeed > 3.5) {
                            trigger()
                        } else if (mode == MotionMode.STICK && angularSpeed > 1.7) {
                            if (now - lastMovement > 650L) movementCount = 0
                            movementCount++
                            lastMovement = now
                            if (movementCount >= 3) {
                                pendingStick?.let(handler::removeCallbacks)
                                val task = Runnable {
                                    if (!disposed && movementCount >= 3 && SystemClock.elapsedRealtime() - lastMovement >= 450L) trigger()
                                }
                                pendingStick = task
                                handler.postDelayed(task, 500L)
                            }
                        } else if (mode == MotionMode.CUP && angularSpeed > 1.8) {
                            if (now - lastMovement > 650L) movementCount = 0
                            movementCount++
                            lastMovement = now
                            if (movementCount >= 2) armedForThrow = true
                        }
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }

            if (accelerometer != null) manager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            if (gyroscope != null) manager.registerListener(listener, gyroscope, SensorManager.SENSOR_DELAY_GAME)
            onDispose {
                disposed = true
                pendingStick?.let(handler::removeCallbacks)
                manager.unregisterListener(listener)
            }
        }
    }
}

@Composable
private fun TempleCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF5A201D)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("▥", color = Gold, fontSize = 28.sp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Gold, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = Paper.copy(alpha = 0.72f), fontSize = 12.sp)
            }
            Text("›", color = Gold, fontSize = 26.sp)
        }
    }
}
