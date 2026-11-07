package com.binisog.amharickeyboard.core

/**
 * Emoji, organised into the categories shown as tabs in the emoji panel. Kept to emoji that
 * render on Android 7+ so they do not show as empty boxes on older phones.
 */
object EmojiData {

    data class Category(val id: String, val icon: String, val title: String, val items: List<String>)

    private fun list(s: String) = s.trim().split(Regex("\\s+"))

    val SMILEYS = Category("smileys", "😀", "Smileys", list("""
        😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔
        🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 🤥 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🤧 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 🤓
        🧐 😕 😟 🙁 ☹️ 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬
        😈 👿 💀 ☠️ 💩 🤡 👹 👺 👻 👽 👾 🤖 😺 😸 😹 😻 😼 😽 🙀 😿 😾 🙈 🙉 🙊
    """))

    val PEOPLE = Category("people", "👋", "People & gestures", list("""
        👋 🤚 🖐️ ✋ 🖖 👌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🤝 🙏
        ✍️ 💅 🤳 💪 🦵 🦶 👂 👃 🧠 👀 👁️ 👅 👄 👶 🧒 👦 👧 🧑 👱 👨 🧔 👩 🧓 👴 👵 🙍 🙎 🙅 🙆
        💁 🙋 🙇 🤦 🤷 👨‍⚕️ 👩‍⚕️ 👨‍🎓 👩‍🎓 👨‍🏫 👩‍🏫 👨‍🌾 👩‍🌾 👨‍🍳 👩‍🍳 👨‍💻 👩‍💻 👮 💂 👷 🤴 👸 👰 🤵 🤰
        🤱 👼 🎅 🤶 🧕 🚶 🏃 💃 🕺 👯 🧘 🛀 🛌 👭 👫 👬 💏 💑 👪
    """))

    val HEARTS = Category("hearts", "❤️", "Hearts & love", list("""
        ❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ♥️ 💋 💌 💐 🌹 🥀 💍 💎 😍 🥰 😘
        💏 💑 🫶 🤗 💯 ✨ 🌟 💫
    """))

    val NATURE = Category("nature", "🌿", "Animals & nature", list("""
        🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋
        🐌 🐞 🐜 🦗 🕷️ 🦂 🐢 🐍 🦎 🐙 🦑 🦐 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🦍 🐘 🦛 🦏 🐪 🐫
        🦒 🦘 🐃 🐂 🐄 🐎 🐖 🐏 🐑 🐐 🦌 🐕 🐈 🐓 🦃 🕊️ 🐇 🐁 🐿️ 🦔 🌵 🎄 🌲 🌳 🌴 🌱 🌿 ☘️ 🍀 🍃
        🍂 🍁 🍄 🌾 💐 🌷 🌹 🥀 🌺 🌸 🌼 🌻 🌞 🌝 🌛 🌜 🌚 🌕 🌙 🌎 🌍 🌏 🪐 💫 ⭐ 🌟 ✨ ⚡ 🔥 💥
        ☀️ 🌤️ ⛅ 🌥️ ☁️ 🌦️ 🌧️ ⛈️ 🌩️ 🌨️ ❄️ ☃️ ⛄ 🌬️ 💨 💧 💦 ☔ 🌊 🌈
    """))

    val FOOD = Category("food", "☕", "Food & drink", list("""
        ☕ 🫖 🍵 🥛 🧃 🥤 🍶 🍺 🍻 🍷 🥂 🍸 🍹 🍾 🧉 🍞 🫓 🥖 🥐 🥨 🥯 🧀 🥚 🍳 🥞 🧇 🥓 🥩 🍗 🍖
        🌭 🍔 🍟 🍕 🥪 🌮 🌯 🥙 🧆 🥘 🍲 🍛 🍜 🍝 🍠 🍢 🍣 🍤 🍙 🍚 🍘 🥗 🥫 🍿 🧂 🍩 🍪 🎂 🍰 🧁
        🥧 🍫 🍬 🍭 🍮 🍯 🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬 🥒 🌶️ 🌽
        🥕 🧄 🧅 🥔 🥜 🌰 🍽️ 🍴 🥄 🔪
    """))

    val ACTIVITIES = Category("activities", "⚽", "Activities & celebrations", list("""
        🎉 🎊 🎈 🎁 🎀 🎂 🕯️ 🎄 🎆 🎇 🧨 ✨ 🎃 🎗️ 🎟️ 🎫 🏆 🥇 🥈 🥉 🏅 🎖️ ⚽ 🏀 🏈 ⚾ 🥎 🎾 🏐 🏉
        🥏 🎱 🏓 🏸 🏒 🏑 🥍 🏏 ⛳ 🏹 🎣 🥊 🥋 🎽 🛹 ⛸️ 🎿 🛷 🏂 🏋️ 🤸 ⛹️ 🤾 🏌️ 🏇 🧗 🚴 🚵 🏊 🤽
        🎮 🕹️ 🎲 ♟️ 🧩 🎯 🎳 🎭 🎨 🎬 🎤 🎧 🎼 🎹 🥁 🎷 🎺 🎸 🪕 🎻 📸
    """))

    val TRAVEL = Category("travel", "🚗", "Travel & places", list("""
        🚗 🚕 🚙 🚌 🚎 🏎️ 🚓 🚑 🚒 🚐 🚚 🚛 🚜 🛵 🏍️ 🚲 🛴 🚨 🚔 🚍 🚘 🚖 🚡 🚠 🚟 🚃 🚋 🚞 🚝 🚄
        🚅 🚈 🚂 🚆 🚇 🚊 🚉 ✈️ 🛫 🛬 🛩️ 💺 🚁 🛶 ⛵ 🚤 🛳️ ⛴️ 🚢 ⚓ ⛽ 🚧 🚦 🚥 🗺️ 🧭 🏔️ ⛰️ 🌋 🏕️
        🏖️ 🏜️ 🏝️ 🏞️ 🏟️ 🏛️ 🏗️ 🏘️ 🏚️ 🏠 🏡 🏢 🏣 🏤 🏥 🏦 🏨 🏩 🏪 🏫 🏬 🏭 🏯 🏰 💒 🗼 🗽 ⛪ 🕌 🕍
        ⛩️ 🕋 ⛲ ⛺ 🌁 🌃 🏙️ 🌄 🌅 🌆 🌇 🌉 🎠 🎡 🎢 🗿
    """))

    val OBJECTS = Category("objects", "💡", "Objects", list("""
        📱 📲 💻 ⌨️ 🖥️ 🖨️ 🖱️ 💽 💾 💿 📀 📷 📹 🎥 📞 ☎️ 📟 📠 📺 📻 🎙️ ⏰ ⏱️ ⏲️ 🕰️ ⌛ ⏳ 📡 🔋 🔌
        💡 🔦 🕯️ 🧯 💸 💵 💴 💶 💷 💰 💳 💎 ⚖️ 🔧 🔨 ⚒️ 🛠️ ⛏️ 🔩 ⚙️ 🧱 ⛓️ 🧲 🔫 💣 🧨 🔪 🗡️ ⚔️ 🛡️
        🔮 📿 🧿 💈 ⚗️ 🔭 🔬 🩹 🩺 💊 💉 🧬 🦠 🧫 🧪 🌡️ 🧹 🧺 🧻 🚽 🚰 🚿 🛁 🧼 🧽 🧴 🛎️ 🔑 🗝️ 🚪
        🛋️ 🛏️ 🖼️ 🛍️ 🛒 🎁 📦 📫 📬 📮 📜 📃 📄 📑 📊 📈 📉 📆 📅 📇 🗂️ 📋 📁 📂 📰 📓 📔 📒 📕 📗
        📘 📙 📚 📖 🔖 🔗 📎 📐 📏 📌 📍 ✂️ 🖊️ 🖋️ ✒️ 🖌️ 🖍️ 📝 ✏️ 🔍 🔎 🔏 🔐 🔒 🔓 👓 🕶️ 👔 👕
        👖 🧣 🧤 🧥 🧦 👗 👘 👙 👚 👛 👜 👝 🎒 👞 👟 🥾 👠 👡 👢 👑 👒 🎩 🎓 🧢 ⛑️ 💄 💍 🌂 ☂️
    """))

    val SYMBOLS = Category("symbols", "✅", "Symbols", list("""
        ✅ ☑️ ✔️ ❌ ❎ ➕ ➖ ➗ ✖️ 💯 ❗ ❕ ❓ ❔ ‼️ ⁉️ ⚠️ 🚫 ⛔ 📛 🔞 ♻️ ⚜️ 🔰 ✳️ ✴️ ❇️ ©️ ®️ ™️
        🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ 🟤 🔺 🔻 🔸 🔹 🔶 🔷 ⬛ ⬜ ◼️ ◻️ ▪️ ▫️ 🔈 🔉 🔊 🔇 📣 📢 🔔 🔕 💬
        💭 🗯️ ♠️ ♣️ ♥️ ♦️ 🃏 🎴 🀄 🕐 🕑 🕒 🕓 🕔 🕕 🕖 🕗 🕘 🕙 🕚 🕛 ⬆️ ↗️ ➡️ ↘️ ⬇️ ↙️ ⬅️ ↖️
        ↕️ ↔️ ↩️ ↪️ ⤴️ ⤵️ 🔃 🔄 🔙 🔚 🔛 🔜 🔝 🛐 ⚛️ ✝️ ☦️ ☪️ ☮️ 🕎 ☯️ ♈ ♉ ♊ ♋ ♌ ♍ ♎ ♏ ♐ ♑
        ♒ ♓ 🆗 🆕 🆙 🆒 🆓 🆘 ℹ️ 🔤 🔡 🔠 🔢 #️⃣ *️⃣ 0️⃣ 1️⃣ 2️⃣ 3️⃣ 4️⃣ 5️⃣ 6️⃣ 7️⃣ 8️⃣ 9️⃣ 🔟
    """))

    val FLAGS = Category("flags", "🇪🇹", "Flags", list("""
        🇪🇹 🇪🇷 🇰🇪 🇸🇴 🇩🇯 🇸🇩 🇸🇸 🇺🇬 🇹🇿 🇷🇼 🇪🇬 🇳🇬 🇬🇭 🇿🇦 🇲🇦 🇩🇿 🇹🇳 🇸🇳 🇨🇲 🇨🇩 🇦🇴 🇿🇼
        🇺🇸 🇨🇦 🇬🇧 🇮🇪 🇫🇷 🇩🇪 🇮🇹 🇪🇸 🇵🇹 🇳🇱 🇧🇪 🇸🇪 🇳🇴 🇩🇰 🇫🇮 🇨🇭 🇦🇹 🇬🇷 🇹🇷 🇷🇺 🇺🇦 🇵🇱
        🇮🇱 🇵🇸 🇸🇦 🇦🇪 🇶🇦 🇰🇼 🇱🇧 🇯🇴 🇮🇳 🇵🇰 🇨🇳 🇯🇵 🇰🇷 🇧🇷 🇦🇷 🇲🇽 🇦🇺 🇳🇿 🏳️ 🏴 🏁 🚩 🏳️‍🌈 🇺🇳
    """))

    /** Text emoticons (kaomoji) that work in every app, even without emoji fonts. */
    val KAOMOJI = Category("kaomoji", ":-)", "Text emoticons", listOf(
        ":)", ":-)", ";)", ":D", "XD", ":P", ":(", ":'(", ":O", "<3", "^_^", "^^", "-_-", "o_O", "T_T",
        "¯\\_(ツ)_/¯", "(╯°□°)╯︵ ┻━┻", "┬─┬ノ( º _ ºノ)", "( ͡° ͜ʖ ͡°)", "ʕ•ᴥ•ʔ", "(◕‿◕)", "(｡◕‿◕｡)",
        "(⌐■_■)", "(ಥ﹏ಥ)", "(ノ◕ヮ◕)ノ*:・゚✧", "ヽ(•‿•)ノ", "(づ｡◕‿‿◕｡)づ", "( ˘ ³˘)♥", "(✿◠‿◠)",
        "(¬‿¬)", "ಠ_ಠ", "(•_•)", "( •_•)>⌐■-■", "(☞ﾟヮﾟ)☞", "☜(ﾟヮﾟ☜)", "(ᵔᴥᵔ)", "(>_<)", "(^_−)☆",
        "(o^▽^o)", "(*^‿^*)", "(╥_╥)", "(҂◡_◡)", "\\(^o^)/", "(~˘▾˘)~", "♪~ ᕕ(ᐛ)ᕗ", "ᕦ(ò_óˇ)ᕤ",
    ))

    /** Built-in categories in tab order (Recent and My packs are added by the panel). */
    val CATEGORIES: List<Category> = listOf(SMILEYS, PEOPLE, HEARTS, NATURE, FOOD, ACTIVITIES, TRAVEL, OBJECTS, SYMBOLS, FLAGS, KAOMOJI)

    /** Most-recently-used list: newest first, no duplicates, at most [max]. */
    fun pushRecent(recent: List<String>, emoji: String, max: Int = 32): List<String> =
        (listOf(emoji) + recent.filter { it != emoji }).take(max)
}
