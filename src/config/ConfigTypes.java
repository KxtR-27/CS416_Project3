package config;

import java.util.Map;

/// Remember: there are two ways to call these classes in your code.
/// 1. type the whole class - `ConfigTypes.HostConfig`
/// 2. or......<br>
///    `import static config.ConfigTypes.*` at the top of your file,<br>
///    then just type `HostConfig`<br>
public class ConfigTypes {
	/// Configuration information for a `Host`
	public record HostConfig(
			String id,
			String realIP,
			int realPort,
			String virtualIP,
			String gatewayVIP,
			String[] neighbors
	) {}

	/// Configuration information for a `Switch`
	public record SwitchConfig(
			String id,
			String ipAddress,
			int port,
			String[] neighbors
	) {}

	/// Configuration information for a `Router`
	public record RouterConfig(
			String id,
			String realIP,
			int realPort,
			String[] virtualIPs,
			Map<String, String[]> neighborsPerVirtualIP
	) {}
}