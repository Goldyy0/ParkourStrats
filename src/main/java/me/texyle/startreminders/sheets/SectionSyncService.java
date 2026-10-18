package me.texyle.startreminders.sheets;

import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;

import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.persistence.DataStoreRepository;
import me.texyle.startreminders.utils.ChatUtils;

/** Downloads the section layout of a map from a Google Sheet through the Apps Script endpoint and applies it. */
public final class SectionSyncService {

	private SectionSyncService() {}

	// -------------------------
	// Section Sync
	// -------------------------

	private static final String SECTION_SYNC_ENDPOINT =
			"https://script.google.com/macros/s/AKfycbyTqmi25lLkfqAqfmcNGDYK7IRwBU-40XWaAwIBNFfkX45LuH-cTjwi2OzMCAw5ElYk/exec";
	private static String lastSectionSyncError = "";

	public static String getLastSectionSyncError() {
		return lastSectionSyncError != null ? lastSectionSyncError : "";
	}

	private static void setLastError(String msg) {
		lastSectionSyncError = (msg != null) ? msg : "";
	}

	public static boolean syncSectionsFromAppsScript(ParkourMap map, String sheetUrl) {
		DataStoreRepository.ensureLoaded();
		setLastError("");

		if (map == null) {
			setLastError("Map is null.");
			postErrorToChat();
			return false;
		}

		String u = (sheetUrl != null) ? sheetUrl.trim() : "";
		if (u.isEmpty()) {
			setLastError("Spreadsheet URL is empty.");
			postErrorToChat();
			return false;
		}

		try {
			String url = SECTION_SYNC_ENDPOINT + "?sheetUrl=" + urlEncode(u);

			String json = httpGetUtf8(url);
			if (json == null || json.trim().isEmpty()) {
				setLastError("Empty response from Apps Script.");
				postErrorToChat();
				return false;
			}

			boolean ok = SectionSheetParser.applySectionsFromJson(map, json);
			if (!ok) {
				// Prefer explicit "error" returned by Apps Script
				String err = SectionSheetParser.readErrorFromJson(json);
				if (err != null && err.trim().length() > 0) {
					setLastError(err.trim());
				} else {
					setLastError("Failed to apply sections from JSON.");
				}

				postErrorToChat();
				return false;
			}

			map.setLastSheetSyncMs(System.currentTimeMillis());
			DataStoreRepository.save();
			return true;

		} catch (Throwable t) {
			t.printStackTrace();
			setLastError(String.valueOf(t));
			postErrorToChat();
			return false;
		}
	}

	private static String httpGetUtf8(String urlStr) throws Exception {
		java.net.URL url = new java.net.URL(urlStr);
		java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
		con.setRequestMethod("GET");
		con.setConnectTimeout(8000);
		con.setReadTimeout(15000);
		con.setUseCaches(false);

		java.io.InputStream in = con.getInputStream();
		try {
			java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(in, "UTF-8"));
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = br.readLine()) != null) {
				sb.append(line);
			}
			return sb.toString();
		} finally {
			try { in.close(); } catch (Throwable ignored) {}
		}
	}

	private static String urlEncode(String s) throws Exception {
		return java.net.URLEncoder.encode(s != null ? s : "", "UTF-8");
	}

	private static void postErrorToChat() {
		try {
			final String err = getLastSectionSyncError();
			if (err == null || err.trim().isEmpty()) {
				return;
			}

			Minecraft mc = Minecraft.getMinecraft();
			if (mc == null) return;

			mc.addScheduledTask(new Runnable() {
				@Override
				public void run() {
					ChatUtils.sendChat(Minecraft.getMinecraft(),
							ChatUtils.ERROR_PREFIX + "Sync Sections failed: " +
									EnumChatFormatting.GRAY + err);
				}
			});
		} catch (Throwable ignored) {
		}
	}

}
