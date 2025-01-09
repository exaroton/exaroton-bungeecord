# exaroton BungeeCord Plugin
A BungeeCord plugin designed to use exaroton servers in your proxy network.
This plugin can also be used on proxies that don't run on exaroton.

## Setup
1. Install the plugin and start the server
2. Add the API token in the config.yml
3. Restart the proxy

## Features

### Start/Stop commands
Start a server and automatically add it to the network
when it goes online with `/exaroton start <server>`.
To stop a server and remove it, use `/exaroton stop`.
Restarting a server is possible with `/exaroton restart`.

To start watching a server, or to add a server that is already online
use `/exaroton add`. To remove a server from the proxy and stop watching
it, use `/exaroton remove`.

The commands require the permission nodes `exaroton.<subcommand>` e.g.
`exaroton.stop`.

### Switch command
Users can switch to any server using `/exaroton switch <server>`.
If the server is offline it will be started before they are moved, if it's not in the network it will be added.
The command only requires the permission `exaroton.switch` 


### Watch servers
Automatically remove offline servers specified in the bungee config
from the network and add them again when they go online.
This also ensures the proxy always uses the correct host and port.

This can be disabled in the config.

### Autostart
Automatically start exaroton servers defined in the plugin config 
when the proxy starts and add them to the network.
This can be enabled in the config.

### Auto-stop
Automatically stop exaroton servers defined in the plugin config
when the proxy shuts down.
This can be enabled in the config.

## Plugin API
You can call any actions in this plugin from your own BungeeCord plugin using the ExarotonPluginAPI class.

Adding it to your gradle project:
```
implementation 'com.exaroton:bungee:1.5.3'
```
