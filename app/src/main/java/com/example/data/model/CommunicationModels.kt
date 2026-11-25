package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class ConversationThread(
    @DocumentId val id: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val studentEmail: String = "",
    val lastMessageText: String = "",
    val lastMessageSenderRole: String = UserRole.STUDENT.name,
    val lastMessageAtMillis: Long = 0L,
    val unreadByAdminCount: Int = 0,
    val unreadByStudentCount: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val lastSenderRoleEnum: UserRole
        get() = UserRole.entries.firstOrNull { it.name == lastMessageSenderRole } ?: UserRole.STUDENT
}

@Keep
@IgnoreExtraProperties
data class DirectMessageEntity(
    @DocumentId val id: String = "",
    val studentId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderRole: String = UserRole.STUDENT.name,
    val body: String = "",
    val sentAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    @get:Exclude
    val senderRoleEnum: UserRole
        get() = UserRole.entries.firstOrNull { it.name == senderRole } ?: UserRole.STUDENT

    @get:Exclude
    val effectiveTimestampMillis: Long
        get() = if (sentAtMillis > 0L) sentAtMillis else (createdAt?.toDate()?.time ?: 0L)
}

@Keep
@IgnoreExtraProperties
data class StudentWarningEntity(
    @DocumentId val id: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val senderAdminId: String = "",
    val senderAdminName: String = "",
    val severity: String = WarningSeverity.IMPORTANT.name,
    val title: String = "",
    val message: String = "",
    val readByStudent: Boolean = false,
    val sentAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val severityEnum: WarningSeverity
        get() = WarningSeverity.entries.firstOrNull { it.name == severity } ?: WarningSeverity.IMPORTANT

    @get:Exclude
    val effectiveTimestampMillis: Long
        get() = if (sentAtMillis > 0L) sentAtMillis else (createdAt?.toDate()?.time ?: 0L)
}

@Keep
@IgnoreExtraProperties
data class AnnouncementEntity(
    @DocumentId val id: String = "",
    val title: String = "",
    val message: String = "",
    val priority: String = AnnouncementPriority.NORMAL.name,
    val authorId: String = "",
    val authorName: String = "",
    val publishedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val priorityEnum: AnnouncementPriority
        get() = AnnouncementPriority.entries.firstOrNull { it.name == priority } ?: AnnouncementPriority.NORMAL

    @get:Exclude
    val effectiveTimestampMillis: Long
        get() = if (publishedAtMillis > 0L) publishedAtMillis else (createdAt?.toDate()?.time ?: 0L)
}
