package net.termer.twinemedia.enumeration

/**
 * Enum class listing all list visibility values
 * @since 1.3.1
 */
enum class ListVisibility {
    /**
     * A private list that can only be viewed by users who created the list and have lists.view OR have lists.view.all permissions
     * @since 1.3.1
     */
    PRIVATE,

    /**
     * A public list that can be viewed by all users AND non-users alike
     * @since 1.3.1
     */
    PUBLIC
}