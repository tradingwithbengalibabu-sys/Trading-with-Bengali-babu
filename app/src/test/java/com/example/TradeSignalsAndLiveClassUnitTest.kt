package com.example

import com.example.data.model.AccountStatus
import com.example.data.model.LiveClassroomEntity
import com.example.data.model.LiveClassroomParticipantEntity
import com.example.data.model.SignalAccess
import com.example.data.model.SignalAudienceType
import com.example.data.model.TradeDirection
import com.example.data.model.TradeSignalEntity
import com.example.data.model.TradeSignalStatus
import com.example.data.model.UserEntity
import com.example.data.model.UserRole
import com.example.liveclass.LiveKitTokenGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class TradeSignalsAndLiveClassUnitTest {

    @Test
    fun tradeSignalModelComputesRiskRewardAndPreservesAllowedReactions() {
        val buySignal = TradeSignalEntity(
            id = "sig_1",
            instrument = "NIFTY",
            direction = TradeDirection.BUY.name,
            entryPrice = 24000.0,
            stopLoss = 23950.0,
            target1 = 24150.0,
            target2 = 24250.0,
            strategyName = "ORB Breakout",
            instructions = "Wait for 5m close above entry.",
            status = TradeSignalStatus.ACTIVE.name,
            audienceType = SignalAudienceType.ALL_STUDENTS.name
        )
        assertEquals("1:3.00", buySignal.computedRiskReward)
        assertEquals(TradeDirection.BUY, buySignal.directionEnum)
        assertEquals(TradeSignalStatus.ACTIVE, buySignal.statusEnum)
        assertEquals(SignalAudienceType.ALL_STUDENTS, buySignal.audienceEnum)
        assertFalse(buySignal.hasVoiceNote)

        val sellSignal = TradeSignalEntity(
            id = "sig_2",
            instrument = "XAUUSD",
            direction = TradeDirection.SELL.name,
            entryPrice = 2650.0,
            stopLoss = 2660.0,
            target1 = 2625.0,
            voiceNoteUrl = "https://example.com/voice.m4a"
        )
        assertEquals("1:2.50", sellSignal.computedRiskReward)
        assertTrue(sellSignal.hasVoiceNote)
        assertEquals(listOf("👍", "❤️", "🔥", "👏", "🚀"), TradeSignalEntity.ALLOWED_REACTIONS)
    }

    @Test
    fun signalAccessBlockingIsCompletelyIndependentFromAccountStatus() {
        val activeStudent = UserEntity(
            id = "st_1",
            userId = "st_1",
            name = "Student One",
            email = "st1@example.com",
            role = UserRole.STUDENT.name,
            status = AccountStatus.ACTIVE.name,
            signalAccess = SignalAccess.ALLOWED.name
        )

        // Blocking signals keeps account ACTIVE for journal, dashboard, analytics, reviews, and login
        val signalBlocked = activeStudent.copy(signalAccess = SignalAccess.BLOCKED.name)
        assertEquals(AccountStatus.ACTIVE, signalBlocked.statusEnum)
        assertEquals(SignalAccess.BLOCKED, signalBlocked.signalAccessEnum)

        // Restoring signal access leaves account status unchanged
        val signalRestored = signalBlocked.copy(signalAccess = SignalAccess.ALLOWED.name)
        assertEquals(AccountStatus.ACTIVE, signalRestored.statusEnum)
        assertEquals(SignalAccess.ALLOWED, signalRestored.signalAccessEnum)

        // Suspending account is orthogonal to signalAccess field
        val suspendedAccountAllowedSignal = activeStudent.copy(status = AccountStatus.SUSPENDED.name)
        assertEquals(AccountStatus.SUSPENDED, suspendedAccountAllowedSignal.statusEnum)
        assertEquals(SignalAccess.ALLOWED, suspendedAccountAllowedSignal.signalAccessEnum)
    }

    @Test
    fun liveKitTokenGeneratorSignsValidJwtWithRoleScopedGrantsAndNoStudentCamera() {
        // Placeholder defaults in .env.example should safely report unconfigured
        assertNull(LiveKitTokenGenerator.loadCredentialsFromBuildConfig())
        assertFalse(LiveKitTokenGenerator.isConfigured())

        fun decodePayloadJson(jwt: String): String {
            val parts = jwt.split(".")
            assertEquals(3, parts.size)
            val header = String(Base64.getUrlDecoder().decode(parts[0]), Charsets.UTF_8)
            assertTrue(header.contains("\"alg\":\"HS256\""))
            return String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
        }

        val teacherJwt = LiveKitTokenGenerator.generateRoomToken(
            apiKey = "APItestKey123",
            apiSecret = "super_secret_signing_key_for_unit_test",
            roomName = LiveClassroomEntity.DEFAULT_ROOM_ID,
            participantUid = "admin_uid",
            participantName = "Admin Teacher",
            isTeacher = true,
            canPublishAudio = true,
            nowEpochSeconds = 1_760_000_000L
        )
        val teacherPayload = decodePayloadJson(teacherJwt)
        assertTrue(teacherPayload.contains("\"iss\":\"APItestKey123\""))
        assertTrue(teacherPayload.contains("\"sub\":\"admin_uid\""))
        assertTrue(teacherPayload.contains("\"name\":\"Admin Teacher\""))
        assertTrue(teacherPayload.contains("\"roomJoin\":true"))
        assertTrue(teacherPayload.contains("\"room\":\"${LiveClassroomEntity.DEFAULT_ROOM_ID}\""))
        assertTrue(teacherPayload.contains("\"canPublish\":true"))
        assertTrue(teacherPayload.contains("\"microphone\""))
        assertTrue(teacherPayload.contains("\"camera\""))
        assertTrue(teacherPayload.contains("\"screen_share\""))
        assertTrue(teacherPayload.contains("\"screen_share_audio\""))

        // Student with approved mic: can publish microphone ONLY (never camera or screen_share)
        val speakingStudentJwt = LiveKitTokenGenerator.generateRoomToken(
            apiKey = "APItestKey123",
            apiSecret = "super_secret_signing_key_for_unit_test",
            roomName = LiveClassroomEntity.DEFAULT_ROOM_ID,
            participantUid = "student_1",
            participantName = "Student One",
            isTeacher = false,
            canPublishAudio = true,
            nowEpochSeconds = 1_760_000_000L
        )
        val studentSpeakPayload = decodePayloadJson(speakingStudentJwt)
        assertTrue(studentSpeakPayload.contains("\"canPublish\":true"))
        assertTrue(studentSpeakPayload.contains("\"canPublishSources\":[\"microphone\"]"))
        assertFalse(studentSpeakPayload.contains("\"camera\""))
        assertFalse(studentSpeakPayload.contains("\"screen_share\""))

        // Student listener: cannot publish audio or video, can still subscribe & send chat data
        val listenerJwt = LiveKitTokenGenerator.generateRoomToken(
            apiKey = "APItestKey123",
            apiSecret = "super_secret_signing_key_for_unit_test",
            roomName = LiveClassroomEntity.DEFAULT_ROOM_ID,
            participantUid = "student_2",
            participantName = "Student Two",
            isTeacher = false,
            canPublishAudio = false,
            nowEpochSeconds = 1_760_000_000L
        )
        val listenerPayload = decodePayloadJson(listenerJwt)
        assertTrue(listenerPayload.contains("\"canPublish\":false"))
        assertTrue(listenerPayload.contains("\"canSubscribe\":true"))
        assertTrue(listenerPayload.contains("\"canPublishData\":true"))
        assertTrue(listenerPayload.contains("\"canPublishSources\":[]"))
    }

    @Test
    fun liveKitUrlNormalizationConvertsHttpsAndRejectsPlaceholdersOrMalformedUrls() {
        assertNull(LiveKitTokenGenerator.normalizeLiveKitUrl(""))
        assertNull(LiveKitTokenGenerator.normalizeLiveKitUrl("null"))
        assertNull(LiveKitTokenGenerator.normalizeLiveKitUrl("YOUR_LIVEKIT_SERVER_URL"))
        assertNull(LiveKitTokenGenerator.normalizeLiveKitUrl("ftp://invalid.livekit.cloud"))
        assertNull(LiveKitTokenGenerator.normalizeLiveKitUrl("wss://"))

        assertEquals(
            "wss://my-classroom.livekit.cloud",
            LiveKitTokenGenerator.normalizeLiveKitUrl("  https://my-classroom.livekit.cloud  ")
        )
        assertEquals(
            "ws://localhost:7880",
            LiveKitTokenGenerator.normalizeLiveKitUrl("http://localhost:7880")
        )
        assertEquals(
            "wss://live.bangalibabu.example",
            LiveKitTokenGenerator.normalizeLiveKitUrl("wss://live.bangalibabu.example")
        )
    }

    @Test
    fun liveClassroomParticipantAudioPermissionRespectsTeacherModeration() {
        val listener = LiveClassroomParticipantEntity(
            id = "st_1",
            uid = "st_1",
            name = "Student",
            role = UserRole.STUDENT.name,
            voiceApproved = false,
            isMutedByTeacher = true,
            isRemoved = false
        )
        assertFalse(listener.canSpeakLive)

        val approvedAndUnmuted = listener.copy(voiceApproved = true, isMutedByTeacher = false)
        assertTrue(approvedAndUnmuted.canSpeakLive)

        val mutedByTeacher = approvedAndUnmuted.copy(isMutedByTeacher = true)
        assertFalse(mutedByTeacher.canSpeakLive)

        val removedParticipant = approvedAndUnmuted.copy(isRemoved = true)
        assertFalse(removedParticipant.canSpeakLive)

        val teacher = LiveClassroomParticipantEntity(
            id = "admin",
            uid = "admin",
            name = "Teacher",
            role = UserRole.ADMIN.name
        )
        assertTrue(teacher.canSpeakLive)
    }
}
