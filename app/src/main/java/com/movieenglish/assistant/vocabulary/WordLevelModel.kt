package com.movieenglish.assistant.vocabulary

data class WordLevelModel(
    val level: Float,
    val clickCount: Int,
    val ignoreCount: Int,
    val bookmarked: Boolean,
    val mastered: Boolean
) {
    companion object {
        const val MAX_LEVEL = 5f
        const val MIN_LEVEL = 0f
        const val CLICK_BOOST = 2.5f
        const val IGNORE_DECAY = 0.5f
        const val MASTERY_THRESHOLD = 0f
    }

    /** 用户点击查词：快速提升 */
    fun onWordClicked(): WordLevelModel {
        if (bookmarked || mastered) return this
        return copy(
            level = (level + CLICK_BOOST).coerceAtMost(MAX_LEVEL),
            clickCount = clickCount + 1
        )
    }

    /** 展示但未点击：缓慢降低 */
    fun onWordIgnored(): WordLevelModel {
        if (bookmarked || mastered) return this
        val newLevel = (level - IGNORE_DECAY).coerceAtLeast(MIN_LEVEL)
        return copy(
            level = newLevel,
            ignoreCount = ignoreCount + 1,
            mastered = newLevel <= MASTERY_THRESHOLD
        )
    }

    /** 加入生词本：锁定最高级 */
    fun bookmark(): WordLevelModel {
        return copy(level = MAX_LEVEL, bookmarked = true)
    }

    /** 标记已掌握：立即归零 */
    fun markMastered(): WordLevelModel {
        return copy(level = MIN_LEVEL, mastered = true)
    }

    fun isDifficult(): Boolean = level >= 2.5f && !mastered
}
