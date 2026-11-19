package ru.otus.highload.chat.model

enum class MarkReadResult { MARKED, ALREADY_READ }

class MessageNotFoundException : RuntimeException("Message not found")

class MessageAccessDeniedException : RuntimeException("Only recipient can mark message read")
