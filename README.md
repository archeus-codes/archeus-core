# archeus-core

A Java library that reads and writes Nintendo DS cheat databases.

## Origin

This library was extracted from the Archeus monorepo, in development since February 2021. Archeus is a curated
Nintendo DS cheat database with a website and a desktop editor:

- The site builds every release of the database with this library, writing one file for each kind of flashcart,
  emulator and Action Replay.
- The desktop app opens, edits and saves cheat files with it, on macOS, Windows and Linux. It's being moved to its
  own repo under [archeus-codes](https://github.com/archeus-codes).

## Features

### Formats

| Format | Files | Read | Write |
|---|---|---|---|
| R4 binary (R4CCE, most flashcarts and kernels) | `usrcheat.dat` | Yes | Yes |
| R4CCE XML | `cheats.xml` | Yes | Yes |
| Action Replay DS(i) XML | `cheats.xml` | Yes | Yes |
| EDGE and iEDGE | `cheats.dat` | Yes | Yes |
| CycloDS Evolution | `user.evoCheats` | Yes | Yes |
| melonDS | one `.mch` per game | Yes | Yes |
| RetroArch | one `.cht` per game | Yes | Yes |

EDGE and CycloDS Evolution had to be reverse engineered from R4CCE with Ghidra and AI. It took many rounds of testing
to get them working. The EDGE files are tested on a real iEDGE cart, and the CycloDS files against R4CCE's own.

### Codes and ROMs

- `NdsRomReader`, which reads a ROM's game code and computes the game ID that cheat databases use.
- An Action Replay DS and Nitrohax code decoder (`codes.archeus.core.ards`) that checks code lines for structural
  problems and classifies the addresses they touch.

## Usage

Reading a `usrcheat.dat` and writing it as an EDGE `cheats.dat` (the classes are in `codes.archeus.core.parser`):

```java
try (var file = new RandomAccessFile("usrcheat.dat", "r")) {
    ParseResult database = new UsercheatDatParser().parse(file);

    try (var out = Files.newOutputStream(Path.of("cheats.dat"))) {
        new EdgeCheatsWriter().write(database.cheatFile(), database.games(), out);
    }
}
```

The XML, EDGE and CycloDS formats work the same way through `CheatFileParser` and `CheatFileWriter`, while melonDS
and RetroArch read and write one game per file. An EDGE or CycloDS file holds at most 4,096 games, so split a larger
database with `UsayParts.split` and write each part to its own file. A file that's damaged or cut short throws
`UsercheatParserException`, saying where.

## Building

It needs JDK 25. The Maven Wrapper downloads Maven itself, so there's nothing else to install.

```
./mvnw test       # run the tests
./mvnw install    # build the jar and install it in your local Maven repository
```

A project can then depend on it:

```xml
<dependency>
  <groupId>codes.archeus</groupId>
  <artifactId>archeus-core</artifactId>
  <version>1.0.0</version>
</dependency>
```

It has one dependency, the SLF4J API, for logging.

## Credits

- **Yasu**, for R4CCE (R4 Cheat Code Editor), which the EDGE and CycloDS Evolution formats were worked out from.
- **Martin Korth**, for [GBATEK](https://problemkaputt.de/gbatek.htm), the Nintendo DS hardware reference.
- **Chishm**, for [Nitrohax](https://github.com/chishm/nitrohax), whose cheat engine's extra code types the decoder
  reads.

## License

[Apache License 2.0](LICENSE), with the attribution in [NOTICE](NOTICE).

Copyright (c) 2021-2026 Deiondre Andrews (Demonic722).
