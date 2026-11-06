package com.karakept.app.data.model

enum class SyncStrategy {
    NEVER,          // Only metadata, fetch content on demand
    PER_BOOKMARK,   // Same as NEVER but explicitly mentioned in requirements as a variation.
                    // Actually, "PER_BOOKMARK: content is fetched everytime we open the bookmark view"
                    // "NEVER: content is fetched everytime we open the bookmark view, and NOT stored in the db"
                    // Wait, user said: "never (content is fetched everytime we open the bookmark view, and NOT stored in the db)"
                    // "per bookmark: (content is fetched everytime we open the bookmark view, and NOT stored in the db)"
                    // These sound identical. 
                    // Let's re-read user request.
                    // Request 1: "strategies for content sync ... never (content is fetched everytime we open the bookmark view, and NOT stored in the db)"
                    // Request 2: "other sync strategies: ... per bookmark: (content is fetched everytime we open the bookmark view, and NOT stored in the db). Offline sync will only work for bookmark that have been opened"
                    // Ah, "NOT stored in the db" means checking it doesn't persist?
                    // "Offline sync will only work for bookmark that have been opened" implies it IS stored after opening.
                    // So "NEVER" = never stored in DB.
                    // "PER_BOOKMARK" = stored in DB after opening (lazy sync).
    
    PER_LIST,       // User selects lists to sync content for
    ALL             // Sync all content
}
