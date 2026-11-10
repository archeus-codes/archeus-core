/*
 * Copyright 2021-2026 Deiondre Andrews (Demonic722)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package codes.archeus.core.parser;

import java.util.regex.Pattern;

import codes.archeus.core.domain.Game;

/**
 * File names for the formats with one file per game, melonDS's {@code .mch} and RetroArch's {@code .cht}.
 */
public final class GameFileNames {
	private static final Pattern UNSAFE_IN_FILE_NAMES = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

	private GameFileNames() {
	}

	/**
	 * The file name for a game, such as {@code Animal Crossing - Wild World (USA) [ADME 8F7851CB].mch}. The serial and
	 * CRC32 tell apart games with the same title, and characters not allowed in file names become {@code _}.
	 */
	public static String of(Game game, String extension) {
		String title = UNSAFE_IN_FILE_NAMES.matcher(game.getTitle()).replaceAll("_").strip();
		return title + " [" + game.getSerial() + " " + game.getCrc32() + "]" + extension;
	}
}
