package com.example.appriskauditor.model

/**
 * Device-wide summary: how many apps hold at least one granted permission
 * in each category. Shown in the header banner on the list screen.
 */
data class CategorySummary(
    val category: PermissionCategory,
    /** Number of apps that have at least one *granted* permission in this category. */
    val appCount: Int
)
