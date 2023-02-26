package me.hufman.androidautoidrive.notifications

import android.content.res.Resources
import com.nhaarman.mockito_kotlin.*
import de.bmw.idrive.BMWRemoting
import io.bimmergestalt.idriveconnectkit.IDriveConnection
import io.bimmergestalt.idriveconnectkit.android.CarAppResources
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionStatus
import io.bimmergestalt.idriveconnectkit.android.security.SecurityAccess
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIComponent
import me.hufman.androidautoidrive.MockBMWRemotingServer
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.carapp.ReadoutState
import me.hufman.androidautoidrive.carapp.notifications.ReadoutApp
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class ReadoutAppTest {

	val iDriveConnectionStatus = mock<IDriveConnectionStatus>()
	val securityAccess = mock<SecurityAccess> {
		on { signChallenge(any(), any() )} doReturn ByteArray(512)
	}
	val carAppResources = mock<CarAppResources> {
		on { getAppCertificate() } doReturn ByteArrayInputStream(ByteArray(0))
		on { getUiDescription() } doAnswer { this.javaClass.classLoader!!.getResourceAsStream("ui_description_news.xml") }
		on { getImagesDB(any()) } doReturn ByteArrayInputStream(ByteArray(0))
		on { getTextsDB(any()) } doReturn ByteArrayInputStream(ByteArray(0))
	}
	val resources = mock<Resources> {
		on { openRawResource(any()) } doThrow Resources.NotFoundException()
	}

	@Test
	fun testAppInit() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = ReadoutApp(iDriveConnectionStatus, securityAccess, carAppResources, resources)

		val labelComponent = app.infoState.state.componentsList.filterIsInstance<RHMIComponent.Button>().first()
		assertEquals(L.CARINFO_TITLE, mockServer.data[labelComponent.model])
		val listComponent = app.infoState.state.componentsList.filterIsInstance<RHMIComponent.List>().first()
		// won't show data until the screen is opened

		assertTrue(mockServer.cdsSubscriptions.contains("hmi.tts"))

		app.disconnect()
	}

	@Test
	fun testTTSCallback() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = ReadoutApp(iDriveConnectionStatus, securityAccess, carAppResources, resources)

		IDriveConnection.mockRemotingClient?.cds_onPropertyChangedEvent(1, "113", "hmi.tts",
				"{\"TTSState\": {\"state\": 0, \"type\": \"app\", \"currentblock\": 0}}" )
		assertEquals(ReadoutState.UNDEFINED, app.readoutController.currentState)
		assertEquals("app", app.readoutController.currentName)
		assertEquals(0, app.readoutController.currentBlock)

		// test invalid data
		IDriveConnection.mockRemotingClient?.cds_onPropertyChangedEvent(1, "1", "hmi.tts", "{}")
	}

	@Test
	fun testTTSTrigger() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = ReadoutApp(iDriveConnectionStatus, securityAccess, carAppResources, resources)

		app.readoutController.readout(listOf("Test Output"))
		val speechList = mockServer.data[app.readoutController.speechList.id] as BMWRemoting.RHMIDataTable
		assertEquals("Test Output", speechList.data[0][0])
		assertEquals(mapOf(0 to null), mockServer.triggeredEvents[app.readoutController.speechEvent.id])

		// car updates the controller
		IDriveConnection.mockRemotingClient?.cds_onPropertyChangedEvent(1, "113", "hmi.tts",
				"{\"TTSState\": {\"state\": 3, \"type\": \"NotificationReadout\", \"currentblock\": 0}}" )
		assertTrue(app.readoutController.isActive)

		// test cancel
		app.readoutController.cancel()
		val commandList = mockServer.data[app.readoutController.commandList.id] as BMWRemoting.RHMIDataTable
		assertEquals("STR_READOUT_STOP", commandList.data[0][0])
		assertEquals(mapOf(0 to null), mockServer.triggeredEvents[app.readoutController.commandEvent.id])

		// car updates the controller
		IDriveConnection.mockRemotingClient?.cds_onPropertyChangedEvent(1, "113", "hmi.tts",
				"{\"TTSState\": {\"state\": 0, \"type\": \"NotificationReadout\", \"currentblock\": 0}}" )
		assertFalse(app.readoutController.isActive)

		// cancel shouldn't trigger another cancel
		mockServer.data.clear()
		mockServer.triggeredEvents.clear()
		app.readoutController.cancel()
		assertNull(mockServer.data[app.readoutController.commandList.id])
		assertNull(mockServer.triggeredEvents[app.readoutController.commandEvent.id])
	}
}