package com.karakept.app.ui.components

/**
 * Organized emoji collections for filter icons.
 * Avoids hardcoding thousands of emojis throughout the codebase.
 */
object EmojiConstants {

    object Documents {
        val all = listOf(
            "📋", // Clipboard
            "📄", // Page
            "📃", // Page with curl
            "📑", // Bookmark tabs
            "📊", // Bar chart
            "📈", // Chart increasing
            "📉", // Chart decreasing
            "📝", // Memo
            "📁", // Folder
            "📂", // Open folder
            "🗂️", // Card index dividers
            "🗃️", // Card file box
            "📇", // Card index
            "🗄️"  // File cabinet
        )
    }

    object Books {
        val all = listOf(
            "📚", // Books
            "📖", // Open book
            "📕", // Closed book (red)
            "📗", // Closed book (green)
            "📘", // Closed book (blue)
            "📙", // Closed book (orange)
            "🎓", // Graduation cap
            "📓", // Notebook
            "📔", // Notebook with decorative cover
            "📒", // Ledger
            "📜"  // Scroll
        )
    }

    object Organization {
        val all = listOf(
            "📌", // Pushpin
            "📍", // Round pushpin
            "🔖", // Bookmark
            "🏷️", // Label
            "🗂️", // Card index dividers
            "🗃️", // Card file box
            "📎", // Paperclip
            "🖇️", // Linked paperclips
            "✂️", // Scissors
            "📐", // Triangular ruler
            "📏"  // Straight ruler
        )
    }

    object Symbols {
        val all = listOf(
            "⭐", // Star
            "✨", // Sparkles
            "💡", // Light bulb
            "🔥", // Fire
            "⚡", // High voltage
            "✅", // Check mark button
            "❌", // Cross mark
            "⚠️", // Warning
            "💯", // Hundred points
            "🎯", // Direct hit
            "🏆", // Trophy
            "🎁", // Wrapped gift
            "🎨", // Artist palette
            "🔔", // Bell
            "📢", // Loudspeaker
            "🔍", // Magnifying glass left
            "🔎", // Magnifying glass right
            "🔑", // Key
            "🔓", // Unlocked
            "🔒"  // Locked
        )
    }

    object Time {
        val all = listOf(
            "⏰", // Alarm clock
            "⏱️", // Stopwatch
            "⏲️", // Timer clock
            "🕐", // One o'clock
            "📅", // Calendar
            "📆", // Tear-off calendar
            "🗓️", // Spiral calendar
            "⌛", // Hourglass done
            "⏳", // Hourglass not done
            "⏏️"  // Eject button
        )
    }

    object Arrows {
        val all = listOf(
            "⬆️", // Up arrow
            "⬇️", // Down arrow
            "➡️", // Right arrow
            "⬅️", // Left arrow
            "↗️", // Up-right arrow
            "↘️", // Down-right arrow
            "↙️", // Down-left arrow
            "↖️", // Up-left arrow
            "🔄", // Counterclockwise arrows button
            "🔃", // Clockwise vertical arrows
            "↩️", // Right arrow curving left
            "↪️"  // Left arrow curving right
        )
    }

    object Colors {
        val all = listOf(
            "🔴", // Red circle
            "🟠", // Orange circle
            "🟡", // Yellow circle
            "🟢", // Green circle
            "🔵", // Blue circle
            "🟣", // Purple circle
            "🟤", // Brown circle
            "⚫", // Black circle
            "⚪", // White circle
            "🟥", // Red square
            "🟧", // Orange square
            "🟨", // Yellow square
            "🟩", // Green square
            "🟦", // Blue square
            "🟪", // Purple square
            "🟫", // Brown square
            "⬛", // Black square
            "⬜"  // White square
        )
    }

    object Technology {
        val all = listOf(
            "💻", // Laptop
            "🖥️", // Desktop computer
            "⌨️", // Keyboard
            "🖱️", // Computer mouse
            "🖨️", // Printer
            "📱", // Mobile phone
            "📞", // Telephone receiver
            "☎️", // Telephone
            "📧", // E-mail
            "📨", // Incoming envelope
            "📩", // Envelope with arrow
            "📬", // Open mailbox with raised flag
            "📭", // Open mailbox with lowered flag
            "💾", // Floppy disk
            "💿", // Optical disk
            "📀", // DVD
            "🔌"  // Electric plug
        )
    }

    object Nature {
        val all = listOf(
            "🌟", // Glowing star
            "🌙", // Crescent moon
            "☀️", // Sun
            "⛅", // Sun behind cloud
            "🌈", // Rainbow
            "🌸", // Cherry blossom
            "🌺", // Hibiscus
            "🌻", // Sunflower
            "🌼", // Blossom
            "🌷", // Tulip
            "🍀", // Four leaf clover
            "🌲", // Evergreen tree
            "🌳", // Deciduous tree
            "🌴", // Palm tree
            "🍂", // Fallen leaf
            "🍁"  // Maple leaf
        )
    }

    object Food {
        val all = listOf(
            "🍕", // Pizza
            "🍔", // Hamburger
            "🍟", // French fries
            "🌭", // Hot dog
            "🍿", // Popcorn
            "🍎", // Red apple
            "🍊", // Tangerine
            "🍋", // Lemon
            "🍌", // Banana
            "🍉", // Watermelon
            "🍇", // Grapes
            "🍓", // Strawberry
            "🍒", // Cherries
            "🍑", // Peach
            "🥝", // Kiwi fruit
            "🍅", // Tomato
            "🥕", // Carrot
            "🌽", // Ear of corn
            "☕", // Hot beverage
            "🍰"  // Shortcake
        )
    }

    object Activities {
        val all = listOf(
            "⚽", // Soccer ball
            "🏀", // Basketball
            "🏈", // American football
            "⚾", // Baseball
            "🎾", // Tennis
            "🏐", // Volleyball
            "🎮", // Video game
            "🎲", // Game die
            "♟️", // Chess pawn
            "🎭", // Performing arts
            "🎬", // Clapper board
            "🎤", // Microphone
            "🎧", // Headphone
            "🎵", // Musical note
            "🎶", // Musical notes
            "🎸", // Guitar
            "🎹", // Musical keyboard
            "🎺"  // Trumpet
        )
    }

    val allCategories = mapOf(
        "Documents" to Documents.all,
        "Books" to Books.all,
        "Organization" to Organization.all,
        "Symbols" to Symbols.all,
        "Time" to Time.all,
        "Arrows" to Arrows.all,
        "Colors" to Colors.all,
        "Technology" to Technology.all,
        "Nature" to Nature.all,
        "Food" to Food.all,
        "Activities" to Activities.all
    )

    // Get all emojis in a single flat list
    val allEmojis: List<String> = allCategories.values.flatten()

    // Default filter icon
    const val DEFAULT_FILTER_ICON = "📋"
}
