package com.example.sudokugraphite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.random.Random

private val Bg = Color(0xFF17191D)
private val Panel = Color(0xFF22252B)
private val Cell = Color(0xFF1D2025)
private val Line = Color(0xFF3B414B)
private val TextMain = Color(0xFFE8EAF0)
private val Muted = Color(0xFF9AA3B2)
private val Accent = Color(0xFF78B7FF)
private val Selected = Color(0xFF33485F)
private val SameDigit = Color(0xFF293746)
private val ErrorColor = Color(0xFFFF7777)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("sudoku_graphite", MODE_PRIVATE)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Accent, background = Bg, surface = Panel,
                onBackground = TextMain, onSurface = TextMain
            )) {
                SudokuScreen(
                    loadData = { prefs.getString("state", null) },
                    saveData = { prefs.edit().putString("state", it).apply() }
                )
            }
        }
    }
}

private data class Puzzle(val solution: IntArray, val puzzle: IntArray, val givens: BooleanArray, val difficulty: String)

@Composable
private fun SudokuScreen(loadData: () -> String?, saveData: (String) -> Unit) {
    var difficulty by remember { mutableStateOf("Средний") }
    var solution by remember { mutableStateOf(IntArray(81)) }
    var board by remember { mutableStateOf(IntArray(81)) }
    var givens by remember { mutableStateOf(BooleanArray(81)) }
    var notes by remember { mutableStateOf(Array(81) { mutableSetOf<Int>() }) }
    var selected by remember { mutableIntStateOf(40) }
    var notesMode by remember { mutableStateOf(false) }
    var mistakes by remember { mutableIntStateOf(0) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var bestSeconds by remember { mutableLongStateOf(0L) }
    var gamesWon by remember { mutableIntStateOf(0) }
    var showStats by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Выбери клетку и нажми цифру") }
    var puzzleId by remember { mutableIntStateOf(0) }

    fun save() {
        val obj = JSONObject()
        obj.put("difficulty", difficulty)
        obj.put("solution", JSONArray(solution.toList()))
        obj.put("board", JSONArray(board.toList()))
        obj.put("givens", JSONArray(givens.map { if (it) 1 else 0 }))
        obj.put("notes", JSONArray().apply {
            notes.forEach { n -> put(JSONArray(n.sorted())) }
        })
        obj.put("selected", selected)
        obj.put("notesMode", notesMode)
        obj.put("mistakes", mistakes)
        obj.put("elapsed", elapsed)
        obj.put("bestSeconds", bestSeconds)
        obj.put("gamesWon", gamesWon)
        obj.put("puzzleId", puzzleId)
        saveData(obj.toString())
    }

    fun newGame(level: String = difficulty) {
        difficulty = level
        val puzzle = SudokuEngine.create(level)
        solution = puzzle.solution
        board = puzzle.puzzle.copyOf()
        givens = puzzle.givens
        notes = Array(81) { mutableSetOf() }
        selected = board.indexOfFirst { it == 0 }.let { if (it >= 0) it else 40 }
        mistakes = 0
        elapsed = 0
        notesMode = false
        message = "Новая игра • $difficulty"
        puzzleId++
        save()
    }

    LaunchedEffect(Unit) {
        val raw = loadData()
        if (raw != null) {
            runCatching {
                val o = JSONObject(raw)
                difficulty = o.optString("difficulty", "Средний")
                solution = o.getJSONArray("solution").toIntArray81()
                board = o.getJSONArray("board").toIntArray81()
                givens = o.getJSONArray("givens").let { a -> BooleanArray(81) { a.optInt(it) == 1 } }
                notes = Array(81) { i ->
                    val a = o.optJSONArray("notes")?.optJSONArray(i)
                    mutableSetOf<Int>().apply { if (a != null) for (j in 0 until a.length()) add(a.optInt(j)) }
                }
                selected = o.optInt("selected", 40).coerceIn(0, 80)
                notesMode = o.optBoolean("notesMode", false)
                mistakes = o.optInt("mistakes", 0)
                elapsed = o.optLong("elapsed", 0)
                bestSeconds = o.optLong("bestSeconds", 0)
                gamesWon = o.optInt("gamesWon", 0)
                puzzleId = o.optInt("puzzleId", 0)
            }.onFailure { newGame("Средний") }
        } else newGame("Средний")
    }

    LaunchedEffect(puzzleId) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            elapsed++
            save()
        }
    }

    fun enterDigit(digit: Int) {
        if (givens.getOrNull(selected) == true) return
        if (notesMode) {
            notes = notes.copyOf().also { it[selected] = it[selected].toMutableSet().apply {
                if (!add(digit)) remove(digit)
            } }
            save()
            return
        }
        if (solution[selected] != digit) {
            mistakes++
            message = "Ошибка $mistakes — попробуй ещё раз"
            save()
            return
        }
        board = board.copyOf().also { it[selected] = digit }
        notes = notes.copyOf().also { it[selected] = mutableSetOf() }
        for (i in 0 until 81) if (i / 9 == selected / 9 || i % 9 == selected % 9 ||
            (i / 27 == selected / 27 && i % 9 / 3 == selected % 9 / 3)) {
            notes[i].remove(digit)
        }
        message = "Верно!"
        if (board.all { it != 0 }) {
            gamesWon++
            if (bestSeconds == 0L || elapsed < bestSeconds) bestSeconds = elapsed
            message = "Готово! Отличная работа 🎉"
        }
        save()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Bg).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("SUDOKU", color = TextMain, fontSize = 25.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Text("GRAPHITE  •  БЕЗ РЕКЛАМЫ", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp)
            }
            TextButton(onClick = { showStats = true }) { Text("СТАТИСТИКА", fontSize = 11.sp) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip("ВРЕМЯ", formatTime(elapsed), Modifier.weight(1f))
            StatChip("ОШИБКИ", "$mistakes", Modifier.weight(1f))
            StatChip("РЕКОРД", if (bestSeconds == 0L) "—" else formatTime(bestSeconds), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Лёгкий", "Средний", "Сложный", "Эксперт").forEach { level ->
                val active = difficulty == level
                Box(
                    Modifier.weight(1f).background(if (active) Selected else Panel, RoundedCornerShape(10.dp))
                        .clickable { newGame(level) }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(level, color = if (active) Accent else Muted, fontSize = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(message, color = if (message.startsWith("Ошибка")) ErrorColor else Muted, fontSize = 12.sp, maxLines = 1)
        Spacer(Modifier.height(8.dp))

        Column(Modifier.fillMaxWidth().aspectRatio(1f).background(Line, RoundedCornerShape(4.dp)).padding(1.dp)) {
            for (r in 0..8) {
                Row(Modifier.weight(1f)) {
                    for (c in 0..8) {
                        val i = r * 9 + c
                        val value = board[i]
                        val isSelected = selected == i
                        val sameValue = value != 0 && board[selected] == value
                        val related = r == selected / 9 || c == selected % 9 ||
                            (r / 3 == selected / 3 && c / 3 == selected % 3)
                        val bg = when {
                            isSelected -> Selected
                            sameValue -> SameDigit
                            related -> Color(0xFF242A32)
                            else -> Cell
                        }
                        Box(
                            Modifier.weight(1f).fillMaxHeight().padding(
                                end = if (c == 2 || c == 5) 1.5.dp else 0.35.dp,
                                bottom = if (r == 2 || r == 5) 1.5.dp else 0.35.dp
                            ).background(bg).clickable { selected = i; message = "Клетка ${r + 1}:${c + 1}" },
                            contentAlignment = Alignment.Center
                        ) {
                            if (value != 0) {
                                Text(
                                    "$value",
                                    color = if (givens[i]) TextMain else Accent,
                                    fontSize = 19.sp,
                                    fontWeight = if (givens[i]) FontWeight.Medium else FontWeight.Bold
                                )
                            } else if (notes[i].isNotEmpty()) {
                                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                                    for (nr in 0..2) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                            for (nc in 1..3) {
                                                val n = nr * 3 + nc
                                                Text(if (n in notes[i]) "$n" else "", color = Muted, fontSize = 7.sp, lineHeight = 8.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            (1..9).forEach { digit ->
                Box(
                    Modifier.weight(1f).height(47.dp).background(Panel, RoundedCornerShape(9.dp))
                        .clickable { enterDigit(digit) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("$digit", fontSize = 20.sp, color = Accent, fontWeight = FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ActionButton(if (notesMode) "✓ ЗАМЕТКИ" else "✎ ЗАМЕТКИ", notesMode, Modifier.weight(1f)) { notesMode = !notesMode; save() }
            ActionButton("⌫ СТЕРЕТЬ", false, Modifier.weight(1f)) {
                if (!givens[selected]) {
                    board = board.copyOf().also { it[selected] = 0 }
                    notes = notes.copyOf().also { it[selected].clear() }
                    save()
                }
            }
            ActionButton("НОВАЯ", false, Modifier.weight(1f)) { newGame() }
        }
        Spacer(Modifier.height(6.dp))
        Text("Выбрана клетка ${selected / 9 + 1} × ${selected % 9 + 1}", color = Muted, fontSize = 10.sp)
    }

    if (showStats) {
        AlertDialog(
            onDismissRequest = { showStats = false },
            containerColor = Panel,
            title = { Text("Твоя статистика", color = TextMain) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatLine("Завершено игр", "$gamesWon")
                    StatLine("Лучшее время", if (bestSeconds == 0L) "Пока нет рекорда" else formatTime(bestSeconds))
                    StatLine("Текущая сложность", difficulty)
                    StatLine("Ошибок в партии", "$mistakes")
                    Text("Прогресс хранится только на этом телефоне.", color = Muted, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showStats = false }) { Text("ЗАКРЫТЬ") } }
        )
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Panel, RoundedCornerShape(10.dp)).padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ActionButton(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.height(42.dp).background(if (active) Selected else Panel, RoundedCornerShape(9.dp))
        .clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text(label, color = if (active) Accent else TextMain, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 13.sp)
        Text(value, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun JSONArray.toIntArray81(): IntArray = IntArray(81) { i -> optInt(i, 0) }
private fun formatTime(s: Long): String = "%02d:%02d".format(s / 60, s % 60)

private object SudokuEngine {
    data class Generated(val solution: IntArray, val puzzle: IntArray, val givens: BooleanArray, val difficulty: String)

    fun create(level: String): Generated {
        val grid = IntArray(81)
        fill(grid, 0)
        val clues = when (level) {
            "Лёгкий" -> 39
            "Средний" -> 32
            "Сложный" -> 27
            else -> 23
        }
        val puzzle = grid.copyOf()
        var removed = 0
        val targets = (0 until 81).shuffled().toMutableList()
        for (idx in targets) {
            if (removed >= 81 - clues) break
            val old = puzzle[idx]
            puzzle[idx] = 0
            if (countSolutions(puzzle.copyOf(), 2) == 1) removed++ else puzzle[idx] = old
        }
        val givens = BooleanArray(81) { puzzle[it] != 0 }
        return Generated(grid, puzzle, givens, level)
    }

    private fun fill(g: IntArray, pos: Int): Boolean {
        if (pos == 81) return true
        val r = pos / 9
        val c = pos % 9
        for (n in (1..9).shuffled()) {
            if (valid(g, r, c, n)) {
                g[pos] = n
                if (fill(g, pos + 1)) return true
                g[pos] = 0
            }
        }
        return false
    }

    private fun valid(g: IntArray, r: Int, c: Int, n: Int): Boolean {
        for (i in 0..8) {
            if (g[r * 9 + i] == n || g[i * 9 + c] == n) return false
        }
        val br = r / 3 * 3
        val bc = c / 3 * 3
        for (rr in br until br + 3) for (cc in bc until bc + 3) if (g[rr * 9 + cc] == n) return false
        return true
    }

    private fun countSolutions(g: IntArray, limit: Int): Int {
        var pos = -1
        var fewest = 10
        for (i in 0 until 81) if (g[i] == 0) {
            val r = i / 9; val c = i % 9
            val opts = (1..9).filter { valid(g, r, c, it) }
            if (opts.isEmpty()) return 0
            if (opts.size < fewest) { fewest = opts.size; pos = i }
        }
        if (pos == -1) return 1
        var count = 0
        val r = pos / 9; val c = pos % 9
        for (n in 1..9) if (valid(g, r, c, n)) {
            g[pos] = n
            count += countSolutions(g, limit - count)
            g[pos] = 0
            if (count >= limit) return count
        }
        return count
    }
}
