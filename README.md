<p align="center">
  <img width="800" src="images/logo/logo.svg" alt="Networks logo"><br><br>
</p>

# Networks-Folia-1.0

Networks-Folia-1.0 is a GitHub-ready fork of Networks for Slimefun, maintained by Allync and updated to support Folia on Paper 1.21.11.

This repository is intended for production use, private server maintenance, and continued compatibility work on modern Slimefun servers.

## Fork Status

- Forked from the original Networks project by Sefiraat
- Maintained by Allync
- Supports Folia
- Target server version: Paper 1.21.11
- Java version: 17

## What This Fork Focuses On

- Stable Folia-compatible behavior
- Network Grid and Quantum Storage integration
- Quantum Storage persistence across restarts
- Compatibility fixes for supported addons
- Ongoing maintenance for private and GitHub distribution

## Main Features

- Network Grid and Crafting Grid for unified item access
- Network Cells for mixed-item storage
- Network Quantum Storage for high-capacity single-item storage
- Importers, Exporters, Grabbers, and Pushers for network logistics
- Wireless item transfer between linked receivers and transmitters
- Power storage and power output through the network
- Blueprint-based autocrafting with Network Encoders and Autocrafters

## Build

Build the plugin with Maven:

```bash
mvn -DskipTests clean package
```

The generated jar will be:

```text
target/Networks-Folia-1.0.jar
```

## Requirements

- Java 17
- Paper 1.21.11 or a compatible Folia build
- Slimefun

## Notes

- This is not the official upstream Networks repository.
- Upstream credit remains with Sefiraat and original contributors.
- This fork may include behavior fixes and compatibility changes not present upstream.

## Credits

- Original project: Sefiraat / Networks
- Fork maintenance and Folia adaptation: Allync

## License

This fork remains licensed under the GNU General Public License v3.0. See the LICENSE file for the full text.
