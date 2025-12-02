package com.karakept.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

object MaterialIconHelper {
    val allIcons: Map<String, ImageVector> = mapOf(
        // Navigation & UI
        "Menu" to Icons.Default.Menu,
        "Home" to Icons.Default.Home,
        "ArrowBack" to Icons.AutoMirrored.Filled.ArrowBack,
        "ArrowForward" to Icons.AutoMirrored.Filled.ArrowForward,
        "Close" to Icons.Default.Close,
        "Add" to Icons.Default.Add,
        "Delete" to Icons.Default.Delete,
        "Edit" to Icons.Default.Edit,
        "Settings" to Icons.Default.Settings,
        "Search" to Icons.Default.Search,
        "Share" to Icons.Default.Share,
        "MoreVert" to Icons.Default.MoreVert,
        "Refresh" to Icons.Default.Refresh,
        "Check" to Icons.Default.Check,
        "CheckCircle" to Icons.Default.CheckCircle,
        "Info" to Icons.Default.Info,
        "Warning" to Icons.Default.Warning,
        "Error" to Icons.Default.Warning,
        "Lock" to Icons.Default.Lock,
        "Unlock" to Icons.Default.LockOpen, // Changed from Login to LockOpen (standard)
        "Visibility" to Icons.Default.Visibility,
        "VisibilityOff" to Icons.Default.VisibilityOff,
        "Favorite" to Icons.Default.Favorite,
        "FavoriteBorder" to Icons.Default.FavoriteBorder,
        "Star" to Icons.Default.Star,
        "StarBorder" to Icons.Default.StarBorder,
        "ThumbUp" to Icons.Default.ThumbUp,
        "ThumbDown" to Icons.Default.ThumbDown,
        
        // Content & Media
        "Article" to Icons.AutoMirrored.Filled.Article,
        "Book" to Icons.Default.Book,
        "Bookmark" to Icons.Default.Bookmark,
        "BookmarkBorder" to Icons.Default.BookmarkBorder,
        "Description" to Icons.Default.Description,
        "Image" to Icons.Default.Image,
        "Video" to Icons.Default.PlayArrow,
        "Audio" to Icons.Default.PlayArrow,
        "Folder" to Icons.Default.Folder, // FolderOpen might be missing
        "FolderOpen" to Icons.Default.Folder, // Fallback
        "List" to Icons.AutoMirrored.Filled.List,
        "Grid" to Icons.Default.List, // Fallback for GridView
        "Filter" to Icons.Default.FilterList,
        "Sort" to Icons.AutoMirrored.Filled.Sort,
        "Label" to Icons.Default.Label,
        "Tag" to Icons.Default.Label,
        "Inbox" to Icons.Default.Inbox,
        "Archive" to Icons.Default.Archive,
        "Unarchive" to Icons.Default.Unarchive, // Should exist
        "Send" to Icons.AutoMirrored.Filled.Send,
        "Drafts" to Icons.Default.Drafts, // Should exist
        "Mail" to Icons.Default.Email,
        "Chat" to Icons.Default.Email,
        "Comment" to Icons.Default.Email,
        
        // Device & Hardware
        "Phone" to Icons.Default.Phone,
        "Tablet" to Icons.Default.Phone,
        "Laptop" to Icons.Default.Computer, // Should exist
        "Desktop" to Icons.Default.Computer,
        "Watch" to Icons.Default.DateRange,
        "Camera" to Icons.Default.Add,
        "Mic" to Icons.Default.Add,
        
        // Social & People
        "Person" to Icons.Default.Person,
        "People" to Icons.Default.Person,
        "Group" to Icons.Default.Person,
        "Face" to Icons.Default.Face,
        "AccountCircle" to Icons.Default.AccountCircle,
        "School" to Icons.Default.Home,
        "Work" to Icons.Default.Home,
        
        // Places & Travel
        "Place" to Icons.Default.Place,
        "Location" to Icons.Default.LocationOn,
        "Map" to Icons.Default.LocationOn,
        "Explore" to Icons.Default.Search,
        "Flight" to Icons.Default.Home,
        
        // Action & Status
        "Done" to Icons.Default.Done,
        "DoneAll" to Icons.Default.DoneAll,
        "Pending" to Icons.Default.DateRange,
        "Schedule" to Icons.Default.DateRange,
        "DateRange" to Icons.Default.DateRange,
        "Event" to Icons.Default.DateRange,
        "Alarm" to Icons.Default.Notifications,
        "Notifications" to Icons.Default.Notifications,
        "NotificationsOff" to Icons.Default.Notifications, // Fallback
        
        // Editor
        "FormatBold" to Icons.Default.Edit,
        "FormatItalic" to Icons.Default.Edit,
        "FormatUnderlined" to Icons.Default.Edit,
        "FormatListBulleted" to Icons.AutoMirrored.Filled.List,
        "FormatListNumbered" to Icons.AutoMirrored.Filled.List,
        "AttachFile" to Icons.Default.Add,
        
        // Objects
        "ShoppingCart" to Icons.Default.ShoppingCart,
        "ShoppingBag" to Icons.Default.ShoppingCart,
        "CreditCard" to Icons.Default.ShoppingCart,
        "Build" to Icons.Default.Build,
        "Code" to Icons.Default.Build,
        "BugReport" to Icons.Default.Build,
        "Lightbulb" to Icons.Default.Info,
        "Flag" to Icons.Default.Info
    )

    val categories: Map<String, List<String>> = mapOf(
        "Common" to listOf("Star", "Bookmark", "Favorite", "Share", "Delete", "Edit", "Settings"),
        "Navigation" to listOf("Menu", "Home", "ArrowBack", "ArrowForward", "Close", "MoreVert", "Refresh", "Check"),
        "Content" to listOf("Article", "Book", "Description", "Image", "Folder", "List", "Filter", "Sort", "Label", "Inbox", "Archive"),
        "Social" to listOf("Person", "Face", "AccountCircle", "Mail", "Chat", "ThumbUp"),
        "Action" to listOf("Done", "DoneAll", "Schedule", "DateRange", "Alarm", "Notifications", "Search", "Lock", "Visibility"),
        "Objects" to listOf("ShoppingCart", "Build", "Lightbulb", "Flag", "Place", "Location")
    )
}

@Composable
fun FilterIcon(
    iconName: String,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    fontSize: TextUnit = 24.sp
) {
    val icon = MaterialIconHelper.allIcons[iconName]
    if (icon != null) {
        Icon(
            imageVector = icon,
            contentDescription = iconName,
            modifier = modifier,
            tint = tint
        )
    } else {
        // Fallback to text (emoji)
        Text(
            text = iconName,
            modifier = modifier,
            fontSize = fontSize,
            color = tint
        )
    }
}
