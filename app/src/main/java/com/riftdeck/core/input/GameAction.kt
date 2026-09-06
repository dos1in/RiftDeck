package com.riftdeck.core.input

enum class GameAction {
    Up,
    Down,
    Left,
    Right,
    Confirm,
    Back,
    Details,
    Favorite,
    PreviousCategory,
    NextCategory,
    PreviousPage,
    NextPage,
    Menu,
    Search,
}

val GameAction.isDirectional: Boolean
    get() = this == GameAction.Up ||
        this == GameAction.Down ||
        this == GameAction.Left ||
        this == GameAction.Right
