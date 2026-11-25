package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class LiveClassroomEntity(
    @DocumentId val id: String = "",
    val title: String = "Live Trading Session",
    val roomName: String = DEFAULT_ROOM_ID,
    @get:PropertyName("isLive")
    @PropertyName("isLive")
    val isLive: Boolean = false,
    @get:PropertyName("isScreenSharing")
    @PropertyName("isScreenSharing")
    val isScreenSharing: Boolean = false,
    val hostUid: String = "",
    val hostName: String = "",
    val startedAtMillis: Long = 0L,
    val endedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        const val DEFAULT_ROOM_ID = "twbb_live_classroom_main"
    }
}

@Keep
@IgnoreExtraProperties
data class LiveClassroomParticipantEntity(
    @DocumentId val id: String = "",
    val uid: String = "",
    val name: String = "",
    val role: String = UserRole.STUDENT.name,
    val profilePhotoUrl: String = "",
    val joinedAtMillis: Long = 0L,
    val handRaised: Boolean = false,
    val voiceApproved: Boolean = false,
    @get:PropertyName("isMutedByTeacher")
    @PropertyName("isMutedByTeacher")
    val isMutedByTeacher: Boolean = true,
    @get:PropertyName("isSelfMuted")
    @PropertyName("isSelfMuted")
    val isSelfMuted: Boolean = true,
    @get:PropertyName("isRemoved")
    @PropertyName("isRemoved")
    val isRemoved: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val roleEnum: UserRole
        get() = UserRole.entries.firstOrNull { it.name == role } ?: UserRole.STUDENT

    @get:Exclude
    val canSpeakLive: Boolean
        get() = !isRemoved && (roleEnum == UserRole.ADMIN || (voiceApproved && !isMutedByTeacher))
}

@Keep
@IgnoreExtraProperties
data class LiveClassroomMessageEntity(
    @DocumentId val id: String = "",
    val roomId: String = LiveClassroomEntity.DEFAULT_ROOM_ID,
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
}
