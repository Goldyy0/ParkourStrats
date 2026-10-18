package me.texyle.startreminders.importing;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import me.texyle.startreminders.data.Jump;
import me.texyle.startreminders.data.ParkourMap;
import me.texyle.startreminders.data.ServerProfile;
import me.texyle.startreminders.persistence.DataStoreRepository;
import me.texyle.startreminders.services.MapService;

/** Exports a map as a template file and imports one back. */
public final class MapTemplateIO {

	private MapTemplateIO() {}

	public static void exportMapTemplateToFile(ServerProfile server, ParkourMap map, File file) throws IOException {
		DataStoreRepository.ensureLoaded();
		if (server == null || map == null || file == null) return;

		Gson gson = new GsonBuilder().setPrettyPrinting().create();

		// Wrap to allow future versioning
		MapTemplatePayload payload = new MapTemplatePayload();
		payload.version = 1;
		payload.serverId = server.getId();
		payload.map = map;

		File parent = file.getParentFile();
		if (parent != null && !parent.exists()) parent.mkdirs();

		FileWriter fw = new FileWriter(file);
		try {
			fw.write(gson.toJson(payload));
		} finally {
			fw.close();
		}
	}

	public static ParkourMap importMapTemplateFromFile(ServerProfile targetServer, File file) throws IOException {
		DataStoreRepository.ensureLoaded();
		if (targetServer == null || file == null) return null;

		Gson gson = new Gson();
		MapTemplatePayload payload;

		FileReader fr = new FileReader(file);
		try {
			payload = gson.fromJson(fr, MapTemplatePayload.class);
		} finally {
			fr.close();
		}

		if (payload == null || payload.map == null) return null;

		ParkourMap imported = payload.map;

		// Safety: ensure structure is not null
		if (imported.getJumps() == null) imported.setJumps(new ArrayList<Jump>());

		// Optional: normalize reminders lists, active indices, etc.
		// (If you implemented ensureEightLines, you can normalize reminder lines here too.)

		// Ensure unique map ID inside the target server
		String baseId = imported.getId() != null ? imported.getId().trim() : "ImportedMap";
		if (baseId.isEmpty()) baseId = "ImportedMap";
		String uniqueId = makeUniqueMapId(targetServer, baseId);
		imported.setId(uniqueId);

		if (targetServer.getMaps() == null) {
			targetServer.setMaps(new ArrayList<ParkourMap>());
		}
		targetServer.getMaps().add(imported);

		DataStoreRepository.save();
		return imported;
	}

	private static String makeUniqueMapId(ServerProfile server, String baseId) {
		String id = baseId;
		int n = 2;

		while (MapService.findMapById(server, id) != null) {
			id = baseId + "_" + n;
			n++;
			if (n > 9999) {
				id = baseId + "_" + System.currentTimeMillis();
				break;
			}
		}
		return id;
	}

	static class MapTemplatePayload {
		int version;
		String serverId;
		ParkourMap map;
	}

}
