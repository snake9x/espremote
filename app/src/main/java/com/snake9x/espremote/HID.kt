package com.snake9x.espremote

data class HIDCommand(val modifiers: Byte, val keycode: Byte)

// Каждая раскладка маппит символы Unicode → HID-коды US-клавиатуры
abstract class KeyboardLayout {
    abstract val name: String
    abstract val flag: String
    // Символ Unicode → HID-команда (модификатор + кейкод US-клавиши)
    abstract val map: Map<Char, HIDCommand>

    fun mapChar(ch: Char): HIDCommand? = map[ch]
}

object HID {
    // Modifier bitmasks
    const val MOD_NONE:        Byte = 0x00
    const val MOD_LEFT_CTRL:   Byte = 0x01
    const val MOD_LEFT_SHIFT:  Byte = 0x02
    const val MOD_LEFT_ALT:    Byte = 0x04
    const val MOD_LEFT_GUI:    Byte = 0x08
    const val MOD_RIGHT_CTRL:  Byte = 0x10.toByte()
    const val MOD_RIGHT_SHIFT: Byte = 0x20.toByte()
    const val MOD_RIGHT_ALT:   Byte = 0x40.toByte()
    const val MOD_RIGHT_GUI:   Byte = 0x80.toByte()

    // Special keycodes
    const val KEY_ENTER:       Byte = 0x28
    const val KEY_ESCAPE:      Byte = 0x29
    const val KEY_BACKSPACE:   Byte = 0x2A
    const val KEY_TAB:         Byte = 0x2B
    const val KEY_SPACE:       Byte = 0x2C
    const val KEY_RIGHT_ARROW: Byte = 0x4F
    const val KEY_LEFT_ARROW:  Byte = 0x50
    const val KEY_DOWN_ARROW:  Byte = 0x51
    const val KEY_UP_ARROW:    Byte = 0x52
    const val KEY_DELETE:      Byte = 0x4C
    const val KEY_HOME:        Byte = 0x4A
    const val KEY_END:         Byte = 0x4D
    const val KEY_PAGE_UP:     Byte = 0x4B
    const val KEY_PAGE_DOWN:   Byte = 0x4E
    const val KEY_CAPS_LOCK:   Byte = 0x39
    const val KEY_F1:          Byte = 0x3A
    const val KEY_F2:          Byte = 0x3B
    const val KEY_F3:          Byte = 0x3C
    const val KEY_F4:          Byte = 0x3D
    const val KEY_F5:          Byte = 0x3E
    const val KEY_F6:          Byte = 0x3F
    const val KEY_F7:          Byte = 0x40
    const val KEY_F8:          Byte = 0x41
    const val KEY_F9:          Byte = 0x42
    const val KEY_F10:         Byte = 0x43
    const val KEY_F11:         Byte = 0x44
    const val KEY_F12:         Byte = 0x45

    val layouts: List<KeyboardLayout> = listOf(
        LayoutEN, LayoutRU
    )

    fun layoutByName(name: String) = layouts.find { it.name == name } ?: LayoutEN
}

// ─────────────────────────────────────────────────────────────────────────────
// Базовые US-коды (переиспользуются во всех раскладках)
// ─────────────────────────────────────────────────────────────────────────────

private fun usBase(): MutableMap<Char, HIDCommand> = mutableMapOf<Char, HIDCommand>().apply {
    val S = HID.MOD_LEFT_SHIFT
    // a-z
    "abcdefghijklmnopqrstuvwxyz".forEachIndexed { i, c ->
        put(c, HIDCommand(0x00, (0x04 + i).toByte()))
    }
    // A-Z
    "ABCDEFGHIJKLMNOPQRSTUVWXYZ".forEachIndexed { i, c ->
        put(c, HIDCommand(S, (0x04 + i).toByte()))
    }
    // Цифры
    put('1', HIDCommand(0x00, 0x1E)); put('2', HIDCommand(0x00, 0x1F))
    put('3', HIDCommand(0x00, 0x20)); put('4', HIDCommand(0x00, 0x21))
    put('5', HIDCommand(0x00, 0x22)); put('6', HIDCommand(0x00, 0x23))
    put('7', HIDCommand(0x00, 0x24)); put('8', HIDCommand(0x00, 0x25))
    put('9', HIDCommand(0x00, 0x26)); put('0', HIDCommand(0x00, 0x27))
    // Shifted цифры
    put('!', HIDCommand(S, 0x1E)); put('@', HIDCommand(S, 0x1F))
    put('#', HIDCommand(S, 0x20)); put('$', HIDCommand(S, 0x21))
    put('%', HIDCommand(S, 0x22)); put('^', HIDCommand(S, 0x23))
    put('&', HIDCommand(S, 0x24)); put('*', HIDCommand(S, 0x25))
    put('(', HIDCommand(S, 0x26)); put(')', HIDCommand(S, 0x27))
    // Пунктуация
    put('-', HIDCommand(0x00, 0x2D)); put('=', HIDCommand(0x00, 0x2E))
    put('[', HIDCommand(0x00, 0x2F)); put(']', HIDCommand(0x00, 0x30))
    put('\\',HIDCommand(0x00, 0x31)); put(';', HIDCommand(0x00, 0x33))
    put('\'',HIDCommand(0x00, 0x34)); put('`', HIDCommand(0x00, 0x35))
    put(',', HIDCommand(0x00, 0x36)); put('.', HIDCommand(0x00, 0x37))
    put('/', HIDCommand(0x00, 0x38))
    put('_', HIDCommand(S, 0x2D)); put('+', HIDCommand(S, 0x2E))
    put('{', HIDCommand(S, 0x2F)); put('}', HIDCommand(S, 0x30))
    put('|', HIDCommand(S, 0x31)); put(':', HIDCommand(S, 0x33))
    put('"', HIDCommand(S, 0x34)); put('~', HIDCommand(S, 0x35))
    put('<', HIDCommand(S, 0x36)); put('>', HIDCommand(S, 0x37))
    put('?', HIDCommand(S, 0x38))
    put(' ', HIDCommand(0x00, HID.KEY_SPACE))
    put('\t', HIDCommand(0x00, HID.KEY_TAB))
}

// ─────────────────────────────────────────────────────────────────────────────
// EN — стандартная US QWERTY
// ─────────────────────────────────────────────────────────────────────────────
object LayoutEN : KeyboardLayout() {
    override val name = "EN"
    override val flag = "🇺🇸"
    override val map  = usBase()
}

// ─────────────────────────────────────────────────────────────────────────────
// RU — ЙЦУКЕН. Маппинг: русская буква → US-клавиша в русской раскладке Windows
// Логика: при активной RU-раскладке на ПК нажатие US-клавиши даёт русскую букву
// ─────────────────────────────────────────────────────────────────────────────
object LayoutRU : KeyboardLayout() {
    override val name = "RU"
    override val flag = "🇷🇺"
    override val map: Map<Char, HIDCommand> = usBase().apply {
        val S = HID.MOD_LEFT_SHIFT
        // Точная таблица ЙЦУКЕН — проверено по стандартной Russian раскладке Windows
        // й=q  ц=w  у=e  к=r  е=t  н=y  г=u  ш=i  щ=o  з=p  х=[  ъ=]
        // ф=a  ы=s  в=d  а=f  п=g  р=h  о=j  л=k  д=l  ж=;  э='
        // я=z  ч=x  с=c  м=v  и=b  т=n  ь=m  б=,  ю=.  ё=`
        val keymap = listOf(
            'й' to 0x14, 'ц' to 0x1A, 'у' to 0x08, 'к' to 0x15, 'е' to 0x17,
            'н' to 0x1C, 'г' to 0x18, 'ш' to 0x0C, 'щ' to 0x12, 'з' to 0x13,
            'х' to 0x2F, 'ъ' to 0x30,
            'ф' to 0x04, 'ы' to 0x16, 'в' to 0x07, 'а' to 0x09, 'п' to 0x0A,
            'р' to 0x0B, 'о' to 0x0D, 'л' to 0x0E, 'д' to 0x0F, 'ж' to 0x33,
            'э' to 0x34,
            'я' to 0x1D, 'ч' to 0x1B, 'с' to 0x06, 'м' to 0x19, 'и' to 0x05,
            'т' to 0x11, 'ь' to 0x10, 'б' to 0x36, 'ю' to 0x37, 'ё' to 0x35
        )
        keymap.forEach { (ru, kc) ->
            put(ru,                 HIDCommand(0x00, kc.toByte()))
            put(ru.uppercaseChar(), HIDCommand(S,    kc.toByte()))
        }
    }
}

