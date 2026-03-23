package config;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static config.ConfigTypes.*;

/* TODO: your group members need the following features in order to complete their work.
 * - send LSA, we decided to use link-state
 *
 * 1. host and switch LSA packets
 * 2. update config example to match project 3 demo topology
 */

/// Parses configuration information for Hosts, Switches, and Routers from the `config.json` file.
///
/// @author KxtR-27 (Kat)
/// @see ConfigTypes
@SuppressWarnings("unused" /* because it's used later based on the rubric. */)
public class ConfigParser {
	/// The `Gson` object used in conjunction with a `JsonReader` to read data from the `config.json` file.
	private static final Gson GSON = new Gson();


	/// A map of all `Host` objects from the `config.json` file.
	private static final Map<String, HostConfig> hostsMap = new HashMap<>();

	/// A map of all `Switch` objects from the `config.json` file.
	private static final Map<String, SwitchConfig> switchesMap = new HashMap<>();

	/// A map of all `Router` objects from the `config.json` file.
	private static final Map<String, RouterConfig> routersMap = new HashMap<>();


	/// Returns a record of configuration information for a `Host` device.
	///
	/// @param id the ID of the `Host` device (ex. "A")
	///
	/// @return a `HostConfig` object containing all information related to the device,
	/// 		or null if no device with the ID exists.
	///
	/// @see HostConfig
	public static HostConfig getHostConfig(String id) {
		if (hostsMap.isEmpty())
			parseConfig();
		return hostsMap.get(id);
	}

	/// Returns a record of configuration information for a `Switch` device.
	///
	/// @param id the ID of the `Switch` device (ex. "S1")
	///
	/// @return a `SwitchConfig` object containing all information related to the device,
	///  		or null if no device with the ID exists.
	///
	/// @see SwitchConfig
	public static SwitchConfig getSwitchConfig(String id) {
		if (switchesMap.isEmpty())
			parseConfig();
		return switchesMap.get(id);
	}

	/// Returns a record of configuration information for a `Router` device.
	///
	/// @param id the ID of the `Router` device (ex. "R1")
	///
	/// @return a `RouterConfig` object containing all information related to the device,
	/// 		or null if no device with the ID exists.
	///
	/// @see RouterConfig
	public static RouterConfig getRouterConfig(String id) {
		if (routersMap.isEmpty())
			parseConfig();
		return routersMap.get(id);
	}


	/// Returns a record of configuration information for a `Host` device.
	///
	/// @param virtualIP the virtual IP of the `Host` device (ex. "subnet1.A")
	///
	/// @return a `HostConfig` object containing all information related to the device,
	/// 		or null if no device with the ID exists.
	///
	/// @see HostConfig
	/// @see #getHostConfig(String)
	public static HostConfig getHostConfigFromVIP(String virtualIP) {
		String id = virtualIP.split("\\.")[1];
		return getHostConfig(id);
	}

	/// Returns a record of configuration information for a `Router` device.
	///
	/// @param virtualIP the virtual IP of the `Host` device  (ex. "subnet1.R1")
	///
	/// @return a `RouterConfig` object containing all information related to the device,
	/// 		or null if no device with the ID exists.
	///
	/// @see RouterConfig
	/// @see #getRouterConfig(String)
	public static RouterConfig getRouterConfigFromVIP(String virtualIP) {
		String id = virtualIP.split("\\.")[1];
		return getRouterConfig(id);
	}


	/// Parses the config.json file into
	/// a map of Host objects, a map of Switch objects, and a map of Router objects.
	private static void parseConfig() {
		ConfigSnapshot configuration = readConfigFile();
		populateDeviceMaps(configuration);
	}

	/// Maps `Host[]`, `Switch[]`, and `Router[]` arrays to maps using their IDs as keys.
	private static void populateDeviceMaps(ConfigSnapshot configuration) {
		hostsMap.clear();
		switchesMap.clear();
		routersMap.clear();

		for (HostConfig hostDevice : configuration.hosts())
			hostsMap.put(hostDevice.id(), hostDevice);
		for (SwitchConfig switchDevice : configuration.switches())
			switchesMap.put(switchDevice.id(), switchDevice);
		for (RouterConfig routerDevice : configuration.routers())
			routersMap.put(routerDevice.id(), routerDevice);
	}

	/// Returns a `ConfigSnapshot` object of identical structure to the `config.json` itself.
	private static ConfigSnapshot readConfigFile() {
		try (JsonReader reader = newConfigReader()) {
			return GSON.fromJson(reader, ConfigSnapshot.class);
		}
		catch (IOException e) {
			System.err.printf("An unexpected error occurred while parsing the config file.%n");
			throw new RuntimeException(e);
		}
	}

	/// Creates a new `JsonReader` wrapped around a `FileReader` at the `config.json` file's expected path.
	private static JsonReader newConfigReader() throws FileNotFoundException {
		return new JsonReader(new FileReader("src/config/config.json"));
	}


	/// A record with identical structure to the `config.json` file.
	/// This makes reading the file extremely easy.
	///
	/// @see ConfigParser#readConfigFile()
	private record ConfigSnapshot(
			HostConfig[] hosts,
			SwitchConfig[] switches,
			RouterConfig[] routers
	) {}
}
