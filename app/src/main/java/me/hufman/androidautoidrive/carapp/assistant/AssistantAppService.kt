package me.hufman.androidautoidrive.carapp.assistant

import io.bimmergestalt.idriveconnectkit.android.CarAppAssetResources
import me.hufman.androidautoidrive.PhoneAppResourcesAndroid
import me.hufman.androidautoidrive.carapp.CarAppService
import me.hufman.androidautoidrive.utils.GraphicsHelpersAndroid

class AssistantAppService: CarAppService() {
	var carappAssistant: AssistantApp? = null

	override fun shouldStartApp(): Boolean {
		return true
	}

	override fun onCarStart() {
		val certName = if (CarAppAssetResources(applicationContext, "cdsbaseapp").getAppCertificateRaw("") != null) {
			"cdsbaseapp" } else { "basecoreOnlineServices" }
		carappAssistant = AssistantApp(iDriveConnectionStatus, securityAccess,
				CarAppAssetResources(applicationContext, certName),
				AssistantControllerAndroid(applicationContext, PhoneAppResourcesAndroid(applicationContext)),
				GraphicsHelpersAndroid())
		carappAssistant?.onCreate()
	}

	override fun onCarStop() {
		carappAssistant?.onDestroy()
		carappAssistant = null
	}
}